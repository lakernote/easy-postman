package com.laker.postman.performance.core.model;

import org.testng.annotations.Test;

import java.lang.reflect.Modifier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PerformanceTrendWindowCollectorTest {

    @Test
    public void shouldDrainTrendDataFromDeltasAndThenResetWindow() {
        PerformanceTrendWindowCollector collector = new PerformanceTrendWindowCollector();
        collector.record(new RequestResult(1_000, 1_100, true, "search", PerformanceProtocol.HTTP));
        collector.record(new RequestResult(1_100, 1_400, false, "search", PerformanceProtocol.HTTP));

        PerformanceTrendSnapshot first = collector.drainWindowSnapshot(
                5,
                0,
                0,
                1_000,
                PerformanceRealtimeMetrics.Sample.empty()
        );
        PerformanceTrendSnapshot second = collector.drainWindowSnapshot(
                5,
                0,
                0,
                1_000,
                PerformanceRealtimeMetrics.Sample.empty()
        );

        assertEquals(first.http().samples(), 2);
        assertEquals(first.http().failures(), 1);
        assertEquals(first.http().sampleRate(), 2.0);
        assertEquals(first.http().avgDurationMs(), 200.0);
        assertEquals(first.http().maxDurationMs(), 300.0);
        assertEquals(first.overview().maxDurationMs(), 300.0);
        assertEquals(second.http().samples(), 0);
        assertTrue(Double.isNaN(second.http().maxDurationMs()));
        assertEquals(second.http().sampleRate(), 0.0);
        assertTrue(Double.isNaN(second.http().failurePercent()));
        assertTrue(Double.isNaN(second.http().avgDurationMs()));
    }

    @Test
    public void shouldSkipTrendWindowAggregationWhenDisabled() {
        PerformanceTrendWindowCollector collector = new PerformanceTrendWindowCollector();
        collector.setEnabled(false);
        collector.record(new RequestResult(1_000, 1_100, true, "search", PerformanceProtocol.HTTP));

        PerformanceTrendSnapshot disabledSnapshot = collector.drainWindowSnapshot(
                5,
                0,
                0,
                1_000,
                PerformanceRealtimeMetrics.Sample.empty()
        );

        assertEquals(disabledSnapshot.http().samples(), 0);

        collector.setEnabled(true);
        collector.record(new RequestResult(2_000, 2_200, true, "search", PerformanceProtocol.HTTP));
        PerformanceTrendSnapshot enabledSnapshot = collector.drainWindowSnapshot(
                5,
                0,
                0,
                1_000,
                PerformanceRealtimeMetrics.Sample.empty()
        );

        assertEquals(enabledSnapshot.http().samples(), 1);
    }

    @Test
    public void shouldUseRealtimeStreamStepRatesInsteadOfCompletedSessionTotals() {
        PerformanceTrendWindowCollector collector = new PerformanceTrendWindowCollector();
        RequestResult completedSession = new RequestResult(0, 10_000, true, "ws", PerformanceProtocol.WEBSOCKET);
        completedSession.sentMessages = 1_000;
        completedSession.receivedMessages = 1_000;
        collector.record(completedSession);

        PerformanceTrendSnapshot snapshot = collector.drainWindowSnapshot(
                1,
                1,
                0,
                1_000,
                PerformanceRealtimeMetrics.Sample.empty()
        );

        assertEquals(snapshot.webSocket().sentRate(), 0.0);
        assertEquals(snapshot.webSocket().receivedRate(), 0.0);
    }

    @Test
    public void recordShouldNotUseCollectorWideSynchronizedMethodLock() throws Exception {
        assertFalse(Modifier.isSynchronized(PerformanceTrendWindowCollector.class
                .getDeclaredMethod("record", RequestResult.class)
                .getModifiers()));
    }
}
