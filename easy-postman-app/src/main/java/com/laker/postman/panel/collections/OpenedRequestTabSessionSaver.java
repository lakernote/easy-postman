package com.laker.postman.panel.collections;

import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.exception.CancelException;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.service.collections.OpenedRequestTabsStore;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class OpenedRequestTabSessionSaver {

    public static void saveOpenTabsOnExit() {
        // 启动壳显示期间请求编辑器尚未创建，不能为了退出而创建空编辑器并覆盖上次会话。
        RequestEditorPanel editPanel = UiSingletonFactory.getExistingInstance(RequestEditorPanel.class).orElse(null);
        if (editPanel == null) {
            return;
        }
        JTabbedPane tabbedPane = editPanel.getTabbedPane();
        List<Integer> unsavedTabs = OpenedRequestTabSnapshotCollector.findUnsavedTabs(tabbedPane);

        boolean saveAll = false;
        if (!unsavedTabs.isEmpty()) {
            int result = showUnsavedChangesDialog();
            if (result == 2 || result == JOptionPane.CLOSED_OPTION) {
                throw new CancelException();
            }
            if (result == 0) {
                saveUnsavedTabs(editPanel, tabbedPane, unsavedTabs);
                saveAll = true;
            }
        }

        List<HttpRequestItem> openedRequestItems =
                OpenedRequestTabSnapshotCollector.collectOpenedRequestItems(tabbedPane, saveAll);
        // 集合加载完成前，旧会话仍留在磁盘上。先保留它，再合并用户刚打开的标签。
        if (new File(OpenedRequestTabsStore.PATHNAME).isFile()) {
            if (openedRequestItems.isEmpty()) {
                return;
            }
            openedRequestItems = mergeWithPendingSession(OpenedRequestTabsStore.loadAll(), openedRequestItems);
        }
        OpenedRequestTabsStore.saveAll(OpenedRequestTabSnapshotCollector.limitToMostRecent(
                openedRequestItems,
                SettingManager.getMaxOpenedRequestsCount()
        ));
    }

    static List<HttpRequestItem> mergeWithPendingSession(List<HttpRequestItem> pending,
                                                          List<HttpRequestItem> current) {
        List<HttpRequestItem> merged = new ArrayList<>(pending);
        for (HttpRequestItem item : current) {
            String id = item.getId();
            if (id != null && !id.isBlank()) {
                merged.removeIf(old -> id.equals(old.getId()));
            }
            merged.add(item);
        }
        return merged;
    }

    private static int showUnsavedChangesDialog() {
        String[] options = {
                I18nUtil.getMessage(MessageKeys.EXIT_SAVE_ALL),
                I18nUtil.getMessage(MessageKeys.EXIT_DISCARD_ALL),
                I18nUtil.getMessage(MessageKeys.EXIT_CANCEL)
        };
        return JOptionPane.showOptionDialog(UiSingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.EXIT_UNSAVED_CHANGES),
                I18nUtil.getMessage(MessageKeys.EXIT_UNSAVED_CHANGES_TITLE),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);
    }

    static void saveUnsavedTabs(RequestEditorPanel editPanel, JTabbedPane tabbedPane, List<Integer> unsavedTabs) {
        for (Integer i : unsavedTabs) {
            tabbedPane.setSelectedIndex(i);
            if (!editPanel.saveCurrentRequest()) {
                // 保留失败或取消保存的标签供用户继续编辑，不能将其视为已保存后退出。
                throw new CancelException();
            }
        }
    }
}
