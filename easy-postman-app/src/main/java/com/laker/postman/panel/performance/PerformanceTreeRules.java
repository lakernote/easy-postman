package com.laker.postman.panel.performance;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.model.NodeType;


import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.model.PerformanceProtocolRules;

import javax.swing.tree.DefaultMutableTreeNode;

public final class PerformanceTreeRules {

    private PerformanceTreeRules() {
    }

    public static RequestItemProtocolEnum resolveRequestProtocol(HttpRequestItem item) {
        return PerformanceProtocolRules.resolveRequestProtocol(item);
    }

    public static boolean isSsePerfRequest(PerformanceTreeNode nodeData) {
        return nodeData != null && nodeData.type == NodeType.REQUEST && nodeData.ssePerformanceEnabled;
    }

    public static boolean isWebSocketPerfRequest(HttpRequestItem item) {
        return PerformanceProtocolRules.isWebSocketPerfRequest(item);
    }

    public static boolean canAcceptChild(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode childNode) {
        if (parentNode == null || childNode == null
                || !(childNode.getUserObject() instanceof PerformanceTreeNode childData)) {
            return false;
        }
        return switch (childData.type) {
            case THREAD_GROUP -> isNodeType(parentNode, NodeType.ROOT);
            case CSV_DATA_SET -> isNodeType(parentNode, NodeType.THREAD_GROUP);
            case REQUEST -> isRequestContainerTarget(parentNode);
            case ASSERTION -> isHttpRequestPostProcessorTarget(parentNode)
                    || isNodeType(parentNode, NodeType.SSE_READ)
                    || isNodeType(parentNode, NodeType.WS_READ);
            case EXTRACTOR -> isHttpRequestPostProcessorTarget(parentNode)
                    || isNodeType(parentNode, NodeType.SSE_READ)
                    || isNodeType(parentNode, NodeType.WS_READ);
            case TIMER -> isNodeType(parentNode, NodeType.REQUEST)
                    || isRequestContainerController(parentNode)
                    || isWebSocketStepContainerTarget(parentNode);
            case SSE_CONNECT, SSE_READ -> isSseStageContainerTarget(parentNode);
            case WS_CONNECT -> isWebSocketRequestTarget(parentNode);
            case WS_SEND, WS_READ, WS_CLOSE -> isWebSocketStepContainerTarget(parentNode);
            case SIMPLE -> canAcceptSimple(parentNode, childNode);
            case LOOP -> canAcceptLoop(parentNode, childNode);
            case CONDITION -> canAcceptCondition(parentNode, childNode);
            case WHILE -> canAcceptWhile(parentNode, childNode);
            case ONCE_ONLY -> canAcceptOnceOnly(parentNode, childNode);
            case ROOT -> false;
        };
    }

    public static boolean canAcceptLoop(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode loopNode) {
        return canAcceptController(parentNode, loopNode);
    }

    public static boolean canAcceptSimple(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode simpleNode) {
        return canAcceptController(parentNode, simpleNode);
    }

    public static boolean canAcceptCondition(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode conditionNode) {
        return canAcceptController(parentNode, conditionNode);
    }

    public static boolean canAcceptWhile(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode whileNode) {
        return canAcceptController(parentNode, whileNode);
    }

    public static boolean canAcceptOnceOnly(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode onceOnlyNode) {
        if (containsWebSocketScenarioNodeOutsideRequest(onceOnlyNode)) {
            return false;
        }
        return isRequestContainerTarget(parentNode);
    }

    private static boolean canAcceptController(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode controllerNode) {
        if (containsNodeType(controllerNode, NodeType.WS_CONNECT)) {
            return false;
        }
        boolean hasRequest = containsNodeType(controllerNode, NodeType.REQUEST);
        boolean hasWebSocketStep = containsAnyNodeType(
                controllerNode,
                NodeType.WS_SEND,
                NodeType.WS_READ,
                NodeType.WS_CLOSE
        );
        if (hasRequest && hasWebSocketStep) {
            return false;
        }
        if (hasRequest) {
            return isRequestContainerTarget(parentNode);
        }
        if (hasWebSocketStep) {
            return isWebSocketStepContainerTarget(parentNode);
        }
        return isRequestContainerTarget(parentNode) || isWebSocketStepContainerTarget(parentNode);
    }

    public static boolean isRequestContainerTarget(DefaultMutableTreeNode node) {
        return isNodeType(node, NodeType.THREAD_GROUP) || isRequestContainerController(node);
    }

    public static boolean isRequestContainerLoop(DefaultMutableTreeNode node) {
        return isRequestContainerNodeType(node, NodeType.LOOP);
    }

    public static boolean isRequestContainerSimple(DefaultMutableTreeNode node) {
        return isRequestContainerNodeType(node, NodeType.SIMPLE);
    }

    public static boolean isRequestContainerCondition(DefaultMutableTreeNode node) {
        return isRequestContainerNodeType(node, NodeType.CONDITION);
    }

    public static boolean isRequestContainerWhile(DefaultMutableTreeNode node) {
        return isRequestContainerNodeType(node, NodeType.WHILE);
    }

    public static boolean isRequestContainerOnceOnly(DefaultMutableTreeNode node) {
        return isRequestContainerNodeType(node, NodeType.ONCE_ONLY);
    }

    public static boolean isRequestContainerController(DefaultMutableTreeNode node) {
        return isRequestContainerLoop(node)
                || isRequestContainerSimple(node)
                || isRequestContainerCondition(node)
                || isRequestContainerWhile(node)
                || isRequestContainerOnceOnly(node);
    }

    private static boolean isRequestContainerNodeType(DefaultMutableTreeNode node, NodeType type) {
        if (node == null || !(node.getUserObject() instanceof PerformanceTreeNode nodeData)
                || nodeData.type != type) {
            return false;
        }
        return getParentRequestNode(node) == null;
    }

    public static boolean isSseStageContainerTarget(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return false;
        }
        return isSsePerfRequest(nodeData);
    }

    public static boolean isWebSocketRequestTarget(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return false;
        }
        return nodeData.type == NodeType.REQUEST && isWebSocketPerfRequest(nodeData.httpRequestItem);
    }

    public static boolean isWebSocketStepContainerTarget(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof PerformanceTreeNode nodeData)) {
            return false;
        }
        if (nodeData.type == NodeType.REQUEST) {
            return isWebSocketRequestTarget(node);
        }
        return (nodeData.type == NodeType.LOOP || nodeData.type == NodeType.SIMPLE || nodeData.type == NodeType.CONDITION
                || nodeData.type == NodeType.WHILE)
                && getParentWebSocketRequestNode(node) != null;
    }

    private static boolean isHttpRequestPostProcessorTarget(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof PerformanceTreeNode nodeData) || nodeData.type != NodeType.REQUEST) {
            return false;
        }
        return !isSsePerfRequest(nodeData) && !isWebSocketPerfRequest(nodeData.httpRequestItem);
    }

    public static DefaultMutableTreeNode getParentRequestNode(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode current = node;
        while (current != null) {
            Object userObj = current.getUserObject();
            if (userObj instanceof PerformanceTreeNode nodeData && nodeData.type == NodeType.REQUEST) {
                return current;
            }
            current = (DefaultMutableTreeNode) current.getParent();
        }
        return null;
    }

    public static DefaultMutableTreeNode getParentWebSocketRequestNode(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode requestNode = getParentRequestNode(node);
        if (requestNode == null || !(requestNode.getUserObject() instanceof PerformanceTreeNode requestNodeData)) {
            return null;
        }
        return isWebSocketPerfRequest(requestNodeData.httpRequestItem) ? requestNode : null;
    }

    public static boolean isNodeType(DefaultMutableTreeNode node, NodeType type) {
        return node != null
                && node.getUserObject() instanceof PerformanceTreeNode nodeData
                && nodeData.type == type;
    }

    public static boolean containsAnyNodeType(DefaultMutableTreeNode node, NodeType... types) {
        for (NodeType type : types) {
            if (containsNodeType(node, type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsNodeType(DefaultMutableTreeNode node, NodeType type) {
        if (node == null) {
            return false;
        }
        if (node.getUserObject() instanceof PerformanceTreeNode nodeData && nodeData.type == type) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsNodeType((DefaultMutableTreeNode) node.getChildAt(i), type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWebSocketScenarioNodeOutsideRequest(DefaultMutableTreeNode node) {
        if (node == null) {
            return false;
        }
        if (node.getUserObject() instanceof PerformanceTreeNode nodeData) {
            if (nodeData.type == NodeType.REQUEST) {
                return false;
            }
            if (nodeData.type == NodeType.WS_CONNECT
                    || nodeData.type == NodeType.WS_SEND
                    || nodeData.type == NodeType.WS_READ
                    || nodeData.type == NodeType.WS_CLOSE) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsWebSocketScenarioNodeOutsideRequest((DefaultMutableTreeNode) node.getChildAt(i))) {
                return true;
            }
        }
        return false;
    }
}
