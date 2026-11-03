/*
 * Adapted from Telegram for Android release-11.4.2-5469, ChatMessageCell botButtons.
 * Copyright Nikolai Kudashov, 2013-2018. Licensed under GNU GPL v. 2 or later.
 * See LICENSE in this archive.
 */
package org.telegram.ui.Cells;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** UI-only extract of ChatMessageCell's inline bot button rows. */
final class InlineKeyboardLayout {
    private static final class Button {
        TextBubbleMessage.InlineButton model;
        int row, column, rowCount, columnCount;
        float x, width;
        final RectF rect = new RectF();
        Text title;
        LoadingDrawable loading;
        Drawable selector;
        int selectorColor;
        float pressT;
        boolean pressed;
        ValueAnimator animator;
    }
    private final TextBubbleCell cell;
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final Path path = new Path();
    private final float[] radii = new float[8];
    private final RectF tmp = new RectF();
    private long messageId = Long.MIN_VALUE;
    private int keyboardWidth, contentHeight, viewWidth;
    private Button pressed;
    private boolean interceptHeld;
    private float downX, downY;
    private Runnable longPress;
    private Drawable linkIcon, inlineIcon;
    InlineKeyboardLayout(TextBubbleCell cell) { this.cell = cell; }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private boolean mediaBackground() {
        return cell instanceof PhotoBubbleCell && ((PhotoBubbleCell)cell).mediaBackground
                || cell instanceof StickerBubbleCell || cell instanceof RoundVideoBubbleCell || cell.isEmojiOnlyMode();
    }
    private TextPaint paint() {
        TextPaint paint = cell.resourcesProvider == null ? Theme.chat_botButtonPaint
                : (TextPaint) cell.resourcesProvider.getPaint(Theme.key_paint_chatBotButton);
        if (paint == null) paint = Theme.chat_botButtonPaint;
        paint.setTextSize(dp(15)); return paint;
    }
    int measure(int width, int height) {
        TextBubbleMessage message = cell.message;
        List<List<TextBubbleMessage.InlineButton>> rows = message == null ? null : message.inlineKeyboard;
        if (rows == null || rows.isEmpty()) { clear(); return 0; }
        viewWidth = width; contentHeight = height;
        HashMap<String, Button> old = new HashMap<>();
        if (messageId == message.id) for (Button b : buttons) old.put(b.model.id, b);
        else for (Button b : buttons) dispose(b);
        buttons.clear(); messageId = message.id;
        TextPaint textPaint = paint();
        int wanted = 0;
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        TextPaint measurePaint = Theme.chat_msgBotButtonPaint;
        measurePaint.setTextSize(dp(15));
        for (List<TextBubbleMessage.InlineButton> row : rows) {
            if (row == null || row.isEmpty()) continue;
            int max = 0;
            for (TextBubbleMessage.InlineButton button : row) {
                if (button == null) continue;
                if (!ids.add(button.id)) throw new IllegalArgumentException("inline keyboard button IDs must be unique");
                CharSequence label = Emoji.replaceEmoji(button.text, measurePaint.getFontMetricsInt(), false);
                android.text.StaticLayout layout = new android.text.StaticLayout(label, measurePaint, dp(2000), android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
                if (layout.getLineCount() > 0) {
                    float textWidth = layout.getLineWidth(0), left = layout.getLineLeft(0);
                    if (left < textWidth) textWidth -= left;
                    max = Math.max(max, (int)Math.ceil(textWidth) + dp(4));
                }
            }
            wanted = Math.max(wanted, (max + dp(12)) * row.size() + dp(5) * (row.size() - 1));
        }
        boolean media = mediaBackground();
        int initialWidth = Math.max(1, cell.backgroundWidth - dp(media ? 0 : 9));
        if (wanted > initialWidth) {
            int display = AndroidUtilities.displaySize.y > 0 ? AndroidUtilities.displaySize.y : width;
            int max = (AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : Math.min(width, display) - dp(5)) - dp(cell.incomingAvatarInset() != 0 ? 62 : 10);
            initialWidth = Math.max(cell.backgroundWidth, Math.min(wanted, max));
        }
        int leftBound = cell.message.isOut ? dp(10) : dp(media ? 10 : (cell.pinnedBottom ? 10 : 10)) + cell.incomingAvatarInset();
        initialWidth = Math.max(1, Math.min(initialWidth, width - leftBound - (cell.message.isOut ? 0 : dp(3))));
        int maxButtonsWidth = 0;
        for (int rowIndex=0; rowIndex<rows.size(); rowIndex++) {
            List<TextBubbleMessage.InlineButton> row = rows.get(rowIndex);
            if (row == null || row.isEmpty()) continue;
            int buttonWidth = Math.max(1, (initialWidth - dp(5)*(row.size()-1) - dp(2)) / row.size());
            for (int col=0; col<row.size(); col++) {
                TextBubbleMessage.InlineButton model = row.get(col); if (model == null) continue;
                Button b = old.remove(model.id); if (b == null) b = new Button();
                b.model = model; b.row = rowIndex; b.column = col; b.rowCount = rows.size(); b.columnCount = row.size();
                b.x = (float)col * (buttonWidth + dp(5)) / initialWidth;
                b.width = (float)buttonWidth / initialWidth;
                CharSequence label = Emoji.replaceEmoji(model.text, textPaint.getFontMetricsInt(), false);
                if (model.type == TextBubbleMessage.InlineButton.Type.COPY) {
                    SpannableStringBuilder span = new SpannableStringBuilder("c ").append(label);
                    ColoredImageSpan icon = new ColoredImageSpan(R.drawable.menu_copy_s); icon.setScale(.9f,.9f);
                    span.setSpan(icon, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); label = span;
                }
                b.title = new Text(label, textPaint);
                buttons.add(b);
                maxButtonsWidth = Math.max(maxButtonsWidth, (int)(b.x*initialWidth) + (int)(b.width*initialWidth));
            }
        }
        for (Button b : old.values()) dispose(b);
        keyboardWidth = Math.max(1, maxButtonsWidth);
        updateRects();
        return dp(48) * rows.size() + dp(1);
    }
    private void updateRects() {
        if (cell.message == null) return;
        boolean media = mediaBackground();
        int backgroundLeft = cell.message.isOut ? viewWidth-cell.backgroundWidth
                : dp(cell.pinnedBottom ? 9 : 3)+cell.incomingAvatarInset();
        if (media) backgroundLeft = cell.message.isOut ? viewWidth-cell.backgroundWidth-dp(9) : dp(9)+cell.incomingAvatarInset();
        int x = cell.message.isOut ? viewWidth-keyboardWidth-dp(10) : backgroundLeft+dp(media || cell.pinnedBottom ? 1 : 7);
        for (Button b : buttons) {
            float left = x + b.x*keyboardWidth, top = contentHeight-dp(2)+b.row*dp(48)+dp(2.5f);
            b.rect.set(left,top,left+b.width*keyboardWidth,top+dp(44));
        }
    }
    boolean ownsDrawable(Drawable who) {
        for (Button button : buttons) if (who == button.selector || who == button.loading) return true;
        return false;
    }
    void getRect(int row, int column, RectF out) {
        out.setEmpty();
        for (Button b : buttons) if (b.row==row && b.column==column) { out.set(b.rect); return; }
    }
    void draw(Canvas canvas) {
        if (buttons.isEmpty()) return;
        TextPaint paint=paint(); paint.setColor(cell.getThemedColor(Theme.key_chat_botButtonText));
        int color=cell.getThemedColor(Theme.key_chat_serviceBackgroundSelector);
        for (Button b : buttons) {
            canvas.save();
            if (b.pressed && b.pressT < 1f) { b.pressT=Math.min(1f,b.pressT+Math.min(40f,1000f/Math.max(1f,AndroidUtilities.screenRefreshRate))/100f); cell.invalidate(); }
            float scale=1f-.04f*b.pressT; canvas.scale(scale,scale,b.rect.centerX(),b.rect.centerY());
            float radius=dp(Math.min(6.75f,SharedConfig.bubbleRadius)); java.util.Arrays.fill(radii,radius);
            if(b.row==b.rowCount-1 && b.column==0)radii[6]=radii[7]=dp(SharedConfig.bubbleRadius);
            if(b.row==b.rowCount-1 && b.column==b.columnCount-1)radii[4]=radii[5]=dp(SharedConfig.bubbleRadius);
            path.rewind(); path.addRoundRect(b.rect,radii,Path.Direction.CW);
            cell.applyServiceShaderMatrix();
            Paint background=cell.resourcesProvider==null ? Theme.chat_actionBackgroundPaint : cell.resourcesProvider.getPaint(Theme.key_paint_chatActionBackground);
            if(background==null)background=Theme.chat_actionBackgroundPaint;
            canvas.drawPath(path,background);
            if(cell.resourcesProvider==null ? Theme.hasGradientService() : cell.resourcesProvider.hasGradientService()) canvas.drawPath(path,Theme.chat_actionBackgroundGradientDarkenPaint);
            canvas.save(); canvas.clipPath(path);
            if (b.model.loading) {
                if(b.loading==null){b.loading=new LoadingDrawable();b.loading.setCallback(cell);b.loading.setRadiiDp(5.5f);b.loading.setAppearByGradient(true);b.loading.strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1.25f));}
                else if(b.loading.isDisappeared()||b.loading.isDisappearing()){b.loading.reset();b.loading.resetDisappear();}
            } else if(b.loading!=null&&!b.loading.isDisappearing()&&!b.loading.isDisappeared()) b.loading.disappear();
            if(b.loading!=null&&(b.model.loading||b.loading.isDisappearing())){
                tmp.set(b.rect);tmp.inset(AndroidUtilities.dpf2(.625f),AndroidUtilities.dpf2(.625f));b.loading.setRadii(radii);b.loading.setBounds(tmp);
                b.loading.setColors(Theme.multAlpha(color,1f),Theme.multAlpha(color,2.5f),Theme.multAlpha(color,3f),Theme.multAlpha(color,10f));b.loading.draw(canvas);cell.invalidate();
            }
            if(b.selector!=null){if (b.selectorColor != color) { b.selectorColor = color; Theme.setSelectorDrawableColor(b.selector, color, false); } b.selector.setBounds((int)b.rect.left,(int)b.rect.top,(int)b.rect.right,(int)b.rect.bottom);b.selector.draw(canvas);}
            canvas.restore();
            b.title.ellipsize(Math.max(1,(int)b.rect.width()-dp(10))).draw(canvas,b.rect.left+(b.rect.width()-b.title.getWidth())/2f,b.rect.centerY());
            Drawable icon=null;
            if(b.model.type==TextBubbleMessage.InlineButton.Type.URL){if(linkIcon==null)linkIcon=cell.getResources().getDrawable(R.drawable.bot_link).mutate();icon=linkIcon;}
            else if(b.model.type==TextBubbleMessage.InlineButton.Type.SWITCH_INLINE){if(inlineIcon==null)inlineIcon=cell.getResources().getDrawable(R.drawable.bot_lines).mutate();icon=inlineIcon;}
            if(icon!=null){Theme.setDrawableColor(icon,cell.getThemedColor(Theme.key_chat_serviceIcon));int x=(int)b.rect.right-dp(3)-icon.getIntrinsicWidth(),y=(int)b.rect.top+dp(3);icon.setBounds(x,y,x+icon.getIntrinsicWidth(),y+icon.getIntrinsicHeight());icon.draw(canvas);}
            canvas.restore();
        }
    }
    boolean dispatch(MotionEvent event) {
        if(cell.message==null || cell.isCheckBoxVisible() || cell.delegate==null){cancel();return false;}
        int action=event.getActionMasked();float x=event.getX(),y=event.getY();
        if(action==MotionEvent.ACTION_DOWN){
            Button hit=null;for(Button b:buttons)if(b.rect.contains(x,y)){hit=b;break;}
            if(hit==null)return false;
            if(longPress!=null)cell.removeCallbacks(longPress);
            if(pressed!=null && pressed!=hit)releasePress(pressed);
            pressed=hit;downX=x;downY=y;
            if(hit.animator!=null){hit.animator.cancel();hit.animator=null;}
            hit.pressed=true;
            if(hit.selector==null){hit.selectorColor=cell.getThemedColor(Theme.key_chat_serviceBackgroundSelector);hit.selector=Theme.createRadSelectorDrawable(hit.selectorColor,6,6);hit.selector.setCallback(cell);}
            hit.selector.setHotspot(x,y);hit.selector.setState(new int[]{android.R.attr.state_enabled,android.R.attr.state_pressed});
            if(cell.getParent()!=null){cell.getParent().requestDisallowInterceptTouchEvent(true);interceptHeld=true;}
            Button selected=hit;
            longPress=()->{ if(pressed!=selected)return; TextBubbleMessage m=cell.message; TextBubbleMessage.InlineButton b=selected.model; cancel(); if(m!=null&&cell.delegate!=null)cell.delegate.onInlineButtonLongPressed(cell,m,b); };
            cell.postDelayed(longPress,ViewConfiguration.getLongPressTimeout()-1);cell.invalidate();return true;
        }
        if(pressed==null)return false;
        if(action==MotionEvent.ACTION_MOVE){float slop=ViewConfiguration.get(cell.getContext()).getScaledTouchSlop();if(!pressed.rect.contains(x,y)||Math.abs(x-downX)>slop||Math.abs(y-downY)>slop){cancel();return false;}return true;}
        if(action==MotionEvent.ACTION_CANCEL){cancel();return true;}
        if(action==MotionEvent.ACTION_UP){Button b=pressed;TextBubbleMessage message=cell.message;cancel();cell.playSoundEffect(SoundEffectConstants.CLICK);if(message!=null&&cell.delegate!=null)cell.delegate.onInlineButtonPressed(cell,message,b.model);return true;}
        return true;
    }
    private void releasePress(Button b){
        b.pressed=false;if(b.selector!=null)b.selector.setState(android.util.StateSet.NOTHING);
        if(b.pressT!=0){b.animator=ValueAnimator.ofFloat(b.pressT,0);b.animator.setDuration(350);b.animator.setInterpolator(new OvershootInterpolator(2));b.animator.addUpdateListener(a->{b.pressT=(float)a.getAnimatedValue();cell.invalidate();});b.animator.start();}
    }
    void cancel(){if(longPress!=null){cell.removeCallbacks(longPress);longPress=null;}if(pressed!=null){releasePress(pressed);pressed=null;}if(interceptHeld){interceptHeld=false;if(cell.getParent()!=null)cell.getParent().requestDisallowInterceptTouchEvent(false);}cell.invalidate();}
    private void dispose(Button b){if(b.animator!=null)b.animator.cancel();if(b.selector!=null)b.selector.setCallback(null);if(b.loading!=null)b.loading.setCallback(null);}
    void clear(){cancel();for(Button b:buttons)dispose(b);buttons.clear();messageId=Long.MIN_VALUE;}
}
