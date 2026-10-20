package org.telegram.messenger;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayList;

/**
 * tgui: business-boundary stub. Upstream MediaController records Opus voice messages, plays
 * music and messages, and talks to the chat backend. ChatActivityEnterView only needs the
 * voice-message recording / preview part, so this class keeps that API and forwards it to a
 * {@link Recorder} that the host can replace ({@link #setRecorder}).
 *
 * <p>The input view reacts to the same NotificationCenter events as upstream; recorders report
 * through the {@code notify*} helpers, which post them with the expected arguments.
 *
 * <p>{@link DefaultRecorder} is a working implementation on top of {@link MediaRecorder}
 * (AAC in .m4a, amplitude from {@link MediaRecorder#getMaxAmplitude()}) with {@link MediaPlayer}
 * preview playback. Hosts that need Opus/OGG (Telegram's format) plug in their own encoder.
 */
public class MediaController {

    /** Reasons for {@link #notifyRecordStopped}, as in upstream NotificationCenter.recordStopped. */
    public static final int STOP_REASON_CANCEL = 0;
    public static final int STOP_REASON_SENT = 1;
    public static final int STOP_REASON_PREVIEW = 2;
    public static final int STOP_REASON_TOO_LONG = 4;
    public static final int STOP_REASON_VIDEO_SENT = 5;
    public static final int STOP_REASON_ERROR = 6;

    /** Result of a voice recording kept for preview ({@code NotificationCenter.audioDidSent} args[1]). */
    public static class RecordedAudio {
        public final String path;
        public final long durationMs;
        public final byte[] waveform;

        public RecordedAudio(String path, long durationMs, byte[] waveform) {
            this.path = path;
            this.durationMs = durationMs;
            this.waveform = waveform;
        }
    }

    /**
     * Host side of voice recording. {@code send} values follow upstream stopRecording():
     * 0 = cancel, 1 = send, 2 = stop and keep for preview, 3 = send (schedule).
     */
    public interface Recorder {
        void startRecording(int guid);

        void stopRecording(int guid, int send, boolean notify, int scheduleDate, boolean once);

        /** Pause (keep for preview) / resume the current recording. */
        default void toggleRecordingPause(int guid, boolean once) {
            stopRecording(guid, 2, true, 0, once);
        }

        /** Delete the file of a recording that was discarded from the preview. */
        default void cleanRecording(boolean delete) {
        }

        boolean isRecording();

        void playRecorded(RecordedAudio audio, float fromProgress);

        void pauseRecorded();

        void seekRecorded(float progress);

        boolean isPlayingRecorded();

        float getRecordedProgress();
    }

    private static volatile MediaController instance;

    public static MediaController getInstance() {
        MediaController local = instance;
        if (local == null) {
            synchronized (MediaController.class) {
                local = instance;
                if (local == null) {
                    instance = local = new MediaController();
                }
            }
        }
        return local;
    }

    private Recorder recorder = new DefaultRecorder();

    public void setRecorder(Recorder recorder) {
        this.recorder = recorder != null ? recorder : new DefaultRecorder();
    }

    public Recorder getRecorder() {
        return recorder;
    }

    // ---- API used by ChatActivityEnterView (upstream names) ----

    public void startRecording(int guid) {
        recorder.startRecording(guid);
    }

    public void stopRecording(int send, boolean notify, int scheduleDate, boolean once) {
        recorder.stopRecording(currentGuid, send, notify, scheduleDate, once);
    }

    public void toggleRecordingPause(boolean once) {
        recorder.toggleRecordingPause(currentGuid, once);
    }

    public void cleanRecording(boolean delete) {
        recorder.cleanRecording(delete);
    }

    public boolean isRecordingAudio() {
        return recorder.isRecording();
    }

    public boolean isPlayingRecorded() {
        return recorder.isPlayingRecorded();
    }

    public void playRecorded(RecordedAudio audio, float fromProgress) {
        recorder.playRecorded(audio, fromProgress);
    }

    public void pauseRecorded() {
        recorder.pauseRecorded();
    }

    public void seekRecorded(float progress) {
        recorder.seekRecorded(progress);
    }

    public float getRecordedProgress() {
        return recorder.getRecordedProgress();
    }

    // ---- helpers for recorders: post the events the input view listens to ----

    private int currentGuid;

    public void notifyRecordStarted(int guid, boolean audio) {
        currentGuid = guid;
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.recordStarted, guid, audio));
    }

    /** @param amplitude RMS-like level; upstream values are roughly 0..1800 for normal speech */
    public void notifyRecordProgress(int guid, double amplitude) {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.recordProgressChanged, guid, amplitude));
    }

    public void notifyRecordStopped(int guid, int reason) {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.recordStopped, guid, reason));
    }

    public void notifyRecordStartError(int guid) {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.recordStartError, guid));
    }

    public void notifyRecordTooShort(int guid) {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.audioRecordTooShort, guid, false, 0));
    }

    /** The recording was sent directly (released without locking): the input view hands it to its delegate. */
    public void notifyRecordedAudioSent(int guid, RecordedAudio audio, boolean notify, int scheduleDate, boolean once) {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.voiceRecordedForSend, guid, audio, notify, scheduleDate, once));
    }

    /** The recording was stopped for preview: the input view switches to the recorded-audio panel. */
    public void notifyRecordedAudio(int guid, RecordedAudio audio) {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.audioDidSent, guid, audio, audio.path));
    }

    public void notifyRecordedPlaybackProgress() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, 0, 0f));
    }

    public void notifyRecordedPlaybackReset() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.messagePlayingDidReset, 0, 0));
    }

    /**
     * Packs up to 100 levels (0..31) into upstream's 5-bit waveform format, as the native
     * {@code set_bits} helper does, so SeekBarWaveform draws them unchanged.
     */
    public static byte[] packWaveform(int[] levels) {
        int count = Math.min(levels.length, 100);
        byte[] bytes = new byte[count * 5 / 8 + 4];
        for (int i = 0; i < count; i++) {
            int value = Math.max(0, Math.min(31, levels[i])) & 31;
            int bitOffset = i * 5;
            int byteNum = bitOffset / 8;
            int shift = bitOffset % 8;
            int v = value << shift;
            for (int k = 0; k < 4 && byteNum + k < bytes.length; k++) {
                bytes[byteNum + k] |= (byte) ((v >> (8 * k)) & 0xff);
            }
        }
        byte[] result = new byte[count * 5 / 8];
        System.arraycopy(bytes, 0, result, 0, result.length);
        return result;
    }

    /** Resamples a level history into 100 waveform buckets (0..31). */
    public static byte[] waveformFromLevels(ArrayList<Integer> history) {
        int[] levels = new int[100];
        if (history.isEmpty()) {
            return packWaveform(levels);
        }
        int max = 1;
        for (int v : history) {
            max = Math.max(max, v);
        }
        for (int i = 0; i < 100; i++) {
            int from = i * history.size() / 100;
            int to = Math.max(from + 1, (i + 1) * history.size() / 100);
            int peak = 0;
            for (int j = from; j < to && j < history.size(); j++) {
                peak = Math.max(peak, history.get(j));
            }
            levels[i] = Math.round(31f * peak / max);
        }
        return packWaveform(levels);
    }

    /** Working default: AAC voice notes with MediaRecorder, preview with MediaPlayer. */
    public static class DefaultRecorder implements Recorder {

        private static final long MIN_DURATION_MS = 700;

        private MediaRecorder mediaRecorder;
        private File file;
        private int guid;
        private long startTime;
        private long recordedBefore;
        private final ArrayList<Integer> levels = new ArrayList<>();
        private Runnable amplitudeRunnable;

        private MediaPlayer player;
        private RecordedAudio playingAudio;
        private Runnable progressRunnable;

        @Override
        public void startRecording(int guid) {
            this.guid = guid;
            stopPlayer();
            try {
                File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "voice");
                dir.mkdirs();
                file = new File(dir, "voice_" + System.currentTimeMillis() + ".m4a");
                mediaRecorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(ApplicationLoader.applicationContext) : new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioSamplingRate(48000);
                mediaRecorder.setAudioEncodingBitRate(64000);
                mediaRecorder.setOutputFile(file.getAbsolutePath());
                mediaRecorder.prepare();
                mediaRecorder.start();
            } catch (Exception e) {
                FileLog.e(e);
                release();
                getInstance().notifyRecordStartError(guid);
                return;
            }
            levels.clear();
            recordedBefore = 0;
            startTime = SystemClock.elapsedRealtime();
            getInstance().notifyRecordStarted(guid, true);
            amplitudeRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mediaRecorder == null) {
                        return;
                    }
                    int max = 0;
                    try {
                        max = mediaRecorder.getMaxAmplitude();
                    } catch (Exception ignore) {
                    }
                    levels.add(max);
                    // getMaxAmplitude() is a peak value (0..32767); scale it towards upstream's RMS range
                    getInstance().notifyRecordProgress(DefaultRecorder.this.guid, max / 8.0);
                    AndroidUtilities.runOnUIThread(this, 50);
                }
            };
            AndroidUtilities.runOnUIThread(amplitudeRunnable, 50);
        }

        @Override
        public void stopRecording(int guid, int send, boolean notify, int scheduleDate, boolean once) {
            if (mediaRecorder == null) {
                return;
            }
            long duration = recordedBefore + SystemClock.elapsedRealtime() - startTime;
            boolean ok = true;
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                ok = false;
            }
            release();
            if (send == 0) {
                deleteFile();
                getInstance().notifyRecordStopped(guid, STOP_REASON_CANCEL);
                return;
            }
            if (!ok || duration < MIN_DURATION_MS) {
                deleteFile();
                getInstance().notifyRecordTooShort(guid);
                return;
            }
            RecordedAudio audio = new RecordedAudio(file.getAbsolutePath(), duration, waveformFromLevels(levels));
            if (send == 2) {
                getInstance().notifyRecordStopped(guid, STOP_REASON_PREVIEW);
                getInstance().notifyRecordedAudio(guid, audio);
            } else {
                getInstance().notifyRecordStopped(guid, STOP_REASON_SENT);
                getInstance().notifyRecordedAudioSent(guid, audio, notify, scheduleDate, once);
            }
        }

        private void release() {
            if (amplitudeRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(amplitudeRunnable);
                amplitudeRunnable = null;
            }
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.release();
                } catch (Exception ignore) {
                }
                mediaRecorder = null;
            }
        }

        private void deleteFile() {
            if (file != null) {
                file.delete();
                file = null;
            }
        }

        @Override
        public void cleanRecording(boolean delete) {
            stopPlayer();
            if (delete) {
                deleteFile();
            }
        }

        @Override
        public boolean isRecording() {
            return mediaRecorder != null;
        }

        @Override
        public void playRecorded(RecordedAudio audio, float fromProgress) {
            try {
                if (player == null || playingAudio != audio) {
                    stopPlayer();
                    player = new MediaPlayer();
                    player.setDataSource(audio.path);
                    player.prepare();
                    player.setOnCompletionListener(mp -> {
                        stopPlayer();
                        getInstance().notifyRecordedPlaybackReset();
                    });
                    playingAudio = audio;
                }
                if (fromProgress > 0 && fromProgress < 1) {
                    player.seekTo((int) (player.getDuration() * fromProgress));
                }
                player.start();
            } catch (Exception e) {
                FileLog.e(e);
                stopPlayer();
                getInstance().notifyRecordedPlaybackReset();
                return;
            }
            progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (player == null) {
                        return;
                    }
                    getInstance().notifyRecordedPlaybackProgress();
                    AndroidUtilities.runOnUIThread(this, 40);
                }
            };
            AndroidUtilities.runOnUIThread(progressRunnable);
        }

        @Override
        public void pauseRecorded() {
            if (player != null) {
                player.pause();
            }
            if (progressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(progressRunnable);
                progressRunnable = null;
            }
        }

        @Override
        public void seekRecorded(float progress) {
            if (player != null) {
                player.seekTo((int) (player.getDuration() * progress));
            }
        }

        @Override
        public boolean isPlayingRecorded() {
            return player != null && player.isPlaying();
        }

        @Override
        public float getRecordedProgress() {
            if (player == null || player.getDuration() <= 0) {
                return 0;
            }
            return player.getCurrentPosition() / (float) player.getDuration();
        }

        private void stopPlayer() {
            if (progressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(progressRunnable);
                progressRunnable = null;
            }
            if (player != null) {
                try {
                    player.release();
                } catch (Exception ignore) {
                }
                player = null;
            }
            playingAudio = null;
        }
    }
}
