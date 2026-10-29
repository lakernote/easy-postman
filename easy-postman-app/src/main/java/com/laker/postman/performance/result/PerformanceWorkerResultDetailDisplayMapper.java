package com.laker.postman.performance.result;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.script.model.TestResult;
import com.laker.postman.performance.model.ResultNodeInfo;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail.DetailRequest;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail.DetailResponse;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail.DetailTestResult;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UtilityClass
public class PerformanceWorkerResultDetailDisplayMapper {

    public ResultNodeInfo toResultNodeInfo(PerformanceWorkerResultDetail detail) {
        if (detail == null) {
            return null;
        }
        HttpResponse response = toResponse(detail.getResponse(), detail.getResponseCode(), detail.getCostMs());
        return new ResultNodeInfo(
                detail.getName(),
                detail.getErrorMsg(),
                toRequest(detail.getRequest()),
                response,
                toTests(detail.getTestResults()),
                detail.isExecutionFailed(),
                detail.isCancelled(),
                toProtocol(detail.getProtocol())
        );
    }

    private PreparedRequest toRequest(DetailRequest source) {
        if (source == null) {
            return null;
        }
        PreparedRequest request = new PreparedRequest();
        request.method = source.getMethod();
        request.url = source.getUrl();
        request.sentRequestBody = source.getBody();
        request.sentHeadersList = toHttpHeaders(source.getHeaders());
        return request;
    }

    private HttpResponse toResponse(DetailResponse source, int responseCode, int costMs) {
        HttpResponse response = new HttpResponse();
        if (source != null) {
            response.code = source.getCode();
            response.protocol = source.getProtocol();
            response.headers = source.getHeaders();
            response.body = source.getBody();
            response.costMs = source.getCostMs();
            response.endTime = source.getEndTimeMs();
            response.bodySize = source.getBodySize();
            response.headersSize = source.getHeadersSize();
        }
        if (response.code <= 0) {
            response.code = Math.max(0, responseCode);
        }
        if (response.costMs <= 0) {
            response.costMs = Math.max(0, costMs);
        }
        return response;
    }

    private List<TestResult> toTests(List<DetailTestResult> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(test -> new TestResult(test.getName(), test.isPassed(), test.getMessage()))
                .toList();
    }

    private List<HttpHeader> toHttpHeaders(Map<String, List<String>> headers) {
        List<HttpHeader> result = new ArrayList<>();
        if (headers == null || headers.isEmpty()) {
            return result;
        }
        headers.forEach((key, values) -> {
            if (key == null || key.isBlank()) {
                return;
            }
            if (values == null || values.isEmpty()) {
                result.add(new HttpHeader(true, key, ""));
                return;
            }
            for (String value : values) {
                result.add(new HttpHeader(true, key, value == null ? "" : value));
            }
        });
        return result;
    }

    private PerformanceProtocol toProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return PerformanceProtocol.HTTP;
        }
        try {
            return PerformanceProtocol.valueOf(protocol);
        } catch (IllegalArgumentException ignored) {
            return PerformanceProtocol.HTTP;
        }
    }
}
