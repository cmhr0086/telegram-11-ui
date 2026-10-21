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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;


/**
 * tgui: upstream 11.4.2 DrawerActionCell, ported directly; the attach-menu bot entries, the "new"
 * badge and the pending-suggestions error check are removed ({@link #setError(boolean)} stays).
 */
public class DrawerActionCell extends FrameLayout {

    private ImageView imageView;
    private TextView textView;
    private int currentId;
    private RectF rect = new RectF();
    private boolean currentError;

    public DrawerActionCell(Context context) {
        super(context);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemIcon), PorterDuff.Mode.SRC_IN));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.TOP, 19, 12, 0, 0));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 72, 0, 16, 0));

        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean redError = currentError;
        boolean error = currentError;
        if (error) {
            int countTop = AndroidUtilities.dp(12.5f);
            int countWidth = AndroidUtilities.dp(9);
            int countLeft = getMeasuredWidth() - countWidth - AndroidUtilities.dp(25);

            int x = countLeft - AndroidUtilities.dp(5.5f);
            rect.set(x, countTop, x + countWidth + AndroidUtilities.dp(14), countTop + AndroidUtilities.dp(23));
            Theme.chat_docBackPaint.setColor(Theme.getColor(redError ? Theme.key_text_RedBold : Theme.key_chats_archiveBackground));
            canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.chat_docBackPaint);

            int w = Theme.dialogs_errorDrawable.getIntrinsicWidth();
            int h = Theme.dialogs_errorDrawable.getIntrinsicHeight();
            Theme.dialogs_errorDrawable.setBounds((int) (rect.centerX() - w / 2), (int) (rect.centerY() - h / 2), (int) (rect.centerX() + w / 2), (int) (rect.centerY() + h / 2));
            Theme.dialogs_errorDrawable.draw(canvas);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemIcon), PorterDuff.Mode.SRC_IN));
    }

    public void setTextAndIcon(int id, String text, int resId) {
        currentId = id;
        try {
            textView.setText(text);
            imageView.setImageResource(resId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void setError(boolean error) {
        currentError = error;
        invalidate();
    }

    public void setTextAndIcon(int id, CharSequence text, int resId) {
        currentId = id;
        try {
            textView.setText(text);
            imageView.setImageResource(resId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void updateTextAndIcon(String text, int resId) {
        try {
            textView.setText(text);
            imageView.setImageResource(resId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public ImageView getImageView() {
        return imageView;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        info.setText(textView.getText());
        info.setClassName(TextView.class.getName());
    }
}
