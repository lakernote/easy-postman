package com.laker.postman.panel.collections.editor;

import cn.hutool.core.text.CharSequenceUtil;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.common.component.tab.ClosableTabComponent;
import com.laker.postman.panel.collections.editor.request.RequestEditSubPanel;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.SavedResponse;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntPredicate;

/**
 * 请求编辑器 Tab 打开控制器。
 * <p>
 * 临时打开、固定打开、同 ID 查重、临时 Tab 固定和插入到 +Tab 前都是 Tab 行为规则，不放在面板布局类里。
 */
@RequiredArgsConstructor
final class RequestEditorTabOpenController {
    private final JTabbedPane tabbedPane;
    private final RequestEditorTransientTabManager transientTabManager;
    private final RequestEditorExecutionScopeSynchronizer executionScopeSynchronizer;
    private final Runnable beforeOpenAction;
    private final Runnable plusTabRestorer;
    private final IntPredicate plusTabPredicate;
    private final BiFunction<String, RequestItemProtocolEnum, RequestEditSubPanel> newRequestTabCreator;
    private final BiConsumer<HttpRequestItem, RequestEditSubPanel> requestDataInitializer;
    private final String defaultRequestTitle;
    private final CollectionTreeEditorGateway collectionGateway;

    void showOrCreateTransientRequest(HttpRequestItem item) {
        beforeOpenAction.run();
        String requestId = item.getId();
        if (requestId == null || requestId.isEmpty()) {
            RequestEditSubPanel tab = newRequestTabCreator.apply(null, item.getProtocol());
            requestDataInitializer.accept(item, tab);
            return;
        }

        executionScopeSynchronizer.syncScopeForRequest(requestId);
        if (switchToExistingRequestTab(requestId)) {
            return;
        }

        transientTabManager.validate();
        RequestEditSubPanel newPanel = new RequestEditSubPanel(requestId, item.getProtocol());
        newPanel.initPanelData(item);
        transientTabManager.showOrReplace(newPanel, requestTitle(item), item);
    }

    void showOrCreateRequestTab(HttpRequestItem item) {
        beforeOpenAction.run();
        String requestId = item.getId();
        if (requestId == null || requestId.isEmpty()) {
            RequestEditSubPanel tab = newRequestTabCreator.apply(null, item.getProtocol());
            requestDataInitializer.accept(item, tab);
            return;
        }

        executionScopeSynchronizer.syncScopeForRequest(requestId);
        if (transientTabManager.isTransientRequest(requestId)) {
            transientTabManager.pin();
            return;
        }
        if (switchToExistingRequestTab(requestId)) {
            return;
        }

        RequestEditSubPanel subPanel = new RequestEditSubPanel(requestId, item.getProtocol());
        subPanel.initPanelData(item);
        String title = requestTitle(item);
        insertFixedTab(title, subPanel, ClosableTabComponent.forRequest(title, item));
    }

    void showOrCreateTransientGroup(DefaultMutableTreeNode groupNode, RequestGroup group) {
        beforeOpenAction.run();
        String groupId = group.getId();
        if (switchToExistingGroupTab(groupId)) {
            return;
        }

        transientTabManager.validate();
        GroupEditPanel groupEditPanel = createGroupEditPanel(groupNode, group);
        transientTabManager.showOrReplace(groupEditPanel, group.getName(), null, isRootGroup(groupNode));
    }

    void showOrCreateGroupTab(DefaultMutableTreeNode groupNode, RequestGroup group) {
        beforeOpenAction.run();
        String groupId = group.getId();
        if (transientTabManager.isTransientGroup(groupId)) {
            transientTabManager.pin();
            return;
        }
        if (switchToExistingGroupTab(groupId)) {
            return;
        }

        GroupEditPanel groupEditPanel = createGroupEditPanel(groupNode, group);
        insertFixedTab(group.getName(), groupEditPanel,
                new ClosableTabComponent(group.getName(), null, isRootGroup(groupNode)));
    }

    void showOrCreateTransientSavedResponse(SavedResponse savedResponse) {
        beforeOpenAction.run();
        if (savedResponse == null || isBlank(savedResponse.getId())) {
            return;
        }

        String savedResponseId = savedResponse.getId();
        if (switchToExistingRequestTab(savedResponseId)) {
            return;
        }

        transientTabManager.validate();
        RequestEditSubPanel newPanel = new RequestEditSubPanel(
                savedResponse, collectionGateway.findRequestContainingResponse(savedResponseId));
        newPanel.loadSavedResponse(savedResponse);
        transientTabManager.showOrReplace(newPanel, savedResponse.getName(), RequestItemProtocolEnum.SAVED_RESPONSE);
    }

    void showOrCreateSavedResponseTab(SavedResponse savedResponse) {
        beforeOpenAction.run();
        if (savedResponse == null || isBlank(savedResponse.getId())) {
            return;
        }

        String savedResponseId = savedResponse.getId();
        if (transientTabManager.isTransientRequest(savedResponseId)) {
            transientTabManager.pin();
            return;
        }
        if (switchToExistingRequestTab(savedResponseId)) {
            return;
        }

        RequestEditSubPanel newPanel = new RequestEditSubPanel(
                savedResponse, collectionGateway.findRequestContainingResponse(savedResponseId));
        newPanel.loadSavedResponse(savedResponse);
        insertFixedTab(savedResponse.getName(), newPanel,
                new ClosableTabComponent(savedResponse.getName(), RequestItemProtocolEnum.SAVED_RESPONSE, false));
    }

    private GroupEditPanel createGroupEditPanel(DefaultMutableTreeNode groupNode, RequestGroup group) {
        return new GroupEditPanel(groupNode, group, () -> collectionGateway.saveGroupNode(groupNode));
    }

    private void insertFixedTab(String title, Component component, ClosableTabComponent tabComponent) {
        int plusTabIndex = tabbedPane.getTabCount() > 0 ? tabbedPane.getTabCount() - 1 : 0;
        if (!plusTabPredicate.test(plusTabIndex)) {
            plusTabIndex = tabbedPane.getTabCount();
        }
        tabbedPane.insertTab(title, null, component, null, plusTabIndex);
        tabbedPane.setTabComponentAt(plusTabIndex, tabComponent);
        tabbedPane.setSelectedIndex(plusTabIndex);
        plusTabRestorer.run();
    }

    private boolean switchToExistingRequestTab(String id) {
        for (int i = 0; i < tabbedPane.getTabCount() - 1; i++) {
            if (i == transientTabManager.transientTabIndex()) {
                continue;
            }
            Component component = tabbedPane.getComponentAt(i);
            if (component instanceof RequestEditSubPanel subPanel && id.equals(subPanel.getId())) {
                tabbedPane.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    private boolean switchToExistingGroupTab(String groupId) {
        if (isBlank(groupId)) {
            return false;
        }
        for (int i = 0; i < tabbedPane.getTabCount() - 1; i++) {
            if (i == transientTabManager.transientTabIndex()) {
                continue;
            }
            Component component = tabbedPane.getComponentAt(i);
            if (component instanceof GroupEditPanel existingPanel
                    && groupId.equals(existingPanel.getGroup().getId())) {
                tabbedPane.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    private String requestTitle(HttpRequestItem item) {
        return CharSequenceUtil.isNotBlank(item.getName()) ? item.getName() : defaultRequestTitle;
    }

    private boolean isRootGroup(DefaultMutableTreeNode groupNode) {
        return groupNode.getLevel() == 1;
    }

    private boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }
}
