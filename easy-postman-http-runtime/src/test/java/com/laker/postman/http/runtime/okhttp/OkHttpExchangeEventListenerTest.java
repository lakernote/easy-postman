package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.NetworkLogEvent;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.transport.HttpExchangeTraceSupport;
import okhttp3.Connection;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
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

    @Test
    public void reusedConnectionShouldNotLogItsUnsafeToString() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        List<NetworkLogEvent> events = new ArrayList<>();
        request.networkLogSink = events::add;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(request);

        try (Socket socket = new Socket()) {
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
                    return Protocol.HTTP_1_1;
                }

                @Override
                public String toString() {
                    return "proxy=user:password@proxy.example";
                }
            };

            listener.connectionAcquired(null, connection);
            listener.connectionReleased(null, connection);
        }

        String messages = events.stream().map(NetworkLogEvent::message).reduce("", String::concat);
        assertTrue(messages.contains("Connection reused"));
        assertFalse(messages.contains("password"));
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
}
