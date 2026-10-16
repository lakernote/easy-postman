package com.laker.postman.panel.collections.editor;

import cn.hutool.core.util.IdUtil;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.panel.collections.editor.request.RequestEditSubPanel;
import com.laker.postman.panel.collections.tree.RequestNameSelection;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 请求编辑器保存控制器。
 * <p>
 * 保存动作需要协调当前 Tab、集合树、命名弹窗和错误提示。放在控制层后，RequestEditorPanel 不再直接承载保存流程细节。
 */
@Slf4j
@RequiredArgsConstructor
final class RequestEditorSaveController {
    private final Component dialogParent;
    private final Supplier<RequestEditSubPanel> currentSubPanelSupplier;
    private final Runnable transientTabPinAction;
    private final BiFunction<TreeModel, String, Optional<RequestNameSelection>> requestNameChooser;
    private final BiConsumer<String, HttpRequestItem> newRequestTabRefresher;
    private final CollectionTreeEditorGateway collectionGateway;
    private final RequestEditorSaveCoordinator saveCoordinator = new RequestEditorSaveCoordinator();

    boolean saveCurrentRequest() {
        RequestEditSubPanel currentSubPanel = currentSubPanelSupplier.get();
        return saveCoordinator.saveCurrentRequest(new RequestEditorSaveCoordinator.SaveContext() {
            @Override
            public boolean isSavedResponseTab() {
                return currentSubPanel != null && currentSubPanel.isSavedResponseTab();
            }

            @Override
            public boolean saveSavedResponseRequest() {
                return currentSubPanel != null && currentSubPanel.saveSavedResponseRequest();
            }

            @Override
            public void pinTransientTab() {
                transientTabPinAction.run();
            }

            @Override
            public String validateRequestSettings() {
                return currentSubPanel != null ? currentSubPanel.validateRequestSettings() : null;
            }

            @Override
            public void showSettingsValidationError(String error) {
                NotificationCenter.showError(error);
            }

            @Override
            public HttpRequestItem currentRequest() {
                return currentSubPanel != null ? currentSubPanel.getCurrentRequest() : null;
            }

            @Override
            public void onNoRequestToSave() {
                log.warn("没有可保存的请求");
            }

            @Override
            public TreeModel groupTreeModel() {
                return collectionGateway.groupTreeModel();
            }

            @Override
            public Optional<RequestNameSelection> chooseGroupAndRequestName(
                    TreeModel groupTreeModel,
                    String defaultName
            ) {
                return requestNameChooser.apply(groupTreeModel, defaultName);
            }

            @Override
            public String newRequestId() {
                return IdUtil.simpleUUID();
            }

            @Override
            public void saveRequestToGroup(RequestGroup group, HttpRequestItem item) {
                collectionGateway.saveRequestToGroup(group, item);
            }

            @Override
            public void refreshNewRequestTab(String requestName, HttpRequestItem item) {
                newRequestTabRefresher.accept(requestName, item);
            }

            @Override
            public boolean updateExistingRequest(HttpRequestItem item) {
                return collectionGateway.updateExistingRequest(item);
            }

            @Override
            public void showUpdateExistingRequestFailed(HttpRequestItem item) {
                log.error("更新请求失败: {}", item.getId() + " - " + item.getName());
                JOptionPane.showMessageDialog(
                        dialogParent,
                        I18nUtil.getMessage(MessageKeys.UPDATE_REQUEST_FAILED),
                        I18nUtil.getMessage(MessageKeys.ERROR),
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
