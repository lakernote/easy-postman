package com.laker.postman.http.runtime.sse;

import com.laker.postman.http.runtime.model.HttpResponse;

/**
 * Callback used by the synchronous SSE request path.
 *
 * <p>This is deliberately owned by the runtime instead of extending an
 * OkHttp-internal callback. OkHttp's internal SSE parser is not a stable API
 * and is not available in OkHttp 5.</p>
 */
public interface SseResponseCallback {
    void onOpen(HttpResponse response);

    default void onEvent(String id, String type, String data) {
    }

    default void onRetryChange(long timeMs) {
    }

    default void onClosed(HttpResponse response) {
    }

    default void onFailure(String errorMsg, HttpResponse response) {
    }
}
