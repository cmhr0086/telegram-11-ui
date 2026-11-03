/*
 * Adapted from Telegram for Android, release-11.4.2-5469 (StickerEmojiCell / ImageReceiver).
 * Copyright Nikolai Kudashov and contributors. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in the project root.
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;

/** Fits a host-owned local sticker or GIF inside this view. */
public class LocalMediaView extends View {
    private final LocalMediaDrawable media;
    private boolean requestedAnimation = true;

    public LocalMediaView(Context context) {
        super(context);
        media = new LocalMediaDrawable(this);
        if (Build.VERSION.SDK_INT < 28) setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setSticker(Sticker sticker) { media.setSticker(sticker); requestLayout(); invalidate(); }
    public void setGif(Gif gif) { media.setGif(gif); requestLayout(); invalidate(); }
    public void setAnimating(boolean value) { requestedAnimation = value; updateVisibility(); }
    public LocalMediaDrawable getMediaDrawable() { return media; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateVisibility(); }
    @Override protected void onDetachedFromWindow() { media.detach(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changedView, int visibility) { super.onVisibilityChanged(changedView, visibility); if (media != null) updateVisibility(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if (media != null) updateVisibility(); }
    private void updateVisibility() {
        boolean visible = isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE;
        if (visible) media.attach(); else media.detach();
        media.setAnimating(requestedAnimation && visible);
    }
    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); media.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom()); media.draw(canvas); }
}
