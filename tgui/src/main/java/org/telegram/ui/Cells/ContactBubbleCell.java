/*
 * Adapted from Telegram for Android release-11.4.2-5469, ChatMessageCell TYPE_CONTACT.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ReplyMessageLine;

/** Host-driven port of ChatMessageCell's contact branch; no account or phonebook lookup. */
public class ContactBubbleCell extends TextBubbleCell {
    public static final int ACTION_VIEW = 0, ACTION_MESSAGE = 1, ACTION_ADD = 2;
    private final RectF contactRect = new RectF(), avatarRect = new RectF();
    private final RectF[] actionRects = {new RectF(), new RectF(), new RectF()};
    private final Rect oldBounds = new Rect();
    private final Path avatarClip = new Path();
    private final ReplyMessageLine contactLine;
    private final ButtonBounce contactBounce;
    private final ButtonBounce[] actionBounces;
    private final AvatarDrawable fallbackAvatar;
    private Drawable emptyAvatar;
    private Drawable cardSelector;
    private final Drawable[] actionSelectors = new Drawable[3];
    private int selectorColor;
    private StaticLayout titleLayout, phoneLayout;
    private final StaticLayout[] actionLayouts = new StaticLayout[3];
    private final int[] actions = new int[2];
    private int actionCount, namesOffset;
    private boolean touching;
    private int pressedAction = -1;
    private float downX, downY;
    private Runnable longPress;

    public ContactBubbleCell(Context context) { this(context, null); }
    public ContactBubbleCell(Context context, Theme.ResourcesProvider provider) {
        super(context, provider);
        contactLine = new ReplyMessageLine(this);
        contactBounce = new ButtonBounce(this, 2f, 2f);
        actionBounces = new ButtonBounce[]{new ButtonBounce(this), new ButtonBounce(this), new ButtonBounce(this)};
        fallbackAvatar = new AvatarDrawable(provider);
    }
    @Override public void setMessage(TextBubbleMessage value, boolean pinnedTop, boolean pinnedBottom) {
        cancelContactTouch();
        if (value == null || value.contact == null) {
            contactRect.setEmpty(); avatarRect.setEmpty(); titleLayout = phoneLayout = null;
            for (RectF rect : actionRects) rect.setEmpty();
        }
        // Contact snapshots can be rebound on the same message object.
        layoutForWidth = -1;
        super.setMessage(value, pinnedTop, pinnedBottom);
        requestLayout();
    }
    @Override protected void buildLayout(int width) {
        layoutForWidth = width; layoutForText = message.text;
        setReactionsLayout();
        TextBubbleMessage.Contact contact = message.contact;
        Theme.chat_contactNamePaint.setTextSize(AndroidUtilities.dp(15));
        Theme.chat_contactPhonePaint.setTextSize(AndroidUtilities.dp(13));
        Theme.chat_instantViewPaint.setTextSize(AndroidUtilities.dp(13));
        int parentWidth = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : width;
        backgroundWidth = Math.max(AndroidUtilities.dp(80), Math.min(parentWidth - AndroidUtilities.dp(incomingAvatarInset() != 0 ? 102 : 50), AndroidUtilities.dp(270)));
        int maxTextWidth = Math.max(AndroidUtilities.dp(10), backgroundWidth - AndroidUtilities.dp(111));
        CharSequence phone = contact == null || TextUtils.isEmpty(contact.phone) ? getContext().getString(R.string.NumberUnknown) : contact.phone;
        CharSequence name = contact == null || TextUtils.isEmpty(contact.name) ? (contact == null || contact.phone == null ? "" : contact.phone) : contact.name;
        name = Emoji.replaceEmoji(AndroidUtilities.replaceNewLines(name), Theme.chat_contactNamePaint.getFontMetricsInt(), false);
        name = TextUtils.ellipsize(name, Theme.chat_contactNamePaint, maxTextWidth, TextUtils.TruncateAt.END);
        titleLayout = new StaticLayout(name, Theme.chat_contactNamePaint, maxTextWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        phoneLayout = new StaticLayout(phone, Theme.chat_contactPhonePaint, maxTextWidth + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1f, AndroidUtilities.dp(1), false);
        measureTime();
        namesOffset = authorHeight + forwardOffset() + buildReplyLayout(backgroundWidth - AndroidUtilities.dp(31) - AndroidUtilities.dp(message.isOut ? 30 : 20), false);
        if (hasForwardLayout()) namesOffset += AndroidUtilities.dp(5);
        else if (hasAuthorLayout() && replyNameLayout == null) namesOffset += AndroidUtilities.dp(7);
        totalHeight = AndroidUtilities.dp(65) + namesOffset + phoneLayout.getHeight();
        if (pinnedTop) namesOffset -= AndroidUtilities.dp(1);
        actionCount = 0;
        if (contact == null || !contact.canMessage && !contact.canAdd) actions[actionCount++] = ACTION_VIEW;
        else {
            if (contact.canMessage) actions[actionCount++] = ACTION_MESSAGE;
            if (contact.canAdd) actions[actionCount++] = ACTION_ADD;
        }
        if (actionCount > 0) totalHeight += AndroidUtilities.dp(60);
        int buttonMax = Math.max(1, (backgroundWidth - AndroidUtilities.dp(75)) / Math.max(1, actionCount));
        for (int i = 0; i < 3; i++) { actionRects[i].setEmpty(); actionLayouts[i] = null; }
        for (int i = 0; i < actionCount; i++) {
            int action = actions[i];
            int label = action == ACTION_VIEW ? R.string.ViewContact : action == ACTION_MESSAGE ? R.string.SharedContactMessage : R.string.SharedContactAdd;
            CharSequence text = TextUtils.ellipsize(getContext().getString(label), Theme.chat_instantViewPaint, buttonMax, TextUtils.TruncateAt.END);
            actionLayouts[action] = new StaticLayout(text, Theme.chat_instantViewPaint, buttonMax + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }
        reactionsLayoutInBubble.measure(backgroundWidth - AndroidUtilities.dp(32), Gravity.LEFT);
        if (!reactionsLayoutInBubble.isEmpty) {
            reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height;
            if (backgroundWidth - AndroidUtilities.dp(32) - reactionsLayoutInBubble.lastLineX < timeWidth) {
                reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(12);
                reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(12);
            }
            totalHeight += reactionsLayoutInBubble.totalHeight;
        }
    }
    @Override protected void drawContent(Canvas canvas) {
        if (message.contact == null || titleLayout == null) return;
        boolean out = message.isOut;
        int left = drawBubbleBackground(canvas);
        drawAuthor(canvas); drawForward(canvas, false); drawReply(canvas, false);
        int avatarX = out ? getMeasuredWidth() - backgroundWidth + AndroidUtilities.dp(26)
                : AndroidUtilities.dp(incomingAvatarInset() != 0 ? 84 : 35);
        int avatarY = AndroidUtilities.dp(24) + namesOffset;
        avatarRect.set(avatarX, avatarY, avatarX + AndroidUtilities.dp(46), avatarY + AndroidUtilities.dp(46));
        int instantY = getHeight() - AndroidUtilities.dp(62) - (reactionsLayoutInBubble.isEmpty ? 0 : reactionsLayoutInBubble.totalHeight);
        int right = currentBackgroundDrawable.getBounds().right - AndroidUtilities.dp(10 + (out && !pinnedBottom ? 6 : 0));
        float textX = avatarX - AndroidUtilities.dp(13);
        contactRect.set(textX, avatarY - AndroidUtilities.dp(9), right, instantY + AndroidUtilities.dp(38));
        int colorId = Math.max(0, message.contact.colorId) % Theme.keys_avatar_nameInMessage.length;
        int nameColor = contactLine.check(colorId, out, resourcesProvider);
        int ripple = Theme.multAlpha(contactLine.getColor(), .1f);
        if (cardSelector == null) {
            cardSelector = Theme.createRadSelectorDrawable(ripple, 6, 6);
            cardSelector.setCallback(this); selectorColor = ripple;
        } else if (selectorColor != ripple) {
            selectorColor = ripple; Theme.setSelectorDrawableColor(cardSelector, ripple, true);
        }
        float rad = (float) Math.floor(SharedConfig.bubbleRadius / 3f);
        int saved = canvas.save();
        float scale = contactBounce.getScale(.0125f);
        canvas.scale(scale, scale, contactRect.centerX(), contactRect.centerY());
        cardSelector.setBounds((int) contactRect.left, (int) contactRect.top, (int) contactRect.right, (int) contactRect.bottom);
        cardSelector.draw(canvas);
        contactLine.drawBackground(canvas, contactRect, rad, rad, rad, 1f);
        contactLine.drawLine(canvas, contactRect);
        drawContactAvatar(canvas, message.contact);
        Theme.chat_contactNamePaint.setColor(nameColor);
        Theme.chat_contactPhonePaint.setColor(getThemedColor(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
        canvas.save(); canvas.translate(avatarRect.right + AndroidUtilities.dp(9), AndroidUtilities.dp(27) + namesOffset); titleLayout.draw(canvas); canvas.restore();
        canvas.save(); canvas.translate(avatarRect.right + AndroidUtilities.dp(9), AndroidUtilities.dp(50) + namesOffset); phoneLayout.draw(canvas); canvas.restore();
        if (actionCount > 0) {
            Theme.chat_instantViewPaint.setColor(nameColor);
            int old = Theme.chat_instantViewPaint.getAlpha();
            Theme.chat_instantViewPaint.setAlpha((int) (old * .18f));
            canvas.drawRect(contactRect.left + AndroidUtilities.dp(10), instantY + AndroidUtilities.dp(2), contactRect.right - AndroidUtilities.dp(7), instantY + AndroidUtilities.dp(2) + Math.max(1, AndroidUtilities.dp(.66f)), Theme.chat_instantViewPaint);
            Theme.chat_instantViewPaint.setAlpha(old);
            float buttonWidth = (backgroundWidth - AndroidUtilities.dpf2(37)) / actionCount;
            float x = textX + AndroidUtilities.dp(3), y = instantY + AndroidUtilities.dp(2);
            for (int i = 0; i < actionCount; i++) {
                int action = actions[i]; StaticLayout layout = actionLayouts[action];
                actionRects[action].set(x, y, Math.min(x + buttonWidth, contactRect.right), y + AndroidUtilities.dp(36));
                if (actionCount > 1) {
                    if (actionSelectors[action] == null) {
                        actionSelectors[action] = Theme.createRadSelectorDrawable(contactLine.getBackgroundColor(), 0, 0, i == actionCount - 1 ? (int) rad : 0, 0);
                        actionSelectors[action].setCallback(this);
                    } else Theme.setSelectorDrawableColor(actionSelectors[action], contactLine.getBackgroundColor(), true);
                    actionSelectors[action].setBounds((int) actionRects[action].left, (int) actionRects[action].top, (int) actionRects[action].right, (int) actionRects[action].bottom);
                    actionSelectors[action].draw(canvas);
                }
                canvas.save(); float factor = actionCount > 1 ? actionBounces[action].getScale(.02f) : 1f;
                canvas.scale(factor, factor, actionRects[action].centerX(), actionRects[action].centerY());
                canvas.translate(x + (buttonWidth - layout.getLineWidth(0)) / 2f - layout.getLineLeft(0), y + AndroidUtilities.dp(10.5f));
                layout.draw(canvas); canvas.restore(); x += buttonWidth;
            }
        }
        canvas.restoreToCount(saved);
        drawReactionsInBubble(canvas, left, 0); drawTimeAndStatus(canvas);
    }
    private void drawContactAvatar(Canvas canvas, TextBubbleMessage.Contact contact) {
        Drawable drawable = contact.avatar;
        if (drawable == null && !TextUtils.isEmpty(contact.name)) {
            fallbackAvatar.setInfo(contact.id, contact.name.toString(), null, null, contact.colorId);
            drawable = fallbackAvatar;
        }
        if (drawable == null) {
            if (emptyAvatar == null) emptyAvatar = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_contact);
            Theme.setCombinedDrawableColor(emptyAvatar, getThemedColor(message.isOut ? Theme.key_chat_outContactBackground : Theme.key_chat_inContactBackground), false);
            Theme.setCombinedDrawableColor(emptyAvatar, getThemedColor(message.isOut ? Theme.key_chat_outContactIcon : Theme.key_chat_inContactIcon), true);
            drawable = emptyAvatar;
        }
        oldBounds.set(drawable.getBounds());
        avatarClip.reset(); avatarClip.addRoundRect(avatarRect, AndroidUtilities.dp(22), AndroidUtilities.dp(22), Path.Direction.CW);
        canvas.save(); canvas.clipPath(avatarClip);
        try { drawable.setBounds(Math.round(avatarRect.left), Math.round(avatarRect.top), Math.round(avatarRect.right), Math.round(avatarRect.bottom)); drawable.draw(canvas); }
        finally { drawable.setBounds(oldBounds); canvas.restore(); }
    }
    public void getContactRect(RectF out) { out.set(contactRect); }
    public void getAvatarRect(RectF out) { out.set(avatarRect); }
    public void getActionRect(int action, RectF out) { if (action >= 0 && action < actionRects.length) out.set(actionRects[action]); else out.setEmpty(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (message == null || message.contact == null || isCheckBoxVisible() || mediaDelegate == null) { cancelContactTouch(); return super.onTouchEvent(event); }
        if (checkForwardTouchEvent(event) || checkReplyTouchEvent(event) || checkReactionsTouchEvent(event)) return true;
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!contactRect.contains(x, y)) return super.onTouchEvent(event);
                cancelContactTouch(); touching = true; downX = x; downY = y;
                pressedAction = actionCount == 1 ? actions[0] : ACTION_VIEW;
                if (actionCount > 1) for (int i = 0; i < actionCount; i++) if (actionRects[actions[i]].contains(x, y)) pressedAction = actions[i];
                if (actionCount > 1 && pressedAction != ACTION_VIEW) {
                    actionBounces[pressedAction].setPressed(true);
                    if (actionSelectors[pressedAction] != null) { actionSelectors[pressedAction].setHotspot(x, y); actionSelectors[pressedAction].setState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed}); }
                } else {
                    contactBounce.setPressed(true);
                    if (cardSelector != null) { cardSelector.setHotspot(x, y); cardSelector.setState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed}); }
                }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                longPress = () -> { longPress = null; cancelContactTouch(); onInteractiveLongPress(false); };
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout()); invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if (!touching) return super.onTouchEvent(event);
                float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (!contactRect.contains(x, y) || Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) { cancelContactTouch(); if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false); return false; }
                return true;
            case MotionEvent.ACTION_UP:
                if (!touching) return super.onTouchEvent(event);
                int action = pressedAction; TextBubbleMessage item = message; TextBubbleMessage.Contact contact = item.contact;
                cancelContactTouch(); if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                playSoundEffect(SoundEffectConstants.CLICK); mediaDelegate.onContactAction(this, item, contact, action); return true;
            case MotionEvent.ACTION_CANCEL:
                if (!touching) return super.onTouchEvent(event);
                cancelContactTouch(); if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return touching || super.onTouchEvent(event);
        }
    }
    private void cancelContactTouch() {
        if (longPress != null) { removeCallbacks(longPress); longPress = null; }
        touching = false; pressedAction = -1; contactBounce.setPressed(false);
        for (ButtonBounce bounce : actionBounces) bounce.setPressed(false);
        if (cardSelector != null) cardSelector.setState(new int[]{});
        for (Drawable selector : actionSelectors) if (selector != null) selector.setState(new int[]{});
        invalidate();
    }
    @Override protected boolean verifyDrawable(Drawable drawable) {
        if (drawable == cardSelector) return true;
        for (Drawable selector : actionSelectors) if (drawable == selector) return true;
        return super.verifyDrawable(drawable);
    }
    @Override protected void onDetachedFromWindow() { cancelContactTouch(); super.onDetachedFromWindow(); }
}
