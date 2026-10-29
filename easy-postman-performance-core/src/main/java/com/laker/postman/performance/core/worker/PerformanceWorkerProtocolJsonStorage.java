package com.laker.postman.performance.core.worker;

import com.laker.postman.performance.core.model.PerformanceTrendSnapshot;
import com.laker.postman.performance.core.report.PerformanceJsonReport;
import com.laker.postman.performance.core.report.PerformanceJsonReportJsonStorage;
import com.laker.postman.performance.core.run.PerformanceRunPlan;
import com.laker.postman.performance.core.run.PerformanceRunPlanJsonStorage;
import com.laker.postman.performance.core.run.PerformanceRunStatus;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail.DetailRequest;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail.DetailResponse;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail.DetailTestResult;
import com.laker.postman.util.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PerformanceWorkerProtocolJsonStorage {
    private final PerformanceRunPlanJsonStorage planStorage = new PerformanceRunPlanJsonStorage();
    private final PerformanceWorkerAssignmentJsonStorage assignmentStorage = new PerformanceWorkerAssignmentJsonStorage();
    private final PerformanceJsonReportJsonStorage reportStorage = new PerformanceJsonReportJsonStorage();

    public String toJson(Object value) {
        return JsonUtil.toJsonPrettyStr(toMap(value));
    }

    public PerformanceWorkerRunRequest runRequestFromJson(String json) {
        Map<String, Object> root = root(json);
        PerformanceRunPlan plan = root.get("plan") == null
                ? null
                : planStorage.fromJson(JsonUtil.toJsonStr(root.get("plan")));
        PerformanceWorkerAssignment assignment = root.get("assignment") == null
                ? null
                : assignmentStorage.fromJson(JsonUtil.toJsonStr(root.get("assignment")));
        return PerformanceWorkerRunRequest.builder()
                .runId(stringValue(root, "runId", ""))
                .plan(plan)
                .assignment(assignment)
                .build();
    }

    public PerformanceWorkerHealthResponse healthResponseFromJson(String json) {
        Map<String, Object> root = root(json);
        return PerformanceWorkerHealthResponse.builder()
                .status(stringValue(root, "status", ""))
                .workerId(stringValue(root, "workerId", ""))
                .host(stringValue(root, "host", ""))
                .port(intValue(root, "port", 0))
                .workerProtocolVersion(stringValue(root, "workerProtocolVersion", ""))
                .build();
    }

    public PerformanceWorkerRunStatusResponse statusResponseFromJson(String json) {
        Map<String, Object> root = root(json);
        PerformanceJsonReport report = root.get("report") == null
                ? null
                : reportStorage.fromJson(JsonUtil.toJsonStr(root.get("report")));
        return PerformanceWorkerRunStatusResponse.builder()
                .runId(stringValue(root, "runId", ""))
                .workerId(stringValue(root, "workerId", ""))
                .status(stringValue(root, "status", PerformanceRunStatus.UNKNOWN))
                .activeUsers(intValue(root, "activeUsers", 0))
                .totalUsers(intValue(root, "totalUsers", 0))
                .activeWebSocketConnections(intValue(root, "activeWebSocketConnections", 0))
                .activeSseStreams(intValue(root, "activeSseStreams", 0))
                .totalRequests(longValue(root, "totalRequests", 0))
                .successRequests(longValue(root, "successRequests", 0))
                .failedRequests(longValue(root, "failedRequests", 0))
                .qps(doubleValue(root, "qps", 0))
                .report(report)
                .trendSnapshot(readTrendSnapshot(objectMap(root.get("trendSnapshot"))))
                .error(stringValue(root, "error", ""))
                .build();
    }

    public PerformanceWorkerRunAcceptedResponse acceptedResponseFromJson(String json) {
        Map<String, Object> root = root(json);
        return PerformanceWorkerRunAcceptedResponse.builder()
                .runId(stringValue(root, "runId", ""))
                .workerId(stringValue(root, "workerId", ""))
                .status(stringValue(root, "status", PerformanceRunStatus.ACCEPTED))
                .message(stringValue(root, "message", ""))
                .build();
    }

    public PerformanceWorkerRunResultResponse resultResponseFromJson(String json) {
        Map<String, Object> root = root(json);
        PerformanceJsonReport report = root.get("report") == null
                ? null
                : reportStorage.fromJson(JsonUtil.toJsonStr(root.get("report")));
        return PerformanceWorkerRunResultResponse.builder()
                .runId(stringValue(root, "runId", ""))
                .workerId(stringValue(root, "workerId", ""))
                .status(stringValue(root, "status", PerformanceRunStatus.UNKNOWN))
                .report(report)
                .error(stringValue(root, "error", ""))
                .build();
    }

    public PerformanceWorkerRunDetailsResponse detailsResponseFromJson(String json) {
        Map<String, Object> root = root(json);
        return PerformanceWorkerRunDetailsResponse.builder()
                .runId(stringValue(root, "runId", ""))
                .workerId(stringValue(root, "workerId", ""))
                .status(stringValue(root, "status", PerformanceRunStatus.UNKNOWN))
                .details(readDetails(listValue(root.get("details"))))
                .error(stringValue(root, "error", ""))
                .build();
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof PerformanceWorkerRunRequest request) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("runId", request.getRunId());
            json.put("plan", request.getPlan() == null
                    ? null
                    : JsonUtil.convertValue(JsonUtil.readTree(planStorage.toJson(request.getPlan())), Map.class));
            json.put("assignment", request.getAssignment() == null
                    ? null
                    : JsonUtil.convertValue(JsonUtil.readTree(assignmentStorage.toJson(request.getAssignment())), Map.class));
            return json;
        }
        if (value instanceof PerformanceWorkerHealthResponse response) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("status", response.getStatus());
            json.put("workerId", response.getWorkerId());
            json.put("host", response.getHost());
            json.put("port", response.getPort());
            json.put("workerProtocolVersion", response.getWorkerProtocolVersion());
            return json;
        }
        if (value instanceof PerformanceWorkerRunAcceptedResponse response) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("runId", response.getRunId());
            json.put("workerId", response.getWorkerId());
            json.put("status", response.getStatus());
            json.put("message", response.getMessage());
            return json;
        }
        if (value instanceof PerformanceWorkerRunStatusResponse response) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("runId", response.getRunId());
            json.put("workerId", response.getWorkerId());
            json.put("status", response.getStatus());
            json.put("activeUsers", response.getActiveUsers());
            json.put("totalUsers", response.getTotalUsers());
            json.put("activeWebSocketConnections", response.getActiveWebSocketConnections());
            json.put("activeSseStreams", response.getActiveSseStreams());
            json.put("totalRequests", response.getTotalRequests());
            json.put("successRequests", response.getSuccessRequests());
            json.put("failedRequests", response.getFailedRequests());
            json.put("qps", response.getQps());
            json.put("report", response.getReport() == null ? null : reportStorage.toMap(response.getReport()));
            json.put("trendSnapshot", trendSnapshotToMap(response.getTrendSnapshot()));
            json.put("error", response.getError());
            return json;
        }
        if (value instanceof PerformanceWorkerRunResultResponse response) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("runId", response.getRunId());
            json.put("workerId", response.getWorkerId());
            json.put("status", response.getStatus());
            json.put("report", response.getReport() == null ? null : reportStorage.toMap(response.getReport()));
            json.put("error", response.getError());
            return json;
        }
        if (value instanceof PerformanceWorkerRunDetailsResponse response) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("runId", response.getRunId());
            json.put("workerId", response.getWorkerId());
            json.put("status", response.getStatus());
            json.put("details", detailsToList(response.getDetails()));
            json.put("error", response.getError());
            return json;
        }
        if (value instanceof PerformanceWorkerErrorResponse response) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("error", response.getError());
            return json;
        }
        return JsonUtil.convertValue(value, Map.class);
    }

    private List<Map<String, Object>> detailsToList(List<PerformanceWorkerResultDetail> details) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (details == null) {
            return values;
        }
        for (PerformanceWorkerResultDetail detail : details) {
            values.add(detailToMap(detail));
        }
        return values;
    }

    private Map<String, Object> detailToMap(PerformanceWorkerResultDetail detail) {
        PerformanceWorkerResultDetail safeDetail = detail == null
                ? PerformanceWorkerResultDetail.builder().build()
                : detail;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("protocol", safeDetail.getProtocol());
        json.put("name", safeDetail.getName());
        json.put("errorMsg", safeDetail.getErrorMsg());
        json.put("responseCode", safeDetail.getResponseCode());
        json.put("costMs", safeDetail.getCostMs());
        json.put("executionFailed", safeDetail.isExecutionFailed());
        json.put("cancelled", safeDetail.isCancelled());
        json.put("request", requestToMap(safeDetail.getRequest()));
        json.put("response", responseToMap(safeDetail.getResponse()));
        json.put("testResults", testsToList(safeDetail.getTestResults()));
        return json;
    }

    private Map<String, Object> requestToMap(DetailRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("method", request.getMethod());
        json.put("url", request.getUrl());
        json.put("body", request.getBody());
        json.put("headers", request.getHeaders());
        return json;
    }

    private Map<String, Object> responseToMap(DetailResponse response) {
        if (response == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("code", response.getCode());
        json.put("protocol", response.getProtocol());
        json.put("headers", response.getHeaders());
        json.put("body", response.getBody());
        json.put("costMs", response.getCostMs());
        json.put("endTimeMs", response.getEndTimeMs());
        json.put("bodySize", response.getBodySize());
        json.put("headersSize", response.getHeadersSize());
        return json;
    }

    private List<Map<String, Object>> testsToList(List<DetailTestResult> tests) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (tests == null) {
            return values;
        }
        for (DetailTestResult test : tests) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("name", test.getName());
            json.put("passed", test.isPassed());
            json.put("message", test.getMessage());
            values.add(json);
        }
        return values;
    }

    private List<PerformanceWorkerResultDetail> readDetails(List<Object> values) {
        List<PerformanceWorkerResultDetail> details = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> json = objectMap(value);
            details.add(PerformanceWorkerResultDetail.builder()
                    .protocol(stringValue(json, "protocol", "HTTP"))
                    .name(stringValue(json, "name", ""))
                    .errorMsg(stringValue(json, "errorMsg", ""))
                    .responseCode(intValue(json, "responseCode", 0))
                    .costMs(intValue(json, "costMs", 0))
                    .executionFailed(booleanValue(json, "executionFailed", false))
                    .cancelled(booleanValue(json, "cancelled", false))
                    .request(readRequest(objectMap(json.get("request"))))
                    .response(readResponse(objectMap(json.get("response"))))
                    .testResults(readTests(listValue(json.get("testResults"))))
                    .build());
        }
        return details;
    }

    private DetailRequest readRequest(Map<String, Object> json) {
        if (json.isEmpty()) {
            return null;
        }
        return DetailRequest.builder()
                .method(stringValue(json, "method", ""))
                .url(stringValue(json, "url", ""))
                .body(stringValue(json, "body", ""))
                .headers(headersValue(json.get("headers")))
                .build();
    }

    private DetailResponse readResponse(Map<String, Object> json) {
        if (json.isEmpty()) {
            return null;
        }
        return DetailResponse.builder()
                .code(intValue(json, "code", 0))
                .protocol(stringValue(json, "protocol", ""))
                .headers(headersValue(json.get("headers")))
                .body(stringValue(json, "body", ""))
                .costMs(longValue(json, "costMs", 0))
                .endTimeMs(longValue(json, "endTimeMs", 0))
                .bodySize(longValue(json, "bodySize", 0))
                .headersSize(longValue(json, "headersSize", 0))
                .build();
    }

    private List<DetailTestResult> readTests(List<Object> values) {
        List<DetailTestResult> tests = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> json = objectMap(value);
            tests.add(DetailTestResult.builder()
                    .name(stringValue(json, "name", ""))
                    .passed(booleanValue(json, "passed", false))
                    .message(stringValue(json, "message", ""))
                    .build());
        }
        return tests;
    }

    private Map<String, List<String>> headersValue(Object value) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : objectMap(value).entrySet()) {
            List<String> values = new ArrayList<>();
            for (Object item : listValue(entry.getValue())) {
                values.add(item == null ? "" : String.valueOf(item));
            }
            headers.put(entry.getKey(), values);
        }
        return headers;
    }

    private Map<String, Object> trendSnapshotToMap(PerformanceTrendSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("activeUsers", snapshot.activeUsers());
        json.put("activeWebSocketConnections", snapshot.activeWebSocketConnections());
        json.put("activeSseStreams", snapshot.activeSseStreams());
        json.put("overview", trendMetricsToMap(snapshot.overview()));
        json.put("http", trendMetricsToMap(snapshot.http()));
        json.put("webSocket", trendMetricsToMap(snapshot.webSocket()));
        json.put("sse", trendMetricsToMap(snapshot.sse()));
        return json;
    }

    private Map<String, Object> trendMetricsToMap(PerformanceTrendSnapshot.ProtocolWindowMetrics metrics) {
        if (metrics == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("samples", metrics.samples());
        json.put("failures", metrics.failures());
        json.put("failurePercent", finiteOrNull(metrics.failurePercent()));
        json.put("sampleRate", finiteOrNull(metrics.sampleRate()));
        json.put("avgDurationMs", finiteOrNull(metrics.avgDurationMs()));
        json.put("sentMessages", metrics.sentMessages());
        json.put("receivedMessages", metrics.receivedMessages());
        json.put("matchedMessages", metrics.matchedMessages());
        json.put("sentRate", finiteOrNull(metrics.sentRate()));
        json.put("receivedRate", finiteOrNull(metrics.receivedRate()));
        json.put("matchedRate", finiteOrNull(metrics.matchedRate()));
        json.put("avgFirstMessageLatencyMs", finiteOrNull(metrics.avgFirstMessageLatencyMs()));
        return json;
    }

    private PerformanceTrendSnapshot readTrendSnapshot(Map<String, Object> json) {
        if (json.isEmpty()) {
            return null;
        }
        return new PerformanceTrendSnapshot(
                intValue(json, "activeUsers", 0),
                intValue(json, "activeWebSocketConnections", 0),
                intValue(json, "activeSseStreams", 0),
                readTrendMetrics(objectMap(json.get("overview"))),
                readTrendMetrics(objectMap(json.get("http"))),
                readTrendMetrics(objectMap(json.get("webSocket"))),
                readTrendMetrics(objectMap(json.get("sse")))
        );
    }

    private PerformanceTrendSnapshot.ProtocolWindowMetrics readTrendMetrics(Map<String, Object> json) {
        if (json.isEmpty()) {
            return emptyTrendMetrics();
        }
        return new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                intValue(json, "samples", 0),
                intValue(json, "failures", 0),
                doubleValue(json, "failurePercent", Double.NaN),
                doubleValue(json, "sampleRate", Double.NaN),
                doubleValue(json, "avgDurationMs", Double.NaN),
                intValue(json, "sentMessages", 0),
                intValue(json, "receivedMessages", 0),
                intValue(json, "matchedMessages", 0),
                doubleValue(json, "sentRate", Double.NaN),
                doubleValue(json, "receivedRate", Double.NaN),
                doubleValue(json, "matchedRate", Double.NaN),
                doubleValue(json, "avgFirstMessageLatencyMs", Double.NaN)
        );
    }

    private PerformanceTrendSnapshot.ProtocolWindowMetrics emptyTrendMetrics() {
        return new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                0, 0, Double.NaN, Double.NaN, Double.NaN, 0, 0, 0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN
        );
    }

    private Object finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private Map<String, Object> root(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        return objectMap(JsonUtil.convertValue(JsonUtil.readTree(json), Map.class));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<Object> listValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
    }

    private String stringValue(Map<String, Object> json, String key, String defaultValue) {
        Object value = json.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private long longValue(Map<String, Object> json, String key, long defaultValue) {
        Object value = json.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int intValue(Map<String, Object> json, String key, int defaultValue) {
        long value = longValue(json, key, defaultValue);
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private boolean booleanValue(Map<String, Object> json, String key, boolean defaultValue) {
        Object value = json.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private double doubleValue(Map<String, Object> json, String key, double defaultValue) {
        Object value = json.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
