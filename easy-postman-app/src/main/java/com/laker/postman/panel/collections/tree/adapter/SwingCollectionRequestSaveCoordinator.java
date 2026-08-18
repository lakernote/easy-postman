package com.laker.postman.panel.collections.tree.adapter;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.service.collections.CollectionTreeNodes;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Objects;
import java.util.Optional;

/**
 * Swing 集合树的保存协调器。
 * 核心点：这里允许操作 DefaultMutableTreeNode，但只作为 Swing 适配层使用；
 * 真正无 UI 的集合读写继续走 CollectionDocument，避免领域层再次依赖 Swing。
 */
public final class SwingCollectionRequestSaveCoordinator {
    private final DefaultMutableTreeNode rootTreeNode;
    private final Runnable persistAction;

    public SwingCollectionRequestSaveCoordinator(DefaultMutableTreeNode rootTreeNode, Runnable persistAction) {
        this.rootTreeNode = rootTreeNode;
        this.persistAction = persistAction == null ? () -> {
        } : persistAction;
    }

    public Optional<RequestSaveResult> addRequestToGroup(RequestGroup targetGroup, HttpRequestItem item) {
        if (targetGroup == null || item == null) {
            return Optional.empty();
        }

        DefaultMutableTreeNode groupNode = findGroupNode(rootTreeNode, targetGroup);
        if (groupNode == null) {
            return Optional.empty();
        }

        DefaultMutableTreeNode requestNode = CollectionTreeNodes.requestNode(item);
        groupNode.add(requestNode);
        persist();
        return Optional.of(new RequestSaveResult(groupNode, requestNode, item));
    }

    public Optional<RequestSaveResult> updateExistingRequest(HttpRequestItem item) {
        Optional<SwingCollectionRequestMutation.Result> mutation = SwingCollectionRequestMutation
                .updateExistingRequest(rootTreeNode, item);
        if (mutation.isEmpty()) {
            return Optional.empty();
        }

        SwingCollectionRequestMutation.Result result = mutation.get();
        persist();
        return Optional.of(new RequestSaveResult(null, result.requestNode(), result.updatedItem()));
    }

    public Optional<SavedResponseSaveResult> appendSavedResponse(HttpRequestItem requestItem,
                                                                 SavedResponse savedResponse) {
        Optional<SwingSavedResponseTreeMutation.Result> mutation = SwingSavedResponseTreeMutation
                .appendSavedResponse(rootTreeNode, requestItem, savedResponse);
        if (mutation.isEmpty()) {
            return Optional.empty();
        }

        persist();
        SwingSavedResponseTreeMutation.Result result = mutation.get();
        return Optional.of(new SavedResponseSaveResult(
                result.requestNode(),
                result.treeRequestItem(),
                savedResponse
        ));
    }

    public Optional<SavedResponseSaveResult> upsertSavedResponse(String requestId, SavedResponse savedResponse) {
        Optional<SwingSavedResponseTreeMutation.Result> mutation = SwingSavedResponseTreeMutation
                .upsertSavedResponse(rootTreeNode, requestId, savedResponse);
        if (mutation.isEmpty()) return Optional.empty();
        persist();
        SwingSavedResponseTreeMutation.Result result = mutation.get();
        return Optional.of(new SavedResponseSaveResult(result.requestNode(), result.treeRequestItem(), savedResponse));
    }

    public Optional<RequestSaveResult> removeSavedResponse(String requestId, String responseId) {
        Optional<SwingSavedResponseTreeMutation.Result> mutation = SwingSavedResponseTreeMutation
                .removeSavedResponse(rootTreeNode, requestId, responseId);
        if (mutation.isEmpty()) return Optional.empty();
        persist();
        SwingSavedResponseTreeMutation.Result result = mutation.get();
        return Optional.of(new RequestSaveResult(null, result.requestNode(), result.treeRequestItem()));
    }

    public DefaultMutableTreeNode findGroupNode(DefaultMutableTreeNode node, RequestGroup targetGroup) {
        if (node == null || targetGroup == null) {
            return null;
        }
        RequestGroup currentGroup = CollectionTreeNodes.group(node).orElse(null);
        if (currentGroup != null && sameGroup(currentGroup, targetGroup)) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode result = findGroupNode(child, targetGroup);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private boolean sameGroup(RequestGroup currentGroup, RequestGroup targetGroup) {
        if (currentGroup.getId() != null && !currentGroup.getId().isBlank()) {
            return currentGroup.getId().equals(targetGroup.getId());
        }
        return currentGroup == targetGroup || Objects.equals(currentGroup.getName(), targetGroup.getName());
    }

    private void persist() {
        persistAction.run();
    }

    public record RequestSaveResult(DefaultMutableTreeNode groupNode,
                                    DefaultMutableTreeNode requestNode,
                                    HttpRequestItem requestItem) {
    }

    public record SavedResponseSaveResult(DefaultMutableTreeNode requestNode,
                                          HttpRequestItem treeRequestItem,
                                          SavedResponse savedResponse) {
    }
}
