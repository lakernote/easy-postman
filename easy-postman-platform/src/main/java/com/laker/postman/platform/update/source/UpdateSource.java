package com.laker.postman.platform.update.source;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

/**
 * 更新源接口 - 定义统一的版本检查和发布信息获取规范
 *
 * <p>不同的更新源（GitHub、Gitee 等）需要实现此接口，提供统一的访问方式。</p>
 */
public interface UpdateSource {

    /** 发布信息中标记来源的内部字段。 */
    String SOURCE_METADATA_KEY = "_source";

    /**
     * 获取更新源的名称
     *
     * @return 更新源名称（如 "GitHub", "Gitee"）
     */
    String getName();

    /**
     * 获取最新版本的 API URL
     *
     * @return API 地址
     */
    String getApiUrl();

    /**
     * 获取按发布时间从新到旧排列的所有版本 API URL
     *
     * @return 所有版本的 API 地址
     */
    String getAllReleasesApiUrl();

    /**
     * 获取网页版发布页面 URL
     *
     * @return 网页版发布页面地址
     */
    String getWebUrl();

    /**
     * 测试更新源的连接速度
     *
     * @return 响应时间（毫秒），如果连接失败返回 Long.MAX_VALUE
     */
    long testConnectionSpeed();

    /**
     * 获取最新版本发布信息
     *
     * @return 发布信息的 JSON 对象，如果获取失败返回 null
     */
    JSONObject fetchLatestReleaseInfo();

    /**
     * 按发布标签获取单个版本信息。
     *
     * <p>不支持按标签查询的自定义更新源可以保留默认实现。</p>
     *
     * @param tagName 完整发布标签，例如 {@code v6.3.1}
     * @return 对应发布信息；标签不存在、请求失败或当前更新源不支持时返回 {@code null}
     */
    default JSONObject fetchReleaseByTag(String tagName) {
        return null;
    }

    /**
     * 获取所有版本发布信息
     *
     * @param limit 限制返回的版本数量，0 表示不限制
     * @return 发布信息的 JSON 数组，如果获取失败返回 null
     */
    JSONArray fetchAllReleases(int limit);
}
