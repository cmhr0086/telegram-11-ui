package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;

/**
 * tgui stub: upstream AnimatedEmojiDrawable loads premium custom emoji documents from the
 * server. Only the constants and {@link SwapAnimatedEmojiDrawable} (as a plain drawable
 * holder used for title/value icons) are kept.
 */
public class AnimatedEmojiDrawable {

    public static final int CACHE_TYPE_MESSAGES = 0;
    public static final int CACHE_TYPE_MESSAGES_LARGE = 1;
    public static final int CACHE_TYPE_KEYBOARD = 2;
    public static final int CACHE_TYPE_ALERT_PREVIEW = 3;
    public static final int CACHE_TYPE_ALERT_PREVIEW_LARGE = 4;
    public static final int CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP = 5;
    public static final int CACHE_TYPE_TAB_STRIP = 6;
    public static final int CACHE_TYPE_EMOJI_STATUS = 7;
    public static final int CACHE_TYPE_ALERT_EMOJI_STATUS = 9;
    public static final int CACHE_TYPE_FORUM_TOPIC = 10;
    public static final int CACHE_TYPE_FORUM_TOPIC_LARGE = 11;
    public static final int CACHE_TYPE_COLORABLE = 19;
    public static final int CACHE_TYPE_STANDARD_EMOJI = 20;
    public static final int CACHE_TYPE_ALERT_STANDARD_EMOJI = 21;

    public static int getCacheTypeForEnterView() {
        return CACHE_TYPE_MESSAGES;
    }

    public static class SwapAnimatedEmojiDrawable extends Drawable {

        public boolean center = false;
        private final int size;
        private View parentView;
        private Drawable drawable;
        private Integer color;
        private int alpha = 255;
        private final AnimatedFloat changeProgress;

        public SwapAnimatedEmojiDrawable(View parentView, int size) {
            this(parentView, false, size, CACHE_TYPE_EMOJI_STATUS);
        }

        public SwapAnimatedEmojiDrawable(View parentView, boolean invalidateParent, int size) {
            this(parentView, invalidateParent, size, CACHE_TYPE_EMOJI_STATUS);
        }

        public SwapAnimatedEmojiDrawable(View parentView, int size, int cacheType) {
            this(parentView, false, size, cacheType);
        }

        public SwapAnimatedEmojiDrawable(View parentView, boolean invalidateParent, int size, int cacheType) {
            this.parentView = parentView;
            this.size = size;
            changeProgress = new AnimatedFloat(this::invalidate, 300, CubicBezierInterpolator.EASE_OUT);
        }

        public void setParentView(View parentView) {
            this.parentView = parentView;
        }

        public void play() {
        }

        public void setColor(Integer color) {
            this.color = color;
            if (drawable != null) {
                drawable.setColorFilter(color == null ? null : new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
        }

        public Integer getColor() {
            return color;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (drawable == null) {
                return;
            }
            int w = drawable.getIntrinsicWidth() > 0 ? Math.min(size, drawable.getIntrinsicWidth()) : size;
            int h = drawable.getIntrinsicHeight() > 0 ? Math.min(size, drawable.getIntrinsicHeight()) : size;
            int cx = getBounds().centerX(), cy = getBounds().centerY();
            drawable.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            drawable.setAlpha(alpha);
            drawable.draw(canvas);
        }

        public Drawable getDrawable() {
            return drawable;
        }

        public void resetAnimation() {
        }

        public float isNotEmpty() {
            return drawable != null ? 1f : 0f;
        }

        public boolean isEmpty() {
            return drawable == null;
        }

        public void removeOldDrawable() {
        }

        public void set(Drawable drawable, boolean animated) {
            this.drawable = drawable;
            if (drawable != null && color != null) {
                drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
            invalidate();
        }

        public void detach() {
        }

        public void attach() {
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int i) {
            alpha = i;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        public void invalidate() {
            if (parentView != null) {
                parentView.invalidate();
            }
            invalidateSelf();
        }

        public void setSecondParent(View secondParent) {
        }

        public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        }
    }
}
