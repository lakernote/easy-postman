package com.laker.postman.performance.plan;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.HttpHeader;


import com.laker.postman.performance.core.plan.PerformanceAssertionElement;
import com.laker.postman.performance.core.plan.PerformanceLoopController;
import com.laker.postman.performance.core.plan.PerformanceProtocolStageElement;
import com.laker.postman.performance.core.plan.PerformanceTestPlan;
import com.laker.postman.performance.core.plan.PerformanceThreadGroupPlan;
import com.laker.postman.performance.core.plan.PerformanceTimerElement;
import com.laker.postman.performance.core.assertion.AssertionData;
import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.config.CsvDataSetData;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.SsePerformanceData;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.performance.core.timer.TimerData;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class PerformanceTestPlanCompilerTest {

    @Test(description = "root 编译时只保留 enabled thread group，并跳过 disabled 子节点")
    public void shouldCompileOnlyEnabledThreadGroupsAndElements() {
        PerformanceTestPlanNode root = new PerformanceTestPlanNode(new PerformanceTreeNode("Plan", NodeType.ROOT));
        root.add(threadGroupNode("disabled group", false));

        PerformanceTestPlanNode enabledGroup = threadGroupNode("enabled group", true);
        enabledGroup.add(timerNode("disabled timer", false, 10));
        enabledGroup.add(timerNode("enabled timer", true, 20));
        root.add(enabledGroup);

        PerformanceTestPlan plan = PerformanceTestPlanCompiler.compile(root);

        assertEquals(plan.getThreadGroups().size(), 1);
        PerformanceThreadGroupPlan groupPlan = plan.getThreadGroups().get(0);
        assertEquals(groupPlan.getName(), "enabled group");
        assertEquals(groupPlan.getElements().size(), 1);
        assertTrue(groupPlan.getElements().get(0) instanceof PerformanceTimerElement);
        assertEquals(((PerformanceTimerElement) groupPlan.getElements().get(0)).getTimerData().delayMs, 20);
    }

    @Test(description = "传入单个 thread group 也应编译成 plan")
    public void shouldCompileSingleThreadGroupAsPlan() {
        PerformanceTestPlanNode groupNode = threadGroupNode("single group", true);
        groupNode.add(requestNode("request", true, RequestItemProtocolEnum.HTTP));

        PerformanceTestPlan plan = PerformanceTestPlanCompiler.compile(groupNode);

        assertEquals(plan.getThreadGroups().size(), 1);
        assertEquals(plan.getThreadGroups().get(0).getName(), "single group");
        assertEquals(plan.getThreadGroups().get(0).getElements().size(), 1);
    }

    @Test(description = "thread group 下的 CSV Data Set 应编译为线程组数据源，不作为执行元素")
    public void shouldCompileCsvDataSetIntoThreadGroupScope() {
        PerformanceTestPlanNode groupNode = threadGroupNode("group", true);
        groupNode.add(csvDataSetNode(
                "CSV Data Set",
                true,
                "users.csv",
                List.of("userId", "roomId"),
                List.of(Map.of("userId", "u1", "roomId", "r1"))
        ));
        groupNode.add(requestNode("request", true, RequestItemProtocolEnum.HTTP));

        PerformanceThreadGroupPlan groupPlan = PerformanceTestPlanCompiler.compile(groupNode).getThreadGroups().get(0);

        assertEquals(groupPlan.getCsvDataSetData().getSourceName(), "users.csv");
        assertEquals(groupPlan.getCsvDataSetData().getHeaders(), List.of("userId", "roomId"));
        assertEquals(groupPlan.getCsvDataSetData().getRows().get(0).get("userId"), "u1");
        assertEquals(groupPlan.getElements().size(), 1);
        assertTrue(groupPlan.getElements().get(0) instanceof PerformanceRequestSampler);
    }

    @Test(description = "nested loop、timer、request 应编译成 immutable plan elements")
    public void shouldCompileNestedLoopTimerAndRequest() {
        PerformanceTestPlanNode groupNode = threadGroupNode("group", true);
        PerformanceTestPlanNode loopNode = loopNode("loop", true, 0);
        loopNode.add(timerNode("timer", true, 250));
        loopNode.add(requestNode("request", true, RequestItemProtocolEnum.HTTP));
        groupNode.add(loopNode);

        PerformanceTestPlan plan = PerformanceTestPlanCompiler.compile(groupNode);

        PerformanceLoopController loop = (PerformanceLoopController) plan.getThreadGroups().get(0).getElements().get(0);
        assertEquals(loop.getName(), "loop");
        assertEquals(loop.getLoopData().iterations, LoopData.MIN_ITERATIONS);
        assertEquals(loop.getElements().size(), 2);
        assertTrue(loop.getElements().get(0) instanceof PerformanceTimerElement);
        assertTrue(loop.getElements().get(1) instanceof PerformanceRequestSampler);
    }

    @Test(description = "request sampler 应保留协议 stage、controller 和 assertion 子元素")
    public void shouldPreserveProtocolStagesControllersAndAssertions() {
        PerformanceTestPlanNode groupNode = threadGroupNode("group", true);
        PerformanceTestPlanNode requestNode = requestNode("ws request", true, RequestItemProtocolEnum.WEBSOCKET);
        PerformanceTestPlanNode connectNode = protocolStageNode("connect", NodeType.WS_CONNECT, true);
        requestNode.add(connectNode);
        requestNode.add(loopNode("stage loop", true, 2));
        requestNode.add(assertionNode("assertion", true, "200"));
        requestNode.add(protocolStageNode("disabled send", NodeType.WS_SEND, false));
        groupNode.add(requestNode);

        PerformanceRequestSampler sampler = (PerformanceRequestSampler) PerformanceTestPlanCompiler.compile(groupNode)
                .getThreadGroups().get(0)
                .getElements().get(0);

        assertEquals(sampler.getChildren().size(), 3);
        assertTrue(sampler.getChildren().get(0) instanceof PerformanceProtocolStageElement);
        assertEquals(sampler.getChildren().get(0).getType(), NodeType.WS_CONNECT);
        assertTrue(sampler.getChildren().get(1) instanceof PerformanceLoopController);
        assertTrue(sampler.getChildren().get(2) instanceof PerformanceAssertionElement);
        PerformanceProtocolStageElement connect = (PerformanceProtocolStageElement) sampler.getChildren().get(0);
        assertEquals(connect.getWebSocketPerformanceData().connectTimeoutMs, 1234);
        assertEquals(connect.getSsePerformanceData().connectTimeoutMs, 5678);

        PerformanceTreeNode connectData = (PerformanceTreeNode) connectNode.getUserObject();
        connectData.webSocketPerformanceData.connectTimeoutMs = 99;
        connectData.ssePerformanceData.connectTimeoutMs = 99;
        assertEquals(connect.getWebSocketPerformanceData().connectTimeoutMs, 1234);
        assertEquals(connect.getSsePerformanceData().connectTimeoutMs, 5678);

        WebSocketPerformanceData copiedWebSocketData = connect.getWebSocketPerformanceData();
        copiedWebSocketData.connectTimeoutMs = 7;
        SsePerformanceData copiedSseData = connect.getSsePerformanceData();
        copiedSseData.connectTimeoutMs = 8;
        assertEquals(connect.getWebSocketPerformanceData().connectTimeoutMs, 1234);
        assertEquals(connect.getSsePerformanceData().connectTimeoutMs, 5678);
    }

    @Test(description = "编译应深拷贝 thread group、loop、timer、request、协议阶段、WS data")
    public void shouldDeepCopyAllRuntimeData() {
        PerformanceTestPlanNode groupNode = threadGroupNode("group", true);
        PerformanceTreeNode groupData = (PerformanceTreeNode) groupNode.getUserObject();
        groupData.threadGroupData.numThreads = 3;
        PerformanceTestPlanNode csvNode = csvDataSetNode(
                "CSV Data Set",
                true,
                "users.csv",
                List.of("userId"),
                List.of(Map.of("userId", "u1"))
        );
        PerformanceTreeNode csvData = (PerformanceTreeNode) csvNode.getUserObject();

        PerformanceTestPlanNode loopNode = loopNode("loop", true, 5);
        PerformanceTreeNode loopData = (PerformanceTreeNode) loopNode.getUserObject();
        PerformanceTestPlanNode timerNode = timerNode("timer", true, 150);
        PerformanceTreeNode timerData = (PerformanceTreeNode) timerNode.getUserObject();
        PerformanceTestPlanNode requestNode = requestNode("sse request", true, RequestItemProtocolEnum.HTTP);
        PerformanceTreeNode requestData = (PerformanceTreeNode) requestNode.getUserObject();
        requestData.httpRequestItem.setHeadersList(List.of(new HttpHeader(true, "Accept", "text/event-stream")));
        requestData.webSocketPerformanceData.connectTimeoutMs = 222;
        PerformanceTestPlanNode sseReadNode = protocolStageNode("sse read", NodeType.SSE_READ, true);
        PerformanceTreeNode sseReadData = (PerformanceTreeNode) sseReadNode.getUserObject();
        sseReadData.ssePerformanceData.connectTimeoutMs = 111;
        requestNode.add(sseReadNode);
        groupNode.add(csvNode);
        loopNode.add(timerNode);
        loopNode.add(requestNode);
        groupNode.add(loopNode);

        PerformanceThreadGroupPlan groupPlan = PerformanceTestPlanCompiler.compile(groupNode).getThreadGroups().get(0);
        PerformanceLoopController loop = (PerformanceLoopController) groupPlan.getElements().get(0);
        PerformanceTimerElement timer = (PerformanceTimerElement) loop.getElements().get(0);
        PerformanceRequestSampler sampler = (PerformanceRequestSampler) loop.getElements().get(1);

        groupData.threadGroupData.numThreads = 99;
        loopData.loopData.iterations = 99;
        timerData.timerData.delayMs = 99;
        requestData.httpRequestItem.setName("mutated");
        sseReadData.ssePerformanceData.connectTimeoutMs = 99;
        requestData.webSocketPerformanceData.connectTimeoutMs = 99;
        csvData.csvDataSetData.getRows().get(0).put("userId", "mutated");
        PerformanceProtocolStageElement sseRead = (PerformanceProtocolStageElement) sampler.getChildren().get(0);

        assertNotSame(groupPlan.getThreadGroupData(), groupData.threadGroupData);
        assertNotSame(groupPlan.getCsvDataSetData(), csvData.csvDataSetData);
        assertNotSame(loop.getLoopData(), loopData.loopData);
        assertNotSame(timer.getTimerData(), timerData.timerData);
        assertNotSame(sampler.getHttpRequestItem(), requestData.httpRequestItem);
        assertNotSame(sseRead.getSsePerformanceData(), sseReadData.ssePerformanceData);
        assertNotSame(sampler.getWebSocketPerformanceData(), requestData.webSocketPerformanceData);
        assertEquals(groupPlan.getThreadGroupData().numThreads, 3);
        assertEquals(groupPlan.getCsvDataSetData().getRows().get(0).get("userId"), "u1");
        assertEquals(loop.getLoopData().iterations, 5);
        assertEquals(timer.getTimerData().delayMs, 150);
        assertEquals(sampler.getHttpRequestItem().getName(), "sse request");
        assertEquals(sseRead.getSsePerformanceData().connectTimeoutMs, 111);
        assertEquals(sampler.getWebSocketPerformanceData().connectTimeoutMs, 222);
    }

    @Test(description = "plan 列表和 data getter 不应允许调用方反向修改 plan")
    public void shouldExposeImmutablePlanState() {
        PerformanceTestPlanNode groupNode = threadGroupNode("group", true);
        groupNode.add(csvDataSetNode(
                "CSV Data Set",
                true,
                "users.csv",
                List.of("userId"),
                List.of(Map.of("userId", "u1"))
        ));
        groupNode.add(timerNode("timer", true, 150));

        PerformanceTestPlan plan = PerformanceTestPlanCompiler.compile(groupNode);
        PerformanceThreadGroupPlan groupPlan = plan.getThreadGroups().get(0);
        PerformanceTimerElement timer = (PerformanceTimerElement) groupPlan.getElements().get(0);

        expectThrows(UnsupportedOperationException.class, () -> plan.getThreadGroups().clear());
        expectThrows(UnsupportedOperationException.class, () -> groupPlan.getElements().clear());

        groupPlan.getThreadGroupData().numThreads = 99;
        groupPlan.getCsvDataSetData().getRows().clear();
        timer.getTimerData().delayMs = 99;

        assertEquals(groupPlan.getThreadGroupData().numThreads, 20);
        assertEquals(groupPlan.getCsvDataSetData().getRows().size(), 1);
        assertEquals(timer.getTimerData().delayMs, 150);
    }

    private static PerformanceTestPlanNode threadGroupNode(String name, boolean enabled) {
        PerformanceTreeNode node = new PerformanceTreeNode(name, NodeType.THREAD_GROUP, new ThreadGroupData());
        node.enabled = enabled;
        return new PerformanceTestPlanNode(node);
    }

    private static PerformanceTestPlanNode loopNode(String name, boolean enabled, int iterations) {
        LoopData data = new LoopData();
        data.iterations = iterations;
        PerformanceTreeNode node = new PerformanceTreeNode(name, NodeType.LOOP, data);
        node.enabled = enabled;
        return new PerformanceTestPlanNode(node);
    }

    private static PerformanceTestPlanNode timerNode(String name, boolean enabled, int delayMs) {
        TimerData data = new TimerData();
        data.delayMs = delayMs;
        PerformanceTreeNode node = new PerformanceTreeNode(name, NodeType.TIMER, data);
        node.enabled = enabled;
        return new PerformanceTestPlanNode(node);
    }

    private static PerformanceTestPlanNode requestNode(String name, boolean enabled, RequestItemProtocolEnum protocol) {
        HttpRequestItem item = new HttpRequestItem();
        item.setId(name + "-id");
        item.setName(name);
        item.setProtocol(protocol);
        PerformanceTreeNode node = new PerformanceTreeNode(name, NodeType.REQUEST, item);
        node.enabled = enabled;
        node.webSocketPerformanceData = new WebSocketPerformanceData();
        return new PerformanceTestPlanNode(node);
    }

    private static PerformanceTestPlanNode csvDataSetNode(String name,
                                                         boolean enabled,
                                                         String sourceName,
                                                         List<String> headers,
                                                         List<Map<String, String>> rows) {
        PerformanceTreeNode node = new PerformanceTreeNode(name, NodeType.CSV_DATA_SET);
        node.enabled = enabled;
        node.csvDataSetData = new CsvDataSetData(sourceName, headers, rows);
        return new PerformanceTestPlanNode(node);
    }

    private static PerformanceTestPlanNode protocolStageNode(String name, NodeType type, boolean enabled) {
        PerformanceTreeNode node = new PerformanceTreeNode(name, type);
        node.enabled = enabled;
        node.webSocketPerformanceData = new WebSocketPerformanceData();
        node.webSocketPerformanceData.connectTimeoutMs = 1234;
        node.ssePerformanceData = new SsePerformanceData();
        node.ssePerformanceData.connectTimeoutMs = 5678;
        return new PerformanceTestPlanNode(node);
    }

    private static PerformanceTestPlanNode assertionNode(String name, boolean enabled, String value) {
        AssertionData data = new AssertionData();
        data.value = value;
        PerformanceTreeNode node = new PerformanceTreeNode(name, NodeType.ASSERTION, data);
        node.enabled = enabled;
        return new PerformanceTestPlanNode(node);
    }
}
