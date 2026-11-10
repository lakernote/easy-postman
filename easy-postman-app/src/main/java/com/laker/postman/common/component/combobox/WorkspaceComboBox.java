package com.laker.postman.common.component.combobox;

import com.laker.postman.model.Workspace;
import com.laker.postman.service.WorkspaceService;
import lombok.Setter;

import java.util.List;
import java.util.function.Consumer;

/**
 * 工作区下拉框组件
 */
public class WorkspaceComboBox extends MenuBarComboBox<Workspace> {
    private boolean isUpdating = false;
    @Setter
    private Consumer<Workspace> onWorkspaceChange;

    public WorkspaceComboBox() {
        setRenderer(new WorkspaceItemRenderer());
        addActionListener(e -> {
            if (isUpdating) return;
            Workspace workspace = (Workspace) getSelectedItem();
            if (workspace != null && onWorkspaceChange != null) {
                onWorkspaceChange.accept(workspace);
            }
        });
        reload();
    }

    /**
     * 重新加载工作区列表
     */
    public void reload() {
        isUpdating = true;
        removeAllItems();

        try {
            WorkspaceService workspaceService = WorkspaceService.getInstance();
            List<Workspace> workspaces = workspaceService.getAllWorkspaces();
            Workspace currentWorkspace = workspaceService.getCurrentWorkspace();

            for (Workspace workspace : workspaces) {
                addItem(workspace);
            }

            // 设置当前选中的工作区
            if (currentWorkspace != null) {
                setSelectedItem(currentWorkspace);
            } else if (getItemCount() > 0) {
                setSelectedIndex(0);
            }
        } catch (Exception e) {
            // 加载失败时静默处理
        } finally {
            isUpdating = false;
        }
    }
}
