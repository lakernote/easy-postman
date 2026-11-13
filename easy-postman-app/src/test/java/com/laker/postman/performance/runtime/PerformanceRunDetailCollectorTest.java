package com.laker.postman.performance.runtime;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail;
import com.laker.postman.performance.execution.PerformanceRequestExecutionResult;
import com.laker.postman.performance.result.PerformanceResultCollector;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class PerformanceRunDetailCollectorTest {

    @Test
    public void shouldEvictOldestDetailsWhenByteBudgetIsExceeded() {
        // Each row holds a 64 KiB body (~128 KiB estimated); a 512 KiB budget keeps only a few of 100 rows.
        PerformanceRunDetailCollector detailCollector = new PerformanceRunDetailCollector(
                () -> 0,
                () -> 100_000,
                512L * 1024
        );
        PerformanceResultCollector collector = new PerformanceResultCollector(List.of(detailCollector));

        for (int i = 0; i < 100; i++) {
            collector.collect(result("req-" + i, "x".repeat(64 * 1024)), false);
        }

        List<PerformanceWorkerResultDetail> details = detailCollector.snapshot();
        assertTrue(details.size() >= 1 && details.size() <= 4, "retained " + details.size());
        assertEquals(details.get(details.size() - 1).getName(), "req-99");
    }

    private static PerformanceRequestExecutionResult result(String name, String body) {
        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = body;
        response.bodySize = body.length();
        response.costMs = 1;
        response.endTime = System.currentTimeMillis();
        return new PerformanceRequestExecutionResult(
                name + "-id",
                name,
                null,
                response,
                "",
                List.of(),
                false,
                false,
                PerformanceProtocol.HTTP,
                System.currentTimeMillis(),
                1
        );
    }
}
