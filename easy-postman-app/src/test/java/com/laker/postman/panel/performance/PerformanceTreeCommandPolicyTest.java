package com.laker.postman.panel.performance;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.HttpHeader;


import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.core.model.NodeType;
import org.testng.annotations.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.EnumSet;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PerformanceTreeCommandPolicyTest {

    @Test
    public void ordinaryHttpRequestShouldAllowExplicitSseWorkloadSelection() {
        TreeFixture fixture = new TreeFixture(RequestItemProtocolEnum.HTTP);
        PerformanceTreeCommandPolicy policy = new PerformanceTreeCommandPolicy(fixture.treeSupport);

        EnumSet<PerformanceTreeCommand> commands = policy.commandsForSingleSelection(fixture.request, List.of());

        assertTrue(commands.contains(PerformanceTreeCommand.TOGGLE_SSE_MODE));
        assertFalse(commands.contains(PerformanceTreeCommand.ADD_SSE_CONNECT));
        assertFalse(commands.contains(PerformanceTreeCommand.ADD_SSE_READ));
    }

    @Test
    public void rootSelectionShouldOnlyExposeRootActions() {
        TreeFixture fixture = new TreeFixture(RequestItemProtocolEnum.HTTP);
        PerformanceTreeCommandPolicy policy = new PerformanceTreeCommandPolicy(fixture.treeSupport);

        EnumSet<PerformanceTreeCommand> commands = policy.commandsForSingleSelection(fixture.root, List.of());

        assertTrue(commands.contains(PerformanceTreeCommand.ADD_THREAD_GROUP));
        assertFalse(commands.contains(PerformanceTreeCommand.ENABLE));
        assertFalse(commands.contains(PerformanceTreeCommand.DISABLE));
        assertFalse(commands.contains(PerformanceTreeCommand.RENAME));
        assertFalse(commands.contains(PerformanceTreeCommand.DELETE));
    }

    @Test
    public void disabledWebSocketConnectSelectionShouldExposeScenarioActionsAndEnable() {
        TreeFixture fixture = new TreeFixture(RequestItemProtocolEnum.WEBSOCKET);
        DefaultMutableTreeNode wsConnect = node(NodeType.WS_CONNECT, false);
        fixture.request.add(wsConnect);
        PerformanceTreeCommandPolicy policy = new PerformanceTreeCommandPolicy(fixture.treeSupport);

        EnumSet<PerformanceTreeCommand> commands = policy.commandsForSingleSelection(wsConnect, List.of());

        assertTrue(commands.contains(PerformanceTreeCommand.ADD_WS_CONNECT));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_WS_SEND));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_WS_READ));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_WS_CLOSE));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_LOOP));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_SIMPLE));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_CONDITION));
        assertFalse(commands.contains(PerformanceTreeCommand.ADD_ONCE_ONLY));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_TIMER));
        assertTrue(commands.contains(PerformanceTreeCommand.COPY));
        assertTrue(commands.contains(PerformanceTreeCommand.DELETE));
        assertTrue(commands.contains(PerformanceTreeCommand.ENABLE));
        assertFalse(commands.contains(PerformanceTreeCommand.DISABLE));
        assertFalse(commands.contains(PerformanceTreeCommand.RENAME));
        assertTrue(policy.canSetEnabled(wsConnect, true));
        assertFalse(policy.canSetEnabled(wsConnect, false));
        assertFalse(policy.canRename(wsConnect));
    }

    @Test
    public void threadGroupSelectionShouldExposeRequestContainerControllerActions() {
        TreeFixture fixture = new TreeFixture(RequestItemProtocolEnum.HTTP);
        PerformanceTreeCommandPolicy policy = new PerformanceTreeCommandPolicy(fixture.treeSupport);

        EnumSet<PerformanceTreeCommand> commands = policy.commandsForSingleSelection(fixture.threadGroup, List.of());

        assertTrue(commands.contains(PerformanceTreeCommand.ADD_REQUEST));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_LOOP));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_SIMPLE));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_CONDITION));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_ONCE_ONLY));
    }

    @Test
    public void sseReadSelectionShouldExposeStageActionsAssertionAndDisable() {
        TreeFixture fixture = new TreeFixture(sseRequestItem());
        fixture.requestData.ssePerformanceEnabled = true;
        DefaultMutableTreeNode sseRead = node(NodeType.SSE_READ, true);
        fixture.request.add(sseRead);
        PerformanceTreeCommandPolicy policy = new PerformanceTreeCommandPolicy(fixture.treeSupport);

        EnumSet<PerformanceTreeCommand> commands = policy.commandsForSingleSelection(sseRead, List.of());

        assertTrue(commands.contains(PerformanceTreeCommand.ADD_SSE_CONNECT));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_SSE_READ));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_ASSERTION));
        assertTrue(commands.contains(PerformanceTreeCommand.ADD_EXTRACTOR));
        assertTrue(commands.contains(PerformanceTreeCommand.DISABLE));
        assertFalse(commands.contains(PerformanceTreeCommand.ENABLE));
        assertFalse(commands.contains(PerformanceTreeCommand.RENAME));
    }

    @Test
    public void mixedMultiSelectionShouldExposeOnlySelectionWideActions() {
        TreeFixture fixture = new TreeFixture(RequestItemProtocolEnum.HTTP);
        DefaultMutableTreeNode disabledRequest = node(NodeType.REQUEST, false);
        fixture.threadGroup.add(disabledRequest);
        PerformanceTreeCommandPolicy policy = new PerformanceTreeCommandPolicy(fixture.treeSupport);

        EnumSet<PerformanceTreeCommand> commands = policy.commandsForMultiSelection(new TreePath[]{
                new TreePath(fixture.request.getPath()),
                new TreePath(disabledRequest.getPath())
        });

        assertTrue(commands.contains(PerformanceTreeCommand.COPY));
        assertTrue(commands.contains(PerformanceTreeCommand.DELETE));
        assertTrue(commands.contains(PerformanceTreeCommand.ENABLE));
        assertTrue(commands.contains(PerformanceTreeCommand.DISABLE));
        assertFalse(commands.contains(PerformanceTreeCommand.ADD_REQUEST));
        assertFalse(commands.contains(PerformanceTreeCommand.PASTE));
        assertFalse(commands.contains(PerformanceTreeCommand.RENAME));
    }

    private static DefaultMutableTreeNode node(NodeType type, boolean enabled) {
        PerformanceTreeNode data = new PerformanceTreeNode(type.name(), type);
        data.enabled = enabled;
        return new DefaultMutableTreeNode(data);
    }

    private static HttpRequestItem requestItem(RequestItemProtocolEnum protocol) {
        HttpRequestItem item = new HttpRequestItem();
        item.setProtocol(protocol);
        return item;
    }

    private static HttpRequestItem sseRequestItem() {
        HttpRequestItem item = requestItem(RequestItemProtocolEnum.HTTP);
        item.setHeadersList(List.of(new HttpHeader(true, "Accept", "text/event-stream")));
        return item;
    }

    private static final class TreeFixture {
        private final DefaultMutableTreeNode root = node(NodeType.ROOT, true);
        private final DefaultMutableTreeNode threadGroup = node(NodeType.THREAD_GROUP, true);
        private final PerformanceTreeNode requestData = new PerformanceTreeNode("request", NodeType.REQUEST, requestItem(RequestItemProtocolEnum.HTTP));
        private final DefaultMutableTreeNode request = new DefaultMutableTreeNode(requestData);
        private final PerformanceTreeSupport treeSupport;

        private TreeFixture(RequestItemProtocolEnum protocol) {
            this(requestItem(protocol));
        }

        private TreeFixture(HttpRequestItem item) {
            requestData.httpRequestItem = item;
            root.add(threadGroup);
            threadGroup.add(request);
            treeSupport = new PerformanceTreeSupport(new DefaultTreeModel(root));
        }
    }
}
