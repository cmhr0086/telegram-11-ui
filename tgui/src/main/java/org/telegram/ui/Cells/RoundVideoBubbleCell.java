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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;

/** Round-video presentation. The host owns actual playback, loading and progress updates. */
public class RoundVideoBubbleCell extends TextBubbleCell {
    private final PhotoBubbleCell.PhotoRenderer renderer = new PhotoBubbleCell.PhotoRenderer();
    private final RadialProgress2 radialProgress;
    private final RectF videoRect = new RectF();
    private final RectF progressRect = new RectF();
    private final RectF durationRect = new RectF();
    private final float[] radii = new float[8];
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint durationBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint durationTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int diameter;
    private int buttonState = -1;
    private int lastDuration = -1;
    private int durationWidth;
    private StaticLayout durationLayout;
    private boolean videoVisible = true;
    private boolean touching;
    private boolean pressed;
    private float downX, downY;
    private Runnable longPress;

    public RoundVideoBubbleCell(Context context) { this(context, null); }
    public RoundVideoBubbleCell(Context context, Theme.ResourcesProvider provider) {
        super(context, provider);
        radialProgress = new RadialProgress2(this, provider);
        radialProgress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected,
                Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStrokeWidth(AndroidUtilities.dp(3));
        ringPaint.setColor(0x9fffffff);
        durationTextPaint.setTextSize(AndroidUtilities.dp(12));
        durationTextPaint.setColor(Color.WHITE);
    }

    @Override public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        if (this.message != message) {
            videoVisible = true;
            buttonState = -1;
            lastDuration = -1;
            cancelVideoTouch();
        }
        super.setMessage(message, pinnedTop, pinnedBottom);
        updatePlayingMessageProgress();
    }

    @Override protected void buildLayout(int width) {
        layoutForWidth = width;
        layoutForText = message.text;
        // This first media port does not lay out reply or reaction rows.
        layoutForReply = message.replyTo;
        setReactionsLayout();
        int displayHeight = AndroidUtilities.displaySize.y > 0 ? AndroidUtilities.displaySize.y : width;
        int side = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : Math.min(width, displayHeight);
        diameter = Math.max(1, (int) (side * 0.6f));
        backgroundWidth = diameter;
        totalHeight = diameter + AndroidUtilities.dp(10);
        measureTime();
    }

    /** Host calls after changing playing/audioProgress/audioProgressSec/mediaState. */
    public void updatePlayingMessageProgress() {
        if (message == null) return;
        int seconds = message.playing || message.videoPaused ? Math.max(0, message.duration - message.audioProgressSec) : Math.max(0, message.duration);
        if (lastDuration != seconds || durationLayout == null) {
            lastDuration = seconds;
            String value = AndroidUtilities.formatLongDuration(seconds);
            durationWidth = Math.max(1, (int) Math.ceil(durationTextPaint.measureText(value)));
            durationLayout = new StaticLayout(value, new android.text.TextPaint(durationTextPaint), durationWidth,
                    Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }
        int state = message.mediaState == TextBubbleMessage.MEDIA_DOWNLOAD ? 2 :
                message.mediaState == TextBubbleMessage.MEDIA_LOADING ? 4 : message.playing ? 1 : 0;
        radialProgress.setProgress(state == 4 ? Math.max(0, Math.min(1, message.mediaProgress)) : 0, false);
        if (state != buttonState) {
            boolean animated = buttonState != -1 && isAttachedToWindow();
            buttonState = state;
            int icon = state == 2 ? MediaActionDrawable.ICON_DOWNLOAD : state == 4 ? MediaActionDrawable.ICON_CANCEL :
                    state == 1 ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_PLAY;
            radialProgress.setIcon(icon, false, animated);
        }
        invalidate();
    }

    @Override protected void drawContent(Canvas canvas) {
        int left = message.isOut ? getMeasuredWidth() - diameter - AndroidUtilities.dp(12) : AndroidUtilities.dp(12) + incomingAvatarInset();
        int top = AndroidUtilities.dp(5);
        videoRect.set(left, top, left + diameter, top + diameter);
        bubbleBottom = (int) videoRect.bottom + AndroidUtilities.dp(2);
        if (videoVisible) {
            Drawable shadow = Theme.chat_roundVideoShadow;
            if (shadow != null) {
                int x = left - AndroidUtilities.dp(2), y = top - AndroidUtilities.dp(2);
                shadow.setBounds(x, y, (int) videoRect.right + AndroidUtilities.dp(4), (int) videoRect.bottom + AndroidUtilities.dp(4));
                shadow.draw(canvas);
            }
            for (int i = 0; i < radii.length; i++) radii[i] = diameter / 2f;
            renderer.draw(canvas, message.photo, videoRect, radii,
                    getThemedColor(message.isOut ? Theme.key_chat_outLoaderSelected : Theme.key_chat_inMediaIcon));
        }
        if (message.playing || message.videoPaused) {
            float inset = AndroidUtilities.dp(4) + AndroidUtilities.dp(1.5f);
            progressRect.set(videoRect.left + inset, videoRect.top + inset, videoRect.right - inset, videoRect.bottom - inset);
            float progress = Float.isNaN(message.audioProgress) ? 0f : Math.max(0f, Math.min(1f, message.audioProgress));
            canvas.drawArc(progressRect, -90, 360 * progress, false, ringPaint);
        }
        int half = AndroidUtilities.dp(24);
        int centerX = (int) videoRect.centerX(), centerY = (int) videoRect.centerY();
        radialProgress.setProgressRect(centerX - half, centerY - half, centerX + half, centerY + half);
        radialProgress.setProgressColor(getThemedColor(Theme.key_chat_mediaProgress));
        radialProgress.setPressed(pressed, false);
        radialProgress.draw(canvas);
        drawDuration(canvas);
        // One message timestamp/status overlay; the host may cover only the video circle.
        drawTimePill(canvas, (int) videoRect.right - AndroidUtilities.dp(5), (int) videoRect.bottom - AndroidUtilities.dp(5), AndroidUtilities.dp(6));
        if (!message.isOut) drawSideAuthor(canvas, sideAuthorBackgroundRight(true));
    }

    private void drawDuration(Canvas canvas) {
        if (durationLayout == null) return;
        float x = videoRect.left + AndroidUtilities.dp(8);
        float y = getMeasuredHeight() - AndroidUtilities.dp(28 - (pinnedBottom ? 2 : 0));
        // Upstream places duration at the left and the time/status pill at the right.
        // On a narrow circle the two can overlap; keep duration directly above time.
        float timeLeft = videoRect.right - AndroidUtilities.dp(5) - timeWidth - AndroidUtilities.dp(message.isOut ? 32 : 12);
        if (x + durationWidth + AndroidUtilities.dp(22) > timeLeft) y -= AndroidUtilities.dp(19);
        durationRect.set(x, y, x + durationWidth + AndroidUtilities.dp(22), y + AndroidUtilities.dp(17));
        durationBackgroundPaint.setColor(getThemedColor(Theme.key_chat_serviceBackground));
        durationBackgroundPaint.setAlpha((int) (durationBackgroundPaint.getAlpha() * .5f));
        canvas.drawRoundRect(durationRect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), durationBackgroundPaint);
        canvas.save();
        canvas.translate(x + AndroidUtilities.dp(4), y + AndroidUtilities.dp(1.7f));
        durationLayout.draw(canvas);
        canvas.restore();
        if (message.contentUnread && !message.playing) {
            durationTextPaint.setColor(Color.WHITE);
            canvas.drawCircle(x + durationWidth + AndroidUtilities.dp(12), y + AndroidUtilities.dp(8.5f), AndroidUtilities.dp(3), durationTextPaint);
        }
    }

    public void getVideoRect(RectF out) { out.set(videoRect); }
    public boolean getVideoRectOnScreen(RectF out) {
        if (!isAttachedToWindow() || out == null || videoRect.isEmpty()) return false;
        int[] location = new int[2];
        getLocationOnScreen(location);
        out.set(videoRect);
        out.offset(location[0], location[1]);
        return true;
    }
    public void setVideoVisible(boolean visible) {
        if (videoVisible != visible) { videoVisible = visible; invalidate(); }
    }

    private boolean insideCircle(float x, float y) {
        float dx = x - videoRect.centerX(), dy = y - videoRect.centerY();
        float radius = videoRect.width() / 2f;
        return radius > 0 && dx * dx + dy * dy <= radius * radius;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (message == null || isCheckBoxVisible() || mediaDelegate == null) {
            cancelVideoTouch();
            return super.onTouchEvent(event);
        }
        float x = event.getX(), y = event.getY();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && insideCircle(x, y)) {
            cancelVideoTouch();
            touching = true;
            pressed = true;
            downX = x; downY = y;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            longPress = () -> {
                longPress = null;
                cancelVideoTouch();
                onInteractiveLongPress(false);
                invalidate();
            };
            postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
            invalidate();
            return true;
        }
        if (touching) {
            if (action == MotionEvent.ACTION_UP) {
                boolean click = insideCircle(x, y);
                cancelVideoTouch();
                if (click) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    mediaDelegate.onMediaPressed(this, message);
                }
                invalidate();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                cancelVideoTouch();
                invalidate();
            } else if (action == MotionEvent.ACTION_MOVE) {
                float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (!insideCircle(x, y) || Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
                    cancelVideoTouch();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    invalidate();
                    return false;
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void cancelVideoTouch() {
        if (touching && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        touching = false;
        pressed = false;
        if (longPress != null) { removeCallbacks(longPress); longPress = null; }
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        radialProgress.onAttachedToWindow();
    }
    @Override protected void onDetachedFromWindow() {
        cancelVideoTouch();
        radialProgress.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }
}
