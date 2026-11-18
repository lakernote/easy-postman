package com.laker.postman.plugin.git;

import com.laker.postman.model.GitAuthType;
import com.laker.postman.model.GitCommitInfo;
import com.laker.postman.model.GitFileChange;
import com.laker.postman.model.GitOperation;
import com.laker.postman.model.GitRepoSource;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import org.eclipse.jgit.api.Git;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * 端到端场景：工作区作为独立仓库，或作为已有代码仓库中的任意目录。
 */
public class WorkspaceGitScenarioTest {

    private final GitWorkspacePluginService service = new GitWorkspacePluginService();
    private Path tempDir;

    @BeforeMethod
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ws-git-scenario").toRealPath();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws IOException {
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void bareRepositoryShouldNotBeTreatedAsWorkspaceRepository() throws Exception {
        Path bare = tempDir.resolve("bare.git");
        Git.init().setBare(true).setDirectory(bare.toFile()).call().close();
        assertTrue(GitWorkspaceLayout.detectDirectory(bare).isEmpty());
    }

    @Test
    public void siblingDirectoryWithSamePrefixShouldStayOutOfScope() throws Exception {
        Path repo = initRepo("prefix-repo");
        Path api = repo.resolve("api");
        Path apiV2 = repo.resolve("api-v2");
        Files.createDirectories(api);
        Files.createDirectories(apiV2);
        Files.writeString(api.resolve("collections.json"), "[]");
        Files.writeString(apiV2.resolve("collections.json"), "[]");

        Workspace workspace = workspace(api);
        List<String> paths = service.listWorkingTreeChanges(workspace).stream().map(GitFileChange::getPath).toList();
        assertEquals(paths, List.of("collections.json"));

        service.commitChanges(workspace, "api only");
        try (Git git = Git.open(repo.toFile())) {
            assertTrue(git.status().call().getUntracked().contains("api-v2/collections.json"));
        }
    }

    @Test
    public void twoWorkspacesInOneRepositoryShouldBeIndependent() throws Exception {
        Path repo = initRepo("multi-repo");
        Path orders = repo.resolve("services/orders/easy-postman");
        Path users = repo.resolve("services/users/easy-postman");
        Files.createDirectories(orders);
        Files.createDirectories(users);
        Files.writeString(orders.resolve("collections.json"), "[{\"o\":1}]");
        Files.writeString(users.resolve("collections.json"), "[{\"u\":1}]");

        Workspace orderWs = workspace(orders);
        Workspace userWs = workspace(users);
        assertEquals(GitWorkspaceLayout.detect(orderWs).orElseThrow().getScope(), "services/orders/easy-postman/");

        service.commitChanges(orderWs, "orders api");
        assertTrue(service.listWorkingTreeChanges(orderWs).isEmpty());
        assertEquals(service.listWorkingTreeChanges(userWs).size(), 1);

        service.commitChanges(userWs, "users api");
        List<String> orderHistory = service.getGitHistory(orderWs, 0).stream().map(GitCommitInfo::getMessage).toList();
        List<String> userHistory = service.getGitHistory(userWs, 0).stream().map(GitCommitInfo::getMessage).toList();
        assertEquals(orderHistory, List.of("orders api"));
        assertEquals(userHistory, List.of("users api"));
    }

    @Test
    public void historyAndCommitDetailsShouldHideSourceCommits() throws Exception {
        Path repo = initRepo("history-repo");
        Path data = repo.resolve("api");
        Files.createDirectories(data);
        try (Git git = Git.open(repo.toFile())) {
            Files.writeString(data.resolve("collections.json"), "[]");
            Files.writeString(repo.resolve("Main.java"), "class Main {}");
            commitAll(git, "mixed commit");
            Files.writeString(repo.resolve("Main.java"), "class Main { int a; }");
            commitAll(git, "source only");
        }
        Workspace workspace = workspace(data);
        List<GitCommitInfo> history = service.getGitHistory(workspace, 0);
        assertEquals(history.stream().map(GitCommitInfo::getMessage).toList(), List.of("mixed commit"));

        String details = service.getCommitDetails(workspace, history.get(0).getCommitId());
        assertTrue(details.contains("collections.json"));
        assertFalse(details.contains("Main.java"), details);
    }

    @Test
    public void embeddedWorkspaceShouldRejectRepositoryRewrites() throws Exception {
        Path repo = initRepo("rewrite-repo");
        Path data = repo.resolve("api");
        Files.createDirectories(data);
        Files.writeString(data.resolve("collections.json"), "[]");
        Files.writeString(repo.resolve("Main.java"), "class Main {}");
        Workspace workspace = workspace(data);
        String head;
        try (Git git = Git.open(repo.toFile())) {
            head = git.getRepository().resolve("HEAD").getName();
        }

        expectThrows(Exception.class, () -> service.stashChanges(workspace));
        expectThrows(Exception.class, () -> service.forcePullUpdates(workspace));
        expectThrows(Exception.class, () -> service.forcePushChanges(workspace));
        expectThrows(Exception.class, () -> service.restoreToCommit(workspace, head, false));
        assertEquals(Files.readString(repo.resolve("Main.java")), "class Main {}");
        assertTrue(Files.exists(data.resolve("collections.json")));
    }

    @Test
    public void scopedCommitShouldRecordDeletionsInsideScopeOnly() throws Exception {
        Path repo = initRepo("delete-repo");
        Path data = repo.resolve("api");
        Files.createDirectories(data);
        try (Git git = Git.open(repo.toFile())) {
            Files.writeString(data.resolve("collections.json"), "[]");
            Files.writeString(data.resolve("environments.json"), "[]");
            Files.writeString(repo.resolve("Main.java"), "class Main {}");
            commitAll(git, "seed");
        }
        Files.delete(data.resolve("environments.json"));
        Files.delete(repo.resolve("Main.java"));

        Workspace workspace = workspace(data);
        assertTrue(service.checkGitStatus(workspace, GitOperation.COMMIT).canCommit);
        service.commitChanges(workspace, "remove env");

        try (Git git = Git.open(repo.toFile())) {
            var status = git.status().call();
            assertTrue(status.getMissing().contains("Main.java"), "source deletion must stay uncommitted");
            assertTrue(status.getUncommittedChanges().stream().noneMatch(p -> p.startsWith("api/")));
        }
    }

    @Test
    public void unicodeAndSpacesInScopeShouldWork() throws Exception {
        Path repo = initRepo("unicode-repo");
        Path data = repo.resolve("接口 测试/easy postman");
        Files.createDirectories(data);
        Files.writeString(data.resolve("collections.json"), "[{\"a\":1}]");
        Workspace workspace = workspace(data);

        assertEquals(GitWorkspaceLayout.detect(workspace).orElseThrow().getScope(), "接口 测试/easy postman/");
        assertTrue(service.getWorkingTreeDiff(workspace, "collections.json").contains("\"a\":1"));
        service.commitChanges(workspace, "unicode");
        assertTrue(service.listWorkingTreeChanges(workspace).isEmpty());
    }

    @Test
    public void symlinkedDataDirectoryShouldFollowItsRealRepository() throws Exception {
        Path repo = initRepo("link-repo");
        Path outside = Files.createDirectories(tempDir.resolve("outside-data"));
        Path link = repo.resolve("api");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }
        // 符号链接指向仓库之外：不能把外部目录当成该仓库的一部分
        assertTrue(GitWorkspaceLayout.detectDirectory(link).isEmpty());
    }

    @Test
    public void ownedStandaloneRepositoryShouldKeepFullControl() throws Exception {
        Path path = tempDir.resolve("standalone");
        Files.createDirectories(path);
        Workspace workspace = workspace(path);
        workspace.setType(WorkspaceType.GIT);
        workspace.setGitRepoSource(GitRepoSource.INITIALIZED);
        workspace.setName("standalone");
        service.prepareGitWorkspace(workspace);

        GitWorkspaceLayout layout = GitWorkspaceLayout.detect(workspace).orElseThrow();
        assertTrue(layout.isFullControl());
        assertEquals(layout.getScope(), "");

        Files.writeString(path.resolve("collections.json"), "[{\"x\":1}]");
        Files.writeString(path.resolve("notes.txt"), "anything");
        service.commitChanges(workspace, "all files");
        try (Git git = Git.open(path.toFile())) {
            assertTrue(git.status().call().isClean());
        }
        // 同一仓库的子目录不继承整仓库权限
        Path sub = Files.createDirectories(path.resolve("sub"));
        assertFalse(GitWorkspaceLayout.detect(workspace(sub)).orElseThrow().isFullControl());
    }

    @Test
    public void remoteStatusShouldReadBranchAndHeadFromDiskWithoutRemote() throws Exception {
        Path repo = initRepo("no-remote-repo");
        var status = service.getRemoteStatus(workspace(repo));
        assertFalse(status.hasRemote);
        assertEquals(status.currentBranch, "main");
        try (Git git = Git.open(repo.toFile())) {
            assertEquals(status.headCommitId, git.getRepository().resolve("HEAD").getName());
        }
    }

    @Test
    public void remoteStatusShouldNotExposePasswordInRemoteUrl() throws Exception {
        Path repo = initRepo("secret-remote-repo");
        try (Git git = Git.open(repo.toFile())) {
            git.remoteAdd().setName("origin")
                    .setUri(new org.eclipse.jgit.transport.URIish("https://bob:s3cret@example.com/r.git")).call();
        }
        var status = service.getRemoteStatus(workspace(repo));
        assertFalse(status.remoteUrl.contains("s3cret"));
        assertTrue(status.remoteUrl.contains("example.com/r.git"));
    }

    private Path initRepo(String name) throws Exception {
        Path repo = tempDir.resolve(name);
        try (Git git = Git.init().setInitialBranch("main").setDirectory(repo.toFile()).call()) {
            Files.writeString(repo.resolve("README.md"), "initial", StandardCharsets.UTF_8);
            commitAll(git, "Initial commit");
        }
        return repo;
    }

    private static void commitAll(Git git, String message) throws Exception {
        git.add().addFilepattern(".").call();
        git.add().addFilepattern(".").setUpdate(true).call();
        git.commit().setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").setMessage(message).call();
    }

    private static Workspace workspace(Path path) {
        Workspace workspace = new Workspace();
        workspace.setType(WorkspaceType.LOCAL);
        workspace.setGitAuthType(GitAuthType.NONE);
        workspace.setPath(path.toString());
        return workspace;
    }
}
