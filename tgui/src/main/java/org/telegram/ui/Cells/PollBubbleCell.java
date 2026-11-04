/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2013-2018.
 */
// Poll layout and interaction adapted from ChatMessageCell, release-11.4.2-5469.
package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CheckBoxBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Host-driven single or multiple choice poll. No account or network state is consulted. */
public class PollBubbleCell extends TextBubbleCell {
    private static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator();
    private static final class Button {
        TextBubbleMessage.PollOption option;
        StaticLayout title;
        AnimatedEmojiSpan.EmojiGroupedSpans spans;
        CheckBoxBase check;
        final RectF hit = new RectF();
        int y, height, percent, previousPercent;
        float fraction, previousFraction, decimal;
        boolean previousChosen;
    }
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final HashSet<String> checked = new HashSet<>();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable chosenIcon;
    private Drawable selector;
    private final RectF rect = new RectF(), actionRect = new RectF();
    private StaticLayout titleLayout, subtitleLayout, infoLayout;
    private AnimatedEmojiSpan.EmojiGroupedSpans titleSpans;
    private long boundId = Long.MIN_VALUE, animationAt, voteLastAt;
    private float voteOffset, voteLength = 360f, voteProgress;
    private boolean voteRising, firstVoteCircle = true;
    private float animation = 1f;
    private boolean previousResults, results, localPending, hasAction, interceptHeld;
    private int namesOffset, pressed = -1, pendingIndex = -1, lastVoteY, subtitleOffset;
    private float downX, downY;
    private Runnable longPress;

    public PollBubbleCell(Context context) { this(context, null); }
    public PollBubbleCell(Context context, Theme.ResourcesProvider provider) {
        super(context, provider);
        chosenIcon = context.getResources().getDrawable(R.drawable.poll_right).mutate();
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeWidth(AndroidUtilities.dp(1));
    }
    @Override public void setMessage(TextBubbleMessage value, boolean pinnedTop, boolean pinnedBottom) {
        cancelTouch();
        boolean changed = value == null || boundId != value.id;
        if (changed) { releaseSpans(); buttons.clear(); checked.clear(); animation = 1f; boundId = value == null ? Long.MIN_VALUE : value.id; }
        localPending = value != null && value.poll != null && value.poll.pending;
        if (!localPending) { pendingIndex = -1; voteLastAt = 0; }
        else if (voteLastAt == 0) voteLastAt = SystemClock.elapsedRealtime();
        layoutForWidth = -1;
        super.setMessage(value, pinnedTop, pinnedBottom);
        if (value == null || value.poll == null) { actionRect.setEmpty(); releaseSpans(); buttons.clear(); }
        requestLayout();
    }
    private void releaseSpans() {
        AnimatedEmojiSpan.release(this, titleSpans); titleSpans = null;
        for (Button b : buttons) { AnimatedEmojiSpan.release(this, b.spans); b.spans = null; if (b.check != null) b.check.onDetachedFromWindow(); }
    }
    private static CharSequence emoji(CharSequence text, android.text.TextPaint paint) {
        CharSequence copy = AnimatedEmojiSpan.cloneSpans(text == null ? "" : text);
        AnimatedEmojiSpan.applyFontMetricsForString(copy, paint);
        return Emoji.replaceEmoji(copy, paint.getFontMetricsInt(), false);
    }
    @Override protected void buildLayout(int width) {
        layoutForWidth = width; layoutForText = message.text;
        TextBubbleMessage.Poll poll = message.poll;
        if (poll == null) { totalHeight = 0; return; }
        HashSet<String> availableIds = new HashSet<>();
        for (TextBubbleMessage.PollOption option : poll.options) availableIds.add(option.id);
        checked.retainAll(availableIds);
        if (poll.pending && !poll.pendingOptionIds.isEmpty()) {
            if (poll.multiple) { checked.clear(); checked.addAll(poll.pendingOptionIds); pendingIndex = -1; }
            else {
                pendingIndex = -1;
                for (int i=0; i<poll.options.size(); i++) if (poll.options.get(i).id.equals(poll.pendingOptionIds.get(0))) { pendingIndex = i; break; }
            }
        }
        setReactionsLayout();
        Theme.chat_audioTitlePaint.setTextSize(AndroidUtilities.dp(16));
        Theme.chat_audioPerformerPaint.setTextSize(AndroidUtilities.dp(15));
        Theme.chat_locationAddressPaint.setTextSize(AndroidUtilities.dp(13));
        Theme.chat_livePaint.setTextSize(AndroidUtilities.dp(12));
        Theme.chat_instantViewPaint.setTextSize(AndroidUtilities.dp(13));
        int side = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : width;
        int max = Math.max(AndroidUtilities.dp(40), Math.min(AndroidUtilities.dp(500), side - AndroidUtilities.dp(80 + (incomingAvatarInset() != 0 ? 52 : 0))));
        backgroundWidth = max + AndroidUtilities.dp(31);
        measureTime();
        namesOffset = authorHeight + forwardOffset() + buildReplyLayout(backgroundWidth - AndroidUtilities.dp(31) - AndroidUtilities.dp(message.isOut ? 30 : 20), false);
        if (hasForwardLayout() && replyNameLayout != null) namesOffset += AndroidUtilities.dp(2);
        titleLayout = new StaticLayout(emoji(poll.question, Theme.chat_audioTitlePaint), Theme.chat_audioTitlePaint, max + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        titleSpans = AnimatedEmojiSpan.update(0, this, titleSpans, titleLayout);
        String type = getContext().getString(poll.closed ? R.string.FinalResults : poll.anonymous ? R.string.AnonymousPoll : R.string.PublicPoll);
        subtitleLayout = new StaticLayout(TextUtils.ellipsize(type, Theme.chat_timePaint, max, TextUtils.TruncateAt.END), Theme.chat_timePaint, max + AndroidUtilities.dp(2), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        boolean titleRtl = false;
        for (int i=0; i<titleLayout.getLineCount(); i++) if (titleLayout.getLineLeft(i)>0) { titleRtl=true; break; }
        boolean uiRtl = getResources().getConfiguration().getLayoutDirection() == android.view.View.LAYOUT_DIRECTION_RTL;
        subtitleOffset = titleRtl && !uiRtl ? (int)Math.ceil(max - subtitleLayout.getLineWidth(0))
                : !titleRtl && uiRtl ? -(int)Math.ceil(subtitleLayout.getLineLeft(0)) : 0;
        int infoWidth = Math.max(1, max - AndroidUtilities.dp(message.isOut ? 28 : 8));
        android.text.TextPaint infoPaint = poll.anonymous && !poll.multiple ? Theme.chat_livePaint : Theme.chat_locationAddressPaint;
        String votes = poll.totalVoters == 0 ? getContext().getString(R.string.NoVotes) : getResources().getQuantityString(R.plurals.tgui_poll_votes, poll.totalVoters, poll.totalVoters);
        infoLayout = new StaticLayout(TextUtils.ellipsize(votes, infoPaint, infoWidth, TextUtils.TruncateAt.END), infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        boolean newResults = poll.closed || poll.isVoted();
        ArrayList<Button> old = new ArrayList<>(buttons);
        boolean oldResults = results;
        if (animation < 1f) advanceAnimation();
        buttons.clear();
        int height = titleLayout.getHeight(), rest = 100, previousPercent = 0, maxVote = 0;
        boolean hasDifferent = false;
        for (TextBubbleMessage.PollOption option : poll.options) {
            Button b = new Button(); b.option = option;
            b.title = new StaticLayout(emoji(option.text, Theme.chat_audioPerformerPaint), Theme.chat_audioPerformerPaint, Math.max(1, max - AndroidUtilities.dp(33)), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            b.spans = AnimatedEmojiSpan.update(0, this, null, b.title);
            b.y = height + AndroidUtilities.dp(52); b.height = b.title.getHeight(); height += b.height + AndroidUtilities.dp(26);
            float exact = newResults && poll.totalVoters > 0 ? 100f * option.voters / poll.totalVoters : 0f;
            b.percent = (int) exact; b.decimal = exact - b.percent;
            if (previousPercent == 0) previousPercent = b.percent;
            else if (b.percent != 0 && previousPercent != b.percent) hasDifferent = true;
            rest -= b.percent; maxVote = Math.max(maxVote, b.percent);
            b.check = new CheckBoxBase(this, 20, resourcesProvider);
            b.check.setDrawUnchecked(false); b.check.setBackgroundType(9);
            if (isAttachedToWindow()) b.check.onAttachedToWindow();
            b.check.setChecked(checked.contains(option.id), false);
            buttons.add(b);
        }
        if (hasDifferent && rest > 0) {
            ArrayList<Button> sorted = new ArrayList<>(buttons);
            Collections.sort(sorted, (a,b) -> { int d = Float.compare(b.decimal, a.decimal); return d != 0 ? d : Integer.compare(a.percent,b.percent); });
            for (int i=0; i<Math.min(rest,sorted.size()); i++) sorted.get(i).percent++;
        }
        int barWidth = Math.max(1, backgroundWidth - AndroidUtilities.dp(76));
        for (Button b : buttons) {
            b.fraction = Math.max(AndroidUtilities.dp(5)/(float)barWidth, maxVote != 0 ? b.percent/(float)maxVote : 0f);
            for (Button prev : old) if (prev.option.id.equals(b.option.id)) {
                b.previousPercent = (int)Math.ceil(prev.previousPercent + (prev.percent - prev.previousPercent) * animation);
                b.previousFraction = prev.previousFraction + (prev.fraction - prev.previousFraction) * animation;
                b.previousChosen = prev.option.chosen; break;
            }
        }
        boolean animate = !old.isEmpty() && isAttachedToWindow() && (newResults || oldResults);
        previousResults = oldResults; results = newResults; animation = animate ? 0f : 1f; animationAt = SystemClock.elapsedRealtime();
        if (!animate) for (Button b : buttons) b.previousChosen = false;
        for (Button prev : old) { AnimatedEmojiSpan.release(this, prev.spans); prev.check.onDetachedFromWindow(); }
        if (results) checked.clear();
        hasAction = poll.multiple && !results || !poll.anonymous && (poll.isVoted() || poll.closed && poll.totalVoters != 0);
        if (!poll.anonymous || poll.multiple) height += AndroidUtilities.dp(13);
        totalHeight = AndroidUtilities.dp(73) + namesOffset + height;
        if (pinnedTop) namesOffset -= AndroidUtilities.dp(1);
        if (pinnedBottom) totalHeight -= AndroidUtilities.dp(pinnedTop ? 2 : 1);
        if (pinnedBottom) totalHeight -= AndroidUtilities.dp(1);
        if (pinnedTop) totalHeight -= AndroidUtilities.dp(1);
        int widest = 0;
        for (int id : new int[]{R.string.PollViewResults,R.string.PollSubmitVotes,R.string.NoVotes}) widest = Math.max(widest, (int)Math.ceil(Theme.chat_instantViewPaint.measureText(getContext().getString(id))));
        int timeTotal = timeWidth + AndroidUtilities.dp(message.isOut ? 20 : 0);
        if ((!poll.anonymous || poll.multiple) && reactionsLayoutInBubble.isEmpty && timeTotal >= (backgroundWidth - AndroidUtilities.dp(76) - widest) / 2) totalHeight += AndroidUtilities.dp(18);
        reactionsLayoutInBubble.measure(max, Gravity.LEFT);
        if (!reactionsLayoutInBubble.isEmpty) {
            reactionsLayoutInBubble.totalHeight = reactionsLayoutInBubble.height + AndroidUtilities.dp(12);
            if (timeTotal >= backgroundWidth - AndroidUtilities.dp(24) - reactionsLayoutInBubble.lastLineX) { reactionsLayoutInBubble.totalHeight += AndroidUtilities.dp(16); reactionsLayoutInBubble.positionOffsetY -= AndroidUtilities.dp(16); }
            totalHeight += reactionsLayoutInBubble.totalHeight;
        }
        updateRects(width);
    }
    private void advanceAnimation() {
        if (animation >= 1f) return;
        long now = SystemClock.elapsedRealtime();
        float elapsed = Math.min(17, Math.max(0, now - animationAt)); animationAt = now;
        // Upstream 300ms decelerate interpolation; retain raw time separately via inverse curve.
        float raw = 1f - (float)Math.sqrt(Math.max(0f, 1f - animation));
        animation = DECELERATE.getInterpolation(Math.min(1f, raw + elapsed / 300f));
        if (animation >= 1f) for (Button b : buttons) b.previousChosen = false;
    }
    private void advanceVoteAnimation() {
        if (!localPending) return;
        long now = SystemClock.elapsedRealtime();
        long dt = voteLastAt == 0 ? 0 : Math.min(17, Math.max(0, now - voteLastAt)); voteLastAt = now;
        voteOffset = (voteOffset + 360f * dt / 2000f) % 360f;
        voteProgress = Math.min(500f, voteProgress + dt);
        if (voteRising) voteLength = 4 + 266 * AndroidUtilities.accelerateInterpolator.getInterpolation(voteProgress / 500f);
        else voteLength = 4 - (firstVoteCircle ? 360 : 270) * (1f - AndroidUtilities.decelerateInterpolator.getInterpolation(voteProgress / 500f));
        if (voteProgress == 500f) {
            if (voteRising) { voteOffset += 270; voteLength = -266; }
            voteRising = !voteRising; firstVoteCircle = false; voteProgress = 0;
        }
    }
    private void beginPending(int index) {
        localPending = true; pendingIndex = index; voteOffset = 0; voteLength = 360; voteProgress = 0;
        voteRising = false; firstVoteCircle = true; voteLastAt = SystemClock.elapsedRealtime();
    }
    private void updateRects(int width) {
        int x = message.isOut ? width - backgroundWidth + AndroidUtilities.dp(11) : AndroidUtilities.dp(20) + incomingAvatarInset();
        for (Button b : buttons) { int y = b.y + namesOffset - AndroidUtilities.dp(13); b.hit.set(x, y, x + backgroundWidth - AndroidUtilities.dp(31), y + b.height + AndroidUtilities.dp(26)); }
        if (!buttons.isEmpty()) lastVoteY = buttons.get(buttons.size()-1).y + namesOffset + buttons.get(buttons.size()-1).height;
        int backgroundLeft = message.isOut ? width - backgroundWidth : AndroidUtilities.dp(pinnedBottom ? 9 : 3) + incomingAvatarInset();
        int actionX = backgroundLeft + AndroidUtilities.dp(message.isOut || pinnedBottom ? 2 : 8);
        if (hasAction) actionRect.set(actionX, lastVoteY + AndroidUtilities.dp(13), actionX + backgroundWidth - AndroidUtilities.dp(13), lastVoteY + AndroidUtilities.dp(57)); else actionRect.setEmpty();
    }
    @Override protected void drawContent(Canvas canvas) {
        if (message.poll == null || titleLayout == null) return;
        TextBubbleMessage.Poll poll = message.poll;
        int left = drawBubbleBackground(canvas); drawAuthor(canvas); drawForward(canvas,false); drawReply(canvas,false);
        updateRects(getMeasuredWidth()); advanceAnimation(); advanceVoteAnimation();
        int x = message.isOut ? getMeasuredWidth() - backgroundWidth + AndroidUtilities.dp(11) : AndroidUtilities.dp(20) + incomingAvatarInset();
        int textColor = getThemedColor(message.isOut ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn);
        int subColor = getThemedColor(message.isOut ? (drawSelectionBackground ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText) : (drawSelectionBackground ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText));
        Theme.chat_audioTitlePaint.setColor(textColor); Theme.chat_audioPerformerPaint.setColor(textColor); Theme.chat_timePaint.setColor(subColor);
        Theme.chat_livePaint.setColor(subColor); Theme.chat_locationAddressPaint.setColor(subColor); Theme.chat_instantViewPaint.setColor(textColor);
        canvas.save(); canvas.translate(x, AndroidUtilities.dp(15) + namesOffset); titleLayout.draw(canvas); AnimatedEmojiSpan.drawAnimatedEmojis(canvas, titleLayout, titleSpans, 0, null, 0, 0, 0, 1f); canvas.restore();
        canvas.save(); canvas.translate(x + subtitleOffset, titleLayout.getHeight() + AndroidUtilities.dp(20) + namesOffset); subtitleLayout.draw(canvas); canvas.restore();
        float resultAlpha = results == previousResults ? (results ? 1f : 0f) : results ? Math.min(1f, animation/.3f) : Math.min(1f, (1f-animation)/.3f);
        int accent = getThemedColor(message.isOut ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill);
        int border = getThemedColor(message.isOut ? (drawSelectionBackground ? Theme.key_chat_outMenuSelected : Theme.key_chat_outMenu) : (drawSelectionBackground ? Theme.key_chat_inMenuSelected : Theme.key_chat_inMenu));
        int line = getThemedColor(message.isOut ? (drawSelectionBackground ? Theme.key_chat_outVoiceSeekbarSelected : Theme.key_chat_outVoiceSeekbar) : (drawSelectionBackground ? Theme.key_chat_inVoiceSeekbarSelected : Theme.key_chat_inVoiceSeekbar));
        if (selector != null && pressed >= 0) selector.draw(canvas);
        for (Button b : buttons) {
            int y = b.y + namesOffset;
            canvas.save(); canvas.translate(x + AndroidUtilities.dp(35), y); b.title.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, b.title, b.spans, 0, null, 0, 0, 0, 1f);
            if (resultAlpha > 0) {
                int layer = canvas.saveLayerAlpha(-AndroidUtilities.dp(45), 0, backgroundWidth, b.height + AndroidUtilities.dp(20), (int)(255*resultAlpha));
                int percent = (int)Math.ceil(b.previousPercent + (b.percent - b.previousPercent)*animation);
                String label = String.format(java.util.Locale.getDefault(), "%d%%", percent);
                canvas.drawText(label, -AndroidUtilities.dp(6.5f)-Theme.chat_instantViewPaint.measureText(label), AndroidUtilities.dp(14), Theme.chat_instantViewPaint);
                float progress = b.previousFraction + (b.fraction-b.previousFraction)*animation;
                rect.set(0,b.height+AndroidUtilities.dp(6),Math.max(0,backgroundWidth-AndroidUtilities.dp(76))*progress,b.height+AndroidUtilities.dp(11));
                fill.setColor(accent); canvas.drawRoundRect(rect,AndroidUtilities.dp(2),AndroidUtilities.dp(2),fill);
                if (b.option.chosen || b.previousChosen) {
                    float cx=-AndroidUtilities.dp(13.5f),cy=rect.centerY(); canvas.drawCircle(cx,cy,AndroidUtilities.dp(7),fill);
                    Theme.setDrawableColor(chosenIcon, message.isOut && accent == 0xffffffff ? getThemedColor(Theme.key_chat_outBubble) : 0xffffffff);
                    int iw=chosenIcon.getIntrinsicWidth(),ih=chosenIcon.getIntrinsicHeight(); chosenIcon.setBounds((int)(cx-iw/2f),(int)(cy-ih/2f),(int)(cx+iw/2f),(int)(cy+ih/2f)); chosenIcon.draw(canvas);
                }
                canvas.restoreToCount(layer);
            }
            if (resultAlpha < 1f) {
                int layer = canvas.saveLayerAlpha(-AndroidUtilities.dp(45),0,backgroundWidth,b.height+AndroidUtilities.dp(20),(int)(255*(1f-resultAlpha)));
                stroke.setColor(line); canvas.drawLine(-AndroidUtilities.dp(2),b.height+AndroidUtilities.dp(13),backgroundWidth-AndroidUtilities.dp(58),b.height+AndroidUtilities.dp(13),stroke);
                stroke.setColor(border);
                if (localPending && pendingIndex == buttons.indexOf(b)) {
                    stroke.setColor(accent);
                    rect.set(-AndroidUtilities.dp(30.5f),AndroidUtilities.dp(.5f),-AndroidUtilities.dp(14.5f),AndroidUtilities.dp(17.5f));
                    canvas.drawArc(rect, voteOffset, voteLength, false, stroke);
                    stroke.setStrokeWidth(AndroidUtilities.dp(1));
                } else canvas.drawCircle(-AndroidUtilities.dp(22),AndroidUtilities.dp(9),AndroidUtilities.dp(8.5f),stroke);
                if (poll.multiple) { int size=AndroidUtilities.dp(8.5f); int checkColor = message.isOut && getThemedColor(Theme.key_checkboxCheck)==0xffffffff ? Theme.key_chat_outBubble : Theme.key_checkboxCheck; b.check.setColor(-1,message.isOut ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill,checkColor); b.check.setBounds(-AndroidUtilities.dp(22)-size/2,AndroidUtilities.dp(9)-size/2,size,size); b.check.draw(canvas); }
                canvas.restoreToCount(layer);
            }
            canvas.restore();
        }
        if (hasAction) {
            String text=getContext().getString(results ? R.string.PollViewResults : R.string.PollSubmitVotes);
            Theme.chat_instantViewPaint.setColor(getThemedColor(message.isOut ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText));
            CharSequence truncated = TextUtils.ellipsize(text, Theme.chat_instantViewPaint, Math.max(1, backgroundWidth - AndroidUtilities.dp(75)), TextUtils.TruncateAt.END);
            int textWidth = Math.max(1, (int)Math.ceil(Theme.chat_instantViewPaint.measureText(truncated.toString())));
            StaticLayout actionText = new StaticLayout(truncated, Theme.chat_instantViewPaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            if (localPending && pendingIndex < 0) {
                int alpha = Theme.chat_instantViewPaint.getAlpha(); Theme.chat_instantViewPaint.setAlpha(alpha / 2);
                canvas.save(); canvas.translate(actionRect.centerX() - textWidth/2f, actionRect.top + AndroidUtilities.dp(14.5f)); actionText.draw(canvas); canvas.restore();
                Theme.chat_instantViewPaint.setAlpha(alpha);
                stroke.setColor(getThemedColor(message.isOut ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText)); stroke.setStrokeWidth(AndroidUtilities.dp(2));
                float cx=actionRect.centerX()+textWidth/2f+AndroidUtilities.dp(14), cy=actionRect.centerY();
                rect.set(cx-AndroidUtilities.dp(7),cy-AndroidUtilities.dp(7),cx+AndroidUtilities.dp(7),cy+AndroidUtilities.dp(7));
                canvas.drawArc(rect,voteOffset,voteLength,false,stroke); stroke.setStrokeWidth(AndroidUtilities.dp(1));
            } else { canvas.save(); canvas.translate(actionRect.centerX() - textWidth/2f, actionRect.top + AndroidUtilities.dp(14.5f)); actionText.draw(canvas); canvas.restore(); }
        } else if (infoLayout != null) {
            float infoX = poll.anonymous && !poll.multiple ? -(float)Math.ceil(infoLayout.getLineLeft(0)) : (backgroundWidth-AndroidUtilities.dp(28)-infoLayout.getLineWidth(0))/2f-infoLayout.getLineLeft(0);
            canvas.save(); canvas.translate(x+infoX,lastVoteY+AndroidUtilities.dp((!poll.anonymous || poll.multiple) ? 28 : 22)); infoLayout.draw(canvas); canvas.restore();
        }
        drawReactionsInBubble(canvas,left,0); drawTimeAndStatus(canvas);
        if ((animation < 1f || localPending) && isAttachedToWindow()) invalidate();
    }
    public void getOptionRect(int index, RectF out) { if (index < 0 || index >= buttons.size()) out.setEmpty(); else out.set(buttons.get(index).hit); }
    public void getActionRect(RectF out) { out.set(actionRect); }
    public int getOptionPercent(int index) { return index < 0 || index >= buttons.size() ? 0 : buttons.get(index).percent; }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (message == null || message.poll == null || isCheckBoxVisible()) { cancelTouch(); return super.onTouchEvent(event); }
        if (checkForwardTouchEvent(event) || checkReplyTouchEvent(event) || checkReactionsTouchEvent(event)) return true;
        int action=event.getActionMasked(); float x=event.getX(),y=event.getY();
        if (action==MotionEvent.ACTION_DOWN && mediaDelegate != null && !localPending && !message.poll.pending && message.state != TextBubbleMessage.STATE_SENDING) {
            int hit=-1; for(int i=0;i<buttons.size();i++) if(buttons.get(i).hit.contains(x,y)){hit=i;break;}
            if (hit<0 && hasAction && actionRect.contains(x,y)) hit=buttons.size();
            if (hit>=0) {
                if (longPress != null) { removeCallbacks(longPress); longPress = null; }
                if (selector != null) { selector.setCallback(null); selector = null; }
                pressed=hit; downX=x; downY=y;
                if (!results || hit == buttons.size()) {
                    selector = Theme.createSelectorDrawable((getThemedColor(message.isOut ? Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText) & 0x00ffffff) | 0x33000000, Theme.RIPPLE_MASK_ALL);
                    RectF area = hit == buttons.size() ? actionRect : buttons.get(hit).hit;
                    selector.setCallback(this);
                    selector.setBounds((int)(area.left - (hit == buttons.size() ? 0 : AndroidUtilities.dp(9))), (int)area.top, (int)(area.right + (hit == buttons.size() ? 0 : AndroidUtilities.dp(9))), (int)area.bottom);
                    selector.setState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed}); selector.setHotspot(x,y);
                }
                if(getParent()!=null){getParent().requestDisallowInterceptTouchEvent(true);interceptHeld=true;}
                longPress=()->{longPress=null; cancelTouch(); onInteractiveLongPress(false);}; postDelayed(longPress,ViewConfiguration.getLongPressTimeout()); return true; }
        } else if (pressed>=0) {
            if (action==MotionEvent.ACTION_MOVE) { float slop=ViewConfiguration.get(getContext()).getScaledTouchSlop(); if(Math.abs(x-downX)>slop||Math.abs(y-downY)>slop){cancelTouch();return false;} }
            else if (action==MotionEvent.ACTION_CANCEL) { cancelTouch(); return true; }
            else if (action==MotionEvent.ACTION_UP) { int target=pressed; cancelTouch(); playSoundEffect(SoundEffectConstants.CLICK); handlePress(target); return true; }
            return true;
        }
        return super.onTouchEvent(event);
    }
    private void handlePress(int target) {
        TextBubbleMessage current=message; if(current==null||current.poll==null||mediaDelegate==null)return;
        TextBubbleMessage.Poll poll=current.poll;
        if (results) {
            if (target < buttons.size()) mediaDelegate.onPollOptionPressed(this, current, buttons.get(target).option);
            else mediaDelegate.onPollResults(this,current);
            return;
        }
        if (target<buttons.size()) {
            Button b=buttons.get(target);
            if(poll.multiple){if(!checked.add(b.option.id))checked.remove(b.option.id); b.check.setChecked(checked.contains(b.option.id),true);invalidate();}
            else { beginPending(target); mediaDelegate.onPollVote(this,current,Collections.singletonList(b.option.id)); invalidate(); }
        } else if (!checked.isEmpty()) {
            ArrayList<String> ids=new ArrayList<>(); for(Button b:buttons)if(checked.contains(b.option.id))ids.add(b.option.id);
            beginPending(-1); mediaDelegate.onPollVote(this,current,Collections.unmodifiableList(ids)); invalidate();
        }
    }
    private void cancelTouch() { pressed=-1; if (selector != null) { selector.setState(android.util.StateSet.NOTHING); selector.setCallback(null); selector = null; } if(longPress!=null){removeCallbacks(longPress);longPress=null;} if(interceptHeld){interceptHeld=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);} }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow(); layoutForWidth=-1; requestLayout();}
    @Override protected void onDetachedFromWindow(){cancelTouch();releaseSpans();super.onDetachedFromWindow();layoutForWidth=-1;}
}
