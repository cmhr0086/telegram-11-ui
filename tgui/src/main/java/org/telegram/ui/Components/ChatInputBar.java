package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;

/**
 * @deprecated use {@link ChatActivityEnterView}, the port of the original input; this simple
 * panel is kept for hosts that only need a text field and a send button.
 *
 * tgui: a small message input panel in the style of upstream ChatActivityEnterView — the
 * multi-line message field, the send button with its 150 ms scale/alpha appear animation,
 * the panel background and compose shadow. No emoji panel, voice, attachments or bot keyboard.
 * Put it at the bottom of a {@link SizeNotifierFrameLayout} in an adjustResize window.
 */
@Deprecated
public class ChatInputBar extends FrameLayout {

    public interface Delegate {
        /** @return true to clear the field */
        boolean onSend(CharSequence text);

        default void onTextChanged(CharSequence text) {
        }
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final EditTextBoldCursor messageEditText;
    private final SendButton sendButton;
    private final FrameLayout textFieldContainer;
    private final Paint backgroundPaint = new Paint();
    private AnimatorSet runningAnimation;
    private boolean sendButtonVisible;
    private Delegate delegate;

    public ChatInputBar(Context context) {
        this(context, null);
    }

    public ChatInputBar(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        Theme.createChatResources(context, false);
        setWillNotDraw(false);
        setClipChildren(false);

        textFieldContainer = new FrameLayout(context);
        textFieldContainer.setClipChildren(false);
        textFieldContainer.setClipToPadding(false);
        textFieldContainer.setPadding(0, dp(1), 0, 0);
        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 1, 0, 0));

        FrameLayout messageEditTextContainer = new FrameLayout(context);
        messageEditTextContainer.setClipChildren(false);
        textFieldContainer.addView(messageEditTextContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 48, 0));

        messageEditText = new EditTextBoldCursor(context);
        messageEditText.setIncludeFontPadding(false);
        messageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messageEditText.setInputType(messageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messageEditText.setSingleLine(false);
        messageEditText.setMaxLines(6);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messageEditText.setGravity(Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        messageEditText.setPadding(0, dp(11), 0, dp(12));
        messageEditText.setBackgroundDrawable(null);
        messageEditText.setCursorSize(dp(20));
        messageEditText.setCursorWidth(1.5f);
        messageEditText.setHintText(LocaleController.getString(R.string.TypeMessage));
        messageEditTextContainer.addView(messageEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 16, 0, 2, 1.5f));
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                checkSendButton(true);
                if (delegate != null) {
                    delegate.onTextChanged(s);
                }
            }
        });

        FrameLayout sendButtonContainer = new FrameLayout(context);
        sendButtonContainer.setClipChildren(false);
        sendButtonContainer.setClipToPadding(false);
        textFieldContainer.addView(sendButtonContainer, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.RIGHT));

        sendButton = new SendButton(context);
        sendButton.setVisibility(INVISIBLE);
        sendButton.setContentDescription(LocaleController.getString(R.string.Send));
        sendButton.setSoundEffectsEnabled(false);
        sendButton.setScaleX(0.1f);
        sendButton.setScaleY(0.1f);
        sendButton.setAlpha(0.0f);
        sendButtonContainer.addView(sendButton, LayoutHelper.createFrame(48, 48));
        sendButton.setOnClickListener(v -> send());

        updateColors();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public EditTextBoldCursor getEditText() {
        return messageEditText;
    }

    public View getSendButton() {
        return sendButton;
    }

    public CharSequence getText() {
        return messageEditText.getText();
    }

    public void setText(CharSequence text) {
        messageEditText.setText(text);
        if (text != null) {
            messageEditText.setSelection(text.length());
        }
    }

    public void setHintText(CharSequence hint) {
        messageEditText.setHintText(hint);
    }

    private void send() {
        CharSequence text = AndroidUtilities.getTrimmedString(messageEditText.getText());
        if (text.length() == 0 || delegate == null) {
            return;
        }
        if (delegate.onSend(text)) {
            messageEditText.setText("");
        }
    }

    private void checkSendButton(boolean animated) {
        boolean show = AndroidUtilities.getTrimmedString(messageEditText.getText()).length() > 0;
        if (show == sendButtonVisible) {
            return;
        }
        sendButtonVisible = show;
        if (runningAnimation != null) {
            runningAnimation.cancel();
            runningAnimation = null;
        }
        float value = show ? 1.0f : 0.1f;
        float alpha = show ? 1.0f : 0.0f;
        if (!animated) {
            sendButton.setScaleX(value);
            sendButton.setScaleY(value);
            sendButton.setAlpha(alpha);
            sendButton.setVisibility(show ? VISIBLE : INVISIBLE);
            return;
        }
        sendButton.setVisibility(VISIBLE);
        runningAnimation = new AnimatorSet();
        runningAnimation.playTogether(
                ObjectAnimator.ofFloat(sendButton, View.SCALE_X, value),
                ObjectAnimator.ofFloat(sendButton, View.SCALE_Y, value),
                ObjectAnimator.ofFloat(sendButton, View.ALPHA, alpha)
        );
        runningAnimation.setDuration(150);
        runningAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == runningAnimation) {
                    sendButton.setVisibility(sendButtonVisible ? VISIBLE : INVISIBLE);
                    runningAnimation = null;
                }
            }
        });
        runningAnimation.start();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    /** Re-reads all chat_messagePanel* colours; registered by {@link #getThemeDescriptions()}. */
    public void updateColors() {
        messageEditText.setTextColor(getThemedColor(Theme.key_chat_messagePanelText));
        messageEditText.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkOut));
        messageEditText.setHighlightColor(getThemedColor(Theme.key_chat_inTextSelectionHighlight));
        messageEditText.setHintColor(getThemedColor(Theme.key_chat_messagePanelHint));
        messageEditText.setHintTextColor(getThemedColor(Theme.key_chat_messagePanelHint));
        messageEditText.setCursorColor(getThemedColor(Theme.key_chat_messagePanelCursor));
        messageEditText.setHandlesColor(getThemedColor(Theme.key_chat_TextSelectionCursor));
        sendButton.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_chat_messagePanelSend), 24), 1));
        sendButton.invalidate();
        invalidate();
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate delegate = this::updateColors;
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_messagePanelBackground));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_messagePanelShadow));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_messagePanelText));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_messagePanelHint));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_messagePanelCursor));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_messagePanelSend));
        return descriptions;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable shadow = Theme.chat_composeShadowDrawable;
        int top = 0;
        int bottom = top + shadow.getIntrinsicHeight();
        Theme.setDrawableColor(shadow, getThemedColor(Theme.key_chat_messagePanelShadow));
        shadow.setBounds(0, top, getMeasuredWidth(), bottom);
        shadow.draw(canvas);
        backgroundPaint.setColor(getThemedColor(Theme.key_chat_messagePanelBackground));
        canvas.drawRect(0, bottom, getWidth(), getHeight(), backgroundPaint);
    }

    private class SendButton extends View {

        private final Drawable drawable;
        private int drawableColor;

        public SendButton(Context context) {
            super(context);
            drawable = context.getResources().getDrawable(R.drawable.ic_send).mutate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int color = getThemedColor(Theme.key_chat_messagePanelSend);
            if (color != drawableColor) {
                drawableColor = color;
                drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
            int x = (getMeasuredWidth() - drawable.getIntrinsicWidth()) / 2 + dp(2);
            int y = (getMeasuredHeight() - drawable.getIntrinsicHeight()) / 2;
            drawable.setBounds(x, y, x + drawable.getIntrinsicWidth(), y + drawable.getIntrinsicHeight());
            drawable.draw(canvas);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (getAlpha() <= 0f) {
                return false;
            }
            return super.onTouchEvent(event);
        }
    }
}
