package com.laker.postman.performance.runtime;

import com.laker.postman.http.runtime.okhttp.HttpClientRuntimeConfig;
import com.laker.postman.model.Environment;
import com.laker.postman.model.Variable;
import com.laker.postman.performance.core.model.PerformanceStatsCollector;
import com.laker.postman.performance.core.model.PerformanceStatsSnapshot;
import com.laker.postman.performance.core.model.PerformanceReportSnapshot;
import com.laker.postman.performance.core.model.PerformanceTrendWindowCollector;
import com.laker.postman.performance.core.plan.PerformanceCorePlanDocumentCompiler;
import com.laker.postman.performance.core.plan.PerformanceTestPlan;
import com.laker.postman.performance.core.report.*;
import com.laker.postman.performance.core.run.*;
import com.laker.postman.performance.core.runtime.*;
import com.laker.postman.performance.core.worker.PerformanceWorkerAssignment;
import com.laker.postman.performance.core.worker.PerformanceWorkerExecutionPlanPartitioner;
import com.laker.postman.performance.execution.PerformanceExecutionConfig;
import com.laker.postman.performance.model.PerformanceStatsCollectorListener;
import com.laker.postman.performance.model.PerformanceTrendWindowCollectorListener;
import com.laker.postman.performance.plan.PerformanceCorePlanAdapter;
import com.laker.postman.performance.result.PerformanceMetricsSnapshotService;
import com.laker.postman.performance.result.PerformanceResultCollector;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.service.variable.RunScopedVariableContext;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PerformanceRunPlanExecutor {
    private static final long LIVE_REPORT_INTERVAL_MS = 1_000L;

    public PerformanceRunExecutionResult execute(Path planPath, PrintStream scriptOutput) throws Exception {
        return execute(planPath, scriptOutput, PerformanceRunReportListener.NOOP);
    }

    public PerformanceRunExecutionResult execute(Path planPath,
                                                 PrintStream scriptOutput,
                                                 PerformanceRunReportListener reportListener) throws Exception {
        if (planPath == null) {
            throw new IllegalArgumentException("--plan is required");
        }
        if (!Files.isRegularFile(planPath)) {
            throw new IllegalArgumentException("Plan file does not exist: " + planPath);
        }
        PerformanceRunPlan runPlan = new PerformanceRunPlanJsonStorage().load(planPath);
        if (runPlan == null) {
            throw new IllegalArgumentException("Plan file is empty: " + planPath);
        }

        Environment environment = toEnvironment(runPlan.getEnvironment());
        Environment globals = toGlobals(runPlan.getGlobals());
        try (RunScopedVariableContext ignored = RunScopedVariableContext.open(environment, globals)) {
            return executeLoadedPlan(
                    runPlan,
                    planPath.toString(),
                    null,
                    environment,
                    scriptOutput,
                    new PerformanceRunExecutionControl(),
                    reportListener
            );
        }
    }

    public PerformanceRunExecutionResult execute(PerformanceRunPlan runPlan,
                                                 String planPath,
                                                 PerformanceWorkerAssignment assignment,
                                                 PrintStream scriptOutput,
                                                 PerformanceRunExecutionControl control) throws InterruptedException {
        if (runPlan == null) {
            throw new IllegalArgumentException("Plan is required");
        }
        Environment environment = toEnvironment(runPlan.getEnvironment());
        Environment globals = toGlobals(runPlan.getGlobals());
        try (RunScopedVariableContext ignored = RunScopedVariableContext.open(environment, globals)) {
            return executeLoadedPlan(
                    runPlan,
                    planPath,
                    assignment,
                    environment,
                    scriptOutput,
                    control == null ? new PerformanceRunExecutionControl() : control,
                    PerformanceRunReportListener.NOOP
            );
        }
    }

    private PerformanceRunExecutionResult executeLoadedPlan(PerformanceRunPlan runPlan,
                                                            String planPath,
                                                            PerformanceWorkerAssignment assignment,
                                                            Environment environment,
                                                            PrintStream scriptOutput,
                                                            PerformanceRunExecutionControl control,
                                                            PerformanceRunReportListener reportListener) throws InterruptedException {
        PerformanceTestPlan corePlan = PerformanceCorePlanDocumentCompiler.compile(runPlan.getTestPlan());
        if (assignment != null) {
            corePlan = new PerformanceWorkerExecutionPlanPartitioner().apply(corePlan, assignment);
        }
        // headless 先复用 app 现有执行链，所有变量、脚本、断言和协议行为与 GUI 本机执行保持同源。
        PerformanceTestPlan appExecutablePlan = PerformanceCorePlanAdapter.toExecutablePlan(corePlan);

        AtomicBoolean running = new AtomicBoolean(false);
        PerformanceStatsCollector statsCollector = new PerformanceStatsCollector();
        PerformanceTrendWindowCollector trendWindowCollector = new PerformanceTrendWindowCollector();
        PerformanceRunDetailCollector detailCollector = new PerformanceRunDetailCollector(
                SettingManager::getPerformanceSlowRequestThreshold,
                SettingManager::getPerformanceResultRowLimit
        );
        control.bindStatsCollector(statsCollector);
        control.bindResultDetailsSupplier(detailCollector::snapshot);
        PerformanceResultCollector resultCollector = new PerformanceResultCollector(
                List.of(
                        new PerformanceStatsCollectorListener(statsCollector),
                        new PerformanceTrendWindowCollectorListener(trendWindowCollector),
                        detailCollector
                )
        );
        AtomicReference<PerformanceRunSummary> summaryRef = new AtomicReference<>();
        AtomicReference<PerformanceRunError> errorRef = new AtomicReference<>();
        PerformanceResultSink resultSink = new PerformanceResultSink() {
            @Override
            public void onError(PerformanceRunError error) {
                if (error != null) {
                    errorRef.accumulateAndGet(error, (current, next) ->
                            current == null || (current.isRecoverable() && !next.isRecoverable()) ? next : current);
                }
            }

            @Override
            public void onComplete(PerformanceRunSummary summary) {
                summaryRef.set(summary);
            }
        };

        PerformanceExecutionEngine executionEngine = new PerformanceExecutionEngine(
                () -> running.get() && control.isRunning(),
                executionConfig(runPlan.getSettings(), environment, scriptOutput),
                resultCollector,
                new PerformanceRunListener() {
                    @Override
                    public void onProgress(PerformanceRunProgress progress) {
                        if (progress != null) {
                            control.recordProgress(progress.getActiveThreads(), progress.getTotalThreads());
                        }
                    }
                },
                () -> httpClientConfig(runPlan.getSettings())
        );
        control.bindRealtimeMetrics(
                executionEngine::liveRealtimeMetrics,
                executionEngine::getActiveWebSockets,
                executionEngine::getActiveSseStreams
        );
        PerformanceMetricsSnapshotService metricsSnapshotService = new PerformanceMetricsSnapshotService(
                statsCollector,
                trendWindowCollector,
                control::getActiveUsers,
                executionEngine::getActiveWebSockets,
                executionEngine::getActiveSseStreams,
                () -> 1_000L,
                executionEngine::drainRealtimeMetricsWindow,
                executionEngine::liveRealtimeMetrics
        );
        metricsSnapshotService.resetTrendWindow(System.currentTimeMillis());
        control.bindTrendSnapshotSupplier(metricsSnapshotService::drainTrendWindowSnapshot);
        control.recordProgress(0, executionEngine.getTotalThreads(appExecutablePlan));
        PerformanceRunSession runSession = new PerformanceRunSession(
                () -> running.get() && control.isRunning(),
                value -> {
                    running.set(value);
                    control.setRunning(value);
                },
                executionEngine
        );
        PerformanceRunHandle handle = runSession.start(PerformanceRunRequest.builder()
                .plan(appExecutablePlan)
                .resultSink(resultSink)
                .build());
        control.bind(handle);
        Thread runThread = handle.threadOrNull();
        if (runThread == null) {
            throw new IllegalStateException("Performance run did not start");
        }
        long liveReportStartTimeMs = System.currentTimeMillis();
        ScheduledExecutorService reportExecutor = startLiveReportPublisher(
                reportListener,
                planPath,
                liveReportStartTimeMs,
                statsCollector,
                executionEngine
        );
        try {
            runThread.join();
        } finally {
            stopLiveReportPublisher(reportExecutor);
        }
        PerformanceStatsSnapshot stats = statsCollector.snapshot();
        PerformanceRunSummary summary = summaryRef.get();
        PerformanceRunError runError = errorRef.get();
        return toResult(planPath, stats, summary, runError);
    }

    private ScheduledExecutorService startLiveReportPublisher(PerformanceRunReportListener reportListener,
                                                               String planPath,
                                                               long startTimeMs,
                                                               PerformanceStatsCollector statsCollector,
                                                               PerformanceExecutionEngine executionEngine) {
        if (reportListener == null || reportListener == PerformanceRunReportListener.NOOP) {
            return null;
        }
        publishLiveReport(reportListener, planPath, startTimeMs, statsCollector, executionEngine);
        ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                PerformanceThreadFactory.daemonFactory("PerformanceCliReport")
        );
        executor.scheduleAtFixedRate(
                () -> publishLiveReport(reportListener, planPath, startTimeMs, statsCollector, executionEngine),
                LIVE_REPORT_INTERVAL_MS,
                LIVE_REPORT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        return executor;
    }

    private void publishLiveReport(PerformanceRunReportListener reportListener,
                                   String planPath,
                                   long startTimeMs,
                                   PerformanceStatsCollector statsCollector,
                                   PerformanceExecutionEngine executionEngine) {
        long now = System.currentTimeMillis();
        PerformanceJsonReport report = PerformanceJsonReportMapper.fromReportSnapshot(
                PerformanceJsonReportMetadata.builder()
                        .source("local")
                        .status(PerformanceRunStatus.RUNNING)
                        .planPath(planPath)
                        .startTimeMs(startTimeMs)
                        .endTimeMs(now)
                        .elapsedTimeMs(Math.max(0L, now - startTimeMs))
                        .build(),
                PerformanceReportSnapshot.of(
                        statsCollector.snapshot(),
                        executionEngine.liveRealtimeMetrics(now)
                )
        );
        try {
            reportListener.onReport(report);
        } catch (RuntimeException ex) {
            log.warn("Failed to publish live CLI performance report", ex);
        }
    }

    private void stopLiveReportPublisher(ScheduledExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private PerformanceExecutionConfig executionConfig(PerformanceRunSettings settings,
                                                       Environment environment,
                                                       PrintStream scriptOutput) {
        PerformanceRunSettings safeSettings = settings == null ? PerformanceRunSettings.defaults() : settings;
        return PerformanceExecutionConfig.fixed(
                safeSettings.isEfficientMode(),
                safeSettings.getResponseBodyPreviewLimitKb(),
                false,
                output -> {
                    if (scriptOutput != null && output != null && !output.isBlank()) {
                        scriptOutput.println(output);
                    }
                },
                () -> environment
        );
    }

    private HttpClientRuntimeConfig httpClientConfig(PerformanceRunSettings settings) {
        PerformanceRunSettings safeSettings = settings == null ? PerformanceRunSettings.defaults() : settings;
        return new HttpClientRuntimeConfig(
                safeSettings.getHttpMaxIdleConnections(),
                safeSettings.getHttpKeepAliveSeconds(),
                safeSettings.getHttpMaxRequests(),
                safeSettings.getHttpMaxRequestsPerHost()
        );
    }

    private PerformanceRunExecutionResult toResult(String planPath,
                                                   PerformanceStatsSnapshot stats,
                                                   PerformanceRunSummary summary,
                                                   PerformanceRunError runError) {
        long totalRequests = stats == null ? 0L : stats.totalRequests();
        long successRequests = stats == null ? 0L : stats.successRequests();
        long failedRequests = Math.max(0L, totalRequests - successRequests);
        Throwable summaryError = summary == null ? null : summary.getError();
        String errorMessage = runError == null ? null : runError.getMessage();
        if ((errorMessage == null || errorMessage.isBlank()) && summaryError != null) {
            errorMessage = summaryError.getMessage();
        }
        PerformanceJsonReportSummary reportSummary = PerformanceJsonReportSummary.builder()
                .totalRequests(totalRequests)
                .successRequests(successRequests)
                .failedRequests(failedRequests)
                .build();
        errorMessage = PerformanceJsonReportStatusResolver.withFailureSummary(errorMessage, reportSummary);
        boolean stopped = summary != null && summary.isStopped();
        String status = PerformanceJsonReportStatusResolver.resolve(
                PerformanceRunExecutionResult.STATUS_SUCCESS,
                stopped,
                errorMessage,
                reportSummary
        );
        PerformanceJsonReport report = PerformanceJsonReportMapper.fromStatsSnapshot(
                PerformanceJsonReportMetadata.builder()
                        .source("local")
                        .status(status)
                        .planPath(planPath)
                        .startTimeMs(summary == null ? 0L : summary.getStartTimeMs())
                        .endTimeMs(summary == null ? 0L : summary.getEndTimeMs())
                        .elapsedTimeMs(summary == null ? 0L : summary.getElapsedTimeMs())
                        .stopped(stopped)
                        .error(errorMessage)
                        .build(),
                stats
        );
        return PerformanceRunExecutionResult.builder()
                .status(status)
                .planPath(planPath)
                .startTimeMs(summary == null ? 0L : summary.getStartTimeMs())
                .endTimeMs(summary == null ? 0L : summary.getEndTimeMs())
                .elapsedTimeMs(summary == null ? 0L : summary.getElapsedTimeMs())
                .stopped(stopped)
                .totalRequests(totalRequests)
                .successRequests(successRequests)
                .failedRequests(failedRequests)
                .error(errorMessage)
                .report(report)
                .build();
    }

    private Environment toEnvironment(PerformanceRunEnvironment source) {
        PerformanceRunEnvironment safeSource = source == null ? PerformanceRunEnvironment.empty() : source;
        Environment environment = new Environment(safeSource.getName());
        environment.setId(safeSource.getId());
        environment.setName(safeSource.getName());
        environment.setActive(true);
        environment.setVariableList(toVariables(safeSource.getVariables()));
        return environment;
    }

    private Environment toGlobals(PerformanceRunVariableSet source) {
        PerformanceRunVariableSet safeSource = source == null ? PerformanceRunVariableSet.empty() : source;
        Environment globals = new Environment("Globals");
        globals.setId("globals");
        globals.setName("Globals");
        globals.setVariableList(toVariables(safeSource.getVariables()));
        return globals;
    }

    private List<Variable> toVariables(List<PerformanceRunVariable> variables) {
        if (variables == null || variables.isEmpty()) {
            return List.of();
        }
        return variables.stream()
                .filter(variable -> variable != null && variable.getKey() != null && !variable.getKey().isBlank())
                .map(variable -> new Variable(variable.isEnabled(), variable.getKey(), variable.getValue()))
                .toList();
    }
}
