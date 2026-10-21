package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SeekBarWaveform;

/**
 * tgui: voice message bubble ({@link TextBubbleMessage#TYPE_VOICE}). Transplanted from upstream
 * ChatMessageCell for DOCUMENT_ATTACH_TYPE_AUDIO without avatars, names, captions or the transcribe
 * button:
 * <ul>
 * <li>setMessageContent (TYPE_VOICE branch) + createDocumentLayout: bubble width / 70dp height</li>
 * <li>onLayout: buttonX / seekBarX / timeAudioX / seekBarY, updateSeekBarWaveformWidth</li>
 * <li>drawContent (DOCUMENT_ATTACH_TYPE_AUDIO branch): duration / progress colors, radialProgress,
 *     seekBarWaveform, durationLayout and the unread dot</li>
 * <li>updatePlayingMessageProgress, updateWaveform, getIconForCurrentState, checkAudioMotionEvent</li>
 * </ul>
 * The host owns playback: it gets {@link MediaDelegate#onMediaPressed} / {@link MediaDelegate#onSeek},
 * updates {@link TextBubbleMessage#playing} / {@link TextBubbleMessage#audioProgress} /
 * {@link TextBubbleMessage#audioProgressSec} and calls {@link #updatePlayingMessageProgress()}
 * (or rebinds with {@link #setMessage}).
 */
public class VoiceBubbleCell extends TextBubbleCell implements SeekBar.SeekBarDelegate {

    private final RadialProgress2 radialProgress;
    private final SeekBarWaveform seekBarWaveform;
    private StaticLayout durationLayout;
    private int timeWidthAudio;
    private int lastTime = -1;
    private int buttonState = -1;
    private int buttonPressed;
    private int buttonX, buttonY, seekBarX, seekBarY, timeAudioX;
    private int namesOffset;

    public VoiceBubbleCell(Context context) {
        this(context, null);
    }

    public VoiceBubbleCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        radialProgress = new RadialProgress2(this, resourcesProvider);
        seekBarWaveform = new SeekBarWaveform(context);
        seekBarWaveform.setDelegate(this);
        seekBarWaveform.setParentView(this);
    }

    @Override
    public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        boolean changed = this.message != message;
        super.setMessage(message, pinnedTop, pinnedBottom);
        if (message == null) {
            return;
        }
        if (changed) {
            lastTime = -1;
            seekBarWaveform.setMessageObject(message.id);
            seekBarWaveform.setWaveform(message.waveform);
            seekBarWaveform.setProgress(message.audioProgress, false);
        }
        updateButtonState(!changed);
        updatePlayingMessageProgress();
    }

    @Override
    protected void buildLayout(int width) {
        layoutForWidth = width;
        layoutForText = message.text;
        layoutForReaction = message.reaction;
        // setMessageContent, TYPE_VOICE: backgroundWidth = maxWidth = min(parentWidth - 50dp, 270dp)
        int maxWidth = Math.min(width - AndroidUtilities.dp(50), AndroidUtilities.dp(270));
        if (AndroidUtilities.isTablet()) {
            maxWidth = Math.min(AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(50), AndroidUtilities.dp(270));
        }
        // createDocumentLayout, DOCUMENT_ATTACH_TYPE_AUDIO
        measureTime();
        int minSize = AndroidUtilities.dp(40 + 14 + 20 + 90 + 10) + timeWidth;
        String timeString = AndroidUtilities.formatLongDuration(message.duration);
        int w = (int) Math.ceil(Theme.chat_audioTimePaint.measureText(timeString));
        backgroundWidth = Math.min(maxWidth, minSize + w);
        // reply panel: namesOffset grows, the bubble widens up to maxWidth for the quote
        int replyOffset = buildReplyLayout(backgroundWidth - AndroidUtilities.dp(31) - AndroidUtilities.dp(message.isOut ? 30 : 20), false);
        if (replyNameLayout != null) {
            backgroundWidth = Math.min(maxWidth, Math.max(backgroundWidth, Math.max(replyNameWidth, replyTextWidth) + AndroidUtilities.dp(31)));
        }
        totalHeight = AndroidUtilities.dp(70) + replyOffset;
        namesOffset = replyOffset + (pinnedTop ? -AndroidUtilities.dp(1) : 0);
        // updateSeekBarWaveformWidth: backgroundWidth - 92dp (no transcribe button)
        seekBarWaveform.setSize(backgroundWidth - AndroidUtilities.dp(92), AndroidUtilities.dp(30));
        lastTime = -1;
        updatePlayingMessageProgress();
    }

    private void updatePositions() {
        int layoutWidth = getMeasuredWidth();
        if (message.isOut) {
            seekBarX = layoutWidth - backgroundWidth + AndroidUtilities.dp(57);
            buttonX = layoutWidth - backgroundWidth + AndroidUtilities.dp(14);
            timeAudioX = layoutWidth - backgroundWidth + AndroidUtilities.dp(67);
        } else {
            seekBarX = AndroidUtilities.dp(66);
            buttonX = AndroidUtilities.dp(23);
            timeAudioX = AndroidUtilities.dp(76);
        }
        seekBarY = AndroidUtilities.dp(13) + namesOffset;
        buttonY = AndroidUtilities.dp(13) + namesOffset;
        radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(44), buttonY + AndroidUtilities.dp(44));
    }

    /** Upstream updatePlayingMessageProgress (DOCUMENT_ATTACH_TYPE_AUDIO): waveform progress and the duration / elapsed time. */
    public void updatePlayingMessageProgress() {
        if (message == null) {
            return;
        }
        if (!seekBarWaveform.isDragging()) {
            seekBarWaveform.setProgress(message.audioProgress, true);
        }
        seekBarWaveform.setPlaying(message.playing);
        int duration = message.playing ? message.audioProgressSec : message.duration;
        if (lastTime != duration) {
            lastTime = duration;
            String timeString = AndroidUtilities.formatLongDuration(duration);
            timeWidthAudio = (int) Math.ceil(Theme.chat_audioTimePaint.measureText(timeString));
            durationLayout = new StaticLayout(timeString, Theme.chat_audioTimePaint, timeWidthAudio, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
        updateButtonState(true);
        invalidate();
    }

    /** Upstream updateButtonState for audio: 0 play, 1 pause, 2 download, 4 cancel (loading). */
    private void updateButtonState(boolean animated) {
        int state;
        if (message.mediaState == TextBubbleMessage.MEDIA_DOWNLOAD) {
            state = 2;
        } else if (message.mediaState == TextBubbleMessage.MEDIA_LOADING) {
            state = 4;
        } else {
            state = message.playing ? 1 : 0;
        }
        if (state == 4) {
            radialProgress.setProgress(message.mediaProgress, animated);
        } else {
            radialProgress.setProgress(0, false);
        }
        if (buttonState != state) {
            boolean animate = animated && buttonState != -1 && isAttachedToWindow();
            buttonState = state;
            radialProgress.setIcon(getIconForCurrentState(), false, animate);
        }
    }

    /** Upstream getIconForCurrentState, DOCUMENT_ATTACH_TYPE_AUDIO branch. */
    private int getIconForCurrentState() {
        if (message.isOut) {
            radialProgress.setColorKeys(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected);
        } else {
            radialProgress.setColorKeys(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
        }
        if (buttonState == 1) {
            return MediaActionDrawable.ICON_PAUSE;
        } else if (buttonState == 2) {
            return MediaActionDrawable.ICON_DOWNLOAD;
        } else if (buttonState == 4) {
            return MediaActionDrawable.ICON_CANCEL;
        }
        return MediaActionDrawable.ICON_PLAY;
    }

    @Override
    protected void drawContent(Canvas canvas) {
        if (durationLayout == null) {
            return;
        }
        drawBubbleBackground(canvas);
        drawReply(canvas, false);
        updatePositions();
        final boolean out = message.isOut;
        // onDraw: seekBarWaveform colors (setMessageObject draws with the theme's voice seekbar keys)
        if (out) {
            seekBarWaveform.setColors(getThemedColor(Theme.key_chat_outVoiceSeekbar), getThemedColor(Theme.key_chat_outVoiceSeekbarFill), getThemedColor(Theme.key_chat_outVoiceSeekbarSelected));
        } else {
            seekBarWaveform.setColors(getThemedColor(Theme.key_chat_inVoiceSeekbar), getThemedColor(Theme.key_chat_inVoiceSeekbarFill), getThemedColor(Theme.key_chat_inVoiceSeekbarSelected));
        }
        seekBarWaveform.setSelected(drawSelectionBackground);
        seekBarWaveform.setContentUnread(message.contentUnread);

        // drawContent, DOCUMENT_ATTACH_TYPE_AUDIO
        if (out) {
            Theme.chat_audioTimePaint.setColor(getThemedColor(drawSelectionBackground ? Theme.key_chat_outAudioDurationSelectedText : Theme.key_chat_outAudioDurationText));
            radialProgress.setProgressColor(getThemedColor(drawSelectionBackground || buttonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
        } else {
            Theme.chat_audioTimePaint.setColor(getThemedColor(drawSelectionBackground ? Theme.key_chat_inAudioDurationSelectedText : Theme.key_chat_inAudioDurationText));
            radialProgress.setProgressColor(getThemedColor(drawSelectionBackground || buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
        }
        // the icon colors are keys: re-read them so a theme switch recolors the button
        getIconForCurrentState();
        radialProgress.setBackgroundDrawable(currentBackgroundDrawable);
        radialProgress.setPressed(buttonPressed != 0, false);
        radialProgress.iconScale = 1f;
        radialProgress.draw(canvas);

        canvas.save();
        canvas.translate(seekBarX + AndroidUtilities.dp(13), seekBarY);
        seekBarWaveform.draw(canvas, this);
        canvas.restore();

        canvas.save();
        canvas.translate(timeAudioX, AndroidUtilities.dp(44) + namesOffset);
        durationLayout.draw(canvas);
        canvas.restore();

        if (message.contentUnread) {
            Theme.chat_docBackPaint.setColor(getThemedColor(out ? Theme.key_chat_outVoiceSeekbarFill : Theme.key_chat_inVoiceSeekbarFill));
            canvas.drawCircle(timeAudioX + timeWidthAudio + AndroidUtilities.dp(6), AndroidUtilities.dp(51) + namesOffset, AndroidUtilities.dp(3), Theme.chat_docBackPaint);
        }

        drawTimeAndStatus(canvas);
    }

    /** Upstream checkAudioMotionEvent, with the press area limited to the button so taps elsewhere open the menu. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (message == null || isCheckBoxVisible() || durationLayout == null) {
            return super.onTouchEvent(event);
        }
        int x = (int) event.getX();
        int y = (int) event.getY();
        boolean result = seekBarWaveform.onTouch(event.getAction(), event.getX() - seekBarX - AndroidUtilities.dp(13), event.getY() - seekBarY);
        if (result) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (!seekBarWaveform.isStartDraging() && event.getAction() == MotionEvent.ACTION_UP) {
                didPressButton();
            }
            invalidate();
            return true;
        }
        int side = AndroidUtilities.dp(44);
        boolean area = x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (area) {
                buttonPressed = 1;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
                return true;
            }
        } else if (buttonPressed != 0) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                buttonPressed = 0;
                playSoundEffect(SoundEffectConstants.CLICK);
                didPressButton();
                invalidate();
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                buttonPressed = 0;
                invalidate();
            } else if (event.getAction() == MotionEvent.ACTION_MOVE && !area) {
                buttonPressed = 0;
                invalidate();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void didPressButton() {
        if (mediaDelegate != null) {
            mediaDelegate.onMediaPressed(this, message);
        }
    }

    @Override
    public void onSeekBarDrag(float progress) {
        if (message == null) {
            return;
        }
        message.audioProgress = progress;
        if (mediaDelegate != null) {
            mediaDelegate.onSeek(this, message, progress);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        radialProgress.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        radialProgress.onDetachedFromWindow();
    }
}
