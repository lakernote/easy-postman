package com.laker.postman.plugin.capture;

import com.laker.postman.plugin.api.PluginStorage;
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
                "/v", "ProxyServer", "/t", "REG_SZ", "/d", "127.0.0.1:8888", "/f"
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
                "/v", "ProxyServer", "/t", "REG_SZ", "/d", "127.0.0.1:8888", "/f"
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

        assertTrue(exception.getMessage().contains("Windows system proxy was not enabled"));
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
    public void shouldRecoverPersistedWindowsProxySnapshotWhenCurrentProxyIsOwned() throws Exception {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        SystemProxyService original = new SystemProxyService(new WindowsRegistryCommandRunner(), "Windows 11", false);
        original.configureStorage(storage);

        original.enable("127.0.0.1", 8888);

        assertTrue(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE));

        WindowsRegistryCommandRunner recoveryRunner = new WindowsRegistryCommandRunner(Map.of(
                "ProxyEnable", new RegistryValue("REG_DWORD", "0x1"),
                "ProxyServer", new RegistryValue("REG_SZ", "127.0.0.1:8888")
        ));
        SystemProxyService recovered = new SystemProxyService(recoveryRunner, "Windows 11", false);
        recovered.configureStorage(storage);

        SystemProxyService.SystemProxyRecoveryResult result = recovered.restorePersistedSnapshotIfOwned();

        assertTrue(result.attempted());
        assertTrue(result.restored());
        assertFalse(result.stale());
        assertFalse(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE));
        assertTrue(recoveryRunner.commands().stream().anyMatch(command -> command.equals(List.of(
                "reg",
                "delete",
                "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v",
                "ProxyEnable",
                "/f"
        ))), "Persisted Windows proxy snapshot should be restored through registry values");
    }

    @Test
    public void shouldDropPersistedWindowsProxySnapshotWhenCurrentProxyWasChangedByUser() throws Exception {
        MemoryPluginStorage storage = new MemoryPluginStorage();
        SystemProxyService original = new SystemProxyService(new WindowsRegistryCommandRunner(), "Windows 11", false);
        original.configureStorage(storage);
        original.enable("127.0.0.1", 8888);

        WindowsRegistryCommandRunner recoveryRunner = new WindowsRegistryCommandRunner(Map.of(
                "ProxyEnable", new RegistryValue("REG_DWORD", "0x1"),
                "ProxyServer", new RegistryValue("REG_SZ", "127.0.0.1:9000")
        ));
        SystemProxyService recovered = new SystemProxyService(recoveryRunner, "Windows 11", false);
        recovered.configureStorage(storage);

        SystemProxyService.SystemProxyRecoveryResult result = recovered.restorePersistedSnapshotIfOwned();

        assertTrue(result.attempted());
        assertFalse(result.restored());
        assertTrue(result.stale());
        assertFalse(storage.files.containsKey(SystemProxyService.RECOVERY_STORAGE_FILE));
        assertFalse(recoveryRunner.commands().stream().anyMatch(command -> command.size() > 1
                        && "reg".equals(command.get(0))
                        && ("add".equals(command.get(1)) || "delete".equals(command.get(1)))),
                "Recovery should not restore a snapshot after the user changes the system proxy");
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

    private static boolean readBooleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static Object readObjectField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class MemoryPluginStorage implements PluginStorage {
        private final Map<String, String> files = new HashMap<>();

        @Override
        public Optional<String> readString(String relativePath) {
            return Optional.ofNullable(files.get(relativePath));
        }

        @Override
        public void writeString(String relativePath, String content) {
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

        private MacNetworkSetupCommandRunner(boolean rejectDocumentedProxyArguments) {
            this.rejectDocumentedProxyArguments = rejectDocumentedProxyArguments;
        }

        private List<List<String>> commands() {
            return commands;
        }

        @Override
        public SystemProxyService.CommandResult run(List<String> command) {
            commands.add(command);
            String operation = command.size() > 1 ? command.get(1) : "";
            if ("-listallnetworkservices".equals(operation)) {
                return new SystemProxyService.CommandResult(0, List.of(
                        "An asterisk (*) denotes that a network service is disabled.", "Wi-Fi"
                ));
            }
            if ("-getwebproxy".equals(operation) || "-getsecurewebproxy".equals(operation)) {
                return new SystemProxyService.CommandResult(0, List.of("Enabled: No", "Server: ", "Port: 0"));
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
            return new SystemProxyService.CommandResult(0, List.of());
        }
    }
}
