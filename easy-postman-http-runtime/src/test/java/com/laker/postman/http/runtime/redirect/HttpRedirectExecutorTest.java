package com.laker.postman.http.runtime.redirect;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.HttpCaptureProfiles;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.NetworkLogEvent;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.observation.NetworkLogSupport;
import com.laker.postman.http.runtime.transport.HttpCallTracker;
import com.laker.postman.http.runtime.transport.HttpExchangeOptions;
import com.laker.postman.http.runtime.transport.HttpTransport;
import com.laker.postman.http.runtime.transport.RealtimeConnectionHandle;
import com.laker.postman.http.runtime.transport.RealtimeConnectionOptions;
import com.laker.postman.http.runtime.transport.RealtimeWebSocketConnection;
import com.laker.postman.request.model.HttpHeader;
import okhttp3.Call;
import okhttp3.WebSocketListener;
import okhttp3.sse.EventSourceListener;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class HttpRedirectExecutorTest {

    @Test
    public void shouldPassCallTrackerToUnderlyingHttpTransport() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        HttpRedirectExecutor executor = new HttpRedirectExecutor(transport);
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://example.test/no-redirect";
        HttpCallTracker tracker = new HttpCallTracker() {
            @Override
            public void onCallStarted(Call call) {
            }
        };

        executor.executeWithRedirects(request, 0, null, tracker);

        assertSame(transport.options.resolvedCallTracker(), tracker);
    }

    @Test
    public void shouldPreserveCallerCaptureProfileOnWorkingRequest() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        HttpRedirectExecutor executor = new HttpRedirectExecutor(transport);
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://example.test/no-redirect";
        HttpCaptureProfiles.apply(request, HttpCaptureProfile.PERFORMANCE_METRICS);

        executor.executeWithRedirects(request, 0, null);

        assertSame(transport.request.captureProfile, HttpCaptureProfile.PERFORMANCE_METRICS);
        assertTrue(transport.request.collectMetricsInfo);
        assertFalse(transport.request.collectBasicInfo);
        assertFalse(transport.request.collectEventInfo);
        assertFalse(transport.request.enableNetworkLog);
    }

    @Test
    public void shouldPreserveCallerNetworkLogProfileOnWorkingRequest() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        HttpRedirectExecutor executor = new HttpRedirectExecutor(transport);
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://example.test/no-redirect";
        HttpCaptureProfiles.apply(request, HttpCaptureProfile.COLLECTION_DIAGNOSTIC);

        executor.executeWithRedirects(request, 0, null);

        assertSame(transport.request.captureProfile, HttpCaptureProfile.COLLECTION_DIAGNOSTIC);
        assertTrue(transport.request.collectBasicInfo);
        assertTrue(transport.request.collectEventInfo);
        assertTrue(transport.request.enableNetworkLog);
    }

    @Test
    public void shouldResolveCaseInsensitiveAbsoluteAndProtocolRelativeLocations() throws Exception {
        RedirectingTransport transport = new RedirectingTransport(
                response(302, "HTTPS://example.test:8443/absolute"),
                response(302, "//example.test/relative"),
                response(200, null)
        );
        HttpRedirectExecutor executor = new HttpRedirectExecutor(transport);
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://origin.test/api/start";
        request.followRedirects = true;

        executor.executeWithRedirects(request, 5, null);

        assertEquals(transport.urls,
                java.util.List.of(
                        "https://origin.test/api/start",
                        "https://example.test:8443/absolute",
                        "https://example.test/relative"
                ));
    }

    @Test
    public void shouldPreserveLenientLocationCompatibility() throws Exception {
        RedirectingTransport transport = new RedirectingTransport(
                response(302, "/next path?q=hello world"),
                response(200, null)
        );
        HttpRedirectExecutor executor = new HttpRedirectExecutor(transport);
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://origin.test/api/start";
        request.followRedirects = true;

        executor.executeWithRedirects(request, 5, null);

        assertEquals(transport.urls, java.util.List.of(
                "https://origin.test/api/start",
                "https://origin.test/next path?q=hello world"
        ));
    }

    @Test
    public void shouldPreservePutMethodAndBodyOn302Redirect() {
        PreparedRequest request = new PreparedRequest();
        request.method = "PUT";
        request.body = "payload";

        PreparedRequest redirected = HttpRedirectExecutor.prepareRedirectRequest(
                request, "https://example.test/next", 302, false);

        assertEquals(redirected.method, "PUT");
        assertEquals(redirected.body, "payload");
    }

    @Test
    public void shouldTransformPostToGetOn301AndAnyNonHeadMethodOn303() {
        PreparedRequest post = new PreparedRequest();
        post.method = "POST";
        post.body = "payload";
        PreparedRequest delete = new PreparedRequest();
        delete.method = "DELETE";
        delete.body = "payload";

        PreparedRequest moved = HttpRedirectExecutor.prepareRedirectRequest(
                post, "https://example.test/moved", 301, false);
        PreparedRequest seeOther = HttpRedirectExecutor.prepareRedirectRequest(
                delete, "https://example.test/result", 303, false);

        assertEquals(moved.method, "GET");
        assertEquals(moved.body, null);
        assertEquals(seeOther.method, "GET");
        assertEquals(seeOther.body, null);
    }

    @Test
    public void shouldNotFollow304EvenWhenLocationIsPresent() throws Exception {
        RedirectingTransport transport = new RedirectingTransport(response(304, "https://example.test/not-modified"));
        HttpRedirectExecutor executor = new HttpRedirectExecutor(transport);
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://origin.test/resource";
        request.followRedirects = true;

        HttpResponse response = executor.executeWithRedirects(request, 5, null);

        assertEquals(response.code, 304);
        assertEquals(transport.urls, java.util.List.of("https://origin.test/resource"));
    }

    @Test
    public void shouldLogCompactRedirectsAndOnlyChangedProxyConfiguration() throws Exception {
        RedirectingTransport transport = new RedirectingTransport(
                response(302, "/middle"), response(302, "/final"), response(200, null))
                .withProxyMessages("Proxy: DIRECT", "Proxy: DIRECT", "Proxy: HTTP proxy.test:8080")
                .withHeaderMessages("Header snapshot:\nCookie: session=a", "Header snapshot:\nCookie: session=a",
                        "Header snapshot:\nCookie: session=b");
        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = diagnosticRequest("https://origin.test/start", events);

        HttpResponse response = new HttpRedirectExecutor(transport).executeWithRedirects(request, 5, null);

        assertEquals(response.code, 200);
        assertEquals(transport.urls, List.of(
                "https://origin.test/start", "https://origin.test/middle", "https://origin.test/final"));
        List<String> redirects = eventMessages(events, NetworkLogEventStage.REDIRECT).stream().map(String::strip).toList();
        assertEquals(redirects, List.of(
                "Redirect #1\nStatus: 302\nFrom: GET https://origin.test/start\nTo: GET https://origin.test/middle",
                "Redirect #2\nStatus: 302\nFrom: GET https://origin.test/middle\nTo: GET https://origin.test/final"));
        assertEquals(eventMessages(events, NetworkLogEventStage.PROXY_SELECT),
                List.of("Proxy: DIRECT", "Proxy: HTTP proxy.test:8080"));
        assertEquals(eventMessages(events, NetworkLogEventStage.REQUEST_HEADERS_END), List.of(
                "Header snapshot:\nCookie: session=a", "Headers unchanged", "Header snapshot:\nCookie: session=b"));
        assertTrue(events.stream().filter(event -> event.stage() == NetworkLogEventStage.REQUEST_HEADERS_END)
                .allMatch(event -> Long.valueOf(7L).equals(event.elapsedMs()) && Long.valueOf(2L).equals(event.durationMs())),
                events.toString());
        assertEquals(eventMessages(events, NetworkLogEventStage.REQUEST_COMPLETE).size(), 1);
        NetworkLogEvent complete = events.get(events.size() - 1);
        assertEquals(complete.stage(), NetworkLogEventStage.REQUEST_COMPLETE);
        assertTrue(complete.message().contains("Status: 200"), complete.message());
        assertTrue(complete.message().contains("Final: GET https://origin.test/final"), complete.message());
        assertTrue(complete.message().contains("Redirects: 2"), complete.message());
        assertTrue(complete.message().contains("Total: "), complete.message());
    }

    @Test
    public void shouldDescribeMethodRewriteAndActualSensitiveHeadersRemovedAcrossOrigins() throws Exception {
        RedirectingTransport transport = new RedirectingTransport(
                response(302, "https://target.test/result"), response(200, null));
        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = diagnosticRequest("https://origin.test/start", events);
        request.method = "POST";
        request.body = "payload";
        request.headersList = List.of(
                new HttpHeader(true, "Authorization", "Bearer secret"),
                new HttpHeader(false, "Cookie", "disabled=value"),
                new HttpHeader(true, "X-Custom", "value"));

        new HttpRedirectExecutor(transport).executeWithRedirects(request, 5, null);

        String redirect = eventMessages(events, NetworkLogEventStage.REDIRECT).get(0);
        assertTrue(redirect.contains("Method Changed: POST → GET"), redirect);
        assertTrue(redirect.contains("Cross-Origin: true"), redirect);
        assertTrue(redirect.contains("Authorization"), redirect);
        assertFalse(redirect.contains("Cookie"), redirect);
        assertFalse(redirect.contains("secret"), redirect);
        PreparedRequest target = transport.requests.get(1);
        assertEquals(target.method, "GET");
        assertEquals(target.body, null);
        assertFalse(target.headersList.stream().anyMatch(header -> header.isEnabled()
                && "Authorization".equalsIgnoreCase(header.getKey())));
        assertTrue(target.headersList.stream().anyMatch(header -> "X-Custom".equals(header.getKey())));
    }

    @DataProvider
    public Object[][] redirectStops() {
        return new Object[][]{
                {false, 5, "/next", "disabled"},
                {true, 0, "/next", "max redirects reached"},
                {true, 5, null, "missing Location"},
                {true, 5, "  ", "missing Location"}
        };
    }

    @Test(dataProvider = "redirectStops")
    public void shouldExplainWhyRedirectWasStoppedWithoutPublishingCompletion(boolean enabled,
                                                                             int maxRedirects,
                                                                             String location,
                                                                             String reason) throws Exception {
        RedirectingTransport transport = new RedirectingTransport(response(302, location));
        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = diagnosticRequest("https://origin.test/start", events);
        request.followRedirects = enabled;

        HttpResponse response = new HttpRedirectExecutor(transport).executeWithRedirects(request, maxRedirects, null);

        assertEquals(response.code, 302);
        assertEquals(transport.urls, List.of("https://origin.test/start"));
        assertFalse(transport.requests.get(0).followRedirects,
                "The single transport call must not follow redirects internally");
        assertTrue(events.stream().anyMatch(event -> event.message().startsWith(
                "Redirect stopped: " + reason + ", status: 302")), events.toString());
        assertTrue(eventMessages(events, NetworkLogEventStage.REQUEST_COMPLETE).isEmpty());
        assertTrue(eventMessages(events, NetworkLogEventStage.REDIRECT).stream()
                .noneMatch(message -> message.startsWith("Redirect #")));
    }

    @Test
    public void shouldAvoidExtraCompletionForRequestWithoutRedirect() throws Exception {
        RedirectingTransport transport = new RedirectingTransport(response(200, null));
        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = diagnosticRequest("https://origin.test/resource", events);

        new HttpRedirectExecutor(transport).executeWithRedirects(request, 5, null);

        assertTrue(eventMessages(events, NetworkLogEventStage.REDIRECT).isEmpty());
        assertTrue(eventMessages(events, NetworkLogEventStage.REQUEST_COMPLETE).isEmpty());
    }

    @Test
    public void shouldKeepLimitResponseAndPublishSummaryForCompletedHop() throws Exception {
        RedirectingTransport transport = new RedirectingTransport(response(302, "/middle"), response(302, "/final"));
        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = diagnosticRequest("https://origin.test/start", events);

        HttpResponse response = new HttpRedirectExecutor(transport).executeWithRedirects(request, 1, null);

        assertEquals(response.code, 302);
        assertEquals(transport.urls, List.of("https://origin.test/start", "https://origin.test/middle"));
        assertTrue(events.stream().anyMatch(event -> event.message().equals(
                "Redirect stopped: max redirects reached, status: 302, max: 1")), events.toString());
        String complete = eventMessages(events, NetworkLogEventStage.REQUEST_COMPLETE).get(0);
        assertTrue(complete.contains("Status: 302"), complete);
        assertTrue(complete.contains("Redirects: 1"), complete);
    }

    @Test
    public void shouldPreserveTransportFailureWithoutPublishingCompletedRequest() {
        RedirectingTransport transport = new RedirectingTransport(response(302, "/next"))
                .thenFail(new IOException("connection reset"));
        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = diagnosticRequest("https://origin.test/start", events);

        IOException failure = expectThrows(IOException.class,
                () -> new HttpRedirectExecutor(transport).executeWithRedirects(request, 5, null));

        assertEquals(failure.getMessage(), "connection reset");
        assertEquals(transport.urls, List.of("https://origin.test/start", "https://origin.test/next"));
        assertEquals(eventMessages(events, NetworkLogEventStage.REDIRECT).size(), 1);
        assertTrue(eventMessages(events, NetworkLogEventStage.REQUEST_COMPLETE).isEmpty());
    }

    private static PreparedRequest diagnosticRequest(String url, List<NetworkLogEvent> events) {
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = url;
        request.followRedirects = true;
        HttpCaptureProfiles.apply(request, HttpCaptureProfile.COLLECTION_DIAGNOSTIC);
        request.networkLogSink = events::add;
        return request;
    }

    private static List<String> eventMessages(List<NetworkLogEvent> events, NetworkLogEventStage stage) {
        return events.stream().filter(event -> event.stage() == stage).map(NetworkLogEvent::message).toList();
    }

    private static HttpResponse response(int code, String location) {
        HttpResponse response = new HttpResponse();
        response.code = code;
        if (location != null) {
            response.headers = java.util.Map.of("Location", java.util.List.of(location));
        }
        return response;
    }

    private static final class CapturingTransport implements HttpTransport {
        private HttpExchangeOptions options;
        private PreparedRequest request;

        @Override
        public HttpResponse execute(PreparedRequest request, HttpExchangeOptions options) {
            this.options = options;
            this.request = request;
            HttpResponse response = new HttpResponse();
            response.code = 200;
            response.body = "ok";
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

    private static final class RedirectingTransport implements HttpTransport {
        private final Deque<HttpResponse> responses = new ArrayDeque<>();
        private final java.util.List<String> urls = new java.util.ArrayList<>();
        private final List<PreparedRequest> requests = new ArrayList<>();
        private final Deque<String> proxyMessages = new ArrayDeque<>();
        private final Deque<String> headerMessages = new ArrayDeque<>();
        private IOException failure;

        private RedirectingTransport(HttpResponse... responses) {
            this.responses.addAll(java.util.List.of(responses));
        }

        private RedirectingTransport withProxyMessages(String... messages) {
            proxyMessages.addAll(List.of(messages));
            return this;
        }

        private RedirectingTransport withHeaderMessages(String... messages) {
            headerMessages.addAll(List.of(messages));
            return this;
        }

        private RedirectingTransport thenFail(IOException failure) {
            this.failure = failure;
            return this;
        }

        @Override
        public HttpResponse execute(PreparedRequest request, HttpExchangeOptions options) throws IOException {
            urls.add(request.url);
            requests.add(request);
            if (!proxyMessages.isEmpty()) {
                NetworkLogSupport.append(request, NetworkLogEventStage.PROXY_SELECT, proxyMessages.removeFirst());
            }
            if (!headerMessages.isEmpty()) {
                NetworkLogSupport.append(request, NetworkLogEventStage.REQUEST_HEADERS_END,
                        headerMessages.removeFirst(), 7L, 2L);
            }
            if (responses.isEmpty() && failure != null) {
                throw failure;
            }
            return responses.removeFirst();
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
