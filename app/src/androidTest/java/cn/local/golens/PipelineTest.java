package cn.local.golens;
import android.test.InstrumentationTestCase;import android.graphics.*;import android.app.Activity;import android.content.Intent;import org.json.*;import java.util.*;

public class PipelineTest extends InstrumentationTestCase {
    private Activity foreground(){Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);return getInstrumentation().startActivitySync(intent);}
    public void testCaptureAndIllegalTransitions(){
        BoardState b=new BoardState(9);int[] initial=new int[81];initial[10]=2;initial[1]=1;initial[9]=1;initial[11]=1;b.reset(initial,1);
        int[] capture=BoardState.play(initial,19,1,9);assertNotNull(capture);assertEquals(0,capture[10]);assertTrue(b.accept(capture));assertEquals(2,b.next);
        int[] wrong=capture.clone();wrong[40]=1;assertFalse(b.accept(wrong));
        int[] multi=capture.clone();multi[40]=2;multi[50]=2;assertFalse(b.accept(multi));
        assertEquals("A9",b.coordinate(0));assertEquals("J1",b.coordinate(80));assertEquals(80,b.index("J1"));assertEquals(-1,b.index("pass"));
        int[] suicide=new int[81];suicide[1]=2;suicide[9]=2;assertNull(BoardState.play(suicide,0,1,9));
    }
    public void testKoAndPass()throws Exception {
        BoardState b=new BoardState(9);int[] s=new int[81];s[10]=2;s[1]=1;s[9]=1;s[19]=1;s[2]=2;s[12]=2;s[20]=2;b.reset(s,1);
        int[] a=BoardState.play(s,11,1,9);assertNotNull(a);assertTrue(b.accept(a));int[] recapture=BoardState.play(a,10,2,9);assertNotNull(recapture);assertFalse(b.accept(recapture));
        b.pass();assertEquals(1,b.next);JSONObject q=b.query("test",7.5,10,1);assertEquals("pass",q.getJSONArray("moves").getJSONArray(1).getString(1));
    }
    private Bitmap boardImage(int n,int[] cells,boolean labels){
        Bitmap b=Bitmap.createBitmap(800,1000,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(240,239,232));Paint p=new Paint(3);p.setColor(Color.rgb(207,173,116));c.drawRect(20,100,780,860,p);float step=40;float left=40,top=120;p.setColor(Color.rgb(64,48,30));p.setStrokeWidth(2);for(int i=0;i<n;i++){c.drawLine(left+i*step,top,left+i*step,top+(n-1)*step,p);c.drawLine(left,top+i*step,left+(n-1)*step,top+i*step,p);}for(int i=0;i<cells.length;i++)if(cells[i]!=0){p.setColor(cells[i]==1?Color.rgb(22,24,25):Color.rgb(249,248,243));c.drawCircle(left+i%n*step,top+i/n*step,17,p);if(labels){p.setColor(cells[i]==1?Color.WHITE:Color.BLACK);p.setTextSize(12);c.drawText("8",left+i%n*step-3,top+i/n*step+3,p);}}return b;
    }
    public void testVisionRoundTripAndOcclusion(){for(int n:new int[]{9,13,19}){int[] cells=new int[n*n];cells[0]=1;cells[n*n-1]=2;cells[n*3+3]=1;cells[n*5+4]=2;Bitmap img=boardImage(n,cells,true);BoardVision.Result r=BoardVision.read(img,new RectF(40,120,40+(n-1)*40,120+(n-1)*40),n);assertTrue("confidence "+r.confidence,r.confidence>=.97);assertTrue(Arrays.equals(cells,r.cells));img.eraseColor(Color.WHITE);assertEquals(0.0,BoardVision.read(img,new RectF(40,120,40+(n-1)*40,120+(n-1)*40),n).confidence);img.recycle();}}
    public void testRealOfflineEngine()throws Exception{
        Activity activity=foreground();LocalEngine engine=new LocalEngine(getInstrumentation().getTargetContext(),0);try{BoardState b=new BoardState(19);long start=System.currentTimeMillis();JSONObject r=engine.analyze(b.query("integration",7.5,32,1.2));JSONArray choices=r.getJSONArray("moveInfos");assertTrue(choices.length()>0);int idx=b.index(choices.getJSONObject(0).getString("move"));assertTrue(idx>=0&&idx<361);android.util.Log.i("GoLensTest","b10 19x19 cold ms="+(System.currentTimeMillis()-start)+" best="+choices.getJSONObject(0).getString("move"));int[] seen=BoardState.play(b.cells,idx,1,19);assertTrue(b.accept(seen));start=System.currentTimeMillis();JSONObject second=engine.analyze(b.query("integration2",7.5,32,1.2));int next=b.index(second.getJSONArray("moveInfos").getJSONObject(0).getString("move"));assertTrue(next==-1||(next>=0&&b.cells[next]==0));android.util.Log.i("GoLensTest","b10 19x19 warm ms="+(System.currentTimeMillis()-start));}finally{engine.close();getInstrumentation().runOnMainSync(activity::finish);}
    }
    public void testStrongOfflineEngine()throws Exception{
        Activity activity=foreground();LocalEngine engine=new LocalEngine(getInstrumentation().getTargetContext(),1);
        try{
            BoardState board=new BoardState(13);long start=System.currentTimeMillis();
            JSONObject answer=engine.analyze(board.query("strong-integration",7.5,64,3));
            JSONArray choices=answer.getJSONArray("moveInfos");assertTrue(choices.length()>0);
            int index=board.index(choices.getJSONObject(0).getString("move"));
            assertTrue(index>=0&&index<169&&board.cells[index]==0);
            android.util.Log.i("GoLensTest","b15 13x13 cold ms="+(System.currentTimeMillis()-start)+" visits="+answer.getJSONObject("rootInfo").getInt("visits"));
        }finally{engine.close();getInstrumentation().runOnMainSync(activity::finish);}
    }
}
