package org.telegram.ui.Cells;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.Interpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.MsgClockDrawable;

/**
 * tgui: lightweight text-only message bubble. Geometry (paddings, time / check positions,
 * grouped corners) follows upstream ChatMessageCell for plain text messages without avatars,
 * names, replies, reactions or media; background is the restored {@link Theme.MessageDrawable}.
 * Emoji are drawn with the bundled bitmaps ({@link Emoji#replaceEmoji}); a message of only emoji
 * is drawn large without a bubble, with the time on a pill, as upstream's TYPE_EMOJIS.
 */
public class TextBubbleCell extends View {

    private final Theme.ResourcesProvider resourcesProvider;

    private TextBubbleMessage message;
    private boolean pinnedTop;
    private boolean pinnedBottom;

    private StaticLayout textLayout;
    private StaticLayout timeLayout;
    private int layoutForWidth = -1;
    private CharSequence layoutForText;
    private String layoutForReaction;
    // chosen reaction chip (ReactionsLayoutInBubble.ReactionButton look: 26dp pill, 20dp emoji, 12dp counter)
    private Drawable reactionDrawable;
    private int reactionWidth;
    private int reactionY;
    private final RectF reactionRect = new RectF();
    private static TextPaint reactionCounterPaint;
    private static android.graphics.Paint reactionPaint;

    // selection mode (ChatMessageCell.setCheckBoxVisible / setChecked / animateCheckboxTranslation / setDrawSelectionBackground)
    private CheckBoxBase checkBox;
    private boolean checkBoxVisible;
    private boolean checkBoxAnimationInProgress;
    private float checkBoxAnimationProgress;
    private long lastCheckBoxAnimationTime;
    private int checkBoxTranslation;
    private boolean drawSelectionBackground;
    private float selectionProgress;
    private long lastSelectionAnimationTime;
    private int bubbleBottom;
    private static android.graphics.Paint selectionPaint;

    private int totalHeight;
    private int backgroundWidth;
    private int timeWidth;
    private int textX, textY;
    private int textLeftOffset;
    private int timeX;
    private boolean emojiOnlyMode;
    private TextPaint textPaint;
    private final RectF timeRect = new RectF();

    private int lastState = -1;
    private long lastMessageId = Long.MIN_VALUE;
    private float statusProgress = 1f;
    private ValueAnimator statusAnimator;

    public TextBubbleCell(Context context) {
        this(context, null);
    }

    public TextBubbleCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        Theme.createChatResources(context, false);
        // upstream ChatMessageCell redraws when emoji bitmaps finish loading
        NotificationCenter.listenEmojiLoading(this);
    }

    /**
     * @param pinnedTop    previous message is from the same side (top corner on the tail side gets the small radius)
     * @param pinnedBottom next message is from the same side (no tail, small bottom corner)
     */
    public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        boolean sameMessage = this.message != null && message != null && lastMessageId == message.id;
        boolean animateStatus = sameMessage && lastState == TextBubbleMessage.STATE_SENDING && message.state != TextBubbleMessage.STATE_SENDING && isAttachedToWindow();
        if (this.message != message || this.pinnedTop != pinnedTop || this.pinnedBottom != pinnedBottom || layoutForText != (message == null ? null : message.text) || !TextUtils.equals(layoutForReaction, message == null ? null : message.reaction)) {
            layoutForWidth = -1;
            requestLayout();
        }
        this.message = message;
        this.pinnedTop = pinnedTop;
        this.pinnedBottom = pinnedBottom;
        if (statusAnimator != null) {
            statusAnimator.cancel();
            statusAnimator = null;
        }
        statusProgress = 1f;
        if (animateStatus) {
            statusProgress = 0f;
            statusAnimator = ValueAnimator.ofFloat(0f, 1f);
            statusAnimator.addUpdateListener(a -> {
                statusProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            statusAnimator.setDuration(220);
            statusAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            statusAnimator.start();
        }
        lastMessageId = message == null ? Long.MIN_VALUE : message.id;
        lastState = message == null ? -1 : message.state;
        invalidate();
    }

    public TextBubbleMessage getMessage() {
        return message;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private int getParentWidth() {
        if (getParent() instanceof View) {
            int w = ((View) getParent()).getMeasuredWidth();
            if (w > 0) {
                return w;
            }
        }
        return AndroidUtilities.displaySize.x;
    }

    private void buildLayout(int width) {
        layoutForWidth = width;
        layoutForText = message.text;
        layoutForReaction = message.reaction;
        boolean out = message.isOut;
        reactionDrawable = null;
        reactionWidth = 0;
        if (message.reaction != null) {
            if (reactionCounterPaint == null) {
                reactionCounterPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                reactionCounterPaint.setTextSize(AndroidUtilities.dp(12));
                reactionCounterPaint.setTypeface(AndroidUtilities.bold());
                reactionPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            }
            reactionDrawable = Emoji.getEmojiDrawable(message.reaction);
            if (reactionDrawable == null) {
                reactionDrawable = Emoji.getEmojiDrawable(Emoji.fixEmoji(message.reaction));
            }
            reactionWidth = AndroidUtilities.dp(8 + 20 + 4 + 10) + (int) Math.ceil(reactionCounterPaint.measureText("1"));
        }

        int maxWidth = Math.min(width, Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) - AndroidUtilities.dp(80);
        if (AndroidUtilities.isTablet()) {
            maxWidth = Math.min(maxWidth, AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80));
        }
        int textMaxWidth = Math.max(AndroidUtilities.dp(1), maxWidth - AndroidUtilities.dp(31));

        String time = message.time == null ? "" : message.time;
        timeWidth = (int) Math.ceil(Theme.chat_timePaint.measureText(time));
        timeLayout = new StaticLayout(time, Theme.chat_timePaint, timeWidth + AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        int timeMore = timeWidth + AndroidUtilities.dp(6);
        if (out) {
            timeMore += AndroidUtilities.dp(20.5f);
        }

        int[] emojiOnly = new int[1];
        CharSequence text = TextUtils.isEmpty(message.text) ? " " : Emoji.replaceEmoji(message.text, Theme.chat_msgTextPaint.getFontMetricsInt(), true, emojiOnly);
        TextPaint emojiPaint = getEmojiOnlyPaint(text, emojiOnly[0]);
        emojiOnlyMode = emojiPaint != null;
        textPaint = emojiOnlyMode ? emojiPaint : Theme.chat_msgTextPaint;
        textLayout = new StaticLayout(text, textPaint, textMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        int lineCount = textLayout.getLineCount();
        int maxChildWidth = 0;
        float minLeft = Float.MAX_VALUE;
        boolean hasRtl = false;
        for (int i = 0; i < lineCount; i++) {
            float left = textLayout.getLineLeft(i);
            minLeft = Math.min(minLeft, left);
            if (left > 0 || textLayout.getParagraphDirection(i) == Layout.DIR_RIGHT_TO_LEFT) {
                hasRtl = true;
            }
        }
        if (minLeft == Float.MAX_VALUE) {
            minLeft = 0;
        }
        textLeftOffset = (int) Math.floor(minLeft);
        for (int i = 0; i < lineCount; i++) {
            maxChildWidth = Math.max(maxChildWidth, (int) Math.ceil(textLayout.getLineRight(i) - minLeft));
        }
        int lastLineWidth = (int) Math.ceil(textLayout.getLineRight(lineCount - 1) - minLeft);

        int textHeight = textLayout.getLineBottom(lineCount - 1);
        if (emojiOnlyMode) {
            backgroundWidth = maxChildWidth;
            textY = AndroidUtilities.dp(4);
            totalHeight = textY + textHeight + AndroidUtilities.dp(2 + 17 + 4);
            if (reactionWidth > 0) {
                reactionY = textY + textHeight + AndroidUtilities.dp(4);
                totalHeight += AndroidUtilities.dp(26 + 4);
            }
            return;
        }
        int namesOffset = pinnedTop ? -AndroidUtilities.dp(1) : 0;
        totalHeight = textHeight + AndroidUtilities.dp(19.5f) + namesOffset;

        boolean newLineForTime = maxWidth - lastLineWidth < timeMore || hasRtl;
        if (newLineForTime) {
            totalHeight += AndroidUtilities.dp(14);
            backgroundWidth = Math.max(maxChildWidth, lastLineWidth) + AndroidUtilities.dp(31);
            backgroundWidth = Math.max(backgroundWidth, (out ? timeWidth + AndroidUtilities.dp(17) : timeWidth) + AndroidUtilities.dp(31));
        } else {
            int diff = maxChildWidth - lastLineWidth;
            if (diff >= 0 && diff <= timeMore) {
                backgroundWidth = maxChildWidth + timeMore - diff + AndroidUtilities.dp(31);
            } else {
                backgroundWidth = Math.max(maxChildWidth, lastLineWidth + timeMore) + AndroidUtilities.dp(31);
            }
        }
        textY = AndroidUtilities.dp(10) + namesOffset;
        if (reactionWidth > 0) {
            // the chip gets its own row, the time sits at the end of that row
            reactionY = textY + textHeight + AndroidUtilities.dp(6);
            totalHeight = reactionY + AndroidUtilities.dp(26 + 7);
            backgroundWidth = Math.max(maxChildWidth + AndroidUtilities.dp(31), reactionWidth + AndroidUtilities.dp(8) + timeMore + AndroidUtilities.dp(31));
        }
    }

    private void drawReaction(Canvas canvas, int x, boolean out) {
        if (reactionWidth <= 0) {
            return;
        }
        reactionRect.set(x, reactionY, x + reactionWidth, reactionY + AndroidUtilities.dp(26));
        reactionPaint.setColor(getThemedColor(out ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground));
        canvas.drawRoundRect(reactionRect, reactionRect.height() / 2f, reactionRect.height() / 2f, reactionPaint);
        if (reactionDrawable != null) {
            int size = AndroidUtilities.dp(20);
            int left = x + AndroidUtilities.dp(8);
            int top = (int) (reactionRect.centerY() - size / 2f);
            reactionDrawable.setBounds(left, top, left + size, top + size);
            reactionDrawable.draw(canvas);
        }
        reactionCounterPaint.setColor(getThemedColor(out ? Theme.key_chat_outReactionButtonTextSelected : Theme.key_chat_inReactionButtonTextSelected));
        canvas.drawText("1", x + AndroidUtilities.dp(8 + 20 + 4), reactionRect.centerY() - (reactionCounterPaint.descent() + reactionCounterPaint.ascent()) / 2f, reactionCounterPaint);
    }

    /**
     * upstream MessageObject.checkEmojiOnly: up to a few bitmap emoji and nothing else (no other
     * spans, no emoji without a bitmap) pick a large paint and resize the emoji spans to it.
     */
    private static TextPaint getEmojiOnlyPaint(CharSequence text, int emojiOnly) {
        if (emojiOnly < 1 || !(text instanceof Spanned) || Theme.chat_msgTextPaintEmoji == null) {
            return null;
        }
        Spanned spanned = (Spanned) text;
        Emoji.EmojiSpan[] spans = spanned.getSpans(0, spanned.length(), Emoji.EmojiSpan.class);
        if (spans == null || spans.length == 0 || emojiOnly - spans.length > 0) {
            return null;
        }
        if (spanned.getSpans(0, spanned.length(), Object.class).length != spans.length) {
            return null;
        }
        TextPaint paint;
        switch (emojiOnly) {
            case 1:
            case 2:
                paint = Theme.chat_msgTextPaintEmoji[2];
                break;
            case 3:
                paint = Theme.chat_msgTextPaintEmoji[3];
                break;
            case 4:
                paint = Theme.chat_msgTextPaintEmoji[4];
                break;
            default:
                paint = Theme.chat_msgTextPaintEmoji[5];
                break;
        }
        int size = (int) (paint.getTextSize() + AndroidUtilities.dp(4));
        for (Emoji.EmojiSpan span : spans) {
            span.replaceFontMetrics(paint.getFontMetricsInt(), size);
        }
        return paint;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = getParentWidth();
        }
        if (message == null) {
            setMeasuredDimension(width, 0);
            return;
        }
        if (layoutForWidth != width || layoutForText != message.text || !TextUtils.equals(layoutForReaction, message.reaction)) {
            buildLayout(width);
        }
        setMeasuredDimension(width, totalHeight);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (checkBox != null) {
            checkBox.onDetachedFromWindow();
        }
        if (statusAnimator != null) {
            statusAnimator.cancel();
            statusAnimator = null;
            statusProgress = 1f;
        }
    }

    /** Upstream ChatMessageCell.setCheckBoxVisible: a 21dp CheckBoxBase slides in from the left, incoming bubbles move 35dp right. */
    public void setCheckBoxVisible(boolean visible, boolean animated) {
        if (visible && checkBox == null) {
            checkBox = new CheckBoxBase(this, 21, resourcesProvider);
            if (isAttachedToWindow()) {
                checkBox.onAttachedToWindow();
            }
        }
        if (checkBoxVisible == visible) {
            if (animated != checkBoxAnimationInProgress && !animated) {
                checkBoxAnimationProgress = visible ? 1.0f : 0.0f;
                invalidate();
            }
            return;
        }
        checkBoxAnimationInProgress = animated;
        checkBoxVisible = visible;
        if (animated) {
            lastCheckBoxAnimationTime = SystemClock.elapsedRealtime();
        } else {
            checkBoxAnimationProgress = visible ? 1.0f : 0.0f;
        }
        invalidate();
    }

    public boolean isCheckBoxVisible() {
        return checkBoxVisible || checkBoxAnimationInProgress;
    }

    /** Upstream setChecked + setDrawSelectionBackground: check mark, selected bubble colors and the row highlight. */
    public void setChecked(boolean checked, boolean animated) {
        if (checkBox != null) {
            checkBox.setChecked(checked, animated);
        }
        if (drawSelectionBackground != checked) {
            drawSelectionBackground = checked;
            lastSelectionAnimationTime = SystemClock.elapsedRealtime();
            if (!animated) {
                selectionProgress = checked ? 1f : 0f;
            }
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (checkBox != null) {
            checkBox.onAttachedToWindow();
        }
    }

    private void animateCheckboxTranslation() {
        if (checkBoxVisible || checkBoxAnimationInProgress) {
            if (checkBoxVisible && checkBoxAnimationProgress == 1.0f || !checkBoxVisible && checkBoxAnimationProgress == 0.0f) {
                checkBoxAnimationInProgress = false;
            }
            Interpolator interpolator = checkBoxVisible ? CubicBezierInterpolator.EASE_OUT : CubicBezierInterpolator.EASE_IN;
            checkBoxTranslation = (int) Math.ceil(interpolator.getInterpolation(checkBoxAnimationProgress) * AndroidUtilities.dp(35));
            if (checkBoxAnimationInProgress) {
                long newTime = SystemClock.elapsedRealtime();
                long dt = newTime - lastCheckBoxAnimationTime;
                lastCheckBoxAnimationTime = newTime;
                if (checkBoxVisible) {
                    checkBoxAnimationProgress = Math.min(1f, checkBoxAnimationProgress + dt / 200.0f);
                } else {
                    checkBoxAnimationProgress = Math.max(0f, checkBoxAnimationProgress - dt / 200.0f);
                }
                invalidate();
            }
        } else {
            checkBoxTranslation = 0;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (message == null || textLayout == null) {
            return;
        }
        animateCheckboxTranslation();
        // row highlight (ChatActivity's ChatListView: MessageBackgroundDrawable with key_chat_selectedBackground)
        float target = drawSelectionBackground ? 1f : 0f;
        if (selectionProgress != target) {
            long newTime = SystemClock.elapsedRealtime();
            float step = (newTime - lastSelectionAnimationTime) / 200f;
            lastSelectionAnimationTime = newTime;
            selectionProgress = target > selectionProgress ? Math.min(target, selectionProgress + step) : Math.max(target, selectionProgress - step);
            invalidate();
        }
        if (selectionProgress > 0) {
            if (selectionPaint == null) {
                selectionPaint = new android.graphics.Paint();
            }
            int color = getThemedColor(Theme.key_chat_selectedBackground);
            selectionPaint.setColor(color);
            selectionPaint.setAlpha((int) (android.graphics.Color.alpha(color) * selectionProgress));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), selectionPaint);
        }
        int restore = canvas.save();
        if (!message.isOut) {
            canvas.translate(checkBoxTranslation, 0);
        }
        drawContent(canvas);
        canvas.restoreToCount(restore);
        if (checkBox != null && (checkBoxVisible || checkBoxAnimationInProgress)) {
            int size = AndroidUtilities.dp(21);
            checkBox.setBounds(AndroidUtilities.dp(8 - 35) + checkBoxTranslation, bubbleBottom - AndroidUtilities.dp(8) - size, size, size);
            checkBox.draw(canvas);
        }
    }

    private void drawContent(Canvas canvas) {
        final boolean out = message.isOut;
        final int layoutWidth = getMeasuredWidth();
        final int layoutHeight = getMeasuredHeight();

        if (emojiOnlyMode) {
            bubbleBottom = layoutHeight - AndroidUtilities.dp(4);
            drawEmojiOnly(canvas, out, layoutWidth, layoutHeight);
            return;
        }

        // --- background (ChatMessageCell.drawBackground for !mediaBackground)
        Theme.MessageDrawable drawable;
        if (drawSelectionBackground) {
            if (pinnedBottom) {
                drawable = out ? Theme.chat_msgOutMediaSelectedDrawable : Theme.chat_msgInMediaSelectedDrawable;
            } else {
                drawable = out ? Theme.chat_msgOutSelectedDrawable : Theme.chat_msgInSelectedDrawable;
            }
        } else if (pinnedBottom) {
            drawable = out ? Theme.chat_msgOutMediaDrawable : Theme.chat_msgInMediaDrawable;
        } else {
            drawable = out ? Theme.chat_msgOutDrawable : Theme.chat_msgInDrawable;
        }
        int backgroundLeft;
        int backgroundRight = backgroundWidth - AndroidUtilities.dp(3);
        if (out) {
            backgroundLeft = layoutWidth - backgroundWidth;
            if (pinnedBottom) {
                backgroundRight -= AndroidUtilities.dp(6);
            }
        } else {
            backgroundLeft = AndroidUtilities.dp(3);
            if (pinnedBottom) {
                backgroundRight -= AndroidUtilities.dp(6);
                backgroundLeft += AndroidUtilities.dp(6);
            }
        }
        int offsetBottom;
        if (pinnedBottom && pinnedTop) {
            offsetBottom = 0;
        } else if (pinnedBottom) {
            offsetBottom = AndroidUtilities.dp(1);
        } else {
            offsetBottom = AndroidUtilities.dp(2);
        }
        int backgroundTop = pinnedTop ? 0 : AndroidUtilities.dp(1);
        int backgroundHeight = layoutHeight - offsetBottom;
        if (pinnedTop) {
            backgroundTop -= AndroidUtilities.dp(1);
            backgroundHeight += AndroidUtilities.dp(1);
        }
        if (pinnedBottom) {
            backgroundHeight += AndroidUtilities.dp(1);
        }
        int parentWidth = layoutWidth, parentHeight = AndroidUtilities.displaySize.y;
        if (getParent() instanceof View) {
            View parent = (View) getParent();
            parentWidth = parent.getMeasuredWidth();
            parentHeight = parent.getMeasuredHeight();
        }
        drawable.setTop((int) getY(), parentWidth, parentHeight, 0, 0, 0, pinnedTop, pinnedBottom);
        // getShadowDrawable() may render its cache through this drawable and change its bounds, so set them afterwards
        Drawable shadow = drawable.getShadowDrawable();
        drawable.setBounds(backgroundLeft, backgroundTop, backgroundLeft + backgroundRight, backgroundTop + backgroundHeight);
        bubbleBottom = backgroundTop + backgroundHeight;
        if (shadow != null) {
            shadow.setBounds(drawable.getBounds());
            shadow.draw(canvas);
        }
        drawable.draw(canvas);

        // --- text
        if (out) {
            textX = backgroundLeft + AndroidUtilities.dp(11);
        } else {
            textX = backgroundLeft + AndroidUtilities.dp(pinnedBottom ? 11 : 17);
        }
        textPaint.setColor(getThemedColor(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
        textPaint.linkColor = getThemedColor(out ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
        canvas.save();
        canvas.translate(textX - textLeftOffset, textY);
        textLayout.draw(canvas);
        canvas.restore();
        drawReaction(canvas, textX, out);

        // --- time
        if (out) {
            timeX = layoutWidth - timeWidth - AndroidUtilities.dp(38.5f);
        } else {
            timeX = backgroundWidth - AndroidUtilities.dp(9) - timeWidth;
        }
        Theme.chat_timePaint.setColor(getThemedColor(out ? (drawSelectionBackground ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText) : (drawSelectionBackground ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText)));
        canvas.save();
        canvas.translate(timeX, layoutHeight - AndroidUtilities.dp(pinnedBottom || pinnedTop ? 7.5f : 6.5f) - timeLayout.getHeight());
        timeLayout.draw(canvas);
        canvas.restore();

        // --- status
        if (out) {
            drawStatus(canvas, layoutWidth, layoutHeight, false);
        }
    }

    private void drawEmojiOnly(Canvas canvas, boolean out, int layoutWidth, int layoutHeight) {
        int pillWidth = timeWidth + AndroidUtilities.dp(out ? 32 : 12);
        int pillHeight = AndroidUtilities.dp(17);
        int contentRight;
        int contentLeft;
        if (out) {
            contentRight = layoutWidth - AndroidUtilities.dp(11);
            contentLeft = contentRight - backgroundWidth;
        } else {
            contentLeft = AndroidUtilities.dp(17);
            contentRight = contentLeft + Math.max(backgroundWidth, pillWidth);
        }
        canvas.save();
        canvas.translate(contentLeft - textLeftOffset, textY);
        textLayout.draw(canvas);
        canvas.restore();
        drawReaction(canvas, out ? contentRight - reactionWidth : contentLeft, out);

        int pillBottom = layoutHeight - AndroidUtilities.dp(4);
        timeRect.set(contentRight - pillWidth, pillBottom - pillHeight, contentRight, pillBottom);
        Theme.chat_timeBackgroundPaint.setColor(getThemedColor(Theme.key_chat_mediaTimeBackground));
        canvas.drawRoundRect(timeRect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_timeBackgroundPaint);
        Theme.chat_timePaint.setColor(getThemedColor(Theme.key_chat_mediaTimeText));
        canvas.save();
        canvas.translate(timeRect.left + AndroidUtilities.dp(6), timeRect.centerY() - timeLayout.getHeight() / 2f);
        timeLayout.draw(canvas);
        canvas.restore();
        if (out) {
            drawStatus(canvas, contentRight + AndroidUtilities.dp(13), pillBottom + AndroidUtilities.dp(4), true);
        }
    }

    /** Checks / clock with their right edge at {@code layoutWidth - 18dp}; {@code media}: white ones for the time pill. */
    private void drawStatus(Canvas canvas, int layoutWidth, int layoutHeight, boolean media) {
        int state = message.state;
        float progress = statusProgress;
        boolean useScale = progress != 1f;
        float scale = 0.5f + 0.5f * progress;
        int checkY = layoutHeight - AndroidUtilities.dp(pinnedBottom || pinnedTop ? 9 : 8);

        if (state == TextBubbleMessage.STATE_SENDING || useScale) {
            MsgClockDrawable clock = Theme.chat_msgClockDrawable;
            float alpha = state == TextBubbleMessage.STATE_SENDING ? 1f : 1f - progress;
            if (alpha > 0) {
                clock.setColor(getThemedColor(media ? Theme.key_chat_mediaSentClock : Theme.key_chat_outSentClock));
                setBounds(clock, layoutWidth - AndroidUtilities.dp(18.5f) - clock.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.5f) - clock.getIntrinsicHeight());
                clock.setAlpha((int) (255 * alpha));
                clock.draw(canvas);
                clock.setAlpha(255);
                if (state == TextBubbleMessage.STATE_SENDING) {
                    invalidate();
                }
            }
        }
        if (state == TextBubbleMessage.STATE_SENDING) {
            return;
        }
        boolean read = state == TextBubbleMessage.STATE_READ;
        Drawable check = media ? Theme.chat_msgMediaCheckDrawable : read ? Theme.chat_msgOutCheckReadDrawable : Theme.chat_msgOutCheckDrawable;
        Theme.setDrawableColor(check, getThemedColor(media ? Theme.key_chat_mediaSentCheck : read ? Theme.key_chat_outSentCheckRead : Theme.key_chat_outSentCheck));
        if (read) {
            setBounds(check, layoutWidth - AndroidUtilities.dp(22.5f) - check.getIntrinsicWidth(), checkY - check.getIntrinsicHeight());
        } else {
            setBounds(check, layoutWidth - AndroidUtilities.dp(18.5f) - check.getIntrinsicWidth(), checkY - check.getIntrinsicHeight());
        }
        drawScaled(canvas, check, useScale, scale, progress);
        if (read) {
            Drawable half = media ? Theme.chat_msgMediaHalfCheckDrawable : Theme.chat_msgOutHalfCheckDrawable;
            Theme.setDrawableColor(half, getThemedColor(media ? Theme.key_chat_mediaSentCheck : Theme.key_chat_outSentCheckRead));
            setBounds(half, layoutWidth - AndroidUtilities.dp(18) - half.getIntrinsicWidth(), checkY - half.getIntrinsicHeight());
            drawScaled(canvas, half, useScale, scale, progress);
        }
    }

    private static void drawScaled(Canvas canvas, Drawable drawable, boolean useScale, float scale, float alpha) {
        if (useScale) {
            canvas.save();
            canvas.scale(scale, scale, drawable.getBounds().centerX(), drawable.getBounds().centerY());
            drawable.setAlpha((int) (255 * alpha));
        }
        drawable.draw(canvas);
        if (useScale) {
            canvas.restore();
            drawable.setAlpha(255);
        }
    }

    private static void setBounds(Drawable drawable, float x, float y) {
        drawable.setBounds((int) x, (int) y, (int) x + drawable.getIntrinsicWidth(), (int) y + drawable.getIntrinsicHeight());
    }
}
