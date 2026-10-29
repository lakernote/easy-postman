package com.laker.postman.performance.runtime;

import com.laker.postman.performance.execution.DefaultPerformanceNetworkRuntime;
import com.laker.postman.performance.execution.PerformanceExecutionConfig;
import com.laker.postman.performance.execution.PerformanceNetworkRuntime;
import com.laker.postman.performance.execution.PerformanceRequestExecutor;
import com.laker.postman.http.runtime.okhttp.HttpClientRuntimeConfig;
import com.laker.postman.performance.result.PerformanceResultCollector;
import com.laker.postman.performance.core.model.PerformanceRealtimeMetrics;
import com.laker.postman.performance.core.plan.PerformanceTestPlan;
import com.laker.postman.performance.core.runtime.*;
import com.laker.postman.performance.core.threadgroup.PerformanceRequestEstimate;
import com.laker.postman.service.js.JsScriptExecutor;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.service.variable.ExecutionVariableContext;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class PerformanceExecutionEngine {

    private final PerformanceNetworkRuntime networkRuntime;
    private final PerformanceCoreExecutionEngine<ExecutionVariableContext> delegate;
    private final PerformanceIterationContextFactory iterationContextFactory;
    private volatile PerformanceCoreResultSink resultSink = PerformanceCoreResultSink.NOOP;
    private volatile JsScriptExecutor.PooledScriptExecutor runScriptExecutor;
    private volatile boolean preparedPlanUsesScripts = true;

    public PerformanceExecutionEngine(BooleanSupplier runningSupplier,
                                      BooleanSupplier efficientModeSupplier,
                                      IntSupplier responseBodyPreviewLimitKbSupplier,
                                      PerformanceResultCollector resultCollector) {
        this(runningSupplier,
                PerformanceExecutionConfig.supplying(efficientModeSupplier, responseBodyPreviewLimitKbSupplier, () -> false),
                resultCollector,
                PerformanceRunListener.NOOP);
    }

    public PerformanceExecutionEngine(BooleanSupplier runningSupplier,
                                      BooleanSupplier efficientModeSupplier,
                                      IntSupplier responseBodyPreviewLimitKbSupplier,
                                      PerformanceResultCollector resultCollector,
                                      PerformanceRunListener runListener) {
        this(runningSupplier,
                PerformanceExecutionConfig.supplying(efficientModeSupplier, responseBodyPreviewLimitKbSupplier, () -> false),
                resultCollector,
                runListener);
    }

    public PerformanceExecutionEngine(BooleanSupplier runningSupplier,
                                      BooleanSupplier efficientModeSupplier,
                                      IntSupplier responseBodyPreviewLimitKbSupplier,
                                      PerformanceResultCollector resultCollector,
                                      PerformanceRunListener runListener,
                                      BooleanSupplier eventLoggingEnabledSupplier,
                                      PerformanceNetworkRuntime networkRuntime) {
        this(runningSupplier,
                PerformanceExecutionConfig.supplying(
                        efficientModeSupplier,
                        responseBodyPreviewLimitKbSupplier,
                        eventLoggingEnabledSupplier
                ),
                resultCollector,
                runListener,
                networkRuntime);
    }

    public PerformanceExecutionEngine(BooleanSupplier runningSupplier,
                                      PerformanceExecutionConfig executionConfig,
                                      PerformanceResultCollector resultCollector) {
        this(runningSupplier, executionConfig, resultCollector, PerformanceRunListener.NOOP);
    }

    public PerformanceExecutionEngine(BooleanSupplier runningSupplier,
                                      PerformanceExecutionConfig executionConfig,
                                      PerformanceResultCollector resultCollector,
                                      PerformanceRunListener runListener) {
        this(runningSupplier, executionConfig, resultCollector, runListener, (PerformanceNetworkRuntime) null);
    }

    public PerformanceExecutionEngine(BooleanSupplier runningSupplier,
                                      PerformanceExecutionConfig executionConfig,
                                      PerformanceResultCollector resultCollector,
                                      PerformanceRunListener runListener,
                                      Supplier<HttpClientRuntimeConfig> httpClientConfigSupplier) {
        this(runningSupplier, executionConfig, resultCollector, runListener, null, httpClientConfigSupplier);
    }

    public PerformanceExecutionEngine(BooleanSupplier runningSupplier,
                                      PerformanceExecutionConfig executionConfig,
                                      PerformanceResultCollector resultCollector,
                                      PerformanceRunListener runListener,
                                      PerformanceNetworkRuntime networkRuntime) {
        this(runningSupplier, executionConfig, resultCollector, runListener, networkRuntime, HttpClientRuntimeConfig::defaults);
    }

    private PerformanceExecutionEngine(BooleanSupplier runningSupplier,
                                       PerformanceExecutionConfig executionConfig,
                                       PerformanceResultCollector resultCollector,
                                       PerformanceRunListener runListener,
                                       PerformanceNetworkRuntime networkRuntime,
                                       Supplier<HttpClientRuntimeConfig> httpClientConfigSupplier) {
        PerformanceVirtualUserCoordinator virtualUsers = new PerformanceVirtualUserCoordinator();
        this.networkRuntime = networkRuntime == null
                ? new DefaultPerformanceNetworkRuntime(httpClientConfigSupplier, virtualUsers::currentVirtualUserScope)
                : networkRuntime;
        PerformanceExecutionConfig resolvedConfig = executionConfig == null
                ? PerformanceExecutionConfig.DEFAULT
                : executionConfig;
        resolvedConfig = resolvedConfig.withScriptExecutorSupplier(this::currentScriptExecutor);
        PerformanceRealtimeMetrics realtimeMetrics = new PerformanceRealtimeMetrics();
        PerformanceRequestExecutor requestExecutor = new PerformanceRequestExecutor(
                runningSupplier,
                this::isCancelledOrInterrupted,
                this.networkRuntime.activeSseSources(),
                this.networkRuntime.activeWebSockets(),
                realtimeMetrics,
                resolvedConfig,
                this.networkRuntime
        );
        PerformanceSamplerExecutor samplerExecutor = new PerformanceSamplerExecutor(
                runningSupplier,
                resolvedConfig::isEfficientMode,
                requestExecutor,
                resultCollector,
                this::currentResultSink
        );
        this.iterationContextFactory = new PerformanceIterationContextFactory(virtualUsers);
        PerformancePlanExecutor planExecutor = new PerformancePlanExecutor(
                () -> runningSupplier.getAsBoolean() && virtualUsers.canStartNextSample(),
                samplerExecutor
        );
        this.delegate = new PerformanceCoreExecutionEngine<>(
                runningSupplier,
                new PerformanceNetworkControl() {
                    @Override
                    public int activeWebSocketCount() {
                        return PerformanceExecutionEngine.this.networkRuntime.activeWebSocketCount();
                    }

                    @Override
                    public int activeSseCount() {
                        return PerformanceExecutionEngine.this.networkRuntime.activeSseCount();
                    }

                    @Override
                    public void cancelAll() {
                        PerformanceExecutionEngine.this.networkRuntime.cancelAll();
                    }
                },
                virtualUsers,
                realtimeMetrics,
                this.iterationContextFactory::create,
                planExecutor::executeIteration,
                runListener
        );
    }

    public int getActiveThreads() {
        return delegate.getActiveThreads();
    }

    public int sampleWindowPeakActiveThreads() {
        return delegate.sampleWindowPeakActiveThreads();
    }

    public int getActiveWebSockets() {
        return delegate.getActiveWebSockets();
    }

    public int getActiveSseStreams() {
        return delegate.getActiveSseStreams();
    }

    public void beginRun(long startTime) {
        beginRun(startTime, PerformanceCoreResultSink.NOOP);
    }

    public void beginRun(long startTime, PerformanceCoreResultSink resultSink) {
        networkRuntime.beginRun();
        startRunScriptExecutor();
        this.resultSink = resultSink == null ? PerformanceCoreResultSink.NOOP : resultSink;
        delegate.beginRun(startTime, this.resultSink);
    }

    public long getStartTime() {
        return delegate.getStartTime();
    }

    public void resetVirtualUsers() {
        iterationContextFactory.resetControlState();
        delegate.resetVirtualUsers();
    }

    public PerformanceRealtimeMetrics.Sample drainRealtimeMetricsWindow(long nowMs) {
        return delegate.drainRealtimeMetricsWindow(nowMs);
    }

    public PerformanceRealtimeMetrics.LiveSnapshot liveRealtimeMetrics(long nowMs) {
        return delegate.liveRealtimeMetrics(nowMs);
    }

    public int getTotalThreads(PerformanceTestPlan plan) {
        return delegate.getTotalThreads(plan);
    }

    public long estimateTotalRequests(PerformanceTestPlan plan) {
        return delegate.estimateTotalRequests(plan);
    }

    public PerformanceRequestEstimate estimateRequestCount(PerformanceTestPlan plan) {
        return delegate.estimateRequestCount(plan);
    }

    void prepareRun(PerformanceTestPlan plan) {
        // 纯 HTTP/CSV 压测不需要启动 GraalJS 池，避免首秒被无用初始化拉低。
        preparedPlanUsesScripts = PerformancePlanScriptUsageDetector.usesScripts(plan);
    }

    public void runTestPlan(PerformanceTestPlan plan, int totalThreads) {
        delegate.runTestPlan(plan, totalThreads);
    }

    public void cancelAllNetworkCalls() {
        delegate.cancelAllNetworkCalls();
    }

    public void endRun() {
        try {
            resultSink = PerformanceCoreResultSink.NOOP;
            delegate.endRun();
        } finally {
            closeRunScriptExecutor();
            preparedPlanUsesScripts = true;
            networkRuntime.endRun();
        }
    }

    public static void joinThreadGroupThreads(List<Thread> threadGroupThreads, Runnable cancellationAction) {
        PerformanceCoreExecutionEngine.joinThreadGroupThreads(threadGroupThreads, cancellationAction);
    }

    public static int calculateStairsTotalSteps(int startThreads, int endThreads, int step) {
        return PerformanceCoreExecutionEngine.calculateStairsTotalSteps(startThreads, endThreads, step);
    }

    private boolean isCancelledOrInterrupted(Throwable ex) {
        if (ex == null) {
            return false;
        }
        if (ex instanceof java.net.SocketTimeoutException) {
            return false;
        }
        if (ex instanceof InterruptedException) {
            return true;
        }
        if (ex instanceof java.io.InterruptedIOException) {
            if (!delegate.isRunning() || Thread.currentThread().isInterrupted()) {
                return true;
            }
            String message = ex.getMessage();
            return message != null && message.contains("Canceled");
        }
        if (ex instanceof java.io.IOException ioException) {
            String message = ioException.getMessage();
            if (message != null && message.contains("Canceled")) {
                return true;
            }
        }
        return isCancelledOrInterrupted(ex.getCause());
    }

    private PerformanceCoreResultSink currentResultSink() {
        PerformanceCoreResultSink current = resultSink;
        return current == null ? PerformanceCoreResultSink.NOOP : current;
    }

    private void startRunScriptExecutor() {
        startRunScriptExecutor(preparedPlanUsesScripts);
    }

    private void startRunScriptExecutor(boolean planUsesScripts) {
        closeRunScriptExecutor();
        if (!planUsesScripts) {
            return;
        }
        runScriptExecutor = new JsScriptExecutor.PooledScriptExecutor(
                SettingManager.getPerformanceJsContextPoolSize(),
                SettingManager.getPerformanceJsContextAcquireTimeoutMs()
        );
    }

    private JsScriptExecutor.ScriptExecutor currentScriptExecutor() {
        return runScriptExecutor;
    }

    private void closeRunScriptExecutor() {
        JsScriptExecutor.PooledScriptExecutor executor = runScriptExecutor;
        runScriptExecutor = null;
        if (executor != null) {
            executor.close();
        }
    }
}
