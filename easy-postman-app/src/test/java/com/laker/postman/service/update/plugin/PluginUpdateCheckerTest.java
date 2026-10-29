package com.laker.postman.service.update.plugin;

import com.laker.postman.plugin.api.PluginDescriptor;
import com.laker.postman.plugin.api.PluginUpdateMetadata;
import com.laker.postman.plugin.api.PluginUpdateMetadataContribution;
import com.laker.postman.plugin.manager.market.PluginCatalogEntry;
import com.laker.postman.plugin.runtime.PluginFileInfo;
import com.laker.postman.plugin.runtime.PluginRuntime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class PluginUpdateCheckerTest {

    private static final String CURRENT_PLUGIN_PLATFORM_VERSION = "4.0.0";

    private Path dataDir;
    private Path appDir;

    @BeforeMethod
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("plugin-update-checker-test");
        appDir = Files.createTempDirectory("plugin-update-checker-app");
        System.setProperty("easyPostman.data.dir", dataDir.toString());
        System.setProperty("easyPostman.app.dir", appDir.toString());
        PluginRuntime.resetForTests();
    }

    @AfterMethod
    public void tearDown() {
        System.clearProperty("easyPostman.data.dir");
        System.clearProperty("easyPostman.app.dir");
        System.clearProperty("easyPostman.portable");
        PluginRuntime.resetForTests();
    }

    @Test
    public void shouldFindCompatiblePluginUpdates() {
        List<PluginUpdateCandidate> candidates = PluginUpdateChecker.findUpdateCandidates(
                List.of(installedPlugin("plugin-kafka", "Kafka Plugin", "5.3.18")),
                List.of(
                        catalogEntry("plugin-kafka", "Kafka Plugin", "5.3.23", "", ""),
                        catalogEntry("plugin-redis", "Redis Plugin", "5.3.18", "", "")
                )
        );

        assertEquals(candidates.size(), 1);
        assertEquals(candidates.get(0).pluginId(), "plugin-kafka");
        assertEquals(candidates.get(0).installedVersion(), "5.3.18");
        assertEquals(candidates.get(0).latestVersion(), "5.3.23");
    }

    @Test
    public void shouldIgnoreIncompatibleAndNonNewerCatalogEntries() {
        List<PluginUpdateCandidate> candidates = PluginUpdateChecker.findUpdateCandidates(
                List.of(installedPlugin("plugin-kafka", "Kafka Plugin", "5.3.18")),
                List.of(
                        catalogEntry("plugin-kafka", "Kafka Plugin", "99.0.0", "99.0.0", ""),
                        catalogEntry("plugin-kafka", "Kafka Plugin", "5.3.18", "", ""),
                        catalogEntry("plugin-kafka", "Kafka Plugin", "5.3.17", "", "")
                )
        );

        assertTrue(candidates.isEmpty());
    }

    @Test
    public void shouldIgnoreCatalogUpdatesBuiltForOldPluginPlatform() {
        List<PluginUpdateCandidate> candidates = PluginUpdateChecker.findUpdateCandidates(
                List.of(installedPlugin("plugin-kafka", "Kafka Plugin", "5.3.18")),
                List.of(catalogEntry("plugin-kafka", "Kafka Plugin", "5.5.28", "", "", "3.0.0", "3.0.0"))
        );

        assertTrue(candidates.isEmpty());
    }

    @Test
    public void shouldFindCompatibleUpdateForInstalledPluginRejectedByOldPlatform() {
        List<PluginUpdateCandidate> candidates = PluginUpdateChecker.findUpdateCandidates(
                List.of(installedPlugin("plugin-kafka", "Kafka Plugin", "5.4.26", false)),
                List.of(catalogEntry("plugin-kafka", "Kafka Plugin", "5.5.28", "", "",
                        CURRENT_PLUGIN_PLATFORM_VERSION, CURRENT_PLUGIN_PLATFORM_VERSION))
        );

        assertEquals(candidates.size(), 1);
        assertEquals(candidates.get(0).pluginId(), "plugin-kafka");
        assertEquals(candidates.get(0).installedVersion(), "5.4.26");
        assertEquals(candidates.get(0).latestVersion(), "5.5.28");
    }

    @Test
    public void shouldUseHighestInstalledVersionPerPluginId() {
        List<PluginUpdateCandidate> candidates = PluginUpdateChecker.findUpdateCandidates(
                List.of(
                        installedPlugin("plugin-kafka", "Kafka Plugin", "5.3.18"),
                        installedPlugin("plugin-kafka", "Kafka Plugin", "5.3.20")
                ),
                List.of(catalogEntry("plugin-kafka", "Kafka Plugin", "5.3.21", "", ""))
        );

        assertEquals(candidates.size(), 1);
        assertEquals(candidates.get(0).installedVersion(), "5.3.20");
        assertEquals(candidates.get(0).latestVersion(), "5.3.21");
    }

    @Test
    public void shouldMergePluginContributedUpdateMetadataWithCatalogEntries() {
        PluginRuntime.getRegistry().registerUpdateMetadataContribution(new PluginUpdateMetadataContribution(
                "private-plugin-updates",
                900,
                () -> List.of(new PluginUpdateMetadata(
                        "plugin-kafka",
                        "Kafka Plugin",
                        "5.3.25",
                        "Private update source",
                        "https://example.com/private/plugin-kafka-5.3.25.jar",
                        "https://example.com/plugin-kafka",
                        "sha256-5.3.25"
                ))
        ));

        List<PluginCatalogEntry> catalogEntries = PluginUpdateMetadataResolver.mergeWithContributedMetadata(
                List.of(catalogEntry("plugin-kafka", "Kafka Plugin", "5.3.21", "", ""))
        );
        List<PluginUpdateCandidate> candidates = PluginUpdateChecker.findUpdateCandidates(
                List.of(installedPlugin("plugin-kafka", "Kafka Plugin", "5.3.18")),
                catalogEntries
        );

        assertEquals(catalogEntries.size(), 1);
        assertEquals(catalogEntries.get(0).version(), "5.3.25");
        assertEquals(catalogEntries.get(0).installUrl(), "https://example.com/private/plugin-kafka-5.3.25.jar");
        assertEquals(candidates.size(), 1);
        assertEquals(candidates.get(0).latestVersion(), "5.3.25");
    }

    private static PluginFileInfo installedPlugin(String id, String name, String version) {
        return installedPlugin(id, name, version, true);
    }

    private static PluginFileInfo installedPlugin(String id, String name, String version, boolean compatible) {
        return new PluginFileInfo(
                new PluginDescriptor(id, name, version, "com.example." + name.replace(" ", "")),
                Path.of("/tmp", id + "-" + version + ".jar"),
                true,
                true,
                compatible
        );
    }

    private static PluginCatalogEntry catalogEntry(String id,
                                                   String name,
                                                   String version,
                                                   String minAppVersion,
                                                   String maxAppVersion) {
        return catalogEntry(id, name, version, minAppVersion, maxAppVersion,
                CURRENT_PLUGIN_PLATFORM_VERSION, CURRENT_PLUGIN_PLATFORM_VERSION);
    }

    private static PluginCatalogEntry catalogEntry(String id,
                                                   String name,
                                                   String version,
                                                   String minAppVersion,
                                                   String maxAppVersion,
                                                   String minPlatformVersion,
                                                   String maxPlatformVersion) {
        return new PluginCatalogEntry(
                id,
                name,
                version,
                "",
                "https://example.com/" + id + "-" + version + ".jar",
                "https://example.com/" + id,
                "sha256-" + version,
                "https://example.com/" + id + "-" + version + ".jar",
                minAppVersion,
                maxAppVersion,
                minPlatformVersion,
                maxPlatformVersion
        );
    }
}
