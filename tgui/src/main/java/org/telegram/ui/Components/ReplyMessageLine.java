package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * tgui: upstream ReplyMessageLine for plain models. The colour comes from a colour id
 * (0..6 = {@link Theme#keys_avatar_nameInMessage}, -1 = the theme's reply line colour) instead of
 * the replied-to peer; peer colours with two / three stripes, background emoji and the loading
 * shimmer are left out. {@link #drawLine} / {@link #drawBackground} are upstream's.
 */
public class ReplyMessageLine {

    private final RectF rectF = new RectF();
    private final Path clipPath = new Path();
    private final Paint color1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public final float[] radii = new float[8];
    private final Path backgroundPath = new Path();
    public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public int backgroundColor, nameColor, color1;
    public final AnimatedColor backgroundColorAnimated;
    public final AnimatedColor color1Animated;
    public final AnimatedColor nameColorAnimated;

    public ReplyMessageLine(View view) {
        backgroundColorAnimated = new AnimatedColor(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
        color1Animated = new AnimatedColor(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
        nameColorAnimated = new AnimatedColor(view, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public int getColor() {
        return color1;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * upstream check(..., TYPE_REPLY)
     * @param colorId 0..6 picks a peer name colour for incoming bubbles, -1 the theme reply colours
     * @return the (animated) name colour
     */
    public int check(int colorId, boolean out, Theme.ResourcesProvider resourcesProvider) {
        final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        if (colorId >= 0 && colorId < Theme.keys_avatar_nameInMessage.length) {
            color1 = Theme.getColor(Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
            backgroundColor = Theme.multAlpha(color1, 0.10f);
            nameColor = color1;
        } else {
            color1 = Theme.getColor(Theme.key_chat_inReplyLine, resourcesProvider);
            backgroundColor = Theme.multAlpha(color1, 0.10f);
            nameColor = Theme.getColor(Theme.key_chat_inReplyNameText, resourcesProvider);
        }
        if (out) {
            color1 = Theme.getColor(Theme.key_chat_outReplyLine, resourcesProvider);
            backgroundColor = Theme.multAlpha(color1, dark ? 0.12f : 0.10f);
            nameColor = Theme.getColor(Theme.key_chat_outReplyNameText, resourcesProvider);
        }
        return nameColorAnimated.set(nameColor);
    }

    public void resetAnimation() {
        color1Animated.set(color1, true);
        nameColorAnimated.set(nameColor, true);
        backgroundColorAnimated.set(backgroundColor, true);
    }

    public void drawLine(Canvas canvas, RectF rect) {
        drawLine(canvas, rect, 1f);
    }

    public void drawLine(Canvas canvas, RectF rect, float alpha) {
        canvas.save();

        clipPath.rewind();
        final int rad = (int) Math.floor(SharedConfig.bubbleRadius / 3f);
        rectF.set(rect.left, rect.top, rect.left + Math.max(dp(3), dp(2 * rad)), rect.bottom);
        clipPath.addRoundRect(rectF, dp(rad), dp(rad), Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.clipRect(rect.left, rect.top, rect.left + dp(3), rect.bottom);

        color1Paint.setColor(Theme.multAlpha(color1Animated.set(color1), alpha));
        canvas.drawPaint(color1Paint);

        canvas.restore();
    }

    public void drawBackground(Canvas canvas, RectF rect, float leftRad, float rightRad, float bottomRad, float alpha) {
        radii[0] = radii[1] = Math.max(AndroidUtilities.dp((int) Math.floor(SharedConfig.bubbleRadius / 3f)), AndroidUtilities.dp(leftRad));
        radii[2] = radii[3] = AndroidUtilities.dp(rightRad);
        radii[4] = radii[5] = AndroidUtilities.dp(bottomRad);
        radii[6] = radii[7] = Math.max(AndroidUtilities.dp((int) Math.floor(SharedConfig.bubbleRadius / 3f)), AndroidUtilities.dp(bottomRad));

        backgroundPath.rewind();
        backgroundPath.addRoundRect(rect, radii, Path.Direction.CW);

        backgroundPaint.setColor(backgroundColorAnimated.set(backgroundColor));
        backgroundPaint.setAlpha((int) (backgroundPaint.getAlpha() * alpha));
        canvas.drawPath(backgroundPath, backgroundPaint);
    }
}
