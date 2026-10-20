/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.HapticFeedbackConstants;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyStubSpan;
import org.telegram.ui.Components.PullForegroundDrawable;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.StatusDrawable;

/**
 * tgui: model-driven rewrite of upstream 11.4.2 {@code ui/Cells/DialogCell.java} (5689 lines).
 * The data layer (TLRPC.Dialog, MessageObject, User/Chat, MessagesController, drafts, forum topics,
 * stories, emoji status, thumbnails, tags, spoilers) is replaced by {@link DialogItem}. Transplanted
 * verbatim from upstream, for the classic (non-forum) layout:
 * <ul>
 * <li>{@code buildLayout()}: text sizes, message format types 1/2, draft / sender prefix spans,
 *     time / name / check / mute / verified / counter / mention / pin geometry, two- and three-line
 *     ({@link SharedConfig#useThreeLinesLayout}) variants, RTL;</li>
 * <li>{@code onDraw()}: swipe-reveal background, Lottie action icon and label, pinned overlay, name,
 *     time, sender line, message ↔ typing cross-fade with {@code Theme.getChatStatusDrawable},
 *     check-status animation, mute / verified icons, reorder handle, error badge, {@code drawCounter},
 *     mention badge, pin icon, avatar, online dot ({@code drawAvatarOverlays}), separator;</li>
 * <li>{@code setTranslationX()} / reveal / bounce progress (swipe actions), {@code setChecked()}
 *     (CheckBox2 over the avatar), {@code animateArchiveAvatar()}, the unread counter change
 *     animation from {@code update()} and the typing animation from {@code DialogUpdateHelper}.</li>
 * </ul>
 */
public class DialogCell extends BaseCell implements NotificationCenter.NotificationCenterDelegate {

    // Upstream SwipeGestureSettingsView.SWIPE_GESTURE_* values.
    public static final int SWIPE_ACTION_PIN = 0;
    public static final int SWIPE_ACTION_READ = 1;
    public static final int SWIPE_ACTION_ARCHIVE = 2;
    public static final int SWIPE_ACTION_MUTE = 3;
    public static final int SWIPE_ACTION_DELETE = 4;
    /** Archive folder row: "Hide" / "Pin" the archive (upstream HideOnTop / UnhideFromTop). */
    public static final int SWIPE_ACTION_FOLDER = 100;
    /** Inside the archive: unarchive. */
    public static final int SWIPE_ACTION_UNARCHIVE = 101;

    public int avatarStart = 10;
    public int messagePaddingStart = 72;
    public int heightDefault = 72;
    public int heightThreeLines = 78;
    public float chekBoxPaddingTop = 42;

    public boolean useSeparator;
    public boolean fullSeparator;

    private DialogItem item;
    private long currentDialogId;
    private boolean useForceThreeLines;
    private final Theme.ResourcesProvider resourcesProvider;

    private int paintIndex;

    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final RectF avatarRect = new RectF();
    private final Path avatarClipPath = new Path();
    private Paint avatarBitmapPaint;
    private final Matrix avatarBitmapMatrix = new Matrix();
    private Bitmap avatarShaderBitmap;
    private BitmapShader avatarShader;

    private final RectF rect = new RectF();

    private int nameLeft;
    private StaticLayout nameLayout;
    private int nameWidth;
    private int nameMuteLeft;
    private int nameLockLeft;
    private int nameLockTop;
    private boolean drawNameLock;
    private boolean drawVerified;

    private int timeLeft;
    private int timeTop;
    private StaticLayout timeLayout;

    private int messageTop;
    private int messageLeft;
    private StaticLayout messageLayout;
    private TextPaintRef currentMessagePaint = new TextPaintRef();

    private int messageNameTop;
    private int messageNameLeft;
    private StaticLayout messageNameLayout;

    private int typingLeft;
    private StaticLayout typingLayout;
    private int printingStringType = -1;
    private int statusDrawableLeft;

    private boolean drawCheck1;
    private boolean drawCheck2;
    private boolean drawClock;
    private int checkDrawLeft;
    private int checkDrawLeft1;
    private int clockDrawLeft;
    private int checkDrawTop;
    private int halfCheckDrawLeft;

    private int lastStatusDrawableParams = -1;
    private boolean statusDrawableAnimationInProgress;
    private ValueAnimator statusDrawableAnimator;
    private float statusDrawableProgress;
    private int animateToStatusDrawableParams;
    private int animateFromStatusDrawableParams;

    private boolean drawError;
    private int errorTop;
    private int errorLeft;

    private boolean drawPin;
    private int pinTop;
    private int pinLeft;

    private boolean drawCount;
    private boolean drawCount2 = true;
    private int countTop;
    private int countLeft;
    private int countWidth;
    private StaticLayout countLayout;

    private boolean drawMention;
    private int mentionLeft;
    private int mentionWidth;
    private StaticLayout mentionLayout;

    private int unreadCount;
    private boolean markUnread;
    private boolean dialogMuted;
    private float dialogMutedProgress;

    private ValueAnimator countAnimator;
    private float countChangeProgress = 1f;
    private StaticLayout countOldLayout;
    private StaticLayout countAnimationStableLayout;
    private StaticLayout countAnimationInLayout;
    private int countWidthOld;
    private int countLeftOld;
    private boolean countAnimationIncrement;

    private boolean isOnline;
    private float onlineProgress;

    // typing (upstream DialogUpdateHelper)
    private float typingProgres;
    private boolean typingOutToTop;
    private int lastKnownTypingType = -1;
    private Integer lastDrawnPrintingType;
    private int lastDrawnMessageHash;

    // selection / reorder
    private CheckBox2 checkBox;
    private boolean drawReorder;
    private float reorderIconProgress;
    private boolean isSelected;

    // archive avatar
    private boolean animatingArchiveAvatar;
    private float animatingArchiveAvatarProgress;
    private final CubicBezierInterpolator interpolator = CubicBezierInterpolator.EASE_OUT;

    // swipe (upstream DialogCell + DialogsActivity.SwipeController)
    private float translationX;
    private boolean isSliding;
    private boolean swipeCanceled;
    private boolean translationAnimationStarted;
    private RLottieDrawable translationDrawable;
    private RLottieDrawable lastDrawTranslationDrawable;
    private int lastDrawSwipeMessageStringId;
    private int swipeMessageTextId;
    private int swipeMessageWidth;
    private StaticLayout swipeMessageTextLayout;
    private boolean drawRevealBackground;
    private float currentRevealProgress;
    private float currentRevealBounceProgress;
    private float cornerProgress;
    private int swipeAction = SWIPE_ACTION_ARCHIVE;
    private boolean archiveHidden;
    private float archiveBackgroundProgress;
    /** Upstream: the pull-to-reveal header drawn by the archive row while the archive is hidden. */
    protected PullForegroundDrawable archivedChatsDrawable;

    private boolean updateLayout;
    private int lastSize;

    // DialogsItemAnimator support
    private boolean moving;
    private float clipProgress;
    private int topClip;
    private int bottomClip;

    public DialogCell(Context context) {
        this(context, false, null);
    }

    public DialogCell(Context context, boolean forceThreeLines, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        Theme.createDialogsResources(context);
        useForceThreeLines = forceThreeLines;
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // tgui: upstream DialogsActivity redraws its rows on emojiLoaded; the cell does it itself here
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (translationDrawable != null) {
            translationDrawable.stop();
            translationDrawable.setProgress(0.0f);
            translationDrawable.setCallback(null);
            translationDrawable = null;
            translationAnimationStarted = false;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            invalidate();
        }
    }

    public DialogItem getDialog() {
        return item;
    }

    public long getDialogId() {
        return currentDialogId;
    }

    /**
     * Binds a model. {@code animated} enables the upstream counter, check-status and typing
     * transitions when the same dialog is rebound with new values.
     */
    public void setDialog(DialogItem dialog, boolean animated) {
        boolean sameDialog = item != null && dialog != null && currentDialogId == dialog.id;
        animated = animated && sameDialog;
        int oldUnreadCount = unreadCount;
        boolean oldMarkUnread = markUnread;

        item = dialog;
        currentDialogId = dialog != null ? dialog.id : 0;
        if (!sameDialog) {
            lastStatusDrawableParams = -1;
            if (statusDrawableAnimator != null) {
                statusDrawableAnimator.cancel();
            }
            statusDrawableAnimationInProgress = false;
            if (countAnimator != null) {
                countAnimator.cancel();
            }
            countChangeProgress = 1f;
            onlineProgress = dialog != null && dialog.online ? 1f : 0f;
            dialogMutedProgress = dialog != null && dialog.muted ? 1f : 0f;
        }
        if (dialog == null) {
            requestLayout();
            return;
        }
        unreadCount = dialog.unreadCount;
        markUnread = dialog.markUnread && dialog.unreadCount == 0;
        dialogMuted = dialog.muted;
        drawPin = dialog.pinned;
        isOnline = dialog.online;

        // avatar
        // users reach upstream AvatarDrawable as first + last name: split the title the same way
        String title = dialog.title != null ? dialog.title.toString().trim() : "";
        int space = title.lastIndexOf(' ');
        if (space > 0) {
            avatarDrawable.setInfo(dialog.id, title.substring(0, space), title.substring(space + 1));
        } else {
            avatarDrawable.setInfo(dialog.id, title, null);
        }
        if (dialog.isFolder || dialog.avatarType == DialogItem.AVATAR_ARCHIVED) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ARCHIVED);
        } else if (dialog.avatarType != DialogItem.AVATAR_NORMAL) {
            avatarDrawable.setAvatarType(dialog.avatarType);
        }
        if (dialog.isFolder && archivedChatsDrawable != null) {
            archivedChatsDrawable.setCell(this);
        }
        resetPinnedArchiveState();

        // typing (DialogUpdateHelper.update)
        Integer printingType = dialog.typing != null ? dialog.typingType : null;
        int messageHash = (dialog.message != null ? dialog.message.toString().hashCode() : 0) + (dialog.time != null ? dialog.time.hashCode() : 0);
        if (!sameDialog) {
            typingProgres = printingType == null ? 0f : 1f;
        } else if (!java.util.Objects.equals(lastDrawnPrintingType, printingType)) {
            typingOutToTop = lastDrawnMessageHash == messageHash;
        }
        if (!animated) {
            typingProgres = printingType == null ? 0f : 1f;
        }
        if (printingType != null) {
            lastKnownTypingType = printingType;
        }
        lastDrawnPrintingType = printingType;
        lastDrawnMessageHash = messageHash;

        if (getMeasuredWidth() != 0) {
            buildLayout();
        } else {
            updateLayout = true;
        }

        if (animated && (oldUnreadCount != unreadCount || oldMarkUnread != markUnread)) {
            startCountAnimation(oldUnreadCount, oldMarkUnread);
        }
        requestLayout();
        invalidate();
    }

    private void startCountAnimation(int oldUnreadCount, boolean oldMarkUnread) {
        if (countAnimator != null) {
            countAnimator.cancel();
        }
        countChangeProgress = 0f;
        countAnimator = ValueAnimator.ofFloat(0, 1f);
        countAnimator.addUpdateListener(valueAnimator -> {
            countChangeProgress = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        countAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                countChangeProgress = 1f;
                countOldLayout = null;
                countAnimationStableLayout = null;
                countAnimationInLayout = null;
                invalidate();
            }
        });
        if ((oldUnreadCount == 0 || markUnread) && !(!markUnread && oldMarkUnread)) {
            countAnimator.setDuration(220);
            countAnimator.setInterpolator(new OvershootInterpolator());
        } else if (unreadCount == 0) {
            countAnimator.setDuration(150);
            countAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        } else {
            countAnimator.setDuration(430);
            countAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        }
        if (drawCount && drawCount2 && countLayout != null) {
            String oldStr = String.format("%d", oldUnreadCount);
            String newStr = String.format("%d", unreadCount);

            if (oldStr.length() == newStr.length()) {
                SpannableStringBuilder oldSpannableStr = new SpannableStringBuilder(oldStr);
                SpannableStringBuilder newSpannableStr = new SpannableStringBuilder(newStr);
                SpannableStringBuilder stableStr = new SpannableStringBuilder(newStr);
                for (int i = 0; i < oldStr.length(); i++) {
                    if (oldStr.charAt(i) == newStr.charAt(i)) {
                        oldSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                        newSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                    } else {
                        stableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                    }
                }

                int countOldWidth = Math.max(dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(oldStr)));
                countOldLayout = new StaticLayout(oldSpannableStr, Theme.dialogs_countTextPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                countAnimationStableLayout = new StaticLayout(stableStr, Theme.dialogs_countTextPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                countAnimationInLayout = new StaticLayout(newSpannableStr, Theme.dialogs_countTextPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
            } else {
                countOldLayout = countLayout;
            }
        }
        countWidthOld = countWidth;
        countLeftOld = countLeft;
        countAnimationIncrement = unreadCount > oldUnreadCount;
        countAnimator.start();
    }

    private boolean isThreeLines() {
        return useForceThreeLines || SharedConfig.useThreeLinesLayout;
    }

    private boolean isFolder() {
        return item != null && item.isFolder;
    }

    public boolean getIsPinned() {
        return drawPin;
    }

    public boolean getIsMuted() {
        return dialogMuted;
    }

    public boolean getHasUnread() {
        return unreadCount != 0 || markUnread;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (checkBox != null) {
            checkBox.measure(
                    MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY)
            );
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), getCollapsedHeight());
        topClip = 0;
        bottomClip = getMeasuredHeight();
    }

    public void setMoving(boolean moving) {
        this.moving = moving;
    }

    public boolean isMoving() {
        return moving;
    }

    public void setClipProgress(float value) {
        clipProgress = value;
        invalidate();
    }

    public float getClipProgress() {
        return clipProgress;
    }

    public void setTopClip(int value) {
        topClip = value;
    }

    public void setBottomClip(int value) {
        bottomClip = value;
    }

    private int getCollapsedHeight() {
        int height = dp(isThreeLines() ? heightThreeLines : heightDefault);
        if (useSeparator) {
            height += 1;
        }
        return height;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (item == null) {
            return;
        }
        if (checkBox != null) {
            int paddingStart = dp(messagePaddingStart - (isThreeLines() ? 29 : 27));
            int x = LocaleController.isRTL ? (right - left) - paddingStart : paddingStart;
            int y = dp(chekBoxPaddingTop + (isThreeLines() ? 6 : 0));
            checkBox.layout(x, y, x + checkBox.getMeasuredWidth(), y + checkBox.getMeasuredHeight());
        }
        int size = getMeasuredHeight() + getMeasuredWidth() << 16;
        if (size != lastSize || updateLayout) {
            updateLayout = false;
            lastSize = size;
            try {
                buildLayout();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private SpannableStringBuilder formatInternal(int messageFormatType, CharSequence s1, CharSequence s2) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        switch (messageFormatType) {
            case 1:
                //"%2$s: ⁨%1$s⁩"
                spannableStringBuilder.append(s2).append(": ⁨").append(s1).append("⁩");
                break;
            case 2:
                //"⁨%1$s⁩"
                spannableStringBuilder.append("⁨").append(s1).append("⁩");
                break;
            case 3:
                //"%2$s: %1$s"
                spannableStringBuilder.append(s2).append(": ").append(s1);
                break;
            case 4:
                //"%1$s"
                spannableStringBuilder.append(s1);
                break;
        }
        return spannableStringBuilder;
    }

    public void buildLayout() {
        if (item == null || getMeasuredWidth() == 0) {
            return;
        }
        final boolean threeLines = isThreeLines();
        if (threeLines) {
            Theme.dialogs_namePaint[0].setTextSize(dp(17));
            Theme.dialogs_nameEncryptedPaint[0].setTextSize(dp(17));
            Theme.dialogs_messagePaint[0].setTextSize(dp(16));
            Theme.dialogs_messagePrintingPaint[0].setTextSize(dp(16));

            Theme.dialogs_namePaint[1].setTextSize(dp(16));
            Theme.dialogs_nameEncryptedPaint[1].setTextSize(dp(16));
            Theme.dialogs_messagePaint[1].setTextSize(dp(15));
            Theme.dialogs_messagePrintingPaint[1].setTextSize(dp(15));

            Theme.dialogs_messagePaint[1].setColor(Theme.dialogs_messagePaint[1].linkColor = Theme.getColor(Theme.key_chats_message_threeLines, resourcesProvider));
            paintIndex = 1;
        } else {
            Theme.dialogs_namePaint[0].setTextSize(dp(17));
            Theme.dialogs_nameEncryptedPaint[0].setTextSize(dp(17));
            Theme.dialogs_messagePaint[0].setTextSize(dp(16));
            Theme.dialogs_messagePrintingPaint[0].setTextSize(dp(16));

            Theme.dialogs_messagePaint[0].setColor(Theme.dialogs_messagePaint[0].linkColor = Theme.getColor(Theme.key_chats_message, resourcesProvider));
            paintIndex = 0;
        }

        CharSequence nameString;
        String timeString = item.time != null ? item.time : "";
        String countString = null;
        String mentionString = null;
        CharSequence messageString;
        CharSequence typingString = "";
        CharSequence messageNameString = null;
        currentMessagePaint.paint = Theme.dialogs_messagePaint[paintIndex];
        boolean checkMessage = true;
        boolean isDraft = false;

        drawNameLock = false;
        drawVerified = false;
        printingStringType = -1;
        int printingStringReplaceIndex = -1;

        int messageFormatType;
        if (!threeLines || isFolder()) {
            //1 - "%2$s: ⁨%1$s⁩";
            messageFormatType = 1;
        } else {
            //2 - "⁨%1$s⁩";
            messageFormatType = 2;
        }

        if (threeLines) {
            nameLeft = !LocaleController.isRTL ? dp(messagePaddingStart + 6) : dp(22);
        } else {
            nameLeft = !LocaleController.isRTL ? dp(messagePaddingStart + 4) : dp(18);
        }
        if (item.secret && !isFolder()) {
            drawNameLock = true;
            if (threeLines) {
                nameLockTop = dp(12.5f);
                if (!LocaleController.isRTL) {
                    nameLockLeft = dp(messagePaddingStart + 6);
                    nameLeft = dp(messagePaddingStart + 10) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
                } else {
                    nameLockLeft = getMeasuredWidth() - dp(messagePaddingStart + 6) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                    nameLeft = dp(22);
                }
            } else {
                nameLockTop = dp(16.5f);
                if (!LocaleController.isRTL) {
                    nameLockLeft = dp(messagePaddingStart + 4);
                    nameLeft = dp(messagePaddingStart + 8) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
                } else {
                    nameLockLeft = getMeasuredWidth() - dp(messagePaddingStart + 4) - Theme.dialogs_lockDrawable.getIntrinsicWidth();
                    nameLeft = dp(18);
                }
            }
        } else if (!isFolder()) {
            drawVerified = item.verified;
        }

        // printing string
        if (item.typing != null) {
            printingStringType = item.typingType;
            StatusDrawable statusDrawable = Theme.getChatStatusDrawable(printingStringType);
            int startPadding = 0;
            if (statusDrawable != null) {
                startPadding = statusDrawable.getIntrinsicWidth() + dp(3);
            }
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            CharSequence printingString = TextUtils.replace(item.typing, new String[]{"..."}, new String[]{""});
            if (printingStringType == 5) {
                printingStringReplaceIndex = printingString.toString().indexOf("**oo**");
            }
            if (printingStringReplaceIndex >= 0) {
                spannableStringBuilder.append(printingString).setSpan(new SimpleTextView.FixedWidthSpan(Theme.getChatStatusDrawable(printingStringType).getIntrinsicWidth()), printingStringReplaceIndex, printingStringReplaceIndex + 6, 0);
            } else {
                spannableStringBuilder.append(" ").append(printingString).setSpan(new SimpleTextView.FixedWidthSpan(startPadding), 0, 1, 0);
            }
            typingString = spannableStringBuilder;
            checkMessage = false;
        }

        if (item.draft != null) {
            checkMessage = false;
            isDraft = true;
            messageNameString = LocaleController.getString(R.string.Draft);
            if (TextUtils.isEmpty(item.draft)) {
                if (threeLines) {
                    messageString = "";
                } else {
                    SpannableStringBuilder stringBuilder = SpannableStringBuilder.valueOf(messageNameString);
                    stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_draft, resourcesProvider), 0, messageNameString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    messageString = stringBuilder;
                }
            } else {
                CharSequence mess = item.draft;
                if (mess.length() > 150) {
                    mess = mess.subSequence(0, 150);
                }
                SpannableStringBuilder stringBuilder = formatInternal(messageFormatType, AndroidUtilities.replaceNewLines(new SpannableStringBuilder(mess)), messageNameString);
                if (!threeLines) {
                    stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_draft, resourcesProvider), 0, messageNameString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), dp(20), false);
            }
        } else if (item.senderName != null) {
            messageNameString = item.senderName;
            checkMessage = false;
            CharSequence mess = item.message != null ? item.message : "";
            if (mess.length() > 150) {
                mess = mess.subSequence(0, 150);
            }
            mess = AndroidUtilities.replaceNewLines(mess);
            SpannableStringBuilder stringBuilder = formatInternal(messageFormatType, mess, messageNameString);
            if (item.mediaPreview) {
                currentMessagePaint.paint = Theme.dialogs_messagePrintingPaint[paintIndex];
                stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_attachMessage, resourcesProvider), messageFormatType == 1 || messageFormatType == 3 ? messageNameString.length() + 2 : 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!threeLines || isFolder() && stringBuilder.length() > 0) {
                stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_nameMessage, resourcesProvider), 0, messageNameString.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), dp(20), false);
        } else if (item.mediaPreview) {
            currentMessagePaint.paint = Theme.dialogs_messagePrintingPaint[paintIndex];
            CharSequence mess = item.message != null ? item.message : "";
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder(AndroidUtilities.replaceNewLines(mess));
            stringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_chats_attachMessage, resourcesProvider), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            messageString = Emoji.replaceEmoji(stringBuilder, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), dp(20), false);
            checkMessage = false;
        } else {
            messageString = item.message != null ? item.message : "";
        }

        if (isFolder()) {
            if (unreadCount + (item.mention ? 1 : 0) > 0 || markUnread) {
                drawCount = true;
                drawMention = false;
                countString = markUnread ? "" : String.format("%d", unreadCount);
            } else {
                drawCount = false;
                drawMention = false;
            }
        } else {
            if (unreadCount != 0) {
                drawCount = true;
                countString = String.format("%d", unreadCount);
            } else if (markUnread) {
                drawCount = true;
                countString = "";
            } else {
                drawCount = false;
            }
            if (item.mention) {
                drawMention = true;
                mentionString = "@";
            } else {
                drawMention = false;
            }
        }

        if (!isDraft && !isFolder() && item.sentState != DialogItem.SENT_NONE) {
            if (item.sentState == DialogItem.SENT_SENDING) {
                drawCheck1 = false;
                drawCheck2 = false;
                drawClock = true;
                drawError = false;
            } else if (item.sentState == DialogItem.SENT_ERROR) {
                drawCheck1 = false;
                drawCheck2 = false;
                drawClock = false;
                drawError = true;
                drawCount = false;
                drawMention = false;
            } else {
                drawCheck1 = item.sentState == DialogItem.SENT_READ;
                drawCheck2 = true;
                drawClock = false;
                drawError = false;
            }
        } else {
            drawCheck1 = false;
            drawCheck2 = false;
            drawClock = false;
            drawError = false;
        }

        nameString = item.title != null ? item.title : "";

        int timeWidth;
        if (!isFolder()) {
            timeWidth = (int) Math.ceil(Theme.dialogs_timePaint.measureText(timeString));
            timeLayout = new StaticLayout(timeString, Theme.dialogs_timePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (!LocaleController.isRTL) {
                timeLeft = getMeasuredWidth() - dp(15) - timeWidth;
            } else {
                timeLeft = dp(15);
            }
        } else {
            timeWidth = 0;
            timeLayout = null;
            timeLeft = 0;
        }
        int timeLeftOffset = 0;

        if (!LocaleController.isRTL) {
            nameWidth = getMeasuredWidth() - nameLeft - dp(14 + 8) - timeWidth;
        } else {
            nameWidth = getMeasuredWidth() - nameLeft - dp(messagePaddingStart + 5 + 8) - timeWidth;
            nameLeft += timeWidth;
        }
        if (drawNameLock) {
            nameWidth -= dp(LocaleController.isRTL ? 8 : 4) + Theme.dialogs_lockDrawable.getIntrinsicWidth();
        }
        if (drawClock) {
            int w = Theme.dialogs_clockDrawable.getIntrinsicWidth() + dp(5);
            nameWidth -= w;
            if (!LocaleController.isRTL) {
                clockDrawLeft = timeLeft - timeLeftOffset - w;
            } else {
                clockDrawLeft = timeLeft + timeWidth + dp(5);
                nameLeft += w;
            }
        } else if (drawCheck2) {
            int w = Theme.dialogs_checkDrawable.getIntrinsicWidth() + dp(5);
            nameWidth -= w;
            if (drawCheck1) {
                nameWidth -= Theme.dialogs_halfCheckDrawable.getIntrinsicWidth() - dp(8);
                if (!LocaleController.isRTL) {
                    halfCheckDrawLeft = timeLeft - timeLeftOffset - w;
                    checkDrawLeft = halfCheckDrawLeft - dp(5.5f);
                } else {
                    checkDrawLeft = timeLeft + timeWidth + dp(5);
                    halfCheckDrawLeft = checkDrawLeft + dp(5.5f);
                    nameLeft += w + Theme.dialogs_halfCheckDrawable.getIntrinsicWidth() - dp(8);
                }
            } else {
                if (!LocaleController.isRTL) {
                    checkDrawLeft1 = timeLeft - timeLeftOffset - w;
                } else {
                    checkDrawLeft1 = timeLeft + timeWidth + dp(5);
                    nameLeft += w;
                }
            }
        }

        if (dialogMuted && !drawVerified) {
            int w = dp(6) + Theme.dialogs_muteDrawable.getIntrinsicWidth();
            nameWidth -= w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        } else if (drawVerified) {
            int w = dp(6) + Theme.dialogs_verifiedDrawable.getIntrinsicWidth();
            nameWidth -= w;
            if (LocaleController.isRTL) {
                nameLeft += w;
            }
        }
        try {
            int ellipsizeWidth = nameWidth - dp(12);
            if (ellipsizeWidth < 0) {
                ellipsizeWidth = 0;
            }
            if (nameString instanceof String) {
                nameString = ((String) nameString).replace('\n', ' ');
            }
            CharSequence nameStringFinal = TextUtils.ellipsize(nameString, Theme.dialogs_namePaint[paintIndex], ellipsizeWidth, TextUtils.TruncateAt.END);
            nameStringFinal = Emoji.replaceEmoji(nameStringFinal, Theme.dialogs_namePaint[paintIndex].getFontMetricsInt(), dp(20), false);
            nameLayout = new StaticLayout(nameStringFinal, Theme.dialogs_namePaint[paintIndex], Math.max(ellipsizeWidth, nameWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        int messageWidth;
        int avatarLeft;
        int avatarTop;
        int avatarSize;
        if (threeLines) {
            avatarTop = dp(11);
            messageNameTop = dp(32);
            timeTop = dp(13);
            errorTop = dp(43);
            pinTop = dp(43);
            countTop = dp(43);
            checkDrawTop = dp(13);
            messageWidth = getMeasuredWidth() - dp(messagePaddingStart + 21);

            if (LocaleController.isRTL) {
                typingLeft = messageLeft = messageNameLeft = dp(16);
                avatarLeft = getMeasuredWidth() - dp(56 + avatarStart);
            } else {
                typingLeft = messageLeft = messageNameLeft = dp(messagePaddingStart + 6);
                avatarLeft = dp(avatarStart);
            }
            avatarSize = dp(56);
        } else {
            avatarTop = dp(9);
            messageNameTop = dp(31);
            timeTop = dp(16);
            errorTop = dp(39);
            pinTop = dp(39);
            countTop = dp(39);
            checkDrawTop = dp(17);
            messageWidth = getMeasuredWidth() - dp(messagePaddingStart + 23 - (LocaleController.isRTL ? 0 : 12));

            if (LocaleController.isRTL) {
                typingLeft = messageLeft = messageNameLeft = dp(22);
                avatarLeft = getMeasuredWidth() - dp(54 + avatarStart);
            } else {
                typingLeft = messageLeft = messageNameLeft = dp(messagePaddingStart + 4);
                avatarLeft = dp(avatarStart);
            }
            avatarSize = dp(54);
        }
        avatarRect.set(avatarLeft, avatarTop, avatarLeft + avatarSize, avatarTop + avatarSize);
        if (getIsPinned()) {
            if (!LocaleController.isRTL) {
                pinLeft = getMeasuredWidth() - Theme.dialogs_pinnedDrawable.getIntrinsicWidth() - dp(14);
            } else {
                pinLeft = dp(14);
            }
        }
        if (drawError) {
            int w = dp(23 + 8);
            messageWidth -= w;
            if (!LocaleController.isRTL) {
                errorLeft = getMeasuredWidth() - dp(23 + 11);
            } else {
                errorLeft = dp(11);
                messageLeft += w;
                typingLeft += w;
                messageNameLeft += w;
            }
        } else if (countString != null || mentionString != null) {
            if (countString != null) {
                countWidth = Math.max(dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(countString)));
                countLayout = new StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                int w = countWidth + dp(18);
                messageWidth -= w;
                if (!LocaleController.isRTL) {
                    countLeft = getMeasuredWidth() - countWidth - dp(20);
                } else {
                    countLeft = dp(20);
                    messageLeft += w;
                    typingLeft += w;
                    messageNameLeft += w;
                }
                drawCount = true;
            } else {
                countWidth = 0;
            }
            if (mentionString != null) {
                if (isFolder()) {
                    mentionWidth = Math.max(dp(12), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(mentionString)));
                    mentionLayout = new StaticLayout(mentionString, Theme.dialogs_countTextPaint, mentionWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                } else {
                    mentionWidth = dp(12);
                    mentionLayout = null;
                }
                int w = mentionWidth + dp(18);
                messageWidth -= w;
                if (!LocaleController.isRTL) {
                    mentionLeft = getMeasuredWidth() - mentionWidth - dp(20) - (countWidth != 0 ? countWidth + dp(18) : 0);
                } else {
                    mentionLeft = dp(20) + (countWidth != 0 ? countWidth + dp(18) : 0);
                    messageLeft += w;
                    typingLeft += w;
                    messageNameLeft += w;
                }
                drawMention = true;
            } else {
                mentionWidth = 0;
            }
        } else {
            if (getIsPinned()) {
                int w = Theme.dialogs_pinnedDrawable.getIntrinsicWidth() + dp(8);
                messageWidth -= w;
                if (LocaleController.isRTL) {
                    messageLeft += w;
                    typingLeft += w;
                    messageNameLeft += w;
                }
            }
            drawCount = false;
            drawMention = false;
        }

        if (checkMessage) {
            if (messageString == null) {
                messageString = "";
            }
            CharSequence mess = messageString;
            if (mess.length() > 150) {
                mess = mess.subSequence(0, 150);
            }
            if (!threeLines || messageNameString != null) {
                mess = AndroidUtilities.replaceNewLines(mess);
            } else {
                mess = AndroidUtilities.replaceTwoNewLinesToOne(mess);
            }
            messageString = Emoji.replaceEmoji(mess, Theme.dialogs_messagePaint[paintIndex].getFontMetricsInt(), dp(17), false);
        }
        messageWidth = Math.max(dp(12), messageWidth);
        if (threeLines && messageNameString != null) {
            try {
                messageNameLayout = StaticLayoutEx.createStaticLayout(messageNameString, Theme.dialogs_messageNamePaint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false, TextUtils.TruncateAt.END, messageWidth, 1);
            } catch (Exception e) {
                FileLog.e(e);
            }
            messageTop = dp(32 + 19);
        } else {
            messageNameLayout = null;
            if (threeLines) {
                messageTop = dp(32);
            } else {
                messageTop = dp(39);
            }
        }

        try {
            if (!TextUtils.isEmpty(typingString)) {
                if (threeLines) {
                    typingLayout = StaticLayoutEx.createStaticLayout(typingString, Theme.dialogs_messagePrintingPaint[paintIndex], messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, dp(1), false, TextUtils.TruncateAt.END, messageWidth, 1);
                } else {
                    typingString = TextUtils.ellipsize(typingString, currentMessagePaint.paint, messageWidth - dp(12), TextUtils.TruncateAt.END);
                    typingLayout = new StaticLayout(typingString, Theme.dialogs_messagePrintingPaint[paintIndex], messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
            } else {
                typingLayout = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            CharSequence messageStringFinal;
            // Removing links and bold spans to get rid of underlining and boldness
            if (messageString instanceof Spannable) {
                Spannable messageStringSpannable = (Spannable) messageString;
                for (Object span : messageStringSpannable.getSpans(0, messageStringSpannable.length(), Object.class)) {
                    if (span instanceof ClickableSpan || (span instanceof StyleSpan && ((StyleSpan) span).getStyle() == android.graphics.Typeface.BOLD)) {
                        messageStringSpannable.removeSpan(span);
                    }
                }
            }
            if (!threeLines || messageNameString != null) {
                messageStringFinal = TextUtils.ellipsize(messageString, currentMessagePaint.paint, messageWidth - dp(12), TextUtils.TruncateAt.END);
            } else {
                messageStringFinal = messageString;
            }

            if (threeLines) {
                messageLayout = StaticLayoutEx.createStaticLayout(messageStringFinal, currentMessagePaint.paint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, dp(1), false, TextUtils.TruncateAt.END, messageWidth, messageNameString != null ? 1 : 2);
            } else {
                messageLayout = new StaticLayout(messageStringFinal, currentMessagePaint.paint, messageWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        } catch (Exception e) {
            messageLayout = null;
            FileLog.e(e);
        }

        double widthpx;
        float left;
        if (LocaleController.isRTL) {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                widthpx = Math.ceil(nameLayout.getLineWidth(0));
                nameLeft += dp(12);
                if (dialogMuted && !drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - dp(6) - Theme.dialogs_muteDrawable.getIntrinsicWidth());
                } else if (drawVerified) {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - dp(6) - Theme.dialogs_verifiedDrawable.getIntrinsicWidth());
                } else {
                    nameMuteLeft = (int) (nameLeft + (nameWidth - widthpx) - dp(6) - Theme.dialogs_muteDrawable.getIntrinsicWidth());
                }
                if (left == 0) {
                    if (widthpx < nameWidth) {
                        nameLeft += (nameWidth - widthpx);
                    }
                }
            }
            if (messageLayout != null) {
                int lineCount = messageLayout.getLineCount();
                if (lineCount > 0) {
                    int w = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = messageLayout.getLineLeft(a);
                        if (left == 0) {
                            widthpx = Math.ceil(messageLayout.getLineWidth(a));
                            w = Math.min(w, (int) (messageWidth - widthpx));
                        } else {
                            w = 0;
                            break;
                        }
                    }
                    if (w != Integer.MAX_VALUE) {
                        messageLeft += w;
                    }
                }
            }
            if (typingLayout != null) {
                int lineCount = typingLayout.getLineCount();
                if (lineCount > 0) {
                    int w = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = typingLayout.getLineLeft(a);
                        if (left == 0) {
                            widthpx = Math.ceil(typingLayout.getLineWidth(a));
                            w = Math.min(w, (int) (messageWidth - widthpx));
                        } else {
                            w = 0;
                            break;
                        }
                    }
                    if (w != Integer.MAX_VALUE) {
                        typingLeft += w;
                    }
                }
            }
            if (messageNameLayout != null && messageNameLayout.getLineCount() > 0) {
                left = messageNameLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(messageNameLayout.getLineWidth(0));
                    if (widthpx < messageWidth) {
                        messageNameLeft += (messageWidth - widthpx);
                    }
                }
            }
        } else {
            if (nameLayout != null && nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineRight(0);
                if (left == nameWidth) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft -= (nameWidth - widthpx);
                    }
                }
                nameMuteLeft = (int) (nameLeft + left + dp(6));
            }
            if (messageLayout != null) {
                int lineCount = messageLayout.getLineCount();
                if (lineCount > 0) {
                    left = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = Math.min(left, messageLayout.getLineLeft(a));
                    }
                    messageLeft -= left;
                }
            }
            if (typingLayout != null) {
                int lineCount = typingLayout.getLineCount();
                if (lineCount > 0) {
                    left = Integer.MAX_VALUE;
                    for (int a = 0; a < lineCount; a++) {
                        left = Math.min(left, typingLayout.getLineLeft(a));
                    }
                    typingLeft -= left;
                }
            }
            if (messageNameLayout != null && messageNameLayout.getLineCount() > 0) {
                messageNameLeft -= messageNameLayout.getLineLeft(0);
            }
        }
        if (typingLayout != null && printingStringType >= 0 && typingLayout.getText().length() > 0) {
            float x1, x2;
            if (printingStringReplaceIndex >= 0 && printingStringReplaceIndex + 1 < typingLayout.getText().length()) {
                x1 = typingLayout.getPrimaryHorizontal(printingStringReplaceIndex);
                x2 = typingLayout.getPrimaryHorizontal(printingStringReplaceIndex + 1);
            } else {
                x1 = typingLayout.getPrimaryHorizontal(0);
                x2 = typingLayout.getPrimaryHorizontal(1);
            }
            if (x1 < x2) {
                statusDrawableLeft = (int) (typingLeft + x1);
            } else {
                statusDrawableLeft = (int) (typingLeft + x2 + dp(3));
            }
        }
    }

    private void drawCheckStatus(Canvas canvas, boolean drawClock, boolean drawCheck1, boolean drawCheck2, boolean moveCheck, float alpha) {
        if (alpha == 0 && !moveCheck) {
            return;
        }
        float scale = 0.5f + 0.5f * alpha;
        if (drawClock) {
            setDrawableBounds(Theme.dialogs_clockDrawable, clockDrawLeft, checkDrawTop);
            if (alpha != 1f) {
                canvas.save();
                canvas.scale(scale, scale, Theme.dialogs_clockDrawable.getBounds().centerX(), Theme.dialogs_halfCheckDrawable.getBounds().centerY());
                Theme.dialogs_clockDrawable.setAlpha((int) (255 * alpha));
            }
            Theme.dialogs_clockDrawable.draw(canvas);
            if (alpha != 1f) {
                canvas.restore();
                Theme.dialogs_clockDrawable.setAlpha(255);
            }
            invalidate();
        } else if (drawCheck2) {
            if (drawCheck1) {
                setDrawableBounds(Theme.dialogs_halfCheckDrawable, halfCheckDrawLeft, checkDrawTop);
                if (moveCheck) {
                    canvas.save();
                    canvas.scale(scale, scale, Theme.dialogs_halfCheckDrawable.getBounds().centerX(), Theme.dialogs_halfCheckDrawable.getBounds().centerY());
                    Theme.dialogs_halfCheckDrawable.setAlpha((int) (255 * alpha));
                }
                if (!moveCheck && alpha != 0) {
                    canvas.save();
                    canvas.scale(scale, scale, Theme.dialogs_halfCheckDrawable.getBounds().centerX(), Theme.dialogs_halfCheckDrawable.getBounds().centerY());
                    Theme.dialogs_halfCheckDrawable.setAlpha((int) (255 * alpha));
                    Theme.dialogs_checkReadDrawable.setAlpha((int) (255 * alpha));
                }

                Theme.dialogs_halfCheckDrawable.draw(canvas);

                if (moveCheck) {
                    canvas.restore();
                    canvas.save();
                    canvas.translate(dp(4) * (1f - alpha), 0);
                }
                setDrawableBounds(Theme.dialogs_checkReadDrawable, checkDrawLeft, checkDrawTop);
                Theme.dialogs_checkReadDrawable.draw(canvas);
                if (moveCheck) {
                    canvas.restore();
                    Theme.dialogs_halfCheckDrawable.setAlpha(255);
                }

                if (!moveCheck && alpha != 0) {
                    canvas.restore();
                    Theme.dialogs_halfCheckDrawable.setAlpha(255);
                    Theme.dialogs_checkReadDrawable.setAlpha(255);
                }
            } else {
                setDrawableBounds(Theme.dialogs_checkDrawable, checkDrawLeft1, checkDrawTop);
                if (alpha != 1f) {
                    canvas.save();
                    canvas.scale(scale, scale, Theme.dialogs_checkDrawable.getBounds().centerX(), Theme.dialogs_halfCheckDrawable.getBounds().centerY());
                    Theme.dialogs_checkDrawable.setAlpha((int) (255 * alpha));
                }
                Theme.dialogs_checkDrawable.draw(canvas);
                if (alpha != 1f) {
                    canvas.restore();
                    Theme.dialogs_checkDrawable.setAlpha(255);
                }
            }
        }
    }

    private void createStatusDrawableAnimator(int lastStatusDrawableParams, int currentStatus) {
        statusDrawableProgress = 0f;
        statusDrawableAnimator = ValueAnimator.ofFloat(0, 1f);
        statusDrawableAnimator.setDuration(220);

        statusDrawableAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animateFromStatusDrawableParams = lastStatusDrawableParams;
        animateToStatusDrawableParams = currentStatus;
        statusDrawableAnimator.addUpdateListener(valueAnimator -> {
            statusDrawableProgress = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        statusDrawableAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                int currentStatus = (DialogCell.this.drawClock ? 1 : 0) + (DialogCell.this.drawCheck1 ? 2 : 0) + (DialogCell.this.drawCheck2 ? 4 : 0);
                if (animateToStatusDrawableParams != currentStatus) {
                    createStatusDrawableAnimator(animateToStatusDrawableParams, currentStatus);
                } else {
                    statusDrawableAnimationInProgress = false;
                    DialogCell.this.lastStatusDrawableParams = animateToStatusDrawableParams;
                }
                invalidate();
            }
        });
        statusDrawableAnimationInProgress = true;
        statusDrawableAnimator.start();
    }

    public boolean isPointInsideAvatar(float x, float y) {
        if (!LocaleController.isRTL) {
            return x >= 0 && x < dp(60);
        } else {
            return x >= getMeasuredWidth() - dp(60) && x < getMeasuredWidth();
        }
    }

    /** Tablet-style selected highlight (upstream setDialogSelected). */
    public void setDialogSelected(boolean value) {
        if (isSelected != value) {
            invalidate();
        }
        isSelected = value;
    }

    /** Upstream: snaps the archive row's pinned/hidden look to {@link SharedConfig#archiveHidden}. */
    public void resetPinnedArchiveState() {
        archiveHidden = SharedConfig.archiveHidden;
        archiveBackgroundProgress = archiveHidden ? 0.0f : 1.0f;
        avatarDrawable.setArchivedAvatarHiddenProgress(archiveBackgroundProgress);
    }

    /** Upstream: the list's pull header, drawn by the archive row (see {@link org.telegram.ui.Components.DialogsListView}). */
    public void setArchivedPullAnimation(PullForegroundDrawable drawable) {
        archivedChatsDrawable = drawable;
        if (drawable != null && isFolder()) {
            drawable.setCell(this);
        }
    }

    /** Upstream: the pull header collapses into the archive avatar once the archive is revealed. */
    public void startOutAnimation() {
        if (archivedChatsDrawable != null) {
            archivedChatsDrawable.outCy = avatarRect.centerY();
            archivedChatsDrawable.outCx = avatarRect.centerX();
            archivedChatsDrawable.outRadius = avatarRect.width() / 2.0f;
            archivedChatsDrawable.outImageSize = avatarRect.width();
            archivedChatsDrawable.startOutAnimation();
        }
    }

    public boolean isFolderCell() {
        return isFolder();
    }

    public void animateArchiveAvatar() {
        if (avatarDrawable.getAvatarType() != AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
            return;
        }
        animatingArchiveAvatar = true;
        animatingArchiveAvatarProgress = 0.0f;
        Theme.dialogs_archiveAvatarDrawable.setProgress(0.0f);
        Theme.dialogs_archiveAvatarDrawable.start();
        invalidate();
    }

    /** Selection mode: upstream CheckBox2 (size 21, background drawn as an arc) over the avatar. */
    public void setChecked(boolean checked, boolean animated) {
        if (checkBox == null && !checked) {
            return;
        }
        if (checkBox == null) {
            checkBox = new CheckBox2(getContext(), 21, resourcesProvider) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    DialogCell.this.invalidate();
                }
            };
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox);
        }
        checkBox.setChecked(checked, animated);
    }

    public boolean isChecked() {
        return checkBox != null && checkBox.isChecked();
    }

    /** Upstream onReorderStateChanged: the reorder handle replaces the pin icon of pinned rows. */
    public void onReorderStateChanged(boolean reordering, boolean animated) {
        if (!getIsPinned() && reordering || drawReorder == reordering) {
            if (!getIsPinned()) {
                drawReorder = false;
            }
            return;
        }
        drawReorder = reordering;
        if (animated) {
            reorderIconProgress = drawReorder ? 0.0f : 1.0f;
        } else {
            reorderIconProgress = drawReorder ? 1.0f : 0.0f;
        }
        invalidate();
    }

    // ---- swipe (driven by DialogsSwipeController) ----

    /** Which action the swipe-reveal shows (SWIPE_ACTION_*). */
    public void setSwipeAction(int action) {
        swipeAction = action;
    }

    public int getSwipeAction() {
        return swipeAction;
    }

    public void setSliding(boolean value) {
        isSliding = value;
    }

    public void setSwipeCanceled(boolean value) {
        swipeCanceled = value;
    }

    public boolean isRevealed() {
        return drawRevealBackground;
    }

    @Override
    public float getTranslationX() {
        return translationX;
    }

    @Override
    public void setTranslationX(float value) {
        if (value == translationX) {
            return;
        }
        translationX = value;
        if (translationDrawable != null && translationX == 0) {
            translationDrawable.setProgress(0.0f);
            translationAnimationStarted = false;
            archiveHidden = SharedConfig.archiveHidden;
            currentRevealProgress = 0;
            isSliding = false;
        }
        if (translationX != 0) {
            isSliding = true;
        } else {
            currentRevealBounceProgress = 0f;
            currentRevealProgress = 0f;
            drawRevealBackground = false;
        }
        if (isSliding && !swipeCanceled) {
            boolean prevValue = drawRevealBackground;
            drawRevealBackground = Math.abs(translationX) >= getMeasuredWidth() * 0.45f;
            if (prevValue != drawRevealBackground && archiveHidden == SharedConfig.archiveHidden) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignore) {}
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (item == null) {
            return;
        }
        final boolean threeLines = isThreeLines();
        boolean needInvalidate = false;

        if (isFolder() && archivedChatsDrawable != null && archivedChatsDrawable.outProgress == 0.0f && translationX == 0.0f) {
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
            archivedChatsDrawable.draw(canvas);
            canvas.restore();
            return;
        }

        if (clipProgress != 0.0f && Build.VERSION.SDK_INT != 24) {
            canvas.save();
            canvas.clipRect(0, topClip * clipProgress, getMeasuredWidth(), getMeasuredHeight() - (int) (bottomClip * clipProgress));
        }

        int backgroundColor = 0;
        if (translationX != 0 || cornerProgress != 0.0f) {
            canvas.save();
            String swipeMessage;
            int revealBackgroundColor;
            int swipeMessageStringId;
            if (swipeAction == SWIPE_ACTION_FOLDER) {
                if (archiveHidden) {
                    backgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground, resourcesProvider);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archiveBackground, resourcesProvider);
                    swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.UnhideFromTop);
                    translationDrawable = Theme.dialogs_unpinArchiveDrawable;
                } else {
                    backgroundColor = Theme.getColor(Theme.key_chats_archiveBackground, resourcesProvider);
                    revealBackgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground, resourcesProvider);
                    swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.HideOnTop);
                    translationDrawable = Theme.dialogs_pinArchiveDrawable;
                }
            } else if (swipeAction == SWIPE_ACTION_UNARCHIVE) {
                backgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground, resourcesProvider);
                revealBackgroundColor = Theme.getColor(Theme.key_chats_archiveBackground, resourcesProvider);
                swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.Unarchive);
                translationDrawable = Theme.dialogs_unarchiveDrawable;
            } else {
                backgroundColor = Theme.getColor(Theme.key_chats_archiveBackground, resourcesProvider);
                revealBackgroundColor = Theme.getColor(Theme.key_chats_archivePinBackground, resourcesProvider);
                if (swipeAction == SWIPE_ACTION_MUTE) {
                    if (dialogMuted) {
                        swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.SwipeUnmute);
                        translationDrawable = Theme.dialogs_swipeUnmuteDrawable;
                    } else {
                        swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.SwipeMute);
                        translationDrawable = Theme.dialogs_swipeMuteDrawable;
                    }
                } else if (swipeAction == SWIPE_ACTION_DELETE) {
                    swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.SwipeDeleteChat);
                    backgroundColor = Theme.getColor(Theme.key_dialogSwipeRemove, resourcesProvider);
                    translationDrawable = Theme.dialogs_swipeDeleteDrawable;
                } else if (swipeAction == SWIPE_ACTION_READ) {
                    if (unreadCount > 0 || markUnread) {
                        swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.SwipeMarkAsRead);
                        translationDrawable = Theme.dialogs_swipeReadDrawable;
                    } else {
                        swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.SwipeMarkAsUnread);
                        translationDrawable = Theme.dialogs_swipeUnreadDrawable;
                    }
                } else if (swipeAction == SWIPE_ACTION_PIN) {
                    if (getIsPinned()) {
                        swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.SwipeUnpin);
                        translationDrawable = Theme.dialogs_swipeUnpinDrawable;
                    } else {
                        swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.SwipePin);
                        translationDrawable = Theme.dialogs_swipePinDrawable;
                    }
                } else {
                    swipeMessage = LocaleController.getString(swipeMessageStringId = R.string.Archive);
                    translationDrawable = Theme.dialogs_archiveDrawable;
                }
            }

            if (swipeCanceled && lastDrawTranslationDrawable != null) {
                translationDrawable = lastDrawTranslationDrawable;
                swipeMessageStringId = lastDrawSwipeMessageStringId;
                swipeMessage = LocaleController.getString(swipeMessageStringId);
            } else {
                lastDrawTranslationDrawable = translationDrawable;
                lastDrawSwipeMessageStringId = swipeMessageStringId;
            }

            if (!translationAnimationStarted && Math.abs(translationX) > dp(43)) {
                translationAnimationStarted = true;
                translationDrawable.setProgress(0.0f);
                translationDrawable.setCallback(this);
                translationDrawable.start();
            }

            float tx = getMeasuredWidth() + translationX;
            if (currentRevealProgress < 1.0f) {
                Theme.dialogs_pinnedPaint.setColor(backgroundColor);
                canvas.drawRect(tx - dp(8), 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
                if (currentRevealProgress == 0) {
                    if (Theme.dialogs_archiveDrawableRecolored) {
                        Theme.dialogs_archiveDrawable.setLayerColor("Arrow.**", Theme.getNonAnimatedColor(Theme.key_chats_archiveBackground));
                        Theme.dialogs_archiveDrawableRecolored = false;
                    }
                }
            }
            int drawableX = getMeasuredWidth() - dp(43) - translationDrawable.getIntrinsicWidth() / 2;
            int drawableY = (getMeasuredHeight() - dp(54)) / 2;
            int drawableCx = drawableX + translationDrawable.getIntrinsicWidth() / 2;
            int drawableCy = drawableY + translationDrawable.getIntrinsicHeight() / 2;

            if (currentRevealProgress > 0.0f) {
                canvas.save();
                canvas.clipRect(tx - dp(8), 0, getMeasuredWidth(), getMeasuredHeight());
                Theme.dialogs_pinnedPaint.setColor(revealBackgroundColor);

                float rad = (float) Math.sqrt(drawableCx * drawableCx + (drawableCy - getMeasuredHeight()) * (drawableCy - getMeasuredHeight()));
                canvas.drawCircle(drawableCx, drawableCy, rad * AndroidUtilities.accelerateInterpolator.getInterpolation(currentRevealProgress), Theme.dialogs_pinnedPaint);
                canvas.restore();

                if (!Theme.dialogs_archiveDrawableRecolored) {
                    Theme.dialogs_archiveDrawable.setLayerColor("Arrow.**", Theme.getNonAnimatedColor(Theme.key_chats_archivePinBackground));
                    Theme.dialogs_archiveDrawableRecolored = true;
                }
            }

            canvas.save();
            canvas.translate(drawableX, drawableY);
            if (currentRevealBounceProgress != 0.0f && currentRevealBounceProgress != 1.0f) {
                float scale = 1.0f + interpolator.getInterpolation(currentRevealBounceProgress);
                canvas.scale(scale, scale, translationDrawable.getIntrinsicWidth() / 2, translationDrawable.getIntrinsicHeight() / 2);
            }
            setDrawableBounds(translationDrawable, 0, 0);
            translationDrawable.draw(canvas);
            canvas.restore();

            canvas.clipRect(tx, 0, getMeasuredWidth(), getMeasuredHeight());

            int width = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(swipeMessage));

            if (swipeMessageTextId != swipeMessageStringId || swipeMessageWidth != getMeasuredWidth()) {
                swipeMessageTextId = swipeMessageStringId;
                swipeMessageWidth = getMeasuredWidth();
                swipeMessageTextLayout = new StaticLayout(swipeMessage, Theme.dialogs_archiveTextPaint, Math.min(dp(80), width), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

                if (swipeMessageTextLayout.getLineCount() > 1) {
                    swipeMessageTextLayout = new StaticLayout(swipeMessage, Theme.dialogs_archiveTextPaintSmall, Math.min(dp(82), width), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                }
            }

            if (swipeMessageTextLayout != null) {
                canvas.save();
                float yOffset = swipeMessageTextLayout.getLineCount() > 1 ? -dp(4) : 0;
                canvas.translate(getMeasuredWidth() - dp(43) - swipeMessageTextLayout.getWidth() / 2f, drawableY + dp(54 - 16) + yOffset);
                swipeMessageTextLayout.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
        } else if (translationDrawable != null) {
            translationDrawable.stop();
            translationDrawable.setProgress(0.0f);
            translationDrawable.setCallback(null);
            translationDrawable = null;
            translationAnimationStarted = false;
        }

        if (translationX != 0) {
            canvas.save();
            canvas.translate(translationX, 0);
        }

        float cornersRadius = dp(8) * cornerProgress;
        if (isSelected) {
            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_tabletSeletedPaint);
        }

        if (isFolder() && (!SharedConfig.archiveHidden || archiveBackgroundProgress != 0)) {
            Theme.dialogs_pinnedPaint.setColor(AndroidUtilities.getOffsetColor(0, Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider), archiveBackgroundProgress, 1.0f));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
        } else if (getIsPinned()) {
            Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
        }

        updateTypingAnimationValues();

        if (translationX != 0 || cornerProgress != 0.0f) {
            canvas.save();

            Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            rect.set(getMeasuredWidth() - dp(64), 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_pinnedPaint);

            if (isSelected) {
                canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_tabletSeletedPaint);
            }

            if (isFolder() && (!SharedConfig.archiveHidden || archiveBackgroundProgress != 0)) {
                Theme.dialogs_pinnedPaint.setColor(AndroidUtilities.getOffsetColor(0, Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider), archiveBackgroundProgress, 1.0f));
                canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_pinnedPaint);
            } else if (getIsPinned()) {
                Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider));
                canvas.drawRoundRect(rect, cornersRadius, cornersRadius, Theme.dialogs_pinnedPaint);
            }
            canvas.restore();
        }

        if (translationX != 0) {
            if (cornerProgress < 1.0f) {
                cornerProgress += 16f / 150.0f;
                if (cornerProgress > 1.0f) {
                    cornerProgress = 1.0f;
                }
                needInvalidate = true;
            }
        } else if (cornerProgress > 0.0f) {
            cornerProgress -= 16f / 150.0f;
            if (cornerProgress < 0.0f) {
                cornerProgress = 0.0f;
            }
            needInvalidate = true;
        }

        if (drawNameLock) {
            setDrawableBounds(Theme.dialogs_lockDrawable, nameLockLeft, nameLockTop);
            Theme.dialogs_lockDrawable.draw(canvas);
        }

        int nameTop = dp(threeLines ? 10 : 13);
        if (nameLayout != null) {
            if (isFolder()) {
                Theme.dialogs_namePaint[paintIndex].setColor(Theme.dialogs_namePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_nameArchived, resourcesProvider));
            } else if (item.secret) {
                Theme.dialogs_namePaint[paintIndex].setColor(Theme.dialogs_namePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_secretName, resourcesProvider));
            } else {
                Theme.dialogs_namePaint[paintIndex].setColor(Theme.dialogs_namePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_name, resourcesProvider));
            }
            canvas.save();
            canvas.translate(nameLeft, nameTop);
            nameLayout.draw(canvas);
            canvas.restore();
        }

        if (timeLayout != null && !isFolder()) {
            canvas.save();
            canvas.translate(timeLeft, timeTop);
            timeLayout.draw(canvas);
            canvas.restore();
        }

        if (messageNameLayout != null) {
            if (isFolder()) {
                Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_nameMessageArchived_threeLines, resourcesProvider));
            } else if (item.draft != null) {
                Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_draft, resourcesProvider));
            } else {
                Theme.dialogs_messageNamePaint.setColor(Theme.dialogs_messageNamePaint.linkColor = Theme.getColor(Theme.key_chats_nameMessage_threeLines, resourcesProvider));
            }
            canvas.save();
            canvas.translate(messageNameLeft, messageNameTop);
            try {
                messageNameLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e(e);
            }
            canvas.restore();
        }

        if (messageLayout != null) {
            if (isFolder()) {
                if (item.senderName != null) {
                    Theme.dialogs_messagePaint[paintIndex].setColor(Theme.dialogs_messagePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_nameMessageArchived, resourcesProvider));
                } else {
                    Theme.dialogs_messagePaint[paintIndex].setColor(Theme.dialogs_messagePaint[paintIndex].linkColor = Theme.getColor(Theme.key_chats_messageArchived, resourcesProvider));
                }
            } else {
                Theme.dialogs_messagePaint[paintIndex].setColor(Theme.dialogs_messagePaint[paintIndex].linkColor = Theme.getColor(threeLines ? Theme.key_chats_message_threeLines : Theme.key_chats_message, resourcesProvider));
            }
            float top;
            float typingAnimationOffset = dp(14);
            if (typingOutToTop) {
                top = messageTop - typingAnimationOffset * typingProgres;
            } else {
                top = messageTop + typingAnimationOffset * typingProgres;
            }
            if (typingProgres != 1f) {
                canvas.save();
                canvas.translate(messageLeft, top);
                int oldAlpha = messageLayout.getPaint().getAlpha();
                messageLayout.getPaint().setAlpha((int) (oldAlpha * (1f - typingProgres)));
                messageLayout.draw(canvas);
                messageLayout.getPaint().setAlpha(oldAlpha);
                canvas.restore();
            }

            canvas.save();
            if (typingOutToTop) {
                top = messageTop + typingAnimationOffset * (1f - typingProgres);
            } else {
                top = messageTop - typingAnimationOffset * (1f - typingProgres);
            }
            canvas.translate(typingLeft, top);
            if (typingLayout != null && typingProgres > 0) {
                int oldAlpha = typingLayout.getPaint().getAlpha();
                typingLayout.getPaint().setAlpha((int) (oldAlpha * typingProgres));
                typingLayout.draw(canvas);
                typingLayout.getPaint().setAlpha(oldAlpha);
            }
            canvas.restore();

            if (typingLayout != null && (printingStringType >= 0 || (typingProgres > 0 && lastKnownTypingType >= 0))) {
                int type = printingStringType >= 0 ? printingStringType : lastKnownTypingType;
                StatusDrawable statusDrawable = Theme.getChatStatusDrawable(type);
                if (statusDrawable != null) {
                    canvas.save();
                    int color = Theme.getColor(Theme.key_chats_actionMessage);
                    statusDrawable.setColor(ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * typingProgres)));
                    if (typingOutToTop) {
                        top = messageTop + typingAnimationOffset * (1f - typingProgres);
                    } else {
                        top = messageTop - typingAnimationOffset * (1f - typingProgres);
                    }
                    if (type == 1 || type == 4) {
                        canvas.translate(statusDrawableLeft, top + (type == 1 ? dp(1) : 0));
                    } else {
                        canvas.translate(statusDrawableLeft, top + (dp(18) - statusDrawable.getIntrinsicHeight()) / 2f);
                    }
                    statusDrawable.draw(canvas);
                    invalidate();
                    canvas.restore();
                }
            }
        }

        if (!isFolder()) {
            int currentStatus = (drawClock ? 1 : 0) + (drawCheck1 ? 2 : 0) + (drawCheck2 ? 4 : 0);
            if (lastStatusDrawableParams >= 0 && lastStatusDrawableParams != currentStatus && !statusDrawableAnimationInProgress) {
                createStatusDrawableAnimator(lastStatusDrawableParams, currentStatus);
            }
            if (statusDrawableAnimationInProgress) {
                currentStatus = animateToStatusDrawableParams;
            }

            boolean drawClock = (currentStatus & 1) != 0;
            boolean drawCheck1 = (currentStatus & 2) != 0;
            boolean drawCheck2 = (currentStatus & 4) != 0;

            if (statusDrawableAnimationInProgress) {
                boolean outDrawClock = (animateFromStatusDrawableParams & 1) != 0;
                boolean outDrawCheck1 = (animateFromStatusDrawableParams & 2) != 0;
                boolean outDrawCheck2 = (animateFromStatusDrawableParams & 4) != 0;
                if (!drawClock && !outDrawClock && outDrawCheck2 && !outDrawCheck1 && drawCheck1 && drawCheck2) {
                    drawCheckStatus(canvas, drawClock, drawCheck1, drawCheck2, true, statusDrawableProgress);
                } else {
                    drawCheckStatus(canvas, outDrawClock, outDrawCheck1, outDrawCheck2, false, 1f - statusDrawableProgress);
                    drawCheckStatus(canvas, drawClock, drawCheck1, drawCheck2, false, statusDrawableProgress);
                }
            } else {
                drawCheckStatus(canvas, drawClock, drawCheck1, drawCheck2, false, 1f);
            }
            lastStatusDrawableParams = (this.drawClock ? 1 : 0) + (this.drawCheck1 ? 2 : 0) + (this.drawCheck2 ? 4 : 0);
        }

        boolean drawMuted = dialogMuted;
        if ((drawMuted || dialogMutedProgress > 0) && !drawVerified) {
            if (drawMuted && dialogMutedProgress != 1f) {
                dialogMutedProgress += 16 / 150f;
                if (dialogMutedProgress > 1f) {
                    dialogMutedProgress = 1f;
                } else {
                    invalidate();
                }
            } else if (!drawMuted && dialogMutedProgress != 0f) {
                dialogMutedProgress -= 16 / 150f;
                if (dialogMutedProgress < 0f) {
                    dialogMutedProgress = 0f;
                } else {
                    invalidate();
                }
            }
            float muteX = nameMuteLeft - dp(threeLines ? 0 : 1);
            float muteY = dp(SharedConfig.useThreeLinesLayout ? 13.5f : 17.5f);
            setDrawableBounds(Theme.dialogs_muteDrawable, muteX, muteY);
            if (dialogMutedProgress != 1f) {
                canvas.save();
                canvas.scale(dialogMutedProgress, dialogMutedProgress, Theme.dialogs_muteDrawable.getBounds().centerX(), Theme.dialogs_muteDrawable.getBounds().centerY());
                Theme.dialogs_muteDrawable.setAlpha((int) (255 * dialogMutedProgress));
                Theme.dialogs_muteDrawable.draw(canvas);
                Theme.dialogs_muteDrawable.setAlpha(255);
                canvas.restore();
            } else {
                Theme.dialogs_muteDrawable.draw(canvas);
            }
        } else if (drawVerified) {
            float y = dp(threeLines ? 13.5f : 16.5f);
            setDrawableBounds(Theme.dialogs_verifiedDrawable, nameMuteLeft - dp(1), y);
            setDrawableBounds(Theme.dialogs_verifiedCheckDrawable, nameMuteLeft - dp(1), y);
            Theme.dialogs_verifiedDrawable.draw(canvas);
            Theme.dialogs_verifiedCheckDrawable.draw(canvas);
        }

        if (drawReorder || reorderIconProgress != 0) {
            Theme.dialogs_reorderDrawable.setAlpha((int) (reorderIconProgress * 255));
            setDrawableBounds(Theme.dialogs_reorderDrawable, pinLeft, pinTop);
            Theme.dialogs_reorderDrawable.draw(canvas);
        }
        if (drawError) {
            Theme.dialogs_errorDrawable.setAlpha((int) ((1.0f - reorderIconProgress) * 255));
            rect.set(errorLeft, errorTop, errorLeft + dp(23), errorTop + dp(23));
            canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.dialogs_errorPaint);
            setDrawableBounds(Theme.dialogs_errorDrawable, errorLeft + dp(5.5f), errorTop + dp(5));
            Theme.dialogs_errorDrawable.draw(canvas);
        } else if ((drawCount || drawMention) && drawCount2 || countChangeProgress != 1f) {
            boolean drawCounterMuted = dialogMuted;
            drawCounter(canvas, drawCounterMuted, countTop, countLeft, countLeftOld, 1f);
            if (drawMention) {
                Theme.dialogs_countPaint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                int x = mentionLeft - dp(5.5f);
                rect.set(x, countTop, x + mentionWidth + dp(11), countTop + dp(23));
                Paint paint = Theme.dialogs_countPaint;
                canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, paint);
                if (mentionLayout != null) {
                    Theme.dialogs_countTextPaint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                    canvas.save();
                    canvas.translate(mentionLeft, countTop + dp(4));
                    mentionLayout.draw(canvas);
                    canvas.restore();
                } else {
                    Theme.dialogs_mentionDrawable.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                    setDrawableBounds(Theme.dialogs_mentionDrawable, mentionLeft - dp(2), countTop + dp(3.2f), dp(16), dp(16));
                    Theme.dialogs_mentionDrawable.draw(canvas);
                }
            }
        } else if (getIsPinned()) {
            Theme.dialogs_pinnedDrawable.setAlpha((int) ((1.0f - reorderIconProgress) * 255));
            setDrawableBounds(Theme.dialogs_pinnedDrawable, pinLeft, pinTop);
            Theme.dialogs_pinnedDrawable.draw(canvas);
        }

        if (animatingArchiveAvatar) {
            canvas.save();
            float scale = 1.0f + interpolator.getInterpolation(animatingArchiveAvatarProgress / 170.0f);
            canvas.scale(scale, scale, avatarRect.centerX(), avatarRect.centerY());
        }

        drawAvatar(canvas);

        if (animatingArchiveAvatar) {
            canvas.restore();
        }

        if (drawAvatarOverlays(canvas)) {
            needInvalidate = true;
        }

        if (translationX != 0) {
            canvas.restore();
        }
        if (isFolder() && translationX == 0 && archivedChatsDrawable != null) {
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
            archivedChatsDrawable.draw(canvas);
            canvas.restore();
        }

        if (useSeparator) {
            int left;
            if (fullSeparator || isFolder() && archiveHidden) {
                left = 0;
            } else {
                left = dp(messagePaddingStart);
            }
            float y = getMeasuredHeight() - 1;
            if (LocaleController.isRTL) {
                canvas.drawLine(0, y, getMeasuredWidth() - left, y, Theme.dividerPaint);
            } else {
                canvas.drawLine(left, y, getMeasuredWidth(), y, Theme.dividerPaint);
            }
        }

        if (clipProgress != 0.0f) {
            if (Build.VERSION.SDK_INT != 24) {
                canvas.restore();
            } else {
                Theme.dialogs_pinnedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                canvas.drawRect(0, 0, getMeasuredWidth(), topClip * clipProgress, Theme.dialogs_pinnedPaint);
                canvas.drawRect(0, getMeasuredHeight() - (int) (bottomClip * clipProgress), getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_pinnedPaint);
            }
        }

        if (drawReorder || reorderIconProgress != 0.0f) {
            if (drawReorder) {
                if (reorderIconProgress < 1.0f) {
                    reorderIconProgress += 16f / 170.0f;
                    if (reorderIconProgress > 1.0f) {
                        reorderIconProgress = 1.0f;
                    }
                    needInvalidate = true;
                }
            } else {
                if (reorderIconProgress > 0.0f) {
                    reorderIconProgress -= 16f / 170.0f;
                    if (reorderIconProgress < 0.0f) {
                        reorderIconProgress = 0.0f;
                    }
                    needInvalidate = true;
                }
            }
        }

        if (archiveHidden) {
            if (archiveBackgroundProgress > 0.0f) {
                archiveBackgroundProgress -= 16f / 230.0f;
                if (archiveBackgroundProgress < 0.0f) {
                    archiveBackgroundProgress = 0.0f;
                }
                if (avatarDrawable.getAvatarType() == AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
                    avatarDrawable.setArchivedAvatarHiddenProgress(CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(archiveBackgroundProgress));
                }
                needInvalidate = true;
            }
        } else {
            if (archiveBackgroundProgress < 1.0f) {
                archiveBackgroundProgress += 16f / 230.0f;
                if (archiveBackgroundProgress > 1.0f) {
                    archiveBackgroundProgress = 1.0f;
                }
                if (avatarDrawable.getAvatarType() == AvatarDrawable.AVATAR_TYPE_ARCHIVED) {
                    avatarDrawable.setArchivedAvatarHiddenProgress(CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(archiveBackgroundProgress));
                }
                needInvalidate = true;
            }
        }

        if (animatingArchiveAvatar) {
            animatingArchiveAvatarProgress += 16f;
            if (animatingArchiveAvatarProgress >= 170.0f) {
                animatingArchiveAvatarProgress = 170.0f;
                animatingArchiveAvatar = false;
            }
            needInvalidate = true;
        }
        if (drawRevealBackground) {
            if (currentRevealBounceProgress < 1.0f) {
                currentRevealBounceProgress += 16f / 170.0f;
                if (currentRevealBounceProgress > 1.0f) {
                    currentRevealBounceProgress = 1.0f;
                    needInvalidate = true;
                }
            }
            if (currentRevealProgress < 1.0f) {
                currentRevealProgress += 16f / 300.0f;
                if (currentRevealProgress > 1.0f) {
                    currentRevealProgress = 1.0f;
                }
                needInvalidate = true;
            }
        } else {
            if (currentRevealBounceProgress == 1.0f) {
                currentRevealBounceProgress = 0.0f;
                needInvalidate = true;
            }
            if (currentRevealProgress > 0.0f) {
                currentRevealProgress -= 16f / 300.0f;
                if (currentRevealProgress < 0.0f) {
                    currentRevealProgress = 0.0f;
                }
                needInvalidate = true;
            }
        }

        if (needInvalidate) {
            invalidate();
        }
    }

    /** Upstream DialogUpdateHelper.updateAnimationValues (typing line cross-fade, 200 ms). */
    private void updateTypingAnimationValues() {
        if (lastDrawnPrintingType != null && typingLayout != null && typingProgres != 1f) {
            typingProgres += 16f / 200f;
            invalidate();
        } else if (lastDrawnPrintingType == null && typingProgres != 0) {
            typingProgres -= 16f / 200f;
            invalidate();
        }
        typingProgres = Math.max(0f, Math.min(1f, typingProgres));
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

    /** Upstream drawAvatarOverlays: the online dot (user dialogs only). */
    private boolean drawAvatarOverlays(Canvas canvas) {
        boolean needInvalidate = false;
        if (isFolder()) {
            return false;
        }
        if (isOnline || onlineProgress != 0) {
            final boolean threeLines = isThreeLines();
            int top = (int) (avatarRect.bottom - dp(threeLines ? 6 : 8));
            int left;
            if (LocaleController.isRTL) {
                left = (int) (avatarRect.left + dp(threeLines ? 10 : 6));
            } else {
                left = (int) (avatarRect.right - dp(threeLines ? 10 : 6));
            }
            Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            canvas.drawCircle(left, top, dp(7) * onlineProgress, Theme.dialogs_onlineCirclePaint);
            Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider));
            canvas.drawCircle(left, top, dp(5) * onlineProgress, Theme.dialogs_onlineCirclePaint);
            if (isOnline) {
                if (onlineProgress < 1.0f) {
                    onlineProgress += 16f / 150.0f;
                    if (onlineProgress > 1.0f) {
                        onlineProgress = 1.0f;
                    }
                    needInvalidate = true;
                }
            } else {
                if (onlineProgress > 0.0f) {
                    onlineProgress -= 16f / 150.0f;
                    if (onlineProgress < 0.0f) {
                        onlineProgress = 0.0f;
                    }
                    needInvalidate = true;
                }
            }
        }
        return needInvalidate;
    }

    private void drawCounter(Canvas canvas, boolean drawCounterMuted, int countTop, int countLeftLocal, int countLeftOld, float globalScale) {
        if (drawCount && drawCount2 || countChangeProgress != 1f) {
            final float progressFinal = (unreadCount == 0 && !markUnread) ? 1f - countChangeProgress : countChangeProgress;
            Paint paint = drawCounterMuted || isFolder() ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint;
            int fillPaintAlpha = 255;

            if (countOldLayout == null || unreadCount == 0) {
                StaticLayout drawLayout = unreadCount == 0 ? countOldLayout : countLayout;
                paint.setAlpha((int) ((1.0f - reorderIconProgress) * fillPaintAlpha));
                Theme.dialogs_countTextPaint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                int x = countLeftLocal - dp(5.5f);
                rect.set(x, countTop, x + countWidth + dp(11), countTop + dp(23));

                int restoreToCount = canvas.save();
                if (globalScale != 1f) {
                    canvas.scale(globalScale, globalScale, rect.centerX(), rect.centerY());
                }
                if (progressFinal != 1f) {
                    if (getIsPinned()) {
                        Theme.dialogs_pinnedDrawable.setAlpha((int) ((1.0f - reorderIconProgress) * 255));
                        setDrawableBounds(Theme.dialogs_pinnedDrawable, pinLeft, pinTop);
                        canvas.save();
                        canvas.scale(1f - progressFinal, 1f - progressFinal, Theme.dialogs_pinnedDrawable.getBounds().centerX(), Theme.dialogs_pinnedDrawable.getBounds().centerY());
                        Theme.dialogs_pinnedDrawable.draw(canvas);
                        canvas.restore();
                    }
                    canvas.scale(progressFinal, progressFinal, rect.centerX(), rect.centerY());
                }

                canvas.drawRoundRect(rect, dp(11.5f), dp(11.5f), paint);
                if (drawLayout != null) {
                    canvas.save();
                    canvas.translate(countLeftLocal, countTop + dp(4));
                    drawLayout.draw(canvas);
                    canvas.restore();
                }

                canvas.restoreToCount(restoreToCount);
            } else {
                paint.setAlpha((int) ((1.0f - reorderIconProgress) * fillPaintAlpha));
                Theme.dialogs_countTextPaint.setAlpha((int) ((1.0f - reorderIconProgress) * 255));

                float progressHalf = progressFinal * 2;
                if (progressHalf > 1f) {
                    progressHalf = 1f;
                }

                float countLeft = countLeftLocal * progressHalf + countLeftOld * (1f - progressHalf);
                float x = countLeft - dp(5.5f);
                rect.set(x, countTop, x + (countWidth * progressHalf) + (countWidthOld * (1f - progressHalf)) + dp(11), countTop + dp(23));

                float scale = 1f;
                if (progressFinal <= 0.5f) {
                    scale += 0.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(progressFinal * 2);
                } else {
                    scale += 0.1f * CubicBezierInterpolator.EASE_IN.getInterpolation((1f - (progressFinal - 0.5f) * 2));
                }

                canvas.save();
                canvas.scale(scale * globalScale, scale * globalScale, rect.centerX(), rect.centerY());
                canvas.drawRoundRect(rect, dp(11.5f), dp(11.5f), paint);
                if (countAnimationStableLayout != null) {
                    canvas.save();
                    canvas.translate(countLeft, countTop + dp(4));
                    countAnimationStableLayout.draw(canvas);
                    canvas.restore();
                }

                int textAlpha = Theme.dialogs_countTextPaint.getAlpha();
                Theme.dialogs_countTextPaint.setAlpha((int) (textAlpha * progressHalf));
                if (countAnimationInLayout != null) {
                    canvas.save();
                    canvas.translate(countLeft, (countAnimationIncrement ? dp(13) : -dp(13)) * (1f - progressHalf) + countTop + dp(4));
                    countAnimationInLayout.draw(canvas);
                    canvas.restore();
                } else if (countLayout != null) {
                    canvas.save();
                    canvas.translate(countLeft, (countAnimationIncrement ? dp(13) : -dp(13)) * (1f - progressHalf) + countTop + dp(4));
                    countLayout.draw(canvas);
                    canvas.restore();
                }

                if (countOldLayout != null) {
                    Theme.dialogs_countTextPaint.setAlpha((int) (textAlpha * (1f - progressHalf)));
                    canvas.save();
                    canvas.translate(countLeft, (countAnimationIncrement ? -dp(13) : dp(13)) * progressHalf + countTop + dp(4));
                    countOldLayout.draw(canvas);
                    canvas.restore();
                }
                Theme.dialogs_countTextPaint.setAlpha(textAlpha);
                canvas.restore();
            }
        }
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        if (who == translationDrawable || who == Theme.dialogs_archiveAvatarDrawable) {
            invalidate(who.getBounds());
        } else {
            super.invalidateDrawable(who);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        if (item == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (item.title != null) {
            sb.append(item.title).append(". ");
        }
        if (unreadCount > 0) {
            sb.append(unreadCount).append(". ");
        }
        if (item.senderName != null) {
            sb.append(item.senderName).append(": ");
        }
        if (item.message != null) {
            sb.append(item.message).append(". ");
        }
        if (item.time != null) {
            sb.append(item.time);
        }
        info.setContentDescription(sb.toString());
    }

    /** Holder so buildLayout can swap the message paint like upstream's currentMessagePaint field. */
    private static class TextPaintRef {
        android.text.TextPaint paint;
    }
}
