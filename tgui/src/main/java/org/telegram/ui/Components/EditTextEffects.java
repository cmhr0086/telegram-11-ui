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
 * Local custom emoji render through AnimatedEmojiSpan; the existing clipping workarounds
 * also apply to their ReplacementSpan draw calls.
 */
public class EditTextEffects extends EditText {

    public boolean suppressOnTextChanged;
    public int quoteColor;
    public boolean drawAnimatedEmojiDrawables = true;
    private boolean clipToPadding;
    private AnimatedEmojiSpan.EmojiGroupedSpans animatedEmojiSpans;

    public EditTextEffects(Context context) {
        super(context);
    }

    public void recycleEmojis() {
        AnimatedEmojiSpan.release(this, animatedEmojiSpans);
        animatedEmojiSpans = null;
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
        updateAnimatedEmoji(false);
        canvas.save();
        if (clipToPadding && getScrollY() != 0) {
            canvas.clipRect(-AndroidUtilities.dp(3), getScrollY() - super.getExtendedPaddingTop() - offsetY, getMeasuredWidth(), getMeasuredHeight() + getScrollY() + super.getExtendedPaddingBottom() - offsetY);
        }
        boolean previousEmojiDrawing = AnimatedEmojiSpan.setDrawingEnabled(drawAnimatedEmojiDrawables);
        try {
            // A Canvas proxy prevents Editor from caching an animated ReplacementSpan in a RenderNode.
            // With the clipping workaround disabled we still delegate clipping normally.
            if (wrapCanvasToFixClipping || animatedEmojiSpans != null && !animatedEmojiSpans.isEmpty()) {
                if (wrappedCanvas == null) {
                    wrappedCanvas = new NoClipCanvas();
                }
                wrappedCanvas.canvas = canvas;
                wrappedCanvas.preserveClipping = !wrapCanvasToFixClipping;
                super.onDraw(wrappedCanvas);
            } else {
                super.onDraw(canvas);
            }
        } finally {
            AnimatedEmojiSpan.setDrawingEnabled(previousEmojiDrawing);
            canvas.restore();
        }
    }

    public void updateAnimatedEmoji(boolean force) {
        if (!drawAnimatedEmojiDrawables || !isAttachedToWindow()) {
            recycleEmojis();
            return;
        }
        if (force) {
            AnimatedEmojiSpan.applyFontMetricsForString(getText(), getPaint());
        }
        animatedEmojiSpans = AnimatedEmojiSpan.update(emojiCacheType(), this, animatedEmojiSpans, getLayout());
        if (force) invalidate();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        AnimatedEmojiSpan.applyFontMetricsForString(text, getPaint());
    }

    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);
        AnimatedEmojiSpan.applyFontMetricsForString(getText(), getPaint());
        requestLayout();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimatedEmoji(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        recycleEmojis();
        super.onDetachedFromWindow();
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
