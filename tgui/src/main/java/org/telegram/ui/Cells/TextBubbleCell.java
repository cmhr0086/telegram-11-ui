package org.telegram.ui.Cells;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.view.animation.Interpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.MsgClockDrawable;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;

/**
 * tgui: lightweight text-only message bubble. Geometry (paddings, time / check positions,
 * grouped corners) follows upstream ChatMessageCell for plain text messages without avatars,
 * names or media; background is the restored {@link Theme.MessageDrawable}. A
 * {@link TextBubbleMessage#replyTo} draws upstream's reply panel (ReplyMessageLine) at the top.
 * URLs, @mentions and #hashtags are detected like MessageObject.addLinks, drawn in the link colour
 * and pressed with upstream's LinkSpanDrawable highlight ({@link Delegate#onLinkPressed}).
 * Emoji are drawn with the bundled bitmaps ({@link Emoji#replaceEmoji}); a message of only emoji
 * is drawn large without a bubble, with the time on a pill, as upstream's TYPE_EMOJIS.
 */
public class TextBubbleCell extends View {

    protected final Theme.ResourcesProvider resourcesProvider;

    protected TextBubbleMessage message;
    protected boolean pinnedTop;
    protected boolean pinnedBottom;

    private StaticLayout textLayout;
    protected StaticLayout timeLayout;
    protected int layoutForWidth = -1;
    protected CharSequence layoutForText;
    protected String layoutForReaction;
    // chosen reaction chip (ReactionsLayoutInBubble.ReactionButton look: 26dp pill, 20dp emoji, 12dp counter)
    private Drawable reactionDrawable;
    private int reactionWidth;
    private int reactionY;
    private final RectF reactionRect = new RectF();
    private static TextPaint reactionCounterPaint;
    private static android.graphics.Paint reactionPaint;

    // selection mode (ChatMessageCell.setCheckBoxVisible / setChecked / animateCheckboxTranslation / setDrawSelectionBackground)
    private CheckBoxBase checkBox;
    protected boolean checkBoxVisible;
    private boolean checkBoxAnimationInProgress;
    private float checkBoxAnimationProgress;
    private long lastCheckBoxAnimationTime;
    private int checkBoxTranslation;
    protected boolean drawSelectionBackground;
    /** Upstream's full-width selected-row band (key_chat_selectedBackground); hosts can turn it back on. */
    public static boolean drawRowHighlight = false;
    private float selectionProgress;
    private long lastSelectionAnimationTime;
    protected int bubbleBottom;
    private static android.graphics.Paint selectionPaint;

    protected int totalHeight;
    protected int backgroundWidth;
    protected int timeWidth;
    private int textX, textY;
    private int textLeftOffset;
    private int timeX;
    private boolean emojiOnlyMode;
    private TextPaint textPaint;
    protected final RectF timeRect = new RectF();
    /** The bubble drawable picked by the last {@link #drawBubbleBackground} (media cells hand it to RadialProgress2). */
    protected Theme.MessageDrawable currentBackgroundDrawable;
    protected MediaDelegate mediaDelegate;

    // reply panel (ChatMessageCell replyNameLayout / replyTextLayout / replyLine / replySelector)
    protected StaticLayout replyNameLayout;
    protected StaticLayout replyTextLayout;
    protected int replyNameWidth;
    protected int replyTextWidth;
    private int replyNameOffset;
    private int replyTextOffset;
    private int replyTextHeight;
    protected int replyStartX;
    protected int replyStartY;
    protected float replyHeight;
    private boolean needReplyImage;
    protected final RectF replySelectorRect = new RectF();
    protected TextBubbleMessage.ReplyTo layoutForReply;
    private ReplyMessageLine replyLine;
    private Drawable replySelector;
    private int replySelectorColor;
    private float replySelectorRadLeft, replySelectorRadRight;
    private boolean replyPressed;
    private boolean replySelectorPressed, replySelectorCanBePressed;
    private ButtonBounce replyBounce;
    private float replyBounceX, replyBounceY;
    private float replyTouchX, replyTouchY;
    private AnimatedFloat replyPressedFloat;
    private final Path replyImagePath = new Path();
    private final RectF replyImageRect = new RectF();
    protected Delegate delegate;
    private Runnable longPressRunnable;

    // links (ChatMessageCell links / pressedLink / checkTextBlockMotionEvent)
    /** Detect URLs, @mentions and #hashtags in bubble text (upstream MessageObject.addLinks). */
    public static boolean detectLinks = true;
    private static Pattern urlPattern;
    private final LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector(this);
    private LinkSpanDrawable<ClickableSpan> pressedLink;
    private float linkTouchX, linkTouchY;

    // ChatMessageCell.setHighlightedAnimated: the selected bubble + chat_selectedBackground row fade out
    private boolean isHighlightedAnimated;
    private int highlightProgress;
    private long lastHighlightProgressTime;

    private int lastState = -1;
    private long lastMessageId = Long.MIN_VALUE;
    private float statusProgress = 1f;
    private ValueAnimator statusAnimator;

    public TextBubbleCell(Context context) {
        this(context, null);
    }

    public TextBubbleCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        Theme.createChatResources(context, false);
        // upstream ChatMessageCell redraws when emoji bitmaps finish loading
        NotificationCenter.listenEmojiLoading(this);
    }

    /**
     * @param pinnedTop    previous message is from the same side (top corner on the tail side gets the small radius)
     * @param pinnedBottom next message is from the same side (no tail, small bottom corner)
     */
    public void setMessage(TextBubbleMessage message, boolean pinnedTop, boolean pinnedBottom) {
        boolean sameMessage = this.message != null && message != null && lastMessageId == message.id;
        boolean animateStatus = sameMessage && lastState == TextBubbleMessage.STATE_SENDING && message.state != TextBubbleMessage.STATE_SENDING && isAttachedToWindow();
        if (this.message != message || this.pinnedTop != pinnedTop || this.pinnedBottom != pinnedBottom || layoutForText != (message == null ? null : message.text) || !TextUtils.equals(layoutForReaction, message == null ? null : message.reaction) || layoutForReply != (message == null ? null : message.replyTo)) {
            layoutForWidth = -1;
            requestLayout();
        }
        this.message = message;
        this.pinnedTop = pinnedTop;
        this.pinnedBottom = pinnedBottom;
        if (statusAnimator != null) {
            statusAnimator.cancel();
            statusAnimator = null;
        }
        if (!sameMessage) {
            isHighlightedAnimated = false;
            highlightProgress = 0;
            resetReplyPressed();
            if (replyLine != null) {
                replyLine.resetAnimation();
            }
        }
        statusProgress = 1f;
        if (animateStatus) {
            statusProgress = 0f;
            statusAnimator = ValueAnimator.ofFloat(0f, 1f);
            statusAnimator.addUpdateListener(a -> {
                statusProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            statusAnimator.setDuration(220);
            statusAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            statusAnimator.start();
        }
        lastMessageId = message == null ? Long.MIN_VALUE : message.id;
        lastState = message == null ? -1 : message.state;
        invalidate();
    }

    public TextBubbleMessage getMessage() {
        return message;
    }

    /** Callbacks for the media cells (voice / photo / file); text bubbles never call it. */
    public interface MediaDelegate {
        /** The play / download / open button (or the photo, or the file row) was tapped. */
        void onMediaPressed(TextBubbleCell cell, TextBubbleMessage message);

        /** The voice waveform was dragged to {@code progress} (0..1). */
        default void onSeek(TextBubbleCell cell, TextBubbleMessage message, float progress) {
        }
    }

    /** Theme keys the media bubbles read while drawing (ChatActivity.getThemeDescriptions for voice / photo / file cells). */
    public static final int[] MEDIA_THEME_KEYS = {
            Theme.key_chat_inLoader, Theme.key_chat_outLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_outLoaderSelected,
            Theme.key_chat_inMediaIcon, Theme.key_chat_outMediaIcon, Theme.key_chat_inMediaIconSelected, Theme.key_chat_outMediaIconSelected,
            Theme.key_chat_inAudioProgress, Theme.key_chat_outAudioProgress, Theme.key_chat_inAudioSelectedProgress, Theme.key_chat_outAudioSelectedProgress,
            Theme.key_chat_inAudioDurationText, Theme.key_chat_outAudioDurationText, Theme.key_chat_inAudioDurationSelectedText, Theme.key_chat_outAudioDurationSelectedText,
            Theme.key_chat_inVoiceSeekbar, Theme.key_chat_outVoiceSeekbar, Theme.key_chat_inVoiceSeekbarFill, Theme.key_chat_outVoiceSeekbarFill,
            Theme.key_chat_inVoiceSeekbarSelected, Theme.key_chat_outVoiceSeekbarSelected,
            Theme.key_chat_mediaTimeBackground, Theme.key_chat_mediaTimeText, Theme.key_chat_mediaSentCheck, Theme.key_chat_mediaSentClock,
            Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected,
            Theme.key_chat_mediaProgress,
            Theme.key_chat_inFileNameText, Theme.key_chat_outFileNameText, Theme.key_chat_inFileInfoText, Theme.key_chat_outFileInfoText,
            Theme.key_chat_inFileInfoSelectedText, Theme.key_chat_outFileInfoSelectedText,
            Theme.key_chat_inMenu, Theme.key_chat_outMenu, Theme.key_chat_inMenuSelected, Theme.key_chat_outMenuSelected,
    };

    /** Adds a redraw ThemeDescription for every key in {@link #MEDIA_THEME_KEYS}. */
    public static void addMediaThemeDescriptions(ArrayList<ThemeDescription> descriptions, ThemeDescription.ThemeDescriptionDelegate redraw) {
        for (int key : MEDIA_THEME_KEYS) {
            descriptions.add(new ThemeDescription(null, 0, null, null, null, redraw, key));
        }
    }

    public void setMediaDelegate(MediaDelegate delegate) {
        mediaDelegate = delegate;
    }

    /** Host callbacks for the reply quote and links (ChatMessageCellDelegate.didPressReplyMessage / didPressUrl). */
    public interface Delegate {
        /** The reply quote was tapped; {@code replyTo.messageId} names the original message. */
        default void onReplyPressed(TextBubbleCell cell, TextBubbleMessage message, TextBubbleMessage.ReplyTo replyTo) {
        }

        /** A long press started on the quote: hosts usually treat it like a long press on the row. */
        default void onBubbleLongPressed(TextBubbleCell cell, TextBubbleMessage message) {
        }

        /**
         * A link was tapped (ChatMessageCellDelegate.didPressUrl). {@code url} is the URL of web links
         * ("https://..."), or the text itself for "@mention" / "#hashtag".
         */
        default void onLinkPressed(TextBubbleCell cell, TextBubbleMessage message, String url) {
        }

        /** A link was long-pressed (didPressUrl with longPress, where upstream shows the open / copy sheet). */
        default void onLinkLongPressed(TextBubbleCell cell, TextBubbleMessage message, String url) {
            onBubbleLongPressed(cell, message);
        }
    }

    /**
     * upstream MessageObject.addLinks: web links (AndroidUtilities.addLinks with Linkify.WEB_URLS) and
     * addUrlsByPattern's @mentions / #hashtags, all as {@link URLSpanNoUnderline}; returns a copy.
     */
    public static CharSequence addLinks(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        try {
            AndroidUtilities.addLinks(sb, Linkify.WEB_URLS, false, false);
        } catch (Exception ignore) {
        }
        for (URLSpan span : sb.getSpans(0, sb.length(), URLSpan.class)) {
            if (span instanceof URLSpanNoUnderline || span instanceof URLSpanReplacement) {
                continue;
            }
            int start = sb.getSpanStart(span);
            int end = sb.getSpanEnd(span);
            sb.removeSpan(span);
            sb.setSpan(new URLSpanNoUnderline(span.getURL()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        try {
            if (urlPattern == null) {
                urlPattern = Pattern.compile("(^|\\s|\\()@[a-zA-Z\\d_]{1,32}|(^|\\s|\\()#[^0-9][\\w.]+(@[^0-9][\\w.]+)?");
            }
            Matcher matcher = urlPattern.matcher(sb);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                char ch = sb.charAt(start);
                if (ch != '@' && ch != '#') {
                    start++;
                }
                URLSpan[] spans = sb.getSpans(start, end, URLSpan.class);
                if (spans != null && spans.length > 0) {
                    continue;
                }
                sb.setSpan(new URLSpanNoUnderline(sb.subSequence(start, end).toString()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception ignore) {
        }
        return sb;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    /** ChatMessageCell.setHighlightedAnimated: flashes the bubble (scroll-to-reply target), fading out over a second. */
    public void setHighlightedAnimated() {
        isHighlightedAnimated = true;
        highlightProgress = 1000;
        lastHighlightProgressTime = System.currentTimeMillis();
        invalidate();
    }

    /** upstream getHighlightAlpha */
    protected float getHighlightAlpha() {
        float t = 1.0f;
        if (!drawSelectionBackground && isHighlightedAnimated) {
            t *= highlightProgress >= 300 ? 1.0f : highlightProgress / 300.0f;
        }
        return isHighlightedAnimated ? t : 0f;
    }

    protected int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    protected int getParentWidth() {
        if (getParent() instanceof View) {
            int w = ((View) getParent()).getMeasuredWidth();
            if (w > 0) {
                return w;
            }
        }
        return AndroidUtilities.displaySize.x;
    }

    /** Upstream measureTime for a plain time string: fills {@link #timeLayout} / {@link #timeWidth}. */
    protected void measureTime() {
        String time = message.time == null ? "" : message.time;
        timeWidth = (int) Math.ceil(Theme.chat_timePaint.measureText(time));
        timeLayout = new StaticLayout(time, Theme.chat_timePaint, timeWidth + AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    }

    /**
     * ChatMessageCell.setMessageObject's reply block: builds {@link #replyNameLayout} / {@link #replyTextLayout}
     * and their widths for {@link TextBubbleMessage#replyTo}.
     * @param maxWidth upstream getMaxNameWidth() minus the bubble paddings
     * @param textType TYPE_TEXT: media messages get 5dp more below the panel
     * @return the height the panel adds to upstream's namesOffset (0 without a reply)
     */
    protected int buildReplyLayout(int maxWidth, boolean textType) {
        layoutForReply = message.replyTo;
        replyNameLayout = null;
        replyTextLayout = null;
        replyNameWidth = 0;
        replyTextWidth = 0;
        replyTextHeight = 0;
        needReplyImage = false;
        TextBubbleMessage.ReplyTo reply = message.replyTo;
        if (reply == null) {
            return 0;
        }
        final TextPaint textPaint = Theme.chat_replyTextPaint;
        needReplyImage = reply.thumb != null;
        if (needReplyImage) {
            maxWidth -= AndroidUtilities.dp(16) + (textPaint.getTextSize() + Theme.chat_replyNamePaint.getTextSize());
        }
        maxWidth = Math.max(AndroidUtilities.dp(10), maxWidth);
        CharSequence mess = reply.text == null ? "" : reply.text;
        if (mess.length() > 150) {
            mess = mess.subSequence(0, 150);
        }
        mess = AndroidUtilities.replaceNewLines(mess);
        CharSequence stringFinalText = Emoji.replaceEmoji(mess, textPaint.getFontMetricsInt(), AndroidUtilities.dp(14), true);
        stringFinalText = TextUtils.ellipsize(stringFinalText, textPaint, maxWidth, TextUtils.TruncateAt.END);

        CharSequence stringFinalName = reply.name == null ? "" : reply.name;
        try {
            stringFinalName = Emoji.replaceEmoji(stringFinalName, Theme.chat_replyNamePaint.getFontMetricsInt(), AndroidUtilities.dp(14), false);
        } catch (Exception ignore) {
        }
        stringFinalName = TextUtils.ellipsize(AndroidUtilities.replaceCharSequence("\n", stringFinalName, " "), Theme.chat_replyNamePaint, maxWidth, TextUtils.TruncateAt.END);
        replyNameWidth = AndroidUtilities.dp(4) + (needReplyImage ? AndroidUtilities.dp(16) + (int) (textPaint.getTextSize() + Theme.chat_replyNamePaint.getTextSize()) : 0);
        replyNameLayout = new StaticLayout(stringFinalName, Theme.chat_replyNamePaint, maxWidth + AndroidUtilities.dp(6), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        if (replyNameLayout.getLineCount() > 0) {
            replyNameWidth += (int) Math.ceil(replyNameLayout.getLineWidth(0)) + AndroidUtilities.dp(4);
            replyNameOffset = (int) replyNameLayout.getLineLeft(0);
        }
        replyNameWidth += AndroidUtilities.dp(3);

        replyTextWidth = AndroidUtilities.dp(4) + (needReplyImage ? AndroidUtilities.dp(33) : 0);
        replyTextOffset = 0;
        replyTextLayout = new StaticLayout(stringFinalText, 0, stringFinalText.length(), textPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, maxWidth);
        replyTextHeight = replyTextLayout.getHeight();
        int offset = 0;
        offset += Math.max(0, replyTextHeight - AndroidUtilities.dp(3.66f) - textPaint.getTextSize());
        if (replyTextLayout.getLineCount() > 0) {
            int width = 0;
            replyTextOffset = replyTextLayout.getWidth();
            for (int i = 0; i < replyTextLayout.getLineCount(); ++i) {
                width = Math.max(width, (int) Math.ceil(replyTextLayout.getLineWidth(i)));
                int left = (int) Math.ceil(replyTextLayout.getLineLeft(i));
                if (i > 0 && (replyTextOffset == 0) != (left == 0)) {
                    width = replyTextLayout.getWidth();
                }
                replyTextOffset = Math.min(replyTextOffset, left);
            }
            replyTextWidth += width + AndroidUtilities.dp(18);
        }
        if (replyNameWidth > replyTextWidth) {
            replyNameWidth += AndroidUtilities.dp(Math.max(2, SharedConfig.bubbleRadius / 4f));
        }
        offset += AndroidUtilities.dp(20) + (Theme.chat_replyTextPaint.getTextSize() + Theme.chat_replyNamePaint.getTextSize());
        if (!textType) {
            offset += AndroidUtilities.dp(5);
        }
        return offset;
    }

    /**
     * ChatMessageCell.drawNamesLayout's hasReply block for bubbles with a background: the tinted
     * rounded rect, ripple, ReplyMessageLine stripe, optional thumbnail, name and one-line preview.
     * Call after {@link #drawBubbleBackground}; {@code mediaBackground} for photo bubbles.
     */
    protected void drawReply(Canvas canvas, boolean mediaBackground) {
        if (replyNameLayout == null || currentBackgroundDrawable == null || message.replyTo == null) {
            return;
        }
        final boolean out = message.isOut;
        final Rect bounds = currentBackgroundDrawable.getBounds();
        final int backgroundDrawableLeft = bounds.left;
        replyHeight = AndroidUtilities.dp(9) + Theme.chat_replyNamePaint.getTextSize() + Math.max(replyTextHeight - AndroidUtilities.dp(3.66f), Theme.chat_replyTextPaint.getTextSize());
        if (out) {
            replyStartX = backgroundDrawableLeft + AndroidUtilities.dp(12);
        } else if (mediaBackground) {
            replyStartX = backgroundDrawableLeft + AndroidUtilities.dp(12);
        } else {
            replyStartX = backgroundDrawableLeft + AndroidUtilities.dp(pinnedBottom ? 12 : 18);
        }
        replyStartY = AndroidUtilities.dp(12);

        if (replyPressedFloat == null) {
            replyPressedFloat = new AnimatedFloat(this);
        }
        float replyPressedT = replyPressedFloat.set(replySelectorPressed ? 1f : 0f);
        if (replyLine == null) {
            replyLine = new ReplyMessageLine(this);
        }
        Theme.chat_replyNamePaint.setColor(replyLine.check(message.replyTo.colorId, out, resourcesProvider));
        int rippleColor = ColorUtils.setAlphaComponent(replyLine.getColor(), 0x1e);

        float blendPressed = replyPressedT;
        int color;
        if (message.replyTo.media) {
            color = getThemedColor(out ? (drawSelectionBackground ? Theme.key_chat_outReplyMediaMessageSelectedText : Theme.key_chat_outReplyMediaMessageText) : (drawSelectionBackground ? Theme.key_chat_inReplyMediaMessageSelectedText : Theme.key_chat_inReplyMediaMessageText));
            blendPressed = .6f + (blendPressed * .4f);
        } else {
            color = getThemedColor(out ? Theme.key_chat_outReplyMessageText : Theme.key_chat_inReplyMessageText);
        }
        Theme.chat_replyTextPaint.setColor(ColorUtils.blendARGB(color, Theme.adaptHue(color, Theme.chat_replyNamePaint.getColor()), blendPressed));
        Theme.chat_replyTextPaint.linkColor = ColorUtils.blendARGB(color, Theme.adaptHue(color, Theme.chat_replyNamePaint.getColor()), Utilities.clamp(blendPressed * 2, 1f, 0f));

        int offset = AndroidUtilities.dp(10);
        float leftRad, rightRad, bottomRad = Math.min(4f, SharedConfig.bubbleRadius);
        if (!out || !pinnedTop) {
            rightRad = SharedConfig.bubbleRadius * .6f;
        } else {
            rightRad = SharedConfig.bubbleRadius / 3f;
        }
        float right = bounds.right - AndroidUtilities.dp(10 + (out && !mediaBackground && !pinnedBottom ? 6 : 0));
        replySelectorRect.set(
                backgroundDrawableLeft + AndroidUtilities.dp(10 + (!out && !mediaBackground && !pinnedBottom ? 6 : 0)),
                replyStartY - AndroidUtilities.dp(!mediaBackground && pinnedTop ? 2 : 0),
                right,
                replyStartY + replyHeight + AndroidUtilities.dp(4)
        );

        if (replyBounce == null) {
            replyBounce = new ButtonBounce(this, 2.0f, 2.0f);
        }
        int restoreToCount = canvas.getSaveCount();
        canvas.save();
        final float s = replyBounce.getScale(0.0125f);
        canvas.scale(s, s, Utilities.clamp(replyBounceX, replySelectorRect.right, replySelectorRect.left), Utilities.clamp(replyBounceY, replySelectorRect.bottom, replySelectorRect.top));

        leftRad = bottomRad; // line redesign
        replyLine.drawBackground(canvas, replySelectorRect, leftRad, rightRad, bottomRad, 1f);

        if (replySelector == null) {
            replySelector = Theme.createRadSelectorDrawable(replySelectorColor = rippleColor, 0, 0);
            replySelector.setCallback(this);
        }
        replySelector.setBounds((int) replySelectorRect.left, (int) replySelectorRect.top, (int) replySelectorRect.right, (int) replySelectorRect.bottom);
        if (leftRad != replySelectorRadLeft || rightRad != replySelectorRadRight) {
            Theme.setMaskDrawableRad(replySelector, replySelectorRadLeft = leftRad, replySelectorRadRight = rightRad, bottomRad, bottomRad);
        }
        if (rippleColor != replySelectorColor) {
            Theme.setSelectorDrawableColor(replySelector, replySelectorColor = rippleColor, true);
        }
        replySelector.draw(canvas);

        replyLine.drawLine(canvas, replySelectorRect);

        float replyImageSz = 0;
        if (needReplyImage) {
            replyImageSz = Math.min(replySelectorRect.height() - AndroidUtilities.dp(10), AndroidUtilities.dp(7) + Theme.chat_replyNamePaint.getTextSize() + Theme.chat_replyTextPaint.getTextSize());
            float ix = replySelectorRect.left + AndroidUtilities.dp(8);
            float iy = replySelectorRect.top + AndroidUtilities.dp(5);
            drawReplyImage(canvas, message.replyTo.thumb, ix, iy, replyImageSz);
        }

        final float offsetX = -AndroidUtilities.dp(1);
        final float offsetY = AndroidUtilities.dp(3);
        canvas.save();
        canvas.translate(replyStartX + offsetX - replyNameOffset + offset + (needReplyImage ? replyImageSz + AndroidUtilities.dp(3) : 0), replyStartY + offsetY);
        replyNameLayout.draw(canvas);
        canvas.restore();

        if (replyTextLayout != null) {
            canvas.save();
            float left = replyStartX + offset + offsetX;
            if (needReplyImage) {
                left += replyImageSz + AndroidUtilities.dp(3);
            }
            canvas.translate(left, replyStartY + offsetY - AndroidUtilities.dp(1) + Theme.chat_replyNamePaint.getTextSize() + AndroidUtilities.dp(5));
            replyTextLayout.draw(canvas);
            canvas.restore();
        }
        canvas.restoreToCount(restoreToCount);
    }

    /** replyImageReceiver with setRoundRadius(dp(4)): the thumbnail centre-cropped into a square. */
    private void drawReplyImage(Canvas canvas, Drawable thumb, float x, float y, float size) {
        replyImageRect.set(x, y, x + size, y + size);
        replyImagePath.rewind();
        replyImagePath.addRoundRect(replyImageRect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(replyImagePath);
        int dw = Math.max(1, thumb.getIntrinsicWidth());
        int dh = Math.max(1, thumb.getIntrinsicHeight());
        float scale = Math.max(size / dw, size / dh);
        float w = dw * scale, h = dh * scale;
        Rect old = new Rect(thumb.getBounds());
        thumb.setBounds((int) (x + (size - w) / 2), (int) (y + (size - h) / 2), (int) (x + (size + w) / 2), (int) (y + (size + h) / 2));
        thumb.draw(canvas);
        thumb.setBounds(old);
        canvas.restore();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == replySelector;
    }

    private void resetReplyPressed() {
        replyPressed = false;
        replySelectorPressed = false;
        replySelectorCanBePressed = false;
        if (replySelector != null) {
            replySelector.setState(new int[]{});
        }
        if (replyBounce != null) {
            replyBounce.setPressed(false);
        }
        cancelCheckLongPress();
    }

    private void startCheckLongPress() {
        cancelCheckLongPress();
        longPressRunnable = () -> {
            longPressRunnable = null;
            if (pressedLink != null) {
                String url = getUrl(pressedLink.getSpan());
                resetPressedLink();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                if (delegate != null) {
                    delegate.onLinkLongPressed(this, message, url);
                }
                return;
            }
            boolean wasReply = replyPressed;
            resetReplyPressed();
            onInteractiveLongPress(wasReply);
            invalidate();
        };
        postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelCheckLongPress() {
        if (longPressRunnable != null) {
            removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    /** A press on the quote (or a link) lasted the long-press timeout. */
    protected void onInteractiveLongPress(boolean reply) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        if (delegate != null) {
            delegate.onBubbleLongPressed(this, message);
        }
    }

    /**
     * upstream checkReplyTouchEvent. The press claims the gesture (so the row tap does not open the
     * menu) and gives it back to the list once the finger moves past the touch slop, so lists still scroll.
     */
    protected boolean checkReplyTouchEvent(MotionEvent event) {
        if (replyNameLayout == null || message.replyTo == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        boolean inside = replySelectorRect.contains(x, y);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (inside) {
                replyPressed = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                replyTouchX = x;
                replyTouchY = y;
                if (replySelector != null) {
                    replySelector.setHotspot(x, y);
                    replySelectorPressed = false;
                    replySelectorCanBePressed = true;
                    postDelayed(() -> {
                        if (replyPressed && !replySelectorPressed && replySelectorCanBePressed) {
                            replySelectorPressed = true;
                            replySelector.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
                            invalidate();
                        }
                    }, ViewConfiguration.getTapTimeout() / 6);
                    invalidate();
                }
                if (replyBounce != null) {
                    replyBounce.setPressed(true);
                    replyBounceX = x;
                    replyBounceY = y;
                }
                startCheckLongPress();
                return true;
            }
            return false;
        }
        if (!replyPressed) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            cancelCheckLongPress();
            replyPressed = false;
            if (replySelector != null) {
                if (!replySelectorPressed) {
                    replySelector.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
                    post(() -> {
                        replySelector.setState(new int[]{});
                        invalidate();
                    });
                } else {
                    replySelector.setState(new int[]{});
                }
                replySelectorPressed = false;
                replySelectorCanBePressed = false;
                invalidate();
            }
            if (replyBounce != null) {
                replyBounce.setPressed(false);
            }
            playSoundEffect(SoundEffectConstants.CLICK);
            if (delegate != null) {
                delegate.onReplyPressed(this, message, message.replyTo);
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            resetReplyPressed();
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (!inside || Math.abs(x - replyTouchX) > slop || Math.abs(y - replyTouchY) > slop) {
                resetReplyPressed();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                return false;
            }
        }
        return true;
    }

    private String getUrl(ClickableSpan span) {
        if (span instanceof URLSpan) {
            return ((URLSpan) span).getURL();
        }
        CharSequence text = textLayout == null ? null : textLayout.getText();
        if (text instanceof Spanned) {
            Spanned spanned = (Spanned) text;
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            if (start >= 0 && end >= start) {
                return spanned.subSequence(start, end).toString();
            }
        }
        return null;
    }

    private void resetPressedLink() {
        if (pressedLink != null) {
            links.removeLink(pressedLink);
            pressedLink = null;
            invalidate();
        }
    }

    /** upstream checkTextBlockMotionEvent for one text layout: press highlight (LinkPath), tap and long press. */
    protected boolean checkLinkTouchEvent(MotionEvent event) {
        if (textLayout == null || emojiOnlyMode || !(textLayout.getText() instanceof Spanned)) {
            return false;
        }
        final int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            float x = event.getX() - (textX - textLeftOffset);
            float y = event.getY() - textY;
            if (y < 0 || y > textLayout.getHeight()) {
                return false;
            }
            final int line = textLayout.getLineForVertical((int) y);
            final int off = textLayout.getOffsetForHorizontal(line, x);
            final float left = textLayout.getLineLeft(line);
            if (left <= x && left + textLayout.getLineWidth(line) >= x) {
                Spanned buffer = (Spanned) textLayout.getText();
                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                if (link != null && link.length > 0) {
                    resetPressedLink();
                    pressedLink = new LinkSpanDrawable<>(link[0], resourcesProvider, x, y);
                    pressedLink.setColor(getThemedColor(message.isOut ? Theme.key_chat_outLinkSelectBackground : Theme.key_chat_linkSelectBackground));
                    try {
                        LinkPath path = pressedLink.obtainNewPath();
                        int start = buffer.getSpanStart(link[0]);
                        int end = buffer.getSpanEnd(link[0]);
                        path.setCurrentLayout(textLayout, start, 0);
                        textLayout.getSelectionPath(start, end, path);
                    } catch (Exception ignore) {
                    }
                    links.addLink(pressedLink);
                    linkTouchX = event.getX();
                    linkTouchY = event.getY();
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    startCheckLongPress();
                    invalidate();
                    return true;
                }
            }
            return false;
        }
        if (pressedLink == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_UP) {
            cancelCheckLongPress();
            String url = getUrl(pressedLink.getSpan());
            resetPressedLink();
            playSoundEffect(SoundEffectConstants.CLICK);
            if (delegate != null && url != null) {
                delegate.onLinkPressed(this, message, url);
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancelCheckLongPress();
            resetPressedLink();
        } else if (action == MotionEvent.ACTION_MOVE) {
            float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.abs(event.getX() - linkTouchX) > slop || Math.abs(event.getY() - linkTouchY) > slop) {
                cancelCheckLongPress();
                resetPressedLink();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (message == null || isCheckBoxVisible()) {
            return super.onTouchEvent(event);
        }
        if (checkReplyTouchEvent(event) || checkLinkTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    protected void buildLayout(int width) {
        resetPressedLink();
        layoutForWidth = width;
        layoutForText = message.text;
        layoutForReaction = message.reaction;
        boolean out = message.isOut;
        reactionDrawable = null;
        reactionWidth = 0;
        if (message.reaction != null) {
            if (reactionCounterPaint == null) {
                reactionCounterPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                reactionCounterPaint.setTextSize(AndroidUtilities.dp(12));
                reactionCounterPaint.setTypeface(AndroidUtilities.bold());
                reactionPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            }
            reactionDrawable = Emoji.getEmojiDrawable(message.reaction);
            if (reactionDrawable == null) {
                reactionDrawable = Emoji.getEmojiDrawable(Emoji.fixEmoji(message.reaction));
            }
            reactionWidth = AndroidUtilities.dp(8 + 20 + 4 + 10) + (int) Math.ceil(reactionCounterPaint.measureText("1"));
        }

        int maxWidth = Math.min(width, Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)) - AndroidUtilities.dp(80);
        if (AndroidUtilities.isTablet()) {
            maxWidth = Math.min(maxWidth, AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80));
        }
        int textMaxWidth = Math.max(AndroidUtilities.dp(1), maxWidth - AndroidUtilities.dp(31));

        measureTime();
        int timeMore = timeWidth + AndroidUtilities.dp(6);
        if (out) {
            timeMore += AndroidUtilities.dp(20.5f);
        }

        int[] emojiOnly = new int[1];
        CharSequence source = detectLinks ? addLinks(message.text) : message.text;
        CharSequence text = TextUtils.isEmpty(source) ? " " : Emoji.replaceEmoji(source, Theme.chat_msgTextPaint.getFontMetricsInt(), true, emojiOnly);
        TextPaint emojiPaint = getEmojiOnlyPaint(text, emojiOnly[0]);
        emojiOnlyMode = emojiPaint != null;
        textPaint = emojiOnlyMode ? emojiPaint : Theme.chat_msgTextPaint;
        textLayout = new StaticLayout(text, textPaint, textMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        int lineCount = textLayout.getLineCount();
        int maxChildWidth = 0;
        float minLeft = Float.MAX_VALUE;
        boolean hasRtl = false;
        for (int i = 0; i < lineCount; i++) {
            float left = textLayout.getLineLeft(i);
            minLeft = Math.min(minLeft, left);
            if (left > 0 || textLayout.getParagraphDirection(i) == Layout.DIR_RIGHT_TO_LEFT) {
                hasRtl = true;
            }
        }
        if (minLeft == Float.MAX_VALUE) {
            minLeft = 0;
        }
        textLeftOffset = (int) Math.floor(minLeft);
        for (int i = 0; i < lineCount; i++) {
            maxChildWidth = Math.max(maxChildWidth, (int) Math.ceil(textLayout.getLineRight(i) - minLeft));
        }
        int lastLineWidth = (int) Math.ceil(textLayout.getLineRight(lineCount - 1) - minLeft);

        int textHeight = textLayout.getLineBottom(lineCount - 1);
        if (emojiOnlyMode && message.replyTo != null) {
            // a reply needs the bubble: draw the emoji at the regular size
            emojiOnlyMode = false;
            textPaint = Theme.chat_msgTextPaint;
            text = Emoji.replaceEmoji(source, Theme.chat_msgTextPaint.getFontMetricsInt(), true);
            textLayout = new StaticLayout(text, textPaint, textMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            lineCount = textLayout.getLineCount();
            maxChildWidth = 0;
            for (int i = 0; i < lineCount; i++) {
                maxChildWidth = Math.max(maxChildWidth, (int) Math.ceil(textLayout.getLineRight(i) - minLeft));
            }
            lastLineWidth = (int) Math.ceil(textLayout.getLineRight(lineCount - 1) - minLeft);
            textHeight = textLayout.getLineBottom(lineCount - 1);
        }
        if (emojiOnlyMode) {
            layoutForReply = null;
            replyNameLayout = null;
            replyTextLayout = null;
            backgroundWidth = maxChildWidth;
            textY = AndroidUtilities.dp(4);
            totalHeight = textY + textHeight + AndroidUtilities.dp(2 + 17 + 4);
            if (reactionWidth > 0) {
                reactionY = textY + textHeight + AndroidUtilities.dp(4);
                totalHeight += AndroidUtilities.dp(26 + 4);
            }
            return;
        }
        int namesOffset = buildReplyLayout(maxWidth - AndroidUtilities.dp(31) - AndroidUtilities.dp(out ? 20 : 10), true);
        if (pinnedTop) {
            namesOffset -= AndroidUtilities.dp(1);
        }
        totalHeight = textHeight + AndroidUtilities.dp(19.5f) + namesOffset;

        boolean newLineForTime = maxWidth - lastLineWidth < timeMore || hasRtl;
        if (newLineForTime) {
            totalHeight += AndroidUtilities.dp(14);
            backgroundWidth = Math.max(maxChildWidth, lastLineWidth) + AndroidUtilities.dp(31);
            backgroundWidth = Math.max(backgroundWidth, (out ? timeWidth + AndroidUtilities.dp(17) : timeWidth) + AndroidUtilities.dp(31));
        } else {
            int diff = maxChildWidth - lastLineWidth;
            if (diff >= 0 && diff <= timeMore) {
                backgroundWidth = maxChildWidth + timeMore - diff + AndroidUtilities.dp(31);
            } else {
                backgroundWidth = Math.max(maxChildWidth, lastLineWidth + timeMore) + AndroidUtilities.dp(31);
            }
        }
        if (replyNameLayout != null) {
            // upstream maxChildWidth = max(maxChildWidth, replyNameWidth / replyTextWidth)
            backgroundWidth = Math.max(backgroundWidth, Math.max(replyNameWidth, replyTextWidth) + AndroidUtilities.dp(31));
        }
        textY = AndroidUtilities.dp(10) + namesOffset;
        if (reactionWidth > 0) {
            // the chip gets its own row, the time sits at the end of that row
            reactionY = textY + textHeight + AndroidUtilities.dp(6);
            totalHeight = reactionY + AndroidUtilities.dp(26 + 7);
            backgroundWidth = Math.max(maxChildWidth + AndroidUtilities.dp(31), reactionWidth + AndroidUtilities.dp(8) + timeMore + AndroidUtilities.dp(31));
            if (replyNameLayout != null) {
                backgroundWidth = Math.max(backgroundWidth, Math.max(replyNameWidth, replyTextWidth) + AndroidUtilities.dp(31));
            }
        }
    }

    private void drawReaction(Canvas canvas, int x, boolean out) {
        if (reactionWidth <= 0) {
            return;
        }
        reactionRect.set(x, reactionY, x + reactionWidth, reactionY + AndroidUtilities.dp(26));
        reactionPaint.setColor(getThemedColor(out ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground));
        canvas.drawRoundRect(reactionRect, reactionRect.height() / 2f, reactionRect.height() / 2f, reactionPaint);
        if (reactionDrawable != null) {
            int size = AndroidUtilities.dp(20);
            int left = x + AndroidUtilities.dp(8);
            int top = (int) (reactionRect.centerY() - size / 2f);
            reactionDrawable.setBounds(left, top, left + size, top + size);
            reactionDrawable.draw(canvas);
        }
        reactionCounterPaint.setColor(getThemedColor(out ? Theme.key_chat_outReactionButtonTextSelected : Theme.key_chat_inReactionButtonTextSelected));
        canvas.drawText("1", x + AndroidUtilities.dp(8 + 20 + 4), reactionRect.centerY() - (reactionCounterPaint.descent() + reactionCounterPaint.ascent()) / 2f, reactionCounterPaint);
    }

    /**
     * upstream MessageObject.checkEmojiOnly: up to a few bitmap emoji and nothing else (no other
     * spans, no emoji without a bitmap) pick a large paint and resize the emoji spans to it.
     */
    private static TextPaint getEmojiOnlyPaint(CharSequence text, int emojiOnly) {
        if (emojiOnly < 1 || !(text instanceof Spanned) || Theme.chat_msgTextPaintEmoji == null) {
            return null;
        }
        Spanned spanned = (Spanned) text;
        Emoji.EmojiSpan[] spans = spanned.getSpans(0, spanned.length(), Emoji.EmojiSpan.class);
        if (spans == null || spans.length == 0 || emojiOnly - spans.length > 0) {
            return null;
        }
        if (spanned.getSpans(0, spanned.length(), Object.class).length != spans.length) {
            return null;
        }
        TextPaint paint;
        switch (emojiOnly) {
            case 1:
            case 2:
                paint = Theme.chat_msgTextPaintEmoji[2];
                break;
            case 3:
                paint = Theme.chat_msgTextPaintEmoji[3];
                break;
            case 4:
                paint = Theme.chat_msgTextPaintEmoji[4];
                break;
            default:
                paint = Theme.chat_msgTextPaintEmoji[5];
                break;
        }
        int size = (int) (paint.getTextSize() + AndroidUtilities.dp(4));
        for (Emoji.EmojiSpan span : spans) {
            span.replaceFontMetrics(paint.getFontMetricsInt(), size);
        }
        return paint;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = getParentWidth();
        }
        if (message == null) {
            setMeasuredDimension(width, 0);
            return;
        }
        if (layoutForWidth != width || layoutForText != message.text || !TextUtils.equals(layoutForReaction, message.reaction) || layoutForReply != message.replyTo) {
            buildLayout(width);
        }
        setMeasuredDimension(width, totalHeight);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelCheckLongPress();
        resetPressedLink();
        if (checkBox != null) {
            checkBox.onDetachedFromWindow();
        }
        if (statusAnimator != null) {
            statusAnimator.cancel();
            statusAnimator = null;
            statusProgress = 1f;
        }
    }

    /** Upstream ChatMessageCell.setCheckBoxVisible: a 21dp CheckBoxBase slides in from the left, incoming bubbles move 35dp right. */
    public void setCheckBoxVisible(boolean visible, boolean animated) {
        if (visible && checkBox == null) {
            checkBox = new CheckBoxBase(this, 21, resourcesProvider);
            if (isAttachedToWindow()) {
                checkBox.onAttachedToWindow();
            }
        }
        if (checkBoxVisible == visible) {
            if (animated != checkBoxAnimationInProgress && !animated) {
                checkBoxAnimationProgress = visible ? 1.0f : 0.0f;
                invalidate();
            }
            return;
        }
        checkBoxAnimationInProgress = animated;
        checkBoxVisible = visible;
        if (animated) {
            lastCheckBoxAnimationTime = SystemClock.elapsedRealtime();
        } else {
            checkBoxAnimationProgress = visible ? 1.0f : 0.0f;
        }
        invalidate();
    }

    public boolean isCheckBoxVisible() {
        return checkBoxVisible || checkBoxAnimationInProgress;
    }

    /** Upstream setChecked + setDrawSelectionBackground: check mark, selected bubble colors and the row highlight. */
    public void setChecked(boolean checked, boolean animated) {
        if (checkBox != null) {
            checkBox.setChecked(checked, animated);
        }
        if (drawSelectionBackground != checked) {
            drawSelectionBackground = checked;
            lastSelectionAnimationTime = SystemClock.elapsedRealtime();
            if (!animated) {
                selectionProgress = checked ? 1f : 0f;
            }
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (checkBox != null) {
            checkBox.onAttachedToWindow();
        }
    }

    private void animateCheckboxTranslation() {
        if (checkBoxVisible || checkBoxAnimationInProgress) {
            if (checkBoxVisible && checkBoxAnimationProgress == 1.0f || !checkBoxVisible && checkBoxAnimationProgress == 0.0f) {
                checkBoxAnimationInProgress = false;
            }
            Interpolator interpolator = checkBoxVisible ? CubicBezierInterpolator.EASE_OUT : CubicBezierInterpolator.EASE_IN;
            checkBoxTranslation = (int) Math.ceil(interpolator.getInterpolation(checkBoxAnimationProgress) * AndroidUtilities.dp(35));
            if (checkBoxAnimationInProgress) {
                long newTime = SystemClock.elapsedRealtime();
                long dt = newTime - lastCheckBoxAnimationTime;
                lastCheckBoxAnimationTime = newTime;
                if (checkBoxVisible) {
                    checkBoxAnimationProgress = Math.min(1f, checkBoxAnimationProgress + dt / 200.0f);
                } else {
                    checkBoxAnimationProgress = Math.max(0f, checkBoxAnimationProgress - dt / 200.0f);
                }
                invalidate();
            }
        } else {
            checkBoxTranslation = 0;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (message == null || timeLayout == null) {
            return;
        }
        animateCheckboxTranslation();
        // row highlight (ChatActivity's ChatListView: MessageBackgroundDrawable with key_chat_selectedBackground)
        // off by default: the user wants no dark band over the row while pressing / selecting
        float target = drawSelectionBackground && drawRowHighlight ? 1f : 0f;
        if (selectionProgress != target) {
            long newTime = SystemClock.elapsedRealtime();
            float step = (newTime - lastSelectionAnimationTime) / 200f;
            lastSelectionAnimationTime = newTime;
            selectionProgress = target > selectionProgress ? Math.min(target, selectionProgress + step) : Math.max(target, selectionProgress - step);
            invalidate();
        }
        if (isHighlightedAnimated) {
            long newTime = System.currentTimeMillis();
            long dt = Math.abs(newTime - lastHighlightProgressTime);
            if (dt > 17) {
                dt = 17;
            }
            highlightProgress -= dt;
            lastHighlightProgressTime = newTime;
            if (highlightProgress <= 0) {
                highlightProgress = 0;
                isHighlightedAnimated = false;
            }
            invalidate();
        }
        // ChatActivity draws key_chat_selectedBackground over a highlighted row with getHighlightAlpha()
        float rowAlpha = Math.max(selectionProgress, getHighlightAlpha());
        if (rowAlpha > 0) {
            if (selectionPaint == null) {
                selectionPaint = new android.graphics.Paint();
            }
            int color = getThemedColor(Theme.key_chat_selectedBackground);
            selectionPaint.setColor(color);
            selectionPaint.setAlpha((int) (android.graphics.Color.alpha(color) * rowAlpha));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), selectionPaint);
        }
        int restore = canvas.save();
        if (!message.isOut) {
            canvas.translate(checkBoxTranslation, 0);
        }
        drawContent(canvas);
        canvas.restoreToCount(restore);
        if (checkBox != null && (checkBoxVisible || checkBoxAnimationInProgress)) {
            int size = AndroidUtilities.dp(21);
            checkBox.setBounds(AndroidUtilities.dp(8 - 35) + checkBoxTranslation, bubbleBottom - AndroidUtilities.dp(8) - size, size, size);
            checkBox.draw(canvas);
        }
    }

    protected void drawContent(Canvas canvas) {
        final boolean out = message.isOut;
        final int layoutWidth = getMeasuredWidth();
        final int layoutHeight = getMeasuredHeight();

        if (emojiOnlyMode) {
            bubbleBottom = layoutHeight - AndroidUtilities.dp(4);
            drawEmojiOnly(canvas, out, layoutWidth, layoutHeight);
            return;
        }

        int backgroundLeft = drawBubbleBackground(canvas);
        drawReply(canvas, false);

        // --- text
        if (out) {
            textX = backgroundLeft + AndroidUtilities.dp(11);
        } else {
            textX = backgroundLeft + AndroidUtilities.dp(pinnedBottom ? 11 : 17);
        }
        textPaint.setColor(getThemedColor(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
        textPaint.linkColor = getThemedColor(out ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
        canvas.save();
        canvas.translate(textX - textLeftOffset, textY);
        if (links.draw(canvas)) {
            invalidate();
        }
        textLayout.draw(canvas);
        canvas.restore();
        drawReaction(canvas, textX, out);

        drawTimeAndStatus(canvas);
    }

    /**
     * ChatMessageCell.drawBackground for !mediaBackground: picks the (selected / pinned) bubble
     * drawable, sets its bounds and draws shadow + bubble.
     * @return the bubble's left edge
     */
    protected int drawBubbleBackground(Canvas canvas) {
        final boolean out = message.isOut;
        final int layoutWidth = getMeasuredWidth();
        final int layoutHeight = getMeasuredHeight();
        Theme.MessageDrawable drawable;
        if (drawSelectionBackground) {
            if (pinnedBottom) {
                drawable = out ? Theme.chat_msgOutMediaSelectedDrawable : Theme.chat_msgInMediaSelectedDrawable;
            } else {
                drawable = out ? Theme.chat_msgOutSelectedDrawable : Theme.chat_msgInSelectedDrawable;
            }
        } else if (pinnedBottom) {
            drawable = out ? Theme.chat_msgOutMediaDrawable : Theme.chat_msgInMediaDrawable;
        } else {
            drawable = out ? Theme.chat_msgOutDrawable : Theme.chat_msgInDrawable;
        }
        int backgroundLeft;
        int backgroundRight = backgroundWidth - AndroidUtilities.dp(3);
        if (out) {
            backgroundLeft = layoutWidth - backgroundWidth;
            if (pinnedBottom) {
                backgroundRight -= AndroidUtilities.dp(6);
            }
        } else {
            backgroundLeft = AndroidUtilities.dp(3);
            if (pinnedBottom) {
                backgroundRight -= AndroidUtilities.dp(6);
                backgroundLeft += AndroidUtilities.dp(6);
            }
        }
        int offsetBottom;
        if (pinnedBottom && pinnedTop) {
            offsetBottom = 0;
        } else if (pinnedBottom) {
            offsetBottom = AndroidUtilities.dp(1);
        } else {
            offsetBottom = AndroidUtilities.dp(2);
        }
        int backgroundTop = pinnedTop ? 0 : AndroidUtilities.dp(1);
        int backgroundHeight = layoutHeight - offsetBottom;
        if (pinnedTop) {
            backgroundTop -= AndroidUtilities.dp(1);
            backgroundHeight += AndroidUtilities.dp(1);
        }
        if (pinnedBottom) {
            backgroundHeight += AndroidUtilities.dp(1);
        }
        int parentWidth = layoutWidth, parentHeight = AndroidUtilities.displaySize.y;
        if (getParent() instanceof View) {
            View parent = (View) getParent();
            parentWidth = parent.getMeasuredWidth();
            parentHeight = parent.getMeasuredHeight();
        }
        drawable.setTop((int) getY(), parentWidth, parentHeight, 0, 0, 0, pinnedTop, pinnedBottom);
        // getShadowDrawable() may render its cache through this drawable and change its bounds, so set them afterwards
        Drawable shadow = drawable.getShadowDrawable();
        drawable.setBounds(backgroundLeft, backgroundTop, backgroundLeft + backgroundRight, backgroundTop + backgroundHeight);
        bubbleBottom = backgroundTop + backgroundHeight;
        if (shadow != null) {
            shadow.setBounds(drawable.getBounds());
            shadow.draw(canvas);
        }
        drawable.draw(canvas);
        currentBackgroundDrawable = drawable;
        drawHighlightBubble(canvas, pinnedBottom ? (out ? Theme.chat_msgOutMediaSelectedDrawable : Theme.chat_msgInMediaSelectedDrawable) : (out ? Theme.chat_msgOutSelectedDrawable : Theme.chat_msgInSelectedDrawable), parentWidth, parentHeight);
        return backgroundLeft;
    }

    /** drawBackground's isHighlightedAnimated branch: the selected bubble over the normal one with getHighlightAlpha(). */
    protected void drawHighlightBubble(Canvas canvas, Theme.MessageDrawable selected, int parentWidth, int parentHeight) {
        float alpha = getHighlightAlpha();
        if (alpha <= 0 || drawSelectionBackground || selected == null || currentBackgroundDrawable == null || selected == currentBackgroundDrawable) {
            return;
        }
        selected.setTop((int) getY(), parentWidth, parentHeight, 0, 0, 0, pinnedTop, pinnedBottom);
        selected.setBounds(currentBackgroundDrawable.getBounds());
        selected.setAlpha((int) (255 * alpha));
        selected.draw(canvas);
        selected.setAlpha(255);
    }

    /** Upstream drawTimeInternal for !mediaBackground: time at the bubble's bottom right, then the checks. */
    protected void drawTimeAndStatus(Canvas canvas) {
        final boolean out = message.isOut;
        final int layoutWidth = getMeasuredWidth();
        final int layoutHeight = getMeasuredHeight();
        // --- time
        if (out) {
            timeX = layoutWidth - timeWidth - AndroidUtilities.dp(38.5f);
        } else {
            timeX = backgroundWidth - AndroidUtilities.dp(9) - timeWidth;
        }
        Theme.chat_timePaint.setColor(getThemedColor(out ? (drawSelectionBackground ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText) : (drawSelectionBackground ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText)));
        canvas.save();
        canvas.translate(timeX, layoutHeight - AndroidUtilities.dp(pinnedBottom || pinnedTop ? 7.5f : 6.5f) - timeLayout.getHeight());
        timeLayout.draw(canvas);
        canvas.restore();

        // --- status
        if (out) {
            drawStatus(canvas, layoutWidth, layoutHeight, false);
        }
    }

    /**
     * Upstream drawTimeInternal for mediaBackground / TYPE_EMOJIS: the time on a
     * key_chat_mediaTimeBackground pill whose right / bottom edges are at {@code right} / {@code bottom},
     * with white checks for outgoing messages.
     */
    protected void drawTimePill(Canvas canvas, int right, int bottom) {
        drawTimePill(canvas, right, bottom, AndroidUtilities.dp(4));
    }

    /** @param radius pill corner radius: 4dp for emoji, min(8dp, photo corner) over media */
    protected void drawTimePill(Canvas canvas, int right, int bottom, int radius) {
        final boolean out = message.isOut;
        int pillWidth = timeWidth + AndroidUtilities.dp(out ? 32 : 12);
        int pillHeight = AndroidUtilities.dp(17);
        timeRect.set(right - pillWidth, bottom - pillHeight, right, bottom);
        Theme.chat_timeBackgroundPaint.setColor(getThemedColor(Theme.key_chat_mediaTimeBackground));
        canvas.drawRoundRect(timeRect, radius, radius, Theme.chat_timeBackgroundPaint);
        Theme.chat_timePaint.setColor(getThemedColor(Theme.key_chat_mediaTimeText));
        canvas.save();
        canvas.translate(timeRect.left + AndroidUtilities.dp(6), timeRect.centerY() - timeLayout.getHeight() / 2f);
        timeLayout.draw(canvas);
        canvas.restore();
        if (out) {
            drawStatus(canvas, right + AndroidUtilities.dp(13), bottom + AndroidUtilities.dp(4), true);
        }
    }

    private void drawEmojiOnly(Canvas canvas, boolean out, int layoutWidth, int layoutHeight) {
        int pillWidth = timeWidth + AndroidUtilities.dp(out ? 32 : 12);
        int contentRight;
        int contentLeft;
        if (out) {
            contentRight = layoutWidth - AndroidUtilities.dp(11);
            contentLeft = contentRight - backgroundWidth;
        } else {
            contentLeft = AndroidUtilities.dp(17);
            contentRight = contentLeft + Math.max(backgroundWidth, pillWidth);
        }
        canvas.save();
        canvas.translate(contentLeft - textLeftOffset, textY);
        textLayout.draw(canvas);
        canvas.restore();
        drawReaction(canvas, out ? contentRight - reactionWidth : contentLeft, out);

        drawTimePill(canvas, contentRight, layoutHeight - AndroidUtilities.dp(4));
    }

    /** Checks / clock with their right edge at {@code layoutWidth - 18dp}; {@code media}: white ones for the time pill. */
    protected void drawStatus(Canvas canvas, int layoutWidth, int layoutHeight, boolean media) {
        int state = message.state;
        float progress = statusProgress;
        boolean useScale = progress != 1f;
        float scale = 0.5f + 0.5f * progress;
        int checkY = layoutHeight - AndroidUtilities.dp(pinnedBottom || pinnedTop ? 9 : 8);

        if (state == TextBubbleMessage.STATE_SENDING || useScale) {
            MsgClockDrawable clock = Theme.chat_msgClockDrawable;
            float alpha = state == TextBubbleMessage.STATE_SENDING ? 1f : 1f - progress;
            if (alpha > 0) {
                clock.setColor(getThemedColor(media ? Theme.key_chat_mediaSentClock : Theme.key_chat_outSentClock));
                setBounds(clock, layoutWidth - AndroidUtilities.dp(18.5f) - clock.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.5f) - clock.getIntrinsicHeight());
                clock.setAlpha((int) (255 * alpha));
                clock.draw(canvas);
                clock.setAlpha(255);
                if (state == TextBubbleMessage.STATE_SENDING) {
                    invalidate();
                }
            }
        }
        if (state == TextBubbleMessage.STATE_SENDING) {
            return;
        }
        boolean read = state == TextBubbleMessage.STATE_READ;
        Drawable check = media ? Theme.chat_msgMediaCheckDrawable : read ? Theme.chat_msgOutCheckReadDrawable : Theme.chat_msgOutCheckDrawable;
        Theme.setDrawableColor(check, getThemedColor(media ? Theme.key_chat_mediaSentCheck : read ? Theme.key_chat_outSentCheckRead : Theme.key_chat_outSentCheck));
        if (read) {
            setBounds(check, layoutWidth - AndroidUtilities.dp(22.5f) - check.getIntrinsicWidth(), checkY - check.getIntrinsicHeight());
        } else {
            setBounds(check, layoutWidth - AndroidUtilities.dp(18.5f) - check.getIntrinsicWidth(), checkY - check.getIntrinsicHeight());
        }
        drawScaled(canvas, check, useScale, scale, progress);
        if (read) {
            Drawable half = media ? Theme.chat_msgMediaHalfCheckDrawable : Theme.chat_msgOutHalfCheckDrawable;
            Theme.setDrawableColor(half, getThemedColor(media ? Theme.key_chat_mediaSentCheck : Theme.key_chat_outSentCheckRead));
            setBounds(half, layoutWidth - AndroidUtilities.dp(18) - half.getIntrinsicWidth(), checkY - half.getIntrinsicHeight());
            drawScaled(canvas, half, useScale, scale, progress);
        }
    }

    private static void drawScaled(Canvas canvas, Drawable drawable, boolean useScale, float scale, float alpha) {
        if (useScale) {
            canvas.save();
            canvas.scale(scale, scale, drawable.getBounds().centerX(), drawable.getBounds().centerY());
            drawable.setAlpha((int) (255 * alpha));
        }
        drawable.draw(canvas);
        if (useScale) {
            canvas.restore();
            drawable.setAlpha(255);
        }
    }

    private static void setBounds(Drawable drawable, float x, float y) {
        drawable.setBounds((int) x, (int) y, (int) x + drawable.getIntrinsicWidth(), (int) y + drawable.getIntrinsicHeight());
    }
}
