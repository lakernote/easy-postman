package com.laker.postman.performance.core.request;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Value
public class PerformanceRequestSnapshot {
    public static final String HTTP_VERSION_AUTO = "AUTO";
    public static final String HTTP_VERSION_HTTP_1_1 = "HTTP_1_1";
    public static final String HTTP_VERSION_HTTP_2 = "HTTP_2";
    public static final String PROXY_POLICY_DEFAULT = "DEFAULT";
    public static final String PROXY_POLICY_USE_PROXY = "USE_PROXY";
    public static final String PROXY_POLICY_NO_PROXY = "NO_PROXY";
    public static final String AUTH_API_KEY_PLACEMENT_HEADER = "Header";

    String id;
    String name;
    String description;
    String url;
    String method;
    PerformanceRequestProtocol protocol;
    List<PerformanceRequestKeyValue> headers;
    String bodyType;
    String body;
    List<PerformanceRequestKeyValue> params;
    List<PerformanceRequestFormDataPart> formData;
    List<PerformanceRequestKeyValue> urlencoded;
    PerformanceAuthType authType;
    String authUsername;
    String authPassword;
    String authToken;
    String authApiKeyName;
    String authApiKeyValue;
    String authApiKeyPlacement;
    Boolean followRedirects;
    Boolean cookieJarEnabled;
    String proxyPolicy;
    String httpVersion;
    Integer requestTimeoutMs;
    Integer webSocketPingIntervalMs;
    String prescript;
    String postscript;
    PerformanceRequestExecutionScopeSnapshot executionScope;

    @Builder(toBuilder = true)
    public PerformanceRequestSnapshot(String id,
                                      String name,
                                      String description,
                                      String url,
                                      String method,
                                      PerformanceRequestProtocol protocol,
                                      List<PerformanceRequestKeyValue> headers,
                                      String bodyType,
                                      String body,
                                      List<PerformanceRequestKeyValue> params,
                                      List<PerformanceRequestFormDataPart> formData,
                                      List<PerformanceRequestKeyValue> urlencoded,
                                      PerformanceAuthType authType,
                                      String authUsername,
                                      String authPassword,
                                      String authToken,
                                      String authApiKeyName,
                                      String authApiKeyValue,
                                      String authApiKeyPlacement,
                                      Boolean followRedirects,
                                      Boolean cookieJarEnabled,
                                      String proxyPolicy,
                                      String httpVersion,
                                      Integer requestTimeoutMs,
                                      Integer webSocketPingIntervalMs,
                                      String prescript,
                                      String postscript,
                                      PerformanceRequestExecutionScopeSnapshot executionScope) {
        this.id = blankToEmpty(id);
        this.name = blankToEmpty(name);
        this.description = blankToEmpty(description);
        this.url = blankToEmpty(url);
        this.method = blankToDefault(method, "GET");
        this.protocol = protocol == null ? PerformanceRequestProtocol.HTTP : protocol;
        this.headers = copyKeyValues(headers);
        this.bodyType = blankToEmpty(bodyType);
        this.body = blankToEmpty(body);
        this.params = copyKeyValues(params);
        this.formData = copyFormData(formData);
        this.urlencoded = copyKeyValues(urlencoded);
        this.authType = authType == null ? PerformanceAuthType.INHERIT : authType;
        this.authUsername = blankToEmpty(authUsername);
        this.authPassword = blankToEmpty(authPassword);
        this.authToken = blankToEmpty(authToken);
        this.authApiKeyName = blankToEmpty(authApiKeyName);
        this.authApiKeyValue = blankToEmpty(authApiKeyValue);
        this.authApiKeyPlacement = blankToDefault(authApiKeyPlacement, AUTH_API_KEY_PLACEMENT_HEADER);
        this.followRedirects = followRedirects;
        this.cookieJarEnabled = cookieJarEnabled;
        this.proxyPolicy = normalizeProxyPolicy(proxyPolicy);
        this.httpVersion = normalizeHttpVersion(httpVersion);
        this.requestTimeoutMs = requestTimeoutMs;
        this.webSocketPingIntervalMs = webSocketPingIntervalMs;
        this.prescript = blankToEmpty(prescript);
        this.postscript = blankToEmpty(postscript);
        this.executionScope = executionScope == null
                ? PerformanceRequestExecutionScopeSnapshot.empty()
                : PerformanceRequestExecutionScopeSnapshot.fromGroupVariables(executionScope.getGroupVariables());
    }

    public static PerformanceRequestSnapshot empty() {
        return builder().build();
    }

    public boolean executesChildrenInSamplerOrder() {
        return protocol == PerformanceRequestProtocol.WEBSOCKET;
    }

    public boolean isHttp() {
        return protocol == PerformanceRequestProtocol.HTTP;
    }

    public boolean isWebSocket() {
        return protocol == PerformanceRequestProtocol.WEBSOCKET;
    }

    private static List<PerformanceRequestKeyValue> copyKeyValues(List<PerformanceRequestKeyValue> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<PerformanceRequestKeyValue> copy = new ArrayList<>(source.size());
        for (PerformanceRequestKeyValue value : source) {
            if (value != null) {
                copy.add(value);
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<PerformanceRequestFormDataPart> copyFormData(List<PerformanceRequestFormDataPart> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<PerformanceRequestFormDataPart> copy = new ArrayList<>(source.size());
        for (PerformanceRequestFormDataPart value : source) {
            if (value != null) {
                copy.add(value);
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static String normalizeHttpVersion(String httpVersion) {
        if (httpVersion == null || httpVersion.trim().isEmpty()) {
            return HTTP_VERSION_AUTO;
        }
        String normalized = httpVersion.trim();
        if (HTTP_VERSION_HTTP_1_1.equals(normalized) || HTTP_VERSION_HTTP_2.equals(normalized)) {
            return normalized;
        }
        return HTTP_VERSION_AUTO;
    }

    public static String normalizeProxyPolicy(String proxyPolicy) {
        if (proxyPolicy == null || proxyPolicy.trim().isEmpty()) {
            return PROXY_POLICY_DEFAULT;
        }
        String normalized = proxyPolicy.trim();
        if (PROXY_POLICY_USE_PROXY.equals(normalized) || PROXY_POLICY_NO_PROXY.equals(normalized)) {
            return normalized;
        }
        return PROXY_POLICY_DEFAULT;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
