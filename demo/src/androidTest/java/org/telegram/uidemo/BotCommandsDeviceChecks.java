package org.telegram.uidemo;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Color;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgui.recyclerview.RecyclerView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.bots.BotCommand;
import org.telegram.ui.bots.BotCommandsMenuView;
import org.telegram.ui.bots.BotCommandsMenuContainer;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatInputLayout;
import org.telegram.ui.bots.ReplyKeyboard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded command-menu checks, invoked from the instrumentation worker thread. */
public final class BotCommandsDeviceChecks {
    private final Instrumentation instrumentation;private final Activity activity;
    private FrameLayout host;private ChatInputLayout parent;private ChatActivityEnterView input;
    private int sent;private BotCommand selected;private List<BotCommand> reentry;
    private BotCommandsDeviceChecks(Instrumentation i,Activity a){instrumentation=i;activity=a;}
    public static void run(Instrumentation instrumentation,Activity activity){
        BotCommandsDeviceChecks t=new BotCommandsDeviceChecks(instrumentation,activity);
        try {t.main(()->{t.setup();t.modelAndRow();t.paddingFallback();});
            t.inputChecks(); t.main(()->{t.surfaceChecks();t.startScrollCheck();});
            SystemClock.sleep(400);t.main(t::finishScrollCheck);
        }finally{t.main(()->{if(t.input!=null)t.input.onDestroy();if(t.host!=null&&t.host.getParent() instanceof ViewGroup)((ViewGroup)t.host.getParent()).removeView(t.host);});}
    }
    private void main(Runnable action){AtomicReference<Throwable> e=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable t){e.set(t);}});if(e.get()!=null)throw new AssertionError("bot commands checks",e.get());}
    private static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    private static final class Palette implements Theme.ResourcesProvider {
        boolean dark;
        @Override public int getColor(int key){if(key==Theme.key_windowBackgroundWhiteBlackText)return dark?Color.YELLOW:Color.BLUE;return Theme.getColor(key);}
        @Override public boolean isDark(){return dark;}
    }
    private void setup(){
        host=new FrameLayout(activity);((ViewGroup)activity.findViewById(android.R.id.content)).addView(host,new ViewGroup.LayoutParams(-1,-1));
        parent=new ChatInputLayout(activity,null);host.addView(parent,new FrameLayout.LayoutParams(-1,-1));
        input=new ChatActivityEnterView(activity,parent,null,true);
        parent.addView(input,new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.BOTTOM));parent.setEnterView(input);
        input.onResume();
        input.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate(){
            @Override public void onBotCommandSelected(BotCommand command){sent++;selected=command;if(reentry!=null){List<BotCommand> next=reentry;reentry=null;input.setBotCommands(next);input.showBotCommandsMenu();}}
        });layout(parent,320,600);
    }
    private static void layout(View v,int w,int h){int width=AndroidUtilities.dp(w),height=AndroidUtilities.dp(h);v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,width,height);}
    private void modelAndRow(){
        SpannableStringBuilder desc=new SpannableStringBuilder("Help");BotCommand a=new BotCommand("a","//start",desc);desc.clear();
        check(a.command.equals("/start")&&a.description.toString().equals("Help"),"normalized command and immutable description");
        boolean invalid=false;try{new BotCommand("x","/two words","");}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"invalid command whitespace");
        Palette palette=new Palette();BotCommandsMenuView.BotCommandsAdapter adapter=new BotCommandsMenuView.BotCommandsAdapter(palette);
        ArrayList<BotCommand> source=new ArrayList<>(Collections.singletonList(a));adapter.setCommands(source);source.clear();check(adapter.getCommands().size()==1,"adapter copies snapshot");
        RecyclerView.ViewHolder holder=adapter.onCreateViewHolder(host,0);adapter.onBindViewHolder(holder,0);
        BotCommandsMenuView.BotCommandView row=(BotCommandsMenuView.BotCommandView)holder.itemView;
        row.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(320),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        TextView label=(TextView)row.getChildAt(0), command=(TextView)row.getChildAt(1);
        check(row.getBotCommand()==a&&row.getCommand().equals("/start"),"bound command identity");
        check(row.getPaddingLeft()==AndroidUtilities.dp(16)&&row.getPaddingTop()==AndroidUtilities.dp(8)
                &&row.getPaddingRight()==AndroidUtilities.dp(16)&&row.getPaddingBottom()==AndroidUtilities.dp(8),"upstream row padding");
        check(Math.abs(label.getTextSize()-AndroidUtilities.dp(16))<=1&&Math.abs(command.getTextSize()-AndroidUtilities.dp(14))<=1
                &&label.getMaxLines()==2,"description/command text sizes and line limit");
        check(row.getMeasuredHeight()==Math.max(label.getMeasuredHeight(),command.getMeasuredHeight())+AndroidUtilities.dp(16),
                "row height uses measured text plus vertical padding");
        check(label.getCurrentTextColor()==Color.BLUE,"theme provider row color");palette.dark=true;row.updateColors();check(label.getCurrentTextColor()==Color.YELLOW,"theme color update");
        BotCommandsMenuView entry=new BotCommandsMenuView(activity,palette);entry.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(200),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        check(entry.getMeasuredHeight()==AndroidUtilities.dp(32),"32dp command entry");
    }
    private void paddingFallback(){
        input.setBotCommands(Arrays.asList(new BotCommand("p1","one","One"),new BotCommand("p2","two","Two"),
                new BotCommand("p3","three","Three"),new BotCommand("p4","four","Four"),new BotCommand("p5","five","Five")));
        input.showBotCommandsMenu();BotCommandsMenuContainer menu=input.getBotCommandsMenuContainer();
        check(menu.listView.getChildCount()==0,"fallback fixture starts before rows laid out");
        int width=AndroidUtilities.dp(320),height=AndroidUtilities.dp(400);
        menu.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        check(menu.listView.getPaddingTop()==Math.max(0,height-AndroidUtilities.dp(8+36*4.3f)),"unmeasured rows use 36dp 4.3-row fallback");
        menu.layout(0,0,width,height);
        int count=Math.min(4,menu.listView.getChildCount()),total=0;
        for(int i=0;i<count;i++)total+=menu.listView.getChildAt(i).getMeasuredHeight();
        check(count==4,"four rows actually measured");
        menu.requestLayout();menu.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        menu.layout(0,0,width,height);
        check(menu.listView.getPaddingTop()==Math.max(0,height-total-AndroidUtilities.dp(8)-AndroidUtilities.dp(12)),"laid out rows use actual heights plus 8dp and 12dp");
        input.dismissBotCommandsMenu();
    }
    private BotCommandsMenuView.BotCommandView awaitRow(BotCommand expected) {
        final BotCommandsMenuView.BotCommandView[] row={null};
        for(int i=0;i<50&&row[0]==null;i++){
            main(()->{layout(parent,320,600);BotCommandsMenuContainer menu=input.getBotCommandsMenuContainer();
                RecyclerView.ViewHolder holder=menu.listView.findViewHolderForAdapterPosition(0);
                if(holder!=null&&holder.itemView instanceof BotCommandsMenuView.BotCommandView){
                    BotCommandsMenuView.BotCommandView candidate=(BotCommandsMenuView.BotCommandView)holder.itemView;
                    if(candidate.getBotCommand()==expected)row[0]=candidate;
                }});
            if(row[0]==null)SystemClock.sleep(20);
        }
        check(row[0]!=null,"attached row binds current command within 1s");return row[0];
    }
    private void inputChecks(){
        BotCommand a=new BotCommand("a","start","Start"),b=new BotCommand("b","help","Help");
        main(()->{input.setBotCommands(Arrays.asList(a,b));check(input.getBotCommands().size()==2,"commands bound");
            check(input.getBotCommandsMenuButton()!=null,"menu button exists");input.setFieldText("draft");input.showBotCommandsMenu();check(input.botCommandsMenuIsShowing(),"menu opens");});
        BotCommandsMenuView.BotCommandView first=awaitRow(a);
        main(()->{int before=sent;input.getBotCommandsMenuContainer().listView.clickItem(first,0);
            check(sent==before+1&&selected==a&&android.text.TextUtils.isEmpty(input.getFieldText())&&!input.botCommandsMenuIsShowing(),"short press clears draft and delivers identity");
            input.showBotCommandsMenu();});
        BotCommandsMenuView.BotCommandView old=awaitRow(a);
        BotCommand replacement=new BotCommand("new","next","Next");
        main(()->{BotCommandsMenuContainer menu=input.getBotCommandsMenuContainer();input.setBotCommands(Collections.singletonList(replacement));
            int before=sent;menu.listView.clickItem(old,0);check(sent==before,"old row rejected after snapshot change");input.showBotCommandsMenu();});
        BotCommandsMenuView.BotCommandView next=awaitRow(replacement);
        main(()->{int before=sent;input.getBotCommandsMenuContainer().listView.longClickItem(next,0);
            check(sent==before&&input.getFieldText().toString().equals("/next ")&&!input.botCommandsMenuIsShowing(),"long press inserts command without sending");input.showBotCommandsMenu();});
        BotCommandsMenuView.BotCommandView nextAgain=awaitRow(replacement);
        BotCommand newer=new BotCommand("newer","fresh","Fresh");
        main(()->{int before=sent;reentry=Collections.singletonList(newer);input.getBotCommandsMenuContainer().listView.clickItem(nextAgain,0);
            check(sent==before+1&&selected==replacement&&input.getBotCommands().get(0)==newer&&input.botCommandsMenuIsShowing(),"callback reentry preserves new menu");
            input.setBotCommands(Collections.emptyList());check(!input.botCommandsMenuIsShowing()&&input.getBotCommands().isEmpty(),"empty commands close menu");
            input.setBotCommands(Collections.singletonList(a));input.setEditingMessageObject(new ChatActivityEnterView.EditingMessage(1,"edit",null,false),false);
            input.showBotCommandsMenu();check(!input.botCommandsMenuIsShowing(),"editing blocks menu");input.setEditingMessageObject(null,false);
            try{java.lang.reflect.Field f=ChatActivityEnterView.class.getDeclaredField("recordingAudioVideo");f.setAccessible(true);f.setBoolean(input,true);input.showBotCommandsMenu();check(!input.botCommandsMenuIsShowing(),"recording blocks menu");f.setBoolean(input,false);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
        });
    }
    private void surfaceChecks(){
        input.setFieldText("");
        input.setBotCommands(Arrays.asList(new BotCommand("1","one","One"),new BotCommand("2","two","Two"),
                new BotCommand("3","three","Three"),new BotCommand("4","four","Four"),new BotCommand("5","five","Five")));
        layout(parent,320,600);
        int menuWidth=input.getBotCommandsMenuButton().getMeasuredWidth();
        try{
            java.lang.reflect.Field f=ChatActivityEnterView.class.getDeclaredField("emojiButton");f.setAccessible(true);View emoji=(View)f.get(input);
            check(((ViewGroup.MarginLayoutParams)emoji.getLayoutParams()).leftMargin==AndroidUtilities.dp(10)+menuWidth,"emoji margin accounts for command entry");
            check(((ViewGroup.MarginLayoutParams)input.getEditField().getLayoutParams()).leftMargin==AndroidUtilities.dp(57)+menuWidth,"edit field margin accounts for command entry");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        ReplyKeyboard.Button reply=new ReplyKeyboard.Button("r","Reply",ReplyKeyboard.Button.Type.TEXT);
        input.setReplyKeyboard(new ReplyKeyboard("reply",Collections.singletonList(Collections.singletonList(reply)),true,false,null),false);
        input.showReplyKeyboard();int padding=input.getEmojiPadding();check(input.isReplyKeyboardShowing()&&padding>0,"reply panel open");
        input.showBotCommandsMenu();check(input.botCommandsMenuIsShowing()&&input.isReplyKeyboardShowing()&&input.getEmojiPadding()==padding,"menu coexists without changing reply padding");
        check(input.hidePopup(true)&&!input.botCommandsMenuIsShowing()&&input.isReplyKeyboardShowing(),"back first dismisses command menu");
        input.hidePopup(false);input.showEmojiView();padding=input.getEmojiPadding();
        input.showBotCommandsMenu();check(input.botCommandsMenuIsShowing()&&input.isPopupShowing()&&input.getEmojiPadding()==padding,"menu coexists with emoji panel");
        input.hidePopup(true);check(input.isPopupShowing(),"back leaves underlying emoji panel open");input.hidePopup(false);
        input.getEditField().requestFocus();
        org.telegram.ui.Components.MentionsController mentions=new org.telegram.ui.Components.MentionsController(input,parent,(q,r)->{},null);
        org.telegram.ui.Components.EmojiSuggestionsController emojis=new org.telegram.ui.Components.EmojiSuggestionsController(input,parent,(q,r)->{},null);
        mentions.getView().setMentions(Collections.singletonList(new org.telegram.ui.Components.Mention(77,"Name","name",null)));mentions.getView().show();
        emojis.getView().setSuggestions(Collections.singletonList(new org.telegram.ui.Components.EmojiSuggestion("e","😀",0)));emojis.getView().show();
        input.showBotCommandsMenu();
        check(!mentions.getView().isOpen()&&!emojis.getView().isOpen(),"open menu suppresses suggestions");
        mentions.destroy();emojis.destroy();input.dismissBotCommandsMenu();
    }
    private void startScrollCheck(){
        input.setBotCommands(Arrays.asList(new BotCommand("s1","s1","One"),new BotCommand("s2","s2","Two"),
                new BotCommand("s3","s3","Three"),new BotCommand("s4","s4","Four"),new BotCommand("s5","s5","Five")));input.showBotCommandsMenu();
        BotCommandsMenuContainer menu=input.getBotCommandsMenuContainer();layout(parent,320,600);layout(menu,320,400);
        int rows=0;for(int i=0;i<Math.min(4,menu.listView.getChildCount());i++)rows+=menu.listView.getChildAt(i).getMeasuredHeight();
        int expected=Math.max(0,menu.getMeasuredHeight()-(rows>0?rows+AndroidUtilities.dp(8)+AndroidUtilities.dp(12):AndroidUtilities.dp(8+36*4.3f)));
        check(menu.listView.getPaddingTop()==expected,"upstream 4.3-row menu padding");
        menu.listView.setTranslationY(AndroidUtilities.dp(8));menu.onStopNestedScroll(menu.listView);
        check(menu.isShowing(),"short downward pull springs back");
        menu.listView.setTranslationY(AndroidUtilities.dp(17));menu.onStopNestedScroll(menu.listView);
        check(!menu.isShowing(),"pull over 16dp dismisses");
        input.showBotCommandsMenu();check(input.botCommandsMenuIsShowing(),"reopen during dismissal");
    }
    private void finishScrollCheck(){
        check(input.botCommandsMenuIsShowing(),"reopened menu survives old dismiss animation");
        host.removeView(parent);
        check(!input.botCommandsMenuIsShowing()&&!input.getBotCommandsMenuButton().isOpened(),"detach synchronizes menu and entry state");
        host.addView(parent,new FrameLayout.LayoutParams(-1,-1));layout(parent,320,600);
        input.getBotCommandsMenuButton().performClick();
        check(input.botCommandsMenuIsShowing(),"first click after reattach opens menu");
        input.onDestroy();input.setBotCommands(Collections.singletonList(new BotCommand("late","late","Late")));input.showBotCommandsMenu();
        check(!input.botCommandsMenuIsShowing(),"destroy prevents reopening");input=null;
    }
}
