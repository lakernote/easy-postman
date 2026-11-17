package com.laker.postman.plugin.git.internal;

import com.laker.postman.model.GitStatusCheck;
import org.eclipse.jgit.api.Git;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.*;

public class GitConflictDetectorScopeTest {

    private static final java.util.List<String> MANAGED = java.util.List.of("api/collections.json", "api/environments.json");

    @Test
    public void sourceChangesOutsideScopeShouldNotEnableCommit() throws Exception {
        Path repo = initRepository();
        Files.writeString(repo.resolve("src/App.java"), "class App { int x; }", StandardCharsets.UTF_8);

        GitStatusCheck check = GitConflictDetector.checkGitStatus(repo.toString(), MANAGED, "api/", "commit", null, null);

        assertFalse(check.canCommit);
        assertFalse(check.hasUncommittedChanges);
        assertTrue(check.modified.isEmpty());
    }

    @Test
    public void scopedChangesShouldUseDataRelativePaths() throws Exception {
        Path repo = initRepository();
        Files.writeString(repo.resolve("src/App.java"), "class App { int x; }", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("api/collections.json"), "[{}]", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("api/environments.json"), "[]", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("api/notes.md"), "ignored", StandardCharsets.UTF_8);

        GitStatusCheck check = GitConflictDetector.checkGitStatus(repo.toString(), MANAGED, "api/", "commit", null, null);

        assertTrue(check.canCommit);
        assertEquals(check.modified, java.util.List.of("collections.json"));
        assertEquals(check.untracked, java.util.List.of("environments.json"));
    }

    @Test
    public void unscopedCheckShouldSeeWholeRepository() throws Exception {
        Path repo = initRepository();
        Files.writeString(repo.resolve("src/App.java"), "class App { int x; }", StandardCharsets.UTF_8);

        GitStatusCheck check = GitConflictDetector.checkGitStatus(repo.toString(), null, "", "commit", null, null);

        assertTrue(check.canCommit);
        assertTrue(check.modified.contains("src/App.java"));
    }

    @Test
    public void sourceChangesShouldNotBlockEmbeddedPullWhenRemoteTouchesOtherFiles() throws Exception {
        Path[] repos = cloneWithRemoteUpdate("src/Other.java");
        Files.writeString(repos[0].resolve("src/App.java"), "class App { int local; }", StandardCharsets.UTF_8);

        GitStatusCheck check = GitConflictDetector.checkGitStatus(repos[0].toString(), MANAGED, "api/", "pull", null, null);

        assertFalse(check.hasUncommittedChanges);
        assertEquals(check.remoteCommitsBehind, 1);
        assertTrue(check.canPull, String.join("; ", check.warnings));
    }

    @Test
    public void sourceChangesShouldBlockEmbeddedPullWhenRemoteTouchesSameFile() throws Exception {
        Path[] repos = cloneWithRemoteUpdate("src/App.java");
        Files.writeString(repos[0].resolve("src/App.java"), "class App { int local; }", StandardCharsets.UTF_8);

        GitStatusCheck check = GitConflictDetector.checkGitStatus(repos[0].toString(), MANAGED, "api/", "pull", null, null);

        assertFalse(check.canPull);
    }

    /**
     * 返回 [本地克隆, 远程裸仓库]；远程比本地多一个修改了 remoteFile 的提交。
     */
    private static Path[] cloneWithRemoteUpdate(String remoteFile) throws Exception {
        Path source = initRepository();
        Path bare = Files.createTempDirectory("git-scope-origin");
        Git.cloneRepository().setURI(source.toUri().toString()).setDirectory(bare.toFile()).setBare(true).call().close();
        Path local = Files.createTempDirectory("git-scope-local");
        Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(local.toFile()).call().close();
        Path other = Files.createTempDirectory("git-scope-other");
        try (Git git = Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(other.toFile()).call()) {
            Files.writeString(other.resolve(remoteFile), "class Remote {}", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("remote").setSign(false)
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
            git.push().call();
        }
        return new Path[]{local, bare};
    }

    private static Path initRepository() throws Exception {
        Path repo = Files.createTempDirectory("git-scope-status");
        Files.createDirectories(repo.resolve("src"));
        Files.createDirectories(repo.resolve("api"));
        Files.writeString(repo.resolve("src/App.java"), "class App {}", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("api/collections.json"), "[]", StandardCharsets.UTF_8);
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setSign(false)
                    .setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
        }
        return repo;
    }
}
