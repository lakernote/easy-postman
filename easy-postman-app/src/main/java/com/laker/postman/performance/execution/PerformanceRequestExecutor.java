package com.laker.postman.performance.execution;

import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.model.PerformanceRealtimeMetrics;
import com.laker.postman.performance.core.plan.PerformanceAssertionElement;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;


import cn.hutool.core.text.CharSequenceUtil;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.script.model.TestResult;
import com.laker.postman.performance.plan.PerformanceRequestSampler;
import com.laker.postman.service.js.ScriptExecutionResult;
import com.laker.postman.service.variable.ExecutionVariableContext;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.MonotonicStopwatch;
import lombok.extern.slf4j.Slf4j;
import com.laker.postman.http.runtime.transport.RealtimeConnectionHandle;
import com.laker.postman.http.runtime.transport.RealtimeWebSocketConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

@Slf4j
public class PerformanceRequestExecutor {
    // Upper bound of the in-memory body that assertions/extractors/post-scripts may evaluate per request.
    static final int MAX_EVALUATED_RESPONSE_BODY_BYTES = 8 * 1024 * 1024;

    private final BooleanSupplier runningSupplier;
    private final Predicate<Throwable> cancelledChecker;
    private final Set<RealtimeConnectionHandle> activeSseSources;
    private final Set<RealtimeWebSocketConnection> activeWebSockets;
    private final PerformanceRealtimeMetrics realtimeMetrics;
    private final PerformanceExecutionConfig executionConfig;
    private final PerformanceNetworkRuntime networkRuntime;
    private final PerformanceRequestRuntime requestRuntime;
    private final PerformanceRequestTransportExecutor transportExecutor;
    private final PerformanceRequestPostProcessor postProcessor;

    public PerformanceRequestExecutor(BooleanSupplier runningSupplier,
                                      Predicate<Throwable> cancelledChecker,
                                      Set<RealtimeConnectionHandle> activeSseSources,
                                      Set<RealtimeWebSocketConnection> activeWebSockets) {
        this(runningSupplier, cancelledChecker, activeSseSources, activeWebSockets, new PerformanceRealtimeMetrics());
    }

    public PerformanceRequestExecutor(BooleanSupplier runningSupplier,
                                      Predicate<Throwable> cancelledChecker,
                                      Set<RealtimeConnectionHandle> activeSseSources,
                                      Set<RealtimeWebSocketConnection> activeWebSockets,
                                      PerformanceRealtimeMetrics realtimeMetrics) {
        this(runningSupplier, cancelledChecker, activeSseSources, activeWebSockets, realtimeMetrics,
                PerformanceExecutionConfig.DEFAULT);
    }

    public PerformanceRequestExecutor(BooleanSupplier runningSupplier,
                                      Predicate<Throwable> cancelledChecker,
                                      Set<RealtimeConnectionHandle> activeSseSources,
                                      Set<RealtimeWebSocketConnection> activeWebSockets,
                                      PerformanceRealtimeMetrics realtimeMetrics,
                                      BooleanSupplier efficientModeSupplier,
                                      IntSupplier responseBodyPreviewLimitKbSupplier) {
        this(runningSupplier, cancelledChecker, activeSseSources, activeWebSockets, realtimeMetrics,
                PerformanceExecutionConfig.supplying(efficientModeSupplier, responseBodyPreviewLimitKbSupplier, () -> false));
    }

    public PerformanceRequestExecutor(BooleanSupplier runningSupplier,
                                      Predicate<Throwable> cancelledChecker,
                                      Set<RealtimeConnectionHandle> activeSseSources,
                                      Set<RealtimeWebSocketConnection> activeWebSockets,
                                      PerformanceRealtimeMetrics realtimeMetrics,
                                      BooleanSupplier efficientModeSupplier,
                                      IntSupplier responseBodyPreviewLimitKbSupplier,
                                      BooleanSupplier eventLoggingEnabledSupplier) {
        this(runningSupplier, cancelledChecker, activeSseSources, activeWebSockets, realtimeMetrics,
                PerformanceExecutionConfig.supplying(
                        efficientModeSupplier,
                        responseBodyPreviewLimitKbSupplier,
                        eventLoggingEnabledSupplier
                ));
    }

    public PerformanceRequestExecutor(BooleanSupplier runningSupplier,
                                      Predicate<Throwable> cancelledChecker,
                                      Set<RealtimeConnectionHandle> activeSseSources,
                                      Set<RealtimeWebSocketConnection> activeWebSockets,
                                      PerformanceExecutionConfig executionConfig) {
        this(runningSupplier, cancelledChecker, activeSseSources, activeWebSockets, new PerformanceRealtimeMetrics(),
                executionConfig);
    }

    public PerformanceRequestExecutor(BooleanSupplier runningSupplier,
                                      Predicate<Throwable> cancelledChecker,
                                      Set<RealtimeConnectionHandle> activeSseSources,
                                      Set<RealtimeWebSocketConnection> activeWebSockets,
                                      PerformanceRealtimeMetrics realtimeMetrics,
                                      PerformanceExecutionConfig executionConfig) {
        this(runningSupplier, cancelledChecker, activeSseSources, activeWebSockets, realtimeMetrics, executionConfig,
                new DefaultPerformanceNetworkRuntime(activeSseSources, activeWebSockets));
    }

    public PerformanceRequestExecutor(BooleanSupplier runningSupplier,
                                      Predicate<Throwable> cancelledChecker,
                                      Set<RealtimeConnectionHandle> activeSseSources,
                                      Set<RealtimeWebSocketConnection> activeWebSockets,
                                      PerformanceRealtimeMetrics realtimeMetrics,
                                      PerformanceExecutionConfig executionConfig,
                                      PerformanceNetworkRuntime networkRuntime) {
        this(runningSupplier, cancelledChecker, activeSseSources, activeWebSockets, realtimeMetrics, executionConfig,
                networkRuntime, new DefaultPerformanceRequestRuntime());
    }

    PerformanceRequestExecutor(BooleanSupplier runningSupplier,
                               Predicate<Throwable> cancelledChecker,
                               Set<RealtimeConnectionHandle> activeSseSources,
                               Set<RealtimeWebSocketConnection> activeWebSockets,
                               PerformanceRealtimeMetrics realtimeMetrics,
                               PerformanceExecutionConfig executionConfig,
                               PerformanceNetworkRuntime networkRuntime,
                               PerformanceRequestRuntime requestRuntime) {
        this.runningSupplier = runningSupplier;
        this.cancelledChecker = cancelledChecker;
        this.activeSseSources = activeSseSources;
        this.activeWebSockets = activeWebSockets;
        this.realtimeMetrics = realtimeMetrics == null ? new PerformanceRealtimeMetrics() : realtimeMetrics;
        this.executionConfig = executionConfig == null ? PerformanceExecutionConfig.DEFAULT : executionConfig;
        this.networkRuntime = networkRuntime == null ? new DefaultPerformanceNetworkRuntime(
                this.activeSseSources,
                this.activeWebSockets
        ) : networkRuntime;
        this.requestRuntime = requestRuntime == null ? new DefaultPerformanceRequestRuntime() : requestRuntime;
        this.transportExecutor = new PerformanceRequestTransportExecutor(
                this.runningSupplier,
                this.cancelledChecker,
                this.networkRuntime,
                this.realtimeMetrics,
                this.executionConfig::responseBodyPreviewLimitKb
        );
        this.postProcessor = new PerformanceRequestPostProcessor(this.runningSupplier);
    }

    public PerformanceRequestExecutionResult execute(PerformanceRequestSampler requestSampler,
                                                     ExecutionVariableContext iterationContext) {
        PerformanceRequestSnapshot requestSnapshot = requestSampler.getRequestSnapshot();
        PerformancePreparedRequest preparedRequest = requestRuntime.prepare(
                requestSnapshot,
                requestSampler,
                iterationContext,
                executionConfig
        );
        if (preparedRequest == null) {
            return null;
        }
        String apiId = preparedRequest.requestId();
        String apiName = preparedRequest.requestName();
        boolean webSocketRequest = PerformanceRequestProtocolResolver.isWebSocketRequest(requestSnapshot);

        PreparedRequest req = preparedRequest.request();
        String requestBodyTemplate = preparedRequest.requestBodyTemplate();
        PerformanceScriptRuntime scriptRuntime = preparedRequest.scriptRuntime();

        String errorMsg = "";
        List<TestResult> testResults = new ArrayList<>();
        boolean executionFailed = false;
        ScriptExecutionResult preResult = scriptRuntime.executePreScript();
        boolean preOk = preResult.isSuccess();
        if (!preOk) {
            log.error("前置脚本: {}", preResult.getErrorMessage());
            errorMsg = I18nUtil.getMessage(MessageKeys.PERFORMANCE_MSG_PRE_SCRIPT_FAILED, preResult.getErrorMessage());
            executionFailed = true;
        }
        if (!runningSupplier.getAsBoolean()) {
            return null;
        }
        if (preOk) {
            // Pre-script may replace pm.request.body. Keep the resulting template for WebSocket send steps,
            // while finalizeRequest resolves the concrete body used by ordinary HTTP requests.
            requestBodyTemplate = req.body;
            scriptRuntime.finalizeRequest();
        }

        MonotonicStopwatch requestStopwatch = MonotonicStopwatch.start();
        long requestStartTime = requestStopwatch.startWallTimeMs();
        long costMs = 0L;
        boolean interrupted = false;
        HttpResponse resp = null;
        boolean sseRequest = PerformanceRequestProtocolResolver.isSseScenario(requestSampler);
        PerformanceProtocol protocol = PerformanceRequestProtocolResolver.resolvePerformanceProtocol(webSocketRequest, sseRequest);
        PerformanceResponseCapturePlan capturePlan = PerformanceResponseCapturePlan.resolve(
                executionConfig.isEfficientMode(),
                requestSampler,
                sseRequest,
                webSocketRequest,
                req.postscript
        );

        if (preOk && runningSupplier.getAsBoolean()) {
            try {
                PerformanceRequestPreparationSupport.configurePreparedRequest(req, executionConfig.isEventLoggingEnabled());
                // SSE is configured by the performance plan's Connect/Read stages. The request
                // snapshot itself remains HTTP; headers and body fields do not define its protocol.
                sseRequest = PerformanceRequestProtocolResolver.isSseScenario(requestSampler);
                webSocketRequest = PerformanceRequestProtocolResolver.isWebSocketRequest(requestSnapshot);
                protocol = PerformanceRequestProtocolResolver.resolvePerformanceProtocol(webSocketRequest, sseRequest);
                boolean transportSseRequest = sseRequest;
                boolean transportWebSocketRequest = webSocketRequest;
                capturePlan = PerformanceResponseCapturePlan.resolve(
                        executionConfig.isEfficientMode(),
                        requestSampler,
                        transportSseRequest,
                        transportWebSocketRequest,
                        req.postscript
                );
                if (!transportSseRequest && !transportWebSocketRequest) {
                    req.responseBodyMode = capturePlan.httpResponseBodyMode();
                    req.responseBodyPreviewLimitBytes = resolveResponseBodyReadLimitBytes(
                            capturePlan,
                            executionConfig.responseBodyPreviewLimitBytes()
                    );
                }
                PerformanceResponseCapturePlan transportCapturePlan = capturePlan;
                String transportRequestBodyTemplate = requestBodyTemplate;
                ProtocolExecutionResult protocolResult = scriptRuntime.withExecutionContextThrowing(() ->
                        transportExecutor.execute(req, requestSampler, requestSnapshot, transportSseRequest, transportWebSocketRequest,
                                transportRequestBodyTemplate, scriptRuntime, transportCapturePlan)
                );
                resp = protocolResult.response();
                errorMsg = CharSequenceUtil.blankToDefault(protocolResult.errorMsg(), errorMsg);
                executionFailed = protocolResult.executionFailed();
                interrupted = protocolResult.interrupted();
                if (resp != null && resp.bodyIncomplete && !interrupted) {
                    // Headers arrived but the body was cut off; the status code alone must not count as success.
                    executionFailed = true;
                    errorMsg = CharSequenceUtil.blankToDefault(errorMsg, resp.body);
                } else if (resp != null && resp.bodyPreviewTruncated && capturePlan.evaluatesResponseBody() && !interrupted) {
                    // Assertions/extractors would judge a partial body; report it instead of a misleading verdict.
                    executionFailed = true;
                    errorMsg = CharSequenceUtil.blankToDefault(errorMsg, I18nUtil.getMessage(
                            MessageKeys.PERFORMANCE_MSG_RESPONSE_BODY_TOO_LARGE_TO_EVALUATE,
                            resp.bodySize,
                            req.responseBodyPreviewLimitBytes
                    ));
                }
                if (!protocolResult.testResults().isEmpty()) {
                    testResults.addAll(protocolResult.testResults());
                }
            } catch (Exception ex) {
                if (cancelledChecker.test(ex)) {
                    log.debug("请求被取消/中断（压测已停止）: {}", ex.getMessage());
                    errorMsg = I18nUtil.getMessage(
                            MessageKeys.PERFORMANCE_MSG_EXECUTION_INTERRUPTED,
                            "Client stopped HTTP request before completion"
                    );
                    interrupted = true;
                } else {
                    log.error("请求执行失败: {}", ex.getMessage(), ex);
                    errorMsg = I18nUtil.getMessage(MessageKeys.PERFORMANCE_MSG_REQUEST_FAILED, ex.getMessage());
                    executionFailed = true;
                }
            } finally {
                costMs = requestStopwatch.elapsedMs();
            }

            PerformanceRequestPostProcessResult postProcessResult = postProcessor.process(
                    requestSampler,
                    resp,
                    sseRequest,
                    webSocketRequest,
                    scriptRuntime,
                    errorMsg,
                    executionFailed,
                    testResults,
                    capturePlan
            );
            errorMsg = postProcessResult.errorMsg();
            executionFailed = postProcessResult.executionFailed();
            if (!sseRequest && !webSocketRequest) {
                retainResponseBodyPreview(resp, executionConfig.responseBodyPreviewLimitBytes());
            }
        } else {
            costMs = requestStopwatch.elapsedMs();
        }

        return new PerformanceRequestExecutionResult(
                apiId,
                apiName,
                req,
                resp,
                errorMsg,
                testResults,
                executionFailed,
                interrupted,
                protocol,
                requestStartTime,
                costMs
        );
    }

    static PreparedRequest.ResponseBodyMode resolveHttpResponseBodyModeForAssertionElements(
            boolean efficientMode,
            List<PerformanceAssertionElement> assertionNodes,
            String postscript) {
        return PerformanceRequestPreparationSupport.resolveHttpResponseBodyModeForAssertionElements(
                efficientMode,
                assertionNodes,
                postscript
        );
    }

    static int resolveResponseBodyReadLimitBytes(PerformanceResponseCapturePlan capturePlan, int previewLimitBytes) {
        if (capturePlan != null && capturePlan.evaluatesResponseBody()) {
            return Math.max(previewLimitBytes, MAX_EVALUATED_RESPONSE_BODY_BYTES);
        }
        return previewLimitBytes;
    }

    /**
     * Shrinks the evaluated body to the retained preview once assertions, extractors and post-scripts are done,
     * and only then appends the human-readable truncation notice.
     */
    static void retainResponseBodyPreview(HttpResponse resp, int previewLimitBytes) {
        if (resp == null || resp.body == null || resp.bodyIncomplete
                || resp.filePath != null || resp.rawBodyBytes != null) {
            return;
        }
        String retained = resp.body;
        // Worst case UTF-8 is 3 bytes per UTF-16 char, so shorter bodies never need scanning.
        if ((long) resp.body.length() * 3 > previewLimitBytes) {
            retained = WebSocketReceivedMessageBuffer.retainUtf8Prefix(resp.body, previewLimitBytes);
        }
        boolean truncated = resp.bodyPreviewTruncated || retained.length() < resp.body.length();
        if (!truncated) {
            return;
        }
        resp.bodyPreviewTruncated = true;
        resp.body = retained + I18nUtil.getMessage(
                MessageKeys.RESPONSE_BODY_TRUNCATED_PERFORMANCE,
                resp.bodySize,
                previewLimitBytes
        );
    }

    static int resolveResponseBodyPreviewLimitBytes(int previewLimitKb) {
        return PerformanceRequestPreparationSupport.resolveResponseBodyPreviewLimitBytes(previewLimitKb);
    }

}
