package com.laker.postman.panel.collections.tree.adapter;

import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.service.collections.CollectionTreeNodes;
import lombok.experimental.UtilityClass;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@UtilityClass
public class SwingSavedResponseTreeMutation {

    /**
     * 同步更新编辑器请求对象、树节点请求对象，以及树下的保存响应子节点。
     */
    public Optional<Result> appendSavedResponse(DefaultMutableTreeNode rootTreeNode,
                                                HttpRequestItem editorRequestItem,
                                                SavedResponse savedResponse) {
        if (editorRequestItem == null || editorRequestItem.getId() == null || editorRequestItem.getId().isEmpty()
                || savedResponse == null) {
            return Optional.empty();
        }

        DefaultMutableTreeNode requestNode = SwingCollectionTreeQueries.findRequestNodeById(
                rootTreeNode,
                editorRequestItem.getId()
        );
        if (requestNode == null) {
            return Optional.empty();
        }

        HttpRequestItem treeRequestItem = CollectionTreeNodes.request(requestNode).orElse(null);
        if (treeRequestItem == null) {
            return Optional.empty();
        }

        appendResponse(treeRequestItem, savedResponse);
        if (treeRequestItem != editorRequestItem) {
            appendResponse(editorRequestItem, savedResponse);
        }
        requestNode.add(CollectionTreeNodes.savedResponseNode(savedResponse));
        return Optional.of(new Result(requestNode, treeRequestItem));
    }

    private void appendResponse(HttpRequestItem item, SavedResponse savedResponse) {
        List<SavedResponse> responses = item.getResponse();
        if (responses == null) {
            responses = new ArrayList<>();
            item.setResponse(responses);
        }
        responses.add(savedResponse);
    }

    public Optional<Result> upsertSavedResponse(DefaultMutableTreeNode rootTreeNode,
                                                String requestId,
                                                SavedResponse savedResponse) {
        if (requestId == null || requestId.isBlank() || savedResponse == null
                || savedResponse.getId() == null || savedResponse.getId().isBlank()) {
            return Optional.empty();
        }
        DefaultMutableTreeNode requestNode = SwingCollectionTreeQueries.findRequestNodeById(rootTreeNode, requestId);
        HttpRequestItem request = CollectionTreeNodes.request(requestNode).orElse(null);
        if (request == null) return Optional.empty();

        List<SavedResponse> responses = request.getResponse();
        if (responses == null) {
            responses = new ArrayList<>();
            request.setResponse(responses);
        }
        int responseIndex = -1;
        for (int i = 0; i < responses.size(); i++) {
            if (Objects.equals(responses.get(i).getId(), savedResponse.getId())) {
                responseIndex = i;
                break;
            }
        }
        if (responseIndex >= 0) responses.set(responseIndex, savedResponse);
        else responses.add(savedResponse);

        DefaultMutableTreeNode responseNode = findResponseNode(requestNode, savedResponse.getId());
        if (responseNode == null) requestNode.add(CollectionTreeNodes.savedResponseNode(savedResponse));
        else CollectionTreeNodes.setSavedResponse(responseNode, savedResponse);
        return Optional.of(new Result(requestNode, request));
    }

    public Optional<Result> removeSavedResponse(DefaultMutableTreeNode rootTreeNode,
                                                String requestId,
                                                String responseId) {
        if (requestId == null || requestId.isBlank() || responseId == null || responseId.isBlank()) {
            return Optional.empty();
        }
        DefaultMutableTreeNode requestNode = SwingCollectionTreeQueries.findRequestNodeById(rootTreeNode, requestId);
        HttpRequestItem request = CollectionTreeNodes.request(requestNode).orElse(null);
        if (request == null) return Optional.empty();

        boolean removed = request.getResponse() != null
                && request.getResponse().removeIf(item -> item != null && responseId.equals(item.getId()));
        DefaultMutableTreeNode responseNode = findResponseNode(requestNode, responseId);
        if (responseNode != null) requestNode.remove(responseNode);
        return removed || responseNode != null ? Optional.of(new Result(requestNode, request)) : Optional.empty();
    }

    private DefaultMutableTreeNode findResponseNode(DefaultMutableTreeNode requestNode, String responseId) {
        for (int i = 0; i < requestNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) requestNode.getChildAt(i);
            SavedResponse response = CollectionTreeNodes.savedResponse(child).orElse(null);
            if (response != null && Objects.equals(response.getId(), responseId)) return child;
        }
        return null;
    }

    public record Result(DefaultMutableTreeNode requestNode, HttpRequestItem treeRequestItem) {
    }
}
