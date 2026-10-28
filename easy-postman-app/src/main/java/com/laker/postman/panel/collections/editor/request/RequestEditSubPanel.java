package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.panel.collections.editor.CollectionTreeEditorGateway;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.panel.collections.editor.request.sub.*;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.SavedResponse;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * 单个请求编辑子面板，包含 URL、方法选择、Headers、Body 和响应展示
 */
public class RequestEditSubPanel extends JPanel {
    private static final int MAX_REDIRECT_COUNT = 10;

    private final RequestEditorDataController dataController;
    private final RequestItemProtocolEnum protocol;
    private final boolean deferEditorInitialization;
    @Getter
    private boolean editorInitialized;
    private Boolean pendingLayoutVertical;
    private final transient RequestEditorDeferredInitializationController deferredInitializationController;

    @Getter
    private final RequestEditSubPanelType panelType;

    @Getter
    private final SavedResponse savedResponse;
    private final HttpRequestItem savedResponseOwner;

    private RequestViewComponents view;
    // 主面板只保留“编排”和“状态持有”，具体行为拆给各个职责明确的协作者，避免再次演变成巨型类。
    private final RequestPreparationFeedbackPresenter requestPreparationFeedbackPresenter = new RequestPreparationFeedbackPresenter();
    private final RequestEditorSendPreparationController sendPreparationController =
            RequestEditorSendPreparationController.createDefault(
                    this::ensureEditorInitialized,
                    this::pinTransientTab,
                    () -> view.requestSettingsPanel.validateSettings(),
                    this::getCurrentRequest
            );
    private RequestEditorBinder requestEditorBinder;
    private RequestEditorDefaultTabSelector requestEditorDefaultTabSelector;
    private RequestEditorTabsVisibilityController requestEditorTabsVisibilityController;
    private RequestTabStateController requestTabStateController;
    private RequestUrlParamsSynchronizer requestUrlParamsSynchronizer;
    private RequestEditorRuntimeController runtimeController;
    private final SavedResponseUiController savedResponseUiController = new SavedResponseUiController(new CollectionTreeEditorGateway());
    private RequestSavedResponseController savedResponseController;

    // 数据加载标志，防止加载时触发自动保存和联动更新
    private boolean isLoadingData = false;

    // 保存最后一次请求和响应，用于保存响应功能
    private PreparedRequest lastRequest;
    private HttpResponse lastResponse;

    private RequestItemProtocolEnum getEffectiveProtocol() {
        return dataController.effectiveProtocol();
    }

    private boolean isBaseHttpProtocol() {
        return protocol != null && protocol.isHttpProtocol();
    }

    private boolean isEffectiveHttpProtocol() {
        return getEffectiveProtocol() != null && getEffectiveProtocol().isHttpProtocol();
    }

    private boolean isEffectiveWebSocketProtocol() {
        return getEffectiveProtocol() != null && getEffectiveProtocol().isWebSocketProtocol();
    }

    /**
     * 判断当前面板是否是保存的响应标签页
     */
    public boolean isSavedResponseTab() {
        return panelType == RequestEditSubPanelType.SAVED_RESPONSE;
    }

    public boolean saveSavedResponseRequest() {
        if (!isSavedResponseTab() || savedResponseOwner == null || savedResponse == null) {
            return false;
        }
        HttpRequestItem editedRequest = getCurrentRequest();
        boolean saved = savedResponseUiController.updateExampleRequest(
                savedResponseOwner.getId(), savedResponse, editedRequest);
        if (saved) {
            dataController.setOriginalRequestItem(editedRequest);
        }
        return saved;
    }

    private SavedResponse responseExampleForSave() {
        return savedResponse;
    }

    private HttpRequestItem responseExampleOwnerForSave() {
        return savedResponseOwner;
    }

    private boolean isPerformanceSnapshot() {
        return panelType == RequestEditSubPanelType.PERFORMANCE_SNAPSHOT;
    }

    /**
     * 普通请求编辑面板构造函数
     */
    public RequestEditSubPanel(String id, RequestItemProtocolEnum protocol) {
        this(id, protocol, false);
    }

    public RequestEditSubPanel(String id, RequestItemProtocolEnum protocol, boolean deferEditorInitialization) {
        this(id, protocol, RequestEditSubPanelType.NORMAL, null, deferEditorInitialization);
    }

    public static RequestEditSubPanel performanceSnapshot(String id,
                                                          RequestItemProtocolEnum protocol,
                                                          boolean deferEditorInitialization) {
        return new RequestEditSubPanel(
                id,
                protocol,
                RequestEditSubPanelType.PERFORMANCE_SNAPSHOT,
                null,
                deferEditorInitialization
        );
    }

    /**
     * 保存的响应面板构造函数
     */
    public RequestEditSubPanel(SavedResponse savedResponse) {
        this(savedResponse, null);
    }

    public RequestEditSubPanel(SavedResponse savedResponse, HttpRequestItem owner) {
        this(savedResponse.getId(), RequestItemProtocolEnum.HTTP, RequestEditSubPanelType.SAVED_RESPONSE,
                savedResponse, owner, false);
    }

    /**
     * 完整构造函数
     */
    private RequestEditSubPanel(String id, RequestItemProtocolEnum protocol, RequestEditSubPanelType panelType,
                                SavedResponse savedResponse,
                                boolean deferEditorInitialization) {
        this(id, protocol, panelType, savedResponse, null, deferEditorInitialization);
    }

    private RequestEditSubPanel(String id, RequestItemProtocolEnum protocol, RequestEditSubPanelType panelType,
                                SavedResponse savedResponse, HttpRequestItem savedResponseOwner,
                                boolean deferEditorInitialization) {
        this.protocol = protocol == null ? RequestItemProtocolEnum.HTTP : protocol;
        this.panelType = panelType;
        this.savedResponse = savedResponse;
        this.savedResponseOwner = savedResponseOwner;
        this.deferEditorInitialization = deferEditorInitialization && panelType != RequestEditSubPanelType.SAVED_RESPONSE;
        this.dataController = new RequestEditorDataController(
                id,
                this.protocol,
                () -> editorInitialized,
                this::isPerformanceSnapshot,
                loading -> isLoadingData = loading,
                this::updateTabIndicators
        );
        this.deferredInitializationController = new RequestEditorDeferredInitializationController(
                this,
                () -> this.deferEditorInitialization && !editorInitialized,
                () -> ensureEditorInitialized(true)
        );
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); // 设置边距为5
        ToolWindowSurfaceStyle.applyCard(this);
        if (!this.deferEditorInitialization) {
            initializeEditorUiIfNeeded(false);
        } else {
            deferredInitializationController.installPlaceholder();
        }
    }

    public void ensureEditorInitialized() {
        ensureEditorInitialized(false);
    }

    public void ensureEditorInitialized(boolean animatePlaceholderTransition) {
        // 占位页升级为真实编辑器时允许做局部淡出，但过渡层只能是 layeredPane 上的纯绘制快照。
        // 这样既保留平滑感，也不影响底层 JSplitPane 的 hover / resize cursor。
        if (editorInitialized) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            initializeEditorUiIfNeeded(animatePlaceholderTransition);
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> initializeEditorUiIfNeeded(animatePlaceholderTransition));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize RequestEditSubPanel on EDT", e);
        }
    }

    public String getId() {
        return dataController.requestId();
    }

    public RequestLinePanel getRequestLinePanel() {
        ensureEditorInitialized();
        return view.requestLinePanel;
    }

    private void initializeEditorUiIfNeeded(boolean animatePlaceholderTransition) {
        if (editorInitialized) {
            return;
        }
        var placeholderSnapshot = animatePlaceholderTransition
                ? deferredInitializationController.capturePlaceholderSnapshot()
                : null;
        removeAll();
        view = RequestViewFactory.create(protocol, panelType, this::sendRequest);
        add(view.requestLinePanel, BorderLayout.NORTH);
        createEditorCollaborators();
        bindBasicEditorInteractions();

        if (!isPerformanceSnapshot()) {
            createRuntimeController();
        } else {
            RequestEditorReadOnlyMode.apply(view);
        }

        finishEditorUiInitialization();

        dataController.populatePendingRequestIfPresent();
        if (pendingLayoutVertical != null) {
            if (runtimeController != null) {
                runtimeController.updateLayoutOrientation(pendingLayoutVertical);
            }
            pendingLayoutVertical = null;
        }
        revalidate();
        repaint();
        deferredInitializationController.startTransition(placeholderSnapshot);
    }

    private void bindBasicEditorInteractions() {
        if (!isPerformanceSnapshot()) {
            RequestEditorUiBinder.bindUrlField(
                    view.urlField,
                    requestPreparationFeedbackPresenter,
                    this::detectAndParseCurl,
                    () -> requestUrlParamsSynchronizer.syncUrlToParams(isLoadingData),
                    this::autoPrependHttpsIfNeeded
            );
            RequestEditorUiBinder.bindParamsSync(view.paramsPanel, () -> requestUrlParamsSynchronizer.syncParamsToUrl(isLoadingData));
        }
    }

    private void createEditorCollaborators() {
        requestUrlParamsSynchronizer = new RequestUrlParamsSynchronizer(view.urlField, view.paramsTabPanel, view.paramsPanel);
        view.requestLinePanel.setPathVariablesSupplier(view.paramsTabPanel::getPathVariablesListFromModel);
        requestEditorBinder = new RequestEditorBinder(view);
        requestEditorDefaultTabSelector = new RequestEditorDefaultTabSelector(view);
        dataController.bindEditor(
                requestEditorBinder,
                requestEditorDefaultTabSelector,
                () -> runtimeController != null ? runtimeController.dirtyStateTracker() : null,
                view.requestSettingsPanel
        );
        requestEditorTabsVisibilityController = new RequestEditorTabsVisibilityController(
                protocol,
                view,
                this::updateTabIndicators
        );
        requestTabStateController = new RequestTabStateController(protocol, view);
    }

    private void createRuntimeController() {
        runtimeController = RequestEditorRuntimeController.create(new RequestEditorRuntimeController.Config(
                this,
                view,
                requestPreparationFeedbackPresenter,
                sendPreparationController::prepareForSending,
                this::sendRequest,
                this::isBaseHttpProtocol,
                this::isEffectiveHttpProtocol,
                this::isEffectiveWebSocketProtocol,
                this::initPanelData,
                this::getCurrentRequestFromModel,
                this::updateParentTabDirty,
                this::recordExchange,
                MAX_REDIRECT_COUNT
        ));
    }

    private void finishEditorUiInitialization() {
        add(view.editorContent, BorderLayout.CENTER);
        RequestEditorUiBinder.applyInitialProtocolUi(
                protocol,
                view.reqTabs,
                view.requestBodyPanel,
                view.paramsTabPanel,
                view.authTabPanel,
                e -> sendWebSocketMessage()
        );
        if (!isPerformanceSnapshot()) {
            requestTabStateController.bindListeners(
                    this::updateTabDirty,
                    isSavedResponseTab() ? () -> {
                    } : this::updateParentTabMethod
            );
        }

        SwingUtilities.invokeLater(this::updateTabIndicators);
        RequestEditorUiBinder.bindSaveResponseButton(protocol, panelType, view.responsePanel,
                e -> getSavedResponseController().showSaveDialog());
        if (!isPerformanceSnapshot()) {
            RequestEditorUiBinder.bindBodyTypeHeaderSync(view.requestBodyPanel, view.headersPanel, () -> isLoadingData);
        }
        editorInitialized = true;
    }

    /**
     * 更新所有tab的内容指示器
     */
    private void updateTabIndicators() {
        if (requestTabStateController == null) {
            return;
        }
        requestTabStateController.updateTabIndicators();
    }


    /**
     * 设置原始请求数据（脏数据检测）
     */
    public void setOriginalRequestItem(HttpRequestItem item) {
        dataController.setOriginalRequestItem(item);
    }

    public HttpRequestItem getOriginalRequestItem() {
        return dataController.originalRequestItem();
    }

    /**
     * 判断当前表单内容是否被修改（与原始请求对比）
     * 注意：比较时排除 response 字段，因为它是历史响应数据，不属于表单编辑内容
     */
    public boolean isModified() {
        return dataController.isModified();
    }

    /**
     * 从 tableModel 直接读取数据构建 HttpRequestItem，不调用 stopCellEditing。
     * 专供 isModified() / updateTabDirty() 等后台比较场景使用，
     * 避免在 TableModelListener 回调中打断用户正在进行的单元格编辑。
     */
    private HttpRequestItem getCurrentRequestFromModel() {
        return dataController.currentRequestFromModel();
    }

    /**
     * 检查脏状态并更新tab标题
     */
    private void updateTabDirty() {
        if (runtimeController == null) {
            return;
        }
        runtimeController.updateTabDirty();
    }

    private void updateParentTabProtocol(RequestItemProtocolEnum newProtocol) {
        updateParentTabDisplay(newProtocol);
    }

    private void updateParentTabMethod() {
        updateParentTabDisplay(getEffectiveProtocol());
    }

    private void updateParentTabDisplay(RequestItemProtocolEnum protocol) {
        if (isSavedResponseTab() || isPerformanceSnapshot() || isLoadingData || view == null || view.methodBox == null) {
            return;
        }
        UiSingletonFactory.getInstance(RequestEditorPanel.class)
                .updateTabDisplay(this, currentMethodForTabDisplay(), protocol);
    }

    private String currentMethodForTabDisplay() {
        Object selectedMethod = view.methodBox.getSelectedItem();
        return selectedMethod instanceof String method ? method : null;
    }

    private void updateParentTabDirty(boolean dirty) {
        UiSingletonFactory.getInstance(RequestEditorPanel.class).updateTabDirty(this, dirty);
    }

    private void recordExchange(PreparedRequest request, HttpResponse response) {
        lastRequest = request;
        lastResponse = response;
    }

    private void sendRequest(ActionEvent e) {
        if (runtimeController == null) {
            return;
        }
        runtimeController.sendRequest();
    }

    public String validateRequestSettings() {
        return sendPreparationController.validateRequestSettings();
    }

    private void pinTransientTab() {
        SwingUtilities.invokeLater(() -> UiSingletonFactory.getInstance(RequestEditorPanel.class).pinTransientTab());
    }

    // WebSocket消息发送逻辑
    private void sendWebSocketMessage() {
        if (runtimeController != null) {
            runtimeController.sendWebSocketMessage();
        }
    }

    /**
     * 更新表单内容（用于切换请求或保存后刷新）
     */
    public void initPanelData(HttpRequestItem item) {
        dataController.initPanelData(item);
        if (item != null) {
            updateParentTabDisplay(item.getProtocol() != null ? item.getProtocol() : getEffectiveProtocol());
        }
    }

    /**
     * 获取当前表单内容封装为HttpRequestItem
     */
    public HttpRequestItem getCurrentRequest() {
        return dataController.currentRequest();
    }

    /**
     * 关闭标签页前释放网络资源，避免后台连接在 UI 被移除后继续存活。
     */
    public void disposeResources() {
        if (runtimeController != null) {
            runtimeController.disposeOpenConnections();
        }
        if (!editorInitialized) {
            dataController.clearPending();
        }
    }

    /**
     * 如果 URL 没有协议，按当前协议和用户配置补全。
     */
    private void autoPrependHttpsIfNeeded() {
        if (runtimeController == null) {
            return;
        }
        runtimeController.autoPrependProtocolIfNeeded();
    }

    private RequestSavedResponseController getSavedResponseController() {
        if (savedResponseController == null) {
            savedResponseController = new RequestSavedResponseController(
                    this,
                    savedResponseUiController,
                    () -> lastRequest,
                    () -> lastResponse,
                    this::getOriginalRequestItem,
                    this::responseExampleForSave,
                    this::responseExampleOwnerForSave,
                    requestEditorBinder,
                    requestEditorDefaultTabSelector,
                    this::getEffectiveProtocol,
                    loading -> isLoadingData = loading,
                    view.responsePanel,
                    view.requestLinePanel,
                    this::sendRequest
            );
        }
        return savedResponseController;
    }

    /**
     * 加载保存的响应到面板
     */
    public void loadSavedResponse(SavedResponse savedResponse) {
        if (savedResponse == null) {
            return;
        }
        ensureEditorInitialized();
        getSavedResponseController().loadSavedResponse(savedResponse);
    }

    /**
     * 动态更新布局方向（用于全局布局切换）
     *
     * @param isVertical true=垂直布局，false=水平布局
     */
    public void updateLayoutOrientation(boolean isVertical) {
        if (!editorInitialized) {
            pendingLayoutVertical = isVertical;
            return;
        }
        if (runtimeController == null) {
            return;
        }
        runtimeController.updateLayoutOrientation(isVertical);
    }

    public void updateRequestEditorTabsVisibility() {
        if (!editorInitialized || requestEditorTabsVisibilityController == null) {
            return;
        }
        requestEditorTabsVisibilityController.updateVisibility();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        if (editorInitialized && runtimeController != null) {
            runtimeController.handleInitialLayout();
        }
    }

    /**
     * 检测并解析 cURL 命令
     */
    private void detectAndParseCurl() {
        ensureEditorInitialized();
        if (runtimeController != null) {
            runtimeController.detectAndParseCurl(isLoadingData);
        }
    }
}
