package cn.local.golens;

/** Conservative cues from Tencent Go's standard portrait board skin. */
final class TencentUi {
    private TencentUi() {}
    static int playerColor(BoardVision.Pixels image,float left,float top,float right){
        float width=right-left;
        int leftStone=stone(image,(int)(left+.084f*width),(int)(top-.173f*width));
        int rightStone=stone(image,(int)(left+.918f*width),(int)(top-.173f*width));
        return leftStone!=0&&rightStone==3-leftStone?leftStone:0;
    }
    static int turn(BoardVision.Pixels image,float left,float top,float right){
        float width=right-left;
        int leftStone=playerColor(image,left,top,right);
        if(leftStone==0)return 0;
        int rightStone=3-leftStone;
        int leftGlow=yellow(image,left+.08f*width,top-.13f*width,left+.30f*width,top-.055f*width);
        int rightGlow=yellow(image,left+.70f*width,top-.13f*width,left+.94f*width,top-.055f*width);
        if(leftGlow>=35&&leftGlow>rightGlow*4+20)return leftStone;
        if(rightGlow>=35&&rightGlow>leftGlow*4+20)return rightStone;
        return 0;
    }
    private static int stone(BoardVision.Pixels image,int x,int y){
        if(x<3||x>=image.width()-3||y<3||y>=image.height()-3)return 0;
        int total=0;
        for(int dy=-2;dy<=2;dy+=2)for(int dx=-2;dx<=2;dx+=2){int color=image.get(x+dx,y+dy);total+=((color>>16)&255)+((color>>8)&255)+(color&255);}
        int brightness=total/27;
        return brightness<90?1:brightness>170?2:0;
    }
    static boolean confirming(BoardVision.Pixels image,float left,float right,float bottom,int[] excluded){
        float width=right-left;
        int hits=0;
        for(int y=clamp((int)(bottom+.34f*width),0,image.height()-1);y<clamp((int)(bottom+.66f*width),0,image.height());y+=4)
            for(int x=clamp((int)(left+.31f*width),0,image.width()-1);x<clamp((int)(left+.69f*width),0,image.width());x+=4){
                if(excluded!=null&&x>=excluded[0]&&x<excluded[2]&&y>=excluded[1]&&y<excluded[3])continue;
                int color=image.get(x,y),r=(color>>16)&255,g=(color>>8)&255,b=color&255;
                if(g>70&&g<180&&g>r+15&&g>b+5)hits++;
            }
        return hits>300;
    }
    private static int yellow(BoardVision.Pixels image,float left,float top,float right,float bottom){
        int hits=0;
        for(int y=clamp((int)top,0,image.height()-1);y<clamp((int)bottom,0,image.height());y+=4)
            for(int x=clamp((int)left,0,image.width()-1);x<clamp((int)right,0,image.width());x+=4){
                int color=image.get(x,y),r=(color>>16)&255,g=(color>>8)&255,b=color&255;
                if(r>210&&g>170&&b<130&&r>=g)hits++;
            }
        return hits;
    }
    private static int clamp(int value,int lo,int hi){return Math.max(lo,Math.min(hi,value));}
}
