/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2013-2018.
 */
// User-mention list, extracted from MentionsContainerView release-11.4.2-5469.
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.MentionCell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Host-driven subset of the upstream mention panel. The host supplies and filters user candidates. */
public class MentionsContainerView extends FrameLayout {
    public interface Delegate { void onMentionSelected(Mention mention); }
    private final Theme.ResourcesProvider resourcesProvider;
    private final RecyclerListView listView;
    private final Adapter adapter;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF panelRect = new RectF();
    private List<Mention> mentions = Collections.emptyList();
    private Delegate delegate;
    private SpringAnimation animation;
    private Runnable startAnimation;
    private boolean shown, destroyed;

    public MentionsContainerView(Context context) { this(context, null); }
    public MentionsContainerView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false); setVisibility(GONE);
        paint.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0x1e000000);
        listView = new RecyclerListView(context, resourcesProvider);
        listView.setClipToPadding(false);
        LinearLayoutManager manager = new LinearLayoutManager(context) {
            @Override public boolean supportsPredictiveItemAnimations() { return false; }
        };
        manager.setOrientation(LinearLayoutManager.VERTICAL); listView.setLayoutManager(manager);
        adapter = new Adapter(); adapter.setHasStableIds(true); listView.setAdapter(adapter);
        listView.setOnItemClickListener((RecyclerListView.OnItemClickListener) (view, position) -> {
            if (!shown || destroyed || delegate == null || !(view instanceof MentionCell)) return;
            Mention selected = ((MentionCell) view).getMention();
            if (selected == null || position < 0 || position >= mentions.size() || mentions.get(position) != selected) return;
            delegate.onMentionSelected(selected);
        });
        addView(listView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }
    public void setDelegate(Delegate value) { delegate = value; }
    /** A defensive snapshot; IDs must be stable and unique for recycler reuse. */
    public void setMentions(List<Mention> items) {
        if (destroyed) return;
        ArrayList<Mention> copy = new ArrayList<>(); HashSet<Long> ids = new HashSet<>();
        if (items != null) for (Mention item : items) {
            if (item == null || item.id == RecyclerView.NO_ID || !ids.add(item.id)) throw new IllegalArgumentException("mention IDs must be unique and not NO_ID");
            copy.add(item);
        }
        mentions = Collections.unmodifiableList(copy); adapter.notifyDataSetChanged(); requestLayout();
        if (mentions.isEmpty()) hide();
    }
    public List<Mention> getMentions() { return mentions; }
    public RecyclerListView getListView() { return listView; }
    public boolean isOpen() { return shown; }
    public void show() {
        if (destroyed || mentions.isEmpty()) return;
        if (shown && getVisibility() == VISIBLE) return;
        cancelAnimation(); shown = true; setVisibility(VISIBLE); requestLayout();
        ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        listView.setTranslationY(Math.max(AndroidUtilities.dp(6), desiredHeight())); invalidate();
        startAnimation = () -> { startAnimation = null; if (shown && !destroyed) animateTo(0, false); };
        post(startAnimation);
    }
    public void hide() {
        if (destroyed || !shown && getVisibility() != VISIBLE) return;
        cancelAnimation(); shown = false;
        if (!isAttachedToWindow() || getHeight() == 0) { listView.setTranslationY(0); setVisibility(GONE); return; }
        animateTo(getHeight() + AndroidUtilities.dp(6), true);
    }
    private void animateTo(float target, boolean hiding) {
        float from = listView.getTranslationY();
        if (Math.abs(target - from) < .01f) { if (hiding && !shown) setVisibility(GONE); return; }
        animation = new SpringAnimation(new FloatValueHolder(from)).setSpring(new SpringForce(target).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY).setStiffness(550f));
        animation.addUpdateListener((anim, value, velocity) -> { listView.setTranslationY(value); invalidate(); });
        animation.addEndListener((anim, canceled, value, velocity) -> {
            if (animation == anim) animation = null;
            if (!canceled && hiding && !shown) { setVisibility(GONE); listView.setTranslationY(0); }
            invalidate();
        });
        animation.start();
    }
    private void cancelAnimation() {
        if (startAnimation != null) { removeCallbacks(startAnimation); startAnimation = null; }
        if (animation != null) { SpringAnimation old = animation; animation = null; old.cancel(); }
    }
    private int desiredHeight() {
        int screen = AndroidUtilities.displaySize.y > 0 ? AndroidUtilities.displaySize.y : getResources().getDisplayMetrics().heightPixels;
        int cap = Math.min(AndroidUtilities.dp(36 * 3.5f), (int)(screen * .22f));
        return Math.max(0, Math.min(mentions.size() * AndroidUtilities.dp(36), cap));
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int desired = desiredHeight(); int mode = MeasureSpec.getMode(heightSpec), limit = MeasureSpec.getSize(heightSpec);
        int height = mode == MeasureSpec.UNSPECIFIED ? desired : Math.min(desired, limit);
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.max(0,height), MeasureSpec.EXACTLY));
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        float top = Math.max(0, listView.getTranslationY() - AndroidUtilities.dp(2));
        if (top >= getHeight()) return;
        int color = Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider);
        paint.setColor(color); float radius = AndroidUtilities.dp(6);
        panelRect.set(0, top, getWidth(), getHeight() + radius);
        canvas.drawRoundRect(panelRect, radius, radius, paint);
        clipPath.rewind(); clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.save(); canvas.clipPath(clipPath); super.dispatchDraw(canvas); canvas.restore();
    }
    public void updateColors() {
        for (int i=0; i<listView.getChildCount(); i++) if (listView.getChildAt(i) instanceof MentionCell) ((MentionCell)listView.getChildAt(i)).updateColors();
        listView.invalidate(); invalidate();
    }
    public void destroy() {
        if (destroyed) return;
        cancelAnimation(); shown = false; destroyed = true; delegate = null;
        listView.setOnItemClickListener((RecyclerListView.OnItemClickListener)null); listView.setAdapter(null);
        mentions = Collections.emptyList(); setVisibility(GONE);
    }
    @Override protected void onDetachedFromWindow() {
        cancelAnimation();
        if (!shown) { listView.setTranslationY(0); setVisibility(GONE); }
        super.onDetachedFromWindow();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (shown) { listView.setTranslationY(0); setVisibility(VISIBLE); }
    }
    private final class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemCount() { return mentions.size(); }
        @Override public long getItemId(int position) { return mentions.get(position).id; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            MentionCell cell = new MentionCell(parent.getContext(), resourcesProvider);
            cell.setLayoutParams(new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(36)));
            return new RecyclerListView.Holder(cell);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) { ((MentionCell)holder.itemView).setMention(mentions.get(position)); }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder) { ((MentionCell)holder.itemView).setMention(null); }
    }
}
