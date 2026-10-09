package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地化网络日志中由 EasyPostman 生成的固定诊断短语。
 * <p>
 * URL、请求头、Cookie、证书主题、协议值和异常原文等网络数据保持原样；
 * 这里只翻译阶段正文中的固定标签，避免运行时模块直接依赖应用层国际化。
 */
@UtilityClass
public class NetworkLogMessageFormatter {
    private static final Pattern FOLLOW_UP = Pattern.compile(
            "^Follow-up: (true|false), response: ([^,]+), next: (.*)$");
    private static final Pattern RETRY = Pattern.compile(
            "^Retry: (true|false), reason: (.*)$");
    private static final Pattern PROXY_CONFIGURATION = Pattern.compile(
            "^requestPolicy=([^,]+), appProxyEnabled=(true|false), appProxyMode=([^,]+), "
                    + "clientProxy=(.*), directHttpSocketFactoryJvmSocksBypass=(true|false), "
                    + "manualProxyConfig=([^,]+)$");
    private static final Pattern REDIRECT_NUMBER = Pattern.compile("^Redirect #(\\d+)$");
    private static final Pattern REDIRECT_STOPPED = Pattern.compile(
            "^Redirect stopped: ([^,]+), status: (\\d+)(?:, max: (\\d+))?$");
    private static final Pattern RESPONSE_WAIT = Pattern.compile("^Wait: (\\d+)ms$");

    public static String format(NetworkLogEventStage stage, String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        return switch (stage == null ? NetworkLogEventStage.DEFAULT : stage) {
            case PROXY_SELECT_START -> replacePrefix(message, "Selecting proxy for ",
                    MessageKeys.NETWORK_LOG_MESSAGE_SELECTING_PROXY);
            case PROXY_SELECT_END -> replacePrefix(message, "Proxies: ",
                    MessageKeys.NETWORK_LOG_MESSAGE_PROXIES);
            case PROXY_SELECT -> formatProxyConfiguration(message);
            case SECURE_CONNECT_START -> exact(message, "TLS handshake start",
                    MessageKeys.NETWORK_LOG_MESSAGE_TLS_START);
            case SECURE_CONNECT_END -> formatTlsMessage(message);
            case CONNECTION_ACQUIRED -> formatConnectionAcquired(message);
            case CONNECTION_RELEASED -> "Connection use released".equals(message)
                    || "Returned to connection pool".equals(message)
                    || message.startsWith("Connection released: ")
                    ? "" : message;
            case REQUEST_HEADERS_END -> formatHeaderSnapshot(message);
            case RESPONSE_HEADERS_END, RESPONSE_HEADERS_END_REDIRECT -> formatResponseHeaders(message);
            case REQUEST_COMPLETE -> formatFlowSummary(message);
            case REDIRECT -> formatRedirect(message);
            case REQUEST_BODY_START -> exact(message, "Request body preview unavailable",
                    MessageKeys.NETWORK_LOG_MESSAGE_REQUEST_BODY_PREVIEW_UNAVAILABLE);
            case REQUEST_BODY_END, RESPONSE_BODY_END -> replacePrefix(message, "bytes=",
                    MessageKeys.NETWORK_LOG_MESSAGE_BYTES);
            case FOLLOW_UP_DECISION -> formatFollowUp(message);
            case RETRY_DECISION -> formatRetry(message);
            case CALL_END -> "done".equals(message) ? "" : message;
            case CANCELED -> exact(message, "Call was canceled",
                    MessageKeys.NETWORK_LOG_MESSAGE_CALL_CANCELED);
            case CACHE_HIT -> replacePrefix(message, "Response served from cache: ",
                    MessageKeys.NETWORK_LOG_MESSAGE_CACHE_HIT);
            case CACHE_MISS -> exact(message, "No cache hit for this call",
                    MessageKeys.NETWORK_LOG_MESSAGE_CACHE_MISS);
            case CACHE_CONDITIONAL_HIT -> replacePrefix(message,
                    "Response served from conditional cache: ",
                    MessageKeys.NETWORK_LOG_MESSAGE_CACHE_CONDITIONAL_HIT);
            case SATISFACTION_FAILURE -> replacePrefix(message,
                    "Response does not satisfy request: ",
                    MessageKeys.NETWORK_LOG_MESSAGE_SATISFACTION_FAILURE);
            default -> message;
        };
    }

    private static String formatFollowUp(String message) {
        Matcher matcher = FOLLOW_UP.matcher(message);
        if (!matcher.matches()) {
            return message;
        }
        if ("false".equals(matcher.group(1))) {
            return "";
        }
        return I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_FOLLOW_UP,
                formatBoolean(matcher.group(1)), matcher.group(2), formatNone(matcher.group(3)));
    }

    private static String formatProxyConfiguration(String message) {
        if ("proxy diagnostics unavailable".equals(message)) {
            return I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_PROXY_DIAGNOSTICS_UNAVAILABLE);
        }
        Matcher matcher = PROXY_CONFIGURATION.matcher(message);
        if (!matcher.matches()) {
            return message;
        }
        String formatted = I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_PROXY_CONFIGURATION,
                formatClientProxy(matcher.group(4)), formatProxyPolicy(matcher.group(1)));
        String manualConfig = matcher.group(6);
        if (!"NOT_USED".equals(manualConfig) && !"FIELDS_PRESENT".equals(manualConfig)) {
            formatted += "\n" + I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_PROXY_FALLBACK,
                    formatManualProxyConfig(manualConfig));
        }
        if ("false".equals(matcher.group(5))) {
            formatted += "\n" + I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_JVM_SOCKS_ACTIVE);
        }
        return formatted;
    }

    private static String formatProxyPolicy(String value) {
        return switch (value) {
            case "DEFAULT" -> I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_DEFAULT);
            case "USE_PROXY" -> I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_USE_PROXY);
            case "NO_PROXY" -> I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_NO_PROXY);
            default -> value;
        };
    }

    private static String formatClientProxy(String value) {
        return switch (value) {
            case "DIRECT" -> I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_STATUS_DIRECT);
            case "SYSTEM_SELECTOR" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_SYSTEM_SELECTOR);
            case "CUSTOM_SELECTOR" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_CUSTOM_SELECTOR);
            default -> value;
        };
    }

    private static String formatManualProxyConfig(String value) {
        return switch (value) {
            case "NOT_USED" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_MANUAL_PROXY_NOT_USED);
            case "MISSING_HOST" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_MANUAL_PROXY_MISSING_HOST);
            case "INVALID_HOST" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_MANUAL_PROXY_INVALID_HOST);
            case "INVALID_PORT" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_MANUAL_PROXY_INVALID_PORT);
            case "FIELDS_PRESENT" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_MANUAL_PROXY_FIELDS_PRESENT);
            default -> value;
        };
    }

    private static String formatRetry(String message) {
        Matcher matcher = RETRY.matcher(message);
        if (!matcher.matches()) {
            return message;
        }
        return I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_RETRY,
                formatBoolean(matcher.group(1)), matcher.group(2));
    }

    private static String formatConnectionAcquired(String message) {
        if (message.startsWith("Connection reused: ")) {
            return replacePrefix(message, "Connection reused: ",
                    MessageKeys.NETWORK_LOG_MESSAGE_CONNECTION_REUSED);
        }
        return replacePrefix(message, "Connection acquired: ",
                MessageKeys.NETWORK_LOG_MESSAGE_CONNECTION_ACQUIRED);
    }

    private static String formatHeaderSnapshot(String message) {
        String http2Prefix = "HTTP/2 header view (regular headers and :authority):";
        String generalPrefix = "Header snapshot:";
        String leadingNewline = message.startsWith("\n") ? "\n" : "";
        String snapshot = message.substring(leadingNewline.length());
        if ("Headers unchanged".equals(snapshot)) {
            return leadingNewline + I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_HEADERS_UNCHANGED);
        }
        if (snapshot.startsWith(http2Prefix)) {
            return leadingNewline + I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_HEADER_SNAPSHOT_HTTP2)
                    + snapshot.substring(http2Prefix.length());
        }
        if (snapshot.startsWith(generalPrefix)) {
            return leadingNewline + I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_HEADER_SNAPSHOT)
                    + snapshot.substring(generalPrefix.length());
        }
        return message;
    }

    private static String formatResponseHeaders(String message) {
        String[] lines = message.split("\n", -1);
        int statusIndex = lines[0].isEmpty() ? 1 : 0;
        int waitIndex = statusIndex + 1;
        if (lines.length <= waitIndex || !lines[statusIndex].startsWith("HTTP/")) {
            return message;
        }
        Matcher wait = RESPONSE_WAIT.matcher(lines[waitIndex]);
        if (wait.matches()) {
            lines[waitIndex] = I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_RESPONSE_WAIT,
                    wait.group(1));
        }
        return String.join("\n", lines);
    }

    private static String formatFlowSummary(String message) {
        String[] lines = message.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            lines[index] = formatSummaryLine(lines[index]);
        }
        return String.join("\n", lines);
    }

    private static String formatSummaryLine(String line) {
        if (line.startsWith("Status: ")) {
            return replacePrefix(line, "Status: ", MessageKeys.NETWORK_LOG_MESSAGE_STATUS);
        }
        if (line.startsWith("Final: ")) {
            return replacePrefix(line, "Final: ", MessageKeys.NETWORK_LOG_MESSAGE_FINAL_REQUEST);
        }
        if (line.startsWith("Redirects: ")) {
            return replacePrefix(line, "Redirects: ", MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_COUNT);
        }
        return replacePrefix(line, "Total: ", MessageKeys.NETWORK_LOG_MESSAGE_TOTAL_DURATION);
    }

    private static String formatRedirect(String message) {
        Matcher stopped = REDIRECT_STOPPED.matcher(message);
        if (stopped.matches()) {
            String reason = switch (stopped.group(1)) {
                case "disabled" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_DISABLED);
                case "max redirects reached" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_LIMIT);
                case "missing Location" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_MISSING_LOCATION);
                default -> stopped.group(1);
            };
            String formatted = I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_STOPPED,
                    reason, stopped.group(2));
            return stopped.group(3) == null ? formatted : formatted + "\n"
                    + I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_MAX, stopped.group(3));
        }
        String[] lines = message.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            Matcher redirect = REDIRECT_NUMBER.matcher(line);
            if (redirect.matches()) {
                lines[index] = I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_NUMBER,
                        redirect.group(1));
            } else if (line.startsWith("From: ")) {
                lines[index] = replacePrefix(line, "From: ", MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_FROM);
            } else if (line.startsWith("To: ")) {
                lines[index] = replacePrefix(line, "To: ", MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_TO);
            } else if ("Cross-Origin: true".equals(line)) {
                lines[index] = I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_CROSS_ORIGIN);
            } else if (line.startsWith("Method Changed: ")) {
                lines[index] = replacePrefix(line, "Method Changed: ",
                        MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_METHOD_CHANGED);
            } else if (line.startsWith("Removed Headers: ")) {
                lines[index] = replacePrefix(line, "Removed Headers: ",
                        MessageKeys.NETWORK_LOG_MESSAGE_REDIRECT_REMOVED_HEADERS);
            } else {
                lines[index] = formatSummaryLine(line);
            }
        }
        return String.join("\n", lines);
    }

    private static String formatBoolean(String value) {
        return Boolean.parseBoolean(value)
                ? I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_YES)
                : I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_NO);
    }

    private static String formatNone(String value) {
        return "none".equals(value)
                ? I18nUtil.getMessage(MessageKeys.NETWORK_LOG_VALUE_NONE)
                : value;
    }

    private static String formatTlsMessage(String message) {
        String[] lines = message.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            lines[index] = formatTlsLine(lines[index]);
        }
        return String.join("\n", lines);
    }

    private static String formatTlsLine(String line) {
        if (line.startsWith("TLS connection: ")) {
            return replacePrefix(line, "TLS connection: ", MessageKeys.NETWORK_LOG_MESSAGE_TLS_CONNECTION);
        }
        if (line.startsWith("SSL connection using ")) {
            return replacePrefix(line, "SSL connection using ", MessageKeys.NETWORK_LOG_MESSAGE_SSL_CONNECTION);
        }
        return switch (line) {
            case "Verification: disabled" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_TLS_VERIFICATION_DISABLED);
            case "Verification: enabled" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_TLS_VERIFICATION_ENABLED);
            case "Verification: passed" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_TLS_VERIFICATION_PASSED);
            case "Server certificate:" -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_SERVER_CERTIFICATE);
            case "SSL certificate verify ok." -> I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_VERIFY_OK);
            default -> formatCertificateDetailLine(line);
        };
    }

    private static String formatCertificateDetailLine(String line) {
        if (line.startsWith(" subject:")) {
            return replaceLiteralPrefix(line, " subject:", MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_SUBJECT);
        }
        if (line.startsWith(" start date:")) {
            return replaceLiteralPrefix(line, " start date:", MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_START_DATE);
        }
        if (line.startsWith(" expire date:")) {
            return replaceLiteralPrefix(line, " expire date:", MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_EXPIRE_DATE);
        }
        if (line.startsWith(" subjectAltName:")) {
            return replaceLiteralPrefix(line, " subjectAltName:", MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_ALT_NAME);
        }
        if (line.startsWith(" issuer:")) {
            return replaceLiteralPrefix(line, " issuer:", MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_ISSUER);
        }
        if (line.startsWith("⚠️  Certificate Warning: ")) {
            return "⚠️  " + replacePrefix(line.substring("⚠️  ".length()), "Certificate Warning: ",
                    MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_WARNING);
        }
        return replacePrefix(line, "Certificate Warning: ", MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_WARNING);
    }

    private static String replacePrefix(String value, String prefix, String messageKey) {
        if (!value.startsWith(prefix)) {
            return value;
        }
        return I18nUtil.getMessage(messageKey, value.substring(prefix.length()));
    }

    private static String replaceLiteralPrefix(String value, String prefix, String messageKey) {
        return I18nUtil.getMessage(messageKey) + value.substring(prefix.length());
    }

    private static String exact(String value, String expected, String messageKey) {
        return expected.equals(value) ? I18nUtil.getMessage(messageKey) : value;
    }
}
