package com.laker.postman.mcp;

import com.laker.postman.functional.execution.FunctionalRequestExecutionResult;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.workspace.cli.WorkspaceRunObserver;
import com.laker.postman.workspace.cli.WorkspaceRunReport;
import com.laker.postman.workspace.cli.WorkspaceRunSelectedRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class McpResponseCapture implements WorkspaceRunObserver {
    private static final int MAX_CAPTURED_HEADER_CHARACTERS = 128 * 1024;
    private static final int MAX_CAPTURED_HEADER_NAMES = 512;
    private static final int MAX_HEADER_VALUES_PER_NAME = 16;
    private static final int MAX_SINGLE_HEADER_VALUE_CHARACTERS = 2048;
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "www-authenticate",
            "proxy-authenticate",
            "authentication-info",
            "x-api-key",
            "api-key"
    );

    private final int maxBytes;
    private final int maxResponses;
    private final List<String> sensitiveValues;
    private final List<Map<String, Object>> responses = new ArrayList<>();
    private int capturedBytes;
    private int capturedHeaderCharacters;
    private int capturedHeaderNames;
    private int omittedResponses;

    McpResponseCapture(int maxBytes, int maxResponses) {
        this(maxBytes, maxResponses, List.of());
    }

    McpResponseCapture(int maxBytes, int maxResponses, List<String> sensitiveValues) {
        this.maxBytes = maxBytes;
        this.maxResponses = Math.max(0, maxResponses);
        this.sensitiveValues = sensitiveValues == null ? List.of() : List.copyOf(sensitiveValues);
    }

    @Override
    public void onRequestCompleted(int iteration,
                                   WorkspaceRunSelectedRequest selected,
                                   FunctionalRequestExecutionResult execution,
                                   WorkspaceRunReport.RequestResult report) {
        if (responses.size() >= maxResponses) {
            omittedResponses++;
            return;
        }
        HttpResponse response = execution.getResponse();
        String body = response == null
                ? ""
                : McpSensitiveDataRedactor.payload(safe(response.body), sensitiveValues);
        int remaining = Math.max(0, maxBytes - capturedBytes);
        TruncatedText capturedBody = truncateUtf8(body, remaining);
        capturedBytes += capturedBody.bytes();
        CapturedHeaders capturedHeaders = response == null
                ? new CapturedHeaders(Map.of(), false)
                : captureHeaders(response.headers);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("iteration", iteration);
        item.put("requestId", safe(selected.request().getId()));
        item.put("name", safe(selected.request().getName()));
        item.put("path", selected.path());
        item.put("status", report.status());
        item.put("statusCode", response == null ? null : response.code);
        item.put("protocol", response == null ? "" : safe(response.protocol));
        item.put("durationMs", report.durationMs());
        item.put("headers", capturedHeaders.headers());
        item.put("headersTruncated", capturedHeaders.truncated());
        item.put("body", capturedBody.text());
        item.put("bodyBytes", response != null && response.bodySize > 0
                ? response.bodySize
                : utf8Length(body));
        item.put("bodyTruncated", capturedBody.truncated());
        item.put("error", McpSensitiveDataRedactor.message(report.error(), sensitiveValues));
        responses.add(item);
    }

    List<Map<String, Object>> responses() {
        return List.copyOf(responses);
    }

    int omittedResponses() {
        return omittedResponses;
    }

    private CapturedHeaders captureHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return new CapturedHeaders(Map.of(), false);
        }
        Map<String, List<String>> redacted = new LinkedHashMap<>();
        boolean truncated = false;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (capturedHeaderCharacters >= MAX_CAPTURED_HEADER_CHARACTERS
                    || capturedHeaderNames >= MAX_CAPTURED_HEADER_NAMES) {
                truncated = true;
                break;
            }
            String name = entry.getKey();
            List<String> values = entry.getValue();
            String safeName = safe(name);
            capturedHeaderNames++;
            if (SENSITIVE_HEADERS.contains(safeName.toLowerCase(Locale.ROOT))
                    || McpSensitiveDataRedactor.isSensitiveName(safeName)) {
                redacted.put(safeName, List.of("<redacted>"));
                capturedHeaderCharacters += "<redacted>".length();
            } else {
                List<String> safeValues = new ArrayList<>();
                if (values != null) {
                    for (String value : values) {
                        if (safeValues.size() >= MAX_HEADER_VALUES_PER_NAME
                                || capturedHeaderCharacters >= MAX_CAPTURED_HEADER_CHARACTERS) {
                            truncated = true;
                            break;
                        }
                        String safeValue = McpSensitiveDataRedactor.url(value, sensitiveValues);
                        int remaining = MAX_CAPTURED_HEADER_CHARACTERS - capturedHeaderCharacters;
                        int limit = Math.min(remaining, MAX_SINGLE_HEADER_VALUE_CHARACTERS);
                        String capturedValue = truncateCharacters(safeValue, limit);
                        safeValues.add(capturedValue);
                        capturedHeaderCharacters += capturedValue.length();
                        if (capturedValue.length() < safeValue.length()) {
                            truncated = true;
                        }
                    }
                }
                redacted.put(safeName, List.copyOf(safeValues));
            }
        }
        return new CapturedHeaders(Map.copyOf(redacted), truncated || redacted.size() < headers.size());
    }

    private static TruncatedText truncateUtf8(String value, int byteLimit) {
        if (byteLimit <= 0) {
            return new TruncatedText("", 0, !value.isEmpty());
        }
        int end = 0;
        int used = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int charCount = Character.charCount(codePoint);
            int codePointBytes = utf8Bytes(codePoint);
            if (used + codePointBytes > byteLimit) {
                break;
            }
            used += codePointBytes;
            end += charCount;
        }
        return new TruncatedText(value.substring(0, end), used, end < value.length());
    }

    private static long utf8Length(String value) {
        long bytes = 0L;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            bytes += utf8Bytes(codePoint);
            offset += Character.charCount(codePoint);
        }
        return bytes;
    }

    private static int utf8Bytes(int codePoint) {
        if (codePoint <= 0x7f) {
            return 1;
        }
        if (codePoint <= 0x7ff) {
            return 2;
        }
        if (codePoint <= 0xffff) {
            return Character.isSurrogate((char) codePoint) ? 1 : 3;
        }
        return 4;
    }

    private static String truncateCharacters(String value, int maxCharacters) {
        if (value == null || value.length() <= maxCharacters) {
            return safe(value);
        }
        if (maxCharacters <= 0) {
            return "";
        }
        int end = maxCharacters;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record TruncatedText(String text, int bytes, boolean truncated) {
    }

    private record CapturedHeaders(Map<String, List<String>> headers, boolean truncated) {
    }
}
