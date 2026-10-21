/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.TelegramUI;

/**
 * tgui: transplant of upstream 11.4.2 {@code ui/Cells/DrawerProfileCell.java} (792 lines): the same
 * views, sizes and gravities (64dp avatar at 16/67, name 15sp bold at 16/28, phone 13sp at 16/9,
 * 59dp accounts arrow, 48dp sun/moon button at 6/90, 148dp + status bar height), the
 * {@code R.raw.sun} Lottie with its frame logic and layer recoloring, {@code applyBackground} and the
 * cats-shadow handling from {@code onDraw}. The user comes from the host ({@link #setUser}); the
 * day/night button calls {@link TelegramUI#toggleDayNight(android.view.View)}. Dropped: emoji status
 * and its reaction effect, premium gradient / star particles, snowflakes, wallpaper-image backgrounds
 * of custom themes and the ThemeActivity long-press.
 */
public class DrawerProfileCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public interface Delegate {
        /** The accounts arrow was tapped; {@code shown} is the new state. */
        default void onAccountsToggled(boolean shown) {
        }

        /** The day/night button switched the theme (the reveal animation is already running). */
        default void onDayNightToggled(boolean toDark) {
        }
    }

    private final BackupImageView avatarImageView;
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final SimpleTextView nameTextView;
    private final TextView phoneTextView;
    private final ImageView shadowView;
    private final ImageView arrowView;
    private final RLottieImageView darkThemeView;
    private static RLottieDrawable sunDrawable;

    private Integer currentColor;
    private Integer currentMoonColor;
    private boolean accountsShown;
    private Delegate delegate;

    public DrawerProfileCell(Context context) {
        super(context);

        shadowView = new ImageView(context);
        shadowView.setVisibility(INVISIBLE);
        shadowView.setScaleType(ImageView.ScaleType.FIT_XY);
        shadowView.setImageResource(R.drawable.bottom_shadow);
        addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, Gravity.LEFT | Gravity.BOTTOM));

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(32));
        addView(avatarImageView, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 67));

        nameTextView = new SimpleTextView(context);
        nameTextView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        nameTextView.setTextSize(15);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameTextView.setEllipsizeByGradient(true);
        nameTextView.setRightDrawableOutside(true);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 52, 28));

        phoneTextView = new TextView(context);
        phoneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        phoneTextView.setLines(1);
        phoneTextView.setMaxLines(1);
        phoneTextView.setSingleLine(true);
        phoneTextView.setGravity(Gravity.LEFT);
        addView(phoneTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 52, 9));

        arrowView = new ImageView(context);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        arrowView.setImageResource(R.drawable.msg_expand);
        addView(arrowView, LayoutHelper.createFrame(59, 59, Gravity.RIGHT | Gravity.BOTTOM));
        arrowView.setOnClickListener(v -> {
            setAccountsShown(!accountsShown, true);
            if (delegate != null) {
                delegate.onAccountsToggled(accountsShown);
            }
        });
        setArrowState(false);

        boolean playDrawable;
        if (playDrawable = sunDrawable == null) {
            sunDrawable = new RLottieDrawable(R.raw.sun, "" + R.raw.sun, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
            sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
            if (Theme.isCurrentThemeDay()) {
                sunDrawable.setCustomEndFrame(0);
                sunDrawable.setCurrentFrame(0);
            } else {
                sunDrawable.setCurrentFrame(35);
                sunDrawable.setCustomEndFrame(36);
            }
        }
        darkThemeView = new RLottieImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (Theme.isCurrentThemeDark()) {
                    info.setText(LocaleController.getString(R.string.AccDescrSwitchToDayTheme));
                } else {
                    info.setText(LocaleController.getString(R.string.AccDescrSwitchToNightTheme));
                }
            }
        };
        darkThemeView.setFocusable(true);
        darkThemeView.setBackground(Theme.createCircleSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0, 0));
        sunDrawable.beginApplyLayerColors();
        int color = Theme.getColor(Theme.key_chats_menuName);
        sunDrawable.setLayerColor("Sunny.**", color);
        sunDrawable.setLayerColor("Path 6.**", color);
        sunDrawable.setLayerColor("Path.**", color);
        sunDrawable.setLayerColor("Path 5.**", color);
        sunDrawable.commitApplyLayerColors();
        darkThemeView.setScaleType(ImageView.ScaleType.CENTER);
        darkThemeView.setAnimation(sunDrawable);
        if (Build.VERSION.SDK_INT >= 21) {
            darkThemeView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(17)));
            Theme.setRippleDrawableForceSoftware((RippleDrawable) darkThemeView.getBackground());
        }
        if (!playDrawable && sunDrawable.getCustomEndFrame() != sunDrawable.getCurrentFrame()) {
            darkThemeView.playAnimation();
        }
        darkThemeView.setOnClickListener(v -> {
            boolean toDark = !Theme.getActiveTheme().isDark();
            sunDrawable.setCustomEndFrame(toDark ? 36 : 0);
            darkThemeView.playAnimation();
            TelegramUI.toggleDayNight(darkThemeView);
            if (delegate != null) {
                delegate.onDayNightToggled(toDark);
            }
        });
        addView(darkThemeView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 6, 90));
        setWillNotDraw(false);
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    /**
     * Upstream setUser: name (Telegram emoji applied), phone line and avatar. A null avatar draws the
     * colored initials for {@code id}.
     */
    public void setUser(long id, String firstName, String lastName, CharSequence phone, Drawable avatar, Bitmap avatarBitmap) {
        avatarDrawable.setInfo(id, firstName, lastName);
        CharSequence text = (firstName != null ? firstName : "") + (lastName != null && lastName.length() > 0 ? " " + lastName : "");
        try {
            text = Emoji.replaceEmoji(text, nameTextView.getPaint().getFontMetricsInt(), false);
        } catch (Exception ignore) {
        }
        nameTextView.setText(text);
        phoneTextView.setText(phone);
        if (avatarBitmap != null) {
            avatarImageView.setImageBitmap(avatarBitmap);
        } else if (avatar != null) {
            avatarImageView.setImageDrawable(avatar);
        } else {
            avatarImageView.setImageDrawable(avatarDrawable);
        }
    }

    public BackupImageView getAvatarImageView() {
        return avatarImageView;
    }

    public boolean isInAvatar(float x, float y) {
        return x >= avatarImageView.getLeft() && x <= avatarImageView.getRight() && y >= avatarImageView.getTop() && y <= avatarImageView.getBottom();
    }

    public boolean isAccountsShown() {
        return accountsShown;
    }

    public void setAccountsShown(boolean value, boolean animated) {
        if (accountsShown == value) {
            return;
        }
        accountsShown = value;
        setArrowState(animated);
    }

    /** Hides the accounts arrow when the host has no account switcher. */
    public void setArrowVisible(boolean visible) {
        arrowView.setVisibility(visible ? VISIBLE : GONE);
    }

    private void setArrowState(boolean animated) {
        final float rotation = accountsShown ? 180.0f : 0.0f;
        if (animated) {
            arrowView.animate().rotation(rotation).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
        } else {
            arrowView.animate().cancel();
            arrowView.setRotation(rotation);
        }
        arrowView.setContentDescription(accountsShown ? LocaleController.getString(R.string.AccDescrHideAccounts) : LocaleController.getString(R.string.AccDescrShowAccounts));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            nameTextView.invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148) + AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        applyBackground(false);
        boolean drawCatsShadow = false;
        int color;
        if (Theme.hasThemeKey(Theme.key_chats_menuTopShadowCats)) {
            color = Theme.getColor(Theme.key_chats_menuTopShadowCats);
            drawCatsShadow = true;
        } else {
            if (Theme.hasThemeKey(Theme.key_chats_menuTopShadow)) {
                color = Theme.getColor(Theme.key_chats_menuTopShadow);
            } else {
                color = Theme.getServiceMessageColor() | 0xff000000;
            }
        }
        if (currentColor == null || currentColor != color) {
            currentColor = color;
            shadowView.getDrawable().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
        color = Theme.getColor(Theme.key_chats_menuName);
        if (currentMoonColor == null || currentMoonColor != color) {
            currentMoonColor = color;
            sunDrawable.beginApplyLayerColors();
            sunDrawable.setLayerColor("Sunny.**", currentMoonColor);
            sunDrawable.setLayerColor("Path 6.**", currentMoonColor);
            sunDrawable.setLayerColor("Path.**", currentMoonColor);
            sunDrawable.setLayerColor("Path 5.**", currentMoonColor);
            sunDrawable.commitApplyLayerColors();
        }
        nameTextView.setTextColor(Theme.getColor(Theme.key_chats_menuName));
        int visibility = drawCatsShadow ? VISIBLE : INVISIBLE;
        if (shadowView.getVisibility() != visibility) {
            shadowView.setVisibility(visibility);
        }
        phoneTextView.setTextColor(Theme.getColor(Theme.key_chats_menuPhoneCats));
        super.onDraw(canvas);
    }

    public Integer applyBackground(boolean force) {
        Integer currentTag = (Integer) getTag();
        int backgroundKey = Theme.hasThemeKey(Theme.key_chats_menuTopBackground) && Theme.getColor(Theme.key_chats_menuTopBackground) != 0 ? Theme.key_chats_menuTopBackground : Theme.key_chats_menuTopBackgroundCats;
        if (force || currentTag == null || backgroundKey != currentTag) {
            setBackgroundColor(Theme.getColor(backgroundKey));
            setTag(backgroundKey);
        }
        return backgroundKey;
    }
}
