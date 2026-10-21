package org.telegram.uidemo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.DialogItem;
import org.telegram.ui.Components.DialogsSearchView;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo data for the chat-list search: filters the demo's own dialogs and a small message
 * history locally, with a short delay to show that results may arrive asynchronously.
 * A real host would query its database / server here.
 */
class DemoSearch {

    private final List<DialogItem> dialogs;
    private final ArrayList<DialogItem> globalChannels = new ArrayList<>();
    /** {dialog id, sender (null = none), text, time} */
    private static final Object[][] HISTORY = {
            {3L, null, "Are we still on for the café tomorrow?", "13:10"},
            {3L, null, "See you tomorrow at the café ☕️", "13:12"},
            {4L, null, "Can you send me the project files?", "12:51"},
            {4L, null, "Sure, I'll send the files tonight", "12:58"},
            {5L, "Kevin", "Is anyone using Compose with RecyclerView?", "12:40"},
            {5L, "Maria", "We moved our chat list to RecyclerView last year", "Yesterday"},
            {6L, "Dad", "Who is coming to dinner on Sunday?", "Mon"},
            {8L, null, "Let me check my calendar", "Yesterday"},
            {10L, null, "The upload failed, retrying…", "Mon"},
            {11L, null, "It's easier to ask forgiveness than it is to get permission.", "Sun"},
            {12L, null, "New Android release brings predictive back to every app 🚀", "Sat"},
            {12L, null, "Telegram for Android: a look at the new chat list design", "Thu"},
            {15L, null, "Thanks for the files! 🙏", "12.10.24"},
    };

    DemoSearch(List<DialogItem> dialogs) {
        this.dialogs = dialogs;
        for (DialogItem d : dialogs) {
            d.status = statusFor(d);
        }
        DialogItem g = new DialogItem(100, "Android Weekly", null, null);
        g.status = "@androidweekly, 48 210 subscribers";
        g.verified = true;
        globalChannels.add(g);
        g = new DialogItem(101, "Telegram Tips", null, null);
        g.status = "@tgtips, 1 240 518 subscribers";
        g.verified = true;
        globalChannels.add(g);
        g = new DialogItem(102, "Café Lovers", null, null);
        g.status = "@cafelovers, 3 912 members";
        globalChannels.add(g);
    }

    private static CharSequence statusFor(DialogItem d) {
        if (d.isFolder || d.avatarType == DialogItem.AVATAR_SAVED) {
            return null;
        }
        if (d.senderName != null && !"You".contentEquals(d.senderName)) {
            return d.id == 6 ? "5 members" : "1 284 members";
        }
        if (d.verified) {
            return "service notifications";
        }
        if (d.muted) {
            return "12 408 subscribers";
        }
        return d.online ? "online" : "last seen recently";
    }

    /** Upstream MediaDataController.hints: the people row. */
    ArrayList<DialogItem> hints() {
        ArrayList<DialogItem> list = new ArrayList<>();
        for (DialogItem d : dialogs) {
            if (!d.isFolder && d.senderName == null && d.avatarType == DialogItem.AVATAR_NORMAL && !d.verified && !d.muted) {
                list.add(d);
            }
            if (list.size() == 8) {
                break;
            }
        }
        return list;
    }

    ArrayList<DialogItem> recent() {
        ArrayList<DialogItem> list = new ArrayList<>();
        long[] ids = {5, 1, 12, 11};
        for (long id : ids) {
            DialogItem d = find(id);
            if (d != null) {
                list.add(d);
            }
        }
        return list;
    }

    DialogItem find(long id) {
        for (DialogItem d : dialogs) {
            if (d.id == id) {
                return d;
            }
        }
        return null;
    }

    void search(String query, DialogsSearchView.ResultsCallback callback) {
        String q = query.toLowerCase();
        DialogsSearchView.Results results = new DialogsSearchView.Results();
        for (DialogItem d : dialogs) {
            if (!d.isFolder && d.title != null && d.title.toString().toLowerCase().contains(q)) {
                results.chats.add(d);
            }
        }
        for (DialogItem g : globalChannels) {
            if (g.title.toString().toLowerCase().contains(q) || g.status.toString().toLowerCase().contains(q)) {
                results.globalChats.add(g);
            }
        }
        for (int i = HISTORY.length - 1; i >= 0; i--) {
            Object[] m = HISTORY[i];
            String text = (String) m[2];
            if (!text.toLowerCase().contains(q)) {
                continue;
            }
            DialogItem chat = find((Long) m[0]);
            if (chat == null) {
                continue;
            }
            // upstream message results: a DialogCell per message, chat title + "sender: text"
            // same id as the chat: same colored-initials avatar
            DialogItem msg = new DialogItem(chat.id, chat.title, text, (String) m[3]);
            msg.senderName = (String) m[1];
            msg.avatar = chat.avatar;
            msg.avatarBitmap = chat.avatarBitmap;
            msg.avatarType = chat.avatarType;
            msg.verified = chat.verified;
            msg.secret = chat.secret;
            results.messages.add(msg);
        }
        // pretend this came from a database query
        AndroidUtilities.runOnUIThread(() -> callback.onResults(results), 120);
    }
}
