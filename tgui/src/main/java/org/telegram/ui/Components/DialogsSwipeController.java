package org.telegram.ui.Components;

import android.view.View;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;

/**
 * tgui: transplant of upstream 11.4.2 {@code DialogsActivity.SwipeController} (swipe-left actions and
 * drag-to-reorder of pinned chats) plus the ACTION_UP part of {@code DialogsRecyclerView.onTouchEvent}
 * (see {@link DialogsListView}). Thresholds (0.45 of the width, escape velocity 3500, cancel animation
 * 200 ms) are upstream's. The host decides the action per row and performs it in the callback:
 * <ul>
 * <li>{@link DialogCell#SWIPE_ACTION_ARCHIVE}, {@link DialogCell#SWIPE_ACTION_UNARCHIVE} and
 *     {@link DialogCell#SWIPE_ACTION_FOLDER} slide the row out ({@link Delegate#onSwiped}); the host
 *     removes it (call {@link DialogsItemAnimator#prepareForRemove()} first, as upstream does);</li>
 * <li>pin / read / mute / delete spring back ({@code swipeFolderBack}) and report
 *     {@link Delegate#onSwipeAction} so the host toggles the state.</li>
 * </ul>
 */
public class DialogsSwipeController extends ItemTouchHelper.Callback {

    public interface Delegate {
        /** One of DialogCell.SWIPE_ACTION_*, or -1 to disable swiping for this row. */
        int getSwipeAction(DialogCell cell);

        /** A spring-back action (pin, read, mute, delete) passed the threshold. */
        default void onSwipeAction(DialogCell cell, int action) {
        }

        /** A slide-out action (archive, unarchive, hide archive) completed; remove the row. */
        default void onSwiped(DialogCell cell, int action) {
        }

        /** Selection mode is on: swipes are disabled and pinned rows can be dragged instead. */
        default boolean isSelectionMode() {
            return false;
        }

        /** Whether this row may be dragged in selection mode (upstream: pinned rows only). */
        default boolean canReorder(DialogCell cell) {
            return false;
        }

        /** Move the item in the host list and notifyItemMoved; return false to refuse. */
        default boolean onReorder(int fromPosition, int toPosition) {
            return false;
        }

        /** Dragging finished (upstream sends the new pinned order to the server here). */
        default void onReorderFinished() {
        }
    }

    private final DialogsListView listView;
    private final Delegate delegate;
    private RecyclerView.ViewHolder currentItemViewHolder;
    boolean swipingFolder;
    boolean swipeFolderBack;
    private DialogCell movingView;
    private boolean movingWas;

    public DialogsSwipeController(DialogsListView listView, Delegate delegate) {
        this.listView = listView;
        this.delegate = delegate;
    }

    private static boolean springsBack(int action) {
        return action == DialogCell.SWIPE_ACTION_MUTE || action == DialogCell.SWIPE_ACTION_READ
                || action == DialogCell.SWIPE_ACTION_PIN || action == DialogCell.SWIPE_ACTION_DELETE;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if (swipingFolder && swipeFolderBack) {
            if (viewHolder.itemView instanceof DialogCell) {
                ((DialogCell) viewHolder.itemView).setSwipeCanceled(true);
            }
            swipingFolder = false;
            return 0;
        }
        if (viewHolder.itemView instanceof DialogCell) {
            DialogCell dialogCell = (DialogCell) viewHolder.itemView;
            if (delegate.isSelectionMode()) {
                if (!delegate.canReorder(dialogCell)) {
                    return 0;
                }
                movingView = dialogCell;
                movingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                swipeFolderBack = false;
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }
            if (!listView.allowSwipeDuringCurrentTouch) {
                return 0;
            }
            int action = delegate.getSwipeAction(dialogCell);
            if (action < 0) {
                return 0;
            }
            dialogCell.setSwipeAction(action);
            swipeFolderBack = false;
            swipingFolder = springsBack(action);
            dialogCell.setSliding(true);
            return makeMovementFlags(0, ItemTouchHelper.LEFT);
        }
        return 0;
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
        if (!(target.itemView instanceof DialogCell) || !delegate.canReorder((DialogCell) target.itemView)) {
            return false;
        }
        int fromIndex = source.getAdapterPosition();
        int toIndex = target.getAdapterPosition();
        if (delegate.onReorder(fromIndex, toIndex)) {
            movingWas = true;
            return true;
        }
        return false;
    }

    @Override
    public int convertToAbsoluteDirection(int flags, int layoutDirection) {
        if (swipeFolderBack) {
            return 0;
        }
        return super.convertToAbsoluteDirection(flags, layoutDirection);
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        if (viewHolder != null && viewHolder.itemView instanceof DialogCell) {
            DialogCell dialogCell = (DialogCell) viewHolder.itemView;
            delegate.onSwiped(dialogCell, dialogCell.getSwipeAction());
        }
    }

    /** Upstream DialogsRecyclerView.onTouchEvent, ACTION_UP / ACTION_CANCEL branch. */
    void onTouchUp(ItemTouchHelper itemTouchHelper) {
        if (!itemTouchHelper.isIdle() && swipingFolder) {
            swipeFolderBack = true;
            if (itemTouchHelper.checkHorizontalSwipe(null, ItemTouchHelper.LEFT) != 0) {
                if (currentItemViewHolder != null && currentItemViewHolder.itemView instanceof DialogCell) {
                    DialogCell dialogCell = (DialogCell) currentItemViewHolder.itemView;
                    delegate.onSwipeAction(dialogCell, dialogCell.getSwipeAction());
                }
            }
        }
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        if (viewHolder != null) {
            listView.hideSelector(false);
        }
        currentItemViewHolder = viewHolder;
        if (viewHolder != null && viewHolder.itemView instanceof DialogCell) {
            ((DialogCell) viewHolder.itemView).setSwipeCanceled(false);
        }
        if (viewHolder == null && movingWas) {
            movingWas = false;
            delegate.onReorderFinished();
        }
        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public long getAnimationDuration(RecyclerView recyclerView, int animationType, float animateDx, float animateDy) {
        if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_CANCEL) {
            return 200;
        } else if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) {
            if (movingView != null) {
                View view = movingView;
                long delay = recyclerView.getItemAnimator() != null ? recyclerView.getItemAnimator().getMoveDuration() : 0;
                AndroidUtilities.runOnUIThread(() -> view.setBackgroundDrawable(null), delay);
                movingView = null;
            }
        }
        return super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy);
    }

    @Override
    public float getSwipeThreshold(RecyclerView.ViewHolder viewHolder) {
        return 0.45f;
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return 3500;
    }

    @Override
    public float getSwipeVelocityThreshold(float defaultValue) {
        return Float.MAX_VALUE;
    }
}
