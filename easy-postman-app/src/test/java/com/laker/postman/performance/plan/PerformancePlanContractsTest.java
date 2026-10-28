package com.laker.postman.performance.plan;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.HttpRequestProxyPolicy;


import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.plan.PerformanceController;
import com.laker.postman.performance.core.plan.PerformanceCorePlanNode;
import com.laker.postman.performance.core.plan.PerformanceLoopController;
import com.laker.postman.performance.core.plan.PerformancePlanElement;
import com.laker.postman.performance.core.plan.PerformanceSampler;
import com.laker.postman.performance.core.plan.PerformanceSimpleController;
import com.laker.postman.performance.core.plan.PerformanceTestPlan;
import com.laker.postman.performance.core.plan.PerformanceTimerElement;
import com.laker.postman.performance.core.request.PerformanceRequestKeyValue;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestProtocol;
import com.laker.postman.performance.core.timer.TimerData;
import com.laker.postman.service.variable.RequestExecutionScope;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class PerformancePlanContractsTest {

    @Test
    public void loopShouldExposeControllerContract() {
        LoopData loopData = new LoopData();
        loopData.iterations = 3;
        PerformanceTimerElement timer = timerElement("timer", 100);

        PerformanceController controller = new PerformanceLoopController("loop", loopData, List.of(timer));

        assertEquals(controller.getName(), "loop");
        assertEquals(controller.getIterationCount(), 3);
        assertEquals(controller.getElements(), List.of(timer));
    }

    @Test
    public void loopShouldExposeNormalizedIterationCount() {
        LoopData loopData = new LoopData();
        loopData.iterations = 0;

        PerformanceController controller = new PerformanceLoopController("loop", loopData, List.of());

        assertEquals(controller.getIterationCount(), LoopData.MIN_ITERATIONS);
    }

    @Test
    public void simpleShouldExposeSingleIterationControllerContract() {
        PerformanceTimerElement timer = timerElement("timer", 100);

        PerformanceController controller = new PerformanceSimpleController("simple", List.of(timer));

        assertEquals(controller.getName(), "simple");
        assertEquals(controller.getType(), NodeType.SIMPLE);
        assertEquals(controller.getIterationCount(), 1);
        assertEquals(controller.getElements(), List.of(timer));
    }

    @Test
    public void requestShouldExposeSamplerContract() {
        PerformanceTimerElement timer = timerElement("timer", 50);

        PerformanceSampler sampler = new PerformanceRequestSampler(
                "http request",
                requestItem("http request", RequestItemProtocolEnum.HTTP),
                null,
                List.of(timer)
        );

        assertEquals(sampler.getName(), "http request");
        assertEquals(sampler.getChildren(), List.of(timer));
        assertFalse(sampler.executesChildrenInSamplerOrder());
    }

    @Test
    public void webSocketRequestShouldDeclareScenarioOrderedChildren() {
        PerformanceSampler sampler = new PerformanceRequestSampler(
                "ws request",
                requestItem("ws request", RequestItemProtocolEnum.WEBSOCKET),
                null,
                List.of(timerElement("ws timer", 25))
        );

        assertTrue(sampler.executesChildrenInSamplerOrder());
    }

    @Test
    public void requestSamplerShouldExposeHeadlessRequestSnapshotCopy() {
        HttpRequestItem item = requestItem("snapshot request", RequestItemProtocolEnum.WEBSOCKET);
        item.setUrl("wss://example.test/ws");
        item.setProxyPolicy(HttpRequestProxyPolicy.NO_PROXY);
        item.setHeadersList(new java.util.ArrayList<>(List.of(new HttpHeader(true, "X-Test", "before"))));

        PerformanceRequestSampler sampler = new PerformanceRequestSampler(
                "snapshot request",
                item,
                null,
                List.of()
        );
        item.setUrl("https://mutated.invalid");
        item.getHeadersList().get(0).setValue("after");

        PerformanceRequestSnapshot snapshot = sampler.getRequestSnapshot();

        assertEquals(snapshot.getName(), "snapshot request");
        assertEquals(snapshot.getUrl(), "wss://example.test/ws");
        assertEquals(snapshot.getProtocol(), PerformanceRequestProtocol.WEBSOCKET);
        assertEquals(snapshot.getProxyPolicy(), PerformanceRequestSnapshot.PROXY_POLICY_NO_PROXY);
        assertEquals(snapshot.getHeaders().get(0).getValue(), "before");
        assertTrue(snapshot.executesChildrenInSamplerOrder());
    }

    @Test
    public void requestSamplerShouldNotStoreGuiHttpRequestItem() {
        boolean hasGuiRequestField = java.util.Arrays.stream(PerformanceRequestSampler.class.getDeclaredFields())
                .anyMatch(field -> field.getType().equals(HttpRequestItem.class));

        assertFalse(hasGuiRequestField);
    }

    @Test
    public void snapshotOnlyRequestSamplerShouldAdaptToGuiHttpRequestItemView() {
        PerformanceRequestSnapshot snapshot = PerformanceRequestSnapshot.builder()
                .id("snapshot-only-id")
                .name("snapshot only")
                .url("https://example.test/sse")
                .method("GET")
                .protocol(PerformanceRequestProtocol.HTTP)
                .proxyPolicy(PerformanceRequestSnapshot.PROXY_POLICY_USE_PROXY)
                .headers(List.of(new PerformanceRequestKeyValue(true, "Accept", "text/event-stream")))
                .build();

        PerformanceRequestSampler sampler = new PerformanceRequestSampler(
                "snapshot only",
                null,
                snapshot,
                null,
                List.of(),
                null
        );

        HttpRequestItem adaptedItem = sampler.getHttpRequestItem();

        assertEquals(adaptedItem.getId(), "snapshot-only-id");
        assertEquals(adaptedItem.getUrl(), "https://example.test/sse");
        assertEquals(adaptedItem.getProtocol(), RequestItemProtocolEnum.HTTP);
        assertEquals(adaptedItem.resolveProxyPolicy(), HttpRequestProxyPolicy.USE_PROXY);
        assertEquals(adaptedItem.getHeadersList().get(0).getValue(), "text/event-stream");
        assertFalse(sampler.executesChildrenInSamplerOrder());
    }

    @Test
    public void requestSamplerWithoutRequestDataShouldNotInventGuiHttpRequestItem() {
        PerformanceRequestSampler sampler = new PerformanceRequestSampler(
                "empty request",
                null,
                null,
                List.of()
        );

        assertNull(sampler.getHttpRequestItem());
        assertNull(sampler.getRequestSnapshot());
        assertFalse(sampler.executesChildrenInSamplerOrder());
    }

    @Test
    public void planNodeShouldUseRequestSnapshotAsCanonicalRequestData() {
        HttpRequestItem item = requestItem("canonical request", RequestItemProtocolEnum.WEBSOCKET);
        item.setUrl("wss://example.test/socket");
        item.setHeadersList(new java.util.ArrayList<>(List.of(new HttpHeader(true, "X-Tenant", "before"))));

        PerformancePlanNode node = PerformancePlanNode.builder()
                .name("canonical request")
                .type(com.laker.postman.performance.core.model.NodeType.REQUEST)
                .httpRequestItem(item)
                .requestExecutionScope(RequestExecutionScope.fromGroupVariables(Map.of("tenant", "acme")))
                .build();
        item.setUrl("https://mutated.invalid");
        item.getHeadersList().get(0).setValue("after");

        assertNotNull(node.getRequestSnapshot());
        assertEquals(node.getRequestSnapshot().getUrl(), "wss://example.test/socket");
        assertEquals(node.getRequestSnapshot().getProtocol(), PerformanceRequestProtocol.WEBSOCKET);
        assertEquals(node.getRequestSnapshot().getHeaders().get(0).getValue(), "before");
        assertEquals(node.getRequestSnapshot().getExecutionScope().getGroupVariable("tenant"), "acme");
    }

    @Test
    public void planNodeShouldNotStoreGuiHttpRequestItem() {
        boolean hasGuiRequestField = java.util.Arrays.stream(PerformancePlanNode.class.getDeclaredFields())
                .anyMatch(field -> field.getType().equals(HttpRequestItem.class));

        assertFalse(hasGuiRequestField);
    }

    @Test
    public void snapshotOnlyPlanNodeShouldExposeGuiRequestViewAndScope() {
        PerformanceRequestSnapshot snapshot = PerformanceRequestSnapshot.builder()
                .id("snapshot-node-id")
                .name("snapshot node")
                .url("https://example.test/headless")
                .protocol(PerformanceRequestProtocol.HTTP)
                .executionScope(com.laker.postman.performance.core.request.PerformanceRequestExecutionScopeSnapshot
                        .fromGroupVariables(Map.of("tenant", "snapshot")))
                .build();

        PerformancePlanNode node = PerformancePlanNode.builder()
                .name("snapshot node")
                .type(com.laker.postman.performance.core.model.NodeType.REQUEST)
                .requestSnapshot(snapshot)
                .build();

        assertEquals(node.getHttpRequestItem().getId(), "snapshot-node-id");
        assertEquals(node.getHttpRequestItem().getUrl(), "https://example.test/headless");
        assertEquals(node.getRequestExecutionScope().getGroupVariable("tenant"), "snapshot");
    }

    @Test
    public void appPlanAdapterShouldCompileThroughCoreAndReturnAppRequestSampler() {
        PerformanceRequestSnapshot snapshot = PerformanceRequestSnapshot.builder()
                .id("core-adapter-id")
                .name("core adapter")
                .url("wss://example.test/core-adapter")
                .protocol(PerformanceRequestProtocol.WEBSOCKET)
                .build();
        PerformancePlanNode request = PerformancePlanNode.builder()
                .name("core adapter")
                .type(NodeType.REQUEST)
                .requestSnapshot(snapshot)
                .children(List.of(PerformancePlanNode.builder()
                        .name("timer")
                        .type(NodeType.TIMER)
                        .timerData(timerData(25))
                        .build()))
                .build();
        PerformancePlanNode group = PerformancePlanNode.builder()
                .name("group")
                .type(NodeType.THREAD_GROUP)
                .threadGroupData(new com.laker.postman.performance.core.threadgroup.ThreadGroupData())
                .children(List.of(request))
                .build();

        PerformanceTestPlan plan = PerformancePlanDocumentCompiler.compile(new PerformancePlanDocument(group));

        PerformancePlanElement element = plan.getThreadGroups().get(0).getElements().get(0);
        assertTrue(element instanceof PerformanceRequestSampler);
        PerformanceRequestSampler sampler = (PerformanceRequestSampler) element;
        assertEquals(sampler.getRequestSnapshot().getUrl(), "wss://example.test/core-adapter");
        assertTrue(sampler.executesChildrenInSamplerOrder());
        assertTrue(sampler.getChildren().get(0) instanceof PerformanceTimerElement);
    }

    @Test
    public void appPlanAdapterShouldConvertCompatibilityNodeToPureCoreNode() {
        HttpRequestItem item = requestItem("adapter request", RequestItemProtocolEnum.HTTP);
        item.setUrl("https://example.test/events");
        item.setHeadersList(List.of(new HttpHeader(true, "Accept", "text/event-stream", "")));
        PerformancePlanNode appNode = PerformancePlanNode.builder()
                .name("adapter request")
                .type(NodeType.REQUEST)
                .httpRequestItem(item)
                .requestExecutionScope(RequestExecutionScope.fromGroupVariables(Map.of("tenant", "adapter")))
                .build();

        PerformanceCorePlanNode coreNode = PerformanceCorePlanAdapter.toCoreNode(appNode);

        assertEquals(coreNode.getName(), "adapter request");
        assertEquals(coreNode.getType(), NodeType.REQUEST);
        assertEquals(coreNode.getRequestSnapshot().getUrl(), "https://example.test/events");
        assertEquals(coreNode.getRequestSnapshot().getProtocol(), PerformanceRequestProtocol.HTTP);
        assertEquals(coreNode.getRequestSnapshot().getExecutionScope().getGroupVariable("tenant"), "adapter");
    }

    private static PerformanceTimerElement timerElement(String name, int delayMs) {
        return new PerformanceTimerElement(name, timerData(delayMs));
    }

    private static TimerData timerData(int delayMs) {
        TimerData timerData = new TimerData();
        timerData.delayMs = delayMs;
        return timerData;
    }

    private static HttpRequestItem requestItem(String name, RequestItemProtocolEnum protocol) {
        HttpRequestItem item = new HttpRequestItem();
        item.setId(name + "-id");
        item.setName(name);
        item.setProtocol(protocol);
        return item;
    }
}
