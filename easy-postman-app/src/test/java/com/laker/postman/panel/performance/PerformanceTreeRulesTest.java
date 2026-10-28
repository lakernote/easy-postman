package com.laker.postman.panel.performance;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.controller.ConditionData;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.core.model.NodeType;
import org.testng.annotations.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PerformanceTreeRulesTest {

    @Test(description = "共享树规则应统一约束协议阶段节点的放置位置")
    public void shouldApplyProtocolStagePlacementRules() {
        DefaultMutableTreeNode wsRequest = newRequestNode(RequestItemProtocolEnum.WEBSOCKET);
        DefaultMutableTreeNode wsLoop = newLoopNode();
        wsRequest.add(wsLoop);

        assertTrue(PerformanceTreeRules.canAcceptChild(wsRequest, newNode(NodeType.WS_CONNECT)));
        assertFalse(PerformanceTreeRules.canAcceptChild(wsLoop, newNode(NodeType.WS_CONNECT)));
        assertTrue(PerformanceTreeRules.canAcceptChild(wsLoop, newNode(NodeType.WS_SEND)));

        DefaultMutableTreeNode sseRequest = newRequestNode(RequestItemProtocolEnum.HTTP);
        ((PerformanceTreeNode) sseRequest.getUserObject()).ssePerformanceEnabled = true;

        assertTrue(PerformanceTreeRules.canAcceptChild(sseRequest, newNode(NodeType.SSE_CONNECT)));
        assertTrue(PerformanceTreeRules.canAcceptChild(sseRequest, newNode(NodeType.SSE_READ)));
        assertTrue(PerformanceTreeRules.canAcceptChild(sseReadNode(), newNode(NodeType.EXTRACTOR)));
        assertTrue(PerformanceTreeRules.canAcceptChild(newRequestNode(RequestItemProtocolEnum.HTTP), newNode(NodeType.EXTRACTOR)));
        assertFalse(PerformanceTreeRules.canAcceptChild(wsRequest, newNode(NodeType.EXTRACTOR)));
    }

    @Test(description = "CSV Data Set 只能挂在线程组下，作为线程组作用域配置")
    public void shouldOnlyAcceptCsvDataSetUnderThreadGroup() {
        DefaultMutableTreeNode root = newNode(NodeType.ROOT);
        DefaultMutableTreeNode threadGroup = newNode(NodeType.THREAD_GROUP);
        DefaultMutableTreeNode request = newRequestNode(RequestItemProtocolEnum.HTTP);
        DefaultMutableTreeNode loop = newLoopNode();

        assertTrue(PerformanceTreeRules.canAcceptChild(threadGroup, newNode(NodeType.CSV_DATA_SET)));
        assertFalse(PerformanceTreeRules.canAcceptChild(root, newNode(NodeType.CSV_DATA_SET)));
        assertFalse(PerformanceTreeRules.canAcceptChild(request, newNode(NodeType.CSV_DATA_SET)));
        assertFalse(PerformanceTreeRules.canAcceptChild(loop, newNode(NodeType.CSV_DATA_SET)));
    }

    @Test(description = "Condition 控制器应和 Loop 一样作为轻量容器，可挂请求或 WebSocket 步骤")
    public void shouldAcceptConditionControllerWhereLoopControllerIsAccepted() {
        DefaultMutableTreeNode threadGroup = newNode(NodeType.THREAD_GROUP);
        DefaultMutableTreeNode requestContainerCondition = newConditionNode();
        threadGroup.add(requestContainerCondition);
        DefaultMutableTreeNode wsRequest = newRequestNode(RequestItemProtocolEnum.WEBSOCKET);
        DefaultMutableTreeNode wsCondition = newConditionNode();
        wsRequest.add(wsCondition);

        assertTrue(PerformanceTreeRules.canAcceptChild(threadGroup, newConditionNode()));
        assertTrue(PerformanceTreeRules.canAcceptChild(requestContainerCondition, newNode(NodeType.REQUEST)));
        assertTrue(PerformanceTreeRules.canAcceptChild(requestContainerCondition, newNode(NodeType.TIMER)));
        assertTrue(PerformanceTreeRules.canAcceptChild(wsCondition, newNode(NodeType.WS_SEND)));
        assertFalse(PerformanceTreeRules.canAcceptChild(wsCondition, newNode(NodeType.WS_CONNECT)));
    }

    @Test(description = "Simple 控制器应和 Loop 一样作为无配置分组容器")
    public void shouldAcceptSimpleControllerWhereLoopControllerIsAccepted() {
        DefaultMutableTreeNode threadGroup = newNode(NodeType.THREAD_GROUP);
        DefaultMutableTreeNode requestContainerSimple = newNode(NodeType.SIMPLE);
        threadGroup.add(requestContainerSimple);
        DefaultMutableTreeNode wsRequest = newRequestNode(RequestItemProtocolEnum.WEBSOCKET);
        DefaultMutableTreeNode wsSimple = newNode(NodeType.SIMPLE);
        wsRequest.add(wsSimple);

        assertTrue(PerformanceTreeRules.canAcceptChild(threadGroup, newNode(NodeType.SIMPLE)));
        assertTrue(PerformanceTreeRules.canAcceptChild(requestContainerSimple, newNode(NodeType.REQUEST)));
        assertTrue(PerformanceTreeRules.canAcceptChild(requestContainerSimple, newNode(NodeType.TIMER)));
        assertTrue(PerformanceTreeRules.canAcceptChild(wsSimple, newNode(NodeType.WS_SEND)));
        assertFalse(PerformanceTreeRules.canAcceptChild(wsSimple, newNode(NodeType.WS_CONNECT)));
    }

    @Test(description = "Once Only 控制器应只作为请求容器，不作为 WebSocket 步骤容器")
    public void shouldAcceptOnceOnlyControllerOnlyAsRequestContainer() {
        DefaultMutableTreeNode threadGroup = newNode(NodeType.THREAD_GROUP);
        DefaultMutableTreeNode onceOnly = newNode(NodeType.ONCE_ONLY);
        threadGroup.add(onceOnly);
        DefaultMutableTreeNode wsRequest = newRequestNode(RequestItemProtocolEnum.WEBSOCKET);

        assertTrue(PerformanceTreeRules.canAcceptChild(threadGroup, newNode(NodeType.ONCE_ONLY)));
        assertTrue(PerformanceTreeRules.canAcceptChild(onceOnly, newNode(NodeType.REQUEST)));
        assertTrue(PerformanceTreeRules.canAcceptChild(onceOnly, newNode(NodeType.TIMER)));
        assertFalse(PerformanceTreeRules.canAcceptChild(wsRequest, newNode(NodeType.ONCE_ONLY)));
        assertFalse(PerformanceTreeRules.canAcceptChild(onceOnly, newNode(NodeType.WS_SEND)));

        DefaultMutableTreeNode pastedOnceOnlyWithBareWsStep = newNode(NodeType.ONCE_ONLY);
        pastedOnceOnlyWithBareWsStep.add(newNode(NodeType.WS_SEND));
        DefaultMutableTreeNode pastedOnceOnlyWithWsRequest = newNode(NodeType.ONCE_ONLY);
        DefaultMutableTreeNode childWsRequest = newRequestNode(RequestItemProtocolEnum.WEBSOCKET);
        childWsRequest.add(newNode(NodeType.WS_CONNECT));
        pastedOnceOnlyWithWsRequest.add(childWsRequest);

        assertFalse(PerformanceTreeRules.canAcceptChild(threadGroup, pastedOnceOnlyWithBareWsStep));
        assertTrue(PerformanceTreeRules.canAcceptChild(threadGroup, pastedOnceOnlyWithWsRequest));
    }

    private static DefaultMutableTreeNode newRequestNode(RequestItemProtocolEnum protocol) {
        HttpRequestItem item = new HttpRequestItem();
        item.setProtocol(protocol);
        return new DefaultMutableTreeNode(new PerformanceTreeNode(protocol.name(), NodeType.REQUEST, item));
    }

    private static DefaultMutableTreeNode newLoopNode() {
        PerformanceTreeNode loopData = new PerformanceTreeNode("Loop", NodeType.LOOP);
        loopData.loopData = new LoopData();
        return new DefaultMutableTreeNode(loopData);
    }

    private static DefaultMutableTreeNode newConditionNode() {
        PerformanceTreeNode conditionData = new PerformanceTreeNode("Condition", NodeType.CONDITION);
        conditionData.conditionData = new ConditionData();
        return new DefaultMutableTreeNode(conditionData);
    }

    private static DefaultMutableTreeNode newNode(NodeType type) {
        return new DefaultMutableTreeNode(new PerformanceTreeNode(type.name(), type));
    }

    private static DefaultMutableTreeNode sseReadNode() {
        return new DefaultMutableTreeNode(new PerformanceTreeNode("SSE Read", NodeType.SSE_READ));
    }
}
