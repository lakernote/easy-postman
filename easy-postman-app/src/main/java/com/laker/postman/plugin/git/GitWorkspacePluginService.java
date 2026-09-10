package com.laker.postman.plugin.git;

import com.laker.postman.model.GitAuthType;
import com.laker.postman.model.GitBranchInfo;
import com.laker.postman.model.GitCommitInfo;
import com.laker.postman.model.GitFileChange;
import com.laker.postman.model.GitOperation;
import com.laker.postman.model.GitOperationResult;
import com.laker.postman.model.GitStatusCheck;
import com.laker.postman.model.GitRepoSource;
import com.laker.postman.model.RemoteStatus;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.plugin.api.service.GitPluginService;
import com.laker.postman.plugin.git.internal.GitConflictDetector;
import com.laker.postman.plugin.git.internal.SshCredentialsProvider;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefLeaseSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class GitWorkspacePluginService implements GitPluginService {

    private static final int GIT_OPERATION_TIMEOUT = 10;
    private static final int MAX_DIFF_PREVIEW_BYTES = 512 * 1024;
    private static final long WORKING_TREE_STATUS_CACHE_NANOS = 500_000_000L;
    private final AtomicReference<CachedWorkingTreeStatus> cachedWorkingTreeStatus = new AtomicReference<>();

    @Override
    public void prepareGitWorkspace(Workspace workspace) throws Exception {
        ensureGitWorkspaceType(workspace);
        if (workspace.getGitRepoSource() == GitRepoSource.CLONED) {
            cloneRepository(workspace);
        } else if (workspace.getGitRepoSource() == GitRepoSource.INITIALIZED) {
            initializeGitRepository(workspace);
        }
    }

    @Override
    public GitStatusCheck checkGitStatus(Workspace workspace, GitOperation operation) {
        return GitConflictDetector.checkGitStatus(
                workspace.getPath(),
                operation.name(),
                getCredentialsProvider(workspace),
                getSshCredentialsProvider(workspace)
        );
    }

    @Override
    public void clearSshCache(String privateKeyPath) {
        if (privateKeyPath != null && !privateKeyPath.isBlank()) {
            SshCredentialsProvider.clearCache(privateKeyPath);
        }
    }

    @Override
    public List<GitBranchInfo> listBranches(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        try (Git git = Git.open(new File(workspace.getPath()))) {
            String currentBranch = git.getRepository().getBranch();
            List<Ref> refs = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            Map<String, String> trackingByLocalBranch = localBranchTrackingMap(git, refs);
            Set<String> trackedRemoteBranches = new HashSet<>(trackingByLocalBranch.values());
            List<GitBranchInfo> branches = new ArrayList<>();
            for (Ref ref : refs) {
                GitBranchInfo branchInfo = toBranchInfo(git, ref, currentBranch, trackingByLocalBranch, trackedRemoteBranches);
                if (branchInfo != null) {
                    branches.add(branchInfo);
                }
            }
            branches.sort((left, right) -> {
                if (left.isCurrent() != right.isCurrent()) {
                    return left.isCurrent() ? -1 : 1;
                }
                if (left.isRemote() != right.isRemote()) {
                    return left.isRemote() ? 1 : -1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            });
            return branches;
        }
    }

    @Override
    public GitOperationResult switchBranch(Workspace workspace, String branchName) throws Exception {
        ensureGitWorkspace(workspace);
        String requestedBranch = normalizeRequestedBranchName(branchName);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Switch Branch";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            var status = git.status().call();
            if (!status.isClean()) {
                throw new IllegalStateException("Cannot switch branches with uncommitted changes. Commit or stash changes first.");
            }

            String localBranch = localBranchNameForCheckout(git, requestedBranch);
            if (git.getRepository().resolve("refs/heads/" + localBranch) != null) {
                git.checkout().setName(localBranch).call();
            } else {
                String remoteBranch = resolveRemoteBranchForCheckout(git, requestedBranch);
                if (remoteBranch == null) {
                    throw new IllegalArgumentException("Branch not found: " + branchName);
                }
                localBranch = remoteBranch.substring(remoteBranch.indexOf('/') + 1);
                git.checkout()
                        .setCreateBranch(true)
                        .setName(localBranch)
                        .setStartPoint(remoteBranch)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .call();
            }

            updateWorkspaceBranchMetadata(git, workspace);
            result.success = true;
            result.message = "Switched to branch " + workspace.getCurrentBranch();
            result.details = "Current branch: " + workspace.getCurrentBranch() + "\n";
        }

        return result;
    }

    @Override
    public GitOperationResult fetchBranches(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Fetch Branches";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            var remotes = git.remoteList().call();
            if (remotes.isEmpty()) {
                throw new IllegalStateException("No remote repository configured, unable to fetch branches");
            }

            var fetchCommand = git.fetch()
                    .setRemote(remotes.get(0).getName())
                    .setRemoveDeletedRefs(true);
            fetchCommand.setTimeout(GIT_OPERATION_TIMEOUT);
            applyAuth(fetchCommand, workspace);
            fetchCommand.call();

            result.success = true;
            result.message = "Fetched remote branches";
            result.details = "Remote: " + remotes.get(0).getName() + "\n";
        }

        return result;
    }

    @Override
    public GitOperationResult createBranch(Workspace workspace, String branchName) throws Exception {
        ensureGitWorkspace(workspace);
        String localBranch = validateLocalBranchName(branchName);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Create Branch";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            if (git.getRepository().resolve("refs/heads/" + localBranch) != null) {
                throw new IllegalArgumentException("Branch already exists: " + localBranch);
            }

            git.checkout()
                    .setCreateBranch(true)
                    .setName(localBranch)
                    .call();

            updateWorkspaceBranchMetadata(git, workspace);
            result.success = true;
            result.message = "Created branch " + localBranch;
            result.details = "Current branch: " + workspace.getCurrentBranch() + "\n";
        }

        return result;
    }

    @Override
    public GitOperationResult deleteBranch(Workspace workspace, String branchName) throws Exception {
        return deleteBranch(workspace, branchName, false);
    }

    @Override
    public GitOperationResult deleteBranch(Workspace workspace, String branchName, boolean force) throws Exception {
        ensureGitWorkspace(workspace);
        String localBranch = validateLocalBranchName(branchName);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Delete Branch";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            String currentBranch = git.getRepository().getBranch();
            if (localBranch.equals(currentBranch)) {
                throw new IllegalStateException("Cannot delete the current branch: " + localBranch);
            }
            boolean hasLocalBranch = git.getRepository().resolve("refs/heads/" + localBranch) != null;
            boolean hasOnlyRemoteBranch = !hasLocalBranch
                    && git.getRepository().resolve("refs/remotes/" + localBranch) != null;
            if (hasOnlyRemoteBranch) {
                throw new IllegalArgumentException("Only local branches can be deleted: " + localBranch);
            }
            if (!hasLocalBranch) {
                throw new IllegalArgumentException("Branch not found: " + localBranch);
            }

            List<String> deletedBranches = git.branchDelete()
                    .setBranchNames(localBranch)
                    .setForce(force)
                    .call();

            result.success = true;
            result.message = "Deleted branch " + localBranch;
            result.details = "Deleted branches: " + String.join(", ", deletedBranches) + "\n";
        }

        return result;
    }

    @Override
    public GitOperationResult publishBranch(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Publish Branch";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            var remotes = git.remoteList().call();
            if (remotes.isEmpty()) {
                throw new IllegalStateException("No remote repository configured, unable to publish branch");
            }

            String currentBranch = git.getRepository().getBranch();
            String tracking = git.getRepository().getConfig().getString("branch", currentBranch, "merge");
            String remoteName = git.getRepository().getConfig().getString("branch", currentBranch, "remote");
            if (tracking != null && remoteName != null && !remoteName.isBlank()) {
                updateWorkspaceBranchMetadata(git, workspace);
                result.success = true;
                result.message = "Branch is already published";
                result.details = "Upstream branch: " + workspace.getRemoteBranch() + "\n";
                return result;
            }

            remoteName = remotes.get(0).getName();
            var pushCommand = git.push()
                    .setRemote(remoteName)
                    .add("refs/heads/" + currentBranch + ":refs/heads/" + currentBranch);
            pushCommand.setTimeout(GIT_OPERATION_TIMEOUT);
            applyAuth(pushCommand, workspace);
            var pushResults = pushCommand.call();

            boolean pushSuccess = false;
            for (var pushResult : pushResults) {
                for (var remoteRefUpdate : pushResult.getRemoteUpdates()) {
                    result.details += "  Branch update: " + remoteRefUpdate.getSrcRef() + " -> "
                            + remoteRefUpdate.getRemoteName() + " (" + remoteRefUpdate.getStatus() + ")\n";
                    if (remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK
                            || remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                        pushSuccess = true;
                    }
                }
            }
            if (!pushSuccess) {
                throw new RuntimeException("Publish branch failed");
            }

            var config = git.getRepository().getConfig();
            config.setString("branch", currentBranch, "remote", remoteName);
            config.setString("branch", currentBranch, "merge", "refs/heads/" + currentBranch);
            config.save();
            updateWorkspaceBranchMetadata(git, workspace);

            try {
                var fetchCommand = git.fetch().setRemote(remoteName);
                fetchCommand.setTimeout(GIT_OPERATION_TIMEOUT);
                applyAuth(fetchCommand, workspace);
                fetchCommand.call();
            } catch (Exception fetchException) {
                log.warn("Published branch {}, but failed to refresh remote refs", currentBranch, fetchException);
                result.details += "Warning: published branch, but refreshing remote refs failed: "
                        + fetchException.getMessage() + "\n";
            }
            result.success = true;
            result.message = "Published branch " + currentBranch;
            result.details += "Upstream branch: " + workspace.getRemoteBranch() + "\n";
        }

        return result;
    }

    @Override
    public GitOperationResult pullUpdates(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Pull";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            String branch = git.getRepository().getBranch();
            String tracking = git.getRepository().getConfig().getString("branch", branch, "merge");
            if (tracking == null) {
                throw new IllegalStateException(
                        "Local branch is not tracking a remote branch, unable to pull. Please set up tracking branch first, e.g.: git branch --set-upstream-to=origin/" + branch);
            }

            var statusBefore = git.status().call();
            String commitIdBefore = getLastCommitId(git);

            if (commitIdBefore == null) {
                result.details += "Detected empty local repository, attempting to pull initial content from remote\n";
                try {
                    var fetchCommand = git.fetch();
                    fetchCommand.setTimeout(GIT_OPERATION_TIMEOUT);
                    applyAuth(fetchCommand, workspace);
                    fetchCommand.call();

                    String remoteName = git.getRepository().getConfig().getString("branch", branch, "remote");
                    if (remoteName == null) {
                        remoteName = "origin";
                    }
                    String remoteBranchName = tracking.startsWith("refs/heads/")
                            ? tracking.substring("refs/heads/".length())
                            : tracking;
                    String remoteRef = "refs/remotes/" + remoteName + "/" + remoteBranchName;
                    ObjectId remoteId = git.getRepository().resolve(remoteRef);
                    if (remoteId == null) {
                        result.success = true;
                        result.message = "Remote repository is empty, nothing to pull";
                        result.details += "Remote repository is currently empty, waiting for initial push\n";
                        return result;
                    }
                } catch (RefNotAdvertisedException e) {
                    result.success = true;
                    result.message = "Remote repository is empty or remote branch does not exist, nothing to pull";
                    result.details += "Remote repository is empty or branch '" + tracking + "' does not exist\n";
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException("Unable to connect to remote repository: " + e.getMessage(), e);
                }
            }

            if (!statusBefore.isClean()) {
                result.details += "Detected local uncommitted content, attempting non-destructive pull\n";
            }

            try {
                var pullCommand = git.pull();
                pullCommand.setTimeout(GIT_OPERATION_TIMEOUT);
                pullCommand.setStrategy(MergeStrategy.RECURSIVE);
                applyAuth(pullCommand, workspace);
                var pullResult = pullCommand.call();
                if (!pullResult.isSuccessful()) {
                    throw new RuntimeException("Git pull failed: " + pullResult);
                }

                String commitIdAfter = getLastCommitId(git);
                if (commitIdBefore == null || !commitIdBefore.equals(commitIdAfter)) {
                    try {
                        if (commitIdBefore != null && commitIdAfter != null) {
                            List<String> changedFiles = getChangedFilesBetweenCommits(workspace, commitIdBefore, commitIdAfter);
                            result.affectedFiles.addAll(changedFiles);
                            result.details += "Pulled new commits, affected files:\n";
                            for (String file : changedFiles) {
                                result.details += "  " + file + "\n";
                            }
                        } else if (commitIdAfter != null) {
                            result.details += "Pulled initial content from remote repository\n";
                        }
                    } catch (Exception e) {
                        log.warn("Failed to get changed files", e);
                        result.details += "Unable to get detailed file change information\n";
                    }
                    result.message = "Successfully pulled updates, " + result.affectedFiles.size() + " files affected";
                } else {
                    result.message = "Already up to date, no updates needed";
                    result.details += "Local repository is already up to date\n";
                }

                workspace.setLastCommitId(commitIdAfter);
                workspace.setUpdatedAt(System.currentTimeMillis());
                result.success = true;
            } catch (RefNotAdvertisedException e) {
                result.success = true;
                result.message = "Remote branch does not exist, nothing to pull";
                result.details += "Remote branch '" + tracking + "' does not exist or is empty\n";
            }
        }

        return result;
    }

    @Override
    public GitOperationResult pushChanges(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Push";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            var remotes = git.remoteList().call();
            if (remotes.isEmpty()) {
                throw new IllegalStateException("No remote repository configured, unable to push");
            }

            String currentBranch = git.getRepository().getBranch();
            String tracking = git.getRepository().getConfig().getString("branch", currentBranch, "merge");
            if (tracking == null) {
                throw new IllegalStateException("Current branch has no upstream branch set, please perform initial push or set upstream branch first");
            }

            String remoteBranchName = tracking.startsWith("refs/heads/")
                    ? tracking.substring("refs/heads/".length())
                    : tracking;
            String remoteName = git.getRepository().getConfig().getString("branch", currentBranch, "remote");
            if (remoteName == null) {
                remoteName = "origin";
            }

            int unpushedCommitsCount = 0;
            try {
                var fetchCommand = git.fetch();
                fetchCommand.setTimeout(GIT_OPERATION_TIMEOUT);
                applyAuth(fetchCommand, workspace);
                fetchCommand.call();

                String localRef = "refs/heads/" + currentBranch;
                String remoteRef = "refs/remotes/" + remoteName + "/" + remoteBranchName;
                ObjectId localId = git.getRepository().resolve(localRef);
                ObjectId remoteId = git.getRepository().resolve(remoteRef);
                if (localId == null) {
                    throw new IllegalStateException("Unable to find local branch: " + currentBranch);
                }

                Iterable<RevCommit> commits = remoteId != null
                        ? git.log().addRange(remoteId, localId).call()
                        : git.log().call();
                for (RevCommit commit : commits) {
                    unpushedCommitsCount++;
                    if (commit.getParentCount() > 0 && (remoteId != null || unpushedCommitsCount <= 10)) {
                        try {
                            var parent = commit.getParent(0);
                            List<DiffEntry> diffs = git.diff()
                                    .setOldTree(prepareTreeParser(git.getRepository(), parent.toObjectId()))
                                    .setNewTree(prepareTreeParser(git.getRepository(), commit.toObjectId()))
                                    .call();
                            for (DiffEntry diff : diffs) {
                                String fileName = diff.getNewPath();
                                if (!result.affectedFiles.contains(fileName)) {
                                    result.affectedFiles.add(fileName);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to get commit files for {}", commit.getName(), e);
                        }
                    }
                }

                if (unpushedCommitsCount == 0) {
                    result.success = true;
                    result.message = "No commits to push, local branch is up to date";
                    result.details = "Local branch " + currentBranch + " is in sync with remote branch "
                            + remoteName + "/" + remoteBranchName + "\n";
                    return result;
                }
            } catch (Exception e) {
                log.warn("Failed to accurately count unpushed commits, proceeding with push", e);
            }

            var pushCommand = git.push().setRemote(remoteName).add(currentBranch + ":" + remoteBranchName);
            pushCommand.setTimeout(GIT_OPERATION_TIMEOUT);
            applyAuth(pushCommand, workspace);
            var pushResults = pushCommand.call();

            result.details += "Push details:\n";
            result.details += "  Local branch: " + currentBranch + "\n";
            result.details += "  Remote branch: " + remoteName + "/" + remoteBranchName + "\n";
            result.details += "  Pushed commits: " + unpushedCommitsCount + "\n";

            boolean pushSuccess = false;
            for (var pushResult : pushResults) {
                String msg = pushResult.getMessages();
                if (msg != null && !msg.isEmpty() && (msg.toLowerCase().contains("error") || msg.toLowerCase().contains("fail"))) {
                    result.details += "  Push message: " + msg + "\n";
                }
                for (var remoteRefUpdate : pushResult.getRemoteUpdates()) {
                    result.details += "  Branch update: " + remoteRefUpdate.getSrcRef() + " -> "
                            + remoteRefUpdate.getRemoteName() + " (" + remoteRefUpdate.getStatus() + ")\n";
                    if (remoteRefUpdate.getMessage() != null && !remoteRefUpdate.getMessage().isEmpty()) {
                        result.details += "    Details: " + remoteRefUpdate.getMessage() + "\n";
                    }
                    if (remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK
                            || remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                        pushSuccess = true;
                    }
                }
            }

            if (!pushSuccess) {
                throw new RuntimeException("Push failed, please check error details in push result");
            }

            result.success = true;
            result.message = "Successfully pushed " + unpushedCommitsCount + " commits, " + result.affectedFiles.size() + " files affected";
        }

        return result;
    }

    @Override
    public GitOperationResult forcePushChanges(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Force Push";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            TrackingRef trackingRef = resolveTrackingRef(git);
            ObjectId expectedRemoteId = git.getRepository().resolve(trackingRef.remoteTrackingRef());
            RefSpec forceRefSpec = new RefSpec("refs/heads/" + trackingRef.localBranch()
                    + ":" + trackingRef.remoteBranchRef()).setForceUpdate(true);
            var pushCommand = git.push()
                    .setRemote(trackingRef.remoteName())
                    .setRefSpecs(forceRefSpec)
                    .setForce(true);
            if (expectedRemoteId != null) {
                pushCommand.setRefLeaseSpecs(new RefLeaseSpec(trackingRef.remoteBranchRef(), expectedRemoteId.name()));
            }
            pushCommand.setTimeout(GIT_OPERATION_TIMEOUT);
            applyAuth(pushCommand, workspace);
            var pushResults = pushCommand.call();

            result.details += "Force push details:\n";
            result.details += "  Local branch: " + trackingRef.localBranch() + "\n";
            result.details += "  Remote branch: " + trackingRef.remoteName() + "/" + trackingRef.remoteBranchName() + "\n";
            result.details += "  Warning: Force push has overwritten remote changes\n";

            boolean pushSuccess = false;
            for (var pushResult : pushResults) {
                for (var remoteRefUpdate : pushResult.getRemoteUpdates()) {
                    result.details += "  Branch update: " + remoteRefUpdate.getSrcRef() + " -> "
                            + remoteRefUpdate.getRemoteName() + " (" + remoteRefUpdate.getStatus() + ")\n";
                    if (remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK
                            || remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                        pushSuccess = true;
                    }
                }
            }

            if (!pushSuccess) {
                throw new RuntimeException("Force push failed");
            }

            result.success = true;
            result.message = "Force push successful, remote changes have been overwritten";
        }

        return result;
    }

    @Override
    public GitOperationResult stashChanges(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Stash";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            var status = git.status().call();
            result.affectedFiles.addAll(status.getModified());
            result.affectedFiles.addAll(status.getChanged());
            result.affectedFiles.addAll(status.getAdded());
            result.affectedFiles.addAll(status.getRemoved());
            result.affectedFiles.addAll(status.getMissing());
            result.affectedFiles.addAll(status.getUntracked());

            var stashResult = git.stashCreate().setIncludeUntracked(true).call();
            if (stashResult == null) {
                throw new RuntimeException("Stash failed, no changes to stash");
            }

            result.success = true;
            result.message = "Successfully stashed " + result.affectedFiles.size() + " file changes";
            result.details = "Stash ID: " + stashResult.getName().substring(0, 8) + "\n";
        }

        return result;
    }

    @Override
    public GitOperationResult popStashChanges(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Pop Stash";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            if (!git.stashList().call().iterator().hasNext()) {
                throw new RuntimeException("No stashed changes found");
            }

            git.stashApply().call();
            git.stashDrop().call();
            var status = git.status().call();
            result.affectedFiles.addAll(status.getModified());
            result.affectedFiles.addAll(status.getChanged());
            result.affectedFiles.addAll(status.getAdded());
            result.affectedFiles.addAll(status.getMissing());
            result.affectedFiles.addAll(status.getUntracked());

            result.success = true;
            result.message = "Successfully restored stashed changes";
            result.details = "Restored files:\n";
            for (String file : result.affectedFiles) {
                result.details += "  " + file + "\n";
            }
        }

        return result;
    }

    @Override
    public GitOperationResult forcePullUpdates(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Force Pull";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            var statusBefore = git.status().call();
            result.affectedFiles.addAll(statusBefore.getModified());
            result.affectedFiles.addAll(statusBefore.getChanged());
            result.affectedFiles.addAll(statusBefore.getAdded());
            result.affectedFiles.addAll(statusBefore.getMissing());
            result.affectedFiles.addAll(statusBefore.getUntracked());

            String commitIdBefore = getLastCommitId(git);
            TrackingRef trackingRef = resolveTrackingRef(git);

            var fetchCmd = git.fetch();
            fetchCmd.setTimeout(GIT_OPERATION_TIMEOUT);
            applyAuth(fetchCmd, workspace);
            fetchCmd.call();

            if (git.getRepository().resolve(trackingRef.remoteTrackingRef()) == null) {
                throw new IllegalStateException("Unable to find upstream branch: "
                        + trackingRef.remoteName() + "/" + trackingRef.remoteBranchName());
            }
            git.reset().setRef(trackingRef.remoteTrackingRef()).setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call();
            git.clean().setCleanDirectories(true).setForce(true).call();

            String commitIdAfter = getLastCommitId(git);
            workspace.setLastCommitId(commitIdAfter);
            workspace.setUpdatedAt(System.currentTimeMillis());

            result.success = true;
            result.message = "Force pull successful, local changes have been discarded";
            result.details = !java.util.Objects.equals(commitIdBefore, commitIdAfter)
                    ? "Pulled new commits\n"
                    : "Already up to date\n";
        }

        return result;
    }

    @Override
    public GitOperationResult commitChanges(Workspace workspace, String message) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Commit";

        try (Git git = Git.open(new File(workspace.getPath()))) {
            var status = git.status().call();
            boolean hasChanges = !status.getAdded().isEmpty()
                    || !status.getModified().isEmpty()
                    || !status.getRemoved().isEmpty()
                    || !status.getUntracked().isEmpty()
                    || !status.getMissing().isEmpty();
            if (!hasChanges) {
                throw new IllegalStateException("No file changes to commit");
            }

            result.affectedFiles.addAll(status.getAdded());
            result.affectedFiles.addAll(status.getModified());
            result.affectedFiles.addAll(status.getRemoved());
            result.affectedFiles.addAll(status.getUntracked());

            git.add().addFilepattern(".").call();
            var commitResult = git.commit().setMessage(message).call();

            workspace.setLastCommitId(getLastCommitId(git));
            workspace.setUpdatedAt(System.currentTimeMillis());

            result.success = true;
            result.message = "Successfully committed " + result.affectedFiles.size() + " file changes";
            result.details = "Commit ID: " + commitResult.getName().substring(0, 8) + "\n";
        }

        return result;
    }

    @Override
    public List<String> getChangedFilesBetweenCommits(Workspace workspace, String oldCommitId, String newCommitId) throws Exception {
        ensureGitWorkspace(workspace);
        try (Git git = Git.open(new File(workspace.getPath()))) {
            ObjectId oldId = git.getRepository().resolve(oldCommitId);
            ObjectId newId = git.getRepository().resolve(newCommitId);
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(prepareTreeParser(git.getRepository(), oldId))
                    .setNewTree(prepareTreeParser(git.getRepository(), newId))
                    .call();
            List<String> files = new ArrayList<>();
            for (DiffEntry entry : diffs) {
                files.add(entry.getNewPath());
            }
            return files;
        }
    }

    @Override
    public List<GitFileChange> listWorkingTreeChanges(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        try (Git git = Git.open(new File(workspace.getPath()))) {
            var status = readAndCacheWorkingTreeStatus(workspace, git);
            Map<String, GitFileChange.Type> changes = new HashMap<>();
            putChanges(changes, status.getAdded(), GitFileChange.Type.ADDED);
            putChanges(changes, status.getChanged(), GitFileChange.Type.MODIFIED);
            putChanges(changes, status.getModified(), GitFileChange.Type.MODIFIED);
            putChanges(changes, status.getRemoved(), GitFileChange.Type.DELETED);
            putChanges(changes, status.getMissing(), GitFileChange.Type.DELETED);
            putChanges(changes, status.getUntracked(), GitFileChange.Type.UNTRACKED);
            putChanges(changes, status.getConflicting(), GitFileChange.Type.CONFLICTING);
            return changes.entrySet().stream()
                    .map(entry -> new GitFileChange(entry.getKey(), entry.getValue()))
                    .sorted((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()))
                    .toList();
        }
    }

    @Override
    public String getWorkingTreeDiff(Workspace workspace, String filePath) throws Exception {
        ensureGitWorkspace(workspace);
        String normalizedPath = normalizeWorkspaceRelativePath(filePath);
        Path workspacePath = Paths.get(workspace.getPath());
        try (Git git = Git.open(new File(workspace.getPath()))) {
            var status = consumeCachedWorkingTreeStatus(workspace, git);
            int largeFileThresholdMb = SettingManager.getGitDiffLargeFileThresholdMb();
            long largeFileThresholdBytes = SettingManager.gitDiffLargeFileThresholdBytes(largeFileThresholdMb);
            if (status.getUntracked().contains(normalizedPath)) {
                return renderUntrackedFileDiff(
                        workspacePath.resolve(normalizedPath),
                        normalizedPath,
                        largeFileThresholdBytes,
                        largeFileThresholdMb
                );
            }
            LargeDiffInputs largeInputs = inspectLargeDiffInputs(git.getRepository(), workspacePath, normalizedPath);
            if (largeInputs.hasLargeInput(largeFileThresholdBytes)) {
                return renderLargeTrackedFileSummary(status, normalizedPath, largeInputs, largeFileThresholdMb);
            }
            return renderJGitTrackedFileDiff(git, normalizedPath);
        }
    }

    private Status readAndCacheWorkingTreeStatus(Workspace workspace, Git git) throws Exception {
        Status status = git.status().call();
        cachedWorkingTreeStatus.set(new CachedWorkingTreeStatus(
                workspaceStatusCacheKey(workspace),
                System.nanoTime(),
                status
        ));
        return status;
    }

    private Status consumeCachedWorkingTreeStatus(Workspace workspace, Git git) throws Exception {
        CachedWorkingTreeStatus cached = cachedWorkingTreeStatus.getAndSet(null);
        if (cached != null
                && cached.workspacePath().equals(workspaceStatusCacheKey(workspace))
                && System.nanoTime() - cached.createdAtNanos() <= WORKING_TREE_STATUS_CACHE_NANOS) {
            return cached.status();
        }
        return git.status().call();
    }

    private String workspaceStatusCacheKey(Workspace workspace) {
        return Paths.get(workspace.getPath()).toAbsolutePath().normalize().toString();
    }

    private String renderLargeTrackedFileSummary(Status status,
                                                 String normalizedPath,
                                                 LargeDiffInputs largeInputs,
                                                 int thresholdMb) {
        StringBuilder result = new StringBuilder();
        if (hasStagedChange(status, normalizedPath)) {
            result.append(summaryLabel(MessageKeys.GIT_DIFF_SUMMARY_STAGED))
                    .append(": ")
                    .append(changedFilesLabel(1))
                    .append('\n');
        }
        if (hasUnstagedChange(status, normalizedPath)) {
            result.append(summaryLabel(MessageKeys.GIT_DIFF_SUMMARY_UNSTAGED))
                    .append(": ")
                    .append(changedFilesLabel(1))
                    .append('\n');
        }
        if (!result.isEmpty()) {
            result.append('\n');
        }
        result.append(I18nUtil.getMessage(MessageKeys.GIT_DIFF_LARGE_SKIPPED_NOTICE, thresholdMb)).append('\n');
        result.append(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_HEAD))
                .append(": ")
                .append(formatBytes(largeInputs.headSize()))
                .append('\n');
        result.append(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_STAGED))
                .append(": ")
                .append(formatBytes(largeInputs.indexSize()))
                .append('\n');
        result.append(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_WORKTREE))
                .append(": ")
                .append(formatBytes(largeInputs.worktreeSize()))
                .append('\n');
        return result.toString();
    }

    private boolean hasStagedChange(Status status, String normalizedPath) {
        return status.getAdded().contains(normalizedPath)
                || status.getChanged().contains(normalizedPath)
                || status.getRemoved().contains(normalizedPath);
    }

    private boolean hasUnstagedChange(Status status, String normalizedPath) {
        return status.getModified().contains(normalizedPath)
                || status.getMissing().contains(normalizedPath);
    }

    private String renderJGitTrackedFileDiff(Git git, String normalizedPath) throws Exception {
        StringBuilder result = new StringBuilder();
        appendDiffStats(
                result,
                MessageKeys.GIT_DIFF_SUMMARY_STAGED,
                diffStats(git.getRepository(), normalizedPath, true)
        );
        appendDiffStats(
                result,
                MessageKeys.GIT_DIFF_SUMMARY_UNSTAGED,
                diffStats(git.getRepository(), normalizedPath, false)
        );
        if (!result.isEmpty()) {
            result.append('\n');
        }

        PreviewOutputStream output = new PreviewOutputStream(MAX_DIFF_PREVIEW_BYTES);
        writeDiffPreview(git, normalizedPath, true, output);
        writeDiffPreview(git, normalizedPath, false, output);
        result.append(output.toString(StandardCharsets.UTF_8));
        if (output.isTruncated()) {
            result.append("\n\n... ")
                    .append(I18nUtil.getMessage(MessageKeys.GIT_DIFF_TRUNCATED_NOTICE))
                    .append('\n');
        }
        return result.toString();
    }

    private LargeDiffInputs inspectLargeDiffInputs(Repository repository, Path workspacePath, String normalizedPath) {
        long headSize = blobSize(repository, "HEAD:" + normalizedPath);
        long indexSize = indexBlobSize(repository, normalizedPath);
        long worktreeSize = worktreeFileSize(workspacePath.resolve(normalizedPath));
        return new LargeDiffInputs(headSize, indexSize, worktreeSize);
    }

    private long blobSize(Repository repository, String revPath) {
        try {
            ObjectId objectId = repository.resolve(revPath);
            if (objectId == null) {
                return -1;
            }
            ObjectLoader loader = repository.open(objectId);
            return loader.getSize();
        } catch (IOException ex) {
            log.debug("Failed to inspect Git blob size for {}", revPath, ex);
            return -1;
        }
    }

    private long indexBlobSize(Repository repository, String normalizedPath) {
        try {
            DirCache dirCache = repository.readDirCache();
            int entryIndex = dirCache.findEntry(normalizedPath);
            if (entryIndex < 0) {
                return -1;
            }
            DirCacheEntry entry = dirCache.getEntry(entryIndex);
            return repository.open(entry.getObjectId()).getSize();
        } catch (IOException ex) {
            log.debug("Failed to inspect Git index blob size for {}", normalizedPath, ex);
            return -1;
        }
    }

    private long worktreeFileSize(Path filePath) {
        try {
            return Files.isRegularFile(filePath) ? Files.size(filePath) : -1;
        } catch (IOException ex) {
            log.debug("Failed to inspect working tree file size for {}", filePath, ex);
            return -1;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "-";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        long kib = Math.round(bytes / 1024.0);
        if (kib < 1024) {
            return kib + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private void writeDiffPreview(Git git,
                                  String normalizedPath,
                                  boolean cached,
                                  PreviewOutputStream output) throws Exception {
        if (output.isTruncated()) {
            return;
        }
        try {
            git.diff()
                    .setCached(cached)
                    .setPathFilter(PathFilter.create(normalizedPath))
                    .setOutputStream(output)
                    .call();
        } catch (Exception ex) {
            if (!hasCause(ex, DiffPreviewTruncatedException.class)) {
                throw ex;
            }
        }
    }

    private void appendDiffStats(StringBuilder result, String labelKey, DiffStats stats) {
        if (stats.isEmpty()) {
            return;
        }
        result.append(summaryLabel(labelKey)).append(": ").append(stats.toShortStat()).append('\n');
    }

    private String summaryLabel(String key) {
        return I18nUtil.getMessage(key);
    }

    private static String changedFilesLabel(int filesChanged) {
        return I18nUtil.getMessage(
                filesChanged == 1
                        ? MessageKeys.GIT_DIFF_SUMMARY_FILE_CHANGED
                        : MessageKeys.GIT_DIFF_SUMMARY_FILES_CHANGED,
                filesChanged
        );
    }

    private DiffStats diffStats(Repository repository, String normalizedPath, boolean cached) throws Exception {
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setPathFilter(PathFilter.create(normalizedPath));
            List<DiffEntry> entries = formatter.scan(
                    cached ? headTreeIterator(repository) : new DirCacheIterator(repository.readDirCache()),
                    cached ? new DirCacheIterator(repository.readDirCache()) : new FileTreeIterator(repository)
            );
            DiffStats stats = new DiffStats(entries.size());
            for (DiffEntry entry : entries) {
                FileHeader header = formatter.toFileHeader(entry);
                EditList edits = header.toEditList();
                for (Edit edit : edits) {
                    stats.addInsertions(edit.getEndB() - edit.getBeginB());
                    stats.addDeletions(edit.getEndA() - edit.getBeginA());
                }
            }
            return stats;
        }
    }

    private AbstractTreeIterator headTreeIterator(Repository repository) throws Exception {
        ObjectId head = repository.resolve("HEAD");
        if (head == null) {
            return new EmptyTreeIterator();
        }
        return prepareTreeParser(repository, head);
    }

    private void putChanges(Map<String, GitFileChange.Type> changes,
                            Set<String> paths,
                            GitFileChange.Type type) {
        for (String path : paths) {
            changes.put(path, type);
        }
    }

    private String renderUntrackedFileDiff(Path filePath,
                                           String displayPath,
                                           long largeFileThresholdBytes,
                                           int thresholdMb) throws IOException {
        if (!Files.isRegularFile(filePath)) {
            return "";
        }
        long fileSize = Files.size(filePath);
        if (fileSize > largeFileThresholdBytes) {
            return renderLargeUntrackedFileSummary(fileSize, thresholdMb);
        }

        UntrackedTextPreview preview = readUntrackedTextPreview(filePath, fileSize);
        if (preview == null) {
            return "";
        }

        List<String> lines = preview.content().isEmpty() ? List.of() : preview.content().lines().toList();
        PreviewOutputStream output = new PreviewOutputStream(MAX_DIFF_PREVIEW_BYTES);
        writePreview(output, "diff --git a/" + displayPath + " b/" + displayPath + "\n");
        writePreview(output, "new file mode 100644\n");
        writePreview(output, "--- /dev/null\n");
        writePreview(output, "+++ b/" + displayPath + "\n");
        writePreview(output, "@@ -0,0 +1," + lines.size() + " @@\n");
        for (String line : lines) {
            writePreview(output, "+" + line + "\n");
            if (output.isTruncated()) {
                break;
            }
        }
        String result = output.toString(StandardCharsets.UTF_8);
        if (preview.truncated() || output.isTruncated()) {
            if (!result.endsWith("\n")) {
                result += "\n";
            }
            result += "\n... " + I18nUtil.getMessage(MessageKeys.GIT_DIFF_TRUNCATED_NOTICE) + "\n";
        }
        return result;
    }

    private String renderLargeUntrackedFileSummary(long worktreeSize, int thresholdMb) {
        return summaryLabel(MessageKeys.GIT_DIFF_SUMMARY_UNSTAGED)
                + ": "
                + changedFilesLabel(1)
                + "\n\n"
                + I18nUtil.getMessage(MessageKeys.GIT_DIFF_LARGE_UNTRACKED_SKIPPED_NOTICE, thresholdMb)
                + "\n"
                + I18nUtil.getMessage(MessageKeys.GIT_DIFF_SIZE_WORKTREE)
                + ": "
                + formatBytes(worktreeSize)
                + "\n";
    }

    private UntrackedTextPreview readUntrackedTextPreview(Path filePath, long fileSize) throws IOException {
        byte[] bytes = readFirstBytes(filePath, (int) Math.min(fileSize, (long) MAX_DIFF_PREVIEW_BYTES + 4));
        if (containsNulByte(bytes)) {
            return null;
        }
        boolean truncated = fileSize > MAX_DIFF_PREVIEW_BYTES;
        int decodeLength = truncated ? Math.min(MAX_DIFF_PREVIEW_BYTES, bytes.length) : bytes.length;
        int minDecodeLength = truncated ? Math.max(0, decodeLength - 4) : decodeLength;
        while (decodeLength >= minDecodeLength) {
            try {
                return new UntrackedTextPreview(decodeUtf8(bytes, decodeLength), truncated);
            } catch (CharacterCodingException e) {
                if (!truncated) {
                    return null;
                }
                decodeLength--;
            }
        }
        return null;
    }

    private byte[] readFirstBytes(Path filePath, int maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(filePath)) {
            return input.readNBytes(Math.max(0, maxBytes));
        }
    }

    private String decodeUtf8(byte[] bytes, int length) throws CharacterCodingException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, length))
                    .toString();
        } catch (CharacterCodingException e) {
            throw e;
        }
    }

    private void writePreview(PreviewOutputStream output, String value) throws IOException {
        if (output.isTruncated()) {
            return;
        }
        try {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(bytes, 0, bytes.length);
        } catch (DiffPreviewTruncatedException ignored) {
            // The preview stream records truncation; callers append a localized notice.
        }
    }

    private boolean containsNulByte(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private record LargeDiffInputs(long headSize, long indexSize, long worktreeSize) {
        private boolean hasLargeInput(long thresholdBytes) {
            return headSize > thresholdBytes
                    || indexSize > thresholdBytes
                    || worktreeSize > thresholdBytes;
        }
    }

    private record CachedWorkingTreeStatus(String workspacePath, long createdAtNanos, Status status) {
    }

    private record UntrackedTextPreview(String content, boolean truncated) {
    }

    private static final class PreviewOutputStream extends OutputStream {
        private final int limit;
        private final ByteArrayOutputStream delegate;
        private boolean truncated;

        private PreviewOutputStream(int limit) {
            this.limit = Math.max(0, limit);
            this.delegate = new ByteArrayOutputStream(Math.min(this.limit, 8192));
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            int remaining = limit - delegate.size();
            if (remaining <= 0) {
                truncated = true;
                throw new DiffPreviewTruncatedException();
            }
            int accepted = Math.min(length, remaining);
            delegate.write(bytes, offset, accepted);
            if (accepted < length) {
                truncated = true;
                throw new DiffPreviewTruncatedException();
            }
        }

        @Override
        public synchronized void write(int value) throws IOException {
            if (delegate.size() >= limit) {
                truncated = true;
                throw new DiffPreviewTruncatedException();
            }
            delegate.write(value);
        }

        private boolean isTruncated() {
            return truncated;
        }

        private String toString(java.nio.charset.Charset charset) {
            return delegate.toString(charset);
        }
    }

    private static final class DiffPreviewTruncatedException extends IOException {
    }

    private static final class DiffStats {
        private final int filesChanged;
        private int insertions;
        private int deletions;

        private DiffStats(int filesChanged) {
            this.filesChanged = filesChanged;
        }

        private void addInsertions(int count) {
            insertions += count;
        }

        private void addDeletions(int count) {
            deletions += count;
        }

        private boolean isEmpty() {
            return filesChanged == 0;
        }

        private String toShortStat() {
            List<String> parts = new ArrayList<>();
            parts.add(changedFilesLabel(filesChanged));
            if (insertions > 0) {
                parts.add(I18nUtil.getMessage(
                        insertions == 1
                                ? MessageKeys.GIT_DIFF_SUMMARY_INSERTION
                                : MessageKeys.GIT_DIFF_SUMMARY_INSERTIONS,
                        insertions
                ));
            }
            if (deletions > 0) {
                parts.add(I18nUtil.getMessage(
                        deletions == 1
                                ? MessageKeys.GIT_DIFF_SUMMARY_DELETION
                                : MessageKeys.GIT_DIFF_SUMMARY_DELETIONS,
                        deletions
                ));
            }
            return String.join(", ", parts);
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalizeWorkspaceRelativePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path is required");
        }
        String normalizedPath = filePath.trim().replace('\\', '/');
        if (normalizedPath.startsWith("/")
                || normalizedPath.contains("../")
                || normalizedPath.endsWith("/..")
                || normalizedPath.equals("..")) {
            throw new IllegalArgumentException("Invalid workspace relative path: " + filePath);
        }
        return normalizedPath;
    }

    @Override
    public void addRemoteRepository(Workspace workspace, String remoteUrl, String remoteBranch,
                                    GitAuthType authType, String username, String password, String token) throws Exception {
        addRemoteRepository(workspace, remoteUrl, remoteBranch, authType, username, password, token, null, null);
    }

    @Override
    public void addRemoteRepository(Workspace workspace, String remoteUrl, String remoteBranch,
                                    GitAuthType authType, String username, String password, String token,
                                    String sshPrivateKeyPath, String sshPassphrase) throws Exception {
        ensureGitWorkspace(workspace);
        if (workspace.getGitRepoSource() != GitRepoSource.INITIALIZED) {
            throw new IllegalStateException("Only Git workspaces of type INITIALIZED can add a remote repository");
        }

        try (Git git = Git.open(new File(workspace.getPath()))) {
            git.remoteAdd().setName("origin").setUri(new URIish(remoteUrl)).call();

            String currentBranch = git.getRepository().getBranch();
            String targetRemoteBranch = remoteBranch;
            if (targetRemoteBranch != null && targetRemoteBranch.startsWith("origin/")) {
                targetRemoteBranch = targetRemoteBranch.substring("origin/".length());
            }
            if (targetRemoteBranch == null || targetRemoteBranch.trim().isEmpty()) {
                targetRemoteBranch = currentBranch;
            }

            var config = git.getRepository().getConfig();
            config.setString("branch", currentBranch, "remote", "origin");
            config.setString("branch", currentBranch, "merge", "refs/heads/" + targetRemoteBranch);
            config.save();

            workspace.setGitRemoteUrl(remoteUrl);
            workspace.setRemoteBranch("origin/" + targetRemoteBranch);
            workspace.setGitAuthType(authType);
            workspace.setGitUsername(username);
            if (authType == GitAuthType.PASSWORD) {
                workspace.setGitPassword(password);
                workspace.setGitToken(null);
                workspace.setSshPrivateKeyPath(null);
                workspace.setSshPassphrase(null);
            } else if (authType == GitAuthType.TOKEN) {
                workspace.setGitToken(token);
                workspace.setGitPassword(null);
                workspace.setSshPrivateKeyPath(null);
                workspace.setSshPassphrase(null);
            } else if (authType == GitAuthType.SSH_KEY) {
                workspace.setSshPrivateKeyPath(sshPrivateKeyPath);
                workspace.setSshPassphrase(sshPassphrase);
                workspace.setGitUsername(null);
                workspace.setGitPassword(null);
                workspace.setGitToken(null);
            } else if (authType == GitAuthType.NONE) {
                workspace.setGitUsername(null);
                workspace.setGitPassword(null);
                workspace.setGitToken(null);
                workspace.setSshPrivateKeyPath(null);
                workspace.setSshPassphrase(null);
            }
            workspace.setUpdatedAt(System.currentTimeMillis());
        }
    }

    @Override
    public RemoteStatus getRemoteStatus(Workspace workspace) throws Exception {
        ensureGitWorkspace(workspace);
        try (Git git = Git.open(new File(workspace.getPath()))) {
            RemoteStatus status = new RemoteStatus();
            var remotes = git.remoteList().call();
            status.hasRemote = !remotes.isEmpty();
            if (status.hasRemote) {
                status.remoteUrl = remotes.get(0).getURIs().get(0).toString();
                String currentBranch = git.getRepository().getBranch();
                String tracking = git.getRepository().getConfig().getString("branch", currentBranch, "merge");
                status.hasUpstream = tracking != null;
                status.currentBranch = currentBranch;
                status.upstreamBranch = tracking;
            }
            return status;
        }
    }

    @Override
    public void convertLocalToGit(Workspace workspace, String localBranch) throws Exception {
        if (workspace.getType() != WorkspaceType.LOCAL) {
            throw new IllegalStateException("Only LOCAL workspaces can be converted to GIT");
        }
        workspace.setType(WorkspaceType.GIT);
        workspace.setGitRepoSource(GitRepoSource.INITIALIZED);
        workspace.setCurrentBranch(localBranch != null && !localBranch.isEmpty() ? localBranch : "master");
        initializeGitRepository(workspace);
        workspace.setUpdatedAt(System.currentTimeMillis());
    }

    @Override
    public List<GitCommitInfo> getGitHistory(Workspace workspace, int maxCount) throws Exception {
        ensureGitWorkspace(workspace);
        List<GitCommitInfo> commits = new ArrayList<>();
        try (Git git = Git.open(new File(workspace.getPath()))) {
            Iterable<RevCommit> logs = maxCount > 0 ? git.log().setMaxCount(maxCount).call() : git.log().call();
            for (RevCommit revCommit : logs) {
                GitCommitInfo commitInfo = new GitCommitInfo();
                commitInfo.setCommitId(revCommit.getName());
                commitInfo.setShortCommitId(revCommit.getName().substring(0, 8));
                commitInfo.setMessage(revCommit.getFullMessage());
                PersonIdent author = revCommit.getAuthorIdent();
                commitInfo.setAuthorName(author.getName());
                commitInfo.setAuthorEmail(author.getEmailAddress());
                commitInfo.setCommitTime(revCommit.getCommitTime() * 1000L);
                PersonIdent committer = revCommit.getCommitterIdent();
                commitInfo.setCommitterName(committer.getName());
                commitInfo.setCommitterEmail(committer.getEmailAddress());
                commits.add(commitInfo);
            }
        } catch (NoHeadException e) {
            log.warn("No commits found in workspace: {}", workspace.getName());
        }
        return commits;
    }

    @Override
    public GitOperationResult restoreToCommit(Workspace workspace, String commitId, boolean createBackup) throws Exception {
        ensureGitWorkspace(workspace);
        GitOperationResult result = new GitOperationResult();
        String backupCommitId = null;

        try (Git git = Git.open(new File(workspace.getPath()))) {
            Repository repository = git.getRepository();
            var status = git.status().call();
            boolean hasChanges = !status.getAdded().isEmpty()
                    || !status.getModified().isEmpty()
                    || !status.getRemoved().isEmpty()
                    || !status.getUntracked().isEmpty()
                    || !status.getMissing().isEmpty();

            if (hasChanges) {
                if (createBackup) {
                    git.add().addFilepattern(".").call();
                    String backupMessage = "Backup before restore to " + commitId.substring(0, 8);
                    backupCommitId = git.commit().setMessage(backupMessage).call().getName();
                    result.details += "Created backup commit: " + backupCommitId.substring(0, 8) + "\n\n";
                } else {
                    git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call();
                    result.details += "Discarded uncommitted changes\n\n";
                }
            }

            ObjectId currentHead = repository.resolve("HEAD");
            git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.SOFT).setRef(commitId).call();
            git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.MIXED).setRef(commitId).call();
            git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.SOFT).setRef(currentHead.getName()).call();
            git.checkout().setAllPaths(true).setForced(true).call();

            git.add().addFilepattern(".").setUpdate(true).call();
            git.add().addFilepattern(".").call();

            var statusAfterRestore = git.status().call();
            boolean hasChangesToCommit = !statusAfterRestore.getAdded().isEmpty()
                    || !statusAfterRestore.getModified().isEmpty()
                    || !statusAfterRestore.getRemoved().isEmpty();

            if (hasChangesToCommit) {
                String restoreMessage = "Restore to commit " + commitId.substring(0, 8);
                if (backupCommitId != null) {
                    restoreMessage += "\n\nBackup: " + backupCommitId.substring(0, 8);
                }
                var restoreCommit = git.commit().setMessage(restoreMessage).call();
                result.details += "Created restore commit: " + restoreCommit.getName().substring(0, 8) + "\n";
            } else {
                result.details += "No changes detected, already at target state\n";
            }

            workspace.setLastCommitId(getLastCommitId(git));
            workspace.setUpdatedAt(System.currentTimeMillis());
            result.success = true;
            result.message = "Successfully restored to commit " + commitId.substring(0, 8);
        }

        return result;
    }

    @Override
    public String getCommitDetails(Workspace workspace, String commitId) throws Exception {
        ensureGitWorkspace(workspace);
        StringBuilder details = new StringBuilder();
        try (Git git = Git.open(new File(workspace.getPath()))) {
            Repository repository = git.getRepository();
            try (RevWalk revWalk = new RevWalk(repository)) {
                ObjectId objectId = repository.resolve(commitId);
                RevCommit commit = revWalk.parseCommit(objectId);

                details.append("Commit: ").append(commit.getName()).append("\n");
                details.append("Author: ").append(commit.getAuthorIdent().getName())
                        .append(" <").append(commit.getAuthorIdent().getEmailAddress()).append(">\n");
                details.append("Date: ").append(new java.util.Date(commit.getCommitTime() * 1000L)).append("\n");
                details.append("\n").append(commit.getFullMessage()).append("\n\n");

                if (commit.getParentCount() > 0) {
                    RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                    CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
                    CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
                    try (var reader = repository.newObjectReader()) {
                        oldTreeParser.reset(reader, parent.getTree().getId());
                        newTreeParser.reset(reader, commit.getTree().getId());
                    }
                    List<DiffEntry> diffs = git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).call();
                    if (!diffs.isEmpty()) {
                        details.append("Changed files:\n");
                        for (DiffEntry entry : diffs) {
                            String changeType = switch (entry.getChangeType()) {
                                case ADD -> "A";
                                case MODIFY -> "M";
                                case DELETE -> "D";
                                case RENAME -> "R";
                                case COPY -> "C";
                            };
                            details.append("  ").append(changeType).append(" ").append(entry.getNewPath()).append("\n");
                        }
                    }
                } else {
                    details.append("(Initial commit)\n");
                }
            }
        }
        return details.toString();
    }

    private void ensureGitWorkspace(Workspace workspace) {
        ensureGitWorkspaceType(workspace);
        ensureGitRepositoryMetadata(workspace);
    }

    private void ensureGitWorkspaceType(Workspace workspace) {
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }
    }

    private void ensureGitRepositoryMetadata(Workspace workspace) {
        Path workspacePath = Paths.get(workspace.getPath()).toAbsolutePath().normalize();
        if (!Files.exists(workspacePath.resolve(".git"))) {
            throw new IllegalStateException(I18nUtil.getMessage(
                    MessageKeys.GIT_WORKSPACE_REPOSITORY_MISSING,
                    workspacePath
            ));
        }
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider(Workspace workspace) {
        if (workspace.getGitAuthType() == GitAuthType.PASSWORD
                && workspace.getGitUsername() != null
                && workspace.getGitPassword() != null) {
            return new UsernamePasswordCredentialsProvider(workspace.getGitUsername(), workspace.getGitPassword());
        }
        if (workspace.getGitAuthType() == GitAuthType.TOKEN
                && workspace.getGitUsername() != null
                && workspace.getGitToken() != null) {
            return new UsernamePasswordCredentialsProvider(workspace.getGitUsername(), workspace.getGitToken());
        }
        return null;
    }

    private SshCredentialsProvider getSshCredentialsProvider(Workspace workspace) {
        if (workspace.getGitAuthType() == GitAuthType.SSH_KEY && workspace.getSshPrivateKeyPath() != null) {
            return new SshCredentialsProvider(workspace.getSshPrivateKeyPath(), workspace.getSshPassphrase());
        }
        return null;
    }

    private void cloneRepository(Workspace workspace) throws Exception {
        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(workspace.getGitRemoteUrl())
                .setDirectory(new File(workspace.getPath()));
        cloneCommand.setTimeout(GIT_OPERATION_TIMEOUT);

        if (workspace.getCurrentBranch() != null && !workspace.getCurrentBranch().isEmpty()) {
            cloneCommand.setBranch(workspace.getCurrentBranch());
        }
        applyAuth(cloneCommand, workspace);

        try (Git git = cloneCommand.call()) {
            workspace.setLastCommitId(getLastCommitId(git));
            String actualCurrentBranch = git.getRepository().getBranch();
            workspace.setCurrentBranch(actualCurrentBranch);

            String remoteBranch = git.getRepository().getConfig().getString("branch", actualCurrentBranch, "merge");
            if (remoteBranch != null) {
                if (remoteBranch.startsWith("refs/heads/")) {
                    String branchName = remoteBranch.substring("refs/heads/".length());
                    workspace.setRemoteBranch("origin/" + branchName);
                } else {
                    workspace.setRemoteBranch(remoteBranch);
                }
            } else {
                try {
                    var config = git.getRepository().getConfig();
                    config.setString("branch", actualCurrentBranch, "remote", "origin");
                    config.setString("branch", actualCurrentBranch, "merge", "refs/heads/" + actualCurrentBranch);
                    config.save();
                    workspace.setRemoteBranch("origin/" + actualCurrentBranch);
                } catch (Exception e) {
                    log.warn("Failed to auto-bind remote branch", e);
                    workspace.setRemoteBranch(null);
                }
            }
        }
    }

    private void initializeGitRepository(Workspace workspace) throws Exception {
        try (Git git = Git.init().setDirectory(new File(workspace.getPath())).call()) {
            createInitialCommit(git, workspace);
            String currentBranch = git.getRepository().getBranch();
            if (workspace.getCurrentBranch() != null
                    && !workspace.getCurrentBranch().isEmpty()
                    && !workspace.getCurrentBranch().equals(currentBranch)) {
                git.branchCreate().setName(workspace.getCurrentBranch()).call();
                git.checkout().setName(workspace.getCurrentBranch()).call();
                workspace.setCurrentBranch(workspace.getCurrentBranch());
            } else {
                workspace.setCurrentBranch(currentBranch);
            }
            workspace.setRemoteBranch(null);
        }
    }

    private void createInitialCommit(Git git, Workspace workspace) throws Exception {
        createGitignore(workspace);
        Path readmePath = Paths.get(workspace.getPath(), "README.md");
        Files.write(readmePath, String.format("# %s\n\n%s",
                workspace.getName(),
                workspace.getDescription() != null ? workspace.getDescription() : "EasyPostman Workspace").getBytes());
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();
        workspace.setLastCommitId(getLastCommitId(git));
    }

    private void createGitignore(Workspace workspace) throws IOException {
        Path gitignorePath = Paths.get(workspace.getPath(), ".gitignore");
        List<String> ignorePatterns = new ArrayList<>();
        ignorePatterns.add("# EasyPostman Workspace");
        ignorePatterns.add("");
        ignorePatterns.add("# Temporary files");
        ignorePatterns.add("*.tmp");
        ignorePatterns.add("*.bak");
        ignorePatterns.add("*~");
        ignorePatterns.add("");
        ignorePatterns.add("# OS generated files");
        ignorePatterns.add(".DS_Store");
        ignorePatterns.add("Thumbs.db");
        Files.write(gitignorePath, ignorePatterns, StandardCharsets.UTF_8);
    }

    private TrackingRef resolveTrackingRef(Git git) throws IOException {
        String localBranch = git.getRepository().getBranch();
        String tracking = git.getRepository().getConfig().getString("branch", localBranch, "merge");
        if (tracking == null || tracking.isBlank()) {
            throw new IllegalStateException("Current branch has no upstream branch set: " + localBranch);
        }
        String remoteName = git.getRepository().getConfig().getString("branch", localBranch, "remote");
        if (remoteName == null || remoteName.isBlank()) {
            remoteName = "origin";
        }
        String remoteBranchName = tracking.startsWith("refs/heads/")
                ? tracking.substring("refs/heads/".length())
                : tracking;
        if (remoteBranchName.isBlank()) {
            throw new IllegalStateException("Current branch has invalid upstream branch: " + tracking);
        }
        return new TrackingRef(
                localBranch,
                remoteName,
                remoteBranchName,
                "refs/heads/" + remoteBranchName,
                "refs/remotes/" + remoteName + "/" + remoteBranchName
        );
    }

    private record TrackingRef(String localBranch,
                               String remoteName,
                               String remoteBranchName,
                               String remoteBranchRef,
                               String remoteTrackingRef) {
    }

    private GitBranchInfo toBranchInfo(Git git,
                                       Ref ref,
                                       String currentBranch,
                                       Map<String, String> trackingByLocalBranch,
                                       Set<String> trackedRemoteBranches) throws Exception {
        String fullName = ref.getName();
        boolean remote = fullName.startsWith("refs/remotes/");
        String name = Repository.shortenRefName(fullName);
        if (remote && name.endsWith("/HEAD")) {
            return null;
        }
        if (remote && trackedRemoteBranches.contains(name)) {
            return null;
        }
        String remoteName = null;
        if (remote) {
            int separatorIndex = name.indexOf('/');
            remoteName = separatorIndex > 0 ? name.substring(0, separatorIndex) : null;
        }
        boolean current = !remote && name.equals(currentBranch);
        String trackingBranch = trackingByLocalBranch.get(name);
        GitBranchInfo branchInfo = new GitBranchInfo(name, fullName, current, remote, remoteName, trackingBranch);
        populateSyncCounts(git, branchInfo);
        return branchInfo;
    }

    private void populateSyncCounts(Git git, GitBranchInfo branchInfo) throws Exception {
        if (branchInfo.isRemote() || branchInfo.getTrackingBranch() == null || branchInfo.getTrackingBranch().isBlank()) {
            return;
        }
        ObjectId localId = git.getRepository().resolve("refs/heads/" + branchInfo.getName());
        ObjectId remoteId = git.getRepository().resolve("refs/remotes/" + branchInfo.getTrackingBranch());
        if (localId == null || remoteId == null) {
            return;
        }
        branchInfo.setAheadCount(countCommitsBetween(git, remoteId, localId));
        branchInfo.setBehindCount(countCommitsBetween(git, localId, remoteId));
    }

    private int countCommitsBetween(Git git, ObjectId startExclusive, ObjectId endInclusive) throws Exception {
        int count = 0;
        for (RevCommit ignored : git.log().addRange(startExclusive, endInclusive).call()) {
            count++;
        }
        return count;
    }

    private Map<String, String> localBranchTrackingMap(Git git, List<Ref> refs) {
        Map<String, String> trackingByLocalBranch = new HashMap<>();
        for (Ref ref : refs) {
            String fullName = ref.getName();
            if (!fullName.startsWith("refs/heads/")) {
                continue;
            }
            String localBranch = Repository.shortenRefName(fullName);
            String trackingBranch = currentTrackingBranchName(git, localBranch);
            if (trackingBranch != null && !trackingBranch.isBlank()) {
                trackingByLocalBranch.put(localBranch, trackingBranch);
            }
        }
        return trackingByLocalBranch;
    }

    private String currentTrackingBranchName(Git git, String currentBranch) {
        String tracking = git.getRepository().getConfig().getString("branch", currentBranch, "merge");
        if (tracking == null || tracking.isBlank()) {
            return null;
        }
        String remoteName = git.getRepository().getConfig().getString("branch", currentBranch, "remote");
        if (remoteName == null || remoteName.isBlank() || ".".equals(remoteName)) {
            return null;
        }
        String remoteBranchName = tracking.startsWith("refs/heads/")
                ? tracking.substring("refs/heads/".length())
                : tracking;
        if (remoteBranchName.isBlank()) {
            return null;
        }
        return remoteName + "/" + remoteBranchName;
    }

    private String normalizeRequestedBranchName(String branchName) {
        if (branchName == null || branchName.trim().isEmpty()) {
            throw new IllegalArgumentException("Branch name is required");
        }
        String normalized = branchName.trim();
        if (normalized.startsWith("refs/heads/")) {
            return normalized.substring("refs/heads/".length());
        }
        if (normalized.startsWith("refs/remotes/")) {
            return normalized.substring("refs/remotes/".length());
        }
        return normalized;
    }

    private String validateLocalBranchName(String branchName) {
        String localBranch = normalizeRequestedBranchName(branchName);
        if (!Repository.isValidRefName("refs/heads/" + localBranch)) {
            throw new IllegalArgumentException("Invalid branch name: " + branchName);
        }
        return localBranch;
    }

    private String localBranchNameForCheckout(Git git, String requestedBranch) throws IOException {
        if (git.getRepository().resolve("refs/heads/" + requestedBranch) != null) {
            return requestedBranch;
        }
        int remoteSeparator = requestedBranch.indexOf('/');
        if (remoteSeparator > 0) {
            return requestedBranch.substring(remoteSeparator + 1);
        }
        return requestedBranch;
    }

    private String resolveRemoteBranchForCheckout(Git git, String requestedBranch) throws Exception {
        if (git.getRepository().resolve("refs/remotes/" + requestedBranch) != null) {
            return requestedBranch;
        }
        if (requestedBranch.contains("/")) {
            return null;
        }

        String matchedRemoteBranch = null;
        for (Ref ref : git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()) {
            String remoteBranch = Repository.shortenRefName(ref.getName());
            if (remoteBranch.endsWith("/HEAD")) {
                continue;
            }
            int separatorIndex = remoteBranch.indexOf('/');
            if (separatorIndex > 0 && remoteBranch.substring(separatorIndex + 1).equals(requestedBranch)) {
                if (matchedRemoteBranch != null) {
                    throw new IllegalArgumentException("Branch name is ambiguous across remotes: " + requestedBranch);
                }
                matchedRemoteBranch = remoteBranch;
            }
        }
        return matchedRemoteBranch;
    }

    private void updateWorkspaceBranchMetadata(Git git, Workspace workspace) throws Exception {
        String currentBranch = git.getRepository().getBranch();
        workspace.setCurrentBranch(currentBranch);
        String tracking = git.getRepository().getConfig().getString("branch", currentBranch, "merge");
        String remoteName = git.getRepository().getConfig().getString("branch", currentBranch, "remote");
        if (tracking != null && !tracking.isBlank()) {
            if (remoteName == null || remoteName.isBlank()) {
                remoteName = "origin";
            }
            String remoteBranchName = tracking.startsWith("refs/heads/")
                    ? tracking.substring("refs/heads/".length())
                    : tracking;
            workspace.setRemoteBranch(remoteName + "/" + remoteBranchName);
        } else {
            workspace.setRemoteBranch(null);
        }
        workspace.setLastCommitId(getLastCommitId(git));
        workspace.setUpdatedAt(System.currentTimeMillis());
    }

    private String getLastCommitId(Git git) {
        try {
            var iterator = git.log().setMaxCount(1).call().iterator();
            return iterator.hasNext() ? iterator.next().getName() : null;
        } catch (NoHeadException e) {
            return null;
        } catch (Exception e) {
            log.error("Failed to get the last commit id from git log", e);
            return null;
        }
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objectId) throws Exception {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objectId);
            ObjectId treeId = commit.getTree().getId();
            CanonicalTreeParser treeWalk = new CanonicalTreeParser();
            try (var reader = repository.newObjectReader()) {
                treeWalk.reset(reader, treeId);
            }
            return treeWalk;
        }
    }

    private void applyAuth(Object command, Workspace workspace) {
        UsernamePasswordCredentialsProvider credentialsProvider = getCredentialsProvider(workspace);
        SshCredentialsProvider sshCredentialsProvider = getSshCredentialsProvider(workspace);
        if (command instanceof org.eclipse.jgit.api.TransportCommand<?, ?> transportCommand) {
            if (credentialsProvider != null) {
                transportCommand.setCredentialsProvider(credentialsProvider);
            } else if (sshCredentialsProvider != null) {
                transportCommand.setTransportConfigCallback(sshCredentialsProvider);
            }
        }
    }
}
