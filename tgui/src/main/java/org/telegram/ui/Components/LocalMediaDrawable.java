/*
 * Adapted from Telegram for Android, release-11.4.2-5469 (ImageReceiver and StickerEmojiCell).
 * Copyright Nikolai Kudashov and contributors. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in the project root.
 */
package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/** Local-only sticker/GIF renderer. WEBM uses a static first frame on all API levels. */
public final class LocalMediaDrawable extends Drawable implements Drawable.Callback {
    private static final int MAX_JSON = 4 * 1024 * 1024;
    private static final ExecutorService DECODER = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final View owner;
    private final Paint moviePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Map<Runnable, Runnable> scheduled = new HashMap<>();
    private MediaSource source;
    private Drawable thumbnail;
    private Sticker.Format stickerFormat;
    private boolean gif, attached, animating = true;
    private int width = 1, height = 1, alpha = 255;
    private long generation, movieElapsed, movieStarted;
    private ColorFilter filter;
    private MediaSource.Request request;
    private Drawable decoded;
    private Movie movie;
    private File privateJson;
    private boolean loading, sourceDelivered;

    public LocalMediaDrawable(View owner) {
        if (owner == null) throw new IllegalArgumentException("owner is required");
        this.owner = owner;
    }

    public void setSticker(@Nullable Sticker sticker) {
        replace(sticker == null ? null : sticker.source, sticker == null ? null : sticker.thumbnail,
                sticker == null ? null : sticker.format, false,
                sticker == null ? 1 : sticker.width, sticker == null ? 1 : sticker.height);
    }

    public void setGif(@Nullable Gif value) {
        replace(value == null ? null : value.source, value == null ? null : value.thumbnail,
                null, true, value == null ? 1 : value.width, value == null ? 1 : value.height);
    }

    private void replace(MediaSource next, Drawable placeholder, Sticker.Format format, boolean isGif, int w, int h) {
        clear();
        source = next; thumbnail = placeholder; stickerFormat = format; gif = isGif;
        width = Math.max(1, w); height = Math.max(1, h);
        if (attached) load();
        invalidate();
    }

    /** Bind and unbind on the main thread. Detach cancels work and releases decoded resources. */
    public void attach() { if (!attached) { attached = true; load(); invalidate(); } }
    public void detach() { if (attached) { attached = false; clear(); invalidate(); } }

    public void setAnimating(boolean value) {
        if (animating == value) return;
        if (movie != null && animating) movieElapsed += Math.max(0, SystemClock.uptimeMillis() - movieStarted);
        animating = value;
        if (movie != null && value) movieStarted = SystemClock.uptimeMillis();
        updateAnimation();
        invalidate();
    }

    public boolean isLoaded() { return decoded != null || movie != null; }
    public boolean isAnimated() {
        return decoded instanceof RLottieDrawable || decoded instanceof Animatable || movie != null;
    }
    public int getCurrentFrame() {
        if (decoded instanceof RLottieDrawable) return ((RLottieDrawable) decoded).getCurrentFrame();
        if (movie != null) return movieTime();
        return -1;
    }

    private void load() {
        if (!attached || source == null || loading || isLoaded()) return;
        loading = true;
        sourceDelivered = false;
        final long token = ++generation;
        if (source.file != null) { decode(source.file, token); return; }
        try {
            MediaSource.Request pending = source.loader.load(new MediaSource.Result() {
                @Override public void onLoaded(File file) { main.post(() -> {
                    if (valid(token) && !sourceDelivered) { sourceDelivered = true; decode(file, token); }
                }); }
                @Override public void onFailed() { main.post(() -> {
                    if (valid(token) && !sourceDelivered) { sourceDelivered = true; loading = false; request = null; invalidate(); }
                }); }
            });
            if (valid(token)) request = pending;
            else if (pending != null) pending.cancel();
        } catch (RuntimeException e) { loading = false; invalidate(); }
    }

    private boolean valid(long token) { return attached && token == generation; }

    private void decode(File file, long token) {
        request = null;
        final boolean animatedGif = gif;
        final Sticker.Format format = stickerFormat;
        final int target = Math.max(1, Math.min(512, Math.max(width, height)));
        DECODER.execute(() -> {
            Decoded result = new Decoded();
            try {
                if (file != null && file.isFile() && file.canRead()) {
                    if (animatedGif && Build.VERSION.SDK_INT >= 28) {
                        result.drawable = Api28.decode(file, target);
                    } else if (animatedGif) {
                        result.movie = Movie.decodeFile(file.getAbsolutePath());
                    } else if (format == Sticker.Format.TGS) {
                        result.json = unpack(file);
                        RLottieDrawable lottie = new RLottieDrawable(result.json, target, target, null, false);
                        if (lottie.canLoadFrames()) { lottie.setAutoRepeat(1); result.drawable = lottie; }
                        else lottie.recycle(false);
                    } else if (format == Sticker.Format.WEBM) {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(file.getAbsolutePath());
                            Bitmap frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                            if (frame != null) result.drawable = new BitmapDrawable(owner.getResources(), limitBitmap(frame, target));
                        } finally { retriever.release(); }
                    } else {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                        options.inJustDecodeBounds = false;
                        options.inSampleSize = sampleSize(options.outWidth, options.outHeight, target);
                        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                        if (bitmap != null) result.drawable = new BitmapDrawable(owner.getResources(), limitBitmap(bitmap, target));
                    }
                }
            } catch (Exception | LinkageError ignored) { }
            main.post(() -> {
                if (!valid(token)) { result.dispose(); return; }
                loading = false;
                decoded = result.drawable; movie = result.movie; privateJson = result.json;
                if (decoded == null && movie == null && privateJson != null) { privateJson.delete(); privateJson = null; }
                if (decoded != null) {
                    decoded.setCallback(this);
                    decoded.setAlpha(alpha);
                    decoded.setColorFilter(filter);
                    if (decoded instanceof RLottieDrawable) ((RLottieDrawable) decoded).addParentView(owner);
                }
                movieElapsed = 0; movieStarted = SystemClock.uptimeMillis();
                updateAnimation();
                invalidate();
            });
        });
    }

    private File unpack(File sourceFile) throws IOException {
        File copy = File.createTempFile("local-media-", ".json", owner.getContext().getCacheDir());
        boolean ok = false;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(sourceFile))) {
            input.mark(2); int a = input.read(), b = input.read(); input.reset();
            try (InputStream data = a == 0x1f && b == 0x8b ? new GZIPInputStream(input) : input;
                 FileOutputStream output = new FileOutputStream(copy)) {
                byte[] buffer = new byte[8192]; int total = 0, count;
                while ((count = data.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_JSON) throw new IOException("TGS exceeds size limit");
                    output.write(buffer, 0, count);
                }
                if (total == 0) throw new IOException("empty TGS");
            }
            ok = true; return copy;
        } finally { if (!ok) copy.delete(); }
    }

    private void updateAnimation() {
        if (decoded instanceof Animatable) {
            if (attached && animating) ((Animatable) decoded).start();
            else ((Animatable) decoded).stop();
        }
    }

    private int movieTime() {
        int duration = movie == null ? 0 : movie.duration();
        if (duration <= 0) duration = 1000;
        long elapsed = movieElapsed + (animating && attached ? Math.max(0, SystemClock.uptimeMillis() - movieStarted) : 0);
        return (int) (elapsed % duration);
    }

    @Override public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        float scale = Math.min(bounds.width() / (float) width, bounds.height() / (float) height);
        float w = width * scale, h = height * scale;
        float x = bounds.exactCenterX() - w / 2, y = bounds.exactCenterY() - h / 2;
        if (movie != null) {
            movie.setTime(movieTime());
            canvas.save(); canvas.translate(x, y);
            canvas.scale(w / Math.max(1, movie.width()), h / Math.max(1, movie.height()));
            movie.draw(canvas, 0, 0, moviePaint); canvas.restore();
            if (attached && animating) owner.postInvalidateDelayed(16);
        } else {
            Drawable drawable = decoded != null ? decoded : thumbnail;
            if (drawable != null) {
                Rect old = new Rect(drawable.getBounds());
                drawable.setBounds(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
                drawable.draw(canvas);
                if (drawable == thumbnail) drawable.setBounds(old);
            }
        }
    }

    private void clear() {
        generation++;
        for (Runnable pending : scheduled.values()) owner.removeCallbacks(pending);
        scheduled.clear();
        if (request != null) { try { request.cancel(); } catch (RuntimeException ignored) { } request = null; }
        loading = false;
        if (decoded != null) {
            if (decoded instanceof RLottieDrawable) {
                RLottieDrawable lottie = (RLottieDrawable) decoded;
                lottie.removeParentView(owner); lottie.stop(); lottie.setCallback(null); lottie.recycle(true);
            } else {
                if (decoded instanceof Animatable) ((Animatable) decoded).stop();
                decoded.setCallback(null);
                if (decoded instanceof BitmapDrawable) {
                    Bitmap bitmap = ((BitmapDrawable) decoded).getBitmap();
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                }
            }
        }
        decoded = null; movie = null;
        if (privateJson != null) { privateJson.delete(); privateJson = null; }
        movieElapsed = 0;
    }

    private static final class Decoded {
        Drawable drawable; Movie movie; File json;
        void dispose() {
            if (drawable instanceof RLottieDrawable) ((RLottieDrawable) drawable).recycle(true);
            else if (drawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
            if (json != null) json.delete();
        }
    }

    private static final class Api28 {
        static Drawable decode(File file, int target) throws IOException {
            Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file), (decoder, info, source) -> {
                int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                if (max > target) {
                    float scale = target / (float) max;
                    decoder.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * scale)),
                            Math.max(1, Math.round(info.getSize().getHeight() * scale)));
                }
            });
            if (drawable instanceof AnimatedImageDrawable)
                ((AnimatedImageDrawable) drawable).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            return drawable;
        }
    }

    private static int sampleSize(int width, int height, int target) {
        int sample = 1;
        while (Math.max(width, height) / sample > target * 2 && sample < 1024) sample *= 2;
        return sample;
    }

    private static Bitmap limitBitmap(Bitmap source, int target) {
        int max = Math.max(source.getWidth(), source.getHeight());
        if (max <= target) return source;
        float scale = target / (float) max;
        Bitmap resized = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * scale)),
                Math.max(1, Math.round(source.getHeight() * scale)), true);
        if (resized != source) source.recycle();
        return resized;
    }

    @Override public void setAlpha(int value) { if (alpha == value) return; alpha = value; moviePaint.setAlpha(value); if (decoded != null) decoded.setAlpha(value); invalidate(); }
    @Override public void setColorFilter(@Nullable ColorFilter value) { filter = value; moviePaint.setColorFilter(value); if (decoded != null) decoded.setColorFilter(value); invalidate(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return width; }
    @Override public int getIntrinsicHeight() { return height; }
    private void invalidate() { owner.invalidate(); invalidateSelf(); }
    @Override public void invalidateDrawable(@NonNull Drawable who) { invalidate(); }
    @Override public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        unscheduleDrawable(who, what);
        Runnable wrapper = () -> { scheduled.remove(what); what.run(); };
        scheduled.put(what, wrapper);
        owner.postDelayed(wrapper, Math.max(0, when - SystemClock.uptimeMillis()));
    }
    @Override public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        Runnable wrapper = scheduled.remove(what);
        if (wrapper != null) owner.removeCallbacks(wrapper);
    }
}
