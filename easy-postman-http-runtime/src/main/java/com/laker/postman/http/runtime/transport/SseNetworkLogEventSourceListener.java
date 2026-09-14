package com.laker.postman.http.runtime.transport;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.observation.NetworkLogSupport;
import com.laker.postman.http.runtime.okhttp.OkHttpRequestSnapshotCapture;
import com.laker.postman.request.model.HttpHeader;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

import java.util.function.BooleanSupplier;

/**
 * SSE 网络日志装饰器：只记录握手响应和生命周期，不改变业务回调语义。
 */
final class SseNetworkLogEventSourceListener extends EventSourceListener {
    private final EventSourceListener delegate;
    private final PreparedRequest preparedRequest;
    private final BooleanSupplier cancellationRequested;
    private boolean requestSnapshotLogged;
    private boolean responseSnapshotLogged;
    private boolean terminalLogged;

    SseNetworkLogEventSourceListener(EventSourceListener delegate,
                                     PreparedRequest preparedRequest,
                                     BooleanSupplier cancellationRequested) {
        this.delegate = delegate == null ? new EventSourceListener() {
        } : delegate;
        this.preparedRequest = preparedRequest;
        this.cancellationRequested = cancellationRequested == null ? () -> false : cancellationRequested;
    }

    @Override
    public void onOpen(EventSource eventSource, Response response) {
        logResponseSnapshot(response);
        log(NetworkLogEventStage.RESPONSE_BODY_START, "SSE stream opened");
        delegate.onOpen(eventSource, response);
    }

    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        delegate.onEvent(eventSource, id, type, data);
    }

    @Override
    public void onClosed(EventSource eventSource) {
        logStreamClosed();
        delegate.onClosed(eventSource);
    }

    @Override
    public void onFailure(EventSource eventSource, Throwable t, Response response) {
        logResponseSnapshot(response);
        if (!terminalLogged) {
            terminalLogged = true;
            if (cancellationRequested.getAsBoolean()) {
                log(NetworkLogEventStage.CANCELED, terminalMessage(t, "SSE stream canceled"));
            } else {
                log(NetworkLogEventStage.CALL_FAILED, terminalMessage(t, "SSE stream failed"));
            }
        }
        delegate.onFailure(eventSource, t, response);
    }

    private void logResponseSnapshot(Response response) {
        if (response == null || responseSnapshotLogged) {
            return;
        }
        responseSnapshotLogged = true;
        logRequestSnapshot(response);
        log(NetworkLogEventStage.RESPONSE_HEADERS_END,
                RealtimeHandshakeNetworkLogFormatter.formatResponseSnapshot(preparedRequest, response));
    }

    private void logRequestSnapshot(Response response) {
        if (requestSnapshotLogged || response == null || response.request() == null) {
            return;
        }
        requestSnapshotLogged = true;
        if (preparedRequest == null || preparedRequest.sentHeadersList == null || preparedRequest.sentHeadersList.isEmpty()) {
            OkHttpRequestSnapshotCapture.capture(preparedRequest, response.request(), false);
        }
        log(NetworkLogEventStage.REQUEST_HEADERS_END, formatRequestHeaders());
        log(NetworkLogEventStage.REQUEST_BODY_START, formatRequestBody());
    }

    private String formatRequestHeaders() {
        if (preparedRequest == null || preparedRequest.sentHeadersList == null || preparedRequest.sentHeadersList.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\nSSE Stream Request Headers:\n");
        for (HttpHeader header : preparedRequest.sentHeadersList) {
            if (header == null || header.getKey() == null) {
                continue;
            }
            sb.append(header.getKey()).append(": ").append(header.getValue()).append("\n");
        }
        return sb.toString();
    }

    private String formatRequestBody() {
        if (preparedRequest == null || preparedRequest.sentRequestBody == null) {
            return "No request body";
        }
        if (preparedRequest.sentRequestBody.isEmpty()) {
            return "Request body is empty";
        }
        return "\n" + preparedRequest.sentRequestBody;
    }

    private void logStreamClosed() {
        if (terminalLogged) {
            return;
        }
        terminalLogged = true;
        log(NetworkLogEventStage.CALL_END, "SSE stream closed");
    }

    private String terminalMessage(Throwable throwable, String fallback) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return fallback;
        }
        return throwable.getMessage();
    }

    private void log(NetworkLogEventStage stage, String message) {
        NetworkLogSupport.append(preparedRequest, stage, message);
    }
}
