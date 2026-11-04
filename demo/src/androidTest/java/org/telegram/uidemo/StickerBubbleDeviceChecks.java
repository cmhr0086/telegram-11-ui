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
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.Cells.GifBubbleCell;
import org.telegram.ui.Cells.PhotoBubbleCell;
import org.telegram.ui.Cells.StickerBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.Sticker;
import org.telegram.ui.Components.ChatBubbleItemAnimator;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Geometry, actual decoded pixels, callbacks and lifecycle checks on a real window. */
public final class StickerBubbleDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private File webp, gifFile, tgsFile;

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
            test.outgoingTgsInRecycler();
            test.outgoingGifInRecycler();
        } finally {
            test.main(() -> {
                if (test.host != null && test.host.getParent() instanceof ViewGroup)
                    ((ViewGroup) test.host.getParent()).removeView(test.host);
            });
            if (test.webp != null) test.webp.delete();
            if (test.gifFile != null) test.gifFile.delete();
            if (test.tgsFile != null) test.tgsFile.delete();
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
        tgsFile = new File(activity.getCacheDir(), "sticker-bubble-check.json");
        try (InputStream input = activity.getResources().openRawResource(org.telegram.tgui.R.raw.utyan_newborn);
             FileOutputStream output = new FileOutputStream(tgsFile)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    /** No manual draw/invalidate: frame progression must be driven by the real window. */
    private void outgoingTgsInRecycler() throws Exception {
        RecyclerListView[] list = new RecyclerListView[1];
        RecyclerListView.SelectionAdapter[] adapter = new RecyclerListView.SelectionAdapter[1];
        TimelineSticker[] cell = new TimelineSticker[1];
        ArrayList<TextBubbleMessage> rows = new ArrayList<>();
        TextBubbleMessage sent = TextBubbleMessage.sticker(102, true, "12:36", TextBubbleMessage.STATE_SENDING,
                new Sticker(101, Sticker.Format.TGS, tgsFile, null, 512, 512, ""));
        int[] binds = {0}, detachedBinds = {0};
        main(() -> {
            rows.add(new TextBubbleMessage(100, "Before sending", false, "12:35", TextBubbleMessage.STATE_NONE));
            list[0] = new RecyclerListView(activity);
            LinearLayoutManager manager = new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false);
            manager.setStackFromEnd(true);
            list[0].setLayoutManager(manager);
            list[0].setItemAnimator(new ChatBubbleItemAnimator());
            adapter[0] = new RecyclerListView.SelectionAdapter() {
                @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
                @Override public int getItemCount() { return rows.size(); }
                @Override public int getItemViewType(int position) { return rows.get(position).type; }
                @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                    TextBubbleCell view;
                    if (type == TextBubbleMessage.TYPE_STICKER) {
                        cell[0] = new TimelineSticker(activity);
                        view = cell[0];
                    } else {
                        view = new TextBubbleCell(activity);
                    }
                    view.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                    return new RecyclerListView.Holder(view);
                }
                @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    if (holder.itemView instanceof TimelineSticker) {
                        binds[0]++;
                        // RecyclerView temporarily removes a child while rebinding its scrap.
                        if (holder.itemView.isAttachedToWindow() && !holder.itemView.isShown()) detachedBinds[0]++;
                    }
                    ((TextBubbleCell) holder.itemView).setMessage(rows.get(position), false, false);
                }
            };
            list[0].setAdapter(adapter[0]);
            host.addView(list[0], new FrameLayout.LayoutParams(AndroidUtilities.dp(320), -1));
        });
        SystemClock.sleep(100); // Other demo animations need not become globally idle.
        main(() -> {
            rows.add(sent);
            adapter[0].notifyItemInserted(1);
        });
        long start = SystemClock.uptimeMillis();
        boolean sentUpdate = false, readUpdate = false, reply = false, completed = false;
        String[] diagnostic = {"not drawn"};
        try {
            while (SystemClock.uptimeMillis() - start < 10000) {
                long elapsed = SystemClock.uptimeMillis() - start;
                if (!sentUpdate && elapsed >= 700) {
                    main(() -> { sent.state = TextBubbleMessage.STATE_SENT; adapter[0].notifyItemChanged(1); });
                    sentUpdate = true;
                }
                if (!readUpdate && elapsed >= 1800) {
                    main(() -> { sent.state = TextBubbleMessage.STATE_READ; adapter[0].notifyItemChanged(1); });
                    readUpdate = true;
                }
                if (!reply && elapsed >= 2400) {
                    main(() -> {
                        rows.add(new TextBubbleMessage(103, "Reply", false, "12:36", TextBubbleMessage.STATE_NONE));
                        adapter[0].notifyItemInserted(2);
                        adapter[0].notifyItemChanged(1);
                    });
                    reply = true;
                }
                boolean[] done = {false};
                main(() -> {
                    TimelineSticker view = cell[0];
                    if (view == null) return;
                    diagnostic[0] = "draws=" + view.draws + ", frame=" + view.getCurrentFrame()
                            + ", max=" + view.maxFrame + ", wraps=" + view.wraps
                            + ", afterWrap=" + view.afterWrap + ", prematureResets=" + view.prematureResets
                            + ", binds=" + binds[0] + ", detachedBinds=" + detachedBinds[0]
                            + ", attached=" + view.isAttachedToWindow() + ", shown=" + view.isShown();
                    done[0] = view.maxFrame >= 170 && view.wraps > 0 && view.afterWrap >= 20;
                });
                if (reply && done[0]) { completed = true; break; }
                SystemClock.sleep(40);
            }
            final boolean complete = completed;
            main(() -> {
                check(binds[0] >= 4, "all outgoing sticker status/reply rebinds executed: " + diagnostic[0]);
                check(cell[0] != null && cell[0].isShown(), "outgoing sticker remains visible: " + diagnostic[0]);
                check(cell[0].prematureResets == 0, "rebind must not reset an unfinished TGS loop: " + diagnostic[0]);
                check(complete, "outgoing TGS must reach frame 170/180, wrap, and continue through real RecyclerView rebinds: " + diagnostic[0]);
            });
            main(() -> {
                cell[0].setAnimating(false);
                adapter[0].notifyItemChanged(1);
            });
            // A decode already in flight may finish once after stop().
            SystemClock.sleep(150);
            int[] pausedFrame = {-1}, pausedDraws = {0};
            main(() -> pausedFrame[0] = cell[0].getCurrentFrame());
            SystemClock.sleep(350);
            main(() -> {
                check(cell[0].getCurrentFrame() == pausedFrame[0], "explicit TGS pause survives a RecyclerView rebind");
                pausedDraws[0] = cell[0].draws;
                cell[0].setAnimating(true);
            });
            boolean resumed = false;
            for (int i = 0; i < 40 && !resumed; i++) {
                SystemClock.sleep(40);
                boolean[] progressed = {false};
                main(() -> progressed[0] = cell[0].getCurrentFrame() != pausedFrame[0]
                        && cell[0].draws >= pausedDraws[0] + 3);
                resumed = progressed[0];
            }
            check(resumed, "explicit TGS resume schedules new natural window draws");
        } finally {
            main(() -> {
                host.removeView(list[0]);
                if (cell[0] != null) check(!cell[0].isMediaLoaded(), "leaving the window releases the TGS decoder");
            });
        }
    }

    private void outgoingGifInRecycler() throws Exception {
        RecyclerListView[] list = new RecyclerListView[1];
        RecyclerListView.SelectionAdapter[] adapter = new RecyclerListView.SelectionAdapter[1];
        TimelineGif[] cell = new TimelineGif[1];
        ArrayList<TextBubbleMessage> rows = new ArrayList<>();
        TextBubbleMessage sent = TextBubbleMessage.gif(202, true, "12:37", TextBubbleMessage.STATE_SENDING,
                new Gif(201, gifFile, null, 1, 1), null);
        int[] binds = {0}, detachedBinds = {0};
        main(() -> {
            list[0] = new RecyclerListView(activity);
            LinearLayoutManager manager = new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false);
            manager.setStackFromEnd(true);
            list[0].setLayoutManager(manager);
            list[0].setItemAnimator(new ChatBubbleItemAnimator());
            adapter[0] = new RecyclerListView.SelectionAdapter() {
                @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
                @Override public int getItemCount() { return rows.size(); }
                @Override public int getItemViewType(int position) { return rows.get(position).type; }
                @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                    TextBubbleCell view;
                    if (type == TextBubbleMessage.TYPE_GIF) {
                        cell[0] = new TimelineGif(activity);
                        view = cell[0];
                    } else view = new TextBubbleCell(activity);
                    view.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                    return new RecyclerListView.Holder(view);
                }
                @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    if (holder.itemView instanceof TimelineGif) {
                        binds[0]++;
                        if (holder.itemView.isAttachedToWindow() && !holder.itemView.isShown()) detachedBinds[0]++;
                    }
                    ((TextBubbleCell) holder.itemView).setMessage(rows.get(position), false, false);
                }
            };
            list[0].setAdapter(adapter[0]);
            host.addView(list[0], new FrameLayout.LayoutParams(AndroidUtilities.dp(320), -1));
        });
        SystemClock.sleep(100);
        main(() -> { rows.add(sent); adapter[0].notifyItemInserted(0); });
        long start = SystemClock.uptimeMillis();
        int stage = 0;
        int[] changesAfterRebind = {-1};
        boolean complete = false;
        try {
            while (SystemClock.uptimeMillis() - start < 6500) {
                long elapsed = SystemClock.uptimeMillis() - start;
                if (stage == 0 && elapsed >= 700) {
                    main(() -> { sent.state = TextBubbleMessage.STATE_SENT; adapter[0].notifyItemChanged(0); });
                    stage++;
                } else if (stage == 1 && elapsed >= 1800) {
                    main(() -> { sent.state = TextBubbleMessage.STATE_READ; adapter[0].notifyItemChanged(0); });
                    stage++;
                } else if (stage == 2 && elapsed >= 2400) {
                    main(() -> {
                        rows.add(new TextBubbleMessage(203, "Reply", false, "12:37", TextBubbleMessage.STATE_NONE));
                        adapter[0].notifyItemInserted(1);
                        adapter[0].notifyItemChanged(0);
                    });
                    stage++;
                }
                if (stage == 3 && elapsed >= 2800) {
                    boolean[] progressed = {false};
                    main(() -> {
                        if (cell[0] == null) return;
                        if (changesAfterRebind[0] < 0) changesAfterRebind[0] = cell[0].colorChanges;
                        progressed[0] = cell[0].colorChanges >= changesAfterRebind[0] + 3;
                    });
                    if (progressed[0]) { complete = true; break; }
                }
                SystemClock.sleep(40);
            }
            final boolean progressed = complete;
            main(() -> {
                check(cell[0] != null && cell[0].isShown(), "outgoing GIF stays in the visible RecyclerView");
                check(binds[0] >= 4 && detachedBinds[0] >= 3, "GIF exercised temporary detach on all three rebinds");
                check(progressed, "GIF red/blue pixels must keep alternating after the final rebind: draws="
                        + cell[0].draws + ", changes=" + cell[0].colorChanges + ", atFinalRebind=" + changesAfterRebind[0]);
            });
        } finally {
            main(() -> {
                host.removeView(list[0]);
                if (cell[0] != null) {
                    check(!cell[0].isMediaLoaded(), "leaving the window releases the GIF decoder");
                    cell[0].releaseCapture();
                }
            });
        }
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
    private static final class TimelineSticker extends StickerBubbleCell {
        int draws, previous = -1, maxFrame = -1, wraps, afterWrap, prematureResets;
        TimelineSticker(Activity activity) { super(activity); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            draws++;
            int frame = getCurrentFrame();
            if (frame < 0) return;
            maxFrame = Math.max(maxFrame, frame);
            if (previous >= 0 && frame < previous) {
                if (previous >= 170 && frame <= 10) wraps++;
                else prematureResets++;
            }
            if (wraps > 0) afterWrap = Math.max(afterWrap, frame);
            previous = frame;
        }
    }
    private static final class TimelineGif extends GifBubbleCell {
        private Bitmap capture;
        private Canvas captureCanvas;
        private final RectF mediaRect = new RectF();
        int draws, lastColor, colorChanges;
        TimelineGif(Activity activity) { super(activity); }
        @Override protected void onDraw(Canvas canvas) {
            // Render exactly once, only when Android naturally requests a draw.
            // The captured pixels are also the pixels displayed by this view.
            if (capture == null || capture.getWidth() != getWidth() || capture.getHeight() != getHeight()) {
                releaseCapture();
                capture = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                captureCanvas = new Canvas(capture);
            }
            capture.eraseColor(Color.TRANSPARENT);
            super.onDraw(captureCanvas);
            canvas.drawBitmap(capture, 0, 0, null);
            draws++;
            getPhotoRect(mediaRect);
            if (mediaRect.isEmpty() || !isMediaLoaded()) return;
            int color = capture.getPixel((int) mediaRect.centerX(), (int) mediaRect.centerY());
            boolean red = Color.red(color) > 180 && Color.blue(color) < 60;
            boolean blue = Color.blue(color) > 180 && Color.red(color) < 60;
            if (red || blue) {
                int next = red ? 1 : 2;
                if (lastColor != 0 && next != lastColor) colorChanges++;
                lastColor = next;
            }
        }
        void releaseCapture() {
            if (capture != null) capture.recycle();
            capture = null;
            captureCanvas = null;
        }
    }
}
