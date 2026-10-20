package org.telegram.ui.Cells;

/**
 * tgui: plain model for {@link TextBubbleCell} (replaces upstream MessageObject for text-only bubbles).
 */
public class TextBubbleMessage {

    public static final int STATE_NONE = 0;
    public static final int STATE_SENDING = 1;
    public static final int STATE_SENT = 2;
    public static final int STATE_READ = 3;

    public long id;
    public CharSequence text;
    public boolean isOut;
    public String time;
    public int state;

    public TextBubbleMessage(long id, CharSequence text, boolean isOut, String time, int state) {
        this.id = id;
        this.text = text;
        this.isOut = isOut;
        this.time = time;
        this.state = isOut ? state : STATE_NONE;
    }
}
