package com.laker.postman.http.request;

import cn.hutool.core.util.IdUtil;
import com.laker.postman.request.defaults.HttpRequestDefaults;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.util.SystemUtil;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.laker.postman.request.defaults.HttpRequestDefaults.APPLICATION_JSON;
import static com.laker.postman.request.defaults.HttpRequestDefaults.CONTENT_TYPE;

@UtilityClass
public class HttpRequestFactory {
    private static final String DEFAULT_USER_AGENT_VERSION = "dev";
    public static final String TEXT_EVENT_STREAM = "text/event-stream";
    public static final String ACCEPT = HttpRequestDefaults.ACCEPT;
    public static final String USER_AGENT = HttpRequestDefaults.USER_AGENT;
    public static final String EASY_POSTMAN_CLIENT = "EasyPostman/" + resolveUserAgentVersion();
    public static final String ACCEPT_ENCODING = HttpRequestDefaults.ACCEPT_ENCODING;
    public static final String CONNECTION = HttpRequestDefaults.CONNECTION;
    public static final String ACCEPT_ENCODING_VALUE = HttpRequestDefaults.ACCEPT_ENCODING_VALUE;
    public static final String IDENTITY_ACCEPT_ENCODING = "identity";
    public static final String CONNECTION_VALUE = HttpRequestDefaults.CONNECTION_VALUE;

    private static String resolveUserAgentVersion() {
        String version = SystemUtil.getCurrentVersion();
        if (version == null || version.isBlank()) {
            return DEFAULT_USER_AGENT_VERSION;
        }
        return StandardCharsets.US_ASCII.newEncoder().canEncode(version)
                ? version
                : DEFAULT_USER_AGENT_VERSION;
    }

    public static HttpRequestItem createDefaultRequest() {
        // Create a default test request
        HttpRequestItem testItem = new HttpRequestItem();
        testItem.setId(IdUtil.simpleUUID());
        testItem.setName("Default Request");
        testItem.setUrl("https://httpbin.org/get");
        testItem.setMethod("GET");
        // Add some default headers (using ArrayList to ensure mutability)
        testItem.setHeadersList(new ArrayList<>(AppRequestHeaderDefaults.generatedHeaders()));
        return testItem;
    }

    public static HttpRequestItem createBlankRequest(RequestItemProtocolEnum protocol) {
        RequestItemProtocolEnum resolvedProtocol = protocol == null ? RequestItemProtocolEnum.HTTP : protocol;
        HttpRequestItem request = createDefaultRequest();
        request.setProtocol(resolvedProtocol);
        request.setMethod("GET");
        request.setUrl("");

        // 新增请求使用“空白模板”，协议差异只在工厂集中处理，避免 Swing/JavaFX/CLI 各自复制初始化规则。
        if (resolvedProtocol.isWebSocketProtocol()) {
            request.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
            appendHeader(request, CONTENT_TYPE, APPLICATION_JSON);
            replaceHeader(request, ACCEPT_ENCODING, IDENTITY_ACCEPT_ENCODING);
        }
        return request;
    }

    // Default redirect request
    public static HttpRequestItem createDefaultRedirectRequest() {
        // Create a default redirect request
        HttpRequestItem testItem = new HttpRequestItem();
        testItem.setId(IdUtil.simpleUUID());
        testItem.setName("Redirect Example");
        testItem.setUrl("https://httpbin.org/redirect/1");
        testItem.setMethod("GET");
        // Add some default headers
        testItem.setHeadersList(new ArrayList<>(AppRequestHeaderDefaults.generatedHeaders()));
        return testItem;
    }

    public static HttpRequestItem createDefaultWebSocketRequest() {
        HttpRequestItem testItem = createBlankRequest(RequestItemProtocolEnum.WEBSOCKET);
        testItem.setName("WebSocket Example");
        testItem.setUrl("wss://ws.ifelse.io");
        return testItem;
    }

    public static HttpRequestItem createDefaultEventStreamRequest() {
        HttpRequestItem testItem = createBlankRequest(RequestItemProtocolEnum.HTTP);
        testItem.setName("SSE Example");
        testItem.setUrl("https://stream.wikimedia.org/v2/stream/recentchange");
        replaceHeader(testItem, ACCEPT, TEXT_EVENT_STREAM);
        replaceHeader(testItem, ACCEPT_ENCODING, IDENTITY_ACCEPT_ENCODING);
        return testItem;
    }

    private void appendHeader(HttpRequestItem request, String key, String value) {
        mutableHeaders(request).add(new HttpHeader(true, key, value));
    }

    private void replaceHeader(HttpRequestItem request, String key, String value) {
        List<HttpHeader> headers = mutableHeaders(request);
        headers.removeIf(header -> key.equalsIgnoreCase(header.getKey()));
        headers.add(new HttpHeader(true, key, value));
    }

    private List<HttpHeader> mutableHeaders(HttpRequestItem request) {
        List<HttpHeader> headers = request.getHeadersList();
        if (headers == null) {
            headers = new ArrayList<>();
            request.setHeadersList(headers);
        }
        return headers;
    }
}
