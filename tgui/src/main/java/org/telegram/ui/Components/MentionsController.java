/*
 * Adapted from Telegram for Android release-11.4.2-5469, mention selection wiring.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Components;

import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Binds the upstream mention panel to an input; search/data remain host-owned. UI-thread API. */
public final class MentionsController {
    public interface Provider {
        /** May complete asynchronously; return an empty list when no candidates match. */
        void search(MentionQuery query, Results results);
    }
    public interface Results { void onResult(List<Mention> mentions); }

    private final ChatActivityEnterView input;
    private final ChatInputLayout container;
    private final Provider provider;
    private final MentionsContainerView view;
    private MentionQuery query, displayedQuery;
    private long generation;
    private boolean destroyed, replacing;
    private final Runnable refreshTask = this::refresh;
    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable s) { scheduleRefresh(); }
    };

    public MentionsController(ChatActivityEnterView input, ChatInputLayout container,
                              Provider provider, Theme.ResourcesProvider resourcesProvider) {
        if (input == null || container == null || provider == null) throw new IllegalArgumentException("input, container and provider required");
        this.input = input;
        this.container = container;
        this.provider = provider;
        view = new MentionsContainerView(input.getContext(), resourcesProvider);
        view.setDelegate(this::select);
        input.attachMentionsController(this);
        container.setInputSuggestionsView(view);
        input.getEditField().addTextChangedListener(watcher);
        refresh();
    }

    public MentionsContainerView getView() { return view; }

    // EditText may dispatch text and selection changes before its final cursor is installed.
    void scheduleRefresh() {
        if (destroyed || replacing) return;
        AndroidUtilities.cancelRunOnUIThread(refreshTask);
        AndroidUtilities.runOnUIThread(refreshTask);
    }

    /** Requery after the host's candidate source changes, even when the input is unchanged. */
    public void refresh() {
        AndroidUtilities.cancelRunOnUIThread(refreshTask);
        if (destroyed || replacing) return;
        final long request = ++generation;
        EditTextCaption edit = input.getEditField();
        query = input.canShowMentionSuggestions()
                ? MentionQuery.parse(edit.getText(), edit.getSelectionStart(), edit.getSelectionEnd()) : null;
        displayedQuery = null;
        final MentionQuery requested = query;
        if (requested == null) { view.hide(); return; }
        provider.search(requested, result -> {
            List<Mention> snapshot = result == null ? Collections.emptyList() : new ArrayList<>(result);
            Runnable apply = () -> {
                if (destroyed || request != generation || query != requested || !input.canShowMentionSuggestions()
                        || !requested.matches(edit.getText(), edit.getSelectionStart(), edit.getSelectionEnd())) return;
                displayedQuery = requested;
                view.setMentions(snapshot);
                if (snapshot.isEmpty()) view.hide(); else view.show();
            };
            if (Looper.myLooper() == Looper.getMainLooper()) apply.run(); else AndroidUtilities.runOnUIThread(apply);
        });
    }

    private void select(Mention mention) {
        EditTextCaption edit = input.getEditField();
        MentionQuery selected = query;
        if (destroyed || selected == null || displayedQuery != selected || !input.canShowMentionSuggestions()
                || !selected.matches(edit.getText(), edit.getSelectionStart(), edit.getSelectionEnd())) return;
        CharSequence replacement;
        if (mention.username != null && !mention.username.isEmpty()) {
            replacement = "@" + mention.username + " ";
        } else {
            SpannableString name = new SpannableString(mention.name + " ");
            name.setSpan(new URLSpanUserMention(Long.toString(mention.id), 3), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            replacement = name;
        }
        replacing = true;
        try { input.replaceWithText(selected.start, selected.end - selected.start, replacement, false); }
        finally { replacing = false; }
        generation++;
        query = null;
        displayedQuery = null;
        view.hide();
    }

    public void updateColors() { if (!destroyed) view.updateColors(); }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        AndroidUtilities.cancelRunOnUIThread(refreshTask);
        generation++;
        query = null;
        displayedQuery = null;
        input.getEditField().removeTextChangedListener(watcher);
        input.detachMentionsController(this);
        if (container.getInputSuggestionsView() == view) container.setInputSuggestionsView(null);
        view.destroy();
    }
}
