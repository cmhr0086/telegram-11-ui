package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;

/**
 * tgui: photo message bubble ({@link TextBubbleMessage#TYPE_PHOTO}) with an optional caption
 * ({@link TextBubbleMessage#text}). Transplanted from upstream ChatMessageCell for TYPE_PHOTO
 * without albums, spoilers or a network loader:
 * <ul>
 * <li>setMessageContent (photo branch) + getMessageSize: max width / height, aspect, 120dp minimum
 *     height, timeWidthTotal, caption width and the extra time line</li>
 * <li>setMessageContent: photoImage y / totalHeight, the canChangeRadius block (bubble radius - 2,
 *     nearRad for grouped (pinned) corners, minRad above a caption)</li>
 * <li>onLayout: photo x (mediaBackground or not), buttonX / buttonY of the 48dp loader</li>
 * <li>drawBackground for mediaBackground (no tail, 9dp side inset), drawCaptionLayout position,
 *     drawTimeInternal's pill on the photo (8dp radius, photo bottom - 23dp)</li>
 * <li>getIconForCurrentState for photos: key_chat_mediaLoaderPhoto* download / cancel overlay</li>
 * </ul>
 * The photo is a host-supplied {@link Drawable} (a {@link BitmapDrawable} is drawn with a
 * BitmapShader for anti-aliased corners, anything else is clipped), scaled center-crop like
 * BackupImageView / ImageReceiver.
 */
public class PhotoBubbleCell extends TextBubbleCell {

    private final RadialProgress2 radialProgress;
    private final RectF photoRect = new RectF();
    private final Path photoPath = new Path();
    private final float[] radii = new float[8];
    private final Matrix shaderMatrix = new Matrix();
    private Paint photoPaint;
    private Bitmap shaderBitmap;
    private BitmapShader shader;

    private int photoWidth, photoHeight;
    private int photoY;
    private boolean mediaBackground;
    private StaticLayout captionLayout;
    private int captionHeight;
    private int namesOffset;
    private int buttonState = -1;
    private boolean photoPressed;

    public PhotoBubbleCell(Context context) {
        this(context, null);
    }

    public PhotoBubbleCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        radialProgress = new RadialProgress2(this, resourcesProvider);
        radialProgress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
    }

    @Override
    public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        boolean changed = this.message != message;
        super.setMessage(message, pinnedTop, pinnedBottom);
        if (message != null) {
            updateButtonState(!changed);
        }
    }

    /** Re-reads {@link TextBubbleMessage#mediaState} / {@link TextBubbleMessage#mediaProgress} (upstream updateButtonState). */
    public void updateButtonState(boolean animated) {
        if (message == null) {
            return;
        }
        int state = message.mediaState;
        if (state == TextBubbleMessage.MEDIA_LOADING) {
            radialProgress.setProgress(message.mediaProgress, animated);
        } else {
            radialProgress.setProgress(0, false);
        }
        if (buttonState != state) {
            boolean animate = animated && buttonState != -1 && isAttachedToWindow();
            buttonState = state;
            int icon;
            if (state == TextBubbleMessage.MEDIA_DOWNLOAD) {
                icon = MediaActionDrawable.ICON_DOWNLOAD;
            } else if (state == TextBubbleMessage.MEDIA_LOADING) {
                icon = MediaActionDrawable.ICON_CANCEL;
            } else {
                icon = MediaActionDrawable.ICON_NONE;
            }
            radialProgress.setIcon(icon, false, animate);
        }
        invalidate();
    }

    @Override
    protected void buildLayout(int width) {
        layoutForWidth = width;
        layoutForText = message.text;
        layoutForReaction = message.reaction;
        final boolean out = message.isOut;
        int imageW = message.photoWidth;
        int imageH = message.photoHeight;
        if ((imageW <= 0 || imageH <= 0) && message.photo != null) {
            imageW = message.photo.getIntrinsicWidth();
            imageH = message.photo.getIntrinsicHeight();
        }

        // setMessageContent: photoWidth / photoHeight limits
        int maxPhotoWidth;
        boolean useFullWidth = false;
        if (AndroidUtilities.isTablet()) {
            maxPhotoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
        } else if (imageW > 0 && imageW >= imageH) {
            maxPhotoWidth = Math.min(width, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(64);
            useFullWidth = true;
        } else {
            maxPhotoWidth = (int) (Math.min(width, AndroidUtilities.displaySize.y) * 0.7f);
        }
        int maxPhotoHeight = maxPhotoWidth + AndroidUtilities.dp(100);
        if (!useFullWidth) {
            maxPhotoWidth = Math.min(maxPhotoWidth, AndroidUtilities.getPhotoSize());
            maxPhotoHeight = Math.min(maxPhotoHeight, AndroidUtilities.getPhotoSize());
        }
        int[] size = getMessageSize(imageW, imageH, maxPhotoWidth, maxPhotoHeight);
        int w = size[0];
        int h = size[1];

        measureTime();
        int timeWidthTotal = timeWidth + AndroidUtilities.dp((SharedConfig.bubbleRadius >= 10 ? 22 : 18) + (out ? 20 : 0));
        if (w < timeWidthTotal) {
            w = timeWidthTotal;
        }

        CharSequence caption = TextUtils.isEmpty(message.text) ? null : Emoji.replaceEmoji(message.text, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
        mediaBackground = caption == null;
        photoWidth = w;
        photoHeight = h;
        int widthForCaption;
        boolean fixPhotoWidth = false;
        // MessageObject.getMaxMessageTextWidth without avatars
        int minCaptionWidth = Math.min(width, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80);
        if (caption != null && photoWidth < minCaptionWidth) {
            widthForCaption = minCaptionWidth;
            fixPhotoWidth = true;
        } else {
            widthForCaption = photoWidth - AndroidUtilities.dp(10);
        }
        backgroundWidth = photoWidth + AndroidUtilities.dp(8);
        if (!mediaBackground) {
            backgroundWidth += AndroidUtilities.dp(9);
        }

        int additionHeight = 0;
        captionLayout = null;
        captionHeight = 0;
        if (caption != null) {
            captionLayout = new StaticLayout(caption, Theme.chat_msgTextPaint, Math.max(1, widthForCaption), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            int captionWidth = 0;
            for (int i = 0; i < captionLayout.getLineCount(); i++) {
                captionWidth = Math.max(captionWidth, (int) Math.ceil(captionLayout.getLineWidth(i)));
            }
            captionWidth = Math.min(captionWidth, widthForCaption);
            captionHeight = captionLayout.getHeight();
            int addedCaptionHeight = captionHeight + AndroidUtilities.dp(9);
            int widthToCheck = Math.max(captionWidth, photoWidth - AndroidUtilities.dp(10));
            int lastLineWidth = (int) Math.ceil(captionLayout.getLineWidth(captionLayout.getLineCount() - 1));
            if (widthToCheck + AndroidUtilities.dp(2) - lastLineWidth < timeWidthTotal) {
                addedCaptionHeight += AndroidUtilities.dp(14);
            }
            additionHeight += addedCaptionHeight;
            if (fixPhotoWidth && photoWidth < captionWidth + AndroidUtilities.dp(10)) {
                photoWidth = captionWidth + AndroidUtilities.dp(10);
                backgroundWidth = photoWidth + AndroidUtilities.dp(8) + AndroidUtilities.dp(9);
            }
        }

        // totalHeight / photoImage y (no name / forward header)
        namesOffset = buildReplyLayout(backgroundWidth - AndroidUtilities.dp(mediaBackground ? 22 : 31) - AndroidUtilities.dp(out ? 30 : 20), false);
        totalHeight = photoHeight + AndroidUtilities.dp(14) + namesOffset + additionHeight;
        if (pinnedTop) {
            namesOffset -= AndroidUtilities.dp(1);
        }
        photoY = AndroidUtilities.dp(5);
        totalHeight -= AndroidUtilities.dp(4);

        // canChangeRadius: photoImage.setRoundRadius(tl, tr, br, bl)
        int minRad = AndroidUtilities.dp(4);
        int rad = AndroidUtilities.dp(SharedConfig.bubbleRadius > 2 ? SharedConfig.bubbleRadius - 2 : SharedConfig.bubbleRadius);
        int nearRad = Math.min(AndroidUtilities.dp(3), rad);
        int tl, tr, bl, br;
        tl = tr = bl = br = rad;
        if (minRad > tl) {
            minRad = tl;
        }
        if (captionLayout != null) {
            bl = br = minRad;
        }
        if (replyNameLayout != null) {
            tl = tr = minRad;
        }
        if (pinnedTop) {
            if (out) {
                tr = nearRad;
            } else {
                tl = nearRad;
            }
        }
        if (pinnedBottom && captionLayout == null) {
            if (out) {
                br = nearRad;
            } else {
                bl = nearRad;
            }
        }
        if (!mediaBackground && !out) {
            bl = nearRad;
        }
        radii[0] = radii[1] = tl;
        radii[2] = radii[3] = tr;
        radii[4] = radii[5] = br;
        radii[6] = radii[7] = bl;
    }

    /** Upstream ChatMessageCell.getMessageSize(imageW, imageH, photoWidth, photoHeight). */
    private static int[] getMessageSize(int imageW, int imageH, int photoWidth, int photoHeight) {
        if (imageW <= 0 || imageH <= 0) {
            return new int[]{AndroidUtilities.dp(150), AndroidUtilities.dp(150)};
        }
        float scale = (float) imageW / (float) photoWidth;
        int w = (int) (imageW / scale);
        int h = (int) (imageH / scale);
        if (w == 0) {
            w = AndroidUtilities.dp(150);
        }
        if (h == 0) {
            h = AndroidUtilities.dp(150);
        }
        if (h > photoHeight) {
            float scale2 = h;
            h = photoHeight;
            scale2 /= h;
            w = (int) (w / scale2);
        } else if (h < AndroidUtilities.dp(120)) {
            h = AndroidUtilities.dp(120);
            float hScale = (float) imageH / h;
            if (imageW / hScale < photoWidth) {
                w = (int) (imageW / hScale);
            }
        }
        return new int[]{w, h};
    }

    @Override
    protected int drawBubbleBackground(Canvas canvas) {
        if (!mediaBackground) {
            return super.drawBubbleBackground(canvas);
        }
        // drawBackground with mediaBackground: always the tail-less media drawable, 9dp from the side
        final boolean out = message.isOut;
        final int layoutWidth = getMeasuredWidth();
        final int layoutHeight = getMeasuredHeight();
        Theme.MessageDrawable drawable;
        if (drawSelectionBackground) {
            drawable = out ? Theme.chat_msgOutMediaSelectedDrawable : Theme.chat_msgInMediaSelectedDrawable;
        } else {
            drawable = out ? Theme.chat_msgOutMediaDrawable : Theme.chat_msgInMediaDrawable;
        }
        int backgroundLeft = out ? layoutWidth - backgroundWidth - AndroidUtilities.dp(9) : AndroidUtilities.dp(9);
        int offsetBottom;
        if (pinnedBottom && pinnedTop) {
            offsetBottom = 0;
        } else if (pinnedBottom) {
            offsetBottom = AndroidUtilities.dp(1);
        } else {
            offsetBottom = AndroidUtilities.dp(2);
        }
        int backgroundTop = pinnedTop ? 0 : AndroidUtilities.dp(1);
        int backgroundHeight = layoutHeight - offsetBottom;
        int parentWidth = layoutWidth, parentHeight = AndroidUtilities.displaySize.y;
        if (getParent() instanceof android.view.View) {
            android.view.View parent = (android.view.View) getParent();
            parentWidth = parent.getMeasuredWidth();
            parentHeight = parent.getMeasuredHeight();
        }
        drawable.setTop((int) getY(), parentWidth, parentHeight, 0, 0, 0, pinnedTop, pinnedBottom);
        Drawable shadow = drawable.getShadowDrawable();
        drawable.setBounds(backgroundLeft, backgroundTop, backgroundLeft + backgroundWidth, backgroundTop + backgroundHeight);
        bubbleBottom = backgroundTop + backgroundHeight;
        if (shadow != null) {
            shadow.setBounds(drawable.getBounds());
            shadow.draw(canvas);
        }
        drawable.draw(canvas);
        currentBackgroundDrawable = drawable;
        drawHighlightBubble(canvas, out ? Theme.chat_msgOutMediaSelectedDrawable : Theme.chat_msgInMediaSelectedDrawable, parentWidth, parentHeight);
        return backgroundLeft;
    }

    @Override
    protected void drawContent(Canvas canvas) {
        final boolean out = message.isOut;
        final int layoutWidth = getMeasuredWidth();
        drawBubbleBackground(canvas);
        drawReply(canvas, mediaBackground);

        // onLayout: photo x
        int x;
        if (out) {
            x = mediaBackground ? layoutWidth - backgroundWidth - AndroidUtilities.dp(3) : layoutWidth - backgroundWidth + AndroidUtilities.dp(6);
        } else {
            x = AndroidUtilities.dp(15);
        }
        x -= AndroidUtilities.dp(2);
        int y = photoY + namesOffset;
        photoRect.set(x, y, x + photoWidth, y + photoHeight);
        drawPhoto(canvas);

        // the 48dp loader in the photo's center
        int buttonX = (int) (x + (photoWidth - AndroidUtilities.dp(48)) / 2.0f);
        int buttonY = (int) (y + (photoHeight - AndroidUtilities.dp(48)) / 2.0f);
        radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48), buttonY + AndroidUtilities.dp(48));
        // RadialProgress2.draw skips itself once ICON_NONE has faded out
        radialProgress.setProgressColor(getThemedColor(Theme.key_chat_mediaProgress));
        radialProgress.setPressed(photoPressed, false);
        radialProgress.draw(canvas);

        if (captionLayout != null) {
            // drawCaptionLayout: below the photo, 5dp in, 6dp down
            Theme.chat_msgTextPaint.setColor(getThemedColor(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
            Theme.chat_msgTextPaint.linkColor = getThemedColor(out ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
            canvas.save();
            canvas.translate(x + AndroidUtilities.dp(5), y + photoHeight + AndroidUtilities.dp(6));
            captionLayout.draw(canvas);
            canvas.restore();
            drawTimeAndStatus(canvas);
        } else {
            // drawTimeInternal on media: pill bottom = photo bottom - 6dp, right edge 5dp inside the photo
            int right = out ? layoutWidth - AndroidUtilities.dp(18) : backgroundWidth;
            drawTimePill(canvas, right, (int) photoRect.bottom - AndroidUtilities.dp(6), AndroidUtilities.dp(8));
        }
    }

    private void drawPhoto(Canvas canvas) {
        photoPath.rewind();
        photoPath.addRoundRect(photoRect, radii, Path.Direction.CW);
        Drawable photo = message.photo;
        if (photo == null) {
            // ImageReceiver placeholder before the image arrives
            Theme.chat_docBackPaint.setColor(getThemedColor(message.isOut ? Theme.key_chat_outLoaderSelected : Theme.key_chat_inMediaIcon));
            Theme.chat_docBackPaint.setAlpha(60);
            canvas.drawPath(photoPath, Theme.chat_docBackPaint);
            Theme.chat_docBackPaint.setAlpha(255);
            return;
        }
        int dw = photo.getIntrinsicWidth();
        int dh = photo.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) {
            dw = (int) photoRect.width();
            dh = (int) photoRect.height();
        }
        // center-crop (ImageReceiver's default scale type)
        float scale = Math.max(photoRect.width() / dw, photoRect.height() / dh);
        float sw = dw * scale, sh = dh * scale;
        float left = photoRect.centerX() - sw / 2f;
        float top = photoRect.centerY() - sh / 2f;
        if (photo instanceof BitmapDrawable && ((BitmapDrawable) photo).getBitmap() != null) {
            Bitmap bitmap = ((BitmapDrawable) photo).getBitmap();
            if (shaderBitmap != bitmap) {
                shaderBitmap = bitmap;
                shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                if (photoPaint == null) {
                    photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                }
                photoPaint.setShader(shader);
            }
            float bitmapScale = Math.max(photoRect.width() / bitmap.getWidth(), photoRect.height() / bitmap.getHeight());
            shaderMatrix.reset();
            shaderMatrix.setScale(bitmapScale, bitmapScale);
            shaderMatrix.postTranslate(photoRect.centerX() - bitmap.getWidth() * bitmapScale / 2f, photoRect.centerY() - bitmap.getHeight() * bitmapScale / 2f);
            shader.setLocalMatrix(shaderMatrix);
            canvas.drawPath(photoPath, photoPaint);
        } else {
            canvas.save();
            canvas.clipPath(photoPath);
            photo.setBounds((int) left, (int) top, (int) (left + sw), (int) (top + sh));
            photo.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (message == null || isCheckBoxVisible() || mediaDelegate == null) {
            return super.onTouchEvent(event);
        }
        // only the download / cancel button takes the tap; a ready photo opens the menu like other bubbles
        boolean area = buttonState != TextBubbleMessage.MEDIA_READY && photoRect.contains(event.getX(), event.getY());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (area) {
                photoPressed = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
                return true;
            }
        } else if (photoPressed) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                photoPressed = false;
                playSoundEffect(SoundEffectConstants.CLICK);
                mediaDelegate.onMediaPressed(this, message);
                invalidate();
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_MOVE && !area) {
                photoPressed = false;
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
