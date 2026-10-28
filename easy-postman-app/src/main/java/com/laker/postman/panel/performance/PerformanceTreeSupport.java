package com.laker.postman.panel.performance;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceProtocol;


import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.panel.performance.tree.PerformanceRequestNodeStateSynchronizer;
import com.laker.postman.panel.performance.tree.PerformanceTreeClipboardSupport;
import com.laker.postman.panel.performance.tree.PerformanceTreeNodeFactory;
import com.laker.postman.panel.performance.tree.PerformanceTreeStructureSupport;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class PerformanceTreeSupport {

    private final PerformanceTreeStructureSupport structureSupport;
    private final PerformanceTreeClipboardSupport clipboardSupport;

    PerformanceTreeSupport(DefaultTreeModel treeModel) {
        this.structureSupport = new PerformanceTreeStructureSupport(treeModel);
        this.clipboardSupport = new PerformanceTreeClipboardSupport(treeModel, structureSupport);
    }

    RequestItemProtocolEnum resolveRequestProtocol(HttpRequestItem item) {
        return PerformanceTreeRules.resolveRequestProtocol(item);
    }

    boolean isWebSocketPerfRequest(HttpRequestItem item) {
        return PerformanceTreeRules.isWebSocketPerfRequest(item);
    }

    PerformanceProtocol resolvePerformanceProtocol(PerformanceTreeNode nodeData) {
        if (nodeData == null) {
            return PerformanceProtocol.HTTP;
        }
        if (isWebSocketPerfRequest(nodeData.httpRequestItem)) {
            return PerformanceProtocol.WEBSOCKET;
        }
        if (PerformanceTreeRules.isSsePerfRequest(nodeData)) {
            return PerformanceProtocol.SSE;
        }
        return PerformanceProtocol.HTTP;
    }

    boolean canToggleSsePerformanceMode(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof PerformanceTreeNode nodeData)
                || nodeData.type != NodeType.REQUEST || nodeData.httpRequestItem == null) {
            return false;
        }
        return !isWebSocketPerfRequest(nodeData.httpRequestItem);
    }

    void setSsePerformanceMode(DefaultMutableTreeNode requestNode, boolean enabled) {
        if (!canToggleSsePerformanceMode(requestNode)
                || !(requestNode.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return;
        }
        nodeData.ssePerformanceEnabled = enabled;
        PerformanceRequestNodeStateSynchronizer.syncSnapshotFromCurrentItem(nodeData);
        structureSupport.setSsePerformanceEnabled(requestNode, nodeData, enabled);
    }

    Set<PerformanceProtocol> collectAvailableProtocols(DefaultMutableTreeNode root) {
        EnumSet<PerformanceProtocol> protocols = EnumSet.noneOf(PerformanceProtocol.class);
        collectAvailableProtocols(root, protocols);
        if (protocols.isEmpty()) {
            protocols.add(PerformanceProtocol.HTTP);
        }
        return protocols;
    }

    private void collectAvailableProtocols(DefaultMutableTreeNode node, EnumSet<PerformanceProtocol> protocols) {
        if (node == null) {
            return;
        }
        Object userObject = node.getUserObject();
        if (userObject instanceof PerformanceTreeNode nodeData) {
            if (!nodeData.enabled) {
                return;
            }
            collectNodeProtocol(nodeData, protocols);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectAvailableProtocols((DefaultMutableTreeNode) node.getChildAt(i), protocols);
        }
    }

    private void collectNodeProtocol(PerformanceTreeNode nodeData, EnumSet<PerformanceProtocol> protocols) {
        switch (nodeData.type) {
            case REQUEST -> {
                protocols.add(resolvePerformanceProtocol(nodeData));
            }
            case WS_CONNECT, WS_SEND, WS_READ, WS_CLOSE -> protocols.add(PerformanceProtocol.WEBSOCKET);
            case SSE_CONNECT, SSE_READ -> protocols.add(PerformanceProtocol.SSE);
            default -> {
            }
        }
    }

    DefaultMutableTreeNode getParentRequestNode(DefaultMutableTreeNode node) {
        return PerformanceTreeRules.getParentRequestNode(node);
    }

    void syncRequestStructure(DefaultMutableTreeNode requestNode, PerformanceTreeNode requestData) {
        structureSupport.syncRequestStructure(requestNode, requestData);
    }

    void ensureRequestStructure(DefaultMutableTreeNode requestNode, PerformanceTreeNode requestData) {
        structureSupport.ensureRequestStructure(requestNode, requestData);
    }

    void syncAllRequestStructures(DefaultMutableTreeNode node) {
        structureSupport.syncAllRequestStructures(node);
    }

    void addWebSocketStepNode(JTree performanceTree, NodeType type, Runnable saveConfigAction) {
        structureSupport.addWebSocketStepNode(performanceTree, type, saveConfigAction);
    }

    void addSseStageNode(JTree performanceTree, NodeType type, Runnable saveConfigAction) {
        structureSupport.addSseStageNode(performanceTree, type, saveConfigAction);
    }

    void addLoopNode(JTree performanceTree, Runnable saveConfigAction) {
        structureSupport.addLoopNode(performanceTree, saveConfigAction);
    }

    void addSimpleNode(JTree performanceTree, Runnable saveConfigAction) {
        structureSupport.addSimpleNode(performanceTree, saveConfigAction);
    }

    void addConditionNode(JTree performanceTree, Runnable saveConfigAction) {
        structureSupport.addConditionNode(performanceTree, saveConfigAction);
    }

    void addWhileNode(JTree performanceTree, Runnable saveConfigAction) {
        structureSupport.addWhileNode(performanceTree, saveConfigAction);
    }

    void addOnceOnlyNode(JTree performanceTree, Runnable saveConfigAction) {
        structureSupport.addOnceOnlyNode(performanceTree, saveConfigAction);
    }

    void addCsvDataSetNode(JTree performanceTree, Runnable saveConfigAction) {
        structureSupport.addCsvDataSetNode(performanceTree, saveConfigAction);
    }

    void addTimerNode(JTree performanceTree, Runnable saveConfigAction) {
        structureSupport.addTimerNode(performanceTree, saveConfigAction);
    }

    boolean isWebSocketStepNode(NodeType type) {
        return structureSupport.isWebSocketStepNode(type);
    }

    boolean isRequestContainerLoop(DefaultMutableTreeNode node) {
        return structureSupport.isRequestContainerLoop(node);
    }

    boolean isRequestContainerController(DefaultMutableTreeNode node) {
        return structureSupport.isRequestContainerController(node);
    }

    boolean hasCopyableNodes(TreePath[] selectedPaths) {
        return clipboardSupport.hasCopyableNodes(selectedPaths);
    }

    boolean hasDeletableNodes(TreePath[] selectedPaths) {
        return clipboardSupport.hasDeletableNodes(selectedPaths);
    }

    int deletableNodeCount(TreePath[] selectedPaths) {
        return clipboardSupport.deletableNodeCount(selectedPaths);
    }

    List<DefaultMutableTreeNode> copyNodes(TreePath[] selectedPaths) {
        return clipboardSupport.copyNodes(selectedPaths);
    }

    List<DefaultMutableTreeNode> deleteNodes(TreePath[] selectedPaths) {
        return clipboardSupport.deleteNodes(selectedPaths);
    }

    boolean canPasteNodes(DefaultMutableTreeNode targetNode, List<DefaultMutableTreeNode> copiedNodes) {
        return clipboardSupport.canPasteNodes(targetNode, copiedNodes);
    }

    List<DefaultMutableTreeNode> pasteNodes(JTree performanceTree,
                                            DefaultMutableTreeNode targetNode,
                                            List<DefaultMutableTreeNode> copiedNodes) {
        return clipboardSupport.pasteNodes(performanceTree, targetNode, copiedNodes);
    }

    DefaultMutableTreeNode resolveWebSocketConnectParent(DefaultMutableTreeNode selectedNode) {
        return structureSupport.resolveWebSocketConnectParent(selectedNode);
    }

    DefaultMutableTreeNode resolveCsvDataSetParent(DefaultMutableTreeNode selectedNode) {
        return structureSupport.resolveCsvDataSetParent(selectedNode);
    }

    DefaultMutableTreeNode resolveWebSocketStepParent(DefaultMutableTreeNode selectedNode) {
        return structureSupport.resolveWebSocketStepParent(selectedNode);
    }

    DefaultMutableTreeNode resolveSseStageParent(DefaultMutableTreeNode selectedNode) {
        return structureSupport.resolveSseStageParent(selectedNode);
    }

    static void createDefaultRequest(DefaultMutableTreeNode root) {
        PerformanceTreeNodeFactory.addDefaultRequest(root);
    }
}
