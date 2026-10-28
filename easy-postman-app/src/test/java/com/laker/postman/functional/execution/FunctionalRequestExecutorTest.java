package com.laker.postman.functional.execution;

import com.laker.postman.functional.model.AssertionResult;
import com.laker.postman.functional.model.RunnerRowData;
import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.transport.HttpExchangeOptions;
import com.laker.postman.http.runtime.transport.HttpTransport;
import com.laker.postman.http.runtime.transport.RealtimeConnectionHandle;
import com.laker.postman.http.runtime.transport.RealtimeConnectionOptions;
import com.laker.postman.http.runtime.transport.RealtimeWebSocketConnection;
import com.laker.postman.http.runtime.sse.SseResponseCallback;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.service.variable.ExecutionVariableContext;
import okhttp3.WebSocketListener;
import okhttp3.sse.EventSourceListener;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class FunctionalRequestExecutorTest {

    @Test
    public void shouldUseFunctionalDiagnosticCaptureProfileWithoutNetworkLog() {
        CapturingTransport transport = new CapturingTransport();
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Functional Profile");
        item.setMethod("GET");
        item.setUrl("https://example.test/functional");

        FunctionalRequestExecutionResult result = new FunctionalRequestExecutor(null, transport).execute(
                new RunnerRowData(item),
                new ExecutionVariableContext(),
                () -> true
        );

        assertEquals(result.getStatus(), "204");
        assertSame(result.getRequest().captureProfile, HttpCaptureProfile.FUNCTIONAL_DIAGNOSTIC);
        assertSame(transport.request.captureProfile, HttpCaptureProfile.FUNCTIONAL_DIAGNOSTIC);
        assertTrue(result.getRequest().collectBasicInfo);
        assertTrue(result.getRequest().collectMetricsInfo);
        assertTrue(result.getRequest().collectEventInfo);
        assertFalse(result.getRequest().enableNetworkLog);
    }

    @Test
    public void shouldKeepExistingFunctionalPostRequestErrorBehavior() {
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Broken post script");
        item.setMethod("GET");
        item.setUrl("https://example.test/failure");
        item.setPostscript("throw new Error('post script boom');");

        FunctionalRequestExecutionResult result = new FunctionalRequestExecutor(
                null,
                new CapturingTransport()
        ).execute(
                new RunnerRowData(item),
                new ExecutionVariableContext(),
                () -> true
        );

        assertEquals(result.getStatus(), "204");
        assertSame(result.getAssertion(), AssertionResult.NO_TESTS);
        assertNull(result.getErrorMessage());
        assertTrue(result.getTestResults().isEmpty());
    }

    @Test
    public void shouldKeepExistingFunctionalPreRequestResultBehavior() {
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Functional pre-request assertion");
        item.setMethod("GET");
        item.setUrl("https://example.test/pre-request");
        item.setPrescript("pm.test('legacy pre test', function () { pm.expect(1).to.equal(2); });");

        FunctionalRequestExecutionResult result = new FunctionalRequestExecutor(
                null,
                new CapturingTransport()
        ).execute(
                new RunnerRowData(item),
                new ExecutionVariableContext(),
                () -> true
        );

        assertEquals(result.getStatus(), "204");
        assertSame(result.getAssertion(), AssertionResult.NO_TESTS);
        assertTrue(result.getTestResults().isEmpty());
    }

    @Test
    public void shouldSendRequestBodyUpdatedByPreScript() {
        CapturingTransport transport = new CapturingTransport();
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Functional request mutation");
        item.setMethod("POST");
        item.setUrl("https://example.test/legacy?source=old");
        item.setBody("{\"orderId\":123}");
        item.setPrescript("""
                pm.request.body = '<data>' + pm.request.body + '</data>';
                pm.request.method = 'PUT';
                pm.request.headers.upsert({key: 'Content-Type', value: 'application/xml'});
                pm.request.url.query.one('source').value = 'script';
                pm.request.url.query.add({key: 'flag'});
                """);

        FunctionalRequestExecutionResult result = new FunctionalRequestExecutor(null, transport).execute(
                new RunnerRowData(item),
                new ExecutionVariableContext(),
                () -> true
        );

        assertEquals(result.getStatus(), "204");
        assertEquals(transport.request.body, "<data>{\"orderId\":123}</data>");
        assertEquals(transport.request.method, "PUT");
        assertEquals(transport.request.headersList.get(0).getValue(), "application/xml");
        assertEquals(transport.request.url, "https://example.test/legacy?source=script&flag");
    }

    @Test
    public void shouldRunPostScriptTestsForEachDetectedSseEvent() {
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Functional SSE over HTTP");
        item.setMethod("POST");
        item.setUrl("https://example.test/events");
        item.setPostscript("pm.test(pm.response.text(), function () { pm.expect(pm.response.text()).to.not.be.empty; });");

        FunctionalRequestExecutionResult result = new FunctionalRequestExecutor(null, new SseEventTransport()).execute(
                new RunnerRowData(item),
                new ExecutionVariableContext(),
                () -> true
        );

        assertEquals(result.getStatus(), "200");
        assertSame(result.getAssertion(), AssertionResult.PASS);
        assertEquals(result.getTestResults().size(), 2);
        assertTrue(result.getTestResults().stream().allMatch(test -> test.passed));
        assertTrue(result.getResponse().isSse);
        assertTrue(result.getResponse().body.contains("data: event-one"));
        assertTrue(result.getResponse().body.contains("data: event-two"));
    }

    private static final class CapturingTransport implements HttpTransport {
        private PreparedRequest request;

        @Override
        public HttpResponse execute(PreparedRequest request, HttpExchangeOptions options) {
            this.request = request;
            HttpResponse response = new HttpResponse();
            response.code = 204;
            response.costMs = 12L;
            response.headers = new LinkedHashMap<>();
            return response;
        }

        @Override
        public RealtimeConnectionHandle openSse(PreparedRequest request,
                                                EventSourceListener listener,
                                                RealtimeConnectionOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RealtimeWebSocketConnection openWebSocket(PreparedRequest request,
                                                        WebSocketListener listener,
                                                        RealtimeConnectionOptions options) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class SseEventTransport implements HttpTransport {
        @Override
        public HttpResponse execute(PreparedRequest request, HttpExchangeOptions options) {
            HttpResponse response = new HttpResponse();
            response.code = 200;
            response.costMs = 25L;
            response.headers = new LinkedHashMap<>();
            SseResponseCallback callback = options.getCallback();
            callback.onOpen(response);
            callback.onEvent("1", "message", "event-one");
            callback.onEvent("2", "message", "event-two");
            callback.onClosed(response);
            return response;
        }

        @Override
        public RealtimeConnectionHandle openSse(PreparedRequest request,
                                                EventSourceListener listener,
                                                RealtimeConnectionOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RealtimeWebSocketConnection openWebSocket(PreparedRequest request,
                                                         WebSocketListener listener,
                                                         RealtimeConnectionOptions options) {
            throw new UnsupportedOperationException();
        }
    }
}
