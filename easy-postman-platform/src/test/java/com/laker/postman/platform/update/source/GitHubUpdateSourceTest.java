package com.laker.postman.platform.update.source;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class GitHubUpdateSourceTest {

    @Test
    public void shouldBuildReleaseByTagApi() {
        String tagApiUrl = new GitHubUpdateSource().buildReleaseByTagApiUrl("v6.3.1");

        assertEquals(tagApiUrl,
                "https://api.github.com/repos/lakernote/easy-postman/releases/tags/v6.3.1");
    }
}
