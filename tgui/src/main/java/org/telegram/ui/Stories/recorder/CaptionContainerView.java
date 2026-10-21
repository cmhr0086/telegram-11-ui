package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.tgui.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;

import org.telegram.ui.Components.ButtonBounce;

import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextCaption;

import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;

import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SizeNotifierFrameLayout;



/**
 * tgui: only {@link PeriodDrawable} (the "view once" / timer badge used by the voice recording
 * controls) is kept from upstream CaptionContainerView, copied verbatim.
 */
public class CaptionContainerView {

    public static class PeriodDrawable extends Drawable {

        public final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final AnimatedTextView.AnimatedTextDrawable textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, false, false) {
            @Override
            public void invalidateSelf() {
                PeriodDrawable.this.invalidateSelf();
            }
        };
        public final AnimatedTextView.AnimatedTextDrawable activeTextDrawable = new AnimatedTextView.AnimatedTextDrawable(true, false, false) {
            @Override
            public void invalidateSelf() {
                PeriodDrawable.this.invalidateSelf();
            }
        };

        private boolean filled = false;
        private final AnimatedFloat fillT = new AnimatedFloat(this::invalidateSelf, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final Path activePath = new Path();
        private final int dashes;
        public float diameterDp = 21;

        public PeriodDrawable() {
            this(5);
        }

        public PeriodDrawable(int dashes) {
            this.dashes = dashes;

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dpf2(1.66f));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);

            textDrawable.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
            textDrawable.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            textDrawable.setTextSize(dpf2(12));
            textDrawable.setGravity(Gravity.CENTER);

            activeTextDrawable.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
            activeTextDrawable.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            activeTextDrawable.setTextSize(dpf2(12));
            activeTextDrawable.setGravity(Gravity.CENTER);

            updateColors(0xffffffff, 0xff1A9CFF, 0xffffffff);
        }

        public void setTextSize(float dp) {
            activeTextDrawable.setTextSize(dpf2(dp));
            textDrawable.setTextSize(dpf2(dp));
        }

        public float textOffsetX, textOffsetY;

        public void updateColors(int strokeColor, int fillColor, int activeTextColor) {
            strokePaint.setColor(strokeColor);
            textDrawable.setTextColor(strokeColor);
            activeTextDrawable.setTextColor(activeTextColor);
            fillPaint.setColor(fillColor);
        }

        private boolean clear;
        public void setClear(boolean clear) {
            if (this.clear != clear) {
                this.clear = clear;
                strokePaint.setXfermode(clear ? new PorterDuffXfermode(PorterDuff.Mode.CLEAR) : null);
                textDrawable.getPaint().setXfermode(clear ? new PorterDuffXfermode(PorterDuff.Mode.CLEAR) : null);
            }
        }

        private float cx, cy;
        public void setCenterXY(float x, float y) {
            this.cx = x;
            this.cy = y;
        }

        @Override
        public void setBounds(@NonNull Rect bounds) {
            super.setBounds(bounds);
            this.cx = getBounds().centerX();
            this.cy = getBounds().centerY();
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            this.cx = getBounds().centerX();
            this.cy = getBounds().centerY();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            draw(canvas, 1f);
        }
        public void draw(@NonNull Canvas canvas, float alpha) {
            final float r = dpf2(diameterDp) / 2f;
            final float fillT = this.fillT.set(filled);

            if (fillT > 0) {
                fillPaint.setAlpha((int) (0xFF * alpha * fillT));
                canvas.drawCircle(cx, cy, dpf2(11.33f) * fillT, fillPaint);
            }

            strokePaint.setAlpha((int) (0xFF * alpha * (1f - fillT)));
            AndroidUtilities.rectTmp.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(AndroidUtilities.rectTmp, 90, 180, false, strokePaint);

            final int gaps = dashes + 1;
            final float dashWeight = 1f, gapWeight = 1.5f;
            final float dashSweep = dashWeight / (dashes * dashWeight + gaps * gapWeight) * 180;
            final float gapSweep = gapWeight / (dashes * dashWeight + gaps * gapWeight) * 180;
            float a = gapSweep;
            for (int i = 0; i < dashes; ++i) {
                canvas.drawArc(AndroidUtilities.rectTmp, 270 + a, dashSweep, false, strokePaint);
                a += dashSweep + gapSweep;
            }

            canvas.save();
            canvas.translate(textOffsetX + 0, textOffsetY);
            AndroidUtilities.rectTmp2.set(
                (int) (cx - dp(20)),
                (int) (cy - dp(20)),
                (int) (cx + dp(20)),
                (int) (cy + dp(20))
            );
            textDrawable.setBounds(AndroidUtilities.rectTmp2);
            textDrawable.setAlpha((int) (0xFF * alpha));
            textDrawable.draw(canvas);
            if (fillT > 0) {
                activePath.rewind();
                activePath.addCircle(cx, cy + dp(1), dpf2(11.33f) * fillT, Path.Direction.CW);
                canvas.clipPath(activePath);
                activeTextDrawable.setBounds(AndroidUtilities.rectTmp2);
                activeTextDrawable.setAlpha((int) (0xFF * alpha));
                activeTextDrawable.draw(canvas);
            }
            canvas.restore();
        }

        public void setValue(int num, boolean fill, boolean animated) {
            textDrawable.setText("" + num, animated);
            activeTextDrawable.setText("" + num, animated);
            filled = fill;
            if (!animated) {
                fillT.set(filled, true);
            }
            invalidateSelf();
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public int getIntrinsicHeight() {
            return dp(24);
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(24);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }
}
