/*
 * Adapted from Telegram for Android, release-11.4.2-5469.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.LocalMediaView;
import org.telegram.ui.Components.Sticker;

/** Local-media version of the upstream sticker cell. */
public class StickerEmojiCell extends FrameLayout {
    private final LocalMediaView media;
    private boolean stickerMode;
    public StickerEmojiCell(Context context) {
        super(context);
        media = new LocalMediaView(context);
        addView(media, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }
    public void setSticker(Sticker sticker) {
        stickerMode = true;
        setTag(sticker);
        media.setSticker(sticker);
        media.setAnimating(true);
    }
    public void setGif(Gif gif) {
        stickerMode = false;
        setTag(gif);
        media.setGif(gif);
        media.setAnimating(true);
    }
    public void stopAnimation() { media.setAnimating(false); }
    public void setAnimating(boolean active) { media.setAnimating(active); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (stickerMode) {
            int size = Math.min(AndroidUtilities.dp(66), Math.min(getMeasuredWidth(), getMeasuredHeight()));
            int exact = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
            media.measure(exact, exact);
        }
    }
}
