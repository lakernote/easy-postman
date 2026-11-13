package com.laker.postman.performance.runtime;

import com.laker.postman.performance.core.plan.PerformanceSampler;
import com.laker.postman.performance.core.runtime.PerformanceCoreResultSink;


import com.laker.postman.performance.execution.PerformanceRequestExecutionResult;
import com.laker.postman.performance.execution.PerformanceRequestExecutor;
import com.laker.postman.performance.plan.PerformanceRequestSampler;
import com.laker.postman.performance.result.PerformanceResultCollector;
import com.laker.postman.service.variable.ExecutionVariableContext;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.MonotonicStopwatch;
import cn.hutool.core.text.CharSequenceUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Slf4j
public final class PerformanceSamplerExecutor {

    private final BooleanSupplier runningSupplier;
    private final BooleanSupplier efficientModeSupplier;
    private final PerformanceRequestExecutor requestExecutor;
    private final PerformanceResultCollector resultCollector;
    private final Supplier<PerformanceCoreResultSink> resultSinkSupplier;
    private static final long FAILURE_LOG_INTERVAL_MS = 5_000L;
    private final AtomicLong lastFailureLogTime = new AtomicLong(0L);
    private final AtomicLong suppressedFailureLogs = new AtomicLong(0L);

    public PerformanceSamplerExecutor(BooleanSupplier runningSupplier,
                                      BooleanSupplier efficientModeSupplier,
                                      PerformanceRequestExecutor requestExecutor,
                                      PerformanceResultCollector resultCollector) {
        this(
                runningSupplier,
                efficientModeSupplier,
                requestExecutor,
                resultCollector,
                () -> PerformanceCoreResultSink.NOOP
        );
    }

    public PerformanceSamplerExecutor(BooleanSupplier runningSupplier,
                                      BooleanSupplier efficientModeSupplier,
                                      PerformanceRequestExecutor requestExecutor,
                                      PerformanceResultCollector resultCollector,
                                      Supplier<PerformanceCoreResultSink> resultSinkSupplier) {
        this.runningSupplier = runningSupplier == null ? () -> false : runningSupplier;
        this.efficientModeSupplier = efficientModeSupplier == null ? () -> false : efficientModeSupplier;
        this.requestExecutor = requestExecutor;
        this.resultCollector = resultCollector == null
                ? new PerformanceResultCollector(PerformanceResultSink.NOOP)
                : resultCollector;
        this.resultSinkSupplier = resultSinkSupplier == null ? () -> PerformanceCoreResultSink.NOOP : resultSinkSupplier;
    }

    PerformanceRequestExecutionResult execute(PerformanceSampler sampler,
                                              ExecutionVariableContext iterationContext) {
        if (!runningSupplier.getAsBoolean() || sampler == null) {
            return null;
        }
        if (!(sampler instanceof PerformanceRequestSampler requestSampler)) {
            log.debug("Unsupported performance sampler type: {}", sampler.getClass().getName());
            return null;
        }

        MonotonicStopwatch stopwatch = MonotonicStopwatch.start();
        PerformanceRequestExecutionResult executionResult;
        try {
            executionResult = requestExecutor.execute(requestSampler, iterationContext);
        } catch (RuntimeException ex) {
            if (!runningSupplier.getAsBoolean()) {
                return null;
            }
            // An unexpected failure while preparing/post-processing still consumed one sample attempt;
            // record it as failed so the run cannot end as SUCCESS with silently missing requests.
            logSamplerFailure(requestSampler, ex);
            executionResult = failedResult(requestSampler, ex, stopwatch);
        }
        if (executionResult == null) {
            return null;
        }
        resultCollector.collect(executionResult, efficientModeSupplier.getAsBoolean(), currentResultSink());
        if (executionResult.interrupted) {
            log.debug("请求在停止时被中断: {}", requestSampler.getName());
        }
        return executionResult;
    }

    private static PerformanceRequestExecutionResult failedResult(PerformanceRequestSampler requestSampler,
                                                                  RuntimeException ex,
                                                                  MonotonicStopwatch stopwatch) {
        String apiId = "";
        String apiName = requestSampler.getName();
        try {
            HttpRequestItem item = requestSampler.getHttpRequestItem();
            if (item != null) {
                apiId = item.getId();
                apiName = CharSequenceUtil.blankToDefault(item.getName(), apiName);
            }
        } catch (RuntimeException ignored) {
            // Keep the sampler name when the request snapshot itself is the broken part.
        }
        String detail = CharSequenceUtil.blankToDefault(ex.getMessage(), ex.getClass().getName());
        return new PerformanceRequestExecutionResult(
                apiId,
                apiName,
                null,
                null,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_MSG_REQUEST_FAILED, detail),
                List.of(),
                true,
                false,
                PerformanceProtocol.HTTP,
                stopwatch.startWallTimeMs(),
                Math.max(1L, stopwatch.elapsedMs())
        );
    }

    private void logSamplerFailure(PerformanceRequestSampler requestSampler, RuntimeException ex) {
        long now = System.currentTimeMillis();
        long last = lastFailureLogTime.get();
        if (now - last >= FAILURE_LOG_INTERVAL_MS && lastFailureLogTime.compareAndSet(last, now)) {
            log.warn("压测请求执行异常，已记为失败样本: {}（期间另有 {} 次异常被抑制）",
                    requestSampler.getName(), suppressedFailureLogs.getAndSet(0L), ex);
        } else {
            suppressedFailureLogs.incrementAndGet();
        }
    }

    private PerformanceCoreResultSink currentResultSink() {
        PerformanceCoreResultSink sink = resultSinkSupplier.get();
        return sink == null ? PerformanceCoreResultSink.NOOP : sink;
    }
}
