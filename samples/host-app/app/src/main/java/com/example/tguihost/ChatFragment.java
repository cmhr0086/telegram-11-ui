package com.example.tguihost;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
// library resources live in the library R (host has nonTransitiveRClass=true by default)
import org.telegram.messenger.R;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatBubbleItemAnimator;
import org.telegram.ui.Components.ChatInputLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessageContextMenu;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.TelegramUI;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Chat screen: wallpaper, TextBubbleCell messages, ChatActivityEnterView, tap menu, long-press multi-select. */
public class ChatFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_NIGHT = 1;
    private static final int ACTION_DELETE = 10;

    private final String title;
    private final ArrayList<TextBubbleMessage> messages = new ArrayList<>();
    private final ArrayList<TextBubbleMessage> selected = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private long nextId = 1;

    private ChatInputLayout contentView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ChatActivityEnterView enterView;
    private NumberTextView selectedCount;

    public ChatFragment(String title) {
        this.title = title;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        add("Hi! This chat runs from the published AAR.", false, TextBubbleMessage.STATE_NONE);
        add("Tap a bubble for the menu, long-press to select.", false, TextBubbleMessage.STATE_NONE);
        add("Looks like Telegram 🙂", true, TextBubbleMessage.STATE_READ);
        add("👍", true, TextBubbleMessage.STATE_READ);
        add("Type something below and send it.", false, TextBubbleMessage.STATE_NONE);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        if (enterView != null) {
            enterView.onDestroy();
        }
        super.onFragmentDestroy();
    }

    private void add(CharSequence text, boolean out, int state) {
        messages.add(new TextBubbleMessage(nextId++, text, out, timeFormat.format(new Date()), state));
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(title);
        actionBar.setAddToContainer(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        clearSelection();
                    } else {
                        finishFragment();
                    }
                } else if (id == MENU_NIGHT) {
                    TelegramUI.toggleDayNight(actionBar);
                } else if (id == ACTION_DELETE) {
                    for (TextBubbleMessage m : new ArrayList<>(selected)) {
                        int index = messages.indexOf(m);
                        messages.remove(index);
                        listView.getAdapter().notifyItemRemoved(index);
                    }
                    clearSelection();
                }
            }
        });
        actionBar.createMenu().addItem(MENU_NIGHT, R.drawable.msg_theme);

        contentView = new ChatInputLayout(context, parentLayout);
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        fragmentView = contentView;

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        layoutManager.setStackFromEnd(true);
        listView.setLayoutManager(layoutManager);
        listView.setItemAnimator(new ChatBubbleItemAnimator());
        listView.setClipToPadding(false);
        listView.setPadding(0, ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(4), 0, AndroidUtilities.dp(3));
        listView.setAdapter(new Adapter(context));
        listView.setInstantClick(true);
        listView.setDisableHighlightState(true);
        listView.setOnItemClickListener((RecyclerListView.OnItemClickListenerExtended) (view, position, x, y) -> {
            if (position < 0 || position >= messages.size()) {
                return;
            }
            if (actionBar.isActionModeShowed()) {
                toggle(messages.get(position));
            } else {
                showMenu(view, messages.get(position), x, y);
            }
        });
        listView.setOnItemLongClickListener((RecyclerListView.OnItemLongClickListenerExtended) (view, position, x, y) -> {
            if (position < 0 || position >= messages.size()) {
                return false;
            }
            if (!actionBar.isActionModeShowed()) {
                showActionMode();
            }
            toggle(messages.get(position));
            return true;
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentView.setContentView(listView);

        enterView = new ChatActivityEnterView(getParentActivity(), contentView, this, true, getResourceProvider());
        enterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onSendText(CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean notify, int scheduleDate) {
                send(text);
            }
        });
        enterView.setRecordButtonVisible(false);
        contentView.addView(enterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        contentView.setEnterView(enterView);

        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        contentView.setActionBar(actionBar);
        return fragmentView;
    }

    private void send(CharSequence text) {
        TextBubbleMessage m = new TextBubbleMessage(nextId++, text.toString(), true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENT);
        messages.add(m);
        int position = messages.size() - 1;
        listView.getAdapter().notifyItemInserted(position);
        if (position > 0) {
            listView.getAdapter().notifyItemChanged(position - 1);
        }
        layoutManager.scrollToPositionWithOffset(position, 0, true);
        if (contentView.getBackgroundImage() instanceof MotionBackgroundDrawable) {
            ((MotionBackgroundDrawable) contentView.getBackgroundImage()).switchToNextPosition();
        }
    }

    private void showMenu(View cell, TextBubbleMessage message, float x, float y) {
        MessageContextMenu menu = new MessageContextMenu(getParentActivity(), getResourceProvider());
        menu.addItem(R.drawable.msg_copy, "Copy", () -> {
            AndroidUtilities.addToClipboard(message.text.toString());
            Toast.makeText(getParentActivity(), "Copied", Toast.LENGTH_SHORT).show();
        });
        menu.addItem(R.drawable.msg_delete, "Delete", true, () -> {
            int index = messages.indexOf(message);
            messages.remove(index);
            listView.getAdapter().notifyItemRemoved(index);
        });
        menu.setReactions(MessageContextMenu.DEFAULT_REACTIONS);
        menu.setDelegate(new MessageContextMenu.Delegate() {
            @Override
            public void onReactionClick(String emoji) {
                message.reaction = emoji.equals(message.reaction) ? null : emoji;
                listView.getAdapter().notifyItemChanged(messages.indexOf(message));
            }
        });
        menu.show(cell, x, y);
    }

    private void showActionMode() {
        ActionBarMenu actionMode = actionBar.createActionMode();
        if (selectedCount == null) {
            selectedCount = new NumberTextView(actionMode.getContext());
            selectedCount.setTextSize(18);
            selectedCount.setTypeface(AndroidUtilities.bold());
            selectedCount.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
            actionMode.addView(selectedCount, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));
            actionMode.addItemWithWidth(ACTION_DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54), "Delete");
        }
        actionBar.showActionMode();
    }

    private void toggle(TextBubbleMessage m) {
        if (!selected.remove(m)) {
            selected.add(m);
        }
        if (selected.isEmpty()) {
            clearSelection();
            return;
        }
        selectedCount.setNumber(selected.size(), true);
        updateRows();
    }

    private void clearSelection() {
        selected.clear();
        if (actionBar.isActionModeShowed()) {
            actionBar.hideActionMode();
        }
        updateRows();
    }

    private void updateRows() {
        boolean mode = actionBar.isActionModeShowed();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int position = listView.getChildAdapterPosition(child);
            if (child instanceof TextBubbleCell && position >= 0 && position < messages.size()) {
                ((TextBubbleCell) child).setCheckBoxVisible(mode, true);
                ((TextBubbleCell) child).setChecked(selected.contains(messages.get(position)), true);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded && listView != null) {
            Emoji.invalidateAll(listView);
        } else if (id == NotificationCenter.didSetNewWallpapper && contentView != null) {
            if (!Theme.isAnimatingColor()) {
                contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            }
            contentView.invalidateBackground();
            listView.invalidateViews();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (actionBar.isActionModeShowed()) {
            clearSelection();
            return false;
        }
        if (enterView != null && enterView.isPopupShowing()) {
            enterView.hidePopup(true);
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (contentView != null) {
            contentView.onResume();
        }
        if (enterView != null) {
            enterView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (contentView != null) {
            contentView.onPause();
        }
        if (enterView != null) {
            enterView.onPause();
        }
    }

    /** Without this the chat screen keeps its old colours when the theme is switched. */
    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> list = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate redraw = () -> {
            if (listView != null) {
                listView.invalidateViews();
            }
            if (contentView != null) {
                contentView.invalidateBackground();
            }
        };
        list.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        list.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        list.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        int[] keys = {Theme.key_chat_wallpaper, Theme.key_chat_inBubble, Theme.key_chat_outBubble,
                Theme.key_chat_messageTextIn, Theme.key_chat_messageTextOut, Theme.key_chat_inTimeText, Theme.key_chat_outTimeText};
        for (int key : keys) {
            list.add(new ThemeDescription(null, 0, null, null, null, redraw, key));
        }
        if (enterView != null) {
            list.addAll(enterView.getThemeDescriptions());
        }
        return list;
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

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextBubbleCell cell = new TextBubbleCell(context, getResourceProvider());
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextBubbleMessage m = messages.get(position);
            boolean pinnedTop = position > 0 && messages.get(position - 1).isOut == m.isOut;
            boolean pinnedBottom = position < messages.size() - 1 && messages.get(position + 1).isOut == m.isOut;
            TextBubbleCell cell = (TextBubbleCell) holder.itemView;
            cell.setMessage(m, pinnedTop, pinnedBottom);
            cell.setCheckBoxVisible(actionBar.isActionModeShowed(), false);
            cell.setChecked(selected.contains(m), false);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }
}
