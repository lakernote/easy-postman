package com.laker.postman.panel.performance;

import com.laker.postman.performance.core.model.NodeType;


import com.laker.postman.performance.model.PerformanceTreeNode;
import lombok.RequiredArgsConstructor;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.EnumSet;
import java.util.List;

@RequiredArgsConstructor
final class PerformanceTreeCommandPolicy {

    private final PerformanceTreeSupport treeSupport;

    EnumSet<PerformanceTreeCommand> commandsForSingleSelection(DefaultMutableTreeNode node,
                                                             List<DefaultMutableTreeNode> copiedNodes) {
        EnumSet<PerformanceTreeCommand> commands = EnumSet.noneOf(PerformanceTreeCommand.class);
        PerformanceTreeNode nodeData = treeNodeData(node);
        if (nodeData == null) {
            return commands;
        }

        if (nodeData.type == NodeType.ROOT) {
            commands.add(PerformanceTreeCommand.ADD_THREAD_GROUP);
            addPasteCommand(commands, node, copiedNodes);
            return commands;
        }

        boolean requestContainerController = treeSupport.isRequestContainerController(node);
        boolean canAddCsvDataSet = treeSupport.resolveCsvDataSetParent(node) != null;
        boolean canManageSseStages = treeSupport.resolveSseStageParent(node) != null;
        boolean canManageWsConnect = treeSupport.resolveWebSocketConnectParent(node) != null;
        boolean canManageWsSteps = treeSupport.resolveWebSocketStepParent(node) != null;

        if (canAddCsvDataSet) {
            commands.add(PerformanceTreeCommand.ADD_CSV_DATA_SET);
        }
        if (nodeData.type == NodeType.THREAD_GROUP || requestContainerController) {
            commands.add(PerformanceTreeCommand.ADD_REQUEST);
            commands.add(PerformanceTreeCommand.ADD_ONCE_ONLY);
        }
        if (nodeData.type == NodeType.THREAD_GROUP || requestContainerController || canManageWsSteps) {
            commands.add(PerformanceTreeCommand.ADD_LOOP);
            commands.add(PerformanceTreeCommand.ADD_SIMPLE);
            commands.add(PerformanceTreeCommand.ADD_CONDITION);
            commands.add(PerformanceTreeCommand.ADD_WHILE);
        }
        if (canManageSseStages) {
            commands.add(PerformanceTreeCommand.ADD_SSE_CONNECT);
            commands.add(PerformanceTreeCommand.ADD_SSE_READ);
        }
        if (treeSupport.canToggleSsePerformanceMode(node)) {
            commands.add(PerformanceTreeCommand.TOGGLE_SSE_MODE);
        }
        if (canManageWsConnect) {
            commands.add(PerformanceTreeCommand.ADD_WS_CONNECT);
        }
        if (canManageWsSteps) {
            commands.add(PerformanceTreeCommand.ADD_WS_SEND);
            commands.add(PerformanceTreeCommand.ADD_WS_READ);
            commands.add(PerformanceTreeCommand.ADD_WS_CLOSE);
        }
        if (canAddAssertion(nodeData)) {
            commands.add(PerformanceTreeCommand.ADD_ASSERTION);
        }
        if (canAddExtractor(nodeData)) {
            commands.add(PerformanceTreeCommand.ADD_EXTRACTOR);
        }
        if (nodeData.type == NodeType.REQUEST || requestContainerController || canManageWsSteps) {
            commands.add(PerformanceTreeCommand.ADD_TIMER);
        }
        if (treeSupport.hasCopyableNodes(singlePath(node))) {
            commands.add(PerformanceTreeCommand.COPY);
        }
        addPasteCommand(commands, node, copiedNodes);
        if (canRename(node)) {
            commands.add(PerformanceTreeCommand.RENAME);
        }
        if (treeSupport.hasDeletableNodes(singlePath(node))) {
            commands.add(PerformanceTreeCommand.DELETE);
        }
        if (canSetEnabled(node, true)) {
            commands.add(PerformanceTreeCommand.ENABLE);
        }
        if (canSetEnabled(node, false)) {
            commands.add(PerformanceTreeCommand.DISABLE);
        }
        return commands;
    }

    EnumSet<PerformanceTreeCommand> commandsForMultiSelection(TreePath[] selectedPaths) {
        EnumSet<PerformanceTreeCommand> commands = EnumSet.noneOf(PerformanceTreeCommand.class);
        if (treeSupport.hasCopyableNodes(selectedPaths)) {
            commands.add(PerformanceTreeCommand.COPY);
        }
        if (treeSupport.hasDeletableNodes(selectedPaths)) {
            commands.add(PerformanceTreeCommand.DELETE);
        }

        boolean hasDisabled = false;
        boolean hasEnabled = false;
        if (selectedPaths != null) {
            for (TreePath path : selectedPaths) {
                if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)) {
                    continue;
                }
                PerformanceTreeNode nodeData = treeNodeData(node);
                if (nodeData == null || nodeData.type == NodeType.ROOT) {
                    continue;
                }
                if (nodeData.enabled) {
                    hasEnabled = true;
                } else {
                    hasDisabled = true;
                }
            }
        }
        if (hasDisabled) {
            commands.add(PerformanceTreeCommand.ENABLE);
        }
        if (hasEnabled) {
            commands.add(PerformanceTreeCommand.DISABLE);
        }
        return commands;
    }

    boolean canRename(DefaultMutableTreeNode node) {
        PerformanceTreeNode nodeData = treeNodeData(node);
        return nodeData != null
                && nodeData.type != NodeType.ROOT
                && !isFixedNameNode(nodeData.type)
                && nodeData.type != NodeType.LOOP
                && nodeData.type != NodeType.CONDITION
                && nodeData.type != NodeType.WHILE
                && nodeData.type != NodeType.ONCE_ONLY
                && nodeData.type != NodeType.EXTRACTOR
                && !treeSupport.isWebSocketStepNode(nodeData.type);
    }

    boolean canSetEnabled(DefaultMutableTreeNode node, boolean enabled) {
        PerformanceTreeNode nodeData = treeNodeData(node);
        return nodeData != null
                && nodeData.type != NodeType.ROOT
                && nodeData.enabled != enabled;
    }

    private void addPasteCommand(EnumSet<PerformanceTreeCommand> commands,
                                DefaultMutableTreeNode node,
                                List<DefaultMutableTreeNode> copiedNodes) {
        if (treeSupport.canPasteNodes(node, copiedNodes)) {
            commands.add(PerformanceTreeCommand.PASTE);
        }
    }

    private boolean canAddAssertion(PerformanceTreeNode nodeData) {
        return canAddRequestPostProcessor(nodeData);
    }

    private boolean canAddExtractor(PerformanceTreeNode nodeData) {
        return canAddRequestPostProcessor(nodeData);
    }

    private boolean canAddRequestPostProcessor(PerformanceTreeNode nodeData) {
        boolean sseRequestNode = nodeData.type == NodeType.REQUEST && nodeData.ssePerformanceEnabled;
        boolean webSocketRequestNode = nodeData.type == NodeType.REQUEST && treeSupport.isWebSocketPerfRequest(nodeData.httpRequestItem);
        return (nodeData.type == NodeType.REQUEST && !sseRequestNode && !webSocketRequestNode)
                || nodeData.type == NodeType.SSE_READ
                || nodeData.type == NodeType.WS_READ;
    }

    private boolean isFixedNameNode(NodeType type) {
        return type == NodeType.SSE_CONNECT
                || type == NodeType.SSE_READ
                || type == NodeType.WS_CONNECT;
    }

    private TreePath[] singlePath(DefaultMutableTreeNode node) {
        return new TreePath[]{new TreePath(node.getPath())};
    }

    private PerformanceTreeNode treeNodeData(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return null;
        }
        return nodeData;
    }
}
