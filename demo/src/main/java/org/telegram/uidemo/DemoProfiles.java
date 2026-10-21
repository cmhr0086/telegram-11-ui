package org.telegram.uidemo;

import android.graphics.drawable.Drawable;
import android.widget.Toast;

import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ProfileFragment;
import org.telegram.ui.TelegramUI;

import java.util.ArrayList;

/** Builds the demo's {@link ProfileFragment}s: a plain model, host rows and host menu items. */
final class DemoProfiles {

    private static final int ROW_PHONE = 1;
    private static final int ROW_USERNAME = 2;
    private static final int ROW_BIO = 3;
    private static final int ROW_NOTIFICATIONS = 4;
    private static final int ROW_MEDIA = 5;
    private static final int ROW_FILES = 6;
    private static final int ROW_LINKS = 7;
    private static final int ROW_GIFS = 8;
    private static final int ROW_GROUPS = 9;
    private static final int ROW_BLOCK = 10;
    private static final int ROW_DELETE = 11;

    private static final int MENU_SHARE = 1;
    private static final int MENU_EDIT = 2;
    private static final int MENU_NIGHT = 3;

    private DemoProfiles() {
    }

    /** The chat partner of DemoChatFragment / the "Alice Johnson" dialog. */
    static void openAlice(BaseFragment from) {
        ProfileFragment.Profile profile = new ProfileFragment.Profile(3, "Alice Johnson");
        profile.status = "online";
        profile.online = true;
        profile.photo = DemoChatFragment.samplePhoto(800, 800, 1);
        open(from, profile, "+1 555 010 2030", "alice_j", "Coffee, mountains and Android ☕️⛰️", true);
    }

    /** "My Profile" from the drawer: no write button, no block / delete rows. */
    static void openSelf(BaseFragment from) {
        ProfileFragment.Profile profile = new ProfileFragment.Profile(42, "Pavel Durov");
        profile.status = "online";
        profile.online = true;
        profile.verified = true;
        profile.photo = DemoChatFragment.samplePhoto(800, 800, 0);
        open(from, profile, "+1 234 567 8900", "durov", "Telegram UI demo account", false);
    }

    /** Any chat list entry; only dialogs with a photo can be pulled open (as upstream). */
    static void open(BaseFragment from, long id, CharSequence title, boolean online, boolean verified, boolean muted, Drawable photo) {
        ProfileFragment.Profile profile = new ProfileFragment.Profile(id, title.toString());
        profile.status = online ? "online" : "last seen recently";
        profile.online = online;
        profile.verified = verified;
        profile.muted = muted;
        profile.photo = photo;
        String username = title.toString().toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        open(from, profile, "+1 555 0" + (100 + id % 900), username, null, true);
    }

    private static void open(BaseFragment from, ProfileFragment.Profile profile, String phone, String username, CharSequence bio, boolean other) {
        ProfileFragment fragment = new ProfileFragment(profile);
        fragment.setWriteButtonAllowed(other);
        fragment.addMenuItem(MENU_SHARE, R.drawable.msg_share, "Share contact");
        if (other) {
            fragment.addMenuItem(MENU_EDIT, R.drawable.msg_edit, "Edit contact");
        }
        fragment.addMenuItem(MENU_NIGHT, R.drawable.msg_theme, "Switch day / night");
        fragment.setDelegate(new ProfileFragment.Delegate() {
            @Override
            public void onMenuItemClick(ProfileFragment f, int id) {
                if (id == MENU_NIGHT) {
                    TelegramUI.toggleDayNight(f.getFragmentView());
                } else {
                    toast(f, id == MENU_SHARE ? "Share contact" : "Edit contact");
                }
            }

            @Override
            public void onWriteButtonClick(ProfileFragment f) {
                // back to the chat when we came from it, otherwise open one
                if (f.getParentLayout() != null && f.getParentLayout().getFragmentStack().size() > 1
                        && f.getParentLayout().getFragmentStack().get(f.getParentLayout().getFragmentStack().size() - 2) instanceof DemoChatFragment) {
                    f.finishFragment();
                } else {
                    f.presentFragment(new DemoChatFragment());
                }
            }
        });

        ArrayList<ProfileFragment.Row> rows = new ArrayList<>();
        ProfileFragment.Row.OnClickListener copy = (f, row, view) -> toast(f, "Copied " + row.text);
        rows.add(ProfileFragment.Row.detail(ROW_PHONE, phone, "Mobile").setOnClickListener(copy));
        rows.add(ProfileFragment.Row.detail(ROW_USERNAME, "@" + username, "Username").setOnClickListener(copy));
        if (bio != null) {
            rows.add(ProfileFragment.Row.bio(ROW_BIO, bio, "Bio"));
        }
        if (other) {
            rows.add(ProfileFragment.Row.shadow());
            rows.add(ProfileFragment.Row.notifications(ROW_NOTIFICATIONS, "Notifications", profile.muted ? "Off" : "On", !profile.muted)
                    .setOnClickListener((f, row, view) -> {
                        // the fragment already flipped row.checked and the switch
                        row.value = row.checked ? "On" : "Off";
                        f.updateRow(row);
                        f.getProfile().muted = !row.checked;
                        f.updateProfile();
                    }));
        }
        rows.add(ProfileFragment.Row.shadow());
        ProfileFragment.Row.OnClickListener media = (f, row, view) -> toast(f, row.text + ": " + row.value);
        rows.add(ProfileFragment.Row.text(ROW_MEDIA, R.drawable.msg_photos, "Photos and videos", "128").setOnClickListener(media));
        rows.add(ProfileFragment.Row.text(ROW_FILES, R.drawable.msg_filehq, "Files", "14").setOnClickListener(media));
        rows.add(ProfileFragment.Row.text(ROW_LINKS, R.drawable.msg_link2, "Shared links", "37").setOnClickListener(media));
        rows.add(ProfileFragment.Row.text(ROW_GIFS, R.drawable.msg_gif, "GIFs", "5").setOnClickListener(media));
        if (other) {
            rows.add(ProfileFragment.Row.text(ROW_GROUPS, R.drawable.msg_groups, "Groups in common", "2").setOnClickListener(media));
            rows.add(ProfileFragment.Row.shadow());
            rows.add(ProfileFragment.Row.destructive(ROW_BLOCK, R.drawable.msg_block, "Block user").setOnClickListener((f, row, view) -> {
                boolean blocked = "Unblock user".contentEquals(row.text);
                if (blocked) {
                    row.text = "Block user";
                    f.updateRow(row);
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(f.getParentActivity());
                builder.setTitle("Block user");
                builder.setMessage("Do you want to block " + f.getProfile().name + " from messaging and calling you on Telegram?");
                builder.setPositiveButton("Block user", (dialog, which) -> {
                    row.text = "Unblock user";
                    f.updateRow(row);
                });
                builder.setNegativeButton("Cancel", null);
                AlertDialog dialog = builder.create();
                f.showDialog(dialog);
                dialog.redPositive();
            }));
            rows.add(ProfileFragment.Row.destructive(ROW_DELETE, R.drawable.msg_delete, "Delete contact").setOnClickListener((f, row, view) -> toast(f, "Delete contact")));
        }
        rows.add(ProfileFragment.Row.shadow());
        fragment.setRows(rows);
        from.presentFragment(fragment);
    }

    private static void toast(BaseFragment fragment, CharSequence text) {
        if (fragment.getParentActivity() != null) {
            Toast.makeText(fragment.getParentActivity(), text, Toast.LENGTH_SHORT).show();
        }
    }
}
