/*
 * This is the source code of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov and contributors.
 */
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** The TYPE_MESSAGE_EFFECTS branch of release-11.4.2-5469 ReactionsContainerLayout.
 * Retains upstream dispatchDraw/drawBubbles, hint, row and selected-state geometry;
 * MessageSendPreview supplies the original 116dp frame and 4/22/4/22dp padding.
 * Account, Premium, network and expanded custom-emoji selection are host concerns. */
public class ReactionsContainerLayout extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    /** The selected host snapshot, or null when the selected item is tapped again. */
    public interface Delegate { void onEffectSelected(MessageEffect effect); }
    private static final float CLIP_PROGRESS = .25f;
    private final Theme.ResourcesProvider resourcesProvider;
    private final RecyclerListView recyclerListView;
    private final Adapter adapter = new Adapter();
    private final TextView hintView;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();
    private final Drawable shadow;
    private final Set<ReactionHolderView> holders = Collections.newSetFromMap(new WeakHashMap<>());
    private List<MessageEffect> effects = Collections.emptyList();
    private Delegate delegate;
    private long selectedEffect;
    private float transitionProgress = 1f;
    private float bubblesOffset = dp(-25), miniBubblesOffset = dp(2);
    private boolean isTop, destroyed, observing;
    private ValueAnimator enterAnimator;

    public ReactionsContainerLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        // MessageSendPreview: 72 + 22 + 22, with a 20dp hint margin above the 52dp row.
        setPadding(dp(4), dp(22), dp(4), dp(22));
        setClipChildren(false);
        setClipToPadding(false);
        shadow = context.getResources().getDrawable(R.drawable.reactions_bubble_shadow).mutate();
        recyclerListView = new RecyclerListView(context, resourcesProvider);
        recyclerListView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        recyclerListView.setOverScrollMode(OVER_SCROLL_NEVER);
        recyclerListView.setAdapter(adapter);
        recyclerListView.setItemAnimator(null);
        // Upstream no-expanded-selector decorations: 6/4/6dp, plus the outer 8dp.
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(Rect out, View child, RecyclerView parent, RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(child);
                out.left = position == 0 ? dp(14) : 0;
                out.right = position == effects.size() - 1 ? dp(14) : dp(4);
            }
        });
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (destroyed || !(view instanceof ReactionHolderView) || position < 0 || position >= effects.size()) return;
            MessageEffect effect = ((ReactionHolderView) view).effect;
            if (effect == null || effects.get(position) != effect) return;
            setSelectedEffect(selectedEffect == effect.id ? 0 : effect.id);
            try { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING); } catch (Exception ignored) { }
            Delegate callback = delegate;
            if (callback != null) callback.onEffectSelected(selectedEffect == 0 ? null : effect);
        });
        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.TOP, 0, 20, 0, 0));
        hintView = new TextView(context);
        hintView.setPadding(dp(8), 0, dp(8), 0);
        hintView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        hintView.setText(R.string.AddEffectMessageHint);
        hintView.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 6, 0, 0));
        updateColors();
    }

    public void setEffects(List<MessageEffect> values) {
        if (destroyed) return;
        ArrayList<MessageEffect> copy = new ArrayList<>();
        HashSet<Long> ids = new HashSet<>();
        if (values != null) for (MessageEffect effect : values) {
            if (effect == null || !ids.add(effect.id)) throw new IllegalArgumentException("Effect ids must be unique and nonzero");
            copy.add(effect);
        }
        effects = Collections.unmodifiableList(copy);
        if (findEffect(selectedEffect) == null) selectedEffect = 0;
        // Drop old sources even before RecyclerView's next layout / rebind.
        for (ReactionHolderView holder : holders) holder.bind(null);
        adapter.notifyDataSetChanged();
        requestLayout();
    }
    public List<MessageEffect> getEffects() { return effects; }
    private MessageEffect findEffect(long id) {
        for (MessageEffect effect : effects) if (effect.id == id) return effect;
        return null;
    }
    /** Zero or an id absent from the current snapshot clears the selection without a callback. */
    public void setSelectedEffect(long id) {
        if (destroyed) return;
        selectedEffect = findEffect(id) == null ? 0 : id;
        for (ReactionHolderView holder : holders) holder.updateSelected(true);
    }
    public long getSelectedEffect() { return selectedEffect; }
    public void setDelegate(Delegate delegate) { if (!destroyed) this.delegate = delegate; }
    public RecyclerListView getListView() { return recyclerListView; }
    public void setTop(boolean top) { isTop = top; invalidate(); }
    public void setBubbleOffset(float offset) { bubblesOffset = offset; invalidate(); }
    public void setMiniBubblesOffset(float offset) { miniBubblesOffset = offset; invalidate(); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = dp(8 + 28 + 40 * effects.size() + 4 * Math.max(0, effects.size() - 1));
        desiredWidth = Math.max(desiredWidth, (int) Math.ceil(hintView.getPaint().measureText(hintView.getText().toString())) + dp(24));
        super.onMeasure(MeasureSpec.makeMeasureSpec(resolveSize(desiredWidth, widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(resolveSize(dp(116), heightMeasureSpec), MeasureSpec.EXACTLY));
        setPivotX(getMeasuredWidth()); setPivotY(getMeasuredHeight());
    }

    public void startEnterAnimation() {
        if (destroyed) return;
        cancelEnterAnimation();
        enterAnimator = ValueAnimator.ofFloat(0f, 1f);
        enterAnimator.setDuration(250);
        enterAnimator.setInterpolator(new OvershootInterpolator(.5f));
        enterAnimator.addUpdateListener(a -> { transitionProgress = (float) a.getAnimatedValue(); invalidate(); });
        enterAnimator.start();
    }
    private void cancelEnterAnimation() {
        if (enterAnimator != null) { enterAnimator.removeAllUpdateListeners(); enterAnimator.cancel(); enterAnimator = null; }
        transitionProgress = 1f;
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        // Upstream dispatchDraw, with expand / pressed / account branches removed.
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float lt = rtl ? 0 : 1f - Math.max(CLIP_PROGRESS, transitionProgress);
        float rt = rtl ? Math.max(CLIP_PROGRESS, transitionProgress) : 1f;
        float offset = (getWidth() - getPaddingRight()) * Math.min(1f, lt);
        rect.set(getPaddingLeft() + offset, getPaddingTop(), (getWidth() - getPaddingRight()) * rt, getHeight() - getPaddingBottom());
        float radius = Math.max(0, (rect.height() - dp(20)) / 2f);
        int save = canvas.save();
        float pivot = rtl ? 0 : getWidth();
        canvas.scale(transitionProgress, transitionProgress, pivot, getHeight() / 2f);
        shadow.setBounds((int) (getPaddingLeft() + (getWidth() - getPaddingRight() + dp(7)) * lt - dp(7)),
                getPaddingTop() - dp(7), (int) ((getWidth() - getPaddingRight() + dp(7)) * rt), getHeight() - getPaddingBottom() + dp(7));
        shadow.draw(canvas);
        canvas.drawRoundRect(rect, radius, radius, bgPaint);
        clipPath.rewind(); clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.translate((rtl ? -1 : 1) * getWidth() * (1f - transitionProgress), 0);
        recyclerListView.setTranslationX(-offset);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
        drawBubbles(canvas);
    }

    private void drawBubbles(Canvas canvas) {
        float progress = (Math.max(CLIP_PROGRESS, Math.min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS);
        float br = dp(8) * progress, sr = dp(4) * progress;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int save = canvas.save();
        if (isTop) canvas.clipRect(0, 0, getMeasuredWidth(), rect.top - (int) Math.ceil(rect.height() / 2f * (1f - transitionProgress)) + 1);
        else canvas.clipRect(0, rect.bottom - (int) Math.ceil(rect.height() / 2f * (1f - transitionProgress)) - 1, getMeasuredWidth(), getMeasuredHeight() + dp(8));
        float cx = (rtl ? dp(36) : getWidth() - dp(36)) + bubblesOffset;
        float cy = isTop ? getPaddingTop() : getHeight() - getPaddingBottom();
        int pad = dp(3);
        shadow.setBounds((int)(cx-br-pad*progress), (int)(cy-br-pad*progress), (int)(cx+br+pad*progress), (int)(cy+br+pad*progress));
        shadow.draw(canvas); canvas.drawCircle(cx, cy, br, bgPaint);
        cx = (rtl ? dp(36)-dp(8) : getWidth()-dp(36)+dp(8)) + bubblesOffset + miniBubblesOffset;
        cy = isTop ? getPaddingTop()-dp(16) : getHeight()-dp(4)-pad;
        pad = -dp(1);
        shadow.setBounds((int)(cx-br-pad*progress), (int)(cy-br-pad*progress), (int)(cx+br+pad*progress), (int)(cy+br+pad*progress));
        shadow.draw(canvas); canvas.drawCircle(cx, cy, sr, bgPaint);
        canvas.restoreToCount(save);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (destroyed || getAlpha() < .5f) return false;
        return super.dispatchTouchEvent(event);
    }
    public void updateColors() {
        bgPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
        selectedPaint.setColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
        hintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        shadow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        recyclerListView.setSelectorDrawableColor(0);
        for (ReactionHolderView holder : holders) holder.invalidate();
        invalidate();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!destroyed && !observing) {
            observing = true; NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        }
    }
    private void stopObserving() {
        if (observing) { observing = false; NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded); }
    }
    @Override protected void onDetachedFromWindow() {
        cancelEnterAnimation(); stopObserving();
        for (ReactionHolderView holder : holders) holder.releaseAnimation();
        super.onDetachedFromWindow();
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (!destroyed && id == NotificationCenter.emojiLoaded) for (ReactionHolderView holder : holders) holder.invalidate();
    }
    public void destroy() {
        if (destroyed) return;
        destroyed = true; cancelEnterAnimation(); stopObserving(); delegate = null;
        recyclerListView.setOnItemClickListener((RecyclerListView.OnItemClickListener) null);
        for (ReactionHolderView holder : holders) holder.bind(null);
        recyclerListView.setAdapter(null); holders.clear(); effects = Collections.emptyList(); selectedEffect = 0;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemCount() { return effects.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ReactionHolderView view = new ReactionHolderView(parent.getContext());
            holders.add(view);
            view.setLayoutParams(new RecyclerView.LayoutParams(dp(40), dp(52)));
            return new RecyclerListView.Holder(view);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) { ((ReactionHolderView) holder.itemView).bind(effects.get(position)); }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder) { ((ReactionHolderView) holder.itemView).bind(null); }
    }

    private final class ReactionHolderView extends View {
        private MessageEffect effect;
        private AnimatedEmojiDrawable animation;
        private Drawable fallback;
        private float iconScale = 1f;
        private boolean selected;
        private ValueAnimator selectedAnimator;
        ReactionHolderView(Context context) { super(context); setFocusable(true); }
        void bind(MessageEffect value) {
            if (animation != null) { animation.removeView(this); animation.recycle(); animation = null; }
            if (selectedAnimator != null) { selectedAnimator.cancel(); selectedAnimator = null; }
            effect = value; fallback = value == null ? null : Emoji.getEmojiDrawable(value.emoticon);
            if (value != null) {
                animation = new AnimatedEmojiDrawable(value.animationDocumentId, dp(34));
                syncAnimation();
            }
            setContentDescription(value == null ? null : value.emoticon);
            updateSelected(false);
        }
        void updateSelected(boolean animated) {
            boolean next = effect != null && effect.id == selectedEffect;
            if (selected == next && animated) return;
            selected = next; setSelected(next);
            if (selectedAnimator != null) { selectedAnimator.cancel(); selectedAnimator = null; }
            float target = selected ? .76f : 1f;
            if (animated && isAttachedToWindow()) {
                selectedAnimator = ValueAnimator.ofFloat(iconScale, target);
                selectedAnimator.setDuration(240); selectedAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                selectedAnimator.addUpdateListener(a -> { iconScale = (float) a.getAnimatedValue(); invalidate(); });
                selectedAnimator.start();
            } else iconScale = target;
            invalidate();
        }
        private void syncAnimation() {
            if (animation == null) return;
            if (!destroyed && isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE) animation.addView(this);
            else animation.removeView(this);
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation(); }
        @Override protected void onVisibilityChanged(View changedView, int visibility) { super.onVisibilityChanged(changedView, visibility); syncAnimation(); }
        @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); syncAnimation(); }
        void releaseAnimation() {
            if (selectedAnimator != null) { selectedAnimator.cancel(); selectedAnimator = null; }
            iconScale = selected ? .76f : 1f;
            if (animation != null) animation.removeView(this);
        }
        @Override protected void onDetachedFromWindow() {
            releaseAnimation();
            super.onDetachedFromWindow();
        }
        @Override protected void onDraw(Canvas canvas) {
            syncAnimation();
            if (selected) canvas.drawCircle(getWidth()/2f, getHeight()/2f, getWidth()/2f-dp(1), selectedPaint);
            Drawable drawable = animation != null && animation.isLoaded() ? animation : fallback;
            if (drawable == null) return;
            int size = Math.round(dp(34) * iconScale);
            drawable.setBounds((getWidth()-size)/2, (getHeight()-size)/2, (getWidth()+size)/2, (getHeight()+size)/2);
            drawable.draw(canvas);
        }
    }
}
