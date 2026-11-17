package com.laker.postman.plugin.git;

import com.laker.postman.model.Workspace;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * 工作区数据目录与所在 Git 仓库的关系，完全由磁盘事实推导。
 * <ul>
 *     <li>{@code scope} 为空：数据目录就是仓库根目录。</li>
 *     <li>{@code scope} 非空（如 {@code api/}）：数据目录嵌在已有代码仓库中，Git 操作只作用于该目录。</li>
 * </ul>
 * 整仓库权限（按整个仓库展示状态、提交，并开放强制拉取、强制推送、Stash、历史恢复）只授予
 * “数据目录就是仓库根目录，且仓库带有 EasyPostman 创建时写入的归属标记”的仓库；
 * 旧版本创建、尚无标记的仓库，需要工作区记录的最后提交存在于该仓库才认定。
 * 不根据工作区类型或文件名推断。其他情况：
 * <ul>
 *     <li>嵌入代码仓库子目录：只管理该数据目录（整个目录）。</li>
 *     <li>打开已有仓库的根目录：只管理 EasyPostman 的数据文件，不碰仓库里的其他文件。</li>
 * </ul>
 */
@Slf4j
@Value
public class GitWorkspaceLayout {

    /**
     * 打开已有仓库的根目录时，Git 状态、Diff、提交和历史只覆盖这些数据文件。
     */
    public static final List<String> MANAGED_DATA_FILES = List.of(
            "collections.json", "environments.json", "functional_config.json",
            "performance_config.json", "mock_servers.json");
    /**
     * EasyPostman 初始化/克隆仓库时写入仓库本地配置（.git/config，不会被提交或推送）的归属标记。
     */
    static final String OWNER_CONFIG_SECTION = "easypostman";
    static final String OWNER_CONFIG_KEY = "workspace";

    Path repositoryRoot;
    Path dataRoot;
    /**
     * 数据目录相对仓库根目录的 Git 路径，使用 {@code /} 分隔；非空时以 {@code /} 结尾。
     */
    String scope;
    boolean fullControl;
    /**
     * 当前分支；分离 HEAD 时为提交 ID 缩写，无法读取时为 {@code null}。
     */
    String branch;

    public boolean isEmbedded() {
        return !scope.isEmpty();
    }

    /**
     * 用于 JGit 路径过滤的 scope（不带末尾斜杠）；仓库根目录时返回 {@code null}。
     */
    public String scopePathFilter() {
        return isEmbedded() ? scope.substring(0, scope.length() - 1) : null;
    }

    /**
     * Git 命令使用的路径过滤；EasyPostman 拥有整个仓库时返回 {@code null}，表示不过滤。
     * 嵌入子目录时是整个数据目录；打开已有仓库的根目录时只有 EasyPostman 数据文件。
     */
    public List<String> managedPathFilters() {
        if (fullControl) {
            return null;
        }
        if (isEmbedded()) {
            return List.of(scopePathFilter());
        }
        return MANAGED_DATA_FILES;
    }

    /**
     * 仓库路径是否属于本工作区管理的范围。
     */
    public boolean isManaged(String repositoryPath) {
        return isManagedPath(repositoryPath, managedPathFilters());
    }

    /**
     * 仓库路径是否被路径过滤命中（与 JGit 的路径过滤语义一致：等于过滤项或位于该目录下）；过滤为 {@code null} 表示整个仓库。
     */
    public static boolean isManagedPath(String repositoryPath, List<String> filters) {
        if (repositoryPath == null) {
            return false;
        }
        if (filters == null) {
            return true;
        }
        for (String filter : filters) {
            if (repositoryPath.equals(filter) || repositoryPath.startsWith(filter + "/")) {
                return true;
            }
        }
        return false;
    }

    public String toRepositoryPath(String dataRelativePath) {
        return scope + dataRelativePath;
    }

    /**
     * 把仓库路径转换为数据目录内的路径；不在管理范围内时返回 {@code null}。
     */
    public String toDataPath(String repositoryPath) {
        if (!isManaged(repositoryPath)) {
            return null;
        }
        return repositoryPath.substring(scope.length());
    }

    public static Optional<GitWorkspaceLayout> detect(Workspace workspace) {
        if (workspace == null || workspace.getPath() == null || workspace.getPath().isBlank()) {
            return Optional.empty();
        }
        try {
            return detect(Paths.get(workspace.getPath()), workspace);
        } catch (Exception e) {
            log.debug("Failed to detect Git repository for workspace {}", workspace.getPath(), e);
            return Optional.empty();
        }
    }

    /**
     * 检测任意目录（可尚不存在）所在的 Git 仓库，不判断 EasyPostman 是否拥有该仓库。
     */
    public static Optional<GitWorkspaceLayout> detectDirectory(Path directory) {
        if (directory == null) {
            return Optional.empty();
        }
        try {
            return detect(directory, (Workspace) null);
        } catch (Exception e) {
            log.debug("Failed to detect Git repository for directory {}", directory, e);
            return Optional.empty();
        }
    }

    /**
     * @param workspace 已登记的工作区，用于识别旧版本创建、尚无归属标记的仓库；探测任意目录时为 {@code null}
     */
    static Optional<GitWorkspaceLayout> detect(Path dataDirectory, Workspace workspace) throws IOException {
        Path dataRoot = resolveRealPath(dataDirectory.toAbsolutePath().normalize());
        FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(dataRoot.toFile());
        addHomeCeiling(builder, dataRoot);
        if (builder.getGitDir() == null) {
            return Optional.empty();
        }
        Path repositoryRoot;
        boolean fullControl;
        String branch;
        try (Repository repository = builder.setMustExist(true).build()) {
            if (repository.isBare()) {
                return Optional.empty();
            }
            repositoryRoot = repository.getWorkTree().toPath().toRealPath();
            if (!dataRoot.startsWith(repositoryRoot)) {
                return Optional.empty();
            }
            fullControl = repositoryRoot.equals(dataRoot) && isOwnedByEasyPostman(repository, workspace);
            branch = readBranch(repository);
        }
        String scope = toGitPath(repositoryRoot.relativize(dataRoot));
        return Optional.of(new GitWorkspaceLayout(repositoryRoot, dataRoot, scope, fullControl, branch));
    }

    /**
     * 在 EasyPostman 初始化/克隆的仓库中写入归属标记。
     */
    public static void markOwnedByEasyPostman(Repository repository) throws IOException {
        StoredConfig config = repository.getConfig();
        config.setBoolean(OWNER_CONFIG_SECTION, null, OWNER_CONFIG_KEY, true);
        config.save();
    }

    /**
     * 仓库是否由 EasyPostman 创建并独占：看磁盘上的归属标记；
     * 旧版本创建的仓库没有标记时，要求工作区记录了创建来源且记录的最后提交存在于该仓库。
     */
    static boolean isOwnedByEasyPostman(Repository repository, Workspace workspace) {
        if (repository.getConfig().getBoolean(OWNER_CONFIG_SECTION, null, OWNER_CONFIG_KEY, false)) {
            return true;
        }
        if (workspace == null || workspace.getGitRepoSource() == null) {
            return false;
        }
        String lastCommitId = workspace.getLastCommitId();
        try {
            return lastCommitId != null && ObjectId.isId(lastCommitId)
                    && repository.getObjectDatabase().has(ObjectId.fromString(lastCommitId));
        } catch (IOException e) {
            log.debug("Failed to verify recorded commit for {}", workspace.getPath(), e);
            return false;
        }
    }

    private static String readBranch(Repository repository) {
        try {
            return repository.getBranch();
        } catch (Exception e) {
            return null;
        }
    }

    static Repository openRepository(GitWorkspaceLayout layout) throws IOException {
        return new FileRepositoryBuilder()
                .findGitDir(layout.getRepositoryRoot().toFile())
                .setMustExist(true)
                .build();
    }

    /**
     * 用户主目录本身不作为候选仓库，避免 dotfiles 仓库把所有本地工作区都识别成 Git 工作区。
     */
    private static void addHomeCeiling(FileRepositoryBuilder builder, Path dataRoot) {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return;
        }
        Path home = resolveRealPath(Paths.get(userHome).toAbsolutePath().normalize());
        if (dataRoot.startsWith(home) && !dataRoot.equals(home)) {
            Path gitDir = builder.getGitDir() == null ? null : builder.getGitDir().toPath().toAbsolutePath();
            if (gitDir != null && home.equals(resolveRealPath(gitDir).getParent())) {
                builder.setGitDir(null);
            }
        }
    }

    /**
     * 解析真实路径；目录尚不存在时，解析最近的已存在祖先再拼回剩余部分。
     */
    private static Path resolveRealPath(Path path) {
        Path existing = path;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return path;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(path)).normalize();
        } catch (IOException e) {
            return path;
        }
    }

    private static String toGitPath(Path relative) {
        String value = relative.toString().replace(File.separatorChar, '/');
        if (value.isEmpty()) {
            return "";
        }
        return value.endsWith("/") ? value : value + "/";
    }
}
