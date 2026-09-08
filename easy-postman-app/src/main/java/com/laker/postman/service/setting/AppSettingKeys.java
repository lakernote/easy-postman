package com.laker.postman.service.setting;

import cn.hutool.json.JSONUtil;
import com.laker.postman.certificate.TrustedCertificateEntry;
import com.laker.postman.model.NotificationPosition;
import com.laker.postman.service.sync.WebDavSyncSettings;
import com.laker.postman.settings.SettingKey;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@UtilityClass
class AppSettingKeys {

    static final int DEFAULT_MAX_BODY_SIZE = 100 * 1024;
    static final int DEFAULT_REQUEST_TIMEOUT = 0;
    static final int DEFAULT_MAX_DOWNLOAD_SIZE = 0;
    static final int DEFAULT_SCRIPT_REMOTE_CONNECT_TIMEOUT_MS = 3000;
    static final int DEFAULT_SCRIPT_REMOTE_READ_TIMEOUT_MS = 5000;
    static final int DEFAULT_SCRIPT_REMOTE_MAX_BYTES = 512 * 1024;
    static final int DEFAULT_PERFORMANCE_SLOW_REQUEST_THRESHOLD_MS = 0;
    static final int DEFAULT_PERFORMANCE_JS_CONTEXT_ACQUIRE_TIMEOUT_MS = 1_000;
    static final int DEFAULT_PERFORMANCE_MAX_IDLE_CONNECTIONS = 100;
    static final long DEFAULT_PERFORMANCE_KEEP_ALIVE_SECONDS = 60L;
    static final int DEFAULT_PERFORMANCE_MAX_REQUESTS = 1000;
    static final int DEFAULT_PERFORMANCE_MAX_REQUESTS_PER_HOST = 1000;
    static final int DEFAULT_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB = 64;
    static final int MIN_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB = 1;
    static final int MAX_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB = 1024;
    static final int DEFAULT_PERFORMANCE_RESULT_ROW_LIMIT = 3_000;
    static final int MIN_PERFORMANCE_RESULT_ROW_LIMIT = 100;
    static final int MAX_PERFORMANCE_RESULT_ROW_LIMIT = 100_000;
    static final int DEFAULT_GIT_DIFF_LARGE_FILE_THRESHOLD_MB = 2;
    static final int MIN_GIT_DIFF_LARGE_FILE_THRESHOLD_MB = 1;
    static final int MAX_GIT_DIFF_LARGE_FILE_THRESHOLD_MB = 64;
    static final int DEFAULT_TREND_SAMPLING_INTERVAL_SECONDS = 1;
    static final int DEFAULT_DOWNLOAD_PROGRESS_DIALOG_THRESHOLD = 100 * 1024 * 1024;
    static final int DEFAULT_MAX_HISTORY_COUNT = 100;
    static final int DEFAULT_MAX_OPENED_REQUESTS_COUNT = 10;
    static final int DEFAULT_PROXY_PORT = 8080;
    static final int DEFAULT_UI_FONT_SIZE = 13;
    static final int DEFAULT_EDITOR_FONT_SIZE = 13;

    static final SettingKey<Integer> MAX_BODY_SIZE = SettingKey.integerKey(
            "max_body_size",
            DEFAULT_MAX_BODY_SIZE
    );
    static final SettingKey<Integer> REQUEST_TIMEOUT = SettingKey.integerKey(
            "request_timeout",
            DEFAULT_REQUEST_TIMEOUT
    );
    static final SettingKey<Integer> MAX_DOWNLOAD_SIZE = SettingKey.integerKey(
            "max_download_size",
            DEFAULT_MAX_DOWNLOAD_SIZE
    );
    static final SettingKey<Integer> PERFORMANCE_MAX_IDLE_CONNECTIONS = SettingKey.integerKey(
            "performance_max_idle_connections",
            DEFAULT_PERFORMANCE_MAX_IDLE_CONNECTIONS,
            value -> positiveOr(value, DEFAULT_PERFORMANCE_MAX_IDLE_CONNECTIONS)
    );
    static final SettingKey<Long> PERFORMANCE_KEEP_ALIVE_SECONDS = SettingKey.longKey(
            "performance_keep_alive_seconds",
            DEFAULT_PERFORMANCE_KEEP_ALIVE_SECONDS,
            value -> positiveOr(value, DEFAULT_PERFORMANCE_KEEP_ALIVE_SECONDS)
    );
    static final SettingKey<Integer> PERFORMANCE_MAX_REQUESTS = SettingKey.integerKey(
            "performance_max_requests",
            DEFAULT_PERFORMANCE_MAX_REQUESTS,
            value -> positiveOr(value, DEFAULT_PERFORMANCE_MAX_REQUESTS)
    );
    static final SettingKey<Integer> PERFORMANCE_MAX_REQUESTS_PER_HOST = SettingKey.integerKey(
            "performance_max_requests_per_host",
            DEFAULT_PERFORMANCE_MAX_REQUESTS_PER_HOST,
            value -> positiveOr(value, DEFAULT_PERFORMANCE_MAX_REQUESTS_PER_HOST)
    );
    static final SettingKey<Integer> PERFORMANCE_JS_CONTEXT_POOL_SIZE = SettingKey.integerKey(
            "performance_js_context_pool_size",
            defaultPerformanceJsContextPoolSize(),
            value -> positiveOr(value, defaultPerformanceJsContextPoolSize())
    );
    static final SettingKey<Integer> PERFORMANCE_JS_CONTEXT_ACQUIRE_TIMEOUT_MS = SettingKey.integerKey(
            "performance_js_context_acquire_timeout_ms",
            DEFAULT_PERFORMANCE_JS_CONTEXT_ACQUIRE_TIMEOUT_MS,
            value -> positiveOr(value, DEFAULT_PERFORMANCE_JS_CONTEXT_ACQUIRE_TIMEOUT_MS)
    );
    static final SettingKey<Integer> PERFORMANCE_SLOW_REQUEST_THRESHOLD = SettingKey.integerKey(
            "performance_slow_request_threshold",
            DEFAULT_PERFORMANCE_SLOW_REQUEST_THRESHOLD_MS,
            value -> Math.max(0, value)
    );
    static final SettingKey<Integer> TREND_SAMPLING_INTERVAL_SECONDS = SettingKey.integerKey(
            "trend_sampling_interval_seconds",
            DEFAULT_TREND_SAMPLING_INTERVAL_SECONDS,
            value -> clamp(value, 1, 60)
    );
    static final SettingKey<Boolean> PERFORMANCE_EVENT_LOGGING_ENABLED = SettingKey.booleanKey(
            "performance_event_logging_enabled",
            false
    );
    static final SettingKey<Integer> PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB = SettingKey.integerKey(
            "performance_response_body_preview_limit_kb",
            DEFAULT_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB,
            AppSettingKeys::sanitizePerformanceResponseBodyPreviewLimitKb
    );
    static final SettingKey<Integer> PERFORMANCE_RESULT_ROW_LIMIT = SettingKey.integerKey(
            "performance_result_row_limit",
            DEFAULT_PERFORMANCE_RESULT_ROW_LIMIT,
            AppSettingKeys::sanitizePerformanceResultRowLimit
    );
    static final SettingKey<Integer> GIT_DIFF_LARGE_FILE_THRESHOLD_MB = SettingKey.integerKey(
            "git_diff_large_file_threshold_mb",
            DEFAULT_GIT_DIFF_LARGE_FILE_THRESHOLD_MB,
            AppSettingKeys::sanitizeGitDiffLargeFileThresholdMb
    );
    static final SettingKey<String> CSV_LAST_IMPORT_DIRECTORY = SettingKey.stringKey(
            "csv_last_import_directory",
            ""
    );
    static final SettingKey<Boolean> SHOW_DOWNLOAD_PROGRESS_DIALOG = SettingKey.booleanKey(
            "show_download_progress_dialog",
            true
    );
    static final SettingKey<Integer> DOWNLOAD_PROGRESS_DIALOG_THRESHOLD = SettingKey.integerKey(
            "download_progress_dialog_threshold",
            DEFAULT_DOWNLOAD_PROGRESS_DIALOG_THRESHOLD
    );
    static final SettingKey<Boolean> FOLLOW_REDIRECTS = SettingKey.booleanKey(
            "follow_redirects",
            true
    );
    static final SettingKey<Boolean> REQUEST_SSL_VERIFICATION_ENABLED = SettingKey.booleanKey(
            "ssl_verification_enabled",
            false
    );
    static final SettingKey<String> DEFAULT_PROTOCOL = SettingKey.stringKey(
            "default_protocol",
            "http"
    ).normalized(AppSettingKeys::normalizeDefaultProtocol);
    static final SettingKey<Set<String>> REQUEST_EDITOR_HIDDEN_TABS = SettingKey.of(
            "request_editor_hidden_tabs",
            Set.of(),
            AppSettingKeys::parseUppercaseSet,
            AppSettingKeys::formatCollection
    ).normalized(AppSettingKeys::normalizeUppercaseSet);
    static final SettingKey<Boolean> REQUEST_EDITOR_TABS_MULTILINE = SettingKey.booleanKey(
            "request_editor_tabs_multiline",
            false
    );
    static final SettingKey<Boolean> REMOTE_JS_REQUIRE_ENABLED = SettingKey.booleanKey(
            "script_remote_require_enabled",
            false
    );
    static final SettingKey<Boolean> REMOTE_JS_REQUIRE_ALLOW_HTTP = SettingKey.booleanKey(
            "script_remote_require_allow_http",
            false
    );
    static final SettingKey<String> REMOTE_JS_REQUIRE_ALLOWED_HOSTS = SettingKey.stringKey(
            "script_remote_require_allowed_hosts",
            ""
    ).normalized(value -> value == null ? "" : value.trim());
    static final SettingKey<Integer> REMOTE_JS_REQUIRE_CONNECT_TIMEOUT_MS = SettingKey.integerKey(
            "script_remote_require_connect_timeout_ms",
            DEFAULT_SCRIPT_REMOTE_CONNECT_TIMEOUT_MS,
            value -> positiveOr(value, DEFAULT_SCRIPT_REMOTE_CONNECT_TIMEOUT_MS)
    );
    static final SettingKey<Integer> REMOTE_JS_REQUIRE_CONNECT_TIMEOUT_MS_TO_WRITE = SettingKey.integerKey(
            "script_remote_require_connect_timeout_ms",
            DEFAULT_SCRIPT_REMOTE_CONNECT_TIMEOUT_MS,
            AppSettingKeys::atLeastOne
    );
    static final SettingKey<Integer> REMOTE_JS_REQUIRE_READ_TIMEOUT_MS = SettingKey.integerKey(
            "script_remote_require_read_timeout_ms",
            DEFAULT_SCRIPT_REMOTE_READ_TIMEOUT_MS,
            value -> positiveOr(value, DEFAULT_SCRIPT_REMOTE_READ_TIMEOUT_MS)
    );
    static final SettingKey<Integer> REMOTE_JS_REQUIRE_READ_TIMEOUT_MS_TO_WRITE = SettingKey.integerKey(
            "script_remote_require_read_timeout_ms",
            DEFAULT_SCRIPT_REMOTE_READ_TIMEOUT_MS,
            AppSettingKeys::atLeastOne
    );
    static final SettingKey<Integer> REMOTE_JS_REQUIRE_MAX_BYTES = SettingKey.integerKey(
            "script_remote_require_max_bytes",
            DEFAULT_SCRIPT_REMOTE_MAX_BYTES,
            value -> positiveOr(value, DEFAULT_SCRIPT_REMOTE_MAX_BYTES)
    );
    static final SettingKey<Integer> REMOTE_JS_REQUIRE_MAX_BYTES_TO_WRITE = SettingKey.integerKey(
            "script_remote_require_max_bytes",
            DEFAULT_SCRIPT_REMOTE_MAX_BYTES,
            AppSettingKeys::atLeastOne
    );
    static final SettingKey<Boolean> CUSTOM_TRUST_MATERIAL_ENABLED = SettingKey.booleanKey(
            "custom_trust_material_enabled",
            false
    );
    static final SettingKey<List<TrustedCertificateEntry>> CUSTOM_TRUST_MATERIAL_ENTRIES = SettingKey.<List<TrustedCertificateEntry>>of(
            "custom_trust_material_entries",
            List.of(),
            AppSettingKeys::parseTrustedCertificateEntries,
            entries -> JSONUtil.toJsonStr(entries)
    ).normalized(AppSettingKeys::sanitizeTrustedCertificateEntries);
    static final SettingKey<Integer> MAX_HISTORY_COUNT = SettingKey.integerKey(
            "max_history_count",
            DEFAULT_MAX_HISTORY_COUNT
    );
    static final SettingKey<Integer> MAX_OPENED_REQUESTS_COUNT = SettingKey.integerKey(
            "max_opened_requests_count",
            DEFAULT_MAX_OPENED_REQUESTS_COUNT
    );
    static final SettingKey<Boolean> AUTO_FORMAT_RESPONSE = SettingKey.booleanKey(
            "auto_format_response",
            true
    );
    static final SettingKey<Boolean> STARTUP_SPLASH_ENABLED = SettingKey.booleanKey(
            "startup_splash_enabled",
            true
    );
    static final SettingKey<Boolean> SIDEBAR_EXPANDED = SettingKey.booleanKey(
            "sidebar_expanded",
            false
    );
    static final SettingKey<List<String>> SIDEBAR_TAB_ORDER = SettingKey.of(
            "sidebar_tab_order",
            List.of(),
            AppSettingKeys::parseTrimmedList,
            AppSettingKeys::formatCollection
    );
    static final SettingKey<Set<String>> SIDEBAR_HIDDEN_TABS = SettingKey.of(
            "sidebar_hidden_tabs",
            Set.of(),
            AppSettingKeys::parseUppercaseSet,
            AppSettingKeys::formatCollection
    );
    static final SettingKey<Boolean> LAYOUT_VERTICAL = SettingKey.booleanKey(
            "layout_vertical",
            true
    );
    static final SettingKey<NotificationPosition> NOTIFICATION_POSITION = SettingKey.of(
            "notification_position",
            NotificationPosition.BOTTOM_RIGHT,
            NotificationPosition::fromName,
            NotificationPosition::name
    );
    static final SettingKey<Boolean> AUTO_UPDATE_CHECK_ENABLED = SettingKey.booleanKey(
            "auto_update_check_enabled",
            true
    );
    static final SettingKey<String> AUTO_UPDATE_CHECK_FREQUENCY = SettingKey.stringKey(
            "auto_update_check_frequency",
            "daily"
    ).normalized(AppSettingKeys::normalizeAutoUpdateFrequency);
    static final SettingKey<Boolean> PLUGIN_UPDATE_CHECK_ENABLED = SettingKey.booleanKey(
            "plugin_update_check_enabled",
            true
    );
    static final SettingKey<String> PLUGIN_UPDATE_CHECK_FREQUENCY = SettingKey.stringKey(
            "plugin_update_check_frequency",
            "daily"
    ).normalized(AppSettingKeys::normalizeAutoUpdateFrequency);
    static final SettingKey<Long> LAST_UPDATE_CHECK_TIME = SettingKey.longKey(
            "last_update_check_time",
            0L,
            value -> Math.max(0L, value)
    );
    static final SettingKey<Set<String>> APP_UPDATE_IGNORED_MARKERS = SettingKey.of(
            "app_update_ignored_markers",
            Set.of(),
            AppSettingKeys::parseTrimmedSet,
            AppSettingKeys::formatCollection
    );
    static final SettingKey<String> UPDATE_SOURCE_PREFERENCE = SettingKey.stringKey(
            "update_source_preference",
            "auto"
    ).normalized(AppSettingKeys::normalizeUpdateSourcePreference);
    static final SettingKey<Boolean> PROXY_ENABLED = SettingKey.booleanKey(
            "proxy_enabled",
            false
    );
    static final SettingKey<String> PROXY_MODE = SettingKey.stringKey(
            "proxy_mode",
            SettingManager.PROXY_MODE_MANUAL
    ).normalized(AppSettingKeys::normalizeProxyMode);
    static final SettingKey<String> PROXY_TYPE = SettingKey.stringKey(
            "proxy_type",
            SettingManager.PROXY_TYPE_HTTP
    ).normalized(AppSettingKeys::normalizeProxyType);
    static final SettingKey<String> PROXY_HOST = SettingKey.stringKey(
            "proxy_host",
            ""
    );
    static final SettingKey<Integer> PROXY_PORT = SettingKey.integerKey(
            "proxy_port",
            DEFAULT_PROXY_PORT
    );
    static final SettingKey<String> PROXY_PORT_TEXT = SettingKey.stringKey(
            "proxy_port",
            ""
    );
    static final SettingKey<String> PROXY_USERNAME = SettingKey.stringKey(
            "proxy_username",
            ""
    );
    static final SettingKey<String> PROXY_PASSWORD = SettingKey.stringKey(
            "proxy_password",
            ""
    );
    static final SettingKey<Boolean> PROXY_SSL_VERIFICATION_DISABLED = SettingKey.booleanKey(
            "proxy_ssl_verification_disabled",
            true
    );
    static final SettingKey<Boolean> WEBDAV_SYNC_ENABLED = SettingKey.booleanKey(
            "webdav_sync_enabled",
            false
    );
    static final SettingKey<String> WEBDAV_SYNC_SERVER_URL = SettingKey.stringKey(
            "webdav_sync_server_url",
            ""
    ).normalized(AppSettingKeys::normalizeNullableText);
    static final SettingKey<String> WEBDAV_SYNC_REMOTE_DIRECTORY = SettingKey.stringKey(
            "webdav_sync_remote_directory",
            WebDavSyncSettings.DEFAULT_REMOTE_DIRECTORY
    ).normalized(AppSettingKeys::normalizeWebDavRemoteDirectory);
    static final SettingKey<String> WEBDAV_SYNC_USERNAME = SettingKey.stringKey(
            "webdav_sync_username",
            ""
    ).normalized(AppSettingKeys::normalizeNullableText);
    static final SettingKey<String> WEBDAV_SYNC_PASSWORD = SettingKey.stringKey(
            "webdav_sync_password",
            ""
    ).normalized(value -> value == null ? "" : value);
    static final SettingKey<Boolean> WEBDAV_SYNC_AUTO_UPLOAD_ENABLED = SettingKey.booleanKey(
            "webdav_sync_auto_upload_enabled",
            false
    );
    static final SettingKey<Integer> WEBDAV_SYNC_AUTO_UPLOAD_INTERVAL_MINUTES = SettingKey.integerKey(
            "webdav_sync_auto_upload_interval_minutes",
            WebDavSyncSettings.DEFAULT_AUTO_UPLOAD_INTERVAL_MINUTES,
            value -> clamp(
                    value,
                    WebDavSyncSettings.MIN_AUTO_UPLOAD_INTERVAL_MINUTES,
                    WebDavSyncSettings.MAX_AUTO_UPLOAD_INTERVAL_MINUTES
            )
    );
    static final SettingKey<Long> WEBDAV_SYNC_LAST_SYNC_TIME = SettingKey.longKey(
            "webdav_sync_last_sync_time",
            0L,
            value -> Math.max(0L, value)
    );
    static final SettingKey<String> UI_FONT_NAME = SettingKey.stringKey(
            "ui_font_name",
            ""
    );
    static final SettingKey<Integer> UI_FONT_SIZE = SettingKey.integerKey(
            "ui_font_size",
            DEFAULT_UI_FONT_SIZE,
            value -> clamp(value, 10, 24)
    );
    static final SettingKey<String> EDITOR_FONT_NAME = SettingKey.stringKey(
            "editor_font_name",
            ""
    ).normalized(value -> value == null ? "" : value.trim());
    static final SettingKey<String> EDITOR_FONT_FALLBACK_NAME = SettingKey.stringKey(
            "editor_font_fallback_name",
            ""
    ).normalized(value -> value == null ? "" : value.trim());
    static final SettingKey<Integer> EDITOR_FONT_SIZE = SettingKey.integerKey(
            "editor_font_size",
            DEFAULT_EDITOR_FONT_SIZE,
            value -> clamp(value, 8, 32)
    );

    static int defaultPerformanceJsContextPoolSize() {
        return Math.max(16, Runtime.getRuntime().availableProcessors() * 4);
    }

    static int sanitizePerformanceResponseBodyPreviewLimitKb(Integer limitKb) {
        if (limitKb == null
                || limitKb < MIN_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB
                || limitKb > MAX_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB) {
            return DEFAULT_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB;
        }
        return limitKb;
    }

    static int sanitizePerformanceResultRowLimit(Integer rowLimit) {
        if (rowLimit == null
                || rowLimit < MIN_PERFORMANCE_RESULT_ROW_LIMIT
                || rowLimit > MAX_PERFORMANCE_RESULT_ROW_LIMIT) {
            return DEFAULT_PERFORMANCE_RESULT_ROW_LIMIT;
        }
        return rowLimit;
    }

    static int sanitizeGitDiffLargeFileThresholdMb(Integer thresholdMb) {
        if (thresholdMb == null
                || thresholdMb < MIN_GIT_DIFF_LARGE_FILE_THRESHOLD_MB
                || thresholdMb > MAX_GIT_DIFF_LARGE_FILE_THRESHOLD_MB) {
            return DEFAULT_GIT_DIFF_LARGE_FILE_THRESHOLD_MB;
        }
        return thresholdMb;
    }

    static String normalizeAutoUpdateFrequency(String frequency) {
        String normalized = normalizedLowerCode(frequency);
        return isSupportedAutoUpdateFrequency(normalized) ? normalized : "daily";
    }

    static boolean isSupportedAutoUpdateFrequency(String frequency) {
        return frequency.equals("startup")
                || frequency.equals("daily")
                || frequency.equals("weekly")
                || frequency.equals("monthly");
    }

    static String normalizeUpdateSourcePreference(String preference) {
        String normalized = normalizedLowerCode(preference);
        return isSupportedUpdateSourcePreference(normalized) ? normalized : "auto";
    }

    static boolean isSupportedUpdateSourcePreference(String preference) {
        return preference.equals("auto") || preference.equals("github") || preference.equals("gitee");
    }

    static String normalizedLowerCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeProxyType(String type) {
        return SettingManager.PROXY_TYPE_SOCKS.equalsIgnoreCase(type)
                ? SettingManager.PROXY_TYPE_SOCKS
                : SettingManager.PROXY_TYPE_HTTP;
    }

    static List<TrustedCertificateEntry> sanitizeTrustedCertificateEntries(List<TrustedCertificateEntry> entries) {
        List<TrustedCertificateEntry> sanitizedEntries = new ArrayList<>();
        if (entries == null) {
            return sanitizedEntries;
        }

        for (TrustedCertificateEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            TrustedCertificateEntry sanitized = new TrustedCertificateEntry();
            sanitized.setEnabled(entry.isEnabled());
            sanitized.setPath(entry.getPath() != null ? entry.getPath().trim() : "");
            sanitized.setPassword(entry.getPassword() != null ? entry.getPassword() : "");
            if (sanitized.hasUsablePath()) {
                sanitizedEntries.add(sanitized);
            }
        }
        return sanitizedEntries;
    }

    private static int positiveOr(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static long positiveOr(long value, long defaultValue) {
        return value > 0L ? value : defaultValue;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int atLeastOne(int value) {
        return Math.max(1, value);
    }

    private static String normalizeDefaultProtocol(String protocol) {
        return "https".equals(protocol) ? "https" : "http";
    }

    private static Set<String> parseUppercaseSet(String value) {
        return normalizeUppercaseSet(parseTrimmedList(value));
    }

    private static Set<String> parseTrimmedSet(String value) {
        return new LinkedHashSet<>(parseTrimmedList(value));
    }

    private static Set<String> normalizeUppercaseSet(Collection<String> values) {
        Set<String> normalizedValues = new LinkedHashSet<>();
        if (values == null) {
            return normalizedValues;
        }
        for (String value : values) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                normalizedValues.add(normalized);
            }
        }
        return normalizedValues;
    }

    private static List<String> parseTrimmedList(String value) {
        List<String> values = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return values;
        }
        for (String token : value.split(",")) {
            String normalized = token == null ? "" : token.trim();
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private static String formatCollection(Collection<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(",", values);
    }

    private static List<TrustedCertificateEntry> parseTrustedCertificateEntries(String json) {
        if (json == null || json.trim().isEmpty()) {
            return List.of();
        }
        try {
            List<TrustedCertificateEntry> entries = JSONUtil.toList(
                    JSONUtil.parseArray(json),
                    TrustedCertificateEntry.class
            );
            return entries == null ? List.of() : entries;
        } catch (Exception e) {
            log.warn("Failed to parse custom trust material entries", e);
            return List.of();
        }
    }

    private static String normalizeProxyMode(String mode) {
        return SettingManager.PROXY_MODE_SYSTEM.equalsIgnoreCase(mode)
                ? SettingManager.PROXY_MODE_SYSTEM
                : SettingManager.PROXY_MODE_MANUAL;
    }

    private static String normalizeNullableText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeWebDavRemoteDirectory(String value) {
        String normalized = normalizeNullableText(value).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? WebDavSyncSettings.DEFAULT_REMOTE_DIRECTORY : normalized;
    }
}
