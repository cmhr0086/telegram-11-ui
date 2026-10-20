package org.telegram.messenger;

// tgui stub: upstream LiteMode toggles heavy effects by device class; all effects stay on here
// except where a flag is explicitly disabled through setEnabled().
public class LiteMode {

    public static final int FLAG_ANIMATED_STICKERS_KEYBOARD = 1;
    public static final int FLAG_ANIMATED_STICKERS_CHAT = 2;
    public static final int FLAG_ANIMATED_EMOJI_KEYBOARD_PREMIUM = 4;
    public static final int FLAG_ANIMATED_EMOJI_KEYBOARD_NOT_PREMIUM = 16384;
    public static final int FLAG_ANIMATED_EMOJI_REACTIONS_PREMIUM = 8;
    public static final int FLAG_ANIMATED_EMOJI_REACTIONS_NOT_PREMIUM = 8192;
    public static final int FLAG_ANIMATED_EMOJI_CHAT_PREMIUM = 16;
    public static final int FLAG_ANIMATED_EMOJI_CHAT_NOT_PREMIUM = 4096;
    public static final int FLAG_CHAT_BACKGROUND = 32;
    public static final int FLAG_CHAT_FORUM_TWOCOLUMN = 64;
    public static final int FLAG_CALLS_ANIMATIONS = 512;
    public static final int FLAG_AUTOPLAY_VIDEOS = 1024;
    public static final int FLAG_AUTOPLAY_GIFS = 2048;
    public static final int FLAG_CHAT_BLUR = 256;
    public static final int FLAG_CHAT_SCALE = 32768;
    public static final int FLAG_CHAT_THANOS = 65536;
    public static final int FLAG_CHAT_SPOILER = 128;
    public static final int FLAGS_CHAT = FLAG_CHAT_BACKGROUND | FLAG_CHAT_FORUM_TWOCOLUMN | FLAG_CHAT_SPOILER | FLAG_CHAT_BLUR | FLAG_CHAT_SCALE | FLAG_CHAT_THANOS;

    private static int disabledFlags = FLAG_CHAT_BLUR;

    public static boolean isEnabled(int flag) {
        return (disabledFlags & flag) == 0;
    }

    public static boolean isEnabledSetting(int flag) {
        return isEnabled(flag);
    }

    public static void setEnabled(int flag, boolean enabled) {
        if (enabled) {
            disabledFlags &= ~flag;
        } else {
            disabledFlags |= flag;
        }
    }

    public static boolean isPowerSaverApplied() {
        return false;
    }
}
