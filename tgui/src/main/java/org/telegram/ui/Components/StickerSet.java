package org.telegram.ui.Components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Snapshot of one host-provided pack. Installation and favorites are host state. */
public final class StickerSet {
    public final long id;
    public final CharSequence title;
    public final List<Sticker> stickers;

    public StickerSet(long id, CharSequence title, List<Sticker> stickers) {
        if (title == null || stickers == null) throw new IllegalArgumentException("title and stickers are required");
        ArrayList<Sticker> copy = new ArrayList<>(stickers);
        java.util.HashSet<Long> ids = new java.util.HashSet<>();
        for (Sticker item : copy) if (item == null || !ids.add(item.id)) throw new IllegalArgumentException("pack ids must be unique");
        this.id = id;
        this.title = title.toString();
        this.stickers = Collections.unmodifiableList(copy);
    }
}
