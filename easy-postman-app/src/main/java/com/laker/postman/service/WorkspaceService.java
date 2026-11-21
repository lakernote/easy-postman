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
import com.laker.postman.plugin.git.GitWorkspaceLayout;
import com.laker.postman.plugin.host.GitServiceAccess;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import com.laker.postman.service.collections.DefaultCollectionDocumentFactory;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.SafeFileUtil;
import com.laker.postman.service.env.DefaultEnvironments;
import com.laker.postman.util.WorkspaceStorageUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final long GIT_LAYOUT_CACHE_TTL_MS = 3000;
    static final String AGENT_GUIDE_FILE = "AGENTS.md";
    private static final List<String> WORKSPACE_DATA_FILES = List.of(
            "collections.json", "environments.json", "functional_config.json",
            "performance_config.json", "mock_servers.json");
    private final Map<String, CachedGitLayout> gitLayoutCache = new ConcurrentHashMap<>();

    private final Set<String> resolvingGitLayouts = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ExecutorService gitLayoutExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "workspace-git-layout");
                thread.setDaemon(true);
                return thread;
            });

    private record CachedGitLayout(GitWorkspaceLayout layout, long checkedAt) {
    }

    /**
     * 不阻塞调用线程的 Git 布局查询结果。
     *
     * @param known  是否已有磁盘探测结果；为 {@code false} 时只能按已保存的类型暂时显示
     * @param layout 所在仓库布局；不在 Git 仓库中时为 {@code null}
     */
    public record GitLayoutPeek(boolean known, GitWorkspaceLayout layout) {
    }

    /**
     * 串行创建（新建、打开文件夹可能在后台线程并发进行），避免同一目录被登记两次。
     */
    public synchronized void createWorkspace(Workspace workspace) throws Exception {
        if (WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            throw new IllegalArgumentException("Cannot create the default workspace");
        }
        validateWorkspace(workspace);
        // 生成唯一ID
        workspace.setId(UUID.randomUUID().toString());
        workspace.setCreatedAt(System.currentTimeMillis());
        workspace.setUpdatedAt(System.currentTimeMillis());

        // 对于非Git克隆模式，创建本地目录；空目录写入初始数据（已在代码仓库中时只写空文件，不带示例）
        Path workspacePath = Paths.get(workspace.getPath());
        boolean seeded = false;
        boolean hadAgentGuide = Files.exists(workspacePath.resolve(AGENT_GUIDE_FILE));
        if (workspace.getType() != WorkspaceType.GIT || workspace.getGitRepoSource() != GitRepoSource.CLONED) {
            if (!Files.exists(workspacePath)) {
                Files.createDirectories(workspacePath);
            }
            seeded = !containsWorkspaceData(workspacePath);
            seedWorkspaceData(workspacePath, GitWorkspaceLayout.detectDirectory(workspacePath).isEmpty());
        }

        // 根据工作区类型执行相应操作
        if (workspace.getType() == WorkspaceType.GIT) {
            try {
                handleGitWorkspace(workspace);
            } catch (Exception e) {
                // 创建失败时不在（可能属于代码仓库的）目录里留下刚写入的初始文件
                if (seeded) {
                    Files.deleteIfExists(workspacePath.resolve("collections.json"));
                    Files.deleteIfExists(workspacePath.resolve("environments.json"));
                    if (!hadAgentGuide) {
                        Files.deleteIfExists(workspacePath.resolve(AGENT_GUIDE_FILE));
                    }
                }
                throw e;
            }
        }

        invalidateGitLayoutCache();
        workspaces.add(workspace);
        saveWorkspaces();

        log.info("Created workspace: {} ({})", workspace.getName(), workspace.getType());
    }

    /**
     * 目录已被其他工作区使用。
     */
    public static class DuplicateWorkspacePathException extends IllegalArgumentException {
        public DuplicateWorkspacePathException(String path) {
            super("Path is already used by another workspace: " + path);
        }
    }

    /**
     * 规范化路径用于比较：解析符号链接（目录不存在时解析最近的已存在祖先）。
     */
    static Path canonicalPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
        } catch (IOException e) {
            return absolute;
        }
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

        if (findWorkspaceByPath(Paths.get(workspace.getPath())) != null) {
            throw new DuplicateWorkspacePathException(workspace.getPath());
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
            boolean firstRun = !WorkspaceStorageUtil.hasWorkspaceRegistry();
            workspaces = WorkspaceStorageUtil.loadWorkspaces();

            String storedCurrentWorkspaceId = WorkspaceStorageUtil.getCurrentWorkspace();
            if (storedCurrentWorkspaceId != null) {
                currentWorkspace = workspaces.stream()
                        .filter(w -> w.getId().equals(storedCurrentWorkspaceId))
                        .findFirst()
                        .orElse(null);
            }

            // 如果没有当前工作区但有工作区列表，设置第一个为当前工作区
            boolean storedWorkspaceSelected = currentWorkspace != null;
            if (currentWorkspace == null && !workspaces.isEmpty()) {
                currentWorkspace = workspaces.get(0);
                WorkspaceStorageUtil.saveCurrentWorkspace(currentWorkspace.getId());
            }

            if (firstRun) {
                // 示例数据只在首次启动写入一次；之后默认工作区即使被清空也保持为空。
                // 写入失败时不保存登记表，下次启动仍按首次启动重试。
                boolean seeded = workspaces.stream()
                        .map(WorkspaceService::seedDefaultWorkspaceOnFirstRun)
                        .reduce(true, Boolean::logicalAnd);
                if (seeded) {
                    WorkspaceStorageUtil.saveWorkspaces(workspaces);
                }
            }

            String selectionSource = storedWorkspaceSelected
                    ? "saved_workspace"
                    : storedCurrentWorkspaceId == null ? "first_available_workspace" : "saved_workspace_not_found";
            log.info("Loaded workspaces: count={}, storedCurrentWorkspaceId={}, selectedCurrentWorkspaceId={}, selectionSource={}",
                    workspaces.size(), storedCurrentWorkspaceId,
                    currentWorkspace == null ? null : currentWorkspace.getId(), selectionSource);
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
     * 为 Git 工作区新增远程仓库或更新已有 origin。
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

    public List<String> testRemoteConnection(String remoteUrl, GitAuthType authType,
                                             String username, String password, String token,
                                             String sshPrivateKeyPath, String sshPassphrase) throws Exception {
        return requireGitService().testRemoteConnection(remoteUrl, authType, username, password, token,
                sshPrivateKeyPath, sshPassphrase);
    }

    /**
     * 获取远程仓库状态信息
     */
    public RemoteStatus getRemoteStatus(String workspaceId) throws Exception {
        Workspace workspace = getWorkspaceById(workspaceId);
        return requireGitService().getRemoteStatus(workspace);
    }

    /**
     * 将本地工作区转换为Git工作区
     * 复用现有的Git初始化逻辑
     */
    public void convertLocalToGit(Workspace workspace, String localBranch) throws Exception {
        requireGitService().convertLocalToGit(workspace, localBranch);
        invalidateGitLayoutCache();
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

    /**
     * Git 能力来自磁盘事实：工作区目录位于任意（非 bare）Git 工作树内即可使用。
     */
    public GitWorkspaceLayout detectGitLayout(Workspace workspace) {
        if (workspace == null || workspace.getPath() == null) {
            return null;
        }
        String key = gitLayoutCacheKey(workspace);
        long now = System.currentTimeMillis();
        CachedGitLayout cached = gitLayoutCache.get(key);
        if (cached != null && now - cached.checkedAt() < GIT_LAYOUT_CACHE_TTL_MS) {
            return cached.layout();
        }
        GitWorkspaceLayout layout = GitWorkspaceLayout.detect(workspace).orElse(null);
        gitLayoutCache.put(key, new CachedGitLayout(layout, now));
        return layout;
    }

    /**
     * 供界面渲染使用：只读取缓存，缓存缺失或过期时在后台探测，结果变化后在 EDT 上回调（通常是重绘）。
     */
    public GitLayoutPeek peekGitLayout(Workspace workspace, Runnable onResolved) {
        if (workspace == null || workspace.getPath() == null) {
            return new GitLayoutPeek(true, null);
        }
        String key = gitLayoutCacheKey(workspace);
        CachedGitLayout cached = gitLayoutCache.get(key);
        boolean fresh = cached != null && System.currentTimeMillis() - cached.checkedAt() < GIT_LAYOUT_CACHE_TTL_MS;
        if (!fresh && resolvingGitLayouts.add(key)) {
            gitLayoutExecutor.execute(() -> {
                boolean changed;
                try {
                    GitWorkspaceLayout layout = GitWorkspaceLayout.detect(workspace).orElse(null);
                    CachedGitLayout previous = gitLayoutCache.put(key, new CachedGitLayout(layout, System.currentTimeMillis()));
                    changed = previous == null || !java.util.Objects.equals(previous.layout(), layout);
                } finally {
                    resolvingGitLayouts.remove(key);
                }
                if (changed && onResolved != null) {
                    javax.swing.SwingUtilities.invokeLater(onResolved);
                }
            });
        }
        return cached == null ? new GitLayoutPeek(false, null) : new GitLayoutPeek(true, cached.layout());
    }

    /**
     * 仓库归属判断会用到创建来源和记录的提交，它们变化时不能复用旧结果。
     */
    private static String gitLayoutCacheKey(Workspace workspace) {
        return String.join("|", workspace.getPath(), String.valueOf(workspace.getGitRepoSource()),
                String.valueOf(workspace.getLastCommitId()));
    }

    public void invalidateGitLayoutCache() {
        gitLayoutCache.clear();
    }

    /**
     * 把已有目录（可位于任意代码仓库中）注册为本地工作区；Git 能力由磁盘自动检测，不修改仓库。
     */
    public synchronized Workspace openFolderAsWorkspace(Path directory, String name) throws Exception {
        Workspace workspace = new Workspace();
        workspace.setName(name);
        workspace.setType(WorkspaceType.LOCAL);
        workspace.setPath(directory.toAbsolutePath().normalize().toString());
        try {
            createWorkspace(workspace);
            return workspace;
        } catch (DuplicateWorkspacePathException e) {
            // 同一目录已登记（例如重复点击打开）：直接使用已有登记
            Workspace existing = findWorkspaceByPath(directory);
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    public Workspace findWorkspaceByPath(Path directory) {
        Path canonical = canonicalPath(directory);
        return new ArrayList<>(workspaces).stream()
                .filter(w -> w.getPath() != null && canonicalPath(Paths.get(w.getPath())).equals(canonical))
                .findFirst()
                .orElse(null);
    }

    /**
     * 目录中还没有任何工作区数据时写入初始的集合与环境文件；加载数据时缺失的文件不会再自动生成。
     */
    static void seedWorkspaceData(Path directory, boolean withSamples) throws IOException {
        if (containsWorkspaceData(directory)) {
            return;
        }
        String collections = withSamples
                ? CollectionDocumentJsonCodec.toJson(DefaultCollectionDocumentFactory.create()).toStringPretty()
                : "[]";
        String environments = withSamples ? DefaultEnvironments.json() : "[]";
        SafeFileUtil.writeStringAtomically(directory.resolve("collections.json"), collections);
        SafeFileUtil.writeStringAtomically(directory.resolve("environments.json"), environments);
        writeAgentGuideIfAbsent(directory);
    }

    /**
     * 写入给 AI agent 阅读的 AGENTS.md；已有时不覆盖。目录本身是已有仓库的根目录时跳过，避免和仓库自己的说明冲突。
     */
    static void writeAgentGuideIfAbsent(Path directory) {
        Path target = directory.resolve(AGENT_GUIDE_FILE);
        try {
            if (Files.exists(target) || GitWorkspaceLayout.detectDirectory(directory)
                    .map(layout -> !layout.isEmbedded()).orElse(false)) {
                return;
            }
            String resource = I18nUtil.isChinese() ? "/workspace/AGENTS_zh.md" : "/workspace/AGENTS_en.md";
            try (var in = WorkspaceService.class.getResourceAsStream(resource)) {
                if (in != null) {
                    Files.write(target, in.readAllBytes());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to write {}", target, e);
        }
    }

    private static boolean seedDefaultWorkspaceOnFirstRun(Workspace workspace) {
        if (workspace == null || !WorkspaceStorageUtil.isDefaultWorkspace(workspace) || workspace.getPath() == null) {
            return true;
        }
        return seedFirstRunDirectory(Paths.get(workspace.getPath()));
    }

    /**
     * 首次启动时，默认工作区目录不存在或为空才写入示例数据。
     */
    static boolean seedFirstRunDirectory(Path directory) {
        if (Files.exists(directory) && !isEmptyDirectory(directory)) {
            return true;
        }
        try {
            Files.createDirectories(directory);
            seedWorkspaceData(directory, true);
            return true;
        } catch (IOException e) {
            log.warn("Failed to seed default workspace: {}", directory, e);
            return false;
        }
    }

    private static boolean isEmptyDirectory(Path directory) {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean containsWorkspaceData(Path directory) {
        for (String fileName : WORKSPACE_DATA_FILES) {
            if (Files.isRegularFile(directory.resolve(fileName))) {
                return true;
            }
        }
        return false;
    }

    public boolean isGitEnabled(Workspace workspace) {
        return detectGitLayout(workspace) != null;
    }

    /**
     * 仅 EasyPostman 创建/克隆且数据目录即仓库根时，才开放强制拉取/推送、Stash、历史恢复等整仓库改写操作。
     */
    public boolean hasGitFullControl(Workspace workspace) {
        GitWorkspaceLayout layout = detectGitLayout(workspace);
        return layout != null && layout.isFullControl();
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
