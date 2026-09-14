package com.laker.postman.http.runtime.redirect;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.cookie.HttpCookieStore;
import com.laker.postman.http.runtime.transport.DefaultHttpTransport;
import com.laker.postman.http.runtime.transport.HttpExchangeOptions;
import com.laker.postman.http.runtime.transport.HttpTransport;
import com.laker.postman.http.runtime.transport.RealtimeConnectionHandle;
import com.laker.postman.http.runtime.transport.RealtimeConnectionOptions;
import com.laker.postman.http.runtime.transport.RealtimeWebSocketConnection;
import com.laker.postman.http.runtime.sse.SseStreamCallback;
import com.laker.postman.http.runtime.sse.SseStreamEventListener;
import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.TransportAuth;


import com.laker.postman.http.runtime.observation.NetworkLogEvent;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.http.request.PreparedRequestFactory;
import com.laker.postman.service.curl.CurlImportUtil;
import com.laker.postman.service.curl.CurlParser;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import okhttp3.HttpUrl;
import okhttp3.Protocol;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.Buffer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.SkipException;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLHandshakeException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class DefaultHttpTransportIntegrationTest {

    private static final long RECORDED_REQUEST_TIMEOUT_SECONDS = 5L;

    private final HttpTransport httpTransport = new DefaultHttpTransport();

    private MockWebServer server;

    @BeforeMethod
    public void isolateRuntimeSettings() {
        HttpRuntimeSettingsProvider.reset();
        OkHttpClientManager.clearClientCache();
    }

    @AfterMethod
    public void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
            server = null;
        }
        OkHttpClientManager.clearClientCache();
        HttpRuntimeSettingsProvider.reset();
        HttpCookieStore.clearAllCookies();
    }

    @Test
    public void shouldSendGetRequestAndReadPlainTextResponse() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("pong"));

        PreparedRequest request = createRequest("GET", serverUrl("/ping"));
        request.headersList.add(new HttpHeader(true, "Accept", "*/*"));
        request.headersList.add(new HttpHeader(true, "User-Agent", "EasyPostman/Test"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(response.code, 200);
        assertEquals(response.body, "pong");
        assertEquals(recordedRequest.getMethod(), "GET");
        assertEquals(recordedRequest.getHeader("Accept"), "*/*");
        assertEquals(recordedRequest.getHeader("User-Agent"), "EasyPostman/Test");
    }

    @Test
    public void shouldSendJsonPostBodyToServer() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("{\"ok\":true}"));

        PreparedRequest request = createRequest("POST", serverUrl("/echo"));
        request.body = "{\"chatId\":1,\"text\":\"hello\"}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json; charset=utf-8"));
        request.headersList.add(new HttpHeader(true, "Accept", "application/json"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(response.code, 200);
        assertEquals(response.body, "{\"ok\":true}");
        assertEquals(recordedRequest.getMethod(), "POST");
        assertEquals(recordedRequest.getHeader("Content-Type"), "application/json; charset=utf-8");
        assertEquals(recordedRequest.getBody().readUtf8(), request.body);
    }

    @Test
    public void shouldPublishRequestSnapshotAfterRequestHeadersStartInNetworkLog() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("{\"ok\":true}"));

        PreparedRequest request = createRequest("POST", serverUrl("/echo"));
        request.body = "{\"chatId\":1,\"text\":\"hello\"}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json; charset=utf-8"));
        request.headersList.add(new HttpHeader(true, "Accept", "application/json"));
        request.enableNetworkLog = true;
        List<NetworkLogEvent> events = Collections.synchronizedList(new ArrayList<>());
        request.networkLogSink = events::add;

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getBody().readUtf8(), request.body);
        int headersStart = firstEventIndex(events, NetworkLogEventStage.REQUEST_HEADERS_START);
        int headersEnd = firstEventIndex(events, NetworkLogEventStage.REQUEST_HEADERS_END);
        int bodyStart = firstEventIndex(events, NetworkLogEventStage.REQUEST_BODY_START);
        int bodyEnd = firstEventIndex(events, NetworkLogEventStage.REQUEST_BODY_END);
        assertTrue(headersStart >= 0, "Network log should include request headers start");
        assertTrue(headersEnd > headersStart, eventSnapshot(events).toString());
        assertTrue(bodyStart > headersEnd, eventSnapshot(events).toString());
        assertTrue(bodyEnd > bodyStart, eventSnapshot(events).toString());
        assertTrue(firstEventMessage(events, NetworkLogEventStage.REQUEST_HEADERS_END).contains("Content-Type"),
                "Request headers end should include the actual sent headers");
        assertTrue(firstEventMessage(events, NetworkLogEventStage.REQUEST_BODY_START).contains("\"chatId\""),
                "Request body start should include the captured request body preview");
        assertDurationRecorded(firstEvent(events, NetworkLogEventStage.REQUEST_HEADERS_END));
        assertDurationRecorded(firstEvent(events, NetworkLogEventStage.REQUEST_BODY_END));
    }

    @Test
    public void shouldMarkReusedConnectionInNetworkLog() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("first"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("second"));

        PreparedRequest firstRequest = createRequest("GET", serverUrl("/first"));
        httpTransport.execute(firstRequest, HttpExchangeOptions.defaults());
        takeRecordedRequest();

        PreparedRequest secondRequest = createRequest("GET", serverUrl("/second"));
        secondRequest.collectEventInfo = true;
        secondRequest.enableNetworkLog = true;
        List<NetworkLogEvent> events = Collections.synchronizedList(new ArrayList<>());
        secondRequest.networkLogSink = events::add;

        httpTransport.execute(secondRequest, HttpExchangeOptions.defaults());
        takeRecordedRequest();

        assertTrue(events.stream().noneMatch(event -> event.stage() == NetworkLogEventStage.CONNECT_START),
                "Second request should reuse the warm connection in this test: " + eventSnapshot(events));
        String connectionAcquired = firstEventMessage(events, NetworkLogEventStage.CONNECTION_ACQUIRED);
        assertTrue(connectionAcquired.contains("Connection reused"),
                "Reused connection should be explicit in the network log: " + eventSnapshot(events));
    }

    @Test
    public void shouldPreserveExplicitJsonContentTypeWhenSendingRawBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true}"));

        PreparedRequest request = createRequest("POST", serverUrl("/echo"));
        request.body = "{\"chatId\": 1, \"text\": \"hello\"}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        request.headersList.add(new HttpHeader(true, "Accept", "text/event-stream"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getHeader("Content-Type"), "application/json");
        assertEquals(recordedRequest.getBody().readUtf8(), request.body);
    }

    @Test
    public void shouldCaptureSentSnapshotForFailedSseRequest() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"forbidden\"}"));

        PreparedRequest request = createRequest("POST", serverUrl("/stream"));
        request.body = "{\"action\":\"next\",\"messages\":[{\"content\":\"hello\"}]}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        request.headersList.add(new HttpHeader(true, "Accept", "text/event-stream"));
        request.collectBasicInfo = true;
        request.collectEventInfo = true;
        request.enableNetworkLog = true;
        List<NetworkLogEvent> events = Collections.synchronizedList(new ArrayList<>());
        request.networkLogSink = events::add;

        CountDownLatch failed = new CountDownLatch(1);
        RealtimeConnectionHandle handle = httpTransport.openSse(
                request,
                new EventSourceListener() {
                    @Override
                    public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                        failed.countDown();
                    }
                },
                RealtimeConnectionOptions.defaults()
        );
        try {
            assertTrue(failed.await(2, TimeUnit.SECONDS), "SSE 403 should report failure");
        } finally {
            handle.cancel();
        }

        RecordedRequest recordedRequest = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals(recordedRequest.getMethod(), "POST");
        assertEquals(recordedRequest.getBody().readUtf8(), request.body);
        assertNotNull(request.sentHeadersList);
        assertEquals(request.sentRequestBody, request.body);
        assertEquals(findHeaderValue(request.sentHeadersList, "Content-Type"), "application/json");
        assertEquals(findHeaderValue(request.sentHeadersList, "Accept"), "text/event-stream");
        assertEquals(findHeaderValue(request.sentHeadersList, "Host"), recordedRequest.getHeader("Host"));
        assertEquals(findHeaderValue(request.sentHeadersList, "Content-Length"),
                String.valueOf(request.body.getBytes(StandardCharsets.UTF_8).length));
        waitForNetworkLogStage(events, NetworkLogEventStage.CALL_START);
        waitForNetworkLogStage(events, NetworkLogEventStage.REQUEST_HEADERS_END);
        String callStart = firstEventMessage(events, NetworkLogEventStage.CALL_START);
        assertTrue(callStart.contains("Stream Request: POST " + request.url), callStart);
        assertTrue(callStart.contains("Stream Flow: HTTP POST + text/event-stream response body stays open"),
                callStart);
        assertFalse(callStart.contains("Stream Flow: HTTP GET + text/event-stream response body stays open"),
                callStart);
        assertTrue(events.stream().anyMatch(event -> event.stage() == NetworkLogEventStage.REQUEST_HEADERS_END),
                "SSE network log should include actual sent headers: " + eventSnapshot(events));
        assertTrue(events.stream().anyMatch(event -> event.stage() == NetworkLogEventStage.REQUEST_BODY_START),
                "SSE network log should include actual sent body snapshot: " + eventSnapshot(events));
        assertTrue(events.stream().anyMatch(event -> event.stage() == NetworkLogEventStage.RESPONSE_HEADERS_END),
                "SSE network log should include handshake response headers: " + eventSnapshot(events));
    }

    @Test
    public void shouldExposeHttpFailureBodyForFailedSseRequest() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("{\"detail\":\"Unusual activity has been detected from your device. Try again later.\"}"));

        PreparedRequest request = createRequest("POST", serverUrl("/stream-failure"));
        request.body = "{\"stream\":true}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        request.headersList.add(new HttpHeader(true, "Accept", "text/event-stream"));
        request.collectBasicInfo = true;
        request.collectEventInfo = true;
        request.enableNetworkLog = true;

        HttpResponse response = new HttpResponse();
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        RealtimeConnectionHandle handle = httpTransport.openSse(
                request,
                new SseStreamEventListener(new SseStreamCallback() {
                    @Override
                    public void onOpen(HttpResponse response, String headersText) {
                    }

                    @Override
                    public void onEvent(String id, String type, String data) {
                    }

                    @Override
                    public void onClosed(HttpResponse response) {
                    }

                    @Override
                    public void onFailure(String errorMsg, HttpResponse response) {
                        errorMessage.set(errorMsg);
                        failed.countDown();
                    }
                }, response, new StringBuilder(), System.currentTimeMillis(), () -> false, request),
                RealtimeConnectionOptions.defaults()
        );
        try {
            assertTrue(failed.await(2, TimeUnit.SECONDS), "SSE 403 should report failure");
        } finally {
            handle.cancel();
        }

        assertEquals(response.code, 403);
        assertTrue(response.body.contains("Unusual activity has been detected"), response.body);
        assertTrue(response.bodySize > 0, "SSE failure response body should be retained");
        assertTrue(errorMessage.get().contains("HTTP 403"), errorMessage.get());
        assertTrue(errorMessage.get().contains("Unusual activity has been detected"), errorMessage.get());
    }

    @Test
    public void shouldPublishCallStartAndConnectionMetadataForSseNetworkLog() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream; charset=utf-8")
                .setBody("data: hello\n\n"));

        PreparedRequest request = createRequest("GET", serverUrl("/events"));
        request.headersList.add(new HttpHeader(true, "Accept", "text/event-stream"));
        request.collectBasicInfo = true;
        request.collectEventInfo = true;
        request.enableNetworkLog = true;
        List<NetworkLogEvent> events = Collections.synchronizedList(new ArrayList<>());
        request.networkLogSink = events::add;

        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        RealtimeConnectionHandle handle = httpTransport.openSse(
                request,
                new EventSourceListener() {
                    @Override
                    public void onOpen(EventSource eventSource, okhttp3.Response response) {
                        opened.countDown();
                    }

                    @Override
                    public void onClosed(EventSource eventSource) {
                        closed.countDown();
                    }
                },
                RealtimeConnectionOptions.defaults()
        );
        try {
            assertTrue(opened.await(2, TimeUnit.SECONDS), "SSE stream should open");
            assertTrue(closed.await(2, TimeUnit.SECONDS), "SSE stream should close after mock body");
        } finally {
            handle.cancel();
        }

        RecordedRequest recordedRequest = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals(recordedRequest.getPath(), "/events");

        waitForNetworkLogStage(events, NetworkLogEventStage.CALL_START);
        waitForNetworkLogStage(events, NetworkLogEventStage.REQUEST_HEADERS_END);
        waitForNetworkLogStage(events, NetworkLogEventStage.RESPONSE_HEADERS_END);
        String callStart = firstEventMessage(events, NetworkLogEventStage.CALL_START);
        String requestHeaders = firstEventMessage(events, NetworkLogEventStage.REQUEST_HEADERS_END);
        String responseHeaders = firstEventMessage(events, NetworkLogEventStage.RESPONSE_HEADERS_END);

        assertEquals(countEvents(events, NetworkLogEventStage.CALL_START), 1,
                "SSE should publish a single CallStart event");
        assertEquals(countEvents(events, NetworkLogEventStage.RESPONSE_HEADERS_END), 1,
                "SSE should publish the response headers once: " + eventSnapshot(events));
        assertTrue(callStart.contains("SSE URL: " + request.url), callStart);
        assertTrue(callStart.contains("Stream Request: GET " + request.url), callStart);
        assertTrue(callStart.contains("Stream Flow: HTTP GET + text/event-stream response body stays open"), callStart);
        assertTrue(requestHeaders.contains("SSE Stream Request Headers:"), requestHeaders);
        assertTrue(responseHeaders.contains("Result: SSE stream accepted"), responseHeaders);
        assertTrue(responseHeaders.contains("SSE URL: " + request.url), responseHeaders);
        assertTrue(responseHeaders.matches("(?s).*Thread: \\S+.*"), responseHeaders);
        assertTrue(responseHeaders.contains("Connection: "), responseHeaders);
        assertTrue(responseHeaders.contains("HTTP Stream Protocol: http/1.1"), responseHeaders);
        assertFalse(eventSnapshot(events).stream()
                        .anyMatch(event -> event.stage() == NetworkLogEventStage.CALL_FAILED
                                && event.message().contains("Socket closed")),
                "Closing a successfully opened SSE stream should not be shown as a protocol failure: " + eventSnapshot(events));
    }

    @Test
    public void shouldNotifyCookieChangeForSseHandshakeSetCookie() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .addHeader("Set-Cookie", "sse_token=abc123; Path=/")
                .setBody("forbidden"));

        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch cookieChanged = new CountDownLatch(1);
        Runnable cookieListener = cookieChanged::countDown;
        HttpCookieStore.registerCookieChangeListener(cookieListener);
        RealtimeConnectionHandle handle = null;
        try {
            PreparedRequest request = createRequest("GET", serverUrl("/stream"));
            request.headersList.add(new HttpHeader(true, "Accept", "text/event-stream"));
            request.notifyCookieChanges = true;

            handle = httpTransport.openSse(
                    request,
                    new EventSourceListener() {
                        @Override
                        public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                            failed.countDown();
                        }
                    },
                    RealtimeConnectionOptions.defaults()
            );

            assertTrue(failed.await(2, TimeUnit.SECONDS), "SSE handshake should complete with failure");
            assertTrue(cookieChanged.await(2, TimeUnit.SECONDS),
                    "SSE Set-Cookie should notify the cookie manager");
        } finally {
            HttpCookieStore.unregisterCookieChangeListener(cookieListener);
            if (handle != null) {
                handle.cancel();
            }
        }
    }

    @Test
    public void shouldTruncateCapturedSentRequestBodyForNetworkLogSnapshot() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("POST", serverUrl("/large"));
        request.body = "x".repeat(96 * 1024);
        request.headersList.add(new HttpHeader(true, "Content-Type", "text/plain"));
        request.collectBasicInfo = true;
        request.collectEventInfo = true;
        request.enableNetworkLog = true;

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getBody().readUtf8(), request.body);
        assertNotNull(request.sentRequestBody);
        assertFalse(request.sentRequestBody.equals(request.body));
        assertTrue(request.sentRequestBody.contains("Truncated request body"));
        assertTrue(request.sentRequestBody.length() < request.body.length());
    }

    @Test
    public void shouldCaptureSentSnapshotForDetailedRequestsWithoutNetworkLog() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("POST", serverUrl("/details"));
        request.body = "{\"payload\":\"hello\"}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        request.collectBasicInfo = true;
        request.collectEventInfo = true;
        request.enableNetworkLog = false;
        List<NetworkLogEvent> events = Collections.synchronizedList(new ArrayList<>());
        request.networkLogSink = events::add;

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(response.code, 200);
        assertEquals(recordedRequest.getBody().readUtf8(), request.body);
        assertNotNull(request.sentHeadersList);
        assertEquals(request.sentUrl, serverUrl("/details"));
        assertEquals(request.sentMethod, "POST");
        assertEquals(request.sentRequestBody, request.body);
        assertEquals(findHeaderValue(request.sentHeadersList, "Content-Type"), "application/json");
        assertEquals(findHeaderValue(request.sentHeadersList, "Content-Length"),
                String.valueOf(request.body.getBytes(StandardCharsets.UTF_8).length));
        assertTrue(events.isEmpty());
    }

    @Test
    public void shouldNotCaptureSnapshotsOrNetworkLogForMetricsOnlyRequests() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("POST", serverUrl("/metrics-only"));
        request.body = "{\"payload\":\"hello\"}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        request.collectBasicInfo = true;
        request.collectMetricsInfo = true;
        request.collectEventInfo = false;
        request.enableNetworkLog = false;
        List<NetworkLogEvent> events = Collections.synchronizedList(new ArrayList<>());
        request.networkLogSink = events::add;

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(response.code, 200);
        assertEquals(recordedRequest.getBody().readUtf8(), request.body);
        assertEquals(request.sentHeadersList, null);
        assertEquals(request.sentRequestBody, null);
        assertTrue(events.isEmpty());
    }

    @Test
    public void shouldCaptureSentSnapshotAndNetworkLogForWebSocketHandshake() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                webSocket.close(1000, "ok");
            }
        }));

        PreparedRequest request = createRequest("GET", serverUrl("/ws").replaceFirst("^http://", "ws://"));
        request.headersList.add(new HttpHeader(true, "X-Trace", "trace-1"));
        request.collectBasicInfo = true;
        request.collectEventInfo = true;
        request.enableNetworkLog = true;
        List<NetworkLogEvent> events = Collections.synchronizedList(new ArrayList<>());
        request.networkLogSink = events::add;

        CountDownLatch opened = new CountDownLatch(1);
        RealtimeWebSocketConnection connection = httpTransport.openWebSocket(
                request,
                new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                        opened.countDown();
                    }
                },
                RealtimeConnectionOptions.defaults()
        );
        try {
            assertTrue(opened.await(2, TimeUnit.SECONDS), "WebSocket handshake should open");
        } finally {
            connection.close(1000, "test done");
        }

        RecordedRequest recordedRequest = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals(recordedRequest.getMethod(), "GET");
        assertEquals(recordedRequest.getPath(), "/ws");
        assertEquals(recordedRequest.getHeader("X-Trace"), "trace-1");
        assertNotNull(request.sentHeadersList);
        assertEquals(findHeaderValue(request.sentHeadersList, "X-Trace"), "trace-1");
        assertEquals(findHeaderValue(request.sentHeadersList, "Host"), recordedRequest.getHeader("Host"));
        assertNotNull(findHeaderValue(request.sentHeadersList, "Sec-WebSocket-Key"));
        waitForNetworkLogStage(events, NetworkLogEventStage.CALL_START);
        assertEquals(countEvents(events, NetworkLogEventStage.CALL_START), 1,
                "WebSocket should publish a single CallStart event");
        waitForNetworkLogStage(events, NetworkLogEventStage.REQUEST_BODY_START);
        waitForNetworkLogStage(events, NetworkLogEventStage.RESPONSE_HEADERS_END);
        String callStart = firstEventMessage(events, NetworkLogEventStage.CALL_START);
        String requestHeaders = firstEventMessage(events, NetworkLogEventStage.REQUEST_HEADERS_END);
        String requestBody = firstEventMessage(events, NetworkLogEventStage.REQUEST_BODY_START);
        String responseHeaders = firstEventMessage(events, NetworkLogEventStage.RESPONSE_HEADERS_END);
        assertTrue(callStart.contains("WebSocket URL: " + request.url), callStart);
        assertTrue(callStart.contains("Handshake Request: GET http://"), callStart);
        assertTrue(callStart.contains("Upgrade Flow: ws:// -> HTTP/1.1 GET + Upgrade: websocket"), callStart);
        assertTrue(requestHeaders.contains("WebSocket Upgrade Request Headers:"), requestHeaders);
        assertTrue(requestBody.contains("No request body"), requestBody);
        assertTrue(responseHeaders.contains("Result: WebSocket upgrade accepted"), responseHeaders);
        assertTrue(responseHeaders.contains("WebSocket URL: " + request.url), responseHeaders);
        assertTrue(responseHeaders.contains("HTTP Handshake Protocol: http/1.1"), responseHeaders);
        assertTrue(responseHeaders.matches("(?s).*Thread: \\S+.*"), responseHeaders);
        assertTrue(responseHeaders.contains("Connection: "), responseHeaders);
    }

    @Test
    public void shouldNotifyCookieChangeForWebSocketHandshakeSetCookie() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .addHeader("Set-Cookie", "ws_token=abc123; Path=/")
                .withWebSocketUpgrade(new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                        webSocket.close(1000, "ok");
                    }
                }));

        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch cookieChanged = new CountDownLatch(1);
        Runnable cookieListener = cookieChanged::countDown;
        HttpCookieStore.registerCookieChangeListener(cookieListener);
        RealtimeWebSocketConnection connection = null;
        try {
            PreparedRequest request = createRequest("GET", serverUrl("/ws").replaceFirst("^http://", "ws://"));
            request.notifyCookieChanges = true;

            connection = httpTransport.openWebSocket(
                    request,
                    new WebSocketListener() {
                        @Override
                        public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                            opened.countDown();
                        }
                    },
                    RealtimeConnectionOptions.defaults()
            );

            assertTrue(opened.await(2, TimeUnit.SECONDS), "WebSocket handshake should open");
            assertTrue(cookieChanged.await(2, TimeUnit.SECONDS),
                    "WebSocket Set-Cookie should notify the cookie manager");
        } finally {
            HttpCookieStore.unregisterCookieChangeListener(cookieListener);
            if (connection != null) {
                connection.close(1000, "test done");
            }
        }
    }

    @Test
    public void shouldCleanJsonCommentsBeforeSendingApplicationJsonBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("{\"ok\":true}"));

        PreparedRequest request = createRequest("POST", serverUrl("/json5"));
        request.body = "{\n  // comment\n  \"chatId\": 1,\n  \"text\": \"hello\" // trailing comment\n}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json; charset=utf-8"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getBody().readUtf8(), "{\"chatId\":1,\"text\":\"hello\"}");
    }

    @Test
    public void shouldSkipInvalidHeaderNamesWhenBuildingRequest() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("GET", serverUrl("/headers"));
        request.headersList.add(new HttpHeader(true, "Valid-Header", "value"));
        request.headersList.add(new HttpHeader(true, "Bad:Header", "should-skip"));
        request.headersList.add(new HttpHeader(true, "", "also-skip"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getHeader("Valid-Header"), "value");
        assertEquals(recordedRequest.getHeader("Bad:Header"), null);
    }

    @Test
    public void shouldRetryWithDigestAuthorizationAfterChallenge() throws Exception {
        server = createServer();
        String realm = "testrealm@host.com";
        String nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093";
        String opaque = "5ccc069c403ebaf9f0171e9517f40e41";
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) {
                String authorization = recordedRequest.getHeader("Authorization");
                if (authorization == null) {
                    return digestChallenge(realm, nonce, opaque, false).setBody("unauthorized");
                }
                if (isValidDigestAuthorization(recordedRequest, realm, nonce, opaque, "Mufasa", "Circle Of Life")) {
                    return new MockResponse().setResponseCode(200).setBody("ok");
                }
                return new MockResponse().setResponseCode(401).setBody("bad digest");
            }
        });

        PreparedRequest request = createRequest("GET", serverUrl("/digest"));
        request.transportAuth = new TransportAuth(AuthType.DIGEST.getConstant(), "Mufasa", "Circle Of Life");

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest firstRequest = server.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest retryRequest = server.takeRequest(1, TimeUnit.SECONDS);

        assertEquals(response.code, 200);
        assertNotNull(firstRequest);
        assertNotNull(retryRequest);
        assertEquals(firstRequest.getHeader("Authorization"), null);
        String authorization = retryRequest.getHeader("Authorization");
        assertNotNull(authorization);
        assertTrue(authorization.startsWith("Digest "));
        assertTrue(authorization.contains("username=\"Mufasa\""));
        assertTrue(authorization.contains("realm=\"testrealm@host.com\""));
        assertTrue(authorization.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""));
        assertTrue(authorization.contains("uri=\"/digest\""));
        assertTrue(authorization.contains("qop=auth"));
        assertTrue(authorization.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""));
        assertTrue(authorization.contains("response=\""));
    }

    @Test
    public void shouldRetryDigestAuthorizationWhenServerMarksNonceStale() throws Exception {
        server = createServer();
        String realm = "testrealm@host.com";
        String opaque = "stale-opaque";
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) {
                String authorization = recordedRequest.getHeader("Authorization");
                if (authorization == null) {
                    return digestChallenge(realm, "old-nonce", opaque, false).setBody("unauthorized");
                }
                if (authorization.contains("nonce=\"old-nonce\"")
                        && isValidDigestAuthorization(recordedRequest, realm, "old-nonce", opaque, "Mufasa", "Circle Of Life")) {
                    return digestChallenge(realm, "new-nonce", opaque, true).setBody("stale");
                }
                if (isValidDigestAuthorization(recordedRequest, realm, "new-nonce", opaque, "Mufasa", "Circle Of Life")) {
                    return new MockResponse().setResponseCode(200).setBody("ok");
                }
                return new MockResponse().setResponseCode(401).setBody("bad digest");
            }
        });

        PreparedRequest request = createRequest("GET", serverUrl("/digest-stale"));
        request.transportAuth = new TransportAuth(AuthType.DIGEST.getConstant(), "Mufasa", "Circle Of Life");

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest firstRequest = server.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest staleRequest = server.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest refreshedRequest = server.takeRequest(1, TimeUnit.SECONDS);

        assertEquals(response.code, 200);
        assertNotNull(firstRequest);
        assertNotNull(staleRequest);
        assertNotNull(refreshedRequest);
        assertEquals(firstRequest.getHeader("Authorization"), null);
        assertTrue(staleRequest.getHeader("Authorization").contains("nonce=\"old-nonce\""));
        assertTrue(refreshedRequest.getHeader("Authorization").contains("nonce=\"new-nonce\""));
    }

    @Test
    public void shouldNotSendDisabledHeaders() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("GET", serverUrl("/disabled-header"));
        request.headersList.add(new HttpHeader(false, "X-Disabled", "skip-me"));
        request.headersList.add(new HttpHeader(true, "X-Enabled", "keep-me"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getHeader("X-Disabled"), null);
        assertEquals(recordedRequest.getHeader("X-Enabled"), "keep-me");
    }

    @Test
    public void shouldSendDuplicateHeadersAsSeparateValues() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("GET", serverUrl("/duplicate-headers"));
        request.headersList.add(new HttpHeader(true, "X-Trace", "one"));
        request.headersList.add(new HttpHeader(true, "X-Trace", "two"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getHeaders().values("X-Trace"), List.of("one", "two"));
    }

    @Test
    public void shouldSendEmptyBodyForPostWithoutBodyContent() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("POST", serverUrl("/empty-post"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getMethod(), "POST");
        assertEquals(recordedRequest.getBodySize(), 0L);
    }

    @Test
    public void shouldSendFormUrlencodedRequestBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("POST", serverUrl("/form"));
        request.urlencodedList = new ArrayList<>();
        request.urlencodedList.add(new HttpFormUrlencoded(true, "name", "Alice"));
        request.urlencodedList.add(new HttpFormUrlencoded(true, "city", "Shanghai"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getMethod(), "POST");
        assertEquals(recordedRequest.getHeader("Content-Type"), "application/x-www-form-urlencoded");
        assertEquals(recordedRequest.getBody().readUtf8(), "name=Alice&city=Shanghai");
    }

    @Test
    public void shouldSkipDisabledFormUrlencodedFields() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("POST", serverUrl("/form-disabled"));
        request.urlencodedList = new ArrayList<>();
        request.urlencodedList.add(new HttpFormUrlencoded(false, "disabled", "skip"));
        request.urlencodedList.add(new HttpFormUrlencoded(true, "enabled", "keep"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getBody().readUtf8(), "enabled=keep");
    }

    @Test
    public void shouldSendMultipartRequestBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("POST", serverUrl("/multipart"));
        request.isMultipart = true;
        request.formDataList = new ArrayList<>();
        request.formDataList.add(new HttpFormData(true, "field1", HttpFormData.TYPE_TEXT, "value1"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();
        String body = recordedRequest.getBody().readUtf8();

        assertEquals(recordedRequest.getMethod(), "POST");
        assertTrue(recordedRequest.getHeader("Content-Type").startsWith("multipart/form-data; boundary="));
        assertTrue(body.contains("name=\"field1\""));
        assertTrue(body.contains("value1"));
    }

    @Test
    public void shouldSkipDisabledMultipartParts() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("POST", serverUrl("/multipart-disabled"));
        request.isMultipart = true;
        request.formDataList = new ArrayList<>();
        request.formDataList.add(new HttpFormData(false, "hidden", HttpFormData.TYPE_TEXT, "skip"));
        request.formDataList.add(new HttpFormData(true, "visible", HttpFormData.TYPE_TEXT, "keep"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();
        String body = recordedRequest.getBody().readUtf8();

        assertFalse(body.contains("name=\"hidden\""));
        assertTrue(body.contains("name=\"visible\""));
        assertTrue(body.contains("keep"));
    }

    @Test
    public void shouldSendMultipartPlaceholderForMissingFile() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("ok"));

        PreparedRequest request = createRequest("POST", serverUrl("/multipart-file"));
        request.isMultipart = true;
        request.formDataList = new ArrayList<>();
        request.formDataList.add(new HttpFormData(true, "upload", HttpFormData.TYPE_FILE, "/path/does/not/exist.txt"));

        httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();
        String body = recordedRequest.getBody().readUtf8();

        assertTrue(body.contains("name=\"upload\""));
        assertTrue(body.contains("filename=\"\""));
    }

    @Test
    public void shouldDecompressBrotliResponseBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Content-Encoding", "br")
                .setBody(new Buffer().write(Base64.getDecoder().decode("DwOAcG9uZy1icgM="))));

        PreparedRequest request = createRequest("GET", serverUrl("/brotli"));
        request.headersList.add(new HttpHeader(true, "Accept-Encoding", "br"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());

        assertEquals(response.code, 200);
        assertEquals(response.body, "pong-br");
        assertEquals(response.headers.get("Content-Encoding").get(0), "br");
    }

    @Test
    public void shouldKeepMultipleSetCookieHeaders() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "a=1; Path=/")
                .addHeader("Set-Cookie", "b=2; Path=/")
                .setBody("ok"));

        PreparedRequest request = createRequest("GET", serverUrl("/multi-cookie"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());

        assertNotNull(response.headers);
        assertEquals(response.headers.get("Set-Cookie"), List.of("a=1; Path=/", "b=2; Path=/"));
    }

    @Test
    public void shouldDecompressGzipResponseBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Content-Encoding", "gzip")
                .setBody(new Buffer().write(gzip("pong-gzip"))));

        PreparedRequest request = createRequest("GET", serverUrl("/gzip"));
        request.headersList.add(new HttpHeader(true, "Accept-Encoding", "gzip"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());

        assertEquals(response.code, 200);
        assertEquals(response.body, "pong-gzip");
        assertNotNull(response.headers);
        assertEquals(response.headers.get("Content-Encoding").get(0), "gzip");
    }

    @Test
    public void shouldDecompressZlibWrappedDeflateResponseBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Content-Encoding", "deflate")
                .setBody(new Buffer().write(deflate("pong-deflate"))));

        PreparedRequest request = createRequest("GET", serverUrl("/deflate"));
        request.headersList.add(new HttpHeader(true, "Accept-Encoding", "deflate"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());

        assertEquals(response.code, 200);
        assertEquals(response.body, "pong-deflate");
        assertNotNull(response.headers);
        assertEquals(response.headers.get("Content-Encoding").get(0), "deflate");
    }

    @Test
    public void shouldDecompressRawDeflateResponseBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Content-Encoding", "deflate")
                .setBody(new Buffer().write(rawDeflate("pong-raw-deflate"))));

        PreparedRequest request = createRequest("GET", serverUrl("/raw-deflate"));
        request.headersList.add(new HttpHeader(true, "Accept-Encoding", "deflate"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());

        assertEquals(response.code, 200);
        assertEquals(response.body, "pong-raw-deflate");
        assertNotNull(response.headers);
        assertEquals(response.headers.get("Content-Encoding").get(0), "deflate");
    }

    @Test
    public void shouldSendHttpsRequestWithLenientSslVerification() throws Exception {
        server = createHttpsServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("secure-pong"));

        PreparedRequest request = createRequest("GET", serverUrl("/secure-ping"));
        request.sslVerificationEnabled = false;
        request.headersList.add(new HttpHeader(true, "Accept", "*/*"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(response.code, 200);
        assertEquals(response.body, "secure-pong");
        assertNotNull(recordedRequest.getHandshake());
        assertTrue(recordedRequest.getRequestUrl().isHttps());
    }

    @Test
    public void shouldUseHttp2WhenRequestedAndServerSupportsIt() throws Exception {
        server = createHttpsServer(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("h2-ok"));

        PreparedRequest request = createRequest("GET", serverUrl("/h2"));
        request.sslVerificationEnabled = false;
        request.httpVersion = HttpRequestItem.HTTP_VERSION_HTTP_2;

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(response.code, 200);
        assertEquals(response.protocol, Protocol.HTTP_2.toString());
        assertNotNull(recordedRequest.getHandshake());
    }

    @Test
    public void shouldUseHttp11WhenRequested() throws Exception {
        server = createHttpsServer(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("h1-ok"));

        PreparedRequest request = createRequest("GET", serverUrl("/h1"));
        request.sslVerificationEnabled = false;
        request.httpVersion = HttpRequestItem.HTTP_VERSION_HTTP_1_1;

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(response.code, 200);
        assertEquals(response.protocol, Protocol.HTTP_1_1.toString());
        assertNotNull(recordedRequest.getHandshake());
    }

    @Test
    public void shouldFailHttpsRequestWhenStrictSslVerificationRejectsSelfSignedCertificate() throws Exception {
        server = createHttpsServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("should-not-reach"));

        PreparedRequest request = createRequest("GET", serverUrl("/strict-secure-ping"));
        request.sslVerificationEnabled = true;

        Exception exception = expectThrows(Exception.class,
                () -> httpTransport.execute(request, HttpExchangeOptions.defaults()));

        Throwable handshakeFailure = exception instanceof SSLHandshakeException
                ? exception
                : Arrays.stream(exception.getSuppressed())
                .filter(SSLHandshakeException.class::isInstance)
                .findFirst()
                .orElse(null);
        assertNotNull(handshakeFailure,
                "Strict SSL failure should remain visible when Happy Eyeballs aggregates address failures: "
                        + exception);
        assertNotNull(handshakeFailure.getMessage());
        assertFalse(handshakeFailure.getMessage().isBlank());
        assertEquals(server.getRequestCount(), 0);
    }

    @Test
    public void shouldHandleHeadResponseWithoutBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("Content-Length", "4"));

        PreparedRequest request = createRequest("HEAD", serverUrl("/head"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getMethod(), "HEAD");
        assertEquals(response.code, 200);
        assertEquals(response.body, "");
        assertEquals(response.bodySize, 0L);
    }

    @Test
    public void shouldHandle204ResponseWithoutBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(204)
                .addHeader("Content-Type", "text/plain; charset=utf-8"));

        PreparedRequest request = createRequest("GET", serverUrl("/no-content"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());

        assertEquals(response.code, 204);
        assertEquals(response.body, "");
        assertEquals(response.bodySize, 0L);
    }

    @Test
    public void shouldHandle304ResponseWithoutBody() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(304)
                .addHeader("ETag", "\"etag-1\""));

        PreparedRequest request = createRequest("GET", serverUrl("/not-modified"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());

        assertEquals(response.code, 304);
        assertEquals(response.body, "");
        assertEquals(response.bodySize, 0L);
        assertEquals(response.headers.get("ETag"), List.of("\"etag-1\""));
    }

    @Test
    public void shouldThrowWhenServerClosesConnectionBeforeSendingResponseHeaders() throws Exception {
        server = createServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);
            }
        });

        PreparedRequest request = createRequest("POST", serverUrl("/api/system/ping"));
        request.body = "{\"chatId\":1,\"text\":\"hello\"}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json; charset=utf-8"));
        request.headersList.add(new HttpHeader(true, "Accept", "*/*"));
        request.headersList.add(new HttpHeader(true, "Connection", "keep-alive"));

        IOException exception = expectThrows(IOException.class, () -> httpTransport.execute(request, HttpExchangeOptions.defaults()));
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(recordedRequest.getMethod(), "POST");
        assertEquals(recordedRequest.getBody().readUtf8(), request.body);
        assertNotNull(exception.getMessage());
        assertFalse(exception.getMessage().isBlank());
    }

    @Test
    public void shouldReturnRedirectResponseWithoutFollowingWhenDisabled() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(302)
                .addHeader("Location", serverUrl("/target"))
                .setBody("redirect"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("target"));

        PreparedRequest request = createRequest("GET", serverUrl("/start"));
        request.followRedirects = false;

        HttpResponse response = new HttpRedirectExecutor().executeWithRedirects(request, 10, null);
        RecordedRequest recordedRequest = takeRecordedRequest();

        assertEquals(response.code, 302);
        assertEquals(recordedRequest.getPath(), "/start");
        assertEquals(server.getRequestCount(), 1);
    }

    @Test
    public void shouldPublishNetworkLogEventsThroughInjectedSink() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(302)
                .addHeader("Location", serverUrl("/target"))
                .setBody("redirect"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("target"));

        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = createRequest("POST", serverUrl("/start"));
        request.body = "{\"hello\":\"world\"}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        request.followRedirects = true;
        request.enableNetworkLog = true;
        request.networkLogSink = events::add;

        HttpResponse response = new HttpRedirectExecutor().executeWithRedirects(request, 10, null);
        RecordedRequest startRequest = takeRecordedRequest();
        RecordedRequest targetRequest = takeRecordedRequest();

        assertEquals(response.code, 200);
        assertEquals(startRequest.getPath(), "/start");
        assertEquals(startRequest.getMethod(), "POST");
        assertEquals(targetRequest.getPath(), "/target");
        assertEquals(targetRequest.getMethod(), "GET");
        assertEquals(request.sentUrl, serverUrl("/target"));
        assertEquals(request.sentMethod, "GET");
        assertNotNull(request.exchangeEventInfo);
        assertEquals(server.getRequestCount(), 2,
                "HttpRedirectExecutor should own the redirect loop instead of letting OkHttp follow internally");
        assertTrue(events.stream().anyMatch(event -> event.stage() == NetworkLogEventStage.REDIRECT),
                "HttpRedirectExecutor should publish redirect events through the injected sink");
        String redirectLog = firstEventMessage(events, NetworkLogEventStage.REDIRECT);
        assertTrue(redirectLog.contains("Redirect #1"), redirectLog);
        assertTrue(redirectLog.contains("Status: 302"), redirectLog);
        assertTrue(redirectLog.contains("From: POST " + serverUrl("/start")), redirectLog);
        assertTrue(redirectLog.contains("To: GET " + serverUrl("/target")), redirectLog);
        assertTrue(redirectLog.contains("Cross-Origin: false"), redirectLog);
        assertTrue(redirectLog.contains("Method Changed: true"), redirectLog);
        assertTrue(events.stream().anyMatch(event -> event.stage() == NetworkLogEventStage.CALL_START),
                "OkHttp event listener should publish call events through the injected sink");
    }

    @Test
    public void shouldPreserveReplayableRequestBodySnapshotAfter307Redirect() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(307)
                .addHeader("Location", serverUrl("/target"))
                .setBody("redirect"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("target"));

        PreparedRequest request = createRequest("POST", serverUrl("/start"));
        request.body = "{\"hello\":\"world\"}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json; charset=utf-8"));
        request.followRedirects = true;
        request.enableNetworkLog = true;

        HttpResponse response = new HttpRedirectExecutor().executeWithRedirects(request, 10, null);
        RecordedRequest startRequest = takeRecordedRequest();
        RecordedRequest targetRequest = takeRecordedRequest();

        assertEquals(response.code, 200);
        assertEquals(startRequest.getMethod(), "POST");
        assertEquals(targetRequest.getMethod(), "POST");
        assertEquals(targetRequest.getBody().readUtf8(), request.body);
        assertEquals(request.sentUrl, serverUrl("/target"));
        assertEquals(request.sentMethod, "POST");
        assertEquals(request.sentRequestBody, request.body);
        assertTrue(request.sentRequestBodyReplayable,
                "Final 307/308 body snapshot should remain exportable from the original request");
        String actualCurl = CurlParser.toActualCurl(request);
        assertTrue(actualCurl.contains("--data-raw '{\"hello\":\"world\"}'"), actualCurl);
    }

    @Test
    public void shouldTransformPostToGetOn302Redirect() throws Exception {
        PreparedRequest request = createRequest("POST", "http://127.0.0.1/start");
        request.body = "{\"hello\":\"world\"}";
        request.isMultipart = true;
        request.formDataList = new ArrayList<>();
        request.urlencodedList = new ArrayList<>();
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json; charset=utf-8"));

        PreparedRequest redirectedRequest = HttpRedirectExecutor.prepareRedirectRequest(request, "http://127.0.0.1/redirected", 302, false);

        assertEquals(redirectedRequest.method, "GET");
        assertEquals(redirectedRequest.url, "http://127.0.0.1/redirected");
        assertEquals(redirectedRequest.body, null);
        assertFalse(redirectedRequest.isMultipart);
        assertEquals(redirectedRequest.formDataList, null);
        assertEquals(redirectedRequest.urlencodedList, null);
        assertEquals(findHeaderValue(redirectedRequest.headersList, "Content-Type"), null);
    }

    @Test
    public void shouldPreserveHeadMethodOn302Redirect() throws Exception {
        PreparedRequest request = createRequest("HEAD", "http://127.0.0.1/start");
        request.body = "ignored";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json; charset=utf-8"));

        PreparedRequest redirectedRequest = HttpRedirectExecutor.prepareRedirectRequest(request, "http://127.0.0.1/redirected", 302, false);

        assertEquals(redirectedRequest.method, "HEAD");
        assertEquals(redirectedRequest.url, "http://127.0.0.1/redirected");
        assertEquals(redirectedRequest.body, null);
        assertEquals(findHeaderValue(redirectedRequest.headersList, "Content-Type"), null);
    }

    @Test
    public void shouldPreserveMethodBodyAndContentTypeOn307Redirect() throws Exception {
        PreparedRequest request = createRequest("POST", "http://127.0.0.1/start");
        request.body = "{\"hello\":\"world\"}";
        request.headersList.add(new HttpHeader(true, "Content-Type", "application/json; charset=utf-8"));
        request.headersList.add(new HttpHeader(true, "Authorization", "Bearer token"));

        PreparedRequest redirectedRequest = HttpRedirectExecutor.prepareRedirectRequest(request, "http://127.0.0.1/upload", 307, false);

        assertEquals(redirectedRequest.method, "POST");
        assertEquals(redirectedRequest.url, "http://127.0.0.1/upload");
        assertEquals(redirectedRequest.body, request.body);
        assertEquals(findHeaderValue(redirectedRequest.headersList, "Content-Type"), "application/json; charset=utf-8");
        assertEquals(findHeaderValue(redirectedRequest.headersList, "Authorization"), "Bearer token");
    }

    @Test
    public void shouldRemoveMultipartContentTypeOn307Redirect() throws Exception {
        PreparedRequest request = createRequest("POST", "http://127.0.0.1/start");
        request.isMultipart = true;
        request.formDataList = new ArrayList<>();
        request.formDataList.add(new HttpFormData(true, "file", HttpFormData.TYPE_TEXT, "value"));
        request.headersList.add(new HttpHeader(true, "Content-Type", "multipart/form-data"));

        PreparedRequest redirectedRequest = HttpRedirectExecutor.prepareRedirectRequest(request, "http://127.0.0.1/upload", 307, false);

        assertTrue(redirectedRequest.isMultipart);
        assertEquals(findHeaderValue(redirectedRequest.headersList, "Content-Type"), null);
    }

    @Test
    public void shouldRemoveMultipartContentTypeOn308Redirect() throws Exception {
        PreparedRequest request = createRequest("POST", "http://127.0.0.1/start");
        request.isMultipart = true;
        request.formDataList = new ArrayList<>();
        request.formDataList.add(new HttpFormData(true, "file", HttpFormData.TYPE_TEXT, "value"));
        request.headersList.add(new HttpHeader(true, "Content-Type", "multipart/form-data"));

        PreparedRequest redirectedRequest = HttpRedirectExecutor.prepareRedirectRequest(request, "http://127.0.0.1/upload", 308, false);

        assertTrue(redirectedRequest.isMultipart);
        assertEquals(findHeaderValue(redirectedRequest.headersList, "Content-Type"), null);
    }

    @Test
    public void shouldStripAuthorizationAndCookieOnCrossDomainRedirect() throws Exception {
        List<HttpHeader> headers = new ArrayList<>();
        headers.add(new HttpHeader(true, "Authorization", "Bearer token"));
        headers.add(new HttpHeader(true, "Cookie", "session=abc"));
        headers.add(new HttpHeader(true, "Content-Type", "application/json"));
        headers.add(new HttpHeader(true, "Host", "127.0.0.1"));

        @SuppressWarnings("unchecked")
        List<HttpHeader> cleanedHeaders = HttpRedirectExecutor.cleanHeadersList(headers, true, false, false);

        assertEquals(findHeaderValue(cleanedHeaders, "Authorization"), null);
        assertEquals(findHeaderValue(cleanedHeaders, "Cookie"), null);
        assertEquals(findHeaderValue(cleanedHeaders, "Content-Type"), null);
        assertEquals(findHeaderValue(cleanedHeaders, "Host"), null);
    }

    @Test
    public void shouldPreserveSensitiveHeadersOnSameOriginRedirect() throws Exception {
        List<HttpHeader> headers = new ArrayList<>();
        headers.add(new HttpHeader(true, "Authorization", "Bearer token"));
        headers.add(new HttpHeader(true, "Cookie", "session=abc"));
        headers.add(new HttpHeader(true, "Content-Type", "application/json"));

        @SuppressWarnings("unchecked")
        List<HttpHeader> cleanedHeaders = HttpRedirectExecutor.cleanHeadersList(headers, false, true, false);

        assertEquals(findHeaderValue(cleanedHeaders, "Authorization"), "Bearer token");
        assertEquals(findHeaderValue(cleanedHeaders, "Cookie"), "session=abc");
        assertEquals(findHeaderValue(cleanedHeaders, "Content-Type"), "application/json");
    }

    @Test
    public void shouldRemoveMultipartContentTypeWhenPreservingBody() throws Exception {
        List<HttpHeader> headers = new ArrayList<>();
        headers.add(new HttpHeader(true, "Content-Type", "multipart/form-data"));
        headers.add(new HttpHeader(true, "Authorization", "Bearer token"));

        @SuppressWarnings("unchecked")
        List<HttpHeader> cleanedHeaders = HttpRedirectExecutor.cleanHeadersList(headers, false, true, true);

        assertEquals(findHeaderValue(cleanedHeaders, "Content-Type"), null);
        assertEquals(findHeaderValue(cleanedHeaders, "Authorization"), "Bearer token");
    }

    @Test
    public void shouldTreatSchemeChangeAsCrossOriginRedirect() throws Exception {
        assertTrue(HttpRedirectExecutor.isCrossOrigin(new URL("http://example.com/api"), new URL("https://example.com/api")));
    }

    @Test
    public void shouldTreatPortChangeAsCrossOriginRedirect() throws Exception {
        assertTrue(HttpRedirectExecutor.isCrossOrigin(new URL("https://example.com/api"), new URL("https://example.com:8443/api")));
    }

    @Test
    public void shouldPersistAndSendCookiesWhenCookieJarEnabled() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "session=abc123; Path=/")
                .setBody("cookie-set"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("cookie-echo"));

        PreparedRequest firstRequest = createRequest("GET", serverUrl("/cookie/set"));
        firstRequest.cookieJarEnabled = true;
        PreparedRequest secondRequest = createRequest("GET", serverUrl("/cookie/check"));
        secondRequest.cookieJarEnabled = true;

        httpTransport.execute(firstRequest, HttpExchangeOptions.defaults());
        httpTransport.execute(secondRequest, HttpExchangeOptions.defaults());
        takeRecordedRequest();
        RecordedRequest cookieRequest = takeRecordedRequest();

        assertEquals(cookieRequest.getHeader("Cookie"), "session=abc123");
    }

    @Test
    public void shouldNotSendCookiesWhenCookieJarDisabled() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "session=abc123; Path=/")
                .setBody("cookie-set"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("cookie-check"));

        PreparedRequest firstRequest = createRequest("GET", serverUrl("/cookie/set"));
        firstRequest.cookieJarEnabled = true;
        PreparedRequest secondRequest = createRequest("GET", serverUrl("/cookie/check"));
        secondRequest.cookieJarEnabled = false;

        httpTransport.execute(firstRequest, HttpExchangeOptions.defaults());
        httpTransport.execute(secondRequest, HttpExchangeOptions.defaults());
        takeRecordedRequest();
        RecordedRequest cookieRequest = takeRecordedRequest();

        assertEquals(cookieRequest.getHeader("Cookie"), null);
    }

    @Test
    public void shouldMergeCookieJarWithExplicitCookieHeaderForLoggedRequests() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "__cf_bm=jar-value; Path=/")
                .addHeader("Set-Cookie", "_cfuvid=jar-fuvid; Path=/")
                .setBody("cookie-set"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("cookie-check"));

        PreparedRequest firstRequest = createRequest("POST", serverUrl("/cookie/set"));
        firstRequest.body = "{}";
        firstRequest.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        firstRequest.headersList.add(new HttpHeader(true, "Cookie", "browser_session=keep; __cf_bm=old-value"));
        firstRequest.enableNetworkLog = true;

        PreparedRequest secondRequest = createRequest("POST", serverUrl("/cookie/check"));
        secondRequest.body = "{}";
        secondRequest.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        secondRequest.headersList.add(new HttpHeader(true, "Cookie", "browser_session=keep; __cf_bm=old-value"));
        secondRequest.enableNetworkLog = true;
        List<NetworkLogEvent> secondRequestEvents = Collections.synchronizedList(new ArrayList<>());
        secondRequest.networkLogSink = secondRequestEvents::add;

        httpTransport.execute(firstRequest, HttpExchangeOptions.defaults());
        httpTransport.execute(secondRequest, HttpExchangeOptions.defaults());
        takeRecordedRequest();
        RecordedRequest cookieRequest = takeRecordedRequest();

        String cookie = cookieRequest.getHeader("Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("browser_session=keep"));
        assertTrue(cookie.contains("__cf_bm=old-value"));
        assertTrue(cookie.contains("_cfuvid=jar-fuvid"));
        assertFalse(cookie.contains("__cf_bm=jar-value"));
        assertEquals(findHeaderValue(secondRequest.sentHeadersList, "Cookie"), cookie);
        String loggedHeaders = firstEventMessage(secondRequestEvents, NetworkLogEventStage.REQUEST_HEADERS_END);
        assertTrue(loggedHeaders.contains("Cookie: " + cookie), loggedHeaders);
    }

    @Test
    public void shouldMergeCookieJarWithExplicitCookieHeaderWhenNetworkLogDisabled() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "__cf_bm=jar-value; Path=/")
                .addHeader("Set-Cookie", "_cfuvid=jar-fuvid; Path=/")
                .setBody("cookie-set"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("cookie-check"));

        PreparedRequest firstRequest = createRequest("POST", serverUrl("/cookie/set"));
        firstRequest.body = "{}";
        firstRequest.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        firstRequest.headersList.add(new HttpHeader(true, "Cookie", "browser_session=keep; __cf_bm=old-value"));
        firstRequest.enableNetworkLog = false;

        PreparedRequest secondRequest = createRequest("POST", serverUrl("/cookie/check"));
        secondRequest.body = "{}";
        secondRequest.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        secondRequest.headersList.add(new HttpHeader(true, "Cookie", "browser_session=keep; __cf_bm=old-value"));
        secondRequest.enableNetworkLog = false;

        httpTransport.execute(firstRequest, HttpExchangeOptions.defaults());
        httpTransport.execute(secondRequest, HttpExchangeOptions.defaults());
        takeRecordedRequest();
        RecordedRequest cookieRequest = takeRecordedRequest();

        String cookie = cookieRequest.getHeader("Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("browser_session=keep"));
        assertTrue(cookie.contains("__cf_bm=old-value"));
        assertTrue(cookie.contains("_cfuvid=jar-fuvid"));
        assertFalse(cookie.contains("__cf_bm=jar-value"));
    }

    @Test
    public void shouldPreserveDuplicateCookieNamesFromCookieJarWhenMergingExplicitCookieHeader() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "jar_token=abc123; Path=/")
                .setBody("cookie-set"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("cookie-check"));

        PreparedRequest firstRequest = createRequest("GET", serverUrl("/api/set"));
        firstRequest.cookieJarEnabled = true;

        PreparedRequest secondRequest = createRequest("GET", serverUrl("/api/check"));
        secondRequest.cookieJarEnabled = true;
        secondRequest.headersList.add(new HttpHeader(true, "Cookie",
                "browser_session=keep; theme=root; theme=api"));

        httpTransport.execute(firstRequest, HttpExchangeOptions.defaults());
        httpTransport.execute(secondRequest, HttpExchangeOptions.defaults());
        takeRecordedRequest();
        RecordedRequest cookieRequest = takeRecordedRequest();

        String cookie = cookieRequest.getHeader("Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("browser_session=keep"));
        assertTrue(cookie.contains("theme=root"));
        assertTrue(cookie.contains("theme=api"));
        assertTrue(cookie.contains("jar_token=abc123"));
        assertEquals(countOccurrences(cookie, "theme="), 2);
    }

    @Test
    public void shouldSendCurlImportedCookieHeaderWithoutCookieJarOverride() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "session=jar; Path=/")
                .setBody("cookie-set"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("cookie-check"));

        PreparedRequest firstRequest = createRequest("GET", serverUrl("/cookie/set"));
        firstRequest.cookieJarEnabled = true;

        httpTransport.execute(firstRequest, HttpExchangeOptions.defaults());
        takeRecordedRequest();

        String curl = "curl '" + serverUrl("/cookie/check") + "' "
                + "-b 'session=curl; browser=1'";
        PreparedRequest curlRequest = PreparedRequestFactory.buildWithoutInheritance(CurlImportUtil.fromCurl(curl));

        httpTransport.execute(curlRequest, HttpExchangeOptions.defaults());
        RecordedRequest cookieRequest = takeRecordedRequest();

        assertEquals(cookieRequest.getHeader("Cookie"), "session=curl; browser=1");
    }

    @Test
    public void shouldRespectRequestTimeoutWhenServerDoesNotRespond() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        PreparedRequest request = createRequest("GET", serverUrl("/hang"));
        request.requestTimeoutMs = 200;

        InterruptedIOException exception = expectThrows(InterruptedIOException.class, () -> httpTransport.execute(request, HttpExchangeOptions.defaults()));

        assertNotNull(exception.getMessage());
        assertFalse(exception.getMessage().isBlank());
    }

    @Test
    public void shouldMarkIncompleteTextResponseWhenConnectionDropsDuringBodyRead() throws Exception {
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody("partial-body")
                .throttleBody(1, 1, TimeUnit.MILLISECONDS)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

        PreparedRequest request = createRequest("GET", serverUrl("/partial"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());

        assertEquals(response.code, 200);
        assertEquals(response.body, I18nUtil.getMessage(MessageKeys.RESPONSE_INCOMPLETE, "unexpected end of stream"));
        assertEquals(response.bodySize, 0L);
        assertEquals(response.filePath, null);
    }

    @Test
    public void shouldMarkIncompleteBinaryResponseWhenConnectionDropsDuringBodyRead() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new SkipException("Binary download path requires a graphics environment");
        }
        server = createServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/octet-stream")
                .setBody(new Buffer().writeUtf8("partial-binary"))
                .throttleBody(1, 1, TimeUnit.MILLISECONDS)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

        PreparedRequest request = createRequest("GET", serverUrl("/partial-binary"));

        HttpResponse response = httpTransport.execute(request, HttpExchangeOptions.defaults());

        assertEquals(response.code, 200);
        assertEquals(response.body, I18nUtil.getMessage(MessageKeys.RESPONSE_INCOMPLETE, "unexpected end of stream"));
        assertEquals(response.bodySize, 0L);
        assertEquals(response.filePath, null);
    }

    private MockWebServer createServer() throws IOException {
        MockWebServer created = new MockWebServer();
        created.start();
        return created;
    }

    private MockWebServer createHttpsServer() throws IOException {
        return createHttpsServer(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1));
    }

    private MockWebServer createHttpsServer(List<Protocol> protocols) throws IOException {
        // okhttp-tls is only test infrastructure here: it lets the test build a throwaway
        // self-signed certificate so MockWebServer can speak real HTTPS on localhost.
        HeldCertificate localhostCertificate = new HeldCertificate.Builder()
                .addSubjectAlternativeName("localhost")
                .addSubjectAlternativeName("127.0.0.1")
                .commonName("localhost")
                .build();
        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(localhostCertificate)
                .build();
        MockWebServer created = new MockWebServer();
        created.setProtocols(protocols);
        created.useHttps(serverCertificates.sslSocketFactory(), false);
        created.start();
        HttpUrl serverUrl = created.url("/");
        assertTrue(serverUrl.isHttps());
        return created;
    }

    private String serverUrl(String path) {
        return server.url(path).toString();
    }

    private RecordedRequest takeRecordedRequest() throws InterruptedException {
        RecordedRequest recordedRequest = server.takeRequest(RECORDED_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(recordedRequest, "Expected MockWebServer to receive a request within "
                + RECORDED_REQUEST_TIMEOUT_SECONDS + " seconds");
        return recordedRequest;
    }

    private PreparedRequest createRequest(String method, String url) {
        PreparedRequest request = new PreparedRequest();
        request.method = method;
        request.url = url;
        request.headersList = new ArrayList<>();
        request.collectBasicInfo = false;
        request.collectEventInfo = false;
        request.enableNetworkLog = false;
        return request;
    }

    private byte[] gzip(String value) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    private byte[] deflate(String value) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(baos)) {
            deflate.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    private byte[] rawDeflate(String value) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        Deflater rawDeflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(baos, rawDeflater)) {
            deflate.write(value.getBytes(StandardCharsets.UTF_8));
        } finally {
            rawDeflater.end();
        }
        return baos.toByteArray();
    }

    private String findHeaderValue(List<HttpHeader> headers, String key) {
        for (HttpHeader header : headers) {
            if (header.getKey() != null && header.getKey().equalsIgnoreCase(key)) {
                return header.getValue();
            }
        }
        return null;
    }

    private void waitForNetworkLogStage(List<NetworkLogEvent> events,
                                        NetworkLogEventStage expectedStage) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (eventSnapshot(events).stream().anyMatch(event -> event.stage() == expectedStage)) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private String firstEventMessage(List<NetworkLogEvent> events, NetworkLogEventStage expectedStage) {
        return eventSnapshot(events).stream()
                .filter(event -> event.stage() == expectedStage)
                .map(NetworkLogEvent::message)
                .findFirst()
                .orElse("");
    }

    private NetworkLogEvent firstEvent(List<NetworkLogEvent> events, NetworkLogEventStage expectedStage) {
        return eventSnapshot(events).stream()
                .filter(event -> event.stage() == expectedStage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing event " + expectedStage + ": " + eventSnapshot(events)));
    }

    private void assertDurationRecorded(NetworkLogEvent event) throws Exception {
        Object duration = NetworkLogEvent.class.getMethod("durationMs").invoke(event);
        assertTrue(duration instanceof Long && (Long) duration >= 0,
                "Network log event should include phase duration: " + event);
    }

    private int firstEventIndex(List<NetworkLogEvent> events, NetworkLogEventStage expectedStage) {
        List<NetworkLogEvent> snapshot = eventSnapshot(events);
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).stage() == expectedStage) {
                return i;
            }
        }
        return -1;
    }

    private long countEvents(List<NetworkLogEvent> events, NetworkLogEventStage expectedStage) {
        return eventSnapshot(events).stream()
                .filter(event -> event.stage() == expectedStage)
                .count();
    }

    private List<NetworkLogEvent> eventSnapshot(List<NetworkLogEvent> events) {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    private int countOccurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }

    private MockResponse digestChallenge(String realm, String nonce, String opaque, boolean stale) {
        String challenge = "Digest realm=\"" + realm + "\", " +
                "qop=\"auth\", " +
                "nonce=\"" + nonce + "\", " +
                "opaque=\"" + opaque + "\"";
        if (stale) {
            challenge += ", stale=true";
        }
        return new MockResponse()
                .setResponseCode(401)
                .addHeader("WWW-Authenticate", challenge);
    }

    private boolean isValidDigestAuthorization(RecordedRequest request,
                                               String realm,
                                               String nonce,
                                               String opaque,
                                               String username,
                                               String password) {
        Map<String, String> attributes = parseDigestAuthorization(request.getHeader("Authorization"));
        if (!username.equals(attributes.get("username"))
                || !realm.equals(attributes.get("realm"))
                || !nonce.equals(attributes.get("nonce"))
                || !opaque.equals(attributes.get("opaque"))
                || !"auth".equals(attributes.get("qop"))
                || request.getPath() == null
                || !request.getPath().equals(attributes.get("uri"))) {
            return false;
        }

        String nc = attributes.get("nc");
        String cnonce = attributes.get("cnonce");
        if (nc == null || cnonce == null) {
            return false;
        }

        String ha1 = md5Hex(username + ":" + realm + ":" + password);
        String ha2 = md5Hex(request.getMethod() + ":" + attributes.get("uri"));
        String expected = md5Hex(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2);
        return expected.equals(attributes.get("response"));
    }

    private Map<String, String> parseDigestAuthorization(String authorization) {
        Map<String, String> attributes = new HashMap<>();
        if (authorization == null || !authorization.regionMatches(true, 0, "Digest ", 0, "Digest ".length())) {
            return attributes;
        }

        for (String token : authorization.substring("Digest ".length()).split(", ")) {
            String[] pair = token.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String value = pair[1];
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            attributes.put(pair[0], value);
        }
        return attributes;
    }

    private String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
