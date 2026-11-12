package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.config.HttpRuntimeSettings;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.config.ProxyOverrideRuntimeSettings;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Credentials;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class ProxyConnectionCheckerTest {
    private static final String USER = "proxy-user";
    private static final String PASSWORD = "proxy-pass";

    private HttpServer proxyServer;
    private final AtomicInteger proxyRequests = new AtomicInteger();
    private final HttpRuntimeSettings globalSettings = new HttpRuntimeSettings() {
    };

    @BeforeMethod
    public void setUp() throws IOException {
        OkHttpClientManager.clearClientCache();
        HttpRuntimeSettingsProvider.set(globalSettings);
        proxyRequests.set(0);
        proxyServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        String expected = Credentials.basic(USER, PASSWORD);
        proxyServer.createContext("/", exchange -> {
            proxyRequests.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Proxy-Authorization");
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            if (!expected.equals(authorization)) {
                exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"test\"");
                exchange.sendResponseHeaders(407, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        proxyServer.createContext("/direct", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        proxyServer.start();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (proxyServer != null) {
            proxyServer.stop(0);
        }
        HttpRuntimeSettingsProvider.reset();
        OkHttpClientManager.clearClientCache();
    }

    @Test
    public void shouldSucceedThroughManualProxyWithCredentials() {
        ProxyConnectionChecker.Result result = ProxyConnectionChecker.check(
                manualProxy(USER, PASSWORD), "http://target.invalid/", 3000);

        assertEquals(result.status(), ProxyConnectionChecker.Status.SUCCESS);
        assertEquals(result.httpCode(), 200);
        assertTrue(result.route().active());
        assertSame(HttpRuntimeSettingsProvider.get(), globalSettings);
    }

    @Test
    public void shouldReportProxyAuthenticationFailureWithoutRetryLoop() {
        ProxyConnectionChecker.Result result = ProxyConnectionChecker.check(
                manualProxy(USER, "wrong"), "http://target.invalid/", 3000);

        assertEquals(result.status(), ProxyConnectionChecker.Status.PROXY_AUTH_FAILED);
        assertEquals(result.httpCode(), 407);
        assertEquals(proxyRequests.get(), 2);
    }

    @Test
    public void shouldConnectDirectlyWhenProxyIsDisabled() {
        ProxyConnectionChecker.Result result = ProxyConnectionChecker.check(
                ProxyOverrideRuntimeSettings.builder().base(globalSettings).proxyEnabled(false).build(),
                "http://127.0.0.1:" + proxyServer.getAddress().getPort() + "/direct",
                3000);

        assertEquals(result.status(), ProxyConnectionChecker.Status.SUCCESS);
        assertEquals(result.httpCode(), 204);
        assertFalse(result.route().active());
    }

    @Test
    public void shouldReportUnreachableProxy() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        ProxyConnectionChecker.Result result = ProxyConnectionChecker.check(
                ProxyOverrideRuntimeSettings.builder()
                        .base(globalSettings)
                        .proxyEnabled(true)
                        .proxyMode(HttpRuntimeSettings.PROXY_MODE_MANUAL)
                        .proxyType(HttpRuntimeSettings.PROXY_TYPE_HTTP)
                        .proxyHost("127.0.0.1")
                        .proxyPort(closedPort)
                        .build(),
                "http://target.invalid/",
                3000);

        assertEquals(result.status(), ProxyConnectionChecker.Status.FAILED);
        assertFalse(result.message().isBlank());
    }

    @Test
    public void shouldNormalizeTargetWithoutScheme() {
        assertEquals(ProxyConnectionChecker.normalizeTarget("example.com"), "https://example.com");
        assertEquals(ProxyConnectionChecker.normalizeTarget(" "), "https://example.com/");
        assertEquals(ProxyConnectionChecker.normalizeTarget("http://a.test"), "http://a.test");
    }

    private ProxyOverrideRuntimeSettings manualProxy(String username, String password) {
        return ProxyOverrideRuntimeSettings.builder()
                .base(globalSettings)
                .proxyEnabled(true)
                .proxyMode(HttpRuntimeSettings.PROXY_MODE_MANUAL)
                .proxyType(HttpRuntimeSettings.PROXY_TYPE_HTTP)
                .proxyHost("127.0.0.1")
                .proxyPort(proxyServer.getAddress().getPort())
                .proxyUsername(username)
                .proxyPassword(password)
                .build();
    }
}
