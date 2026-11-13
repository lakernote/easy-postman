package com.laker.postman.panel.performance.result;

import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.model.PerformanceTrendSnapshot;


import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.component.ToolWindowActionToolbar;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.SegmentedButtonBar;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.performance.model.PerformanceProtocolLabels;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import net.miginfocom.swing.MigLayout;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.time.DateRange;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

public class PerformanceTrendPanel extends UiSingletonPanel implements PerformanceTrendView {

    private static final String SEPARATE_VIEW = "separate";
    private static final String COMBINED_VIEW = "combined";
    private static final int MAX_TREND_POINTS = 3_600;
    private static final long EMPTY_DOMAIN_WINDOW_MS = 60_000L;
    private static final long MIN_ACTIVE_IDLE_TRANSITION_MS = 1_000L;
    private static final String JFREE_CHART_BUNDLE = "org.jfree.chart.LocalizationBundle";
    private static final String SAVE_AS_PNG_COMMAND = "SAVE_AS_PNG";
    private static final String SAVE_AS_SVG_COMMAND = "SAVE_AS_SVG";
    private static final String SAVE_AS_PDF_COMMAND = "SAVE_AS_PDF";
    private static final Map<String, String> CHART_POPUP_ACTION_KEYS = Map.ofEntries(
            Map.entry(ChartPanel.PROPERTIES_COMMAND, "Properties..."),
            Map.entry(ChartPanel.COPY_COMMAND, "Copy"),
            Map.entry(ChartPanel.SAVE_COMMAND, "Save_as..."),
            Map.entry(SAVE_AS_PNG_COMMAND, "PNG..."),
            Map.entry(SAVE_AS_SVG_COMMAND, "SVG..."),
            Map.entry(SAVE_AS_PDF_COMMAND, "PDF..."),
            Map.entry(ChartPanel.PRINT_COMMAND, "Print..."),
            Map.entry(ChartPanel.ZOOM_IN_BOTH_COMMAND, "All_Axes"),
            Map.entry(ChartPanel.ZOOM_IN_DOMAIN_COMMAND, "Domain_Axis"),
            Map.entry(ChartPanel.ZOOM_IN_RANGE_COMMAND, "Range_Axis"),
            Map.entry(ChartPanel.ZOOM_OUT_BOTH_COMMAND, "All_Axes"),
            Map.entry(ChartPanel.ZOOM_OUT_DOMAIN_COMMAND, "Domain_Axis"),
            Map.entry(ChartPanel.ZOOM_OUT_RANGE_COMMAND, "Range_Axis"),
            Map.entry(ChartPanel.ZOOM_RESET_BOTH_COMMAND, "All_Axes"),
            Map.entry(ChartPanel.ZOOM_RESET_DOMAIN_COMMAND, "Domain_Axis"),
            Map.entry(ChartPanel.ZOOM_RESET_RANGE_COMMAND, "Range_Axis")
    );

    private final TimeSeries httpVirtualUsersSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_VIRTUAL_USERS));
    private final TimeSeries httpRpsSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_QPS));
    private final TimeSeries httpAvgResponseSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_RESPONSE_TIME_MS));
    private final TimeSeries httpMaxResponseSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_MAX_RESPONSE_TIME_MS));
    private final TimeSeries httpErrorRateSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_ERROR_RATE_PERCENT));

    private final TimeSeries wsActiveSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_ACTIVE_WS));
    private final TimeSeries wsSentRateSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_SENT_RATE));
    private final TimeSeries wsReceivedRateSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_RECEIVED_RATE));
    private final TimeSeries wsFirstMessageLatencySeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_FIRST_MESSAGE_LATENCY_MS));
    private final TimeSeries wsSessionDurationSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_SESSION_DURATION_MS));
    private final TimeSeries wsErrorRateSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_ERROR_RATE_PERCENT));

    private final TimeSeries sseActiveSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_ACTIVE_SSE));
    private final TimeSeries sseEventRateSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_EVENT_RATE));
    private final TimeSeries sseMatchedRateSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_MATCHED_RATE));
    private final TimeSeries sseFirstEventLatencySeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_FIRST_EVENT_LATENCY_MS));
    private final TimeSeries sseStreamDurationSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_STREAM_DURATION_MS));
    private final TimeSeries sseErrorRateSeries = new TimeSeries(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_ERROR_RATE_PERCENT));

    private final List<TrendView> trendViews = new ArrayList<>();
    private final Map<PerformanceProtocol, JToggleButton> protocolButtons = new EnumMap<>(PerformanceProtocol.class);
    private JPanel protocolSwitcherRow;
    private JComponent protocolSwitcher;
    private JPanel metricControlsCards;
    private JPanel protocolCards;
    private PerformanceProtocol selectedProtocol = PerformanceProtocol.HTTP;
    private Set<PerformanceProtocol> availableProtocols = EnumSet.of(PerformanceProtocol.HTTP);
    private Long trendDomainStartMs;
    private Long trendDomainEndMs;
    private String chartMode = SEPARATE_VIEW;

    @Override
    protected void initUI() {
        configureSeriesRetention();
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(this);
        protocolCards = new JPanel(new CardLayout());
        metricControlsCards = new JPanel(new CardLayout());
        ToolWindowSurfaceStyle.applyCard(protocolCards);
        metricControlsCards.setOpaque(false);
        protocolCards.add(createHttpPanel(metricControlsCards), PerformanceProtocol.HTTP.name());
        protocolCards.add(createWebSocketPanel(metricControlsCards), PerformanceProtocol.WEBSOCKET.name());
        protocolCards.add(createSsePanel(metricControlsCards), PerformanceProtocol.SSE.name());
        add(createToolbar(protocolCards, metricControlsCards), BorderLayout.NORTH);
        add(protocolCards, BorderLayout.CENTER);
        applyAvailableProtocols();
    }

    @Override
    protected void registerListeners() {
        // Charts are updated by PerformanceStatisticsCoordinator.
    }

    @Override
    public void setAvailableProtocols(Set<PerformanceProtocol> protocols) {
        availableProtocols = normalizeProtocols(protocols);
        applyAvailableProtocols();
    }

    private void configureSeriesRetention() {
        for (TimeSeries series : allSeries()) {
            series.setMaximumItemCount(MAX_TREND_POINTS);
        }
    }

    private JPanel createHttpPanel(JPanel metricControlsCards) {
        return createTrendView(
                metricControlsCards,
                PerformanceProtocol.HTTP,
                MessageKeys.PERFORMANCE_TREND_METRICS,
                new SeriesSpec(httpVirtualUsersSeries, PerformanceTrendTheme.threadsLine(), true, AxisFormat.INTEGER),
                new SeriesSpec(httpRpsSeries, PerformanceTrendTheme.qpsLine(), true, AxisFormat.DECIMAL),
                new SeriesSpec(httpAvgResponseSeries, PerformanceTrendTheme.responseTimeLine(), true, AxisFormat.DECIMAL),
                new SeriesSpec(httpMaxResponseSeries, PerformanceTrendTheme.durationLine(), true, AxisFormat.DECIMAL),
                new SeriesSpec(httpErrorRateSeries, PerformanceTrendTheme.errorRateLine(), true, AxisFormat.DECIMAL)
        );
    }

    private JPanel createWebSocketPanel(JPanel metricControlsCards) {
        return createTrendView(
                metricControlsCards,
                PerformanceProtocol.WEBSOCKET,
                MessageKeys.PERFORMANCE_TREND_METRICS,
                new SeriesSpec(wsActiveSeries, PerformanceTrendTheme.threadsLine(), true, AxisFormat.INTEGER),
                new SeriesSpec(wsSentRateSeries, PerformanceTrendTheme.matchedLine(), true, AxisFormat.DECIMAL),
                new SeriesSpec(wsReceivedRateSeries, PerformanceTrendTheme.qpsLine(), true, AxisFormat.DECIMAL),
                new SeriesSpec(wsErrorRateSeries, PerformanceTrendTheme.errorRateLine(), true, AxisFormat.DECIMAL)
        );
    }

    private JPanel createSsePanel(JPanel metricControlsCards) {
        return createTrendView(
                metricControlsCards,
                PerformanceProtocol.SSE,
                MessageKeys.PERFORMANCE_TREND_METRICS,
                new SeriesSpec(sseActiveSeries, PerformanceTrendTheme.threadsLine(), true, AxisFormat.INTEGER),
                new SeriesSpec(sseEventRateSeries, PerformanceTrendTheme.qpsLine(), true, AxisFormat.DECIMAL),
                new SeriesSpec(sseErrorRateSeries, PerformanceTrendTheme.errorRateLine(), true, AxisFormat.DECIMAL)
        );
    }

    private JPanel createTrendView(JPanel metricControlsCards,
                                   PerformanceProtocol protocol,
                                   String titleKey,
                                   SeriesSpec... specs) {
        TrendView view = new TrendView(titleKey, specs);
        trendViews.add(view);
        metricControlsCards.add(view.controlsPanel(), protocol.name());
        return view.panel();
    }

    private JPanel createToolbar(JPanel protocolCards, JPanel metricControlsCards) {
        JPanel toolbar = new JPanel(new MigLayout(
                "insets 0, fillx, novisualpadding, hidemode 3, gap 0",
                "[grow,fill]",
                "[]6[]"
        ));
        ToolWindowSurfaceStyle.applyToolWindowToolbarSeparator(toolbar, 7, 10, 8, 10);
        protocolSwitcherRow = createProtocolSwitcher();
        toolbar.add(protocolSwitcherRow, "growx, wrap");
        toolbar.add(createMetricSelector(metricControlsCards), "growx, wmin 0");
        return toolbar;
    }

    private JPanel createMetricSelector(JPanel metricControlsCards) {
        JPanel panel = new JPanel(new MigLayout(
                "insets 0, fillx, novisualpadding, gap 0",
                "[]8[grow,fill]",
                "[]"
        ));
        panel.setOpaque(false);

        JLabel label = new JLabel(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_METRIC_FILTERS));
        label.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        label.setForeground(ModernColors.getTextSecondary());

        panel.add(label);
        panel.add(metricControlsCards, "growx, wmin 0");
        return panel;
    }

    private JPanel createProtocolSwitcher() {
        JPanel row = new JPanel(new MigLayout(
                "insets 0, fillx, novisualpadding, hidemode 3, gap 0",
                "[pref!,left]push[]",
                "[]"
        ));
        row.setOpaque(false);
        SegmentedButtonBar<PerformanceProtocol> switcher = new SegmentedButtonBar<>(FlowLayout.LEFT);
        for (PerformanceProtocol protocol : PerformanceProtocol.values()) {
            JToggleButton button = switcher.addOption(
                    protocol,
                    PerformanceProtocolLabels.displayName(protocol),
                    protocol == PerformanceProtocol.HTTP
            );
            button.addActionListener(e -> {
                if (button.isSelected()) {
                    showProtocol(protocol);
                }
            });
            protocolButtons.put(protocol, button);
        }
        protocolSwitcher = switcher;
        row.add(switcher);
        row.add(ToolWindowActionToolbar.inlineRight(createModeSwitcher()), "align right");
        return row;
    }

    private void applyAvailableProtocols() {
        if (protocolCards == null || metricControlsCards == null || protocolSwitcherRow == null) {
            return;
        }
        for (Map.Entry<PerformanceProtocol, JToggleButton> entry : protocolButtons.entrySet()) {
            entry.getValue().setVisible(availableProtocols.contains(entry.getKey()));
        }
        if (!availableProtocols.contains(selectedProtocol)) {
            selectedProtocol = firstAvailableProtocol();
        }
        protocolSwitcher.setVisible(availableProtocols.size() > 1);
        protocolSwitcherRow.setVisible(true);
        showProtocol(selectedProtocol);
        revalidate();
        repaint();
    }

    private PerformanceProtocol firstAvailableProtocol() {
        if (availableProtocols.contains(PerformanceProtocol.HTTP)) {
            return PerformanceProtocol.HTTP;
        }
        for (PerformanceProtocol protocol : PerformanceProtocol.values()) {
            if (availableProtocols.contains(protocol)) {
                return protocol;
            }
        }
        return PerformanceProtocol.HTTP;
    }

    private void showProtocol(PerformanceProtocol protocol) {
        selectedProtocol = protocol;
        JToggleButton button = protocolButtons.get(protocol);
        if (button != null && !button.isSelected()) {
            button.setSelected(true);
        }
        CardLayout layout = (CardLayout) protocolCards.getLayout();
        layout.show(protocolCards, protocol.name());
        CardLayout controlsLayout = (CardLayout) metricControlsCards.getLayout();
        controlsLayout.show(metricControlsCards, protocol.name());
    }

    private static Set<PerformanceProtocol> normalizeProtocols(Set<PerformanceProtocol> protocols) {
        EnumSet<PerformanceProtocol> normalized = EnumSet.noneOf(PerformanceProtocol.class);
        if (protocols != null) {
            normalized.addAll(protocols);
        }
        if (normalized.isEmpty()) {
            normalized.add(PerformanceProtocol.HTTP);
        }
        return normalized;
    }

    private JPanel createModeSwitcher() {
        SegmentedButtonBar<String> modePanel = new SegmentedButtonBar<>(FlowLayout.RIGHT);
        JToggleButton separateButton = modePanel.addOption(
                SEPARATE_VIEW,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_SEPARATE_CHARTS),
                true
        );
        JToggleButton combinedButton = modePanel.addOption(
                COMBINED_VIEW,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_COMBINED_CHART),
                false
        );
        separateButton.addActionListener(e -> showChartMode(SEPARATE_VIEW));
        combinedButton.addActionListener(e -> showChartMode(COMBINED_VIEW));
        return modePanel;
    }

    private ChartPanel createChartPanel(TimeSeriesCollection dataset, String titleKey) {
        return createChartPanel(dataset, I18nUtil.getMessage(titleKey), true);
    }

    private ChartPanel createChartPanel(TimeSeriesCollection dataset, String title, boolean legend) {
        return createChartPanel(dataset, title, legend, AxisFormat.DECIMAL);
    }

    private ChartPanel createChartPanel(TimeSeriesCollection dataset, String title, boolean legend, AxisFormat axisFormat) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                title,
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_TIME),
                title,
                dataset,
                legend,
                true,
                false
        );
        chart.setBackgroundPaint(PerformanceTrendTheme.chartBackground());
        chart.getTitle().setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 0));
        chart.getTitle().setPaint(PerformanceTrendTheme.text());
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(FontsUtil.getDefaultFont(Font.PLAIN));
            chart.getLegend().setItemPaint(PerformanceTrendTheme.text());
            chart.getLegend().setBackgroundPaint(PerformanceTrendTheme.chartBackground());
        }

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(PerformanceTrendTheme.chartBackground());
        plot.setDomainGridlinePaint(PerformanceTrendTheme.gridLine());
        plot.setRangeGridlinePaint(PerformanceTrendTheme.gridLine());
        plot.setOutlinePaint(PerformanceTrendTheme.chartBorder());

        DateAxis dateAxis = new DateAxis(I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_TIME));
        PerformanceTrendAxisConfigurer.configureTimeAxis(dateAxis, EMPTY_DOMAIN_WINDOW_MS);
        dateAxis.setTickLabelFont(FontsUtil.getDefaultFont(Font.PLAIN));
        dateAxis.setLabelFont(FontsUtil.getDefaultFont(Font.PLAIN));
        dateAxis.setTickLabelPaint(PerformanceTrendTheme.text());
        dateAxis.setLabelPaint(PerformanceTrendTheme.text());
        plot.setDomainAxis(dateAxis);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setTickLabelFont(FontsUtil.getDefaultFont(Font.PLAIN));
        rangeAxis.setLabelFont(FontsUtil.getDefaultFont(Font.PLAIN));
        rangeAxis.setTickLabelPaint(PerformanceTrendTheme.text());
        rangeAxis.setLabelPaint(PerformanceTrendTheme.text());
        PerformanceTrendAxisConfigurer.configureMetricAxis(rangeAxis);
        if (axisFormat == AxisFormat.INTEGER) {
            PerformanceTrendAxisConfigurer.configureIntegerAxis(rangeAxis);
        }

        ChartPanel panel = new ChartPanel(chart);
        panel.setMouseWheelEnabled(true);
        panel.setBackground(PerformanceTrendTheme.chartPanelBackground());
        ToolWindowSurfaceStyle.applyCard(panel);
        panel.setDisplayToolTips(true);
        panel.setMinimumDrawWidth(0);
        panel.setMinimumDrawHeight(0);
        panel.setMaximumDrawWidth(Integer.MAX_VALUE);
        panel.setMaximumDrawHeight(Integer.MAX_VALUE);
        installLocalizedChartPopupMenu(panel);
        return panel;
    }

    private static void installLocalizedChartPopupMenu(ChartPanel panel) {
        JPopupMenu popupMenu = panel.getPopupMenu();
        if (popupMenu == null) {
            return;
        }
        localizeChartPopupMenu(popupMenu);
        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                localizeChartPopupMenu(popupMenu);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
    }

    private static void localizeChartPopupMenu(JPopupMenu popupMenu) {
        ResourceBundle bundle = chartPopupResourceBundle();
        popupMenu.setLabel(chartPopupText(bundle, "Chart") + ":");
        for (Component component : popupMenu.getComponents()) {
            localizeChartPopupComponent(component, bundle);
        }
    }

    private static void localizeChartPopupComponent(Component component, ResourceBundle bundle) {
        if (component instanceof JMenu menu) {
            String menuKey = resolveChartPopupMenuKey(menu);
            if (menuKey != null) {
                menu.setText(chartPopupText(bundle, menuKey));
            }
            for (Component child : menu.getMenuComponents()) {
                localizeChartPopupComponent(child, bundle);
            }
            return;
        }

        if (component instanceof JMenuItem menuItem) {
            String key = CHART_POPUP_ACTION_KEYS.get(menuItem.getActionCommand());
            if (key != null) {
                menuItem.setText(chartPopupText(bundle, key));
            }
        }
    }

    private static String resolveChartPopupMenuKey(JMenu menu) {
        if (containsChartPopupCommand(menu, ChartPanel.SAVE_COMMAND, SAVE_AS_PNG_COMMAND,
                SAVE_AS_SVG_COMMAND, SAVE_AS_PDF_COMMAND)) {
            return "Save_as";
        }
        if (containsChartPopupCommand(menu, ChartPanel.ZOOM_IN_BOTH_COMMAND,
                ChartPanel.ZOOM_IN_DOMAIN_COMMAND, ChartPanel.ZOOM_IN_RANGE_COMMAND)) {
            return "Zoom_In";
        }
        if (containsChartPopupCommand(menu, ChartPanel.ZOOM_OUT_BOTH_COMMAND,
                ChartPanel.ZOOM_OUT_DOMAIN_COMMAND, ChartPanel.ZOOM_OUT_RANGE_COMMAND)) {
            return "Zoom_Out";
        }
        if (containsChartPopupCommand(menu, ChartPanel.ZOOM_RESET_BOTH_COMMAND,
                ChartPanel.ZOOM_RESET_DOMAIN_COMMAND, ChartPanel.ZOOM_RESET_RANGE_COMMAND)) {
            return "Auto_Range";
        }
        return null;
    }

    private static boolean containsChartPopupCommand(JMenu menu, String... commands) {
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JMenu childMenu && containsChartPopupCommand(childMenu, commands)) {
                return true;
            }
            if (component instanceof JMenuItem menuItem && matchesCommand(menuItem.getActionCommand(), commands)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCommand(String actionCommand, String... commands) {
        for (String command : commands) {
            if (command.equals(actionCommand)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceBundle chartPopupResourceBundle() {
        Locale locale = I18nUtil.isChinese() ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
        return ResourceBundle.getBundle(JFREE_CHART_BUNDLE, locale);
    }

    private static String chartPopupText(ResourceBundle bundle, String key) {
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    private XYLineAndShapeRenderer createTrendRenderer() {
        XYLineAndShapeRenderer renderer = new PerformanceTrendSinglePointRenderer();
        renderer.setDefaultShape(new Line2D.Double(-8.0, 0.0, 8.0, 0.0));
        renderer.setDefaultShapesFilled(false);
        renderer.setDrawOutlines(false);
        return renderer;
    }

    private XYLineAndShapeRenderer createActiveCountTrendRenderer() {
        XYStepRenderer renderer = new XYStepRenderer();
        renderer.setDefaultShapesVisible(false);
        renderer.setDefaultShapesFilled(false);
        renderer.setDrawOutlines(false);
        renderer.setStepPoint(1.0);
        return renderer;
    }

    @Override
    public void clearTrendDataset() {
        long resetTimeMs = System.currentTimeMillis();
        for (TimeSeries series : allSeries()) {
            series.clear();
        }
        trendDomainStartMs = null;
        trendDomainEndMs = null;
        for (TrendView trendView : trendViews) {
            trendView.resetAxes(resetTimeMs);
        }
    }

    private static void resetAxes(ChartPanel chartPanel, long resetTimeMs) {
        if (chartPanel == null || chartPanel.getChart() == null) {
            return;
        }
        XYPlot plot = chartPanel.getChart().getXYPlot();
        plot.clearDomainMarkers();
        if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
            long rightPaddingMs = PerformanceTrendAxisConfigurer.domainRightPaddingMs(EMPTY_DOMAIN_WINDOW_MS);
            DateRange resetRange = new DateRange(
                    new Date(Math.max(0, resetTimeMs - EMPTY_DOMAIN_WINDOW_MS)),
                    new Date(resetTimeMs + rightPaddingMs)
            );
            dateAxis.setAutoRange(true);
            dateAxis.setRange(resetRange, false, true);
            PerformanceTrendAxisConfigurer.configureTimeAxis(dateAxis, EMPTY_DOMAIN_WINDOW_MS);
        }
        if (plot.getRangeAxis() != null) {
            plot.getRangeAxis().setAutoRange(true);
        }
    }

    private TimeSeries[] allSeries() {
        return new TimeSeries[]{
                httpVirtualUsersSeries, httpRpsSeries, httpAvgResponseSeries, httpMaxResponseSeries, httpErrorRateSeries,
                wsActiveSeries, wsSentRateSeries, wsReceivedRateSeries, wsFirstMessageLatencySeries,
                wsSessionDurationSeries, wsErrorRateSeries,
                sseActiveSeries, sseEventRateSeries, sseMatchedRateSeries, sseFirstEventLatencySeries,
                sseStreamDurationSeries, sseErrorRateSeries
        };
    }

    @Override
    public void addOrUpdate(RegularTimePeriod period, PerformanceTrendSnapshot snapshot) {
        if (period == null || snapshot == null) {
            return;
        }
        period = normalizeDisplayPeriod(period, snapshot);
        boolean suppressLeadingIdleActiveCounts = shouldSuppressLeadingIdleActiveCounts(snapshot);

        httpVirtualUsersSeries.addOrUpdate(period, PerformanceTrendSeriesValue.activeCount(
                snapshot.activeUsers(), suppressLeadingIdleActiveCounts));
        httpRpsSeries.addOrUpdate(period, PerformanceTrendSeriesValue.sampleMetric(snapshot.http().sampleRate()));
        httpAvgResponseSeries.addOrUpdate(period, PerformanceTrendSeriesValue.sampleMetric(snapshot.http().avgDurationMs()));
        httpMaxResponseSeries.addOrUpdate(period, PerformanceTrendSeriesValue.sampleMetric(snapshot.http().maxDurationMs()));
        httpErrorRateSeries.addOrUpdate(period, PerformanceTrendSeriesValue.sampleMetric(snapshot.http().failurePercent()));

        wsActiveSeries.addOrUpdate(period, PerformanceTrendSeriesValue.activeCount(
                snapshot.activeWebSocketConnections(), suppressLeadingIdleActiveCounts));
        wsSentRateSeries.addOrUpdate(period, PerformanceTrendSeriesValue.sampleMetric(snapshot.webSocket().sentRate()));
        wsReceivedRateSeries.addOrUpdate(period, PerformanceTrendSeriesValue.sampleMetric(snapshot.webSocket().receivedRate()));
        wsFirstMessageLatencySeries.addOrUpdate(period,
                PerformanceTrendSeriesValue.sampleMetric(snapshot.webSocket().avgFirstMessageLatencyMs()));
        wsSessionDurationSeries.addOrUpdate(period,
                PerformanceTrendSeriesValue.sampleMetric(snapshot.webSocket().avgDurationMs()));
        wsErrorRateSeries.addOrUpdate(period,
                PerformanceTrendSeriesValue.sampleMetric(snapshot.webSocket().failurePercent()));

        sseActiveSeries.addOrUpdate(period, PerformanceTrendSeriesValue.activeCount(
                snapshot.activeSseStreams(), suppressLeadingIdleActiveCounts));
        sseEventRateSeries.addOrUpdate(period, PerformanceTrendSeriesValue.sampleMetric(snapshot.sse().receivedRate()));
        sseMatchedRateSeries.addOrUpdate(period, PerformanceTrendSeriesValue.sampleMetric(snapshot.sse().matchedRate()));
        sseFirstEventLatencySeries.addOrUpdate(period,
                PerformanceTrendSeriesValue.sampleMetric(snapshot.sse().avgFirstMessageLatencyMs()));
        sseStreamDurationSeries.addOrUpdate(period,
                PerformanceTrendSeriesValue.sampleMetric(snapshot.sse().avgDurationMs()));
        sseErrorRateSeries.addOrUpdate(period, PerformanceTrendSeriesValue.sampleMetric(snapshot.sse().failurePercent()));

        syncDomainAxes(period);
    }

    private RegularTimePeriod normalizeDisplayPeriod(RegularTimePeriod period, PerformanceTrendSnapshot snapshot) {
        if (trendDomainStartMs == null || !isIdleSnapshot(snapshot)) {
            return period;
        }
        Long lastActiveTimeMs = lastPositiveActiveSampleTimeMs();
        if (lastActiveTimeMs == null) {
            return period;
        }
        if (period.getFirstMillisecond() - trendDomainStartMs >= MIN_ACTIVE_IDLE_TRANSITION_MS) {
            return period;
        }
        long minTerminalTimeMs = lastActiveTimeMs + MIN_ACTIVE_IDLE_TRANSITION_MS;
        if (period.getFirstMillisecond() >= minTerminalTimeMs) {
            return period;
        }
        // 极短压测中 active=0 和最后 active>0 可能挤在同一毫秒，才额外拉开一点距离。
        return new Millisecond(new Date(minTerminalTimeMs));
    }

    private boolean shouldSuppressLeadingIdleActiveCounts(PerformanceTrendSnapshot snapshot) {
        return trendDomainStartMs == null && isIdleSnapshot(snapshot);
    }

    private static boolean isIdleSnapshot(PerformanceTrendSnapshot snapshot) {
        return snapshot.activeUsers() == 0
                && snapshot.activeWebSocketConnections() == 0
                && snapshot.activeSseStreams() == 0
                && hasNoSamples(snapshot.overview())
                && hasNoSamples(snapshot.http())
                && hasNoSamples(snapshot.webSocket())
                && hasNoSamples(snapshot.sse());
    }

    private static boolean hasNoSamples(PerformanceTrendSnapshot.ProtocolWindowMetrics metrics) {
        return metrics == null || metrics.samples() == 0;
    }

    private Long lastPositiveActiveSampleTimeMs() {
        Long httpTime = lastPositiveSampleTimeMs(httpVirtualUsersSeries);
        Long wsTime = lastPositiveSampleTimeMs(wsActiveSeries);
        Long sseTime = lastPositiveSampleTimeMs(sseActiveSeries);
        Long latest = latestTime(httpTime, wsTime);
        return latestTime(latest, sseTime);
    }

    private static Long latestTime(Long first, Long second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return Math.max(first, second);
    }

    private static Long lastPositiveSampleTimeMs(TimeSeries series) {
        for (int i = series.getItemCount() - 1; i >= 0; i--) {
            Number value = series.getValue(i);
            if (value != null && value.doubleValue() > 0) {
                return series.getTimePeriod(i).getFirstMillisecond();
            }
        }
        return null;
    }

    private void syncDomainAxes(RegularTimePeriod period) {
        long periodStart = period.getFirstMillisecond();
        long periodEnd = period.getLastMillisecond();
        trendDomainStartMs = trendDomainStartMs == null ? periodStart : Math.min(trendDomainStartMs, periodStart);
        trendDomainEndMs = trendDomainEndMs == null ? periodEnd : Math.max(trendDomainEndMs, periodEnd);

        // 同一次压测的分离图必须共享 X 轴，否则空值较多的指标会自动裁剪到不同时间范围。
        long end = trendDomainEndMs;
        long visibleDurationMs = end - trendDomainStartMs;
        long rightPaddingMs = PerformanceTrendAxisConfigurer.domainRightPaddingMs(visibleDurationMs);
        DateRange range = new DateRange(new Date(trendDomainStartMs), new Date(end + rightPaddingMs));
        for (TrendView trendView : trendViews) {
            trendView.setDomainRange(range, visibleDurationMs);
        }
    }

    private static void setDomainRange(ChartPanel chartPanel,
                                       DateRange range,
                                       long visibleDurationMs) {
        if (chartPanel == null || chartPanel.getChart() == null) {
            return;
        }
        XYPlot plot = chartPanel.getChart().getXYPlot();
        if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
            dateAxis.setAutoRange(false);
            dateAxis.setRange(range, false, true);
            PerformanceTrendAxisConfigurer.configureTimeAxis(dateAxis, visibleDurationMs);
        }
    }

    private void showChartMode(String mode) {
        chartMode = mode;
        for (TrendView trendView : trendViews) {
            trendView.showChartMode(mode);
        }
    }

    private final class TrendView {
        private final JPanel panel;
        private final JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        private final JPanel splitChartsPanel = new ScrollableChartGridPanel();
        private final JPanel chartCards = new JPanel(new CardLayout());
        private final TimeSeriesCollection combinedDataset = new TimeSeriesCollection();
        private final ChartPanel combinedChartPanel;
        private final List<SeriesControl> controls = new ArrayList<>();
        private final List<SplitChart> splitCharts = new ArrayList<>();

        private TrendView(String titleKey, SeriesSpec... specs) {
            panel = new JPanel(new BorderLayout(8, 0));
            ToolWindowSurfaceStyle.applyCard(panel);
            panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

            controlsPanel.setOpaque(false);
            for (SeriesSpec spec : specs) {
                JCheckBox checkBox = new JCheckBox(spec.series().getKey().toString(), spec.selected());
                checkBox.setOpaque(false);
                checkBox.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
                checkBox.addActionListener(e -> {
                    if (selectedCount() == 0) {
                        checkBox.setSelected(true);
                    }
                    rebuildCharts();
                });
                controls.add(new SeriesControl(spec, checkBox));
                controlsPanel.add(checkBox);
                splitCharts.add(new SplitChart(spec, createSplitChartPanel(spec)));
            }

            JScrollPane splitScrollPane = new JScrollPane(splitChartsPanel);
            ToolWindowSurfaceStyle.applyScrollPaneCard(splitScrollPane);
            splitScrollPane.getVerticalScrollBar().setUnitIncrement(16);

            combinedChartPanel = createChartPanel(combinedDataset, titleKey);
            ToolWindowSurfaceStyle.applyCard(chartCards);
            chartCards.add(splitScrollPane, SEPARATE_VIEW);
            chartCards.add(combinedChartPanel, COMBINED_VIEW);

            panel.add(chartCards, BorderLayout.CENTER);
            rebuildCharts();
            showChartMode(chartMode);
        }

        private JPanel panel() {
            return panel;
        }

        private JPanel controlsPanel() {
            return controlsPanel;
        }

        private int selectedCount() {
            int count = 0;
            for (SeriesControl control : controls) {
                if (control.checkBox().isSelected()) {
                    count++;
                }
            }
            return count;
        }

        private void rebuildCharts() {
            rebuildCombinedChart();
            rebuildSplitCharts();
        }

        private void rebuildCombinedChart() {
            combinedDataset.removeAllSeries();
            XYLineAndShapeRenderer renderer = createTrendRenderer();
            int visibleIndex = 0;
            for (SeriesControl control : controls) {
                if (!control.checkBox().isSelected()) {
                    continue;
                }
                combinedDataset.addSeries(control.spec().series());
                renderer.setSeriesPaint(visibleIndex++, control.spec().color());
            }
            combinedChartPanel.getChart().getXYPlot().setRenderer(renderer);
            combinedChartPanel.repaint();
        }

        private void rebuildSplitCharts() {
            int selectedCount = selectedCount();
            splitChartsPanel.removeAll();
            ToolWindowSurfaceStyle.applyCard(splitChartsPanel);
            splitChartsPanel.setLayout(new GridLayout(0, selectedCount == 1 ? 1 : 2, 12, 12));
            splitChartsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            for (SplitChart splitChart : splitCharts) {
                if (isSelected(splitChart.spec())) {
                    splitChartsPanel.add(splitChart.chartPanel());
                }
            }
            splitChartsPanel.revalidate();
            splitChartsPanel.repaint();
        }

        private boolean isSelected(SeriesSpec spec) {
            for (SeriesControl control : controls) {
                if (control.spec() == spec) {
                    return control.checkBox().isSelected();
                }
            }
            return false;
        }

        private ChartPanel createSplitChartPanel(SeriesSpec spec) {
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(spec.series());
            ChartPanel chartPanel = createChartPanel(dataset, spec.series().getKey().toString(), false, spec.axisFormat());
            chartPanel.setPreferredSize(new Dimension(420, 220));
            XYLineAndShapeRenderer renderer = createSplitTrendRenderer(spec);
            renderer.setSeriesPaint(0, spec.color());
            chartPanel.getChart().getXYPlot().setRenderer(renderer);
            return chartPanel;
        }

        private XYLineAndShapeRenderer createSplitTrendRenderer(SeriesSpec spec) {
            if (spec.axisFormat() == AxisFormat.INTEGER) {
                return createActiveCountTrendRenderer();
            }
            return createTrendRenderer();
        }

        private void showChartMode(String mode) {
            CardLayout layout = (CardLayout) chartCards.getLayout();
            layout.show(chartCards, mode);
            chartCards.revalidate();
            chartCards.repaint();
        }

        private void setDomainRange(DateRange range, long visibleDurationMs) {
            PerformanceTrendPanel.setDomainRange(combinedChartPanel, range, visibleDurationMs);
            for (SplitChart splitChart : splitCharts) {
                PerformanceTrendPanel.setDomainRange(splitChart.chartPanel(), range, visibleDurationMs);
            }
        }

        private void resetAxes(long resetTimeMs) {
            PerformanceTrendPanel.resetAxes(combinedChartPanel, resetTimeMs);
            for (SplitChart splitChart : splitCharts) {
                PerformanceTrendPanel.resetAxes(splitChart.chartPanel(), resetTimeMs);
            }
        }
    }

    private record SeriesSpec(TimeSeries series, Color color, boolean selected, AxisFormat axisFormat) {
    }

    private enum AxisFormat {
        DECIMAL,
        INTEGER
    }

    private record SeriesControl(SeriesSpec spec, JCheckBox checkBox) {
    }

    private record SplitChart(SeriesSpec spec, ChartPanel chartPanel) {
    }

    private static final class ScrollableChartGridPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(24, visibleRect.height - 24);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            Container parent = getParent();
            if (parent instanceof JViewport viewport) {
                return getPreferredSize().height <= viewport.getHeight();
            }
            return false;
        }
    }

}
