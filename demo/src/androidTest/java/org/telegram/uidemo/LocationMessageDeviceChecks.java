package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.BitmapDrawable;
import org.telegram.ui.ActionBar.Theme;
import java.lang.reflect.Field;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.LocationBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded location-message checks, called from the instrumentation worker thread. */
public final class LocationMessageDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private CountingCell held;
    private TextBubbleMessage heldMessage;
    private int clicks, longPresses;
    private TextBubbleMessage clickedMessage;
    private TextBubbleMessage.Location clickedLocation;

    private final class CountingCell extends LocationBubbleCell {
        CountingCell() { super(activity); }
        CountingCell(Theme.ResourcesProvider provider) { super(activity, provider); }
        int reactionX() { return reactionsLayoutInBubble.x; }
        @Override protected void onInteractiveLongPress(boolean reply) { longPresses++; }
    }
    private LocationMessageDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation; this.activity = activity;
    }
    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        LocationMessageDeviceChecks test = new LocationMessageDeviceChecks(instrumentation, activity);
        try {
            test.main(() -> {
                test.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.host, new ViewGroup.LayoutParams(-1, -1));
                test.scenarios();
                test.interceptOwnership();
                test.reactionPosition();
                test.placeholderAlpha();
                test.startLongPress();
            });
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 180L);
            test.main(test::finishLongPress);
        } finally {
            test.main(() -> {
                if (test.host != null && test.host.getParent() instanceof ViewGroup)
                    ((ViewGroup) test.host.getParent()).removeView(test.host);
            });
        }
    }
    private void main(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() != null) throw new AssertionError("location device checks", failure.get());
    }
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }
    private void delegate(CountingCell cell) {
        cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
            @Override public void onMediaPressed(TextBubbleCell c, TextBubbleMessage m) { }
            @Override public void onLocationPressed(TextBubbleCell c, TextBubbleMessage m, TextBubbleMessage.Location location) {
                clicks++; clickedMessage = m; clickedLocation = location;
            }
        });
    }
    private Bitmap draw(CountingCell cell, int width) { return draw(cell, width, false); }
    private Bitmap draw(CountingCell cell, int width, boolean opaqueBackground) {
        cell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cell.layout(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight());
        check(cell.getHeight() > 0, "positive height");
        Bitmap bitmap = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (opaqueBackground) canvas.drawColor(Color.WHITE);
        cell.draw(canvas); return bitmap;
    }
    private static RectF rect(CountingCell cell) { RectF rect = new RectF(); cell.getMapRect(rect); return rect; }
    private static void event(CountingCell cell, RectF rect, int action, long down) {
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, rect.centerX(), rect.centerY(), 0);
        cell.onTouchEvent(e); e.recycle();
    }
    private static void tap(CountingCell cell, RectF rect) {
        long down = SystemClock.uptimeMillis(); event(cell, rect, MotionEvent.ACTION_DOWN, down); event(cell, rect, MotionEvent.ACTION_UP, down);
    }
    private void scenarios() {
        for (boolean out : new boolean[]{false, true}) for (boolean venue : new boolean[]{false, true}) {
            CountingCell cell = new CountingCell(); host.addView(cell); delegate(cell);
            ColorDrawable image = new ColorDrawable(Color.BLUE); image.setBounds(3, 5, 23, 27); Rect original = new Rect(image.getBounds());
            TextBubbleMessage.Location location = new TextBubbleMessage.Location(34.22, 108.95,
                    venue ? "地点 طويل title" : null, venue ? "عنوان طويل جدا لاختبار النص الممتد في الاتجاه الأيمن إلى اليسار 123456789" : null, image);
            TextBubbleMessage message = TextBubbleMessage.location(1, out, "12:34", 1, location);
            message.group = true; message.sender = new TextBubbleMessage.Sender(2, "Sender", null);
            message.forwardedFrom = new TextBubbleMessage.ForwardedFrom(3, "Source");
            message.replyTo = new TextBubbleMessage.ReplyTo(4, "Reply", "Text");
            message.reactions.add(new TextBubbleMessage.Reaction("😀", 1, false));
            cell.setMessage(message, false, false);
            for (int widthDp : new int[]{240, 400}) {
                int width = AndroidUtilities.dp(widthDp); Bitmap bitmap = draw(cell, width); RectF map = rect(cell);
                check(!map.isEmpty() && map.left >= 0 && map.right <= width && map.top >= 0 && map.bottom <= cell.getHeight(), "map bounds");
                check(Math.abs(map.height() - AndroidUtilities.dp(195)) <= 1, "upstream 195dp map height");
                int side = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : width;
                int bubbleWidth = Math.min(side - AndroidUtilities.dp(out ? 50 : 102), AndroidUtilities.dp(289)) - AndroidUtilities.dp(4);
                int expectedMapWidth = bubbleWidth - AndroidUtilities.dp(venue ? 17 : 8);
                check(Math.abs(map.width() - expectedMapWidth) <= 1, "upstream map width for group, direction and venue");
                check(image.getBounds().equals(original), "host map drawable bounds restored");
                float[] radii = cell.getMapRadii();
                check(radii != null && radii.length == 8, "four rounded corners");
                for (float radius : radii) check(radius >= 0 && radius <= Math.min(map.width(), map.height()) / 2, "valid map radius");
                bitmap.recycle();
                int before = clicks; tap(cell, map);
                check(clicks == before + 1 && clickedMessage == message && clickedLocation == location, "location callback identity");
                long down = SystemClock.uptimeMillis(); event(cell, map, MotionEvent.ACTION_DOWN, down); event(cell, map, MotionEvent.ACTION_CANCEL, down);
                check(clicks == before + 1, "cancel does not click");
            }
            RectF old = rect(cell); long down = SystemClock.uptimeMillis(); event(cell, old, MotionEvent.ACTION_DOWN, down);
            TextBubbleMessage.Location missing = new TextBubbleMessage.Location(0, 0, null, null, null);
            TextBubbleMessage replacement = TextBubbleMessage.location(9, out, "12:34", 1, missing);
            cell.setMessage(replacement, false, false); Bitmap placeholder = draw(cell, AndroidUtilities.dp(400));
            int before = clicks; event(cell, old, MotionEvent.ACTION_UP, down); check(clicks == before, "rebind cancels old press");
            RectF missingRect = rect(cell); check(!missingRect.isEmpty(), "missing map placeholder drawn");
            boolean visible = false;
            for (int y = (int) missingRect.top + 2; y < (int) missingRect.bottom - 2 && !visible; y += 2)
                for (int x = (int) missingRect.left + 2; x < (int) missingRect.right - 2; x += 2)
                    if (Color.alpha(placeholder.getPixel(x, y)) != 0) { visible = true; break; }
            check(visible, "placeholder area contains visible drawing");
            placeholder.recycle(); tap(cell, missingRect);
            check(clicks == before + 1 && clickedLocation == missing && clickedMessage == replacement, "placeholder remains clickable");
            cell.setCheckBoxVisible(true, false); draw(cell, AndroidUtilities.dp(400)).recycle();
            RectF selected = rect(cell); before = clicks; tap(cell, selected); check(clicks == before, "selection suppresses location action");
            cell.setCheckBoxVisible(false, false); cell.setMessage(null, false, false);
            check(rect(cell).isEmpty(), "clear model removes hit area");
            host.removeView(cell);
        }
    }
    private final class InterceptParent extends FrameLayout {
        int releases;
        InterceptParent() { super(activity); }
        @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
            if (!disallow) releases++;
            super.requestDisallowInterceptTouchEvent(disallow);
        }
    }
    private void interceptOwnership() {
        InterceptParent parent = new InterceptParent(); host.addView(parent);
        CountingCell cell = new CountingCell(); parent.addView(cell); delegate(cell);
        TextBubbleMessage first = TextBubbleMessage.location(41, false, "12:34", 0,
                new TextBubbleMessage.Location(0, 0, null, null, new ColorDrawable(Color.BLUE)));
        TextBubbleMessage second = TextBubbleMessage.location(42, false, "12:34", 0,
                new TextBubbleMessage.Location(1, 1, null, null, new ColorDrawable(Color.BLUE)));
        cell.setMessage(first, false, false); draw(cell, AndroidUtilities.dp(320)).recycle();
        parent.releases = 0; cell.setMessage(second, false, false); draw(cell, AndroidUtilities.dp(320)).recycle();
        check(parent.releases == 0, "rebinding without a held intercept does not release");
        RectF map = rect(cell); long down = SystemClock.uptimeMillis(); int before = clicks;
        event(cell, map, MotionEvent.ACTION_DOWN, down); event(cell, map, MotionEvent.ACTION_DOWN, down);
        check(parent.releases == 0, "replayed down must not release intercept");
        event(cell, map, MotionEvent.ACTION_UP, down);
        check(parent.releases == 1 && clicks == before + 1, "up releases once and clicks once");
        parent.releases = 0; before = clicks; down = SystemClock.uptimeMillis();
        event(cell, map, MotionEvent.ACTION_DOWN, down); event(cell, map, MotionEvent.ACTION_CANCEL, down);
        check(parent.releases == 1 && clicks == before, "cancel releases once without click");
        parent.releases = 0; down = SystemClock.uptimeMillis();
        event(cell, map, MotionEvent.ACTION_DOWN, down); cell.setMessage(first, false, false);
        event(cell, map, MotionEvent.ACTION_UP, down);
        check(parent.releases == 1 && clicks == before, "rebind releases once and cancels click");
        draw(cell, AndroidUtilities.dp(320)).recycle(); map = rect(cell);
        parent.releases = 0; down = SystemClock.uptimeMillis();
        event(cell, map, MotionEvent.ACTION_DOWN, down); parent.removeView(cell);
        event(cell, map, MotionEvent.ACTION_UP, down);
        check(parent.releases == 1 && clicks == before, "detach releases once and cancels click");
        host.removeView(parent);
    }
    private void reactionPosition() {
        CountingCell cell = new CountingCell(); host.addView(cell); delegate(cell);
        TextBubbleMessage m = TextBubbleMessage.location(30, false, "12:34", 0,
                new TextBubbleMessage.Location(0, 0, null, null, new ColorDrawable(Color.BLUE)));
        m.reactions.add(new TextBubbleMessage.Reaction("😀", 1, false));
        for (boolean pinnedBottom : new boolean[]{false, true}) {
            cell.setMessage(m, false, pinnedBottom); draw(cell, AndroidUtilities.dp(320)).recycle();
            check(Math.abs(cell.reactionX() - (rect(cell).left + AndroidUtilities.dp(4))) <= 1,
                    "incoming media reaction anchor, pinned=" + pinnedBottom);
        }
        host.removeView(cell);
    }
    private Bitmap placeholder(int color, RectF out) {
        Theme.ResourcesProvider provider = new Theme.ResourcesProvider() {
            @Override public int getColor(int key) {
                return key == Theme.key_chat_inLocationIcon ? color : Theme.getColor(key);
            }
        };
        CountingCell cell = new CountingCell(provider); host.addView(cell);
        TextBubbleMessage m = TextBubbleMessage.location(31, false, "12:34", 0,
                new TextBubbleMessage.Location(0, 0, null, null, null));
        cell.setMessage(m, false, false); Bitmap result = draw(cell, AndroidUtilities.dp(320), true); cell.getMapRect(out);
        try {
            Field field = LocationBubbleCell.class.getDeclaredField("placeholder"); field.setAccessible(true);
            BitmapDrawable drawable = (BitmapDrawable) field.get(cell);
            check(drawable != null && drawable.getBitmap() != null, "SVG placeholder bitmap exists");
            Bitmap svg = drawable.getBitmap(); boolean content = false;
            for (int y = 0; y < svg.getHeight() && !content; y += 2)
                for (int x = 0; x < svg.getWidth(); x += 2)
                    if (Color.alpha(svg.getPixel(x, y)) != 0) { content = true; break; }
            check(content, "SVG placeholder bitmap has visible content");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        host.removeView(cell); return result;
    }
    private void placeholderAlpha() {
        RectF first = new RectF(), second = new RectF();
        Bitmap red = placeholder(Color.RED, first), blue = placeholder(Color.BLUE, second);
        check(first.equals(second), "placeholder comparison geometry");
        int maxDiff = 0;
        for (int y = (int) first.top + 2; y < (int) first.bottom - 2; y++) {
            for (int x = (int) first.left + 2; x < (int) first.right - 2; x++) {
                int a = red.getPixel(x, y), b = blue.getPixel(x, y);
                maxDiff = Math.max(maxDiff, Math.max(Math.abs(Color.red(a) - Color.red(b)), Math.abs(Color.blue(a) - Color.blue(b))));
            }
        }
        red.recycle(); blue.recycle();
        // The sole difference is an opaque red/blue SVG tint. Alpha-70 composition
        // caps its per-channel effect near 70 rather than the 255 of an opaque layer.
        check(maxDiff > 3 && maxDiff <= 72, "placeholder SVG is visibly blended at alpha 70, difference=" + maxDiff);
    }
    private void startLongPress() {
        held = new CountingCell(); host.addView(held); delegate(held);
        heldMessage = TextBubbleMessage.location(17, false, "12:34", 0, new TextBubbleMessage.Location(1, 2, null, null, null));
        held.setMessage(heldMessage, false, false); draw(held, AndroidUtilities.dp(320)).recycle();
        RectF map = rect(held); long down = SystemClock.uptimeMillis(); event(held, map, MotionEvent.ACTION_DOWN, down);
    }
    private void finishLongPress() {
        check(longPresses == 1, "long press invokes selection handler");
        int before = clicks; RectF map = rect(held); long down = SystemClock.uptimeMillis(); event(held, map, MotionEvent.ACTION_UP, down);
        check(clicks == before, "long press does not invoke location");
        host.removeView(held);
    }
}
