/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2013-2018.
 */
// Host-owned user snapshot for the MentionsContainerView extract.
package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.text.SpannedString;

/** A locally supplied user candidate; no account lookup is performed. */
public final class Mention {
    public final long id;
    public final CharSequence name;
    public final String username;
    public final Drawable avatar;
    public Mention(long id, CharSequence name, String username, Drawable avatar) {
        this.id = id;
        String normalized = username == null ? null : username.trim();
        if (normalized != null) {
            while (normalized.startsWith("@")) normalized = normalized.substring(1);
            if (normalized.isEmpty()) normalized = null;
        }
        this.username = normalized;
        CharSequence display = name;
        if (display == null || display.toString().trim().isEmpty()) display = normalized != null ? normalized : Long.toString(id);
        this.name = display instanceof Spanned ? new SpannedString(display) : display.toString();
        this.avatar = avatar;
    }
}
