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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.GridLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.ContentPreviewViewer;
import static org.telegram.messenger.AndroidUtilities.dp;

/** Local pack branch of upstream StickersAlert; installation/account/network logic removed. */
public class StickersAlert extends BottomSheet {
    public interface Delegate extends ContentPreviewViewer.Delegate { }
    private final StickerSet stickerSet;
    private final Delegate stickerDelegate;
    private final LocalMediaView stickerImageView;
    private final FrameLayout stickerPreviewLayout;
    private final RecyclerListView gridView;
    private final ContentPreviewViewer.Binding previewBinding;
    private Sticker selectedSticker;

    public StickersAlert(Context context, StickerSet set, Delegate delegate, Theme.ResourcesProvider colors) {
        super(context, false, colors);
        if (set == null || delegate == null) throw new IllegalArgumentException("set and delegate required");
        stickerSet = set; stickerDelegate = delegate;
        setApplyTopPadding(false); setApplyBottomPadding(false);
        // Upstream normal pack: five columns, 82dp items and 48dp bars (737–753,1089,1132–1150).
        FrameLayout content = new FrameLayout(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int rows = Math.max(3, (int) Math.ceil(stickerSet.stickers.size() / 5f));
                int wanted = dp(96 + rows * 82);
                int cap = Math.min(MeasureSpec.getSize(heightSpec), AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.min(wanted, cap), MeasureSpec.EXACTLY));
            }
        };
        content.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, colors));
        setCustomView(content);
        gridView = new RecyclerListView(context);
        gridView.setLayoutManager(new GridLayoutManager(context, 5));
        gridView.setPadding(dp(10), 0, dp(10), dp(8));
        gridView.setClipToPadding(false);
        gridView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
            @Override public int getItemCount() { return stickerSet.stickers.size(); }
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                StickerEmojiCell cell = new StickerEmojiCell(context);
                cell.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(82)));
                return new RecyclerListView.Holder(cell);
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((StickerEmojiCell) holder.itemView).setSticker(stickerSet.stickers.get(position));
            }
        });
        content.addView(gridView, LayoutHelper.createFrame(-1, -1, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 48));
        TextView titleTextView = new TextView(context);
        titleTextView.setLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, colors));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setPadding(dp(18), dp(6), dp(18), dp(6));
        titleTextView.setGravity(Gravity.CENTER_VERTICAL);
        titleTextView.setText(stickerSet.title);
        content.addView(titleTextView, LayoutHelper.createFrame(-1, 48, Gravity.TOP));
        TextView close = button(context, context.getString(R.string.Cancel), colors);
        close.setOnClickListener(v -> dismiss());
        content.addView(close, LayoutHelper.createFrame(-1, 48, Gravity.BOTTOM));
        if (set.stickers.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.tgui_no_stickers); empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Theme.getColor(Theme.key_dialogTextGray, colors));
            content.addView(empty, LayoutHelper.createFrame(-1, -1, Gravity.TOP, 0, 48, 0, 48));
        }
        stickerPreviewLayout = new FrameLayout(context);
        stickerPreviewLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, colors));
        stickerPreviewLayout.setVisibility(View.GONE);
        content.addView(stickerPreviewLayout, LayoutHelper.createFrame(-1, -1));
        stickerPreviewLayout.setOnClickListener(v -> hidePreview());
        stickerImageView = new LocalMediaView(context);
        stickerPreviewLayout.addView(stickerImageView, LayoutHelper.createFrame(-1, -1, Gravity.CENTER, 48, 48, 48, 48));
        TextView previewSendButton = button(context, context.getString(R.string.Send), colors);
        previewSendButton.setPadding(dp(29), 0, dp(29), 0);
        stickerPreviewLayout.addView(previewSendButton, LayoutHelper.createFrame(-1, 48, Gravity.BOTTOM));
        previewSendButton.setOnClickListener(v -> {
            if (selectedSticker != null) { Sticker selected = selectedSticker; dismiss(); stickerDelegate.onSendSticker(selected); }
        });
        gridView.setOnItemClickListener((view, position) -> {
            selectedSticker = stickerSet.stickers.get(position);
            stickerImageView.setSticker(selectedSticker);
            stickerPreviewLayout.setVisibility(View.VISIBLE);
            stickerPreviewLayout.setAlpha(0f);
            stickerPreviewLayout.animate().alpha(1f).setDuration(200).start();
        });
        previewBinding = ContentPreviewViewer.bind(gridView, View::getTag, delegate, colors);
    }
    private TextView button(Context context, CharSequence text, Theme.ResourcesProvider colors) {
        TextView view = new TextView(context);
        view.setText(text); view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, colors));
        view.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_dialogBackground, colors), Theme.getColor(Theme.key_listSelector, colors)));
        view.setPadding(dp(18), 0, dp(18), 0);
        view.setTypeface(AndroidUtilities.bold()); view.setGravity(Gravity.CENTER);
        return view;
    }
    private void hidePreview() {
        stickerPreviewLayout.animate().cancel(); stickerPreviewLayout.setVisibility(View.GONE);
        stickerImageView.setSticker(null); selectedSticker = null;
    }
    @Override public void onBackPressed() {
        if (stickerPreviewLayout.getVisibility() == View.VISIBLE) hidePreview(); else super.onBackPressed();
    }
    @Override public void dismiss() {
        hidePreview(); previewBinding.close(); super.dismiss();
    }
}
