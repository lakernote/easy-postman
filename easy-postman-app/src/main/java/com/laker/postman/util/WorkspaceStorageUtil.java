package com.laker.postman.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作区数据存储工具类
 * 负责工作区数据的 JSON 文件持久化
 */
@Slf4j
@UtilityClass
public class WorkspaceStorageUtil {

    private static final Object lock = new Object();

    private static final String DATA_ROOT_TOKEN = "$EASY_POSTMAN_DATA$";
    private static final String WORKSPACES_FILE_NAME = "workspaces.json";
    private static final String WORKSPACE_SETTINGS_FILE_NAME = "workspace_settings.json";
    private static final String MANAGED_WORKSPACES_DIR = "workspaces";
    private static final String DEFAULT_WORKSPACE_DIR = "default";
    private static final String PORTABLE_APP_DATA_WORKSPACES_MARKER = "/easypostman/app/data/workspaces/";
    private static final String DEFAULT_DATA_WORKSPACES_MARKER = "/easypostman/workspaces/";
    private static final String PORTABLE_ROOT_DATA_WORKSPACES_MARKER = "/easypostman/data/workspaces/";

    private static final String DEFAULT_WORKSPACE_ID = "default-workspace";
    private static final String DEFAULT_WORKSPACE_NAME = I18nUtil.getMessage(MessageKeys.WORKSPACE_DEFAULT_NAME);
    private static final String DEFAULT_WORKSPACE_DESCRIPTION = I18nUtil.getMessage(MessageKeys.WORKSPACE_DEFAULT_DESCRIPTION);

    /**
     * 判断是否为默认工作区
     */
    public static boolean isDefaultWorkspace(Workspace workspace) {
        return workspace != null && DEFAULT_WORKSPACE_ID.equals(workspace.getId());
    }

    /**
     * 获取默认工作区对象
     * <p>
     * 默认工作区路径为当前数据根下的 {@code workspaces/default/}，
     * 与其他子工作区平级，使根目录完全不参与 git 管理，
     * 从根本上避免嵌套 git 仓库问题。
     * </p>
     */
    public static Workspace getDefaultWorkspace() {
        Workspace ws = new Workspace();
        ws.setId(DEFAULT_WORKSPACE_ID);
        ws.setName(DEFAULT_WORKSPACE_NAME);
        ws.setType(WorkspaceType.LOCAL);
        ws.setPath(defaultWorkspacePath().toString());
        ws.setDescription(DEFAULT_WORKSPACE_DESCRIPTION);
        ws.setCreatedAt(System.currentTimeMillis());
        ws.setUpdatedAt(System.currentTimeMillis());
        return ws;
    }

    /**
     * 保存工作区列表
     * <p>
     * 注意：内部操作副本，不会修改传入的原始列表。
     * </p>
     */
    public static void saveWorkspaces(List<Workspace> workspaces) {
        synchronized (lock) {
            try {
                // 操作副本，避免修改调用方持有的列表引用（如 WorkspaceService.workspaces 字段）
                List<Workspace> toSave = new ArrayList<>();
                for (Workspace workspace : workspaces) {
                    toSave.add(toPersistedWorkspace(workspace));
                }
                // 确保目录存在
                File file = workspacesFile();
                FileUtil.mkParentDirs(file);
                // 保证默认工作区始终存在
                boolean hasDefault = toSave.stream().anyMatch(WorkspaceStorageUtil::isDefaultWorkspace);
                if (!hasDefault) {
                    toSave.add(0, toPersistedWorkspace(getDefaultWorkspace()));
                }
                String json = JSONUtil.toJsonPrettyStr(toSave);
                FileUtil.writeString(json, file, StandardCharsets.UTF_8);
                log.debug("Saved {} workspaces to {}", toSave.size(), file.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to save workspaces", e);
                throw new RuntimeException("Failed to save workspaces", e);
            }
        }
    }

    /**
     * 加载工作区列表
     */
    public static List<Workspace> loadWorkspaces() {
        synchronized (lock) {
            try {
                File file = workspacesFile();
                List<Workspace> workspaces;
                if (!file.exists()) {
                    log.debug("Workspaces file not found, returning default workspace");
                    workspaces = new ArrayList<>();
                } else {
                    String json = FileUtil.readString(file, StandardCharsets.UTF_8);
                    if (json == null || json.trim().isEmpty()) {
                        log.debug("Workspaces file is empty, returning default workspace");
                        workspaces = new ArrayList<>();
                    } else {
                        workspaces = JSONUtil.parseArray(json).toList(Workspace.class);
                    }
                }
                normalizeLoadedWorkspacePaths(workspaces);
                // 运行时视图保证默认工作区可用；持久化只在 saveWorkspaces 中发生。
                boolean hasDefault = workspaces.stream().anyMatch(WorkspaceStorageUtil::isDefaultWorkspace);
                if (!hasDefault) {
                    workspaces.add(0, getDefaultWorkspace());
                }
                log.debug("Loaded {} workspaces from {}", workspaces.size(), file.getAbsolutePath());
                return workspaces;
            } catch (Exception e) {
                log.error("Failed to load workspaces", e);
                // 加载失败也返回默认工作区
                List<Workspace> ws = new ArrayList<>();
                ws.add(getDefaultWorkspace());
                return ws;
            }
        }
    }

    /**
     * 保存当前工作区ID
     */
    public static void saveCurrentWorkspace(String workspaceId) {
        synchronized (lock) {
            try {
                File file = workspaceSettingsFile();
                FileUtil.mkParentDirs(file);

                Map<String, Object> settings = loadWorkspaceSettings();
                settings.put("currentWorkspaceId", workspaceId);

                String json = JSONUtil.toJsonPrettyStr(settings);
                FileUtil.writeString(json, file, StandardCharsets.UTF_8);
                log.debug("Saved current workspace: {}", workspaceId);
            } catch (Exception e) {
                log.error("Failed to save current workspace", e);
            }
        }
    }

    /**
     * 获取当前工作区ID
     */
    public static String getCurrentWorkspace() {
        synchronized (lock) {
            try {
                Map<String, Object> settings = loadWorkspaceSettings();
                Object currentWorkspaceId = settings.get("currentWorkspaceId");
                return currentWorkspaceId != null ? currentWorkspaceId.toString() : null;
            } catch (Exception e) {
                log.error("Failed to get current workspace", e);
                return null;
            }
        }
    }

    /**
     * 加载工作区设置
     */
    private static Map<String, Object> loadWorkspaceSettings() {
        try {
            File file = workspaceSettingsFile();
            if (!file.exists()) {
                return new HashMap<>();
            }

            String json = FileUtil.readString(file, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                return new HashMap<>();
            }

            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            log.warn("Failed to load workspace settings, returning empty map", e);
            return new HashMap<>();
        }
    }

    private static Workspace toPersistedWorkspace(Workspace workspace) {
        if (workspace == null) {
            return null;
        }
        Workspace copy = new Workspace();
        BeanUtil.copyProperties(workspace, copy);
        copy.setPath(toStoredWorkspacePath(workspace.getPath()));
        return copy;
    }

    private static String toStoredWorkspacePath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String runtimePath = resolveStoredWorkspacePath(path);
        try {
            Path workspacePath = Paths.get(runtimePath).toAbsolutePath().normalize();
            Path dataRoot = dataRootPath();
            if (!workspacePath.startsWith(dataRoot)) {
                return ensureTrailingSeparator(runtimePath.trim());
            }
            String relative = dataRoot.relativize(workspacePath).toString().replace('\\', '/');
            return DATA_ROOT_TOKEN + "/" + ensureTrailingSlash(relative);
        } catch (Exception e) {
            return ensureTrailingSeparator(path.trim());
        }
    }

    private static void normalizeLoadedWorkspacePaths(List<Workspace> workspaces) {
        for (Workspace workspace : workspaces) {
            if (workspace == null) {
                continue;
            }
            if (isDefaultWorkspace(workspace)) {
                workspace.setPath(defaultWorkspacePath().toString());
            } else {
                workspace.setPath(resolveStoredWorkspacePath(workspace.getPath()));
            }
        }
    }

    private static String resolveStoredWorkspacePath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }

        String trimmed = path.trim();
        String tokenRelative = dataRootTokenRelativePath(trimmed);
        if (tokenRelative != null) {
            return absolutePathString(dataRootPath().resolve(tokenRelative).normalize());
        }

        if (!isAbsolutePath(trimmed)) {
            return absolutePathString(dataRootPath().resolve(normalizePortableSeparators(trimmed)).normalize());
        }

        try {
            Path absolutePath = Paths.get(trimmed).toAbsolutePath().normalize();
            Path rebased = rebaseCopiedManagedWorkspacePath(trimmed, absolutePath);
            return absolutePathString(rebased);
        } catch (Exception e) {
            return ensureTrailingSeparator(trimmed);
        }
    }

    private static String dataRootTokenRelativePath(String path) {
        if (DATA_ROOT_TOKEN.equals(path)) {
            return "";
        }
        if (!path.startsWith(DATA_ROOT_TOKEN)) {
            return null;
        }
        String relative = path.substring(DATA_ROOT_TOKEN.length());
        return stripLeadingSeparators(normalizePortableSeparators(relative));
    }

    private static Path rebaseCopiedManagedWorkspacePath(String originalPath, Path absolutePath) {
        Path candidate = copiedManagedWorkspaceCandidate(originalPath);
        if (candidate != null && Files.exists(candidate)) {
            return candidate;
        }
        return absolutePath;
    }

    private static Path copiedManagedWorkspaceCandidate(String originalPath) {
        String normalized = normalizePortableSeparators(originalPath);
        String lower = normalized.toLowerCase(Locale.ROOT);
        String marker = "/" + MANAGED_WORKSPACES_DIR + "/";
        int markerIndex = lower.lastIndexOf(marker);
        if (markerIndex < 0 || !isLikelyLegacyManagedWorkspacePath(lower)) {
            return null;
        }
        String managedRelativePath = normalized.substring(markerIndex + 1);
        return dataRootPath().resolve(managedRelativePath).normalize();
    }

    private static boolean isLikelyLegacyManagedWorkspacePath(String lowerNormalizedPath) {
        return lowerNormalizedPath.contains(PORTABLE_APP_DATA_WORKSPACES_MARKER)
                || lowerNormalizedPath.contains(DEFAULT_DATA_WORKSPACES_MARKER)
                || lowerNormalizedPath.contains(PORTABLE_ROOT_DATA_WORKSPACES_MARKER);
    }

    private static boolean isAbsolutePath(String path) {
        if (path.matches("^[A-Za-z]:[\\\\/].*") || path.startsWith("\\\\") || path.startsWith("//")) {
            return true;
        }
        try {
            return Paths.get(path).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 工作区注册表是否已经写入过；不存在表示首次启动。
     */
    public static boolean hasWorkspaceRegistry() {
        return workspacesFile().exists();
    }

    private static File workspacesFile() {
        return dataRootPath().resolve(WORKSPACES_FILE_NAME).toFile();
    }

    private static File workspaceSettingsFile() {
        return dataRootPath().resolve(WORKSPACE_SETTINGS_FILE_NAME).toFile();
    }

    private static Path defaultWorkspacePath() {
        return dataRootPath().resolve(MANAGED_WORKSPACES_DIR).resolve(DEFAULT_WORKSPACE_DIR).normalize();
    }

    private static Path dataRootPath() {
        return Paths.get(SystemUtil.getEasyPostmanPath()).toAbsolutePath().normalize();
    }

    private static String absolutePathString(Path path) {
        return ensureTrailingSeparator(path.toAbsolutePath().normalize().toString());
    }

    private static String ensureTrailingSeparator(String value) {
        if (value == null || value.isBlank() || value.endsWith("/") || value.endsWith("\\")) {
            return value;
        }
        return value + File.separator;
    }

    private static String ensureTrailingSlash(String value) {
        if (value == null || value.isBlank() || value.endsWith("/")) {
            return value;
        }
        return value + "/";
    }

    private static String normalizePortableSeparators(String value) {
        return value == null ? null : value.replace('\\', '/');
    }

    private static String stripLeadingSeparators(String value) {
        String result = value;
        while (result.startsWith("/") || result.startsWith("\\")) {
            result = result.substring(1);
        }
        return result;
    }
}
