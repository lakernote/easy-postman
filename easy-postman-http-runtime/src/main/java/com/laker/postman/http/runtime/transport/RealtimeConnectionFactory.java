package com.laker.postman.http.runtime.transport;

import com.laker.postman.http.runtime.cookie.HttpCookieStore;
import com.laker.postman.http.runtime.model.HttpCaptureProfiles;
import com.laker.postman.http.runtime.model.HttpExchangeKind;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.HttpLifecycleLogSink;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.observation.NetworkLogSupport;
import com.laker.postman.http.runtime.okhttp.WebSocketLifecycleLogListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RealtimeConnectionFactory {
    private final HttpClientResolver clientResolver;

    public RealtimeConnectionFactory(HttpClientResolver clientResolver) {
        this.clientResolver = clientResolver == null ? HttpClientResolver.DEFAULT : clientResolver;
    }

    public RealtimeConnectionHandle openSseConnection(PreparedRequest request,
                                                      EventSourceListener listener,
                                                      RealtimeConnectionOptions options) {
        RealtimeConnectionOptions resolvedOptions = options == null ? RealtimeConnectionOptions.defaults() : options;
        AtomicBoolean cancellationRequested = new AtomicBoolean();
        EventSource eventSource = openSse(
                request,
                listener,
                resolvedOptions.getBaseClientProvider(),
                cancellationRequested
        );
        return OkHttpRealtimeConnectionHandles.sse(eventSource, cancellationRequested);
    }

    private EventSource openSse(PreparedRequest request,
                                EventSourceListener listener,
                                HttpBaseClientProvider baseClientProvider,
                                AtomicBoolean cancellationRequested) {
        OkHttpClient customClient = clientResolver.resolveClient(
                request, baseClientProvider, HttpExchangeKind.ASYNC_SSE);
        Request okRequest = PreparedOkHttpRequestFactory.build(request);
        EventSourceListener wrappedListener = wrapSseCookieChangeListener(listener, request);
        if (NetworkLogSupport.isEnabled(request)) {
            wrappedListener = new SseNetworkLogEventSourceListener(
                    wrappedListener, request, cancellationRequested::get);
        }
        return EventSources.createFactory(customClient)
                .newEventSource(okRequest, wrappedListener);
    }

    public RealtimeWebSocketConnection openWebSocketConnection(PreparedRequest request,
                                                              WebSocketListener listener,
                                                              RealtimeConnectionOptions options) {
        RealtimeConnectionOptions resolvedOptions = options == null ? RealtimeConnectionOptions.defaults() : options;
        return OkHttpRealtimeConnectionHandles.webSocket(openWebSocket(
                request,
                listener,
                resolvedOptions.getBaseClientProvider(),
                resolvedOptions.isLifecycleLoggingEnabled()
        ));
    }

    private WebSocket openWebSocket(PreparedRequest request,
                                    WebSocketListener listener,
                                    HttpBaseClientProvider baseClientProvider,
                                    boolean lifecycleLoggingEnabled) {
        OkHttpClient customClient = clientResolver.resolveClient(
                request, baseClientProvider, HttpExchangeKind.WEBSOCKET);
        Request okRequest = PreparedOkHttpRequestFactory.build(request);
        logWebSocketCallStart(request, okRequest);
        WebSocketListener snapshotListener = NetworkLogSupport.isEnabled(request)
                ? new WebSocketNetworkLogListener(listener, request)
                : listener;
        WebSocketListener cookieChangeListener = wrapWebSocketCookieChangeListener(snapshotListener, request);
        return customClient.newWebSocket(okRequest, wrapWebSocketListener(
                cookieChangeListener,
                lifecycleLoggingEnabled,
                request != null ? request.lifecycleLogSink : HttpLifecycleLogSink.noop()
        ));
    }

    private void logWebSocketCallStart(PreparedRequest request, Request okRequest) {
        if (okRequest == null || !isWebSocketUrl(request != null ? request.url : null)) {
            return;
        }
        String websocketUrl = valueOrDash(request.url);
        String scheme = websocketUrl.toLowerCase(Locale.ROOT).startsWith("wss://") ? "wss://" : "ws://";
        String flow = scheme.equals("wss://")
                ? "TLS + HTTP/1.1 GET + Upgrade: websocket"
                : "HTTP/1.1 GET + Upgrade: websocket";
        NetworkLogSupport.append(request, NetworkLogEventStage.CALL_START,
                "\nWebSocket URL: " + websocketUrl + "\n"
                        + "Handshake Request: " + okRequest.method() + " " + okRequest.url() + "\n"
                        + "Upgrade Flow: " + scheme + " -> " + flow + "\n");
    }

    private boolean isWebSocketUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("ws://") || lower.startsWith("wss://");
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    WebSocketListener wrapWebSocketListener(WebSocketListener listener, boolean lifecycleLoggingEnabled) {
        return wrapWebSocketListener(listener, lifecycleLoggingEnabled, HttpLifecycleLogSink.noop());
    }

    WebSocketListener wrapWebSocketListener(WebSocketListener listener,
                                            boolean lifecycleLoggingEnabled,
                                            HttpLifecycleLogSink lifecycleLogSink) {
        WebSocketListener resolvedListener = listener == null ? new WebSocketListener() {
        } : listener;
        return lifecycleLoggingEnabled
                ? new WebSocketLifecycleLogListener(resolvedListener, lifecycleLogSink)
                : resolvedListener;
    }

    private EventSourceListener wrapSseCookieChangeListener(EventSourceListener listener,
                                                            PreparedRequest request) {
        EventSourceListener resolvedListener = listener == null ? new EventSourceListener() {
        } : listener;
        if (request == null || !HttpCaptureProfiles.resolve(request).notifyCookieChanges()) {
            return resolvedListener;
        }
        return new EventSourceListener() {
            @Override
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                notifyCookieChanged();
                resolvedListener.onOpen(eventSource, response);
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                resolvedListener.onEvent(eventSource, id, type, data);
            }

            @Override
            public void onClosed(EventSource eventSource) {
                resolvedListener.onClosed(eventSource);
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                if (response != null) {
                    notifyCookieChanged();
                }
                resolvedListener.onFailure(eventSource, t, response);
            }
        };
    }

    private WebSocketListener wrapWebSocketCookieChangeListener(WebSocketListener listener,
                                                               PreparedRequest request) {
        WebSocketListener resolvedListener = listener == null ? new WebSocketListener() {
        } : listener;
        if (request == null || !HttpCaptureProfiles.resolve(request).notifyCookieChanges()) {
            return resolvedListener;
        }
        return new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                notifyCookieChanged();
                resolvedListener.onOpen(webSocket, response);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                resolvedListener.onMessage(webSocket, text);
            }

            @Override
            public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                resolvedListener.onMessage(webSocket, bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                resolvedListener.onClosing(webSocket, code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                resolvedListener.onClosed(webSocket, code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                if (response != null) {
                    notifyCookieChanged();
                }
                resolvedListener.onFailure(webSocket, t, response);
            }
        };
    }

    private void notifyCookieChanged() {
        try {
            HttpCookieStore.notifyCookieChanged();
        } catch (Throwable ignored) {
            // Cookie UI refresh must not affect realtime callbacks.
        }
    }
}
