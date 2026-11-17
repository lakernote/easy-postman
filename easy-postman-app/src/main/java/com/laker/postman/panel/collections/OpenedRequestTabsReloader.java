package com.laker.postman.panel.collections;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.request.model.HttpRequestItem;
import lombok.experimental.UtilityClass;

import javax.swing.JTabbedPane;
import java.util.List;

/**
 * 从磁盘重新加载集合时保留已打开的请求标签：标签按 ID 以新集合内容重新打开，已删除的请求不再恢复。
 */
@UtilityClass
public class OpenedRequestTabsReloader {

    public static boolean hasUnsavedTabs() {
        return UiSingletonFactory.getExistingInstance(RequestEditorPanel.class)
                .map(panel -> !OpenedRequestTabSnapshotCollector.findUnsavedTabs(panel.getTabbedPane()).isEmpty())
                .orElse(false);
    }

    public static void reloadCollectionsKeepingTabs(String collectionFilePath, Runnable onComplete) {
        List<HttpRequestItem> openedRequests = UiSingletonFactory.getExistingInstance(RequestEditorPanel.class)
                .map(RequestEditorPanel::getTabbedPane)
                .map(OpenedRequestTabsReloader::collectOpenedRequests)
                .orElse(List.of());
        UiSingletonFactory.getInstance(CollectionTreePanel.class).switchWorkspaceAndRefreshUI(collectionFilePath, () -> {
            if (openedRequests.isEmpty()) {
                runIfPresent(onComplete);
                return;
            }
            OpenedRequestTabSessionRestorer.restoreOpenedRequests(openedRequests, () -> runIfPresent(onComplete));
        });
    }

    private static List<HttpRequestItem> collectOpenedRequests(JTabbedPane tabbedPane) {
        return OpenedRequestTabSnapshotCollector.collectOpenedRequestItems(tabbedPane, false);
    }

    private static void runIfPresent(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}
