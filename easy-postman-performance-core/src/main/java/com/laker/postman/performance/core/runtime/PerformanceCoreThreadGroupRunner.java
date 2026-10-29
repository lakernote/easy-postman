package com.laker.postman.performance.core.runtime;

import com.laker.postman.performance.core.plan.PerformanceTestPlan;
import com.laker.postman.performance.core.plan.PerformanceThreadGroupPlan;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Slf4j
public final class PerformanceCoreThreadGroupRunner<C> {

    @FunctionalInterface
    public interface IterationContextFactory<C> {
        C create(PerformanceThreadGroupPlan groupPlan, int iterationCount);
    }

    @FunctionalInterface
    public interface IterationExecutor<C> {
        void executeIteration(PerformanceThreadGroupPlan groupPlan, C iterationContext);
    }

    private final BooleanSupplier runningSupplier;
    private final LongSupplier startTimeSupplier;
    private final Runnable cancellationAction;
    private final PerformanceVirtualUserCoordinator virtualUsers;
    private final IterationContextFactory<C> iterationContextFactory;
    private final IterationExecutor<C> iterationExecutor;
    private final Supplier<PerformanceCoreResultSink> resultSinkSupplier;
    private final AtomicLong progressSequence = new AtomicLong(0L);
    private final AtomicBoolean drainTimedOut = new AtomicBoolean(false);

    public PerformanceCoreThreadGroupRunner(BooleanSupplier runningSupplier,
                                            LongSupplier startTimeSupplier,
                                            Runnable cancellationAction,
                                            PerformanceVirtualUserCoordinator virtualUsers,
                                            IterationContextFactory<C> iterationContextFactory,
                                            IterationExecutor<C> iterationExecutor,
                                            Supplier<PerformanceCoreResultSink> resultSinkSupplier) {
        BooleanSupplier resolvedRunningSupplier = runningSupplier == null ? () -> false : runningSupplier;
        this.runningSupplier = () -> resolvedRunningSupplier.getAsBoolean() && !drainTimedOut.get();
        this.startTimeSupplier = startTimeSupplier == null ? System::currentTimeMillis : startTimeSupplier;
        this.cancellationAction = cancellationAction == null ? () -> {
        } : cancellationAction;
        this.virtualUsers = virtualUsers == null ? new PerformanceVirtualUserCoordinator() : virtualUsers;
        this.iterationContextFactory = iterationContextFactory == null ? (groupPlan, iterationCount) -> null : iterationContextFactory;
        this.iterationExecutor = iterationExecutor == null ? (groupPlan, iterationContext) -> {
        } : iterationExecutor;
        this.resultSinkSupplier = resultSinkSupplier == null ? () -> PerformanceCoreResultSink.NOOP : resultSinkSupplier;
    }

    public void run(PerformanceTestPlan plan, int totalThreads) {
        drainTimedOut.set(false);
        virtualUsers.startAcceptingSamples();
        if (!runningSupplier.getAsBoolean() || plan == null) {
            return;
        }
        progressSequence.set(0L);

        List<Thread> threadGroupThreads = new ArrayList<>();
        for (PerformanceThreadGroupPlan threadGroup : plan.getThreadGroups()) {
            Thread thread = PerformanceThreadFactory.newDaemonThread(
                    "PerformanceThreadGroup",
                    () -> runThreadGroup(threadGroup, totalThreads)
            );
            threadGroupThreads.add(thread);
            thread.start();
        }
        joinThreadGroupThreads(threadGroupThreads, cancellationAction);
    }

    private void runThreadGroup(PerformanceThreadGroupPlan groupPlan, int totalThreads) {
        if (groupPlan == null) {
            return;
        }
        ThreadGroupData threadGroupData = groupPlan.getThreadGroupData();
        if (threadGroupData == null) {
            threadGroupData = new ThreadGroupData();
        }
        threadGroupData.normalize();
        AtomicInteger groupVirtualUserCounter = new AtomicInteger(0);
        BiConsumer<Integer, Integer> progressUpdater = this::publishProgress;
        switch (threadGroupData.threadMode) {
            case FIXED -> runFixedThreads(groupPlan, threadGroupData, progressUpdater, totalThreads, groupVirtualUserCounter);
            case RAMP_UP -> runRampUpThreads(groupPlan, threadGroupData, progressUpdater, totalThreads, groupVirtualUserCounter);
            case SPIKE -> runSpikeThreads(groupPlan, threadGroupData, progressUpdater, totalThreads, groupVirtualUserCounter);
            case STAIRS -> runStairsThreads(groupPlan, threadGroupData, progressUpdater, totalThreads, groupVirtualUserCounter);
        }
    }

    private void runFixedThreads(PerformanceThreadGroupPlan groupPlan,
                                 ThreadGroupData tg,
                                 BiConsumer<Integer, Integer> progressUpdater,
                                 int totalThreads,
                                 AtomicInteger groupVirtualUserCounter) {
        int numThreads = tg.numThreads;
        int loops = tg.loops;
        boolean useTime = tg.useTime;
        int durationSeconds = tg.duration;

        ExecutorService executor = Executors.newFixedThreadPool(
                numThreads,
                PerformanceThreadFactory.daemonFactory("PerformanceFixedWorker")
        );
        long threadGroupStartTime = System.currentTimeMillis();
        long endTime = useTime ? (threadGroupStartTime + (durationSeconds * 1000L)) : Long.MAX_VALUE;

        for (int i = 0; i < numThreads; i++) {
            if (!runningSupplier.getAsBoolean()) {
                executor.shutdownNow();
                return;
            }
            virtualUsers.submit(executor, progressUpdater, totalThreads, groupVirtualUserCounter::getAndIncrement,
                    virtualUserScopeFactory(groupPlan), () -> virtualUsers.runWithinLoadWindow(endTime, () -> {
                        if (useTime) {
                            while (System.currentTimeMillis() < endTime && runningSupplier.getAsBoolean()) {
                                runTaskIteration(groupPlan, 0);
                            }
                        } else {
                            runTask(groupPlan, loops);
                        }
                    }));
        }
        executor.shutdown();
        try {
            boolean terminated;
            if (useTime) {
                terminated = executor.awaitTermination(
                        (long) durationSeconds + tg.maxInFlightWaitSeconds,
                        TimeUnit.SECONDS
                );
            } else {
                terminated = awaitFixedLoopWorkers(executor);
            }

            if (!terminated || !runningSupplier.getAsBoolean()) {
                log.warn("线程池未能在预期时间内完成，强制关闭剩余线程");
                markDrainTimedOut(terminated);
                List<Runnable> pendingTasks = executor.shutdownNow();
                log.debug("已取消 {} 个待执行任务", pendingTasks.size());
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    log.warn("部分线程在强制关闭后仍未终止，这是正常的，线程会在网络操作完成后自动退出");
                } else {
                    log.info("所有线程已成功终止");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            if (runningSupplier.getAsBoolean()) {
                publishError(PerformanceRunError.builder()
                        .message(exception.getMessage())
                        .cause(exception)
                        .build());
                log.error(exception.getMessage(), exception);
            } else {
                log.debug("固定线程模式已停止");
            }
        }
    }

    private boolean awaitFixedLoopWorkers(ExecutorService executor) throws InterruptedException {
        while (runningSupplier.getAsBoolean()) {
            if (executor.awaitTermination(250, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return executor.isTerminated();
    }

    private void runRampUpThreads(PerformanceThreadGroupPlan groupPlan,
                                  ThreadGroupData tg,
                                  BiConsumer<Integer, Integer> progressUpdater,
                                  int totalThreads,
                                  AtomicInteger groupVirtualUserCounter) {
        int startThreads = tg.rampUpStartThreads;
        int endThreads = tg.rampUpEndThreads;
        int rampUpTime = tg.rampUpTime;
        int totalDuration = tg.rampUpDuration;
        double threadsPerSecond = (double) (endThreads - startThreads) / rampUpTime;

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                1,
                PerformanceThreadFactory.daemonFactory("PerformanceRampScheduler")
        );
        ExecutorService executor = Executors.newCachedThreadPool(
                PerformanceThreadFactory.daemonFactory("PerformanceRampWorker")
        );
        AtomicInteger activeWorkerThreads = new AtomicInteger(0);

        scheduler.scheduleAtFixedRate(() -> {
            if (!runningSupplier.getAsBoolean()) {
                scheduler.shutdownNow();
                executor.shutdownNow();
                return;
            }

            int currentSecond = (int) (System.currentTimeMillis() - startTimeSupplier.getAsLong()) / 1000;
            if (currentSecond > totalDuration) {
                scheduler.shutdown();
                return;
            }

            if (currentSecond <= rampUpTime) {
                int targetThreads = startThreads + (int) (threadsPerSecond * currentSecond);
                targetThreads = Math.min(targetThreads, endThreads);

                while (runningSupplier.getAsBoolean()) {
                    int current = activeWorkerThreads.get();
                    if (current >= targetThreads) {
                        break;
                    }
                    if (!activeWorkerThreads.compareAndSet(current, current + 1)) {
                        continue;
                    }
                    virtualUsers.submit(executor, progressUpdater, totalThreads, groupVirtualUserCounter::getAndIncrement,
                            virtualUserScopeFactory(groupPlan), () -> virtualUsers.runWithinLoadWindow(
                                    startTimeSupplier.getAsLong() + totalDuration * 1000L,
                                    () -> {
                                        try {
                                            while (runningSupplier.getAsBoolean()
                                                    && System.currentTimeMillis() - startTimeSupplier.getAsLong()
                                                    < totalDuration * 1000L) {
                                                runTaskIteration(groupPlan, 0);
                                            }
                                        } finally {
                                            activeWorkerThreads.decrementAndGet();
                                        }
                                    }));
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        try {
            boolean schedulerTerminated = scheduler.awaitTermination(totalDuration + 10L, TimeUnit.SECONDS);
            if (!schedulerTerminated || !runningSupplier.getAsBoolean()) {
                log.warn("递增模式调度器未能正常终止，强制关闭");
                scheduler.shutdownNow();
            }

            executor.shutdown();
            boolean executorTerminated = executor.awaitTermination(tg.maxInFlightWaitSeconds, TimeUnit.SECONDS);
            if (!executorTerminated || !runningSupplier.getAsBoolean()) {
                log.warn("递增模式执行器未能正常终止，强制关闭");
                markDrainTimedOut(executorTerminated);
                executor.shutdownNow();
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    log.warn("递增模式部分线程在强制关闭后仍未终止");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            executor.shutdownNow();
            log.error("递增线程执行中断", e);
        }
    }

    private void runSpikeThreads(PerformanceThreadGroupPlan groupPlan,
                                 ThreadGroupData tg,
                                 BiConsumer<Integer, Integer> progressUpdater,
                                 int totalThreads,
                                 AtomicInteger groupVirtualUserCounter) {
        int minThreads = tg.spikeMinThreads;
        int maxThreads = tg.spikeMaxThreads;
        int rampUpTime = tg.spikeRampUpTime;
        int holdTime = tg.spikeHoldTime;
        int rampDownTime = tg.spikeRampDownTime;
        int totalTime = tg.spikeDuration;

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                1,
                PerformanceThreadFactory.daemonFactory("PerformanceSpikeScheduler")
        );
        AtomicInteger activeWorkerThreads = new AtomicInteger(0);
        ConcurrentHashMap<Thread, Long> threadEndTimes = new ConcurrentHashMap<>();

        SpikePhaseDurations phases = calculateSpikePhaseDurations(rampUpTime, holdTime, rampDownTime, totalTime);
        int adjustedRampUpTime = phases.rampUpSeconds();
        int adjustedHoldTime = phases.holdSeconds();
        int adjustedRampDownTime = phases.rampDownSeconds();

        for (int i = 0; i < minThreads; i++) {
            if (!runningSupplier.getAsBoolean()) {
                return;
            }
            activeWorkerThreads.incrementAndGet();
            startWindowedVirtualUser(
                    "PerformanceSpikeWorker",
                    groupPlan,
                    activeWorkerThreads,
                    totalTime,
                    progressUpdater,
                    totalThreads,
                    groupVirtualUserCounter,
                    threadEndTimes
            );
        }

        scheduler.scheduleAtFixedRate(() -> {
            if (!runningSupplier.getAsBoolean()) {
                scheduler.shutdownNow();
                return;
            }

            long elapsedSeconds = (System.currentTimeMillis() - startTimeSupplier.getAsLong()) / 1000;
            if (elapsedSeconds >= totalTime) {
                scheduler.shutdown();
                return;
            }

            long now = System.currentTimeMillis();
            int targetThreads;

            if (adjustedRampUpTime > 0 && elapsedSeconds < adjustedRampUpTime) {
                double progress = (double) elapsedSeconds / adjustedRampUpTime;
                targetThreads = minThreads + (int) (progress * (maxThreads - minThreads));
                adjustSpikeThreadCount(groupPlan, tg, activeWorkerThreads, targetThreads, totalTime, progressUpdater, totalThreads,
                        groupVirtualUserCounter, threadEndTimes);
            } else if (adjustedHoldTime > 0 && elapsedSeconds < adjustedRampUpTime + adjustedHoldTime) {
                targetThreads = maxThreads;
                adjustSpikeThreadCount(groupPlan, tg, activeWorkerThreads, targetThreads, totalTime, progressUpdater, totalThreads,
                        groupVirtualUserCounter, threadEndTimes);
            } else {
                double progress = adjustedRampDownTime <= 0
                        ? 1.0
                        : (double) (elapsedSeconds - adjustedRampUpTime - adjustedHoldTime) / adjustedRampDownTime;
                targetThreads = maxThreads - (int) (progress * (maxThreads - minThreads));
                targetThreads = Math.max(targetThreads, minThreads);

                int threadsToRemove = activeWorkerThreads.get() - targetThreads;
                if (threadsToRemove > 0) {
                    threadEndTimes.keySet().stream()
                            .filter(t -> t.isAlive() && isOpenEndedWorker(threadEndTimes, t))
                            .limit(threadsToRemove)
                            .forEach(t -> threadEndTimes.put(t, now + 500));
                }
                adjustSpikeThreadCount(groupPlan, tg, activeWorkerThreads, targetThreads, totalTime, progressUpdater, totalThreads,
                        groupVirtualUserCounter, threadEndTimes);
            }

            updateProgress(progressUpdater, totalThreads);
        }, 1, 1, TimeUnit.SECONDS);

        try {
            boolean schedulerTerminated = scheduler.awaitTermination(totalTime + 10L, TimeUnit.SECONDS);
            if (!schedulerTerminated || !runningSupplier.getAsBoolean()) {
                log.warn("尖刺模式调度器未能正常终止，强制关闭");
                scheduler.shutdownNow();
            }

            awaitWindowedWorkers("尖刺模式", threadEndTimes.keySet(), tg.maxInFlightWaitSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            for (Thread thread : threadEndTimes.keySet()) {
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            }
            log.error("尖刺模式执行中断", e);
        }
    }

    private void runStairsThreads(PerformanceThreadGroupPlan groupPlan,
                                  ThreadGroupData tg,
                                  BiConsumer<Integer, Integer> progressUpdater,
                                  int totalThreads,
                                  AtomicInteger groupVirtualUserCounter) {
        int startThreads = tg.stairsStartThreads;
        int endThreads = tg.stairsEndThreads;
        int step = tg.stairsStep;
        int holdTime = tg.stairsHoldTime;
        int totalTime = tg.stairsDuration;

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                1,
                PerformanceThreadFactory.daemonFactory("PerformanceStairsScheduler")
        );
        AtomicInteger activeWorkerThreads = new AtomicInteger(0);
        ConcurrentHashMap<Thread, Long> threadEndTimes = new ConcurrentHashMap<>();
        int totalSteps = calculateStairsTotalSteps(startThreads, endThreads, step);
        AtomicInteger currentStair = new AtomicInteger(0);
        AtomicLong lastStairChangeTime = new AtomicLong(System.currentTimeMillis());

        for (int i = 0; i < startThreads; i++) {
            if (!runningSupplier.getAsBoolean()) {
                return;
            }
            activeWorkerThreads.incrementAndGet();
            startWindowedVirtualUser(
                    "PerformanceStairsWorker",
                    groupPlan,
                    activeWorkerThreads,
                    totalTime,
                    progressUpdater,
                    totalThreads,
                    groupVirtualUserCounter,
                    threadEndTimes
            );
        }

        scheduler.scheduleAtFixedRate(() -> {
            if (!runningSupplier.getAsBoolean()) {
                scheduler.shutdownNow();
                return;
            }

            long elapsedSeconds = (System.currentTimeMillis() - startTimeSupplier.getAsLong()) / 1000;
            if (elapsedSeconds >= totalTime) {
                scheduler.shutdown();
                return;
            }

            long now = System.currentTimeMillis();
            long timeSinceLastChange = now - lastStairChangeTime.get();
            int stair = currentStair.get();

            if (timeSinceLastChange >= holdTime * 1000L && stair < totalSteps) {
                stair = currentStair.incrementAndGet();
                lastStairChangeTime.set(now);
            }

            int targetThreads = startThreads;
            if (stair > 0 && stair <= totalSteps) {
                targetThreads = startThreads + stair * step;
                targetThreads = Math.min(targetThreads, endThreads);
            }

            adjustStairsThreadCount(groupPlan, activeWorkerThreads, targetThreads, totalTime, progressUpdater, totalThreads,
                    groupVirtualUserCounter, threadEndTimes);
            updateProgress(progressUpdater, totalThreads);
        }, 1, 1, TimeUnit.SECONDS);

        try {
            boolean schedulerTerminated = scheduler.awaitTermination(totalTime + 10L, TimeUnit.SECONDS);
            if (!schedulerTerminated || !runningSupplier.getAsBoolean()) {
                log.warn("阶梯模式调度器未能正常终止，强制关闭");
                scheduler.shutdownNow();
            }

            awaitWindowedWorkers("阶梯模式", threadEndTimes.keySet(), tg.maxInFlightWaitSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            for (Thread thread : threadEndTimes.keySet()) {
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            }
            log.error("阶梯模式执行中断", e);
        }
    }

    public void adjustSpikeThreadCount(PerformanceThreadGroupPlan groupPlan,
                                       ThreadGroupData tg,
                                       AtomicInteger activeWorkerThreads,
                                       int targetThreads,
                                       int totalTime,
                                       BiConsumer<Integer, Integer> progressUpdater,
                                       int totalThreads,
                                       ConcurrentHashMap<Thread, Long> threadEndTimes) {
        adjustSpikeThreadCount(groupPlan, tg, activeWorkerThreads, targetThreads, totalTime, progressUpdater, totalThreads,
                new AtomicInteger(0), threadEndTimes);
    }

    public void adjustSpikeThreadCount(PerformanceThreadGroupPlan groupPlan,
                                       ThreadGroupData tg,
                                       AtomicInteger activeWorkerThreads,
                                       int targetThreads,
                                       int totalTime,
                                       BiConsumer<Integer, Integer> progressUpdater,
                                       int totalThreads,
                                       AtomicInteger groupVirtualUserCounter,
                                       ConcurrentHashMap<Thread, Long> threadEndTimes) {
        int current = activeWorkerThreads.get();

        if (current < targetThreads) {
            int threadsToAdd = targetThreads - current;
            for (int i = 0; i < threadsToAdd; i++) {
                if (!runningSupplier.getAsBoolean()) {
                    return;
                }

                activeWorkerThreads.incrementAndGet();
                startWindowedVirtualUser(
                        "PerformanceSpikeWorker",
                        groupPlan,
                        activeWorkerThreads,
                        totalTime,
                        progressUpdater,
                        totalThreads,
                        groupVirtualUserCounter,
                        threadEndTimes
                );
            }
        } else if (current > targetThreads) {
            int threadsToRemove = current - targetThreads;
            long now = System.currentTimeMillis();
            List<Thread> availableThreads = threadEndTimes.keySet().stream()
                    .filter(t -> t.isAlive() && isOpenEndedWorker(threadEndTimes, t))
                    .limit(threadsToRemove)
                    .toList();

            if (!availableThreads.isEmpty()) {
                SpikePhaseDurations phases = calculateSpikePhaseDurations(
                        tg.spikeRampUpTime,
                        tg.spikeHoldTime,
                        tg.spikeRampDownTime,
                        totalTime
                );
                int adjustedRampDownTime = Math.max(phases.rampDownSeconds(), 1);

                long elapsedSeconds = (now - startTimeSupplier.getAsLong()) / 1000;
                long rampDownStartTime = (long) phases.rampUpSeconds() + phases.holdSeconds();
                long timeLeftInRampDown = Math.max(1, adjustedRampDownTime - (elapsedSeconds - rampDownStartTime));

                for (int i = 0; i < availableThreads.size(); i++) {
                    Thread thread = availableThreads.get(i);
                    long delayMs = now + (long) (i + 1) * timeLeftInRampDown * 1000 / (availableThreads.size() + 1);
                    threadEndTimes.put(thread, delayMs);
                }
            }
        }
    }

    private void adjustStairsThreadCount(PerformanceThreadGroupPlan groupPlan,
                                         AtomicInteger activeWorkerThreads,
                                         int targetThreads,
                                         int totalTime,
                                         BiConsumer<Integer, Integer> progressUpdater,
                                         int totalThreads,
                                         AtomicInteger groupVirtualUserCounter,
                                         ConcurrentHashMap<Thread, Long> threadEndTimes) {
        int current = activeWorkerThreads.get();
        if (current >= targetThreads) {
            return;
        }

        int threadsToAdd = targetThreads - current;
        for (int i = 0; i < threadsToAdd; i++) {
            if (!runningSupplier.getAsBoolean()) {
                return;
            }

            activeWorkerThreads.incrementAndGet();
            startWindowedVirtualUser(
                    "PerformanceStairsWorker",
                    groupPlan,
                    activeWorkerThreads,
                    totalTime,
                    progressUpdater,
                    totalThreads,
                    groupVirtualUserCounter,
                    threadEndTimes
            );
        }
    }

    private void startWindowedVirtualUser(String threadNamePrefix,
                                          PerformanceThreadGroupPlan groupPlan,
                                          AtomicInteger activeWorkerThreads,
                                          int totalTime,
                                          BiConsumer<Integer, Integer> progressUpdater,
                                          int totalThreads,
                                          AtomicInteger groupVirtualUserCounter,
                                          ConcurrentHashMap<Thread, Long> threadEndTimes) {
        Thread thread = virtualUsers.newThread(threadNamePrefix, progressUpdater, totalThreads,
                groupVirtualUserCounter::getAndIncrement, virtualUserScopeFactory(groupPlan), () ->
                        virtualUsers.runWithinLoadWindow(
                                startTimeSupplier.getAsLong() + totalTime * 1000L,
                                () -> {
                                    try {
                                        Thread currentThread = Thread.currentThread();
                                        while (runningSupplier.getAsBoolean()
                                                && System.currentTimeMillis() - startTimeSupplier.getAsLong()
                                                < totalTime * 1000L
                                                && System.currentTimeMillis()
                                                < threadEndTimes.getOrDefault(currentThread, Long.MAX_VALUE)) {
                                            runTaskIteration(groupPlan, 0);
                                        }
                                    } finally {
                                        activeWorkerThreads.decrementAndGet();
                                        threadEndTimes.remove(Thread.currentThread());
                                    }
                                }));
        threadEndTimes.put(thread, Long.MAX_VALUE);
        thread.start();
    }

    private static IntFunction<String> virtualUserScopeFactory(PerformanceThreadGroupPlan groupPlan) {
        int groupIdentity = groupPlan == null ? 0 : System.identityHashCode(groupPlan);
        int offset = groupPlan == null ? 0 : groupPlan.getVirtualUserIndexOffset();
        return virtualUserIndex -> "tg:" + groupIdentity + ":offset:" + offset + ":vu:" + virtualUserIndex;
    }

    public static int calculateStairsTotalSteps(int startThreads, int endThreads, int step) {
        int threadRange = Math.max(0, endThreads - startThreads);
        int safeStep = Math.max(1, step);
        return Math.max(1, (threadRange + safeStep - 1) / safeStep);
    }

    record SpikePhaseDurations(int rampUpSeconds, int holdSeconds, int rampDownSeconds) {
    }

    static SpikePhaseDurations calculateSpikePhaseDurations(int rampUpTime,
                                                            int holdTime,
                                                            int rampDownTime,
                                                            int totalTime) {
        int safeTotalTime = Math.max(1, totalTime);
        int[] weights = {
                Math.max(0, rampUpTime),
                Math.max(0, holdTime),
                Math.max(0, rampDownTime)
        };
        int positivePhaseCount = 0;
        long totalWeight = 0L;
        for (int weight : weights) {
            if (weight > 0) {
                positivePhaseCount++;
                totalWeight += weight;
            }
        }
        if (positivePhaseCount == 0) {
            return new SpikePhaseDurations(0, 0, safeTotalTime);
        }

        int[] seconds = new int[weights.length];
        if (safeTotalTime < positivePhaseCount) {
            for (int i = 0; i < safeTotalTime; i++) {
                seconds[largestUnassignedWeightIndex(weights, seconds)] = 1;
            }
            return new SpikePhaseDurations(seconds[0], seconds[1], seconds[2]);
        }

        int remainingSeconds = safeTotalTime;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] > 0) {
                seconds[i] = 1;
                remainingSeconds--;
            }
        }
        if (remainingSeconds <= 0) {
            return new SpikePhaseDurations(seconds[0], seconds[1], seconds[2]);
        }

        long[] remainders = new long[weights.length];
        int assignedSeconds = 0;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) {
                continue;
            }
            long scaledSeconds = (long) remainingSeconds * weights[i];
            int wholeSeconds = (int) (scaledSeconds / totalWeight);
            seconds[i] += wholeSeconds;
            assignedSeconds += wholeSeconds;
            remainders[i] = scaledSeconds % totalWeight;
        }
        while (assignedSeconds < remainingSeconds) {
            int index = largestRemainderIndex(remainders, weights);
            seconds[index]++;
            remainders[index] = -1L;
            assignedSeconds++;
        }
        return new SpikePhaseDurations(seconds[0], seconds[1], seconds[2]);
    }

    private static int largestUnassignedWeightIndex(int[] weights, int[] seconds) {
        int selectedIndex = 0;
        int selectedWeight = Integer.MIN_VALUE;
        for (int i = 0; i < weights.length; i++) {
            if (seconds[i] == 0 && weights[i] > selectedWeight) {
                selectedIndex = i;
                selectedWeight = weights[i];
            }
        }
        return selectedIndex;
    }

    private static int largestRemainderIndex(long[] remainders, int[] weights) {
        int selectedIndex = 0;
        long selectedRemainder = Long.MIN_VALUE;
        int selectedWeight = Integer.MIN_VALUE;
        for (int i = 0; i < remainders.length; i++) {
            if (weights[i] <= 0) {
                continue;
            }
            if (remainders[i] > selectedRemainder
                    || remainders[i] == selectedRemainder && weights[i] > selectedWeight) {
                selectedIndex = i;
                selectedRemainder = remainders[i];
                selectedWeight = weights[i];
            }
        }
        return selectedIndex;
    }

    private static boolean isOpenEndedWorker(ConcurrentHashMap<Thread, Long> threadEndTimes, Thread thread) {
        return Long.valueOf(Long.MAX_VALUE).equals(threadEndTimes.get(thread));
    }

    private void awaitWindowedWorkers(String modeName,
                                      Iterable<Thread> workers,
                                      int maxWaitSeconds) throws InterruptedException {
        List<Thread> snapshot = new ArrayList<>();
        workers.forEach(snapshot::add);
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(maxWaitSeconds);

        for (Thread worker : snapshot) {
            while (worker.isAlive() && runningSupplier.getAsBoolean()) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    break;
                }
                worker.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            }
        }

        List<Thread> unfinished = snapshot.stream().filter(Thread::isAlive).toList();
        if (unfinished.isEmpty()) {
            return;
        }

        log.warn("{}仍有 {} 个请求未在 {} 秒内完成，将强制取消", modeName, unfinished.size(), maxWaitSeconds);
        markDrainTimedOut(false);
        unfinished.forEach(Thread::interrupt);
        long cleanupDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L);
        for (Thread worker : unfinished) {
            long remainingNanos = cleanupDeadlineNanos - System.nanoTime();
            if (worker.isAlive() && remainingNanos > 0L) {
                worker.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            }
        }
    }

    private void markDrainTimedOut(boolean terminated) {
        boolean timedOut = !terminated && runningSupplier.getAsBoolean();
        if (timedOut && drainTimedOut.compareAndSet(false, true)) {
            virtualUsers.stopAcceptingSamples();
            cancellationAction.run();
        }
    }

    private void runTaskIteration(PerformanceThreadGroupPlan groupPlan, int iterationCount) {
        C iterationContext = iterationContextFactory.create(groupPlan, iterationCount);
        iterationExecutor.executeIteration(groupPlan, iterationContext);
    }

    private void runTask(PerformanceThreadGroupPlan groupPlan, int loops) {
        for (int l = 0; l < loops && runningSupplier.getAsBoolean(); l++) {
            runTaskIteration(groupPlan, loops);
        }
    }

    private void updateProgress(BiConsumer<Integer, Integer> progressUpdater, int totalThreads) {
        progressUpdater.accept(virtualUsers.getActiveThreads(), totalThreads);
    }

    private void publishProgress(int activeThreads, int totalThreads) {
        try {
            currentResultSink().onProgress(PerformanceRunProgress.sequenced(
                    activeThreads,
                    totalThreads,
                    progressSequence.incrementAndGet()
            ));
        } catch (Exception e) {
            log.warn("压测进度事件监听器执行失败", e);
        }
    }

    private void publishError(PerformanceRunError error) {
        try {
            currentResultSink().onError(error);
        } catch (Exception e) {
            log.warn("压测错误事件监听器执行失败", e);
        }
    }

    private PerformanceCoreResultSink currentResultSink() {
        PerformanceCoreResultSink sink = resultSinkSupplier.get();
        return sink == null ? PerformanceCoreResultSink.NOOP : sink;
    }

    public static void joinThreadGroupThreads(List<Thread> threadGroupThreads, Runnable cancellationAction) {
        boolean interrupted = false;
        for (Thread thread : threadGroupThreads) {
            while (thread.isAlive()) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                    if (cancellationAction != null) {
                        cancellationAction.run();
                    }
                    for (Thread candidate : threadGroupThreads) {
                        if (candidate.isAlive()) {
                            candidate.interrupt();
                        }
                    }
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
