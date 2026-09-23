package cn.local.golens;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** A single persistent, offline KataGo process. No shell and no network. */
public final class LocalEngine implements AutoCloseable {
    private final Context context;
    private volatile Process process;
    private BufferedWriter input;
    private final BlockingQueue<String> output=new LinkedBlockingQueue<>(32);
    private volatile boolean closed;
    private volatile String diagnostic="";
    public LocalEngine(Context c){context=c.getApplicationContext();}
    private void asset(String name)throws IOException{
        File f=new File(context.getFilesDir(),name);
        if(f.exists()&&f.length()>0)return;
        File tmp=new File(context.getFilesDir(),name+".tmp");
        try(InputStream in=context.getAssets().open(name.equals("model.txt.gz")?"model.data":name);OutputStream out=new FileOutputStream(tmp)){byte[] buffer=new byte[16384];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}
        if(!tmp.renameTo(f))throw new IOException("无法准备本地模型");
    }
    private synchronized void start()throws IOException{
        if(closed)throw new IOException("分析已停止");
        if(process!=null&&process.isAlive())return;
        asset("model.txt.gz");asset("analysis.cfg");output.clear();
        File dir=context.getFilesDir();
        Process p=new ProcessBuilder(context.getApplicationInfo().nativeLibraryDir+"/libkatago.so","analysis","-model",new File(dir,"model.txt.gz").getPath(),"-config",new File(dir,"analysis.cfg").getPath()).directory(dir).start();
        process=p;input=new BufferedWriter(new OutputStreamWriter(p.getOutputStream(),StandardCharsets.UTF_8));
        Thread stdout=new Thread(()->{try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){String s;while((s=r.readLine())!=null)output.offer(s);}catch(IOException ignored){}finally{output.offer("{\"error\":\"本地引擎已退出\"}");}},"katago-output");stdout.setDaemon(true);stdout.start();
        Thread stderr=new Thread(()->{try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getErrorStream(),StandardCharsets.UTF_8))){String s;while((s=r.readLine())!=null){diagnostic=s;android.util.Log.i("GoLensEngine",s);}}catch(IOException ignored){}},"katago-log");stderr.setDaemon(true);stderr.start();
    }
    public JSONObject analyze(JSONObject query)throws Exception{
        start();input.write(query.toString());input.newLine();input.flush();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
        while(!closed&&System.nanoTime()<end){String line=output.poll(1,TimeUnit.SECONDS);if(line==null)continue;JSONObject answer;
            try{answer=new JSONObject(line);}catch(JSONException e){continue;}
            if(answer.has("error"))throw new IOException(answer.optString("error")+" "+diagnostic);
            if(query.getString("id").equals(answer.optString("id"))&&!answer.optBoolean("isDuringSearch",false)&&answer.has("moveInfos"))return answer;
        }
        Process p=process;if(p!=null)p.destroy();throw new IOException("分析超时，请暂停后重新启动");
    }
    public synchronized void close(){closed=true;Process p=process;if(p!=null)p.destroy();output.offer("{\"error\":\"已停止\"}");}
    static String readText(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");}
}
