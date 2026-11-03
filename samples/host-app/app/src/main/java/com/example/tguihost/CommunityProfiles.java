package com.example.tguihost;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.Toast;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ProfileFragment;
import org.telegram.ui.TelegramUI;
import java.util.ArrayList;

/** Host-owned models and callbacks; all profile UI comes from tgui. */
final class CommunityProfiles {
    static void open(BaseFragment from, boolean channel) {
        Context context = from.getParentActivity();
        ProfileFragment.Profile profile = new ProfileFragment.Profile(channel ? 700 : 701,
                context.getString(channel ? R.string.profile_channel_title : R.string.profile_group_title));
        profile.kind = channel ? ProfileFragment.Profile.Kind.CHANNEL : ProfileFragment.Profile.Kind.GROUP;
        profile.memberCount = channel ? 1240 : 3;
        profile.onlineCount = channel ? 0 : 2;
        profile.verified = channel;
        profile.photo = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff5c9ed5, channel ? 0xff9068d8 : 0xff70ba80});
        ProfileFragment fragment = new ProfileFragment(profile);
        fragment.addMenuItem(1, org.telegram.tgui.R.drawable.msg_theme, context.getString(R.string.profile_theme));
        fragment.addMenuItem(2, org.telegram.tgui.R.drawable.msg_groups,
                context.getString(channel ? R.string.profile_group_menu : R.string.profile_channel_menu));
        fragment.setDelegate(new ProfileFragment.Delegate() {
            @Override public void onMenuItemClick(ProfileFragment f, int id) {
                if (id == 1) TelegramUI.toggleDayNight(f.getFragmentView());
                else if (id == 2) open(f, !channel);
            }
            @Override public void onWriteButtonClick(ProfileFragment f) {
                Toast.makeText(context, R.string.profile_open_callback, Toast.LENGTH_SHORT).show();
                f.finishFragment();
            }
        });
        ArrayList<ProfileFragment.Row> rows = new ArrayList<>();
        rows.add(ProfileFragment.Row.bio(1, context.getString(R.string.profile_about_text), context.getString(R.string.profile_about)));
        rows.add(ProfileFragment.Row.detail(2, channel ? "@local_updates" : "@local_group", context.getString(R.string.profile_username)));
        rows.add(ProfileFragment.Row.notifications(3, context.getString(R.string.profile_notifications),
                context.getString(org.telegram.tgui.R.string.NotificationsOn), true).setOnClickListener((f, row, view) -> {
            profile.muted = !row.checked;
            row.value = context.getString(row.checked ? org.telegram.tgui.R.string.NotificationsOn : org.telegram.tgui.R.string.NotificationsOff);
            f.updateRow(row);
            f.updateProfile();
        }));
        rows.add(ProfileFragment.Row.shadow());
        if (channel) {
            rows.add(ProfileFragment.Row.text(4, org.telegram.tgui.R.drawable.msg_groups,
                    context.getString(R.string.profile_subscribers), Integer.toString(profile.memberCount))
                    .setOnClickListener((f, row, view) -> Toast.makeText(context, R.string.profile_list_callback, Toast.LENGTH_SHORT).show()));
        }
        rows.add(ProfileFragment.Row.header(5, context.getString(channel ? R.string.profile_admins : R.string.profile_members)));
        for (int i = 0; i < (channel ? 2 : 3); i++) {
            ProfileFragment.Member member = new ProfileFragment.Member(i + 1, new String[]{"Alice", "Bob", "Carol"}[i]);
            member.online = i < 2;
            member.status = context.getString(member.online ? R.string.profile_online : R.string.profile_recent);
            member.role = i == 0 ? context.getString(R.string.profile_owner) : i == 1 ? context.getString(R.string.profile_admin) : null;
            rows.add(ProfileFragment.Row.member(10 + i, member).setOnClickListener((f, row, view) ->
                    Toast.makeText(context, context.getString(R.string.profile_member_callback, member.name), Toast.LENGTH_SHORT).show()));
        }
        rows.add(ProfileFragment.Row.shadow());
        ProfileFragment.Row membership = ProfileFragment.Row.text(20, org.telegram.tgui.R.drawable.msg_groups,
                context.getString(R.string.profile_leave), null);
        final boolean[] joined = {true};
        membership.setOnClickListener((f, row, view) -> {
            joined[0] = !joined[0];
            profile.memberCount += joined[0] ? 1 : -1;
            row.text = context.getString(joined[0] ? R.string.profile_leave : R.string.profile_join);
            f.updateRow(row);
            if (channel) {
                for (ProfileFragment.Row countRow : rows) if (countRow.id == 4) {
                    countRow.value = Integer.toString(profile.memberCount); f.updateRow(countRow);
                }
            }
            f.updateProfile();
            Toast.makeText(context, R.string.profile_membership_callback, Toast.LENGTH_SHORT).show();
        });
        rows.add(membership);
        fragment.setRows(rows);
        from.presentFragment(fragment);
    }
}

