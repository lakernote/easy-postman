package com.laker.postman.panel.collections;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.panel.collections.editor.RequestEditorTabInserter;
import com.laker.postman.panel.collections.editor.request.RequestEditSubPanel;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreeQueries;
import com.laker.postman.request.model.HttpRequestItem;
import lombok.experimental.UtilityClass;

import javax.swing.*;
import java.util.List;

@UtilityClass
public class OpenedRequestTabSessionRestorer {

    private static volatile boolean startupRestoreCompleted;

    /**
     * 启动恢复完成后，磁盘快照保留到退出时覆盖写入，崩溃或强制结束后下次仍能恢复。
     */
    public static void markStartupRestoreCompleted() {
        startupRestoreCompleted = true;
    }

    public static boolean isStartupRestoreCompleted() {
        return startupRestoreCompleted;
    }

    public static void restoreOpenedRequests(List<HttpRequestItem> requestItems, Runnable onComplete) {
        Runnable restoreTask = () -> {
            CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
            List<HttpRequestItem> restorableRequests = SwingCollectionTreeQueries.resolveRestorableOpenedRequests(
                    requestItems,
                    leftPanel.getRootTreeNode()
            );
            for (int i = 0; i < restorableRequests.size(); i++) {
                HttpRequestItem item = restorableRequests.get(i);
                boolean selectTab = i == restorableRequests.size() - 1;
                // 选中标签直接构建真实编辑器：先画骨架再替换会出现“骨架 → 卡顿 → 淡出”的二次刷新
                RequestEditSubPanel panel = RequestEditorTabInserter.insertRequestTab(item, selectTab, !selectTab);
                RequestEditorTabInserter.setTabNewRequestMarker(panel, item.isNewRequest());
            }
            if (onComplete != null) {
                onComplete.run();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            restoreTask.run();
        } else {
            SwingUtilities.invokeLater(restoreTask);
        }
    }
}
