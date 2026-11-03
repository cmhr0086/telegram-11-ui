/*
 * Adapted from Telegram for Android release-11.4.2-5469.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.StaticLayoutEx;

/**
 * File message bubble ({@link TextBubbleMessage#TYPE_DOCUMENT}), adapted from the upstream
 * DOCUMENT_ATTACH_TYPE_DOCUMENT branches for both local thumbnails and files without previews:
 * <ul>
 * <li>setMessageContent (TYPE_FILE branch): backgroundWidth = getMaxMessageTextWidth, 56dp row
 *     growing with the title's lines and the time line, pinned top offsets</li>
 * <li>createDocumentLayout (document branch): 2-line middle-ellipsized docTitleLayout, infoLayout
 *     "size EXT"; createLoadingProgressLayout "loaded / total"</li>
 * <li>onLayout: 86dp thumbnail and 48dp centered progress, or the 44dp file button</li>
 * <li>drawContent (DOCUMENT_ATTACH_TYPE_DOCUMENT): name / info / progress colors, menu dots, title
 *     and info positions</li>
 * <li>getIconForCurrentState: ICON_FILE / ICON_DOWNLOAD / ICON_CANCEL in the bubble's loader colors</li>
 * </ul>
 * The host supplies {@link TextBubbleMessage#fileName}, {@link TextBubbleMessage#fileSize},
 * {@link TextBubbleMessage#mediaState} / {@link TextBubbleMessage#mediaProgress}, and gets
 * {@link MediaDelegate#onMediaPressed} when the round button is tapped.
 */
public class DocumentBubbleCell extends TextBubbleCell {

    private static Drawable menuDrawable;

    private final RadialProgress2 radialProgress;
    private StaticLayout docTitleLayout;
    private int docTitleOffsetX;
    private StaticLayout infoLayout;
    private int infoWidth;
    private int docTitleWidth;
    private StaticLayout loadingProgressLayout;
    private long loadingProgressHash = -1;
    private int namesOffset;
    private int buttonState = -2;
    private int buttonPressed;
    private int buttonX, buttonY;
    private boolean hasThumbnail, interceptHeld, radialPressed;
    private float downX, downY;
    private Runnable longPress;
    private final RectF thumbnailRect = new RectF(), buttonRect = new RectF();
    private final float[] thumbnailRadii = new float[8];
    private final PhotoBubbleCell.PhotoRenderer thumbnailRenderer = new PhotoBubbleCell.PhotoRenderer();
    private StaticLayout captionLayout;
    private float captionTextXOffset;
    private AnimatedEmojiSpan.EmojiGroupedSpans captionSpans;
    private int captionHeight;

    public DocumentBubbleCell(Context context) {
        this(context, null);
    }

    public DocumentBubbleCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        radialProgress = new RadialProgress2(this, resourcesProvider);
        if (menuDrawable == null) {
            // Theme.chat_msgIn/OutMenuDrawable (R.drawable.msg_actions), tinted per side at draw time
            menuDrawable = context.getResources().getDrawable(R.drawable.msg_actions).mutate();
        }
    }

    @Override
    public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        cancelDocumentTouch();
        boolean changed = this.message != message;
        boolean oldThumbnail = hasThumbnail;
        hasThumbnail = message != null && message.documentThumbnail != null;
        layoutForWidth = -1;
        super.setMessage(message, pinnedTop, pinnedBottom);
        requestLayout();
        if (message == null) {
            thumbnailRect.setEmpty(); buttonRect.setEmpty();
            AnimatedEmojiSpan.release(this, captionSpans); captionSpans = null; captionLayout = null;
        }
        if (message != null) {
            if (changed || oldThumbnail != hasThumbnail) {
                buttonState = -2;
                loadingProgressHash = -1;
            }
            updateButtonState(!changed);
        }
    }

    /** Upstream updateButtonState for documents: -1 ready (file icon), 0 download, 1 loading. */
    public void updateButtonState(boolean animated) {
        if (message == null) {
            return;
        }
        int state;
        if (message.mediaState == TextBubbleMessage.MEDIA_DOWNLOAD) {
            state = 0;
        } else if (message.mediaState == TextBubbleMessage.MEDIA_LOADING) {
            state = 1;
        } else {
            state = -1;
        }
        if (state == 1) {
            radialProgress.setProgress(message.mediaProgress, animated);
            createLoadingProgressLayout((long) (message.fileSize * message.mediaProgress), message.fileSize);
        } else {
            radialProgress.setProgress(0, false);
        }
        if (buttonState != state) {
            boolean animate = animated && buttonState != -2 && isAttachedToWindow();
            buttonState = state;
            radialProgress.setIcon(getIconForCurrentState(), false, animate);
        }
        invalidate();
    }

    private int getIconForCurrentState() {
        if (hasThumbnail) {
            radialProgress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
        } else if (message.isOut) {
            radialProgress.setColorKeys(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected);
        } else {
            radialProgress.setColorKeys(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
        }
        if (buttonState == -1) {
            return hasThumbnail ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_FILE;
        } else if (buttonState == 0) {
            return MediaActionDrawable.ICON_DOWNLOAD;
        } else if (buttonState == 1) {
            return MediaActionDrawable.ICON_CANCEL;
        }
        return MediaActionDrawable.ICON_NONE;
    }

    /** Upstream FileLoader.getDocumentExtension: the part after the last dot, upper case. */
    public static String getDocumentExtension(String fileName) {
        String ext = null;
        if (fileName != null) {
            int idx = fileName.lastIndexOf('.');
            if (idx != -1) {
                ext = fileName.substring(idx + 1);
            }
        }
        return ext == null ? "" : ext.toUpperCase();
    }

    @Override
    protected void buildLayout(int width) {
        layoutForWidth = width;
        layoutForText = message.text;
        setReactionsLayout();
        // TYPE_FILE: backgroundWidth = messageObject.getMaxMessageTextWidth() (no avatar / share button)
        int minSize = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : width;
        backgroundWidth = minSize - AndroidUtilities.dp(80) - (hasThumbnail && incomingAvatarInset() != 0 ? AndroidUtilities.dp(52) : incomingAvatarInset());
        // maxWidth = backgroundWidth - 86dp, and with no caption (maxTextWidth == 0) max(0, backgroundWidth - 138dp)
        int maxWidth = Math.max(0, backgroundWidth - AndroidUtilities.dp(86 + 52));

        captionLayout = null; captionHeight = 0; captionTextXOffset = 0;
        int maxCaptionWidth = 0;
        if (!TextUtils.isEmpty(message.text)) {
            CharSequence caption = AnimatedEmojiSpan.cloneSpans(message.text);
            AnimatedEmojiSpan.applyFontMetricsForString(caption, Theme.chat_msgTextPaint);
            caption = Emoji.replaceEmoji(caption, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
            int widthForCaption = Math.max(1, backgroundWidth);
            captionLayout = new StaticLayout(caption, Theme.chat_msgTextPaint, widthForCaption, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            captionSpans = AnimatedEmojiSpan.update(0, this, captionSpans, captionLayout);
            // MessageObject.TextLayoutBlocks: normalize a wholly RTL block, retaining mixed-line extent.
            captionTextXOffset = Float.MAX_VALUE;
            float captionRight = 0;
            for (int i = 0; i < captionLayout.getLineCount(); i++) {
                captionTextXOffset = Math.min(captionTextXOffset, captionLayout.getLineLeft(i));
                captionRight = Math.max(captionRight, captionLayout.getLineRight(i));
            }
            if (captionTextXOffset == Float.MAX_VALUE) captionTextXOffset = 0;
            maxCaptionWidth = (int)Math.ceil(captionRight - captionTextXOffset);
            maxWidth = Math.max(maxWidth, Math.min(backgroundWidth - AndroidUtilities.dp(86), maxCaptionWidth));
        } else { AnimatedEmojiSpan.release(this, captionSpans); captionSpans = null; }
        // createDocumentLayout: the non-thumbnail branch adds 30dp.
        if (!hasThumbnail) maxWidth += AndroidUtilities.dp(30);
        maxWidth = Math.max(1, maxWidth);
        String name = TextUtils.isEmpty(message.fileName) ? getContext().getString(R.string.ChatDocument) : message.fileName;
        docTitleLayout = StaticLayoutEx.createStaticLayout(name, Theme.chat_docNamePaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.MIDDLE, maxWidth, 2, false);
        docTitleOffsetX = Integer.MIN_VALUE;
        if (docTitleLayout != null && docTitleLayout.getLineCount() > 0) {
            int maxLineWidth = 0;
            for (int a = 0; a < docTitleLayout.getLineCount(); a++) {
                maxLineWidth = Math.max(maxLineWidth, (int) Math.ceil(docTitleLayout.getLineWidth(a)));
                docTitleOffsetX = Math.max(docTitleOffsetX, (int) Math.ceil(-docTitleLayout.getLineLeft(a)));
            }
            docTitleWidth = Math.min(maxWidth, maxLineWidth);
        } else {
            docTitleWidth = maxWidth;
            docTitleOffsetX = 0;
        }
        String str = AndroidUtilities.formatFileSize(message.fileSize) + " " + getDocumentExtension(message.fileName);
        infoWidth = Math.min(maxWidth - AndroidUtilities.dp(30), (int) Math.ceil(Theme.chat_infoPaint.measureText("000.0 mm / " + AndroidUtilities.formatFileSize(message.fileSize))));
        if (infoWidth < 0) {
            infoWidth = AndroidUtilities.dp(10);
        }
        CharSequence str2 = TextUtils.ellipsize(str, Theme.chat_infoPaint, infoWidth, TextUtils.TruncateAt.END);
        infoLayout = new StaticLayout(str2, Theme.chat_infoPaint, infoWidth + AndroidUtilities.dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        int requiredWidth = Math.max(docTitleWidth + AndroidUtilities.dp(hasThumbnail ? 138 : 108), infoWidth + AndroidUtilities.dp(hasThumbnail ? 138 : 108));
        if (captionLayout != null) requiredWidth = Math.max(requiredWidth, maxCaptionWidth + AndroidUtilities.dp(31));
        if (hasThumbnail || captionLayout != null) backgroundWidth = Math.max(backgroundWidth, requiredWidth);
        loadingProgressHash = -1;
        if (message.mediaState == TextBubbleMessage.MEDIA_LOADING) {
            createLoadingProgressLayout((long) (message.fileSize * message.mediaProgress), message.fileSize);
        }

        // photoHeight for !drawPhotoImage
        measureTime();
        int photoHeight = AndroidUtilities.dp(hasThumbnail ? 86 : 56);
        if (!hasThumbnail && docTitleLayout != null && docTitleLayout.getLineCount() > 1) {
            photoHeight += (docTitleLayout.getLineCount() - 1) * AndroidUtilities.dp(16);
        }
        reactionsLayoutInBubble.measure(backgroundWidth - AndroidUtilities.dp(31), android.view.Gravity.LEFT);
        int timeLeft = backgroundWidth - AndroidUtilities.dp(40 + 18 + 56 + 8) - infoWidth;
        if (!hasThumbnail && captionLayout == null && reactionsLayoutInBubble.isEmpty) {
            if (timeLeft < timeWidth) {
                photoHeight += AndroidUtilities.dp(12);
            } else if (infoLayout.getLineCount() == 1) {
                photoHeight += AndroidUtilities.dp(4);
            }
        }
        int additionHeight = 0;
        if (!reactionsLayoutInBubble.isEmpty) {
            // setMessageContent, TYPE_FILE without a thumbnail: pills under the file row, time after the last row or below it
            if (!hasThumbnail) reactionsLayoutInBubble.positionOffsetY += AndroidUtilities.dp(2);
            reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(!hasThumbnail || captionLayout == null ? 8 : 0);
            if (hasThumbnail && captionLayout == null) reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(8);
            int reactionsTimeLeft = backgroundWidth - reactionsLayoutInBubble.lastLineX - AndroidUtilities.dp(42);
            if (reactionsTimeLeft < timeWidth) {
                reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(captionLayout == null ? 15 : 12);
                reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(15);
            }
            if (captionLayout != null) additionHeight += AndroidUtilities.dp(12);
            additionHeight += reactionsLayoutInBubble.totalHeight;
        }

        namesOffset = authorHeight + forwardOffset() + buildReplyLayout(backgroundWidth - AndroidUtilities.dp(31) - AndroidUtilities.dp(message.isOut ? 30 : 20), false);
        if (hasThumbnail) {
            if (hasForwardLayout()) {
                namesOffset += AndroidUtilities.dp(5);
                if (replyNameLayout != null) namesOffset += AndroidUtilities.dp(2);
            } else if (hasAuthorLayout() && replyNameLayout == null) namesOffset += AndroidUtilities.dp(7);
        }
        totalHeight = photoHeight + AndroidUtilities.dp(14) + namesOffset + additionHeight;
        if (captionLayout != null) {
            captionHeight = captionLayout.getHeight();
            int last = captionLayout.getLineCount() - 1;
            if (last >= 0 && reactionsLayoutInBubble.isEmpty && backgroundWidth - AndroidUtilities.dp(39) - (captionLayout.getLineRight(last) - captionTextXOffset) < timeWidth + AndroidUtilities.dp(message.isOut ? 20 : 0)) captionHeight += AndroidUtilities.dp(14);
            totalHeight += captionHeight + AndroidUtilities.dp(9);
        }
        if (pinnedTop) {
            namesOffset -= AndroidUtilities.dp(2);
        }
        if (pinnedTop && !message.isOut) {
            totalHeight += AndroidUtilities.dp(2);
        }
        int photoY = AndroidUtilities.dp(namesOffset > 0 ? 7 : 5);
        totalHeight -= AndroidUtilities.dp(hasThumbnail && namesOffset > 0 ? 2 : 4);
        if (hasThumbnail) {
            if (pinnedBottom) totalHeight -= AndroidUtilities.dp(pinnedTop ? 2 : 1);
            if (pinnedBottom) totalHeight -= AndroidUtilities.dp(1);
            if (pinnedTop) totalHeight -= AndroidUtilities.dp(1);
        }
        int mapX = message.isOut ? width - backgroundWidth + AndroidUtilities.dp(4) : AndroidUtilities.dp(13) + incomingAvatarInset();
        if (hasThumbnail) thumbnailRect.set(mapX, photoY + namesOffset, mapX + AndroidUtilities.dp(86), photoY + namesOffset + AndroidUtilities.dp(86));
        else thumbnailRect.setEmpty();
        updateButtonRect(width);
    }

    private void updateButtonRect(int width) {
        if (hasThumbnail) {
            buttonX = (int) thumbnailRect.left + AndroidUtilities.dp(19);
            buttonY = (int) thumbnailRect.top + AndroidUtilities.dp(19);
            buttonRect.set(buttonX, buttonY, buttonX + AndroidUtilities.dp(48), buttonY + AndroidUtilities.dp(48));
        } else {
            buttonX = message.isOut ? width - backgroundWidth + AndroidUtilities.dp(14) : AndroidUtilities.dp(23) + incomingAvatarInset();
            buttonY = AndroidUtilities.dp(13) + namesOffset;
            buttonRect.set(buttonX, buttonY, buttonX + AndroidUtilities.dp(44), buttonY + AndroidUtilities.dp(44));
        }
    }
    /** Thumbnail bounds in cell coordinates after measure; empty for a document without one. */
    public void getThumbnailRect(RectF out) { out.set(thumbnailRect); }
    public void getButtonRect(RectF out) { out.set(buttonRect); }
    private void updateThumbnailRadii() {
        int radius = AndroidUtilities.dp(SharedConfig.bubbleRadius > 2 ? SharedConfig.bubbleRadius - 2 : SharedConfig.bubbleRadius);
        int min = Math.min(AndroidUtilities.dp(4), radius), near = Math.min(AndroidUtilities.dp(3), radius);
        int tl = radius, tr = min, br = min, bl = radius;
        if (hasAuthorLayout() || hasForwardLayout() || replyNameLayout != null) tl = tr = min;
        if (captionLayout != null) bl = br = min;
        if (pinnedTop) { if (message.isOut) tr = near; else tl = near; }
        if (pinnedBottom) { if (message.isOut) br = near; else bl = near; }
        if (!message.isOut) bl = near;
        thumbnailRadii[0] = thumbnailRadii[1] = tl; thumbnailRadii[2] = thumbnailRadii[3] = tr;
        thumbnailRadii[4] = thumbnailRadii[5] = br; thumbnailRadii[6] = thumbnailRadii[7] = bl;
    }

    /** Upstream createLoadingProgressLayout for DOCUMENT_ATTACH_TYPE_DOCUMENT. */
    private void createLoadingProgressLayout(long loadedSize, long totalSize) {
        if (totalSize <= 0) {
            loadingProgressLayout = null;
            return;
        }
        long hash = (loadedSize << 16) + totalSize;
        if (loadingProgressLayout != null && loadingProgressHash == hash) {
            return;
        }
        loadingProgressHash = hash;
        String totalStr = AndroidUtilities.formatFileSize(totalSize);
        String maxAvailableString = String.format("000.0 mm / %s", totalStr);
        int w = (int) Math.ceil(Theme.chat_infoPaint.measureText(maxAvailableString));
        String str;
        int max = Math.max(infoWidth, docTitleWidth);
        if (w <= max) {
            str = String.format("%s / %s", AndroidUtilities.formatFileSize(loadedSize), totalStr);
        } else {
            str = AndroidUtilities.formatFileSize(loadedSize);
        }
        int width = (int) Math.ceil(Theme.chat_infoPaint.measureText(str));
        loadingProgressLayout = new StaticLayout(str, Theme.chat_infoPaint, width + AndroidUtilities.dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    }

    @Override
    protected void drawContent(Canvas canvas) {
        if (docTitleLayout == null) {
            return;
        }
        int backgroundLeft = drawBubbleBackground(canvas);
        drawAuthor(canvas);
        drawForward(canvas, false);
        drawReply(canvas, false);
        final boolean out = message.isOut;
        final int layoutWidth = getMeasuredWidth();
        // onLayout
        updateButtonRect(layoutWidth);
        radialProgress.setProgressRect((int)buttonRect.left, (int)buttonRect.top, (int)buttonRect.right, (int)buttonRect.bottom);
        if (hasThumbnail) {
            updateThumbnailRadii();
            Drawable thumbnail = message.documentThumbnail;
            if (thumbnail != null) {
                Rect bounds = new Rect(thumbnail.getBounds());
                try { thumbnailRenderer.draw(canvas, thumbnail, thumbnailRect, thumbnailRadii, 0); }
                finally { thumbnail.setBounds(bounds); }
            }
        }

        // drawContent, DOCUMENT_ATTACH_TYPE_DOCUMENT
        int menuColor;
        if (out) {
            Theme.chat_docNamePaint.setColor(getThemedColor(Theme.key_chat_outFileNameText));
            Theme.chat_infoPaint.setColor(getThemedColor(drawSelectionBackground ? Theme.key_chat_outFileInfoSelectedText : Theme.key_chat_outFileInfoText));
            menuColor = getThemedColor(drawSelectionBackground ? Theme.key_chat_outMenuSelected : Theme.key_chat_outMenu);
        } else {
            Theme.chat_docNamePaint.setColor(getThemedColor(Theme.key_chat_inFileNameText));
            Theme.chat_infoPaint.setColor(getThemedColor(drawSelectionBackground ? Theme.key_chat_inFileInfoSelectedText : Theme.key_chat_inFileInfoText));
            menuColor = getThemedColor(drawSelectionBackground ? Theme.key_chat_inMenuSelected : Theme.key_chat_inMenu);
        }
        Theme.setDrawableColor(menuDrawable, menuColor);
        int otherX = hasThumbnail ? (int)thumbnailRect.left + backgroundWidth - AndroidUtilities.dp(40) : buttonX + backgroundWidth - AndroidUtilities.dp(48);
        int otherY = hasThumbnail ? (int)thumbnailRect.top + AndroidUtilities.dp(4) : buttonY - AndroidUtilities.dp(2);
        menuDrawable.setBounds(otherX, otherY, otherX + menuDrawable.getIntrinsicWidth(), otherY + menuDrawable.getIntrinsicHeight());
        float x = hasThumbnail ? thumbnailRect.right + AndroidUtilities.dp(10) : buttonX + AndroidUtilities.dp(53);
        int titleY = hasThumbnail ? (int)thumbnailRect.top + AndroidUtilities.dp(8) : buttonY + AndroidUtilities.dp(4);
        int subtitleY = hasThumbnail ? (int)thumbnailRect.top + docTitleLayout.getLineBottom(docTitleLayout.getLineCount() - 1) + AndroidUtilities.dp(13) : buttonY + AndroidUtilities.dp(27);
        if (!hasThumbnail && docTitleLayout.getLineCount() > 1) {
            subtitleY += (docTitleLayout.getLineCount() - 1) * AndroidUtilities.dp(16) + AndroidUtilities.dp(2);
        }
        if (hasThumbnail) {
            radialProgress.setProgressColor(getThemedColor(Theme.key_chat_mediaProgress));
        } else if (out) {
            radialProgress.setProgressColor(getThemedColor(drawSelectionBackground || buttonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
        } else {
            radialProgress.setProgressColor(getThemedColor(drawSelectionBackground || buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
        }
        menuDrawable.draw(canvas);

        canvas.save();
        canvas.translate(x + docTitleOffsetX, titleY);
        docTitleLayout.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(x, subtitleY);
        if (buttonState == 1 && loadingProgressLayout != null) {
            loadingProgressLayout.draw(canvas);
        } else {
            infoLayout.draw(canvas);
        }
        canvas.restore();

        // upstream drawRadialProgress for documents
        getIconForCurrentState();
        // Upstream photoImage.hasImageSet(): a loaded preview must not tint its icon with the bubble gradient.
        radialProgress.setBackgroundDrawable(hasThumbnail ? null : currentBackgroundDrawable);
        boolean pressed = buttonPressed == 1 || drawSelectionBackground;
        if (pressed != radialPressed) { radialPressed = pressed; radialProgress.setPressed(pressed, false); }
        radialProgress.draw(canvas);
        if (captionLayout != null) {
            Theme.chat_msgTextPaint.setColor(getThemedColor(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
            Theme.chat_msgTextPaint.linkColor = getThemedColor(out ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
            int captionX = backgroundLeft + AndroidUtilities.dp(out || pinnedBottom ? 11 : 17);
            int captionY = getHeight() - captionHeight - AndroidUtilities.dp(pinnedTop ? 9 : 10) - reactionsLayoutInBubble.totalHeight - (reactionsLayoutInBubble.isEmpty ? 0 : AndroidUtilities.dp(10));
            canvas.save(); canvas.translate(captionX - captionTextXOffset, captionY); captionLayout.draw(canvas); canvas.restore();
        }
        drawReactionsInBubble(canvas, backgroundLeft, 0);
        drawTimeAndStatus(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (message != null && !isCheckBoxVisible() && (checkForwardTouchEvent(event) || checkReplyTouchEvent(event) || checkReactionsTouchEvent(event))) return true;
        if (message == null || isCheckBoxVisible() || mediaDelegate == null) { cancelDocumentTouch(); return super.onTouchEvent(event); }
        float x = event.getX(), y = event.getY();
        int menuX = hasThumbnail ? (int)thumbnailRect.left + backgroundWidth - AndroidUtilities.dp(40) : buttonX + backgroundWidth - AndroidUtilities.dp(48);
        int menuY = hasThumbnail ? (int)thumbnailRect.top + AndroidUtilities.dp(4) : buttonY - AndroidUtilities.dp(2);
        boolean menuArea = x >= menuX - AndroidUtilities.dp(20) && x <= menuX + AndroidUtilities.dp(20) && y >= menuY - AndroidUtilities.dp(4) && y <= menuY + AndroidUtilities.dp(30);
        boolean area = hasThumbnail ? x >= thumbnailRect.left && x <= thumbnailRect.left + backgroundWidth - AndroidUtilities.dp(50) && y >= thumbnailRect.top && y <= thumbnailRect.bottom
                : x >= buttonX && x < menuX - AndroidUtilities.dp(10) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(44);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && (menuArea || area)) {
            if (longPress != null) removeCallbacks(longPress);
            buttonPressed = menuArea ? 2 : 1; downX = x; downY = y;
            if (getParent() != null) { getParent().requestDisallowInterceptTouchEvent(true); interceptHeld = true; }
            longPress = () -> { longPress = null; cancelDocumentTouch(); onInteractiveLongPress(false); };
            postDelayed(longPress, ViewConfiguration.getLongPressTimeout()); invalidate(); return true;
        } else if (buttonPressed != 0) {
            if (action == MotionEvent.ACTION_UP) {
                int pressed = buttonPressed; cancelDocumentTouch(); playSoundEffect(SoundEffectConstants.CLICK);
                if (pressed == 2) mediaDelegate.onMenuPressed(this, message, x, y); else mediaDelegate.onMediaPressed(this, message);
                invalidate(); return true;
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_MOVE &&
                    (!(buttonPressed == 2 ? menuArea : area) || Math.abs(x-downX) > ViewConfiguration.get(getContext()).getScaledTouchSlop() || Math.abs(y-downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop())) {
                cancelDocumentTouch(); invalidate(); return action != MotionEvent.ACTION_MOVE;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
    private void cancelDocumentTouch() {
        buttonPressed = 0;
        if (longPress != null) { removeCallbacks(longPress); longPress = null; }
        if (interceptHeld) { interceptHeld = false; if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false); }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        radialProgress.onAttachedToWindow();
        layoutForWidth = -1; requestLayout();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        radialProgress.onDetachedFromWindow();
        cancelDocumentTouch();
        AnimatedEmojiSpan.release(this, captionSpans); captionSpans = null; layoutForWidth = -1;
    }
}
