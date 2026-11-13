package com.laker.postman.performance.result;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.performance.model.ResultNodeInfo;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.script.model.TestResult;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;

/**
 * Bounds the heap held by retained performance result rows. Row limits alone are not enough:
 * 3000 rows of 1 MiB bodies already need ~3 GiB, so retention also evicts by estimated bytes.
 */
@UtilityClass
public class PerformanceResultMemoryBudget {
    static final long MIN_BUDGET_BYTES = 16L * 1024 * 1024;
    static final long MAX_BUDGET_BYTES = 256L * 1024 * 1024;
    private static final long ROW_OVERHEAD_BYTES = 1024L;

    /**
     * Budget for one result store: a quarter of the max heap, clamped to [16 MiB, 256 MiB].
     */
    public static long defaultBudgetBytes() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        if (maxMemory <= 0 || maxMemory == Long.MAX_VALUE) {
            return MAX_BUDGET_BYTES;
        }
        return Math.max(MIN_BUDGET_BYTES, Math.min(MAX_BUDGET_BYTES, maxMemory / 4));
    }

    /**
     * Cheap upper-bound-ish estimate of the bytes a retained row keeps alive (string chars count as 2 bytes).
     */
    public static long estimateBytes(ResultNodeInfo info) {
        if (info == null) {
            return 0L;
        }
        long chars = length(info.name) + length(info.errorMsg)
                + request(info.req) + response(info.resp) + tests(info.testResults);
        return ROW_OVERHEAD_BYTES + chars * 2;
    }

    private static long request(PreparedRequest req) {
        if (req == null) {
            return 0L;
        }
        return length(req.url) + length(req.sentUrl) + length(req.body) + length(req.sentRequestBody)
                + length(req.prescript) + length(req.postscript)
                + headers(req.headersList) + headers(req.sentHeadersList);
    }

    private static long response(HttpResponse resp) {
        if (resp == null) {
            return 0L;
        }
        long total = length(resp.body);
        Map<String, List<String>> headers = resp.headers;
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                total += length(entry.getKey());
                if (entry.getValue() != null) {
                    for (String value : entry.getValue()) {
                        total += length(value);
                    }
                }
            }
        }
        return total;
    }

    private static long headers(List<HttpHeader> headers) {
        if (headers == null) {
            return 0L;
        }
        long total = 0L;
        for (HttpHeader header : headers) {
            if (header != null) {
                total += length(header.getKey()) + length(header.getValue());
            }
        }
        return total;
    }

    private static long tests(List<TestResult> tests) {
        if (tests == null) {
            return 0L;
        }
        long total = 0L;
        for (TestResult test : tests) {
            if (test != null) {
                total += length(test.name) + length(test.message);
            }
        }
        return total;
    }

    private static long length(String value) {
        return value == null ? 0L : value.length();
    }
}
