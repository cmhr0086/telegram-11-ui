package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Sticker;
import org.telegram.ui.Components.StickerMediaPage;
import org.telegram.ui.Components.StickerSet;
import org.telegram.ui.ContentPreviewViewer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded device checks; called from the existing instrumentation worker. */
public final class EmojiMediaDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private EmojiView emoji;
    private Sticker sticker;
    private Gif gif;
    private final int[] callbacks = new int[4];
    private int lastTab = -1;

    private EmojiMediaDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation; this.activity = activity;
    }
    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        EmojiMediaDeviceChecks test = new EmojiMediaDeviceChecks(instrumentation, activity);
        AnimatedEmojiDrawable.SourceProvider previous = AnimatedEmojiDrawable.getSourceProvider();
        try {
            LocalEmojiSamples.install(activity);
            test.setup();
            test.pageAndAllow();
            test.tapAndPreview();
        } finally {
            test.main(() -> {
                if (test.emoji != null) { test.emoji.setShowing(false); test.emoji.onDestroy(); }
                if (test.host != null && test.host.getParent() instanceof ViewGroup) ((ViewGroup) test.host.getParent()).removeView(test.host);
                AnimatedEmojiDrawable.setSourceProvider(previous);
            });
        }
    }
    private void main(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() != null) throw new AssertionError("emoji media UI test", failure.get());
    }
    private void setup() {
        main(() -> {
            File source = new File(activity.getFilesDir(), "emoji-duck.tgs");
            sticker = new Sticker(707, Sticker.Format.TGS, source, new ColorDrawable(Color.RED), 120, 120, "🐥");
            gif = new Gif(808, source, new ColorDrawable(Color.BLUE), 320, 180);
            StickerSet pack = new StickerSet(90, "Sample Pack", Collections.singletonList(sticker));
            emoji = new EmojiView(activity, true, null);
            emoji.setDelegate(new EmojiView.EmojiViewDelegate() {
                @Override public void onStickerSelected(Sticker s) { check(s == sticker, "sticker callback item"); callbacks[0]++; }
                @Override public void onGifSelected(Gif g) { check(g == gif, "GIF callback item"); callbacks[1]++; }
                @Override public void onStickerSetRequested(StickerSet set) { check(set.id == 90, "pack callback id"); callbacks[2]++; }
                @Override public void onTabOpened(int type) { lastTab = type; }
            });
            host = new FrameLayout(activity);
            ((ViewGroup) activity.findViewById(android.R.id.content)).addView(host, new ViewGroup.LayoutParams(-1, -1));
            host.addView(emoji, new FrameLayout.LayoutParams(AndroidUtilities.dp(320), AndroidUtilities.dp(420)));
            emoji.setMediaData(Collections.singletonList(pack), Collections.singletonList(sticker), Collections.singletonList(sticker), Collections.singletonList(gif));
            emoji.setAllow(true, true, false);
            emoji.setShowing(true);
        });
    }
    private void pageAndAllow() throws Exception {
        waitFor(() -> emoji.getWidth() > 0, "emoji panel layout");
        checkOnMain(() -> { check(emoji.getCurrentPage() == 0, "initial emoji type"); check(emoji.areThereAnyStickers(), "snapshot reports stickers"); });
        clickTab(2);
        waitFor(() -> emoji.getCurrentPage() == 2, "sticker page");
        checkOnMain(() -> check(lastTab == 3, "sticker callback follows input API"));
        clickTab(1);
        waitFor(() -> emoji.getCurrentPage() == 1, "GIF page");
        checkOnMain(() -> check(lastTab == 2, "GIF callback type"));
        main(() -> emoji.setAllow(true, false, false));
        checkOnMain(() -> { check(emoji.getCurrentPage() == 0, "disallow selected GIF returns to emoji"); check(tabs().getChildCount() == 2, "disabled GIF tab hidden"); });
        main(() -> emoji.setAllow(true, true, false));
        checkOnMain(() -> check(tabs().getChildCount() == 3, "allowed pages restored"));
    }
    private void tapAndPreview() throws Exception {
        clickTab(2);
        waitFor(() -> emoji.getCurrentPage() == 2 && readyCell(page("stickerPage"), sticker), "sticker cell layout and animations");
        RecyclerListView stickerList = list(page("stickerPage"));
        View cell = getOnMain(() -> findMediaCell(page("stickerPage"), sticker));
        tap(stickerList, cell);
        waitFor(() -> callbacks[0] == 1, "sticker tap callback");
        checkOnMain(() -> check(callbacks[1] == 0, "sticker tap must not send GIF"));
        ContentPreviewViewer.Binding binding = getOnMain(() -> (ContentPreviewViewer.Binding) field(page("stickerPage"), "previewBinding"));
        final long down = SystemClock.uptimeMillis();
        main(() -> {
            MotionEvent start = event(down, down, MotionEvent.ACTION_DOWN, cell.getX() + cell.getWidth() / 2f, cell.getY() + cell.getHeight() / 2f);
            stickerList.dispatchTouchEvent(start); start.recycle();
            MotionEvent move = event(down, down + 30, MotionEvent.ACTION_MOVE, cell.getX() + cell.getWidth() / 2f + AndroidUtilities.dp(20), cell.getY() + cell.getHeight() / 2f);
            stickerList.dispatchTouchEvent(move); move.recycle();
            MotionEvent cancel = event(down, down + 31, MotionEvent.ACTION_CANCEL, cell.getX() + cell.getWidth() / 2f + AndroidUtilities.dp(20), cell.getY() + cell.getHeight() / 2f);
            stickerList.dispatchTouchEvent(cancel); cancel.recycle();
        });
        SystemClock.sleep(350);
        checkOnMain(() -> check(!binding.isShowing(), "moving before long press cancels preview"));
        long nextDown = SystemClock.uptimeMillis();
        main(() -> {
            MotionEvent start = event(nextDown, nextDown, MotionEvent.ACTION_DOWN, cell.getX() + cell.getWidth() / 2f, cell.getY() + cell.getHeight() / 2f);
            stickerList.dispatchTouchEvent(start); start.recycle();
        });
        waitFor(binding::isShowing, "long press opens preview");
        // Invoke the actual preview menu's registered send action; do not bypass its delegate.
        main(() -> {
            try {
                Method showMenu = binding.getClass().getDeclaredMethod("showMenu"); showMenu.setAccessible(true); showMenu.invoke(binding);
                BottomSheet menu = (BottomSheet) field(binding, "menu");
                check(menu != null, "preview menu opens");
                Field action = BottomSheet.class.getDeclaredField("onClickListener"); action.setAccessible(true);
                ((DialogInterface.OnClickListener) action.get(menu)).onClick(menu, 0);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        checkOnMain(() -> check(callbacks[0] == 2 && callbacks[1] == 0, "preview send routes only sticker"));
        clickTab(1);
        waitFor(() -> emoji.getCurrentPage() == 1 && readyCell(page("gifPage"), gif), "GIF cell layout and animations");
        RecyclerListView gifList = list(page("gifPage"));
        View gifCell = getOnMain(() -> findMediaCell(page("gifPage"), gif));
        tap(gifList, gifCell);
        waitFor(() -> callbacks[1] == 1, "GIF tap callback");
        checkOnMain(() -> check(callbacks[0] == 2, "GIF tap must not send sticker"));
        main(() -> { emoji.setShowing(false); check(field(page("gifPage"), "previewBinding") == null, "hide disposes preview"); });
    }
    private void tap(RecyclerListView list, View cell) {
        long when = SystemClock.uptimeMillis();
        main(() -> {
            float x = cell.getX() + cell.getWidth() / 2f, y = cell.getY() + cell.getHeight() / 2f;
            MotionEvent down = event(when, when, MotionEvent.ACTION_DOWN, x, y);
            MotionEvent up = event(when, when + 30, MotionEvent.ACTION_UP, x, y);
            list.dispatchTouchEvent(down); list.dispatchTouchEvent(up); down.recycle(); up.recycle();
        });
    }
    private static MotionEvent event(long down, long time, int action, float x, float y) { return MotionEvent.obtain(down, time, action, x, y, 0); }
    private void clickTab(int index) { main(() -> tabs().getChildAt(index).performClick()); }
    private LinearLayout tabs() { return (LinearLayout) field(emoji, "typeTabs"); }
    private StickerMediaPage page(String name) { return (StickerMediaPage) field(emoji, name); }
    private RecyclerListView list(StickerMediaPage page) { return (RecyclerListView) field(page, "list"); }
    private View findMediaCell(StickerMediaPage page, Object target) {
        RecyclerListView list = list(page);
        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i).getTag() == target) return list.getChildAt(i);
        return null;
    }
    private boolean readyCell(StickerMediaPage page, Object target) {
        RecyclerListView list = list(page);
        View cell = findMediaCell(page, target);
        return cell != null && cell.isShown() && cell.getWidth() > 0 && cell.getHeight() > 0
                && list.getScrollState() == RecyclerListView.SCROLL_STATE_IDLE
                && (list.getItemAnimator() == null || !list.getItemAnimator().isRunning())
                && list.findChildViewUnder(cell.getX() + cell.getWidth() / 2f, cell.getY() + cell.getHeight() / 2f) == cell;
    }
    private static Object field(Object owner, String name) {
        try { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private interface Condition { boolean get(); }
    private interface Supplier<T> { T get(); }
    private void waitFor(Condition condition, String label) {
        for (int i = 0; i < 50; i++) {
            if (getOnMain(condition::get)) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out: " + label);
    }
    private <T> T getOnMain(Supplier<T> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        main(() -> result.set(supplier.get()));
        return result.get();
    }
    private void checkOnMain(Runnable check) { main(check); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
