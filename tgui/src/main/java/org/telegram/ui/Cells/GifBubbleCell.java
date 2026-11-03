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
import android.graphics.Path;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LocalMediaDrawable;
import org.telegram.ui.Components.Gif;

/**
 * Local GIF rendering in the existing photo bubble. ChatMessageCell groups GIF
 * with photos in setMessageContent (release-11.4.2-5469, 8640-8723): the original
 * max dimensions, 120dp minimum, caption, reply, corners and time are inherited.
 * Its ImageReceiver/DownloadController branches are replaced by a host source.
 */
public class GifBubbleCell extends PhotoBubbleCell {
    private final LocalMediaDrawable media = new LocalMediaDrawable(this);
    private final Path clipPath = new Path();
    private boolean animating = true;
    private boolean photoVisible = true;
    private Gif boundGif;

    public GifBubbleCell(Context context) { this(context, null); }
    public GifBubbleCell(Context context, Theme.ResourcesProvider provider) {
        super(context, provider);
        // The platform Movie fallback (API 21-27) requires a software canvas.
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        if (message != null && (message.type != TextBubbleMessage.TYPE_GIF || message.gif == null)) {
            throw new IllegalArgumentException("GifBubbleCell requires a GIF message");
        }
        if (this.message != message) {
            MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            super.onTouchEvent(cancel);
            cancel.recycle();
            photoVisible = true;
        }
        if (message != null) {
            message.photoWidth = message.gif.width;
            message.photoHeight = message.gif.height;
            message.photo = message.gif.thumbnail;
        }
        layoutForWidth = -1;
        super.setMessage(message, pinnedTop, pinnedBottom);
        Gif next = message == null ? null : message.gif;
        if (boundGif != next) {
            boundGif = next;
            media.setGif(next);
        }
        if (message == null) photoRect.setEmpty();
        updateAnimation();
        requestLayout();
    }

    @Override protected void drawPhoto(Canvas canvas) {
        clipPath.rewind();
        clipPath.addRoundRect(photoRect, radii, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        // PhotoBubbleCell may widen a portrait frame for its caption. Keep the
        // same center-crop policy as ImageReceiver rather than stretching it.
        float scale = Math.max(photoRect.width() / message.gif.width, photoRect.height() / message.gif.height);
        float w = message.gif.width * scale, h = message.gif.height * scale;
        media.setBounds(Math.round(photoRect.centerX() - w / 2), Math.round(photoRect.centerY() - h / 2),
                Math.round(photoRect.centerX() + w / 2), Math.round(photoRect.centerY() + h / 2));
        media.draw(canvas);
        canvas.restoreToCount(save);
    }

    public void setAnimating(boolean enabled) { animating = enabled; updateAnimation(); }
    public boolean isMediaLoaded() { return media.isLoaded(); }
    public int getCurrentFrame() { return media.getCurrentFrame(); }
    @Override public void setPhotoVisible(boolean visible) {
        photoVisible = visible;
        super.setPhotoVisible(visible);
        updateAnimation();
    }
    private void updateAnimation() { media.setAnimating(animating && photoVisible && isShown() && getWindowVisibility() == VISIBLE); }
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
