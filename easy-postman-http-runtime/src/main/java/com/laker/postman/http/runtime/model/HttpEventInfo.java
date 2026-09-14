package com.laker.postman.http.runtime.model;

import lombok.Data;

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 采集 HTTP 全流程事件信息
 */
@Data
public class HttpEventInfo {
    // 连接信息
    private String localAddress;
    private String remoteAddress;
    /** All concrete IPv4/IPv6 routes attempted by OkHttp during connection setup. */
    private final List<HttpRouteAttempt> routeAttempts = new CopyOnWriteArrayList<>();
    // 各阶段时间戳
    private long queueStart; // newCall前的时间戳 自己额外定义的发起请求时间
    private long callStart;
    private long dispatcherQueueStart;
    private long dispatcherQueueEnd;
    private long proxySelectStart;
    private long proxySelectEnd;
    private long dnsStart;
    private long dnsEnd;
    private String dnsHost;
    private final List<String> dnsAddresses = new CopyOnWriteArrayList<>();
    private String dnsError;
    private long connectStart;
    private long secureConnectStart;
    private long secureConnectEnd;
    private long connectEnd;
    private long connectionAcquired;
    private long requestHeadersStart;
    private long requestHeadersEnd;
    private long requestBodyStart;
    private long requestBodyEnd;
    private long responseHeadersStart;
    private long responseHeadersEnd;
    private long responseBodyStart;
    private long responseBodyEnd;
    private long connectionReleased;
    private long callEnd;
    private long callFailed;
    private long canceled;

    // 耗时统计
    private long queueingCost; // 排队耗时
    private long stalledCost; // 阻塞耗时
    private int retryDecisionCount;
    private int retryCount;
    private int followUpDecisionCount;
    private int followUpCount;
    // 协议
    private String protocol;
    // TLS/证书
    private List<Certificate> peerCertificates = new ArrayList<>();
    private List<Certificate> localCertificates = new ArrayList<>();
    private String tlsVersion;
    // 加密套件
    private String cipherName;
    // SSL 证书验证警告信息（如过期、域名不匹配、自签名等）
    private String sslCertWarning;
    // 异常
    private String errorMessage;
    private Throwable error;
    // 其他
    private String threadName;

    private long bodyBytesSent;
    private long bodyBytesReceived;
    private long headerBytesSent;
    private long headerBytesReceived;

    public void addRouteAttempt(HttpRouteAttempt routeAttempt) {
        if (routeAttempt != null) {
            routeAttempts.add(routeAttempt);
        }
    }

    public void replaceRouteAttempt(int index, HttpRouteAttempt routeAttempt) {
        if (routeAttempt != null && index >= 0 && index < routeAttempts.size()) {
            routeAttempts.set(index, routeAttempt);
        }
    }

    public void replaceDnsAddresses(List<String> addresses) {
        dnsAddresses.clear();
        if (addresses != null) {
            dnsAddresses.addAll(addresses);
        }
    }

    public synchronized void recordRetryDecision(boolean retry) {
        retryDecisionCount++;
        if (retry) {
            retryCount++;
        }
    }

    public synchronized void recordFollowUpDecision(boolean followUp) {
        followUpDecisionCount++;
        if (followUp) {
            followUpCount++;
        }
    }
}
