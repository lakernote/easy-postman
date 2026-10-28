package com.laker.postman.panel.performance.tree;

import com.laker.postman.panel.performance.PerformanceTreeRules;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.SsePerformanceData;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class PerformanceTreeStructureSupport {

    private final DefaultTreeModel treeModel;

    public void syncRequestStructure(DefaultMutableTreeNode requestNode, PerformanceTreeNode requestData) {
        syncRequestStructure(requestNode, requestData, false);
    }

    public void ensureRequestStructure(DefaultMutableTreeNode requestNode, PerformanceTreeNode requestData) {
        syncRequestStructure(requestNode, requestData, true);
    }

    public void setSsePerformanceEnabled(DefaultMutableTreeNode requestNode,
                                         PerformanceTreeNode requestData,
                                         boolean enabled) {
        if (requestData == null || requestData.type != NodeType.REQUEST) {
            return;
        }
        requestData.ssePerformanceEnabled = enabled;
        syncRequestStructure(requestNode, requestData, enabled);
    }

    private void syncRequestStructure(DefaultMutableTreeNode requestNode,
                                      PerformanceTreeNode requestData,
                                      boolean ensureMissingStages) {
        if (requestNode == null || requestData == null || requestData.httpRequestItem == null) {
            return;
        }

        boolean isSse = requestData.ssePerformanceEnabled;
        boolean isWebSocket = PerformanceTreeRules.isWebSocketPerfRequest(requestData.httpRequestItem);

        cleanupSseRequestStructure(requestNode, !isSse);
        cleanupWebSocketRequestStructure(requestNode, !isWebSocket);

        if (isSse) {
            if (ensureMissingStages) {
                DefaultMutableTreeNode connectNode = ensureFixedChildNode(
                        requestNode,
                        NodeType.SSE_CONNECT,
                        I18nUtil.getMessage(MessageKeys.PERFORMANCE_SSE_NODE_CONNECT),
                        0
                );
                DefaultMutableTreeNode readNode = ensureFixedChildNode(
                        requestNode,
                        NodeType.SSE_READ,
                        PerformanceTreeNodeTitleFormatter.sseReadTitle(new SsePerformanceData()),
                        1
                );
                ensureSseStageData(connectNode);
                ensureSseStageData(readNode);
            }
            ensureSseStageData(requestNode);
            DefaultMutableTreeNode readNode = findChildNode(requestNode, NodeType.SSE_READ);
            if (readNode != null) {
                moveChildrenByType(requestNode, readNode, NodeType.ASSERTION);
                moveChildrenByType(requestNode, readNode, NodeType.EXTRACTOR);
            }
            refreshSseStageTitles(requestNode);
        } else if (isWebSocket) {
            if (requestData.webSocketPerformanceData == null) {
                requestData.webSocketPerformanceData = new WebSocketPerformanceData();
            }
            if (ensureMissingStages) {
                ensureFixedChildNode(
                        requestNode,
                        NodeType.WS_CONNECT,
                        I18nUtil.getMessage(MessageKeys.PERFORMANCE_WS_NODE_CONNECT),
                        0
                );
            }
            refreshWebSocketStepTitles(requestNode);
        }
    }

    public void syncAllRequestStructures(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();
        if (userObj instanceof PerformanceTreeNode nodeData && nodeData.type == NodeType.REQUEST) {
            syncRequestStructure(node, nodeData);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            syncAllRequestStructures((DefaultMutableTreeNode) node.getChildAt(i));
        }
    }

    public void addWebSocketStepNode(JTree performanceTree, NodeType type, Runnable saveConfigAction) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        DefaultMutableTreeNode parentNode = type == NodeType.WS_CONNECT
                ? resolveWebSocketConnectParent(selectedNode)
                : resolveWebSocketStepParent(selectedNode);
        DefaultMutableTreeNode requestNode = type == NodeType.WS_CONNECT
                ? parentNode
                : getParentWebSocketRequestNode(parentNode);
        if (parentNode == null || requestNode == null || !(requestNode.getUserObject() instanceof PerformanceTreeNode requestNodeData)) {
            return;
        }
        WebSocketPerformanceData defaults = requestNodeData.webSocketPerformanceData != null
                ? requestNodeData.webSocketPerformanceData
                : new WebSocketPerformanceData();
        DefaultMutableTreeNode newNode = PerformanceTreeNodeFactory.webSocketStepNode(type, defaults);
        int insertIndex;
        if (type == NodeType.WS_CONNECT) {
            insertIndex = resolveWebSocketConnectInsertIndex(requestNode, selectedNode);
        } else {
            insertIndex = parentNode.getChildCount();
            if (selectedNode != null && selectedNode.getParent() == parentNode) {
                insertIndex = parentNode.getIndex(selectedNode) + 1;
            }
            if (parentNode == requestNode) {
                insertIndex = Math.max(1, insertIndex);
            }
        }
        treeModel.insertNodeInto(newNode, parentNode, Math.min(insertIndex, parentNode.getChildCount()));
        refreshWebSocketStepTitles(requestNode);
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        performanceTree.setSelectionPath(new TreePath(newNode.getPath()));
        saveConfigAction.run();
    }

    public void addSseStageNode(JTree performanceTree, NodeType type, Runnable saveConfigAction) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        DefaultMutableTreeNode requestNode = resolveSseStageParent(selectedNode);
        if (requestNode == null || !(requestNode.getUserObject() instanceof PerformanceTreeNode)) {
            return;
        }
        DefaultMutableTreeNode existingNode = findChildNode(requestNode, type);
        if (existingNode != null) {
            performanceTree.expandPath(new TreePath(requestNode.getPath()));
            performanceTree.setSelectionPath(new TreePath(existingNode.getPath()));
            return;
        }
        DefaultMutableTreeNode newNode = PerformanceTreeNodeFactory.sseStageNode(type);
        int insertIndex = requestNode.getChildCount();
        if (selectedNode != null && selectedNode.getParent() == requestNode) {
            insertIndex = requestNode.getIndex(selectedNode) + 1;
        }
        treeModel.insertNodeInto(newNode, requestNode, Math.min(insertIndex, requestNode.getChildCount()));
        refreshSseStageTitles(requestNode);
        performanceTree.expandPath(new TreePath(requestNode.getPath()));
        performanceTree.setSelectionPath(new TreePath(newNode.getPath()));
        saveConfigAction.run();
    }

    public void addLoopNode(JTree performanceTree, Runnable saveConfigAction) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        DefaultMutableTreeNode parentNode = resolveControllerInsertParent(selectedNode);
        if (parentNode == null) {
            return;
        }
        DefaultMutableTreeNode loopNode = PerformanceTreeNodeFactory.loopNode();

        int insertIndex = parentNode.getChildCount();
        if (selectedNode.getParent() == parentNode) {
            insertIndex = parentNode.getIndex(selectedNode) + 1;
        }
        DefaultMutableTreeNode requestNode = getParentWebSocketRequestNode(parentNode);
        if (parentNode == requestNode) {
            insertIndex = Math.max(1, insertIndex);
        }
        treeModel.insertNodeInto(loopNode, parentNode, Math.min(insertIndex, parentNode.getChildCount()));
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        performanceTree.setSelectionPath(new TreePath(loopNode.getPath()));
        saveConfigAction.run();
    }

    public void addSimpleNode(JTree performanceTree, Runnable saveConfigAction) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        DefaultMutableTreeNode parentNode = resolveControllerInsertParent(selectedNode);
        if (parentNode == null) {
            return;
        }
        DefaultMutableTreeNode simpleNode = PerformanceTreeNodeFactory.simpleNode();

        int insertIndex = parentNode.getChildCount();
        if (selectedNode.getParent() == parentNode) {
            insertIndex = parentNode.getIndex(selectedNode) + 1;
        }
        DefaultMutableTreeNode requestNode = getParentWebSocketRequestNode(parentNode);
        if (parentNode == requestNode) {
            insertIndex = Math.max(1, insertIndex);
        }
        treeModel.insertNodeInto(simpleNode, parentNode, Math.min(insertIndex, parentNode.getChildCount()));
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        performanceTree.setSelectionPath(new TreePath(simpleNode.getPath()));
        saveConfigAction.run();
    }

    public void addConditionNode(JTree performanceTree, Runnable saveConfigAction) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        DefaultMutableTreeNode parentNode = resolveControllerInsertParent(selectedNode);
        if (parentNode == null) {
            return;
        }
        DefaultMutableTreeNode conditionNode = PerformanceTreeNodeFactory.conditionNode();

        int insertIndex = parentNode.getChildCount();
        if (selectedNode.getParent() == parentNode) {
            insertIndex = parentNode.getIndex(selectedNode) + 1;
        }
        DefaultMutableTreeNode requestNode = getParentWebSocketRequestNode(parentNode);
        if (parentNode == requestNode) {
            insertIndex = Math.max(1, insertIndex);
        }
        treeModel.insertNodeInto(conditionNode, parentNode, Math.min(insertIndex, parentNode.getChildCount()));
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        performanceTree.setSelectionPath(new TreePath(conditionNode.getPath()));
        saveConfigAction.run();
    }

    public void addWhileNode(JTree performanceTree, Runnable saveConfigAction) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        DefaultMutableTreeNode parentNode = resolveControllerInsertParent(selectedNode);
        if (parentNode == null) {
            return;
        }
        DefaultMutableTreeNode whileNode = PerformanceTreeNodeFactory.whileNode();

        int insertIndex = parentNode.getChildCount();
        if (selectedNode.getParent() == parentNode) {
            insertIndex = parentNode.getIndex(selectedNode) + 1;
        }
        DefaultMutableTreeNode requestNode = getParentWebSocketRequestNode(parentNode);
        if (parentNode == requestNode) {
            insertIndex = Math.max(1, insertIndex);
        }
        treeModel.insertNodeInto(whileNode, parentNode, Math.min(insertIndex, parentNode.getChildCount()));
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        performanceTree.setSelectionPath(new TreePath(whileNode.getPath()));
        saveConfigAction.run();
    }

    public void addOnceOnlyNode(JTree performanceTree, Runnable saveConfigAction) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        DefaultMutableTreeNode parentNode = resolveRequestControllerInsertParent(selectedNode);
        if (parentNode == null) {
            return;
        }
        DefaultMutableTreeNode onceOnlyNode = PerformanceTreeNodeFactory.onceOnlyNode();

        int insertIndex = parentNode.getChildCount();
        if (selectedNode.getParent() == parentNode) {
            insertIndex = parentNode.getIndex(selectedNode) + 1;
        }
        treeModel.insertNodeInto(onceOnlyNode, parentNode, Math.min(insertIndex, parentNode.getChildCount()));
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        performanceTree.setSelectionPath(new TreePath(onceOnlyNode.getPath()));
        saveConfigAction.run();
    }

    public void addCsvDataSetNode(JTree performanceTree, Runnable saveConfigAction) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        DefaultMutableTreeNode parentNode = resolveCsvDataSetParent(selectedNode);
        if (parentNode == null) {
            return;
        }
        DefaultMutableTreeNode csvDataSetNode = PerformanceTreeNodeFactory.csvDataSetNode();
        int insertIndex = firstNonCsvChildIndex(parentNode);
        treeModel.insertNodeInto(csvDataSetNode, parentNode, Math.min(insertIndex, parentNode.getChildCount()));
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        performanceTree.setSelectionPath(new TreePath(csvDataSetNode.getPath()));
        saveConfigAction.run();
    }

    public void addTimerNode(JTree performanceTree, Runnable saveConfigAction) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        if (selectedNode == null) {
            return;
        }
        DefaultMutableTreeNode parentNode = selectedNode;
        int insertIndex = parentNode.getChildCount();
        DefaultMutableTreeNode wsParent = resolveWebSocketStepParent(selectedNode);
        if (wsParent != null) {
            parentNode = wsParent;
            insertIndex = selectedNode.getParent() == wsParent
                    ? wsParent.getIndex(selectedNode) + 1
                    : wsParent.getChildCount();
            if (wsParent == getParentWebSocketRequestNode(wsParent)) {
                insertIndex = Math.max(1, insertIndex);
            }
        } else if (isRequestContainerController(selectedNode)) {
            parentNode = selectedNode;
            insertIndex = parentNode.getChildCount();
        }
        DefaultMutableTreeNode timer = PerformanceTreeNodeFactory.timerNode();
        treeModel.insertNodeInto(timer, parentNode, Math.min(insertIndex, parentNode.getChildCount()));
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        performanceTree.setSelectionPath(new TreePath(timer.getPath()));
        saveConfigAction.run();
    }

    public boolean isWebSocketStepNode(NodeType type) {
        return type == NodeType.WS_SEND || type == NodeType.WS_READ || type == NodeType.WS_CLOSE;
    }

    public boolean isRequestContainerLoop(DefaultMutableTreeNode node) {
        return PerformanceTreeRules.isRequestContainerLoop(node);
    }

    public boolean isRequestContainerController(DefaultMutableTreeNode node) {
        return PerformanceTreeRules.isRequestContainerController(node);
    }

    public DefaultMutableTreeNode resolveCsvDataSetParent(DefaultMutableTreeNode selectedNode) {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return null;
        }
        if (nodeData.type == NodeType.THREAD_GROUP) {
            return selectedNode;
        }
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
        if (PerformanceTreeRules.isNodeType(parent, NodeType.THREAD_GROUP)) {
            return parent;
        }
        return null;
    }

    public DefaultMutableTreeNode resolveWebSocketConnectParent(DefaultMutableTreeNode selectedNode) {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return null;
        }
        if (nodeData.type == NodeType.REQUEST && PerformanceTreeRules.isWebSocketPerfRequest(nodeData.httpRequestItem)) {
            return selectedNode;
        }
        return getParentWebSocketRequestNode(selectedNode);
    }

    int resolveWebSocketConnectInsertIndex(DefaultMutableTreeNode requestNode,
                                           DefaultMutableTreeNode selectedNode) {
        if (requestNode == null) {
            return 0;
        }
        if (selectedNode != null
                && selectedNode.getParent() == requestNode
                && PerformanceTreeRules.isNodeType(selectedNode, NodeType.WS_CONNECT)) {
            return requestNode.getIndex(selectedNode) + 1;
        }
        int insertIndex = 0;
        while (insertIndex < requestNode.getChildCount()
                && PerformanceTreeRules.isNodeType((DefaultMutableTreeNode) requestNode.getChildAt(insertIndex), NodeType.WS_CONNECT)) {
            insertIndex++;
        }
        return insertIndex;
    }

    public DefaultMutableTreeNode resolveWebSocketStepParent(DefaultMutableTreeNode selectedNode) {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return null;
        }
        if (nodeData.type == NodeType.REQUEST && PerformanceTreeRules.isWebSocketPerfRequest(nodeData.httpRequestItem)) {
            return selectedNode;
        }
        if ((nodeData.type == NodeType.LOOP || nodeData.type == NodeType.SIMPLE || nodeData.type == NodeType.CONDITION
                || nodeData.type == NodeType.WHILE)
                && isWebSocketScenarioController(selectedNode)) {
            return selectedNode;
        }
        if (nodeData.type == NodeType.WS_CONNECT
                || nodeData.type == NodeType.TIMER
                || isWebSocketStepNode(nodeData.type)) {
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
            if (isWebSocketScenarioController(parent)) {
                return parent;
            }
            return getParentWebSocketRequestNode(selectedNode);
        }
        return null;
    }

    public DefaultMutableTreeNode resolveSseStageParent(DefaultMutableTreeNode selectedNode) {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return null;
        }
        if (nodeData.type == NodeType.REQUEST && nodeData.ssePerformanceEnabled) {
            return selectedNode;
        }
        DefaultMutableTreeNode requestNode = PerformanceTreeRules.getParentRequestNode(selectedNode);
        if (requestNode == null || !(requestNode.getUserObject() instanceof PerformanceTreeNode requestNodeData)) {
            return null;
        }
        return requestNodeData.ssePerformanceEnabled ? requestNode : null;
    }

    private DefaultMutableTreeNode resolveControllerInsertParent(DefaultMutableTreeNode selectedNode) {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return null;
        }
        if (nodeData.type == NodeType.THREAD_GROUP || isRequestContainerController(selectedNode)) {
            return selectedNode;
        }
        return resolveWebSocketStepParent(selectedNode);
    }

    private DefaultMutableTreeNode resolveRequestControllerInsertParent(DefaultMutableTreeNode selectedNode) {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return null;
        }
        if (nodeData.type == NodeType.THREAD_GROUP || isRequestContainerController(selectedNode)) {
            return selectedNode;
        }
        return null;
    }

    private int firstNonCsvChildIndex(DefaultMutableTreeNode parentNode) {
        int insertIndex = 0;
        while (insertIndex < parentNode.getChildCount()
                && PerformanceTreeRules.isNodeType((DefaultMutableTreeNode) parentNode.getChildAt(insertIndex), NodeType.CSV_DATA_SET)) {
            insertIndex++;
        }
        return insertIndex;
    }

    private boolean isWebSocketScenarioController(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return false;
        }
        return (nodeData.type == NodeType.LOOP || nodeData.type == NodeType.SIMPLE || nodeData.type == NodeType.CONDITION
                || nodeData.type == NodeType.WHILE)
                && getParentWebSocketRequestNode(node) != null;
    }

    private DefaultMutableTreeNode getParentWebSocketRequestNode(DefaultMutableTreeNode node) {
        return PerformanceTreeRules.getParentWebSocketRequestNode(node);
    }

    private DefaultMutableTreeNode findChildNode(DefaultMutableTreeNode parent, NodeType type) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object userObj = child.getUserObject();
            if (userObj instanceof PerformanceTreeNode nodeData && nodeData.type == type) {
                return child;
            }
        }
        return null;
    }

    private DefaultMutableTreeNode ensureFixedChildNode(DefaultMutableTreeNode parent, NodeType type, String name, int index) {
        DefaultMutableTreeNode existing = findChildNode(parent, type);
        if (existing != null) {
            Object userObj = existing.getUserObject();
            if (userObj instanceof PerformanceTreeNode nodeData) {
                nodeData.name = name;
            }
            if (parent.getIndex(existing) != index) {
                treeModel.removeNodeFromParent(existing);
                treeModel.insertNodeInto(existing, parent, Math.min(index, parent.getChildCount()));
            } else {
                treeModel.nodeChanged(existing);
            }
            return existing;
        }
        DefaultMutableTreeNode child = new DefaultMutableTreeNode(new PerformanceTreeNode(name, type));
        treeModel.insertNodeInto(child, parent, Math.min(index, parent.getChildCount()));
        return child;
    }

    private void moveChildrenByType(DefaultMutableTreeNode from, DefaultMutableTreeNode to, NodeType type) {
        if (from == null || to == null) {
            return;
        }
        List<DefaultMutableTreeNode> toMove = new ArrayList<>();
        for (int i = 0; i < from.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) from.getChildAt(i);
            Object userObj = child.getUserObject();
            if (userObj instanceof PerformanceTreeNode nodeData && nodeData.type == type) {
                toMove.add(child);
            }
        }
        for (DefaultMutableTreeNode child : toMove) {
            treeModel.removeNodeFromParent(child);
            treeModel.insertNodeInto(child, to, to.getChildCount());
        }
    }

    private void cleanupSseRequestStructure(DefaultMutableTreeNode requestNode, boolean removeNodes) {
        DefaultMutableTreeNode connectNode = findChildNode(requestNode, NodeType.SSE_CONNECT);
        DefaultMutableTreeNode readNode = findChildNode(requestNode, NodeType.SSE_READ);
        if (removeNodes && readNode != null) {
            moveChildrenByType(readNode, requestNode, NodeType.ASSERTION);
            moveChildrenByType(readNode, requestNode, NodeType.EXTRACTOR);
        }
        if (removeNodes && connectNode != null) {
            treeModel.removeNodeFromParent(connectNode);
        }
        if (removeNodes && readNode != null) {
            treeModel.removeNodeFromParent(readNode);
        }
    }

    private void cleanupWebSocketRequestStructure(DefaultMutableTreeNode requestNode, boolean removeNodes) {
        DefaultMutableTreeNode connectNode = findChildNode(requestNode, NodeType.WS_CONNECT);
        List<DefaultMutableTreeNode> wsStepNodes = new ArrayList<>();
        for (int i = 0; i < requestNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) requestNode.getChildAt(i);
            Object userObj = child.getUserObject();
            if (userObj instanceof PerformanceTreeNode nodeData && removeNodes && isWebSocketScenarioNode(nodeData.type)) {
                moveAssertionsFromWebSocketScenario(child, requestNode);
                wsStepNodes.add(child);
            }
        }
        if (removeNodes && connectNode != null) {
            treeModel.removeNodeFromParent(connectNode);
        }
        if (removeNodes) {
            for (DefaultMutableTreeNode wsStepNode : wsStepNodes) {
                treeModel.removeNodeFromParent(wsStepNode);
            }
        }
    }

    private boolean isWebSocketScenarioNode(NodeType type) {
        return type == NodeType.WS_SEND
                || type == NodeType.WS_READ
                || type == NodeType.WS_CLOSE
                || type == NodeType.LOOP
                || type == NodeType.SIMPLE
                || type == NodeType.CONDITION
                || type == NodeType.WHILE;
    }

    private void moveAssertionsFromWebSocketScenario(DefaultMutableTreeNode from, DefaultMutableTreeNode requestNode) {
        if (from == null || requestNode == null) {
            return;
        }
        Object userObj = from.getUserObject();
        if (userObj instanceof PerformanceTreeNode nodeData && nodeData.type == NodeType.WS_READ) {
            moveChildrenByType(from, requestNode, NodeType.ASSERTION);
            moveChildrenByType(from, requestNode, NodeType.EXTRACTOR);
        }
        for (int i = 0; i < from.getChildCount(); i++) {
            moveAssertionsFromWebSocketScenario((DefaultMutableTreeNode) from.getChildAt(i), requestNode);
        }
    }

    private void refreshWebSocketStepTitles(DefaultMutableTreeNode requestNode) {
        for (int i = 0; i < requestNode.getChildCount(); i++) {
            refreshWebSocketScenarioNodeTitle((DefaultMutableTreeNode) requestNode.getChildAt(i));
        }
    }

    private void ensureSseStageData(DefaultMutableTreeNode node) {
        if (node == null) {
            return;
        }
        Object userObj = node.getUserObject();
        if (userObj instanceof PerformanceTreeNode nodeData
                && (nodeData.type == NodeType.SSE_CONNECT || nodeData.type == NodeType.SSE_READ)
                && nodeData.ssePerformanceData == null) {
            nodeData.ssePerformanceData = new SsePerformanceData();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            ensureSseStageData((DefaultMutableTreeNode) node.getChildAt(i));
        }
    }

    private void refreshSseStageTitles(DefaultMutableTreeNode requestNode) {
        for (int i = 0; i < requestNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) requestNode.getChildAt(i);
            Object userObj = child.getUserObject();
            if (userObj instanceof PerformanceTreeNode nodeData) {
                switch (nodeData.type) {
                    case SSE_CONNECT -> nodeData.name = I18nUtil.getMessage(MessageKeys.PERFORMANCE_SSE_NODE_CONNECT);
                    case SSE_READ -> {
                        if (nodeData.ssePerformanceData == null) {
                            nodeData.ssePerformanceData = new SsePerformanceData();
                        }
                        nodeData.name = PerformanceTreeNodeTitleFormatter.sseReadTitle(nodeData.ssePerformanceData);
                    }
                    default -> {
                    }
                }
                treeModel.nodeChanged(child);
            }
        }
    }

    private void refreshWebSocketScenarioNodeTitle(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();
        if (userObj instanceof PerformanceTreeNode nodeData) {
            switch (nodeData.type) {
                case WS_CONNECT -> nodeData.name = I18nUtil.getMessage(MessageKeys.PERFORMANCE_WS_NODE_CONNECT);
                case WS_SEND ->
                        nodeData.name = PerformanceTreeNodeTitleFormatter.webSocketSendTitle(nodeData.webSocketPerformanceData);
                case WS_READ ->
                        nodeData.name = PerformanceTreeNodeTitleFormatter.webSocketReadTitle(nodeData.webSocketPerformanceData);
                case WS_CLOSE -> nodeData.name = I18nUtil.getMessage(MessageKeys.PERFORMANCE_WS_NODE_CLOSE);
                case LOOP -> nodeData.name = PerformanceTreeNodeTitleFormatter.loopTitle(nodeData.loopData);
                case CONDITION -> nodeData.name = PerformanceTreeNodeTitleFormatter.conditionTitle(nodeData.conditionData);
                case WHILE -> nodeData.name = PerformanceTreeNodeTitleFormatter.whileTitle(nodeData.whileData);
                case EXTRACTOR -> nodeData.name = PerformanceTreeNodeTitleFormatter.extractorTitle(nodeData.extractorData);
                default -> {
                }
            }
            treeModel.nodeChanged(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            refreshWebSocketScenarioNodeTitle((DefaultMutableTreeNode) node.getChildAt(i));
        }
    }
}
