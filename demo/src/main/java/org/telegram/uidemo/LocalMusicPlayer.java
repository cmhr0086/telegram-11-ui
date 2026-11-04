package org.telegram.uidemo;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Cells.TextBubbleMessage;

/** Demo-owned local audio session. Public operations must run on the main thread. */
final class LocalMusicPlayer implements AudioManager.OnAudioFocusChangeListener {
    private final Context context;
    private final AudioManager audio;
    private final Runnable onChanged;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private TextBubbleMessage message;
    private boolean prepared, wantsPlaying, hasFocus, seeking;
    private Integer pendingSeek;
    private final Runnable tick = this::update;

    LocalMusicPlayer(Context context, Runnable onChanged) {
        this.context = context.getApplicationContext();
        this.onChanged = onChanged;
        this.audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    TextBubbleMessage getPlayingMessage() { return message; }

    void toggle(TextBubbleMessage next, Uri uri) {
        if (next == null) return;
        if (message == next && player != null) {
            if (wantsPlaying) pause();
            else {
                if (!focus()) { notifyChanged(); return; }
                wantsPlaying = true;
                if (prepared) {
                    try { player.start(); next.playing = true; next.audioPaused = false; }
                    catch (RuntimeException e) { fail(e); return; }
                    update();
                } else notifyChanged();
            }
            return;
        }
        stop();
        if (uri == null || !focus()) return;
        message = next;
        wantsPlaying = true;
        next.playing = false; next.audioPaused = false;
        try {
            MediaPlayer created = new MediaPlayer();
            player = created;
            created.setAudioStreamType(AudioManager.STREAM_MUSIC);
            created.setDataSource(context, uri);
            created.setOnPreparedListener(mp -> {
                if (player != mp || message == null) return;
                prepared = true;
                if (message.audioProgress > 0 && message.audioProgress < 1) {
                    try { requestSeek((int) (mp.getDuration() * message.audioProgress)); }
                    catch (RuntimeException e) { fail(e); return; }
                }
                if (wantsPlaying && hasFocus) {
                    try { mp.start(); message.playing = true; message.audioPaused = false; }
                    catch (RuntimeException e) { fail(e); return; }
                } else { message.playing = false; message.audioPaused = true; }
                update();
            });
            created.setOnSeekCompleteListener(mp -> {
                if (player != mp || message == null) return;
                if (pendingSeek != null) {
                    int nextPosition = pendingSeek;
                    pendingSeek = null;
                    try { mp.seekTo(nextPosition); }
                    catch (RuntimeException e) { fail(e); }
                } else {
                    seeking = false;
                    update();
                }
            });
            created.setOnCompletionListener(mp -> {
                if (player != mp) return;
                message.audioProgress = 0; message.audioProgressSec = 0;
                stop();
            });
            created.setOnErrorListener((mp, what, extra) -> {
                if (player == mp) fail(null);
                return true;
            });
            created.prepareAsync();
            notifyChanged();
        } catch (Exception e) { fail(e); }
    }

    void seek(TextBubbleMessage target, float progress) {
        if (target != message || !prepared || player == null || Float.isNaN(progress)) return;
        float clamped = Math.max(0, Math.min(1, progress));
        try {
            int duration = player.getDuration();
            int position = (int) (duration * clamped);
            target.audioProgress = clamped;
            target.audioProgressSec = position / 1000;
            requestSeek(position);
            notifyChanged();
        } catch (RuntimeException e) { fail(e); }
    }

    private void requestSeek(int position) {
        if (seeking) { pendingSeek = position; return; }
        seeking = true;
        player.seekTo(position);
    }

    private void fail(Exception error) {
        if (error != null) FileLog.e(error);
        stop();
        Toast.makeText(context, R.string.music_playback_error, Toast.LENGTH_SHORT).show();
    }

    private boolean focus() {
        if (hasFocus) return true;
        hasFocus = audio == null || audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasFocus;
    }

    private void pause() {
        wantsPlaying = false;
        handler.removeCallbacks(tick);
        if (prepared && player != null) {
            try { updatePosition(); player.pause(); }
            catch (RuntimeException e) { fail(e); return; }
        }
        if (message != null) { message.playing = false; message.audioPaused = true; }
        abandonFocus();
        notifyChanged();
    }

    private void updatePosition() {
        if (seeking) return;
        int elapsed = player.getCurrentPosition(), duration = player.getDuration();
        message.audioProgressSec = elapsed / 1000;
        message.audioProgress = duration <= 0 ? 0 : Math.min(1f, elapsed / (float) duration);
    }

    private void update() {
        handler.removeCallbacks(tick);
        if (player == null || message == null || !prepared) return;
        try { updatePosition(); }
        catch (RuntimeException e) { fail(e); return; }
        notifyChanged();
        if (wantsPlaying) handler.postDelayed(tick, 100);
    }

    void stop() {
        handler.removeCallbacks(tick);
        TextBubbleMessage old = message;
        message = null;
        MediaPlayer oldPlayer = player; player = null;
        prepared = wantsPlaying = seeking = false;
        pendingSeek = null;
        if (old != null) { old.playing = false; old.audioPaused = false; }
        if (oldPlayer != null) try { oldPlayer.release(); } catch (RuntimeException ignored) { }
        abandonFocus();
        if (old != null) notifyChanged();
    }

    private void abandonFocus() {
        if (hasFocus && audio != null) audio.abandonAudioFocus(this);
        hasFocus = false;
    }

    private void notifyChanged() { if (onChanged != null) onChanged.run(); }

    @Override public void onAudioFocusChange(int focus) {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post(() -> onAudioFocusChange(focus)); return; }
        if (focus < 0 && message != null && wantsPlaying) pause();
    }
}
