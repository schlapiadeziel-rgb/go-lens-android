package cn.local.golens;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;

public class LocatorTest {
 static class Screen implements BoardVision.Pixels {int w=480,h=1000;int[] data=new int[w*h];public int width(){return w;}public int height(){return h;}public int get(int x,int y){return data[y*w+x];}}
 static Screen scene(int n,int top,boolean crowded){Screen s=new Screen();Arrays.fill(s.data,0xfff5f3ef);float left=40,edge=396,step=edge/(n-1);for(int y=top-16;y<=top+edge+16;y++)for(int x=24;x<=452;x++)s.data[y*s.w+x]=0xffcfad74;
  for(int y=top;y<=top+edge;y++)for(int x=40;x<=436;x++){double ix=(x-left)/step,iy=(y-top)/step;if(Math.abs(ix-Math.rint(ix))*step<1.1||Math.abs(iy-Math.rint(iy))*step<1.1)s.data[y*s.w+x]=0xff3f3122;}
  for(int row=0;row<n;row++)for(int col=0;col<n;col++){if(crowded?(row*n+col)%3!=0:!((row==3&&col==3)||(row==n-4&&col==n-4)))continue;float cx=left+col*step,cy=top+row*step,rad=step*.43f;for(int y=(int)(cy-rad);y<=cy+rad;y++)for(int x=(int)(cx-rad);x<=cx+rad;x++)if((x-cx)*(x-cx)+(y-cy)*(y-cy)<rad*rad)s.data[y*s.w+x]=(row+col)%2==0?0xff191919:0xfff8f8f3;}
  return s;
 }
 @Test public void automaticallyFindSizesAndMovedBoard(){for(int n:new int[]{9,13,19})for(int top:new int[]{120,350}){Screen s=scene(n,top,false);BoardLocator.Found f=BoardLocator.locate(s);assertNotNull("n="+n+" top="+top,f);assertEquals(n,f.size);assertEquals(40,f.left,3);assertEquals(top,f.top,3);assertEquals(436,f.right,3);assertEquals(top+396,f.bottom,3);}}
 @Test public void crowdedBoard(){Screen s=scene(19,240,true);BoardLocator.Found f=BoardLocator.locate(s);assertNotNull(f);assertEquals(19,f.size);}
 @Test public void rejectNoBoard(){Screen s=new Screen();Arrays.fill(s.data,0xfff0f0f0);assertNull(BoardLocator.locate(s));Arrays.fill(s.data,0xffcfad74);assertNull(BoardLocator.locate(s));}
}
