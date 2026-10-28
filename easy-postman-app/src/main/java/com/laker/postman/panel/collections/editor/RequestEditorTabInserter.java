package com.laker.postman.panel.collections.editor;

import cn.hutool.core.text.CharSequenceUtil;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.tab.ClosableTabComponent;
import com.laker.postman.panel.collections.editor.request.RequestEditSubPanel;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import javax.swing.JTabbedPane;
import java.awt.Component;

@UtilityClass
public class RequestEditorTabInserter {

    public static RequestEditSubPanel insertRequestTab(
            HttpRequestItem item,
            boolean selectTab,
            boolean deferEditorInitialization) {
        String id = item.getId();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Request item ID cannot be null or empty");
        }

        new RequestEditorExecutionScopeSynchronizer().syncScopeForRequest(id);

        RequestEditSubPanel subPanel = new RequestEditSubPanel(id, item.getProtocol(), deferEditorInitialization);
        subPanel.initPanelData(item);
        HttpRequestItem tabRequest = subPanel.getCurrentRequest();
        String tabTitle = CharSequenceUtil.isNotBlank(item.getName())
                ? item.getName()
                : I18nUtil.getMessage(MessageKeys.NEW_REQUEST);
        RequestEditorPanel requestEditPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
        JTabbedPane tabbedPane = requestEditPanel.getTabbedPane();
        int insertIndex = tabbedPane.getTabCount();
        if (insertIndex > 0 && RequestEditorPanel.PLUS_TAB.equals(tabbedPane.getTitleAt(insertIndex - 1))) {
            insertIndex--;
        }
        tabbedPane.insertTab(tabTitle, null, subPanel, null, insertIndex);
        tabbedPane.setTabComponentAt(insertIndex, ClosableTabComponent.forRequest(tabTitle, tabRequest));
        boolean shouldSelectInsertedTab = selectTab;
        if (shouldSelectInsertedTab && deferEditorInitialization) {
            shouldSelectInsertedTab = requestEditPanel.isStartupRestoreSelectingLastTab();
        }
        if (shouldSelectInsertedTab) {
            tabbedPane.setSelectedIndex(insertIndex);
            if (!deferEditorInitialization) {
                requestEditPanel.setAutoInitializeSelectedTabOnTabAdd(true);
                requestEditPanel.initializeSelectedTabSoon();
            }
        }
        return subPanel;
    }

    public static void setTabNewRequestMarker(RequestEditSubPanel panel, boolean isNew) {
        JTabbedPane tabbedPane = UiSingletonFactory.getInstance(RequestEditorPanel.class).getTabbedPane();
        int idx = tabbedPane.indexOfComponent(panel);
        if (idx < 0) {
            return;
        }
        Component tabComp = tabbedPane.getTabComponentAt(idx);
        if (tabComp instanceof ClosableTabComponent closable) {
            closable.updateMarkers(markers -> markers.withNewRequest(isNew));
        }
    }
}
