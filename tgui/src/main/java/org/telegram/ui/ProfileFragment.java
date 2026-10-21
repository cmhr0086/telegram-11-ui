/*
 * This is the source code of Telegram for Android v. 11.4.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgui.R;
import org.telegram.tgui.recyclerview.LinearLayoutManager;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

/**
 * tgui: the user profile screen of upstream {@code ProfileActivity}, rewritten onto a plain
 * {@link Profile} model and a host-supplied list of {@link Row}s.
 * <p>
 * The collapsing header is upstream's: {@code needLayout} / {@code setAvatarExpandProgress} /
 * {@code needLayoutText} geometry, the pull-down {@code expandAnimator} that grows the avatar into a
 * full-width square with the name and status on a gradient, the release snap, the floating
 * write-message button and its show/hide animation, Theme keys and drawables. The photo gallery
 * pager, stories, shared media pager, emoji status and the TL/controller plumbing are gone: the
 * expanded avatar is the {@link Profile#photo} drawable itself.
 */
public class ProfileFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    /** Who the page is about. Change fields and call {@link #updateProfile()}. */
    public static class Profile {
        public long id;
        public String name;
        public CharSequence status;
        /** Status drawn in {@link Theme#key_profile_status} (upstream "online"). */
        public boolean online;
        /** Photo shown in the avatar; {@code null} draws {@link AvatarDrawable} initials. Only a photo can be pulled open. */
        public Drawable photo;
        public boolean verified;
        public boolean muted;

        public Profile(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** One entry of the info list under the header, in the cells upstream uses for it. */
    public static class Row {
        /** TextDetailCell: value on top, label below (phone, username). */
        public static final int TYPE_DETAIL = 0;
        /** Multi-line TextDetailCell (bio / about). */
        public static final int TYPE_BIO = 1;
        /** NotificationsCheckCell with a switch. The fragment flips {@link #checked} before calling the listener. */
        public static final int TYPE_NOTIFICATIONS = 2;
        /** TextCell with icon and optional value (shared media counters, actions). */
        public static final int TYPE_TEXT = 3;
        /** Red TextCell ("Block user", "Delete contact"). */
        public static final int TYPE_DESTRUCTIVE = 4;
        /** ShadowSectionCell between sections. */
        public static final int TYPE_SHADOW = 5;

        public interface OnClickListener {
            void onClick(ProfileFragment fragment, Row row, View view);
        }

        public final int type;
        public final int id;
        public CharSequence text;
        public CharSequence value;
        public int icon;
        public boolean checked;
        public OnClickListener onClickListener;

        public Row(int type, int id) {
            this.type = type;
            this.id = id;
        }

        public static Row detail(int id, CharSequence value, CharSequence label) {
            Row row = new Row(TYPE_DETAIL, id);
            row.text = value;
            row.value = label;
            return row;
        }

        public static Row bio(int id, CharSequence about, CharSequence label) {
            Row row = new Row(TYPE_BIO, id);
            row.text = about;
            row.value = label;
            return row;
        }

        public static Row notifications(int id, CharSequence text, CharSequence value, boolean checked) {
            Row row = new Row(TYPE_NOTIFICATIONS, id);
            row.text = text;
            row.value = value;
            row.checked = checked;
            return row;
        }

        public static Row text(int id, int icon, CharSequence text, CharSequence value) {
            Row row = new Row(TYPE_TEXT, id);
            row.icon = icon;
            row.text = text;
            row.value = value;
            return row;
        }

        public static Row destructive(int id, int icon, CharSequence text) {
            Row row = new Row(TYPE_DESTRUCTIVE, id);
            row.icon = icon;
            row.text = text;
            return row;
        }

        public static Row shadow() {
            return new Row(TYPE_SHADOW, 0);
        }

        public Row setOnClickListener(OnClickListener listener) {
            onClickListener = listener;
            return this;
        }
    }

    public interface Delegate {
        default void onMenuItemClick(ProfileFragment fragment, int id) {
        }

        default void onWriteButtonClick(ProfileFragment fragment) {
        }
    }

    private static final int TYPE_BOTTOM_PADDING = 100;
    private static final int OTHER_ITEM = 10;

    private final Profile profile;
    private final ArrayList<Row> rows = new ArrayList<>();
    private final ArrayList<Object[]> menuItems = new ArrayList<>();
    private Delegate delegate;
    private boolean writeButtonAllowed = true;

    private FrameLayout contentView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter listAdapter;
    private TopView topView;
    private FrameLayout avatarContainer;
    private FrameLayout avatarContainer2;
    private AvatarImageView avatarImage;
    private OverlaysView overlaysView;
    private final SimpleTextView[] nameTextView = new SimpleTextView[2];
    private final SimpleTextView[] onlineTextView = new SimpleTextView[2];
    private ImageView writeButton;
    private AnimatorSet writeButtonAnimation;
    private ActionBarMenuItem otherItem;
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private Drawable verifiedDrawable, verifiedCheckDrawable;
    private CrossfadeDrawable verifiedCrossfadeDrawable;
    private Drawable muteDrawable;

    private float extraHeight;
    private float avatarX, avatarY, avatarScale;
    private float nameX, nameY, onlineX, onlineY;
    private float expandProgress;
    private float listViewVelocityY;
    private boolean isPulledDown;
    private boolean allowPullingDown;
    private boolean openingAvatar;
    private ValueAnimator expandAnimator;
    private final float[] expandAnimatorValues = new float[]{0f, 1f};
    private float currentExpanAnimatorFracture;
    private float currentExpandAnimatorValue;
    private final Rect rect = new Rect();

    public ProfileFragment(Profile profile) {
        this.profile = profile;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    /** Adds an entry to the ⋮ menu. Call before the fragment is presented. */
    public void addMenuItem(int id, int icon, CharSequence text) {
        menuItems.add(new Object[]{id, icon, text});
    }

    public void setWriteButtonAllowed(boolean allowed) {
        writeButtonAllowed = allowed;
        if (fragmentView != null) {
            needLayout(true);
        }
    }

    public void setRows(List<Row> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    public List<Row> getRows() {
        return rows;
    }

    /** Rebinds one row after the host changed its fields. */
    public void updateRow(Row row) {
        int index = rows.indexOf(row);
        if (listAdapter != null && index >= 0) {
            listAdapter.notifyItemChanged(index);
        }
    }

    public boolean isPulledDown() {
        return isPulledDown;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        if (expandAnimator != null) {
            expandAnimator.cancel();
        }
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme && fragmentView != null) {
            updateColors();
        }
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context, getResourceProvider()) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                avatarContainer.getHitRect(rect);
                if (rect.contains((int) event.getX(), (int) event.getY())) {
                    return false;
                }
                return super.onTouchEvent(event);
            }
        };
        actionBar.setForceSkipTouches(true);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), true);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setClipContent(true);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet() && !isInBubbleMode());
        return actionBar;
    }

    @Override
    public View createView(Context context) {
        Theme.createProfileResources(context);
        hasOwnBackground = true;
        extraHeight = AndroidUtilities.dp(88f);
        isPulledDown = false;
        allowPullingDown = false;
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (delegate != null) {
                    delegate.onMenuItemClick(ProfileFragment.this, id);
                }
            }
        });
        if (!menuItems.isEmpty()) {
            ActionBarMenu menu = actionBar.createMenu();
            otherItem = menu.addItem(OTHER_ITEM, R.drawable.ic_ab_other);
            otherItem.setContentDescription("More options");
            for (Object[] item : menuItems) {
                otherItem.addSubItem((Integer) item[0], (Integer) item[1], (CharSequence) item[2]);
            }
        }

        contentView = new FrameLayout(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // upstream ProfileActivity contentView.onMeasure: the list's top padding is the screen
                // width, scrolled so the first row sits at 88dp; pulling down can then open a square
                final int width = MeasureSpec.getSize(widthMeasureSpec);
                final int height = MeasureSpec.getSize(heightMeasureSpec);
                final int paddingTop = AndroidUtilities.isTablet() || width > height ? AndroidUtilities.dp(88f) : width;
                final int currentPaddingTop = listView.getPaddingTop();
                if (currentPaddingTop != paddingTop) {
                    ignoreLayout = true;
                    View view = null;
                    int pos = RecyclerView.NO_POSITION;
                    for (int i = 0; i < listView.getChildCount(); i++) {
                        int p = listView.getChildAdapterPosition(listView.getChildAt(i));
                        if (p != RecyclerView.NO_POSITION) {
                            view = listView.getChildAt(i);
                            pos = p;
                            break;
                        }
                    }
                    if (view != null) {
                        int top = view.getTop();
                        if (pos == 0 && !allowPullingDown && top > AndroidUtilities.dp(88)) {
                            top = AndroidUtilities.dp(88);
                        }
                        layoutManager.scrollToPositionWithOffset(pos, top - paddingTop);
                    } else {
                        layoutManager.scrollToPositionWithOffset(0, AndroidUtilities.dp(88) - paddingTop);
                    }
                    listView.setPadding(0, paddingTop, 0, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                checkListViewScroll();
            }
        };
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = contentView;

        listView = new RecyclerListView(context) {

            private VelocityTracker velocityTracker;

            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }

            @Override
            protected void requestChildOnScreen(View child, View focused) {

            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                final int action = e.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    } else {
                        velocityTracker.clear();
                    }
                    velocityTracker.addMovement(e);
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(e);
                        velocityTracker.computeCurrentVelocity(1000);
                        listViewVelocityY = velocityTracker.getYVelocity(e.getPointerId(e.getActionIndex()));
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                }
                final boolean result = super.onTouchEvent(e);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (allowPullingDown) {
                        final View view = layoutManager.findViewByPosition(0);
                        if (view != null) {
                            if (isPulledDown) {
                                final int actionBarHeight = getHeaderTop();
                                listView.smoothScrollBy(0, view.getTop() - listView.getMeasuredWidth() + actionBarHeight, CubicBezierInterpolator.EASE_OUT_QUINT);
                            } else {
                                listView.smoothScrollBy(0, view.getTop() - AndroidUtilities.dp(88), CubicBezierInterpolator.EASE_OUT_QUINT);
                            }
                        }
                    }
                }
                return result;
            }
        };
        listView.setVerticalScrollBarEnabled(false);
        NotificationCenter.getGlobalInstance().listenGlobal(listView, NotificationCenter.emojiLoaded, args -> Emoji.invalidateAll(listView));
        listView.setClipToPadding(false);
        listView.setHideIfEmpty(false);
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                final View view = layoutManager.findViewByPosition(0);
                if (view != null && !openingAvatar) {
                    final int canScroll = view.getTop() - AndroidUtilities.dp(88);
                    if (!allowPullingDown && canScroll > dy) {
                        dy = canScroll;
                        if (canExpandAvatar()) {
                            allowPullingDown = true;
                        }
                    } else if (allowPullingDown) {
                        if (dy >= canScroll) {
                            dy = canScroll;
                            allowPullingDown = false;
                        } else if (listView.getScrollState() == RecyclerListView.SCROLL_STATE_DRAGGING) {
                            if (!isPulledDown) {
                                dy /= 2;
                            }
                        }
                    }
                }
                return super.scrollVerticallyBy(dy, recycler, state);
            }
        };
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        layoutManager.mIgnoreTopPadding = false;
        listView.setLayoutManager(layoutManager);
        listView.setGlowColor(0);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position < 0 || position >= rows.size()) {
                return;
            }
            Row row = rows.get(position);
            if (row.type == Row.TYPE_NOTIFICATIONS) {
                row.checked = !row.checked;
                if (view instanceof NotificationsCheckCell) {
                    ((NotificationsCheckCell) view).setChecked(row.checked);
                }
            }
            if (row.onClickListener != null) {
                row.onClickListener.onClick(this, row, view);
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (openingAvatar && newState != RecyclerView.SCROLL_STATE_SETTLING) {
                    openingAvatar = false;
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkListViewScroll();
            }
        });

        topView = new TopView(context);
        topView.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        contentView.addView(topView);

        avatarContainer = new FrameLayout(context);
        avatarContainer2 = new FrameLayout(context);
        contentView.addView(avatarContainer2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.START, 0, 0, 0, 0));
        avatarContainer.setPivotX(0);
        avatarContainer.setPivotY(0);
        avatarContainer2.addView(avatarContainer, LayoutHelper.createFrame(42, 42, Gravity.TOP | Gravity.LEFT, 64, 0, 0, 0));
        avatarImage = new AvatarImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        avatarImage.setContentDescription("Profile picture");
        avatarContainer.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        avatarImage.setOnClickListener(v -> {
            if (isPulledDown) {
                collapseAvatar();
            } else {
                openAvatar();
            }
        });

        overlaysView = new OverlaysView(context);
        avatarContainer2.addView(overlaysView);

        contentView.addView(actionBar);

        for (int a = 1; a < nameTextView.length; a++) {
            nameTextView[a] = new SimpleTextView(context);
            nameTextView[a].setTextColor(Theme.getColor(Theme.key_profile_title));
            nameTextView[a].setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(4));
            nameTextView[a].setTextSize(18);
            nameTextView[a].setGravity(Gravity.LEFT);
            nameTextView[a].setTypeface(AndroidUtilities.bold());
            nameTextView[a].setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
            nameTextView[a].setPivotX(0);
            nameTextView[a].setPivotY(0);
            nameTextView[a].setScrollNonFitText(true);
            nameTextView[a].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            nameTextView[a].setEllipsizeByGradient(true);
            avatarContainer2.addView(nameTextView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 118, -6, 0, 0));
        }
        for (int a = 1; a < onlineTextView.length; a++) {
            onlineTextView[a] = new SimpleTextView(context);
            onlineTextView[a].setEllipsizeByGradient(true);
            onlineTextView[a].setTextColor(Theme.getColor(Theme.key_avatar_subtitleInProfileBlue));
            onlineTextView[a].setTextSize(14);
            onlineTextView[a].setGravity(Gravity.LEFT);
            onlineTextView[a].setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(2), AndroidUtilities.dp(4), AndroidUtilities.dp(2));
            onlineTextView[a].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            avatarContainer2.addView(onlineTextView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 118 - 4, -2, 8 - 4, 0));
        }

        writeButton = new ImageView(context);
        writeButton.setImageResource(R.drawable.profile_newmsg);
        writeButton.setContentDescription("Send message");
        writeButton.setScaleType(ImageView.ScaleType.CENTER);
        writeButtonSetBackground();
        contentView.addView(writeButton, LayoutHelper.createFrame(60, 60, Gravity.RIGHT | Gravity.TOP, 0, 0, 16, 0));
        writeButton.setOnClickListener(v -> {
            if (writeButton.getTag() != null) {
                return;
            }
            if (delegate != null) {
                delegate.onWriteButtonClick(this);
            }
        });

        expandAnimator = ValueAnimator.ofFloat(0f, 1f);
        expandAnimator.addUpdateListener(anim -> setAvatarExpandProgress(anim.getAnimatedFraction()));
        expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        expandAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                actionBar.setItemsBackgroundColor(isPulledDown ? Theme.ACTION_BAR_WHITE_SELECTOR_COLOR : Theme.getColor(Theme.key_avatar_actionBarSelectorBlue), false);
            }
        });

        updateProfile();
        needLayout(false);
        fixLayout();
        return fragmentView;
    }

    /** Re-reads {@link #getProfile()} into the header. */
    public void updateProfile() {
        if (nameTextView[1] == null) {
            return;
        }
        avatarDrawable.setInfo(profile.id, profile.name, null);
        avatarImage.setImage(profile.photo, avatarDrawable);
        nameTextView[1].setText(profile.name);
        onlineTextView[1].setText(profile.status);
        onlineTextView[1].setTag(profile.online ? Theme.key_profile_status : Theme.key_avatar_subtitleInProfileBlue);
        if (profile.verified) {
            nameTextView[1].setRightDrawable2(getVerifiedCrossfadeDrawable());
        } else if (profile.muted) {
            nameTextView[1].setRightDrawable2(getMuteDrawable());
        } else {
            nameTextView[1].setRightDrawable2(null);
        }
        if (!isPulledDown && (expandAnimator == null || !expandAnimator.isRunning())) {
            onlineTextView[1].setTextColor(getStatusColor());
        }
        if (!canExpandAvatar() && isPulledDown) {
            collapseAvatar();
        }
        needLayout(false);
    }

    private boolean canExpandAvatar() {
        return profile.photo != null && fragmentView != null && !AndroidUtilities.isTablet()
                && fragmentView.getMeasuredWidth() < fragmentView.getMeasuredHeight();
    }

    private int getStatusColor() {
        Object tag = onlineTextView[1].getTag();
        return Theme.getColor(tag instanceof Integer ? (Integer) tag : Theme.key_avatar_subtitleInProfileBlue);
    }

    private int getHeaderTop() {
        return (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
    }

    /** Animates the avatar open, as tapping it does upstream. */
    public void openAvatar() {
        if (!canExpandAvatar() || listView == null) {
            return;
        }
        final View view = layoutManager.findViewByPosition(0);
        if (view == null) {
            return;
        }
        openingAvatar = true;
        allowPullingDown = true;
        listView.smoothScrollBy(0, view.getTop() - listView.getMeasuredWidth() + getHeaderTop(), CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    /** Scrolls the header back to its normal height. */
    public void collapseAvatar() {
        if (listView == null) {
            return;
        }
        final View view = layoutManager.findViewByPosition(0);
        if (view != null) {
            listView.smoothScrollBy(0, view.getTop() - AndroidUtilities.dp(88), CubicBezierInterpolator.EASE_OUT_QUINT);
        }
    }

    private Drawable getVerifiedCrossfadeDrawable() {
        if (verifiedCrossfadeDrawable == null) {
            verifiedDrawable = Theme.profile_verifiedDrawable.getConstantState().newDrawable().mutate();
            verifiedCheckDrawable = Theme.profile_verifiedCheckDrawable.getConstantState().newDrawable().mutate();
            verifiedCrossfadeDrawable = new CrossfadeDrawable(
                    new CombinedDrawable(verifiedDrawable, verifiedCheckDrawable),
                    getParentActivity().getResources().getDrawable(R.drawable.verified_profile).mutate()
            );
        }
        updateVerifiedColors();
        return verifiedCrossfadeDrawable;
    }

    private void updateVerifiedColors() {
        if (verifiedDrawable != null) {
            verifiedDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_verifiedBackground), PorterDuff.Mode.MULTIPLY));
            verifiedCheckDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_verifiedCheck), PorterDuff.Mode.MULTIPLY));
        }
    }

    private Drawable getMuteDrawable() {
        if (muteDrawable == null) {
            muteDrawable = getParentActivity().getResources().getDrawable(R.drawable.list_mute).mutate();
        }
        muteDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_muteIcon), PorterDuff.Mode.MULTIPLY));
        return muteDrawable;
    }

    private void writeButtonSetBackground() {
        if (writeButton == null) return;
        Drawable shadowDrawable = writeButton.getContext().getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable,
                Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_profile_actionBackground), Theme.getColor(Theme.key_profile_actionPressedBackground)),
                0, 0);
        combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
        writeButton.setBackground(combinedDrawable);
        writeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_actionIcon), PorterDuff.Mode.MULTIPLY));
    }

    private void updateColors() {
        if (fragmentView == null) {
            return;
        }
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        if (!isPulledDown) {
            topView.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        }
        final float value = currentExpandAnimatorValue;
        nameTextView[1].setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_profile_title), Color.WHITE, value));
        onlineTextView[1].setTextColor(ColorUtils.blendARGB(getStatusColor(), 0xB3FFFFFF, value));
        actionBar.setItemsColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefaultIcon), Color.WHITE, value), false);
        actionBar.setItemsBackgroundColor(isPulledDown ? Theme.ACTION_BAR_WHITE_SELECTOR_COLOR : Theme.getColor(Theme.key_avatar_actionBarSelectorBlue), false);
        actionBar.setPopupBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), true);
        actionBar.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false, true);
        actionBar.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true, true);
        updateVerifiedColors();
        if (muteDrawable != null) {
            getMuteDrawable();
        }
        avatarDrawable.setInfo(profile.id, profile.name, null);
        avatarImage.invalidate();
        writeButtonSetBackground();
        nameTextView[1].invalidate();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean isLightStatusBar() {
        if (isPulledDown) {
            return false;
        }
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue)) > 0.7f;
    }

    private void checkSystemBarColors() {
        if (getParentActivity() != null && parentLayout != null && parentLayout.getLastFragment() == this) {
            AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), isLightStatusBar());
        }
    }

    private void fixLayout() {
        if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView != null) {
                    checkListViewScroll();
                    needLayout(true);
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return true;
            }
        });
    }

    private void checkListViewScroll() {
        if (listView == null || listView.getVisibility() != View.VISIBLE || listView.getChildCount() <= 0) {
            return;
        }

        int newOffset = 0;
        View child = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            if (listView.getChildAdapterPosition(listView.getChildAt(i)) == 0) {
                child = listView.getChildAt(i);
                break;
            }
        }
        RecyclerListView.Holder holder = child == null ? null : (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child == null ? 0 : child.getTop();
        int adapterPosition = holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
        if (top >= 0 && adapterPosition == 0) {
            newOffset = top;
        }

        if (extraHeight != newOffset) {
            extraHeight = newOffset;
            topView.invalidate();
            needLayout(true);
        }
    }

    // upstream ProfileActivity.needLayout (user profile path; no search/stories/qr/gallery pager)
    private void needLayout(boolean animated) {
        final int newTop = getHeaderTop();

        FrameLayout.LayoutParams layoutParams;
        if (listView != null) {
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            if (layoutParams.topMargin != newTop) {
                layoutParams.topMargin = newTop;
                listView.setLayoutParams(layoutParams);
            }
        }

        if (avatarContainer != null) {
            final float diff = Math.min(1f, extraHeight / AndroidUtilities.dp(88f));

            listView.setTopGlowOffset((int) extraHeight);

            listView.setOverScrollMode(extraHeight > AndroidUtilities.dp(88f) && extraHeight < listView.getMeasuredWidth() - newTop ? View.OVER_SCROLL_NEVER : View.OVER_SCROLL_ALWAYS);

            if (writeButton != null) {
                writeButton.setTranslationY(newTop + extraHeight - AndroidUtilities.dp(29.5f));

                boolean writeButtonVisible = diff > 0.2f && writeButtonAllowed;
                boolean currentVisible = writeButton.getTag() == null;
                if (writeButtonVisible != currentVisible) {
                    if (writeButtonVisible) {
                        writeButton.setTag(null);
                    } else {
                        writeButton.setTag(0);
                    }
                    if (writeButtonAnimation != null) {
                        AnimatorSet old = writeButtonAnimation;
                        writeButtonAnimation = null;
                        old.cancel();
                    }
                    if (animated) {
                        writeButtonAnimation = new AnimatorSet();
                        if (writeButtonVisible) {
                            writeButtonAnimation.setInterpolator(new DecelerateInterpolator());
                            writeButtonAnimation.playTogether(
                                    ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 1.0f),
                                    ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 1.0f),
                                    ObjectAnimator.ofFloat(writeButton, View.ALPHA, 1.0f)
                            );
                        } else {
                            writeButtonAnimation.setInterpolator(new AccelerateInterpolator());
                            writeButtonAnimation.playTogether(
                                    ObjectAnimator.ofFloat(writeButton, View.SCALE_X, 0.2f),
                                    ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, 0.2f),
                                    ObjectAnimator.ofFloat(writeButton, View.ALPHA, 0.0f)
                            );
                        }
                        writeButtonAnimation.setDuration(150);
                        writeButtonAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (writeButtonAnimation != null && writeButtonAnimation.equals(animation)) {
                                    writeButtonAnimation = null;
                                }
                            }
                        });
                        writeButtonAnimation.start();
                    } else {
                        writeButton.setScaleX(writeButtonVisible ? 1.0f : 0.2f);
                        writeButton.setScaleY(writeButtonVisible ? 1.0f : 0.2f);
                        writeButton.setAlpha(writeButtonVisible ? 1.0f : 0.0f);
                    }
                }
            }

            avatarX = -AndroidUtilities.dpf2(47f) * diff;
            avatarY = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2.0f * (1.0f + diff) - 21 * AndroidUtilities.density + 27 * AndroidUtilities.density * diff + actionBar.getTranslationY();

            float h = extraHeight;
            if (h > AndroidUtilities.dp(88f) || isPulledDown) {
                expandProgress = Math.max(0f, Math.min(1f, (h - AndroidUtilities.dp(88f)) / (listView.getMeasuredWidth() - newTop - AndroidUtilities.dp(88f))));
                avatarScale = AndroidUtilities.lerp((42f + 18f) / 42f, (42f + 42f + 18f) / 42f, Math.min(1f, expandProgress * 3f));

                final float durationFactor = Math.min(AndroidUtilities.dpf2(2000f), Math.max(AndroidUtilities.dpf2(1100f), Math.abs(listViewVelocityY))) / AndroidUtilities.dpf2(1100f);

                if (allowPullingDown && (openingAvatar || expandProgress >= 0.33f)) {
                    if (!isPulledDown) {
                        isPulledDown = true;
                        checkSystemBarColors();
                        overlaysView.setOverlaysVisible(true, durationFactor);
                        expandAnimator.cancel();
                        float value = AndroidUtilities.lerp(expandAnimatorValues, currentExpanAnimatorFracture);
                        expandAnimatorValues[0] = value;
                        expandAnimatorValues[1] = 1f;
                        expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                        expandAnimator.setDuration((long) ((1f - value) * 250f / durationFactor));
                        expandAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                expandAnimator.removeListener(this);
                                topView.setBackgroundColor(Color.BLACK);
                            }
                        });
                        expandAnimator.start();
                    }
                    if (!expandAnimator.isRunning()) {
                        // upstream resizes the gallery pager here; the avatar itself is the expanded photo
                        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
                        params.width = (int) (listView.getMeasuredWidth() / avatarScale);
                        params.height = (int) ((h + newTop) / avatarScale);
                        params.leftMargin = 0;
                        avatarContainer.requestLayout();
                        avatarContainer.setScaleX(avatarScale);
                        avatarContainer.setScaleY(avatarScale);
                        avatarContainer.setTranslationX(0);
                        avatarContainer.setTranslationY(0);

                        onlineX = AndroidUtilities.dpf2(16f) - onlineTextView[1].getLeft();
                        nameTextView[1].setTranslationX(AndroidUtilities.dpf2(18f) - nameTextView[1].getLeft());
                        nameTextView[1].setTranslationY(newTop + h - AndroidUtilities.dpf2(38f) - nameTextView[1].getBottom());
                        onlineTextView[1].setTranslationX(onlineX);
                        onlineTextView[1].setTranslationY(newTop + h - AndroidUtilities.dpf2(18f) - onlineTextView[1].getBottom());
                    }
                } else {
                    if (isPulledDown) {
                        isPulledDown = false;
                        checkSystemBarColors();
                        overlaysView.setOverlaysVisible(false, durationFactor);
                        expandAnimator.cancel();

                        float value = AndroidUtilities.lerp(expandAnimatorValues, currentExpanAnimatorFracture);
                        expandAnimatorValues[0] = value;
                        expandAnimatorValues[1] = 0f;
                        expandAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                        expandAnimator.setDuration((long) (value * 250f / durationFactor));
                        topView.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
                        expandAnimator.start();
                    }

                    avatarContainer.setScaleX(avatarScale);
                    avatarContainer.setScaleY(avatarScale);

                    if (expandAnimator == null || !expandAnimator.isRunning()) {
                        refreshNameAndOnlineXY();
                        nameTextView[1].setTranslationX(nameX);
                        nameTextView[1].setTranslationY(nameY);
                        onlineTextView[1].setTranslationX(onlineX);
                        onlineTextView[1].setTranslationY(onlineY);
                    }
                }
            }

            if (extraHeight <= AndroidUtilities.dp(88f)) {
                avatarScale = (42 + 18 * diff) / 42.0f;
                float nameScale = 1.0f + 0.12f * diff;
                if (expandAnimator == null || !expandAnimator.isRunning()) {
                    avatarContainer.setScaleX(avatarScale);
                    avatarContainer.setScaleY(avatarScale);
                    avatarContainer.setTranslationX(avatarX);
                    avatarContainer.setTranslationY((float) Math.ceil(avatarY));
                }
                nameX = -21 * AndroidUtilities.density * diff;
                nameY = (float) Math.floor(avatarY) + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7) * diff;
                onlineX = -21 * AndroidUtilities.density * diff;
                onlineY = (float) Math.floor(avatarY) + AndroidUtilities.dp(24) + (float) Math.floor(11 * AndroidUtilities.density) * diff;
                if (expandAnimator == null || !expandAnimator.isRunning()) {
                    nameTextView[1].setTranslationX(nameX);
                    nameTextView[1].setTranslationY(nameY);
                    onlineTextView[1].setTranslationX(onlineX);
                    onlineTextView[1].setTranslationY(onlineY);
                }
                nameTextView[1].setScaleX(nameScale);
                nameTextView[1].setScaleY(nameScale);
            }

            if (expandAnimator == null || !expandAnimator.isRunning()) {
                needLayoutText(diff);
            }
        }

        if (isPulledDown || (overlaysView != null && overlaysView.animator != null && overlaysView.animator.isRunning())) {
            final ViewGroup.LayoutParams overlaysLp = overlaysView.getLayoutParams();
            overlaysLp.width = listView.getMeasuredWidth();
            overlaysLp.height = (int) (extraHeight + newTop);
            overlaysView.requestLayout();
        }
    }

    private void refreshNameAndOnlineXY() {
        nameX = AndroidUtilities.dp(-21f) + avatarContainer.getMeasuredWidth() * (avatarScale - (42f + 18f) / 42f);
        nameY = (float) Math.floor(avatarY) + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7f) + avatarContainer.getMeasuredHeight() * (avatarScale - (42f + 18f) / 42f) / 2f;
        onlineX = AndroidUtilities.dp(-21f) + avatarContainer.getMeasuredWidth() * (avatarScale - (42f + 18f) / 42f);
        onlineY = (float) Math.floor(avatarY) + AndroidUtilities.dp(24) + (float) Math.floor(11 * AndroidUtilities.density) + avatarContainer.getMeasuredHeight() * (avatarScale - (42f + 18f) / 42f) / 2f;
    }

    // upstream ProfileActivity.setAvatarExpandProgress
    private void setAvatarExpandProgress(float animatedFracture) {
        final int newTop = getHeaderTop();
        final float value = currentExpandAnimatorValue = AndroidUtilities.lerp(expandAnimatorValues, currentExpanAnimatorFracture = animatedFracture);
        avatarContainer.setScaleX(avatarScale);
        avatarContainer.setScaleY(avatarScale);
        avatarContainer.setTranslationX(AndroidUtilities.lerp(avatarX, 0f, value));
        avatarContainer.setTranslationY(AndroidUtilities.lerp((float) Math.ceil(avatarY), 0f, value));
        avatarImage.setRoundRadius((int) AndroidUtilities.lerp(AndroidUtilities.dp(21), 0f, value));

        if (extraHeight > AndroidUtilities.dp(88f) && expandProgress < 0.33f) {
            refreshNameAndOnlineXY();
        }

        if (verifiedCrossfadeDrawable != null) {
            verifiedCrossfadeDrawable.setProgress(value);
        }

        final float k = AndroidUtilities.dpf2(8f);

        final float nameTextViewXEnd = AndroidUtilities.dpf2(18f) - nameTextView[1].getLeft();
        final float nameTextViewYEnd = newTop + extraHeight - AndroidUtilities.dpf2(38f) - nameTextView[1].getBottom();
        final float nameTextViewCx = k + nameX + (nameTextViewXEnd - nameX) / 2f;
        final float nameTextViewCy = k + nameY + (nameTextViewYEnd - nameY) / 2f;
        final float nameTextViewX = (1 - value) * (1 - value) * nameX + 2 * (1 - value) * value * nameTextViewCx + value * value * nameTextViewXEnd;
        final float nameTextViewY = (1 - value) * (1 - value) * nameY + 2 * (1 - value) * value * nameTextViewCy + value * value * nameTextViewYEnd;

        final float onlineTextViewXEnd = AndroidUtilities.dpf2(16f) - onlineTextView[1].getLeft();
        final float onlineTextViewYEnd = newTop + extraHeight - AndroidUtilities.dpf2(18f) - onlineTextView[1].getBottom();
        final float onlineTextViewCx = k + onlineX + (onlineTextViewXEnd - onlineX) / 2f;
        final float onlineTextViewCy = k + onlineY + (onlineTextViewYEnd - onlineY) / 2f;
        final float onlineTextViewX = (1 - value) * (1 - value) * onlineX + 2 * (1 - value) * value * onlineTextViewCx + value * value * onlineTextViewXEnd;
        final float onlineTextViewY = (1 - value) * (1 - value) * onlineY + 2 * (1 - value) * value * onlineTextViewCy + value * value * onlineTextViewYEnd;

        nameTextView[1].setTranslationX(nameTextViewX);
        nameTextView[1].setTranslationY(nameTextViewY);
        onlineTextView[1].setTranslationX(onlineTextViewX);
        onlineTextView[1].setTranslationY(onlineTextViewY);
        onlineTextView[1].setTextColor(ColorUtils.blendARGB(getStatusColor(), 0xB3FFFFFF, value));
        if (extraHeight > AndroidUtilities.dp(88f)) {
            nameTextView[1].setPivotY(AndroidUtilities.lerp(0, nameTextView[1].getMeasuredHeight(), value));
            nameTextView[1].setScaleX(AndroidUtilities.lerp(1.12f, 1.67f, value));
            nameTextView[1].setScaleY(AndroidUtilities.lerp(1.12f, 1.67f, value));
        }

        needLayoutText(Math.min(1f, extraHeight / AndroidUtilities.dp(88f)));

        nameTextView[1].setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_profile_title), Color.WHITE, currentExpandAnimatorValue));
        actionBar.setItemsColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefaultIcon), Color.WHITE, value), false);
        actionBar.setMenuOffsetSuppressed(true);

        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) avatarContainer.getLayoutParams();
        params.width = (int) AndroidUtilities.lerp(AndroidUtilities.dpf2(42f), listView.getMeasuredWidth() / avatarScale, value);
        params.height = (int) AndroidUtilities.lerp(AndroidUtilities.dpf2(42f), (extraHeight + newTop) / avatarScale, value);
        params.leftMargin = (int) AndroidUtilities.lerp(AndroidUtilities.dpf2(64f), 0f, value);
        avatarContainer.requestLayout();
    }

    // upstream ProfileActivity.needLayoutText; only the ⋮ item sits next to the title here
    private void needLayoutText(float diff) {
        FrameLayout.LayoutParams layoutParams;
        float scale = nameTextView[1].getScaleX();
        float maxScale = extraHeight > AndroidUtilities.dp(88f) ? 1.67f : 1.12f;

        if (extraHeight > AndroidUtilities.dp(88f) && scale != maxScale) {
            return;
        }

        int viewWidth = AndroidUtilities.isTablet() ? AndroidUtilities.dp(490) : AndroidUtilities.displaySize.x;
        int buttonsWidth = AndroidUtilities.dp(118 + 8 + 40);
        int minWidth = viewWidth - buttonsWidth;

        int width = (int) (viewWidth - buttonsWidth * Math.max(0.0f, 1.0f - (diff != 1.0f ? diff * 0.15f / (1.0f - diff) : 1.0f)) - nameTextView[1].getTranslationX());
        CharSequence name = nameTextView[1].getText();
        float width2 = nameTextView[1].getPaint().measureText(name == null ? "" : name.toString()) * scale + nameTextView[1].getSideDrawablesSize();
        layoutParams = (FrameLayout.LayoutParams) nameTextView[1].getLayoutParams();
        int prevWidth = layoutParams.width;
        if (width < width2) {
            layoutParams.width = Math.max(minWidth, (int) Math.ceil((width - AndroidUtilities.dp(24)) / (scale + ((maxScale - scale) * 7.0f))));
        } else {
            layoutParams.width = (int) Math.ceil(width2);
        }
        layoutParams.width = (int) Math.min((viewWidth - nameTextView[1].getX()) / scale - AndroidUtilities.dp(8), layoutParams.width);
        if (layoutParams.width != prevWidth) {
            nameTextView[1].requestLayout();
        }

        CharSequence status = onlineTextView[1].getText();
        width2 = onlineTextView[1].getPaint().measureText(status == null ? "" : status.toString()) + onlineTextView[1].getRightDrawableWidth();
        layoutParams = (FrameLayout.LayoutParams) onlineTextView[1].getLayoutParams();
        prevWidth = layoutParams.width;
        layoutParams.rightMargin = (int) Math.ceil(onlineTextView[1].getTranslationX() + AndroidUtilities.dp(8) + AndroidUtilities.dp(40) * (1.0f - diff));
        if (width < width2) {
            layoutParams.width = (int) Math.ceil(width);
        } else {
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
        }
        if (prevWidth != layoutParams.width) {
            onlineTextView[1].requestLayout();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate themeDelegate = this::updateColors;
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_windowBackgroundGray));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundActionBarBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_profile_title));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_subtitleInProfileBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_profile_actionBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_actionBarDefaultIcon));

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCell.class, TextDetailCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        return arrayList;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position < 0 || position >= rows.size()) {
                return false;
            }
            Row row = rows.get(position);
            return row.type != Row.TYPE_SHADOW && (row.onClickListener != null || row.type == Row.TYPE_NOTIFICATIONS);
        }

        @Override
        public int getItemCount() {
            return rows.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position < rows.size() ? rows.get(position).type : TYPE_BOTTOM_PADDING;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case Row.TYPE_DETAIL:
                    view = new TextDetailCell(context, getResourceProvider(), false);
                    break;
                case Row.TYPE_BIO:
                    view = new TextDetailCell(context, getResourceProvider(), true);
                    break;
                case Row.TYPE_NOTIFICATIONS:
                    view = new NotificationsCheckCell(context, 23, 70, false, getResourceProvider());
                    break;
                case Row.TYPE_TEXT:
                case Row.TYPE_DESTRUCTIVE:
                    view = new TextCell(context, getResourceProvider());
                    break;
                case Row.TYPE_SHADOW:
                    view = new ShadowSectionCell(context);
                    break;
                default:
                    // upstream VIEW_TYPE_BOTTOM_PADDING: lets short pages scroll the header fully collapsed
                    view = new View(context) {

                        private int lastPaddingHeight = 0;
                        private int lastListViewHeight = 0;

                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            if (lastListViewHeight != listView.getMeasuredHeight()) {
                                lastPaddingHeight = 0;
                            }
                            lastListViewHeight = listView.getMeasuredHeight();
                            int n = listView.getChildCount();
                            if (n == listAdapter.getItemCount()) {
                                int totalHeight = 0;
                                for (int i = 0; i < n; i++) {
                                    View view = listView.getChildAt(i);
                                    int p = listView.getChildAdapterPosition(view);
                                    if (p >= 0 && p != rows.size()) {
                                        totalHeight += listView.getChildAt(i).getMeasuredHeight();
                                    }
                                }
                                int paddingHeight = (fragmentView == null ? 0 : fragmentView.getMeasuredHeight()) - getHeaderTop() - totalHeight;
                                if (paddingHeight <= 0) {
                                    paddingHeight = 0;
                                }
                                setMeasuredDimension(listView.getMeasuredWidth(), lastPaddingHeight = paddingHeight);
                            } else {
                                setMeasuredDimension(listView.getMeasuredWidth(), lastPaddingHeight);
                            }
                        }
                    };
                    view.setBackground(new ColorDrawable(Color.TRANSPARENT));
                    break;
            }
            if (viewType != Row.TYPE_SHADOW && viewType != TYPE_BOTTOM_PADDING) {
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position >= rows.size()) {
                return;
            }
            Row row = rows.get(position);
            boolean divider = position + 1 < rows.size() && rows.get(position + 1).type != Row.TYPE_SHADOW;
            View view = holder.itemView;
            if (row.type != Row.TYPE_SHADOW) {
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
            switch (row.type) {
                case Row.TYPE_DETAIL:
                case Row.TYPE_BIO: {
                    TextDetailCell cell = (TextDetailCell) view;
                    cell.updateColors();
                    if (row.type == Row.TYPE_BIO) {
                        cell.textView.setSingleLine(false);
                        cell.textView.setMaxLines(Integer.MAX_VALUE);
                        cell.textView.setEllipsize(null);
                    }
                    cell.setTextWithEmojiAndValue(row.text, row.value, false);
                    break;
                }
                case Row.TYPE_NOTIFICATIONS: {
                    NotificationsCheckCell cell = (NotificationsCheckCell) view;
                    cell.updateColors();
                    cell.setTextAndValueAndCheck(row.text, row.value, row.checked, divider);
                    break;
                }
                case Row.TYPE_TEXT: {
                    TextCell cell = (TextCell) view;
                    cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    if (row.icon != 0) {
                        cell.setTextAndValueAndIcon(row.text, row.value, row.icon, divider);
                    } else {
                        cell.setTextAndValue(row.text, row.value, divider);
                    }
                    break;
                }
                case Row.TYPE_DESTRUCTIVE: {
                    TextCell cell = (TextCell) view;
                    cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                    if (row.icon != 0) {
                        cell.setTextAndIcon(row.text, row.icon, divider);
                    } else {
                        cell.setText(row.text, divider);
                    }
                    break;
                }
                case Row.TYPE_SHADOW: {
                    view.setBackground(Theme.getThemedDrawableByKey(context, position + 1 >= rows.size() ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }
    }

    /** upstream ProfileActivity.TopView without the peer-color gradient / emoji pattern. */
    private class TopView extends View {

        private int currentColor;
        private final Paint paint = new Paint();

        public TopView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(widthMeasureSpec) + AndroidUtilities.dp(3));
        }

        @Override
        public void setBackgroundColor(int color) {
            if (color != currentColor) {
                currentColor = color;
                paint.setColor(color);
                invalidate();
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int height = getHeaderTop();
            final float v = extraHeight + height;
            if (v != 0) {
                paint.setColor(currentColor);
                canvas.drawRect(0, 0, getMeasuredWidth(), v, paint);
            }
        }
    }

    /** The avatar: a photo (center-cropped as the expanded square needs) or AvatarDrawable initials. */
    private static class AvatarImageView extends View {

        private Drawable photo;
        private AvatarDrawable placeholder;
        private int roundRadius;
        private final Path clipPath = new Path();
        private final RectF rect = new RectF();

        public AvatarImageView(Context context) {
            super(context);
        }

        public void setImage(Drawable photo, AvatarDrawable placeholder) {
            if (this.photo != null) {
                this.photo.setCallback(null);
            }
            this.photo = photo;
            if (photo != null) {
                photo.setCallback(this);
            }
            this.placeholder = placeholder;
            invalidate();
        }

        public void setRoundRadius(int value) {
            if (roundRadius != value) {
                roundRadius = value;
                invalidate();
            }
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == photo || super.verifyDrawable(who);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            canvas.save();
            if (roundRadius > 0) {
                clipPath.rewind();
                rect.set(0, 0, w, h);
                clipPath.addRoundRect(rect, roundRadius, roundRadius, Path.Direction.CW);
                canvas.clipPath(clipPath);
            }
            if (photo != null) {
                int iw = photo.getIntrinsicWidth(), ih = photo.getIntrinsicHeight();
                if (iw > 0 && ih > 0) {
                    float scale = Math.max(w / (float) iw, h / (float) ih);
                    int dw = (int) Math.ceil(iw * scale), dh = (int) Math.ceil(ih * scale);
                    int x = (w - dw) / 2, y = (h - dh) / 2;
                    photo.setBounds(x, y, x + dw, y + dh);
                } else {
                    photo.setBounds(0, 0, w, h);
                }
                photo.draw(canvas);
            } else if (placeholder != null) {
                placeholder.setBounds(0, 0, w, h);
                placeholder.draw(canvas);
            }
            canvas.restore();
        }
    }

    /** upstream ProfileActivity.OverlaysView: the top / bottom shading over the expanded photo. */
    private class OverlaysView extends View {

        private final Rect topOverlayRect = new Rect();
        private final Rect bottomOverlayRect = new Rect();
        private final GradientDrawable topOverlayGradient;
        private final GradientDrawable bottomOverlayGradient;
        private final Paint backgroundPaint;
        private final ValueAnimator animator;
        private final float[] animatorValues = new float[]{0f, 1f};
        private boolean isOverlaysVisible;
        private float currentAnimationValue;

        public OverlaysView(Context context) {
            super(context);
            setVisibility(GONE);

            topOverlayGradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x42000000, 0});
            topOverlayGradient.setShape(GradientDrawable.RECTANGLE);

            bottomOverlayGradient = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x42000000, 0});
            bottomOverlayGradient.setShape(GradientDrawable.RECTANGLE);

            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(Color.BLACK);
            backgroundPaint.setAlpha(66);
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            animator.addUpdateListener(anim -> {
                float value = AndroidUtilities.lerp(animatorValues, currentAnimationValue = anim.getAnimatedFraction());
                setAlphaValue(value);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!isOverlaysVisible) {
                        setVisibility(GONE);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    setVisibility(VISIBLE);
                }
            });
        }

        public void setAlphaValue(float value) {
            int alpha = (int) (255 * value);
            topOverlayGradient.setAlpha(alpha);
            bottomOverlayGradient.setAlpha(alpha);
            backgroundPaint.setAlpha((int) (66 * value));
            invalidate();
        }

        public void setOverlaysVisible(boolean overlaysVisible, float durationFactor) {
            if (overlaysVisible != isOverlaysVisible) {
                isOverlaysVisible = overlaysVisible;
                animator.cancel();
                final float value = AndroidUtilities.lerp(animatorValues, currentAnimationValue);
                if (overlaysVisible) {
                    animator.setDuration((long) ((1f - value) * 250f / durationFactor));
                } else {
                    animator.setDuration((long) (value * 250f / durationFactor));
                }
                animatorValues[0] = value;
                animatorValues[1] = overlaysVisible ? 1f : 0f;
                animator.start();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            final int actionBarHeight = getHeaderTop();
            final float k = 0.5f;
            topOverlayRect.set(0, 0, w, (int) (actionBarHeight * k));
            bottomOverlayRect.set(0, (int) (h - AndroidUtilities.dp(72f) * k), w, h);
            topOverlayGradient.setBounds(0, topOverlayRect.bottom, w, actionBarHeight + AndroidUtilities.dp(16f));
            bottomOverlayGradient.setBounds(0, h - AndroidUtilities.dp(72f) - AndroidUtilities.dp(24f), w, bottomOverlayRect.top);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            topOverlayGradient.draw(canvas);
            bottomOverlayGradient.draw(canvas);
            canvas.drawRect(topOverlayRect, backgroundPaint);
            canvas.drawRect(bottomOverlayRect, backgroundPaint);
        }
    }
}
