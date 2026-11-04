package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;
import java.io.File;

/** A host-provided local animated GIF, with known dimensions for the flow layout. */
public final class Gif {
    public final long id;
    public final MediaSource source;
    public final Drawable thumbnail;
    public final int width, height;

    public Gif(long id, MediaSource source, Drawable thumbnail, int width, int height) {
        if (source == null) throw new IllegalArgumentException("source is required");
        this.id = id;
        this.source = source;
        this.thumbnail = thumbnail;
        this.width = width > 0 ? width : 320;
        this.height = height > 0 ? height : 240;
    }

    public Gif(long id, File file, Drawable thumbnail, int width, int height) {
        this(id, new MediaSource(file), thumbnail, width, height);
    }
}
