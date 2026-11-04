package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.bots.BotKeyboardView;
import org.telegram.ui.bots.ReplyKeyboard;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded reply keyboard checks, invoked from the instrumentation worker thread. */
public final class ReplyKeyboardDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private FrameLayout host;
    private int presses;
    private ReplyKeyboard.Button selected;
    private ReplyKeyboard selectedKeyboard;
    private ChatActivityEnterView raceEnter;
    private SizeNotifierFrameLayout raceParent;
    private ReplyKeyboardDeviceChecks(Instrumentation i, Activity a) { instrumentation=i; activity=a; }
    public static void run(Instrumentation instrumentation, Activity activity) {
        ReplyKeyboardDeviceChecks t=new ReplyKeyboardDeviceChecks(instrumentation,activity);
        try { t.main(() -> {
            t.host=new FrameLayout(activity);((ViewGroup)activity.findViewById(android.R.id.content)).addView(t.host,new ViewGroup.LayoutParams(-1,-1));
            t.modelChecks(); t.panelChecks(); t.inputChecks(); t.startImeRace();
        });
        android.os.SystemClock.sleep(400);
        t.main(t::finishImeRace);
        } finally { t.main(() -> {if(t.raceEnter!=null){t.raceEnter.onDestroy();t.raceEnter=null;}if(t.host!=null&&t.host.getParent() instanceof ViewGroup)((ViewGroup)t.host.getParent()).removeView(t.host);}); }
    }
    private void main(Runnable r) {
        AtomicReference<Throwable> e=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{r.run();}catch(Throwable t){e.set(t);}});
        if(e.get()!=null)throw new AssertionError("reply keyboard checks",e.get());
    }
    private static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    private static final class Palette implements Theme.ResourcesProvider {
        boolean dark;
        @Override public int getColor(int key){if(key==Theme.key_chat_botKeyboardButtonText)return dark?Color.YELLOW:Color.BLUE;return Theme.getColor(key);}
        @Override public boolean isDark(){return dark;}
    }
    private static ReplyKeyboard.Button button(String id,CharSequence text,ReplyKeyboard.Button.Type type){return new ReplyKeyboard.Button(id,text,type);}
    private static ReplyKeyboard keyboard(String id,boolean resize,boolean once,ReplyKeyboard.Button a,ReplyKeyboard.Button b,ReplyKeyboard.Button c){
        return new ReplyKeyboard(id,Arrays.asList(Arrays.asList(a,b),Collections.singletonList(c)),resize,once,"Input hint");
    }
    private static View find(View root,Object tag){
        if(root.getTag()==tag)return root;
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=find(g.getChildAt(i),tag);if(v!=null)return v;}}
        return null;
    }
    private static void layout(View view,int width,int height){
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);
    }
    private void modelChecks(){
        SpannableStringBuilder text=new SpannableStringBuilder("Original"), hint=new SpannableStringBuilder("Hint");
        ReplyKeyboard.Button a=button("a",text,ReplyKeyboard.Button.Type.TEXT),b=button("b","B",ReplyKeyboard.Button.Type.POLL);
        ArrayList<ReplyKeyboard.Button> row=new ArrayList<>(Arrays.asList(a,b));ArrayList<List<ReplyKeyboard.Button>> rows=new ArrayList<>();rows.add(row);
        ReplyKeyboard model=new ReplyKeyboard("id",rows,true,false,hint);text.replace(0,text.length(),"Changed");hint.clear();row.clear();rows.clear();
        check(model.rows.size()==1&&model.rows.get(0).size()==2&&model.rows.get(0).get(0).text.toString().equals("Original")&&model.placeholder.toString().equals("Hint"),"model is defensive snapshot");
        boolean immutable=false;try{model.rows.get(0).clear();}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"rows immutable");
        boolean invalid=false;try{new ReplyKeyboard("id",Arrays.asList(Arrays.asList(a,a)),true,false,null);}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"duplicate IDs rejected");
        invalid=false;try{new ReplyKeyboard("id",Arrays.asList(Collections.emptyList()),true,false,null);}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"empty row rejected");
        check(new ReplyKeyboard("empty",Collections.emptyList(),true,false,null).rows.isEmpty(),"empty keyboard allowed");
    }
    private void panelChecks(){
        Palette palette=new Palette();BotKeyboardView panel=new BotKeyboardView(activity,palette);host.addView(panel);
        panel.setDelegate(b->{presses++;selected=b;});
        ReplyKeyboard.Button a=button("a","مرحبا مثال طويل جدا لاختبار واجهة الأزرار",ReplyKeyboard.Button.Type.TEXT);
        ReplyKeyboard.Button b=button("b","Contact",ReplyKeyboard.Button.Type.REQUEST_CONTACT);
        ReplyKeyboard.Button c=button("c","Website",ReplyKeyboard.Button.Type.WEB_VIEW);
        ReplyKeyboard compact=keyboard("compact",true,false,a,b,c);panel.setPanelHeight(AndroidUtilities.dp(300));panel.setButtons(compact);
        layout(panel,AndroidUtilities.dp(320),AndroidUtilities.dp(300));
        check(!panel.isFullSize()&&panel.getKeyboardHeight()==2*AndroidUtilities.dp(42)+AndroidUtilities.dp(40),"upstream resize panel height");
        View first=find(panel,a),second=find(panel,b),third=find(panel,c);check(first instanceof TextView&&second!=null&&third!=null,"all button views bound");
        check(first.getHeight()==AndroidUtilities.dp(42)&&second.getHeight()==AndroidUtilities.dp(42),"upstream 42dp compact rows");
        check(first.getWidth()>0&&second.getWidth()>0&&Math.abs(first.getWidth()-second.getWidth())<=1,"equal column weights");
        check(((TextView)first).getCurrentTextColor()==Color.BLUE,"day provider text color");
        palette.dark=true;panel.updateColors();check(((TextView)first).getCurrentTextColor()==Color.YELLOW,"updated provider text color");
        int before=presses;first.performClick();check(presses==before+1&&selected==a,"button identity");
        ReplyKeyboard full=keyboard("full",false,false,a,b,c);panel.setButtons(full);layout(panel,AndroidUtilities.dp(240),AndroidUtilities.dp(300));
        check(panel.isFullSize()&&panel.getKeyboardHeight()==AndroidUtilities.dp(300),"full panel height");
        first.performClick();check(presses==before+1,"removed view cannot dispatch after rebind");
        View rebound=find(panel,b);check(rebound!=null,"rebound button exists");rebound.performClick();check(presses==before+2&&selected==b,"rebound button identity");
        panel.setButtons(null);check(panel.getKeyboardHeight()==0,"cleared panel height");
        rebound.performClick();check(presses==before+2,"cleared view cannot dispatch");host.removeView(panel);
    }
    private void inputChecks(){
        SizeNotifierFrameLayout parent=new SizeNotifierFrameLayout(activity);host.addView(parent,new FrameLayout.LayoutParams(-1,-1));
        ChatActivityEnterView enter=new ChatActivityEnterView(activity,parent,null,true);parent.addView(enter);
        final ReplyKeyboard[] replacement={null};
        final boolean[] destroyed={false};
        enter.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate(){
            @Override public void onReplyKeyboardButtonPressed(ReplyKeyboard keyboard,ReplyKeyboard.Button button){
                presses++;selectedKeyboard=keyboard;selected=button;
                if(replacement[0]!=null){ReplyKeyboard next=replacement[0];replacement[0]=null;enter.setReplyKeyboard(next,false);enter.showReplyKeyboard();}
            }
        });
        try {
            layout(parent,AndroidUtilities.dp(320),AndroidUtilities.dp(600));
            ReplyKeyboard.Button a=button("one","One",ReplyKeyboard.Button.Type.TEXT);
            ReplyKeyboard.Button b=button("two","Two",ReplyKeyboard.Button.Type.REQUEST_LOCATION);
            ReplyKeyboard.Button c=button("three","Three",ReplyKeyboard.Button.Type.POLL);
            ReplyKeyboard persistent=keyboard("persistent",true,false,a,b,c);
            String initialHint=hint(enter);
            enter.setReplyKeyboard(persistent);
            check(enter.isReplyKeyboardShowing(),"empty input auto opens keyboard");
            check(hint(enter).equals("Input hint"),"open panel shows placeholder");
            enter.setReplyKeyboard(null); check(!enter.isReplyKeyboardShowing(),"clear auto-opened model");
            enter.setReplyKeyboard(persistent,false);
            check(!enter.isReplyKeyboardShowing()&&enter.getReplyKeyboardView()!=null,"set without auto open");
            enter.showReplyKeyboard();check(enter.isReplyKeyboardShowing(),"open reply panel");
            View action=find(enter.getReplyKeyboardView(),b);check(action!=null,"input panel button exists");
            int before=presses;action.performClick();
            check(presses==before+1&&selectedKeyboard==persistent&&selected==b&&enter.isReplyKeyboardShowing(),"persistent callback keeps panel");
            enter.showEmojiView();check(!enter.isReplyKeyboardShowing(),"emoji replaces reply keyboard");
            enter.showReplyKeyboard();check(enter.isReplyKeyboardShowing(),"reply keyboard replaces emoji");
            enter.hidePopup(false);check(!enter.isReplyKeyboardShowing(),"hide reply panel");
            check(hint(enter).equals(initialHint),"hide restores previous input hint");
            ReplyKeyboard once=keyboard("once",true,true,a,b,c);enter.setReplyKeyboard(once,false);enter.showReplyKeyboard();
            before=presses;find(enter.getReplyKeyboardView(),a).performClick();
            check(presses==before+1&&selectedKeyboard==once&&selected==a&&!enter.isReplyKeyboardShowing(),"single use closes after callback");
            enter.showReplyKeyboard();check(enter.isReplyKeyboardShowing(),"single use keyboard remains available");
            replacement[0]=persistent;
            find(enter.getReplyKeyboardView(),a).performClick();
            check(enter.isReplyKeyboardShowing() && find(enter.getReplyKeyboardView(),b)!=null,
                    "single use callback replacing model preserves new open panel");
            enter.setReplyKeyboard(null);check(!enter.isReplyKeyboardShowing(),"removing model hides panel");
            enter.setReplyKeyboard(persistent,false);
            enter.setEditingMessageObject(new ChatActivityEnterView.EditingMessage(1,"edit",null,false),false);
            enter.showReplyKeyboard();check(!enter.isReplyKeyboardShowing(),"editing blocks reply panel");
            enter.setEditingMessageObject(null,false);
            try {
                java.lang.reflect.Field f=ChatActivityEnterView.class.getDeclaredField("recordingAudioVideo");f.setAccessible(true);
                f.setBoolean(enter,true);enter.showReplyKeyboard();check(!enter.isReplyKeyboardShowing(),"recording blocks reply panel");f.setBoolean(enter,false);
            } catch(ReflectiveOperationException e){throw new AssertionError(e);}
            enter.onDestroy(); destroyed[0]=true; enter.setReplyKeyboard(persistent); enter.showReplyKeyboard();
            check(enter.getReplyKeyboardView()==null && !enter.isReplyKeyboardShowing(),"destroy prevents keyboard reconstruction");
        } finally { if(!destroyed[0])enter.onDestroy();host.removeView(parent); }
    }
    private static String hint(ChatActivityEnterView enter){
        try{
            java.lang.reflect.Field animated=org.telegram.ui.Components.EditTextBoldCursor.class.getDeclaredField("hintAnimatedDrawable");animated.setAccessible(true);
            Object value=animated.get(enter.getEditField());
            if(value instanceof org.telegram.ui.Components.AnimatedTextView.AnimatedTextDrawable)
                return ((org.telegram.ui.Components.AnimatedTextView.AnimatedTextDrawable)value).getText().toString();
            java.lang.reflect.Field raw=org.telegram.ui.Components.EditTextBoldCursor.class.getDeclaredField("hint");raw.setAccessible(true);
            CharSequence text=(CharSequence)raw.get(enter.getEditField());return text==null?"":text.toString();
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private void startImeRace(){
        raceParent=new SizeNotifierFrameLayout(activity);host.addView(raceParent,new FrameLayout.LayoutParams(-1,-1));
        raceEnter=new ChatActivityEnterView(activity,raceParent,null,true);raceParent.addView(raceEnter);
        layout(raceParent,AndroidUtilities.dp(320),AndroidUtilities.dp(600));
        ReplyKeyboard.Button a=button("a","A",ReplyKeyboard.Button.Type.TEXT),b=button("b","B",ReplyKeyboard.Button.Type.TEXT),c=button("c","C",ReplyKeyboard.Button.Type.TEXT);
        raceEnter.setReplyKeyboard(keyboard("race",true,false,a,b,c),false);raceEnter.forceSmoothKeyboard(true);raceEnter.showReplyKeyboard();
        layout(raceParent,AndroidUtilities.dp(320),AndroidUtilities.dp(600));
        raceEnter.hidePopup(false);
        check(raceEnter.panelAnimationInProgress(),"test establishes closing animation");
        raceEnter.onSizeChanged(AndroidUtilities.dp(240),false);
        check(!raceEnter.panelAnimationInProgress(),"IME arrival stops closing animation");
        check(!raceEnter.isReplyKeyboardShowing()&&raceEnter.getReplyKeyboardView().getVisibility()==View.GONE,"IME hides reply panel");
        try{java.lang.reflect.Field f=ChatActivityEnterView.class.getDeclaredField("emojiPadding");f.setAccessible(true);check(f.getInt(raceEnter)==0,"IME clears panel padding");}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private void finishImeRace(){
        check(!raceEnter.panelAnimationInProgress()&&raceEnter.getReplyKeyboardView().getVisibility()==View.GONE,"delayed close stays finished");
        try{java.lang.reflect.Field f=ChatActivityEnterView.class.getDeclaredField("emojiPadding");f.setAccessible(true);check(f.getInt(raceEnter)==0,"delayed panel padding remains zero");}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
        raceEnter.onDestroy();raceEnter=null;host.removeView(raceParent);raceParent=null;
    }

}
