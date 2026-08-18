package com.laker.postman.panel.topmenu;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.combobox.EnvironmentComboBox;
import com.laker.postman.common.component.combobox.WorkspaceComboBox;
import com.laker.postman.model.GitOperation;
import com.laker.postman.model.RemoteStatus;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.panel.env.EnvironmentPanel;
import com.laker.postman.panel.functional.FunctionalPanel;
import com.laker.postman.panel.mock.MockServerPanel;
import com.laker.postman.panel.performance.PerformancePanel;
import com.laker.postman.panel.workspace.components.GitOperationDialog;
import com.laker.postman.panel.workspace.components.GitOperationPresentation;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.util.SystemUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;

/**
 * 顶部菜单栏右侧的工作区、环境和 Git 快捷操作区。
 */
@Slf4j
@RequiredArgsConstructor
class TopMenuWorkspaceControls {
    private final Component dialogParent;
    private final Runnable refreshMenuBar;

    private EnvironmentComboBox environmentComboBox;
    private WorkspaceComboBox workspaceComboBox;

    EnvironmentComboBox getEnvironmentComboBox() {
        ensureComboBoxesCreated();
        return environmentComboBox;
    }

    WorkspaceComboBox getWorkspaceComboBox() {
        ensureComboBoxesCreated();
        return workspaceComboBox;
    }

    void addTo(JMenuBar menuBar) {
        reloadComboBoxes();
        addGitToolbarIfNeeded(menuBar);

        menuBar.add(createToolbarIconLabel("icons/workspace.svg"));
        menuBar.add(workspaceComboBox);
        menuBar.add(Box.createHorizontalStrut(2));
        menuBar.add(createToolbarIconLabel("icons/environments.svg"));
        menuBar.add(environmentComboBox);
    }

    void updateWorkspaceComboBox() {
        if (workspaceComboBox != null) {
            workspaceComboBox.reload();
        }
    }

    void updateWorkspaceDisplay() {
        refreshMenuBar.run();
    }

    void performGitOperation(Workspace workspace, GitOperation operation) {
        saveCurrentWorkspaceScopedPanels();
        GitOperationDialog dialog = new GitOperationDialog(
                SwingUtilities.getWindowAncestor(dialogParent),
                workspace,
                operation
        );
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            if (operation == GitOperation.PULL) {
                UiSingletonFactory.getInstance(CollectionTreePanel.class)
                        .switchWorkspaceAndRefreshUI(SystemUtil.getCollectionPathForWorkspace(workspace), this::refreshExistingWorkspaceScopedPanels);
                UiSingletonFactory.getInstance(EnvironmentPanel.class)
                        .switchWorkspaceAndRefreshUI(SystemUtil.getEnvPathForWorkspace(workspace));
            }

            log.info("Git {} operation completed successfully", operation.getDisplayName());
        }
    }

    private void ensureComboBoxesCreated() {
        if (workspaceComboBox == null) {
            workspaceComboBox = new WorkspaceComboBox();
            workspaceComboBox.setOnWorkspaceChange(this::switchToWorkspace);
        }

        if (environmentComboBox == null) {
            environmentComboBox = new EnvironmentComboBox();
        }
    }

    private void reloadComboBoxes() {
        if (workspaceComboBox == null) {
            workspaceComboBox = new WorkspaceComboBox();
            workspaceComboBox.setOnWorkspaceChange(this::switchToWorkspace);
        } else {
            workspaceComboBox.reload();
        }

        if (environmentComboBox == null) {
            environmentComboBox = new EnvironmentComboBox();
        } else {
            environmentComboBox.reload();
        }
    }

    private JLabel createToolbarIconLabel(String iconPath) {
        return new JLabel(IconUtil.createThemed(iconPath, IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
    }

    private void addGitToolbarIfNeeded(JMenuBar menuBar) {
        try {
            Workspace currentWorkspace = WorkspaceService.getInstance().getCurrentWorkspace();
            if (currentWorkspace != null && currentWorkspace.getType() == WorkspaceType.GIT) {
                menuBar.add(createGitToolbar(currentWorkspace));
                menuBar.add(Box.createHorizontalStrut(12));
            }
        } catch (Exception e) {
            log.error("Failed to create Git toolbar", e);
        }
    }

    private void switchToWorkspace(Workspace workspace) {
        try {
            WorkspaceService workspaceService = WorkspaceService.getInstance();
            Workspace currentWorkspace = workspaceService.getCurrentWorkspace();

            if (currentWorkspace != null && currentWorkspace.getId().equals(workspace.getId())) {
                return;
            }

            saveCurrentWorkspaceScopedPanels();
            workspaceService.switchWorkspace(workspace.getId());

            UiSingletonFactory.getInstance(EnvironmentPanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getEnvPathForWorkspace(workspace));

            UiSingletonFactory.getInstance(CollectionTreePanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getCollectionPathForWorkspace(workspace), () -> {
                        refreshExistingWorkspaceScopedPanels();
                        refreshMenuBar.run();
                        log.info("Switched to workspace: {}", workspace.getName());
                    });
        } catch (Exception e) {
            log.error("Failed to switch workspace", e);
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.WORKSPACE_OPERATION_FAILED_DETAIL, e.getMessage()));
        }
    }

    private void saveCurrentWorkspaceScopedPanels() {
        UiSingletonFactory.getExistingInstance(FunctionalPanel.class).ifPresent(FunctionalPanel::save);
        UiSingletonFactory.getExistingInstance(PerformancePanel.class).ifPresent(PerformancePanel::save);
    }

    private void refreshExistingWorkspaceScopedPanels() {
        UiSingletonFactory.getExistingInstance(FunctionalPanel.class)
                .ifPresent(FunctionalPanel::switchWorkspaceAndRefreshUI);
        UiSingletonFactory.getExistingInstance(PerformancePanel.class)
                .ifPresent(PerformancePanel::switchWorkspaceAndRefreshUI);
        UiSingletonFactory.getExistingInstance(MockServerPanel.class)
                .ifPresent(MockServerPanel::switchWorkspaceAndRefreshUI);
    }

    private JPanel createGitToolbar(Workspace workspace) {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        toolbar.setOpaque(false);

        try {
            RemoteStatus remoteStatus = WorkspaceService.getInstance().getRemoteStatus(workspace.getId());
            toolbar.add(createGitButton(
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_GIT_COMMIT),
                    GitOperationPresentation.getIconName(GitOperation.COMMIT),
                    e -> performGitOperation(workspace, GitOperation.COMMIT)
            ));

            if (remoteStatus.hasRemote) {
                toolbar.add(createGitButton(
                        I18nUtil.getMessage(MessageKeys.WORKSPACE_GIT_PULL),
                        GitOperationPresentation.getIconName(GitOperation.PULL),
                        e -> performGitOperation(workspace, GitOperation.PULL)
                ));

                if (remoteStatus.hasUpstream) {
                    toolbar.add(createGitButton(
                            I18nUtil.getMessage(MessageKeys.WORKSPACE_GIT_PUSH),
                            GitOperationPresentation.getIconName(GitOperation.PUSH),
                            e -> performGitOperation(workspace, GitOperation.PUSH)
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to create Git toolbar buttons", e);
        }

        return toolbar;
    }

    private JButton createGitButton(String tooltip, String iconPath, ActionListener action) {
        JButton button = new JButton();
        button.setIcon(IconUtil.createThemed(iconPath, 18, 18));
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setPreferredSize(new Dimension(24, 24));
        button.addActionListener(action);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return button;
    }
}
