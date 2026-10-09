package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.config.HttpRequestRuntimeSettingsResolver;
import com.laker.postman.http.runtime.config.HttpRuntimeSettings;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.model.HttpCapturePolicy;
import com.laker.postman.http.runtime.model.HttpCaptureProfiles;
import com.laker.postman.http.runtime.model.HttpExchangeKind;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpRouteAttempt;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.observation.NetworkLogSupport;
import com.laker.postman.http.runtime.observation.SafeSocketAddressFormatter;
import com.laker.postman.http.runtime.error.NetworkErrorMessageResolver;
import com.laker.postman.http.runtime.ssl.CertificateCapturingSSLSocketFactory;
import com.laker.postman.http.runtime.ssl.SSLCertificateValidator;
import com.laker.postman.http.runtime.ssl.SSLConfigurationUtil;
import com.laker.postman.http.runtime.ssl.SSLValidationResult;
import com.laker.postman.http.runtime.transport.HttpExchangeTraceSupport;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestProxyPolicy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 事件监听器，既记录详细连接事件和耗时，也统计连接信息
 */
@Slf4j
public class OkHttpExchangeEventListener extends EventListener {
    // Match OkHttp's HTTP/2 codec filtering; this is a logical view, not captured wire bytes.
    private static final Set<String> HTTP2_OMITTED_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-connection", "transfer-encoding", "encoding", "upgrade",
            ":method", ":path", ":scheme", ":authority");
    private long callStartNanos;
    private Long lastRequestSendOffset;
    private final HttpEventInfo info;
    private final PreparedRequest preparedRequest;
    private final HttpExchangeKind exchangeKind;

    // 精细化控制开关
    private final boolean collectMetricsInfo; // 是否收集轻量统计指标（时间戳、发送/接收字节）
    private final boolean collectEventInfo; // 是否收集完整事件信息（DNS、连接等）
    private final boolean enableNetworkLog; // 是否启用网络日志面板输出
    private final Object routeAttemptLock = new Object();
    private final AtomicBoolean socksProtocolMismatchLogged = new AtomicBoolean();
    private final Set<String> hiddenDiagnosticHosts;
    private final List<PendingRouteAttempt> pendingRouteAttempts = new ArrayList<>();
    private final Map<NetworkLogEventStage, Long> phaseStartOffsets;
    private boolean successfulRouteConnected;

    public OkHttpExchangeEventListener(PreparedRequest preparedRequest) {
        this(preparedRequest, HttpExchangeKind.HTTP);
    }

    public OkHttpExchangeEventListener(PreparedRequest preparedRequest, HttpExchangeKind exchangeKind) {
        this.callStartNanos = System.nanoTime();
        this.info = new HttpEventInfo();
        this.preparedRequest = preparedRequest;
        this.exchangeKind = exchangeKind == null ? HttpExchangeKind.HTTP : exchangeKind;
        HttpExchangeTraceSupport.bindToRequest(preparedRequest, info);
        HttpCapturePolicy capturePolicy = HttpCaptureProfiles.resolve(preparedRequest);
        this.collectMetricsInfo = capturePolicy.collectMetrics();
        this.collectEventInfo = capturePolicy.collectEventDetails();
        this.enableNetworkLog = capturePolicy.emitNetworkLog();
        this.phaseStartOffsets = enableNetworkLog ? new ConcurrentHashMap<>() : Collections.emptyMap();
        this.hiddenDiagnosticHosts = collectEventInfo
                ? ConcurrentHashMap.newKeySet() : Collections.emptySet();
    }

    /**
     * 发布网络日志事件（仅在 enableNetworkLog=true 时使用）。
     */
    private void log(NetworkLogEventStage stage, String msg) {
        log(stage, msg, null);
    }

    private void log(NetworkLogEventStage stage, String msg, Long durationMs) {
        // 只有启用了网络日志才向外发布事件，具体展示由调用方注入的 sink 负责。
        if (!enableNetworkLog) {
            return;
        }

        long now = System.nanoTime();
        long elapsedMs = (now - callStartNanos) / 1_000_000;
        NetworkLogEventStage startStage = phaseStartStage(stage);
        if (startStage == stage) {
            phaseStartOffsets.put(stage, elapsedMs);
        } else if (startStage != null) {
            Long startOffset = phaseStartOffsets.remove(startStage);
            if (startOffset != null) {
                // Use the same monotonic clock and rounding as the displayed +Nms offsets.
                durationMs = Math.max(0L, elapsedMs - startOffset);
            }
        }
        if (stage == NetworkLogEventStage.REQUEST_HEADERS_END || stage == NetworkLogEventStage.REQUEST_BODY_END) {
            lastRequestSendOffset = elapsedMs;
        } else if (stage == NetworkLogEventStage.RESPONSE_HEADERS_END
                || stage == NetworkLogEventStage.RESPONSE_HEADERS_END_REDIRECT) {
            if (lastRequestSendOffset != null) {
                int statusEnd = msg.indexOf('\n', msg.startsWith("\n") ? 1 : 0);
                if (statusEnd >= 0) {
                    msg = msg.substring(0, statusEnd + 1)
                            + "Wait: " + Math.max(0L, elapsedMs - lastRequestSendOffset) + "ms\n"
                            + msg.substring(statusEnd + 1);
                }
            }
            // Header parsing time is less useful here than the wait after the request was sent.
            durationMs = null;
        }
        if (shouldDelegateRealtimeStage(stage) || isRedundantHttpStage(stage)) {
            return;
        }
        NetworkLogSupport.append(preparedRequest, stage,
                containsEndpointDiagnostics(stage) ? safeDiagnosticText(msg) : msg,
                elapsedMs, durationMs);
    }

    private boolean isRedundantHttpStage(NetworkLogEventStage stage) {
        return exchangeKind == HttpExchangeKind.HTTP && (stage == NetworkLogEventStage.REQUEST_HEADERS_START
                || stage == NetworkLogEventStage.RESPONSE_HEADERS_START
                || stage == NetworkLogEventStage.RESPONSE_BODY_START
                || stage == NetworkLogEventStage.PROXY_SELECT_START
                || stage == NetworkLogEventStage.CONNECT_END
                || stage == NetworkLogEventStage.CONNECTION_RELEASED);
    }

    private static NetworkLogEventStage phaseStartStage(NetworkLogEventStage stage) {
        return switch (stage) {
            case DISPATCHER_QUEUE_START, PROXY_SELECT_START, DNS_START, SECURE_CONNECT_START,
                    REQUEST_HEADERS_START, REQUEST_BODY_START, RESPONSE_HEADERS_START, RESPONSE_BODY_START -> stage;
            case DISPATCHER_QUEUE_END -> NetworkLogEventStage.DISPATCHER_QUEUE_START;
            case PROXY_SELECT_END -> NetworkLogEventStage.PROXY_SELECT_START;
            case DNS_END -> NetworkLogEventStage.DNS_START;
            case SECURE_CONNECT_END -> NetworkLogEventStage.SECURE_CONNECT_START;
            case REQUEST_HEADERS_END -> NetworkLogEventStage.REQUEST_HEADERS_START;
            case REQUEST_BODY_END -> NetworkLogEventStage.REQUEST_BODY_START;
            case RESPONSE_HEADERS_END, RESPONSE_HEADERS_END_REDIRECT -> NetworkLogEventStage.RESPONSE_HEADERS_START;
            case RESPONSE_BODY_END -> NetworkLogEventStage.RESPONSE_BODY_START;
            default -> null;
        };
    }

    private static boolean containsEndpointDiagnostics(NetworkLogEventStage stage) {
        return switch (stage) {
            case PROXY_SELECT_END, DNS_START, DNS_END, CONNECT_START, CONNECT_END, CONNECT_FAILED,
                    CONNECTION_ACQUIRED, CONNECTION_RELEASED, RETRY_DECISION,
                    REQUEST_FAILED, RESPONSE_FAILED, CALL_FAILED -> true;
            default -> false;
        };
    }

    @Override
    public void callStart(Call call) {
        if (!collectMetricsInfo) {
            return;
        }
        callStartNanos = System.nanoTime();
        lastRequestSendOffset = null;
        if (enableNetworkLog) {
            phaseStartOffsets.clear();
        }
        if (collectEventInfo) {
            SSLConfigurationUtil.clearValidationResult();
            CertificateCapturingSSLSocketFactory.clearLastCapturedCertificates();
        }
        info.setCallStart(System.currentTimeMillis());
        info.setThreadName(Thread.currentThread().getName());
        Request request = call.request();
        if (enableNetworkLog) {
            OkHttpRequestSnapshotCapture.capture(preparedRequest, request, false);
        }
        if (enableNetworkLog && exchangeKind != HttpExchangeKind.WEBSOCKET) {
            log(NetworkLogEventStage.CALL_START, formatCallStart(request));
        }
    }

    @Override
    public void dispatcherQueueStart(Call call, Dispatcher dispatcher) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setDispatcherQueueStart(System.currentTimeMillis());
        log(NetworkLogEventStage.DISPATCHER_QUEUE_START, "Waiting for dispatcher capacity");
    }

    @Override
    public void dispatcherQueueEnd(Call call, Dispatcher dispatcher) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setDispatcherQueueEnd(System.currentTimeMillis());
        log(NetworkLogEventStage.DISPATCHER_QUEUE_END, "Dispatcher capacity acquired",
                duration(info.getDispatcherQueueStart(), info.getDispatcherQueueEnd()));
    }

    private String formatCallStart(Request request) {
        if (exchangeKind == HttpExchangeKind.ASYNC_SSE) {
            String sseUrl = valueOrDash(preparedRequest != null ? preparedRequest.url : null);
            return "\nSSE URL: " + sseUrl + "\n"
                    + "Stream Request: " + request.method() + " " + request.url() + "\n"
                    + "Stream Flow: HTTP " + request.method()
                    + " + text/event-stream response body stays open\n";
        }
        return request.method() + " " + request.url();
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean shouldDelegateRealtimeStage(NetworkLogEventStage stage) {
        if (exchangeKind == HttpExchangeKind.HTTP) {
            return false;
        }
        boolean delegatedLifecycleStage = stage == NetworkLogEventStage.REQUEST_HEADERS_END
                || stage == NetworkLogEventStage.REQUEST_BODY_START
                || stage == NetworkLogEventStage.RESPONSE_HEADERS_END
                || stage == NetworkLogEventStage.RESPONSE_BODY_START
                || stage == NetworkLogEventStage.CALL_END
                || stage == NetworkLogEventStage.CALL_FAILED;
        return delegatedLifecycleStage
                || (exchangeKind == HttpExchangeKind.ASYNC_SSE
                && stage == NetworkLogEventStage.CANCELED);
    }

    @Override
    public void proxySelectStart(Call call, HttpUrl url) {
        if (!collectEventInfo) {
            return;
        }
        info.setProxySelectStart(System.currentTimeMillis());
        log(NetworkLogEventStage.PROXY_SELECT_START, "Selecting proxy for " + url);
    }

    @Override
    public void proxySelectEnd(Call call, HttpUrl url, List<Proxy> proxies) {
        if (!collectEventInfo) {
            return;
        }
        info.setProxySelectEnd(System.currentTimeMillis());
        if (exchangeKind == HttpExchangeKind.HTTP && proxies.stream().allMatch(proxy -> proxy.type() == Proxy.Type.DIRECT)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Proxies: ");
        for (Proxy proxy : proxies) {
            sb.append(proxy.type()).append(" ");
            if (proxy.address() instanceof InetSocketAddress address) {
                // getHostName() may trigger an unrelated reverse-DNS lookup while tracing.
                safeDiagnosticHost(address.getHostString());
                sb.append(SafeSocketAddressFormatter.hostPort(address)).append(" ");
            }
        }
        log(NetworkLogEventStage.PROXY_SELECT_END, sb.toString(),
                duration(info.getProxySelectStart(), info.getProxySelectEnd()));
    }

    @Override
    public void dnsStart(Call call, String domainName) {
        if (!collectEventInfo) {
            return;
        }
        info.setDnsStart(System.currentTimeMillis());
        info.setDnsHost(safeDiagnosticHost(domainName));
        info.setDnsError(null);
        info.replaceDnsAddresses(List.of());
        log(NetworkLogEventStage.DNS_START, info.getDnsHost());
    }

    @Override
    public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
        if (!collectEventInfo) {
            return;
        }
        info.setDnsEnd(System.currentTimeMillis());
        String displayedHost = safeDiagnosticHost(domainName);
        List<String> addresses = inetAddressList.stream()
                .map(OkHttpExchangeEventListener::concreteAddress)
                .toList();
        info.replaceDnsAddresses(addresses);
        log(NetworkLogEventStage.DNS_END, displayedHost + " -> " + addresses,
                duration(info.getDnsStart(), info.getDnsEnd()));
    }

    @Override
    public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
        if (!collectEventInfo) {
            return;
        }
        long startTime = System.currentTimeMillis();
        safeDiagnosticHost(inetSocketAddress.getHostString());
        synchronized (routeAttemptLock) {
            pendingRouteAttempts.add(new PendingRouteAttempt(
                    inetSocketAddress,
                    routeAddress(inetSocketAddress),
                    addressFamily(inetSocketAddress),
                    startTime,
                    System.nanoTime()
            ));
        }
        log(NetworkLogEventStage.CONNECT_START,
                SafeSocketAddressFormatter.socketAddress(inetSocketAddress) + " via " + proxy.type());
    }

    @Override
    public void secureConnectStart(Call call) {
        if (!collectEventInfo) {
            return;
        }
        info.setSecureConnectStart(System.currentTimeMillis());
        log(NetworkLogEventStage.SECURE_CONNECT_START, "");
    }

    @Override
    public void secureConnectEnd(Call call, Handshake handshake) {
        if (!collectEventInfo) {
            CertificateCapturingSSLSocketFactory.clearLastCapturedCertificates();
            SSLConfigurationUtil.clearValidationResult();
            return;
        }
        info.setSecureConnectEnd(System.currentTimeMillis());
        try {
            if (handshake != null) {
                info.setTlsVersion(handshake.tlsVersion().javaName());
                info.setCipherName(handshake.cipherSuite().toString());

                // 首先尝试从 Handshake 获取证书
                List<Certificate> peerCerts = handshake.peerCertificates();
                log.debug("=== SSL Handshake Debug ===");
                log.debug("TLS Version: {}", handshake.tlsVersion());
                log.debug("Cipher Suite: {}", handshake.cipherSuite());
                log.debug("Peer Certificates from Handshake: {}", peerCerts.size());

                // 如果 Handshake 中的证书为空，尝试从同线程的 SSLSession 捕获缓存获取
                if (peerCerts.isEmpty()) {
                    log.warn("⚠️  Handshake.peerCertificates() is empty, trying to get from SSLSession...");

                    peerCerts = CertificateCapturingSSLSocketFactory.getLastCapturedCertificates();
                    log.debug("Peer Certificates from SSLSession cache: {}", peerCerts.size());

                    if (!peerCerts.isEmpty()) {
                        log.debug("✅ Successfully retrieved {} certificates from SSLSession cache!", peerCerts.size());
                    } else {
                        log.error("❌ Failed to retrieve certificates from both Handshake and SSLSession!");
                    }
                }

                if (!peerCerts.isEmpty()) {
                    for (int i = 0; i < peerCerts.size(); i++) {
                        Certificate cert = peerCerts.get(i);
                        log.debug("Certificate[{}]: Type={}, Class={}", i, cert.getType(), cert.getClass().getName());
                        if (cert instanceof X509Certificate x509) {
                            log.debug("  Subject: {}", x509.getSubjectX500Principal().getName());
                            log.debug("  Issuer: {}", x509.getIssuerX500Principal().getName());
                            log.debug("  Valid: {} to {}", x509.getNotBefore(), x509.getNotAfter());
                        }
                    }
                }

                info.setPeerCertificates(peerCerts);
                info.setLocalCertificates(handshake.localCertificates());

                // 验证证书并记录警告信息
                StringBuilder allWarnings = new StringBuilder();
                try {
                    String hostname = call.request().url().host();

                    // 1. 检查在握手阶段捕获的SSL验证错误（如untrusted root, hostname mismatch）
                    SSLValidationResult validationResult = SSLConfigurationUtil.getLastValidationResult();
                    if (validationResult != null && validationResult.hasErrors()) {
                        String sslValidationError = validationResult.getSummary();
                        allWarnings.append(sslValidationError);
                        log.warn("SSL Validation Error: {}", sslValidationError);
                    }

                    // 2. 对证书本身进行检查（如expired, self-signed等）
                    SSLValidationResult certValidationResult = SSLCertificateValidator.validateCertificates(
                            peerCerts,
                            hostname
                    );
                    if (certValidationResult != null && (certValidationResult.hasErrors() || certValidationResult.hasWarnings())) {
                        String certWarning = certValidationResult.getSummary();
                        if (certWarning != null && !certWarning.isEmpty()) {
                            if (!allWarnings.isEmpty()) {
                                allWarnings.append("; ");
                            }
                            allWarnings.append(certWarning);
                        }
                    }

                    // 合并所有警告
                    if (!allWarnings.isEmpty()) {
                        info.setSslCertWarning(allWarnings.toString());
                    }

                } catch (Exception e) {
                    log.debug("Error validating certificate: {}", e.getMessage());
                }
            }
            // 记录handshake信息
            if (handshake != null) {
                StringBuilder handshakeInfo = new StringBuilder();
                handshakeInfo.append("TLS connection: ")
                        .append(handshake.tlsVersion().javaName())
                        .append(" / ")
                        .append(handshake.cipherSuite())
                        .append("\n");
                boolean verificationEnabled = preparedRequest != null && preparedRequest.sslVerificationEnabled
                        && !HttpRequestRuntimeSettingsResolver.isProxySslVerificationForcedDisabled(
                                preparedRequest.url, preparedRequest.proxyPolicy);
                handshakeInfo.append("Verification: ").append(verificationEnabled ? "enabled" : "disabled").append("\n");
                boolean hasWarning = info.getSslCertWarning() != null && !info.getSslCertWarning().isBlank();
                List<Certificate> peerCertificates = info.getPeerCertificates();
                // Certificate metadata remains available in the trace; expand it in the log only for a warning.
                if (hasWarning && peerCertificates != null && !peerCertificates.isEmpty()) {
                    Certificate cert = peerCertificates.get(0);
                    if (cert instanceof X509Certificate x509) {
                        handshakeInfo.append("Server certificate:\n");
                        handshakeInfo.append(" subject: ").append(x509.getSubjectX500Principal()).append("\n");
                        handshakeInfo.append(" start date: ")
                                .append(DateTimeFormatter.ISO_INSTANT.format(x509.getNotBefore().toInstant())).append("\n");
                        handshakeInfo.append(" expire date: ")
                                .append(DateTimeFormatter.ISO_INSTANT.format(x509.getNotAfter().toInstant())).append("\n");
                        Collection<List<?>> altNames = null;
                        try {
                            altNames = x509.getSubjectAlternativeNames();
                        } catch (Exception ignored) {
                        }
                        if (altNames != null) {
                            handshakeInfo.append(" subjectAltName: ");
                            for (List<?> altName : altNames) {
                                if (altName.size() > 1) {
                                    handshakeInfo.append(altName.get(1)).append(", ");
                                }
                            }
                            if (handshakeInfo.length() >= 2 && handshakeInfo.charAt(handshakeInfo.length() - 2) == ',') {
                                handshakeInfo.setLength(handshakeInfo.length() - 2);
                            }
                            handshakeInfo.append("\n");
                        }
                        handshakeInfo.append(" issuer: ").append(x509.getIssuerX500Principal()).append("\n");
                    }
                }

                // 如果有证书警告，添加到日志中
                if (hasWarning) {
                    handshakeInfo.append("⚠️  Certificate Warning: ").append(info.getSslCertWarning()).append("\n");
                }

                log(NetworkLogEventStage.SECURE_CONNECT_END, handshakeInfo.toString(),
                        duration(info.getSecureConnectStart(), info.getSecureConnectEnd()));
            } else {
                log(NetworkLogEventStage.SECURE_CONNECT_END, "no handshake",
                        duration(info.getSecureConnectStart(), info.getSecureConnectEnd()));
            }
        } finally {
            CertificateCapturingSSLSocketFactory.clearLastCapturedCertificates();
            SSLConfigurationUtil.clearValidationResult();
        }
    }

    @Override
    public void connectEnd(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol) {
        if (!collectEventInfo) {
            return;
        }
        long endTime = System.currentTimeMillis();
        info.setProtocol(protocol == null ? null : protocol.toString());
        HttpRouteAttempt attempt;
        synchronized (routeAttemptLock) {
            attempt = completeRouteAttemptLocked(inetSocketAddress, endTime, true, false,
                    protocol == null ? null : protocol.toString(), null);
            if (protocol != null) {
                successfulRouteConnected = true;
                markCanceledFallbackRoutesLocked(attempt);
            }
        }
        if (attempt != null) {
            // Summary timing always describes the winning route. Failed fallback
            // attempts remain visible in routeAttempts without overwriting it.
            info.setConnectStart(attempt.startTime());
            info.setConnectEnd(attempt.endTime());
        }
        log(NetworkLogEventStage.CONNECT_END,
                SafeSocketAddressFormatter.socketAddress(inetSocketAddress)
                        + " via " + proxy.type() + ", protocol=" + protocol,
                attempt == null ? null : attempt.durationMs());
    }

    @Override
    public void connectFailed(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol, IOException ioe) {
        logSocksProtocolMismatch(proxy, ioe);
        if (!collectEventInfo) {
            return;
        }
        safeDiagnosticHost(inetSocketAddress.getHostString());
        HttpRouteAttempt attempt;
        synchronized (routeAttemptLock) {
            boolean canceled = (call != null && call.isCanceled())
                    || (successfulRouteConnected && isRouteCancellationSignal(ioe));
            attempt = completeRouteAttemptLocked(
                    inetSocketAddress,
                    System.currentTimeMillis(),
                    false,
                    canceled,
                    protocol == null ? null : protocol.toString(),
                    exceptionMessage(ioe)
            );
        }
        log(NetworkLogEventStage.CONNECT_FAILED,
                SafeSocketAddressFormatter.socketAddress(inetSocketAddress)
                        + " via " + proxy.type() + ", protocol=" + protocol + ", error: " + exceptionMessage(ioe),
                attempt == null ? null : attempt.durationMs());
    }

    private void logSocksProtocolMismatch(Proxy proxy, IOException exception) {
        if (!enableNetworkLog || !NetworkErrorMessageResolver.isSocksProtocolMismatch(exception.getMessage())) {
            return;
        }
        if (!socksProtocolMismatchLogged.compareAndSet(false, true)) {
            return;
        }
        String routeProxy = proxy == null ? "UNKNOWN" : proxy.type().name();
        HttpRequestProxyPolicy requestPolicy = HttpRequestProxyPolicy.normalize(
                preparedRequest == null ? null : preparedRequest.proxyPolicy);
        try {
            HttpRuntimeSettings settings = HttpRuntimeSettingsProvider.get();
            boolean appProxyEnabled = settings.isProxyEnabled();
            boolean systemProxyMode = settings.isSystemProxyMode();
            boolean manualProxyRequested = requestPolicy != HttpRequestProxyPolicy.NO_PROXY
                    && !systemProxyMode
                    && (requestPolicy == HttpRequestProxyPolicy.USE_PROXY || appProxyEnabled);
            String manualProxyType = "NOT_USED";
            if (manualProxyRequested) {
                manualProxyType = HttpRuntimeSettings.PROXY_TYPE_SOCKS.equalsIgnoreCase(settings.getProxyType())
                        ? "SOCKS" : "HTTP";
            }
            log.warn("SOCKS_PROTOCOL_MISMATCH requestPolicy={} appProxyEnabled={} appProxyMode={} "
                            + "manualProxyType={} routeProxy={}",
                    requestPolicy, appProxyEnabled, systemProxyMode ? "SYSTEM" : "MANUAL",
                    manualProxyType, routeProxy);
        } catch (RuntimeException ignored) {
            log.warn("SOCKS_PROTOCOL_MISMATCH requestPolicy={} routeProxy={} proxySettings=unavailable",
                    requestPolicy, routeProxy);
        }
    }

    private HttpRouteAttempt completeRouteAttemptLocked(InetSocketAddress address,
                                                        long endTime,
                                                        boolean connected,
                                                        boolean canceled,
                                                        String protocol,
                                                        String error) {
        HttpRouteAttempt completedAttempt = null;
        for (int i = pendingRouteAttempts.size() - 1; i >= 0; i--) {
            PendingRouteAttempt pending = pendingRouteAttempts.get(i);
            if (!pending.socketAddress.equals(address)) {
                continue;
            }
            pendingRouteAttempts.remove(i);
            completedAttempt = toRouteAttempt(pending, endTime, connected, canceled, protocol, error);
            break;
        }
        info.addRouteAttempt(completedAttempt);
        return completedAttempt;
    }

    /**
     * OkHttp cancels in-flight Fast Fallback plans as soon as one route wins.
     * Those plans still report connectFailed("canceled"/"Socket closed"), often
     * before the winner's TLS setup reaches connectEnd. Reclassify only matching
     * cancellation signals whose lifetime overlapped the winning route.
     */
    private void markCanceledFallbackRoutesLocked(HttpRouteAttempt winningAttempt) {
        if (winningAttempt == null) {
            return;
        }
        List<HttpRouteAttempt> attempts = info.getRouteAttempts();
        for (int i = 0; i < attempts.size(); i++) {
            HttpRouteAttempt attempt = attempts.get(i);
            if (attempt.connected() || attempt.canceled()
                    || !isRouteCancellationSignal(attempt.error())
                    || attempt.endTime() < winningAttempt.startTime()) {
                continue;
            }
            info.replaceRouteAttempt(i, new HttpRouteAttempt(
                    attempt.address(),
                    attempt.addressFamily(),
                    attempt.startTime(),
                    attempt.endTime(),
                    attempt.durationMs(),
                    false,
                    true,
                    attempt.protocol(),
                    attempt.error()
            ));
        }
    }

    private void completePendingRouteAttempts(long endTime, boolean canceled, String error) {
        List<HttpRouteAttempt> completedAttempts = new ArrayList<>();
        synchronized (routeAttemptLock) {
            for (PendingRouteAttempt pending : pendingRouteAttempts) {
                completedAttempts.add(toRouteAttempt(pending, endTime, false, canceled, null, error));
            }
            pendingRouteAttempts.clear();
        }
        completedAttempts.forEach(info::addRouteAttempt);
    }

    private void applyFailedRouteWindowToSummary() {
        if (info.getConnectStart() > 0 || info.getConnectEnd() > 0) {
            return;
        }
        long earliestStart = Long.MAX_VALUE;
        long latestEnd = 0L;
        for (HttpRouteAttempt attempt : info.getRouteAttempts()) {
            if (attempt.startTime() > 0) {
                earliestStart = Math.min(earliestStart, attempt.startTime());
            }
            latestEnd = Math.max(latestEnd, attempt.endTime());
        }
        if (earliestStart != Long.MAX_VALUE && latestEnd >= earliestStart) {
            info.setConnectStart(earliestStart);
            info.setConnectEnd(latestEnd);
        }
    }

    private static HttpRouteAttempt toRouteAttempt(PendingRouteAttempt pending,
                                                   long endTime,
                                                   boolean connected,
                                                   boolean canceled,
                                                   String protocol,
                                                   String error) {
        long durationMs = Math.max(0L,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pending.startNanos));
        return new HttpRouteAttempt(
                pending.address,
                pending.addressFamily,
                pending.startTime,
                endTime,
                durationMs,
                connected,
                canceled,
                protocol,
                error
        );
    }

    private static String routeAddress(InetSocketAddress address) {
        InetAddress resolvedAddress = address.getAddress();
        if (resolvedAddress == null) {
            return SafeSocketAddressFormatter.hostPort(address);
        }
        return socketEndpoint(resolvedAddress, address.getPort());
    }

    private static String concreteAddress(InetAddress address) {
        if (address == null) {
            return "";
        }
        String value = address.getHostAddress();
        return address instanceof Inet6Address ? "[" + value + "]" : value;
    }

    private static String socketEndpoint(InetAddress address, int port) {
        String host = address.getHostAddress();
        if (address instanceof Inet6Address) {
            host = "[" + host + "]";
        }
        return host + ":" + port;
    }

    private static String addressFamily(InetSocketAddress address) {
        InetAddress inetAddress = address.getAddress();
        if (inetAddress instanceof Inet6Address) {
            return "IPv6";
        }
        if (inetAddress != null) {
            return "IPv4";
        }
        return "Unknown";
    }

    private static final class PendingRouteAttempt {
        private final InetSocketAddress socketAddress;
        private final String address;
        private final String addressFamily;
        private final long startTime;
        private final long startNanos;

        private PendingRouteAttempt(InetSocketAddress socketAddress,
                                    String address,
                                    String addressFamily,
                                    long startTime,
                                    long startNanos) {
            this.socketAddress = socketAddress;
            this.address = address;
            this.addressFamily = addressFamily;
            this.startTime = startTime;
            this.startNanos = startNanos;
        }
    }

    @Override
    public void connectionAcquired(Call call, Connection connection) {
        if (!collectEventInfo) {
            return;
        }
        info.setConnectionAcquired(System.currentTimeMillis());
        try {
            Socket socket = connection.socket();
            String local = socketEndpoint(socket.getLocalAddress(), socket.getLocalPort());
            String remote = socketEndpoint(socket.getInetAddress(), socket.getPort());
            info.setLocalAddress(local);
            info.setRemoteAddress(remote);
            if (connection.protocol() != null) {
                info.setProtocol(connection.protocol().toString());
            }
        } catch (Exception e) {
            info.setLocalAddress("无法获取");
            info.setRemoteAddress("无法获取");
        }
        boolean reused = info.getConnectStart() <= 0;
        String label = reused ? "Connection reused" : "Connection acquired";
        Long setupDuration = reused ? null : info.getRouteAttempts().stream()
                .filter(attempt -> attempt.connected() && attempt.startTime() == info.getConnectStart())
                .map(HttpRouteAttempt::durationMs)
                .findFirst().orElse(null);
        log(NetworkLogEventStage.CONNECTION_ACQUIRED, label + ": " + connectionRouteDescription(connection)
                + ", local=" + info.getLocalAddress() + ", remote=" + info.getRemoteAddress(), setupDuration);
    }

    @Override
    public void connectionReleased(Call call, Connection connection) {
        if (!collectEventInfo) {
            return;
        }
        info.setConnectionReleased(System.currentTimeMillis());
        log(NetworkLogEventStage.CONNECTION_RELEASED, "Connection use released");
    }

    private static String connectionRouteDescription(Connection connection) {
        try {
            Route route = connection.route();
            return "proxy=" + route.proxy().type()
                    + ", protocol=" + connection.protocol();
        } catch (RuntimeException ignored) {
            return "route unavailable";
        }
    }

    @Override
    public void requestHeadersStart(Call call) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setRequestHeadersStart(System.currentTimeMillis());
        log(NetworkLogEventStage.REQUEST_HEADERS_START, "");
    }

    @Override
    public void requestHeadersEnd(Call call, Request request) {
        Headers headers = request.headers();
        if (enableNetworkLog) {
            OkHttpRequestSnapshotCapture.capture(preparedRequest, request, false);
        }
        if (!collectMetricsInfo) {
            return;
        }
        info.setHeaderBytesSent(headers.toString().getBytes(StandardCharsets.UTF_8).length);
        info.setRequestHeadersEnd(System.currentTimeMillis());
        if (enableNetworkLog) {
            log(NetworkLogEventStage.REQUEST_HEADERS_END, formatSentHeaders(),
                    duration(info.getRequestHeadersStart(), info.getRequestHeadersEnd()));
        }
    }

    @Override
    public void requestBodyStart(Call call) {
        if (collectMetricsInfo) {
            info.setRequestBodyStart(System.currentTimeMillis());
            if (enableNetworkLog && !(exchangeKind == HttpExchangeKind.HTTP && isEmptyRequestBody(call))) {
                log(NetworkLogEventStage.REQUEST_BODY_START, formatSentRequestBody());
            }
        }
    }

    @Override
    public void requestBodyEnd(Call call, long byteCount) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setBodyBytesSent(byteCount);
        info.setRequestBodyEnd(System.currentTimeMillis());
        if (exchangeKind == HttpExchangeKind.HTTP && byteCount == 0L) {
            // Still record the send completion for response waiting time and timeline metrics.
            if (enableNetworkLog) {
                lastRequestSendOffset = (System.nanoTime() - callStartNanos) / 1_000_000;
                phaseStartOffsets.remove(NetworkLogEventStage.REQUEST_BODY_START);
            }
        } else {
            log(NetworkLogEventStage.REQUEST_BODY_END, "bytes=" + byteCount,
                    duration(info.getRequestBodyStart(), info.getRequestBodyEnd()));
        }
    }

    @Override
    public void requestFailed(Call call, IOException ioe) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setErrorMessage(safeDiagnosticText(NetworkErrorMessageResolver.toUserFriendlyMessage(ioe)));
        info.setError(ioe);
        if (!enableNetworkLog) {
            return;
        }
        log(NetworkLogEventStage.REQUEST_FAILED, exceptionMessage(ioe));
    }

    @Override
    public void responseHeadersStart(Call call) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setResponseHeadersStart(System.currentTimeMillis());
        log(NetworkLogEventStage.RESPONSE_HEADERS_START, "");
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setHeaderBytesReceived(response.headers().toString().getBytes(StandardCharsets.UTF_8).length);
        info.setResponseHeadersEnd(System.currentTimeMillis());
        if (!enableNetworkLog) {
            return;
        }
        StringBuilder sb = new StringBuilder("\n");
        boolean isRedirect = response.isRedirect();
        String protocol = response.protocol() == Protocol.HTTP_2 || response.protocol() == Protocol.H2_PRIOR_KNOWLEDGE
                ? "HTTP/2" : response.protocol().toString().toUpperCase(Locale.ROOT);
        sb.append(protocol).append(" ").append(response.code());
        if (!response.message().isBlank()) {
            sb.append(" ").append(response.message());
        }
        sb.append("\n");
        // 处理响应头
        Headers headers = response.headers();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String value = headers.value(i);
            if (name.equalsIgnoreCase("set-cookie")) {
                // 只保留可见字符，避免乱码
                value = value.replaceAll("[^\\x20-\\x7E]", "");
            }
            sb.append(name).append(": ").append(value).append("\n");
        }
        // 如果是重定向，使用橙色高亮
        if (isRedirect) {
            log(NetworkLogEventStage.RESPONSE_HEADERS_END_REDIRECT, sb.toString(),
                    duration(info.getResponseHeadersStart(), info.getResponseHeadersEnd()));
        } else {
            log(NetworkLogEventStage.RESPONSE_HEADERS_END, sb.toString(),
                    duration(info.getResponseHeadersStart(), info.getResponseHeadersEnd()));
        }
    }

    @Override
    public void responseBodyStart(Call call) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setResponseBodyStart(System.currentTimeMillis());
        log(NetworkLogEventStage.RESPONSE_BODY_START, "");
    }

    @Override
    public void responseBodyEnd(Call call, long byteCount) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setBodyBytesReceived(byteCount);
        info.setResponseBodyEnd(System.currentTimeMillis());
        log(NetworkLogEventStage.RESPONSE_BODY_END, "bytes=" + byteCount,
                duration(info.getResponseBodyStart(), info.getResponseBodyEnd()));
    }

    @Override
    public void responseFailed(Call call, IOException ioe) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setErrorMessage(safeDiagnosticText(NetworkErrorMessageResolver.toUserFriendlyMessage(ioe)));
        info.setError(ioe);
        if (!enableNetworkLog) {
            return;
        }
        log(NetworkLogEventStage.RESPONSE_FAILED, exceptionMessage(ioe));
    }

    @Override
    public void callEnd(Call call) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setCallEnd(System.currentTimeMillis());
        completePendingRouteAttempts(info.getCallEnd(), true, "Canceled after another route connected");
        log(NetworkLogEventStage.CALL_END, "done");
    }

    @Override
    public void callFailed(Call call, IOException ioe) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setCallFailed(System.currentTimeMillis());
        info.setErrorMessage(safeDiagnosticText(NetworkErrorMessageResolver.toUserFriendlyMessage(ioe)));
        info.setError(ioe);
        if (info.getDnsStart() > 0 && info.getDnsEnd() <= 0 && info.getRouteAttempts().isEmpty()) {
            info.setDnsError(exceptionMessage(ioe));
        }
        completePendingRouteAttempts(info.getCallFailed(), call != null && call.isCanceled(), exceptionMessage(ioe));
        applyFailedRouteWindowToSummary();
        if (!enableNetworkLog) {
            return;
        }
        String errorMsg = ioe.getMessage() != null ? ioe.getMessage() : ioe.getClass().getSimpleName();
        log(NetworkLogEventStage.CALL_FAILED, errorMsg);
    }

    @Override
    public void canceled(Call call) {
        if (!collectEventInfo) {
            return;
        }
        info.setCanceled(System.currentTimeMillis());
        completePendingRouteAttempts(info.getCanceled(), true, "Call was canceled");
        applyFailedRouteWindowToSummary();
        log(NetworkLogEventStage.CANCELED, "Call was canceled");
    }

    @Override
    public void retryDecision(Call call, IOException ioe, boolean retry) {
        if (!collectEventInfo) {
            return;
        }
        info.recordRetryDecision(retry);
        String error = exceptionMessage(ioe);
        log(NetworkLogEventStage.RETRY_DECISION, "Retry: " + retry + ", reason: " + error);
    }

    @Override
    public void followUpDecision(Call call, Response response, Request nextRequest) {
        if (!collectEventInfo) {
            return;
        }
        boolean followUp = nextRequest != null;
        info.recordFollowUpDecision(followUp);
        // Application redirects are handled outside this call. A negative internal decision
        // adds no useful information and would contradict the following REDIRECT event.
        if (!followUp) {
            return;
        }
        String next = nextRequest.method() + " " + nextRequest.url();
        log(NetworkLogEventStage.FOLLOW_UP_DECISION,
                "Follow-up: " + followUp + ", response: " + response.code() + ", next: " + next);
    }


    @Override
    public void satisfactionFailure(Call call, Response response) {
        if (!collectEventInfo) {
            return;
        }
        info.setErrorMessage("Response does not satisfy request: " + response.code() + " " + response.message());
        log(NetworkLogEventStage.SATISFACTION_FAILURE, "Response does not satisfy request: " + response.code() + " " + response.message());
    }


    @Override
    public void cacheHit(Call call, Response response) {
        if (!enableNetworkLog) {
            return;
        }
        log(NetworkLogEventStage.CACHE_HIT, "Response served from cache: " + response.code() + " " + response.message());
    }

    @Override
    public void cacheMiss(Call call) {
        // An ordinary network response is the default. Cache hit events carry the useful exception.
    }

    @Override
    public void cacheConditionalHit(Call call, Response cachedResponse) {
        if (!enableNetworkLog) {
            return;
        }
        log(NetworkLogEventStage.CACHE_CONDITIONAL_HIT, "Response served from conditional cache: " + cachedResponse.code() + " " + cachedResponse.message());
    }


    private String formatSentHeaders() {
        if (preparedRequest.sentHeadersList == null || preparedRequest.sentHeadersList.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n");
        boolean http2 = "h2".equals(info.getProtocol()) || "h2_prior_knowledge".equals(info.getProtocol());
        sb.append(http2 ? "HTTP/2 header view (regular headers and :authority):" : "Header snapshot:").append("\n");
        if (http2) {
            String authority = null;
            for (HttpHeader header : preparedRequest.sentHeadersList) {
                if (header != null && "Host".equalsIgnoreCase(header.getKey())) {
                    authority = header.getValue();
                }
            }
            // Request.header("Host") selects the last value; HTTP/2 writes one :authority.
            if (authority != null) {
                sb.append(":authority: ").append(authority).append("\n");
            }
        }
        for (HttpHeader header : preparedRequest.sentHeadersList) {
            if (header == null || header.getKey() == null) {
                continue;
            }
            String name = header.getKey();
            if (http2) {
                name = name.toLowerCase(Locale.ROOT);
                if ("host".equals(name) || HTTP2_OMITTED_HEADERS.contains(name)
                        || ("te".equals(name) && !"trailers".equals(header.getValue()))) {
                    continue;
                }
            }
            sb.append(name).append(": ").append(header.getValue()).append("\n");
        }
        return sb.toString();
    }

    private String formatSentRequestBody() {
        if (preparedRequest.sentRequestBody == null) {
            return "Request body preview unavailable";
        }
        if (preparedRequest.sentRequestBody.isEmpty()) {
            return "Request body is empty";
        }
        return "\n" + preparedRequest.sentRequestBody;
    }

    private boolean isEmptyRequestBody(Call call) {
        if (preparedRequest.sentRequestBody != null && preparedRequest.sentRequestBody.isEmpty()) {
            return true;
        }
        try {
            return call != null && call.request().body() != null && call.request().body().contentLength() == 0L;
        } catch (IOException ignored) {
            return false;
        }
    }

    private Long duration(long startMs, long endMs) {
        if (startMs <= 0 || endMs <= 0 || endMs < startMs) {
            return null;
        }
        return endMs - startMs;
    }

    private String exceptionMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : safeDiagnosticText(message);
    }

    private String safeDiagnosticHost(String rawHost) {
        String displayed = SafeSocketAddressFormatter.host(rawHost);
        if (!SafeSocketAddressFormatter.isSafeHost(rawHost)
                && rawHost != null && rawHost.length() >= 4 && rawHost.indexOf('@') > 0) {
            hiddenDiagnosticHosts.add(rawHost);
        }
        return displayed;
    }

    private String safeDiagnosticText(String text) {
        if (text == null || hiddenDiagnosticHosts.isEmpty()) {
            return text;
        }
        String result = text;
        for (String host : hiddenDiagnosticHosts) {
            result = result.replace(host, "<invalid-host>");
        }
        return result;
    }

    private static boolean isRouteCancellationSignal(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (isRouteCancellationSignal(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRouteCancellationSignal(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.trim().toLowerCase(java.util.Locale.ROOT);
        return "canceled".equals(normalized)
                || "cancelled".equals(normalized)
                || "socket closed".equals(normalized)
                || "socket is closed".equals(normalized);
    }

}
