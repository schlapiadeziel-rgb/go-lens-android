package cn.local.golens;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class TencentUiTest {
    static class Screen implements BoardVision.Pixels {
        final int[] colors=new int[630*1400];
        Screen(){Arrays.fill(colors,0xffc8d0dc);}
        public int width(){return 630;}
        public int height(){return 1400;}
        public int get(int x,int y){return colors[y*630+x];}
        void fill(int l,int t,int r,int b,int color){for(int y=t;y<b;y++)for(int x=l;x<r;x++)colors[y*630+x]=color;}
    }
    @Test public void detectOnlyConfidentActiveClockAndConfirmation(){
        Screen s=new Screen();assertEquals(0,TencentUi.turn(s,25,235,605));assertEquals(0,TencentUi.playerColor(s,25,235,605));assertFalse(TencentUi.confirming(s,25,605,815,null));
        s.fill(69,130,81,142,0xff202020);s.fill(551,130,563,142,0xffe5e5e5);
        s.fill(90,170,150,195,0xffffc438);assertEquals(1,TencentUi.playerColor(s,25,235,605));assertEquals(1,TencentUi.turn(s,25,235,605));
        s.fill(69,130,81,142,0xffe5e5e5);s.fill(551,130,563,142,0xff202020);assertEquals(2,TencentUi.playerColor(s,25,235,605));assertEquals(2,TencentUi.turn(s,25,235,605));
        s.fill(90,170,150,195,0xffc8d0dc);s.fill(480,170,540,195,0xffffc438);assertEquals(1,TencentUi.turn(s,25,235,605));
        s.fill(230,1020,390,1160,0xff396b50);assertTrue(TencentUi.confirming(s,25,605,815,null));
        assertTrue(TencentUi.confirming(s,25,605,815,new int[]{0,950,320,1200}));
    }
}
