package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.StaticLayoutEx;

/**
 * tgui: file (document) message bubble ({@link TextBubbleMessage#TYPE_DOCUMENT}) without a
 * thumbnail. Transplanted from upstream ChatMessageCell for DOCUMENT_ATTACH_TYPE_DOCUMENT with
 * drawPhotoImage == false and no caption:
 * <ul>
 * <li>setMessageContent (TYPE_FILE branch): backgroundWidth = getMaxMessageTextWidth, 56dp row
 *     growing with the title's lines and the time line, pinned top offsets</li>
 * <li>createDocumentLayout (document branch): 2-line middle-ellipsized docTitleLayout, infoLayout
 *     "size EXT"; createLoadingProgressLayout "loaded / total"</li>
 * <li>onLayout (DOCUMENT_ATTACH_TYPE_DOCUMENT && !drawPhotoImage): buttonX / buttonY</li>
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
        boolean changed = this.message != message;
        super.setMessage(message, pinnedTop, pinnedBottom);
        if (message != null) {
            if (changed) {
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
        if (message.isOut) {
            radialProgress.setColorKeys(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected);
        } else {
            radialProgress.setColorKeys(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
        }
        if (buttonState == -1) {
            return MediaActionDrawable.ICON_FILE;
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
        layoutForReaction = message.reaction;
        // TYPE_FILE: backgroundWidth = messageObject.getMaxMessageTextWidth() (no avatar / share button)
        int minSize = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : width;
        backgroundWidth = minSize - AndroidUtilities.dp(80);
        // maxWidth = backgroundWidth - 86dp, and with no caption (maxTextWidth == 0) max(0, backgroundWidth - 138dp)
        int maxWidth = Math.max(0, backgroundWidth - AndroidUtilities.dp(86 + 52));

        // createDocumentLayout, no photo
        maxWidth += AndroidUtilities.dp(30);
        String name = TextUtils.isEmpty(message.fileName) ? "File" : message.fileName;
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
        loadingProgressHash = -1;
        if (message.mediaState == TextBubbleMessage.MEDIA_LOADING) {
            createLoadingProgressLayout((long) (message.fileSize * message.mediaProgress), message.fileSize);
        }

        // photoHeight for !drawPhotoImage
        measureTime();
        int photoHeight = AndroidUtilities.dp(56);
        if (docTitleLayout != null && docTitleLayout.getLineCount() > 1) {
            photoHeight += (docTitleLayout.getLineCount() - 1) * AndroidUtilities.dp(16);
        }
        int timeLeft = backgroundWidth - AndroidUtilities.dp(40 + 18 + 56 + 8) - infoWidth;
        if (timeLeft < timeWidth) {
            photoHeight += AndroidUtilities.dp(12);
        } else if (infoLayout.getLineCount() == 1) {
            photoHeight += AndroidUtilities.dp(4);
        }

        namesOffset = 0;
        totalHeight = photoHeight + AndroidUtilities.dp(14) + namesOffset;
        if (pinnedTop) {
            namesOffset -= AndroidUtilities.dp(2);
        }
        if (pinnedTop && !message.isOut) {
            totalHeight += AndroidUtilities.dp(2);
        }
        totalHeight -= AndroidUtilities.dp(4);
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
        drawBubbleBackground(canvas);
        final boolean out = message.isOut;
        final int layoutWidth = getMeasuredWidth();
        // onLayout
        buttonX = out ? layoutWidth - backgroundWidth + AndroidUtilities.dp(14) : AndroidUtilities.dp(23);
        buttonY = AndroidUtilities.dp(13) + namesOffset;
        radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(44), buttonY + AndroidUtilities.dp(44));

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
        int otherX = buttonX + backgroundWidth - AndroidUtilities.dp(48);
        int otherY = buttonY - AndroidUtilities.dp(2);
        menuDrawable.setBounds(otherX, otherY, otherX + menuDrawable.getIntrinsicWidth(), otherY + menuDrawable.getIntrinsicHeight());
        float x = buttonX + AndroidUtilities.dp(53);
        int titleY = buttonY + AndroidUtilities.dp(4);
        int subtitleY = buttonY + AndroidUtilities.dp(27);
        if (docTitleLayout.getLineCount() > 1) {
            subtitleY += (docTitleLayout.getLineCount() - 1) * AndroidUtilities.dp(16) + AndroidUtilities.dp(2);
        }
        if (out) {
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
        radialProgress.setBackgroundDrawable(currentBackgroundDrawable);
        radialProgress.setPressed(buttonPressed != 0, false);
        radialProgress.draw(canvas);

        drawTimeAndStatus(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (message == null || isCheckBoxVisible() || mediaDelegate == null) {
            return super.onTouchEvent(event);
        }
        int x = (int) event.getX();
        int y = (int) event.getY();
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
                mediaDelegate.onMediaPressed(this, message);
                invalidate();
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_MOVE && !area) {
                buttonPressed = 0;
                invalidate();
            }
            return true;
        }
        return super.onTouchEvent(event);
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
