/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

// Music branch extracted from release-11.4.2-5469; host-owned models replace account/playback business code.
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.SeekBar;

/** Local music UI extracted from ChatMessageCell; the host owns playback and downloads. */
public class MusicBubbleCell extends TextBubbleCell implements SeekBar.SeekBarDelegate {
    private final SeekBar seekBar;
    private final RadialProgress2 radialProgress;
    private final RectF buttonRect = new RectF(), seekRect = new RectF(), menuRect = new RectF();
    private final Drawable menuDrawable;
    private StaticLayout songLayout, performerLayout, durationLayout, captionLayout;
    private AnimatedEmojiSpan.EmojiGroupedSpans captionSpans;
    private int namesOffset, buttonX, buttonY, seekX, seekY, timeAudioX, songX, performerX, captionHeight;
    private int buttonState = -1, lastSeconds = -1, lastDuration = -1;
    private float seekTransition;
    private boolean buttonPressed, menuPressed, dragClaimed;
    private boolean radialPressed;
    private float downX, downY;
    private Runnable longPress;

    public MusicBubbleCell(Context context) { this(context, null); }
    public MusicBubbleCell(Context context, Theme.ResourcesProvider provider) {
        super(context, provider);
        seekBar = new SeekBar(this); seekBar.setDelegate(this);
        radialProgress = new RadialProgress2(this, provider);
        menuDrawable = context.getResources().getDrawable(R.drawable.msg_actions).mutate();
    }
    @Override public void setMessage(TextBubbleMessage value, boolean pinnedTop, boolean pinnedBottom) {
        boolean changed = this.message != value;
        cancelInteractions();
        // Titles/captions may be replaced on the same host-owned message.
        layoutForWidth = -1;
        super.setMessage(value, pinnedTop, pinnedBottom);
        requestLayout();
        if (value == null) {
            songLayout = performerLayout = durationLayout = captionLayout = null;
            buttonRect.setEmpty(); seekRect.setEmpty(); menuRect.setEmpty();
            AnimatedEmojiSpan.release(this, captionSpans); captionSpans = null;
        } else {
            if (changed) { buttonState = -1; lastSeconds = lastDuration = -1; seekTransition = activeSession() ? 1f : 0f; }
            updatePlayingMessageProgress();
        }
    }
    private boolean activeSession() { return message != null && (message.playing || message.audioPaused); }
    @Override protected void buildLayout(int width) {
        layoutForWidth = width; layoutForText = message.text;
        setReactionsLayout();
        Theme.chat_audioTitlePaint.setTextSize(AndroidUtilities.dp(16));
        Theme.chat_audioPerformerPaint.setTextSize(AndroidUtilities.dp(15));
        Theme.chat_audioTimePaint.setTextSize(AndroidUtilities.dp(12));
        int side = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : width;
        int maxWidth = Math.max(AndroidUtilities.dp(80), Math.min(side - AndroidUtilities.dp(incomingAvatarInset() != 0 ? 102 : 50), AndroidUtilities.dp(270)));
        backgroundWidth = maxWidth;
        int musicWidth = Math.max(AndroidUtilities.dp(10), backgroundWidth - AndroidUtilities.dp(92));
        CharSequence title = TextUtils.isEmpty(message.musicTitle) ? getContext().getString(R.string.AudioUnknownTitle) : AndroidUtilities.replaceNewLines(message.musicTitle);
        CharSequence performer = TextUtils.isEmpty(message.musicPerformer) ? getContext().getString(R.string.AudioUnknownArtist) : AndroidUtilities.replaceNewLines(message.musicPerformer);
        title = Emoji.replaceEmoji(title, Theme.chat_audioTitlePaint.getFontMetricsInt(), false);
        performer = Emoji.replaceEmoji(performer, Theme.chat_audioPerformerPaint.getFontMetricsInt(), false);
        title = TextUtils.ellipsize(title, Theme.chat_audioTitlePaint, Math.max(1, musicWidth - AndroidUtilities.dp(12)), TextUtils.TruncateAt.END);
        performer = TextUtils.ellipsize(performer, Theme.chat_audioPerformerPaint, musicWidth, TextUtils.TruncateAt.END);
        songLayout = new StaticLayout(title, Theme.chat_audioTitlePaint, musicWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
        performerLayout = new StaticLayout(performer, Theme.chat_audioPerformerPaint, musicWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
        songX = -(int) Math.ceil(songLayout.getLineLeft(0)); performerX = -(int) Math.ceil(performerLayout.getLineLeft(0));
        measureTime();
        int replyOffset = buildReplyLayout(backgroundWidth - AndroidUtilities.dp(31) - AndroidUtilities.dp(message.isOut ? 30 : 20), false);
        namesOffset = authorHeight + forwardOffset() + replyOffset;
        totalHeight = AndroidUtilities.dp(82) + namesOffset;
        String widestTime = AndroidUtilities.formatShortDuration(message.duration, message.duration);
        int durationWidth = (int) Math.ceil(Theme.chat_audioTimePaint.measureText(widestTime));
        if (AndroidUtilities.dp(76) + durationWidth >= backgroundWidth - timeWidth - AndroidUtilities.dp(12)) totalHeight += AndroidUtilities.dp(14);
        if (pinnedTop) namesOffset -= AndroidUtilities.dp(1);
        captionLayout = null; captionHeight = 0;
        if (!TextUtils.isEmpty(message.text)) {
            CharSequence caption = AnimatedEmojiSpan.cloneSpans(message.text);
            AnimatedEmojiSpan.applyFontMetricsForString(caption, Theme.chat_msgTextPaint);
            caption = Emoji.replaceEmoji(caption, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
            int captionWidth = Math.max(1, backgroundWidth - AndroidUtilities.dp(45));
            captionLayout = new StaticLayout(caption, Theme.chat_msgTextPaint, captionWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            captionHeight = captionLayout.getHeight();
            int last = captionLayout.getLineCount() - 1;
            if (last >= 0 && reactionsLayoutInBubble.isEmpty
                    && backgroundWidth - AndroidUtilities.dp(31 + 8) - captionLayout.getLineWidth(last)
                    < timeWidth + AndroidUtilities.dp(message.isOut ? 20 : 0)) captionHeight += AndroidUtilities.dp(14);
            totalHeight += captionHeight + AndroidUtilities.dp(9);
            captionSpans = AnimatedEmojiSpan.update(0, this, captionSpans, captionLayout);
        } else { AnimatedEmojiSpan.release(this, captionSpans); captionSpans = null; }
        reactionsLayoutInBubble.measure(maxWidth - AndroidUtilities.dp(24), Gravity.LEFT);
        if (!reactionsLayoutInBubble.isEmpty) {
            reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(12);
            if (reactionsLayoutInBubble.width > backgroundWidth) backgroundWidth = reactionsLayoutInBubble.width;
            if (reactionsLayoutInBubble.lastLineX + timeWidth + AndroidUtilities.dp(24) > backgroundWidth) {
                reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(12);
                reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(12);
            }
            if (captionLayout != null) reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(14);
            totalHeight += reactionsLayoutInBubble.totalHeight;
        }
        seekBar.setSize(Math.max(AndroidUtilities.dp(25), backgroundWidth - AndroidUtilities.dp(65)), AndroidUtilities.dp(30));
        lastSeconds = lastDuration = -1;
        updatePlayingMessageProgress();
    }
    private void updatePositions() {
        int width = getMeasuredWidth();
        if (message.isOut) {
            buttonX = width - backgroundWidth + AndroidUtilities.dp(14);
            seekX = width - backgroundWidth + AndroidUtilities.dp(56);
            timeAudioX = width - backgroundWidth + AndroidUtilities.dp(67);
        } else {
            buttonX = AndroidUtilities.dp(23) + incomingAvatarInset();
            seekX = AndroidUtilities.dp(65) + incomingAvatarInset();
            timeAudioX = AndroidUtilities.dp(76) + incomingAvatarInset();
        }
        buttonY = AndroidUtilities.dp(13) + namesOffset; seekY = AndroidUtilities.dp(29) + namesOffset;
        buttonRect.set(buttonX, buttonY, buttonX + AndroidUtilities.dp(44), buttonY + AndroidUtilities.dp(44));
        seekRect.set(seekX, seekY, seekX + seekBar.getWidth() + AndroidUtilities.dp(24), seekY + AndroidUtilities.dp(30));
        int menuX = buttonX + backgroundWidth - AndroidUtilities.dp(48), menuY = buttonY - AndroidUtilities.dp(2);
        menuRect.set(menuX - AndroidUtilities.dp(20), menuY - AndroidUtilities.dp(4), menuX + AndroidUtilities.dp(20), menuY + AndroidUtilities.dp(30));
        radialProgress.setProgressRect((int) buttonRect.left, (int) buttonRect.top, (int) buttonRect.right, (int) buttonRect.bottom);
    }
    public void updatePlayingMessageProgress() {
        if (message == null) return;
        if (!seekBar.isDragging()) seekBar.setProgress(clamp(message.audioProgress));
        int seconds = activeSession() ? Math.max(0, Math.min(message.duration, message.audioProgressSec)) : 0;
        if (!seekBar.isDragging() && (lastSeconds != seconds || lastDuration != message.duration || durationLayout == null)) {
            lastSeconds = seconds; lastDuration = message.duration;
            String text = AndroidUtilities.formatShortDuration(seconds, message.duration);
            int w = Math.max(1, (int) Math.ceil(Theme.chat_audioTimePaint.measureText(text)));
            durationLayout = new StaticLayout(text, Theme.chat_audioTimePaint, w, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }
        int state = message.mediaState == TextBubbleMessage.MEDIA_DOWNLOAD ? 2 : message.mediaState == TextBubbleMessage.MEDIA_LOADING ? 4 : message.playing ? 1 : 0;
        radialProgress.setProgress(state == 4 ? clamp(message.mediaProgress) : 0f, state == 4 && isAttachedToWindow());
        radialProgress.setColorKeys(message.isOut ? Theme.key_chat_outLoader : Theme.key_chat_inLoader,
                message.isOut ? Theme.key_chat_outLoaderSelected : Theme.key_chat_inLoaderSelected,
                message.isOut ? Theme.key_chat_outMediaIcon : Theme.key_chat_inMediaIcon,
                message.isOut ? Theme.key_chat_outMediaIconSelected : Theme.key_chat_inMediaIconSelected);
        if (state != buttonState) {
            boolean animate = buttonState != -1 && isAttachedToWindow(); buttonState = state;
            radialProgress.setIcon(state == 1 ? MediaActionDrawable.ICON_PAUSE : state == 2 ? MediaActionDrawable.ICON_DOWNLOAD : state == 4 ? MediaActionDrawable.ICON_CANCEL : MediaActionDrawable.ICON_PLAY, false, animate);
        }
        invalidate();
    }
    private static float clamp(float value) { return Float.isNaN(value) ? 0f : Math.max(0f, Math.min(1f, value)); }
    @Override protected void drawContent(Canvas canvas) {
        if (songLayout == null || durationLayout == null) return;
        int backgroundLeft = drawBubbleBackground(canvas);
        drawAuthor(canvas); drawForward(canvas, false); drawReply(canvas, false);
        updatePositions();
        boolean out = message.isOut, selected = drawSelectionBackground;
        Theme.chat_audioTitlePaint.setColor(getThemedColor(out ? Theme.key_chat_outAudioTitleText : Theme.key_chat_inAudioTitleText));
        Theme.chat_audioPerformerPaint.setColor(getThemedColor(out ? (selected ? Theme.key_chat_outAudioPerformerSelectedText : Theme.key_chat_outAudioPerformerText) : (selected ? Theme.key_chat_inAudioPerformerSelectedText : Theme.key_chat_inAudioPerformerText)));
        Theme.chat_audioTimePaint.setColor(getThemedColor(out ? (selected ? Theme.key_chat_outAudioDurationSelectedText : Theme.key_chat_outAudioDurationText) : (selected ? Theme.key_chat_inAudioDurationSelectedText : Theme.key_chat_inAudioDurationText)));
        int progressColor = getThemedColor(out ? (selected || buttonPressed ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress) : (selected || buttonPressed ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
        radialProgress.setProgressColor(progressColor);
        boolean pressed = buttonPressed || selected;
        if (radialPressed != pressed) { radialPressed = pressed; radialProgress.setPressed(pressed, false); }
        radialProgress.setBackgroundDrawable(currentBackgroundDrawable); radialProgress.draw(canvas);
        int bg = getThemedColor(out ? Theme.key_chat_outAudioSeekbar : Theme.key_chat_inAudioSeekbar);
        int cache = getThemedColor(out ? Theme.key_chat_outAudioCacheSeekbar : Theme.key_chat_inAudioCacheSeekbar);
        int fill = getThemedColor(out ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill);
        int selectedColor = getThemedColor(out ? Theme.key_chat_outAudioSeekbarSelected : Theme.key_chat_inAudioSeekbarSelected);
        seekBar.setColors(bg, cache, fill, fill, selectedColor); seekBar.setSelected(selected);
        canvas.save(); canvas.translate(timeAudioX + songX, AndroidUtilities.dp(13) + namesOffset); songLayout.draw(canvas); canvas.restore();
        float step = 16f / 100f;
        float target = activeSession() ? 1f : 0f;
        if (seekTransition != target) { seekTransition = target > seekTransition ? Math.min(target, seekTransition + step) : Math.max(target, seekTransition - step); invalidate(); }
        if (seekTransition > 0) { canvas.save(); canvas.translate(seekX, seekY); seekBar.setAlpha(seekTransition); seekBar.draw(canvas); seekBar.setAlpha(1f); canvas.restore(); }
        if (seekTransition < 1) {
            float x = timeAudioX + performerX, y = AndroidUtilities.dp(35) + namesOffset;
            canvas.saveLayerAlpha(x, y, x + performerLayout.getWidth(), y + performerLayout.getHeight(), Math.round(255 * (1f - seekTransition)));
            float s = .7f + .3f * (1f - seekTransition); canvas.scale(s, s, x, y + performerLayout.getHeight() / 2f);
            canvas.translate(x, y); performerLayout.draw(canvas); canvas.restore();
        }
        canvas.save(); canvas.translate(timeAudioX, AndroidUtilities.dp(57) + namesOffset); durationLayout.draw(canvas); canvas.restore();
        Theme.setDrawableColor(menuDrawable, getThemedColor(out ? (selected ? Theme.key_chat_outMenuSelected : Theme.key_chat_outMenu) : (selected ? Theme.key_chat_inMenuSelected : Theme.key_chat_inMenu)));
        int menuX = buttonX + backgroundWidth - AndroidUtilities.dp(48), menuY = buttonY - AndroidUtilities.dp(2);
        menuDrawable.setBounds(menuX, menuY, menuX + menuDrawable.getIntrinsicWidth(), menuY + menuDrawable.getIntrinsicHeight()); menuDrawable.draw(canvas);
        if (captionLayout != null) {
            Theme.chat_msgTextPaint.setColor(getThemedColor(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
            Theme.chat_msgTextPaint.linkColor = getThemedColor(out ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
            int captionX = backgroundLeft + AndroidUtilities.dp(out || pinnedBottom ? 11 : 17);
            int captionY = getHeight() - captionHeight - AndroidUtilities.dp(pinnedTop ? 9 : 10) - reactionsLayoutInBubble.totalHeight;
            canvas.save(); canvas.translate(captionX, captionY); captionLayout.draw(canvas); canvas.restore();
        }
        drawReactionsInBubble(canvas, backgroundLeft, captionLayout != null ? -AndroidUtilities.dp(14) : 0); drawTimeAndStatus(canvas);
    }
    public void getSeekBarRect(RectF out) { out.set(seekRect); }
    public void getButtonRect(RectF out) { out.set(buttonRect); }
    public void getMenuRect(RectF out) { out.set(menuRect); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (message == null || isCheckBoxVisible()) { cancelInteractions(); return super.onTouchEvent(event); }
        if (checkForwardTouchEvent(event) || checkReplyTouchEvent(event) || checkReactionsTouchEvent(event)) return true;
        int action = event.getActionMasked(); float x = event.getX(), y = event.getY();
        if (action == MotionEvent.ACTION_DOWN && menuRect.contains(x, y) && mediaDelegate != null) {
            cancelInteractions(); menuPressed = true; downX = x; downY = y; startLongPress(); requestParentDisallow(true); return true;
        }
        if (menuPressed) {
            if (action == MotionEvent.ACTION_MOVE && moved(x, y)) { cancelInteractions(); requestParentDisallow(false); return false; }
            if (action == MotionEvent.ACTION_CANCEL) { cancelInteractions(); requestParentDisallow(false); return true; }
            if (action == MotionEvent.ACTION_UP) { cancelInteractions(); requestParentDisallow(false); if (mediaDelegate != null) mediaDelegate.onMenuPressed(this, message, x, y); return true; }
            return true;
        }
        if ((activeSession() || dragClaimed) && seekBar.onTouch(action, x - seekX, y - seekY)) {
            if (action == MotionEvent.ACTION_DOWN) { dragClaimed = true; requestParentDisallow(true); }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { dragClaimed = false; requestParentDisallow(false); updatePlayingMessageProgress(); }
            invalidate(); return true;
        }
        boolean area = buttonState == 4 ? x >= buttonX && x <= buttonX + AndroidUtilities.dp(36) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(36)
                : x >= buttonX - AndroidUtilities.dp(12) && x <= buttonX - AndroidUtilities.dp(12) + backgroundWidth && y >= namesOffset && y <= namesOffset + AndroidUtilities.dp(82);
        if (action == MotionEvent.ACTION_DOWN && area && mediaDelegate != null) {
            cancelInteractions(); buttonPressed = true; downX = x; downY = y; startLongPress(); requestParentDisallow(true); invalidate(); return true;
        }
        if (buttonPressed) {
            if (action == MotionEvent.ACTION_MOVE && (!area || moved(x, y))) { cancelInteractions(); requestParentDisallow(false); return false; }
            if (action == MotionEvent.ACTION_CANCEL) { cancelInteractions(); requestParentDisallow(false); return true; }
            if (action == MotionEvent.ACTION_UP) { cancelInteractions(); requestParentDisallow(false); playSoundEffect(SoundEffectConstants.CLICK); if (mediaDelegate != null) mediaDelegate.onMediaPressed(this, message); return true; }
            return true;
        }
        return super.onTouchEvent(event);
    }
    private boolean moved(float x, float y) { float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop(); return Math.abs(x - downX) > slop || Math.abs(y - downY) > slop; }
    private void requestParentDisallow(boolean value) { if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(value); }
    private void startLongPress() { longPress = () -> { longPress = null; cancelInteractions(); requestParentDisallow(false); onInteractiveLongPress(false); }; postDelayed(longPress, ViewConfiguration.getLongPressTimeout()); }
    private void cancelInteractions() {
        if (longPress != null) { removeCallbacks(longPress); longPress = null; }
        if (dragClaimed || seekBar.isDragging()) seekBar.onTouch(MotionEvent.ACTION_CANCEL, 0, 0);
        if (buttonPressed || menuPressed || dragClaimed) requestParentDisallow(false);
        buttonPressed = menuPressed = dragClaimed = false; invalidate();
    }
    @Override public void onSeekBarDrag(float progress) {
        if (message == null) return;
        message.audioProgress = clamp(progress); message.audioProgressSec = (int) (message.duration * message.audioProgress);
        if (mediaDelegate != null) mediaDelegate.onSeek(this, message, message.audioProgress);
        updatePlayingMessageProgress();
    }
    @Override public void onSeekBarContinuousDrag(float progress) {
        if (message == null) return;
        lastSeconds = -1;
        int seconds = (int) (message.duration * clamp(progress));
        String text = AndroidUtilities.formatShortDuration(seconds, message.duration);
        int width = Math.max(1, (int) Math.ceil(Theme.chat_audioTimePaint.measureText(text)));
        durationLayout = new StaticLayout(text, Theme.chat_audioTimePaint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        invalidate();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); radialProgress.onAttachedToWindow(); if (captionLayout != null) captionSpans = AnimatedEmojiSpan.update(0, this, captionSpans, captionLayout); }
    @Override protected void onDetachedFromWindow() { cancelInteractions(); AnimatedEmojiSpan.release(this, captionSpans); captionSpans = null; radialProgress.onDetachedFromWindow(); super.onDetachedFromWindow(); }
}
