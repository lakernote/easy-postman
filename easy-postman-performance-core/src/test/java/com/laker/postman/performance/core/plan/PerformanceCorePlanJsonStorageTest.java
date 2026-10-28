package com.laker.postman.performance.core.plan;

import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.controller.ConditionData;
import com.laker.postman.performance.core.controller.WhileData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.request.PerformanceAuthType;
import com.laker.postman.performance.core.request.PerformanceRequestExecutionScopeSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestFormDataPart;
import com.laker.postman.performance.core.request.PerformanceRequestKeyValue;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestProtocol;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.performance.core.timer.TimerData;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class PerformanceCorePlanJsonStorageTest {

    @Test
    public void shouldRoundTripCorePlanDocumentWithoutAppRequestItem() throws Exception {
        PerformanceCorePlanDocument document = document();
        PerformanceCorePlanJsonStorage storage = new PerformanceCorePlanJsonStorage();

        String json = storage.toJson(document);

        assertTrue(json.contains("\"requestSnapshot\""));
        assertTrue(json.contains("\"conditionData\""));
        assertFalse(json.contains("\"requestItem\""));
        assertFalse(json.contains("\"requestItemId\""));
        assertFalse(json.contains("\"requestInheritanceSnapshot\""));

        PerformanceCorePlanDocument loaded = storage.fromJson(json);

        PerformanceCorePlanNode loadedRoot = loaded.getRoot();
        assertEquals(loadedRoot.getType(), NodeType.ROOT);
        PerformanceCorePlanNode loadedGroup = loadedRoot.getChildren().get(0);
        assertEquals(loadedGroup.getThreadGroupData().threadMode, ThreadGroupData.ThreadMode.RAMP_UP);
        assertEquals(loadedGroup.getThreadGroupData().rampUpEndThreads, 6);
        assertEquals(loadedGroup.getThreadGroupData().maxInFlightWaitSeconds, 45);

        PerformanceCorePlanNode loadedLoop = loadedGroup.getChildren().get(0);
        assertEquals(loadedLoop.getLoopData().iterations, 3);
        PerformanceCorePlanNode loadedSimple = loadedLoop.getChildren().get(0);
        assertEquals(loadedSimple.getType(), NodeType.SIMPLE);
        PerformanceCorePlanNode loadedOnceOnly = loadedSimple.getChildren().get(0);
        assertEquals(loadedOnceOnly.getType(), NodeType.ONCE_ONLY);
        PerformanceCorePlanNode loadedWhile = loadedOnceOnly.getChildren().get(0);
        assertEquals(loadedWhile.getType(), NodeType.WHILE);
        assertEquals(loadedWhile.getWhileData().expression, "{{retryCount}} < 3");
        assertEquals(loadedWhile.getWhileData().intervalMs, 500);
        assertEquals(loadedWhile.getWhileData().maxIterations, 20);
        assertEquals(loadedWhile.getWhileData().timeoutMs, 15000);
        PerformanceCorePlanNode loadedCondition = loadedWhile.getChildren().get(0);
        assertEquals(loadedCondition.getType(), NodeType.CONDITION);
        assertEquals(loadedCondition.getConditionData().expression, "{{enabled}} == true");
        PerformanceCorePlanNode loadedRequest = loadedCondition.getChildren().get(0);
        assertEquals(loadedRequest.getWebSocketPerformanceData().connectTimeoutMs, 2468);
        assertEquals(loadedRequest.getWebSocketPerformanceData().sendMode,
                WebSocketPerformanceData.SendMode.REQUEST_BODY_REPEAT);

        PerformanceRequestSnapshot snapshot = loadedRequest.getRequestSnapshot();
        assertNotNull(snapshot);
        assertEquals(snapshot.getId(), "req-1");
        assertEquals(snapshot.getProtocol(), PerformanceRequestProtocol.WEBSOCKET);
        assertEquals(snapshot.getUrl(), "wss://example.test/ws");
        assertEquals(snapshot.getHeaders().get(0), new PerformanceRequestKeyValue(true, "X-Test", "yes"));
        assertEquals(snapshot.getFormData().get(0), new PerformanceRequestFormDataPart(true, "upload", "File", "/tmp/a.txt"));
        assertEquals(snapshot.getAuthType(), PerformanceAuthType.API_KEY);
        assertEquals(snapshot.getAuthApiKeyName(), "X-API-Key");
        assertEquals(snapshot.getAuthApiKeyValue(), "secret");
        assertEquals(snapshot.getAuthApiKeyPlacement(), PerformanceRequestSnapshot.AUTH_API_KEY_PLACEMENT_HEADER);
        assertEquals(snapshot.getProxyPolicy(), PerformanceRequestSnapshot.PROXY_POLICY_NO_PROXY);
        assertEquals(snapshot.getWebSocketPingIntervalMs(), Integer.valueOf(15000));
        assertEquals(snapshot.getExecutionScope().getGroupVariable("tenant"), "core");

        TimerData timerData = loadedRequest.getChildren().get(0).getTimerData();
        assertEquals(timerData.delayMs, 75);
    }

    @Test
    public void shouldSaveAndLoadCorePlanDocumentFromPath() throws Exception {
        PerformanceCorePlanJsonStorage storage = new PerformanceCorePlanJsonStorage();
        Path planFile = Files.createTempDirectory("ep-core-plan").resolve("plan.json");

        storage.saveDocument(planFile, document());
        PerformanceCorePlanDocument loaded = storage.loadDocument(planFile);

        assertEquals(loaded.getRoot().getChildren().get(0).getName(), "users");
    }

    @Test
    public void shouldNormalizeLegacySseRequestProtocolToHttpWhenLoadingPlan() {
        PerformanceCorePlanJsonStorage storage = new PerformanceCorePlanJsonStorage();
        String legacyJson = storage.toJson(document())
                .replaceFirst("\\\"protocol\\\"\\s*:\\s*\\\"WEBSOCKET\\\"", "\\\"protocol\\\":\\\"SSE\\\"");

        PerformanceCorePlanDocument loaded = storage.fromJson(legacyJson);
        PerformanceCorePlanNode loadedRequest = findRequestNode(loaded.getRoot());

        assertNotNull(loadedRequest);
        assertEquals(loadedRequest.getRequestSnapshot().getProtocol(), PerformanceRequestProtocol.HTTP);
    }

    private static PerformanceCorePlanNode findRequestNode(PerformanceCorePlanNode node) {
        if (node.getType() == NodeType.REQUEST) {
            return node;
        }
        for (PerformanceCorePlanNode child : node.getChildren()) {
            PerformanceCorePlanNode request = findRequestNode(child);
            if (request != null) {
                return request;
            }
        }
        return null;
    }

    private static PerformanceCorePlanDocument document() {
        Map<String, String> groupVariables = new LinkedHashMap<>();
        groupVariables.put("tenant", "core");
        groupVariables.put("region", "test");

        PerformanceRequestSnapshot snapshot = PerformanceRequestSnapshot.builder()
                .id("req-1")
                .name("socket request")
                .description("snapshot only")
                .url("wss://example.test/ws")
                .method("POST")
                .protocol(PerformanceRequestProtocol.WEBSOCKET)
                .headers(List.of(new PerformanceRequestKeyValue(true, "X-Test", "yes")))
                .params(List.of(new PerformanceRequestKeyValue(true, "debug", "1")))
                .formData(List.of(new PerformanceRequestFormDataPart(true, "upload", "File", "/tmp/a.txt")))
                .urlencoded(List.of(new PerformanceRequestKeyValue(false, "ignored", "true")))
                .bodyType("json")
                .body("{\"hello\":\"world\"}")
                .authType(PerformanceAuthType.API_KEY)
                .authApiKeyName("X-API-Key")
                .authApiKeyValue("secret")
                .authApiKeyPlacement(PerformanceRequestSnapshot.AUTH_API_KEY_PLACEMENT_HEADER)
                .followRedirects(false)
                .cookieJarEnabled(true)
                .proxyPolicy(PerformanceRequestSnapshot.PROXY_POLICY_NO_PROXY)
                .httpVersion(PerformanceRequestSnapshot.HTTP_VERSION_HTTP_2)
                .requestTimeoutMs(1500)
                .webSocketPingIntervalMs(15000)
                .prescript("pm.variables.set('a', 'b')")
                .postscript("pm.test('ok')")
                .executionScope(PerformanceRequestExecutionScopeSnapshot.fromGroupVariables(groupVariables))
                .build();

        WebSocketPerformanceData webSocketData = new WebSocketPerformanceData();
        webSocketData.connectTimeoutMs = 2468;
        webSocketData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_REPEAT;
        webSocketData.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
        webSocketData.customSendBody = "ping";
        webSocketData.sendCount = 4;
        webSocketData.completionMode = WebSocketPerformanceData.CompletionMode.MESSAGE_COUNT;
        webSocketData.targetMessageCount = 2;
        webSocketData.messageFilter = "pong";

        TimerData timerData = new TimerData();
        timerData.delayMs = 75;
        PerformanceCorePlanNode timer = PerformanceCorePlanNode.builder()
                .name("think time")
                .type(NodeType.TIMER)
                .timerData(timerData)
                .build();

        LoopData loopData = new LoopData();
        loopData.iterations = 3;
        ConditionData conditionData = new ConditionData();
        conditionData.expression = "{{enabled}} == true";
        WhileData whileData = new WhileData();
        whileData.expression = "{{retryCount}} < 3";
        whileData.intervalMs = 500;
        whileData.maxIterations = 20;
        whileData.timeoutMs = 15000;
        PerformanceCorePlanNode request = PerformanceCorePlanNode.builder()
                .name("socket request")
                .type(NodeType.REQUEST)
                .requestSnapshot(snapshot)
                .webSocketPerformanceData(webSocketData)
                .children(List.of(timer))
                .build();
        PerformanceCorePlanNode condition = PerformanceCorePlanNode.builder()
                .name("condition")
                .type(NodeType.CONDITION)
                .conditionData(conditionData)
                .children(List.of(request))
                .build();
        PerformanceCorePlanNode whileNode = PerformanceCorePlanNode.builder()
                .name("while")
                .type(NodeType.WHILE)
                .whileData(whileData)
                .children(List.of(condition))
                .build();
        PerformanceCorePlanNode onceOnly = PerformanceCorePlanNode.builder()
                .name("once only")
                .type(NodeType.ONCE_ONLY)
                .children(List.of(whileNode))
                .build();
        PerformanceCorePlanNode simple = PerformanceCorePlanNode.builder()
                .name("simple")
                .type(NodeType.SIMPLE)
                .children(List.of(onceOnly))
                .build();
        PerformanceCorePlanNode loop = PerformanceCorePlanNode.builder()
                .name("loop")
                .type(NodeType.LOOP)
                .loopData(loopData)
                .children(List.of(simple))
                .build();

        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.threadMode = ThreadGroupData.ThreadMode.RAMP_UP;
        threadGroupData.rampUpStartThreads = 2;
        threadGroupData.rampUpEndThreads = 6;
        threadGroupData.rampUpTime = 12;
        threadGroupData.useTime = false;
        threadGroupData.loops = 5;
        threadGroupData.maxInFlightWaitSeconds = 45;
        PerformanceCorePlanNode group = PerformanceCorePlanNode.builder()
                .name("users")
                .type(NodeType.THREAD_GROUP)
                .threadGroupData(threadGroupData)
                .children(List.of(loop))
                .build();

        return new PerformanceCorePlanDocument(PerformanceCorePlanNode.builder()
                .name("plan")
                .type(NodeType.ROOT)
                .children(List.of(group))
                .build());
    }
}
