package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.text.Layout;
import android.text.style.ReplacementSpan;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;

import java.util.List;

/**
 * tgui stub: custom (premium) emoji spans are a messenger feature that comes back in a later phase
 * (see README roadmap). The span keeps the custom emoji id so entities survive copy/paste and
 * sending, and reserves one emoji of space; drawing the animation is not implemented yet.
 */
public class AnimatedEmojiSpan extends ReplacementSpan {

    public long documentId;
    public TLRPC.Document document;
    public String emoji;
    public float size = AndroidUtilities.dp(20);
    public boolean fromEmojiKeyboard;
    private Paint.FontMetricsInt fontMetrics;

    public AnimatedEmojiSpan(long documentId, Paint.FontMetricsInt fontMetrics) {
        this(documentId, 1.2f, fontMetrics);
    }

    public AnimatedEmojiSpan(long documentId, float scale, Paint.FontMetricsInt fontMetrics) {
        this.documentId = documentId;
        applyFontMetrics(fontMetrics);
    }

    public AnimatedEmojiSpan(TLRPC.Document document, Paint.FontMetricsInt fontMetrics) {
        this(document != null ? document.id : 0, fontMetrics);
        this.document = document;
    }

    public long getDocumentId() {
        return document != null ? document.id : documentId;
    }

    public void applyFontMetrics(Paint.FontMetricsInt fontMetrics) {
        this.fontMetrics = fontMetrics;
        if (fontMetrics != null) {
            size = Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
            if (size == 0) {
                size = AndroidUtilities.dp(20);
            }
        }
    }

    public void applyFontMetrics(Paint.FontMetricsInt fontMetrics, int cacheType) {
        applyFontMetrics(fontMetrics);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null && fontMetrics != null) {
            fm.ascent = fontMetrics.ascent;
            fm.descent = fontMetrics.descent;
            fm.top = fontMetrics.top;
            fm.bottom = fontMetrics.bottom;
        }
        return (int) size;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        // tgui: animated emoji rendering is not ported yet; draw the fallback emoji text if the span covers one
        if (text != null && end > start) {
            canvas.drawText(text, start, end, x, y, paint);
        }
    }

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
