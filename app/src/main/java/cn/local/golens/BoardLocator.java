package cn.local.golens;

import java.util.*;

/** Finds a wooden board and fits two equally spaced line lattices, without fixed screen coordinates. */
public final class BoardLocator {
    public static final class Found {
        public final float left,top,right,bottom;public final int size;public final double score;
        Found(float l,float t,float r,float b,int n,double s){left=l;top=t;right=r;bottom=b;size=n;score=s;}
    }
    private static boolean wood(int c){int r=BoardVision.red(c),g=BoardVision.green(c),b=BoardVision.blue(c);return r>105&&r<253&&g>85&&g<235&&r>=g-5&&g>b+12&&r-b>28;}
    private static final class Axis {float start,end,step;double quality;Axis(float a,float b,float s,double q){start=a;end=b;step=s;quality=q;}}
    public static Found locate(BoardVision.Pixels image){
        int w=image.width(),h=image.height(),sample=Math.max(2,w/360);int rows=(h+sample-1)/sample;boolean[] active=new boolean[rows];
        for(int r=0;r<rows;r++){int hits=0;for(int x=0;x<w;x+=sample)if(wood(image.get(x,Math.min(h-1,r*sample))))hits++;active[r]=hits*sample>w*.40;}
        ArrayList<int[]> bands=new ArrayList<>();int begin=-1,last=-1,maxGap=Math.max(2,(int)(w*.075/sample));
        for(int r=0;r<=rows;r++){if(r<rows&&active[r]){if(begin<0)begin=r;last=r;}if(begin>=0&&(r==rows||r-last>maxGap)){if((last-begin)*sample>w*.40)bands.add(new int[]{begin*sample,Math.min(h-1,last*sample)});begin=-1;}}
        Found best=null;
        for(int[] band:bands){int t=band[0],b=band[1],l=w,r=0;for(int x=0;x<w;x+=sample){int count=0,total=0;for(int y=t;y<=b;y+=sample){total++;if(wood(image.get(x,y)))count++;}if(count>total*.25){l=Math.min(l,x);r=x;}}
            if(r-l<w*.48||b-t<(r-l)*.70||b-t>(r-l)*1.40)continue;
            l=Math.max(0,l-sample);r=Math.min(w-1,r+sample);t=Math.max(0,t-sample);b=Math.min(h-1,b+sample);
            float[] vertical=projection(image,l,t,r,b,true),horizontal=projection(image,l,t,r,b,false);
            for(int n:new int[]{19,13,9}){Axis ax=fit(vertical,r-l,n),ay=fit(horizontal,b-t,n);if(ax==null||ay==null||Math.abs(ax.step/ay.step-1)>.04)continue;
                double score=Math.min(ax.quality,ay.quality);Found f=new Found(l+ax.start,t+ay.start,l+ax.end,t+ay.end,n,score);
                BoardVision.Result read=BoardVision.read(image,f.left,f.top,f.right,f.bottom,n);

                if(read.confidence>=.97&&(best==null||score>best.score))best=f;
            }
        }return best;
    }
    private static float[] projection(BoardVision.Pixels image,int l,int t,int r,int b,boolean vertical){
        int len=vertical?r-l+1:b-t+1,other=vertical?b-t+1:r-l+1;int stride=Math.max(1,other/350);float[] raw=new float[len],sharp=new float[len];
        for(int i=0;i<len;i++){float sum=0;int count=0;for(int j=0;j<other;j+=stride){int c=image.get(vertical?l+i:l+j,vertical?t+j:t+i);sum+=Math.max(0,150-BoardVision.luma(c));count++;}raw[i]=sum/count;}
        int radius=Math.max(3,len/150);for(int i=radius;i<len-radius;i++)sharp[i]=Math.max(0,raw[i]-(raw[i-radius]+raw[i+radius])*.5f);return sharp;
    }
    private static Axis fit(float[] p,int span,int n){
        float max=0;for(float v:p)max=Math.max(max,v);if(max<8)return null;float threshold=Math.max(6,max*.16f);double best=-1;float bestStart=0,bestStep=0;
        float low=span/(n+1.5f),high=span/(n-1f);int margin=Math.max(4,span/8);
        for(float step=low;step<=high;step+=.25f){if(step<8)continue;for(int start=2;start<=margin;start++){
            if(start+(n-1)*step>=span-1)continue;double sum=0;int hits=0;
            for(int k=0;k<n;k++){int center=Math.round(start+k*step);float peak=0;for(int delta=-2;delta<=2;delta++){int at=center+delta;if(at>=0&&at<p.length)peak=Math.max(peak,p[at]);}sum+=Math.min(peak,max*.90);if(peak>=threshold)hits++;}
            if(hits<n*.88)continue;double score=sum/n;if(score>best){best=score;bestStart=start;bestStep=step;}
        }}
        if(best<8)return null;
        // Regress the actual peak coordinates to remove the fit tolerance's endpoint bias.
        double sx=0,sy=0,sxx=0,sxy=0;int count=0;
        for(int k=0;k<n;k++){int center=Math.round(bestStart+k*bestStep),at=center;float peak=-1;for(int d=-2;d<=2;d++){int j=center+d;if(j>=0&&j<p.length&&p[j]>peak){peak=p[j];at=j;}}if(peak>=threshold){sx+=k;sy+=at;sxx+=k*k;sxy+=k*at;count++;}}
        double step=(count*sxy-sx*sy)/(count*sxx-sx*sx),start=(sy-step*sx)/count;
        int extras=0;for(int i=3;i<p.length-3;i++)if(p[i]>Math.max(8,max*.4)&&p[i]>=p[i-1]&&p[i]>p[i+1]){double k=Math.rint((i-start)/step);if(k<0||k>=n||Math.abs(i-(start+k*step))>Math.max(3,step*.15))extras++;}
        if(extras>n*.35)return null;
        return new Axis((float)start,(float)(start+(n-1)*step),(float)step,best);
    }
}


