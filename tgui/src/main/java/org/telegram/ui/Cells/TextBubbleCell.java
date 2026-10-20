package org.telegram.ui.Cells;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.MsgClockDrawable;

/**
 * tgui: lightweight text-only message bubble. Geometry (paddings, time / check positions,
 * grouped corners) follows upstream ChatMessageCell for plain text messages without avatars,
 * names, replies, reactions or media; background is the restored {@link Theme.MessageDrawable}.
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

    private int totalHeight;
    private int backgroundWidth;
    private int timeWidth;
    private int textX, textY;
    private int textLeftOffset;
    private int timeX;

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
    }

    /**
     * @param pinnedTop    previous message is from the same side (top corner on the tail side gets the small radius)
     * @param pinnedBottom next message is from the same side (no tail, small bottom corner)
     */
    public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        boolean sameMessage = this.message != null && message != null && lastMessageId == message.id;
        boolean animateStatus = sameMessage && lastState == TextBubbleMessage.STATE_SENDING && message.state != TextBubbleMessage.STATE_SENDING && isAttachedToWindow();
        if (this.message != message || this.pinnedTop != pinnedTop || this.pinnedBottom != pinnedBottom || layoutForText != (message == null ? null : message.text)) {
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
        boolean out = message.isOut;

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

        CharSequence text = TextUtils.isEmpty(message.text) ? " " : message.text;
        textLayout = new StaticLayout(text, Theme.chat_msgTextPaint, textMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

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
        if (layoutForWidth != width || layoutForText != message.text) {
            buildLayout(width);
        }
        setMeasuredDimension(width, totalHeight);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (statusAnimator != null) {
            statusAnimator.cancel();
            statusAnimator = null;
            statusProgress = 1f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (message == null || textLayout == null) {
            return;
        }
        final boolean out = message.isOut;
        final int layoutWidth = getMeasuredWidth();
        final int layoutHeight = getMeasuredHeight();

        // --- background (ChatMessageCell.drawBackground for !mediaBackground)
        Theme.MessageDrawable drawable;
        if (pinnedBottom) {
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
        Theme.chat_msgTextPaint.setColor(getThemedColor(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
        Theme.chat_msgTextPaint.linkColor = getThemedColor(out ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
        canvas.save();
        canvas.translate(textX - textLeftOffset, textY);
        textLayout.draw(canvas);
        canvas.restore();

        // --- time
        if (out) {
            timeX = layoutWidth - timeWidth - AndroidUtilities.dp(38.5f);
        } else {
            timeX = backgroundWidth - AndroidUtilities.dp(9) - timeWidth;
        }
        Theme.chat_timePaint.setColor(getThemedColor(out ? Theme.key_chat_outTimeText : Theme.key_chat_inTimeText));
        canvas.save();
        canvas.translate(timeX, layoutHeight - AndroidUtilities.dp(pinnedBottom || pinnedTop ? 7.5f : 6.5f) - timeLayout.getHeight());
        timeLayout.draw(canvas);
        canvas.restore();

        // --- status
        if (out) {
            drawStatus(canvas, layoutWidth, layoutHeight);
        }
    }

    private void drawStatus(Canvas canvas, int layoutWidth, int layoutHeight) {
        int state = message.state;
        float progress = statusProgress;
        boolean useScale = progress != 1f;
        float scale = 0.5f + 0.5f * progress;
        int checkY = layoutHeight - AndroidUtilities.dp(pinnedBottom || pinnedTop ? 9 : 8);

        if (state == TextBubbleMessage.STATE_SENDING || useScale) {
            MsgClockDrawable clock = Theme.chat_msgClockDrawable;
            float alpha = state == TextBubbleMessage.STATE_SENDING ? 1f : 1f - progress;
            if (alpha > 0) {
                clock.setColor(getThemedColor(Theme.key_chat_outSentClock));
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
        Drawable check = read ? Theme.chat_msgOutCheckReadDrawable : Theme.chat_msgOutCheckDrawable;
        Theme.setDrawableColor(check, getThemedColor(read ? Theme.key_chat_outSentCheckRead : Theme.key_chat_outSentCheck));
        if (read) {
            setBounds(check, layoutWidth - AndroidUtilities.dp(22.5f) - check.getIntrinsicWidth(), checkY - check.getIntrinsicHeight());
        } else {
            setBounds(check, layoutWidth - AndroidUtilities.dp(18.5f) - check.getIntrinsicWidth(), checkY - check.getIntrinsicHeight());
        }
        drawScaled(canvas, check, useScale, scale, progress);
        if (read) {
            Drawable half = Theme.chat_msgOutHalfCheckDrawable;
            Theme.setDrawableColor(half, getThemedColor(Theme.key_chat_outSentCheckRead));
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
