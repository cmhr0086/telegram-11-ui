package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.text.Layout;
import android.view.View;

import java.util.List;

/**
 * tgui stub: custom (premium) emoji spans are a messenger feature. Static helpers are kept as
 * no-ops so text views copied from upstream compile unchanged.
 */
public class AnimatedEmojiSpan {

    public static void applyFontMetricsForString(CharSequence text, Paint textPaint) {
    }

    public static void drawAnimatedEmojis(Canvas canvas, Layout layout, EmojiGroupedSpans stack, float offset, List<?> spoilers, float boundTop, float boundBottom, float drawingYOffset, float alpha) {
    }

    public static void drawAnimatedEmojis(Canvas canvas, Layout layout, EmojiGroupedSpans stack, float offset, List<?> spoilers, float boundTop, float boundBottom, float drawingYOffset, float alpha, ColorFilter colorFilter) {
    }

    public static EmojiGroupedSpans update(int cacheType, View view, EmojiGroupedSpans prev, Layout... layouts) {
        return prev;
    }

    public static EmojiGroupedSpans update(int cacheType, View view, boolean invalidateParent, EmojiGroupedSpans prev, Layout... layouts) {
        return prev;
    }

    public static void release(View holder, EmojiGroupedSpans spans) {
    }

    public static CharSequence cloneSpans(CharSequence text) {
        return text;
    }

    public static CharSequence cloneSpans(CharSequence text, int newCacheType) {
        return text;
    }

    public static class EmojiGroupedSpans {
        public boolean isEmpty() {
            return true;
        }

        public void clearPositions() {
        }

        public void recordPositions(boolean record) {
        }

        public void replaceLayout(Layout newText, Layout oldText) {
        }
    }
}
