package com.laker.postman.common.component.table;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.util.CommonI18n;
import com.laker.postman.util.CommonMessageKeys;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.IconUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

/**
 * 所有 EasyPostman 表格面板的抽象基类
 * 提供通用的表格UI配置、工具方法和递归保护机制
 *
 * @param <T> 表格数据模型类型
 * @author Laker
 */
@Slf4j
public abstract class AbstractTablePanel<T> extends JPanel {
    private static final int DELETE_COLUMN_WIDTH = 28;

    // ========== 共同字段 ==========

    /**
     * 表格数据模型
     */
    protected DefaultTableModel tableModel;

    /**
     * 表格组件
     */
    @Getter
    protected JTable table;

    /**
     * 列名数组
     */
    protected final String[] columns;

    /**
     * 表格是否可编辑
     */
    @Getter
    protected boolean editable = true;

    /**
     * 批量操作时抑制自动追加空行的标志
     */
    protected boolean suppressAutoAppendRow = false;

    /**
     * 防止递归调用 stopCellEditing 的标志
     */
    protected boolean isStoppingCellEdit = false;

    /**
     * 当前鼠标悬停的行索引（视图索引），-1 表示无悬停
     */
    protected int hoveredRow = -1;

    /**
     * 当前鼠标悬停的列索引（视图索引），-1 表示无悬停
     */
    protected int hoveredColumn = -1;

    private static final String ACTION_DELETE_ROW = "deleteRow";
    private static final String ACTION_ENTER_NAV  = "enterNav";
    private static final String ACTION_EDITOR_NEXT_CELL = "editorNextCell";
    private static final String ACTION_EDITOR_PREVIOUS_CELL = "editorPreviousCell";

    // ========== 构造函数 ==========

    /**
     * 构造函数
     *
     * @param columns 列名数组
     */
    protected AbstractTablePanel(String[] columns) {
        this.columns = columns;
    }

    // ========== 抽象方法（子类必须实现）==========

    /**
     * 获取启用列的索引
     *
     * @return 启用列索引
     */
    protected abstract int getEnabledColumnIndex();

    /**
     * 获取删除列的索引
     *
     * @return 删除列索引
     */
    protected abstract int getDeleteColumnIndex();

    /**
     * 获取第一个可编辑列的索引（用于Tab导航）
     *
     * @return 第一个可编辑列索引
     */
    protected abstract int getFirstEditableColumnIndex();

    /**
     * 获取最后一个可编辑列的索引（用于Tab导航）
     *
     * @return 最后一个可编辑列索引
     */
    protected abstract int getLastEditableColumnIndex();

    /**
     * 获取 Key 列按 Enter 后跳转的目标列。
     * 默认沿用最后一个可编辑列；包含说明列的 Key/Value 表可覆盖为 Value 列。
     *
     * @return Enter 导航目标列索引
     */
    protected int getEnterTargetColumnIndex() {
        return getLastEditableColumnIndex();
    }

    /**
     * 检查指定单元格是否可编辑（用于Tab导航）
     *
     * @param row    行索引
     * @param column 列索引
     * @return true 如果可编辑
     */
    protected abstract boolean isCellEditableForNavigation(int row, int column);

    /**
     * 检查指定行是否有内容
     *
     * @param row 行索引
     * @return true 如果有内容
     */
    protected abstract boolean hasContentInRow(int row);

    /**
     * 获取表格模型的列类型
     * 子类可以重写此方法来定义特殊列的类型（如Boolean类型的复选框列）
     *
     * @param columnIndex 列索引
     * @return 列的Class类型
     */
    protected Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == getEnabledColumnIndex()) {
            return Boolean.class;
        }
        return Object.class;
    }

    /**
     * 检查单元格是否可编辑（用于表格模型）
     * 子类可以重写此方法来实现特殊的编辑逻辑
     *
     * @param row    行索引
     * @param column 列索引
     * @return true 如果可编辑
     */
    protected boolean isCellEditable(int row, int column) {
        if (!editable) {
            return false;
        }

        // Checkbox column is always editable
        if (column == getEnabledColumnIndex()) {
            return true;
        }

        // Delete column is not editable (uses custom renderer)
        if (column == getDeleteColumnIndex()) {
            return false;
        }

        // Other columns use navigation logic
        return isCellEditableForNavigation(row, column);
    }

    // ========== 通用 UI 配置方法 ==========

    /**
     * 通用的组件初始化方法
     * 创建表格模型、表格和滚动面板
     */
    protected void initializeComponents() {
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        // Initialize table model with custom cell editing logic
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return AbstractTablePanel.this.getColumnClass(columnIndex);
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return AbstractTablePanel.this.isCellEditable(row, column);
            }

            @Override
            public void setValueAt(Object aValue, int row, int column) {
                Object oldValue = getValueAt(row, column);
                if (Objects.equals(oldValue, aValue)) {
                    return;
                }
                super.setValueAt(aValue, row, column);
            }
        };

        // Create table
        table = new JTable(tableModel);

        if (useTableScrollPane()) {
            // Wrap table in JScrollPane to show headers
            JScrollPane scrollPane = new JScrollPane(table);
            ToolWindowSurfaceStyle.applyTableScrollPaneCard(scrollPane, table);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            configureTableScrollPane(scrollPane);
            add(scrollPane, BorderLayout.CENTER);
        } else {
            add(table.getTableHeader(), BorderLayout.NORTH);
            add(table, BorderLayout.CENTER);
        }
    }

    protected void configureTableScrollPane(JScrollPane scrollPane) {
        // Default scroll behavior remains owned by Swing/FlatLaf.
    }

    protected boolean useTableScrollPane() {
        return true;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // 主题切换时重新设置边框，确保表格网格颜色更新
        if (getBorder() != null) {
            setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        }
    }

    /**
     * 初始化表格 UI
     * 所有表格共用的 UI 配置
     */
    protected void initializeTableUI() {
        table.setFillsViewportHeight(false); // 只占用必要的高度，避免空白行过多
        table.setRowHeight(28);
        table.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1)); // 比标准字体小1号
        table.getTableHeader().setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1)); // 比标准字体小1号（粗体）
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setRowMargin(1);
        ToolWindowSurfaceStyle.applyTableCard(table);
        setColumnRenderer(getEnabledColumnIndex(), new EnabledColumnRenderer());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

        // Configure Tab key behavior to move between columns
        table.setSurrendersFocusOnKeystroke(true);

        // 关键设置：失去焦点时自动停止编辑并保存，实现 Postman 风格的即时保存
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // 单击即进入编辑模式（Postman 风格）
        setupSingleClickEditing();

        // 行悬停高亮
        setupRowHoverHighlight();

        // Delete / Backspace 键删除选中行
        setupDeleteKeyShortcut();

        // Enter 键在列间跳转
        setupEnterKeyNavigation();
    }

    /**
     * 单击即进入编辑模式，同时处理悬停行的 mouseExited 清除。
     * 两个逻辑合并为一个 MouseAdapter，减少监听器对象数量。
     */
    private void setupSingleClickEditing() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (editable && SwingUtilities.isLeftMouseButton(e)) {
                    triggerSingleClickEdit(e);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredRow != -1 || hoveredColumn != -1) {
                    hoveredRow = -1;
                    hoveredColumn = -1;
                    table.putClientProperty("hoverRow", -1);
                    table.putClientProperty("hoverColumn", -1);
                    updateDeleteButtonCursor(-1, -1);
                    table.repaint();
                }
            }
        });
    }

    /** 鼠标单击时触发单元格编辑（延迟一帧确保选中先完成） */
    private void triggerSingleClickEdit(MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        int col = table.columnAtPoint(e.getPoint());
        if (row < 0 || col < 0) return;
        if (col == getEnabledColumnIndex() || col == getDeleteColumnIndex()) return;
        if (!table.isCellEditable(row, col)) return;
        SwingUtilities.invokeLater(() -> {
            if (!table.isEditing() || table.getEditingRow() != row || table.getEditingColumn() != col) {
                table.editCellAt(row, col);
            }
            if (table.isEditing()
                    && table.getEditingRow() == row
                    && table.getEditingColumn() == col) {
                Component ed = table.getEditorComponent();
                if (ed != null) {
                    focusEditorTextComponent(ed, true);
                }
            }
        });
    }

    /**
     * 将焦点交给当前可见的文本编辑组件。
     *
     * <p>智能值编辑器是一个 CardLayout 容器，同时持有单行 JTextField 和多行 JTextArea。
     * 不能只按组件类型查找 JTextField，否则长值切换到多行卡片后会找到隐藏的单行组件，
     * Windows 下尤其容易表现为双击后无法输入。</p>
     */
    private void focusEditorTextComponent(Component editor, boolean selectSingleLineText) {
        JTextComponent textComponent = findVisibleTextComponent(editor);
        if (textComponent == null) {
            editor.requestFocusInWindow();
            return;
        }

        if (textComponent instanceof JTextArea textArea) {
            installTextAreaTabNavigation(textArea);
        }
        textComponent.requestFocusInWindow();
        if (selectSingleLineText && textComponent instanceof JTextField textField) {
            textField.selectAll();
        }
    }

    /**
     * JTextArea 默认会把 Tab 插入为制表符，优先级高于表格的祖先 InputMap。
     * 长值编辑器使用 JTextArea 时，需要显式转发到表格的单元格导航。
     */
    private void installTextAreaTabNavigation(JTextArea textArea) {
        InputMap inputMap = textArea.getInputMap(JComponent.WHEN_FOCUSED);
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0),
                ACTION_EDITOR_NEXT_CELL);
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB,
                        java.awt.event.InputEvent.SHIFT_DOWN_MASK),
                ACTION_EDITOR_PREVIOUS_CELL);

        ActionMap actionMap = textArea.getActionMap();
        actionMap.put(ACTION_EDITOR_NEXT_CELL, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                moveToNextEditableCell(false);
            }
        });
        actionMap.put(ACTION_EDITOR_PREVIOUS_CELL, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                moveToNextEditableCell(true);
            }
        });
    }

    private static JTextComponent findVisibleTextComponent(Component root) {
        return findVisibleTextComponent(root, true);
    }

    private static JTextComponent findVisibleTextComponent(Component root, boolean parentVisible) {
        boolean visible = parentVisible && root.isVisible();
        if (root instanceof JTextComponent textComponent
                && visible
                && textComponent.isEnabled()) {
            return textComponent;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextComponent found = findVisibleTextComponent(child, visible);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }


    /**
     * 悬停行高亮：鼠标移动时记录当前行索引
     */
    private void setupRowHoverHighlight() {
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());
                boolean hoverChanged = row != hoveredRow || column != hoveredColumn;
                hoveredRow = row;
                hoveredColumn = column;
                table.putClientProperty("hoverRow", row);
                table.putClientProperty("hoverColumn", column);
                updateDeleteButtonCursor(row, column);
                if (hoverChanged) {
                    table.repaint();
                }
            }
        });
    }

    /**
     * Delete / Backspace 键删除当前选中行（仅当单元格不在编辑状态时）
     */
    private void setupDeleteKeyShortcut() {
        InputMap inputMap = table.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = table.getActionMap();

        AbstractAction deleteAction = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!table.isEditing()) {
                    deleteSelectedRow();
                }
            }
        };

        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), ACTION_DELETE_ROW);
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_SPACE, 0), ACTION_DELETE_ROW);
        actionMap.put(ACTION_DELETE_ROW, deleteAction);
    }

    /**
     * Enter 键导航：
     * - 在 Key 列按 Enter → 跳到同行 Value 列
     * - 在 Value 列按 Enter → 跳到下一行 Key 列（如无下一行则追加）
     */
    private void setupEnterKeyNavigation() {
        InputMap inputMap = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = table.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), ACTION_ENTER_NAV);
        actionMap.put(ACTION_ENTER_NAV, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!editable) return;

                // 如果自动补全弹窗正在显示，把 Enter 交给弹窗处理（接受建议），不做行间导航
                if (isAutoCompletePopupVisible()) {
                    int row = table.isEditing() ? table.getEditingRow()    : table.getSelectedRow();
                    int col = table.isEditing() ? table.getEditingColumn() : table.getSelectedColumn();
                    dispatchEnterToEditor();
                    // Key 列接受建议后自动跳到 Value 列，Value 列接受建议后停留（用户再按 Enter 换行）
                    if (col == getFirstEditableColumnIndex() && row >= 0) {
                        enterFromKeyColumn(row);
                    }
                    return;
                }

                int row = table.isEditing() ? table.getEditingRow()    : table.getSelectedRow();
                int col = table.isEditing() ? table.getEditingColumn() : table.getSelectedColumn();
                if (row < 0) return;

                if (table.isEditing()) {
                    table.getCellEditor().stopCellEditing();
                }

                if (col == getFirstEditableColumnIndex()) {
                    enterFromKeyColumn(row);
                } else {
                    enterFromValueColumn(row);
                }
            }
        });
    }

    /** Enter 在 Key 列：跳到同行 Value 列 */
    private void enterFromKeyColumn(int row) {
        int valueCol = getEnterTargetColumnIndex();
        if (table.isCellEditable(row, valueCol)) {
            // invokeLater 确保 stopCellEditing 已将 Key 值写入 model，Value 编辑器再读取
            SwingUtilities.invokeLater(() -> startEditAt(row, valueCol));
        }
    }

    /** Enter 在 Value 列：跳到下一个可编辑 Key 行 */
    private void enterFromValueColumn(int row) {
        int keyCol  = getFirstEditableColumnIndex();
        int nextRow = row + 1;

        if (nextRow >= table.getRowCount()) {
            // 当前是最后一行：autoAppendRowFeature 也用 invokeLater 追加新行。
            // 用双重 invokeLater：第一次让 autoAppend 的 invokeLater 先执行完，
            // 第二次再跳到新追加的那一行（此时 rowCount 已增加）。
            SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
                int last = table.getRowCount() - 1;
                if (last > row) {
                    // 新行已追加，跳到它的 Key 列
                    startEditAt(last, keyCol);
                }
                // 若 autoAppend 没有追加（当前行本身是空行），停留不动
            }));
            return;
        }

        // 跳过 Key 列不可编辑的行（如默认 header 行，其 Key 是只读的）
        while (nextRow < table.getRowCount() && !table.isCellEditable(nextRow, keyCol)) {
            nextRow++;
        }
        if (nextRow < table.getRowCount()) {
            final int targetRow = nextRow;
            SwingUtilities.invokeLater(() -> startEditAt(targetRow, keyCol));
        }
    }

    /**
     * 检查当前编辑器中是否有自动补全弹窗正在显示。
     * 用于 Enter 键判断：若弹窗可见，Enter 应接受建议而非跳行。
     */
    private boolean isAutoCompletePopupVisible() {
        Component ed = table.getEditorComponent();
        if (ed == null) return false;
        return containsVisibleAutoComplete(ed);
    }

    private static boolean containsVisibleAutoComplete(Component root) {
        // 导入路径避免硬依赖：通过类名反射判断，或直接检查方法
        if (root instanceof com.laker.postman.common.component.AutoCompleteEasyTextField ac) {
            return ac.isAutoCompletePopupVisible();
        }
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                if (containsVisibleAutoComplete(child)) return true;
            }
        }
        return false;
    }

    /**
     * 把 Enter 键事件直接转发给编辑器中的文本框，
     * 让 AutoCompleteEasyTextField 的 KeyAdapter 接受建议。
     */
    private void dispatchEnterToEditor() {
        Component ed = table.getEditorComponent();
        if (ed == null) return;
        com.laker.postman.common.component.AutoCompleteEasyTextField ac = findAutoComplete(ed);
        if (ac != null) {
            // 直接调用接受建议的逻辑（通过公开方法触发）
            ac.acceptCurrentSuggestion();
        }
    }

    private static com.laker.postman.common.component.AutoCompleteEasyTextField findAutoComplete(Component root) {
        if (root instanceof com.laker.postman.common.component.AutoCompleteEasyTextField ac) return ac;
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                var found = findAutoComplete(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * 设置启用列的宽度
     *
     * @param width 宽度
     */
    protected void setEnabledColumnWidth(int width) {
        int colIndex = getEnabledColumnIndex();
        table.getColumnModel().getColumn(colIndex).setPreferredWidth(width);
        table.getColumnModel().getColumn(colIndex).setMaxWidth(width);
        table.getColumnModel().getColumn(colIndex).setMinWidth(width);
    }

    /**
     * 设置删除列的宽度
     */
    protected void setDeleteColumnWidth() {
        int colIndex = getDeleteColumnIndex();
        table.getColumnModel().getColumn(colIndex).setPreferredWidth(DELETE_COLUMN_WIDTH);
        table.getColumnModel().getColumn(colIndex).setMaxWidth(DELETE_COLUMN_WIDTH);
        table.getColumnModel().getColumn(colIndex).setMinWidth(DELETE_COLUMN_WIDTH);
        table.getColumnModel().getColumn(colIndex).setResizable(false);
    }

    /**
     * 设置列编辑器
     *
     * @param column 列索引
     * @param editor 单元格编辑器
     */
    protected void setColumnEditor(int column, TableCellEditor editor) {
        if (column >= 0 && column < table.getColumnCount()) {
            table.getColumnModel().getColumn(column).setCellEditor(editor);
        }
    }

    /**
     * 设置列渲染器
     *
     * @param column   列索引
     * @param renderer 单元格渲染器
     */
    protected void setColumnRenderer(int column, TableCellRenderer renderer) {
        if (column >= 0 && column < table.getColumnCount()) {
            table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
    }

    // ========== 通用工具方法 ==========

    /**
     * 安全地停止单元格编辑
     * 防止在修改表格数据时出现 ArrayIndexOutOfBoundsException
     */
    public void stopCellEditing() {
        if (table.isEditing()) {
            TableCellEditor editor = table.getCellEditor();
            if (editor != null) {
                editor.stopCellEditing();
            }
        }
    }

    /**
     * 带递归保护的停止编辑
     * 在调用可能触发表格事件的 getter 方法时使用
     */
    protected void stopCellEditingWithProtection() {
        if (!isStoppingCellEdit) {
            isStoppingCellEdit = true;
            try {
                stopCellEditing();
            } finally {
                isStoppingCellEdit = false;
            }
        }
    }

    /**
     * 清空表格数据
     */
    public void clear() {
        stopCellEditing();

        // 确保在 EDT 线程中执行表格操作，避免 RowSorter 的线程安全问题
        if (SwingUtilities.isEventDispatchThread()) {
            clearInternal();
        } else {
            try {
                SwingUtilities.invokeAndWait(this::clearInternal);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                log.warn("Interrupted while clearing table", e);
            } catch (java.lang.reflect.InvocationTargetException e) {
                log.error("Error clearing table", e);
            }
        }
    }

    private void clearInternal() {
        suppressAutoAppendRow = true;
        try {
            tableModel.setRowCount(0);
            tableModel.addRow(createEmptyRow());
        } finally {
            suppressAutoAppendRow = false;
        }
    }

    /**
     * 创建空行数据
     *
     * @return 空行数组
     */
    protected Object[] createEmptyRow() {
        Object[] row = new Object[columns.length];
        row[getEnabledColumnIndex()] = true; // 默认启用
        for (int i = 0; i < columns.length; i++) {
            if (i != getEnabledColumnIndex()) {
                row[i] = "";
            }
        }
        return row;
    }

    /**
     * 检查最后一行是否有内容
     *
     * @return true 如果最后一行有内容
     */
    protected boolean hasContentInLastRow() {
        int rowCount = tableModel.getRowCount();
        if (rowCount == 0) {
            return false;
        }
        return hasContentInRow(rowCount - 1);
    }

    /**
     * 确保表格最后一行是空行
     * 类似 Postman 的自动追加空行功能
     */
    protected void ensureEmptyLastRow() {
        suppressAutoAppendRow = true;
        try {
            if (tableModel.getRowCount() == 0 || hasContentInLastRow()) {
                tableModel.addRow(createEmptyRow());
            }
        } finally {
            suppressAutoAppendRow = false;
        }
    }

    /**
     * 添加表格模型监听器
     *
     * @param listener 监听器
     */
    public void addTableModelListener(TableModelListener listener) {
        if (tableModel != null) {
            tableModel.addTableModelListener(listener);
        }
    }

    /**
     * 添加一行数据（通用方法）
     *
     * @param values 行数据
     */
    public void addRow(Object... values) {
        if (values == null || values.length == 0) {
            tableModel.addRow(createEmptyRow());
        } else {
            // Create row with provided values
            Object[] row = new Object[columns.length];
            row[getEnabledColumnIndex()] = true; // 默认启用

            // Fill in the provided values
            int valueIndex = 0;
            for (int i = 0; i < columns.length; i++) {
                if (i == getDeleteColumnIndex()) {
                    row[i] = ""; // Delete column is always empty
                } else if (i != getEnabledColumnIndex()) {
                    // enabled column already set to true; fill everything else
                    row[i] = (valueIndex < values.length) ? values[valueIndex++] : "";
                }
            }
            tableModel.addRow(row);
        }
    }

    /**
     * 添加一行并滚动到最后
     */
    public void addRowAndScroll() {
        addRow();
        scrollToLastRow();
    }

    /**
     * 删除当前选中的行
     */
    public void deleteSelectedRow() {
        stopCellEditing();

        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            // Convert view index to model index if using row sorter
            int modelRow = selectedRow;
            if (table.getRowSorter() != null) {
                modelRow = table.getRowSorter().convertRowIndexToModel(selectedRow);
            }

            if (modelRow >= 0 && modelRow < tableModel.getRowCount()) {
                int rowCount = tableModel.getRowCount();

                // Don't delete if it's the only row (keep at least one empty row like Postman)
                if (rowCount <= 1) {
                    return;
                }

                // Check if this row can be deleted (子类可以重写 isDeletableRow 来添加额外的检查)
                if (!isDeletableRow(modelRow)) {
                    return;
                }

                // Delete the row
                tableModel.removeRow(modelRow);
                ensureEmptyLastRow();
            }
        }
    }

    /**
     * 检查指定行是否可以删除。子类可以重写此方法来添加额外的检查逻辑。
     *
     * @param modelRow 模型行索引
     * @return true 如果可以删除
     */
    protected boolean isDeletableRow(int modelRow) {
        return true;
    }

    /**
     * 判断指定视图单元格是否是当前可点击的删除按钮。
     */
    protected boolean isDeleteActionAvailableAtViewPosition(int viewRow, int viewColumn) {
        if (viewRow < 0 || viewColumn != getDeleteColumnIndex()) {
            return false;
        }
        int modelRow = (table.getRowSorter() != null)
                ? table.getRowSorter().convertRowIndexToModel(viewRow) : viewRow;
        return isDeleteActionAvailableAtModelRow(modelRow);
    }

    /**
     * 判断指定模型行是否展示并响应删除按钮。
     */
    private boolean isDeleteActionAvailableAtModelRow(int modelRow) {
        if (!editable || modelRow < 0 || modelRow >= tableModel.getRowCount()) {
            return false;
        }
        if (!isDeletableRow(modelRow)) {
            return false;
        }

        int rowCount = tableModel.getRowCount();
        boolean isLastRow = modelRow == rowCount - 1;
        boolean isEmpty = !hasContentInRow(modelRow);
        return !isLastRow || (!isEmpty && rowCount > 1);
    }

    /**
     * 删除按钮悬停时由 JTable 自身切换鼠标形态；renderer 组件上的 cursor 不会生效。
     */
    private void updateDeleteButtonCursor(int viewRow, int viewColumn) {
        int cursorType = isDeleteActionAvailableAtViewPosition(viewRow, viewColumn)
                ? Cursor.HAND_CURSOR
                : Cursor.DEFAULT_CURSOR;
        if (table.getCursor().getType() != cursorType) {
            table.setCursor(Cursor.getPredefinedCursor(cursorType));
        }
    }


    /**
     * 添加右键菜单监听器
     */
    protected void addTableRightMouseListener() {
        MouseAdapter tableMouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }

            private void showPopupMenu(MouseEvent e) {
                if (!editable || !isContextMenuEnabled()) {
                    return;
                }

                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                }

                JPopupMenu contextMenu = createContextPopupMenu(viewRow);
                contextMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        };

        table.addMouseListener(tableMouseListener);
    }

    /**
     * 创建右键菜单
     * 子类可以重写此方法来自定义菜单项
     *
     * @param viewRow 当前选中的视图行索引，-1 表示未选中任何行
     * @return 右键菜单
     */
    protected JPopupMenu createContextPopupMenu(int viewRow) {
        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);

        JMenuItem addItem = new JMenuItem("Add");
        addItem.addActionListener(e -> addRowAndScroll());
        menu.add(addItem);

        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(e -> deleteSelectedRow());
        menu.add(removeItem);

        return menu;
    }

    protected boolean isContextMenuEnabled() {
        return true;
    }

    /**
     * 添加删除按钮监听器
     */
    protected void addDeleteButtonListener() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (editable && SwingUtilities.isLeftMouseButton(e)) {
                    handleDeleteButtonClick(e);
                }
            }
        });
    }

    /** 处理删除列的点击事件 */
    private void handleDeleteButtonClick(MouseEvent e) {
        int column = table.columnAtPoint(e.getPoint());
        int row    = table.rowAtPoint(e.getPoint());

        if (!isDeleteActionAvailableAtViewPosition(row, column)) {
            return;
        }

        int modelRow = (table.getRowSorter() != null)
                ? table.getRowSorter().convertRowIndexToModel(row) : row;

        stopCellEditing();
        tableModel.removeRow(modelRow);
        ensureEmptyLastRow();
        hoveredRow = -1;
        hoveredColumn = -1;
        updateDeleteButtonCursor(-1, -1);
        table.repaint();
    }

    /**
     * 添加表格监听器（右键菜单和删除按钮）
     */
    protected void setupTableListeners() {
        addTableRightMouseListener();
        addDeleteButtonListener();
    }

    /**
     * 设置表格是否可编辑
     *
     * @param editable 是否可编辑
     */
    public void setEditable(boolean editable) {
        this.editable = editable;
        updateDeleteButtonCursor(hoveredRow, hoveredColumn);
        table.repaint(); // 刷新显示
    }

    /**
     * 滚动到最后一行
     */
    protected void scrollToLastRow() {
        SwingUtilities.invokeLater(() -> {
            int rowCount = table.getRowCount();
            if (rowCount > 0) {
                Rectangle rect = table.getCellRect(rowCount - 1, 0, true);
                table.scrollRectToVisible(rect);
            }
        });
    }

    /**
     * 从表格模型的指定行获取字符串值
     *
     * @param row 行索引
     * @param col 列索引
     * @return 字符串值，null 转为空字符串
     */
    protected String getStringValue(int row, int col) {
        Object obj = tableModel.getValueAt(row, col);
        return obj == null ? "" : obj.toString().trim();
    }

    /**
     * 从表格模型的指定行获取布尔值
     *
     * @param row 行索引
     * @param col 列索引
     * @return 布尔值，非Boolean类型默认为true
     */
    protected boolean getBooleanValue(int row, int col) {
        Object obj = tableModel.getValueAt(row, col);
        return !(obj instanceof Boolean b) || b;
    }

    /**
     * 添加自动追加空行功能
     * 当最后一行有内容时，自动在末尾添加新的空行（类似Postman行为）
     */
    protected void addAutoAppendRowFeature() {
        tableModel.addTableModelListener(e -> {
            if (suppressAutoAppendRow || !editable) {
                return;
            }

            SwingUtilities.invokeLater(() -> {
                try {
                    int rowCount = tableModel.getRowCount();
                    if (rowCount == 0) {
                        return;
                    }

                    // Check if the last row has any content
                    int lastRow = rowCount - 1;
                    boolean lastRowHasContent = hasContentInRow(lastRow);

                    // Add empty row if last row has content
                    if (lastRowHasContent) {
                        suppressAutoAppendRow = true;
                        try {
                            tableModel.addRow(createEmptyRow());
                        } finally {
                            suppressAutoAppendRow = false;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error in auto-append row feature", ex);
                }
            });
        });
    }

    protected class EnabledColumnRenderer extends JCheckBox implements TableCellRenderer {
        private EnabledColumnRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
            setBorderPainted(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            setSelected(value instanceof Boolean selected && selected);
            setBackground(TableUIConstants.getCellBackground(isSelected, row == hoveredRow, false, table, row));
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }
    }

    /**
     * 删除列保留可点击区域，可删除行始终显示图标。
     */
    protected class DeleteButtonRenderer extends JLabel implements TableCellRenderer {
        private final FlatSVGIcon deleteIcon;

        public DeleteButtonRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
            deleteIcon = IconUtil.createThemed("icons/delete.svg", 18, 18);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            // Set background
            setBackground(TableUIConstants.getCellBackground(isSelected, row == hoveredRow, false, table, row));
            setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());

            // Clear icon by default
            setIcon(null);
            setCursor(Cursor.getDefaultCursor());

            // Convert view row to model row
            int modelRow = row;
            if (table.getRowSorter() != null) {
                modelRow = table.getRowSorter().convertRowIndexToModel(row);
            }

            boolean deleteAvailable = isDeleteActionAvailableAtModelRow(modelRow);
            setToolTipText(deleteAvailable ? CommonI18n.get(CommonMessageKeys.BUTTON_DELETE) : null);

            if (deleteAvailable) {
                setIcon(deleteIcon);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                if (!isSelected && row == hoveredRow && column == hoveredColumn) {
                    setBackground(TableUIConstants.getCellBackground(false, true, false, table, row));
                }
            }

            return this;
        }
    }

    // ========== Tab 键导航功能 ==========

    /**
     * 设置Tab键导航，支持在可编辑单元格之间跳转
     */
    protected void setupTabKeyNavigation() {
        InputMap inputMap = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = table.getActionMap();

        // Tab key - move to next editable cell
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0), "nextCell");
        actionMap.put("nextCell", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                moveToNextEditableCell(false);
            }
        });

        // Shift+Tab - move to previous editable cell
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, java.awt.event.InputEvent.SHIFT_DOWN_MASK), "previousCell");
        actionMap.put("previousCell", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                moveToNextEditableCell(true);
            }
        });
    }

    /**
     * 移动到下一个（或上一个）可编辑单元格
     *
     * @param reverse true 表示向后移动（Shift+Tab），false 表示向前移动（Tab）
     */
    protected void moveToNextEditableCell(boolean reverse) {
        // 优先使用编辑中的行列（比 selectedRow/Col 更准确），
        // 因为单击直接进入编辑时不一定同时更新 selection。
        int currentRow    = table.isEditing() ? table.getEditingRow()    : table.getSelectedRow();
        int currentColumn = table.isEditing() ? table.getEditingColumn() : table.getSelectedColumn();

        if (currentRow < 0 || currentColumn < 0) {
            startEditAt(0, getFirstEditableColumnIndex());
            return;
        }

        // 先提交当前编辑，再寻找目标单元格
        // stopCellEditing() 可能返回 false（例如 EasySmartValueCellEditor 的 ignoreFocusLost 保护），
        // 此时使用 forceStop（对智能编辑器）或 cancelCellEditing() 强制退出，确保 Tab 导航始终能继续。
        if (table.isEditing()) {
            TableCellEditor currentEditor = table.getCellEditor();
            if (currentEditor != null) {
                boolean stopped = false;
                if (currentEditor instanceof EasySmartValueCellEditor smartEditor) {
                    stopped = smartEditor.forceStopCellEditing();
                } else {
                    stopped = currentEditor.stopCellEditing();
                }
                if (!stopped) {
                    currentEditor.cancelCellEditing();
                }
            }
        }

        if (!reverse) {
            // Tab 向前：检测是否在最后一行的最后一个可编辑列
            boolean isLastRow = (currentRow == table.getRowCount() - 1);
            boolean isLastEditableCol = (currentColumn == getLastEditableColumnIndex());
            if (isLastRow && isLastEditableCol && editable) {
                // 类似 Enter 在 Value 列的行为：追加新行并跳到它的 Key 列
                SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
                    int last = table.getRowCount() - 1;
                    if (last > currentRow) {
                        startEditAt(last, getFirstEditableColumnIndex());
                    }
                    // 若 autoAppend 未追加（当前行是空行），停留不动
                }));
                return;
            }
        }

        // 计算目标单元格（stopCellEditing 是同步的，但 model 更新可能触发事件，用 invokeLater 保证时序）
        final int[] target = reverse
                ? findPreviousEditableCell(currentRow, currentColumn)
                : findNextEditableCell(currentRow, currentColumn);

        final int targetRow = target[0];
        final int targetCol = target[1];
        final int columnCount = table.getColumnCount();

        SwingUtilities.invokeLater(() -> {
            if (targetCol >= 0 && targetCol < columnCount && targetRow >= 0 && targetRow < table.getRowCount()) {
                startEditAt(targetRow, targetCol);
            }
        });
    }

    /** 向前（Tab）搜索下一个可编辑单元格，返回 [row, col] */
    private int[] findNextEditableCell(int row, int col) {
        int columnCount  = table.getColumnCount();
        int maxAttempts  = columnCount * (table.getRowCount() + 1);
        for (int i = 0; i < maxAttempts; i++) {
            col++;
            if (col >= columnCount) {
                if (row < table.getRowCount() - 1) {
                    row++;
                    col = getFirstEditableColumnIndex() - 1; // 下一次循环 +1 后正好是 firstEditable
                } else {
                    // 已到末尾（此分支在 moveToNextEditableCell 中已提前处理），停在最后一个可编辑列
                    return new int[]{row, getLastEditableColumnIndex()};
                }
                continue; // 让循环 col++ 来到正确位置
            }
            if (isCellEditableForNavigation(row, col)) {
                return new int[]{row, col};
            }
        }
        return new int[]{row, getFirstEditableColumnIndex()};
    }

    /** 向后（Shift+Tab）搜索上一个可编辑单元格，返回 [row, col] */
    private int[] findPreviousEditableCell(int row, int col) {
        int columnCount = table.getColumnCount();
        int maxAttempts = columnCount * (table.getRowCount() + 1);
        for (int i = 0; i < maxAttempts; i++) {
            col--;
            if (col < 0) {
                if (row > 0) {
                    row--;
                    col = getLastEditableColumnIndex() + 1; // 下一次循环 -1 后正好是 lastEditable
                } else {
                    // 已到开头，停在第一个可编辑列
                    return new int[]{row, getFirstEditableColumnIndex()};
                }
                continue;
            }
            if (isCellEditableForNavigation(row, col)) {
                return new int[]{row, col};
            }
        }
        return new int[]{row, getFirstEditableColumnIndex()};
    }

    /** 开始编辑指定单元格并聚焦当前可见的编辑组件 */
    private void startEditAt(int row, int col) {
        table.changeSelection(row, col, false, false);
        table.editCellAt(row, col);
        // 延迟获取焦点：editCellAt 可能异步完成，invokeLater 确保编辑器已渲染
        SwingUtilities.invokeLater(() -> {
            Component ed = table.getEditorComponent();
            if (ed == null) return;
            focusEditorTextComponent(ed, true);
        });
    }
}
