package com.laker.postman.panel.performance.result;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.button.SegmentedButtonGroupPanel;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.model.PerformanceProtocolLabels;
import com.laker.postman.performance.core.model.PerformanceTrendSnapshot;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class PerformanceTrendPanelTest extends AbstractSwingUiTest {

    @Test
    public void shouldUseTopToolbarProtocolSwitcherAndGlobalViewMode() {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        JToggleButton httpButton = findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.HTTP));
        JToggleButton webSocketButton = findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.WEBSOCKET));
        JToggleButton sseButton = findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.SSE));

        JToggleButton separateButton = findToggleButton(
                panel,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_SEPARATE_CHARTS)
        );
        JToggleButton combinedButton = findToggleButton(
                panel,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_COMBINED_CHART)
        );

        if (!httpButton.isSelected()) {
            httpButton.doClick();
        }
        if (!separateButton.isSelected()) {
            separateButton.doClick();
        }

        assertTrue(findAll(panel, JTabbedPane.class).isEmpty());
        assertTrue(httpButton.isSelected());
        assertFalse(webSocketButton.isSelected());
        assertFalse(sseButton.isSelected());
        assertEquals(findAll(panel, SegmentedButtonGroupPanel.class).size(), 2);
        assertTrue(separateButton.isSelected());
        assertFalse(combinedButton.isSelected());
        assertEquals(countVisibleCharts(panel), 5);
        assertTrue(hasCheckBox(
                panel,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_VIRTUAL_USERS)
        ));
        assertTrue(((NumberAxis) findAll(panel, ChartPanel.class).get(0).getChart().getXYPlot().getRangeAxis())
                .getNumberFormatOverride() != null);

        combinedButton.doClick();

        assertTrue(combinedButton.isSelected());
        assertFalse(separateButton.isSelected());
        assertEquals(countVisibleCharts(panel), 1);
        assertFalse(findAll(panel, JCheckBox.class).isEmpty());

        separateButton.doClick();
    }

    @Test
    public void shouldHideProtocolSwitcherWhenOnlyHttpMetricsAreAvailable() throws Exception {
        clearSingletonInstance(PerformanceTrendPanel.class);
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.setAvailableProtocols(EnumSet.of(PerformanceProtocol.HTTP));
        panel.setSize(new Dimension(1500, 700));
        layoutRecursively(panel);

        assertFalse(isEffectivelyVisible(
                findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.HTTP)),
                panel
        ));
        assertFalse(isEffectivelyVisible(
                findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.WEBSOCKET)),
                panel
        ));
        assertTrue(isEffectivelyVisible(
                findCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_VIRTUAL_USERS)),
                panel
        ));
        assertTrue(isEffectivelyVisible(
                findToggleButton(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_SEPARATE_CHARTS)),
                panel
        ));
    }

    @Test
    public void shouldShowOnlyAvailableProtocolButtonsWhenStreamingMetricsExist() throws Exception {
        clearSingletonInstance(PerformanceTrendPanel.class);
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.setAvailableProtocols(EnumSet.of(PerformanceProtocol.HTTP, PerformanceProtocol.WEBSOCKET));
        panel.setSize(new Dimension(1500, 700));
        layoutRecursively(panel);

        JToggleButton httpButton = findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.HTTP));
        JToggleButton webSocketButton = findToggleButton(
                panel,
                PerformanceProtocolLabels.displayName(PerformanceProtocol.WEBSOCKET)
        );
        JToggleButton sseButton = findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.SSE));

        assertTrue(isEffectivelyVisible(httpButton, panel));
        assertTrue(isEffectivelyVisible(webSocketButton, panel));
        assertFalse(isEffectivelyVisible(sseButton, panel));

        webSocketButton.doClick();
        layoutRecursively(panel);

        assertTrue(isEffectivelyVisible(
                findCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_ACTIVE_WS)),
                panel
        ));
        assertFalse(isEffectivelyVisible(
                findCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_VIRTUAL_USERS)),
                panel
        ));
    }

    @Test
    public void shouldSelectSingleStreamingProtocolWithoutShowingProtocolSwitcher() throws Exception {
        clearSingletonInstance(PerformanceTrendPanel.class);
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.setAvailableProtocols(EnumSet.of(PerformanceProtocol.WEBSOCKET));
        panel.setSize(new Dimension(1500, 700));
        layoutRecursively(panel);

        assertFalse(isEffectivelyVisible(
                findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.WEBSOCKET)),
                panel
        ));
        assertTrue(isEffectivelyVisible(
                findCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_ACTIVE_WS)),
                panel
        ));
        assertFalse(isEffectivelyVisible(
                findCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_VIRTUAL_USERS)),
                panel
        ));
    }

    @Test
    public void lazyTrendPanelShouldCacheProtocolsWithoutCreatingCharts() {
        LazyPerformanceTrendPanel panel = new LazyPerformanceTrendPanel(PerformanceTrendPanel::new);

        panel.setAvailableProtocols(EnumSet.of(PerformanceProtocol.WEBSOCKET));

        assertFalse(panel.isTrendPanelCreated());
    }

    @Test
    public void shouldSeparateTrendViewControlsFromMetricFilters() throws Exception {
        clearSingletonInstance(PerformanceTrendPanel.class);
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.setAvailableProtocols(EnumSet.of(PerformanceProtocol.HTTP, PerformanceProtocol.WEBSOCKET));
        panel.setSize(new Dimension(1500, 700));
        layoutRecursively(panel);

        JToggleButton httpButton = findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.HTTP));
        JCheckBox virtualUsersCheckBox = findCheckBox(
                panel,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_VIRTUAL_USERS)
        );
        JToggleButton separateButton = findToggleButton(
                panel,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_SEPARATE_CHARTS)
        );

        Rectangle httpBounds = boundsIn(panel, httpButton);
        Rectangle virtualUsersBounds = boundsIn(panel, virtualUsersCheckBox);
        Rectangle separateBounds = boundsIn(panel, separateButton);

        assertTrue(
                Math.abs(centerY(httpBounds) - centerY(separateBounds)) <= 4,
                "chart mode switcher should stay in the same header row as the protocol switcher"
        );
        assertTrue(
                virtualUsersBounds.y > httpBounds.y,
                "metric checkboxes should live in the filter row below the protocol switcher"
        );
        assertTrue(separateBounds.x > httpBounds.x);
    }

    @Test
    public void chineseViewModeLabelsShouldStayCompact() {
        ResourceBundle zh = ResourceBundle.getBundle("messages", Locale.CHINESE);

        assertEquals(zh.getString(MessageKeys.PERFORMANCE_TREND_SEPARATE_CHARTS), "分离");
        assertEquals(zh.getString(MessageKeys.PERFORMANCE_TREND_COMBINED_CHART), "合并");
    }

    @Test
    public void shouldStretchSeparateChartGridWhenViewportIsTallerThanPreferredHeight() {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        JToggleButton separateButton = findToggleButton(
                panel,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_SEPARATE_CHARTS)
        );
        if (!separateButton.isSelected()) {
            separateButton.doClick();
        }

        JScrollPane splitScrollPane = findFirst(panel, JScrollPane.class);
        JViewport viewport = splitScrollPane.getViewport();
        viewport.setExtentSize(new Dimension(1600, 900));

        Component view = viewport.getView();

        assertTrue(((Scrollable) view).getScrollableTracksViewportHeight());
    }

    @Test
    public void shouldLeaveGapWhenStreamLatencyWindowHasNoSample() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries series = getTimeSeries(panel, "wsFirstMessageLatencySeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithWebSocketLatency(60.0));
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), snapshotWithWebSocketLatency(Double.NaN));

        assertEquals(series.getValue(0).doubleValue(), 60.0);
        assertNull(series.getValue(1));
    }

    @Test
    public void shouldLeaveGapWhenHttpResponseWindowHasNoSample() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries series = getTimeSeries(panel, "httpAvgResponseSeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithHttpResponse(69.0));
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), snapshotWithHttpResponse(Double.NaN));

        assertEquals(series.getValue(0).doubleValue(), 69.0);
        assertNull(series.getValue(1));
    }

    @Test
    public void shouldPlotHttpWindowMaxResponseTimeAndLeaveGapWhenUnknown() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries series = getTimeSeries(panel, "httpMaxResponseSeries");
        long base = System.currentTimeMillis();
        PerformanceTrendSnapshot.ProtocolWindowMetrics empty = emptyMetrics();
        PerformanceTrendSnapshot.ProtocolWindowMetrics slowWindow = new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                100, 0, 0, 100, 450.0, 0, 0, 0, 0, 0, 0, Double.NaN, 37_570.0
        );

        panel.addOrUpdate(new Millisecond(new Date(base)), new PerformanceTrendSnapshot(1, 0, 0, empty, slowWindow, empty, empty));
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), snapshotWithHttpResponse(Double.NaN));

        assertEquals(series.getValue(0).doubleValue(), 37_570.0);
        assertNull(series.getValue(1));
    }

    @Test
    public void shouldLeaveGapWhenHttpErrorRateWindowHasNoSample() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries series = getTimeSeries(panel, "httpErrorRateSeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithHttpErrorRate(100.0));
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), snapshotWithHttpErrorRate(Double.NaN));

        assertEquals(series.getValue(0).doubleValue(), 100.0);
        assertNull(series.getValue(1));
    }

    @Test
    public void shouldUseTerminalIdleSnapshotOnlyForActiveSeries() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries activeUsers = getTimeSeries(panel, "httpVirtualUsersSeries");
        TimeSeries qps = getTimeSeries(panel, "httpRpsSeries");
        TimeSeries errorRate = getTimeSeries(panel, "httpErrorRateSeries");
        TimeSeries wsSentRate = getTimeSeries(panel, "wsSentRateSeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithAllProtocolMetrics());
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), PerformanceTrendSnapshot.terminalIdle());

        assertEquals(activeUsers.getValue(0).intValue(), 5);
        assertEquals(activeUsers.getValue(1).intValue(), 0);
        assertEquals(qps.getValue(0).doubleValue(), 1.0);
        assertNull(qps.getValue(1));
        assertEquals(errorRate.getValue(0).doubleValue(), 0.0);
        assertNull(errorRate.getValue(1));
        assertEquals(wsSentRate.getValue(0).doubleValue(), 1.0);
        assertNull(wsSentRate.getValue(1));
    }

    @Test
    public void shouldNotMarkRunEndOnTrendChartsWhenTerminalIdleAppended() {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithAllProtocolMetrics());
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), PerformanceTrendSnapshot.terminalIdle());

        for (ChartPanel chartPanel : findAll(panel, ChartPanel.class)) {
            assertNoRunEndMarkers(chartPanel);
        }
    }

    @Test
    public void clearTrendDatasetShouldKeepRunEndMarkersAbsent() {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithAllProtocolMetrics());
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), PerformanceTrendSnapshot.terminalIdle());
        for (ChartPanel chartPanel : findAll(panel, ChartPanel.class)) {
            assertNoRunEndMarkers(chartPanel);
        }

        panel.clearTrendDataset();

        for (ChartPanel chartPanel : findAll(panel, ChartPanel.class)) {
            assertNoRunEndMarkers(chartPanel);
        }
    }

    @Test
    public void shouldNotDrawLeadingIdlePointForActiveUserSeries() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries activeUsers = getTimeSeries(panel, "httpVirtualUsersSeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), PerformanceTrendSnapshot.terminalIdle());
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), snapshotWithAllProtocolMetrics());

        assertNull(activeUsers.getValue(0));
        assertEquals(activeUsers.getValue(1).intValue(), 5);
    }

    @Test
    public void shouldUseStepRendererForActiveUserSplitChart() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries activeUsers = getTimeSeries(panel, "httpVirtualUsersSeries");

        ChartPanel chartPanel = findChartPanelForSeries(panel, activeUsers);

        assertTrue(chartPanel.getChart().getXYPlot().getRenderer() instanceof XYStepRenderer);
    }

    @Test
    public void shouldUseActualVisibleDomainWindowForVeryShortRuns() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries activeUsers = getTimeSeries(panel, "httpVirtualUsersSeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), PerformanceTrendSnapshot.terminalIdle());
        panel.addOrUpdate(new Millisecond(new Date(base + 500)), snapshotWithAllProtocolMetrics());

        DateAxis domainAxis = (DateAxis) findChartPanelForSeries(panel, activeUsers)
                .getChart()
                .getXYPlot()
                .getDomainAxis();

        assertEquals(domainAxis.getLowerBound(), base, 0.1);
        assertTrue(domainAxis.getUpperBound() > base + 500);
        assertTrue(domainAxis.getUpperBound() - domainAxis.getLowerBound() < 10_000);
    }

    @Test
    public void shouldSpaceTerminalIdlePointAfterLastActiveSampleForVeryShortRuns() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries activeUsers = getTimeSeries(panel, "httpVirtualUsersSeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithAllProtocolMetrics());
        panel.addOrUpdate(new Millisecond(new Date(base + 1)), PerformanceTrendSnapshot.terminalIdle());

        assertEquals(activeUsers.getValue(0).intValue(), 5);
        assertEquals(activeUsers.getValue(1).intValue(), 0);
        assertTrue(activeUsers.getTimePeriod(1).getFirstMillisecond() >= base + 1_000);
    }

    @Test
    public void shouldNotDelayTerminalIdlePointAfterEstablishedRun() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries activeUsers = getTimeSeries(panel, "httpVirtualUsersSeries");
        long base = System.currentTimeMillis();
        long runEnd = base + 10_000L;

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithAllProtocolMetrics());
        panel.addOrUpdate(new Millisecond(new Date(runEnd - 1L)), snapshotWithAllProtocolMetrics());
        panel.addOrUpdate(new Millisecond(new Date(runEnd)), PerformanceTrendSnapshot.terminalIdle());

        assertEquals(activeUsers.getValue(2).intValue(), 0);
        assertEquals(activeUsers.getTimePeriod(2).getFirstMillisecond(), runEnd);
    }

    @Test
    public void shouldSynchronizeSeparateChartDomainRangeAcrossSparseSeries() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries activeUsers = getTimeSeries(panel, "httpVirtualUsersSeries");
        TimeSeries qps = getTimeSeries(panel, "httpRpsSeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), PerformanceTrendSnapshot.terminalIdle());
        panel.addOrUpdate(new Millisecond(new Date(base + 10_000)), snapshotWithAllProtocolMetrics());

        DateAxis activeDomainAxis = (DateAxis) findChartPanelForSeries(panel, activeUsers)
                .getChart()
                .getXYPlot()
                .getDomainAxis();
        DateAxis qpsDomainAxis = (DateAxis) findChartPanelForSeries(panel, qps)
                .getChart()
                .getXYPlot()
                .getDomainAxis();

        assertEquals(qpsDomainAxis.getLowerBound(), activeDomainAxis.getLowerBound(), 0.1);
        assertEquals(qpsDomainAxis.getUpperBound(), activeDomainAxis.getUpperBound(), 0.1);
        assertTrue(activeDomainAxis.getUpperBound() > base + 10_000);
    }

    @Test
    public void shouldRenderSingleFinitePointWhenLaterTrendSamplesAreEmpty() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries series = getTimeSeries(panel, "httpAvgResponseSeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithHttpResponse(69.0));
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), snapshotWithHttpResponse(Double.NaN));

        ChartPanel chartPanel = findChartPanelForSeries(panel, series);
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) chartPanel.getChart().getXYPlot().getRenderer();

        assertTrue(renderer.getItemShapeVisible(0, 0));
        assertFalse(renderer.getItemShapeVisible(0, 1));
    }

    @Test
    public void shouldHideHistoricalIsolatedPointInsideSparseTrendSeries() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries series = getTimeSeries(panel, "httpAvgResponseSeries");
        long base = System.currentTimeMillis();

        panel.addOrUpdate(new Millisecond(new Date(base)), snapshotWithHttpResponse(3_200.0));
        panel.addOrUpdate(new Millisecond(new Date(base + 1_000)), snapshotWithHttpResponse(Double.NaN));
        panel.addOrUpdate(new Millisecond(new Date(base + 2_000)), snapshotWithHttpResponse(1_400.0));
        panel.addOrUpdate(new Millisecond(new Date(base + 3_000)), snapshotWithHttpResponse(1_300.0));

        ChartPanel chartPanel = findChartPanelForSeries(panel, series);
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) chartPanel.getChart().getXYPlot().getRenderer();

        assertFalse(renderer.getItemShapeVisible(0, 0));
        assertFalse(renderer.getItemShapeVisible(0, 1));
        assertFalse(renderer.getItemShapeVisible(0, 2));
        assertFalse(renderer.getItemShapeVisible(0, 3));
    }

    @Test
    public void clearTrendDatasetShouldRestoreAutoRangeForSplitCharts() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        TimeSeries series = getTimeSeries(panel, "httpRpsSeries");
        ChartPanel chartPanel = findChartPanelForSeries(panel, series);
        NumberAxis rangeAxis = (NumberAxis) chartPanel.getChart().getXYPlot().getRangeAxis();
        rangeAxis.setRange(0, 20_000);
        rangeAxis.setAutoRange(false);

        panel.clearTrendDataset();
        panel.addOrUpdate(new Millisecond(new Date(System.currentTimeMillis())), snapshotWithAllProtocolMetrics());

        assertTrue(rangeAxis.isAutoRange());
        assertTrue(rangeAxis.getUpperBound() < 20_000);
    }

    @Test
    public void clearTrendDatasetShouldResetDomainAxisForAllProtocolTrendCharts() throws Exception {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        panel.clearTrendDataset();
        long oldRunTime = System.currentTimeMillis() - 30 * 60 * 1000L;

        panel.addOrUpdate(new Millisecond(new Date(oldRunTime)), snapshotWithAllProtocolMetrics());
        panel.clearTrendDataset();

        long now = System.currentTimeMillis();
        for (String fieldName : List.of(
                "httpVirtualUsersSeries",
                "httpRpsSeries",
                "httpAvgResponseSeries",
                "httpMaxResponseSeries",
                "httpErrorRateSeries",
                "wsActiveSeries",
                "wsSentRateSeries",
                "wsReceivedRateSeries",
                "wsErrorRateSeries",
                "sseActiveSeries",
                "sseEventRateSeries",
                "sseErrorRateSeries"
        )) {
            TimeSeries series = getTimeSeries(panel, fieldName);
            ChartPanel chartPanel = findChartPanelForSeries(panel, series);
            DateAxis domainAxis = (DateAxis) chartPanel.getChart().getXYPlot().getDomainAxis();
            assertDomainAxisNearNow(fieldName, domainAxis, now);
        }
        for (ChartPanel chartPanel : findAll(panel, ChartPanel.class)) {
            DateAxis domainAxis = (DateAxis) chartPanel.getChart().getXYPlot().getDomainAxis();
            assertDomainAxisNearNow(chartPanel.getChart().getTitle().getText(), domainAxis, now);
        }
    }

    @Test
    public void chartPopupMenuShouldFollowEasyPostmanLanguageWhenJFreeChartBundleDiffers() throws Exception {
        boolean originalChinese = I18nUtil.isChinese();
        ResourceBundle originalChartBundle = chartPanelLocalizationBundle();
        try {
            setChartPanelLocalizationBundle(Locale.SIMPLIFIED_CHINESE);
            clearSingletonInstance(PerformanceTrendPanel.class);
            I18nUtil.setLocale("en");

            PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
            JPopupMenu popupMenu = findFirst(panel, ChartPanel.class).getPopupMenu();

            assertTrue(hasPopupLeafText(popupMenu, "Properties..."));
            assertTrue(hasPopupLeafText(popupMenu, "Copy"));
            assertTrue(hasPopupMenuText(popupMenu, "Save as"));
            assertTrue(hasPopupMenuText(popupMenu, "Zoom In"));
            assertTrue(hasPopupMenuText(popupMenu, "Auto Range"));
            assertFalse(hasAnyPopupText(popupMenu, "属性", "复制", "另存为", "放大", "自动调整"));
        } finally {
            I18nUtil.setLocale(originalChinese ? "zh" : "en");
            setChartPanelLocalizationBundle(originalChartBundle);
            clearSingletonInstance(PerformanceTrendPanel.class);
        }
    }

    @Test
    public void chartPopupMenuShouldUseChineseWhenEasyPostmanLanguageIsChinese() throws Exception {
        boolean originalChinese = I18nUtil.isChinese();
        ResourceBundle originalChartBundle = chartPanelLocalizationBundle();
        try {
            setChartPanelLocalizationBundle(Locale.ENGLISH);
            clearSingletonInstance(PerformanceTrendPanel.class);
            I18nUtil.setLocale("zh");

            PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
            JPopupMenu popupMenu = findFirst(panel, ChartPanel.class).getPopupMenu();

            assertTrue(hasPopupLeafText(popupMenu, "属性"));
            assertTrue(hasPopupLeafText(popupMenu, "复制"));
            assertTrue(hasPopupMenuText(popupMenu, "另存为"));
            assertTrue(hasPopupMenuText(popupMenu, "放大"));
            assertTrue(hasPopupMenuText(popupMenu, "自动调整"));
            assertFalse(hasAnyPopupText(popupMenu, "Properties...", "Copy", "Save as", "Zoom In", "Auto Range"));
        } finally {
            I18nUtil.setLocale(originalChinese ? "zh" : "en");
            setChartPanelLocalizationBundle(originalChartBundle);
            clearSingletonInstance(PerformanceTrendPanel.class);
        }
    }

    @Test
    public void shouldHideLatencyAndDurationChartsForStreamingProtocols() {
        PerformanceTrendPanel panel = UiSingletonFactory.getInstance(PerformanceTrendPanel.class);
        JToggleButton separateButton = findToggleButton(
                panel,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_SEPARATE_CHARTS)
        );
        if (!separateButton.isSelected()) {
            separateButton.doClick();
        }

        findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.WEBSOCKET)).doClick();

        assertEquals(countVisibleCharts(panel), 4);
        assertFalse(hasCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_FIRST_MESSAGE_LATENCY_MS)));
        assertFalse(hasCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_SESSION_DURATION_MS)));
        assertTrue(hasCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_ACTIVE_WS)));

        findToggleButton(panel, PerformanceProtocolLabels.displayName(PerformanceProtocol.SSE)).doClick();

        assertEquals(countVisibleCharts(panel), 3);
        assertFalse(hasCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_MATCHED_RATE)));
        assertFalse(hasCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_FIRST_EVENT_LATENCY_MS)));
        assertFalse(hasCheckBox(panel, I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_STREAM_DURATION_MS)));
    }

    private static PerformanceTrendSnapshot snapshotWithHttpResponse(double responseMs) {
        PerformanceTrendSnapshot.ProtocolWindowMetrics empty = emptyMetrics();
        PerformanceTrendSnapshot.ProtocolWindowMetrics http = new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                0, 0, 0, 0, responseMs, 0, 0, 0, 0, 0, 0, Double.NaN
        );
        return new PerformanceTrendSnapshot(0, 0, 0, empty, http, empty, empty);
    }

    private static PerformanceTrendSnapshot snapshotWithHttpErrorRate(double errorRate) {
        PerformanceTrendSnapshot.ProtocolWindowMetrics empty = emptyMetrics();
        PerformanceTrendSnapshot.ProtocolWindowMetrics http = new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                1, Double.isFinite(errorRate) && errorRate > 0 ? 1 : 0, errorRate, 1, 10, 0, 0, 0, 0, 0, 0, Double.NaN
        );
        return new PerformanceTrendSnapshot(0, 0, 0, empty, http, empty, empty);
    }

    private static PerformanceTrendSnapshot snapshotWithWebSocketLatency(double latencyMs) {
        PerformanceTrendSnapshot.ProtocolWindowMetrics empty = emptyMetrics();
        PerformanceTrendSnapshot.ProtocolWindowMetrics ws = new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, latencyMs
        );
        return new PerformanceTrendSnapshot(0, 0, 0, empty, empty, ws, empty);
    }

    private static PerformanceTrendSnapshot snapshotWithAllProtocolMetrics() {
        PerformanceTrendSnapshot.ProtocolWindowMetrics overview = new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                3, 0, 0, 3.0, 50.0, 1, 2, 1, 1.0, 2.0, 1.0, 30.0
        );
        PerformanceTrendSnapshot.ProtocolWindowMetrics http = new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                1, 0, 0, 1.0, 50.0, 0, 0, 0, 0, 0, 0, Double.NaN, 50.0
        );
        PerformanceTrendSnapshot.ProtocolWindowMetrics ws = new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                1, 0, 0, 1.0, 60.0, 1, 1, 1, 1.0, 1.0, 1.0, 20.0
        );
        PerformanceTrendSnapshot.ProtocolWindowMetrics sse = new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                1, 0, 0, 1.0, 70.0, 0, 1, 1, 0, 1.0, 1.0, 25.0
        );
        return new PerformanceTrendSnapshot(5, 3, 2, overview, http, ws, sse);
    }

    private static PerformanceTrendSnapshot.ProtocolWindowMetrics emptyMetrics() {
        return new PerformanceTrendSnapshot.ProtocolWindowMetrics(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Double.NaN
        );
    }

    private static TimeSeries getTimeSeries(PerformanceTrendPanel panel, String fieldName) throws Exception {
        Field field = PerformanceTrendPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (TimeSeries) field.get(panel);
    }

    private static void assertDomainAxisNearNow(String label, DateAxis domainAxis, long now) {
        assertTrue(
                domainAxis.getLowerBound() >= now - 2 * 60 * 1000L,
                label + " lowerBound=" + domainAxis.getLowerBound() + ", now=" + now
        );
        assertTrue(
                domainAxis.getUpperBound() <= now + 2 * 60 * 1000L,
                label + " upperBound=" + domainAxis.getUpperBound() + ", now=" + now
        );
    }

    private static ResourceBundle chartPanelLocalizationBundle() throws Exception {
        Field field = ChartPanel.class.getDeclaredField("localizationResources");
        field.setAccessible(true);
        return (ResourceBundle) field.get(null);
    }

    private static void setChartPanelLocalizationBundle(Locale locale) throws Exception {
        setChartPanelLocalizationBundle(ResourceBundle.getBundle("org.jfree.chart.LocalizationBundle", locale));
    }

    private static void setChartPanelLocalizationBundle(ResourceBundle bundle) throws Exception {
        Field field = ChartPanel.class.getDeclaredField("localizationResources");
        field.setAccessible(true);
        field.set(null, bundle);
    }

    private static void assertNoRunEndMarkers(ChartPanel chartPanel) {
        Collection<Marker> markers = chartPanel.getChart().getXYPlot().getDomainMarkers(Layer.FOREGROUND);
        String title = chartPanel.getChart().getTitle().getText();
        if (markers == null) {
            return;
        }
        assertTrue(markers.isEmpty(), title);
    }

    private static void clearSingletonInstance(Class<?> clazz) throws Exception {
        Field field = UiSingletonFactory.class.getDeclaredField("INSTANCE_MAP");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Class<?>, Object> instances = (Map<Class<?>, Object>) field.get(null);
        instances.remove(clazz);
    }

    private static boolean hasPopupLeafText(JPopupMenu popupMenu, String text) {
        return hasPopupText(popupMenu, text, false);
    }

    private static boolean hasPopupMenuText(JPopupMenu popupMenu, String text) {
        return hasPopupText(popupMenu, text, true);
    }

    private static boolean hasPopupText(Component component, String text, boolean menuOnly) {
        if (component instanceof JMenu menu && text.equals(menu.getText())) {
            return true;
        }
        if (!menuOnly && component instanceof JMenuItem item && !(component instanceof JMenu)
                && text.equals(item.getText())) {
            return true;
        }
        for (Component child : popupChildren(component)) {
            if (hasPopupText(child, text, menuOnly)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyPopupText(JPopupMenu popupMenu, String... texts) {
        for (String text : texts) {
            if (hasPopupText(popupMenu, text, false) || hasPopupText(popupMenu, text, true)) {
                return true;
            }
        }
        return false;
    }

    private static Component[] popupChildren(Component component) {
        if (component instanceof JPopupMenu popupMenu) {
            return popupMenu.getComponents();
        }
        if (component instanceof JMenu menu) {
            return menu.getMenuComponents();
        }
        return new Component[0];
    }

    private static ChartPanel findChartPanelForSeries(Component root, TimeSeries series) {
        for (ChartPanel chartPanel : findAll(root, ChartPanel.class)) {
            if (chartPanel.getChart().getXYPlot().getDataset() instanceof TimeSeriesCollection dataset
                    && dataset.getSeriesCount() == 1
                    && dataset.getSeries(0) == series) {
                return chartPanel;
            }
        }
        throw new AssertionError("Chart panel not found for series: " + series.getKey());
    }

    private static boolean hasCheckBox(Component root, String text) {
        for (JCheckBox checkBox : findAll(root, JCheckBox.class)) {
            if (text.equals(checkBox.getText())) {
                return true;
            }
        }
        return false;
    }

    private static JCheckBox findCheckBox(Component root, String text) {
        for (JCheckBox checkBox : findAll(root, JCheckBox.class)) {
            if (text.equals(checkBox.getText())) {
                return checkBox;
            }
        }
        throw new AssertionError("Checkbox not found: " + text);
    }

    private static int countVisibleCharts(Component root) {
        int count = 0;
        for (ChartPanel chartPanel : findAll(root, ChartPanel.class)) {
            if (isEffectivelyVisible(chartPanel, root)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isEffectivelyVisible(Component component, Component root) {
        Component current = component;
        while (current != null) {
            if (current == root) {
                return true;
            }
            if (!current.isVisible()) {
                return false;
            }
            current = current.getParent();
        }
        return false;
    }

    private static JToggleButton findToggleButton(Component root, String text) {
        for (JToggleButton button : findAll(root, JToggleButton.class)) {
            if (text.equals(button.getText())) {
                return button;
            }
        }
        throw new AssertionError("Toggle button not found: " + text);
    }

    private static <T extends Component> T findFirst(Component root, Class<T> type) {
        List<T> components = findAll(root, type);
        if (components.isEmpty()) {
            throw new AssertionError("Component not found: " + type.getName());
        }
        return components.get(0);
    }

    private static <T extends Component> List<T> findAll(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        collect(root, type, matches);
        return matches;
    }

    private static <T extends Component> void collect(Component component, Class<T> type, List<T> matches) {
        if (type.isInstance(component)) {
            matches.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, matches);
            }
        }
    }

    private static void layoutRecursively(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container childContainer) {
                layoutRecursively(childContainer);
            }
        }
    }

    private static Rectangle boundsIn(Component root, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
    }

    private static int centerY(Rectangle rectangle) {
        return rectangle.y + rectangle.height / 2;
    }
}
