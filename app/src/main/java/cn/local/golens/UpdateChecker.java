package cn.local.golens;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional release metadata lookup. Board capture and analysis never use the network. */
final class UpdateChecker {
    static final String RELEASES_PAGE="https://github.com/schlapiadeziel-rgb/go-lens-android/releases";
    private static final String API="https://api.github.com/repos/schlapiadeziel-rgb/go-lens-android/releases?per_page=10";
    private static final Pattern VERSION=Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)");

    static final class Release {
        final String tag,downloadUrl,htmlUrl;final long size;
        Release(String tag,String downloadUrl,String htmlUrl,long size){this.tag=tag;this.downloadUrl=downloadUrl;this.htmlUrl=htmlUrl;this.size=size;}
    }

    static Release fetchLatest()throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(API).openConnection();
        connection.setConnectTimeout(6000);connection.setReadTimeout(6000);
        connection.setRequestProperty("Accept","application/vnd.github+json");
        connection.setRequestProperty("User-Agent","GoLens-Android");
        try{
            if(connection.getResponseCode()!=200)throw new java.io.IOException("GitHub 暂时不可用");
            try(InputStream input=connection.getInputStream()){
                Release newest=parseNewest(LocalEngine.readText(input));
                if(newest==null)throw new java.io.IOException("暂未找到可用版本");
                return newest;
            }
        }finally{connection.disconnect();}
    }

    static Release parseNewest(String json)throws Exception{
        JSONArray releases=new JSONArray(json);Release newest=null;
        for(int i=0;i<releases.length();i++){
            JSONObject item=releases.optJSONObject(i);if(item==null||item.optBoolean("draft"))continue;
            String tag=item.optString("tag_name");if(versionParts(tag)==null)continue;
            String html=item.optString("html_url");
            if(!trusted(html,"/schlapiadeziel-rgb/go-lens-android/releases/tag/"))continue;
            String download=null;long size=0;JSONArray assets=item.optJSONArray("assets");
            if(assets!=null)for(int j=0;j<assets.length();j++){
                JSONObject asset=assets.optJSONObject(j);if(asset==null||!asset.optString("name").matches("GoLens-[0-9.]+-arm64\\.apk"))continue;
                String candidate=asset.optString("browser_download_url");
                if(trusted(candidate,"/schlapiadeziel-rgb/go-lens-android/releases/download/")){download=candidate;size=asset.optLong("size");break;}
            }
            if(newest==null||compareVersions(tag,newest.tag)>0)newest=new Release(tag,download,html,size);
        }
        return newest;
    }

    static int compareVersions(String a,String b){
        int[] first=versionParts(a),second=versionParts(b);
        if(first==null||second==null)throw new IllegalArgumentException("版本号格式错误");
        for(int i=0;i<3;i++)if(first[i]!=second[i])return Integer.compare(first[i],second[i]);
        return 0;
    }

    private static int[] versionParts(String version){
        Matcher matcher=VERSION.matcher(version);if(!matcher.matches())return null;
        try{return new int[]{Integer.parseInt(matcher.group(1)),Integer.parseInt(matcher.group(2)),Integer.parseInt(matcher.group(3))};}
        catch(NumberFormatException e){return null;}
    }

    private static boolean trusted(String url,String pathPrefix){
        try{URL parsed=new URL(url);return "https".equals(parsed.getProtocol())&&"github.com".equals(parsed.getHost())&&parsed.getPath().startsWith(pathPrefix);}
        catch(Exception e){return false;}
    }
}
