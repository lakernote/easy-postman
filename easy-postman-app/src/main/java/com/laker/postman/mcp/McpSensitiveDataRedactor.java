package com.laker.postman.mcp;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class McpSensitiveDataRedactor {
    private static final String SENSITIVE_NAME_PATTERN =
            "(?:api[-_ ]?key|x[-_ ]?api[-_ ]?key|token|api[-_ ]?token|access[-_ ]?token|auth[-_ ]?token|refresh[-_ ]?token|"
                    + "id[-_ ]?token|session[-_ ]?token|security[-_ ]?token|client[-_ ]?secret|"
                    + "consumer[-_ ]?secret|password|passwd|secret|signature|private[-_ ]?key|"
                    + "client[-_ ]?assertion|authorization|cookie)";
    private static final Pattern QUERY_PARAMETER = Pattern.compile("([?&])([^=&#]+)=([^&#\\s]*)");
    private static final Pattern URL_USER_INFO = Pattern.compile("(?i)(://)([^/?#@]+)@");
    private static final Pattern EMBEDDED_URL = Pattern.compile("(?i)https?://[^\\s\\\"'<>]+");
    private static final Pattern AUTHORIZATION_VALUE = Pattern.compile(
            "(?i)\\b(Bearer|Basic)\\s+[^\\s,;]+"
    );
    private static final Pattern QUOTED_SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(?<![?&\\p{Alnum}_])([\\\"']?" + SENSITIVE_NAME_PATTERN
                    + "[\\\"']?\\s*[:=]\\s*)([\\\"'])(.*?)\\2"
    );
    private static final Pattern BARE_SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(?<![?&\\p{Alnum}_])([\\\"']?" + SENSITIVE_NAME_PATTERN
                    + "[\\\"']?\\s*[:=]\\s*)(?![\\\"']|Bearer\\b|Basic\\b)[^\\s,;]+"
    );
    private static final Pattern FILE_URI = Pattern.compile("(?i)file:/+[^\\s,;\\\"'<>]+");
    private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)\\b[A-Z]:\\\\[^\\s,;\\\"'<>]+");
    private static final Pattern UNIX_PATH = Pattern.compile(
            "(?<![:/\\p{Alnum}_])/(?:[^\\s/]+/)*[^\\s,;:\\x22'<>]+"
    );
    private static final Set<String> SENSITIVE_QUERY_NAMES = Set.of(
            "apikey",
            "accesstoken",
            "authtoken",
            "token",
            "password",
            "passwd",
            "secret",
            "signature",
            "sig",
            "clientsecret",
            "consumersecret",
            "refreshtoken",
            "idtoken",
            "sessiontoken",
            "securitytoken",
            "privatekey",
            "clientassertion",
            "authorization",
            "cookie"
    );

    private McpSensitiveDataRedactor() {
    }

    static String url(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        String withoutUserInfo = URL_USER_INFO.matcher(value).replaceAll("$1<redacted>@");
        Matcher matcher = QUERY_PARAMETER.matcher(withoutUserInfo);
        StringBuffer redacted = new StringBuffer(withoutUserInfo.length());
        while (matcher.find()) {
            if (!isSensitiveQueryName(matcher.group(2))) {
                matcher.appendReplacement(redacted, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(
                    redacted,
                    Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + "=<redacted>")
            );
        }
        matcher.appendTail(redacted);
        return redacted.toString();
    }

    static String url(String value, Iterable<String> sensitiveValues) {
        return redactKnownValues(url(value), sensitiveValues);
    }

    static String message(String value) {
        String redacted = value == null ? "" : value;
        redacted = FILE_URI.matcher(redacted).replaceAll("<path>");
        redacted = WINDOWS_PATH.matcher(redacted).replaceAll("<path>");
        redacted = UNIX_PATH.matcher(redacted).replaceAll("<path>");
        redacted = url(redacted);
        redacted = AUTHORIZATION_VALUE.matcher(redacted).replaceAll("$1 <redacted>");
        return redactAssignments(redacted);
    }

    static String message(String value, Iterable<String> sensitiveValues) {
        return message(redactKnownValues(value, sensitiveValues));
    }

    static String payload(String value) {
        String redacted = redactEmbeddedUrls(value == null ? "" : value);
        redacted = AUTHORIZATION_VALUE.matcher(redacted).replaceAll("$1 <redacted>");
        return redactAssignments(redacted);
    }

    static String payload(String value, Iterable<String> sensitiveValues) {
        return payload(redactKnownValues(value, sensitiveValues));
    }

    private static String redactKnownValues(String value, Iterable<String> sensitiveValues) {
        String redacted = value == null ? "" : value;
        if (sensitiveValues == null) {
            return redacted;
        }
        Iterable<String> orderedValues = StreamSupport.stream(sensitiveValues.spliterator(), false)
                .filter(secret -> secret != null && secret.length() >= 4)
                .distinct()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();
        for (String secret : orderedValues) {
            redacted = redacted.replace(secret, "<redacted>");
        }
        return redacted;
    }

    private static String redactAssignments(String value) {
        String redacted = QUOTED_SENSITIVE_ASSIGNMENT.matcher(value).replaceAll("$1$2<redacted>$2");
        return BARE_SENSITIVE_ASSIGNMENT.matcher(redacted).replaceAll("$1<redacted>");
    }

    private static String redactEmbeddedUrls(String value) {
        Matcher matcher = EMBEDDED_URL.matcher(value);
        StringBuffer redacted = new StringBuffer(value.length());
        while (matcher.find()) {
            matcher.appendReplacement(redacted, Matcher.quoteReplacement(url(matcher.group())));
        }
        matcher.appendTail(redacted);
        return redacted.toString();
    }

    private static boolean isSensitiveQueryName(String encodedName) {
        String decoded;
        try {
            decoded = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            decoded = encodedName;
        }
        return isSensitiveName(decoded);
    }

    static boolean isSensitiveName(String name) {
        String normalized = name == null
                ? ""
                : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (SENSITIVE_QUERY_NAMES.contains(normalized)) {
            return true;
        }
        return normalized.endsWith("apikey")
                || normalized.endsWith("accesstoken")
                || normalized.endsWith("authtoken")
                || normalized.endsWith("token")
                || normalized.endsWith("password")
                || normalized.endsWith("secret")
                || normalized.endsWith("signature")
                || normalized.endsWith("sessiontoken")
                || normalized.endsWith("securitytoken");
    }
}
