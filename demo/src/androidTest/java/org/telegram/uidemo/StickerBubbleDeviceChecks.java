package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.GifBubbleCell;
import org.telegram.ui.Cells.PhotoBubbleCell;
import org.telegram.ui.Cells.StickerBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.Sticker;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/** Geometry, actual decoded pixels, callbacks and lifecycle checks on a real window. */
public final class StickerBubbleDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private File webp, gifFile;

    private StickerBubbleDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation;
        this.activity = activity;
    }

    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        StickerBubbleDeviceChecks test = new StickerBubbleDeviceChecks(instrumentation, activity);
        try {
            test.fixtures();
            test.main(() -> {
                test.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.host, new ViewGroup.LayoutParams(-1, -1));
            });
            for (boolean out : new boolean[]{false, true}) test.sticker(out);
            test.gif();
        } finally {
            test.main(() -> {
                if (test.host != null && test.host.getParent() instanceof ViewGroup)
                    ((ViewGroup) test.host.getParent()).removeView(test.host);
            });
            if (test.webp != null) test.webp.delete();
            if (test.gifFile != null) test.gifFile.delete();
        }
    }

    private void fixtures() throws Exception {
        webp = new File(activity.getCacheDir(), "sticker-bubble-check.webp");
        Bitmap bitmap = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(); paint.setColor(Color.RED);
        new Canvas(bitmap).drawRect(32, 16, 224, 112, paint);
        try (FileOutputStream stream = new FileOutputStream(webp)) {
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, 100, stream), "WEBP fixture compression");
        }
        bitmap.recycle();
        gifFile = new File(activity.getCacheDir(), "sticker-bubble-check.gif");
        // Original 1x1 two-frame red/blue GIF fixture; each frame lasts 150ms.
        byte[] bytes = Base64.decode("R0lGODlhAQABAIAAAP8AAAAA/yH/C05FVFNDQVBFMi4wAwEAAAAh+QQADwAAACwAAAAAAQABAAACAkQBACH5BAAPAAAALAAAAAABAAEAAAICTAEAOw==", Base64.DEFAULT);
        try (FileOutputStream stream = new FileOutputStream(gifFile)) { stream.write(bytes); }
    }

    private void sticker(boolean out) throws Exception {
        CountingSticker[] cell = new CountingSticker[1];
        TextBubbleMessage[] message = new TextBubbleMessage[1];
        int[] clicks = {0}, longs = {0};
        int width = AndroidUtilities.dp(320);
        main(() -> {
            Sticker sticker = new Sticker(11, Sticker.Format.WEBP, webp, null, 256, 128, "");
            message[0] = TextBubbleMessage.sticker(12, out, "12:34", TextBubbleMessage.STATE_READ, sticker);
            check(message[0].type == TextBubbleMessage.TYPE_STICKER && message[0].sticker == sticker,
                    "sticker factory retains identity");
            check(TextBubbleMessage.ReplyTo.of(message[0], "Sender").media, "sticker reply is media");
            cell[0] = new CountingSticker(activity);
            cell[0].setMessage(message[0], false, false);
            cell[0].setMediaDelegate((view, model) -> {
                check(view == cell[0] && model == message[0], "sticker media callback identity"); clicks[0]++;
            });
            cell[0].setDelegate(new TextBubbleCell.Delegate() {
                @Override public void onBubbleLongPressed(TextBubbleCell view, TextBubbleMessage model) {
                    check(view == cell[0] && model == message[0], "sticker long press identity"); longs[0]++;
                }
            });
            host.addView(cell[0], new FrameLayout.LayoutParams(width, -2));
            draw(cell[0], width).recycle();
        });
        awaitLoaded(cell[0], null);
        main(() -> {
            cell[0].times = 0;
            Bitmap frame = draw(cell[0], width);
            RectF rect = new RectF(); cell[0].getPhotoRect(rect);
            int expected = (int) (AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() * .4f
                    : Math.min(width, AndroidUtilities.displaySize.y) * .5f);
            check(Math.abs(rect.width() - expected) <= 1 && Math.abs(rect.width() / 2 - rect.height()) <= 1,
                    "sticker upstream aspect and display limit");
            check(cell[0].times == 1, "exactly one sticker timestamp");
            check(Color.alpha(frame.getPixel((int) rect.left + 2, (int) rect.top + 2)) == 0,
                    "transparent sticker corner has no bubble background");
            check(Color.red(frame.getPixel((int) rect.centerX(), (int) rect.centerY())) > 180,
                    "actual local WEBP pixels rendered");
            frame.recycle();
            tap(cell[0], rect.centerX(), rect.centerY());
            check(clicks[0] == 1, "sticker tap sends media callback");
            send(cell[0], MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY());
        });
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 100L);
        main(() -> {
            check(longs[0] == 1 && clicks[0] == 1, "long press selects without a click");
            host.removeView(cell[0]);
            check(!cell[0].isMediaLoaded(), "detach releases sticker decoder");
        });
    }

    private void gif() throws Exception {
        GifBubbleCell[] cell = new GifBubbleCell[1];
        int width = AndroidUtilities.dp(320);
        main(() -> {
            Gif gif = new Gif(21, gifFile, null, 1, 1);
            TextBubbleMessage message = TextBubbleMessage.gif(22, false, "12:35", 0, gif, "Caption");
            check(message.type == TextBubbleMessage.TYPE_GIF && message.gif == gif && "Caption".contentEquals(message.text),
                    "GIF factory and caption");
            cell[0] = new GifBubbleCell(activity);
            cell[0].setMessage(message, false, false);
            host.addView(cell[0], new FrameLayout.LayoutParams(width, -2));
            draw(cell[0], width).recycle();
        });
        awaitLoaded(null, cell[0]);
        int[] first = {0};
        main(() -> first[0] = centerPixel(cell[0], width));
        boolean changed = false;
        for (int i = 0; i < 25 && !changed; i++) {
            SystemClock.sleep(80);
            int[] next = {0}; main(() -> next[0] = centerPixel(cell[0], width));
            changed = next[0] != first[0];
        }
        check(changed, "GIF automatically renders distinct frames");
        int[] paused = {0};
        main(() -> {
            cell[0].setAnimating(false);
            paused[0] = centerPixel(cell[0], width);
        });
        SystemClock.sleep(380);
        main(() -> {
            check(centerPixel(cell[0], width) == paused[0], "explicit pause freezes GIF pixels");
            host.removeView(cell[0]);
            check(!cell[0].isMediaLoaded(), "detach releases GIF decoder");
            host.addView(cell[0], new FrameLayout.LayoutParams(width, -2));
        });
        awaitLoaded(null, cell[0]);
        main(() -> paused[0] = centerPixel(cell[0], width));
        SystemClock.sleep(380);
        main(() -> {
            check(centerPixel(cell[0], width) == paused[0], "reattach preserves explicit pause");
            cell[0].setMessage(null, false, false);
            RectF rect = new RectF(); cell[0].getPhotoRect(rect);
            check(rect.isEmpty() && !cell[0].isMediaLoaded(), "null bind clears geometry and decoder");
            Bitmap empty = Bitmap.createBitmap(width, 100, Bitmap.Config.ARGB_8888);
            cell[0].draw(new Canvas(empty)); empty.recycle();
            host.removeView(cell[0]);
        });
    }

    private void awaitLoaded(StickerBubbleCell sticker, GifBubbleCell gif) throws Exception {
        for (int i = 0; i < 100; i++) {
            boolean[] ready = {false}; main(() -> ready[0] = sticker != null ? sticker.isMediaLoaded() : gif.isMediaLoaded());
            if (ready[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("local media decoder did not load");
    }
    private static int centerPixel(PhotoBubbleCell cell, int width) {
        Bitmap bitmap = draw(cell, width);
        RectF rect = new RectF(); cell.getPhotoRect(rect);
        int color = bitmap.getPixel((int) rect.centerX(), (int) rect.centerY());
        bitmap.recycle(); return color;
    }
    private static Bitmap draw(View cell, int width) {
        cell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cell.layout(0, 0, width, cell.getMeasuredHeight());
        Bitmap frame = Bitmap.createBitmap(width, cell.getHeight(), Bitmap.Config.ARGB_8888);
        cell.draw(new Canvas(frame)); return frame;
    }
    private static void tap(View view, float x, float y) { send(view, MotionEvent.ACTION_DOWN, x, y); send(view, MotionEvent.ACTION_UP, x, y); }
    private static void send(View view, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
    private void main(Runnable task) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { task.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError("Sticker/GIF bubble check", failure.get());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class CountingSticker extends StickerBubbleCell {
        int times;
        CountingSticker(Activity activity) { super(activity); }
        @Override protected void drawStickerTime(Canvas canvas) { times++; super.drawStickerTime(canvas); }
    }
}
