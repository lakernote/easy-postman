package com.laker.postman.common.component.dialog;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.component.setting.SettingsInputStyle;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.model.Workspace;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * 工作区选择对话框 - 可复用组件
 * 用于在多个场景中选择目标工作区（如转移环境、转移集合等）
 */
public class WorkspaceSelectionDialog extends JDialog {
    private final JList<Workspace> workspaceList;
    private final DefaultListModel<Workspace> listModel;
    private Workspace selectedWorkspace;
    private final JTextField searchField;

    public WorkspaceSelectionDialog(String title, List<Workspace> workspaces) {
        super(UiSingletonFactory.getInstance(MainFrame.class), title, true);
        setSize(500, 400);
        setLocationRelativeTo(UiSingletonFactory.getInstance(MainFrame.class));
        setLayout(new BorderLayout(10, 10));
        ToolWindowSurfaceStyle.applyDialogWindowChrome(this);
        ToolWindowSurfaceStyle.applyDialogSurface((JComponent) getContentPane());

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        ToolWindowSurfaceStyle.applyDialogSurface(mainPanel);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 顶部搜索框
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setOpaque(false);
        JLabel searchLabel = new JLabel(I18nUtil.getMessage(MessageKeys.GENERAL_SEARCH) + ":");
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(0, 30));
        SettingsInputStyle.apply(searchField);
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        mainPanel.add(searchPanel, BorderLayout.NORTH);

        // 创建工作区列表
        listModel = new DefaultListModel<>();
        for (Workspace workspace : workspaces) {
            listModel.addElement(workspace);
        }

        workspaceList = new JList<>(listModel);
        workspaceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        workspaceList.setFixedCellHeight(40);
        // 与工作区面板共用渲染器：类型与图标来自磁盘探测，不阻塞界面线程
        workspaceList.setCellRenderer(new com.laker.postman.panel.workspace.components.WorkspaceListCellRenderer());

        JScrollPane scrollPane = new JScrollPane(workspaceList);
        ToolWindowSurfaceStyle.applyDialogListScrollPane(scrollPane, workspaceList);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 说明文字
        JLabel hintLabel = new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_SELECT_HINT));
        hintLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.ITALIC, -2));
        hintLabel.setForeground(ModernColors.getTextSecondary());
        hintLabel.setBorder(new EmptyBorder(5, 0, 0, 0));
        mainPanel.add(hintLabel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        ToolWindowSurfaceStyle.applyDialogFooter(buttonPanel);

        JButton okButton = ModernButtonFactory.createButton(I18nUtil.getMessage(MessageKeys.GENERAL_OK), true);
        JButton cancelButton = ModernButtonFactory.createButton(I18nUtil.getMessage(MessageKeys.GENERAL_CANCEL), false);

        okButton.setPreferredSize(new Dimension(80, 30));
        cancelButton.setPreferredSize(new Dimension(80, 30));

        okButton.addActionListener(e -> onOkClicked());
        cancelButton.addActionListener(e -> onCancelClicked());

        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // 注册事件监听器
        registerListeners(workspaces);

        // 自动选中第一个
        if (!listModel.isEmpty()) {
            workspaceList.setSelectedIndex(0);
        }

        // 设置默认按钮
        getRootPane().setDefaultButton(okButton);
    }

    private void registerListeners(List<Workspace> allWorkspaces) {
        // 搜索框监听
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterWorkspaces();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterWorkspaces();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterWorkspaces();
            }

            private void filterWorkspaces() {
                String filter = searchField.getText().trim().toLowerCase();
                listModel.clear();
                for (Workspace workspace : allWorkspaces) {
                    if (filter.isEmpty() ||
                            workspace.getName().toLowerCase().contains(filter) ||
                            (workspace.getDescription() != null && workspace.getDescription().toLowerCase().contains(filter))) {
                        listModel.addElement(workspace);
                    }
                }
                // 自动选中第一个搜索结果
                if (!listModel.isEmpty()) {
                    workspaceList.setSelectedIndex(0);
                }
            }
        });

        // 双击确认
        workspaceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = workspaceList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        workspaceList.setSelectedIndex(index);
                        onOkClicked();
                    }
                }
            }
        });

        // Enter键确认
        workspaceList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    onOkClicked();
                }
            }
        });

        // ESC键取消
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCancelClicked();
            }
        });
    }

    private void onOkClicked() {
        Workspace selected = workspaceList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_SELECT_REQUIRED),
                    I18nUtil.getMessage(MessageKeys.GENERAL_TIP),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        selectedWorkspace = selected;
        dispose();
    }

    private void onCancelClicked() {
        selectedWorkspace = null;
        dispose();
    }

    /**
     * 显示对话框并返回选择的工作区
     *
     * @return 选择的工作区，如果取消则返回null
     */
    public Workspace showDialog() {
        setVisible(true);
        return selectedWorkspace;
    }
}
