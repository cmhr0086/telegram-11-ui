/*
 * Adapted from Telegram for Android, release-11.4.2-5469.
 * Copyright Nikolai Kudashov and contributors. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in the project root.
 */
package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.zip.GZIPInputStream;

/** An animated emoji backed exclusively by a host-provided local JSON or TGS file. */
public class AnimatedEmojiDrawable extends Drawable {
    public static final int CACHE_TYPE_MESSAGES = 0;
    public static final int CACHE_TYPE_MESSAGES_LARGE = 1;
    public static final int CACHE_TYPE_KEYBOARD = 2;
    public static final int CACHE_TYPE_ALERT_PREVIEW = 3;
    public static final int CACHE_TYPE_ALERT_PREVIEW_LARGE = 4;
    public static final int CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP = 5;
    public static final int CACHE_TYPE_TAB_STRIP = 6;
    public static final int CACHE_TYPE_EMOJI_STATUS = 7;
    public static final int CACHE_TYPE_ALERT_EMOJI_STATUS = 9;
    public static final int CACHE_TYPE_FORUM_TOPIC = 10;
    public static final int CACHE_TYPE_FORUM_TOPIC_LARGE = 11;
    public static final int CACHE_TYPE_COLORABLE = 19;
    public static final int CACHE_TYPE_STANDARD_EMOJI = 20;
    public static final int CACHE_TYPE_ALERT_STANDARD_EMOJI = 21;
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private static final Object SOURCE_LOCK = new Object();
    private static final Set<AnimatedEmojiDrawable> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile SourceProvider sourceProvider;
    private static volatile long sourceVersion;

    public interface SourceProvider { File getFile(long documentId); }

    /** Call again when the mapping or contents change to refresh existing instances. */
    public static void setSourceProvider(@Nullable SourceProvider provider) {
        final ArrayList<AnimatedEmojiDrawable> active;
        synchronized (SOURCE_LOCK) {
            sourceProvider = provider;
            sourceVersion++;
            active = new ArrayList<>(ACTIVE);
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (AnimatedEmojiDrawable drawable : active) {
                for (View parent : new ArrayList<>(drawable.parents)) parent.invalidate();
                drawable.invalidateSelf();
            }
        });
    }

    @Nullable public static SourceProvider getSourceProvider() { return sourceProvider; }
    public static long getSourceVersion() { return sourceVersion; }
    public static boolean hasSource(long id) {
        SourceProvider provider = sourceProvider;
        if (provider == null) return false;
        try {
            File file = provider.getFile(id);
            return file != null && file.isFile() && file.canRead();
        } catch (RuntimeException e) {
            return false;
        }
    }
    public static int getCacheTypeForEnterView() { return CACHE_TYPE_MESSAGES; }

    private final long documentId;
    private final int size;
    private final ArrayList<View> parents = new ArrayList<>();
    private RLottieDrawable animation;
    private File privateFile;
    private long loadedVersion = -1;
    private int alpha = 255;
    private ColorFilter colorFilter;

    public AnimatedEmojiDrawable(long documentId, int sizePx) {
        if (sizePx <= 0) throw new IllegalArgumentException("sizePx must be positive");
        this.documentId = documentId;
        this.size = sizePx;
    }

    /** Attach and detach on the UI thread; a drawable may serve multiple parent views. */
    public void addView(View view) {
        if (view == null || parents.contains(view)) return;
        parents.add(view);
        synchronized (SOURCE_LOCK) { ACTIVE.add(this); }
        if (animation != null && loadedVersion == sourceVersion) animation.addParentView(view);
        ensureLoaded();
        if (animation != null) animation.start();
    }

    public void removeView(View view) {
        if (view == null || !parents.remove(view)) return;
        if (animation != null) animation.removeParentView(view);
        if (parents.isEmpty()) {
            synchronized (SOURCE_LOCK) { ACTIVE.remove(this); }
            releaseAnimation();
            loadedVersion = -1;
        }
    }

    /** Releases native state and all parent references. It may be attached again later. */
    public void recycle() {
        synchronized (SOURCE_LOCK) { ACTIVE.remove(this); }
        releaseAnimation();
        parents.clear();
        loadedVersion = -1;
    }

    public boolean isLoaded() {
        if (!parents.isEmpty()) ensureLoaded();
        return animation != null && animation.canLoadFrames();
    }
    public int getCurrentFrame() { return animation == null ? -1 : animation.getCurrentFrame(); }

    private void ensureLoaded() {
        long version = sourceVersion;
        if (loadedVersion == version) return;
        releaseAnimation();
        loadedVersion = version;
        SourceProvider provider = sourceProvider;
        if (provider == null || ApplicationLoader.applicationContext == null) return;
        File copy = null;
        try {
            File original = provider.getFile(documentId);
            if (original == null || !original.isFile() || !original.canRead()) return;
            copy = copyLocalJson(original);
            RLottieDrawable loaded = new RLottieDrawable(copy, size, size, null, false);
            if (!loaded.canLoadFrames()) {
                loaded.recycle(true);
                copy.delete();
                return;
            }
            privateFile = copy;
            animation = loaded;
            animation.setAutoRepeat(1);
            animation.setAlpha(alpha);
            animation.setColorFilter(colorFilter);
            for (View parent : parents) animation.addParentView(parent);
            animation.start();
        } catch (Exception | LinkageError e) {
            if (copy != null) copy.delete();
        }
    }

    private static File copyLocalJson(File original) throws IOException {
        File directory = new File(ApplicationLoader.applicationContext.getCacheDir(), "animated-emoji");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) throw new IOException("Cannot create animation cache");
        File copy = File.createTempFile("emoji-", ".json", directory);
        boolean success = false;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(original))) {
            input.mark(2);
            int first = input.read(), second = input.read();
            input.reset();
            try (InputStream data = first == 0x1f && second == 0x8b ? new GZIPInputStream(input) : input;
                 FileOutputStream output = new FileOutputStream(copy)) {
                byte[] buffer = new byte[8192];
                int total = 0, read;
                while ((read = data.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_JSON_BYTES) throw new IOException("Animation exceeds size limit");
                    output.write(buffer, 0, read);
                }
                if (total == 0) throw new IOException("Empty animation");
            }
            success = true;
            return copy;
        } finally {
            if (!success) copy.delete();
        }
    }

    private void releaseAnimation() {
        if (animation != null) {
            for (View parent : parents) animation.removeParentView(parent);
            animation.setCallback(null);
            animation.setMasterParent(null);
            animation.stop();
            animation.recycle(true);
            animation = null;
        }
        if (privateFile != null) {
            privateFile.delete();
            privateFile = null;
        }
    }

    @Override public void draw(@NonNull Canvas canvas) {
        if (!parents.isEmpty()) ensureLoaded();
        if (animation != null) {
            animation.setBounds(getBounds());
            animation.draw(canvas);
        }
    }
    @Override public void setAlpha(int alpha) {
        this.alpha = alpha;
        if (animation != null) animation.setAlpha(alpha);
        invalidateSelf();
    }
    @Override public void setColorFilter(@Nullable ColorFilter filter) {
        colorFilter = filter;
        if (animation != null) animation.setColorFilter(filter);
        invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }

    public static class SwapAnimatedEmojiDrawable extends Drawable implements Drawable.Callback {
        public boolean center = false;
        private final int size;
        private View parentView;
        private View secondParent;
        private Drawable drawable;
        private Integer color;
        private int alpha = 255;
        private boolean attached = true;
        private final AnimatedFloat changeProgress;

        public SwapAnimatedEmojiDrawable(View parentView, int size) { this(parentView, false, size, CACHE_TYPE_EMOJI_STATUS); }
        public SwapAnimatedEmojiDrawable(View parentView, boolean invalidateParent, int size) { this(parentView, invalidateParent, size, CACHE_TYPE_EMOJI_STATUS); }
        public SwapAnimatedEmojiDrawable(View parentView, int size, int cacheType) { this(parentView, false, size, cacheType); }
        public SwapAnimatedEmojiDrawable(View parentView, boolean invalidateParent, int size, int cacheType) {
            this.parentView = parentView;
            this.size = size;
            changeProgress = new AnimatedFloat(this::invalidate, 300, CubicBezierInterpolator.EASE_OUT);
        }
        public void setParentView(View parentView) {
            if (this.parentView == parentView) return;
            if (this.parentView != secondParent) unbind(this.parentView);
            this.parentView = parentView;
            if (attached && parentView != secondParent) bind(parentView);
            invalidate();
        }
        public void play() {
            if (attached) {
                bind(parentView);
                if (secondParent != parentView) bind(secondParent);
                if (drawable instanceof Animatable) ((Animatable) drawable).start();
            }
        }
        public void setColor(Integer color) {
            this.color = color;
            if (drawable != null) drawable.setColorFilter(color == null ? null : new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            invalidate();
        }
        public Integer getColor() { return color; }
        @Override public void draw(@NonNull Canvas canvas) {
            if (drawable == null) return;
            int w = drawable.getIntrinsicWidth() > 0 ? Math.min(size, drawable.getIntrinsicWidth()) : size;
            int h = drawable.getIntrinsicHeight() > 0 ? Math.min(size, drawable.getIntrinsicHeight()) : size;
            int cx = getBounds().centerX(), cy = getBounds().centerY();
            drawable.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            drawable.setAlpha(alpha);
            drawable.draw(canvas);
        }
        public Drawable getDrawable() { return drawable; }
        public void resetAnimation() {
            if (drawable instanceof RLottieDrawable) ((RLottieDrawable) drawable).setCurrentFrame(0);
            play();
        }
        public float isNotEmpty() { return drawable != null ? 1f : 0f; }
        public boolean isEmpty() { return drawable == null; }
        public void removeOldDrawable() { }
        public void set(Drawable drawable, boolean animated) {
            if (this.drawable == drawable) return;
            detachDrawable();
            this.drawable = drawable;
            if (drawable != null) {
                drawable.setCallback(this);
                if (color != null) drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                if (attached) play();
            }
            invalidate();
        }
        private void bind(View view) {
            if (view == null || drawable == null) return;
            if (drawable instanceof AnimatedEmojiDrawable) ((AnimatedEmojiDrawable) drawable).addView(view);
            else if (drawable instanceof RLottieDrawable) ((RLottieDrawable) drawable).addParentView(view);
        }
        private void unbind(View view) {
            if (view == null || drawable == null) return;
            if (drawable instanceof AnimatedEmojiDrawable) ((AnimatedEmojiDrawable) drawable).removeView(view);
            else if (drawable instanceof RLottieDrawable) ((RLottieDrawable) drawable).removeParentView(view);
        }
        private void detachDrawable() {
            unbind(parentView);
            if (secondParent != parentView) unbind(secondParent);
            if (drawable instanceof Animatable) ((Animatable) drawable).stop();
            if (drawable != null && drawable.getCallback() == this) drawable.setCallback(null);
        }
        public void detach() { if (attached) { detachDrawable(); attached = false; } }
        public void attach() {
            if (!attached) {
                attached = true;
                if (drawable != null) drawable.setCallback(this);
                play();
            }
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidate(); }
        @Override public void setColorFilter(@Nullable ColorFilter filter) { if (drawable != null) drawable.setColorFilter(filter); invalidate(); }
        @Override public int getOpacity() { return PixelFormat.TRANSPARENT; }
        public void invalidate() {
            if (parentView != null) parentView.invalidate();
            if (secondParent != null && secondParent != parentView) secondParent.invalidate();
            invalidateSelf();
        }
        @Override public void invalidateDrawable(@NonNull Drawable who) { invalidate(); }
        @Override public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) { scheduleSelf(what, when); }
        @Override public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) { unscheduleSelf(what); }
        public void setSecondParent(View secondParent) {
            if (this.secondParent == secondParent) return;
            if (this.secondParent != parentView) unbind(this.secondParent);
            this.secondParent = secondParent;
            if (attached && secondParent != parentView) bind(secondParent);
        }
        public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) { }
    }
}
