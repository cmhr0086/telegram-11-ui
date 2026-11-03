/*
 * Adapted from Telegram for Android release-11.4.2-5469, SuggestEmojiView.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Components;

import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.ViewTreeObserver;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** UI-thread binding; the host supplies keyword and emoji alternatives without network dependencies. */
public final class EmojiSuggestionsController {
    public interface Provider { void search(EmojiSuggestionQuery query, Results results); }
    public interface Results { void onResult(List<EmojiSuggestion> suggestions); }
    public interface Delegate {
        default void onSelected(EmojiSuggestion suggestion) { }
        default void onLongPressed(EmojiSuggestion suggestion) { }
    }
    private final ChatActivityEnterView input;
    private final ChatInputLayout parent;
    private final Provider provider;
    private final SuggestEmojiView view;
    private Delegate delegate;
    private EmojiSuggestionQuery query, displayedQuery;
    private String dismissedText;
    private int dismissedCursor = -1;
    private long generation;
    private boolean destroyed, replacing;
    private Runnable searchTask;
    private final Runnable updateTask = this::refresh;
    private final int[] editLocation = new int[2], parentLocation = new int[2];
    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable s) { scheduleRefresh(); }
    };
    private final ViewTreeObserver.OnPreDrawListener anchorListener = () -> { updateAnchor(); return true; };

    public EmojiSuggestionsController(ChatActivityEnterView input, ChatInputLayout parent, Provider provider, Theme.ResourcesProvider colors) {
        if (input == null || parent == null || provider == null) throw new IllegalArgumentException("input, parent and provider required");
        this.input = input; this.parent = parent; this.provider = provider;
        view = new SuggestEmojiView(input.getContext(), colors);
        view.setDelegate(new SuggestEmojiView.Delegate() {
            @Override public void onEmojiSelected(EmojiSuggestion suggestion) { select(suggestion); }
            @Override public void onEmojiLongPressed(EmojiSuggestion suggestion) {
                if (isCurrent() && delegate != null) delegate.onLongPressed(suggestion);
            }
        });
        input.attachEmojiSuggestionsController(this);
        parent.setEmojiSuggestionsView(view);
        input.getEditField().addTextChangedListener(watcher);
        parent.getViewTreeObserver().addOnPreDrawListener(anchorListener);
        refresh();
    }

    public SuggestEmojiView getView() { return view; }
    public void setDelegate(Delegate delegate) { this.delegate = delegate; }

    void scheduleRefresh() {
        if (destroyed || replacing) return;
        AndroidUtilities.cancelRunOnUIThread(updateTask);
        AndroidUtilities.runOnUIThread(updateTask, 16);
    }

    /** Requeries host data; first searches retain the original 600ms debounce. */
    public void refresh() {
        AndroidUtilities.cancelRunOnUIThread(updateTask);
        if (destroyed || replacing) return;
        if (searchTask != null) AndroidUtilities.cancelRunOnUIThread(searchTask);
        searchTask = null;
        final long request = ++generation;
        displayedQuery = null;
        EditTextCaption edit = input.getEditField();
        boolean mention = input.hasMentionsController() && MentionQuery.parse(edit.getText(), edit.getSelectionStart(), edit.getSelectionEnd()) != null;
        query = input.canShowMentionSuggestions() && !input.isStickersExpanded() && !mention
                ? EmojiSuggestionQuery.parse(edit.getText(), edit.getSelectionStart(), edit.getSelectionEnd()) : null;
        if (query == null || dismissedCursor == edit.getSelectionStart() && dismissedText != null && dismissedText.contentEquals(edit.getText())) {
            query = null; view.hide(); return;
        }
        dismissedText = null;
        final EmojiSuggestionQuery requested = query;
        searchTask = () -> {
            searchTask = null;
            if (destroyed || request != generation) return;
            provider.search(requested, result -> {
                List<EmojiSuggestion> snapshot = result == null ? Collections.emptyList() : new ArrayList<>(result);
                Runnable apply = () -> {
                    if (destroyed || request != generation || query != requested || !input.canShowMentionSuggestions() || input.isStickersExpanded()
                            || input.hasMentionsController() && MentionQuery.parse(edit.getText(), edit.getSelectionStart(), edit.getSelectionEnd()) != null
                            || !requested.matches(edit.getText(), edit.getSelectionStart(), edit.getSelectionEnd())) return;
                    displayedQuery = requested;
                    view.setSuggestions(snapshot);
                    updateAnchor();
                    if (snapshot.isEmpty()) view.hide(); else view.show();
                };
                if (Looper.myLooper() == Looper.getMainLooper()) apply.run(); else AndroidUtilities.runOnUIThread(apply);
            });
        };
        if (view.getSuggestions().isEmpty()) AndroidUtilities.runOnUIThread(searchTask, 600); else searchTask.run();
    }

    private boolean isCurrent() {
        EditTextCaption edit = input.getEditField();
        return !destroyed && query != null && query == displayedQuery && input.canShowMentionSuggestions() && !input.isStickersExpanded()
                && !(input.hasMentionsController() && MentionQuery.parse(edit.getText(), edit.getSelectionStart(), edit.getSelectionEnd()) != null)
                && query.matches(edit.getText(), edit.getSelectionStart(), edit.getSelectionEnd());
    }

    private void select(EmojiSuggestion suggestion) {
        if (!isCurrent()) return;
        Editable editable = input.getEditField().getText();
        EmojiSuggestionQuery selected = query;
        int length = selected.end - selected.start, cursor = selected.end;
        if (length <= 0) return;
        String before = editable.toString();
        replacing = true;
        try {
            // Preserve upstream replacement of adjacent identical emoji to the left.
            for (int start = selected.start; start >= 0 && before.regionMatches(start, selected.query, 0, length); start -= length) {
                if (editable.getSpans(start, start + length, AnimatedEmojiSpan.class).length > 0) break;
                for (Emoji.EmojiSpan old : editable.getSpans(start, start + length, Emoji.EmojiSpan.class)) editable.removeSpan(old);
                CharSequence replacement = makeEmoji(suggestion);
                editable.replace(start, start + length, replacement);
                cursor += replacement.length() - length;
                if (selected.type == EmojiSuggestionQuery.Type.KEYWORD) break;
            }
            Selection.setSelection(editable, Math.max(0, Math.min(cursor, editable.length())));
            input.getEditField().updateAnimatedEmoji(true);
        } finally { replacing = false; }
        generation++; displayedQuery = query = null;
        dismissedText = editable.toString(); dismissedCursor = input.getEditField().getSelectionStart();
        view.hide();
        Emoji.addRecentEmoji(suggestion.documentId == 0 ? suggestion.emoji.toString() : "animated_" + suggestion.documentId);
        try { input.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING); } catch (Exception ignored) { }
        if (delegate != null) delegate.onSelected(suggestion);
    }

    private CharSequence makeEmoji(EmojiSuggestion suggestion) {
        if (suggestion.documentId == 0) return Emoji.replaceEmoji(suggestion.emoji, input.getEditField().getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), true);
        SpannableString value = new SpannableString(suggestion.emoji);
        value.setSpan(new AnimatedEmojiSpan(suggestion.documentId, input.getEditField().getPaint().getFontMetricsInt()), 0, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }

    private void updateAnchor() {
        if (destroyed || query == null) return;
        EditTextCaption edit = input.getEditField();
        Layout layout = edit.getLayout();
        if (layout == null || query.end > edit.length()) return;
        edit.getLocationInWindow(editLocation); parent.getLocationInWindow(parentLocation);
        float x = editLocation[0] - parentLocation[0] + edit.getPaddingLeft() + AndroidUtilities.dp(12);
        float spanY = AndroidUtilities.dp(10);
        if (query.type == EmojiSuggestionQuery.Type.EMOJI) {
            int line = layout.getLineForOffset(query.start);
            float endX = layout.getLineForOffset(query.end) == line
                    ? layout.getPrimaryHorizontal(query.end)
                    : layout.isRtlCharAt(query.start) ? layout.getLineLeft(line) : layout.getLineRight(line);
            float spanX = (layout.getPrimaryHorizontal(query.start) + endX) / 2f;
            spanY = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f;
            Spanned spans = edit.getText();
            for (Emoji.EmojiSpan span : spans.getSpans(query.start, query.end, Emoji.EmojiSpan.class)) {
                if (span.drawn && spans.getSpanStart(span) == query.start && spans.getSpanEnd(span) == query.end) {
                    // Upstream anchors to the rendered span, including bidi and soft-wrap placement.
                    spanX = span.lastDrawX;
                    spanY = span.lastDrawY;
                    break;
                }
            }
            x = editLocation[0] - parentLocation[0] + edit.getPaddingLeft() - edit.getScrollX() + spanX;
        }
        view.setAnchor(x);
        parent.setEmojiSuggestionsAnchorOffset(-edit.getHeight() - edit.getScrollY() + spanY + AndroidUtilities.dp(5));
    }

    public void updateColors() { if (!destroyed) view.updateColors(); }
    public void destroy() {
        if (destroyed) return;
        destroyed = true; generation++;
        AndroidUtilities.cancelRunOnUIThread(updateTask);
        if (searchTask != null) AndroidUtilities.cancelRunOnUIThread(searchTask);
        input.getEditField().removeTextChangedListener(watcher);
        if (parent.getViewTreeObserver().isAlive()) parent.getViewTreeObserver().removeOnPreDrawListener(anchorListener);
        input.detachEmojiSuggestionsController(this);
        if (parent.getEmojiSuggestionsView() == view) parent.setEmojiSuggestionsView(null);
        query = displayedQuery = null; delegate = null; view.destroy();
    }
}
