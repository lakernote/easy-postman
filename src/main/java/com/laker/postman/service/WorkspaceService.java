package com.laker.postman.service;

import com.laker.postman.model.*;
import com.laker.postman.service.git.SshCredentialsProvider;
import com.laker.postman.util.WorkspaceStorageUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 工作区服务类
 * 负责工作区的创建、管理、Git操作等核心功能
 */
@Slf4j
public class WorkspaceService {

    private static final String WORKSPACE_NOT_FOUND_MSG = "Workspace not found: ";

    // Git 操作超时时间（秒）
    private static final int GIT_OPERATION_TIMEOUT = 10; // 10 秒超时

    private static WorkspaceService instance;
    private List<Workspace> workspaces = new ArrayList<>();
    @Getter
    private Workspace currentWorkspace;

    private WorkspaceService() {
        loadWorkspaces();
    }

    public static synchronized WorkspaceService getInstance() {
        if (instance == null) {
            instance = new WorkspaceService();
        }
        return instance;
    }

    /**
     * 获取所有工作区
     */
    public List<Workspace> getAllWorkspaces() {
        return new ArrayList<>(workspaces);
    }

    /**
     * 创建新工作区
     */
    public void createWorkspace(Workspace workspace) throws Exception {
        if (WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            throw new IllegalArgumentException("Cannot create the default workspace");
        }
        validateWorkspace(workspace);

        // 生成唯一ID
        workspace.setId(UUID.randomUUID().toString());
        workspace.setCreatedAt(System.currentTimeMillis());
        workspace.setUpdatedAt(System.currentTimeMillis());

        // 对于非Git克隆模式，创建本地目录
        if (workspace.getType() != WorkspaceType.GIT || workspace.getGitRepoSource() != GitRepoSource.CLONED) {
            Path workspacePath = workspace.getPath();
            if (!Files.exists(workspacePath)) {
                Files.createDirectories(workspacePath);
            }
        }

        // 根据工作区类型执行相应操作
        if (workspace.getType() == WorkspaceType.GIT) {
            handleGitWorkspace(workspace);
        }

        // 添加到工作区列表并保存
        workspaces.add(workspace);
        saveWorkspaces();

        log.info("Created workspace: {} ({})", workspace.getName(), workspace.getType());
    }

    /**
     * 处理Git工作区创建
     */
    private void handleGitWorkspace(Workspace workspace) throws Exception {
        if (workspace.getGitRepoSource() == GitRepoSource.CLONED) {
            cloneRepository(workspace);
        } else if (workspace.getGitRepoSource() == GitRepoSource.INITIALIZED) {
            initializeGitRepository(workspace);
        }
    }

    /**
     * Helper: Get CredentialsProvider for Git operations
     */
    public UsernamePasswordCredentialsProvider getCredentialsProvider(Workspace workspace) {
        if (workspace.getGitAuthType() == GitAuthType.PASSWORD &&
                workspace.getGitUsername() != null && workspace.getGitPassword() != null) {
            return new UsernamePasswordCredentialsProvider(workspace.getGitUsername(), workspace.getGitPassword());
        } else if (workspace.getGitAuthType() == GitAuthType.TOKEN &&
                workspace.getGitToken() != null && workspace.getGitUsername() != null) {
            return new UsernamePasswordCredentialsProvider(workspace.getGitUsername(), workspace.getGitToken());
        }
        return null;
    }

    /**
     * Helper: Get SSH TransportConfigCallback for SSH authentication
     */
    public SshCredentialsProvider getSshCredentialsProvider(Workspace workspace) {
        if (workspace.getGitAuthType() == GitAuthType.SSH_KEY &&
                workspace.getSshPrivateKeyPath() != null) {
            return new SshCredentialsProvider(
                    workspace.getSshPrivateKeyPath(),
                    workspace.getSshPassphrase()
            );
        }
        return null;
    }

    /**
     * 克隆远程仓库
     */
    private void cloneRepository(Workspace workspace) throws Exception {
        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(workspace.getGitRemoteUrl())
                .setDirectory(workspace.getPath().toFile());
        cloneCommand.setTimeout(GIT_OPERATION_TIMEOUT); // 设置超时时间

        // 如果用户指定了特定的分支，设置分支名称
        if (workspace.getCurrentBranch() != null && !workspace.getCurrentBranch().isEmpty()) {
            cloneCommand.setBranch(workspace.getCurrentBranch());
        }

        // 设置认证信息
        UsernamePasswordCredentialsProvider credentialsProvider = getCredentialsProvider(workspace);
        if (credentialsProvider != null) {
            cloneCommand.setCredentialsProvider(credentialsProvider);
        } else {
            // 尝试使用 SSH 认证
            SshCredentialsProvider sshCredentialsProvider = getSshCredentialsProvider(workspace);
            if (sshCredentialsProvider != null) {
                cloneCommand.setTransportConfigCallback(sshCredentialsProvider);
            }
        }

        try (Git git = cloneCommand.call()) {
            workspace.setLastCommitId(getLastCommitId(git));

            // 获取实际的当前分支名称
            String actualCurrentBranch = git.getRepository().getBranch();
            workspace.setCurrentBranch(actualCurrentBranch);

            // 自动检测远程分支，统一转换为 origin/分支名 格式
            String remoteBranch = git.getRepository().getConfig().getString("branch", actualCurrentBranch, "merge");
            if (remoteBranch != null) {
                // 将 refs/heads/分支名 转换为 origin/分支名 格式
                if (remoteBranch.startsWith("refs/heads/")) {
                    String branchName = remoteBranch.substring("refs/heads/".length());
                    workspace.setRemoteBranch("origin/" + branchName);
                } else {
                    workspace.setRemoteBranch(remoteBranch);
                }
            } else {
                // remoteBranch 为空，说明远程仓库可能为空，自动为本地分支绑定远程分支关系
                try {
                    var config = git.getRepository().getConfig();
                    config.setString("branch", actualCurrentBranch, "remote", "origin");
                    config.setString("branch", actualCurrentBranch, "merge", "refs/heads/" + actualCurrentBranch);
                    config.save();
                    workspace.setRemoteBranch("origin/" + actualCurrentBranch);
                    log.info("远程仓库为空，已自动为本地分支 '{}' 绑定上游: origin/{}", actualCurrentBranch, actualCurrentBranch);
                } catch (Exception e) {
                    log.warn("自动绑定远程分支失败: {}", e.getMessage(), e);
                    workspace.setRemoteBranch(null);
                }
            }

            log.info("Successfully cloned repository: {} on branch: {}", workspace.getGitRemoteUrl(), actualCurrentBranch);
        }
    }

    /**
     * 初始化本地Git仓库
     */
    private void initializeGitRepository(Workspace workspace) throws Exception {
        try (Git git = Git.init().setDirectory(workspace.getPath().toFile()).call()) {
            // 创建初始提交
            createInitialCommit(git, workspace);

            // 获取当前分支名称
            String currentBranch = git.getRepository().getBranch();

            // 如果用户指定了不同的分支名称，则创建并切换到该分支
            if (workspace.getCurrentBranch() != null &&
                    !workspace.getCurrentBranch().isEmpty() &&
                    !workspace.getCurrentBranch().equals(currentBranch)) {

                // 创建并切换到用户指定的分支
                git.branchCreate()
                        .setName(workspace.getCurrentBranch())
                        .call();
                git.checkout()
                        .setName(workspace.getCurrentBranch())
                        .call();

                workspace.setCurrentBranch(workspace.getCurrentBranch());
            } else {
                // 使用默认分支名称
                workspace.setCurrentBranch(currentBranch);
            }

            // INITIALIZED模式下不设置远程分支
            workspace.setRemoteBranch(null);

            log.info("Initialized git repository at: {} on branch: {}", workspace.getPath(), workspace.getCurrentBranch());
        }
    }

    /**
     * 创建初始提交
     */
    private void createInitialCommit(Git git, Workspace workspace) throws Exception {
        // 创建README文件
        Path readmePath = workspace.getPath().resolve("README.md");
        Files.write(readmePath, String.format("# %s\n\n%s",
                workspace.getName(),
                workspace.getDescription() != null ? workspace.getDescription() : "EasyPostman Workspace").getBytes());

        // 添加并提交
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial commit").call();

        workspace.setLastCommitId(getLastCommitId(git));
    }

    /**
     * 获取最后一次提交ID
     */
    private String getLastCommitId(Git git) {
        try {
            var logCommand = git.log().setMaxCount(1);
            var commits = logCommand.call();
            var iterator = commits.iterator();
            if (iterator.hasNext()) {
                return iterator.next().getName();
            } else {
                // 仓库为空，没有提交
                return null;
            }
        } catch (NoHeadException e) {
            // 仓库为空，没有 HEAD
            log.warn("Repository has no HEAD (empty repository): {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to get the last commit id from git log", e);
            return null;
        }
    }


    /**
     * 验证工作区参数
     */
    private void validateWorkspace(Workspace workspace) throws IllegalArgumentException {
        if (workspace.getName() == null || workspace.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace name is required");
        }

        if (workspace.getPath() == null) {
            throw new IllegalArgumentException("Workspace path is required");
        }

        if (workspace.getType() == WorkspaceType.GIT) {
            if (workspace.getGitRepoSource() == GitRepoSource.CLONED) {
                if (workspace.getGitRemoteUrl() == null || workspace.getGitRemoteUrl().trim().isEmpty()) {
                    throw new IllegalArgumentException("Git repository URL is required for cloning");
                }

                if (workspace.getGitAuthType() == GitAuthType.PASSWORD) {
                    if (workspace.getGitUsername() == null || workspace.getGitPassword() == null) {
                        throw new IllegalArgumentException("Username and password are required");
                    }
                } else if (workspace.getGitAuthType() == GitAuthType.TOKEN) {
                    if (workspace.getGitUsername() == null || workspace.getGitToken() == null) {
                        throw new IllegalArgumentException("Username and access token are required");
                    }
                }
            }
        }

        // 检查路径是否已被其他工作区使用
        Path normalizedPath = workspace.getPath().toAbsolutePath().normalize();
        boolean pathExists = workspaces.stream()
                .anyMatch(w -> w.getPath().toAbsolutePath().normalize().equals(normalizedPath));

        if (pathExists) {
            throw new IllegalArgumentException("Path is already used by another workspace");
        }

        // 对于Git克隆模式，检查目标目录是否已存在且不为空
        if (workspace.getType() == WorkspaceType.GIT && workspace.getGitRepoSource() == GitRepoSource.CLONED) {
            Path targetPath = workspace.getPath();
            if (Files.exists(targetPath)) {
                try {
                    // 检查目录是否为空
                    boolean isEmpty = Files.list(targetPath).findAny().isEmpty();
                    if (!isEmpty) {
                        throw new IllegalArgumentException("Target directory is not empty: " + workspace.getPath());
                    }
                    // 如果目录存在但为空，删除它让Git克隆重新创建
                    Files.delete(targetPath);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Cannot access target directory: " + workspace.getPath());
                }
            }
        }
    }

    /**
     * 切换工作区
     */
    public void switchWorkspace(String workspaceId) {
        Workspace workspace = workspaces.stream()
                .filter(w -> w.getId().equals(workspaceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(WORKSPACE_NOT_FOUND_MSG + workspaceId));

        currentWorkspace = workspace;
        WorkspaceStorageUtil.saveCurrentWorkspace(workspaceId);
        log.info("Switched to workspace: {}", workspace.getName());
    }

    /**
     * 删除工作区
     */
    public void deleteWorkspace(String workspaceId) throws Exception {
        Workspace workspace = workspaces.stream()
                .filter(w -> w.getId().equals(workspaceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(WORKSPACE_NOT_FOUND_MSG + workspaceId));
        if (WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            throw new IllegalArgumentException("Default workspace cannot be deleted");
        }
        // 删除工作区文件
        Path workspacePath = workspace.getPath();
        if (Files.exists(workspacePath)) {
            deleteDirectoryRecursively(workspacePath);
        }

        workspaces.removeIf(w -> w.getId().equals(workspaceId));

        // 如果删除的是当前工作区，切换到默认工作区
        if (currentWorkspace != null && currentWorkspace.getId().equals(workspaceId)) {
            // 优先切换到默认工作区
            currentWorkspace = getDefaultWorkspace();
            WorkspaceStorageUtil.saveCurrentWorkspace(currentWorkspace != null ? currentWorkspace.getId() : null);
        }

        saveWorkspaces();
        log.info("Deleted workspace: {}", workspace.getName());
    }

    public Workspace getDefaultWorkspace() {
        return workspaces.stream()
                .filter(WorkspaceStorageUtil::isDefaultWorkspace)
                .findFirst()
                .orElse(workspaces.isEmpty() ? null : workspaces.get(0));
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var stream = Files.walk(directory)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                log.warn("Failed to delete file: {}", file.getAbsolutePath());
                            }
                        });
            }
        }
    }

    /**
     * 重命名工作区
     */
    public void renameWorkspace(String workspaceId, String newName) {
        Workspace workspace = workspaces.stream()
                .filter(w -> w.getId().equals(workspaceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(WORKSPACE_NOT_FOUND_MSG + workspaceId));
        if (WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            throw new IllegalArgumentException("Default workspace cannot be renamed");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace name cannot be empty");
        }

        workspace.setName(newName.trim());
        workspace.setUpdatedAt(System.currentTimeMillis());
        saveWorkspaces();

        log.info("Renamed workspace to: {}", newName);
    }

    /**
     * Git操作结果封装
     */
    public static class GitOperationResult {
        public boolean success = false;
        public String message = "";
        public List<String> affectedFiles = new ArrayList<>();
        public String operationType = "";
        public String details = "";
    }

    /**
     * Git操作：拉取更新
     */
    public GitOperationResult pullUpdates(String workspaceId) throws Exception {
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Pull";

        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }

        try (Git git = Git.open(workspace.getPath().toFile())) {
            String branch = git.getRepository().getBranch();
            String tracking = git.getRepository().getConfig().getString("branch", branch, "merge");
            log.info("Current branch: {}, tracking: {}", branch, tracking);

            if (tracking == null) {
                throw new IllegalStateException("Local branch is not tracking a remote branch, unable to pull. Please set up tracking branch first, e.g.: git branch --set-upstream-to=origin/" + branch);
            }

            // 记录拉取前的状态
            var statusBefore = git.status().call();
            String commitIdBefore = getLastCommitId(git);

            // 检查是否是空仓库的情况
            if (commitIdBefore == null) {
                // 本地仓库为空，尝试从远程拉取
                log.info("Local repository is empty, attempting to pull initial content from remote");
                result.details += "Detected empty local repository, attempting to pull initial content from remote\n";

                try {
                    // 先尝试 fetch 来检查远程是否有内容
                    var fetchCommand = git.fetch();
                    fetchCommand.setTimeout(GIT_OPERATION_TIMEOUT); // 设置超时时间
                    UsernamePasswordCredentialsProvider credentialsProvider = getCredentialsProvider(workspace);
                    SshCredentialsProvider sshCredentialsProvider = getSshCredentialsProvider(workspace);

                    if (credentialsProvider != null) {
                        fetchCommand.setCredentialsProvider(credentialsProvider);
                    } else if (sshCredentialsProvider != null) {
                        fetchCommand.setTransportConfigCallback(sshCredentialsProvider);
                    }
                    fetchCommand.call();

                    // 检查远程分支是否存在
                    String remoteName = git.getRepository().getConfig().getString("branch", branch, "remote");
                    if (remoteName == null) {
                        remoteName = "origin";
                    }

                    String remoteBranchName = tracking;
                    if (remoteBranchName.startsWith("refs/heads/")) {
                        remoteBranchName = remoteBranchName.substring("refs/heads/".length());
                    }

                    String remoteRef = "refs/remotes/" + remoteName + "/" + remoteBranchName;
                    ObjectId remoteId = git.getRepository().resolve(remoteRef);

                    if (remoteId == null) {
                        // 远程分支不存在，说明远程仓库也是空的
                        result.success = true;
                        result.message = "Remote repository is empty, nothing to pull";
                        result.details += "Remote repository is currently empty, waiting for initial push\n";
                        log.info("Remote repository is empty, nothing to pull");
                        return result;
                    }

                } catch (RefNotAdvertisedException e) {
                    // 远程分支不存在的错误
                    result.success = true;
                    result.message = "Remote repository is empty or remote branch does not exist, nothing to pull";
                    result.details += "Remote repository is empty or branch '" + tracking + "' does not exist\n";
                    result.details += "This usually happens when the remote repository was just created and no content has been pushed yet\n";
                    log.info("Remote branch does not exist: {}", e.getMessage());
                    return result;
                } catch (Exception e) {
                    log.warn("Failed to fetch from remote repository", e);
                    // 如果 fetch 失败，可能是网络问题或认证问题
                    throw new RuntimeException("Unable to connect to remote repository: " + e.getMessage(), e);
                }
            }

            if (!statusBefore.isClean()) {
                log.warn("Local has uncommitted content or conflicts, automatically executing git reset --hard and git clean -fd");
                result.details += "Detected local uncommitted content, automatically cleaning:\n";

                // 记录被清理的文件
                if (!statusBefore.getModified().isEmpty()) {
                    result.details += "  Reset modified files: " + String.join(", ", statusBefore.getModified()) + "\n";
                }
                if (!statusBefore.getUntracked().isEmpty()) {
                    result.details += "  Clean untracked files: " + String.join(", ", statusBefore.getUntracked()) + "\n";
                }

                // 强制丢弃本地所有未提交内容和未跟踪文件
                git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call();
                git.clean().setCleanDirectories(true).setForce(true).call();

                // 再次检查
                var statusAfterClean = git.status().call();
                if (!statusAfterClean.isClean()) {
                    throw new IllegalStateException("Still have uncommitted content or conflicts after automatic cleanup, please handle manually.");
                }
            }

            try {
                var pullCommand = git.pull();
                pullCommand.setTimeout(GIT_OPERATION_TIMEOUT); // 设置超时时间
                // 设置自动merge策略，优先使用远程版本解决冲突
                pullCommand.setStrategy(MergeStrategy.RECURSIVE);

                // 设置认证信息 - 支持SSH
                UsernamePasswordCredentialsProvider credentialsProvider = getCredentialsProvider(workspace);
                SshCredentialsProvider sshCredentialsProvider = getSshCredentialsProvider(workspace);

                if (credentialsProvider != null) {
                    pullCommand.setCredentialsProvider(credentialsProvider);
                } else if (sshCredentialsProvider != null) {
                    pullCommand.setTransportConfigCallback(sshCredentialsProvider);
                }

                var pullResult = pullCommand.call();
                log.info("Pull result: {}", pullResult.isSuccessful());

                if (!pullResult.isSuccessful()) {
                    throw new RuntimeException("Git pull failed: " + pullResult);
                }

                String commitIdAfter = getLastCommitId(git);

                // 检查是否有新的提交
                if (commitIdBefore == null || !commitIdBefore.equals(commitIdAfter)) {
                    // 获取变更的文件列表
                    try {
                        if (commitIdBefore != null && commitIdAfter != null) {
                            List<String> changedFiles = getChangedFilesBetweenCommits(workspaceId, commitIdBefore, commitIdAfter);
                            result.affectedFiles.addAll(changedFiles);
                            result.details += "Pulled new commits, affected files:\n";
                            for (String file : changedFiles) {
                                result.details += "  " + file + "\n";
                            }
                        } else if (commitIdAfter != null) {
                            // 从空仓库拉取到了内容
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
                saveWorkspaces();
                result.success = true;

                log.info("Pulled updates for workspace: {}", workspace.getName());

            } catch (RefNotAdvertisedException e) {
                // 处理远程分支不存在的情况
                result.success = true;
                result.message = "Remote branch does not exist, nothing to pull";
                result.details += "Remote branch '" + tracking + "' does not exist or is empty\n";
                result.details += "This usually happens when the remote repository was just created and no content has been pushed yet\n";
                log.info("Remote branch does not exist during pull: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * Git操作：推送变更
     */
    public GitOperationResult pushChanges(String workspaceId) throws Exception {
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Push";

        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }

        try (Git git = Git.open(workspace.getPath().toFile())) {
            // 检查是否有远程仓库
            var remotes = git.remoteList().call();
            if (remotes.isEmpty()) {
                throw new IllegalStateException("No remote repository configured, unable to push");
            }

            // 检查当前分支是否有上游分支
            String currentBranch = git.getRepository().getBranch();
            String tracking = git.getRepository().getConfig().getString("branch", currentBranch, "merge");
            if (tracking == null) {
                throw new IllegalStateException("Current branch has no upstream branch set, please perform initial push or set upstream branch first");
            }

            // 获取远程分支名称 (去掉 refs/heads/ 前缀)
            String remoteBranchName = tracking;
            if (remoteBranchName.startsWith("refs/heads/")) {
                remoteBranchName = remoteBranchName.substring("refs/heads/".length());
            }

            // 获取远程仓库名称
            String remoteName = git.getRepository().getConfig().getString("branch", currentBranch, "remote");
            if (remoteName == null) {
                remoteName = "origin"; // 默认使用 origin
            }

            // 获取认证信息
            UsernamePasswordCredentialsProvider credentialsProvider = getCredentialsProvider(workspace);

            // 正确计算未推送的提交
            int unpushedCommitsCount = 0;

            try {
                // 先 fetch 最新的远程信息
                var fetchCommand = git.fetch();
                fetchCommand.setTimeout(GIT_OPERATION_TIMEOUT); // 设置超时时间

                SshCredentialsProvider sshCredentialsProvider = getSshCredentialsProvider(workspace);

                if (credentialsProvider != null) {
                    fetchCommand.setCredentialsProvider(credentialsProvider);
                } else if (sshCredentialsProvider != null) {
                    fetchCommand.setTransportConfigCallback(sshCredentialsProvider);
                }
                fetchCommand.call();

                // 比较本地分支和远程分支的差异
                String localRef = "refs/heads/" + currentBranch;
                String remoteRef = "refs/remotes/" + remoteName + "/" + remoteBranchName;

                ObjectId localId = git.getRepository().resolve(localRef);
                ObjectId remoteId = git.getRepository().resolve(remoteRef);

                if (localId == null) {
                    throw new IllegalStateException("Unable to find local branch: " + currentBranch);
                }

                if (remoteId != null) {
                    // 获取本地领先于远程的提交
                    Iterable<RevCommit> commits = git.log()
                            .addRange(remoteId, localId)
                            .call();

                    for (RevCommit commit : commits) {
                        unpushedCommitsCount++;

                        // 获取提交涉及的文件
                        try {
                            if (commit.getParentCount() > 0) {
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
                            }
                        } catch (Exception e) {
                            log.warn("Failed to get commit files for {}", commit.getName(), e);
                        }
                    }
                } else {
                    // 远程分支不存在，说明是首次推送
                    log.info("Remote branch does not exist, will push all local commits");
                    Iterable<RevCommit> commits = git.log().call();
                    for (RevCommit commit : commits) {
                        unpushedCommitsCount++;

                        // 获取提交涉及的文件 (简化处理，只获取前几个提交的文件)
                        if (unpushedCommitsCount <= 10) {
                            try {
                                if (commit.getParentCount() > 0) {
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
                                }
                            } catch (Exception e) {
                                log.warn("Failed to get commit files for {}", commit.getName(), e);
                            }
                        }
                    }
                }

                if (unpushedCommitsCount == 0) {
                    result.success = true;
                    result.message = "No commits to push, local branch is up to date";
                    result.details = "Local branch " + currentBranch + " is in sync with remote branch " + remoteName + "/" + remoteBranchName + "\n";
                    return result;
                }
            } catch (Exception e) {
                log.warn("Failed to accurately count unpushed commits, proceeding with push", e);
                // 如果无法准确计算，继续执行推送
            }

            // 执行推送 - 明确指定推送的分支
            var pushCommand = git.push()
                    .setRemote(remoteName)
                    .add(currentBranch + ":" + remoteBranchName); // 明确指定本地分支推送到远程分支
            pushCommand.setTimeout(GIT_OPERATION_TIMEOUT); // 设置超时时间
            SshCredentialsProvider sshCredentialsProvider = getSshCredentialsProvider(workspace);
            // 设置认证信息 - 支持SSH
            if (credentialsProvider != null) {
                pushCommand.setCredentialsProvider(credentialsProvider);
            } else if (sshCredentialsProvider != null) {
                pushCommand.setTransportConfigCallback(sshCredentialsProvider);
            }

            var pushResults = pushCommand.call();

            result.details += "Push details:\n";
            result.details += "  Local branch: " + currentBranch + "\n";
            result.details += "  Remote branch: " + remoteName + "/" + remoteBranchName + "\n";
            result.details += "  Pushed commits: " + unpushedCommitsCount + "\n";

            if (!result.affectedFiles.isEmpty()) {
                result.details += "  Affected files (" + result.affectedFiles.size() + "):\n";
                for (String file : result.affectedFiles) {
                    result.details += "    " + file + "\n";
                }
            }

            // 详细检查推送结果
            boolean pushSuccess = false;
            for (var pushResult : pushResults) {
                // 只在有错误或警告时输出推送消息
                String msg = pushResult.getMessages();
                if (msg != null && !msg.isEmpty() && (msg.toLowerCase().contains("error") || msg.toLowerCase().contains("fail"))) {
                    result.details += "  Push message: " + msg + "\n";
                }
                // 输出每个 ref 的推送状态
                for (var remoteRefUpdate : pushResult.getRemoteUpdates()) {
                    result.details += "  Branch update: " + remoteRefUpdate.getSrcRef() + " -> " +
                            remoteRefUpdate.getRemoteName() + " (" + remoteRefUpdate.getStatus() + ")\n";
                    if (remoteRefUpdate.getMessage() != null && !remoteRefUpdate.getMessage().isEmpty()) {
                        result.details += "    Details: " + remoteRefUpdate.getMessage() + "\n";
                    }
                    if (remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK ||
                            remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                        pushSuccess = true;
                    } else {
                        result.details += "    Error: " + remoteRefUpdate.getMessage() + "\n";
                    }
                }
            }

            if (!pushSuccess) {
                throw new RuntimeException("Push failed, please check error details in push result");
            }

            result.success = true;
            result.message = "Successfully pushed " + unpushedCommitsCount + " commits, " + result.affectedFiles.size() + " files affected";

            log.info("Successfully pushed changes for workspace: {} to {}/{}",
                    workspace.getName(), remoteName, remoteBranchName);
        }

        return result;
    }

    /**
     * 强制推送变更（覆盖远程变更）
     */
    public GitOperationResult forcePushChanges(String workspaceId) throws Exception {
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Force Push";

        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }

        try (Git git = Git.open(workspace.getPath().toFile())) {
            String currentBranch = git.getRepository().getBranch();

            // 获取认证信息
            UsernamePasswordCredentialsProvider credentialsProvider = getCredentialsProvider(workspace);

            // 执行强制推送
            var pushCommand = git.push()
                    .setForce(true); // 设置强制推送
            pushCommand.setTimeout(GIT_OPERATION_TIMEOUT); // 设置超时时间

            if (credentialsProvider != null) {
                pushCommand.setCredentialsProvider(credentialsProvider);
            }

            var pushResults = pushCommand.call();

            result.details += "Force push details:\n";
            result.details += "  Local branch: " + currentBranch + "\n";
            result.details += "  ❗ Warning: Force push has overwritten remote changes\n";

            // 检查推送结果
            boolean pushSuccess = false;
            for (var pushResult : pushResults) {
                for (var remoteRefUpdate : pushResult.getRemoteUpdates()) {
                    result.details += "  Branch update: " + remoteRefUpdate.getSrcRef() + " -> " +
                            remoteRefUpdate.getRemoteName() + " (" + remoteRefUpdate.getStatus() + ")\n";

                    if (remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK ||
                            remoteRefUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                        pushSuccess = true;
                    }
                }
            }

            if (!pushSuccess) {
                throw new RuntimeException("Force push failed");
            }

            result.success = true;
            result.message = "Force push successful, remote changes have been overwritten";

            log.info("Force pushed changes for workspace: {}", workspace.getName());
        }

        return result;
    }

    /**
     * 暂存本地变更
     */
    public GitOperationResult stashChanges(String workspaceId) throws Exception {
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Stash";

        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }

        try (Git git = Git.open(workspace.getPath().toFile())) {
            // 获取暂存前的状态
            var status = git.status().call();

            // 记录被暂存的文件
            result.affectedFiles.addAll(status.getModified());
            result.affectedFiles.addAll(status.getChanged());
            result.affectedFiles.addAll(status.getAdded());
            result.affectedFiles.addAll(status.getRemoved());

            // 执行暂存
            var stashResult = git.stashCreate()
                    .call();

            if (stashResult == null) {
                throw new RuntimeException("Stash failed, no changes to stash");
            }

            result.success = true;
            result.message = "Successfully stashed " + result.affectedFiles.size() + " file changes";
            result.details = "Stash ID: " + stashResult.getName().substring(0, 8) + "\n";
            result.details += "Stash time: " +
                    java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n";
            result.details += "Stashed files:\n";
            for (String file : result.affectedFiles) {
                result.details += "  " + file + "\n";
            }

            log.info("Stashed changes for workspace: {}", workspace.getName());
        }

        return result;
    }

    /**
     * 恢复暂存的变更
     */
    public GitOperationResult popStashChanges(String workspaceId) throws Exception {
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Pop Stash";

        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }

        try (Git git = Git.open(workspace.getPath().toFile())) {
            // 检查是否有暂存
            var stashList = git.stashList().call();
            if (!stashList.iterator().hasNext()) {
                throw new RuntimeException("No stashed changes found");
            }

            // 恢复最新的暂存
            git.stashApply().call();
            git.stashDrop().call(); // 删除已应用的暂存

            // 获取恢复后的状态
            var status = git.status().call();
            result.affectedFiles.addAll(status.getModified());
            result.affectedFiles.addAll(status.getChanged());
            result.affectedFiles.addAll(status.getAdded());

            result.success = true;
            result.message = "Successfully restored stashed changes";
            result.details = "Restored files:\n";
            for (String file : result.affectedFiles) {
                result.details += "  " + file + "\n";
            }

            log.info("Popped stash for workspace: {}", workspace.getName());
        }

        return result;
    }

    /**
     * 强制拉取更新（丢弃本地变更）
     */
    public GitOperationResult forcePullUpdates(String workspaceId) throws Exception {
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Force Pull";

        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }

        try (Git git = Git.open(workspace.getPath().toFile())) {
            // 记录拉取前的未提交变更（将被丢弃）
            var statusBefore = git.status().call();
            result.affectedFiles.addAll(statusBefore.getModified());
            result.affectedFiles.addAll(statusBefore.getChanged());
            result.affectedFiles.addAll(statusBefore.getAdded());
            result.affectedFiles.addAll(statusBefore.getUntracked());

            String commitIdBefore = getLastCommitId(git);

            result.details += "❗ Force pull will discard the following local changes:\n";
            for (String file : result.affectedFiles) {
                result.details += "  " + file + "\n";
            }
            result.details += "\n";

            // 获取当前分支名
            String branchName = git.getRepository().getBranch();
            // 拉取远程内容
            UsernamePasswordCredentialsProvider credentialsProvider = getCredentialsProvider(workspace);
            var fetchCmd = git.fetch();
            fetchCmd.setTimeout(GIT_OPERATION_TIMEOUT); // 设置超时时间
            if (credentialsProvider != null) {
                fetchCmd.setCredentialsProvider(credentialsProvider);
            }
            fetchCmd.call();
            // 强制重置到远程分支状态
            git.reset().setRef("origin/" + branchName).setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call();
            git.clean().setCleanDirectories(true).setForce(true).call();

            String commitIdAfter = getLastCommitId(git);

            // 更新工作区信息
            workspace.setLastCommitId(commitIdAfter);
            workspace.setUpdatedAt(System.currentTimeMillis());
            saveWorkspaces();

            result.success = true;
            result.message = "Force pull successful, local changes have been discarded";

            if (!commitIdBefore.equals(commitIdAfter)) {
                result.details += "Pulled new commits\n";
            } else {
                result.details += "Already up to date\n";
            }

            log.info("Force pulled updates for workspace: {}", workspace.getName());
        }

        return result;
    }

    /**
     * Git操作：提交变更
     */
    public GitOperationResult commitChanges(String workspaceId, String message) throws Exception {
        GitOperationResult result = new GitOperationResult();
        result.operationType = "Commit";

        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }

        try (Git git = Git.open(workspace.getPath().toFile())) {
            // 检查是否有文件需要提交
            var status = git.status().call();
            boolean hasChanges = !status.getAdded().isEmpty() ||
                    !status.getModified().isEmpty() ||
                    !status.getRemoved().isEmpty() ||
                    !status.getUntracked().isEmpty() ||
                    !status.getMissing().isEmpty();

            if (!hasChanges) {
                throw new IllegalStateException("No file changes to commit");
            }

            // 记录要提交的文件
            result.details += "Commit details:\n";
            result.details += "  Commit message: " + message + "\n";

            if (!status.getAdded().isEmpty()) {
                result.affectedFiles.addAll(status.getAdded());
                result.details += "  Added files (" + status.getAdded().size() + "):\n";
                for (String file : status.getAdded()) {
                    result.details += "    + " + file + "\n";
                }
            }

            if (!status.getModified().isEmpty()) {
                result.affectedFiles.addAll(status.getModified());
                result.details += "  Modified files (" + status.getModified().size() + "):\n";
                for (String file : status.getModified()) {
                    result.details += "    M " + file + "\n";
                }
            }

            if (!status.getRemoved().isEmpty()) {
                result.affectedFiles.addAll(status.getRemoved());
                result.details += "  Deleted files (" + status.getRemoved().size() + "):\n";
                for (String file : status.getRemoved()) {
                    result.details += "    - " + file + "\n";
                }
            }

            if (!status.getUntracked().isEmpty()) {
                result.affectedFiles.addAll(status.getUntracked());
                result.details += "  Untracked files (" + status.getUntracked().size() + "):\n";
                for (String file : status.getUntracked()) {
                    result.details += "    ? " + file + "\n";
                }
            }

            git.add().addFilepattern(".").call();
            var commitResult = git.commit().setMessage(message).call();

            workspace.setLastCommitId(getLastCommitId(git));
            workspace.setUpdatedAt(System.currentTimeMillis());
            saveWorkspaces();

            result.success = true;
            result.message = "Successfully committed " + result.affectedFiles.size() + " file changes";
            result.details += "  Commit ID: " + commitResult.getName().substring(0, 8) + "\n";

            log.info("Committed changes for workspace: {}", workspace.getName());
        }

        return result;
    }

    /**
     * 获取两个 commit 之间的变更文件列表
     */
    public List<String> getChangedFilesBetweenCommits(String workspaceId, String oldCommitId, String newCommitId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        try (Git git = Git.open(workspace.getPath().toFile())) {
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
        } catch (Exception e) {
            log.error("Failed to get changed files between commits", e);
            throw e;
        }
    }

    /**
     * 辅助方法：获取 commit 的 tree parser
     */
    private AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objectId) throws Exception {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objectId);
            ObjectId treeId = commit.getTree().getId();
            CanonicalTreeParser treeWalk = new CanonicalTreeParser();
            try (var reader = repository.newObjectReader()) {
                treeWalk.reset(reader, treeId);
            }
            return treeWalk;
        } catch (Exception e) {
            log.error("Failed to prepare tree parser", e);
            throw e;
        }
    }

    /**
     * 根据ID获取工作区
     */
    private Workspace getWorkspaceById(String workspaceId) {
        return workspaces.stream()
                .filter(w -> w.getId().equals(workspaceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(WORKSPACE_NOT_FOUND_MSG + workspaceId));
    }

    /**
     * 加载工作区配置
     */
    private void loadWorkspaces() {
        try {
            workspaces = WorkspaceStorageUtil.loadWorkspaces();

            String currentWorkspaceId = WorkspaceStorageUtil.getCurrentWorkspace();
            if (currentWorkspaceId != null) {
                currentWorkspace = workspaces.stream()
                        .filter(w -> w.getId().equals(currentWorkspaceId))
                        .findFirst()
                        .orElse(null);
            }

            // 如果没有当前工作区但有工作区列表，设置第一个为当前工作区
            if (currentWorkspace == null && !workspaces.isEmpty()) {
                currentWorkspace = workspaces.get(0);
                WorkspaceStorageUtil.saveCurrentWorkspace(currentWorkspace.getId());
            }

            log.info("Loaded {} workspaces", workspaces.size());
        } catch (Exception e) {
            log.error("Failed to load workspaces", e);
            workspaces = new ArrayList<>();
        }
    }

    /**
     * 保存工作区配置
     */
    private void saveWorkspaces() {
        try {
            WorkspaceStorageUtil.saveWorkspaces(workspaces);
        } catch (Exception e) {
            log.error("Failed to save workspaces", e);
        }
    }

    /**
     * 为 INITIALIZED 工作区添加远程仓库
     */
    public void addRemoteRepository(String workspaceId, String remoteUrl, String remoteBranch,
                                    GitAuthType authType, String username, String password, String token) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT || workspace.getGitRepoSource() != GitRepoSource.INITIALIZED) {
            throw new IllegalStateException("Only Git workspaces of type INITIALIZED can add a remote repository");
        }

        try (Git git = Git.open(workspace.getPath().toFile())) {
            // 添加远程仓库
            git.remoteAdd()
                    .setName("origin")
                    .setUri(new URIish(remoteUrl))
                    .call();

            // 获取当前分支名称
            String currentBranch = git.getRepository().getBranch();

            // 处理远程分支名称
            String targetRemoteBranch = remoteBranch;
            if (targetRemoteBranch != null && targetRemoteBranch.startsWith("origin/")) {
                // 去掉 origin/ 前缀，获取纯分支名
                targetRemoteBranch = targetRemoteBranch.substring("origin/".length());
            }

            // 如果没有指定远程分支，使用当前分支名
            if (targetRemoteBranch == null || targetRemoteBranch.trim().isEmpty()) {
                targetRemoteBranch = currentBranch;
            }

            // 设置当前分支的 tracking 关系
            var config = git.getRepository().getConfig();
            config.setString("branch", currentBranch, "remote", "origin");
            config.setString("branch", currentBranch, "merge", "refs/heads/" + targetRemoteBranch);
            config.save();

            log.info("设置分支 tracking 关系: {} -> origin/{}", currentBranch, targetRemoteBranch);

            // 更新工作区信息
            workspace.setGitRemoteUrl(remoteUrl);
            workspace.setRemoteBranch("origin/" + targetRemoteBranch); // 保存完整的远程分支格式
            workspace.setGitAuthType(authType);
            workspace.setGitUsername(username);

            if (authType == GitAuthType.PASSWORD) {
                workspace.setGitPassword(password);
                workspace.setGitToken(null);
            } else if (authType == GitAuthType.TOKEN) {
                workspace.setGitToken(token);
                workspace.setGitPassword(null);
            }

            workspace.setUpdatedAt(System.currentTimeMillis());
            saveWorkspaces();

            log.info("Added remote repository for workspace: {} -> {}, tracking: {}",
                    workspace.getName(), remoteUrl, "origin/" + targetRemoteBranch);
        }
    }

    /**
     * 获取远程仓库状态信息
     */
    public RemoteStatus getRemoteStatus(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }

        try (Git git = Git.open(workspace.getPath().toFile())) {
            RemoteStatus status = new RemoteStatus();

            // 获取远程仓库列表
            var remotes = git.remoteList().call();
            status.hasRemote = !remotes.isEmpty();

            if (status.hasRemote) {
                status.remoteUrl = remotes.get(0).getURIs().get(0).toString();

                // 检查当前分支是否有上游分支
                String currentBranch = git.getRepository().getBranch();
                String tracking = git.getRepository().getConfig().getString("branch", currentBranch, "merge");
                status.hasUpstream = tracking != null;
                status.currentBranch = currentBranch;
                status.upstreamBranch = tracking;
            }

            return status;
        } catch (Exception e) {
            log.error("Failed to get remote status for workspace: {}", workspace.getName(), e);
            throw e;
        }
    }

    /**
     * 更新工作区的 Git 认证信息
     */
    public void updateGitAuthentication(String workspaceId, GitAuthType authType,
                                       String username, String password, String token,
                                       String sshKeyPath, String sshPassphrase) {
        Workspace workspace = getWorkspaceById(workspaceId);
        if (workspace.getType() != WorkspaceType.GIT) {
            throw new IllegalStateException("Not a Git workspace");
        }

        if (workspace.getGitRemoteUrl() == null || workspace.getGitRemoteUrl().isEmpty()) {
            throw new IllegalStateException("Workspace does not have a remote repository configured");
        }

        // 更新工作区的认证信息
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
            workspace.setSshPrivateKeyPath(sshKeyPath);
            workspace.setSshPassphrase(sshPassphrase);
            workspace.setGitPassword(null);
            workspace.setGitToken(null);
            workspace.setGitUsername(null);
        } else if (authType == GitAuthType.NONE) {
            workspace.setGitPassword(null);
            workspace.setGitToken(null);
            workspace.setGitUsername(null);
            workspace.setSshPrivateKeyPath(null);
            workspace.setSshPassphrase(null);
        }

        workspace.setUpdatedAt(System.currentTimeMillis());
        saveWorkspaces();

        log.info("Updated Git authentication for workspace: {}, authType: {}", workspace.getName(), authType);
    }
}
