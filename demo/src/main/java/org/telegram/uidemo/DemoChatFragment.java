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
import org.telegram.ui.Cells.ServiceMessage;
import org.telegram.ui.Cells.MessageGrouping;
import org.telegram.ui.Cells.ChatAvatarDecoration;
import org.telegram.ui.Cells.DocumentBubbleCell;
import org.telegram.ui.Cells.PhotoBubbleCell;
import org.telegram.ui.Cells.AlbumBubbleCell;
import org.telegram.ui.Cells.RoundVideoBubbleCell;
import org.telegram.ui.Cells.StickerBubbleCell;
import org.telegram.ui.Cells.GifBubbleCell;
import org.telegram.ui.Components.Sticker;
import org.telegram.ui.Components.StickerSet;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.Attachment;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.bots.ReplyKeyboard;
import org.telegram.ui.Components.Mention;
import org.telegram.ui.Components.MentionsController;
import org.telegram.ui.Components.EmojiSuggestion;
import org.telegram.ui.Components.EmojiSuggestionQuery;
import org.telegram.ui.Components.EmojiSuggestionsController;
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
import org.telegram.ui.MediaViewer;
import org.telegram.ui.TelegramUI;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;

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
    private static final int MENU_LOCAL_EMOJI = 3;
    private static final int MENU_ALBUM_TEN = 4;
    private static final int MENU_UNREAD = 5;
    private static final int MENU_SERVICE = 6;
    private static final int MENU_GROUP = 7;
    private static final int MENU_FORWARD = 8;
    private static final int MENU_CONTACT = 9;
    private static final int MENU_MUSIC = 20;
    private static final int MENU_LOCATION = 21;
    private static final int MENU_POLL = 22;
    private static final int MENU_DOCUMENT_THUMB = 23;
    private static final int MENU_INLINE = 24;
    private static final int MENU_REPLY_KEYBOARD = 25;
    private static final int MENU_MENTIONS = 26;
    private static final int MENU_SUGGEST_EMOJI = 27;
    private static final int MENU_BOT_COMMANDS = 28;
    private long firstInlineId;
    private final ArrayList<Runnable> inlineCompletions = new ArrayList<>();
    private long firstDocumentThumbnailId;
    private long firstPollId;
    private final ArrayList<Runnable> pollCompletions = new ArrayList<>();
    private long firstLocationId;
    private long firstMusicId;
    private long firstContactId;
    private long firstForwardedId;
    private boolean groupMode;
    private ActionBarMenuSubItem groupItem;
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
    private LocalStickerSamples stickerSamples;
    private EmojiView mediaPanel;
    private ChatInputTopPanel topPanel;
    private final ArrayList<TextBubbleMessage> messages = new ArrayList<>();
    private long nextId = 1;
    private int replyIndex;
    private TextBubbleMessage replyingTo;
    /** Adapter rows: messages, service events, unread marker, or Long day separators. */
    private final ArrayList<Object> rows = new ArrayList<>();
    private final ArrayList<ServiceMessage> serviceEvents = new ArrayList<>();
    private static final int VIEW_TYPE_DATE = 100;
    private static final int VIEW_TYPE_UNREAD = 101;
    private static final int VIEW_TYPE_SERVICE = 102;
    private static final Object UNREAD_MARKER = new Object();
    private long firstUnreadId;
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
    private LocalRoundVideoPlayer roundPlayer;
    private LocalMusicPlayer musicPlayer;
    private long fakePlayStart;
    private final Runnable voiceProgressUpdater = this::updateVoiceProgress;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        ChatSounds.preload();
        TextBubbleMessage.ForwardedFrom source = new TextBubbleMessage.ForwardedFrom(-100,
                ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.forward_source_name));
        sampleDaysAgo = 3;
        addSample("Hey! This is the chat background from the current theme.", false, "10:02", TextBubbleMessage.STATE_NONE);
        addSample("The input below is upstream ChatActivityEnterView: select text for bold / italic / links, tap the smiley to switch to the emoji panel.", false, "10:02", TextBubbleMessage.STATE_NONE);
        sampleDaysAgo = 1;
        TextBubbleMessage looksLike = addSample("Looks just like Telegram", true, "10:03", TextBubbleMessage.STATE_READ);
        looksLike.forwardedFrom = source;
        firstForwardedId = looksLike.id;
        // reactions: pills inside the bubble (ReactionsLayoutInBubble), tap one to toggle yours
        react(looksLike, "👍", 3, true);
        react(looksLike, "❤", 1, false);
        addSample("Bubbles are Theme.MessageDrawable", true, "10:03", TextBubbleMessage.STATE_READ);
        addSample("with time and check marks drawn like ChatMessageCell 🙂", true, "10:04", TextBubbleMessage.STATE_READ);
        addSample("Hold the microphone to record a voice message, slide left to cancel or up to lock.", false, "10:05", TextBubbleMessage.STATE_NONE);
        TextBubbleMessage voice = TextBubbleMessage.voice(nextId++, false, "10:05", TextBubbleMessage.STATE_NONE, 7, sampleWaveform());
        voice.contentUnread = true;
        voice.forwardedFrom = source;
        react(voice, "🔥", 2, false);
        addSample(voice);
        addSample("Tap a message for the menu and reactions, long-press to select messages.", false, "10:05", TextBubbleMessage.STATE_NONE);
        TextBubbleMessage emojiSet = addSample("Emoji use Telegram's own set 😎🔥🎉 in bubbles and in the input field", false, "10:06", TextBubbleMessage.STATE_NONE);
        react(emojiSet, "👍", 5, true);
        react(emojiSet, "😁", 2, false);
        react(emojiSet, "🎉", 1, false);
        sampleDaysAgo = 0;
        addSample("Links work too: https://telegram.org, core.telegram.org/api and mentions like @durov or #hashtags. Long-press a link to copy it.", false, "10:06", TextBubbleMessage.STATE_NONE);
        TextBubbleMessage emojiReply = react(addSample("👋", false, "10:06", TextBubbleMessage.STATE_NONE), "❤", 1, false);
        emojiReply.forwardedFrom = source;
        emojiReply.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        addSample("😂❤️👍", true, "10:07", TextBubbleMessage.STATE_READ).forwardedFrom = source;
        addSample("ok", true, "10:07", TextBubbleMessage.STATE_SENT);
        // replies: the quote is upstream's reply panel; tapping it scrolls to the original and flashes it
        TextBubbleMessage quoted = addSample("It does! Tap this quote to jump to the original message.", false, "10:07", TextBubbleMessage.STATE_NONE);
        quoted.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        quoted.forwardedFrom = source;
        // photos: the host hands in a Drawable (here generated, no network); one waits for a tap to "download"
        TextBubbleMessage landscape = TextBubbleMessage.photo(nextId++, false, "10:08", TextBubbleMessage.STATE_NONE, samplePhoto(1280, 853, 0), null);
        react(landscape, "🥰", 4, false);
        landscape.forwardedFrom = source;
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
        // a video: a photo bubble with the play button and duration, MediaViewer plays the bundled mp4
        android.net.Uri videoUri = android.net.Uri.parse("android.resource://" + ApplicationLoader.applicationContext.getPackageName() + "/" + org.telegram.uidemo.R.raw.sample_video);
        TextBubbleMessage video = TextBubbleMessage.video(nextId++, true, "10:10", TextBubbleMessage.STATE_READ, sampleVideoThumb(videoUri), videoUri, 6, "Short clip 🎬");
        addSample(video);
        // files: name / size / state from the host, the round button reports taps
        TextBubbleMessage guide = TextBubbleMessage.document(nextId++, false, "10:11", TextBubbleMessage.STATE_NONE, "Telegram_UI_Guide.pdf", 2_457_600);
        react(guide, "👍", 2, false);
        addSample(guide);
        TextBubbleMessage archive = TextBubbleMessage.document(nextId++, false, "10:11", TextBubbleMessage.STATE_NONE, "tgui-11.4.2-ui3-release-with-a-very-long-file-name-sources.zip", 48_234_496);
        archive.mediaState = TextBubbleMessage.MEDIA_DOWNLOAD;
        archive.forwardedFrom = new TextBubbleMessage.ForwardedFrom(0,
                ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.forward_anonymous_name));
        addSample(archive);
        addSample(TextBubbleMessage.document(nextId++, true, "10:12", TextBubbleMessage.STATE_READ, "notes.txt", 12_288));
        TextBubbleMessage contact = TextBubbleMessage.contact(nextId++, false, "10:12", TextBubbleMessage.STATE_NONE,
                new TextBubbleMessage.Contact(1, ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.attach_sample_contact_one),
                        "+1 202 555 0101", null, true, true));
        contact.forwardedFrom = source;
        contact.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        firstContactId = contact.id;
        addSample(contact);
        addSample(TextBubbleMessage.contact(nextId++, true, "10:12", TextBubbleMessage.STATE_READ,
                new TextBubbleMessage.Contact(2, ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.attach_sample_contact_two),
                        "+1 202 555 0102", null)));
        TextBubbleMessage song = TextBubbleMessage.music(nextId++, false, "10:12", TextBubbleMessage.STATE_NONE,
                ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.music_sample_title),
                ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.music_sample_artist), 12,
                ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.music_sample_caption));
        song.forwardedFrom = source;
        song.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        firstMusicId = song.id;
        react(song, "👍", 2, false);
        addSample(song);
        TextBubbleMessage anotherSong = TextBubbleMessage.music(nextId++, true, "10:12", TextBubbleMessage.STATE_READ,
                ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.music_sample_title_two),
                ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.music_sample_artist), 12, null);
        addSample(anotherSong);
        TextBubbleMessage downloadSong = TextBubbleMessage.music(nextId++, false, "10:12", TextBubbleMessage.STATE_NONE,
                ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.music_sample_download), null, 12, null);
        downloadSong.mediaState = TextBubbleMessage.MEDIA_DOWNLOAD;
        addSample(downloadSong);
        Context sampleContext = ApplicationLoader.applicationContext;
        TextBubbleMessage venue = TextBubbleMessage.location(nextId++, false, "10:12", TextBubbleMessage.STATE_NONE,
                new TextBubbleMessage.Location(0, 0, sampleContext.getString(org.telegram.uidemo.R.string.location_sample_title),
                        sampleContext.getString(org.telegram.uidemo.R.string.location_sample_address), sampleAttachmentMap(sampleContext)));
        venue.forwardedFrom = source;
        venue.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        firstLocationId = venue.id;
        react(venue, "👍", 2, false);
        addSample(venue);
        addSample(TextBubbleMessage.location(nextId++, true, "10:12", TextBubbleMessage.STATE_READ,
                new TextBubbleMessage.Location(0, 0, null, null, sampleAttachmentMap(sampleContext))));
        addSample(TextBubbleMessage.location(nextId++, false, "10:12", TextBubbleMessage.STATE_NONE,
                new TextBubbleMessage.Location(0, 0, sampleContext.getString(org.telegram.uidemo.R.string.location_sample_missing),
                        sampleContext.getString(org.telegram.uidemo.R.string.location_sample_address), null)));
        TextBubbleMessage pollSample = TextBubbleMessage.poll(nextId++, false, "10:12", TextBubbleMessage.STATE_NONE,
                samplePoll(sampleContext, false, false));
        firstPollId = pollSample.id;
        pollSample.forwardedFrom = source;
        pollSample.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        react(pollSample, "👍", 2, false);
        addSample(pollSample);
        addSample(TextBubbleMessage.poll(nextId++, true, "10:12", TextBubbleMessage.STATE_READ,
                samplePoll(sampleContext, true, false)));
        addSample(TextBubbleMessage.poll(nextId++, false, "10:12", TextBubbleMessage.STATE_NONE,
                samplePoll(sampleContext, false, true)));
        TextBubbleMessage thumbFile = TextBubbleMessage.document(nextId++, false, "10:12", TextBubbleMessage.STATE_NONE,
                "Landscape_reference.jpg", 2_457_600, samplePhoto(480, 320, 0),
                sampleContext.getString(org.telegram.uidemo.R.string.document_thumbnail_caption));
        thumbFile.forwardedFrom = source;
        thumbFile.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        react(thumbFile, "👍", 2, false);
        firstDocumentThumbnailId = thumbFile.id;
        addSample(thumbFile);
        TextBubbleMessage downloadFile = TextBubbleMessage.document(nextId++, true, "10:12", TextBubbleMessage.STATE_READ,
                "A_long_file_name_for_the_portrait_preview_and_download_state.jpg", 8_388_608,
                samplePhoto(320, 480, 1), null);
        downloadFile.mediaState = TextBubbleMessage.MEDIA_DOWNLOAD;
        addSample(downloadFile);
        addSample(TextBubbleMessage.document(nextId++, false, "10:12", TextBubbleMessage.STATE_NONE,
                "Caption_without_a_thumbnail.txt", 12_288, null,
                sampleContext.getString(org.telegram.uidemo.R.string.document_plain_caption)));
        TextBubbleMessage inlineText = addSample(sampleContext.getString(org.telegram.uidemo.R.string.inline_sample_text), false, "10:12", TextBubbleMessage.STATE_NONE);
        firstInlineId = inlineText.id;
        inlineText.forwardedFrom = source;
        inlineText.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        inlineText.inlineKeyboard = sampleInlineKeyboard(sampleContext, false);
        react(inlineText, "👍", 2, false);
        TextBubbleMessage inlinePhoto = TextBubbleMessage.photo(nextId++, true, "10:12", TextBubbleMessage.STATE_READ,
                samplePhoto(480, 320, 2), sampleContext.getString(org.telegram.uidemo.R.string.inline_sample_photo));
        inlinePhoto.inlineKeyboard = sampleInlineKeyboard(sampleContext, false);
        addSample(inlinePhoto);
        try {
            LocalEmojiSamples.install(ApplicationLoader.applicationContext);
            addSample(LocalEmojiSamples.text(ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.local_emoji_sample)), false, "10:13", TextBubbleMessage.STATE_NONE);
        } catch (java.io.IOException error) {
            org.telegram.messenger.FileLog.e(error);
        }
        addSample(sampleAlbum(2, false, false));
        addSample(sampleAlbum(3, true, true));
        addSample(sampleAlbum(4, false, true));
        addSample(sampleAlbum(5, true, false));
        TextBubbleMessage roundIncoming = TextBubbleMessage.roundVideo(nextId++, false, "10:15", TextBubbleMessage.STATE_NONE,
                sampleVideoThumb(videoUri), videoUri, 6);
        roundIncoming.contentUnread = true;
        roundIncoming.forwardedFrom = source;
        roundIncoming.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
        addSample(roundIncoming);
        addSample(TextBubbleMessage.roundVideo(nextId++, true, "10:15", TextBubbleMessage.STATE_READ,
                sampleVideoThumb(videoUri), videoUri, 6));
        try {
            stickerSamples = new LocalStickerSamples(ApplicationLoader.applicationContext);
            for (Sticker sticker : stickerSamples.sets.get(0).stickers) {
                TextBubbleMessage stickerMessage = TextBubbleMessage.sticker(nextId++, sticker.id % 2 == 0, "10:16", TextBubbleMessage.STATE_READ, sticker);
                stickerMessage.forwardedFrom = source;
                if (!stickerMessage.isOut) stickerMessage.replyTo = TextBubbleMessage.ReplyTo.of(looksLike, "You");
                addSample(stickerMessage);
            }
            addSample(TextBubbleMessage.gif(nextId++, false, "10:17", TextBubbleMessage.STATE_NONE,
                    stickerSamples.gifs.get(0), ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.local_gif_caption)));
        } catch (java.io.IOException error) { org.telegram.messenger.FileLog.e(error); }
        for (TextBubbleMessage item : messages) {
            if (item.type == TextBubbleMessage.TYPE_ALBUM && !item.isOut || item.type == TextBubbleMessage.TYPE_GIF) item.forwardedFrom = source;
        }
        if (!messages.isEmpty()) firstUnreadId = messages.get(messages.size() - 1).id;
        ServiceMessage.Person alice = new ServiceMessage.Person(1L, "Alice");
        ServiceMessage.Person bob = new ServiceMessage.Person(2L, "Bob");
        serviceEvents.add(ServiceMessage.join("join", alice, false));
        serviceEvents.add(ServiceMessage.invite("invite", alice, bob, false, false));
        serviceEvents.add(ServiceMessage.title("title", bob, false,
                ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.service_group_title)));
        serviceEvents.add(ServiceMessage.pin("pin", alice, false, looksLike.id, looksLike.text));
        rebuildRows();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        if (enterView != null) {
            enterView.onDestroy();
        }
        for (Runnable completion : pollCompletions) AndroidUtilities.cancelRunOnUIThread(completion);
        pollCompletions.clear();
        for (Runnable completion : inlineCompletions) AndroidUtilities.cancelRunOnUIThread(completion);
        inlineCompletions.clear();
        stopFakeVideo();
        stopVoice();
        if (roundPlayer != null) roundPlayer.stop();
        if (musicPlayer != null) musicPlayer.stop();
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
        bindSampleSender(message);
        messages.add(message);
    }

    private void bindSampleSender(TextBubbleMessage message) {
        message.group = groupMode;
        long senderId = message.isOut ? 99L : (message.id / 4 % 2 == 0 ? 1L : 2L);
        message.sender = new TextBubbleMessage.Sender(senderId,
                senderId == 99 ? "Me" : senderId == 1 ? "Alice" : "Bob", null);
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
            if (message.id == firstUnreadId) {
                rows.addAll(serviceEvents);
                rows.add(UNREAD_MARKER);
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
        boolean separatorAtTop = position >= 0 && position < rows.size() && rows.get(position) instanceof Long
                && topChild.getTop() > top - topChild.getHeight();
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
        if (roundPlayer != null) roundPlayer.stop();
        if (musicPlayer != null) musicPlayer.stop();
        // BackDrawable like ChatActivity: the arrow turns into × in selection mode
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Chat");
        actionBar.setSubtitle("ChatActivityEnterView");
        // upstream ChatAvatarContainer: tapping the header opens the profile
        View.OnClickListener openProfile = v -> {
            if (!actionBar.isActionModeShowed()) {
                DemoProfiles.openAlice(this);
            }
        };
        actionBar.getTitleTextView().setOnClickListener(openProfile);
        actionBar.getSubtitleTextView().setOnClickListener(openProfile);
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
                } else if (id >= ACTION_EDIT && id <= ACTION_DELETE) {
                    processActionModeItem(id);
                } else if (id == MENU_NIGHT) {
                    TelegramUI.toggleDayNight(nightItem);
                } else if (id == MENU_RECORD_BUTTON) {
                    boolean visible = !enterView.isRecordButtonVisible();
                    enterView.setRecordButtonVisible(visible);
                    recordButtonItem.setChecked(visible);
                } else if (id == MENU_LOCAL_EMOJI) {
                    enterView.setFieldText(LocalEmojiSamples.text(getParentActivity().getString(org.telegram.uidemo.R.string.local_emoji_sample)));
                    enterView.getEditField().updateAnimatedEmoji(true);
                } else if (id == MENU_ALBUM_TEN) {
                    sendOutgoing(sampleAlbum(10, true, true));
                } else if (id == MENU_UNREAD) {
                    int row = rows.indexOf(UNREAD_MARKER);
                    if (row >= 0) layoutManager.scrollToPositionWithOffset(row, 0);
                } else if (id == MENU_SERVICE && !serviceEvents.isEmpty()) {
                    int row = rows.indexOf(serviceEvents.get(0));
                    if (row >= 0) layoutManager.scrollToPositionWithOffset(row, 0);
                } else if (id == MENU_GROUP) {
                    groupMode = !groupMode;
                    groupItem.setChecked(groupMode);
                    for (TextBubbleMessage message : messages) message.group = groupMode;
                    listView.getAdapter().notifyDataSetChanged();
                    listView.invalidate();
                } else if (id == MENU_FORWARD) {
                    scrollToMessage(firstForwardedId);
                } else if (id == MENU_CONTACT) {
                    scrollToMessage(firstContactId);
                } else if (id == MENU_MUSIC) {
                    scrollToMessage(firstMusicId);
                } else if (id == MENU_LOCATION) {
                    scrollToMessage(firstLocationId);
                } else if (id == MENU_POLL) {
                    scrollToMessage(firstPollId);
                } else if (id == MENU_BOT_COMMANDS) {
                    enterView.showBotCommandsMenu();
                } else if (id == MENU_SUGGEST_EMOJI) {
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider())
                            .setTitle(getParentActivity().getString(org.telegram.uidemo.R.string.suggest_emoji_menu))
                            .setItems(new CharSequence[]{getParentActivity().getString(org.telegram.uidemo.R.string.suggest_emoji_keyword_example),
                                    getParentActivity().getString(org.telegram.uidemo.R.string.suggest_emoji_animated_example)}, (dialog, which) -> {
                                enterView.getEditField().requestFocus();
                                CharSequence text = which == 0 ? getParentActivity().getString(org.telegram.uidemo.R.string.suggest_emoji_keyword) : "🐥🐥";
                                enterView.setFieldText(org.telegram.messenger.Emoji.replaceEmoji(text, enterView.getEditField().getPaint().getFontMetricsInt(), true));
                            }).show();
                } else if (id == MENU_MENTIONS) {
                    enterView.getEditField().requestFocus();
                    enterView.setFieldText("@");
                } else if (id == MENU_REPLY_KEYBOARD) {
                    showReplyKeyboardOptions();
                } else if (id == MENU_INLINE) {
                    scrollToMessage(firstInlineId);
                } else if (id == MENU_DOCUMENT_THUMB) {
                    scrollToMessage(firstDocumentThumbnailId);
                }
            }
        });
        nightItem = actionBar.createMenu().addItem(MENU_NIGHT, R.drawable.msg_theme);
        nightItem.setContentDescription("Switch day / night");
        ActionBarMenuItem otherItem = actionBar.createMenu().addItem(0, R.drawable.ic_ab_other);
        otherItem.setContentDescription("More options");
        recordButtonItem = otherItem.addSubItem(MENU_RECORD_BUTTON, R.drawable.input_mic, "Voice / video button", true);
        recordButtonItem.setChecked(true);
        otherItem.addSubItem(MENU_LOCAL_EMOJI, R.drawable.msg_emoji_smiles, context.getString(org.telegram.uidemo.R.string.local_emoji_insert));
        otherItem.addSubItem(MENU_ALBUM_TEN, R.drawable.msg_photos, context.getString(org.telegram.uidemo.R.string.album_ten));
        otherItem.addSubItem(MENU_UNREAD, R.drawable.ic_ab_new, context.getString(R.string.UnreadMessages));
        otherItem.addSubItem(MENU_SERVICE, R.drawable.msg_info, context.getString(org.telegram.uidemo.R.string.service_messages));
        groupItem = otherItem.addSubItem(MENU_GROUP, R.drawable.msg_groups, context.getString(org.telegram.uidemo.R.string.group_messages), true);
        groupItem.setChecked(groupMode);
        otherItem.addSubItem(MENU_FORWARD, R.drawable.msg_forward, context.getString(org.telegram.uidemo.R.string.forward_messages));
        otherItem.addSubItem(MENU_CONTACT, R.drawable.msg_addcontact, context.getString(org.telegram.uidemo.R.string.contact_messages));
        otherItem.addSubItem(MENU_MUSIC, R.drawable.bot_music, context.getString(org.telegram.uidemo.R.string.music_messages));
        otherItem.addSubItem(MENU_LOCATION, R.drawable.msg_location, context.getString(org.telegram.uidemo.R.string.location_messages));
        otherItem.addSubItem(MENU_POLL, R.drawable.msg_message, context.getString(org.telegram.uidemo.R.string.poll_messages));
        otherItem.addSubItem(MENU_DOCUMENT_THUMB, R.drawable.msg_message, context.getString(org.telegram.uidemo.R.string.document_thumbnail_messages));
        otherItem.addSubItem(MENU_INLINE, R.drawable.msg_message, context.getString(org.telegram.uidemo.R.string.inline_messages));
        otherItem.addSubItem(MENU_REPLY_KEYBOARD, R.drawable.input_bot2, context.getString(org.telegram.uidemo.R.string.reply_keyboard_menu));
        otherItem.addSubItem(MENU_MENTIONS, R.drawable.msg_addcontact, context.getString(org.telegram.uidemo.R.string.mention_menu));
        otherItem.addSubItem(MENU_SUGGEST_EMOJI, R.drawable.msg_emoji_smiles, context.getString(org.telegram.uidemo.R.string.suggest_emoji_menu));
        otherItem.addSubItem(MENU_BOT_COMMANDS, R.drawable.input_bot2, context.getString(org.telegram.uidemo.R.string.bot_commands_menu));

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
        listView.addItemDecoration(new ChatAvatarDecoration());
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
        roundPlayer = new LocalRoundVideoPlayer(contentView, listView);
        musicPlayer = new LocalMusicPlayer(context, this::refreshMusic);

        enterView = new ChatActivityEnterView(getParentActivity(), contentView, this, true, getResourceProvider());
        enterView.setDelegate(new EnterViewDelegate());
        enterView.setAllowStickersAndGifs(true, true, true);
        enterView.setDialogId(777, UserConfig.selectedAccount);
        enterView.setRoundVideoEnabled(true);
        contentView.addView(enterView, contentView.getChildCount(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        contentView.setEnterView(enterView);
        enterView.setReplyKeyboard(sampleReplyKeyboard(context, true, false), false);
        enterView.setBotCommands(java.util.Arrays.asList(
                new org.telegram.ui.bots.BotCommand("start", "start", context.getString(org.telegram.uidemo.R.string.bot_command_start)),
                new org.telegram.ui.bots.BotCommand("help", "help", context.getString(org.telegram.uidemo.R.string.bot_command_help)),
                new org.telegram.ui.bots.BotCommand("settings", "settings", context.getString(org.telegram.uidemo.R.string.bot_command_settings)),
                new org.telegram.ui.bots.BotCommand("weather", "weather@local_demo", context.getString(org.telegram.uidemo.R.string.bot_command_weather)),
                new org.telegram.ui.bots.BotCommand("about", "about", context.getString(org.telegram.uidemo.R.string.bot_command_about)),
                new org.telegram.ui.bots.BotCommand("feedback", "feedback", context.getString(org.telegram.uidemo.R.string.bot_command_feedback))));
        java.util.List<Mention> mentionUsers = java.util.Arrays.asList(
                new Mention(101, context.getString(org.telegram.uidemo.R.string.mention_alice), "alice", null),
                new Mention(102, context.getString(org.telegram.uidemo.R.string.mention_bob), "bob", null),
                new Mention(103, context.getString(org.telegram.uidemo.R.string.mention_carol), null, null),
                new Mention(104, context.getString(org.telegram.uidemo.R.string.mention_david), "david", null),
                new Mention(105, context.getString(org.telegram.uidemo.R.string.mention_eve), "eve", null));
        new MentionsController(enterView, contentView, (query, results) -> {
            String prefix = query.query.toLowerCase(java.util.Locale.ROOT);
            java.util.ArrayList<Mention> matches = new java.util.ArrayList<>();
            for (Mention user : mentionUsers) {
                if (user.name.toString().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)
                        || user.username != null && user.username.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) matches.add(user);
            }
            results.onResult(matches);
        }, getResourceProvider());
        EmojiSuggestionsController emojiSuggestions = new EmojiSuggestionsController(enterView, contentView, (query, results) -> {
            java.util.ArrayList<EmojiSuggestion> matches = new java.util.ArrayList<>();
            if (query.type == EmojiSuggestionQuery.Type.EMOJI || context.getString(org.telegram.uidemo.R.string.suggest_emoji_keyword).startsWith(query.query.toLowerCase(java.util.Locale.ROOT))) {
                matches.add(new EmojiSuggestion("duck-animated", "🐥", 90001));
                String[] codes = {"😀", "😃", "😄", "😁", "😆", "😅", "😂", "😊", "🥰", "😍", "🙂", "🐥"};
                for (int i = 0; i < codes.length; i++) matches.add(new EmojiSuggestion("unicode-" + i, codes[i], 0));
            }
            results.onResult(matches);
        }, getResourceProvider());
        emojiSuggestions.setDelegate(new EmojiSuggestionsController.Delegate() {
            @Override public void onLongPressed(EmojiSuggestion suggestion) {
                android.widget.Toast.makeText(context, context.getString(org.telegram.uidemo.R.string.suggest_emoji_host_action, suggestion.emoji), android.widget.Toast.LENGTH_SHORT).show();
            }
        });

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
        if (roundPlayer != null) roundPlayer.stop();
        if (musicPlayer != null) musicPlayer.stop();
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
        if (message.type != TextBubbleMessage.TYPE_ROUND_VIDEO) {
            menu.setReactions(MessageContextMenu.DEFAULT_REACTIONS);
        }
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
        if (musicPlayer != null && musicPlayer.getPlayingMessage() == message) musicPlayer.stop();
        int row = rowOf(message);
        messages.remove(index);
        // the day's separator goes too when this was its only message
        int firstRemoved = row;
        while (firstRemoved > 0 && (rows.get(firstRemoved - 1) == UNREAD_MARKER
                || rows.get(firstRemoved - 1) instanceof ServiceMessage)) firstRemoved--;
        boolean removeSeparator = firstRemoved > 0 && rows.get(firstRemoved - 1) instanceof Long && (row + 1 >= rows.size() || rows.get(row + 1) instanceof Long);
        if (removeSeparator) firstRemoved--;
        int removedCount = row - firstRemoved + 1;
        rebuildRows();
        RecyclerView.Adapter<?> adapter = listView.getAdapter();
        if (adapter != null && row >= 0) {
            adapter.notifyItemRangeRemoved(firstRemoved, removedCount);
            row = firstRemoved;
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
        @Override public void onReplyKeyboardButtonPressed(ReplyKeyboard keyboard, ReplyKeyboard.Button button) { onReplyKeyboardButton(keyboard, button); }

        @Override public void onEmojiPanelCreated(EmojiView panel) { mediaPanel = panel; refreshMediaPanel(); }
        @Override public void onSendSticker(Sticker sticker) { sendSticker(sticker); }
        @Override public void onSendGif(Gif gif) {
            sendOutgoing(TextBubbleMessage.gif(nextId++, true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENDING, gif, null));
        }
        @Override public void onStickerSetRequested(StickerSet set) { openStickerSet(set); }
        @Override public void onStickerFavoriteChanged(Sticker sticker, boolean favorite) { changeFavorite(sticker, favorite); }
        @Override public void onGifRemoved(Gif gif) {
            if (stickerSamples != null) {
                for (int i = stickerSamples.gifs.size() - 1; i >= 0; i--) if (stickerSamples.gifs.get(i).id == gif.id) stickerSamples.gifs.remove(i);
                refreshMediaPanel();
            }
        }

        @Override
        public void onSendText(CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean notify, int scheduleDate) {
            // like upstream MessageObject: plain text + entities (the editor's IME / emoji spans are not carried over)
            SpannableStringBuilder styled = new SpannableStringBuilder(text.toString());
            if (entities != null && !entities.isEmpty()) {
                MediaDataController.addTextStyleRuns(entities, text, styled);
                MediaDataController.addAnimatedEmojiSpans(entities, styled, Theme.chat_msgTextPaint.getFontMetricsInt());
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
                    MediaDataController.addAnimatedEmojiSpans(entities, styled, Theme.chat_msgTextPaint.getFontMetricsInt());
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
            showAttachments();
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

    private void refreshMediaPanel() {
        if (mediaPanel != null && stickerSamples != null) mediaPanel.setMediaData(stickerSamples.sets,
                stickerSamples.recent, stickerSamples.favorites, stickerSamples.gifs);
    }
    private void sendSticker(Sticker sticker) {
        if (stickerSamples != null) {
            for (int i = stickerSamples.recent.size() - 1; i >= 0; i--) if (stickerSamples.recent.get(i).id == sticker.id) stickerSamples.recent.remove(i);
            stickerSamples.recent.add(0, sticker);
            refreshMediaPanel();
        }
        sendOutgoing(TextBubbleMessage.sticker(nextId++, true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENDING, sticker));
    }
    private void changeFavorite(Sticker sticker, boolean favorite) {
        if (stickerSamples == null) return;
        for (int i = stickerSamples.favorites.size() - 1; i >= 0; i--) if (stickerSamples.favorites.get(i).id == sticker.id) stickerSamples.favorites.remove(i);
        if (favorite) stickerSamples.favorites.add(sticker);
        refreshMediaPanel();
    }
    private void openStickerSet(StickerSet set) {
        showDialog(new StickersAlert(getParentActivity(), set, new StickersAlert.Delegate() {
            @Override public void onSendSticker(Sticker sticker) { sendSticker(sticker); }
            @Override public boolean isFavorite(Sticker sticker) {
                for (Sticker item : stickerSamples.favorites) if (item.id == sticker.id) return true;
                return false;
            }
            @Override public void onFavoriteChanged(Sticker sticker, boolean favorite) { changeFavorite(sticker, favorite); }
        }, getResourceProvider()));
    }

    private void showReplyKeyboardOptions() {
        if (getParentActivity() == null) return;
        Context context = getParentActivity();
        new org.telegram.ui.ActionBar.AlertDialog.Builder(context, getResourceProvider())
                .setTitle(context.getString(org.telegram.uidemo.R.string.reply_keyboard_menu))
                .setItems(new CharSequence[]{context.getString(org.telegram.uidemo.R.string.reply_keyboard_compact),
                        context.getString(org.telegram.uidemo.R.string.reply_keyboard_full),
                        context.getString(org.telegram.uidemo.R.string.reply_keyboard_once),
                        context.getString(org.telegram.uidemo.R.string.reply_keyboard_remove)}, (dialog, which) -> {
                    if (which == 3) enterView.setReplyKeyboard(null);
                    else {
                        enterView.setReplyKeyboard(sampleReplyKeyboard(context, which != 1, which == 2), false);
                        enterView.showReplyKeyboard();
                    }
                }).show();
    }

    private static ReplyKeyboard sampleReplyKeyboard(Context context, boolean resize, boolean singleUse) {
        ReplyKeyboard.Button greeting = new ReplyKeyboard.Button("greeting", context.getString(org.telegram.uidemo.R.string.reply_keyboard_greeting), ReplyKeyboard.Button.Type.TEXT);
        List<List<ReplyKeyboard.Button>> rows = singleUse ? java.util.Collections.singletonList(java.util.Collections.singletonList(greeting)) : java.util.Arrays.asList(
                java.util.Arrays.asList(greeting, new ReplyKeyboard.Button("thanks", context.getString(org.telegram.uidemo.R.string.reply_keyboard_thanks), ReplyKeyboard.Button.Type.TEXT)),
                java.util.Arrays.asList(new ReplyKeyboard.Button("contact", context.getString(org.telegram.uidemo.R.string.reply_keyboard_contact), ReplyKeyboard.Button.Type.REQUEST_CONTACT),
                        new ReplyKeyboard.Button("location", context.getString(org.telegram.uidemo.R.string.reply_keyboard_location), ReplyKeyboard.Button.Type.REQUEST_LOCATION)),
                java.util.Arrays.asList(new ReplyKeyboard.Button("poll", context.getString(org.telegram.uidemo.R.string.reply_keyboard_poll), ReplyKeyboard.Button.Type.POLL),
                        new ReplyKeyboard.Button("app", context.getString(org.telegram.uidemo.R.string.reply_keyboard_app), ReplyKeyboard.Button.Type.WEB_VIEW)));
        return new ReplyKeyboard("sample-reply", rows, resize, singleUse, context.getString(org.telegram.uidemo.R.string.reply_keyboard_placeholder));
    }

    private void clearReplyAfterKeyboardSend() {
        if (topPanel != null && topPanel.getMode() == ChatInputTopPanel.MODE_REPLY) topPanel.hide(true);
        replyingTo = null;
        enterView.setReplyingMessageObject(null);
    }

    private void onReplyKeyboardButton(ReplyKeyboard keyboard, ReplyKeyboard.Button button) {
        if (getParentActivity() == null) return;
        if (button.type == ReplyKeyboard.Button.Type.TEXT) {
            sendOutgoing(button.text);
            clearReplyAfterKeyboardSend();
        } else if (button.type == ReplyKeyboard.Button.Type.REQUEST_CONTACT) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle(getParentActivity().getString(org.telegram.uidemo.R.string.reply_keyboard_contact))
                    .setMessage(getParentActivity().getString(org.telegram.uidemo.R.string.reply_keyboard_contact_confirm))
                    .setPositiveButton(getParentActivity().getString(org.telegram.tgui.R.string.Send), (dialog, which) -> {
                        sendOutgoing(TextBubbleMessage.contact(nextId++, true, timeFormat.format(new Date()), TextBubbleMessage.STATE_SENT, new TextBubbleMessage.Contact(99, getParentActivity().getString(org.telegram.uidemo.R.string.reply_keyboard_sample_contact), "+1 202 555 0199", null)));
                        clearReplyAfterKeyboardSend();
                    }).setNegativeButton(getParentActivity().getString(org.telegram.tgui.R.string.Cancel), null).show();
        } else if (button.type == ReplyKeyboard.Button.Type.REQUEST_LOCATION || button.type == ReplyKeyboard.Button.Type.POLL) {
            enterView.hidePopup(false);
            showAttachments(button.type == ReplyKeyboard.Button.Type.POLL ? ChatAttachAlert.TAB_POLL : ChatAttachAlert.TAB_LOCATION);
        } else {
            Toast.makeText(getParentActivity(), getParentActivity().getString(org.telegram.uidemo.R.string.reply_keyboard_action, button.id), Toast.LENGTH_SHORT).show();
        }
    }

    private void showAttachments() { showAttachments(ChatAttachAlert.TAB_GALLERY); }

    private void showAttachments(int initialTab) {
        if (getParentActivity() == null) return;
        ChatAttachAlert sheet = new ChatAttachAlert(getParentActivity(), new ChatAttachAlert.Delegate() {
            @Override public List<Attachment.Contact> getContacts() {
                ArrayList<Attachment.Contact> result = new ArrayList<>();
                result.add(new Attachment.Contact(1, getParentActivity().getString(org.telegram.uidemo.R.string.attach_sample_contact_one), "+1 202 555 0101", null));
                result.add(new Attachment.Contact(2, getParentActivity().getString(org.telegram.uidemo.R.string.attach_sample_contact_two), "+1 202 555 0102", null));
                return result;
            }
            @Override public View createLocationView(Context context, ChatAttachAlert.LocationCallback callback) {
                TextView map = new TextView(context);
                map.setGravity(Gravity.CENTER); map.setTextSize(18); map.setTextColor(android.graphics.Color.WHITE);
                map.setShadowLayer(3, 1, 1, android.graphics.Color.BLACK);
                map.setText(org.telegram.uidemo.R.string.attach_sample_map);
                map.setBackground(sampleAttachmentMap(context));
                map.setOnClickListener(v -> callback.onLocationSelected(new Attachment.Location(0, 0,
                        context.getString(org.telegram.uidemo.R.string.attach_sample_location), sampleAttachmentMap(context))));
                return map;
            }
            @Override public void onSend(List<Attachment> selection, CharSequence caption) {
                String time = timeFormat.format(new Date());
                for (Attachment item : selection) {
                    if (item.type == Attachment.Type.FILE) {
                        appendMessage(TextBubbleMessage.document(nextId++, true, time, TextBubbleMessage.STATE_SENT, item.name, item.size));
                    } else if (item.type == Attachment.Type.CONTACT) {
                        appendMessage(TextBubbleMessage.contact(nextId++, true, time, TextBubbleMessage.STATE_SENT,
                                new TextBubbleMessage.Contact(item.contact.id, item.contact.name, item.contact.phone, item.contact.avatar)));
                    } else if (item.type == Attachment.Type.POLL) {
                        ArrayList<TextBubbleMessage.PollOption> options = new ArrayList<>();
                        for (int i = 0; i < item.poll.options.size(); i++) {
                            options.add(new TextBubbleMessage.PollOption(Integer.toString(i), item.poll.options.get(i), 0, false));
                        }
                        appendMessage(TextBubbleMessage.poll(nextId++, true, time, TextBubbleMessage.STATE_SENT,
                                new TextBubbleMessage.Poll(item.poll.question, options, 0, item.poll.anonymous, item.poll.multipleChoice, false, false)));
                    } else if (item.type == Attachment.Type.LOCATION) {
                        appendMessage(TextBubbleMessage.location(nextId++, true, time, TextBubbleMessage.STATE_SENT,
                                new TextBubbleMessage.Location(item.location.latitude, item.location.longitude,
                                        item.location.title, null, item.location.mapImage)));
                    } else {
                        String description;
                        switch (item.type) {
                            case PHOTO: case VIDEO:
                                description = getParentActivity().getString(org.telegram.uidemo.R.string.attach_media_result, item.name, item.uri); break;
                            case CONTACT:
                                description = getParentActivity().getString(org.telegram.uidemo.R.string.attach_contact_result, item.contact.name, item.contact.phone); break;
                            case LOCATION:
                                description = getParentActivity().getString(org.telegram.uidemo.R.string.attach_location_result, item.location.title, item.location.latitude, item.location.longitude); break;
                            case POLL:
                                description = getParentActivity().getString(org.telegram.uidemo.R.string.attach_poll_result, item.poll.question, android.text.TextUtils.join(" / ", item.poll.options)); break;
                            default: continue;
                        }
                        appendMessage(new TextBubbleMessage(nextId++, description, true, time, TextBubbleMessage.STATE_SENT));
                    }
                }
                if (!android.text.TextUtils.isEmpty(caption)) appendMessage(new TextBubbleMessage(nextId++, caption, true, time, TextBubbleMessage.STATE_SENT));
            }
        }, getResourceProvider());
        sheet.selectTab(initialTab);
        sheet.show();
    }

    private static TextBubbleMessage.Poll samplePoll(Context context, boolean multiple, boolean closed) {
        ArrayList<TextBubbleMessage.PollOption> options = new ArrayList<>();
        int[] labels = {org.telegram.uidemo.R.string.poll_option_walk, org.telegram.uidemo.R.string.poll_option_coffee, org.telegram.uidemo.R.string.poll_option_read};
        for (int i = 0; i < labels.length; i++) {
            options.add(new TextBubbleMessage.PollOption(Integer.toString(i), context.getString(labels[i]), closed ? new int[]{4, 3, 2}[i] : 0, closed && i == 0));
        }
        return new TextBubbleMessage.Poll(context.getString(multiple ? org.telegram.uidemo.R.string.poll_question_multiple : org.telegram.uidemo.R.string.poll_question),
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
            lines.append(getParentActivity().getString(org.telegram.uidemo.R.string.poll_result_line, option.text, option.voters));
        }
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(message.poll.question).setMessage(lines)
                .setPositiveButton(getParentActivity().getString(org.telegram.tgui.R.string.OK), null).show();
    }

    private static Drawable sampleAttachmentMap(Context context) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(480, 240, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(0xffa9c7b3);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffe7e3cf); paint.setStrokeWidth(18);
        canvas.drawLine(0, 70, 480, 155, paint); canvas.drawLine(105, 0, 195, 240, paint);
        canvas.drawLine(330, 0, 270, 240, paint);
        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
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
        bindSampleSender(message);
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
        if (enterView != null && (enterView.botCommandsMenuIsShowing() || enterView.isPopupShowing())) {
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
        if (roundPlayer != null) roundPlayer.stop();
        if (musicPlayer != null) musicPlayer.stop();
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
                for (int i = 0; i < listView.getChildCount(); i++) {
                    if (listView.getChildAt(i) instanceof org.telegram.ui.Cells.ChatUnreadCell)
                        ((org.telegram.ui.Cells.ChatUnreadCell) listView.getChildAt(i)).updateColors();
                }
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
                Theme.key_chat_unreadMessagesStartBackground, Theme.key_chat_unreadMessagesStartText,
                Theme.key_chat_unreadMessagesStartArrowIcon,
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
        if (message.type == TextBubbleMessage.TYPE_POLL && message.poll != null) return message.poll.question;
        if (message.type == TextBubbleMessage.TYPE_LOCATION && message.location != null) {
            return android.text.TextUtils.isEmpty(message.location.title)
                    ? ApplicationLoader.applicationContext.getString(R.string.ChatLocation) : message.location.title;
        }
        if (message.type == TextBubbleMessage.TYPE_MUSIC) {
            return android.text.TextUtils.isEmpty(message.musicTitle)
                    ? ApplicationLoader.applicationContext.getString(R.string.AttachMusic) : message.musicTitle;
        }
        if (message.type == TextBubbleMessage.TYPE_CONTACT && message.contact != null) {
            return ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.attach_contact_result,
                    message.contact.name, message.contact.phone);
        }
        if (message.type == TextBubbleMessage.TYPE_ROUND_VIDEO) {
            return ApplicationLoader.applicationContext.getString(R.string.tgui_round_video);
        }
        if (message.type == TextBubbleMessage.TYPE_VOICE) {
            return "🎤 Voice message";
        } else if (message.type == TextBubbleMessage.TYPE_PHOTO) {
            if (android.text.TextUtils.isEmpty(message.text)) {
                return message.videoUri != null ? "📹 Video" : "🖼 Photo";
            }
            return message.text;
        } else if (message.type == TextBubbleMessage.TYPE_DOCUMENT) {
            return "📎 " + message.fileName;
        }
        return message.text == null ? "" : message.text;
    }

    /** The video's first frame as its bubble thumbnail (the host's preview); a generated photo if that fails. */
    private static Drawable sampleVideoThumb(android.net.Uri uri) {
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            retriever.setDataSource(ApplicationLoader.applicationContext, uri);
            android.graphics.Bitmap frame = retriever.getFrameAtTime(0);
            if (frame != null) {
                return new android.graphics.drawable.BitmapDrawable(ApplicationLoader.applicationContext.getResources(), frame);
            }
        } catch (Exception ignore) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignore) {
            }
        }
        return samplePhoto(640, 360, 0);
    }

    /**
     * ChatActivity.didPressImage -> PhotoViewer.openPhoto: every loaded photo / video of the chat,
     * in order, opened on {@code message} and animated out of its bubble.
     */
    private void openMediaViewer(TextBubbleMessage message) {
        ArrayList<MediaViewer.Item> items = new ArrayList<>();
        ArrayList<TextBubbleMessage> sources = new ArrayList<>();
        int index = -1;
        SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy 'at' HH:mm", Locale.US);
        ArrayList<TextBubbleMessage> flattened = new ArrayList<>();
        for (TextBubbleMessage parent : messages) {
            if (parent.type == TextBubbleMessage.TYPE_ALBUM) flattened.addAll(parent.albumMedia);
            else flattened.add(parent);
        }
        for (TextBubbleMessage m : flattened) {
            if (m.type != TextBubbleMessage.TYPE_PHOTO || m.mediaState != TextBubbleMessage.MEDIA_READY || m.photo == null && m.videoUri == null) {
                continue;
            }
            if (m == message) {
                index = items.size();
            }
            CharSequence sender = m.isOut ? "You" : "Alice Johnson";
            CharSequence date = m.date != 0 ? format.format(new Date(m.date * 1000)) : m.time;
            // Repeat the shared group caption on its pages when a member has no own caption.
            TextBubbleMessage owner = albumOwner(m);
            CharSequence caption = android.text.TextUtils.isEmpty(m.text) && owner != m ? owner.text : m.text;
            MediaViewer.Item item = m.videoUri != null
                    ? MediaViewer.Item.video(m.videoUri, m.photo, sender, date, caption)
                    : MediaViewer.Item.photo(m.photo, sender, date, caption);
            item.width = m.photoWidth;
            item.height = m.photoHeight;
            item.tag = m;
            items.add(item);
            sources.add(m);
        }
        if (items.isEmpty() || index < 0) {
            return;
        }
        MediaViewer.open(getParentActivity(), items, index, new MediaViewer.Provider() {
            @Override
            public boolean getSourceRect(int i, android.graphics.RectF outRect, float[] outRadii) {
                PhotoBubbleCell cell = findPhotoCell(sources.get(i));
                int tile = cell instanceof AlbumBubbleCell ? cell.getMessage().albumMedia.indexOf(sources.get(i)) : -1;
                boolean found = cell instanceof AlbumBubbleCell
                        ? ((AlbumBubbleCell) cell).getTileRectOnScreen(tile, outRect)
                        : cell != null && cell.getPhotoRectOnScreen(outRect);
                if (!found) {
                    return false;
                }
                // keep the part under the action bar / input out of the animation's start rect
                int[] location = new int[2];
                listView.getLocationOnScreen(location);
                if (outRect.bottom < location[1] + listView.getPaddingTop() || outRect.top > location[1] + listView.getHeight() - listView.getPaddingBottom()) {
                    return false;
                }
                System.arraycopy(tile >= 0 ? ((AlbumBubbleCell) cell).getTileRadii(tile) : cell.getPhotoRadii(), 0, outRadii, 0, 8);
                return true;
            }

            @Override
            public void setSourceVisible(int i, boolean visible) {
                viewerHiddenMessage = visible ? null : sources.get(i);
                PhotoBubbleCell cell = findPhotoCell(sources.get(i));
                if (cell != null) {
                    if (cell instanceof AlbumBubbleCell) {
                        ((AlbumBubbleCell) cell).setTileVisible(cell.getMessage().albumMedia.indexOf(sources.get(i)), visible);
                    } else cell.setPhotoVisible(visible);
                }
            }

            @Override
            public void onPageSelected(int i) {
                // ChatActivity scrolls the chat along so closing lands on the right bubble
                int position = rows.indexOf(albumOwner(sources.get(i)));
                if (position >= 0 && findPhotoCell(sources.get(i)) == null) {
                    layoutManager.scrollToPositionWithOffset(position, listView.getMeasuredHeight() / 3, false);
                }
            }

            @Override
            public void onClosed() {
                viewerHiddenMessage = null;
                for (int c = 0; c < listView.getChildCount(); c++) {
                    View child = listView.getChildAt(c);
                    if (child instanceof PhotoBubbleCell) {
                        ((PhotoBubbleCell) child).setPhotoVisible(true);
                        if (child instanceof AlbumBubbleCell) {
                            for (int tile = 0; tile < ((AlbumBubbleCell) child).getMessage().albumMedia.size(); tile++) {
                                ((AlbumBubbleCell) child).setTileVisible(tile, true);
                            }
                        }
                    }
                }
            }
        });
    }

    /** The photo MediaViewer shows full screen; its bubble leaves the image out (also after rebinding). */
    private TextBubbleMessage viewerHiddenMessage;

    private PhotoBubbleCell findPhotoCell(TextBubbleMessage message) {
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof PhotoBubbleCell && ((PhotoBubbleCell) child).getMessage() == albumOwner(message)) {
                return (PhotoBubbleCell) child;
            }
        }
        return null;
    }

    private TextBubbleMessage albumOwner(TextBubbleMessage item) {
        for (TextBubbleMessage message : messages) {
            if (message.type == TextBubbleMessage.TYPE_ALBUM && message.albumMedia.contains(item)) return message;
        }
        return item;
    }

    private TextBubbleMessage sampleAlbum(int count, boolean out, boolean caption) {
        ArrayList<TextBubbleMessage> items = new ArrayList<>();
        int[][] sizes = {{280, 440}, {480, 270}, {320, 320}, {240, 400}, {480, 320}};
        for (int i = 0; i < count; i++) {
            int[] size = sizes[i % sizes.length];
            TextBubbleMessage item = TextBubbleMessage.photo(nextId++, out, "10:14", out ? TextBubbleMessage.STATE_READ : TextBubbleMessage.STATE_NONE,
                    samplePhoto(size[0], size[1], i % 3), null);
            item.photoWidth = size[0];
            item.photoHeight = size[1];
            item.date = sampleDate(0, "10:14");
            items.add(item);
        }
        return TextBubbleMessage.album(nextId++, out, "10:14", out ? TextBubbleMessage.STATE_READ : TextBubbleMessage.STATE_NONE,
                items, caption ? ApplicationLoader.applicationContext.getString(org.telegram.uidemo.R.string.album_caption, count) : null);
    }

    /** A generated landscape (sky gradient, sun, two hills) standing in for a decoded photo. */
    static Drawable samplePhoto(int width, int height, int variant) {
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
                Toast.makeText(getParentActivity(), getParentActivity().getString(org.telegram.uidemo.R.string.document_open_action, message.fileName), Toast.LENGTH_SHORT).show();
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
            if (child instanceof TextBubbleCell && ((TextBubbleCell) child).getMessage() == albumOwner(message)) {
                if (child instanceof PhotoBubbleCell) {
                    ((PhotoBubbleCell) child).updateButtonState(true);
                } else if (child instanceof DocumentBubbleCell) {
                    ((DocumentBubbleCell) child).updateButtonState(true);
                } else if (child instanceof RoundVideoBubbleCell) {
                    ((RoundVideoBubbleCell) child).updatePlayingMessageProgress();
                } else if (child instanceof org.telegram.ui.Cells.MusicBubbleCell) {
                    ((org.telegram.ui.Cells.MusicBubbleCell) child).updatePlayingMessageProgress();
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

    private static List<List<TextBubbleMessage.InlineButton>> sampleInlineKeyboard(Context context, boolean loading) {
        return java.util.Arrays.asList(
                java.util.Arrays.asList(
                        new TextBubbleMessage.InlineButton("callback", context.getString(org.telegram.uidemo.R.string.inline_callback), TextBubbleMessage.InlineButton.Type.CALLBACK, loading),
                        new TextBubbleMessage.InlineButton("url", context.getString(org.telegram.uidemo.R.string.inline_url), TextBubbleMessage.InlineButton.Type.URL, false)),
                java.util.Arrays.asList(
                        new TextBubbleMessage.InlineButton("switch", context.getString(org.telegram.uidemo.R.string.inline_switch), TextBubbleMessage.InlineButton.Type.SWITCH_INLINE, false),
                        new TextBubbleMessage.InlineButton("copy", context.getString(org.telegram.uidemo.R.string.inline_copy), TextBubbleMessage.InlineButton.Type.COPY, false)));
    }

    private final TextBubbleCell.Delegate cellDelegate = new TextBubbleCell.Delegate() {
        @Override public void onInlineButtonPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.InlineButton button) {
            if (actionBar.isActionModeShowed() || getParentActivity() == null) return;
            Context context = getParentActivity();
            Toast.makeText(context, context.getString(org.telegram.uidemo.R.string.inline_action, button.id), Toast.LENGTH_SHORT).show();
            if (button.type == TextBubbleMessage.InlineButton.Type.CALLBACK) {
                final List<List<TextBubbleMessage.InlineButton>> pending = sampleInlineKeyboard(context, true);
                message.inlineKeyboard = pending;
                updateState(message, message.state);
                Runnable completion = new Runnable() {
                    @Override public void run() {
                        inlineCompletions.remove(this);
                        if (!messages.contains(message) || message.inlineKeyboard != pending) return;
                        message.inlineKeyboard = sampleInlineKeyboard(ApplicationLoader.applicationContext, false);
                        updateState(message, message.state);
                    }
                };
                inlineCompletions.add(completion);
                AndroidUtilities.runOnUIThread(completion, 1200);
            } else if (button.type == TextBubbleMessage.InlineButton.Type.SWITCH_INLINE) {
                enterView.setFieldText(context.getString(org.telegram.uidemo.R.string.inline_query));
            } else if (button.type == TextBubbleMessage.InlineButton.Type.COPY) {
                AndroidUtilities.addToClipboard(context.getString(org.telegram.uidemo.R.string.inline_copy_value));
            }
        }
        @Override public void onInlineButtonLongPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.InlineButton button) {
            if (getParentActivity() != null) Toast.makeText(getParentActivity(), getParentActivity().getString(org.telegram.uidemo.R.string.inline_long_action, button.id), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onForwardPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.ForwardedFrom source) {
            if (actionBar.isActionModeShowed()) {
                processRowSelect(message);
                return;
            }
            Toast.makeText(getParentActivity(), getParentActivity().getString(org.telegram.uidemo.R.string.forward_source_selected,
                    source.name, source.sourceId), Toast.LENGTH_SHORT).show();
        }

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
        @Override public void onPollVote(TextBubbleCell cell, TextBubbleMessage message, List<String> optionIds) {
            if (actionBar.isActionModeShowed() || message.poll == null || message.poll.pending) return;
            final TextBubbleMessage.Poll before = message.poll;
            final ArrayList<String> ids = new ArrayList<>(optionIds);
            final TextBubbleMessage.Poll pending = new TextBubbleMessage.Poll(before.question, before.options,
                    before.totalVoters, before.anonymous, before.multiple, before.closed, true, ids);
            message.poll = pending;
            updateState(message, message.state);
            Runnable completion = new Runnable() {
                @Override public void run() {
                    pollCompletions.remove(this);
                    if (!messages.contains(message) || message.poll != pending) return;
                    message.poll = localPollResult(before, ids);
                    updateState(message, message.state);
                }
            };
            pollCompletions.add(completion);
            AndroidUtilities.runOnUIThread(completion, 650);
        }
        @Override public void onPollOptionPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.PollOption option) {
            if (actionBar.isActionModeShowed() || getParentActivity() == null) return;
            Toast.makeText(getParentActivity(), getParentActivity().getString(org.telegram.uidemo.R.string.poll_result_line, option.text, option.voters), Toast.LENGTH_SHORT).show();
        }
        @Override public void onPollResults(TextBubbleCell cell, TextBubbleMessage message) {
            if (!actionBar.isActionModeShowed()) showPollResults(message);
        }
        @Override public void onLocationPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.Location location) {
            if (actionBar.isActionModeShowed()) { processRowSelect(message); return; }
            Toast.makeText(getParentActivity(), getParentActivity().getString(org.telegram.uidemo.R.string.location_action,
                    location.latitude, location.longitude), Toast.LENGTH_SHORT).show();
        }
        @Override public void onContactAction(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.Contact contact, int action) {
            if (actionBar.isActionModeShowed()) { processRowSelect(message); return; }
            int label = action == org.telegram.ui.Cells.ContactBubbleCell.ACTION_MESSAGE ? R.string.SharedContactMessage
                    : action == org.telegram.ui.Cells.ContactBubbleCell.ACTION_ADD ? R.string.SharedContactAdd : R.string.ViewContact;
            Toast.makeText(getParentActivity(), getParentActivity().getString(org.telegram.uidemo.R.string.contact_action,
                    getParentActivity().getString(label), contact.name, contact.id), Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onAlbumMediaPressed(AlbumBubbleCell cell, TextBubbleMessage album, TextBubbleMessage item, int index) {
            if (actionBar.isActionModeShowed()) processRowSelect(album);
            else onMediaPressed(cell, item);
        }
        @Override
        public void onMenuPressed(TextBubbleCell cell, TextBubbleMessage message, float x, float y) {
            if (!actionBar.isActionModeShowed()) {
                showMessageOptions(cell, message, x, y);
            }
        }

        @Override
        public void onMediaPressed(TextBubbleCell cell, TextBubbleMessage message) {
            if (message.type == TextBubbleMessage.TYPE_STICKER) {
                if (actionBar.isActionModeShowed()) processRowSelect(message);
                else if (stickerSamples != null) openStickerSet(stickerSamples.sets.get(0));
            } else if (message.type == TextBubbleMessage.TYPE_GIF) {
                if (actionBar.isActionModeShowed()) processRowSelect(message);
                else showMessageOptions(cell, message, cell.getWidth() / 2f, cell.getHeight() / 2f);
            } else if (message.type == TextBubbleMessage.TYPE_ROUND_VIDEO) {
                if (message.mediaState == TextBubbleMessage.MEDIA_READY) {
                    stopVoice();
                    if (musicPlayer != null) musicPlayer.stop();
                    roundPlayer.toggle(message);
                } else toggleFakeDownload(message);
            } else if (message.type == TextBubbleMessage.TYPE_MUSIC) {
                if (message.mediaState == TextBubbleMessage.MEDIA_READY) {
                    stopVoice();
                    if (roundPlayer != null) roundPlayer.stop();
                    musicPlayer.toggle(message, android.net.Uri.parse("android.resource://"
                            + getParentActivity().getPackageName() + "/" + org.telegram.uidemo.R.raw.sample_music));
                } else toggleFakeDownload(message);
            } else if (message.type == TextBubbleMessage.TYPE_VOICE) {
                if (roundPlayer != null) roundPlayer.stop();
                if (musicPlayer != null) musicPlayer.stop();
                toggleVoice(message);
            } else if (message.type == TextBubbleMessage.TYPE_PHOTO && message.mediaState == TextBubbleMessage.MEDIA_READY) {
                if (actionBar.isActionModeShowed()) {
                    processRowSelect(message);
                } else {
                    openMediaViewer(message);
                }
            } else if (message.type == TextBubbleMessage.TYPE_PHOTO || message.type == TextBubbleMessage.TYPE_DOCUMENT) {
                toggleFakeDownload(message);
            }
        }

        @Override
        public void onSeek(TextBubbleCell cell, TextBubbleMessage message, float progress) {
            if (message.type == TextBubbleMessage.TYPE_MUSIC && musicPlayer != null) {
                musicPlayer.seek(message, progress);
                return;
            }
            if (message.type == TextBubbleMessage.TYPE_ROUND_VIDEO && roundPlayer != null) {
                roundPlayer.seek(message, progress);
                return;
            }
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
        TextBubbleMessage oldVoice = playingVoice;
        if (playingVoice != null) {
            playingVoice.playing = false;
            playingVoice.audioProgress = 0;
            playingVoice.audioProgressSec = 0;
            playingVoice = null;
        }
        if (oldVoice != null) refreshVoice(oldVoice);
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

    private void refreshMusic() {
        if (listView == null) return;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof org.telegram.ui.Cells.MusicBubbleCell) {
                ((org.telegram.ui.Cells.MusicBubbleCell) child).updatePlayingMessageProgress();
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
            return holder.getItemViewType() != VIEW_TYPE_DATE && holder.getItemViewType() != VIEW_TYPE_UNREAD
                    && holder.getItemViewType() != VIEW_TYPE_SERVICE;
        }

        @Override
        public int getItemViewType(int position) {
            if (rows.get(position) == UNREAD_MARKER) return VIEW_TYPE_UNREAD;
            if (rows.get(position) instanceof ServiceMessage) return VIEW_TYPE_SERVICE;
            TextBubbleMessage message = messageAt(position);
            return message == null ? VIEW_TYPE_DATE : message.type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_UNREAD) {
                org.telegram.ui.Cells.ChatUnreadCell cell = new org.telegram.ui.Cells.ChatUnreadCell(context, getResourceProvider());
                cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerListView.Holder(cell);
            }
            if (viewType == VIEW_TYPE_DATE || viewType == VIEW_TYPE_SERVICE) {
                ChatActionCell dateCell = new ChatActionCell(context, getResourceProvider());
                dateCell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(dateCell);
            }
            TextBubbleCell cell;
            if (viewType == TextBubbleMessage.TYPE_VOICE) {
                cell = new VoiceBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_PHOTO) {
                cell = new PhotoBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_ALBUM) {
                cell = new AlbumBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_ROUND_VIDEO) {
                cell = new RoundVideoBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_STICKER) {
                cell = new StickerBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_GIF) {
                cell = new GifBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_DOCUMENT) {
                cell = new DocumentBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_MUSIC) {
                cell = new org.telegram.ui.Cells.MusicBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_POLL) {
                cell = new org.telegram.ui.Cells.PollBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_LOCATION) {
                cell = new org.telegram.ui.Cells.LocationBubbleCell(context, getResourceProvider());
            } else if (viewType == TextBubbleMessage.TYPE_CONTACT) {
                cell = new org.telegram.ui.Cells.ContactBubbleCell(context, getResourceProvider());
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
            if (holder.itemView instanceof org.telegram.ui.Cells.ChatUnreadCell) {
                org.telegram.ui.Cells.ChatUnreadCell cell = (org.telegram.ui.Cells.ChatUnreadCell) holder.itemView;
                cell.setText(context.getString(R.string.UnreadMessages));
                cell.updateColors();
                return;
            }
            if (holder.itemView instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) holder.itemView;
                Object row = rows.get(position);
                if (row instanceof ServiceMessage) {
                    cell.setDelegate(new ChatActionCell.Delegate() {
                        @Override public void onUserPressed(Object id) {
                            Toast.makeText(context, context.getString(org.telegram.uidemo.R.string.service_user_selected, id), Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onPinnedMessagePressed(Object id) {
                            if (id instanceof Long) scrollToMessage((Long) id);
                        }
                    });
                    cell.setMessage((ServiceMessage) row);
                } else {
                    cell.setDelegate(null);
                    cell.setCustomDate((Long) row);
                }
                return;
            }
            TextBubbleMessage message = messageAt(position);
            TextBubbleMessage prev = messageAt(position - 1);
            TextBubbleMessage next = messageAt(position + 1);
            boolean pinnedTop = MessageGrouping.canMerge(prev, message);
            boolean pinnedBottom = MessageGrouping.canMerge(message, next);
            TextBubbleCell cell = (TextBubbleCell) holder.itemView;
            cell.setAvatarDrawnByList(true);
            cell.setMessage(message, pinnedTop, pinnedBottom);
            cell.setCheckBoxVisible(actionBar.isActionModeShowed(), false);
            cell.setChecked(selectedMessages.contains(message), false);
            if (cell instanceof PhotoBubbleCell) {
                if (cell instanceof AlbumBubbleCell) {
                    ((PhotoBubbleCell) cell).setPhotoVisible(true);
                    for (int tile = 0; tile < message.albumMedia.size(); tile++) {
                        ((AlbumBubbleCell) cell).setTileVisible(tile, message.albumMedia.get(tile) != viewerHiddenMessage);
                    }
                } else ((PhotoBubbleCell) cell).setPhotoVisible(message != viewerHiddenMessage);
            }
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
