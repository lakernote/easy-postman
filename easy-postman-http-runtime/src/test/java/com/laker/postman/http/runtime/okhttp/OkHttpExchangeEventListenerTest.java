package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.NetworkLogEvent;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.transport.HttpExchangeTraceSupport;
import okhttp3.Address;
import okhttp3.Connection;
import okhttp3.CipherSuite;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.TlsVersion;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class OkHttpExchangeEventListenerTest {

    @Test
    public void proxyConnectionEventsShouldHideUserInfoInMalformedProxyHost() {
        PreparedRequest request = new PreparedRequest();
        request.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        List<NetworkLogEvent> events = new ArrayList<>();
        request.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(request);
        InetSocketAddress address = InetSocketAddress.createUnresolved("user:password@proxy.example", 8080);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, address);

        listener.proxySelectEnd(null, null, List.of(proxy));
        listener.connectStart(null, address, proxy);
        listener.connectFailed(null, address, proxy, null, new IOException("connection refused"));

        String messages = events.stream().map(NetworkLogEvent::message).reduce("", String::concat);
        assertTrue(messages.contains("<invalid-host>:8080"));
        assertFalse(messages.contains("password"));
    }

    @Test
    public void dnsAndFailureDiagnosticsShouldHideUserInfoInMalformedProxyHost() throws Exception {
        String malformedHost = "user:password@proxy.example";
        PreparedRequest request = new PreparedRequest();
        request.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        List<NetworkLogEvent> events = new ArrayList<>();
        request.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(request);

        listener.dnsStart(null, malformedHost);
        listener.dnsEnd(null, malformedHost, List.of(
                InetAddress.getByAddress(malformedHost, new byte[]{127, 0, 0, 1})));
        listener.callFailed(null, new IOException("Failed to resolve " + malformedHost));

        HttpEventInfo info = HttpExchangeTraceSupport.resolveFromRequest(request);
        String messages = events.stream().map(NetworkLogEvent::message).reduce("", String::concat);
        assertEquals(info.getDnsHost(), "<invalid-host>");
        assertFalse(info.getErrorMessage().contains("password"));
        assertFalse(messages.contains("password"));
        assertTrue(messages.contains("<invalid-host>"));

        PreparedRequest failedDnsRequest = new PreparedRequest();
        failedDnsRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        OkHttpExchangeEventListener failedDnsListener = new OkHttpExchangeEventListener(failedDnsRequest);
        failedDnsListener.dnsStart(null, malformedHost);
        failedDnsListener.callFailed(null, new IOException("Unknown host " + malformedHost));
        HttpEventInfo failedDnsInfo = HttpExchangeTraceSupport.resolveFromRequest(failedDnsRequest);
        assertEquals(failedDnsInfo.getDnsHost(), "<invalid-host>");
        assertEquals(failedDnsInfo.getDnsError(), "Unknown host <invalid-host>");
    }

    @Test
    public void proxyHostRedactionShouldPreserveRequestBody() {
        String malformedHost = "user:password@proxy.example";
        PreparedRequest request = new PreparedRequest();
        request.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        request.sentRequestBody = "contact=" + malformedHost;
        List<NetworkLogEvent> events = new ArrayList<>();
        request.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(request);

        listener.dnsStart(null, malformedHost);
        listener.requestBodyStart(null);
        listener.callFailed(null, new IOException("Cannot resolve " + malformedHost));

        assertTrue(events.stream().anyMatch(event -> event.stage()
                == NetworkLogEventStage.REQUEST_BODY_START
                && event.message().contains("contact=" + malformedHost)));
        assertTrue(events.stream().anyMatch(event -> event.stage()
                == NetworkLogEventStage.CALL_FAILED
                && event.message().contains("<invalid-host>")));
    }

    @DataProvider
    public Object[][] interceptionCases() {
        return new Object[][]{
                {Proxy.NO_PROXY, new byte[]{127, 0, 0, 1}, new byte[]{10, (byte) 175, (byte) 180, 2}, true},
                {Proxy.NO_PROXY, new byte[]{10, (byte) 175, (byte) 167, (byte) 167},
                        new byte[]{10, (byte) 175, (byte) 180, 2}, false},
                {Proxy.NO_PROXY, new byte[]{127, 0, 0, 1}, new byte[]{127, 0, 0, 1}, false},
                // Loopback local address with a non-loopback remote: only DIRECT routes are suspicious.
                {new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890)),
                        new byte[]{127, 0, 0, 1}, new byte[]{10, (byte) 175, (byte) 180, 2}, false},
                {new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 7891)),
                        new byte[]{127, 0, 0, 1}, new byte[]{10, (byte) 175, (byte) 180, 2}, false},
        };
    }

    @Test(dataProvider = "interceptionCases")
    public void directRouteWithLoopbackLocalAddressShouldWarnAboutInterception(
            Proxy proxy, byte[] local, byte[] remote, boolean expectWarning) throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        List<NetworkLogEvent> events = new ArrayList<>();
        request.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(request);
        InetAddress localAddress = InetAddress.getByAddress(local);
        InetAddress remoteAddress = InetAddress.getByAddress(remote);
        Route route = new Route(new Address("internal.example", 80, okhttp3.Dns.SYSTEM,
                javax.net.SocketFactory.getDefault(), null, null, null, okhttp3.Authenticator.NONE,
                proxy, List.of(Protocol.HTTP_1_1), List.of(okhttp3.ConnectionSpec.CLEARTEXT),
                java.net.ProxySelector.getDefault()), proxy, new InetSocketAddress(remoteAddress, 80));

        try (Socket socket = new Socket() {
            @Override
            public InetAddress getLocalAddress() {
                return localAddress;
            }

            @Override
            public InetAddress getInetAddress() {
                return remoteAddress;
            }
        }) {
            Connection connection = fixedConnection(route, socket);
            assertEquals(OkHttpExchangeEventListener.isInterceptedDirectRoute(connection), expectWarning);
            listener.connectionAcquired(null, connection);
        }

        List<NetworkLogEvent> warnings = events.stream()
                .filter(event -> event.stage() == NetworkLogEventStage.ROUTE_INTERCEPTED)
                .toList();
        assertEquals(warnings.size(), expectWarning ? 1 : 0);
        if (expectWarning) {
            assertTrue(warnings.get(0).message().startsWith("Direct route intercepted: local=127.0.0.1:"),
                    warnings.get(0).message());
            assertTrue(warnings.get(0).message().contains("remote=10.175.180.2:"), warnings.get(0).message());
        }
    }

    @Test
    public void interceptionApplicationLogWarningShouldBeRateLimited() {
        OkHttpExchangeEventListener.lastInterceptionWarningNanos.set(0L);
        try {
            assertTrue(OkHttpExchangeEventListener.warnInterceptedDirectRoute("127.0.0.1:1", "10.0.0.1:80"));
            assertFalse(OkHttpExchangeEventListener.warnInterceptedDirectRoute("127.0.0.1:2", "10.0.0.2:80"));
        } finally {
            OkHttpExchangeEventListener.lastInterceptionWarningNanos.set(0L);
        }
    }

    @Test
    public void interceptionCheckShouldTolerateMissingRoute() throws Exception {
        try (Socket socket = new Socket()) {
            assertFalse(OkHttpExchangeEventListener.isInterceptedDirectRoute(fixedConnection(null, socket)));
        }
    }

    private static Connection fixedConnection(Route route, Socket socket) {
        return new Connection() {
            @Override
            public Route route() {
                return route;
            }

            @Override
            public Socket socket() {
                return socket;
            }

            @Override
            public Handshake handshake() {
                return null;
            }

            @Override
            public Protocol protocol() {
                return Protocol.HTTP_1_1;
            }
        };
    }

    @Test
    public void reusedConnectionShouldNotLogItsUnsafeToString() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        List<NetworkLogEvent> events = new ArrayList<>();
        request.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(request);

        try (Socket socket = new Socket() {
            @Override
            public InetAddress getLocalAddress() {
                return InetAddress.getLoopbackAddress();
            }

            @Override
            public InetAddress getInetAddress() {
                return InetAddress.getLoopbackAddress();
            }
        }) {
            Connection connection = new Connection() {
                @Override
                public Route route() {
                    return null;
                }

                @Override
                public Socket socket() {
                    return socket;
                }

                @Override
                public Handshake handshake() {
                    return null;
                }

                @Override
                public Protocol protocol() {
                    return Protocol.HTTP_2;
                }

                @Override
                public String toString() {
                    return "proxy=user:password@proxy.example";
                }
            };

            listener.connectionAcquired(null, connection);
            listener.requestHeadersStart(null);
            listener.requestHeadersEnd(null, new Request.Builder()
                    .url("https://example.test/")
                    .header("Connection", "keep-alive")
                    .addHeader("Host", "superseded.test")
                    .addHeader("Host", "example.test")
                    .header("Keep-Alive", "timeout=5")
                    .header("Proxy-Connection", "keep-alive")
                    .header("Transfer-Encoding", "chunked")
                    .header("Upgrade", "websocket")
                    .addHeader("TE", "gzip")
                    .addHeader("TE", "trailers")
                    .build());
            listener.connectionReleased(null, connection);
        }

        String messages = events.stream().map(NetworkLogEvent::message).reduce("", String::concat);
        assertTrue(messages.contains("Connection reused"));
        assertFalse(messages.contains("password"));
        assertFalse(events.stream().anyMatch(event -> event.stage() == NetworkLogEventStage.CONNECTION_RELEASED));
        assertTrue(HttpExchangeTraceSupport.resolveFromRequest(request).getConnectionReleased() > 0);
        String headers = events.stream()
                .filter(event -> event.stage() == NetworkLogEventStage.REQUEST_HEADERS_END)
                .map(NetworkLogEvent::message)
                .findFirst().orElseThrow();
        assertTrue(headers.contains("HTTP/2 header view (regular headers and :authority):"),
                headers);
        assertFalse(headers.contains("Connection: keep-alive"), headers);
        assertFalse(headers.contains("Host: example.test"), headers);
        assertTrue(headers.contains(":authority: example.test"), headers);
        assertEquals(headers.lines().filter(line -> line.startsWith(":authority:")).count(), 1L);
        assertFalse(headers.contains("superseded.test"), headers);
        assertFalse(headers.contains("keep-alive:"), headers);
        assertFalse(headers.contains("proxy-connection:"), headers);
        assertFalse(headers.contains("transfer-encoding:"), headers);
        assertFalse(headers.contains("upgrade:"), headers);
        assertFalse(headers.contains("te: gzip"), headers);
        assertTrue(headers.contains("te: trailers"), headers);
        assertTrue(request.sentHeadersList.stream().anyMatch(header -> "Connection".equalsIgnoreCase(header.getKey())
                && "keep-alive".equals(header.getValue())));
        assertTrue(request.sentHeadersList.stream().anyMatch(header -> "Host".equalsIgnoreCase(header.getKey())
                && "example.test".equals(header.getValue())));
    }

    @Test
    public void compactHttpLogsShouldPreservePhaseMetricsAndListResponseHeadersOnce() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        List<NetworkLogEvent> events = new ArrayList<>();
        preparedRequest.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);
        Request request = new Request.Builder()
                .url("https://example.test/")
                .header("Connection", "keep-alive")
                .header("Accept", "application/json")
                .build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(302)
                .message("")
                .header("Content-Type", "application/json")
                .header("Content-Length", "2")
                .header("Location", "/next")
                .build();

        listener.requestHeadersStart(null);
        listener.requestHeadersEnd(null, request);
        listener.responseHeadersStart(null);
        listener.responseHeadersEnd(null, response);
        listener.responseBodyStart(null);
        listener.responseBodyEnd(null, 2);

        HttpEventInfo info = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertTrue(info.getRequestHeadersStart() > 0);
        assertTrue(info.getRequestHeadersEnd() >= info.getRequestHeadersStart());
        assertTrue(info.getResponseHeadersStart() > 0);
        assertTrue(info.getResponseHeadersEnd() >= info.getResponseHeadersStart());
        assertTrue(info.getResponseBodyStart() > 0);
        assertTrue(info.getResponseBodyEnd() >= info.getResponseBodyStart());
        assertTrue(info.getHeaderBytesSent() > 0);
        assertTrue(info.getHeaderBytesReceived() > 0);
        assertEquals(info.getBodyBytesReceived(), 2L);
        assertFalse(events.stream().anyMatch(event -> List.of(
                NetworkLogEventStage.REQUEST_HEADERS_START,
                NetworkLogEventStage.RESPONSE_HEADERS_START,
                NetworkLogEventStage.RESPONSE_BODY_START).contains(event.stage())), events.toString());

        String headers = events.stream()
                .filter(event -> event.stage() == NetworkLogEventStage.RESPONSE_HEADERS_END_REDIRECT)
                .map(NetworkLogEvent::message)
                .findFirst()
                .orElseThrow();
        assertTrue(headers.contains("HTTP/2 302"), headers);
        assertEquals(headers.lines().filter(line -> line.equalsIgnoreCase("Content-Type: application/json")).count(), 1L);
        assertEquals(headers.lines().filter(line -> line.equalsIgnoreCase("Content-Length: 2")).count(), 1L);
        assertEquals(headers.lines().filter(line -> line.equalsIgnoreCase("Location: /next")).count(), 1L);
        assertEquals(headers.lines().filter(line -> line.matches("Wait: [0-9]+ms")).count(), 1L, headers);
        assertTrue(events.stream().filter(event -> event.stage() == NetworkLogEventStage.RESPONSE_HEADERS_END_REDIRECT)
                .allMatch(event -> event.durationMs() == null), events.toString());
        assertFalse(headers.contains("Redirect:"), headers);
        assertFalse(headers.contains("Cache: MISS"), headers);
        assertFalse(headers.contains("Network: NO"), headers);
    }

    @Test
    public void shouldOmitEmptyRequestBodyAndDirectProxyLogsWhileRetainingMetrics() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        List<NetworkLogEvent> events = new ArrayList<>();
        preparedRequest.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);
        Request request = new Request.Builder().url("https://example.test/").build();

        listener.proxySelectStart(null, request.url());
        listener.proxySelectEnd(null, request.url(), List.of(Proxy.NO_PROXY));
        preparedRequest.sentRequestBody = "";
        listener.requestBodyStart(null);
        listener.requestBodyEnd(null, 0);

        HttpEventInfo info = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertTrue(info.getProxySelectStart() > 0);
        assertTrue(info.getProxySelectEnd() >= info.getProxySelectStart());
        assertTrue(info.getRequestBodyStart() > 0);
        assertTrue(info.getRequestBodyEnd() >= info.getRequestBodyStart());
        assertEquals(info.getBodyBytesSent(), 0L);
        assertTrue(events.isEmpty(), events.toString());

        Proxy proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.test", 8080));
        listener.proxySelectStart(null, request.url());
        listener.proxySelectEnd(null, request.url(), List.of(proxy));

        assertEquals(events.size(), 1);
        assertEquals(events.get(0).stage(), NetworkLogEventStage.PROXY_SELECT_END);
        assertTrue(events.get(0).message().contains("proxy.test:8080"));
    }

    @DataProvider
    public Object[][] tlsVerificationModes() {
        return new Object[][]{{false, "disabled"}, {true, "enabled"}};
    }

    @Test(dataProvider = "tlsVerificationModes")
    public void shouldDescribeTlsVerificationWithoutHealthyCertificateDumpOrPassedClaim(boolean verificationEnabled,
                                                                                      String description) {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.url = "https://example.test/";
        preparedRequest.sslVerificationEnabled = verificationEnabled;
        preparedRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        List<NetworkLogEvent> events = new ArrayList<>();
        preparedRequest.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);
        Handshake handshake = Handshake.get(TlsVersion.TLS_1_3, CipherSuite.TLS_AES_128_GCM_SHA256,
                List.of(), List.of());

        listener.secureConnectStart(null);
        listener.secureConnectEnd(null, handshake);

        String tls = events.stream()
                .filter(event -> event.stage() == NetworkLogEventStage.SECURE_CONNECT_END)
                .map(NetworkLogEvent::message).findFirst().orElseThrow();
        assertTrue(tls.contains("TLSv1.3"), tls);
        assertTrue(tls.contains("TLS_AES_128_GCM_SHA256"), tls);
        assertTrue(tls.contains("Verification: " + description), tls);
        assertFalse(tls.contains("verify ok"), tls);
        assertFalse(tls.contains("Verification: passed"), tls);
        assertFalse(tls.contains("Server certificate:"), tls);
        assertEquals(tls.lines().filter(line -> !line.isBlank()).count(), 2L, tls);
        assertTrue(events.stream().filter(event -> event.stage() == NetworkLogEventStage.SECURE_CONNECT_START)
                .allMatch(event -> event.message().isEmpty()), events.toString());
        HttpEventInfo info = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertEquals(info.getTlsVersion(), "TLSv1.3");
        assertEquals(info.getCipherName(), "TLS_AES_128_GCM_SHA256");
    }

    @Test
    public void shouldOmitNegativeFollowUpLogsWhilePreservingDecisionMetrics() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        List<NetworkLogEvent> events = new ArrayList<>();
        preparedRequest.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);
        Request request = new Request.Builder().url("https://example.test/").build();
        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "/next")
                .build();

        listener.followUpDecision(null, response, null);
        listener.followUpDecision(null, response.newBuilder().code(200).message("OK").build(), null);

        HttpEventInfo info = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertEquals(info.getFollowUpDecisionCount(), 2);
        assertEquals(info.getFollowUpCount(), 0);
        assertTrue(events.isEmpty(), events.toString());

        Request authenticated = new Request.Builder().url("https://example.test/next").build();
        listener.followUpDecision(null, response.newBuilder().code(401).message("Unauthorized").build(), authenticated);

        assertEquals(info.getFollowUpDecisionCount(), 3);
        assertEquals(info.getFollowUpCount(), 1);
        assertEquals(events.size(), 1);
        assertEquals(events.get(0).stage(), NetworkLogEventStage.FOLLOW_UP_DECISION);
        assertTrue(events.get(0).message().contains("GET https://example.test/next"));
    }

    @Test
    public void shouldCaptureDnsAnswersAndOkHttp5Decisions() throws Exception {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.url = "https://example.test/";
        preparedRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);

        listener.dispatcherQueueStart(null, null);
        listener.dispatcherQueueEnd(null, null);
        listener.dnsStart(null, "example.test");
        listener.dnsEnd(null, "example.test", List.of(
                InetAddress.getByName("2001:db8::10"),
                InetAddress.getByName("192.0.2.10")
        ));
        listener.retryDecision(null, new IOException("route failed"), true);

        Request original = new Request.Builder().url("https://example.test/").build();
        Request next = new Request.Builder().url("https://example.test/authenticated").build();
        Response response = new Response.Builder()
                .request(original)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .build();
        listener.followUpDecision(null, response, next);

        HttpEventInfo info = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertEquals(info.getDnsHost(), "example.test");
        assertEquals(info.getDnsAddresses(), List.of("[2001:db8:0:0:0:0:0:10]", "192.0.2.10"));
        assertEquals(info.getRetryDecisionCount(), 1);
        assertEquals(info.getRetryCount(), 1);
        assertEquals(info.getFollowUpDecisionCount(), 1);
        assertEquals(info.getFollowUpCount(), 1);
        assertTrue(info.getDispatcherQueueStart() > 0);
        assertTrue(info.getDispatcherQueueEnd() > 0);
    }

    @Test
    public void shouldDowngradeRetriedFailureToRecoveredError() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.url = "http://127.0.0.1/";
        preparedRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);

        listener.responseFailed(null, new IOException("unexpected end of stream"));
        listener.retryDecision(null, new IOException("unexpected end of stream"), true);
        listener.callEnd(null);

        HttpEventInfo info = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertNull(info.getErrorMessage());
        assertNull(info.getError());
        assertTrue(info.getRecoveredErrorMessage().contains("unexpected end of stream"));
    }

    @Test
    public void shouldKeepErrorWhenRetryAlsoFails() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.url = "http://127.0.0.1/";
        preparedRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);

        listener.responseFailed(null, new IOException("unexpected end of stream"));
        listener.retryDecision(null, new IOException("unexpected end of stream"), true);
        listener.callFailed(null, new IOException("Connection refused"));

        HttpEventInfo info = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertTrue(info.getErrorMessage() != null && !info.getErrorMessage().isBlank());
        assertTrue(info.getRecoveredErrorMessage().contains("unexpected end of stream"));
    }
}
