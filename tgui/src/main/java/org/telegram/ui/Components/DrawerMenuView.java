package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.EmptyCell;

import java.util.ArrayList;

/**
 * tgui: the side menu, after upstream LaunchActivity's {@code sideMenuContainer} / {@code sideMenu} and
 * {@code DrawerLayoutAdapter} (view types 0 profile, 1 8dp gap, 2 divider, 3 action; width
 * min(320dp, screen − 56dp)). Accounts, attach-menu bots and the premium status entries are left
 * out; the profile and the menu items come from the host.
 * <pre>
 * DrawerMenuView menu = new DrawerMenuView(context);
 * menu.setUser(id, "First", "Last", "+1 234 567", null, null);
 * menu.addItem(ID_CONTACTS, R.drawable.msg_contacts, "Contacts");
 * menu.addDivider();
 * menu.setDelegate(id -> ...);
 * menu.attachTo(drawerLayoutContainer);
 * </pre>
 */
public class DrawerMenuView extends FrameLayout {

    public interface Delegate {
        void onItemClick(int id);
    }

    private static class Item {
        final int id;
        final int icon;
        final CharSequence text;

        Item(int id, int icon, CharSequence text) {
            this.id = id;
            this.icon = icon;
            this.text = text;
        }
    }

    private final RecyclerListView sideMenu;
    private final ArrayList<Item> items = new ArrayList<>();
    private final Adapter adapter;
    private Delegate delegate;
    private DrawerProfileCell.Delegate profileDelegate;
    private DrawerProfileCell profileCell;

    private long userId;
    private String firstName;
    private String lastName;
    private CharSequence phone;
    private Drawable avatar;
    private Bitmap avatarBitmap;
    private boolean arrowVisible;

    public DrawerMenuView(Context context) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenu = new RecyclerListView(context);
        sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenu.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        sideMenu.setAllowItemsInteractionDuringAnimation(false);
        sideMenu.setAdapter(adapter = new Adapter(context));
        sideMenu.setOnItemClickListener((view, position) -> {
            int index = position - 2;
            if (index >= 0 && index < items.size() && items.get(index) != null && delegate != null) {
                delegate.onItemClick(items.get(index).id);
            }
        });
        addView(sideMenu, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    /** Installs the menu as the drawer of {@code container} with upstream's width rule. */
    public void attachTo(DrawerLayoutContainer container) {
        container.setDrawerLayout(this, sideMenu);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        Point screenSize = AndroidUtilities.getRealScreenSize();
        layoutParams.width = AndroidUtilities.isTablet() ? AndroidUtilities.dp(320) : Math.min(AndroidUtilities.dp(320), Math.min(screenSize.x, screenSize.y) - AndroidUtilities.dp(56));
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        setLayoutParams(layoutParams);
    }

    public RecyclerListView getListView() {
        return sideMenu;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setProfileDelegate(DrawerProfileCell.Delegate delegate) {
        profileDelegate = delegate;
        if (profileCell != null) {
            profileCell.setDelegate(delegate);
        }
    }

    public void setUser(long id, String firstName, String lastName, CharSequence phone, Drawable avatar, Bitmap avatarBitmap) {
        this.userId = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.avatar = avatar;
        this.avatarBitmap = avatarBitmap;
        if (profileCell != null) {
            profileCell.setUser(id, firstName, lastName, phone, avatar, avatarBitmap);
        }
    }

    /** Shows the accounts arrow (hidden by default: tgui has no account list). */
    public void setAccountsArrowVisible(boolean visible) {
        arrowVisible = visible;
        if (profileCell != null) {
            profileCell.setArrowVisible(visible);
        }
    }

    public void addItem(int id, int icon, CharSequence text) {
        items.add(new Item(id, icon, text));
        adapter.notifyDataSetChanged();
    }

    public void addDivider() {
        items.add(null);
        adapter.notifyDataSetChanged();
    }

    public void clearItems() {
        items.clear();
        adapter.notifyDataSetChanged();
    }

    /** Upstream LaunchActivity / DialogsActivity side-menu ThemeDescriptions. */
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
            if (profileCell != null) {
                profileCell.applyBackground(true);
            }
        };
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_chats_menuBackground));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuName));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuPhone));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuPhoneCats));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuTopShadow));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuTopShadowCats));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{DrawerProfileCell.class}, null, null, cellDelegate, Theme.key_chats_menuTopBackgroundCats));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{DrawerProfileCell.class}, null, null, cellDelegate, Theme.key_chats_menuTopBackground));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{DrawerActionCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_chats_menuItemIcon));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DrawerActionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chats_menuItemText));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DividerCell.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_chats_menuBackground));
        return arrayList;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 3;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = profileCell = new DrawerProfileCell(context);
                    profileCell.setDelegate(profileDelegate);
                    profileCell.setArrowVisible(arrowVisible);
                    break;
                case 2:
                    view = new DividerCell(context);
                    break;
                case 3:
                    view = new DrawerActionCell(context);
                    break;
                case 1:
                default:
                    view = new EmptyCell(context, AndroidUtilities.dp(8));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ((DrawerProfileCell) holder.itemView).setUser(userId, firstName, lastName, phone, avatar, avatarBitmap);
                    break;
                case 3: {
                    Item item = items.get(position - 2);
                    ((DrawerActionCell) holder.itemView).setTextAndIcon(item.id, item.text, item.icon);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == 0) {
                return 0;
            } else if (i == 1) {
                return 1;
            }
            i -= 2;
            if (i < 0 || i >= items.size() || items.get(i) == null) {
                return 2;
            }
            return 3;
        }

        @Override
        public int getItemCount() {
            return items.size() + 2;
        }
    }
}
