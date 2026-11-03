/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2013-2018.
 */
// SuggestEmojiView, cropped from Telegram release-11.4.2-5469; host supplies candidate snapshots.
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgui.recyclerview.DefaultItemAnimator;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Upstream arrow popup and horizontal emoji list, with host-owned search and selection. */
public class SuggestEmojiView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    public static final int DIRECTION_TO_BOTTOM = 0, DIRECTION_TO_TOP = 1;
    public interface Delegate {
        void onEmojiSelected(EmojiSuggestion suggestion);
        default void onEmojiLongPressed(EmojiSuggestion suggestion) { }
    }
    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout containerView;
    private final RecyclerListView listView;
    private final Adapter adapter;
    private final Path path = new Path(), circlePath = new Path();
    private final RectF rect = new RectF(), touchRect = new RectF();
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedFloat showFloat1, showFloat2, leftGradientAlpha, rightGradientAlpha, arrowXAnimated, listViewCenterAnimated, listViewWidthAnimated;
    private List<EmojiSuggestion> suggestions = Collections.emptyList();
    private Delegate delegate;
    private boolean show, destroyed, revealClipped, trackingTouch, observingEmoji;
    private int direction = DIRECTION_TO_BOTTOM;
    private int horizontalPadding = AndroidUtilities.dp(10);
    private float arrowX;

    public SuggestEmojiView(Context context) { this(context, null); }
    public SuggestEmojiView(Context context, Theme.ResourcesProvider provider) {
        super(context); resourcesProvider = provider;
        setVisibility(GONE); setClipChildren(false); setClipToPadding(false);
        backgroundPaint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(2)));
        backgroundPaint.setShadowLayer(AndroidUtilities.dp(4.33f), 0, AndroidUtilities.dp(1/3f), 0x33000000);
        containerView = new FrameLayout(context) {
            @Override protected void dispatchDraw(Canvas canvas) {
                if (!drawContainerBegin(canvas)) return;
                super.dispatchDraw(canvas); drawContainerEnd(canvas);
            }
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                setPadding(horizontalPadding, direction == DIRECTION_TO_BOTTOM ? AndroidUtilities.dp(8) : AndroidUtilities.dp(6.66f), horizontalPadding,
                        direction == DIRECTION_TO_BOTTOM ? AndroidUtilities.dp(6.66f) : AndroidUtilities.dp(8));
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        showFloat1 = new AnimatedFloat(0f, containerView, 120, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        showFloat2 = new AnimatedFloat(0f, containerView, 150, 600, CubicBezierInterpolator.EASE_OUT_QUINT);
        leftGradientAlpha = new AnimatedFloat(containerView, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        rightGradientAlpha = new AnimatedFloat(containerView, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
        arrowXAnimated = new AnimatedFloat(containerView, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        listViewCenterAnimated = new AnimatedFloat(containerView, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        listViewWidthAnimated = new AnimatedFloat(containerView, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        listView = new RecyclerListView(context, provider) {
            @Override public void onScrolled(int dx, int dy) { super.onScrolled(dx,dy); containerView.invalidate(); }
        };
        LinearLayoutManager manager = new LinearLayoutManager(context); manager.setOrientation(RecyclerView.HORIZONTAL); listView.setLayoutManager(manager);
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setAddDuration(45); animator.setMoveDuration(45); animator.setChangeDuration(45); animator.setRemoveDuration(45);
        animator.setTranslationInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT); listView.setItemAnimator(animator);
        adapter = new Adapter(); listView.setAdapter(adapter);
        listView.setOnItemClickListener((RecyclerListView.OnItemClickListener)(view,position) -> {
            EmojiSuggestion item = validItem(view, position); if (item != null && delegate != null) delegate.onEmojiSelected(item);
        });
        listView.setOnItemLongClickListener((RecyclerListView.OnItemLongClickListener)(view,position) -> {
            EmojiSuggestion item = validItem(view, position); if (item == null || delegate == null) return false;
            delegate.onEmojiLongPressed(item); return true;
        });
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52));
        addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 66.66f, Gravity.BOTTOM, 0,0,0,0));
        updateColors();
    }
    private EmojiSuggestion validItem(View view, int position) {
        if (!show || destroyed || !(view instanceof EmojiImageView) || position < 0 || position >= suggestions.size()) return null;
        EmojiSuggestion item = ((EmojiImageView)view).suggestion;
        return item == suggestions.get(position) ? item : null;
    }
    public void setDelegate(Delegate value) { delegate = value; }
    public RecyclerListView getListView() { return listView; }
    public List<EmojiSuggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<EmojiSuggestion> values) {
        if (destroyed) return;
        ArrayList<EmojiSuggestion> copy = new ArrayList<>(); HashSet<String> ids = new HashSet<>();
        if (values != null) for (EmojiSuggestion item : values) {
            if (item == null || !ids.add(item.id)) throw new IllegalArgumentException("emoji suggestion ids must be unique");
            copy.add(item);
        }
        suggestions = Collections.unmodifiableList(copy); adapter.notifyDataSetChanged();
        if (suggestions.isEmpty()) hide(); else if (show) containerView.invalidate();
    }
    /** The arrow position in this view's coordinate system. */
    public void setAnchor(float x) {
        if (Float.isNaN(x) || Float.isInfinite(x) || Float.compare(arrowX, x) == 0) return;
        arrowX = x; containerView.invalidate();
    }
    /** Optional vertical translation of the popup relative to this view; hosts may instead position the parent. */
    public void setAnchor(float x, float yOffset) {
        if (Float.isNaN(yOffset) || Float.isInfinite(yOffset)) return;
        setAnchor(x);
        if (Float.compare(containerView.getTranslationY(), yOffset) != 0) containerView.setTranslationY(yOffset);
    }
    public int getDirection() { return direction; }
    public void setDirection(int value) {
        if (value != DIRECTION_TO_BOTTOM && value != DIRECTION_TO_TOP) throw new IllegalArgumentException("invalid direction");
        if (direction != value) { direction = value; requestLayout(); adapter.notifyDataSetChanged(); containerView.invalidate(); }
    }
    public void setHorizontalPadding(int padding) { horizontalPadding = Math.max(0,padding); requestLayout(); }
    public boolean isOpen() { return show; }
    @Override public boolean isShown() { return show; }
    public void show() {
        if (destroyed || suggestions.isEmpty()) return;
        show = true; setVisibility(VISIBLE); containerView.setVisibility(VISIBLE); updateAttachments(); containerView.invalidate();
    }
    public void hide() {
        if (destroyed || !show && getVisibility() == GONE) return;
        if (trackingTouch) {
            long now = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_CANCEL, 0, 0, 0);
            super.dispatchTouchEvent(cancel); cancel.recycle(); trackingTouch = false;
        }
        show = false; updateAttachments(); containerView.invalidate();
        if (!isAttachedToWindow()) { containerView.setVisibility(GONE); setVisibility(GONE); }
    }
    public void updateColors() {
        int color = Theme.getColor(Theme.key_chat_stickersHintPanel, resourcesProvider);
        backgroundPaint.setColor(color);
        listView.setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
        containerView.invalidate();
    }
    private void updateAttachments() {
        for (int i=0; i<listView.getChildCount(); i++) if (listView.getChildAt(i) instanceof EmojiImageView) ((EmojiImageView)listView.getChildAt(i)).updateAttachment();
    }
    public void destroy() {
        if (destroyed) return;
        hide(); show=false; updateAttachments(); destroyed=true; delegate=null;
        stopObservingEmoji();
        listView.setOnItemClickListener((RecyclerListView.OnItemClickListener)null);
        listView.setOnItemLongClickListener((RecyclerListView.OnItemLongClickListener)null);
        listView.setAdapter(null); suggestions=Collections.emptyList(); containerView.setVisibility(GONE); setVisibility(GONE);
    }
    @Override protected void onDetachedFromWindow() { stopObservingEmoji(); super.onDetachedFromWindow(); updateAttachments(); }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!destroyed && !observingEmoji) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
            observingEmoji = true;
        }
        invalidateEmojiCells(); updateAttachments();
    }
    private void stopObservingEmoji() {
        if (observingEmoji) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
            observingEmoji = false;
        }
    }
    private void invalidateEmojiCells() {
        for (int i = 0; i < listView.getChildCount(); i++) listView.getChildAt(i).invalidate();
        listView.invalidate(); containerView.invalidate();
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (!destroyed && id == NotificationCenter.emojiLoaded) invalidateEmojiCells();
    }

    // drawContainerBegin/End retain the upstream rounded path, 8.66 x 6.66dp arrow,
    // circular reveal and scroll-edge gradients; host supplies arrow X instead of EditText internals.
    private boolean drawContainerBegin(Canvas canvas) {
        float showT1=showFloat1.set(show ? 1f : 0f), showT2=showFloat2.set(show ? 1f : 0f);
        if(showT1<=0 && showT2<=0 && !show) { containerView.setVisibility(GONE); setVisibility(GONE); return false; }
        float panelWidth = Math.max(1, getWidth());
        float requestedArrow = Math.max(AndroidUtilities.dp(10), Math.min(panelWidth - AndroidUtilities.dp(10), arrowX));
        float animatedArrow=arrowXAnimated.set(requestedArrow);
        path.rewind();
        float listLeft=listView.getLeft(), listRight=listLeft+suggestions.size()*AndroidUtilities.dp(44);
        boolean force=listViewWidthAnimated.get()<=0;
        float width=listRight-listLeft<=0 ? listViewWidthAnimated.get() : listViewWidthAnimated.set(listRight-listLeft,force);
        float center=listViewCenterAnimated.set((listLeft+listRight)/2f,force);
        int paddingLeft=(int)Math.max(requestedArrow-Math.max(width/4f,Math.min(width/2f,AndroidUtilities.dp(66)))-listView.getLeft(),0);
        if(listView.getPaddingLeft()!=paddingLeft){int dx=listView.getPaddingLeft()-paddingLeft;listView.setPadding(paddingLeft,0,0,0);listView.scrollBy(dx,0);}
        int animatedPadding=(int)Math.max(animatedArrow-Math.max(width/4f,Math.min(width/2f,AndroidUtilities.dp(66)))-listView.getLeft(),0);
        listView.setTranslationX(animatedPadding-paddingLeft);
        float left=center-width/2f+listView.getPaddingLeft()+listView.getTranslationX();
        float top=listView.getTop()+listView.getTranslationY()+listView.getPaddingTop()+(direction==DIRECTION_TO_BOTTOM ? 0 : AndroidUtilities.dp(6.66f));
        float right=Math.min(center+width/2f+listView.getPaddingLeft()+listView.getTranslationX(),getWidth()-containerView.getPaddingRight());
        float bottom=listView.getBottom()+listView.getTranslationY()-(direction==DIRECTION_TO_BOTTOM ? AndroidUtilities.dp(6.66f) : 0);
        if(right<=left || bottom<=top) { touchRect.setEmpty(); return false; }
        touchRect.set(left + containerView.getX(), top + containerView.getY(), right + containerView.getX(), listView.getBottom() + containerView.getY());
        float arrow=Math.max(left+AndroidUtilities.dp(9),Math.min(right-AndroidUtilities.dp(9),animatedArrow));
        float R=Math.min(AndroidUtilities.dp(9),Math.min(width/2f,(right-left)/2f)),D=R*2;
        if(direction==DIRECTION_TO_BOTTOM){
            rect.set(left,bottom-D,left+D,bottom);path.arcTo(rect,90,90);
            rect.set(left,top,left+D,top+D);path.arcTo(rect,-180,90);
            rect.set(right-D,top,right,top+D);path.arcTo(rect,-90,90);
            rect.set(right-D,bottom-D,right,bottom);path.arcTo(rect,0,90);
            path.lineTo(arrow+AndroidUtilities.dp(8.66f),bottom);path.lineTo(arrow,bottom+AndroidUtilities.dp(6.66f));path.lineTo(arrow-AndroidUtilities.dp(8.66f),bottom);
        }else{
            rect.set(right-D,top,right,top+D);path.arcTo(rect,-90,90);
            rect.set(right-D,bottom-D,right,bottom);path.arcTo(rect,0,90);
            rect.set(left,bottom-D,left+D,bottom);path.arcTo(rect,90,90);
            rect.set(left,top,left+D,top+D);path.arcTo(rect,-180,90);
            path.lineTo(arrow-AndroidUtilities.dp(8.66f),top);path.lineTo(arrow,top-AndroidUtilities.dp(6.66f));path.lineTo(arrow+AndroidUtilities.dp(8.66f),top);
        }
        path.close(); revealClipped=showT1<1f;
        if(revealClipped){
            circlePath.rewind();float cy=direction==DIRECTION_TO_BOTTOM ? bottom+AndroidUtilities.dp(6.66f) : top-AndroidUtilities.dp(6.66f);
            float radius=(float)Math.sqrt(Math.max(Math.max(Math.pow(arrow-left,2)+Math.pow(cy-top,2),Math.pow(arrow-right,2)+Math.pow(cy-top,2)),Math.max(Math.pow(arrow-left,2)+Math.pow(cy-bottom,2),Math.pow(arrow-right,2)+Math.pow(cy-bottom,2))));
            circlePath.addCircle(arrow,cy,radius*showT1,Path.Direction.CW);canvas.save();canvas.clipPath(circlePath);canvas.saveLayerAlpha(0,0,getWidth(),getHeight(),(int)(255*showT1),Canvas.ALL_SAVE_FLAG);
        }
        canvas.drawPath(path,backgroundPaint);canvas.save();canvas.clipPath(path);return true;
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        int action = event.getActionMasked();
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            trackingTouch = show && touchRect.contains(event.getX(), event.getY());
            if (!trackingTouch) return false;
        } else if (!trackingTouch) return false;
        if (action == android.view.MotionEvent.ACTION_MOVE && !touchRect.contains(event.getX(), event.getY())) {
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(event); cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel); cancel.recycle(); trackingTouch = false; return false;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) trackingTouch = false;
        return handled;
    }

    private void drawContainerEnd(Canvas canvas) {
        float width=listViewWidthAnimated.get(),center=listViewCenterAnimated.get();
        float left=center-width/2f+listView.getPaddingLeft()+listView.getTranslationX(),top=listView.getTop()+listView.getPaddingTop();
        float right=Math.min(center+width/2f+listView.getPaddingLeft()+listView.getTranslationX(),getWidth()-containerView.getPaddingRight()),bottom=listView.getBottom();
        int color=Theme.getColor(Theme.key_chat_stickersHintPanel,resourcesProvider);
        float a=leftGradientAlpha.set(listView.canScrollHorizontally(-1)?1f:0f);
        if(a>0 && Theme.chat_gradientRightDrawable!=null){Drawable d=Theme.chat_gradientRightDrawable;d.setColorFilter(new PorterDuffColorFilter(color,PorterDuff.Mode.MULTIPLY));d.setBounds((int)left,(int)top,(int)left+AndroidUtilities.dp(32),(int)bottom);d.setAlpha((int)(255*a));d.draw(canvas);d.setAlpha(255);}
        a=rightGradientAlpha.set(listView.canScrollHorizontally(1)?1f:0f);
        if(a>0 && Theme.chat_gradientLeftDrawable!=null){Drawable d=Theme.chat_gradientLeftDrawable;d.setColorFilter(new PorterDuffColorFilter(color,PorterDuff.Mode.MULTIPLY));d.setBounds((int)right-AndroidUtilities.dp(32),(int)top,(int)right,(int)bottom);d.setAlpha((int)(255*a));d.draw(canvas);d.setAlpha(255);}
        canvas.restore();if(revealClipped){canvas.restore();canvas.restore();}
    }
    public final class EmojiImageView extends View {
        private EmojiSuggestion suggestion;
        public Drawable drawable;
        public EmojiSuggestion getSuggestion() { return suggestion; }
        private boolean registered;
        private final AnimatedFloat pressed = new AnimatedFloat(this,350,new OvershootInterpolator(5f));
        private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        EmojiImageView(Context context){super(context);fallbackPaint.setTextSize(AndroidUtilities.dp(30));fallbackPaint.setTextAlign(Paint.Align.CENTER);}
        void bind(EmojiSuggestion item){
            release();suggestion=item;
            if(item!=null){if(item.documentId!=0)drawable=new AnimatedEmojiDrawable(item.documentId,AndroidUtilities.dp(38));
                else if(!TextUtils.isEmpty(item.emoji))drawable=Emoji.getEmojiBigDrawable(item.emoji.toString());}
            updateAttachment();invalidate();
        }
        void updateAttachment(){
            boolean need=show && isAttachedToWindow() && !destroyed && drawable instanceof AnimatedEmojiDrawable;
            if(need&&!registered){((AnimatedEmojiDrawable)drawable).addView(this);registered=true;}
            else if(!need&&registered){((AnimatedEmojiDrawable)drawable).removeView(this);registered=false;}
        }
        void release(){if(registered && drawable instanceof AnimatedEmojiDrawable)((AnimatedEmojiDrawable)drawable).removeView(this);registered=false;if(drawable instanceof AnimatedEmojiDrawable)((AnimatedEmojiDrawable)drawable).recycle();drawable=null;suggestion=null;}
        @Override protected void onMeasure(int w,int h){setPadding(AndroidUtilities.dp(3),AndroidUtilities.dp(direction==DIRECTION_TO_BOTTOM?3:9.66f),AndroidUtilities.dp(3),AndroidUtilities.dp(direction==DIRECTION_TO_BOTTOM?9.66f:3));super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(52),MeasureSpec.EXACTLY));}
        @Override public void setPressed(boolean value) { super.setPressed(value); invalidate(); }
        @Override protected void onDraw(Canvas canvas){
            float scale=.8f+.2f*(1f-pressed.set(isPressed()?1f:0f));int cx=getWidth()/2,cy=(getHeight()-getPaddingBottom()+getPaddingTop())/2;
            canvas.save();canvas.scale(scale,scale,cx,cy);
            if(drawable!=null && (!(drawable instanceof AnimatedEmojiDrawable)||((AnimatedEmojiDrawable)drawable).isLoaded())){drawable.setBounds(getPaddingLeft(),getPaddingTop(),getWidth()-getPaddingRight(),getHeight()-getPaddingBottom());drawable.draw(canvas);}
            else if(suggestion!=null&&!TextUtils.isEmpty(suggestion.emoji)){fallbackPaint.setColor(Theme.getColor(Theme.key_chat_botKeyboardButtonText,resourcesProvider));canvas.drawText(suggestion.emoji.toString(),cx,cy-(fallbackPaint.ascent()+fallbackPaint.descent())/2f,fallbackPaint);}
            canvas.restore();
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();updateAttachment();}
        @Override protected void onDetachedFromWindow(){if(registered && drawable instanceof AnimatedEmojiDrawable)((AnimatedEmojiDrawable)drawable).removeView(this);registered=false;super.onDetachedFromWindow();}
    }
    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder){return true;}
        @Override public int getItemCount(){return suggestions.size();}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){return new RecyclerListView.Holder(new EmojiImageView(parent.getContext()));}
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){((EmojiImageView)holder.itemView).bind(suggestions.get(position));}
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder){((EmojiImageView)holder.itemView).release();}
    }
}
