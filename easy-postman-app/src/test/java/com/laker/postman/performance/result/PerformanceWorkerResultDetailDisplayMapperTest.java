package com.laker.postman.performance.result;

import com.laker.postman.performance.model.ResultNodeInfo;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PerformanceWorkerResultDetailDisplayMapperTest {

    @Test
    public void shouldMapRemoteWorkerDetailToResultTableRow() {
        PerformanceWorkerResultDetail detail = PerformanceWorkerResultDetail.builder()
                .protocol("HTTP")
                .name("remote-failure")
                .errorMsg("script failed")
                .responseCode(500)
                .costMs(25)
                .executionFailed(true)
                .request(PerformanceWorkerResultDetail.DetailRequest.builder()
                        .method("POST")
                        .url("http://localhost/api")
                        .body("{\"id\":1}")
                        .headers(Map.of("Content-Type", List.of("application/json")))
                        .build())
                .response(PerformanceWorkerResultDetail.DetailResponse.builder()
                        .code(500)
                        .body("{\"ok\":false}")
                        .costMs(25)
                        .headers(Map.of("X-Trace", List.of("abc")))
                        .build())
                .testResults(List.of(PerformanceWorkerResultDetail.DetailTestResult.builder()
                        .name("status")
                        .passed(false)
                        .message("expected 200")
                        .build()))
                .build();

        ResultNodeInfo row = PerformanceWorkerResultDetailDisplayMapper.toResultNodeInfo(detail);

        assertEquals(row.name, "remote-failure");
        assertEquals(row.responseCode, 500);
        assertEquals(row.costMs, 25);
        assertEquals(row.req.method, "POST");
        assertEquals(row.req.sentHeadersList.get(0).getKey(), "Content-Type");
        assertEquals(row.req.sentHeadersList.get(0).getValue(), "application/json");
        assertEquals(row.resp.headers.get("X-Trace"), List.of("abc"));
        assertTrue(row.executionFailed);
        assertTrue(row.hasAssertionFailed());
        assertFalse(row.isActuallySuccessful());
    }

    @Test
    public void shouldKeepFileMetadataFromRemoteWorker() {
        PerformanceWorkerResultDetail detail = PerformanceWorkerResultDetail.builder()
                .protocol("HTTP")
                .name("export")
                .responseCode(200)
                .response(PerformanceWorkerResultDetail.DetailResponse.builder()
                        .code(200)
                        .body("")
                        .headers(Map.of("Content-Type", List.of("application/json")))
                        .fileName("orders.xlsx")
                        .detectedFileExtension(".xlsx")
                        .fileContentOmitted(true)
                        .build())
                .build();

        ResultNodeInfo row = PerformanceWorkerResultDetailDisplayMapper.toResultNodeInfo(detail);

        assertEquals(row.resp.fileName, "orders.xlsx");
        assertEquals(row.resp.detectedFileExtension, ".xlsx");
        assertTrue(row.resp.fileContentOmitted);
    }
}
