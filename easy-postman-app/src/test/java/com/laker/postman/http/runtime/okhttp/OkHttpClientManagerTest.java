package com.laker.postman.http.runtime.okhttp;

import cn.hutool.json.JSONUtil;
import com.laker.postman.model.ClientCertificate;
import com.laker.postman.certificate.TrustedCertificateEntry;
import com.laker.postman.http.runtime.app.AppHttpRuntimeBootstrap;
import com.laker.postman.plugin.api.service.ClientCertificatePluginService;
import com.laker.postman.plugin.runtime.PluginRegistry;
import com.laker.postman.plugin.runtime.PluginRuntime;
import com.laker.postman.request.model.HttpRequestProxyPolicy;
import com.laker.postman.service.setting.SettingManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.net.ssl.KeyManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class OkHttpClientManagerTest {

    @BeforeMethod
    public void configureRuntimeAdapters() {
        AppHttpRuntimeBootstrap.configure();
    }

    @Test
    public void defaultProxySettingsShouldDisableProxyWhenUnset() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);

        try {
            props.clear();

            assertFalse(SettingManager.isProxyEnabled());
            assertEquals(SettingManager.getProxyMode(), SettingManager.PROXY_MODE_MANUAL);
            assertTrue(SettingManager.isManualProxyMode());
            assertEquals(SettingManager.getProxyPortText(), "");
        } finally {
            props.clear();
            props.putAll(backup);
        }
    }

    @Test
    public void clientsShouldEnableHappyEyeballsFallback() {
        try {
            OkHttpClient client = OkHttpClientManager.getClient("https://api.example.com", true);

            assertTrue(client.fastFallback());
        } finally {
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void ordinaryClientsShouldNotSendGlobalHttp2Pings() {
        try {
            OkHttpClient client = OkHttpClientManager.getClient("https://api.example.com", true);

            assertEquals(client.pingIntervalMillis(), 0);
        } finally {
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void originsWithTheSameTransportProfileShouldShareAClient() {
        try {
            OkHttpClient first = OkHttpClientManager.getClient(
                    "http://first.example.test", true, HttpRequestProxyPolicy.NO_PROXY);
            OkHttpClient second = OkHttpClientManager.getClient(
                    "http://second.example.test", true, HttpRequestProxyPolicy.NO_PROXY);

            assertSame(second, first);
        } finally {
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void customTrustCacheKeyShouldChangeWhenTrustMaterialFileChanges() throws Exception {
        Path trustFile = Files.createTempFile("easy-postman-trust-material", ".pem");
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);

        try {
            Files.writeString(trustFile, "first", StandardCharsets.US_ASCII);

            props.setProperty("proxy_enabled", "false");
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");
            props.setProperty("custom_trust_material_enabled", "true");
            TrustedCertificateEntry entry = new TrustedCertificateEntry();
            entry.setPath(trustFile.toString());
            entry.setPassword("");
            props.setProperty("custom_trust_material_entries", JSONUtil.toJsonStr(java.util.List.of(entry)));

            String firstKey = getProxyConfigKey("https://localhost:8443");

            Thread.sleep(1100L);
            Files.writeString(trustFile, "second-version", StandardCharsets.US_ASCII);

            String secondKey = getProxyConfigKey("https://localhost:8443");
            assertNotEquals(secondKey, firstKey);
        } finally {
            props.clear();
            props.putAll(backup);
            Files.deleteIfExists(trustFile);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void httpClientShouldIgnoreBrokenCustomTrustMaterial() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);

        try {
            props.clear();
            props.setProperty("proxy_enabled", "false");
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");
            props.setProperty("custom_trust_material_enabled", "true");
            TrustedCertificateEntry entry = new TrustedCertificateEntry();
            entry.setPath("/tmp/does-not-exist.pem");
            props.setProperty("custom_trust_material_entries", JSONUtil.toJsonStr(java.util.List.of(entry)));

            OkHttpClient client = OkHttpClientManager.getClient("https://localhost:8443", true);

            assertNotNull(client);
        } finally {
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void proxySslBypassShouldNotAffectDirectHttpsRequests() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);

        try {
            props.clear();
            props.setProperty("proxy_enabled", "false");
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");

            OkHttpClient baseline = OkHttpClientManager.getClient("https://localhost:8443", true);
            String baselineHostnameVerifier = baseline.hostnameVerifier().getClass().getName();
            String baselineSocketFactory = baseline.sslSocketFactory().getClass().getName();

            OkHttpClientManager.clearClientCache();
            props.setProperty("proxy_ssl_verification_disabled", "true");

            OkHttpClient directClient = OkHttpClientManager.getClient("https://localhost:8443", true);

            assertEquals(directClient.hostnameVerifier().getClass().getName(), baselineHostnameVerifier);
            assertEquals(directClient.sslSocketFactory().getClass().getName(), baselineSocketFactory);
        } finally {
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void disabledProxyShouldForceDirectConnectionsEvenWhenSystemProxyExists() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        ProxySelector originalSelector = ProxySelector.getDefault();

        try {
            props.clear();
            props.setProperty("proxy_enabled", "false");
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");

            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", 8888)));
                }

                @Override
                public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
                }
            });

            OkHttpClient client = OkHttpClientManager.getClient("https://example.com", true);

            assertEquals(client.proxy(), Proxy.NO_PROXY);
        } finally {
            ProxySelector.setDefault(originalSelector);
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void systemProxyModeShouldResolveProxyFromDefaultSelector() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        ProxySelector originalSelector = ProxySelector.getDefault();

        try {
            props.clear();
            props.setProperty("proxy_enabled", "true");
            props.setProperty("proxy_mode", SettingManager.PROXY_MODE_SYSTEM);
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");

            Proxy expectedProxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", 9090));
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    if ("example.com".equalsIgnoreCase(uri.getHost())) {
                        return List.of(expectedProxy);
                    }
                    return List.of(Proxy.NO_PROXY);
                }

                @Override
                public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
                }
            });

            Proxy selectedProxy = OkHttpClientManager.selectSystemProxy("https://example.com");
            assertEquals(selectedProxy.type(), Proxy.Type.HTTP);
            InetSocketAddress address = (InetSocketAddress) selectedProxy.address();
            assertEquals(address.getHostString(), "127.0.0.1");
            assertEquals(address.getPort(), 9090);

            String proxyKey = getProxyConfigKey("https://example.com");
            assertTrue(proxyKey.contains("proxy:system:HTTP:127.0.0.1:9090"));

            OkHttpClient client = OkHttpClientManager.getClient("https://example.com", true);
            List<Proxy> proxies = client.proxySelector().select(URI.create("https://example.com"));
            assertEquals(proxies.size(), 1);
            assertEquals(proxies.get(0), expectedProxy);
        } finally {
            ProxySelector.setDefault(originalSelector);
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void systemProxyCacheKeyShouldChangeWhenSelectedProxyChanges() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        ProxySelector originalSelector = ProxySelector.getDefault();

        try {
            props.clear();
            props.setProperty("proxy_enabled", "true");
            props.setProperty("proxy_mode", SettingManager.PROXY_MODE_SYSTEM);
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");

            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", 8080)));
                }

                @Override
                public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
                }
            });
            String firstKey = getProxyConfigKey("https://example.com");

            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", 9090)));
                }

                @Override
                public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
                }
            });
            String secondKey = getProxyConfigKey("https://example.com");

            assertNotEquals(secondKey, firstKey);
        } finally {
            ProxySelector.setDefault(originalSelector);
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void runtimeSettingsCacheKeyShouldChangeWhenProxyPasswordChanges() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);

        try {
            props.clear();
            props.setProperty("proxy_enabled", "true");
            props.setProperty("proxy_mode", SettingManager.PROXY_MODE_MANUAL);
            props.setProperty("proxy_type", SettingManager.PROXY_TYPE_HTTP);
            props.setProperty("proxy_host", "127.0.0.1");
            props.setProperty("proxy_port", "8080");
            props.setProperty("proxy_username", "worker");
            props.setProperty("proxy_password", "first");
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");

            String firstKey = OkHttpClientManager.runtimeSettingsCacheKey("https://example.com");

            props.setProperty("proxy_password", "second");
            String secondKey = OkHttpClientManager.runtimeSettingsCacheKey("https://example.com");

            assertNotEquals(secondKey, firstKey);
        } finally {
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void socksProxyShouldUseConfiguredCredentialsDuringHandshake() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        Authenticator originalAuthenticator = Authenticator.getDefault();
        String expectedUsername = "configured-proxy-user";
        String expectedPassword = "p@ss:wo=rd#?&%\\\\ end";

        try (FakeSocks5Server socksServer = new FakeSocks5Server()) {
            Authenticator.setDefault(null);
            props.clear();
            props.setProperty("proxy_enabled", "true");
            props.setProperty("proxy_mode", SettingManager.PROXY_MODE_MANUAL);
            props.setProperty("proxy_type", SettingManager.PROXY_TYPE_SOCKS);
            props.setProperty("proxy_host", "127.0.0.1");
            props.setProperty("proxy_port", String.valueOf(socksServer.port()));
            props.setProperty("proxy_username", expectedUsername);
            props.setProperty("proxy_password", expectedPassword);
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");

            OkHttpClient client = OkHttpClientManager.getClient("http://example.com", true);

            try {
                client.newCall(new Request.Builder()
                        .url("http://example.com/")
                        .build()).execute();
            } catch (Exception ignored) {
                // The fake proxy closes after capturing the SOCKS authentication exchange.
            }

            assertEquals(socksServer.username(), expectedUsername);
            assertEquals(socksServer.password(), expectedPassword);
        } finally {
            Authenticator.setDefault(originalAuthenticator);
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void socksProxyShouldUseConfiguredCredentialsWhenRequestForcesProxy() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        Authenticator originalAuthenticator = Authenticator.getDefault();
        String expectedUsername = "forced-proxy-user";
        String expectedPassword = "forced-secret";

        try (FakeSocks5Server socksServer = new FakeSocks5Server()) {
            Authenticator.setDefault(null);
            props.clear();
            props.setProperty("proxy_enabled", "false");
            props.setProperty("proxy_mode", SettingManager.PROXY_MODE_MANUAL);
            props.setProperty("proxy_type", SettingManager.PROXY_TYPE_SOCKS);
            props.setProperty("proxy_host", "127.0.0.1");
            props.setProperty("proxy_port", String.valueOf(socksServer.port()));
            props.setProperty("proxy_username", expectedUsername);
            props.setProperty("proxy_password", expectedPassword);
            props.setProperty("ssl_verification_enabled", "true");
            props.setProperty("proxy_ssl_verification_disabled", "false");

            OkHttpClient client = OkHttpClientManager.getClient(
                    "http://example.com",
                    true,
                    HttpRequestProxyPolicy.USE_PROXY
            );

            try {
                client.newCall(new Request.Builder()
                        .url("http://example.com/")
                        .build()).execute();
            } catch (Exception ignored) {
                // The fake proxy closes after capturing the SOCKS authentication exchange.
            }

            assertEquals(socksServer.username(), expectedUsername);
            assertEquals(socksServer.password(), expectedPassword);
        } finally {
            Authenticator.setDefault(originalAuthenticator);
            props.clear();
            props.putAll(backup);
            OkHttpClientManager.clearClientCache();
        }
    }

    @Test
    public void runtimeSettingsCacheKeyShouldChangeWhenClientCertificateChanges() throws Exception {
        PluginRegistry registry = PluginRuntime.getRegistry();
        Map<Class<?>, Object> services = getPluginServices(registry);
        Object previousRegistration = services.get(ClientCertificatePluginService.class);

        try {
            registry.registerService(
                    ClientCertificatePluginService.class,
                    clientCertificateService("first-cert", "/tmp/first-client.p12")
            );
            String firstKey = OkHttpClientManager.runtimeSettingsCacheKey("https://mtls.example.com:443");

            registry.registerService(
                    ClientCertificatePluginService.class,
                    clientCertificateService("second-cert", "/tmp/second-client.p12")
            );
            String secondKey = OkHttpClientManager.runtimeSettingsCacheKey("https://mtls.example.com:443");

            assertNotEquals(secondKey, firstKey);
        } finally {
            if (previousRegistration == null) {
                services.remove(ClientCertificatePluginService.class);
            } else {
                services.put(ClientCertificatePluginService.class, previousRegistration);
            }
        }
    }

    private static Properties getSettingsProperties() throws Exception {
        Field propsField = SettingManager.class.getDeclaredField("props");
        propsField.setAccessible(true);
        return (Properties) propsField.get(null);
    }

    private static String getProxyConfigKey(String baseUri) throws Exception {
        Method method = OkHttpClientManager.class.getDeclaredMethod("getProxyConfigKey", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, baseUri);
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Object> getPluginServices(PluginRegistry registry) throws Exception {
        Field servicesField = PluginRegistry.class.getDeclaredField("services");
        servicesField.setAccessible(true);
        return (Map<Class<?>, Object>) servicesField.get(registry);
    }

    private static ClientCertificatePluginService clientCertificateService(String id, String certPath) {
        ClientCertificate certificate = new ClientCertificate();
        certificate.setId(id);
        certificate.setName(id);
        certificate.setHost("mtls.example.com");
        certificate.setPort(443);
        certificate.setCertPath(certPath);
        certificate.setCertType(ClientCertificate.CERT_TYPE_PFX);
        certificate.setCertPassword("secret");
        certificate.setEnabled(true);
        certificate.setUpdatedAt(System.currentTimeMillis());

        return new ClientCertificatePluginService() {
            @Override
            public List<ClientCertificate> getAllCertificates() {
                return List.of(certificate);
            }

            @Override
            public void addCertificate(ClientCertificate certificate) {
            }

            @Override
            public void updateCertificate(ClientCertificate certificate) {
            }

            @Override
            public void deleteCertificate(String id) {
            }

            @Override
            public boolean validateCertificatePaths(ClientCertificate certificate) {
                return true;
            }

            @Override
            public KeyManager[] loadClientCertificateKeyManagers(String host, int port) {
                return new KeyManager[0];
            }
        };
    }

    private static final class FakeSocks5Server implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread serverThread;
        private volatile String username;
        private volatile String password;

        private FakeSocks5Server() throws Exception {
            serverSocket = new ServerSocket(0);
            serverThread = new Thread(this::acceptOneConnection, "fake-socks5-proxy");
            serverThread.setDaemon(true);
            serverThread.start();
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private String username() {
            return username;
        }

        private String password() {
            return password;
        }

        private void acceptOneConnection() {
            try (Socket socket = serverSocket.accept()) {
                byte[] greetingHeader = readExactly(socket, 2);
                if (greetingHeader.length < 2 || greetingHeader[0] != 0x05) {
                    return;
                }
                readExactly(socket, greetingHeader[1] & 0xff);
                socket.getOutputStream().write(new byte[]{0x05, 0x02});
                socket.getOutputStream().flush();

                byte[] authHeader = readExactly(socket, 2);
                if (authHeader.length < 2 || authHeader[0] != 0x01) {
                    return;
                }
                username = new String(readExactly(socket, authHeader[1] & 0xff), StandardCharsets.ISO_8859_1);
                byte[] passwordLengthBytes = readExactly(socket, 1);
                int passwordLength = passwordLengthBytes.length == 0 ? 0 : passwordLengthBytes[0] & 0xff;
                password = new String(readExactly(socket, passwordLength), StandardCharsets.ISO_8859_1);
                socket.getOutputStream().write(new byte[]{0x01, 0x00});
                socket.getOutputStream().flush();

                byte[] connectHeader = readExactly(socket, 4);
                if (connectHeader.length < 4) {
                    return;
                }
                skipSocksAddress(socket, connectHeader[3] & 0xff);
                readExactly(socket, 2);
                socket.getOutputStream().write(new byte[]{0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
                socket.getOutputStream().flush();
            } catch (Exception ignored) {
                // The test asserts on whatever credentials were captured before the connection failed.
            }
        }

        private static byte[] readExactly(Socket socket, int length) throws Exception {
            byte[] data = new byte[length];
            int offset = 0;
            while (offset < length) {
                int count = socket.getInputStream().read(data, offset, length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
            if (offset == length) {
                return data;
            }
            byte[] partial = new byte[offset];
            System.arraycopy(data, 0, partial, 0, offset);
            return partial;
        }

        private static void skipSocksAddress(Socket socket, int addressType) throws Exception {
            if (addressType == 0x01) {
                readExactly(socket, 4);
                return;
            }
            if (addressType == 0x03) {
                byte[] lengthBytes = readExactly(socket, 1);
                if (lengthBytes.length == 1) {
                    readExactly(socket, lengthBytes[0] & 0xff);
                }
                return;
            }
            if (addressType == 0x04) {
                readExactly(socket, 16);
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            serverThread.join(1000L);
        }
    }
}
