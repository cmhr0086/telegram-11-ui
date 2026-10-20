package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * tgui: upstream BackupImageView is backed by ImageReceiver (network/file/cache image loader
 * bound to TL documents). The UI library keeps the view for layout compatibility and draws a
 * local Drawable/Bitmap with optional rounded corners. Plug your own image loader by calling
 * {@link #setImageDrawable(Drawable)} / {@link #setImageBitmap(Bitmap)}.
 */
public class BackupImageView extends View {

    private Drawable drawable;
    private int roundRadius;
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    protected int width = -1;
    protected int height = -1;

    public BackupImageView(Context context) {
        super(context);
    }

    public void setRoundRadius(int value) {
        roundRadius = value;
        invalidate();
    }

    public int getRoundRadius() {
        return roundRadius;
    }

    public void setSize(int w, int h) {
        width = w;
        height = h;
        invalidate();
    }

    public void setImageDrawable(Drawable drawable) {
        if (this.drawable != null) {
            this.drawable.setCallback(null);
        }
        this.drawable = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
        }
        invalidate();
    }

    public void setImageBitmap(Bitmap bitmap) {
        setImageDrawable(bitmap == null ? null : new BitmapDrawable(getResources(), bitmap));
    }

    public void setImageResource(int resId) {
        setImageDrawable(resId == 0 ? null : getContext().getResources().getDrawable(resId).mutate());
    }

    public Drawable getImageDrawable() {
        return drawable;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == drawable || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (drawable == null) {
            return;
        }
        int w = width == -1 ? getWidth() : width;
        int h = height == -1 ? getHeight() : height;
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;
        drawable.setBounds(x, y, x + w, y + h);
        if (roundRadius > 0) {
            canvas.save();
            clipPath.rewind();
            rect.set(x, y, x + w, y + h);
            clipPath.addRoundRect(rect, roundRadius, roundRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
            drawable.draw(canvas);
            canvas.restore();
        } else {
            drawable.draw(canvas);
        }
    }
}
