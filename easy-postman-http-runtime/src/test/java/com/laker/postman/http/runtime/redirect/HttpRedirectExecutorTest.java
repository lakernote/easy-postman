package com.laker.postman.http.runtime.redirect;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.HttpCaptureProfiles;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.transport.HttpCallTracker;
import com.laker.postman.http.runtime.transport.HttpExchangeOptions;
import com.laker.postman.http.runtime.transport.HttpTransport;
import com.laker.postman.http.runtime.transport.RealtimeConnectionHandle;
import com.laker.postman.http.runtime.transport.RealtimeConnectionOptions;
import com.laker.postman.http.runtime.transport.RealtimeWebSocketConnection;
import okhttp3.Call;
import okhttp3.WebSocketListener;
import okhttp3.sse.EventSourceListener;
import org.testng.annotations.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

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

        private RedirectingTransport(HttpResponse... responses) {
            this.responses.addAll(java.util.List.of(responses));
        }

        @Override
        public HttpResponse execute(PreparedRequest request, HttpExchangeOptions options) {
            urls.add(request.url);
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
