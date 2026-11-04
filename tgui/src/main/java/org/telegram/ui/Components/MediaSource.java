package org.telegram.ui.Components;

import java.io.File;

/** Host-owned local media. A loader may prepare a file asynchronously; tgui performs no network I/O. */
public final class MediaSource {
    public interface Request { void cancel(); }
    public interface Result { void onLoaded(File localFile); void onFailed(); }
    public interface Loader { Request load(Result result); }

    public final File file;
    public final Loader loader;

    public MediaSource(File file) {
        if (file == null) throw new IllegalArgumentException("file is required");
        this.file = file;
        this.loader = null;
    }

    public MediaSource(Loader loader) {
        if (loader == null) throw new IllegalArgumentException("loader is required");
        this.file = null;
        this.loader = loader;
    }
}
