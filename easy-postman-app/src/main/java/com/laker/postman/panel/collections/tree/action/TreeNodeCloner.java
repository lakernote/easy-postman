package com.laker.postman.panel.collections.tree.action;

import com.laker.postman.collection.CollectionIdNormalizer;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.util.JsonUtil;
import lombok.experimental.UtilityClass;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.UUID;

/**
 * 树节点克隆工具类
 */
@UtilityClass
public class TreeNodeCloner {

    /**
     * 深拷贝分组节点及其所有子节点
     */
    public static DefaultMutableTreeNode deepCopyGroupNode(DefaultMutableTreeNode node) {
        RequestGroup originalGroup = CollectionTreeNodes.group(node)
                .orElseThrow(() -> new IllegalArgumentException("Expected group node: " + node));
        RequestGroup copiedGroup = JsonUtil.deepCopy(originalGroup, RequestGroup.class);
        copiedGroup.setId(UUID.randomUUID().toString());
        DefaultMutableTreeNode copy = CollectionTreeNodes.groupNode(copiedGroup);

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            copy.add(copyChildNode(child));
        }

        return copy;
    }

    /**
     * 拷贝子节点
     */
    private static DefaultMutableTreeNode copyChildNode(DefaultMutableTreeNode child) {
        Object childUserObj = child.getUserObject();
        if (!(childUserObj instanceof Object[])) {
            return new DefaultMutableTreeNode(childUserObj);
        }

        if (CollectionTreeNodes.isGroup(child)) {
            return deepCopyGroupNode(child);
        } else if (CollectionTreeNodes.isRequest(child)) {
            return copyRequestNode(child);
        } else if (CollectionTreeNodes.isSavedResponse(child)) {
            SavedResponse savedResponse = CollectionTreeNodes.savedResponse(child).orElseThrow();
            return CollectionTreeNodes.savedResponseNode(savedResponse);
        }

        return new DefaultMutableTreeNode(childUserObj);
    }

    /**
     * 深拷贝请求节点
     */
    private static DefaultMutableTreeNode copyRequestNode(DefaultMutableTreeNode child) {
        HttpRequestItem item = CollectionTreeNodes.request(child).orElseThrow();
        return CollectionTreeNodes.requestNodeWithExamples(CollectionIdNormalizer.copyRequestWithNewIds(item));
    }

    /**
     * 递归克隆树节点（用于创建选择树）
     * 会过滤掉 SavedResponse 类型的请求。
     */
    public static DefaultMutableTreeNode cloneTreeNode(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();
        DefaultMutableTreeNode copy = new DefaultMutableTreeNode(
                userObj instanceof Object[] ? ((Object[]) userObj).clone() : userObj
        );

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);

            // 检查是否需要过滤此节点
            if (shouldFilterNode(child)) {
                continue; // 跳过 SavedResponse 类型的请求
            }

            copy.add(cloneTreeNode(child));
        }

        return copy;
    }

    /**
     * 判断是否应该过滤掉该节点（SavedResponse）
     */
    private static boolean shouldFilterNode(DefaultMutableTreeNode node) {
        return CollectionTreeNodes.isSavedResponse(node);
    }
}
