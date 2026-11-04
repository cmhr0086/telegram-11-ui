package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Host values replacing the attach menu's account, document and network objects. */
public final class Attachment {
    public interface SelectionListener { void onSelectionChanged(List<Attachment> selection); }
    public enum Type { PHOTO, VIDEO, FILE, CONTACT, LOCATION, POLL }
    public final long id;
    public final Type type;
    public final Uri uri;
    public final String mimeType, name;
    public final long size;
    public final int width, height, duration;
    public final Drawable thumbnail;
    public final Contact contact;
    public final Location location;
    public final Poll poll;
    private Attachment(long id, Type type, Uri uri, String mime, String name, long size,
                       int width, int height, int duration, Drawable thumbnail,
                       Contact contact, Location location, Poll poll) {
        this.id = id; this.type = type; this.uri = uri;
        this.mimeType = mime == null ? "application/octet-stream" : mime;
        this.name = name == null ? "" : name; this.size = Math.max(0, size);
        this.width = width; this.height = height; this.duration = Math.max(0, duration);
        this.thumbnail = thumbnail; this.contact = contact; this.location = location; this.poll = poll;
    }
    public static Attachment media(long id, Uri uri, String mime, String name, long size,
                                   int width, int height, int duration, Drawable thumbnail) {
        if (uri == null) throw new IllegalArgumentException("uri required");
        return new Attachment(id, mime != null && mime.startsWith("video/") ? Type.VIDEO : Type.PHOTO,
                uri, mime, name, size, width, height, duration, thumbnail, null, null, null);
    }
    public static Attachment file(long id, Uri uri, String name, String mime, long size) {
        if (uri == null) throw new IllegalArgumentException("uri required");
        return new Attachment(id, Type.FILE, uri, mime, name, size, 0, 0, 0, null, null, null, null);
    }
    public static Attachment contact(Contact contact) {
        if (contact == null) throw new IllegalArgumentException("contact required");
        return new Attachment(contact.id, Type.CONTACT, null, null, contact.name, 0, 0, 0, 0, contact.avatar, contact, null, null);
    }
    public static Attachment location(Location location) {
        if (location == null) throw new IllegalArgumentException("location required");
        return new Attachment(0, Type.LOCATION, null, null, location.title, 0, 0, 0, 0, location.mapImage, null, location, null);
    }
    public static Attachment poll(Poll poll) {
        if (poll == null) throw new IllegalArgumentException("poll required");
        return new Attachment(0, Type.POLL, null, null, poll.question, 0, 0, 0, 0, null, null, null, poll);
    }
    public static final class Contact {
        public final long id;
        public final String name, phone;
        public final Drawable avatar;
        public Contact(long id, String name, String phone, Drawable avatar) {
            this.id = id; this.name = name == null ? "" : name; this.phone = phone == null ? "" : phone; this.avatar = avatar;
        }
    }
    public static final class Location {
        public final double latitude, longitude;
        public final String title;
        public final Drawable mapImage;
        public Location(double latitude, double longitude, String title, Drawable mapImage) {
            if (Double.isNaN(latitude) || Double.isNaN(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
                throw new IllegalArgumentException("invalid coordinates");
            this.latitude = latitude; this.longitude = longitude; this.title = title == null ? "" : title; this.mapImage = mapImage;
        }
    }
    public static final class Poll {
        public final String question;
        public final List<String> options;
        public final boolean anonymous, multipleChoice;
        public Poll(String question, List<String> options, boolean anonymous, boolean multipleChoice) {
            if (question == null || question.trim().isEmpty() || question.length() > 255 || options == null || options.size() < 2 || options.size() > 10)
                throw new IllegalArgumentException("poll requires a question and 2..10 options");
            ArrayList<String> copy = new ArrayList<>();
            for (String option : options) {
                if (option == null || option.trim().isEmpty() || option.length() > 100) throw new IllegalArgumentException("invalid option");
                copy.add(option.trim());
            }
            this.question = question.trim(); this.options = Collections.unmodifiableList(copy);
            this.anonymous = anonymous; this.multipleChoice = multipleChoice;
        }
    }
}
