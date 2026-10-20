package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;

/**
 * tgui: the reply / edit / forward / link panel above the message input. Upstream builds it inside
 * ChatActivity (replyLayout + ChatActivityEnterTopView, shown with showFieldPanel); this class
 * keeps that layout, icons and colours and exposes it as a small API for hosts.
 *
 * <pre>
 * ChatInputTopPanel panel = new ChatInputTopPanel(context, enterView, resourcesProvider);
 * panel.showReply("Alice", "See you tomorrow");   // or showEdit / showForward / showLink
 * panel.hide();
 * </pre>
 */
public class ChatInputTopPanel {

    public static final int MODE_NONE = 0;
    public static final int MODE_EDIT = 1;
    public static final int MODE_REPLY = 2;
    public static final int MODE_FORWARD = 3;
    public static final int MODE_LINK = 4;

    public interface Listener {
        /** The close button was pressed; the panel hides itself afterwards. */
        default void onClose(int mode) {
        }

        /** The panel body was tapped (upstream scrolls to the message / opens reply options). */
        default void onClick(int mode) {
        }
    }

    private final ChatActivityEnterView enterView;
    private final Theme.ResourcesProvider resourcesProvider;
    private final ChatActivityEnterTopView topView;
    private final View replyLineView;
    private final ImageView replyIconImageView;
    private final ImageView replyCloseImageView;
    private final SimpleTextView replyNameTextView;
    private final SimpleTextView replyObjectTextView;
    private Listener listener;
    private int mode = MODE_NONE;

    public ChatInputTopPanel(Context context, ChatActivityEnterView enterView, Theme.ResourcesProvider resourcesProvider) {
        this.enterView = enterView;
        this.resourcesProvider = resourcesProvider;

        topView = new ChatActivityEnterTopView(context) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                ChatInputTopPanel.this.enterView.invalidate();
            }

            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        replyLineView = new View(context);
        replyLineView.setBackgroundColor(getThemedColor(Theme.key_chat_replyPanelLine));
        enterView.addTopView(topView, replyLineView, 48);

        final FrameLayout replyLayout = new FrameLayout(context);
        topView.addReplyView(replyLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.NO_GRAVITY, 0, 0, 52, 0));
        replyLayout.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(mode);
            }
        });

        replyIconImageView = new ImageView(context);
        replyIconImageView.setScaleType(ImageView.ScaleType.CENTER);
        replyLayout.addView(replyIconImageView, LayoutHelper.createFrame(52, 46, Gravity.TOP | Gravity.LEFT));

        replyCloseImageView = new ImageView(context);
        replyCloseImageView.setImageResource(R.drawable.input_clear);
        replyCloseImageView.setScaleType(ImageView.ScaleType.CENTER);
        topView.addView(replyCloseImageView, LayoutHelper.createFrame(52, 46, Gravity.RIGHT | Gravity.TOP, 0, 0.5f, 0, 0));
        replyCloseImageView.setOnClickListener(v -> {
            int closedMode = mode;
            if (listener != null) {
                listener.onClose(closedMode);
            }
            if (closedMode == MODE_EDIT) {
                enterView.setEditingMessageObject(null, false);
            } else if (closedMode == MODE_REPLY) {
                enterView.setReplyingMessageObject(null);
            }
            hide(true);
        });

        replyNameTextView = new SimpleTextView(context);
        replyNameTextView.setTextSize(14);
        replyNameTextView.setTypeface(AndroidUtilities.bold());
        replyLayout.addView(replyNameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 52, 6, 0, 0));

        replyObjectTextView = new SimpleTextView(context);
        replyObjectTextView.setTextSize(14);
        replyLayout.addView(replyObjectTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 52, 24, 0, 0));

        updateColors();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getMode() {
        return mode;
    }

    public ChatActivityEnterTopView getView() {
        return topView;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    /** "Reply to {name}" with the quoted message text; {@code quote} switches to the quote icon. */
    public void showReply(CharSequence name, CharSequence text, boolean quote) {
        mode = MODE_REPLY;
        replyIconImageView.setImageResource(quote ? R.drawable.filled_reply_quote : R.drawable.ic_ab_reply);
        CharSequence nameText = AndroidUtilities.replaceCharSequence("%s", LocaleController.getString(quote ? R.string.ReplyToQuote : R.string.ReplyTo), name == null ? "" : name);
        setTexts(nameText, text);
        replyIconImageView.setContentDescription(LocaleController.getString(R.string.AccDescrReplying));
        replyCloseImageView.setContentDescription(LocaleController.getString(R.string.AccDescrCancelReply));
        show();
    }

    public void showReply(CharSequence name, CharSequence text) {
        showReply(name, text, false);
    }

    /** "Edit Message" (or "Edit Caption") with the original text. */
    public void showEdit(CharSequence text, boolean caption) {
        mode = MODE_EDIT;
        replyIconImageView.setImageResource(R.drawable.group_edit);
        setTexts(LocaleController.getString(caption ? R.string.EditCaption : R.string.EditMessage), text);
        replyIconImageView.setContentDescription(LocaleController.getString(R.string.AccDescrEditing));
        replyCloseImageView.setContentDescription(LocaleController.getString(R.string.AccDescrCancelEdit));
        show();
    }

    /** Forwarded messages: title such as "Forward 3 messages" and the senders line. */
    public void showForward(CharSequence title, CharSequence text) {
        mode = MODE_FORWARD;
        replyIconImageView.setImageResource(R.drawable.filled_forward);
        setTexts(title, text);
        replyIconImageView.setContentDescription(LocaleController.getString(R.string.AccDescrForwarding));
        replyCloseImageView.setContentDescription(LocaleController.getString(R.string.AccDescrCancelForward));
        show();
    }

    /** Link preview: site name / title and description. */
    public void showLink(CharSequence title, CharSequence text) {
        mode = MODE_LINK;
        replyIconImageView.setImageResource(R.drawable.filled_link);
        setTexts(title, text);
        replyCloseImageView.setContentDescription(LocaleController.getString(R.string.AccDescrCancelReply));
        show();
    }

    private void setTexts(CharSequence name, CharSequence text) {
        replyNameTextView.setText(Emoji.replaceEmoji(name, replyNameTextView.getPaint().getFontMetricsInt(), false));
        CharSequence body = text == null ? "" : text.toString().replace('\n', ' ');
        replyObjectTextView.setText(Emoji.replaceEmoji(body, replyObjectTextView.getPaint().getFontMetricsInt(), false));
    }

    private void show() {
        topView.setVisibility(View.VISIBLE);
        enterView.showTopView(true, true);
    }

    public void hide(boolean animated) {
        mode = MODE_NONE;
        enterView.hideTopView(animated);
    }

    public boolean isShown() {
        return mode != MODE_NONE;
    }

    public void updateColors() {
        replyLineView.setBackgroundColor(getThemedColor(Theme.key_chat_replyPanelLine));
        replyIconImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_replyPanelIcons), PorterDuff.Mode.MULTIPLY));
        replyCloseImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_replyPanelClose), PorterDuff.Mode.MULTIPLY));
        if (Build.VERSION.SDK_INT >= 21) {
            replyCloseImageView.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_inappPlayerClose) & 0x19ffffff, 1, AndroidUtilities.dp(18)));
        }
        replyNameTextView.setTextColor(getThemedColor(Theme.key_chat_replyPanelName));
        replyObjectTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate delegate = this::updateColors;
        int[] keys = {Theme.key_chat_replyPanelLine, Theme.key_chat_replyPanelIcons, Theme.key_chat_replyPanelClose, Theme.key_chat_replyPanelName, Theme.key_windowBackgroundWhiteGrayText, Theme.key_inappPlayerClose};
        for (int key : keys) {
            descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, key));
        }
        return descriptions;
    }
}
