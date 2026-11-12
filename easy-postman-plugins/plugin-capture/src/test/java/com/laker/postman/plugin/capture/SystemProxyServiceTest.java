package com.laker.postman.plugin.capture;

import com.laker.postman.plugin.api.PluginStorage;
import com.laker.postman.util.JsonUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class SystemProxyServiceTest {

    @Test
    public void shouldSkipWindowsRestoreWhenNoSnapshotWasCaptured() throws Exception {
        SystemProxyService service = new SystemProxyService();
        Method restoreWindowsSnapshot = SystemProxyService.class.getDeclaredMethod("restoreWindowsSnapshot");
        restoreWindowsSnapshot.setAccessible(true);

        restoreWindowsSnapshot.invoke(service);

        assertFalse(readBooleanField(service, "active"));
        assertNull(readObjectField(service, "windowsSnapshot"));
    }

    @Test
    public void shouldApplyWindowsProxyThroughRegistryAndRefreshWindowsSettings() throws Exception {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner();
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);

        service.enable("127.0.0.1", 8888);

        List<List<String>> commands = runner.commands();
        assertTrue(commands.stream().anyMatch(command -> command.equals(List.of(
                "reg", "add",
                "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyServer", "/t", "REG_SZ", "/d", "http=127.0.0.1:8888;https=127.0.0.1:8888", "/f"
        ))), "Windows Settings should see the configured proxy endpoint");
        assertTrue(commands.stream().anyMatch(command -> command.equals(List.of(
                "reg", "add",
                "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "1", "/f"
        ))), "Windows Settings should see the manual proxy switch enabled");
        assertTrue(commands.stream().anyMatch(command -> command.stream()
                        .anyMatch(part -> part.contains("InternetSetOption")
                                && part.contains("wininet.dll"))),
                "Windows proxy changes should notify WinINet after registry writes");
    }

    @Test
    public void shouldNotFailProxyEnableWhenWinInetRefreshCommandIsUnavailable() throws Exception {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner(true);
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);

        service.enable("127.0.0.1", 8888);

        List<List<String>> commands = runner.commands();
        assertTrue(readBooleanField(service, "active"));
        assertTrue(commands.stream().anyMatch(command -> command.equals(List.of(
                "reg", "add",
                "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyServer", "/t", "REG_SZ", "/d", "http=127.0.0.1:8888;https=127.0.0.1:8888", "/f"
        ))), "Registry should apply ProxyServer even when WinINet refresh is unavailable");
        assertTrue(commands.stream().anyMatch(command -> command.equals(List.of(
                "cmd", "/c", "RUNDLL32.EXE", "USER32.DLL,UpdatePerUserSystemParameters", "1", "True"
        ))), "Legacy Windows settings refresh should still be attempted when WinINet notification fails");
    }

    @Test
    public void shouldFailWindowsProxyEnableWhenRegistryWriteDoesNotTakeEffect() {
        SystemProxyService service = new SystemProxyService(command -> {
            if (command.size() > 2 && "reg".equals(command.get(0)) && "query".equals(command.get(1))) {
                return new SystemProxyService.CommandResult(1, List.of());
            }
            return new SystemProxyService.CommandResult(0, List.of());
        }, "Windows 11", false);

        IllegalStateException exception = expectThrows(
                IllegalStateException.class,
                () -> service.enable("127.0.0.1", 8888)
        );

        assertTrue(exception.getMessage().contains("ProxyEnable is off"), exception.getMessage());
    }

    @Test
    public void shouldRestoreWindowsProxyThroughRegistryAndRefreshWindowsSettings() throws Exception {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner();
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);

        service.enable("127.0.0.1", 8888);
        runner.commands().clear();

        service.disable();

        List<List<String>> commands = runner.commands();
        assertTrue(commands.stream().anyMatch(command -> command.equals(List.of(
                "reg", "delete",
                "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyEnable", "/f"
        ))), "Windows Settings should see the manual proxy switch restored to its previous missing state");
        assertTrue(commands.stream().anyMatch(command -> command.equals(List.of(
                "reg", "delete",
                "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyServer", "/f"
        ))), "Windows Settings should see the proxy endpoint restored to its previous missing state");
        assertTrue(commands.stream().anyMatch(command -> command.stream()
                        .anyMatch(part -> part.contains("InternetSetOption")
                                && part.contains("wininet.dll"))),
                "Windows proxy restore should notify WinINet after registry writes");
    }

    @Test
    public void shouldScopeWindowsCaptureProxyToHttpAndHttps() {
        assertEquals(SystemProxyService.windowsProxyServerValue("127.0.0.1", 8888),
                "http=127.0.0.1:8888;https=127.0.0.1:8888");
    }

    @DataProvider
    public Object[][] nonExactCaptureProxyServerValues() {
        return new Object[][]{
                {"127.0.0.1:8888"},
                {"https=127.0.0.1:8888;http=127.0.0.1:8888"},
                {"HTTP=127.0.0.1:8888;https=127.0.0.1:8888"},
                {"http =127.0.0.1:8888;https=127.0.0.1:8888"},
                {"http= 127.0.0.1:8888;https=127.0.0.1:8888"},
                {"http=127.0.0.1:8888;https=127.0.0.1:8888;"},
                {"http=127.0.0.1:8888;https=127.0.0.1:8888;ftp=10.0.0.1:21"},
                {"http=corp:8080;http=127.0.0.1:8888;https=127.0.0.1:8888"},
                {"http=127.0.0.1:8888;https=127.0.0.1:8888;socks=127.0.0.1:8888"},
                {"http=127.0.0.1:8888;https=127.0.0.1:8888;socks=socks://127.0.0.1:8888"},
                {"http=127.0.0.1:8888;https=127.0.0.1:8888;socks=corp:1080"},
                {"http=127.0.0.1:8888"},
        };
    }

    @Test(dataProvider = "nonExactCaptureProxyServerValues")
    public void shouldRewriteAnyProxyServerValueOtherThanTheExactCaptureValue(String changedValue)
            throws Exception {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner();
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);
        service.enable("127.0.0.1", 8888);
        assertFalse(service.ensureSynced(), "The exact value written by capture is healthy");

        runner.registry.put("ProxyServer", new RegistryValue("REG_SZ", changedValue));

        assertTrue(service.ensureSynced(), "Capture owns ProxyServer exclusively: " + changedValue);
        assertEquals(runner.registry.get("ProxyServer").data(), "http=127.0.0.1:8888;https=127.0.0.1:8888");
    }

    @Test
    public void shouldRewriteProxyOverrideEditedDuringCapture() throws Exception {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner();
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);
        service.enable("127.0.0.1", 8888);
        String applied = runner.registry.get("ProxyOverride").data();

        runner.registry.put("ProxyOverride", new RegistryValue("REG_SZ", "*.corp.example"));

        assertTrue(service.ensureSynced());
        assertEquals(runner.registry.get("ProxyOverride").data(), applied);
    }

    @Test
    public void shouldRejectIpv6ListenAddressForWindowsProxySync() {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner();
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> service.enable("::1", 8888));

        assertTrue(exception.getMessage().contains("IPv6"), exception.getMessage());
        assertFalse(service.isActive());
        assertFalse(runner.commands().stream().anyMatch(command -> command.size() > 1
                && "reg".equals(command.get(0)) && "add".equals(command.get(1))));
    }

    @DataProvider
    public Object[][] windowsAutomaticProxySettings() {
        return new Object[][]{
                {"AutoConfigURL", new RegistryValue("REG_SZ", "http://wpad.corp/proxy.pac")},
                {"AutoDetect", new RegistryValue("REG_DWORD", "0x1")},
        };
    }

    @Test(dataProvider = "windowsAutomaticProxySettings")
    public void shouldReapplyWhenWindowsAutomaticProxyIsReEnabledDuringCapture(String name, RegistryValue value)
            throws Exception {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner();
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);
        service.enable("127.0.0.1", 8888);
        assertFalse(service.ensureSynced());

        runner.registry.put(name, value);

        assertTrue(service.ensureSynced(), "PAC/auto-detect overrides the manual proxy and must be repaired");
        assertFalse(runner.registry.containsKey("AutoConfigURL"));
        assertTrue(Long.decode(runner.registry.get("AutoDetect").data()) == 0L);
    }

    @Test
    public void shouldRewriteExtraWindowsProxyEntriesAddedDuringCaptureAndRestoreOriginalOnStop()
            throws Exception {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner(Map.of(
                "ProxyEnable", new RegistryValue("REG_DWORD", "0x1"),
                "ProxyServer", new RegistryValue("REG_SZ", "corp:8080")
        ));
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);
        service.enable("127.0.0.1", 8888);

        runner.registry.put("ProxyServer", new RegistryValue("REG_SZ",
                "http=127.0.0.1:8888;https=127.0.0.1:8888;socks=socks://127.0.0.1:8888"));

        assertTrue(service.ensureSynced());
        assertEquals(runner.registry.get("ProxyServer").data(), "http=127.0.0.1:8888;https=127.0.0.1:8888");
        service.disable();
        assertEquals(runner.registry.get("ProxyServer").data(), "corp:8080");
    }

    @Test
    public void shouldReapplyScopedProxyWhenWindowsValueRevertsToBareFormat() throws Exception {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner();
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);
        service.enable("127.0.0.1", 8888);
        assertFalse(service.ensureSynced());

        runner.registry.put("ProxyServer", new RegistryValue("REG_SZ", "127.0.0.1:8888"));

        assertTrue(service.ensureSynced());
        assertEquals(runner.registry.get("ProxyServer").data(), "http=127.0.0.1:8888;https=127.0.0.1:8888");
    }

    private static final Map<String, RegistryValue> ORIGINAL_WINDOWS_PROXY = Map.of(
            "ProxyEnable", new RegistryValue("REG_DWORD", "0x0"),
            "ProxyServer", new RegistryValue("REG_SZ", "corp.example:8080"),
            "ProxyOverride", new RegistryValue("REG_SZ", "*.corp.example"),
            "AutoConfigURL", new RegistryValue("REG_SZ", "http://wpad.corp.example/proxy.pac"),
            "AutoDetect", new RegistryValue("REG_DWORD", "0x1"));

    /** Simulates a capture process that applied the Windows proxy and then crashed; returns the applied state. */
    private static Map<String, RegistryValue> crashedWindowsCapture(MemoryPluginStorage storage) throws Exception {
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner(ORIGINAL_WINDOWS_PROXY);
        SystemProxyService original = new SystemProxyService(runner, "Windows 11", false);
        original.configureStorage(storage);
        original.enable("127.0.0.1", 8888);
        assertTrue(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE));
        setSnapshotOwnerProcess(storage, Long.MAX_VALUE, 0L);
        return new HashMap<>(runner.registry);
    }

    private static SystemProxyService.SystemProxyRecoveryResult recover(
            MemoryPluginStorage storage, WindowsRegistryCommandRunner runner) throws Exception {
        SystemProxyService recovered = new SystemProxyService(runner, "Windows 11", false);
        recovered.configureStorage(storage);
        return recovered.restorePersistedSnapshotIfOwned();
    }

    @DataProvider
    public Object[][] ownedWindowsProxyStates() {
        return new Object[][]{
                {"fully applied", List.of(), null},
                {"legacy bare ProxyServer", List.of(), "127.0.0.1:8888"},
                {"crashed after writing ProxyServer only",
                        List.of("ProxyEnable", "ProxyOverride", "AutoConfigURL", "AutoDetect"), null},
                {"crashed after writing ProxyServer and ProxyOverride",
                        List.of("ProxyEnable", "AutoConfigURL", "AutoDetect"), null},
                {"crashed before deleting AutoConfigURL", List.of("AutoConfigURL", "AutoDetect"), null},
                {"restore interrupted after ProxyEnable", List.of("ProxyEnable"), null},
                {"restore interrupted after ProxyOverride",
                        List.of("ProxyEnable", "ProxyServer", "ProxyOverride"), null},
                {"restore finished but snapshot not deleted", List.copyOf(ORIGINAL_WINDOWS_PROXY.keySet()), null},
        };
    }

    @Test(dataProvider = "ownedWindowsProxyStates")
    public void shouldRecoverWindowsProxyFromAnyMixOfOriginalAndCaptureValues(
            String scenario, List<String> originalValues, String proxyServer) throws Exception {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        Map<String, RegistryValue> registry = crashedWindowsCapture(storage);
        originalValues.forEach(name -> registry.put(name, ORIGINAL_WINDOWS_PROXY.get(name)));
        if (proxyServer != null) {
            registry.put("ProxyServer", new RegistryValue("REG_SZ", proxyServer));
        }
        WindowsRegistryCommandRunner recoveryRunner = new WindowsRegistryCommandRunner(registry);

        SystemProxyService.SystemProxyRecoveryResult result = recover(storage, recoveryRunner);

        assertTrue(result.restored(), scenario + ": " + result.detail());
        assertFalse(result.stale(), scenario);
        assertFalse(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE), scenario);
        assertEquals(recoveryRunner.registry, ORIGINAL_WINDOWS_PROXY, scenario);
    }

    @DataProvider
    public Object[][] userChangedWindowsProxySettings() {
        return new Object[][]{
                {"ProxyServer", new RegistryValue("REG_SZ", "127.0.0.1:9000")},
                {"ProxyServer", new RegistryValue("REG_SZ", "127.0.0.1:8888;http=corp:8080;https=corp:8080")},
                {"ProxyServer", new RegistryValue("REG_SZ", "http=127.0.0.1:8888;https=127.0.0.1:8888;ftp=corp:21")},
                {"ProxyServer", new RegistryValue("REG_SZ", "http =127.0.0.1:8888;https=127.0.0.1:8888")},
                {"ProxyOverride", new RegistryValue("REG_SZ", "*.other.example;<local>")},
                {"AutoConfigURL", new RegistryValue("REG_SZ", "http://wpad.other.example/proxy.pac")},
        };
    }

    @Test(dataProvider = "userChangedWindowsProxySettings")
    public void shouldDropPersistedWindowsProxySnapshotWhenSettingsWereChangedAfterCrash(
            String name, RegistryValue value) throws Exception {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        Map<String, RegistryValue> registry = crashedWindowsCapture(storage);
        registry.put(name, value);
        WindowsRegistryCommandRunner recoveryRunner = new WindowsRegistryCommandRunner(registry);

        SystemProxyService.SystemProxyRecoveryResult result = recover(storage, recoveryRunner);

        assertTrue(result.attempted());
        assertFalse(result.restored());
        assertTrue(result.stale());
        assertFalse(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE));
        assertFalse(recoveryRunner.commands().stream().anyMatch(command -> command.size() > 1
                        && "reg".equals(command.get(0))
                        && ("add".equals(command.get(1)) || "delete".equals(command.get(1)))),
                "Recovery must not overwrite " + name + " changed by the user or group policy");
    }

    @Test
    public void shouldNotTouchWindowsProxyWhenRecoverySnapshotCannotBePersisted() {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        storage.failWrites = true;
        WindowsRegistryCommandRunner runner = new WindowsRegistryCommandRunner(ORIGINAL_WINDOWS_PROXY);
        SystemProxyService service = new SystemProxyService(runner, "Windows 11", false);
        service.configureStorage(storage);

        expectThrows(IllegalStateException.class, () -> service.enable("127.0.0.1", 8888));

        assertFalse(service.isActive());
        assertEquals(runner.registry, ORIGINAL_WINDOWS_PROXY);
    }

    @Test
    public void shouldUseDocumentedMacProxyArguments() throws Exception {
        MacNetworkSetupCommandRunner runner = new MacNetworkSetupCommandRunner(false);
        SystemProxyService service = new SystemProxyService(runner, "macOS 26", false);

        service.enable("127.0.0.1", 8888);

        assertTrue(runner.commands().contains(List.of(
                "/usr/sbin/networksetup", "-setwebproxy", "Wi-Fi", "127.0.0.1", "8888", "off", "", ""
        )));
        assertTrue(runner.commands().contains(List.of(
                "/usr/sbin/networksetup", "-setsecurewebproxy", "Wi-Fi", "127.0.0.1", "8888", "off", "", ""
        )));
    }

    @Test
    public void shouldFallbackToLegacyMacProxyArgumentsWhenNeeded() throws Exception {
        MacNetworkSetupCommandRunner runner = new MacNetworkSetupCommandRunner(true);
        SystemProxyService service = new SystemProxyService(runner, "macOS 12", false);

        service.enable("127.0.0.1", 8888);

        assertTrue(runner.commands().contains(List.of(
                "/usr/sbin/networksetup", "-setwebproxy", "Wi-Fi", "127.0.0.1", "8888"
        )));
        assertTrue(runner.commands().contains(List.of(
                "/usr/sbin/networksetup", "-setsecurewebproxy", "Wi-Fi", "127.0.0.1", "8888"
        )));
    }

    @Test
    public void shouldRepairMacProxyWhenEffectiveSettingsAreReplacedWhileCaptureRuns() throws Exception {
        MacNetworkSetupCommandRunner runner = new MacNetworkSetupCommandRunner(false);
        SystemProxyService service = new SystemProxyService(runner, "macOS 26", false);
        service.enable("127.0.0.1", 8888);

        runner.replaceProxyExternally();

        assertTrue(service.ensureSynced());
        assertTrue(service.isEffectivelySynced());
        assertFalse(service.ensureSynced(), "A healthy proxy must not be reapplied on every check");
        assertTrue(runner.commands().stream().filter(command -> command.size() > 1
                        && "-setsecurewebproxystate".equals(command.get(1))
                        && "on".equals(command.get(3))).count() >= 2);
    }

    @Test
    public void shouldReportLostSyncWhenMacProxyCannotBeRestored() throws Exception {
        MacNetworkSetupCommandRunner runner = new MacNetworkSetupCommandRunner(false);
        SystemProxyService service = new SystemProxyService(runner, "macOS 26", false);
        service.enable("127.0.0.1", 8888);
        runner.replaceProxyExternally();
        runner.blockUpdates = true;

        expectThrows(IllegalStateException.class, service::ensureSynced);

        assertFalse(service.isEffectivelySynced());
    }

    @Test
    public void shouldRestoreOriginalMacProxyAfterPartialEnableFailure() throws Exception {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        MacNetworkSetupCommandRunner runner = new MacNetworkSetupCommandRunner(false);
        runner.failNextSecureProxyEnable = true;
        SystemProxyService service = new SystemProxyService(runner, "macOS 26", false);
        service.configureStorage(storage);

        expectThrows(IllegalStateException.class, () -> service.enable("127.0.0.1", 8888));

        assertFalse(service.isActive());
        assertFalse(runner.webProxyEnabled);
        assertFalse(runner.secureProxyEnabled);
        assertTrue(runner.proxyPort == 9000);
        assertFalse(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE));
    }

    @Test
    public void shouldNotRestoreProxyOwnedByAnotherCaptureInstance() throws Exception {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        MacNetworkSetupCommandRunner runner = new MacNetworkSetupCommandRunner(false);
        SystemProxyService older = new SystemProxyService(runner, "macOS 26", false);
        SystemProxyService newer = new SystemProxyService(runner, "macOS 26", false);
        older.configureStorage(storage);
        newer.configureStorage(storage);
        older.enable("127.0.0.1", 8888);
        newer.enable("127.0.0.1", 8888);
        int commandCount = runner.commands().size();

        expectThrows(IllegalStateException.class, older::ensureSynced);
        older.disable();

        assertFalse(older.isActive());
        assertFalse(older.isEffectivelySynced());
        assertTrue(newer.isActive());
        assertTrue(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE));
        assertTrue(runner.commands().size() == commandCount,
                "The older instance must not change settings owned by the newer instance");
    }

    @Test
    public void shouldLeaveProxyUntouchedWhenSnapshotOwnerProcessIsStillAlive() throws Exception {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        MacNetworkSetupCommandRunner runner = new MacNetworkSetupCommandRunner(false);
        SystemProxyService owner = new SystemProxyService(runner, "macOS 26", false);
        owner.configureStorage(storage);
        owner.enable("127.0.0.1", 8888);
        int commandCount = runner.commands().size();

        SystemProxyService otherProcess = new SystemProxyService(runner, "macOS 26", false);
        otherProcess.configureStorage(storage);
        SystemProxyService.SystemProxyRecoveryResult result = otherProcess.restorePersistedSnapshotIfOwned();

        assertFalse(result.attempted());
        assertFalse(result.restored());
        assertTrue(runner.webProxyEnabled);
        assertTrue(runner.secureProxyEnabled);
        assertTrue(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE));
        assertTrue(runner.commands().size() == commandCount,
                "A second process must not query or restore a live capture owner's system proxy");
    }

    @Test
    public void shouldRecoverWhenSnapshotPidWasReusedByAnotherProcess() throws Exception {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        MacNetworkSetupCommandRunner runner = new MacNetworkSetupCommandRunner(false);
        SystemProxyService owner = new SystemProxyService(runner, "macOS 26", false);
        owner.configureStorage(storage);
        owner.enable("127.0.0.1", 8888);
        long startedAt = ProcessHandle.current().info().startInstant()
                .orElseThrow().toEpochMilli();
        setSnapshotOwnerProcess(storage, ProcessHandle.current().pid(), startedAt - 1);

        SystemProxyService recovered = new SystemProxyService(runner, "macOS 26", false);
        recovered.configureStorage(storage);
        SystemProxyService.SystemProxyRecoveryResult result = recovered.restorePersistedSnapshotIfOwned();

        assertTrue(result.restored());
        assertFalse(runner.webProxyEnabled);
        assertFalse(runner.secureProxyEnabled);
    }

    @Test
    public void shouldNotDeleteAnotherInstanceSnapshotWhenInactiveInstanceShutsDown() throws Exception {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        MacNetworkSetupCommandRunner runner = new MacNetworkSetupCommandRunner(false);
        SystemProxyService inactive = new SystemProxyService(runner, "macOS 26", false);
        SystemProxyService running = new SystemProxyService(runner, "macOS 26", false);
        inactive.configureStorage(storage);
        running.configureStorage(storage);
        running.enable("127.0.0.1", 8888);
        Method restoreQuietly = SystemProxyService.class.getDeclaredMethod("restoreQuietly");
        restoreQuietly.setAccessible(true);

        restoreQuietly.invoke(inactive);

        assertTrue(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE));
        assertTrue(running.isActive());
    }

    private static boolean readBooleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    @SuppressWarnings("unchecked")
    private static void setSnapshotOwnerProcess(MemoryPluginStorage storage, long pid, long startedAt) {
        String json = storage.files.get(SystemProxyService.RECOVERY_STORAGE_FILE);
        Map<String, Object> snapshot = new HashMap<>(
                JsonUtil.convertValue(JsonUtil.readTree(json), Map.class));
        snapshot.put("ownerPid", pid);
        snapshot.put("ownerStartedAt", startedAt);
        storage.files.put(SystemProxyService.RECOVERY_STORAGE_FILE, JsonUtil.toJsonPrettyStr(snapshot));
    }

    private static Object readObjectField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class MemoryPluginStorage implements PluginStorage {
        private final Map<String, String> files = new HashMap<>();
        private boolean failWrites;

        @Override
        public Optional<String> readString(String relativePath) {
            return Optional.ofNullable(files.get(relativePath));
        }

        @Override
        public void writeString(String relativePath, String content) throws IOException {
            if (failWrites) {
                throw new IOException("disk full");
            }
            files.put(relativePath, content);
        }

        @Override
        public void delete(String relativePath) {
            files.remove(relativePath);
        }

        @Override
        public Path dataDirectory() {
            return Path.of("");
        }
    }

    private record RegistryValue(String type, String data) {
    }

    private static final class WindowsRegistryCommandRunner implements SystemProxyService.CommandRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private final Map<String, RegistryValue> registry = new HashMap<>();
        private final boolean failPowerShell;

        private WindowsRegistryCommandRunner() {
            this(false);
        }

        private WindowsRegistryCommandRunner(boolean failPowerShell) {
            this(Map.of(), failPowerShell);
        }

        private WindowsRegistryCommandRunner(Map<String, RegistryValue> initialValues) {
            this(initialValues, false);
        }

        private WindowsRegistryCommandRunner(Map<String, RegistryValue> initialValues, boolean failPowerShell) {
            registry.putAll(initialValues);
            this.failPowerShell = failPowerShell;
        }

        private List<List<String>> commands() {
            return commands;
        }

        @Override
        public SystemProxyService.CommandResult run(List<String> command) throws Exception {
            commands.add(command);
            if ("powershell".equals(command.get(0)) && failPowerShell) {
                throw new IOException("powershell missing");
            }
            if (!"reg".equals(command.get(0)) || command.size() < 2) {
                return new SystemProxyService.CommandResult(0, List.of());
            }
            if ("query".equals(command.get(1))) {
                return query(command);
            }
            if ("add".equals(command.get(1))) {
                return add(command);
            }
            if ("delete".equals(command.get(1))) {
                return delete(command);
            }
            return new SystemProxyService.CommandResult(0, List.of());
        }

        private SystemProxyService.CommandResult query(List<String> command) {
            String name = valueAfter(command, "/v");
            RegistryValue value = registry.get(name);
            if (value == null) {
                return new SystemProxyService.CommandResult(1, List.of());
            }
            return new SystemProxyService.CommandResult(
                    0,
                    List.of("    " + name + "    " + value.type() + "    " + value.data())
            );
        }

        private SystemProxyService.CommandResult add(List<String> command) {
            String name = valueAfter(command, "/v");
            String type = valueAfter(command, "/t");
            String data = valueAfter(command, "/d");
            registry.put(name, new RegistryValue(type, data));
            return new SystemProxyService.CommandResult(0, List.of());
        }

        private SystemProxyService.CommandResult delete(List<String> command) {
            registry.remove(valueAfter(command, "/v"));
            return new SystemProxyService.CommandResult(0, List.of());
        }

        private String valueAfter(List<String> command, String flag) {
            int index = command.indexOf(flag);
            return index >= 0 && index + 1 < command.size() ? command.get(index + 1) : "";
        }
    }

    private static final class MacNetworkSetupCommandRunner implements SystemProxyService.CommandRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private final boolean rejectDocumentedProxyArguments;
        private boolean webProxyEnabled;
        private boolean secureProxyEnabled;
        private int proxyPort = 9000;
        private boolean blockUpdates;
        private boolean failNextSecureProxyEnable;

        private MacNetworkSetupCommandRunner(boolean rejectDocumentedProxyArguments) {
            this.rejectDocumentedProxyArguments = rejectDocumentedProxyArguments;
        }

        private List<List<String>> commands() {
            return commands;
        }

        private void replaceProxyExternally() {
            webProxyEnabled = false;
            secureProxyEnabled = false;
            proxyPort = 9000;
        }

        @Override
        public SystemProxyService.CommandResult run(List<String> command) {
            commands.add(command);
            if ("/usr/sbin/scutil".equals(command.get(0))) {
                return new SystemProxyService.CommandResult(0, List.of(
                        "HTTPEnable : " + (webProxyEnabled ? 1 : 0),
                        "HTTPSEnable : " + (secureProxyEnabled ? 1 : 0),
                        "HTTPProxy : 127.0.0.1",
                        "HTTPSProxy : 127.0.0.1",
                        "HTTPPort : " + proxyPort,
                        "HTTPSPort : " + proxyPort,
                        "ProxyAutoConfigEnable : 0",
                        "ProxyAutoDiscoveryEnable : 0"
                ));
            }
            String operation = command.size() > 1 ? command.get(1) : "";
            if (failNextSecureProxyEnable && "-setsecurewebproxystate".equals(operation)
                    && "on".equals(command.get(3))) {
                failNextSecureProxyEnable = false;
                return new SystemProxyService.CommandResult(1, List.of("Temporary failure"));
            }
            if (blockUpdates && operation.startsWith("-set")) {
                return new SystemProxyService.CommandResult(1, List.of("Permission denied"));
            }
            if ("-listallnetworkservices".equals(operation)) {
                return new SystemProxyService.CommandResult(0, List.of(
                        "An asterisk (*) denotes that a network service is disabled.", "Wi-Fi"
                ));
            }
            if ("-getwebproxy".equals(operation) || "-getsecurewebproxy".equals(operation)) {
                boolean enabled = "-getsecurewebproxy".equals(operation) ? secureProxyEnabled : webProxyEnabled;
                return new SystemProxyService.CommandResult(0, List.of(
                        "Enabled: " + (enabled ? "Yes" : "No"), "Server: 127.0.0.1", "Port: " + proxyPort));
            }
            if ("-getproxyautodiscovery".equals(operation)) {
                return new SystemProxyService.CommandResult(0, List.of("Auto Proxy Discovery: Off"));
            }
            if ("-getautoproxyurl".equals(operation)) {
                return new SystemProxyService.CommandResult(0, List.of("Enabled: No", "URL: "));
            }
            if ("-getproxybypassdomains".equals(operation)) {
                return new SystemProxyService.CommandResult(0, List.of(
                        "There aren't any bypass domains set on Wi-Fi."
                ));
            }
            if (("-setwebproxy".equals(operation) || "-setsecurewebproxy".equals(operation))
                    && rejectDocumentedProxyArguments && command.size() == 8) {
                return new SystemProxyService.CommandResult(1, List.of("** Error: The parameters were not valid."));
            }
            if ("-setwebproxy".equals(operation) || "-setsecurewebproxy".equals(operation)) {
                proxyPort = Integer.parseInt(command.get(4));
            }
            if ("-setwebproxystate".equals(operation)) {
                webProxyEnabled = "on".equals(command.get(3));
            }
            if ("-setsecurewebproxystate".equals(operation)) {
                secureProxyEnabled = "on".equals(command.get(3));
            }
            return new SystemProxyService.CommandResult(0, List.of());
        }
    }
}
