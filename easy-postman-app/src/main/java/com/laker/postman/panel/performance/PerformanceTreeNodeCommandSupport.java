package com.laker.postman.panel.performance;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.model.NodeType;


import com.laker.postman.common.component.dialog.TextInputDialog;
import com.laker.postman.panel.collections.RequestSelectionDialogSupport;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.panel.performance.tree.PerformanceRequestNodeStateSynchronizer;
import com.laker.postman.panel.performance.tree.PerformanceTreeNodeFactory;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import lombok.RequiredArgsConstructor;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.CardLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

@RequiredArgsConstructor
final class PerformanceTreeNodeCommandSupport {

    private final Component parentComponent;
    private final JTree performanceTree;
    private final DefaultTreeModel treeModel;
    private final CardLayout propertyCardLayout;
    private final JPanel propertyPanel;
    private final PerformanceTreeSupport treeSupport;
    private final Runnable persistLastSelectionAction;
    private final Consumer<HttpRequestItem> switchRequestEditorAction;
    private final Supplier<DefaultMutableTreeNode> currentRequestNodeSupplier;
    private final Consumer<DefaultMutableTreeNode> currentRequestNodeSetter;
    private final Runnable saveConfigAction;
    private final String requestCard;

    private List<DefaultMutableTreeNode> copiedNodes = List.of();
    private IntPredicate deleteConfirmationAction = this::confirmDelete;

    List<DefaultMutableTreeNode> copiedNodes() {
        return copiedNodes;
    }

    void setDeleteConfirmationAction(IntPredicate deleteConfirmationAction) {
        this.deleteConfirmationAction = deleteConfirmationAction == null
                ? this::confirmDelete
                : deleteConfirmationAction;
    }

    void addThreadGroupNode() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        DefaultMutableTreeNode group = new DefaultMutableTreeNode(new PerformanceTreeNode("Thread Group", NodeType.THREAD_GROUP));
        treeModel.insertNodeInto(group, root, root.getChildCount());
        performanceTree.expandPath(new TreePath(root.getPath()));
        saveConfigAction.run();
    }

    void addRequestNodes() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        if (node == null) {
            return;
        }
        Object userObj = node.getUserObject();
        if (!(userObj instanceof PerformanceTreeNode nodeData)
                || (nodeData.type != NodeType.THREAD_GROUP && !treeSupport.isRequestContainerController(node))) {
            JOptionPane.showMessageDialog(
                    parentComponent,
                    I18nUtil.getMessage(MessageKeys.PERFORMANCE_MSG_SELECT_THREAD_GROUP),
                    I18nUtil.getMessage(MessageKeys.GENERAL_INFO),
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        RequestSelectionDialogSupport.showMultiSelectRequestDialog(selectedList -> addSelectedRequests(node, selectedList));
    }

    void addAssertionNode() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        if (node == null) {
            return;
        }
        DefaultMutableTreeNode parentNode = node;
        Object userObj = node.getUserObject();
        if (userObj instanceof PerformanceTreeNode nodeData
                && (nodeData.type == NodeType.SSE_READ || nodeData.type == NodeType.WS_READ)) {
            parentNode = node;
        }
        DefaultMutableTreeNode assertion = new DefaultMutableTreeNode(new PerformanceTreeNode("Assertion", NodeType.ASSERTION));
        treeModel.insertNodeInto(assertion, parentNode, parentNode.getChildCount());
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        saveConfigAction.run();
    }

    void addExtractorNode() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
        if (node == null) {
            return;
        }
        DefaultMutableTreeNode extractor = PerformanceTreeNodeFactory.extractorNode();
        treeModel.insertNodeInto(extractor, node, node.getChildCount());
        performanceTree.expandPath(new TreePath(node.getPath()));
        performanceTree.setSelectionPath(new TreePath(extractor.getPath()));
        saveConfigAction.run();
    }

    Action createRenameAction() {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
                if (node == null) {
                    return;
                }
                Object userObj = node.getUserObject();
                if (!(userObj instanceof PerformanceTreeNode nodeData) || !commandPolicy().canRename(node)) {
                    return;
                }
                TextInputDialog.showRequiredName(
                        parentComponent,
                        I18nUtil.getMessage(MessageKeys.PERFORMANCE_MENU_RENAME),
                        nodeData.name,
                        I18nUtil.getMessage(MessageKeys.PERFORMANCE_NODE_NAME_EMPTY)
                ).ifPresent(newName -> {
                    nodeData.name = newName;
                    if (nodeData.type == NodeType.REQUEST && nodeData.httpRequestItem != null) {
                        nodeData.httpRequestItem.setName(newName);
                        switchRequestEditorAction.accept(nodeData.httpRequestItem);
                    }
                    treeModel.nodeChanged(node);
                    saveConfigAction.run();
                });
            }
        };
    }

    Action createDeleteAction() {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                TreePath[] selectedPaths = performanceTree.getSelectionPaths();
                if (selectedPaths == null || selectedPaths.length == 0) {
                    return;
                }
                int deletableCount = treeSupport.deletableNodeCount(selectedPaths);
                if (deletableCount == 0 || !deleteConfirmationAction.test(deletableCount)) {
                    return;
                }

                DefaultMutableTreeNode currentRequestNode = currentRequestNodeSupplier.get();
                List<DefaultMutableTreeNode> deletedNodes = treeSupport.deleteNodes(selectedPaths);
                if (deletedNodes.isEmpty()) {
                    return;
                }

                if (currentRequestNode != null && deletedNodes.stream()
                        .anyMatch(node -> node == currentRequestNode || node.isNodeDescendant(currentRequestNode))) {
                    currentRequestNodeSetter.accept(null);
                }
                saveConfigAction.run();
            }
        };
    }

    private boolean confirmDelete(int count) {
        String message = count == 1
                ? I18nUtil.getMessage(MessageKeys.PERFORMANCE_DELETE_CONFIRM)
                : I18nUtil.getMessage(MessageKeys.PERFORMANCE_DELETE_BATCH_CONFIRM, count);
        int result = JOptionPane.showConfirmDialog(
                parentComponent,
                message,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_DELETE_CONFIRM_TITLE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return result == JOptionPane.YES_OPTION;
    }

    Action createCopyAction() {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                TreePath[] selectedPaths = performanceTree.getSelectionPaths();
                if (selectedPaths == null || selectedPaths.length == 0) {
                    return;
                }
                persistLastSelectionAction.run();
                List<DefaultMutableTreeNode> newCopiedNodes = treeSupport.copyNodes(selectedPaths);
                if (!newCopiedNodes.isEmpty()) {
                    copiedNodes = newCopiedNodes;
                }
            }
        };
    }

    Action createPasteAction() {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                DefaultMutableTreeNode targetNode = (DefaultMutableTreeNode) performanceTree.getLastSelectedPathComponent();
                if (targetNode == null || !treeSupport.canPasteNodes(targetNode, copiedNodes)) {
                    return;
                }
                persistLastSelectionAction.run();
                List<DefaultMutableTreeNode> pastedNodes = treeSupport.pasteNodes(performanceTree, targetNode, copiedNodes);
                if (!pastedNodes.isEmpty()) {
                    saveConfigAction.run();
                }
            }
        };
    }

    void setSelectedNodesEnabled(boolean enabled) {
        TreePath[] selectedPaths = performanceTree.getSelectionPaths();
        if (selectedPaths == null || selectedPaths.length == 0) {
            return;
        }
        boolean changed = false;
        PerformanceTreeCommandPolicy policy = commandPolicy();
        for (TreePath path : selectedPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObj = node.getUserObject();
            if (userObj instanceof PerformanceTreeNode nodeData && policy.canSetEnabled(node, enabled)) {
                nodeData.enabled = enabled;
                treeModel.nodeChanged(node);
                changed = true;
            }
        }
        if (changed) {
            saveConfigAction.run();
        }
    }

    private void addSelectedRequests(DefaultMutableTreeNode parentNode, List<HttpRequestItem> selectedList) {
        if (selectedList == null || selectedList.isEmpty()) {
            return;
        }

        List<HttpRequestItem> supportedList = selectedList.stream()
                .filter(reqItem -> {
                    RequestItemProtocolEnum protocol = treeSupport.resolveRequestProtocol(reqItem);
                    return protocol.isHttpProtocol() || protocol.isWebSocketProtocol();
                })
                .toList();

        if (supportedList.isEmpty()) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.MSG_ONLY_HTTP_SSE_WS_SUPPORTED));
            return;
        }

        List<DefaultMutableTreeNode> newNodes = new ArrayList<>();
        for (HttpRequestItem reqItem : supportedList) {
            PerformanceTreeNode requestData = new PerformanceTreeNode(reqItem.getName(), NodeType.REQUEST);
            PerformanceRequestNodeStateSynchronizer.replaceRequestItem(requestData, reqItem);
            DefaultMutableTreeNode req = new DefaultMutableTreeNode(requestData);
            treeModel.insertNodeInto(req, parentNode, parentNode.getChildCount());
            treeSupport.ensureRequestStructure(req, (PerformanceTreeNode) req.getUserObject());
            newNodes.add(req);
        }
        performanceTree.expandPath(new TreePath(parentNode.getPath()));
        TreePath newPath = new TreePath(newNodes.get(0).getPath());
        performanceTree.setSelectionPath(newPath);
        propertyCardLayout.show(propertyPanel, requestCard);
        PerformanceTreeNode newRequestNode = (PerformanceTreeNode) newNodes.get(0).getUserObject();
        switchRequestEditorAction.accept(newRequestNode.httpRequestItem);
        saveConfigAction.run();
    }

    private PerformanceTreeCommandPolicy commandPolicy() {
        return new PerformanceTreeCommandPolicy(treeSupport);
    }
}
