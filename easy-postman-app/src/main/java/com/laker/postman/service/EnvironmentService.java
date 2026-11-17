package com.laker.postman.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.model.Environment;
import com.laker.postman.model.Workspace;
import com.laker.postman.util.SafeFileUtil;
import com.laker.postman.util.SystemUtil;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * 环境变量管理服务，负责环境变量的持久化、加载和管理
 */
@Slf4j
@UtilityClass
public class EnvironmentService {

    private static final Map<String, Environment> environments = Collections.synchronizedMap(new LinkedHashMap<>());

    @Getter
    private static Environment activeEnvironment = null;


    // 当前数据文件路径
    private static String currentDataFilePath;

    static {
        loadEnvironments();
    }

    /**
     * 获取当前数据文件路径
     */
    public static String getDataFilePath() {
        if (currentDataFilePath != null) {
            return currentDataFilePath;
        }
        // 如果没有设置，返回当前工作区的环境文件路径
        Workspace currentWorkspace = WorkspaceService.getInstance().getCurrentWorkspace();
        return SystemUtil.getEnvPathForWorkspace(currentWorkspace);
    }

    /**
     * 切换环境变量数据文件路径，并重新加载
     */
    public static void setDataFilePath(String path) {
        if (path == null || path.isBlank()) return;
        currentDataFilePath = path;
        loadEnvironmentsFromPath(path);
    }

    /**
     * 从指定路径加载环境变量
     */
    private static void loadEnvironmentsFromPath(String filePath) {
        File file = new File(filePath);
        Optional<String> content;
        try {
            content = SafeFileUtil.readForLoad(file.toPath());
        } catch (IOException e) {
            content = null;
        }
        if (content != null && content.isEmpty()) {
            // 文件缺失（例如切换分支删除了它）时只清空内存，不生成默认文件，避免把仓库重新弄脏。
            log.info("环境变量文件不存在，按空环境加载: {}", filePath);
            replaceEnvironments(new LinkedHashMap<>(), null);
            rememberLoaded(filePath);
            return;
        }

        Map<String, Environment> loaded = new LinkedHashMap<>();
        Environment loadedActive = null;
        try {
            if (content == null) {
                throw new IOException("Cannot read " + filePath);
            }
            JSONArray array = JSONUtil.parseArray(content.get());
            for (Object obj : array) {
                Environment env = JSONUtil.toBean((JSONObject) obj, Environment.class);
                loaded.put(env.getId(), env);
                if (env.isActive()) {
                    loadedActive = env;
                }
            }
        } catch (Exception e) {
            // 外部编辑可能留下不完整的 JSON：保留原文件并备份，不能用默认环境覆盖。
            log.error("加载环境变量失败，原文件保持不变: {}", filePath, e);
            SafeFileUtil.backupUnreadable(file.toPath());
            replaceEnvironments(new LinkedHashMap<>(), null);
            rememberLoaded(filePath);
            return;
        }

        if (loadedActive == null && !loaded.isEmpty()) {
            // 只在内存中激活，加载过程不回写磁盘
            loadedActive = loaded.values().iterator().next();
            loadedActive.setActive(true);
        }
        replaceEnvironments(loaded, loadedActive);
        rememberLoaded(filePath);
    }

    private static void replaceEnvironments(Map<String, Environment> loaded, Environment active) {
        synchronized (environments) {
            environments.clear();
            environments.putAll(loaded);
        }
        activeEnvironment = active;
    }

    /**
     * 以加载结果为保存基准：未修改时保存不写盘，不会重新生成被删除的文件或覆盖推迟重新加载的外部修改。
     */
    private static void rememberLoaded(String filePath) {
        if (filePath == null) {
            return;
        }
        try {
            List<Environment> envList;
            synchronized (environments) {
                envList = new ArrayList<>(environments.values());
            }
            SafeFileUtil.rememberLoaded(Path.of(filePath), JSONUtil.toJsonPrettyStr(envList));
        } catch (Exception e) {
            log.debug("记录环境变量加载基准失败", e);
        }
    }

    /**
     * 加载所有环境变量
     */
    public static void loadEnvironments() {
        if (currentDataFilePath != null) {
            loadEnvironmentsFromPath(currentDataFilePath);
        } else {
            Workspace currentWorkspace = WorkspaceService.getInstance().getCurrentWorkspace();
            String filePath = SystemUtil.getEnvPathForWorkspace(currentWorkspace);
            loadEnvironmentsFromPath(filePath);
        }
    }

    /**
     * 保存所有环境变量
     */
    public static void saveEnvironments() {
        try {
            String filePath;
            if (currentDataFilePath != null) {
                filePath = currentDataFilePath;
            } else {
                Workspace currentWorkspace = WorkspaceService.getInstance().getCurrentWorkspace();
                filePath = SystemUtil.getEnvPathForWorkspace(currentWorkspace);
            }

            List<Environment> envList;
            synchronized (environments) {
                envList = new ArrayList<>(environments.values());
            }
            SafeFileUtil.writeIfChanged(Path.of(filePath), JSONUtil.toJsonPrettyStr(envList));
            log.debug("环境变量已保存到: {}", filePath);
        } catch (Exception e) {
            log.error("保存环境变量失败", e);
        }
    }

    /**
     * 添加或更新环境
     */
    public static void saveEnvironment(Environment environment) {
        // 如果环境没有 ID，自动生成一个
        if (environment.getId() == null) {
            environment.setId("env-" + IdUtil.simpleUUID());
        }
        environments.put(environment.getId(), environment);
        saveEnvironments();
    }

    public static boolean isManagedEnvironment(Environment environment) {
        if (environment == null || environment.getId() == null) {
            return false;
        }
        return environments.get(environment.getId()) == environment;
    }

    /**
     * 删除环境
     */
    public static void deleteEnvironment(String id) {
        Environment env = environments.remove(id);
        if (env != null && env.isActive() && !environments.isEmpty()) {
            // 如果删除的是当前激活的环境，激活第一个环境
            Environment firstEnv = environments.values().iterator().next();
            firstEnv.setActive(true);
            activeEnvironment = firstEnv;
        }
        // if environments is empty after removal, set activeEnvironment to null
        if (environments.isEmpty()) {
            activeEnvironment = null;
        }
        saveEnvironments();
    }

    /**
     * 获取所有环境
     */
    public static List<Environment> getAllEnvironments() {
        return new ArrayList<>(environments.values());
    }

    /**
     * 设置激活的环境
     */
    public static void setActiveEnvironment(String id) {
        if (activeEnvironment != null) {
            activeEnvironment.setActive(false);
        }

        Environment env = environments.get(id);
        if (env != null) {
            env.setActive(true);
            activeEnvironment = env;
            saveEnvironments();
        }
    }

    /**
     * 根据指定的id顺序重排environments并持久化
     */
    public static void saveEnvironmentOrder(List<String> idOrder) {
        synchronized (environments) {
            LinkedHashMap<String, Environment> newOrder = new LinkedHashMap<>();
            for (String id : idOrder) {
                Environment env = environments.get(id);
                if (env != null) {
                    newOrder.put(id, env);
                }
            }
            // 补充未在idOrder中的环境（防止遗漏）
            for (Map.Entry<String, Environment> entry : environments.entrySet()) {
                if (!newOrder.containsKey(entry.getKey())) {
                    newOrder.put(entry.getKey(), entry.getValue());
                }
            }
            environments.clear();
            environments.putAll(newOrder);
            saveEnvironments();
        }
    }
}
