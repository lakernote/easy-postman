package com.laker.postman.performance.model;

import com.laker.postman.performance.core.model.PerformanceStatsCollector;


import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PerformanceStatsCollectorListener implements PerformanceResultListener {
    private final PerformanceStatsCollector statsCollector;

    @Override
    public void onSample(PerformanceSampleEvent event) {
        if (statsCollector == null || event == null || event.sampleRecord() == null) {
            return;
        }
        if (event.sampleRecord().isInterrupted()) {
            return;
        }
        statsCollector.record(event.sampleRecord().toRequestResult());
    }
}
