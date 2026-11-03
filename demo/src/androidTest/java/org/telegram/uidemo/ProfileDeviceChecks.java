package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ProfileFragment;
import org.telegram.ui.TelegramActivity;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Profile checks in the real activity and navigation stack. */
public final class ProfileDeviceChecks {
    private final Instrumentation instrumentation;
    private final TelegramActivity activity;
    private ProfileFragment fragment;
    private ProfileFragment.Profile profile;
    private ProfileFragment.Member first, second;
    private ProfileFragment.Row firstRow, secondRow;
    private boolean night;
    private int clicks;
    private ProfileFragment.Row clicked;
    private ProfileDeviceChecks(Instrumentation instrumentation, Activity activity) {
        this.instrumentation = instrumentation; this.activity = (TelegramActivity) activity;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void main(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new AssertionError("profile checks", error.get());
    }
    private static Object field(Object owner, Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (ReflectiveOperationException e) { throw new AssertionError(name, e); }
    }
    private RecyclerListView list() { return (RecyclerListView)field(fragment, ProfileFragment.class, "listView"); }
    private CharSequence status() { return ((SimpleTextView[])field(fragment, ProfileFragment.class, "onlineTextView"))[1].getText(); }
    private UserCell member(int position) {
        RecyclerView.ViewHolder holder = list().findViewHolderForAdapterPosition(position);
        check(holder != null && holder.itemView instanceof UserCell, "visible member row " + position);
        return (UserCell)holder.itemView;
    }
    private String members(int count) { return activity.getResources().getQuantityString(R.plurals.tgui_profile_members, count, count); }
    private String online(int count) { return activity.getResources().getQuantityString(R.plurals.tgui_profile_online, count, count); }
    public static void run(Instrumentation instrumentation, Activity activity) {
        ProfileDeviceChecks t = new ProfileDeviceChecks(instrumentation, activity);
        try {
            t.main(() -> {
                t.profile = new ProfileFragment.Profile(10, "Group");
                t.profile.kind = ProfileFragment.Profile.Kind.GROUP; t.profile.memberCount = 4; t.profile.onlineCount = 99;
                t.first = new ProfileFragment.Member(1, "Alice"); t.first.status = "online"; t.first.online = true; t.first.role = "admin";
                t.second = new ProfileFragment.Member(2, "Bob"); t.second.status = "offline";
                t.firstRow = ProfileFragment.Row.member(11, t.first).setOnClickListener((fragment, row, view) -> { t.clicks++; t.clicked = row; });
                t.secondRow = ProfileFragment.Row.member(12, t.second); t.secondRow.memberAvatarPadding = 6;
                t.fragment = new ProfileFragment(t.profile);
                t.fragment.setResourceProvider(key -> key == Theme.key_windowBackgroundWhiteBlueText ? (t.night ? Color.CYAN : Color.BLUE) : Theme.getColor(key));
                t.fragment.setRows(Arrays.asList(ProfileFragment.Row.header(1, "Members"), t.firstRow, t.secondRow));
                check(t.activity.presentFragment(t.fragment), "present profile");
            });
            SystemClock.sleep(500);
            t.main(() -> {
                check(t.activity.getLastFragment() == t.fragment, "profile in real navigation");
                String expected = t.activity.getString(R.string.tgui_profile_members_online, t.members(4), t.online(4));
                check(expected.contentEquals(t.status()), "group online count clamped to members");
                t.profile.onlineCount = -5; t.fragment.updateProfile(); check(t.members(4).contentEquals(t.status()), "negative online clamped");
                t.profile.memberCount = -1; t.fragment.updateProfile(); check(t.members(0).contentEquals(t.status()), "negative members clamped");
                t.profile.kind = ProfileFragment.Profile.Kind.CHANNEL; t.profile.memberCount = 12; t.fragment.updateProfile();
                check(t.activity.getResources().getQuantityString(R.plurals.tgui_profile_subscribers, 12, 12).contentEquals(t.status()), "channel subscribers");
                t.profile.status = "host override"; t.fragment.updateProfile(); check("host override".contentEquals(t.status()), "host status overrides count");
                t.profile.kind = ProfileFragment.Profile.Kind.USER; t.profile.status = "last seen"; t.fragment.updateProfile();
                check("last seen".contentEquals(t.status()), "existing user profile status");
                t.profile.kind = ProfileFragment.Profile.Kind.GROUP; t.profile.status = null; t.fragment.updateProfile();
                t.list().scrollToPosition(0);
            });
            SystemClock.sleep(300);
            t.main(() -> {
                UserCell a = t.member(1), b = t.member(2);
                check(a.getMember() == t.first && b.getMember() == t.second, "member identities bound");
                check(a.getMeasuredHeight() == AndroidUtilities.dp(58) + 1 && a.needDivider && b.getMeasuredHeight() == AndroidUtilities.dp(58) && !b.needDivider, "member height and divider");
                check(a.avatarImageView.getMeasuredWidth() == AndroidUtilities.dp(46) && a.avatarImageView.getMeasuredHeight() == AndroidUtilities.dp(46), "46dp avatar");
                check(a.avatarImageView.getLeft() == AndroidUtilities.dp(16) && b.avatarImageView.getLeft() == AndroidUtilities.dp(13), "avatar padding 9 and 6");
                TextView role = (TextView)field(a, UserCell.class, "adminTextView");
                SimpleTextView name = (SimpleTextView)field(a, UserCell.class, "nameTextView");
                SimpleTextView status = (SimpleTextView)field(a, UserCell.class, "statusTextView");
                check(role.getVisibility() == View.VISIBLE && "admin".contentEquals(role.getText()), "role shown");
                check(status.getTextColor() == Color.BLUE, "day online color from provider");
                t.list().clickItem(a, 1); check(t.clicks == 1 && t.clicked == t.firstRow, "host receives correct row");
                t.night = true; a.updateColors(); check(status.getTextColor() == Color.CYAN, "night online color from provider");
                a.setData(t.second, false);
                check(role.getVisibility() == View.GONE && name.getPaddingLeft() == 0 && name.getPaddingRight() == 0, "rebind clears role and name padding");
                android.graphics.drawable.ColorDrawable shared = new android.graphics.drawable.ColorDrawable(Color.MAGENTA);
                android.graphics.Rect originalBounds = new android.graphics.Rect(3, 5, 37, 54);
                shared.setBounds(originalBounds);
                View hostCallback = new View(t.activity);
                shared.setCallback(hostCallback);
                t.first.photo = shared; t.second.photo = shared;
                a.setData(t.first, true); b.setData(t.second, false);
                android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(Math.max(1, a.getWidth()), Math.max(1, a.getHeight()), android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(image);
                a.draw(canvas); b.draw(canvas);
                check(originalBounds.equals(shared.getBounds()) && shared.getCallback() == hostCallback, "shared avatar preserves host bounds and callback after draws");
                a.setData(null, false);
                check(originalBounds.equals(shared.getBounds()) && shared.getCallback() == hostCallback, "unbinding one shared avatar preserves host callback");
                b.draw(canvas);
                check(originalBounds.equals(shared.getBounds()) && shared.getCallback() == hostCallback, "remaining shared avatar keeps host ownership");
                image.recycle();
                t.fragment.setRows(Arrays.asList(ProfileFragment.Row.bio(20,
                        "A long biography with several sentences that must wrap across multiple lines on the profile screen. "
                                + "The descriptive label belongs below every line of this biography and must remain readable.", "About")));
                t.list().scrollToPosition(0);
            });
            SystemClock.sleep(300);
            t.main(() -> {
                RecyclerView.ViewHolder holder = t.list().findViewHolderForAdapterPosition(0);
                check(holder != null && holder.itemView instanceof org.telegram.ui.Cells.TextDetailCell, "visible biography row");
                org.telegram.ui.Cells.TextDetailCell bio = (org.telegram.ui.Cells.TextDetailCell)holder.itemView;
                android.text.Layout textLayout = bio.textView.getLayout(), labelLayout = bio.valueTextView.getLayout();
                check(textLayout != null && labelLayout != null && textLayout.getLineCount() > 1, "biography wraps across lines");
                int textBottom = bio.textView.getTop() + bio.textView.getCompoundPaddingTop() + textLayout.getLineBottom(textLayout.getLineCount() - 1);
                int labelTop = bio.valueTextView.getTop() + bio.valueTextView.getCompoundPaddingTop() + labelLayout.getLineTop(0);
                int labelBottom = bio.valueTextView.getTop() + bio.valueTextView.getCompoundPaddingTop() + labelLayout.getLineBottom(labelLayout.getLineCount() - 1);
                check(labelTop >= textBottom, "biography text does not overlap label");
                check(labelBottom <= bio.getMeasuredHeight(), "biography cell contains label");
            });
        } finally {
            t.main(() -> { if (t.fragment != null && t.activity.getLastFragment() == t.fragment) t.activity.onBackPressed(); });
            SystemClock.sleep(400);
        }
    }
}
