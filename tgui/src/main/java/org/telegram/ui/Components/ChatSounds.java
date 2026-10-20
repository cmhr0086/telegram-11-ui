package org.telegram.ui.Components;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.SystemClock;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

/**
 * tgui: the in-chat "message sent" / "message received" sounds (res/raw/sound_out, sound_in),
 * played like upstream NotificationsController.playOutChatSound() / playInChatSound():
 * a SoundPool on the system stream, skipped when the ringer is silent.
 */
public final class ChatSounds {

    private static SoundPool soundPool;
    private static int soundIn, soundOut;
    private static boolean soundInReady, soundOutReady;
    private static boolean pendingIn, pendingOut;
    private static long lastSoundInPlay, lastSoundOutPlay;
    private static boolean enabled = true;

    private ChatSounds() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Loads both sounds ahead of time so the first message does not play late. */
    public static void preload() {
        Utilities.globalQueue.postRunnable(ChatSounds::ensurePool);
    }

    public static void playOut() {
        if (!canPlay()) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            if (Math.abs(SystemClock.elapsedRealtime() - lastSoundOutPlay) <= 100) {
                return;
            }
            lastSoundOutPlay = SystemClock.elapsedRealtime();
            ensurePool();
            if (soundOutReady) {
                play(soundOut);
            } else {
                pendingOut = true;
            }
        });
    }

    public static void playIn() {
        if (!canPlay()) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            if (Math.abs(SystemClock.elapsedRealtime() - lastSoundInPlay) <= 500) {
                return;
            }
            lastSoundInPlay = SystemClock.elapsedRealtime();
            ensurePool();
            if (soundInReady) {
                play(soundIn);
            } else {
                pendingIn = true;
            }
        });
    }

    private static boolean canPlay() {
        if (!enabled) {
            return false;
        }
        try {
            AudioManager audioManager = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null && audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                return false;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    private static void ensurePool() {
        if (soundPool != null) {
            return;
        }
        try {
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> Utilities.globalQueue.postRunnable(() -> {
                if (status != 0) {
                    return;
                }
                if (sampleId == soundIn) {
                    soundInReady = true;
                    if (pendingIn) {
                        pendingIn = false;
                        play(soundIn);
                    }
                } else if (sampleId == soundOut) {
                    soundOutReady = true;
                    if (pendingOut) {
                        pendingOut = false;
                        play(soundOut);
                    }
                }
            }));
            Context context = ApplicationLoader.applicationContext;
            soundIn = soundPool.load(context, R.raw.sound_in, 1);
            soundOut = soundPool.load(context, R.raw.sound_out, 1);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void play(int sampleId) {
        try {
            soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
