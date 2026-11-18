package com.laker.postman.panel.workspace;

import com.laker.postman.common.UiSingletonFactory;
import com.formdev.flatlaf.util.SystemFileChooser;
import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.component.SearchTextField;
import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSidebarToolbar;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.PlusButton;
import com.laker.postman.common.component.dialog.TextInputDialog;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.model.GitOperation;
import com.laker.postman.model.RemoteStatus;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.plugin.git.GitWorkspaceLayout;
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
import java.nio.file.Path;
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
    private static final String DEFAULT_EMBEDDED_DIRECTORY = "easy-postman";
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
        // 详情区的自然最小宽度较大，会限制分隔条拖动范围；内容不足时由各自区域裁剪/滚动
        leftPanel.setMinimumSize(new Dimension(160, 0));
        workspaceContentSplitPane.setMinimumSize(new Dimension(320, 0));

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
        newButton.addActionListener(e -> showNewWorkspaceMenu(newButton));

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
        if (workspace != null && workspaceService.isGitEnabled(workspace)) {
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
     * 新建入口：新建 / 打开文件夹 / 从 Git 克隆。
     */
    private void showNewWorkspaceMenu(JComponent anchor) {
        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);
        JMenuItem newItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_ACTION_NEW));
        newItem.setIcon(IconUtil.createThemed("icons/local.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        newItem.addActionListener(e -> showCreateWorkspaceDialog(false));
        menu.add(newItem);
        JMenuItem openItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_ACTION_OPEN_FOLDER));
        openItem.setIcon(IconUtil.createThemed("icons/file.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        openItem.addActionListener(e -> openFolderAsWorkspace());
        menu.add(openItem);
        JMenuItem cloneItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_ACTION_CLONE));
        cloneItem.setIcon(IconUtil.create("icons/git-remote.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        cloneItem.addActionListener(e -> showCreateWorkspaceDialog(true));
        menu.add(cloneItem);
        menu.show(anchor, 0, anchor.getHeight());
    }

    /**
     * 显示创建工作区对话框
     */
    private void showCreateWorkspaceDialog(boolean cloneFromGit) {
        WorkspaceCreateDialog dialog = new WorkspaceCreateDialog(
                SwingUtilities.getWindowAncestor(this),
                cloneFromGit
        );
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            onWorkspaceAdded(dialog.getWorkspace());
        }
    }

    /**
     * 新建、克隆或打开文件夹后直接切换过去，加载其集合与环境。
     */
    private void onWorkspaceAdded(Workspace workspace) {
        refreshWorkspaceList();
        selectWorkspaceInList(workspace);
        // 更新顶部菜单栏的工作区下拉框（不需要重新加载整个菜单栏）
        UiSingletonFactory.getInstance(TopMenuBar.class).updateWorkspaceComboBox();
        openWorkspace(workspace);
    }

    private void openWorkspace(Workspace workspace) {
        if (workspace == null || workspace.getId() == null) {
            return;
        }
        Workspace current = workspaceService.getCurrentWorkspace();
        if (current != null && current.getId().equals(workspace.getId())) {
            return;
        }
        switchToWorkspace(workspace);
    }

    private void selectWorkspaceInList(Workspace workspace) {
        if (workspace == null || workspace.getId() == null) {
            return;
        }
        for (int i = 0; i < listModel.size(); i++) {
            if (workspace.getId().equals(listModel.get(i).getId())) {
                workspaceList.setSelectedIndex(i);
                workspaceList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    /**
     * 打开已有目录作为工作区：目录可以是独立目录，也可以位于任意代码仓库中。
     */
    private void openFolderAsWorkspace() {
        SystemFileChooser chooser = FileChooserUtil.createDirectoryChooser(
                "workspace.open.folder",
                I18nUtil.getMessage(MessageKeys.WORKSPACE_ACTION_OPEN_FOLDER));
        if (chooser.showOpenDialog(this) != SystemFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }
        Path directory = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        Workspace registered = workspaceService.findWorkspaceByPath(directory);
        if (registered != null) {
            openExistingRegistration(registered);
            return;
        }
        // 仓库探测与数据写入在后台执行，界面线程只负责确认对话框
        runInBackground(() -> WorkspaceDirectoryProbe.probe(directory), this::continueOpenFolder,
                ex -> showError(I18nUtil.getMessage(MessageKeys.WORKSPACE_OPEN_FOLDER_FAILED, ex.getMessage())));
    }

    private void continueOpenFolder(WorkspaceDirectoryProbe probe) {
        Path directory = probe.directory();
        GitWorkspaceLayout layout = probe.layout();
        boolean confirmedLocation = false;
        if (layout != null && !layout.isEmbedded() && !probe.hasData()) {
            confirmedLocation = true;
            Path subdirectory = directory.resolve(DEFAULT_EMBEDDED_DIRECTORY);
            String[] options = {
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_OPEN_FOLDER_USE_SUBDIRECTORY),
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_OPEN_FOLDER_USE_SELECTED),
                    CommonI18n.get(CommonMessageKeys.BUTTON_CANCEL)
            };
            int choice = JOptionPane.showOptionDialog(this,
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_OPEN_FOLDER_REPO_ROOT_PROMPT, directory.toString()),
                    I18nUtil.getMessage(MessageKeys.WORKSPACE_ACTION_OPEN_FOLDER),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                directory = subdirectory;
            } else if (choice != 1) {
                return;
            }
        }

        Workspace existing = workspaceService.findWorkspaceByPath(directory);
        if (existing != null) {
            openExistingRegistration(existing);
            return;
        }
        if (!confirmedLocation && !WorkspaceConfirmations.confirmCreateData(this, probe, true)) {
            return;
        }
        Path target = directory;
        runInBackground(() -> workspaceService.openFolderAsWorkspace(target, suggestWorkspaceName(target)),
                this::onWorkspaceAdded,
                ex -> {
                    log.error("Failed to open folder as workspace: {}", target, ex);
                    showError(I18nUtil.getMessage(MessageKeys.WORKSPACE_OPEN_FOLDER_FAILED, ex.getMessage()));
                });
    }

    private <T> void runInBackground(java.util.concurrent.Callable<T> task,
                                     java.util.function.Consumer<T> onSuccess,
                                     java.util.function.Consumer<Exception> onFailure) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                T result;
                try {
                    result = get();
                } catch (java.util.concurrent.ExecutionException e) {
                    onFailure.accept(e.getCause() instanceof Exception cause ? cause : e);
                    return;
                } catch (Exception e) {
                    onFailure.accept(e);
                    return;
                }
                onSuccess.accept(result);
            }
        }.execute();
    }

    private void openExistingRegistration(Workspace existing) {
        selectWorkspaceInList(existing);
        NotificationCenter.showInfo(I18nUtil.getMessage(MessageKeys.WORKSPACE_OPEN_FOLDER_EXISTS, existing.getName()));
        openWorkspace(existing);
    }

    static String suggestWorkspaceName(Path directory) {
        Path fileName = directory.getFileName();
        if (fileName == null) {
            return directory.toString();
        }
        Path parentName = directory.getParent() == null ? null : directory.getParent().getFileName();
        if (DEFAULT_EMBEDDED_DIRECTORY.equals(fileName.toString()) && parentName != null) {
            return parentName.toString();
        }
        return fileName.toString();
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

        int last = menu.getComponentCount() - 1;
        if (last >= 0 && menu.getComponent(last) instanceof JSeparator) {
            menu.remove(last);
        }
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
        } else {
            JMenuItem reloadItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_RELOAD_FROM_DISK));
            reloadItem.setIcon(IconUtil.createThemed("icons/refresh.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
            reloadItem.addActionListener(e -> reloadCurrentWorkspaceFromDisk());
            menu.add(reloadItem);
            menu.addSeparator();
        }
    }

    private void addGitMenuItems(JPopupMenu menu, Workspace workspace) {
        if (workspace.getType() == WorkspaceType.GIT || workspaceService.isGitEnabled(workspace)) {
            return;
        }

        // 所有本地工作区都可以转换为 Git 工作区（包括默认工作区）。
        JMenuItem convertToGitItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_CONVERT_TO_GIT));
        convertToGitItem.setIcon(IconUtil.create("icons/git.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        convertToGitItem.addActionListener(e -> convertToGitWorkspace(workspace));
        menu.add(convertToGitItem);
        if (!WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            menu.addSeparator();
        }
    }

    private void addManagementMenuItems(JPopupMenu menu, Workspace workspace) {
        // 默认工作区不可重命名和移除
        if (!WorkspaceStorageUtil.isDefaultWorkspace(workspace)) {
            // 重命名
            JMenuItem renameItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_RENAME));
            renameItem.setIcon(IconUtil.createThemed("icons/edit.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
            renameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
            renameItem.addActionListener(e -> renameWorkspace(workspace));
            menu.add(renameItem);

            // 移除工作区记录，保留本地文件
            JMenuItem removeItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.WORKSPACE_REMOVE));
            removeItem.setIcon(IconUtil.createThemed("icons/close.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
            removeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
            removeItem.addActionListener(e -> removeWorkspace(workspace));
            menu.add(removeItem);
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
            removeWorkspace(workspace);
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
                        WorkspaceExternalChangeMonitor.acknowledgeCurrentWorkspace();
                        // 更新顶部菜单栏工作区显示
                        UiSingletonFactory.getInstance(TopMenuBar.class).updateWorkspaceDisplay();
                        refreshWorkspaceList();
                    });
        } catch (Exception e) {
            log.error("Failed to switch workspace", e);
        }
    }

    private void saveCurrentWorkspaceScopedPanels() {
        WorkspaceDataReloader.saveCurrentWorkspacePanels();
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
     * Git 操作改写了仓库文件：当前工作区与之相同或位于同一仓库时重新加载，否则只刷新列表。
     */
    private void refreshAfterRepositoryChange(Workspace workspace) {
        WorkspaceDataReloader.reloadAfterRepositoryChange(workspace, this::refreshWorkspaceList);
    }

    private void reloadCurrentWorkspaceFromDisk() {
        WorkspaceExternalChangeMonitor.reloadCurrentWorkspaceFromDisk(() -> {
            refreshWorkspaceList();
            NotificationCenter.showInfo(I18nUtil.getMessage(MessageKeys.WORKSPACE_RELOAD_FROM_DISK_DONE));
        });
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
            refreshAfterRepositoryChange(workspace);
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
            refreshAfterRepositoryChange(workspace);
        }
    }

    /**
     * 显示 Git 历史记录
     */
    private void showGitHistory(Workspace workspace) {
        showWorkspaceTool(new GitHistoryPanel(workspace, () -> refreshAfterGitHistoryRestore(workspace)));
    }

    private void refreshAfterGitHistoryRestore(Workspace workspace) {
        refreshAfterRepositoryChange(workspace);
    }

    private void showGitBranches(Workspace workspace) {
        saveCurrentWorkspaceScopedPanels();
        showWorkspaceTool(new GitBranchPanel(workspace, () -> refreshAfterRepositoryChange(workspace)));
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
     * 移除工作区记录，本地目录及文件始终保留。
     */
    private void removeWorkspace(Workspace workspace) {
        String[] options = {
                I18nUtil.getMessage(MessageKeys.WORKSPACE_REMOVE),
                I18nUtil.getMessage(MessageKeys.BUTTON_CANCEL)
        };

        int choice = JOptionPane.showOptionDialog(
                this,
                I18nUtil.getMessage(MessageKeys.WORKSPACE_REMOVE_CONFIRM,
                        workspace.getName(), workspace.getPath()),
                I18nUtil.getMessage(MessageKeys.WORKSPACE_REMOVE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[1]
        );

        if (choice == 0) {
            // 检查是否移除的是当前工作区
            boolean isRemovingCurrentWorkspace = workspaceService.getCurrentWorkspace() != null &&
                    workspaceService.getCurrentWorkspace().getId().equals(workspace.getId());

            try {
                if (isRemovingCurrentWorkspace) {
                    saveCurrentWorkspaceScopedPanels();
                }
                workspaceService.removeWorkspace(workspace.getId());
            } catch (Exception e) {
                log.error("Failed to remove workspace", e);
                showError(I18nUtil.getMessage(MessageKeys.WORKSPACE_REMOVE_FAILED, e.getMessage()));
                return;
            }

            try {
                // 如果移除的是当前工作区，需要切换到新的当前工作区并刷新相关UI
                if (isRemovingCurrentWorkspace) {
                    Workspace newCurrentWorkspace = workspaceService.getCurrentWorkspace();
                    if (newCurrentWorkspace != null) {
                        // 切换环境变量文件
                        UiSingletonFactory.getInstance(EnvironmentPanel.class).switchWorkspaceAndRefreshUI(
                                SystemUtil.getEnvPathForWorkspace(newCurrentWorkspace));
                        // 切换请求集合文件
                        UiSingletonFactory.getInstance(CollectionTreePanel.class).switchWorkspaceAndRefreshUI(
                                SystemUtil.getCollectionPathForWorkspace(newCurrentWorkspace), () -> {
                                    refreshExistingWorkspaceScopedPanels();
                                    WorkspaceExternalChangeMonitor.acknowledgeCurrentWorkspace();
                                });

                    }
                }

                refreshWorkspaceList();
                UiSingletonFactory.getInstance(TopMenuBar.class).updateWorkspaceDisplay();
            } catch (Exception e) {
                log.error("Workspace was removed, but failed to refresh workspace UI", e);
                showError(I18nUtil.getMessage(MessageKeys.WORKSPACE_REMOVE_REFRESH_FAILED, e.getMessage()));
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
        GitWorkspaceLayout layout = workspaceService.detectGitLayout(workspace);
        if (layout == null) {
            return null;
        }

        boolean hasRemote = false;
        boolean hasUpstream = false;
        boolean remoteStatusKnown = false;
        RemoteStatus remoteStatus = null;
        try {
            remoteStatus = workspaceService.getRemoteStatus(workspace.getId());
            hasRemote = remoteStatus.hasRemote;
            hasUpstream = remoteStatus.hasUpstream;
            remoteStatusKnown = true;
        } catch (Exception ex) {
            log.warn("Failed to check remote status for workspace: {}", workspace.getId(), ex);
        }

        Runnable pullAction = hasRemote ? () -> performGitPull(workspace) : null;
        Runnable pushAction = hasRemote && hasUpstream ? () -> performGitPush(workspace) : null;
        Runnable remoteConfigAction = remoteStatusKnown ? () -> configureRemoteRepository(workspace) : null;
        return new WorkspaceDetailPanel.GitActions(
                () -> performGitCommit(workspace),
                pullAction,
                pushAction,
                remoteConfigAction,
                () -> showGitHistory(workspace),
                () -> showGitBranches(workspace),
                () -> showGitDiff(workspace),
                repositoryHint(layout),
                remoteStatus
        );
    }

    private static String repositoryHint(GitWorkspaceLayout layout) {
        if (layout.isFullControl()) {
            return null;
        }
        return layout.isEmbedded()
                ? I18nUtil.getMessage(MessageKeys.GIT_WORKSPACE_EMBEDDED_HINT,
                layout.getRepositoryRoot().toString(), layout.getScope())
                : I18nUtil.getMessage(MessageKeys.GIT_WORKSPACE_EXISTING_REPOSITORY_HINT,
                layout.getRepositoryRoot().toString());
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
