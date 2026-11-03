/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2013-2018.
 *
 * tgui: assembly extracted from DemoChatFragment and samples/host-app/ChatFragment,
 * using the transplanted release-11.4.2-5469 chat components without account business.
 */
package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatAvatarDecoration;
import org.telegram.ui.Cells.ChatUnreadCell;
import org.telegram.ui.Cells.TextBubbleCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatBubbleItemAnimator;
import org.telegram.ui.Components.ChatInputLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Existing chat components assembled as a fragment. Hosts retain ownership of their adapter,
 * data, sending, media playback, selection mode and input configuration.
 *
 * Implement the adapter/delegate factories and configureChat; call super from lifecycle,
 * notification and theme-description overrides. For selection mode, consume Back in the host
 * before calling super. Views may be rebuilt: configuration must bind existing host data.
 */
public abstract class ChatFragmentBase extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    protected ChatInputLayout contentView;
    protected RecyclerListView listView;
    protected LinearLayoutManager layoutManager;
    protected ChatActivityEnterView enterView;

    private boolean observing;
    private View observedActionBar;
    private CharSequence savedDraft;
    private int savedSelectionStart = -1, savedSelectionEnd = -1;
    private final View.OnLayoutChangeListener paddingListener = (view, l, t, r, b, oldL, oldT, oldR, oldB) -> updateListPadding();

    public ChatFragmentBase() { super(); }
    public ChatFragmentBase(Bundle arguments) { super(arguments); }

    protected abstract RecyclerView.Adapter createMessageAdapter(Context context);
    protected abstract ChatActivityEnterView.ChatActivityEnterViewDelegate createInputDelegate();

    /** Called with the list and input installed, before the action bar is added on top. */
    protected void configureChat(Context context) { }

    protected RecyclerListView createMessageList(Context context) {
        return new RecyclerListView(context) {
            @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                invalidateViews(); // Gradient bubbles depend on their position in the list.
            }
        };
    }

    protected ChatActivityEnterView createInputView(Context context) {
        return new ChatActivityEnterView(getParentActivity(), contentView, this, true, getResourceProvider());
    }

    @Override public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        if (!observing) {
            observing = true;
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        }
        return true;
    }

    @Override public View createView(Context context) {
        releaseChatViews(true);
        actionBar.setAddToContainer(false);
        contentView = new ChatInputLayout(context, parentLayout);
        contentView.setResourcesProvider(getResourceProvider());
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        fragmentView = contentView;

        listView = createMessageList(context);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        layoutManager.setStackFromEnd(true);
        listView.setLayoutManager(layoutManager);
        listView.setItemAnimator(new ChatBubbleItemAnimator());
        listView.addItemDecoration(new ChatAvatarDecoration());
        listView.setVerticalScrollBarEnabled(true);
        listView.setClipToPadding(false);
        listView.setInstantClick(true);
        listView.setDisableHighlightState(true);
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                recyclerView.invalidate();
                if (recyclerView instanceof RecyclerListView) ((RecyclerListView) recyclerView).invalidateViews();
            }
        });
        listView.setAdapter(createMessageAdapter(context));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentView.setContentView(listView);

        enterView = createInputView(context);
        enterView.setDelegate(createInputDelegate());
        contentView.addView(enterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        contentView.setEnterView(enterView);
        if (savedDraft != null) {
            enterView.setFieldText(savedDraft, true, true);
            int length = enterView.getEditField().length();
            if (savedSelectionStart >= 0 && savedSelectionEnd >= 0) {
                enterView.getEditField().setSelection(Math.min(savedSelectionStart, length), Math.min(savedSelectionEnd, length));
            }
            savedDraft = null;
        }
        configureChat(context);
        if (actionBar.getParent() instanceof ViewGroup) ((ViewGroup) actionBar.getParent()).removeView(actionBar);
        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        contentView.setActionBar(actionBar);
        observedActionBar = actionBar;
        observedActionBar.addOnLayoutChangeListener(paddingListener);
        contentView.addOnLayoutChangeListener(paddingListener);
        updateListPadding();
        return fragmentView;
    }

    /** The original 4dp / 3dp insets, recalculated for status-bar policy and bar height changes. */
    protected void updateListPadding() {
        if (listView == null || actionBar == null) return;
        int barHeight = actionBar.getVisibility() == View.GONE ? 0 : actionBar.getMeasuredHeight();
        if (barHeight == 0 && actionBar.getVisibility() != View.GONE) {
            barHeight = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
        }
        int top = barHeight + AndroidUtilities.dp(4), bottom = AndroidUtilities.dp(3);
        if (listView.getPaddingTop() != top || listView.getPaddingBottom() != bottom) {
            listView.setPadding(listView.getPaddingLeft(), top, listView.getPaddingRight(), bottom);
        }
    }

    @Override public void onResume() {
        super.onResume();
        if (contentView != null) contentView.onResume();
        if (enterView != null) enterView.onResume();
    }

    @Override public void onPause() {
        super.onPause();
        if (contentView != null) contentView.onPause();
        if (enterView != null) enterView.onPause();
    }

    @Override public boolean onBackPressed() {
        if (enterView != null && (enterView.botCommandsMenuIsShowing() || enterView.isPopupShowing())) {
            enterView.hidePopup(true);
            return false;
        }
        return super.onBackPressed();
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded && listView != null) {
            Emoji.invalidateAll(listView);
        } else if (id == NotificationCenter.didSetNewWallpapper && contentView != null) {
            if (!Theme.isAnimatingColor()) contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            onChatColorsChanged();
        }
    }

    /** Redraw hook for host overlays; call super when overriding. */
    protected void onChatColorsChanged() {
        if (enterView != null) enterView.updateColors();
        if (listView != null) {
            listView.invalidateViews();
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof ChatUnreadCell) ((ChatUnreadCell) child).updateColors();
            }
        }
        if (contentView != null) contentView.invalidateBackground();
    }

    @Override public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate redraw = this::onChatColorsChanged;
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
        return descriptions;
    }

    private void releaseChatViews(boolean retainDraft) {
        if (observedActionBar != null) observedActionBar.removeOnLayoutChangeListener(paddingListener);
        observedActionBar = null;
        if (contentView != null) {
            contentView.removeOnLayoutChangeListener(paddingListener);
            contentView.onPause();
        }
        if (enterView != null) {
            if (retainDraft) {
                CharSequence draft = enterView.getFieldText();
                savedDraft = draft == null ? "" : new SpannableStringBuilder(draft);
                savedSelectionStart = enterView.getEditField().getSelectionStart();
                savedSelectionEnd = enterView.getEditField().getSelectionEnd();
            }
            enterView.onDestroy();
            enterView = null;
        }
        if (listView != null) listView.setAdapter(null);
        listView = null;
        layoutManager = null;
        contentView = null;
    }

    @Override public void clearViews() {
        releaseChatViews(true);
        super.clearViews();
    }

    @Override public void onFragmentDestroy() {
        if (observing) {
            observing = false;
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        }
        releaseChatViews(false);
        savedDraft = null;
        super.onFragmentDestroy();
    }
}
