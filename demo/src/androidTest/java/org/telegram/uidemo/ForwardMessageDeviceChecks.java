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
import org.telegram.ui.Cells.*;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.Sticker;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded forwarding checks; call on the instrumentation worker thread. */
public final class ForwardMessageDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private int clicks;
    private TextBubbleMessage clickedMessage;
    private TextBubbleMessage.ForwardedFrom clickedSource;

    private ForwardMessageDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation; this.activity = activity;
    }
    public static void run(Instrumentation instrumentation, Activity activity) {
        ForwardMessageDeviceChecks test = new ForwardMessageDeviceChecks(instrumentation, activity);
        try {
            test.main(() -> {
                test.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.host,
                        new ViewGroup.LayoutParams(-1, -1));
                test.runChecks();
            });
        } finally {
            test.main(() -> {
                if (test.host != null && test.host.getParent() instanceof ViewGroup)
                    ((ViewGroup) test.host.getParent()).removeView(test.host);
            });
        }
    }
    private void main(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new AssertionError("forward device checks", error.get());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Object field(Object owner, String name) {
        try { Field f = TextBubbleCell.class.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (Exception e) { throw new AssertionError(name, e); }
    }
    private static int offset(TextBubbleCell cell) {
        try { Method m = TextBubbleCell.class.getDeclaredMethod("forwardOffset"); m.setAccessible(true); return (Integer) m.invoke(cell); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private TextBubbleCell cell(int kind) {
        switch (kind) {
            case 1: return new PhotoBubbleCell(activity);
            case 2: return new VoiceBubbleCell(activity);
            case 3: return new DocumentBubbleCell(activity);
            case 4: return new AlbumBubbleCell(activity);
            case 5: return new GifBubbleCell(activity);
            case 6: return new StickerBubbleCell(activity);
            case 7: return new RoundVideoBubbleCell(activity);
            default: return new TextBubbleCell(activity);
        }
    }
    private TextBubbleMessage message(int kind, boolean out) {
        ColorDrawable image = new ColorDrawable(Color.BLUE);
        File absent = new File(activity.getCacheDir(), "forward-nonexistent-media");
        switch (kind) {
            case 1: return TextBubbleMessage.photo(1, out, "12:34", 1, image, "caption");
            case 2: return TextBubbleMessage.voice(2, out, "12:34", 1, 12, null);
            case 3: return TextBubbleMessage.document(3, out, "12:34", 1, "file.pdf", 1000);
            case 4: return TextBubbleMessage.album(4, out, "12:34", 1, Arrays.asList(
                    TextBubbleMessage.photo(41, out, "12:34", 1, image, null),
                    TextBubbleMessage.photo(42, out, "12:34", 1, image, null)), "caption");
            case 5: return TextBubbleMessage.gif(5, out, "12:34", 1, new Gif(5, absent, image, 200, 120), "caption");
            case 6: return TextBubbleMessage.sticker(6, out, "12:34", 1,
                    new Sticker(6, Sticker.Format.WEBP, absent, image, 200, 200, ""));
            case 7: return TextBubbleMessage.roundVideo(7, out, "12:34", 1, image, null, 12);
            default: return new TextBubbleMessage(8, "message body", out, "12:34", 1);
        }
    }
    private void draw(TextBubbleCell cell, int width) {
        cell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cell.layout(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight());
        check(cell.getHeight() > 0, "measured height");
        Bitmap bitmap = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
        cell.draw(new Canvas(bitmap)); bitmap.recycle();
    }
    private RectF bounds(TextBubbleCell cell) { return new RectF((RectF) field(cell, "forwardTouchRect")); }
    private void touch(TextBubbleCell cell, RectF r, int action, long down) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, r.centerX(), r.centerY(), 0);
        cell.onTouchEvent(event); event.recycle();
    }
    private void tap(TextBubbleCell cell, RectF r) {
        long now = SystemClock.uptimeMillis(); touch(cell, r, MotionEvent.ACTION_DOWN, now); touch(cell, r, MotionEvent.ACTION_UP, now);
    }
    private void runChecks() {
        for (boolean out : new boolean[]{false, true}) for (int kind = 0; kind <= 7; kind++) {
            TextBubbleCell cell = cell(kind);
            final int normalOffset = AndroidUtilities.dp(8) + 2 * (int) Theme.chat_forwardNamePaint.getTextSize();
            host.addView(cell);
            cell.setDelegate(new TextBubbleCell.Delegate() {
                @Override public void onForwardPressed(TextBubbleCell c, TextBubbleMessage m, TextBubbleMessage.ForwardedFrom source) {
                    clicks++; clickedMessage = m; clickedSource = source;
                }
            });
            TextBubbleMessage m = message(kind, out);
            if (kind <= 5) {
                m.group = true;
                m.sender = new TextBubbleMessage.Sender(10, "Sender", null);
                m.replyTo = new TextBubbleMessage.ReplyTo(33, "Replied", "Reply text");
            }
            ColorDrawable avatar = new ColorDrawable(Color.RED);
            avatar.setBounds(2, 3, 24, 27); Rect original = new Rect(avatar.getBounds());
            TextBubbleMessage.ForwardedFrom source = new TextBubbleMessage.ForwardedFrom(123, "Original sender", avatar, 2);
            m.forwardedFrom = source;
            cell.setMessage(m, false, false);
            for (int width : new int[]{AndroidUtilities.dp(240), AndroidUtilities.dp(400)}) {
                draw(cell, width);
                RectF rect = bounds(cell);
                check(!rect.isEmpty() && rect.left >= 0 && rect.right <= width && rect.top >= 0 && rect.bottom <= cell.getHeight(), "forward in bounds kind=" + kind + " out=" + out);
                check(avatar.getBounds().equals(original), "host avatar bounds restored");
                int colorKey = kind == 6 || kind == 7 ? Theme.key_chat_stickerReplyNameText
                        : out ? Theme.key_chat_outForwardedNameText : Theme.keys_avatar_nameInMessage[2];
                check(Theme.chat_forwardNamePaint.getColor() == Theme.getColor(colorKey), "forward theme color kind=" + kind);
                check(offset(cell) == (kind == 6 || kind == 7 ? 0 : normalOffset), "upstream header offset kind=" + kind);
                int before = clicks; tap(cell, rect);
                check(clicks == before + 1 && clickedMessage == m && clickedSource == source, "forward click kind=" + kind);
                long now = SystemClock.uptimeMillis(); touch(cell, rect, MotionEvent.ACTION_DOWN, now); touch(cell, rect, MotionEvent.ACTION_CANCEL, now);
                check(clicks == before + 1, "cancel does not click");
            }
            host.removeView(cell);
        }
        rebindAndEmoji();
        sideReplies();
        flatStickerAndEmojiReply();
    }
    private void flatStickerAndEmojiReply() {
        for (boolean out : new boolean[]{false, true}) {
            StickerBubbleCell cell = new StickerBubbleCell(activity);
            host.addView(cell);
            TextBubbleMessage m = TextBubbleMessage.sticker(100, out, "12:34", 1,
                    new Sticker(100, Sticker.Format.WEBP, new File(activity.getCacheDir(), "absent-flat-sticker"),
                            new ColorDrawable(Color.BLUE), 800, 80, ""));
            m.group = true;
            m.sender = new TextBubbleMessage.Sender(12, "Group sender", null);
            m.forwardedFrom = new TextBubbleMessage.ForwardedFrom(2, "Source");
            m.replyTo = new TextBubbleMessage.ReplyTo(42, "Reply author", "A longer reply preview");
            cell.setMessage(m, false, false);
            draw(cell, AndroidUtilities.dp(400));
            RectF reply = new RectF((RectF) field(cell, "replySelectorRect"));
            RectF media = new RectF(); cell.getPhotoRect(media);
            check(reply.bottom <= cell.getHeight() && reply.bottom > media.bottom, "flat sticker reserves full side header height");
            check(out ? reply.right <= media.left : reply.left >= media.right, "side reply does not cover flat sticker");
            check(Math.abs(media.width() / media.height() - 10) < .1f, "side header preserves sticker aspect ratio");
            host.removeView(cell);
        }
        TextBubbleCell cell = cell(0); host.addView(cell);
        TextBubbleMessage emoji = new TextBubbleMessage(101, "😀", true, "12:34", 1);
        emoji.forwardedFrom = new TextBubbleMessage.ForwardedFrom(0, "A");
        emoji.replyTo = new TextBubbleMessage.ReplyTo(42, "A long reply author", "Reply text much wider than the forwarding source");
        cell.setMessage(emoji, false, false); draw(cell, AndroidUtilities.dp(240));
        RectF reply = new RectF((RectF) field(cell, "replySelectorRect"));
        check((Boolean) field(cell, "emojiOnlyMode"), "forward plus reply retains large emoji");
        check(!reply.isEmpty() && reply.left >= 0 && reply.right <= cell.getWidth()
                && reply.top >= bounds(cell).bottom && reply.bottom <= cell.getHeight(), "emoji reply fits below short forwarding source");
        host.removeView(cell);
    }
    private void sideReplies() {
        for (int kind : new int[]{6, 7}) for (boolean out : new boolean[]{false, true}) {
            TextBubbleCell cell = cell(kind);
            host.addView(cell);
            TextBubbleMessage m = message(kind, out);
            m.group = true;
            m.sender = new TextBubbleMessage.Sender(12, "Group sender", null);
            m.forwardedFrom = new TextBubbleMessage.ForwardedFrom(3, "A long original channel name that must fit beside the media");
            m.replyTo = new TextBubbleMessage.ReplyTo(42, "Reply author", "A reply preview beside the media");
            int[] replyClicks = {0};
            cell.setDelegate(new TextBubbleCell.Delegate() {
                @Override public void onReplyPressed(TextBubbleCell c, TextBubbleMessage item, TextBubbleMessage.ReplyTo reply) {
                    check(reply.messageId == 42, "side reply identity"); replyClicks[0]++;
                }
                @Override public void onForwardPressed(TextBubbleCell c, TextBubbleMessage item, TextBubbleMessage.ForwardedFrom source) { clicks++; }
            });
            cell.setMessage(m, false, false);
            for (int width : new int[]{AndroidUtilities.dp(240), AndroidUtilities.dp(400)}) {
                draw(cell, width);
                RectF forward = bounds(cell);
                RectF reply = new RectF((RectF) field(cell, "replySelectorRect"));
                check(!reply.isEmpty() && reply.left >= 0 && reply.right <= width
                        && reply.top >= forward.bottom && reply.bottom <= cell.getHeight(),
                        "side reply bounds kind=" + kind + " out=" + out + " forward=" + forward + " reply=" + reply);
                check(forward.left >= 0 && forward.right <= width, "side source fits alongside group media");
                int before = replyClicks[0]; tap(cell, reply);
                check(replyClicks[0] == before + 1, "side reply click reaches host");
                int forwardBefore = clicks; tap(cell, forward);
                // At 240dp the original side-width formula can leave less than one
                // ellipsis beside a group sticker. Invisible text must not be clickable;
                // the 400dp case below still requires a visible, actionable source.
                if (forward.isEmpty()) {
                    check(width == AndroidUtilities.dp(240) && !out, "unexpected missing forwarding header");
                    check(clicks == forwardBefore && replyClicks[0] == before + 1, "empty narrow header must not steal taps");
                } else check(clicks == forwardBefore + 1 && replyClicks[0] == before + 1,
                        "forward/reply click isolation kind=" + kind + " out=" + out + " width=" + width
                                + " forward=" + forward + " reply=" + reply + " clicks=" + clicks + "/" + forwardBefore
                                + " replies=" + replyClicks[0] + "/" + before);
            }
            host.removeView(cell);
        }
    }
    private void rebindAndEmoji() {
        TextBubbleCell cell = cell(0); host.addView(cell);
        cell.setDelegate(new TextBubbleCell.Delegate() {
            @Override public void onForwardPressed(TextBubbleCell c, TextBubbleMessage m, TextBubbleMessage.ForwardedFrom source) {
                clicks++; clickedMessage = m; clickedSource = source;
            }
        });
        TextBubbleMessage m = message(0, false);
        m.forwardedFrom = new TextBubbleMessage.ForwardedFrom(1, "Original");
        cell.setMessage(m, false, false); draw(cell, AndroidUtilities.dp(320));
        RectF old = bounds(cell); long now = SystemClock.uptimeMillis(); touch(cell, old, MotionEvent.ACTION_DOWN, now);
        TextBubbleMessage.ForwardedFrom anonymous = new TextBubbleMessage.ForwardedFrom(0, "Anonymous");
        m.forwardedFrom = anonymous; cell.setMessage(m, false, false); draw(cell, AndroidUtilities.dp(320));
        int before = clicks; touch(cell, old, MotionEvent.ACTION_UP, now);
        check(clicks == before, "rebind cancels pressed source");
        check(field(cell, "forwardAvatar") == null, "anonymous source has no avatar");
        tap(cell, bounds(cell)); check(clicks == before + 1 && clickedSource == anonymous, "anonymous callback");
        m.forwardedFrom = null; cell.setMessage(m, false, false); draw(cell, AndroidUtilities.dp(320));
        check(bounds(cell).isEmpty(), "clear on same message object");
        m.forwardedFrom = new TextBubbleMessage.ForwardedFrom(9, "Source");
        cell.setMessage(m, false, false); draw(cell, AndroidUtilities.dp(320));
        RectF b = bounds(cell); now = SystemClock.uptimeMillis(); touch(cell, b, MotionEvent.ACTION_DOWN, now);
        host.removeView(cell); before = clicks; touch(cell, b, MotionEvent.ACTION_UP, now);
        check(clicks == before, "detach cancels press");
        TextBubbleCell emoji = cell(0); host.addView(emoji);
        TextBubbleMessage e = new TextBubbleMessage(99, "😀", false, "12:34", 0);
        emoji.setMessage(e, false, false); draw(emoji, AndroidUtilities.dp(320)); int noForward = emoji.getHeight();
        e.forwardedFrom = new TextBubbleMessage.ForwardedFrom(8, "Emoji source");
        emoji.setMessage(e, false, false); draw(emoji, AndroidUtilities.dp(320));
        check(!bounds(emoji).isEmpty() && emoji.getHeight() > noForward, "emoji head reserves separate vertical space");
        host.removeView(emoji);
    }
}
