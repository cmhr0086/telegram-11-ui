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
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** File selection through the host's system picker; no filesystem enumeration. */
public class ChatAttachAlertDocumentLayout extends FrameLayout {
    private final ArrayList<Attachment> files = new ArrayList<>();
    private final LinkedHashMap<String, Attachment> selected = new LinkedHashMap<>();
    private final Attachment.SelectionListener listener;
    private final Theme.ResourcesProvider colors;
    private final RecyclerListView list;
    private final TextView empty;
    private final Adapter adapter = new Adapter();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean destroyed;
    private int generation;

    public ChatAttachAlertDocumentLayout(Context context, Theme.ResourcesProvider colors,
                                         Attachment.SelectionListener listener) {
        super(context);
        this.colors = colors; this.listener = listener;
        TextView choose = new TextView(context);
        choose.setText(R.string.tgui_attach_files_pick);
        choose.setTextSize(16);
        choose.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        choose.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        choose.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, colors));
        choose.setBackground(Theme.getSelectorDrawable(false));
        choose.setOnClickListener(v -> {
            Context current = getContext();
            while (current instanceof android.content.ContextWrapper && !(current instanceof Activity))
                current = ((android.content.ContextWrapper) current).getBaseContext();
            if (current instanceof Activity) pickFiles((Activity) current);
        });
        addView(choose, LayoutHelper.createFrame(-1, 56, Gravity.TOP));
        list = new RecyclerListView(context, colors);
        list.setLayoutManager(new LinearLayoutManager(context)); list.setAdapter(adapter);
        list.setPadding(0, 0, 0, AndroidUtilities.dp(48)); list.setClipToPadding(false);
        addView(list, LayoutHelper.createFrame(-1, -1, Gravity.FILL, 0, 56, 0, 0));
        empty = new TextView(context); empty.setGravity(Gravity.CENTER); empty.setTextSize(14);
        empty.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, colors)); empty.setText(R.string.tgui_attach_files_empty);
        empty.setClickable(false);
        addView(empty, LayoutHelper.createFrame(-1, -1, Gravity.FILL, 0, 56, 0, 0));
        list.setOnItemClickListener((view, position) -> {
            if (destroyed || position < 0 || position >= files.size()) return;
            Attachment file = files.get(position); String key = key(file);
            if (selected.containsKey(key)) selected.remove(key); else selected.put(key, file);
            adapter.notifyItemChanged(position); notifySelection();
        });
        refreshEmpty();
    }

    public void pickFiles(Activity activity) {
        if (destroyed || activity == null) return;
        final int request = ++generation;
        AttachmentAccess.pickFiles(activity, values -> main.post(() -> {
            if (destroyed || generation != request || values == null || values.isEmpty()) return;
            replaceFiles(values, true);
        }));
    }
    public void setFiles(List<Attachment> values) { if (!destroyed) { generation++; replaceFiles(values, false); } }
    private void replaceFiles(List<Attachment> values, boolean selectAll) {
        LinkedHashMap<String, Attachment> unique = new LinkedHashMap<>();
        if (values != null) for (Attachment file : values)
            if (file != null && file.type == Attachment.Type.FILE && file.uri != null) unique.put(key(file), file);
        files.clear(); files.addAll(unique.values());
        selected.clear(); if (selectAll) selected.putAll(unique);
        adapter.notifyDataSetChanged(); refreshEmpty(); notifySelection();
    }
    private static String key(Attachment file) { return file.uri.toString(); }
    private void refreshEmpty() { empty.setVisibility(files.isEmpty() ? VISIBLE : GONE); }
    private void notifySelection() { if (listener != null && !destroyed) listener.onSelectionChanged(new ArrayList<>(selected.values())); }
    public void onShow() { }
    public void onHide() { }
    public void onDestroy() { destroyed = true; generation++; selected.clear(); files.clear(); adapter.notifyDataSetChanged(); }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemCount() { return files.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) { return new RecyclerListView.Holder(new FileRow(parent.getContext())); }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Attachment item = files.get(position); ((FileRow) holder.itemView).bind(item, selected.containsKey(key(item)));
        }
    }
    private final class FileRow extends FrameLayout {
        final TextView title, subtitle; final CheckBox2 check;
        FileRow(Context context) {
            super(context);
            title = label(context, 16, Theme.key_dialogTextBlack);
            subtitle = label(context, 13, Theme.key_dialogTextGray2);
            addView(title, LayoutHelper.createFrame(-1, 20, Gravity.TOP | Gravity.START, 16, 8, 48, 0));
            addView(subtitle, LayoutHelper.createFrame(-1, 20, Gravity.TOP | Gravity.START, 16, 30, 48, 0));
            check = new CheckBox2(context, 21, colors);
            check.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            check.setDrawUnchecked(true);
            addView(check, LayoutHelper.createFrame(24, 24, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 16, 0));
        }
        private TextView label(Context context, int size, int key) {
            TextView text = new TextView(context); text.setTextSize(size); text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END); text.setTextColor(Theme.getColor(key, colors)); return text;
        }
        void bind(Attachment file, boolean checked) {
            title.setText(file.name); subtitle.setText(android.text.format.Formatter.formatShortFileSize(getContext(), file.size));
            check.setChecked(checked, false); setSelected(checked); setContentDescription(file.name);
        }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
        }
    }
}
