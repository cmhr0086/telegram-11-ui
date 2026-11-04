package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ServiceMessage;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded service-cell checks, invoked on the instrumentation worker thread. */
public final class ServiceMessageDeviceChecks {
    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            FrameLayout host = new FrameLayout(activity);
            try {
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(host);
                ChatActionCell cell = new ChatActionCell(activity);
                host.addView(cell);
                Object[] selected = new Object[1];
                cell.setDelegate(new ChatActionCell.Delegate() {
                    @Override public void onUserPressed(Object id) { selected[0] = id; }
                    @Override public void onPinnedMessagePressed(Object id) { selected[0] = id; }
                });
                ServiceMessage.Person first = new ServiceMessage.Person("first", "un2 Alice");
                ServiceMessage.Person second = new ServiceMessage.Person("second", "Bob");
                cell.setMessage(ServiceMessage.invite(1, first, second, false, false));
                check(cell.getCustomText().toString().equals("un2 Alice added Bob"), "template substitution");
                draw(cell);
                tapSpan(cell, 0, false);
                check("first".equals(selected[0]), "actor callback");
                tapSpan(cell, 1, false);
                check("second".equals(selected[0]), "target callback");
                selected[0] = null;
                tapSpan(cell, 0, true);
                check(selected[0] == null, "cancelled tap");
                cell.setMessage(ServiceMessage.invite(2, new ServiceMessage.Person("new", "un2 Alice"), second, false, false));
                draw(cell); tapSpan(cell, 0, false);
                check("new".equals(selected[0]), "same text must rebind span target");
                cell.setMessage(ServiceMessage.pin(3, first, false, "pinned", "hello"));
                draw(cell); tapSpan(cell, 1, false);
                check("pinned".equals(selected[0]), "pinned message callback");
                String[] descriptions = {"photo", "video", "voice message", "audio file", "file", "contact", "map", "poll", "sticker", "GIF"};
                ServiceMessage.PinnedKind[] kinds = {ServiceMessage.PinnedKind.PHOTO, ServiceMessage.PinnedKind.VIDEO,
                        ServiceMessage.PinnedKind.VOICE, ServiceMessage.PinnedKind.MUSIC, ServiceMessage.PinnedKind.FILE,
                        ServiceMessage.PinnedKind.CONTACT, ServiceMessage.PinnedKind.LOCATION, ServiceMessage.PinnedKind.POLL,
                        ServiceMessage.PinnedKind.STICKER, ServiceMessage.PinnedKind.GIF};
                for (int i = 0; i < kinds.length; i++) {
                    cell.setMessage(ServiceMessage.pin(3, first, false, null, kinds[i], null)); draw(cell);
                    check(cell.getCustomText().toString().endsWith(descriptions[i]), "pin type " + kinds[i]);
                    Spanned rendered = (Spanned) cell.getCustomText();
                    check(rendered.getSpans(0, rendered.length(), ClickableSpan.class).length == 1, "no target link");
                }
                cell.setMessage(ServiceMessage.pin(3, first, false, "pinned", "12345678901234567890extra"));
                check(cell.getCustomText().toString().contains("12345678901234567890..."), "20 character pin truncation");
                cell.setMessage(ServiceMessage.join(4, first, false)); draw(cell);
                check(cell.getCustomText().toString().contains("returned to the group"), "ordinary join wording");
                cell.setMessage(ServiceMessage.join(4, first, false, true)); draw(cell);
                check(cell.getCustomText().toString().contains("joined the group"), "megagroup join wording");
                cell.setMessage(ServiceMessage.join(4, first, true, true)); draw(cell);
                check(cell.getCustomText().toString().equals("You joined this group"), "own megagroup join wording");
                cell.setMessage(ServiceMessage.join(4, new ServiceMessage.Person("emoji", "Alice 😀"), false));
                draw(cell); tapSpan(cell, 0, false);
                check("emoji".equals(selected[0]), "emoji replacement preserves user link");
                cell.setCustomText(null); draw(cell);
                Field cleared = ChatActionCell.class.getDeclaredField("textLayout"); cleared.setAccessible(true);
                check(cleared.get(cell) == null, "null clears stale layout");
                cell.setMessage(ServiceMessage.title(5, first, false, "New title")); draw(cell);
                cell.setMessage(ServiceMessage.custom(6, "custom service")); draw(cell);
                check(cell.getCustomText().toString().equals("custom service"), "custom text");
                cell.setCustomDate(System.currentTimeMillis() / 1000); draw(cell);
                check(cell.getCustomText().length() > 0, "date API");
            } catch (Throwable t) { failure.set(t); }
            finally { ((ViewGroup) host.getParent()).removeView(host); }
        });
        if (failure.get() != null) throw new AssertionError("service device check", failure.get());
    }
    private static void draw(ChatActionCell cell) {
        int width = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY);
        cell.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cell.layout(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
        cell.draw(new Canvas(bitmap)); bitmap.recycle();
    }
    private static void tapSpan(ChatActionCell cell, int index, boolean cancel) throws Exception {
        Spanned text = (Spanned) cell.getCustomText();
        ClickableSpan[] spans = text.getSpans(0, text.length(), ClickableSpan.class);
        check(index < spans.length, "span exists");
        // getSpans does not promise visual/text order: template replacement can insert
        // the target span before the actor span to keep placeholder offsets stable.
        java.util.Arrays.sort(spans, (a, b) -> Integer.compare(text.getSpanStart(a), text.getSpanStart(b)));
        Field field = ChatActionCell.class.getDeclaredField("textLayout"); field.setAccessible(true);
        Layout layout = (Layout) field.get(cell);
        int start = text.getSpanStart(spans[index]), end = text.getSpanEnd(spans[index]);
        int offset = (start + end) / 2;
        int line = layout.getLineForOffset(offset);
        Field xField = ChatActionCell.class.getDeclaredField("textXLeft"); xField.setAccessible(true);
        Field yField = ChatActionCell.class.getDeclaredField("textY"); yField.setAccessible(true);
        float x = xField.getInt(cell) + (layout.getPrimaryHorizontal(offset) + layout.getPrimaryHorizontal(offset + 1)) / 2f;
        float y = yField.getInt(cell) + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f;
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 20, cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP, x, y, 0);
        cell.onTouchEvent(down); cell.onTouchEvent(up); down.recycle(); up.recycle();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
