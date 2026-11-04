package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;
import java.io.File;

/** Local sticker metadata; format and stable id are supplied by the host. */
public final class Sticker {
    public enum Format { WEBP, TGS, WEBM }
    public final long id;
    public final Format format;
    public final MediaSource source;
    public final Drawable thumbnail;
    public final int width, height;
    public final String emoji;

    public Sticker(long id, Format format, MediaSource source, Drawable thumbnail, int width, int height, String emoji) {
        if (format == null || source == null) throw new IllegalArgumentException("format and source are required");
        this.id = id;
        this.format = format;
        this.source = source;
        this.thumbnail = thumbnail;
        this.width = width > 0 ? width : 512;
        this.height = height > 0 ? height : 512;
        this.emoji = emoji == null ? "" : emoji;
    }

    public Sticker(long id, Format format, File file, Drawable thumbnail, int width, int height, String emoji) {
        this(id, format, new MediaSource(file), thumbnail, width, height, emoji);
    }
}
