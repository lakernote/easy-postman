package com.laker.postman.service;

import com.laker.postman.model.GitAuthType;
import com.laker.postman.model.GitBranchInfo;
import com.laker.postman.model.GitCommitInfo;
import com.laker.postman.model.GitFileChange;
import com.laker.postman.model.GitOperation;
import com.laker.postman.model.GitOperationResult;
import com.laker.postman.model.GitRepoSource;
import com.laker.postman.model.GitStatusCheck;
import com.laker.postman.model.RemoteStatus;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.plugin.api.service.GitPluginService;
import com.laker.postman.plugin.host.GitServiceAccess;
import com.laker.postman.util.WorkspaceStorageUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 工作区服务类
 * 负责工作区的创建、管理、Git操作等核心功能
 */
@Slf4j
public class WorkspaceService {

    private static final String WORKSPACE_NOT_FOUND_MSG = "Workspace not found: ";

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
            Path workspacePath = Paths.get(workspace.getPath());
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
        requireGitService().prepareGitWorkspace(workspace);
    }

    /**
     * 验证工作区参数
     */
    private void validateWorkspace(Workspace workspace) throws IllegalArgumentException {
        if (workspace.getName() == null || workspace.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace name is required");
        }

        if (workspace.getPath() == null || workspace.getPath().trim().isEmpty()) {
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
        String normalizedPath = Paths.get(workspace.getPath()).toAbsolutePath().normalize().toString();
        boolean pathExists = workspaces.stream()
                .anyMatch(w -> Paths.get(w.getPath()).toAbsolutePath().normalize().toString().equals(normalizedPath));

        if (pathExists) {
            throw new IllegalArgumentException("Path is already used by another workspace");
        }

        // 对于Git克隆模式，检查目标目录是否已存在且不为空
        if (workspace.getType() == WorkspaceType.GIT && workspace.getGitRepoSource() == GitRepoSource.CLONED) {
            Path targetPath = Paths.get(workspace.getPath());
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
     * 从 EasyPostman 中移除工作区记录，保留本地目录及其中的所有文件。
     */
    public void removeWorkspace(String workspaceId) {
        Workspace workspace = workspaces.stream()
                .filter(w -> w.getId().equals(workspaceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(WORKSPACE_NOT_FOUND_MSG + workspaceId));
        if (WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            throw new IllegalArgumentException("Default workspace cannot be removed");
        }

        List<Workspace> remainingWorkspaces = new ArrayList<>(workspaces);
        remainingWorkspaces.removeIf(w -> w.getId().equals(workspaceId));

        // 先持久化，保存失败时保持内存状态和 UI 不变。
        WorkspaceStorageUtil.saveWorkspaces(remainingWorkspaces);
        workspaces = remainingWorkspaces;

        // 如果移除的是当前工作区，切换到默认工作区
        if (currentWorkspace != null && currentWorkspace.getId().equals(workspaceId)) {
            // 优先切换到默认工作区，找不到则取列表第一个，确保 currentWorkspace 不为 null
            currentWorkspace = getDefaultWorkspace();
            if (currentWorkspace != null) {
                WorkspaceStorageUtil.saveCurrentWorkspace(currentWorkspace.getId());
            } else {
                log.warn("No workspace available after removal, currentWorkspace is null");
            }
        }

        log.info("Removed workspace registration and preserved local files: {} ({})",
                workspace.getName(), workspace.getPath());
    }

    public Workspace getDefaultWorkspace() {
        return workspaces.stream()
                .filter(WorkspaceStorageUtil::isDefaultWorkspace)
                .findFirst()
                .orElse(workspaces.isEmpty() ? null : workspaces.get(0));
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
     * Git操作：拉取更新
     */
    public GitOperationResult pullUpdates(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().pullUpdates(workspace);
        saveWorkspaces();
        return result;
    }

    /**
     * Git操作：推送变更
     */
    public GitOperationResult pushChanges(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().pushChanges(workspace);
        saveWorkspaces();
        return result;
    }

    /**
     * 强制推送变更（覆盖远程变更）
     */
    public GitOperationResult forcePushChanges(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().forcePushChanges(workspace);
        saveWorkspaces();
        return result;
    }

    /**
     * 暂存本地变更
     */
    public GitOperationResult stashChanges(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().stashChanges(workspace);
        saveWorkspaces();
        return result;
    }

    /**
     * 恢复暂存的变更
     */
    public GitOperationResult popStashChanges(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().popStashChanges(workspace);
        saveWorkspaces();
        return result;
    }

    /**
     * 强制拉取更新（丢弃本地变更）
     */
    public GitOperationResult forcePullUpdates(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().forcePullUpdates(workspace);
        saveWorkspaces();
        return result;
    }

    /**
     * Git操作：提交变更
     */
    public GitOperationResult commitChanges(String workspaceId, String message) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().commitChanges(workspace, message);
        saveWorkspaces();
        return result;
    }

    /**
     * 获取两个 commit 之间的变更文件列表
     */
    public List<String> getChangedFilesBetweenCommits(String workspaceId, String oldCommitId, String newCommitId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        return requireGitService().getChangedFilesBetweenCommits(workspace, oldCommitId, newCommitId);
    }

    public List<GitFileChange> listWorkingTreeChanges(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        return requireGitService().listWorkingTreeChanges(workspace);
    }

    public String getWorkingTreeDiff(String workspaceId, String filePath) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        return requireGitService().getWorkingTreeDiff(workspace, filePath);
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
            // 加载失败时也要保证内存中有默认工作区，避免后续逻辑空指针
            Workspace defaultWs = WorkspaceStorageUtil.getDefaultWorkspace();
            workspaces.add(defaultWs);
            currentWorkspace = defaultWs;
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
        addRemoteRepository(workspaceId, remoteUrl, remoteBranch, authType, username, password, token, null, null);
    }

    public void addRemoteRepository(String workspaceId, String remoteUrl, String remoteBranch,
                                    GitAuthType authType, String username, String password, String token,
                                    String sshPrivateKeyPath, String sshPassphrase) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        requireGitService().addRemoteRepository(workspace, remoteUrl, remoteBranch, authType, username, password, token,
                sshPrivateKeyPath, sshPassphrase);
        saveWorkspaces();
    }

    /**
     * 获取远程仓库状态信息
     */
    public RemoteStatus getRemoteStatus(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        return requireGitService().getRemoteStatus(workspace);
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

    /**
     * 将本地工作区转换为Git工作区
     * 复用现有的Git初始化逻辑
     */
    public void convertLocalToGit(Workspace workspace, String localBranch) throws Exception {
        requireGitService().convertLocalToGit(workspace, localBranch);
        saveWorkspaces();
    }

    /**
     * 保存工作区顺序（拖拽排序后调用）
     */
    public void saveWorkspaceOrder(List<String> idOrder) {
        try {
            // 根据idOrder重新排序workspaces列表
            List<Workspace> sortedWorkspaces = new ArrayList<>();
            for (String id : idOrder) {
                for (Workspace ws : workspaces) {
                    if (ws.getId().equals(id)) {
                        sortedWorkspaces.add(ws);
                        break;
                    }
                }
            }

            // 添加可能遗漏的工作区（防止数据丢失）
            for (Workspace ws : workspaces) {
                if (!sortedWorkspaces.contains(ws)) {
                    sortedWorkspaces.add(ws);
                }
            }

            // 更新工作区列表
            workspaces = sortedWorkspaces;

            // 持久化到文件
            saveWorkspaces();

            log.debug("Workspace order saved successfully");
        } catch (Exception e) {
            log.error("Failed to save workspace order", e);
        }
    }

    /**
     * 获取工作区的 Git 提交历史
     *
     * @param workspaceId 工作区ID
     * @param maxCount    最大返回数量，0表示返回所有
     * @return Git 提交信息列表
     */
    public List<GitCommitInfo> getGitHistory(String workspaceId, int maxCount) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        return requireGitService().getGitHistory(workspace, maxCount);
    }

    /**
     * 恢复工作区到指定的 Git 提交版本
     * 使用 reset + commit 方式，保留完整的历史记录
     *
     * @param workspaceId  工作区ID
     * @param commitId     提交ID
     * @param createBackup 是否在恢复前创建备份提交（保存未提交的更改）
     * @return 操作结果
     */
    public GitOperationResult restoreToCommit(String workspaceId, String commitId, boolean createBackup) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().restoreToCommit(workspace, commitId, createBackup);
        saveWorkspaces();
        return result;
    }

    /**
     * 查看指定提交的详细信息
     *
     * @param workspaceId 工作区ID
     * @param commitId    提交ID
     * @return 提交详细信息
     */
    public String getCommitDetails(String workspaceId, String commitId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        return requireGitService().getCommitDetails(workspace, commitId);
    }

    public GitStatusCheck checkGitStatus(Workspace workspace, GitOperation operation) {
        return requireGitService().checkGitStatus(workspace, operation);
    }

    public void clearSshCache(String privateKeyPath) {
        requireGitService().clearSshCache(privateKeyPath);
    }

    public boolean isGitServiceAvailable() {
        return GitServiceAccess.isServiceAvailable();
    }

    public List<GitBranchInfo> listGitBranches(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        return requireGitService().listBranches(workspace);
    }

    public GitOperationResult switchGitBranch(String workspaceId, String branchName) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().switchBranch(workspace, branchName);
        saveWorkspaces();
        return result;
    }

    public GitOperationResult fetchGitBranches(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        return requireGitService().fetchBranches(workspace);
    }

    public GitOperationResult createGitBranch(String workspaceId, String branchName) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().createBranch(workspace, branchName);
        saveWorkspaces();
        return result;
    }

    public GitOperationResult deleteGitBranch(String workspaceId, String branchName) throws Exception {
        return deleteGitBranch(workspaceId, branchName, false);
    }

    public GitOperationResult deleteGitBranch(String workspaceId, String branchName, boolean force) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().deleteBranch(workspace, branchName, force);
        saveWorkspaces();
        return result;
    }

    public GitOperationResult publishGitBranch(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        GitOperationResult result = requireGitService().publishBranch(workspace);
        saveWorkspaces();
        return result;
    }

    private GitPluginService requireGitService() {
        return GitServiceAccess.requireService();
    }
}
