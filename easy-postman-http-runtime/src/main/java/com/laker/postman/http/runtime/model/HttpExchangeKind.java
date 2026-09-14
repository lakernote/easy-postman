package com.laker.postman.http.runtime.model;

/**
 * Identifies how an OkHttp call is consumed by the transport runtime.
 *
 * <p>This is explicit execution metadata. It must not be inferred from HTTP
 * headers because a regular blocking request may legitimately advertise
 * {@code Accept: text/event-stream}.</p>
 */
public enum HttpExchangeKind {
    HTTP,
    ASYNC_SSE,
    WEBSOCKET
}
