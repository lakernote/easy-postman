package com.laker.postman.platform.network;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class SocketDirectProxySelectorTest {
    private static final Proxy SYSTEM_SOCKS = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1));
    private static final Proxy SYSTEM_HTTP = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.corp", 8080));

    private ProxySelector originalSelector;

    @BeforeMethod
    public void rememberDefaultSelector() {
        originalSelector = ProxySelector.getDefault();
    }

    @AfterMethod(alwaysRun = true)
    public void restoreDefaultSelector() {
        ProxySelector.setDefault(originalSelector);
    }

    @Test
    public void socketLookupsShouldAlwaysBeDirect() {
        RecordingSelector delegate = new RecordingSelector(List.of(SYSTEM_SOCKS));
        SocketDirectProxySelector selector = new SocketDirectProxySelector(delegate);

        assertEquals(selector.select(URI.create("socket://10.175.180.2:80")), List.of(Proxy.NO_PROXY));
        assertEquals(selector.select(URI.create("SOCKET://redis.corp:6379")), List.of(Proxy.NO_PROXY));
        assertTrue(delegate.selected.isEmpty(), "socket:// lookups must not reach the system selector");
    }

    @Test
    public void explicitJvmSocksConfigurationShouldStillBeHonoredForSocketLookups() {
        RecordingSelector delegate = new RecordingSelector(List.of(SYSTEM_SOCKS));
        SocketDirectProxySelector selector = new SocketDirectProxySelector(delegate);
        String previous = System.getProperty("socksProxyHost");
        System.setProperty("socksProxyHost", "jump.corp");
        try {
            assertEquals(selector.select(URI.create("socket://redis.corp:6379")), List.of(SYSTEM_SOCKS));
            assertEquals(selector.select(URI.create("socket://127.0.0.1:6379")), List.of(Proxy.NO_PROXY));
        } finally {
            if (previous == null) {
                System.clearProperty("socksProxyHost");
            } else {
                System.setProperty("socksProxyHost", previous);
            }
        }
    }

    @Test
    public void httpLookupsShouldBeDelegated() {
        RecordingSelector delegate = new RecordingSelector(List.of(SYSTEM_HTTP));
        SocketDirectProxySelector selector = new SocketDirectProxySelector(delegate);

        assertEquals(selector.select(URI.create("https://api.github.com/")), List.of(SYSTEM_HTTP));
        assertEquals(selector.select(URI.create("http://10.175.180.2/")), List.of(SYSTEM_HTTP));
        assertEquals(delegate.selected.size(), 2);
    }

    @Test
    public void loopbackTargetsShouldBeDirectForEveryScheme() {
        RecordingSelector delegate = new RecordingSelector(List.of(SYSTEM_HTTP));
        SocketDirectProxySelector selector = new SocketDirectProxySelector(delegate);

        for (String uri : List.of(
                "http://localhost:8080/",
                "https://LOCALHOST/",
                "http://app.localhost/",
                "http://127.0.0.1/",
                "http://127.10.20.30:9000/",
                "http://[::1]:8080/",
                "http://[0:0:0:0:0:0:0:1]/",
                "http://localhost.:8080/",
                "https://app.localhost./",
                "http://127.0.0.1.:8080/",
                "http://user@127.0.0.1.:8080/",
                "http://127.1:8080/",
                "http://127.0.1/",
                "http://2130706433/",
                "http://0002130706433/",
                "http://000000000127.1/",
                "socks://127.1:1080")) {
            assertEquals(selector.select(URI.create(uri)), List.of(Proxy.NO_PROXY), uri);
        }
        assertTrue(delegate.selected.isEmpty());
    }

    @Test
    public void loopbackDetectionShouldNotMatchNonLoopbackHosts() {
        assertFalse(SocketDirectProxySelector.isLoopbackHost("10.175.180.2"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("128.0.0.1"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("localhost.example.com"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("127.example.com"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("10.0.0.1."));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("128.1"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("167772161"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("4294967296"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("127.256.1"), "only the last part may be wide");
        assertFalse(SocketDirectProxySelector.isLoopbackHost("127.0.0.0.1"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("127..1"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("127.0x1"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("127.0.0.0000000001"),
                "the JDK rejects numeric literals longer than 15 characters");
        assertFalse(SocketDirectProxySelector.isLoopbackHost("localhost.example.com."));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("[fe80::1]"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost("[not-an-ip]"));
        assertFalse(SocketDirectProxySelector.isLoopbackHost(null));
        assertFalse(SocketDirectProxySelector.isLoopbackHost(" "));
    }

    @Test
    public void nonLoopbackTrailingDotHostShouldStillBeDelegated() {
        RecordingSelector delegate = new RecordingSelector(List.of(SYSTEM_HTTP));
        SocketDirectProxySelector selector = new SocketDirectProxySelector(delegate);

        assertEquals(selector.select(URI.create("http://10.175.180.2.:80/")), List.of(SYSTEM_HTTP));
        assertEquals(selector.select(URI.create("http://example.com.:80/")), List.of(SYSTEM_HTTP));
    }

    @Test
    public void numericIpv4ParsingShouldMatchJdkForms() throws Exception {
        for (String host : List.of("127.1", "127.0.1", "2130706433", "10.1", "192.168.1", "1.2.3.4",
                "0002130706433", "000000000127.1", "0127.0.0.1")) {
            long expected = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(
                    java.net.InetAddress.getByName(host).getAddress()).getInt());
            assertEquals(SocketDirectProxySelector.parseNumericIpv4(host), expected, host);
        }
        assertEquals(SocketDirectProxySelector.parseNumericIpv4("example"), -1L);
        assertEquals(SocketDirectProxySelector.parseNumericIpv4("99999999999"), -1L);
        assertEquals(SocketDirectProxySelector.parseNumericIpv4("127.0.0.0000000001"), -1L);
        assertEquals(SocketDirectProxySelector.parseNumericIpv4("127.0.0.1."), -1L);
        assertEquals(SocketDirectProxySelector.parseNumericIpv4(""), -1L);
    }

    @Test
    public void nullDelegateShouldFallBackToDirect() {
        SocketDirectProxySelector selector = new SocketDirectProxySelector(null);

        assertEquals(selector.select(URI.create("https://example.com/")), List.of(Proxy.NO_PROXY));
        selector.connectFailed(URI.create("https://example.com/"), null, new IOException("ignored"));
    }

    @Test
    public void nullUriShouldBeRejectedLikeTheJdkSelector() {
        SocketDirectProxySelector selector = new SocketDirectProxySelector(new RecordingSelector(List.of()));

        expectThrows(IllegalArgumentException.class, () -> selector.select(null));
    }

    @Test
    public void connectFailedShouldBeDelegated() {
        RecordingSelector delegate = new RecordingSelector(List.of(SYSTEM_HTTP));
        SocketDirectProxySelector selector = new SocketDirectProxySelector(delegate);

        selector.connectFailed(URI.create("https://example.com/"), new InetSocketAddress("proxy.corp", 8080),
                new IOException("refused"));

        assertEquals(delegate.failures, 1);
    }

    @Test
    public void installShouldBeIdempotent() {
        ProxySelector.setDefault(new RecordingSelector(List.of(SYSTEM_HTTP)));

        SocketDirectProxySelector.install();
        ProxySelector installed = ProxySelector.getDefault();
        SocketDirectProxySelector.install();

        assertTrue(installed instanceof SocketDirectProxySelector);
        assertSame(ProxySelector.getDefault(), installed);
        assertEquals(installed.select(URI.create("https://example.com/")), List.of(SYSTEM_HTTP));
    }

    @Test
    public void plainSocketShouldBeHijackedBySystemSocksWithoutTheGuard() throws Exception {
        // Documents the JDK behavior the guard exists for.
        ProxySelector.setDefault(new RecordingSelector(List.of(SYSTEM_SOCKS)));

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket socket = new Socket()) {
            expectThrows(IOException.class, () -> socket.connect(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()), 2000));
        }
    }

    @Test
    public void plainSocketShouldConnectDirectlyWhenSystemSelectorReturnsSocks() throws Exception {
        // The delegate points every lookup at a dead SOCKS endpoint, like a Windows system proxy
        // without a protocol prefix. Without the guard, new Socket() would attempt a SOCKS handshake.
        ProxySelector.setDefault(new RecordingSelector(List.of(SYSTEM_SOCKS)));
        SocketDirectProxySelector.install();

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()), 2000);
            try (Socket accepted = server.accept()) {
                assertEquals(accepted.getPort(), socket.getLocalPort());
            }
        }
    }

    private static final class RecordingSelector extends ProxySelector {
        private final List<Proxy> result;
        private final List<URI> selected = new ArrayList<>();
        private int failures;

        private RecordingSelector(List<Proxy> result) {
            this.result = result;
        }

        @Override
        public List<Proxy> select(URI uri) {
            selected.add(uri);
            return result;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            failures++;
        }
    }
}
