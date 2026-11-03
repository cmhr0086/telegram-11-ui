/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.bots;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import org.telegram.tgui.recyclerview.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StaticLayoutEx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class BotCommandsMenuView extends View {

    final RectF rectTmp = new RectF();
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final MenuDrawable backDrawable = new MenuDrawable() {
        @Override
        public void invalidateSelf() {
            super.invalidateSelf();
            invalidate();
        }
    };
    public boolean expanded;
    float expandProgress;

    private String menuText;
    private final Theme.ResourcesProvider resourcesProvider;
    StaticLayout menuTextLayout;
    private float menuTextWidth;
    boolean isOpened;

    Drawable backgroundDrawable;
    boolean drawBackgroundDrawable = true;

    public BotCommandsMenuView(Context context) { this(context, null); }
    public BotCommandsMenuView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        menuText = context.getString(R.string.BotsMenuTitle);
        updateColors();
        backDrawable.setMiniIcon(true);
        backDrawable.setRotateToBack(false);
        backDrawable.setRotation(0f, false);
        backDrawable.setCallback(this);
        textPaint.setTypeface(AndroidUtilities.bold());
        backDrawable.setRoundCap();
        backgroundDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16), Color.TRANSPARENT, Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider));
        backgroundDrawable.setCallback(this);
        setContentDescription(context.getString(R.string.AccDescrBotMenu));
    }

    public void setDrawBackgroundDrawable(boolean drawBackgroundDrawable) {
        this.drawBackgroundDrawable = drawBackgroundDrawable;
        invalidate();
    }

    public void updateColors() {
        paint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground, resourcesProvider));
        int textColor = Theme.getColor(Theme.key_chat_messagePanelVoiceDuration, resourcesProvider);
        backDrawable.setBackColor(textColor); backDrawable.setIconColor(textColor); textPaint.setColor(textColor);
        if (backgroundDrawable != null) Theme.setSelectorDrawableColor(backgroundDrawable, Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider), true);
        invalidate();
    }

    int lastSize;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = MeasureSpec.getSize(widthMeasureSpec) + MeasureSpec.getSize(heightMeasureSpec) << 16;
        if (lastSize != size || menuTextLayout == null) {
            backDrawable.setBounds(0, 0, AndroidUtilities.dp(24), AndroidUtilities.dp(24));
            textPaint.setTextSize(AndroidUtilities.dp(15));
            lastSize = size;
            CharSequence c = Emoji.replaceEmoji(menuText, textPaint.getFontMetricsInt(), false);
            int w = Math.max(1, (int) (AndroidUtilities.displaySize.x * .6f));
            menuTextLayout = StaticLayoutEx.createStaticLayout(c, textPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false, TextUtils.TruncateAt.END, w, 1);
            menuTextWidth = menuTextLayout.getLineCount() > 0 ? menuTextLayout.getLineWidth(0) : 0;
        }
        onTranslationChanged((menuTextWidth + AndroidUtilities.dp(4)) * expandProgress);
        int width = AndroidUtilities.dp(40);
        if (expanded) {
            width += (int) menuTextWidth + AndroidUtilities.dp(4);
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (menuTextLayout != null) {
            boolean update = false;
            if (expanded && expandProgress != 1f) {
                expandProgress += 16f / 150f;
                if (expandProgress > 1) {
                    expandProgress = 1f;
                } else {
                    invalidate();
                }
                update = true;
            } else if (!expanded && expandProgress != 0) {
                expandProgress -= 16f / 150f;
                if (expandProgress < 0) {
                    expandProgress = 0;
                } else {
                    invalidate();
                }
                update = true;
            }

            float expandProgress = CubicBezierInterpolator.DEFAULT.getInterpolation(this.expandProgress);
            if (update && expandProgress > 0) {
                textPaint.setAlpha((int) (255 * expandProgress));
            }

            if (drawBackgroundDrawable) {
                rectTmp.set(0, 0, AndroidUtilities.dp(40) + (menuTextWidth + AndroidUtilities.dp(4)) * expandProgress, getMeasuredHeight());
                canvas.drawRoundRect(rectTmp, AndroidUtilities.dp(16), AndroidUtilities.dp(16), paint);
                backgroundDrawable.setBounds((int) rectTmp.left, (int) rectTmp.top, (int) rectTmp.right, (int) rectTmp.bottom);
                backgroundDrawable.draw(canvas);
            }

            canvas.save();
            canvas.translate(AndroidUtilities.dp(8), AndroidUtilities.dp(4));
            backDrawable.draw(canvas);
            canvas.restore();

            if (expandProgress > 0) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(34), (getMeasuredHeight() - menuTextLayout.getHeight()) / 2f);
                menuTextLayout.draw(canvas);
                canvas.restore();
            }

            if (update) {
                onTranslationChanged((menuTextWidth + AndroidUtilities.dp(4)) * expandProgress);
            }
        }
        super.dispatchDraw(canvas);
    }

    protected void onTranslationChanged(float translationX) {

    }

    public boolean setMenuText(String menuText) {
        if (menuText == null) {
            menuText = getContext().getString(R.string.BotsMenuTitle);
        }
        boolean changed = this.menuText == null || !this.menuText.equals(menuText);
        this.menuText = menuText;
        menuTextLayout = null;
        requestLayout();
        return changed;
    }

    public void setExpanded(boolean expanded, boolean animated) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            if (!animated) {
                expandProgress = expanded ? 1f : 0f;
            }
            requestLayout();
            invalidate();
        }
    }

    public boolean isOpened() {
        return isOpened;
    }

    public static class BotCommandsAdapter extends RecyclerListView.SelectionAdapter {
        private final Theme.ResourcesProvider resourcesProvider;
        private List<BotCommand> commands = Collections.emptyList();
        public BotCommandsAdapter() { this(null); }
        public BotCommandsAdapter(Theme.ResourcesProvider provider) { resourcesProvider = provider; }
        public void setCommands(List<BotCommand> values) {
            ArrayList<BotCommand> copy = new ArrayList<>(); HashSet<String> ids = new HashSet<>();
            if (values != null) for (BotCommand item : values) {
                if (item == null || !ids.add(item.id)) throw new IllegalArgumentException("command ids must be unique");
                copy.add(item);
            }
            commands = Collections.unmodifiableList(copy); notifyDataSetChanged();
        }
        public List<BotCommand> getCommands() { return commands; }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemCount() { return commands.size(); }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            BotCommandView row = new BotCommandView(parent.getContext(), resourcesProvider);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(row);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) { ((BotCommandView)holder.itemView).bind(commands.get(position)); }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder) { ((BotCommandView)holder.itemView).bind(null); }
    }

    public void setOpened(boolean opened) {
        isOpened = opened;
        backDrawable.setRotation(opened ? 1f : 0f, true);
    }

    public static class BotCommandView extends LinearLayout {
        private final TextView command, description;
        private final Theme.ResourcesProvider resourcesProvider;
        private BotCommand botCommand;
        public BotCommandView(Context context) { this(context, null); }
        public BotCommandView(Context context, Theme.ResourcesProvider provider) {
            super(context); resourcesProvider = provider;
            setOrientation(HORIZONTAL);
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            description = new TextView(context) {
                @Override public void setText(CharSequence text, BufferType type) {
                    super.setText(Emoji.replaceEmoji(text == null ? "" : text, getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false), type);
                }
            };
            NotificationCenter.listenEmojiLoading(description);
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            description.setMaxLines(2); description.setEllipsize(TextUtils.TruncateAt.END);
            addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, AndroidUtilities.dp(8), 0));
            command = new TextView(context); command.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(command, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL));
            updateColors();
        }
        void bind(BotCommand item) {
            botCommand = item;
            command.setText(item == null ? "" : item.command);
            description.setText(item == null ? "" : item.description);
            updateColors();
        }
        public BotCommand getBotCommand() { return botCommand; }
        public String getCommand() { return botCommand == null ? null : botCommand.command; }
        public void updateColors() {
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            command.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || backgroundDrawable == who || backDrawable == who;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        backgroundDrawable.setState(getDrawableState());
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        backgroundDrawable.jumpToCurrentState();
    }
}
