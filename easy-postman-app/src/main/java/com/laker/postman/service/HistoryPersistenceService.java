package com.laker.postman.service;

import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.HttpRouteAttempt;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.history.RequestHistoryItem;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.common.constants.ConfigPathConstants;
import com.laker.postman.ioc.Component;
import com.laker.postman.ioc.PostConstruct;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 历史记录持久化管理器
 */
@Slf4j
@Component
public class HistoryPersistenceService {
    private static final String HISTORY_FILE = ConfigPathConstants.REQUEST_HISTORY;

    // 限制单个响应体保存的最大字符数 (10KB)
    private static final int MAX_BODY_SIZE = 10 * 1024;
    // 限制单个请求体保存的最大字符数 (10KB)
    private static final int MAX_REQUEST_BODY_SIZE = 10 * 1024;
    // 限制文件大小 (50MB)
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final List<RequestHistoryItem> historyItems = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        ensureHistoryDirExists();
        loadHistory();
    }

    private void ensureHistoryDirExists() {
        try {
            Path historyDir = Paths.get(SystemUtil.getEasyPostmanPath());
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
    public RequestHistoryItem addHistory(PreparedRequest request, HttpResponse response, long requestTime) {
        RequestHistoryItem item = new RequestHistoryItem(request, response, requestTime);
        historyItems.add(0, item); // 添加到开头

        // 限制历史记录数量
        int maxCount = SettingManager.getMaxHistoryCount();
        while (historyItems.size() > maxCount) {
            historyItems.remove(historyItems.size() - 1);
        }

        // 异步保存
        saveHistoryAsync();
        return item;
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
     * 删除单条历史记录
     */
    public void removeHistory(RequestHistoryItem item) {
        if (item == null) {
            return;
        }
        if (historyItems.remove(item)) {
            saveHistoryAsync();
        }
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
            String jsonString = JSONUtil.toJsonStr(jsonArray);
            Files.writeString(Paths.get(HISTORY_FILE), jsonString, StandardCharsets.UTF_8);
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
     * 截断过大的body内容
     */
    private String truncateBody(String body, int maxSize) {
        if (body == null) {
            return "";
        }
        if (body.length() <= maxSize) {
            return body;
        }
        // 截断并添加提示信息
        return body.substring(0, maxSize) + "\n\n... [内容过大，已截断。原始大小: " + body.length() + " 字符] ...";
    }

    /**
     * 加载历史记录 - 使用流式读取避免内存溢出
     */
    private void loadHistory() {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) {
            return;
        }

        try {
            // 检查文件大小，如果超过限制则删除文件并重新开始
            long fileSizeInBytes = file.length();

            if (fileSizeInBytes > MAX_FILE_SIZE) {
                log.warn("History file is too large ({} bytes, max: {} bytes), deleting and starting fresh",
                        fileSizeInBytes, MAX_FILE_SIZE);
                deleteHistoryFile(file);
                return;
            }

            // 如果文件为空，直接返回
            if (fileSizeInBytes == 0) {
                return;
            }

            // 使用流式读取，避免一次性加载整个文件到内存
            String jsonString = loadFileContent(file);
            if (jsonString.trim().isEmpty()) {
                return;
            }

            JSONArray jsonArray = JSONUtil.parseArray(jsonString);
            historyItems.clear();

            // 限制加载的历史记录数量
            int maxCount = SettingManager.getMaxHistoryCount();
            int loadCount = Math.min(jsonArray.size(), maxCount);

            for (int i = 0; i < loadCount; i++) {
                try {
                    JSONObject jsonItem = jsonArray.getJSONObject(i);
                    RequestHistoryItem item = convertFromJson(jsonItem);
                    historyItems.add(item);
                } catch (Exception e) {
                    // 忽略无法恢复的历史记录项
                    log.warn("Failed to restore history item at index {}: {}", i, e.getMessage());
                }
            }

            log.info("Successfully loaded {} history items from file", historyItems.size());

        } catch (OutOfMemoryError e) {
            log.error("Out of memory while loading history file, deleting and starting fresh", e);
            // 内存溢出，删除文件并重新开始
            deleteHistoryFile(file);
            historyItems.clear();
        } catch (Exception e) {
            log.error("Failed to load history: {}", e.getMessage(), e);
            // 加载失败，删除损坏的文件
            deleteHistoryFile(file);
            historyItems.clear();
        }
    }

    /**
     * 使用流式方式读取文件内容，更节省内存
     */
    private String loadFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder((int) Math.min(file.length(), MAX_FILE_SIZE));
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            char[] buffer = new char[8192]; // 8KB buffer
            int charsRead;
            long totalCharsRead = 0;
            long maxChars = MAX_FILE_SIZE / 2; // 限制读取字符数，防止超大文件

            while ((charsRead = reader.read(buffer)) != -1) {
                totalCharsRead += charsRead;
                if (totalCharsRead > maxChars) {
                    log.warn("History file content too large, truncating at {} chars", totalCharsRead);
                    break;
                }
                content.append(buffer, 0, charsRead);
            }
        }
        return content.toString();
    }

    /**
     * 删除历史文件
     */
    private void deleteHistoryFile(File file) {
        try {
            Files.delete(file.toPath());
            log.info("Deleted history file: {}", file.getPath());
        } catch (IOException e) {
            log.error("Failed to delete history file: {}", file.getPath(), e);
        }
    }

    /**
     * 将 RequestHistoryItem 转换为 JSON 对象
     */
    private JSONObject convertToJson(RequestHistoryItem item) {
        PreparedRequest request = item.getRequest();
        HttpResponse response = item.getResponse();
        JSONObject jsonItem = new JSONObject();

        // 基本信息
        jsonItem.set("method", item.getMethod());
        jsonItem.set("url", item.getUrl());
        jsonItem.set("responseCode", item.getResponseCode());
        jsonItem.set("requestTime", item.getRequestTime()); // 新增请求时间

        // 请求信息
        JSONObject requestJson = new JSONObject();
        requestJson.set("method", request.method);
        requestJson.set("url", request.url);
        // 请求体 - 保留完整内容，历史记录支持重新打开为可编辑请求时需要精确还原
        String requestBody = "";
        if (request.sentRequestBody != null && !request.sentRequestBody.isEmpty()) {
            requestBody = request.sentRequestBody;
        } else if (request.body != null) {
            requestBody = request.body;
        }
        requestBody = truncateBody(requestBody, MAX_REQUEST_BODY_SIZE);
        requestJson.set("body", requestBody);
        if (!Objects.equals(request.body, requestBody)) {
            requestJson.set("originalBody", truncateBody(request.body, MAX_REQUEST_BODY_SIZE));
        }
        requestJson.set("bodyType", request.bodyType);
        requestJson.set("id", request.id);
        requestJson.set("followRedirects", request.followRedirects);
        requestJson.set("isMultipart", request.isMultipart);
        requestJson.set("cookieJarEnabled", request.cookieJarEnabled);
        requestJson.set("sslVerificationEnabled", request.sslVerificationEnabled);
        requestJson.set("httpVersion", request.httpVersion);
        requestJson.set("requestTimeoutMs", request.requestTimeoutMs);
        requestJson.set("webSocketPingIntervalMs", request.webSocketPingIntervalMs);
        requestJson.set("collectBasicInfo", request.collectBasicInfo);
        requestJson.set("collectEventInfo", request.collectEventInfo);
        requestJson.set("enableNetworkLog", request.enableNetworkLog);
        requestJson.set("prescript", request.prescript);
        requestJson.set("postscript", request.postscript);

        // 请求头 - 优先保存实际发送的sentHeadersList
        JSONObject requestHeaders = new JSONObject();
        if (request.sentHeadersList != null && !request.sentHeadersList.isEmpty()) {
            for (HttpHeader header : request.sentHeadersList) {
                if (header != null && header.getKey() != null && !header.getKey().isBlank()) {
                    requestHeaders.set(header.getKey(), header.getValue());
                }
            }
        }
        requestJson.set("headers", requestHeaders);
        requestJson.set("headersList", convertHeadersListToJson(request.headersList));
        requestJson.set("paramsList", convertParamsListToJson(request.paramsList));
        requestJson.set("formDataList", convertFormDataListToJson(request.formDataList));
        requestJson.set("urlencodedList", convertUrlencodedListToJson(request.urlencodedList));


        jsonItem.set("request", requestJson);

        // 响应信息
        JSONObject responseJson = new JSONObject();
        responseJson.set("code", response.code);
        // 限制响应体大小
        String responseBody = response.body != null ? response.body : "";
        responseBody = truncateBody(responseBody, MAX_BODY_SIZE);
        responseJson.set("body", responseBody);
        responseJson.set("costMs", response.costMs);
        responseJson.set("threadName", response.threadName);
        responseJson.set("filePath", response.filePath);
        responseJson.set("fileName", response.fileName);
        responseJson.set("protocol", response.protocol);
        responseJson.set("idleConnectionCount", response.idleConnectionCount);
        responseJson.set("connectionCount", response.connectionCount);
        responseJson.set("bodySize", response.bodySize);
        responseJson.set("headersSize", response.headersSize);
        responseJson.set("isSse", response.isSse);

        // 响应头
        JSONObject responseHeaders = new JSONObject();
        if (response.headers != null) {
            for (java.util.Map.Entry<String, java.util.List<String>> entry : response.headers.entrySet()) {
                String key = entry.getKey();
                java.util.List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    responseHeaders.set(key, String.join(", ", values));
                }
            }
        }
        responseJson.set("headers", responseHeaders);

        // 事件信息
        if (response.httpEventInfo != null) {
            JSONObject eventInfo = new JSONObject();
            eventInfo.set("localAddress", response.httpEventInfo.getLocalAddress());
            eventInfo.set("remoteAddress", response.httpEventInfo.getRemoteAddress());
            eventInfo.set("queueStart", response.httpEventInfo.getQueueStart());
            eventInfo.set("callStart", response.httpEventInfo.getCallStart());
            eventInfo.set("dispatcherQueueStart", response.httpEventInfo.getDispatcherQueueStart());
            eventInfo.set("dispatcherQueueEnd", response.httpEventInfo.getDispatcherQueueEnd());
            eventInfo.set("proxySelectStart", response.httpEventInfo.getProxySelectStart());
            eventInfo.set("proxySelectEnd", response.httpEventInfo.getProxySelectEnd());
            eventInfo.set("dnsStart", response.httpEventInfo.getDnsStart());
            eventInfo.set("dnsEnd", response.httpEventInfo.getDnsEnd());
            eventInfo.set("dnsHost", response.httpEventInfo.getDnsHost());
            eventInfo.set("dnsAddresses", response.httpEventInfo.getDnsAddresses());
            eventInfo.set("dnsError", response.httpEventInfo.getDnsError());
            eventInfo.set("connectStart", response.httpEventInfo.getConnectStart());
            eventInfo.set("secureConnectStart", response.httpEventInfo.getSecureConnectStart());
            eventInfo.set("secureConnectEnd", response.httpEventInfo.getSecureConnectEnd());
            eventInfo.set("connectEnd", response.httpEventInfo.getConnectEnd());
            eventInfo.set("connectionAcquired", response.httpEventInfo.getConnectionAcquired());
            eventInfo.set("requestHeadersStart", response.httpEventInfo.getRequestHeadersStart());
            eventInfo.set("requestHeadersEnd", response.httpEventInfo.getRequestHeadersEnd());
            eventInfo.set("requestBodyStart", response.httpEventInfo.getRequestBodyStart());
            eventInfo.set("requestBodyEnd", response.httpEventInfo.getRequestBodyEnd());
            eventInfo.set("responseHeadersStart", response.httpEventInfo.getResponseHeadersStart());
            eventInfo.set("responseHeadersEnd", response.httpEventInfo.getResponseHeadersEnd());
            eventInfo.set("responseBodyStart", response.httpEventInfo.getResponseBodyStart());
            eventInfo.set("responseBodyEnd", response.httpEventInfo.getResponseBodyEnd());
            eventInfo.set("connectionReleased", response.httpEventInfo.getConnectionReleased());
            eventInfo.set("callEnd", response.httpEventInfo.getCallEnd());
            eventInfo.set("callFailed", response.httpEventInfo.getCallFailed());
            eventInfo.set("canceled", response.httpEventInfo.getCanceled());
            eventInfo.set("queueingCost", response.httpEventInfo.getQueueingCost());
            eventInfo.set("stalledCost", response.httpEventInfo.getStalledCost());
            eventInfo.set("retryDecisionCount", response.httpEventInfo.getRetryDecisionCount());
            eventInfo.set("retryCount", response.httpEventInfo.getRetryCount());
            eventInfo.set("followUpDecisionCount", response.httpEventInfo.getFollowUpDecisionCount());
            eventInfo.set("followUpCount", response.httpEventInfo.getFollowUpCount());
            eventInfo.set("protocol", response.httpEventInfo.getProtocol());
            JSONArray routeAttempts = new JSONArray();
            for (HttpRouteAttempt attempt : response.httpEventInfo.getRouteAttempts()) {
                JSONObject routeAttempt = new JSONObject();
                routeAttempt.set("address", attempt.address());
                routeAttempt.set("addressFamily", attempt.addressFamily());
                routeAttempt.set("startTime", attempt.startTime());
                routeAttempt.set("endTime", attempt.endTime());
                routeAttempt.set("durationMs", attempt.durationMs());
                routeAttempt.set("connected", attempt.connected());
                routeAttempt.set("canceled", attempt.canceled());
                routeAttempt.set("protocol", attempt.protocol());
                routeAttempt.set("error", attempt.error());
                routeAttempts.add(routeAttempt);
            }
            eventInfo.set("routeAttempts", routeAttempts);
            eventInfo.set("tlsVersion", response.httpEventInfo.getTlsVersion());
            eventInfo.set("errorMessage", response.httpEventInfo.getErrorMessage());
            eventInfo.set("threadName", response.httpEventInfo.getThreadName());
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
        request.body = requestJson.getStr("originalBody", requestJson.getStr("body"));
        request.bodyType = requestJson.getStr("bodyType");
        request.sentRequestBody = requestJson.getStr("body");
        request.id = requestJson.getStr("id");
        request.followRedirects = requestJson.getBool("followRedirects", true);
        request.isMultipart = requestJson.getBool("isMultipart", false);
        request.cookieJarEnabled = requestJson.getBool("cookieJarEnabled", true);
        request.sslVerificationEnabled = requestJson.getBool("sslVerificationEnabled", false);
        request.httpVersion = requestJson.getStr("httpVersion");
        request.requestTimeoutMs = requestJson.getInt("requestTimeoutMs", 0);
        if (requestJson.containsKey("webSocketPingIntervalMs")) {
            request.webSocketPingIntervalMs = requestJson.getInt("webSocketPingIntervalMs");
        }
        request.collectBasicInfo = requestJson.getBool("collectBasicInfo", true);
        request.collectEventInfo = requestJson.getBool("collectEventInfo", true);
        request.enableNetworkLog = requestJson.getBool("enableNetworkLog", false);
        request.prescript = requestJson.getStr("prescript");
        request.postscript = requestJson.getStr("postscript");
        request.headersList = convertHeadersListFromJson(requestJson.getJSONArray("headersList"));
        request.paramsList = convertParamsListFromJson(requestJson.getJSONArray("paramsList"));
        request.formDataList = convertFormDataListFromJson(requestJson.getJSONArray("formDataList"));
        request.urlencodedList = convertUrlencodedListFromJson(requestJson.getJSONArray("urlencodedList"));

        // 重建请求头 - 构建 sentHeadersList 供 HttpHtmlRenderer 使用
        JSONObject requestHeaders = requestJson.getJSONObject("headers");
        if (requestHeaders != null && !requestHeaders.isEmpty()) {
            List<HttpHeader> sentHeaders = new ArrayList<>();
            for (String key : requestHeaders.keySet()) {
                if (key != null && !key.isBlank()) {
                    sentHeaders.add(new HttpHeader(true, key, requestHeaders.getStr(key)));
                }
            }
            request.sentHeadersList = sentHeaders;
        } else if (request.headersList != null && !request.headersList.isEmpty()) {
            List<HttpHeader> sentHeaders = new ArrayList<>();
            for (HttpHeader header : request.headersList) {
                if (header == null || !header.isEnabled() || header.getKey() == null || header.getKey().isBlank()) {
                    continue;
                }
                sentHeaders.add(new HttpHeader(true, header.getKey(), header.getValue(), header.getDescription()));
            }
            request.sentHeadersList = sentHeaders;
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
            response.httpEventInfo = new HttpEventInfo();
            response.httpEventInfo.setLocalAddress(eventInfoJson.getStr("localAddress"));
            response.httpEventInfo.setRemoteAddress(eventInfoJson.getStr("remoteAddress"));
            response.httpEventInfo.setQueueStart(eventInfoJson.getLong("queueStart", 0L));
            response.httpEventInfo.setCallStart(eventInfoJson.getLong("callStart", 0L));
            response.httpEventInfo.setDispatcherQueueStart(eventInfoJson.getLong("dispatcherQueueStart", 0L));
            response.httpEventInfo.setDispatcherQueueEnd(eventInfoJson.getLong("dispatcherQueueEnd", 0L));
            response.httpEventInfo.setProxySelectStart(eventInfoJson.getLong("proxySelectStart", 0L));
            response.httpEventInfo.setProxySelectEnd(eventInfoJson.getLong("proxySelectEnd", 0L));
            response.httpEventInfo.setDnsStart(eventInfoJson.getLong("dnsStart", 0L));
            response.httpEventInfo.setDnsEnd(eventInfoJson.getLong("dnsEnd", 0L));
            response.httpEventInfo.setDnsHost(eventInfoJson.getStr("dnsHost"));
            response.httpEventInfo.replaceDnsAddresses(readStringList(eventInfoJson.getJSONArray("dnsAddresses")));
            response.httpEventInfo.setDnsError(eventInfoJson.getStr("dnsError"));
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
            response.httpEventInfo.setRetryDecisionCount(eventInfoJson.getInt("retryDecisionCount", 0));
            response.httpEventInfo.setRetryCount(eventInfoJson.getInt("retryCount", 0));
            response.httpEventInfo.setFollowUpDecisionCount(eventInfoJson.getInt("followUpDecisionCount", 0));
            response.httpEventInfo.setFollowUpCount(eventInfoJson.getInt("followUpCount", 0));

            JSONArray routeAttempts = eventInfoJson.getJSONArray("routeAttempts");
            if (routeAttempts != null) {
                for (int i = 0; i < routeAttempts.size(); i++) {
                    JSONObject routeAttempt = routeAttempts.getJSONObject(i);
                    response.httpEventInfo.addRouteAttempt(new HttpRouteAttempt(
                            routeAttempt.getStr("address"),
                            routeAttempt.getStr("addressFamily"),
                            routeAttempt.getLong("startTime", 0L),
                            routeAttempt.getLong("endTime", 0L),
                            routeAttempt.getLong("durationMs",
                                    Math.max(0L, routeAttempt.getLong("endTime", 0L)
                                            - routeAttempt.getLong("startTime", 0L))),
                            routeAttempt.getBool("connected", false),
                            routeAttempt.getBool("canceled", false),
                            routeAttempt.getStr("protocol"),
                            routeAttempt.getStr("error")
                    ));
                }
            }

            String protocolStr = eventInfoJson.getStr("protocol");
            if (protocolStr != null && !protocolStr.isEmpty()) {
                response.httpEventInfo.setProtocol(protocolStr);
            }

            response.httpEventInfo.setTlsVersion(eventInfoJson.getStr("tlsVersion"));
            response.httpEventInfo.setErrorMessage(eventInfoJson.getStr("errorMessage"));
            response.httpEventInfo.setThreadName(eventInfoJson.getStr("threadName"));
        }

        // 读取请求时间
        long requestTime = jsonItem.getLong("requestTime", System.currentTimeMillis());

        return new RequestHistoryItem(request, response, requestTime);
    }

    private JSONArray convertHeadersListToJson(List<HttpHeader> headersList) {
        JSONArray jsonArray = new JSONArray();
        if (headersList == null) {
            return jsonArray;
        }
        for (HttpHeader header : headersList) {
            if (header == null) {
                continue;
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.set("enabled", header.isEnabled());
            jsonObject.set("key", header.getKey());
            jsonObject.set("value", header.getValue());
            jsonObject.set("description", header.getDescription());
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }

    private List<HttpHeader> convertHeadersListFromJson(JSONArray jsonArray) {
        List<HttpHeader> headersList = new ArrayList<>();
        if (jsonArray == null) {
            return headersList;
        }
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            headersList.add(new HttpHeader(
                    jsonObject.getBool("enabled", true),
                    jsonObject.getStr("key", ""),
                    jsonObject.getStr("value", ""),
                    jsonObject.getStr("description", "")
            ));
        }
        return headersList;
    }

    private JSONArray convertParamsListToJson(List<HttpParam> paramsList) {
        JSONArray jsonArray = new JSONArray();
        if (paramsList == null) {
            return jsonArray;
        }
        for (HttpParam param : paramsList) {
            if (param == null) {
                continue;
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.set("enabled", param.isEnabled());
            jsonObject.set("key", param.getKey());
            jsonObject.set("value", param.getValue());
            jsonObject.set("description", param.getDescription());
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }

    private List<HttpParam> convertParamsListFromJson(JSONArray jsonArray) {
        List<HttpParam> paramsList = new ArrayList<>();
        if (jsonArray == null) {
            return paramsList;
        }
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            paramsList.add(new HttpParam(
                    jsonObject.getBool("enabled", true),
                    jsonObject.getStr("key", ""),
                    jsonObject.getStr("value", ""),
                    jsonObject.getStr("description", "")
            ));
        }
        return paramsList;
    }

    private JSONArray convertFormDataListToJson(List<HttpFormData> formDataList) {
        JSONArray jsonArray = new JSONArray();
        if (formDataList == null) {
            return jsonArray;
        }
        for (HttpFormData formData : formDataList) {
            if (formData == null) {
                continue;
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.set("enabled", formData.isEnabled());
            jsonObject.set("key", formData.getKey());
            jsonObject.set("type", formData.getType());
            jsonObject.set("value", formData.getValue());
            jsonObject.set("description", formData.getDescription());
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }

    private List<HttpFormData> convertFormDataListFromJson(JSONArray jsonArray) {
        List<HttpFormData> formDataList = new ArrayList<>();
        if (jsonArray == null) {
            return formDataList;
        }
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            formDataList.add(new HttpFormData(
                    jsonObject.getBool("enabled", true),
                    jsonObject.getStr("key", ""),
                    jsonObject.getStr("type", HttpFormData.TYPE_TEXT),
                    jsonObject.getStr("value", ""),
                    jsonObject.getStr("description", "")
            ));
        }
        return formDataList;
    }

    private JSONArray convertUrlencodedListToJson(List<HttpFormUrlencoded> urlencodedList) {
        JSONArray jsonArray = new JSONArray();
        if (urlencodedList == null) {
            return jsonArray;
        }
        for (HttpFormUrlencoded urlencoded : urlencodedList) {
            if (urlencoded == null) {
                continue;
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.set("enabled", urlencoded.isEnabled());
            jsonObject.set("key", urlencoded.getKey());
            jsonObject.set("value", urlencoded.getValue());
            jsonObject.set("description", urlencoded.getDescription());
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }

    private List<HttpFormUrlencoded> convertUrlencodedListFromJson(JSONArray jsonArray) {
        List<HttpFormUrlencoded> urlencodedList = new ArrayList<>();
        if (jsonArray == null) {
            return urlencodedList;
        }
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            urlencodedList.add(new HttpFormUrlencoded(
                    jsonObject.getBool("enabled", true),
                    jsonObject.getStr("key", ""),
                    jsonObject.getStr("value", ""),
                    jsonObject.getStr("description", "")
            ));
        }
        return urlencodedList;
    }

    private List<String> readStringList(JSONArray jsonArray) {
        List<String> values = new ArrayList<>();
        if (jsonArray == null) {
            return values;
        }
        for (int i = 0; i < jsonArray.size(); i++) {
            String value = jsonArray.getStr(i);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }
}
