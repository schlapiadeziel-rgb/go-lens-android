package cn.local.golens;

import java.util.*;
import org.json.*;

/** Screen rows are top to bottom; GTP coordinates count upwards and skip I. */
public final class BoardState {
    public final int size;
    public int[] cells, initial;
    public int next = 1, initialPlayer = 1;
    public final List<int[]> moves = new ArrayList<>();
    private final Set<String> positions = new HashSet<>();
    public BoardState(int size) { this.size=size; reset(new int[size*size],1); }
    public void reset(int[] board,int player) {
        cells=board.clone(); initial=board.clone(); next=player; initialPlayer=player;
        moves.clear(); positions.clear(); positions.add(Arrays.toString(cells));
    }
    public void pass() { moves.add(new int[]{next,-1}); next=3-next; }
    public String coordinate(int index) { return index<0 ? "pass" : "ABCDEFGHJKLMNOPQRST".charAt(index%size)+Integer.toString(size-index/size); }
    public int index(String coord) {
        if(coord.equalsIgnoreCase("pass"))return -1;
        try { int x="ABCDEFGHJKLMNOPQRST".indexOf(Character.toUpperCase(coord.charAt(0))); int y=size-Integer.parseInt(coord.substring(1)); return x>=0&&x<size&&y>=0&&y<size ? y*size+x : -2; } catch(Exception e){return -2;}
    }
    public boolean accept(int[] seen) {
        if(Arrays.equals(cells,seen))return true;
        int added=-1;
        for(int i=0;i<cells.length;i++)if(cells[i]!=seen[i]&&seen[i]!=0){
            if(cells[i]!=0||seen[i]!=next||added!=-1)return false;
            added=i;
        }
        if(added<0)return false;
        int[] played=play(cells,added,next,size);
        if(played==null||!Arrays.equals(played,seen)||positions.contains(Arrays.toString(played)))return false;
        moves.add(new int[]{next,added}); cells=played; positions.add(Arrays.toString(cells)); next=3-next; return true;
    }
    /** Recover a visually observed move after an unrecorded pass. */
    public boolean acceptAfterPass(int[] seen){
        int before=next;pass();if(accept(seen))return true;
        moves.remove(moves.size()-1);next=before;return false;
    }
    /** Resume after several unseen moves only when no stone was removed and colors alternate. */
    public boolean recoverSkippedNoCaptures(int[] seen){
        if(seen.length!=cells.length)return false;
        int black=0,white=0;
        for(int i=0;i<seen.length;i++){
            if(cells[i]==seen[i])continue;
            if(cells[i]!=0)return false;
            if(seen[i]==1)black++;else if(seen[i]==2)white++;else return false;
        }
        int total=black+white;
        if(total<2)return false;
        int expectedBlack=next==1?(total+1)/2:total/2;
        if(black!=expectedBlack||white!=total-expectedBlack)return false;
        reset(seen,total%2==0?next:3-next);
        return true;
    }
    /** Reattach across a short gap with captures of previously visible stones. */
    public boolean recoverSkippedWithCaptures(int[] seen){
        if(seen.length!=cells.length)return false;
        int black=0,white=0,removedColor=0,removed=0;
        for(int i=0;i<seen.length;i++){
            if(cells[i]==seen[i])continue;
            if(cells[i]==0){if(seen[i]==1)black++;else if(seen[i]==2)white++;else return false;}
            else if(seen[i]==0){if(removedColor!=0&&removedColor!=cells[i])return false;removedColor=cells[i];removed++;}
            else return false;
        }
        int total=black+white;
        if(removed==0||total<2||total>8)return false;
        int expectedBlack=next==1?(total+1)/2:total/2;
        if(black!=expectedBlack||white!=total-expectedBlack)return false;
        boolean adjacentNewCaptor=false;
        for(int i=0;i<seen.length;i++)if(cells[i]==removedColor&&seen[i]==0){
            for(int a:neighbors(i,size))if(a>=0){
                if(seen[a]==0&&cells[a]!=removedColor)return false;
                if(cells[a]==0&&seen[a]==3-removedColor)adjacentNewCaptor=true;
            }
        }
        if(!adjacentNewCaptor)return false;
        reset(seen,total%2==0?next:3-next);
        return true;
    }
    static int[] neighbors(int p,int n){int x=p%n,y=p/n; return new int[]{x>0?p-1:-1,x<n-1?p+1:-1,y>0?p-n:-1,y<n-1?p+n:-1};}
    static Set<Integer> group(int[] b,int start,int n){Set<Integer> g=new HashSet<>();ArrayDeque<Integer> q=new ArrayDeque<>();q.add(start);g.add(start);while(!q.isEmpty()){int p=q.remove();for(int a:neighbors(p,n))if(a>=0&&b[a]==b[start]&&g.add(a))q.add(a);}return g;}
    static boolean hasLiberty(int[] b,Set<Integer> g,int n){for(int p:g)for(int a:neighbors(p,n))if(a>=0&&b[a]==0)return true;return false;}
    public static int[] play(int[] old,int p,int color,int n){
        if(p<0||p>=old.length||old[p]!=0)return null;
        int[] b=old.clone();b[p]=color;
        for(int a:neighbors(p,n))if(a>=0&&b[a]==3-color){Set<Integer> g=group(b,a,n);if(!hasLiberty(b,g,n))for(int k:g)b[k]=0;}
        return hasLiberty(b,group(b,p,n),n)?b:null;
    }
    public JSONObject query(String id,double komi,int visits,double seconds)throws JSONException{
        JSONObject q=new JSONObject();q.put("id",id);q.put("boardXSize",size);q.put("boardYSize",size);q.put("rules","chinese");q.put("komi",komi);
        q.put("initialPlayer",initialPlayer==1?"B":"W");JSONArray stones=new JSONArray();
        for(int i=0;i<initial.length;i++)if(initial[i]!=0)stones.put(new JSONArray().put(initial[i]==1?"B":"W").put(coordinate(i)));
        JSONArray played=new JSONArray();for(int[] m:moves)played.put(new JSONArray().put(m[0]==1?"B":"W").put(coordinate(m[1])));
        q.put("initialStones",stones);q.put("moves",played);q.put("maxVisits",visits);q.put("overrideSettings",new JSONObject().put("maxTime",seconds));return q;
    }
}
