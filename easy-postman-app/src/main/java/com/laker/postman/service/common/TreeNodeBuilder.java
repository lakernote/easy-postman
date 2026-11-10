package com.laker.postman.service.common;

import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.CollectionParseResult;
import com.laker.postman.service.collections.CollectionTreeNodes;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * TreeNode 构建工具类
 * 统一的 UI 层转换逻辑，将解析结果转换为 Swing TreeNode
 */
public class TreeNodeBuilder {

    private TreeNodeBuilder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 从解析结果构建 TreeNode
     *
     * @param parseResult 解析结果
     * @return TreeNode
     */
    public static DefaultMutableTreeNode buildFromParseResult(CollectionParseResult parseResult) {
        // 创建分组节点
        DefaultMutableTreeNode groupNode = CollectionTreeNodes.groupNode(parseResult.getGroup());

        // 递归构建子节点
        for (CollectionNode child : parseResult.getChildren()) {
            DefaultMutableTreeNode childNode = buildFromCollectionNode(child);
            groupNode.add(childNode);
        }

        return groupNode;
    }

    /**
     * 递归构建 TreeNode
     *
     * @param node Collection 节点
     * @return TreeNode
     */
    private static DefaultMutableTreeNode buildFromCollectionNode(CollectionNode node) {
        if (node.isGroup()) {
            // 分组节点
            DefaultMutableTreeNode groupNode = CollectionTreeNodes.groupNode(node.asGroup());

            // 递归处理子节点
            for (CollectionNode child : node.getChildren()) {
                groupNode.add(buildFromCollectionNode(child));
            }

            return groupNode;
        } else {
            return CollectionTreeNodes.requestNodeWithExamples(node.asRequest());
        }
    }
}
