/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Components;

import android.text.TextUtils;

/** A host-provided emoji candidate. documentId=0 selects a Unicode emoji. */
public final class EmojiSuggestion {
    public final String id;
    public final CharSequence emoji;
    public final long documentId;
    public EmojiSuggestion(String id, CharSequence emoji, long documentId) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("suggestion id required");
        if (TextUtils.isEmpty(emoji)) throw new IllegalArgumentException("fallback emoji required");
        if (documentId < 0) throw new IllegalArgumentException("documentId must be nonnegative");
        this.id = id;
        // Keep only Unicode text; the receiving field creates its own replacement spans.
        this.emoji = emoji.toString();
        this.documentId = documentId;
    }
}
