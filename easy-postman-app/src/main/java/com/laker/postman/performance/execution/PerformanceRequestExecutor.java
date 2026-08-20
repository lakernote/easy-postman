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
        boolean sseRequest = PerformanceRequestProtocolResolver.isSseRequest(requestSnapshot);
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
                sseRequest = PerformanceRequestProtocolResolver.isSseRequest(requestSnapshot, req);
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
                    req.responseBodyPreviewLimitBytes = resolveResponseBodyPreviewLimitBytes(
                            executionConfig.responseBodyPreviewLimitKb()
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

    static int resolveResponseBodyPreviewLimitBytes(int previewLimitKb) {
        return PerformanceRequestPreparationSupport.resolveResponseBodyPreviewLimitBytes(previewLimitKb);
    }

}
