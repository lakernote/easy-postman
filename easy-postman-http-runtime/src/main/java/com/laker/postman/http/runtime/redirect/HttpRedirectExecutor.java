package com.laker.postman.http.runtime.redirect;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.RedirectInfo;


import com.laker.postman.http.runtime.transport.DefaultHttpTransport;
import com.laker.postman.http.runtime.transport.HttpCallTracker;
import com.laker.postman.http.runtime.transport.HttpExchangeOptions;
import com.laker.postman.http.runtime.transport.HttpTransport;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.http.runtime.observation.NetworkLogEvent;
import com.laker.postman.http.runtime.observation.NetworkLogSink;
import com.laker.postman.http.runtime.observation.NetworkLogSupport;
import com.laker.postman.http.runtime.sse.SseResponseCallback;
import com.laker.postman.request.util.HttpUrlUtil;
import com.laker.postman.util.MonotonicStopwatch;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 负责处理重定向链
 */
@Slf4j
public class HttpRedirectExecutor {
    private final HttpTransport httpTransport;

    public HttpRedirectExecutor() {
        this(new DefaultHttpTransport());
    }

    public HttpRedirectExecutor(HttpTransport httpTransport) {
        this.httpTransport = httpTransport == null ? new DefaultHttpTransport() : httpTransport;
    }

    public HttpResponse executeWithRedirects(PreparedRequest req, int maxRedirects, SseResponseCallback callback) throws Exception {
        return executeWithRedirects(req, maxRedirects, callback, HttpCallTracker.NOOP);
    }

    public HttpResponse executeWithRedirects(PreparedRequest req,
                                             int maxRedirects,
                                             SseResponseCallback callback,
                                             HttpCallTracker callTracker) throws Exception {
        MonotonicStopwatch flowTimer = NetworkLogSupport.isEnabled(req) ? MonotonicStopwatch.start() : null;
        // 创建工作副本
        PreparedRequest workingReq = req.shallowCopy();
        deduplicateLogDetails(workingReq);

        if (!workingReq.followRedirects || maxRedirects <= 0) {
            // A zero redirect limit must also disable transport-level automatic redirects.
            workingReq.followRedirects = false;
            HttpResponse resp = executeAndSyncRequestMetadata(req, workingReq, callback, callTracker);
            if (isRedirectStatus(resp.code)) {
                logRedirectStopped(workingReq, resp.code,
                        req.followRedirects ? "max redirects reached" : "disabled", maxRedirects);
            }
            return resp;
        }

        // 重定向链由 HttpRedirectExecutor 统一处理，底层单次 OkHttp call 不能再自动跟随。
        // 否则 3xx 响应会被 OkHttp 吞掉，后续的重定向日志、跨域敏感头清理和最大跳转次数都会失效。
        workingReq.followRedirects = false;

        URL prevUrl = new URL(HttpUrlUtil.normalizeIpv6Url(workingReq.url));
        int redirectCount = 0;

        while (true) {
            HttpResponse resp = executeAndSyncRequestMetadata(req, workingReq, callback, callTracker);

            // 判断是否重定向
            RedirectInfo info = buildRedirectInfo(workingReq.url, resp);
            if (isRedirectStatus(info.statusCode) && info.location != null && !info.location.isBlank()) {
                if (redirectCount >= maxRedirects) {
                    logRedirectStopped(workingReq, resp.code, "max redirects reached", maxRedirects);
                    logRequestComplete(workingReq, resp, redirectCount, flowTimer);
                    return resp;
                }

                URL nextUrl = resolveRedirectUrl(prevUrl, info.location);
                boolean isCrossDomain = isCrossOrigin(prevUrl, nextUrl);

                redirectCount++;

                // 基于当前请求创建下一次重定向请求
                PreparedRequest redirectReq = prepareRedirectRequest(workingReq, nextUrl.toString(), info.statusCode, isCrossDomain);
                logRedirect(workingReq, redirectReq, info, redirectCount, nextUrl.toString(), isCrossDomain);
                workingReq = redirectReq;
                prevUrl = nextUrl;
            } else {
                if (isRedirectStatus(info.statusCode)) {
                    logRedirectStopped(workingReq, resp.code, "missing Location", maxRedirects);
                }
                logRequestComplete(workingReq, resp, redirectCount, flowTimer);
                return resp;
            }
        }
    }

    private static void deduplicateLogDetails(PreparedRequest request) {
        if (!NetworkLogSupport.isEnabled(request)) {
            return;
        }
        NetworkLogSink sink = NetworkLogSupport.resolveSink(request);
        AtomicReference<String> previousProxyConfiguration = new AtomicReference<>();
        AtomicReference<String> previousRequestHeaders = new AtomicReference<>();
        request.networkLogSink = event -> {
            if (event.stage() == NetworkLogEventStage.PROXY_SELECT
                    && java.util.Objects.equals(event.message(), previousProxyConfiguration.getAndSet(event.message()))) {
                return;
            }
            if (event.stage() == NetworkLogEventStage.REQUEST_HEADERS_END
                    && event.message() != null && !event.message().isBlank()
                    && event.message().equals(previousRequestHeaders.getAndSet(event.message()))) {
                sink.append(new NetworkLogEvent(event.stage(), "Headers unchanged", event.elapsedMs(), event.durationMs()));
                return;
            }
            sink.append(event);
        };
    }

    private static void logRedirectStopped(PreparedRequest request, int status, String reason, int maxRedirects) {
        String message = "Redirect stopped: " + reason + ", status: " + status;
        if ("max redirects reached".equals(reason)) {
            message += ", max: " + Math.max(0, maxRedirects);
        }
        NetworkLogSupport.append(request, NetworkLogEventStage.REDIRECT, message);
    }

    private static void logRequestComplete(PreparedRequest request,
                                           HttpResponse response,
                                           int redirectCount,
                                           MonotonicStopwatch flowTimer) {
        if (redirectCount <= 0 || !NetworkLogSupport.isEnabled(request)) {
            return;
        }
        String finalMethod = request.sentMethod == null ? request.method : request.sentMethod;
        String finalUrl = request.sentUrl == null ? request.url : request.sentUrl;
        NetworkLogSupport.append(request, NetworkLogEventStage.REQUEST_COMPLETE,
                "Status: " + response.code + "\nFinal: " + finalMethod + " " + finalUrl
                        + "\nRedirects: " + redirectCount + "\nTotal: " + flowTimer.elapsedMs() + "ms");
    }

    /**
     * Resolve Location according to URI rules before handing the result to the
     * legacy URL-based redirect model. This handles absolute, protocol-relative,
     * path-relative, and IPv6 authorities without a case-sensitive prefix check.
     */
    private static URL resolveRedirectUrl(URL previousUrl, String location) throws Exception {
        try {
            URI previousUri = URI.create(HttpUrlUtil.normalizeIpv6Url(previousUrl.toString()));
            URI locationUri = URI.create(HttpUrlUtil.normalizeIpv6Url(location));
            URI resolvedUri = previousUri.resolve(locationUri);
            return new URL(HttpUrlUtil.normalizeIpv6Url(resolvedUri.toString()));
        } catch (IllegalArgumentException strictUriFailure) {
            // Preserve the legacy URL parser's tolerance for non-strict Location
            // values (for example, an unescaped space). OkHttp canonicalizes the
            // resulting URL before sending the follow-up request.
            return new URL(previousUrl, location);
        }
    }

    private HttpResponse executeAndSyncRequestMetadata(PreparedRequest originalReq,
                                                       PreparedRequest workingReq,
                                                       SseResponseCallback callback,
                                                       HttpCallTracker callTracker) throws Exception {
        HttpResponse resp = httpTransport.execute(
                workingReq,
                HttpExchangeOptions.builder()
                        .callback(callback)
                        .callTracker(callTracker)
                        .build()
        );

        // 更新原始请求对象的 OkHttp 相关字段，UI 和诊断导出需要看到最后一次实际发送快照。
        originalReq.sentUrl = workingReq.sentUrl;
        originalReq.sentMethod = workingReq.sentMethod;
        originalReq.sentHeadersList = workingReq.sentHeadersList;
        originalReq.sentRequestBody = workingReq.sentRequestBody;
        originalReq.sentRequestBodyReplayable = workingReq.sentRequestBodyReplayable;
        originalReq.exchangeEventInfo = workingReq.exchangeEventInfo;
        return resp;
    }

    /**
     * 构建重定向信息
     */
    private static RedirectInfo buildRedirectInfo(String url, HttpResponse resp) {
        RedirectInfo info = new RedirectInfo();
        info.url = url;
        info.statusCode = resp.code;
        info.headers = resp.headers;
        info.responseBody = resp.body;
        info.location = extractLocationHeader(resp);
        return info;
    }

    /**
     * 准备重定向请求
     */
    static PreparedRequest prepareRedirectRequest(PreparedRequest currentReq, String newUrl, int statusCode, boolean isCrossDomain) {
        PreparedRequest redirectReq = currentReq.shallowCopy();
        redirectReq.url = newUrl;
        boolean redirectToGet = shouldRedirectToGet(statusCode, redirectReq.method);
        boolean preserveRequestBody = !redirectToGet && !"HEAD".equalsIgnoreCase(redirectReq.method);

        // 根据状态码处理 method 和 body
        if (!preserveRequestBody) {
            // 301/302 only permit the historical POST -> GET rewrite.
            // 303 changes every method except HEAD to GET; 307/308 preserve it.
            if (redirectToGet) {
                redirectReq.method = "GET";
            }
            redirectReq.body = null;
            redirectReq.isMultipart = false;
            redirectReq.formDataList = null;
            redirectReq.urlencodedList = null;
        }

        // 处理 headers：移除特定 header
        redirectReq.headersList = cleanHeadersList(
                redirectReq.headersList,
                isCrossDomain,
                preserveRequestBody,
                redirectReq.isMultipart
        );

        return redirectReq;
    }

    private static boolean isRedirectStatus(int statusCode) {
        return statusCode == 300
                || statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static boolean shouldRedirectToGet(int statusCode, String method) {
        if ("HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        if (statusCode == 303) {
            return true;
        }
        return (statusCode == 301 || statusCode == 302) && "POST".equalsIgnoreCase(method);
    }


    /**
     * 清理 List 结构的 headersList
     */
    static List<HttpHeader> cleanHeadersList(List<HttpHeader> headersList,
                                             boolean isCrossDomain,
                                             boolean preserveRequestBody,
                                             boolean isMultipartRequest) {
        if (headersList == null) {
            return Collections.emptyList();
        }

        List<HttpHeader> cleaned = new ArrayList<>(headersList);
        boolean shouldRemoveContentType = !preserveRequestBody || isMultipartRequest;
        cleaned.removeIf(h -> h.isEnabled() && (
                "Content-Length".equalsIgnoreCase(h.getKey()) ||
                        "Host".equalsIgnoreCase(h.getKey()) ||
                        (shouldRemoveContentType && "Content-Type".equalsIgnoreCase(h.getKey())) ||
                        (isCrossDomain && ("Authorization".equalsIgnoreCase(h.getKey()) || "Cookie".equalsIgnoreCase(h.getKey())))
        ));

        return cleaned;
    }

    static boolean isCrossOrigin(URL previousUrl, URL nextUrl) {
        if (previousUrl == null || nextUrl == null) {
            return false;
        }
        return !previousUrl.getProtocol().equalsIgnoreCase(nextUrl.getProtocol())
                || !previousUrl.getHost().equalsIgnoreCase(nextUrl.getHost())
                || effectivePort(previousUrl) != effectivePort(nextUrl);
    }

    private static int effectivePort(URL url) {
        int explicitPort = url.getPort();
        return explicitPort != -1 ? explicitPort : url.getDefaultPort();
    }

    /**
     * 记录重定向日志
     */
    private static void logRedirect(PreparedRequest request,
                                    PreparedRequest redirectRequest,
                                    RedirectInfo info,
                                    int redirectNumber,
                                    String nextUrl,
                                    boolean crossOrigin) {
        if (!NetworkLogSupport.isEnabled(request)) {
            return;
        }
        String currentMethod = request.method == null || request.method.isBlank() ? "HTTP" : request.method;
        String nextMethod = redirectRequest.method == null || redirectRequest.method.isBlank()
                ? currentMethod
                : redirectRequest.method;
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Redirect #").append(redirectNumber).append("\n");
        logMessage.append("Status: ").append(info.statusCode).append("\n");
        logMessage.append("From: ").append(currentMethod).append(" ").append(info.url).append("\n");
        logMessage.append("To: ").append(nextMethod).append(" ").append(nextUrl).append("\n");
        if (crossOrigin) {
            logMessage.append("Cross-Origin: true\n");
            String removedHeaders = request.headersList == null ? "" : request.headersList.stream()
                    .filter(header -> header != null && header.isEnabled())
                    .map(HttpHeader::getKey)
                    .filter(key -> "Authorization".equalsIgnoreCase(key) || "Cookie".equalsIgnoreCase(key))
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (!removedHeaders.isEmpty()) {
                logMessage.append("Removed Headers: ").append(removedHeaders).append("\n");
            }
        }
        if (!currentMethod.equalsIgnoreCase(nextMethod)) {
            logMessage.append("Method Changed: ").append(currentMethod).append(" → ").append(nextMethod);
        }
        NetworkLogSupport.append(request, NetworkLogEventStage.REDIRECT, logMessage.toString());
    }

    private static String extractLocationHeader(HttpResponse resp) {
        if (resp.headers != null) {
            for (Map.Entry<String, List<String>> entry : resp.headers.entrySet()) {
                if (entry.getKey() != null && "Location".equalsIgnoreCase(entry.getKey())) {
                    List<String> values = entry.getValue();
                    return values == null || values.isEmpty() ? null : values.get(0);
                }
            }
        }
        return null;
    }
}
