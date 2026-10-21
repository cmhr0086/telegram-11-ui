/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CounterView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * tgui: upstream HintDialogCell (one avatar of the "People" row above the recent searches)
 * on a {@link DialogItem}: 54dp avatar, first name, unread counter and the online dot.
 * No premium lock.
 */
public class HintDialogCell extends FrameLayout {

    private BackupImageView imageView;
    private TextView nameTextView;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private Theme.ResourcesProvider resourcesProvider;
    private int lastUnreadCount;
    private DialogItem currentDialog;
    private long dialogId;

    float showOnlineProgress;
    boolean wasDraw;

    CounterView counterView;
    CheckBox2 checkBox;
    private final boolean drawCheckbox;

    public HintDialogCell(Context context, boolean drawCheckbox, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.drawCheckbox = drawCheckbox;
        this.resourcesProvider = resourcesProvider;

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(27));
        addView(imageView, LayoutHelper.createFrame(54, 54, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

        nameTextView = new TextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), AndroidUtilities.dp(10), false);
                super.setText(text, type);
            }
        };
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(1);
        nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nameTextView.setLines(1);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 64, 6, 0));

        counterView = new CounterView(context, resourcesProvider);
        addView(counterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, Gravity.TOP, 0, 4, 0, 0));
        counterView.setColors(Theme.key_chats_unreadCounterText, Theme.key_chats_unreadCounter);
        counterView.setGravity(Gravity.RIGHT);

        if (drawCheckbox) {
            checkBox = new CheckBox2(context, 21, resourcesProvider);
            checkBox.setColor(Theme.key_dialogRoundCheckBox, Theme.key_dialogBackground, Theme.key_dialogRoundCheckBoxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(4);
            checkBox.setProgressDelegate(progress -> {
                float scale = 1.0f - (1.0f - 0.857f) * checkBox.getProgress();
                imageView.setScaleX(scale);
                imageView.setScaleY(scale);
                invalidate();
            });
            addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 19, 42, 0, 0));
            checkBox.setChecked(false, false);
            setWillNotDraw(false);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86), MeasureSpec.EXACTLY));
        counterView.counterDrawable.horizontalPadding = AndroidUtilities.dp(13);
    }

    /** Upstream update(mask): refreshes the counter and the online dot from the current item. */
    public void update() {
        if (currentDialog == null) {
            return;
        }
        int unread = currentDialog.unreadCount;
        if (unread != lastUnreadCount) {
            lastUnreadCount = unread;
            counterView.setCount(unread, wasDraw);
        }
        imageView.invalidate();
        invalidate();
    }

    public void updateColors() {
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        counterView.invalidate();
        invalidate();
    }

    public void setColors(int textColorKey, int backgroundColorKey) {
        nameTextView.setTextColor(Theme.getColor(textColorKey, resourcesProvider));
        this.backgroundColorKey = backgroundColorKey;
        if (checkBox != null) {
            checkBox.setColor(Theme.key_dialogRoundCheckBox, backgroundColorKey, Theme.key_dialogRoundCheckBoxCheck);
        }
    }

    /**
     * Upstream setDialog(uid, counter, name).
     *
     * @param name null shows the first word of the title (upstream UserObject.getFirstName)
     */
    public void setDialog(DialogItem dialog, boolean counter, CharSequence name) {
        if (dialogId != dialog.id) {
            wasDraw = false;
            invalidate();
        }
        currentDialog = dialog;
        dialogId = dialog.id;
        if (name != null) {
            nameTextView.setText(name);
        } else if (dialog.title != null) {
            String title = dialog.title.toString().trim();
            int space = dialog.senderName == null && dialog.avatarType == DialogItem.AVATAR_NORMAL ? title.indexOf(' ') : -1;
            nameTextView.setText(space > 0 ? title.substring(0, space) : title);
        } else {
            nameTextView.setText("");
        }
        dialog.fillAvatarDrawable(avatarDrawable);
        if (dialog.avatarBitmap != null) {
            imageView.setImageDrawable(new BitmapDrawable(getResources(), dialog.avatarBitmap));
        } else if (dialog.avatar != null) {
            imageView.setImageDrawable(dialog.avatar);
        } else {
            imageView.setImageDrawable(avatarDrawable);
        }
        if (counter) {
            lastUnreadCount = dialog.unreadCount;
            counterView.setCount(lastUnreadCount, false);
        } else {
            lastUnreadCount = 0;
            counterView.setCount(0, false);
        }
    }

    private int backgroundColorKey = Theme.key_windowBackgroundWhite;

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == imageView) {
            boolean showOnline = currentDialog != null && currentDialog.online;
            if (!wasDraw) {
                showOnlineProgress = showOnline ? 1f : 0f;
            }
            if (showOnline && showOnlineProgress != 1f) {
                showOnlineProgress += 16f / 150;
                if (showOnlineProgress > 1) {
                    showOnlineProgress = 1f;
                }
                invalidate();
            } else if (!showOnline && showOnlineProgress != 0) {
                showOnlineProgress -= 16f / 150;
                if (showOnlineProgress < 0) {
                    showOnlineProgress = 0;
                }
                invalidate();
            }
            if (showOnlineProgress != 0) {
                int top = AndroidUtilities.dp(53);
                int left = AndroidUtilities.dp(59);
                canvas.save();
                canvas.scale(showOnlineProgress, showOnlineProgress, left, top);
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(backgroundColorKey));
                canvas.drawCircle(left, top, AndroidUtilities.dp(7), Theme.dialogs_onlineCirclePaint);
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
                canvas.drawCircle(left, top, AndroidUtilities.dp(5), Theme.dialogs_onlineCirclePaint);
                canvas.restore();
            }
            wasDraw = true;
        }
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawCheckbox) {
            int cx = imageView.getLeft() + imageView.getMeasuredWidth() / 2;
            int cy = imageView.getTop() + imageView.getMeasuredHeight() / 2;
            Theme.checkboxSquare_checkPaint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBox));
            Theme.checkboxSquare_checkPaint.setAlpha((int) (checkBox.getProgress() * 255));
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(28), Theme.checkboxSquare_checkPaint);
        }
    }

    public void setChecked(boolean checked, boolean animated) {
        if (drawCheckbox) {
            checkBox.setChecked(checked, animated);
        }
    }

    public DialogItem getDialog() {
        return currentDialog;
    }

    public long getDialogId() {
        return dialogId;
    }
}
