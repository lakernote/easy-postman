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
            case CONNECTION_ACQUIRED -> replacePrefix(message, "Connection acquired: ",
                    MessageKeys.NETWORK_LOG_MESSAGE_CONNECTION_ACQUIRED);
            case CONNECTION_RELEASED -> replacePrefix(message, "Connection released: ",
                    MessageKeys.NETWORK_LOG_MESSAGE_CONNECTION_RELEASED);
            case REQUEST_BODY_END, RESPONSE_BODY_END -> replacePrefix(message, "bytes=",
                    MessageKeys.NETWORK_LOG_MESSAGE_BYTES);
            case FOLLOW_UP_DECISION -> formatFollowUp(message);
            case RETRY_DECISION -> formatRetry(message);
            case CALL_END -> exact(message, "done", MessageKeys.NETWORK_LOG_MESSAGE_REQUEST_DONE);
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
        return I18nUtil.getMessage(MessageKeys.NETWORK_LOG_MESSAGE_PROXY_CONFIGURATION,
                formatProxyPolicy(matcher.group(1)), formatBoolean(matcher.group(2)),
                formatProxyMode(matcher.group(3)), formatClientProxy(matcher.group(4)),
                formatBoolean(matcher.group(5)), formatManualProxyConfig(matcher.group(6)));
    }

    private static String formatProxyPolicy(String value) {
        return switch (value) {
            case "DEFAULT" -> I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_DEFAULT);
            case "USE_PROXY" -> I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_USE_PROXY);
            case "NO_PROXY" -> I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_NO_PROXY);
            default -> value;
        };
    }

    private static String formatProxyMode(String value) {
        return switch (value) {
            case "MANUAL" -> I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE_MANUAL);
            case "SYSTEM" -> I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE_SYSTEM);
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
        String formatted = replacePrefix(message, "SSL connection using ",
                MessageKeys.NETWORK_LOG_MESSAGE_SSL_CONNECTION);
        formatted = replaceLine(formatted, "Server certificate:",
                MessageKeys.NETWORK_LOG_MESSAGE_SERVER_CERTIFICATE);
        formatted = replaceLine(formatted, " subject:",
                MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_SUBJECT);
        formatted = replaceLine(formatted, " start date:",
                MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_START_DATE);
        formatted = replaceLine(formatted, " expire date:",
                MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_EXPIRE_DATE);
        formatted = replaceLine(formatted, " subjectAltName:",
                MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_ALT_NAME);
        formatted = replaceLine(formatted, " issuer:",
                MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_ISSUER);
        formatted = replaceLine(formatted, "SSL certificate verify ok.",
                MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_VERIFY_OK);
        return replaceLinePrefix(formatted, "⚠️  Certificate Warning: ",
                "⚠️  ", MessageKeys.NETWORK_LOG_MESSAGE_CERTIFICATE_WARNING);
    }

    private static String replacePrefix(String value, String prefix, String messageKey) {
        if (!value.startsWith(prefix)) {
            return value;
        }
        return I18nUtil.getMessage(messageKey, value.substring(prefix.length()));
    }

    private static String replaceLine(String value, String prefix, String messageKey) {
        return value.replace(prefix, I18nUtil.getMessage(messageKey));
    }

    private static String replaceLinePrefix(String value, String prefix, String preservedPrefix,
                                            String messageKey) {
        return value.replace(prefix, preservedPrefix + I18nUtil.getMessage(messageKey, ""));
    }

    private static String exact(String value, String expected, String messageKey) {
        return expected.equals(value) ? I18nUtil.getMessage(messageKey) : value;
    }
}
