package com.laker.postman.panel.performance;

import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.plan.PerformanceCorePlanDocument;
import com.laker.postman.performance.core.plan.PerformanceCorePlanNode;
import com.laker.postman.performance.core.run.PerformanceRunPlan;
import com.laker.postman.performance.core.model.PerformanceTrendSnapshot;
import com.laker.postman.performance.core.worker.PerformanceWorkerEndpoint;
import com.laker.postman.performance.core.worker.PerformanceWorkerApiPaths;
import com.laker.postman.performance.core.worker.PerformanceWorkerProtocol;
import com.sun.net.httpserver.HttpServer;
import org.jfree.data.time.RegularTimePeriod;
import org.testng.annotations.Test;

import com.laker.postman.panel.performance.result.PerformanceTrendView;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class PerformanceRemoteRunControlSupportTest {

    @Test
    public void shouldReturnImmediatelyWhenNoRemoteRunExists() throws Exception {
        assertTrue(support(false, false).awaitStopOnExit(1, TimeUnit.SECONDS));
    }

    @Test
    public void shouldPreventSubmissionWhenStoppedBeforeRunThreadBegins() throws Exception {
        try (RemoteWorkerServer worker = RemoteWorkerServer.start(false, false)) {
            PerformanceRemoteRunControlSupport support = support(false, false);
            registerRun(support, "run-before-submit");

            support.stopRun();
            invokeSubmit(support, List.of(worker.endpoint()));

            assertEquals(worker.submitCount.get(), 0);
            assertEquals(worker.stopCount.get(), 0);
            assertTrue(support.awaitStopOnExit(1, TimeUnit.SECONDS));
        }
    }

    @Test
    public void shouldWaitForSubmissionBeforeStoppingAndSkipRemainingWorkers() throws Exception {
        try (RemoteWorkerServer first = RemoteWorkerServer.start(true, false);
             RemoteWorkerServer second = RemoteWorkerServer.start(false, false)) {
            PerformanceRemoteRunControlSupport support = support(false, false);
            registerRun(support, "run-during-submit");
            ExecutorService submitter = Executors.newSingleThreadExecutor();
            try {
                Future<?> submission = submitter.submit(() -> {
                    invokeSubmit(support, List.of(first.endpoint(), second.endpoint()));
                    return null;
                });
                assertTrue(first.submitReceived.await(2, TimeUnit.SECONDS));

                support.stopRun();
                support.stopRun();
                assertFalse(support.awaitStopOnExit(100, TimeUnit.MILLISECONDS));
                assertEquals(first.stopCount.get(), 0, "stop must not race ahead of an unfinished submit");

                first.releaseSubmit.countDown();
                submission.get(2, TimeUnit.SECONDS);
                assertTrue(support.awaitStopOnExit(2, TimeUnit.SECONDS));
                assertEquals(first.stopCount.get(), 1, "Repeated stop requests should share one completion");
                assertEquals(second.submitCount.get(), 0, "Exit must prevent dispatch to subsequent workers");
                assertEquals(second.stopCount.get(), 0);
            } finally {
                first.releaseSubmit.countDown();
                submitter.shutdownNow();
            }
        }
    }

    @Test
    public void shouldStopWorkersInParallelAndKeepWaitingAfterRunThreadFinishes() throws Exception {
        try (RemoteWorkerServer first = RemoteWorkerServer.start(false, true);
             RemoteWorkerServer second = RemoteWorkerServer.start(false, true)) {
            PerformanceRemoteRunControlSupport support = support(false, false);
            Object run = registerRun(support, "run-parallel-stop");
            invokeSubmit(support, List.of(first.endpoint(), second.endpoint()));

            support.stopRun();
            assertTrue(first.stopReceived.await(2, TimeUnit.SECONDS));
            assertTrue(second.stopReceived.await(2, TimeUnit.SECONDS), "One slow worker must not delay stopping others");
            invokeRunState(run, "markFinished");
            support.stopRun();
            support.stopRun();
            assertFalse(support.awaitStopOnExit(100, TimeUnit.MILLISECONDS));
            assertEquals(first.stopCount.get(), 1, "Repeated stop must reuse the in-flight worker request");
            assertEquals(second.stopCount.get(), 1);

            first.releaseStop.countDown();
            second.releaseStop.countDown();
            assertTrue(support.awaitStopOnExit(2, TimeUnit.SECONDS));
            assertEquals(first.stopCount.get(), 1);
            assertEquals(second.stopCount.get(), 1);
        }
    }

    @Test
    public void shouldWaitForStopsFromPreviousRunWhenAnotherRunHasStarted() throws Exception {
        try (RemoteWorkerServer worker = RemoteWorkerServer.start(false, true)) {
            PerformanceRemoteRunControlSupport support = support(false, false);
            registerRun(support, "run-old-stop");
            invokeSubmit(support, List.of(worker.endpoint()));
            support.stopRun();
            assertTrue(worker.stopReceived.await(2, TimeUnit.SECONDS));
            registerRun(support, "run-new");

            assertFalse(support.awaitStopOnExit(100, TimeUnit.MILLISECONDS));
            worker.releaseStop.countDown();
            assertTrue(support.awaitStopOnExit(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void shouldRetryFailedWorkerWithoutResendingSuccessfulStops() throws Exception {
        try (RemoteWorkerServer failedWorker = RemoteWorkerServer.start(false, false, 1);
             RemoteWorkerServer successfulWorker = RemoteWorkerServer.start(false, false)) {
            PerformanceRemoteRunControlSupport support = support(false, false);
            Object run = registerRun(support, "run-stop-retry");
            invokeSubmit(support, List.of(failedWorker.endpoint(), successfulWorker.endpoint()));

            assertFirstStopFails(run);
            support.stopRun();

            assertTrue(support.awaitStopOnExit(2, TimeUnit.SECONDS));
            assertEquals(failedWorker.stopCount.get(), 2, "A later Stop must retry the failed worker");
            assertEquals(successfulWorker.stopCount.get(), 1, "A successful worker must not receive another Stop");
            support.stopRun();
            assertTrue(support.awaitStopOnExit(2, TimeUnit.SECONDS));
            assertEquals(failedWorker.stopCount.get(), 2, "Successful retries should also be cached");
        }
    }

    @Test
    public void shouldRetryFailedStopOnExitAfterRunThreadFinishes() throws Exception {
        try (RemoteWorkerServer worker = RemoteWorkerServer.start(false, false, 1)) {
            PerformanceRemoteRunControlSupport support = support(false, false);
            Object run = registerRun(support, "run-finished-stop-retry");
            invokeSubmit(support, List.of(worker.endpoint()));

            assertFirstStopFails(run);
            invokeRunState(run, "markFinished");

            assertTrue(support.awaitStopOnExit(2, TimeUnit.SECONDS), "Exit must retry an earlier failed Stop");
            assertEquals(worker.stopCount.get(), 2);
        }
    }

    @Test
    public void shouldRetryFailedStopFromPreviousRunWhenAnotherRunHasStarted() throws Exception {
        try (RemoteWorkerServer worker = RemoteWorkerServer.start(false, false, 1)) {
            PerformanceRemoteRunControlSupport support = support(false, false);
            Object oldRun = registerRun(support, "run-old-failed-stop");
            invokeSubmit(support, List.of(worker.endpoint()));
            assertFirstStopFails(oldRun);
            invokeRunState(oldRun, "markFinished");
            registerRun(support, "run-after-failure");

            assertTrue(support.awaitStopOnExit(2, TimeUnit.SECONDS), "Exit must retry the previous failed run");
            assertEquals(worker.stopCount.get(), 2, "A completed failure must survive replacement of currentRun");
            assertTrue(support.awaitStopOnExit(2, TimeUnit.SECONDS));
            assertEquals(worker.stopCount.get(), 2, "A successful retry must be retained as successful");
        }
    }

    @Test
    public void shouldNotReportSuccessWhilePreviousRunStopStillFails() throws Exception {
        try (RemoteWorkerServer worker = RemoteWorkerServer.start(false, false, 2)) {
            PerformanceRemoteRunControlSupport support = support(false, false);
            Object oldRun = registerRun(support, "run-old-repeated-failure");
            invokeSubmit(support, List.of(worker.endpoint()));
            assertFirstStopFails(oldRun);
            invokeRunState(oldRun, "markFinished");
            registerRun(support, "run-after-repeated-failure");

            assertFalse(support.awaitStopOnExit(2, TimeUnit.SECONDS), "An old run failing again must prevent success");
            assertEquals(worker.stopCount.get(), 2);
            assertTrue(support.awaitStopOnExit(2, TimeUnit.SECONDS), "A later exit attempt must still retry the old run");
            assertEquals(worker.stopCount.get(), 3);
        }
    }

    private static void assertFirstStopFails(Object run) throws Exception {
        CompletableFuture<?> stop = (CompletableFuture<?>) invokeRunState(run, "requestStop");
        try {
            stop.get(2, TimeUnit.SECONDS);
            fail("Expected the first worker Stop to fail");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause().getMessage().contains("status 500"));
        }
    }

    private static Object registerRun(PerformanceRemoteRunControlSupport support, String runId) throws Exception {
        Class<?> stateClass = Class.forName(PerformanceRemoteRunControlSupport.class.getName() + "$RemoteRunState");
        Constructor<?> constructor = stateClass.getDeclaredConstructor(PerformanceRemoteRunControlSupport.class, String.class);
        constructor.setAccessible(true);
        Object state = constructor.newInstance(support, runId);
        Field currentRunField = PerformanceRemoteRunControlSupport.class.getDeclaredField("currentRun");
        currentRunField.setAccessible(true);
        currentRunField.set(support, state);
        return state;
    }

    private static void invokeSubmit(PerformanceRemoteRunControlSupport support,
                                     List<PerformanceWorkerEndpoint> workers) throws Exception {
        Field currentRunField = PerformanceRemoteRunControlSupport.class.getDeclaredField("currentRun");
        currentRunField.setAccessible(true);
        Object run = currentRunField.get(support);
        Method submitRun = PerformanceRemoteRunControlSupport.class.getDeclaredMethod(
                "submitRun", PerformanceRunPlan.class, List.class, run.getClass());
        submitRun.setAccessible(true);
        try {
            submitRun.invoke(support, emptyPlan(), workers, run);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static Object invokeRunState(Object state, String methodName) throws Exception {
        Method method = state.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(state);
    }

    @Test
    public void shouldRequestWorkerTrendSnapshotWithoutFullReportWhenOnlyTrendIsEnabled() {
        PerformanceRemoteRunControlSupport support = support(true, false);

        assertFalse(support.shouldIncludeStatusReport());
        assertTrue(support.shouldIncludeStatusTrend());
    }

    @Test
    public void shouldRequestWorkerReportWhenRealtimeReportIsEnabled() {
        PerformanceRemoteRunControlSupport support = support(false, true);

        assertTrue(support.shouldIncludeStatusReport());
        assertFalse(support.shouldIncludeStatusTrend());
    }

    @Test
    public void shouldSkipWorkerReportWhenTrendAndRealtimeReportAreDisabled() {
        PerformanceRemoteRunControlSupport support = support(false, false);

        assertFalse(support.shouldIncludeStatusReport());
        assertFalse(support.shouldIncludeStatusTrend());
    }

    @Test
    public void shouldRequestRemoteTrendSnapshotOnlyAtConfiguredSamplingInterval() {
        PerformanceRemoteRunControlSupport support = support(true, false, 2_000L, new NoopTrendView());

        assertFalse(support.shouldRequestStatusTrendSnapshot(10_000L, false));
        assertFalse(support.shouldRequestStatusTrendSnapshot(11_000L, false));
        assertTrue(support.shouldRequestStatusTrendSnapshot(12_000L, false));
        assertFalse(support.shouldRequestStatusTrendSnapshot(13_000L, false));
        assertTrue(support.shouldRequestStatusTrendSnapshot(14_000L, false));
    }

    @Test
    public void shouldRejectLegacyRemoteWorkerWithoutProtocolVersionBeforeSubmit() throws Exception {
        try (TestServer server = TestServer.start("""
                {"status":"UP","workerId":"legacy-worker","host":"127.0.0.1","port":19090}
                """)) {
            PerformanceRemoteRunControlSupport support = support(false, false);
            registerRun(support, "run-legacy");

            try {
                invokeSubmit(support, List.of(server.endpoint()));
                fail("Expected legacy worker protocol rejection");
            } catch (IllegalStateException ex) {
                assertTrue(ex.getMessage().contains("127.0.0.1"), ex.getMessage());
                assertTrue(ex.getMessage().contains("protocol"), ex.getMessage());
            }

            assertEquals(server.requestCount().get(), 1, "旧 worker 应在 health 阶段被拦截，不能继续 POST /runs");
        }
    }

    private static PerformanceRemoteRunControlSupport support(boolean trendEnabled,
                                                             boolean reportRealtimeEnabled) {
        return support(trendEnabled, reportRealtimeEnabled, 1_000L, null);
    }

    private static PerformanceRemoteRunControlSupport support(boolean trendEnabled,
                                                             boolean reportRealtimeEnabled,
                                                             long samplingIntervalMs,
                                                             PerformanceTrendView trendView) {
        return new PerformanceRemoteRunControlSupport(
                () -> false,
                ignored -> {
                },
                null,
                null,
                null,
                trendView,
                () -> {
                },
                () -> {
                },
                () -> trendEnabled,
                () -> reportRealtimeEnabled,
                () -> samplingIntervalMs
        );
    }

    private static PerformanceRunPlan emptyPlan() {
        return PerformanceRunPlan.builder()
                .testPlan(new PerformanceCorePlanDocument(PerformanceCorePlanNode.builder()
                        .name("run plan")
                        .type(NodeType.ROOT)
                        .build()))
                .build();
    }

    private record TestServer(HttpServer server,
                              java.util.concurrent.atomic.AtomicInteger requestCount) implements AutoCloseable {

        static TestServer start(String body) throws Exception {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            java.util.concurrent.atomic.AtomicInteger requestCount = new java.util.concurrent.atomic.AtomicInteger();
            server.createContext("/", exchange -> {
                requestCount.incrementAndGet();
                byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBody.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody);
                }
            });
            server.start();
            return new TestServer(server, requestCount);
        }

        PerformanceWorkerEndpoint endpoint() {
            return new PerformanceWorkerEndpoint("127.0.0.1", server.getAddress().getPort());
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class RemoteWorkerServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final CountDownLatch submitReceived = new CountDownLatch(1);
        private final CountDownLatch stopReceived = new CountDownLatch(1);
        private final CountDownLatch releaseSubmit;
        private final CountDownLatch releaseStop;
        private final AtomicInteger submitCount = new AtomicInteger();
        private final AtomicInteger stopCount = new AtomicInteger();

        private RemoteWorkerServer(boolean blockSubmit, boolean blockStop, int failedStops) throws Exception {
            releaseSubmit = new CountDownLatch(blockSubmit ? 1 : 0);
            releaseStop = new CountDownLatch(blockStop ? 1 : 0);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body = "{}";
                int responseStatus = 200;
                try {
                    if (path.equals(PerformanceWorkerApiPaths.HEALTH)) {
                        body = "{\"status\":\"UP\",\"workerProtocolVersion\":\""
                                + PerformanceWorkerProtocol.CURRENT_VERSION + "\"}";
                    } else if (path.equals(PerformanceWorkerApiPaths.RUNS)) {
                        submitCount.incrementAndGet();
                        submitReceived.countDown();
                        releaseSubmit.await(5, TimeUnit.SECONDS);
                    } else if (path.endsWith(PerformanceWorkerApiPaths.STOP_SUFFIX)) {
                        int stopNumber = stopCount.incrementAndGet();
                        responseStatus = stopNumber <= failedStops ? 500 : 200;
                        stopReceived.countDown();
                        releaseStop.await(5, TimeUnit.SECONDS);
                    }
                    byte[] response = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(responseStatus, response.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(response);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                }
            });
            server.start();
        }

        static RemoteWorkerServer start(boolean blockSubmit, boolean blockStop) throws Exception {
            return start(blockSubmit, blockStop, 0);
        }

        static RemoteWorkerServer start(boolean blockSubmit, boolean blockStop, int failedStops) throws Exception {
            return new RemoteWorkerServer(blockSubmit, blockStop, failedStops);
        }

        PerformanceWorkerEndpoint endpoint() {
            return new PerformanceWorkerEndpoint("127.0.0.1", server.getAddress().getPort());
        }

        @Override
        public void close() {
            releaseSubmit.countDown();
            releaseStop.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class NoopTrendView implements PerformanceTrendView {
        @Override
        public void clearTrendDataset() {
        }

        @Override
        public void addOrUpdate(RegularTimePeriod period, PerformanceTrendSnapshot snapshot) {
        }
    }
}
