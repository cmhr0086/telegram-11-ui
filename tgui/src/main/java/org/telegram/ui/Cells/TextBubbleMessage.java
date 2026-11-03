/*
 * Adapted from Telegram for Android release-11.4.2-5469.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Cells;

import android.graphics.drawable.Drawable;

import java.util.ArrayList;

/**
 * tgui: plain model for {@link TextBubbleCell} and its media siblings {@link VoiceBubbleCell},
 * {@link PhotoBubbleCell} and {@link DocumentBubbleCell} (replaces upstream MessageObject).
 * {@link #type} picks the cell; for media messages {@link #text} is the optional caption.
 */
public class TextBubbleMessage {

    /** Host-provided group identity; no account or network lookup is performed. */
    public static final class Sender {
        public final long id;
        public final CharSequence name;
        /** Static host-owned drawable. To update its content, the host invalidates the cell or rebinds the message. */
        public final Drawable avatar;
        public final int colorId;
        public Sender(long id, CharSequence name, Drawable avatar) { this(id, name, avatar, -1); }
        public Sender(long id, CharSequence name, Drawable avatar, int colorId) {
            this.id = id; this.name = name; this.avatar = avatar; this.colorId = colorId;
        }
    }
    /** Explicit group context; absent on existing direct-chat models. */
    public boolean group;
    public Sender sender;
    /** Host-provided forward origin; sourceId 0 represents an anonymous origin. */
    public static final class ForwardedFrom {
        public final long sourceId;
        public final CharSequence name;
        public final Drawable avatar;
        public final int colorId;
        public ForwardedFrom(long sourceId, CharSequence name) { this(sourceId, name, null, -1); }
        public ForwardedFrom(long sourceId, CharSequence name, Drawable avatar, int colorId) {
            this.sourceId = sourceId; this.name = name; this.avatar = avatar; this.colorId = colorId;
        }
    }
    public ForwardedFrom forwardedFrom;

    public static final int STATE_NONE = 0;
    public static final int STATE_SENDING = 1;
    public static final int STATE_SENT = 2;
    public static final int STATE_READ = 3;

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_VOICE = 1;
    public static final int TYPE_PHOTO = 2;
    public static final int TYPE_DOCUMENT = 3;
    public static final int TYPE_ALBUM = 4;
    public static final int TYPE_ROUND_VIDEO = 5;
    public static final int TYPE_STICKER = 6;
    public static final int TYPE_GIF = 7;
    public static final int TYPE_CONTACT = 8;
    public static final int TYPE_MUSIC = 9;
    public static final int TYPE_LOCATION = 10;

    /** Host-owned static map and coordinates. The map should not contain a marker. */
    public static final class Location {
        public final double latitude, longitude;
        public final CharSequence title, address;
        public final Drawable mapImage;
        public Location(double latitude, double longitude, CharSequence title, CharSequence address, Drawable mapImage) {
            this.latitude = latitude; this.longitude = longitude;
            this.title = title; this.address = address; this.mapImage = mapImage;
        }
    }
    public Location location;
    public static TextBubbleMessage location(long id, boolean isOut, String time, int state, Location location) {
        if (location == null) throw new IllegalArgumentException("location required");
        TextBubbleMessage result = new TextBubbleMessage(id, null, isOut, time, state);
        result.type = TYPE_LOCATION; result.location = location; return result;
    }
    public CharSequence musicTitle, musicPerformer;
    public static TextBubbleMessage music(long id, boolean isOut, String time, int state,
                                           CharSequence title, CharSequence performer, int duration, CharSequence caption) {
        TextBubbleMessage result = new TextBubbleMessage(id, caption, isOut, time, state);
        result.type = TYPE_MUSIC; result.musicTitle = title; result.musicPerformer = performer;
        result.duration = Math.max(0, duration); return result;
    }

    /** Host-owned contact snapshot; capability flags select the available actions. */
    public static final class Contact {
        public final long id;
        public final CharSequence name, phone;
        public final Drawable avatar;
        public final boolean canMessage, canAdd;
        public final int colorId;
        public Contact(long id, CharSequence name, CharSequence phone, Drawable avatar) {
            this(id, name, phone, avatar, false, false);
        }
        public Contact(long id, CharSequence name, CharSequence phone, Drawable avatar, boolean canMessage, boolean canAdd) {
            this(id, name, phone, avatar, canMessage, canAdd, (int) Math.abs(id % 7));
        }
        public Contact(long id, CharSequence name, CharSequence phone, Drawable avatar, boolean canMessage, boolean canAdd, int colorId) {
            this.id = id; this.name = name; this.phone = phone; this.avatar = avatar;
            this.canMessage = canMessage; this.canAdd = canAdd; this.colorId = colorId;
        }
    }
    public Contact contact;
    public static TextBubbleMessage contact(long id, boolean isOut, String time, int state, Contact contact) {
        if (contact == null) throw new IllegalArgumentException("contact required");
        TextBubbleMessage result = new TextBubbleMessage(id, null, isOut, time, state);
        result.type = TYPE_CONTACT; result.contact = contact; return result;
    }

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
    /** A paused music session remains selected and keeps its seek bar visible. */
    public boolean audioPaused;
    /** Round video has a prepared but paused session; keeps remaining time/progress visible. */
    public boolean videoPaused;
    public float audioProgress;
    public int audioProgressSec;
    /** Not listened yet: the small dot after the duration. */
    public boolean contentUnread;

    // --- photo
    public Drawable photo;
    /** Original photo size in pixels (aspect ratio for the bubble); defaults to the drawable's intrinsic size. */
    public int photoWidth;
    public int photoHeight;
    /**
     * Set for a video: the bubble draws {@link #photo} as its thumbnail with a play button and the
     * {@link #duration} pill, and MediaViewer plays this uri.
     */
    public android.net.Uri videoUri;

    /** Host-owned local media; no Telegram document or network state is required. */
    public org.telegram.ui.Components.Sticker sticker;
    public org.telegram.ui.Components.Gif gif;

    /** Ordered local photo/video items, 2..10, for TYPE_ALBUM; the group's text is its caption. */
    public final ArrayList<TextBubbleMessage> albumMedia = new ArrayList<>();

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
                        text = message.videoUri != null ? "Video" : "Photo";
                        break;
                    case TYPE_ALBUM:
                        text = org.telegram.messenger.ApplicationLoader.applicationContext.getString(org.telegram.tgui.R.string.tgui_album);
                        break;
                    case TYPE_ROUND_VIDEO:
                        text = org.telegram.messenger.ApplicationLoader.applicationContext.getString(org.telegram.tgui.R.string.tgui_round_video);
                        break;
                    case TYPE_STICKER:
                        text = org.telegram.messenger.ApplicationLoader.applicationContext.getString(org.telegram.tgui.R.string.tgui_sticker);
                        break;
                    case TYPE_GIF:
                        text = org.telegram.messenger.ApplicationLoader.applicationContext.getString(org.telegram.tgui.R.string.tgui_gif);
                        break;
                    case TYPE_CONTACT:
                        if (message.contact != null && !android.text.TextUtils.isEmpty(message.contact.name)) {
                            text = message.contact.name;
                        } else if (message.contact != null && !android.text.TextUtils.isEmpty(message.contact.phone)) {
                            text = message.contact.phone;
                        } else {
                            text = org.telegram.messenger.ApplicationLoader.applicationContext.getString(org.telegram.tgui.R.string.AttachContact);
                        }
                        break;
                    case TYPE_LOCATION:
                        text = message.location != null && !android.text.TextUtils.isEmpty(message.location.title)
                                ? message.location.title : org.telegram.messenger.ApplicationLoader.applicationContext.getString(org.telegram.tgui.R.string.ChatLocation);
                        break;
                    case TYPE_MUSIC:
                        text = !android.text.TextUtils.isEmpty(message.musicTitle) ? message.musicTitle : org.telegram.messenger.ApplicationLoader.applicationContext.getString(org.telegram.tgui.R.string.AttachMusic);
                        break;
                    default:
                        text = message.fileName != null ? message.fileName : "File";
                        break;
                }
            }
            ReplyTo reply = new ReplyTo(message.id, name, text);
            reply.media = media;
            if (message.type == TYPE_LOCATION && message.location != null) {
                reply.thumb = message.location.mapImage;
            } else if (message.type == TYPE_PHOTO) {
                reply.thumb = message.photo;
            } else if (message.type == TYPE_ROUND_VIDEO) {
                reply.thumb = message.photo;
            } else if (message.type == TYPE_ALBUM && !message.albumMedia.isEmpty()) {
                reply.thumb = message.albumMedia.get(0).photo;
            } else if (message.type == TYPE_STICKER && message.sticker != null) {
                reply.thumb = message.sticker.thumbnail;
            } else if (message.type == TYPE_GIF && message.gif != null) {
                reply.thumb = message.gif.thumbnail;
            } else if (message.type == TYPE_CONTACT && message.contact != null) {
                reply.thumb = message.contact.avatar;
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

    /** A video message: {@code thumb} is the first frame (or any preview), {@code duration} in seconds. */
    public static TextBubbleMessage video(long id, boolean isOut, String time, int state, Drawable thumb, android.net.Uri videoUri, int duration, CharSequence caption) {
        TextBubbleMessage message = photo(id, isOut, time, state, thumb, caption);
        message.videoUri = videoUri;
        message.duration = duration;
        return message;
    }

    public static TextBubbleMessage document(long id, boolean isOut, String time, int state, String fileName, long fileSize) {
        TextBubbleMessage message = new TextBubbleMessage(id, null, isOut, time, state);
        message.type = TYPE_DOCUMENT;
        message.fileName = fileName;
        message.fileSize = fileSize;
        return message;
    }

    /** One group row: member ids remain available to the host's media click callback. */
    public static TextBubbleMessage album(long id, boolean isOut, String time, int state,
                                          java.util.List<TextBubbleMessage> items, CharSequence caption) {
        if (items == null || items.size() < 2 || items.size() > 10) {
            throw new IllegalArgumentException("An album requires 2..10 photo/video items");
        }
        java.util.Set<TextBubbleMessage> unique = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (TextBubbleMessage item : items) {
            if (item == null || item.type != TYPE_PHOTO) throw new IllegalArgumentException("Album members must be photo/video items");
            if (!unique.add(item)) throw new IllegalArgumentException("Album members must be distinct message objects");
        }
        TextBubbleMessage message = new TextBubbleMessage(id, caption, isOut, time, state);
        message.type = TYPE_ALBUM;
        message.albumMedia.addAll(items);
        return message;
    }

    /** Local circular video; the host owns decoding, audio focus, playback and progress updates. */
    public static TextBubbleMessage roundVideo(long id, boolean isOut, String time, int state,
                                               Drawable thumb, android.net.Uri uri, int duration) {
        TextBubbleMessage message = new TextBubbleMessage(id, null, isOut, time, state);
        message.type = TYPE_ROUND_VIDEO;
        message.photo = thumb;
        message.videoUri = uri;
        message.duration = Math.max(0, duration);
        return message;
    }

    public static TextBubbleMessage sticker(long id, boolean isOut, String time, int state,
                                            org.telegram.ui.Components.Sticker sticker) {
        if (sticker == null) throw new IllegalArgumentException("sticker is required");
        TextBubbleMessage message = new TextBubbleMessage(id, null, isOut, time, state);
        message.type = TYPE_STICKER;
        message.sticker = sticker;
        message.photo = sticker.thumbnail;
        message.photoWidth = sticker.width;
        message.photoHeight = sticker.height;
        return message;
    }

    public static TextBubbleMessage gif(long id, boolean isOut, String time, int state,
                                        org.telegram.ui.Components.Gif gif, CharSequence caption) {
        if (gif == null) throw new IllegalArgumentException("gif is required");
        TextBubbleMessage message = new TextBubbleMessage(id, caption, isOut, time, state);
        message.type = TYPE_GIF;
        message.gif = gif;
        message.photo = gif.thumbnail;
        message.photoWidth = gif.width;
        message.photoHeight = gif.height;
        return message;
    }
}
