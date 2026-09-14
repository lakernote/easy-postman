package com.laker.postman.http.runtime.transport;

import com.laker.postman.http.runtime.app.AppHttpRuntimeBootstrap;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.okhttp.OkHttpExchangeEventListener;
import com.laker.postman.http.runtime.okhttp.WebSocketLifecycleLogListener;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.service.setting.SettingManager;
import okhttp3.OkHttpClient;
import okhttp3.WebSocketListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class HttpTransportComponentsTest {
    private static final HttpClientResolver CLIENT_RESOLVER = new HttpClientResolver();
    private static final RealtimeConnectionFactory REALTIME_CONNECTION_FACTORY =
            new RealtimeConnectionFactory(CLIENT_RESOLVER);

    @BeforeMethod
    public void configureRuntimeAdapters() {
        AppHttpRuntimeBootstrap.configure();
    }

    @Test
    public void shouldIsolateConnectionPoolWhenRequestSslModeDiffersFromGlobal() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);

        try {
            props.clear();
            props.setProperty("proxy_enabled", "false");
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");

            PreparedRequest request = new PreparedRequest();
            request.url = "https://api.example.com/data";
            request.sslVerificationEnabled = false;

            assertTrue(CLIENT_RESOLVER.shouldIsolateConnectionPool(request));
        } finally {
            props.clear();
            props.putAll(backup);
        }
    }

    @Test
    public void shouldNotIsolateConnectionPoolWhenRequestSslModeMatchesGlobal() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);

        try {
            props.clear();
            props.setProperty("proxy_enabled", "false");
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");

            PreparedRequest request = new PreparedRequest();
            request.url = "https://api.example.com/data";
            request.sslVerificationEnabled = true;

            assertFalse(CLIENT_RESOLVER.shouldIsolateConnectionPool(request));
        } finally {
            props.clear();
            props.putAll(backup);
        }
    }

    @Test
    public void shouldUseSecureDefaultPortForWss() {
        assertTrue(CLIENT_RESOLVER.resolveSecurePort("wss", -1) == 443);
        assertTrue(CLIENT_RESOLVER.resolveSecurePort("https", -1) == 443);
    }

    @Test
    public void shouldUseResponseReceivedTimestampForDisplayedRequestCost() throws Exception {
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setQueueStart(1_000L);
        eventInfo.setResponseBodyEnd(1_120L);
        eventInfo.setCallEnd(1_130L);
        HttpResponse response = new HttpResponse();
        response.httpEventInfo = eventInfo;

        long resolvedEnd = HttpExchangeTraceSupport.resolveResponseReceivedEndTime(response, 1_500L);

        assertEquals(resolvedEnd, 1_130L);
    }

    @Test
    public void shouldBindExchangeTraceToPreparedRequestWithoutThreadLocalState() {
        PreparedRequest request = new PreparedRequest();
        request.collectMetricsInfo = true;
        request.collectEventInfo = true;
        request.enableNetworkLog = true;

        new OkHttpExchangeEventListener(request);

        assertTrue(request.exchangeEventInfo != null);
    }

    @Test
    public void shouldFallbackToCallbackThreadWhenTraceThreadIsMissing() {
        PreparedRequest request = new PreparedRequest();
        request.exchangeEventInfo = new HttpEventInfo();
        HttpResponse response = new HttpResponse();

        HttpExchangeTraceSupport.attachToResponse(response, System.currentTimeMillis(), request);

        assertEquals(response.threadName, Thread.currentThread().getName());
        assertEquals(response.httpEventInfo.getThreadName(), Thread.currentThread().getName());
    }

    @Test
    public void shouldReuseBaseSslConfigurationWhenRequestSslModeMatchesGlobal() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);

        try {
            props.clear();
            props.setProperty("proxy_enabled", "false");
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");
            OkHttpClientManager.clearClientCache();

            PreparedRequest request = new PreparedRequest();
            request.url = "https://api.example.com/data";
            request.followRedirects = true;
            request.sslVerificationEnabled = true;

            OkHttpClient baseClient = OkHttpClientManager.getClient("https://api.example.com", true);
            OkHttpClient customClient = CLIENT_RESOLVER.resolveClient(request, null);

            assertSame(customClient.sslSocketFactory(), baseClient.sslSocketFactory());
            assertSame(customClient.hostnameVerifier(), baseClient.hostnameVerifier());
        } finally {
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void systemProxyBypassShouldNotForceSslIsolation() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        ProxySelector originalSelector = ProxySelector.getDefault();

        try {
            props.clear();
            props.setProperty("proxy_enabled", "true");
            props.setProperty("proxy_mode", SettingManager.PROXY_MODE_SYSTEM);
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "true");

            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(Proxy.NO_PROXY);
                }

                @Override
                public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
                }
            });

            PreparedRequest request = new PreparedRequest();
            request.url = "https://bypass.example.com/data";
            request.sslVerificationEnabled = true;

            assertFalse(CLIENT_RESOLVER.shouldIsolateConnectionPool(request));
        } finally {
            ProxySelector.setDefault(originalSelector);
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void strictRequestShouldNotReuseLenientSslComponents() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);

        try {
            props.clear();
            props.setProperty("proxy_enabled", "false");
            props.setProperty("ssl_verification_enabled", "false");
            props.setProperty("proxy_ssl_verification_disabled", "false");
            OkHttpClientManager.clearClientCache();

            PreparedRequest request = new PreparedRequest();
            request.url = "https://api.example.com/data";
            request.followRedirects = true;
            request.sslVerificationEnabled = true;

            OkHttpClient lenientBaseClient = OkHttpClientManager.getClient("https://api.example.com", true);
            OkHttpClient strictClient = CLIENT_RESOLVER.resolveClient(request, null);
            OkHttpClient strictDefaultClient = new OkHttpClient();

            assertEquals(
                    strictClient.hostnameVerifier().getClass().getName(),
                    strictDefaultClient.hostnameVerifier().getClass().getName()
            );
            assertEquals(
                    strictClient.sslSocketFactory().getClass().getName(),
                    strictDefaultClient.sslSocketFactory().getClass().getName()
            );
            assertFalse(
                    strictClient.hostnameVerifier().getClass().getName()
                            .equals(lenientBaseClient.hostnameVerifier().getClass().getName())
            );
            assertFalse(
                    strictClient.sslSocketFactory().getClass().getName()
                            .equals(lenientBaseClient.sslSocketFactory().getClass().getName())
            );
            assertSame(strictClient.dispatcher(), lenientBaseClient.dispatcher());
            assertFalse(strictClient.connectionPool() == lenientBaseClient.connectionPool());
        } finally {
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void shouldNotWrapWebSocketListenerWhenLifecycleLoggingIsDisabled() {
        WebSocketListener listener = new WebSocketListener() {
        };

        WebSocketListener resolvedListener = REALTIME_CONNECTION_FACTORY.wrapWebSocketListener(listener, false);

        assertSame(resolvedListener, listener);
        assertFalse(resolvedListener instanceof WebSocketLifecycleLogListener);
    }

    @Test
    public void transportPortShouldExposeOnlyUseCaseMethods() {
        Set<String> methodNames = Arrays.stream(HttpTransport.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(methodNames, Set.of("execute", "openSse", "openWebSocket"));
        assertEquals(HttpTransport.class.getDeclaredMethods().length, 3);
    }

    @Test
    public void transportOptionsShouldResolveDefaults() {
        assertSame(HttpExchangeOptions.defaults().resolvedCallTracker(), HttpCallTracker.NOOP);
        assertTrue(RealtimeConnectionOptions.defaults().isLifecycleLoggingEnabled());
    }

    private static Properties getSettingsProperties() throws Exception {
        Field propsField = SettingManager.class.getDeclaredField("props");
        propsField.setAccessible(true);
        return (Properties) propsField.get(null);
    }
}
