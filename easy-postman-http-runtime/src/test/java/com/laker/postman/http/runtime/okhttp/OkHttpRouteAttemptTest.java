package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpRouteAttempt;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.transport.HttpExchangeTraceSupport;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class OkHttpRouteAttemptTest {

    @Test
    public void shouldRecordFailedIpv6AndSuccessfulIpv4Fallback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        try {
            PreparedRequest preparedRequest = new PreparedRequest();
            preparedRequest.url = "http://dual-stack-route-test:" + server.getAddress().getPort() + "/";
            HttpCaptureProfile profile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
            preparedRequest.captureProfile = profile;

            Dns dns = host -> List.of(
                    InetAddress.getByName("::1"),
                    InetAddress.getByName("127.0.0.1")
            );
            OkHttpClient client = new OkHttpClient.Builder()
                    .dns(dns)
                    .fastFallback(true)
                    .eventListenerFactory(call -> new OkHttpExchangeEventListener(preparedRequest))
                    .build();

            Request request = new Request.Builder().url(preparedRequest.url).build();
            try (Response response = client.newCall(request).execute()) {
                assertEquals(response.code(), 200);
            }

            HttpEventInfo eventInfo = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
            assertTrue(eventInfo.getRouteAttempts().stream()
                    .anyMatch(attempt -> isFamily(attempt, "IPv6") && !attempt.connected()));
            assertTrue(eventInfo.getRouteAttempts().stream()
                    .filter(attempt -> isFamily(attempt, "IPv6"))
                    .noneMatch(HttpRouteAttempt::canceled));
            assertTrue(eventInfo.getRouteAttempts().stream()
                    .anyMatch(attempt -> isFamily(attempt, "IPv4") && attempt.connected()));
            assertFalse(eventInfo.getRouteAttempts().isEmpty());
            assertNull(eventInfo.getError(), "A failed fallback route is not an overall call failure");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldMarkFastFallbackLoserAsCanceledWhenFailurePrecedesWinnerCallback() throws Exception {
        PreparedRequest preparedRequest = diagnosticRequest();
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);
        InetSocketAddress ipv6 = new InetSocketAddress(InetAddress.getByName("2001:db8::10"), 443);
        InetSocketAddress ipv4 = new InetSocketAddress(InetAddress.getByName("192.0.2.10"), 443);

        listener.connectStart(null, ipv6, Proxy.NO_PROXY);
        listener.connectStart(null, ipv4, Proxy.NO_PROXY);
        // OkHttp cancels racing plans before the winner reaches connectEnd.
        listener.connectFailed(null, ipv6, Proxy.NO_PROXY, null, new SocketException("Socket closed"));
        listener.connectEnd(null, ipv4, Proxy.NO_PROXY, okhttp3.Protocol.HTTP_1_1);

        List<HttpRouteAttempt> attempts = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest)
                .getRouteAttempts();
        HttpRouteAttempt loser = attempts.stream()
                .filter(attempt -> "IPv6".equals(attempt.addressFamily()))
                .findFirst()
                .orElseThrow();
        assertFalse(loser.connected());
        assertTrue(loser.canceled());
        assertEquals(loser.error(), "Socket closed");
        assertTrue(attempts.stream().anyMatch(HttpRouteAttempt::connected));
    }

    @Test
    public void shouldKeepRealFallbackFailureAsFailedWhenAnotherRouteWins() throws Exception {
        PreparedRequest preparedRequest = diagnosticRequest();
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);
        InetSocketAddress ipv6 = new InetSocketAddress(InetAddress.getByName("2001:db8::20"), 443);
        InetSocketAddress ipv4 = new InetSocketAddress(InetAddress.getByName("192.0.2.20"), 443);

        listener.connectStart(null, ipv6, Proxy.NO_PROXY);
        listener.connectStart(null, ipv4, Proxy.NO_PROXY);
        listener.connectFailed(null, ipv6, Proxy.NO_PROXY, null, new IOException("Connection refused"));
        listener.connectEnd(null, ipv4, Proxy.NO_PROXY, okhttp3.Protocol.HTTP_1_1);

        HttpRouteAttempt failed = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest)
                .getRouteAttempts().stream()
                .filter(attempt -> "IPv6".equals(attempt.addressFamily()))
                .findFirst()
                .orElseThrow();
        assertFalse(failed.connected());
        assertFalse(failed.canceled());
        assertEquals(failed.error(), "Connection refused");
    }

    @Test
    public void shouldCompleteRoutesSafelyWhenCallbacksRace() throws Exception {
        PreparedRequest preparedRequest = diagnosticRequest();
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);
        List<InetSocketAddress> addresses = new ArrayList<>();
        int routeCount = 128;
        for (int i = 0; i < routeCount; i++) {
            InetAddress address = InetAddress.getByAddress(new byte[]{127, 0, (byte) (i / 250), (byte) (i % 250 + 1)});
            InetSocketAddress socketAddress = new InetSocketAddress(address, 8080 + i);
            addresses.add(socketAddress);
            listener.connectStart(null, socketAddress, Proxy.NO_PROXY);
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> completions = new ArrayList<>();
            for (InetSocketAddress address : addresses) {
                completions.add(executor.submit(() -> listener.connectFailed(
                        null,
                        address,
                        Proxy.NO_PROXY,
                        okhttp3.Protocol.HTTP_1_1,
                        new IOException("unreachable")
                )));
            }
            // A terminal callback may drain still-pending Happy Eyeballs routes
            // while individual connect callbacks are completing on other threads.
            completions.add(executor.submit(() -> listener.callEnd(null)));
            for (Future<?> completion : completions) {
                completion.get();
            }
        } finally {
            executor.shutdownNow();
        }

        HttpEventInfo eventInfo = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertEquals(eventInfo.getRouteAttempts().size(), routeCount);
        assertEquals(eventInfo.getRouteAttempts().stream()
                .map(HttpRouteAttempt::address)
                .collect(Collectors.toSet()).size(), routeCount);
        assertTrue(eventInfo.getRouteAttempts().stream().allMatch(attempt -> !attempt.connected()));
    }

    @Test
    public void shouldRecordConcreteIpInsteadOfOriginalHostname() throws Exception {
        PreparedRequest preparedRequest = diagnosticRequest();
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);
        InetAddress namedAddress = InetAddress.getByAddress(
                "resolved.example.test",
                new byte[]{(byte) 192, 0, 2, 10}
        );
        InetSocketAddress socketAddress = new InetSocketAddress(namedAddress, 8443);

        listener.connectStart(null, socketAddress, Proxy.NO_PROXY);
        listener.connectEnd(null, socketAddress, Proxy.NO_PROXY, okhttp3.Protocol.HTTP_1_1);

        HttpRouteAttempt attempt = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest)
                .getRouteAttempts().get(0);
        assertEquals(attempt.address(), "192.0.2.10:8443");
        assertFalse(attempt.address().contains("resolved.example.test"));
    }

    @Test
    public void failedCallShouldKeepAggregateConnectWindow() throws Exception {
        PreparedRequest preparedRequest = diagnosticRequest();
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);
        InetSocketAddress socketAddress = new InetSocketAddress(
                InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 20}),
                443
        );

        listener.connectStart(null, socketAddress, Proxy.NO_PROXY);
        listener.connectFailed(null, socketAddress, Proxy.NO_PROXY,
                okhttp3.Protocol.HTTP_1_1, new IOException("unreachable"));
        listener.callFailed(null, new IOException("all routes failed"));

        HttpEventInfo eventInfo = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertTrue(eventInfo.getConnectStart() > 0);
        assertTrue(eventInfo.getConnectEnd() >= eventInfo.getConnectStart());
        assertEquals(eventInfo.getError().getMessage(), "all routes failed");
    }

    private static PreparedRequest diagnosticRequest() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.url = "http://example.test/";
        preparedRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        return preparedRequest;
    }

    private static boolean isFamily(HttpRouteAttempt attempt, String family) {
        return family.equals(attempt.addressFamily());
    }
}
