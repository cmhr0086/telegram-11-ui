package com.example.tguihost;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
// library resources live in the library R (host has nonTransitiveRClass=true by default)
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.DialogItem;
import org.telegram.ui.Components.DialogsListView;
import org.telegram.ui.Components.DialogsSwipeController;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.TelegramUI;

import java.util.ArrayList;

/** Chat list: DialogCell rows bound to DialogItem, swipe actions, folder tabs. */
public class ChatListFragment extends BaseFragment {

    private static final int MENU_NIGHT = 1;
    private static final String[] TABS = {"All", "Unread", "Groups"};

    private final ArrayList<DialogItem> all = new ArrayList<>();
    private final ArrayList<DialogItem> shown = new ArrayList<>();
    private DialogsListView listView;
    private Adapter adapter;
    private FilterTabsView tabs;
    private int currentTab;

    @Override
    public boolean onFragmentCreate() {
        // not in createView(): TelegramActivity builds the root fragment view twice at startup
        fillDialogs();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle("tgui host");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == MENU_NIGHT) {
                    TelegramUI.toggleDayNight(actionBar);
                }
            }
        });
        actionBar.createMenu().addItem(MENU_NIGHT, R.drawable.msg_theme);

        createTabs(context);

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = frame;

        listView = new DialogsListView(context);
        listView.setAdapter(adapter = new Adapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof DialogCell) {
                presentFragment(new ChatFragment(((DialogCell) view).getDialog().title.toString()));
            }
        });
        listView.setSwipeDelegate(new DialogsSwipeController.Delegate() {
            @Override
            public int getSwipeAction(DialogCell cell) {
                return DialogCell.SWIPE_ACTION_MUTE;
            }

            @Override
            public void onSwipeAction(DialogCell cell, int action) {
                DialogItem d = cell.getDialog();
                d.muted = !d.muted;
                cell.setDialog(d, true);
            }
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void createTabs(Context context) {
        tabs = new FilterTabsView(context);
        tabs.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override
            public void onPageSelected(FilterTabsView.Tab tab, boolean forward) {
                currentTab = tab.id;
                applyFilter();
            }

            @Override
            public void onPageScrolled(float progress) {
            }

            @Override
            public void onSamePageSelected() {
                listView.scrollToTop(true);
            }

            @Override
            public int getTabCounter(int tabId) {
                int n = 0;
                for (DialogItem d : all) {
                    if (matches(tabId, d) && d.unreadCount > 0) {
                        n++;
                    }
                }
                return n;
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
        for (int a = 0; a < TABS.length; a++) {
            tabs.addTab(a, a, TABS[a], a == 0, false);
        }
        tabs.finishAddingTabs(false);
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        actionBar.addView(tabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.BOTTOM));
    }

    private static boolean matches(int tab, DialogItem d) {
        if (tab == 1) {
            return d.unreadCount > 0;
        }
        if (tab == 2) {
            return d.senderName != null;
        }
        return true;
    }

    private void applyFilter() {
        shown.clear();
        for (DialogItem d : all) {
            if (matches(currentTab, d)) {
                shown.add(d);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void fillDialogs() {
        String[] names = {"Alice", "Bob", "Weekend Plans", "Carol", "Dev Team", "Dan", "Eve", "Book Club", "Frank", "Grace"};
        String[] texts = {"See you tomorrow ☕️", "Sure, sending the files", "Who brings the tent?", "ok 👍", "Build is green",
                "Call me later", "😂😂😂", "Next book: Dune", "Thanks!", "Where are you?"};
        for (int i = 0; i < names.length; i++) {
            DialogItem d = new DialogItem(i + 1, names[i], texts[i], (12 - i / 2) + ":" + (10 + i * 4));
            if (i % 3 == 2) {
                d.senderName = "Alice";
            }
            if (i % 4 == 0) {
                d.unreadCount = i + 1;
            }
            d.pinned = i == 0;
            all.add(d);
        }
        applyFilter();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        Adapter(Context context) {
            this.context = context;
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
            cell.useSeparator = position != getItemCount() - 1;
            cell.setDialog(shown.get(position), false);
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
