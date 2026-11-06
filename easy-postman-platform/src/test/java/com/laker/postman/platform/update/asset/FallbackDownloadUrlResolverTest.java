package com.laker.postman.platform.update.asset;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.laker.postman.platform.update.source.UpdateSource;
import com.laker.postman.platform.update.source.UpdateSourceSelector;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class FallbackDownloadUrlResolverTest {

    private final String originalOsName = System.getProperty("os.name");
    private final String originalOsArch = System.getProperty("os.arch");

    @AfterMethod
    public void restoreSystemProperties() {
        restoreProperty("os.name", originalOsName);
        restoreProperty("os.arch", originalOsArch);
    }

    @Test
    public void shouldResolveSameVersionAssetFromFallbackReleaseMetadata() {
        useMacArm();
        StubUpdateSource fallback = new StubUpdateSource(
                release("v6.3.1", "EasyPostman-6.3.1-macos-arm64.dmg"));
        FallbackDownloadUrlResolver resolver = new FallbackDownloadUrlResolver(
                new StubSourceSelector(fallback));

        String result = resolver.resolve("v6.3.1", "GitHub");

        assertEquals(result, "https://mirror.example/EasyPostman-6.3.1-macos-arm64.dmg");
        assertEquals(fallback.requestedTag, "v6.3.1");
    }

    @Test
    public void shouldReturnNullWhenFallbackHasNotPublishedExpectedVersion() {
        useMacArm();
        UpdateSource fallback = new StubUpdateSource(null);
        FallbackDownloadUrlResolver resolver = new FallbackDownloadUrlResolver(
                new StubSourceSelector(fallback));

        assertNull(resolver.resolve("v6.3.1", "GitHub"));
    }

    @Test
    public void shouldRejectUnexpectedTagReturnedByFallbackSource() {
        useMacArm();
        UpdateSource fallback = new StubUpdateSource(
                release("v6.3.1foo", "EasyPostman-6.3.1-macos-arm64.dmg"));
        FallbackDownloadUrlResolver resolver = new FallbackDownloadUrlResolver(
                new StubSourceSelector(fallback));

        assertNull(resolver.resolve("v6.3.1", "GitHub"));
    }

    @Test
    public void shouldReturnNullForUnknownPrimarySource() {
        useMacArm();
        FallbackDownloadUrlResolver resolver = new FallbackDownloadUrlResolver(
                new UpdateSourceSelector(() -> "github"));

        assertNull(resolver.resolve("v6.3.1", "Unknown"));
    }

    private static JSONObject release(String version, String assetName) {
        JSONObject asset = new JSONObject()
                .set("name", assetName)
                .set("browser_download_url", "https://mirror.example/" + assetName);
        return new JSONObject()
                .set("tag_name", version)
                .set("assets", new JSONArray().put(asset));
    }

    private static void useMacArm() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("os.arch", "aarch64");
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static final class StubSourceSelector extends UpdateSourceSelector {
        private final UpdateSource fallback;

        private StubSourceSelector(UpdateSource fallback) {
            super(() -> "github");
            this.fallback = fallback;
        }

        @Override
        public UpdateSource getFallbackSource(String primarySourceName) {
            return "GitHub".equalsIgnoreCase(primarySourceName) ? fallback : null;
        }
    }

    private static final class StubUpdateSource implements UpdateSource {
        private final JSONObject release;
        private String requestedTag;

        private StubUpdateSource(JSONObject release) {
            this.release = release;
        }

        @Override
        public String getName() {
            return "Fallback";
        }

        @Override
        public String getApiUrl() {
            return "https://mirror.example/api/latest";
        }

        @Override
        public String getAllReleasesApiUrl() {
            return "https://mirror.example/api/releases";
        }

        @Override
        public String getWebUrl() {
            return "https://mirror.example/releases";
        }

        @Override
        public long testConnectionSpeed() {
            return 1;
        }

        @Override
        public JSONObject fetchLatestReleaseInfo() {
            return release;
        }

        @Override
        public JSONObject fetchReleaseByTag(String tagName) {
            requestedTag = tagName;
            return release;
        }

        @Override
        public JSONArray fetchAllReleases(int limit) {
            return new JSONArray();
        }
    }
}
