package org.telegram.ui.Cells;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import org.telegram.ui.Components.AvatarDrawable;

import java.util.ArrayList;

/**
 * tgui: plain model for {@link DialogCell}, replacing upstream TLRPC.Dialog + MessageObject + User/Chat.
 * Fill the fields, hand the item to {@link DialogCell#setDialog(DialogItem, boolean)}; call it again
 * with {@code animated = true} after changing fields to get the upstream counter / check / typing
 * animations.
 */
public class DialogItem {

    public static final int SENT_NONE = -1;
    public static final int SENT_SENDING = 0;
    public static final int SENT_SENT = 1;
    public static final int SENT_READ = 2;
    public static final int SENT_ERROR = 3;

    public static final int AVATAR_NORMAL = 0;
    /** Upstream saved-messages bookmark avatar (AvatarDrawable.AVATAR_TYPE_SAVED). */
    public static final int AVATAR_SAVED = 1;
    /** Upstream archive folder avatar (Lottie, AvatarDrawable.AVATAR_TYPE_ARCHIVED). */
    public static final int AVATAR_ARCHIVED = 2;
    /** Upstream replies avatar (AvatarDrawable.AVATAR_TYPE_REPLIES). */
    public static final int AVATAR_REPLIES = 12;

    /** Stable id: used for the avatar palette color and by the list adapter. */
    public long id;
    public CharSequence title;
    /** Last message text; Telegram emoji are applied by the cell. */
    public CharSequence message;
    /** Optional sender prefix ("Alice" → "Alice: text" in two-line mode, a separate line in three-line mode). */
    public CharSequence senderName;
    /** The preview is a media/service description ("Photo", "Sticker"…), drawn in the attach color. */
    public boolean mediaPreview;
    /** Non-null shows "Draft: …" in the draft color instead of the message. */
    public CharSequence draft;
    /** Non-null shows the typing line ("typing", "Alice is typing"…) with an animated status icon. */
    public CharSequence typing;
    /** Upstream printing types: 0 typing, 1 recording voice, 2 sending file, 3 playing game, 4 recording round video, 5 choosing sticker. */
    public int typingType;
    public String time;

    public int unreadCount;
    /** Upstream "mark as unread": a blank counter. */
    public boolean markUnread;
    /** Unread mention: the "@" badge. */
    public boolean mention;
    public boolean muted;
    public boolean pinned;
    public boolean verified;
    /** Secret chat: lock icon and green title. */
    public boolean secret;
    public boolean online;
    /** One of SENT_*; only outgoing last messages show checks. */
    public int sentState = SENT_NONE;

    /** Avatar image; null draws the upstream colored-initials avatar (or {@link #avatarType}). */
    public Drawable avatar;
    public Bitmap avatarBitmap;
    public int avatarType = AVATAR_NORMAL;
    /** Archive folder row: archived name/message colors, gray counters, no time. */
    public boolean isFolder;

    /** Search: second line of {@link ProfileSearchCell} ("online", "last seen…", "@username", "12 members"). */
    public CharSequence status;
    /** Search: {@link #status} is drawn in the online (blue) color. Defaults to {@link #online}. */
    public Boolean statusOnline;
    /** Search: words highlighted in the title and message (upstream MessageObject.highlightedWords). */
    public ArrayList<String> highlightedWords;

    /** Upstream AvatarDrawable.setInfo for this item: users reach it as first + last name, split the title the same way. */
    public void fillAvatarDrawable(AvatarDrawable avatarDrawable) {
        String name = title != null ? title.toString().trim() : "";
        int space = name.lastIndexOf(' ');
        if (space > 0) {
            avatarDrawable.setInfo(id, name.substring(0, space), name.substring(space + 1));
        } else {
            avatarDrawable.setInfo(id, name, null);
        }
        if (isFolder || avatarType == AVATAR_ARCHIVED) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ARCHIVED);
        } else if (avatarType != AVATAR_NORMAL) {
            avatarDrawable.setAvatarType(avatarType);
        }
    }

    public DialogItem() {
    }

    public DialogItem(long id, CharSequence title, CharSequence message, String time) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.time = time;
    }
}
