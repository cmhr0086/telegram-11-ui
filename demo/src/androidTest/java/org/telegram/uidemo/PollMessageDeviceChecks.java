package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PollBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded poll checks; call from the instrumentation worker thread. */
public final class PollMessageDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private int votes, results, optionPresses;
    private TextBubbleMessage.PollOption pressedOption;
    private TextBubbleMessage optionMessage;
    private TextBubbleMessage votedMessage;
    private List<String> selected;
    private PollBubbleCell animatedCell;
    private PollMessageDeviceChecks(Instrumentation instrumentation, Activity activity) { this.instrumentation = instrumentation; this.activity = activity; }
    public static void run(Instrumentation instrumentation, Activity activity) {
        PollMessageDeviceChecks t = new PollMessageDeviceChecks(instrumentation, activity);
        try {
            t.main(() -> {
                t.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(t.host, new ViewGroup.LayoutParams(-1, -1));
                t.checks();
                t.pendingSnapshotChecks();
                t.startAnimation();
            });
            for (int i = 0; i < 24; i++) {
                SystemClock.sleep(18);
                t.main(t::advanceAnimation);
            }
            t.main(t::finishAnimation);
        } finally {
            t.main(() -> { if (t.host != null && t.host.getParent() instanceof ViewGroup) ((ViewGroup) t.host.getParent()).removeView(t.host); });
        }
    }
    private void main(Runnable work) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { work.run(); } catch (Throwable e) { failure.set(e); } });
        if (failure.get() != null) throw new AssertionError("poll checks", failure.get());
    }
    private static void check(boolean condition, String text) { if (!condition) throw new AssertionError(text); }
    private static final class Palette implements Theme.ResourcesProvider {
        final boolean dark;
        Palette(boolean dark) { this.dark = dark; }
        @Override public int getColor(int key) { return Theme.getColor(key); }
        @Override public boolean isDark() { return dark; }
    }
    private TextBubbleMessage.Poll poll(boolean anonymous, boolean multiple, boolean closed, boolean pending, boolean chosen) {
        return new TextBubbleMessage.Poll("Question with emoji 😀 and a long title for layout", Arrays.asList(
                new TextBubbleMessage.PollOption("a", "First option", 1, chosen),
                new TextBubbleMessage.PollOption("b", "Second option with long text طويل جدا", 2, false),
                new TextBubbleMessage.PollOption("c", "Third option", 3, false)), 6, anonymous, multiple, closed, pending);
    }
    private void draw(PollBubbleCell cell, int width) {
        cell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cell.layout(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight());
        check(cell.getWidth() > 0 && cell.getHeight() > 0, "positive layout");
        Bitmap b = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888); cell.draw(new Canvas(b)); b.recycle();
    }
    private static RectF option(PollBubbleCell cell, int i) { RectF r = new RectF(); cell.getOptionRect(i, r); return r; }
    private static RectF action(PollBubbleCell cell) { RectF r = new RectF(); cell.getActionRect(r); return r; }
    private static void inside(RectF r, PollBubbleCell cell, String label) {
        check(!r.isEmpty() && r.left >= 0 && r.top >= 0 && r.right <= cell.getWidth() && r.bottom <= cell.getHeight(), label + " bounds");
    }
    private static void event(PollBubbleCell cell, RectF r, int action, long down) {
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, r.centerX(), r.centerY(), 0); cell.onTouchEvent(e); e.recycle();
    }
    private static void tap(PollBubbleCell cell, RectF r) { long down = SystemClock.uptimeMillis(); event(cell, r, MotionEvent.ACTION_DOWN, down); event(cell, r, MotionEvent.ACTION_UP, down); }
    private void checks() {
        for (boolean dark : new boolean[]{false, true}) for (boolean out : new boolean[]{false, true}) {
            PollBubbleCell cell = new PollBubbleCell(activity, new Palette(dark)); host.addView(cell);
            cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
                @Override public void onMediaPressed(TextBubbleCell c, TextBubbleMessage m) { }
                @Override public void onPollVote(TextBubbleCell c, TextBubbleMessage m, List<String> optionIds) {
                    votes++; votedMessage = m; selected = new ArrayList<>(optionIds);
                }
                @Override public void onPollResults(TextBubbleCell c, TextBubbleMessage m) { results++; }
                @Override public void onPollOptionPressed(TextBubbleCell c, TextBubbleMessage m, TextBubbleMessage.PollOption option) {
                    optionPresses++; optionMessage = m; pressedOption = option;
                }
            });
            TextBubbleMessage message = TextBubbleMessage.poll(1, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE, poll(true, false, false, false, false));
            message.group = true; message.sender = new TextBubbleMessage.Sender(2, "Sender", null);
            message.forwardedFrom = new TextBubbleMessage.ForwardedFrom(3, "Source");
            message.replyTo = new TextBubbleMessage.ReplyTo(4, "Reply", "Text");
            message.reactions.add(new TextBubbleMessage.Reaction("😀", 1, false));
            cell.setMessage(message, false, false);
            for (int dp : new int[]{240, 400}) {
                draw(cell, AndroidUtilities.dp(dp));
                RectF previous = null;
                for (int i = 0; i < 3; i++) {
                    RectF r = option(cell, i); inside(r, cell, "option " + i);
                    check(r.height() >= AndroidUtilities.dp(20), "option hit height");
                    if (previous != null) check(r.top >= previous.bottom, "option rows do not overlap");
                    previous = r;
                }
            }
            int before = votes; tap(cell, option(cell, 1));
            check(votes == before + 1 && votedMessage == message && selected.equals(Arrays.asList("b")), "single vote callback identity");
            tap(cell, option(cell, 1)); check(votes == before + 1, "rapid duplicate vote suppressed");
            message.poll = poll(true, false, false, true, false); cell.setMessage(message, false, false); draw(cell, AndroidUtilities.dp(400));
            before = votes; tap(cell, option(cell, 0)); check(votes == before, "pending disables vote");
            message.poll = poll(true, false, false, false, false); cell.setMessage(message, false, false); draw(cell, AndroidUtilities.dp(400));
            tap(cell, option(cell, 0)); check(votes == before + 1 && selected.equals(Arrays.asList("a")), "failed pending snapshot permits retry");
            message.poll = poll(true, false, true, false, false); cell.setMessage(message, false, false); draw(cell, AndroidUtilities.dp(400));
            before = votes; tap(cell, option(cell, 0)); check(votes == before, "closed disables vote");
            check(cell.getOptionPercent(0) == 17 && cell.getOptionPercent(1) == 33 && cell.getOptionPercent(2) == 50,
                    "upstream largest remainder percentages for 1,2,3 of 6");
            message.poll = poll(true, true, false, false, false); cell.setMessage(message, false, false); draw(cell, AndroidUtilities.dp(400));
            before = votes; tap(cell, option(cell, 0)); tap(cell, option(cell, 2)); check(votes == before, "multi select waits for submit");
            message.poll = new TextBubbleMessage.Poll("reordered", Arrays.asList(
                    new TextBubbleMessage.PollOption("c", "Third", 0, false), new TextBubbleMessage.PollOption("b", "Second", 0, false),
                    new TextBubbleMessage.PollOption("a", "First", 0, false)), 0, true, true, false, false);
            cell.setMessage(message, false, false); draw(cell, AndroidUtilities.dp(400));
            RectF submit = action(cell); inside(submit, cell, "submit"); tap(cell, submit);
            check(votes == before + 1 && votedMessage == message && selected.size() == 2 && selected.contains("a") && selected.contains("c"), "multi vote ids");
            TextBubbleMessage different = TextBubbleMessage.poll(123, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE,
                    poll(true, true, false, false, false));
            cell.setMessage(different, false, false); draw(cell, AndroidUtilities.dp(400));
            before = votes; tap(cell, action(cell)); check(votes == before, "new message id clears multi selection");
            message.poll = poll(false, false, false, false, true); cell.setMessage(message, false, false); draw(cell, AndroidUtilities.dp(400));
            int optionBefore = optionPresses;
            TextBubbleMessage.PollOption expectedOption = message.poll.options.get(1);
            tap(cell, option(cell, 1));
            check(optionPresses == optionBefore + 1 && optionMessage == message && pressedOption == expectedOption,
                    "public result option callback identity");
            RectF resultAction = action(cell); inside(resultAction, cell, "public results"); int resultsBefore = results; tap(cell, resultAction);
            check(results == resultsBefore + 1, "public results callback");
            message.poll = poll(true, false, false, false, false); cell.setMessage(message, false, false); draw(cell, AndroidUtilities.dp(400));
            RectF first = option(cell, 0); long down = SystemClock.uptimeMillis(); before = votes;
            event(cell, first, MotionEvent.ACTION_DOWN, down); event(cell, first, MotionEvent.ACTION_CANCEL, down); check(votes == before, "cancel does not vote");
            event(cell, first, MotionEvent.ACTION_DOWN, down);
            cell.setMessage(TextBubbleMessage.poll(9, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE, poll(true, false, false, false, false)), false, false);
            event(cell, first, MotionEvent.ACTION_UP, down); check(votes == before, "rebind cancels vote");
            cell.setCheckBoxVisible(true, false); draw(cell, AndroidUtilities.dp(400)); tap(cell, option(cell, 0));
            check(votes == before, "selection mode suppresses vote");
            cell.setCheckBoxVisible(false, false);
            if (out) {
                TextBubbleMessage sending = TextBubbleMessage.poll(11, true, "12:34", TextBubbleMessage.STATE_SENDING,
                        poll(true, false, false, false, false));
                cell.setMessage(sending, false, false); draw(cell, AndroidUtilities.dp(400));
                before = votes; tap(cell, option(cell, 0)); check(votes == before, "sending message cannot vote");
            }
            TextBubbleMessage.Poll equal = new TextBubbleMessage.Poll("Equal", Arrays.asList(
                    new TextBubbleMessage.PollOption("a", "A", 1, false), new TextBubbleMessage.PollOption("b", "B", 1, false),
                    new TextBubbleMessage.PollOption("c", "C", 1, false)), 3, true, false, true, false);
            cell.setMessage(TextBubbleMessage.poll(12, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE, equal), false, false);
            draw(cell, AndroidUtilities.dp(400));
            check(cell.getOptionPercent(0) == 33 && cell.getOptionPercent(1) == 33 && cell.getOptionPercent(2) == 33, "equal votes retain 33/33/33");
            TextBubbleMessage.Poll overlapping = new TextBubbleMessage.Poll("Multiple", Arrays.asList(
                    new TextBubbleMessage.PollOption("a", "A", 2, false), new TextBubbleMessage.PollOption("b", "B", 2, false),
                    new TextBubbleMessage.PollOption("c", "C", 1, false)), 2, true, true, true, false);
            cell.setMessage(TextBubbleMessage.poll(13, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE, overlapping), false, false);
            draw(cell, AndroidUtilities.dp(400));
            check(cell.getOptionPercent(0) == 100 && cell.getOptionPercent(1) == 100 && cell.getOptionPercent(2) == 50, "multiple percentages may exceed total 100");
            // Exercise zero-voter and result-animation transition without division by zero.
            TextBubbleMessage.Poll zero = new TextBubbleMessage.Poll("Empty", Arrays.asList(
                    new TextBubbleMessage.PollOption("x", "X", 0, false), new TextBubbleMessage.PollOption("y", "Y", 0, false)), 0, true, false, true, false);
            cell.setMessage(TextBubbleMessage.poll(10, out, "12:34", out ? TextBubbleMessage.STATE_SENT : TextBubbleMessage.STATE_NONE, zero), false, false);
            draw(cell, AndroidUtilities.dp(240));
            check(cell.getOptionPercent(0) == 0 && cell.getOptionPercent(1) == 0, "zero voter percentages");
            cell.setMessage(null, false, false); check(option(cell, 0).isEmpty(), "null binding clears option geometry");
            host.removeView(cell);
        }
    }
    private Object pollField(PollBubbleCell cell, String name) {
        try { java.lang.reflect.Field f = PollBubbleCell.class.getDeclaredField(name); f.setAccessible(true); return f.get(cell); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private void pendingSnapshotChecks() {
        List<TextBubbleMessage.PollOption> options = Arrays.asList(new TextBubbleMessage.PollOption("a", "A", 0, false),
                new TextBubbleMessage.PollOption("b", "B", 0, false), new TextBubbleMessage.PollOption("c", "C", 0, false));
        ArrayList<String> source = new ArrayList<>(Arrays.asList("b"));
        TextBubbleMessage.Poll single = new TextBubbleMessage.Poll("Single", options, 0, true, false, false, true, source);
        source.clear(); check(single.pendingOptionIds.equals(Arrays.asList("b")), "pending IDs defensively copied");
        boolean immutable = false;
        try { single.pendingOptionIds.add("a"); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "pending ID snapshot immutable");
        boolean invalid = false;
        try { new TextBubbleMessage.Poll("Invalid", options, 0, true, false, false, true, Arrays.asList("missing")); }
        catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "unknown pending ID rejected");
        PollBubbleCell singleCell = new PollBubbleCell(activity); host.addView(singleCell);
        singleCell.setMessage(TextBubbleMessage.poll(90, false, "12:34", TextBubbleMessage.STATE_NONE, single), false, false);
        draw(singleCell, AndroidUtilities.dp(320));
        check((Integer) pollField(singleCell, "pendingIndex") == 1, "new cell restores pending single option");
        host.removeView(singleCell);
        TextBubbleMessage.Poll multiple = new TextBubbleMessage.Poll("Multi", options, 0, true, true, false, true, Arrays.asList("c", "a"));
        PollBubbleCell multipleCell = new PollBubbleCell(activity); host.addView(multipleCell);
        multipleCell.setMessage(TextBubbleMessage.poll(91, false, "12:34", TextBubbleMessage.STATE_NONE, multiple), false, false);
        draw(multipleCell, AndroidUtilities.dp(320));
        Object checked = pollField(multipleCell, "checked");
        check(checked instanceof java.util.Set && ((java.util.Set<?>) checked).equals(new java.util.HashSet<>(Arrays.asList("a", "c"))),
                "new cell restores pending multi option identities");
        host.removeView(multipleCell);
    }
    private void startAnimation() {
        animatedCell = new PollBubbleCell(activity); host.addView(animatedCell);
        TextBubbleMessage m = TextBubbleMessage.poll(70, false, "12:34", TextBubbleMessage.STATE_NONE, poll(true, false, false, false, false));
        animatedCell.setMessage(m, false, false); draw(animatedCell, AndroidUtilities.dp(320));
        m.poll = poll(true, false, true, false, false);
        animatedCell.setMessage(m, false, false); draw(animatedCell, AndroidUtilities.dp(320));
        check(animationValue() < 1f, "result animation starts");
    }
    private float animationValue() {
        try { java.lang.reflect.Field f = PollBubbleCell.class.getDeclaredField("animation"); f.setAccessible(true); return f.getFloat(animatedCell); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private void advanceAnimation() { draw(animatedCell, AndroidUtilities.dp(320)); }
    private void finishAnimation() {
        check(animationValue() >= .99f, "result animation reaches end over real time");
        check(animatedCell.getOptionPercent(0) == 17 && animatedCell.getOptionPercent(2) == 50, "final result values");
        host.removeView(animatedCell);
    }

}
