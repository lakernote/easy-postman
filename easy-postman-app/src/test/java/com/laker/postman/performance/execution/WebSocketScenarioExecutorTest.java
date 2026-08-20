package com.laker.postman.performance.execution;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.model.Variable;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.assertion.AssertionData;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.core.controller.ConditionData;
import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.controller.WhileData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceRealtimeMetrics;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.plan.PerformanceController;
import com.laker.postman.performance.core.plan.PerformanceConditionController;
import com.laker.postman.performance.core.plan.PerformanceLoopController;
import com.laker.postman.performance.core.plan.PerformancePlanElement;
import com.laker.postman.performance.core.plan.PerformanceProtocolStageElement;
import com.laker.postman.performance.plan.PerformanceRequestSampler;
import com.laker.postman.performance.plan.PerformanceTestPlanCompiler;
import com.laker.postman.performance.plan.PerformanceTestPlanNode;
import com.laker.postman.performance.core.timer.TimerData;
import com.laker.postman.service.js.ScriptExecutionPipeline;
import com.laker.postman.service.variable.ExecutionVariableContext;
import com.laker.postman.service.variable.IterationDataVariableService;
import com.laker.postman.service.variable.VariablesService;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class WebSocketScenarioExecutorTest {

    @Test
    public void performanceWebSocketExecutionShouldDisableGuiLifecycleLogging() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/laker/postman/performance/execution/WebSocketScenarioExecutor.java"
        ));

        assertTrue(source.contains("openWebSocket("));
    }

    @Test
    public void webSocketRuntimeErrorMessagesShouldUseMessageKeys() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/laker/postman/performance/execution/WebSocketScenarioExecutor.java"
        ));

        assertFalse(source.contains("\"WebSocket connection timeout\""));
        assertFalse(source.contains("\"WebSocket read timeout\""));
        assertFalse(source.contains("\"WebSocket target message count timeout\""));
    }

    @Test
    public void webSocketReadShouldNotPollEveryHundredMillisecondsWhileIdle() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/laker/postman/performance/execution/WebSocketScenarioExecutor.java"
        ));

        assertTrue(source.contains("READ_IDLE_CHECK_INTERVAL_MS"));
        assertFalse(source.contains("Math.min(100, Math.max(1, deadline - now))"));
    }

    private static final long SESSION_END_DELAY_MS = 220;

    @Test
    public void shouldBuildResponseBodyFromAllReceivedMessages() {
        String body = WebSocketScenarioResponseBuilder.buildResponseBody(Arrays.asList("first", "second"));

        assertEquals(body, "first\n\nsecond\n\n");
    }

    @Test
    public void shouldReturnEmptyBodyWhenNoMessagesReceived() {
        String body = WebSocketScenarioResponseBuilder.buildResponseBody(Collections.emptyList());

        assertEquals(body, "");
    }

    @Test
    public void shouldIgnoreMessageFilterForFixedDurationMode() throws Exception {
        WebSocketPerformanceData cfg = new WebSocketPerformanceData();
        cfg.completionMode = WebSocketPerformanceData.CompletionMode.FIXED_DURATION;
        cfg.messageFilter = "done";
        WebSocketScenarioExecutor executor = new WebSocketScenarioExecutor(
                () -> true,
                throwable -> false,
                ConcurrentHashMap.newKeySet(),
                new PerformanceRealtimeMetrics()
        );

        Method matchesMessage = WebSocketScenarioExecutor.class.getDeclaredMethod("matchesMessage", WebSocketPerformanceData.class, String.class);
        matchesMessage.setAccessible(true);

        assertTrue((Boolean) matchesMessage.invoke(executor, cfg, "anything"));
    }

    @Test
    public void shouldRetainReadMessagePreviewByUtf8Bytes() {
        assertEquals(WebSocketReceivedMessageBuffer.retainUtf8Prefix("你好abc", 4), "你");
        assertEquals(WebSocketReceivedMessageBuffer.retainUtf8Prefix("🙂abc", 4), "🙂");
    }

    @Test
    public void receivedMessageBufferShouldEvictOldMessagesWhenRetainedBytesExceedLimit() {
        WebSocketReceivedMessageBuffer buffer = new WebSocketReceivedMessageBuffer(5);

        buffer.add("abc", 100);
        buffer.add("def", 200);

        WebSocketReceivedMessageBuffer.Message message = buffer.removeFirst();
        assertEquals(message.payload(), "def");
        assertEquals(message.receivedAtMs(), 200);
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void receivedMessageBufferShouldCapMessagesWhenPayloadIsNotNeeded() {
        WebSocketReceivedMessageBuffer buffer = new WebSocketReceivedMessageBuffer(1024, false, 1);

        buffer.add("first", 100);
        buffer.add("second", 200);

        WebSocketReceivedMessageBuffer.Message message = buffer.removeFirst();
        assertEquals(message.payload(), "");
        assertEquals(message.receivedAtMs(), 200);
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void shouldAddWebSocketSummaryHeaders() {
        HttpResponse response = new HttpResponse();
        WebSocketPerformanceData cfg = new WebSocketPerformanceData();
        cfg.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_REPEAT;
        cfg.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
        cfg.sendCount = 3;
        cfg.sendIntervalMs = 20;
        cfg.completionMode = WebSocketPerformanceData.CompletionMode.UNTIL_MATCH;
        cfg.messageFilter = "ack";

        WebSocketScenarioResponseBuilder.addSummaryHeaders(
                response,
                cfg,
                5,
                3,
                2,
                42,
                "last",
                "boom"
        );

        assertEquals(response.headers.get("X-Easy-WS-Send-Mode").get(0), "REQUEST_BODY_REPEAT");
        assertEquals(response.headers.get("X-Easy-WS-Send-Content-Source").get(0), "CUSTOM_TEXT");
        assertEquals(response.headers.get("X-Easy-WS-Send-Count-Configured").get(0), "3");
        assertFalse(response.headers.containsKey("X-Easy-WS-Read-Type"));
        assertEquals(response.headers.get("X-Easy-WS-Received-Count").get(0), "5");
        assertEquals(response.headers.get("X-Easy-WS-Sent-Count").get(0), "3");
        assertEquals(response.headers.get("X-Easy-WS-Message-Count").get(0), "2");
        assertEquals(response.headers.get("X-Easy-WS-First-Message-Latency-Ms").get(0), "42");
        assertEquals(response.headers.get("X-Easy-WS-Last-Message").get(0), "last");
        assertEquals(response.headers.get("X-Easy-WS-Error").get(0), "boom");
    }

    @Test
    public void shouldFailSingleReadWhenNoMessageArrivesBeforeTimeout() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-read-timeout-test");
            item.setName("WS Read Timeout Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(
                    new PerformanceTreeNode("request", NodeType.REQUEST, item)
            );
            addConnectStep(requestNode, new WebSocketPerformanceData());
            PerformanceTreeNode readStep = new PerformanceTreeNode("read", NodeType.WS_READ);
            readStep.webSocketPerformanceData = new WebSocketPerformanceData();
            readStep.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.SINGLE_MESSAGE;
            readStep.webSocketPerformanceData.firstMessageTimeoutMs = 100;
            requestNode.add(new PerformanceTestPlanNode(readStep));

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            );

            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertTrue(result.executionFailed);
            assertEquals(result.errorMsg,
                    I18nUtil.getMessage(MessageKeys.PERFORMANCE_MSG_WS_READ_TIMEOUT));
        }
    }

    @Test
    public void stepSupportShouldDetectReadStepInsideLoop() {
        PerformanceProtocolStageElement readStep = new PerformanceProtocolStageElement(
                "read",
                NodeType.WS_READ,
                null,
                new WebSocketPerformanceData(),
                List.of()
        );
        PerformanceLoopController loop = new PerformanceLoopController("loop", new LoopData(), List.of(readStep));
        PerformanceRequestSampler sampler = new PerformanceRequestSampler(
                "request",
                null,
                null,
                List.of(loop)
        );

        assertTrue(WebSocketScenarioStepSupport.hasEnabledReadStep(sampler));
    }

    @Test
    public void stepSupportShouldDetectReadStepInsideControllerContract() {
        PerformanceProtocolStageElement readStep = new PerformanceProtocolStageElement(
                "read",
                NodeType.WS_READ,
                null,
                new WebSocketPerformanceData(),
                List.of()
        );
        PerformanceController controller = new TestController("controller", 1, List.of(readStep));
        PerformanceRequestSampler sampler = new PerformanceRequestSampler(
                "request",
                null,
                null,
                List.of(controller)
        );

        assertTrue(WebSocketScenarioStepSupport.hasEnabledReadStep(sampler));
    }

    @Test
    public void stepSupportShouldDetectReadStepInsideCondition() {
        PerformanceProtocolStageElement readStep = new PerformanceProtocolStageElement(
                "read",
                NodeType.WS_READ,
                null,
                new WebSocketPerformanceData(),
                List.of()
        );
        ConditionData conditionData = new ConditionData();
        conditionData.expression = "true";
        PerformanceConditionController condition = new PerformanceConditionController(
                "condition",
                conditionData,
                List.of(readStep)
        );
        PerformanceRequestSampler sampler = new PerformanceRequestSampler(
                "request",
                null,
                null,
                List.of(condition)
        );

        assertTrue(WebSocketScenarioStepSupport.hasEnabledReadStep(sampler));
    }

    @Test
    public void stepSupportShouldUseStageConfigBeforeRequestDefault() {
        WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
        requestCfg.sendCount = 1;
        WebSocketPerformanceData stageCfg = new WebSocketPerformanceData();
        stageCfg.sendCount = 7;
        PerformanceProtocolStageElement sendStep = new PerformanceProtocolStageElement(
                "send",
                NodeType.WS_SEND,
                null,
                stageCfg,
                List.of()
        );

        WebSocketPerformanceData resolved = WebSocketScenarioStepSupport.webSocketData(sendStep, requestCfg);

        assertEquals(resolved.sendCount, 7);
    }

    @Test
    public void stepSupportShouldSkipBlankRequestBodyButAllowBlankCustomTextSend() {
        PreparedRequest request = new PreparedRequest();
        request.body = "";
        WebSocketPerformanceData requestBodyCfg = new WebSocketPerformanceData();
        requestBodyCfg.sendContentSource = WebSocketPerformanceData.SendContentSource.REQUEST_BODY;
        WebSocketPerformanceData customTextCfg = new WebSocketPerformanceData();
        customTextCfg.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
        customTextCfg.customSendBody = "";

        assertFalse(WebSocketScenarioStepSupport.hasSendPayload(request, null, requestBodyCfg));
        assertTrue(WebSocketScenarioStepSupport.hasSendPayload(request, null, customTextCfg));
    }

    @Test
    public void stepSupportShouldSkipBlankSendPreScriptWithoutPreparingPipelineBindings() {
        ScriptExecutionPipeline pipeline = ScriptExecutionPipeline.builder()
                .request(new PreparedRequest())
                .build();

        assertTrue(WebSocketScenarioStepSupport.executeSendPreScript(
                new DefaultPerformanceScriptRuntime(pipeline),
                new WebSocketPerformanceData(),
                0,
                1,
                "send"
        ).isSuccess());
        assertNull(pipeline.getBindings());
    }

    @Test
    public void shouldWalkWebSocketScenarioLoopStepsWithoutPreExpansion() {
        PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST));
        PerformanceTreeNode loopData = new PerformanceTreeNode("loop", NodeType.LOOP);
        loopData.loopData = new LoopData();
        loopData.loopData.iterations = 3;
        PerformanceTestPlanNode loopNode = new PerformanceTestPlanNode(loopData);
        PerformanceTestPlanNode sendNode = new PerformanceTestPlanNode(new PerformanceTreeNode("send", NodeType.WS_SEND));
        loopNode.add(sendNode);
        requestNode.add(loopNode);

        PerformanceRequestSampler requestSampler = PerformanceTestPlanCompiler.compileRequestSampler(requestNode);
        WebSocketScenarioPlanStepCursor cursor = new WebSocketScenarioPlanStepCursor(requestSampler, () -> true);

        assertNextStep(cursor, NodeType.WS_SEND, "send");
        assertNextStep(cursor, NodeType.WS_SEND, "send");
        assertNextStep(cursor, NodeType.WS_SEND, "send");
        assertEquals(cursor.next(), null);
    }

    @Test
    public void shouldWalkWebSocketScenarioSimpleControllerSteps() {
        PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST));
        PerformanceTestPlanNode simpleNode = new PerformanceTestPlanNode(
                new PerformanceTreeNode("simple", NodeType.SIMPLE)
        );
        simpleNode.add(new PerformanceTestPlanNode(new PerformanceTreeNode("send", NodeType.WS_SEND)));
        simpleNode.add(new PerformanceTestPlanNode(new PerformanceTreeNode("read", NodeType.WS_READ)));
        requestNode.add(simpleNode);

        PerformanceRequestSampler requestSampler = PerformanceTestPlanCompiler.compileRequestSampler(requestNode);
        WebSocketScenarioPlanStepCursor cursor = new WebSocketScenarioPlanStepCursor(requestSampler, () -> true);

        assertNextStep(cursor, NodeType.WS_SEND, "send");
        assertNextStep(cursor, NodeType.WS_READ, "read");
        assertEquals(cursor.next(), null);
    }

    @Test
    public void shouldWalkWebSocketScenarioControllerContractSteps() {
        PerformanceProtocolStageElement sendStep = new PerformanceProtocolStageElement(
                "send",
                NodeType.WS_SEND,
                null,
                new WebSocketPerformanceData(),
                List.of()
        );
        PerformanceController controller = new TestController("controller", 2, List.of(sendStep));
        PerformanceRequestSampler requestSampler = new PerformanceRequestSampler(
                "request",
                null,
                null,
                List.of(controller)
        );
        WebSocketScenarioPlanStepCursor cursor = new WebSocketScenarioPlanStepCursor(requestSampler, () -> true);

        assertNextStep(cursor, NodeType.WS_SEND, "send");
        assertNextStep(cursor, NodeType.WS_SEND, "send");
        assertEquals(cursor.next(), null);
    }

    @Test
    public void shouldWalkWebSocketScenarioConditionSteps() {
        PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST));
        PerformanceTreeNode skippedConditionData = new PerformanceTreeNode("skip condition", NodeType.CONDITION);
        skippedConditionData.conditionData = new ConditionData();
        skippedConditionData.conditionData.expression = "false";
        PerformanceTestPlanNode skippedCondition = new PerformanceTestPlanNode(skippedConditionData);
        skippedCondition.add(new PerformanceTestPlanNode(new PerformanceTreeNode("skipped send", NodeType.WS_SEND)));

        PerformanceTreeNode conditionData = new PerformanceTreeNode("run condition", NodeType.CONDITION);
        conditionData.conditionData = new ConditionData();
        conditionData.conditionData.expression = "true";
        PerformanceTestPlanNode condition = new PerformanceTestPlanNode(conditionData);
        condition.add(new PerformanceTestPlanNode(new PerformanceTreeNode("read", NodeType.WS_READ)));
        requestNode.add(skippedCondition);
        requestNode.add(condition);

        PerformanceRequestSampler requestSampler = PerformanceTestPlanCompiler.compileRequestSampler(requestNode);
        WebSocketScenarioPlanStepCursor cursor = new WebSocketScenarioPlanStepCursor(requestSampler, () -> true);

        assertNextStep(cursor, NodeType.WS_READ, "read");
        assertEquals(cursor.next(), null);
    }

    @Test
    public void shouldWalkWebSocketScenarioWhileSteps() {
        PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST));
        PerformanceTreeNode whileData = new PerformanceTreeNode("while", NodeType.WHILE);
        whileData.whileData = new WhileData();
        whileData.whileData.expression = "true";
        whileData.whileData.intervalMs = 0;
        whileData.whileData.maxIterations = 2;
        PerformanceTestPlanNode whileNode = new PerformanceTestPlanNode(whileData);
        whileNode.add(new PerformanceTestPlanNode(new PerformanceTreeNode("send", NodeType.WS_SEND)));
        requestNode.add(whileNode);

        PerformanceRequestSampler requestSampler = PerformanceTestPlanCompiler.compileRequestSampler(requestNode);
        WebSocketScenarioPlanStepCursor cursor = new WebSocketScenarioPlanStepCursor(requestSampler, () -> true);

        assertNextStep(cursor, NodeType.WS_SEND, "send");
        assertNextStep(cursor, NodeType.WS_SEND, "send");
        assertEquals(cursor.next(), null);
    }

    @Test
    public void shouldStopWebSocketScenarioCursorWithoutCompletingLargeLoop() {
        PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST));
        PerformanceTreeNode loopData = new PerformanceTreeNode("loop", NodeType.LOOP);
        loopData.loopData = new LoopData();
        loopData.loopData.iterations = LoopData.MAX_ITERATIONS;
        PerformanceTestPlanNode loopNode = new PerformanceTestPlanNode(loopData);
        PerformanceTestPlanNode sendNode = new PerformanceTestPlanNode(new PerformanceTreeNode("send", NodeType.WS_SEND));
        loopNode.add(sendNode);
        requestNode.add(loopNode);

        PerformanceRequestSampler requestSampler = PerformanceTestPlanCompiler.compileRequestSampler(requestNode);
        WebSocketScenarioPlanStepCursor cursor = new WebSocketScenarioPlanStepCursor(requestSampler, () -> true);

        assertNextStep(cursor, NodeType.WS_SEND, "send");
        cursor.stop();
        assertEquals(cursor.next(), null);
    }

    @Test
    public void shouldResolveCustomSendBodyWithExecutionAndIterationVariables() throws Exception {
        VariablesService.getInstance().detachContext();
        IterationDataVariableService.getInstance().detachContext();

        CountDownLatch serverReceivedMessage = new CountDownLatch(1);
        AtomicReference<String> receivedPayload = new AtomicReference<>("");

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    receivedPayload.set(text);
                    serverReceivedMessage.countDown();
                    webSocket.send("ack:" + text);
                    webSocket.close(1000, "done");
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    serverReceivedMessage.countDown();
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-custom-variable-test");
            item.setName("WS Custom Variable Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));
            item.setPrescript("pm.variables.set('runToken', 'script-token');");

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTreeNode requestData = new PerformanceTreeNode("request", NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = requestCfg;
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);
            addConnectStep(requestNode, requestCfg);

            PerformanceTreeNode sendStep = new PerformanceTreeNode("send", NodeType.WS_SEND);
            sendStep.webSocketPerformanceData = new WebSocketPerformanceData();
            sendStep.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_ON_CONNECT;
            sendStep.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
            sendStep.webSocketPerformanceData.customSendBody = "{{runToken}}/{{csvUser}}";
            requestNode.add(new PerformanceTestPlanNode(sendStep));

            PerformanceTreeNode readStep = new PerformanceTreeNode("read", NodeType.WS_READ);
            readStep.webSocketPerformanceData = new WebSocketPerformanceData();
            readStep.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.SINGLE_MESSAGE;
            readStep.webSocketPerformanceData.firstMessageTimeoutMs = 2000;
            requestNode.add(new PerformanceTestPlanNode(readStep));

            ExecutionVariableContext iterationContext = new ExecutionVariableContext(
                    new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>(Map.of("csvUser", "csv-alice"))
            );

            PerformanceRealtimeMetrics realtimeMetrics = new PerformanceRealtimeMetrics();
            realtimeMetrics.reset(System.currentTimeMillis());
            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    realtimeMetrics
            );

            executor.execute(PerformanceTestPlanCompiler.compileRequestSampler(requestNode), iterationContext);
            PerformanceRealtimeMetrics.Sample sample = realtimeMetrics.drainWindow(System.currentTimeMillis());

            assertTrue(serverReceivedMessage.await(1, TimeUnit.SECONDS), "WebSocket server should receive one message");
            assertEquals(receivedPayload.get(), "script-token/csv-alice");
            assertTrue(sample.webSocketSentRate() > 0, "WebSocket sent rate should be recorded in real time");
            assertTrue(sample.webSocketReceivedRate() > 0, "WebSocket received rate should be recorded in real time");
        } finally {
            VariablesService.getInstance().detachContext();
            IterationDataVariableService.getInstance().detachContext();
        }
    }

    @Test
    public void shouldRunWebSocketSendPreScriptBeforeEveryRepeatedCustomPayload() throws Exception {
        VariablesService.getInstance().detachContext();
        IterationDataVariableService.getInstance().detachContext();

        CountDownLatch serverReceivedMessages = new CountDownLatch(3);
        List<String> receivedPayloads = new CopyOnWriteArrayList<>();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    receivedPayloads.add(text);
                    serverReceivedMessages.countDown();
                    if (serverReceivedMessages.getCount() == 0) {
                        webSocket.close(1000, "done");
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    while (serverReceivedMessages.getCount() > 0) {
                        serverReceivedMessages.countDown();
                    }
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-repeated-custom-script-test");
            item.setName("WS Repeated Custom Script Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));
            item.setPrescript("pm.variables.set('prefix', 'script');");

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTreeNode requestData = new PerformanceTreeNode("request", NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = requestCfg;
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);
            addConnectStep(requestNode, requestCfg);

            PerformanceTreeNode sendStep = new PerformanceTreeNode("send", NodeType.WS_SEND);
            sendStep.webSocketPerformanceData = new WebSocketPerformanceData();
            sendStep.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_REPEAT;
            sendStep.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
            sendStep.webSocketPerformanceData.sendCount = 3;
            sendStep.webSocketPerformanceData.sendIntervalMs = 0;
            sendStep.webSocketPerformanceData.sendPreScript = """
                    pm.variables.set('a', pm.variables.get('prefix') + '-' + pm.info.wsSendIndex);
                    """;
            sendStep.webSocketPerformanceData.customSendBody = "{{a}}/{{csvUser}}";
            requestNode.add(new PerformanceTestPlanNode(sendStep));

            ExecutionVariableContext iterationContext = new ExecutionVariableContext(
                    new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>(Map.of("csvUser", "csv-alice"))
            );

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    new PerformanceRealtimeMetrics(),
                    () -> true,
                    () -> 64
            );

            executor.execute(PerformanceTestPlanCompiler.compileRequestSampler(requestNode), iterationContext);

            assertTrue(serverReceivedMessages.await(1, TimeUnit.SECONDS), "WebSocket server should receive repeated messages");
            assertEquals(receivedPayloads, List.of("script-0/csv-alice", "script-1/csv-alice", "script-2/csv-alice"));
        } finally {
            VariablesService.getInstance().detachContext();
            IterationDataVariableService.getInstance().detachContext();
        }
    }

    @Test
    public void shouldRunSendPreScriptBeforeRejectingEmptyRequestBody() throws Exception {
        VariablesService.getInstance().detachContext();
        IterationDataVariableService.getInstance().detachContext();

        CountDownLatch serverReceivedMessage = new CountDownLatch(1);
        AtomicReference<String> receivedPayload = new AtomicReference<>();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    receivedPayload.set(text);
                    serverReceivedMessage.countDown();
                    webSocket.close(1000, "done");
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-empty-body-send-script-test");
            item.setName("WS Empty Body Send Script Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTreeNode requestData = new PerformanceTreeNode("request", NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = requestCfg;
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);
            addConnectStep(requestNode, requestCfg);

            PerformanceTreeNode sendStep = new PerformanceTreeNode("send", NodeType.WS_SEND);
            sendStep.webSocketPerformanceData = new WebSocketPerformanceData();
            sendStep.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_ON_CONNECT;
            sendStep.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.REQUEST_BODY;
            sendStep.webSocketPerformanceData.sendPreScript = "pm.request.body = 'created-by-send-script';";
            requestNode.add(new PerformanceTestPlanNode(sendStep));

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            );

            executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertTrue(serverReceivedMessage.await(1, TimeUnit.SECONDS),
                    "WebSocket send script should be able to create an initially empty payload");
            assertEquals(receivedPayload.get(), "created-by-send-script");
        } finally {
            VariablesService.getInstance().detachContext();
            IterationDataVariableService.getInstance().detachContext();
        }
    }

    @Test
    public void shouldResolveRequestBodyTemplateAndHonorPerSendBodyMutation() throws Exception {
        VariablesService.getInstance().detachContext();
        IterationDataVariableService.getInstance().detachContext();

        CountDownLatch serverReceivedMessages = new CountDownLatch(4);
        List<String> receivedPayloads = new CopyOnWriteArrayList<>();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    receivedPayloads.add(text);
                    serverReceivedMessages.countDown();
                    if (serverReceivedMessages.getCount() == 0) {
                        webSocket.close(1000, "done");
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    while (serverReceivedMessages.getCount() > 0) {
                        serverReceivedMessages.countDown();
                    }
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-repeated-body-template-test");
            item.setName("WS Repeated Body Template Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));
            item.setBody("{{a}}");
            item.setPrescript("""
                    pm.request.body = 'wrapped-{{a}}';
                    pm.variables.set('a', 'connect-value');
                    """);

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTreeNode requestData = new PerformanceTreeNode("request", NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = requestCfg;
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);
            addConnectStep(requestNode, requestCfg);

            PerformanceTreeNode sendStep = new PerformanceTreeNode("send", NodeType.WS_SEND);
            sendStep.webSocketPerformanceData = new WebSocketPerformanceData();
            sendStep.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_REPEAT;
            sendStep.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.REQUEST_BODY;
            sendStep.webSocketPerformanceData.sendCount = 4;
            sendStep.webSocketPerformanceData.sendIntervalMs = 0;
            sendStep.webSocketPerformanceData.sendPreScript = """
                    pm.variables.set('a', 'body-' + pm.info.wsSendIndex);
                    if (pm.info.wsSendIndex === 1) {
                        pm.request.body = 'wrapped-connect-value';
                    } else if (pm.info.wsSendIndex === 2) {
                        pm.request.body = 'direct-' + pm.info.wsSendIndex;
                    }
                    """;
            requestNode.add(new PerformanceTestPlanNode(sendStep));

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            );

            executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertTrue(serverReceivedMessages.await(1, TimeUnit.SECONDS), "WebSocket server should receive repeated body messages");
            assertEquals(receivedPayloads,
                    List.of("wrapped-body-0", "wrapped-connect-value", "direct-2", "direct-2"));
        } finally {
            VariablesService.getInstance().detachContext();
            IterationDataVariableService.getInstance().detachContext();
        }
    }

    @Test
    public void shouldRepeatWebSocketSendAndReadStepsInsideLoop() throws Exception {
        VariablesService.getInstance().detachContext();
        IterationDataVariableService.getInstance().detachContext();

        CountDownLatch serverReceivedMessages = new CountDownLatch(2);
        List<String> receivedPayloads = new CopyOnWriteArrayList<>();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    receivedPayloads.add(text);
                    serverReceivedMessages.countDown();
                    webSocket.send("ack-" + text + "-" + receivedPayloads.size());
                    if (serverReceivedMessages.getCount() == 0) {
                        webSocket.close(1000, "done");
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    while (serverReceivedMessages.getCount() > 0) {
                        serverReceivedMessages.countDown();
                    }
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-loop-send-read-test");
            item.setName("WS Loop Send Read Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTreeNode requestData = new PerformanceTreeNode("request", NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = requestCfg;
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);
            addConnectStep(requestNode, requestCfg);

            PerformanceTreeNode loopData = new PerformanceTreeNode("Loop", NodeType.LOOP);
            loopData.loopData = new LoopData();
            loopData.loopData.iterations = 2;
            PerformanceTestPlanNode loopNode = new PerformanceTestPlanNode(loopData);
            requestNode.add(loopNode);

            PerformanceTreeNode sendStep = new PerformanceTreeNode("send", NodeType.WS_SEND);
            sendStep.webSocketPerformanceData = new WebSocketPerformanceData();
            sendStep.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_ON_CONNECT;
            sendStep.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
            sendStep.webSocketPerformanceData.customSendBody = "ping";
            loopNode.add(new PerformanceTestPlanNode(sendStep));

            PerformanceTreeNode readStep = new PerformanceTreeNode("read", NodeType.WS_READ);
            readStep.webSocketPerformanceData = new WebSocketPerformanceData();
            readStep.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.SINGLE_MESSAGE;
            readStep.webSocketPerformanceData.firstMessageTimeoutMs = 2000;
            loopNode.add(new PerformanceTestPlanNode(readStep));

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            );

            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertTrue(serverReceivedMessages.await(1, TimeUnit.SECONDS), "WebSocket server should receive looped messages");
            assertEquals(receivedPayloads, List.of("ping", "ping"));
            assertEquals(result.response.headers.get("X-Easy-WS-Sent-Count").get(0), "2");
            assertEquals(result.response.headers.get("X-Easy-WS-Received-Count").get(0), "2");
            assertFalse(result.response.headers.containsKey("X-Easy-WS-Completion-Reason"));
        } finally {
            VariablesService.getInstance().detachContext();
            IterationDataVariableService.getInstance().detachContext();
        }
    }

    @Test
    public void shouldMeasureFirstMessageLatencyWhenMessageArrivesBeforeReadStep() throws Exception {
        VariablesService.getInstance().detachContext();
        IterationDataVariableService.getInstance().detachContext();

        CountDownLatch serverReceivedMessages = new CountDownLatch(3);

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    serverReceivedMessages.countDown();
                    webSocket.send("ack-" + text);
                    if (serverReceivedMessages.getCount() == 0) {
                        webSocket.close(1000, "done");
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    while (serverReceivedMessages.getCount() > 0) {
                        serverReceivedMessages.countDown();
                    }
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-first-message-arrival-time-test");
            item.setName("WS First Message Arrival Time Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTreeNode requestData = new PerformanceTreeNode("request", NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = requestCfg;
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);
            addConnectStep(requestNode, requestCfg);

            PerformanceTreeNode sendStep = new PerformanceTreeNode("send", NodeType.WS_SEND);
            sendStep.webSocketPerformanceData = new WebSocketPerformanceData();
            sendStep.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_REPEAT;
            sendStep.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
            sendStep.webSocketPerformanceData.customSendBody = "hello";
            sendStep.webSocketPerformanceData.sendCount = 3;
            sendStep.webSocketPerformanceData.sendIntervalMs = 300;
            requestNode.add(new PerformanceTestPlanNode(sendStep));

            PerformanceTreeNode readStep = new PerformanceTreeNode("read", NodeType.WS_READ);
            readStep.webSocketPerformanceData = new WebSocketPerformanceData();
            readStep.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.SINGLE_MESSAGE;
            readStep.webSocketPerformanceData.firstMessageTimeoutMs = 2000;
            requestNode.add(new PerformanceTestPlanNode(readStep));

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            );

            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertTrue(serverReceivedMessages.await(1, TimeUnit.SECONDS), "WebSocket server should receive repeated messages");
            long firstMessageLatency = Long.parseLong(result.response.headers
                    .get("X-Easy-WS-First-Message-Latency-Ms")
                    .get(0));
            assertTrue(firstMessageLatency < 300,
                    "First message latency should use message arrival time, actual: " + firstMessageLatency);
        } finally {
            VariablesService.getInstance().detachContext();
            IterationDataVariableService.getInstance().detachContext();
        }
    }

    @Test
    public void shouldNotRetainWebSocketResponseBodyInEfficientModeWhenReadHasNoBodyAssertion() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send("stats-only-message");
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-no-body-retention-test");
            item.setName("WS No Body Retention Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(
                    new PerformanceTreeNode("request", NodeType.REQUEST, item)
            );
            addConnectStep(requestNode, new WebSocketPerformanceData());
            addFirstMessageReadStep(requestNode);

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    new PerformanceRealtimeMetrics(),
                    () -> true,
                    () -> 64
            );

            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(result.response.headers.get("X-Easy-WS-Received-Count").get(0), "1");
            assertEquals(result.response.headers.get("X-Easy-WS-Last-Message").get(0), "stats-only-message");
            assertEquals(result.response.body, "");
            assertEquals(result.response.bodySize, 0);
        }
    }

    @Test
    public void shouldRunMessageCountBodyAssertionAgainstCompletionMessageOnly() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send("{\"name\":\"first\"}");
                    webSocket.send("{\"name\":\"target\"}");
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-message-count-assertion-test");
            item.setName("WS Message Count Assertion Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(
                    new PerformanceTreeNode("request", NodeType.REQUEST, item)
            );
            addConnectStep(requestNode, new WebSocketPerformanceData());
            PerformanceTreeNode readStep = new PerformanceTreeNode("read", NodeType.WS_READ);
            readStep.webSocketPerformanceData = new WebSocketPerformanceData();
            readStep.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.MESSAGE_COUNT;
            readStep.webSocketPerformanceData.targetMessageCount = 2;
            readStep.webSocketPerformanceData.firstMessageTimeoutMs = 2000;
            readStep.webSocketPerformanceData.holdConnectionMs = 2000;
            PerformanceTestPlanNode readNode = new PerformanceTestPlanNode(readStep);
            readNode.add(assertionNode("JSONPath", "target", "$.name"));
            requestNode.add(readNode);

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    new PerformanceRealtimeMetrics(),
                    () -> true,
                    () -> 64
            );

            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(result.testResults.size(), 1);
            assertTrue(result.testResults.get(0).passed);
            assertEquals(result.response.headers.get("X-Easy-WS-Message-Count").get(0), "2");
        }
    }

    @Test
    public void shouldFailMessageCountFromReadStartWhenTargetNotReachedBeforeReadTimeout() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send("first-only");
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-message-count-single-timeout-test");
            item.setName("WS Message Count Single Timeout Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(
                    new PerformanceTreeNode("request", NodeType.REQUEST, item)
            );
            addConnectStep(requestNode, new WebSocketPerformanceData());
            PerformanceTreeNode readStep = new PerformanceTreeNode("read", NodeType.WS_READ);
            readStep.webSocketPerformanceData = new WebSocketPerformanceData();
            readStep.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.MESSAGE_COUNT;
            readStep.webSocketPerformanceData.targetMessageCount = 2;
            readStep.webSocketPerformanceData.firstMessageTimeoutMs = 100;
            readStep.webSocketPerformanceData.holdConnectionMs = 1200;
            requestNode.add(new PerformanceTestPlanNode(readStep));

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            );

            long start = System.currentTimeMillis();
            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );
            long elapsedMs = System.currentTimeMillis() - start;

            assertTrue(result.executionFailed);
            assertEquals(result.errorMsg,
                    I18nUtil.getMessage(MessageKeys.PERFORMANCE_MSG_WS_TARGET_MESSAGE_COUNT_TIMEOUT));
            assertTrue(elapsedMs < 900, "elapsedMs=" + elapsedMs);
        }
    }

    @Test
    public void shouldRetainWebSocketResponseBodyForRequestLevelBodyAssertion() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send("{\"name\":\"target\"}");
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-request-level-assertion-test");
            item.setName("WS Request Level Assertion Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(
                    new PerformanceTreeNode("request", NodeType.REQUEST, item)
            );
            addConnectStep(requestNode, new WebSocketPerformanceData());
            addFirstMessageReadStep(requestNode);
            requestNode.add(assertionNode("JSONPath", "target", "$.name"));

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            );

            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(result.testResults.size(), 1);
            assertTrue(result.testResults.get(0).passed);
            assertTrue(result.response.body.contains("\"name\":\"target\""));
        }
    }

    @Test
    public void shouldRetainLatestWebSocketMessageOutsideEfficientMode() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send("latest-message");
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-non-efficient-body-test");
            item.setName("WS Non Efficient Body Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(
                    new PerformanceTreeNode("request", NodeType.REQUEST, item)
            );
            addConnectStep(requestNode, new WebSocketPerformanceData());
            addFirstMessageReadStep(requestNode);

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    new PerformanceRealtimeMetrics(),
                    () -> false,
                    () -> 64
            );

            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(result.response.body, "latest-message");
            assertEquals(result.response.bodySize, "latest-message".getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        }
    }

    @Test
    public void shouldRunRequestLevelBodyAssertionAgainstLatestWebSocketMessage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send("first-message");
                    webSocket.send("second-message");
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-request-level-multi-message-assertion-test");
            item.setName("WS Request Level Multi Message Assertion Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(
                    new PerformanceTreeNode("request", NodeType.REQUEST, item)
            );
            addConnectStep(requestNode, new WebSocketPerformanceData());
            PerformanceTreeNode readStep = new PerformanceTreeNode("read", NodeType.WS_READ);
            readStep.webSocketPerformanceData = new WebSocketPerformanceData();
            readStep.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.MESSAGE_COUNT;
            readStep.webSocketPerformanceData.targetMessageCount = 2;
            readStep.webSocketPerformanceData.firstMessageTimeoutMs = 2000;
            readStep.webSocketPerformanceData.holdConnectionMs = 2000;
            requestNode.add(new PerformanceTestPlanNode(readStep));
            requestNode.add(assertionNode("Contains", "second-message", ""));

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            );

            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(result.testResults.size(), 1);
            assertTrue(result.testResults.get(0).passed);
            assertFalse(result.response.body.contains("first-message"), result.response.body);
            assertEquals(result.response.body, "second-message");
        }
    }

    @Test
    public void shouldReconnectAfterCloseStepWhenAnotherConnectStepFollows() throws Exception {
        CountDownLatch serverOpenedConnections = new CountDownLatch(2);
        CountDownLatch serverReceivedMessages = new CountDownLatch(2);
        AtomicInteger connectionIndex = new AtomicInteger();
        List<String> receivedPayloads = new CopyOnWriteArrayList<>();

        try (MockWebServer server = new MockWebServer()) {
            for (int i = 0; i < 2; i++) {
                server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                    private int connectionNumber;

                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        connectionNumber = connectionIndex.incrementAndGet();
                        serverOpenedConnections.countDown();
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        receivedPayloads.add(text);
                        serverReceivedMessages.countDown();
                        webSocket.send("ack-" + connectionNumber + "-" + text);
                    }
                }));
            }
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-reconnect-after-close-test");
            item.setName("WS Reconnect After Close Test");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTreeNode requestData = new PerformanceTreeNode("request", NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = requestCfg;
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);
            addConnectStep(requestNode, requestCfg);
            addCustomSendStep(requestNode, "first");
            addFirstMessageReadStep(requestNode);
            addCloseStep(requestNode);
            addTimerStep(requestNode, 10);
            addConnectStep(requestNode, requestCfg);
            addCustomSendStep(requestNode, "second");
            addFirstMessageReadStep(requestNode);
            addCloseStep(requestNode);

            PerformanceRequestExecutor executor = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    new PerformanceRealtimeMetrics(),
                    () -> true,
                    () -> 64
            );

            PerformanceRequestExecutionResult result = executor.execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertTrue(serverOpenedConnections.await(1, TimeUnit.SECONDS),
                    "WebSocket scenario should open a second connection after the close step");
            assertTrue(serverReceivedMessages.await(1, TimeUnit.SECONDS),
                    "WebSocket scenario should send on both connections");
            assertEquals(receivedPayloads, List.of("first", "second"));
            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(result.response.headers.get("X-Easy-WS-Received-Count").get(0), "2");
            assertEquals(result.response.headers.get("X-Easy-WS-Sent-Count").get(0), "2");
            assertEquals(result.response.body, "");
        }
    }

    @Test
    public void shouldExcludeWebSocketCloseCleanupFromReportedCost() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send("hello");
                }
            }));
            server.start();

            PreparedRequest request = new PreparedRequest();
            request.method = "GET";
            request.url = server.url("/socket").toString().replaceFirst("^http", "ws");

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST));
            PerformanceTreeNode readStep = new PerformanceTreeNode("read", NodeType.WS_READ);
            readStep.webSocketPerformanceData = new WebSocketPerformanceData();
            readStep.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.SINGLE_MESSAGE;
            readStep.webSocketPerformanceData.firstMessageTimeoutMs = 2000;
            requestNode.add(new PerformanceTestPlanNode(readStep));

            long wallStart = System.currentTimeMillis();
            WebSocketScenarioExecutor.Result result = new WebSocketScenarioExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    new SlowWebSocketSessionEndMetrics()
            ).execute(
                    request,
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    requestCfg,
                    "",
                    null,
                    "",
                    ""
            );
            long wallElapsed = System.currentTimeMillis() - wallStart;

            assertFalse(result.executionFailed, result.errorMsg);
            assertTrue(wallElapsed - result.response.costMs >= SESSION_END_DELAY_MS - 50,
                    "reported cost should exclude close cleanup delay, wallElapsed="
                            + wallElapsed + ", costMs=" + result.response.costMs);
        }
    }

    @Test
    public void shouldUseOneStableMetricsKeyForSingleWebSocketSession() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch serverReceivedMessage = new CountDownLatch(1);
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    serverReceivedMessage.countDown();
                    webSocket.send("ack");
                }
            }));
            server.start();

            PreparedRequest request = new PreparedRequest();
            request.method = "GET";
            request.url = server.url("/socket").toString().replaceFirst("^http", "ws");

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST));
            addConnectStep(requestNode, new WebSocketPerformanceData());
            addCustomSendStep(requestNode, "payload");
            addFirstMessageReadStep(requestNode);
            addCloseStep(requestNode);

            RecordingWebSocketMetrics metrics = new RecordingWebSocketMetrics();
            WebSocketScenarioExecutor.Result result = new WebSocketScenarioExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    metrics
            ).execute(
                    request,
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new WebSocketPerformanceData(),
                    "",
                    null,
                    "ws-api",
                    "WS API"
            );

            assertTrue(serverReceivedMessage.await(1, TimeUnit.SECONDS));
            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(metrics.uniqueSessionKeyCount(), 1,
                    "同一个 WebSocket 会话的 start/send/receive/match/end 必须落在同一个统计 key 上");
        }
    }

    @Test
    public void shouldMarkRepeatedSendAsInterruptedWhenRunStopsBeforeCompletion() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch firstMessageReceived = new CountDownLatch(1);
            AtomicBoolean running = new AtomicBoolean(true);
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    firstMessageReceived.countDown();
                }
            }));
            server.start();

            Thread stopper = new Thread(() -> {
                try {
                    firstMessageReceived.await(2, TimeUnit.SECONDS);
                    running.set(false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            stopper.start();

            PreparedRequest request = new PreparedRequest();
            request.method = "GET";
            request.url = server.url("/socket").toString().replaceFirst("^http", "ws");

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST));
            addConnectStep(requestNode, new WebSocketPerformanceData());
            PerformanceTreeNode sendStep = new PerformanceTreeNode("send", NodeType.WS_SEND);
            sendStep.webSocketPerformanceData = new WebSocketPerformanceData();
            sendStep.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_REPEAT;
            sendStep.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
            sendStep.webSocketPerformanceData.customSendBody = "payload";
            sendStep.webSocketPerformanceData.sendCount = 5;
            sendStep.webSocketPerformanceData.sendIntervalMs = 50;
            requestNode.add(new PerformanceTestPlanNode(sendStep));

            WebSocketScenarioExecutor.Result result = new WebSocketScenarioExecutor(
                    running::get,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    new PerformanceRealtimeMetrics()
            ).execute(
                    request,
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    requestCfg,
                    "",
                    null,
                    "",
                    ""
            );

            stopper.join(2000);
            assertTrue(result.interrupted, result.errorMsg);
            assertFalse(result.executionFailed, result.errorMsg);
            assertTrue(result.response.costMs < 1000, "run should stop before all repeated sends complete");
        }
    }

    @Test
    public void shouldExplainRemoteCloseDuringRepeatedSend() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new ClosingWebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    webSocket.close(1000, "server idle timeout");
                }
            }));
            server.start();

            PreparedRequest request = new PreparedRequest();
            request.method = "GET";
            request.url = server.url("/socket").toString().replaceFirst("^http", "ws");

            WebSocketPerformanceData requestCfg = new WebSocketPerformanceData();
            requestCfg.connectTimeoutMs = 2000;

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode("request", NodeType.REQUEST));
            addConnectStep(requestNode, new WebSocketPerformanceData());
            PerformanceTreeNode sendStep = new PerformanceTreeNode("send", NodeType.WS_SEND);
            sendStep.webSocketPerformanceData = new WebSocketPerformanceData();
            sendStep.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_REPEAT;
            sendStep.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
            sendStep.webSocketPerformanceData.customSendBody = "payload";
            sendStep.webSocketPerformanceData.sendCount = 5;
            sendStep.webSocketPerformanceData.sendIntervalMs = 1000;
            requestNode.add(new PerformanceTestPlanNode(sendStep));

            WebSocketScenarioExecutor.Result result = new WebSocketScenarioExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    new PerformanceRealtimeMetrics()
            ).execute(
                    request,
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    requestCfg,
                    "",
                    null,
                    "",
                    ""
            );

            assertTrue(result.executionFailed, result.errorMsg);
            assertTrue(result.errorMsg.contains("Remote peer closed WebSocket before send completed"), result.errorMsg);
            assertTrue(result.errorMsg.contains("server idle timeout"), result.errorMsg);
            assertEquals(result.response.headers.get("X-Easy-WS-Error").get(0), result.errorMsg);
        }
    }

    @Test
    public void shouldExplainClientHeartbeatCloseWhenPeerDoesNotPong() throws Exception {
        Method method = WebSocketScenarioExecutor.class.getDeclaredMethod("describeWebSocketFailureMessage", String.class);
        method.setAccessible(true);

        String message = (String) method.invoke(
                null,
                "sent ping but didn't receive pong within 30000ms (after 0 successful ping/pongs)"
        );

        assertTrue(message.contains("OkHttp client closed the WebSocket"), message);
        assertTrue(message.contains("peer did not respond to ping"), message);
    }

    private static class ClosingWebSocketListener extends WebSocketListener {
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(code, reason);
        }
    }

    private static final class SlowWebSocketSessionEndMetrics extends PerformanceRealtimeMetrics {
        @Override
        public void recordWebSocketSessionEnd(Object session) {
            sleepSessionEndDelay();
            super.recordWebSocketSessionEnd(session);
        }
    }

    private static final class RecordingWebSocketMetrics extends PerformanceRealtimeMetrics {
        private final java.util.Set<Integer> sessionKeys = ConcurrentHashMap.newKeySet();

        @Override
        public void recordWebSocketSessionStart(Object session, long startTimeMs, String apiId, String apiName) {
            recordSessionKey(session);
            super.recordWebSocketSessionStart(session, startTimeMs, apiId, apiName);
        }

        @Override
        public void recordWebSocketSent(Object session) {
            recordSessionKey(session);
            super.recordWebSocketSent(session);
        }

        @Override
        public void recordWebSocketReceived(Object session) {
            recordSessionKey(session);
            super.recordWebSocketReceived(session);
        }

        @Override
        public void recordWebSocketMatched(Object session) {
            recordSessionKey(session);
            super.recordWebSocketMatched(session);
        }

        @Override
        public void recordWebSocketFirstMessageLatency(Object session, long latencyMs) {
            recordSessionKey(session);
            super.recordWebSocketFirstMessageLatency(session, latencyMs);
        }

        @Override
        public void recordWebSocketSessionEnd(Object session) {
            recordSessionKey(session);
            super.recordWebSocketSessionEnd(session);
        }

        private void recordSessionKey(Object session) {
            if (session != null) {
                sessionKeys.add(System.identityHashCode(session));
            }
        }

        private int uniqueSessionKeyCount() {
            return sessionKeys.size();
        }
    }

    private static void sleepSessionEndDelay() {
        try {
            Thread.sleep(SESSION_END_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void addConnectStep(PerformanceTestPlanNode requestNode, WebSocketPerformanceData data) {
        PerformanceTreeNode connectStep = new PerformanceTreeNode("connect", NodeType.WS_CONNECT);
        connectStep.webSocketPerformanceData = data;
        requestNode.add(new PerformanceTestPlanNode(connectStep));
    }

    private static void addFirstMessageReadStep(PerformanceTestPlanNode requestNode) {
        PerformanceTreeNode readStep = new PerformanceTreeNode("read", NodeType.WS_READ);
        readStep.webSocketPerformanceData = new WebSocketPerformanceData();
        readStep.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.SINGLE_MESSAGE;
        readStep.webSocketPerformanceData.firstMessageTimeoutMs = 2000;
        requestNode.add(new PerformanceTestPlanNode(readStep));
    }

    private static void addCustomSendStep(PerformanceTestPlanNode requestNode, String body) {
        PerformanceTreeNode sendStep = new PerformanceTreeNode("send", NodeType.WS_SEND);
        sendStep.webSocketPerformanceData = new WebSocketPerformanceData();
        sendStep.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_ON_CONNECT;
        sendStep.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
        sendStep.webSocketPerformanceData.customSendBody = body;
        requestNode.add(new PerformanceTestPlanNode(sendStep));
    }

    private static void addCloseStep(PerformanceTestPlanNode requestNode) {
        requestNode.add(new PerformanceTestPlanNode(new PerformanceTreeNode("close", NodeType.WS_CLOSE)));
    }

    private static void addTimerStep(PerformanceTestPlanNode requestNode, int delayMs) {
        PerformanceTreeNode timerStep = new PerformanceTreeNode("timer", NodeType.TIMER);
        timerStep.timerData = new TimerData();
        timerStep.timerData.delayMs = delayMs;
        requestNode.add(new PerformanceTestPlanNode(timerStep));
    }

    private static PerformanceTestPlanNode assertionNode(String type, String content, String value) {
        AssertionData data = new AssertionData();
        data.type = type;
        data.content = content;
        data.value = value;
        return new PerformanceTestPlanNode(new PerformanceTreeNode(type, NodeType.ASSERTION, data));
    }

    private static void assertNextStep(WebSocketScenarioPlanStepCursor cursor, NodeType expectedType, String expectedName) {
        PerformancePlanElement next = cursor.next();
        assertEquals(next.getType(), expectedType);
        assertEquals(next.getName(), expectedName);
    }

    private record TestController(String name,
                                  int iterationCount,
                                  List<PerformancePlanElement> elements) implements PerformanceController {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public NodeType getType() {
            return NodeType.LOOP;
        }

        @Override
        public int getIterationCount() {
            return iterationCount;
        }

        @Override
        public List<PerformancePlanElement> getElements() {
            return elements;
        }
    }
}
