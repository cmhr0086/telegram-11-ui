/*
 * Adapted from Telegram for Android, release-11.4.2-5469.
 * Copyright Nikolai Kudashov and contributors. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in the project root.
 */
package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;

/** A visual undo prompt. The host owns the operation and both completion callbacks. */
public class UndoView extends FrameLayout {
    public static final int ACTION_DELETE = 1;
    public static final int ACTION_ARCHIVE = 2;
    public static final int ACTION_ARCHIVE_FEW = 4;
    public static final int ACTION_DELETE_FEW = 27;
    private static final long DURATION = 5000;

    private final Theme.ResourcesProvider resourcesProvider;
    private final TextView infoTextView;
    private final TextView undoTextView;
    private final ImageView undoImageView;
    private final RLottieImageView leftImageView;
    private final LinearLayout undoButton;
    private final Drawable backgroundDrawable;
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF(AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(33), AndroidUtilities.dp(33));
    private int undoViewHeight = AndroidUtilities.dp(52);
    private int enterOffsetMargin = AndroidUtilities.dp(8);
    private float enterOffset;
    private float additionalTranslationY;
    private int currentAction;
    private long deadline;
    private long generation;
    private boolean pending;
    private boolean detaching;
    private Runnable actionRunnable;
    private Runnable cancelRunnable;
    private Runnable timeoutRunnable;
    private AnimatorSet visibilityAnimator;
    private int prevSeconds = -1;
    private int textWidth;
    private StaticLayout timeLayout;
    private StaticLayout timeLayoutOut;
    private float timeReplaceProgress = 1;

    public UndoView(Context context) {
        this(context, null);
    }

    public UndoView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        infoTextView.setGravity(Gravity.LEFT | Gravity.TOP);
        addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 45, 13, 0, 0));

        leftImageView = new RLottieImageView(context);
        leftImageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(leftImageView, LayoutHelper.createFrame(54, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 3, 0, 0, 0));

        undoButton = new LinearLayout(context);
        undoButton.setOrientation(LinearLayout.HORIZONTAL);
        addView(undoButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 11, 0));
        undoButton.setOnClickListener(v -> hide(false, 1));
        undoImageView = new ImageView(context);
        undoImageView.setImageResource(R.drawable.chats_undo);
        undoButton.addView(undoImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 4, 4, 0, 4));
        undoTextView = new TextView(context);
        undoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        undoTextView.setTypeface(AndroidUtilities.bold());
        undoTextView.setText(R.string.Undo);
        undoButton.addView(undoTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 6, 4, 8, 4));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());
        backgroundDrawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(10), getColor(Theme.key_undo_background));
        setWillNotDraw(false);
        setOnTouchListener((v, event) -> true);
        setVisibility(INVISIBLE);
        updateColors();
    }

    public void updateColors() {
        int background = getColor(Theme.key_undo_background);
        int text = getColor(Theme.key_undo_infoColor);
        int cancel = getColor(Theme.key_undo_cancelColor);
        Theme.setDrawableColor(backgroundDrawable, background);
        infoTextView.setTextColor(text);
        undoTextView.setTextColor(cancel);
        undoImageView.setColorFilter(new PorterDuffColorFilter(cancel, PorterDuff.Mode.MULTIPLY));
        undoButton.setBackground(Theme.createRadSelectorDrawable(cancel & 0x22ffffff, AndroidUtilities.dp(2), AndroidUtilities.dp(2)));
        progressPaint.setColor(text);
        textPaint.setColor(text);
        leftImageView.setLayerColor("info1.**", background | 0xff000000);
        leftImageView.setLayerColor("info2.**", background | 0xff000000);
        leftImageView.setLayerColor("Oval.**", text);
        for (int i = 1; i <= 12; i++) leftImageView.setLayerColor("luc" + i + ".**", text);
        invalidate();
    }

    public View getUndoButton() { return undoButton; }
    public boolean hasPendingAction() { return pending; }

    public void showWithAction(int action, CharSequence text, Runnable apply, Runnable undo) {
        if (action != ACTION_DELETE && action != ACTION_DELETE_FEW && action != ACTION_ARCHIVE && action != ACTION_ARCHIVE_FEW) {
            throw new IllegalArgumentException("Unsupported undo action: " + action);
        }
        long request = ++generation;
        if (pending) settle(true, 0);
        // A completion callback can synchronously show a newer operation.
        if (generation != request) {
            // The incoming operation has already happened in the host. A reentrant newer
            // prompt supersedes it, so commit it rather than silently dropping its callback.
            if (apply != null) apply.run();
            return;
        }
        if (detaching) {
            if (apply != null) apply.run();
            return;
        }
        cancelVisibilityAnimation();
        currentAction = action;
        pending = true;
        actionRunnable = apply;
        cancelRunnable = undo;
        deadline = SystemClock.elapsedRealtime() + DURATION;
        prevSeconds = -1;
        timeLayout = timeLayoutOut = null;
        timeReplaceProgress = 1;
        boolean archive = action == ACTION_ARCHIVE || action == ACTION_ARCHIVE_FEW;
        if (text == null) {
            int stringId = action == ACTION_ARCHIVE ? R.string.ChatArchived : action == ACTION_ARCHIVE_FEW ? R.string.ChatsArchived : action == ACTION_DELETE_FEW ? R.string.ChatsDeletedUndo : R.string.ChatDeletedUndo;
            infoTextView.setText(stringId);
        } else {
            infoTextView.setText(text);
        }
        infoTextView.setTypeface(Typeface.DEFAULT);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) infoTextView.getLayoutParams();
        lp.leftMargin = AndroidUtilities.dp(archive ? 58 : 45);
        lp.topMargin = AndroidUtilities.dp(13);
        infoTextView.setLayoutParams(lp);
        leftImageView.setVisibility(archive ? VISIBLE : GONE);
        if (archive) {
            leftImageView.setAnimation(R.raw.chats_archived, 36, 36);
            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        }
        requestLayout();
        setVisibility(VISIBLE);
        setAlpha(1);
        setScaleX(1);
        setScaleY(1);
        setEnterOffset(enterOffsetMargin + undoViewHeight);
        animateOffset(-1, 180, false);
        final long token = generation;
        timeoutRunnable = () -> {
            if (!pending || generation != token) return;
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining > 0) {
                AndroidUtilities.runOnUIThread(timeoutRunnable, remaining);
            } else {
                hide(true, 1);
            }
        };
        AndroidUtilities.runOnUIThread(timeoutRunnable, DURATION);
        invalidate();
    }

    public void hide(boolean apply, int animated) {
        if (!pending) return;
        ++generation;
        settle(apply, animated);
    }

    private void settle(boolean apply, int animated) {
        if (!pending) return;
        pending = false;
        if (timeoutRunnable != null) AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
        timeoutRunnable = null;
        Runnable callback = apply ? actionRunnable : cancelRunnable;
        actionRunnable = cancelRunnable = null;
        cancelVisibilityAnimation();
        if (animated == 0) {
            setVisibility(INVISIBLE);
        } else if (animated == 1) {
            animateOffset(enterOffsetMargin + undoViewHeight, 250, true);
        } else {
            AnimatorSet animator = new AnimatorSet();
            visibilityAnimator = animator;
            animator.playTogether(ObjectAnimator.ofFloat(this, View.SCALE_X, 0.8f), ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.8f), ObjectAnimator.ofFloat(this, View.ALPHA, 0));
            animator.setDuration(180);
            animator.setInterpolator(new DecelerateInterpolator());
            finishWhenDone(animator);
            animator.start();
        }
        if (callback != null) callback.run();
    }

    private void animateOffset(float target, long duration, boolean hideAtEnd) {
        AnimatorSet animator = new AnimatorSet();
        visibilityAnimator = animator;
        animator.playTogether(ObjectAnimator.ofFloat(this, "enterOffset", target));
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        if (hideAtEnd) finishWhenDone(animator);
        animator.start();
    }

    private void finishWhenDone(AnimatorSet animator) {
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (visibilityAnimator != animator) return;
                visibilityAnimator = null;
                setVisibility(INVISIBLE);
                setScaleX(1);
                setScaleY(1);
                setAlpha(1);
            }
        });
    }

    private void cancelVisibilityAnimation() {
        AnimatorSet old = visibilityAnimator;
        visibilityAnimator = null;
        if (old != null) old.cancel();
    }

    @Override protected void onDetachedFromWindow() {
        detaching = true;
        if (pending) hide(true, 0);
        cancelVisibilityAnimation();
        setVisibility(INVISIBLE);
        setScaleX(1);
        setScaleY(1);
        setAlpha(1);
        super.onDetachedFromWindow();
        detaching = false;
    }

    @Keep public float getEnterOffset() { return enterOffset; }
    @Keep public void setEnterOffset(float value) {
        enterOffset = value;
        updatePosition();
    }

    public void setAdditionalTranslationY(float value) {
        additionalTranslationY = value;
        updatePosition();
    }

    private void updatePosition() {
        setTranslationY(enterOffset - enterOffsetMargin + AndroidUtilities.dp(8) - additionalTranslationY);
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (getParent() instanceof ViewGroup && width > 0) {
            measureChildWithMargins(undoButton, widthMeasureSpec, 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
            FrameLayout.LayoutParams buttonParams = (FrameLayout.LayoutParams) undoButton.getLayoutParams();
            int buttonWidth = undoButton.getMeasuredWidth() + buttonParams.leftMargin + buttonParams.rightMargin;
            // FrameLayout measures children again in super.onMeasure; reserve this space in
            // LayoutParams as well so that pass cannot expand the label over the button.
            ((FrameLayout.LayoutParams) infoTextView.getLayoutParams()).rightMargin = buttonWidth;
            measureChildWithMargins(infoTextView, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
            undoViewHeight = infoTextView.getMeasuredHeight() + AndroidUtilities.dp(28);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(undoViewHeight, MeasureSpec.EXACTLY));
        backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (additionalTranslationY != 0) {
            canvas.save();
            float bottom = getMeasuredHeight() - enterOffset + AndroidUtilities.dp(9);
            if (bottom > 0) {
                canvas.clipRect(0, 0, getMeasuredWidth(), bottom);
                super.dispatchDraw(canvas);
            }
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (additionalTranslationY != 0) {
            canvas.save();
            float bottom = getMeasuredHeight() - enterOffset + enterOffsetMargin + AndroidUtilities.dp(1);
            if (bottom > 0) {
                canvas.clipRect(0, 0, getMeasuredWidth(), bottom);
                super.dispatchDraw(canvas);
            }
            backgroundDrawable.draw(canvas);
            canvas.restore();
        } else {
            backgroundDrawable.draw(canvas);
        }
        if (!pending) return;
        long remaining = Math.max(0, deadline - SystemClock.elapsedRealtime());
        if (remaining == 0) {
            // Drawing can resume after sleep before the Handler's uptime-based delay expires.
            hide(true, 1);
            return;
        }
        if (currentAction == ACTION_DELETE || currentAction == ACTION_DELETE_FEW) {
            int seconds = remaining > 0 ? (int) Math.ceil(remaining / 1000.0f) : 0;
            if (prevSeconds != seconds) {
                prevSeconds = seconds;
                if (timeLayout != null) {
                    timeLayoutOut = timeLayout;
                    timeReplaceProgress = 0;
                }
                String value = String.format(java.util.Locale.getDefault(), "%d", Math.max(1, seconds));
                textWidth = (int) Math.ceil(textPaint.measureText(value));
                timeLayout = new StaticLayout(value, textPaint, Integer.MAX_VALUE, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            if (timeReplaceProgress < 1f) timeReplaceProgress = Math.min(1f, timeReplaceProgress + 16f / 150f);
            int alpha = textPaint.getAlpha();
            if (timeLayoutOut != null && timeReplaceProgress < 1f) {
                textPaint.setAlpha((int) (alpha * (1f - timeReplaceProgress)));
                canvas.save();
                canvas.translate(rect.centerX() - textWidth / 2f, AndroidUtilities.dp(17.2f) + AndroidUtilities.dp(10) * timeReplaceProgress);
                timeLayoutOut.draw(canvas);
                canvas.restore();
                textPaint.setAlpha(alpha);
            }
            if (timeLayout != null) {
                textPaint.setAlpha((int) (alpha * timeReplaceProgress));
                canvas.save();
                canvas.translate(rect.centerX() - textWidth / 2f, AndroidUtilities.dp(17.2f) - AndroidUtilities.dp(10) * (1f - timeReplaceProgress));
                timeLayout.draw(canvas);
                canvas.restore();
                textPaint.setAlpha(alpha);
            }
            canvas.drawArc(rect, -90, -360 * (remaining / (float) DURATION), false, progressPaint);
        }
        invalidate();
    }

    private int getColor(int key) { return Theme.getColor(key, resourcesProvider); }
}
