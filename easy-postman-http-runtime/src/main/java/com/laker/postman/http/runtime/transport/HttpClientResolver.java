package com.laker.postman.http.runtime.transport;

import com.laker.postman.http.runtime.config.HttpRequestRuntimeSettingsResolver;
import com.laker.postman.http.runtime.config.HttpRuntimeSettings;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.model.HttpCapturePolicy;
import com.laker.postman.http.runtime.model.HttpCaptureProfiles;
import com.laker.postman.http.runtime.model.HttpExchangeKind;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.okhttp.DigestAuthenticator;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.http.runtime.okhttp.OkHttpExchangeEventListener;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.observation.NetworkLogSupport;
import com.laker.postman.http.runtime.observation.SafeSocketAddressFormatter;
import com.laker.postman.http.runtime.ssl.SSLConfigurationUtil;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.HttpRequestProxyPolicy;
import com.laker.postman.request.model.TransportAuth;
import com.laker.postman.request.util.HttpUrlUtil;
import okhttp3.CookieJar;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.laker.postman.request.util.HttpUrlUtil.extractBaseUri;

public final class HttpClientResolver {
    static final HttpClientResolver DEFAULT = new HttpClientResolver();

    public OkHttpClient resolveClient(PreparedRequest request, HttpBaseClientProvider baseClientProvider) {
        return resolveClient(request, baseClientProvider, HttpExchangeKind.HTTP);
    }

    OkHttpClient resolveClient(PreparedRequest request,
                               HttpBaseClientProvider baseClientProvider,
                               HttpExchangeKind exchangeKind) {
        OkHttpClient baseClient = baseClientProvider == null
                ? resolveDefaultBaseClient(request)
                : baseClientProvider.getBaseClient(request);
        return buildDynamicClient(baseClient, request, request.requestTimeoutMs, exchangeKind);
    }

    OkHttpClient resolveDefaultBaseClient(PreparedRequest request) {
        String baseUri = extractBaseUri(request.url);
        boolean isolateSslConfiguration = shouldIsolateConnectionPool(request);
        return isolateSslConfiguration
                ? OkHttpClientManager.getClientForSslMode(
                        baseUri,
                        request.followRedirects,
                        resolveSslVerificationMode(request),
                        request.proxyPolicy
                )
                : OkHttpClientManager.getClient(baseUri, request.followRedirects, request.proxyPolicy);
    }

    boolean shouldIsolateConnectionPool(PreparedRequest preparedRequest) {
        if (preparedRequest == null) {
            return false;
        }

        URI uri;
        try {
            uri = URI.create(HttpUrlUtil.normalizeIpv6Url(preparedRequest.url));
        } catch (Exception e) {
            return false;
        }

        String scheme = uri.getScheme();
        boolean secureScheme = "https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme);
        if (!secureScheme) {
            return false;
        }

        return resolveSslVerificationMode(preparedRequest) != resolveGlobalSslVerificationMode(preparedRequest.url);
    }

    SSLConfigurationUtil.SSLVerificationMode resolveSslVerificationMode(PreparedRequest preparedRequest) {
        if (preparedRequest == null) {
            return resolveGlobalSslVerificationMode();
        }

        boolean proxySslDisabled = HttpRequestRuntimeSettingsResolver
                .isProxySslVerificationForcedDisabled(preparedRequest.url, preparedRequest.proxyPolicy);
        return (!preparedRequest.sslVerificationEnabled || proxySslDisabled)
                ? SSLConfigurationUtil.SSLVerificationMode.LENIENT
                : SSLConfigurationUtil.SSLVerificationMode.STRICT;
    }

    int resolveSecurePort(String scheme, int port) {
        if (port != -1) {
            return port;
        }
        return ("https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)) ? 443 : 80;
    }

    private OkHttpClient buildDynamicClient(OkHttpClient baseClient,
                                            PreparedRequest preparedRequest,
                                            int timeoutMs,
                                            HttpExchangeKind exchangeKind) {
        OkHttpClient.Builder builder = baseClient.newBuilder();
        HttpCapturePolicy capturePolicy = HttpCaptureProfiles.resolve(preparedRequest);
        boolean needEventListener = capturePolicy.collectMetrics()
                || capturePolicy.collectEventDetails()
                || capturePolicy.emitNetworkLog();
        if (CookieHeaderMergeNetworkInterceptor.hasEnabledExplicitCookieHeader(preparedRequest)) {
            builder.addNetworkInterceptor(new CookieHeaderMergeNetworkInterceptor(preparedRequest));
        }
        if (capturePolicy.captureSentRequest()) {
            builder.addNetworkInterceptor(
                    new RequestSnapshotNetworkInterceptor(preparedRequest, capturePolicy.captureSentRequestBody())
            );
        }
        builder.addNetworkInterceptor(new CompressionDecompressNetworkInterceptor());

        applyRequestSettings(builder, preparedRequest);
        applyWebSocketSettings(builder, preparedRequest);

        if (needEventListener) {
            EventListener.Factory existingFactory = baseClient.eventListenerFactory();
            builder.eventListenerFactory(call -> new OkHttpExchangeEventListener(preparedRequest, exchangeKind)
                    .plus(existingFactory.create(call)));
        }

        if (timeoutMs > 0) {
            builder.connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        }
        OkHttpClient client = builder.build();
        if (capturePolicy.emitNetworkLog()) {
            publishProxyConfiguration(preparedRequest, client);
        }
        return client;
    }

    private void publishProxyConfiguration(PreparedRequest request, OkHttpClient client) {
        try {
            HttpRuntimeSettings settings = HttpRuntimeSettingsProvider.get();
            HttpRequestProxyPolicy policy = HttpRequestProxyPolicy.normalize(request.proxyPolicy);
            boolean appProxyEnabled = settings.isProxyEnabled();
            boolean systemProxyMode = settings.isSystemProxyMode();
            String message = "requestPolicy=" + policy
                    + ", appProxyEnabled=" + appProxyEnabled
                    + ", appProxyMode=" + (systemProxyMode ? "SYSTEM" : "MANUAL")
                    + ", clientProxy=" + describeClientProxy(client)
                    + ", directHttpSocketFactoryJvmSocksBypass="
                    + OkHttpClientManager.bypassesJvmSocketProxySelector(client)
                    + ", manualProxyConfig=" + describeManualProxyConfig(
                    settings, policy, appProxyEnabled, systemProxyMode);
            NetworkLogSupport.append(request, NetworkLogEventStage.PROXY_SELECT, message);
        } catch (RuntimeException ignored) {
            NetworkLogSupport.append(request, NetworkLogEventStage.PROXY_SELECT, "proxy diagnostics unavailable");
        }
    }

    private String describeClientProxy(OkHttpClient client) {
        Proxy proxy = client.proxy();
        if (proxy == null) {
            return client.proxySelector() == ProxySelector.getDefault() ? "SYSTEM_SELECTOR" : "CUSTOM_SELECTOR";
        }
        if (proxy == Proxy.NO_PROXY || proxy.type() == Proxy.Type.DIRECT) {
            return "DIRECT";
        }
        if (proxy.address() instanceof InetSocketAddress address) {
            return proxy.type() + ":" + SafeSocketAddressFormatter.hostPort(address);
        }
        return proxy.type().toString();
    }

    private String describeManualProxyConfig(HttpRuntimeSettings settings,
                                             HttpRequestProxyPolicy policy,
                                             boolean appProxyEnabled,
                                             boolean systemProxyMode) {
        if (systemProxyMode || policy == HttpRequestProxyPolicy.NO_PROXY
                || (policy != HttpRequestProxyPolicy.USE_PROXY && !appProxyEnabled)) {
            return "NOT_USED";
        }
        String host = settings.getProxyHost();
        if (host == null || host.isBlank()) {
            return "MISSING_HOST";
        }
        if (!SafeSocketAddressFormatter.isSafeHost(host.trim())) {
            return "INVALID_HOST";
        }
        int port = settings.getProxyPort();
        return port > 0 && port <= 65535 ? "FIELDS_PRESENT" : "INVALID_PORT";
    }

    private void applyRequestSettings(OkHttpClient.Builder builder,
                                      PreparedRequest preparedRequest) {
        if (!preparedRequest.cookieJarEnabled) {
            builder.cookieJar(CookieJar.NO_COOKIES);
        }

        applyDigestAuthenticator(builder, preparedRequest);

        String httpVersion = preparedRequest.httpVersion != null
                ? preparedRequest.httpVersion
                : HttpRequestItem.HTTP_VERSION_AUTO;
        if (HttpRequestItem.HTTP_VERSION_HTTP_1_1.equals(httpVersion)) {
            builder.protocols(List.of(Protocol.HTTP_1_1));
        } else if (HttpRequestItem.HTTP_VERSION_HTTP_2.equals(httpVersion)) {
            builder.protocols(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1));
        }
    }

    private void applyWebSocketSettings(OkHttpClient.Builder builder,
                                        PreparedRequest preparedRequest) {
        if (!isWebSocketRequest(preparedRequest)) {
            return;
        }
        int intervalMs = Math.max(0, preparedRequest.webSocketPingIntervalMs);
        builder.pingInterval(intervalMs, TimeUnit.MILLISECONDS);
    }

    private void applyDigestAuthenticator(OkHttpClient.Builder builder,
                                          PreparedRequest preparedRequest) {
        TransportAuth auth = preparedRequest != null ? preparedRequest.transportAuth : null;
        if (auth == null || !auth.isDigest()) {
            return;
        }
        if (isBlank(auth.username) || containsUnresolvedPlaceholder(auth.username)
                || containsUnresolvedPlaceholder(auth.password)) {
            return;
        }
        builder.authenticator(new DigestAuthenticator(
                auth.username,
                auth.password == null ? "" : auth.password
        ));
    }

    private SSLConfigurationUtil.SSLVerificationMode resolveGlobalSslVerificationMode() {
        return resolveGlobalSslVerificationMode(null);
    }

    private SSLConfigurationUtil.SSLVerificationMode resolveGlobalSslVerificationMode(String url) {
        boolean proxySslDisabled = false;
        if (url != null && !url.isBlank()) {
            proxySslDisabled = HttpRequestRuntimeSettingsResolver.isProxySslVerificationForcedDisabled(url);
        }
        return (HttpRuntimeSettingsProvider.get().isRequestSslVerificationDisabled() || proxySslDisabled)
                ? SSLConfigurationUtil.SSLVerificationMode.LENIENT
                : SSLConfigurationUtil.SSLVerificationMode.STRICT;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean containsUnresolvedPlaceholder(String value) {
        return value != null && value.contains("{{") && value.contains("}}");
    }

    private boolean isWebSocketRequest(PreparedRequest request) {
        if (request == null || request.url == null) {
            return false;
        }
        String lower = request.url.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("ws://") || lower.startsWith("wss://");
    }
}
