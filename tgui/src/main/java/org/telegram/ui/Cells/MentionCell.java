/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Mention;

/** The upstream 36dp user-mention row, with host-supplied user data. */
public class MentionCell extends LinearLayout {
    private final AvatarView imageView;
    private final TextView nameTextView, usernameTextView;
    private final AvatarDrawable avatarDrawable;
    private final Theme.ResourcesProvider resourcesProvider;
    private Mention mention;

    public MentionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(HORIZONTAL);
        avatarDrawable = new AvatarDrawable(resourcesProvider);
        avatarDrawable.setTextSize(AndroidUtilities.dp(18));
        imageView = new AvatarView(context);
        addView(imageView, LayoutHelper.createLinear(28, 28, 12, 4, 0, 0));
        nameTextView = new TextView(context) {
            @Override public void setText(CharSequence text, BufferType type) {
                super.setText(Emoji.replaceEmoji(text == null ? "" : text, getPaint().getFontMetricsInt(), false), type);
            }
        };
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameTextView.setSingleLine(true); nameTextView.setGravity(Gravity.LEFT); nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        usernameTextView = new TextView(context);
        usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        usernameTextView.setSingleLine(true); usernameTextView.setGravity(Gravity.LEFT); usernameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 8, 0));
        updateColors();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(36), MeasureSpec.EXACTLY));
    }
    public void setMention(Mention value) {
        mention = value;
        if (value == null) { nameTextView.setText(""); usernameTextView.setText(""); imageView.setDrawable(null); return; }
        avatarDrawable.setInfo(value.id, value.name.toString(), null);
        imageView.setDrawable(value.avatar != null ? value.avatar : avatarDrawable);
        updateColors();
        nameTextView.setText(value.name);
        String username = value.username;
        usernameTextView.setText(TextUtils.isEmpty(username) ? "" : username.charAt(0) == '@' ? username : "@" + username);
    }
    public Mention getMention() { return mention; }
    public void updateColors() {
        if (mention != null) avatarDrawable.setInfo(mention.id, mention.name.toString(), null);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        usernameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
        invalidate();
    }
    /** Host drawables remain host-owned: neither callbacks nor bounds are retained or changed. */
    private static final class AvatarView extends View {
        private Drawable drawable;
        private final Path path = new Path();
        private final RectF rect = new RectF();
        AvatarView(Context context) { super(context); }
        void setDrawable(Drawable value) { drawable = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            if (drawable == null) return;
            Rect old = new Rect(drawable.getBounds());
            rect.set(0, 0, getWidth(), getHeight()); path.rewind(); path.addRoundRect(rect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), Path.Direction.CW);
            canvas.save(); canvas.clipPath(path);
            int intrinsicWidth = drawable.getIntrinsicWidth(), intrinsicHeight = drawable.getIntrinsicHeight();
            if (intrinsicWidth <= 0 || intrinsicHeight <= 0) { intrinsicWidth = getWidth(); intrinsicHeight = getHeight(); }
            float scale = Math.max(getWidth() / (float)Math.max(1, intrinsicWidth), getHeight() / (float)Math.max(1, intrinsicHeight));
            float width = intrinsicWidth * scale, height = intrinsicHeight * scale;
            int left = Math.round((getWidth() - width) / 2f), top = Math.round((getHeight() - height) / 2f);
            try { drawable.setBounds(left, top, Math.round(left + width), Math.round(top + height)); drawable.draw(canvas); }
            finally { drawable.setBounds(old); canvas.restore(); }
        }
    }
}
