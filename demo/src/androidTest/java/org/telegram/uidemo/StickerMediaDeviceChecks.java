package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.LocalMediaDrawable;
import org.telegram.ui.Components.LocalMediaView;
import org.telegram.ui.Components.MediaSource;
import org.telegram.ui.Components.Sticker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

/** Device checks for local sticker and GIF decoding; call on the instrumentation worker. */
public final class StickerMediaDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private LocalMediaView view;
    private File directory, tgs, gif, webpRed, webpGreen, webm;

    private StickerMediaDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation;
        this.activity = activity;
    }

    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        StickerMediaDeviceChecks test = new StickerMediaDeviceChecks(instrumentation, activity);
        Theme.ThemeInfo originalTheme = Theme.getActiveTheme();
        boolean originalDark = Theme.isCurrentThemeDark();
        try {
            test.prepareFiles();
            test.main(() -> {
                test.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.host,
                        new ViewGroup.LayoutParams(-1, -1));
                test.view = new LocalMediaView(activity);
                test.host.addView(test.view, new FrameLayout.LayoutParams(AndroidUtilities.dp(120), AndroidUtilities.dp(120)));
                test.layout();
            });
            test.animatedTgs();
            test.animatedGif();
            test.staticFiles();
            test.loaderReuseAndDetach();
            test.themeVariants();
        } finally {
            test.main(() -> {
                if (test.host != null && test.host.getParent() instanceof ViewGroup)
                    ((ViewGroup) test.host.getParent()).removeView(test.host);
                if (originalTheme != null) Theme.applyTheme(originalTheme, false, originalDark);
            });
            if (test.directory != null) {
                File[] files = test.directory.listFiles();
                if (files != null) for (File file : files) file.delete();
                test.directory.delete();
            }
        }
    }

    private void prepareFiles() throws Exception {
        directory = new File(activity.getCacheDir(), "sticker-check-" + System.nanoTime());
        check(directory.mkdirs(), "create fixture directory");
        tgs = new File(directory, "fixture.tgs");
        try (InputStream in = activity.getResources().openRawResource(org.telegram.tgui.R.raw.utyan_private);
             GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(tgs))) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        gif = copyRaw(R.raw.sample_sticker, "animated.gif");
        webm = copyRaw(R.raw.sample_video_sticker, "sample.webm");
        webpRed = makeWebp("red.webp", Color.RED);
        webpGreen = makeWebp("green.webp", Color.GREEN);
    }

    private File copyRaw(int resource, String name) throws Exception {
        File file = new File(directory, name);
        try (InputStream in = activity.getResources().openRawResource(resource);
             FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        return file;
    }

    @SuppressWarnings("deprecation")
    private File makeWebp(String name, int color) throws Exception {
        File file = new File(directory, name);
        Bitmap bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream out = new FileOutputStream(file)) {
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out), "encode WEBP fixture");
        } finally { bitmap.recycle(); }
        return file;
    }

    private void main(Runnable task) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { task.run(); } catch (Throwable t) { failure.set(t); }
        });
        if (failure.get() != null) throw new AssertionError("local media device check", failure.get());
    }

    private static void check(boolean ok, String detail) { if (!ok) throw new AssertionError(detail); }

    private Sticker sticker(long id, Sticker.Format format, MediaSource source, int w, int h) {
        return new Sticker(id, format, source, new ColorDrawable(Color.MAGENTA), w, h, "");
    }

    private void layout() {
        int size = AndroidUtilities.dp(120);
        view.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, size, size);
    }

    private void waitLoaded(String name) {
        boolean[] loaded = {false};
        for (int i = 0; i < 100 && !loaded[0]; i++) {
            main(() -> loaded[0] = view.getMediaDrawable().isLoaded());
            if (!loaded[0]) SystemClock.sleep(100);
        }
        check(loaded[0], name + " did not load in ten seconds");
    }

    private Bitmap frame() {
        int size = AndroidUtilities.dp(120);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private long hash() {
        Bitmap bitmap = frame();
        long result = 1;
        for (int y = 0; y < bitmap.getHeight(); y += 2)
            for (int x = 0; x < bitmap.getWidth(); x += 2) result = result * 31 + bitmap.getPixel(x, y);
        bitmap.recycle();
        return result;
    }

    private void assertPixelsChange(String label, boolean frameNumber) {
        long[] firstHash = {0}; int[] firstFrame = {-1};
        main(() -> { firstHash[0] = hash(); firstFrame[0] = view.getMediaDrawable().getCurrentFrame(); });
        boolean[] changed = {false}, advanced = {false};
        for (int i = 0; i < 50 && !(changed[0] && (!frameNumber || advanced[0])); i++) {
            SystemClock.sleep(150);
            main(() -> {
                if (hash() != firstHash[0]) changed[0] = true;
                int frame = view.getMediaDrawable().getCurrentFrame();
                if (frame >= 0 && frame != firstFrame[0]) advanced[0] = true;
            });
        }
        check(changed[0], label + " rendered pixels did not change");
        if (frameNumber) check(advanced[0], label + " frame index did not advance");
    }

    private void animatedTgs() {
        main(() -> view.setSticker(sticker(1, Sticker.Format.TGS, new MediaSource(tgs), 512, 512)));
        waitLoaded("TGS");
        main(() -> check(view.getMediaDrawable().isAnimated(), "TGS animated"));
        assertPixelsChange("TGS", true);
        check(tgs.isFile(), "TGS host source retained");
    }

    private void animatedGif() {
        main(() -> view.setGif(new Gif(2, gif, null, 200, 200)));
        waitLoaded("GIF");
        main(() -> check(view.getMediaDrawable().isAnimated(), "GIF animated"));
        assertPixelsChange("GIF", false);
        main(() -> view.setAnimating(false));
        SystemClock.sleep(300);
        long[] paused = {0}; main(() -> paused[0] = hash());
        SystemClock.sleep(400);
        main(() -> check(hash() == paused[0], "GIF paused pixels remain stable"));
        main(() -> view.setAnimating(true));
        assertPixelsChange("resumed GIF", false);
        check(gif.isFile(), "GIF host source retained");
    }

    private void staticFiles() {
        main(() -> view.setSticker(sticker(3, Sticker.Format.WEBP, new MediaSource(webpRed), 40, 20)));
        waitLoaded("WEBP");
        main(() -> {
            LocalMediaDrawable media = view.getMediaDrawable();
            check(!media.isAnimated(), "WEBP static");
            Bitmap bitmap = frame();
            int center = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
            int corner = bitmap.getPixel(1, 1);
            bitmap.recycle();
            check(Color.red(center) > 200 && Color.green(center) < 60, "WEBP pixels decoded");
            check(Color.alpha(corner) == 0, "wide WEBP fits instead of stretching");
        });
        main(() -> view.setSticker(sticker(4, Sticker.Format.WEBM, new MediaSource(webm), 160, 160)));
        waitLoaded("WEBM");
        main(() -> {
            check(!view.getMediaDrawable().isAnimated(), "WEBM first-frame fallback");
            Bitmap bitmap = frame();
            int pixel = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
            bitmap.recycle();
            check(Color.alpha(pixel) > 0 && pixel != Color.MAGENTA, "WEBM actual first frame");
        });
        check(webpRed.isFile() && webm.isFile(), "static host files retained");
    }

    private void loaderReuseAndDetach() throws Exception {
        AtomicReference<MediaSource.Result> delayed = new AtomicReference<>();
        AtomicInteger cancels = new AtomicInteger();
        MediaSource.Loader loader = result -> { delayed.set(result); return cancels::incrementAndGet; };
        main(() -> view.setSticker(sticker(5, Sticker.Format.WEBP, new MediaSource(loader), 40, 20)));
        check(delayed.get() != null, "loader invoked");
        main(() -> view.setSticker(sticker(6, Sticker.Format.WEBP, new MediaSource(webpGreen), 40, 20)));
        check(cancels.get() == 1, "previous request cancelled on reuse");
        Thread stale = new Thread(() -> delayed.get().onLoaded(webpRed));
        stale.start(); stale.join(3000); check(!stale.isAlive(), "stale callback bounded");
        waitLoaded("new WEBP");
        SystemClock.sleep(200);
        main(() -> {
            Bitmap bitmap = frame(); int center = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2); bitmap.recycle();
            check(Color.green(center) > 200 && Color.red(center) < 60, "late callback must not replace reused view");
            host.removeView(view);
            check(!view.getMediaDrawable().isLoaded(), "detach releases decoded media");
            host.addView(view, new FrameLayout.LayoutParams(AndroidUtilities.dp(120), AndroidUtilities.dp(120)));
            layout();
        });
        waitLoaded("reattached WEBP");
        check(webpRed.isFile() && webpGreen.isFile(), "loader host sources retained");

        AtomicReference<MediaSource.Result> fresh = new AtomicReference<>();
        main(() -> view.setSticker(sticker(7, Sticker.Format.WEBP,
                new MediaSource(result -> { fresh.set(result); return () -> {}; }), 40, 20)));
        Thread worker = new Thread(() -> fresh.get().onLoaded(webpRed));
        worker.start(); worker.join(3000); check(!worker.isAlive(), "worker callback bounded");
        waitLoaded("worker-loaded WEBP");
        main(() -> {
            Bitmap bitmap = frame(); int center = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2); bitmap.recycle();
            check(Color.red(center) > 200 && Color.green(center) < 60, "background loader result bound");
        });
    }

    private void themeVariants() {
        main(() -> {
            Theme.ThemeInfo night = Theme.getCurrentNightTheme();
            Theme.ThemeInfo day = Theme.getCurrentTheme();
            check(night != null && day != null, "theme fixtures available");
            Theme.applyTheme(night, false, true);
            Bitmap dark = frame();
            check(Color.alpha(dark.getPixel(dark.getWidth() / 2, dark.getHeight() / 2)) > 0, "night media visible");
            dark.recycle();
            Theme.applyTheme(day, false, false);
            Bitmap light = frame();
            check(Color.alpha(light.getPixel(light.getWidth() / 2, light.getHeight() / 2)) > 0, "day media visible");
            light.recycle();
        });
    }
}
