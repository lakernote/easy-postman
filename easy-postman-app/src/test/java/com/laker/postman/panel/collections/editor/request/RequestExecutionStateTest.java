package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.http.runtime.transport.RealtimeConnectionHandle;
import com.laker.postman.http.runtime.transport.RealtimeWebSocketConnection;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.EventListener;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;
import kotlin.jvm.functions.Function0;
import kotlin.reflect.KClass;
import org.testng.annotations.Test;

import javax.swing.*;
import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class RequestExecutionStateTest {

    @Test
    public void shouldDisposeOpenConnectionsAndClearRunningState() {
        RequestExecutionState state = new RequestExecutionState();
        FakeRealtimeHandle eventSource = new FakeRealtimeHandle();
        FakeWebSocketConnection webSocket = new FakeWebSocketConnection();
        SwingWorker<Void, Void> worker = newNoopWorker();

        state.startWorker(worker);
        state.startSseConnection(eventSource);
        state.beginWebSocketConnection("ws-1");
        state.attachWebSocketConnection(webSocket);
        state.markAutoDetectedHttpSseOpen();

        state.disposeOpenConnections();

        assertTrue(state.isDisposed());
        assertTrue(state.isSseCancelled());
        assertTrue(eventSource.canceled);
        assertTrue(webSocket.closed);
        assertEquals(webSocket.closeCode, 1000);
        assertEquals(webSocket.closeReason, "Tab closed");
        assertTrue(worker.isCancelled());
        assertNull(state.currentEventSource());
        assertNull(state.currentWebSocket());
        assertNull(state.currentWebSocketConnectionId());
        assertNull(state.currentWorker());
        assertFalse(state.isAutoDetectedHttpSseOpen());
    }

    @Test
    public void shouldClearCurrentWorkerOnlyWhenIdentityMatches() {
        RequestExecutionState state = new RequestExecutionState();
        SwingWorker<Void, Void> oldWorker = newNoopWorker();
        SwingWorker<Void, Void> newWorker = newNoopWorker();

        state.startWorker(newWorker);
        state.clearCurrentWorkerIf(oldWorker);
        assertSame(state.currentWorker(), newWorker);

        state.clearCurrentWorkerIf(newWorker);
        assertNull(state.currentWorker());
    }

    @Test
    public void shouldTrackAutoDetectedHttpSseStateWithExplicitMethods() {
        RequestExecutionState state = new RequestExecutionState();

        state.markAutoDetectedHttpSseOpen();
        assertTrue(state.isAutoDetectedHttpSseOpen());

        state.clearAutoDetectedHttpSseOpen();
        assertFalse(state.isAutoDetectedHttpSseOpen());
    }

    @Test
    public void shouldTrackUserCancelledSseState() {
        RequestExecutionState state = new RequestExecutionState();

        state.markSseCancelled();
        assertTrue(state.isSseCancelled());

        state.resetSseCancelled();
        assertFalse(state.isSseCancelled());
    }

    @Test
    public void shouldCancelActiveHttpCallWhenUserCancelsRequest() {
        RequestExecutionState state = new RequestExecutionState();
        FakeCall call = new FakeCall();

        state.onCallStarted(call);
        state.cancelCurrentHttpCall();

        assertTrue(call.cancelled);
        assertSame(state.currentHttpCall(), call);

        state.onCallFinished(call);
        assertNull(state.currentHttpCall());
    }

    @Test
    public void shouldDisposeActiveHttpCall() {
        RequestExecutionState state = new RequestExecutionState();
        FakeCall call = new FakeCall();

        state.onCallStarted(call);
        state.disposeOpenConnections();

        assertTrue(call.cancelled);
        assertNull(state.currentHttpCall());
    }

    private SwingWorker<Void, Void> newNoopWorker() {
        return new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                return null;
            }
        };
    }

    private static class FakeRealtimeHandle implements RealtimeConnectionHandle {
        private boolean canceled;

        @Override
        public void cancel() {
            canceled = true;
        }
    }

    private static final class FakeWebSocketConnection extends FakeRealtimeHandle
            implements RealtimeWebSocketConnection {
        private boolean closed;
        private int closeCode;
        private String closeReason;

        @Override
        public boolean close(int code, String reason) {
            closed = true;
            closeCode = code;
            closeReason = reason;
            return true;
        }

        @Override
        public boolean send(String text) {
            return true;
        }

        @Override
        public long queueSize() {
            return 0;
        }
    }

    private static final class FakeCall implements Call {
        private boolean cancelled;

        @Override
        public Request request() {
            return new Request.Builder().url("https://example.test/cancel").build();
        }

        @Override
        public Response execute() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enqueue(Callback responseCallback) {
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isExecuted() {
            return false;
        }

        @Override
        public boolean isCanceled() {
            return cancelled;
        }

        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }

        @Override
        public void addEventListener(EventListener eventListener) {
        }

        @Override
        public <T> T tag(Class<? extends T> type) {
            return null;
        }

        @Override
        public <T> T tag(KClass<T> type) {
            return null;
        }

        @Override
        public <T> T tag(KClass<T> type, Function0<? extends T> defaultValue) {
            return defaultValue == null ? null : defaultValue.invoke();
        }

        @Override
        public <T> T tag(Class<T> type, Function0<? extends T> defaultValue) {
            return defaultValue == null ? null : defaultValue.invoke();
        }

        @Override
        public Call clone() {
            return new FakeCall();
        }
    }
}
