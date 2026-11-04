/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Cells;

import android.text.TextUtils;

/** Host-owned service event data, replacing the MessageObject service-action branches. */
public final class ServiceMessage {
    public enum Kind { JOIN, INVITE, TITLE, PIN, CUSTOM }
    public enum PinnedKind { TEXT, PHOTO, VIDEO, VOICE, MUSIC, FILE, CONTACT, LOCATION, POLL, STICKER, GIF }
    public static final class Person {
        public final Object id;
        public final CharSequence name;
        public Person(Object id, CharSequence name) {
            this.id = id;
            this.name = TextUtils.isEmpty(name) ? "" : name.toString();
        }
    }
    public final Object id;
    public final Kind kind;
    public final Person actor, target;
    public final boolean actorIsSelf, targetIsSelf, megagroup;
    public final CharSequence text;
    public final Object pinnedId;
    public final PinnedKind pinnedKind;

    private ServiceMessage(Object id, Kind kind, Person actor, Person target,
                           boolean actorIsSelf, boolean targetIsSelf, CharSequence text, Object pinnedId, PinnedKind pinnedKind, boolean megagroup) {
        this.id = id; this.kind = kind; this.actor = actor; this.target = target;
        this.actorIsSelf = actorIsSelf; this.targetIsSelf = targetIsSelf;
        this.text = text; this.pinnedId = pinnedId; this.pinnedKind = pinnedKind; this.megagroup = megagroup;
    }
    public static ServiceMessage join(Object id, Person actor, boolean isSelf) {
        return join(id, actor, isSelf, false);
    }
    /** megagroup selects the upstream channel-megagroup wording. */
    public static ServiceMessage join(Object id, Person actor, boolean isSelf, boolean megagroup) {
        return new ServiceMessage(id, Kind.JOIN, actor, null, isSelf, false, null, null, null, megagroup);
    }
    public static ServiceMessage invite(Object id, Person actor, Person target, boolean actorIsSelf, boolean targetIsSelf) {
        return new ServiceMessage(id, Kind.INVITE, actor, target, actorIsSelf, targetIsSelf, null, null, null, false);
    }
    public static ServiceMessage title(Object id, Person actor, boolean actorIsSelf, CharSequence title) {
        return new ServiceMessage(id, Kind.TITLE, actor, null, actorIsSelf, false, title, null, null, false);
    }
    public static ServiceMessage pin(Object id, Person actor, boolean actorIsSelf, Object pinnedId, CharSequence preview) {
        return pin(id, actor, actorIsSelf, pinnedId, PinnedKind.TEXT, preview);
    }
    public static ServiceMessage pin(Object id, Person actor, boolean actorIsSelf, Object pinnedId, PinnedKind kind, CharSequence preview) {
        return new ServiceMessage(id, Kind.PIN, actor, null, actorIsSelf, false, preview, pinnedId, kind == null ? PinnedKind.TEXT : kind, false);
    }
    public static ServiceMessage custom(Object id, CharSequence text) {
        return new ServiceMessage(id, Kind.CUSTOM, null, null, false, false, text, null, null, false);
    }
}
