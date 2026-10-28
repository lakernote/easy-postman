package com.laker.postman.http.request;

import lombok.experimental.UtilityClass;

@UtilityClass
public class HttpRequestProtocol {
    public static boolean isWebSocketUrl(String url) {
        return url != null && (url.startsWith("ws://") || url.startsWith("wss://"));
    }
}
