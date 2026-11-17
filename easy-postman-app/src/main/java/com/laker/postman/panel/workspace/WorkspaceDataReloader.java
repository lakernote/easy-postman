package com.laker.postman.panel.workspace;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.mock.app.MockServerManager;
import com.laker.postman.model.Workspace;
import com.laker.postman.panel.collections.OpenedRequestTabsReloader;
import com.laker.postman.panel.env.EnvironmentPanel;
import com.laker.postman.panel.functional.FunctionalPanel;
import com.laker.postman.panel.mock.MockServerPanel;
import com.laker.postman.panel.performance.PerformancePanel;
import com.laker.postman.plugin.git.GitWorkspaceLayout;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.SystemUtil;
import com.laker.postman.workspace.reload.WorkspaceDataChangeDetector;
import com.laker.postman.workspace.reload.WorkspaceDataFile;
import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import java.nio.file.Path;
import lombok.experimental.UtilityClass;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 统一刷新当前工作区的各个数据面板。
 * <p>
 * Git 拉取、切换分支、历史恢复和外部修改都会改变磁盘文件；这里只重新加载受影响的部分，
 * 并在完成后同步外部修改监视器的快照，避免把刚加载的内容再次当作外部修改。
 */
@UtilityClass
public class WorkspaceDataReloader {

    /**
     * 在读取或改写磁盘前把带防抖的面板数据立即落盘。
     */
    public static void saveCurrentWorkspacePanels() {
        UiSingletonFactory.getExistingInstance(FunctionalPanel.class).ifPresent(FunctionalPanel::save);
        UiSingletonFactory.getExistingInstance(PerformancePanel.class).ifPresent(PerformancePanel::save);
    }

    /**
     * 当前工作区的全部数据都从磁盘重新加载，已打开的请求标签按 ID 恢复。
     */
    public static void reloadCurrentWorkspace(Runnable onComplete) {
        reloadCurrentWorkspace(EnumSet.allOf(WorkspaceDataFile.class), onComplete);
    }

    public static void reloadCurrentWorkspace(Set<WorkspaceDataFile> files, Runnable onComplete) {
        Workspace current = WorkspaceService.getInstance().getCurrentWorkspace();
        if (current == null || files == null || files.isEmpty()) {
            runIfPresent(onComplete);
            return;
        }
        Set<WorkspaceDataFile> skipped = unloadableFiles(current, files);
        if (!skipped.isEmpty()) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.WORKSPACE_EXTERNAL_CHANGE_UNREADABLE,
                    WorkspaceExternalChangeMonitor.fileNames(skipped)));
            files = EnumSet.copyOf(files);
            files.removeAll(skipped);
        }
        Set<WorkspaceDataFile> reloadFiles = files;
        if (reloadFiles.contains(WorkspaceDataFile.ENVIRONMENTS)) {
            UiSingletonFactory.getInstance(EnvironmentPanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getEnvPathForWorkspace(current));
        }
        Runnable reloadOthers = () -> {
            reloadSecondaryPanels(reloadFiles);
            WorkspaceExternalChangeMonitor.acknowledgeCurrentWorkspace();
            runIfPresent(onComplete);
        };
        if (reloadFiles.contains(WorkspaceDataFile.COLLECTIONS)) {
            OpenedRequestTabsReloader.reloadCollectionsKeepingTabs(
                    SystemUtil.getCollectionPathForWorkspace(current), reloadOthers);
        } else {
            reloadOthers.run();
        }
    }

    /**
     * Git 操作改变了 changedWorkspace 所在仓库的文件后调用：只有当前工作区与其相同或共享同一仓库时才重新加载。
     */
    public static void reloadAfterRepositoryChange(Workspace changedWorkspace, Runnable onComplete) {
        WorkspaceService workspaceService = WorkspaceService.getInstance();
        workspaceService.invalidateGitLayoutCache();
        if (isAffectedByRepositoryChange(changedWorkspace)) {
            reloadCurrentWorkspace(onComplete);
        } else {
            runIfPresent(onComplete);
        }
    }

    static boolean isAffectedByRepositoryChange(Workspace changedWorkspace) {
        WorkspaceService workspaceService = WorkspaceService.getInstance();
        Workspace current = workspaceService.getCurrentWorkspace();
        if (current == null || changedWorkspace == null) {
            return false;
        }
        if (Objects.equals(current.getId(), changedWorkspace.getId())) {
            return true;
        }
        GitWorkspaceLayout changedLayout = workspaceService.detectGitLayout(changedWorkspace);
        GitWorkspaceLayout currentLayout = workspaceService.detectGitLayout(current);
        return changedLayout != null && currentLayout != null
                && changedLayout.getRepositoryRoot().equals(currentLayout.getRepositoryRoot());
    }

    private static void reloadSecondaryPanels(Set<WorkspaceDataFile> files) {
        if (files.contains(WorkspaceDataFile.FUNCTIONAL)) {
            UiSingletonFactory.getExistingInstance(FunctionalPanel.class)
                    .ifPresent(FunctionalPanel::switchWorkspaceAndRefreshUI);
        }
        if (files.contains(WorkspaceDataFile.PERFORMANCE)) {
            UiSingletonFactory.getExistingInstance(PerformancePanel.class)
                    .ifPresent(PerformancePanel::switchWorkspaceAndRefreshUI);
        }
        if (files.contains(WorkspaceDataFile.MOCK_SERVERS)) {
            UiSingletonFactory.getExistingInstance(MockServerPanel.class).ifPresentOrElse(
                    MockServerPanel::switchWorkspaceAndRefreshUI,
                    () -> BeanFactory.getBean(MockServerManager.class).reloadWorkspace());
        }
    }

    /**
     * 写了一半或结构不正确的文件不加载，保留当前界面；缺失的文件按空数据加载。
     */
    static Set<WorkspaceDataFile> unloadableFiles(Workspace workspace, Set<WorkspaceDataFile> files) {
        Set<WorkspaceDataFile> unloadable = EnumSet.noneOf(WorkspaceDataFile.class);
        if (workspace.getPath() == null || workspace.getPath().isBlank()) {
            return unloadable;
        }
        Path dataRoot = Path.of(workspace.getPath());
        for (WorkspaceDataFile file : files) {
            if (!WorkspaceDataChangeDetector.isLoadable(file, file.resolve(dataRoot))) {
                unloadable.add(file);
            }
        }
        return unloadable;
    }

    private static void runIfPresent(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}
