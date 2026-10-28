package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.http.execution.RequestPreparationResult;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.request.AppRequestHeaderDefaults;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.service.setting.SettingManager;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 请求编辑器运行时控制器集合。
 * <p>
 * 核心点：发送、取消、协议分发、流式连接、响应回填和布局联动都属于运行态编排，
 * 不放在 RequestEditSubPanel 的视图初始化代码里。
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class RequestEditorRuntimeController {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final RequestExecutionState executionState;
    private final RequestDirtyStateTracker dirtyStateTracker;
    private final RequestEditorCommandController commandController;
    private final RequestSendCoordinator sendCoordinator;
    private final RequestSplitLayoutController splitLayoutController;

    static RequestEditorRuntimeController create(Config config) {
        RequestViewComponents view = config.view();
        RequestExecutionState executionState = new RequestExecutionState();
        RequestDirtyStateTracker dirtyStateTracker = new RequestDirtyStateTracker(
                config.currentRequestFromModelSupplier(),
                config.tabDirtyUpdater(),
                AppRequestHeaderDefaults.generatedHeaderPolicy()
        );
        RequestExecutionUiUpdater executionUiUpdater = new RequestExecutionUiUpdater(
                view.responsePanel,
                view.requestLinePanel,
                view.requestBodyPanel,
                view.reqTabs,
                config.sendAction(),
                config.baseHttpProtocolSupplier(),
                config.effectiveHttpProtocolSupplier(),
                config.effectiveWebSocketProtocolSupplier()
        );
        RequestStreamUiAppender streamUiAppender = new RequestStreamUiAppender(view.responsePanel, TIME_FORMATTER);
        RequestEditorCommandController commandController = new RequestEditorCommandController(
                view.urlField,
                view.requestBodyPanel,
                view.requestLinePanel,
                streamUiAppender,
                view.responsePanel,
                config.sendAction(),
                () -> config.baseHttpProtocolSupplier().getAsBoolean(),
                () -> config.effectiveWebSocketProtocolSupplier().getAsBoolean(),
                executionState,
                new RequestCurlImportController(view.urlField, config.requestImporter())
        );
        RequestResponseHandler responseHandler = new RequestResponseHandler(
                config.owner(),
                view.responsePanel,
                view.responsePanel::setTestResults,
                config.exchangeRecorder()
        );
        HttpRequestExecutor httpExecutor = new HttpRequestExecutor(
                view.responsePanel,
                executionUiUpdater,
                streamUiAppender,
                responseHandler,
                executionState
        );
        WebSocketRequestExecutor webSocketExecutor = new WebSocketRequestExecutor(
                view.responsePanel,
                executionUiUpdater,
                streamUiAppender,
                responseHandler,
                executionState
        );
        RequestProtocolDispatcher protocolDispatcher = new RequestProtocolDispatcher(
                view.responsePanel,
                httpExecutor,
                webSocketExecutor,
                executionState,
                config.maxRedirectCount()
        );
        RequestSendCoordinator sendCoordinator = new RequestSendCoordinator(
                executionState,
                commandController::cancelCurrentRequest,
                view.urlField,
                config.preparationFeedbackPresenter(),
                view.requestLinePanel,
                config.sendAction(),
                view.responsePanel,
                config.preparationSupplier(),
                executionUiUpdater::updateUIForRequesting,
                protocolDispatcher::dispatch
        );
        RequestSplitLayoutController splitLayoutController = new RequestSplitLayoutController(
                view.splitPane,
                view.responsePanel,
                () -> config.effectiveWebSocketProtocolSupplier().getAsBoolean()
        );
        applyInitialSplitLayout(view.splitPane, splitLayoutController);
        return new RequestEditorRuntimeController(
                executionState,
                dirtyStateTracker,
                commandController,
                sendCoordinator,
                splitLayoutController
        );
    }

    RequestDirtyStateTracker dirtyStateTracker() {
        return dirtyStateTracker;
    }

    void updateTabDirty() {
        dirtyStateTracker.updateTabDirty();
    }

    void sendRequest() {
        sendCoordinator.sendRequest();
    }

    void sendWebSocketMessage() {
        commandController.sendWebSocketMessage();
    }

    void disposeOpenConnections() {
        executionState.disposeOpenConnections();
    }

    void autoPrependProtocolIfNeeded() {
        commandController.autoPrependProtocolIfNeeded();
    }

    void detectAndParseCurl(boolean loadingData) {
        commandController.detectAndParseCurl(loadingData);
    }

    void updateLayoutOrientation(boolean vertical) {
        splitLayoutController.updateLayoutOrientation(vertical);
    }

    void handleInitialLayout() {
        splitLayoutController.handleInitialLayout();
    }

    private static void applyInitialSplitLayout(JSplitPane splitPane, RequestSplitLayoutController splitLayoutController) {
        boolean vertical = SettingManager.isLayoutVertical();
        double initialRatio = vertical ? splitLayoutController.getDefaultResizeWeight() : 0.5;
        splitPane.setResizeWeight(initialRatio);
    }

    record Config(Component owner,
                  RequestViewComponents view,
                  RequestPreparationFeedbackPresenter preparationFeedbackPresenter,
                  Supplier<RequestPreparationResult> preparationSupplier,
                  ActionListener sendAction,
                  BooleanSupplier baseHttpProtocolSupplier,
                  BooleanSupplier effectiveHttpProtocolSupplier,
                  BooleanSupplier effectiveWebSocketProtocolSupplier,
                  Consumer<HttpRequestItem> requestImporter,
                  Supplier<HttpRequestItem> currentRequestFromModelSupplier,
                  Consumer<Boolean> tabDirtyUpdater,
                  BiConsumer<PreparedRequest, HttpResponse> exchangeRecorder,
                  int maxRedirectCount) {
    }
}
