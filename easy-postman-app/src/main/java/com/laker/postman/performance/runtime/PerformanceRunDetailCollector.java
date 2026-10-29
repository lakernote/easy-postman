package com.laker.postman.performance.runtime;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.script.model.TestResult;
import com.laker.postman.performance.core.model.PerformanceSampleRecord;
import com.laker.postman.performance.model.PerformanceResultListener;
import com.laker.postman.performance.model.PerformanceResultRetentionPolicy;
import com.laker.postman.performance.model.PerformanceSampleEvent;
import com.laker.postman.performance.model.PerformanceSampleResult;
import com.laker.postman.performance.model.ResultNodeInfo;
import com.laker.postman.performance.result.PerformanceResultDisplayMapper;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail.DetailRequest;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail.DetailResponse;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail.DetailTestResult;
import lombok.RequiredArgsConstructor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

@RequiredArgsConstructor
public final class PerformanceRunDetailCollector implements PerformanceResultListener {
    private static final int COMPACT_DETAIL_ROW_LIMIT = 1_000;

    private final IntSupplier slowRequestThresholdSupplier;
    private final IntSupplier resultRowLimitSupplier;
    private final ArrayDeque<PerformanceWorkerResultDetail> details = new ArrayDeque<>();

    @Override
    public void onSample(PerformanceSampleEvent event) {
        if (event == null || event.sampleRecord() == null) {
            return;
        }
        PerformanceSampleRecord sampleRecord = event.sampleRecord();
        int slowRequestThresholdMs = slowRequestThresholdSupplier == null ? 0 : slowRequestThresholdSupplier.getAsInt();
        if (!PerformanceResultRetentionPolicy.shouldRecord(
                event.isEfficientMode(),
                sampleRecord.isSuccessful(),
                sampleRecord.getElapsedTimeMs(),
                slowRequestThresholdMs)) {
            return;
        }

        PerformanceSampleResult sampleResult = event.getSampleResult();
        if (sampleResult == null) {
            return;
        }
        ResultNodeInfo displayInfo = PerformanceResultDisplayMapper.toDisplayNodeInfo(sampleResult, event.isEfficientMode());
        int rowLimit = event.isEfficientMode()
                ? COMPACT_DETAIL_ROW_LIMIT
                : Math.max(1, resultRowLimitSupplier == null ? COMPACT_DETAIL_ROW_LIMIT : resultRowLimitSupplier.getAsInt());
        synchronized (details) {
            details.addLast(toDetail(displayInfo, sampleResult));
            while (details.size() > rowLimit) {
                details.removeFirst();
            }
        }
    }

    public List<PerformanceWorkerResultDetail> snapshot() {
        synchronized (details) {
            return List.copyOf(details);
        }
    }

    private PerformanceWorkerResultDetail toDetail(ResultNodeInfo info, PerformanceSampleResult sampleResult) {
        if (info == null) {
            return PerformanceWorkerResultDetail.builder().build();
        }
        return PerformanceWorkerResultDetail.builder()
                .protocol(info.protocol.name())
                .name(info.name)
                .errorMsg(info.errorMsg)
                .responseCode(responseCode(info, sampleResult))
                .costMs(costMs(info, sampleResult))
                .executionFailed(info.executionFailed)
                .cancelled(info.cancelled)
                .request(toDetailRequest(info.req))
                .response(toDetailResponse(info.resp))
                .testResults(toDetailTests(info.testResults))
                .build();
    }

    private int responseCode(ResultNodeInfo info, PerformanceSampleResult sampleResult) {
        if (info.responseCode > 0 || sampleResult == null) {
            return info.responseCode;
        }
        return Math.max(0, sampleResult.getResponseCode());
    }

    private int costMs(ResultNodeInfo info, PerformanceSampleResult sampleResult) {
        if (info.costMs > 0 || sampleResult == null) {
            return info.costMs;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, sampleResult.getElapsedTimeMs()));
    }

    private DetailRequest toDetailRequest(PreparedRequest request) {
        if (request == null) {
            return null;
        }
        return DetailRequest.builder()
                .method(request.method)
                .url(request.url)
                .body(request.sentRequestBody)
                .headers(sentHeadersListToMap(request))
                .build();
    }

    private Map<String, List<String>> sentHeadersListToMap(PreparedRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (request == null || request.sentHeadersList == null) {
            return headers;
        }
        for (HttpHeader header : request.sentHeadersList) {
            if (header == null || header.getKey() == null || header.getKey().isBlank()) {
                continue;
            }
            headers.computeIfAbsent(header.getKey(), ignored -> new ArrayList<>())
                    .add(header.getValue());
        }
        return headers;
    }

    private DetailResponse toDetailResponse(HttpResponse response) {
        if (response == null) {
            return null;
        }
        return DetailResponse.builder()
                .code(response.code)
                .protocol(response.protocol)
                .headers(copyHeaders(response.headers))
                .body(response.body)
                .costMs(response.costMs)
                .endTimeMs(response.endTime)
                .bodySize(response.bodySize)
                .headersSize(response.headersSize)
                .build();
    }

    private Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (source == null) {
            return headers;
        }
        source.forEach((key, values) -> headers.put(key, values == null ? List.of() : List.copyOf(values)));
        return headers;
    }

    private List<DetailTestResult> toDetailTests(List<TestResult> tests) {
        if (tests == null || tests.isEmpty()) {
            return List.of();
        }
        return tests.stream()
                .map(test -> DetailTestResult.builder()
                        .name(test.name)
                        .passed(test.passed)
                        .message(test.message)
                        .build())
                .toList();
    }
}
