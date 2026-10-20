package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.CompoundEmoji;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

/**
 * tgui placeholder for upstream EmojiView (emoji / sticker / GIF keyboard, ~9.2k lines).
 *
 * <p>Phase A of the ChatActivityEnterView port keeps the input bar's own panel logic intact
 * (keyboard ↔ panel switching, smooth keyboard animation, emoji button icon morphing): this
 * class provides an empty panel with the upstream background colour and the same API surface,
 * so that code runs unchanged. The real EmojiView replaces it in the next phase (see README
 * roadmap). Hosts can put their own content into it in the meantime via
 * {@code ChatActivityEnterView.Delegate#onEmojiPanelCreated}.
 */
public class EmojiView extends FrameLayout {

    public interface EmojiViewDelegate {
        default boolean onBackspace() {
            return false;
        }

        default void onEmojiSelected(String emoji) {
        }

        default void onCustomEmojiSelected(long documentId, TLRPC.Document document, String emoticon, boolean isRecent) {
        }

        default void onTabOpened(int type) {
        }

        default void onClearEmojiRecent() {
        }

        default void onSearchOpenClose(int type) {
        }

        default boolean isSearchOpened() {
            return false;
        }

        default boolean isExpanded() {
            return false;
        }

        default boolean canSchedule() {
            return false;
        }

        default boolean isInScheduleMode() {
            return false;
        }

        default long getDialogId() {
            return 0;
        }

        default int getThreadId() {
            return 0;
        }

        default void invalidateEnterView() {
        }

        default float getProgressToSearchOpened() {
            return 0f;
        }
    }

    public interface DragListener {
        void onDragStart();

        void onDragEnd(float velocity);

        void onDragCancel();

        void onDrag(int offset);
    }

    public int currentAccount = UserConfig.selectedAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private EmojiViewDelegate delegate;
    private boolean showing;
    private final boolean shouldDrawBackground;

    public EmojiView(Context context, boolean shouldDrawBackground, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.shouldDrawBackground = shouldDrawBackground;
        updateColors();
    }

    public void updateColors() {
        if (shouldDrawBackground) {
            setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground, resourcesProvider));
        } else {
            setBackground(null);
        }
    }

    public void setDelegate(EmojiViewDelegate delegate) {
        this.delegate = delegate;
    }

    public EmojiViewDelegate getDelegate() {
        return delegate;
    }

    public void setDragListener(DragListener listener) {
    }

    public void setAllow(boolean stickers, boolean gifs, boolean animate) {
    }

    public void setShowing(boolean showing) {
        this.showing = showing;
    }

    public boolean isShowing() {
        return showing;
    }

    public void setStickersBanned(boolean emojiBanned, boolean stickersBanned, long chatId) {
    }

    public void onOpen(boolean forceEmoji, boolean groupEmojiPackHintWasVisible) {
    }

    public void closeSearch(boolean animated) {
    }

    public void closeSearch(boolean animated, long scrollToSet) {
    }

    public void hideSearchKeyboard() {
    }

    public void showSearchField(boolean show) {
    }

    public void searchProgressChanged() {
    }

    public int getCurrentPage() {
        return 0;
    }

    public float getStickersExpandOffset() {
        return 0;
    }

    public void switchToGifRecent() {
    }

    public void invalidateViews() {
    }

    public void onDestroy() {
    }

    public void freeze(boolean freeze) {
    }

    public void clearRecentEmoji() {
    }

    public boolean areThereAnyStickers() {
        return false;
    }

    public void addEmojiToRecent(String code) {
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Placeholder for upstream's emoji search progress, used by the input bar. */
    public static boolean isSearchSupported() {
        return false;
    }

    public View getContentView() {
        return this;
    }

    /** upstream, verbatim (used by EmojiColorPickerWindow) */
    public static String addColorToCode(String code, String color) {
        if (CompoundEmoji.isHandshake(code) != null) {
            return CompoundEmoji.applyColor(code, color);
        } else {
            String end = null;
            boolean invert = false;
            if (Emoji.endsWithRightArrow(code)) {
                code = code.substring(0, code.length() - 2);
                invert = true;
            }
            int length = code.length();
            if (length > 2 && code.charAt(code.length() - 2) == '\u200D') {
                end = code.substring(code.length() - 2);
                code = code.substring(0, code.length() - 2);
            } else if (length > 3 && code.charAt(code.length() - 3) == '\u200D') {
                end = code.substring(code.length() - 3);
                code = code.substring(0, code.length() - 3);
            }
            code += color;
            if (end != null) {
                code += end;
            }
            if (invert) {
                code += "\u200D➡";
            }
            return code;
        }
    }

}
