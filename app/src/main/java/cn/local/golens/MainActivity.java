package cn.local.golens;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.database.Cursor;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    static final int INK=Color.rgb(22,53,48),TEAL=Color.rgb(30,109,90),PAPER=Color.rgb(246,243,235);
    Spinner size,side,speed;EditText komi;TextView status;
    private long updateDownloadId=-1;
    private boolean waitingForInstallPermission,installerOpened;
    private final Handler updateHandler=new Handler(Looper.getMainLooper());
    private final Runnable updatePoll=this::pollUpdateDownload;
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    static GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(INK);t.setPadding(0,dp(7),0,dp(7));return t;}
    Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackground(bg(TEAL,dp(14)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(12);b.setLayoutParams(p);b.setOnClickListener(v->r.run());return b;}
    Spinner choice(LinearLayout box,String label,String[] values,int selected){box.addView(text(label,14));Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));s.setSelection(selected);box.addView(s,new LinearLayout.LayoutParams(-1,dp(44)));return s;}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(PAPER);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(22),dp(24),dp(28));scroll.addView(root);setContentView(scroll);
        root.addView(text("GO LENS   /   棋局分析完全离线",12));TextView title=text("棋眼",38);title.setTypeface(null,Typeface.BOLD);root.addView(title);root.addView(text("看清棋局，再落一子。",20));root.addView(text("自动观察棋盘，在悬浮窗显示下一步建议。模型已内置，无需账号或电脑。",15));
        TextView updates=text("检查更新 · GitHub（仅此操作联网）",15);updates.setTextColor(TEAL);updates.setPaintFlags(updates.getPaintFlags()|android.graphics.Paint.UNDERLINE_TEXT_FLAG);updates.setOnClickListener(v->showUpdates());root.addView(updates);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(12),dp(16),dp(16));card.setBackground(bg(Color.WHITE,dp(20)));root.addView(card);
        android.content.SharedPreferences prefs=getSharedPreferences("settings",0);
        size=choice(card,"棋盘大小（屏幕模式自动识别）",new String[]{"19 路","13 路","9 路"},prefs.getInt("sizeIndex",0));
        side=choice(card,"开始识别时，轮到谁下？",new String[]{"黑棋","白棋"},prefs.getInt("side",1)-1);
        speed=choice(card,"分析时间",new String[]{"快速 · b10 模型 · 约 1 秒","均衡 · b15 模型 · 约 3 秒","深入 · b15 模型 · 约 8 秒"},prefs.getInt("speed",0));
        card.addView(text("贴目（中国规则）",14));komi=new EditText(this);komi.setSingleLine(true);komi.setInputType(8194);komi.setText(prefs.getString("komi","7.5"));card.addView(komi);
        root.addView(button("开始屏幕辅助",this::begin));root.addView(button("先试试离线分析",()->{if(save())startActivity(new Intent(this,DemoActivity.class));}));
        root.addView(button("停止辅助",()->{stopService(new Intent(this,CaptureService.class));status.setText("已停止屏幕读取和分析");}));
        status=text("首次使用：允许悬浮窗 → 授权共享整个屏幕 → 打开棋盘。自动寻找棋盘、识别路数并校准位置。",14);root.addView(status);
        root.addView(text("屏幕仅在本机处理，不保存、不上传。停止共享或锁屏会结束读取。请在允许辅助的练习场景使用。",12));
        TextView licenses=text("开源组件与说明",12);licenses.setOnClickListener(v->{try(java.io.InputStream in=getAssets().open("NOTICE.txt")){new AlertDialog.Builder(this).setTitle("开源组件").setMessage(LocalEngine.readText(in)).setPositiveButton("关闭",null).show();}catch(Exception e){}});root.addView(licenses);
        updateDownloadId=getSharedPreferences("update",0).getLong("id",-1);
        String pendingTag=getSharedPreferences("update",0).getString("tag","");
        if(updateDownloadId>0){try{if(UpdateChecker.compareVersions(installedVersion(),pendingTag)>=0)clearUpdateDownload();}catch(Exception e){clearUpdateDownload();}}
    }
    String installedVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "0.0.0";}}
    boolean save(){double k;try{k=Double.parseDouble(komi.getText().toString());if(!Double.isFinite(k)||Math.abs(k)>100)throw new Exception();}catch(Exception e){status.setText("请填写有效贴目，例如 7.5");return false;}
        getSharedPreferences("settings",0).edit().putInt("sizeIndex",size.getSelectedItemPosition()).putInt("size",new int[]{19,13,9}[size.getSelectedItemPosition()]).putInt("side",side.getSelectedItemPosition()+1).putInt("speed",speed.getSelectedItemPosition()).putString("komi",Double.toString(k)).apply();return true;}
    void showUpdates(){
        if(updateDownloadId>0){
            DownloadManager manager=getSystemService(DownloadManager.class);
            try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(updateDownloadId))){
                if(cursor!=null&&cursor.moveToFirst()){
                    int state=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if(state==DownloadManager.STATUS_SUCCESSFUL){new AlertDialog.Builder(this).setTitle("更新已下载").setMessage("现在打开 Android 安装界面？")
                        .setNegativeButton("稍后",null).setPositiveButton("安装",(dialog,which)->installDownloadedUpdate()).show();return;}
                    if(state!=DownloadManager.STATUS_FAILED){pollUpdateDownload();return;}
                }
            }catch(Exception ignored){}
            clearUpdateDownload();
        }
        final String current=installedVersion();status.setText("正在向 GitHub 检查更新…");
        new Thread(()->{
            try{
                UpdateChecker.Release release=UpdateChecker.fetchLatest();
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    boolean newer=UpdateChecker.compareVersions(release.tag,current)>0;
                    status.setText(newer?"发现新版 "+release.tag:"当前已是最新版本 "+current);
                    String amount=release.size>0?String.format(java.util.Locale.ROOT,"（约 %.1f MB）",release.size/1048576.0):"";
                    new AlertDialog.Builder(this).setTitle(newer?"发现新版 "+release.tag:"已是最新版本")
                        .setMessage(newer?"当前 "+current+" → "+release.tag+"。将在棋眼中下载 APK"+amount+"，下载完成后打开 Android 安装界面。":"当前版本："+current+"。棋局分析继续完全离线。")
                        .setNegativeButton("关闭",null)
                        .setPositiveButton(newer&&release.downloadUrl!=null?"下载并安装":"查看发布页",(dialog,which)->{if(newer&&release.downloadUrl!=null)startUpdateDownload(release);else openUpdatePage(release.htmlUrl);}).show();
                });
            }catch(Exception e){runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                status.setText("更新检查失败，可打开发布页手动查看");
                new AlertDialog.Builder(this).setTitle("无法检查更新").setMessage("请确认网络可用，或直接查看 GitHub 发布页。")
                    .setNegativeButton("关闭",null).setPositiveButton("打开发布页",(dialog,which)->openUpdatePage(UpdateChecker.RELEASES_PAGE)).show();
            });}
        },"release-check").start();
    }
    void openUpdatePage(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){status.setText("没有可打开 GitHub 的浏览器");}}
    void startUpdateDownload(UpdateChecker.Release release){
        try{
            DownloadManager manager=getSystemService(DownloadManager.class);
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(release.downloadUrl));
            request.setTitle("棋眼 "+release.tag+" 更新");request.setDescription("下载完成后由系统确认安装");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,"GoLens-"+release.tag+"-"+System.currentTimeMillis()+".apk");
            updateDownloadId=manager.enqueue(request);installerOpened=false;
            getSharedPreferences("update",0).edit().putLong("id",updateDownloadId).putString("tag",release.tag).apply();
            pollUpdateDownload();
        }catch(Exception e){status.setText("无法开始下载，请检查网络或存储空间");}
    }
    void pollUpdateDownload(){
        updateHandler.removeCallbacks(updatePoll);if(updateDownloadId<0||isFinishing()||isDestroyed())return;
        DownloadManager manager=getSystemService(DownloadManager.class);
        try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(updateDownloadId))){
            if(cursor==null||!cursor.moveToFirst()){status.setText("更新下载记录已失效，请重新检查");clearUpdateDownload();return;}
            int state=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if(state==DownloadManager.STATUS_SUCCESSFUL){status.setText("更新已下载，准备安装…");if(!installerOpened&&!waitingForInstallPermission)installDownloadedUpdate();return;}
            if(state==DownloadManager.STATUS_FAILED){status.setText("更新下载失败，请重新检查");clearUpdateDownload();return;}
            long received=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            status.setText(total>0?"正在下载更新："+(received*100/total)+"%":"正在下载更新…");
            updateHandler.postDelayed(updatePoll,1000);
        }catch(Exception e){status.setText("无法读取下载进度，请稍后重试");}
    }
    void installDownloadedUpdate(){
        if(updateDownloadId<0)return;
        if(!getPackageManager().canRequestPackageInstalls()){
            waitingForInstallPermission=true;status.setText("请允许棋眼安装此来源的应用，返回后继续安装");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;
        }
        Uri apk=getSystemService(DownloadManager.class).getUriForDownloadedFile(updateDownloadId);
        if(apk==null){status.setText("无法打开已下载 APK，请重新检查更新");clearUpdateDownload();return;}
        installerOpened=true;
        try{Intent install=new Intent(Intent.ACTION_VIEW).setDataAndType(apk,"application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(install);}
        catch(ActivityNotFoundException e){installerOpened=false;status.setText("手机没有可用的 APK 安装程序");}
    }
    void clearUpdateDownload(){
        updateHandler.removeCallbacks(updatePoll);
        if(updateDownloadId>0)try{getSystemService(DownloadManager.class).remove(updateDownloadId);}catch(Exception ignored){}
        updateDownloadId=-1;installerOpened=false;waitingForInstallPermission=false;getSharedPreferences("update",0).edit().clear().apply();
    }
    @Override protected void onResume(){super.onResume();if(waitingForInstallPermission&&getPackageManager().canRequestPackageInstalls()){
        waitingForInstallPermission=false;installDownloadedUpdate();
    }else if(updateDownloadId>0&&!installerOpened&&!waitingForInstallPermission)updateHandler.post(updatePoll);}
    @Override protected void onPause(){updateHandler.removeCallbacks(updatePoll);super.onPause();}
    void begin(){if(!save())return;if(CaptureService.running){status.setText("辅助已在运行，请使用悬浮窗；更改设置需停止后重启。");return;}
        if(!Settings.canDrawOverlays(this)){status.setText("请允许棋眼显示悬浮窗，再返回点击开始。");startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},8);return;}
        requestProjection();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==8)requestProjection();}
    private void requestProjection(){
        MediaProjectionManager mgr=getSystemService(MediaProjectionManager.class);Intent request=Build.VERSION.SDK_INT>=34?mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):mgr.createScreenCaptureIntent();startActivityForResult(request,7);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==7){if(result!=RESULT_OK||data==null){status.setText("未授权屏幕共享，辅助未启动。");return;}Intent service=new Intent(this,CaptureService.class).putExtra("code",result).putExtra("data",data);startForegroundService(service);status.setText("辅助已启动。切换到腾讯围棋，将自动识别并校准棋盘。");moveTaskToBack(true);}}
}
