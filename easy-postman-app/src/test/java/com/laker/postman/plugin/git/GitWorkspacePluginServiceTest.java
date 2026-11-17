package com.laker.postman.plugin.git;

import com.laker.postman.model.GitAuthType;
import com.laker.postman.model.GitBranchInfo;
import com.laker.postman.model.GitFileChange;
import com.laker.postman.model.GitOperationResult;
import com.laker.postman.model.GitOperation;
import com.laker.postman.model.GitRepoSource;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class GitWorkspacePluginServiceTest {

    private final GitWorkspacePluginService service = new GitWorkspacePluginService();
    private Path tempDir;
    private Properties settingsProps;
    private Properties settingsBackup;

    @BeforeMethod
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("easy-postman-git-service");
        settingsProps = getSettingsProperties();
        settingsBackup = new Properties();
        settingsBackup.putAll(settingsProps);
        settingsProps.setProperty(
                "git_diff_large_file_threshold_mb",
                String.valueOf(SettingManager.DEFAULT_GIT_DIFF_LARGE_FILE_THRESHOLD_MB)
        );
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws IOException {
        if (settingsProps != null && settingsBackup != null) {
            settingsProps.clear();
            settingsProps.putAll(settingsBackup);
        }
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup for temp test repositories.
                        }
                    });
        }
    }

    @Test
    public void pullUpdatesShouldPreserveNonConflictingUntrackedFiles() throws Exception {
        Path remote = createBareRemote();
        Path seed = seedRemote(remote);
        Path workspacePath = cloneRemote(remote, "main");
        Path scratch = workspacePath.resolve("scratch.txt");
        Files.writeString(scratch, "local scratch", StandardCharsets.UTF_8);

        try (Git git = Git.open(seed.toFile())) {
            writeAndCommit(git, seed.resolve("remote.txt"), "remote update", "Add remote file");
            git.push().setRemote("origin").add("refs/heads/main:refs/heads/main").call();
        }

        GitOperationResult result = service.pullUpdates(workspace(workspacePath, GitRepoSource.CLONED));

        assertTrue(result.success);
        assertTrue(Files.exists(scratch), "Plain pull must not delete non-conflicting untracked files");
        assertEquals(Files.readString(workspacePath.resolve("remote.txt")), "remote update");
    }

    @Test
    public void stashChangesShouldIncludeUntrackedFiles() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("stash-workspace");
        Path tracked = workspacePath.resolve("README.md");
        Path untracked = workspacePath.resolve("local-only.txt");
        Files.writeString(tracked, "changed", StandardCharsets.UTF_8);
        Files.writeString(untracked, "untracked", StandardCharsets.UTF_8);

        GitOperationResult stashResult = service.stashChanges(workspace(workspacePath, GitRepoSource.INITIALIZED));

        assertTrue(stashResult.success);
        assertTrue(stashResult.affectedFiles.contains("local-only.txt"));
        assertFalse(Files.exists(untracked), "Stash should remove untracked files from the working tree");

        GitOperationResult popResult = service.popStashChanges(workspace(workspacePath, GitRepoSource.INITIALIZED));

        assertTrue(popResult.success);
        assertEquals(Files.readString(untracked), "untracked");
        assertEquals(Files.readString(tracked), "changed");
    }

    @Test
    public void addRemoteRepositoryShouldPersistSshAuthentication() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("ssh-remote-workspace");
        Workspace workspace = workspace(workspacePath, GitRepoSource.INITIALIZED);

        service.addRemoteRepository(
                workspace,
                "git@example.com:team/repo.git",
                "main",
                GitAuthType.SSH_KEY,
                "",
                "",
                "",
                "/Users/test/.ssh/id_ed25519",
                "optional-passphrase"
        );

        assertEquals(workspace.getGitAuthType(), GitAuthType.SSH_KEY);
        assertEquals(workspace.getSshPrivateKeyPath(), "/Users/test/.ssh/id_ed25519");
        assertEquals(workspace.getSshPassphrase(), "optional-passphrase");
    }

    @Test
    public void forcePullUpdatesShouldResetFromConfiguredUpstreamBranch() throws Exception {
        Path remote = createBareRemote();
        Path seed = seedRemote(remote);
        Path workspacePath = cloneRemote(remote, "main");
        configureLocalBranchTracking(workspacePath, "feature", "origin", "main");

        try (Git git = Git.open(seed.toFile())) {
            writeAndCommit(git, seed.resolve("README.md"), "remote main update", "Update main");
            git.push().setRemote("origin").add("refs/heads/main:refs/heads/main").call();
        }

        GitOperationResult result = service.forcePullUpdates(workspace(workspacePath, GitRepoSource.CLONED));

        assertTrue(result.success);
        assertEquals(Files.readString(workspacePath.resolve("README.md")), "remote main update");
    }

    @Test
    public void forcePushChangesShouldPushCurrentBranchToConfiguredUpstreamBranch() throws Exception {
        Path remote = createBareRemote();
        seedRemote(remote);
        Path workspacePath = cloneRemote(remote, "main");
        configureLocalBranchTracking(workspacePath, "feature", "origin", "main");

        try (Git git = Git.open(workspacePath.toFile())) {
            writeAndCommit(git, workspacePath.resolve("README.md"), "local feature overwrite", "Overwrite main from feature");
        }

        GitOperationResult result = service.forcePushChanges(workspace(workspacePath, GitRepoSource.CLONED));

        assertTrue(result.success);
        Path verifyPath = cloneRemote(remote, "main", "verify-force-push");
        assertEquals(Files.readString(verifyPath.resolve("README.md")), "local feature overwrite");
    }

    @Test
    public void listBranchesShouldIncludeLocalAndRemoteBranches() throws Exception {
        Path remote = createBareRemote();
        Path seed = seedRemote(remote);
        createAndPushBranch(seed, "feature", "feature content");
        Path workspacePath = cloneRemote(remote, "main");

        List<GitBranchInfo> branches = service.listBranches(workspace(workspacePath, GitRepoSource.CLONED));

        assertTrue(branches.stream().anyMatch(branch -> branch.getName().equals("main")
                && !branch.isRemote()
                && branch.isCurrent()));
        assertTrue(branches.stream().anyMatch(branch -> branch.getName().equals("origin/feature")
                && branch.isRemote()
                && !branch.isCurrent()));
    }

    @Test
    public void listBranchesShouldFoldTrackedRemoteIntoLocalBranch() throws Exception {
        Path remote = createBareRemote();
        seedRemote(remote);
        Path workspacePath = cloneRemote(remote, "main");

        List<GitBranchInfo> branches = service.listBranches(workspace(workspacePath, GitRepoSource.CLONED));

        assertTrue(branches.stream().anyMatch(branch -> branch.getName().equals("main")
                && !branch.isRemote()
                && branch.isCurrent()
                && "origin/main".equals(branch.getTrackingBranch())));
        assertFalse(branches.stream().anyMatch(branch -> branch.getName().equals("origin/main")
                && branch.isRemote()));
    }

    @Test
    public void listBranchesShouldShowAheadAndBehindCounts() throws Exception {
        Path remote = createBareRemote();
        Path seed = seedRemote(remote);
        Path workspacePath = cloneRemote(remote, "main");
        Workspace workspace = workspace(workspacePath, GitRepoSource.CLONED);

        try (Git git = Git.open(workspacePath.toFile())) {
            writeAndCommit(git, workspacePath.resolve("local.txt"), "local", "Local change");
        }
        try (Git git = Git.open(seed.toFile())) {
            writeAndCommit(git, seed.resolve("remote.txt"), "remote", "Remote change");
            git.push().setRemote("origin").add("refs/heads/main:refs/heads/main").call();
        }
        service.fetchBranches(workspace);

        List<GitBranchInfo> branches = service.listBranches(workspace);

        assertTrue(branches.stream().anyMatch(branch -> branch.getName().equals("main")
                && branch.getAheadCount() == 1
                && branch.getBehindCount() == 1));
    }

    @Test
    public void publishBranchShouldPushCurrentBranchAndSetUpstream() throws Exception {
        Path remote = createBareRemote();
        seedRemote(remote);
        Path workspacePath = cloneRemote(remote, "main");
        Workspace workspace = workspace(workspacePath, GitRepoSource.CLONED);
        service.createBranch(workspace, "feature/new-api");
        try (Git git = Git.open(workspacePath.toFile())) {
            writeAndCommit(git, workspacePath.resolve("feature.txt"), "feature", "Feature change");
        }

        GitOperationResult result = service.publishBranch(workspace);
        List<GitBranchInfo> branches = service.listBranches(workspace);

        assertTrue(result.success);
        assertEquals(workspace.getCurrentBranch(), "feature/new-api");
        assertEquals(workspace.getRemoteBranch(), "origin/feature/new-api");
        assertTrue(branches.stream().anyMatch(branch -> branch.getName().equals("feature/new-api")
                && "origin/feature/new-api".equals(branch.getTrackingBranch())));
        Path verifyPath = cloneRemote(remote, "feature/new-api", "verify-publish");
        assertEquals(Files.readString(verifyPath.resolve("feature.txt")), "feature");
    }

    @Test
    public void publishBranchShouldKeepPublishSuccessWhenPostPublishFetchFails() throws IOException {
        String source = Files.readString(repositoryRoot().resolve(
                "easy-postman-app/src/main/java/com/laker/postman/plugin/git/GitWorkspacePluginService.java"));
        String method = source.substring(
                source.indexOf("public GitOperationResult publishBranch"),
                source.indexOf("\n    @Override\n    public GitOperationResult pullUpdates", source.indexOf("public GitOperationResult publishBranch"))
        );

        int metadataUpdate = method.indexOf("updateWorkspaceBranchMetadata(git, workspace);", method.indexOf("config.save();"));
        int fetch = method.indexOf("git.fetch()");
        int fetchWarning = method.indexOf("catch (Exception fetchException)", fetch);

        assertTrue(metadataUpdate > 0, "Published branch metadata should be updated immediately after upstream config is saved.");
        assertTrue(fetch > metadataUpdate, "Post-publish fetch should run after workspace metadata is already updated.");
        assertTrue(fetchWarning > fetch, "Post-publish fetch must be best-effort and must not fail the publish operation.");
    }

    @Test
    public void switchBranchShouldMapRemoteTrackingBranchToExistingLocalBranch() throws Exception {
        Path remote = createBareRemote();
        seedRemote(remote);
        Path workspacePath = cloneRemote(remote, "main");
        Workspace workspace = workspace(workspacePath, GitRepoSource.CLONED);

        GitOperationResult result = service.switchBranch(workspace, "origin/main");

        assertTrue(result.success);
        assertEquals(workspace.getCurrentBranch(), "main");
        assertEquals(workspace.getRemoteBranch(), "origin/main");
    }

    @Test
    public void switchBranchShouldCheckoutExistingLocalBranchAndUpdateWorkspaceMetadata() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("switch-local-workspace");
        try (Git git = Git.open(workspacePath.toFile())) {
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            writeAndCommit(git, workspacePath.resolve("README.md"), "feature local", "Feature change");
            git.checkout().setName("main").call();
        }
        Workspace workspace = workspace(workspacePath, GitRepoSource.INITIALIZED);

        GitOperationResult result = service.switchBranch(workspace, "feature");

        assertTrue(result.success);
        assertEquals(workspace.getCurrentBranch(), "feature");
        assertEquals(Files.readString(workspacePath.resolve("README.md")), "feature local");
    }

    @Test
    public void switchBranchShouldCreateTrackingLocalBranchFromRemoteBranch() throws Exception {
        Path remote = createBareRemote();
        Path seed = seedRemote(remote);
        createAndPushBranch(seed, "feature", "feature remote");
        Path workspacePath = cloneRemote(remote, "main");
        Workspace workspace = workspace(workspacePath, GitRepoSource.CLONED);

        GitOperationResult result = service.switchBranch(workspace, "origin/feature");

        assertTrue(result.success);
        assertEquals(workspace.getCurrentBranch(), "feature");
        assertEquals(workspace.getRemoteBranch(), "origin/feature");
        assertEquals(Files.readString(workspacePath.resolve("README.md")), "feature remote");
    }

    @Test
    public void fetchBranchesShouldUpdateRemoteBranchList() throws Exception {
        Path remote = createBareRemote();
        Path seed = seedRemote(remote);
        Path workspacePath = cloneRemote(remote, "main");
        createAndPushBranch(seed, "feature", "feature remote");
        Workspace workspace = workspace(workspacePath, GitRepoSource.CLONED);

        GitOperationResult result = service.fetchBranches(workspace);
        List<GitBranchInfo> branches = service.listBranches(workspace);

        assertTrue(result.success);
        assertTrue(branches.stream().anyMatch(branch -> branch.getName().equals("origin/feature")
                && branch.isRemote()));
    }

    @Test
    public void createBranchShouldCreateAndCheckoutLocalBranch() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("create-branch-workspace");
        Workspace workspace = workspace(workspacePath, GitRepoSource.INITIALIZED);

        GitOperationResult result = service.createBranch(workspace, "feature/new-api");

        assertTrue(result.success);
        assertEquals(workspace.getCurrentBranch(), "feature/new-api");
        List<GitBranchInfo> branches = service.listBranches(workspace);
        assertTrue(branches.stream().anyMatch(branch -> branch.getName().equals("feature/new-api")
                && branch.isCurrent()
                && !branch.isRemote()));
    }

    @Test
    public void deleteBranchShouldRejectCurrentBranch() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("delete-current-branch-workspace");
        Workspace workspace = workspace(workspacePath, GitRepoSource.INITIALIZED);

        try {
            service.deleteBranch(workspace, "main");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cannot delete the current branch"));
            return;
        }

        throw new AssertionError("Deleting the current branch should fail");
    }

    @Test
    public void deleteBranchShouldDeleteLocalBranch() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("delete-branch-workspace");
        try (Git git = Git.open(workspacePath.toFile())) {
            git.branchCreate().setName("feature/delete-me").call();
        }
        Workspace workspace = workspace(workspacePath, GitRepoSource.INITIALIZED);

        GitOperationResult result = service.deleteBranch(workspace, "feature/delete-me");
        List<GitBranchInfo> branches = service.listBranches(workspace);

        assertTrue(result.success);
        assertFalse(branches.stream().anyMatch(branch -> branch.getName().equals("feature/delete-me")));
    }

    @Test
    public void forceDeleteBranchShouldDeleteUnmergedLocalBranch() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("force-delete-branch-workspace");
        try (Git git = Git.open(workspacePath.toFile())) {
            git.checkout().setCreateBranch(true).setName("feature/unmerged").call();
            writeAndCommit(git, workspacePath.resolve("feature.txt"), "unmerged", "Unmerged feature");
            git.checkout().setName("main").call();
        }
        Workspace workspace = workspace(workspacePath, GitRepoSource.INITIALIZED);

        GitOperationResult result = service.deleteBranch(workspace, "feature/unmerged", true);
        List<GitBranchInfo> branches = service.listBranches(workspace);

        assertTrue(result.success);
        assertFalse(branches.stream().anyMatch(branch -> branch.getName().equals("feature/unmerged")));
    }

    @Test
    public void listWorkingTreeChangesShouldIncludeTrackedAndUntrackedFiles() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-summary-workspace");
        Files.writeString(workspacePath.resolve("README.md"), "changed", StandardCharsets.UTF_8);
        Files.writeString(workspacePath.resolve("new-request.json"), "{}", StandardCharsets.UTF_8);

        List<GitFileChange> changes = service.listWorkingTreeChanges(workspace(workspacePath, GitRepoSource.INITIALIZED));

        assertTrue(changes.stream().anyMatch(change -> change.getPath().equals("README.md")
                && change.getType() == GitFileChange.Type.MODIFIED));
        assertTrue(changes.stream().anyMatch(change -> change.getPath().equals("new-request.json")
                && change.getType() == GitFileChange.Type.UNTRACKED));
    }

    @Test
    public void gitOperationsShouldExplainWhenWorkspaceRepositoryMetadataIsMissing() throws Exception {
        Path workspacePath = tempDir.resolve("webdav-restored-without-git");
        Files.createDirectories(workspacePath);

        try {
            service.listWorkingTreeChanges(workspace(workspacePath, GitRepoSource.INITIALIZED));
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Git"));
            assertTrue(e.getMessage().contains(workspacePath.getFileName().toString()));
            assertFalse(e.getMessage().contains("RepositoryNotFoundException"));
            return;
        }

        throw new AssertionError("Git operations should fail clearly when .git metadata is missing");
    }

    @Test
    public void getWorkingTreeDiffShouldRenderTrackedAndUntrackedPatch() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-content-workspace");
        Files.writeString(workspacePath.resolve("README.md"), "changed", StandardCharsets.UTF_8);
        Files.writeString(workspacePath.resolve("new-request.json"), "{\n  \"name\": \"new\"\n}", StandardCharsets.UTF_8);
        Workspace workspace = workspace(workspacePath, GitRepoSource.INITIALIZED);

        String trackedDiff = service.getWorkingTreeDiff(workspace, "README.md");
        String untrackedDiff = service.getWorkingTreeDiff(workspace, "new-request.json");

        assertTrue(trackedDiff.contains("-initial"));
        assertTrue(trackedDiff.contains("+changed"));
        assertTrue(untrackedDiff.contains("+++ b/new-request.json"));
        assertTrue(untrackedDiff.contains("+  \"name\": \"new\""));
    }

    @Test
    public void getWorkingTreeDiffShouldSkipBinaryUntrackedFiles() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-binary-workspace");
        Files.write(workspacePath.resolve("binary.bin"), new byte[]{0, (byte) 0xC3, 0x28, 1});

        String diff = service.getWorkingTreeDiff(workspace(workspacePath, GitRepoSource.INITIALIZED), "binary.bin");

        assertEquals(diff, "");
    }

    @Test
    public void getWorkingTreeDiffShouldSkipLargeUntrackedFiles() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-large-workspace");
        Files.writeString(workspacePath.resolve("large.txt"),
                "x".repeat((int) SettingManager.getGitDiffLargeFileThresholdBytes() + 1),
                StandardCharsets.UTF_8);

        String diff = service.getWorkingTreeDiff(workspace(workspacePath, GitRepoSource.INITIALIZED), "large.txt");

        assertTrue(diff.contains(oneFileSummary(MessageKeys.GIT_DIFF_SUMMARY_UNSTAGED)));
        assertTrue(diff.contains(I18nUtil.getMessage(
                MessageKeys.GIT_DIFF_LARGE_UNTRACKED_SKIPPED_NOTICE,
                SettingManager.getGitDiffLargeFileThresholdMb()
        )));
        assertTrue(diff.contains(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_WORKTREE) + ":"));
    }

    @Test
    public void getWorkingTreeDiffShouldTruncateUntrackedTextPreview() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-large-untracked-preview-workspace");
        Files.writeString(workspacePath.resolve("preview.txt"), "x".repeat(700 * 1024), StandardCharsets.UTF_8);

        String diff = service.getWorkingTreeDiff(workspace(workspacePath, GitRepoSource.INITIALIZED), "preview.txt");

        assertTrue(diff.length() < 540 * 1024);
        assertTrue(diff.contains("+++ b/preview.txt"));
        assertTrue(diff.contains(I18nUtil.getMessage(MessageKeys.GIT_DIFF_TRUNCATED_NOTICE)));
    }

    @Test
    public void getWorkingTreeDiffShouldSummarizeTrackedFileAboveDefaultLargeFileThreshold() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-large-tracked-workspace");
        Path largeFile = workspacePath.resolve("large.txt");
        String original = largeMultilineContent("old head");
        assertTrue(original.getBytes(StandardCharsets.UTF_8).length > SettingManager.getGitDiffLargeFileThresholdBytes());
        try (Git git = Git.open(workspacePath.toFile())) {
            writeAndCommit(git, largeFile, original, "Add large text file");
        }
        Files.writeString(largeFile, original.replace("old head", "new head"), StandardCharsets.UTF_8);

        String diff = service.getWorkingTreeDiff(workspace(workspacePath, GitRepoSource.INITIALIZED), "large.txt");

        assertTrue(diff.contains(oneFileSummary(MessageKeys.GIT_DIFF_SUMMARY_UNSTAGED)));
        assertTrue(diff.contains(largeSkippedNotice()));
        assertTrue(diff.contains(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_WORKTREE) + ":"));
    }

    @Test
    public void getWorkingTreeDiffShouldRenderTrackedDiffBelowDefaultLargeFileThreshold() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-medium-tracked-workspace");
        Path mediumFile = workspacePath.resolve("medium.txt");
        String original = mediumMultilineContent("old head");
        int originalBytes = original.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(originalBytes > 1024 * 1024);
        assertTrue(originalBytes < SettingManager.getGitDiffLargeFileThresholdBytes());
        try (Git git = Git.open(workspacePath.toFile())) {
            writeAndCommit(git, mediumFile, original, "Add medium text file");
        }
        Files.writeString(mediumFile, original.replace("old head", "new head"), StandardCharsets.UTF_8);

        String diff = service.getWorkingTreeDiff(workspace(workspacePath, GitRepoSource.INITIALIZED), "medium.txt");

        assertTrue(diff.contains("-old head"));
        assertTrue(diff.contains("+new head"));
        assertFalse(diff.contains(largeSkippedNotice()));
    }

    @Test
    public void getWorkingTreeDiffShouldSummarizeVeryLargeTrackedPatchInput() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-truncated-workspace");
        Files.writeString(workspacePath.resolve("README.md"), "x".repeat(9 * 1024 * 1024), StandardCharsets.UTF_8);

        String diff = service.getWorkingTreeDiff(workspace(workspacePath, GitRepoSource.INITIALIZED), "README.md");

        assertTrue(diff.length() < 128 * 1024);
        assertTrue(diff.contains(oneFileSummary(MessageKeys.GIT_DIFF_SUMMARY_UNSTAGED)));
        assertTrue(diff.contains(largeSkippedNotice()));
        assertTrue(diff.contains(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_WORKTREE) + ":"));
    }

    @Test
    public void getWorkingTreeDiffShouldSummarizeLargeFileWithStagedAndUnstagedChanges() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-large-staged-unstaged-workspace");
        Path collections = workspacePath.resolve("collections.json");
        try (Git git = Git.open(workspacePath.toFile())) {
            writeAndCommit(git, collections, largeSingleLineContent("committed"), "Add large collection");
            Files.writeString(collections, largeSingleLineContent("staged"), StandardCharsets.UTF_8);
            git.add().addFilepattern("collections.json").call();
            Files.writeString(collections, largeSingleLineContent("unstaged"), StandardCharsets.UTF_8);
        }

        String diff = service.getWorkingTreeDiff(workspace(workspacePath, GitRepoSource.INITIALIZED), "collections.json");

        assertTrue(diff.length() < 128 * 1024);
        assertTrue(diff.contains(oneFileSummary(MessageKeys.GIT_DIFF_SUMMARY_STAGED)));
        assertTrue(diff.contains(oneFileSummary(MessageKeys.GIT_DIFF_SUMMARY_UNSTAGED)));
        assertTrue(diff.contains(largeSkippedNotice()));
        assertTrue(diff.contains(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_STAGED) + ":"));
        assertTrue(diff.contains(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_WORKTREE) + ":"));
    }

    @Test
    public void getWorkingTreeDiffShouldSummarizeLargeStagedBlobWhenWorktreeFileIsSmall() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-large-index-small-worktree-workspace");
        Path collections = workspacePath.resolve("collections.json");
        try (Git git = Git.open(workspacePath.toFile())) {
            writeAndCommit(git, collections, largeSingleLineContent("committed"), "Add large collection");
            Files.writeString(collections, largeSingleLineContent("staged"), StandardCharsets.UTF_8);
            git.add().addFilepattern("collections.json").call();
            Files.writeString(collections, "{\"small\":true}\n", StandardCharsets.UTF_8);
        }

        String diff = service.getWorkingTreeDiff(workspace(workspacePath, GitRepoSource.INITIALIZED), "collections.json");

        assertTrue(diff.length() < 128 * 1024);
        assertTrue(diff.contains(oneFileSummary(MessageKeys.GIT_DIFF_SUMMARY_STAGED)));
        assertTrue(diff.contains(oneFileSummary(MessageKeys.GIT_DIFF_SUMMARY_UNSTAGED)));
        assertTrue(diff.contains(largeSkippedNotice()));
        assertTrue(diff.contains(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_STAGED) + ":"));
        assertTrue(diff.contains(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_WORKTREE) + ":"));
    }

    @Test
    public void getWorkingTreeDiffShouldShowStagedAndUnstagedShortStats() throws Exception {
        Path workspacePath = initRepositoryWithInitialCommit("diff-staged-unstaged-workspace");
        Path readme = workspacePath.resolve("README.md");
        Files.writeString(readme, "initial\nstaged\n", StandardCharsets.UTF_8);
        try (Git git = Git.open(workspacePath.toFile())) {
            git.add().addFilepattern("README.md").call();
        }
        Files.writeString(readme, "initial\nstaged\nunstaged\n", StandardCharsets.UTF_8);

        String diff = service.getWorkingTreeDiff(workspace(workspacePath, GitRepoSource.INITIALIZED), "README.md");

        assertTrue(diff.contains(oneFileSummary(MessageKeys.GIT_DIFF_SUMMARY_STAGED)));
        assertTrue(diff.contains(oneFileSummary(MessageKeys.GIT_DIFF_SUMMARY_UNSTAGED)));
    }

    @Test
    public void embeddedWorkspaceShouldOnlyExposeAndCommitItsDataFiles() throws Exception {
        Path repo = initRepositoryWithInitialCommit("code-repo");
        Path dataDir = repo.resolve("api");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("collections.json"), "[]", StandardCharsets.UTF_8);
        Files.writeString(dataDir.resolve("mock_servers.json"), "[]", StandardCharsets.UTF_8);
        Path source = repo.resolve("Main.java");
        Path stagedSource = repo.resolve("Staged.java");
        try (Git git = Git.open(repo.toFile())) {
            Files.writeString(source, "class Main {}", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("seed").call();

            Files.writeString(dataDir.resolve("collections.json"), "[{\"v\":1}]", StandardCharsets.UTF_8);
            Files.delete(dataDir.resolve("mock_servers.json"));
            Files.writeString(dataDir.resolve("environments.json"), "[]", StandardCharsets.UTF_8);
            Files.writeString(dataDir.resolve("notes.md"), "not EasyPostman data", StandardCharsets.UTF_8);
            Files.createDirectories(dataDir.resolve("backups"));
            Files.writeString(dataDir.resolve("backups/old.json"), "[]", StandardCharsets.UTF_8);
            Files.writeString(source, "class Main { int x; }", StandardCharsets.UTF_8);
            Files.writeString(stagedSource, "class Staged {}", StandardCharsets.UTF_8);
            git.add().addFilepattern("Staged.java").call();
        }

        Workspace workspace = localWorkspace(dataDir);
        GitWorkspaceLayout layout = GitWorkspaceLayout.detect(workspace).orElseThrow();
        assertEquals(layout.getScope(), "api/");
        assertFalse(layout.isFullControl());

        List<String> changedPaths = service.listWorkingTreeChanges(workspace).stream()
                .map(GitFileChange::getPath).sorted().toList();
        // 嵌入目录整体由工作区管理：目录内的其他文件同样纳入状态与提交，目录外的源码不受影响
        assertEquals(changedPaths, List.of("backups/old.json", "collections.json", "environments.json",
                "mock_servers.json", "notes.md"));
        assertTrue(service.getWorkingTreeDiff(workspace, "collections.json").contains("\"v\":1"));
        assertTrue(service.checkGitStatus(workspace, GitOperation.COMMIT).canCommit);

        service.commitChanges(workspace, "scoped commit");

        try (Git git = Git.open(repo.toFile())) {
            var status = git.status().call();
            assertTrue(status.getAdded().contains("Staged.java"), "out-of-scope staged file must stay staged");
            assertTrue(status.getModified().contains("Main.java"), "out-of-scope change must stay uncommitted");
            assertTrue(status.getUncommittedChanges().stream().noneMatch(p -> p.startsWith("api/")));
        }
        assertTrue(service.listWorkingTreeChanges(workspace).isEmpty());
        assertFalse(service.checkGitStatus(workspace, GitOperation.COMMIT).canCommit);
        assertEquals(service.getGitHistory(workspace, 10).get(0).getMessage().trim(), "scoped commit");
    }

    @Test
    public void openedRepositoryRootShouldOnlyCommitDataFiles() throws Exception {
        Path repo = initRepositoryWithInitialCommit("opened-root-repo");
        try (Git git = Git.open(repo.toFile())) {
            Files.writeString(repo.resolve("collections.json"), "[]", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("seed").call();
        }
        Files.writeString(repo.resolve("collections.json"), "[{\"v\":2}]", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("README.md"), "changed source", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("Main.java"), "class Main {}", StandardCharsets.UTF_8);
        Workspace workspace = localWorkspace(repo);

        assertEquals(service.listWorkingTreeChanges(workspace).stream().map(GitFileChange::getPath).toList(),
                List.of("collections.json"));

        service.commitChanges(workspace, "data only");

        try (Git git = Git.open(repo.toFile())) {
            var status = git.status().call();
            assertTrue(status.getModified().contains("README.md"));
            assertTrue(status.getUntracked().contains("Main.java"));
            assertFalse(status.getUncommittedChanges().contains("collections.json"));
        }
        assertEquals(service.getChangedFilesBetweenCommits(workspace, "HEAD~1", "HEAD"), List.of("collections.json"));
    }

    @Test
    public void openedRepositoryWithoutOwnershipShouldRejectRepositoryRewrites() throws Exception {
        Path repo = initRepositoryWithInitialCommit("opened-repo");
        try (Git git = Git.open(repo.toFile())) {
            writeAndCommit(git, repo.resolve("pom.xml"), "<project/>", "source");
        }
        Workspace workspace = localWorkspace(repo);

        GitWorkspaceLayout layout = GitWorkspaceLayout.detect(workspace).orElseThrow();
        assertFalse(layout.isEmbedded());
        assertFalse(layout.isFullControl());
        assertRejected(() -> service.stashChanges(workspace));
        assertRejected(() -> service.forcePullUpdates(workspace));
        assertRejected(() -> service.forcePushChanges(workspace));
        assertRejected(() -> service.restoreToCommit(workspace, "HEAD", false));

        // 旧类型不能单独授予整仓库权限
        workspace.setType(WorkspaceType.GIT);
        assertFalse(GitWorkspaceLayout.detect(workspace).orElseThrow().isFullControl());
        // 旧版本记录的创建来源 + 最后提交与磁盘仓库一致时才认定
        workspace.setGitRepoSource(GitRepoSource.INITIALIZED);
        workspace.setLastCommitId("0123456789012345678901234567890123456789");
        assertFalse(GitWorkspaceLayout.detect(workspace).orElseThrow().isFullControl());
        try (Git git = Git.open(repo.toFile())) {
            workspace.setLastCommitId(git.getRepository().resolve("HEAD").getName());
        }
        assertTrue(GitWorkspaceLayout.detect(workspace).orElseThrow().isFullControl());
    }

    @Test
    public void ownershipShouldComeFromMarkerNotFileNames() throws Exception {
        Path repo = initRepositoryWithInitialCommit("dedicated-repo");
        try (Git git = Git.open(repo.toFile())) {
            writeAndCommit(git, repo.resolve("collections.json"), "[]", "data");
        }
        // 只有数据文件也不代表独占：历史或其他分支可能包含源码
        assertFalse(GitWorkspaceLayout.detect(localWorkspace(repo)).orElseThrow().isFullControl());

        try (Git git = Git.open(repo.toFile())) {
            GitWorkspaceLayout.markOwnedByEasyPostman(git.getRepository());
        }
        assertTrue(GitWorkspaceLayout.detect(localWorkspace(repo)).orElseThrow().isFullControl());
        assertTrue(GitWorkspaceLayout.detectDirectory(repo).orElseThrow().isFullControl());
        // 标记只对仓库根目录生效
        Files.createDirectories(repo.resolve("sub"));
        assertFalse(GitWorkspaceLayout.detectDirectory(repo.resolve("sub")).orElseThrow().isFullControl());
    }

    @Test
    public void initializedWorkspaceShouldBeMarkedAsOwned() throws Exception {
        Path path = tempDir.resolve("created-by-app");
        Workspace workspace = workspace(path, GitRepoSource.INITIALIZED);
        workspace.setName("created");
        Files.createDirectories(path);
        service.prepareGitWorkspace(workspace);
        GitWorkspaceLayout layout = GitWorkspaceLayout.detectDirectory(path).orElseThrow();
        assertTrue(layout.isFullControl());
        assertNotNull(layout.getBranch());
    }

    @Test
    public void commitShouldAcceptChangesThatAreOnlyStaged() throws Exception {
        Path repo = initRepositoryWithInitialCommit("staged-only");
        Workspace workspace = workspace(repo, GitRepoSource.INITIALIZED);
        try (Git git = Git.open(repo.toFile())) {
            Files.writeString(repo.resolve("README.md"), "staged", StandardCharsets.UTF_8);
            git.add().addFilepattern("README.md").call();
        }

        service.commitChanges(workspace, "staged only");

        try (Git git = Git.open(repo.toFile())) {
            assertTrue(git.status().call().isClean());
        }
    }

    @Test
    public void layoutDetectionShouldSupportWorktreesAndMissingDataDirectories() throws Exception {
        Path repo = initRepositoryWithInitialCommit("main-repo");
        Path worktree = tempDir.resolve("linked-worktree");
        Path gitDir = repo.resolve(".git/worktrees/linked");
        Files.createDirectories(gitDir);
        Files.createDirectories(worktree);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n", StandardCharsets.UTF_8);
        Files.writeString(gitDir.resolve("commondir"), "../..\n", StandardCharsets.UTF_8);
        Files.writeString(gitDir.resolve("gitdir"), worktree.resolve(".git").toString() + "\n", StandardCharsets.UTF_8);
        Files.writeString(worktree.resolve(".git"), "gitdir: " + gitDir + "\n", StandardCharsets.UTF_8);

        GitWorkspaceLayout layout = GitWorkspaceLayout.detect(localWorkspace(worktree.resolve("tools/api"))).orElseThrow();
        assertEquals(layout.getRepositoryRoot(), worktree.toRealPath());
        assertEquals(layout.getScope(), "tools/api/");

        assertTrue(GitWorkspaceLayout.detect(localWorkspace(createBareRemote())).isEmpty());
        assertTrue(GitWorkspaceLayout.detect(localWorkspace(tempDir.resolve("plain"))).isEmpty());
    }

    @Test
    public void initializingInsideExistingRepositoryShouldNotCreateNestedRepository() throws Exception {
        Path repo = initRepositoryWithInitialCommit("host-repo");
        Workspace workspace = workspace(repo.resolve("api"), GitRepoSource.INITIALIZED);

        assertRejected(() -> service.prepareGitWorkspace(workspace));
        assertFalse(Files.exists(repo.resolve("api/.git")));
    }

    private void assertRejected(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("Expected operation to be rejected");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private Workspace localWorkspace(Path path) {
        Workspace workspace = new Workspace();
        workspace.setType(WorkspaceType.LOCAL);
        workspace.setGitAuthType(GitAuthType.NONE);
        workspace.setPath(path.toString());
        return workspace;
    }

    private Path createBareRemote() throws Exception {
        Path remote = tempDir.resolve("remote.git");
        try (Git ignored = Git.init().setBare(true).setDirectory(remote.toFile()).call()) {
            return remote;
        }
    }

    private Path seedRemote(Path remote) throws Exception {
        Path seed = tempDir.resolve("seed");
        try (Git git = Git.init().setInitialBranch("main").setDirectory(seed.toFile()).call()) {
            writeAndCommit(git, seed.resolve("README.md"), "initial", "Initial commit");
            git.remoteAdd().setName("origin").setUri(new URIish(remote.toUri().toString())).call();
            git.push().setRemote("origin").add("refs/heads/main:refs/heads/main").call();
        }
        return seed;
    }

    private Path initRepositoryWithInitialCommit(String name) throws Exception {
        Path repo = tempDir.resolve(name);
        try (Git git = Git.init().setInitialBranch("main").setDirectory(repo.toFile()).call()) {
            writeAndCommit(git, repo.resolve("README.md"), "initial", "Initial commit");
        }
        return repo;
    }

    private Path cloneRemote(Path remote, String branch) throws Exception {
        return cloneRemote(remote, branch, "workspace");
    }

    private Path cloneRemote(Path remote, String branch, String directoryName) throws Exception {
        Path workspace = tempDir.resolve(directoryName);
        try (Git ignored = Git.cloneRepository()
                .setURI(remote.toUri().toString())
                .setDirectory(workspace.toFile())
                .setBranch(branch)
                .call()) {
            return workspace;
        }
    }

    private void configureLocalBranchTracking(Path repoPath, String localBranch, String remoteName, String remoteBranch) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            git.branchCreate().setName(localBranch).call();
            git.checkout().setName(localBranch).call();
            var config = git.getRepository().getConfig();
            config.setString("branch", localBranch, "remote", remoteName);
            config.setString("branch", localBranch, "merge", "refs/heads/" + remoteBranch);
            config.save();
        }
    }

    private void createAndPushBranch(Path repoPath, String branchName, String content) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            git.checkout().setCreateBranch(true).setName(branchName).call();
            writeAndCommit(git, repoPath.resolve("README.md"), content, "Update " + branchName);
            git.push().setRemote("origin").add("refs/heads/" + branchName + ":refs/heads/" + branchName).call();
            git.checkout().setName("main").call();
        }
    }

    private void writeAndCommit(Git git, Path file, String content, String message) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
        git.add().addFilepattern(".").call();
        git.commit()
                .setAuthor("EasyPostman Test", "test@example.com")
                .setCommitter("EasyPostman Test", "test@example.com")
                .setMessage(message)
                .call();
    }

    private String largeMultilineContent(String tail) {
        StringBuilder content = new StringBuilder(1024 * 1024 + 128);
        content.append(tail).append('\n');
        for (int i = 0; i < 90_000; i++) {
            content.append("line-").append(i).append(": unchanged content\n");
        }
        return content.toString();
    }

    private String mediumMultilineContent(String tail) {
        StringBuilder content = new StringBuilder(1024 * 1024 + 128);
        content.append(tail).append('\n');
        for (int i = 0; i < 40_000; i++) {
            content.append("line-").append(i).append(": unchanged content\n");
        }
        return content.toString();
    }

    private String largeSingleLineContent(String marker) {
        return "{\"marker\":\"" + marker + "\",\"data\":\"" + "x".repeat(2 * 1024 * 1024) + "\"}\n";
    }

    private String oneFileSummary(String labelKey) {
        return I18nUtil.getMessage(labelKey) + ": "
                + I18nUtil.getMessage(MessageKeys.GIT_DIFF_SUMMARY_FILE_CHANGED, 1);
    }

    private String largeSkippedNotice() {
        return I18nUtil.getMessage(
                MessageKeys.GIT_DIFF_LARGE_SKIPPED_NOTICE,
                SettingManager.getGitDiffLargeFileThresholdMb()
        );
    }

    private static Properties getSettingsProperties() throws Exception {
        Field propsField = SettingManager.class.getDeclaredField("props");
        propsField.setAccessible(true);
        return (Properties) propsField.get(null);
    }

    /**
     * 模拟由 EasyPostman 创建的工作区：仓库已存在时写入归属标记。
     */
    private Workspace workspace(Path path, GitRepoSource repoSource) {
        Workspace workspace = new Workspace();
        workspace.setType(WorkspaceType.GIT);
        workspace.setGitRepoSource(repoSource);
        workspace.setGitAuthType(GitAuthType.NONE);
        workspace.setPath(path.toString());
        if (Files.exists(path.resolve(".git"))) {
            try (Git git = Git.open(path.toFile())) {
                GitWorkspaceLayout.markOwnedByEasyPostman(git.getRepository());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return workspace;
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
