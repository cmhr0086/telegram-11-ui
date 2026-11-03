/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Host-provided contacts; row sizing follows upstream's 64dp UserCell. */
public class ChatAttachAlertContactsLayout extends FrameLayout {
    private final ArrayList<Attachment.Contact> contacts = new ArrayList<>();
    private final LinkedHashMap<Long, Attachment.Contact> selected = new LinkedHashMap<>();
    private final Attachment.SelectionListener listener;
    private final Theme.ResourcesProvider colors;
    private final RecyclerListView list;
    private final TextView empty;
    private final Adapter adapter = new Adapter();
    private boolean destroyed;

    public ChatAttachAlertContactsLayout(Context context, Theme.ResourcesProvider colors,
                                         Attachment.SelectionListener listener) {
        super(context);
        this.colors = colors; this.listener = listener;
        list = new RecyclerListView(context, colors);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);
        list.setPadding(0, 0, 0, AndroidUtilities.dp(48));
        list.setClipToPadding(false);
        addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = new TextView(context);
        empty.setGravity(Gravity.CENTER);
        empty.setTextSize(14);
        empty.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, colors));
        empty.setText(R.string.tgui_attach_contacts_empty);
        addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= contacts.size() || destroyed) return;
            Attachment.Contact contact = contacts.get(position);
            if (selected.containsKey(contact.id)) selected.remove(contact.id);
            else selected.put(contact.id, contact);
            adapter.notifyItemChanged(position);
            notifySelection();
        });
        refreshEmpty();
    }

    public void setContacts(List<Attachment.Contact> values) {
        if (destroyed) return;
        contacts.clear();
        LinkedHashMap<Long, Attachment.Contact> unique = new LinkedHashMap<>();
        if (values != null) for (Attachment.Contact contact : values) if (contact != null) unique.put(contact.id, contact);
        contacts.addAll(unique.values());
        selected.keySet().retainAll(unique.keySet());
        for (Long id : new ArrayList<>(selected.keySet())) selected.put(id, unique.get(id));
        adapter.notifyDataSetChanged(); refreshEmpty(); notifySelection();
    }

    private void refreshEmpty() { empty.setVisibility(contacts.isEmpty() ? VISIBLE : GONE); }
    private void notifySelection() {
        if (listener == null || destroyed) return;
        ArrayList<Attachment> result = new ArrayList<>();
        for (Attachment.Contact contact : selected.values()) result.add(Attachment.contact(contact));
        listener.onSelectionChanged(result);
    }
    public void onShow() { }
    public void onHide() { }
    public void onDestroy() { destroyed = true; selected.clear(); contacts.clear(); adapter.notifyDataSetChanged(); }

    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemCount() { return contacts.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ContactRow(parent.getContext()));
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((ContactRow) holder.itemView).bind(contacts.get(position), selected.containsKey(contacts.get(position).id));
        }
    }

    private final class ContactRow extends FrameLayout {
        final ImageView avatar;
        final TextView name, phone;
        final CheckBox2 check;
        ContactRow(Context context) {
            super(context);
            avatar = new ImageView(context);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundBlue, colors));
            avatar.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
            });
            avatar.setClipToOutline(true);
            addView(avatar, LayoutHelper.createFrame(46, 46, Gravity.TOP | Gravity.START, 14, 9, 0, 0));
            name = label(context, 16, Theme.key_dialogTextBlack);
            name.setTypeface(AndroidUtilities.bold());
            addView(name, LayoutHelper.createFrame(-1, 20, Gravity.TOP | Gravity.START, 72, 12, 28, 0));
            phone = label(context, 13, Theme.key_dialogTextGray2);
            addView(phone, LayoutHelper.createFrame(-1, 20, Gravity.TOP | Gravity.START, 72, 36, 28, 0));
            check = new CheckBox2(context, 21, colors);
            check.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            check.setDrawUnchecked(false); check.setDrawBackgroundAsArc(3);
            addView(check, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.START, 44, 37, 0, 0));
        }
        private TextView label(Context context, int size, int key) {
            TextView text = new TextView(context); text.setTextSize(size); text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END); text.setTextColor(Theme.getColor(key, colors)); return text;
        }
        void bind(Attachment.Contact contact, boolean checked) {
            avatar.setImageDrawable(contact.avatar);
            name.setText(contact.name);
            phone.setText(contact.phone.isEmpty() ? getContext().getString(R.string.tgui_attach_contact_unknown_phone) : contact.phone);
            check.setChecked(checked, false);
            setContentDescription(contact.name + ", " + phone.getText());
            setSelected(checked);
        }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
        }
    }
}
