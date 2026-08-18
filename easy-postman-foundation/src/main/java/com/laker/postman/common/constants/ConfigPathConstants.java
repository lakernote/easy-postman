package com.laker.postman.common.constants;

import com.laker.postman.model.Workspace;
import com.laker.postman.util.AppRuntimeLayout;
import com.laker.postman.util.SystemUtil;
import lombok.experimental.UtilityClass;

import java.io.File;

/**
 * 配置文件路径常量统一管理
 * 所有配置文件路径都在这里定义，便于维护
 */
@UtilityClass
public class ConfigPathConstants {

    /**
     * 获取数据根目录
     * Portable 模式：程序所在目录/data/
     * 普通模式：用户主目录/EasyPostman/
     */
    private static String getDataRootPath() {
        return SystemUtil.getEasyPostmanPath();
    }

    // ==================== 配置文件路径常量 ====================

    /**
     * 客户端证书配置文件
     */
    public static final String CLIENT_CERTIFICATES = getDataRootPath() + "client_certificates.json";

    /**
     * 默认工作区目录（与其他工作区平级，根目录不参与 git）
     */
    public static final String DEFAULT_WORKSPACE_DIR = getDataRootPath() + "workspaces" + File.separator + "default" + File.separator;

    /**
     * 集合配置文件（默认工作区）
     */
    public static final String COLLECTIONS = DEFAULT_WORKSPACE_DIR + "collections.json";

    /**
     * 应用设置文件
     */
    public static final String EASY_POSTMAN_SETTINGS = getDataRootPath() + "easy_postman_settings.properties";

    /**
     * 环境变量配置文件（默认工作区）
     */
    public static final String ENVIRONMENTS = DEFAULT_WORKSPACE_DIR + "environments.json";

    /**
     * 全局变量配置文件（应用级，不跟随工作区切换）
     */
    public static final String GLOBAL_VARIABLES = getDataRootPath() + "global_variables.json";

    /**
     * 功能测试配置文件
     */
    public static final String FUNCTIONAL_CONFIG = getDataRootPath() + "functional_config.json";

    /**
     * 性能测试配置文件
     */
    public static final String PERFORMANCE_CONFIG = getDataRootPath() + "performance_config.json";

    /**
     * Mock Server 配置文件（默认工作区）。
     */
    public static final String MOCK_SERVERS = DEFAULT_WORKSPACE_DIR + "mock_servers.json";

    /**
     * Elasticsearch 连接配置文件
     */
    public static final String ELASTICSEARCH_CONNECTION_PROFILES = getDataRootPath() + "elasticsearch_connection_profiles.json";

    /**
     * InfluxDB 连接配置文件
     */
    public static final String INFLUXDB_CONNECTION_PROFILES = getDataRootPath() + "influxdb_connection_profiles.json";

    /**
     * 请求历史记录文件
     */
    public static final String REQUEST_HISTORY = getDataRootPath() + "request_history.json";

    /**
     * 快捷键配置文件
     */
    public static final String SHORTCUTS = getDataRootPath() + "shortcuts.properties";

    /**
     * 用户设置文件
     */
    public static final String USER_SETTINGS = getDataRootPath() + "user_settings.json";

    /**
     * 工作区配置文件
     */
    public static final String WORKSPACES = getDataRootPath() + "workspaces.json";

    /**
     * 工作区设置文件
     */
    public static final String WORKSPACE_SETTINGS = getDataRootPath() + "workspace_settings.json";

    /**
     * 工作区目录
     */
    public static final String WORKSPACES_DIR = getDataRootPath() + "workspaces" + File.separator;

    /**
     * 已打开的请求文件
     */
    public static final String OPENED_REQUESTS = getDataRootPath() + "opened_requests.json";

    /**
     * 日志目录
     * Portable 模式：程序所在目录/logs/
     * 普通模式：用户主目录/EasyPostman/logs/
     */
    public static final String LOG_DIR = AppRuntimeLayout.logRootDirectory(ConfigPathConstants.class).toString()
            + File.separator;

    // ==================== 工作区相关路径方法 ====================

    /**
     * 获取指定工作区的集合文件路径。
     * {@link com.laker.postman.model.Workspace#getPath()} 保证末尾始终带 {@link java.io.File#separator}，
     * 直接拼接文件名即可。
     */
    public static String getCollectionsPath(Workspace workspace) {
        if (workspace == null) {
            return COLLECTIONS;
        }
        return workspace.getPath() + "collections.json";
    }

    /**
     * 获取指定工作区的环境变量文件路径。
     * {@link com.laker.postman.model.Workspace#getPath()} 保证末尾始终带 {@link java.io.File#separator}，
     * 直接拼接文件名即可。
     */
    public static String getEnvironmentsPath(Workspace workspace) {
        if (workspace == null) {
            return ENVIRONMENTS;
        }
        return workspace.getPath() + "environments.json";
    }

    /**
     * 获取指定工作区的功能测试配置文件路径。
     */
    public static String getFunctionalConfigPath(Workspace workspace) {
        if (workspace == null || workspace.getPath() == null || workspace.getPath().isBlank()) {
            return DEFAULT_WORKSPACE_DIR + "functional_config.json";
        }
        return workspace.getPath() + "functional_config.json";
    }

    /**
     * 获取指定工作区的性能测试配置文件路径。
     */
    public static String getPerformanceConfigPath(Workspace workspace) {
        if (workspace == null || workspace.getPath() == null || workspace.getPath().isBlank()) {
            return DEFAULT_WORKSPACE_DIR + "performance_config.json";
        }
        return workspace.getPath() + "performance_config.json";
    }

    /**
     * 获取指定工作区的 Mock Server 配置文件路径。
     */
    public static String getMockServersPath(Workspace workspace) {
        if (workspace == null || workspace.getPath() == null || workspace.getPath().isBlank()) {
            return MOCK_SERVERS;
        }
        return workspace.getPath() + "mock_servers.json";
    }
}
