package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatInputLayout;
import org.telegram.ui.Components.EmojiSuggestion;
import org.telegram.ui.Components.EmojiSuggestionQuery;
import org.telegram.ui.Components.EmojiSuggestionsController;
import org.telegram.ui.Components.MentionsController;
import org.telegram.ui.Components.SuggestEmojiView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded emoji suggestion checks, invoked from the instrumentation worker thread. */
public final class SuggestEmojiDeviceChecks {
    private final Instrumentation instrumentation; private final Activity activity;
    private ChatInputLayout parent; private ChatActivityEnterView input; private EmojiSuggestionsController controller;
    private MentionsController mentions;
    private final ArrayList<EmojiSuggestionsController.Results> callbacks=new ArrayList<>();
    private final ArrayList<EmojiSuggestionQuery> queries=new ArrayList<>();
    private int selections,longs; private EmojiSuggestion selected;
    private EmojiSuggestion replacementDuck;
    private EmojiSuggestionQuery replacementQuery;
    private SuggestEmojiDeviceChecks(Instrumentation i,Activity a){instrumentation=i;activity=a;}
    public static void run(Instrumentation instrumentation,Activity activity) throws Exception {
        SuggestEmojiDeviceChecks t=new SuggestEmojiDeviceChecks(instrumentation,activity);
        try {
            t.main(()->{t.queryChecks();t.setup();t.firstRequest();});
            SystemClock.sleep(700);t.main(t::firstResult);
            t.awaitCandidate(); t.exerciseAnimatedCandidate();
            t.coldUnicodeAtlas();
            t.main(t::prepareKeyword);
            t.awaitCandidate();
            t.main(()->{t.keywordAndFocusChecks();t.multilineAnchorChecks();t.softWrapAnchorCheck();t.prepareReplacement();});
            t.awaitCandidate(); t.main(t::finishReplacementAndPrepareMixed);
            t.awaitCandidate(); t.main(t::finishMixedAndStale);
            t.backgroundAndDestroy();
        } finally {t.main(()->{if(t.mentions!=null)t.mentions.destroy();if(t.controller!=null)t.controller.destroy();if(t.input!=null)t.input.onDestroy();if(t.parent!=null&&t.parent.getParent() instanceof ViewGroup)((ViewGroup)t.parent.getParent()).removeView(t.parent);});}
    }
    private void main(Runnable r){AtomicReference<Throwable> e=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{r.run();}catch(Throwable t){e.set(t);}});if(e.get()!=null)throw new AssertionError("suggest emoji checks",e.get());}
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    private void queryChecks(){
        EmojiSuggestionQuery q=EmojiSuggestionQuery.parse("hello world tail",11,11);
        check(q!=null&&q.type==EmojiSuggestionQuery.Type.KEYWORD&&q.start==0&&q.end==11&&q.query.equals("hello world"),"keyword is complete prefix");
        check(EmojiSuggestionQuery.parse("",0,0)==null&&EmojiSuggestionQuery.parse("abc",0,2)==null,"empty and selection rejected");
        StringBuilder longText=new StringBuilder();for(int i=0;i<52;i++)longText.append('x');
        check(EmojiSuggestionQuery.parse(longText,52,52)==null&&EmojiSuggestionQuery.parse(longText,51,51)!=null,"upstream keyword length boundary");
        check(EmojiSuggestionQuery.parse("😀",1,1)==null,"surrogate cannot split");
        CharSequence unicode=Emoji.replaceEmoji("😀😀 tail",null,true);
        q=EmojiSuggestionQuery.parse(unicode,4,4);
        check(q!=null&&q.type==EmojiSuggestionQuery.Type.EMOJI&&q.start==2&&q.end==4&&q.query.equals("😀"),"complete emoji span before caret");
        SpannableString animated=new SpannableString("😀");animated.setSpan(new AnimatedEmojiSpan(LocalEmojiSamples.TGS_ID,null),0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        check(EmojiSuggestionQuery.parse(animated,2,2)==null,"animated span does not recurse");
    }
    private void setup(){
        try{LocalEmojiSamples.install(activity);}catch(Exception e){throw new AssertionError(e);}
        parent=new ChatInputLayout(activity,null);((ViewGroup)activity.findViewById(android.R.id.content)).addView(parent,new ViewGroup.LayoutParams(-1,-1));
        input=new ChatActivityEnterView(activity,parent,null,true);parent.addView(input,new FrameLayout.LayoutParams(-1,AndroidUtilities.dp(50),Gravity.BOTTOM));parent.setEnterView(input);layout();
        input.getEditField().requestFocus();check(input.getEditField().hasFocus(),"input focus");
        controller=new EmojiSuggestionsController(input,parent,(q,result)->{queries.add(q);callbacks.add(result);},null);
        controller.setDelegate(new EmojiSuggestionsController.Delegate(){
            @Override public void onSelected(EmojiSuggestion value){selections++;selected=value;}
            @Override public void onLongPressed(EmojiSuggestion value){longs++;selected=value;}
        });
    }
    private void layout(){int w=AndroidUtilities.dp(320),h=AndroidUtilities.dp(600);parent.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));parent.layout(0,0,w,h);}
    private void firstRequest(){
        input.setFieldText("hello");input.getEditField().setSelection(5);controller.refresh();
        check(callbacks.isEmpty(),"first query delayed");
    }
    private void firstResult(){
        check(!callbacks.isEmpty()&&queries.get(queries.size()-1).query.equals("hello"),"first query after 600ms");
        EmojiSuggestion suggestion=new EmojiSuggestion("duck","🦆",LocalEmojiSamples.TGS_ID);
        callbacks.get(callbacks.size()-1).onResult(Collections.singletonList(suggestion));
        layout();check(controller.getView().isOpen()&&parent.getEmojiSuggestionsView()==controller.getView(),"emoji panel attached");
    }
    private View firstChild(){
        layout();org.telegram.tgui.recyclerview.RecyclerView.ViewHolder holder=controller.getView().getListView().findViewHolderForAdapterPosition(0);
        View v=holder==null?null:holder.itemView;
        check(v instanceof SuggestEmojiView.EmojiImageView,"current adapter-position candidate attached");
        check(((SuggestEmojiView.EmojiImageView)v).getSuggestion()==controller.getView().getSuggestions().get(0),"holder bound to current candidate identity");return v;
    }
    private void exerciseAnimatedCandidate() throws Exception {
        final SuggestEmojiView.EmojiImageView[] cell=new SuggestEmojiView.EmojiImageView[1];
        main(()->{cell[0]=(SuggestEmojiView.EmojiImageView)firstChild();check(cell[0].getMeasuredWidth()==AndroidUtilities.dp(44)&&cell[0].getMeasuredHeight()==AndroidUtilities.dp(52),"upstream emoji cell size");});
        boolean loaded=false;
        for(int i=0;i<100&&!loaded;i++){
            boolean[] ok={false};main(()->ok[0]=cell[0].drawable instanceof org.telegram.ui.Components.AnimatedEmojiDrawable && ((org.telegram.ui.Components.AnimatedEmojiDrawable)cell[0].drawable).isLoaded());loaded=ok[0];if(!loaded)SystemClock.sleep(40);
        }
        check(loaded,"local TGS candidate loads");
        main(()->{int w=controller.getView().getWidth(),h=controller.getView().getHeight();if(w>0&&h>0){Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);controller.getView().draw(new Canvas(b));b.recycle();}});
        main(()->{EmojiSuggestion expected=controller.getView().getSuggestions().get(0);controller.getView().getListView().longClickItem(cell[0],0);check(longs==1&&selected==expected,"candidate long press identity");});
    }
    private void coldUnicodeAtlas() throws Exception {
        final SuggestEmojiView.EmojiImageView[] cell={null};
        final int[] notifications={0}, draws={0}, drawsAtNotification={-1};
        final android.view.ViewTreeObserver.OnDrawListener drawListener=()->draws[0]++;
        final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate observer=(id,account,args)->{
            notifications[0]++; drawsAtNotification[0]=draws[0];
        };
        try {
            main(()->{
                input.setFieldText("cold");input.getEditField().setSelection(4);controller.refresh();
                callbacks.get(callbacks.size()-1).onResult(Collections.singletonList(new EmojiSuggestion("cold-unicode","😀",0)));
            });
            awaitCandidate();
            main(()->{
                cell[0]=(SuggestEmojiView.EmojiImageView)firstChild();
                check(cell[0].drawable instanceof Emoji.SimpleEmojiDrawable,"Unicode atlas drawable");
                try {
                    Object info=((Emoji.SimpleEmojiDrawable)cell[0].drawable).getDrawableInfo();
                    java.lang.reflect.Field p=info.getClass().getDeclaredField("page"),s=info.getClass().getDeclaredField("page2");p.setAccessible(true);s.setAccessible(true);
                    java.lang.reflect.Field cache=Emoji.class.getDeclaredField("emojiBmp");cache.setAccessible(true);
                    ((Bitmap[][])cache.get(null))[p.getByte(info)][s.getShort(info)]=null;
                } catch(ReflectiveOperationException e){throw new AssertionError(e);}
                check(!((Emoji.EmojiDrawable)cell[0].drawable).isLoaded(),"atlas starts cold");
                org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(observer,org.telegram.messenger.NotificationCenter.emojiLoaded);
                parent.getViewTreeObserver().addOnDrawListener(drawListener);
                Bitmap first=Bitmap.createBitmap(cell[0].getWidth(),cell[0].getHeight(),Bitmap.Config.ARGB_8888);
                cell[0].draw(new Canvas(first));first.recycle();
            });
            boolean complete=false;
            for(int i=0;i<150&&!complete;i++){
                boolean[] ok={false};main(()->ok[0]=notifications[0]>0 && ((Emoji.EmojiDrawable)cell[0].drawable).isLoaded() && draws[0]>drawsAtNotification[0]);
                complete=ok[0];if(!complete)SystemClock.sleep(40);
            }
            check(complete,"cold atlas automatically notifies and causes a later view-tree draw");
            main(()->{
                Bitmap loaded=Bitmap.createBitmap(cell[0].getWidth(),cell[0].getHeight(),Bitmap.Config.ARGB_8888);cell[0].draw(new Canvas(loaded));
                int warm=0;for(int y=0;y<loaded.getHeight();y++)for(int x=0;x<loaded.getWidth();x++){
                    int color=loaded.getPixel(x,y),r=android.graphics.Color.red(color),g=android.graphics.Color.green(color),b=android.graphics.Color.blue(color);
                    if(android.graphics.Color.alpha(color)>80&&r>130&&g>70&&r>b+35&&g>b+20)warm++;
                }loaded.recycle();check(warm>8,"loaded Unicode renders colored emoji pixels instead of gray placeholder");
            });
        } finally {
            main(()->{
                org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(observer,org.telegram.messenger.NotificationCenter.emojiLoaded);
                if(parent.getViewTreeObserver().isAlive())parent.getViewTreeObserver().removeOnDrawListener(drawListener);
            });
        }
    }
    private void prepareKeyword(){
        EmojiSuggestion unicode=new EmojiSuggestion("smile","🙂",0);
        input.setFieldText("cat suffix");input.getEditField().setSelection(3);controller.refresh();
        callbacks.get(callbacks.size()-1).onResult(Collections.singletonList(unicode));
    }
    private void keywordAndFocusChecks(){
        EmojiSuggestion unicode=controller.getView().getSuggestions().get(0);
        controller.getView().getListView().clickItem(firstChild(),0);
        check(input.getEditField().getText().toString().equals("🙂 suffix")&&input.getEditField().getSelectionStart()==2,
                "single keyword replacement preserves suffix and caret");
        input.setFieldText("focus");input.getEditField().setSelection(5);controller.refresh();
        EmojiSuggestionsController.Results pending=callbacks.get(callbacks.size()-1);
        input.getEditField().clearFocus();controller.refresh();
        pending.onResult(Collections.singletonList(unicode));
        check(!controller.getView().isOpen(),"focus loss blocks late result");
        input.getEditField().requestFocus();check(input.getEditField().hasFocus(),"focus restored");
    }
    private float parentOffset(){
        try{java.lang.reflect.Field f=ChatInputLayout.class.getDeclaredField("emojiSuggestionsAnchorOffset");f.setAccessible(true);return f.getFloat(parent);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private void multilineAnchorChecks(){
        EmojiSuggestion unicode=new EmojiSuggestion("anchor","🙂",0);
        CharSequence text=Emoji.replaceEmoji("😀\n😀",input.getEditField().getPaint().getFontMetricsInt(),true);
        input.setFieldText(text);input.getEditField().setSelection(2);controller.refresh();callbacks.get(callbacks.size()-1).onResult(Collections.singletonList(unicode));layout();
        float first=parentOffset();
        input.getEditField().setSelection(5);controller.refresh();callbacks.get(callbacks.size()-1).onResult(Collections.singletonList(unicode));layout();
        float second=parentOffset();
        check(second>first,"emoji on second input line moves anchor down");
    }
    private void softWrapAnchorCheck(){
        android.widget.EditText edit=input.getEditField(); int emojiStart=-1,emojiEnd=-1;
        for(int count=4;count<=80;count++){
            StringBuilder raw=new StringBuilder();for(int i=0;i<count;i++)raw.append('W');raw.append("😀x");
            CharSequence text=Emoji.replaceEmoji(raw,edit.getPaint().getFontMetricsInt(),true);
            input.setFieldText(text);layout();emojiStart=count;emojiEnd=count+2;
            android.text.Layout textLayout=edit.getLayout();
            if(textLayout!=null&&textLayout.getLineForOffset(emojiStart)!=textLayout.getLineForOffset(emojiEnd))break;
            emojiStart=-1;
        }
        check(emojiStart>=0,"fixture places emoji at soft-wrap line end");
        edit.setSelection(emojiEnd);controller.refresh();
        Bitmap b=Bitmap.createBitmap(edit.getWidth(),edit.getHeight(),Bitmap.Config.ARGB_8888);edit.draw(new Canvas(b));b.recycle();
        Spanned text=(Spanned)edit.getText();Emoji.EmojiSpan found=null;
        for(Emoji.EmojiSpan span:text.getSpans(emojiStart,emojiEnd,Emoji.EmojiSpan.class))
            if(text.getSpanStart(span)==emojiStart&&text.getSpanEnd(span)==emojiEnd)found=span;
        check(found!=null&&found.drawn,"soft-wrap emoji span was actually drawn");
        callbacks.get(callbacks.size()-1).onResult(Collections.singletonList(new EmojiSuggestion("wrap","🙂",0)));
        int[] e=new int[2],p=new int[2];edit.getLocationInWindow(e);parent.getLocationInWindow(p);
        float expectedX=e[0]-p[0]+edit.getPaddingLeft()-edit.getScrollX()+found.lastDrawX;
        float expectedY=-edit.getHeight()-edit.getScrollY()+found.lastDrawY+AndroidUtilities.dp(5);
        try{java.lang.reflect.Field f=SuggestEmojiView.class.getDeclaredField("arrowX");f.setAccessible(true);
            check(Math.abs(f.getFloat(controller.getView())-expectedX)<1f,"soft-wrap arrow uses drawn span center");
        }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        check(Math.abs(parentOffset()-expectedY)<1f,"soft-wrap vertical anchor uses drawn span center");
    }
    private void prepareReplacement(){
        replacementDuck=new EmojiSuggestion("duck-replacement","🦆",LocalEmojiSamples.TGS_ID);
        CharSequence text=Emoji.replaceEmoji("😀😀 tail",input.getEditField().getPaint().getFontMetricsInt(),true);
        input.setFieldText(text);input.getEditField().setSelection(4);controller.refresh();
        check(queries.get(queries.size()-1).type==EmojiSuggestionQuery.Type.EMOJI,"emoji query from input");
        replacementQuery=queries.get(queries.size()-1);
        callbacks.get(callbacks.size()-1).onResult(Collections.singletonList(replacementDuck));
    }
    private void awaitCandidate() throws Exception {
        boolean ready=false;
        for(int i=0;i<50&&!ready;i++){
            boolean[] found={false};main(()->{layout();org.telegram.tgui.recyclerview.RecyclerView.ViewHolder holder=controller.getView().getListView().findViewHolderForAdapterPosition(0);
                found[0]=holder!=null&&holder.itemView instanceof SuggestEmojiView.EmojiImageView
                        &&((SuggestEmojiView.EmojiImageView)holder.itemView).getSuggestion()==controller.getView().getSuggestions().get(0);});
            ready=found[0];if(!ready)SystemClock.sleep(20);
        }
        check(ready,"current adapter holder binds expected candidate within one second");
    }
    private void finishReplacementAndPrepareMixed(){
        EmojiSuggestion duck=replacementDuck;EmojiSuggestionQuery selectedQuery=replacementQuery;View child=firstChild();
        EmojiSuggestion bound=((SuggestEmojiView.EmojiImageView)child).getSuggestion();
        int before=selections;controller.getView().getListView().clickItem(child,0);
        Spanned result=input.getEditField().getText();AnimatedEmojiSpan[] spans=result.getSpans(0,result.length(),AnimatedEmojiSpan.class);
        check(selections==before+1&&selected==duck&&result.toString().equals("🦆🦆 tail")&&input.getEditField().getSelectionStart()==4,
                "repeated emoji replacement: text="+result+" cursor="+input.getEditField().getSelectionStart()
                        +" callbacks="+before+"->"+selections+" selected="+(selected==null?"null":selected.id)
                        +" expected="+duck.id+" child="+(bound==null?"null":bound.id)+" childIsExpected="+(bound==duck)
                        +" rows="+controller.getView().getSuggestions().size()+" query="+selectedQuery.type+":"+selectedQuery.start+".."+selectedQuery.end+":"+selectedQuery.query);
        check(spans.length==2&&spans[0].getDocumentId()==LocalEmojiSamples.TGS_ID&&spans[1].getDocumentId()==LocalEmojiSamples.TGS_ID,"actual local animated emoji spans");
        int count=queries.size();controller.refresh();check(queries.size()==count,"animated output not requeried");
        SpannableString mixed=new SpannableString("😀😀");
        mixed.setSpan(new AnimatedEmojiSpan(LocalEmojiSamples.JSON_ID,null),0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        CharSequence mixedEmoji=Emoji.replaceEmoji(mixed,input.getEditField().getPaint().getFontMetricsInt(),false);
        input.setFieldText(mixedEmoji);input.getEditField().setSelection(4);controller.refresh();
        callbacks.get(callbacks.size()-1).onResult(Collections.singletonList(duck));
    }
    private void finishMixedAndStale(){
        controller.getView().getListView().clickItem(firstChild(),0);
        Spanned mixedResult=input.getEditField().getText();
        AnimatedEmojiSpan[] preserved=mixedResult.getSpans(0,mixedResult.length(),AnimatedEmojiSpan.class);
        boolean prior=false, next=false;for(AnimatedEmojiSpan span:preserved){
            if(span.getDocumentId()==LocalEmojiSamples.JSON_ID && mixedResult.getSpanStart(span)==0)prior=true;
            if(span.getDocumentId()==LocalEmojiSamples.TGS_ID && mixedResult.getSpanStart(span)==2)next=true;
        }
        check(mixedResult.toString().equals("😀🦆")&&prior&&next,"replacement stops at earlier animated span");
        input.setFieldText("first");input.getEditField().setSelection(5);controller.refresh();EmojiSuggestionsController.Results stale=callbacks.get(callbacks.size()-1);
        input.setFieldText("second");input.getEditField().setSelection(6);controller.refresh();EmojiSuggestionsController.Results newest=callbacks.get(callbacks.size()-1);
        java.util.List<EmojiSuggestion> retained=new ArrayList<>(controller.getView().getSuggestions());stale.onResult(Collections.singletonList(new EmojiSuggestion("old","🙂",0)));
        check(controller.getView().getSuggestions().equals(retained),"late result does not replace suggestions");
        newest.onResult(Collections.singletonList(new EmojiSuggestion("new","🙂",0)));check(controller.getView().isOpen(),"newest result visible");
        mentions=new MentionsController(input,parent,(q,done)->{},null);
        int searches=queries.size();input.setFieldText("@alice");input.getEditField().setSelection(6);controller.refresh();
        check(queries.size()==searches&&!controller.getView().isOpen(),"mention takes priority");mentions.destroy();mentions=null;
    }
    private void backgroundAndDestroy() throws Exception {
        final EmojiSuggestionsController.Results[] response=new EmojiSuggestionsController.Results[1];
        main(()->{input.setFieldText("background");input.getEditField().setSelection(10);controller.refresh();response[0]=callbacks.get(callbacks.size()-1);});
        EmojiSuggestion value=new EmojiSuggestion("worker","😀",0);
        Thread worker=new Thread(()->response[0].onResult(Collections.singletonList(value)));worker.start();worker.join(2000);check(!worker.isAlive(),"background callback completes");
        main(()->check(controller.getView().getSuggestions().equals(Collections.singletonList(value)),"background result applied"));
        main(()->{input.setFieldText("late");input.getEditField().setSelection(4);controller.refresh();response[0]=callbacks.get(callbacks.size()-1);controller.destroy();});
        Thread late=new Thread(()->response[0].onResult(Collections.singletonList(value)));late.start();late.join(2000);
        main(()->check(!controller.getView().isOpen()&&controller.getView().getSuggestions().isEmpty(),"destroy rejects late callback"));
    }
}
