package com.laker.postman.http.runtime.error;

import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NetworkErrorMessageResolver {
    private static final String SOCKS_MALFORMED_REPLY = "Malformed reply from SOCKS server";
    private static final String SOCKS_BAD_VERSION_REPLY = "Reply from SOCKS server has bad version";
    private static final Pattern UNKNOWN_HOST_PAREN_PATTERN = Pattern.compile("\\(([^()]+)\\)\\s*$");
    private static final Pattern UNKNOWN_HOST_PREFIX_PATTERN = Pattern.compile("^([A-Za-z0-9._-]+)(?::\\s.*)?$");

    private NetworkErrorMessageResolver() {
    }

    public static String toUserFriendlyMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        if (throwable instanceof UnknownHostException) {
            return resolveUnknownHostMessage(throwable.getMessage());
        }
        return toUserFriendlyMessage(throwable.getMessage());
    }

    public static String toUserFriendlyMessage(String rawMessage) {
        String normalized = rawMessage == null ? "" : rawMessage.trim();
        if (isSocksProtocolMismatch(normalized)) {
            return I18nUtil.getMessage(MessageKeys.NETWORK_ERROR_PROXY_SOCKS_MALFORMED, normalized);
        }
        return normalized;
    }

    public static boolean isSocksProtocolMismatch(String message) {
        return message != null
                && (message.contains(SOCKS_MALFORMED_REPLY) || message.contains(SOCKS_BAD_VERSION_REPLY));
    }

    public static String toLogMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        if (throwable instanceof UnknownHostException) {
            return resolveUnknownHostMessage(throwable.getMessage());
        }
        return toUserFriendlyMessage(throwable);
    }

    private static String resolveUnknownHostMessage(String rawMessage) {
        String host = extractUnknownHost(rawMessage);
        if (!host.isBlank()) {
            return I18nUtil.getMessage(MessageKeys.NETWORK_ERROR_UNKNOWN_HOST, host);
        }
        return I18nUtil.getMessage(MessageKeys.ERROR_SERVER_UNREACHABLE);
    }

    private static String extractUnknownHost(String rawMessage) {
        String normalized = rawMessage == null ? "" : rawMessage.trim();
        if (normalized.isBlank()) {
            return "";
        }

        Matcher parenMatcher = UNKNOWN_HOST_PAREN_PATTERN.matcher(normalized);
        if (parenMatcher.find()) {
            String host = parenMatcher.group(1).trim();
            if (isLikelyHost(host)) {
                return host;
            }
        }

        Matcher prefixMatcher = UNKNOWN_HOST_PREFIX_PATTERN.matcher(normalized);
        if (prefixMatcher.matches()) {
            String host = prefixMatcher.group(1).trim();
            if (isLikelyHost(host)) {
                return host;
            }
        }

        return "";
    }

    private static boolean isLikelyHost(String value) {
        if (value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '.' || ch == '-' || ch == '_')) {
                return false;
            }
        }
        return true;
    }
}
