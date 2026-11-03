package org.telegram.uidemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Cells.RoundVideoBubbleCell;
import org.telegram.ui.Cells.TextBubbleMessage;

/** Demo host playback. The library cell itself owns no player, surface or codec. */
final class LocalRoundVideoPlayer implements AudioManager.OnAudioFocusChangeListener {
    private final FrameLayout root;
    private final ViewGroup list;
    private final AudioManager audio;
    private final RectF screenRect = new RectF();
    private final int[] rootLocation = new int[2];
    private final int[] listLocation = new int[2];
    private FrameLayout layer, circle;
    private TextureView texture;
    private Surface surface;
    private MediaPlayer player;
    private TextBubbleMessage message;
    private RoundVideoBubbleCell cell;
    private boolean prepared, firstFrame, requestedPlaying;
    private final Runnable tick = this::update;

    LocalRoundVideoPlayer(FrameLayout root, ViewGroup list) {
        this.root = root;
        this.list = list;
        audio = (AudioManager) root.getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    void toggle(TextBubbleMessage next) {
        if (message == next && player != null) {
            requestedPlaying = !requestedPlaying;
            if (prepared) {
                if (requestedPlaying) {
                    audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                    player.start();
                } else player.pause();
                message.playing = requestedPlaying;
                message.videoPaused = !requestedPlaying;
            }
            update();
            return;
        }
        stop();
        if (next.videoUri == null) return;
        message = next;
        requestedPlaying = true;
        layer = new FrameLayout(root.getContext()) {
            @Override protected void dispatchDraw(Canvas canvas) {
                root.getLocationOnScreen(rootLocation);
                list.getLocationOnScreen(listLocation);
                int save = canvas.save();
                canvas.clipRect(0, listLocation[1] - rootLocation[1] + list.getPaddingTop(), getWidth(),
                        listLocation[1] - rootLocation[1] + list.getHeight() - list.getPaddingBottom());
                super.dispatchDraw(canvas);
                canvas.restoreToCount(save);
            }
        };
        // Draw below the transparent list: the cell draws its progress ring over the video.
        root.addView(layer, Math.max(0, root.indexOfChild(list)), new FrameLayout.LayoutParams(-1, -1));
        circle = new FrameLayout(root.getContext());
        circle.setClipToOutline(true);
        circle.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        layer.addView(circle, new FrameLayout.LayoutParams(1, 1));
        texture = new TextureView(root.getContext());
        circle.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture value, int width, int height) { prepare(value); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture value, int width, int height) { transform(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture value) { stop(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture value) {
                if (!firstFrame && prepared && player != null) {
                    firstFrame = true;
                    if (cell != null) cell.setVideoVisible(false);
                }
            }
        });
        update();
    }

    private void prepare(SurfaceTexture value) {
        if (message == null || player != null) return;
        try {
            surface = new Surface(value);
            MediaPlayer created = player = new MediaPlayer();
            created.setAudioStreamType(AudioManager.STREAM_MUSIC);
            created.setSurface(surface);
            created.setDataSource(root.getContext(), message.videoUri);
            created.setOnPreparedListener(mp -> {
                if (player != mp || message == null) return;
                prepared = true;
                transform();
                if (message.audioProgressSec > 0) mp.seekTo(message.audioProgressSec * 1000);
                if (requestedPlaying) {
                    audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                    mp.start();
                }
                message.playing = requestedPlaying;
                message.videoPaused = !requestedPlaying;
                message.contentUnread = false;
                update();
            });
            created.setOnVideoSizeChangedListener((mp, width, height) -> transform());
            created.setOnCompletionListener(mp -> {
                if (player != mp) return;
                if (message != null) { message.audioProgress = 0; message.audioProgressSec = 0; }
                stop();
            });
            created.setOnErrorListener((mp, what, extra) -> {
                if (player != mp) return true;
                stop();
                Toast.makeText(root.getContext(), R.string.round_video_error, Toast.LENGTH_SHORT).show();
                return true;
            });
            created.prepareAsync();
        } catch (Exception error) {
            FileLog.e(error);
            stop();
            Toast.makeText(root.getContext(), R.string.round_video_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void transform() {
        if (player == null || texture == null || texture.getWidth() == 0 || texture.getHeight() == 0) return;
        int width = player.getVideoWidth(), height = player.getVideoHeight();
        if (width <= 0 || height <= 0) return;
        float scale = Math.max(texture.getWidth() / (float) width, texture.getHeight() / (float) height);
        Matrix matrix = new Matrix();
        matrix.setScale(width * scale / texture.getWidth(), height * scale / texture.getHeight(), texture.getWidth() / 2f, texture.getHeight() / 2f);
        texture.setTransform(matrix);
    }

    void seek(TextBubbleMessage target, float progress) {
        if (target == message && prepared && player != null) {
            player.seekTo((int) (player.getDuration() * Math.max(0, Math.min(1, progress))));
            update();
        }
    }

    private void update() {
        AndroidUtilities.cancelRunOnUIThread(tick);
        if (message == null || layer == null) return;
        RoundVideoBubbleCell found = null;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof RoundVideoBubbleCell && ((RoundVideoBubbleCell) child).getMessage() == message) {
                found = (RoundVideoBubbleCell) child;
                break;
            }
        }
        if (found == null || !found.getVideoRectOnScreen(screenRect)) { stop(); return; }
        if (cell != found) {
            if (cell != null) cell.setVideoVisible(true);
            cell = found;
        }
        if (prepared && player != null) {
            int elapsed = player.getCurrentPosition();
            message.audioProgressSec = elapsed / 1000;
            message.audioProgress = player.getDuration() == 0 ? 0 : elapsed / (float) player.getDuration();
        }
        cell.setVideoVisible(!firstFrame);
        cell.updatePlayingMessageProgress();
        root.getLocationOnScreen(rootLocation);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) circle.getLayoutParams();
        int side = Math.max(1, Math.round(screenRect.width()));
        if (params.width != side || params.height != side) {
            params.width = params.height = side;
            circle.setLayoutParams(params);
            circle.invalidateOutline();
        }
        circle.setTranslationX(screenRect.left - rootLocation[0]);
        circle.setTranslationY(screenRect.top - rootLocation[1]);
        transform();
        layer.invalidate();
        AndroidUtilities.runOnUIThread(tick, 33);
    }

    void stop() {
        AndroidUtilities.cancelRunOnUIThread(tick);
        TextBubbleMessage old = message;
        message = null;
        if (old != null) { old.playing = false; old.videoPaused = false; }
        if (cell != null) { cell.setVideoVisible(true); cell.updatePlayingMessageProgress(); cell = null; }
        MediaPlayer oldPlayer = player;
        player = null;
        if (oldPlayer != null) { try { oldPlayer.release(); } catch (RuntimeException ignored) { } }
        if (surface != null) { surface.release(); surface = null; }
        FrameLayout oldLayer = layer;
        layer = null;
        if (oldLayer != null) root.removeView(oldLayer);
        texture = null;
        circle = null;
        prepared = firstFrame = requestedPlaying = false;
        audio.abandonAudioFocus(this);
    }

    @Override public void onAudioFocusChange(int focus) {
        if (focus < 0 && message != null && requestedPlaying) toggle(message);
    }
}
