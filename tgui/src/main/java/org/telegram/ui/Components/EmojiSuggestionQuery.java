/*
 * Adapted from Telegram for Android release-11.4.2-5469, SuggestEmojiView.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Components;

import android.text.Spanned;
import org.telegram.messenger.Emoji;

/** Immutable search request. All ranges use Android UTF-16 indices. */
public final class EmojiSuggestionQuery {
    public enum Type { KEYWORD, EMOJI }
    public final Type type;
    public final String text, query;
    public final int start, end;

    private EmojiSuggestionQuery(Type type, CharSequence text, int start, int end) {
        this.type = type; this.text = text.toString(); this.start = start; this.end = end;
        query = this.text.substring(start, end);
    }

    public static EmojiSuggestionQuery parse(CharSequence text, int selectionStart, int selectionEnd) {
        if (text == null || selectionStart != selectionEnd || selectionEnd <= 0 || selectionEnd > text.length()) return null;
        if (selectionEnd < text.length() && Character.isHighSurrogate(text.charAt(selectionEnd - 1)) && Character.isLowSurrogate(text.charAt(selectionEnd))) return null;
        if (text instanceof Spanned) {
            Spanned spans = (Spanned) text;
            for (AnimatedEmojiSpan span : spans.getSpans(selectionEnd, selectionEnd, AnimatedEmojiSpan.class)) {
                if (spans.getSpanStart(span) < selectionEnd && spans.getSpanEnd(span) >= selectionEnd) return null;
            }
            Emoji.EmojiSpan last = null;
            Emoji.EmojiSpan[] nearby = spans.getSpans(Math.max(0, selectionEnd - 24), selectionEnd, Emoji.EmojiSpan.class);
            for (Emoji.EmojiSpan span : nearby) {
                if (spans.getSpanEnd(span) == selectionEnd && (last == null || spans.getSpanStart(span) > spans.getSpanStart(last))) last = span;
            }
            if (last != null) return new EmojiSuggestionQuery(Type.EMOJI, text, spans.getSpanStart(last), selectionEnd);
            if (nearby.length > 0) return null;
        }
        // The original keyword search uses the entire prefix, not the last word.
        return selectionEnd < 52 ? new EmojiSuggestionQuery(Type.KEYWORD, text, 0, selectionEnd) : null;
    }

    public boolean matches(CharSequence value, int start, int end) {
        if (value == null || !text.contentEquals(value) || start != this.end || end != this.end) return false;
        EmojiSuggestionQuery current = parse(value, start, end);
        return current != null && current.type == type && current.start == this.start;
    }
}
