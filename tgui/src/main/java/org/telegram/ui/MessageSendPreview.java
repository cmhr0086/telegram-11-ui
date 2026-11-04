/*
 * This is the source code of Telegram for Android v. 11.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * Copyright Nikolai Kudashov, 2013-2024.
 * Adapted from release-11.4.2-5469 MessageSendPreview.
 */
package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.graphics.Matrix;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.*;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.MessageEffect;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.RecyclerListView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/** Model-driven adaptation of the upstream send preview. All content belongs to the host. */
public class MessageSendPreview extends Dialog {
    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout windowView;
    private final FrameLayout content;
    private final RecyclerListView chatListView;
    private final RecyclerView.Adapter<Holder> adapter;
    private final ArrayList<TextBubbleMessage> messages = new ArrayList<>();
    private final ArrayList<MessageEffect> effects = new ArrayList<>();
    private final ReactionsContainerLayout effectSelector;
    private final EffectView effectOverlay;
    private ChatActivityEnterView.SendButton anchorSendButton, sendButton;
    private View optionsView;
    private View.OnClickListener sendListener;
    private ValueAnimator animator;
    private Bitmap blurBitmap;
    private BitmapShader blurShader;
    private final Matrix blurMatrix = new Matrix();
    private ColorMatrixColorFilter blurColorFilter;
    private float openProgress;
    private boolean dismissing, disposed, sent, fillWhenClose, sendDelivered;
    private static final WeakHashMap<View, AnchorOwner> anchorOwners = new WeakHashMap<>();
    private static final class AnchorOwner {
        final MessageSendPreview owner;
        final float alpha;
        AnchorOwner(MessageSendPreview owner, float alpha) { this.owner = owner; this.alpha = alpha; }
    }
    private int insetTop, insetBottom;
    private float anchorAlpha = 1;
    private float anchorX, anchorY, targetX, targetY;

    public MessageSendPreview(Context context, Theme.ResourcesProvider provider) {
        super(context, R.style.TransparentDialog);
        resourcesProvider = provider;
        windowView = new FrameLayout(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (dismissing || disposed) return true;
                return super.dispatchTouchEvent(event);
            }
            @Override protected void dispatchDraw(Canvas canvas) {
                if (blurBitmap != null && !blurBitmap.isRecycled()) {
                    paint.setAlpha((int)(255 * openProgress));
                    float scale = getWidth() / (float) blurBitmap.getWidth();
                    blurMatrix.reset(); blurMatrix.setScale(scale, scale);
                    blurShader.setLocalMatrix(blurMatrix);
                    paint.setShader(blurShader); paint.setColorFilter(blurColorFilter);
                    canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                } else {
                    canvas.drawColor(((int)(openProgress * 0x66) << 24));
                }
                super.dispatchDraw(canvas);
            }
        };
        windowView.setOnClickListener(v -> dismiss(false));
        content = new FrameLayout(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
                int w = getMeasuredWidth(), h = getMeasuredHeight();
                int available = Math.max(0, h - insetTop - insetBottom - dp(16));
                int selectorHeight = effectSelector.getVisibility() == View.GONE ? 0 : effectSelector.getMeasuredHeight();
                int buttonHeight = sendButton == null ? dp(48) : sendButton.getMeasuredHeight();
                int reserveList = messages.isEmpty() ? 0 : dp(48);
                if (optionsView != null && optionsView.getVisibility() != View.GONE) {
                    int max = Math.max(0, available - selectorHeight - buttonHeight - reserveList);
                    optionsView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
                }
                int optionsHeight = optionsView == null || optionsView.getVisibility() == View.GONE ? 0 : optionsView.getMeasuredHeight();
                int listMax = Math.max(0, available - selectorHeight - optionsHeight - (messages.isEmpty() ? 0 : buttonHeight));
                chatListView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(listMax, View.MeasureSpec.AT_MOST));
            }
            @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                layoutPreview();
            }
        };
        content.setOnClickListener(v -> dismiss(false));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        windowView.addView(content, new FrameLayout.LayoutParams(-1, -1));
        chatListView = new RecyclerListView(context, provider) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        chatListView.setLayoutManager(new LinearLayoutManager(context));
        chatListView.setClipToPadding(false);
        adapter = new RecyclerView.Adapter<Holder>() {
            @Override public int getItemCount() { return messages.size(); }
            @Override public int getItemViewType(int position) { return messages.get(position).type; }
            @Override public Holder onCreateViewHolder(ViewGroup parent, int type) { return new Holder(createCell(type)); }
            @Override public void onBindViewHolder(Holder holder, int position) { holder.cell.setMessage(messages.get(position), false, false); }
            @Override public void onViewRecycled(Holder holder) { holder.cell.setMessage(null, false, false); }
        };
        chatListView.setAdapter(adapter);
        content.addView(chatListView, new FrameLayout.LayoutParams(-1, -2));
        effectSelector = new ReactionsContainerLayout(context, provider);
        effectSelector.setDelegate(effect -> {
            if (disposed || dismissing || !isShowing()) return;
            long id = effect == null ? 0 : effect.id;
            effectSelector.setSelectedEffect(id);
            showEffect(effect);
            onEffectChange(id);
        });
        content.addView(effectSelector, new FrameLayout.LayoutParams(-2, -2));
        effectSelector.setVisibility(View.GONE);
        effectOverlay = new EffectView(context);
        windowView.addView(effectOverlay, new FrameLayout.LayoutParams(-1, -1));
        effectOverlay.setClickable(false);
        windowView.setFitsSystemWindows(true);
        windowView.setOnApplyWindowInsetsListener((view, insets) -> {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            content.requestLayout();
            return insets.consumeSystemWindowInsets();
        });
    }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private final class Holder extends RecyclerView.ViewHolder {
        final TextBubbleCell cell;
        Holder(TextBubbleCell cell) { super(cell); this.cell = cell; cell.setDrawTime(false); }
    }
    private TextBubbleCell createCell(int type) {
        Context c = getContext();
        switch (type) {
            case TextBubbleMessage.TYPE_VOICE: return new VoiceBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_PHOTO: return new PhotoBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_DOCUMENT: return new DocumentBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_ALBUM: return new AlbumBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_ROUND_VIDEO: return new RoundVideoBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_STICKER: return new StickerBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_GIF: return new GifBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_MUSIC: return new MusicBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_CONTACT: return new ContactBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_LOCATION: return new LocationBubbleCell(c, resourcesProvider);
            case TextBubbleMessage.TYPE_POLL: return new PollBubbleCell(c, resourcesProvider);
            default: return new TextBubbleCell(c, resourcesProvider);
        }
    }
    public void setMessages(List<TextBubbleMessage> values) {
        if (disposed) return;
        messages.clear();
        if (values != null) for (TextBubbleMessage value : values) if (value != null) messages.add(value);
        adapter.notifyDataSetChanged();
        content.requestLayout();
    }
    public void setSendButton(ChatActivityEnterView.SendButton anchor, boolean fillWhenClose, View.OnClickListener listener) {
        if (disposed) return;
        if (sendButton != null) content.removeView(sendButton);
        releaseAnchor();
        anchorSendButton = anchor; this.fillWhenClose = fillWhenClose; sendListener = listener;
        if (anchor == null) { sendButton = null; return; }
        anchorAlpha = anchor.getAlpha();
        sendButton = new ChatActivityEnterView.SendButton(getContext(), anchor.resId, resourcesProvider) {
            @Override public boolean isInScheduleMode() { return anchor.isInScheduleMode(); }
            @Override public boolean isOpen() { return MessageSendPreview.this.fillWhenClose ? !dismissing : true; }
            @Override public boolean isInactive() { return anchor.isInactive(); }
            @Override public boolean shouldDrawBackground() { return anchor.shouldDrawBackground(); }
            @Override public int getFillColor() { return anchor.getFillColor(); }
        };
        anchor.copyCountTo(sendButton); anchor.copyEmojiTo(sendButton);
        sendButton.center = anchor.center;
        sendButton.open.set(anchor.open.get(), true);
        sendButton.setOnClickListener(v -> {
            if (disposed || dismissing || sendDelivered || !isShowing()) return;
            sendDelivered = true;
            if (sendListener != null) sendListener.onClick(v);
        });
        content.addView(sendButton, new FrameLayout.LayoutParams(Math.max(1, anchor.getWidth()), Math.max(1, anchor.getHeight())));
        if (isShowing() && !dismissing) claimAnchor();
        content.requestLayout();
    }
    public void setOptionsView(View view) {
        if (disposed) return;
        if (optionsView != null) content.removeView(optionsView);
        optionsView = view;
        if (view != null) {
            if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
            content.addView(view, new FrameLayout.LayoutParams(-2, -2));
        }
        content.requestLayout();
    }
    public void setEffects(List<MessageEffect> values) {
        if (disposed) return;
        List<MessageEffect> snapshot = values == null ? Collections.emptyList() : new ArrayList<>(values);
        effectSelector.setEffects(snapshot);
        effects.clear(); effects.addAll(effectSelector.getEffects());
        syncSelectedEffect();
        effectSelector.setVisibility(effects.isEmpty() ? View.GONE : View.VISIBLE);
        content.requestLayout();
    }
    public void setEffectId(long id) {
        if (disposed) return;
        effectSelector.setSelectedEffect(id);
        syncSelectedEffect();
    }
    private void syncSelectedEffect() {
        long id = effectSelector.getSelectedEffect();
        MessageEffect selected = null;
        for (MessageEffect effect : effects) if (effect.id == id) { selected = effect; break; }
        effectOverlay.setEffect(selected);
    }
    public long getSelectedEffect() { return effectSelector.getSelectedEffect(); }
    protected void onEffectChange(long effectId) { }
    public RecyclerListView getChatListView() { return chatListView; }
    public ChatActivityEnterView.SendButton getSendButton() { return sendButton; }
    public ReactionsContainerLayout getEffectSelector() { return effectSelector; }
    public View getEffectOverlay() { return effectOverlay; }
    /** False as soon as dismissal starts, including during the exit animation. */
    public boolean isInteractive() { return !disposed && !dismissing && isShowing(); }

    private void layoutPreview() {
        if (content.getWidth() == 0 || sendButton == null || anchorSendButton == null) return;
        int[] anchor = new int[2], root = new int[2];
        anchorSendButton.getLocationOnScreen(anchor); content.getLocationOnScreen(root);
        float x = anchor[0] - root[0], y = anchor[1] - root[1];
        int optionsHeight = optionsView == null || optionsView.getVisibility() == View.GONE ? 0 : optionsView.getMeasuredHeight();
        int up = chatListView.getMeasuredHeight() - sendButton.getMeasuredWidth()
                + (effectSelector.getVisibility() == View.GONE ? 0 : effectSelector.getMeasuredHeight());
        int down = dp(messages.isEmpty() ? -6 : 48) + optionsHeight;
        int bottom = content.getHeight() - dp(8) - insetBottom;
        y = Math.min(y, bottom - down);
        y = Math.max(y, insetTop + dp(8) + up);
        y = Math.min(y, bottom - down);
        anchorX = x; anchorY = anchor[1] - root[1]; targetX = x; targetY = y;
        positionSendButton();
        chatListView.setX(x + dp(7) - chatListView.getMeasuredWidth());
        chatListView.setY(y + sendButton.getMeasuredWidth() - chatListView.getMeasuredHeight());
        if (optionsView != null && optionsView.getVisibility() != View.GONE) { optionsView.setX(x + dp(7) - optionsView.getMeasuredWidth()); optionsView.setY(y + (messages.isEmpty() ? -dp(6) : sendButton.getMeasuredHeight())); }
        effectSelector.setX(Math.max(0, x + sendButton.getMeasuredWidth() - effectSelector.getMeasuredWidth() - dp(6)));
        effectSelector.setY(Math.max(insetTop, chatListView.getY() - effectSelector.getMeasuredHeight()));
    }
    private void positionSendButton() {
        if (sendButton == null) return;
        sendButton.setX(anchorX + (targetX - anchorX) * openProgress);
        sendButton.setY(anchorY + (targetY - anchorY) * openProgress);
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(windowView, new ViewGroup.LayoutParams(-1, -1));
        WindowManager.LayoutParams p = window.getAttributes();
        p.width = -1; p.height = -1; p.gravity = Gravity.FILL; p.dimAmount = 0;
        p.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        p.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(p);
        windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_VISIBLE);
    }
    @Override public void show() {
        if (disposed) return;
        if (isShowing()) return;
        captureBlur(); dismissing = false; sent = false; sendDelivered = false;
        super.show();
        claimAnchor();
        animateOpenTo(true);
        if (!effects.isEmpty()) effectSelector.startEnterAnimation();
    }
    @Override public void onBackPressed() { dismiss(false); }
    @Override public void dismiss() { dismiss(false); }
    public void dismiss(boolean sent) {
        if (dismissing || disposed) return;
        this.sent = sent; dismissing = true;
        animateOpenTo(false);
    }
    public void dismissImmediately() { if (!disposed) finishDismiss(); }
    private void animateOpenTo(boolean open) {
        if (animator != null) { animator.removeAllListeners(); animator.cancel(); }
        animator = ValueAnimator.ofFloat(openProgress, open ? 1 : 0);
        animator.setDuration(350); animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.addUpdateListener(a -> { openProgress = (float)a.getAnimatedValue();
            chatListView.setAlpha(openProgress);
            effectSelector.setAlpha(openProgress);
            if (optionsView != null) optionsView.setAlpha(openProgress);
            effectOverlay.setAlpha(openProgress);
            positionSendButton(); windowView.invalidate(); });
        animator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { animator = null; if (!open) finishDismiss(); } });
        animator.start();
    }
    private void finishDismiss() {
        if (disposed) return;
        disposed = true;
        if (animator != null) { animator.removeAllListeners(); animator.cancel(); animator = null; }
        effectOverlay.clear(); effectSelector.destroy(); chatListView.setAdapter(null);
        releaseAnchor();
        if (blurBitmap != null) { blurBitmap.recycle(); blurBitmap = null; }
        blurShader = null;
        super.dismiss();
    }
    private void claimAnchor() {
        if (anchorSendButton == null) return;
        AnchorOwner previous = anchorOwners.get(anchorSendButton);
        anchorAlpha = previous == null ? anchorSendButton.getAlpha() : previous.alpha;
        anchorOwners.put(anchorSendButton, new AnchorOwner(this, anchorAlpha));
        anchorSendButton.setAlpha(0);
    }
    private void releaseAnchor() {
        if (anchorSendButton == null) return;
        AnchorOwner owner = anchorOwners.get(anchorSendButton);
        if (owner != null && owner.owner == this) {
            anchorOwners.remove(anchorSendButton);
            anchorSendButton.setAlpha(owner.alpha);
        }
    }
    private void captureBlur() {
        Context c = getContext();
        while (c instanceof ContextWrapper && !(c instanceof Activity)) c = ((ContextWrapper)c).getBaseContext();
        if (!(c instanceof Activity)) return;
        View source = ((Activity)c).getWindow().getDecorView();
        if (source.getWidth() <= 0 || source.getHeight() <= 0) return;
        try {
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, source.getWidth() / 14), Math.max(1, source.getHeight() / 14), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            canvas.scale(1f / 14, 1f / 14); source.draw(canvas);
            Utilities.stackBlurBitmap(bitmap, Math.max(14, Math.max(bitmap.getWidth(), bitmap.getHeight()) / 180));
            if (blurBitmap != null) blurBitmap.recycle(); blurBitmap = bitmap;
            blurShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            boolean dark = resourcesProvider == null ? Theme.isCurrentThemeDark() : resourcesProvider.isDark();
            ColorMatrix colors = new ColorMatrix();
            AndroidUtilities.adjustSaturationColorMatrix(colors, dark ? .08f : .25f);
            AndroidUtilities.adjustBrightnessColorMatrix(colors, dark ? -.02f : -.07f);
            blurColorFilter = new ColorMatrixColorFilter(colors);
        } catch (RuntimeException | OutOfMemoryError ignored) { }
    }
    private void showEffect(MessageEffect effect) { effectOverlay.setEffect(effect); }
    private final class EffectView extends View {
        private AnimatedEmojiDrawable drawable;
        private MessageEffect effect;
        private int previousFrame = -1;
        private final android.graphics.PointF timePosition = new android.graphics.PointF();
        EffectView(Context context) { super(context); }
        void setEffect(MessageEffect value) {
            releaseDrawable();
            previousFrame = -1;
            effect = value;
            if (isAttachedToWindow()) loadDrawable();
        }
        private void loadDrawable() {
            if (effect == null || effect.animationDocumentId == 0 || drawable != null) return;
            int side = AndroidUtilities.isTablet() ? (int)(AndroidUtilities.getMinTabletSide() * .4f) : (int)(Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * .5f);
            int size = Math.max(1, (int)(2f * side / 1.3f));
            drawable = new AnimatedEmojiDrawable(effect.animationDocumentId, size);
            drawable.addView(this); invalidate();
        }
        private void releaseDrawable() {
            if (drawable != null) { drawable.removeView(this); drawable.recycle(); drawable = null; invalidate(); }
        }
        void clear() { effect = null; releaseDrawable(); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); loadDrawable(); }
        @Override protected void onDetachedFromWindow() { releaseDrawable(); super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (drawable == null) return;
            int frame = drawable.getCurrentFrame();
            if (previousFrame > 0 && frame < previousFrame) {
                clear();
                return;
            }
            previousFrame = frame;
            int[] pos = new int[2], root = new int[2];
            View cell = chatListView.getChildCount() == 0 ? null : chatListView.getChildAt(chatListView.getChildCount()-1);
            if (cell == null) return;
            cell.getLocationOnScreen(pos); getLocationOnScreen(root);
            int size = drawable.getIntrinsicWidth();
            boolean hasTime = cell instanceof TextBubbleCell && ((TextBubbleCell) cell).getTimePosition(timePosition);
            float timeX = hasTime ? timePosition.x : cell.getWidth() - dp(44);
            float timeY = hasTime ? timePosition.y : cell.getHeight() - dp(10);
            int x = Math.max(0, Math.min(getWidth()-size, (int)(pos[0]-root[0]+timeX-size/2f)));
            int y = (int)(pos[1]-root[1]+timeY-size/2f);
            drawable.setBounds(x,y,x+size,y+size); drawable.draw(canvas);
        }
    }
}
