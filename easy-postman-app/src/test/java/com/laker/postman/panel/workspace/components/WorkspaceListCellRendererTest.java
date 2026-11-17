package com.laker.postman.panel.workspace.components;

import com.laker.postman.model.GitAuthType;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.plugin.git.GitWorkspaceLayout;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.eclipse.jgit.api.Git;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class WorkspaceListCellRendererTest {

    private Path tempDir;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("easy-postman-workspace-label");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws IOException {
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void typeLabelShouldReflectDetectedRepository() throws Exception {
        Path repo = tempDir.resolve("order-service");
        Git.init().setInitialBranch("main").setDirectory(repo.toFile()).call().close();
        String git = I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_GIT);

        Workspace plain = workspace(tempDir.resolve("plain"), WorkspaceType.LOCAL);
        assertEquals(WorkspaceListCellRenderer.typeLabel(plain, (GitWorkspaceLayout) null),
                I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_LOCAL));

        Workspace embedded = workspace(repo.resolve("tools/api"), WorkspaceType.LOCAL);
        assertEquals(WorkspaceListCellRenderer.typeLabel(embedded, detect(embedded)),
                git + " · order-service/tools/api · main");

        // 旧类型不决定归属：没有归属标记的仓库根目录仍显示仓库名
        Workspace openedRoot = workspace(repo, WorkspaceType.GIT);
        assertEquals(WorkspaceListCellRenderer.typeLabel(openedRoot, detect(openedRoot)), git + " · order-service · main");

        Path dedicated = tempDir.resolve("api-repo");
        try (Git created = Git.init().setInitialBranch("dev").setDirectory(dedicated.toFile()).call()) {
            GitWorkspaceLayout.markOwnedByEasyPostman(created.getRepository());
        }
        Workspace owned = workspace(dedicated, WorkspaceType.LOCAL);
        assertEquals(WorkspaceListCellRenderer.typeLabel(owned, detect(owned)), git + " · dev");
    }

    @Test
    public void typeLabelShouldFollowDiskFactsOnceKnown() {
        String git = I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_GIT);
        String local = I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_LOCAL);
        Workspace savedAsGit = workspace(tempDir.resolve("repo-removed"), WorkspaceType.GIT);

        // 探测结果返回前按已保存类型显示，返回后以磁盘为准（.git 已删除即为本地）
        assertEquals(WorkspaceListCellRenderer.typeLabel(savedAsGit, new WorkspaceService.GitLayoutPeek(false, null)), git);
        assertEquals(WorkspaceListCellRenderer.typeLabel(savedAsGit, new WorkspaceService.GitLayoutPeek(true, null)), local);
    }

    @Test
    public void workspaceDataShouldBeDetectedFromKnownFiles() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("data"));
        assertFalse(WorkspaceService.containsWorkspaceData(dir));
        Files.writeString(dir.resolve("environments.json"), "[]");
        assertTrue(WorkspaceService.containsWorkspaceData(dir));
    }

    private static GitWorkspaceLayout detect(Workspace workspace) {
        return GitWorkspaceLayout.detect(workspace).orElseThrow();
    }

    private static Workspace workspace(Path path, WorkspaceType type) {
        Workspace workspace = new Workspace();
        workspace.setType(type);
        workspace.setGitAuthType(GitAuthType.NONE);
        workspace.setPath(path.toString());
        return workspace;
    }
}
