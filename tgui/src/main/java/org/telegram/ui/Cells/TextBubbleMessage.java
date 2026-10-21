package org.telegram.ui.Cells;

import android.graphics.drawable.Drawable;

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
    public int state;
    /** Emoji of the reaction chosen by the user, shown as a chip under the text (null = none). */
    public String reaction;

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
