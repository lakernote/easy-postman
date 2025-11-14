package com.laker.postman.service.har;

import cn.hutool.core.lang.Pair;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.model.*;
import lombok.extern.slf4j.Slf4j;

import javax.swing.tree.DefaultMutableTreeNode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.laker.postman.panel.collections.right.request.sub.AuthTabPanel.*;

/**
 * HAR (HTTP Archive) 格式解析器
 * 负责解析 HAR 1.2 格式的文件，转换为内部数据结构
 */
@Slf4j
public class HarParser {
    // 常量定义
    private static final String GROUP = "group";
    private static final String REQUEST = "request";

    /**
     * 私有构造函数，防止实例化
     */
    private HarParser() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 解析 HAR JSON 文件，返回根节点
     *
     * @param json HAR JSON 字符串
     * @return 集合根节点，如果解析失败返回 null
     */
    public static DefaultMutableTreeNode parseHar(String json) {
        try {
            JSONObject harRoot = JSONUtil.parseObj(json);
            if (!harRoot.containsKey("log")) {
                log.error("HAR文件格式错误：缺少 log 字段");
                return null;
            }

            JSONObject logObj = harRoot.getJSONObject("log");
            String version = logObj.getStr("version", "");
            if (!"1.2".equals(version)) {
                log.warn("HAR版本不是1.2，当前版本: {}", version);
            }

            // 创建分组节点
            String groupName = "HAR Import " + System.currentTimeMillis();
            RequestGroup collectionGroup = new RequestGroup(groupName);
            DefaultMutableTreeNode collectionNode = new DefaultMutableTreeNode(new Object[]{GROUP, collectionGroup});

            // 解析 entries
            JSONArray entries = logObj.getJSONArray("entries");
            if (entries == null || entries.isEmpty()) {
                log.warn("HAR文件中没有请求条目");
                return collectionNode;
            }

            // 遍历所有条目
            for (Object entryObj : entries) {
                JSONObject entry = (JSONObject) entryObj;
                HttpRequestItem requestItem = parseHarEntry(entry);
                if (requestItem != null) {
                    DefaultMutableTreeNode requestNode = new DefaultMutableTreeNode(new Object[]{REQUEST, requestItem});
                    collectionNode.add(requestNode);
                }
            }

            return collectionNode;
        } catch (Exception e) {
            log.error("解析HAR文件失败", e);
            return null;
        }
    }

    /**
     * 解析单个 HAR entry 为 HttpRequestItem
     */
    private static HttpRequestItem parseHarEntry(JSONObject entry) {
        try {
            HttpRequestItem req = new HttpRequestItem();
            req.setId(UUID.randomUUID().toString());

            JSONObject request = entry.getJSONObject("request");
            if (request == null) {
                log.warn("HAR entry 缺少 request 字段");
                return null;
            }

            // 方法
            req.setMethod(request.getStr("method", "GET"));

            // URL
            String url = request.getStr("url", "");
            if (url.isEmpty()) {
                log.warn("HAR entry 的 URL 为空");
                return null;
            }
            req.setUrl(url);

            // 请求名称（从 comment 或 URL 提取）
            String comment = entry.getStr("comment", "");
            if (!comment.isEmpty()) {
                req.setName(comment);
            } else {
                // 从 URL 提取名称
                try {
                    URI uri = new URI(url);
                    String path = uri.getPath();
                    if (path != null && !path.isEmpty()) {
                        String[] parts = path.split("/");
                        String name = parts[parts.length - 1];
                        if (name.isEmpty() && parts.length > 1) {
                            name = parts[parts.length - 2];
                        }
                        req.setName(name.isEmpty() ? url : name);
                    } else {
                        req.setName(url);
                    }
                } catch (Exception e) {
                    req.setName(url);
                }
            }

            // Headers
            JSONArray headers = request.getJSONArray("headers");
            if (headers != null && !headers.isEmpty()) {
                List<HttpHeader> headersList = new ArrayList<>();
                for (Object h : headers) {
                    JSONObject hObj = (JSONObject) h;
                    String name = hObj.getStr("name", "");
                    String value = hObj.getStr("value", "");
                    // 检查是否是认证头
                    if ("Authorization".equalsIgnoreCase(name)) {
                        if (value.startsWith("Basic ")) {
                            req.setAuthType(AUTH_TYPE_BASIC);
                            Pair<String, String> basicAuth = parseBasicAuth(value);
                            if (basicAuth != null) {
                                req.setAuthUsername(basicAuth.getKey());
                                req.setAuthPassword(basicAuth.getValue());
                            }
                        } else if (value.startsWith("Bearer ")) {
                            req.setAuthType(AUTH_TYPE_BEARER);
                            req.setAuthToken(value.substring(7));
                        } else {
                            req.setAuthType(AUTH_TYPE_NONE);
                            headersList.add(new HttpHeader(true, name, value));
                        }
                    } else {
                        headersList.add(new HttpHeader(true, name, value));
                    }
                }
                req.setHeadersList(headersList);
            }

            // Query String
            JSONArray queryString = request.getJSONArray("queryString");
            if (queryString != null && !queryString.isEmpty()) {
                List<HttpParam> paramsList = new ArrayList<>();
                for (Object q : queryString) {
                    JSONObject qObj = (JSONObject) q;
                    String name = qObj.getStr("name", "");
                    String value = qObj.getStr("value", "");
                    paramsList.add(new HttpParam(true, name, value));
                }
                req.setParamsList(paramsList);
            }

            // Post Data
            JSONObject postData = request.getJSONObject("postData");
            if (postData != null) {
                String mimeType = postData.getStr("mimeType", "");
                String text = postData.getStr("text", "");

                if (mimeType.contains("application/json")) {
                    req.setBody(text);
                    req.setBodyType("raw");
                } else if (mimeType.contains("application/x-www-form-urlencoded")) {
                    // 解析 urlencoded 数据
                    List<HttpFormUrlencoded> urlencodedList = new ArrayList<>();
                    JSONArray params = postData.getJSONArray("params");
                    if (params != null && !params.isEmpty()) {
                        for (Object p : params) {
                            JSONObject pObj = (JSONObject) p;
                            String name = pObj.getStr("name", "");
                            String value = pObj.getStr("value", "");
                            urlencodedList.add(new HttpFormUrlencoded(true, name, value));
                        }
                    } else if (!text.isEmpty()) {
                        // 如果没有 params 数组，尝试从 text 解析
                        String[] pairs = text.split("&");
                        for (String pair : pairs) {
                            String[] kv = pair.split("=", 2);
                            String name = kv.length > 0 ? URLDecoder.decode(kv[0], StandardCharsets.UTF_8) : "";
                            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                            urlencodedList.add(new HttpFormUrlencoded(true, name, value));
                        }
                    }
                    req.setUrlencodedList(urlencodedList);
                } else if (mimeType.contains("multipart/form-data")) {
                    // 解析 multipart/form-data
                    List<HttpFormData> formDataList = new ArrayList<>();
                    JSONArray params = postData.getJSONArray("params");
                    if (params != null && !params.isEmpty()) {
                        for (Object p : params) {
                            JSONObject pObj = (JSONObject) p;
                            String name = pObj.getStr("name", "");
                            String fileName = pObj.getStr("fileName", "");
                            String value = pObj.getStr("value", "");
                            if (!fileName.isEmpty()) {
                                formDataList.add(new HttpFormData(true, name, HttpFormData.TYPE_FILE, fileName));
                            } else {
                                formDataList.add(new HttpFormData(true, name, HttpFormData.TYPE_TEXT, value));
                            }
                        }
                    }
                    req.setFormDataList(formDataList);
                } else if (!text.isEmpty()) {
                    // 其他类型作为 raw body
                    req.setBody(text);
                    req.setBodyType("raw");
                }
            }
            return req;
        } catch (Exception e) {
            log.error("解析HAR entry失败", e);
            return null;
        }
    }

    /**
     * 解析 Basic Auth
     *
     * @param value Basic Auth 值，格式为 "Basic base64(username:password)"
     * @return 包含用户名和密码的 Pair 对象
     */
    private static Pair<String, String> parseBasicAuth(String value) {
        String credentials = value.substring(6).trim();
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(credentials));
        } catch (IllegalArgumentException e) {
            log.error("解析 Basic auth 信息失败 value: {}", value, e);
            return null;
        }
        String[] parts = decoded.split(":", 2);
        if (parts.length == 2) {
            return new Pair<>(parts[0], parts[1]);
        } else {
            log.error("解析 Basic auth 信息失败 value: {}", value);
            return null;
        }
    }
}

