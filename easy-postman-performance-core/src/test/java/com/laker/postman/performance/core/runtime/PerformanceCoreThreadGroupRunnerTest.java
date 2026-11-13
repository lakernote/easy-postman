package com.laker.postman.performance.core.runtime;

import com.laker.postman.performance.core.plan.PerformanceTestPlan;
import com.laker.postman.performance.core.plan.PerformancePlanElement;
import com.laker.postman.performance.core.plan.PerformanceSampler;
import com.laker.postman.performance.core.plan.PerformanceThreadGroupPlan;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PerformanceCoreThreadGroupRunnerTest {

    @Test(timeOut = 3000)
    public void shouldRunFixedThreadGroupWithGenericIterationContext() {
        PerformanceVirtualUserCoordinator virtualUsers = new PerformanceVirtualUserCoordinator();
        List<String> executions = new CopyOnWriteArrayList<>();
        List<PerformanceRunProgress> progressEvents = new CopyOnWriteArrayList<>();
        PerformanceCoreThreadGroupRunner<String> runner = new PerformanceCoreThreadGroupRunner<>(
                () -> true,
                System::currentTimeMillis,
                () -> {
                },
                virtualUsers,
                (groupPlan, iterationCount) -> "ctx:" + groupPlan.getName() + ":" + iterationCount,
                (groupPlan, iterationContext) -> executions.add(iterationContext),
                () -> new PerformanceCoreResultSink() {
                    @Override
                    public void onProgress(PerformanceRunProgress progress) {
                        progressEvents.add(progress);
                    }
                }
        );

        runner.run(new PerformanceTestPlan(List.of(fixedGroup("group", 2))), 1);

        assertEquals(executions, List.of("ctx:group:2", "ctx:group:2"));
        assertEquals(virtualUsers.getActiveThreads(), 0);
        assertTrue(progressEvents.stream().anyMatch(progress ->
                progress.getActiveThreads() == 1 && progress.getTotalThreads() == 1));
        assertTrue(progressEvents.stream().allMatch(progress -> progress.getSequence() > 0L));
        assertEquals(progressEvents.get(progressEvents.size() - 1).getActiveThreads(), 0);
    }

    @Test(timeOut = 3000)
    public void shouldAssignDistinctVirtualUserScopesAcrossThreadGroups() {
        PerformanceVirtualUserCoordinator virtualUsers = new PerformanceVirtualUserCoordinator();
        List<String> scopes = new CopyOnWriteArrayList<>();
        PerformanceCoreThreadGroupRunner<String> runner = new PerformanceCoreThreadGroupRunner<>(
                () -> true,
                System::currentTimeMillis,
                () -> {
                },
                virtualUsers,
                (groupPlan, iterationCount) -> {
                    scopes.add(virtualUsers.currentVirtualUserScope());
                    return "ctx";
                },
                (groupPlan, iterationContext) -> {
                },
                noopSink()
        );

        runner.run(new PerformanceTestPlan(List.of(fixedGroup("first", 1), fixedGroup("second", 1))), 2);

        assertEquals(scopes.size(), 2);
        assertEquals(new HashSet<>(scopes).size(), 2);
        assertTrue(scopes.stream().allMatch(scope -> scope != null && scope.contains(":vu:0")));
    }

    @Test(timeOut = 4000)
    public void shouldLetInFlightIterationFinishAfterDuration() {
        List<String> executions = new CopyOnWriteArrayList<>();
        AtomicInteger cancellations = new AtomicInteger();
        PerformanceVirtualUserCoordinator virtualUsers = new PerformanceVirtualUserCoordinator();
        PerformanceCorePlanExecutor<String> planExecutor = new PerformanceCorePlanExecutor<>(
                virtualUsers::canStartNextSample,
                (sampler, context) -> {
                    executions.add(sampler.getName());
                    if ("first".equals(sampler.getName())) {
                        try {
                            Thread.sleep(1_200L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
        );
        PerformanceCoreThreadGroupRunner<String> runner = new PerformanceCoreThreadGroupRunner<>(
                () -> true,
                System::currentTimeMillis,
                cancellations::incrementAndGet,
                virtualUsers,
                (groupPlan, iterationCount) -> "ctx",
                planExecutor::executeIteration,
                noopSink()
        );

        runner.run(new PerformanceTestPlan(List.of(timedFixedGroup(
                "group",
                2,
                List.of(new RecordingSampler("first"), new RecordingSampler("second"))
        ))), 1);

        assertEquals(executions, List.of("first"));
        assertEquals(cancellations.get(), 0);
        assertEquals(virtualUsers.getActiveThreads(), 0);
    }

    @Test(timeOut = 5000)
    public void shouldCancelInFlightIterationAfterConfiguredWait() {
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch iterationStarted = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        PerformanceCoreThreadGroupRunner<String> runner = new PerformanceCoreThreadGroupRunner<>(
                () -> true,
                System::currentTimeMillis,
                cancellations::incrementAndGet,
                new PerformanceVirtualUserCoordinator(),
                (groupPlan, iterationCount) -> "ctx",
                (groupPlan, iterationContext) -> {
                    executions.incrementAndGet();
                    iterationStarted.countDown();
                    try {
                        neverReleased.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                noopSink()
        );
        long startNanos = System.nanoTime();

        runner.run(new PerformanceTestPlan(List.of(timedFixedGroup("group", 1))), 1);

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        assertEquals(iterationStarted.getCount(), 0L);
        assertEquals(cancellations.get(), 1);
        assertTrue(elapsedMs >= 1_800L, "must honor duration plus configured completion wait");
        assertTrue(elapsedMs < 4_500L, "must not retain the previous hard-coded five-second wait");

        neverReleased.countDown();
        runner.run(new PerformanceTestPlan(List.of(fixedGroup("next run", 1))), 1);
        assertEquals(executions.get(), 2, "a drain timeout must not prevent the next run");
    }

    @Test(timeOut = 5000)
    public void shouldStopAllThreadGroupsWhenOneDrainTimesOut() {
        AtomicBoolean longGroupFinished = new AtomicBoolean(false);
        AtomicBoolean cancellationObservedLongGroupFinished = new AtomicBoolean(false);
        CountDownLatch neverReleased = new CountDownLatch(1);
        PerformanceCoreThreadGroupRunner<String> runner = new PerformanceCoreThreadGroupRunner<>(
                () -> true,
                System::currentTimeMillis,
                () -> cancellationObservedLongGroupFinished.set(longGroupFinished.get()),
                new PerformanceVirtualUserCoordinator(),
                (groupPlan, iterationCount) -> "ctx",
                (groupPlan, iterationContext) -> {
                    try {
                        if ("short".equals(groupPlan.getName())) {
                            neverReleased.await();
                        } else {
                            Thread.sleep(2_200L);
                            longGroupFinished.set(true);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                noopSink()
        );

        runner.run(new PerformanceTestPlan(List.of(
                timedFixedGroup("short", 1),
                timedFixedGroup("long", 2)
        )), 2);

        assertTrue(longGroupFinished.get());
        assertFalse(cancellationObservedLongGroupFinished.get());
    }

    @Test(timeOut = 3000)
    public void adjustSpikeThreadCountShouldIgnoreStaleThreadEndEntries() throws Exception {
        PerformanceCoreThreadGroupRunner<String> runner = new PerformanceCoreThreadGroupRunner<>(
                () -> true,
                () -> 0L,
                () -> {
                },
                new PerformanceVirtualUserCoordinator(),
                (groupPlan, iterationCount) -> "ctx",
                (groupPlan, iterationContext) -> {
                },
                noopSink()
        );
        ThreadGroupData threadGroupData = new ThreadGroupData();
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        worker.start();

        ConcurrentHashMap<Thread, Long> staleThreadEndTimes = new ConcurrentHashMap<>() {
            @Override
            public Long get(Object key) {
                if (key == worker) {
                    return null;
                }
                return super.get(key);
            }
        };
        staleThreadEndTimes.put(worker, Long.MAX_VALUE);

        try {
            runner.adjustSpikeThreadCount(
                    null,
                    threadGroupData,
                    new AtomicInteger(1),
                    0,
                    1,
                    (BiConsumer<Integer, Integer>) (active, total) -> {
                    },
                    1,
                    staleThreadEndTimes
            );
        } finally {
            worker.interrupt();
            worker.join(1_000);
        }
    }

    @Test(timeOut = 3000)
    public void adjustSpikeThreadCountShouldNotRetireWorkersAlreadyScheduledToStop() throws Exception {
        PerformanceCoreThreadGroupRunner<String> runner = new PerformanceCoreThreadGroupRunner<>(
                () -> true,
                System::currentTimeMillis,
                () -> {
                },
                new PerformanceVirtualUserCoordinator(),
                (groupPlan, iterationCount) -> "ctx",
                (groupPlan, iterationContext) -> {
                },
                noopSink()
        );
        CountDownLatch release = new CountDownLatch(1);
        ConcurrentHashMap<Thread, Long> threadEndTimes = new ConcurrentHashMap<>();
        List<Thread> workers = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread worker = new Thread(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            worker.start();
            workers.add(worker);
            threadEndTimes.put(worker, Long.MAX_VALUE);
        }
        AtomicInteger activeWorkers = new AtomicInteger(workers.size());
        ThreadGroupData threadGroupData = new ThreadGroupData();
        BiConsumer<Integer, Integer> noProgress = (active, total) -> {
        };

        try {
            runner.adjustSpikeThreadCount(null, threadGroupData, activeWorkers, 8, 60, noProgress, 10, threadEndTimes);
            assertEquals(scheduledToStop(threadEndTimes), 2);

            // Retiring workers are still alive; the same target must not retire another batch.
            runner.adjustSpikeThreadCount(null, threadGroupData, activeWorkers, 8, 60, noProgress, 10, threadEndTimes);
            assertEquals(scheduledToStop(threadEndTimes), 2);

            runner.adjustSpikeThreadCount(null, threadGroupData, activeWorkers, 5, 60, noProgress, 10, threadEndTimes);
            assertEquals(scheduledToStop(threadEndTimes), 5);
            assertTrue(threadEndTimes.values().stream()
                    .filter(endTime -> endTime != Long.MAX_VALUE)
                    .allMatch(endTime -> endTime <= System.currentTimeMillis() + 1_000L));
        } finally {
            release.countDown();
            for (Thread worker : workers) {
                worker.join(1_000);
            }
        }
    }

    @Test(timeOut = 3000)
    public void iterationFailureShouldNotRetireVirtualUser() {
        AtomicInteger iterations = new AtomicInteger();
        PerformanceCoreThreadGroupRunner<String> runner = new PerformanceCoreThreadGroupRunner<>(
                () -> true,
                System::currentTimeMillis,
                () -> {
                },
                new PerformanceVirtualUserCoordinator(),
                (groupPlan, iterationCount) -> "ctx",
                (groupPlan, iterationContext) -> {
                    if (iterations.incrementAndGet() == 1) {
                        throw new IllegalStateException("broken iteration");
                    }
                },
                noopSink()
        );

        runner.run(new PerformanceTestPlan(List.of(fixedGroup("group", 3))), 1);

        assertEquals(iterations.get(), 3);
    }

    @Test
    public void iterationFailuresShouldBeReportedOncePerRun() {
        List<PerformanceRunError> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        PerformanceCoreResultSink sink = new PerformanceCoreResultSink() {
            @Override
            public void onError(PerformanceRunError error) {
                errors.add(error);
            }
        };
        PerformanceCoreThreadGroupRunner<String> runner = new PerformanceCoreThreadGroupRunner<>(
                () -> true,
                System::currentTimeMillis,
                () -> {
                },
                new PerformanceVirtualUserCoordinator(),
                (groupPlan, iterationCount) -> "ctx",
                (groupPlan, iterationContext) -> {
                    throw new IllegalStateException("broken iteration");
                },
                () -> sink
        );

        runner.run(new PerformanceTestPlan(List.of(fixedGroup("group", 3))), 1);
        assertEquals(errors.size(), 1);
        assertEquals(errors.get(0).getMessage(), "broken iteration");
        assertTrue(errors.get(0).isRecoverable());

        runner.run(new PerformanceTestPlan(List.of(fixedGroup("group", 2))), 1);
        assertEquals(errors.size(), 2);
    }

    private static long scheduledToStop(ConcurrentHashMap<Thread, Long> threadEndTimes) {
        return threadEndTimes.values().stream().filter(endTime -> endTime != Long.MAX_VALUE).count();
    }

    @Test
    public void spikePhaseDurationsShouldKeepPositiveConfiguredPhasesWhenPossible() {
        PerformanceCoreThreadGroupRunner.SpikePhaseDurations phases =
                PerformanceCoreThreadGroupRunner.calculateSpikePhaseDurations(20, 15, 20, 3);

        assertEquals(phases.rampUpSeconds(), 1);
        assertEquals(phases.holdSeconds(), 1);
        assertEquals(phases.rampDownSeconds(), 1);
    }

    @Test
    public void spikePhaseDurationsShouldFitShortDurationWithoutNegativeValues() {
        PerformanceCoreThreadGroupRunner.SpikePhaseDurations phases =
                PerformanceCoreThreadGroupRunner.calculateSpikePhaseDurations(20, 15, 20, 1);

        assertEquals(phases.rampUpSeconds() + phases.holdSeconds() + phases.rampDownSeconds(), 1);
        assertTrue(phases.rampUpSeconds() >= 0);
        assertTrue(phases.holdSeconds() >= 0);
        assertTrue(phases.rampDownSeconds() >= 0);
    }

    @Test
    public void fixedLoopModeShouldNotUseHardCodedOneHourTerminationCutoff() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/laker/postman/performance/core/runtime/PerformanceCoreThreadGroupRunner.java"
        ));

        assertFalse(source.contains("TimeUnit.HOURS"));
        assertFalse(source.contains("awaitTermination(1,"));
    }

    private static Supplier<PerformanceCoreResultSink> noopSink() {
        return () -> PerformanceCoreResultSink.NOOP;
    }

    private static PerformanceThreadGroupPlan fixedGroup(String name, int loops) {
        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.threadMode = ThreadGroupData.ThreadMode.FIXED;
        threadGroupData.numThreads = 1;
        threadGroupData.useTime = false;
        threadGroupData.loops = loops;
        return new PerformanceThreadGroupPlan(name, threadGroupData, List.of());
    }

    private static PerformanceThreadGroupPlan timedFixedGroup(String name, int maxInFlightWaitSeconds) {
        return timedFixedGroup(name, maxInFlightWaitSeconds, List.of());
    }

    private static PerformanceThreadGroupPlan timedFixedGroup(String name,
                                                              int maxInFlightWaitSeconds,
                                                              List<PerformancePlanElement> elements) {
        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.threadMode = ThreadGroupData.ThreadMode.FIXED;
        threadGroupData.numThreads = 1;
        threadGroupData.useTime = true;
        threadGroupData.duration = 1;
        threadGroupData.maxInFlightWaitSeconds = maxInFlightWaitSeconds;
        return new PerformanceThreadGroupPlan(name, threadGroupData, elements);
    }

    private record RecordingSampler(String name) implements PerformanceSampler {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public NodeType getType() {
            return NodeType.REQUEST;
        }

        @Override
        public List<PerformancePlanElement> getChildren() {
            return List.of();
        }

        @Override
        public boolean executesChildrenInSamplerOrder() {
            return false;
        }
    }
}
