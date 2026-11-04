package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Cells.MessageGrouping;
import org.telegram.ui.Cells.PhotoBubbleCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Cells.RoundVideoBubbleCell;
import org.telegram.ui.Cells.StickerBubbleCell;
import org.telegram.ui.Components.Sticker;
import org.telegram.ui.Cells.ChatAvatarDecoration;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.tgui.recyclerview.LinearLayoutManager;

import java.util.concurrent.atomic.AtomicReference;

/** Layout and grouping checks run by the existing instrumentation worker. */
public final class GroupMessageDeviceChecks {
    public static void run(Instrumentation instrumentation, Activity activity) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { checkGrouping(); checkLayout(activity); checkStickyAvatar(activity); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("group message checks", failure.get());
    }
    private static TextBubbleMessage message(long id, String text) {
        TextBubbleMessage item = new TextBubbleMessage(id, text, false, "12:00", TextBubbleMessage.STATE_NONE);
        item.sender = new TextBubbleMessage.Sender(21, "Alex", null, 1);
        item.group = true; item.date = 1700000000L; return item;
    }
    private static void checkGrouping() {
        TextBubbleMessage a = message(1, "first"), b = message(2, "second");
        b.date += 300; check(MessageGrouping.canMerge(a, b), "five-minute boundary");
        b.date++; check(!MessageGrouping.canMerge(a, b), "outside time window");
        b.date = a.date; b.sender = new TextBubbleMessage.Sender(22, "Other", null, 2);
        check(!MessageGrouping.canMerge(a, b), "different sender");
        b.sender = null; check(!MessageGrouping.canMerge(a, b), "unknown sender");
        b.sender = a.sender; b.date = 0; check(!MessageGrouping.canMerge(a, b), "unknown timestamp");
        b.date = a.date; b.isOut = true; check(!MessageGrouping.canMerge(a, b), "different direction");
    }
    private static void layout(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, width, view.getMeasuredHeight());
    }
    private static void draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), Math.max(1, view.getHeight()), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap)); bitmap.recycle();
    }
    private static void checkLayout(Activity context) {
        int width = AndroidUtilities.dp(320);
        TextBubbleMessage grouped = message(3, "Hello, group members");
        TextBubbleCell cell = new TextBubbleCell(context);
        cell.setMessage(grouped, false, false); layout(cell, width); draw(cell);
        int groupHeight = cell.getMeasuredHeight();
        String firstState = layoutState(cell);
        cell.setMessage(grouped, true, false); layout(cell, width); draw(cell);
        check(groupHeight > cell.getMeasuredHeight(), "first message reserves author height: first=" + firstState + ", pinned=" + layoutState(cell));
        TextBubbleMessage direct = message(4, "Hello, group members"); direct.group = false;
        cell.setMessage(direct, false, false); layout(cell, width); draw(cell);
        check(groupHeight > cell.getMeasuredHeight(), "direct message has no author header");
        TextBubbleMessage shortMessage = message(6, "ok");
        shortMessage.sender = new TextBubbleMessage.Sender(21, "Alexandra Montgomery", null, 1);
        cell.setMessage(shortMessage, false, false); layout(cell, width); draw(cell);
        int wideAuthorBubble = backgroundWidth(cell);
        shortMessage.sender = new TextBubbleMessage.Sender(21, "Al", null, 1);
        cell.setMessage(shortMessage, false, false); layout(cell, width); draw(cell);
        check(wideAuthorBubble > backgroundWidth(cell), "long author widens short text bubble");
        shortMessage.group = false;
        cell.setMessage(shortMessage, false, false); layout(cell, width); draw(cell);
        check(cell.getMeasuredHeight() < groupHeight, "mutating same message group to direct rebuilds layout");
        ColorDrawable hostAvatar = new ColorDrawable(Color.BLUE);
        hostAvatar.setBounds(2, 3, 9, 11);
        shortMessage.group = true;
        shortMessage.sender = new TextBubbleMessage.Sender(21, "Al", hostAvatar, 1);
        cell.setMessage(shortMessage, false, false); layout(cell, width); draw(cell);
        check(hostAvatar.getBounds().equals(new android.graphics.Rect(2, 3, 9, 11)), "host avatar bounds restored");
        shortMessage.sender = new TextBubbleMessage.Sender(1, "Al", null);
        cell.setMessage(shortMessage, false, false); layout(cell, width); draw(cell);
        int color1 = Theme.chat_namePaint.getColor();
        check(color1 == Theme.getColor(AvatarDrawable.getNameColorNameForId(1)), "sender 1 default name color");
        shortMessage.sender = new TextBubbleMessage.Sender(2, "Al", null);
        cell.setMessage(shortMessage, false, false); layout(cell, width); draw(cell);
        int color2 = Theme.chat_namePaint.getColor();
        check(color2 == Theme.getColor(AvatarDrawable.getNameColorNameForId(2)) && color1 != color2, "sender 2 default name color");
        PhotoBubbleCell photo = new PhotoBubbleCell(context);
        TextBubbleMessage photoMessage = TextBubbleMessage.photo(5, false, "12:00", 0, new ColorDrawable(Color.RED), null);
        photoMessage.photoWidth = photoMessage.photoHeight = 100;
        photo.setMessage(photoMessage, false, false); layout(photo, width); draw(photo);
        RectF before = new RectF(); photo.getPhotoRect(before);
        photoMessage.group = true; photoMessage.sender = grouped.sender;
        photo.setMessage(photoMessage, false, false); layout(photo, width); draw(photo);
        RectF after = new RectF(); photo.getPhotoRect(after);
        check(Math.abs((after.left - before.left) - AndroidUtilities.dp(48)) <= 1, "photo and hit rect use avatar column");
        check(Math.abs(after.top - before.top - (AndroidUtilities.dp(5) + (int) Theme.chat_namePaint.getTextSize() + AndroidUtilities.dp(7))) <= 1, "photo author header and seven dp spacing");
        int radius = AndroidUtilities.dp(SharedConfig.bubbleRadius > 2 ? SharedConfig.bubbleRadius - 2 : SharedConfig.bubbleRadius);
        check(photo.getPhotoRadii()[0] == Math.min(AndroidUtilities.dp(4), radius) && photo.getPhotoRadii()[2] == Math.min(AndroidUtilities.dp(4), radius), "author rounds both photo top corners");
        try {
            java.lang.reflect.Field background = TextBubbleCell.class.getDeclaredField("currentBackgroundDrawable"); background.setAccessible(true);
            android.graphics.drawable.Drawable drawable = (android.graphics.drawable.Drawable) background.get(photo);
            java.lang.reflect.Method left = TextBubbleCell.class.getDeclaredMethod("authorLeft", boolean.class); left.setAccessible(true);
            check((int) left.invoke(photo, true) == drawable.getBounds().left + AndroidUtilities.dp(11), "media author left padding");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        RoundVideoBubbleCell round = new RoundVideoBubbleCell(context);
        TextBubbleMessage roundMessage = TextBubbleMessage.roundVideo(7, false, "12:00", 0, new ColorDrawable(Color.RED), null, 5);
        roundMessage.group = true; roundMessage.sender = grouped.sender;
        round.setMessage(roundMessage, false, false); layout(round, width); draw(round);
        try {
            java.lang.reflect.Field side = TextBubbleCell.class.getDeclaredField("sideDrawLayout"); side.setAccessible(true);
            check(side.get(round) != null, "round video draws side author");
            RectF rect = new RectF(); round.getVideoRect(rect);
            check(rect.left >= AndroidUtilities.dp(48), "round video preserves avatar column");
            int sideSize = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : Math.min(width, AndroidUtilities.displaySize.y);
            check(Math.abs(rect.width() - (int) (sideSize * .6f)) <= 1, "group avatar does not shrink upstream round diameter");
            StickerBubbleCell sticker = new StickerBubbleCell(context);
            Sticker sample = new Sticker(88, Sticker.Format.WEBP, new java.io.File(context.getCacheDir(), "missing-sticker.webp"), new ColorDrawable(Color.RED), 100, 100, "");
            TextBubbleMessage stickerMessage = TextBubbleMessage.sticker(8, false, "12:00", 0, sample);
            stickerMessage.group = true; stickerMessage.sender = grouped.sender;
            sticker.setMessage(stickerMessage, false, false); layout(sticker, width); draw(sticker);
            check(side.get(sticker) != null, "sticker draws side author");
            int stickerWidth = AndroidUtilities.isTablet() ? (int) (AndroidUtilities.getMinTabletSide() * .4f)
                    : (int) (Math.min(width, AndroidUtilities.displaySize.y) * .5f);
            check(backgroundWidth(sticker) == stickerWidth + AndroidUtilities.dp(12), "group avatar does not shrink upstream sticker size");
            java.lang.reflect.Method logicalRight = TextBubbleCell.class.getDeclaredMethod("sideAuthorBackgroundRight", boolean.class);
            logicalRight.setAccessible(true);
            check((int) logicalRight.invoke(sticker, true) == AndroidUtilities.dp(48 + 9) + backgroundWidth(sticker), "sticker name uses upstream background edge");
            check((int) logicalRight.invoke(round, true) == AndroidUtilities.dp(48 + 9) + backgroundWidth(round), "round video name uses upstream background edge");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void checkStickyAvatar(Activity activity) {
        FrameLayout host = new FrameLayout(activity);
        ((ViewGroup) activity.findViewById(android.R.id.content)).addView(host, new ViewGroup.LayoutParams(-1, -1));
        try {
            RecyclerListView list = new RecyclerListView(activity);
            LinearLayoutManager manager = new LinearLayoutManager(activity);
            list.setLayoutManager(manager);
            ChatAvatarDecoration decoration = new ChatAvatarDecoration();
            list.addItemDecoration(decoration);
            ColorDrawable avatar = new ColorDrawable(Color.BLUE);
            avatar.setBounds(1, 2, 7, 9);
            TextBubbleMessage.Sender sender = new TextBubbleMessage.Sender(55, "Group member", avatar);
            list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @Override public int getItemCount() { return 12; }
                @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                    TextBubbleCell cell = new TextBubbleCell(parent.getContext());
                    cell.setAvatarDrawnByList(true);
                    return new RecyclerListView.Holder(cell);
                }
                @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    TextBubbleMessage item = message(position + 100, "Group message " + position);
                    item.sender = sender;
                    ((TextBubbleCell) holder.itemView).setMessage(item, position > 0, position < 11);
                }
            });
            host.addView(list, new FrameLayout.LayoutParams(AndroidUtilities.dp(320), AndroidUtilities.dp(200)));
            list.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(320), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(200), View.MeasureSpec.EXACTLY));
            list.layout(0, 0, AndroidUtilities.dp(320), AndroidUtilities.dp(200));
            manager.scrollToPositionWithOffset(2, -AndroidUtilities.dp(10));
            list.layout(0, 0, AndroidUtilities.dp(320), AndroidUtilities.dp(200));
            Bitmap bitmap = Bitmap.createBitmap(list.getWidth(), list.getHeight(), Bitmap.Config.ARGB_8888);
            list.draw(new Canvas(bitmap));
            int color = bitmap.getPixel(AndroidUtilities.dp(27), list.getHeight() - AndroidUtilities.dp(23));
            bitmap.recycle();
            check(Color.blue(color) > 200 && Color.red(color) < 30, "long group avatar sticks to viewport bottom");
            check(avatar.getBounds().equals(new android.graphics.Rect(1, 2, 7, 9)), "sticky avatar restores host bounds");
            list.setClipToPadding(false);
            list.setPadding(0, AndroidUtilities.dp(20), 0, AndroidUtilities.dp(15));
            list.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(320), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(200), View.MeasureSpec.EXACTLY));
            list.layout(0, 0, AndroidUtilities.dp(320), AndroidUtilities.dp(200));
            for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).setAlpha(.5f);
            Bitmap overlay = Bitmap.createBitmap(list.getWidth(), list.getHeight(), Bitmap.Config.ARGB_8888);
            decoration.onDrawOver(new Canvas(overlay), list, new RecyclerView.State());
            int visiblePixel = overlay.getPixel(AndroidUtilities.dp(27), list.getHeight() - AndroidUtilities.dp(15 + 21));
            int paddingPixel = overlay.getPixel(AndroidUtilities.dp(27), list.getHeight() - AndroidUtilities.dp(7));
            overlay.recycle();
            check(Color.alpha(visiblePixel) >= 120 && Color.alpha(visiblePixel) <= 136, "avatar follows cell alpha");
            check(Color.alpha(paddingPixel) == 0, "avatar stays above bottom padding when clipToPadding false");
        } finally { ((ViewGroup) host.getParent()).removeView(host); }
    }
    private static int backgroundWidth(TextBubbleCell cell) {
        try {
            java.lang.reflect.Field field = TextBubbleCell.class.getDeclaredField("backgroundWidth");
            field.setAccessible(true); return field.getInt(cell);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static String layoutState(TextBubbleCell cell) {
        try {
            String[] names = {"authorHeight", "authorLayout", "emojiOnlyMode", "totalHeight", "layoutForWidth", "pinnedTop", "pinnedBottom"};
            StringBuilder result = new StringBuilder("height=").append(cell.getMeasuredHeight());
            for (String name : names) {
                java.lang.reflect.Field field = TextBubbleCell.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(cell);
                result.append(' ').append(name).append('=').append(name.equals("authorLayout") ? value != null : value);
            }
            return result.toString();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private GroupMessageDeviceChecks() { }
}
