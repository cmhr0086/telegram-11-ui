/*
 * Adapted from Telegram for Android release-11.4.2-5469, MentionsAdapter.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Components;

/** A host-search request and its exact UTF-16 replacement range. */
public final class MentionQuery {
    public final String text;
    public final String query;
    public final int start, end;

    private MentionQuery(String text, int start, int end) {
        this.text = text;
        this.start = start;
        this.end = end;
        query = text.substring(start + 1, end);
    }

    public static MentionQuery parse(CharSequence value, int selectionStart, int selectionEnd) {
        if (value == null || selectionStart != selectionEnd || selectionStart <= 0 || selectionStart > value.length()) return null;
        // Android indices are UTF-16; never split a supplementary character.
        if (selectionStart < value.length() && Character.isHighSurrogate(value.charAt(selectionStart - 1))
                && Character.isLowSurrogate(value.charAt(selectionStart))) return null;
        String text = value.toString();
        // Upstream scans backwards and permits spaces for full-name prefix searches.
        for (int i = selectionStart - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            boolean boundary = i == 0 || text.charAt(i - 1) == ' ' || text.charAt(i - 1) == '\n';
            if (boundary && ch == '@') return new MentionQuery(text, i, selectionStart);
            // Other upstream suggestion types own these tokens.
            if (boundary && (ch == '#' || i == 0 && ch == '/') || ch == ':' && i + 1 < selectionStart) return null;
        }
        return null;
    }

    public boolean matches(CharSequence value, int selectionStart, int selectionEnd) {
        return selectionStart == end && selectionEnd == end && value != null && text.contentEquals(value);
    }
}
