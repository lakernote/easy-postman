package com.laker.postman.panel.performance.result;

import com.laker.postman.request.model.HttpHeader;



import com.laker.postman.common.component.SearchTextField;
import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.performance.model.PerformanceInternalHeaders;
import com.laker.postman.performance.model.PerformanceProtocolLabels;
import com.laker.postman.performance.model.ResultNodeInfo;
import com.laker.postman.performance.result.PerformanceResultMemoryBudget;
import com.laker.postman.service.render.HttpHtmlRenderer;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.IconUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能测试结果表
 * - 200ms 增量刷新机制
 * - 支持排序和深度搜索过滤
 */
@Slf4j
public class PerformanceResultTablePanel extends JPanel {

    private JTable table;
    private ResultTableModel tableModel;
    private JTabbedPane detailTabs;
    private TableRowSorter<ResultTableModel> rowSorter;
    private JScrollPane tableScroll;
    private JPanel contentHolder;
    private JSplitPane detailSplit;
    private JPanel detailPanel;
    private JLabel detailTitleLabel;
    private JLabel detailSummaryLabel;
    private JLabel rowCountLabel;
    private JCheckBox failedOnlyCheckBox;
    private int lastDividerLocation = -1;

    private SearchTextField searchField;

    static final int COMPACT_RESULT_ROW_LIMIT = 1_000;

    private final Queue<PendingResult> pendingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingResultCount = new AtomicInteger(0);
    private final AtomicLong pendingResultBytes = new AtomicLong(0L);
    private final long retentionBudgetBytes = PerformanceResultMemoryBudget.defaultBudgetBytes();

    private static final int BATCH_SIZE = 2000;

    // 搜索防抖定时器（300ms）
    private Timer searchDebounceTimer;

    // UI 帧刷新定时器（200ms）
    private final Timer uiFrameTimer = new Timer(200, e -> {

        flushQueueOnEDT();
        tableModel.flushIfDirty();
    });

    public PerformanceResultTablePanel() {
        initUI();
        registerListeners();
        uiFrameTimer.start();
    }

    // 批量刷新队列数据到 TableModel
    private void flushQueueOnEDT() {
        List<PendingResult> batch = new ArrayList<>(1024);
        PendingResult pending;

        while ((pending = pendingQueue.poll()) != null) {
            pendingResultCount.decrementAndGet();
            pendingResultBytes.addAndGet(-pending.estimatedBytes());
            batch.add(pending);
            if (batch.size() >= BATCH_SIZE) break;
        }

        if (!batch.isEmpty()) {
            tableModel.append(batch);
        }
    }

    /**
     * 强制刷新所有待处理的结果（测试停止/完成时调用）
     * 确保 pendingQueue 中的所有数据都显示到表格中
     */
    public void flushPendingResults() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::flushPendingResults);
            return;
        }

        List<PendingResult> batch = new ArrayList<>();
        PendingResult pending;

        // 一次性刷新所有待处理的结果
        while ((pending = pendingQueue.poll()) != null) {
            pendingResultCount.decrementAndGet();
            pendingResultBytes.addAndGet(-pending.estimatedBytes());
            batch.add(pending);
        }

        if (!batch.isEmpty()) {
            tableModel.append(batch);
            log.debug("强制刷新了 {} 条待处理的结果到结果树", batch.size());
        }
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 6));
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        searchField = new SearchTextField();
        searchField.setPlaceholderText(I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_SEARCH_PLACEHOLDER));

        failedOnlyCheckBox = new JCheckBox(I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_FAILED_ONLY));
        failedOnlyCheckBox.setOpaque(false);
        failedOnlyCheckBox.setFocusable(false);

        rowCountLabel = new JLabel();
        rowCountLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        rowCountLabel.setForeground(PerformanceTheme.resultMutedForeground());

        JPanel filterActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filterActions.setOpaque(false);
        filterActions.add(failedOnlyCheckBox);
        filterActions.add(rowCountLabel);

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(filterActions, BorderLayout.EAST);

        tableModel = new ResultTableModel(retentionBudgetBytes);
        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setFocusable(false);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setGridColor(PerformanceTheme.resultGridColor());
        ResultRowRenderer rowRenderer = new ResultRowRenderer();
        table.setDefaultRenderer(Object.class, rowRenderer);
        // 数值列也走同一渲染器，否则 JTable 默认 NumberRenderer 会绕过格式化和配色。
        table.setDefaultRenderer(Number.class, rowRenderer);

        rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);
        configureSorterComparators();
        configureColumnWidths();

        tableScroll = new JScrollPane(table);
        ToolWindowSurfaceStyle.applyTableScrollPaneCard(tableScroll, table);
        tableScroll.setMinimumSize(new Dimension(tableMinimumWidth(), 150));

        detailPanel = createDetailPanel();

        detailSplit = AppToolWindowChrome.createHorizontalInnerSplitPane(new JPanel(), new JPanel(), 0);
        detailSplit.setResizeWeight(0.5);

        // 默认只显示全宽表格；选中行后才展开右侧详情。
        contentHolder = new JPanel(new BorderLayout());
        contentHolder.setOpaque(false);
        contentHolder.add(tableScroll, BorderLayout.CENTER);

        add(searchPanel, BorderLayout.NORTH);
        add(contentHolder, BorderLayout.CENTER);
        updateRowCount();
    }

    private JPanel createDetailPanel() {
        detailTitleLabel = new JLabel();
        detailTitleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 0));
        detailSummaryLabel = new JLabel();
        detailSummaryLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        detailSummaryLabel.setForeground(PerformanceTheme.resultMutedForeground());

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(detailTitleLabel);
        titles.add(detailSummaryLabel);

        JButton closeButton = new JButton(IconUtil.createThemed("icons/close.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        closeButton.setToolTipText(I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_DETAIL_CLOSE));
        closeButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        closeButton.setFocusable(false);
        closeButton.addActionListener(e -> table.clearSelection());

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 2));
        header.add(titles, BorderLayout.CENTER);
        header.add(closeButton, BorderLayout.EAST);

        detailTabs = new JTabbedPane();
        ToolWindowSurfaceStyle.applyTabbedPaneCard(detailTabs);

        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        panel.setMinimumSize(new Dimension(320, 150));
        panel.add(header, BorderLayout.NORTH);
        panel.add(detailTabs, BorderLayout.CENTER);
        panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeResultDetail");
        panel.getActionMap().put("closeResultDetail", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.clearSelection();
            }
        });
        return panel;
    }

    boolean isDetailVisible() {
        return detailPanel.getParent() == detailSplit && detailSplit.getParent() == contentHolder;
    }

    private void showDetailPanel() {
        if (isDetailVisible()) {
            return;
        }
        detailSplit.setLeftComponent(tableScroll);
        detailSplit.setRightComponent(detailPanel);
        contentHolder.removeAll();
        contentHolder.add(detailSplit, BorderLayout.CENTER);
        int width = contentHolder.getWidth();
        int minTableWidth = tableScroll.getMinimumSize().width;
        int location = lastDividerLocation > 0 && (width <= 0 || lastDividerLocation < width - 320)
                ? lastDividerLocation
                : Math.max(minTableWidth, (int) (width * 0.58));
        detailSplit.setDividerLocation(location);
        contentHolder.revalidate();
        contentHolder.repaint();
    }

    private void hideDetailPanel() {
        if (!isDetailVisible()) {
            return;
        }
        lastDividerLocation = detailSplit.getDividerLocation();
        contentHolder.removeAll();
        contentHolder.add(tableScroll, BorderLayout.CENTER);
        detailSplit.setRightComponent(null);
        contentHolder.revalidate();
        contentHolder.repaint();
    }

    // 配置排序比较器
    private void configureSorterComparators() {
        rowSorter.setComparator(ResultTableModel.COL_PROTOCOL, Comparator.comparing(String::toString, String.CASE_INSENSITIVE_ORDER));
        rowSorter.setComparator(ResultTableModel.COL_NAME, Comparator.comparing(String::toString, String.CASE_INSENSITIVE_ORDER));
        // 状态码按数值排序
        rowSorter.setComparator(ResultTableModel.COL_STATUS, Comparator.comparing((Object o) -> {
            String s = o.toString();
            if ("-".equals(s)) return 0;
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }));
        rowSorter.setComparator(ResultTableModel.COL_START_TIME, Comparator.comparingLong(Long.class::cast));
        rowSorter.setComparator(ResultTableModel.COL_COST, Comparator.comparingInt(Integer.class::cast));
        rowSorter.setComparator(ResultTableModel.COL_LATENCY, Comparator.comparingLong(Long.class::cast));
        rowSorter.setComparator(ResultTableModel.COL_CONNECT, Comparator.comparingLong(Long.class::cast));
        rowSorter.setComparator(ResultTableModel.COL_ASSERTION, Comparator.comparing(String::toString));
    }

    // 配置列宽度
    private void configureColumnWidths() {
        configureColumnWidth(ResultTableModel.COL_PROTOCOL, 64, 72, 95);
        configureColumnWidth(ResultTableModel.COL_NAME, 120, 200, Integer.MAX_VALUE);
        configureColumnWidth(ResultTableModel.COL_STATUS, 56, 64, 90);
        configureColumnWidth(ResultTableModel.COL_START_TIME, 92, 100, 120);
        configureColumnWidth(ResultTableModel.COL_COST, 72, 100, 130);
        configureColumnWidth(ResultTableModel.COL_LATENCY, 72, 92, 120);
        configureColumnWidth(ResultTableModel.COL_CONNECT, 64, 84, 120);
        configureColumnWidth(ResultTableModel.COL_ASSERTION, 56, 64, 100);
    }

    private void configureColumnWidth(int column, int min, int preferred, int max) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        // 表头文案（尤其英文）不能被截断，最小宽度至少容纳表头。
        int headerWidth = headerPreferredWidth(tableColumn);
        int effectiveMin = Math.max(min, headerWidth);
        tableColumn.setMinWidth(effectiveMin);
        tableColumn.setMaxWidth(Math.max(max, effectiveMin));
        tableColumn.setPreferredWidth(Math.max(preferred, effectiveMin));
    }

    private int headerPreferredWidth(TableColumn tableColumn) {
        javax.swing.table.TableCellRenderer renderer = tableColumn.getHeaderRenderer();
        if (renderer == null) {
            renderer = table.getTableHeader().getDefaultRenderer();
        }
        Component header = renderer.getTableCellRendererComponent(
                table, tableColumn.getHeaderValue(), false, false, -1, tableColumn.getModelIndex());
        return header.getPreferredSize().width + 24;
    }

    private int tableMinimumWidth() {
        int width = 0;
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            width += table.getColumnModel().getColumn(i).getMinWidth();
        }
        return width + 4;
    }

    private void registerListeners() {
        // 选择监听器 - 显示详情
        table.getSelectionModel().addListSelectionListener(this::onRowSelected);
        tableModel.addTableModelListener(e -> SwingUtilities.invokeLater(this::updateRowCount));
        rowSorter.addRowSorterListener(e -> updateRowCount());
        failedOnlyCheckBox.addActionListener(e -> doFilter());


        // 搜索防抖
        searchDebounceTimer = new Timer(300, e -> doFilter());
        searchDebounceTimer.setRepeats(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleFilter();
            }

            private void scheduleFilter() {
                searchDebounceTimer.restart();
            }
        });

        // Enter 键立即过滤
        searchField.addActionListener(e -> {
            searchDebounceTimer.stop();
            doFilter();
        });
    }


    /**
     * 执行过滤
     */
    private void doFilter() {
        String text = searchField.getText();
        String keyword = text == null ? "" : text.trim().toLowerCase();
        List<RowFilter<ResultTableModel, Integer>> filters = new ArrayList<>(2);
        if (!keyword.isEmpty()) {
            filters.add(new ResultRowFilter(keyword));
        }
        if (failedOnlyCheckBox.isSelected()) {
            filters.add(new FailedOnlyRowFilter());
        }
        rowSorter.setRowFilter(filters.isEmpty() ? null
                : filters.size() == 1 ? filters.get(0) : RowFilter.andFilter(filters));
        // 关键字过滤后无可见行时变红
        searchField.setNoResult(!keyword.isEmpty() && table.getRowCount() == 0);
    }

    private void updateRowCount() {
        int total = tableModel.getRowCount();
        int visible = table.getRowCount();
        rowCountLabel.setText(visible == total
                ? I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_ROW_COUNT_TOTAL, String.valueOf(total))
                : I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_ROW_COUNT_FILTERED,
                String.valueOf(visible), String.valueOf(total)));
    }

    private void onRowSelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        int row = table.getSelectedRow();
        if (row < 0) {
            clearDetailTabs();
            hideDetailPanel();
            return;
        }

        // 转换视图索引到模型索引
        int modelRow = table.convertRowIndexToModel(row);
        ResultNodeInfo info = tableModel.getRow(modelRow);
        if (info == null) {
            return;
        }
        renderDetail(info);
        showDetailPanel();
    }

    public void addResult(ResultNodeInfo info) {
        addResult(info, false);
    }

    public void addResult(ResultNodeInfo info, boolean compactRetention) {
        if (info == null) {
            return;
        }
        int rowLimit = compactRetention
                ? COMPACT_RESULT_ROW_LIMIT
                : SettingManager.getPerformanceResultRowLimit();
        long estimatedBytes = PerformanceResultMemoryBudget.estimateBytes(info);
        pendingQueue.offer(new PendingResult(info, rowLimit, estimatedBytes));
        pendingResultCount.incrementAndGet();
        pendingResultBytes.addAndGet(estimatedBytes);
        trimPendingQueueForRetention(rowLimit);
    }

    public void clearResults() {
        pendingQueue.clear();
        pendingResultCount.set(0);
        pendingResultBytes.set(0L);
        table.clearSelection();
        tableModel.clear();
        clearDetailTabs();
        hideDetailPanel();
    }

    private void trimPendingQueueForRetention(int rowLimit) {
        int effectiveLimit = Math.max(1, rowLimit);
        while (pendingResultCount.get() > effectiveLimit
                || (pendingResultCount.get() > 1 && pendingResultBytes.get() > retentionBudgetBytes)) {
            PendingResult removed = pendingQueue.poll();
            if (removed == null) {
                pendingResultCount.set(0);
                pendingResultBytes.set(0L);
                return;
            }
            pendingResultCount.decrementAndGet();
            pendingResultBytes.addAndGet(-removed.estimatedBytes());
        }
    }

    private void renderDetail(ResultNodeInfo info) {
        renderDetailHeader(info);
        detailTabs.removeAll();

        int responseTabIndex = -1;

        // 1. Request 标签页（始终显示）
        if (info.req != null) {
            addTabWithContent(
                    I18nUtil.getMessage(MessageKeys.PERFORMANCE_TAB_REQUEST),
                    HttpHtmlRenderer.renderRequest(info.req)
            );
        }

        // 2. Response 标签页（始终显示，整合响应数据和错误信息）
        String responseHtml = HttpHtmlRenderer.renderResponseWithError(info);
        addTabWithContent(
                I18nUtil.getMessage(MessageKeys.PERFORMANCE_TAB_RESPONSE),
                responseHtml
        );
        responseTabIndex = detailTabs.getTabCount() - 1;

        // 3. Tests 标签页（仅在有断言结果时显示）
        if (info.testResults != null && !info.testResults.isEmpty()) {
            addTabWithContent(
                    I18nUtil.getMessage(MessageKeys.PERFORMANCE_TAB_TESTS),
                    HttpHtmlRenderer.renderTestResults(info.testResults)
            );
        }

        // 4. Timing 标签页（仅在有事件信息时显示）
        // 5. Event Info 标签页（仅在有事件信息时显示）
        if (info.resp != null && info.resp.httpEventInfo != null) {
            addTabWithContent(
                    I18nUtil.getMessage(MessageKeys.PERFORMANCE_TAB_TIMING),
                    HttpHtmlRenderer.renderTimingInfo(info.resp)
            );
            addTabWithContent(
                    I18nUtil.getMessage(MessageKeys.PERFORMANCE_TAB_EVENT_INFO),
                    HttpHtmlRenderer.renderEventInfo(info.resp)
            );
        }

        // 如果有错误信息，自动切换到 Response 标签页
        if (info.errorMsg != null && !info.errorMsg.isEmpty() && responseTabIndex >= 0) {
            detailTabs.setSelectedIndex(responseTabIndex);
        }
    }

    /**
     * 添加标签页并设置内容
     */
    private void addTabWithContent(String title, String html) {
        JEditorPane pane = new JEditorPane("text/html", "");
        pane.setEditable(false);
        pane.setText(html);
        pane.setCaretPosition(0);
        ToolWindowSurfaceStyle.applyTextComponentCard(pane);
        JScrollPane scrollPane = new JScrollPane(pane);
        ToolWindowSurfaceStyle.applyScrollPaneCard(scrollPane);
        detailTabs.addTab(title, scrollPane);
    }

    private void renderDetailHeader(ResultNodeInfo info) {
        String url = info.req != null ? info.req.url : null;
        String method = info.req != null ? info.req.method : null;
        detailTitleLabel.setText(info.name == null || info.name.isBlank() ? "-" : info.name);
        detailTitleLabel.setForeground(!info.cancelled && !info.isActuallySuccessful()
                ? PerformanceTheme.resultFailureForeground()
                : PerformanceTheme.tableForeground());
        detailTitleLabel.setToolTipText(url == null ? null : (method == null ? "" : method + " ") + url);
        detailSummaryLabel.setText(buildDetailSummary(info));
    }

    static String buildDetailSummary(ResultNodeInfo info) {
        String status = info.cancelled
                ? I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_CANCELLED)
                : info.responseCode > 0 ? String.valueOf(info.responseCode) : "-";
        return I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_DETAIL_SUMMARY,
                status,
                info.costMs + " ms",
                formatMillisWithUnit(info.timing.latencyMs()),
                formatMillisWithUnit(info.timing.connectMs()),
                ResultRowRenderer.formatStartTime(info.timing.startTimeMs()));
    }

    private static String formatMillisWithUnit(long millis) {
        return millis >= 0 ? millis + " ms" : "—";
    }

    private void clearDetailTabs() {
        detailTabs.removeAll();
    }

    // 资源清理
    public void dispose() {
        uiFrameTimer.stop();
        if (searchDebounceTimer != null) {
            searchDebounceTimer.stop();
        }
    }

    static class FailedOnlyRowFilter extends RowFilter<ResultTableModel, Integer> {
        @Override
        public boolean include(Entry<? extends ResultTableModel, ? extends Integer> entry) {
            ResultNodeInfo info = entry.getModel().getRow(entry.getIdentifier());
            return info != null && !info.cancelled && !info.isActuallySuccessful();
        }
    }

    // 自定义 RowFilter - 支持深度搜索
    static class ResultRowFilter extends RowFilter<ResultTableModel, Integer> {
        private final String keyword;

        public ResultRowFilter(String keyword) {
            this.keyword = keyword;
        }

        @Override
        public boolean include(Entry<? extends ResultTableModel, ? extends Integer> entry) {
            ResultTableModel model = entry.getModel();
            int row = entry.getIdentifier();
            ResultNodeInfo info = model.getRow(row);

            if (info == null) return false;

            // 1. 检查接口名称
            if (info.name != null && info.name.toLowerCase().contains(keyword)) {
                return true;
            }

            // 2. 检查用户可见错误
            if (info.errorMsg != null && info.errorMsg.toLowerCase().contains(keyword)) {
                return true;
            }

            // 3. 检查请求内容（URL、Headers、Body）
            if (matchesRequest(info)) {
                return true;
            }

            // 4. 检查响应内容（Headers、Body）
            return matchesResponse(info);
        }

        // 检查请求内容是否匹配关键字
        private boolean matchesRequest(ResultNodeInfo info) {
            if (info.req == null) return false;

            // 检查 URL
            if (info.req.url != null && info.req.url.toLowerCase().contains(keyword)) {
                return true;
            }

            // 检查请求 Headers
            if (info.req.headersList != null) {
                for (HttpHeader header : info.req.headersList) {
                    if (!header.isEnabled()) continue;
                    String headerStr = (header.getKey() + ": " + header.getValue()).toLowerCase();
                    if (headerStr.contains(keyword)) {
                        return true;
                    }
                }
            }

            // 检查请求 Body
            return info.req.body != null && info.req.body.toLowerCase().contains(keyword);
        }

        // 检查响应内容是否匹配关键字
        private boolean matchesResponse(ResultNodeInfo info) {
            if (info.resp == null) return false;

            // 检查响应 Headers
            if (info.resp.headers != null) {
                for (var entry : info.resp.headers.entrySet()) {
                    String key = entry.getKey();
                    if (PerformanceInternalHeaders.isInternalHeader(key)) {
                        continue;
                    }
                    List<String> values = entry.getValue();
                    String headerStr = (key + ": " + String.join(", ", values)).toLowerCase();
                    if (headerStr.contains(keyword)) {
                        return true;
                    }
                }
            }

            // 检查响应 Body
            return info.resp.body != null && info.resp.body.toLowerCase().contains(keyword);
        }

    }

    // TableModel - 增量刷新优化
    record PendingResult(ResultNodeInfo info, int rowLimit, long estimatedBytes) {
    }

    static class ResultTableModel extends AbstractTableModel {

        static final int COL_PROTOCOL = 0;
        static final int COL_NAME = 1;
        static final int COL_STATUS = 2;
        static final int COL_START_TIME = 3;
        static final int COL_COST = 4;
        static final int COL_LATENCY = 5;
        static final int COL_CONNECT = 6;
        static final int COL_ASSERTION = 7;
        static final int COLUMN_COUNT = 8;

        private final List<ResultNodeInfo> dataList = new ArrayList<>(1024);
        private final List<Long> rowBytes = new ArrayList<>(1024);
        private final long retentionBudgetBytes;
        private long retainedBytes = 0L;
        private boolean dirty = false;
        private boolean fullRefreshRequired = false;

        // 追踪新增行的起始位置，用于增量刷新
        private int firstNewRow = -1;

        ResultTableModel() {
            this(PerformanceResultMemoryBudget.defaultBudgetBytes());
        }

        ResultTableModel(long retentionBudgetBytes) {
            this.retentionBudgetBytes = Math.max(1L, retentionBudgetBytes);
        }

        long retainedBytes() {
            return retainedBytes;
        }

        @Override
        public int getRowCount() {
            return dataList.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_COUNT;
        }

        @Override
        public String getColumnName(int col) {
            return switch (col) {
                case COL_PROTOCOL -> I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_COLUMN_PROTOCOL);
                case COL_NAME -> I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_COLUMN_NAME);
                case COL_STATUS -> I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_COLUMN_STATUS);
                case COL_START_TIME -> I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_COLUMN_START_TIME);
                case COL_COST -> I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_COLUMN_COST);
                case COL_LATENCY -> I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_COLUMN_LATENCY);
                case COL_CONNECT -> I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_COLUMN_CONNECT);
                case COL_ASSERTION -> I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_TREE_COLUMN_ASSERTION);
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int row, int col) {
            ResultNodeInfo r = dataList.get(row);
            return switch (col) {
                case COL_PROTOCOL -> PerformanceProtocolLabels.displayName(r.protocol);
                case COL_NAME -> r.name;
                case COL_STATUS -> r.cancelled
                        ? I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_CANCELLED)
                        : r.responseCode > 0 ? String.valueOf(r.responseCode) : "-";
                case COL_START_TIME -> r.timing.startTimeMs();
                case COL_COST -> r.costMs;
                case COL_LATENCY -> r.timing.latencyMs();
                case COL_CONNECT -> r.timing.connectMs();
                case COL_ASSERTION -> formatAssertion(r);
                default -> "";
            };
        }

        // Keep assertion symbols themeable; color comes from ResultRowRenderer.
        private String formatAssertion(ResultNodeInfo r) {
            if (r.cancelled) {
                return "—";
            }
            // 1. 如果有断言结果
            if (r.testResults != null && !r.testResults.isEmpty()) {
                return r.hasAssertionFailed() ? "×" : "✓";
            }

            // 2. 无断言测试
            if (!r.isActuallySuccessful()) {
                return "×";
            }

            return "—";
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case COL_PROTOCOL -> String.class;
                case COL_NAME -> String.class;
                case COL_STATUS -> String.class;
                case COL_START_TIME, COL_LATENCY, COL_CONNECT -> Long.class;
                case COL_COST -> Integer.class;
                case COL_ASSERTION -> String.class;
                default -> Object.class;
            };
        }

        ResultNodeInfo getRow(int row) {
            if (row < 0 || row >= dataList.size()) {
                return null;
            }
            return dataList.get(row);
        }


        void append(List<PendingResult> batch) {
            if (batch.isEmpty()) return;

            // 记录新增行的起始位置
            if (firstNewRow == -1) {
                firstNewRow = dataList.size();
            }

            for (PendingResult pending : batch) {
                dataList.add(pending.info());
                rowBytes.add(pending.estimatedBytes());
                retainedBytes += pending.estimatedBytes();
                trimRowsForRetention(pending.rowLimit());
            }
            dirty = true;
        }

        void flushIfDirty() {
            if (!dirty) return;
            dirty = false;

            if (fullRefreshRequired) {
                fullRefreshRequired = false;
                firstNewRow = -1;
                fireTableDataChanged();
                return;
            }

            // 增量刷新：仅通知新增的行
            if (firstNewRow != -1 && firstNewRow < dataList.size()) {
                int lastRow = dataList.size() - 1;
                fireTableRowsInserted(firstNewRow, lastRow);
                firstNewRow = -1; // 重置
            }
        }

        void clear() {
            dataList.clear();
            rowBytes.clear();
            retainedBytes = 0L;
            dirty = false;
            fullRefreshRequired = false;
            firstNewRow = -1; // 重置
            fireTableDataChanged();
        }

        private void trimRowsForRetention(int rowLimit) {
            int effectiveLimit = Math.max(1, rowLimit);
            int overflow = Math.max(0, dataList.size() - effectiveLimit);
            long bytesAfterRowTrim = retainedBytes;
            for (int i = 0; i < overflow; i++) {
                bytesAfterRowTrim -= rowBytes.get(i);
            }
            // Evict the oldest rows by bytes too, always keeping the newest row visible.
            while (bytesAfterRowTrim > retentionBudgetBytes && overflow < dataList.size() - 1) {
                bytesAfterRowTrim -= rowBytes.get(overflow);
                overflow++;
            }
            if (overflow > 0) {
                dataList.subList(0, overflow).clear();
                rowBytes.subList(0, overflow).clear();
                retainedBytes = bytesAfterRowTrim;
                fullRefreshRequired = true;
                firstNewRow = -1;
            }
        }
    }

    // 行渲染器 - 设置不同列的对齐方式和颜色
    static class ResultRowRenderer extends DefaultTableCellRenderer {
        private static final DateTimeFormatter START_TIME_FORMATTER =
                DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setFont(table.getFont());

            // 获取列索引
            int modelColumn = table.convertColumnIndexToModel(column);
            ResultNodeInfo info = getRowInfo(table, row);

            setBorder(createCellBorder(modelColumn, info, isSelected));
            setToolTipText(null);
            if (!isSelected) {
                setBackground(row % 2 == 1 ? alternateRowBackground(table) : table.getBackground());
                setForeground(table.getForeground());
            }

            // 设置对齐方式和颜色
            switch (modelColumn) {
                case ResultTableModel.COL_NAME -> {
                    setHorizontalAlignment(SwingConstants.LEFT);
                    if (info != null && info.req != null && info.req.url != null) {
                        setToolTipText(info.req.method == null ? info.req.url : info.req.method + " " + info.req.url);
                    }
                    if (!isSelected && info != null && !info.cancelled && !info.isActuallySuccessful()) {
                        setForeground(PerformanceTheme.resultFailureForeground());
                        setFont(table.getFont().deriveFont(Font.BOLD));
                    }
                }
                case ResultTableModel.COL_STATUS -> {
                    setHorizontalAlignment(SwingConstants.CENTER);
                    if (!isSelected && value != null && !"-".equals(value)) {
                        applyStatusColors(this, value.toString());
                    }
                }
                case ResultTableModel.COL_START_TIME -> {
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setText(formatStartTime(value));
                }
                case ResultTableModel.COL_COST -> setHorizontalAlignment(SwingConstants.RIGHT);
                case ResultTableModel.COL_LATENCY, ResultTableModel.COL_CONNECT -> {
                    setHorizontalAlignment(SwingConstants.RIGHT);
                    setText(formatMillis(value));
                }
                case ResultTableModel.COL_PROTOCOL, ResultTableModel.COL_ASSERTION -> {
                    setHorizontalAlignment(SwingConstants.CENTER);
                    if (modelColumn == ResultTableModel.COL_ASSERTION && !isSelected && info != null) {
                        applyAssertionColors(this, info);
                    }
                }
                default -> setHorizontalAlignment(SwingConstants.LEFT);
            }

            return this;
        }

        private static Color alternateRowBackground(JTable table) {
            Color alternate = UIManager.getColor("Table.alternateRowColor");
            if (alternate != null) {
                return alternate;
            }
            Color bg = table.getBackground();
            Color fg = table.getForeground();
            float ratio = 0.035f;
            return new Color(
                    Math.round(bg.getRed() + (fg.getRed() - bg.getRed()) * ratio),
                    Math.round(bg.getGreen() + (fg.getGreen() - bg.getGreen()) * ratio),
                    Math.round(bg.getBlue() + (fg.getBlue() - bg.getBlue()) * ratio));
        }

        static String formatStartTime(Object value) {
            if (value instanceof Long time && time > 0) {
                return START_TIME_FORMATTER.format(Instant.ofEpochMilli(time));
            }
            return "—";
        }

        static String formatMillis(Object value) {
            return value instanceof Long millis && millis >= 0 ? String.valueOf(millis) : "—";
        }

        private ResultNodeInfo getRowInfo(JTable table, int viewRow) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow < 0) {
                return null;
            }
            if (!(table.getModel() instanceof ResultTableModel model)) {
                return null;
            }
            return model.getRow(modelRow);
        }

        private javax.swing.border.Border createCellBorder(int modelColumn, ResultNodeInfo info, boolean isSelected) {
            int leftInset = modelColumn == ResultTableModel.COL_NAME ? 8 : 6;

            if (modelColumn == ResultTableModel.COL_NAME && info != null && !isSelected) {
                Color stripeColor = info.cancelled ? PerformanceTheme.resultMutedForeground()
                        : info.isActuallySuccessful() ? PerformanceTheme.resultSuccessStripe()
                        : PerformanceTheme.resultFailureStripe();
                return BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, stripeColor),
                        BorderFactory.createEmptyBorder(0, leftInset, 0, 6)
                );
            }

            return BorderFactory.createEmptyBorder(0, leftInset, 0, 6);
        }

        /**
         * 根据状态码应用颜色 - 参考 FunctionalRunnerTableModel
         */
        private void applyStatusColors(Component c, String status) {
            Color foreground = PerformanceTheme.tableForeground();
            if (I18nUtil.getMessage(MessageKeys.PERFORMANCE_RESULT_CANCELLED).equals(status)) {
                c.setForeground(PerformanceTheme.resultMutedForeground());
                return;
            }

            try {
                int code = Integer.parseInt(status);
                if (code >= 200 && code < 300) {
                    // 成功：使用绿色
                    foreground = PerformanceTheme.resultSuccessForeground();
                } else if (code >= 400 && code < 500) {
                    // 客户端错误：使用警告色
                    foreground = PerformanceTheme.resultWarningForeground();
                } else if (code >= 500) {
                    // 服务器错误：使用错误色
                    foreground = PerformanceTheme.resultFailureForeground();
                }
            } catch (NumberFormatException e) {
                // 非数字状态（如错误消息）
                foreground = PerformanceTheme.resultFailureForeground();
            }

            // 只设置文字颜色
            c.setForeground(foreground);
        }

        private void applyAssertionColors(Component c, ResultNodeInfo info) {
            if (info.cancelled) {
                c.setForeground(PerformanceTheme.resultMutedForeground());
                return;
            }
            if (info.testResults == null || info.testResults.isEmpty()) {
                c.setForeground(info.isActuallySuccessful()
                        ? PerformanceTheme.resultMutedForeground()
                        : PerformanceTheme.resultFailureForeground());
                return;
            }
            c.setForeground(info.hasAssertionFailed()
                    ? PerformanceTheme.resultFailureForeground()
                    : PerformanceTheme.resultSuccessForeground());
        }
    }
}
