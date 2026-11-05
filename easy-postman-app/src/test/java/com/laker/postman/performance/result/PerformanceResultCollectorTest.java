package com.laker.postman.performance.result;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.performance.execution.PerformanceRequestExecutionResult;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.model.PerformanceResultRetentionPolicy;
import com.laker.postman.performance.core.model.PerformanceSampleRecord;
import com.laker.postman.performance.model.PerformanceSampleEvent;
import com.laker.postman.performance.model.PerformanceSampleResult;
import com.laker.postman.performance.core.model.PerformanceStatsCollector;
import com.laker.postman.performance.model.PerformanceStatsCollectorListener;
import com.laker.postman.performance.core.model.PerformanceStatsSnapshot;
import com.laker.postman.performance.core.model.RequestResult;
import com.laker.postman.performance.core.runtime.PerformanceCoreResultSink;
import com.laker.postman.performance.runtime.PerformanceResultSink;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class PerformanceResultCollectorTest {

    @Test
    public void collectorShouldOnlyBeWiredWithExplicitResultListeners() {
        assertFalse(hasConstructorParameter(PerformanceResultCollector.class, PerformanceStatsCollector.class));
    }

    @Test
    public void collectorShouldPublishProtocolNeutralSampleEventsToListeners() {
        List<PerformanceSampleEvent> events = new ArrayList<>();
        PerformanceResultCollector collector = new PerformanceResultCollector(List.of(events::add));

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.costMs = 42;
        response.endTime = 142;

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api",
                "API",
                new PreparedRequest(),
                response,
                "",
                List.of(),
                false,
                false,
                PerformanceProtocol.HTTP,
                100L,
                0L
        );

        collector.collect(executionResult, true);

        assertEquals(events.size(), 1);
        PerformanceSampleEvent event = events.get(0);
        assertSame(event.executionResult(), executionResult);
        assertTrue(event.efficientMode());
        assertEquals(event.sampleResult().getApiId(), "api");
        assertEquals(event.sampleResult().getElapsedTimeMs(), 42L);
        assertTrue(event.sampleResult().isSuccessful());
    }

    @Test
    public void collectorShouldAcceptNonUiResultSink() {
        List<PerformanceSampleEvent> events = new ArrayList<>();
        PerformanceResultCollector collector = new PerformanceResultCollector(new PerformanceResultSink() {
            @Override
            public void onSample(PerformanceSampleEvent event) {
                events.add(event);
            }
        });

        collector.collect(successfulHttpResult(), true);

        assertEquals(events.size(), 1);
        assertEquals(events.get(0).getSampleResult().getApiId(), "api");
    }

    @Test
    public void collectorShouldPublishCoreSampleRecordToResultSink() {
        List<PerformanceSampleRecord> records = new ArrayList<>();
        PerformanceResultCollector collector = new PerformanceResultCollector(new PerformanceResultSink() {
            @Override
            public boolean acceptsSamples() {
                return true;
            }

            @Override
            public void onSample(PerformanceSampleRecord record) {
                records.add(record);
            }
        });

        collector.collect(successfulHttpResult(), true);

        assertEquals(records.size(), 1);
        assertEquals(records.get(0).getApiId(), "api");
        assertEquals(records.get(0).getProtocol(), PerformanceProtocol.HTTP);
        assertTrue(records.get(0).isSuccessful());
    }

    @Test
    public void collectorShouldPublishCoreSampleRecordToPerRunSink() {
        List<PerformanceSampleRecord> records = new ArrayList<>();
        PerformanceResultCollector collector = new PerformanceResultCollector(PerformanceResultSink.NOOP);

        collector.collect(successfulHttpResult(), true, new PerformanceCoreResultSink() {
            @Override
            public boolean acceptsSamples() {
                return true;
            }

            @Override
            public void onSample(PerformanceSampleRecord record) {
                records.add(record);
            }
        });

        assertEquals(records.size(), 1);
        assertEquals(records.get(0).getApiId(), "api");
        assertEquals(records.get(0).getProtocol(), PerformanceProtocol.HTTP);
    }

    @Test
    public void collectorShouldNotBuildCoreSampleRecordWhenPerRunSinkDoesNotAcceptSamples() {
        PerformanceResultCollector collector = new PerformanceResultCollector(PerformanceResultSink.NOOP);

        collector.collect(successfulHttpResult(), true, new PerformanceCoreResultSink() {
            @Override
            public void onSample(PerformanceSampleRecord record) {
                throw new AssertionError("sample record should not be published");
            }
        });
    }

    @Test
    public void shouldOnlyRecordSlowOrFailedResultsInEfficientMode() {
        assertFalse(PerformanceResultRetentionPolicy.shouldRecord(true, true, 1200, 3000));
        assertTrue(PerformanceResultRetentionPolicy.shouldRecord(true, true, 3000, 3000));
        assertTrue(PerformanceResultRetentionPolicy.shouldRecord(true, false, 100, 3000));
        assertTrue(PerformanceResultRetentionPolicy.shouldRecord(false, true, 100, 3000));
        assertFalse(PerformanceResultRetentionPolicy.shouldRecord(true, true, 5000, 0));
    }

    @Test
    public void statsListenerShouldUseLightweightSampleRecordWithoutResolvingDetails() {
        PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
        AtomicInteger detailResolutionCount = new AtomicInteger();
        PerformanceSampleRecord record = PerformanceSampleRecord.builder()
                .apiId("api")
                .apiName("API")
                .protocol(PerformanceProtocol.HTTP)
                .startTimeMs(100)
                .endTimeMs(110)
                .elapsedTimeMs(10)
                .responseCode(200)
                .successful(true)
                .build();
        PerformanceSampleEvent event = PerformanceSampleEvent.lazy(
                record,
                successfulHttpResult(),
                true,
                () -> {
                    detailResolutionCount.incrementAndGet();
                    return PerformanceSampleResult.fromExecutionResult(successfulHttpResult());
                }
        );

        new PerformanceStatsCollectorListener(statsCollector).onSample(event);

        assertEquals(statsCollector.snapshot().totalRequests(), 1L);
        assertEquals(detailResolutionCount.get(), 0);
    }

    @Test
    public void shouldExtractWebSocketStreamMetricsFromResponseHeaders() {
        HttpResponse response = new HttpResponse();
        response.headers = new LinkedHashMap<>();
        response.costMs = 5000;
        response.endTime = 6000;
        response.addHeader("X-Easy-WS-Sent-Count", List.of("2"));
        response.addHeader("X-Easy-WS-Received-Count", List.of("4"));
        response.addHeader("X-Easy-WS-Message-Count", List.of("3"));
        response.addHeader("X-Easy-WS-First-Message-Latency-Ms", List.of("120"));

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api-ws",
                "WS API",
                new PreparedRequest(),
                response,
                "",
                List.of(),
                false,
                false,
                PerformanceProtocol.WEBSOCKET,
                1000L,
                0L
        );

        RequestResult result = PerformanceSampleResult.fromExecutionResult(executionResult).toRequestResult();

        assertEquals(result.protocol, PerformanceProtocol.WEBSOCKET);
        assertEquals(result.sentMessages, 2);
        assertEquals(result.receivedMessages, 4);
        assertEquals(result.matchedMessages, 3);
        assertEquals(result.firstMessageLatencyMs, 120L);
    }

    @Test
    public void shouldExtractSseFirstEventLatencyFromResponseHeaders() {
        HttpResponse response = new HttpResponse();
        response.headers = new LinkedHashMap<>();
        response.costMs = 5000;
        response.endTime = 6000;
        response.addHeader("X-Easy-SSE-Event-Count", List.of("4"));
        response.addHeader("X-Easy-SSE-Message-Count", List.of("1"));
        response.addHeader("X-Easy-SSE-First-Event-Latency-Ms", List.of("90"));

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api-sse",
                "SSE API",
                new PreparedRequest(),
                response,
                "",
                List.of(),
                false,
                false,
                PerformanceProtocol.SSE,
                1000L,
                0L
        );

        RequestResult result = PerformanceSampleResult.fromExecutionResult(executionResult).toRequestResult();

        assertEquals(result.protocol, PerformanceProtocol.SSE);
        assertEquals(result.receivedMessages, 4);
        assertEquals(result.matchedMessages, 1);
        assertEquals(result.firstMessageLatencyMs, 90L);
    }

    @Test
    public void shouldRecordInterruptedStreamResultWhenResponseContainsCollectedMetrics() {
        PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
        List<PerformanceSampleEvent> events = new ArrayList<>();
        HttpResponse response = new HttpResponse();
        response.headers = new LinkedHashMap<>();
        response.code = 101;
        response.costMs = 2500;
        response.endTime = 3500;
        response.addHeader("X-Easy-WS-Sent-Count", List.of("12"));
        response.addHeader("X-Easy-WS-Received-Count", List.of("7"));
        response.addHeader("X-Easy-WS-Message-Count", List.of("3"));

        PerformanceResultCollector collector = new PerformanceResultCollector(List.of(
                new PerformanceStatsCollectorListener(statsCollector),
                events::add
        ));

        collector.collect(new PerformanceRequestExecutionResult(
                "api-ws",
                "WS API",
                new PreparedRequest(),
                response,
                "",
                List.of(),
                false,
                true,
                PerformanceProtocol.WEBSOCKET,
                1000L,
                0L
        ), false);

        assertEquals(statsCollector.snapshot().totalRequests(), 0L, "取消的采样不计入统计");
        assertEquals(events.size(), 1, "取消的采样仍保留在结果列表");
        RequestResult result = events.get(0).getSampleResult().toRequestResult();
        assertFalse(result.success);
        assertEquals(result.sentMessages, 12L);
        assertEquals(result.receivedMessages, 7L);
        assertEquals(result.matchedMessages, 3L);
    }

    @Test
    public void shouldRetainInterruptedStreamDetailsInEfficientMode() {
        PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
        List<PerformanceSampleEvent> events = new ArrayList<>();
        HttpResponse response = new HttpResponse();
        response.headers = new LinkedHashMap<>();
        response.code = 101;
        response.costMs = 100;
        response.endTime = 1100;
        response.addHeader("X-Easy-WS-Sent-Count", List.of("1"));

        PerformanceResultCollector collector = new PerformanceResultCollector(List.of(
                new PerformanceStatsCollectorListener(statsCollector),
                events::add
        ));

        collector.collect(new PerformanceRequestExecutionResult(
                "api-ws",
                "WS API",
                new PreparedRequest(),
                response,
                "",
                List.of(),
                false,
                true,
                PerformanceProtocol.WEBSOCKET,
                1000L,
                0L
        ), true);

        assertEquals(statsCollector.snapshot().totalRequests(), 0L, "取消的采样不计入统计");
        assertEquals(events.size(), 1, "取消的采样仍保留在结果列表");
    }

    @Test
    public void shouldRetainInterruptedStreamDetailsInEfficientModeWhenFailed() {
        PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
        List<PerformanceSampleEvent> events = new ArrayList<>();
        HttpResponse response = new HttpResponse();
        response.headers = new LinkedHashMap<>();
        response.code = 101;
        response.costMs = 100;
        response.endTime = 1100;
        response.addHeader("X-Easy-WS-Sent-Count", List.of("1"));

        PerformanceResultCollector collector = new PerformanceResultCollector(List.of(
                new PerformanceStatsCollectorListener(statsCollector),
                events::add
        ));

        collector.collect(new PerformanceRequestExecutionResult(
                "api-ws",
                "WS API",
                new PreparedRequest(),
                response,
                "",
                List.of(),
                true,
                true,
                PerformanceProtocol.WEBSOCKET,
                1000L,
                0L
        ), true);

        assertEquals(statsCollector.snapshot().totalRequests(), 0L, "取消的采样不计入统计");
        assertEquals(events.size(), 1, "取消的采样仍保留在结果列表");
    }

    @Test
    public void shouldRecordInterruptedHttpResultWithoutResponse() {
        PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
        List<PerformanceSampleEvent> events = new ArrayList<>();
        PerformanceResultCollector collector = new PerformanceResultCollector(List.of(
                new PerformanceStatsCollectorListener(statsCollector),
                events::add
        ));

        collector.collect(new PerformanceRequestExecutionResult(
                "api-http",
                "HTTP API",
                new PreparedRequest(),
                null,
                "Client stopped HTTP request before completion",
                List.of(),
                false,
                true,
                PerformanceProtocol.HTTP,
                1000L,
                2500L
        ), false);

        PerformanceStatsSnapshot snapshot = statsCollector.snapshot();
        assertEquals(snapshot.totalRequests(), 0L, "取消的采样不计入统计");
        assertEquals(snapshot.summaries().size(), 0);
        assertEquals(events.size(), 1, "取消的采样仍保留在结果列表");
        assertEquals(events.get(0).getSampleResult().getElapsedTimeMs(), 2500L);
        assertFalse(events.get(0).getSampleResult().isSuccessful());
    }

    private static PerformanceRequestExecutionResult successfulHttpResult() {
        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.costMs = 10;
        response.endTime = 110;
        return new PerformanceRequestExecutionResult(
                "api",
                "API",
                new PreparedRequest(),
                response,
                "",
                List.of(),
                false,
                false,
                PerformanceProtocol.HTTP,
                100,
                10
        );
    }

    private static boolean hasConstructorParameter(Class<?> type, Class<?> parameterType) {
        return java.util.Arrays.stream(type.getConstructors())
                .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(parameterType::equals);
    }
}
