/*
 * Adapted from Telegram for Android, release-11.4.2-5469.
 * Copyright Nikolai Kudashov and contributors. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in the project root.
 */
package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Custom emoji span whose local animation source is supplied by the host. */
public class AnimatedEmojiSpan extends ReplacementSpan {
    // Scoped by EditTextEffects during its UI-thread draw, including nested custom draws.
    private static boolean drawingEnabled = true;
    static boolean setDrawingEnabled(boolean enabled) {
        boolean previous = drawingEnabled;
        drawingEnabled = enabled;
        return previous;
    }
    public long documentId;
    public TLRPC.Document document;
    public String emoji;
    public float size = AndroidUtilities.dp(20);
    public boolean fromEmojiKeyboard;
    private final float scale;
    private Paint.FontMetricsInt fontMetrics;
    private int cacheType = -1;
    private int measuredSize;
    private AnimatedEmojiDrawable drawable;
    private long drawableDocumentId;
    private int drawableSize;
    // A span can appear in several layout groups, including several groups for one view.
    private final IdentityHashMap<View, Integer> bindings = new IdentityHashMap<>();

    public AnimatedEmojiSpan(long documentId, Paint.FontMetricsInt fontMetrics) {
        this(documentId, 1.2f, fontMetrics);
    }

    public AnimatedEmojiSpan(long documentId, float scale, Paint.FontMetricsInt fontMetrics) {
        this.documentId = documentId;
        this.scale = scale;
        applyFontMetrics(fontMetrics);
    }

    public AnimatedEmojiSpan(TLRPC.Document document, Paint.FontMetricsInt fontMetrics) {
        this(document != null ? document.id : 0, fontMetrics);
        this.document = document;
    }

    public long getDocumentId() { return document != null ? document.id : documentId; }

    public void applyFontMetrics(Paint.FontMetricsInt fontMetrics) {
        this.fontMetrics = copyMetrics(fontMetrics);
        if (fontMetrics != null) {
            size = Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
            if (size == 0) size = AndroidUtilities.dp(20);
        }
    }

    public void applyFontMetrics(Paint.FontMetricsInt fontMetrics, int cacheType) {
        this.cacheType = cacheType;
        applyFontMetrics(fontMetrics);
    }

    private static Paint.FontMetricsInt copyMetrics(Paint.FontMetricsInt source) {
        if (source == null) return null;
        Paint.FontMetricsInt copy = new Paint.FontMetricsInt();
        copy.top = source.top;
        copy.bottom = source.bottom;
        copy.ascent = source.ascent;
        copy.descent = source.descent;
        copy.leading = source.leading;
        return copy;
    }

    private int targetSize() { return Math.max(1, (int) (size * scale)); }

    @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        measuredSize = targetSize();
        if (fm != null) {
            if (fontMetrics == null) {
                int offset = AndroidUtilities.dp(8);
                int half = AndroidUtilities.dp(10);
                fm.top = fm.ascent = (int) ((-half - offset) * scale);
                fm.bottom = fm.descent = (int) ((half - offset) * scale);
                fm.leading = 0;
            } else {
                fm.ascent = fontMetrics.ascent;
                fm.descent = fontMetrics.descent;
                fm.top = fontMetrics.top;
                fm.bottom = fontMetrics.bottom;
                fm.leading = fontMetrics.leading;
            }
        }
        return Math.max(0, measuredSize - 1);
    }

    @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        int side = measuredSize > 0 ? measuredSize : targetSize();
        AnimatedEmojiDrawable current = drawingEnabled ? ensureDrawable(side) : null;
        if (current != null && current.isLoaded()) {
            float cy = top + (bottom - top) / 2f;
            int left = Math.round(x);
            int upper = Math.round(cy - side / 2f);
            current.setBounds(left, upper, left + side, upper + side);
            current.setAlpha(paint.getAlpha());
            current.draw(canvas);
        } else if (text != null && end > start) {
            canvas.drawText(text, start, end, x, y, paint);
        }
    }

    /** Current drawable, or null if no attached view owns this span. */
    public AnimatedEmojiDrawable getDrawable() { return ensureDrawable(targetSize()); }

    private AnimatedEmojiDrawable ensureDrawable(int side) {
        if (bindings.isEmpty()) return null;
        long id = getDocumentId();
        if (drawable == null || drawableDocumentId != id || drawableSize != side) {
            if (drawable != null) drawable.recycle();
            drawable = new AnimatedEmojiDrawable(id, side);
            drawableDocumentId = id;
            drawableSize = side;
            for (View view : bindings.keySet()) drawable.addView(view);
        }
        return drawable;
    }

    private void bind(View view) {
        Integer old = bindings.get(view);
        bindings.put(view, old == null ? 1 : old + 1);
        if (old == null) {
            AnimatedEmojiDrawable current = ensureDrawable(targetSize());
            // ensureDrawable binds all views when it creates a new drawable.
            if (current != null && bindings.size() > 1) current.addView(view);
        }
    }

    private void unbind(View view) {
        Integer old = bindings.get(view);
        if (old == null) return;
        if (old > 1) {
            bindings.put(view, old - 1);
        } else {
            bindings.remove(view);
            if (drawable != null) drawable.removeView(view);
            if (bindings.isEmpty()) {
                if (drawable != null) drawable.recycle();
                drawable = null;
            }
        }
    }

    public static void applyFontMetricsForString(CharSequence text, Paint textPaint) {
        if (text instanceof Spanned && textPaint != null) {
            Spanned spanned = (Spanned) text;
            for (AnimatedEmojiSpan span : spanned.getSpans(0, text.length(), AnimatedEmojiSpan.class)) {
                span.applyFontMetrics(textPaint.getFontMetricsInt());
            }
        }
    }

    // Rendering occurs in ReplacementSpan.draw, not in this legacy overlay hook.
    public static void drawAnimatedEmojis(Canvas canvas, Layout layout, EmojiGroupedSpans stack, float offset, List<?> spoilers, float boundTop, float boundBottom, float drawingYOffset, float alpha) { }
    public static void drawAnimatedEmojis(Canvas canvas, Layout layout, EmojiGroupedSpans stack, float offset, List<?> spoilers, float boundTop, float boundBottom, float drawingYOffset, float alpha, ColorFilter colorFilter) { }

    public static EmojiGroupedSpans update(int cacheType, View view, EmojiGroupedSpans prev, Layout... layouts) {
        return update(cacheType, view, false, prev, layouts);
    }

    public static EmojiGroupedSpans update(int cacheType, View view, boolean invalidateParent, EmojiGroupedSpans prev, Layout... layouts) {
        if (view == null) {
            if (prev != null) prev.release();
            return null;
        }
        if (prev == null || prev.released || prev.view != view) {
            if (prev != null) prev.release();
            prev = new EmojiGroupedSpans(view);
        }
        prev.update(layouts);
        return prev;
    }

    public static void release(View holder, EmojiGroupedSpans spans) {
        if (spans != null && (holder == null || spans.view == holder)) spans.release();
    }

    public static CharSequence cloneSpans(CharSequence text) { return cloneSpans(text, -1); }

    public static CharSequence cloneSpans(CharSequence text, int newCacheType) {
        if (!(text instanceof Spanned)) return text;
        Spanned original = (Spanned) text;
        Spannable copy = new SpannableString(text);
        for (AnimatedEmojiSpan span : original.getSpans(0, original.length(), AnimatedEmojiSpan.class)) {
            int start = original.getSpanStart(span), end = original.getSpanEnd(span), flags = original.getSpanFlags(span);
            AnimatedEmojiSpan cloned = new AnimatedEmojiSpan(span.documentId, span.scale, span.fontMetrics);
            cloned.document = span.document;
            cloned.emoji = span.emoji;
            cloned.size = span.size;
            cloned.fromEmojiKeyboard = span.fromEmojiKeyboard;
            cloned.cacheType = newCacheType < 0 ? span.cacheType : newCacheType;
            copy.removeSpan(span);
            copy.setSpan(cloned, start, end, flags);
        }
        return copy;
    }

    public static class EmojiGroupedSpans implements View.OnAttachStateChangeListener {
        private final View view;
        private final IdentityHashMap<Layout, Set<AnimatedEmojiSpan>> layouts = new IdentityHashMap<>();
        private final Set<AnimatedEmojiSpan> spans = identitySet();
        private boolean bound;
        private boolean released;

        public EmojiGroupedSpans() {
            this.view = null;
        }

        private EmojiGroupedSpans(View view) {
            this.view = view;
            view.addOnAttachStateChangeListener(this);
            bound = view.isAttachedToWindow();
        }

        private static Set<AnimatedEmojiSpan> identitySet() {
            return Collections.newSetFromMap(new IdentityHashMap<>());
        }

        private static Set<AnimatedEmojiSpan> scan(Layout layout) {
            Set<AnimatedEmojiSpan> result = identitySet();
            if (layout != null && layout.getText() instanceof Spanned) {
                Spanned text = (Spanned) layout.getText();
                Collections.addAll(result, text.getSpans(0, text.length(), AnimatedEmojiSpan.class));
            }
            return result;
        }

        private void update(Layout... current) {
            layouts.clear();
            if (current != null) for (Layout layout : current) {
                if (layout != null) layouts.put(layout, scan(layout));
            }
            reconcile();
        }

        private void reconcile() {
            Set<AnimatedEmojiSpan> next = identitySet();
            for (Set<AnimatedEmojiSpan> group : layouts.values()) next.addAll(group);
            if (bound) {
                for (AnimatedEmojiSpan span : new ArrayList<>(spans)) if (!next.contains(span)) span.unbind(view);
                for (AnimatedEmojiSpan span : next) if (!spans.contains(span)) span.bind(view);
            }
            spans.clear();
            spans.addAll(next);
        }

        public boolean isEmpty() { return spans.isEmpty(); }
        public void clearPositions() { }
        public void recordPositions(boolean record) { }
        public void replaceLayout(Layout newText, Layout oldText) {
            layouts.remove(oldText);
            if (newText != null) layouts.put(newText, scan(newText));
            reconcile();
        }
        @Override public void onViewAttachedToWindow(View v) {
            if (!released && !bound) {
                bound = true;
                for (AnimatedEmojiSpan span : spans) span.bind(view);
                view.invalidate();
            }
        }
        @Override public void onViewDetachedFromWindow(View v) {
            if (bound) {
                for (AnimatedEmojiSpan span : spans) span.unbind(view);
                bound = false;
            }
        }
        private void release() {
            if (released) return;
            if (bound) for (AnimatedEmojiSpan span : spans) span.unbind(view);
            bound = false;
            released = true;
            spans.clear();
            layouts.clear();
            if (view != null) view.removeOnAttachStateChangeListener(this);
        }
    }
}
