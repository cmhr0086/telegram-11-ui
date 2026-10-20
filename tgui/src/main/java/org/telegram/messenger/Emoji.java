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

    /**
     * tgui: upstream parses emoji ranges with its emoji tables; the input only uses the emoji-only
     * count to decide whether to trim whitespace, so this reports "not emoji-only".
     */
    public static java.util.ArrayList<Object> parseEmojis(CharSequence cs, int[] emojiOnly) {
        if (emojiOnly != null && emojiOnly.length > 0) {
            emojiOnly[0] = 0;
        }
        return new java.util.ArrayList<>();
    }
}
