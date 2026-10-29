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
    }

    @Value
    @Builder(toBuilder = true)
    public static class DetailTestResult {
        String name;
        boolean passed;
        String message;
    }
}
