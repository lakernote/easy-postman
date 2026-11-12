package com.laker.postman.panel.collections.editor;

import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.tab.TabbedPaneDragHandler;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.panel.collections.editor.request.RequestEditSubPanel;
import com.laker.postman.panel.collections.editor.request.RequestEditSubPanelType;
import com.laker.postman.panel.collections.tree.CollectionGroupSelectionDialog;
import com.laker.postman.panel.collections.tree.RequestNameSelection;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.service.collections.RequestSaveEventPublisher;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.service.variable.RequestExecutionContext;
import com.laker.postman.service.variable.RequestExecutionScope;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.Getter;
import lombok.Setter;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.Optional;

import static com.formdev.flatlaf.FlatClientProperties.*;

/**
 * 请求编辑面板，支持多标签页，每个标签页为独立的请求编辑子面板
 */
public class RequestEditorPanel extends UiSingletonPanel {
    private static final String REQUEST_STRING = I18nUtil.getMessage(MessageKeys.NEW_REQUEST);
    static final Insets EDITOR_WORKSPACE_INSETS = new Insets(6, 6, 6, 6);
    static final int REQUEST_TAB_HEIGHT = 34;
    static final Insets REQUEST_TAB_INSETS = new Insets(2, 5, 2, 5);
    static final Insets REQUEST_TAB_AREA_INSETS = new Insets(0, 0, 0, 5);
    static final String PLUS_TAB = "+";
    @Getter
    private JTabbedPane tabbedPane; // 使用 JTabbedPane 管理多个请求编辑子面板

    @Getter
    private TabbedPaneDragHandler dragHandler; // Tab 拖拽排序支持

    // 普通交互下，新增/选中 tab 后会在下一轮 EDT 初始化当前编辑器。
    @Setter
    private boolean autoInitializeSelectedTabOnTabAdd = true;
    // 启动恢复多个请求时，仅最后一个 tab 自动成为选中 tab。
    @Getter
    @Setter
    private boolean startupRestoreSelectingLastTab;

    private RequestEditorTransientTabManager transientTabManager;
    private RequestEditorSaveController saveController;
    private RequestEditorShortcutInstaller shortcutInstaller;
    private RequestEditorTabOpenController tabOpenController;
    private RequestEditorTabCloseController tabCloseController;
    private RequestEditorTabStateController tabStateController;
    private RequestEditorTabLifecycleController tabLifecycleController;
    private RequestEditorTabRemovalController tabRemovalController;
    private RequestEditorTabInitializationScheduler tabInitializationScheduler;
    private RequestEditorExecutionScopeSynchronizer executionScopeSynchronizer;
    private final CollectionTreeEditorGateway collectionTreeGateway = new CollectionTreeEditorGateway();


    // 新建Tab，可指定标题
    public RequestEditSubPanel addNewTab(String title, RequestItemProtocolEnum protocol) {
        cancelStartupRestoreAutoSelectionIfNeeded();
        return tabLifecycleController.addNewRequestTab(title, protocol);
    }

    // 新建Tab，可指定标题
    public void addNewTab(String title) {
        addNewTab(title, RequestItemProtocolEnum.HTTP);
    }

    // 添加"+"Tab
    public void addPlusTab() {
        tabLifecycleController.addPlusTab();
    }

    // 判断是否为“+”Tab
    private boolean isPlusTab(int idx) {
        return tabLifecycleController.isPlusTab(idx);
    }

    /**
     * 单击树节点时显示或复用临时 Tab。
     */
    public void showOrCreateTransientTab(HttpRequestItem item) {
        tabOpenController.showOrCreateTransientRequest(item);
    }

    /**
     * 将当前临时 Tab 固定为普通 Tab。
     */
    public void pinTransientTab() {
        transientTabManager.pin();
    }

    /**
     * 单击 Group 时显示或复用临时 Tab。
     */
    public void showOrCreateTransientTabForGroup(DefaultMutableTreeNode groupNode, RequestGroup group) {
        tabOpenController.showOrCreateTransientGroup(groupNode, group);
    }

    // showOrCreateTab 需适配 "+" Tab（双击时调用，创建固定 tab）
    public void showOrCreateTab(HttpRequestItem item) {
        tabOpenController.showOrCreateRequestTab(item);
    }

    /**
     * 重新加载快捷键（快捷键设置修改后调用）
     */
    public void reloadShortcuts() {
        shortcutInstaller.install();
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getComponentAt(i) instanceof RequestEditorEmptyStatePanel emptyStatePanel) {
                emptyStatePanel.refreshShortcutHints();
            }
        }
    }

    /**
     * 保存当前请求
     */
    public boolean saveCurrentRequest() {
        return saveController.saveCurrentRequest();
    }

    protected Optional<RequestNameSelection> chooseGroupAndRequestName(
            TreeModel groupTreeModel,
            String defaultName
    ) {
        return CollectionGroupSelectionDialog.chooseGroupAndRequestName(groupTreeModel, defaultName);
    }

    // 用于动态更新tab红点
    public void updateTabDirty(RequestEditSubPanel panel, boolean dirty) {
        tabStateController.updateRequestDirty(panel, dirty);
    }

    public void updateTabDisplay(RequestEditSubPanel panel, String method, RequestItemProtocolEnum protocol) {
        tabStateController.updateRequestDisplay(panel, method, protocol);
    }

    /**
     * 更新 GroupEditPanel 的 Tab 标题
     */
    void updateGroupTabTitle(GroupEditPanel panel, String newTitle) {
        tabStateController.updateGroupTitle(panel, newTitle);
    }

    @Override
    protected void initUI() {
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(BorderFactory.createEmptyBorder(
                EDITOR_WORKSPACE_INSETS.top,
                EDITOR_WORKSPACE_INSETS.left,
                EDITOR_WORKSPACE_INSETS.bottom,
                EDITOR_WORKSPACE_INSETS.right));
        setLayout(new BorderLayout());
        tabbedPane = createRequestTabbedPane();
        tabLifecycleController = new RequestEditorTabLifecycleController(
                tabbedPane,
                PLUS_TAB,
                REQUEST_STRING,
                () -> autoInitializeSelectedTabOnTabAdd,
                this::initializeSelectedTabSoon);
        installDeferredTabInitialization();
        add(tabbedPane, BorderLayout.CENTER);
        installTransientTabAndDragSupport();
        createTabControllers();
    }

    private JTabbedPane createRequestTabbedPane() {
        JTabbedPane tabs = new RequestEditorTabbedPane(SwingConstants.TOP, requestTabsLayoutPolicy());
        ToolWindowSurfaceStyle.applyTabbedPaneCard(tabs);
        tabs.putClientProperty(TABBED_PANE_TAB_AREA_INSETS, REQUEST_TAB_AREA_INSETS);
        tabs.putClientProperty(TABBED_PANE_TAB_INSETS, REQUEST_TAB_INSETS);
        tabs.putClientProperty(TABBED_PANE_TAB_HEIGHT, REQUEST_TAB_HEIGHT);
        tabs.putClientProperty(TABBED_PANE_TAB_WIDTH_MODE, TABBED_PANE_TAB_WIDTH_MODE_PREFERRED);
        tabs.putClientProperty(TABBED_PANE_TAB_AREA_ALIGNMENT, TABBED_PANE_ALIGN_LEADING);
        tabs.putClientProperty(TABBED_PANE_TAB_ALIGNMENT, TABBED_PANE_ALIGN_LEADING);
        tabs.putClientProperty(TABBED_PANE_HAS_FULL_BORDER, false);
        tabs.putClientProperty(TABBED_PANE_SHOW_CONTENT_SEPARATOR, true);
        return tabs;
    }

    private static final class RequestEditorTabbedPane extends JTabbedPane {
        private RequestEditorTabbedPane(int tabPlacement, int tabLayoutPolicy) {
            super(tabPlacement, tabLayoutPolicy);
        }

        @Override
        public void updateUI() {
            setUI(new RequestEditorTabbedPaneUi());
        }
    }

    static int requestTabsLayoutPolicy() {
        return SettingManager.isRequestEditorTabsMultiLineEnabled()
                ? JTabbedPane.WRAP_TAB_LAYOUT
                : JTabbedPane.SCROLL_TAB_LAYOUT;
    }

    private void installDeferredTabInitialization() {
        tabInitializationScheduler = new RequestEditorTabInitializationScheduler(tabbedPane, () -> startupRestoreSelectingLastTab);
        tabbedPane.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                if (tabbedPane.getTabCount() > 0 && autoInitializeSelectedTabOnTabAdd) {
                    refreshSelectedRequestExecutionScope();
                    tabInitializationScheduler.initializeSelectedTabSoon();
                }
            }
        });
        tabbedPane.addChangeListener(e -> {
            refreshSelectedRequestExecutionScope();
            tabInitializationScheduler.initializeSelectedTabSoon();
        });
    }

    private void installTransientTabAndDragSupport() {
        transientTabManager = new RequestEditorTransientTabManager(
                tabbedPane,
                this::isPlusTab,
                this::addPlusTab,
                RequestEditorTabResourceCleaner::cleanup);

        dragHandler = TabbedPaneDragHandler.install(tabbedPane,
                transientTabManager::transientTabIndex,
                transientTabManager::setTransientTabIndex);
    }

    private void createTabControllers() {
        executionScopeSynchronizer = new RequestEditorExecutionScopeSynchronizer();
        tabStateController = new RequestEditorTabStateController(tabbedPane, transientTabManager::pinIfTransient);
        tabRemovalController = new RequestEditorTabRemovalController(tabbedPane, transientTabManager);
        saveController = new RequestEditorSaveController(
                this,
                tabStateController::currentRequestTab,
                this::pinTransientTab,
                this::chooseGroupAndRequestName,
                tabStateController::refreshNewRequestTab,
                collectionTreeGateway);
        tabCloseController = new RequestEditorTabCloseController(
                tabbedPane,
                this::isPlusTab,
                this::saveCurrentRequest,
                tabRemovalController::removeAt,
                tabRemovalController::removeComponent);
        tabOpenController = new RequestEditorTabOpenController(
                tabbedPane,
                transientTabManager,
                executionScopeSynchronizer,
                this::cancelStartupRestoreAutoSelectionIfNeeded,
                this::addPlusTab,
                this::isPlusTab,
                this::addNewTab,
                (item, tab) -> tab.initPanelData(item),
                REQUEST_STRING,
                collectionTreeGateway);
        shortcutInstaller = createShortcutInstaller();
    }

    /**
     * 在下一轮 EDT 初始化当前选中的请求编辑器，保持切换直接且不额外引入定时器。
     */
    void initializeSelectedTabSoon() {
        tabInitializationScheduler.initializeSelectedTabSoon();
    }

    void refreshSelectedRequestExecutionScope() {
        if (executionScopeSynchronizer == null || tabbedPane == null) {
            return;
        }
        Component selectedComponent = tabbedPane.getSelectedComponent();
        if (selectedComponent instanceof RequestEditSubPanel subPanel
                && subPanel.getPanelType() == RequestEditSubPanelType.NORMAL) {
            executionScopeSynchronizer.syncScopeForRequest(subPanel.getId());
            return;
        }
        RequestExecutionContext.setCurrentScope(RequestExecutionScope.empty());
    }

    public void initializeSelectedStartupRestoreTab() {
        tabInitializationScheduler.initializeSelectedStartupRestoreTab();
    }

    public void warmUpDeferredRequestTabsAfterStartup() {
        tabInitializationScheduler.warmUpDeferredRequestTabsAfterStartup();
    }

    private void cancelStartupRestoreAutoSelectionIfNeeded() {
        if (!startupRestoreSelectingLastTab) {
            return;
        }
        startupRestoreSelectingLastTab = false;
    }

    @Override
    protected void registerListeners() {
        RequestSaveEventPublisher.register(tabStateController::syncSavedRequest);
        reloadShortcuts();
        new RequestEditorPlusTabController(tabbedPane, this, PLUS_TAB, REQUEST_STRING, this::addNewTab).install();
    }

    private RequestEditorShortcutInstaller createShortcutInstaller() {
        return new RequestEditorShortcutInstaller(
                this,
                tabStateController::currentRequestTab,
                () -> addNewTab(null),
                this::closeCurrentTab,
                this::closeOtherTabs,
                this::closeAllTabs);
    }

    /**
     * 显示分组编辑面板（参考 Postman，不使用弹窗）
     * 双击时调用，创建固定 tab
     */
    public void showGroupEditPanel(DefaultMutableTreeNode groupNode, RequestGroup group) {
        tabOpenController.showOrCreateGroupTab(groupNode, group);
    }

    /**
     * 关闭当前标签页
     */
    public void closeCurrentTab() {
        tabCloseController.closeCurrentTab();
    }

    /**
     * 关闭其他标签页
     */
    public void closeOtherTabs() {
        tabCloseController.closeOtherTabs();
    }

    /**
     * 关闭所有标签页
     */
    public void closeAllTabs() {
        tabCloseController.closeAllTabs();
    }

    /**
     * 单击保存的响应：在临时 Tab 中显示
     */
    public void showOrCreateTransientTabForSavedResponse(SavedResponse savedResponse) {
        tabOpenController.showOrCreateTransientSavedResponse(savedResponse);
    }

    /**
     * 双击保存的响应：在固定 Tab 中显示
     */
    public void showOrCreateTabForSavedResponse(SavedResponse savedResponse) {
        tabOpenController.showOrCreateSavedResponseTab(savedResponse);
    }

    // ==================== Helper Methods ====================

    public void removeTabAtWithCleanup(int index) {
        tabRemovalController.removeAt(index);
    }

    /**
     * 更新所有打开标签页的布局方向
     *
     * @param isVertical true=垂直布局，false=水平布局
     */
    public void updateAllTabsLayout(boolean isVertical) {
        tabStateController.updateAllRequestLayouts(isVertical);
    }

    public void updateAllRequestEditorTabsVisibility() {
        tabStateController.updateAllRequestEditorTabsVisibility();
    }

    public void updateRequestEditorTabsLayoutPolicy() {
        if (tabbedPane == null) {
            return;
        }
        tabbedPane.setTabLayoutPolicy(requestTabsLayoutPolicy());
        tabbedPane.revalidate();
        tabbedPane.repaint();
    }

    /**
     * Snapshot used by the global right-side request assistant.
     */
    public HttpRequestItem getCurrentRequestSnapshotForAssistant() {
        if (tabStateController == null) {
            return null;
        }
        RequestEditSubPanel currentTab = tabStateController.currentRequestTab();
        if (currentTab == null || currentTab.isSavedResponseTab()
                || currentTab.getPanelType() == RequestEditSubPanelType.PERFORMANCE_SNAPSHOT) {
            return null;
        }
        HttpRequestItem request = currentTab.getCurrentRequest();
        if (request == null || request.getProtocol() == null) {
            return request;
        }
        return request.getProtocol().isHttpProtocol() ? request : null;
    }

}
