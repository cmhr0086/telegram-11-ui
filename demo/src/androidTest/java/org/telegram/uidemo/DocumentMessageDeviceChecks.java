package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DocumentBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Components.RadialProgress2;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded document regression checks on the instrumentation worker thread. */
public final class DocumentMessageDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private CountingCell held;
    private int presses, longPresses;
    private TextBubbleMessage pressedMessage;
    private final class CountingCell extends DocumentBubbleCell {
        CountingCell() { super(activity); }
        int bubbleWidth() { return backgroundWidth; }
        Rect bubbleBounds() { return new Rect(currentBackgroundDrawable.getBounds()); }
        @Override protected void onInteractiveLongPress(boolean reply) { longPresses++; }
    }
    private final class InterceptParent extends FrameLayout {
        int releases;
        InterceptParent() { super(activity); }
        @Override public void requestDisallowInterceptTouchEvent(boolean value) { if (!value) releases++; super.requestDisallowInterceptTouchEvent(value); }
    }
    private DocumentMessageDeviceChecks(Instrumentation i, Activity a) { instrumentation = i; activity = a; }
    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        DocumentMessageDeviceChecks test = new DocumentMessageDeviceChecks(instrumentation, activity);
        try {
            test.main(() -> {
                test.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.host, new ViewGroup.LayoutParams(-1, -1));
                test.checks(); test.longContentBounds(); test.rtlCaptionBounds(); test.startLongPress();
            });
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 180L);
            test.main(test::finishLongPress);
        } finally {
            test.main(() -> { if (test.host != null && test.host.getParent() instanceof ViewGroup) ((ViewGroup) test.host.getParent()).removeView(test.host); });
        }
    }
    private void main(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new AssertionError("document device checks", error.get());
    }
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }
    private static Object field(Object owner, String name) {
        try { Field f = DocumentBubbleCell.class.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private void delegate(CountingCell cell) {
        cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
            @Override public void onMediaPressed(TextBubbleCell c, TextBubbleMessage m) { presses++; pressedMessage = m; }
        });
    }
    private void draw(CountingCell cell, int width) {
        cell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cell.layout(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight());
        check(cell.getHeight() > 0, "positive height");
        Bitmap b = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888); cell.draw(new Canvas(b)); b.recycle();
    }
    private static RectF thumbnail(CountingCell cell) { RectF r = new RectF(); cell.getThumbnailRect(r); return r; }
    private static RectF button(CountingCell cell) { RectF r = new RectF(); cell.getButtonRect(r); return r; }
    private static int thumbnailPixel(CountingCell cell) {
        Bitmap b = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
        cell.draw(new Canvas(b)); RectF r = thumbnail(cell);
        int pixel = b.getPixel((int) (r.left + AndroidUtilities.dp(9)), (int) r.centerY()); b.recycle(); return pixel;
    }
    private static void event(CountingCell cell, RectF r, int action, long down) {
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, r.centerX(), r.centerY(), 0); cell.onTouchEvent(e); e.recycle();
    }
    private static void tap(CountingCell cell, RectF r) { long t = SystemClock.uptimeMillis(); event(cell, r, MotionEvent.ACTION_DOWN, t); event(cell, r, MotionEvent.ACTION_UP, t); }
    private static void inside(RectF r, CountingCell cell, String name) {
        check(!r.isEmpty() && r.left >= 0 && r.top >= 0 && r.right <= cell.getWidth() && r.bottom <= cell.getHeight(), name + " bounds");
    }
    private void checks() {
        for (boolean out : new boolean[]{false, true}) {
            InterceptParent parent = new InterceptParent(); host.addView(parent);
            CountingCell cell = new CountingCell(); parent.addView(cell); delegate(cell);
            TextBubbleMessage old = TextBubbleMessage.document(1, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE, "old.pdf", 1024);
            cell.setMessage(old, false, false); draw(cell, AndroidUtilities.dp(320));
            check(thumbnail(cell).isEmpty(), "old no-thumbnail factory");
            check(Math.abs(button(cell).width() - AndroidUtilities.dp(44)) <= 1, "old 44dp file button");
            int before = presses; tap(cell, button(cell)); check(presses == before + 1 && pressedMessage == old, "old file button callback");
            ColorDrawable image = new ColorDrawable(Color.BLUE); image.setBounds(2, 4, 21, 30); Rect original = new Rect(image.getBounds());
            TextBubbleMessage m = TextBubbleMessage.document(2, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE,
                    "Long document name for ellipsis.pdf", 2048, image, "Caption");
            m.group = true; m.sender = new TextBubbleMessage.Sender(3, "Sender", null);
            m.replyTo = new TextBubbleMessage.ReplyTo(4, "Reply", "quoted"); m.forwardedFrom = new TextBubbleMessage.ForwardedFrom(5, "Source");
            m.reactions.add(new TextBubbleMessage.Reaction("😀", 1, false));
            cell.setMessage(m, false, false);
            for (int dp : new int[]{240, 400}) {
                draw(cell, AndroidUtilities.dp(dp));
                RectF thumb = thumbnail(cell), control = button(cell); inside(thumb, cell, "thumbnail"); inside(control, cell, "control");
                check(Math.abs(thumb.width() - AndroidUtilities.dp(86)) <= 1 && Math.abs(thumb.height() - AndroidUtilities.dp(86)) <= 1, "86dp thumbnail");
                check(Math.abs(control.width() - AndroidUtilities.dp(48)) <= 1 && Math.abs(control.height() - AndroidUtilities.dp(48)) <= 1, "48dp thumbnail control");
                check(Math.abs(thumb.centerX() - control.centerX()) <= 1 && Math.abs(thumb.centerY() - control.centerY()) <= 1, "control centered on thumbnail");
                check(image.getBounds().equals(original), "host thumbnail bounds restored");
                for (int state : new int[]{TextBubbleMessage.MEDIA_READY, TextBubbleMessage.MEDIA_DOWNLOAD, TextBubbleMessage.MEDIA_LOADING}) {
                    m.mediaState = state; m.mediaProgress = .5f; cell.setMessage(m, false, false); draw(cell, AndroidUtilities.dp(dp));
                    int expected = state == TextBubbleMessage.MEDIA_READY ? -1 : state == TextBubbleMessage.MEDIA_DOWNLOAD ? 0 : 1;
                    check((Integer) field(cell, "buttonState") == expected, "download control state");
                    before = presses; tap(cell, thumbnail(cell)); check(presses == before + 1 && pressedMessage == m, "thumbnail action state=" + state);
                }
                RectF thumb2 = thumbnail(cell); before = presses; long t = SystemClock.uptimeMillis();
                event(cell, thumb2, MotionEvent.ACTION_DOWN, t); event(cell, thumb2, MotionEvent.ACTION_CANCEL, t);
                check(presses == before, "cancel does not invoke file");
            }
            m.mediaState = TextBubbleMessage.MEDIA_READY; cell.setMessage(m, false, false); draw(cell, AndroidUtilities.dp(400));
            int bluePixel = thumbnailPixel(cell);
            ColorDrawable replacement = new ColorDrawable(Color.RED); replacement.setBounds(4, 5, 10, 12); Rect replacementBounds = new Rect(replacement.getBounds());
            m.documentThumbnail = replacement; m.isOut = !out; m.mediaState = TextBubbleMessage.MEDIA_READY;
            cell.setMessage(m, false, false); draw(cell, AndroidUtilities.dp(400));
            check(replacement.getBounds().equals(replacementBounds) && !thumbnail(cell).isEmpty(), "same model thumbnail and direction replacement");
            int redPixel = thumbnailPixel(cell);
            check(Color.blue(bluePixel) > Color.red(bluePixel) && Color.red(redPixel) > Color.blue(redPixel), "same model draws updated thumbnail");
            RectF r = thumbnail(cell); parent.releases = 0; before = presses; long t = SystemClock.uptimeMillis();
            event(cell, r, MotionEvent.ACTION_DOWN, t); cell.setMessage(old, false, false); event(cell, r, MotionEvent.ACTION_UP, t);
            check(parent.releases == 1 && presses == before, "rebind releases gesture without action");
            cell.setMessage(m, false, false); cell.setCheckBoxVisible(true, false); draw(cell, AndroidUtilities.dp(400));
            before = presses; tap(cell, thumbnail(cell)); check(presses == before, "selection mode blocks file action");
            cell.setCheckBoxVisible(false, false); cell.setMessage(null, false, false);
            check(thumbnail(cell).isEmpty(), "null bind clears thumbnail bounds");
            parent.removeView(cell); host.removeView(parent);
        }
    }
    private void longContentBounds() {
        for (boolean out : new boolean[]{false, true}) for (boolean withThumb : new boolean[]{false, true}) {
            CountingCell cell = new CountingCell(); host.addView(cell); delegate(cell);
            TextBubbleMessage m = TextBubbleMessage.document(140, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE,
                    "A deliberately very long document filename with extensive suffix and Unicode 文件名称.pdf", 12345678,
                    withThumb ? new ColorDrawable(Color.BLUE) : null,
                    "A very long caption that wraps across many lines and should remain inside the document bubble and viewport.");
            m.group = true; m.sender = new TextBubbleMessage.Sender(3, "Sender", null);
            cell.setMessage(m, false, false);
            for (int dp : new int[]{240, 400}) {
                int width = AndroidUtilities.dp(dp); draw(cell, width);
                Rect bubble = cell.bubbleBounds();
                check(bubble.left >= 0 && bubble.right <= width, "long content bubble inside width=" + dp + " thumb=" + withThumb + " out=" + out + " bounds=" + bubble);
                android.text.Layout title = (android.text.Layout) field(cell, "docTitleLayout");
                int titleOffset = (Integer) field(cell, "docTitleOffsetX");
                float titleX = withThumb ? thumbnail(cell).right + AndroidUtilities.dp(10) : button(cell).left + AndroidUtilities.dp(53);
                for (int i = 0; i < title.getLineCount(); i++) {
                    float left = titleX + titleOffset + title.getLineLeft(i), right = titleX + titleOffset + title.getLineRight(i);
                    check(left >= 0 && right <= width + 1, "filename line inside viewport");
                }
                android.graphics.drawable.Drawable menu = (android.graphics.drawable.Drawable) field(cell, "menuDrawable");
                Rect menuBounds = menu.getBounds();
                check(menuBounds.left >= 0 && menuBounds.right <= width && menuBounds.top >= 0 && menuBounds.bottom <= cell.getHeight(), "menu inside viewport");
            }
            // Without a thumbnail and without a caption retain the original width calculation.
            TextBubbleMessage legacy = TextBubbleMessage.document(141, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE, "plain.pdf", 100);
            legacy.group = true; legacy.sender = new TextBubbleMessage.Sender(3, "Sender", null);
            cell.setMessage(legacy, false, false);
            for (int dp : new int[]{240, 400}) {
                int width = AndroidUtilities.dp(dp); draw(cell, width);
                int side = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : width;
                int expected = side - AndroidUtilities.dp(80) - (out ? 0 : AndroidUtilities.dp(48));
                check(cell.bubbleWidth() == expected, "legacy no-thumb no-caption width");
            }
            host.removeView(cell);
        }
    }
    private void rtlCaptionBounds() {
        for (boolean out : new boolean[]{false, true}) for (boolean thumb : new boolean[]{false, true})
            for (boolean pinnedBottom : new boolean[]{false, true}) {
                CountingCell cell = new CountingCell(); host.addView(cell);
                for (String caption : new String[]{"مثال بسيط", "Hello مثال بسيط world مرحبا"}) {
                    TextBubbleMessage m = TextBubbleMessage.document(150, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE,
                            "example.pdf", 100, thumb ? new ColorDrawable(Color.BLUE) : null, caption);
                    m.group = true; m.sender = new TextBubbleMessage.Sender(3, "Sender", null);
                    cell.setMessage(m, false, pinnedBottom);
                    for (int dp : new int[]{240, 400}) {
                        draw(cell, AndroidUtilities.dp(dp));
                        Rect bubble = cell.bubbleBounds();
                        android.text.Layout layout = (android.text.Layout) field(cell, "captionLayout");
                        float offset = (Float) field(cell, "captionTextXOffset");
                        float drawX = bubble.left + AndroidUtilities.dp(out || pinnedBottom ? 11 : 17) - offset;
                        for (int i = 0; i < layout.getLineCount(); i++) {
                            float left = drawX + layout.getLineLeft(i), right = drawX + layout.getLineRight(i);
                            check(left >= bubble.left - 1 && right <= bubble.right + 1,
                                    "RTL/mixed caption line inside bubble: " + caption + " out=" + out + " thumb=" + thumb
                                            + " pinned=" + pinnedBottom + " width=" + dp + " line=" + left + ".." + right + " bubble=" + bubble);
                        }
                    }
                }
                host.removeView(cell);
            }
    }
    private void startLongPress() {
        held = new CountingCell(); host.addView(held); delegate(held);
        held.setMessage(TextBubbleMessage.document(80, false, "12:34", 0, "file.pdf", 20, new ColorDrawable(Color.BLUE), null), false, false);
        draw(held, AndroidUtilities.dp(320)); long down = SystemClock.uptimeMillis(); event(held, thumbnail(held), MotionEvent.ACTION_DOWN, down);
    }
    private void finishLongPress() {
        int before = presses; check(longPresses == 1, "file long press"); long down = SystemClock.uptimeMillis();
        event(held, thumbnail(held), MotionEvent.ACTION_UP, down); check(presses == before, "long press suppresses file click");
        host.removeView(held);
    }
}
