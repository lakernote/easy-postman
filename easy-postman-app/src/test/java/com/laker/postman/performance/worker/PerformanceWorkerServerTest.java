package com.laker.postman.performance.worker;

import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.plan.PerformanceCorePlanDocument;
import com.laker.postman.performance.core.plan.PerformanceCorePlanNode;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.model.PerformanceRealtimeMetrics;
import com.laker.postman.performance.core.model.PerformanceStatsCollector;
import com.laker.postman.performance.core.model.RequestResult;
import com.laker.postman.performance.core.report.PerformanceJsonReport;
import com.laker.postman.performance.core.report.PerformanceJsonReportMetadata;
import com.laker.postman.performance.core.report.PerformanceJsonReportSummary;
import com.laker.postman.performance.core.report.PerformanceJsonReportSummaryMapper;
import com.laker.postman.performance.core.run.PerformanceRunPlan;
import com.laker.postman.performance.core.worker.PerformanceWorkerAssignment;
import com.laker.postman.performance.core.worker.PerformanceWorkerHealthResponse;
import com.laker.postman.performance.core.worker.PerformanceWorkerProtocol;
import com.laker.postman.performance.core.worker.PerformanceWorkerProtocolJsonStorage;
import com.laker.postman.performance.core.worker.PerformanceWorkerResultDetail;
import com.laker.postman.performance.core.worker.PerformanceWorkerRunDetailsResponse;
import com.laker.postman.performance.core.worker.PerformanceWorkerRunRequest;
import com.laker.postman.performance.core.worker.PerformanceWorkerRunResultResponse;
import com.laker.postman.performance.core.worker.PerformanceWorkerRunStatusResponse;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class PerformanceWorkerServerTest {

    @Test
    public void shouldExposeWorkerProtocolVersionInHealthResponse() throws Exception {
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> PerformanceJsonReport.builder().build()
        )) {
            server.start();

            HttpResponse<String> health = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/api/performance/v1/health"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            PerformanceWorkerHealthResponse response = storage.healthResponseFromJson(health.body());

            assertEquals(health.statusCode(), 200, health.body());
            assertEquals(response.getStatus(), "UP");
            assertEquals(response.getWorkerProtocolVersion(), PerformanceWorkerProtocol.CURRENT_VERSION);
            assertTrue(response.usesCurrentProtocol());
        }
    }

    @Test
    public void shouldAcceptRunAndExposeResultOverHttp() throws Exception {
        AtomicReference<PerformanceWorkerRunRequest> captured = new AtomicReference<>();
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> {
                    captured.set(request);
                    return PerformanceJsonReport.builder()
                            .metadata(PerformanceJsonReportMetadata.builder()
                                    .runId(request.getRunId())
                                    .source("worker")
                                    .status("SUCCESS")
                                    .build())
                            .summary(PerformanceJsonReportSummary.builder()
                                    .totalRequests(2L)
                                    .successRequests(2L)
                                    .build())
                            .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                            .build();
                }
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String runId = "run-http";
            submitRun(client, storage, server.getPort(), runId);

            PerformanceWorkerRunStatusResponse statusResponse =
                    awaitStatus(client, storage, server.getPort(), runId, "SUCCESS");
            assertEquals(statusResponse.getStatus(), "SUCCESS");
            PerformanceWorkerRunRequest executedRequest = captured.get();
            assertNotNull(executedRequest);
            assertEquals(executedRequest.getRunId(), runId);

            HttpResponse<String> result = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort()
                                    + "/api/performance/v1/runs/" + runId + "/result"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            PerformanceWorkerRunResultResponse resultResponse = storage.resultResponseFromJson(result.body());

            assertEquals(result.statusCode(), 200, result.body());
            assertEquals(resultResponse.getReport().getSummary().getTotalRequests(), 2L);
        }
    }

    @Test
    public void shouldExposeRetainedResultDetailsOverHttp() throws Exception {
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> {
                    control.bindResultDetailsSupplier(() -> List.of(PerformanceWorkerResultDetail.builder()
                            .protocol("HTTP")
                            .name("failing-api")
                            .errorMsg("assert failed")
                            .responseCode(500)
                            .costMs(17)
                            .executionFailed(false)
                            .request(PerformanceWorkerResultDetail.DetailRequest.builder()
                                    .method("GET")
                                    .url("http://localhost/fail")
                                    .headers(Map.of("X-Test", List.of("1")))
                                    .build())
                            .response(PerformanceWorkerResultDetail.DetailResponse.builder()
                                    .code(500)
                                    .protocol("HTTP/1.1")
                                    .headers(Map.of("Content-Type", List.of("application/json")))
                                    .body("{\"error\":true}")
                                    .costMs(17)
                                    .build())
                            .testResults(List.of(PerformanceWorkerResultDetail.DetailTestResult.builder()
                                    .name("status")
                                    .passed(false)
                                    .message("expected 200")
                                    .build()))
                            .build()));
                    return PerformanceJsonReport.builder()
                            .metadata(PerformanceJsonReportMetadata.builder()
                                    .runId(request.getRunId())
                                    .source("worker")
                                    .status("SUCCESS")
                                    .build())
                            .summary(PerformanceJsonReportSummary.builder()
                                    .totalRequests(1L)
                                    .failedRequests(1L)
                                    .build())
                            .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                            .build();
                }
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            submitRun(client, storage, server.getPort(), "run-details");
            awaitStatus(client, storage, server.getPort(), "run-details", "SUCCESS");

            HttpResponse<String> details = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort()
                                    + "/api/performance/v1/runs/run-details/details"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            PerformanceWorkerRunDetailsResponse detailsResponse = storage.detailsResponseFromJson(details.body());

            assertEquals(details.statusCode(), 200, details.body());
            assertEquals(detailsResponse.getStatus(), "SUCCESS");
            assertEquals(detailsResponse.getDetails().size(), 1);
            PerformanceWorkerResultDetail detail = detailsResponse.getDetails().get(0);
            assertEquals(detail.getName(), "failing-api");
            assertEquals(detail.getResponseCode(), 500);
            assertEquals(detail.getCostMs(), 17);
            assertEquals(detail.getRequest().getHeaders().get("X-Test"), List.of("1"));
            assertEquals(detail.getTestResults().get(0).getMessage(), "expected 200");
        }
    }

    @Test
    public void shouldNotifyWorkerRunLifecycleForConsoleFeedback() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        PerformanceWorkerServerListener listener = new PerformanceWorkerServerListener() {
            @Override
            public void onRunAccepted(String runId, String workerId) {
                events.add("accepted:" + runId);
            }

            @Override
            public void onRunStarted(String runId, String workerId) {
                events.add("started:" + runId);
            }

            @Override
            public void onRunCompleted(String runId,
                                       String workerId,
                                       String status,
                                       PerformanceJsonReportSummary summary,
                                       long elapsedMs,
                                       String error) {
                events.add("completed:" + runId + ":" + status + ":" + summary.getTotalRequests());
                completed.countDown();
            }
        };
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> PerformanceJsonReport.builder()
                        .metadata(PerformanceJsonReportMetadata.builder()
                                .runId(request.getRunId())
                                .source("worker")
                                .status("SUCCESS")
                                .build())
                        .summary(PerformanceJsonReportSummary.builder()
                                .totalRequests(3L)
                                .successRequests(3L)
                                .build())
                        .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                        .build(),
                listener
        )) {
            server.start();
            submitRun(HttpClient.newHttpClient(), storage, server.getPort(), "run-events");

            assertTrue(completed.await(1, TimeUnit.SECONDS));
            assertTrue(events.indexOf("accepted:run-events") >= 0, events.toString());
            assertTrue(events.indexOf("started:run-events") > events.indexOf("accepted:run-events"), events.toString());
            assertTrue(events.contains("completed:run-events:SUCCESS:3"), events.toString());
        }
    }

    @Test
    public void shouldExposeLiveWorkerProgressForStatusAndConsoleFeedback() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch progressSeen = new CountDownLatch(1);
        AtomicReference<PerformanceWorkerRunStatusResponse> progressRef = new AtomicReference<>();
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        PerformanceWorkerServerListener listener = new PerformanceWorkerServerListener() {
            @Override
            public void onRunProgress(String runId,
                                      String workerId,
                                      String status,
                                      int activeUsers,
                                      int totalUsers,
                                      long totalRequests,
                                      long successRequests,
                                      long failedRequests,
                                      double qps) {
                progressRef.set(PerformanceWorkerRunStatusResponse.builder()
                        .runId(runId)
                        .workerId(workerId)
                        .status(status)
                        .activeUsers(activeUsers)
                        .totalUsers(totalUsers)
                        .totalRequests(totalRequests)
                        .successRequests(successRequests)
                        .failedRequests(failedRequests)
                        .qps(qps)
                        .build());
                progressSeen.countDown();
            }
        };
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder()
                        .host("127.0.0.1")
                        .port(0)
                        .progressIntervalMs(10L)
                        .build(),
                (request, control) -> {
                    control.recordProgress(3, 7);
                    started.countDown();
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                    control.recordProgress(0, 7);
                    return PerformanceJsonReport.builder()
                            .metadata(PerformanceJsonReportMetadata.builder()
                                    .runId(request.getRunId())
                                    .source("worker")
                                    .status("SUCCESS")
                                    .build())
                            .summary(PerformanceJsonReportSummary.builder()
                                    .totalRequests(1L)
                                    .successRequests(1L)
                                    .build())
                            .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                            .build();
                },
                listener
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            submitRun(client, storage, server.getPort(), "run-progress");

            assertTrue(started.await(1, TimeUnit.SECONDS));
            HttpResponse<String> status = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort()
                                    + "/api/performance/v1/runs/run-progress"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            PerformanceWorkerRunStatusResponse statusResponse = storage.statusResponseFromJson(status.body());

            assertEquals(status.statusCode(), 200, status.body());
            assertEquals(statusResponse.getActiveUsers(), 3);
            assertEquals(statusResponse.getTotalUsers(), 7);
            assertTrue(progressSeen.await(1, TimeUnit.SECONDS));
            assertEquals(progressRef.get().getActiveUsers(), 3);
            assertEquals(progressRef.get().getTotalUsers(), 7);
            release.countDown();
            awaitStatus(client, storage, server.getPort(), "run-progress", "SUCCESS");
        }
    }

    @Test
    public void shouldExposeLiveWebSocketReportBeforeAnyCompletedSample() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> {
                    PerformanceRealtimeMetrics realtimeMetrics = new PerformanceRealtimeMetrics();
                    long startTimeMs = System.currentTimeMillis() - 1_000L;
                    realtimeMetrics.recordWebSocketSessionStart("ws-1", startTimeMs, "ws-api", "WS Echo");
                    realtimeMetrics.recordWebSocketSent("ws-1");
                    realtimeMetrics.recordWebSocketReceived("ws-1");
                    control.bindRealtimeMetrics(
                            realtimeMetrics::liveSnapshot,
                            () -> realtimeMetrics.liveSnapshot(System.currentTimeMillis()).webSocket().activeSessions(),
                            () -> realtimeMetrics.liveSnapshot(System.currentTimeMillis()).sse().activeSessions()
                    );
                    control.recordProgress(1, 1);
                    started.countDown();
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                    realtimeMetrics.recordWebSocketSessionEnd("ws-1");
                    return PerformanceJsonReport.builder()
                            .metadata(PerformanceJsonReportMetadata.builder()
                                    .runId(request.getRunId())
                                    .source("worker")
                                    .status("SUCCESS")
                                    .build())
                            .summary(PerformanceJsonReportSummary.builder().build())
                            .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                            .build();
                }
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            submitRun(client, storage, server.getPort(), "run-live-ws");
            assertTrue(started.await(1, TimeUnit.SECONDS));

            HttpResponse<String> status = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort()
                                    + "/api/performance/v1/runs/run-live-ws"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            PerformanceWorkerRunStatusResponse statusResponse = storage.statusResponseFromJson(status.body());

            assertEquals(status.statusCode(), 200, status.body());
            assertEquals(statusResponse.getActiveWebSocketConnections(), 1);
            assertNotNull(statusResponse.getReport());
            assertEquals(statusResponse.getReport().getProtocols().get("WEBSOCKET").getTotal().getTotal(), 1L);
            assertEquals(statusResponse.getReport().getProtocols().get("WEBSOCKET").getTotal()
                    .getStream().getSentMessages(), 1L);
            assertEquals(statusResponse.getReport().getProtocols().get("WEBSOCKET").getTotal()
                    .getStream().getReceivedMessages(), 1L);
            assertEquals(statusResponse.getReport().getProtocols().get("WEBSOCKET").getApis().get(0)
                    .getApiId(), "ws-api");
            release.countDown();
            awaitStatus(client, storage, server.getPort(), "run-live-ws", "SUCCESS");
        }
    }

    @Test
    public void shouldReturnLightweightStatusWhenReportIsNotRequested() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> {
                    PerformanceStatsCollector collector = new PerformanceStatsCollector();
                    collector.record(new RequestResult(1_000L, 1_010L, true,
                            "api", "API", PerformanceProtocol.HTTP));
                    control.bindStatsCollector(collector);
                    control.recordProgress(1, 1);
                    started.countDown();
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                    return PerformanceJsonReport.builder()
                            .metadata(PerformanceJsonReportMetadata.builder()
                                    .runId(request.getRunId())
                                    .source("worker")
                                    .status("SUCCESS")
                                    .build())
                            .summary(PerformanceJsonReportSummary.builder()
                                    .totalRequests(1L)
                                    .successRequests(1L)
                                    .build())
                            .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                            .build();
                }
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            submitRun(client, storage, server.getPort(), "run-light-status");
            assertTrue(started.await(1, TimeUnit.SECONDS));

            HttpResponse<String> status = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort()
                                    + "/api/performance/v1/runs/run-light-status?report=false"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            PerformanceWorkerRunStatusResponse statusResponse = storage.statusResponseFromJson(status.body());

            assertEquals(status.statusCode(), 200, status.body());
            assertEquals(statusResponse.getTotalRequests(), 1L);
            assertEquals(statusResponse.getSuccessRequests(), 1L);
            assertEquals(statusResponse.getFailedRequests(), 0L);
            assertEquals(statusResponse.getQps(), 100.0);
            assertNull(statusResponse.getReport());
            release.countDown();
            awaitStatus(client, storage, server.getPort(), "run-light-status", "SUCCESS");
        }
    }

    @Test
    public void shouldKeepStoppedRunNonTerminalUntilReportIsReady() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopSeen = new CountDownLatch(1);
        CountDownLatch releaseReport = new CountDownLatch(1);
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> {
                    started.countDown();
                    while (control.isRunning()) {
                        Thread.sleep(10);
                    }
                    stopSeen.countDown();
                    assertTrue(releaseReport.await(2, TimeUnit.SECONDS));
                    return PerformanceJsonReport.builder()
                            .metadata(PerformanceJsonReportMetadata.builder()
                                    .runId(request.getRunId())
                                    .source("worker")
                                    .status("STOPPED")
                                    .stopped(true)
                                    .build())
                            .summary(PerformanceJsonReportSummary.builder()
                                    .totalRequests(1L)
                                    .successRequests(1L)
                                    .build())
                            .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                            .build();
                }
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            PerformanceWorkerRunRequest runRequest = PerformanceWorkerRunRequest.builder()
                    .runId("run-stop")
                    .plan(emptyPlan())
                    .assignment(PerformanceWorkerAssignment.builder().runId("run-stop").workerId("worker-a").build())
                    .build();

            client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/api/performance/v1/runs"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(storage.toJson(runRequest)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(started.await(1, TimeUnit.SECONDS));

            HttpResponse<String> stop = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/api/performance/v1/runs/run-stop/stop"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            PerformanceWorkerRunStatusResponse stopResponse = storage.statusResponseFromJson(stop.body());

            assertEquals(stop.statusCode(), 200, stop.body());
            assertEquals(stopResponse.getStatus(), "STOPPING");
            assertTrue(stopSeen.await(1, TimeUnit.SECONDS));

            HttpResponse<String> stoppingStatus = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/api/performance/v1/runs/run-stop"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(storage.statusResponseFromJson(stoppingStatus.body()).getStatus(), "STOPPING");

            releaseReport.countDown();
            PerformanceWorkerRunResultResponse resultResponse = awaitStoppedResult(client, storage, server.getPort());
            assertEquals(resultResponse.getStatus(), "STOPPED");
            assertNotNull(resultResponse.getReport());
            assertEquals(resultResponse.getReport().getSummary().getTotalRequests(), 1L);
        }
    }

    @Test
    public void shouldIncludeLateInterruptedSamplesInStoppedWorkerReport() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopSeen = new CountDownLatch(1);
        CountDownLatch lateSampleRecorded = new CountDownLatch(1);
        PerformanceStatsCollector collector = new PerformanceStatsCollector();
        List<PerformanceWorkerResultDetail> details = new CopyOnWriteArrayList<>();
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> {
                    control.bindStatsCollector(collector);
                    control.bindResultDetailsSupplier(() -> details);
                    control.recordProgress(1, 1);
                    started.countDown();
                    while (control.isRunning()) {
                        Thread.sleep(10);
                    }
                    stopSeen.countDown();
                    Thread lateRecorder = new Thread(() -> {
                        try {
                            Thread.sleep(50);
                            RequestResult result = new RequestResult(
                                    1_000L,
                                    1_100L,
                                    false,
                                    "ws-api",
                                    "WS API",
                                    PerformanceProtocol.WEBSOCKET
                            );
                            result.sentMessages = 15;
                            result.receivedMessages = 15;
                            collector.record(result);
                            details.add(PerformanceWorkerResultDetail.builder()
                                    .protocol("WEBSOCKET")
                                    .name("WS API")
                                    .errorMsg("stopped")
                                    .responseCode(101)
                                    .costMs(100)
                                    .executionFailed(true)
                                    .build());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            control.recordProgress(0, 1);
                            lateSampleRecorded.countDown();
                        }
                    }, "late-interrupted-sample");
                    lateRecorder.start();
                    return PerformanceJsonReport.builder()
                            .metadata(PerformanceJsonReportMetadata.builder()
                                    .runId(request.getRunId())
                                    .source("worker")
                                    .status("STOPPED")
                                    .stopped(true)
                                    .build())
                            .summary(PerformanceJsonReportSummary.builder().build())
                            .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                            .build();
                }
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            submitRun(client, storage, server.getPort(), "run-late-stop");
            assertTrue(started.await(1, TimeUnit.SECONDS));

            HttpResponse<String> stop = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort()
                                    + "/api/performance/v1/runs/run-late-stop/stop"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(stop.statusCode(), 200, stop.body());
            assertTrue(stopSeen.await(1, TimeUnit.SECONDS));
            PerformanceWorkerRunResultResponse resultResponse = awaitStoppedResult(
                    client,
                    storage,
                    server.getPort(),
                    "run-late-stop"
            );

            assertTrue(lateSampleRecorded.await(1, TimeUnit.SECONDS));
            assertNotNull(resultResponse.getReport());
            assertEquals(resultResponse.getReport().getSummary().getTotalRequests(), 1L);
            assertEquals(resultResponse.getReport().getSummary().getFailedRequests(), 1L);
            assertEquals(resultResponse.getReport().getProtocols().get("WEBSOCKET").getTotal().getTotal(), 1L);
            assertEquals(resultResponse.getReport().getProtocols().get("WEBSOCKET").getTotal()
                    .getStream().getSentMessages(), 15L);
        }
    }

    @Test
    public void shouldPruneCompletedRunsAfterRetentionWindow() throws Exception {
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder()
                        .host("127.0.0.1")
                        .port(0)
                        .completedRunRetentionMs(100L)
                        .build(),
                (request, control) -> PerformanceJsonReport.builder()
                        .metadata(PerformanceJsonReportMetadata.builder()
                                .runId(request.getRunId())
                                .source("worker")
                                .status("SUCCESS")
                                .build())
                        .summary(PerformanceJsonReportSummary.builder()
                                .totalRequests(1L)
                                .successRequests(1L)
                                .build())
                        .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                        .build()
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            submitRun(client, storage, server.getPort(), "run-old");
            awaitStatus(client, storage, server.getPort(), "run-old", "SUCCESS");
            Thread.sleep(150);

            submitRun(client, storage, server.getPort(), "run-new");

            HttpResponse<String> oldResult = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort()
                                    + "/api/performance/v1/runs/run-old/result"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(oldResult.statusCode(), 404, oldResult.body());
        }
    }

    @Test
    public void shouldRejectSecondRunWhileActive() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> {
                    started.countDown();
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                    return PerformanceJsonReport.builder()
                            .metadata(PerformanceJsonReportMetadata.builder()
                                    .runId(request.getRunId())
                                    .source("worker")
                                    .status("SUCCESS")
                                    .build())
                            .summary(PerformanceJsonReportSummary.builder()
                                    .totalRequests(1L)
                                    .successRequests(1L)
                                    .build())
                            .protocols(PerformanceJsonReportSummaryMapper.emptyProtocols())
                            .build();
                }
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            submitRun(client, storage, server.getPort(), "run-active");
            assertTrue(started.await(1, TimeUnit.SECONDS));

            PerformanceWorkerRunRequest secondRun = PerformanceWorkerRunRequest.builder()
                    .runId("run-second")
                    .plan(emptyPlan())
                    .assignment(PerformanceWorkerAssignment.builder().runId("run-second").workerId("worker-a").build())
                    .build();
            HttpResponse<String> second = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/api/performance/v1/runs"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(storage.toJson(secondRun)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(second.statusCode(), 409, second.body());
            release.countDown();
            awaitStatus(client, storage, server.getPort(), "run-active", "SUCCESS");
        }
    }

    @Test
    public void shouldRejectRunWithoutWorkerAssignment() throws Exception {
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> PerformanceJsonReport.builder().build()
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            PerformanceWorkerRunRequest runRequest = PerformanceWorkerRunRequest.builder()
                    .runId("run-no-assignment")
                    .plan(emptyPlan())
                    .build();

            HttpResponse<String> rejected = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/api/performance/v1/runs"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(storage.toJson(runRequest)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(rejected.statusCode(), 400, rejected.body());
            assertTrue(rejected.body().contains("assignment"), rejected.body());
        }
    }

    @Test
    public void shouldUseAssignmentRunIdWhenRequestRunIdIsBlank() throws Exception {
        AtomicReference<PerformanceWorkerRunRequest> captured = new AtomicReference<>();
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> {
                    captured.set(request);
                    return PerformanceJsonReport.builder()
                            .metadata(PerformanceJsonReportMetadata.builder()
                                    .runId(request.getRunId())
                                    .status("SUCCESS")
                                    .build())
                            .build();
                }
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            PerformanceWorkerRunRequest runRequest = PerformanceWorkerRunRequest.builder()
                    .plan(emptyPlan())
                    .assignment(PerformanceWorkerAssignment.builder()
                            .runId("assignment-run")
                            .workerId("worker-a")
                            .build())
                    .build();

            HttpResponse<String> accepted = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/api/performance/v1/runs"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(storage.toJson(runRequest)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(accepted.statusCode(), 202, accepted.body());
            assertEquals(storage.acceptedResponseFromJson(accepted.body()).getRunId(), "assignment-run");
            awaitStatus(client, storage, server.getPort(), "assignment-run", "SUCCESS");
            assertEquals(captured.get().getRunId(), "assignment-run");
            assertEquals(captured.get().getAssignment().getRunId(), "assignment-run");
        }
    }

    @Test
    public void shouldNotOverwriteFailedRunStatusWhenStopArrivesLate() throws Exception {
        PerformanceWorkerProtocolJsonStorage storage = new PerformanceWorkerProtocolJsonStorage();
        try (PerformanceWorkerServer server = new PerformanceWorkerServer(
                PerformanceWorkerOptions.builder().host("127.0.0.1").port(0).build(),
                (request, control) -> {
                    throw new IllegalStateException("worker failed");
                }
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            submitRun(client, storage, server.getPort(), "run-failed");
            awaitStatus(client, storage, server.getPort(), "run-failed", "FAILED");

            HttpResponse<String> stop = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort()
                                    + "/api/performance/v1/runs/run-failed/stop"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            PerformanceWorkerRunStatusResponse stopResponse = storage.statusResponseFromJson(stop.body());

            assertEquals(stop.statusCode(), 200, stop.body());
            assertEquals(stopResponse.getStatus(), "FAILED");
            assertEquals(stopResponse.getError(), "worker failed");
        }
    }

    private static PerformanceWorkerRunResultResponse awaitStoppedResult(HttpClient client,
                                                                         PerformanceWorkerProtocolJsonStorage storage,
                                                                         int port) throws Exception {
        return awaitStoppedResult(client, storage, port, "run-stop");
    }

    private static PerformanceWorkerRunResultResponse awaitStoppedResult(HttpClient client,
                                                                         PerformanceWorkerProtocolJsonStorage storage,
                                                                         int port,
                                                                         String runId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        PerformanceWorkerRunResultResponse response;
        do {
            HttpResponse<String> result = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port
                                    + "/api/performance/v1/runs/" + runId + "/result"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            response = storage.resultResponseFromJson(result.body());
            if ("STOPPED".equals(response.getStatus())) {
                return response;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        return response;
    }

    private static void submitRun(HttpClient client,
                                  PerformanceWorkerProtocolJsonStorage storage,
                                  int port,
                                  String runId) throws Exception {
        PerformanceWorkerRunRequest runRequest = PerformanceWorkerRunRequest.builder()
                .runId(runId)
                .plan(emptyPlan())
                .assignment(PerformanceWorkerAssignment.builder().runId(runId).workerId("worker-a").build())
                .build();
        HttpResponse<String> accepted = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/api/performance/v1/runs"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(storage.toJson(runRequest)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(accepted.statusCode(), 202, accepted.body());
    }

    private static PerformanceWorkerRunStatusResponse awaitStatus(HttpClient client,
                                                                  PerformanceWorkerProtocolJsonStorage storage,
                                                                  int port,
                                                                  String runId,
                                                                  String expectedStatus) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        PerformanceWorkerRunStatusResponse response;
        do {
            HttpResponse<String> status = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/api/performance/v1/runs/" + runId))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(status.statusCode(), 200, status.body());
            response = storage.statusResponseFromJson(status.body());
            if (expectedStatus.equals(response.getStatus())) {
                return response;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        return response;
    }

    private static PerformanceRunPlan emptyPlan() {
        return PerformanceRunPlan.builder()
                .testPlan(new PerformanceCorePlanDocument(PerformanceCorePlanNode.builder()
                        .name("run plan")
                        .type(NodeType.ROOT)
                        .build()))
                .build();
    }
}
