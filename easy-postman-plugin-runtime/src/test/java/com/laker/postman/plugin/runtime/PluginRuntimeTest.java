package com.laker.postman.plugin.runtime;

import com.example.TestRuntimePlugin;
import com.laker.postman.plugin.api.PluginDescriptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class PluginRuntimeTest {
    private static final String CURRENT_PLUGIN_PLATFORM_VERSION = "4.0.0";

    private Path dataDir;

    @BeforeMethod
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("plugin-runtime-test");
        System.setProperty("easyPostman.data.dir", dataDir.toString());
        TestRuntimePlugin.reset();
        PluginRuntime.resetForTests();
    }

    @AfterMethod
    public void tearDown() {
        System.clearProperty("easyPostman.data.dir");
        PluginRuntime.resetForTests();
    }

    @Test
    public void shouldClearPendingUninstallWhenPluginIsEnabledAgain() {
        PluginRuntime.markPluginPendingUninstall("plugin-redis");

        assertTrue(PluginRuntime.isPluginPendingUninstall("plugin-redis"));
        assertFalse(PluginRuntime.isPluginEnabled("plugin-redis"));

        PluginRuntime.setPluginEnabled("plugin-redis", true);

        assertFalse(PluginRuntime.isPluginPendingUninstall("plugin-redis"));
        assertTrue(PluginRuntime.isPluginEnabled("plugin-redis"));
    }

    @Test
    public void shouldUseConfiguredDataDirectoryOverride() {
        Path managedDir = PluginRuntime.getManagedPluginDir();
        Path packageDir = PluginRuntime.getPluginPackageDir();

        assertTrue(managedDir.startsWith(dataDir));
        assertTrue(packageDir.startsWith(dataDir));
    }

    @Test
    public void shouldUseConfiguredDataDirectoryOverrideForPluginStorage() {
        PluginFileStorage storage = PluginFileStorage.forPlugin("plugin:redis");

        assertTrue(storage.dataDirectory().startsWith(dataDir.resolve("plugins").resolve("data")));
        assertEquals(storage.dataDirectory().getFileName().toString(), "plugin_redis");
    }

    @Test
    public void shouldRejectPluginStoragePathTraversal() throws Exception {
        PluginFileStorage storage = PluginFileStorage.forPlugin("plugin-redis");

        assertThrows(IOException.class, () -> storage.writeString("../escape.txt", "x"));
        assertThrows(IOException.class, () -> storage.writeString(dataDir.resolve("escape.txt").toString(), "x"));
        assertFalse(Files.exists(dataDir.resolve("plugins").resolve("escape.txt")));
        assertFalse(Files.exists(dataDir.resolve("escape.txt")));
    }

    @Test
    public void shouldExposeIndependentPluginPlatformVersion() {
        assertEquals(PluginRuntime.getCurrentPluginPlatformVersion(), CURRENT_PLUGIN_PLATFORM_VERSION);
    }

    @Test
    public void shouldRejectPluginWhenPlatformVersionIsOutOfRange() {
        PluginDescriptor descriptor = new PluginDescriptor(
                "plugin-redis",
                "Redis Plugin",
                "5.3.16",
                "com.example.StubPlugin",
                "",
                "",
                "",
                "",
                "9.0.0",
                "9.0.0"
        );

        PluginCompatibility compatibility = PluginRuntime.evaluateCompatibility(descriptor);

        assertFalse(compatibility.compatible());
        assertTrue(compatibility.appVersionCompatible());
        assertFalse(compatibility.platformVersionCompatible());
    }

    @Test
    public void shouldRejectPreviousPluginPlatformAfterRequestProtocolBreakingChange() {
        PluginDescriptor descriptor = new PluginDescriptor(
                "plugin-client-cert",
                "Client Certificate Plugin",
                "5.3.18",
                "com.example.StubPlugin",
                "",
                "",
                "",
                "",
                "4.0.0",
                "4.0.0"
        );

        PluginCompatibility compatibility = PluginRuntime.evaluateCompatibility(descriptor);

        assertFalse(compatibility.compatible());
        assertTrue(compatibility.appVersionCompatible());
        assertFalse(compatibility.platformVersionCompatible());
        assertEquals(compatibility.currentPlatformVersion(), CURRENT_PLUGIN_PLATFORM_VERSION);
        assertEquals(compatibility.maxPlatformVersion(), "4.0.0");
    }

    @Test
    public void shouldDeleteManagedPluginFilesDuringPendingUninstallCleanup() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-redis-5.3.16.jar");
        writeStubPluginJar(pluginJar, "plugin-redis");
        PluginRuntime.markPluginPendingUninstall("plugin-redis");

        PluginRuntime.cleanupPendingUninstallPlugins();

        assertFalse(Files.exists(pluginJar));
        assertFalse(PluginRuntime.isPluginPendingUninstall("plugin-redis"));
        assertTrue(PluginRuntime.isPluginEnabled("plugin-redis"));
    }

    @Test
    public void shouldLoadOnlyHighestVersionForSamePluginId() throws Exception {
        Path pluginDir = PluginRuntime.getManagedPluginDir();
        writePluginJar(pluginDir.resolve("plugin-dup-1.0.0.jar"),
                "plugin-dup", "1.0.0", "com.example.TestRuntimePlugin");
        writePluginJar(pluginDir.resolve("plugin-dup-2.0.0.jar"),
                "plugin-dup", "2.0.0", "com.example.TestRuntimePlugin");

        PluginRuntime.initialize();

        assertEquals(TestRuntimePlugin.getLoadCount(), 1);
        assertEquals(TestRuntimePlugin.getStartCount(), 1);
        assertNotNull(PluginRuntime.getRegistry().createScriptApis().get("testRuntime"));

        long loadedCount = PluginRuntime.getInstalledPlugins().stream()
                .filter(info -> "plugin-dup".equals(info.descriptor().id()) && info.loaded())
                .count();
        assertEquals(loadedCount, 1L);
        assertTrue(PluginRuntime.getInstalledPlugins().stream()
                .anyMatch(info -> "plugin-dup".equals(info.descriptor().id())
                        && "2.0.0".equals(info.descriptor().version())
                        && info.loaded()));
    }

    @Test
    public void shouldExposeSettingsContributionsFromLoadedPlugin() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-ok-1.0.0.jar");
        writePluginJar(pluginJar, "plugin-ok", "1.0.0", "com.example.TestRuntimePlugin");

        PluginRuntime.initialize();

        var contributions = PluginRuntime.getRegistry().getSettingsContributions();
        assertEquals(contributions.size(), 1);
        assertEquals(contributions.get(0).id(), "test-runtime-settings");
        assertNotNull(contributions.get(0).titleClassLoader());
    }

    @Test
    public void shouldExposeMenuContributionsFromLoadedPlugin() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-ok-1.0.0.jar");
        writePluginJar(pluginJar, "plugin-ok", "1.0.0", "com.example.TestRuntimePlugin");

        PluginRuntime.initialize();

        var contributions = PluginRuntime.getRegistry().getMenuContributions();
        assertEquals(contributions.size(), 1);
        assertEquals(contributions.get(0).id(), "test-runtime-action");
        assertNotNull(contributions.get(0).titleClassLoader());
    }

    @Test
    public void shouldExposeStatusBarActionContributionsFromLoadedPlugin() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-ok-1.0.0.jar");
        writePluginJar(pluginJar, "plugin-ok", "1.0.0", "com.example.TestRuntimePlugin");

        PluginRuntime.initialize();

        var contributions = PluginRuntime.getRegistry().getStatusBarActionContributions();
        assertEquals(contributions.size(), 1);
        assertEquals(contributions.get(0).id(), "test-runtime-status-action");
        assertEquals(contributions.get(0).targetId(), "runtime-tool");
        assertNotNull(contributions.get(0).iconClassLoader());
    }

    @Test
    public void shouldExposeUpdateMetadataContributionsFromLoadedPlugin() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-ok-1.0.0.jar");
        writePluginJar(pluginJar, "plugin-ok", "1.0.0", "com.example.TestRuntimePlugin");

        PluginRuntime.initialize();

        var contributions = PluginRuntime.getRegistry().getUpdateMetadataContributions();
        assertEquals(contributions.size(), 1);
        assertEquals(contributions.get(0).id(), "test-runtime-update-metadata");
        assertEquals(contributions.get(0).loadMetadata().get(0).pluginId(), "plugin-ok");
        assertEquals(contributions.get(0).loadMetadata().get(0).version(), "1.0.1");
    }

    @Test
    public void shouldExposePrivateStorageToLoadedPlugin() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-ok-1.0.0.jar");
        writePluginJar(pluginJar, "plugin-ok", "1.0.0", "com.example.TestRuntimePlugin");

        PluginRuntime.initialize();

        Path storageFile = dataDir.resolve("plugins").resolve("data")
                .resolve("plugin-ok").resolve("runtime-storage.txt");
        assertTrue(Files.exists(storageFile));
        assertEquals(Files.readString(storageFile), "plugin-ok");
    }

    @Test
    public void shouldContinueLoadingOtherPluginsWhenOnePluginFails() throws Exception {
        Path pluginDir = PluginRuntime.getManagedPluginDir();
        writePluginJar(pluginDir.resolve("plugin-broken-1.0.0.jar"),
                "plugin-broken", "1.0.0", "com.example.MissingPlugin");
        writePluginJar(pluginDir.resolve("plugin-ok-1.0.0.jar"),
                "plugin-ok", "1.0.0", "com.example.TestRuntimePlugin");

        PluginRuntime.initialize();

        assertEquals(TestRuntimePlugin.getLoadCount(), 1);
        assertEquals(TestRuntimePlugin.getStartCount(), 1);
        assertNotNull(PluginRuntime.getRegistry().createScriptApis().get("testRuntime"));
        assertTrue(PluginRuntime.getInstalledPlugins().stream()
                .anyMatch(info -> "plugin-ok".equals(info.descriptor().id()) && info.loaded()));
        PluginFileInfo brokenPlugin = PluginRuntime.getInstalledPlugins().stream()
                .filter(info -> "plugin-broken".equals(info.descriptor().id()))
                .findFirst()
                .orElseThrow();
        assertFalse(brokenPlugin.loaded());
        assertFalse(brokenPlugin.compatible());
        assertTrue(brokenPlugin.hasLoadFailure());
        assertTrue(brokenPlugin.loadFailureMessage().contains("MissingPlugin"));
    }

    @Test
    public void shouldContinueLoadingOtherPluginsWhenOnePluginHasBinaryIncompatibleLinkage() throws Exception {
        Path pluginDir = PluginRuntime.getManagedPluginDir();
        writePluginJar(pluginDir.resolve("plugin-old-client-cert-1.0.0.jar"),
                "plugin-old-client-cert", "1.0.0", "com.example.LinkageErrorRuntimePlugin");
        writePluginJar(pluginDir.resolve("plugin-ok-1.0.0.jar"),
                "plugin-ok", "1.0.0", "com.example.TestRuntimePlugin");

        PluginRuntime.initialize();

        assertEquals(TestRuntimePlugin.getLoadCount(), 1);
        assertEquals(TestRuntimePlugin.getStartCount(), 1);
        assertNotNull(PluginRuntime.getRegistry().createScriptApis().get("testRuntime"));
        assertTrue(PluginRuntime.getInstalledPlugins().stream()
                .anyMatch(info -> "plugin-ok".equals(info.descriptor().id()) && info.loaded()));
        PluginFileInfo brokenPlugin = PluginRuntime.getInstalledPlugins().stream()
                .filter(info -> "plugin-old-client-cert".equals(info.descriptor().id()))
                .findFirst()
                .orElseThrow();
        assertFalse(brokenPlugin.loaded());
        assertFalse(brokenPlugin.compatible());
        assertTrue(brokenPlugin.hasLoadFailure());
        assertTrue(brokenPlugin.loadFailureMessage().contains("ClientCertificatePluginService"));
    }

    @Test
    public void shouldCallPluginStopDuringShutdown() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-ok-1.0.0.jar");
        writePluginJar(pluginJar, "plugin-ok", "1.0.0", "com.example.TestRuntimePlugin");

        PluginRuntime.initialize();
        PluginRuntime.shutdown();

        assertEquals(TestRuntimePlugin.getLoadCount(), 1);
        assertEquals(TestRuntimePlugin.getStartCount(), 1);
        assertEquals(TestRuntimePlugin.getStopCount(), 1);
    }

    @Test
    public void shouldLoadHighestCompatibleVersionWhenLatestVersionIsIncompatible() throws Exception {
        Path pluginDir = PluginRuntime.getManagedPluginDir();
        writePluginJar(pluginDir.resolve("plugin-select-1.0.0.jar"),
                "plugin-select", "1.0.0", "com.example.TestRuntimePlugin");
        writePluginJar(pluginDir.resolve("plugin-select-2.0.0.jar"),
                "plugin-select", "2.0.0", "com.example.TestRuntimePlugin",
                "", "", "9.0.0", "9.0.0");

        PluginRuntime.initialize();

        assertEquals(TestRuntimePlugin.getLoadCount(), 1);
        assertTrue(PluginRuntime.getInstalledPlugins().stream()
                .anyMatch(info -> "plugin-select".equals(info.descriptor().id())
                        && "1.0.0".equals(info.descriptor().version())
                        && info.loaded()
                        && info.compatible()));
        assertTrue(PluginRuntime.getInstalledPlugins().stream()
                .anyMatch(info -> "plugin-select".equals(info.descriptor().id())
                        && "2.0.0".equals(info.descriptor().version())
                        && !info.loaded()
                        && !info.compatible()));
    }

    @Test
    public void shouldNotLoadDisabledPlugin() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-disabled-1.0.0.jar");
        writePluginJar(pluginJar, "plugin-disabled", "1.0.0", "com.example.TestRuntimePlugin");
        PluginRuntime.setPluginEnabled("plugin-disabled", false);

        PluginRuntime.initialize();

        assertEquals(TestRuntimePlugin.getLoadCount(), 0);
        assertTrue(PluginRuntime.getInstalledPlugins().stream()
                .anyMatch(info -> "plugin-disabled".equals(info.descriptor().id())
                        && !info.enabled()
                        && !info.loaded()));
    }

    @Test
    public void shouldRejectPreviousPlatformPluginBeforeLoadingItsCode() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-old-platform-1.0.0.jar");
        writePluginJar(pluginJar, "plugin-old-platform", "1.0.0", "com.example.TestRuntimePlugin",
                "", "", "4.0.0", "4.0.0");

        PluginRuntime.initialize();

        assertEquals(TestRuntimePlugin.getLoadCount(), 0);
        assertTrue(PluginRuntime.getInstalledPlugins().stream()
                .anyMatch(info -> "plugin-old-platform".equals(info.descriptor().id())
                        && !info.compatible()
                        && !info.loaded()));
    }

    @Test
    public void shouldDeletePendingUninstallPluginBeforeLoading() throws Exception {
        Path pluginJar = PluginRuntime.getManagedPluginDir().resolve("plugin-pending-1.0.0.jar");
        writePluginJar(pluginJar, "plugin-pending", "1.0.0", "com.example.TestRuntimePlugin");
        PluginRuntime.markPluginPendingUninstall("plugin-pending");

        PluginRuntime.initialize();

        assertEquals(TestRuntimePlugin.getLoadCount(), 0);
        assertFalse(Files.exists(pluginJar));
        assertFalse(PluginRuntime.isPluginPendingUninstall("plugin-pending"));
    }

    private static void writeStubPluginJar(Path jarPath, String pluginId) throws IOException {
        writePluginJar(jarPath, pluginId, "5.3.16", "com.example.StubPlugin");
    }

    private static void writePluginJar(Path jarPath,
                                       String pluginId,
                                       String version,
                                       String entryClass) throws IOException {
        writePluginJar(jarPath, pluginId, version, entryClass, "", "", "", "");
    }

    private static void writePluginJar(Path jarPath,
                                       String pluginId,
                                       String version,
                                       String entryClass,
                                       String minAppVersion,
                                       String maxAppVersion,
                                       String minPlatformVersion,
                                       String maxPlatformVersion) throws IOException {
        Files.createDirectories(jarPath.getParent());
        try (OutputStream outputStream = Files.newOutputStream(jarPath);
             JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
            jarOutputStream.putNextEntry(new JarEntry("META-INF/easy-postman/" + pluginId + ".properties"));
            jarOutputStream.write(("""
                    plugin.id=%s
                    plugin.name=Stub Plugin
                    plugin.version=%s
                    plugin.entryClass=%s
                    plugin.minAppVersion=%s
                    plugin.maxAppVersion=%s
                    plugin.minPlatformVersion=%s
                    plugin.maxPlatformVersion=%s
                    """.formatted(
                    pluginId,
                    version,
                    entryClass,
                    minAppVersion,
                    maxAppVersion,
                    minPlatformVersion,
                    maxPlatformVersion
            )).getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }
    }
}
