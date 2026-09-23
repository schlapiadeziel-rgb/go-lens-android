package cn.local.golens;
import android.graphics.*;
import java.util.Arrays;

public final class BoardVision {
    public static final class Result { public final int[] cells; public final double confidence; Result(int[] c,double f){cells=c;confidence=f;} }
    public interface Pixels { int width(); int height(); int get(int x,int y); }
    static int red(int c){return (c>>16)&255;}static int green(int c){return (c>>8)&255;}static int blue(int c){return c&255;}
    static float luma(int c){return .299f*red(c)+.587f*green(c)+.114f*blue(c);}
    static int pixel(Pixels b,float x,float y){return b.get(Math.max(0,Math.min(b.width()-1,Math.round(x))),Math.max(0,Math.min(b.height()-1,Math.round(y))));}
    public static Result read(Bitmap image,RectF rect,int n){
        return read(new Pixels(){public int width(){return image.getWidth();}public int height(){return image.getHeight();}public int get(int x,int y){return image.getPixel(x,y);}},rect.left,rect.top,rect.right,rect.bottom,n);
    }
    public static Result read(Pixels image,float left,float top,float right,float bottom,int n){
        if(right-left<n*8||bottom-top<n*8||left<0||top<0||right>=image.width()||bottom>=image.height())return new Result(new int[n*n],0);
        float dx=(right-left)/(n-1),dy=(bottom-top)/(n-1);
        float[] bg=new float[(n-1)*(n-1)];int bi=0;
        for(int y=0;y<n-1;y++)for(int x=0;x<n-1;x++)bg[bi++]=luma(pixel(image,left+(x+.5f)*dx,top+(y+.5f)*dy));
        Arrays.sort(bg);float baseline=bg[bg.length/2];int backgroundOutliers=0;for(float v:bg)if(Math.abs(v-baseline)>35)backgroundOutliers++;int[] board=new int[n*n];int uncertain=0,empty=0,grid=0;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){
            float cx=left+x*dx,cy=top+y*dy;
            float[] samples=new float[12];float sat=0;
            for(int k=0;k<12;k++){double angle=(35+(k%3)*10+(k/3)*90)*Math.PI/180;int p=pixel(image,cx+(float)Math.cos(angle)*dx*.30f,cy+(float)Math.sin(angle)*dy*.30f);samples[k]=luma(p);sat+=Math.max(red(p),Math.max(green(p),blue(p)))-Math.min(red(p),Math.min(green(p),blue(p)));}
            Arrays.sort(samples);float m=(samples[5]+samples[6])/2;float spread=samples[9]-samples[2];sat/=12;
            if(m<Math.min(110,baseline-40)&&spread<90)board[y*n+x]=1;
            else if(m>Math.max(165,baseline+10)&&sat<48&&spread<65)board[y*n+x]=2;
            else {empty++;float center=255;int radius=Math.max(2,Math.round(Math.min(dx,dy)*.10f));for(int yy=-radius;yy<=radius;yy++)for(int xx=-radius;xx<=radius;xx++)center=Math.min(center,luma(pixel(image,cx+xx,cy+yy)));if(center<m-22)grid++;if(Math.abs(m-baseline)>28)uncertain++;}
        }
        double confidence=1.0-uncertain/(double)(n*n);
        if(baseline<105||baseline>238||backgroundOutliers>bg.length*.03||(empty>20&&grid<empty*.88))confidence=0;
        return new Result(board,confidence);
    }
}
