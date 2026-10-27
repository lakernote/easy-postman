package com.laker.postman.panel.workspace;

import com.laker.postman.common.component.AppToolWindowChrome;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class WorkspacePanelTest {

    @Test
    public void defaultDetailDividerShouldNotOverstretchDetailAreaInTallWindows() {
        assertEquals(WorkspacePanel.defaultWorkspaceDetailDividerLocation(
                1400,
                AppToolWindowChrome.DIVIDER_SIZE
        ), 400);
    }

    @Test
    public void defaultDetailDividerShouldGiveDetailAreaReadableSpaceInLargeWindows() {
        assertEquals(WorkspacePanel.defaultWorkspaceDetailDividerLocation(
                1000,
                AppToolWindowChrome.DIVIDER_SIZE
        ), 360);
    }

    @Test
    public void defaultDetailDividerShouldKeepDetailAreaReadableInMediumWindows() {
        assertEquals(WorkspacePanel.defaultWorkspaceDetailDividerLocation(
                700,
                AppToolWindowChrome.DIVIDER_SIZE
        ), 360);
    }

    @Test
    public void defaultDetailDividerShouldPreserveToolAreaWhenWindowIsShort() {
        assertEquals(WorkspacePanel.defaultWorkspaceDetailDividerLocation(
                520,
                AppToolWindowChrome.DIVIDER_SIZE
        ), 295);
    }

    @Test
    public void workspacePanelShouldEmbedRemoteRepositoryConfiguration() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void configureRemoteRepository"),
                source.indexOf("\n    /**", source.indexOf("private void configureRemoteRepository") + 1)
        );

        assertFalse(method.contains("workspaceService.addRemoteRepository("),
                "RemoteConfigPanel performs the remote configuration; WorkspacePanel should only refresh after confirmation.");
        assertFalse(method.contains("new RemoteConfigDialog("));
        assertTrue(method.contains("new RemoteConfigPanel("));
        assertTrue(method.contains("refreshWorkspaceList();"));
    }

    @Test
    public void gitWorkspaceContextMenuShouldNotDuplicateEmbeddedBranchManagement() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void addGitMenuItems"),
                source.indexOf("\n    private void addManagementMenuItems", source.indexOf("private void addGitMenuItems"))
        );

        assertFalse(method.contains("MessageKeys.WORKSPACE_GIT_BRANCHES"));
        assertFalse(method.contains("showGitBranches(workspace)"));
    }

    @Test
    public void gitWorkspaceContextMenuShouldNotDuplicateEmbeddedDiffManagement() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void addGitMenuItems"),
                source.indexOf("\n    private void addManagementMenuItems", source.indexOf("private void addGitMenuItems"))
        );

        assertFalse(method.contains("MessageKeys.WORKSPACE_GIT_DIFF"));
        assertFalse(method.contains("showGitDiff(workspace)"));
    }

    @Test
    public void gitWorkspaceContextMenuShouldNotDuplicateHeaderGitActions() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void addGitMenuItems"),
                source.indexOf("\n    private void addManagementMenuItems", source.indexOf("private void addGitMenuItems"))
        );

        assertFalse(method.contains("MessageKeys.WORKSPACE_GIT_COMMIT"));
        assertFalse(method.contains("MessageKeys.WORKSPACE_GIT_PULL"));
        assertFalse(method.contains("MessageKeys.WORKSPACE_GIT_PUSH"));
        assertFalse(method.contains("MessageKeys.WORKSPACE_GIT_HISTORY"));
        assertFalse(method.contains("performGitCommit(workspace)"));
        assertFalse(method.contains("performGitPull(workspace)"));
        assertFalse(method.contains("performGitPush(workspace)"));
        assertFalse(method.contains("showGitHistory(workspace)"));
        assertFalse(method.contains("MessageKeys.WORKSPACE_REMOTE_CONFIG_TITLE"));
        assertFalse(method.contains("configureRemoteRepository(workspace)"));
        assertFalse(method.contains("updateGitAuthentication"));
    }

    @Test
    public void workspaceDetailShouldExposeGitManagementActions() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void updateInfoPanel"),
                source.indexOf("\n    private void showError", source.indexOf("private void updateInfoPanel") + 1)
        );

        assertTrue(method.contains("new WorkspaceDetailPanel("));
        assertTrue(method.contains("createGitActions(selected)"));
    }

    @Test
    public void workspaceDetailShouldExposeGitSyncActions() throws IOException {
        Path panelPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        Path detailPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components/WorkspaceDetailPanel.java");
        String panelSource = Files.readString(panelPath);
        String detailSource = Files.readString(detailPath);
        String method = panelSource.substring(
                panelSource.indexOf("private WorkspaceDetailPanel.GitActions createGitActions"),
                panelSource.indexOf("\n    private void showError", panelSource.indexOf("private WorkspaceDetailPanel.GitActions createGitActions") + 1)
        );

        assertTrue(method.contains("() -> performGitCommit(workspace)"));
        assertTrue(method.contains("() -> performGitPull(workspace)"));
        assertTrue(method.contains("() -> performGitPush(workspace)"));
        assertTrue(method.contains("() -> configureRemoteRepository(workspace)"));
        assertTrue(method.contains("() -> showGitHistory(workspace)"));
        assertTrue(method.contains("() -> showGitBranches(workspace)"));
        assertTrue(method.contains("() -> showGitDiff(workspace)"));

        assertTrue(detailSource.contains("GitOperation.COMMIT"));
        assertTrue(detailSource.contains("GitOperation.PULL"));
        assertTrue(detailSource.contains("GitOperation.PUSH"));
        assertTrue(detailSource.contains("MessageKeys.WORKSPACE_REMOTE_CONFIG_TITLE"));
        assertTrue(detailSource.contains("MessageKeys.WORKSPACE_GIT_HISTORY"));
        assertTrue(detailSource.contains("\"icons/git-remote-config.svg\", gitActions.remoteConfigAction()"));
        assertTrue(detailSource.contains("\"icons/git.svg\", gitActions.branchManagementAction()"));
        assertTrue(detailSource.contains("BUTTON_TYPE_TOOLBAR_BUTTON"));
        assertTrue(detailSource.contains("setAccessibleName"));
    }

    @Test
    public void remoteRepositoryConfigurationShouldUseDistinctToolbarIcon() throws IOException {
        Path remoteWorkspaceIconPath = repositoryRoot().resolve("easy-postman-app/src/main/resources/icons/git-remote.svg");
        Path remoteConfigIconPath = repositoryRoot().resolve("easy-postman-app/src/main/resources/icons/git-remote-config.svg");
        String remoteWorkspaceIcon = Files.readString(remoteWorkspaceIconPath);
        String remoteConfigIcon = Files.readString(remoteConfigIconPath);

        assertFalse(remoteConfigIcon.equals(remoteWorkspaceIcon));
        assertTrue(remoteConfigIcon.contains("stroke=\"currentColor\""));
        assertTrue(remoteConfigIcon.contains("stroke-width=\"2\""));
        assertTrue(remoteConfigIcon.contains("m10.852 19.772"));
        assertFalse(remoteConfigIcon.contains("M12 13v8"));
        assertFalse(remoteConfigIcon.contains("stroke=\"#"));
    }

    @Test
    public void remoteGitWorkspaceListIconsShouldUseColoredGitStatusIcon() throws IOException {
        Path workspaceListRendererPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components/WorkspaceListCellRenderer.java");
        Path workspaceSelectionDialogPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/common/component/dialog/WorkspaceSelectionDialog.java");
        Path remoteIconPath = repositoryRoot().resolve("easy-postman-app/src/main/resources/icons/git-remote.svg");
        String listRenderer = Files.readString(workspaceListRendererPath);
        String selectionDialog = Files.readString(workspaceSelectionDialogPath);
        String remoteIcon = Files.readString(remoteIconPath);

        assertTrue(listRenderer.contains("IconUtil.create(\"icons/git-remote.svg\", 20, 20)"));
        assertTrue(selectionDialog.contains("IconUtil.create(\"icons/git-remote.svg\", 20, 20)"));
        assertFalse(listRenderer.contains("IconUtil.createThemed(\"icons/git-remote.svg\""));
        assertFalse(selectionDialog.contains("IconUtil.createThemed(\"icons/git-remote.svg\""));
        assertTrue(remoteIcon.contains("stroke=\"#f97316\""));
        assertTrue(remoteIcon.contains("<circle cx=\"18\""));
        assertFalse(remoteIcon.contains("stroke=\"currentColor\""));
    }

    @Test
    public void remoteConfigPanelShouldUseCompactEmbeddedFormLayout() throws IOException {
        Path remoteConfigPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components/RemoteConfigPanel.java");
        Path gitAuthPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components/GitAuthPanel.java");
        Path modernButtonFactoryPath = repositoryRoot().resolve(
                "easy-postman-ui/src/main/java/com/laker/postman/common/component/button/ModernButtonFactory.java");
        String source = Files.readString(remoteConfigPath);
        String authSource = Files.readString(gitAuthPath);
        String buttonFactorySource = Files.readString(modernButtonFactoryPath);

        assertTrue(source.contains("FORM_LABEL_WIDTH = 128"));
        assertTrue(source.contains("FORM_LABEL_GAP = 12"));
        assertTrue(source.contains("URL_FIELD_WIDTH = 520"));
        assertTrue(source.contains("FORM_MAX_WIDTH = FORM_LABEL_WIDTH + FORM_LABEL_GAP + URL_FIELD_WIDTH"));
        assertTrue(source.contains("BRANCH_FIELD_WIDTH = 260"));
        assertTrue(source.contains("ModernButtonFactory.createCompactButton("));
        assertTrue(source.contains("I18nUtil.getMessage(MessageKeys.BUTTON_SAVE)"));
        assertTrue(source.contains("\"icons/save.svg\""));
        assertFalse(source.contains("new SecondaryButton("));
        assertFalse(source.contains("I18nUtil.getMessage(MessageKeys.GENERAL_OK)"));
        assertFalse(source.contains("ModernButtonFactory.createButton("));
        assertTrue(source.contains("header.add(createHeaderActionRow(), \"w \" + FORM_MAX_WIDTH + \"!, growx 0\");"));
        assertTrue(source.contains("private JPanel createHeaderActionRow()"));
        assertTrue(source.contains("row.add(saveButton, \"aligny center, h \" + ModernButtonFactory.COMPACT_BUTTON_HEIGHT + \"!\");"));
        assertTrue(source.contains("hidemode 3"));
        assertTrue(source.contains("\"w \" + FORM_MAX_WIDTH + \"!, growx 0\""));
        assertTrue(source.contains("\" + FORM_LABEL_GAP + \"[grow,fill]"));
        assertTrue(source.contains("progressBar.setStringPainted(false)"));
        assertTrue(buttonFactorySource.contains("COMPACT_BUTTON_HEIGHT = 30"));
        assertTrue(buttonFactorySource.contains("COMPACT_BUTTON_MIN_WIDTH = 72"));
        assertTrue(buttonFactorySource.contains("COMPACT_HORIZONTAL_PADDING = 24"));
        assertTrue(buttonFactorySource.contains("margin: 2,8,2,8; arc: 6"));
        assertTrue(authSource.contains("AUTH_TYPE_WIDTH = 260"));
        assertTrue(authSource.contains("CREDENTIAL_FIELD_WIDTH = 420"));
        assertTrue(authSource.contains("FORM_LABEL_WIDTH = 128"));
        assertFalse(source.contains("BorderLayout.SOUTH"));
    }

    @Test
    public void workspacePanelShouldEmbedGitDiffPanel() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void showGitDiff"),
                source.indexOf("\n    /**", source.indexOf("private void showGitDiff") + 1)
        );

        assertTrue(method.contains("saveCurrentWorkspaceScopedPanels();"));
        assertTrue(method.contains("new GitDiffPanel("));
        assertFalse(method.contains("new GitDiffDialog("));
    }

    @Test
    public void workspacePanelShouldEmbedGitBranchPanel() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void showGitBranches"),
                source.indexOf("\n    private void showGitDiff", source.indexOf("private void showGitBranches") + 1)
        );

        assertTrue(method.contains("saveCurrentWorkspaceScopedPanels();"));
        assertTrue(method.contains("new GitBranchPanel("));
        assertFalse(method.contains("new GitBranchDialog("));
    }

    @Test
    public void workspacePanelShouldEmbedGitHistoryPanel() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void showGitHistory"),
                source.indexOf("\n    private void showGitBranches", source.indexOf("private void showGitHistory") + 1)
        );

        assertTrue(method.contains("new GitHistoryPanel("));
        assertFalse(method.contains("new GitHistoryDialog("));
    }

    @Test
    public void obsoleteGitManagementDialogsShouldBeRemoved() {
        Path componentDir = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components");

        assertFalse(Files.exists(componentDir.resolve("GitDiffDialog.java")));
        assertFalse(Files.exists(componentDir.resolve("GitBranchDialog.java")));
        assertFalse(Files.exists(componentDir.resolve("GitHistoryDialog.java")));
        assertFalse(Files.exists(componentDir.resolve("RemoteConfigDialog.java")));
    }

    @Test
    public void workspacePanelShouldKeepEmbeddedGitToolWhenSameWorkspaceRefreshes() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void updateInfoPanel"),
                source.indexOf("\n    private void showError", source.indexOf("private void updateInfoPanel") + 1)
        );

        assertTrue(source.contains("displayedWorkspaceId"));
        assertTrue(method.contains("Objects.equals"));
        assertTrue(method.contains("if (workspaceChanged)"));
        assertTrue(method.contains("showDefaultWorkspaceTool(selected);"));
    }

    @Test
    public void workspacePanelShouldDefaultGitWorkspacesToDiffPanelInsteadOfLog() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String method = source.substring(
                source.indexOf("private void showDefaultWorkspaceTool"),
                source.indexOf("\n    private void showWorkspaceTool", source.indexOf("private void showDefaultWorkspaceTool") + 1)
        );

        assertTrue(method.contains("WorkspaceType.GIT"));
        assertTrue(method.contains("showGitDiff(workspace, false);"));
        assertTrue(method.contains("hideWorkspaceTool();"));
        assertFalse(source.contains("createEmptyWorkspaceToolPanel"));
        assertFalse(source.contains("logArea"));
        assertFalse(source.contains("createWorkspaceLogPanel"));
    }

    @Test
    public void workspacePanelShouldCollapseToolAreaWhenNoEmbeddedToolIsShown() throws IOException {
        Path sourcePath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java");
        String source = Files.readString(sourcePath);
        String showTool = source.substring(
                source.indexOf("private void showWorkspaceTool"),
                source.indexOf("\n    private void hideWorkspaceTool", source.indexOf("private void showWorkspaceTool") + 1)
        );
        String visibility = source.substring(
                source.indexOf("private void setWorkspaceToolVisible"),
                source.indexOf("\n    private static void installInitialWorkspaceDetailDivider",
                        source.indexOf("private void setWorkspaceToolVisible") + 1)
        );

        assertTrue(showTool.contains("setWorkspaceToolVisible(true);"));
        assertTrue(visibility.contains("workspaceToolPanel.setVisible(visible);"));
        assertTrue(visibility.contains("workspaceContentSplitPane.setDividerSize(visible ? AppToolWindowChrome.DIVIDER_SIZE : 0);"));
        assertTrue(visibility.contains("workspaceContentSplitPane.setResizeWeight(visible ? WORKSPACE_DETAIL_RESIZE_WEIGHT : 1.0);"));
    }

    @Test
    public void embeddedGitPanelsShouldUseWorkspaceSurfaceInsteadOfDialogSurface() throws IOException {
        Path diffPanelPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components/GitDiffPanel.java");
        Path branchPanelPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components/GitBranchPanel.java");
        Path historyPanelPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components/GitHistoryPanel.java");
        Path remoteConfigPanelPath = repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/components/RemoteConfigPanel.java");
        String diffPanel = Files.readString(diffPanelPath);
        String branchPanel = Files.readString(branchPanelPath);
        String historyPanel = Files.readString(historyPanelPath);
        String remoteConfigPanel = Files.readString(remoteConfigPanelPath);

        assertFalse(diffPanel.contains("applyDialogHeader"));
        assertFalse(diffPanel.contains("applyDialogSurface"));
        assertFalse(diffPanel.contains("applyDialogFrame"));
        assertFalse(branchPanel.contains("applyDialogHeader"));
        assertFalse(branchPanel.contains("applyDialogSurface"));
        assertFalse(branchPanel.contains("applyDialogFrame"));
        assertFalse(historyPanel.contains("applyDialogHeader"));
        assertFalse(historyPanel.contains("applyDialogSurface"));
        assertFalse(historyPanel.contains("applyDialogFrame"));
        assertFalse(remoteConfigPanel.contains("applyDialogHeader"));
        assertFalse(remoteConfigPanel.contains("applyDialogSurface"));
        assertFalse(remoteConfigPanel.contains("applyDialogFrame"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("easy-postman-app"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}
