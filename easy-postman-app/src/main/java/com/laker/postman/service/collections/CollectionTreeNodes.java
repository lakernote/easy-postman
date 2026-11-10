package com.laker.postman.service.collections;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.request.model.HttpRequestItem;


import lombok.experimental.UtilityClass;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Optional;

@UtilityClass
public class CollectionTreeNodes {

    public static DefaultMutableTreeNode groupNode(RequestGroup group) {
        return new DefaultMutableTreeNode(userObject(CollectionTreeNodeTypes.GROUP, group));
    }

    public static DefaultMutableTreeNode requestNode(HttpRequestItem request) {
        return new DefaultMutableTreeNode(userObject(CollectionTreeNodeTypes.REQUEST, request));
    }

    /**
     * 创建请求节点，并为其 Example 创建子节点。
     */
    public static DefaultMutableTreeNode requestNodeWithExamples(HttpRequestItem request) {
        DefaultMutableTreeNode requestNode = requestNode(request);
        if (request.getResponse() != null) {
            for (SavedResponse savedResponse : request.getResponse()) {
                if (savedResponse != null) {
                    requestNode.add(savedResponseNode(savedResponse));
                }
            }
        }
        return requestNode;
    }

    public static DefaultMutableTreeNode savedResponseNode(SavedResponse savedResponse) {
        return new DefaultMutableTreeNode(userObject(CollectionTreeNodeTypes.SAVED_RESPONSE, savedResponse));
    }

    public static Object[] userObject(String type, Object payload) {
        return new Object[]{type, payload};
    }

    public static boolean isGroup(DefaultMutableTreeNode node) {
        return group(node).isPresent();
    }

    public static boolean isRequest(DefaultMutableTreeNode node) {
        return request(node).isPresent();
    }

    public static boolean isSavedResponse(DefaultMutableTreeNode node) {
        return savedResponse(node).isPresent();
    }

    public static Optional<RequestGroup> group(DefaultMutableTreeNode node) {
        return groupPayload(userObject(node));
    }

    public static Optional<HttpRequestItem> request(DefaultMutableTreeNode node) {
        return requestPayload(userObject(node));
    }

    public static Optional<SavedResponse> savedResponse(DefaultMutableTreeNode node) {
        return savedResponsePayload(userObject(node));
    }

    public static Optional<String> type(DefaultMutableTreeNode node) {
        return typeOfUserObject(userObject(node));
    }

    public static Optional<RequestGroup> groupPayload(Object userObject) {
        return payload(userObject, CollectionTreeNodeTypes.GROUP, RequestGroup.class);
    }

    public static Optional<HttpRequestItem> requestPayload(Object userObject) {
        return payload(userObject, CollectionTreeNodeTypes.REQUEST, HttpRequestItem.class);
    }

    public static Optional<SavedResponse> savedResponsePayload(Object userObject) {
        return payload(userObject, CollectionTreeNodeTypes.SAVED_RESPONSE, SavedResponse.class);
    }

    public static Optional<String> typeOfUserObject(Object userObject) {
        if (userObject instanceof Object[] obj && obj.length >= 1 && obj[0] instanceof String type) {
            return Optional.of(type);
        }
        return Optional.empty();
    }

    public static boolean hasType(DefaultMutableTreeNode node, String expectedType) {
        return type(node).filter(expectedType::equals).isPresent();
    }

    public static void setRequest(DefaultMutableTreeNode node, HttpRequestItem request) {
        replacePayload(node, CollectionTreeNodeTypes.REQUEST, request);
    }

    public static void setSavedResponse(DefaultMutableTreeNode node, SavedResponse savedResponse) {
        replacePayload(node, CollectionTreeNodeTypes.SAVED_RESPONSE, savedResponse);
    }

    private static void replacePayload(DefaultMutableTreeNode node, String expectedType, Object payload) {
        Object userObject = userObject(node);
        if (!(userObject instanceof Object[] obj) || obj.length < 2 || !expectedType.equals(obj[0])) {
            throw new IllegalArgumentException("Expected collection tree node type: " + expectedType);
        }
        obj[1] = payload;
    }

    private static <T> Optional<T> payload(Object userObject, String expectedType, Class<T> payloadType) {
        if (userObject instanceof Object[] obj
                && obj.length >= 2
                && expectedType.equals(obj[0])
                && payloadType.isInstance(obj[1])) {
            return Optional.of(payloadType.cast(obj[1]));
        }
        return Optional.empty();
    }

    private static Object userObject(DefaultMutableTreeNode node) {
        return node != null ? node.getUserObject() : null;
    }
}
