package cn.local.golens;

import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.graphics.*;import android.hardware.display.*;import android.media.*;import android.media.projection.*;import android.os.*;import android.util.DisplayMetrics;import android.view.*;import android.widget.*;
import org.json.*;import java.nio.*;import java.util.*;import java.util.concurrent.*;

public class CaptureService extends Service {
    public static volatile boolean running;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService analysis=Executors.newSingleThreadExecutor();
    private HandlerThread captureThread;private Handler captureHandler;private MediaProjection projection;private VirtualDisplay display;private ImageReader reader;private WindowManager windows;
    private LinearLayout panel;private TextView label,title;private Button turnButton,pauseButton,compact;private View modal;private WindowManager.LayoutParams panelParams,compactParams;private boolean panelWasVisible;
    private Bitmap latest;private RectF boardRect;private BoardState board;private int[] candidate,observed;private int stable,revision,size,width,height,dpi,initialSide,speed;private double komi;
    private boolean paused,calibrating,previewing,busy,closed,desynced,pendingConfirmation;private volatile boolean frameQueued;private long lastFrame,lastValid;private int appliedRevision=-1,unsafeAt=-1,unsafeClearFrames;private JSONArray recommendations=new JSONArray();private LocalEngine engine;
    private long lastLocate;private int locateVotes,badFrames;private BoardLocator.Found locating;
    private MoveMarkers markers;
    private final Runnable collapsePanel=()->{if(panel!=null&&compact!=null&&panel.getVisibility()==View.VISIBLE&&!calibrating&&!previewing){panel.setVisibility(View.GONE);compact.setVisibility(View.VISIBLE);}};
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}if(running)return START_NOT_STICKY;
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("capture","棋眼屏幕辅助",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,CaptureService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"capture").setSmallIcon(cn.local.golens.R.drawable.ic_go).setContentTitle("棋眼正在本机识别棋盘").setContentText("屏幕不保存、不上传 · 点停止即可结束").addAction(new Notification.Action.Builder(null,"停止",stop).build()).setOngoing(true).build();
        startForeground(11,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        try{
            SharedPreferences p=getSharedPreferences("settings",0);size=p.getInt("size",19);initialSide=p.getInt("side",1);speed=p.getInt("speed",0);komi=Double.parseDouble(p.getString("komi","7.5"));board=new BoardState(size);engine=new LocalEngine(this);
            windows=getSystemService(WindowManager.class);DisplayMetrics metrics=new DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);width=metrics.widthPixels;height=metrics.heightPixels;dpi=metrics.densityDpi;
            captureThread=new HandlerThread("screen-frames");captureThread.start();captureHandler=new Handler(captureThread.getLooper());
            Intent data=intent.getParcelableExtra("data");projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("code",0),data);
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}@Override public void onCapturedContentResize(int w,int h){if(w!=width||h!=height){message("屏幕尺寸变化，请停止后重新启动");stopSelf();}}},ui);
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
            display=projection.createVirtualDisplay("GoLens",width,height,dpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,captureHandler);
            showPanel();running=true;ui.postDelayed(watchdog,1000);captureHandler.post(captureTick);
        }catch(Exception e){android.util.Log.e("GoLens","start",e);Toast.makeText(this,"无法启动："+e.getMessage(),Toast.LENGTH_LONG).show();stopSelf();}
        return START_NOT_STICKY;
    }
    private final Runnable watchdog=new Runnable(){public void run(){if(closed)return;if(boardRect!=null&&!paused&&!calibrating&&!previewing&&!pendingConfirmation&&SystemClock.elapsedRealtime()-lastValid>3500){invalidateAdvice();message("未看到清晰棋盘，等待画面恢复");}ui.postDelayed(this,1000);}};
    // Poll the newest buffer: a static screen need not produce another image callback.
    private final Runnable captureTick=new Runnable(){public void run(){if(closed)return;onImage(reader);if(!closed)captureHandler.postDelayed(this,650);}};
    private void onImage(ImageReader r){
        if(closed)return;
        try(Image image=r.acquireLatestImage()){
            if(image==null){ui.post(()->{if(!closed)processFrame();});return;}if(frameQueued)return;
            Image.Plane plane=image.getPlanes()[0];int padded=plane.getRowStride()/plane.getPixelStride();Bitmap raw=Bitmap.createBitmap(padded,height,Bitmap.Config.ARGB_8888);raw.copyPixelsFromBuffer(plane.getBuffer());Bitmap shot=Bitmap.createBitmap(raw,0,0,width,height);if(shot!=raw)raw.recycle();frameQueued=true;
            ui.post(()->{frameQueued=false;if(closed){shot.recycle();return;}if(calibrating||previewing){shot.recycle();return;}if(latest!=null)latest.recycle();latest=shot;processFrame();});
        }catch(Exception e){android.util.Log.w("GoLens","frame",e);}
    }
    private WindowManager.LayoutParams params(int w,int h,boolean touch){WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;if(!touch)p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;return p;}
    private Button small(String s,Runnable click){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);b.setBackground(MainActivity.bg(Color.rgb(39,89,77),dp(9)));b.setOnClickListener(v->click.run());return b;}
    private void row(Button... buttons){LinearLayout row=new LinearLayout(this);for(Button b:buttons){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(32),1);lp.setMargins(dp(2),dp(3),dp(2),0);row.addView(b,lp);}panel.addView(row);}
    private void showPanel(){markers=new MoveMarkers();WindowManager.LayoutParams markerParams=params(-1,-1,false);markerParams.alpha=.6f;windows.addView(markers,markerParams);panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(6),dp(4),dp(6),dp(5));panel.setBackground(MainActivity.bg(Color.argb(165,20,51,45),dp(10)));
        title=new TextView(this);title.setText("棋眼  ·  点此收起");title.setTextSize(11);title.setTextColor(Color.rgb(183,216,204));title.setPadding(0,dp(3),0,dp(3));panel.addView(title);
        label=new TextView(this);label.setTextColor(Color.WHITE);label.setTextSize(12);label.setMinHeight(dp(48));label.setText("打开腾讯围棋，正在自动寻找棋盘…");panel.addView(label);
        turnButton=small(initialSide==1?"轮到黑":"轮到白",this::switchTurn);pauseButton=small("暂停",()->{paused=!paused;invalidateAdvice();pauseButton.setText(paused?"继续":"暂停");message(paused?"已暂停分析":"等待稳定棋盘…");stable=0;});
        row(small("手动校准",this::calibrate),turnButton,pauseButton);
        row(small("棋盘图",this::preview),small("停一手",this::pass),small("关闭",this::stopSelf));
        panelParams=params(dp(200),-2,true);panelParams.alpha=.85f;panelParams.x=dp(12);panelParams.y=Math.max(dp(30),height-dp(180));windows.addView(panel,panelParams);
        title.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==0){x=e.getRawX();y=e.getRawY();px=panelParams.x;py=panelParams.y;return true;}if(e.getAction()==2){panelParams.x=Math.max(0,Math.min(width-panel.getWidth(),px+(int)(e.getRawX()-x)));panelParams.y=Math.max(dp(24),Math.min(height-panel.getHeight()-dp(24),py+(int)(e.getRawY()-y)));windows.updateViewLayout(panel,panelParams);return true;}if(e.getAction()==1&&Math.abs(e.getRawX()-x)<dp(8)&&Math.abs(e.getRawY()-y)<dp(8)){panel.setVisibility(View.GONE);compact.setVisibility(View.VISIBLE);}return true;}});
        compact=small("棋",()->{});compact.setTextSize(13);compactParams=params(dp(36),dp(36),true);compactParams.alpha=.5f;compactParams.x=width-dp(46);compactParams.y=Math.max(dp(24),height-dp(95));windows.addView(compact,compactParams);
        compact.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;boolean moved;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==0){x=e.getRawX();y=e.getRawY();px=compactParams.x;py=compactParams.y;moved=false;return true;}if(e.getAction()==2){if(Math.abs(e.getRawX()-x)>dp(8)||Math.abs(e.getRawY()-y)>dp(8))moved=true;if(moved){compactParams.x=Math.max(0,Math.min(width-dp(36),px+(int)(e.getRawX()-x)));compactParams.y=Math.max(dp(24),Math.min(height-dp(36),py+(int)(e.getRawY()-y)));windows.updateViewLayout(compact,compactParams);}return true;}if(e.getAction()==1){if(!moved){compact.setVisibility(View.GONE);panel.setVisibility(View.VISIBLE);ui.removeCallbacks(collapsePanel);ui.postDelayed(collapsePanel,8000);}return true;}return true;}});
        title.setOnClickListener(v->{ui.removeCallbacks(collapsePanel);panel.setVisibility(View.GONE);compact.setVisibility(View.VISIBLE);});
        panel.setVisibility(View.GONE);
    }
    private void message(String s){if(label!=null)label.setText(s);if(compact!=null){compact.setText(appliedRevision==revision?"①":"棋");compact.setContentDescription("棋眼，"+s);}if(markers!=null)markers.invalidate();}
    private void invalidateAdvice(){revision++;appliedRevision=-1;recommendations=new JSONArray();if(markers!=null)markers.invalidate();}
    private BoardVision.Pixels screenPixels(){Bitmap frame=latest;return new BoardVision.Pixels(){public int width(){return frame.getWidth();}public int height(){return frame.getHeight();}public int get(int x,int y){return frame.getPixel(x,y);}};}
    private int adviceConflict(JSONArray choices,BoardVision.Pixels pixels){
        if(choices==null||boardRect==null)return -1;
        for(int i=0;i<Math.min(3,choices.length());i++){
            JSONObject choice=choices.optJSONObject(i);if(choice==null)continue;
            int at=board.index(choice.optString("move"));
            if(at>=0&&(board.cells[at]!=0||BoardVision.visuallyOccupied(pixels,boardRect.left,boardRect.top,boardRect.right,boardRect.bottom,size,at)))return at;
        }
        return -1;
    }
    private void blockUnsafeAdvice(int at){unsafeAt=at;unsafeClearFrames=0;invalidateAdvice();message("建议点疑似已有棋子，已撤下标点；等待画面重新确认…");}
    private void processFrame(){
        if(markers!=null)markers.invalidate();
        if(paused||calibrating||previewing||latest==null)return;
        if(boardRect==null){autoLocate();return;}
        int[] loc=new int[2];panel.getLocationOnScreen(loc);RectF cover=new RectF(loc[0],loc[1],loc[0]+panel.getWidth(),loc[1]+panel.getHeight());
        if(panel.getVisibility()==View.VISIBLE&&RectF.intersects(cover,boardRect)){panel.setVisibility(View.GONE);compact.setVisibility(View.VISIBLE);invalidateAdvice();stable=0;message("菜单已自动收起，避免遮住棋盘");return;}
        BoardVision.Pixels pixels=screenPixels();int shownTurn=TencentUi.turn(pixels,boardRect.left,boardRect.top,boardRect.right);
        int[] excluded=panel.getVisibility()==View.VISIBLE?new int[]{loc[0],loc[1],loc[0]+panel.getWidth(),loc[1]+panel.getHeight()}:null;
        if(TencentUi.confirming(pixels,boardRect.left,boardRect.right,boardRect.bottom,excluded)){if(!pendingConfirmation){pendingConfirmation=true;invalidateAdvice();}message("等待腾讯围棋确认落子…");return;}
        if(pendingConfirmation){pendingConfirmation=false;candidate=null;stable=0;}
        BoardVision.Result result=BoardVision.read(latest,boardRect,size);
        if(result.confidence<.97){invalidateAdvice();stable=0;badFrames++;message("正在重新定位棋盘，请保持画面清晰");if(badFrames>=2)autoLocate();return;}
        if(BoardVision.firstMissedStone(pixels,boardRect.left,boardRect.top,boardRect.right,boardRect.bottom,size,result.cells)>=0){invalidateAdvice();stable=0;badFrames++;message("发现疑似漏认棋子，暂停建议并重新定位…");if(badFrames>=2)autoLocate();return;}
        badFrames=0;lastValid=SystemClock.elapsedRealtime();observed=result.cells;
        if(panel.getVisibility()==View.VISIBLE&&!Arrays.equals(board.cells,observed)){ui.removeCallbacks(collapsePanel);panel.setVisibility(View.GONE);compact.setVisibility(View.VISIBLE);candidate=null;stable=0;invalidateAdvice();message("棋盘变化，面板已收起，等待落子确认…");return;}
        if(!Arrays.equals(candidate,observed)){candidate=observed.clone();stable=1;unsafeAt=-1;unsafeClearFrames=0;invalidateAdvice();message("棋盘变化，确认落子中…");return;}stable++;
        if(stable<2)return;
        if(unsafeAt>=0){
            if(BoardVision.visuallyOccupied(pixels,boardRect.left,boardRect.top,boardRect.right,boardRect.bottom,size,unsafeAt)){unsafeClearFrames=0;message("建议点疑似已有棋子，已暂停标点；等待棋盘变化…");return;}
            if(++unsafeClearFrames<3){message("正在复核建议点…");return;}
            unsafeAt=-1;unsafeClearFrames=0;
        }
        if(desynced){if(shownTurn!=0){board.reset(observed,shownTurn);desynced=false;invalidateAdvice();message("已根据计时框重新同步局面");}else if(recoverSkipped(observed)){desynced=false;invalidateAdvice();}else{message("局面未同步，点“校准”重新确认\n当前轮次可用“轮到黑/白”切换");return;}}
        if(board.initial==null)return;
        if(!Arrays.equals(board.cells,observed)){
            if(shownTurn!=0&&shownTurn==board.next){if(!recoverSkipped(observed)){if(stable<4){invalidateAdvice();message("等待落子确认和轮次切换…");return;}board.reset(observed,shownTurn);message("已按计时框重新接续当前局面");}}
            else if(!board.accept(observed)&&!recoverTurn(observed)&&!recoverSkipped(observed)){
                if(shownTurn==0){invalidateAdvice();desynced=true;message("棋盘变化不符合单步落子，请重新确认局面");return;}
                board.reset(observed,shownTurn);message("已根据计时框重建当前局面");
            }
            revision++;recommendations=new JSONArray();appliedRevision=-1;turnButton.setText(board.next==1?"轮到黑":"轮到白");
        }
        if(shownTurn!=0&&board.next!=shownTurn){if(Arrays.equals(board.cells,observed)&&board.moves.size()>0)board.pass();else board.reset(observed,shownTurn);invalidateAdvice();turnButton.setText(shownTurn==1?"轮到黑":"轮到白");}
        int playerColor=TencentUi.playerColor(pixels,boardRect.left,boardRect.top,boardRect.right);
        if(shownTurn!=0&&playerColor!=0&&shownTurn!=playerColor){if(appliedRevision!=-1)invalidateAdvice();message("等待对手落子…");return;}
        if(appliedRevision==revision){int conflict=adviceConflict(recommendations,pixels);if(conflict>=0){blockUnsafeAdvice(conflict);return;}}
        if(!busy&&appliedRevision!=revision)analyze();
    }
    private boolean recoverTurn(int[] seen){
        if(board.moves.isEmpty()){BoardState corrected=new BoardState(size);corrected.reset(board.cells,3-board.next);if(corrected.accept(seen)){board=corrected;initialSide=corrected.initialPlayer;message("已根据新棋子自动修正黑白轮次");return true;}}
        if(board.acceptAfterPass(seen)){message("已检测到可能停一手，继续观察");return true;}
        return false;
    }
    private boolean recoverSkipped(int[] seen){if(board.recoverSkippedNoCaptures(seen)){message("已接续漏看的几手，继续分析");return true;}if(board.recoverSkippedWithCaptures(seen)){message("已接续漏看的提子变化，继续分析");return true;}return false;}
    private int inferredSide(int[] seen){int black=0,white=0;for(int stone:seen){if(stone==1)black++;else if(stone==2)white++;}if(black==white)return 1;if(black==white+1)return 2;return initialSide;}
    private void autoLocate(){
        long now=SystemClock.elapsedRealtime();if(now-lastLocate<1200)return;lastLocate=now;
        int w=latest.getWidth(),h=latest.getHeight();int[] argb=new int[w*h];latest.getPixels(argb,0,w,0,0,w,h);
        BoardVision.Pixels pixels=new BoardVision.Pixels(){public int width(){return w;}public int height(){return h;}public int get(int x,int y){return argb[y*w+x];}};
        BoardLocator.Found found=BoardLocator.locate(pixels);
        if(found==null){locating=null;locateVotes=0;message("正在寻找棋盘…\n请完整显示木色棋盘，移开遮挡");return;}
        if(locating!=null&&found.size==locating.size&&Math.abs(found.left-locating.left)<5&&Math.abs(found.top-locating.top)<5&&Math.abs(found.right-locating.right)<5&&Math.abs(found.bottom-locating.bottom)<5)locateVotes++;else locateVotes=1;
        locating=found;if(locateVotes<2){message("已发现 "+found.size+" 路棋盘，自动校准中…");return;}
        int[] seen=BoardVision.read(pixels,found.left,found.top,found.right,found.bottom,found.size).cells;
        boolean first=boardRect==null;boolean keep=!first&&size==found.size&&(Arrays.equals(board.cells,seen)||board.accept(seen)||recoverSkipped(seen));
        boolean empty=true;for(int stone:seen)if(stone!=0){empty=false;break;}
        int priorSide=board.next;int shownTurn=TencentUi.turn(pixels,found.left,found.top,found.right);size=found.size;if(!keep){board=new BoardState(size);board.reset(seen,shownTurn!=0?shownTurn:(empty?1:(first?inferredSide(seen):priorSide)));}
        boardRect=new RectF(found.left,found.top,found.right,found.bottom);candidate=seen.clone();observed=seen.clone();stable=0;badFrames=0;desynced=!first&&!keep&&!empty;unsafeAt=-1;unsafeClearFrames=0;revision++;appliedRevision=-1;recommendations=new JSONArray();lastValid=now;locateVotes=0;
        turnButton.setText(board.next==1?"轮到黑":"轮到白");message(desynced?"已重新定位，但落子历史不连续\n请确认当前轮次后继续":"已自动校准 "+size+" 路棋盘\n"+(board.next==1?"黑棋":"白棋")+"行棋，准备分析");
    }
    private void analyze(){
        final int epoch=revision;final long start=SystemClock.elapsedRealtime();busy=true;message((board.next==1?"黑棋":"白棋")+"本地分析中…");
        try{JSONObject query=board.query("screen-"+epoch+"-"+start,komi,new int[]{64,200,600}[speed],new double[]{1.2,3,8}[speed]);
            analysis.execute(()->{try{JSONObject answer=engine.analyze(query);ui.post(()->{busy=false;if(closed||epoch!=revision||paused||calibrating||previewing||desynced||SystemClock.elapsedRealtime()-lastValid>2500)return;JSONArray choices=answer.optJSONArray("moveInfos");if(choices==null||choices.length()==0){message("此局面没有可用建议");return;}int conflict=adviceConflict(choices,screenPixels());if(conflict>=0){blockUnsafeAdvice(conflict);return;}recommendations=choices;appliedRevision=revision;try{String move=recommendations.getJSONObject(0).getString("move");int index=board.index(move);String where=index>=0?"左起 "+(index%size+1)+" 列 · 上起 "+(index/size+1)+" 行":"建议停一手";String alternate="";for(int i=1;i<Math.min(3,recommendations.length());i++)alternate+="  "+recommendations.getJSONObject(i).getString("move");message((board.next==1?"黑":"白")+"棋建议 "+move+" · "+String.format(java.util.Locale.ROOT,"%.1f",(SystemClock.elapsedRealtime()-start)/1000.0)+"秒\n"+where+(alternate.isEmpty()?"":"\n备选"+alternate));}catch(Exception e){message("建议解析失败，请重试");}});}catch(Exception e){ui.post(()->{busy=false;if(!closed&&epoch==revision){paused=true;pauseButton.setText("继续");message("分析失败："+e.getMessage());}});}});
        }catch(Exception e){busy=false;message("无法分析："+e.getMessage());}
    }
    private void switchTurn(){revision++;appliedRevision=-1;recommendations=new JSONArray();unsafeAt=-1;unsafeClearFrames=0;initialSide=3-(boardRect==null?initialSide:board.next);if(boardRect!=null)board.reset(board.cells,initialSide);desynced=false;turnButton.setText(initialSide==1?"轮到黑":"轮到白");message("已切换轮次，历史已重置，请确认棋盘");stable=0;}
    private void pass(){if(boardRect==null||desynced){message("请先校准棋盘");return;}board.pass();revision++;appliedRevision=-1;recommendations=new JSONArray();unsafeAt=-1;unsafeClearFrames=0;turnButton.setText(board.next==1?"轮到黑":"轮到白");message("已记录停一手，等待分析");}
    private void calibrate(){if(latest==null){message("等待屏幕画面，请稍后再试");return;}if(calibrating||previewing)return;calibrating=true;revision++;appliedRevision=-1;recommendations=new JSONArray();if(markers!=null)markers.invalidate();panelWasVisible=panel.getVisibility()==View.VISIBLE;panel.setVisibility(View.GONE);compact.setVisibility(View.GONE);CalibrationView view=new CalibrationView();modal=view;windows.addView(view,params(-1,-1,true));}
    private void finishCalibration(RectF rect,int[] cells){boardRect=new RectF(rect);board.reset(cells,initialSide);observed=cells.clone();candidate=cells.clone();stable=0;desynced=false;unsafeAt=-1;unsafeClearFrames=0;lastValid=SystemClock.elapsedRealtime();turnButton.setText(initialSide==1?"轮到黑":"轮到白");closeModal();message("已校准，等待稳定画面…");}
    private void closeModal(){if(modal!=null){windows.removeView(modal);modal=null;}calibrating=false;previewing=false;panel.setVisibility(panelWasVisible?View.VISIBLE:View.GONE);compact.setVisibility(panelWasVisible?View.GONE:View.VISIBLE);stable=0;candidate=null;lastValid=0;}
    private void preview(){if(boardRect==null){message("请先校准棋盘");return;}previewing=true;revision++;appliedRevision=-1;if(markers!=null)markers.invalidate();panelWasVisible=panel.getVisibility()==View.VISIBLE;panel.setVisibility(View.GONE);compact.setVisibility(View.GONE);modal=new PreviewView();windows.addView(modal,params(-1,-1,true));}
    /** Ring samples stay outside the stone classifier's inner sampling region. */
    class MoveMarkers extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        MoveMarkers(){super(CaptureService.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            if(boardRect==null||paused||calibrating||previewing||desynced||closed||appliedRevision!=revision||SystemClock.elapsedRealtime()-lastValid>2500)return;
            int[] origin=new int[2];getLocationOnScreen(origin);float dx=boardRect.width()/(size-1),dy=boardRect.height()/(size-1),step=Math.min(dx,dy);
            for(int i=0;i<Math.min(3,recommendations.length());i++){
                JSONObject move=recommendations.optJSONObject(i);if(move==null)continue;int at=board.index(move.optString("move"));if(at<0||at>=board.cells.length||board.cells[at]!=0)continue;
                if(latest==null||BoardVision.visuallyOccupied(screenPixels(),boardRect.left,boardRect.top,boardRect.right,boardRect.bottom,size,at))continue;
                float x=boardRect.left+(at%size)*dx-origin[0],y=boardRect.top+(at/size)*dy-origin[1];
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,step*.07f));p.setColor(i==0?Color.rgb(0,220,105):Color.rgb(255,133,0));canvas.drawCircle(x,y,step*.44f,p);
                p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(step*.36f);canvas.drawText(Integer.toString(i+1),x,y-(p.ascent()+p.descent())/2,p);
            }
        }
    }
    class CalibrationView extends View {
        Paint paint=new Paint(3);PointF first;RectF selected;BoardVision.Result detected;String hint="点击棋盘左上角的交叉点";
        CalibrationView(){super(CaptureService.this);}
        protected void onDraw(Canvas c){c.drawBitmap(latest,0,0,paint);if(first!=null){paint.setColor(Color.CYAN);paint.setStrokeWidth(3);c.drawLine(first.x-22,first.y,first.x+22,first.y,paint);c.drawLine(first.x,first.y-22,first.x,first.y+22,paint);}if(selected!=null){paint.setColor(Color.CYAN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);c.drawRect(selected,paint);paint.setStyle(Paint.Style.FILL);for(int y=0;y<size;y++)for(int x=0;x<size;x++){paint.setColor(detected!=null&&detected.cells[y*size+x]!=0?Color.MAGENTA:Color.CYAN);c.drawCircle(selected.left+x*selected.width()/(size-1),selected.top+y*selected.height()/(size-1),3,paint);}}
            paint.setColor(Color.rgb(20,51,45));c.drawRect(0,height-dp(165),width,height,paint);paint.setColor(Color.WHITE);paint.setTextSize(dp(16));c.drawText(hint,dp(16),height-dp(133),paint);paint.setTextSize(dp(12));c.drawText("选交叉点，不是木质边框 · "+size+"路 · "+(initialSide==1?"黑棋先下":"白棋先下"),dp(16),height-dp(105),paint);c.drawText("彩色点应与所有交叉点重合；棋子用紫点标记",dp(16),height-dp(83),paint);paint.setTextSize(dp(16));c.drawText("取消",dp(20),height-dp(43),paint);c.drawText("重选",width/2-dp(18),height-dp(43),paint);c.drawText("确认",width-dp(62),height-dp(43),paint);
        }
        public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()!=1)return true;float x=e.getRawX(),y=e.getRawY();
            if(y>height-dp(75)){if(x<width/3){closeModal();message("已取消校准");}else if(x<width*2/3){first=null;selected=null;detected=null;hint="点击棋盘左上角的交叉点";invalidate();}else if(selected!=null&&detected!=null&&detected.confidence>=.97)finishCalibration(selected,detected.cells);return true;}
            if(y>height-dp(170))return true;
            if(first==null){first=new PointF(x,y);hint="再点棋盘右下角的交叉点";}
            else{RectF rect=new RectF(first.x,first.y,x,y);float ratio=rect.width()/Math.max(1,rect.height());if(rect.width()<size*8||rect.height()<size*8||ratio<.85||ratio>1.15){hint="位置不正确，点重选后重新选择";}else{selected=rect;detected=BoardVision.read(latest,rect,size);hint=detected.confidence>=.97?"检查彩色点对齐后，点击确认":"识别不清晰，请重选或换清晰棋盘";}}
            invalidate();return true;}
    }
    class PreviewView extends View {Paint p=new Paint(3);PreviewView(){super(CaptureService.this);}
        protected void onDraw(Canvas c){c.drawColor(MainActivity.PAPER);float edge=width-dp(48),l=dp(24),t=Math.max(dp(100),(height-edge)/2),s=edge/(size-1);p.setTextSize(dp(22));p.setColor(MainActivity.INK);c.drawText("识别棋盘 · "+(board.next==1?"黑棋行棋":"白棋行棋"),dp(24),t-dp(40),p);p.setColor(Color.rgb(212,177,118));c.drawRect(l-dp(10),t-dp(10),l+edge+dp(10),t+edge+dp(10),p);p.setColor(Color.rgb(85,67,39));p.setStrokeWidth(1.5f);for(int i=0;i<size;i++){c.drawLine(l+i*s,t,l+i*s,t+edge,p);c.drawLine(l,t+i*s,l+edge,t+i*s,p);}for(int i=0;i<board.cells.length;i++)if(board.cells[i]!=0){p.setColor(board.cells[i]==1?Color.rgb(25,25,25):Color.WHITE);c.drawCircle(l+i%size*s,t+i/size*s,s*.43f,p);}for(int i=0;i<Math.min(3,recommendations.length());i++){int idx=board.index(recommendations.optJSONObject(i).optString("move"));if(idx>=0){p.setColor(i==0?MainActivity.TEAL:Color.rgb(172,91,28));c.drawCircle(l+idx%size*s,t+idx/size*s,s*.40f,p);p.setColor(Color.WHITE);p.setTextSize(s*.55f);p.setTextAlign(Paint.Align.CENTER);c.drawText(Integer.toString(i+1),l+idx%size*s,t+idx/size*s+s*.18f,p);p.setTextAlign(Paint.Align.LEFT);}}p.setTextSize(dp(15));p.setColor(MainActivity.INK);c.drawText("1 为首选，2 / 3 为备选",dp(24),t+edge+dp(50),p);c.drawText("点任意位置返回并继续观察",dp(24),t+edge+dp(80),p);}
        public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==1){recommendations=new JSONArray();closeModal();message("等待棋盘恢复…");}return true;}
    }
    @Override public void onDestroy(){closed=true;running=false;revision++;ui.removeCallbacks(watchdog);ui.removeCallbacks(collapsePanel);if(engine!=null)engine.close();analysis.shutdownNow();if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(captureThread!=null)captureThread.quitSafely();if(windows!=null){if(modal!=null)windows.removeView(modal);if(panel!=null)windows.removeView(panel);if(compact!=null)windows.removeView(compact);if(markers!=null)windows.removeView(markers);}if(latest!=null)latest.recycle();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}

