package com.laker.postman.http.runtime.transport;

import com.laker.postman.http.runtime.cookie.HttpCookieStore;
import com.laker.postman.http.runtime.model.HttpCaptureProfiles;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.okhttp.OkHttpResponseHandler;
import com.laker.postman.http.runtime.sse.SseResponseCallback;
import com.laker.postman.util.MonotonicStopwatch;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public final class HttpExchangeExecutor {
    private final HttpClientResolver clientResolver;

    public HttpExchangeExecutor(HttpClientResolver clientResolver) {
        this.clientResolver = clientResolver == null ? HttpClientResolver.DEFAULT : clientResolver;
    }

    public HttpResponse executeHttp(PreparedRequest request, HttpExchangeOptions options) throws Exception {
        HttpExchangeOptions resolvedOptions = options == null ? HttpExchangeOptions.defaults() : options;
        Request okRequest = PreparedOkHttpRequestFactory.build(request);
        return executeRequest(
                request,
                okRequest,
                resolvedOptions.getCallback(),
                resolvedOptions.resolvedCallTracker(),
                resolvedOptions.getBaseClientProvider()
        );
    }

    private HttpResponse executeRequest(PreparedRequest request,
                                        Request okRequest,
                                        SseResponseCallback callback,
                                        HttpCallTracker callTracker,
                                        HttpBaseClientProvider baseClientProvider) throws Exception {
        OkHttpClient client = clientResolver.resolveClient(request, baseClientProvider);
        Call call = client.newCall(okRequest);
        callTracker.onCallStarted(call);
        try {
            return callWithRequest(request, call, client, callback);
        } finally {
            callTracker.onCallFinished(call);
        }
    }

    private HttpResponse callWithRequest(PreparedRequest request,
                                         Call call,
                                         OkHttpClient client,
                                         SseResponseCallback callback) throws IOException {
        MonotonicStopwatch stopwatch = MonotonicStopwatch.start();
        long queueStartMs = stopwatch.startWallTimeMs();
        HttpResponse httpResponse = new HttpResponse();
        ConnectionPool pool = client.connectionPool();
        httpResponse.idleConnectionCount = pool.idleConnectionCount();
        httpResponse.connectionCount = pool.connectionCount();
        Response okResponse;
        try {
            okResponse = call.execute();
        } finally {
            HttpExchangeTraceSupport.attachToResponse(httpResponse, queueStartMs, request);
        }
        OkHttpResponseHandler.handleResponse(
                okResponse,
                httpResponse,
                callback,
                request.responseBodyMode,
                request.responseBodyPreviewLimitBytes,
                request.downloadProgressSinkFactory,
                request.responseSizeLimitWarningSink
        );
        long elapsedMs = stopwatch.elapsedMs();
        httpResponse.costMs = elapsedMs;
        httpResponse.endTime = queueStartMs + elapsedMs;
        if (HttpCaptureProfiles.resolve(request).notifyCookieChanges()) {
            HttpCookieStore.notifyCookieChanged();
        }
        return httpResponse;
    }
}
