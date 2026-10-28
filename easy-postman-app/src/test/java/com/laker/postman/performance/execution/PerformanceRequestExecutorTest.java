package com.laker.postman.performance.execution;

import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.model.Environment;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.assertion.AssertionData;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.model.PerformanceSampleResult;
import com.laker.postman.performance.core.model.RequestResult;
import com.laker.postman.performance.core.model.SsePerformanceData;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.plan.PerformanceAssertionElement;
import com.laker.postman.performance.plan.PerformanceRequestSampler;
import com.laker.postman.performance.plan.PerformanceTestPlanCompiler;
import com.laker.postman.performance.plan.PerformanceTestPlanNode;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.service.variable.ExecutionVariableContext;
import com.laker.postman.service.variable.RequestExecutionScope;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class PerformanceRequestExecutorTest {

    private static final long RECORDED_REQUEST_TIMEOUT_SECONDS = 5L;

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
    public void shouldKeepFullResponseBodyOutsideEfficientMode() {
        PreparedRequest.ResponseBodyMode mode = PerformanceRequestExecutor.resolveHttpResponseBodyModeForAssertionElements(
                false,
                List.of(),
                ""
        );

        assertEquals(mode, PreparedRequest.ResponseBodyMode.FULL);
    }

    @Test
    public void shouldSkipResponseBodyInEfficientModeWhenOnlyMetadataIsNeeded() {
        PreparedRequest.ResponseBodyMode mode = PerformanceRequestExecutor.resolveHttpResponseBodyModeForAssertionElements(
                true,
                List.of(assertionElement("Response Code")),
                ""
        );

        assertEquals(mode, PreparedRequest.ResponseBodyMode.METADATA_ONLY);
    }

    @Test
    public void shouldUsePreviewResponseBodyInEfficientModeWhenAssertionsNeedBody() {
        PreparedRequest.ResponseBodyMode mode = PerformanceRequestExecutor.resolveHttpResponseBodyModeForAssertionElements(
                true,
                List.of(assertionElement("Contains")),
                ""
        );

        assertEquals(mode, PreparedRequest.ResponseBodyMode.PREVIEW);
    }

    @Test
    public void shouldUsePreviewResponseBodyInEfficientModeWhenPostScriptMayReadBody() {
        PreparedRequest.ResponseBodyMode mode = PerformanceRequestExecutor.resolveHttpResponseBodyModeForAssertionElements(
                true,
                List.of(),
                "pm.test('body', () => pm.response.text())"
        );

        assertEquals(mode, PreparedRequest.ResponseBodyMode.PREVIEW);
    }

    @Test
    public void shouldResolveConfiguredPreviewLimitToBytes() {
        assertEquals(PerformanceRequestExecutor.resolveResponseBodyPreviewLimitBytes(4), 4 * 1024);
    }

    @Test
    public void shouldClampInvalidPreviewLimitToDefault() {
        assertEquals(
                PerformanceRequestExecutor.resolveResponseBodyPreviewLimitBytes(0),
                SettingManager.DEFAULT_PERFORMANCE_RESPONSE_BODY_PREVIEW_LIMIT_KB * 1024
        );
    }

    @Test
    public void shouldResolvePerformanceProtocolPriority() {
        assertEquals(PerformanceRequestProtocolResolver.resolvePerformanceProtocol(true, true), PerformanceProtocol.WEBSOCKET);
        assertEquals(PerformanceRequestProtocolResolver.resolvePerformanceProtocol(false, true), PerformanceProtocol.SSE);
        assertEquals(PerformanceRequestProtocolResolver.resolvePerformanceProtocol(false, false), PerformanceProtocol.HTTP);
    }

    @Test
    public void requestExecutorShouldDelegateRequestBuildToRuntimeAdapter() throws IOException {
        String source = Files.readString(moduleDir().resolve(
                "src/main/java/com/laker/postman/performance/execution/PerformanceRequestExecutor.java"
        ));

        assertFalse(source.contains("HttpRequestItem"));
        assertFalse(source.contains("PreparedRequestFactory"));
        assertTrue(source.contains("PerformanceRequestRuntime"));
    }

    @Test
    public void performanceTransportContextShouldNotExposeScriptExecutionPipeline() throws IOException {
        String contextSource = Files.readString(moduleDir().resolve(
                "src/main/java/com/laker/postman/performance/execution/PerformanceProtocolSamplerContext.java"
        ));
        String preparedSource = Files.readString(moduleDir().resolve(
                "src/main/java/com/laker/postman/performance/execution/PerformancePreparedRequest.java"
        ));

        assertFalse(contextSource.contains("ScriptExecutionPipeline"));
        assertFalse(preparedSource.contains("ScriptExecutionPipeline"));
        assertTrue(contextSource.contains("PerformanceScriptRuntime"));
        assertTrue(preparedSource.contains("PerformanceScriptRuntime"));
    }

    @Test
    public void shouldDisableCookieNotificationsForPerformanceRequests() {
        PreparedRequest request = new PreparedRequest();

        PerformanceRequestPreparationSupport.configurePreparedRequest(request, true);

        assertFalse(request.notifyCookieChanges);
    }

    @Test
    public void shouldApplyInjectedEventLoggingFlagToPreparedRequest() {
        PreparedRequest eventLoggingDisabled = new PreparedRequest();
        PreparedRequest eventLoggingEnabled = new PreparedRequest();

        PerformanceRequestPreparationSupport.configurePreparedRequest(eventLoggingDisabled, false);
        PerformanceRequestPreparationSupport.configurePreparedRequest(eventLoggingEnabled, true);

        assertSame(eventLoggingDisabled.captureProfile, HttpCaptureProfile.PERFORMANCE_METRICS);
        assertSame(eventLoggingEnabled.captureProfile, HttpCaptureProfile.PERFORMANCE_EVENT_TRACE);
        assertTrue(eventLoggingDisabled.collectMetricsInfo);
        assertTrue(eventLoggingEnabled.collectMetricsInfo);
        assertFalse(eventLoggingDisabled.collectEventInfo);
        assertTrue(eventLoggingEnabled.collectEventInfo);
    }

    @Test
    public void shouldApplyPureExecutionConfigToPreparedRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("ok"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("pure-execution-config");
            item.setName("Pure Execution Config");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/config").toString());

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    PerformanceExecutionConfig.fixed(false, 1, true)
            ).execute(
                    new PerformanceRequestSampler(item.getName(), item, null, List.of()),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertSame(result.request.captureProfile, HttpCaptureProfile.PERFORMANCE_EVENT_TRACE);
            assertTrue(result.request.collectMetricsInfo);
            assertTrue(result.request.collectEventInfo);
            assertEquals(result.request.responseBodyPreviewLimitBytes, 1024);
        }
    }

    @Test
    public void shouldRouteScriptConsoleOutputThroughInjectedPerformanceConfig() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("ok"));
            server.start();

            List<String> consoleOutput = new ArrayList<>();
            HttpRequestItem item = new HttpRequestItem();
            item.setId("script-output-callback");
            item.setName("Script Output Callback");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/script-output").toString());
            item.setPrescript("console.log('headless-ok');");

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    PerformanceExecutionConfig.fixed(true, 64, false, consoleOutput::add)
            ).execute(
                    new PerformanceRequestSampler(item.getName(), item, null, List.of()),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertTrue(consoleOutput.stream().anyMatch(output -> output.contains("headless-ok")));
        }
    }

    @Test
    public void defaultPerformanceExecutionConfigShouldUseHeadlessScriptOutputCallback() {
        assertTrue(PerformanceExecutionConfig.DEFAULT.scriptOutputCallback() != null);
    }

    @Test
    public void defaultPerformanceExecutionConfigShouldUseHeadlessEnvironmentSupplier() {
        assertTrue(PerformanceExecutionConfig.DEFAULT.environmentSupplier() != null);
        assertEquals(PerformanceExecutionConfig.DEFAULT.environmentSupplier().get(), null);
    }

    @Test
    public void dynamicPerformanceExecutionConfigShouldUseHeadlessScriptDefaults() {
        PerformanceExecutionConfig config = PerformanceExecutionConfig.supplying(
                () -> true,
                () -> 64,
                () -> false
        );

        assertTrue(config.scriptOutputCallback() != null);
        assertTrue(config.environmentSupplier() != null);
        assertEquals(config.environmentSupplier().get(), null);
    }

    @Test
    public void fixedPerformanceExecutionConfigShouldUseHeadlessScriptDefaultWhenCallbackIsNull() {
        PerformanceExecutionConfig config = PerformanceExecutionConfig.fixed(true, 64, false, null);

        assertTrue(config.scriptOutputCallback() != null);
    }

    @Test
    public void shouldRouteScriptErrorsThroughInjectedPerformanceConfig() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();

            List<String> consoleOutput = new ArrayList<>();
            HttpRequestItem item = new HttpRequestItem();
            item.setId("script-error-callback");
            item.setName("Script Error Callback");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/script-error").toString());
            item.setPrescript("throw new Error('headless-fail');");

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    PerformanceExecutionConfig.fixed(true, 64, false, consoleOutput::add)
            ).execute(
                    new PerformanceRequestSampler(item.getName(), item, null, List.of()),
                    new ExecutionVariableContext()
            );

            assertTrue(result.executionFailed);
            assertTrue(consoleOutput.stream().anyMatch(output ->
                    output.contains("[PreScript Error]") && output.contains("headless-fail")));
            assertEquals(server.getRequestCount(), 0);
        }
    }

    @Test
    public void shouldUseInjectedEnvironmentForPerformanceScripts() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("ok"));
            server.start();

            Environment environment = new Environment("headless-env");
            environment.addVariable("tenant", "run-tenant");
            HttpRequestItem item = new HttpRequestItem();
            item.setId("injected-env");
            item.setName("Injected Environment");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/env").toString());
            item.setPostscript("""
                    pm.test('uses injected env', function () {
                        pm.expect(pm.environment.get('tenant')).to.eql('run-tenant');
                    });
                    """);

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet(),
                    PerformanceExecutionConfig.fixed(true, 64, false, null, () -> environment)
            ).execute(
                    new PerformanceRequestSampler(item.getName(), item, null, List.of()),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(result.testResults.size(), 1);
            assertTrue(result.testResults.get(0).passed);
        }
    }

    @Test
    public void shouldResolveGroupVariablesFromHeadlessRequestScope() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("ok"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("headless-group-scope");
            item.setName("Headless Group Scope");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/scope").toString());
            item.setHeadersList(List.of(new HttpHeader(true, "X-Tenant", "{{tenantId}}")));

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            ).execute(
                    new PerformanceRequestSampler(
                            item.getName(),
                            item,
                            null,
                            List.of(),
                            RequestExecutionScope.fromGroupVariables(Map.of("tenantId", "headless-tenant"))
                    ),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(takeRecordedRequest(server).getHeader("X-Tenant"), "headless-tenant");
        }
    }

    @Test
    public void shouldNotResolveCollectionTreeInheritanceDuringExecution() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("ok"));
            server.start();

            HttpRequestItem headlessSnapshot = new HttpRequestItem();
            headlessSnapshot.setId("execution-tree-isolation");
            headlessSnapshot.setName("Headless Snapshot");
            headlessSnapshot.setProtocol(RequestItemProtocolEnum.HTTP);
            headlessSnapshot.setMethod("GET");
            headlessSnapshot.setUrl(server.url("/headless").toString());

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            ).execute(
                    new PerformanceRequestSampler(headlessSnapshot.getName(), headlessSnapshot, null, List.of()),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(takeRecordedRequest(server).getHeader("X-Tree-Inherited"), null);
        }
    }

    @Test
    public void shouldKeepApiNameInSampleResultWithoutWritingGlobalMetadata() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("ok"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("local-api-name");
            item.setName("Run Scoped API");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/local-name").toString());

            PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            ).execute(
                    new PerformanceRequestSampler(item.getName(), item, null, List.of()),
                    new ExecutionVariableContext()
            );

            RequestResult requestResult = PerformanceSampleResult.fromExecutionResult(executionResult).toRequestResult();

            assertEquals(requestResult.getApiName(), "Run Scoped API");
        }
    }

    @Test(description = "WebSocket 请求缺少启用的 Connect 阶段时不应真实发起连接")
    public void shouldNotOpenWebSocketWhenConnectStageIsMissing() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                    webSocket.close(1000, "unexpected");
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-missing-connect");
            item.setName("WS Missing Connect");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));
            PerformanceTreeNode requestData = new PerformanceTreeNode(item.getName(), NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = new WebSocketPerformanceData();
            requestData.webSocketPerformanceData.connectTimeoutMs = 500;
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            ).execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertTrue(result.executionFailed);
            assertEquals(server.getRequestCount(), 0);
        }
    }

    @Test(description = "SSE 请求缺少启用的 Connect 或 Receive 阶段时不应真实发起连接")
    public void shouldNotOpenSseWhenRequiredStagesAreMissing() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("data: unexpected\n\n"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("sse-missing-connect");
            item.setName("SSE Missing Connect");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/events").toString());
            PerformanceTreeNode requestData = new PerformanceTreeNode(item.getName(), NodeType.REQUEST, item);
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);
            PerformanceTreeNode connectData = new PerformanceTreeNode("connect", NodeType.SSE_CONNECT);
            connectData.ssePerformanceData = new SsePerformanceData();
            requestNode.add(new PerformanceTestPlanNode(connectData));

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            ).execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertTrue(result.executionFailed);
            assertEquals(server.getRequestCount(), 0);
        }
    }

    @Test
    public void shouldExposeSseBodySizeToPostScriptWithoutRetainingBodyText() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("data: hello\n\n"));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("sse-size-postscript");
            item.setName("SSE Size Postscript");
            item.setProtocol(RequestItemProtocolEnum.HTTP);
            item.setMethod("GET");
            item.setUrl(server.url("/events").toString());
            item.setHeadersList(List.of(new HttpHeader(true, "Accept", "text/event-stream")));
            item.setPostscript("""
                    pm.test('stream size', function () {
                        pm.expect(pm.response.size().body).to.be.above(0);
                    });
                    """);

            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(new PerformanceTreeNode(item.getName(), NodeType.REQUEST, item));
            PerformanceTreeNode connectData = new PerformanceTreeNode("connect", NodeType.SSE_CONNECT);
            connectData.ssePerformanceData = new SsePerformanceData();
            connectData.ssePerformanceData.connectTimeoutMs = 2000;
            requestNode.add(new PerformanceTestPlanNode(connectData));
            PerformanceTreeNode readData = new PerformanceTreeNode("read", NodeType.SSE_READ);
            readData.ssePerformanceData = new SsePerformanceData();
            readData.ssePerformanceData.firstMessageTimeoutMs = 2000;
            requestNode.add(new PerformanceTestPlanNode(readData));

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            ).execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(result.testResults.size(), 1);
            assertTrue(result.testResults.get(0).passed);
            assertEquals(result.response.body, "");
            assertTrue(result.response.bodySize > 0);
        }
    }

    @Test
    public void shouldExposeWebSocketBodySizeToPostScriptWithoutRetainingBodyText() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                    webSocket.send("hello");
                }
            }));
            server.start();

            HttpRequestItem item = new HttpRequestItem();
            item.setId("ws-size-postscript");
            item.setName("WS Size Postscript");
            item.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            item.setMethod("GET");
            item.setUrl(server.url("/socket").toString().replaceFirst("^http", "ws"));
            item.setPostscript("""
                    pm.test('stream size', function () {
                        pm.expect(pm.response.size().body).to.be.above(0);
                    });
                    """);

            PerformanceTreeNode requestData = new PerformanceTreeNode(item.getName(), NodeType.REQUEST, item);
            requestData.webSocketPerformanceData = new WebSocketPerformanceData();
            requestData.webSocketPerformanceData.connectTimeoutMs = 2000;
            PerformanceTestPlanNode requestNode = new PerformanceTestPlanNode(requestData);
            requestNode.add(new PerformanceTestPlanNode(new PerformanceTreeNode("connect", NodeType.WS_CONNECT)));
            PerformanceTreeNode readData = new PerformanceTreeNode("read", NodeType.WS_READ);
            readData.webSocketPerformanceData = new WebSocketPerformanceData();
            readData.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.SINGLE_MESSAGE;
            readData.webSocketPerformanceData.firstMessageTimeoutMs = 2000;
            requestNode.add(new PerformanceTestPlanNode(readData));

            PerformanceRequestExecutionResult result = new PerformanceRequestExecutor(
                    () -> true,
                    throwable -> false,
                    ConcurrentHashMap.newKeySet(),
                    ConcurrentHashMap.newKeySet()
            ).execute(
                    PerformanceTestPlanCompiler.compileRequestSampler(requestNode),
                    new ExecutionVariableContext()
            );

            assertFalse(result.executionFailed, result.errorMsg);
            assertEquals(result.testResults.size(), 1);
            assertTrue(result.testResults.get(0).passed);
            assertEquals(result.response.body, "");
            assertTrue(result.response.bodySize > 0);
        }
    }

    private static PerformanceAssertionElement assertionElement(String type) {
        AssertionData data = new AssertionData();
        data.type = type;
        return new PerformanceAssertionElement(type, data);
    }

    private static RecordedRequest takeRecordedRequest(MockWebServer server) throws InterruptedException {
        RecordedRequest recordedRequest = server.takeRequest(RECORDED_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(recordedRequest, "Expected MockWebServer to receive a request within "
                + RECORDED_REQUEST_TIMEOUT_SECONDS + " seconds");
        return recordedRequest;
    }

    private static Path moduleDir() {
        Path moduleDir = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(moduleDir.resolve("src/main/java"))) {
            moduleDir = moduleDir.resolve("easy-postman-app");
        }
        return moduleDir;
    }
}
