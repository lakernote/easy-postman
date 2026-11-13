package com.laker.postman.performance.runtime;

import com.laker.postman.http.runtime.file.ResponseFiles;
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
import com.laker.postman.performance.result.PerformanceResultMemoryBudget;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class PerformanceRunDetailCollector implements PerformanceResultListener {
    private static final int COMPACT_DETAIL_ROW_LIMIT = 1_000;

    private final IntSupplier slowRequestThresholdSupplier;
    private final IntSupplier resultRowLimitSupplier;
    private final ArrayDeque<RetainedDetail> details = new ArrayDeque<>();
    private final long retentionBudgetBytes;
    private long retainedBytes;

    public PerformanceRunDetailCollector(IntSupplier slowRequestThresholdSupplier, IntSupplier resultRowLimitSupplier) {
        this(slowRequestThresholdSupplier, resultRowLimitSupplier, PerformanceResultMemoryBudget.defaultBudgetBytes());
    }

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
        long estimatedBytes = PerformanceResultMemoryBudget.estimateBytes(displayInfo);
        PerformanceWorkerResultDetail detail = toDetail(displayInfo, sampleResult);
        synchronized (details) {
            details.addLast(new RetainedDetail(detail, estimatedBytes));
            retainedBytes += estimatedBytes;
            while (details.size() > rowLimit
                    || (details.size() > 1 && retainedBytes > retentionBudgetBytes)) {
                retainedBytes -= details.removeFirst().estimatedBytes();
            }
        }
    }

    public List<PerformanceWorkerResultDetail> snapshot() {
        synchronized (details) {
            return details.stream().map(RetainedDetail::detail).toList();
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
        // 文件响应的正文只是乱码或临时文件提示，不随详情传输
        boolean fileContentOmitted = ResponseFiles.isFileResponse(response);
        return DetailResponse.builder()
                .code(response.code)
                .protocol(response.protocol)
                .headers(copyHeaders(response.headers))
                .body(fileContentOmitted ? "" : response.body)
                .costMs(response.costMs)
                .endTimeMs(response.endTime)
                .bodySize(response.bodySize)
                .headersSize(response.headersSize)
                .fileName(response.fileName)
                .detectedFileExtension(response.detectedFileExtension)
                .fileContentOmitted(fileContentOmitted)
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

    private record RetainedDetail(PerformanceWorkerResultDetail detail, long estimatedBytes) {
    }
}
