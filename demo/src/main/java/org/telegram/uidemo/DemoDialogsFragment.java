package org.telegram.uidemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.DialogItem;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.TelegramUI;

import java.util.ArrayList;

/**
 * Chat list demo: upstream DialogCell rows bound to plain {@link DialogItem}s.
 */
public class DemoDialogsFragment extends BaseFragment {

    private static final int MENU_LINES = 1;
    private static final int MENU_SIMULATE = 2;
    private static final int MENU_NIGHT = 3;

    private RecyclerListView listView;
    private Adapter adapter;
    private final ArrayList<DialogItem> dialogs = new ArrayList<>();
    private int simulateStep;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Telegram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_LINES) {
                    SharedConfig.useThreeLinesLayout = !SharedConfig.useThreeLinesLayout;
                    adapter.notifyDataSetChanged();
                } else if (id == MENU_SIMULATE) {
                    simulateUpdate();
                } else if (id == MENU_NIGHT) {
                    TelegramUI.toggleDayNight(actionBar);
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem other = menu.addItem(0, R.drawable.ic_ab_other);
        other.addSubItem(MENU_SIMULATE, R.drawable.msg_msgbubble2, "Simulate update");
        other.addSubItem(MENU_LINES, R.drawable.msg_list, "Two / three lines");
        other.addSubItem(MENU_NIGHT, R.drawable.msg_theme, "Day / night");

        fillDialogs();

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        listView = new RecyclerListView(context);
        NotificationCenter.getGlobalInstance().listenGlobal(listView, NotificationCenter.emojiLoaded, args -> Emoji.invalidateAll(listView));
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(true);
        listView.setAdapter(adapter = new Adapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof DialogCell && ((DialogCell) view).getDialog().isFolder) {
                ((DialogCell) view).animateArchiveAvatar();
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void simulateUpdate() {
        // cycles through the upstream animated transitions: counter, typing, check status
        DialogItem d = dialogs.get(3);
        switch (simulateStep++ % 4) {
            case 0:
                d.unreadCount++;
                d.message = "One more message " + d.unreadCount;
                d.time = "now";
                break;
            case 1:
                d.typing = "typing";
                d.typingType = 0;
                break;
            case 2:
                d.typing = null;
                d.message = "Done typing 👋";
                d.unreadCount++;
                break;
            case 3:
                d.unreadCount = 0;
                break;
        }
        DialogItem out = dialogs.get(4);
        out.sentState = out.sentState == DialogItem.SENT_READ ? DialogItem.SENT_SENDING : out.sentState + 1;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof DialogCell) {
                DialogCell cell = (DialogCell) child;
                if (cell.getDialog() == d || cell.getDialog() == out) {
                    cell.setDialog(cell.getDialog(), true);
                }
            }
        }
    }

    private void fillDialogs() {
        dialogs.clear();
        DialogItem d;

        d = new DialogItem(0, "Archived Chats", "Design Team, Weekend Plans, Old Project", null);
        d.isFolder = true;
        d.senderName = null;
        d.unreadCount = 4;
        dialogs.add(d);

        d = new DialogItem(1, "Saved Messages", "photo_2024.jpg", "14:02");
        d.avatarType = DialogItem.AVATAR_SAVED;
        d.pinned = true;
        d.sentState = DialogItem.SENT_READ;
        dialogs.add(d);

        d = new DialogItem(2, "Telegram", "Your login code: 52713. Do not give this code to anyone.", "13:47");
        d.verified = true;
        d.pinned = true;
        d.unreadCount = 1;
        d.avatar = gradientAvatar(0xFF2AABEE, 0xFF229ED9, "✈");
        dialogs.add(d);

        d = new DialogItem(3, "Alice Johnson", "See you tomorrow at the café ☕️", "13:12");
        d.online = true;
        d.unreadCount = 2;
        d.avatarBitmap = gradientBitmap(0xFFFF8A65, 0xFFE91E63);
        dialogs.add(d);

        d = new DialogItem(4, "Bob Smith", "Sure, I'll send the files tonight", "12:58");
        d.sentState = DialogItem.SENT_SENT;
        dialogs.add(d);

        d = new DialogItem(5, "Android Developers", "Is anyone using Compose with RecyclerView?", "12:40");
        d.senderName = "Kevin";
        d.unreadCount = 128;
        d.mention = true;
        dialogs.add(d);

        d = new DialogItem(6, "Family 🏡", "Photo", "11:05");
        d.senderName = "Mom";
        d.mediaPreview = true;
        d.muted = true;
        d.unreadCount = 17;
        dialogs.add(d);

        d = new DialogItem(7, "Charlie", "typing", "10:31");
        d.message = "Did you see the game last night?";
        d.typing = "typing";
        d.typingType = 0;
        d.online = true;
        dialogs.add(d);

        d = new DialogItem(8, "Diana Prince", "Let me check my calendar", "Yesterday");
        d.draft = "I think Friday works better for";
        dialogs.add(d);

        d = new DialogItem(9, "Evan", "Voice message", "Yesterday");
        d.mediaPreview = true;
        d.sentState = DialogItem.SENT_SENDING;
        dialogs.add(d);

        d = new DialogItem(10, "Frank Ocean", "The upload failed, retrying…", "Mon");
        d.sentState = DialogItem.SENT_ERROR;
        dialogs.add(d);

        d = new DialogItem(11, "Grace Hopper", "It's easier to ask forgiveness than it is to get permission.", "Sun");
        d.secret = true;
        dialogs.add(d);

        d = new DialogItem(12, "Tech News", "New Android release brings predictive back to every app 🚀", "Sat");
        d.muted = true;
        d.markUnread = true;
        dialogs.add(d);

        d = new DialogItem(13, "Hannah", "😂😂😂", "Fri");
        d.sentState = DialogItem.SENT_READ;
        dialogs.add(d);

        d = new DialogItem(14, "Ivan Petrov", "Sticker", "Thu");
        d.mediaPreview = true;
        d.senderName = "You";
        dialogs.add(d);

        d = new DialogItem(15, "Julia", "Thanks! 🙏", "12.10.24");
        dialogs.add(d);

        d = new DialogItem(16, "Kate", "recording voice", "11.10.24");
        d.message = "ok";
        d.typing = "recording voice";
        d.typingType = 1;
        dialogs.add(d);
    }

    private static Bitmap gradientBitmap(int c1, int c2) {
        int size = AndroidUtilities.dp(54);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, size, size, c1, c2, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, size, size, paint);
        paint.setShader(null);
        paint.setColor(0x55FFFFFF);
        canvas.drawCircle(size * 0.5f, size * 0.42f, size * 0.18f, paint);
        canvas.drawCircle(size * 0.5f, size * 1.05f, size * 0.38f, paint);
        return bitmap;
    }

    private static Drawable gradientAvatar(int c1, int c2, String symbol) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas canvas) {
                android.graphics.Rect b = getBounds();
                paint.setShader(new LinearGradient(b.left, b.top, b.left, b.bottom, c1, c2, Shader.TileMode.CLAMP));
                canvas.drawRect(b, paint);
                textPaint.setColor(0xFFFFFFFF);
                textPaint.setTextSize(b.height() * 0.45f);
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(symbol, b.centerX(), b.centerY() + b.height() * 0.16f, textPaint);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
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

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            DialogCell cell = new DialogCell(context);
            cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            DialogCell cell = (DialogCell) holder.itemView;
            DialogItem item = dialogs.get(position);
            DialogItem next = position + 1 < dialogs.size() ? dialogs.get(position + 1) : null;
            // upstream DialogsAdapter: full-width divider under the last pinned row
            cell.useSeparator = position != getItemCount() - 1;
            cell.fullSeparator = (item.pinned || item.isFolder) && next != null && !next.pinned;
            cell.setDialog(item, false);
        }

        @Override
        public int getItemCount() {
            return dialogs.size();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        // upstream DialogsActivity.getThemeDescriptions (DialogCell part)
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        RecyclerListView list = listView;
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_countPaint, null, null, Theme.key_chats_unreadCounter));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_countGrayPaint, null, null, Theme.key_chats_unreadCounterMuted));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_countTextPaint, null, null, Theme.key_chats_unreadCounterText));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_lockDrawable}, null, Theme.key_chats_secretIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_pinnedDrawable, Theme.dialogs_reorderDrawable}, null, Theme.key_chats_pinnedIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Paint[]{Theme.dialogs_namePaint[0], Theme.dialogs_namePaint[1], Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Paint[]{Theme.dialogs_nameEncryptedPaint[0], Theme.dialogs_nameEncryptedPaint[1], Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_messagePaint[1], null, null, Theme.key_chats_message_threeLines));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_messagePaint[0], null, null, Theme.key_chats_message));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_messageNamePaint, null, null, Theme.key_chats_nameMessage_threeLines));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_draft));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, Theme.dialogs_messagePrintingPaint, null, null, Theme.key_chats_actionMessage));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_timePaint, null, null, Theme.key_chats_date));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_pinnedPaint, null, null, Theme.key_chats_pinnedOverlay));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_tabletSeletedPaint, null, null, Theme.key_chats_tabletSelectedOverlay));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_checkDrawable}, null, Theme.key_chats_sentCheck));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_checkReadDrawable, Theme.dialogs_halfCheckDrawable}, null, Theme.key_chats_sentReadCheck));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_clockDrawable}, null, Theme.key_chats_sentClock));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, Theme.dialogs_errorPaint, null, null, Theme.key_chats_sentError));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_errorDrawable}, null, Theme.key_chats_sentErrorIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_verifiedCheckDrawable}, null, Theme.key_chats_verifiedCheck));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_verifiedDrawable}, null, Theme.key_chats_verifiedBackground));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_muteDrawable}, null, Theme.key_chats_muteIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_mentionDrawable}, null, Theme.key_chats_mentionIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_archivePinBackground));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_archiveBackground));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_onlineCircle));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_CHECKBOX, new Class[]{DialogCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(list, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{DialogCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow1", Theme.key_avatar_backgroundArchived));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow2", Theme.key_avatar_backgroundArchived));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Box2", Theme.key_avatar_text));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Box1", Theme.key_avatar_text));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_pinArchiveDrawable}, "Arrow", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_pinArchiveDrawable}, "Line", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unpinArchiveDrawable}, "Arrow", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unpinArchiveDrawable}, "Line", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Arrow", Theme.key_chats_archiveBackground));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Box2", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Box1", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Arrow1", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Arrow2", Theme.key_chats_archivePinBackground));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Box2", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(list, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Box1", Theme.key_chats_archiveIcon));
        return arrayList;
    }
}
