package org.telegram.uidemo;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgui.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.DocumentBubbleCell;
import org.telegram.ui.Cells.PhotoBubbleCell;
import org.telegram.ui.Cells.VoiceBubbleCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatBubbleItemAnimator;
import org.telegram.ui.Components.ChatInputLayout;
import org.telegram.ui.Components.ChatInputTopPanel;
import org.telegram.ui.Components.ChatSounds;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessageContextMenu;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.TelegramUI;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
            "Tap a message for the menu, long-press to select.",
            "ok",
    };

    private static final int MENU_NIGHT = 1;
    private static final int MENU_RECORD_BUTTON = 2;
    // action mode (ChatActivity.createActionMode ids)
    private static final int ACTION_EDIT = 10;
    private static final int ACTION_COPY = 11;
    private static final int ACTION_FORWARD = 12;
    private static final int ACTION_DELETE = 13;

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
    /** Adapter rows: a TextBubbleMessage, or a Long (start of day, unix seconds) for a date separator. */
    private final ArrayList<Object> rows = new ArrayList<>();
    private static final int VIEW_TYPE_DATE = 100;
    private ChatActionCell floatingDateView;
    private boolean floatingDateVisible;
    private final Runnable hideFloatingDate = () -> showFloatingDate(false);
    /** Day of the next sample messages (0 = today), see sampleDate. */
    private int sampleDaysAgo;
    /** Set by scrollToMessage: the row flashes when it is bound / found after the scroll. */
    private long highlightMessageId;
    private TextBubbleMessage editing;
    // multi-select (ChatActivity selectedMessagesIds / actionModeViews / bottomMessagesActionContainer)
    private final ArrayList<TextBubbleMessage> selectedMessages = new ArrayList<>();
    private final ArrayList<View> actionModeViews = new ArrayList<>();
    private NumberTextView selectedMessagesCountTextView;
    private FrameLayout bottomMessagesActionContainer;
    private TextView replyButton;
    private TextView forwardButton;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

    // simulated round video recording (the camera belongs to the host app)
    private Runnable fakeVideoAmplitude;

    // voice playback: recorded files play with MediaPlayer, the sample voice message only runs a timer
    private final HashMap<Long, String> voicePaths = new HashMap<>();
    private TextBubbleMessage playingVoice;
    private MediaPlayer voicePlayer;
    private long fakePlayStart;
    private final Runnable voiceProgressUpdater = this::updateVoiceProgress;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        ChatSounds.preload();
        sampleDaysAgo = 3;
        addSample("Hey! This is the chat background from the current theme.", false, "10:02", TextBubbleMessage.STATE_NONE);
        addSample("The input below is upstream ChatActivityEnterView: select text for bold / italic / links, tap the smiley to switch to the emoji panel.", false, "10:02", TextBubbleMessage.STATE_NONE);
        sampleDaysAgo = 1;
        TextBubbleMessage looksLike = addSample("Looks just like Telegram", true, "10:03", TextBubbleMessage.STATE_READ);
        // reactions: pills inside the bubble (ReactionsLayoutInBubble), tap one to toggle yours
        react(looksLike, "👍", 3, true);
        react(looksLike, "❤", 1, false);
        addSample("Bubbles are Theme.MessageDrawable", true, "10:03", TextBubbleMessage.STATE_READ);
        addSample("with time and check marks drawn like ChatMessageCell 🙂", true, "10:04", TextBubbleMessage.STATE_READ);
        addSample("Hold the microphone to record a voice message, slide left to cancel or up to lock.", false, "10:05", TextBubbleMessage.STATE_NONE);
        TextBubbleMessage voice = TextBubbleMessage.voice(nextId++, false, "10:05", TextBubbleMessage.STATE_NONE, 7, sampleWaveform());
        voice.contentUnread = true;
        react(voice, "🔥", 2, false);
        addSample(voice);
        addSample("Tap a message for the menu and reactions, long-press to select messages.", false, "10:05", TextBubbleMessage.STATE_NONE);
        TextBubbleMessage emojiSet = addSample("Emoji use Telegram's own set 😎🔥🎉 in bubbles and in the input field", false, "10:06", TextBubbleMessage.STATE_NONE);
        react(emojiSet, "👍", 5, true);
        react(emojiSet, "😁", 2, false);
        react(emojiSet, "🎉", 1, false);
        sampleDaysAgo = 0;
        addSample("Links work too: https://telegram.org, core.telegram.org/api and mentions like @durov or #hashtags. Long-press a link to copy it.", false, "10:06", TextBubbleMessage.STATE_NONE);
        react(addSample("👋", false, "10:06", TextBubbleMessage.STATE_NONE), "❤", 1, false);
        addSample("😂❤️👍", true, "10:07", TextBubbleMessage.STATE_READ);
        addSample("ok", true, "10:07", TextBubbleMessage.STATE_SENT);
        // replies: the quote is upstream's reply panel; tapping it scrolls to the original and flashes it
        TextBubbleMessage quoted = addSample("It does! Tap this quote to jump to the original message.", false, "10:07", TextBubbleMessage.STATE_NONE);
        quoted.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        // photos: the host hands in a Drawable (here generated, no network); one waits for a tap to "download"
        TextBubbleMessage landscape = TextBubbleMessage.photo(nextId++, false, "10:08", TextBubbleMessage.STATE_NONE, samplePhoto(1280, 853, 0), null);
        react(landscape, "🥰", 4, false);
        react(landscape, "👏", 1, true);
        addSample(landscape);
        TextBubbleMessage portrait = TextBubbleMessage.photo(nextId++, true, "10:09", TextBubbleMessage.STATE_READ, samplePhoto(720, 960, 1), "Sunset from the hill 🌅");
        react(portrait, "❤", 7, false);
        addSample(portrait);
        TextBubbleMessage photoReply = addSample("Beautiful 😍 where is that?", false, "10:09", TextBubbleMessage.STATE_NONE);
        photoReply.replyTo = TextBubbleMessage.ReplyTo.of(portrait, "You");
        photoReply.replyTo.colorId = 5;
        TextBubbleMessage download = TextBubbleMessage.photo(nextId++, false, "10:10", TextBubbleMessage.STATE_NONE, samplePhoto(1280, 720, 2), null);
        download.mediaState = TextBubbleMessage.MEDIA_DOWNLOAD;
        addSample(download);
        // files: name / size / state from the host, the round button reports taps
        TextBubbleMessage guide = TextBubbleMessage.document(nextId++, false, "10:11", TextBubbleMessage.STATE_NONE, "Telegram_UI_Guide.pdf", 2_457_600);
        react(guide, "👍", 2, false);
        addSample(guide);
        TextBubbleMessage archive = TextBubbleMessage.document(nextId++, false, "10:11", TextBubbleMessage.STATE_NONE, "tgui-11.4.2-ui3-release-with-a-very-long-file-name-sources.zip", 48_234_496);
        archive.mediaState = TextBubbleMessage.MEDIA_DOWNLOAD;
        addSample(archive);
        addSample(TextBubbleMessage.document(nextId++, true, "10:12", TextBubbleMessage.STATE_READ, "notes.txt", 12_288));
        rebuildRows();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        if (enterView != null) {
            enterView.onDestroy();
        }
        stopFakeVideo();
        stopVoice();
        super.onFragmentDestroy();
    }

    private static TextBubbleMessage react(TextBubbleMessage message, String emoji, int count, boolean chosen) {
        message.reactions.add(new TextBubbleMessage.Reaction(emoji, count, chosen));
        return message;
    }

    private TextBubbleMessage addSample(CharSequence text, boolean out, String time, int state) {
        TextBubbleMessage message = new TextBubbleMessage(nextId++, text, out, time, state);
        addSample(message);
        return message;
    }

    private void addSample(TextBubbleMessage message) {
        message.date = sampleDate(sampleDaysAgo, message.time);
        messages.add(message);
    }

    private static long sampleDate(int daysAgo, String time) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo);
        String[] hm = time.split(":");
        calendar.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
        calendar.set(java.util.Calendar.MINUTE, Integer.parseInt(hm[1]));
        return calendar.getTimeInMillis() / 1000;
    }

    private static long dayStart(long date) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(date * 1000);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis() / 1000;
    }

    /** ChatActivity's date messages: a separator row before the first message of every day. */
    private void rebuildRows() {
        rows.clear();
        long lastDay = Long.MIN_VALUE;
        for (TextBubbleMessage message : messages) {
            long day = dayStart(message.date);
            if (day != lastDay) {
                rows.add(day);
                lastDay = day;
            }
            rows.add(message);
        }
    }

    private TextBubbleMessage messageAt(int position) {
        if (position < 0 || position >= rows.size()) {
            return null;
        }
        Object row = rows.get(position);
        return row instanceof TextBubbleMessage ? (TextBubbleMessage) row : null;
    }

    private int rowOf(TextBubbleMessage message) {
        return rows.indexOf(message);
    }

    /**
     * ChatActivity.updateMessagesVisiblePart's floatingDateView: the date of the topmost visible row,
     * shown while the list scrolls and faded out a second after it stops; hidden when a real
     * separator sits at the top.
     */
    private void updateFloatingDate(boolean scrolling) {
        if (floatingDateView == null || listView.getChildCount() == 0) {
            return;
        }
        int top = listView.getPaddingTop();
        View topChild = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child.getBottom() > top && (topChild == null || child.getTop() < topChild.getTop())) {
                topChild = child;
            }
        }
        if (topChild == null) {
            return;
        }
        int position = listView.getChildAdapterPosition(topChild);
        long day = Long.MIN_VALUE;
        for (int i = Math.min(position, rows.size() - 1); i >= 0; i--) {
            if (rows.get(i) instanceof Long) {
                day = (Long) rows.get(i);
                break;
            }
        }
        if (day == Long.MIN_VALUE) {
            return;
        }
        floatingDateView.setCustomDate(day);
        // the real separator of this day is on screen right below the header: let it show instead
        boolean separatorAtTop = topChild instanceof ChatActionCell && topChild.getTop() > top - topChild.getHeight();
        if (scrolling && !separatorAtTop) {
            AndroidUtilities.cancelRunOnUIThread(hideFloatingDate);
            showFloatingDate(true);
            AndroidUtilities.runOnUIThread(hideFloatingDate, 1000);
        } else if (separatorAtTop) {
            showFloatingDate(false);
        }
    }

    private void showFloatingDate(boolean show) {
        if (floatingDateView == null || floatingDateVisible == show) {
            return;
        }
        floatingDateVisible = show;
        floatingDateView.animate().cancel();
        floatingDateView.animate().alpha(show ? 1f : 0f).setDuration(150).start();
    }

    @Override
    public View createView(Context context) {
        // BackDrawable like ChatActivity: the arrow turns into × in selection mode
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Chat");
        actionBar.setSubtitle("ChatActivityEnterView");
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
                } else if (id >= ACTION_EDIT) {
                    processActionModeItem(id);
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
                updateFloatingDate(dy != 0);
            }
        });
        // like ChatActivity: no list selector on message rows, clicks fire on finger up
        listView.setInstantClick(true);
        listView.setDisableHighlightState(true);
        // setDisableHighlightState only stops setPressed(); the list selector would still ripple over the wallpaper
        // no selector at all: the long-press TransitionDrawable still darkened the row even with a transparent colour
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        // tap: context menu (createMenu(view, single = true)); in selection mode it toggles the row
        listView.setOnItemClickListener((RecyclerListView.OnItemClickListenerExtended) (view, position, x, y) -> {
            TextBubbleMessage message = messageAt(position);
            if (message == null) {
                return;
            }
            if (actionBar.isActionModeShowed()) {
                processRowSelect(message);
            } else {
                showMessageOptions(view, message, x, y);
            }
        });
        // long press: selection mode (createMenu(view, single = false) + startMultiselect)
        listView.setOnItemLongClickListener((RecyclerListView.OnItemLongClickListenerExtended) (view, position, x, y) -> {
            TextBubbleMessage message = messageAt(position);
            if (message == null) {
                return false;
            }
            if (!actionBar.isActionModeShowed()) {
                showActionMode(message);
            } else {
                processRowSelect(message);
            }
            startMultiselect(position);
            return true;
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        // sticky date while scrolling (ChatActivity floatingDateView, 4dp under the action bar)
        floatingDateView = new ChatActionCell(context, getResourceProvider());
        floatingDateView.setFloating(true);
        floatingDateView.setAlpha(0f);
        contentView.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 0, 4, 0, 0));
        floatingDateView.setTranslationY(ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0));
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
                int index = target == null ? -1 : rowOf(target);
                if (index >= 0) {
                    listView.smoothScrollToPosition(index);
                }
            }
        });

        bottomMessagesActionContainer = new FrameLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.getThemePaint(Theme.key_paint_chatComposeBackground));
            }
        };
        bottomMessagesActionContainer.setVisibility(View.INVISIBLE);
        bottomMessagesActionContainer.setWillNotDraw(false);
        bottomMessagesActionContainer.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        bottomMessagesActionContainer.setOnTouchListener((v, event) -> true);
        contentView.addView(bottomMessagesActionContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        contentView.setActionBar(actionBar);
        return fragmentView;
    }

    // ---- selection mode (ChatActivity.createActionMode / createBottomMessagesActionButtons / createMenu's
    // action-mode branch / processRowSelect / addToSelectedMessages / startMultiselect / hideActionMode)

    private void createActionMode() {
        if (selectedMessagesCountTextView != null) {
            return;
        }
        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.bold());
        selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        selectedMessagesCountTextView.setOnTouchListener((v, event) -> true);
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));

        actionModeViews.add(actionMode.addItemWithWidth(ACTION_EDIT, R.drawable.msg_edit, AndroidUtilities.dp(54), "Edit"));
        actionModeViews.add(actionMode.addItemWithWidth(ACTION_COPY, R.drawable.msg_copy, AndroidUtilities.dp(54), "Copy"));
        actionModeViews.add(actionMode.addItemWithWidth(ACTION_FORWARD, R.drawable.msg_forward, AndroidUtilities.dp(54), "Forward"));
        actionModeViews.add(actionMode.addItemWithWidth(ACTION_DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54), "Delete"));

        Context context = getParentActivity();
        replyButton = new TextView(context);
        replyButton.setText("Reply");
        replyButton.setGravity(Gravity.CENTER_VERTICAL);
        replyButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        replyButton.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(21), 0);
        replyButton.setCompoundDrawablePadding(AndroidUtilities.dp(7));
        replyButton.setTypeface(AndroidUtilities.bold());
        replyButton.setOnClickListener(v -> {
            TextBubbleMessage message = selectedMessages.isEmpty() ? null : selectedMessages.get(0);
            clearSelection();
            if (message != null) {
                startReply(message);
            }
        });
        bottomMessagesActionContainer.addView(replyButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        forwardButton = new TextView(context);
        forwardButton.setText("Forward");
        forwardButton.setGravity(Gravity.CENTER_VERTICAL);
        forwardButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        forwardButton.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
        forwardButton.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        forwardButton.setTypeface(AndroidUtilities.bold());
        forwardButton.setOnClickListener(v -> processActionModeItem(ACTION_FORWARD));
        bottomMessagesActionContainer.addView(forwardButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));
        updateActionModeColors();
    }

    private void updateActionModeColors() {
        if (selectedMessagesCountTextView == null) {
            return;
        }
        int iconColor = Theme.getColor(Theme.key_actionBarActionModeDefaultIcon);
        selectedMessagesCountTextView.setTextColor(iconColor);
        TextView[] buttons = {replyButton, forwardButton};
        int[] icons = {R.drawable.input_reply, R.drawable.input_forward};
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 3));
            buttons[i].setTextColor(iconColor);
            Drawable image = getParentActivity().getResources().getDrawable(icons[i]).mutate();
            image.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            buttons[i].setCompoundDrawablesWithIntrinsicBounds(image, null, null, null);
        }
        bottomMessagesActionContainer.invalidate();
    }

    private void showActionMode(TextBubbleMessage message) {
        createActionMode();
        updateActionModeColors();
        if (enterView.isPopupShowing()) {
            enterView.hidePopup(true);
        }
        AndroidUtilities.hideKeyboard(enterView);
        bottomMessagesActionContainer.setVisibility(View.VISIBLE);
        bottomMessagesActionContainer.setTranslationY(AndroidUtilities.dp(51));
        int translationY = enterView.getMeasuredHeight() - AndroidUtilities.dp(51);
        actionBar.showActionMode(true, bottomMessagesActionContainer, null, new View[]{enterView}, new boolean[]{false}, listView, translationY);

        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        for (int a = 0; a < actionModeViews.size(); a++) {
            View view = actionModeViews.get(a);
            view.setPivotY(ActionBar.getCurrentActionBarHeight() / 2);
            AndroidUtilities.clearDrawableAnimation(view);
            animators.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.1f, 1.0f));
        }
        animatorSet.playTogether(animators);
        animatorSet.setDuration(250);
        animatorSet.start();

        selectedMessages.add(message);
        updateActionModeTitle(false);
        updateVisibleRows();
    }

    private void processRowSelect(TextBubbleMessage message) {
        if (selectedMessages.contains(message)) {
            selectedMessages.remove(message);
        } else {
            selectedMessages.add(message);
        }
        if (selectedMessages.isEmpty()) {
            clearSelection();
            return;
        }
        updateActionModeTitle(true);
        updateVisibleRows();
    }

    private void updateActionModeTitle(boolean animated) {
        selectedMessagesCountTextView.setNumber(selectedMessages.size(), animated);
        boolean canEdit = selectedMessages.size() == 1 && selectedMessages.get(0).isOut && selectedMessages.get(0).type == TextBubbleMessage.TYPE_TEXT;
        actionBar.createActionMode().setItemVisibility(ACTION_EDIT, canEdit ? View.VISIBLE : View.GONE);
        boolean canCopy = false;
        for (TextBubbleMessage message : selectedMessages) {
            canCopy |= !android.text.TextUtils.isEmpty(message.text);
        }
        actionBar.createActionMode().setItemVisibility(ACTION_COPY, canCopy ? View.VISIBLE : View.GONE);
    }

    private void clearSelection() {
        selectedMessages.clear();
        if (actionBar.isActionModeShowed()) {
            actionBar.hideActionMode();
        }
        updateVisibleRows();
    }

    private void updateVisibleRows() {
        boolean selectionMode = actionBar.isActionModeShowed();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int position = listView.getChildAdapterPosition(child);
            TextBubbleMessage message = messageAt(position);
            if (child instanceof TextBubbleCell && message != null) {
                TextBubbleCell cell = (TextBubbleCell) child;
                cell.setCheckBoxVisible(selectionMode, true);
                cell.setChecked(selectedMessages.contains(message), true);
            }
        }
    }

    private void processActionModeItem(int id) {
        ArrayList<TextBubbleMessage> selected = new ArrayList<>(selectedMessages);
        selected.sort((a, b) -> Integer.compare(messages.indexOf(a), messages.indexOf(b)));
        if (id == ACTION_EDIT) {
            clearSelection();
            if (!selected.isEmpty()) {
                startEdit(selected.get(0));
            }
        } else if (id == ACTION_COPY) {
            StringBuilder text = new StringBuilder();
            for (TextBubbleMessage message : selected) {
                if (android.text.TextUtils.isEmpty(message.text)) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                text.append(message.text);
            }
            clearSelection();
            if (AndroidUtilities.addToClipboard(text)) {
                Toast.makeText(getParentActivity(), selected.size() == 1 ? "Message copied to clipboard" : "Messages copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        } else if (id == ACTION_FORWARD) {
            clearSelection();
            Toast.makeText(getParentActivity(), "Forward: the host opens its own chat picker here", Toast.LENGTH_SHORT).show();
        } else if (id == ACTION_DELETE) {
            clearSelection();
            for (TextBubbleMessage message : selected) {
                deleteMessage(message);
            }
        }
    }

    private void startMultiselect(int position) {
        final boolean unselect = !selectedMessages.contains(messageAt(position));
        final ArrayList<TextBubbleMessage> alreadySelected = new ArrayList<>(selectedMessages);
        listView.startMultiselect(position, false, new RecyclerListView.onMultiSelectionChanged() {
            @Override
            public void onSelectionChanged(int position, boolean selected, float x, float y) {
                if (unselect) {
                    selected = !selected;
                }
                TextBubbleMessage message = messageAt(position);
                if (message == null) {
                    return;
                }
                if (selected == selectedMessages.contains(message)) {
                    return;
                }
                processRowSelect(message);
            }

            @Override
            public boolean canSelect(int position) {
                TextBubbleMessage message = messageAt(position);
                if (message == null) {
                    return false;
                }
                boolean already = alreadySelected.contains(message);
                return unselect == already;
            }

            @Override
            public int checkPosition(int position, boolean selectionFromTop) {
                return position;
            }

            @Override
            public boolean limitReached() {
                return false;
            }

            @Override
            public void getPaddings(int[] paddings) {
                paddings[0] = listView.getPaddingTop();
                paddings[1] = 0;
            }

            @Override
            public void scrollBy(int dy) {
                listView.scrollBy(0, dy);
            }
        });
    }

    /** Tap menu: upstream's scrim + popup + reactions bar (MessageContextMenu). */
    private void showMessageOptions(View cell, TextBubbleMessage message, float x, float y) {
        MessageContextMenu menu = new MessageContextMenu(getParentActivity(), getResourceProvider());
        menu.addItem(R.drawable.menu_reply, "Reply", () -> startReply(message));
        if (!android.text.TextUtils.isEmpty(message.text)) {
            menu.addItem(R.drawable.msg_copy, "Copy", () -> {
                if (AndroidUtilities.addToClipboard(message.text.toString())) {
                    Toast.makeText(getParentActivity(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (message.isOut && message.type == TextBubbleMessage.TYPE_TEXT) {
            menu.addItem(R.drawable.msg_edit, "Edit", () -> startEdit(message));
        }
        menu.addItem(R.drawable.msg_delete, "Delete", () -> deleteMessage(message));
        menu.setReactions(MessageContextMenu.DEFAULT_REACTIONS);
        menu.setSelectedReactions(message);
        menu.setDelegate(new MessageContextMenu.Delegate() {
            @Override
            public void onReactionClick(String emoji) {
                toggleReaction(message, emoji);
            }
        });
        menu.show(cell, x, y);
    }

    /** ChatActivity.selectReaction: toggles my reaction and rebinds the row, which animates the pills. */
    private void toggleReaction(TextBubbleMessage message, String emoji) {
        message.toggleReaction(emoji);
        int index = rowOf(message);
        if (index >= 0 && listView.getAdapter() != null) {
            listView.getAdapter().notifyItemChanged(index);
        }
    }

    private void deleteMessage(TextBubbleMessage message) {
        int index = messages.indexOf(message);
        if (index < 0) {
            return;
        }
        selectedMessages.remove(message);
        if (message == replyingTo || message == editing) {
            topPanel.hide(true);
            enterView.setReplyingMessageObject(null);
            enterView.setEditingMessageObject(null, false);
            replyingTo = null;
            editing = null;
        }
        if (message == playingVoice) {
            stopVoice();
        }
        int row = rowOf(message);
        messages.remove(index);
        // the day's separator goes too when this was its only message
        boolean removeSeparator = row > 0 && rows.get(row - 1) instanceof Long && (row + 1 >= rows.size() || rows.get(row + 1) instanceof Long);
        rebuildRows();
        RecyclerView.Adapter<?> adapter = listView.getAdapter();
        if (adapter != null && row >= 0) {
            if (removeSeparator) {
                adapter.notifyItemRangeRemoved(row - 1, 2);
                row--;
            } else {
                adapter.notifyItemRemoved(row);
            }
            // neighbours may gain or lose their tail / grouped corners
            if (row > 0) {
                adapter.notifyItemChanged(row - 1);
            }
            if (row < rows.size()) {
                adapter.notifyItemChanged(row);
            }
        }
    }

    private void startReply(TextBubbleMessage message) {
        editing = null;
        enterView.setEditingMessageObject(null, false);
        replyingTo = message;
        enterView.setReplyingMessageObject(message);
        topPanel.showReply(message.isOut ? "You" : "Telegram UI", previewText(message));
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
            TextBubbleMessage message = TextBubbleMessage.voice(nextId++, true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENDING, Math.max(1, Math.round(audio.durationMs / 1000f)), audio.waveform);
            voicePaths.put(message.id, audio.path);
            sendOutgoing(message);
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
                int index = rowOf(editing);
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
        sendOutgoing(new TextBubbleMessage(nextId++, text, true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENDING));
    }

    private void sendOutgoing(TextBubbleMessage message) {
        if (replyingTo != null && message.replyTo == null) {
            // sent from the input's reply panel: the bubble quotes the message (onMessageSend clears replyingTo afterwards)
            message.replyTo = TextBubbleMessage.ReplyTo.of(replyingTo, replyingTo.isOut ? "You" : "Telegram UI");
        }
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
        if (message.date == 0) {
            message.date = System.currentTimeMillis() / 1000;
        }
        messages.add(message);
        int oldCount = rows.size();
        rebuildRows();
        if (listView == null || listView.getAdapter() == null) {
            return;
        }
        int position = rows.size() - 1;
        // a new day starts with its date separator
        listView.getAdapter().notifyItemRangeInserted(oldCount, rows.size() - oldCount);
        if (oldCount > 0) {
            // the previous bubble loses its tail when the new one is from the same side
            listView.getAdapter().notifyItemChanged(oldCount - 1);
        }
        layoutManager.scrollToPositionWithOffset(position, 0, true);
    }

    private void updateState(TextBubbleMessage message, int state) {
        message.state = state;
        if (listView == null) {
            return;
        }
        int index = rowOf(message);
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
            floatingDateView.invalidate();
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

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate redraw = () -> {
            if (listView != null) {
                listView.invalidateViews();
            }
            if (floatingDateView != null) {
                floatingDateView.invalidate();
            }
            if (contentView != null) {
                contentView.invalidateBackground();
            }
            updateActionModeColors();
        };
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubtitle));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefault));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
        descriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        int[] keys = {
                Theme.key_chat_wallpaper,
                Theme.key_chat_inBubble, Theme.key_chat_inBubbleSelected, Theme.key_chat_inBubbleShadow,
                Theme.key_chat_outBubble, Theme.key_chat_outBubbleSelected, Theme.key_chat_outBubbleShadow,
                Theme.key_chat_messageTextIn, Theme.key_chat_messageTextOut,
                Theme.key_chat_messageLinkIn, Theme.key_chat_messageLinkOut,
                Theme.key_chat_inTimeText, Theme.key_chat_outTimeText,
                Theme.key_chat_outSentCheck, Theme.key_chat_outSentCheckRead, Theme.key_chat_outSentClock,
                Theme.key_chat_selectedBackground, Theme.key_chat_inTimeSelectedText, Theme.key_chat_outTimeSelectedText,
                Theme.key_chat_serviceBackground, Theme.key_checkbox, Theme.key_checkboxCheck,
                Theme.key_chat_outReactionButtonBackground, Theme.key_chat_inReactionButtonBackground,
                Theme.key_actionBarActionModeDefaultIcon, Theme.key_actionBarActionModeDefaultSelector, Theme.key_chat_messagePanelBackground,
        };
        for (int key : keys) {
            descriptions.add(new ThemeDescription(null, 0, null, null, null, redraw, key));
        }
        TextBubbleCell.addMediaThemeDescriptions(descriptions, redraw);
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

    /** What the reply panel / copy shows for a message: the text, or a media label like upstream's MessageObject.messageText. */
    private static CharSequence previewText(TextBubbleMessage message) {
        if (message.type == TextBubbleMessage.TYPE_VOICE) {
            return "🎤 Voice message";
        } else if (message.type == TextBubbleMessage.TYPE_PHOTO) {
            return android.text.TextUtils.isEmpty(message.text) ? "🖼 Photo" : message.text;
        } else if (message.type == TextBubbleMessage.TYPE_DOCUMENT) {
            return "📎 " + message.fileName;
        }
        return message.text == null ? "" : message.text;
    }

    /** A generated landscape (sky gradient, sun, two hills) standing in for a decoded photo. */
    private static Drawable samplePhoto(int width, int height, int variant) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int[][] skies = {{0xff4fa3e0, 0xffbfe3f7}, {0xff3b2667, 0xffff9a62}, {0xff1d4350, 0xffa3d39c}};
        int[][] hills = {{0xff5d9b46, 0xff3f7a32}, {0xff4a2d4f, 0xff2a1830}, {0xff2f6b4f, 0xff1f4a37}};
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, height, skies[variant][0], skies[variant][1], android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        paint.setColor(variant == 1 ? 0xffffd36b : 0xfffff4c2);
        canvas.drawCircle(width * 0.7f, height * 0.38f, Math.min(width, height) * 0.11f, paint);
        paint.setColor(hills[variant][0]);
        canvas.drawOval(-width * 0.3f, height * 0.62f, width * 0.8f, height * 1.4f, paint);
        paint.setColor(hills[variant][1]);
        canvas.drawOval(width * 0.25f, height * 0.7f, width * 1.4f, height * 1.5f, paint);
        return new android.graphics.drawable.BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap);
    }

    /** Pretend download / upload for photos and files: MEDIA_DOWNLOAD -> MEDIA_LOADING (progress) -> MEDIA_READY; tap again cancels. */
    private void toggleFakeDownload(TextBubbleMessage message) {
        if (message.mediaState == TextBubbleMessage.MEDIA_READY) {
            if (message.type == TextBubbleMessage.TYPE_DOCUMENT) {
                Toast.makeText(getParentActivity(), "Open " + message.fileName + ": the host opens the file here", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (message.mediaState == TextBubbleMessage.MEDIA_LOADING) {
            message.mediaState = TextBubbleMessage.MEDIA_DOWNLOAD;
            message.mediaProgress = 0;
            refreshMedia(message);
            return;
        }
        message.mediaState = TextBubbleMessage.MEDIA_LOADING;
        message.mediaProgress = 0;
        refreshMedia(message);
        Runnable step = new Runnable() {
            @Override
            public void run() {
                if (message.mediaState != TextBubbleMessage.MEDIA_LOADING) {
                    return;
                }
                message.mediaProgress = Math.min(1f, message.mediaProgress + 0.04f);
                if (message.mediaProgress >= 1f) {
                    message.mediaState = TextBubbleMessage.MEDIA_READY;
                } else {
                    AndroidUtilities.runOnUIThread(this, 80);
                }
                refreshMedia(message);
            }
        };
        AndroidUtilities.runOnUIThread(step, 80);
    }

    private void refreshMedia(TextBubbleMessage message) {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof TextBubbleCell && ((TextBubbleCell) child).getMessage() == message) {
                if (child instanceof PhotoBubbleCell) {
                    ((PhotoBubbleCell) child).updateButtonState(true);
                } else if (child instanceof DocumentBubbleCell) {
                    ((DocumentBubbleCell) child).updateButtonState(true);
                }
            }
        }
    }

    private static byte[] sampleWaveform() {
        ArrayList<Integer> levels = new ArrayList<>();
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 100; i++) {
            double envelope = Math.sin(Math.PI * i / 100.0) * 0.7 + 0.3;
            levels.add((int) (envelope * (8 + random.nextInt(24))));
        }
        return MediaController.waveformFromLevels(levels);
    }

    private final TextBubbleCell.Delegate cellDelegate = new TextBubbleCell.Delegate() {
        @Override
        public void onReactionPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.Reaction reaction) {
            if (actionBar.isActionModeShowed()) {
                processRowSelect(message);
                return;
            }
            toggleReaction(message, reaction.emoji);
        }

        @Override
        public void onReactionLongPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.Reaction reaction) {
            if (actionBar.isActionModeShowed()) {
                return;
            }
            // upstream opens the list of who reacted; the demo has no users
            Toast.makeText(getParentActivity(), reaction.emoji + " × " + reaction.count + (reaction.chosen ? " (including you)" : ""), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onReplyPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.ReplyTo replyTo) {
            if (actionBar.isActionModeShowed()) {
                processRowSelect(message);
                return;
            }
            scrollToMessage(replyTo.messageId);
        }

        @Override
        public void onLinkPressed(TextBubbleCell cell, TextBubbleMessage message, String url) {
            if (actionBar.isActionModeShowed()) {
                processRowSelect(message);
                return;
            }
            openLink(url);
        }

        @Override
        public void onLinkLongPressed(TextBubbleCell cell, TextBubbleMessage message, String url) {
            if (actionBar.isActionModeShowed()) {
                return;
            }
            // ChatActivity.didPressUrl(longPress): a sheet titled with the link, Open / Copy
            BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity(), false, getResourceProvider());
            builder.setTitle(url);
            builder.setItems(new CharSequence[]{"Open", "Copy"}, (dialog, which) -> {
                if (which == 0) {
                    openLink(url);
                } else if (AndroidUtilities.addToClipboard(url)) {
                    Toast.makeText(getParentActivity(), "Link copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            });
            showDialog(builder.create());
        }

        @Override
        public void onBubbleLongPressed(TextBubbleCell cell, TextBubbleMessage message) {
            if (messageAt(listView.getChildAdapterPosition(cell)) == null) {
                return;
            }
            if (!actionBar.isActionModeShowed()) {
                showActionMode(message);
            } else {
                processRowSelect(message);
            }
        }
    };

    /** Web links open in the browser; @mentions / #hashtags belong to the host (here a toast). */
    private void openLink(String url) {
        if (url.startsWith("@") || url.startsWith("#")) {
            Toast.makeText(getParentActivity(), (url.startsWith("@") ? "Open profile " : "Search ") + url, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
            getParentActivity().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getParentActivity(), "No app can open " + url, Toast.LENGTH_SHORT).show();
        }
    }

    /** ChatActivity.scrollToMessageId + highlightMessageId: brings the message to the middle and flashes it. */
    private void scrollToMessage(long messageId) {
        int position = -1;
        for (int i = 0; i < rows.size(); i++) {
            TextBubbleMessage message = messageAt(i);
            if (message != null && message.id == messageId) {
                position = i;
                break;
            }
        }
        if (position < 0) {
            Toast.makeText(getParentActivity(), "The original message was deleted", Toast.LENGTH_SHORT).show();
            return;
        }
        highlightMessageId = messageId;
        layoutManager.scrollToPositionWithOffset(position, listView.getMeasuredHeight() / 3, false);
        listView.post(() -> {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof TextBubbleCell && ((TextBubbleCell) child).getMessage() != null && ((TextBubbleCell) child).getMessage().id == highlightMessageId) {
                    ((TextBubbleCell) child).setHighlightedAnimated();
                    highlightMessageId = 0;
                }
            }
        });
    }

    private final TextBubbleCell.MediaDelegate mediaDelegate = new TextBubbleCell.MediaDelegate() {
        @Override
        public void onMenuPressed(TextBubbleCell cell, TextBubbleMessage message, float x, float y) {
            if (!actionBar.isActionModeShowed()) {
                showMessageOptions(cell, message, x, y);
            }
        }

        @Override
        public void onMediaPressed(TextBubbleCell cell, TextBubbleMessage message) {
            if (message.type == TextBubbleMessage.TYPE_VOICE) {
                toggleVoice(message);
            } else if (message.type == TextBubbleMessage.TYPE_PHOTO || message.type == TextBubbleMessage.TYPE_DOCUMENT) {
                toggleFakeDownload(message);
            }
        }

        @Override
        public void onSeek(TextBubbleCell cell, TextBubbleMessage message, float progress) {
            if (message == playingVoice && voicePlayer != null) {
                voicePlayer.seekTo((int) (progress * voicePlayer.getDuration()));
            } else if (message == playingVoice) {
                fakePlayStart = System.currentTimeMillis() - (long) (progress * message.duration * 1000);
            }
            message.audioProgressSec = (int) (progress * message.duration);
            refreshVoice(message);
        }
    };

    private void toggleVoice(TextBubbleMessage message) {
        if (playingVoice == message) {
            if (voicePlayer != null) {
                // pause / resume the same file (upstream MediaController.pauseMessage / playMessage)
                if (message.playing) {
                    voicePlayer.pause();
                } else {
                    voicePlayer.start();
                }
            } else if (!message.playing) {
                fakePlayStart = System.currentTimeMillis() - (long) (message.audioProgress * message.duration * 1000);
            }
            message.playing = !message.playing;
            AndroidUtilities.cancelRunOnUIThread(voiceProgressUpdater);
            if (message.playing) {
                AndroidUtilities.runOnUIThread(voiceProgressUpdater);
            }
            refreshVoice(message);
            return;
        }
        stopVoice();
        String path = voicePaths.get(message.id);
        if (path != null) {
            try {
                voicePlayer = new MediaPlayer();
                voicePlayer.setDataSource(path);
                voicePlayer.prepare();
                if (message.audioProgress > 0 && message.audioProgress < 1) {
                    voicePlayer.seekTo((int) (message.audioProgress * voicePlayer.getDuration()));
                }
                voicePlayer.setOnCompletionListener(mp -> finishVoice());
                voicePlayer.start();
            } catch (Exception e) {
                voicePlayer = null;
                Toast.makeText(getParentActivity(), "Can't play " + path, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            fakePlayStart = System.currentTimeMillis() - (long) (message.audioProgress * message.duration * 1000);
        }
        playingVoice = message;
        message.playing = true;
        message.contentUnread = false;
        AndroidUtilities.runOnUIThread(voiceProgressUpdater);
        refreshVoice(message);
    }

    private void updateVoiceProgress() {
        TextBubbleMessage message = playingVoice;
        if (message == null || !message.playing) {
            return;
        }
        float progress;
        int seconds;
        if (voicePlayer != null) {
            int duration = Math.max(1, voicePlayer.getDuration());
            int position = voicePlayer.getCurrentPosition();
            progress = position / (float) duration;
            seconds = position / 1000;
        } else {
            long elapsed = System.currentTimeMillis() - fakePlayStart;
            progress = elapsed / (message.duration * 1000f);
            seconds = (int) (elapsed / 1000);
            if (progress >= 1f) {
                finishVoice();
                return;
            }
        }
        message.audioProgress = Math.min(1f, progress);
        message.audioProgressSec = seconds;
        refreshVoice(message);
        AndroidUtilities.runOnUIThread(voiceProgressUpdater, 32);
    }

    /** Playback reached the end: back to the play icon with the waveform reset (upstream cleanupPlayer). */
    private void finishVoice() {
        TextBubbleMessage message = playingVoice;
        stopVoice();
        if (message != null) {
            refreshVoice(message);
        }
    }

    private void stopVoice() {
        AndroidUtilities.cancelRunOnUIThread(voiceProgressUpdater);
        if (voicePlayer != null) {
            try {
                voicePlayer.release();
            } catch (Exception ignore) {
            }
            voicePlayer = null;
        }
        if (playingVoice != null) {
            playingVoice.playing = false;
            playingVoice.audioProgress = 0;
            playingVoice.audioProgressSec = 0;
            playingVoice = null;
        }
    }

    /** Pushes the message's playback state into its visible cell without a rebind (no item animation). */
    private void refreshVoice(TextBubbleMessage message) {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof VoiceBubbleCell && ((VoiceBubbleCell) child).getMessage() == message) {
                ((VoiceBubbleCell) child).updatePlayingMessageProgress();
            }
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != VIEW_TYPE_DATE;
        }

        @Override
        public int getItemViewType(int position) {
            TextBubbleMessage message = messageAt(position);
            return message == null ? VIEW_TYPE_DATE : message.type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_DATE) {
                ChatActionCell dateCell = new ChatActionCell(context, getResourceProvider());
                dateCell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(dateCell);
            }
            TextBubbleCell cell;
            if (viewType == TextBubbleMessage.TYPE_VOICE) {
                cell = new VoiceBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_PHOTO) {
                cell = new PhotoBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_DOCUMENT) {
                cell = new DocumentBubbleCell(context, getResourceProvider());
            } else {
                cell = new TextBubbleCell(context, getResourceProvider());
            }
            cell.setMediaDelegate(mediaDelegate);
            cell.setDelegate(cellDelegate);
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof ChatActionCell) {
                ((ChatActionCell) holder.itemView).setCustomDate((Long) rows.get(position));
                return;
            }
            TextBubbleMessage message = messageAt(position);
            TextBubbleMessage prev = messageAt(position - 1);
            TextBubbleMessage next = messageAt(position + 1);
            boolean pinnedTop = prev != null && prev.isOut == message.isOut;
            boolean pinnedBottom = next != null && next.isOut == message.isOut;
            TextBubbleCell cell = (TextBubbleCell) holder.itemView;
            cell.setMessage(message, pinnedTop, pinnedBottom);
            cell.setCheckBoxVisible(actionBar.isActionModeShowed(), false);
            cell.setChecked(selectedMessages.contains(message), false);
            if (highlightMessageId != 0 && message.id == highlightMessageId) {
                highlightMessageId = 0;
                cell.setHighlightedAnimated();
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
