package org.telegram.ui.Cells;

import android.graphics.drawable.Drawable;

import java.util.ArrayList;

/**
 * tgui: plain model for {@link TextBubbleCell} and its media siblings {@link VoiceBubbleCell},
 * {@link PhotoBubbleCell} and {@link DocumentBubbleCell} (replaces upstream MessageObject).
 * {@link #type} picks the cell; for media messages {@link #text} is the optional caption.
 */
public class TextBubbleMessage {

    public static final int STATE_NONE = 0;
    public static final int STATE_SENDING = 1;
    public static final int STATE_SENT = 2;
    public static final int STATE_READ = 3;

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_VOICE = 1;
    public static final int TYPE_PHOTO = 2;
    public static final int TYPE_DOCUMENT = 3;

    /** Media is available locally: voice shows play / pause, files show their extension icon. */
    public static final int MEDIA_READY = 0;
    /** Media can be downloaded: download arrow. */
    public static final int MEDIA_DOWNLOAD = 1;
    /** Media is loading or uploading: cancel cross with {@link #mediaProgress}. */
    public static final int MEDIA_LOADING = 2;

    public long id;
    public int type = TYPE_TEXT;
    public CharSequence text;
    public boolean isOut;
    public String time;
    /** Unix time in seconds (0 = unknown); hosts use it for date separators ({@link ChatActionCell#formatDate}). */
    public long date;
    public int state;
    /**
     * Reactions drawn as pills inside the bubble (upstream messageOwner.reactions.results), in this
     * order; empty = none. Change it (or call {@link #toggleReaction}) and set the message again to
     * animate the pills.
     */
    public final ArrayList<Reaction> reactions = new ArrayList<>();

    /** Media download / upload state ({@code MEDIA_*}) and its 0..1 progress for MEDIA_LOADING. */
    public int mediaState = MEDIA_READY;
    public float mediaProgress;

    // --- voice (upstream TL_documentAttributeAudio + MessageObject.audioProgress / audioProgressSec)
    /** Duration in seconds. */
    public int duration;
    /** Upstream 5-bit packed waveform (as recorded by MediaController / ChatActivityEnterView), may be null. */
    public byte[] waveform;
    public boolean playing;
    public float audioProgress;
    public int audioProgressSec;
    /** Not listened yet: the small dot after the duration. */
    public boolean contentUnread;

    // --- photo
    public Drawable photo;
    /** Original photo size in pixels (aspect ratio for the bubble); defaults to the drawable's intrinsic size. */
    public int photoWidth;
    public int photoHeight;

    // --- document
    public String fileName;
    public long fileSize;

    /** upstream TLRPC.ReactionCount reduced to a static emoji, its count and whether it is mine. */
    public static class Reaction {
        public String emoji;
        public int count;
        /** Chosen by the current user: the pill is filled (chat_in/outReactionButtonBackground). */
        public boolean chosen;

        public Reaction(String emoji, int count, boolean chosen) {
            this.emoji = emoji;
            this.count = count;
            this.chosen = chosen;
        }
    }

    /** The reaction with {@code emoji}, or null. */
    public Reaction getReaction(String emoji) {
        for (int i = 0; i < reactions.size(); i++) {
            if (reactions.get(i).emoji.equals(emoji)) {
                return reactions.get(i);
            }
        }
        return null;
    }

    /**
     * Upstream SendMessagesHelper.sendReaction for a non-premium user (one reaction per user):
     * choosing {@code emoji} again removes it, choosing another one replaces the previous choice.
     * Counts that drop to zero remove the reaction; a new one is appended.
     * @return true if {@code emoji} is now chosen
     */
    public boolean toggleReaction(String emoji) {
        Reaction target = getReaction(emoji);
        boolean wasChosen = target != null && target.chosen;
        for (int i = reactions.size() - 1; i >= 0; i--) {
            Reaction r = reactions.get(i);
            if (r.chosen) {
                r.chosen = false;
                r.count--;
                if (r.count <= 0) {
                    reactions.remove(i);
                }
            }
        }
        if (wasChosen) {
            return false;
        }
        target = getReaction(emoji);
        if (target == null) {
            reactions.add(new Reaction(emoji, 1, true));
        } else {
            target.count++;
            target.chosen = true;
        }
        return true;
    }

    /** Quote of the message this one replies to, drawn at the top of the bubble (null = not a reply). */
    public ReplyTo replyTo;

    /** upstream replyMessageObject reduced to what the reply panel draws. */
    public static class ReplyTo {
        /** Id of the replied-to message, handed back by {@link TextBubbleCell.Delegate#onReplyPressed}. */
        public long messageId;
        public CharSequence name;
        /** One-line preview; Telegram emoji are replaced, new lines become spaces. */
        public CharSequence text;
        /** Optional thumbnail (photo replies), drawn centre-cropped with 4dp corners. */
        public Drawable thumb;
        /** The preview describes media ("Photo", "Voice message"): drawn in the reply media colour like upstream. */
        public boolean media;
        /** 0..6 picks a peer name colour (Theme.keys_avatar_nameInMessage) for incoming bubbles, -1 the theme reply colour. */
        public int colorId = -1;

        public ReplyTo(long messageId, CharSequence name, CharSequence text) {
            this.messageId = messageId;
            this.name = name;
            this.text = text;
        }

        /** Builds the quote for {@code message}: text, or caption / media description with the photo as thumbnail. */
        public static ReplyTo of(TextBubbleMessage message, CharSequence name) {
            CharSequence text = message.text;
            boolean media = false;
            if (message.type != TYPE_TEXT && (text == null || text.length() == 0)) {
                media = true;
                switch (message.type) {
                    case TYPE_VOICE:
                        text = "Voice message";
                        break;
                    case TYPE_PHOTO:
                        text = "Photo";
                        break;
                    default:
                        text = message.fileName != null ? message.fileName : "File";
                        break;
                }
            }
            ReplyTo reply = new ReplyTo(message.id, name, text);
            reply.media = media;
            if (message.type == TYPE_PHOTO) {
                reply.thumb = message.photo;
            }
            return reply;
        }
    }

    public TextBubbleMessage(long id, CharSequence text, boolean isOut, String time, int state) {
        this.id = id;
        this.text = text;
        this.isOut = isOut;
        this.time = time;
        this.state = isOut ? state : STATE_NONE;
    }

    public static TextBubbleMessage voice(long id, boolean isOut, String time, int state, int duration, byte[] waveform) {
        TextBubbleMessage message = new TextBubbleMessage(id, null, isOut, time, state);
        message.type = TYPE_VOICE;
        message.duration = duration;
        message.waveform = waveform;
        return message;
    }

    public static TextBubbleMessage photo(long id, boolean isOut, String time, int state, Drawable photo, CharSequence caption) {
        TextBubbleMessage message = new TextBubbleMessage(id, caption, isOut, time, state);
        message.type = TYPE_PHOTO;
        message.photo = photo;
        return message;
    }

    public static TextBubbleMessage document(long id, boolean isOut, String time, int state, String fileName, long fileSize) {
        TextBubbleMessage message = new TextBubbleMessage(id, null, isOut, time, state);
        message.type = TYPE_DOCUMENT;
        message.fileName = fileName;
        message.fileSize = fileSize;
        return message;
    }
}
