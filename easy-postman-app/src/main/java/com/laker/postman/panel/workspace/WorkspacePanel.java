package com.laker.postman.panel.workspace;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.component.SearchTextField;
import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSidebarToolbar;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.PlusButton;
import com.laker.postman.common.component.dialog.TextInputDialog;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.model.GitAuthType;
import com.laker.postman.model.GitOperation;
import com.laker.postman.model.GitRepoSource;
import com.laker.postman.model.RemoteStatus;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.panel.env.EnvironmentPanel;
import com.laker.postman.panel.functional.FunctionalPanel;
import com.laker.postman.panel.mock.MockServerPanel;
import com.laker.postman.panel.performance.PerformancePanel;
import com.laker.postman.panel.topmenu.TopMenuBar;
import com.laker.postman.panel.workspace.components.*;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.*;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 工作区面板
 * 显示工作区列表，支持创建、切换、管理工作区
 */
@Slf4j
public class WorkspacePanel extends UiSingletonPanel {

    private static final String HTML_START = "<html>";
    private static final String HTML_END = "</html>";
    private static final String WORKSPACE_DETAIL_DIVIDER_APPLIED_PROPERTY =
            "EasyPostman.workspace.detailDividerApplied";
    private static final int WORKSPACE_DETAIL_DEFAULT_HEIGHT = 360;
    private static final int WORKSPACE_DETAIL_MIN_HEIGHT = 320;
    private static final int WORKSPACE_DETAIL_MAX_HEIGHT = 400;
    private static final int WORKSPACE_TOOL_MIN_HEIGHT = 220;
    private static final double WORKSPACE_DETAIL_RESIZE_WEIGHT = 0.34;

    private JList<Workspace> workspaceList;
    private DefaultListModel<Workspace> listModel;
    private SearchTextField workspaceSearchField;
    private List<Workspace> allWorkspaces = new ArrayList<>();
    private JPanel infoPanel;
    private JSplitPane workspaceContentSplitPane;
    private JPanel workspaceToolPanel;
    private String displayedWorkspaceId;
    private transient WorkspaceService workspaceService;

    @Override
    protected void initUI() {
        workspaceService = WorkspaceService.getInstance();
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyBackground(this);

        JPanel leftPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(leftPanel);
        leftPanel.add(createToolbar(), BorderLayout.NORTH);
        leftPanel.add(createWorkspaceListPanel(), BorderLayout.CENTER);

        workspaceContentSplitPane = AppToolWindowChrome.createVerticalInnerSplitPane(
                createInfoPanel(),
                createWorkspaceToolPanel(),
                WORKSPACE_DETAIL_DEFAULT_HEIGHT
        );
        workspaceContentSplitPane.setResizeWeight(WORKSPACE_DETAIL_RESIZE_WEIGHT);
        installInitialWorkspaceDetailDivider(workspaceContentSplitPane);

        JSplitPane mainSplitPane = AppToolWindowChrome.createHorizontalCardSplitPane(
                leftPanel,
                workspaceContentSplitPane,
                AppToolWindowChrome.DEFAULT_SIDE_WIDTH
        );
        mainSplitPane.setResizeWeight(0.0);

        add(mainSplitPane, BorderLayout.CENTER);

        // 刷新工作区列表
        refreshWorkspaceList();
    }

    /**
     * 创建工具栏
     */
    private JPanel createToolbar() {
        // 新建工作区按钮
        JButton newButton = new PlusButton();
        newButton.addActionListener(e -> showCreateWorkspaceDialog());

        workspaceSearchField = new SearchTextField();
        workspaceSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyWorkspaceFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyWorkspaceFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyWorkspaceFilter();
            }
        });
        return new ToolWindowSidebarToolbar(newButton, workspaceSearchField);
    }

    /**
     * 创建工作区列表面板
     */
    private JScrollPane createWorkspaceListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(panel);
        // 创建列表模型和列表
        listModel = new DefaultListModel<>();
        workspaceList = new JList<>(listModel);

        // 设置列表样式
        workspaceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        workspaceList.setCellRenderer(new WorkspaceListCellRenderer());
        // 动态计算单元格高度，基于字体大小
        workspaceList.setFixedCellHeight(WorkspaceListCellRenderer.calculateCellHeight());

        // 添加右键菜单和双击事件
        workspaceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = workspaceList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    workspaceList.setSelectedIndex(index);
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showContextMenu(e);
                    } else if (e.getClickCount() == 2) {
                        handleDoubleClick();
                    }
                }
            }
        });

        // 添加选择监听器
        workspaceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateInfoPanel();
            }
        });

        workspaceList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleListKeyPressed(e);
            }
        });

        // 拖拽排序支持
        setupDragAndDrop();

        JScrollPane scrollPane = new JScrollPane(workspaceList);
        ToolWindowSurfaceStyle.applyListScrollPaneCard(scrollPane, workspaceList);
        panel.add(scrollPane, BorderLayout.CENTER);

        return scrollPane;
    }

    /**
     * 创建信息面板
     */
    private JPanel createInfoPanel() {
        infoPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(infoPanel);
        infoPanel.setPreferredSize(new Dimension(400, 0));

        JLabel welcomeLabel = new JLabel("<html><center>" +
                I18nUtil.getMessage(MessageKeys.FUNCTIONAL_DETAIL_WELCOME_MESSAGE) +
                "</center></html>");
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        welcomeLabel.setFont(FontsUtil.getDefaultFont(Font.ITALIC));
        welcomeLabel.setForeground(ModernColors.getTextHint());

        infoPanel.add(welcomeLabel, BorderLayout.CENTER);

        return infoPanel;
    }

    /**
     * 创建工作区内嵌工具区域
     */
    private JPanel createWorkspaceToolPanel() {
        workspaceToolPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(workspaceToolPanel);
        workspaceToolPanel.setMinimumSize(new Dimension(0, WORKSPACE_TOOL_MIN_HEIGHT));
        workspaceToolPanel.setPreferredSize(new Dimension(0, WORKSPACE_TOOL_MIN_HEIGHT));
        return workspaceToolPanel;
    }

    private void showDefaultWorkspaceTool(Workspace workspace) {
        if (workspace != null && workspace.getType() == WorkspaceType.GIT) {
            showGitDiff(workspace, false);
            return;
        }
        hideWorkspaceTool();
    }

    private void showWorkspaceTool(JComponent component) {
        if (workspaceToolPanel == null || component == null) {
            return;
        }
        setWorkspaceToolVisible(true);
        workspaceToolPanel.removeAll();
        workspaceToolPanel.add(component, BorderLayout.CENTER);
        workspaceToolPanel.revalidate();
        workspaceToolPanel.repaint();
    }

    private void hideWorkspaceTool() {
        if (workspaceToolPanel == null) {
            return;
        }
        workspaceToolPanel.removeAll();
        setWorkspaceToolVisible(false);
        workspaceToolPanel.revalidate();
        workspaceToolPanel.repaint();
    }

    private void setWorkspaceToolVisible(boolean visible) {
        if (workspaceContentSplitPane == null || workspaceToolPanel == null) {
            return;
        }
        if (workspaceToolPanel.isVisible() == visible
                && workspaceContentSplitPane.getDividerSize() == (visible ? AppToolWindowChrome.DIVIDER_SIZE : 0)) {
            return;
        }
        workspaceToolPanel.setVisible(visible);
        workspaceContentSplitPane.setDividerSize(visible ? AppToolWindowChrome.DIVIDER_SIZE : 0);
        workspaceContentSplitPane.setResizeWeight(visible ? WORKSPACE_DETAIL_RESIZE_WEIGHT : 1.0);
        SwingUtilities.invokeLater(() -> {
            if (visible) {
                workspaceContentSplitPane.setDividerLocation(defaultWorkspaceDetailDividerLocation(
                        workspaceContentSplitPane.getHeight(),
                        workspaceContentSplitPane.getDividerSize()
                ));
            } else {
                workspaceContentSplitPane.setDividerLocation(1.0);
            }
        });
        workspaceContentSplitPane.revalidate();
        workspaceContentSplitPane.repaint();
    }

    private static void installInitialWorkspaceDetailDivider(JSplitPane splitPane) {
        splitPane.putClientProperty(WORKSPACE_DETAIL_DIVIDER_APPLIED_PROPERTY, Boolean.FALSE);
        ComponentAdapter listener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (applyInitialWorkspaceDetailDivider(splitPane)) {
                    splitPane.removeComponentListener(this);
                }
            }
        };
        splitPane.addComponentListener(listener);
        SwingUtilities.invokeLater(() -> {
            if (applyInitialWorkspaceDetailDivider(splitPane)) {
                splitPane.removeComponentListener(listener);
            }
        });
    }

    private static boolean applyInitialWorkspaceDetailDivider(JSplitPane splitPane) {
        if (Boolean.TRUE.equals(splitPane.getClientProperty(WORKSPACE_DETAIL_DIVIDER_APPLIED_PROPERTY))) {
            return true;
        }
        int splitHeight = splitPane.getHeight();
        if (splitHeight <= 0) {
            return false;
        }
        splitPane.setDividerLocation(defaultWorkspaceDetailDividerLocation(splitHeight, splitPane.getDividerSize()));
        splitPane.putClientProperty(WORKSPACE_DETAIL_DIVIDER_APPLIED_PROPERTY, Boolean.TRUE);
        return true;
    }

    static int defaultWorkspaceDetailDividerLocation(int splitHeight, int dividerSize) {
        int usableHeight = Math.max(0, splitHeight - Math.max(0, dividerSize));
        if (usableHeight <= 0) {
            return WORKSPACE_DETAIL_DEFAULT_HEIGHT;
        }
        int ratioLocation = (int) Math.round(usableHeight * WORKSPACE_DETAIL_RESIZE_WEIGHT);
        int desiredLocation = Math.max(WORKSPACE_DETAIL_DEFAULT_HEIGHT,
                Math.max(WORKSPACE_DETAIL_MIN_HEIGHT, ratioLocation));
        desiredLocation = Math.min(desiredLocation, WORKSPACE_DETAIL_MAX_HEIGHT);
        int maxLocation = usableHeight - WORKSPACE_TOOL_MIN_HEIGHT;
        if (maxLocation < WORKSPACE_DETAIL_MIN_HEIGHT) {
            return Math.max(0, maxLocation);
        }
        return Math.min(desiredLocation, maxLocation);
    }

    /**
     * 设置拖拽排序功能
     */
    private void setupDragAndDrop() {
        workspaceList.setDragEnabled(true);
        workspaceList.setDropMode(DropMode.INSERT);
        workspaceList.setTransferHandler(new TransferHandler() {
            private int fromIndex = -1;

            @Override
            protected Transferable createTransferable(JComponent c) {
                fromIndex = workspaceList.getSelectedIndex();
                Workspace selected = workspaceList.getSelectedValue();
                return new StringSelection(selected != null ? selected.getName() : "");
            }

            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop() && !isWorkspaceSearchActive();
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
                int toIndex = dl.getIndex();
                if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false;

                Workspace moved = listModel.getElementAt(fromIndex);
                listModel.remove(fromIndex);
                if (toIndex > fromIndex) toIndex--;
                listModel.add(toIndex, moved);
                workspaceList.setSelectedIndex(toIndex);

                // 持久化顺序
                persistWorkspaceOrder();
                return true;
            }
        });
    }

    /**
     * 持久化工作区顺序
     */
    private void persistWorkspaceOrder() {
        List<String> idOrder = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            idOrder.add(listModel.get(i).getId());
        }
        workspaceService.saveWorkspaceOrder(idOrder);
    }

    private boolean isWorkspaceSearchActive() {
        return workspaceSearchField != null && !workspaceSearchField.getText().trim().isEmpty();
    }

    /**
     * 显示创建工作区对话框
     */
    private void showCreateWorkspaceDialog() {
        WorkspaceCreateDialog dialog = new WorkspaceCreateDialog(
                SwingUtilities.getWindowAncestor(this)
        );
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            refreshWorkspaceList();
            // 更新顶部菜单栏的工作区下拉框（不需要重新加载整个菜单栏）
            UiSingletonFactory.getInstance(TopMenuBar.class).updateWorkspaceComboBox();
        }
    }

    /**
     * 显示右键菜单
     */
    private void showContextMenu(MouseEvent e) {
        Workspace workspace = workspaceList.getSelectedValue();
        if (workspace != null) {
            JPopupMenu menu = createWorkspaceContextMenu(workspace);
            menu.show(workspaceList, e.getX(), e.getY());
        }
    }

    /**
     * 创建工作区右键菜单
     */
    private JPopupMenu createWorkspaceContextMenu(Workspace workspace) {
        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);

        addSwitchMenuItem(menu, workspace);
        addGitMenuItems(menu, workspace);
        addManagementMenuItems(menu, workspace);

        return menu;
    }

    private void addSwitchMenuItem(JPopupMenu menu, Workspace workspace) {
        Workspace current = workspaceService.getCurrentWorkspace();
        if (current == null || !current.getId().equals(workspace.getId())) {
            JMenuItem switchItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_SWITCH));
            switchItem.setIcon(IconUtil.createThemed("icons/switch.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
            switchItem.addActionListener(e -> switchToWorkspace(workspace));
            menu.add(switchItem);
            if (!WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
                menu.addSeparator();
            }
        }
    }

    private void addGitMenuItems(JPopupMenu menu, Workspace workspace) {
        if (workspace.getType() != WorkspaceType.GIT) {
            // 所有本地工作区都可以转换为Git工作区（包括默认工作区）
            // 默认工作区位于 workspaces/default/ 子目录，与其他工作区平级，根目录不参与 git
            JMenuItem convertToGitItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_CONVERT_TO_GIT));
            convertToGitItem.setIcon(IconUtil.create("icons/git.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
            convertToGitItem.addActionListener(e -> convertToGitWorkspace(workspace));
            menu.add(convertToGitItem);
            // 只有非默认工作区才添加分隔符（因为后面还有重命名和删除选项）
            if (!WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
                menu.addSeparator();
            }
            return;
        }

        boolean gitItemsAdded = addStandardGitMenuItems(menu, workspace);
        // 只有非默认工作区才添加分隔符（因为后面还有重命名和删除选项）
        if (gitItemsAdded && !WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            menu.addSeparator();
        }
    }

    private boolean addStandardGitMenuItems(JPopupMenu menu, Workspace workspace) {
        try {
            RemoteStatus remoteStatus = workspaceService.getRemoteStatus(workspace.getId());
            if (remoteStatus.hasRemote) {
                JMenuItem updateAuthItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_GIT_AUTH_UPDATE));
                updateAuthItem.setIcon(IconUtil.createThemed("icons/security.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
                updateAuthItem.addActionListener(e -> updateGitAuthentication(workspace));
                menu.add(updateAuthItem);
                return true;
            }
        } catch (Exception ex) {
            log.warn("Failed to check remote repository for workspace: {}", workspace.getId(), ex);
        }
        return false;
    }

    private void addManagementMenuItems(JPopupMenu menu, Workspace workspace) {
        // 默认工作区不可重命名和删除
        if (!WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            // 重命名
            JMenuItem renameItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_RENAME));
            renameItem.setIcon(IconUtil.createThemed("icons/refresh.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
            renameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
            renameItem.addActionListener(e -> renameWorkspace(workspace));
            menu.add(renameItem);

            // 删除
            JMenuItem deleteItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_DELETE));
            deleteItem.setIcon(IconUtil.createThemed("icons/close.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
            deleteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
            deleteItem.addActionListener(e -> deleteWorkspace(workspace));
            menu.add(deleteItem);
        }
    }

    private void handleListKeyPressed(KeyEvent e) {
        Workspace workspace = workspaceList.getSelectedValue();
        if (workspace == null || WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_F2) {
            renameWorkspace(workspace);
            e.consume();
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            deleteWorkspace(workspace);
            e.consume();
        }
    }

    /**
     * 处理双击事件
     */
    private void handleDoubleClick() {
        Workspace workspace = workspaceList.getSelectedValue();
        if (workspace != null) {
            switchToWorkspace(workspace);
        }
    }

    /**
     * 切换到指定工作区
     */
    private void switchToWorkspace(Workspace workspace) {
        try {
            saveCurrentWorkspaceScopedPanels();
            workspaceService.switchWorkspace(workspace.getId());
            // 切换环境变量文件
            UiSingletonFactory.getInstance(EnvironmentPanel.class).switchWorkspaceAndRefreshUI(SystemUtil.getEnvPathForWorkspace(workspace));
            // 切换请求集合文件
            UiSingletonFactory.getInstance(CollectionTreePanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getCollectionPathForWorkspace(workspace), () -> {
                        refreshExistingWorkspaceScopedPanels();
                        // 更新顶部菜单栏工作区显示
                        UiSingletonFactory.getInstance(TopMenuBar.class).updateWorkspaceDisplay();
                        refreshWorkspaceList();
                    });
        } catch (Exception e) {
            log.error("Failed to switch workspace", e);
        }
    }

    private void saveCurrentWorkspaceScopedPanels() {
        UiSingletonFactory.getExistingInstance(FunctionalPanel.class).ifPresent(FunctionalPanel::save);
        UiSingletonFactory.getExistingInstance(PerformancePanel.class).ifPresent(PerformancePanel::save);
    }

    private void refreshExistingWorkspaceScopedPanels() {
        UiSingletonFactory.getExistingInstance(FunctionalPanel.class)
                .ifPresent(FunctionalPanel::switchWorkspaceAndRefreshUI);
        UiSingletonFactory.getExistingInstance(PerformancePanel.class)
                .ifPresent(PerformancePanel::switchWorkspaceAndRefreshUI);
        UiSingletonFactory.getExistingInstance(MockServerPanel.class)
                .ifPresent(MockServerPanel::switchWorkspaceAndRefreshUI);
    }

    /**
     * Git拉取操作
     */
    private void performGitPull(Workspace workspace) {
        saveCurrentWorkspaceScopedPanels();
        GitOperationDialog dialog = new GitOperationDialog(
                SwingUtilities.getWindowAncestor(this),
                workspace,
                GitOperation.PULL
        );
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            // 刷新 requests 和 env 面板
            UiSingletonFactory.getInstance(CollectionTreePanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getCollectionPathForWorkspace(workspace), () -> {
                        refreshExistingWorkspaceScopedPanels();
                        refreshWorkspaceList();
                    });
            UiSingletonFactory.getInstance(EnvironmentPanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getEnvPathForWorkspace(workspace));
        }
    }

    /**
     * Git提交操作
     */
    private void performGitCommit(Workspace workspace) {
        saveCurrentWorkspaceScopedPanels();
        GitOperationDialog dialog = new GitOperationDialog(
                SwingUtilities.getWindowAncestor(this),
                workspace,
                GitOperation.COMMIT
        );
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            refreshWorkspaceList();
        }
    }

    /**
     * Git推送操作
     */
    private void performGitPush(Workspace workspace) {
        saveCurrentWorkspaceScopedPanels();
        GitOperationDialog dialog = new GitOperationDialog(
                SwingUtilities.getWindowAncestor(this),
                workspace,
                GitOperation.PUSH
        );
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            // 刷新 requests 和 env 面板
            UiSingletonFactory.getInstance(CollectionTreePanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getCollectionPathForWorkspace(workspace), () -> {
                        refreshExistingWorkspaceScopedPanels();
                        refreshWorkspaceList();
                    });
            UiSingletonFactory.getInstance(EnvironmentPanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getEnvPathForWorkspace(workspace));
        }
    }

    /**
     * 显示 Git 历史记录
     */
    private void showGitHistory(Workspace workspace) {
        showWorkspaceTool(new GitHistoryPanel(workspace, () -> refreshAfterGitHistoryRestore(workspace)));
    }

    private void refreshAfterGitHistoryRestore(Workspace workspace) {
        UiSingletonFactory.getInstance(CollectionTreePanel.class)
                .switchWorkspaceAndRefreshUI(SystemUtil.getCollectionPathForWorkspace(workspace), () -> {
                    refreshExistingWorkspaceScopedPanels();
                    refreshWorkspaceList();
                });
        UiSingletonFactory.getInstance(EnvironmentPanel.class)
                .switchWorkspaceAndRefreshUI(SystemUtil.getEnvPathForWorkspace(workspace));
    }

    private void showGitBranches(Workspace workspace) {
        Workspace current = workspaceService.getCurrentWorkspace();
        boolean isCurrentWorkspace = current != null && current.getId().equals(workspace.getId());
        if (isCurrentWorkspace) {
            saveCurrentWorkspaceScopedPanels();
        }

        showWorkspaceTool(new GitBranchPanel(
                workspace,
                () -> refreshAfterGitBranchChange(workspace, isCurrentWorkspace)
        ));
    }

    private void showGitDiff(Workspace workspace) {
        showGitDiff(workspace, true);
    }

    private void showGitDiff(Workspace workspace, boolean saveBeforeShow) {
        Workspace current = workspaceService.getCurrentWorkspace();
        boolean isCurrentWorkspace = current != null && current.getId().equals(workspace.getId());
        if (saveBeforeShow && isCurrentWorkspace) {
            saveCurrentWorkspaceScopedPanels();
        }

        showWorkspaceTool(new GitDiffPanel(workspace));
    }

    private void refreshAfterGitBranchChange(Workspace workspace, boolean isCurrentWorkspace) {
        if (isCurrentWorkspace) {
            UiSingletonFactory.getInstance(CollectionTreePanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getCollectionPathForWorkspace(workspace), () -> {
                        refreshExistingWorkspaceScopedPanels();
                        refreshWorkspaceList();
                    });
            UiSingletonFactory.getInstance(EnvironmentPanel.class)
                    .switchWorkspaceAndRefreshUI(SystemUtil.getEnvPathForWorkspace(workspace));
        } else {
            refreshWorkspaceList();
        }
    }

    /**
     * 重命名工作区
     */
    private void renameWorkspace(Workspace workspace) {
        TextInputDialog.showRequiredName(
                this,
                I18nUtil.getMessage(MessageKeys.WORKSPACE_RENAME),
                workspace.getName(),
                I18nUtil.getMessage(MessageKeys.WORKSPACE_VALIDATION_NAME_REQUIRED)
        ).ifPresent(newName -> {
            if (newName.equals(workspace.getName())) {
                return;
            }
            try {
                workspaceService.renameWorkspace(workspace.getId(), newName);
                refreshWorkspaceList();
                // 如果重命名的是当前工作区，更新顶部菜单栏的工作区下拉框
                Workspace current = workspaceService.getCurrentWorkspace();
                if (current != null && current.getId().equals(workspace.getId())) {
                    // 只更新下拉框，不需要重新加载整个菜单栏（工作区类型未变）
                    UiSingletonFactory.getInstance(TopMenuBar.class).updateWorkspaceComboBox();
                }
            } catch (Exception e) {
                log.error("Failed to rename workspace", e);
            }
        });
    }

    /**
     * 删除工作区
     */
    private void deleteWorkspace(Workspace workspace) {
        String[] options = {
                I18nUtil.getMessage(MessageKeys.WORKSPACE_DELETE),
                I18nUtil.getMessage(MessageKeys.BUTTON_CANCEL)
        };

        int choice = JOptionPane.showOptionDialog(
                this,
                I18nUtil.getMessage(MessageKeys.WORKSPACE_DELETE_CONFIRM, workspace.getName()),
                I18nUtil.getMessage(MessageKeys.WORKSPACE_DELETE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0] // default option
        );

        if (choice == 0) { // 删除
            try {
                // 检查是否删除的是当前工作区
                boolean isDeletingCurrentWorkspace = workspaceService.getCurrentWorkspace() != null &&
                        workspaceService.getCurrentWorkspace().getId().equals(workspace.getId());

                workspaceService.deleteWorkspace(workspace.getId());

                // 如果删除的是当前工作区，需要切换到新的当前工作区并刷新相关UI
                if (isDeletingCurrentWorkspace) {
                    Workspace newCurrentWorkspace = workspaceService.getCurrentWorkspace();
                    if (newCurrentWorkspace != null) {
                        // 切换环境变量文件
                        UiSingletonFactory.getInstance(EnvironmentPanel.class).switchWorkspaceAndRefreshUI(
                                SystemUtil.getEnvPathForWorkspace(newCurrentWorkspace));
                        // 切换请求集合文件
                        UiSingletonFactory.getInstance(CollectionTreePanel.class).switchWorkspaceAndRefreshUI(
                                SystemUtil.getCollectionPathForWorkspace(newCurrentWorkspace), this::refreshExistingWorkspaceScopedPanels);

                    }
                }

                refreshWorkspaceList();
                UiSingletonFactory.getInstance(TopMenuBar.class).updateWorkspaceDisplay();
            } catch (Exception e) {
                log.error("Failed to delete workspace", e);
            }
        }
    }

    /**
     * 转换本地工作区为Git工作区
     */
    private void convertToGitWorkspace(Workspace workspace) {
        // 使用自定义对话框，合并分支名输入和确认步骤
        ConvertToGitDialog dialog = new ConvertToGitDialog(
                SwingUtilities.getWindowAncestor(this),
                workspace
        );
        dialog.setVisible(true);

        if (!dialog.isConfirmed()) {
            return;
        }

        // 执行转换
        try {
            workspaceService.convertLocalToGit(workspace, dialog.getBranchName());
            refreshWorkspaceList();

            // 如果转换的是当前工作区，需要更新顶部菜单栏
            Workspace current = workspaceService.getCurrentWorkspace();
            if (current != null && current.getId().equals(workspace.getId())) {
                UiSingletonFactory.getInstance(TopMenuBar.class).updateWorkspaceDisplay();
            }

            log.info("Successfully converted workspace '{}' to Git workspace (branch: {})",
                    workspace.getName(), dialog.getBranchName());
        } catch (Exception e) {
            log.error("Failed to convert workspace to Git", e);
            JOptionPane.showMessageDialog(
                    this,
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_CONVERT_FAILED) + ": " + e.getMessage(),
                    I18nUtil.getMessage(MessageKeys.GENERAL_ERROR),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * 配置远程仓库
     */
    private void configureRemoteRepository(Workspace workspace) {
        showWorkspaceTool(new RemoteConfigPanel(workspace, () -> refreshAfterRemoteConfigured(workspace)));
    }

    private void refreshAfterRemoteConfigured(Workspace workspace) {
        refreshWorkspaceList();
        UiSingletonFactory.getInstance(TopMenuBar.class).updateWorkspaceDisplay();
    }

    /**
     * 更新 Git 认证信息
     */
    private void updateGitAuthentication(Workspace workspace) {
        UpdateAuthDialog dialog = new UpdateAuthDialog(
                SwingUtilities.getWindowAncestor(this), workspace);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            refreshWorkspaceList();
            String logMessage = "Git authentication updated successfully for workspace: " + workspace.getName();

            // 如果更新了SSH密钥，添加额外提示
            if (dialog.getAuthType() == GitAuthType.SSH_KEY) {
                logMessage += " (SSH session cache cleared)";
            }

            log.info(logMessage);
        }
    }

    /**
     * 刷新工作区列表
     */
    private void refreshWorkspaceList() {
        try {
            allWorkspaces = new ArrayList<>(workspaceService.getAllWorkspaces());
            applyWorkspaceFilter();
        } catch (Exception e) {
            log.error("Failed to refresh workspace list", e);
        }
    }

    private void applyWorkspaceFilter() {
        if (listModel == null) {
            return;
        }

        String query = workspaceSearchField == null ? "" : workspaceSearchField.getText().trim();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        listModel.clear();
        for (Workspace workspace : allWorkspaces) {
            if (normalizedQuery.isEmpty() || matchesWorkspaceSearch(workspace, normalizedQuery)) {
                listModel.addElement(workspace);
            }
        }

        if (workspaceSearchField != null) {
            workspaceSearchField.setNoResult(!normalizedQuery.isEmpty() && listModel.isEmpty());
        }
        if (workspaceList != null) {
            workspaceList.setDragEnabled(normalizedQuery.isEmpty());
        }
        selectCurrentWorkspaceInVisibleList();
        updateInfoPanel();
    }

    private boolean matchesWorkspaceSearch(Workspace workspace, String normalizedQuery) {
        return containsIgnoreCase(workspace.getName(), normalizedQuery)
                || containsIgnoreCase(workspace.getDescription(), normalizedQuery)
                || containsIgnoreCase(workspace.getPath(), normalizedQuery)
                || containsIgnoreCase(workspace.getGitRemoteUrl(), normalizedQuery)
                || containsIgnoreCase(workspace.getCurrentBranch(), normalizedQuery)
                || (workspace.getType() != null
                && workspace.getType().name().toLowerCase(Locale.ROOT).contains(normalizedQuery));
    }

    private static boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private void selectCurrentWorkspaceInVisibleList() {
        if (workspaceList == null) {
            return;
        }
        workspaceList.clearSelection();
        Workspace current = workspaceService.getCurrentWorkspace();
        if (current == null) {
            return;
        }
        for (int i = 0; i < listModel.getSize(); i++) {
            if (listModel.getElementAt(i).getId().equals(current.getId())) {
                workspaceList.setSelectedIndex(i);
                break;
            }
        }
    }

    /**
     * 更新信息面板
     */
    private void updateInfoPanel() {
        Workspace selected = workspaceList.getSelectedValue();
        infoPanel.removeAll();
        String selectedWorkspaceId = selected == null ? null : selected.getId();
        boolean workspaceChanged = !Objects.equals(displayedWorkspaceId, selectedWorkspaceId);
        displayedWorkspaceId = selectedWorkspaceId;
        if (workspaceChanged) {
            showDefaultWorkspaceTool(selected);
        }

        if (selected != null) {
            infoPanel.add(new WorkspaceDetailPanel(
                    selected,
                    createGitActions(selected)
            ), BorderLayout.CENTER);
        } else {
            JLabel welcomeLabel = new JLabel(HTML_START + "<center>" +
                    I18nUtil.getMessage(MessageKeys.FUNCTIONAL_DETAIL_WELCOME_MESSAGE) +
                    "</center>" + HTML_END);
            welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);
            welcomeLabel.setFont(FontsUtil.getDefaultFont(Font.ITALIC));
            welcomeLabel.setForeground(ModernColors.getTextSecondary());
            infoPanel.add(welcomeLabel, BorderLayout.CENTER);
        }

        infoPanel.revalidate();
        infoPanel.repaint();
    }

    private WorkspaceDetailPanel.GitActions createGitActions(Workspace workspace) {
        if (workspace.getType() != WorkspaceType.GIT) {
            return null;
        }

        boolean hasRemote = false;
        boolean hasUpstream = false;
        boolean remoteStatusKnown = false;
        try {
            RemoteStatus remoteStatus = workspaceService.getRemoteStatus(workspace.getId());
            hasRemote = remoteStatus.hasRemote;
            hasUpstream = remoteStatus.hasUpstream;
            remoteStatusKnown = true;
        } catch (Exception ex) {
            log.warn("Failed to check remote status for workspace: {}", workspace.getId(), ex);
        }

        Runnable pullAction = hasRemote ? () -> performGitPull(workspace) : null;
        Runnable pushAction = hasRemote && hasUpstream ? () -> performGitPush(workspace) : null;
        Runnable remoteConfigAction = remoteStatusKnown
                && !hasRemote
                && workspace.getGitRepoSource() == GitRepoSource.INITIALIZED
                ? () -> configureRemoteRepository(workspace)
                : null;
        return new WorkspaceDetailPanel.GitActions(
                () -> performGitCommit(workspace),
                pullAction,
                pushAction,
                remoteConfigAction,
                () -> showGitHistory(workspace),
                () -> showGitBranches(workspace),
                () -> showGitDiff(workspace)
        );
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                I18nUtil.getMessage(MessageKeys.ERROR),
                JOptionPane.ERROR_MESSAGE
        );
    }

    @Override
    protected void registerListeners() {
        // 监听器已在initUI中注册
    }
}
