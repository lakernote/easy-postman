package com.laker.postman.panel.performance;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.model.SsePerformanceData;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.request.PerformanceRequestProtocol;
import com.laker.postman.panel.performance.tree.PerformanceSwingTreePlanAdapter;
import com.laker.postman.performance.plan.PerformancePlanNode;
import org.testng.annotations.Test;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class PerformanceTreeSupportTest {

    @Test(description = "SSE 请求应自动补齐固定节点，并把断言归并到 Read 节点下")
    public void shouldCreateSseStructureAndMoveAssertionsUnderReadNode() {
        TestContext context = newSseTestContext();
        DefaultMutableTreeNode assertionNode = newNode("Assertion", NodeType.ASSERTION);
        context.treeModel.insertNodeInto(assertionNode, context.requestNode, context.requestNode.getChildCount());

        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.SSE_CONNECT, NodeType.SSE_READ));
        DefaultMutableTreeNode readNode = findChild(context.requestNode, NodeType.SSE_READ);
        assertNotNull(readNode);
        assertEquals(childTypesOf(readNode), List.of(NodeType.ASSERTION));
        assertSame(readNode.getChildAt(0), assertionNode);
        assertNotNull(((PerformanceTreeNode) findChild(context.requestNode, NodeType.SSE_CONNECT).getUserObject()).ssePerformanceData);
        assertNotNull(((PerformanceTreeNode) readNode.getUserObject()).ssePerformanceData);
    }

    @Test(description = "SSE 阶段节点应使用独立默认配置，不从父请求读取旧配置")
    public void shouldInitializeSseStageNodesWithIndependentDefaultData() {
        TestContext context = newSseTestContext();
        context.requestData.ssePerformanceData = new SsePerformanceData();
        context.requestData.ssePerformanceData.completionMode = SsePerformanceData.CompletionMode.STREAM_CLOSED;
        context.requestData.ssePerformanceData.holdConnectionMs = 30000;

        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        DefaultMutableTreeNode connectNode = findChild(context.requestNode, NodeType.SSE_CONNECT);
        DefaultMutableTreeNode readNode = findChild(context.requestNode, NodeType.SSE_READ);
        PerformanceTreeNode connectData = (PerformanceTreeNode) connectNode.getUserObject();
        PerformanceTreeNode readData = (PerformanceTreeNode) readNode.getUserObject();
        assertNotNull(connectData.ssePerformanceData);
        assertNotNull(readData.ssePerformanceData);
        assertNotSame(connectData.ssePerformanceData, context.requestData.ssePerformanceData);
        assertNotSame(readData.ssePerformanceData, context.requestData.ssePerformanceData);
        assertEquals(readData.ssePerformanceData.completionMode, SsePerformanceData.CompletionMode.SINGLE_MESSAGE);
        assertTrue(readData.name.contains("10s"), readData.name);
        assertFalse(readData.name.contains("30s"), readData.name);
    }

    @Test(description = "SSE Read 标题应跟随 Read 节点配置，而不是父请求上的旧配置")
    public void shouldRefreshSseReadTitleFromReadStageData() {
        TestContext context = newSseTestContext();
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        DefaultMutableTreeNode readNode = findChild(context.requestNode, NodeType.SSE_READ);
        PerformanceTreeNode readData = (PerformanceTreeNode) readNode.getUserObject();
        readData.ssePerformanceData.completionMode = SsePerformanceData.CompletionMode.STREAM_CLOSED;
        readData.ssePerformanceData.holdConnectionMs = 30000;

        context.treeSupport.syncRequestStructure(context.requestNode, context.requestData);

        assertTrue(readData.name.contains("30s"), readData.name);
        assertFalse(readData.name.contains("10s"), readData.name);
    }

    @Test(description = "SSE 匹配消息模式应在 Receive 节点标题展示消息过滤条件")
    public void shouldShowSseMatchedMessageFilterInReadNodeTitle() {
        TestContext context = newSseTestContext();
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        DefaultMutableTreeNode readNode = findChild(context.requestNode, NodeType.SSE_READ);
        assertNotNull(readNode);
        PerformanceTreeNode readData = (PerformanceTreeNode) readNode.getUserObject();
        readData.ssePerformanceData.completionMode = SsePerformanceData.CompletionMode.UNTIL_MATCH;
        readData.ssePerformanceData.firstMessageTimeoutMs = 10000;
        readData.ssePerformanceData.messageFilter = "done";

        context.treeSupport.syncRequestStructure(context.requestNode, context.requestData);

        assertTrue(readData.name.contains("10s"), readData.name);
        assertTrue(readData.name.contains("contains=done"), readData.name);
    }

    @Test(description = "SSE 固定时长模式不应在 Receive 节点标题展示事件过滤条件")
    public void shouldHideSseEventFilterInFixedDurationReadNodeTitle() {
        TestContext context = newSseTestContext();
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        DefaultMutableTreeNode readNode = findChild(context.requestNode, NodeType.SSE_READ);
        assertNotNull(readNode);
        PerformanceTreeNode readData = (PerformanceTreeNode) readNode.getUserObject();
        readData.ssePerformanceData.completionMode = SsePerformanceData.CompletionMode.FIXED_DURATION;
        readData.ssePerformanceData.holdConnectionMs = 30000;
        readData.ssePerformanceData.eventNameFilter = "done";

        context.treeSupport.syncRequestStructure(context.requestNode, context.requestData);

        assertTrue(readData.name.contains("30s"), readData.name);
        assertFalse(readData.name.contains("event=done"), readData.name);
    }

    @Test(description = "WebSocket 固定时长等待不应在 Read 节点标题展示消息过滤条件")
    public void shouldHideWebSocketMessageFilterInFixedDurationReadNodeTitle() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode readNode = newNode("Read", NodeType.WS_READ);
        PerformanceTreeNode readData = (PerformanceTreeNode) readNode.getUserObject();
        readData.webSocketPerformanceData = new com.laker.postman.performance.core.model.WebSocketPerformanceData();
        readData.webSocketPerformanceData.completionMode = com.laker.postman.performance.core.model.WebSocketPerformanceData.CompletionMode.FIXED_DURATION;
        readData.webSocketPerformanceData.holdConnectionMs = 30000;
        readData.webSocketPerformanceData.messageFilter = "done";
        context.treeModel.insertNodeInto(readNode, context.requestNode, context.requestNode.getChildCount());

        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        assertTrue(readData.name.contains("30s"), readData.name);
        assertFalse(readData.name.contains("contains=done"), readData.name);
    }

    @Test(description = "SSE 请求切回 HTTP 后应移除 SSE 固定节点，并把断言恢复到请求节点下")
    public void shouldRemoveSseNodesAndRestoreAssertionsWhenSwitchingBackToHttp() {
        TestContext context = newTestContext(RequestItemProtocolEnum.HTTP);
        DefaultMutableTreeNode assertionNode = newNode("Assertion", NodeType.ASSERTION);
        context.treeModel.insertNodeInto(assertionNode, context.requestNode, context.requestNode.getChildCount());

        context.treeSupport.setSsePerformanceMode(context.requestNode, true);
        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.SSE_CONNECT, NodeType.SSE_READ));
        assertEquals(context.requestData.requestSnapshot.getProtocol(), PerformanceRequestProtocol.HTTP);
        assertSame(findChild(context.requestNode, NodeType.SSE_READ).getChildAt(0), assertionNode);
        PerformancePlanNode persistedNode = PerformanceSwingTreePlanAdapter.toDocumentNode(context.requestNode);
        assertEquals(persistedNode.getRequestSnapshot().getProtocol(), PerformanceRequestProtocol.HTTP);
        DefaultMutableTreeNode restoredNode = PerformanceSwingTreePlanAdapter.toTreeNode(persistedNode);
        assertTrue(((PerformanceTreeNode) restoredNode.getUserObject()).ssePerformanceEnabled);

        context.treeSupport.setSsePerformanceMode(context.requestNode, false);

        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.ASSERTION));
        assertEquals(context.requestData.requestSnapshot.getProtocol(), PerformanceRequestProtocol.HTTP);
        assertSame(context.requestNode.getChildAt(0), assertionNode);
        assertNull(findChild(context.requestNode, NodeType.SSE_CONNECT));
        assertNull(findChild(context.requestNode, NodeType.SSE_READ));
    }

    @Test(description = "WebSocket 请求应创建连接节点并初始化默认压测数据")
    public void shouldCreateWebSocketConnectNodeAndDefaults() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);

        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.WS_CONNECT));
        assertNotNull(context.requestData.webSocketPerformanceData);
        assertNotNull(findChild(context.requestNode, NodeType.WS_CONNECT));
    }

    @Test(description = "WebSocket 同步结构时不应把 Read 节点下的断言搬回请求节点")
    public void shouldKeepWebSocketReadAssertionsWhenSyncingStructure() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode readNode = newNode("Read", NodeType.WS_READ);
        DefaultMutableTreeNode assertionNode = newNode("Assertion", NodeType.ASSERTION);
        readNode.add(assertionNode);
        context.treeModel.insertNodeInto(readNode, context.requestNode, context.requestNode.getChildCount());

        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        assertSame(assertionNode.getParent(), readNode);
        assertEquals(childTypesOf(readNode), List.of(NodeType.ASSERTION));
    }

    @Test(description = "WebSocket 请求切回 HTTP 后应清理步骤节点，并把 Read 下的断言恢复出来")
    public void shouldRemoveWebSocketNodesAndRestoreAssertionsWhenSwitchingBackToHttp() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode readNode = newNode("Read", NodeType.WS_READ);
        DefaultMutableTreeNode assertionNode = newNode("Assertion", NodeType.ASSERTION);
        readNode.add(assertionNode);
        context.treeModel.insertNodeInto(newNode("Send", NodeType.WS_SEND), context.requestNode, context.requestNode.getChildCount());
        context.treeModel.insertNodeInto(readNode, context.requestNode, context.requestNode.getChildCount());
        context.treeModel.insertNodeInto(newNode("Close", NodeType.WS_CLOSE), context.requestNode, context.requestNode.getChildCount());

        context.requestData.httpRequestItem.setProtocol(RequestItemProtocolEnum.HTTP);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.ASSERTION));
        assertSame(context.requestNode.getChildAt(0), assertionNode);
        assertNull(findChild(context.requestNode, NodeType.WS_CONNECT));
        assertNull(findChild(context.requestNode, NodeType.WS_SEND));
        assertNull(findChild(context.requestNode, NodeType.WS_READ));
        assertNull(findChild(context.requestNode, NodeType.WS_CLOSE));
    }

    @Test(description = "WebSocket 切回 HTTP 时应清理 Loop 中的步骤，并恢复嵌套 Read 断言")
    public void shouldRemoveWebSocketLoopAndRestoreNestedReadAssertionsWhenSwitchingBackToHttp() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        DefaultMutableTreeNode loopNode = newLoopNode(2);
        DefaultMutableTreeNode readNode = newNode("Read", NodeType.WS_READ);
        DefaultMutableTreeNode assertionNode = newNode("Assertion", NodeType.ASSERTION);
        readNode.add(assertionNode);
        loopNode.add(newNode("Send", NodeType.WS_SEND));
        loopNode.add(readNode);
        context.treeModel.insertNodeInto(loopNode, context.requestNode, context.requestNode.getChildCount());

        context.requestData.httpRequestItem.setProtocol(RequestItemProtocolEnum.HTTP);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.ASSERTION));
        assertSame(context.requestNode.getChildAt(0), assertionNode);
        assertNull(findChild(context.requestNode, NodeType.LOOP));
    }

    @Test(description = "WebSocket Loop 应作为步骤容器，便于在其中继续添加 Send/Read/Timer")
    public void shouldResolveWebSocketLoopAsStepParent() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        DefaultMutableTreeNode loopNode = newLoopNode(2);
        DefaultMutableTreeNode sendNode = newNode("Send", NodeType.WS_SEND);
        loopNode.add(sendNode);
        context.treeModel.insertNodeInto(loopNode, context.requestNode, context.requestNode.getChildCount());

        assertSame(context.treeSupport.resolveWebSocketStepParent(loopNode), loopNode);
        assertSame(context.treeSupport.resolveWebSocketStepParent(sendNode), loopNode);
        assertFalse(context.treeSupport.isRequestContainerLoop(loopNode));
    }

    @Test(description = "WebSocket Send 重复次数应标注为每轮次数，避免和外层 Loop 总次数混淆")
    public void shouldShowWebSocketSendRepeatCountAsPerLoopCount() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        DefaultMutableTreeNode sendNode = newNode("Send", NodeType.WS_SEND);
        PerformanceTreeNode sendData = (PerformanceTreeNode) sendNode.getUserObject();
        sendData.webSocketPerformanceData = new WebSocketPerformanceData();
        sendData.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_REPEAT;
        sendData.webSocketPerformanceData.sendCount = 3;
        context.treeModel.insertNodeInto(sendNode, context.requestNode, context.requestNode.getChildCount());

        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        assertTrue(sendData.name.contains("每轮 3 次") || sendData.name.contains("Per loop 3x"), sendData.name);
        assertFalse(sendData.name.contains(" | 3x | "), sendData.name);
    }

    @Test(description = "线程组下的 Loop 应作为请求容器，供 HTTP/SSE/WS 请求复用")
    public void shouldIdentifyLoopUnderThreadGroupAsRequestContainer() {
        DefaultMutableTreeNode root = newNode("Plan", NodeType.ROOT);
        DefaultMutableTreeNode groupNode = newNode("Group", NodeType.THREAD_GROUP);
        DefaultMutableTreeNode loopNode = newLoopNode(3);
        root.add(groupNode);
        groupNode.add(loopNode);
        PerformanceTreeSupport treeSupport = new PerformanceTreeSupport(new DefaultTreeModel(root));

        assertTrue(treeSupport.isRequestContainerLoop(loopNode));
    }

    @Test(description = "复制请求节点应复制完整子树并生成新的请求 id")
    public void shouldCopyRequestSubtreeWithIndependentRequestId() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.requestData.httpRequestItem.setId("original-request");
        context.requestData.httpRequestItem.setUrl("ws://localhost:8080/ws");
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode sendNode = newNode("Send", NodeType.WS_SEND);
        PerformanceTreeNode sendData = (PerformanceTreeNode) sendNode.getUserObject();
        sendData.webSocketPerformanceData = new WebSocketPerformanceData();
        sendData.webSocketPerformanceData.customSendBody = "hello";
        context.treeModel.insertNodeInto(sendNode, context.requestNode, context.requestNode.getChildCount());

        List<DefaultMutableTreeNode> copied = context.treeSupport.copyNodes(paths(context.requestNode));

        assertEquals(copied.size(), 1);
        PerformanceTreeNode copiedRequestData = (PerformanceTreeNode) copied.get(0).getUserObject();
        assertNotSame(copiedRequestData.httpRequestItem, context.requestData.httpRequestItem);
        assertNotEquals(copiedRequestData.httpRequestItem.getId(), context.requestData.httpRequestItem.getId());
        assertEquals(copiedRequestData.httpRequestItem.getUrl(), "ws://localhost:8080/ws");
        assertEquals(childTypesOf(copied.get(0)), List.of(NodeType.WS_CONNECT, NodeType.WS_SEND));
        PerformanceTreeNode copiedSendData = (PerformanceTreeNode) ((DefaultMutableTreeNode) copied.get(0).getChildAt(1)).getUserObject();
        assertNotSame(copiedSendData.webSocketPerformanceData, sendData.webSocketPerformanceData);
        assertEquals(copiedSendData.webSocketPerformanceData.customSendBody, "hello");
    }

    @Test(description = "多选复制时应过滤已包含在父节点子树中的重复子节点")
    public void shouldCopyOnlyTopLevelNodesFromMultiSelection() {
        DefaultMutableTreeNode root = newNode("Plan", NodeType.ROOT);
        DefaultMutableTreeNode groupNode = newNode("Group", NodeType.THREAD_GROUP);
        DefaultMutableTreeNode loopNode = newLoopNode(3);
        DefaultMutableTreeNode nestedRequest = newRequestNode("nested", "Nested");
        DefaultMutableTreeNode siblingRequest = newRequestNode("sibling", "Sibling");
        root.add(groupNode);
        groupNode.add(loopNode);
        loopNode.add(nestedRequest);
        groupNode.add(siblingRequest);
        PerformanceTreeSupport treeSupport = new PerformanceTreeSupport(new DefaultTreeModel(root));

        List<DefaultMutableTreeNode> copied = treeSupport.copyNodes(paths(loopNode, nestedRequest, siblingRequest));

        assertEquals(copied.size(), 2);
        assertEquals(nodeType(copied.get(0)), NodeType.LOOP);
        assertEquals(nodeType(copied.get(1)), NodeType.REQUEST);
        assertEquals(childTypesOf(copied.get(0)), List.of(NodeType.REQUEST));
    }

    @Test(description = "粘贴请求到另一个请求上时应作为同级插入到目标之后")
    public void shouldPasteRequestAfterSelectedSiblingWhenTargetCannotContainRequest() {
        DefaultMutableTreeNode root = newNode("Plan", NodeType.ROOT);
        DefaultMutableTreeNode groupNode = newNode("Group", NodeType.THREAD_GROUP);
        DefaultMutableTreeNode firstRequest = newRequestNode("first", "First");
        DefaultMutableTreeNode secondRequest = newRequestNode("second", "Second");
        root.add(groupNode);
        groupNode.add(firstRequest);
        groupNode.add(secondRequest);
        DefaultTreeModel treeModel = new DefaultTreeModel(root);
        PerformanceTreeSupport treeSupport = new PerformanceTreeSupport(treeModel);
        List<DefaultMutableTreeNode> copied = treeSupport.copyNodes(paths(firstRequest));

        List<DefaultMutableTreeNode> pasted = treeSupport.pasteNodes(new JTree(treeModel), secondRequest, copied);

        assertEquals(pasted.size(), 1);
        assertSame(groupNode.getChildAt(2), pasted.get(0));
        PerformanceTreeNode pastedData = (PerformanceTreeNode) pasted.get(0).getUserObject();
        assertNotEquals(pastedData.httpRequestItem.getId(), "first");
        assertNotEquals(pastedData.httpRequestItem.getId(), ((PerformanceTreeNode) copied.get(0).getUserObject()).httpRequestItem.getId());
    }

    @Test(description = "粘贴 WebSocket 步骤到 WebSocket 请求上时应插入步骤并保留步骤配置")
    public void shouldPasteWebSocketStepIntoWebSocketRequest() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode sendNode = newNode("Send", NodeType.WS_SEND);
        PerformanceTreeNode sendData = (PerformanceTreeNode) sendNode.getUserObject();
        sendData.webSocketPerformanceData = new WebSocketPerformanceData();
        sendData.webSocketPerformanceData.customSendBody = "payload";
        context.treeModel.insertNodeInto(sendNode, context.requestNode, context.requestNode.getChildCount());
        List<DefaultMutableTreeNode> copied = context.treeSupport.copyNodes(paths(sendNode));

        List<DefaultMutableTreeNode> pasted = context.treeSupport.pasteNodes(new JTree(context.treeModel), context.requestNode, copied);

        assertEquals(pasted.size(), 1);
        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.WS_CONNECT, NodeType.WS_SEND, NodeType.WS_SEND));
        PerformanceTreeNode pastedData = (PerformanceTreeNode) pasted.get(0).getUserObject();
        assertNotSame(pastedData.webSocketPerformanceData, sendData.webSocketPerformanceData);
        assertEquals(pastedData.webSocketPerformanceData.customSendBody, "payload");
    }

    @Test(description = "SSE 固定阶段节点也应支持单独复制")
    public void shouldCopySseStageNodesIndividually() {
        TestContext context = newSseTestContext();
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode connectNode = findChild(context.requestNode, NodeType.SSE_CONNECT);
        DefaultMutableTreeNode readNode = findChild(context.requestNode, NodeType.SSE_READ);

        assertTrue(context.treeSupport.hasCopyableNodes(paths(connectNode)));
        assertTrue(context.treeSupport.hasCopyableNodes(paths(readNode)));
        assertEquals(context.treeSupport.copyNodes(paths(connectNode, readNode)).size(), 2);
    }

    @Test(description = "粘贴 SSE 阶段节点到 SSE 请求上时应插入阶段节点")
    public void shouldPasteSseStageNodeIntoSseRequest() {
        TestContext context = newSseTestContext();
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode readNode = findChild(context.requestNode, NodeType.SSE_READ);
        PerformanceTreeNode readData = (PerformanceTreeNode) readNode.getUserObject();
        readData.ssePerformanceData = new SsePerformanceData();
        readData.ssePerformanceData.messageFilter = "ready";
        List<DefaultMutableTreeNode> copied = context.treeSupport.copyNodes(paths(readNode));

        List<DefaultMutableTreeNode> pasted = context.treeSupport.pasteNodes(new JTree(context.treeModel), context.requestNode, copied);

        assertEquals(pasted.size(), 1);
        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.SSE_CONNECT, NodeType.SSE_READ, NodeType.SSE_READ));
        PerformanceTreeNode pastedData = (PerformanceTreeNode) pasted.get(0).getUserObject();
        assertNotSame(pastedData.ssePerformanceData, readData.ssePerformanceData);
        assertEquals(pastedData.ssePerformanceData.messageFilter, "ready");
    }

    @Test(description = "WebSocket Connect 节点应支持单独复制")
    public void shouldCopyWebSocketConnectNodeIndividually() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode connectNode = findChild(context.requestNode, NodeType.WS_CONNECT);

        assertTrue(context.treeSupport.hasCopyableNodes(paths(connectNode)));
        assertEquals(context.treeSupport.copyNodes(paths(connectNode)).size(), 1);
    }

    @Test(description = "粘贴 WebSocket Connect 到 WebSocket 请求上时应插入连接节点")
    public void shouldPasteWebSocketConnectIntoWebSocketRequest() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode connectNode = findChild(context.requestNode, NodeType.WS_CONNECT);
        PerformanceTreeNode connectData = (PerformanceTreeNode) connectNode.getUserObject();
        connectData.webSocketPerformanceData = new WebSocketPerformanceData();
        connectData.webSocketPerformanceData.connectTimeoutMs = 15000;
        List<DefaultMutableTreeNode> copied = context.treeSupport.copyNodes(paths(connectNode));

        List<DefaultMutableTreeNode> pasted = context.treeSupport.pasteNodes(new JTree(context.treeModel), context.requestNode, copied);

        assertEquals(pasted.size(), 1);
        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.WS_CONNECT, NodeType.WS_CONNECT));
        PerformanceTreeNode pastedData = (PerformanceTreeNode) pasted.get(0).getUserObject();
        assertNotSame(pastedData.webSocketPerformanceData, connectData.webSocketPerformanceData);
        assertEquals(pastedData.webSocketPerformanceData.connectTimeoutMs, 15000);
    }

    @Test(description = "WebSocket Connect 粘到场景 Loop 上时应回落为请求级阶段节点")
    public void shouldPasteWebSocketConnectAtRequestLevelWhenTargetingScenarioLoop() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);
        DefaultMutableTreeNode connectNode = findChild(context.requestNode, NodeType.WS_CONNECT);
        DefaultMutableTreeNode loopNode = newLoopNode(2);
        context.treeModel.insertNodeInto(loopNode, context.requestNode, context.requestNode.getChildCount());
        List<DefaultMutableTreeNode> copied = context.treeSupport.copyNodes(paths(connectNode));

        List<DefaultMutableTreeNode> pasted = context.treeSupport.pasteNodes(new JTree(context.treeModel), loopNode, copied);

        assertEquals(pasted.size(), 1);
        assertEquals(childTypesOf(loopNode), List.of());
        assertSame(pasted.get(0).getParent(), context.requestNode);
        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.WS_CONNECT, NodeType.WS_CONNECT, NodeType.LOOP));
    }

    @Test(description = "从 WebSocket Loop 内显式添加 Connect 时应补到请求根节点")
    public void shouldAddWebSocketConnectAtRequestLevelWhenSelectionIsScenarioLoop() {
        TestContext context = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        DefaultMutableTreeNode loopNode = newLoopNode(2);
        context.treeModel.insertNodeInto(loopNode, context.requestNode, context.requestNode.getChildCount());
        JTree tree = new JTree(context.treeModel);
        tree.setSelectionPath(new TreePath(loopNode.getPath()));

        context.treeSupport.addWebSocketStepNode(tree, NodeType.WS_CONNECT, () -> {
        });

        assertEquals(childTypesOf(loopNode), List.of());
        assertEquals(childTypesOf(context.requestNode), List.of(NodeType.WS_CONNECT, NodeType.LOOP));
    }

    @Test(description = "SSE Connect、SSE Read、WebSocket Connect 阶段节点应支持删除")
    public void shouldDeleteProtocolStageNodes() {
        TestContext sseContext = newSseTestContext();
        sseContext.treeSupport.ensureRequestStructure(sseContext.requestNode, sseContext.requestData);
        DefaultMutableTreeNode sseConnectNode = findChild(sseContext.requestNode, NodeType.SSE_CONNECT);
        DefaultMutableTreeNode sseReadNode = findChild(sseContext.requestNode, NodeType.SSE_READ);

        assertTrue(sseContext.treeSupport.hasDeletableNodes(paths(sseConnectNode, sseReadNode)));
        List<DefaultMutableTreeNode> deletedSseNodes = sseContext.treeSupport.deleteNodes(paths(sseConnectNode, sseReadNode));

        assertEquals(deletedSseNodes.size(), 2);
        assertEquals(childTypesOf(sseContext.requestNode), List.of());

        TestContext wsContext = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        wsContext.treeSupport.ensureRequestStructure(wsContext.requestNode, wsContext.requestData);
        DefaultMutableTreeNode wsConnectNode = findChild(wsContext.requestNode, NodeType.WS_CONNECT);

        assertTrue(wsContext.treeSupport.hasDeletableNodes(paths(wsConnectNode)));
        List<DefaultMutableTreeNode> deletedWsNodes = wsContext.treeSupport.deleteNodes(paths(wsConnectNode));

        assertEquals(deletedWsNodes.size(), 1);
        assertEquals(childTypesOf(wsContext.requestNode), List.of());
    }

    @Test(description = "删除多选节点时应过滤父节点子树内的重复子节点")
    public void shouldDeleteOnlyTopLevelNodesFromMultiSelection() {
        DefaultMutableTreeNode root = newNode("Plan", NodeType.ROOT);
        DefaultMutableTreeNode groupNode = newNode("Group", NodeType.THREAD_GROUP);
        DefaultMutableTreeNode requestNode = newRequestNode("request", "Request");
        DefaultMutableTreeNode timerNode = newNode("Timer", NodeType.TIMER);
        root.add(groupNode);
        groupNode.add(requestNode);
        requestNode.add(timerNode);
        PerformanceTreeSupport treeSupport = new PerformanceTreeSupport(new DefaultTreeModel(root));

        List<DefaultMutableTreeNode> deletedNodes = treeSupport.deleteNodes(paths(root, requestNode, timerNode));

        assertEquals(deletedNodes.size(), 1);
        assertSame(deletedNodes.get(0), requestNode);
        assertEquals(childTypesOf(groupNode), List.of());
        assertFalse(treeSupport.hasDeletableNodes(paths(root)));
    }

    @Test(description = "删除协议阶段节点后，普通同步不应自动补回")
    public void shouldNotRecreateDeletedProtocolStagesDuringSync() {
        TestContext sseContext = newSseTestContext();
        sseContext.treeSupport.ensureRequestStructure(sseContext.requestNode, sseContext.requestData);
        sseContext.treeSupport.deleteNodes(paths(
                findChild(sseContext.requestNode, NodeType.SSE_CONNECT),
                findChild(sseContext.requestNode, NodeType.SSE_READ)
        ));

        sseContext.treeSupport.syncRequestStructure(sseContext.requestNode, sseContext.requestData);

        assertEquals(childTypesOf(sseContext.requestNode), List.of());

        TestContext wsContext = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        wsContext.treeSupport.ensureRequestStructure(wsContext.requestNode, wsContext.requestData);
        wsContext.treeSupport.deleteNodes(paths(findChild(wsContext.requestNode, NodeType.WS_CONNECT)));

        wsContext.treeSupport.syncRequestStructure(wsContext.requestNode, wsContext.requestData);

        assertEquals(childTypesOf(wsContext.requestNode), List.of());
    }

    @Test(description = "协议阶段节点应能通过显式添加补回")
    public void shouldAddProtocolStageNodesExplicitly() {
        TestContext sseContext = newSseTestContext();
        JTree sseTree = new JTree(sseContext.treeModel);
        sseTree.setSelectionPath(new TreePath(sseContext.requestNode.getPath()));

        sseContext.treeSupport.addSseStageNode(sseTree, NodeType.SSE_CONNECT, () -> {
        });
        sseContext.treeSupport.addSseStageNode(sseTree, NodeType.SSE_READ, () -> {
        });

        assertEquals(childTypesOf(sseContext.requestNode), List.of(NodeType.SSE_CONNECT, NodeType.SSE_READ));

        TestContext wsContext = newTestContext(RequestItemProtocolEnum.WEBSOCKET);
        JTree wsTree = new JTree(wsContext.treeModel);
        wsTree.setSelectionPath(new TreePath(wsContext.requestNode.getPath()));

        wsContext.treeSupport.addWebSocketStepNode(wsTree, NodeType.WS_CONNECT, () -> {
        });

        assertEquals(childTypesOf(wsContext.requestNode), List.of(NodeType.WS_CONNECT));
    }

    @Test(description = "Accept 头不应隐式更改压测场景，SSE 模式由用户明确选择")
    public void shouldNotInferSsePerformanceModeFromAcceptHeader() {
        TestContext context = newTestContext(RequestItemProtocolEnum.HTTP);
        context.requestData.httpRequestItem.setHeadersList(List.of(
                new HttpHeader(true, "Accept", "text/event-stream")
        ));

        assertEquals(context.treeSupport.resolvePerformanceProtocol(context.requestData), PerformanceProtocol.HTTP);

        context.requestData.ssePerformanceEnabled = true;
        context.treeSupport.ensureRequestStructure(context.requestNode, context.requestData);

        assertEquals(context.treeSupport.resolvePerformanceProtocol(context.requestData), PerformanceProtocol.SSE);
    }

    private static TestContext newTestContext(RequestItemProtocolEnum protocol) {
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Request");
        item.setProtocol(protocol);
        PerformanceTreeNode requestData = new PerformanceTreeNode("Request", NodeType.REQUEST, item);
        DefaultMutableTreeNode root = newNode("Plan", NodeType.ROOT);
        DefaultMutableTreeNode requestNode = new DefaultMutableTreeNode(requestData);
        root.add(requestNode);
        DefaultTreeModel treeModel = new DefaultTreeModel(root);
        return new TestContext(new PerformanceTreeSupport(treeModel), treeModel, requestNode, requestData);
    }

    private static TestContext newSseTestContext() {
        TestContext context = newTestContext(RequestItemProtocolEnum.HTTP);
        context.requestData.ssePerformanceEnabled = true;
        return context;
    }

    private static DefaultMutableTreeNode newNode(String name, NodeType type) {
        return new DefaultMutableTreeNode(new PerformanceTreeNode(name, type));
    }

    private static DefaultMutableTreeNode newRequestNode(String id, String name) {
        HttpRequestItem item = new HttpRequestItem();
        item.setId(id);
        item.setName(name);
        return new DefaultMutableTreeNode(new PerformanceTreeNode(name, NodeType.REQUEST, item));
    }

    private static DefaultMutableTreeNode newLoopNode(int iterations) {
        PerformanceTreeNode loopData = new PerformanceTreeNode("Loop", NodeType.LOOP);
        loopData.loopData = new LoopData();
        loopData.loopData.iterations = iterations;
        return new DefaultMutableTreeNode(loopData);
    }

    private static DefaultMutableTreeNode findChild(DefaultMutableTreeNode parent, NodeType type) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object userObject = child.getUserObject();
            if (userObject instanceof PerformanceTreeNode node && node.type == type) {
                return child;
            }
        }
        return null;
    }

    private static List<NodeType> childTypesOf(DefaultMutableTreeNode parent) {
        List<NodeType> types = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            Object userObject = ((DefaultMutableTreeNode) parent.getChildAt(i)).getUserObject();
            if (userObject instanceof PerformanceTreeNode node) {
                types.add(node.type);
            }
        }
        return types;
    }

    private static TreePath[] paths(DefaultMutableTreeNode... nodes) {
        TreePath[] paths = new TreePath[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            paths[i] = new TreePath(nodes[i].getPath());
        }
        return paths;
    }

    private static NodeType nodeType(DefaultMutableTreeNode node) {
        return ((PerformanceTreeNode) node.getUserObject()).type;
    }

    private record TestContext(
            PerformanceTreeSupport treeSupport,
            DefaultTreeModel treeModel,
            DefaultMutableTreeNode requestNode,
            PerformanceTreeNode requestData
    ) {
    }
}
