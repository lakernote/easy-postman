package com.laker.postman.performance.core.worker;

import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceTrendSnapshot;
import com.laker.postman.performance.core.plan.PerformanceCorePlanDocument;
import com.laker.postman.performance.core.plan.PerformanceCorePlanNode;
import com.laker.postman.performance.core.report.PerformanceJsonReport;
import com.laker.postman.performance.core.report.PerformanceJsonReportMetadata;
import com.laker.postman.performance.core.report.PerformanceJsonReportSummary;
import com.laker.postman.performance.core.report.PerformanceJsonReportSummaryMapper;
import com.laker.postman.performance.core.run.PerformanceRunPlan;
import com.laker.postman.performance.core.run.PerformanceRunStatus;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PerformanceWorkerProtocolJsonStorageTest {

    @Test
    public void shouldRoundTripWorkerRunRequestAndResponses() {
        PerformanceWorkerRunRequest request = PerformanceWorkerRunRequest.builder()
                .runId("run-1")
                .plan(emptyPlan())
                .assignment(PerformanceWorkerAssignment.builder()
                        .runId("run-1")
                        .workerId("worker-a")
                        .assignmentId("assignment-a")
                        .endpoint(new PerformanceWorkerEndpoint("127.0.0.1", 19090))
                        .threadGroups(List.of(new PerformanceWorkerThreadGroupAssignment("0", 0, 2, 3)))
                        .build())
                .build();
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();

        PerformanceWorkerHealthResponse health = PerformanceWorkerHealthResponse.builder()
                .status("UP")
                .workerId("worker-a")
                .host("127.0.0.1")
                .port(19090)
                .workerProtocolVersion(PerformanceWorkerProtocol.CURRENT_VERSION)
                .build();
        PerformanceWorkerHealthResponse loadedHealth = storage.healthResponseFromJson(storage.toJson(health));

        assertEquals(loadedHealth.getStatus(), "UP");
        assertEquals(loadedHealth.getWorkerProtocolVersion(), PerformanceWorkerProtocol.CURRENT_VERSION);
        assertEquals(loadedHealth.getPort(), 19090);

        PerformanceWorkerRunRequest loaded = storage.runRequestFromJson(storage.toJson(request));

        assertEquals(loaded.getRunId(), "run-1");
        assertEquals(loaded.getAssignment().getWorkerId(), "worker-a");
        assertEquals(loaded.getAssignment().getThreadGroups().get(0).getFirstVirtualUserIndex(), 2);
        assertEquals(loaded.getPlan().getTestPlan().getRoot().getName(), "run plan");

        PerformanceJsonReport report = PerformanceJsonReport.builder()
                .metadata(PerformanceJsonReportMetadata.builder().runId("run-1").status(PerformanceRunStatus.SUCCESS).build())
                .summary(PerformanceJsonReportSummary.builder().totalRequests(4L).successRequests(4L).build())
                .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                .build();
        PerformanceWorkerRunResultResponse result = PerformanceWorkerRunResultResponse.builder()
                .runId(report.getMetadata().getRunId())
                .workerId(report.getMetadata().getSource())
                .status(report.getMetadata().getStatus())
                .report(report)
                .error(report.getMetadata().getError())
                .build();
        PerformanceWorkerRunResultResponse loadedResult = storage.resultResponseFromJson(storage.toJson(result));

        assertEquals(loadedResult.getStatus(), PerformanceRunStatus.SUCCESS);
        assertEquals(loadedResult.getReport().getSummary().getTotalRequests(), 4L);

        PerformanceWorkerRunStatusResponse status = PerformanceWorkerRunStatusResponse.builder()
                .runId("run-1")
                .workerId("worker-a")
                .status(PerformanceRunStatus.RUNNING)
                .activeUsers(2)
                .totalUsers(7)
                .activeWebSocketConnections(3)
                .activeSseStreams(4)
                .totalRequests(10L)
                .successRequests(9L)
                .failedRequests(1L)
                .qps(12.5)
                .report(report)
                .trendSnapshot(new PerformanceTrendSnapshot(
                        2,
                        3,
                        4,
                        new PerformanceTrendSnapshot.ProtocolWindowMetrics(10, 1, 10.0, 5.0, 20.0, 0, 0, 0, 0, 0, 0, Double.NaN, 90.0),
                        new PerformanceTrendSnapshot.ProtocolWindowMetrics(10, 1, 10.0, 5.0, 20.0, 0, 0, 0, 0, 0, 0, Double.NaN, 90.0),
                        new PerformanceTrendSnapshot.ProtocolWindowMetrics(0, 0, Double.NaN, Double.NaN, Double.NaN, 6, 7, 8, 3.0, 3.5, 4.0, 11.0),
                        new PerformanceTrendSnapshot.ProtocolWindowMetrics(0, 0, Double.NaN, Double.NaN, Double.NaN, 0, 9, 10, 0, 4.5, 5.0, 12.0)
                ))
                .build();
        PerformanceWorkerRunStatusResponse loadedStatus = storage.statusResponseFromJson(storage.toJson(status));

        assertEquals(loadedStatus.getActiveUsers(), 2);
        assertEquals(loadedStatus.getTotalUsers(), 7);
        assertEquals(loadedStatus.getActiveWebSocketConnections(), 3);
        assertEquals(loadedStatus.getActiveSseStreams(), 4);
        assertEquals(loadedStatus.getTotalRequests(), 10L);
        assertEquals(loadedStatus.getSuccessRequests(), 9L);
        assertEquals(loadedStatus.getFailedRequests(), 1L);
        assertEquals(loadedStatus.getQps(), 12.5);
        assertEquals(loadedStatus.getReport().getSummary().getTotalRequests(), 4L);
        assertEquals(loadedStatus.getTrendSnapshot().activeUsers(), 2);
        assertEquals(loadedStatus.getTrendSnapshot().webSocket().receivedMessages(), 7);
        assertEquals(loadedStatus.getTrendSnapshot().sse().matchedRate(), 5.0);
        assertEquals(loadedStatus.getTrendSnapshot().http().maxDurationMs(), 90.0);
        assertTrue(Double.isNaN(loadedStatus.getTrendSnapshot().sse().maxDurationMs()));

        PerformanceWorkerRunAcceptedResponse accepted = PerformanceWorkerRunAcceptedResponse.builder()
                .runId("run-1")
                .workerId("worker-a")
                .build();
        PerformanceWorkerRunAcceptedResponse loadedAccepted = storage.acceptedResponseFromJson(storage.toJson(accepted));

        assertEquals(loadedAccepted.getRunId(), "run-1");
        assertEquals(loadedAccepted.getWorkerId(), "worker-a");
        assertEquals(loadedAccepted.getStatus(), PerformanceRunStatus.ACCEPTED);
    }

    @Test
    public void workerRunRequestJsonShouldOnlySendEnabledPlanNodes() {
        PerformanceWorkerRunRequest request = PerformanceWorkerRunRequest.builder()
                .runId("run-1")
                .plan(PerformanceRunPlan.builder()
                        .testPlan(new PerformanceCorePlanDocument(PerformanceCorePlanNode.builder()
                                .name("run plan")
                                .type(NodeType.ROOT)
                                .children(List.of(
                                        PerformanceCorePlanNode.builder()
                                                .name("disabled group")
                                                .type(NodeType.THREAD_GROUP)
                                                .enabled(false)
                                                .build(),
                                        PerformanceCorePlanNode.builder()
                                                .name("enabled group")
                                                .type(NodeType.THREAD_GROUP)
                                                .build()
                                ))
                                .build()))
                        .build())
                .build();

        String json = new PerformanceWorkerProtocolJsonStorage().toJson(request);

        assertFalse(json.contains("disabled group"));
    }

    private static PerformanceRunPlan emptyPlan() {
        return PerformanceRunPlan.builder()
                .generatedBy("test")
                .testPlan(new PerformanceCorePlanDocument(PerformanceCorePlanNode.builder()
                        .name("run plan")
                        .type(NodeType.ROOT)
                        .build()))
                .build();
    }

    @Test
    public void shouldRoundTripFileMetadataInResultDetails() {
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        PerformanceWorkerRunDetailsResponse details = PerformanceWorkerRunDetailsResponse.builder()
                .runId("run-1")
                .workerId("worker-a")
                .status(PerformanceRunStatus.SUCCESS)
                .details(List.of(PerformanceWorkerResultDetail.builder()
                        .protocol("HTTP")
                        .name("export")
                        .response(PerformanceWorkerResultDetail.DetailResponse.builder()
                                .code(200)
                                .body("")
                                .bodySize(2048)
                                .fileName("orders.xlsx")
                                .detectedFileExtension(".xlsx")
                                .fileContentOmitted(true)
                                .build())
                        .build()))
                .build();

        PerformanceWorkerResultDetail.DetailResponse loaded =
                storage.detailsResponseFromJson(storage.toJson(details)).getDetails().get(0).getResponse();

        assertEquals(loaded.getFileName(), "orders.xlsx");
        assertEquals(loaded.getDetectedFileExtension(), ".xlsx");
        assertEquals(loaded.isFileContentOmitted(), true);
        assertEquals(loaded.getBodySize(), 2048L);

        PerformanceWorkerResultDetail.DetailResponse legacy = storage.detailsResponseFromJson(
                "{\"details\":[{\"response\":{\"code\":200,\"body\":\"ok\"}}]}").getDetails().get(0).getResponse();
        assertEquals(legacy.getFileName(), null);
        assertFalse(legacy.isFileContentOmitted(), "旧 worker 不带文件字段时按普通响应处理");
    }

    @Test
    public void shouldRoundTripTimingInResultDetailsAndDefaultToUnknownForLegacyWorkers() {
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        PerformanceWorkerRunDetailsResponse details = PerformanceWorkerRunDetailsResponse.builder()
                .runId("run-1")
                .details(List.of(PerformanceWorkerResultDetail.builder()
                        .name("slow")
                        .startTimeMs(1_700_000_000_123L)
                        .latencyMs(20_500L)
                        .connectMs(20_000L)
                        .build()))
                .build();

        PerformanceWorkerResultDetail loaded = storage.detailsResponseFromJson(storage.toJson(details)).getDetails().get(0);
        assertEquals(loaded.getStartTimeMs(), 1_700_000_000_123L);
        assertEquals(loaded.getLatencyMs(), 20_500L);
        assertEquals(loaded.getConnectMs(), 20_000L);

        PerformanceWorkerResultDetail legacy = storage.detailsResponseFromJson(
                "{\"details\":[{\"name\":\"old\"}]}").getDetails().get(0);
        assertEquals(legacy.getStartTimeMs(), -1L);
        assertEquals(legacy.getLatencyMs(), -1L);
        assertEquals(legacy.getConnectMs(), -1L);
    }
}
