package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.model.HttpCapturePolicy;
import com.laker.postman.http.runtime.model.HttpCaptureProfiles;
import com.laker.postman.http.runtime.model.HttpExchangeKind;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpRouteAttempt;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.observation.NetworkLogSupport;
import com.laker.postman.http.runtime.error.NetworkErrorMessageResolver;
import com.laker.postman.http.runtime.ssl.CertificateCapturingSSLSocketFactory;
import com.laker.postman.http.runtime.ssl.SSLCertificateValidator;
import com.laker.postman.http.runtime.ssl.SSLConfigurationUtil;
import com.laker.postman.http.runtime.ssl.SSLValidationResult;
import com.laker.postman.http.runtime.transport.HttpExchangeTraceSupport;
import com.laker.postman.request.model.HttpHeader;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 事件监听器，既记录详细连接事件和耗时，也统计连接信息
 */
@Slf4j
public class OkHttpExchangeEventListener extends EventListener {
    private final long callStartNanos;
    private final HttpEventInfo info;
    private final PreparedRequest preparedRequest;
    private final HttpExchangeKind exchangeKind;

    // 精细化控制开关
    private final boolean collectMetricsInfo; // 是否收集轻量统计指标（时间戳、发送/接收字节）
    private final boolean collectEventInfo; // 是否收集完整事件信息（DNS、连接等）
    private final boolean enableNetworkLog; // 是否启用网络日志面板输出
    private final Object routeAttemptLock = new Object();
    private final List<PendingRouteAttempt> pendingRouteAttempts = new ArrayList<>();
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
    }

    /**
     * 发布网络日志事件（仅在 enableNetworkLog=true 时使用）。
     */
    private void log(NetworkLogEventStage stage, String msg) {
        log(stage, msg, null);
    }

    private void log(NetworkLogEventStage stage, String msg, Long durationMs) {
        // 只有启用了网络日志才向外发布事件，具体展示由调用方注入的 sink 负责。
        if (!enableNetworkLog || shouldDelegateRealtimeStage(stage)) {
            return;
        }

        long now = System.nanoTime();
        long elapsedMs = (now - callStartNanos) / 1_000_000;
        NetworkLogSupport.append(preparedRequest, stage, msg, elapsedMs, durationMs);
    }

    @Override
    public void callStart(Call call) {
        if (!collectMetricsInfo) {
            return;
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
        StringBuilder sb = new StringBuilder();
        sb.append("Proxies: ");
        for (Proxy proxy : proxies) {
            sb.append(proxy.type()).append(" ");
            if (proxy.address() instanceof InetSocketAddress address) {
                // getHostName() may trigger an unrelated reverse-DNS lookup while tracing.
                sb.append(address.getHostString()).append(":").append(address.getPort()).append(" ");
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
        info.setDnsHost(domainName);
        info.setDnsError(null);
        info.replaceDnsAddresses(List.of());
        log(NetworkLogEventStage.DNS_START, domainName);
    }

    @Override
    public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
        if (!collectEventInfo) {
            return;
        }
        info.setDnsEnd(System.currentTimeMillis());
        info.replaceDnsAddresses(inetAddressList.stream()
                .map(OkHttpExchangeEventListener::concreteAddress)
                .toList());
        log(NetworkLogEventStage.DNS_END, domainName + " -> " + inetAddressList,
                duration(info.getDnsStart(), info.getDnsEnd()));
    }

    @Override
    public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
        if (!collectEventInfo) {
            return;
        }
        long startTime = System.currentTimeMillis();
        synchronized (routeAttemptLock) {
            pendingRouteAttempts.add(new PendingRouteAttempt(
                    routeAddress(inetSocketAddress),
                    addressFamily(inetSocketAddress),
                    startTime,
                    System.nanoTime()
            ));
        }
        log(NetworkLogEventStage.CONNECT_START, inetSocketAddress + " via " + proxy.type());
    }

    @Override
    public void secureConnectStart(Call call) {
        if (!collectEventInfo) {
            return;
        }
        info.setSecureConnectStart(System.currentTimeMillis());
        log(NetworkLogEventStage.SECURE_CONNECT_START, "TLS handshake start");
    }

    @Override
    public void secureConnectEnd(Call call, Handshake handshake) {
        if (!collectEventInfo) {
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

                    // 清除线程本地存储的SSL错误
                    SSLConfigurationUtil.clearValidationResult();
                } catch (Exception e) {
                    log.debug("Error validating certificate: {}", e.getMessage());
                }
            }
            // 记录handshake信息
            if (handshake != null) {
                StringBuilder handshakeInfo = new StringBuilder();
                handshakeInfo.append("SSL connection using ")
                        .append(handshake.tlsVersion())
                        .append(" / ")
                        .append(handshake.cipherSuite())
                        .append("\n");
                List<Certificate> peerCertificates = info.getPeerCertificates();
                if (peerCertificates != null && !peerCertificates.isEmpty()) {
                    Certificate cert = peerCertificates.get(0);
                    if (cert instanceof X509Certificate x509) {
                        handshakeInfo.append("Server certificate:\n");
                        handshakeInfo.append(" subject: ").append(x509.getSubjectDN()).append("\n");
                        handshakeInfo.append(" start date: ").append(x509.getNotBefore()).append(" GMT\n");
                        handshakeInfo.append(" expire date: ").append(x509.getNotAfter()).append(" GMT\n");
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
                        handshakeInfo.append(" issuer: ").append(x509.getIssuerDN()).append("\n");
                    }
                }

                // 如果有证书警告，添加到日志中
                if (info.getSslCertWarning() != null && !info.getSslCertWarning().isEmpty()) {
                    handshakeInfo.append("⚠️  Certificate Warning: ").append(info.getSslCertWarning()).append("\n");
                } else {
                    handshakeInfo.append("SSL certificate verify ok.\n");
                }

                log(NetworkLogEventStage.SECURE_CONNECT_END, handshakeInfo.toString(),
                        duration(info.getSecureConnectStart(), info.getSecureConnectEnd()));
            } else {
                log(NetworkLogEventStage.SECURE_CONNECT_END, "no handshake",
                        duration(info.getSecureConnectStart(), info.getSecureConnectEnd()));
            }
        } finally {
            CertificateCapturingSSLSocketFactory.clearLastCapturedCertificates();
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
        log(NetworkLogEventStage.CONNECT_END, inetSocketAddress + " via " + proxy.type() + ", protocol=" + protocol,
                attempt == null ? null : attempt.durationMs());
    }

    @Override
    public void connectFailed(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol, IOException ioe) {
        if (!collectEventInfo) {
            return;
        }
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
        log(NetworkLogEventStage.CONNECT_FAILED, inetSocketAddress + " via " + proxy.type() + ", protocol=" + protocol + ", error: " + exceptionMessage(ioe),
                attempt == null ? null : attempt.durationMs());
    }

    private HttpRouteAttempt completeRouteAttemptLocked(InetSocketAddress address,
                                                        long endTime,
                                                        boolean connected,
                                                        boolean canceled,
                                                        String protocol,
                                                        String error) {
        String routeAddress = routeAddress(address);
        HttpRouteAttempt completedAttempt = null;
        for (int i = pendingRouteAttempts.size() - 1; i >= 0; i--) {
            PendingRouteAttempt pending = pendingRouteAttempts.get(i);
            if (!pending.address.equals(routeAddress)) {
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
            return address.getHostString() + ":" + address.getPort();
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
        private final String address;
        private final String addressFamily;
        private final long startTime;
        private final long startNanos;

        private PendingRouteAttempt(String address, String addressFamily, long startTime, long startNanos) {
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
        log(NetworkLogEventStage.CONNECTION_ACQUIRED, label + ": " + connection.toString() + ", local=" + info.getLocalAddress() + ", remote=" + info.getRemoteAddress());
    }

    @Override
    public void connectionReleased(Call call, Connection connection) {
        if (!collectEventInfo) {
            return;
        }
        info.setConnectionReleased(System.currentTimeMillis());
        log(NetworkLogEventStage.CONNECTION_RELEASED, "Connection released: " + connection.toString() + ", local=" + info.getLocalAddress() + ", remote=" + info.getRemoteAddress(),
                duration(info.getConnectionAcquired(), info.getConnectionReleased()));
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
            if (enableNetworkLog) {
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
        log(NetworkLogEventStage.REQUEST_BODY_END, "bytes=" + byteCount,
                duration(info.getRequestBodyStart(), info.getRequestBodyEnd()));
    }

    @Override
    public void requestFailed(Call call, IOException ioe) {
        if (!collectMetricsInfo) {
            return;
        }
        info.setErrorMessage(NetworkErrorMessageResolver.toUserFriendlyMessage(ioe));
        info.setError(ioe);
        if (!enableNetworkLog) {
            return;
        }
        log(NetworkLogEventStage.REQUEST_FAILED, ioe.getMessage() + "\n" + getStackTrace(ioe));
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
        sb.append("Redirect: ").append(isRedirect).append("\n");
        sb.append("Response Code: ").append(response.code()).append(" ").append(response.message()).append("\n");
        sb.append("Protocol: ").append(response.protocol()).append("\n");
        sb.append("Content-Type: ").append(response.header("Content-Type", "")).append("\n");
        sb.append("Content-Length: ").append(response.header("Content-Length", "")).append("\n");
        if (isRedirect) {
            sb.append("Location: ").append(response.header("Location", "")).append("\n");
        }
        if (response.cacheResponse() != null) {
            sb.append("Cache: HIT\n");
        } else {
            sb.append("Cache: MISS\n");
        }
        if (response.networkResponse() != null) { // 如果有 networkResponse，说明是网络请求
            sb.append("Network: YES\n");
        } else {
            sb.append("Network: NO\n");
        }
        if (response.priorResponse() != null) { // 如果有 priorResponse，说明是重定向或缓存的响应
            sb.append("PriorResponse: YES\n");
        }
        sb.append("\n");
        sb.append("Headers:\n");
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
        info.setErrorMessage(NetworkErrorMessageResolver.toUserFriendlyMessage(ioe));
        info.setError(ioe);
        if (!enableNetworkLog) {
            return;
        }
        String errorMsg = ioe.getMessage() != null ? ioe.getMessage() : ioe.getClass().getSimpleName();
        log(NetworkLogEventStage.RESPONSE_FAILED, errorMsg + "\n" + getStackTrace(ioe));
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
        info.setErrorMessage(NetworkErrorMessageResolver.toUserFriendlyMessage(ioe));
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
        String next = followUp ? nextRequest.method() + " " + nextRequest.url() : "none";
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
        if (!enableNetworkLog) {
            return;
        }
        log(NetworkLogEventStage.CACHE_MISS, "No cache hit for this call");
    }

    @Override
    public void cacheConditionalHit(Call call, Response cachedResponse) {
        if (!enableNetworkLog) {
            return;
        }
        log(NetworkLogEventStage.CACHE_CONDITIONAL_HIT, "Response served from conditional cache: " + cachedResponse.code() + " " + cachedResponse.message());
    }


    // 辅助方法：获取异常堆栈
    private String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) {
            sb.append("    at ").append(e.toString()).append("\n");
        }
        return sb.toString();
    }

    private String formatSentHeaders() {
        if (preparedRequest.sentHeadersList == null || preparedRequest.sentHeadersList.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n");
        for (HttpHeader header : preparedRequest.sentHeadersList) {
            if (header == null || header.getKey() == null) {
                continue;
            }
            sb.append(header.getKey()).append(": ").append(header.getValue()).append("\n");
        }
        return sb.toString();
    }

    private String formatSentRequestBody() {
        if (preparedRequest.sentRequestBody == null) {
            return "No request body";
        }
        if (preparedRequest.sentRequestBody.isEmpty()) {
            return "Request body is empty";
        }
        return "\n" + preparedRequest.sentRequestBody;
    }

    private Long duration(long startMs, long endMs) {
        if (startMs <= 0 || endMs <= 0 || endMs < startMs) {
            return null;
        }
        return endMs - startMs;
    }

    private static String exceptionMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
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
