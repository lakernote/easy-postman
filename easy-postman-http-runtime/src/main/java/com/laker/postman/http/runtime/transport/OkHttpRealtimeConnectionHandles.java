package com.laker.postman.http.runtime.transport;

import okhttp3.WebSocket;
import okhttp3.sse.EventSource;

import java.util.concurrent.atomic.AtomicBoolean;

final class OkHttpRealtimeConnectionHandles {
    private OkHttpRealtimeConnectionHandles() {
    }

    static RealtimeConnectionHandle sse(EventSource eventSource) {
        return sse(eventSource, new AtomicBoolean());
    }

    static RealtimeConnectionHandle sse(EventSource eventSource, AtomicBoolean cancellationRequested) {
        return new SseHandle(eventSource,
                cancellationRequested == null ? new AtomicBoolean() : cancellationRequested);
    }

    static RealtimeWebSocketConnection webSocket(WebSocket webSocket) {
        return new WebSocketHandle(webSocket);
    }

    private record SseHandle(EventSource eventSource,
                             AtomicBoolean cancellationRequested) implements RealtimeConnectionHandle {
        @Override
        public void cancel() {
            cancellationRequested.set(true);
            if (eventSource != null) {
                eventSource.cancel();
            }
        }

        @Override
        public Object metricKey() {
            return eventSource != null ? eventSource : this;
        }
    }

    private record WebSocketHandle(WebSocket webSocket) implements RealtimeWebSocketConnection {
        @Override
        public boolean send(String text) {
            return webSocket != null && webSocket.send(text);
        }

        @Override
        public long queueSize() {
            return webSocket == null ? 0 : webSocket.queueSize();
        }

        @Override
        public boolean close(int code, String reason) {
            return webSocket != null && webSocket.close(code, reason);
        }

        @Override
        public void cancel() {
            if (webSocket != null) {
                webSocket.cancel();
            }
        }

        @Override
        public Object metricKey() {
            return webSocket != null ? webSocket : this;
        }
    }
}
