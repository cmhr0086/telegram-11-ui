package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatUnreadCell;

import java.util.concurrent.atomic.AtomicReference;

/** Natural window layout and upstream ThemeDescription bindings on a reused unread row. */
public final class ChatUnreadDeviceChecks {
    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        FrameLayout[] host = new FrameLayout[1];
        CapturedUnread[] cell = new CapturedUnread[1];
        Palette palette = new Palette();
        try {
            main(instrumentation, () -> {
                host[0] = new FrameLayout(activity);
                cell[0] = new CapturedUnread(activity, palette);
                cell[0].setText("Unread messages");
                // Parent requests a taller row; upstream must still enforce 40dp.
                host[0].addView(cell[0], new FrameLayout.LayoutParams(AndroidUtilities.dp(320), AndroidUtilities.dp(100)));
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(host[0], new ViewGroup.LayoutParams(-1, -1));
            });
            awaitDraw(instrumentation, cell, 0);
            main(instrumentation, () -> {
                geometry(cell[0]);
                colors(cell[0], palette);
                check("Unread messages".contentEquals(cell[0].getTextView().getText()), "host text is displayed unchanged");
            });
            int[] previousDraw = {0};
            for (boolean dark : new boolean[]{true, false}) {
                main(instrumentation, () -> {
                    previousDraw[0] = cell[0].draws;
                    palette.dark = dark;
                    cell[0].setText(dark ? "7 unread" : "Unread messages");
                    // Same fields/flags used by upstream ChatActivity; no new cell API.
                    recolor(host[0], ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE,
                            "backgroundLayout", Theme.key_chat_unreadMessagesStartBackground, palette);
                    recolor(host[0], ThemeDescription.FLAG_IMAGECOLOR,
                            "imageView", Theme.key_chat_unreadMessagesStartArrowIcon, palette);
                    recolor(host[0], ThemeDescription.FLAG_TEXTCOLOR,
                            "textView", Theme.key_chat_unreadMessagesStartText, palette);
                });
                awaitDraw(instrumentation, cell, previousDraw[0]);
                main(instrumentation, () -> {
                    geometry(cell[0]);
                    colors(cell[0], palette);
                    check((dark ? "7 unread" : "Unread messages").contentEquals(cell[0].getTextView().getText()),
                            "same row accepts host text during day/night reuse");
                });
            }
            main(instrumentation, () -> {
                previousDraw[0] = cell[0].draws;
                palette.dark = true;
                cell[0].updateColors();
            });
            awaitDraw(instrumentation, cell, previousDraw[0]);
            main(instrumentation, () -> {
                geometry(cell[0]);
                colors(cell[0], palette);
            });
        } finally {
            main(instrumentation, () -> {
                if (host[0] != null && host[0].getParent() instanceof ViewGroup)
                    ((ViewGroup) host[0].getParent()).removeView(host[0]);
                if (cell[0] != null) cell[0].releaseCapture();
            });
        }
    }

    private static void recolor(FrameLayout host, int flags, String field, int key, Palette palette) {
        new ThemeDescription(host, flags, new Class[]{ChatUnreadCell.class}, new String[]{field},
                null, null, null, key).setColor(palette.getColor(key), false, false);
    }

    private static void geometry(ChatUnreadCell cell) {
        FrameLayout background = cell.getBackgroundLayout();
        TextView text = cell.getTextView();
        ImageView arrow = cell.getImageView();
        check(cell.isShown() && cell.isAttachedToWindow(), "row is naturally attached and visible");
        check(cell.getHeight() == AndroidUtilities.dp(40), "row retains upstream 40dp height");
        check(background.getTop() == AndroidUtilities.dp(7) && background.getHeight() == AndroidUtilities.dp(27)
                && background.getWidth() == cell.getWidth(), "divider is full width, 27dp high, inset 7dp from top");
        check(Math.abs(text.getLeft() + text.getWidth() / 2f - cell.getWidth() / 2f) <= 1
                && Math.abs(text.getTop() + text.getHeight() / 2f - cell.getHeight() / 2f) <= 1,
                "unread text is centered in the 40dp row");
        check(Math.abs(text.getTextSize() - AndroidUtilities.dpf2(14)) <= 1
                && text.getPaddingBottom() == AndroidUtilities.dp(1), "upstream 14dp text and 1dp bottom padding");
        check(background.getWidth() - arrow.getRight() == AndroidUtilities.dp(10)
                && Math.abs(arrow.getTop() + arrow.getHeight() / 2f - background.getHeight() / 2f) <= 1,
                "arrow retains right 10dp inset and vertical centering");
        check(arrow.getPaddingTop() == AndroidUtilities.dp(2), "arrow retains 2dp top padding");
    }

    private static void colors(CapturedUnread cell, Palette palette) {
        check(cell.getTextView().getCurrentTextColor() == palette.getColor(Theme.key_chat_unreadMessagesStartText),
                "text color follows provider/theme binding");
        int background = cell.capture.getPixel(AndroidUtilities.dp(5),
                cell.getBackgroundLayout().getTop() + cell.getBackgroundLayout().getHeight() / 2);
        check(dominant(background, palette.dark ? 2 : 1), "divider pixels follow provider/theme binding");
        ImageView arrow = cell.getImageView();
        int left = arrow.getLeft(), top = cell.getBackgroundLayout().getTop() + arrow.getTop();
        boolean foundArrow = false;
        for (int y = top; y < top + arrow.getHeight() && !foundArrow; y++) {
            for (int x = left; x < left + arrow.getWidth(); x++) {
                if (dominant(cell.capture.getPixel(x, y), palette.dark ? 1 : 0)) { foundArrow = true; break; }
            }
        }
        check(foundArrow, "arrow pixels follow provider/theme binding");
    }

    private static boolean dominant(int color, int channel) {
        int[] rgb = {Color.red(color), Color.green(color), Color.blue(color)};
        return rgb[channel] > 40 && rgb[channel] > rgb[(channel + 1) % 3] + 20
                && rgb[channel] > rgb[(channel + 2) % 3] + 20;
    }

    private static void awaitDraw(Instrumentation instrumentation, CapturedUnread[] cell, int previous) {
        for (int i = 0; i < 50; i++) {
            boolean[] ready = {false};
            main(instrumentation, () -> ready[0] = cell[0].draws > previous);
            if (ready[0]) return;
            SystemClock.sleep(40);
        }
        throw new AssertionError("unread row did not draw naturally");
    }

    private static void main(Instrumentation instrumentation, Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError("ChatUnreadCell device check", failure.get());
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Palette implements Theme.ResourcesProvider {
        boolean dark;
        @Override public int getColor(int key) {
            if (key == Theme.key_chat_unreadMessagesStartBackground) return dark ? Color.BLUE : Color.GREEN;
            if (key == Theme.key_chat_unreadMessagesStartArrowIcon) return dark ? Color.GREEN : Color.RED;
            if (key == Theme.key_chat_unreadMessagesStartText) return dark ? Color.YELLOW : Color.BLUE;
            return Theme.getColor(key);
        }
        @Override public boolean isDark() { return dark; }
    }

    private static final class CapturedUnread extends ChatUnreadCell {
        Bitmap capture;
        int draws;
        CapturedUnread(Activity activity, Theme.ResourcesProvider provider) { super(activity, provider); }
        @Override protected void dispatchDraw(Canvas canvas) {
            if (capture == null || capture.getWidth() != getWidth() || capture.getHeight() != getHeight()) {
                releaseCapture();
                capture = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            }
            capture.eraseColor(Color.TRANSPARENT);
            super.dispatchDraw(new Canvas(capture));
            canvas.drawBitmap(capture, 0, 0, null);
            draws++;
        }
        void releaseCapture() { if (capture != null) capture.recycle(); capture = null; }
    }
}
