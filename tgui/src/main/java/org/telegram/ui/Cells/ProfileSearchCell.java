/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CheckBox2;

import java.util.ArrayList;
import java.util.Locale;

/**
 * tgui: upstream ProfileSearchCell (the chat rows of the chat-list search) transplanted onto
 * {@link DialogItem}: avatar, name with secret lock / verified badge, the status line
 * ({@link DialogItem#status}, blue when online) and the unread counter of recent searches.
 * {@link DialogItem#highlightedWords} highlight the match in the name. No TLRPC, no ImageReceiver,
 * no emoji status / premium lock / bot "Open" button.
 */
public class ProfileSearchCell extends BaseCell implements NotificationCenter.NotificationCenterDelegate, Theme.Colorable {

    private CharSequence currentName;
    private final AvatarDrawable avatarDrawable;
    private CharSequence subLabel;
    private Theme.ResourcesProvider resourcesProvider;

    private DialogItem item;
    private ArrayList<String> highlightedWords;
    private boolean savedMessages;

    public boolean useSeparator;

    private int nameLeft;
    private int nameTop;
    private StaticLayout nameLayout;
    private boolean drawNameLock;
    private int nameLockLeft;
    private int nameLockTop;
    private int nameWidth;

    private int sublabelOffsetX;
    private int sublabelOffsetY;

    private boolean drawCount;
    private int lastUnreadCount;
    private int countTop = AndroidUtilities.dp(19);
    private int countLeft;
    private int countWidth;
    private StaticLayout countLayout;

    private boolean drawCheck;

    private int statusLeft;
    private StaticLayout statusLayout;

    private final RectF rect = new RectF();
    private final RectF avatarRect = new RectF();
    private Paint avatarBitmapPaint;
    private Bitmap avatarShaderBitmap;
    private BitmapShader avatarShader;
    private final Matrix avatarBitmapMatrix = new Matrix();
    private final Path avatarClipPath = new Path();

    CheckBox2 checkBox;

    public ProfileSearchCell(Context context) {
        this(context, null);
    }

    public ProfileSearchCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        avatarDrawable = new AvatarDrawable();

        checkBox = new CheckBox2(context, 21, resourcesProvider);
        checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(3);
        addView(checkBox);
    }

    /**
     * Upstream setData(user/chat, encryptedChat, name, status, needCount, saved).
     *
     * @param n         name override (null uses {@link DialogItem#title})
     * @param s         status override (null uses {@link DialogItem#status})
     * @param needCount draw the unread counter (recent searches)
     * @param saved     saved-messages row: no status line, name centered
     */
    public void setData(DialogItem dialog, CharSequence n, CharSequence s, boolean needCount, boolean saved) {
        currentName = n;
        item = dialog;
        subLabel = s;
        drawCount = needCount;
        savedMessages = saved;
        update(0);
    }

    /** Words to highlight in the name (overrides {@link DialogItem#highlightedWords}); call before setData. */
    public void setHighlightedWords(ArrayList<String> words) {
        highlightedWords = words;
    }

    public DialogItem getDialog() {
        return item;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (item != null && getMeasuredWidth() > 0) {
                buildLayout();
            }
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (checkBox != null) {
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(60) + (useSeparator ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (item == null) {
            return;
        }
        if (checkBox != null) {
            int x = LocaleController.isRTL ? (right - left) - AndroidUtilities.dp(42) : AndroidUtilities.dp(42);
            int y = AndroidUtilities.dp(36);
            checkBox.layout(x, y, x + checkBox.getMeasuredWidth(), y + checkBox.getMeasuredHeight());
        }
        if (changed) {
            buildLayout();
        }
    }

    public void setSublabelOffset(int x, int y) {
        sublabelOffsetX = x;
        sublabelOffsetY = y;
    }

    public void buildLayout() {
        CharSequence nameString;
        TextPaint currentNamePaint;

        drawNameLock = false;
        drawCheck = false;
        nameLockTop = 0;

        if (item.secret) {
            drawNameLock = true;
            if (!LocaleController.isRTL) {
                nameLockLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline + 4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
            } else {
                nameLockLeft = getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline + 2) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                nameLeft = AndroidUtilities.dp(11);
            }
            nameLockTop = AndroidUtilities.dp(22.0f);
        } else {
            if (!LocaleController.isRTL) {
                nameLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
            } else {
                nameLeft = AndroidUtilities.dp(11);
            }
            nameLockTop = AndroidUtilities.dp(21);
            drawCheck = item.verified;
        }
        if (!LocaleController.isRTL) {
            statusLeft = AndroidUtilities.dp(AndroidUtilities.leftBaseline);
        } else {
            statusLeft = AndroidUtilities.dp(11);
        }

        if (currentName != null) {
            nameString = currentName;
        } else {
            nameString = item.title != null ? AndroidUtilities.replaceNewLines(item.title) : "";
        }
        if (item.secret) {
            currentNamePaint = Theme.dialogs_searchNameEncryptedPaint;
        } else {
            currentNamePaint = Theme.dialogs_searchNamePaint;
        }

        int statusWidth;
        if (!LocaleController.isRTL) {
            statusWidth = nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(14);
        } else {
            statusWidth = nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(AndroidUtilities.leftBaseline);
        }
        if (drawNameLock) {
            nameWidth -= AndroidUtilities.dp(6) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
        }
        if (drawCheck) {
            nameWidth -= AndroidUtilities.dp(6) + Theme.dialogs_verifiedDrawable.getIntrinsicWidth();
        }

        nameWidth -= getPaddingLeft() + getPaddingRight();
        statusWidth -= getPaddingLeft() + getPaddingRight();

        if (drawCount) {
            int unreadCount = item.unreadCount;
            if (unreadCount != 0) {
                lastUnreadCount = unreadCount;
                String countString = String.format(Locale.US, "%d", unreadCount);
                countWidth = Math.max(AndroidUtilities.dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(countString)));
                countLayout = new StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                int w = countWidth + AndroidUtilities.dp(18);
                nameWidth -= w;
                statusWidth -= w;
                if (!LocaleController.isRTL) {
                    countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(19);
                } else {
                    countLeft = AndroidUtilities.dp(19);
                    nameLeft += w;
                    statusLeft += w;
                }
            } else {
                lastUnreadCount = 0;
                countLayout = null;
            }
        } else {
            lastUnreadCount = 0;
            countLayout = null;
        }

        if (nameWidth < 0) {
            nameWidth = 0;
        }
        CharSequence nameStringFinal = TextUtils.ellipsize(nameString, currentNamePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        if (nameStringFinal != null) {
            nameStringFinal = Emoji.replaceEmoji(nameStringFinal, currentNamePaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
        }
        // tgui: upstream DialogsSearchAdapter passes the name already run through AndroidUtilities.highlightText
        ArrayList<String> words = highlightedWords != null ? highlightedWords : item.highlightedWords;
        if (words != null && !words.isEmpty()) {
            CharSequence s = AndroidUtilities.highlightText(nameStringFinal, words, resourcesProvider);
            if (s != null) {
                nameStringFinal = s;
            }
        }
        nameLayout = new StaticLayout(nameStringFinal, currentNamePaint, Math.max(nameWidth, 0), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        CharSequence statusString = subLabel != null ? subLabel : item.status;
        TextPaint currentStatusPaint = Theme.dialogs_offlinePaint;
        if (item.statusOnline != null ? item.statusOnline : item.online) {
            currentStatusPaint = Theme.dialogs_onlinePaint;
        }
        if (savedMessages) {
            statusString = null;
            nameTop = AndroidUtilities.dp(20);
        }

        if (!TextUtils.isEmpty(statusString)) {
            CharSequence statusStringFinal = TextUtils.ellipsize(statusString, currentStatusPaint, statusWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            statusLayout = new StaticLayout(statusStringFinal, currentStatusPaint, Math.max(statusWidth, 0), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            nameTop = AndroidUtilities.dp(9);
            nameLockTop -= AndroidUtilities.dp(10);
        } else {
            nameTop = AndroidUtilities.dp(20);
            statusLayout = null;
        }

        int avatarLeft;
        if (LocaleController.isRTL) {
            avatarLeft = getMeasuredWidth() - AndroidUtilities.dp(57) - getPaddingRight();
        } else {
            avatarLeft = AndroidUtilities.dp(11) + getPaddingLeft();
        }
        avatarRect.set(avatarLeft, AndroidUtilities.dp(7), avatarLeft + AndroidUtilities.dp(46), AndroidUtilities.dp(7) + AndroidUtilities.dp(46));

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft += (nameWidth - widthpx);
                    }
                }
            }
            if (statusLayout != null && statusLayout.getLineCount() > 0) {
                left = statusLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(statusLayout.getLineWidth(0));
                    if (widthpx < statusWidth) {
                        statusLeft += (statusWidth - widthpx);
                    }
                }
            }
        } else {
            if (nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineRight(0);
                if (left == nameWidth) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft -= (nameWidth - widthpx);
                    }
                }
            }
            if (statusLayout != null && statusLayout.getLineCount() > 0) {
                left = statusLayout.getLineRight(0);
                if (left == statusWidth) {
                    widthpx = Math.ceil(statusLayout.getLineWidth(0));
                    if (widthpx < statusWidth) {
                        statusLeft -= (statusWidth - widthpx);
                    }
                }
            }
        }

        nameLeft += getPaddingLeft();
        statusLeft += getPaddingLeft();
        nameLockLeft += getPaddingLeft();
    }

    public void update(int mask) {
        if (item != null) {
            item.fillAvatarDrawable(avatarDrawable);
        }
        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            buildLayout();
        } else {
            requestLayout();
        }
        postInvalidate();
    }

    @Override
    public void updateColors() {
        if (nameLayout != null && getMeasuredWidth() > 0) {
            buildLayout();
        }
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox == null) {
            return;
        }
        checkBox.setChecked(checked, animated);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (item == null) {
            return;
        }

        if (useSeparator) {
            Paint dividerPaint = Theme.dividerPaint;
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, dividerPaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, dividerPaint);
            }
        }

        if (drawNameLock) {
            setDrawableBounds(Theme.dialogs_lockDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_lockDrawable.draw(canvas);
        }

        if (nameLayout != null) {
            canvas.save();
            canvas.translate(nameLeft, nameTop);
            nameLayout.draw(canvas);
            canvas.restore();

            if (drawCheck) {
                // upstream statusDrawable = CombinedDrawable(verifiedDrawable, verifiedCheckDrawable)
                Drawable verified = Theme.dialogs_verifiedDrawable;
                int x;
                if (LocaleController.isRTL) {
                    if (nameLayout.getLineLeft(0) == 0) {
                        x = nameLeft - AndroidUtilities.dp(3) - verified.getIntrinsicWidth();
                    } else {
                        float w = nameLayout.getLineWidth(0);
                        x = (int) (nameLeft + nameWidth - Math.ceil(w) - AndroidUtilities.dp(3) - verified.getIntrinsicWidth());
                    }
                } else {
                    x = (int) (nameLeft + nameLayout.getLineRight(0) + AndroidUtilities.dp(6));
                }
                float y = nameTop + (nameLayout.getHeight() - verified.getIntrinsicHeight()) / 2f;
                setDrawableBounds(verified, x, y);
                verified.draw(canvas);
                setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, x, y);
                Theme.dialogs_verifiedCheckDrawable.draw(canvas);
            }
        }

        if (statusLayout != null) {
            canvas.save();
            canvas.translate(statusLeft + sublabelOffsetX, AndroidUtilities.dp(33) + sublabelOffsetY);
            statusLayout.draw(canvas);
            canvas.restore();
        }

        if (countLayout != null) {
            int x = countLeft - AndroidUtilities.dp(5.5f);
            rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(11), countTop + AndroidUtilities.dp(23));
            canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, item.muted ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint);
            canvas.save();
            canvas.translate(countLeft, countTop + AndroidUtilities.dp(4));
            countLayout.draw(canvas);
            canvas.restore();
        }

        drawAvatar(canvas);
    }

    /** Replaces upstream ImageReceiver + StoriesUtilities.drawAvatarWithStory (no story ring). */
    private void drawAvatar(Canvas canvas) {
        Bitmap bitmap = item.avatarBitmap;
        Drawable drawable = item.avatar;
        if (bitmap != null && !bitmap.isRecycled()) {
            if (avatarBitmapPaint == null) {
                avatarBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            }
            if (avatarShaderBitmap != bitmap) {
                avatarShaderBitmap = bitmap;
                avatarShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                avatarBitmapPaint.setShader(avatarShader);
            }
            float scale = Math.max(avatarRect.width() / bitmap.getWidth(), avatarRect.height() / bitmap.getHeight());
            avatarBitmapMatrix.reset();
            avatarBitmapMatrix.setScale(scale, scale);
            avatarBitmapMatrix.postTranslate(avatarRect.centerX() - bitmap.getWidth() * scale / 2f, avatarRect.centerY() - bitmap.getHeight() * scale / 2f);
            avatarShader.setLocalMatrix(avatarBitmapMatrix);
            canvas.drawCircle(avatarRect.centerX(), avatarRect.centerY(), avatarRect.width() / 2f, avatarBitmapPaint);
        } else if (drawable != null) {
            canvas.save();
            avatarClipPath.rewind();
            avatarClipPath.addCircle(avatarRect.centerX(), avatarRect.centerY(), avatarRect.width() / 2f, Path.Direction.CW);
            canvas.clipPath(avatarClipPath);
            drawable.setBounds((int) avatarRect.left, (int) avatarRect.top, (int) avatarRect.right, (int) avatarRect.bottom);
            drawable.draw(canvas);
            canvas.restore();
        } else {
            avatarDrawable.setBounds((int) avatarRect.left, (int) avatarRect.top, (int) avatarRect.right, (int) avatarRect.bottom);
            avatarDrawable.draw(canvas);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        StringBuilder builder = new StringBuilder();
        if (nameLayout != null) {
            builder.append(nameLayout.getText());
        }
        if (statusLayout != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(statusLayout.getText());
        }
        info.setText(builder.toString());
    }

    public long getDialogId() {
        return item != null ? item.id : 0;
    }
}
