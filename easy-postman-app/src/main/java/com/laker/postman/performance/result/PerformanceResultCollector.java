package com.laker.postman.performance.result;

import com.laker.postman.performance.core.model.PerformanceSampleRecord;
import com.laker.postman.performance.core.runtime.PerformanceCoreResultSink;


import com.laker.postman.performance.execution.PerformanceRequestExecutionResult;
import com.laker.postman.performance.model.PerformanceResultListener;
import com.laker.postman.performance.model.PerformanceSampleEvent;
import com.laker.postman.performance.model.PerformanceSampleResult;
import com.laker.postman.performance.model.PerformanceSampleRecordFactory;
import com.laker.postman.performance.runtime.PerformanceResultSink;
import com.laker.postman.performance.runtime.PerformanceResultSinkListenerAdapter;

import java.util.List;

public final class PerformanceResultCollector {

    private final PerformanceResultSink resultSink;

    public PerformanceResultCollector(List<PerformanceResultListener> listeners) {
        this(new PerformanceResultSinkListenerAdapter(listeners));
    }

    public PerformanceResultCollector(PerformanceResultSink resultSink) {
        this.resultSink = resultSink == null ? PerformanceResultSink.NOOP : resultSink;
    }

    public void collect(PerformanceRequestExecutionResult executionResult, boolean efficientMode) {
        collect(executionResult, efficientMode, PerformanceCoreResultSink.NOOP);
    }

    public void collect(PerformanceRequestExecutionResult executionResult,
                        boolean efficientMode,
                        PerformanceCoreResultSink runResultSink) {
        if (executionResult == null) {
            return;
        }
        boolean recordableInterruptedResult = isRecordableInterruptedResult(executionResult);
        if (executionResult.interrupted && !recordableInterruptedResult) {
            return;
        }

        PerformanceSampleRecord sampleRecord = PerformanceSampleRecordFactory.fromExecutionResult(executionResult);
        if (sampleRecord == null) {
            return;
        }
        PerformanceSampleEvent event = PerformanceSampleEvent.lazy(
                sampleRecord,
                executionResult,
                efficientMode,
                () -> PerformanceSampleResult.fromExecutionResult(executionResult)
        );
        resultSink.onSample(event);
        PerformanceCoreResultSink resolvedRunSink = runResultSink == null
                ? PerformanceCoreResultSink.NOOP
                : runResultSink;
        if (resolvedRunSink.acceptsSamples() && !sampleRecord.isInterrupted()) {
            resolvedRunSink.onSample(sampleRecord);
        }
    }

    private boolean isRecordableInterruptedResult(PerformanceRequestExecutionResult executionResult) {
        if (!executionResult.interrupted) {
            return false;
        }
        // Keep an attempted canceled sample in the result list for every protocol,
        // even when cancellation happened before a response or stream message arrived.
        return executionResult.request != null
                || executionResult.response != null
                || executionResult.fallbackCostMs > 0;
    }

}
