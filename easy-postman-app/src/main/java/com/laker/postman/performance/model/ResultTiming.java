package com.laker.postman.performance.model;

import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpResponse;

/**
 * 结果表的 JMeter 风格计时：开始时间、首字节（Latency）和连接耗时（Connect）。
 * <p>
 * 首字节和连接都从请求发起（排队开始）计算，与持续时间同一基准；
 * 连接包含排队、DNS、TCP 与 TLS，复用连接时接近 0。未知值用 -1 表示。
 */
public record ResultTiming(long startTimeMs, long latencyMs, long connectMs) {

    public static final long UNKNOWN = -1L;
    public static final ResultTiming EMPTY = new ResultTiming(UNKNOWN, UNKNOWN, UNKNOWN);

    public static ResultTiming of(long startTimeMs, long latencyMs, long connectMs) {
        return new ResultTiming(positiveOrUnknown(startTimeMs), nonNegativeOrUnknown(latencyMs),
                nonNegativeOrUnknown(connectMs));
    }

    public static ResultTiming fromResponse(HttpResponse response, long fallbackStartTimeMs) {
        if (response == null) {
            return of(fallbackStartTimeMs, UNKNOWN, UNKNOWN);
        }
        HttpEventInfo info = response.httpEventInfo;
        long base = info == null ? 0L : info.getQueueStart() > 0 ? info.getQueueStart() : info.getCallStart();
        long startTimeMs = base > 0 ? base : fallbackStartTimeMs;
        if (startTimeMs <= 0 && response.endTime > 0 && response.costMs >= 0) {
            startTimeMs = response.endTime - response.costMs;
        }
        if (base <= 0) {
            return of(startTimeMs, UNKNOWN, UNKNOWN);
        }
        long latencyMs = elapsed(base, info.getResponseHeadersStart());
        long connectMs = elapsed(base, info.getRequestHeadersStart());
        // 耗时用单调时钟、事件用墙钟，取整可能差 1ms；保持 JMeter 语义：连接 ≤ 首字节 ≤ 耗时。
        if (latencyMs >= 0 && response.costMs > 0) {
            latencyMs = Math.min(latencyMs, response.costMs);
        }
        if (connectMs >= 0 && latencyMs >= 0) {
            connectMs = Math.min(connectMs, latencyMs);
        }
        return of(startTimeMs, latencyMs, connectMs);
    }

    private static long elapsed(long base, long timestamp) {
        return timestamp > 0 && timestamp >= base ? timestamp - base : UNKNOWN;
    }

    private static long positiveOrUnknown(long value) {
        return value > 0 ? value : UNKNOWN;
    }

    private static long nonNegativeOrUnknown(long value) {
        return value >= 0 ? value : UNKNOWN;
    }
}
