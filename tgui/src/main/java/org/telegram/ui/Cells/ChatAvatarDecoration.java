/*
 * Adapted from Telegram for Android release-11.4.2-5469, ChatActivity.ChatListView.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Cells;

import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.recyclerview.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;

/** Draw one avatar per visible contiguous sender group, clamped to the list viewport. */
public final class ChatAvatarDecoration extends RecyclerView.ItemDecoration {
    private final ArrayList<View> children = new ArrayList<>();
    @Override public void onDrawOver(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        children.clear();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && parent.getChildAdapterPosition(child) != RecyclerView.NO_POSITION) children.add(child);
        }
        Collections.sort(children, (a, b) -> Float.compare(a.getY(), b.getY()));
        int visibleTop = parent.getPaddingTop();
        int visibleBottom = parent.getHeight() - parent.getPaddingBottom();
        int size = AndroidUtilities.dp(42);
        if (visibleBottom - visibleTop < size) return;
        for (int i = 0; i < children.size(); i++) {
            View view = children.get(i);
            if (!(view instanceof TextBubbleCell)) continue;
            TextBubbleCell first = (TextBubbleCell) view;
            if (!first.isAvatarDrawnByList() || !first.hasGroupAvatar()) continue;
            TextBubbleCell last = first;
            while (i + 1 < children.size() && children.get(i + 1) instanceof TextBubbleCell) {
                TextBubbleCell next = (TextBubbleCell) children.get(i + 1);
                if (!next.isAvatarDrawnByList() || !next.hasGroupAvatar() || !last.isPinnedBottom() || !next.isPinnedTop()
                        || !MessageGrouping.canMerge(last.getMessage(), next.getMessage())) break;
                last = next; i++;
            }
            float groupTop = first.getY(), groupBottom = last.getY() + last.getHeight();
            if (groupBottom <= visibleTop || groupTop >= visibleBottom) continue;
            float bottom = Math.max(visibleTop + size, Math.min(visibleBottom, groupBottom - AndroidUtilities.dp(3)));
            int alpha = Math.round(Math.max(0f, Math.min(1f, last.getAlpha())) * 255f);
            if (alpha == 0) continue;
            if (alpha == 255) canvas.save();
            else canvas.saveLayerAlpha(0, visibleTop, parent.getWidth(), visibleBottom, alpha);
            canvas.clipRect(0, visibleTop, parent.getWidth(), visibleBottom);
            last.drawAvatarAt(canvas, last.getX() + last.getAvatarColumnX(), bottom);
            canvas.restore();
        }
    }
}
