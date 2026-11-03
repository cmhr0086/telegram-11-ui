package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.AlbumBubbleCell;
import org.telegram.ui.Cells.GroupedMediaLayout;
import org.telegram.ui.Cells.PhotoBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded device checks; call on the instrumentation worker thread. */
public final class AlbumDeviceChecks {
    private static final class CountingAlbum extends AlbumBubbleCell {
        int timeDraws;
        CountingAlbum(Activity activity) { super(activity); }
        @Override protected void drawTimeAndStatus(Canvas canvas) { timeDraws++; super.drawTimeAndStatus(canvas); }
        @Override protected void drawTimePill(Canvas canvas, int right, int bottom, int radius) {
            timeDraws++; super.drawTimePill(canvas, right, bottom, radius);
        }
    }
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;

    private AlbumDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation;
        this.activity = activity;
    }

    public static void run(Instrumentation instrumentation, Activity activity) throws Exception {
        AlbumDeviceChecks test = new AlbumDeviceChecks(instrumentation, activity);
        try {
            test.main(() -> {
                test.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(test.host,
                        new ViewGroup.LayoutParams(-1, -1));
                test.modelChecks();
                test.viewChecks();
                test.photoRegression();
            });
        } finally {
            test.main(() -> {
                if (test.host != null && test.host.getParent() instanceof ViewGroup) {
                    ((ViewGroup) test.host.getParent()).removeView(test.host);
                }
            });
        }
    }

    private void main(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { action.run(); } catch (Throwable t) { failure.set(t); }
        });
        if (failure.get() != null) throw new AssertionError("album device check", failure.get());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static List<TextBubbleMessage> photos(int count, int pattern) {
        ArrayList<TextBubbleMessage> result = new ArrayList<>();
        int[][] sizes = {{320, 800}, {1200, 650}, {600, 600}, {900, 600}, {3000, 500}};
        for (int i = 0; i < count; i++) {
            int[] size = sizes[(i + pattern) % sizes.length];
            TextBubbleMessage item = TextBubbleMessage.photo(100 + i, false, "12:34",
                    TextBubbleMessage.STATE_SENT, new ColorDrawable(Color.rgb(40 + 17 * i, 90, 150)), null);
            item.photoWidth = size[0];
            item.photoHeight = size[1];
            result.add(item);
        }
        return result;
    }

    private void modelChecks() {
        List<TextBubbleMessage> duplicates = photos(2, 0);
        duplicates.set(1, duplicates.get(0));
        boolean rejected = false;
        try { TextBubbleMessage.album(1, false, "12:34", 0, duplicates, null); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "duplicate member references must be rejected");
        for (boolean out : new boolean[]{false, true}) {
            for (int count : new int[]{2, 3, 4, 5, 10}) {
                for (int pattern = 0; pattern < 5; pattern++) {
                    GroupedMediaLayout layout = new GroupedMediaLayout();
                    layout.calculate(photos(count, pattern), out);
                    check(layout.positions.size() == count, "position count " + count);
                    int top = 0, bottom = 0, left = 0, right = 0, last = 0;
                    for (GroupedMediaLayout.Position p : layout.positions) {
                        check(p.pw > 0 && p.spanSize > 0 && p.ph > 0 && Float.isFinite(p.ph)
                                && Float.isFinite(p.aspectRatio) && p.aspectRatio > 0,
                                "finite positive model dimensions");
                        check(p.minX <= p.maxX && p.minY <= p.maxY, "ordered model coordinates");
                        if ((p.flags & GroupedMediaLayout.TOP) != 0) top++;
                        if ((p.flags & GroupedMediaLayout.BOTTOM) != 0) bottom++;
                        if ((p.flags & GroupedMediaLayout.LEFT) != 0) left++;
                        if ((p.flags & GroupedMediaLayout.RIGHT) != 0) right++;
                        if (p.last) last++;
                        if (p.siblingHeights != null) for (float h : p.siblingHeights)
                            check(Float.isFinite(h) && h > 0, "positive sibling height");
                    }
                    check(top > 0 && bottom > 0 && left > 0 && right > 0 && last == 1,
                            "outer flags and one last position");
                }
            }
            GroupedMediaLayout sibling = new GroupedMediaLayout();
            sibling.calculate(photos(3, 0), out);
            check(sibling.hasSibling && sibling.positions.get(0).siblingHeights != null,
                    "portrait first item exercises sibling layout");
        }
    }

    private void viewChecks() {
        for (boolean out : new boolean[]{false, true}) {
            for (int widthDp : new int[]{240, 400}) {
                for (int count = 2; count <= 10; count++) {
                    List<TextBubbleMessage> items = photos(count, count % 5);
                    TextBubbleMessage album = TextBubbleMessage.album(42, out, "12:34",
                            TextBubbleMessage.STATE_SENT, items, "A caption for the album");
                    album.replyTo = new TextBubbleMessage.ReplyTo(7, "Author", "Reply preview");
                    album.reactions.add(new TextBubbleMessage.Reaction("👍", 2, false));
                    CountingAlbum cell = new CountingAlbum(activity);
                    host.addView(cell, new FrameLayout.LayoutParams(AndroidUtilities.dp(widthDp), -2));
                    cell.setMessage(album, false, false);
                    draw(cell, AndroidUtilities.dp(widthDp));
                    check(cell.timeDraws == 1, "one time/status for the group");
                    RectF[] rects = new RectF[count];
                    for (int i = 0; i < count; i++) {
                        RectF rect = rects[i] = new RectF();
                        cell.getTileRect(i, rect);
                        check(!rect.isEmpty() && finite(rect), "nonempty finite tile " + count + "/" + i);
                        check(rect.left >= -1 && rect.top >= -1 && rect.right <= cell.getWidth() + 1
                                && rect.bottom <= cell.getHeight() + 1, "tile inside cell " + count + "/" + i);
                        float[] radii = cell.getTileRadii(i);
                        check(radii != null && radii.length == 8, "tile radii");
                        for (float radius : radii) check(Float.isFinite(radius) && radius >= 0, "finite radius");
                        GroupedMediaLayout layout = new GroupedMediaLayout();
                        layout.calculate(items, out);
                        int flags = layout.positions.get(i).flags;
                        float inner = AndroidUtilities.dp(4);
                        if ((flags & (GroupedMediaLayout.TOP | GroupedMediaLayout.LEFT))
                                != (GroupedMediaLayout.TOP | GroupedMediaLayout.LEFT))
                            check(Math.abs(radii[0] - inner) < 1, "interior top-left radius");
                        if ((flags & (GroupedMediaLayout.BOTTOM | GroupedMediaLayout.RIGHT))
                                != (GroupedMediaLayout.BOTTOM | GroupedMediaLayout.RIGHT))
                            check(Math.abs(radii[4] - inner) < 1, "interior bottom-right radius");
                        for (int j = 0; j < i; j++) {
                            RectF overlap = new RectF(rect);
                            check(!overlap.intersect(rects[j]) || overlap.width() <= 1 || overlap.height() <= 1,
                                    "tile interiors overlap " + count + "/" + i + "/" + j);
                        }
                    }
                    final int[] clicked = {-1};
                    cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
                        @Override public void onMediaPressed(TextBubbleCell c, TextBubbleMessage m) {
                            throw new AssertionError("album must route an item callback");
                        }
                        @Override public void onAlbumMediaPressed(AlbumBubbleCell c, TextBubbleMessage m,
                                TextBubbleMessage item, int index) {
                            check(c == cell && m == album && item == items.get(index), "callback identity");
                            clicked[0] = index;
                        }
                    });
                    for (int i = 0; i < count; i++) {
                        clicked[0] = -1;
                        tap(cell, rects[i].centerX(), rects[i].centerY());
                        check(clicked[0] == i, "tile callback index " + i);
                    }
                    cell.setTileVisible(0, false);
                    clicked[0] = -1;
                    tap(cell, rects[0].centerX(), rects[0].centerY());
                    check(clicked[0] == -1, "hidden tile must not dispatch");
                    cell.setTileVisible(0, true);
                    host.removeView(cell);
                }
            }
        }
    }

    private void photoRegression() {
        TextBubbleMessage photo = photos(2, 0).get(0);
        PhotoBubbleCell cell = new PhotoBubbleCell(activity);
        host.addView(cell, new FrameLayout.LayoutParams(AndroidUtilities.dp(320), -2));
        cell.setMessage(photo, false, false);
        draw(cell, AndroidUtilities.dp(320));
        RectF rect = new RectF();
        cell.getPhotoRect(rect);
        check(!rect.isEmpty() && finite(rect), "single photo has visible geometry");
        final boolean[] clicked = {false};
        cell.setMediaDelegate((c, m) -> clicked[0] = c == cell && m == photo);
        tap(cell, rect.centerX(), rect.centerY());
        check(clicked[0], "single photo click regression");
        host.removeView(cell);
    }

    private static boolean finite(RectF r) {
        return Float.isFinite(r.left) && Float.isFinite(r.top) && Float.isFinite(r.right) && Float.isFinite(r.bottom);
    }

    private static void draw(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        check(view.getMeasuredHeight() > 0, "positive measured height");
        view.layout(0, 0, width, view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, view.getHeight(), Bitmap.Config.ARGB_8888);
        if (view instanceof CountingAlbum) ((CountingAlbum) view).timeDraws = 0;
        view.draw(new Canvas(bitmap));
        bitmap.recycle();
    }

    private static void tap(View view, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, x, y, 0);
        try { view.dispatchTouchEvent(down); view.dispatchTouchEvent(up); }
        finally { down.recycle(); up.recycle(); }
    }
}
