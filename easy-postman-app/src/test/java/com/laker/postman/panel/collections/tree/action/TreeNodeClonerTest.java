package com.laker.postman.panel.collections.tree.action;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.service.collections.CollectionTreeNodes;
import org.testng.annotations.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

public class TreeNodeClonerTest {

    @Test
    public void shouldCopyGroupWithNewIdsAndExampleNodes() {
        SavedResponse example = new SavedResponse();
        example.setId("example");
        example.setName("OK");
        HttpRequestItem request = new HttpRequestItem();
        request.setId("request");
        request.setResponse(new ArrayList<>(List.of(example)));
        RequestGroup group = new RequestGroup("Group");
        DefaultMutableTreeNode groupNode = CollectionTreeNodes.groupNode(group);
        groupNode.add(CollectionTreeNodes.requestNodeWithExamples(request));

        DefaultMutableTreeNode copy = TreeNodeCloner.deepCopyGroupNode(groupNode);

        assertNotEquals(CollectionTreeNodes.group(copy).orElseThrow().getId(), group.getId());
        DefaultMutableTreeNode requestCopyNode = (DefaultMutableTreeNode) copy.getChildAt(0);
        HttpRequestItem requestCopy = CollectionTreeNodes.request(requestCopyNode).orElseThrow();
        assertNotEquals(requestCopy.getId(), "request");
        assertEquals(requestCopyNode.getChildCount(), 1, "example child nodes should be copied");
        SavedResponse exampleCopy = CollectionTreeNodes.savedResponse(
                (DefaultMutableTreeNode) requestCopyNode.getChildAt(0)).orElseThrow();
        assertSame(exampleCopy, requestCopy.getResponse().get(0), "tree node and request must share the example");
        assertNotEquals(exampleCopy.getId(), "example");
        assertEquals(example.getId(), "example");
        assertEquals(request.getId(), "request");
    }
}
