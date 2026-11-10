package com.laker.postman.panel.collections.tree.adapter;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.service.collections.CollectionTreeNodes;
import lombok.experimental.UtilityClass;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@UtilityClass
public class SwingCollectionTreeDocumentMapper {

    public CollectionDocument fromRoot(DefaultMutableTreeNode rootNode) {
        if (rootNode == null) {
            return CollectionDocument.empty();
        }

        List<CollectionNode> roots = new ArrayList<>();
        Optional<CollectionNode> rootAsDomainNode = mapNode(rootNode);
        if (rootAsDomainNode.isPresent()) {
            roots.add(rootAsDomainNode.get());
        } else {
            for (int i = 0; i < rootNode.getChildCount(); i++) {
                if (rootNode.getChildAt(i) instanceof DefaultMutableTreeNode childNode) {
                    mapNode(childNode).ifPresent(roots::add);
                }
            }
        }
        return new CollectionDocument(roots);
    }

    public void replaceRootChildren(DefaultMutableTreeNode rootNode, CollectionDocument document) {
        if (rootNode == null) {
            return;
        }
        rootNode.removeAllChildren();
        for (CollectionNode node : document == null ? List.<CollectionNode>of() : document.getRoots()) {
            toTreeNode(node).ifPresent(rootNode::add);
        }
    }

    public List<DefaultMutableTreeNode> toTreeNodes(CollectionDocument document) {
        List<DefaultMutableTreeNode> result = new ArrayList<>();
        if (document == null) {
            return result;
        }
        for (CollectionNode node : document.getRoots()) {
            toTreeNode(node).ifPresent(result::add);
        }
        return result;
    }

    private Optional<CollectionNode> mapNode(DefaultMutableTreeNode treeNode) {
        Optional<RequestGroup> group = CollectionTreeNodes.group(treeNode);
        if (group.isPresent()) {
            CollectionNode groupNode = CollectionNode.group(group.get());
            for (int i = 0; i < treeNode.getChildCount(); i++) {
                if (treeNode.getChildAt(i) instanceof DefaultMutableTreeNode childNode) {
                    mapNode(childNode).ifPresent(groupNode::addChild);
                }
            }
            return Optional.of(groupNode);
        }

        Optional<HttpRequestItem> request = CollectionTreeNodes.request(treeNode);
        return request.map(CollectionNode::request);
    }

    private Optional<DefaultMutableTreeNode> toTreeNode(CollectionNode node) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.isGroup()) {
            DefaultMutableTreeNode groupNode = CollectionTreeNodes.groupNode(node.asGroup());
            for (CollectionNode child : node.getChildren()) {
                toTreeNode(child).ifPresent(groupNode::add);
            }
            return Optional.of(groupNode);
        }
        if (node.isRequest()) {
            return Optional.of(CollectionTreeNodes.requestNodeWithExamples(node.asRequest()));
        }
        return Optional.empty();
    }
}
