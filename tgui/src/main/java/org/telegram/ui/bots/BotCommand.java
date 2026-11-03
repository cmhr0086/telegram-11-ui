/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.bots;

import android.text.SpannedString;

/** Host-owned command snapshot; execution remains the host's responsibility. */
public final class BotCommand {
    public final String id, command;
    public final CharSequence description;
    public BotCommand(String id, String command, CharSequence description) {
        if (id == null || id.isEmpty() || command == null || command.isEmpty()) throw new IllegalArgumentException("id and command required");
        for (int i = 0; i < command.length(); i++) if (Character.isWhitespace(command.charAt(i)) || Character.isSpaceChar(command.charAt(i))) throw new IllegalArgumentException("command must not contain whitespace");
        int start = 0; while (start < command.length() && command.charAt(start) == '/') start++;
        if (start == command.length()) throw new IllegalArgumentException("command required after slash");
        this.id = id; this.command = "/" + command.substring(start);
        this.description = description == null ? "" : SpannedString.valueOf(description);
    }
}
