package org.telegram.uidemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.tgui.recyclerview.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.DialogItem;
import org.telegram.ui.Components.DialogsActionMode;
import org.telegram.ui.Components.DialogsFloatingButton;
import org.telegram.ui.Components.DrawerMenuView;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.DialogsListView;
import org.telegram.ui.Components.DialogsSwipeController;
import org.telegram.ui.Components.DialogsViewPager;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.TelegramUI;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Chat list demo: upstream DialogCell rows bound to plain {@link DialogItem}s, with the upstream
 * swipe actions, long-press selection mode, DialogsItemAnimator, the hidden archive (pull down on
 * "All Chats") and sideways swipes between folder pages ({@link DialogsViewPager}).
 */
public class DemoDialogsFragment extends BaseFragment {

    private static final int MENU_LINES = 1;
    private static final int MENU_SIMULATE = 2;
    private static final int MENU_NIGHT = 3;
    private static final int MENU_SWIPE = 4;

    private static final int ACTION_PIN = 100;
    private static final int ACTION_MUTE = 101;
    private static final int ACTION_ARCHIVE = 102;
    private static final int ACTION_DELETE = 103;
    private static final int ACTION_READ = 104;

    /** -1: upstream SWIPE_GESTURE_FOLDERS (the default when folders exist): sideways swipes switch folders. */
    private static final int[] SWIPE_ACTIONS = {-1, DialogCell.SWIPE_ACTION_ARCHIVE, DialogCell.SWIPE_ACTION_MUTE, DialogCell.SWIPE_ACTION_READ, DialogCell.SWIPE_ACTION_PIN, DialogCell.SWIPE_ACTION_DELETE};
    private static final String[] SWIPE_NAMES = {"Switch folders", "Archive", "Mute", "Read", "Pin", "Delete"};

    /** One folder page (upstream DialogsActivity.ViewPage). */
    private static class Page {
        DialogsListView listView;
        Adapter adapter;
        final ArrayList<DialogItem> dialogs = new ArrayList<>();
    }

    private final Page[] pages = new Page[2];
    private DialogsViewPager viewPager;
    // the visible page's views and items
    private DialogsListView listView;
    private Adapter adapter;
    private ArrayList<DialogItem> dialogs;
    private DialogsActionMode actionMode;
    private ActionBarMenuItem swipeMenuItem;
    private ActionBarMenuSubItem swipeSubItem;
    private final ArrayList<DialogItem> allDialogs = new ArrayList<>();
    private FilterTabsView filterTabsView;
    private DialogsFloatingButton floatingButton;
    private DrawerMenuView drawerMenu;
    private MenuDrawable menuDrawable;
    private int currentTab;
    private final HashSet<Long> selected = new HashSet<>();
    private int simulateStep;
    private int swipeIndex;
    private int archivedCount = 4;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(menuDrawable = new MenuDrawable());
        actionBar.setBackButtonContentDescription("Open navigation menu");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Telegram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionMode.isShowing()) {
                        clearSelection();
                    } else if (getParentLayout() != null && getParentLayout().getDrawerLayoutContainer() != null) {
                        getParentLayout().getDrawerLayoutContainer().openDrawer(false);
                    }
                } else if (id == MENU_LINES) {
                    SharedConfig.useThreeLinesLayout = !SharedConfig.useThreeLinesLayout;
                    for (Page page : pages) {
                        page.adapter.notifyDataSetChanged();
                    }
                } else if (id == MENU_SIMULATE) {
                    simulateUpdate();
                } else if (id == MENU_NIGHT) {
                    TelegramUI.toggleDayNight(actionBar);
                } else if (id == MENU_SWIPE) {
                    swipeIndex = (swipeIndex + 1) % SWIPE_ACTIONS.length;
                    updateSwipeMenuText();
                } else {
                    onActionModeItem(id);
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        swipeMenuItem = menu.addItem(0, R.drawable.ic_ab_other);
        swipeMenuItem.addSubItem(MENU_SIMULATE, R.drawable.msg_msgbubble2, "Simulate update");
        swipeSubItem = swipeMenuItem.addSubItem(MENU_SWIPE, R.drawable.msg_archive, "");
        swipeMenuItem.addSubItem(MENU_LINES, R.drawable.msg_list, "Two / three lines");
        swipeMenuItem.addSubItem(MENU_NIGHT, R.drawable.msg_theme, "Day / night");
        updateSwipeMenuText();

        // upstream DialogsActivity.createActionMode items
        actionMode = new DialogsActionMode(actionBar);
        actionMode.addItem(ACTION_PIN, R.drawable.msg_pin, "Pin");
        actionMode.addItem(ACTION_MUTE, R.drawable.msg_mute, "Mute");
        actionMode.addItem(ACTION_ARCHIVE, R.drawable.msg_archive, "Archive");
        actionMode.addItem(ACTION_DELETE, R.drawable.msg_delete, "Delete");
        actionMode.addOverflowItem(ACTION_READ, R.drawable.msg_markread, "Mark as read");

        // upstream: the archive starts pinned; the demo starts with it hidden to show the pull-down
        SharedConfig.archiveHidden = true;
        fillDialogs();
        createFilterTabs(context);
        actionMode.setFilterTabsView(filterTabsView);
        createDrawer(context);

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        for (int a = 0; a < pages.length; a++) {
            pages[a] = createPage(context);
        }
        setCurrentPage(pages[0]);
        viewPager = new DialogsViewPager(context, pages[0].listView, pages[1].listView, filterTabsView, new DialogsViewPager.Delegate() {
            @Override
            public void onBindPage(View view, int tabId) {
                fillPage(pageFor(view), tabId);
            }

            @Override
            public void onPageChanged(View view, int tabId) {
                currentTab = tabId;
                setCurrentPage(pageFor(view));
                updateDrawerSwipe();
            }

            @Override
            public boolean canSwipe() {
                DrawerLayoutContainer drawer = getParentLayout() != null ? getParentLayout().getDrawerLayoutContainer() : null;
                return SWIPE_ACTIONS[swipeIndex] < 0 && !actionMode.isShowing() && (drawer == null || !drawer.isDrawerOpened());
            }
        });
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        floatingButton = new DialogsFloatingButton(context);
        floatingButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("New message");
            builder.setMessage("The host opens its own compose screen here.");
            builder.setPositiveButton("OK", null);
            showDialog(builder.create());
        });
        frameLayout.addView(floatingButton, DialogsFloatingButton.createLayoutParams());
        for (Page page : pages) {
            floatingButton.attachToList(page.listView);
        }
        return fragmentView;
    }

    private Page pageFor(View view) {
        return pages[0].listView == view ? pages[0] : pages[1];
    }

    private void setCurrentPage(Page page) {
        listView = page.listView;
        adapter = page.adapter;
        dialogs = page.dialogs;
    }

    private Page createPage(Context context) {
        Page page = new Page();
        DialogsListView listView;
        page.listView = listView = new DialogsListView(context);
        listView.setAdapter(page.adapter = new Adapter(context, page.dialogs));
        listView.setOnItemClickListener((view, position) -> {
            if (!(view instanceof DialogCell)) {
                return;
            }
            DialogCell cell = (DialogCell) view;
            if (actionMode.isShowing()) {
                if (!cell.getDialog().isFolder) {
                    toggleSelection(cell);
                }
            } else if (cell.getDialog().isFolder) {
                cell.animateArchiveAvatar();
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (!(view instanceof DialogCell) || ((DialogCell) view).getDialog().isFolder) {
                return false;
            }
            toggleSelection((DialogCell) view);
            return true;
        });
        listView.setSwipeDelegate(new DialogsSwipeController.Delegate() {
            @Override
            public int getSwipeAction(DialogCell cell) {
                if (cell.getDialog().isFolder) {
                    // upstream: the archive row swipes to hide (or, when hidden, pin) the archive
                    return DialogCell.SWIPE_ACTION_FOLDER;
                }
                return SWIPE_ACTIONS[swipeIndex];
            }

            @Override
            public void onSwipeAction(DialogCell cell, int action) {
                DialogItem d = cell.getDialog();
                if (action == DialogCell.SWIPE_ACTION_MUTE) {
                    d.muted = !d.muted;
                    cell.setDialog(d, true);
                } else if (action == DialogCell.SWIPE_ACTION_READ) {
                    if (d.unreadCount > 0 || d.markUnread) {
                        d.unreadCount = 0;
                        d.markUnread = false;
                        d.mention = false;
                    } else {
                        d.markUnread = true;
                    }
                    cell.setDialog(d, true);
                    filterTabsView.checkTabsCounter();
                } else if (action == DialogCell.SWIPE_ACTION_PIN) {
                    togglePinned(d);
                } else if (action == DialogCell.SWIPE_ACTION_DELETE) {
                    confirmDelete(d);
                }
            }

            @Override
            public void onSwiped(DialogCell cell, int action) {
                removeDialog(cell.getDialog(), true);
            }

            @Override
            public boolean isSelectionMode() {
                return actionMode.isShowing();
            }

            @Override
            public boolean canReorder(DialogCell cell) {
                return cell.getDialog().pinned;
            }

            @Override
            public boolean onReorder(int fromPosition, int toPosition) {
                DialogItem item = dialogs.remove(fromPosition);
                dialogs.add(toPosition, item);
                adapter.notifyItemMoved(fromPosition, toPosition);
                syncAll();
                return true;
            }
        });
        listView.setArchivePullDelegate(() -> !page.dialogs.isEmpty() && page.dialogs.get(0).isFolder);
        return page;
    }

    // ---- folder tabs (upstream DialogsActivity filterTabsView) ----

    private static final String[] TABS = {"All Chats", "Unread", "Personal", "Groups"};

    private void createFilterTabs(Context context) {
        filterTabsView = new FilterTabsView(context);
        filterTabsView.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override
            public void onPageSelected(FilterTabsView.Tab tab, boolean forward) {
                if (actionMode.isShowing()) {
                    clearSelection();
                }
                viewPager.onTabSelected(tab, forward);
            }

            @Override
            public void onPageScrolled(float progress) {
                viewPager.onTabScrolled(progress);
            }

            @Override
            public void onSamePageSelected() {
                listView.scrollToTop(true);
            }

            @Override
            public int getTabCounter(int tabId) {
                int count = 0;
                for (DialogItem d : allDialogs) {
                    if (!d.isFolder && matches(tabId, d) && (d.unreadCount > 0 || d.markUnread)) {
                        count++;
                    }
                }
                return count;
            }

            @Override
            public boolean didSelectTab(FilterTabsView.TabView tabView, boolean selected) {
                return true;
            }

            @Override
            public boolean isTabMenuVisible() {
                return false;
            }

            @Override
            public void onDeletePressed(int id) {
            }

            @Override
            public void onPageReorder(int fromId, int toId) {
            }

            @Override
            public boolean canPerformActions() {
                return true;
            }
        });
        // upstream order: delegate first (addTab measures counters through it)
        for (int a = 0; a < TABS.length; a++) {
            filterTabsView.addTab(a, a, TABS[a], a == 0, false);
        }
        filterTabsView.finishAddingTabs(false);
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        actionBar.addView(filterTabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.BOTTOM));
    }

    private static boolean matches(int tab, DialogItem d) {
        switch (tab) {
            case 1:
                return d.unreadCount > 0 || d.markUnread || d.isFolder;
            case 2:
                return d.senderName == null && d.avatarType == DialogItem.AVATAR_NORMAL;
            case 3:
                return d.senderName != null;
            default:
                return true;
        }
    }

    /** Upstream switchToCurrentSelectedMode: fills a page with a folder's chats, scrolled to the top. */
    private void fillPage(Page page, int tab) {
        page.dialogs.clear();
        for (DialogItem d : allDialogs) {
            if (matches(tab, d) && (tab == 0 || !d.isFolder)) {
                page.dialogs.add(d);
            }
        }
        DialogsListView list = page.listView;
        // a new folder is a new page upstream: no item animations for the swap
        list.setItemAnimator(null);
        page.adapter.notifyDataSetChanged();
        list.updatePullState();
        list.scrollToTop(false);
        list.post(() -> list.setItemAnimator(list.getDialogsItemAnimator()));
    }

    /** Upstream updateDrawerSwipeEnabled: a right swipe opens the drawer on the first folder only. */
    private void updateDrawerSwipe() {
        if (getParentLayout() != null && getParentLayout().getDrawerLayoutContainer() != null) {
            getParentLayout().getDrawerLayoutContainer().setAllowOpenDrawerBySwipe(currentTab == filterTabsView.getFirstTabId());
        }
    }

    /** Keeps the master list in step with edits made on the visible page. */
    private void syncAll() {
        if (currentTab == 0) {
            allDialogs.clear();
            allDialogs.addAll(dialogs);
        }
        if (filterTabsView != null) {
            filterTabsView.checkTabsCounter();
        }
    }

    // ---- drawer (upstream LaunchActivity side menu) ----

    private static final int DRAWER_NEW_GROUP = 1;
    private static final int DRAWER_CONTACTS = 2;
    private static final int DRAWER_CALLS = 3;
    private static final int DRAWER_SAVED = 4;
    private static final int DRAWER_SETTINGS = 5;
    private static final int DRAWER_BACK = 6;

    private void createDrawer(Context context) {
        if (getParentLayout() == null || getParentLayout().getDrawerLayoutContainer() == null) {
            return;
        }
        DrawerLayoutContainer container = getParentLayout().getDrawerLayoutContainer();
        if (container.getDrawerLayout() instanceof DrawerMenuView) {
            drawerMenu = (DrawerMenuView) container.getDrawerLayout();
        } else {
            drawerMenu = new DrawerMenuView(getParentActivity());
            drawerMenu.setUser(42, "Pavel", "Durov", "+1 234 567 8900", null, gradientBitmap(0xFF7BC862, 0xFF46A03A));
            drawerMenu.addItem(DRAWER_NEW_GROUP, R.drawable.msg_groups, "New Group");
            drawerMenu.addItem(DRAWER_CONTACTS, R.drawable.msg_contacts, "Contacts");
            drawerMenu.addItem(DRAWER_CALLS, R.drawable.msg_calls, "Calls");
            drawerMenu.addItem(DRAWER_SAVED, R.drawable.msg_saved, "Saved Messages");
            drawerMenu.addItem(DRAWER_SETTINGS, R.drawable.msg_settings_old, "Settings");
            drawerMenu.addDivider();
            drawerMenu.addItem(DRAWER_BACK, R.drawable.msg_leave, "Back to demo");
            drawerMenu.attachTo(container);
        }
        drawerMenu.setDelegate(id -> {
            container.closeDrawer(false);
            if (id == DRAWER_BACK) {
                finishFragment();
            } else if (id == DRAWER_SETTINGS) {
                presentFragment(new DemoCellsFragment());
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getParentLayout() != null && getParentLayout().getDrawerLayoutContainer() != null) {
            getParentLayout().getDrawerLayoutContainer().setAllowOpenDrawer(true, false);
        }
        if (filterTabsView != null) {
            updateDrawerSwipe();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getParentLayout() != null && getParentLayout().getDrawerLayoutContainer() != null) {
            getParentLayout().getDrawerLayoutContainer().setAllowOpenDrawer(false, false);
            getParentLayout().getDrawerLayoutContainer().setAllowOpenDrawerBySwipe(true);
        }
    }

    @Override
    public boolean isSwipeBackEnabled(android.view.MotionEvent event) {
        // upstream DialogsActivity is the root screen: a swipe from the edge opens the drawer instead
        return false;
    }

    private void updateSwipeMenuText() {
        swipeSubItem.setText("Swipe action: " + SWIPE_NAMES[swipeIndex]);
    }

    @Override
    public boolean onBackPressed() {
        if (actionMode != null && actionMode.isShowing()) {
            clearSelection();
            return false;
        }
        return super.onBackPressed();
    }

    // ---- selection (upstream showOrUpdateActionMode / hideActionMode) ----

    private int pinnedCount() {
        int n = 0;
        for (DialogItem d : dialogs) {
            if (d.pinned) {
                n++;
            }
        }
        return n;
    }

    private void toggleSelection(DialogCell cell) {
        long id = cell.getDialog().id;
        boolean wasShowing = actionMode.isShowing();
        boolean checked = !selected.remove(id);
        if (checked) {
            selected.add(id);
        }
        cell.setChecked(checked, true);
        actionMode.setCount(selected.size());
        if (wasShowing != actionMode.isShowing()) {
            updateReorderState(actionMode.isShowing());
        }
    }

    private void clearSelection() {
        selected.clear();
        actionMode.hide();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof DialogCell) {
                ((DialogCell) child).setChecked(false, true);
            }
        }
        updateReorderState(false);
    }

    private void updateReorderState(boolean reordering) {
        boolean show = reordering && pinnedCount() > 1;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof DialogCell) {
                ((DialogCell) child).onReorderStateChanged(show, true);
            }
        }
    }

    private void onActionModeItem(int id) {
        ArrayList<DialogItem> items = new ArrayList<>();
        for (DialogItem d : dialogs) {
            if (selected.contains(d.id)) {
                items.add(d);
            }
        }
        if (id == ACTION_DELETE) {
            clearSelection();
            for (DialogItem d : items) {
                removeDialog(d, false);
            }
            return;
        }
        if (id == ACTION_ARCHIVE) {
            clearSelection();
            for (DialogItem d : items) {
                removeDialog(d, true);
            }
            return;
        }
        for (DialogItem d : items) {
            if (id == ACTION_PIN) {
                togglePinned(d);
            } else if (id == ACTION_MUTE) {
                d.muted = !d.muted;
            } else if (id == ACTION_READ) {
                d.unreadCount = 0;
                d.markUnread = false;
                d.mention = false;
            }
        }
        clearSelection();
        rebindVisible(true);
        syncAll();
    }

    // ---- list changes (animated by DialogsItemAnimator) ----

    private void rebindVisible(boolean animated) {
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof DialogCell) {
                DialogCell cell = (DialogCell) child;
                cell.setDialog(cell.getDialog(), animated);
            }
        }
    }

    private void togglePinned(DialogItem d) {
        int from = dialogs.indexOf(d);
        dialogs.remove(from);
        d.pinned = !d.pinned;
        int to = 0;
        if (d.pinned) {
            // after the archive row, on top of the pinned block (upstream pins go first)
            while (to < dialogs.size() && dialogs.get(to).isFolder) {
                to++;
            }
        } else {
            to = pinnedCount() + (dialogs.size() > 0 && dialogs.get(0).isFolder ? 1 : 0);
        }
        dialogs.add(to, d);
        adapter.notifyItemMoved(from, to);
        syncAll();
        listView.post(() -> {
            rebindVisible(true);
            adapter.notifyItemRangeChanged(0, dialogs.size());
        });
    }

    private void confirmDelete(DialogItem d) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Delete chat");
        builder.setMessage("Are you sure you want to delete the chat with " + d.title + "?");
        builder.setPositiveButton("Delete", (dialog, which) -> removeDialog(d, false));
        builder.setNegativeButton("Cancel", null);
        builder.makeRed(AlertDialog.BUTTON_POSITIVE);
        showDialog(builder.create());
    }

    private void removeDialog(DialogItem d, boolean archive) {
        int position = dialogs.indexOf(d);
        if (position < 0) {
            return;
        }
        listView.getDialogsItemAnimator().prepareForRemove();
        dialogs.remove(position);
        allDialogs.remove(d);
        adapter.notifyItemRemoved(position);
        syncAll();
        if (archive) {
            archivedCount++;
            if (!dialogs.isEmpty() && dialogs.get(0).isFolder) {
                DialogItem folder = dialogs.get(0);
                folder.message = d.title + ", " + folder.message;
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
                if (holder != null && holder.itemView instanceof DialogCell) {
                    ((DialogCell) holder.itemView).setDialog(folder, true);
                    ((DialogCell) holder.itemView).animateArchiveAvatar();
                }
            }
        }
    }

    private DialogItem findDialog(long id) {
        for (DialogItem d : dialogs) {
            if (d.id == id) {
                return d;
            }
        }
        return null;
    }

    private void simulateUpdate() {
        // cycles through the upstream animated transitions: counter, typing, check status
        DialogItem d = findDialog(3);
        DialogItem out = findDialog(4);
        if (d != null) {
            switch (simulateStep++ % 4) {
                case 0:
                    d.unreadCount++;
                    d.message = "One more message " + d.unreadCount;
                    d.time = "now";
                    break;
                case 1:
                    d.typing = "typing";
                    d.typingType = 0;
                    break;
                case 2:
                    d.typing = null;
                    d.message = "Done typing 👋";
                    d.unreadCount++;
                    break;
                case 3:
                    d.unreadCount = 0;
                    break;
            }
        }
        if (out != null) {
            out.sentState = out.sentState == DialogItem.SENT_READ ? DialogItem.SENT_SENDING : out.sentState + 1;
        }
        rebindVisible(true);
        if (filterTabsView != null) {
            filterTabsView.checkTabsCounter();
        }
    }

    private void fillDialogs() {
        allDialogs.clear();
        DialogItem d;

        d = new DialogItem(0, "Archived Chats", "Design Team, Weekend Plans, Old Project", null);
        d.isFolder = true;
        d.senderName = null;
        d.unreadCount = 4;
        allDialogs.add(d);

        d = new DialogItem(1, "Saved Messages", "photo_2024.jpg", "14:02");
        d.avatarType = DialogItem.AVATAR_SAVED;
        d.pinned = true;
        d.sentState = DialogItem.SENT_READ;
        allDialogs.add(d);

        d = new DialogItem(2, "Telegram", "Your login code: 52713. Do not give this code to anyone.", "13:47");
        d.verified = true;
        d.pinned = true;
        d.unreadCount = 1;
        d.avatar = gradientAvatar(0xFF2AABEE, 0xFF229ED9, "✈");
        allDialogs.add(d);

        d = new DialogItem(3, "Alice Johnson", "See you tomorrow at the café ☕️", "13:12");
        d.online = true;
        d.unreadCount = 2;
        d.avatarBitmap = gradientBitmap(0xFFFF8A65, 0xFFE91E63);
        allDialogs.add(d);

        d = new DialogItem(4, "Bob Smith", "Sure, I'll send the files tonight", "12:58");
        d.sentState = DialogItem.SENT_SENT;
        allDialogs.add(d);

        d = new DialogItem(5, "Android Developers", "Is anyone using Compose with RecyclerView?", "12:40");
        d.senderName = "Kevin";
        d.unreadCount = 128;
        d.mention = true;
        allDialogs.add(d);

        d = new DialogItem(6, "Family 🏡", "Photo", "11:05");
        d.senderName = "Mom";
        d.mediaPreview = true;
        d.muted = true;
        d.unreadCount = 17;
        allDialogs.add(d);

        d = new DialogItem(7, "Charlie", "typing", "10:31");
        d.message = "Did you see the game last night?";
        d.typing = "typing";
        d.typingType = 0;
        d.online = true;
        allDialogs.add(d);

        d = new DialogItem(8, "Diana Prince", "Let me check my calendar", "Yesterday");
        d.draft = "I think Friday works better for";
        allDialogs.add(d);

        d = new DialogItem(9, "Evan", "Voice message", "Yesterday");
        d.mediaPreview = true;
        d.sentState = DialogItem.SENT_SENDING;
        allDialogs.add(d);

        d = new DialogItem(10, "Frank Ocean", "The upload failed, retrying…", "Mon");
        d.sentState = DialogItem.SENT_ERROR;
        allDialogs.add(d);

        d = new DialogItem(11, "Grace Hopper", "It's easier to ask forgiveness than it is to get permission.", "Sun");
        d.secret = true;
        allDialogs.add(d);

        d = new DialogItem(12, "Tech News", "New Android release brings predictive back to every app 🚀", "Sat");
        d.muted = true;
        d.markUnread = true;
        allDialogs.add(d);

        d = new DialogItem(13, "Hannah", "😂😂😂", "Fri");
        d.sentState = DialogItem.SENT_READ;
        allDialogs.add(d);

        d = new DialogItem(14, "Ivan Petrov", "Sticker", "Thu");
        d.mediaPreview = true;
        d.senderName = "You";
        allDialogs.add(d);

        d = new DialogItem(15, "Julia", "Thanks! 🙏", "12.10.24");
        allDialogs.add(d);

        d = new DialogItem(16, "Kate", "recording voice", "11.10.24");
        d.message = "ok";
        d.typing = "recording voice";
        d.typingType = 1;
        allDialogs.add(d);
    }

    private static Bitmap gradientBitmap(int c1, int c2) {
        int size = AndroidUtilities.dp(54);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, size, size, c1, c2, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, size, size, paint);
        paint.setShader(null);
        paint.setColor(0x55FFFFFF);
        canvas.drawCircle(size * 0.5f, size * 0.42f, size * 0.18f, paint);
        canvas.drawCircle(size * 0.5f, size * 1.05f, size * 0.38f, paint);
        return bitmap;
    }

    private static Drawable gradientAvatar(int c1, int c2, String symbol) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas canvas) {
                android.graphics.Rect b = getBounds();
                paint.setShader(new LinearGradient(b.left, b.top, b.left, b.bottom, c1, c2, Shader.TileMode.CLAMP));
                canvas.drawRect(b, paint);
                textPaint.setColor(0xFFFFFFFF);
                textPaint.setTextSize(b.height() * 0.45f);
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(symbol, b.centerX(), b.centerY() + b.height() * 0.16f, textPaint);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final Context context;
        private final ArrayList<DialogItem> dialogs;

        Adapter(Context context, ArrayList<DialogItem> dialogs) {
            this.context = context;
            this.dialogs = dialogs;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return dialogs.get(position).id;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            DialogCell cell = new DialogCell(context);
            cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            DialogCell cell = (DialogCell) holder.itemView;
            DialogItem item = dialogs.get(position);
            DialogItem next = position + 1 < dialogs.size() ? dialogs.get(position + 1) : null;
            // upstream DialogsAdapter: full-width divider under the last pinned row
            cell.useSeparator = position != getItemCount() - 1;
            cell.fullSeparator = (item.pinned || item.isFolder) && next != null && !next.pinned;
            cell.setDialog(item, false);
            cell.setChecked(selected.contains(item.id), false);
            cell.onReorderStateChanged(actionMode.isShowing() && pinnedCount() > 1, false);
        }

        @Override
        public int getItemCount() {
            return dialogs.size();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        // upstream DialogsActivity.getThemeDescriptions (DialogCell part)
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        arrayList.addAll(actionMode.getThemeDescriptions());
        arrayList.addAll(floatingButton.getThemeDescriptions());
        if (drawerMenu != null) {
            arrayList.addAll(drawerMenu.getThemeDescriptions());
        }
        ThemeDescription.ThemeDescriptionDelegate tabsDelegate = () -> {
            filterTabsView.getTabsContainer().invalidateViews();
            filterTabsView.invalidate();
        };
        arrayList.add(new ThemeDescription(null, 0, null, null, null, tabsDelegate, Theme.key_actionBarTabLine));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, tabsDelegate, Theme.key_actionBarTabActiveText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, tabsDelegate, Theme.key_actionBarTabUnactiveText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, tabsDelegate, Theme.key_actionBarTabSelector));

        for (Page page : pages) {
            RecyclerListView list = page.listView;
            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_countPaint, null, null, Theme.key_chats_unreadCounter));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_countGrayPaint, null, null, Theme.key_chats_unreadCounterMuted));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_countTextPaint, null, null, Theme.key_chats_unreadCounterText));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_lockDrawable}, null, Theme.key_chats_secretIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_pinnedDrawable, Theme.dialogs_reorderDrawable}, null, Theme.key_chats_pinnedIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Paint[]{Theme.dialogs_namePaint[0], Theme.dialogs_namePaint[1], Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Paint[]{Theme.dialogs_nameEncryptedPaint[0], Theme.dialogs_nameEncryptedPaint[1], Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_messagePaint[1], null, null, Theme.key_chats_message_threeLines));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_messagePaint[0], null, null, Theme.key_chats_message));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_messageNamePaint, null, null, Theme.key_chats_nameMessage_threeLines));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_draft));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, Theme.dialogs_messagePrintingPaint, null, null, Theme.key_chats_actionMessage));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_timePaint, null, null, Theme.key_chats_date));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_pinnedPaint, null, null, Theme.key_chats_pinnedOverlay));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_tabletSeletedPaint, null, null, Theme.key_chats_tabletSelectedOverlay));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_checkDrawable}, null, Theme.key_chats_sentCheck));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_checkReadDrawable, Theme.dialogs_halfCheckDrawable}, null, Theme.key_chats_sentReadCheck));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_clockDrawable}, null, Theme.key_chats_sentClock));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_errorPaint, null, null, Theme.key_chats_sentError));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_errorDrawable}, null, Theme.key_chats_sentErrorIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_verifiedCheckDrawable}, null, Theme.key_chats_verifiedCheck));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_verifiedDrawable}, null, Theme.key_chats_verifiedBackground));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_muteDrawable}, null, Theme.key_chats_muteIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_mentionDrawable}, null, Theme.key_chats_mentionIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_archivePinBackground));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_archiveBackground));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_onlineCircle));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_CHECKBOX, new Class[]{DialogCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{DialogCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow1", Theme.key_avatar_backgroundArchived));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow2", Theme.key_avatar_backgroundArchived));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Box2", Theme.key_avatar_text));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Box1", Theme.key_avatar_text));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_pinArchiveDrawable}, "Arrow", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_pinArchiveDrawable}, "Line", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unpinArchiveDrawable}, "Arrow", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unpinArchiveDrawable}, "Line", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Arrow", Theme.key_chats_archiveBackground));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Box2", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Box1", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Arrow1", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Arrow2", Theme.key_chats_archivePinBackground));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Box2", Theme.key_chats_archiveIcon));
            arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Box1", Theme.key_chats_archiveIcon));
        }
        ThemeDescription.ThemeDescriptionDelegate pullDelegate = () -> {
            for (Page page : pages) {
                page.listView.updatePullColors();
                page.listView.invalidate();
            }
        };
        arrayList.add(new ThemeDescription(null, 0, null, null, null, pullDelegate, Theme.key_chats_archivePullDownBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, pullDelegate, Theme.key_chats_archivePullDownBackgroundActive));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, pullDelegate, Theme.key_avatar_backgroundArchivedHidden));
        return arrayList;
    }
}
