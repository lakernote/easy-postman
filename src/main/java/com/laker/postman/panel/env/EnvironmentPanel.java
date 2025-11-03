package com.laker.postman.panel.env;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.SingletonBasePanel;
import com.laker.postman.common.SingletonFactory;
import com.laker.postman.common.component.SearchTextField;
import com.laker.postman.common.component.combobox.EnvironmentComboBox;
import com.laker.postman.common.component.list.EnvironmentListCellRenderer;
import com.laker.postman.common.component.table.EasyPostmanEnvironmentTablePanel;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.model.Environment;
import com.laker.postman.model.EnvironmentItem;
import com.laker.postman.model.EnvironmentVariable;
import com.laker.postman.model.Workspace;
import com.laker.postman.panel.topmenu.TopMenuBarPanel;
import com.laker.postman.service.EnvironmentService;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.service.postman.PostmanImport;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.NotificationUtil;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 环境变量管理面板
 */
@Slf4j
public class EnvironmentPanel extends SingletonBasePanel {
    public static final String SAVE_VARIABLES = "saveVariables";
    public static final String EXPORT_FILE_NAME = "EasyPostman-Environments.json";
    private EasyPostmanEnvironmentTablePanel variablesTablePanel;
    private transient Environment currentEnvironment;
    private JList<EnvironmentItem> environmentList;
    private DefaultListModel<EnvironmentItem> environmentListModel;
    private JTextField searchField;
    private String originalVariablesSnapshot; // 原始变量快照，直接用json字符串
    private boolean isLoadingData = false; // 用于控制是否正在加载数据，防止自动保存

    @Override
    protected void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY));
        setPreferredSize(new Dimension(700, 400));

        // 左侧环境列表面板
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(250, 400));
        // 顶部搜索和导入导出按钮
        leftPanel.add(getSearchAndImportPanel(), BorderLayout.NORTH);

        // 环境列表
        environmentListModel = new DefaultListModel<>();
        environmentList = new JList<>(environmentListModel);
        environmentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        environmentList.setFixedCellHeight(28); // 设置每行高度
        environmentList.setCellRenderer(new EnvironmentListCellRenderer());
        environmentList.setFixedCellWidth(0); // 让JList自适应宽度
        environmentList.setVisibleRowCount(-1); // 让JList显示所有行
        JScrollPane envListScroll = new JScrollPane(environmentList);
        envListScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); // 禁用横向滚动条
        leftPanel.add(envListScroll, BorderLayout.CENTER);
        add(leftPanel, BorderLayout.WEST);

        // 右侧 导入 导出 变量表格及操作
        JPanel rightPanel = new JPanel(new BorderLayout());
        // 变量表格
        variablesTablePanel = new EasyPostmanEnvironmentTablePanel();
        rightPanel.add(variablesTablePanel, BorderLayout.CENTER);

        add(rightPanel, BorderLayout.CENTER);

        // 初始化表格验证和自动保存功能
        initTableValidationAndAutoSave();
    }

    /**
     * 自动保存功能
     */
    private void initTableValidationAndAutoSave() {

        // 添加表格模型监听器，实现自动保存
        variablesTablePanel.addTableModelListener(e -> {
            if (currentEnvironment == null || isLoadingData) return;

            // 防止在加载数据时触发自动保存
            if (e.getType() == TableModelEvent.INSERT ||
                    e.getType() == TableModelEvent.UPDATE ||
                    e.getType() == TableModelEvent.DELETE) {

                // 使用 SwingUtilities.invokeLater 确保在事件处理完成后执行保存
                SwingUtilities.invokeLater(() -> {
                    // 在拖拽期间跳过自动保存，避免保存中间状态
                    if (!isLoadingData && !variablesTablePanel.isDragging() && isVariablesChanged()) {
                        autoSaveVariables();
                    }
                });
            }
        });
    }

    /**
     * 自动保存变量（无提示框版本）
     */
    private void autoSaveVariables() {
        if (currentEnvironment == null) return;

        try {
            variablesTablePanel.stopCellEditing();
            List<EnvironmentVariable> variableList = variablesTablePanel.getVariableList();
            currentEnvironment.setVariableList(new ArrayList<>(variableList)); // 使用副本避免并发修改
            EnvironmentService.saveEnvironment(currentEnvironment);
            // 保存后更新快照
            originalVariablesSnapshot = JSONUtil.toJsonStr(currentEnvironment.getVariableList());
            log.debug("自动保存环境变量: {}", currentEnvironment.getName());
        } catch (Exception ex) {
            log.error("自动保存环境变量失败", ex);
        }
    }

    private JPanel getSearchAndImportPanel() {
        JPanel importExportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        importExportPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JButton importBtn = new JButton(new FlatSVGIcon("icons/import.svg", 20, 20));
        importBtn.setFocusPainted(false);
        importBtn.setBackground(Color.WHITE);
        importBtn.setIconTextGap(6);
        JPopupMenu importMenu = new JPopupMenu();
        JMenuItem importEasyToolsItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.ENV_MENU_IMPORT_EASY),
                new FlatSVGIcon("icons/easy.svg", 20, 20));
        importEasyToolsItem.addActionListener(e -> importEnvironments());
        JMenuItem importPostmanItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.ENV_MENU_IMPORT_POSTMAN),
                new FlatSVGIcon("icons/postman.svg", 20, 20));
        importPostmanItem.addActionListener(e -> importPostmanEnvironments());
        importMenu.add(importEasyToolsItem);
        importMenu.add(importPostmanItem);
        importBtn.addActionListener(e -> importMenu.show(importBtn, 0, importBtn.getHeight()));
        importExportPanel.add(importBtn);

        JButton exportBtn = new JButton(new FlatSVGIcon("icons/export.svg", 20, 20));
        exportBtn.setFocusPainted(false);
        exportBtn.setBackground(Color.WHITE);
        exportBtn.setIconTextGap(6);
        exportBtn.addActionListener(e -> exportEnvironments());
        importExportPanel.add(exportBtn);

        searchField = new SearchTextField();
        importExportPanel.add(searchField);
        return importExportPanel;
    }

    @Override
    protected void registerListeners() {
        // 联动菜单栏右上角下拉框
        EnvironmentComboBox topComboBox = SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox();
        if (topComboBox != null) {
            topComboBox.setOnEnvironmentChange(env -> {
                environmentListModel.clear();
                List<Environment> envs = EnvironmentService.getAllEnvironments();
                for (Environment envItem : envs) {
                    environmentListModel.addElement(new EnvironmentItem(envItem));
                }
                if (!environmentListModel.isEmpty()) {
                    environmentList.setSelectedIndex(topComboBox.getSelectedIndex()); // 设置选中当前激活环境
                }
                loadActiveEnvironmentVariables();
            });
        }
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                reloadEnvironmentList(searchField.getText());
            }

            public void removeUpdate(DocumentEvent e) {
                reloadEnvironmentList(searchField.getText());
            }

            public void changedUpdate(DocumentEvent e) {
                reloadEnvironmentList(searchField.getText());
            }
        });
        environmentList.addListSelectionListener(e -> { // 监听环境列表左键
            if (!e.getValueIsAdjusting()) {
                EnvironmentItem item = environmentList.getSelectedValue();
                if (item == null || item.getEnvironment() == currentEnvironment) {
                    return; // 没有切换环境，不处理
                }
                if (isVariablesChanged()) {
                    int option = JOptionPane.showConfirmDialog(this,
                            I18nUtil.getMessage(MessageKeys.ENV_DIALOG_SAVE_CHANGES),
                            I18nUtil.getMessage(MessageKeys.ENV_DIALOG_SAVE_CHANGES_TITLE),
                            JOptionPane.YES_NO_OPTION);
                    if (option == JOptionPane.YES_OPTION) {
                        saveVariables();
                    } else {
                        loadVariables(currentEnvironment);
                    }
                }
                currentEnvironment = item.getEnvironment();
                loadVariables(currentEnvironment);
            }
        });
        // 环境列表右键菜单
        addRightMenuList();

        addSaveKeyStroke();

        // 默认加载当前激活环境变量
        loadActiveEnvironmentVariables();

        // 环境列表加载与搜索
        reloadEnvironmentList("");

    }

    private void addSaveKeyStroke() {
        // 右键菜单由EasyTablePanel自带，无需再注册
        // 增加 Command+S 保存快捷键（兼容 Mac 和 Windows Ctrl+S）
        KeyStroke saveKeyStroke = KeyStroke.getKeyStroke("meta S"); // Mac Command+S
        KeyStroke saveKeyStroke2 = KeyStroke.getKeyStroke("control S"); // Windows/Linux Ctrl+S
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(saveKeyStroke, SAVE_VARIABLES);
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(saveKeyStroke2, SAVE_VARIABLES);
        this.getActionMap().put(SAVE_VARIABLES, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveVariables();
            }
        });
    }

    private void addRightMenuList() {
        JPopupMenu envListMenu = new JPopupMenu();
        JMenuItem addItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.ENV_BUTTON_ADD), new FlatSVGIcon("icons/environments.svg", 16, 16));
        addItem.addActionListener(e -> addEnvironment());
        envListMenu.add(addItem);
        envListMenu.addSeparator();
        JMenuItem renameItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.ENV_BUTTON_RENAME), new FlatSVGIcon("icons/refresh.svg", 16, 16));
        // 设置 F2 快捷键显示
        renameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        JMenuItem copyItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.ENV_BUTTON_DUPLICATE), new FlatSVGIcon("icons/duplicate.svg", 16, 16)); // 复制菜单项
        JMenuItem deleteItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.ENV_BUTTON_DELETE), new FlatSVGIcon("icons/close.svg", 16, 16));
        // 设置 Delete 快捷键显示
        deleteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        JMenuItem exportPostmanItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.ENV_BUTTON_EXPORT_POSTMAN), new FlatSVGIcon("icons/postman.svg", 16, 16));
        exportPostmanItem.addActionListener(e -> exportSelectedEnvironmentAsPostman());
        renameItem.addActionListener(e -> renameSelectedEnvironment());
        copyItem.addActionListener(e -> copySelectedEnvironment()); // 复制事件
        deleteItem.addActionListener(e -> deleteSelectedEnvironment());
        envListMenu.add(renameItem);
        envListMenu.add(copyItem);
        envListMenu.add(deleteItem);
        envListMenu.addSeparator();
        envListMenu.add(exportPostmanItem);

        // 转移到其他工作区
        JMenuItem moveToWorkspaceItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.ENV_MENU_MOVE_TO_WORKSPACE), new FlatSVGIcon("icons/workspace.svg", 16, 16));
        moveToWorkspaceItem.addActionListener(e -> moveEnvironmentToWorkspace());
        envListMenu.add(moveToWorkspaceItem);

        // 添加键盘监听器，支持 F2 重命名和 Delete 删除
        environmentList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                EnvironmentItem selectedItem = environmentList.getSelectedValue();
                if (selectedItem != null) {
                    if (e.getKeyCode() == KeyEvent.VK_F2) {
                        // F2 重命名
                        renameSelectedEnvironment();
                    } else if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                        // Delete 或 Backspace 删除（Mac 上常用 Backspace）
                        deleteSelectedEnvironment();
                    }
                }
            }
        });

        environmentList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) { // 右键菜单
                    int idx = environmentList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        environmentList.setSelectedIndex(idx);
                    }
                    envListMenu.show(environmentList, e.getX(), e.getY());
                }
                // 双击激活环境并联动下拉框
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    int idx = environmentList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        environmentList.setSelectedIndex(idx);
                        EnvironmentItem item = environmentList.getModel().getElementAt(idx);
                        if (item != null) {
                            Environment env = item.getEnvironment();
                            // 激活环境
                            EnvironmentService.setActiveEnvironment(env.getId());
                            // 联动顶部下拉框
                            EnvironmentComboBox comboBox = SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox();
                            if (comboBox != null) {
                                comboBox.setSelectedEnvironment(env);
                            }
                            // 刷新面板
                            refreshUI();
                        }
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) mousePressed(e);
            }
        });
        // 拖拽排序支持
        environmentList.setDragEnabled(true);
        environmentList.setDropMode(DropMode.INSERT);
        environmentList.setTransferHandler(new TransferHandler() {
            private int fromIndex = -1;

            @Override
            protected Transferable createTransferable(JComponent c) {
                fromIndex = environmentList.getSelectedIndex();
                EnvironmentItem selected = environmentList.getSelectedValue();
                return new StringSelection(selected != null ? selected.toString() : "");
            }

            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop();
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
                int toIndex = dl.getIndex();
                if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false;
                EnvironmentItem moved = environmentListModel.getElementAt(fromIndex);
                environmentListModel.remove(fromIndex);
                if (toIndex > fromIndex) toIndex--;
                environmentListModel.add(toIndex, moved);
                environmentList.setSelectedIndex(toIndex);
                // 1. 同步顺序到 EnvironmentService
                persistEnvironmentOrder();
                // 2. 同步到顶部下拉框
                syncComboBoxOrder();
                return true;
            }
        });
    }

    /**
     * 只加载当前激活环境变量
     */
    public void loadActiveEnvironmentVariables() {
        Environment env = EnvironmentService.getActiveEnvironment();
        currentEnvironment = env;
        loadVariables(env);
    }

    private void loadVariables(Environment env) {
        variablesTablePanel.stopCellEditing();
        currentEnvironment = env;
        variablesTablePanel.clear();
        isLoadingData = true; // 设置标志位，开始加载数据
        if (env != null) {
            variablesTablePanel.setVariableList(env.getVariableList());
            originalVariablesSnapshot = JSONUtil.toJsonStr(env.getVariableList()); // 用rows做快照，保证同步
        } else {
            variablesTablePanel.clear();
            originalVariablesSnapshot = JSONUtil.toJsonStr(new ArrayList<>()); // 空快照
        }
        isLoadingData = false; // 清除标志位，结束加载数据
    }

    /**
     * 保存表格中的变量到当前环境
     */
    public void saveVariables() {
        if (currentEnvironment == null) return;
        variablesTablePanel.stopCellEditing();

        // 保存到新格式 variableList
        List<EnvironmentVariable> variableList = variablesTablePanel.getVariableList();
        currentEnvironment.setVariableList(new ArrayList<>(variableList)); // 使用副本避免并发修改
        EnvironmentService.saveEnvironment(currentEnvironment);
        // 保存后更新快照为json字符串
        originalVariablesSnapshot = JSONUtil.toJsonStr(currentEnvironment.getVariableList());
    }

    /**
     * 导出所有环境变量为JSON文件
     */
    private void exportEnvironments() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(I18nUtil.getMessage(MessageKeys.ENV_DIALOG_EXPORT_TITLE));
        fileChooser.setSelectedFile(new File(EXPORT_FILE_NAME));
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(fileToSave), StandardCharsets.UTF_8)) {
                java.util.List<Environment> envs = EnvironmentService.getAllEnvironments();
                writer.write(JSONUtil.toJsonPrettyStr(envs));
                NotificationUtil.showSuccess(I18nUtil.getMessage(MessageKeys.ENV_DIALOG_EXPORT_SUCCESS));
            } catch (Exception ex) {
                log.error("Export Error", ex);
                JOptionPane.showMessageDialog(this,
                        I18nUtil.getMessage(MessageKeys.ENV_DIALOG_EXPORT_FAIL, ex.getMessage()),
                        I18nUtil.getMessage(MessageKeys.GENERAL_ERROR), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 导入环境变量JSON文件
     */
    private void importEnvironments() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(I18nUtil.getMessage(MessageKeys.ENV_DIALOG_IMPORT_EASY_TITLE));
        int userSelection = fileChooser.showOpenDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToOpen = fileChooser.getSelectedFile();
            try {
                java.util.List<Environment> envs = JSONUtil.toList(JSONUtil.readJSONArray(fileToOpen, StandardCharsets.UTF_8), Environment.class);
                // 导入新环境
                refreshListAndComboFromAdd(envs);
            } catch (Exception ex) {
                log.error("Import Error", ex);
                JOptionPane.showMessageDialog(this,
                        I18nUtil.getMessage(MessageKeys.ENV_DIALOG_IMPORT_EASY_FAIL, ex.getMessage()),
                        I18nUtil.getMessage(MessageKeys.GENERAL_ERROR), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refreshListAndComboFromAdd(List<Environment> envs) {
        EnvironmentComboBox environmentComboBox = SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox();
        for (Environment env : envs) {
            EnvironmentService.saveEnvironment(env);
            environmentComboBox.addItem(new EnvironmentItem(env)); // 添加到下拉框
            environmentListModel.addElement(new EnvironmentItem(env)); // 添加到列表
        }
        NotificationUtil.showSuccess(I18nUtil.getMessage(MessageKeys.ENV_DIALOG_IMPORT_EASY_SUCCESS));
    }

    /**
     * 导入Postman环境变量JSON文件
     */
    private void importPostmanEnvironments() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(I18nUtil.getMessage(MessageKeys.ENV_DIALOG_IMPORT_POSTMAN_TITLE));
        int userSelection = fileChooser.showOpenDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            java.io.File fileToOpen = fileChooser.getSelectedFile();
            try {
                String json = FileUtil.readString(fileToOpen, StandardCharsets.UTF_8);
                List<Environment> envs = PostmanImport.parsePostmanEnvironments(json);
                if (!envs.isEmpty()) {
                    // 导入新环境
                    refreshListAndComboFromAdd(envs);
                } else {
                    JOptionPane.showMessageDialog(this,
                            I18nUtil.getMessage(MessageKeys.ENV_DIALOG_IMPORT_POSTMAN_INVALID),
                            I18nUtil.getMessage(MessageKeys.ENV_DIALOG_IMPORT_POSTMAN_TITLE), JOptionPane.WARNING_MESSAGE);
                }
            } catch (Exception ex) {
                log.error("Import Error", ex);
                JOptionPane.showMessageDialog(this,
                        I18nUtil.getMessage(MessageKeys.ENV_DIALOG_IMPORT_POSTMAN_FAIL, ex.getMessage()),
                        I18nUtil.getMessage(MessageKeys.GENERAL_ERROR), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 新增环境
    private void addEnvironment() {
        String name = JOptionPane.showInputDialog(this,
                I18nUtil.getMessage(MessageKeys.ENV_DIALOG_ADD_PROMPT),
                I18nUtil.getMessage(MessageKeys.ENV_DIALOG_ADD_TITLE), JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            Environment env = new Environment(name.trim());
            env.setId("env-" + IdUtil.simpleUUID());
            EnvironmentService.saveEnvironment(env);
            environmentListModel.addElement(new EnvironmentItem(env));
            environmentList.setSelectedValue(new EnvironmentItem(env), true);
            EnvironmentComboBox environmentComboBox = SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox();
            if (environmentComboBox != null) {
                environmentComboBox.addItem(new EnvironmentItem(env));
            }
        }
    }

    private void reloadEnvironmentList(String filter) {
        environmentListModel.clear();
        java.util.List<Environment> envs = EnvironmentService.getAllEnvironments();
        int activeIdx = -1;
        for (Environment env : envs) {
            if (filter == null || filter.isEmpty() || env.getName().toLowerCase().contains(filter.toLowerCase())) {
                environmentListModel.addElement(new EnvironmentItem(env));
                if (env.isActive()) {
                    activeIdx = environmentListModel.size() - 1;
                }
            }
        }
        if (!environmentListModel.isEmpty()) {
            environmentList.setSelectedIndex(Math.max(activeIdx, 0));
        }
    }

    private void renameSelectedEnvironment() {
        EnvironmentItem item = environmentList.getSelectedValue();
        if (item == null) return;
        Environment env = item.getEnvironment();
        Object result = JOptionPane.showInputDialog(this,
                I18nUtil.getMessage(MessageKeys.ENV_DIALOG_RENAME_PROMPT),
                I18nUtil.getMessage(MessageKeys.ENV_DIALOG_RENAME_TITLE),
                JOptionPane.PLAIN_MESSAGE, null, null, env.getName());
        if (result != null) {
            String newName = result.toString().trim();
            if (!newName.isEmpty() && !newName.equals(env.getName())) {
                env.setName(newName);
                EnvironmentService.saveEnvironment(env);
                environmentListModel.setElementAt(new EnvironmentItem(env), environmentList.getSelectedIndex());
                // 同步刷新顶部环境下拉框
                SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox().reload();
            } else {
                JOptionPane.showMessageDialog(this,
                        I18nUtil.getMessage(MessageKeys.ENV_DIALOG_RENAME_FAIL),
                        I18nUtil.getMessage(MessageKeys.ENV_DIALOG_SAVE_CHANGES_TITLE), JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void deleteSelectedEnvironment() {
        EnvironmentItem item = environmentList.getSelectedValue();
        if (item == null) return;
        Environment env = item.getEnvironment();
        int confirm = JOptionPane.showConfirmDialog(this,
                I18nUtil.getMessage(MessageKeys.ENV_DIALOG_DELETE_PROMPT, env.getName()),
                I18nUtil.getMessage(MessageKeys.ENV_DIALOG_DELETE_TITLE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            environmentListModel.removeElement(new EnvironmentItem(env));
            EnvironmentService.deleteEnvironment(env.getId());
            SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox().reload(); // 刷新顶部下拉框
            // 设置当前的变量表格为激活环境
            loadActiveEnvironmentVariables();
        }
    }

    // 复制环境方法
    private void copySelectedEnvironment() {
        EnvironmentItem item = environmentList.getSelectedValue();
        if (item == null) return;
        Environment env = item.getEnvironment();
        try {
            Environment copy = new Environment(env.getName() + " " + I18nUtil.getMessage(MessageKeys.ENV_NAME_COPY_SUFFIX));
            copy.setId("env-" + IdUtil.simpleUUID());
            // 复制变量
            for (String key : env.getVariables().keySet()) {
                copy.addVariable(key, env.getVariable(key));
            }
            EnvironmentService.saveEnvironment(copy);
            EnvironmentItem copyItem = new EnvironmentItem(copy);
            environmentListModel.addElement(copyItem);
            EnvironmentComboBox environmentComboBox = SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox();
            if (environmentComboBox != null) {
                environmentComboBox.addItem(copyItem);
            }
            environmentList.setSelectedValue(copyItem, true);
        } catch (Exception ex) {
            log.error("复制环境失败", ex);
            JOptionPane.showMessageDialog(this,
                    I18nUtil.getMessage(MessageKeys.ENV_DIALOG_COPY_FAIL, ex.getMessage()),
                    I18nUtil.getMessage(MessageKeys.GENERAL_ERROR), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 刷新整个环境面板（列表和变量表格，保持激活环境高亮和选中）
     */
    public void refreshUI() {
        // 获取当前激活环境id
        Environment active = EnvironmentService.getActiveEnvironment();
        String activeId = active != null ? active.getId() : null;
        // 重新加载环境列表
        environmentListModel.clear();
        java.util.List<Environment> envs = EnvironmentService.getAllEnvironments();
        int selectIdx = -1;
        for (int i = 0; i < envs.size(); i++) {
            Environment env = envs.get(i);
            EnvironmentItem item = new EnvironmentItem(env);
            environmentListModel.addElement(item);
            if (activeId != null && activeId.equals(env.getId())) {
                selectIdx = i;
            }
        }
        // 先取消选中再选中，强制触发 selection 事件，保证表格刷新
        environmentList.clearSelection();
        if (selectIdx >= 0) {
            environmentList.setSelectedIndex(selectIdx);
            environmentList.ensureIndexIsVisible(selectIdx);
        }
        // 强制刷新变量表格，防止selection事件未触发
        EnvironmentItem selectedItem = environmentList.getSelectedValue();
        if (selectedItem != null) {
            loadVariables(selectedItem.getEnvironment());
        } else {
            variablesTablePanel.clear();
        }
    }

    // 判断当前表格内容和快照是否一致，使用JSON序列化比较
    private boolean isVariablesChanged() {
        String curJson = JSONUtil.toJsonStr(variablesTablePanel.getVariableList());
        boolean isVariablesChanged = !CharSequenceUtil.equals(curJson, originalVariablesSnapshot);
        if (isVariablesChanged) {
            log.debug("env name: {}", currentEnvironment != null ? currentEnvironment.getName() : "null");
            log.debug("current  variables: {}", curJson);
            log.debug("original variables: {}", originalVariablesSnapshot);
        }
        return isVariablesChanged;
    }

    // 导出选中环境为Postman格式
    private void exportSelectedEnvironmentAsPostman() {
        EnvironmentItem item = environmentList.getSelectedValue();
        if (item == null) return;
        Environment env = item.getEnvironment();
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(I18nUtil.getMessage(MessageKeys.ENV_DIALOG_EXPORT_POSTMAN_TITLE));
        fileChooser.setSelectedFile(new File(env.getName() + "-postman-env.json"));
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try {
                // 只导出当前环境为Postman格式
                String postmanEnvJson = PostmanImport.toPostmanEnvironmentJson(env);
                FileUtil.writeUtf8String(postmanEnvJson, fileToSave);
                NotificationUtil.showSuccess(I18nUtil.getMessage(MessageKeys.ENV_DIALOG_EXPORT_POSTMAN_SUCCESS));
            } catch (Exception ex) {
                log.error("导出Postman环境失败", ex);
                JOptionPane.showMessageDialog(this,
                        I18nUtil.getMessage(MessageKeys.ENV_DIALOG_EXPORT_POSTMAN_FAIL, ex.getMessage()),
                        I18nUtil.getMessage(MessageKeys.GENERAL_ERROR), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 拖拽后持久化顺序
     */
    private void persistEnvironmentOrder() {
        List<String> idOrder = new ArrayList<>();
        for (int i = 0; i < environmentListModel.size(); i++) {
            idOrder.add(environmentListModel.get(i).getEnvironment().getId());
        }
        EnvironmentService.saveEnvironmentOrder(idOrder);
    }

    /**
     * 拖拽后同步顶部下拉框顺序
     */
    private void syncComboBoxOrder() {
        EnvironmentComboBox comboBox = SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox();
        if (comboBox != null) {
            List<EnvironmentItem> items = new ArrayList<>();
            for (int i = 0; i < environmentListModel.size(); i++) {
                items.add(environmentListModel.get(i));
            }
            comboBox.setModel(new DefaultComboBoxModel<>(items.toArray(new EnvironmentItem[0])));
        }
    }

    /**
     * 切换到指定工作区的环境数据文件，并刷新UI
     */
    public void switchWorkspaceAndRefreshUI(Path envFilePath) {
        EnvironmentService.setDataFilePath(envFilePath);
        this.refreshUI();
        // 同步刷新顶部环境下拉框
        SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox().reload();
    }

    /**
     * 转移环境到其他工作区
     */
    private void moveEnvironmentToWorkspace() {
        EnvironmentItem selectedItem = environmentList.getSelectedValue();
        if (selectedItem == null) {
            return;
        }

        Environment environment = selectedItem.getEnvironment();
        String environmentName = environment.getName();

        try {
            // 获取所有工作区
            WorkspaceService workspaceService = WorkspaceService.getInstance();
            List<Workspace> allWorkspaces = workspaceService.getAllWorkspaces();
            Workspace currentWorkspace = workspaceService.getCurrentWorkspace();

            // 过滤掉当前工作区
            List<Workspace> availableWorkspaces = allWorkspaces.stream()
                    .filter(w -> currentWorkspace == null || !w.getId().equals(currentWorkspace.getId()))
                    .toList();

            if (availableWorkspaces.isEmpty()) {
                JOptionPane.showMessageDialog(SingletonFactory.getInstance(MainFrame.class),
                        "没有其他可用的工作区",
                        I18nUtil.getMessage(MessageKeys.GENERAL_TIP),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 创建工作区选择对话框
            Workspace selectedWorkspace = showWorkspaceSelectionDialog(availableWorkspaces);
            if (selectedWorkspace == null) {
                return; // 用户取消选择
            }

            // 确认转移操作
            int confirm = JOptionPane.showConfirmDialog(SingletonFactory.getInstance(MainFrame.class),
                    I18nUtil.getMessage(MessageKeys.ENV_MENU_MOVE_TO_WORKSPACE_CONFIRM,
                            environmentName, selectedWorkspace.getName()),
                    I18nUtil.getMessage(MessageKeys.ENV_MENU_MOVE_TO_WORKSPACE_CONFIRM_TITLE),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            // 执行转移操作
            performEnvironmentMove(environment, selectedWorkspace);

            // 显示成功消息
            JOptionPane.showMessageDialog(SingletonFactory.getInstance(MainFrame.class),
                    I18nUtil.getMessage(MessageKeys.ENV_MENU_MOVE_TO_WORKSPACE_SUCCESS, selectedWorkspace.getName()),
                    I18nUtil.getMessage(MessageKeys.SUCCESS),
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            log.error("Move environment to workspace failed", ex);
            JOptionPane.showMessageDialog(SingletonFactory.getInstance(MainFrame.class),
                    I18nUtil.getMessage(MessageKeys.ENV_MENU_MOVE_TO_WORKSPACE_FAIL, ex.getMessage()),
                    I18nUtil.getMessage(MessageKeys.GENERAL_ERROR),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 显示工作区选择对话框
     */
    private Workspace showWorkspaceSelectionDialog(List<Workspace> workspaces) {
        JDialog dialog = new JDialog(SingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.ENV_MENU_MOVE_TO_WORKSPACE_SELECT), true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(SingletonFactory.getInstance(MainFrame.class));
        dialog.setLayout(new BorderLayout());

        // 创建工作区列表
        DefaultListModel<Workspace> listModel = new DefaultListModel<>();
        for (Workspace workspace : workspaces) {
            listModel.addElement(workspace);
        }

        JList<Workspace> workspaceList = new JList<>(listModel);
        workspaceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        workspaceList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Workspace workspace) {
                    setText(workspace.getName());
                    setIcon(new FlatSVGIcon("icons/workspace.svg", 16, 16));
                    setToolTipText(workspace.getDescription());
                }
                return this;
            }
        });

        JScrollPane scrollPane = new JScrollPane(workspaceList);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton(I18nUtil.getMessage(MessageKeys.GENERAL_OK));
        JButton cancelButton = new JButton(I18nUtil.getMessage(MessageKeys.GENERAL_CANCEL));

        final Workspace[] selectedWorkspace = {null};

        okButton.addActionListener(e -> {
            Workspace selected = workspaceList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog,
                        "请选择一个工作区",
                        I18nUtil.getMessage(MessageKeys.GENERAL_TIP),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            selectedWorkspace[0] = selected;
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return selectedWorkspace[0];
    }

    /**
     * 执行环境转移操作
     */
    private void performEnvironmentMove(Environment environment, Workspace targetWorkspace) {
        // 1. 深拷贝环境对象
        Environment copiedEnvironment = new Environment(environment.getName());
        copiedEnvironment.setId(environment.getId()); // 保持相同的ID
        // 复制所有变量
        for (String key : environment.getVariables().keySet()) {
            copiedEnvironment.addVariable(key, environment.getVariable(key));
        }

        // 2. 获取目标工作区的环境文件路径
        Path targetEnvPath = SystemUtil.getEnvPathForWorkspace(targetWorkspace);

        // 3. 临时切换到目标工作区的环境服务
        Path originalDataFilePath = EnvironmentService.getDataFilePath();
        try {
            // 切换到目标工作区
            EnvironmentService.setDataFilePath(targetEnvPath);

            // 4. 将环境保存到目标工作区
            EnvironmentService.saveEnvironment(copiedEnvironment);

            // 5. 切换回原工作区并删除原环境
            EnvironmentService.setDataFilePath(originalDataFilePath);
            EnvironmentService.deleteEnvironment(environment.getId());

            // 6. 刷新当前面板
            refreshUI();

            // 7. 刷新顶部环境下拉框
            SingletonFactory.getInstance(TopMenuBarPanel.class).getEnvironmentComboBox().reload();

            log.info("Successfully moved environment '{}' to workspace '{}'",
                    environment.getName(), targetWorkspace.getName());

        } catch (Exception e) {
            // 如果出现异常，确保恢复原来的数据文件路径
            EnvironmentService.setDataFilePath(originalDataFilePath);
            throw new RuntimeException("转移环境失败: " + e.getMessage(), e);
        }
    }
}
