package org.telegram.ui.Components;

import android.text.Editable;
import android.util.TypedValue;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;

/**
 * tgui: upstream EditTextEffects renders spoilers, quote blocks and animated custom emoji.
 * Those are messenger features; this version keeps the public surface and the clipping
 * workarounds so EditTextBoldCursor behaves the same for plain/styled text.
 */
public class EditTextEffects extends EditText {

    public boolean suppressOnTextChanged;
    public int quoteColor;
    public boolean drawAnimatedEmojiDrawables = true;
    private boolean clipToPadding;

    public EditTextEffects(Context context) {
        super(context);
    }

    public void recycleEmojis() {
    }

    public void setShouldRevealSpoilersByTouch(boolean shouldRevealSpoilersByTouch) {
    }

    public void setSpoilersRevealed(boolean spoilersRevealed, boolean notifyEffects) {
    }

    protected float offsetY;

    public void setOffsetY(float offset) {
        this.offsetY = offset;
        invalidate();
    }

    public float getOffsetY() {
        return offsetY;
    }

    private static Boolean allowHackingTextCanvasCache;
    public static boolean allowHackingTextCanvas() {
        if (allowHackingTextCanvasCache == null) {
            allowHackingTextCanvasCache = Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH && (
                Build.MANUFACTURER == null ||
                !Build.MANUFACTURER.toLowerCase().contains("honor") &&
                !Build.MANUFACTURER.toLowerCase().contains("huawei") &&
                !Build.MANUFACTURER.toLowerCase().contains("alps")
            ) && (
                Build.MODEL == null ||
                !Build.MODEL.toLowerCase().contains("mediapad")
            );
        }
        return allowHackingTextCanvasCache;
    }

    public boolean wrapCanvasToFixClipping = allowHackingTextCanvas();
    private NoClipCanvas wrappedCanvas;

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        if (clipToPadding && getScrollY() != 0) {
            canvas.clipRect(-AndroidUtilities.dp(3), getScrollY() - super.getExtendedPaddingTop() - offsetY, getMeasuredWidth(), getMeasuredHeight() + getScrollY() + super.getExtendedPaddingBottom() - offsetY);
        }
        if (wrapCanvasToFixClipping) {
            if (wrappedCanvas == null) {
                wrappedCanvas = new NoClipCanvas();
            }
            wrappedCanvas.canvas = canvas;
            super.onDraw(wrappedCanvas);
        } else {
            super.onDraw(canvas);
        }
        canvas.restore();
    }

    public void updateAnimatedEmoji(boolean force) {
    }

    protected int emojiCacheType() {
        return 0;
    }

    public void invalidateQuotes(boolean force) {
    }

    // really dirty workaround to reset fontmetrics cache (lineheightspan.chooseheight works only when text is inserted into the respected line)
    protected void resetFontMetricsCache() {
        float originalTextSize = getTextSize();
        setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextSize + 1);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextSize);
    }

    public void invalidateEffects() {
        invalidate();
    }

    protected void invalidateSpoilers() {
        invalidate();
    }

    public void setClipToPadding(boolean clipToPadding) {
        this.clipToPadding = clipToPadding;
    }

    public CharSequence getTextToUse() {
        Editable text = getText();
        return text;
    }
}
