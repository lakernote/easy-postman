package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.certificate.TrustedCertificateEntry;
import com.laker.postman.http.runtime.config.HttpRuntimeSettings;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.ssl.SSLConfigurationUtil;
import com.laker.postman.request.model.HttpRequestProxyPolicy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.Authenticator;

import java.io.File;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.laker.postman.request.util.HttpUrlUtil.extractBaseUri;

/**
 * OkHttpClient 管理器，按 baseUri（协议+host+port）分配连接池和 OkHttpClient
 * 连接池参数参考 Chrome：每 host 6 个连接，保活 90 秒
 */
@Slf4j
public class OkHttpClientManager {
    // 每个 baseUri 一个连接池和 OkHttpClient
    private static final Map<String, OkHttpClient> clientMap = new ConcurrentHashMap<>();
    // 连接池参数
    private static final int MAX_IDLE_CONNECTIONS = 6;
    private static final long KEEP_ALIVE_DURATION = 90L;

    // Dispatcher 并发参数（OkHttp默认：maxRequests=64, maxRequestsPerHost=5）
    private static final int DEFAULT_MAX_REQUESTS = 64;
    private static final int DEFAULT_MAX_REQUESTS_PER_HOST = 5;

    // 全局 CookieManager，支持标准 CookiePolicy
    private static final CookieManager GLOBAL_COOKIE_MANAGER = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private static final JavaNetCookieJar GLOBAL_COOKIE_JAR = new JavaNetCookieJar(GLOBAL_COOKIE_MANAGER);

    public record ProxyInspection(boolean resolved, Proxy proxy) {
        public boolean active() {
            return resolved && !isDirectProxy(proxy);
        }

        public String description() {
            return OkHttpClientManager.describeProxy(proxy);
        }
    }

    /**
     * 清理所有客户端缓存，用于代理设置更改后强制重新创建客户端
     */
    public static void clearClientCache() {
        for (OkHttpClient client : clientMap.values()) {
            shutdownClient(client);
        }
        clientMap.clear();
        SocksProxyAuthenticatorSupport.clearAllowedEndpoints();
    }

    private static void shutdownClient(OkHttpClient client) {
        client.dispatcher().cancelAll();
        client.dispatcher().executorService().shutdownNow();
        client.connectionPool().evictAll();
    }

    /**
     * 获取或创建指定 baseUri 的 OkHttpClient 实例
     */
    public static OkHttpClient getClient(String baseUri, boolean followRedirects) {
        return getClient(baseUri, followRedirects, HttpRequestProxyPolicy.DEFAULT);
    }

    public static OkHttpClient getClient(String baseUri, boolean followRedirects, HttpRequestProxyPolicy proxyPolicy) {
        HttpRequestProxyPolicy resolvedProxyPolicy = HttpRequestProxyPolicy.normalize(proxyPolicy);
        // 将代理配置也作为客户端缓存key的一部分，确保代理设置变更时重新创建客户端
        String proxyKey = getProxyConfigKey(baseUri, resolvedProxyPolicy);
        String key = baseUri + "|" + followRedirects + "|" + proxyKey;

        return clientMap.computeIfAbsent(key, k -> createClient(
                baseUri,
                followRedirects,
                resolveSslVerificationMode(baseUri, resolvedProxyPolicy),
                resolvedProxyPolicy
        ));
    }

    public static OkHttpClient createClientForSslMode(String baseUri,
                                                      boolean followRedirects,
                                                      SSLConfigurationUtil.SSLVerificationMode sslMode,
                                                      HttpRequestProxyPolicy proxyPolicy) {
        HttpRequestProxyPolicy resolvedProxyPolicy = HttpRequestProxyPolicy.normalize(proxyPolicy);
        Dispatcher sharedDispatcher = getClient(baseUri, followRedirects, resolvedProxyPolicy).dispatcher();
        return createClient(baseUri, followRedirects, sslMode, sharedDispatcher, resolvedProxyPolicy);
    }

    /**
     * 基于目标 URL 获取已应用全局代理/SSL 配置的客户端，并按需覆盖超时参数。
     */
    public static OkHttpClient getClientForUrl(String url,
                                               boolean followRedirects,
                                               int connectTimeoutMs,
                                               int readTimeoutMs,
                                               int writeTimeoutMs) {
        OkHttpClient baseClient = getClient(extractBaseUri(url), followRedirects);
        return baseClient.newBuilder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Creates an isolated client for protocol integrations that must not reuse the
     * general request client's connection pool or cookie jar.
     *
     * <p>The client still uses the global proxy and SSL settings, but owns its
     * dispatcher, connection pool, and protocol selection.</p>
     */
    public static OkHttpClient createDedicatedClientForUrl(String url,
                                                           boolean followRedirects,
                                                           int connectTimeoutMs,
                                                           int readTimeoutMs,
                                                           int writeTimeoutMs,
                                                           List<Protocol> protocols) {
        String baseUri = extractBaseUri(url);
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(DEFAULT_MAX_REQUESTS);
        dispatcher.setMaxRequestsPerHost(DEFAULT_MAX_REQUESTS_PER_HOST);

        OkHttpClient client = createClient(
                baseUri,
                followRedirects,
                resolveSslVerificationMode(baseUri, HttpRequestProxyPolicy.DEFAULT),
                dispatcher,
                1,
                30L,
                true,
                CookieJar.NO_COOKIES,
                HttpRequestProxyPolicy.DEFAULT
        );
        OkHttpClient.Builder builder = client.newBuilder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS);
        if (protocols != null && !protocols.isEmpty()) {
            builder.protocols(protocols);
        }
        return builder.build();
    }

    /**
     * 配置网络代理
     */
    private static void configureProxy(OkHttpClient.Builder builder, String baseUri, HttpRequestProxyPolicy proxyPolicy) {
        HttpRequestProxyPolicy resolvedProxyPolicy = HttpRequestProxyPolicy.normalize(proxyPolicy);
        if (resolvedProxyPolicy == HttpRequestProxyPolicy.NO_PROXY || !isProxyEnabledForPolicy(resolvedProxyPolicy)) {
            // The JVM may still expose OS proxy settings via the default ProxySelector.
            // Force direct connections when the in-app proxy toggle is off.
            builder.proxy(Proxy.NO_PROXY);
            return;
        }

        if (settings().isSystemProxyMode()) {
            configureSystemProxy(builder, baseUri, resolvedProxyPolicy);
            return;
        }

        try {
            Proxy proxy = createManualProxy();
            if (proxy == null) {
                builder.proxy(Proxy.NO_PROXY);
                return;
            }

            builder.proxy(proxy);
            SocksProxyAuthenticatorSupport.configureFor(proxy, shouldAllowSocksAuthenticationWhenGlobalProxyDisabled(resolvedProxyPolicy));
            configureProxyAuthenticator(builder);
        } catch (Exception e) {
            log.error("Failed to configure proxy for {}: {}", baseUri, e.getMessage(), e);
            builder.proxy(Proxy.NO_PROXY);
        }
    }

    private static Proxy createManualProxy() {
        String proxyHost = normalizedProxyHost();
        if (proxyHost.isEmpty()) {
            return null;
        }

        int proxyPort = settings().getProxyPort();
        if (proxyPort <= 0 || proxyPort > 65535) {
            return null;
        }
        String proxyType = settings().getProxyType();
        Proxy.Type type = HttpRuntimeSettings.PROXY_TYPE_SOCKS.equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        return new Proxy(type, new InetSocketAddress(proxyHost, proxyPort));
    }

    private static void configureSystemProxy(OkHttpClient.Builder builder,
                                             String baseUri,
                                             HttpRequestProxyPolicy proxyPolicy) {
        ProxySelector selector = getSystemProxySelector();
        if (selector == null) {
            log.debug("System proxy auto-detection is unavailable, using direct connection");
            return;
        }

        builder.proxySelector(selector);
        configureSystemSocksProxyAuthentication(selector, baseUri, shouldAllowSocksAuthenticationWhenGlobalProxyDisabled(proxyPolicy));
        configureProxyAuthenticator(builder);
    }

    private static void configureSystemSocksProxyAuthentication(ProxySelector selector,
                                                                String baseUri,
                                                                boolean allowWhenGlobalProxyDisabled) {
        URI uri = tryParseUri(baseUri, "system SOCKS proxy authentication");
        if (uri == null) {
            return;
        }
        List<Proxy> proxies = selectProxyCandidates(selector, uri, baseUri);
        if (proxies == null || proxies.isEmpty()) {
            return;
        }
        for (Proxy proxy : proxies) {
            SocksProxyAuthenticatorSupport.configureFor(proxy, allowWhenGlobalProxyDisabled);
        }
    }

    private static boolean shouldAllowSocksAuthenticationWhenGlobalProxyDisabled(HttpRequestProxyPolicy proxyPolicy) {
        return proxyPolicy == HttpRequestProxyPolicy.USE_PROXY && !settings().isProxyEnabled();
    }

    private static void configureProxyAuthenticator(OkHttpClient.Builder builder) {
        String proxyUsername = settings().getProxyUsername();
        String proxyPassword = settings().getProxyPassword();

        if (proxyUsername.trim().isEmpty() || proxyPassword.trim().isEmpty()) {
            return;
        }

        Authenticator proxyAuthenticator = (route, response) -> {
            String credential = Credentials.basic(proxyUsername.trim(), proxyPassword);
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };
        builder.proxyAuthenticator(proxyAuthenticator);
    }

    /**
     * 配置SSL设置（统一处理所有HTTPS连接的SSL配置）
     */
    private static OkHttpClient createClient(String baseUri,
                                             boolean followRedirects,
                                             SSLConfigurationUtil.SSLVerificationMode sslMode,
                                             HttpRequestProxyPolicy proxyPolicy) {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(DEFAULT_MAX_REQUESTS);
        dispatcher.setMaxRequestsPerHost(DEFAULT_MAX_REQUESTS_PER_HOST);
        return createClient(
                baseUri,
                followRedirects,
                sslMode,
                dispatcher,
                MAX_IDLE_CONNECTIONS,
                KEEP_ALIVE_DURATION,
                true,
                GLOBAL_COOKIE_JAR,
                proxyPolicy
        );
    }

    public static OkHttpClient createClientForRuntimeConfig(String baseUri,
                                                            boolean followRedirects,
                                                            SSLConfigurationUtil.SSLVerificationMode sslMode,
                                                            HttpClientRuntimeConfig config,
                                                            CookieJar cookieJar,
                                                            HttpRequestProxyPolicy proxyPolicy) {
        HttpClientRuntimeConfig resolvedConfig = config == null ? HttpClientRuntimeConfig.defaults() : config;
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(resolvedConfig.maxRequests());
        dispatcher.setMaxRequestsPerHost(resolvedConfig.maxRequestsPerHost());
        return createClient(
                baseUri,
                followRedirects,
                sslMode,
                dispatcher,
                resolvedConfig.maxIdleConnections(),
                resolvedConfig.keepAliveDurationSeconds(),
                false,
                cookieJar,
                proxyPolicy
        );
    }

    private static OkHttpClient createClient(String baseUri,
                                             boolean followRedirects,
                                             SSLConfigurationUtil.SSLVerificationMode sslMode,
                                             Dispatcher dispatcher,
                                             HttpRequestProxyPolicy proxyPolicy) {
        return createClient(
                baseUri,
                followRedirects,
                sslMode,
                dispatcher,
                MAX_IDLE_CONNECTIONS,
                KEEP_ALIVE_DURATION,
                true,
                GLOBAL_COOKIE_JAR,
                proxyPolicy
        );
    }

    private static OkHttpClient createClient(String baseUri,
                                             boolean followRedirects,
                                             SSLConfigurationUtil.SSLVerificationMode sslMode,
                                             Dispatcher dispatcher,
                                             int poolMaxIdleConnections,
                                             long poolKeepAliveDurationSeconds,
                                             boolean sslConsoleLoggingEnabled,
                                             CookieJar cookieJar,
                                             HttpRequestProxyPolicy proxyPolicy) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(0, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .dispatcher(dispatcher)
                .connectionPool(new ConnectionPool(
                        poolMaxIdleConnections,
                        poolKeepAliveDurationSeconds,
                        TimeUnit.SECONDS
                ))
                .retryOnConnectionFailure(true)
                .followRedirects(followRedirects)
                .cache(null)
                .pingInterval(30, TimeUnit.SECONDS);

        builder.cookieJar(cookieJar == null ? GLOBAL_COOKIE_JAR : cookieJar);
        configureProxy(builder, baseUri, proxyPolicy);
        configureSSLSettings(builder, baseUri, sslMode, sslConsoleLoggingEnabled);
        return builder.build();
    }

    private static void configureSSLSettings(OkHttpClient.Builder builder,
                                             String baseUri,
                                             SSLConfigurationUtil.SSLVerificationMode mode,
                                             boolean sslConsoleLoggingEnabled) {
        URI uri = tryParseUri(baseUri, "SSL configuration");
        if (uri == null || !isSecureScheme(uri.getScheme())) {
            return;
        }

        if (mode == SSLConfigurationUtil.SSLVerificationMode.LENIENT) {
            log.warn("SSL verification disabled by user settings");
        }

        SSLConfigurationUtil.configureSSL(builder, mode, uri.getHost(), resolveSecurePort(uri), sslConsoleLoggingEnabled);
    }

    public static String runtimeSettingsCacheKey(String baseUri) {
        return getProxyConfigKey(baseUri);
    }

    public static String runtimeSettingsCacheKey(String baseUri, HttpRequestProxyPolicy proxyPolicy) {
        return getProxyConfigKey(baseUri, proxyPolicy);
    }

    private static String getProxyConfigKey(String baseUri) {
        return getProxyConfigKey(baseUri, HttpRequestProxyPolicy.DEFAULT);
    }

    private static String getProxyConfigKey(String baseUri, HttpRequestProxyPolicy proxyPolicy) {
        HttpRequestProxyPolicy resolvedProxyPolicy = HttpRequestProxyPolicy.normalize(proxyPolicy);
        String proxyPart = buildProxyConfigKeyPart(baseUri, resolvedProxyPolicy);

        StringBuilder trustPart = new StringBuilder();
        for (TrustedCertificateEntry entry : settings().getCustomTrustMaterialEntries()) {
            String trustPath = entry.getPath();
            File trustFile = (trustPath == null || trustPath.isBlank()) ? null : new File(trustPath);
            long trustLastModified = (trustFile != null && trustFile.exists()) ? trustFile.lastModified() : -1L;
            long trustFileLength = (trustFile != null && trustFile.exists()) ? trustFile.length() : -1L;
            trustPart.append(entry.isEnabled())
                    .append(':')
                    .append(trustPath)
                    .append(':')
                    .append(trustLastModified)
                    .append(':')
                    .append(trustFileLength)
                    .append(':')
                    .append(entry.getPassword() == null ? 0 : entry.getPassword().hashCode())
                    .append(';');
        }
        boolean effectiveProxySslDisabled = isProxyActiveForBaseUri(baseUri, resolvedProxyPolicy)
                && settings().isProxySslVerificationDisabled();
        return String.format("%s|ssl:%b:%b|customTrust:%b:%s|%s",
                proxyPart,
                effectiveProxySslDisabled,
                settings().isRequestSslVerificationDisabled(),
                settings().isCustomTrustMaterialEnabled(),
                trustPart,
                buildClientCertificateCachePart(baseUri));
    }

    private static String buildProxyConfigKeyPart(String baseUri, HttpRequestProxyPolicy proxyPolicy) {
        if (proxyPolicy == HttpRequestProxyPolicy.NO_PROXY) {
            return "proxy:request-disabled";
        }
        if (!isProxyEnabledForPolicy(proxyPolicy)) {
            return "proxy:disabled";
        }
        try {
            return buildProxyConfigPart(baseUri);
        } catch (Exception e) {
            log.warn("Failed to build proxy cache key for {}, using direct proxy fallback", baseUri, e);
            return "proxy:unavailable";
        }
    }

    private static String buildProxyConfigPart(String baseUri) {
        if (settings().isSystemProxyMode()) {
            return "proxy:system:" + describeProxy(selectSystemProxy(baseUri)) + ":"
                    + settings().getProxyUsername() + ":" + settings().getProxyPassword().hashCode();
        }

        return String.format("proxy:manual:%s:%s:%d:%s",
                settings().getProxyType(),
                settings().getProxyHost(),
                settings().getProxyPort(),
                settings().getProxyUsername() + ":" + settings().getProxyPassword().hashCode());
    }

    private static String buildClientCertificateCachePart(String baseUri) {
        URI uri = tryParseUri(baseUri, "client certificate cache key");
        if (uri == null || !isSecureScheme(uri.getScheme())) {
            return "clientCert:none";
        }
        return SSLConfigurationUtil.clientCertificateCacheKey(uri.getHost(), resolveSecurePort(uri));
    }

    private static ProxySelector getSystemProxySelector() {
        return ProxySelector.getDefault();
    }

    static Proxy selectSystemProxy(String baseUri) {
        ProxySelector selector = getSystemProxySelector();
        if (selector == null) {
            return Proxy.NO_PROXY;
        }

        URI uri = tryParseUri(baseUri, "system proxy selection");
        if (uri == null) {
            return Proxy.NO_PROXY;
        }

        List<Proxy> proxies = selectProxyCandidates(selector, uri, baseUri);
        if (proxies == null || proxies.isEmpty()) {
            return Proxy.NO_PROXY;
        }

        for (Proxy proxy : proxies) {
            if (proxy == null) {
                continue;
            }
            if (isDirectProxy(proxy)) {
                return Proxy.NO_PROXY;
            }
            if (proxy.address() instanceof InetSocketAddress) {
                return proxy;
            }
        }
        return Proxy.NO_PROXY;
    }

    public static ProxyInspection inspectSystemProxyForUrl(String url) {
        if (url == null || url.isBlank()) {
            return new ProxyInspection(false, Proxy.NO_PROXY);
        }

        String baseUri = extractBaseUriSafely(url, "system proxy inspection");
        if (baseUri == null) {
            return new ProxyInspection(false, Proxy.NO_PROXY);
        }

        return new ProxyInspection(true, selectSystemProxy(baseUri));
    }

    public static boolean isProxyActiveForUrl(String url, HttpRequestProxyPolicy proxyPolicy) {
        String baseUri = extractBaseUriSafely(url, "proxy check");
        if (baseUri == null) {
            return false;
        }

        return isProxyActiveForBaseUri(baseUri, proxyPolicy);
    }

    private static boolean isProxyActiveForBaseUri(String baseUri, HttpRequestProxyPolicy proxyPolicy) {
        try {
            return resolveProxyActiveForBaseUri(baseUri, proxyPolicy);
        } catch (Exception e) {
            log.warn("Failed to resolve proxy activity for {}, treating proxy as inactive", baseUri, e);
            return false;
        }
    }

    private static boolean resolveProxyActiveForBaseUri(String baseUri, HttpRequestProxyPolicy proxyPolicy) {
        HttpRequestProxyPolicy resolvedProxyPolicy = HttpRequestProxyPolicy.normalize(proxyPolicy);
        if (resolvedProxyPolicy == HttpRequestProxyPolicy.NO_PROXY || !isProxyEnabledForPolicy(resolvedProxyPolicy)) {
            return false;
        }
        if (!settings().isSystemProxyMode()) {
            return createManualProxy() != null;
        }

        Proxy proxy = selectSystemProxy(baseUri);
        return !isDirectProxy(proxy);
    }

    private static String describeProxy(Proxy proxy) {
        if (isDirectProxy(proxy)) {
            return "direct";
        }

        SocketAddress address = proxy.address();
        if (address instanceof InetSocketAddress socketAddress) {
            String host = socketAddress.getHostString();
            return proxy.type() + ":" + host + ":" + socketAddress.getPort();
        }
        return proxy.type() + ":unknown";
    }

    public static CookieManager getGlobalCookieManager() {
        return GLOBAL_COOKIE_MANAGER;
    }

    private static SSLConfigurationUtil.SSLVerificationMode resolveSslVerificationMode(String baseUri, HttpRequestProxyPolicy proxyPolicy) {
        boolean proxySslDisabled = isProxyActiveForBaseUri(baseUri, proxyPolicy)
                && settings().isProxySslVerificationDisabled();
        if (settings().isRequestSslVerificationDisabled() || proxySslDisabled) {
            return SSLConfigurationUtil.SSLVerificationMode.LENIENT;
        }
        return SSLConfigurationUtil.SSLVerificationMode.STRICT;
    }

    private static int resolveSecurePort(URI uri) {
        int port = uri.getPort();
        return port == -1 ? 443 : port;
    }

    private static boolean isSecureScheme(String scheme) {
        return "https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme);
    }

    private static String normalizedProxyHost() {
        String proxyHost = settings().getProxyHost();
        return proxyHost == null ? "" : proxyHost.trim();
    }

    private static URI tryParseUri(String value, String context) {
        try {
            return URI.create(value);
        } catch (Exception e) {
            log.debug("Failed to parse URI for {}: {}", context, value, e);
            return null;
        }
    }

    private static List<Proxy> selectProxyCandidates(ProxySelector selector, URI uri, String rawTarget) {
        try {
            return selector.select(uri);
        } catch (Exception e) {
            log.warn("Failed to auto-detect system proxy for {}", rawTarget, e);
            return null;
        }
    }

    private static String extractBaseUriSafely(String url, String context) {
        try {
            return extractBaseUri(url);
        } catch (Exception e) {
            log.debug("Failed to extract baseUri for {}: {}", context, url, e);
            return null;
        }
    }

    private static boolean isDirectProxy(Proxy proxy) {
        return proxy == null || proxy == Proxy.NO_PROXY || proxy.type() == Proxy.Type.DIRECT;
    }

    private static boolean isProxyEnabledForPolicy(HttpRequestProxyPolicy proxyPolicy) {
        return proxyPolicy == HttpRequestProxyPolicy.USE_PROXY || settings().isProxyEnabled();
    }

    private static HttpRuntimeSettings settings() {
        return HttpRuntimeSettingsProvider.get();
    }
}
