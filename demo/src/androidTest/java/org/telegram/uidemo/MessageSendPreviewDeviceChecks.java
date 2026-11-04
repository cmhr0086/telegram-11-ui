package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.Cells.TextBubbleMessage;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatInputLayout;
import org.telegram.ui.Components.MessageEffect;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.MessageSendPreview;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded real-dialog checks, called from the instrumentation worker thread. */
public final class MessageSendPreviewDeviceChecks {
    private final Instrumentation instrumentation; private final Activity activity;
    private ChatInputLayout parent; private ChatActivityEnterView input; private MessageSendPreview dialog;
    private int sentText,sentVoice,standaloneSends,legacyText,legacyVoice;
    private View options; private long sentEffect; private CharSequence sentValue;
    private MessageSendPreviewDeviceChecks(Instrumentation i,Activity a){instrumentation=i;activity=a;}
    public static void run(Instrumentation instrumentation,Activity activity) throws Exception {
        MessageSendPreviewDeviceChecks t=new MessageSendPreviewDeviceChecks(instrumentation,activity);
        try {
            t.main(()->{t.setup();t.openStandalone();});
            SystemClock.sleep(450);t.overlayCycle();t.standaloneChecks();
            t.main(t::anchorAndBlankClose);
            SystemClock.sleep(450);t.main(()->{check(!t.dialog.isShowing(),"blank tap closes dialog");t.dialog=null;});
            t.main(t::startInputChecks);SystemClock.sleep(450);t.main(t::sendInputChecks);
            SystemClock.sleep(450);t.main(t::finishInputChecks);
        } finally {t.main(()->{if(t.dialog!=null)t.dialog.dismissImmediately();if(t.input!=null)t.input.onDestroy();if(t.parent!=null&&t.parent.getParent() instanceof ViewGroup)((ViewGroup)t.parent.getParent()).removeView(t.parent);});}
    }
    private void main(Runnable r){AtomicReference<Throwable> error=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{r.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError("send preview checks",error.get());}
    private static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException e){throw new AssertionError(name,e);}}
    private void layout(){int w=AndroidUtilities.dp(320),h=AndroidUtilities.dp(600);parent.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));parent.layout(0,0,w,h);}
    private void setup(){
        try{LocalEmojiSamples.install(activity);}catch(Exception e){throw new AssertionError(e);}
        parent=new ChatInputLayout(activity,null);((ViewGroup)activity.findViewById(android.R.id.content)).addView(parent,new ViewGroup.LayoutParams(-1,-1));
        input=new ChatActivityEnterView(activity,parent,null,true);parent.addView(input,new FrameLayout.LayoutParams(-1,AndroidUtilities.dp(50)));parent.setEnterView(input);input.onResume();
        input.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate(){
            @Override public void onSendTextWithEffect(CharSequence text,ArrayList<TLRPC.MessageEntity> entities,boolean notify,int scheduleDate,long effectId){sentText++;sentValue=text;sentEffect=effectId;}
            @Override public void onSendVoiceWithEffect(MediaController.RecordedAudio audio,boolean notify,int scheduleDate,long effectId){sentVoice++;sentEffect=effectId;}
        });
        input.setFieldText("draft preview");layout();
    }
    private ChatActivityEnterView.SendButton anchor(){return (ChatActivityEnterView.SendButton)field(input,"sendButton");}
    private MessageEffect effect(){return new MessageEffect(73,"🦆",LocalEmojiSamples.TGS_ID);}
    private void openStandalone(){
        dialog=new MessageSendPreview(activity,null);
        ArrayList<TextBubbleMessage> items=new ArrayList<>();
        items.add(new TextBubbleMessage(1,"A long message "+new String(new char[150]).replace('\0','x'),true,"12:34",TextBubbleMessage.STATE_SENT));
        items.add(TextBubbleMessage.document(2,false,"12:34",0,"document.pdf",100));
        dialog.setMessages(items);items.clear();
        dialog.setSendButton(anchor(),true,v->standaloneSends++);dialog.setEffects(Collections.singletonList(effect()));
        options=new View(activity);options.setMinimumWidth(AndroidUtilities.dp(120));options.setMinimumHeight(AndroidUtilities.dp(80));dialog.setOptionsView(options);dialog.show();
        check(dialog.isShowing(),"dialog opens");
    }
    private View holder(ReactionsContainerLayout selector){
        RecyclerView.ViewHolder h=selector.getListView().findViewHolderForAdapterPosition(0);
        return h==null?null:h.itemView;
    }
    private Object holderField(View v,String name){try{Field f=v.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(v);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private View awaitHolder(ReactionsContainerLayout selector) throws Exception {
        final View[] result={null};for(int i=0;i<100&&result[0]==null;i++){main(()->result[0]=holder(selector));if(result[0]==null)SystemClock.sleep(30);}check(result[0]!=null,"effect holder attaches");return result[0];
    }
    private Bitmap capture(View view){Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));return b;}
    private void overlayCycle() throws Exception {
        main(()->dialog.setEffectId(73));
        final View overlay=dialog.getEffectOverlay();
        boolean loaded=false;
        for(int i=0;i<120&&!loaded;i++){
            boolean[] ok={false};main(()->{Object d=holderField(overlay,"drawable");ok[0]=d instanceof AnimatedEmojiDrawable&&((AnimatedEmojiDrawable)d).isLoaded();});
            loaded=ok[0];if(!loaded)SystemClock.sleep(40);
        }
        check(loaded,"full-screen effect loads");
        final Bitmap[] before={null};final int[] frame={-1};
        main(()->{before[0]=capture(overlay);frame[0]=((AnimatedEmojiDrawable)holderField(overlay,"drawable")).getCurrentFrame();});
        SystemClock.sleep(180);
        main(()->{
            Object drawable=holderField(overlay,"drawable");check(drawable instanceof AnimatedEmojiDrawable,"effect remains active during first samples");
            Bitmap after=capture(overlay);int diff=0,visible=0;
            for(int y=0;y<after.getHeight();y++)for(int x=0;x<after.getWidth();x++){
                int px=after.getPixel(x,y);if(Color.alpha(px)>0)visible++;if(before[0].getPixel(x,y)!=px)diff++;
            }
            before[0].recycle();after.recycle();
            check(visible>0&&diff>0&&((AnimatedEmojiDrawable)drawable).getCurrentFrame()!=frame[0],"full-screen effect pixels and natural frame advance");
        });
        boolean released=false;
        for(int i=0;i<400&&!released;i++){
            boolean[] done={false};main(()->done[0]=holderField(overlay,"drawable")==null);
            released=done[0];if(!released)SystemClock.sleep(40);
        }
        check(released,"full-screen effect completes one natural cycle and releases drawable");
        main(()->dialog.setEffectId(0));
    }
    private void standaloneChecks() throws Exception {
        final ReactionsContainerLayout selector=dialog.getEffectSelector();
        main(()->{
            check(dialog.getChatListView().getAdapter().getItemCount()==2,"message snapshot retained");
            check(dialog.getChatListView().getTop()>=0&&dialog.getChatListView().getBottom()<=dialog.getWindow().getDecorView().getHeight(),"preview list within dialog");
            check(dialog.getSendButton()!=null&&dialog.getSendButton().getWidth()>0,"anchor clone laid out");
            int[] list=new int[2],opt=new int[2],sel=new int[2];dialog.getChatListView().getLocationOnScreen(list);options.getLocationOnScreen(opt);dialog.getEffectSelector().getLocationOnScreen(sel);
            check(sel[1]+dialog.getEffectSelector().getHeight()<=list[1]+1 && list[1]+dialog.getChatListView().getHeight()<=opt[1]+1,"long message, effects and options do not overlap");
        });
        View h=awaitHolder(selector);
        boolean loaded=false;for(int i=0;i<100&&!loaded;i++){boolean[] ok={false};main(()->{Object a=holderField(h,"animation");ok[0]=a instanceof AnimatedEmojiDrawable&&((AnimatedEmojiDrawable)a).isLoaded();});loaded=ok[0];if(!loaded)SystemClock.sleep(50);}check(loaded,"effect TGS loads");
        final Bitmap[] first={null};final int[] frame={0};main(()->{first[0]=capture(h);frame[0]=((AnimatedEmojiDrawable)holderField(h,"animation")).getCurrentFrame();});
        SystemClock.sleep(300);main(()->{Bitmap second=capture(h);int changed=0;for(int y=0;y<second.getHeight();y++)for(int x=0;x<second.getWidth();x++)if(first[0].getPixel(x,y)!=second.getPixel(x,y))changed++;first[0].recycle();second.recycle();check(changed>0&&((AnimatedEmojiDrawable)holderField(h,"animation")).getCurrentFrame()!=frame[0],"effect pixels and frame advance");
            selector.getListView().clickItem(h,0);check(selector.getSelectedEffect()==73,"select effect");selector.getListView().clickItem(h,0);check(selector.getSelectedEffect()==0,"second tap clears effect");
            dialog.setEffectId(73);check(dialog.getSelectedEffect()==73,"setEffectId selects effect");
            dialog.setEffects(Collections.singletonList(new MessageEffect(73,"🦆",-1)));
            check(dialog.getSelectedEffect()==73,"negative document id accepted on replacement");
            dialog.setEffects(Collections.singletonList(effect()));
            dialog.getSendButton().performClick();dialog.getSendButton().performClick();
            check(standaloneSends==1,"duplicate send click delivered once");
            dialog.dismiss(false);dialog.getSendButton().performClick();check(standaloneSends==1,"closing dialog rejects send click");});
        SystemClock.sleep(450);main(()->{check(!dialog.isShowing(),"cancel dismissal completes");check(selector.getListView().getAdapter()==null,"selector resources disposed");dialog=null;});
    }
    private void anchorAndBlankClose(){
        ChatActivityEnterView.SendButton anchor=anchor();float original=anchor.getAlpha();
        MessageSendPreview first=new MessageSendPreview(activity,null),second=new MessageSendPreview(activity,null);
        first.setSendButton(anchor,true,v->{});first.show();
        second.setSendButton(anchor,true,v->{});second.show();
        first.dismissImmediately();check(anchor.getAlpha()==0f,"older preview cannot restore shared anchor");
        second.dismissImmediately();check(Math.abs(anchor.getAlpha()-original)<.01f,"last preview restores shared anchor alpha");
        dialog=new MessageSendPreview(activity,null);dialog.setSendButton(anchor,true,v->{});dialog.show();
        ((View)field(dialog,"windowView")).performClick();
    }
    private void startInputChecks(){
        input.setMessageEffects(Collections.singletonList(effect()));input.setFieldText("preview draft");layout();
        input.setSendPreviewEnabled(false);check(!input.showMessageSendPreview(),"disabled preview does not open");
        input.setSendPreviewEnabled(true);check(input.showMessageSendPreview(),"input opens text preview");
        dialog=input.getMessageSendPreview();check(dialog!=null&&dialog.isShowing(),"input preview dialog visible");
        input.onPause();check(!dialog.isShowing(),"pause closes preview immediately");
        input.onResume();check(input.showMessageSendPreview(),"resume permits fresh preview");dialog=input.getMessageSendPreview();
        long version=(Long)field(input,"previewDraftVersion");dialog.dismiss(false);
        check(!dialog.isInteractive(),"dismissal immediately disables preview");
        try{java.lang.reflect.Method late=ChatActivityEnterView.class.getDeclaredMethod("sendFromPreview",MessageSendPreview.class,long.class,MediaController.RecordedAudio.class,Object.class,boolean.class,int.class);
            late.setAccessible(true);late.invoke(input,dialog,version,null,null,true,0);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        check(sentText==0&&input.getFieldText().toString().equals("preview draft"),"late async send while closing rejected");
    }
    private void sendInputChecks(){
        check(input.getFieldText().toString().equals("preview draft")&&sentText==0,"cancel retains draft and does not send");
        check(input.showMessageSendPreview(),"preview can reopen");dialog=input.getMessageSendPreview();
        View staleSend=dialog.getSendButton();input.setFieldText("changed draft");staleSend.performClick();
        check(sentText==0&&input.getFieldText().toString().equals("changed draft"),"old preview cannot send changed draft");
        input.setFieldText("preview draft");check(input.showMessageSendPreview(),"changed draft opens fresh preview");dialog=input.getMessageSendPreview();dialog.setEffectId(73);
        check(dialog.getSelectedEffect()==73,"input preview selects effect");dialog.getSendButton().performClick();
        check(sentText==1&&sentEffect==73&&"preview draft".contentEquals(sentValue),"send reports text and selected effect");
    }
    private void finishInputChecks(){
        input.setFieldText("voice draft sentinel");input.setVoiceDraft(new MediaController.RecordedAudio("/nonexistent/preview-test.ogg",1500,null),false);
        check(input.showMessageSendPreview(),"voice draft can open preview");dialog=input.getMessageSendPreview();dialog.setEffectId(73);dialog.getSendButton().performClick();
        check(sentVoice==1&&sentEffect==73,"voice preview reports effect");
        input.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate(){
            @Override public void onSendText(CharSequence text,ArrayList<TLRPC.MessageEntity> entities,boolean notify,int date){legacyText++;}
            @Override public void onSendVoice(MediaController.RecordedAudio audio,boolean notify,int date){legacyVoice++;}
        });
        input.setFieldText("legacy text");check(input.showMessageSendPreview(),"legacy text preview opens");
        MessageSendPreview legacy=input.getMessageSendPreview();legacy.setEffectId(73);legacy.getSendButton().performClick();
        check(legacyText==1,"default effect callback falls back to legacy text callback");
        input.setVoiceDraft(new MediaController.RecordedAudio("/nonexistent/legacy-test.ogg",1500,null),false);
        check(input.showMessageSendPreview(),"legacy voice preview opens");legacy=input.getMessageSendPreview();legacy.setEffectId(73);legacy.getSendButton().performClick();
        check(legacyVoice==1,"default effect callback falls back to legacy voice callback");
        input.setFieldText("destroyed draft");check(input.showMessageSendPreview(),"open before destroy");
        MessageSendPreview stale=input.getMessageSendPreview();View staleSend=stale.getSendButton();input.onDestroy();
        staleSend.performClick();check(sentText==1&&sentVoice==1&&!stale.isShowing(),"destroy closes preview and rejects late send");input=null;
    }
}
