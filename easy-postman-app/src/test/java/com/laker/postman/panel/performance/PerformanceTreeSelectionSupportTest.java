package com.laker.postman.panel.performance;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.model.Variable;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreeDocumentMapper;
import com.laker.postman.panel.performance.config.CsvDataSetPropertyPanel;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestProtocol;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.service.collections.CollectionTreeRootRegistry;
import com.laker.postman.service.collections.CollectionDocumentRegistry;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.service.variable.RequestExecutionContext;
import com.laker.postman.service.variable.RequestExecutionScope;
import com.laker.postman.service.variable.VariableResolver;
import org.testng.annotations.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import java.awt.CardLayout;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class PerformanceTreeSelectionSupportTest extends AbstractSwingUiTest {

    @Test
    public void requestSelectionShouldSyncStructureSwitchEditorAndTrackCurrentRequest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TreeFixture fixture = new TreeFixture();
            AtomicInteger syncCount = new AtomicInteger();
            AtomicReference<HttpRequestItem> switchedItem = new AtomicReference<>();
            AtomicReference<DefaultMutableTreeNode> currentRequest = new AtomicReference<>();
            PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                    ignored -> {
                    },
                    switchedItem::set,
                    (node, treeNode) -> syncCount.incrementAndGet(),
                    currentRequest::set
            );
            support.install();

            fixture.tree.setSelectionPath(new TreePath(fixture.requestNode.getPath()));

            assertEquals(syncCount.get(), 1);
            assertSame(switchedItem.get(), fixture.requestItem);
            assertSame(currentRequest.get(), fixture.requestNode);
            assertTrue(fixture.requestCard.isVisible());
            assertFalse(fixture.emptyCard.isVisible());
        });
    }

    @Test
    public void requestSelectionShouldUsePerformanceRequestScopeForVariableResolution() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                RequestExecutionContext.setCurrentScope(RequestExecutionScope.fromGroupVariables(Map.of(
                        "testname",
                        "1111"
                )));
                TreeFixture fixture = new TreeFixture();
                PerformanceTreeNode requestData = (PerformanceTreeNode) fixture.requestNode.getUserObject();
                requestData.requestExecutionScope = RequestExecutionScope.fromGroupVariables(Map.of(
                        "testname",
                        "2222"
                ));
                PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                        ignored -> {
                        },
                        ignored -> {
                        },
                        (node, treeNode) -> {
                        },
                        ignored -> {
                        }
                );
                support.install();

                fixture.tree.setSelectionPath(new TreePath(fixture.requestNode.getPath()));

                assertEquals(VariableResolver.resolveVariable("testname"), "2222");
            } finally {
                RequestExecutionContext.clearCurrentScope();
            }
        });
    }

    @Test
    public void requestSelectionShouldPreferLatestCollectionScopeForVariableResolution() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                RequestExecutionContext.setCurrentScope(RequestExecutionScope.fromGroupVariables(Map.of(
                        "testname",
                        "333"
                )));
                TreeFixture fixture = new TreeFixture();
                fixture.requestItem.setId("latest-selection-scope-request");
                PerformanceTreeNode requestData = (PerformanceTreeNode) fixture.requestNode.getUserObject();
                requestData.requestExecutionScope = RequestExecutionScope.fromGroupVariables(Map.of(
                        "testname",
                        "333"
                ));
                registerCollectionRequest(fixture.requestItem, "888");
                PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                        ignored -> {
                        },
                        ignored -> {
                        },
                        (node, treeNode) -> {
                        },
                        ignored -> {
                        }
                );
                support.install();

                fixture.tree.setSelectionPath(new TreePath(fixture.requestNode.getPath()));

                assertEquals(VariableResolver.resolveVariable("testname"), "888");
            } finally {
                RequestExecutionContext.clearCurrentScope();
                clearCollectionRegistries();
            }
        });
    }

    @Test
    public void requestSelectionShouldSwitchEditorToLatestCollectionRequestWhenSourceExists() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                TreeFixture fixture = new TreeFixture();
                fixture.requestItem.setId("latest-selection-request");
                fixture.requestItem.setUrl("https://httpbin.org/get?test={{testname}}");
                HttpRequestItem latestItem = new HttpRequestItem();
                latestItem.setId("latest-selection-request");
                latestItem.setName("request");
                latestItem.setUrl("https://httpbingo.org/get?test={{testname}}");
                registerCollectionRequest(latestItem, "888");
                AtomicReference<HttpRequestItem> switchedItem = new AtomicReference<>();
                PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                        ignored -> {
                        },
                        switchedItem::set,
                        (node, treeNode) -> {
                        },
                        ignored -> {
                        }
                );
                support.install();

                fixture.tree.setSelectionPath(new TreePath(fixture.requestNode.getPath()));

                assertEquals(switchedItem.get().getUrl(), "https://httpbingo.org/get?test={{testname}}");
                assertEquals(fixture.requestItem.getUrl(), "https://httpbin.org/get?test={{testname}}");
                PerformanceTreeNode requestData = (PerformanceTreeNode) fixture.requestNode.getUserObject();
                assertEquals(requestData.httpRequestItem.getUrl(), "https://httpbingo.org/get?test={{testname}}");
            } finally {
                clearCollectionRegistries();
            }
        });
    }

    @Test
    public void requestSelectionShouldNotifyTreeNodeChangedAfterLatestCollectionRefresh() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                TreeFixture fixture = new TreeFixture();
                fixture.requestItem.setId("latest-selection-render-request");
                HttpRequestItem latestItem = new HttpRequestItem();
                latestItem.setId("latest-selection-render-request");
                latestItem.setName("renamed request");
                latestItem.setUrl("https://httpbingo.org/get?test={{testname}}");
                registerCollectionRequest(latestItem, "888");
                AtomicInteger changedCount = new AtomicInteger();
                fixture.treeModel.addTreeModelListener(new TreeModelListener() {
                    @Override
                    public void treeNodesChanged(TreeModelEvent e) {
                        changedCount.incrementAndGet();
                    }

                    @Override
                    public void treeNodesInserted(TreeModelEvent e) {
                    }

                    @Override
                    public void treeNodesRemoved(TreeModelEvent e) {
                    }

                    @Override
                    public void treeStructureChanged(TreeModelEvent e) {
                    }
                });
                PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                        ignored -> {
                        },
                        ignored -> {
                        },
                        (node, treeNode) -> {
                        },
                        ignored -> {
                        }
                );
                support.install();

                fixture.tree.setSelectionPath(new TreePath(fixture.requestNode.getPath()));

                assertEquals(changedCount.get(), 1);
            } finally {
                clearCollectionRegistries();
            }
        });
    }

    @Test
    public void installShouldHydrateExistingRequestSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TreeFixture fixture = new TreeFixture();
            AtomicReference<HttpRequestItem> switchedItem = new AtomicReference<>();
            AtomicReference<DefaultMutableTreeNode> currentRequest = new AtomicReference<>();
            fixture.tree.setSelectionPath(new TreePath(fixture.requestNode.getPath()));

            PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                    ignored -> {
                    },
                    switchedItem::set,
                    (node, treeNode) -> {
                    },
                    currentRequest::set
            );
            support.install();

            assertSame(switchedItem.get(), fixture.requestItem);
            assertSame(currentRequest.get(), fixture.requestNode);
            assertTrue(fixture.requestCard.isVisible());
        });
    }

    @Test
    public void requestSelectionShouldReportMissingRequestData() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TreeFixture fixture = new TreeFixture();
            PerformanceTreeNode requestData = (PerformanceTreeNode) fixture.requestNode.getUserObject();
            requestData.httpRequestItem = null;
            requestData.requestSnapshot = PerformanceRequestSnapshot.builder()
                    .id("snapshot-id")
                    .name("snapshot request")
                    .url("ws://localhost:18080/ws")
                    .method("GET")
                    .protocol(PerformanceRequestProtocol.WEBSOCKET)
                    .build();
            AtomicInteger syncCount = new AtomicInteger();
            AtomicReference<HttpRequestItem> switchedItem = new AtomicReference<>();
            AtomicReference<DefaultMutableTreeNode> currentRequest = new AtomicReference<>(fixture.requestNode);
            AtomicReference<String> notification = new AtomicReference<>();
            PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                    ignored -> {
                    },
                    switchedItem::set,
                    (node, treeNode) -> syncCount.incrementAndGet(),
                    currentRequest::set,
                    notification::set
            );
            support.install();

            fixture.tree.setSelectionPath(new TreePath(fixture.requestNode.getPath()));

            assertEquals(syncCount.get(), 0);
            assertNull(requestData.httpRequestItem);
            assertNull(switchedItem.get());
            assertNull(currentRequest.get());
            assertTrue(fixture.emptyCard.isVisible());
            assertFalse(fixture.requestCard.isVisible());
            assertEquals(
                    notification.get(),
                    I18nUtil.getMessage(MessageKeys.PERFORMANCE_MSG_REQUEST_DATA_MISSING, "request")
            );
        });
    }

    @Test
    public void changingSelectionShouldPersistPreviousRequestAndShowEmptyForUnknownNode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TreeFixture fixture = new TreeFixture();
            AtomicReference<DefaultMutableTreeNode> savedRequest = new AtomicReference<>();
            AtomicReference<DefaultMutableTreeNode> currentRequest = new AtomicReference<>();
            PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                    savedRequest::set,
                    ignored -> {
                    },
                    (node, treeNode) -> {
                    },
                    currentRequest::set
            );
            support.install();

            fixture.tree.setSelectionPath(new TreePath(fixture.requestNode.getPath()));
            fixture.tree.setSelectionPath(new TreePath(fixture.unknownNode.getPath()));

            assertSame(savedRequest.get(), fixture.requestNode);
            assertEquals(currentRequest.get(), null);
            assertTrue(fixture.emptyCard.isVisible());
            assertFalse(fixture.requestCard.isVisible());
        });
    }

    @Test
    public void controllerSelectionShouldShowDescriptionCards() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TreeFixture fixture = new TreeFixture();
            DefaultMutableTreeNode simpleNode = new DefaultMutableTreeNode(
                    new PerformanceTreeNode("simple", NodeType.SIMPLE)
            );
            DefaultMutableTreeNode onceOnlyNode = new DefaultMutableTreeNode(
                    new PerformanceTreeNode("once only", NodeType.ONCE_ONLY)
            );
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) fixture.treeModel.getRoot();
            fixture.treeModel.insertNodeInto(simpleNode, root, root.getChildCount());
            fixture.treeModel.insertNodeInto(onceOnlyNode, root, root.getChildCount());
            AtomicReference<DefaultMutableTreeNode> currentRequest = new AtomicReference<>();
            PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                    ignored -> {
                    },
                    ignored -> {
                    },
                    (node, treeNode) -> {
                    },
                    currentRequest::set
            );
            support.install();

            fixture.tree.setSelectionPath(new TreePath(simpleNode.getPath()));
            assertTrue(fixture.simpleCard.isVisible());
            assertNull(currentRequest.get());

            fixture.tree.setSelectionPath(new TreePath(onceOnlyNode.getPath()));
            assertTrue(fixture.onceOnlyCard.isVisible());
            assertNull(currentRequest.get());
        });
    }

    @Test
    public void webSocketConnectSelectionShouldEditConnectStageNode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TreeFixture fixture = new TreeFixture();
            fixture.requestItem.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            PerformanceTreeNode connectData = new PerformanceTreeNode("connect", NodeType.WS_CONNECT);
            connectData.webSocketPerformanceData = new WebSocketPerformanceData();
            DefaultMutableTreeNode connectNode = new DefaultMutableTreeNode(connectData);
            fixture.requestNode.add(connectNode);
            RecordingWebSocketStagePropertyPanel wsConnectPanel = new RecordingWebSocketStagePropertyPanel();
            PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                    ignored -> {
                    },
                    ignored -> {
                    },
                    (node, treeNode) -> {
                    },
                    ignored -> {
                    },
                    wsConnectPanel
            );
            support.install();

            fixture.tree.setSelectionPath(new TreePath(connectNode.getPath()));

            assertSame(wsConnectPanel.lastNode, connectData);
            assertTrue(fixture.wsConnectCard.isVisible());
        });
    }

    @Test
    public void webSocketConnectSelectionShouldInitializeStageConfigFromRequestDefaults() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TreeFixture fixture = new TreeFixture();
            fixture.requestItem.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
            PerformanceTreeNode requestData = (PerformanceTreeNode) fixture.requestNode.getUserObject();
            requestData.webSocketPerformanceData = new WebSocketPerformanceData();
            requestData.webSocketPerformanceData.connectTimeoutMs = 4321;
            PerformanceTreeNode connectData = new PerformanceTreeNode("connect", NodeType.WS_CONNECT);
            DefaultMutableTreeNode connectNode = new DefaultMutableTreeNode(connectData);
            fixture.requestNode.add(connectNode);
            RecordingWebSocketStagePropertyPanel wsConnectPanel = new RecordingWebSocketStagePropertyPanel();
            PerformanceTreeSelectionSupport support = fixture.createSelectionSupport(
                    ignored -> {
                    },
                    ignored -> {
                    },
                    (node, treeNode) -> {
                    },
                    ignored -> {
                    },
                    wsConnectPanel
            );
            support.install();

            fixture.tree.setSelectionPath(new TreePath(connectNode.getPath()));

            assertSame(wsConnectPanel.lastNode, connectData);
            assertEquals(connectData.webSocketPerformanceData.connectTimeoutMs, 4321);
            assertNotSame(connectData.webSocketPerformanceData, requestData.webSocketPerformanceData);
        });
    }

    private static void registerCollectionRequest(HttpRequestItem item, String variableValue) {
        RequestGroup group = new RequestGroup("Group");
        group.setVariables(List.of(new Variable(true, "testname", variableValue)));
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode groupNode = CollectionTreeNodes.groupNode(group);
        groupNode.add(CollectionTreeNodes.requestNode(item));
        rootNode.add(groupNode);
        CollectionTreeRootRegistry.registerRootSupplier(() -> rootNode);
        CollectionDocumentRegistry.registerDocumentSupplier(() -> SwingCollectionTreeDocumentMapper.fromRoot(rootNode));
    }

    private static void clearCollectionRegistries() {
        CollectionTreeRootRegistry.clear();
        CollectionDocumentRegistry.registerDocumentSupplier(com.laker.postman.collection.model.CollectionDocument::empty);
    }

    private static final class TreeFixture {
        private static final String EMPTY_CARD = "empty";
        private static final String SIMPLE_CARD = "simple";
        private static final String ONCE_ONLY_CARD = "onceOnly";
        private static final String REQUEST_CARD = "request";
        private static final String WS_CONNECT_CARD = "wsConnect";

        private final HttpRequestItem requestItem = new HttpRequestItem();
        private final DefaultMutableTreeNode requestNode;
        private final DefaultMutableTreeNode unknownNode;
        private final JTree tree;
        private final DefaultTreeModel treeModel;
        private final CardLayout cardLayout = new CardLayout();
        private final JPanel propertyPanel = new JPanel(cardLayout);
        private final JPanel emptyCard = new JPanel();
        private final JPanel simpleCard = new JPanel();
        private final JPanel onceOnlyCard = new JPanel();
        private final JPanel requestCard = new JPanel();
        private final JPanel wsConnectCard = new JPanel();

        private TreeFixture() {
            requestItem.setName("request");
            PerformanceTreeNode requestData = new PerformanceTreeNode("request", NodeType.REQUEST, requestItem);
            requestNode = new DefaultMutableTreeNode(requestData);
            unknownNode = new DefaultMutableTreeNode("unknown");
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("root", NodeType.ROOT));
            root.add(requestNode);
            root.add(unknownNode);
            treeModel = new DefaultTreeModel(root);
            tree = new JTree(treeModel);
            emptyCard.add(new JLabel("empty"));
            simpleCard.add(new JLabel("simple"));
            onceOnlyCard.add(new JLabel("onceOnly"));
            requestCard.add(new JLabel("request"));
            wsConnectCard.add(new JLabel("wsConnect"));
            propertyPanel.add(emptyCard, EMPTY_CARD);
            propertyPanel.add(simpleCard, SIMPLE_CARD);
            propertyPanel.add(onceOnlyCard, ONCE_ONLY_CARD);
            propertyPanel.add(requestCard, REQUEST_CARD);
            propertyPanel.add(wsConnectCard, WS_CONNECT_CARD);
            cardLayout.show(propertyPanel, EMPTY_CARD);
        }

        private PerformanceTreeSelectionSupport createSelectionSupport(
                java.util.function.Consumer<DefaultMutableTreeNode> saveRequestAction,
                java.util.function.Consumer<HttpRequestItem> switchRequestEditorAction,
                java.util.function.BiConsumer<DefaultMutableTreeNode, PerformanceTreeNode> syncRequestStructureAction,
                java.util.function.Consumer<DefaultMutableTreeNode> currentRequestSetter) {
            return createSelectionSupport(
                    saveRequestAction,
                    switchRequestEditorAction,
                    syncRequestStructureAction,
                    currentRequestSetter,
                    null,
                    ignored -> {
                    }
            );
        }

        private PerformanceTreeSelectionSupport createSelectionSupport(
                java.util.function.Consumer<DefaultMutableTreeNode> saveRequestAction,
                java.util.function.Consumer<HttpRequestItem> switchRequestEditorAction,
                java.util.function.BiConsumer<DefaultMutableTreeNode, PerformanceTreeNode> syncRequestStructureAction,
                java.util.function.Consumer<DefaultMutableTreeNode> currentRequestSetter,
                java.util.function.Consumer<String> requestDataMissingAction) {
            return createSelectionSupport(
                    saveRequestAction,
                    switchRequestEditorAction,
                    syncRequestStructureAction,
                    currentRequestSetter,
                    null,
                    requestDataMissingAction
            );
        }

        private PerformanceTreeSelectionSupport createSelectionSupport(
                java.util.function.Consumer<DefaultMutableTreeNode> saveRequestAction,
                java.util.function.Consumer<HttpRequestItem> switchRequestEditorAction,
                java.util.function.BiConsumer<DefaultMutableTreeNode, PerformanceTreeNode> syncRequestStructureAction,
                java.util.function.Consumer<DefaultMutableTreeNode> currentRequestSetter,
                WebSocketStagePropertyPanel wsConnectPanel) {
            return createSelectionSupport(
                    saveRequestAction,
                    switchRequestEditorAction,
                    syncRequestStructureAction,
                    currentRequestSetter,
                    wsConnectPanel,
                    ignored -> {
                    }
            );
        }

        private PerformanceTreeSelectionSupport createSelectionSupport(
                java.util.function.Consumer<DefaultMutableTreeNode> saveRequestAction,
                java.util.function.Consumer<HttpRequestItem> switchRequestEditorAction,
                java.util.function.BiConsumer<DefaultMutableTreeNode, PerformanceTreeNode> syncRequestStructureAction,
                java.util.function.Consumer<DefaultMutableTreeNode> currentRequestSetter,
                WebSocketStagePropertyPanel wsConnectPanel,
                java.util.function.Consumer<String> requestDataMissingAction) {
            PerformanceTreeSelectionSupport support = new PerformanceTreeSelectionSupport(
                    tree,
                    treeModel,
                    cardLayout,
                    propertyPanel,
                    null,
                    new CsvDataSetPropertyPanel(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    wsConnectPanel,
                    null,
                    null,
                    null,
                    new PerformanceTreeSupport(treeModel),
                    saveRequestAction,
                    ignored -> {
                    },
                    ignored -> {
                    },
                    switchRequestEditorAction,
                    syncRequestStructureAction,
                    currentRequestSetter,
                    EMPTY_CARD,
                    "threadGroup",
                    "csvDataSet",
                    "loop",
                    SIMPLE_CARD,
                    "condition",
                    "while",
                    ONCE_ONLY_CARD,
                    REQUEST_CARD,
                    "assertion",
                    "extractor",
                    "timer",
                    "sseConnect",
                    "sseRead",
                    WS_CONNECT_CARD,
                    "wsSend",
                    "wsRead",
                    "wsClose"
            );
            support.setRequestDataMissingAction(requestDataMissingAction);
            return support;
        }
    }

    private static final class RecordingWebSocketStagePropertyPanel extends WebSocketStagePropertyPanel {
        private PerformanceTreeNode lastNode;

        private RecordingWebSocketStagePropertyPanel() {
            super(Stage.CONNECT);
        }

        @Override
        public void setNode(PerformanceTreeNode node) {
            lastNode = node;
        }
    }
}
