package com.laker.postman.panel.collections.tree;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.model.Workspace;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;


import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.async.EasyTaskExecutor;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.tree.RequestTreeCellRenderer;
import com.laker.postman.common.component.tree.TreeTransferHandler;
import com.laker.postman.panel.collections.OpenedRequestTabSessionRestorer;
import com.laker.postman.panel.collections.tree.action.TreeNodeCloner;
import com.laker.postman.panel.collections.tree.handler.RequestTreeKeyboardHandler;
import com.laker.postman.panel.collections.tree.handler.RequestTreeMouseHandler;
import com.laker.postman.panel.collections.tree.coordinator.RequestTreeCoordinator;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.service.collections.CollectionDocumentRegistry;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionRequestSaveCoordinator;
import com.laker.postman.service.collections.CollectionTreeNodeTypes;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.service.collections.CollectionTreeRootRegistry;
import com.laker.postman.service.collections.OpenedRequestTabsStore;
import com.laker.postman.service.collections.CollectionTreeQueryService;
import com.laker.postman.service.collections.RequestSaveEventPublisher;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreePersistence;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreeQueries;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreeDocumentMapper;
import com.laker.postman.util.SystemUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求集合面板，展示所有请求分组和请求项
 * 支持请求的增删改查、分组管理、拖拽排序等功能
 */
@Slf4j
public class CollectionTreePanel extends UiSingletonPanel {
    public static final String REQUEST = CollectionTreeNodeTypes.REQUEST;
    public static final String GROUP = CollectionTreeNodeTypes.GROUP;
    public static final String ROOT = CollectionTreeNodeTypes.ROOT;
    public static final String SAVED_RESPONSE = CollectionTreeNodeTypes.SAVED_RESPONSE;
    public static final String EXPORT_FILE_NAME = "EasyPostman-Collections.json";
    // 请求集合的根节点
    @Getter
    private DefaultMutableTreeNode rootTreeNode;
    // 请求树组件
    @Getter
    private JTree requestTree;
    // 树模型，用于管理树节点
    @Getter
    private DefaultTreeModel treeModel;
    @Getter
    private transient SwingCollectionTreePersistence collectionTreePersistence;
    private transient SwingCollectionRequestSaveCoordinator requestSaveCoordinator;

    private record StartupLoadSnapshot(List<HttpRequestItem> openedRequests, HttpRequestItem lastNonNewRequest) {
    }


    @Override
    protected void initUI() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(200, 200));

        // 顶部面板，导入导出按钮在最上方，环境信息在下方
        JPanel topPanel = getTopPanel();
        topPanel.setOpaque(false);
        add(topPanel, BorderLayout.NORTH);

        JScrollPane treeScrollPane = getTreeScrollPane();
        add(treeScrollPane, BorderLayout.CENTER);
    }

    private JScrollPane getTreeScrollPane() {
        // 初始化请求树
        rootTreeNode = new DefaultMutableTreeNode(ROOT);
        CollectionTreeRootRegistry.registerRootSupplier(() -> rootTreeNode);
        CollectionDocumentRegistry.registerDocumentSupplier(() -> SwingCollectionTreeDocumentMapper.fromRoot(rootTreeNode));
        treeModel = new DefaultTreeModel(rootTreeNode);
        Workspace currentWorkspace = WorkspaceService.getInstance().getCurrentWorkspace();
        String filePath = SystemUtil.getCollectionPathForWorkspace(currentWorkspace);
        // 初始化持久化工具
        collectionTreePersistence = new SwingCollectionTreePersistence(filePath, rootTreeNode, treeModel);
        requestSaveCoordinator = new SwingCollectionRequestSaveCoordinator(rootTreeNode, collectionTreePersistence::saveCurrentTree);
        // 创建树组件，重写 getScrollableTracksViewportWidth 确保树宽度始终铺满 viewport，
        // 这样鼠标在行的右侧空白区域仍在 JTree 上，mouseMoved 事件能正常触发
        requestTree = new JTree(treeModel) {
            @Override
            public boolean isPathEditable(TreePath path) {
                Object node = path.getLastPathComponent();
                if (node instanceof DefaultMutableTreeNode treeNode) {
                    return treeNode.getParent() != null;
                }
                return false;
            }

            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }

            @Override
            public void paint(Graphics g) {
                super.paint(g);
                // 在树正常绘制之后，对 hover 行叠加整行背景高亮
                CollectionTreeHoverOverlay.paint(g, this);
            }
        };
        // 不显示根节点
        requestTree.setRootVisible(false);
        // 让 JTree 组件显示根节点的"展开/收起"小三角（即树形结构的手柄）
        requestTree.setShowsRootHandles(true);
        // 禁用双击展开/收起：设置 toggleClickCount 为 0
        // 这样双击只会触发我们自定义的打开 tab 行为，不会展开/收起
        requestTree.setToggleClickCount(0);
        // 设置树支持多选（支持批量删除）
        requestTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        // FlatLaf wideCellRenderer：让 CellRenderer 宽度铺满整行，使 "+" 号精确贴在右侧
        requestTree.putClientProperty("FlatLaf.style", "wideCellRenderer: true");
        // 注册 ToolTipManager，允许动态设置 tooltip（hover "+" 时显示 Add Request）
        ToolTipManager.sharedInstance().registerComponent(requestTree);
        // 设置树的字体和行高
        requestTree.setCellRenderer(new RequestTreeCellRenderer());
        requestTree.setRowHeight(28);
        JScrollPane treeScrollPane = new JScrollPane(requestTree);
        ToolWindowSurfaceStyle.applyTreeScrollPaneCard(treeScrollPane, requestTree);
        treeScrollPane.getVerticalScrollBar().setUnitIncrement(16); // 设置滚动条增量
        // 启用拖拽排序
        requestTree.setDragEnabled(true);
        requestTree.setDropMode(DropMode.ON_OR_INSERT);
        requestTree.setTransferHandler(new TreeTransferHandler(requestTree, treeModel, this::saveCurrentTree));
        return treeScrollPane;
    }

    private JPanel getTopPanel() {
        return UiSingletonFactory.getInstance(CollectionTreeToolbar.class);
    }


    @Override
    protected void registerListeners() {
        RequestTreeCoordinator treeCoordinator = new RequestTreeCoordinator(requestTree, this);
        requestTree.addKeyListener(new RequestTreeKeyboardHandler(requestTree, this, treeCoordinator));
        RequestTreeMouseHandler mouseHandler = new RequestTreeMouseHandler(requestTree, this, treeCoordinator);
        requestTree.addMouseListener(mouseHandler);
        requestTree.addMouseMotionListener(mouseHandler);

        EasyTaskExecutor.execute(
                this::loadStartupSnapshot,
                this::applyStartupSnapshot,
                this::handleStartupLoadError,
                "RequestCollections-Loader"
        );
    }

    private StartupLoadSnapshot loadStartupSnapshot() {
        collectionTreePersistence.loadIntoTree();
        List<HttpRequestItem> openedRequests = OpenedRequestTabsStore.loadAll();
        HttpRequestItem lastNonNewRequest = CollectionTreeQueryService.findLastPersistedRequest(openedRequests);
        return new StartupLoadSnapshot(openedRequests, lastNonNewRequest);
    }

    private void applyStartupSnapshot(StartupLoadSnapshot snapshot) {
        RequestEditorPanel requestEditPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
        requestEditPanel.setAutoInitializeSelectedTabOnTabAdd(true);
        restoreOpenedRequestTabs(snapshot, requestEditPanel);
        restoreTreeSelection(snapshot);
    }

    private void restoreOpenedRequestTabs(StartupLoadSnapshot snapshot, RequestEditorPanel requestEditPanel) {
        if (snapshot.openedRequests().isEmpty()) {
            requestEditPanel.addPlusTab();
            return;
        }
        requestEditPanel.setStartupRestoreSelectingLastTab(true);
        requestEditPanel.setAutoInitializeSelectedTabOnTabAdd(false);
        requestEditPanel.addPlusTab();
        OpenedRequestTabSessionRestorer.restoreOpenedRequests(
                snapshot.openedRequests(),
                () -> {
                    requestEditPanel.setStartupRestoreSelectingLastTab(false);
                    requestEditPanel.setAutoInitializeSelectedTabOnTabAdd(true);
                    requestEditPanel.initializeSelectedStartupRestoreTab();
                    requestEditPanel.warmUpDeferredRequestTabsAfterStartup();
                }
        );
    }

    private void restoreTreeSelection(StartupLoadSnapshot snapshot) {
        if (snapshot.lastNonNewRequest() != null) {
            locateAndSelectRequest(snapshot.lastNonNewRequest().getId());
            return;
        }
        expandAndSelectFirstGroupIfPresent();
    }

    private void expandAndSelectFirstGroupIfPresent() {
        if (rootTreeNode.getChildCount() <= 0) {
            return;
        }
        DefaultMutableTreeNode firstGroup = (DefaultMutableTreeNode) rootTreeNode.getChildAt(0);
        TreePath path = new TreePath(firstGroup.getPath());
        requestTree.setSelectionPath(path);
        requestTree.expandPath(path);
    }

    private void handleStartupLoadError(Throwable error) {
        log.error("Error loading request collections", error);
        UiSingletonFactory.getInstance(RequestEditorPanel.class).addPlusTab();
    }


    private void saveCurrentTree() {
        collectionTreePersistence.saveCurrentTree();
    }


    /**
     * 将请求保存到指定分组
     *
     * @param targetGroup 分组信息
     * @param item        请求项
     */
    public void saveRequestToGroup(RequestGroup targetGroup, HttpRequestItem item) {
        requestSaveCoordinator.addRequestToGroup(targetGroup, item)
                .ifPresent(result -> {
                    treeModel.reload(result.groupNode());
                    requestTree.expandPath(new TreePath(result.groupNode().getPath()));
                });
    }

    /**
     * 根据名称查找分组节点
     */
    public DefaultMutableTreeNode findGroupNode(DefaultMutableTreeNode node, String groupName) {
        if (node == null) return null;

        RequestGroup group = CollectionTreeNodes.group(node).orElse(null);
        if (group != null && groupName.equals(group.getName())) {
            return node;
        }


        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode result = findGroupNode(child, groupName);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * 更新已存在的请求
     *
     * @param item 请求项
     * @return 是否更新成功
     */
    public boolean updateExistingRequest(HttpRequestItem item) {
        if (item == null || item.getId() == null || item.getId().isEmpty()) {
            return false;
        }
        SwingCollectionRequestSaveCoordinator.RequestSaveResult result = requestSaveCoordinator
                .updateExistingRequest(item)
                .orElse(null);
        if (result == null) {
            return false;
        }
        HttpRequestItem updatedItem = result.requestItem();

        treeModel.nodeChanged(result.requestNode());
        // 保存后通知各 UI 订阅者自行同步最新数据。
        SwingUtilities.invokeLater(() -> RequestSaveEventPublisher.publishRequestSaved(updatedItem));
        return true;
    }

    public boolean saveResponseForRequest(HttpRequestItem requestItem, SavedResponse savedResponse) {
        SwingCollectionRequestSaveCoordinator.SavedResponseSaveResult result = requestSaveCoordinator
                .appendSavedResponse(requestItem, savedResponse)
                .orElse(null);
        if (result == null) {
            return false;
        }

        DefaultMutableTreeNode requestNode = result.requestNode();
        treeModel.reload(requestNode);
        requestTree.expandPath(new TreePath(requestNode.getPath()));
        return true;
    }

    public boolean upsertResponseForRequest(String requestId, SavedResponse savedResponse) {
        SwingCollectionRequestSaveCoordinator.SavedResponseSaveResult result = requestSaveCoordinator
                .upsertSavedResponse(requestId, savedResponse)
                .orElse(null);
        if (result == null) return false;
        treeModel.reload(result.requestNode());
        requestTree.expandPath(new TreePath(result.requestNode().getPath()));
        return true;
    }

    public boolean deleteResponseForRequest(String requestId, String responseId) {
        SwingCollectionRequestSaveCoordinator.RequestSaveResult result = requestSaveCoordinator
                .removeSavedResponse(requestId, responseId)
                .orElse(null);
        if (result == null) return false;
        treeModel.reload(result.requestNode());
        return true;
    }


    /**
     * 获取分组树的 TreeModel（用于分组选择树）
     */
    public DefaultTreeModel getGroupTreeModel() {
        return treeModel;
    }


    // 创建一个可多选的请求/分组选择树（用于Runner面板弹窗）
    public JTree createRequestSelectionTree() {
        DefaultTreeModel model = new DefaultTreeModel(TreeNodeCloner.cloneTreeNode(rootTreeNode));
        JTree tree = new JTree(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new RequestTreeCellRenderer());
        tree.setRowHeight(28);
        // 支持多选
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        return tree;
    }

    // 获取树中选中的所有请求（包含分组下所有请求）
    public List<HttpRequestItem> getSelectedRequestsFromTree(JTree tree) {
        List<HttpRequestItem> result = new ArrayList<>();
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) return result;
        for (TreePath path : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            collectRequestsRecursively(node, result);
        }
        // 去重（按id）
        Map<String, HttpRequestItem> map = new LinkedHashMap<>();
        for (HttpRequestItem item : result) {
            map.put(item.getId(), item);
        }
        return new ArrayList<>(map.values());
    }

    // 递归收集请求
    private void collectRequestsRecursively(DefaultMutableTreeNode node, List<HttpRequestItem> list) {
        CollectionTreeNodes.request(node).ifPresent(list::add);
        if (CollectionTreeNodes.isGroup(node)) {
            for (int i = 0; i < node.getChildCount(); i++) {
                collectRequestsRecursively((DefaultMutableTreeNode) node.getChildAt(i), list);
            }
        }
    }

    /**
     * 根据请求ID定位并选中树中的对应节点
     *
     * @param requestId 请求ID
     */
    public void locateAndSelectRequest(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return;
        }

        DefaultMutableTreeNode targetNode = SwingCollectionTreeQueries.findRequestNodeById(rootTreeNode, requestId);
        if (targetNode == null) {
            return;
        }

        TreePath treePath = new TreePath(targetNode.getPath());
        requestTree.expandPath(treePath.getParentPath());
        requestTree.setSelectionPath(treePath);
        requestTree.requestFocusInWindow();
    }

    /**
     * 切换到指定工作区的请求集合文件，并在集合树加载完成后执行回调。
     */
    public void switchWorkspaceAndRefreshUI(String collectionFilePath, Runnable onSuccessCallback) {
        // 使用 AsyncTaskExecutor 异步切换工作区
        EasyTaskExecutor.builder()
                .threadName("SwitchWorkspace-Loader")
                .backgroundTask(() -> {
                    // 后台线程：执行文件加载操作
                    if (collectionTreePersistence != null) {
                        collectionTreePersistence.switchDataFilePath(collectionFilePath);
                    }
                })
                .onSuccess(() -> {
                    // EDT线程：更新UI
                    UiSingletonFactory.getInstance(RequestEditorPanel.class).getTabbedPane().removeAll();
                    UiSingletonFactory.getInstance(RequestEditorPanel.class).addPlusTab();
                    expandFirstGroup();
                    if (onSuccessCallback != null) {
                        onSuccessCallback.run();
                    }
                })
                .onError(error -> {
                    // EDT线程：处理错误
                    log.error("Error switching workspace and loading collections", error);
                    UiSingletonFactory.getInstance(RequestEditorPanel.class).getTabbedPane().removeAll();
                    UiSingletonFactory.getInstance(RequestEditorPanel.class).addPlusTab();
                })
                .execute();
    }

    /**
     * 展开第一个分组
     */
    public void expandFirstGroup() {
        if (rootTreeNode != null && rootTreeNode.getChildCount() > 0) {
            DefaultMutableTreeNode firstChild = (DefaultMutableTreeNode) rootTreeNode.getChildAt(0);
            TreePath path = new TreePath(firstChild.getPath());
            requestTree.expandPath(path);
        }
    }

}
