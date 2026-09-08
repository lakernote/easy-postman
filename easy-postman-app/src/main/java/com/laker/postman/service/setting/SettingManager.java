package com.laker.postman.service.setting;

import com.laker.postman.common.constants.ConfigPathConstants;
import com.laker.postman.certificate.TrustedCertificateEntry;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.model.NotificationPosition;
import com.laker.postman.platform.update.model.UpdateCheckFrequency;
import com.laker.postman.platform.update.model.UpdatePolicy;
import com.laker.postman.platform.update.model.UpdateTarget;
import com.laker.postman.settings.PreferencesStore;
import com.laker.postman.settings.SettingKey;
import com.laker.postman.service.sync.WebDavSyncSettings;
import com.laker.postman.common.component.notification.NotificationCenter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@UtilityClass
public class SettingManager {
    private static final String CONFIG_FILE = ConfigPathConstants.EASY_POSTMAN_SETTINGS;
    private static final Object SETTINGS_IO_LOCK = new Object();
    public static final int DEFAULT_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB =
            AppSettingKeys.DEFAULT_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB;
    public static final int MIN_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB =
            AppSettingKeys.MIN_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB;
    public static final int MAX_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB =
            AppSettingKeys.MAX_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB;
    public static final int DEFAULT_PERFORMANCE_RESULT_ROW_LIMIT =
            AppSettingKeys.DEFAULT_PERFORMANCE_RESULT_ROW_LIMIT;
    public static final int MIN_PERFORMANCE_RESULT_ROW_LIMIT =
            AppSettingKeys.MIN_PERFORMANCE_RESULT_ROW_LIMIT;
    public static final int MAX_PERFORMANCE_RESULT_ROW_LIMIT =
            AppSettingKeys.MAX_PERFORMANCE_RESULT_ROW_LIMIT;
    public static final int DEFAULT_GIT_DIFF_LARGE_FILE_THRESHOLD_MB =
            AppSettingKeys.DEFAULT_GIT_DIFF_LARGE_FILE_THRESHOLD_MB;
    public static final int MIN_GIT_DIFF_LARGE_FILE_THRESHOLD_MB =
            AppSettingKeys.MIN_GIT_DIFF_LARGE_FILE_THRESHOLD_MB;
    public static final int MAX_GIT_DIFF_LARGE_FILE_THRESHOLD_MB =
            AppSettingKeys.MAX_GIT_DIFF_LARGE_FILE_THRESHOLD_MB;
    public static final String PROXY_MODE_MANUAL = "MANUAL";
    public static final String PROXY_MODE_SYSTEM = "SYSTEM";
    public static final String PROXY_TYPE_HTTP = "HTTP";
    public static final String PROXY_TYPE_SOCKS = "SOCKS";
    public static final String REQUEST_EDITOR_TAB_DOCS = "DOCS";
    public static final String REQUEST_EDITOR_TAB_PARAMS = "PARAMS";
    public static final String REQUEST_EDITOR_TAB_AUTH = "AUTH";
    public static final String REQUEST_EDITOR_TAB_HEADERS = "HEADERS";
    public static final String REQUEST_EDITOR_TAB_BODY = "BODY";
    public static final String REQUEST_EDITOR_TAB_SCRIPTS = "SCRIPTS";
    public static final String REQUEST_EDITOR_TAB_SETTINGS = "SETTINGS";
    private static final Properties props = new Properties();
    private static final PreferencesStore SETTINGS_STORE = PreferencesStore.backedBy(
            Path.of(CONFIG_FILE),
            props,
            SETTINGS_IO_LOCK
    );

    static {
        load();
        initializeNotificationPosition();
    }

    public static void load() {
        SETTINGS_STORE.loadAndMigrate(AppSettingsMigrations.migrations());
    }

    /**
     * 初始化通知位置设置
     */
    private static void initializeNotificationPosition() {
        try {
            NotificationPosition position = getNotificationPosition();
            NotificationCenter.setDefaultPosition(position);
        } catch (Exception e) {
            // 如果解析失败，使用默认值
            log.error("Error initializing notification position", e);
        }
    }

    public static void save() {
        SETTINGS_STORE.save();
    }

    static Properties saveProperty(File configFile, String key, String value) {
        synchronized (SETTINGS_IO_LOCK) {
            Properties merged = loadProperties(configFile);
            if (value == null) {
                merged.remove(key);
            } else {
                merged.setProperty(key, value);
            }
            storeProperties(merged, configFile);
            return merged;
        }
    }

    private static void setAndSaveProperty(String key, String value) {
        updateAndSaveProperties(settings -> applyProperty(settings, key, value));
    }

    private static void updateAndSaveProperties(Consumer<Properties> updater) {
        SETTINGS_STORE.updateAndSave(updater);
    }

    private static <T> T get(SettingKey<T> key) {
        return SETTINGS_STORE.get(key);
    }

    private static <T> void put(SettingKey<T> key, T value) {
        SETTINGS_STORE.put(key, value);
    }

    private static boolean contains(SettingKey<?> key) {
        return SETTINGS_STORE.contains(key);
    }

    private static void applyProperty(Properties settings, String key, String value) {
        if (value == null) {
            settings.remove(key);
        } else {
            settings.setProperty(key, value);
        }
    }

    private static Properties loadProperties(File file) {
        return PreferencesStore.loadProperties(file == null ? null : file.toPath());
    }

    private static void storeProperties(Properties properties, File file) {
        PreferencesStore.storeProperties(properties, file == null ? null : file.toPath());
    }

    public static int getMaxBodySize() {
        return get(AppSettingKeys.MAX_BODY_SIZE);
    }

    public static void setMaxBodySize(int size) {
        put(AppSettingKeys.MAX_BODY_SIZE, size);
    }

    public static int getRequestTimeout() {
        return get(AppSettingKeys.REQUEST_TIMEOUT);
    }

    public static void setRequestTimeout(int timeout) {
        put(AppSettingKeys.REQUEST_TIMEOUT, timeout);
    }

    public static int getMaxDownloadSize() {
        return get(AppSettingKeys.MAX_DOWNLOAD_SIZE);
    }

    public static void setMaxDownloadSize(int size) {
        put(AppSettingKeys.MAX_DOWNLOAD_SIZE, size);
    }

    public static int getPerformanceMaxIdleConnections() {
        return get(AppSettingKeys.PERFORMANCE_MAX_IDLE_CONNECTIONS);
    }

    public static void setPerformanceMaxIdleConnections(int maxIdle) {
        put(AppSettingKeys.PERFORMANCE_MAX_IDLE_CONNECTIONS, maxIdle);
    }

    public static long getPerformanceKeepAliveSeconds() {
        return get(AppSettingKeys.PERFORMANCE_KEEP_ALIVE_SECONDS);
    }

    public static void setPerformanceKeepAliveSeconds(long seconds) {
        put(AppSettingKeys.PERFORMANCE_KEEP_ALIVE_SECONDS, seconds);
    }

    public static int getPerformanceMaxRequests() {
        return get(AppSettingKeys.PERFORMANCE_MAX_REQUESTS);
    }

    public static void setPerformanceMaxRequests(int maxRequests) {
        put(AppSettingKeys.PERFORMANCE_MAX_REQUESTS, maxRequests);
    }

    public static int getPerformanceMaxRequestsPerHost() {
        return get(AppSettingKeys.PERFORMANCE_MAX_REQUESTS_PER_HOST);
    }

    public static void setPerformanceMaxRequestsPerHost(int maxRequestsPerHost) {
        put(AppSettingKeys.PERFORMANCE_MAX_REQUESTS_PER_HOST, maxRequestsPerHost);
    }

    public static int getDefaultPerformanceJsContextPoolSize() {
        return AppSettingKeys.defaultPerformanceJsContextPoolSize();
    }

    public static int getPerformanceJsContextPoolSize() {
        return get(AppSettingKeys.PERFORMANCE_JS_CONTEXT_POOL_SIZE);
    }

    public static void setPerformanceJsContextPoolSize(int poolSize) {
        put(AppSettingKeys.PERFORMANCE_JS_CONTEXT_POOL_SIZE, poolSize);
    }

    public static int getPerformanceJsContextAcquireTimeoutMs() {
        return get(AppSettingKeys.PERFORMANCE_JS_CONTEXT_ACQUIRE_TIMEOUT_MS);
    }

    public static void setPerformanceJsContextAcquireTimeoutMs(int timeoutMs) {
        put(AppSettingKeys.PERFORMANCE_JS_CONTEXT_ACQUIRE_TIMEOUT_MS, timeoutMs);
    }

    public static int getPerformanceSlowRequestThreshold() {
        return get(AppSettingKeys.PERFORMANCE_SLOW_REQUEST_THRESHOLD);
    }

    public static void setPerformanceSlowRequestThreshold(int thresholdMs) {
        put(AppSettingKeys.PERFORMANCE_SLOW_REQUEST_THRESHOLD, thresholdMs);
    }

    public static int getTrendSamplingIntervalSeconds() {
        return get(AppSettingKeys.TREND_SAMPLING_INTERVAL_SECONDS);
    }

    public static void setTrendSamplingIntervalSeconds(int seconds) {
        put(AppSettingKeys.TREND_SAMPLING_INTERVAL_SECONDS, seconds);
    }

    public static boolean isPerformanceEventLoggingEnabled() {
        return get(AppSettingKeys.PERFORMANCE_EVENT_LOGGING_ENABLED);
    }

    public static void setPerformanceEventLoggingEnabled(boolean enabled) {
        put(AppSettingKeys.PERFORMANCE_EVENT_LOGGING_ENABLED, enabled);
    }

    public static int getPerformanceResponseBodyPreviewLimitKb() {
        return get(AppSettingKeys.PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB);
    }

    public static void setPerformanceResponseBodyPreviewLimitKb(int limitKb) {
        put(AppSettingKeys.PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB, limitKb);
    }

    public static int sanitizePerformanceResponseBodyPreviewLimitKb(Integer limitKb) {
        return AppSettingKeys.sanitizePerformanceResponseBodyPreviewLimitKb(limitKb);
    }

    public static int performanceResponseBodyPreviewLimitBytes(int limitKb) {
        return sanitizePerformanceResponseBodyPreviewLimitKb(limitKb) * 1024;
    }

    public static int getPerformanceResultRowLimit() {
        return get(AppSettingKeys.PERFORMANCE_RESULT_ROW_LIMIT);
    }

    public static void setPerformanceResultRowLimit(int rowLimit) {
        put(AppSettingKeys.PERFORMANCE_RESULT_ROW_LIMIT, rowLimit);
    }

    public static int sanitizePerformanceResultRowLimit(Integer rowLimit) {
        return AppSettingKeys.sanitizePerformanceResultRowLimit(rowLimit);
    }

    public static int getGitDiffLargeFileThresholdMb() {
        return get(AppSettingKeys.GIT_DIFF_LARGE_FILE_THRESHOLD_MB);
    }

    public static void setGitDiffLargeFileThresholdMb(int thresholdMb) {
        put(AppSettingKeys.GIT_DIFF_LARGE_FILE_THRESHOLD_MB, thresholdMb);
    }

    public static int sanitizeGitDiffLargeFileThresholdMb(Integer thresholdMb) {
        return AppSettingKeys.sanitizeGitDiffLargeFileThresholdMb(thresholdMb);
    }

    public static long gitDiffLargeFileThresholdBytes(int thresholdMb) {
        return sanitizeGitDiffLargeFileThresholdMb(thresholdMb) * 1024L * 1024L;
    }

    public static long getGitDiffLargeFileThresholdBytes() {
        return gitDiffLargeFileThresholdBytes(getGitDiffLargeFileThresholdMb());
    }

    public static String getCsvLastImportDirectory() {
        return get(AppSettingKeys.CSV_LAST_IMPORT_DIRECTORY);
    }

    public static void setCsvLastImportDirectory(String directory) {
        String normalized = directory == null ? "" : directory.trim();
        put(AppSettingKeys.CSV_LAST_IMPORT_DIRECTORY, normalized.isEmpty() ? null : normalized);
    }

    public static boolean isShowDownloadProgressDialog() {
        return get(AppSettingKeys.SHOW_DOWNLOAD_PROGRESS_DIALOG);
    }

    public static void setShowDownloadProgressDialog(boolean show) {
        put(AppSettingKeys.SHOW_DOWNLOAD_PROGRESS_DIALOG, show);
    }

    public static int getDownloadProgressDialogThreshold() {
        return get(AppSettingKeys.DOWNLOAD_PROGRESS_DIALOG_THRESHOLD);
    }

    public static void setDownloadProgressDialogThreshold(int threshold) {
        put(AppSettingKeys.DOWNLOAD_PROGRESS_DIALOG_THRESHOLD, threshold);
    }

    public static boolean isFollowRedirects() {
        return get(AppSettingKeys.FOLLOW_REDIRECTS);
    }

    public static void setFollowRedirects(boolean follow) {
        put(AppSettingKeys.FOLLOW_REDIRECTS, follow);
    }

    /**
     * 是否禁用 SSL 证书验证（通用请求设置）
     * 此设置应用于所有 HTTPS 请求，用于开发测试环境
     */
    public static boolean isRequestSslVerificationDisabled() {
        return !get(AppSettingKeys.REQUEST_SSL_VERIFICATION_ENABLED);
    }

    public static void setRequestSslVerificationDisabled(boolean disabled) {
        put(AppSettingKeys.REQUEST_SSL_VERIFICATION_ENABLED, !disabled);
        // 清除客户端缓存以应用新的 SSL 设置
        OkHttpClientManager.clearClientCache();
    }

    /**
     * 获取默认协议（当URL没有协议时自动补全）
     * 支持的值：http, https
     */
    public static String getDefaultProtocol() {
        return get(AppSettingKeys.DEFAULT_PROTOCOL);
    }

    public static void setDefaultProtocol(String protocol) {
        if (protocol != null && (protocol.equals("http") || protocol.equals("https"))) {
            put(AppSettingKeys.DEFAULT_PROTOCOL, protocol);
        }
    }

    public static Set<String> getHiddenRequestEditorTabs() {
        return new LinkedHashSet<>(get(AppSettingKeys.REQUEST_EDITOR_HIDDEN_TABS));
    }

    public static void setHiddenRequestEditorTabs(Collection<String> hiddenTabs) {
        if (hiddenTabs == null || hiddenTabs.isEmpty()) {
            put(AppSettingKeys.REQUEST_EDITOR_HIDDEN_TABS, null);
            return;
        }

        Set<String> normalizedTabs = new LinkedHashSet<>();
        for (String hiddenTab : hiddenTabs) {
            String normalized = normalizeRequestEditorTabId(hiddenTab);
            if (!normalized.isEmpty()) {
                normalizedTabs.add(normalized);
            }
        }
        put(AppSettingKeys.REQUEST_EDITOR_HIDDEN_TABS, normalizedTabs.isEmpty() ? null : normalizedTabs);
    }

    public static boolean isRequestEditorTabVisible(String tabId) {
        String normalized = normalizeRequestEditorTabId(tabId);
        return normalized.isEmpty() || !getHiddenRequestEditorTabs().contains(normalized);
    }

    public static boolean isRequestEditorTabsMultiLineEnabled() {
        return get(AppSettingKeys.REQUEST_EDITOR_TABS_MULTILINE);
    }

    public static void setRequestEditorTabsMultiLineEnabled(boolean enabled) {
        put(AppSettingKeys.REQUEST_EDITOR_TABS_MULTILINE, enabled);
    }

    private static String normalizeRequestEditorTabId(String tabId) {
        return tabId == null ? "" : tabId.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isRemoteJsRequireEnabled() {
        return get(AppSettingKeys.REMOTE_JS_REQUIRE_ENABLED);
    }

    public static void setRemoteJsRequireEnabled(boolean enabled) {
        put(AppSettingKeys.REMOTE_JS_REQUIRE_ENABLED, enabled);
    }

    public static boolean isInsecureRemoteJsRequireEnabled() {
        return get(AppSettingKeys.REMOTE_JS_REQUIRE_ALLOW_HTTP);
    }

    public static void setInsecureRemoteJsRequireEnabled(boolean enabled) {
        put(AppSettingKeys.REMOTE_JS_REQUIRE_ALLOW_HTTP, enabled);
    }

    public static String getRemoteJsRequireAllowedHosts() {
        return get(AppSettingKeys.REMOTE_JS_REQUIRE_ALLOWED_HOSTS);
    }

    public static void setRemoteJsRequireAllowedHosts(String hosts) {
        put(AppSettingKeys.REMOTE_JS_REQUIRE_ALLOWED_HOSTS, hosts != null ? hosts : "");
    }

    public static int getRemoteJsRequireConnectTimeoutMs() {
        return get(AppSettingKeys.REMOTE_JS_REQUIRE_CONNECT_TIMEOUT_MS);
    }

    public static void setRemoteJsRequireConnectTimeoutMs(int timeoutMs) {
        put(AppSettingKeys.REMOTE_JS_REQUIRE_CONNECT_TIMEOUT_MS_TO_WRITE, timeoutMs);
    }

    public static int getRemoteJsRequireReadTimeoutMs() {
        return get(AppSettingKeys.REMOTE_JS_REQUIRE_READ_TIMEOUT_MS);
    }

    public static void setRemoteJsRequireReadTimeoutMs(int timeoutMs) {
        put(AppSettingKeys.REMOTE_JS_REQUIRE_READ_TIMEOUT_MS_TO_WRITE, timeoutMs);
    }

    public static int getRemoteJsRequireMaxBytes() {
        return get(AppSettingKeys.REMOTE_JS_REQUIRE_MAX_BYTES);
    }

    public static void setRemoteJsRequireMaxBytes(int maxBytes) {
        put(AppSettingKeys.REMOTE_JS_REQUIRE_MAX_BYTES_TO_WRITE, maxBytes);
    }

    /**
     * 是否启用自定义受信任证书或 truststore。
     */
    public static boolean isCustomTrustMaterialEnabled() {
        return get(AppSettingKeys.CUSTOM_TRUST_MATERIAL_ENABLED);
    }

    public static void setCustomTrustMaterialEnabled(boolean enabled) {
        put(AppSettingKeys.CUSTOM_TRUST_MATERIAL_ENABLED, enabled);
        OkHttpClientManager.clearClientCache();
    }

    public static List<TrustedCertificateEntry> getCustomTrustMaterialEntries() {
        return new ArrayList<>(get(AppSettingKeys.CUSTOM_TRUST_MATERIAL_ENTRIES));
    }

    public static void setCustomTrustMaterialEntries(List<TrustedCertificateEntry> entries) {
        List<TrustedCertificateEntry> sanitizedEntries = sanitizeTrustedCertificateEntries(entries);
        put(AppSettingKeys.CUSTOM_TRUST_MATERIAL_ENTRIES, sanitizedEntries.isEmpty() ? null : sanitizedEntries);
        OkHttpClientManager.clearClientCache();
    }

    private static List<TrustedCertificateEntry> sanitizeTrustedCertificateEntries(List<TrustedCertificateEntry> entries) {
        return AppSettingKeys.sanitizeTrustedCertificateEntries(entries);
    }

    public static int getMaxHistoryCount() {
        return get(AppSettingKeys.MAX_HISTORY_COUNT);
    }

    public static void setMaxHistoryCount(int count) {
        put(AppSettingKeys.MAX_HISTORY_COUNT, count);
    }

    public static int getMaxOpenedRequestsCount() {
        return get(AppSettingKeys.MAX_OPENED_REQUESTS_COUNT);
    }

    public static void setMaxOpenedRequestsCount(int count) {
        put(AppSettingKeys.MAX_OPENED_REQUESTS_COUNT, count);
    }

    /**
     * 是否根据响应类型自动格式化响应体
     */
    public static boolean isAutoFormatResponse() {
        return get(AppSettingKeys.AUTO_FORMAT_RESPONSE);
    }

    public static void setAutoFormatResponse(boolean autoFormat) {
        put(AppSettingKeys.AUTO_FORMAT_RESPONSE, autoFormat);
    }

    /**
     * 是否在启动时显示欢迎画面
     */
    public static boolean isStartupSplashEnabled() {
        return get(AppSettingKeys.STARTUP_SPLASH_ENABLED);
    }

    public static void setStartupSplashEnabled(boolean enabled) {
        put(AppSettingKeys.STARTUP_SPLASH_ENABLED, enabled);
    }

    /**
     * 是否默认展开侧边栏
     */
    public static boolean isSidebarExpanded() {
        return get(AppSettingKeys.SIDEBAR_EXPANDED);
    }

    public static void setSidebarExpanded(boolean expanded) {
        put(AppSettingKeys.SIDEBAR_EXPANDED, expanded);
    }

    public static List<String> getSidebarTabOrder() {
        return new ArrayList<>(get(AppSettingKeys.SIDEBAR_TAB_ORDER));
    }

    public static void setSidebarTabOrder(Collection<String> tabOrder) {
        put(AppSettingKeys.SIDEBAR_TAB_ORDER, tabOrder == null || tabOrder.isEmpty()
                ? null
                : new ArrayList<>(tabOrder));
    }

    public static Set<String> getHiddenSidebarTabs() {
        return new LinkedHashSet<>(get(AppSettingKeys.SIDEBAR_HIDDEN_TABS));
    }

    public static void setHiddenSidebarTabs(Collection<String> hiddenTabs) {
        put(AppSettingKeys.SIDEBAR_HIDDEN_TABS, hiddenTabs == null || hiddenTabs.isEmpty()
                ? null
                : new LinkedHashSet<>(hiddenTabs));
    }

    /**
     * 获取布局方向（true=垂直，false=水平）
     */
    public static boolean isLayoutVertical() {
        return get(AppSettingKeys.LAYOUT_VERTICAL);
    }

    public static void setLayoutVertical(boolean vertical) {
        put(AppSettingKeys.LAYOUT_VERTICAL, vertical);
    }

    /**
     * 获取通知位置
     */
    public static NotificationPosition getNotificationPosition() {
        return get(AppSettingKeys.NOTIFICATION_POSITION);
    }

    public static void setNotificationPosition(NotificationPosition position) {
        put(AppSettingKeys.NOTIFICATION_POSITION, Objects.requireNonNull(position, "position"));
    }

    // ===== 自动更新设置 =====

    /**
     * 是否启用自动检查更新
     */
    public static boolean isAutoUpdateCheckEnabled() {
        return get(AppSettingKeys.AUTO_UPDATE_CHECK_ENABLED);
    }

    public static void setAutoUpdateCheckEnabled(boolean enabled) {
        put(AppSettingKeys.AUTO_UPDATE_CHECK_ENABLED, enabled);
    }

    /**
     * 获取更新检查频率
     * 支持的值：startup（每次启动）、daily（每日）、weekly（每周）、monthly（每月）
     */
    public static String getAutoUpdateCheckFrequency() {
        return get(AppSettingKeys.AUTO_UPDATE_CHECK_FREQUENCY);
    }

    public static void setAutoUpdateCheckFrequency(String frequency) {
        String normalized = AppSettingKeys.normalizedLowerCode(frequency);
        if (AppSettingKeys.isSupportedAutoUpdateFrequency(normalized)) {
            put(AppSettingKeys.AUTO_UPDATE_CHECK_FREQUENCY, normalized);
        }
    }

    public static UpdatePolicy getAppUpdatePolicy() {
        return new UpdatePolicy(
                UpdateTarget.APP,
                isAutoUpdateCheckEnabled(),
                UpdateCheckFrequency.fromCode(getAutoUpdateCheckFrequency())
        );
    }

    public static UpdatePolicy getPluginUpdatePolicy() {
        boolean enabled = contains(AppSettingKeys.PLUGIN_UPDATE_CHECK_ENABLED)
                ? get(AppSettingKeys.PLUGIN_UPDATE_CHECK_ENABLED)
                : isAutoUpdateCheckEnabled();
        String frequencyCode = contains(AppSettingKeys.PLUGIN_UPDATE_CHECK_FREQUENCY)
                ? get(AppSettingKeys.PLUGIN_UPDATE_CHECK_FREQUENCY)
                : getAutoUpdateCheckFrequency();
        return new UpdatePolicy(
                UpdateTarget.PLUGIN,
                enabled,
                UpdateCheckFrequency.fromCode(frequencyCode)
        );
    }

    public static void setPluginUpdateCheckEnabled(boolean enabled) {
        put(AppSettingKeys.PLUGIN_UPDATE_CHECK_ENABLED, enabled);
    }

    public static void setPluginUpdateCheckFrequency(String frequency) {
        String normalized = AppSettingKeys.normalizedLowerCode(frequency);
        if (AppSettingKeys.isSupportedAutoUpdateFrequency(normalized)) {
            put(AppSettingKeys.PLUGIN_UPDATE_CHECK_FREQUENCY, normalized);
        }
    }

    /**
     * 获取上次检查更新的时间戳（毫秒）
     */
    public static long getLastUpdateCheckTime() {
        return get(AppSettingKeys.LAST_UPDATE_CHECK_TIME);
    }

    /**
     * 设置上次检查更新的时间戳（毫秒）
     */
    public static void setLastUpdateCheckTime(long timestamp) {
        put(AppSettingKeys.LAST_UPDATE_CHECK_TIME, timestamp);
    }

    public static Set<String> getAppUpdateIgnoredMarkers() {
        return get(AppSettingKeys.APP_UPDATE_IGNORED_MARKERS);
    }

    public static void rememberAppUpdateIgnoredMarker(String marker) {
        if (marker == null || marker.isBlank()) {
            return;
        }
        updateAndSaveProperties(settings -> {
            Set<String> markers = AppSettingKeys.APP_UPDATE_IGNORED_MARKERS.read(settings);
            Set<String> updatedMarkers = new LinkedHashSet<>(markers);
            updatedMarkers.add(marker.trim());
            AppSettingKeys.APP_UPDATE_IGNORED_MARKERS.write(settings, updatedMarkers);
        });
    }

    /**
     * 更新源偏好设置
     * 支持的值：
     * - "auto": 自动选择最快的源（默认）
     * - "github": 始终使用 GitHub
     * - "gitee": 始终使用 Gitee
     */
    public static String getUpdateSourcePreference() {
        return get(AppSettingKeys.UPDATE_SOURCE_PREFERENCE);
    }

    public static void setUpdateSourcePreference(String preference) {
        String normalized = AppSettingKeys.normalizedLowerCode(preference);
        if (AppSettingKeys.isSupportedUpdateSourcePreference(normalized)) {
            put(AppSettingKeys.UPDATE_SOURCE_PREFERENCE, normalized);
            log.info("Update source preference saved: {}", normalized);
        }
    }

    // ===== 网络代理设置 =====

    /**
     * 是否启用网络代理
     */
    public static boolean isProxyEnabled() {
        return get(AppSettingKeys.PROXY_ENABLED);
    }

    public static void setProxyEnabled(boolean enabled) {
        put(AppSettingKeys.PROXY_ENABLED, enabled);
    }

    /**
     * 代理模式：MANUAL 或 SYSTEM
     */
    public static String getProxyMode() {
        return get(AppSettingKeys.PROXY_MODE);
    }

    public static void setProxyMode(String mode) {
        put(AppSettingKeys.PROXY_MODE, mode);
    }

    public static boolean isSystemProxyMode() {
        return PROXY_MODE_SYSTEM.equalsIgnoreCase(getProxyMode());
    }

    public static boolean isManualProxyMode() {
        return PROXY_MODE_MANUAL.equalsIgnoreCase(getProxyMode());
    }

    public static boolean isManualProxyModeValue(String mode) {
        return PROXY_MODE_MANUAL.equalsIgnoreCase(mode);
    }

    /**
     * 代理类型：HTTP 或 SOCKS
     */
    public static String getProxyType() {
        return get(AppSettingKeys.PROXY_TYPE);
    }

    public static void setProxyType(String type) {
        put(AppSettingKeys.PROXY_TYPE, type);
    }

    public static String normalizeProxyType(String type) {
        return AppSettingKeys.normalizeProxyType(type);
    }

    /**
     * 代理服务器地址
     */
    public static String getProxyHost() {
        return get(AppSettingKeys.PROXY_HOST);
    }

    public static void setProxyHost(String host) {
        put(AppSettingKeys.PROXY_HOST, host);
    }

    /**
     * 代理服务器端口
     */
    public static int getProxyPort() {
        return get(AppSettingKeys.PROXY_PORT);
    }

    public static String getProxyPortText() {
        return get(AppSettingKeys.PROXY_PORT_TEXT);
    }

    public static void setProxyPort(int port) {
        put(AppSettingKeys.PROXY_PORT, port);
    }

    /**
     * 代理用户名
     */
    public static String getProxyUsername() {
        return get(AppSettingKeys.PROXY_USERNAME);
    }

    public static void setProxyUsername(String username) {
        put(AppSettingKeys.PROXY_USERNAME, username);
    }

    /**
     * 代理密码
     */
    public static String getProxyPassword() {
        return get(AppSettingKeys.PROXY_PASSWORD);
    }

    public static void setProxyPassword(String password) {
        put(AppSettingKeys.PROXY_PASSWORD, password);
    }


    /**
     * 是否禁用 SSL 证书验证（代理环境专用设置）
     * 此设置专门用于解决代理环境下的 SSL 证书验证问题
     */
    public static boolean isProxySslVerificationDisabled() {
        return get(AppSettingKeys.PROXY_SSL_VERIFICATION_DISABLED);
    }

    public static void setProxySslVerificationDisabled(boolean disabled) {
        put(AppSettingKeys.PROXY_SSL_VERIFICATION_DISABLED, disabled);
        // 清除客户端缓存以应用新的 SSL 设置
        OkHttpClientManager.clearClientCache();
    }

    // ===== WebDAV 同步设置 =====

    public static WebDavSyncSettings getWebDavSyncSettings() {
        return new WebDavSyncSettings(
                get(AppSettingKeys.WEBDAV_SYNC_ENABLED),
                get(AppSettingKeys.WEBDAV_SYNC_SERVER_URL),
                get(AppSettingKeys.WEBDAV_SYNC_REMOTE_DIRECTORY),
                get(AppSettingKeys.WEBDAV_SYNC_USERNAME),
                get(AppSettingKeys.WEBDAV_SYNC_PASSWORD),
                get(AppSettingKeys.WEBDAV_SYNC_AUTO_UPLOAD_ENABLED),
                get(AppSettingKeys.WEBDAV_SYNC_AUTO_UPLOAD_INTERVAL_MINUTES)
        );
    }

    public static void setWebDavSyncSettings(WebDavSyncSettings settings) {
        WebDavSyncSettings normalized = settings == null
                ? new WebDavSyncSettings(false, "", WebDavSyncSettings.DEFAULT_REMOTE_DIRECTORY, "", "")
                : settings;
        updateAndSaveProperties(properties -> {
            AppSettingKeys.WEBDAV_SYNC_ENABLED.write(properties, normalized.enabled());
            AppSettingKeys.WEBDAV_SYNC_SERVER_URL.write(properties, normalized.serverUrl());
            AppSettingKeys.WEBDAV_SYNC_REMOTE_DIRECTORY.write(properties, normalized.remoteDirectory());
            AppSettingKeys.WEBDAV_SYNC_USERNAME.write(properties, normalized.username());
            AppSettingKeys.WEBDAV_SYNC_PASSWORD.write(properties, normalized.password());
            AppSettingKeys.WEBDAV_SYNC_AUTO_UPLOAD_ENABLED.write(properties, normalized.autoUploadEnabled());
            AppSettingKeys.WEBDAV_SYNC_AUTO_UPLOAD_INTERVAL_MINUTES.write(
                    properties,
                    normalized.autoUploadIntervalMinutes()
            );
        });
    }

    public static long getWebDavSyncLastSyncTime() {
        return get(AppSettingKeys.WEBDAV_SYNC_LAST_SYNC_TIME);
    }

    public static void setWebDavSyncLastSyncTime(long timestamp) {
        put(AppSettingKeys.WEBDAV_SYNC_LAST_SYNC_TIME, timestamp);
    }

    // ===== UI 字体设置 =====

    /**
     * 获取UI字体名称
     */
    public static String getUiFontName() {
        return get(AppSettingKeys.UI_FONT_NAME);
    }

    public static void setUiFontName(String fontName) {
        put(AppSettingKeys.UI_FONT_NAME, fontName != null ? fontName : "");
    }

    /**
     * 获取UI字体大小
     */
    public static int getUiFontSize() {
        return get(AppSettingKeys.UI_FONT_SIZE);
    }

    public static void setUiFontSize(int size) {
        put(AppSettingKeys.UI_FONT_SIZE, size);
    }

    // ===== 编辑器字体设置 =====

    public static String getEditorFontName() {
        return get(AppSettingKeys.EDITOR_FONT_NAME);
    }

    public static void setEditorFontName(String fontName) {
        put(AppSettingKeys.EDITOR_FONT_NAME, fontName);
    }

    public static String getEditorFontFallbackName() {
        return get(AppSettingKeys.EDITOR_FONT_FALLBACK_NAME);
    }

    public static void setEditorFontFallbackName(String fontName) {
        put(AppSettingKeys.EDITOR_FONT_FALLBACK_NAME, fontName);
    }

    public static int getEditorFontSize() {
        return get(AppSettingKeys.EDITOR_FONT_SIZE);
    }

    public static void setEditorFontSize(int size) {
        put(AppSettingKeys.EDITOR_FONT_SIZE, size);
    }
}
