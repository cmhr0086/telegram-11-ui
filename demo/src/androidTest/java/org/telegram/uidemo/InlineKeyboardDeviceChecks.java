package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.*;
import org.telegram.ui.Components.Gif;
import org.telegram.ui.Components.Sticker;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded inline keyboard checks, invoked on the instrumentation worker thread. */
public final class InlineKeyboardDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private TextBubbleCell held;
    private int clicks, longs;
    private TextBubbleMessage clickedMessage;
    private TextBubbleMessage.InlineButton clickedButton;
    private final class InterceptParent extends FrameLayout {
        int releases;
        InterceptParent() { super(activity); }
        @Override public void requestDisallowInterceptTouchEvent(boolean value) { if (!value) releases++; super.requestDisallowInterceptTouchEvent(value); }
    }
    private InlineKeyboardDeviceChecks(Instrumentation i, Activity a) { instrumentation=i; activity=a; }
    public static void run(Instrumentation instrumentation, Activity activity) {
        InlineKeyboardDeviceChecks t = new InlineKeyboardDeviceChecks(instrumentation, activity);
        try {
            t.main(() -> {
                t.host = new FrameLayout(activity);
                ((ViewGroup) activity.findViewById(android.R.id.content)).addView(t.host, new ViewGroup.LayoutParams(-1,-1));
                t.checks(); t.groupingAndEmoji(); t.startLong();
            });
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 180L);
            t.main(t::finishLong);
        } finally { t.main(() -> { if (t.host != null && t.host.getParent() instanceof ViewGroup) ((ViewGroup)t.host.getParent()).removeView(t.host); }); }
    }
    private void main(Runnable run) {
        AtomicReference<Throwable> error=new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { try {run.run();} catch(Throwable t){error.set(t);} });
        if(error.get()!=null)throw new AssertionError("inline keyboard checks",error.get());
    }
    private static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    private static final class Palette implements Theme.ResourcesProvider {
        final boolean dark; Palette(boolean dark){this.dark=dark;}
        @Override public int getColor(int key){
            if(key==Theme.key_chat_botButtonText)return dark ? Color.YELLOW : Color.BLUE;
            if(key==Theme.key_chat_serviceIcon)return dark ? Color.WHITE : Color.BLACK;
            return Theme.getColor(key);
        }
        @Override public boolean isDark(){return dark;}
    }
    private TextBubbleCell cell(int kind, Theme.ResourcesProvider p) {
        switch(kind){case 1:return new PhotoBubbleCell(activity,p);case 2:return new VoiceBubbleCell(activity,p);
            case 3:return new DocumentBubbleCell(activity,p);case 4:return new AlbumBubbleCell(activity,p);
            case 5:return new GifBubbleCell(activity,p);case 6:return new StickerBubbleCell(activity,p);
            case 7:return new RoundVideoBubbleCell(activity,p);case 8:return new ContactBubbleCell(activity,p);
            case 9:return new MusicBubbleCell(activity,p);case 10:return new LocationBubbleCell(activity,p);
            case 11:return new PollBubbleCell(activity,p);default:return new TextBubbleCell(activity,p);}
    }
    private TextBubbleMessage message(int kind,boolean out) {
        ColorDrawable image=new ColorDrawable(Color.BLUE);File missing=new File(activity.getCacheDir(),"inline-unavailable");
        int state=out?TextBubbleMessage.STATE_SENT:TextBubbleMessage.STATE_NONE;
        switch(kind){case 1:return TextBubbleMessage.photo(1,out,"12:34",state,image,"Caption");
            case 2:return TextBubbleMessage.voice(2,out,"12:34",state,5,null);
            case 3:return TextBubbleMessage.document(3,out,"12:34",state,"file.pdf",100);
            case 4:return TextBubbleMessage.album(4,out,"12:34",state,Arrays.asList(
                    TextBubbleMessage.photo(41,out,"12:34",state,image,null),TextBubbleMessage.photo(42,out,"12:34",state,image,null)),"Caption");
            case 5:return TextBubbleMessage.gif(5,out,"12:34",state,new Gif(5,missing,image,200,120),"Caption");
            case 6:return TextBubbleMessage.sticker(6,out,"12:34",state,new Sticker(6,Sticker.Format.WEBP,missing,image,200,200,""));
            case 7:return TextBubbleMessage.roundVideo(7,out,"12:34",state,image,null,8);
            case 8:return TextBubbleMessage.contact(8,out,"12:34",state,new TextBubbleMessage.Contact(1,"Contact","123",image));
            case 9:return TextBubbleMessage.music(9,out,"12:34",state,"Song","Artist",12,null);
            case 10:return TextBubbleMessage.location(10,out,"12:34",state,new TextBubbleMessage.Location(1,2,null,null,image));
            case 11:return TextBubbleMessage.poll(11,out,"12:34",state,new TextBubbleMessage.Poll("Question",Arrays.asList(
                    new TextBubbleMessage.PollOption("a","A",0,false),new TextBubbleMessage.PollOption("b","B",0,false)),0,true,false,false,false));
            default:return new TextBubbleMessage(8,"Message",out,"12:34",state);}
    }
    private void delegate(TextBubbleCell c) {
        c.setDelegate(new TextBubbleCell.Delegate(){
            @Override public void onInlineButtonPressed(TextBubbleCell cell,TextBubbleMessage m,TextBubbleMessage.InlineButton b){clicks++;clickedMessage=m;clickedButton=b;}
            @Override public void onInlineButtonLongPressed(TextBubbleCell cell,TextBubbleMessage m,TextBubbleMessage.InlineButton b){longs++;clickedMessage=m;clickedButton=b;}
        });
    }
    private void draw(TextBubbleCell cell,int width) {
        cell.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        cell.layout(0,0,cell.getMeasuredWidth(),cell.getMeasuredHeight());
        check(cell.getHeight()>0,"positive height");Bitmap b=Bitmap.createBitmap(cell.getWidth(),cell.getHeight(),Bitmap.Config.ARGB_8888);cell.draw(new Canvas(b));b.recycle();
    }
    private static RectF rect(TextBubbleCell c,int row,int col){RectF r=new RectF();c.getInlineButtonRect(row,col,r);return r;}
    private static void event(TextBubbleCell c,RectF r,int a,long t){MotionEvent e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),a,r.centerX(),r.centerY(),0);c.dispatchTouchEvent(e);e.recycle();}
    private static void tap(TextBubbleCell c,RectF r){long t=SystemClock.uptimeMillis();event(c,r,MotionEvent.ACTION_DOWN,t);event(c,r,MotionEvent.ACTION_UP,t);}
    private static Rect background(TextBubbleCell c){
        try{Field f=TextBubbleCell.class.getDeclaredField("currentBackgroundDrawable");f.setAccessible(true);android.graphics.drawable.Drawable d=(android.graphics.drawable.Drawable)f.get(c);return d==null?null:new Rect(d.getBounds());}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private TextBubbleMessage.InlineButton button(String id,TextBubbleMessage.InlineButton.Type type,boolean loading){return new TextBubbleMessage.InlineButton(id,"Button "+id,type,loading);}
    private void checks(){
        for(boolean out:new boolean[]{false,true})for(int kind=0;kind<=11;kind++){
            InterceptParent parent=new InterceptParent();host.addView(parent);TextBubbleCell c=cell(kind,new Palette(out));parent.addView(c);delegate(c);
            TextBubbleMessage m=message(kind,out);c.setMessage(m,false,false);
            int[] widths={240,400,320};int[] base=new int[3];
            for(int i=0;i<widths.length;i++){draw(c,AndroidUtilities.dp(widths[i]));base[i]=c.getBubbleContentHeight();}
            int beforeHeight=c.getHeight();
            TextBubbleMessage.InlineButton a=button("a",TextBubbleMessage.InlineButton.Type.CALLBACK,false);
            TextBubbleMessage.InlineButton b=button("b",TextBubbleMessage.InlineButton.Type.URL,false);
            TextBubbleMessage.InlineButton d=button("c",TextBubbleMessage.InlineButton.Type.COPY,true);
            m.inlineKeyboard=Arrays.asList(Arrays.asList(a,b),Collections.singletonList(d));
            c.setMessage(m,false,false);
            for(int dp:new int[]{240,400}){
                int width=AndroidUtilities.dp(dp);draw(c,width);
                check(c.getBubbleContentHeight()==base[dp==240?0:1],"keyboard preserves content height kind="+kind+" width="+dp);
                RectF r0=rect(c,0,0),r1=rect(c,0,1),r2=rect(c,1,0);
                for(RectF r:new RectF[]{r0,r1,r2}){
                    check(!r.isEmpty()&&r.left>=0&&r.right<=width&&r.top>=0&&r.bottom<=c.getHeight(),"button bounds kind="+kind+" out="+out+" width="+dp);
                    check(Math.abs(r.height()-AndroidUtilities.dp(44))<=1,"upstream 44dp button height");
                }
                check(r0.right<=r1.left&&r2.top>=r0.bottom,"separate buttons and rows");
                Rect background=background(c);
                if(background!=null && kind!=6 && kind!=7)check(background.bottom<=r0.top,"keyboard below bubble background");
                int before=clicks;tap(c,r1);check(clicks==before+1&&clickedMessage==m&&clickedButton==b,"URL identity");
                before=clicks;tap(c,r2);check(clicks==before+1&&clickedMessage==m&&clickedButton==d,"loading button callback");
                long t=SystemClock.uptimeMillis();before=clicks;event(c,r0,MotionEvent.ACTION_DOWN,t);event(c,r0,MotionEvent.ACTION_CANCEL,t);check(clicks==before,"cancel does not click");
            }
            draw(c,AndroidUtilities.dp(320));check(c.getBubbleContentHeight()==base[2],"keyboard preserves 320dp content height");
            check(c.getHeight()-beforeHeight>=AndroidUtilities.dp(96),"two keyboard rows reserve height");
            RectF r=rect(c,0,0);long t=SystemClock.uptimeMillis();int before=clicks;parent.releases=0;event(c,r,MotionEvent.ACTION_DOWN,t);
            c.setMessage(message(kind,out),false,false);event(c,r,MotionEvent.ACTION_UP,t);
            check(clicks==before&&parent.releases==1,"rebind cancels and releases intercept");
            c.setMessage(m,false,false);c.setCheckBoxVisible(true,false);draw(c,AndroidUtilities.dp(320));before=clicks;tap(c,rect(c,0,0));check(clicks==before,"selection suppresses button");
            c.setCheckBoxVisible(false,false);c.setMessage(null,false,false);check(rect(c,0,0).isEmpty(),"empty binding clears button");
            parent.removeView(c);host.removeView(parent);
        }
    }
    private static final class AvatarProbe extends ColorDrawable {
        Rect lastDrawn = new Rect();
        AvatarProbe(){super(Color.GREEN);}
        @Override public void draw(Canvas canvas){lastDrawn.set(getBounds());super.draw(canvas);}
    }
    private static int integerField(TextBubbleCell cell,String name){
        try{Field f=TextBubbleCell.class.getDeclaredField(name);f.setAccessible(true);return f.getInt(cell);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private void groupingAndEmoji(){
        TextBubbleMessage a=message(0,false),b=message(0,false);
        a.date=1000;b.date=1010;a.group=b.group=true;
        a.sender=new TextBubbleMessage.Sender(9,"Sender",null);b.sender=new TextBubbleMessage.Sender(9,"Sender",null);
        check(MessageGrouping.canMerge(a,b),"matching adjacent messages group");
        TextBubbleMessage.InlineButton key=button("group",TextBubbleMessage.InlineButton.Type.CALLBACK,false);
        a.inlineKeyboard=Collections.singletonList(Collections.singletonList(key));
        check(!MessageGrouping.canMerge(a,b),"keyboard ends message group");
        InterceptParent parent=new InterceptParent();host.addView(parent);TextBubbleCell c=cell(0,new Palette(false));parent.addView(c);delegate(c);
        AvatarProbe avatar=new AvatarProbe();a.sender=new TextBubbleMessage.Sender(9,"Sender",avatar);
        a.forwardedFrom=new TextBubbleMessage.ForwardedFrom(4,"Forward source");
        a.replyTo=new TextBubbleMessage.ReplyTo(5,"Reply","Replied text");a.reactions.add(new TextBubbleMessage.Reaction("😀",1,false));
        a.inlineKeyboard=Collections.emptyList();c.setMessage(a,false,false);draw(c,AndroidUtilities.dp(320));
        int content=c.getBubbleContentHeight(), bottom=integerField(c,"bubbleBottom"),timeX=integerField(c,"timeX");Rect avatarBefore=new Rect(avatar.lastDrawn);
        a.inlineKeyboard=Collections.singletonList(Collections.singletonList(key));c.setMessage(a,false,false);draw(c,AndroidUtilities.dp(320));
        check(c.getBubbleContentHeight()==content && integerField(c,"bubbleBottom")==bottom && integerField(c,"timeX")==timeX && avatar.lastDrawn.equals(avatarBefore),
                "group avatar, time and bubble remain at original position");
        RectF r=rect(c,0,0);int before=clicks;long t=SystemClock.uptimeMillis();event(c,r,MotionEvent.ACTION_DOWN,t);
        MotionEvent move=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,r.right+AndroidUtilities.dp(30),r.centerY(),0);
        c.dispatchTouchEvent(move);move.recycle();event(c,r,MotionEvent.ACTION_UP,t);check(clicks==before,"move outside cancels click");
        parent.removeView(c);parent.addView(c);draw(c,AndroidUtilities.dp(320));tap(c,rect(c,0,0));
        check(clicks==before+1&&clickedButton==key,"reattached keyboard remains clickable");
        parent.removeView(c);host.removeView(parent);
        TextBubbleCell emoji=cell(0,null);host.addView(emoji);delegate(emoji);
        TextBubbleMessage m=new TextBubbleMessage(800,"😀",false,"12:34",TextBubbleMessage.STATE_NONE);
        emoji.setMessage(m,false,false);draw(emoji,AndroidUtilities.dp(320));int original=emoji.getBubbleContentHeight();
        m.inlineKeyboard=Collections.singletonList(Collections.singletonList(key));emoji.setMessage(m,false,false);draw(emoji,AndroidUtilities.dp(320));
        check(emoji.getBubbleContentHeight()==original&&!rect(emoji,0,0).isEmpty(),"emoji-only keyboard preserves content height");
        before=clicks;tap(emoji,rect(emoji,0,0));check(clicks==before+1&&clickedMessage==m,"emoji-only keyboard click");host.removeView(emoji);
    }
    private void startLong(){
        held=cell(0,null);host.addView(held);delegate(held);TextBubbleMessage m=message(0,false);
        m.inlineKeyboard=Collections.singletonList(Collections.singletonList(button("long",TextBubbleMessage.InlineButton.Type.SWITCH_INLINE,false)));
        held.setMessage(m,false,false);draw(held,AndroidUtilities.dp(320));long t=SystemClock.uptimeMillis();event(held,rect(held,0,0),MotionEvent.ACTION_DOWN,t);
    }
    private void finishLong(){
        check(longs==1&&clickedButton!=null&&"long".equals(clickedButton.id),"long press button identity");
        int before=clicks;long t=SystemClock.uptimeMillis();event(held,rect(held,0,0),MotionEvent.ACTION_UP,t);check(clicks==before,"long press suppresses click");
        host.removeView(held);
    }
}
