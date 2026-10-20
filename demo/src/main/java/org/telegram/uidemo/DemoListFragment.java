package org.telegram.uidemo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Settings-style screen: an ActionBar plus a RecyclerListView of upstream cells, described by a
 * flat list of {@link Item}s that subclasses rebuild in {@link #fillItems(ArrayList)}.
 */
public abstract class DemoListFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    protected static final int TYPE_HEADER = 0;
    protected static final int TYPE_SHADOW = 1;
    protected static final int TYPE_TEXT = 2;
    protected static final int TYPE_SETTINGS = 3;
    protected static final int TYPE_CHECK = 4;
    protected static final int TYPE_INFO = 5;
    protected static final int TYPE_RADIO = 6;
    protected static final int TYPE_NOTIFICATIONS_CHECK = 7;
    protected static final int TYPE_DETAIL = 8;
    protected static final int TYPE_CHECKBOX = 9;

    protected static class Item {
        final int type;
        final int id;
        CharSequence text;
        CharSequence value;
        int icon;
        boolean checked;

        Item(int type, int id, CharSequence text) {
            this.type = type;
            this.id = id;
            this.text = text;
        }

        Item value(CharSequence value) {
            this.value = value;
            return this;
        }

        Item icon(int icon) {
            this.icon = icon;
            return this;
        }

        Item checked(boolean checked) {
            this.checked = checked;
            return this;
        }
    }

    protected static Item header(CharSequence text) {
        return new Item(TYPE_HEADER, 0, text);
    }

    protected static Item shadow() {
        return new Item(TYPE_SHADOW, 0, null);
    }

    protected static Item info(CharSequence text) {
        return new Item(TYPE_INFO, 0, text);
    }

    protected static Item text(int id, CharSequence text, int icon) {
        return new Item(TYPE_TEXT, id, text).icon(icon);
    }

    protected RecyclerListView listView;
    protected ListAdapter adapter;
    protected final ArrayList<Item> items = new ArrayList<>();

    protected abstract CharSequence getTitle();

    protected abstract void fillItems(ArrayList<Item> items);

    protected abstract void onItemClick(Item item, View view, float x, float y);

    protected boolean showBackButton() {
        return true;
    }

    protected void createMenu(ActionBar actionBar) {
    }

    protected void onMenuItemClick(int id) {
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme && listView != null) {
            // theme names / checks depend on the active theme; colors are animated by ThemeDescriptions
            listView.post(this::updateItems);
        }
    }

    @Override
    public View createView(Context context) {
        if (showBackButton()) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else {
                    onMenuItemClick(id);
                }
            }
        });
        createMenu(actionBar);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        // emoji bitmaps load in the background; upstream screens redraw their lists on emojiLoaded
        NotificationCenter.getGlobalInstance().listenGlobal(listView, NotificationCenter.emojiLoaded, args -> Emoji.invalidateAll(listView));
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position >= 0 && position < items.size()) {
                onItemClick(items.get(position), view, x, y);
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        updateItems();
        return fragmentView;
    }

    protected void updateItems() {
        items.clear();
        fillItems(items);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        // Day/night switching animates every color registered here (upstream pattern).
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        descriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCell.class, TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, RadioCell.class, NotificationsCheckCell.class, TextDetailSettingsCell.class, CheckBoxCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));
        return descriptions;
    }

    protected class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type != TYPE_HEADER && type != TYPE_SHADOW && type != TYPE_INFO;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HEADER:
                    view = new HeaderCell(context);
                    break;
                case TYPE_SHADOW:
                    view = new ShadowSectionCell(context);
                    break;
                case TYPE_SETTINGS:
                    view = new TextSettingsCell(context);
                    break;
                case TYPE_CHECK:
                    view = new TextCheckCell(context);
                    break;
                case TYPE_INFO:
                    view = new TextInfoPrivacyCell(context);
                    break;
                case TYPE_RADIO:
                    view = new RadioCell(context);
                    break;
                case TYPE_NOTIFICATIONS_CHECK:
                    view = new NotificationsCheckCell(context);
                    break;
                case TYPE_DETAIL:
                    view = new TextDetailSettingsCell(context);
                    break;
                case TYPE_CHECKBOX:
                    view = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT);
                    break;
                case TYPE_TEXT:
                default:
                    view = new TextCell(context);
                    break;
            }
            if (viewType != TYPE_SHADOW && viewType != TYPE_INFO) {
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            boolean divider = position + 1 < items.size() && items.get(position + 1).type != TYPE_SHADOW && items.get(position + 1).type != TYPE_INFO && items.get(position + 1).type != TYPE_HEADER;
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(item.text);
                    break;
                case TYPE_SHADOW: {
                    ShadowSectionCell cell = (ShadowSectionCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawableByKey(context, position + 1 >= items.size() ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(item.text);
                    cell.setBackground(Theme.getThemedDrawableByKey(context, position + 1 >= items.size() ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (item.value != null) {
                        cell.setTextAndValue(item.text, item.value, divider);
                    } else {
                        cell.setText(item.text, divider);
                    }
                    break;
                }
                case TYPE_CHECK:
                    ((TextCheckCell) holder.itemView).setTextAndCheck(item.text, item.checked, divider);
                    break;
                case TYPE_RADIO:
                    ((RadioCell) holder.itemView).setText(item.text, item.checked, divider);
                    break;
                case TYPE_NOTIFICATIONS_CHECK:
                    ((NotificationsCheckCell) holder.itemView).setTextAndValueAndCheck(item.text, item.value, item.checked, divider);
                    break;
                case TYPE_DETAIL:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    detailCell.setTextAndValue(Emoji.replaceEmoji(item.text, detailCell.getTextView().getPaint().getFontMetricsInt(), false), item.value, divider);
                    break;
                case TYPE_CHECKBOX:
                    ((CheckBoxCell) holder.itemView).setText(item.text, item.value == null ? "" : item.value.toString(), item.checked, divider);
                    break;
                case TYPE_TEXT:
                default: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (item.value != null && item.icon != 0) {
                        cell.setTextAndValueAndIcon(item.text, item.value, item.icon, divider);
                    } else if (item.value != null) {
                        cell.setTextAndValue(item.text, item.value, divider);
                    } else if (item.icon != 0) {
                        cell.setTextAndIcon(item.text, item.icon, divider);
                    } else {
                        cell.setText(item.text, divider);
                    }
                    break;
                }
            }
        }
    }
}
