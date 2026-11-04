package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.MusicBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded checks; run from the instrumentation worker thread. */
public final class MusicMessageDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private LocalMusicPlayer player;
    private int mediaClicks, menuClicks, seeks, changes;
    private float lastSeek;
    private MusicMessageDeviceChecks(Instrumentation instrumentation, Activity activity) { this.instrumentation = instrumentation; this.activity = activity; }
    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        MusicMessageDeviceChecks test = new MusicMessageDeviceChecks(instrumentation, activity);
        try {
            test.main(() -> {
                test.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.host, new ViewGroup.LayoutParams(-1, -1));
                test.cellChecks();
                test.regressionChecks();
            });
            test.playerChecks();
        } finally {
            test.main(() -> {
                if (test.player != null) test.player.stop();
                if (test.host != null && test.host.getParent() instanceof ViewGroup) ((ViewGroup) test.host.getParent()).removeView(test.host);
            });
        }
    }
    private void main(Runnable work) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { work.run(); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new AssertionError("music device check", error.get());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class Palette implements Theme.ResourcesProvider {
        final boolean dark;
        Palette(boolean dark) { this.dark = dark; }
        @Override public int getColor(int key) { return Theme.getColor(key); }
        @Override public boolean isDark() { return dark; }
    }
    private void draw(MusicBubbleCell cell, int width) {
        cell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cell.layout(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
        cell.draw(new Canvas(bitmap)); bitmap.recycle();
    }
    private static void event(MusicBubbleCell cell, int action, float x, float y, long down) {
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0); cell.onTouchEvent(e); e.recycle();
    }
    private static void tap(MusicBubbleCell cell, RectF rect, boolean cancel) {
        long t = SystemClock.uptimeMillis(); event(cell, MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY(), t);
        event(cell, cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP, rect.centerX(), rect.centerY(), t);
    }
    private static void inside(RectF r, MusicBubbleCell cell, String name) {
        check(!r.isEmpty() && r.left >= 0 && r.top >= 0 && r.right <= cell.getWidth() && r.bottom <= cell.getHeight(), name + " bounds");
    }
    private void cellChecks() {
        for (boolean dark : new boolean[]{false, true}) for (boolean out : new boolean[]{false, true}) {
            MusicBubbleCell cell = new MusicBubbleCell(activity, new Palette(dark)); host.addView(cell);
            cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
                @Override public void onMediaPressed(TextBubbleCell c, TextBubbleMessage message) { mediaClicks++; }
                @Override public void onMenuPressed(TextBubbleCell c, TextBubbleMessage message, float x, float y) { menuClicks++; }
                @Override public void onSeek(TextBubbleCell c, TextBubbleMessage message, float progress) { seeks++; lastSeek = progress; }
            });
            TextBubbleMessage m = TextBubbleMessage.music(1, out, "12:34", 1, "A long title with emoji 😀", "Long artist name", 12, "caption");
            m.group = true; m.sender = new TextBubbleMessage.Sender(2, "Sender", null);
            m.replyTo = new TextBubbleMessage.ReplyTo(3, "Reply", "quoted message");
            m.forwardedFrom = new TextBubbleMessage.ForwardedFrom(4, "Source");
            cell.setMessage(m, false, false);
            for (int dp : new int[]{240, 400}) {
                draw(cell, AndroidUtilities.dp(dp));
                RectF button = new RectF(), menu = new RectF(), seek = new RectF();
                cell.getButtonRect(button); cell.getMenuRect(menu); cell.getSeekBarRect(seek);
                inside(button, cell, "button"); inside(menu, cell, "menu"); inside(seek, cell, "seek");
                int before = mediaClicks; tap(cell, button, false); check(mediaClicks == before + 1, "media click");
                tap(cell, button, true); check(mediaClicks == before + 1, "cancel media click");
                before = menuClicks; tap(cell, menu, false); check(menuClicks == before + 1, "menu callback");
                m.playing = true; cell.updatePlayingMessageProgress(); draw(cell, AndroidUtilities.dp(dp)); cell.getSeekBarRect(seek);
                long t = SystemClock.uptimeMillis();
                event(cell, MotionEvent.ACTION_DOWN, seek.left + seek.width() * .2f, seek.centerY(), t);
                event(cell, MotionEvent.ACTION_MOVE, seek.left + seek.width() * .7f, seek.centerY(), t);
                event(cell, MotionEvent.ACTION_UP, seek.left + seek.width() * .7f, seek.centerY(), t);
                check(seeks > 0 && lastSeek >= 0 && lastSeek <= 1, "seek gesture callback");
                m.playing = false; cell.updatePlayingMessageProgress();
            }
            cell.setCheckBoxVisible(true, false); draw(cell, AndroidUtilities.dp(400));
            RectF button = new RectF(); cell.getButtonRect(button); int before = mediaClicks; tap(cell, button, false);
            check(mediaClicks == before, "selection mode blocks media action");
            cell.setCheckBoxVisible(false, false); draw(cell, AndroidUtilities.dp(400)); cell.getButtonRect(button);
            long t = SystemClock.uptimeMillis(); event(cell, MotionEvent.ACTION_DOWN, button.centerX(), button.centerY(), t);
            cell.setMessage(TextBubbleMessage.music(9, out, "12:34", 1, "New", "Artist", 12, null), false, false);
            event(cell, MotionEvent.ACTION_UP, button.centerX(), button.centerY(), t);
            check(mediaClicks == before, "rebind cancels gesture");
            host.removeView(cell);
        }
    }
    private void regressionChecks() {
        MusicBubbleCell cell = new MusicBubbleCell(activity); host.addView(cell);
        cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
            @Override public void onMediaPressed(TextBubbleCell c, TextBubbleMessage m) { mediaClicks++; }
            @Override public void onSeek(TextBubbleCell c, TextBubbleMessage m, float progress) { seeks++; }
        });
        TextBubbleMessage m = TextBubbleMessage.music(99, false, "12:34", 0, "Title", "Artist", 120, null);
        m.playing = true; m.audioProgress = .1f; m.audioProgressSec = 12;
        cell.setMessage(m, false, false); draw(cell, AndroidUtilities.dp(400));
        RectF seek = new RectF(); cell.getSeekBarRect(seek);
        int before = seeks; long t = SystemClock.uptimeMillis();
        event(cell, MotionEvent.ACTION_DOWN, seek.left + seek.width() * .2f, seek.centerY(), t);
        event(cell, MotionEvent.ACTION_MOVE, seek.left + seek.width() * .7f, seek.centerY(), t);
        android.text.Layout dragLayout = (android.text.Layout) field(cell, "durationLayout");
        String dragText = dragLayout.getText().toString();
        String hostText = AndroidUtilities.formatShortDuration(12, 120);
        check(!dragText.equals(hostText), "drag shows preview time");
        cell.updatePlayingMessageProgress();
        check(((android.text.Layout) field(cell, "durationLayout")).getText().toString().equals(dragText), "tick preserves drag preview");
        event(cell, MotionEvent.ACTION_CANCEL, seek.left + seek.width() * .7f, seek.centerY(), t);
        check(seeks == before && ((android.text.Layout) field(cell, "durationLayout")).getText().toString().equals(hostText),
                "cancel restores host time without seek");
        for (int i = 0; i < 20; i++) draw(cell, AndroidUtilities.dp(400));
        float active = (Float) field(cell, "seekTransition");
        m.playing = false; m.audioPaused = true; cell.updatePlayingMessageProgress(); draw(cell, AndroidUtilities.dp(400));
        check((Float) field(cell, "seekTransition") >= active && ((android.text.Layout) field(cell, "durationLayout")).getText().toString().equals(hostText),
                "paused session keeps seek display and elapsed time");
        m.mediaState = TextBubbleMessage.MEDIA_DOWNLOAD; cell.updatePlayingMessageProgress();
        check((Integer) field(cell, "buttonState") == 2, "download state");
        m.mediaState = TextBubbleMessage.MEDIA_LOADING; m.mediaProgress = .5f; cell.updatePlayingMessageProgress();
        check((Integer) field(cell, "buttonState") == 4, "loading state");
        m.mediaState = TextBubbleMessage.MEDIA_READY; cell.updatePlayingMessageProgress();
        check((Integer) field(cell, "buttonState") == 0, "ready paused state");
        org.telegram.ui.Components.RadialProgress2 radial = (org.telegram.ui.Components.RadialProgress2) field(cell, "radialProgress");
        check(radial.iconColorKey == Theme.key_chat_inMediaIcon, "incoming radial icon key");
        m.isOut = true; cell.setMessage(m, false, false); draw(cell, AndroidUtilities.dp(400));
        check(radial.iconColorKey == Theme.key_chat_outMediaIcon, "same model direction updates radial icon key");
        cell.setMessage(null, false, false);
        RectF button = new RectF(), menu = new RectF(), bar = new RectF();
        cell.getButtonRect(button); cell.getMenuRect(menu); cell.getSeekBarRect(bar);
        check(button.isEmpty() && menu.isEmpty() && bar.isEmpty(), "null binding clears hit bounds");
        host.removeView(cell);
    }
    private static Object field(Object owner, String name) {
        try { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private void playerChecks() throws Exception {
        TextBubbleMessage a = TextBubbleMessage.music(10, false, "12:34", 0, "A", "Artist", 12, null);
        TextBubbleMessage b = TextBubbleMessage.music(11, false, "12:34", 0, "B", "Artist", 12, null);
        Uri uri = Uri.parse("android.resource://" + activity.getPackageName() + "/" + R.raw.sample_music);
        main(() -> { player = new LocalMusicPlayer(activity, () -> changes++); player.toggle(a, uri); });
        boolean running = false;
        for (int i = 0; i < 100 && !running; i++) {
            boolean[] ok = {false}; main(() -> { MediaPlayer p = (MediaPlayer) field(player, "player"); ok[0] = p != null && a.playing && p.getCurrentPosition() > 350; });
            running = ok[0]; if (!running) SystemClock.sleep(60);
        }
        check(running, "real WAV prepared and advanced");
        final int[] paused = {0};
        main(() -> { player.toggle(a, uri); MediaPlayer p = (MediaPlayer) field(player, "player"); paused[0] = p.getCurrentPosition(); check(!a.playing && a.audioPaused, "paused state"); });
        SystemClock.sleep(400);
        main(() -> { MediaPlayer p = (MediaPlayer) field(player, "player"); check(Math.abs(p.getCurrentPosition() - paused[0]) < 120, "pause stable"); player.seek(a, .2f); player.seek(a, .65f); player.seek(a, .5f);
            check(Math.abs(a.audioProgress - .5f) < .01f, "latest paused seek shown immediately"); });
        boolean sought = false;
        for (int i = 0; i < 100 && !sought; i++) {
            boolean[] ok = {false}; main(() -> { MediaPlayer p = (MediaPlayer) field(player, "player");
                ok[0] = !((Boolean) field(player, "seeking")) && p.getCurrentPosition() > 5000 && p.getCurrentPosition() < 7000; });
            sought = ok[0]; if (!sought) SystemClock.sleep(50);
        }
        check(sought, "latest rapid paused seek reaches native player");
        main(() -> { check(Math.abs(a.audioProgress - .5f) < .08f && a.audioPaused, "paused seek retained");
            player.toggle(a, uri); check(a.playing && !a.audioPaused, "resume"); });
        SystemClock.sleep(350);
        main(() -> { MediaPlayer p = (MediaPlayer) field(player, "player"); check(p.getCurrentPosition() > 6100, "resume advances");
            player.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
            check(!a.playing && a.audioPaused, "focus loss pauses session");
            b.audioProgress = .25f;
            player.toggle(b, uri); check(!a.playing && !a.audioPaused && player.getPlayingMessage() == b, "switch session"); });
        boolean switched = false;
        for (int i = 0; i < 100 && !switched; i++) {
            boolean[] ok = {false}; main(() -> { ok[0] = b.playing; }); switched = ok[0]; if (!switched) SystemClock.sleep(60);
        }
        check(switched, "second WAV started");
        boolean resumedAtSaved = false;
        for (int i = 0; i < 100 && !resumedAtSaved; i++) {
            boolean[] ok = {false}; main(() -> { MediaPlayer p = (MediaPlayer) field(player, "player");
                ok[0] = !((Boolean) field(player, "seeking")) && p.getCurrentPosition() >= 2800; });
            resumedAtSaved = ok[0]; if (!resumedAtSaved) SystemClock.sleep(50);
        }
        check(resumedAtSaved, "prepared player restores saved position");
        main(() -> player.seek(b, .97f));
        boolean completed = false;
        for (int i = 0; i < 100 && !completed; i++) {
            boolean[] ok = {false}; main(() -> ok[0] = player.getPlayingMessage() == null); completed = ok[0]; if (!completed) SystemClock.sleep(60);
        }
        check(completed, "completion releases session");
        main(() -> { check(field(player, "player") == null && !b.playing && !b.audioPaused && b.audioProgress == 0 && changes > 0, "completion state and notifications");
            player.stop(); player.stop(); check(field(player, "player") == null, "repeated stop is safe"); });
    }
}
