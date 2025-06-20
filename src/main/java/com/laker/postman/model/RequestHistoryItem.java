package com.laker.postman.model;

/**
 * 请求历史项，包含请求和响应的简要信息
 */
public class RequestHistoryItem {
    public final String method;
    public final String url;
    public final String requestBody;
    public final String requestHeaders;
    public final String responseStatus;
    public final String responseBody;
    public final String responseHeaders;
    public final long timestamp;
    // 用于存储重定向链
    public String extra;
    public String threadName; // 执行线程名

    public RequestHistoryItem(String method, String url, String requestBody, String requestHeaders, String responseStatus, String responseHeaders, String responseBody, long timestamp) {
        this.method = method;
        this.url = url;
        this.requestBody = requestBody;
        this.requestHeaders = requestHeaders;
        this.responseStatus = responseStatus;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
        this.timestamp = timestamp;
    }

    public RequestHistoryItem(String method, String url, String requestBody, String requestHeaders, String responseStatus, String responseHeaders, String responseBody, long timestamp, String threadName) {
        this.method = method;
        this.url = url;
        this.requestBody = requestBody;
        this.requestHeaders = requestHeaders;
        this.responseStatus = responseStatus;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
        this.timestamp = timestamp;
        this.threadName = threadName;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", method, url);
    }
}
