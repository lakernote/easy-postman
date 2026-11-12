package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.config.HttpRuntimeSettings;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.error.NetworkErrorMessageResolver;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Sends one real request with the supplied (possibly unsaved) proxy settings to verify connectivity.
 */
@Slf4j
public final class ProxyConnectionChecker {
    public static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final int HTTP_PROXY_AUTH_REQUIRED = 407;

    public enum Status {
        SUCCESS,
        PROXY_AUTH_FAILED,
        FAILED
    }

    public record Result(Status status,
                         int httpCode,
                         OkHttpClientManager.ProxyInspection route,
                         String message,
                         long elapsedMs) {
    }

    private ProxyConnectionChecker() {
    }

    /**
     * Must be called off the EDT: blocks until the request completes or times out.
     */
    public static Result check(HttpRuntimeSettings settings, String target, int timeoutMs) {
        String url = normalizeTarget(target);
        return HttpRuntimeSettingsProvider.callWithThreadOverride(settings, () -> execute(url, timeoutMs));
    }

    static String normalizeTarget(String target) {
        String value = target == null ? "" : target.trim();
        if (value.isEmpty()) {
            return "https://example.com/";
        }
        return value.contains("://") ? value : "https://" + value;
    }

    private static Result execute(String url, int timeoutMs) {
        long start = System.nanoTime();
        OkHttpClient client = null;
        OkHttpClientManager.ProxyInspection route = new OkHttpClientManager.ProxyInspection(false, null);
        try {
            route = OkHttpClientManager.inspectEffectiveProxyForUrl(url);
            client = OkHttpClientManager.createDedicatedClientForUrl(url, true, timeoutMs, timeoutMs, timeoutMs, null)
                    .newBuilder()
                    .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build();
            Request request = new Request.Builder().url(url).get().build();
            // Synchronous execution keeps proxy/SOCKS authentication on this thread, where the override is visible.
            try (Response response = client.newCall(request).execute()) {
                Status status = response.code() == HTTP_PROXY_AUTH_REQUIRED ? Status.PROXY_AUTH_FAILED : Status.SUCCESS;
                return new Result(status, response.code(), route, response.message(), elapsedMs(start));
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Proxy connection check failed for {}", url, e);
            Status status = isProxyAuthenticationFailure(e) ? Status.PROXY_AUTH_FAILED : Status.FAILED;
            return new Result(status, 0, route, describe(e), elapsedMs(start));
        } finally {
            if (client != null) {
                client.dispatcher().executorService().shutdown();
                client.connectionPool().evictAll();
            }
        }
    }

    static boolean isProxyAuthenticationFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            String normalized = message.toLowerCase(Locale.ROOT);
            if (normalized.contains("failed to authenticate with proxy")
                    || (normalized.contains("socks") && normalized.contains("authentication failed"))) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Throwable throwable) {
        String message = NetworkErrorMessageResolver.toUserFriendlyMessage(throwable);
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
