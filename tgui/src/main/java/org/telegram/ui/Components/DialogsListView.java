package org.telegram.ui.Components;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

/**
 * tgui: the chat-list RecyclerListView, after upstream {@code DialogsActivity.DialogsRecyclerView}.
 * Wires up {@link DialogsSwipeController} (swipe actions, pinned reordering) and the upstream
 * {@link DialogsItemAnimator}; the touch handling that decides "spring back vs. slide out" on finger
 * up is upstream's.
 * <pre>
 * DialogsListView list = new DialogsListView(context);
 * list.setSwipeDelegate(delegate);
 * list.setAdapter(adapter);   // items are DialogCell
 * </pre>
 */
public class DialogsListView extends RecyclerListView {

    private final DialogsItemAnimator itemAnimator;
    private DialogsSwipeController swipeController;
    private ItemTouchHelper itemTouchHelper;
    boolean allowSwipeDuringCurrentTouch = true;

    public DialogsListView(Context context) {
        super(context);
        setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        setVerticalScrollBarEnabled(true);
        setItemAnimator(itemAnimator = new DialogsItemAnimator(this));
        setInstantClick(true);
    }

    public DialogsItemAnimator getDialogsItemAnimator() {
        return itemAnimator;
    }

    /** Enables upstream swipe actions and pinned-chat dragging; null disables them. */
    public void setSwipeDelegate(DialogsSwipeController.Delegate delegate) {
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
            itemTouchHelper = null;
            swipeController = null;
        }
        if (delegate != null) {
            swipeController = new DialogsSwipeController(this, delegate);
            itemTouchHelper = new ItemTouchHelper(swipeController);
            itemTouchHelper.attachToRecyclerView(this);
        }
    }

    /** Starts dragging a row (upstream starts drags from long-press in selection mode). */
    public void startDrag(ViewHolder holder) {
        if (itemTouchHelper != null) {
            itemTouchHelper.startDrag(holder);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (itemAnimator.isRunning()) {
            return false;
        }
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            allowSwipeDuringCurrentTouch = true;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        }
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && swipeController != null) {
            swipeController.onTouchUp(itemTouchHelper);
        }
        return super.onTouchEvent(e);
    }
}
