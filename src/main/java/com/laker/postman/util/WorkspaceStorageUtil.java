package com.laker.postman.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作区数据存储工具类
 * 负责工作区数据的 JSON 文件持久化
 */
@Slf4j
public class WorkspaceStorageUtil {

    private static final String WORKSPACES_FILE = "workspaces.json";
    private static final String WORKSPACE_SETTINGS_FILE = "workspace_settings.json";
    private static final Path WORKSPACES_PATH = SystemUtil.EASY_POSTMAN_HOME.resolve(WORKSPACES_FILE);
    private static final Path WORKSPACE_SETTINGS_PATH = SystemUtil.EASY_POSTMAN_HOME.resolve(WORKSPACE_SETTINGS_FILE);
    private static final Object lock = new Object();

    private static final String DEFAULT_WORKSPACE_ID = "default-workspace";
    private static final String DEFAULT_WORKSPACE_NAME = I18nUtil.getMessage(MessageKeys.WORKSPACE_DEFAULT_NAME);
    private static final String DEFAULT_WORKSPACE_DESCRIPTION = I18nUtil.getMessage(MessageKeys.WORKSPACE_DEFAULT_DESCRIPTION);

    private WorkspaceStorageUtil() {
        // 私有构造函数，禁止实例化
    }

    /**
     * 判断是否为默认工作区
     */
    public static boolean isDefaultWorkspace(Workspace workspace) {
        return workspace != null && DEFAULT_WORKSPACE_ID.equals(workspace.getId());
    }

    /**
     * 获取默认工作区对象
     */
    public static Workspace getDefaultWorkspace() {
        Workspace ws = new Workspace();
        ws.setId(DEFAULT_WORKSPACE_ID);
        ws.setName(DEFAULT_WORKSPACE_NAME);
        ws.setType(WorkspaceType.LOCAL);
        ws.setPath(SystemUtil.EASY_POSTMAN_HOME);
        ws.setDescription(DEFAULT_WORKSPACE_DESCRIPTION);
        ws.setCreatedAt(System.currentTimeMillis());
        ws.setUpdatedAt(System.currentTimeMillis());
        return ws;
    }

    /**
     * 保存工作区列表
     */
    public static void saveWorkspaces(List<Workspace> workspaces) {
        synchronized (lock) {
            try {
                // 确保目录存在
                File file = WORKSPACES_PATH.toFile();
                FileUtil.mkParentDirs(file);
                // 保证默认工作区始终存在
                boolean hasDefault = workspaces.stream().anyMatch(WorkspaceStorageUtil::isDefaultWorkspace);
                if (!hasDefault) {
                    workspaces.add(0, getDefaultWorkspace());
                }
                String json = JSONUtil.toJsonPrettyStr(workspaces);
                FileUtil.writeString(json, file, StandardCharsets.UTF_8);
                log.debug("Saved {} workspaces to {}", workspaces.size(), WORKSPACES_PATH);
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
                File file = WORKSPACES_PATH.toFile();
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
                // 保证默认工作区始终存在
                boolean hasDefault = workspaces.stream().anyMatch(WorkspaceStorageUtil::isDefaultWorkspace);
                if (!hasDefault) {
                    workspaces.add(0, getDefaultWorkspace());
                }
                log.debug("Loaded {} workspaces from {}", workspaces.size(), WORKSPACES_PATH);
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
                File file = WORKSPACE_SETTINGS_PATH.toFile();
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
            File file = WORKSPACE_SETTINGS_PATH.toFile();
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
}