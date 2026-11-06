package com.laker.postman.platform.update.source;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * 抽象更新源基类 - 提供通用的 HTTP 连接和响应处理逻辑
 *
 * <p>子类只需实现 getApiUrl()、getAllReleasesApiUrl()、getWebUrl() 和 getName() 方法即可。</p>
 */
@Slf4j
public abstract class AbstractUpdateSource implements UpdateSource {

    /** 防止插件发布挤占首页，同时限制异常更新源带来的额外请求和等待时间。 */
    private static final int MAX_RELEASE_PAGES = 2;

    /** 连接超时时间（毫秒） */
    protected static final int CONNECTION_TIMEOUT = 2500;

    /** 读取超时时间（毫秒） */
    protected static final int READ_TIMEOUT = 2500;

    /** 获取发布信息时的连接超时时间（毫秒） */
    protected static final int FETCH_CONNECTION_TIMEOUT = 10000;

    /** 获取发布信息时的读取超时时间（毫秒） */
    protected static final int FETCH_READ_TIMEOUT = 10000;

    @Override
    public long testConnectionSpeed() {
        long startTime = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(getApiUrl());
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", buildUserAgent());
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");

            int code = conn.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            if (code == 200 || code == 301 || code == 302) {
                log.debug("{} connection test successful: {} ms (HTTP {})", getName(), responseTime, code);
                return responseTime;
            } else {
                log.debug("{} connection test failed with code: {}", getName(), code);
                return Long.MAX_VALUE;
            }
        } catch (java.net.SocketTimeoutException e) {
            long failedTime = System.currentTimeMillis() - startTime;
            log.debug("{} connection timeout after {} ms", getName(), failedTime);
            return Long.MAX_VALUE;
        } catch (Exception e) {
            long failedTime = System.currentTimeMillis() - startTime;
            log.debug("{} connection test failed after {} ms: {}", getName(), failedTime, e.getMessage());
            return Long.MAX_VALUE;
        } finally {
            closeConnection(conn);
        }
    }

    @Override
    public JSONObject fetchLatestReleaseInfo() {
        JSONArray releases = fetchAllReleases(1);
        if (releases == null || releases.isEmpty()) {
            log.warn("No stable app releases found from {}", getName());
            return null;
        }

        JSONObject releaseInfo = releases.getJSONObject(0);
        releaseInfo.set(SOURCE_METADATA_KEY, getName());
        log.info("Selected latest stable app release from {}", getName());
        return releaseInfo;
    }

    @Override
    public JSONObject fetchReleaseByTag(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return null;
        }

        HttpURLConnection conn = null;
        try {
            conn = openFetchConnection(new URL(buildReleaseByTagApiUrl(tagName)));
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                JSONObject releaseInfo = new JSONObject(readResponse(conn));
                releaseInfo.set(SOURCE_METADATA_KEY, getName());
                log.info("Successfully fetched release {} from {}", tagName, getName());
                return releaseInfo;
            }

            String errorResponse = readErrorResponse(conn);
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                log.info("Release {} is not available from {}", tagName, getName());
            } else {
                log.warn("Failed to fetch release {} from {}, HTTP code: {}, response: {}",
                        tagName, getName(), code, errorResponse);
            }
            return null;
        } catch (Exception e) {
            log.debug("Error fetching release {} from {}: {}", tagName, getName(), e.getMessage());
            return null;
        } finally {
            closeConnection(conn);
        }
    }

    @Override
    public JSONArray fetchAllReleases(int limit) {
        int requestLimit = expandedReleaseFetchLimit(limit);
        JSONArray allReleases = new JSONArray();

        for (int page = 1; page <= MAX_RELEASE_PAGES; page++) {
            JSONArray pageReleases = fetchRawReleases(requestLimit, page);
            if (pageReleases == null) {
                return null;
            }

            for (Object release : pageReleases) {
                allReleases.add(release);
            }

            JSONArray filtered = AppReleaseSelector.selectStableAppReleases(allReleases, limit);
            if (limit > 0 && filtered.size() >= limit) {
                log.info("Selected {} stable app releases from {} after {} page(s)",
                        filtered.size(), getName(), page);
                return filtered;
            }

            if (pageReleases.size() < requestLimit) {
                log.info("Selected {} stable app releases from {} after exhausting {} page(s)",
                        filtered.size(), getName(), page);
                return filtered;
            }
        }

        JSONArray filtered = AppReleaseSelector.selectStableAppReleases(allReleases, limit);
        log.warn("Stopped fetching releases from {} after {} pages; selected {} stable app releases",
                getName(), MAX_RELEASE_PAGES, filtered.size());
        return filtered;
    }

    private JSONArray fetchRawReleases(int limit, int page) {
        HttpURLConnection conn = null;
        try {
            String url = getAllReleasesApiUrl();
            if (limit > 0) {
                url += (url.contains("?") ? "&" : "?") + "per_page=" + limit;
            }
            url += (url.contains("?") ? "&" : "?") + "page=" + page;

            URL apiUrl = new URL(url);
            conn = openFetchConnection(apiUrl);

            int code = conn.getResponseCode();
            if (code == 200) {
                String json = readResponse(conn);
                JSONArray releases = new JSONArray(json);
                log.info("Successfully fetched {} raw releases from {} page {}",
                        releases.size(), getName(), page);
                return releases;
            } else {
                String errorResponse = readErrorResponse(conn);
                log.warn("Failed to fetch releases from {}, HTTP code: {}, response: {}",
                        getName(), code, errorResponse);
                return null;
            }
        } catch (Exception e) {
            log.debug("Error fetching releases from {}: {}", getName(), e.getMessage());
            return null;
        } finally {
            closeConnection(conn);
        }
    }

    private int expandedReleaseFetchLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return 100;
        }
        return Math.min(100, Math.max(requestedLimit * 5, requestedLimit + 20));
    }

    /**
     * 构造按标签查询单个发布的 API 地址。GitHub 与 Gitee 均采用
     * {@code /releases/tags/{tag}} 路径；查询参数不属于基础路径，需要先移除。
     */
    protected String buildReleaseByTagApiUrl(String tagName) {
        String releasesUrl = getAllReleasesApiUrl();
        int queryStart = releasesUrl.indexOf('?');
        if (queryStart >= 0) {
            releasesUrl = releasesUrl.substring(0, queryStart);
        }
        if (releasesUrl.endsWith("/")) {
            releasesUrl = releasesUrl.substring(0, releasesUrl.length() - 1);
        }
        String encodedTag = URLEncoder.encode(tagName, StandardCharsets.UTF_8).replace("+", "%20");
        return releasesUrl + "/tags/" + encodedTag;
    }

    private HttpURLConnection openFetchConnection(URL apiUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
        conn.setConnectTimeout(FETCH_CONNECTION_TIMEOUT);
        conn.setReadTimeout(FETCH_READ_TIMEOUT);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", buildUserAgent());
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        conn.setRequestProperty("Cache-Control", "no-cache");
        return conn;
    }

    /**
     * 读取成功响应
     */
    protected String readResponse(HttpURLConnection conn) throws Exception {
        try (InputStream is = conn.getInputStream();
             Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").next();
        }
    }

    /**
     * 读取错误响应
     */
    protected String readErrorResponse(HttpURLConnection conn) {
        try (InputStream errorStream = conn.getErrorStream()) {
            if (errorStream != null) {
                try (Scanner scanner = new Scanner(errorStream, StandardCharsets.UTF_8)) {
                    if (scanner.hasNext()) {
                        return scanner.useDelimiter("\\A").next();
                    }
                }
            }
        } catch (Exception ignored) {
            // 忽略读取错误响应的异常
        }
        return "";
    }

    /**
     * 构建 User-Agent 字符串
     */
    protected String buildUserAgent() {
        return "Mozilla/5.0 (compatible; EasyPostman/" + SystemUtil.getCurrentVersion() + ")";
    }

    /**
     * 关闭连接
     */
    protected void closeConnection(HttpURLConnection conn) {
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception ignored) {
                // 忽略断开连接时的异常
            }
        }
    }
}
