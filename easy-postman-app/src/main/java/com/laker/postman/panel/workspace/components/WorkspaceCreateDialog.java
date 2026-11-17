package com.laker.postman.panel.workspace.components;

import cn.hutool.core.util.RandomUtil;
import com.formdev.flatlaf.util.SystemFileChooser;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.setting.SettingsInputStyle;
import com.laker.postman.common.constants.ConfigPathConstants;
import com.laker.postman.common.exception.WorkspaceCreateException;
import com.laker.postman.model.GitAuthType;
import com.laker.postman.model.GitRepoSource;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.plugin.git.GitWorkspaceLayout;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 创建工作区对话框。
 * <p>
 * “新建”只需选择目录：目录位于已有 Git 仓库中时直接使用该仓库，否则可选初始化一个由 EasyPostman 管理的仓库；
 * “克隆”是独立入口。
 */
@Slf4j
public class WorkspaceCreateDialog extends ProgressDialog {

    private static final String DEFAULT_BRANCH = "master";
    private static final int DIALOG_MIN_WIDTH = 580;
    public static final String WORKSPACES = "workspaces";

    @Getter
    private transient Workspace workspace;

    // UI组件
    private JTextField nameField;
    private JTextArea descriptionArea;
    private final boolean cloneMode;
    private JCheckBox initGitCheckBox;
    private boolean pathInRepository;
    private int pathStatusGeneration;
    /**
     * 与当前路径输入对应的最新后台探测结果；界面线程只读取它，不直接访问磁盘。
     */
    private WorkspaceDirectoryProbe pathProbe;
    private boolean confirmAfterProbe;
    private JTextField pathField;
    private JButton browseButton;
    private JCheckBox autoGeneratePathCheckBox;
    private JScrollPane descriptionScrollPane;

    // Git相关组件
    private JPanel gitPanel;
    private JLabel gitUrlLabel;
    private JTextField gitUrlField;
    private GitAuthPanel gitAuthPanel;

    // 新增分支相关组件
    private JTextField branchField;
    private JLabel branchLabel;

    private JLabel pathStatusLabel;
    private Timer pathStatusTimer;

    public WorkspaceCreateDialog(Window parent) {
        this(parent, false);
    }

    public WorkspaceCreateDialog(Window parent, boolean cloneFromGit) {
        super(parent, I18nUtil.getMessage(cloneFromGit ? MessageKeys.WORKSPACE_ACTION_CLONE : MessageKeys.WORKSPACE_CREATE));
        this.cloneMode = cloneFromGit;
        initComponents();
        initDialog();
    }

    private void initComponents() {
        // 基本信息
        nameField = new JTextField(10);
        SettingsInputStyle.apply(nameField);
        descriptionArea = new JTextArea(2, 10);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        ToolWindowSurfaceStyle.applyTextComponentInput(descriptionArea);
        descriptionScrollPane = new JScrollPane(descriptionArea);
        ToolWindowSurfaceStyle.applyFramedScrollPaneCard(descriptionScrollPane);

        // 是否初始化 Git：目录已在仓库中时禁用，直接使用该仓库
        initGitCheckBox = new JCheckBox(I18nUtil.getMessage(MessageKeys.WORKSPACE_INIT_GIT));

        // 路径选择 - 设置默认工作区目录
        pathField = new JTextField(15);
        SettingsInputStyle.apply(pathField);
        setDefaultWorkspacePath(); // 设置默认路径

        browseButton = new JButton(I18nUtil.getMessage(MessageKeys.WORKSPACE_SELECT_PATH));
        browseButton.setIcon(IconUtil.createThemed("icons/file.svg", 16, 16));

        pathStatusLabel = new JLabel();
        pathStatusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        pathStatusLabel.setForeground(ModernColors.getTextSecondary());
        pathStatusLabel.setVisible(false);

        // 自动生成路径选项
        autoGeneratePathCheckBox = new JCheckBox(I18nUtil.getMessage(MessageKeys.WORKSPACE_AUTO_GENERATE_PATH), true);
        autoGeneratePathCheckBox.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));

        // Git相关组件
        gitUrlField = new JTextField(15);
        SettingsInputStyle.apply(gitUrlField);
        gitAuthPanel = new GitAuthPanel();

        // 初始化分支字段
        branchField = new JTextField(15);
        branchField.setText(DEFAULT_BRANCH);
        SettingsInputStyle.apply(branchField);

        // 进度相关组件
        progressPanel = new ProgressPanel("Progress");
        progressPanel.setVisible(false);

        // 设置默认字体
        Font defaultFont = FontsUtil.getDefaultFont(Font.PLAIN);
        nameField.setFont(defaultFont);
        descriptionArea.setFont(defaultFont);
        pathField.setFont(defaultFont);
        gitUrlField.setFont(defaultFont);
        branchField.setFont(defaultFont);
    }

    /**
     * 设置默认的工作区路径
     */
    private void setDefaultWorkspacePath() {
        String defaultWorkspaceDir = SystemUtil.getEasyPostmanPath() + WORKSPACES;
        pathField.setText(defaultWorkspaceDir);
    }

    /**
     * 根据工作区名称生成符合路径规则的目录名
     */
    private String generatePathFromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }

        // 清理名称，生成合法的目录名
        String cleanName = name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_") // 替换非法字符
                .replaceAll("\\s+", "_") // 空格替换为下划线
                .replaceAll("_{2,}", "_") // 多个下划线合并为一个
                .replaceAll("^_|_$", ""); // 去除首尾下划线

        if (cleanName.isEmpty()) {
            cleanName = RandomUtil.randomString(10); // 如果清理后为空，使用随机字符串
        }

        return ConfigPathConstants.WORKSPACES_DIR + cleanName;
    }

    @Override
    protected void setupLayout() {
        setLayout(new BorderLayout());

        // 创建主容器面板
        JPanel containerPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(containerPanel);
        containerPanel.setBorder(BorderFactory.createEmptyBorder(16, 18, 0, 18));

        // 创建内容面板 - 使用滚动面板以适应不同屏幕大小
        JPanel contentPanel = new JPanel(new GridBagLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(contentPanel);
        GridBagConstraints contentGbc = new GridBagConstraints();
        contentGbc.gridx = 0;
        contentGbc.weightx = 1.0;
        contentGbc.fill = GridBagConstraints.HORIZONTAL;
        contentGbc.anchor = GridBagConstraints.NORTHWEST;

        // 基本信息面板
        JPanel basicPanel = createBasicInfoPanel();
        contentGbc.gridy = 0;
        contentGbc.weighty = 0;
        contentPanel.add(basicPanel, contentGbc);

        // Git配置面板
        gitPanel = createGitPanel();
        gitPanel.setVisible(cloneMode);
        contentGbc.gridy = 1;
        contentGbc.insets = new Insets(10, 0, 0, 0);
        contentPanel.add(gitPanel, contentGbc);

        contentGbc.gridy = 2;
        contentGbc.weighty = 1.0;
        contentGbc.fill = GridBagConstraints.BOTH;
        contentGbc.insets = new Insets(0, 0, 0, 0);
        contentPanel.add(Box.createVerticalGlue(), contentGbc);

        // 为内容面板添加滚动支持
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        ToolWindowSurfaceStyle.applyDialogScrollPane(scrollPane);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        containerPanel.add(scrollPane, BorderLayout.CENTER);

        // 底部面板：进度条 + 按钮
        JPanel bottomPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(bottomPanel);

        // 进度面板
        bottomPanel.add(progressPanel, BorderLayout.NORTH);

        // 按钮面板
        JPanel buttonPanel = createStandardButtonPanel(I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE));
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        containerPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(containerPanel, BorderLayout.CENTER);

        // 保持一个稳定的最小宽度，高度由当前可见内容决定
        setPreferredSize(new Dimension(DIALOG_MIN_WIDTH, getPreferredSize().height));
        setMinimumSize(new Dimension(DIALOG_MIN_WIDTH, 0));
    }

    @Override
    protected void setupEventHandlers() {
        initGitCheckBox.addActionListener(e -> updateGitPanelVisibility());

        // 路径浏览
        browseButton.addActionListener(this::browseForPath);

        // 路径变化时检测所在 Git 仓库和已有数据，给出提示
        pathStatusTimer = new Timer(250, e -> updatePathStatus());
        pathStatusTimer.setRepeats(false);
        pathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                pathStatusTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                pathStatusTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                pathStatusTimer.restart();
            }
        });

        // 名称字段变化监听器 - 自动生成路径
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (autoGeneratePathCheckBox.isSelected()) {
                    updatePathFromName();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (autoGeneratePathCheckBox.isSelected()) {
                    updatePathFromName();
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                if (autoGeneratePathCheckBox.isSelected()) {
                    updatePathFromName();
                }
            }
        });

        // 自动生成路径复选框
        autoGeneratePathCheckBox.addActionListener(e -> {
            boolean autoGen = autoGeneratePathCheckBox.isSelected();
            pathField.setEditable(!autoGen);
            browseButton.setEnabled(!autoGen);
            if (autoGen) {
                updatePathFromName();
            }
        });

        // 初始状态
        updateGitPanelVisibility();
        updateGitUrlFieldState();
        updatePathStatus();

        // 设置初始状态
        pathField.setEditable(false);
        browseButton.setEnabled(false);
    }

    /**
     * 根据名称更新路径
     */
    private void updatePathFromName() {
        String name = nameField.getText();
        String generatedPath = generatePathFromName(name);
        pathField.setText(generatedPath);
    }

    private boolean isGitSelected() {
        return cloneMode || (!pathInRepository && initGitCheckBox.isSelected());
    }

    /**
     * Git 仓库探测会访问磁盘（网络盘、大仓库可能较慢），在后台执行；只应用与当前输入一致的最新结果。
     */
    private void updatePathStatus() {
        String pathText = pathField.getText().trim();
        boolean clone = cloneMode;
        int generation = ++pathStatusGeneration;
        pathProbe = null;
        Path path = parsePath(pathText);
        if (clone || path == null) {
            confirmAfterProbe = false;
            applyPathStatus(clone, null);
            return;
        }
        new SwingWorker<WorkspaceDirectoryProbe, Void>() {
            @Override
            protected WorkspaceDirectoryProbe doInBackground() {
                return WorkspaceDirectoryProbe.probe(path);
            }

            @Override
            protected void done() {
                if (generation != pathStatusGeneration) {
                    return;
                }
                WorkspaceDirectoryProbe probe;
                try {
                    probe = get();
                } catch (Exception e) {
                    probe = null;
                }
                applyPathStatus(false, probe);
                if (confirmAfterProbe) {
                    confirmAfterProbe = false;
                    confirmButton.setEnabled(true);
                    if (probe != null) {
                        retryConfirm();
                    }
                }
            }
        }.execute();
    }

    private void applyPathStatus(boolean clone, WorkspaceDirectoryProbe probe) {
        pathProbe = probe;
        if (!clone) {
            pathInRepository = probe != null && probe.inRepository();
            initGitCheckBox.setEnabled(!pathInRepository && !isOperationInProgress());
            initGitCheckBox.setToolTipText(pathInRepository
                    ? I18nUtil.getMessage(MessageKeys.WORKSPACE_INIT_GIT_IN_REPO) : null);
            updateGitPanelVisibility();
        }
        String message = clone ? null : describePath(probe);
        pathStatusLabel.setText(message == null ? "" : "<html>" + escapeHtml(message) + "</html>");
        pathStatusLabel.setVisible(message != null);
        pathStatusLabel.revalidate();
    }

    private static Path parsePath(String pathText) {
        if (pathText.isEmpty()) {
            return null;
        }
        try {
            return Paths.get(pathText).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private static String describePath(WorkspaceDirectoryProbe probe) {
        if (probe == null) {
            return null;
        }
        GitWorkspaceLayout layout = probe.layout();
        StringBuilder message = new StringBuilder();
        if (layout != null) {
            message.append(I18nUtil.getMessage(MessageKeys.WORKSPACE_PATH_IN_REPOSITORY,
                    layout.getRepositoryRoot().toString(),
                    layout.isEmbedded() ? layout.getScope() : "./"));
        }
        if (probe.hasData()) {
            if (!message.isEmpty()) {
                message.append(' ');
            }
            message.append(I18nUtil.getMessage(MessageKeys.WORKSPACE_PATH_HAS_DATA));
        }
        return message.isEmpty() ? null : message.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel createBasicInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        ToolWindowSurfaceStyle.applyDialogSection(panel);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel titleLabel = new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_INFO));
        titleLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(titleLabel, gbc);

        // 工作区名称
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_NAME) + ":"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(nameField, gbc);

        // 描述
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_DESCRIPTION) + ":"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        panel.add(descriptionScrollPane, gbc);


        // 自动生成路径选项
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(autoGeneratePathCheckBox, gbc);

        // 本地路径
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_PATH) + ":"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(pathField, gbc);
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(browseButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 8, 6, 8);
        panel.add(pathStatusLabel, gbc);

        if (!cloneMode) {
            gbc.gridx = 0;
            gbc.gridy = 7;
            gbc.gridwidth = 3;
            gbc.insets = new Insets(2, 8, 6, 8);
            panel.add(initGitCheckBox, gbc);
        }

        return panel;
    }

    private JPanel createGitPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        ToolWindowSurfaceStyle.applyDialogSection(panel);

        GridBagConstraints rootGbc = new GridBagConstraints();
        rootGbc.gridx = 0;
        rootGbc.weightx = 1.0;
        rootGbc.fill = GridBagConstraints.HORIZONTAL;
        rootGbc.anchor = GridBagConstraints.NORTHWEST;

        JLabel titleLabel = new JLabel("Git " + I18nUtil.getMessage(MessageKeys.WORKSPACE_INFO));
        titleLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD));
        rootGbc.gridy = 0;
        rootGbc.insets = new Insets(6, 8, 2, 8);
        panel.add(titleLabel, rootGbc);

        // Git URL、分支配置
        JPanel gitConfigPanel = new JPanel(new GridBagLayout());
        gitConfigPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Git URL
        gbc.gridx = 0;
        gbc.gridy = 0;
        gitUrlLabel = new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_GIT_URL) + ":");
        gitConfigPanel.add(gitUrlLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gitConfigPanel.add(gitUrlField, gbc);

        // 分支配置
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        branchLabel = new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE_DIALOG_BRANCH_LABEL));
        gitConfigPanel.add(branchLabel, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gitConfigPanel.add(branchField, gbc);

        rootGbc.gridy = 2;
        rootGbc.insets = new Insets(0, 8, 2, 8);
        panel.add(gitConfigPanel, rootGbc);

        // Git认证面板
        rootGbc.gridy = 3;
        rootGbc.insets = new Insets(0, 8, 6, 8);
        panel.add(gitAuthPanel, rootGbc);

        // 占位空行，确保内容始终贴顶显示，而不是拉伸中间表单区域
        rootGbc.gridy = 4;
        rootGbc.weighty = 1.0;
        rootGbc.fill = GridBagConstraints.BOTH;
        rootGbc.insets = new Insets(0, 0, 0, 0);
        panel.add(Box.createVerticalGlue(), rootGbc);

        return panel;
    }

    private void updateGitPanelVisibility() {
        boolean isGit = isGitSelected();
        if (gitPanel.isVisible() == isGit && isShowing()) {
            return;
        }
        gitPanel.setVisible(isGit);

        setPreferredSize(null);
        pack();
        if (getWidth() < DIALOG_MIN_WIDTH) {
            setSize(new Dimension(DIALOG_MIN_WIDTH, getHeight()));
        }
        setLocationRelativeTo(getParent());
    }

    private void updateGitUrlFieldState() {
        // 初始化新仓库时只需要分支名，远程地址与认证在之后添加远程仓库时再配置
        gitUrlLabel.setVisible(cloneMode);
        gitUrlField.setVisible(cloneMode);
        gitAuthPanel.setVisible(cloneMode);
        gitUrlField.setEnabled(cloneMode);
        gitAuthPanel.setComponentsEnabled(cloneMode);

        // 根据模式控制分支字段的标签和状态
        if (cloneMode) {
            branchLabel.setText(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_REMOTE_BRANCH) + ":");
            branchField.setEnabled(true);
            if (branchField.getText().trim().isEmpty()) {
                branchField.setText(DEFAULT_BRANCH);
            }
        } else {
            branchLabel.setText(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_LOCAL_BRANCH) + ":");
            branchField.setEnabled(true);
            if (branchField.getText().trim().isEmpty()) {
                branchField.setText(DEFAULT_BRANCH);
            }
        }
    }

    private void browseForPath(ActionEvent e) {
        SystemFileChooser chooser = FileChooserUtil.createDirectoryChooser(
                "workspace.create.path",
                I18nUtil.getMessage(MessageKeys.WORKSPACE_SELECT_PATH));

        String currentPath = pathField.getText().trim();
        if (currentPath.isEmpty()) {
            currentPath = ConfigPathConstants.WORKSPACES_DIR;
        }

        // 确保父目录存在
        File parentDir = new File(currentPath).getParentFile();
        if (parentDir != null && parentDir.exists()) {
            chooser.setCurrentDirectory(parentDir);
        } else {
            chooser.setCurrentDirectory(new File(SystemUtil.getEasyPostmanPath()));
        }

        int result = chooser.showOpenDialog(this);
        if (result == SystemFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            if (nameField.getText().trim().isEmpty()) {
                autoGeneratePathCheckBox.setSelected(false);
                nameField.setText(selectedDir.getName());
            }
            pathField.setText(selectedDir.getAbsolutePath());
            // 取消自动生成，因为用户手动选择了路径
            autoGeneratePathCheckBox.setSelected(false);
            pathField.setEditable(true);
            browseButton.setEnabled(true);
        }
    }

    @Override
    protected void validateInput() throws IllegalArgumentException {
        // 验证工作区名称
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(I18nUtil.getMessage(MessageKeys.WORKSPACE_VALIDATION_NAME_REQUIRED));
        }

        // 验证路径
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            throw new IllegalArgumentException(I18nUtil.getMessage(MessageKeys.WORKSPACE_VALIDATION_PATH_REQUIRED));
        }

        // Git工作区特殊验证
        // 克隆模式下必须提供Git URL 和认证信息 和分支信息
        if (cloneMode) {
            String gitUrl = gitUrlField.getText().trim();
            if (gitUrl.isEmpty()) {
                throw new IllegalArgumentException(I18nUtil.getMessage(MessageKeys.WORKSPACE_VALIDATION_GIT_URL_REQUIRED));
            }
            String branch = branchField.getText().trim();
            if (branch.isEmpty()) {
                throw new IllegalArgumentException(I18nUtil.getMessage(MessageKeys.WORKSPACE_VALIDATION_GIT_BRANCH_INVALID));
            }

            // 验证认证信息
            gitAuthPanel.validateAuth();
        }
        WorkspaceDirectoryProbe probe = null;
        if (!cloneMode) {
            probe = pathProbe;
            if (probe == null || !probe.isFor(parsePath(path))) {
                // 磁盘探测尚未完成：后台探测结束后自动继续，不在界面线程访问磁盘
                Path parsed = parsePath(path);
                if (parsed == null) {
                    throw new IllegalArgumentException(I18nUtil.getMessage(MessageKeys.WORKSPACE_VALIDATION_PATH_REQUIRED));
                }
                confirmAfterProbe = true;
                confirmButton.setEnabled(false);
                updatePathStatus();
                throw new IllegalArgumentException("");
            }
            pathInRepository = probe.inRepository();
        }
        // 初始化模式下只需要验证分支信息
        if (isGitSelected() && !cloneMode) {
            if (probe.inRepository()) {
                throw new IllegalArgumentException(I18nUtil.getMessage(MessageKeys.GIT_WORKSPACE_ALREADY_IN_REPOSITORY));
            }
            String branch = branchField.getText().trim();
            if (branch.isEmpty()) {
                throw new IllegalArgumentException(I18nUtil.getMessage(MessageKeys.WORKSPACE_VALIDATION_GIT_BRANCH_INVALID));
            }
        }

        // 在已有目录（尤其是代码仓库）中创建数据前确认
        if (!cloneMode) {
            // 用完即清空：再次确认（如创建失败后重试）时重新探测，不沿用旧的磁盘状态
            pathProbe = null;
            if (!WorkspaceConfirmations.confirmCreateData(this, probe, false)) {
                throw new IllegalArgumentException("");
            }
        }

        // 构建workspace对象供后续使用；createWorkspace 会为同一对象分配 ID
        workspace = buildWorkspace();
    }

    @Override
    protected SwingWorker<Void, String> createWorkerTask() {
        return new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                publish(I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE_DIALOG_CREATING));
                setProgress(10);
                try {
                    // 使用 WorkspaceService 创建工作区，它会处理所有的 Git 操作
                    WorkspaceService.getInstance().createWorkspace(workspace);
                    publish(I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE_DIALOG_CREATION_COMPLETED));
                    setProgress(100);
                } catch (Exception e) {
                    // 重新抛出异常，让 SwingWorker 的错误处理机制处理
                    log.error(I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE_DIALOG_CREATION_FAILED), e);
                    throw new WorkspaceCreateException(I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE_DIALOG_CREATION_FAILED_WITH_MESSAGE, e.getMessage()), e);
                }

                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) {
                    progressPanel.getStatusLabel().setText(chunks.get(chunks.size() - 1));
                }
            }
        };
    }

    @Override
    protected void setInputComponentsEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        descriptionArea.setEnabled(enabled);
        if (!cloneMode) {
            initGitCheckBox.setEnabled(enabled && !pathInRepository);
        }
        autoGeneratePathCheckBox.setEnabled(enabled);

        // 路径相关控件的启用状态取决于是否自动生成
        if (enabled) {
            boolean autoGen = autoGeneratePathCheckBox.isSelected();
            pathField.setEditable(!autoGen);
            browseButton.setEnabled(!autoGen);
        } else {
            pathField.setEditable(false);
            browseButton.setEnabled(false);
        }

        if (gitPanel.isVisible()) {
            gitUrlField.setEnabled(enabled && cloneMode);
            gitAuthPanel.setComponentsEnabled(enabled && cloneMode);
            branchField.setEnabled(enabled);
        }
    }

    private Workspace buildWorkspace() {
        Workspace ws = new Workspace();
        ws.setName(nameField.getText().trim());
        ws.setDescription(descriptionArea.getText().trim());
        ws.setPath(pathField.getText().trim());

        if (!isGitSelected()) {
            ws.setType(WorkspaceType.LOCAL);
        } else {
            ws.setType(WorkspaceType.GIT);

            // 设置本地分支信息
            String localBranch = branchField.getText().trim();
            if (!localBranch.isEmpty()) {
                ws.setCurrentBranch(localBranch);
            }

            if (cloneMode) {
                ws.setGitRepoSource(GitRepoSource.CLONED);
                ws.setGitRemoteUrl(gitUrlField.getText().trim());

                GitAuthType authType = (GitAuthType) gitAuthPanel.getAuthTypeCombo().getSelectedItem();
                ws.setGitAuthType(authType);

                if (authType == GitAuthType.PASSWORD) {
                    ws.setGitUsername(gitAuthPanel.getUsername());
                    ws.setGitPassword(gitAuthPanel.getPassword());
                } else if (authType == GitAuthType.TOKEN) {
                    ws.setGitUsername(gitAuthPanel.getUsername());
                    ws.setGitToken(gitAuthPanel.getToken());
                } else if (authType == GitAuthType.SSH_KEY) {
                    ws.setSshPrivateKeyPath(gitAuthPanel.getSshKeyPath());
                    ws.setSshPassphrase(gitAuthPanel.getSshPassphrase());
                }
            } else {
                ws.setGitRepoSource(GitRepoSource.INITIALIZED);
            }
        }

        return ws;
    }
}
