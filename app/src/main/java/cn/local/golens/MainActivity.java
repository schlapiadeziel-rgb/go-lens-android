package cn.local.golens;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    static final int INK=Color.rgb(22,53,48),TEAL=Color.rgb(30,109,90),PAPER=Color.rgb(246,243,235);
    Spinner size,side,speed;EditText komi;TextView status;
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    static GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(INK);t.setPadding(0,dp(7),0,dp(7));return t;}
    Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackground(bg(TEAL,dp(14)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(12);b.setLayoutParams(p);b.setOnClickListener(v->r.run());return b;}
    Spinner choice(LinearLayout box,String label,String[] values,int selected){box.addView(text(label,14));Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));s.setSelection(selected);box.addView(s,new LinearLayout.LayoutParams(-1,dp(44)));return s;}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(PAPER);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(22),dp(24),dp(28));scroll.addView(root);setContentView(scroll);
        root.addView(text("GO LENS   /   完全离线",12));TextView title=text("棋眼",38);title.setTypeface(null,Typeface.BOLD);root.addView(title);root.addView(text("看清棋局，再落一子。",20));root.addView(text("自动观察棋盘，在悬浮窗显示下一步建议。模型已内置，无需账号或电脑。",15));
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
    }
    boolean save(){double k;try{k=Double.parseDouble(komi.getText().toString());if(!Double.isFinite(k)||Math.abs(k)>100)throw new Exception();}catch(Exception e){status.setText("请填写有效贴目，例如 7.5");return false;}
        getSharedPreferences("settings",0).edit().putInt("sizeIndex",size.getSelectedItemPosition()).putInt("size",new int[]{19,13,9}[size.getSelectedItemPosition()]).putInt("side",side.getSelectedItemPosition()+1).putInt("speed",speed.getSelectedItemPosition()).putString("komi",Double.toString(k)).apply();return true;}
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
