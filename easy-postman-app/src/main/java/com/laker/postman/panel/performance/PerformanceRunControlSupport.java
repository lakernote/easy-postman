package com.laker.postman.panel.performance;

import com.laker.postman.panel.performance.control.PerformanceRunUiController;
import com.laker.postman.panel.performance.control.PerformanceStatisticsCoordinator;
import com.laker.postman.panel.performance.control.PerformanceTimerManager;
import com.laker.postman.performance.plan.PerformancePlanDocumentCompiler;
import com.laker.postman.panel.performance.tree.PerformanceSwingTreePlanAdapter;
import com.laker.postman.panel.performance.result.PerformanceResultTablePanel;
import com.laker.postman.performance.runtime.PerformanceExecutionEngine;
import com.laker.postman.performance.runtime.PerformanceResultSink;
import com.laker.postman.performance.runtime.PerformanceRunRequest;
import com.laker.postman.performance.runtime.PerformanceRunSession;
import com.laker.postman.performance.core.model.PerformanceStatsCollector;
import com.laker.postman.performance.core.model.PerformanceStatsSnapshot;
import com.laker.postman.performance.core.plan.PerformanceTestPlan;
import com.laker.postman.performance.core.runtime.PerformanceRunError;
import com.laker.postman.performance.core.runtime.PerformanceRunHandle;
import com.laker.postman.performance.core.runtime.PerformanceRunSummary;
import com.laker.postman.performance.core.threadgroup.PerformanceRequestEstimate;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

@Slf4j
@RequiredArgsConstructor
final class PerformanceRunControlSupport {

    private final JComponent parentComponent;
    private final BooleanSupplier runningSupplier;
    private final Consumer<Boolean> runningSetter;
    private final LongSupplier startTimeSupplier;
    private final LongConsumer startTimeSetter;
    private final PerformancePropertyPanelSupport propertyPanelSupport;
    private final PerformanceExecutionEngine executionEngine;
    private final PerformanceRunSession runSession;
    private final PerformanceStatisticsCoordinator statisticsCoordinator;
    private final PerformanceTimerManager timerManager;
    private final PerformanceRunUiController runUiController;
    private final JCheckBox efficientCheckBox;
    private final PerformanceResultTablePanel performanceResultTablePanel;
    private final PerformanceStatsCollector statsCollector;
    private final Runnable clearCachedPerformanceResultsAction;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private volatile long expectedTrendEndTimeMs;
    private volatile JLabel currentRunLimitLabel;
    private volatile RunLimitDisplay currentRunLimitDisplay;
    private Timer runStatusTimer;

    Thread startRun(DefaultMutableTreeNode rootNode,
                    JLabel progressLabel,
                    boolean efficientMode,
                    Consumer<Boolean> efficientModeSetter) {
        return startRun(rootNode, progressLabel, null, efficientMode, efficientModeSetter);
    }

    Thread startRun(DefaultMutableTreeNode rootNode,
                    JLabel progressLabel,
                    JLabel limitLabel,
                    boolean efficientMode,
                    Consumer<Boolean> efficientModeSetter) {
        propertyPanelSupport.saveAllPropertyPanelData();
        DefaultMutableTreeNode executionRootNode = PerformanceTreeSnapshot.copy(rootNode);
        PerformanceTestPlan executionPlan = PerformancePlanDocumentCompiler.compile(
                PerformanceSwingTreePlanAdapter.toDocument(executionRootNode)
        );

        PerformanceRequestEstimate requestEstimate = executionEngine.estimateRequestCount(executionPlan);
        long estimatedRequests = requestEstimate.estimatedRequests();
        RunLimitDisplay runLimitDisplay = runLimitDisplay(executionPlan, requestEstimate);
        final int highConcurrencyThreshold = 5000;
        if (estimatedRequests >= highConcurrencyThreshold && !efficientMode) {
            String message = I18nUtil.getMessage(
                    MessageKeys.PERFORMANCE_RESULT_DETAIL_COMPACT_WARNING_MSG,
                    String.format("%,d", estimatedRequests)
            );
            int result = JOptionPane.showConfirmDialog(
                    parentComponent,
                    message,
                    I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_DETAIL_COMPACT_WARNING_TITLE),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (result == JOptionPane.YES_OPTION) {
                efficientModeSetter.accept(true);
                efficientCheckBox.setSelected(true);
                propertyPanelSupport.saveAllPropertyPanelData();
                NotificationCenter.showInfo(I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_DETAIL_COMPACT_ENABLED));
            }
        }

        if (runningSupplier.getAsBoolean() || stopping.get()) {
            return null;
        }

        runUiController.markRunning();
        clearCachedPerformanceResultsAction.run();

        long startTime = System.currentTimeMillis();
        startTimeSetter.accept(startTime);
        expectedTrendEndTimeMs = expectedTrendEndTime(startTime, executionPlan);
        statisticsCoordinator.markRunStarted(startTime);
        timerManager.startAll();

        int totalThreads = executionEngine.getTotalThreads(executionPlan);
        runUiController.initializeProgress(progressLabel, totalThreads);
        startRunStatusTimer(limitLabel, runLimitDisplay);

        PerformanceRunHandle runHandle = runSession.start(PerformanceRunRequest.builder()
                .plan(executionPlan)
                .resultSink(new PerformanceResultSink() {
                    @Override
                    public void onError(PerformanceRunError error) {
                        showRunError(error);
                    }

                    @Override
                    public void onComplete(PerformanceRunSummary summary) {
                        if (summary != null && (summary.isStopped() || summary.getError() != null)) {
                            SwingUtilities.invokeLater(PerformanceRunControlSupport.this::finishStoppedRunUi);
                        } else {
                            SwingUtilities.invokeLater(PerformanceRunControlSupport.this::finishRunUi);
                        }
                    }
                })
                .build());
        return runHandle.threadOrNull();
    }

    void stopRun() {
        if (!runningSupplier.getAsBoolean()) {
            return;
        }
        stopping.set(true);
        runSession.stop();
        timerManager.stopAll();
    }

    private void showRunError(PerformanceRunError error) {
        String detail = "";
        if (error != null && error.getMessage() != null) {
            detail = error.getMessage();
        } else if (error != null && error.getCause() != null && error.getCause().getMessage() != null) {
            detail = error.getCause().getMessage();
        }
        String message = I18nUtil.getMessage(MessageKeys.PERFORMANCE_MSG_EXECUTION_INTERRUPTED, detail);
        SwingUtilities.invokeLater(() -> NotificationCenter.showError(message));
    }

    private void finishRunUi() {
        stopping.set(false);
        runningSetter.accept(false);
        runUiController.markIdle();
        stopRunStatusTimer();
        timerManager.stopAll();

        flushPendingAndCharts("完成时", true);
        statisticsCoordinator.updateFinalReportWithStatsSync();
        refreshFinalRunStatus();

        long totalTime = System.currentTimeMillis() - startTimeSupplier.getAsLong();
        PerformanceStatsSnapshot statsSnapshot = statsCollector.snapshot();
        long totalRequests = statsSnapshot.totalRequests();
        long successCount = statsSnapshot.successRequests();
        String message = I18nUtil.getMessage(
                MessageKeys.PERFORMANCE_MSG_EXECUTION_COMPLETED,
                totalRequests,
                successCount,
                totalTime / 1000.0
        );
        NotificationCenter.showSuccess(message);
    }

    private void flushUiAfterStop() {
        flushPendingAndCharts("停止时", false);
        statisticsCoordinator.updateFinalReportWithStatsSync();
    }

    private void finishStoppedRunUi() {
        stopping.set(false);
        runningSetter.accept(false);
        runUiController.markIdle();
        stopRunStatusTimer();
        timerManager.stopAll();
        flushUiAfterStop();
        refreshFinalRunStatus();
    }

    private void startRunStatusTimer(JLabel limitLabel,
                                     RunLimitDisplay runLimitDisplay) {
        if (limitLabel == null) {
            currentRunLimitLabel = null;
            currentRunLimitDisplay = null;
            return;
        }
        stopRunStatusTimer();
        currentRunLimitLabel = limitLabel;
        currentRunLimitDisplay = runLimitDisplay;
        updateRunStatusLabels(limitLabel, runLimitDisplay);
        runStatusTimer = new Timer(1000, e -> updateRunStatusLabels(
                limitLabel,
                runLimitDisplay
        ));
        runStatusTimer.setRepeats(true);
        runStatusTimer.start();
    }

    private void updateRunStatusLabels(JLabel limitLabel,
                                       RunLimitDisplay runLimitDisplay) {
        long now = System.currentTimeMillis();
        RunStatusText statusText = runStatusText(runLimitDisplay, now);
        runUiController.updateRunStatus(
                limitLabel,
                statusText.text(),
                statusText.tooltipText(),
                statusText.iconPath(),
                statusText.animated()
        );
    }

    private void stopRunStatusTimer() {
        if (runStatusTimer != null) {
            runStatusTimer.stop();
            runStatusTimer = null;
        }
    }

    private void refreshFinalRunStatus() {
        JLabel limitLabel = currentRunLimitLabel;
        RunLimitDisplay runLimitDisplay = currentRunLimitDisplay;
        if (limitLabel != null && runLimitDisplay != null) {
            updateRunStatusLabels(limitLabel, runLimitDisplay);
        }
    }

    private void flushPendingAndCharts(String phase, boolean preferExpectedEndTime) {
        try {
            performanceResultTablePanel.flushPendingResults();
        } catch (Exception e) {
            log.warn("{}刷新结果树失败", phase, e);
        }

        try {
            long terminalTimeMs = terminalTrendTimeMs(preferExpectedEndTime);
            statisticsCoordinator.sampleTrendDataSync(finalTrendSampleTimeMs(terminalTimeMs));
            statisticsCoordinator.appendTerminalIdleTrendPointSync(terminalTimeMs);
        } catch (Exception e) {
            log.warn("{}最后一次趋势图采样失败", phase, e);
        }
    }

    private long terminalTrendTimeMs(boolean preferExpectedEndTime) {
        long now = System.currentTimeMillis();
        if (!preferExpectedEndTime) {
            return now + 1L;
        }
        long expectedEnd = expectedTrendEndTimeMs;
        return Math.max(now + 1L, expectedEnd);
    }

    private long finalTrendSampleTimeMs(long terminalTimeMs) {
        long now = System.currentTimeMillis();
        return Math.max(now, terminalTimeMs - 1L);
    }

    private long expectedTrendEndTime(long startTimeMs, PerformanceTestPlan plan) {
        long expectedDurationMs = expectedTrendDurationMs(plan);
        return expectedDurationMs <= 0 ? 0L : startTimeMs + expectedDurationMs;
    }

    private long expectedTrendDurationMs(PerformanceTestPlan plan) {
        if (plan == null || plan.getThreadGroups().isEmpty()) {
            return 0L;
        }
        long durationSeconds = 0L;
        for (var group : plan.getThreadGroups()) {
            if (group == null) {
                continue;
            }
            ThreadGroupData data = group.getThreadGroupData();
            if (data == null) {
                continue;
            }
            durationSeconds = Math.max(durationSeconds, expectedThreadGroupDurationSeconds(data));
        }
        return durationSeconds * 1000L;
    }

    private long expectedThreadGroupDurationSeconds(ThreadGroupData data) {
        if (data.threadMode == null) {
            return 0L;
        }
        return switch (data.threadMode) {
            case FIXED -> data.useTime ? Math.max(0L, data.duration) : 0L;
            case RAMP_UP -> Math.max(0L, data.rampUpDuration);
            case SPIKE -> Math.max(0L, data.spikeDuration);
            case STAIRS -> Math.max(0L, data.stairsDuration);
        };
    }

    private RunLimitDisplay runLimitDisplay(PerformanceTestPlan plan, PerformanceRequestEstimate requestEstimate) {
        long expectedDurationMs = expectedTrendDurationMs(plan);
        if (expectedDurationMs > 0L) {
            return RunLimitDisplay.duration(expectedDurationMs);
        }
        PerformanceRequestEstimate estimate = requestEstimate == null
                ? PerformanceRequestEstimate.fixed(0L)
                : requestEstimate;
        return RunLimitDisplay.requests(estimate.estimatedRequests(), estimate.dynamic());
    }

    private RunStatusText runStatusText(RunLimitDisplay display, long nowMs) {
        if (display == null) {
            return RunStatusText.hidden();
        }
        if (display.durationMode()) {
            long remainingMs = Math.max(0L, startTimeSupplier.getAsLong() + display.durationMs() - nowMs);
            int activeThreads = executionEngine.getActiveThreads();
            if (remainingMs == 0L && runningSupplier.getAsBoolean() && activeThreads > 0) {
                return RunStatusText.draining(
                        I18nUtil.getMessage(MessageKeys.PERFORMANCE_RUN_STATUS_DRAINING)
                );
            }
            return RunStatusText.eta(formatRunDuration(remainingMs));
        }
        long completed = Math.max(0L, statsCollector.progressSnapshot().totalRequests());
        if (display.dynamicRequests()) {
            return RunStatusText.request(formatCount(completed) + "/"
                    + I18nUtil.getMessage(MessageKeys.PERFORMANCE_RUN_STATUS_DYNAMIC));
        }
        String text = display.estimatedRequests() == Long.MAX_VALUE
                ? formatCount(completed) + "/max"
                : formatCount(completed) + "/" + formatCount(display.estimatedRequests());
        return RunStatusText.request(text);
    }

    static String formatRunDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long seconds = totalSeconds % 60L;
        long minutes = (totalSeconds / 60L) % 60L;
        long hours = totalSeconds / 3600L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static String formatCount(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private record RunLimitDisplay(boolean durationMode,
                                   long durationMs,
                                   long estimatedRequests,
                                   boolean dynamicRequests) {
        private static RunLimitDisplay duration(long durationMs) {
            return new RunLimitDisplay(true, Math.max(0L, durationMs), 0L, false);
        }

        private static RunLimitDisplay requests(long estimatedRequests, boolean dynamicRequests) {
            return new RunLimitDisplay(false, 0L, Math.max(0L, estimatedRequests), dynamicRequests);
        }
    }

    private record RunStatusText(String text, String tooltipText, String iconPath, boolean animated) {
        private static RunStatusText hidden() {
            return new RunStatusText("", "", null, false);
        }

        private static RunStatusText eta(String text) {
            return new RunStatusText(text, text, PerformanceRunUiController.ETA_STATUS_ICON, false);
        }

        private static RunStatusText draining(String tooltipText) {
            return new RunStatusText("", tooltipText, PerformanceRunUiController.ETA_STATUS_ICON, true);
        }

        private static RunStatusText request(String text) {
            return new RunStatusText(text, text, PerformanceRunUiController.REQUEST_STATUS_ICON, false);
        }
    }
}
