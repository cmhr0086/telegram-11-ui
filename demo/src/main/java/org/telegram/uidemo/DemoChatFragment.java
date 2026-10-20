package org.telegram.uidemo;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatBubbleItemAnimator;
import org.telegram.ui.Components.ChatInputLayout;
import org.telegram.ui.Components.ChatInputTopPanel;
import org.telegram.ui.Components.ChatSounds;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.TelegramUI;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Chat background (theme wallpaper, motion gradient), text bubbles and the upstream message input
 * (ChatActivityEnterView): formatting menu, emoji/keyboard switch, voice recording with
 * slide-to-cancel and lock, the recorded-voice preview, the reply / edit panel and send options.
 */
public class DemoChatFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final String[] REPLIES = {
            "Nice 👍",
            "The background just moved, did you see it?",
            "Hold the microphone to record, slide left to cancel, slide up to lock.",
            "Long-press a message to reply or edit it.",
            "ok",
    };

    private static final int MENU_NIGHT = 1;
    private static final int MENU_RECORD_BUTTON = 2;

    private ActionBarMenuItem nightItem;
    private ActionBarMenuSubItem recordButtonItem;
    private ChatInputLayout contentView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ChatActivityEnterView enterView;
    private ChatInputTopPanel topPanel;
    private final ArrayList<TextBubbleMessage> messages = new ArrayList<>();
    private long nextId = 1;
    private int replyIndex;
    private TextBubbleMessage replyingTo;
    private TextBubbleMessage editing;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

    // simulated round video recording (the camera belongs to the host app)
    private Runnable fakeVideoAmplitude;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        ChatSounds.preload();
        addSample("Hey! This is the chat background from the current theme.", false, "10:02", TextBubbleMessage.STATE_NONE);
        addSample("The input below is upstream ChatActivityEnterView: select text for bold / italic / links, tap the smiley to switch to the emoji panel.", false, "10:02", TextBubbleMessage.STATE_NONE);
        addSample("Looks just like Telegram", true, "10:03", TextBubbleMessage.STATE_READ);
        addSample("Bubbles are Theme.MessageDrawable", true, "10:03", TextBubbleMessage.STATE_READ);
        addSample("with time and check marks drawn like ChatMessageCell 🙂", true, "10:04", TextBubbleMessage.STATE_READ);
        addSample("Hold the microphone to record a voice message, slide left to cancel or up to lock.", false, "10:05", TextBubbleMessage.STATE_NONE);
        addSample("Long-press a message to reply to it, or to edit your own.", false, "10:05", TextBubbleMessage.STATE_NONE);
        addSample("Emoji use Telegram's own set 😎🔥🎉 in bubbles and in the input field", false, "10:06", TextBubbleMessage.STATE_NONE);
        addSample("👋", false, "10:06", TextBubbleMessage.STATE_NONE);
        addSample("😂❤️👍", true, "10:07", TextBubbleMessage.STATE_READ);
        addSample("ok", true, "10:07", TextBubbleMessage.STATE_SENT);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        if (enterView != null) {
            enterView.onDestroy();
        }
        stopFakeVideo();
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
        actionBar.setSubtitle("ChatActivityEnterView");
        actionBar.setAddToContainer(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_NIGHT) {
                    TelegramUI.toggleDayNight(nightItem);
                } else if (id == MENU_RECORD_BUTTON) {
                    boolean visible = !enterView.isRecordButtonVisible();
                    enterView.setRecordButtonVisible(visible);
                    recordButtonItem.setChecked(visible);
                }
            }
        });
        nightItem = actionBar.createMenu().addItem(MENU_NIGHT, R.drawable.msg_theme);
        nightItem.setContentDescription("Switch day / night");
        ActionBarMenuItem otherItem = actionBar.createMenu().addItem(0, R.drawable.ic_ab_other);
        otherItem.setContentDescription("More options");
        recordButtonItem = otherItem.addSubItem(MENU_RECORD_BUTTON, R.drawable.input_mic, "Voice / video button", true);
        recordButtonItem.setChecked(true);

        contentView = new ChatInputLayout(context, parentLayout);
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        fragmentView = contentView;

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
        listView.setItemAnimator(new ChatBubbleItemAnimator());
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
        listView.setOnItemLongClickListener((view, position) -> {
            showMessageOptions(messages.get(position));
            return true;
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentView.setContentView(listView);

        enterView = new ChatActivityEnterView(getParentActivity(), contentView, this, true, getResourceProvider());
        enterView.setDelegate(new EnterViewDelegate());
        enterView.setDialogId(777, UserConfig.selectedAccount);
        enterView.setRoundVideoEnabled(true);
        contentView.addView(enterView, contentView.getChildCount(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        contentView.setEnterView(enterView);

        topPanel = new ChatInputTopPanel(context, enterView, getResourceProvider());
        topPanel.setListener(new ChatInputTopPanel.Listener() {
            @Override
            public void onClose(int mode) {
                replyingTo = null;
                editing = null;
            }

            @Override
            public void onClick(int mode) {
                TextBubbleMessage target = mode == ChatInputTopPanel.MODE_EDIT ? editing : replyingTo;
                int index = target == null ? -1 : messages.indexOf(target);
                if (index >= 0) {
                    listView.smoothScrollToPosition(index);
                }
            }
        });

        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        contentView.setActionBar(actionBar);
        return fragmentView;
    }

    private void showMessageOptions(TextBubbleMessage message) {
        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        items.add("Reply");
        icons.add(R.drawable.menu_reply);
        actions.add(() -> startReply(message));
        if (message.isOut) {
            items.add("Edit");
            icons.add(R.drawable.msg_edit);
            actions.add(() -> startEdit(message));
        }
        int[] iconIds = new int[icons.size()];
        for (int i = 0; i < iconIds.length; i++) {
            iconIds[i] = icons.get(i);
        }
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setItems(items.toArray(new CharSequence[0]), iconIds, (d, which) -> actions.get(which).run())
                .create());
    }

    private void startReply(TextBubbleMessage message) {
        editing = null;
        enterView.setEditingMessageObject(null, false);
        replyingTo = message;
        enterView.setReplyingMessageObject(message);
        topPanel.showReply(message.isOut ? "You" : "Telegram UI", message.text);
    }

    private void startEdit(TextBubbleMessage message) {
        replyingTo = null;
        enterView.setReplyingMessageObject(null);
        editing = message;
        // like MessageObject: plain text + entities (bold, italic, links...) extracted from the bubble's spans
        CharSequence[] text = new CharSequence[]{message.text};
        ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(text, true);
        enterView.setEditingMessageObject(new ChatActivityEnterView.EditingMessage((int) message.id, text[0].toString(), entities, false), false);
        topPanel.showEdit(message.text, false);
    }

    private class EnterViewDelegate implements ChatActivityEnterView.ChatActivityEnterViewDelegate {

        @Override
        public void onSendText(CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean notify, int scheduleDate) {
            // like upstream MessageObject: plain text + entities (the editor's IME / emoji spans are not carried over)
            SpannableStringBuilder styled = new SpannableStringBuilder(text.toString());
            if (entities != null && !entities.isEmpty()) {
                MediaDataController.addTextStyleRuns(entities, text, styled);
            }
            String prefix = scheduleDate != 0 ? "⏰ " : (!notify ? "🔕 " : "");
            sendOutgoing(prefix.isEmpty() ? styled : new SpannableStringBuilder(prefix).append(styled));
        }

        @Override
        public void onSendVoice(MediaController.RecordedAudio audio, boolean notify, int scheduleDate) {
            sendOutgoing("🎤 Voice message · " + AndroidUtilities.formatShortDuration((int) (audio.durationMs / 1000)));
        }

        @Override
        public void onMessageSend(CharSequence message, boolean notify, int scheduleDate) {
            if (topPanel.getMode() == ChatInputTopPanel.MODE_REPLY) {
                topPanel.hide(true);
            }
            replyingTo = null;
            enterView.setReplyingMessageObject(null);
        }

        @Override
        public void onEditDone(CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
            if (editing != null) {
                SpannableStringBuilder styled = new SpannableStringBuilder(text.toString());
                if (entities != null && !entities.isEmpty()) {
                    MediaDataController.addTextStyleRuns(entities, text, styled);
                }
                editing.text = styled;
                int index = messages.indexOf(editing);
                if (index >= 0 && listView.getAdapter() != null) {
                    listView.getAdapter().notifyItemChanged(index);
                }
            }
        }

        @Override
        public void onMessageEditEnd(boolean loading) {
            editing = null;
            if (topPanel.getMode() == ChatInputTopPanel.MODE_EDIT) {
                topPanel.hide(true);
            }
        }

        @Override
        public void didPressAttachButton() {
            Toast.makeText(getParentActivity(), "Attach: the host opens its own picker here", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void needShowMediaBanHint() {
            Toast.makeText(getParentActivity(), "Not allowed in this chat", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void needStartRecordVideo(int state, boolean notify, int scheduleDate, int ttl, long effectId) {
            // state 0 = start, 1/4 = send, 2 = cancel (upstream drives InstantCameraView here)
            if (state == 0) {
                MediaController.getInstance().notifyRecordStarted(enterView.recordingGuid, false);
                fakeVideoAmplitude = new Runnable() {
                    @Override
                    public void run() {
                        MediaController.getInstance().notifyRecordProgress(enterView.recordingGuid, 400 + Math.random() * 1200);
                        AndroidUtilities.runOnUIThread(this, 80);
                    }
                };
                AndroidUtilities.runOnUIThread(fakeVideoAmplitude, 80);
            } else {
                stopFakeVideo();
                if (state == 1 || state == 4) {
                    MediaController.getInstance().notifyRecordStopped(enterView.recordingGuid, MediaController.STOP_REASON_VIDEO_SENT);
                    sendOutgoing("📹 Round video (simulated: the camera belongs to the host)");
                } else {
                    MediaController.getInstance().notifyRecordStopped(enterView.recordingGuid, MediaController.STOP_REASON_CANCEL);
                }
            }
        }

        @Override
        public boolean roundVideoNeedsCameraPermission() {
            return false; // the demo only simulates the camera
        }

        @Override
        public boolean canScheduleMessage() {
            return true;
        }

        @Override
        public boolean hasScheduledMessages() {
            return false;
        }

        @Override
        public void onImagePasted(android.net.Uri uri, String mimeType) {
            Toast.makeText(getParentActivity(), "Image pasted: " + mimeType, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopFakeVideo() {
        if (fakeVideoAmplitude != null) {
            AndroidUtilities.cancelRunOnUIThread(fakeVideoAmplitude);
            fakeVideoAmplitude = null;
        }
    }

    private void sendOutgoing(CharSequence text) {
        TextBubbleMessage message = new TextBubbleMessage(nextId++, text, true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENDING);
        appendMessage(message);
        rotateMotionBackground();
        ChatSounds.playOut();
        AndroidUtilities.runOnUIThread(() -> updateState(message, TextBubbleMessage.STATE_SENT), 700);
        AndroidUtilities.runOnUIThread(() -> updateState(message, TextBubbleMessage.STATE_READ), 1800);
        AndroidUtilities.runOnUIThread(() -> {
            if (fragmentView == null) {
                return;
            }
            appendMessage(new TextBubbleMessage(nextId++, REPLIES[replyIndex++ % REPLIES.length], false, timeFormat.format(new Date()), TextBubbleMessage.STATE_NONE));
            ChatSounds.playIn();
        }, 2400);
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
        layoutManager.scrollToPositionWithOffset(position, 0, true);
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
    public boolean onBackPressed() {
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
        if (enterView != null) {
            descriptions.addAll(enterView.getThemeDescriptions());
        }
        if (topPanel != null) {
            descriptions.addAll(topPanel.getThemeDescriptions());
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
