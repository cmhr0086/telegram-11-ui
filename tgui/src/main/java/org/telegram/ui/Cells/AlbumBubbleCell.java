/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;

import java.util.ArrayList;
import java.util.HashSet;

/** A single message bubble containing a Telegram-style group of locally supplied photos. */
public class AlbumBubbleCell extends PhotoBubbleCell {
    private static final class Tile {
        final RectF normalized = new RectF();
        final RectF rect = new RectF();
        final float[] radii = new float[8];
        final PhotoRenderer renderer = new PhotoRenderer();
        final RadialProgress2 progress;
        TextBubbleMessage item;
        boolean visible = true;
        int state = -1;
        boolean video;
        int duration = -1;
        int durationWidth;
        StaticLayout durationLayout;
        Tile(View parent, Theme.ResourcesProvider provider) {
            progress = new RadialProgress2(parent, provider);
            progress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected,
                    Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
        }
    }

    private final GroupedMediaLayout grouped = new GroupedMediaLayout();
    private final ArrayList<Tile> tiles = new ArrayList<>();
    private final HashSet<Integer> hiddenIndices = new HashSet<>();
    private TextBubbleMessage lastAlbum;

    public AlbumBubbleCell(Context context) { this(context, null); }
    public AlbumBubbleCell(Context context, Theme.ResourcesProvider resourcesProvider) { super(context, resourcesProvider); }

    @Override public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        if (lastAlbum != message) {
            hiddenIndices.clear();
            for (Tile tile : tiles) tile.visible = true;
            lastAlbum = message;
        }
        super.setMessage(message, pinnedTop, pinnedBottom);
        layoutForWidth = -1;
        requestLayout();
        invalidate();
    }

    @Override protected int[] measurePhoto(int width, int imageW, int imageH) {
        grouped.calculate(message.albumMedia, message.isOut);
        int count = grouped.positions.size();
        while (tiles.size() > count) {
            Tile old = tiles.remove(tiles.size() - 1);
            if (isAttachedToWindow()) old.progress.onDetachedFromWindow();
        }
        while (tiles.size() < count) {
            Tile tile = new Tile(this, resourcesProvider);
            tiles.add(tile);
            if (isAttachedToWindow()) tile.progress.onAttachedToWindow();
        }
        int rowCount = 0;
        for (GroupedMediaLayout.Position position : grouped.positions) rowCount = Math.max(rowCount, position.maxY + 1);
        float[] rowHeights = new float[rowCount];
        for (int i = 0; i < count; i++) {
            GroupedMediaLayout.Position p = grouped.positions.get(i);
            tiles.get(i).item = message.albumMedia.get(i);
            tiles.get(i).visible = !hiddenIndices.contains(i);
            if (p.minY == p.maxY) rowHeights[p.minY] = Math.max(rowHeights[p.minY], p.ph);
        }
        float totalHeight = 0;
        for (float h : rowHeights) totalHeight += h;
        if (totalHeight <= 0) totalHeight = 1;
        float[] rowTop = new float[rowCount + 1];
        for (int i = 0; i < rowCount; i++) rowTop[i + 1] = rowTop[i] + rowHeights[i] / totalHeight;
        int sibling = -1;
        for (int i = 0; i < count; i++) if (grouped.positions.get(i).siblingHeights != null) { sibling = i; break; }
        if (sibling >= 0) {
            GroupedMediaLayout.Position left = grouped.positions.get(sibling);
            int rightWidth = 0;
            for (GroupedMediaLayout.Position p : grouped.positions) {
                if (p != left && (p.flags & GroupedMediaLayout.RIGHT) != 0) { rightWidth = p.pw; break; }
            }
            float split = left.pw / (float) Math.max(1, left.pw + rightWidth);
            for (int i = 0; i < count; i++) {
                GroupedMediaLayout.Position p = grouped.positions.get(i);
                if (i == sibling) tiles.get(i).normalized.set(0, 0, split, 1);
                else tiles.get(i).normalized.set(split, rowTop[p.minY], 1, rowTop[p.maxY + 1]);
            }
        } else {
            for (int row = 0; row < rowCount; row++) {
                int rowWidth = 0;
                for (GroupedMediaLayout.Position p : grouped.positions) if (p.minY == row) rowWidth += p.pw;
                float x = 0;
                for (int i = 0; i < count; i++) {
                    GroupedMediaLayout.Position p = grouped.positions.get(i);
                    if (p.minY != row) continue;
                    float right = x + p.pw / (float) Math.max(1, rowWidth);
                    tiles.get(i).normalized.set(x, rowTop[row], right, rowTop[row + 1]);
                    x = right;
                }
            }
        }
        // Upstream ChatMessageCell uses pw / 1000 * parent width and max(parentWidth, displayHeight) / 2 for ph.
        int w = Math.max(AndroidUtilities.dp(120), (int) Math.ceil(width * 800f / 1000f) - AndroidUtilities.dp(18));
        int h = Math.max(AndroidUtilities.dp(120), (int) Math.ceil(Math.max(width, AndroidUtilities.displaySize.y) * 0.5f * totalHeight));
        return new int[] { w, h };
    }

    @Override protected void drawPhoto(Canvas canvas) {
        float gap = AndroidUtilities.dp(2) / 2f;
        int color = getThemedColor(message.isOut ? Theme.key_chat_outLoaderSelected : Theme.key_chat_inMediaIcon);
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            GroupedMediaLayout.Position p = grouped.positions.get(i);
            RectF n = tile.normalized;
            tile.rect.set(photoRect.left + n.left * photoRect.width(), photoRect.top + n.top * photoRect.height(),
                    photoRect.left + n.right * photoRect.width(), photoRect.top + n.bottom * photoRect.height());
            if (n.left > 0) tile.rect.left += gap;
            if (n.right < 0.9999f) tile.rect.right -= gap;
            if (n.top > 0) tile.rect.top += gap;
            if (n.bottom < 0.9999f) tile.rect.bottom -= gap;
            float inner = Math.min(AndroidUtilities.dp(4), AndroidUtilities.dp(SharedConfig.bubbleRadius > 2 ? SharedConfig.bubbleRadius - 2 : SharedConfig.bubbleRadius));
            float tl = (p.flags & (GroupedMediaLayout.TOP | GroupedMediaLayout.LEFT)) == (GroupedMediaLayout.TOP | GroupedMediaLayout.LEFT) ? radii[0] : inner;
            float tr = (p.flags & (GroupedMediaLayout.TOP | GroupedMediaLayout.RIGHT)) == (GroupedMediaLayout.TOP | GroupedMediaLayout.RIGHT) ? radii[2] : inner;
            float br = (p.flags & (GroupedMediaLayout.BOTTOM | GroupedMediaLayout.RIGHT)) == (GroupedMediaLayout.BOTTOM | GroupedMediaLayout.RIGHT) ? radii[4] : inner;
            float bl = (p.flags & (GroupedMediaLayout.BOTTOM | GroupedMediaLayout.LEFT)) == (GroupedMediaLayout.BOTTOM | GroupedMediaLayout.LEFT) ? radii[6] : inner;
            tile.radii[0] = tile.radii[1] = tl; tile.radii[2] = tile.radii[3] = tr;
            tile.radii[4] = tile.radii[5] = br; tile.radii[6] = tile.radii[7] = bl;
            if (!tile.visible) continue;
            tile.renderer.draw(canvas, tile.item.photo, tile.rect, tile.radii, color);
            int state = tile.item.mediaState;
            boolean video = tile.item.videoUri != null;
            if (tile.state != state || tile.video != video) {
                tile.state = state; tile.video = video;
                tile.progress.setIcon(state == TextBubbleMessage.MEDIA_DOWNLOAD ? MediaActionDrawable.ICON_DOWNLOAD :
                        state == TextBubbleMessage.MEDIA_LOADING ? MediaActionDrawable.ICON_CANCEL :
                                video ? MediaActionDrawable.ICON_PLAY : MediaActionDrawable.ICON_NONE, false, false);
            }
            tile.progress.setProgress(state == TextBubbleMessage.MEDIA_LOADING ? tile.item.mediaProgress : 0, false);
            tile.progress.setProgressColor(getThemedColor(Theme.key_chat_mediaProgress));
            float centerX = tile.rect.centerX(), centerY = tile.rect.centerY();
            canvas.save();
            canvas.clipRect(tile.rect);
            int half = AndroidUtilities.dp(24);
            tile.progress.setProgressRect((int) centerX - half, (int) centerY - half, (int) centerX + half, (int) centerY + half);
            tile.progress.draw(canvas);
            if (video) drawDuration(canvas, tile);
            canvas.restore();
        }
    }

    private void drawDuration(Canvas canvas, Tile tile) {
        if (tile.durationLayout == null || tile.duration != tile.item.duration) {
            tile.duration = tile.item.duration;
            String text = AndroidUtilities.formatShortDuration(tile.duration);
            tile.durationWidth = (int) Math.ceil(Theme.chat_durationPaint.measureText(text));
            tile.durationLayout = new StaticLayout(text, Theme.chat_durationPaint, Math.max(1, tile.durationWidth), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
        }
        int width = tile.durationWidth;
        StaticLayout layout = tile.durationLayout;
        float x = tile.rect.left + AndroidUtilities.dp(9), y = tile.rect.top + AndroidUtilities.dp(6);
        RectF box = new RectF(x - AndroidUtilities.dp(4), y - AndroidUtilities.dp(1.5f),
                x + width + AndroidUtilities.dp(4), y + layout.getHeight() + AndroidUtilities.dp(1.5f));
        float radius = AndroidUtilities.dp(SharedConfig.bubbleRadius > 2 ? SharedConfig.bubbleRadius - 2 : SharedConfig.bubbleRadius);
        Theme.chat_timeBackgroundPaint.setColor(getThemedColor(Theme.key_chat_mediaTimeBackground));
        canvas.drawRoundRect(box, radius, radius, Theme.chat_timeBackgroundPaint);
        Theme.chat_durationPaint.setColor(getThemedColor(Theme.key_chat_mediaTimeText));
        canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore();
    }

    @Override protected void drawPhotoOverlays(Canvas canvas, int x, int y) { }

    @Override protected void onPhotoPressed(float x, float y) {
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            if (tile.visible && tile.rect.contains(x, y)) {
                mediaDelegate.onAlbumMediaPressed(this, message, tile.item, i);
                return;
            }
        }
    }

    public boolean getTileRect(int index, RectF out) {
        if (index < 0 || index >= tiles.size() || out == null || tiles.get(index).rect.isEmpty()) return false;
        out.set(tiles.get(index).rect); return true;
    }
    public boolean getTileRectOnScreen(int index, RectF out) {
        if (!isAttachedToWindow() || !getTileRect(index, out)) return false;
        int[] location = new int[2]; getLocationOnScreen(location); out.offset(location[0], location[1]); return true;
    }
    public float[] getTileRadii(int index) {
        if (index < 0 || index >= tiles.size()) return null;
        return tiles.get(index).radii.clone();
    }
    public void setTileVisible(int index, boolean visible) {
        if (index < 0) return;
        if (visible) hiddenIndices.remove(index); else hiddenIndices.add(index);
        if (index < tiles.size()) tiles.get(index).visible = visible;
        invalidate();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (Tile tile : tiles) tile.progress.onAttachedToWindow();
    }
    @Override protected void onDetachedFromWindow() {
        for (Tile tile : tiles) tile.progress.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }
}
