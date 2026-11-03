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
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.MediaViewer;
import org.telegram.ui.Components.GroupedPhotosListView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded real-dialog gallery checks, run on the instrumentation worker thread. */
public final class MediaViewerDeviceChecks {
    private final Instrumentation instrumentation; private final Activity activity;
    private MediaViewer viewer; private final ArrayList<String> visibility=new ArrayList<>();
    private final ArrayList<Integer> pages=new ArrayList<>(); private int closed;
    private final ArrayList<MediaViewer.Item> items=new ArrayList<>();
    private MediaViewerDeviceChecks(Instrumentation i,Activity a){instrumentation=i;activity=a;}
    public static void run(Instrumentation instrumentation,Activity activity) throws Exception {
        MediaViewerDeviceChecks t=new MediaViewerDeviceChecks(instrumentation,activity);
        try {
            t.main(()->{t.prepare();t.open(false);});SystemClock.sleep(400);
            t.main(t::initialChecks);SystemClock.sleep(300);t.main(t::stripClickAndGroups);
            t.main(t::beginFade);SystemClock.sleep(350);t.main(t::finishFadeAndReenter);
            SystemClock.sleep(300);t.main(t::gestureSetup);SystemClock.sleep(350);t.main(t::zoomCheckAndReset);
            SystemClock.sleep(350);t.main(t::swipePage);SystemClock.sleep(450);t.main(t::closeFirst);
            SystemClock.sleep(350);t.main(t::forceAllChecks);
            t.main(t::openLongGroup);SystemClock.sleep(400);t.main(t::longGroupChecks);
            t.main(t::openVideoGroup);SystemClock.sleep(400);t.main(t::tapVideo);
            t.awaitVideo();t.main(t::openVideoGroup);SystemClock.sleep(400);t.main(t::scrollVideo);t.awaitVideo();
        } finally {t.main(()->{if(t.viewer!=null&&t.viewer.isShowing())t.viewer.close(false);});}
    }
    private void main(Runnable action){AtomicReference<Throwable> e=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable t){e.set(t);}});if(e.get()!=null)throw new AssertionError("media viewer checks",e.get());}
    private static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new AssertionError(name,e);}}
    private void prepare(){
        for(int i=0;i<7;i++){
            ColorDrawable d=new ColorDrawable(Color.rgb(40+i*20,70,150));d.setBounds(1,2,15,19);
            MediaViewer.Item item=MediaViewer.Item.photo(i==2?null:d,"Sender","Date","Caption "+i);
            item.width=i%2==0?900:200;item.height=i%2==0?200:900;
            item.groupId=i>=1&&i<=3?70:i>=5?80:0;items.add(item);
        }
    }
    private void open(boolean all){
        visibility.clear();pages.clear();closed=0;
        viewer=MediaViewer.open(activity,items,1,new MediaViewer.Provider(){
            @Override public boolean forceAllInGroup(){return all;}
            @Override public void setSourceVisible(int index,boolean visible){visibility.add(index+":"+visible);}
            @Override public void onPageSelected(int index){pages.add(index);}
            @Override public void onClosed(){closed++;}
        });
        check(viewer!=null&&viewer.isShowing(),"real viewer dialog opens");
    }
    private static void draw(View view){if(view.getWidth()<=0||view.getHeight()<=0)return;Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));b.recycle();}
    private void initialChecks(){
        GroupedPhotosListView strip=viewer.getGroupedPhotosListView();
        check(viewer.getCurrentIndex()==1&&strip.getCount()==3&&strip.getIndex()==1&&strip.hasPhotos(),"initial contiguous group and absolute index");
        check(visibility.contains("1:false"),"initial source hidden");
        draw(strip);
        for(MediaViewer.Item item:items)if(item.photo!=null)check(item.photo.getBounds().equals(new Rect(1,2,15,19)),"host drawable bounds preserved");
        @SuppressWarnings("unchecked") ArrayList<Object> receivers=(ArrayList<Object>)field(strip,"imagesToDraw");
        check(!receivers.isEmpty(),"thumbnail receivers drawn");
        boolean sawPortrait=false,sawLandscape=false;
        for(Object receiver:receivers){
            int local=(Integer)field(receiver,"param");
            @SuppressWarnings("unchecked") List<GroupedPhotosListView.Entry> entries=(List<GroupedPhotosListView.Entry>)field(strip,"currentPhotos");
            if(local<0||local>=entries.size())continue;GroupedPhotosListView.Entry entry=entries.get(local);
            float w=(Float)field(receiver,"imageWidth"),h=(Float)field(receiver,"imageHeight");
            check(w>0&&h>0&&Math.abs(h-AndroidUtilities.dp(56))<=1,"positive 56dp thumbnail geometry");
            if(entry.width>entry.height)sawLandscape=true;if(entry.width<entry.height)sawPortrait=true;
        }
        check(sawPortrait&&sawLandscape,"portrait and landscape thumbnail entries");
        viewer.setCurrentIndex(2);check(viewer.getCurrentIndex()==2&&strip.getIndex()==2&&strip.getCount()==3,"null thumbnail page retained in group");
        check(visibility.indexOf("1:true")>=0&&visibility.indexOf("1:true")<visibility.indexOf("2:false")&&visibility.contains("2:false"),"old source restored before new source hidden");
        check(pages.contains(2),"host page callback uses absolute index");
    }
    private void stripClickAndGroups(){
        GroupedPhotosListView strip=viewer.getGroupedPhotosListView();draw(strip);
        @SuppressWarnings("unchecked") ArrayList<Object> receivers=(ArrayList<Object>)field(strip,"imagesToDraw");
        @SuppressWarnings("unchecked") List<GroupedPhotosListView.Entry> entries=(List<GroupedPhotosListView.Entry>)field(strip,"currentPhotos");
        Object target=null;for(Object r:receivers){int i=(Integer)field(r,"param");if(i>=0&&i<entries.size()&&entries.get(i).index==3)target=r;}
        check(target!=null,"adjacent strip item visible");
        float x=(Float)field(target,"imageX")+(Float)field(target,"imageWidth")/2f;
        float y=(Float)field(target,"imageY")+(Float)field(target,"imageHeight")/2f;
        long t=SystemClock.uptimeMillis();MotionEvent d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),u=MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,x,y,0);
        strip.onTouchEvent(d);strip.onTouchEvent(u);d.recycle();u.recycle();
        check(viewer.getCurrentIndex()==3&&strip.getIndex()==3,"thumbnail tap changes viewer absolute index");
        viewer.setCurrentIndex(4);check(viewer.getCurrentIndex()==4&&!strip.hasPhotos(),"ungrouped page hides strip");
        viewer.setCurrentIndex(5);check(strip.getCount()==2&&strip.getIndex()==5,"adjacent second group isolated");
        viewer.setCurrentIndex(1);check(strip.getCount()==3&&strip.getIndex()==1,"return restores original group");
    }
    private void beginFade(){
        GroupedPhotosListView strip=viewer.getGroupedPhotosListView();viewer.setCurrentIndex(4);
        check(!strip.hasPhotos()&&strip.getCount()==3,"old thumbnails retained during fade");
    }
    private void finishFadeAndReenter(){
        GroupedPhotosListView strip=viewer.getGroupedPhotosListView();
        check(strip.getCount()==0&&strip.getVisibility()==View.GONE,"fade completes and strip collapses");
        viewer.setCurrentIndex(1);check(strip.getCount()==3&&strip.getIndex()==1,"group reentry rebuilds strip");
    }
    private void touch(View v,int action,float x,float y,long down,long time){MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);v.dispatchTouchEvent(e);e.recycle();}
    private void doubleTap(View v,float x,float y){long t=SystemClock.uptimeMillis();touch(v,MotionEvent.ACTION_DOWN,x,y,t,t);touch(v,MotionEvent.ACTION_UP,x,y,t,t+35);touch(v,MotionEvent.ACTION_DOWN,x,y,t+100,t+100);touch(v,MotionEvent.ACTION_UP,x,y,t+100,t+135);}
    private void gestureSetup(){View container=(View)field(viewer,"containerView");doubleTap(container,container.getWidth()/2f,container.getHeight()/2f);}
    private void zoomCheckAndReset(){
        check((Float)field(viewer,"scale")>1f,"double tap zooms original photo");
        View container=(View)field(viewer,"containerView");doubleTap(container,container.getWidth()/2f,container.getHeight()/2f);
    }
    private void swipePage(){
        check(Math.abs((Float)field(viewer,"scale")-1f)<.1f,"double tap restores scale");
        View c=(View)field(viewer,"containerView");long t=SystemClock.uptimeMillis();float y=c.getHeight()/2f;
        touch(c,MotionEvent.ACTION_DOWN,c.getWidth()*.85f,y,t,t);
        touch(c,MotionEvent.ACTION_MOVE,c.getWidth()*.72f,y,t,t+50);
        touch(c,MotionEvent.ACTION_MOVE,c.getWidth()*.35f,y,t,t+110);
        touch(c,MotionEvent.ACTION_MOVE,c.getWidth()*.12f,y,t,t+160);
        touch(c,MotionEvent.ACTION_UP,c.getWidth()*.12f,y,t,t+200);
    }
    private void closeFirst(){
        check(viewer.getCurrentIndex()==2,"photo horizontal swipe pages");
        check(viewer.getGroupedPhotosListView().getIndex()==2,"strip follows viewer swipe");
        viewer.close(true);
    }
    private void forceAllChecks(){
        check(!viewer.isShowing()&&closed==1&&visibility.contains("2:true"),"close restores final source");
        open(true);viewer.setCurrentIndex(0);
        // setCurrentIndex is transition-gated; the initial page remains 1 until open completes.
        GroupedPhotosListView strip=viewer.getGroupedPhotosListView();check(strip.getCount()==7,"force-all includes adjacent gallery pages");
        viewer.close(false);
    }
    private void openLongGroup(){
        ArrayList<MediaViewer.Item> many=new ArrayList<>();
        for(int i=0;i<32;i++){MediaViewer.Item item=MediaViewer.Item.photo(new ColorDrawable(Color.BLUE),"S","D",null);item.groupId=700;item.width=100;item.height=100;many.add(item);}
        viewer=MediaViewer.open(activity,many,15,new MediaViewer.Provider(){});
    }
    private void longGroupChecks(){
        GroupedPhotosListView strip=viewer.getGroupedPhotosListView();Object snapshot=field(strip,"items");
        check(strip.getCount()==20&&strip.getIndex()==15,"long group uses bounded 20 item window");
        viewer.setCurrentIndex(16);
        check(strip.getIndex()==16&&field(strip,"items")==snapshot,"same group keeps strip snapshot while selected page remains in window");
        viewer.close(false);
    }
    private void openVideoGroup(){
        android.net.Uri uri=android.net.Uri.parse("android.resource://"+activity.getPackageName()+"/"+R.raw.sample_video);
        MediaViewer.Item photo=MediaViewer.Item.photo(new ColorDrawable(Color.BLUE),"S","D",null);photo.groupId=9;
        MediaViewer.Item video=MediaViewer.Item.video(uri,new ColorDrawable(Color.RED),"S","D",null);video.groupId=9;
        viewer=MediaViewer.open(activity,Arrays.asList(photo,video),0,new MediaViewer.Provider(){});
    }
    private void tapVideo(){
        GroupedPhotosListView strip=viewer.getGroupedPhotosListView();draw(strip);
        @SuppressWarnings("unchecked") List<Object> receivers=(List<Object>)field(strip,"imagesToDraw");
        @SuppressWarnings("unchecked") List<GroupedPhotosListView.Entry> entries=(List<GroupedPhotosListView.Entry>)field(strip,"currentPhotos");
        Object target=null;for(Object receiver:receivers){int local=(Integer)field(receiver,"param");if(local>=0&&local<entries.size()&&entries.get(local).index==1)target=receiver;}
        check(target!=null,"video thumbnail visible");
        float x=(Float)field(target,"imageX")+(Float)field(target,"imageWidth")/2f,y=(Float)field(target,"imageY")+(Float)field(target,"imageHeight")/2f;
        long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(t,t+35,MotionEvent.ACTION_UP,x,y,0);
        strip.onTouchEvent(down);strip.onTouchEvent(up);down.recycle();up.recycle();
        check(viewer.getCurrentIndex()==1,"video thumbnail changes page");
    }
    private void scrollVideo(){
        GroupedPhotosListView strip=viewer.getGroupedPhotosListView();draw(strip);
        long t=SystemClock.uptimeMillis();float y=strip.getHeight()/2f,start=strip.getWidth()/2f;
        MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,start,y,0);
        MotionEvent move1=MotionEvent.obtain(t,t+60,MotionEvent.ACTION_MOVE,start-AndroidUtilities.dp(18),y,0);
        MotionEvent move2=MotionEvent.obtain(t,t+120,MotionEvent.ACTION_MOVE,start-AndroidUtilities.dp(65),y,0);
        MotionEvent up=MotionEvent.obtain(t,t+170,MotionEvent.ACTION_UP,start-AndroidUtilities.dp(65),y,0);
        strip.onTouchEvent(down);strip.onTouchEvent(move1);strip.onTouchEvent(move2);strip.onTouchEvent(up);
        down.recycle();move1.recycle();move2.recycle();up.recycle();
        check(viewer.getCurrentIndex()==1,"scroll changes to video item");
    }
    private void awaitVideo(){
        boolean prepared=false;
        for(int i=0;i<120&&!prepared;i++){
            boolean[] ready={false};main(()->ready[0]=field(viewer,"player")!=null&&(Boolean)field(viewer,"playerPrepared"));
            prepared=ready[0];if(!prepared)SystemClock.sleep(50);
        }
        check(prepared,"tapping video thumbnail prepares local MediaPlayer");main(()->viewer.close(false));
    }

}
