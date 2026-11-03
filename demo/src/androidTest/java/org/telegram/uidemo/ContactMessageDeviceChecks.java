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
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ContactBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded contact-message checks, called from the instrumentation worker thread. */
public final class ContactMessageDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private int clicks, action;
    private TextBubbleMessage selectedMessage;
    private TextBubbleMessage.Contact selectedContact;

    private ContactMessageDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation; this.activity = activity;
    }
    public static void run(Instrumentation instrumentation, Activity activity) {
        ContactMessageDeviceChecks test = new ContactMessageDeviceChecks(instrumentation, activity);
        try {
            test.main(() -> {
                test.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.host,
                        new ViewGroup.LayoutParams(-1, -1));
                test.checks();
            });
        } finally {
            test.main(() -> {
                if (test.host != null && test.host.getParent() instanceof ViewGroup)
                    ((ViewGroup) test.host.getParent()).removeView(test.host);
            });
        }
    }
    private void main(Runnable work) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { work.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() != null) throw new AssertionError("contact device checks", failure.get());
    }
    private static void check(boolean condition, String text) { if (!condition) throw new AssertionError(text); }
    private static final class Palette implements Theme.ResourcesProvider {
        final boolean dark; final Set<Integer> keys = new HashSet<>();
        Palette(boolean dark) { this.dark = dark; }
        @Override public int getColor(int key) {
            keys.add(key);
            for (int avatarKey : Theme.keys_avatar_nameInMessage) {
                if (key == avatarKey) return dark ? Color.YELLOW : Color.BLUE;
            }
            if (key == Theme.key_chat_outReplyNameText)
                return dark ? Color.YELLOW : Color.BLUE;
            if (key == Theme.key_chat_messageTextIn || key == Theme.key_chat_messageTextOut)
                return dark ? Color.WHITE : Color.BLACK;
            return Theme.getColor(key);
        }
        @Override public boolean isDark() { return dark; }
    }
    private void draw(ContactBubbleCell cell, int width) {
        cell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cell.layout(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight());
        check(cell.getWidth() > 0 && cell.getHeight() > 0, "positive cell dimensions");
        Bitmap bitmap = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
        cell.draw(new Canvas(bitmap)); bitmap.recycle();
    }
    private static void event(ContactBubbleCell cell, RectF rect, int action, long start) {
        MotionEvent e = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, rect.centerX(), rect.centerY(), 0);
        cell.onTouchEvent(e); e.recycle();
    }
    private static void tap(ContactBubbleCell cell, RectF rect) {
        long t = SystemClock.uptimeMillis(); event(cell, rect, MotionEvent.ACTION_DOWN, t); event(cell, rect, MotionEvent.ACTION_UP, t);
    }
    private static void contained(RectF rect, int width, int height, String label) {
        check(!rect.isEmpty() && rect.left >= 0 && rect.top >= 0 && rect.right <= width && rect.bottom <= height, label + " in cell");
    }
    private void checks() {
        for (boolean dark : new boolean[]{false, true}) for (boolean out : new boolean[]{false, true}) {
            Palette palette = new Palette(dark);
            ContactBubbleCell cell = new ContactBubbleCell(activity, palette);
            host.addView(cell);
            cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
                @Override public void onMediaPressed(TextBubbleCell c, TextBubbleMessage m) {
                    throw new AssertionError("contact action must use the contact callback");
                }
                @Override public void onContactAction(TextBubbleCell c, TextBubbleMessage m, TextBubbleMessage.Contact contact, int which) {
                    clicks++; action = which; selectedMessage = m; selectedContact = contact;
                }
            });
            for (int kind = 0; kind < 3; kind++) {
                ColorDrawable avatar = new ColorDrawable(Color.RED); avatar.setBounds(2, 3, 19, 23);
                Rect original = new Rect(avatar.getBounds());
                TextBubbleMessage.Contact contact = new TextBubbleMessage.Contact(100 + kind,
                        "Very long contact name to test truncation with emoji 😀", kind == 0 ? "" : "+123 456 789 012345678901234567890",
                        avatar, kind == 1, kind == 2);
                TextBubbleMessage message = TextBubbleMessage.contact(1, out, "12:34", 1, contact);
                message.group = true;
                message.sender = new TextBubbleMessage.Sender(8, "Sender", null);
                message.replyTo = new TextBubbleMessage.ReplyTo(10, "Reply author", "Reply text");
                message.forwardedFrom = new TextBubbleMessage.ForwardedFrom(12, "Forward origin");
                cell.setMessage(message, false, false);
                int expected = kind == 0 ? ContactBubbleCell.ACTION_VIEW : kind == 1 ? ContactBubbleCell.ACTION_MESSAGE : ContactBubbleCell.ACTION_ADD;
                for (int width : new int[]{AndroidUtilities.dp(240), AndroidUtilities.dp(400)}) {
                    draw(cell, width);
                    check(palette.keys.contains(out ? Theme.key_chat_outReplyNameText : Theme.keys_avatar_nameInMessage[contact.colorId]),
                            "contact identity chooses the upstream name color");
                    check(Theme.chat_contactPhonePaint.getColor() == (dark ? Color.WHITE : Color.BLACK), "phone uses host message text color");
                    RectF panel = new RectF(), image = new RectF(), button = new RectF();
                    cell.getContactRect(panel); cell.getAvatarRect(image); cell.getActionRect(expected, button);
                    contained(panel, width, cell.getHeight(), "contact");
                    contained(image, width, cell.getHeight(), "avatar");
                    contained(button, width, cell.getHeight(), "action");
                    check(Math.abs(image.width() - AndroidUtilities.dp(46)) <= 1 && Math.abs(image.height() - AndroidUtilities.dp(46)) <= 1,
                            "upstream 46dp avatar");
                    check(avatar.getBounds().equals(original), "host avatar bounds restored");
                    check(Theme.chat_contactPhonePaint.getColor() == (dark ? Color.WHITE : Color.BLACK), "phone uses message text color");
                    int before = clicks; tap(cell, button);
                    check(clicks == before + 1 && selectedMessage == message && selectedContact == contact && action == expected,
                            "selected contact and action");
                    long time = SystemClock.uptimeMillis(); event(cell, button, MotionEvent.ACTION_DOWN, time); event(cell, button, MotionEvent.ACTION_CANCEL, time);
                    check(clicks == before + 1, "cancel does not dispatch");
                }
                // A down bound to one model must never dispatch after replacing it.
                RectF button = new RectF(); cell.getActionRect(expected, button);
                long time = SystemClock.uptimeMillis(); event(cell, button, MotionEvent.ACTION_DOWN, time);
                TextBubbleMessage replacement = TextBubbleMessage.contact(2, out, "12:34", 1,
                        new TextBubbleMessage.Contact(999, "New", "123", null));
                cell.setMessage(replacement, false, false); draw(cell, AndroidUtilities.dp(400));
                int before = clicks; event(cell, button, MotionEvent.ACTION_UP, time);
                check(clicks == before, "rebind cancels pending action");
                RectF replacementButton = new RectF(); cell.getActionRect(ContactBubbleCell.ACTION_VIEW, replacementButton);
                cell.setCheckBoxVisible(true, false); draw(cell, AndroidUtilities.dp(400));
                cell.getActionRect(ContactBubbleCell.ACTION_VIEW, replacementButton);
                tap(cell, replacementButton); check(clicks == before, "selection mode does not dispatch contact action");
                cell.setCheckBoxVisible(false, false);
            }
            TextBubbleMessage.Contact dual = new TextBubbleMessage.Contact(888, "Dual", "555", null, true, true);
            TextBubbleMessage dualMessage = TextBubbleMessage.contact(3, out, "12:34", 1, dual);
            cell.setMessage(dualMessage, false, false);
            draw(cell, AndroidUtilities.dp(400));
            for (int which : new int[]{ContactBubbleCell.ACTION_MESSAGE, ContactBubbleCell.ACTION_ADD}) {
                RectF button = new RectF(); cell.getActionRect(which, button);
                contained(button, cell.getWidth(), cell.getHeight(), "dual action");
                int before = clicks; tap(cell, button);
                check(clicks == before + 1 && action == which && selectedMessage == dualMessage && selectedContact == dual,
                        "dual button identity");
            }
            RectF avatar = new RectF(); cell.getAvatarRect(avatar);
            int before = clicks; tap(cell, avatar);
            check(clicks == before + 1 && action == ContactBubbleCell.ACTION_VIEW && selectedMessage == dualMessage && selectedContact == dual,
                    "dual panel opens contact");
            check(palette.keys.contains(out ? Theme.key_chat_outReplyNameText : Theme.keys_avatar_nameInMessage[dual.colorId])
                    && palette.keys.contains(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn),
                    "contact color and message text theme keys consulted");
            long down = SystemClock.uptimeMillis(); event(cell, avatar, MotionEvent.ACTION_DOWN, down);
            cell.setMessage(null, false, false);
            before = clicks; event(cell, avatar, MotionEvent.ACTION_UP, down);
            check(clicks == before, "clearing model cancels pending contact action");
            RectF cleared = new RectF(); cell.getContactRect(cleared);
            check(cleared.isEmpty(), "cleared contact has no stale touch bounds");
            cell.getAvatarRect(cleared); check(cleared.isEmpty(), "cleared avatar bounds");
            host.removeView(cell);
        }
    }
}
