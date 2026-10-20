package org.telegram.messenger;

import android.graphics.Paint;

/**
 * tgui stub: upstream replaces emoji with bundled Apple-style bitmaps (assets/emoji).
 * Phase 1 renders system emoji, so replaceEmoji() returns the text as-is.
 */
public class Emoji {

    public static boolean emojiDrawingUseAlpha = true;

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, boolean createNew) {
        return cs;
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, int size, boolean createNew) {
        return cs;
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, int size, boolean createNew, int[] emojiOnly) {
        return cs;
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, boolean createNew, int[] emojiOnly) {
        return cs;
    }

    public static CharSequence replaceWithRestrictedEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, Runnable update) {
        return cs;
    }
}
