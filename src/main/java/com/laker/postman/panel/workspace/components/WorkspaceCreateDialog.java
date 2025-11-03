package com.laker.postman.panel.workspace.components;

import cn.hutool.core.util.RandomUtil;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.exception.WorkspaceCreateException;
import com.laker.postman.model.GitAuthType;
import com.laker.postman.model.GitRepoSource;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.SystemUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 创建工作区对话框
 * 支持创建本地工作区和Git工作区
 */
@Slf4j
public class WorkspaceCreateDialog extends ProgressDialog {

    private static final String DEFAULT_BRANCH = "master";
    public static final String WORKSPACES = "workspaces";

    @Getter
    private transient Workspace workspace;

    // UI组件
    private JTextField nameField;
    private JTextArea descriptionArea;
    private JRadioButton localTypeRadio;
    private JRadioButton gitTypeRadio;
    private JTextField pathField;
    private JButton browseButton;
    private JCheckBox autoGeneratePathCheckBox;

    // Git相关组件
    private JPanel gitPanel;
    private JRadioButton cloneRadio;
    private JRadioButton initRadio;
    private JTextField gitUrlField;
    private GitAuthPanel gitAuthPanel;

    // 新增分支相关组件
    private JTextField branchField;
    private JLabel branchLabel;

    public WorkspaceCreateDialog(Window parent) {
        super(parent, I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE));
        initComponents();
        initDialog();
    }

    private void initComponents() {
        // 基本信息
        nameField = new JTextField(10);
        descriptionArea = new JTextArea(3, 10);
        descriptionArea.setLineWrap(true);
        descriptionArea.setBackground(Color.white);
        descriptionArea.setWrapStyleWord(true);

        // 工作区类型
        localTypeRadio = new JRadioButton(I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_LOCAL), true);
        gitTypeRadio = new JRadioButton(I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_GIT));
        ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(localTypeRadio);
        typeGroup.add(gitTypeRadio);

        // 路径选择 - 设置默认工作区目录
        pathField = new JTextField(15);
        setDefaultWorkspacePath(); // 设置默认路径

        browseButton = new JButton(I18nUtil.getMessage(MessageKeys.WORKSPACE_SELECT_PATH));
        browseButton.setIcon(new FlatSVGIcon("icons/file.svg", 16, 16));

        // 自动生成路径选项
        autoGeneratePathCheckBox = new JCheckBox(I18nUtil.getMessage(MessageKeys.WORKSPACE_AUTO_GENERATE_PATH), true);
        autoGeneratePathCheckBox.setFont(FontsUtil.getDefaultFont(Font.PLAIN, 11));

        // Git相关组件
        cloneRadio = new JRadioButton(I18nUtil.getMessage(MessageKeys.WORKSPACE_CLONE_FROM_REMOTE), true);
        initRadio = new JRadioButton(I18nUtil.getMessage(MessageKeys.WORKSPACE_INIT_LOCAL));
        ButtonGroup gitModeGroup = new ButtonGroup();
        gitModeGroup.add(cloneRadio);
        gitModeGroup.add(initRadio);

        gitUrlField = new JTextField(15);
        gitAuthPanel = new GitAuthPanel();

        // 初始化分支字段
        branchField = new JTextField(15);
        branchField.setText(DEFAULT_BRANCH);

        // 进度相关组件
        progressPanel = new ProgressPanel("Progress");
        progressPanel.setVisible(false);

        // 设置默认字体
        Font defaultFont = FontsUtil.getDefaultFont(Font.PLAIN, 12);
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
        Path defaultWorkspaceDir = SystemUtil.EASY_POSTMAN_HOME.resolve(WORKSPACES);
        pathField.setText(defaultWorkspaceDir.toString());
    }

    /**
     * 根据工作区名称生成符合路径规则的目录名
     */
    private Path generatePathFromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
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

        return SystemUtil.EASY_POSTMAN_HOME.resolve(WORKSPACES).resolve(cleanName);
    }

    @Override
    protected void setupLayout() {
        setLayout(new BorderLayout());

        // 创建主容器面板
        JPanel containerPanel = new JPanel(new BorderLayout());
        containerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));

        // 创建内容面板 - 使用滚动面板以适应不同屏幕大小
        JPanel contentPanel = new JPanel(new BorderLayout());

        // 基本信息面板
        JPanel basicPanel = createBasicInfoPanel();
        contentPanel.add(basicPanel, BorderLayout.NORTH);

        // Git配置面板
        gitPanel = createGitPanel();
        gitPanel.setVisible(false);
        contentPanel.add(gitPanel, BorderLayout.CENTER);

        // 为内容面板添加滚动支持
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        containerPanel.add(scrollPane, BorderLayout.CENTER);

        // 底部面板：进度条 + 按钮
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));

        // 进度面板
        bottomPanel.add(progressPanel, BorderLayout.NORTH);

        // 按钮面板
        JPanel buttonPanel = createStandardButtonPanel(I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE));
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        containerPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(containerPanel, BorderLayout.CENTER);

        // 设置对话框的初始大小
        setPreferredSize(new Dimension(550, 520));
    }

    @Override
    protected void setupEventHandlers() {
        // 工作区类型切换
        ActionListener typeChangeListener = e -> updateGitPanelVisibility();
        localTypeRadio.addActionListener(typeChangeListener);
        gitTypeRadio.addActionListener(typeChangeListener);

        // Git模式切换
        ActionListener gitModeListener = e -> updateGitUrlFieldState();
        cloneRadio.addActionListener(gitModeListener);
        initRadio.addActionListener(gitModeListener);

        // 路径浏览
        browseButton.addActionListener(this::browseForPath);

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

        // 设置初始状态
        pathField.setEditable(false);
        browseButton.setEnabled(false);
    }

    /**
     * 根据名称更新路径
     */
    private void updatePathFromName() {
        String name = nameField.getText();
        Path generatedPath = generatePathFromName(name);
        pathField.setText(generatedPath == null ? "":generatedPath.toString());
    }

    private JPanel createBasicInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                I18nUtil.getMessage(MessageKeys.WORKSPACE_INFO),
                TitledBorder.LEFT,
                TitledBorder.TOP,
                FontsUtil.getDefaultFont(Font.BOLD, 12)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // 工作区名称
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_NAME) + ":"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(nameField, gbc);

        // 描述
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_DESCRIPTION) + ":"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        panel.add(new JScrollPane(descriptionArea), gbc);

        // 工作区类型
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE) + ":"), gbc);
        gbc.gridx = 1;
        panel.add(localTypeRadio, gbc);
        gbc.gridx = 2;
        panel.add(gitTypeRadio, gbc);

        // 自动生成路径选项
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(autoGeneratePathCheckBox, gbc);

        // 本地路径
        gbc.gridx = 0;
        gbc.gridy = 4;
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

        return panel;
    }

    private JPanel createGitPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Git " + I18nUtil.getMessage(MessageKeys.WORKSPACE_INFO),
                TitledBorder.LEFT,
                TitledBorder.TOP,
                FontsUtil.getDefaultFont(Font.BOLD, 12)
        ));

        // Git模式选择
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.add(cloneRadio);
        modePanel.add(initRadio);
        panel.add(modePanel, BorderLayout.NORTH);

        // Git URL、分支配置
        JPanel gitConfigPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Git URL
        gbc.gridx = 0;
        gbc.gridy = 0;
        gitConfigPanel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_GIT_URL) + ":"), gbc);
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

        panel.add(gitConfigPanel, BorderLayout.CENTER);

        // Git认证面板
        panel.add(gitAuthPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void updateGitPanelVisibility() {
        boolean isGit = gitTypeRadio.isSelected();
        gitPanel.setVisible(isGit);

        // 调整对话框大小以适应内容
        if (isGit) {
            setPreferredSize(new Dimension(550, 680));
        } else {
            setPreferredSize(new Dimension(550, 420));
        }

        pack();
        setLocationRelativeTo(getParent());
    }

    private void updateGitUrlFieldState() {
        boolean enableUrl = cloneRadio.isSelected();
        gitUrlField.setEnabled(enableUrl);
        gitAuthPanel.setComponentsEnabled(enableUrl);

        // 根据模式控制分支字段的标签和状态
        if (cloneRadio.isSelected()) {
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
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(I18nUtil.getMessage(MessageKeys.WORKSPACE_SELECT_PATH));

        String currentPathStr = pathField.getText().trim();
        Path currentPath = SystemUtil.EASY_POSTMAN_HOME.resolve(WORKSPACES);
        if (!currentPathStr.isEmpty()) {
            currentPath = Paths.get(currentPathStr);
        }

        // 确保父目录存在
        Path parentDir = currentPath.getParent();
        if (parentDir != null && Files.exists(parentDir)) {
            chooser.setCurrentDirectory(parentDir.toFile());
        } else {
            chooser.setCurrentDirectory(SystemUtil.EASY_POSTMAN_HOME.toFile());
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
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
        if (gitTypeRadio.isSelected() && cloneRadio.isSelected()) {
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
        // 初始化模式下只需要验证分支信息
        if (gitTypeRadio.isSelected() && initRadio.isSelected()) {
            String branch = branchField.getText().trim();
            if (branch.isEmpty()) {
                throw new IllegalArgumentException(I18nUtil.getMessage(MessageKeys.WORKSPACE_VALIDATION_GIT_BRANCH_INVALID));
            }
        }

        // 构建workspace对象供后续使用
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
    protected void onOperationSuccess() {
        workspace = buildWorkspace();
        super.onOperationSuccess();
    }

    @Override
    protected void setInputComponentsEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        descriptionArea.setEnabled(enabled);
        localTypeRadio.setEnabled(enabled);
        gitTypeRadio.setEnabled(enabled);
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
            cloneRadio.setEnabled(enabled);
            initRadio.setEnabled(enabled);
            gitUrlField.setEnabled(enabled);
            gitAuthPanel.setComponentsEnabled(enabled);
            branchField.setEnabled(enabled);
        }
    }

    private Workspace buildWorkspace() {
        Workspace ws = new Workspace();
        ws.setName(nameField.getText().trim());
        ws.setDescription(descriptionArea.getText().trim());
        ws.setPath(Paths.get(pathField.getText().trim()));

        if (localTypeRadio.isSelected()) {
            ws.setType(WorkspaceType.LOCAL);
        } else {
            ws.setType(WorkspaceType.GIT);

            // 设置本地分支信息
            String localBranch = branchField.getText().trim();
            if (!localBranch.isEmpty()) {
                ws.setCurrentBranch(localBranch);
            }

            if (cloneRadio.isSelected()) {
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
