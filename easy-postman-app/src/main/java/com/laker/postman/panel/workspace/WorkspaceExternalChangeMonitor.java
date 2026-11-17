package com.laker.postman.panel.workspace;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.mock.app.MockServerManager;
import com.laker.postman.model.Workspace;
import com.laker.postman.panel.collections.OpenedRequestTabsReloader;
import com.laker.postman.panel.performance.PerformancePanel;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.workspace.reload.WorkspaceDataChangeDetector;
import com.laker.postman.workspace.reload.WorkspaceDataChangeDetector.WorkspaceDataChanges;
import com.laker.postman.workspace.reload.WorkspaceDataFile;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 主窗口重新获得焦点时检查当前工作区数据文件是否被外部工具修改（IDE、AI、Git 等），并按需重新加载。
 * <p>
 * 没有未保存修改时自动重新加载；会丢弃未保存请求、停止运行中的 Mock 或压测时先询问用户。
 * 无法解析的文件（例如正在写入）不会加载，当前界面与磁盘文件都保持不变。
 */
@Slf4j
@UtilityClass
public class WorkspaceExternalChangeMonitor {

    private static final WorkspaceDataChangeDetector DETECTOR = new WorkspaceDataChangeDetector();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Workspace-Change-Monitor");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean HANDLING = new AtomicBoolean();
    /**
     * 用户选择“稍后”的文件：之后不再打扰，阻塞条件（未保存标签、运行中的 Mock/压测）解除后的下一次检查自动重载，
     * 有新的外部修改时与之合并一起提示。
     */
    private static final Set<WorkspaceDataFile> PENDING = EnumSet.noneOf(WorkspaceDataFile.class);
    private static Path pendingRoot;

    public static void install(Window window) {
        if (window == null || !INSTALLED.compareAndSet(false, true)) {
            return;
        }
        acknowledgeCurrentWorkspace();
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                checkForExternalChanges();
            }
        });
    }

    /**
     * 以当前磁盘内容为基准，之后只报告新的外部修改。
     */
    public static void acknowledgeCurrentWorkspace() {
        Path dataRoot = currentDataRoot();
        clearPending();
        EXECUTOR.execute(() -> DETECTOR.reset(dataRoot));
    }

    public static void checkForExternalChanges() {
        if (HANDLING.get()) {
            return;
        }
        Workspace workspace = WorkspaceService.getInstance().getCurrentWorkspace();
        Path dataRoot = dataRoot(workspace);
        if (dataRoot == null) {
            return;
        }
        String workspaceId = workspace.getId();
        EXECUTOR.execute(() -> {
            try {
                WorkspaceDataChanges changes = DETECTOR.detect(dataRoot);
                if (!changes.isEmpty() || hasPending(dataRoot)) {
                    SwingUtilities.invokeLater(() -> handleChanges(workspaceId, dataRoot, changes));
                }
            } catch (Exception e) {
                log.warn("Failed to check workspace data changes: {}", dataRoot, e);
            }
        });
    }

    /**
     * 手动从磁盘重新加载当前工作区全部数据；不先保存面板，避免覆盖外部修改。
     */
    public static void reloadCurrentWorkspaceFromDisk(Runnable onComplete) {
        Set<WorkspaceDataFile> all = EnumSet.allOf(WorkspaceDataFile.class);
        List<String> consequences = reloadConsequences(all);
        String message = I18nUtil.getMessage(MessageKeys.WORKSPACE_RELOAD_FROM_DISK) + "?";
        if (!consequences.isEmpty() && !confirm(message, consequences)) {
            return;
        }
        WorkspaceDataReloader.reloadCurrentWorkspace(all, onComplete);
    }

    private static void handleChanges(String workspaceId, Path dataRoot, WorkspaceDataChanges changes) {
        Workspace current = WorkspaceService.getInstance().getCurrentWorkspace();
        if (current == null || !Objects.equals(current.getId(), workspaceId)) {
            return;
        }
        if (!changes.unreadable().isEmpty()) {
            NotificationCenter.showWarning(I18nUtil.getMessage(
                    MessageKeys.WORKSPACE_EXTERNAL_CHANGE_UNREADABLE, fileNames(changes.unreadable())));
        }
        Set<WorkspaceDataFile> changed = mergeWithPending(dataRoot, changes);
        if (changed.isEmpty()) {
            return;
        }
        List<String> consequences = reloadConsequences(changed);
        if (!consequences.isEmpty()) {
            boolean onlyPending = changes.changed().isEmpty();
            if (onlyPending || !confirmReload(changed, consequences)) {
                setPending(dataRoot, changed);
                if (!onlyPending) {
                    NotificationCenter.showInfo(I18nUtil.getMessage(
                            MessageKeys.WORKSPACE_EXTERNAL_CHANGE_POSTPONED, fileNames(changed)));
                }
                return;
            }
        }
        WorkspaceDataReloader.reloadCurrentWorkspace(changed, () -> NotificationCenter.showInfo(
                I18nUtil.getMessage(MessageKeys.WORKSPACE_EXTERNAL_CHANGE_RELOADED, fileNames(changed))));
    }

    static Set<WorkspaceDataFile> mergeWithPending(Path dataRoot, WorkspaceDataChanges changes) {
        Set<WorkspaceDataFile> merged = EnumSet.noneOf(WorkspaceDataFile.class);
        synchronized (PENDING) {
            if (Objects.equals(pendingRoot, dataRoot)) {
                merged.addAll(PENDING);
            }
        }
        merged.addAll(changes.changed());
        merged.removeAll(changes.unreadable());
        return merged;
    }

    static void setPending(Path dataRoot, Set<WorkspaceDataFile> files) {
        synchronized (PENDING) {
            PENDING.clear();
            PENDING.addAll(files);
            pendingRoot = dataRoot;
        }
    }

    static boolean hasPending(Path dataRoot) {
        synchronized (PENDING) {
            return !PENDING.isEmpty() && Objects.equals(pendingRoot, dataRoot);
        }
    }

    static void clearPending() {
        synchronized (PENDING) {
            PENDING.clear();
            pendingRoot = null;
        }
    }

    private static List<String> reloadConsequences(Set<WorkspaceDataFile> changed) {
        List<String> consequences = new ArrayList<>();
        if (changed.contains(WorkspaceDataFile.COLLECTIONS) && OpenedRequestTabsReloader.hasUnsavedTabs()) {
            consequences.add(I18nUtil.getMessage(MessageKeys.WORKSPACE_EXTERNAL_CHANGE_DISCARD_UNSAVED));
        }
        if (changed.contains(WorkspaceDataFile.MOCK_SERVERS)
                && BeanFactory.getBean(MockServerManager.class).hasRunningServers()) {
            consequences.add(I18nUtil.getMessage(MessageKeys.WORKSPACE_EXTERNAL_CHANGE_STOP_MOCK));
        }
        if (changed.contains(WorkspaceDataFile.PERFORMANCE)
                && UiSingletonFactory.getExistingInstance(PerformancePanel.class)
                .map(PerformancePanel::isRunInProgress).orElse(false)) {
            consequences.add(I18nUtil.getMessage(MessageKeys.WORKSPACE_EXTERNAL_CHANGE_STOP_PERFORMANCE));
        }
        return consequences;
    }

    private static boolean confirmReload(Set<WorkspaceDataFile> changed, List<String> consequences) {
        return confirm(I18nUtil.getMessage(MessageKeys.WORKSPACE_EXTERNAL_CHANGE_CONFIRM, fileNames(changed)),
                consequences);
    }

    private static boolean confirm(String summary, List<String> consequences) {
        HANDLING.set(true);
        try {
            String message = summary + "\n\n"
                    + consequences.stream().map(item -> "• " + item).collect(Collectors.joining("\n"));
            Object[] options = {
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_RELOAD_FROM_DISK),
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_EXTERNAL_CHANGE_LATER)
            };
            int choice = JOptionPane.showOptionDialog(
                    activeWindow(),
                    message,
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_EXTERNAL_CHANGE_TITLE),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            return choice == 0;
        } finally {
            HANDLING.set(false);
        }
    }

    private static Window activeWindow() {
        Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return window != null ? window : JOptionPane.getRootFrame();
    }

    static String fileNames(Set<WorkspaceDataFile> files) {
        return files.stream().map(WorkspaceDataFile::getFileName).collect(Collectors.joining(", "));
    }

    private static Path currentDataRoot() {
        return dataRoot(WorkspaceService.getInstance().getCurrentWorkspace());
    }

    private static Path dataRoot(Workspace workspace) {
        if (workspace == null || workspace.getPath() == null || workspace.getPath().isBlank()) {
            return null;
        }
        return Path.of(workspace.getPath());
    }
}
