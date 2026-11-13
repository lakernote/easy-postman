package com.laker.postman.performance.core.worker;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class PerformanceWorkerResultDetail {
    String protocol;
    String name;
    String errorMsg;
    int responseCode;
    int costMs;
    boolean executionFailed;
    boolean cancelled;
    /** 请求开始时间（epoch ms），未知为 -1。 */
    @Builder.Default
    long startTimeMs = -1L;
    /** 首字节耗时（ms），未知为 -1。 */
    @Builder.Default
    long latencyMs = -1L;
    /** 连接耗时（含排队/DNS/TCP/TLS，ms），未知为 -1。 */
    @Builder.Default
    long connectMs = -1L;
    DetailRequest request;
    DetailResponse response;
    List<DetailTestResult> testResults;

    @Value
    @Builder(toBuilder = true)
    public static class DetailRequest {
        String method;
        String url;
        String body;
        Map<String, List<String>> headers;
    }

    @Value
    @Builder(toBuilder = true)
    public static class DetailResponse {
        int code;
        String protocol;
        Map<String, List<String>> headers;
        String body;
        long costMs;
        long endTimeMs;
        long bodySize;
        long headersSize;
        /** 响应文件名，仅文件响应有值。 */
        String fileName;
        /** 文件头识别出、与 Content-Type 不符的扩展名。 */
        String detectedFileExtension;
        /** 文件正文未随详情传输，只展示文件信息。 */
        boolean fileContentOmitted;
    }

    @Value
    @Builder(toBuilder = true)
    public static class DetailTestResult {
        String name;
        boolean passed;
        String message;
    }
}
