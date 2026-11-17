package com.laker.postman.panel.functional;

import com.laker.postman.common.component.notification.NotificationCenter;

import com.laker.postman.functional.model.AssertionResult;
import com.laker.postman.functional.model.BatchExecutionHistory;
import com.laker.postman.functional.execution.FunctionalRequestExecutionResult;
import com.laker.postman.functional.execution.FunctionalRequestExecutor;
import com.laker.postman.functional.model.IterationResult;
import com.laker.postman.functional.model.RequestResult;
import com.laker.postman.functional.model.RunnerRowData;
import com.laker.postman.request.model.HttpRequestItem;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.DebouncedSaveSupport;
import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.CsvDataPanel;
import com.laker.postman.common.component.ToolWindowActionToolbar;
import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.*;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.functional.model.FunctionalConfigRow;
import com.laker.postman.functional.model.FunctionalConfigSnapshot;
import com.laker.postman.functional.model.FunctionalCsvDataState;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.panel.collections.RequestSelectionDialogSupport;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.panel.functional.table.FunctionalRunnerTableModel;
import com.laker.postman.panel.functional.table.TableRowTransferHandler;
import com.laker.postman.panel.sidebar.SidebarTabPanel;
import com.laker.postman.service.FunctionalPersistenceService;
import com.laker.postman.common.component.HttpRequestDisplayMetadata;
import com.laker.postman.service.collections.CollectionRequestLookup;
import com.laker.postman.service.collections.RequestSaveEventPublisher;
import com.laker.postman.service.variable.ExecutionVariableContext;
import com.laker.postman.service.variable.IterationDataRuntimeSupport;
import com.laker.postman.util.*;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
public class FunctionalPanel extends UiSingletonPanel {
    public static final String ERROR = "Error";
    private JTable table;
    private FunctionalRunnerTableModel tableModel;
    private StartButton runBtn;
    private StopButton stopBtn;    // 停止按钮
    private JLabel timeLabel;     // 执行时间标签
    private JLabel progressLabel; // 进度标签
    private long startTime;       // 记录开始时间
    private Timer executionTimer; // 执行时间计时器
    private volatile boolean isStopped = false; // 停止标志
    private volatile int executionGeneration = 0;

    // CSV 数据管理面板
    private CsvDataPanel csvDataPanel;

    // 批量执行历史记录
    private transient BatchExecutionHistory executionHistory;
    private JTabbedPane mainTabbedPane;
    private ExecutionResultsPanel resultsPanel;

    // 持久化服务
    private transient FunctionalPersistenceService persistenceService;
    private final transient CollectionRequestLookup requestLookup = new CollectionRequestLookup();
    private final transient DebouncedSaveSupport autoSaveSupport = new DebouncedSaveSupport(500, this::saveAsync);
    private final transient FunctionalRequestExecutor requestExecutor =
            new FunctionalRequestExecutor(error -> com.laker.postman.panel.sidebar.ConsolePanel.appendLog(
                    "[Request Error]\n" + error,
                    com.laker.postman.panel.sidebar.ConsolePanel.LogType.ERROR
            ));


    @Override
    protected void initUI() {
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyBackground(this);
        // 移除硬编码 setPreferredSize，改用最小尺寸约束，让父容器自适应分配
        setMinimumSize(new Dimension(500, 300));

        // 创建主选项卡面板
        mainTabbedPane = new JTabbedPane();
        ToolWindowSurfaceStyle.applyTabbedPaneCard(mainTabbedPane);
        mainTabbedPane.setBorder(BorderFactory.createEmptyBorder());
        mainTabbedPane.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, +1));

        JPanel executionPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(executionPanel);
        executionPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        executionPanel.add(createTopPanel(), BorderLayout.NORTH);
        executionPanel.add(createTablePanel(), BorderLayout.CENTER);
        mainTabbedPane.addTab(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_TAB_REQUEST_CONFIG),
                new FlatSVGIcon("icons/functional.svg", 16, 16)
                        .setColorFilter(new FlatSVGIcon.ColorFilter(color -> ModernColors.getTextPrimary())),
                executionPanel);

        resultsPanel = new ExecutionResultsPanel();
        mainTabbedPane.addTab(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_TAB_EXECUTION_RESULTS),
                new FlatSVGIcon("icons/history.svg", 16, 16)
                        .setColorFilter(new FlatSVGIcon.ColorFilter(color -> ModernColors.getTextPrimary())),
                resultsPanel);

        add(AppToolWindowChrome.wrapInsetToolWindow(mainTabbedPane), BorderLayout.CENTER);

        this.persistenceService = BeanFactory.getBean(FunctionalPersistenceService.class);
        loadSaved();
    }

    private JPanel createTopPanel() {
        // 改用 FlowLayout 弹性布局，避免固定高度
        JPanel topPanel = new JPanel(new BorderLayout(6, 0));
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        topPanel.setOpaque(false);

        // 初始化 CSV 数据面板
        csvDataPanel = new CsvDataPanel();
        csvDataPanel.setChangeListener(this::scheduleSave);

        // 左侧按钮组
        topPanel.add(createButtonPanel(), BorderLayout.WEST);

        // 中间 CSV 状态面板（无数据时自然折叠）
        topPanel.add(csvDataPanel, BorderLayout.CENTER);

        // 右侧：执行时间 + 进度（紧凑排列）
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        // 执行时间
        timeLabel = new JLabel("0 ms");
        timeLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD));
        JLabel timeIcon = new JLabel(new FlatSVGIcon("icons/time.svg", 16, 16)
                .setColorFilter(new FlatSVGIcon.ColorFilter(color -> ModernColors.getTextPrimary())));
        rightPanel.add(timeIcon);
        rightPanel.add(timeLabel);

        // 分隔符
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 16));
        rightPanel.add(sep);

        // 进度
        progressLabel = new JLabel("0/0");
        progressLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD));
        JLabel taskIcon = new JLabel(new FlatSVGIcon("icons/functional.svg", 16, 16)
                .setColorFilter(new FlatSVGIcon.ColorFilter(color -> ModernColors.getTextPrimary())));
        rightPanel.add(taskIcon);
        rightPanel.add(progressLabel);

        topPanel.add(rightPanel, BorderLayout.EAST);
        return topPanel;
    }

    private JPanel createButtonPanel() {
        JButton loadBtn = new LoadButton();
        loadBtn.addActionListener(e -> showLoadRequestsDialog());

        runBtn = new StartButton();
        runBtn.addActionListener(e -> {
            runSelectedRequests();
        });

        stopBtn = new StopButton();
        stopBtn.setEnabled(false); // 初始禁用，执行时才启用
        stopBtn.addActionListener(e -> {
            isStopped = true;
            stopBtn.setEnabled(false);
        });

        JButton refreshBtn = new RefreshButton();
        refreshBtn.addActionListener(e -> refreshRequestsFromCollections());

        JButton clearBtn = new ClearButton();
        clearBtn.addActionListener(e -> {
            tableModel.clear();
            runBtn.setEnabled(false);
            stopBtn.setEnabled(false);
            resetProgress();
            resultsPanel.updateExecutionHistory(null);
            autoSaveSupport.cancel();
            if (csvDataPanel != null) {
                csvDataPanel.restoreState(null);
            }
            persistenceService.clear();
        });

        return ToolWindowActionToolbar.inlineLeft(loadBtn, runBtn, stopBtn, refreshBtn, clearBtn);
    }

    // 批量运行
    private void runSelectedRequests() {
        isStopped = false;
        int rowCount = tableModel.getRowCount();
        int selectedCount = (int) IntStream.range(0, rowCount).mapToObj(i -> tableModel.getRow(i)).filter(row -> row != null && row.selected).count();
        if (selectedCount == 0) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MSG_NO_RUNNABLE_REQUEST));
            return;
        }

        // 检查是否使用 CSV 数据
        int iterations = 1;
        if (csvDataPanel.hasData()) {
            iterations = csvDataPanel.getRowCount();
            int response = JOptionPane.showConfirmDialog(this,
                    I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MSG_CSV_DETECTED, iterations),
                    I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MSG_CSV_TITLE),
                    JOptionPane.YES_NO_OPTION);
            if (response != JOptionPane.YES_OPTION) {
                iterations = 1;
            }
        }

        final int totalExecutions = selectedCount * iterations;
        BatchExecutionHistory currentHistory = new BatchExecutionHistory();
        currentHistory.setTotalIterations(iterations);
        currentHistory.setTotalRequests(totalExecutions);
        executionHistory = currentHistory;
        int generation = ++executionGeneration;

        clearRunResults(rowCount);
        runBtn.setEnabled(false);
        stopBtn.setEnabled(true); // 开始执行时启用 Stop

        progressLabel.setText("0/" + totalExecutions);


        startTime = System.currentTimeMillis();
        executionTimer = new Timer(100, e -> updateExecutionTime());
        executionTimer.start();

        final int finalIterations = iterations;
        new Thread(() -> executeBatchRequestsWithCsv(rowCount, selectedCount, finalIterations, generation, currentHistory)).start();
    }

    private void clearRunResults(int rowCount) {
        for (int i = 0; i < rowCount; i++) {
            RunnerRowData row = tableModel.getRow(i);
            if (row != null) {
                row.response = null;
                row.cost = 0;
                row.status = null;
                row.assertion = null;
                row.testResults = null;
                tableModel.fireTableRowsUpdated(i, i);
            }
        }
    }

    private void executeBatchRequestsWithCsv(int rowCount, int selectedCount, int iterations,
                                             int generation, BatchExecutionHistory currentHistory) {
        int totalFinished = 0;

        for (int iteration = 0; iteration < iterations && isExecutionActive(generation); iteration++) {
            // 获取当前迭代的 CSV 数据
            Map<String, String> currentCsvRow = getCsvDataForIteration(iteration);
            Map<String, String> currentIterationData = IterationDataRuntimeSupport.prepare(currentCsvRow);
            ExecutionVariableContext iterationContext = new ExecutionVariableContext();
            iterationContext.setIterationInfo(iteration, iterations);
            iterationContext.replaceIterationData(currentIterationData);

            // 创建当前迭代的结果记录
            IterationResult iterationResult = new IterationResult(iteration, currentIterationData);

            totalFinished = processIterationRequests(rowCount, selectedCount, iterations, totalFinished,
                    iterationResult, currentIterationData, iterationContext, generation);

            // 完成当前迭代并添加到历史记录（无论是否停止，都要保存当前迭代的结果）
            iterationResult.complete();
            currentHistory.addIteration(iterationResult);

            // 实时更新结果面板
            SwingUtilities.invokeLater(() -> {
                if (isExecutionGenerationCurrent(generation)) {
                    resultsPanel.updateExecutionHistory(currentHistory);
                }
            });

            if (isStopped) break;
        }

        currentHistory.complete();
        finalizeExecution(generation, currentHistory);
    }

    private Map<String, String> getCsvDataForIteration(int iteration) {
        if (csvDataPanel.hasData() && iteration < csvDataPanel.getRowCount()) {
            return csvDataPanel.getRowData(iteration);
        }
        return Collections.emptyMap();
    }

    private int processIterationRequests(int rowCount, int selectedCount, int iterations,
                                         int totalFinished, IterationResult iterationResult,
                                         Map<String, String> currentCsvRow,
                                         ExecutionVariableContext iterationContext,
                                         int generation) {
        int finished = totalFinished;

        for (int i = 0; i < rowCount && isExecutionActive(generation); i++) {
            RunnerRowData row = tableModel.getRow(i);

            if (!isValidRow(row)) {
                continue;
            }

            if (row.selected) {
                finished = executeAndRecordRequest(row, currentCsvRow, iterationResult, finished,
                        selectedCount, iterations, iterationContext, generation);
            }
        }

        return finished;
    }

    private boolean isValidRow(RunnerRowData row) {
        if (row == null || row.requestItem == null) {
            log.warn("Row is invalid, skipping execution");
            return false;
        }
        return true;
    }

    private boolean isExecutionActive(int generation) {
        return isExecutionGenerationCurrent(generation) && !isStopped;
    }

    private boolean isExecutionGenerationCurrent(int generation) {
        return generation == executionGeneration;
    }

    private int executeAndRecordRequest(RunnerRowData row, Map<String, String> currentCsvRow,
                                        IterationResult iterationResult, int totalFinished,
                                        int selectedCount, int iterations,
                                        ExecutionVariableContext iterationContext,
                                        int generation) {
        if (!isExecutionGenerationCurrent(generation)) {
            return totalFinished;
        }

        // 找到当前行的索引
        int rowIndex = -1;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getRow(i) == row) {
                rowIndex = i;
                break;
            }
        }

        // 高亮当前执行的行
        final int currentRowIndex = rowIndex;
        if (currentRowIndex >= 0) {
            SwingUtilities.invokeLater(() -> {
                if (isExecutionGenerationCurrent(generation) && currentRowIndex < tableModel.getRowCount()) {
                    table.setRowSelectionInterval(currentRowIndex, currentRowIndex);
                    table.scrollRectToVisible(table.getCellRect(currentRowIndex, 0, true));
                }
            });
        }

        FunctionalRequestExecutionResult result = requestExecutor.execute(
                row,
                iterationContext,
                () -> isExecutionActive(generation)
        );
        if (!isExecutionGenerationCurrent(generation)) {
            return totalFinished;
        }

        // 更新表格中的执行结果
        if (currentRowIndex >= 0) {
            row.status = result.getStatus();
            row.cost = result.getCost();
            row.assertion = result.getAssertion();
            row.response = result.getResponse();
            row.testResults = result.getTestResults();

            SwingUtilities.invokeLater(() -> {
                if (isExecutionGenerationCurrent(generation) && currentRowIndex < tableModel.getRowCount()) {
                    tableModel.fireTableRowsUpdated(currentRowIndex, currentRowIndex);
                }
            });
        }

        // 记录请求结果到执行历史
        RequestResult requestResult = new RequestResult(
                row.requestItem.getName(),
                row.requestItem.getMethod(),
                result.getRequest().url,
                result.getRequest(),
                result.getResponse(),
                result.getCost(),
                result.getStatus(),
                result.getAssertion(),
                result.getTestResults(),
                result.getErrorMessage()
        );
        iterationResult.addRequestResult(requestResult);

        int newTotalFinished = totalFinished + 1;
        SwingUtilities.invokeLater(() -> {
            if (isExecutionGenerationCurrent(generation)) {
                progressLabel.setText(newTotalFinished + "/" + (selectedCount * iterations));
            }
        });

        return newTotalFinished;
    }

    private void finalizeExecution(int generation, BatchExecutionHistory currentHistory) {
        SwingUtilities.invokeLater(() -> {
            if (!isExecutionGenerationCurrent(generation)) {
                return;
            }
            runBtn.setEnabled(true);
            stopBtn.setEnabled(false);

            // 停止计时器
            stopExecutionTimer();

            // 最终更新结果面板
            resultsPanel.updateExecutionHistory(currentHistory);

            // 无论是正常完成还是用户停止，都切换到结果面板显示已执行的结果
            mainTabbedPane.setSelectedIndex(1); // 切换到执行结果面板

            // 自动选择第一个迭代节点并展开详细信息
            SwingUtilities.invokeLater(() -> resultsPanel.selectFirstIteration());
        });
    }

    private void stopExecutionTimer() {
        if (executionTimer != null && executionTimer.isRunning()) {
            executionTimer.stop();
        }
    }

    // 更新执行时间显示
    private void updateExecutionTime() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        timeLabel.setText(TimeDisplayUtil.formatElapsedTime(elapsedTime));
    }

    // 重置进度和时间显示
    private void resetProgress() {
        // 如果计时器在运行，停止它
        if (executionTimer != null && executionTimer.isRunning()) {
            executionTimer.stop();
        }

        // 重置标签文本
        timeLabel.setText("0 ms");
        progressLabel.setText("0/0");
    }

    private JScrollPane createTablePanel() {
        tableModel = new FunctionalRunnerTableModel();
        table = new JTable(tableModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0; // 只允许第一列（选中列）可编辑
            }
        };
        table.setRowHeight(28);
        table.setFont(FontsUtil.getDefaultFont(Font.PLAIN)); // 使用用户设置的字体大小
        table.getTableHeader().setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, +1)); // 比标准字体大1号（粗体）

        // 添加表头点击监听器，点击"选择"列表头时全选/反选
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = table.columnAtPoint(e.getPoint());
                if (column == 0) { // 点击选择列
                    boolean hasSelected = tableModel.hasSelectedRows();
                    tableModel.setAllSelected(!hasSelected);
                    scheduleSave();
                }
            }
        });
        tableModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) {
                scheduleSave();
            }
        });

        setTableColumnWidths();
        setTableRenderers();

        table.setDragEnabled(true);
        table.setDropMode(DropMode.INSERT_ROWS);
        TableRowTransferHandler transferHandler = new TableRowTransferHandler(table);
        transferHandler.setOnRowOrderChanged(this::scheduleSave); // 拖拽完成后自动持久化
        table.setTransferHandler(transferHandler);

        JScrollPane scrollPane = new JScrollPane(table);
        ToolWindowSurfaceStyle.applyTableScrollPaneCard(scrollPane, table);
        return scrollPane;
    }

    private void setTableColumnWidths() {
        if (table.getColumnModel().getColumnCount() > 0) {
            // Select column (✓) - 固定宽度，不需要太宽
            table.getColumnModel().getColumn(0).setMinWidth(45);
            table.getColumnModel().getColumn(0).setMaxWidth(55);
            table.getColumnModel().getColumn(0).setPreferredWidth(50);

            // Name column - 相对灵活，可以较宽
            table.getColumnModel().getColumn(1).setMinWidth(120);
            table.getColumnModel().getColumn(1).setPreferredWidth(180);

            // URL column - 最宽的列，可以占据更多空间
            table.getColumnModel().getColumn(2).setMinWidth(150);
            table.getColumnModel().getColumn(2).setPreferredWidth(250);

            // Method column - 需要足够宽度显示 "Method"（6个字符）+ 内容（DELETE最长7个字符）
            table.getColumnModel().getColumn(3).setMinWidth(75);
            table.getColumnModel().getColumn(3).setMaxWidth(90);
            table.getColumnModel().getColumn(3).setPreferredWidth(80);

            // Status column - 需要足够宽度显示 "Status"（6个字符）+ 状态码（3位数）
            table.getColumnModel().getColumn(4).setMinWidth(70);
            table.getColumnModel().getColumn(4).setMaxWidth(85);
            table.getColumnModel().getColumn(4).setPreferredWidth(75);

            // Time column - 需要足够宽度显示 "Time"（4个字符）+ 时间（如 "123 ms"）
            table.getColumnModel().getColumn(5).setMinWidth(75);
            table.getColumnModel().getColumn(5).setMaxWidth(100);
            table.getColumnModel().getColumn(5).setPreferredWidth(85);

            // Result column - 需要足够宽度显示 "Result"（6个字符）+ emoji
            table.getColumnModel().getColumn(6).setMinWidth(65);
            table.getColumnModel().getColumn(6).setMaxWidth(80);
            table.getColumnModel().getColumn(6).setPreferredWidth(70);
        }
    }

    private void setTableRenderers() {
        table.getColumnModel().getColumn(3).setCellRenderer(createMethodRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(createStatusRenderer());
        table.getColumnModel().getColumn(6).setCellRenderer(createResultRenderer());
    }

    private DefaultTableCellRenderer createMethodRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    c.setForeground(HttpRequestDisplayMetadata.methodColor(value.toString()));
                }
                return c;
            }
        };
    }

    private DefaultTableCellRenderer createStatusRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (value != null && !"-".equals(value)) {
                    applyStatusColors(c, value.toString());
                }
                setHorizontalAlignment(CENTER);
                return c;
            }
        };
    }

    /**
     * 根据状态码应用颜色 - 只设置文字颜色
     */
    private void applyStatusColors(Component c, String status) {
        Color foreground = ModernColors.getTextPrimary();

        if (ERROR.equalsIgnoreCase(status)) {
            // 错误状态：使用错误色
            foreground = ModernColors.getErrorDark();
        } else {
            // 尝试解析状态码
            try {
                int code = Integer.parseInt(status);
                if (code >= 200 && code < 300) {
                    // 成功：使用绿色
                    foreground = ModernColors.getSuccessDark();
                } else if (code >= 400 && code < 500) {
                    // 客户端错误：使用警告色
                    foreground = ModernColors.getWarningDarker();
                } else if (code >= 500) {
                    // 服务器错误：使用错误色
                    foreground = ModernColors.getErrorDarker();
                }
            } catch (NumberFormatException e) {
                // 非数字状态（如错误消息）
                foreground = ModernColors.getErrorDark();
            }

        }
        // 只设置文字颜色
        c.setForeground(foreground);
    }

    private DefaultTableCellRenderer createResultRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);
                if (value instanceof AssertionResult ar) {
                    setText(ar.getDisplayValue());
                    if (AssertionResult.PASS.equals(ar)) {
                        c.setForeground(isSelected ? c.getForeground() : ModernColors.getSuccessDark());
                    } else if (AssertionResult.FAIL.equals(ar)) {
                        c.setForeground(isSelected ? c.getForeground() : ModernColors.getErrorDark());
                    } else {
                        c.setForeground(isSelected ? c.getForeground() : ModernColors.getTextSecondary());
                    }
                } else {
                    setText("-");
                    c.setForeground(ModernColors.getTextDisabled());
                }
                return c;
            }
        };
    }

    @Override
    protected void registerListeners() {
        RequestSaveEventPublisher.register(this::syncRequestItem);
        // 添加表格鼠标监听器
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;

                if (SwingUtilities.isRightMouseButton(e)) { // 右键 - 只显示菜单
                    table.setRowSelectionInterval(row, row);
                    showTableContextMenu(e, row);
                } else if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) { // 左键双击
                    showRequestDetail(row);
                }
            }
        });
    }

    /**
     * 显示请求详情
     */
    private void showRequestDetail(int rowIndex) {
        RunnerRowData row = tableModel.getRow(rowIndex);
        if (row != null && row.requestItem != null) {
            // 打开请求编辑面板
            RequestEditorPanel editPanel =
                    UiSingletonFactory.getInstance(RequestEditorPanel.class);
            editPanel.showOrCreateTab(row.requestItem);

            // 切换到Collections标签
            SidebarTabPanel sidebarPanel =
                    UiSingletonFactory.getInstance(SidebarTabPanel.class);
            sidebarPanel.getTabbedPane().setSelectedIndex(0);
        }
    }

    /**
     * 显示表格右键菜单
     */
    private void showTableContextMenu(java.awt.event.MouseEvent e, int rowIndex) {
        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);
        RunnerRowData row = tableModel.getRow(rowIndex);

        // 查看详情
        JMenuItem viewItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MENU_VIEW_DETAIL));
        viewItem.addActionListener(evt -> showRequestDetail(rowIndex));
        menu.add(viewItem);

        menu.addSeparator();

        // 勾选 / 取消勾选
        if (row != null) {
            String toggleText = row.selected
                    ? I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MENU_UNCHECK)
                    : I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MENU_CHECK);
            JMenuItem toggleItem = new JMenuItem(toggleText);
            toggleItem.addActionListener(evt -> {
                row.selected = !row.selected;
                tableModel.fireTableRowsUpdated(rowIndex, rowIndex);
                scheduleSave();
            });
            menu.add(toggleItem);
        }

        menu.addSeparator();

        // 上移
        JMenuItem upItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MENU_MOVE_UP));
        upItem.setEnabled(rowIndex > 0);
        upItem.addActionListener(evt -> {
            tableModel.moveRow(rowIndex, rowIndex - 1);
            table.setRowSelectionInterval(rowIndex - 1, rowIndex - 1);
            scheduleSave();
        });
        menu.add(upItem);

        // 下移
        JMenuItem downItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MENU_MOVE_DOWN));
        downItem.setEnabled(rowIndex < tableModel.getRowCount() - 1);
        downItem.addActionListener(evt -> {
            tableModel.moveRow(rowIndex, rowIndex + 1);
            table.setRowSelectionInterval(rowIndex + 1, rowIndex + 1);
            scheduleSave();
        });
        menu.add(downItem);

        menu.addSeparator();

        // 移除当前行
        JMenuItem deleteItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MENU_REMOVE));
        deleteItem.addActionListener(evt -> {
            tableModel.removeRow(rowIndex);
            if (tableModel.getRowCount() == 0) {
                runBtn.setEnabled(false);
            }
            scheduleSave();
        });
        menu.add(deleteItem);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // 弹出选择请求/分组对话框
    private void showLoadRequestsDialog() {
        RequestSelectionDialogSupport.showMultiSelectRequestDialog(
                selected -> {
                    if (selected == null || selected.isEmpty()) return;
                    // 过滤只保留HTTP类型的请求
                    List<HttpRequestItem> httpOnlyList = selected.stream()
                            .filter(reqItem -> reqItem.getProtocol() != null && reqItem.getProtocol().isHttpProtocol())
                            .toList();
                    if (httpOnlyList.isEmpty()) {
                        NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.MSG_ONLY_HTTP_SUPPORTED));
                        return;
                    }
                    loadRequests(httpOnlyList);
                }
        );
    }

    // 加载选中的请求到表格
    public void loadRequests(List<HttpRequestItem> requests) {
        // 构建已有请求的 ID 集合，用于去重
        Set<String> existingIds = tableModel.getAllRows().stream()
                .filter(r -> r != null && r.requestItem != null && r.requestItem.getId() != null)
                .map(r -> r.requestItem.getId())
                .collect(java.util.stream.Collectors.toSet());

        int skippedCount = 0;
        for (HttpRequestItem item : requests) {
            if (item.getId() != null && existingIds.contains(item.getId())) {
                skippedCount++;
                continue; // 跳过已存在的请求，避免重复
            }
            tableModel.addRow(new RunnerRowData(item));
            if (item.getId() != null) {
                existingIds.add(item.getId()); // 同批次中也去重
            }
        }

        if (skippedCount > 0) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MSG_DUPLICATE_SKIPPED, skippedCount));
        }

        table.setEnabled(true);
        runBtn.setEnabled(true);
        // 加载请求后保存配置
        scheduleSave();
    }

    /**
     * 加载保存的配置
     */
    private void loadSaved() {
        try {
            FunctionalConfigSnapshot snapshot = persistenceService.loadSnapshot();
            csvDataPanel.restoreState(toCsvState(snapshot.getCsvState()));
            List<RunnerRowData> savedRows = restoreRows(snapshot.getRows());
            if (!savedRows.isEmpty()) {
                for (RunnerRowData row : savedRows) {
                    tableModel.addRow(row);
                }
                table.setEnabled(true);
                runBtn.setEnabled(true);
                log.info("Loaded {} saved test configurations", savedRows.size());
            }
            // 以界面加载结果为保存基准：未修改时切换/退出不写盘，不会重新生成被 Git 删除的文件。
            persistenceService.rememberLoaded(toConfigSnapshot(tableModel.getAllRows()));
        } catch (Exception e) {
            log.error("Failed to load saved config", e);
        }
    }

    public void switchWorkspaceAndRefreshUI() {
        if (!isReadyForWorkspaceSwitch()) {
            return;
        }

        executionGeneration++;
        isStopped = true;
        stopExecutionTimer();
        // 切换 workspace 前取消待保存任务，避免旧 workspace 的 CSV/行配置延迟写到新 workspace。
        autoSaveSupport.cancel();
        tableModel.clear();
        table.clearSelection();
        table.setEnabled(false);
        runBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        resetProgress();
        executionHistory = null;
        resultsPanel.updateExecutionHistory(null);
        mainTabbedPane.setSelectedIndex(0);
        loadSaved();
    }

    private boolean isReadyForWorkspaceSwitch() {
        return tableModel != null
                && table != null
                && persistenceService != null
                && csvDataPanel != null
                && runBtn != null
                && stopBtn != null
                && timeLabel != null
                && progressLabel != null
                && resultsPanel != null
                && mainTabbedPane != null;
    }

    /**
     * 同步最新的 HttpRequestItem 到表格中对应的行（由 Collections 保存时调用）
     * 避免用户在 editSubPanel 修改并保存后，FunctionalPanel 仍持有旧数据。
     *
     * @param item 已保存的最新请求数据
     */
    public void syncRequestItem(HttpRequestItem item) {
        if (item == null || item.getId() == null) return;
        List<RunnerRowData> rows = tableModel.getAllRows();
        for (int i = 0; i < rows.size(); i++) {
            RunnerRowData row = rows.get(i);
            if (row != null && row.requestItem != null && item.getId().equals(row.requestItem.getId())) {
                row.requestItem = item;
                row.name = item.getName();
                row.url = item.getUrl();
                row.method = item.getMethod();
                tableModel.fireTableRowsUpdated(i, i);
                log.debug("FunctionalPanel syncRequestItem: id={}, name={}", item.getId(), item.getName());
            }
        }
    }

    /**
     * 保存当前配置
     */
    public void save() {
        try {
            if (tableModel == null || persistenceService == null) {
                return;
            }
            if (table != null && table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            autoSaveSupport.cancel();
            persistenceService.save(toConfigSnapshot(tableModel.getAllRows()));
        } catch (Exception e) {
            log.error("Failed to save config", e);
        }
    }

    private void scheduleSave() {
        // 表格和 CSV 面板变更较频繁，统一走防抖保存，降低连续编辑时的落盘压力。
        autoSaveSupport.requestSave();
    }

    private void saveAsync() {
        try {
            if (tableModel == null || persistenceService == null) {
                return;
            }
            persistenceService.saveAsync(toConfigSnapshot(tableModel.getAllRows()));
        } catch (Exception e) {
            log.error("Failed to schedule functional config save", e);
        }
    }

    private List<RunnerRowData> restoreRows(List<FunctionalConfigRow> configRows) {
        List<RunnerRowData> rows = new ArrayList<>();
        if (configRows == null || configRows.isEmpty()) {
            return rows;
        }
        for (FunctionalConfigRow configRow : configRows) {
            if (configRow == null || configRow.getRequestId() == null || configRow.getRequestId().isBlank()) {
                continue;
            }
            try {
                Optional<HttpRequestItem> requestItem = requestLookup.findRequestItemById(configRow.getRequestId());
                if (requestItem.isEmpty()) {
                    log.warn("Request with ID {} not found in collections, skipping", configRow.getRequestId());
                    continue;
                }
                RunnerRowData row = new RunnerRowData(requestItem.get());
                row.selected = configRow.isSelected();
                rows.add(row);
            } catch (Exception e) {
                log.warn("Failed to restore functional row for request {}: {}", configRow.getRequestId(), e.getMessage());
            }
        }
        return rows;
    }

    private FunctionalConfigSnapshot toConfigSnapshot(List<RunnerRowData> rows) {
        List<FunctionalConfigRow> configRows = new ArrayList<>();
        if (rows != null) {
            for (RunnerRowData row : rows) {
                if (row != null && row.requestItem != null && row.requestItem.getId() != null) {
                    configRows.add(new FunctionalConfigRow(row.requestItem.getId(), row.selected));
                }
            }
        }
        return new FunctionalConfigSnapshot(configRows, exportFunctionalCsvState());
    }

    private FunctionalCsvDataState exportFunctionalCsvState() {
        if (csvDataPanel == null) {
            return null;
        }
        CsvDataPanel.CsvState state = csvDataPanel.exportState();
        return toFunctionalCsvDataState(state);
    }

    private static FunctionalCsvDataState toFunctionalCsvDataState(CsvDataPanel.CsvState state) {
        if (state == null) {
            return null;
        }
        return new FunctionalCsvDataState(state.getSourceName(), state.getHeaders(), state.getRows());
    }

    private static CsvDataPanel.CsvState toCsvState(FunctionalCsvDataState state) {
        if (state == null) {
            return null;
        }
        return new CsvDataPanel.CsvState(state.getSourceName(), state.getHeaders(), state.getRows());
    }

    /**
     * 从集合中刷新请求数据
     * 重新加载所有请求的最新配置
     */
    private void refreshRequestsFromCollections() {
        List<RunnerRowData> currentRows = tableModel.getAllRows();
        if (currentRows.isEmpty()) {
            NotificationCenter.showInfo(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MSG_NO_RUNNABLE_REQUEST));
            return;
        }

        int updatedCount = 0;
        int removedCount = 0;
        List<Integer> rowsToRemove = new ArrayList<>();

        for (int i = 0; i < currentRows.size(); i++) {
            RunnerRowData row = currentRows.get(i);
            if (row == null || row.requestItem == null) {
                rowsToRemove.add(i);
                removedCount++;
                continue;
            }

            // 通过ID从集合中查找最新的请求配置
            HttpRequestItem latestRequestItem = requestLookup.findRequestItemById(row.requestItem.getId()).orElse(null);

            if (latestRequestItem == null) {
                // 请求在集合中已被删除
                log.warn("Request with ID {} not found in collections", row.requestItem.getId());
                rowsToRemove.add(i);
                removedCount++;
            } else {
                // 更新请求数据
                try {
                    row.requestItem = latestRequestItem;
                    row.name = latestRequestItem.getName();
                    row.url = latestRequestItem.getUrl();
                    row.method = latestRequestItem.getMethod();
                    updatedCount++;
                } catch (Exception e) {
                    log.error("Failed to refresh request {}: {}", latestRequestItem.getName(), e.getMessage());
                }
            }
        }

        // 移除不存在的请求（从后往前删除，避免索引变化）
        for (int i = rowsToRemove.size() - 1; i >= 0; i--) {
            tableModel.removeRow(rowsToRemove.get(i));
        }

        // 刷新表格显示
        tableModel.fireTableDataChanged();

        // 保存更新后的配置
        scheduleSave();

        // 更新按钮状态
        if (tableModel.getRowCount() == 0) {
            runBtn.setEnabled(false);
        }

        // 显示刷新结果
        if (removedCount > 0) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MSG_REFRESH_WARNING, removedCount));
        } else {
            NotificationCenter.showInfo(I18nUtil.getMessage(MessageKeys.FUNCTIONAL_MSG_REFRESH_SUCCESS, updatedCount));
        }
    }
}
