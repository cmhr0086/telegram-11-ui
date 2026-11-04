/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.GridLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.Cells.StickerEmojiCell;

import java.util.ArrayList;
import java.util.List;

/** Host-backed sticker sections and GIF flow, adapted from EmojiView's upstream pages. */
public class StickerMediaPage extends FrameLayout {
    public interface Listener {
        void onStickerSelected(Sticker sticker);
        void onGifSelected(Gif gif);
        void onStickerSetRequested(StickerSet set);
        void onStickerFavoriteChanged(Sticker sticker, boolean favorite);
        void onGifRemoved(Gif gif);
    }
    private static final class Section {
        final CharSequence label;
        final StickerSet pack;
        final List<Sticker> stickers;
        final int icon;
        int position;
        Section(CharSequence label, StickerSet pack, List<Sticker> stickers, int icon) {
            this.label = label; this.pack = pack; this.stickers = stickers; this.icon = icon;
        }
    }
    private final boolean gifsPage;
    private final Theme.ResourcesProvider provider;
    private final Listener listener;
    private final RecyclerListView list;
    private final HorizontalScrollView tabsScroll;
    private final LinearLayout tabs;
    private final TextView empty;
    private final Adapter adapter = new Adapter();
    private final ArrayList<Object> rows = new ArrayList<>();
    private final ArrayList<Section> sections = new ArrayList<>();
    private final ArrayList<View> tabViews = new ArrayList<>();
    private final ArrayList<ImageView> tabIcons = new ArrayList<>();
    private final ArrayList<LocalMediaView> tabMedia = new ArrayList<>();
    private final ArrayList<Gif> gifs = new ArrayList<>();
    private final ArrayList<Sticker> favoriteSnapshot = new ArrayList<>();
    private ContentPreviewViewer.Binding previewBinding;
    private boolean pageActive;
    private GridLayoutManager stickerLayout;
    private ExtendedGridLayoutManager gifLayout;
    private int selectedSection = -1;

    public StickerMediaPage(Context context, boolean gifsPage, Theme.ResourcesProvider provider, Listener listener) {
        super(context);
        this.gifsPage = gifsPage;
        this.provider = provider;
        this.listener = listener;
        list = new RecyclerListView(context);
        list.setClipToPadding(false);
        list.setPadding(0, AndroidUtilities.dp(gifsPage ? 0 : 36), 0, AndroidUtilities.dp(44));
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setAdapter(adapter);
        if (gifsPage) configureGifLayout(); else configureStickerLayout(1);
        list.setOnItemClickListener((RecyclerListView.OnItemClickListener) (view, position) -> {
            if (position < 0 || position >= rows.size() || listener == null) return;
            Object item = rows.get(position);
            if (item instanceof Sticker) listener.onStickerSelected((Sticker) item);
            else if (item instanceof Gif) listener.onGifSelected((Gif) item);
            else if (item instanceof Section && ((Section) item).pack != null) listener.onStickerSetRequested(((Section) item).pack);
        });
        list.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recycler, int dx, int dy) {
                if (!gifsPage && stickerLayout != null) syncSection(stickerLayout.findFirstVisibleItemPosition());
            }
        });
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        empty = new TextView(context);
        empty.setGravity(Gravity.CENTER);
        empty.setText(gifsPage ? R.string.tgui_no_gifs : R.string.tgui_no_stickers);
        addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tabsScroll = new HorizontalScrollView(context);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(context);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabsScroll.addView(tabs, new android.widget.HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        if (!gifsPage) addView(tabsScroll, new LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(36), Gravity.TOP));
        updateColors();
    }

    private void configureStickerLayout(int columns) {
        stickerLayout = new GridLayoutManager(getContext(), columns);
        stickerLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return position >= 0 && position < rows.size() && rows.get(position) instanceof Section ? stickerLayout.getSpanCount() : 1;
            }
        });
        list.setLayoutManager(stickerLayout);
    }

    private void configureGifLayout() {
        gifLayout = new ExtendedGridLayoutManager(getContext(), 100, true) {
            @Override protected Size getSizeForItem(int i) {
                Gif gif = gifs.get(i);
                return new Size(gif.width, gif.height);
            }
            @Override protected int getFlowItemCount() { return gifs.size(); }
        };
        gifLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return Math.max(1, gifLayout.getSpanSizeForItem(position)); }
        });
        list.setLayoutManager(gifLayout);
    }

    public void setData(List<StickerSet> packs, List<Sticker> recent, List<Sticker> favorites, List<Gif> data) {
        rows.clear(); sections.clear(); gifs.clear();
        favoriteSnapshot.clear();
        if (favorites != null) for (Sticker sticker : favorites) if (sticker != null) favoriteSnapshot.add(sticker);
        if (gifsPage) {
            if (data != null) for (Gif gif : data) if (gif != null) gifs.add(gif);
            rows.addAll(gifs);
            // The upstream flow cache keys only item count and width; rebuild for same-sized changed data.
            configureGifLayout();
        } else {
            addSection(getContext().getString(R.string.tgui_favorites), null, favorites, R.drawable.emoji_tabs_faves);
            addSection(getContext().getString(R.string.tgui_sticker_recent), null, recent, R.drawable.msg_emoji_recent);
            if (packs != null) for (StickerSet pack : packs) if (pack != null) addSection(pack.title, pack, pack.stickers, 0);
            rebuildTabs();
        }
        selectedSection = -1;
        empty.setVisibility(rows.isEmpty() ? VISIBLE : GONE);
        list.setVisibility(rows.isEmpty() ? GONE : VISIBLE);
        adapter.notifyDataSetChanged();
        if (!rows.isEmpty() && !gifsPage) syncSection(0);
    }

    public void setPageActive(boolean active) {
        if (pageActive == active) return;
        pageActive = active;
        if (active && previewBinding == null) {
            previewBinding = ContentPreviewViewer.bind(list, View::getTag, new ContentPreviewViewer.Delegate() {
                @Override public void onSendSticker(Sticker sticker) { if (listener != null) listener.onStickerSelected(sticker); }
                @Override public void onSendGif(Gif gif) { if (listener != null) listener.onGifSelected(gif); }
                @Override public boolean isFavorite(Sticker sticker) {
                    for (Sticker favorite : favoriteSnapshot) if (favorite.id == sticker.id) return true;
                    return false;
                }
                @Override public void onFavoriteChanged(Sticker sticker, boolean favorite) { if (listener != null) listener.onStickerFavoriteChanged(sticker, favorite); }
                @Override public void onOpenSet(Sticker sticker) {
                    for (Section section : sections) if (section.pack != null) {
                        for (Sticker member : section.stickers) if (member.id == sticker.id) {
                            if (listener != null) listener.onStickerSetRequested(section.pack);
                            return;
                        }
                    }
                }
                @Override public void onGifRemoved(Gif gif) { if (listener != null) listener.onGifRemoved(gif); }
            }, provider);
        } else if (!active && previewBinding != null) {
            previewBinding.close(); previewBinding = null;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof StickerEmojiCell) ((StickerEmojiCell) child).setAnimating(active);
        }
        for (LocalMediaView media : tabMedia) media.setAnimating(active);
    }

    public void dispose() {
        if (previewBinding != null) { previewBinding.close(); previewBinding = null; }
        pageActive = false;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof StickerEmojiCell) ((StickerEmojiCell) child).setAnimating(false);
        }
        for (LocalMediaView media : tabMedia) media.setAnimating(false);
    }

    private void addSection(CharSequence name, StickerSet pack, List<Sticker> stickers, int icon) {
        if (stickers == null || stickers.isEmpty() && pack == null) return;
        Section section = new Section(name, pack, new ArrayList<>(stickers), icon);
        section.position = rows.size();
        sections.add(section); rows.add(section);
        for (Sticker sticker : stickers) if (sticker != null) rows.add(sticker);
    }

    private void rebuildTabs() {
        tabs.removeAllViews(); tabViews.clear(); tabIcons.clear(); tabMedia.clear();
        for (Section section : sections) {
            FrameLayout tab = new FrameLayout(getContext());
            tab.setContentDescription(section.label);
            tab.setFocusable(true);
            ImageView icon = null;
            if (section.pack != null && !section.stickers.isEmpty()) {
                LocalMediaView cover = new LocalMediaView(getContext());
                cover.setSticker(section.stickers.get(0));
                cover.setAnimating(pageActive);
                tab.addView(cover, new FrameLayout.LayoutParams(AndroidUtilities.dp(30), AndroidUtilities.dp(30), Gravity.CENTER));
                tabMedia.add(cover);
            } else {
                icon = new ImageView(getContext());
                icon.setScaleType(ImageView.ScaleType.CENTER);
                icon.setImageResource(section.icon != 0 ? section.icon : R.drawable.smiles_tab_stickers);
                tab.addView(icon, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            tab.setOnClickListener(v -> {
                list.stopScroll();
                stickerLayout.scrollToPositionWithOffset(section.position, 0);
                syncSection(section.position);
            });
            tabs.addView(tab, new LinearLayout.LayoutParams(AndroidUtilities.dp(36), LayoutParams.MATCH_PARENT));
            tabViews.add(tab);
            tabIcons.add(icon);
        }
    }

    private void syncSection(int position) {
        int selected = -1;
        for (int i = 0; i < sections.size(); i++) if (sections.get(i).position <= position) selected = i;
        if (selectedSection == selected) return;
        selectedSection = selected;
        updateTabColors();
        if (selected >= 0 && selected < tabViews.size()) {
            View tab = tabViews.get(selected);
            tabsScroll.smoothScrollTo(Math.max(0, tab.getLeft() - tabsScroll.getWidth() / 2 + tab.getWidth() / 2), 0);
        }
    }

    private int color(int key) { return Theme.getColor(key, provider); }
    public void updateColors() {
        int background = color(Theme.key_chat_emojiPanelBackground);
        tabsScroll.setBackgroundColor(background);
        empty.setTextColor(color(Theme.key_chat_emojiPanelStickerSetName));
        list.setGlowColor(background);
        updateTabColors();
        adapter.notifyDataSetChanged();
    }
    private void updateTabColors() {
        for (int i = 0; i < tabIcons.size(); i++) if (tabIcons.get(i) != null) tabIcons.get(i).setColorFilter(color(i == selectedSection ? Theme.key_chat_emojiPanelIconSelected : Theme.key_chat_emojiPanelIcon));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (!gifsPage && stickerLayout != null) {
            int columns = Math.max(1, MeasureSpec.getSize(widthSpec) / Math.max(1, AndroidUtilities.dp(72)));
            if (columns != stickerLayout.getSpanCount()) stickerLayout.setSpanCount(columns);
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int position) { return rows.get(position) instanceof Section ? 1 : 0; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            View view;
            if (type == 1) {
                TextView title = new TextView(getContext());
                title.setGravity(Gravity.CENTER_VERTICAL);
                title.setTextSize(14);
                title.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
                title.setLayoutParams(new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(36)));
                view = title;
            } else {
                StickerEmojiCell cell = new StickerEmojiCell(getContext());
                cell.setLayoutParams(new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(gifsPage ? 100 : 82)));
                view = cell;
            }
            return new RecyclerListView.Holder(view);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Object item = rows.get(position);
            holder.itemView.setTag(item);
            if (item instanceof Section) {
                TextView title = (TextView) holder.itemView;
                title.setText(((Section) item).label);
                title.setTextColor(color(Theme.key_chat_emojiPanelStickerSetName));
            } else if (item instanceof Sticker) ((StickerEmojiCell) holder.itemView).setSticker((Sticker) item);
            else ((StickerEmojiCell) holder.itemView).setGif((Gif) item);
            if (holder.itemView instanceof StickerEmojiCell) ((StickerEmojiCell) holder.itemView).setAnimating(pageActive);
        }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof StickerEmojiCell) ((StickerEmojiCell) holder.itemView).stopAnimation();
            super.onViewRecycled(holder);
        }
    }
}
