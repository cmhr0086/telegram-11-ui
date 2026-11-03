/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.GridLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import static org.telegram.messenger.AndroidUtilities.dp;

/** Photo grid branch of upstream ChatAttachAlertPhotoLayout, backed by MediaStore URI snapshots. */
public class ChatAttachAlertPhotoLayout extends FrameLayout {
    private final Attachment.SelectionListener listener;
    private final Theme.ResourcesProvider colors;
    private final RecyclerListView grid;
    private final GridLayoutManager layout;
    private final Adapter adapter = new Adapter();
    private final ArrayList<Attachment> items = new ArrayList<>();
    private final LinkedHashMap<String, Attachment> selected = new LinkedHashMap<>();
    private final TextView empty, access;
    private Activity activity;
    private AttachmentAccess.PermissionListener permissionListener;
    private long generation, snapshotGeneration;
    private boolean destroyed, attempted, active, permissionPending;
    private int itemSize;

    public ChatAttachAlertPhotoLayout(Context context, Theme.ResourcesProvider colors, Attachment.SelectionListener listener) {
        super(context); this.colors = colors; this.listener = listener;
        grid = new RecyclerListView(context);
        layout = new GridLayoutManager(context, 3); grid.setLayoutManager(layout);
        grid.setPadding(dp(6), dp(6), dp(6), dp(6)); grid.setClipToPadding(false);
        grid.setAdapter(adapter);
        grid.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(Rect out, View view, RecyclerView parent, RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                out.set(0, 0, position % layout.getSpanCount() == layout.getSpanCount() - 1 ? 0 : dp(5), dp(5));
            }
        });
        grid.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) return;
            Attachment item = items.get(position); String key = item.uri.toString();
            if (selected.containsKey(key)) selected.remove(key); else selected.put(key, item);
            adapter.notifyDataSetChanged(); publish();
        });
        addView(grid, LayoutHelper.createFrame(-1, -1, Gravity.FILL, 0, 40, 0, 0));
        access = new TextView(context); access.setGravity(Gravity.CENTER); access.setTextSize(14);
        access.setText(R.string.tgui_attach_gallery_access); access.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, colors));
        access.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, colors), 2));
        access.setOnClickListener(v -> {
            if (activity != null && !destroyed && !permissionPending) {
                permissionPending = true;
                AttachmentAccess.requestGalleryAccess(activity, () -> { permissionPending = false; load(false); });
            }
        });
        addView(access, LayoutHelper.createFrame(-1, 40, Gravity.TOP));
        empty = new TextView(context); empty.setText(R.string.tgui_attach_gallery_empty); empty.setGravity(Gravity.CENTER);
        empty.setTextColor(Theme.getColor(Theme.key_dialogTextGray, colors));
        addView(empty, LayoutHelper.createFrame(-1, -1, Gravity.FILL, 0, 40, 0, 0));
    }
    public void refresh(Activity activity, AttachmentAccess.PermissionListener permission) {
        this.activity = activity; permissionListener = permission;
        boolean request = !attempted; attempted = true; load(request);
    }
    private void load(boolean request) {
        if (activity == null || destroyed) return;
        empty.setText(R.string.tgui_attach_gallery_empty);
        final long token = ++generation;
        final long snapshotToken = snapshotGeneration;
        permissionPending = request && !AttachmentAccess.hasGalleryAccess(activity);
        AttachmentAccess.loadGallery(activity, new AttachmentAccess.GalleryResult() {
            @Override public void onResult(List<Attachment> values) { if (!destroyed && token == generation && snapshotToken == snapshotGeneration) applyItems(values); }
            @Override public void onError(Throwable error) {
                if (!destroyed && token == generation && snapshotToken == snapshotGeneration) empty.setText(R.string.tgui_attach_gallery_error);
            }
        }, (granted, partial) -> {
            permissionPending = false;
            if (!destroyed && token == generation) {
                access.setVisibility(!granted || partial ? VISIBLE : GONE);
                if (permissionListener != null) permissionListener.onPermissionResult(granted, partial);
            }
        }, request);
    }
    /** Host snapshots can also be supplied for restricted media providers and deterministic tests. */
    public void setItems(List<Attachment> values) {
        if (destroyed) return;
        snapshotGeneration++;
        applyItems(values);
    }
    private void applyItems(List<Attachment> values) {
        items.clear();
        if (values != null) for (Attachment item : values) if (item != null && item.uri != null
                && (item.type == Attachment.Type.PHOTO || item.type == Attachment.Type.VIDEO)) items.add(item);
        HashSet<String> available = new HashSet<>();
        for (Attachment item : items) available.add(item.uri.toString());
        Iterator<String> keys = selected.keySet().iterator();
        while (keys.hasNext()) if (!available.contains(keys.next())) keys.remove();
        empty.setVisibility(items.isEmpty() ? VISIBLE : GONE);
        grid.setVisibility(items.isEmpty() ? GONE : VISIBLE);
        adapter.notifyDataSetChanged(); publish();
    }
    public List<Attachment> getSelection() { return new ArrayList<>(selected.values()); }
    private void publish() { if (listener != null) listener.onSelectionChanged(getSelection()); }
    public void onShow() { active = true; publish(); }
    public void onHide() { active = false; }
    public void onDestroy() { destroyed = true; generation++; items.clear(); selected.clear(); grid.setAdapter(null); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        // Grants can change in settings while the sheet is backgrounded. Never prompt on resume.
        if (focused && active && attempted && !permissionPending) load(false);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int columns = AndroidUtilities.isTablet() || AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 4 : 3;
        if (layout.getSpanCount() != columns) layout.setSpanCount(columns);
        itemSize = Math.max(1, (MeasureSpec.getSize(widthSpec) - dp(12) - dp(5) * (columns - 1)) / columns);
        super.onMeasure(widthSpec, heightSpec);
    }
    private final class PhotoCell extends FrameLayout {
        final AttachmentThumbnailView image;
        final CheckBox2 check;
        final TextView duration;
        PhotoCell(Context context) {
            super(context);
            image = new AttachmentThumbnailView(context); image.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray, colors));
            addView(image, LayoutHelper.createFrame(-1, -1));
            // PhotoAttachPhotoCell's 24dp numbered selection control in its 42dp hit area.
            check = new CheckBox2(context, 24, colors); check.setDrawUnchecked(true);
            check.setColor(Theme.key_chat_attachCheckBoxBackground, Theme.key_chat_attachPhotoBackground, Theme.key_chat_attachCheckBoxCheck);
            addView(check, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.RIGHT, 0, 5, 5, 0));
            duration = new TextView(context); duration.setTextColor(0xffffffff); duration.setTextSize(12);
            duration.setGravity(Gravity.RIGHT); duration.setPadding(dp(4), 0, dp(4), 0);
            duration.setBackgroundColor(0x66000000);
            addView(duration, LayoutHelper.createFrame(-1, 18, Gravity.BOTTOM));
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY));
        }
    }
    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemCount() { return items.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            PhotoCell cell = new PhotoCell(getContext()); cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(cell);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Attachment item = items.get(position); PhotoCell cell = (PhotoCell) holder.itemView;
            cell.setTag(item); cell.setContentDescription(item.name); cell.image.setAttachment(item);
            int index = new ArrayList<>(selected.keySet()).indexOf(item.uri.toString());
            cell.check.setChecked(index, index >= 0, false);
            cell.duration.setVisibility(item.type == Attachment.Type.VIDEO ? VISIBLE : GONE);
            cell.duration.setText(String.format(Locale.US, "%d:%02d", item.duration / 60, item.duration % 60));
        }
    }
}
