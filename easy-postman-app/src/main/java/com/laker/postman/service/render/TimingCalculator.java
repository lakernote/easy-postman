package com.laker.postman.service.render;

import com.laker.postman.http.runtime.model.HttpEventInfo;

// 时序计算辅助类
public class TimingCalculator {
    private final HttpEventInfo info;

    public TimingCalculator(HttpEventInfo info) {
        this.info = info;
    }

    public long getTotal() {
        return calculateDuration(info.getCallStart(), terminalTime());
    }

    public long getQueueing() {
        long dispatcherQueueing = calculateDuration(
                info.getDispatcherQueueStart(),
                info.getDispatcherQueueEnd()
        );
        if (dispatcherQueueing >= 0) {
            return dispatcherQueueing;
        }
        return info.getQueueingCost() > 0 ? info.getQueueingCost() :
                calculateDuration(info.getQueueStart(), info.getCallStart());
    }

    public long getStalled() {
        long calculated = calculateDuration(info.getCallStart(), firstPhaseStartAfterCall());
        return calculated >= 0 ? calculated : (info.getStalledCost() > 0 ? info.getStalledCost() : -1);
    }

    public long getDns() {
        return calculateDuration(info.getDnsStart(), info.getDnsEnd());
    }

    public long getConnect() {
        long connectEnd = info.getConnectEnd();
        if (info.getSecureConnectStart() > info.getConnectStart()
                && info.getSecureConnectEnd() > info.getSecureConnectStart()
                && info.getSecureConnectEnd() <= info.getConnectEnd()) {
            connectEnd = info.getSecureConnectStart();
        }
        return calculateDuration(info.getConnectStart(), connectEnd);
    }

    public long getTls() {
        return calculateDuration(info.getSecureConnectStart(), info.getSecureConnectEnd());
    }

    public long getRequestSent() {
        long reqHeaders = calculateDuration(info.getRequestHeadersStart(), info.getRequestHeadersEnd());
        long reqBody = calculateDuration(info.getRequestBodyStart(), info.getRequestBodyEnd());

        if (reqHeaders >= 0 && reqBody >= 0) {
            return reqHeaders + reqBody;
        } else if (reqHeaders >= 0) {
            return reqHeaders;
        } else if (reqBody >= 0) {
            return reqBody;
        }
        return -1;
    }

    public long getServerCost() {
        if (info.getResponseHeadersStart() <= 0) {
            return -1;
        }

        // 优先使用 RequestBodyEnd，如果没有则使用 RequestHeadersEnd
        long requestEndTime = info.getRequestBodyEnd() > 0 ?
                info.getRequestBodyEnd() : info.getRequestHeadersEnd();

        if (requestEndTime <= 0) {
            return -1;
        }

        return calculateDuration(requestEndTime, info.getResponseHeadersStart());
    }

    public long getResponseBody() {
        return calculateDuration(info.getResponseBodyStart(), info.getResponseBodyEnd());
    }

    public boolean getConnectionReused() {
        // 如果没有连接获取事件，无法判断
        if (info.getConnectionAcquired() <= 0) {
            return false;
        }

        // 如果连接获取时间早于连接开始时间，说明是复用的连接
        return info.getConnectStart() <= 0 || info.getConnectionAcquired() < info.getConnectStart();
    }

    private long terminalTime() {
        return Math.max(info.getCallEnd(), Math.max(info.getCallFailed(), info.getCanceled()));
    }

    private long calculateDuration(long start, long end) {
        if (start <= 0 || end <= 0 || end < start) {
            return -1;
        }

        long duration = end - start;

        // 防止异常大的时间差（超过1小时认为异常）
        if (duration > 3600000) { // 3600000ms = 1小时
            return -1;
        }

        return duration;
    }

    private long firstPhaseStartAfterCall() {
        long callStart = info.getCallStart();
        if (callStart <= 0) {
            return -1;
        }
        long earliest = Long.MAX_VALUE;
        earliest = minPositiveAtOrAfter(earliest, callStart, info.getDispatcherQueueStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, info.getProxySelectStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, info.getDnsStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, info.getConnectStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, info.getConnectionAcquired());
        earliest = minPositiveAtOrAfter(earliest, callStart, info.getRequestHeadersStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, info.getRequestBodyStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, info.getResponseHeadersStart());
        earliest = minPositiveAtOrAfter(earliest, callStart, info.getResponseBodyStart());
        return earliest == Long.MAX_VALUE ? -1 : earliest;
    }

    private long minPositiveAtOrAfter(long current, long lowerBound, long value) {
        if (value <= 0 || value < lowerBound) {
            return current;
        }
        return Math.min(current, value);
    }
}
