package com.laker.postman.plugin.capture;

import com.laker.postman.plugin.api.PluginStorage;
import com.laker.postman.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
final class SystemProxyService {
    static final String RECOVERY_STORAGE_FILE = "system-proxy-recovery.json";
    private static final String NETWORKSETUP = "/usr/sbin/networksetup";
    private static final String INVALID_PARAMETERS_MARKER = "the parameters were not valid";
    private static final String USAGE_MARKER = "usage:";
    private static final String REG = "reg";
    private static final String CMD = "cmd";
    private static final String POWERSHELL = "powershell";
    private static final String WINDOWS_PROXY_KEY = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
    private static final List<String> REQUIRED_BYPASS_DOMAINS = List.of("localhost", "127.0.0.1", "::1");
    private static final String EMPTY = "Empty";
    private static final String WININET_REFRESH_SCRIPT = """
            $ErrorActionPreference='Stop';
            Add-Type -Namespace EasyPostman -Name WinInet -MemberDefinition '[System.Runtime.InteropServices.DllImport("wininet.dll", SetLastError=true)] public static extern bool InternetSetOption(System.IntPtr hInternet, int dwOption, System.IntPtr lpBuffer, int dwBufferLength);';
            [EasyPostman.WinInet]::InternetSetOption([System.IntPtr]::Zero, 39, [System.IntPtr]::Zero, 0) | Out-Null;
            [EasyPostman.WinInet]::InternetSetOption([System.IntPtr]::Zero, 95, [System.IntPtr]::Zero, 0) | Out-Null;
            [EasyPostman.WinInet]::InternetSetOption([System.IntPtr]::Zero, 37, [System.IntPtr]::Zero, 0) | Out-Null;
            """;

    private final Thread shutdownHook;
    private final CommandRunner commandRunner;
    private final String osName;

    private volatile PluginStorage storage = PluginStorage.noop();
    private volatile Map<String, ProxyServiceSnapshot> snapshots = Map.of();
    private volatile WindowsProxySnapshot windowsSnapshot;
    private volatile boolean active;
    private volatile String activeHost = "";
    private volatile int activePort;

    SystemProxyService() {
        this(new ProcessCommandRunner(), System.getProperty("os.name", ""), true);
    }

    SystemProxyService(CommandRunner commandRunner, String osName, boolean registerShutdownHook) {
        this.commandRunner = commandRunner == null ? new ProcessCommandRunner() : commandRunner;
        this.osName = osName == null ? "" : osName;
        shutdownHook = new Thread(this::restoreQuietly, "capture-system-proxy-restore");
        if (registerShutdownHook) {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
    }

    boolean isSupported() {
        String normalizedOsName = osName.toLowerCase(Locale.ROOT);
        return normalizedOsName.contains("mac") || normalizedOsName.contains("win");
    }

    boolean isActive() {
        return active;
    }

    void configureStorage(PluginStorage storage) {
        this.storage = storage == null ? PluginStorage.noop() : storage;
    }

    synchronized SystemProxyRecoveryResult restorePersistedSnapshotIfOwned() throws Exception {
        if (!isSupported()) {
            return new SystemProxyRecoveryResult(false, false, false, "System proxy recovery is unsupported on this OS");
        }
        if (active) {
            return new SystemProxyRecoveryResult(false, false, false, "System proxy sync is already active");
        }

        Optional<SystemProxyRecoverySnapshot> maybeSnapshot = loadRecoverySnapshot();
        if (maybeSnapshot.isEmpty()) {
            return new SystemProxyRecoveryResult(false, false, false, "No persisted system proxy snapshot");
        }

        SystemProxyRecoverySnapshot snapshot = maybeSnapshot.get();
        if (!isSamePlatform(snapshot.osName())) {
            deleteRecoverySnapshotQuietly();
            return new SystemProxyRecoveryResult(true, false, true, "Persisted proxy snapshot belongs to another OS");
        }
        if (!snapshot.hasProxySnapshot()) {
            deleteRecoverySnapshotQuietly();
            return new SystemProxyRecoveryResult(true, false, true, "Persisted proxy snapshot is incomplete");
        }
        if (!isCurrentProxyOwnedBy(snapshot)) {
            deleteRecoverySnapshotQuietly();
            return new SystemProxyRecoveryResult(true, false, true,
                    "Current system proxy no longer points to the persisted EasyPostman proxy");
        }

        activeHost = normalizeProxyHost(snapshot.activeHost());
        activePort = snapshot.activePort();
        active = true;
        if (isWindows()) {
            windowsSnapshot = snapshot.windowsSnapshot();
            restoreWindowsSnapshot();
        } else {
            snapshots = Map.copyOf(snapshot.macSnapshots());
            restoreSnapshots();
        }
        return new SystemProxyRecoveryResult(true, true, false, "Restored persisted system proxy snapshot");
    }

    synchronized void enable(String host, int port) throws Exception {
        ensureSupported();
        if (isWindows()) {
            enableWindows(host, port);
            return;
        }
        String proxyHost = normalizeProxyHost(host);
        if (active) {
            if (proxyHost.equals(activeHost) && port == activePort) {
                return;
            }
            restoreSnapshots();
        }

        List<String> services = listEnabledNetworkServices();
        if (services.isEmpty()) {
            throw new IllegalStateException("No enabled macOS network services found");
        }

        Map<String, ProxyServiceSnapshot> captured = new LinkedHashMap<>();
        for (String service : services) {
            captured.put(service, readSnapshot(service));
        }
        saveRecoverySnapshotQuietly(proxyHost, port, captured, null);
        try {
            for (Map.Entry<String, ProxyServiceSnapshot> entry : captured.entrySet()) {
                applyProxy(entry.getKey(), proxyHost, port, entry.getValue().bypassDomains());
            }
            snapshots = Map.copyOf(captured);
            activeHost = proxyHost;
            activePort = port;
            active = true;
        } catch (Exception ex) {
            snapshots = captured;
            restoreQuietly();
            throw ex;
        }
    }

    synchronized void disable() throws Exception {
        if (!active) {
            return;
        }
        if (isWindows()) {
            restoreWindowsSnapshot();
            return;
        }
        restoreSnapshots();
    }

    String statusSummary() {
        if (!isSupported()) {
            return "System proxy: unsupported";
        }
        if (!active) {
            return "System proxy: manual";
        }
        return "System proxy: synced to " + activeHost + ":" + activePort;
    }

    private void ensureSupported() {
        if (!isSupported()) {
            throw new IllegalStateException("System proxy sync is only supported on macOS and Windows");
        }
    }

    private synchronized void restoreSnapshots() throws Exception {
        Map<String, ProxyServiceSnapshot> currentSnapshots = snapshots;
        try {
            for (Map.Entry<String, ProxyServiceSnapshot> entry : currentSnapshots.entrySet()) {
                restoreSnapshot(entry.getKey(), entry.getValue());
            }
            deleteRecoverySnapshotQuietly();
        } finally {
            active = false;
            activeHost = "";
            activePort = 0;
            snapshots = Map.of();
        }
    }

    private void restoreQuietly() {
        try {
            if (isWindows()) {
                restoreWindowsSnapshot();
            } else {
                restoreSnapshots();
            }
        } catch (Exception ex) {
            log.warn("Failed to restore system proxy settings", ex);
        }
    }

    private void saveRecoverySnapshotQuietly(
            String host,
            int port,
            Map<String, ProxyServiceSnapshot> macSnapshots,
            WindowsProxySnapshot windowsSnapshot
    ) {
        try {
            storage.writeString(RECOVERY_STORAGE_FILE, recoverySnapshotJson(
                    host,
                    port,
                    macSnapshots == null ? Map.of() : macSnapshots,
                    windowsSnapshot
            ));
        } catch (IOException ex) {
            log.warn("Failed to persist system proxy recovery snapshot", ex);
        }
    }

    private Optional<SystemProxyRecoverySnapshot> loadRecoverySnapshot() {
        try {
            Optional<String> content = storage.readString(RECOVERY_STORAGE_FILE);
            if (content.isEmpty() || content.get().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(recoverySnapshotFromJson(content.get()));
        } catch (Exception ex) {
            log.warn("Failed to load system proxy recovery snapshot", ex);
            deleteRecoverySnapshotQuietly();
            return Optional.empty();
        }
    }

    private void deleteRecoverySnapshotQuietly() {
        try {
            storage.delete(RECOVERY_STORAGE_FILE);
        } catch (IOException ex) {
            log.warn("Failed to delete system proxy recovery snapshot", ex);
        }
    }

    private boolean isSamePlatform(String snapshotOsName) {
        String normalized = snapshotOsName == null ? "" : snapshotOsName.toLowerCase(Locale.ROOT);
        return isWindows() ? normalized.contains("win") : normalized.contains("mac");
    }

    private boolean isCurrentProxyOwnedBy(SystemProxyRecoverySnapshot snapshot) throws Exception {
        String host = normalizeProxyHost(snapshot.activeHost());
        int port = snapshot.activePort();
        if (port <= 0) {
            return false;
        }
        if (isWindows()) {
            WindowsProxySnapshot current = readWindowsSnapshot();
            return isRegistryDwordEnabled(current.proxyEnable())
                    && windowsProxyServerMatches(registryData(current.proxyServer()), host, port);
        }

        for (String service : snapshot.macSnapshots().keySet()) {
            try {
                if (proxyEndpointMatches(readProxyEndpoint(service, false), host, port)
                        || proxyEndpointMatches(readProxyEndpoint(service, true), host, port)) {
                    return true;
                }
            } catch (Exception ex) {
                log.debug("Failed to inspect macOS network service proxy ownership: {}", service, ex);
            }
        }
        return false;
    }

    private boolean proxyEndpointMatches(ProxyEndpoint endpoint, String host, int port) {
        return endpoint != null
                && endpoint.enabled()
                && normalizeProxyHost(endpoint.server()).equals(host)
                && endpoint.port() == port;
    }

    private boolean windowsProxyServerMatches(String proxyServer, String host, int port) {
        if (proxyServer == null || proxyServer.isBlank()) {
            return false;
        }
        String expected = (host + ":" + port).toLowerCase(Locale.ROOT);
        for (String rawToken : proxyServer.split(";")) {
            String token = rawToken.trim();
            int separator = token.indexOf('=');
            String endpoint = separator >= 0 ? token.substring(separator + 1).trim() : token;
            if (expected.equals(endpoint.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String recoverySnapshotJson(
            String host,
            int port,
            Map<String, ProxyServiceSnapshot> macSnapshots,
            WindowsProxySnapshot windowsSnapshot
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("osName", osName);
        root.put("activeHost", host);
        root.put("activePort", port);
        root.put("macSnapshots", proxyServiceSnapshotsToMap(macSnapshots));
        root.put("windowsSnapshot", windowsSnapshot == null ? Map.of() : windowsProxySnapshotToMap(windowsSnapshot));
        return JsonUtil.toJsonPrettyStr(root);
    }

    private SystemProxyRecoverySnapshot recoverySnapshotFromJson(String json) {
        Map<String, Object> root = objectMap(JsonUtil.convertValue(JsonUtil.readTree(json), Map.class));
        return new SystemProxyRecoverySnapshot(
                stringValue(root, "osName", ""),
                stringValue(root, "activeHost", ""),
                intValue(root, "activePort", 0),
                proxyServiceSnapshotsFromMap(objectMap(root.get("macSnapshots"))),
                windowsProxySnapshotFromMap(objectMap(root.get("windowsSnapshot")))
        );
    }

    private Map<String, Object> proxyServiceSnapshotsToMap(Map<String, ProxyServiceSnapshot> snapshots) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, ProxyServiceSnapshot> entry : snapshots.entrySet()) {
            root.put(entry.getKey(), proxyServiceSnapshotToMap(entry.getValue()));
        }
        return root;
    }

    private Map<String, ProxyServiceSnapshot> proxyServiceSnapshotsFromMap(Map<String, Object> root) {
        Map<String, ProxyServiceSnapshot> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            result.put(entry.getKey(), proxyServiceSnapshotFromMap(objectMap(entry.getValue())));
        }
        return result;
    }

    private Map<String, Object> proxyServiceSnapshotToMap(ProxyServiceSnapshot snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("webProxy", proxyEndpointToMap(snapshot.webProxy()));
        root.put("secureWebProxy", proxyEndpointToMap(snapshot.secureWebProxy()));
        root.put("bypassDomains", snapshot.bypassDomains());
        root.put("autoDiscoveryEnabled", snapshot.autoDiscoveryEnabled());
        root.put("autoProxyEnabled", snapshot.autoProxyEnabled());
        root.put("autoProxyUrl", snapshot.autoProxyUrl());
        return root;
    }

    private ProxyServiceSnapshot proxyServiceSnapshotFromMap(Map<String, Object> root) {
        return new ProxyServiceSnapshot(
                proxyEndpointFromMap(objectMap(root.get("webProxy"))),
                proxyEndpointFromMap(objectMap(root.get("secureWebProxy"))),
                stringList(root.get("bypassDomains")),
                booleanValue(root, "autoDiscoveryEnabled", false),
                booleanValue(root, "autoProxyEnabled", false),
                stringValue(root, "autoProxyUrl", "")
        );
    }

    private Map<String, Object> proxyEndpointToMap(ProxyEndpoint endpoint) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("enabled", endpoint.enabled());
        root.put("server", endpoint.server());
        root.put("port", endpoint.port());
        return root;
    }

    private ProxyEndpoint proxyEndpointFromMap(Map<String, Object> root) {
        return new ProxyEndpoint(
                booleanValue(root, "enabled", false),
                stringValue(root, "server", ""),
                intValue(root, "port", 0)
        );
    }

    private Map<String, Object> windowsProxySnapshotToMap(WindowsProxySnapshot snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("proxyEnable", windowsRegistryValueToMap(snapshot.proxyEnable()));
        root.put("proxyServer", windowsRegistryValueToMap(snapshot.proxyServer()));
        root.put("proxyOverride", windowsRegistryValueToMap(snapshot.proxyOverride()));
        root.put("autoConfigUrl", windowsRegistryValueToMap(snapshot.autoConfigUrl()));
        root.put("autoDetect", windowsRegistryValueToMap(snapshot.autoDetect()));
        return root;
    }

    private WindowsProxySnapshot windowsProxySnapshotFromMap(Map<String, Object> root) {
        if (root.isEmpty()) {
            return null;
        }
        return new WindowsProxySnapshot(
                windowsRegistryValueFromMap(objectMap(root.get("proxyEnable"))),
                windowsRegistryValueFromMap(objectMap(root.get("proxyServer"))),
                windowsRegistryValueFromMap(objectMap(root.get("proxyOverride"))),
                windowsRegistryValueFromMap(objectMap(root.get("autoConfigUrl"))),
                windowsRegistryValueFromMap(objectMap(root.get("autoDetect")))
        );
    }

    private Map<String, Object> windowsRegistryValueToMap(WindowsRegistryValue value) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("present", value != null && value.present());
        root.put("type", value == null ? "" : value.type());
        root.put("data", value == null ? "" : value.data());
        return root;
    }

    private WindowsRegistryValue windowsRegistryValueFromMap(Map<String, Object> root) {
        return new WindowsRegistryValue(
                booleanValue(root, "present", false),
                stringValue(root, "type", ""),
                stringValue(root, "data", "")
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String stringValue(Map<String, Object> root, String key, String defaultValue) {
        Object value = root.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private int intValue(Map<String, Object> root, String key, int defaultValue) {
        Object value = root.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean booleanValue(Map<String, Object> root, String key, boolean defaultValue) {
        Object value = root.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private boolean isWindows() {
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private void enableWindows(String host, int port) throws Exception {
        String proxyHost = normalizeProxyHost(host);
        if (active) {
            if (proxyHost.equals(activeHost) && port == activePort) {
                return;
            }
            restoreWindowsSnapshot();
        }

        WindowsProxySnapshot snapshot = readWindowsSnapshot();
        saveRecoverySnapshotQuietly(proxyHost, port, Map.of(), snapshot);
        try {
            applyWindowsProxy(proxyHost, port, snapshot.proxyOverrideData());
            windowsSnapshot = snapshot;
            activeHost = proxyHost;
            activePort = port;
            active = true;
        } catch (Exception ex) {
            windowsSnapshot = snapshot;
            restoreQuietly();
            throw ex;
        }
    }

    private void applyProxy(String service, String host, int port, List<String> originalBypassDomains) throws Exception {
        runCommand(NETWORKSETUP, "-setproxyautodiscovery", service, "off");
        runCommand(NETWORKSETUP, "-setautoproxystate", service, "off");
        setProxyEndpoint(service, false, host, port);
        setProxyEndpoint(service, true, host, port);

        LinkedHashSet<String> bypassDomains = new LinkedHashSet<>(originalBypassDomains);
        bypassDomains.addAll(REQUIRED_BYPASS_DOMAINS);
        setBypassDomains(service, new ArrayList<>(bypassDomains));

        runCommand(NETWORKSETUP, "-setwebproxystate", service, "on");
        runCommand(NETWORKSETUP, "-setsecurewebproxystate", service, "on");
    }

    private void applyWindowsProxy(String host, int port, String originalBypass) throws Exception {
        applyWindowsProxyWithRegistry(host, port, originalBypass);
    }

    private void applyWindowsProxyWithRegistry(String host, int port, String originalBypass) throws Exception {
        writeWindowsRegistryValue("ProxyServer", "REG_SZ", host + ":" + port);
        writeWindowsRegistryValue("ProxyOverride", "REG_SZ", mergeWindowsBypass(originalBypass));
        writeWindowsRegistryValue("ProxyEnable", "REG_DWORD", "1");
        deleteWindowsRegistryValue("AutoConfigURL");
        writeWindowsRegistryValue("AutoDetect", "REG_DWORD", "0");
        refreshWindowsInternetOptions();
        verifyWindowsProxyApplied(host, port);
    }

    private void restoreSnapshot(String service, ProxyServiceSnapshot snapshot) throws Exception {
        restoreProxy(service, false, snapshot.webProxy());
        restoreProxy(service, true, snapshot.secureWebProxy());
        setBypassDomains(service, snapshot.bypassDomains());

        if (snapshot.autoProxyUrl() != null && !snapshot.autoProxyUrl().isBlank()) {
            runCommand(NETWORKSETUP, "-setautoproxyurl", service, snapshot.autoProxyUrl());
        }
        runCommand(NETWORKSETUP, "-setautoproxystate", service, snapshot.autoProxyEnabled() ? "on" : "off");
        runCommand(NETWORKSETUP, "-setproxyautodiscovery", service, snapshot.autoDiscoveryEnabled() ? "on" : "off");
    }

    private void restoreProxy(String service, boolean secure, ProxyEndpoint endpoint) throws Exception {
        if (endpoint.server() != null && !endpoint.server().isBlank() && endpoint.port() > 0) {
            setProxyEndpoint(service, secure, endpoint.server(), endpoint.port());
        }
        runCommand(
                NETWORKSETUP,
                secure ? "-setsecurewebproxystate" : "-setwebproxystate",
                service,
                endpoint.enabled() ? "on" : "off"
        );
    }

    private ProxyServiceSnapshot readSnapshot(String service) throws Exception {
        ProxyEndpoint webProxy = readProxyEndpoint(service, false);
        ProxyEndpoint secureWebProxy = readProxyEndpoint(service, true);
        boolean autoDiscoveryEnabled = parseEnabled(readLines(NETWORKSETUP, "-getproxyautodiscovery", service));
        AutoProxyConfig autoProxyConfig = readAutoProxyConfig(service);
        List<String> bypassDomains = readBypassDomains(service);
        return new ProxyServiceSnapshot(
                webProxy,
                secureWebProxy,
                bypassDomains,
                autoDiscoveryEnabled,
                autoProxyConfig.enabled(),
                autoProxyConfig.url()
        );
    }

    private ProxyEndpoint readProxyEndpoint(String service, boolean secure) throws Exception {
        List<String> lines = readLines(NETWORKSETUP, secure ? "-getsecurewebproxy" : "-getwebproxy", service);
        Map<String, String> values = parseKeyValueLines(lines);
        boolean enabled = "yes".equalsIgnoreCase(values.getOrDefault("Enabled", "No"));
        String server = values.getOrDefault("Server", "");
        int port = parsePort(values.get("Port"));
        return new ProxyEndpoint(enabled, server, port);
    }

    private AutoProxyConfig readAutoProxyConfig(String service) throws Exception {
        Map<String, String> values = parseKeyValueLines(readLines(NETWORKSETUP, "-getautoproxyurl", service));
        boolean enabled = "yes".equalsIgnoreCase(values.getOrDefault("Enabled", "No"));
        return new AutoProxyConfig(enabled, values.getOrDefault("URL", ""));
    }

    private List<String> readBypassDomains(String service) throws Exception {
        List<String> lines = readLines(NETWORKSETUP, "-getproxybypassdomains", service);
        List<String> domains = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.toLowerCase(Locale.ROOT).contains("there aren't any bypass domains")) {
                return List.of();
            }
            domains.add(trimmed);
        }
        return domains;
    }

    private List<String> listEnabledNetworkServices() throws Exception {
        List<String> lines = readLines(NETWORKSETUP, "-listallnetworkservices");
        List<String> services = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("An asterisk")) {
                continue;
            }
            if (trimmed.startsWith("*")) {
                continue;
            }
            services.add(trimmed);
        }
        return services;
    }

    private void setBypassDomains(String service, List<String> domains) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(NETWORKSETUP);
        command.add("-setproxybypassdomains");
        command.add(service);
        if (domains == null || domains.isEmpty()) {
            command.add(EMPTY);
        } else {
            command.addAll(domains);
        }
        runCommand(command);
    }

    /**
     * macOS documents the authenticated/username/password arguments for these
     * commands, but some older releases accepted the shorter three-argument
     * form. Prefer the documented form and retain a narrowly-scoped fallback
     * for those older releases.
     */
    private void setProxyEndpoint(String service, boolean secure, String host, int port) throws Exception {
        String operation = secure ? "-setsecurewebproxy" : "-setwebproxy";
        List<String> documentedCommand = List.of(
                NETWORKSETUP,
                operation,
                service,
                host,
                String.valueOf(port),
                "off",
                "",
                ""
        );
        CommandResult documentedResult = runCommandAllowFailure(documentedCommand);
        if (documentedResult.exitCode() == 0) {
            return;
        }

        if (!isParameterValidationFailure(documentedResult)) {
            throw commandFailure(documentedCommand, documentedResult);
        }

        List<String> legacyCommand = List.of(
                NETWORKSETUP,
                operation,
                service,
                host,
                String.valueOf(port)
        );
        log.warn("macOS networksetup rejected documented proxy arguments; trying legacy form: "
                        + "command={}, exitCode={}, output={}",
                formatCommand(documentedCommand),
                documentedResult.exitCode(),
                formatOutput(documentedResult));
        CommandResult legacyResult = runCommandAllowFailure(legacyCommand);
        if (legacyResult.exitCode() == 0) {
            log.info("Using legacy macOS networksetup proxy argument form for service {}", service);
            return;
        }

        throw commandFailure(legacyCommand, legacyResult);
    }

    private boolean isParameterValidationFailure(CommandResult result) {
        String output = String.join(System.lineSeparator(), result.lines()).toLowerCase(Locale.ROOT);
        return output.contains(INVALID_PARAMETERS_MARKER) || output.contains(USAGE_MARKER);
    }

    private boolean parseEnabled(List<String> lines) {
        Map<String, String> values = parseKeyValueLines(lines);
        return values.values().stream().findFirst()
                .map(value -> "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value))
                .orElse(false);
    }

    private Map<String, String> parseKeyValueLines(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            values.put(key, value);
        }
        return values;
    }

    private WindowsProxySnapshot readWindowsSnapshot() throws Exception {
        return new WindowsProxySnapshot(
                queryWindowsRegistryValue("ProxyEnable"),
                queryWindowsRegistryValue("ProxyServer"),
                queryWindowsRegistryValue("ProxyOverride"),
                queryWindowsRegistryValue("AutoConfigURL"),
                queryWindowsRegistryValue("AutoDetect")
        );
    }

    private synchronized void restoreWindowsSnapshot() throws Exception {
        WindowsProxySnapshot snapshot = windowsSnapshot;
        if (snapshot == null) {
            active = false;
            activeHost = "";
            activePort = 0;
            return;
        }
        try {
            restoreWindowsSnapshotWithRegistry(snapshot);
            deleteRecoverySnapshotQuietly();
        } finally {
            active = false;
            activeHost = "";
            activePort = 0;
            windowsSnapshot = null;
        }
    }

    private void restoreWindowsSnapshotWithRegistry(WindowsProxySnapshot snapshot) throws Exception {
        restoreWindowsValue("ProxyEnable", snapshot.proxyEnable());
        restoreWindowsValue("ProxyServer", snapshot.proxyServer());
        restoreWindowsValue("ProxyOverride", snapshot.proxyOverride());
        restoreWindowsValue("AutoConfigURL", snapshot.autoConfigUrl());
        restoreWindowsValue("AutoDetect", snapshot.autoDetect());
        refreshWindowsInternetOptions();
        verifyWindowsSnapshotRestored(snapshot);
    }

    private void restoreWindowsValue(String name, WindowsRegistryValue value) throws Exception {
        if (value == null || !value.present()) {
            deleteWindowsRegistryValue(name);
            return;
        }
        writeWindowsRegistryValue(name, value.type(), value.data());
    }

    private WindowsRegistryValue queryWindowsRegistryValue(String name) throws Exception {
        CommandResult result = runCommandAllowFailure(List.of(REG, "query", WINDOWS_PROXY_KEY, "/v", name));
        if (result.exitCode() != 0) {
            return new WindowsRegistryValue(false, "", "");
        }
        for (String line : result.lines()) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(name)) {
                continue;
            }
            String remainder = trimmed.substring(name.length()).trim();
            String[] parts = remainder.split("\\s+", 2);
            if (parts.length == 2) {
                return new WindowsRegistryValue(true, parts[0], parts[1].trim());
            }
        }
        return new WindowsRegistryValue(false, "", "");
    }

    private void verifyWindowsProxyApplied(String host, int port) throws Exception {
        WindowsProxySnapshot current = readWindowsSnapshot();
        if (!isRegistryDwordEnabled(current.proxyEnable())) {
            throw new IllegalStateException("Windows system proxy was not enabled after update");
        }
        if (!windowsProxyServerMatches(registryData(current.proxyServer()), host, port)) {
            throw new IllegalStateException("Windows system proxy endpoint was not applied: expected "
                    + host + ":" + port + ", actual " + registryData(current.proxyServer()));
        }
    }

    private void verifyWindowsSnapshotRestored(WindowsProxySnapshot expected) throws Exception {
        WindowsProxySnapshot current = readWindowsSnapshot();
        verifyWindowsRegistryValueRestored("ProxyEnable", expected.proxyEnable(), current.proxyEnable());
        verifyWindowsRegistryValueRestored("ProxyServer", expected.proxyServer(), current.proxyServer());
        verifyWindowsRegistryValueRestored("ProxyOverride", expected.proxyOverride(), current.proxyOverride());
        verifyWindowsRegistryValueRestored("AutoConfigURL", expected.autoConfigUrl(), current.autoConfigUrl());
        verifyWindowsRegistryValueRestored("AutoDetect", expected.autoDetect(), current.autoDetect());
    }

    private void verifyWindowsRegistryValueRestored(
            String name,
            WindowsRegistryValue expected,
            WindowsRegistryValue actual
    ) {
        boolean expectedPresent = expected != null && expected.present();
        boolean actualPresent = actual != null && actual.present();
        if (expectedPresent != actualPresent) {
            throw new IllegalStateException("Windows system proxy value was not restored: "
                    + name + " expected present=" + expectedPresent + ", actual present=" + actualPresent);
        }
        if (!expectedPresent) {
            return;
        }
        if (!expected.type().equalsIgnoreCase(actual.type())
                || !expected.data().equalsIgnoreCase(actual.data())) {
            throw new IllegalStateException("Windows system proxy value was not restored: "
                    + name + " expected " + expected.type() + " " + expected.data()
                    + ", actual " + actual.type() + " " + actual.data());
        }
    }

    private void writeWindowsRegistryValue(String name, String type, String data) throws Exception {
        runCommand(REG, "add", WINDOWS_PROXY_KEY, "/v", name, "/t", type, "/d", data, "/f");
    }

    private void deleteWindowsRegistryValue(String name) throws Exception {
        runCommandAllowFailure(List.of(REG, "delete", WINDOWS_PROXY_KEY, "/v", name, "/f"));
    }

    private String mergeWindowsBypass(String originalBypass) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (originalBypass != null && !originalBypass.isBlank()) {
            for (String value : originalBypass.split(";")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
        }
        values.add("localhost");
        values.add("127.0.0.1");
        values.add("::1");
        values.add("<local>");
        return String.join(";", values);
    }

    private boolean isRegistryDwordEnabled(WindowsRegistryValue value) {
        if (value == null || !value.present() || value.data() == null || value.data().isBlank()) {
            return false;
        }
        String data = value.data().trim();
        try {
            return Integer.decode(data) != 0;
        } catch (NumberFormatException ex) {
            return !"0".equals(data);
        }
    }

    private String registryData(WindowsRegistryValue value) {
        if (value == null || !value.present() || value.data() == null) {
            return "";
        }
        return value.data();
    }

    private void refreshWindowsInternetOptions() throws Exception {
        try {
            runCommandAllowFailure(List.of(
                    POWERSHELL,
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    WININET_REFRESH_SCRIPT
            ));
        } catch (Exception ex) {
            log.debug("Failed to notify WinINet proxy settings refresh", ex);
        }
        try {
            runCommandAllowFailure(List.of(CMD, "/c", "RUNDLL32.EXE", "USER32.DLL,UpdatePerUserSystemParameters", "1", "True"));
        } catch (Exception ex) {
            log.debug("Failed to run legacy Windows proxy settings refresh", ex);
        }
    }

    private int parsePort(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<String> readLines(String... command) throws Exception {
        return readLines(List.of(command));
    }

    private List<String> readLines(List<String> command) throws Exception {
        return runCommand(command).lines();
    }

    private CommandResult runCommand(String... command) throws Exception {
        return runCommand(List.of(command));
    }

    private CommandResult runCommand(List<String> command) throws Exception {
        CommandResult result = runCommandAllowFailure(command);
        if (result.exitCode() != 0) {
            throw commandFailure(command, result);
        }
        return result;
    }

    private IllegalStateException commandFailure(List<String> command, CommandResult result) {
        IllegalStateException failure = new IllegalStateException(
                "Command failed with exit code " + result.exitCode() + ": " + formatOutput(result)
        );
        log.error("External command failed: command={}, exitCode={}, output={}",
                formatCommand(command), result.exitCode(), formatOutput(result), failure);
        return failure;
    }

    private String formatCommand(List<String> command) {
        return command.stream()
                .map(this::formatCommandArgument)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private String formatCommandArgument(String argument) {
        if (argument == null) {
            return "<null>";
        }
        String value = argument.length() > 240 ? argument.substring(0, 240) + "..." : argument;
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private String formatOutput(CommandResult result) {
        if (result.lines() == null || result.lines().isEmpty()) {
            return "<no output>";
        }
        String output = String.join(" | ", result.lines()).trim();
        return output.length() > 2_000 ? output.substring(0, 2_000) + "..." : output;
    }

    private CommandResult runCommandAllowFailure(List<String> command) throws Exception {
        return commandRunner.run(command);
    }

    private String normalizeProxyHost(String host) {
        if (host == null || host.isBlank()) {
            return "127.0.0.1";
        }
        String trimmed = host.trim();
        if ("0.0.0.0".equals(trimmed) || "::".equals(trimmed) || "::0".equals(trimmed)) {
            return "127.0.0.1";
        }
        return trimmed;
    }

    @FunctionalInterface
    interface CommandRunner {
        CommandResult run(List<String> command) throws Exception;
    }

    record CommandResult(int exitCode, List<String> lines) {
    }

    record SystemProxyRecoveryResult(boolean attempted, boolean restored, boolean stale, String detail) {
    }

    private record SystemProxyRecoverySnapshot(
            String osName,
            String activeHost,
            int activePort,
            Map<String, ProxyServiceSnapshot> macSnapshots,
            WindowsProxySnapshot windowsSnapshot
    ) {
        boolean hasProxySnapshot() {
            return windowsSnapshot != null || !macSnapshots.isEmpty();
        }
    }

    private static final class ProcessCommandRunner implements CommandRunner {
        @Override
        public CommandResult run(List<String> command) throws Exception {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, lines);
        }
    }

    private record ProxyServiceSnapshot(
            ProxyEndpoint webProxy,
            ProxyEndpoint secureWebProxy,
            List<String> bypassDomains,
            boolean autoDiscoveryEnabled,
            boolean autoProxyEnabled,
            String autoProxyUrl
    ) {
    }

    private record ProxyEndpoint(boolean enabled, String server, int port) {
    }

    private record AutoProxyConfig(boolean enabled, String url) {
    }

    private record WindowsRegistryValue(boolean present, String type, String data) {
    }

    private record WindowsProxySnapshot(
            WindowsRegistryValue proxyEnable,
            WindowsRegistryValue proxyServer,
            WindowsRegistryValue proxyOverride,
            WindowsRegistryValue autoConfigUrl,
            WindowsRegistryValue autoDetect
    ) {
        String proxyOverrideData() {
            return proxyOverride.present() ? proxyOverride.data() : "";
        }
    }
}
