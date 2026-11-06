package com.laker.postman.platform.update.source;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class GiteeUpdateSourceTest {

    @Test
    public void shouldRequestReleasesNewestFirst() {
        String releasesApiUrl = new GiteeUpdateSource().getAllReleasesApiUrl();

        assertTrue(releasesApiUrl.contains("direction=desc"));
    }

    @Test
    public void shouldBuildReleaseByTagApiWithoutListQueryParameters() {
        String tagApiUrl = new GiteeUpdateSource().buildReleaseByTagApiUrl("v6.3.1");

        assertEquals(tagApiUrl,
                "https://gitee.com/api/v5/repos/lakernote/easy-postman/releases/tags/v6.3.1");
    }
}
