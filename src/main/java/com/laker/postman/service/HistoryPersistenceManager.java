package com.laker.postman.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.panel.topmenu.setting.SettingManager;
import com.laker.postman.model.HttpResponse;
import com.laker.postman.model.PreparedRequest;
import com.laker.postman.model.RequestHistoryItem;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 历史记录持久化管理器
 */
@Slf4j
public class HistoryPersistenceManager {
    private static final Path HISTORY_FILE = SystemUtil.EASY_POSTMAN_HOME.resolve("request_history.json");

    private static HistoryPersistenceManager instance;
    private final List<RequestHistoryItem> historyItems = new CopyOnWriteArrayList<>();

    private HistoryPersistenceManager() {
        ensureHistoryDirExists();
        loadHistory();
    }

    public static synchronized HistoryPersistenceManager getInstance() {
        if (instance == null) {
            instance = new HistoryPersistenceManager();
        }
        return instance;
    }

    private void ensureHistoryDirExists() {
        try {
            Path historyDir = SystemUtil.EASY_POSTMAN_HOME;
            if (!Files.exists(historyDir)) {
                Files.createDirectories(historyDir);
            }
        } catch (IOException e) {
            log.error("Failed to create history directory: {}", e.getMessage());
        }
    }

    /**
     * 添加历史记录
     */
    public void addHistory(PreparedRequest request, HttpResponse response, long requestTime) {
        RequestHistoryItem item = new RequestHistoryItem(request, response, requestTime);
        historyItems.add(0, item); // 添加到开头

        // 限制历史记录数量
        int maxCount = SettingManager.getMaxHistoryCount();
        while (historyItems.size() > maxCount) {
            historyItems.remove(historyItems.size() - 1);
        }

        // 异步保存
        saveHistoryAsync();
    }

    /**
     * 获取所有历史记录
     */
    public List<RequestHistoryItem> getHistory() {
        return new ArrayList<>(historyItems);
    }

    /**
     * 清空历史记录
     */
    public void clearHistory() {
        historyItems.clear();
        saveHistoryAsync();
    }

    /**
     * 同步保存历史记录
     */
    public void saveHistory() {
        try {
            // 只保存有限的历史记录
            int maxCount = SettingManager.getMaxHistoryCount();
            JSONArray jsonArray = new JSONArray();

            int count = Math.min(historyItems.size(), maxCount);
            for (int i = 0; i < count; i++) {
                RequestHistoryItem item = historyItems.get(i);
                JSONObject jsonItem = convertToJson(item);
                jsonArray.add(jsonItem);
            }

            // 写入文件
            String jsonString = JSONUtil.toJsonPrettyStr(jsonArray);
            Files.writeString(HISTORY_FILE, jsonString, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to save history: {}", e.getMessage());
        }
    }

    /**
     * 异步保存历史记录
     */
    private void saveHistoryAsync() {
        Thread saveThread = new Thread(this::saveHistory);
        saveThread.setDaemon(true);
        saveThread.start();
    }

    /**
     * 加载历史记录
     */
    private void loadHistory() {
        File file = HISTORY_FILE.toFile();
        if (!file.exists()) {
            return;
        }

        try {
            String jsonString = Files.readString(HISTORY_FILE, StandardCharsets.UTF_8);
            if (jsonString.trim().isEmpty()) {
                return;
            }

            JSONArray jsonArray = JSONUtil.parseArray(jsonString);
            historyItems.clear();

            for (int i = 0; i < jsonArray.size(); i++) {
                try {
                    JSONObject jsonItem = jsonArray.getJSONObject(i);
                    RequestHistoryItem item = convertFromJson(jsonItem);
                    historyItems.add(item);
                } catch (Exception e) {
                    // 忽略无法恢复的历史记录项
                    log.error("Failed to restore history item: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to load history: {}", e.getMessage());
            // 如果加载失败，删除损坏的文件
            boolean deleted = file.delete();
            if (!deleted) {
                log.error("Failed to delete corrupted history file: {}", file.getPath());
            }
        } catch (Exception e) {
            log.error("Failed to parse history JSON: {}", e.getMessage());
            // JSON 解析失败，删除损坏的文件
            boolean deleted = file.delete();
            if (!deleted) {
                log.error("Failed to delete corrupted history file: {}", file.getPath());
            }
        }
    }

    /**
     * 将 RequestHistoryItem 转换为 JSON 对象
     */
    private JSONObject convertToJson(RequestHistoryItem item) {
        JSONObject jsonItem = new JSONObject();

        // 基本信息
        jsonItem.set("method", item.method);
        jsonItem.set("url", item.url);
        jsonItem.set("responseCode", item.responseCode);
        jsonItem.set("requestTime", item.requestTime); // 新增请求时间

        // 请求信息
        JSONObject requestJson = new JSONObject();
        requestJson.set("method", item.request.method);
        requestJson.set("url", item.request.url);
        // 请求体 - 优先保存实际发送的okHttpRequestBody
        String requestBody = "";
        if (item.request.okHttpRequestBody != null && !item.request.okHttpRequestBody.isEmpty()) {
            requestBody = item.request.okHttpRequestBody;
        } else if (item.request.body != null) {
            requestBody = item.request.body;
        }
        requestJson.set("body", requestBody);
        requestJson.set("id", item.request.id);
        requestJson.set("followRedirects", item.request.followRedirects);
        requestJson.set("logEvent", item.request.logEvent);
        requestJson.set("isMultipart", item.request.isMultipart);

        // 请求头 - 优先保存实际发送的okHttpHeaders
        JSONObject requestHeaders = new JSONObject();
        if (item.request.okHttpHeaders != null && item.request.okHttpHeaders.size() > 0) {
            // 使用实际发送的OkHttp Headers
            for (int i = 0; i < item.request.okHttpHeaders.size(); i++) {
                String name = item.request.okHttpHeaders.name(i);
                String value = item.request.okHttpHeaders.value(i);
                requestHeaders.set(name, value);
            }
        } else if (item.request.headers != null) {
            // 回退到普通headers
            requestHeaders.putAll(item.request.headers);
        }
        requestJson.set("headers", requestHeaders);

        // 表单数据
        if (item.request.formData != null) {
            JSONObject formData = new JSONObject();
            formData.putAll(item.request.formData);
            requestJson.set("formData", formData);
        }

        // 表单文件
        if (item.request.formFiles != null) {
            JSONObject formFiles = new JSONObject();
            formFiles.putAll(item.request.formFiles);
            requestJson.set("formFiles", formFiles);
        }

        // URL编码数据
        if (item.request.urlencoded != null) {
            JSONObject urlencoded = new JSONObject();
            urlencoded.putAll(item.request.urlencoded);
            requestJson.set("urlencoded", urlencoded);
        }

        jsonItem.set("request", requestJson);

        // 响应信息
        JSONObject responseJson = new JSONObject();
        responseJson.set("code", item.response.code);
        responseJson.set("body", item.response.body != null ? item.response.body : "");
        responseJson.set("costMs", item.response.costMs);
        responseJson.set("threadName", item.response.threadName);
        responseJson.set("filePath", item.response.filePath);
        responseJson.set("fileName", item.response.fileName);
        responseJson.set("protocol", item.response.protocol);
        responseJson.set("idleConnectionCount", item.response.idleConnectionCount);
        responseJson.set("connectionCount", item.response.connectionCount);
        responseJson.set("bodySize", item.response.bodySize);
        responseJson.set("headersSize", item.response.headersSize);
        responseJson.set("isSse", item.response.isSse);

        // 响应头
        JSONObject responseHeaders = new JSONObject();
        if (item.response.headers != null) {
            for (java.util.Map.Entry<String, java.util.List<String>> entry : item.response.headers.entrySet()) {
                String key = entry.getKey();
                java.util.List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    responseHeaders.set(key, String.join(", ", values));
                }
            }
        }
        responseJson.set("headers", responseHeaders);

        // 事件信息
        if (item.response.httpEventInfo != null) {
            JSONObject eventInfo = new JSONObject();
            eventInfo.set("localAddress", item.response.httpEventInfo.getLocalAddress());
            eventInfo.set("remoteAddress", item.response.httpEventInfo.getRemoteAddress());
            eventInfo.set("queueStart", item.response.httpEventInfo.getQueueStart());
            eventInfo.set("callStart", item.response.httpEventInfo.getCallStart());
            eventInfo.set("proxySelectStart", item.response.httpEventInfo.getProxySelectStart());
            eventInfo.set("proxySelectEnd", item.response.httpEventInfo.getProxySelectEnd());
            eventInfo.set("dnsStart", item.response.httpEventInfo.getDnsStart());
            eventInfo.set("dnsEnd", item.response.httpEventInfo.getDnsEnd());
            eventInfo.set("connectStart", item.response.httpEventInfo.getConnectStart());
            eventInfo.set("secureConnectStart", item.response.httpEventInfo.getSecureConnectStart());
            eventInfo.set("secureConnectEnd", item.response.httpEventInfo.getSecureConnectEnd());
            eventInfo.set("connectEnd", item.response.httpEventInfo.getConnectEnd());
            eventInfo.set("connectionAcquired", item.response.httpEventInfo.getConnectionAcquired());
            eventInfo.set("requestHeadersStart", item.response.httpEventInfo.getRequestHeadersStart());
            eventInfo.set("requestHeadersEnd", item.response.httpEventInfo.getRequestHeadersEnd());
            eventInfo.set("requestBodyStart", item.response.httpEventInfo.getRequestBodyStart());
            eventInfo.set("requestBodyEnd", item.response.httpEventInfo.getRequestBodyEnd());
            eventInfo.set("responseHeadersStart", item.response.httpEventInfo.getResponseHeadersStart());
            eventInfo.set("responseHeadersEnd", item.response.httpEventInfo.getResponseHeadersEnd());
            eventInfo.set("responseBodyStart", item.response.httpEventInfo.getResponseBodyStart());
            eventInfo.set("responseBodyEnd", item.response.httpEventInfo.getResponseBodyEnd());
            eventInfo.set("connectionReleased", item.response.httpEventInfo.getConnectionReleased());
            eventInfo.set("callEnd", item.response.httpEventInfo.getCallEnd());
            eventInfo.set("callFailed", item.response.httpEventInfo.getCallFailed());
            eventInfo.set("canceled", item.response.httpEventInfo.getCanceled());
            eventInfo.set("queueingCost", item.response.httpEventInfo.getQueueingCost());
            eventInfo.set("stalledCost", item.response.httpEventInfo.getStalledCost());
            eventInfo.set("protocol", item.response.httpEventInfo.getProtocol() != null ? item.response.httpEventInfo.getProtocol().toString() : null);
            eventInfo.set("tlsVersion", item.response.httpEventInfo.getTlsVersion());
            eventInfo.set("errorMessage", item.response.httpEventInfo.getErrorMessage());
            eventInfo.set("threadName", item.response.httpEventInfo.getThreadName());
            responseJson.set("httpEventInfo", eventInfo);
        }

        jsonItem.set("response", responseJson);

        return jsonItem;
    }

    /**
     * 从 JSON 对象转换为 RequestHistoryItem
     */
    private RequestHistoryItem convertFromJson(JSONObject jsonItem) {
        // 重建 PreparedRequest
        PreparedRequest request = new PreparedRequest();
        JSONObject requestJson = jsonItem.getJSONObject("request");
        request.method = requestJson.getStr("method");
        request.url = requestJson.getStr("url");
        request.body = requestJson.getStr("body");
        // 同时设置 okHttpRequestBody 供 HttpHtmlRenderer 使用
        request.okHttpRequestBody = request.body;
        request.id = requestJson.getStr("id");
        request.followRedirects = requestJson.getBool("followRedirects", true);
        request.logEvent = requestJson.getBool("logEvent", false);
        request.isMultipart = requestJson.getBool("isMultipart", false);

        // 重建请求头 - 同时设置 headers 和 okHttpHeaders
        request.headers = new java.util.HashMap<>();
        JSONObject requestHeaders = requestJson.getJSONObject("headers");
        if (requestHeaders != null) {
            for (String key : requestHeaders.keySet()) {
                request.headers.put(key, requestHeaders.getStr(key));
            }

            // 同时构建 okHttpHeaders 供 HttpHtmlRenderer 使用
            if (!request.headers.isEmpty()) {
                okhttp3.Headers.Builder headersBuilder = new okhttp3.Headers.Builder();
                for (java.util.Map.Entry<String, String> entry : request.headers.entrySet()) {
                    try {
                        headersBuilder.add(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        // 忽略无效的头信息
                    }
                }
                request.okHttpHeaders = headersBuilder.build();
            }
        }

        // 重建表单数据
        JSONObject formData = requestJson.getJSONObject("formData");
        if (formData != null) {
            request.formData = new java.util.HashMap<>();
            for (String key : formData.keySet()) {
                request.formData.put(key, formData.getStr(key));
            }
        }

        // 重建表单文件
        JSONObject formFiles = requestJson.getJSONObject("formFiles");
        if (formFiles != null) {
            request.formFiles = new java.util.HashMap<>();
            for (String key : formFiles.keySet()) {
                request.formFiles.put(key, formFiles.getStr(key));
            }
        }

        // 重建URL编码数据
        JSONObject urlencoded = requestJson.getJSONObject("urlencoded");
        if (urlencoded != null) {
            request.urlencoded = new java.util.HashMap<>();
            for (String key : urlencoded.keySet()) {
                request.urlencoded.put(key, urlencoded.getStr(key));
            }
        }

        // 重建 HttpResponse
        HttpResponse response = new HttpResponse();
        JSONObject responseJson = jsonItem.getJSONObject("response");
        response.code = responseJson.getInt("code");
        response.body = responseJson.getStr("body");
        response.costMs = responseJson.getLong("costMs", 0L);
        response.threadName = responseJson.getStr("threadName");
        response.filePath = responseJson.getStr("filePath");
        response.fileName = responseJson.getStr("fileName");
        response.protocol = responseJson.getStr("protocol");
        response.idleConnectionCount = responseJson.getInt("idleConnectionCount", 0);
        response.connectionCount = responseJson.getInt("connectionCount", 0);
        response.bodySize = responseJson.getInt("bodySize", 0);
        response.headersSize = responseJson.getInt("headersSize", 0);
        response.isSse = responseJson.getBool("isSse", false);

        // 重建响应头
        response.headers = new LinkedHashMap<>();
        JSONObject responseHeaders = responseJson.getJSONObject("headers");
        if (responseHeaders != null) {
            for (String key : responseHeaders.keySet()) {
                String value = responseHeaders.getStr(key);
                List<String> valueList = new ArrayList<>();
                // 如果值包含逗号，分割为多个值
                String[] values = value.split(", ");
                for (String v : values) {
                    valueList.add(v.trim());
                }
                response.headers.put(key, valueList);
            }
        }

        // 重建事件信息
        JSONObject eventInfoJson = responseJson.getJSONObject("httpEventInfo");
        if (eventInfoJson != null) {
            response.httpEventInfo = new com.laker.postman.model.HttpEventInfo();
            response.httpEventInfo.setLocalAddress(eventInfoJson.getStr("localAddress"));
            response.httpEventInfo.setRemoteAddress(eventInfoJson.getStr("remoteAddress"));
            response.httpEventInfo.setQueueStart(eventInfoJson.getLong("queueStart", 0L));
            response.httpEventInfo.setCallStart(eventInfoJson.getLong("callStart", 0L));
            response.httpEventInfo.setProxySelectStart(eventInfoJson.getLong("proxySelectStart", 0L));
            response.httpEventInfo.setProxySelectEnd(eventInfoJson.getLong("proxySelectEnd", 0L));
            response.httpEventInfo.setDnsStart(eventInfoJson.getLong("dnsStart", 0L));
            response.httpEventInfo.setDnsEnd(eventInfoJson.getLong("dnsEnd", 0L));
            response.httpEventInfo.setConnectStart(eventInfoJson.getLong("connectStart", 0L));
            response.httpEventInfo.setSecureConnectStart(eventInfoJson.getLong("secureConnectStart", 0L));
            response.httpEventInfo.setSecureConnectEnd(eventInfoJson.getLong("secureConnectEnd", 0L));
            response.httpEventInfo.setConnectEnd(eventInfoJson.getLong("connectEnd", 0L));
            response.httpEventInfo.setConnectionAcquired(eventInfoJson.getLong("connectionAcquired", 0L));
            response.httpEventInfo.setRequestHeadersStart(eventInfoJson.getLong("requestHeadersStart", 0L));
            response.httpEventInfo.setRequestHeadersEnd(eventInfoJson.getLong("requestHeadersEnd", 0L));
            response.httpEventInfo.setRequestBodyStart(eventInfoJson.getLong("requestBodyStart", 0L));
            response.httpEventInfo.setRequestBodyEnd(eventInfoJson.getLong("requestBodyEnd", 0L));
            response.httpEventInfo.setResponseHeadersStart(eventInfoJson.getLong("responseHeadersStart", 0L));
            response.httpEventInfo.setResponseHeadersEnd(eventInfoJson.getLong("responseHeadersEnd", 0L));
            response.httpEventInfo.setResponseBodyStart(eventInfoJson.getLong("responseBodyStart", 0L));
            response.httpEventInfo.setResponseBodyEnd(eventInfoJson.getLong("responseBodyEnd", 0L));
            response.httpEventInfo.setConnectionReleased(eventInfoJson.getLong("connectionReleased", 0L));
            response.httpEventInfo.setCallEnd(eventInfoJson.getLong("callEnd", 0L));
            response.httpEventInfo.setCallFailed(eventInfoJson.getLong("callFailed", 0L));
            response.httpEventInfo.setCanceled(eventInfoJson.getLong("canceled", 0L));
            response.httpEventInfo.setQueueingCost(eventInfoJson.getLong("queueingCost", 0L));
            response.httpEventInfo.setStalledCost(eventInfoJson.getLong("stalledCost", 0L));

            // 处理协议类型
            String protocolStr = eventInfoJson.getStr("protocol");
            if (protocolStr != null && !protocolStr.isEmpty()) {
                try {
                    response.httpEventInfo.setProtocol(okhttp3.Protocol.valueOf(protocolStr));
                } catch (IllegalArgumentException e) {
                    // 忽略无法解析的协议类型
                }
            }

            response.httpEventInfo.setTlsVersion(eventInfoJson.getStr("tlsVersion"));
            response.httpEventInfo.setErrorMessage(eventInfoJson.getStr("errorMessage"));
            response.httpEventInfo.setThreadName(eventInfoJson.getStr("threadName"));
        }

        // 读取请求时间
        long requestTime = jsonItem.getLong("requestTime", System.currentTimeMillis());

        return new RequestHistoryItem(request, response, requestTime);
    }
}
