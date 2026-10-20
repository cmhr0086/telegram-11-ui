package org.telegram.uidemo;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Components.ChatInputBar;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Chat background (theme wallpaper, motion gradient), text bubbles and the input bar.
 * Sending a message rotates the motion gradient like upstream ChatActivity.
 */
public class DemoChatFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final String[] REPLIES = {
            "Nice 👍",
            "The background just moved, did you see it?",
            "Try switching to Night mode from the ⋮ menu — bubbles, wallpaper and the input panel recolour together.",
            "ok",
    };

    private SizeNotifierFrameLayout contentView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ChatInputBar inputBar;
    private final ArrayList<TextBubbleMessage> messages = new ArrayList<>();
    private long nextId = 1;
    private int replyIndex;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        addSample("Hey! This is the chat background from the current theme.", false, "10:02", TextBubbleMessage.STATE_NONE);
        addSample("Consecutive messages from the same side are grouped: the tail is only drawn on the last one and the inner corners get the small radius.", false, "10:02", TextBubbleMessage.STATE_NONE);
        addSample("Looks just like Telegram", true, "10:03", TextBubbleMessage.STATE_READ);
        addSample("Bubbles are Theme.MessageDrawable", true, "10:03", TextBubbleMessage.STATE_READ);
        addSample("with time and check marks drawn like ChatMessageCell 🙂", true, "10:04", TextBubbleMessage.STATE_READ);
        addSample("Long messages wrap and the time moves to its own line when the last line is too wide to fit it next to the text.", false, "10:05", TextBubbleMessage.STATE_NONE);
        addSample("Type something below and send it.", false, "10:05", TextBubbleMessage.STATE_NONE);
        addSample("ok", true, "10:06", TextBubbleMessage.STATE_SENT);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        super.onFragmentDestroy();
    }

    private TextBubbleMessage addSample(CharSequence text, boolean out, String time, int state) {
        TextBubbleMessage message = new TextBubbleMessage(nextId++, text, out, time, state);
        messages.add(message);
        return message;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Chat");
        actionBar.setSubtitle("bubbles · background · input");
        actionBar.setAddToContainer(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        contentView = new SizeNotifierFrameLayout(context, parentLayout);
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        fragmentView = contentView;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        contentView.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                // bubbles with a gradient depend on their position in the list
                invalidateViews();
            }
        };
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        layoutManager.setStackFromEnd(true);
        listView.setLayoutManager(layoutManager);
        listView.setItemAnimator(null);
        listView.setVerticalScrollBarEnabled(true);
        listView.setClipToPadding(false);
        listView.setPadding(0, ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + AndroidUtilities.dp(4), 0, AndroidUtilities.dp(3));
        listView.setAdapter(new Adapter(context));
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                listView.invalidateViews();
            }
        });
        column.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        inputBar = new ChatInputBar(context, getResourceProvider());
        inputBar.setDelegate(this::sendMessage);
        column.addView(inputBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        return fragmentView;
    }

    private boolean sendMessage(CharSequence text) {
        TextBubbleMessage message = new TextBubbleMessage(nextId++, text.toString(), true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENDING);
        appendMessage(message);
        rotateMotionBackground();
        AndroidUtilities.runOnUIThread(() -> updateState(message, TextBubbleMessage.STATE_SENT), 700);
        AndroidUtilities.runOnUIThread(() -> updateState(message, TextBubbleMessage.STATE_READ), 1800);
        AndroidUtilities.runOnUIThread(() -> {
            if (fragmentView == null) {
                return;
            }
            appendMessage(new TextBubbleMessage(nextId++, REPLIES[replyIndex++ % REPLIES.length], false, timeFormat.format(new Date()), TextBubbleMessage.STATE_NONE));
        }, 2400);
        return true;
    }

    private void appendMessage(TextBubbleMessage message) {
        messages.add(message);
        if (listView == null || listView.getAdapter() == null) {
            return;
        }
        int position = messages.size() - 1;
        listView.getAdapter().notifyItemInserted(position);
        if (position > 0) {
            // the previous bubble loses its tail when the new one is from the same side
            listView.getAdapter().notifyItemChanged(position - 1);
        }
        listView.smoothScrollToPosition(position);
    }

    private void updateState(TextBubbleMessage message, int state) {
        message.state = state;
        if (listView == null) {
            return;
        }
        int index = messages.indexOf(message);
        if (index >= 0 && listView.getAdapter() != null) {
            listView.getAdapter().notifyItemChanged(index);
        }
    }

    /** Same as ChatActivity.rotateMotionBackgroundDrawable(). */
    private void rotateMotionBackground() {
        Drawable wallpaper = contentView.getBackgroundImage();
        if (wallpaper instanceof MotionBackgroundDrawable) {
            ((MotionBackgroundDrawable) wallpaper).switchToNextPosition();
        }
        MotionBackgroundDrawable bubbleGradient = Theme.chat_msgOutDrawable.getMotionBackgroundDrawable();
        if (bubbleGradient != null) {
            bubbleGradient.switchToNextPosition();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewWallpapper && contentView != null) {
            if (!Theme.isAnimatingColor()) {
                contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            }
            contentView.invalidateBackground();
            listView.invalidateViews();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (contentView != null) {
            contentView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (contentView != null) {
            contentView.onPause();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate redraw = () -> {
            if (listView != null) {
                listView.invalidateViews();
            }
            if (contentView != null) {
                contentView.invalidateBackground();
            }
        };
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubtitle));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        int[] keys = {
                Theme.key_chat_wallpaper,
                Theme.key_chat_inBubble, Theme.key_chat_inBubbleSelected, Theme.key_chat_inBubbleShadow,
                Theme.key_chat_outBubble, Theme.key_chat_outBubbleSelected, Theme.key_chat_outBubbleShadow,
                Theme.key_chat_messageTextIn, Theme.key_chat_messageTextOut,
                Theme.key_chat_messageLinkIn, Theme.key_chat_messageLinkOut,
                Theme.key_chat_inTimeText, Theme.key_chat_outTimeText,
                Theme.key_chat_outSentCheck, Theme.key_chat_outSentCheckRead, Theme.key_chat_outSentClock,
        };
        for (int key : keys) {
            descriptions.add(new ThemeDescription(null, 0, null, null, null, redraw, key));
        }
        if (inputBar != null) {
            descriptions.addAll(inputBar.getThemeDescriptions());
        }
        return descriptions;
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarDefault)) > 0.721f;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
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
            TextBubbleMessage message = messages.get(position);
            boolean pinnedTop = position > 0 && messages.get(position - 1).isOut == message.isOut;
            boolean pinnedBottom = position < messages.size() - 1 && messages.get(position + 1).isOut == message.isOut;
            ((TextBubbleCell) holder.itemView).setMessage(message, pinnedTop, pinnedBottom);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }
}
