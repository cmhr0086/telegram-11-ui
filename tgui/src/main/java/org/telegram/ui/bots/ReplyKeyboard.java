/*
 * Adapted from Telegram for Android release-11.4.2-5469, reply keyboard models.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.bots;

import android.text.Spanned;
import android.text.SpannedString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Immutable host data for BotKeyboardView; contains no account or transport objects. */
public final class ReplyKeyboard {
    public static final class Button {
        public enum Type { TEXT, REQUEST_CONTACT, REQUEST_LOCATION, POLL, URL, COPY, WEB_VIEW }
        public final String id;
        public final CharSequence text;
        public final Type type;

        public Button(String id, CharSequence text, Type type) {
            if (id == null || id.isEmpty() || type == null) throw new IllegalArgumentException("button ID and type required");
            this.id = id;
            this.text = snapshot(text);
            this.type = type;
        }
    }

    public final String id;
    public final List<List<Button>> rows;
    public final boolean resize;
    public final boolean singleUse;
    public final CharSequence placeholder;

    public ReplyKeyboard(String id, List<? extends List<Button>> rows, boolean resize,
                         boolean singleUse, CharSequence placeholder) {
        if (id == null || id.isEmpty() || rows == null) throw new IllegalArgumentException("keyboard ID and rows required");
        this.id = id;
        ArrayList<List<Button>> copy = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        for (List<Button> row : rows) {
            if (row == null || row.isEmpty()) throw new IllegalArgumentException("keyboard rows must contain buttons");
            ArrayList<Button> buttons = new ArrayList<>();
            for (Button button : row) {
                if (button == null || !ids.add(button.id)) throw new IllegalArgumentException("button IDs must be unique within a keyboard");
                buttons.add(button);
            }
            copy.add(Collections.unmodifiableList(buttons));
        }
        this.rows = Collections.unmodifiableList(copy);
        this.resize = resize;
        this.singleUse = singleUse;
        this.placeholder = snapshot(placeholder);
    }

    private static CharSequence snapshot(CharSequence value) {
        return value == null ? "" : value instanceof Spanned ? new SpannedString(value) : value.toString();
    }
}
