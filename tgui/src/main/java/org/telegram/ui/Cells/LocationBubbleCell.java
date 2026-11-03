/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2013-2018.
 */
// Static TYPE_GEO branches adapted from ChatMessageCell, release-11.4.2-5469.
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;

/** Static host-supplied map. Empty title selects the plain coordinate layout. */
public class LocationBubbleCell extends PhotoBubbleCell {
    private final PhotoRenderer renderer = new PhotoRenderer();
    private final Drawable pin;
    private Drawable placeholder;
    private int placeholderWidth, placeholderHeight, placeholderColor;
    private StaticLayout titleLayout, addressLayout;
    private boolean venue;

    public LocationBubbleCell(Context context) { this(context, null); }
    public LocationBubbleCell(Context context, Theme.ResourcesProvider provider) {
        super(context, provider);
        pin = context.getResources().getDrawable(R.drawable.map_pin).mutate();
    }
    @Override public void setMessage(TextBubbleMessage value, boolean pinnedTop, boolean pinnedBottom) {
        layoutForWidth = -1;
        super.setMessage(value, pinnedTop, pinnedBottom);
        if (value == null || value.location == null) {
            photoRect.setEmpty(); titleLayout = addressLayout = null;
        }
        requestLayout();
    }
    @Override protected void buildLayout(int width) {
        layoutForWidth = width; layoutForText = message.text;
        setReactionsLayout();
        TextBubbleMessage.Location location = message.location;
        venue = location != null && !TextUtils.isEmpty(location.title);
        Theme.chat_locationTitlePaint.setTextSize(AndroidUtilities.dp(15));
        Theme.chat_locationAddressPaint.setTextSize(AndroidUtilities.dp(13));
        int side = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : width;
        backgroundWidth = Math.max(AndroidUtilities.dp(40), Math.min(side - AndroidUtilities.dp(incomingAvatarInset() != 0 ? 102 : 50), AndroidUtilities.dp(289)) - AndroidUtilities.dp(4));
        photoWidth = Math.max(1, backgroundWidth - AndroidUtilities.dp(venue ? 17 : 8));
        photoHeight = AndroidUtilities.dp(195);
        mediaBackground = !venue;
        int additionHeight = 0;
        titleLayout = addressLayout = null;
        measureTime();
        if (venue) {
            int max = Math.max(1, backgroundWidth - AndroidUtilities.dp(34));
            CharSequence title = TextUtils.ellipsize(AndroidUtilities.replaceNewLines(location.title), Theme.chat_locationTitlePaint, max, TextUtils.TruncateAt.END);
            titleLayout = new StaticLayout(title, Theme.chat_locationTitlePaint, max + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
            additionHeight += AndroidUtilities.dp(50);
            if (!TextUtils.isEmpty(location.address)) {
                CharSequence address = TextUtils.ellipsize(AndroidUtilities.replaceNewLines(location.address), Theme.chat_locationAddressPaint, max, TextUtils.TruncateAt.END);
                addressLayout = new StaticLayout(address, Theme.chat_locationAddressPaint, max, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
                int remaining = backgroundWidth - (int) Math.ceil(addressLayout.getLineWidth(0)) - AndroidUtilities.dp(24);
                boolean rtl = addressLayout.getLineLeft(0) > 0;
                if (rtl || remaining < timeWidth + AndroidUtilities.dp(20 + (message.isOut ? 20 : 0))) additionHeight += AndroidUtilities.dp(rtl ? 10 : 8);
            }
        }
        reactionsLayoutInBubble.measure(Math.max(1, backgroundWidth - AndroidUtilities.dp(16)), Gravity.LEFT);
        if (!reactionsLayoutInBubble.isEmpty) {
            reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(14);
            if (reactionsLayoutInBubble.lastLineX + timeWidth + AndroidUtilities.dp(24) > backgroundWidth) {
                reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(12);
                reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(12);
            }
            additionHeight += reactionsLayoutInBubble.totalHeight;
        }
        namesOffset = authorHeight + forwardOffset() + buildReplyLayout(backgroundWidth - AndroidUtilities.dp(mediaBackground ? 22 : 31) - AndroidUtilities.dp(message.isOut ? 30 : 20), false);
        if (hasForwardLayout()) namesOffset += AndroidUtilities.dp(5);
        else if (hasAuthorLayout() && replyNameLayout == null) namesOffset += AndroidUtilities.dp(7);
        totalHeight = photoHeight + AndroidUtilities.dp(14) + namesOffset + additionHeight;
        if (pinnedTop) namesOffset -= AndroidUtilities.dp(1);
        photoY = AndroidUtilities.dp(namesOffset > 0 ? 7 : 5);
        totalHeight -= AndroidUtilities.dp(namesOffset > 0 ? 2 : 4);
        if (pinnedBottom) totalHeight -= AndroidUtilities.dp(pinnedTop ? 2 : 1);
        if (!mediaBackground) {
            if (pinnedBottom) totalHeight -= AndroidUtilities.dp(1);
            if (pinnedTop) totalHeight -= AndroidUtilities.dp(1);
        }
        int rad = AndroidUtilities.dp(SharedConfig.bubbleRadius > 2 ? SharedConfig.bubbleRadius - 2 : SharedConfig.bubbleRadius);
        int near = Math.min(AndroidUtilities.dp(3), rad), min = Math.min(AndroidUtilities.dp(4), rad);
        int tl = rad, tr = rad, bl = venue ? near : rad, br = venue ? near : rad;
        if (replyNameLayout != null || hasAuthorLayout() || hasForwardLayout()) tl = tr = min;
        if (pinnedTop) { if (message.isOut) tr = near; else tl = near; }
        if (pinnedBottom) { if (message.isOut) br = near; else bl = near; }
        if (!mediaBackground && !message.isOut) bl = near;
        radii[0] = radii[1] = tl; radii[2] = radii[3] = tr;
        radii[4] = radii[5] = br; radii[6] = radii[7] = bl;
        updateMapRect(width);
    }
    private void updateMapRect(int width) {
        int x = message.isOut ? width - backgroundWidth + AndroidUtilities.dp(mediaBackground ? -5 : 4) : AndroidUtilities.dp(13) + incomingAvatarInset();
        int y = photoY + namesOffset;
        photoRect.set(x, y, x + photoWidth, y + photoHeight);
    }
    @Override protected void drawContent(Canvas canvas) {
        if (message.location == null) return;
        int left = drawBubbleBackground(canvas);
        drawAuthor(canvas, mediaBackground); drawForward(canvas, mediaBackground); drawReply(canvas, mediaBackground);
        updateMapRect(getMeasuredWidth());
        Drawable image = message.location.mapImage;
        if (image == null) image = getPlaceholder();
        Rect old = image == null ? null : new Rect(image.getBounds());
        // SvgDrawable uses alpha 70 in upstream's non-animated placeholder branch.
        // Apply it to the layer: SvgHelper's parser replaces color alpha and PhotoRenderer uses a shader.
        int layer = message.location.mapImage == null && image != null ? canvas.saveLayerAlpha(photoRect, 70) : -1;
        renderer.draw(canvas, image, photoRect, radii, getThemedColor(message.isOut ? Theme.key_chat_outBubbleLocationPlaceholder : Theme.key_chat_inBubbleLocationPlaceholder));
        if (layer != -1) canvas.restoreToCount(layer);
        if (image != null) image.setBounds(old);
        if (message.location.mapImage != null) {
            int w = (int) (pin.getIntrinsicWidth() * .8f), h = (int) (pin.getIntrinsicHeight() * .8f);
            int x = (int) (photoRect.centerX() - w / 2f), y = (int) (photoRect.centerY() - h);
            pin.setBounds(x, y, x + w, y + h); pin.draw(canvas);
        }
        if (venue && titleLayout != null) {
            Theme.chat_locationTitlePaint.setColor(getThemedColor(message.isOut ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
            Theme.chat_locationAddressPaint.setColor(getThemedColor(message.isOut ? (drawSelectionBackground ? Theme.key_chat_outVenueInfoSelectedText : Theme.key_chat_outVenueInfoText) : (drawSelectionBackground ? Theme.key_chat_inVenueInfoSelectedText : Theme.key_chat_inVenueInfoText)));
            canvas.save(); canvas.translate(photoRect.left + AndroidUtilities.dp(6), photoRect.bottom + AndroidUtilities.dp(8));
            titleLayout.draw(canvas);
            if (addressLayout != null) { canvas.translate(0, AndroidUtilities.dp(21)); addressLayout.draw(canvas); }
            canvas.restore();
        }
        if (mediaBackground && reactionsLayoutInBubble.isEmpty) {
            int right = message.isOut ? getMeasuredWidth() - AndroidUtilities.dp(18) : backgroundWidth + incomingAvatarInset();
            drawTimePill(canvas, right, (int) photoRect.bottom - AndroidUtilities.dp(6), (int) Math.min(AndroidUtilities.dp(8), Math.max(radii[4], radii[6])));
        } else {
            // TYPE_GEO uses the media background's +8dp incoming reaction anchor,
            // including pinned rows; the ordinary bubble helper uses +17dp / +11dp.
            int reactionLeft = left - (mediaBackground && !message.isOut ? AndroidUtilities.dp(pinnedBottom ? 3 : 9) : 0);
            drawReactionsInBubble(canvas, reactionLeft, 0);
            if (mediaBackground) {
                int timeX = message.isOut ? getMeasuredWidth() - timeWidth - AndroidUtilities.dp(42)
                        : backgroundWidth - AndroidUtilities.dp(4) - timeWidth + incomingAvatarInset();
                if (SharedConfig.bubbleRadius >= 10) timeX -= AndroidUtilities.dp(2);
                drawTimeAndStatus(canvas, timeX);
            } else {
                drawTimeAndStatus(canvas);
            }
        }
    }
    private Drawable getPlaceholder() {
        int color = getThemedColor(message.isOut ? Theme.key_chat_outLocationIcon : Theme.key_chat_inLocationIcon);
        int w = Math.max(1, photoWidth), h = Math.max(1, photoHeight);
        if (placeholder == null || w != placeholderWidth || h != placeholderHeight || color != placeholderColor) {
            Bitmap bitmap = SvgHelper.getBitmap(R.raw.map_placeholder, w, h, color);
            placeholder = bitmap == null ? null : new BitmapDrawable(getResources(), bitmap);
            placeholderWidth = w; placeholderHeight = h; placeholderColor = color;
        }
        return placeholder;
    }
    public void getMapRect(RectF out) { out.set(photoRect); }
    public float[] getMapRadii() { return radii.clone(); }
    @Override protected void onPhotoPressed(float x, float y) {
        if (mediaDelegate != null && message != null && message.location != null) mediaDelegate.onLocationPressed(this, message, message.location);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (message != null && !isCheckBoxVisible() && (checkReplyTouchEvent(event) || checkReactionsTouchEvent(event))) return true;
        return super.onTouchEvent(event);
    }
}
