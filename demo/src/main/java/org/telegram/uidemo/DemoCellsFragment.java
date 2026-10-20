package org.telegram.uidemo;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.TextCheckCell;

import java.util.ArrayList;

/** Gallery of the upstream settings cells, wired to local state. */
public class DemoCellsFragment extends DemoListFragment {

    private boolean notifications = true;
    private boolean previews = false;
    private boolean sounds = true;
    private int selectedSize = 1;
    private final boolean[] checks = {true, false, true};

    private static final int ROW_NOTIFY = 1;
    private static final int ROW_PREVIEW = 2;
    private static final int ROW_SOUND = 3;
    private static final int ROW_SIZE_BASE = 10;
    private static final int ROW_CHECK_BASE = 20;
    private static final int ROW_DETAIL = 30;
    private static final int ROW_VALUE = 31;
    private static final int ROW_ICON_VALUE = 32;
    private static final int ROW_BIO = 33;

    private static final String[] SIZES = {"Small", "Medium", "Large"};

    @Override
    protected CharSequence getTitle() {
        return "Settings cells";
    }

    @Override
    protected void fillItems(ArrayList<Item> items) {
        items.add(header("Switches"));
        items.add(new Item(TYPE_NOTIFICATIONS_CHECK, ROW_NOTIFY, "Notifications").value(notifications ? "On, sound & vibrate" : "Off").checked(notifications));
        items.add(new Item(TYPE_CHECK, ROW_PREVIEW, "Message preview").checked(previews));
        items.add(new Item(TYPE_CHECK, ROW_SOUND, "In-app sounds").checked(sounds));
        items.add(shadow());

        items.add(header("Radio group"));
        for (int i = 0; i < SIZES.length; i++) {
            items.add(new Item(TYPE_RADIO, ROW_SIZE_BASE + i, SIZES[i]).checked(selectedSize == i));
        }
        items.add(info("RadioCell + RadioButton with the upstream check animation."));

        items.add(header("Check boxes"));
        String[] names = {"Photos", "Videos", "Files"};
        for (int i = 0; i < names.length; i++) {
            items.add(new Item(TYPE_CHECKBOX, ROW_CHECK_BASE + i, names[i]).value(i == 0 ? "1.2 MB" : i == 1 ? "48 MB" : "3 KB").checked(checks[i]));
        }
        items.add(shadow());

        items.add(header("Values"));
        items.add(new Item(TYPE_DETAIL, ROW_DETAIL, "Username").value("@telegram_ui_demo"));
        items.add(new Item(TYPE_DETAIL, ROW_BIO, "Building Telegram-style UIs \uD83D\uDE80\u2728\uD83D\uDC4D").value("Bio"));
        items.add(new Item(TYPE_SETTINGS, ROW_VALUE, "Text size").value(SIZES[selectedSize]));
        items.add(text(ROW_ICON_VALUE, "Language", R.drawable.msg_language).value("English"));
        items.add(text(0, "Notifications and Sounds", R.drawable.msg_notifications));
        items.add(text(0, "Privacy and Security", R.drawable.msg_settings));
        items.add(info("TextCell, TextSettingsCell, TextDetailSettingsCell, HeaderCell, ShadowSectionCell and TextInfoPrivacyCell are the original classes; only messenger-specific setters were removed."));
    }

    @Override
    protected void onItemClick(Item item, View view, float x, float y) {
        if (item.id == ROW_NOTIFY) {
            notifications = !notifications;
            if (view instanceof NotificationsCheckCell) {
                ((NotificationsCheckCell) view).setChecked(notifications);
            }
            updateItems();
        } else if (item.id == ROW_PREVIEW || item.id == ROW_SOUND) {
            boolean value;
            if (item.id == ROW_PREVIEW) {
                value = previews = !previews;
            } else {
                value = sounds = !sounds;
            }
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(value);
            }
            item.checked = value;
        } else if (item.id >= ROW_SIZE_BASE && item.id < ROW_SIZE_BASE + SIZES.length) {
            selectedSize = item.id - ROW_SIZE_BASE;
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof RadioCell) {
                    int position = listView.getChildAdapterPosition(child);
                    if (position >= 0 && position < items.size()) {
                        Item it = items.get(position);
                        ((RadioCell) child).setChecked(it.id == item.id, true);
                    }
                }
            }
            AndroidUtilitiesCompat.postUpdate(this);
        } else if (item.id >= ROW_CHECK_BASE && item.id < ROW_CHECK_BASE + checks.length) {
            int i = item.id - ROW_CHECK_BASE;
            checks[i] = !checks[i];
            item.checked = checks[i];
            if (view instanceof CheckBoxCell) {
                ((CheckBoxCell) view).setChecked(checks[i], true);
            }
        }
    }

    /** Rebind after the radio animation so values elsewhere on screen refresh. */
    private static class AndroidUtilitiesCompat {
        static void postUpdate(DemoCellsFragment fragment) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(fragment::updateItems, 250);
        }
    }
}
