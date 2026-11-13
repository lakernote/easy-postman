package com.laker.postman.performance.core.model;

import lombok.experimental.UtilityClass;

@UtilityClass
final class PerformanceWindowMetricsFactory {

    PerformanceTrendSnapshot.ProtocolWindowMetrics fromSnapshot(
            PerformanceSampleMeterSnapshot stats,
            PerformanceProtocol protocol,
            long stepIntervalMs,
            PerformanceRealtimeMetrics.Sample realtimeMetrics) {
        if (stats == null || stats.total() == 0) {
            return realtimeStreamMetrics(protocol, realtimeMetrics);
        }

        double sampleRate = PerformanceMetricMath.rate(stats.total(), stepIntervalMs);
        double sentRate = stepRate(stats.sentMessages(), stepIntervalMs);
        double receivedRate = stepRate(stats.receivedMessages(), stepIntervalMs);
        double matchedRate = stepRate(stats.matchedMessages(), stepIntervalMs);
        double avgDuration = stats.avgDurationMs();
        double avgFirstMessageLatency = stats.avgFirstMessageLatencyMs();

        if (realtimeMetrics != null && protocol == PerformanceProtocol.WEBSOCKET) {
            sentRate = realtimeMetrics.webSocketSentRate();
            receivedRate = realtimeMetrics.webSocketReceivedRate();
            matchedRate = realtimeMetrics.webSocketMatchedRate();
            avgFirstMessageLatency = realtimeMetrics.webSocketFirstMessageLatencyMs();
            if (realtimeMetrics.webSocketActiveSessionDurationMs() > 0) {
                avgDuration = realtimeMetrics.webSocketActiveSessionDurationMs();
            }
        } else if (realtimeMetrics != null && protocol == PerformanceProtocol.SSE) {
            receivedRate = realtimeMetrics.sseReceivedRate();
            matchedRate = realtimeMetrics.sseMatchedRate();
            avgFirstMessageLatency = realtimeMetrics.sseFirstMessageLatencyMs();
            if (realtimeMetrics.sseActiveSessionDurationMs() > 0) {
                avgDuration = realtimeMetrics.sseActiveSessionDurationMs();
            }
        } else if (protocol == PerformanceProtocol.WEBSOCKET || protocol == PerformanceProtocol.SSE) {
            // 没有实时计数器时，流式消息总量来自会话结束样本，按会话跨度分摊，避免结束瞬间尖峰。
            double sessionSeconds = stats.sampleSpanSeconds();
            sentRate = PerformanceMetricMath.rate(stats.sentMessages(), sessionSeconds);
            receivedRate = PerformanceMetricMath.rate(stats.receivedMessages(), sessionSeconds);
            matchedRate = PerformanceMetricMath.rate(stats.matchedMessages(), sessionSeconds);
        }

        return new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                PerformanceMetricMath.clampToInt(stats.total()),
                PerformanceMetricMath.clampToInt(stats.fail()),
                stats.total() > 0 ? PerformanceMetricMath.round((double) stats.fail() * 100 / stats.total()) : 0,
                sampleRate,
                avgDuration,
                PerformanceMetricMath.clampToInt(stats.sentMessages()),
                PerformanceMetricMath.clampToInt(stats.receivedMessages()),
                PerformanceMetricMath.clampToInt(stats.matchedMessages()),
                sentRate,
                receivedRate,
                matchedRate,
                avgFirstMessageLatency,
                maxDuration(stats, protocol)
        );
    }

    // 流式协议的耗时是会话时长，最大值没有诊断意义。
    private double maxDuration(PerformanceSampleMeterSnapshot stats, PerformanceProtocol protocol) {
        if (protocol == PerformanceProtocol.WEBSOCKET || protocol == PerformanceProtocol.SSE) {
            return Double.NaN;
        }
        return stats.durationStats().max();
    }

    private double stepRate(long count, long stepIntervalMs) {
        return PerformanceMetricMath.rate(count, stepIntervalMs);
    }

    private PerformanceTrendSnapshot.ProtocolWindowMetrics realtimeStreamMetrics(
            PerformanceProtocol protocol,
            PerformanceRealtimeMetrics.Sample realtimeMetrics) {
        if (realtimeMetrics != null && protocol == PerformanceProtocol.WEBSOCKET) {
            return new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                    0,
                    0,
                    Double.NaN,
                    0,
                    realtimeMetrics.webSocketActiveSessionDurationMs(),
                    0,
                    0,
                    0,
                    realtimeMetrics.webSocketSentRate(),
                    realtimeMetrics.webSocketReceivedRate(),
                    realtimeMetrics.webSocketMatchedRate(),
                    realtimeMetrics.webSocketFirstMessageLatencyMs()
            );
        }
        if (realtimeMetrics != null && protocol == PerformanceProtocol.SSE) {
            return new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                    0,
                    0,
                    Double.NaN,
                    0,
                    realtimeMetrics.sseActiveSessionDurationMs(),
                    0,
                    0,
                    0,
                    0,
                    realtimeMetrics.sseReceivedRate(),
                    realtimeMetrics.sseMatchedRate(),
                    realtimeMetrics.sseFirstMessageLatencyMs()
            );
        }
        return new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                0,
                0,
                Double.NaN,
                0,
                Double.NaN,
                0,
                0,
                0,
                0,
                0,
                0,
                Double.NaN
        );
    }
}
