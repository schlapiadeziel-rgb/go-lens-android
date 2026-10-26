package cn.local.golens;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateCheckerTest {
    @Test public void compareNumericVersions(){
        assertTrue(UpdateChecker.compareVersions("v0.8.10","0.8.9")>0);
        assertEquals(0,UpdateChecker.compareVersions("v0.8.4","0.8.4"));
        assertTrue(UpdateChecker.compareVersions("0.8.3","v0.8.4")<0);
    }

    @Test public void selectPublishedReleaseIncludingPrereleaseAndTrustedApk()throws Exception{
        String base="https://github.com/schlapiadeziel-rgb/go-lens-android/releases/";
        String json="["
            +"{\"tag_name\":\"v0.8.3\",\"draft\":false,\"html_url\":\""+base+"tag/v0.8.3\",\"assets\":[{\"name\":\"GoLens-0.8.3-arm64.apk\",\"browser_download_url\":\""+base+"download/v0.8.3/GoLens-0.8.3-arm64.apk\"}]},"
            +"{\"tag_name\":\"v0.8.9\",\"draft\":true,\"html_url\":\""+base+"tag/v0.8.9\"},"
            +"{\"tag_name\":\"v0.8.4\",\"prerelease\":true,\"html_url\":\""+base+"tag/v0.8.4\",\"assets\":[{\"name\":\"GoLens-0.8.4-arm64.apk\",\"browser_download_url\":\"https://example.com/fake.apk\"},{\"name\":\"GoLens-0.8.4-arm64.apk\",\"browser_download_url\":\""+base+"download/v0.8.4/GoLens-0.8.4-arm64.apk\"}]}]";
        UpdateChecker.Release latest=UpdateChecker.parseNewest(json);
        assertNotNull(latest);assertEquals("v0.8.4",latest.tag);
        assertEquals(base+"download/v0.8.4/GoLens-0.8.4-arm64.apk",latest.downloadUrl);
    }
}
