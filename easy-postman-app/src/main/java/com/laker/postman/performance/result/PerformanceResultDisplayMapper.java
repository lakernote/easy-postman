package com.laker.postman.performance.result;

import com.laker.postman.performance.core.model.PerformanceProtocol;


import cn.hutool.core.text.CharSequenceUtil;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.performance.model.PerformanceInternalHeaders;
import com.laker.postman.performance.model.PerformanceSampleResult;
import com.laker.postman.performance.model.ResultNodeInfo;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;

@UtilityClass
public class PerformanceResultDisplayMapper {

    private static final int STREAM_RESULT_BODY_PREVIEW_BYTES = 4 * 1024;
    private static final String WS_LAST_MESSAGE_HEADER = "X-Easy-WS-Last-Message";
    private static final String SSE_EVENT_TYPE_HEADER = "X-Easy-SSE-Event-Type";

    public ResultNodeInfo toDisplayNodeInfo(PerformanceSampleResult sampleResult) {
        return toDisplayNodeInfo(sampleResult, false);
    }

    public ResultNodeInfo toDisplayNodeInfo(PerformanceSampleResult sampleResult, boolean compactMode) {
        if (sampleResult == null) {
            return null;
        }
        String displayErrorMsg = resolveDisplayErrorMsg(sampleResult);
        HttpResponse displayResponse = responseForDisplay(sampleResult, displayErrorMsg);
        simplifyForDisplay(sampleResult, displayResponse, compactMode, displayErrorMsg);
        return new ResultNodeInfo(
                sampleResult.getApiName(),
                displayErrorMsg,
                sampleResult.getRequest(),
                displayResponse,
                sampleResult.getTestResults(),
                sampleResult.isExecutionFailed(),
                sampleResult.isInterrupted(),
                sampleResult.getProtocol()
        );
    }

    private void simplifyForDisplay(PerformanceSampleResult sampleResult,
                                    HttpResponse displayResponse,
                                    boolean compactMode,
                                    String displayErrorMsg) {
        if (sampleResult.getRequest() != null) {
            sampleResult.getRequest().simplify();
        }
        if (displayResponse != null) {
            displayResponse.simplify();
            if (sampleResult.getRequest() == null || !sampleResult.getRequest().collectEventInfo) {
                displayResponse.httpEventInfo = null;
            }
            simplifyStreamBodyForResultTable(sampleResult, displayResponse, compactMode, displayErrorMsg);
            PerformanceInternalHeaders.removeInternalHeaders(displayResponse.headers);
        }
    }

    private void simplifyStreamBodyForResultTable(PerformanceSampleResult sampleResult,
                                                  HttpResponse response,
                                                  boolean compactMode,
                                                  String displayErrorMsg) {
        if (!isStreamProtocol(sampleResult.getProtocol())) {
            return;
        }
        if (response == null) {
            return;
        }
        if (compactMode && !sampleResult.isSuccessful()) {
            response.body = buildCompactFailureBody(sampleResult, response, displayErrorMsg);
            return;
        }
        response.body = retainUtf8PrefixWithNotice(response.body, STREAM_RESULT_BODY_PREVIEW_BYTES);
    }

    private String buildCompactFailureBody(PerformanceSampleResult sampleResult,
                                           HttpResponse response,
                                           String displayErrorMsg) {
        Map<String, List<String>> headers = response == null ? null : response.headers;
        StringBuilder body = new StringBuilder(512);
        appendLine(body, "Error", CharSequenceUtil.blankToDefault(displayErrorMsg, sampleResult.getErrorMsg()));
        appendLine(body, "Status", response == null || response.code <= 0 ? "-" : String.valueOf(response.code));
        appendLine(body, "Sent", String.valueOf(sampleResult.getSentMessages()));
        appendLine(body, "Received", String.valueOf(sampleResult.getReceivedMessages()));
        appendLine(body, "Matched", String.valueOf(sampleResult.getMatchedMessages()));

        String lastPreview = sampleResult.getProtocol() == PerformanceProtocol.WEBSOCKET
                ? firstHeaderValue(headers, WS_LAST_MESSAGE_HEADER)
                : firstNonBlank(response == null ? "" : response.body, firstHeaderValue(headers, SSE_EVENT_TYPE_HEADER));
        lastPreview = retainUtf8PrefixWithNotice(lastPreview, STREAM_RESULT_BODY_PREVIEW_BYTES);
        if (CharSequenceUtil.isNotBlank(lastPreview)) {
            body.append("\nLast message preview:\n").append(lastPreview);
        }
        return retainUtf8PrefixWithNotice(body.toString(), STREAM_RESULT_BODY_PREVIEW_BYTES);
    }

    private HttpResponse responseForDisplay(PerformanceSampleResult sampleResult, String displayErrorMsg) {
        if (sampleResult.getResponse() != null) {
            return sampleResult.getResponse();
        }
        if (!sampleResult.isInterrupted() && !sampleResult.isExecutionFailed()) {
            return null;
        }

        // HTTP 在停止时可能没有响应对象；结果表仍要展示耗时和失败原因。
        HttpResponse response = new HttpResponse();
        response.code = sampleResult.getResponseCode();
        response.costMs = Math.max(0L, sampleResult.getElapsedTimeMs());
        response.endTime = sampleResult.getEndTimeMs();
        response.body = CharSequenceUtil.blankToDefault(displayErrorMsg, sampleResult.getErrorMsg());
        return response;
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(label).append(": ").append(CharSequenceUtil.blankToDefault(value, ""));
    }

    private boolean isStreamProtocol(PerformanceProtocol protocol) {
        return protocol == PerformanceProtocol.WEBSOCKET || protocol == PerformanceProtocol.SSE;
    }

    private String firstNonBlank(String first, String second) {
        return CharSequenceUtil.isNotBlank(first) ? first : CharSequenceUtil.blankToDefault(second, "");
    }

    private String resolveDisplayErrorMsg(PerformanceSampleResult sampleResult) {
        if (CharSequenceUtil.isNotBlank(sampleResult.getErrorMsg())) {
            return sampleResult.getErrorMsg();
        }
        if (sampleResult.getResponse() == null) {
            return "";
        }
        return PerformanceInternalHeaders.firstStreamError(sampleResult.getResponse().headers);
    }

    private String firstHeaderValue(Map<String, List<String>> headers, String name) {
        if (headers == null || name == null) {
            return "";
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!name.equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty() || values.get(0) == null) {
                return "";
            }
            return values.get(0);
        }
        return "";
    }

    private String retainUtf8PrefixWithNotice(String value, int maxUtf8Bytes) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (utf8Length(value) <= maxUtf8Bytes) {
            return value;
        }
        String retained = retainUtf8Prefix(value, maxUtf8Bytes);
        return retained + "\n\n[truncated; retained bytes: " + utf8Length(retained)
                + ", original bytes: " + utf8Length(value) + "]";
    }

    private String retainUtf8Prefix(String value, int maxUtf8Bytes) {
        if (value == null || value.isEmpty() || maxUtf8Bytes <= 0) {
            return "";
        }
        int bytes = 0;
        int index = 0;
        while (index < value.length()) {
            CharSpan span = charSpan(value, index, value.length());
            if (bytes + span.utf8Bytes() > maxUtf8Bytes) {
                break;
            }
            bytes += span.utf8Bytes();
            index += span.charCount();
        }
        return value.substring(0, index);
    }

    private int utf8Length(CharSequence value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int bytes = 0;
        for (int i = 0; i < value.length(); ) {
            CharSpan span = charSpan(value, i, value.length());
            bytes += span.utf8Bytes();
            i += span.charCount();
        }
        return bytes;
    }

    private CharSpan charSpan(CharSequence text, int index, int end) {
        char ch = text.charAt(index);
        if (ch <= 0x7F) {
            return new CharSpan(1, 1);
        }
        if (ch <= 0x7FF) {
            return new CharSpan(1, 2);
        }
        if (Character.isHighSurrogate(ch)
                && index + 1 < end
                && Character.isLowSurrogate(text.charAt(index + 1))) {
            return new CharSpan(2, 4);
        }
        return new CharSpan(1, 3);
    }

    private record CharSpan(int charCount, int utf8Bytes) {
    }
}
