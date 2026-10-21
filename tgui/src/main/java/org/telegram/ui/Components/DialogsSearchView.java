/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.DialogItem;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HintDialogCell;
import org.telegram.ui.Cells.ProfileSearchCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * tgui: the chat-list search page — upstream SearchViewPager's dialogs page with
 * DialogsSearchAdapter, rewritten on {@link DialogItem}s supplied by the host.
 * <p>
 * Empty query: the "People" row of {@link HintDialogCell}s and the "Recent" section
 * ({@link GraySectionCell} with "Clear") of {@link ProfileSearchCell}s.
 * A query: "Chats" and "Global search" ({@link ProfileSearchCell}, match highlighted in the name)
 * and "Messages" ({@link DialogCell} rows, match highlighted in the text), or the upstream
 * "No results" empty view.
 * <p>
 * Results come from {@link Delegate#onSearch}; the host answers through the callback, on the UI
 * thread, whenever it is ready (answers for an outdated query are dropped).
 */
public class DialogsSearchView extends FrameLayout {

    /**
     * Search results for one query. Chat rows highlight the query words in the name without touching
     * the items (so the chat list's own items can be reused); message rows are search-only items:
     * those with no {@link DialogItem#highlightedWords} get the query words.
     */
    public static class Results {
        public final ArrayList<DialogItem> chats = new ArrayList<>();
        public final ArrayList<DialogItem> globalChats = new ArrayList<>();
        public final ArrayList<DialogItem> messages = new ArrayList<>();

        public boolean isEmpty() {
            return chats.isEmpty() && globalChats.isEmpty() && messages.isEmpty();
        }
    }

    public interface ResultsCallback {
        void onResults(Results results);
    }

    public interface Delegate {
        /** Search {@code query} (never empty); call {@code callback} on the UI thread when done. */
        void onSearch(String query, ResultsCallback callback);

        /** A chat row (chats, global, recent or the People row) was tapped. */
        void onChatClick(DialogItem chat);

        /** A message row was tapped. */
        void onMessageClick(DialogItem message);

        /** Upstream needClearList: "Clear" next to "Recent" (upstream asks with an alert first). */
        default void onClearRecent() {
        }

        /** Upstream needRemoveHint: long press on a People avatar. */
        default void onRemoveHint(DialogItem hint) {
        }
    }

    private static final int VIEW_TYPE_PROFILE_CELL = 0;
    private static final int VIEW_TYPE_GRAY_SECTION = 1;
    private static final int VIEW_TYPE_DIALOG_CELL = 2;
    private static final int VIEW_TYPE_CATEGORY_LIST = 6;

    private final RecyclerListView searchListView;
    private final SearchAdapter adapter;
    private final LinearLayoutManager layoutManager;
    private final EmptyView emptyView;
    private Delegate delegate;

    private final ArrayList<DialogItem> hints = new ArrayList<>();
    private final ArrayList<DialogItem> recent = new ArrayList<>();
    private Results results;
    private String lastQuery = "";
    private ArrayList<String> lastQueryWords;
    private int searchId;
    private boolean searchWas;
    private RecyclerListView innerListView;

    public DialogsSearchView(Context context) {
        super(context);

        searchListView = new RecyclerListView(context);
        searchListView.setPivotY(0);
        searchListView.setAdapter(adapter = new SearchAdapter(context));
        searchListView.setVerticalScrollBarEnabled(true);
        searchListView.setInstantClick(true);
        searchListView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        searchListView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        searchListView.setItemAnimator(null);
        searchListView.setGlowColor(Theme.getColor(Theme.key_actionBarDefault));
        searchListView.setOnItemClickListener((view, position) -> {
            if (delegate == null) {
                return;
            }
            if (view instanceof ProfileSearchCell) {
                delegate.onChatClick(((ProfileSearchCell) view).getDialog());
            } else if (view instanceof DialogCell) {
                delegate.onMessageClick(((DialogCell) view).getDialog());
            }
        });
        searchListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(recyclerView.findFocus() != null ? recyclerView.findFocus() : getRootView().findFocus());
                }
            }
        });
        addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new EmptyView(context);
        emptyView.setVisibility(GONE);
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public RecyclerListView getSearchListView() {
        return searchListView;
    }

    /** Upstream MediaDataController.hints (the "People" row) and the recent searches, shown for an empty query. */
    public void setRecent(List<DialogItem> hints, List<DialogItem> recent) {
        this.hints.clear();
        if (hints != null) {
            this.hints.addAll(hints);
        }
        this.recent.clear();
        if (recent != null) {
            this.recent.addAll(recent);
        }
        if (innerListView != null && innerListView.getAdapter() != null) {
            innerListView.getAdapter().notifyDataSetChanged();
        }
        update();
    }

    /** Upstream DialogsSearchAdapter.hasRecentSearch. */
    public boolean hasRecentSearch() {
        return !recent.isEmpty() || !hints.isEmpty();
    }

    public String getQuery() {
        return lastQuery;
    }

    /** Upstream SearchViewPager.onTextChanged → DialogsSearchAdapter.searchDialogs. */
    public void onTextChanged(String text) {
        String query = text == null ? "" : text.trim();
        if (TextUtils.equals(query, lastQuery) && (query.isEmpty() || results != null)) {
            return;
        }
        lastQuery = query;
        int id = ++searchId;
        if (query.isEmpty()) {
            searchWas = false;
            results = null;
            lastQueryWords = null;
            update();
            return;
        }
        searchWas = true;
        lastQueryWords = queryWords(query);
        if (delegate != null) {
            delegate.onSearch(query, r -> {
                if (id != searchId) {
                    return;
                }
                results = r;
                if (r != null) {
                    // chat rows get the words through the cell; message rows are search-only items
                    applyWords(r.messages);
                }
                update();
                layoutManager.scrollToPositionWithOffset(0, 0);
            });
        }
    }

    /** Upstream SearchViewPager.clear: back to the recent page, scrolled to the top. */
    public void clear() {
        searchId++;
        lastQuery = "";
        lastQueryWords = null;
        results = null;
        searchWas = false;
        update();
        layoutManager.scrollToPositionWithOffset(0, 0);
    }

    private void applyWords(ArrayList<DialogItem> items) {
        for (int i = 0; i < items.size(); i++) {
            DialogItem item = items.get(i);
            if (item.highlightedWords == null) {
                item.highlightedWords = lastQueryWords;
            }
        }
    }

    private static ArrayList<String> queryWords(String query) {
        ArrayList<String> words = new ArrayList<>();
        String lower = query.toLowerCase();
        words.add(lower);
        for (String w : lower.split("\\s+")) {
            if (!w.isEmpty() && !words.contains(w)) {
                words.add(w);
            }
        }
        return words;
    }

    private void update() {
        adapter.notifyDataSetChanged();
        boolean empty = searchWas && results != null && results.isEmpty();
        if (empty) {
            emptyView.subtitle.setText(LocaleController.formatString(R.string.NoResultFoundFor2, lastQuery));
        }
        if (empty != (emptyView.getVisibility() == VISIBLE)) {
            emptyView.setVisibility(empty ? VISIBLE : GONE);
            if (empty) {
                emptyView.playAnimation();
            }
        }
    }

    public void updateColors() {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        searchListView.setGlowColor(Theme.getColor(Theme.key_actionBarDefault));
        emptyView.updateColors();
        for (int i = 0; i < searchListView.getChildCount(); i++) {
            updateChildColors(searchListView.getChildAt(i));
        }
        // recycled / cached holders are rebound on the next notify
        adapter.notifyDataSetChanged();
    }

    private void updateChildColors(View child) {
        if (child instanceof GraySectionCell) {
            ((GraySectionCell) child).updateColors();
        } else if (child instanceof ProfileSearchCell) {
            child.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            ((ProfileSearchCell) child).updateColors();
        } else if (child instanceof DialogCell) {
            child.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            ((DialogCell) child).setDialog(((DialogCell) child).getDialog(), false);
        } else if (child instanceof RecyclerListView) {
            RecyclerListView list = (RecyclerListView) child;
            list.setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector));
            for (int j = 0; j < list.getChildCount(); j++) {
                View hint = list.getChildAt(j);
                if (hint instanceof HintDialogCell) {
                    ((HintDialogCell) hint).updateColors();
                }
            }
        }
        child.invalidate();
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::updateColors;
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_graySection));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_graySectionText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(searchListView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        arrayList.add(new ThemeDescription(searchListView, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_offlinePaint, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        arrayList.add(new ThemeDescription(searchListView, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_onlinePaint, null, null, Theme.key_windowBackgroundWhiteBlueText3));
        arrayList.add(new ThemeDescription(searchListView, 0, new Class[]{ProfileSearchCell.class}, null, new android.graphics.Paint[]{Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
        arrayList.add(new ThemeDescription(searchListView, 0, new Class[]{ProfileSearchCell.class}, null, new android.graphics.Paint[]{Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));
        return arrayList;
    }

    private boolean isRecentSearchDisplayed() {
        return !searchWas && !recent.isEmpty();
    }

    private boolean hasHints() {
        return !searchWas && !hints.isEmpty();
    }

    private int getRecentItemsCount() {
        return (isRecentSearchDisplayed() ? recent.size() + 1 : 0) + (hasHints() ? 1 : 0);
    }

    private static int sectionCount(ArrayList<DialogItem> list) {
        return list.isEmpty() ? 0 : list.size() + 1;
    }

    /** Upstream DialogsSearchAdapter, reduced to recent / chats / global / messages. */
    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        SearchAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            if (!searchWas) {
                return getRecentItemsCount();
            }
            if (results == null) {
                return 0;
            }
            return sectionCount(results.chats) + sectionCount(results.globalChats) + sectionCount(results.messages);
        }

        /** Section list for a searched query: {list, header string res}. */
        private ArrayList<DialogItem> sectionAt(int[] position) {
            int i = position[0];
            ArrayList<DialogItem>[] lists = new ArrayList[]{results.chats, results.globalChats, results.messages};
            for (ArrayList<DialogItem> list : lists) {
                int c = sectionCount(list);
                if (i < c) {
                    position[0] = i;
                    return list;
                }
                i -= c;
            }
            position[0] = -1;
            return null;
        }

        @Override
        public int getItemViewType(int i) {
            if (!searchWas) {
                int offset = hasHints() ? 1 : 0;
                if (i < offset) {
                    return VIEW_TYPE_CATEGORY_LIST;
                }
                if (i == offset) {
                    return VIEW_TYPE_GRAY_SECTION;
                }
                return VIEW_TYPE_PROFILE_CELL;
            }
            int[] pos = {i};
            ArrayList<DialogItem> list = sectionAt(pos);
            if (pos[0] == 0) {
                return VIEW_TYPE_GRAY_SECTION;
            }
            return list == results.messages ? VIEW_TYPE_DIALOG_CELL : VIEW_TYPE_PROFILE_CELL;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type != VIEW_TYPE_GRAY_SECTION && type != VIEW_TYPE_CATEGORY_LIST;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_PROFILE_CELL:
                    view = new ProfileSearchCell(mContext);
                    break;
                case VIEW_TYPE_GRAY_SECTION:
                    view = new GraySectionCell(mContext);
                    break;
                case VIEW_TYPE_DIALOG_CELL:
                    view = new DialogCell(mContext);
                    break;
                case VIEW_TYPE_CATEGORY_LIST:
                default:
                    RecyclerListView horizontalListView = new RecyclerListView(mContext) {
                        @Override
                        public boolean onInterceptTouchEvent(MotionEvent e) {
                            if (getParent() != null && getParent().getParent() != null) {
                                getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1));
                            }
                            return super.onInterceptTouchEvent(e);
                        }
                    };
                    horizontalListView.setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector));
                    horizontalListView.setTag(9);
                    horizontalListView.setItemAnimator(null);
                    horizontalListView.setLayoutAnimation(null);
                    LinearLayoutManager layoutManager = new LinearLayoutManager(mContext) {
                        @Override
                        public boolean supportsPredictiveItemAnimations() {
                            return false;
                        }
                    };
                    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    horizontalListView.setLayoutManager(layoutManager);
                    horizontalListView.setAdapter(new CategoryAdapterRecycler(mContext));
                    horizontalListView.setOnItemClickListener((view1, position) -> {
                        if (delegate != null && view1 instanceof HintDialogCell) {
                            delegate.onChatClick(((HintDialogCell) view1).getDialog());
                        }
                    });
                    horizontalListView.setOnItemLongClickListener((view12, position) -> {
                        if (delegate != null && view12 instanceof HintDialogCell) {
                            delegate.onRemoveHint(((HintDialogCell) view12).getDialog());
                        }
                        return true;
                    });
                    view = horizontalListView;
                    innerListView = horizontalListView;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_PROFILE_CELL: {
                    ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    DialogItem item;
                    boolean isRecent;
                    boolean last;
                    if (!searchWas) {
                        int i = position - (hasHints() ? 2 : 1);
                        item = recent.get(i);
                        isRecent = true;
                        last = i == recent.size() - 1;
                    } else {
                        int[] pos = {position};
                        ArrayList<DialogItem> list = sectionAt(pos);
                        item = list.get(pos[0] - 1);
                        isRecent = false;
                        last = pos[0] == list.size();
                    }
                    cell.useSeparator = !last;
                    cell.setHighlightedWords(isRecent ? null : lastQueryWords);
                    cell.setData(item, null, null, isRecent, item.avatarType == DialogItem.AVATAR_SAVED);
                    cell.setChecked(false, false);
                    break;
                }
                case VIEW_TYPE_GRAY_SECTION: {
                    GraySectionCell cell = (GraySectionCell) holder.itemView;
                    cell.updateColors();
                    if (!searchWas) {
                        cell.setText(LocaleController.getString(R.string.Recent), LocaleController.getString(R.string.ClearButton), v -> {
                            if (delegate != null) {
                                delegate.onClearRecent();
                            }
                        });
                    } else {
                        int[] pos = {position};
                        ArrayList<DialogItem> list = sectionAt(pos);
                        if (list == results.chats) {
                            cell.setText(LocaleController.getString(R.string.SearchAllChatsShort));
                        } else if (list == results.globalChats) {
                            cell.setText(LocaleController.getString(R.string.GlobalSearch));
                        } else {
                            cell.setText(LocaleController.getString(R.string.SearchMessages));
                        }
                    }
                    break;
                }
                case VIEW_TYPE_DIALOG_CELL: {
                    DialogCell cell = (DialogCell) holder.itemView;
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    int[] pos = {position};
                    ArrayList<DialogItem> list = sectionAt(pos);
                    cell.useSeparator = pos[0] != list.size();
                    cell.setDialog(list.get(pos[0] - 1), false);
                    break;
                }
                case VIEW_TYPE_CATEGORY_LIST: {
                    RecyclerListView recyclerListView = (RecyclerListView) holder.itemView;
                    ((CategoryAdapterRecycler) recyclerListView.getAdapter()).setIndex(position / 2);
                    break;
                }
            }
        }
    }

    /** Upstream DialogsSearchAdapter.CategoryAdapterRecycler: the horizontal "People" row. */
    private class CategoryAdapterRecycler extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        CategoryAdapterRecycler(Context context) {
            mContext = context;
        }

        public void setIndex(int value) {
            notifyDataSetChanged();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            HintDialogCell cell = new HintDialogCell(mContext, false, null);
            cell.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(80), AndroidUtilities.dp(86)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            HintDialogCell cell = (HintDialogCell) holder.itemView;
            cell.setDialog(hints.get(position), true, null);
        }

        @Override
        public int getItemCount() {
            return hints.size();
        }
    }

    /**
     * Upstream StickerEmptyView (STICKER_TYPE_SEARCH) layout: 117dp sticker, bold 20sp title,
     * 14sp subtitle. The upstream search duck is a server sticker, so this plays the bundled
     * utyan_empty Lottie instead.
     */
    private static class EmptyView extends FrameLayout {

        final RLottieImageView stickerView;
        final TextView title;
        final TextView subtitle;

        EmptyView(Context context) {
            super(context);
            setClickable(true);
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            stickerView = new RLottieImageView(context);
            stickerView.setAnimation(R.raw.utyan_empty, 117, 117);
            stickerView.setOnClickListener(v -> playAnimation());

            title = new TextView(context);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setGravity(Gravity.CENTER);
            title.setText(LocaleController.getString(R.string.NoResult));

            subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);

            linearLayout.addView(stickerView, LayoutHelper.createLinear(117, 117, Gravity.CENTER_HORIZONTAL));
            linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
            linearLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 46, 0, 46, 30));
            updateColors();
        }

        void playAnimation() {
            RLottieDrawable drawable = stickerView.getAnimatedDrawable();
            if (drawable != null) {
                drawable.setCurrentFrame(0, false);
                stickerView.playAnimation();
            }
        }

        void updateColors() {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
    }
}
