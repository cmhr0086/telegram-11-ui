/*
 * Adapted from Telegram for Android, release-11.4.2-5469, ChatActivity.java.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Cells;

/** Pure subset of ChatActivity's adjacent-message grouping. */
public final class MessageGrouping {
    private MessageGrouping() { }
    public static boolean canMerge(TextBubbleMessage first, TextBubbleMessage second) {
        if (first == null || second == null || first.isOut != second.isOut || first.group != second.group
                || first.date <= 0 || second.date <= 0 || first.sender == null || second.sender == null
                || first.sender.id == 0 || first.sender.id != second.sender.id) return false;
        // ChatActivity: a keyboard below the upper message separates the next bubble.
        if (first.inlineKeyboard != null && !first.inlineKeyboard.isEmpty()) return false;
        // Subtraction without overflow for positive Unix timestamps.
        long earlier = Math.min(first.date, second.date), later = Math.max(first.date, second.date);
        return later - earlier <= 5 * 60;
    }
}
