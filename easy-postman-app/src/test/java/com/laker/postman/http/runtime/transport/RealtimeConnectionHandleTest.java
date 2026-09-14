package com.laker.postman.http.runtime.transport;

import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.sse.EventSource;
import okio.ByteString;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class RealtimeConnectionHandleTest {

    @Test
    public void shouldDelegateSseCancellationThroughNeutralHandle() {
        FakeEventSource eventSource = new FakeEventSource();
        AtomicBoolean cancellationRequested = new AtomicBoolean();

        RealtimeConnectionHandle handle = OkHttpRealtimeConnectionHandles.sse(
                eventSource, cancellationRequested);

        handle.cancel();

        assertTrue(eventSource.cancelled);
        assertTrue(cancellationRequested.get());
        assertSame(handle.metricKey(), eventSource);
    }

    @Test
    public void shouldDelegateWebSocketOperationsThroughNeutralHandle() {
        FakeWebSocket webSocket = new FakeWebSocket();
        webSocket.queueSize = 42;

        RealtimeWebSocketConnection handle = OkHttpRealtimeConnectionHandles.webSocket(webSocket);

        assertTrue(handle.send("hello"));
        assertTrue(handle.close(1000, "done"));
        handle.cancel();

        assertEquals(webSocket.lastText, "hello");
        assertEquals(webSocket.closeCode, 1000);
        assertEquals(webSocket.closeReason, "done");
        assertTrue(webSocket.cancelled);
        assertEquals(handle.queueSize(), 42);
        assertSame(handle.metricKey(), webSocket);
    }

    private static final class FakeEventSource implements EventSource {
        private boolean cancelled;

        @Override
        public Request request() {
            return new Request.Builder().url("http://localhost").build();
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private String lastText;
        private int closeCode;
        private String closeReason;
        private boolean cancelled;
        private long queueSize;

        @Override
        public Request request() {
            return new Request.Builder().url("http://localhost").build();
        }

        @Override
        public long queueSize() {
            return queueSize;
        }

        @Override
        public boolean send(String text) {
            lastText = text;
            return true;
        }

        @Override
        public boolean send(ByteString bytes) {
            return true;
        }

        @Override
        public boolean close(int code, String reason) {
            closeCode = code;
            closeReason = reason;
            return true;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
