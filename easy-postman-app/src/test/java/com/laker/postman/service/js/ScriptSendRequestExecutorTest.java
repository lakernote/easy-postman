package com.laker.postman.service.js;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.transport.HttpExchangeOptions;
import com.laker.postman.http.runtime.transport.HttpTransport;
import com.laker.postman.http.runtime.transport.RealtimeConnectionHandle;
import com.laker.postman.http.runtime.transport.RealtimeConnectionOptions;
import com.laker.postman.http.runtime.transport.RealtimeWebSocketConnection;
import okhttp3.WebSocketListener;
import okhttp3.sse.EventSourceListener;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class ScriptSendRequestExecutorTest {

    @Test
    public void scriptRequestsShouldReadResponseInMemoryInsteadOfTempFiles() throws Exception {
        AtomicReference<PreparedRequest> sent = new AtomicReference<>();
        HttpTransport transport = new HttpTransport() {
            @Override
            public HttpResponse execute(PreparedRequest request, HttpExchangeOptions options) {
                sent.set(request);
                return new HttpResponse();
            }

            @Override
            public RealtimeConnectionHandle openSse(PreparedRequest request, EventSourceListener listener,
                                                    RealtimeConnectionOptions options) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RealtimeWebSocketConnection openWebSocket(PreparedRequest request, WebSocketListener listener,
                                                             RealtimeConnectionOptions options) {
                throw new UnsupportedOperationException();
            }
        };

        new ScriptSendRequestExecutor(transport).sendRequest(Map.of("url", "http://example.test/token"), null);

        assertNotNull(sent.get());
        assertEquals(sent.get().responseBodyMode, PreparedRequest.ResponseBodyMode.PREVIEW);
        assertEquals(sent.get().responseBodyPreviewLimitBytes, ScriptSendRequestExecutor.SCRIPT_RESPONSE_BODY_LIMIT_BYTES);
    }
}
