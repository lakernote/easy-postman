package com.laker.postman.http.runtime.transport;

import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import lombok.experimental.UtilityClass;
import okhttp3.Connection;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;

/**
 * HTTP 交换链路信息的统一出口。
 * <p>
 * EventListener、同步请求和 SSE/WebSocket 回调可能运行在不同线程，
 * 因此链路信息绑定到 PreparedRequest 的 transient 字段，不依赖 ThreadLocal。
 */
@UtilityClass
public class HttpExchangeTraceSupport {

    public static void attachToResponse(HttpResponse httpResponse, long queueStartMs, PreparedRequest request) {
        HttpEventInfo httpEventInfo = resolveFromRequest(request);
        if (httpEventInfo != null) {
            completeTiming(httpEventInfo, queueStartMs);
        }
        if (httpResponse == null) {
            return;
        }
        // SSE/WebSocket 的回调可能晚于 OkHttp EventListener 或跨线程触发，响应详情至少要展示当前回调线程。
        String fallbackThreadName = Thread.currentThread().getName();
        if (httpEventInfo != null && isBlank(httpEventInfo.getThreadName())) {
            httpEventInfo.setThreadName(fallbackThreadName);
        }
        httpResponse.httpEventInfo = httpEventInfo;
        if (isBlank(httpResponse.threadName)) {
            httpResponse.threadName = httpEventInfo == null ? fallbackThreadName : httpEventInfo.getThreadName();
        }
        if (httpEventInfo != null) {
            if (isBlank(httpResponse.protocol)) {
                httpResponse.protocol = httpEventInfo.getProtocol();
            }
        }
    }

    public static HttpEventInfo resolveFromRequest(PreparedRequest request) {
        return request == null ? null : request.exchangeEventInfo;
    }

    public static void bindToRequest(PreparedRequest request, HttpEventInfo httpEventInfo) {
        if (request != null) {
            request.exchangeEventInfo = httpEventInfo;
        }
    }

    /**
     * network interceptor 是能拿到实际连接的最晚请求侧位置，用它补充本地/远端地址和协议信息。
     */
    public static void updateFromConnection(PreparedRequest request, Connection connection) {
        HttpEventInfo httpEventInfo = resolveFromRequest(request);
        if (httpEventInfo == null || connection == null) {
            return;
        }
        if (connection.protocol() != null) {
            httpEventInfo.setProtocol(connection.protocol().toString());
        }
        try {
            Socket socket = connection.socket();
            if (socket != null) {
                httpEventInfo.setLocalAddress(formatSocketEndpoint(socket.getLocalAddress(), socket.getLocalPort()));
                httpEventInfo.setRemoteAddress(formatSocketEndpoint(socket.getInetAddress(), socket.getPort()));
            }
        } catch (Exception ignored) {
            // mock 连接或已释放连接可能无法暴露 socket 细节。
        }
    }

    static long resolveResponseReceivedEndTime(HttpResponse httpResponse, long fallbackEndTime) {
        if (httpResponse == null || httpResponse.httpEventInfo == null) {
            return fallbackEndTime;
        }
        long responseEnd = Math.max(
                httpResponse.httpEventInfo.getResponseBodyEnd(),
                Math.max(
                        Math.max(httpResponse.httpEventInfo.getCallEnd(), httpResponse.httpEventInfo.getCallFailed()),
                        Math.max(httpResponse.httpEventInfo.getCanceled(), httpResponse.httpEventInfo.getResponseHeadersEnd())
                )
        );
        return responseEnd > 0 ? Math.max(httpResponse.httpEventInfo.getQueueStart(), responseEnd) : fallbackEndTime;
    }

    private static void completeTiming(HttpEventInfo httpEventInfo, long queueStartMs) {
        httpEventInfo.setQueueStart(queueStartMs);
        if (httpEventInfo.getCallStart() > 0) {
            httpEventInfo.setQueueingCost(httpEventInfo.getCallStart() - queueStartMs);
        }
        long stalledEnd = firstPhaseStartAfterCall(httpEventInfo);
        if (stalledEnd > 0 && httpEventInfo.getCallStart() > 0) {
            httpEventInfo.setStalledCost(stalledEnd - httpEventInfo.getCallStart());
        }
    }

    private static long firstPhaseStartAfterCall(HttpEventInfo httpEventInfo) {
        long callStart = httpEventInfo.getCallStart();
        if (callStart <= 0) {
            return -1L;
        }
        long earliest = Long.MAX_VALUE;
        earliest = minPositiveAtOrAfter(earliest, callStart, httpEventInfo.getDispatcherQueueStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, httpEventInfo.getProxySelectStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, httpEventInfo.getDnsStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, httpEventInfo.getConnectStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, httpEventInfo.getConnectionAcquired());
        earliest = minPositiveAtOrAfter(earliest, callStart, httpEventInfo.getRequestHeadersStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, httpEventInfo.getRequestBodyStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, httpEventInfo.getResponseHeadersStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, httpEventInfo.getResponseBodyStart());
        return earliest == Long.MAX_VALUE ? -1L : earliest;
    }

    private static long minPositiveAtOrAfter(long current, long lowerBound, long value) {
        if (value <= 0 || value < lowerBound) {
            return current;
        }
        return Math.min(current, value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String formatSocketEndpoint(InetAddress address, int port) {
        String host = address.getHostAddress();
        if (address instanceof Inet6Address) {
            host = "[" + host + "]";
        }
        return host + ":" + port;
    }
}
