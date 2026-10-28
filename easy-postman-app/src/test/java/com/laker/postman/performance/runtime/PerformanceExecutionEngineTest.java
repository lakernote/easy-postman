package com.laker.postman.performance.runtime;

import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.assertion.AssertionData;
import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceStatsCollector;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.plan.PerformanceTimerElement;
import com.laker.postman.performance.core.plan.PerformanceTestPlan;
import com.laker.postman.performance.core.plan.PerformanceThreadGroupPlan;
import com.laker.postman.performance.core.runtime.PerformanceRunListener;
import com.laker.postman.performance.core.runtime.PerformanceRunProgress;
import com.laker.postman.performance.core.runtime.PerformanceVirtualUserCoordinator;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.performance.core.timer.TimerData;
import com.laker.postman.performance.execution.PerformanceExecutionConfig;
import com.laker.postman.performance.model.PerformanceStatsCollectorListener;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.plan.PerformanceRequestSampler;
import com.laker.postman.performance.plan.PerformanceTestPlanCompiler;
import com.laker.postman.performance.plan.PerformanceTestPlanNode;
import com.laker.postman.performance.result.PerformanceResultCollector;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PerformanceExecutionEngineTest {

    @BeforeMethod
    public void isolateRuntimeSettings() {
        HttpRuntimeSettingsProvider.reset();
        OkHttpClientManager.clearClientCache();
    }

    @AfterMethod
    public void tearDownRuntimeSettings() {
        OkHttpClientManager.clearClientCache();
        HttpRuntimeSettingsProvider.reset();
    }

    @Test
    public void executionApisShouldNotExposeSwingTreeCompatibilityMethods() {
        assertFalse(hasSwingTreeNodeParameter(PerformanceExecutionEngine.class));
    }

    @Test
    public void executionEngineShouldNotExposeSwingResultTablePanelParameters() {
        assertFalse(hasParameterTypeName(
                PerformanceExecutionEngine.class,
                "com.laker.postman." + "panel.performance.result.PerformanceResultTablePanel"
        ));
    }

    @Test
    public void runtimeExecutionApisShouldNotExposeSwingComponentParameters() {
        assertFalse(hasParameterTypeName(PerformanceExecutionEngine.class, "java" + ".awt.Component"));
        assertFalse(hasParameterTypeName(PerformanceThreadGroupRunner.class, "java" + ".awt.Component"));
    }

    @Test
    public void executionEngineShouldNotExposeRawResultListenerCollections() {
        assertFalse(hasConstructorParameter(PerformanceExecutionEngine.class, List.class));
    }

    @Test(timeOut = 3000)
    public void joinThreadGroupThreadsShouldInterruptChildrenAndWaitWhenInterrupted() throws Exception {
        CountDownLatch childStarted = new CountDownLatch(1);
        AtomicBoolean childInterrupted = new AtomicBoolean(false);
        AtomicBoolean cancellationCalled = new AtomicBoolean(false);

        Thread child = new Thread(() -> {
            childStarted.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                childInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        child.start();
        assertTrue(childStarted.await(500, TimeUnit.MILLISECONDS));

        Thread joiner = new Thread(() ->
                PerformanceExecutionEngine.joinThreadGroupThreads(List.of(child), () -> cancellationCalled.set(true))
        );
        joiner.start();

        Thread.sleep(100);
        joiner.interrupt();
        joiner.join(1000);

        assertFalse(joiner.isAlive());
        assertFalse(child.isAlive());
        assertTrue(childInterrupted.get());
        assertTrue(cancellationCalled.get());
    }

    @Test
    public void totalThreadsShouldClampInvalidFixedThreadCountFromPersistedConfig() {
        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.threadMode = ThreadGroupData.ThreadMode.FIXED;
        threadGroupData.numThreads = 0;

        PerformanceTestPlanNode root = new PerformanceTestPlanNode(new PerformanceTreeNode("root", NodeType.ROOT));
        root.add(new PerformanceTestPlanNode(new PerformanceTreeNode("thread group", NodeType.THREAD_GROUP, threadGroupData)));

        PerformanceExecutionEngine engine = new PerformanceExecutionEngine(
                () -> true,
                () -> true,
                () -> 4,
                emptyResultCollector()
        );

        assertEquals(engine.getTotalThreads(compile(root)), 1);
    }

    @Test
    public void estimateTotalRequestsShouldUseSpikeTestDuration() {
        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.threadMode = ThreadGroupData.ThreadMode.SPIKE;
        threadGroupData.spikeMinThreads = 1;
        threadGroupData.spikeMaxThreads = 1;
        threadGroupData.spikeRampUpTime = 10;
        threadGroupData.spikeHoldTime = 5;
        threadGroupData.spikeRampDownTime = 10;
        threadGroupData.spikeDuration = 120;

        PerformanceTestPlanNode root = new PerformanceTestPlanNode(new PerformanceTreeNode("root", NodeType.ROOT));
        PerformanceTestPlanNode group = new PerformanceTestPlanNode(
                new PerformanceTreeNode("thread group", NodeType.THREAD_GROUP, threadGroupData)
        );
        group.add(new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST)));
        root.add(group);

        PerformanceExecutionEngine engine = new PerformanceExecutionEngine(
                () -> true,
                () -> true,
                () -> 4,
                emptyResultCollector()
        );

        assertEquals(engine.estimateTotalRequests(compile(root)), 400L);
    }

    @Test
    public void estimateTotalRequestsShouldUseLongMathForNestedLoopControllers() {
        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.threadMode = ThreadGroupData.ThreadMode.FIXED;
        threadGroupData.numThreads = 1;
        threadGroupData.useTime = false;
        threadGroupData.loops = 1;

        PerformanceTestPlanNode root = new PerformanceTestPlanNode(new PerformanceTreeNode("root", NodeType.ROOT));
        PerformanceTestPlanNode group = new PerformanceTestPlanNode(
                new PerformanceTreeNode("thread group", NodeType.THREAD_GROUP, threadGroupData)
        );
        PerformanceTreeNode outerLoopData = new PerformanceTreeNode("outer loop", NodeType.LOOP);
        outerLoopData.loopData = new LoopData();
        outerLoopData.loopData.iterations = LoopData.MAX_ITERATIONS;
        PerformanceTestPlanNode outerLoop = new PerformanceTestPlanNode(outerLoopData);
        PerformanceTreeNode innerLoopData = new PerformanceTreeNode("inner loop", NodeType.LOOP);
        innerLoopData.loopData = new LoopData();
        innerLoopData.loopData.iterations = LoopData.MAX_ITERATIONS;
        PerformanceTestPlanNode innerLoop = new PerformanceTestPlanNode(innerLoopData);
        innerLoop.add(new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST)));
        outerLoop.add(innerLoop);
        group.add(outerLoop);
        root.add(group);

        PerformanceExecutionEngine engine = new PerformanceExecutionEngine(
                () -> true,
                () -> true,
                () -> 4,
                emptyResultCollector()
        );

        assertEquals(engine.estimateTotalRequests(compile(root)), 10_000_000_000L);
    }

    @Test
    public void fixedThreadGroupShouldExecuteHttpRequestsInsideLoopController() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("ok-1"));
            server.enqueue(new MockResponse().setBody("ok-2"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("http-loop-request");
            item.setName("HTTP Loop Request");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/loop").toString());

            ThreadGroupData threadGroupData = new ThreadGroupData();
            threadGroupData.threadMode = ThreadGroupData.ThreadMode.FIXED;
            threadGroupData.numThreads = 1;
            threadGroupData.useTime = false;
            threadGroupData.loops = 1;

            PerformanceTestPlanNode group = new PerformanceTestPlanNode(
                    new PerformanceTreeNode("thread group", NodeType.THREAD_GROUP, threadGroupData)
            );
            PerformanceTreeNode loopData = new PerformanceTreeNode("Loop", NodeType.LOOP);
            loopData.loopData = new LoopData();
            loopData.loopData.iterations = 2;
            PerformanceTestPlanNode loopNode = new PerformanceTestPlanNode(loopData);
            loopNode.add(new PerformanceTestPlanNode(new PerformanceTreeNode(item.getName(), NodeType.REQUEST, item)));
            group.add(loopNode);

            PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
            PerformanceExecutionEngine engine = new PerformanceExecutionEngine(
                    () -> true,
                    () -> false,
                    () -> 4,
                    statsResultCollector(statsCollector)
            );

            engine.runTestPlan(compile(group), 1);

            assertEquals(server.getRequestCount(), 2);
            assertEquals(statsCollector.snapshot().totalRequests(), 2);
        }
    }

    @Test
    public void executionEngineShouldRunPurePlanWithFixedExecutionConfig() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("ok"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("pure-plan-http");
            item.setName("Pure Plan HTTP");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/pure").toString());

            ThreadGroupData threadGroupData = new ThreadGroupData();
            threadGroupData.threadMode = ThreadGroupData.ThreadMode.FIXED;
            threadGroupData.numThreads = 1;
            threadGroupData.useTime = false;
            threadGroupData.loops = 1;

            PerformanceTestPlan plan = new PerformanceTestPlan(List.of(
                    new PerformanceThreadGroupPlan(
                            "thread group",
                            threadGroupData,
                            List.of(new PerformanceRequestSampler(item.getName(), item, null, List.of()))
                    )
            ));
            PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
            PerformanceExecutionEngine engine = new PerformanceExecutionEngine(
                    () -> true,
                    PerformanceExecutionConfig.fixed(false, 1, false),
                    statsResultCollector(statsCollector)
            );

            engine.runTestPlan(plan, 1);

            assertEquals(server.getRequestCount(), 1);
            assertEquals(statsCollector.snapshot().totalRequests(), 1);
        }
    }

    @Test(timeOut = 5000)
    public void defaultExecutionEngineShouldIsolateCookiesPerVirtualUser() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            AtomicInteger loginSequence = new AtomicInteger();
            CountDownLatch allLoginsReachedServer = new CountDownLatch(2);
            List<String> checkCookies = new CopyOnWriteArrayList<>();
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    if (request.getPath().startsWith("/login")) {
                        int loginId = loginSequence.incrementAndGet();
                        allLoginsReachedServer.countDown();
                        try {
                            if (!allLoginsReachedServer.await(1, TimeUnit.SECONDS)) {
                                return new MockResponse().setResponseCode(500).setBody("login barrier timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return new MockResponse().setResponseCode(500).setBody("interrupted");
                        }
                        return new MockResponse()
                                .addHeader("Set-Cookie", "sid=user-" + loginId + "; Path=/")
                                .setBody("login");
                    }
                    if (request.getPath().startsWith("/check")) {
                        checkCookies.add(request.getHeader("Cookie"));
                        return new MockResponse().setBody("check");
                    }
                    return new MockResponse().setResponseCode(404);
                }
            });
            server.start();

            ThreadGroupData threadGroupData = new ThreadGroupData();
            threadGroupData.threadMode = ThreadGroupData.ThreadMode.FIXED;
            threadGroupData.numThreads = 2;
            threadGroupData.useTime = false;
            threadGroupData.loops = 1;

            PerformanceTestPlan plan = new PerformanceTestPlan(List.of(
                    new PerformanceThreadGroupPlan(
                            "cookie users",
                            threadGroupData,
                            List.of(
                                    timer("cookie barrier", 100),
                                    requestSampler("login", server.url("/login").toString()),
                                    requestSampler("check", server.url("/check").toString())
                            )
                    )
            ));

            new PerformanceExecutionEngine(
                    () -> true,
                    () -> false,
                    () -> 4,
                    emptyResultCollector()
            ).runTestPlan(plan, 2);

            assertEquals(checkCookies.size(), 2);
            assertEquals(new HashSet<>(checkCookies).size(), 2);
            assertTrue(checkCookies.stream().allMatch(cookie -> cookie != null && cookie.startsWith("sid=user-")));
        }
    }

    @Test
    public void compiledPlanExecutionShouldRunHttpAssertions() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("http-assertion-request");
            item.setName("HTTP Assertion Request");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/assertion").toString());

            PerformanceTestPlanNode request = new PerformanceTestPlanNode(
                    new PerformanceTreeNode(item.getName(), NodeType.REQUEST, item)
            );
            request.add(responseCodeAssertion("201"));
            PerformanceTestPlanNode group = fixedThreadGroup(1);
            group.add(request);

            PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
            new PerformanceExecutionEngine(
                    () -> true,
                    () -> false,
                    () -> 4,
                    statsResultCollector(statsCollector)
            ).runTestPlan(compile(group), 1);

            assertEquals(server.getRequestCount(), 1);
            assertEquals(statsCollector.snapshot().totalRequests(), 1);
            assertEquals(statsCollector.snapshot().successRequests(), 0);
        }
    }

    @Test
    public void compiledPlanExecutionShouldValidateWebSocketStagesBeforeNetwork() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("unexpected"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("compiled-ws-missing-connect");
            item.setName("Compiled WS Missing Connect");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            PerformanceTreeNode requestData = new PerformanceTreeNode(item.getName(), NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = new WebSocketPerformanceData();
            PerformanceTestPlanNode group = fixedThreadGroup(1);
            group.add(new PerformanceTestPlanNode(requestData));

            PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
            new PerformanceExecutionEngine(
                    () -> true,
                    () -> false,
                    () -> 4,
                    statsResultCollector(statsCollector)
            ).runTestPlan(compile(group), 1);

            assertEquals(server.getRequestCount(), 0);
            assertEquals(statsCollector.snapshot().totalRequests(), 1);
            assertEquals(statsCollector.snapshot().successRequests(), 0);
        }
    }

    @Test
    public void compiledPlanShouldNotInferSseScenarioFromRequestHeaders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("data: unexpected\n\n"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("compiled-sse-missing-stages");
            item.setName("Compiled SSE Missing Stages");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/events").toString());
            item.setHeadersList(List.of(new HttpHeader(true, "Accept", "text/event-stream")));

            PerformanceTreeNode requestData = new PerformanceTreeNode(item.getName(), NodeType.REQUEST, item);
            PerformanceTestPlanNode group = fixedThreadGroup(1);
            group.add(new PerformanceTestPlanNode(requestData));

            PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
            new PerformanceExecutionEngine(
                    () -> true,
                    () -> false,
                    () -> 4,
                    statsResultCollector(statsCollector)
            ).runTestPlan(compile(group), 1);

            assertEquals(server.getRequestCount(), 1);
            assertEquals(statsCollector.snapshot().totalRequests(), 1);
            assertEquals(statsCollector.snapshot().successRequests(), 1);
        }
    }

    @Test
    public void stairsStepCountShouldRoundUpWhenStepDoesNotDivideThreadRange() {
        assertEquals(PerformanceExecutionEngine.calculateStairsTotalSteps(1, 10, 4), 3);
    }

    @Test
    public void executionEngineShouldPublishProgressThroughRunListener() {
        List<PerformanceRunProgress> progressEvents = new CopyOnWriteArrayList<>();
        PerformanceExecutionEngine engine = new PerformanceExecutionEngine(
                () -> true,
                () -> true,
                () -> 4,
                emptyResultCollector(),
                new PerformanceRunListener() {
                    @Override
                    public void onProgress(PerformanceRunProgress progress) {
                        progressEvents.add(progress);
                    }
                }
        );

        engine.runTestPlan(compile(fixedThreadGroup(1)), 1);

        assertTrue(progressEvents.stream().anyMatch(progress ->
                progress.getActiveThreads() == 1 && progress.getTotalThreads() == 1));
        assertEquals(progressEvents.get(progressEvents.size() - 1).getActiveThreads(), 0);
    }

    @Test
    public void runListenerFailureShouldNotLeaveVirtualUsersActive() {
        PerformanceExecutionEngine engine = new PerformanceExecutionEngine(
                () -> true,
                () -> true,
                () -> 4,
                emptyResultCollector(),
                new PerformanceRunListener() {
                    @Override
                    public void onProgress(PerformanceRunProgress progress) {
                        throw new IllegalStateException("listener failed");
                    }
                }
        );

        engine.runTestPlan(compile(fixedThreadGroup(1)), 1);

        assertEquals(engine.getActiveThreads(), 0);
    }

    @Test(timeOut = 3000)
    public void adjustSpikeThreadCountShouldIgnoreStaleThreadEndEntries() throws Exception {
        PerformanceThreadGroupRunner runner = new PerformanceThreadGroupRunner(
                () -> true,
                () -> 0L,
                () -> {
                },
                new PerformanceVirtualUserCoordinator(),
                null,
                null,
                null
        );
        ThreadGroupData threadGroupData = new ThreadGroupData();
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        worker.start();

        ConcurrentHashMap<Thread, Long> staleThreadEndTimes = new ConcurrentHashMap<>() {
            @Override
            public Long get(Object key) {
                if (key == worker) {
                    return null;
                }
                return super.get(key);
            }
        };
        staleThreadEndTimes.put(worker, Long.MAX_VALUE);

        try {
            runner.adjustSpikeThreadCount(
                    null,
                    threadGroupData,
                    new AtomicInteger(1),
                    0,
                    1,
                    (BiConsumer<Integer, Integer>) (active, total) -> {
                    },
                    1,
                    staleThreadEndTimes
            );
        } finally {
            worker.interrupt();
            worker.join(1_000);
        }
    }

    private static PerformanceTestPlanNode fixedThreadGroup(int loops) {
        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.threadMode = ThreadGroupData.ThreadMode.FIXED;
        threadGroupData.numThreads = 1;
        threadGroupData.useTime = false;
        threadGroupData.loops = loops;
        return new PerformanceTestPlanNode(new PerformanceTreeNode("thread group", NodeType.THREAD_GROUP, threadGroupData));
    }

    private static PerformanceTestPlanNode responseCodeAssertion(String expectedStatus) {
        AssertionData assertionData = new AssertionData();
        assertionData.type = "Response Code";
        assertionData.operator = "=";
        assertionData.value = expectedStatus;
        return new PerformanceTestPlanNode(new PerformanceTreeNode("status assertion", NodeType.ASSERTION, assertionData));
    }

    private static PerformanceRequestSampler requestSampler(String name, String url) {
        HttpRequestItem item = new HttpRequestItem();
        item.setId(name);
        item.setName(name);
        item.setProtocol(RequestItemProtocolEnum.HTTP);
        item.setMethod("GET");
        item.setUrl(url);
        return new PerformanceRequestSampler(name, item, null, List.of());
    }

    private static PerformanceTimerElement timer(String name, int delayMs) {
        TimerData timerData = new TimerData();
        timerData.delayMs = delayMs;
        return new PerformanceTimerElement(name, timerData);
    }

    private static PerformanceTestPlan compile(PerformanceTestPlanNode root) {
        return PerformanceTestPlanCompiler.compile(root);
    }

    private static boolean hasSwingTreeNodeParameter(Class<?> type) {
        return hasParameterTypeName(type, String.join(".", "javax", "swing", "tree", "DefaultMutable" + "TreeNode"));
    }

    private static PerformanceResultCollector emptyResultCollector() {
        return new PerformanceResultCollector(List.of());
    }

    private static PerformanceResultCollector statsResultCollector(PerformanceStatsCollector statsCollector) {
        return new PerformanceResultCollector(List.of(new PerformanceStatsCollectorListener(statsCollector)));
    }

    private static boolean hasParameterTypeName(Class<?> type, String parameterTypeName) {
        boolean methodParameter = java.util.Arrays.stream(type.getMethods())
                .flatMap(method -> java.util.Arrays.stream(method.getParameterTypes()))
                .map(Class::getName)
                .anyMatch(parameterTypeName::equals);
        boolean constructorParameter = java.util.Arrays.stream(type.getConstructors())
                .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
                .map(Class::getName)
                .anyMatch(parameterTypeName::equals);
        return methodParameter || constructorParameter;
    }

    private static boolean hasConstructorParameter(Class<?> type, Class<?> parameterType) {
        return java.util.Arrays.stream(type.getConstructors())
                .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(parameterType::equals);
    }
}
