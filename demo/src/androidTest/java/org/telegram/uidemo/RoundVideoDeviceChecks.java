package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.RoundVideoBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;

import java.util.concurrent.atomic.AtomicReference;

/** Run on the instrumentation worker thread, with an active Activity. */
public final class RoundVideoDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private CountingCell cell;
    private TextBubbleMessage message;
    private final RectF rect = new RectF();
    private int clicks;
    private int longPresses;

    private RoundVideoDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation;
        this.activity = activity;
    }

    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        RoundVideoDeviceChecks test = new RoundVideoDeviceChecks(instrumentation, activity);
        try {
            test.main(() -> {
                test.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.host,
                        new ViewGroup.LayoutParams(-1, -1));
            });
            for (boolean out : new boolean[]{false, true}) {
                for (int width : new int[]{240, 400}) test.scenario(out, width);
            }
            test.realPlayerLifecycle();
        } finally {
            test.main(() -> {
                if (test.host != null && test.host.getParent() instanceof ViewGroup)
                    ((ViewGroup) test.host.getParent()).removeView(test.host);
            });
        }
    }

    private void realPlayerLifecycle() throws Exception {
        final LocalRoundVideoPlayer[] owner = new LocalRoundVideoPlayer[1];
        final android.media.MediaPlayer[] nativePlayer = new android.media.MediaPlayer[1];
        final TextBubbleMessage[] item = new TextBubbleMessage[1];
        final FrameLayout[] list = new FrameLayout[1];
        try {
            main(() -> {
                list[0] = new FrameLayout(activity);
                host.addView(list[0], new FrameLayout.LayoutParams(AndroidUtilities.dp(320), AndroidUtilities.dp(400)));
                item[0] = TextBubbleMessage.roundVideo(99, false, "12:35", 0, new ColorDrawable(Color.BLUE),
                        Uri.parse("android.resource://" + activity.getPackageName() + "/" + org.telegram.uidemo.R.raw.sample_video), 6);
                CountingCell video = new CountingCell(activity);
                list[0].addView(video, new FrameLayout.LayoutParams(-1, -2));
                video.setMessage(item[0], false, false);
                Bitmap bitmap = draw(video, AndroidUtilities.dp(320));
                bitmap.recycle();
                owner[0] = new LocalRoundVideoPlayer(host, list[0]);
                owner[0].toggle(item[0]);
            });
            boolean running = false;
            for (int attempt = 0; attempt < 100 && !running; attempt++) {
                final boolean[] ready = {false};
                main(() -> {
                    nativePlayer[0] = (android.media.MediaPlayer) field(owner[0], "player");
                    ready[0] = Boolean.TRUE.equals(field(owner[0], "firstFrame")) && item[0].playing
                            && nativePlayer[0] != null && nativePlayer[0].getCurrentPosition() > 600;
                });
                running = ready[0];
                if (!running) SystemClock.sleep(60);
            }
            check(running, "real local player must prepare and render its first frame");
            final int[] pausedAt = {0};
            main(() -> {
                owner[0].toggle(item[0]);
                check(item[0].videoPaused && !item[0].playing && !nativePlayer[0].isPlaying(), "host pause state");
                pausedAt[0] = nativePlayer[0].getCurrentPosition();
            });
            SystemClock.sleep(450);
            main(() -> {
                check(Math.abs(nativePlayer[0].getCurrentPosition() - pausedAt[0]) < 120, "paused playback position remains stable");
                owner[0].toggle(item[0]);
                check(item[0].playing && !item[0].videoPaused && nativePlayer[0].isPlaying(), "resume host playback");
            });
            SystemClock.sleep(400);
            main(() -> {
                check(nativePlayer[0].getCurrentPosition() > pausedAt[0] + 150, "resumed position advances");
                owner[0].stop();
                check(field(owner[0], "player") == null && field(owner[0], "surface") == null
                        && field(owner[0], "layer") == null && !item[0].playing && !item[0].videoPaused,
                        "stop releases player, surface, layer and session state");
                owner[0].stop();
            });
        } finally {
            main(() -> {
                if (owner[0] != null) owner[0].stop();
                if (list[0] != null) host.removeView(list[0]);
            });
        }
    }

    private static Object field(Object target, String name) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private void main(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { action.run(); } catch (Throwable t) { failure.set(t); }
        });
        if (failure.get() != null) throw new AssertionError("round video device check", failure.get());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void scenario(boolean out, int widthDp) throws Exception {
        final int width = AndroidUtilities.dp(widthDp);
        final RecordingDrawable thumb = new RecordingDrawable();
        main(() -> {
            clicks = longPresses = 0;
            message = TextBubbleMessage.roundVideo(55, out, "12:34", TextBubbleMessage.STATE_SENT,
                    thumb, Uri.parse("content://example/round-video"), 63);
            cell = new CountingCell(activity);
            cell.setMediaDelegate((c, m) -> {
                check(c == cell && m == message, "media callback identity");
                clicks++;
            });
            cell.setDelegate(new TextBubbleCell.Delegate() {
                @Override public void onBubbleLongPressed(TextBubbleCell c, TextBubbleMessage m) {
                    check(c == cell && m == message, "long-press identity");
                    longPresses++;
                }
            });
            host.addView(cell, new FrameLayout.LayoutParams(width, -2));
            cell.setMessage(message, false, false);
            Bitmap frame = draw(cell, width);
            frame.recycle();
            cell.getVideoRect(rect);
            int side = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide()
                    : Math.min(width, AndroidUtilities.displaySize.y > 0 ? AndroidUtilities.displaySize.y : width);
            check(Math.abs(rect.width() - (int) (side * .6f)) <= 1, "diameter at " + widthDp + "dp");
            check(Math.abs(rect.width() - rect.height()) <= 1 && rect.left >= 0 && rect.top >= 0
                    && rect.right <= cell.getWidth() && rect.bottom <= cell.getHeight(), "square video in cell");
            check(thumb.drawCount > 0 && thumb.lastWidth > thumb.lastHeight
                    && thumb.lastHeight >= rect.height() - 2 && thumb.lastWidth >= rect.width(),
                    "wide source center-crops to square");
            check(cell.timePills == 1, "exactly one message timestamp per draw");

            tap(rect.centerX(), rect.centerY());
            check(clicks == 1, "center invokes playback");
            tap(rect.left + 1, rect.top + 1);
            check(clicks == 1, "square corner outside circle cannot invoke playback");
            long down = SystemClock.uptimeMillis();
            send(MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY(), down);
            send(MotionEvent.ACTION_MOVE, rect.right + AndroidUtilities.dp(20), rect.centerY(), down);
            send(MotionEvent.ACTION_UP, rect.right + AndroidUtilities.dp(20), rect.centerY(), down);
            check(clicks == 1, "movement cancels click");

            message.playing = true;
            message.audioProgress = 0f;
            cell.updatePlayingMessageProgress();
            cell.setVideoVisible(false);
            Bitmap emptyRing = draw(cell, width);
            message.audioProgress = .5f;
            message.audioProgressSec = 31;
            cell.updatePlayingMessageProgress();
            Bitmap halfRing = draw(cell, width);
            int changed = 0;
            float cx = rect.centerX(), cy = rect.centerY(), radius = rect.width() / 2;
            for (int y = Math.max(0, (int) rect.top); y < Math.min(cell.getHeight(), (int) rect.bottom); y++) {
                for (int x = Math.max(0, (int) rect.left); x < Math.min(width, (int) rect.right); x++) {
                    float distance = (float) Math.hypot(x - cx, y - cy);
                    if (distance > radius - AndroidUtilities.dp(10) && distance < radius
                            && y < cy && emptyRing.getPixel(x, y) != halfRing.getPixel(x, y)) changed++;
                }
            }
            emptyRing.recycle(); halfRing.recycle();
            check(changed > 3, "half progress draws ring when thumbnail hidden");
            message.playing = false;
            message.videoPaused = true;
            cell.updatePlayingMessageProgress();
            try {
                java.lang.reflect.Field remaining = RoundVideoBubbleCell.class.getDeclaredField("lastDuration");
                remaining.setAccessible(true);
                check(remaining.getInt(cell) == 32, "pause retains remaining duration");
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            tap(rect.centerX(), rect.centerY());
            check(clicks == 2, "hidden thumbnail retains hit target");
            cell.setVideoVisible(true);
            for (int state : new int[]{TextBubbleMessage.MEDIA_DOWNLOAD, TextBubbleMessage.MEDIA_LOADING,
                    TextBubbleMessage.MEDIA_READY}) {
                message.mediaState = state;
                message.mediaProgress = .5f;
                message.playing = false;
                message.videoPaused = false;
                cell.updatePlayingMessageProgress();
                Bitmap bitmap = draw(cell, width);
                bitmap.recycle();
                check(cell.timePills == 1, "timestamp once for media state " + state);
            }
            send(MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY(), SystemClock.uptimeMillis());
        });
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L);
        main(() -> {
            check(longPresses == 1 && clicks == 2, "long press invokes selection once");
            long down = SystemClock.uptimeMillis();
            send(MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY(), down);
            host.removeView(cell);
        });
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L);
        main(() -> check(longPresses == 1 && clicks == 2, "detach cancels pending press"));
    }

    private void tap(float x, float y) {
        long now = SystemClock.uptimeMillis();
        send(MotionEvent.ACTION_DOWN, x, y, now);
        send(MotionEvent.ACTION_UP, x, y, now);
    }

    private void send(int action, float x, float y, long down) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        try { cell.dispatchTouchEvent(event); } finally { event.recycle(); }
    }

    private static Bitmap draw(CountingCell view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        check(view.getMeasuredHeight() > 0, "positive height");
        view.layout(0, 0, width, view.getMeasuredHeight());
        view.timePills = 0;
        Bitmap bitmap = Bitmap.createBitmap(width, view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static final class CountingCell extends RoundVideoBubbleCell {
        int timePills;
        CountingCell(Activity activity) { super(activity); }
        @Override protected void drawTimePill(Canvas canvas, int right, int bottom, int radius) {
            timePills++;
            super.drawTimePill(canvas, right, bottom, radius);
        }
    }

    private static final class RecordingDrawable extends ColorDrawable {
        int drawCount, lastWidth, lastHeight;
        RecordingDrawable() { super(Color.rgb(30, 120, 190)); }
        @Override public int getIntrinsicWidth() { return 1200; }
        @Override public int getIntrinsicHeight() { return 600; }
        @Override public void draw(Canvas canvas) {
            drawCount++;
            lastWidth = getBounds().width();
            lastHeight = getBounds().height();
            super.draw(canvas);
        }
    }
}
