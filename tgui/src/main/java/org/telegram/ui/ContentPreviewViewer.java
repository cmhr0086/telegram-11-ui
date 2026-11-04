/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.LocalMediaDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Sticker;
import java.util.ArrayList;
import static org.telegram.messenger.AndroidUtilities.dp;

/** Pruned upstream content preview: host media replaces account/document lookup and actions. */
public final class ContentPreviewViewer {
    public interface ItemProvider { Object getItem(View cell); }
    public interface Delegate {
        default void onSendSticker(Sticker sticker) { }
        default void onSendGif(Gif gif) { }
        default boolean isFavorite(Sticker sticker) { return false; }
        default void onFavoriteChanged(Sticker sticker, boolean favorite) { }
        default void onOpenSet(Sticker sticker) { }
        default void onGifRemoved(Gif gif) { }
    }
    /** Keep the binding for explicit disposal; detach always cancels pending/visible previews. */
    public static Binding bind(RecyclerListView list, ItemProvider provider, Delegate delegate,
                               Theme.ResourcesProvider resourcesProvider) {
        return new Binding(list, provider, delegate, resourcesProvider);
    }
    public static final class Binding implements RecyclerView.OnItemTouchListener,
            View.OnAttachStateChangeListener, AutoCloseable {
        private final RecyclerListView list;
        private final ItemProvider provider;
        private final Delegate delegate;
        private final Theme.ResourcesProvider colors;
        private float startX, startY;
        private Object current;
        private View cell;
        private Preview preview;
        private BottomSheet menu;
        private boolean active, disposed;
        private final Runnable open = this::open;
        private final Runnable showMenu = this::showMenu;

        private Binding(RecyclerListView list, ItemProvider provider, Delegate delegate, Theme.ResourcesProvider colors) {
            this.list = list; this.provider = provider; this.delegate = delegate; this.colors = colors;
            list.addOnItemTouchListener(this);
            list.addOnAttachStateChangeListener(this);
        }
        private Object at(MotionEvent e) {
            View child = list.findChildViewUnder(e.getX(), e.getY());
            Object value = child == null ? null : provider.getItem(child);
            if (!(value instanceof Sticker) && !(value instanceof Gif)) return null;
            cell = child;
            return value;
        }
        private void open() {
            Activity activity = AndroidUtilities.findActivity(list.getContext());
            if (disposed || current == null || !list.isAttachedToWindow() || activity == null || activity.isFinishing()) return;
            list.cancelClickRunnables(true);
            list.requestDisallowInterceptTouchEvent(true);
            active = true;
            preview = new Preview(activity);
            preview.setItem(current);
            // Use the source window's decor, including a sheet's window. No overlay permission.
            View root = list.getRootView();
            if (!(root instanceof ViewGroup)) { active = false; preview = null; return; }
            ((ViewGroup) root).addView(preview, new ViewGroup.LayoutParams(-1, -1));
            if (cell != null) cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            AndroidUtilities.runOnUIThread(showMenu, 1300);
        }
        private void showMenu() {
            if (!active || preview == null || current == null || menu != null) return;
            Object item = current;
            ArrayList<CharSequence> labels = new ArrayList<>();
            ArrayList<Runnable> actions = new ArrayList<>();
            labels.add(list.getContext().getString(R.string.Send));
            actions.add(() -> { if (item instanceof Sticker) delegate.onSendSticker((Sticker) item); else delegate.onSendGif((Gif) item); });
            if (item instanceof Sticker) {
                Sticker sticker = (Sticker) item;
                boolean favorite = delegate.isFavorite(sticker);
                labels.add(list.getContext().getString(favorite ? R.string.tgui_favorite_remove : R.string.tgui_favorite_add));
                actions.add(() -> delegate.onFavoriteChanged(sticker, !favorite));
                labels.add(list.getContext().getString(R.string.tgui_view_pack));
                actions.add(() -> delegate.onOpenSet(sticker));
            } else {
                labels.add(list.getContext().getString(R.string.tgui_remove_gif));
                actions.add(() -> delegate.onGifRemoved((Gif) item));
            }
            menu = new BottomSheet.Builder(list.getContext(), false, colors)
                    .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                        Runnable action = actions.get(which);
                        hide(false);
                        action.run();
                    }).create();
            menu.setOnDismissListener(dialog -> { menu = null; hide(false); });
            menu.show();
        }
        private void handle(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE) {
                if (!active && Math.hypot(startX - event.getX(), startY - event.getY()) > dp(10)) {
                    AndroidUtilities.cancelRunOnUIThread(open);
                    current = null;
                } else if (active && menu == null) {
                    Object item = at(event);
                    if (item != null && item != current) {
                        current = item;
                        preview.setItem(item);
                        AndroidUtilities.cancelRunOnUIThread(showMenu);
                        AndroidUtilities.runOnUIThread(showMenu, 1300);
                    }
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                AndroidUtilities.cancelRunOnUIThread(open);
                if (menu == null || action == MotionEvent.ACTION_CANCEL) hide(false);
            }
        }
        @Override public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
            if (disposed) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                hide(true);
                startX = e.getX(); startY = e.getY(); current = at(e);
                if (current != null) AndroidUtilities.runOnUIThread(open, 200);
                return false;
            }
            boolean intercept = active;
            handle(e);
            return intercept;
        }
        @Override public void onTouchEvent(RecyclerView rv, MotionEvent e) { handle(e); }
        @Override public void onRequestDisallowInterceptTouchEvent(boolean disallow) { }
        private void hide(boolean immediately) {
            boolean wasActive = active;
            AndroidUtilities.cancelRunOnUIThread(open);
            AndroidUtilities.cancelRunOnUIThread(showMenu);
            active = false; current = null; cell = null;
            // RecyclerListView cancels its pending click even for disallow=false.
            // A short tap never took ownership of the gesture and must retain it.
            if (wasActive) list.requestDisallowInterceptTouchEvent(false);
            if (menu != null) { BottomSheet old = menu; menu = null; old.setOnDismissListener((android.content.DialogInterface.OnDismissListener) null); old.dismiss(); }
            if (preview != null) { Preview old = preview; preview = null; old.hide(immediately); }
        }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { hide(true); }
        @Override public void close() {
            disposed = true; hide(true);
            list.removeOnItemTouchListener(this); list.removeOnAttachStateChangeListener(this);
        }
        public boolean isShowing() { return preview != null; }
    }

    /** Geometry/dim/120ms progression retained from upstream onDraw, lines 1817–1918. */
    private static final class Preview extends View {
        private final LocalMediaDrawable centerImage = new LocalMediaDrawable(this);
        private final ColorDrawable backgroundDrawable = new ColorDrawable(0xff000000);
        private final Path clip = new Path();
        private final RectF rect = new RectF();
        private boolean gif, visible = true;
        private float showProgress;
        private long lastUpdateTime = android.os.SystemClock.uptimeMillis();
        Preview(Activity context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setItem(Object item) {
            gif = item instanceof Gif;
            if (gif) centerImage.setGif((Gif) item); else centerImage.setSticker((Sticker) item);
            invalidate();
        }
        void hide(boolean immediately) {
            visible = false;
            if (immediately) remove(); else { lastUpdateTime = android.os.SystemClock.uptimeMillis(); invalidate(); }
        }
        private void remove() { if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); centerImage.attach(); }
        @Override protected void onDetachedFromWindow() { centerImage.detach(); super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas canvas) {
            long now = android.os.SystemClock.uptimeMillis();
            showProgress = Math.max(0, Math.min(1, showProgress + (visible ? 1 : -1) * (now - lastUpdateTime) / 120f));
            lastUpdateTime = now;
            backgroundDrawable.setAlpha((int) (180 * showProgress));
            backgroundDrawable.setBounds(0, 0, getWidth(), getHeight());
            backgroundDrawable.draw(canvas);
            int top = AndroidUtilities.statusBarHeight, bottom = 0;
            if (android.os.Build.VERSION.SDK_INT >= 23 && getRootWindowInsets() != null) {
                top = getRootWindowInsets().getStableInsetTop(); bottom = getRootWindowInsets().getStableInsetBottom();
            }
            int available = Math.min(getWidth(), getHeight() - top - bottom);
            int size = gif ? available - dp(40) : (int) (available / 1.8f);
            float topOffset = Math.max(size / 2f + top, (getHeight() - top - bottom) / 2f);
            size = (int) (size * showProgress);
            canvas.save();
            canvas.translate(getWidth() / 2f, topOffset);
            if (gif) {
                rect.set(-size / 2f, -size / 2f, size / 2f, size / 2f);
                clip.rewind(); clip.addRoundRect(rect, dp(6), dp(6), Path.Direction.CW); canvas.clipPath(clip);
            }
            centerImage.setAlpha((int) (255 * showProgress));
            centerImage.setBounds(-size / 2, -size / 2, size / 2, size / 2);
            centerImage.draw(canvas);
            canvas.restore();
            if (!visible && showProgress == 0) remove();
            else if (visible && showProgress < 1 || !visible) postInvalidateOnAnimation();
        }
    }
}
