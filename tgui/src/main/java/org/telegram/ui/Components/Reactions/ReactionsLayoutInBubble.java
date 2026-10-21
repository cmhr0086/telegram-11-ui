package org.telegram.ui.Components.Reactions;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CounterView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * tgui: upstream ReactionsLayoutInBubble (Telegram 11.4.2) transplanted onto
 * {@link TextBubbleMessage.Reaction}: the 26dp reaction pills drawn inside a message bubble.
 * <p>
 * Kept verbatim: measure() (pill sizes, 4dp gaps, flow wrapping, lastLineX for the time),
 * draw() (IN = scale 0.5..1 + fade, OUT = the reverse, MOVE = position / width / colour lerp),
 * recordDrawingState() / animateChange() / resetAnimation(), ReactionButton.draw() colours
 * (chat_in/outReactionButton*, unselected background at 15.6% alpha), the CounterDrawable
 * TYPE_CHAT_REACTIONS counter with its replace animation, ButtonBounce on press and
 * checkTouchEvent() with the long-press timeout.
 * <p>
 * Not ported: TLRPC / server state, animated (lottie / custom) emoji via ImageReceiver, paid
 * (star) reactions and their particles, saved-message tags, the small (isSmall) mode, avatars
 * of recent reactors, the scrim preview counter. Emoji are static Telegram emoji ({@link Emoji}).
 * Buttons keep the model's order (upstream sorts chosen ones first in private chats).
 */
public class ReactionsLayoutInBubble {

    private final static int ANIMATION_TYPE_IN = 1;
    private final static int ANIMATION_TYPE_OUT = 2;
    private final static int ANIMATION_TYPE_MOVE = 3;
    /** 1 = pills on the wallpaper (emoji-only messages): chat_actionBackground paint and service colours. */
    public float drawServiceShaderBackground;

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    public static void initPaints(Theme.ResourcesProvider resourcesProvider) {
        paint.setColor(Theme.getColor(Theme.key_chat_inLoader, resourcesProvider));
        textPaint.setColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        textPaint.setTextSize(dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());
    }

    /** Pill tap / long press (ChatMessageCellDelegate.didPressReaction). */
    public interface Delegate {
        void didPressReaction(TextBubbleMessage.Reaction reaction, boolean longPress, float x, float y);
    }

    public int x;
    public int y;
    private float fromX;
    private float fromY;
    private float lastDrawnX;
    private float lastDrawnY;
    private boolean wasDrawn;
    private boolean animateMove;
    private boolean animateWidth;
    private boolean animateHeight;
    public int positionOffsetY;
    public int height;
    public int totalHeight;
    public int width;
    public int fromWidth;
    public boolean isEmpty = true;
    private final float touchSlop;
    public int lastLineX;
    public ArrayList<ReactionButton> reactionButtons = new ArrayList<>();
    ArrayList<ReactionButton> outButtons = new ArrayList<>();
    HashMap<String, ReactionButton> lastDrawingReactionButtons = new HashMap<>();
    HashMap<String, ReactionButton> lastDrawingReactionButtonsTmp = new HashMap<>();
    final View parentView;
    Theme.ResourcesProvider resourcesProvider;
    private boolean isOutOwner;
    private Delegate delegate;

    int availableWidth;
    private int lastDrawnWidth;
    private int lastDrawTotalHeight;
    private int animateFromTotalHeight;

    public ReactionsLayoutInBubble(View parentView) {
        this.parentView = parentView;
        initPaints(resourcesProvider);
        touchSlop = ViewConfiguration.get(parentView.getContext()).getScaledTouchSlop();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    /** upstream setMessage: one button per reaction with a count, reusing the old counter drawables. */
    public void setReactions(List<TextBubbleMessage.Reaction> reactions, boolean isOutOwner, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        this.isOutOwner = isOutOwner;
        ArrayList<ReactionButton> oldButtons = new ArrayList<>(reactionButtons);
        reactionButtons.clear();
        if (reactions != null) {
            for (int i = 0; i < reactions.size(); i++) {
                TextBubbleMessage.Reaction reactionCount = reactions.get(i);
                if (reactionCount == null || reactionCount.emoji == null || reactionCount.count <= 0) {
                    continue;
                }
                ReactionButton old = null;
                for (int j = 0; j < oldButtons.size(); ++j) {
                    ReactionButton btn = oldButtons.get(j);
                    if (btn.key.equals(reactionCount.emoji)) {
                        old = btn;
                        break;
                    }
                }
                reactionButtons.add(new ReactionButton(old, parentView, reactionCount, isOutOwner, resourcesProvider));
            }
        }
        isEmpty = reactionButtons.isEmpty();
    }

    public void measure(int availableWidth, int gravity) {
        height = 0;
        width = 0;
        positionOffsetY = 0;
        totalHeight = 0;
        if (isEmpty) {
            return;
        }
        this.availableWidth = availableWidth;
        int maxWidth = 0;
        int currentX = 0;
        int currentY = 0;
        for (int i = 0; i < reactionButtons.size(); i++) {
            ReactionButton button = reactionButtons.get(i);
            button.width = dp(8) + dp(20) + dp(4);
            button.width += button.counterDrawable.getCurrentWidth() + dp(8);
            button.height = dp(26);

            if (currentX + button.width > availableWidth) {
                currentX = 0;
                currentY += button.height + dp(4);
            }
            button.x = currentX;
            button.y = currentY;
            currentX += button.width + dp(4);
            if (currentX > maxWidth) {
                maxWidth = currentX;
            }
        }
        if (gravity == Gravity.RIGHT && !reactionButtons.isEmpty()) {
            int fromP = 0;
            int startY = reactionButtons.get(0).y;
            for (int i = 0; i < reactionButtons.size(); i++) {
                if (reactionButtons.get(i).y != startY) {
                    int lineOffset = availableWidth - (reactionButtons.get(i - 1).x + reactionButtons.get(i - 1).width);
                    for (int k = fromP; k < i; k++) {
                        reactionButtons.get(k).x += lineOffset;
                    }
                    fromP = i;
                }
            }
            int last = reactionButtons.size() - 1;
            int lineOffset = availableWidth - (reactionButtons.get(last).x + reactionButtons.get(last).width);
            for (int k = fromP; k <= last; k++) {
                reactionButtons.get(k).x += lineOffset;
            }
        }
        lastLineX = currentX;
        if (gravity == Gravity.RIGHT) {
            width = availableWidth;
        } else {
            width = maxWidth;
        }
        height = currentY + (reactionButtons.size() == 0 ? 0 : dp(26));
        drawServiceShaderBackground = 0f;
    }

    public void draw(Canvas canvas, float animationProgress) {
        if (isEmpty && outButtons.isEmpty()) {
            return;
        }
        float totalX = this.x;
        float totalY = this.y;
        if (isEmpty) {
            totalX = lastDrawnX;
            totalY = lastDrawnY;
        } else if (animateMove) {
            totalX = totalX * (animationProgress) + fromX * (1f - animationProgress);
            totalY = totalY * (animationProgress) + fromY * (1f - animationProgress);
        }
        for (int i = 0; i < reactionButtons.size(); i++) {
            ReactionButton reactionButton = reactionButtons.get(i);
            canvas.save();
            float x = reactionButton.x;
            float y = reactionButton.y;
            if (animationProgress != 1f && reactionButton.animationType == ANIMATION_TYPE_MOVE) {
                x = reactionButton.x * animationProgress + reactionButton.animateFromX * (1f - animationProgress);
                y = reactionButton.y * animationProgress + reactionButton.animateFromY * (1f - animationProgress);
            }
            float alpha = 1f;
            if (animationProgress != 1f && reactionButton.animationType == ANIMATION_TYPE_IN) {
                float s = 0.5f + 0.5f * animationProgress;
                alpha = animationProgress;
                canvas.scale(s, s, totalX + x + reactionButton.width / 2f, totalY + y + reactionButton.height / 2f);
            }
            reactionButton.drawServiceShaderBackground = drawServiceShaderBackground;
            reactionButton.draw(canvas, totalX + x, totalY + y, reactionButton.animationType == ANIMATION_TYPE_MOVE ? animationProgress : 1f, alpha);
            canvas.restore();
        }

        for (int i = 0; i < outButtons.size(); i++) {
            ReactionButton reactionButton = outButtons.get(i);
            float s = 0.5f + 0.5f * (1f - animationProgress);
            canvas.save();
            canvas.scale(s, s, totalX + reactionButton.x + reactionButton.width / 2f, totalY + reactionButton.y + reactionButton.height / 2f);
            reactionButton.drawServiceShaderBackground = drawServiceShaderBackground;
            outButtons.get(i).draw(canvas, totalX + reactionButton.x, totalY + reactionButton.y, 1f, (1f - animationProgress));
            canvas.restore();
        }
    }

    public void recordDrawingState() {
        lastDrawingReactionButtons.clear();
        for (int i = 0; i < reactionButtons.size(); i++) {
            lastDrawingReactionButtons.put(reactionButtons.get(i).key, reactionButtons.get(i));
        }
        wasDrawn = !isEmpty;
        lastDrawnX = x;
        lastDrawnY = y;
        lastDrawnWidth = width;
        lastDrawTotalHeight = totalHeight;
    }

    public boolean animateChange() {
        boolean changed = false;
        lastDrawingReactionButtonsTmp.clear();
        outButtons.clear();
        lastDrawingReactionButtonsTmp.putAll(lastDrawingReactionButtons);
        for (int i = 0; i < reactionButtons.size(); i++) {
            ReactionButton button = reactionButtons.get(i);
            ReactionButton lastButton = lastDrawingReactionButtonsTmp.get(button.key);
            if (lastButton != null) {
                lastDrawingReactionButtonsTmp.remove(button.key);
                if (button.x != lastButton.x || button.y != lastButton.y || button.width != lastButton.width || button.count != lastButton.count || button.choosen != lastButton.choosen) {
                    button.animateFromX = lastButton.x;
                    button.animateFromY = lastButton.y;
                    button.animateFromWidth = lastButton.width;

                    button.fromTextColor = lastButton.lastDrawnTextColor;
                    button.fromBackgroundColor = lastButton.lastDrawnBackgroundColor;
                    button.animationType = ANIMATION_TYPE_MOVE;

                    if (button.count != lastButton.count && button.counterDrawable != null) {
                        button.counterDrawable.setCount(lastButton.count, false);
                        button.counterDrawable.setCount(button.count, true);
                    }
                    changed = true;
                } else {
                    button.animationType = 0;
                }
            } else {
                changed = true;
                button.animationType = ANIMATION_TYPE_IN;
            }
        }
        if (!lastDrawingReactionButtonsTmp.isEmpty()) {
            changed = true;
            outButtons.addAll(lastDrawingReactionButtonsTmp.values());
        }

        if (wasDrawn && (lastDrawnX != x || lastDrawnY != y)) {
            animateMove = true;
            fromX = lastDrawnX;
            fromY = lastDrawnY;
            changed = true;
        }

        if (lastDrawnWidth != width) {
            animateWidth = true;
            fromWidth = lastDrawnWidth;
            changed = true;
        }

        if (lastDrawTotalHeight != totalHeight) {
            animateHeight = true;
            animateFromTotalHeight = lastDrawTotalHeight;
            changed = true;
        }

        return changed;
    }

    public void resetAnimation() {
        outButtons.clear();
        animateMove = false;
        animateWidth = false;
        animateHeight = false;
        for (int i = 0; i < reactionButtons.size(); i++) {
            reactionButtons.get(i).animationType = 0;
        }
    }

    public ReactionButton getReactionButton(String emoji) {
        return lastDrawingReactionButtons.get(emoji);
    }

    public static class ReactionButton {

        private final TextBubbleMessage.Reaction reactionCount;
        public int animationType;
        public int animateFromX;
        public int animateFromY;
        public int animateFromWidth;
        public int fromBackgroundColor;
        public int fromTextColor;
        public String key;
        public boolean choosen;

        public int count;
        public int x;
        public int y;
        public int width;
        public int height;
        public Drawable emojiDrawable;
        private final Rect drawingImageRect = new Rect();
        public CounterView.CounterDrawable counterDrawable;
        int backgroundColor;
        int textColor;
        int serviceBackgroundColor;
        int serviceTextColor;
        float drawServiceShaderBackground;

        public int lastDrawnTextColor;
        public int lastDrawnBackgroundColor;

        private final boolean isOutOwner;
        private final Theme.ResourcesProvider resourcesProvider;

        public final ButtonBounce bounce;

        public ReactionButton(ReactionButton reuseFrom, View parentView, TextBubbleMessage.Reaction reactionCount, boolean isOutOwner, Theme.ResourcesProvider resourcesProvider) {
            this.bounce = new ButtonBounce(parentView);
            this.resourcesProvider = resourcesProvider;
            this.isOutOwner = isOutOwner;
            if (reuseFrom != null) {
                counterDrawable = reuseFrom.counterDrawable;
                emojiDrawable = reuseFrom.emojiDrawable;
            }
            if (counterDrawable == null) {
                counterDrawable = new CounterView.CounterDrawable(parentView, false, null);
            }
            this.reactionCount = reactionCount;
            this.count = reactionCount.count;
            this.choosen = reactionCount.chosen;
            this.key = reactionCount.emoji;
            if (emojiDrawable == null) {
                emojiDrawable = Emoji.getEmojiDrawable(key);
                if (emojiDrawable == null) {
                    emojiDrawable = Emoji.getEmojiDrawable(Emoji.fixEmoji(key));
                }
            }
            counterDrawable.updateVisibility = false;
            counterDrawable.shortFormat = true;
            counterDrawable.setSize(dp(26), dp(100));
            counterDrawable.textPaint = textPaint;
            counterDrawable.setCount(count, false);
            counterDrawable.setType(CounterView.CounterDrawable.TYPE_CHAT_REACTIONS);
            counterDrawable.gravity = Gravity.LEFT;
        }

        public TextBubbleMessage.Reaction getReaction() {
            return reactionCount;
        }

        protected boolean drawCounter() {
            return count != 0 || counterDrawable.countChangeProgress != 1f;
        }

        public void draw(Canvas canvas, float x, float y, float progress, float alpha) {
            if (choosen) {
                backgroundColor = Theme.getColor(isOutOwner ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground, resourcesProvider);
                textColor = Theme.getColor(isOutOwner ? Theme.key_chat_outReactionButtonTextSelected : Theme.key_chat_inReactionButtonTextSelected, resourcesProvider);
                serviceTextColor = Theme.getColor(isOutOwner ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground, resourcesProvider);
                serviceBackgroundColor = Theme.getColor(isOutOwner ? Theme.key_chat_outBubble : Theme.key_chat_inBubble, resourcesProvider);
            } else {
                textColor = Theme.getColor(isOutOwner ? Theme.key_chat_outReactionButtonText : Theme.key_chat_inReactionButtonText, resourcesProvider);
                backgroundColor = Theme.getColor(isOutOwner ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground, resourcesProvider);
                backgroundColor = ColorUtils.setAlphaComponent(backgroundColor, (int) (Color.alpha(backgroundColor) * 0.156f));
                serviceTextColor = Theme.getColor(Theme.key_chat_serviceText, resourcesProvider);
                serviceBackgroundColor = Color.TRANSPARENT;
            }
            updateColors(progress);
            textPaint.setColor(lastDrawnTextColor);
            paint.setColor(lastDrawnBackgroundColor);

            if (alpha != 1f) {
                textPaint.setAlpha((int) (textPaint.getAlpha() * alpha));
                paint.setAlpha((int) (paint.getAlpha() * alpha));
            }

            final float bounceScale = bounce.getScale(0.1f);
            boolean restore = false;
            int w = width;
            if (progress != 1f && animationType == ANIMATION_TYPE_MOVE) {
                w = (int) (width * progress + animateFromWidth * (1f - progress));
            }
            AndroidUtilities.rectTmp.set(x, y, x + w, y + height);
            if (bounceScale != 1f) {
                restore = true;
                canvas.save();
                canvas.scale(bounceScale, bounceScale, x + w / 2f, y + height / 2f);
            }
            float rad = height / 2f;
            if (drawServiceShaderBackground > 0) {
                Paint paint1 = Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider);
                Paint paint2 = Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken, resourcesProvider);
                int oldAlpha = paint1.getAlpha();
                int oldAlpha2 = paint2.getAlpha();
                paint1.setAlpha((int) (oldAlpha * alpha * drawServiceShaderBackground));
                paint2.setAlpha((int) (oldAlpha2 * alpha * drawServiceShaderBackground));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint1);
                if (Theme.hasGradientService()) {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint2);
                }
                paint1.setAlpha(oldAlpha);
                paint2.setAlpha(oldAlpha2);
            }
            canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint);

            if (emojiDrawable != null) {
                int size = dp(20);
                int X = dp(8);
                int Y = (int) ((height - size) / 2f);
                drawingImageRect.set((int) x + X, (int) y + Y, (int) x + X + size, (int) y + Y + size);
                emojiDrawable.setBounds(drawingImageRect);
                emojiDrawable.setAlpha((int) (0xFF * alpha));
                emojiDrawable.draw(canvas);
                emojiDrawable.setAlpha(0xFF);
            }

            if (counterDrawable != null && drawCounter()) {
                canvas.save();
                canvas.translate(x + dp(8) + dp(20) + dp(2), y);
                counterDrawable.draw(canvas);
                canvas.restore();
            }

            if (restore) {
                canvas.restore();
            }
        }

        protected void updateColors(float progress) {
            lastDrawnTextColor = ColorUtils.blendARGB(fromTextColor, ColorUtils.blendARGB(textColor, serviceTextColor, drawServiceShaderBackground), progress);
            lastDrawnBackgroundColor = ColorUtils.blendARGB(fromBackgroundColor, ColorUtils.blendARGB(backgroundColor, serviceBackgroundColor, drawServiceShaderBackground), progress);
        }
    }

    float lastX;
    float lastY;
    ReactionButton lastSelectedButton;
    public boolean pressed;
    Runnable longPressRunnable;

    public boolean checkTouchEvent(MotionEvent event) {
        if (isEmpty) {
            return false;
        }
        float x = event.getX() - this.x;
        float y = event.getY() - this.y;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            for (int i = 0, n = reactionButtons.size(); i < n; i++) {
                if (x > reactionButtons.get(i).x && x < reactionButtons.get(i).x + reactionButtons.get(i).width &&
                    y > reactionButtons.get(i).y && y < reactionButtons.get(i).y + reactionButtons.get(i).height) {
                    lastX = event.getX();
                    lastY = event.getY();
                    lastSelectedButton = reactionButtons.get(i);
                    if (longPressRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                        longPressRunnable = null;
                    }
                    lastSelectedButton.bounce.setPressed(true);

                    final ReactionButton selectedButtonFinal = lastSelectedButton;
                    AndroidUtilities.runOnUIThread(longPressRunnable = () -> {
                        if (delegate != null) {
                            parentView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                            delegate.didPressReaction(selectedButtonFinal.reactionCount, true, 0, 0);
                        }
                        selectedButtonFinal.bounce.setPressed(false);
                        lastSelectedButton = null;
                        pressed = false;
                        longPressRunnable = null;
                    }, ViewConfiguration.getLongPressTimeout());
                    pressed = true;
                    break;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressed && Math.abs(event.getX() - lastX) > touchSlop || Math.abs(event.getY() - lastY) > touchSlop) {
                pressed = false;
                if (lastSelectedButton != null) {
                    lastSelectedButton.bounce.setPressed(false);
                }
                lastSelectedButton = null;
                if (longPressRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    longPressRunnable = null;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (longPressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                longPressRunnable = null;
            }
            if (pressed && lastSelectedButton != null && event.getAction() == MotionEvent.ACTION_UP) {
                if (delegate != null) {
                    delegate.didPressReaction(lastSelectedButton.reactionCount, false, event.getX(), event.getY());
                }
            }
            pressed = false;
            if (lastSelectedButton != null) {
                lastSelectedButton.bounce.setPressed(false);
            }
            lastSelectedButton = null;
        }
        return pressed;
    }

    public float getCurrentWidth(float transitionProgress) {
        if (animateWidth) {
            return fromWidth * (1f - transitionProgress) + width * transitionProgress;
        }
        return width;
    }

    public float getCurrentTotalHeight(float transitionProgress) {
        if (animateHeight) {
            return animateFromTotalHeight * (1f - transitionProgress) + totalHeight * transitionProgress;
        }
        return totalHeight;
    }
}
