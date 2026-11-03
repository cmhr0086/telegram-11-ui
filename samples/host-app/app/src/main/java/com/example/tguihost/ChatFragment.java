package com.example.tguihost;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
// library resources live in the library R (host has nonTransitiveRClass=true by default)
import org.telegram.tgui.R;
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
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.Sticker;
import org.telegram.ui.Components.StickerSet;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.Attachment;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.TelegramUI;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.List;

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
    private EmojiView emojiPanel;
    private StickerSet localPack;
    private final ArrayList<Sticker> recentStickers = new ArrayList<>();
    private final ArrayList<Sticker> favoriteStickers = new ArrayList<>();
    private final ArrayList<Gif> localGifs = new ArrayList<>();

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
        add(localEmoji(), false, TextBubbleMessage.STATE_NONE);
        ArrayList<TextBubbleMessage> photos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            android.graphics.drawable.GradientDrawable image = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xff5a9bd5 + i * 0x00080000, 0xffa8d6bd});
            TextBubbleMessage item = TextBubbleMessage.photo(nextId++, false, "12:34", TextBubbleMessage.STATE_NONE, image, null);
            item.photoWidth = i == 0 ? 320 : 480;
            item.photoHeight = i == 0 ? 600 : 320;
            photos.add(item);
        }
        messages.add(TextBubbleMessage.album(nextId++, false, "12:34", TextBubbleMessage.STATE_NONE,
                photos, org.telegram.messenger.ApplicationLoader.applicationContext.getString(com.example.tguihost.R.string.local_album_sample)));
        messages.add(TextBubbleMessage.roundVideo(nextId++, true, "12:35", TextBubbleMessage.STATE_READ,
                photos.get(0).photo, null, 6));
        Context app = org.telegram.messenger.ApplicationLoader.applicationContext;
        // HostApp already copies this exact upstream JSON file for the inline emoji.
        // TGS format accepts either gzip TGS or plain Lottie JSON through the same rlottie decoder.
        Sticker sticker = new Sticker(90001, Sticker.Format.TGS,
                new java.io.File(app.getFilesDir(), "local-emoji.json"), null, 512, 512, "🐥");
        localPack = new StickerSet(90000, app.getString(com.example.tguihost.R.string.local_sticker_pack),
                java.util.Collections.singletonList(sticker));
        recentStickers.add(sticker);
        localGifs.add(new Gif(90002, new java.io.File(app.getFilesDir(), "local-sticker.gif"), null, 200, 113));
        messages.add(TextBubbleMessage.sticker(nextId++, false, "12:36", TextBubbleMessage.STATE_NONE, sticker));
        messages.add(TextBubbleMessage.gif(nextId++, true, "12:37", TextBubbleMessage.STATE_READ, localGifs.get(0),
                app.getString(com.example.tguihost.R.string.local_gif_caption)));
        messages.add(TextBubbleMessage.contact(nextId++, false, "12:38", TextBubbleMessage.STATE_NONE,
                new TextBubbleMessage.Contact(1, app.getString(com.example.tguihost.R.string.attach_sample_contact_one),
                        "+1 202 555 0101", null, true, true)));
        messages.add(TextBubbleMessage.music(nextId++, true, "12:39", TextBubbleMessage.STATE_READ,
                app.getString(com.example.tguihost.R.string.music_sample_title),
                app.getString(com.example.tguihost.R.string.music_sample_artist), 12,
                app.getString(com.example.tguihost.R.string.music_host)));
        messages.add(TextBubbleMessage.location(nextId++, false, "12:40", TextBubbleMessage.STATE_NONE,
                new TextBubbleMessage.Location(0, 0, app.getString(com.example.tguihost.R.string.location_sample_title),
                        app.getString(com.example.tguihost.R.string.location_sample_address), sampleAttachmentMap(app))));
        messages.add(TextBubbleMessage.location(nextId++, true, "12:40", TextBubbleMessage.STATE_READ,
                new TextBubbleMessage.Location(0, 0, null, null, sampleAttachmentMap(app))));
        messages.add(TextBubbleMessage.document(nextId++, false, "12:42", TextBubbleMessage.STATE_NONE,
                "Host_map_preview.png", 786_432, sampleAttachmentMap(app), app.getString(com.example.tguihost.R.string.document_thumbnail_caption)));
        TextBubbleMessage hostDownload = TextBubbleMessage.document(nextId++, true, "12:42", TextBubbleMessage.STATE_READ,
                "A_long_file_name_for_a_host_thumbnail.jpg", 2_457_600, photos.get(0).photo, null);
        hostDownload.mediaState = TextBubbleMessage.MEDIA_DOWNLOAD;
        messages.add(hostDownload);
        messages.add(TextBubbleMessage.document(nextId++, false, "12:42", TextBubbleMessage.STATE_NONE,
                "No_thumbnail.txt", 12_288, null, app.getString(com.example.tguihost.R.string.document_plain_caption)));
        TextBubbleMessage inlineSample = new TextBubbleMessage(nextId++, app.getString(com.example.tguihost.R.string.inline_sample_text), false, "12:43", TextBubbleMessage.STATE_NONE);
        inlineSample.inlineKeyboard = java.util.Arrays.asList(
                java.util.Arrays.asList(
                        new TextBubbleMessage.InlineButton("callback", app.getString(com.example.tguihost.R.string.inline_callback), TextBubbleMessage.InlineButton.Type.CALLBACK, false),
                        new TextBubbleMessage.InlineButton("url", app.getString(com.example.tguihost.R.string.inline_url), TextBubbleMessage.InlineButton.Type.URL, false)),
                java.util.Arrays.asList(
                        new TextBubbleMessage.InlineButton("switch", app.getString(com.example.tguihost.R.string.inline_switch), TextBubbleMessage.InlineButton.Type.SWITCH_INLINE, false),
                        new TextBubbleMessage.InlineButton("copy", app.getString(com.example.tguihost.R.string.inline_copy), TextBubbleMessage.InlineButton.Type.COPY, false)));
        messages.add(inlineSample);
        for (TextBubbleMessage message : messages) bindGroupSender(message);
        TextBubbleMessage.ForwardedFrom source = new TextBubbleMessage.ForwardedFrom(-100,
                app.getString(com.example.tguihost.R.string.forward_source_name));
        messages.get(0).forwardedFrom = source;
        messages.add(TextBubbleMessage.poll(nextId++, false, "12:41", TextBubbleMessage.STATE_NONE, samplePoll(app, false, false)));
        messages.add(TextBubbleMessage.poll(nextId++, true, "12:41", TextBubbleMessage.STATE_READ, samplePoll(app, true, false)));
        messages.add(TextBubbleMessage.poll(nextId++, false, "12:41", TextBubbleMessage.STATE_NONE, samplePoll(app, false, true)));
        for (TextBubbleMessage message : messages) {
            if (message.type == TextBubbleMessage.TYPE_STICKER || message.type == TextBubbleMessage.TYPE_ALBUM
                    || message.type == TextBubbleMessage.TYPE_ROUND_VIDEO) message.forwardedFrom = source;
        }
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

    private void bindGroupSender(TextBubbleMessage message) {
        message.group = true;
        if (message.date == 0) message.date = System.currentTimeMillis() / 1000;
        message.sender = new TextBubbleMessage.Sender(message.isOut ? 99 : 1, message.isOut ? "Me" : title, null);
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
                } else if (id == 2) {
                    enterView.setFieldText(localEmoji());
                    enterView.getEditField().updateAnimatedEmoji(true);
                } else if (id == 3) {
                    org.telegram.ui.Cells.ChatUnreadCell unread = new org.telegram.ui.Cells.ChatUnreadCell(context, getResourceProvider());
                    unread.setText(context.getString(R.string.UnreadMessages));
                    org.telegram.ui.ActionBar.BottomSheet sheet = new org.telegram.ui.ActionBar.BottomSheet(context, false, getResourceProvider());
                    sheet.setCustomView(unread);
                    showDialog(sheet);
                } else if (id == 4) {
                    android.widget.LinearLayout events = new android.widget.LinearLayout(context);
                    events.setOrientation(android.widget.LinearLayout.VERTICAL);
                    org.telegram.ui.Cells.ServiceMessage.Person alice = new org.telegram.ui.Cells.ServiceMessage.Person(1L, "Alice");
                    org.telegram.ui.Cells.ServiceMessage[] examples = {
                        org.telegram.ui.Cells.ServiceMessage.join(1, alice, false),
                        org.telegram.ui.Cells.ServiceMessage.invite(2, alice,
                            new org.telegram.ui.Cells.ServiceMessage.Person(2L, "Bob"), false, false),
                        org.telegram.ui.Cells.ServiceMessage.pin(3, alice, false, messages.get(0).id, messages.get(0).text)
                    };
                    for (org.telegram.ui.Cells.ServiceMessage event : examples) {
                        org.telegram.ui.Cells.ChatActionCell cell = new org.telegram.ui.Cells.ChatActionCell(context, getResourceProvider());
                        cell.setMessage(event);
                        cell.setDelegate(new org.telegram.ui.Cells.ChatActionCell.Delegate() {
                            @Override public void onUserPressed(Object userId) {
                                android.widget.Toast.makeText(context, context.getString(com.example.tguihost.R.string.service_user_selected, userId), android.widget.Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onPinnedMessagePressed(Object messageId) {
                                android.widget.Toast.makeText(context, context.getString(com.example.tguihost.R.string.service_pin_selected, messageId), android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
                        events.addView(cell, new android.widget.LinearLayout.LayoutParams(-1, -2));
                    }
                    org.telegram.ui.ActionBar.BottomSheet sheet = new org.telegram.ui.ActionBar.BottomSheet(context, false, getResourceProvider());
                    sheet.setCustomView(events);
                    showDialog(sheet);
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
        actionBar.createMenu().addItem(3, R.drawable.ic_ab_new).setContentDescription(context.getString(R.string.UnreadMessages));
        actionBar.createMenu().addItem(4, R.drawable.msg_info).setContentDescription(context.getString(com.example.tguihost.R.string.service_messages));
        actionBar.createMenu().addItem(2, R.drawable.msg_emoji_smiles)
                .setContentDescription(context.getString(com.example.tguihost.R.string.local_emoji_insert));

        contentView = new ChatInputLayout(context, parentLayout);
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        fragmentView = contentView;

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        layoutManager.setStackFromEnd(true);
        listView.setLayoutManager(layoutManager);
        listView.setItemAnimator(new ChatBubbleItemAnimator());
        listView.addItemDecoration(new org.telegram.ui.Cells.ChatAvatarDecoration());
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
                android.text.SpannableStringBuilder styled = new android.text.SpannableStringBuilder(text.toString());
                org.telegram.messenger.MediaDataController.addTextStyleRuns(entities, text, styled);
                org.telegram.messenger.MediaDataController.addAnimatedEmojiSpans(entities, styled, Theme.chat_msgTextPaint.getFontMetricsInt());
                send(styled);
            }
            @Override public void onEmojiPanelCreated(EmojiView panel) { emojiPanel = panel; updateMediaPanel(); }
            @Override public void onSendSticker(Sticker sticker) { sendSticker(sticker); }
            @Override public void onSendGif(Gif gif) {
                append(TextBubbleMessage.gif(nextId++, true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENT, gif, null));
            }
            @Override public void onStickerSetRequested(StickerSet set) { showStickerSet(set); }
            @Override public void onStickerFavoriteChanged(Sticker sticker, boolean favorite) { setFavorite(sticker, favorite); }
            @Override public void onGifRemoved(Gif gif) { localGifs.remove(gif); updateMediaPanel(); }
            @Override public void didPressAttachButton() { showAttachments(); }
        });
        enterView.setAllowStickersAndGifs(true, true, true);
        enterView.setRecordButtonVisible(false);
        contentView.addView(enterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        contentView.setEnterView(enterView);

        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        contentView.setActionBar(actionBar);
        return fragmentView;
    }

    private void showAttachments() {
        if (getParentActivity() == null) return;
        new ChatAttachAlert(getParentActivity(), new ChatAttachAlert.Delegate() {
            @Override public List<Attachment.Contact> getContacts() {
                ArrayList<Attachment.Contact> contacts = new ArrayList<>();
                contacts.add(new Attachment.Contact(1, getParentActivity().getString(com.example.tguihost.R.string.attach_sample_contact_one), "+1 202 555 0101", null));
                contacts.add(new Attachment.Contact(2, getParentActivity().getString(com.example.tguihost.R.string.attach_sample_contact_two), "+1 202 555 0102", null));
                return contacts;
            }
            @Override public View createLocationView(Context context, ChatAttachAlert.LocationCallback callback) {
                TextView map = new TextView(context);
                map.setGravity(Gravity.CENTER); map.setTextSize(18); map.setTextColor(android.graphics.Color.WHITE);
                map.setBackground(sampleAttachmentMap(context)); map.setText(com.example.tguihost.R.string.attach_sample_map);
                map.setShadowLayer(3, 1, 1, android.graphics.Color.BLACK);
                map.setOnClickListener(v -> callback.onLocationSelected(new Attachment.Location(0, 0,
                        context.getString(com.example.tguihost.R.string.attach_sample_location), sampleAttachmentMap(context))));
                return map;
            }
            @Override public void onSend(List<Attachment> selection, CharSequence caption) {
                String time = timeFormat.format(new Date());
                for (Attachment item : selection) {
                    if (item.type == Attachment.Type.FILE) {
                        append(TextBubbleMessage.document(nextId++, true, time, TextBubbleMessage.STATE_SENT, item.name, item.size));
                    } else if (item.type == Attachment.Type.CONTACT) {
                        append(TextBubbleMessage.contact(nextId++, true, time, TextBubbleMessage.STATE_SENT,
                                new TextBubbleMessage.Contact(item.contact.id, item.contact.name, item.contact.phone, item.contact.avatar)));
                    } else if (item.type == Attachment.Type.POLL) {
                        ArrayList<TextBubbleMessage.PollOption> options = new ArrayList<>();
                        for (int i = 0; i < item.poll.options.size(); i++) {
                            options.add(new TextBubbleMessage.PollOption(Integer.toString(i), item.poll.options.get(i), 0, false));
                        }
                        append(TextBubbleMessage.poll(nextId++, true, time, TextBubbleMessage.STATE_SENT,
                                new TextBubbleMessage.Poll(item.poll.question, options, 0, item.poll.anonymous, item.poll.multipleChoice, false, false)));
                    } else if (item.type == Attachment.Type.LOCATION) {
                        append(TextBubbleMessage.location(nextId++, true, time, TextBubbleMessage.STATE_SENT,
                                new TextBubbleMessage.Location(item.location.latitude, item.location.longitude,
                                        item.location.title, null, item.location.mapImage)));
                    } else {
                        String description;
                        switch (item.type) {
                            case PHOTO: case VIDEO:
                                description = getParentActivity().getString(com.example.tguihost.R.string.attach_media_result, item.name, item.uri); break;
                            case CONTACT:
                                description = getParentActivity().getString(com.example.tguihost.R.string.attach_contact_result, item.contact.name, item.contact.phone); break;
                            case LOCATION:
                                description = getParentActivity().getString(com.example.tguihost.R.string.attach_location_result, item.location.title, item.location.latitude, item.location.longitude); break;
                            case POLL:
                                description = getParentActivity().getString(com.example.tguihost.R.string.attach_poll_result, item.poll.question, android.text.TextUtils.join(" / ", item.poll.options)); break;
                            default: continue;
                        }
                        append(new TextBubbleMessage(nextId++, description, true, time, TextBubbleMessage.STATE_SENT));
                    }
                }
                if (!android.text.TextUtils.isEmpty(caption)) append(new TextBubbleMessage(nextId++, caption, true, time, TextBubbleMessage.STATE_SENT));
            }
        }, getResourceProvider()).show();
    }

    private static TextBubbleMessage.Poll samplePoll(Context context, boolean multiple, boolean closed) {
        ArrayList<TextBubbleMessage.PollOption> options = new ArrayList<>();
        int[] labels = {com.example.tguihost.R.string.poll_option_walk, com.example.tguihost.R.string.poll_option_coffee, com.example.tguihost.R.string.poll_option_read};
        for (int i = 0; i < labels.length; i++) {
            options.add(new TextBubbleMessage.PollOption(Integer.toString(i), context.getString(labels[i]), closed ? new int[]{4, 3, 2}[i] : 0, closed && i == 0));
        }
        return new TextBubbleMessage.Poll(context.getString(multiple ? com.example.tguihost.R.string.poll_question_multiple : com.example.tguihost.R.string.poll_question),
                options, closed ? 9 : 0, !multiple, multiple, closed, false);
    }

    private static TextBubbleMessage.Poll localPollResult(TextBubbleMessage.Poll poll, List<String> ids) {
        ArrayList<TextBubbleMessage.PollOption> options = new ArrayList<>();
        for (TextBubbleMessage.PollOption option : poll.options) {
            boolean chosen = ids.contains(option.id);
            options.add(new TextBubbleMessage.PollOption(option.id, option.text, option.voters + (chosen ? 1 : 0), chosen));
        }
        return new TextBubbleMessage.Poll(poll.question, options, poll.totalVoters + 1, poll.anonymous, poll.multiple, poll.closed, false);
    }

    private void showPollResults(TextBubbleMessage message) {
        if (getParentActivity() == null || message.poll == null) return;
        StringBuilder lines = new StringBuilder();
        for (TextBubbleMessage.PollOption option : message.poll.options) {
            if (lines.length() > 0) lines.append('\n');
            lines.append(getParentActivity().getString(com.example.tguihost.R.string.poll_result_line, option.text, option.voters));
        }
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(message.poll.question).setMessage(lines)
                .setPositiveButton(getParentActivity().getString(org.telegram.tgui.R.string.OK), null).show();
    }

    private static android.graphics.drawable.Drawable sampleAttachmentMap(Context context) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(480, 240, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap); canvas.drawColor(0xffa9c7b3);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffe7e3cf); paint.setStrokeWidth(18);
        canvas.drawLine(0, 70, 480, 155, paint); canvas.drawLine(105, 0, 195, 240, paint);
        canvas.drawLine(330, 0, 270, 240, paint);
        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    private void send(CharSequence text) {
        TextBubbleMessage m = new TextBubbleMessage(nextId++, text, true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENT);
        append(m);
    }

    private void append(TextBubbleMessage m) {
        bindGroupSender(m);
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

    private void updateMediaPanel() {
        if (emojiPanel != null) emojiPanel.setMediaData(java.util.Collections.singletonList(localPack), recentStickers, favoriteStickers, localGifs);
    }

    private void sendSticker(Sticker sticker) {
        recentStickers.remove(sticker);
        recentStickers.add(0, sticker);
        updateMediaPanel();
        append(TextBubbleMessage.sticker(nextId++, true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENT, sticker));
    }

    private void setFavorite(Sticker sticker, boolean favorite) {
        favoriteStickers.remove(sticker);
        if (favorite) favoriteStickers.add(sticker);
        updateMediaPanel();
    }

    private void showStickerSet(StickerSet set) {
        showDialog(new StickersAlert(getParentActivity(), set, new StickersAlert.Delegate() {
            @Override public void onSendSticker(Sticker sticker) { sendSticker(sticker); }
            @Override public boolean isFavorite(Sticker sticker) { return favoriteStickers.contains(sticker); }
            @Override public void onFavoriteChanged(Sticker sticker, boolean favorite) { setFavorite(sticker, favorite); }
        }, getResourceProvider()));
    }

    private static CharSequence localEmoji() {
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder("🐥");
        text.setSpan(new org.telegram.ui.Components.AnimatedEmojiSpan(90001, Theme.chat_msgTextPaint.getFontMetricsInt()),
                0, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private void showMenu(View cell, TextBubbleMessage message, float x, float y) {
        MessageContextMenu menu = new MessageContextMenu(getParentActivity(), getResourceProvider());
        if (!android.text.TextUtils.isEmpty(message.text)) menu.addItem(R.drawable.msg_copy, "Copy", () -> {
            AndroidUtilities.addToClipboard(message.text.toString());
            Toast.makeText(getParentActivity(), "Copied", Toast.LENGTH_SHORT).show();
        });
        menu.addItem(R.drawable.msg_delete, "Delete", true, () -> {
            int index = messages.indexOf(message);
            messages.remove(index);
            listView.getAdapter().notifyItemRemoved(index);
        });
        menu.setReactions(MessageContextMenu.DEFAULT_REACTIONS);
        menu.setSelectedReactions(message);
        menu.setDelegate(new MessageContextMenu.Delegate() {
            @Override
            public void onReactionClick(String emoji) {
                message.toggleReaction(emoji);
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
        TextBubbleCell.addMediaThemeDescriptions(list, redraw);
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
            TextBubbleCell cell;
            if (viewType == TextBubbleMessage.TYPE_ALBUM) cell = new org.telegram.ui.Cells.AlbumBubbleCell(context, getResourceProvider());
            else if (viewType == TextBubbleMessage.TYPE_ROUND_VIDEO) cell = new org.telegram.ui.Cells.RoundVideoBubbleCell(context, getResourceProvider());
            else if (viewType == TextBubbleMessage.TYPE_STICKER) cell = new org.telegram.ui.Cells.StickerBubbleCell(context, getResourceProvider());
            else if (viewType == TextBubbleMessage.TYPE_GIF) cell = new org.telegram.ui.Cells.GifBubbleCell(context, getResourceProvider());
            else if (viewType == TextBubbleMessage.TYPE_DOCUMENT) cell = new org.telegram.ui.Cells.DocumentBubbleCell(context, getResourceProvider());
            else if (viewType == TextBubbleMessage.TYPE_MUSIC) cell = new org.telegram.ui.Cells.MusicBubbleCell(context, getResourceProvider());
            else if (viewType == TextBubbleMessage.TYPE_POLL) cell = new org.telegram.ui.Cells.PollBubbleCell(context, getResourceProvider());
            else if (viewType == TextBubbleMessage.TYPE_LOCATION) cell = new org.telegram.ui.Cells.LocationBubbleCell(context, getResourceProvider());
            else if (viewType == TextBubbleMessage.TYPE_CONTACT) cell = new org.telegram.ui.Cells.ContactBubbleCell(context, getResourceProvider());
            else cell = new TextBubbleCell(context, getResourceProvider());
            cell.setMediaDelegate(new TextBubbleCell.MediaDelegate() {
                @Override public void onPollVote(TextBubbleCell cell, TextBubbleMessage message, List<String> optionIds) {
                    if (actionBar.isActionModeShowed() || message.poll == null || message.poll.pending) return;
                    message.poll = localPollResult(message.poll, optionIds);
                    int position = messages.indexOf(message);
                    if (position >= 0) notifyItemChanged(position);
                }
                @Override public void onPollOptionPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.PollOption option) {
                    if (actionBar.isActionModeShowed() || getParentActivity() == null) return;
                    Toast.makeText(getParentActivity(), getParentActivity().getString(com.example.tguihost.R.string.poll_result_line, option.text, option.voters), Toast.LENGTH_SHORT).show();
                }
                @Override public void onPollResults(TextBubbleCell cell, TextBubbleMessage message) {
                    if (!actionBar.isActionModeShowed()) showPollResults(message);
                }
                @Override public void onLocationPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.Location location) {
                    if (actionBar.isActionModeShowed()) { toggle(message); return; }
                    Toast.makeText(context, context.getString(com.example.tguihost.R.string.location_action,
                            location.latitude, location.longitude), Toast.LENGTH_SHORT).show();
                }
                @Override public void onContactAction(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.Contact contact, int action) {
                    if (actionBar.isActionModeShowed()) { toggle(message); return; }
                    int label = action == org.telegram.ui.Cells.ContactBubbleCell.ACTION_MESSAGE ? org.telegram.tgui.R.string.SharedContactMessage
                            : action == org.telegram.ui.Cells.ContactBubbleCell.ACTION_ADD ? org.telegram.tgui.R.string.SharedContactAdd : org.telegram.tgui.R.string.ViewContact;
                    Toast.makeText(context, context.getString(com.example.tguihost.R.string.contact_action,
                            context.getString(label), contact.name, contact.id), Toast.LENGTH_SHORT).show();
                }
                @Override public void onMediaPressed(TextBubbleCell source, TextBubbleMessage item) {
                    if (item.type == TextBubbleMessage.TYPE_DOCUMENT) {
                        if (actionBar.isActionModeShowed()) { toggle(item); return; }
                        if (item.mediaState == TextBubbleMessage.MEDIA_READY) {
                            Toast.makeText(context, context.getString(com.example.tguihost.R.string.document_open_action, item.fileName), Toast.LENGTH_SHORT).show();
                        } else {
                            item.mediaState = item.mediaState == TextBubbleMessage.MEDIA_DOWNLOAD ? TextBubbleMessage.MEDIA_LOADING : TextBubbleMessage.MEDIA_DOWNLOAD;
                            item.mediaProgress = item.mediaState == TextBubbleMessage.MEDIA_LOADING ? .45f : 0;
                            ((org.telegram.ui.Cells.DocumentBubbleCell) source).updateButtonState(true);
                        }
                    } else if (item.type == TextBubbleMessage.TYPE_MUSIC) {
                        Toast.makeText(context, com.example.tguihost.R.string.music_host, Toast.LENGTH_SHORT).show();
                    } else if (item.type == TextBubbleMessage.TYPE_ROUND_VIDEO) {
                        Toast.makeText(context, com.example.tguihost.R.string.round_video_host, Toast.LENGTH_SHORT).show();
                    } else if (item.type == TextBubbleMessage.TYPE_STICKER) {
                        showStickerSet(localPack);
                    } else if (item.type == TextBubbleMessage.TYPE_GIF) {
                        showMenu(source, item, source.getWidth() / 2f, source.getHeight() / 2f);
                    }
                }
                @Override public void onSeek(TextBubbleCell source, TextBubbleMessage item, float progress) {
                    if (item.type == TextBubbleMessage.TYPE_MUSIC) {
                        Toast.makeText(context, context.getString(com.example.tguihost.R.string.music_seek, progress), Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onMenuPressed(TextBubbleCell source, TextBubbleMessage item, float x, float y) {
                    showMenu(source, item, x, y);
                }
                @Override public void onAlbumMediaPressed(org.telegram.ui.Cells.AlbumBubbleCell source,
                        TextBubbleMessage album, TextBubbleMessage item, int index) {
                    Toast.makeText(context, context.getString(com.example.tguihost.R.string.album_item, index + 1), Toast.LENGTH_SHORT).show();
                }
            });
            cell.setDelegate(new TextBubbleCell.Delegate() {
                @Override public void onInlineButtonPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.InlineButton button) {
                    Toast.makeText(context, context.getString(com.example.tguihost.R.string.inline_action, button.id), Toast.LENGTH_SHORT).show();
                    if (button.type == TextBubbleMessage.InlineButton.Type.SWITCH_INLINE) enterView.setFieldText(context.getString(com.example.tguihost.R.string.inline_query));
                    else if (button.type == TextBubbleMessage.InlineButton.Type.COPY) AndroidUtilities.addToClipboard(context.getString(com.example.tguihost.R.string.inline_copy_value));
                }
                @Override public void onInlineButtonLongPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.InlineButton button) {
                    Toast.makeText(context, context.getString(com.example.tguihost.R.string.inline_long_action, button.id), Toast.LENGTH_SHORT).show();
                }

                @Override public void onForwardPressed(TextBubbleCell sourceCell, TextBubbleMessage item, TextBubbleMessage.ForwardedFrom source) {
                    Toast.makeText(context, context.getString(com.example.tguihost.R.string.forward_source_selected,
                            source.name, source.sourceId), Toast.LENGTH_SHORT).show();
                }
                @Override public void onBubbleLongPressed(TextBubbleCell source, TextBubbleMessage item) {
                    if (!actionBar.isActionModeShowed()) showActionMode();
                    toggle(item);
                }
            });
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextBubbleMessage m = messages.get(position);
            boolean pinnedTop = position > 0 && org.telegram.ui.Cells.MessageGrouping.canMerge(messages.get(position - 1), m);
            boolean pinnedBottom = position < messages.size() - 1 && org.telegram.ui.Cells.MessageGrouping.canMerge(m, messages.get(position + 1));
            TextBubbleCell cell = (TextBubbleCell) holder.itemView;
            cell.setAvatarDrawnByList(true);
            cell.setMessage(m, pinnedTop, pinnedBottom);
            cell.setCheckBoxVisible(actionBar.isActionModeShowed(), false);
            cell.setChecked(selected.contains(m), false);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).type;
        }
    }
}
