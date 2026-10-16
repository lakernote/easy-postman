package com.laker.postman.panel.collections.tree.adapter;

import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.request.model.SavedResponse;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@UtilityClass
public class SwingCollectionTreeQueries {

    /**
     * 在当前 Swing 树快照里查找请求节点；领域查询不要依赖这个方法。
     */
    public DefaultMutableTreeNode findRequestNodeById(DefaultMutableTreeNode node, String requestId) {
        if (node == null) {
            return null;
        }

        var request = CollectionTreeNodes.request(node);
        if (request.isPresent()) {
            HttpRequestItem item = request.get();
            if (requestId.equals(item.getId())) {
                return node;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode result = findRequestNodeById(child, requestId);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    public HttpRequestItem findRequestContainingResponse(DefaultMutableTreeNode node, String responseId) {
        if (node == null || responseId == null || responseId.isBlank()) {
            return null;
        }
        HttpRequestItem request = CollectionTreeNodes.request(node).orElse(null);
        if (request != null && request.getResponse() != null) {
            for (SavedResponse response : request.getResponse()) {
                if (response != null && responseId.equals(response.getId())) {
                    return request;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            HttpRequestItem result = findRequestContainingResponse((DefaultMutableTreeNode) node.getChildAt(i), responseId);
            if (result != null) return result;
        }
        return null;
    }

    public List<HttpRequestItem> resolveRestorableOpenedRequests(List<HttpRequestItem> requestItems,
                                                                 DefaultMutableTreeNode rootNode) {
        if (requestItems == null || requestItems.isEmpty()) {
            return List.of();
        }

        List<HttpRequestItem> restorableItems = new ArrayList<>();
        for (HttpRequestItem item : requestItems) {
            // 恢复打开标签前先以当前树为准，避免已经删除的请求又被打开。
            if (item == null || item.getId() == null || item.getId().isEmpty()) {
                log.warn("Skip restoring request as it is null or has null/empty ID: {}",
                        item != null ? item.getName() : "null");
                continue;
            }

            HttpRequestItem resolvedItem = item;
            if (!item.isNewRequest()) {
                DefaultMutableTreeNode node = findRequestNodeById(rootNode, item.getId());
                if (node == null) {
                    log.warn("Skip restoring request {} (id={}) as it no longer exists in the tree",
                            item.getName(), item.getId());
                    continue;
                }
                resolvedItem = CollectionTreeNodes.request(node).orElse(null);
                if (resolvedItem == null) {
                    log.warn("Skip restoring request {} (id={}) as the tree node payload is invalid",
                            item.getName(), item.getId());
                    continue;
                }
            }
            restorableItems.add(resolvedItem);
        }
        return restorableItems;
    }
}
