package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.MentionCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatInputLayout;
import org.telegram.ui.Components.Mention;
import org.telegram.ui.Components.MentionQuery;
import org.telegram.ui.Components.MentionsContainerView;
import org.telegram.ui.Components.MentionsController;
import org.telegram.ui.Components.URLSpanUserMention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded mention checks, invoked from the instrumentation worker thread. */
public final class MentionsDeviceChecks {
    private final Instrumentation instrumentation;
    private final Activity activity;
    private ChatInputLayout parent;
    private ChatActivityEnterView input;
    private MentionsController controller;
    private final ArrayList<MentionsController.Results> pending=new ArrayList<>();
    private final ArrayList<MentionQuery> queries=new ArrayList<>();
    private MentionsDeviceChecks(Instrumentation i,Activity a){instrumentation=i;activity=a;}
    public static void run(Instrumentation instrumentation,Activity activity) throws Exception {
        MentionsDeviceChecks t=new MentionsDeviceChecks(instrumentation,activity);
        try {
            t.main(()->{t.queryChecks();t.setup();t.synchronousChecks();});
            t.backgroundResultCheck();
        } finally {t.main(()->{if(t.controller!=null)t.controller.destroy();if(t.input!=null)t.input.onDestroy();if(t.parent!=null&&t.parent.getParent() instanceof ViewGroup)((ViewGroup)t.parent.getParent()).removeView(t.parent);});}
    }
    private void main(Runnable r){AtomicReference<Throwable> error=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{r.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError("mentions checks",error.get());}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    private void queryChecks(){
        MentionQuery q=MentionQuery.parse("hello @alice tail",10,10);
        check(q!=null&&q.start==6&&q.end==10&&q.query.equals("ali")&&q.text.equals("hello @alice tail"),"cursor-middle range");
        check(MentionQuery.parse("test@example.com",16,16)==null,"email not a mention");
        check(MentionQuery.parse("word @foo bar",13,13)!=null,"name search permits spaces");
        q=MentionQuery.parse("line\n@user",10,10);check(q!=null&&q.start==5&&q.query.equals("user"),"newline boundary");
        check(MentionQuery.parse("@name",1,3)==null,"noncollapsed selection");
        check(MentionQuery.parse("@😀",2,2)==null,"cursor cannot split surrogate pair");
        check(MentionQuery.parse("@😀",3,3)!=null,"complete supplementary character");
        check(MentionQuery.parse("a #tag",6,6)==null,"other suggestion tokens ignored");
    }
    private void setup(){
        parent=new ChatInputLayout(activity,null);((ViewGroup)activity.findViewById(android.R.id.content)).addView(parent,new ViewGroup.LayoutParams(-1,-1));
        input=new ChatActivityEnterView(activity,parent,null,true);parent.addView(input,new FrameLayout.LayoutParams(-1,AndroidUtilities.dp(50),Gravity.BOTTOM));parent.setEnterView(input);
        layout(); input.getEditField().requestFocus();
        check(input.getEditField().hasFocus(), "mention fixture input focus");
        controller=new MentionsController(input,parent,(q,result)->{queries.add(q);pending.add(result);},null);
        layout();
    }
    private void layout(){int w=AndroidUtilities.dp(320),h=AndroidUtilities.dp(600);parent.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));parent.layout(0,0,w,h);}
    private MentionsController.Results request(String value,int cursor){
        input.setFieldText(value);input.getEditField().setSelection(cursor);controller.refresh();
        check(!pending.isEmpty(),"provider received query");return pending.get(pending.size()-1);
    }
    private void select(Mention mention){
        MentionCell fake=new MentionCell(activity,null);fake.setMention(mention);
        controller.getView().getListView().clickItem(fake,0);
    }
    private void synchronousChecks(){
        Mention first=new Mention(11,"Alice","alice",null),second=new Mention(12,"Bob","bob",null);
        MentionsController.Results stale=request("@ali",4);MentionsController.Results latest=request("@bob",4);
        List<Mention> retained=new ArrayList<>(controller.getView().getMentions());
        stale.onResult(Collections.singletonList(first));check(controller.getView().getMentions().equals(retained),"old query does not replace rows");
        latest.onResult(Collections.singletonList(second));check(controller.getView().getMentions().equals(Collections.singletonList(second)),"latest query displayed");
        MentionsController.Results oldSame=request("@bob X",4);MentionsController.Results newSame=request("@bob Y",4);
        retained=new ArrayList<>(controller.getView().getMentions());
        check(controller.getView().isOpen() && retained.equals(Collections.singletonList(second)), "old panel remains visible while waiting");
        String waitingText=input.getEditField().getText().toString();
        select(second);check(input.getEditField().getText().toString().equals(waitingText),"waiting old candidate cannot insert");
        oldSame.onResult(Collections.singletonList(first));check(controller.getView().getMentions().equals(retained)&&controller.getView().isOpen(),"same query with changed text ignored");
        newSame.onResult(Collections.singletonList(first));
        check(controller.getView().isOpen()&&controller.getView().getMentions().equals(Collections.singletonList(first)),"latest synchronous results replace rows without closing panel");
        request("@bob Y",4).onResult(Collections.singletonList(second));
        check(controller.getView().isOpen(),"synchronous update keeps panel open");
        layout();MentionsContainerView panel=controller.getView();check(panel.isOpen()&&parent.getInputSuggestionsView()==panel,"panel open and attached");
        check(panel.getHeight()<=AndroidUtilities.dp(126)&&panel.getBottom()==input.getTop()+AndroidUtilities.dp(2),"panel anchored above input");
        select(second);check(input.getEditField().getText().toString().equals("@bob  Y"),"selection replaces only prefix and preserves suffix");
        Mention nameOnly=new Mention(44,"Display Name",null,null);
        request("hello @di end",9).onResult(Collections.singletonList(nameOnly));select(nameOnly);
        Spanned result=input.getEditField().getText();URLSpanUserMention[] spans=result.getSpans(0,result.length(),URLSpanUserMention.class);
        check(result.toString().equals("hello Display Name  end")&&spans.length==1&&"44".equals(spans[0].getURL()),"no username inserts mention span and preserves suffix");
        MentionsController.Results changed=request("@alice",6);input.getEditField().setSelection(2);changed.onResult(Collections.singletonList(first));
        check(!controller.getView().getMentions().contains(first),"changed selection rejects stale results");
        String changedText=input.getEditField().getText().toString();
        List<Mention> existing=controller.getView().getMentions();
        if(!existing.isEmpty())select(existing.get(0));
        check(input.getEditField().getText().toString().equals(changedText),"changed selection prevents old candidate insertion");
        Mention prefixed=new Mention(45,"Prefixed","@already",null);
        check("already".equals(prefixed.username),"leading at-sign normalized");
        request("@al",3).onResult(Collections.singletonList(prefixed));select(prefixed);
        check(input.getEditField().getText().toString().equals("@already "),"prefixed username inserted with single at-sign");
        MentionsController.Results focusPending=request("@focus",6);
        List<Mention> beforeFocusLoss=new ArrayList<>(controller.getView().getMentions());
        input.getEditField().clearFocus();controller.refresh();
        check(!input.getEditField().hasFocus()&&!controller.getView().isOpen(),"focus loss hides suggestions");
        focusPending.onResult(Collections.singletonList(first));
        check(!controller.getView().isOpen()&&controller.getView().getMentions().equals(beforeFocusLoss),"late result while unfocused ignored");
        input.getEditField().requestFocus();controller.refresh();
        pending.get(pending.size()-1).onResult(Collections.singletonList(first));
        check(controller.getView().isOpen()&&controller.getView().getMentions().equals(Collections.singletonList(first)),"focus restoration permits search");
        ColorDrawable avatar=new ColorDrawable(Color.RED);avatar.setBounds(1,2,20,24);Rect bounds=new Rect(avatar.getBounds());
        MentionCell cell=new MentionCell(activity,null);cell.setMention(new Mention(80,"Sample","sample",avatar));
        cell.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(320),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        check(cell.getMeasuredHeight()==AndroidUtilities.dp(36),"upstream 36dp mention row");cell.layout(0,0,cell.getMeasuredWidth(),cell.getMeasuredHeight());
        Bitmap bitmap=Bitmap.createBitmap(cell.getWidth(),cell.getHeight(),Bitmap.Config.ARGB_8888);cell.draw(new Canvas(bitmap));bitmap.recycle();check(avatar.getBounds().equals(bounds),"avatar bounds restored");
        ArrayList<Mention> source=new ArrayList<>(Arrays.asList(first,second));panel.setMentions(source);source.clear();check(panel.getMentions().size()==2,"panel defensively copies results");
    }
    private void backgroundResultCheck() throws Exception {
        final MentionsController.Results[] callback=new MentionsController.Results[1];
        main(()->callback[0]=request("@thread",7));
        Mention background=new Mention(99,"Background","background",null);
        Thread worker=new Thread(()->callback[0].onResult(Collections.singletonList(background)));worker.start();worker.join(2000);check(!worker.isAlive(),"provider callback finishes");
        main(()->check(controller.getView().getMentions().equals(Collections.singletonList(background)),"background results delivered on main"));
        final MentionsController.Results[] late=new MentionsController.Results[1];
        main(()->{late[0]=request("@late",5);controller.destroy();});
        Thread afterDestroy=new Thread(()->late[0].onResult(Collections.singletonList(background)));afterDestroy.start();afterDestroy.join(2000);
        main(()->check(controller.getView().getMentions().isEmpty()&&!controller.getView().isOpen(),"destroy rejects delayed callback"));
    }
}
