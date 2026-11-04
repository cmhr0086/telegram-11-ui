/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileFragment;

/** Upstream UserCell's profile-member branch (checkbox=0, admin=true), with host data. */
public class UserCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    public final BackupImageView avatarImageView;
    protected final SimpleTextView nameTextView;
    protected final SimpleTextView statusTextView;
    private final TextView adminTextView;
    protected final AvatarDrawable avatarDrawable = new AvatarDrawable();
    protected final Theme.ResourcesProvider resourcesProvider;
    public boolean needDivider;
    private int avatarPadding;
    private ProfileFragment.Member member;

    public UserCell(Context context, int padding, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        avatarPadding = padding;
        avatarImageView = new BackupImageView(context) {
            private final Rect originalBounds = new Rect();
            private final Path avatarClip = new Path();
            private int cropWidth = -1, cropHeight = -1;
            @Override public void setImageDrawable(Drawable drawable) {
                // Member photos are static host-owned drawables; never steal a shared callback.
                Drawable previous = getImageDrawable();
                Drawable.Callback previousCallback = previous == null ? null : previous.getCallback();
                Drawable.Callback nextCallback = drawable == null ? null : drawable.getCallback();
                super.setImageDrawable(drawable);
                if (previous != null) previous.setCallback(previousCallback);
                if (drawable != null) drawable.setCallback(nextCallback);
            }
            @Override protected void onDraw(Canvas canvas) {
                Drawable drawable = getImageDrawable();
                if (drawable == null) return;
                originalBounds.set(drawable.getBounds());
                int width = getWidth(), height = getHeight();
                int imageWidth = drawable.getIntrinsicWidth(), imageHeight = drawable.getIntrinsicHeight();
                int desiredWidth = width, desiredHeight = height;
                if (imageWidth > 0 && imageHeight > 0) {
                    float scale = Math.max(width / (float) imageWidth, height / (float) imageHeight);
                    desiredWidth = Math.round(imageWidth * scale);
                    desiredHeight = Math.round(imageHeight * scale);
                }
                if (cropWidth != desiredWidth || cropHeight != desiredHeight) {
                    setSize(cropWidth = desiredWidth, cropHeight = desiredHeight);
                }
                avatarClip.reset();
                avatarClip.addRoundRect(0, 0, width, height, dp(24), dp(24), Path.Direction.CW);
                canvas.save();
                canvas.clipPath(avatarClip);
                try { super.onDraw(canvas); }
                finally { canvas.restore(); drawable.setBounds(originalBounds); }
            }
        };
        avatarImageView.setRoundRadius(dp(24));
        addView(avatarImageView);
        setClipChildren(false);
        nameTextView = new SimpleTextView(context);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setTextSize(16);
        addView(nameTextView);
        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(15);
        addView(statusTextView);
        adminTextView = new TextView(context);
        adminTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addView(adminTextView);
        setFocusable(true);
        applyLayout();
        updateColors();
    }

    private boolean isRtl() { return getResources().getConfiguration().getLayoutDirection() == LAYOUT_DIRECTION_RTL; }

    private void applyLayout() {
        boolean rtl = isRtl();
        avatarImageView.setLayoutParams(LayoutHelper.createFrame(46, 46, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, rtl ? 0 : 7 + avatarPadding, 6, rtl ? 7 + avatarPadding : 0, 0));
        nameTextView.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        nameTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, rtl ? 28 : 64 + avatarPadding, 10, rtl ? 64 + avatarPadding : 28, 0));
        statusTextView.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        statusTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, rtl ? 28 : 64 + avatarPadding, 32, rtl ? 64 + avatarPadding : 28, 0));
        adminTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, rtl ? 23 : 0, 10, rtl ? 0 : 23, 0));
        setAdminRole(member == null ? null : member.role);
    }

    public void setAvatarPadding(int padding) {
        if (avatarPadding == padding) return;
        avatarPadding = padding;
        applyLayout();
    }

    public ProfileFragment.Member getMember() { return member; }

    public void setData(ProfileFragment.Member member, boolean divider) {
        this.member = member;
        needDivider = divider;
        setWillNotDraw(!divider);
        setAdminRole(member == null ? null : member.role);
        if (member == null) {
            nameTextView.setText(""); statusTextView.setText("");
            avatarImageView.setImageDrawable(null);
        } else {
            CharSequence name = member.name == null ? "" : member.name;
            nameTextView.setText(Emoji.replaceEmoji(name, nameTextView.getPaint().getFontMetricsInt(), false));
            statusTextView.setText(member.status);
            avatarDrawable.setInfo(member.id, name.toString(), null);
            avatarImageView.setImageDrawable(member.photo == null ? avatarDrawable : member.photo);
        }
        updateColors();
        requestLayout();
        invalidate();
    }

    public void setAdminRole(CharSequence role) {
        if (adminTextView == null) {
            return;
        }
        adminTextView.setVisibility(role != null ? VISIBLE : GONE);
        adminTextView.setText(role);
        if (role != null) {
            CharSequence text = adminTextView.getText();
            int size = (int) Math.ceil(adminTextView.getPaint().measureText(text, 0, text.length()));
            setRightPadding(size, true, false);
        } else {
            setRightPadding(0, true, false);
        }
    }

    public void setRightPadding(int pad, boolean top, boolean bottom) {
        if (pad > 0) pad += dp(6);
        if (top) nameTextView.setPadding(isRtl() ? pad : 0, 0, !isRtl() ? pad : 0, 0);
        if (bottom) statusTextView.setPadding(isRtl() ? pad : 0, 0, !isRtl() ? pad : 0, 0);
    }

    public void updateColors() {
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        statusTextView.setTextColor(Theme.getColor(member != null && member.online ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        adminTextView.setTextColor(Theme.getColor(Theme.key_profile_creatorIcon, resourcesProvider));
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(58) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (needDivider) canvas.drawLine(isRtl() ? 0 : dp(68), getMeasuredHeight() - 1, getMeasuredWidth() - (isRtl() ? dp(68) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }
    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) nameTextView.invalidate();
    }
    @Override public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        if (adminTextView != null) applyLayout();
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (member != null) info.setText(TextUtils.concat(member.name == null ? "" : member.name,
                TextUtils.isEmpty(member.status) ? "" : ", " + member.status,
                TextUtils.isEmpty(member.role) ? "" : ", " + member.role));
    }
}
