package com.laker.postman.platform.update.source;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class UpdateSourceSelectorTest {

    @Test
    public void shouldUseInjectedGithubPreferenceWithoutAppSettings() {
        UpdateSourceSelector selector = new UpdateSourceSelector(() -> "github");

        UpdateSource source = selector.selectBestSource();

        assertEquals(source.getName(), "GitHub");
    }

    @Test
    public void shouldUseInjectedGiteePreferenceWithoutAppSettings() {
        UpdateSourceSelector selector = new UpdateSourceSelector(() -> "gitee");

        UpdateSource source = selector.selectBestSource();

        assertEquals(source.getName(), "Gitee");
    }

    @Test
    public void shouldResolveFallbackSourceByMetadataName() {
        UpdateSourceSelector selector = new UpdateSourceSelector(() -> "github");

        assertEquals(selector.getFallbackSource("GitHub").getName(), "Gitee");
        assertEquals(selector.getFallbackSource("Gitee").getName(), "GitHub");
        assertNull(selector.getFallbackSource("unknown"));
    }
}
