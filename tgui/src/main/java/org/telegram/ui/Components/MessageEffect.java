/*
 * This is the source code of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov and contributors.
 */
package org.telegram.ui.Components;

/** Immutable host-owned effect. Both ids are nonzero stable host ids (not Telegram ids).
 * The animation id resolves through AnimatedEmojiDrawable.SourceProvider to a local JSON/TGS.
 * A null MessageEffect, rather than id zero, represents cancelling an effect selection. */
public final class MessageEffect {
    public final long id;
    public final String emoticon;
    public final long animationDocumentId;

    public MessageEffect(long id, String emoticon, long animationDocumentId) {
        if (id == 0 || animationDocumentId == 0) throw new IllegalArgumentException("Effect and animation ids must be nonzero");
        if (emoticon == null || emoticon.isEmpty()) throw new IllegalArgumentException("Effect emoticon required");
        this.id = id; this.emoticon = emoticon; this.animationDocumentId = animationDocumentId;
    }
}
