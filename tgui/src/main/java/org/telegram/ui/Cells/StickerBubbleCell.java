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
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LocalMediaDrawable;
import org.telegram.ui.Components.Sticker;

/**
 * ChatMessageCell's ordinary sticker branch, reduced to host Sticker metadata.
 * Source: release-11.4.2-5469, setMessageContent (8492-8519, 8577-8578,
 * 8612-8619, 9311-9360), onLayout (11725-11753), drawTimeInternal (20476-20550).
 * Account/document loading, Premium effects and dice are removed. The existing
 * photo cell supplies media tap, long press, selection and visibility APIs.
 * Sticker replies/forward headers are not laid out in this first extraction.
 */
public class StickerBubbleCell extends PhotoBubbleCell {
    private static final int STICKER_STATUS_OFFSET = 6;
    private final LocalMediaDrawable media = new LocalMediaDrawable(this);
    private int photoWidth, photoHeight;
    private boolean photoVisible = true;
    private boolean animating = true;
    private Sticker boundSticker;

    public StickerBubbleCell(Context context) { this(context, null); }
    public StickerBubbleCell(Context context, Theme.ResourcesProvider provider) {
        super(context, provider);
    }

    @Override public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        if (message != null && (message.type != TextBubbleMessage.TYPE_STICKER || message.sticker == null)) {
            throw new IllegalArgumentException("StickerBubbleCell requires a sticker message");
        }
        if (this.message != message) {
            MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            super.onTouchEvent(cancel);
            cancel.recycle();
            photoVisible = true;
        }
        // A host may replace the media on the same message object.
        layoutForWidth = -1;
        super.setMessage(message, pinnedTop, pinnedBottom);
        Sticker next = message == null ? null : message.sticker;
        if (boundSticker != next) {
            boundSticker = next;
            media.setSticker(next);
        }
        if (message == null) photoRect.setEmpty();
        updateAnimation();
        requestLayout();
    }

    @Override protected void buildLayout(int width) {
        layoutForWidth = width;
        layoutForText = message.text;
        layoutForReply = message.replyTo;
        setReactionsLayout();
        photoWidth = message.sticker.width;
        photoHeight = message.sticker.height;
        float maxHeight;
        int maxWidth;
        if (AndroidUtilities.isTablet()) {
            maxHeight = maxWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.4f);
        } else {
            maxHeight = maxWidth = (int) (Math.min(width, AndroidUtilities.displaySize.y) * 0.5f);
        }
        if (photoWidth == 0) {
            photoHeight = (int) maxHeight;
            photoWidth = photoHeight + AndroidUtilities.dp(100);
        }
        photoHeight *= maxWidth / (float) photoWidth;
        photoWidth = (int) maxWidth;
        if (photoHeight > maxHeight) {
            photoWidth *= maxHeight / photoHeight;
            photoHeight = (int) maxHeight;
        }
        photoWidth = Math.max(1, photoWidth);
        photoHeight = Math.max(1, photoHeight);
        backgroundWidth = photoWidth + AndroidUtilities.dp(12);
        totalHeight = photoHeight + AndroidUtilities.dp(14) - AndroidUtilities.dp(4);
        if (!reactionsLayoutInBubble.isEmpty) {
            reactionsLayoutInBubble.measure(maxWidth + AndroidUtilities.dp(36), message.isOut ? Gravity.RIGHT : Gravity.LEFT);
            reactionsLayoutInBubble.drawServiceShaderBackground = 1f;
            reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(8);
            reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(4);
            totalHeight += reactionsLayoutInBubble.totalHeight;
        }
        measureTime();
    }

    @Override protected void drawContent(Canvas canvas) {
        // RecyclerView may rebind while temporarily detached (isShown=false),
        // then reattach its scrap without an onAttachedToWindow callback.
        // Recheck here so a visible sticker resumes without restarting its loop.
        updateAnimation();
        int x = message.isOut ? getMeasuredWidth() - backgroundWidth - AndroidUtilities.dp(5) : AndroidUtilities.dp(13) + incomingAvatarInset();
        int y = AndroidUtilities.dp(pinnedTop ? 4 : 5);
        photoRect.set(x, y, x + photoWidth, y + photoHeight);
        bubbleBottom = getMeasuredHeight() - AndroidUtilities.dp(2);
        if (photoVisible) {
            media.setBounds(x, y, x + photoWidth, y + photoHeight);
            media.draw(canvas);
        }
        if (hasReactionsToDraw()) {
            reactionsLayoutInBubble.x = message.isOut ? getMeasuredWidth() - reactionsLayoutInBubble.width - AndroidUtilities.dp(16) : x;
            reactionsLayoutInBubble.y = (int) photoRect.bottom + AndroidUtilities.dp(4);
            applyServiceShaderMatrix();
            drawReactions(canvas);
        }
        drawStickerTime(canvas);
        if (!message.isOut) drawSideAuthor(canvas, sideAuthorBackgroundRight(true));
    }

    protected void drawStickerTime(Canvas canvas) {
        // Upstream sticker status is inset by six dp; its time pill has an
        // additional two dp at each side and a 12dp radius.
        float timeX = message.isOut ? getMeasuredWidth() - timeWidth - AndroidUtilities.dp(42)
                : Math.max(AndroidUtilities.dp(26), backgroundWidth - AndroidUtilities.dp(4) - timeWidth) + incomingAvatarInset();
        timeX -= AndroidUtilities.dp(STICKER_STATUS_OFFSET);
        float x1 = timeX - AndroidUtilities.dp(4);
        float offsetX = AndroidUtilities.dp(2);
        float top = photoRect.bottom - AndroidUtilities.dp(23);
        float height = Math.max(AndroidUtilities.dp(17), Theme.chat_timePaint.getTextSize() + AndroidUtilities.dp(5));
        timeRect.set(x1 - offsetX, top, offsetX + x1 + timeWidth + AndroidUtilities.dp(message.isOut ? 28 : 8), top + height);
        applyServiceShaderMatrix();
        Paint paint = resourcesProvider == null ? Theme.chat_actionBackgroundPaint : resourcesProvider.getPaint(Theme.key_paint_chatActionBackground);
        if (paint == null) paint = Theme.chat_actionBackgroundPaint;
        int oldAlpha = paint.getAlpha();
        paint.setAlpha((int) (oldAlpha * .6f));
        canvas.drawRoundRect(timeRect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);
        paint.setAlpha(oldAlpha);
        if (resourcesProvider == null ? Theme.hasGradientService() : resourcesProvider.hasGradientService()) {
            canvas.drawRoundRect(timeRect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), Theme.chat_actionBackgroundGradientDarkenPaint);
        }
        Theme.chat_timePaint.setColor(getThemedColor(Theme.key_chat_serviceText));
        canvas.save();
        canvas.translate(timeX - timeLayout.getLineLeft(0), photoRect.bottom - AndroidUtilities.dp(7.3f) - timeLayout.getHeight());
        timeLayout.draw(canvas);
        canvas.restore();
        if (message.isOut) drawStatus(canvas, (int) timeRect.right + AndroidUtilities.dp(13), (int) timeRect.bottom + AndroidUtilities.dp(4), true, true);
    }

    public void setAnimating(boolean enabled) { animating = enabled; updateAnimation(); }
    public boolean isMediaLoaded() { return media.isLoaded(); }
    public int getCurrentFrame() { return media.getCurrentFrame(); }
    @Override public void setPhotoVisible(boolean visible) {
        photoVisible = visible;
        super.setPhotoVisible(visible);
        updateAnimation();
        invalidate();
    }
    private void updateAnimation() {
        media.setAnimating(animating && photoVisible && isShown() && getWindowVisibility() == VISIBLE);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); media.attach(); updateAnimation(); }
    @Override protected void onDetachedFromWindow() { media.detach(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (media != null) updateAnimation();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (media != null) updateAnimation();
    }
}
