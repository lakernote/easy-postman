package com.laker.postman.panel.workspace.components;

import com.laker.postman.common.component.notification.NotificationCenter;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.component.button.PrimaryButton;
import com.laker.postman.common.component.StepIndicator;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.model.GitOperation;
import com.laker.postman.model.GitOperationResult;
import com.laker.postman.model.GitStatusCheck;
import com.laker.postman.model.Workspace;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.service.render.HttpHtmlRenderer;
import com.laker.postman.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Git 操作对话框
 * 场景1：提交 推送 拉取
 * 场景2：本地提交和远程提交不冲突
 */
@Slf4j
public class GitOperationDialog extends JDialog {

    // 常量定义
    private static final String OPTION_FORCE = "force"; // 强制操作
    private static final String OPTION_CANCEL = "cancel"; // 取消操作
    private static final String OPTION_COMMIT_FIRST = "commit_first"; // 先提交
    private static final String OPTION_STASH = "stash"; // 暂存
    private static final String OPTION_PULL_FIRST = "pull_first"; // 先拉取
    private static final String OPTION_COMMIT_AND_PUSH = "commit_and_push"; // 提交并推送

    private final transient Workspace workspace;
    private final GitOperation operation;
    private final transient WorkspaceService workspaceService;

    @Getter
    private boolean confirmed = false;
    @Getter
    private String commitMessage;

    // 步骤指示器
    private StepIndicator stepIndicator;
    private JEditorPane detailsArea;
    private JEditorPane fileChangesArea;
    private JTextArea commitMessageArea;
    private JLabel statusIcon;
    private JLabel statusMessage;

    // 操作选择
    private JPanel optionsPanel;
    private ButtonGroup optionGroup;
    private JButton retryButton;
    private JButton cancelButton;
    private JLabel actionHintLabel;
    private JPanel footerFeedbackPanel;
    private CardLayout footerFeedbackLayout;

    // 进度和按钮
    private JProgressBar progressBar;
    private JButton executeButton;
    private boolean checkingStatus;
    private boolean operationRunning;

    // 检测结果
    private GitStatusCheck statusCheck;

    public GitOperationDialog(Window parent, Workspace workspace, GitOperation operation) {
        super(parent, operation.getDisplayName() + " - " + workspace.getName(), ModalityType.APPLICATION_MODAL);
        this.workspace = workspace;
        this.operation = operation;
        this.workspaceService = WorkspaceService.getInstance();

        setupDialog();
        initializeUI();
        performPreOperationCheck();
    }

    /**
     * 将 Color 转换为 HTML 颜色字符串（格式: #RRGGBB）
     */
    private String toHtmlColor(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * 获取 HTML 中使用的成功色（绿色）
     */
    private String getHtmlSuccessColor() {
        return toHtmlColor(ModernColors.getSuccess());
    }

    /**
     * 获取 HTML 中使用的警告色（橙色）
     */
    private String getHtmlWarningColor() {
        return toHtmlColor(ModernColors.getWarning());
    }

    /**
     * 获取 HTML 中使用的错误色（红色）
     */
    private String getHtmlErrorColor() {
        return toHtmlColor(ModernColors.getError());
    }

    /**
     * 获取 HTML 中使用的信息色（蓝色）
     */
    private String getHtmlInfoColor() {
        return toHtmlColor(ModernColors.getPrimary());
    }

    /**
     * 获取 HTML 中使用的次要文本色（灰色）
     */
    private String getHtmlSecondaryColor() {
        return toHtmlColor(ModernColors.getTextSecondary());
    }

    /**
     * 设置对话框基本属性
     */
    private void setupDialog() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(780, 560);
        setMinimumSize(new Dimension(720, 520));
        setLocationRelativeTo(getParent());
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogWindowChrome(this);
    }

    /**
     * 初始化UI组件
     */
    private void initializeUI() {
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(mainPanel);
        // 创建各个区域
        JPanel headerPanel = createHeaderPanel();
        JPanel stepPanel = createStepPanel();
        JPanel summaryPanel = createSummaryPanel();
        JPanel actionPanel = createActionPanel();
        JPanel footerPanel = createFooterPanel();

        // 布局主面板
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(stepPanel, BorderLayout.NORTH);
        centerPanel.add(summaryPanel, BorderLayout.CENTER);
        centerPanel.add(actionPanel, BorderLayout.SOUTH);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    /**
     * 创建头部面板 - 显示操作类型和基本信息
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogHeader(panel, 8, 24, 8, 24);

        // 左侧：操作图标和名称
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);

        FlatSVGIcon icon = IconUtil.createColored(
                GitOperationPresentation.getIconName(operation),
                22,
                22,
                GitOperationPresentation.getColor(operation)
        );
        JLabel operationIcon = new JLabel(icon);
        operationIcon.setBorder(new EmptyBorder(2, 0, 0, 10));

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 3));
        textPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(operation.getDisplayName());
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, +1));
        titleLabel.setForeground(ModernColors.getTextPrimary());

        JLabel subtitleLabel = new JLabel(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_WORKSPACE, workspace.getName()));
        subtitleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        subtitleLabel.setForeground(ModernColors.getTextSecondary());

        textPanel.add(titleLabel);
        textPanel.add(subtitleLabel);

        leftPanel.add(operationIcon);
        leftPanel.add(textPanel);

        // 右侧：分支信息
        JPanel rightPanel = createBranchInfoPanel();

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * 创建分支信息面板
     */
    private JPanel createBranchInfoPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 3));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 16, 0, 0));

        JLabel currentBranchLabel = new JLabel(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CURRENT_BRANCH,
                workspace.getCurrentBranch() != null ? workspace.getCurrentBranch() : I18nUtil.getMessage(MessageKeys.GIT_DIALOG_UNKNOWN)));
        currentBranchLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        currentBranchLabel.setForeground(ModernColors.getTextSecondary());
        currentBranchLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JLabel remoteBranchLabel = new JLabel(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOTE_BRANCH,
                workspace.getRemoteBranch() != null ? workspace.getRemoteBranch() : I18nUtil.getMessage(MessageKeys.GIT_DIALOG_NOT_SET)));
        remoteBranchLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        remoteBranchLabel.setForeground(ModernColors.getTextSecondary());
        remoteBranchLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        panel.add(currentBranchLabel);
        panel.add(remoteBranchLabel);

        return panel;
    }

    /**
     * 创建步骤指示器面板
     */
    private JPanel createStepPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        ToolWindowSurfaceStyle.applyDialogSurface(panel);
        panel.setBorder(new EmptyBorder(5, 24, 7, 24));

        stepIndicator = new StepIndicator();
        panel.add(stepIndicator);

        return panel;
    }

    /**
     * 创建摘要信息面板
     */
    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(panel);
        panel.setBorder(new EmptyBorder(0, 24, 8, 24));

        // 状态显示区域
        JPanel statusPanel = createStatusPanel();

        // 文件变更区域
        JPanel filesPanel = createFilesPanel();

        JSplitPane sectionsSplitPane = AppToolWindowChrome.createHorizontalInnerSplitPane(
                statusPanel,
                filesPanel,
                AppToolWindowChrome.DEFAULT_SIDE_WIDTH
        );
        sectionsSplitPane.setResizeWeight(0.45);
        ToolWindowSurfaceStyle.applyDialogSplitPane(sectionsSplitPane);
        panel.add(sectionsSplitPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建状态显示面板
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        applyIdeaSection(panel);
        panel.add(createSectionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_STATUS_CHECK)), BorderLayout.NORTH);

        JPanel statusInfoPanel = new JPanel(new BorderLayout(8, 0));
        statusInfoPanel.setOpaque(false);
        JPanel statusMessagePanel = new JPanel(new BorderLayout(8, 0));
        statusMessagePanel.setOpaque(false);
        // Use theme-adapted icon that automatically adjusts to dark/light theme
        statusIcon = new JLabel(IconUtil.createThemed("icons/refresh.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        statusMessage = new JLabel(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CHECKING_STATUS));
        statusMessage.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        statusMessage.setBorder(new EmptyBorder(0, 6, 0, 0));

        statusMessagePanel.add(statusIcon, BorderLayout.WEST);
        statusMessagePanel.add(statusMessage, BorderLayout.CENTER);
        statusInfoPanel.add(statusMessagePanel, BorderLayout.CENTER);
        retryButton = ModernButtonFactory.createButton(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_RETRY_CHECK), false);
        retryButton.setVisible(false);
        retryButton.addActionListener(e -> performPreOperationCheck());
        statusInfoPanel.add(retryButton, BorderLayout.EAST);

        detailsArea = new JEditorPane();
        detailsArea.setEditable(false);
        detailsArea.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        detailsArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        detailsArea.setBorder(new EmptyBorder(2, 0, 8, 0));
        detailsArea.setContentType("text/html"); // 支持HTML渲染
        ToolWindowSurfaceStyle.applyTextComponentDialogSurface(detailsArea);

        JScrollPane detailsScrollPane = new JScrollPane(detailsArea);
        detailsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        detailsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ToolWindowSurfaceStyle.applyDialogScrollPane(detailsScrollPane);
        styleIdeaScrollPane(detailsScrollPane);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 6));
        contentPanel.setOpaque(false);
        contentPanel.add(statusInfoPanel, BorderLayout.NORTH);
        contentPanel.add(detailsScrollPane, BorderLayout.CENTER);
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建文件变更面板
     */
    private JPanel createFilesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        applyIdeaSection(panel);
        panel.add(createSectionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_FILE_CHANGES)), BorderLayout.NORTH);

        JPanel fileChangesPanel = new JPanel(new BorderLayout());
        fileChangesPanel.setOpaque(false);

        fileChangesArea = new JEditorPane();
        fileChangesArea.setEditable(false);
        fileChangesArea.setFont(FontsUtil.getMonospacedFontWithOffset(Font.PLAIN, -2));
        fileChangesArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        fileChangesArea.setBorder(new EmptyBorder(2, 0, 10, 0));
        fileChangesArea.setContentType("text/html"); // 支持HTML渲染
        fileChangesArea.setText(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_LOADING_FILE_CHANGES));
        ToolWindowSurfaceStyle.applyTextComponentDialogSurface(fileChangesArea);

        JScrollPane scrollPane = new JScrollPane(fileChangesArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ToolWindowSurfaceStyle.applyDialogScrollPane(scrollPane);
        styleIdeaScrollPane(scrollPane);

        fileChangesPanel.add(scrollPane, BorderLayout.CENTER);

        // 如果是提交操作，添加提交信息输入区域
        if (operation == GitOperation.COMMIT) {
            JPanel commitPanel = createCommitMessagePanel();
            fileChangesPanel.add(commitPanel, BorderLayout.SOUTH);
        }

        panel.add(fileChangesPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建提交信息输入面板
     */
    private JPanel createCommitMessagePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        ToolWindowSurfaceStyle.applyDialogTopSeparator(panel, 8, 0, 0, 0);
        panel.add(createEditableSectionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_COMMIT_MESSAGE)), BorderLayout.NORTH);
        panel.setPreferredSize(new Dimension(0, 72)); // 设置固定高度

        commitMessageArea = new JTextArea(1, 0);
        commitMessageArea.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        commitMessageArea.setLineWrap(true);
        commitMessageArea.setWrapStyleWord(true);
        ToolWindowSurfaceStyle.applyTextComponentInput(commitMessageArea);
        commitMessageArea.setBorder(new EmptyBorder(5, 8, 5, 8));
        commitMessageArea.setText(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_DEFAULT_COMMIT_MESSAGE,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        JScrollPane scrollPane = new JScrollPane(commitMessageArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ToolWindowSurfaceStyle.applyDialogScrollPane(scrollPane);
        styleIdeaScrollPane(scrollPane);
        installEditableInputBorder(scrollPane, commitMessageArea);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建操作选择面板
     */
    private JPanel createActionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(panel);
        panel.setBorder(new EmptyBorder(0, 24, 8, 24));

        optionsPanel = new JPanel();
        applyIdeaSection(optionsPanel);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setVisible(false);

        panel.add(optionsPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建底部面板
     */
    private JPanel createFooterPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        ToolWindowSurfaceStyle.applyDialogFooter(panel);

        // 状态提示与操作进度共用一块区域，避免禁用按钮看起来没有原因。
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("");
        actionHintLabel = new JLabel();
        actionHintLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        actionHintLabel.setForeground(ModernColors.getTextSecondary());
        actionHintLabel.setBorder(new EmptyBorder(0, 4, 0, 4));
        footerFeedbackLayout = new CardLayout();
        footerFeedbackPanel = new JPanel(footerFeedbackLayout);
        footerFeedbackPanel.setOpaque(false);
        footerFeedbackPanel.add(actionHintLabel, "hint");
        footerFeedbackPanel.add(progressBar, "progress");
        footerFeedbackLayout.show(footerFeedbackPanel, "hint");

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);

        cancelButton = ModernButtonFactory.createButton(I18nUtil.getMessage(MessageKeys.BUTTON_CANCEL), false);
        cancelButton.addActionListener(e -> dispose());

        executeButton = createExecuteButton(operation);
        executeButton.addActionListener(new ExecuteActionListener());
        if (operation == GitOperation.COMMIT) {
            commitMessageArea.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateExecuteButtonStateByChoice();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateExecuteButtonStateByChoice();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateExecuteButtonStateByChoice();
                }
            });
        }

        buttonPanel.add(cancelButton);
        buttonPanel.add(executeButton);

        panel.add(footerFeedbackPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    static JButton createExecuteButton(GitOperation operation) {
        Color baseColor = GitOperationPresentation.getColor(operation);
        PrimaryButton button = new PrimaryButton(
                operation.getDisplayName(),
                GitOperationPresentation.getIconName(operation)
        );
        button.putClientProperty("baseColor", baseColor);
        button.putClientProperty("hoverColor", ModernColors.blendColors(baseColor, Color.WHITE, 0.18f));
        button.putClientProperty("pressColor", ModernColors.blendColors(baseColor, Color.BLACK, 0.18f));
        button.putClientProperty("colorsInitialized", false);
        button.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        button.setPreferredSize(new Dimension(100, 34));
        button.setToolTipText(operation.getDisplayName());
        return button;
    }

    private JLabel createSectionTitle(String title) {
        JLabel label = new JLabel(title);
        label.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        label.setForeground(ModernColors.getTextPrimary());
        return label;
    }

    private JPanel createEditableSectionTitle(String title) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);

        JLabel iconLabel = new JLabel(IconUtil.createThemed("icons/edit.svg", 14, 14));
        iconLabel.setBorder(new EmptyBorder(1, 0, 0, 6));

        panel.add(iconLabel);
        panel.add(createSectionTitle(title));
        return panel;
    }

    private void applyIdeaSection(JPanel panel) {
        ToolWindowSurfaceStyle.applyDialogSection(panel);
    }

    private void styleIdeaScrollPane(JScrollPane scrollPane) {
        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        if (verticalScrollBar != null) {
            verticalScrollBar.setUnitIncrement(16);
            verticalScrollBar.setPreferredSize(new Dimension(8, 0));
        }
        JScrollBar horizontalScrollBar = scrollPane.getHorizontalScrollBar();
        if (horizontalScrollBar != null) {
            horizontalScrollBar.setPreferredSize(new Dimension(0, 8));
        }
    }

    private void installEditableInputBorder(JScrollPane scrollPane, JTextArea textArea) {
        updateEditableInputBorder(scrollPane, false);
        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                updateEditableInputBorder(scrollPane, true);
            }

            @Override
            public void focusLost(FocusEvent e) {
                updateEditableInputBorder(scrollPane, false);
            }
        });
    }

    private void updateEditableInputBorder(JScrollPane scrollPane, boolean focused) {
        ToolWindowSurfaceStyle.applyDialogInputBorder(scrollPane, focused);
    }

    /**
     * 执行操作前检查
     */
    private void performPreOperationCheck() {
        checkingStatus = true;
        statusCheck = null;
        updateExecuteButtonStateByChoice();
        stepIndicator.setCurrentStep(0);
        updateStatus(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CHECKING_STATUS_AND_CONFLICT), "icons/refresh.svg", ModernColors.getPrimary());
        retryButton.setVisible(false);
        detailsArea.setText(createHtmlStart(false)
                + escapeHtml(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CHECKING_STATUS_AND_CONFLICT))
                + "</body></html>");
        fileChangesArea.setText(createHtmlStart(true)
                + escapeHtml(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_LOADING_FILE_CHANGES))
                + "</body></html>");
        optionsPanel.removeAll();
        optionsPanel.setVisible(false);
        optionsPanel.revalidate();
        optionsPanel.repaint();

        // 禁用执行按钮，防止在检查期间执行操作
        executeButton.setEnabled(false);

        // 使用 SwingWorker 在后台线程执行 Git 检查，避免阻塞 UI
        SwingWorker<GitStatusCheck, Void> worker = new SwingWorker<>() {
            @Override
            protected GitStatusCheck doInBackground() {
                try {
                    return workspaceService.checkGitStatus(workspace, operation);
                } catch (Exception e) {
                    log.error("Failed to check git status", e);
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    statusCheck = get();
                    if (statusCheck != null) {
                        displayStatusCheck(statusCheck);
                        displayFileChangesStatus();
                        stepIndicator.setCurrentStep(1);
                        if (isRemoteStatusUnavailable(statusCheck)) {
                            updateStatus(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOTE_STATUS_UNAVAILABLE),
                                    "icons/warning.svg", ModernColors.getError());
                            statusMessage.setToolTipText(statusCheck.warnings.isEmpty()
                                    ? null : statusCheck.warnings.get(0));
                            retryButton.setVisible(true);
                        } else if (isPullUpToDate(statusCheck)) {
                            updateStatus(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_NO_REMOTE_CHANGES), "icons/check.svg", ModernColors.getSuccess());
                        } else {
                            updateStatus(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_STATUS_CHECK_DONE), "icons/check.svg", ModernColors.getSuccess());
                        }
                    } else {
                        updateStatus(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_STATUS_CHECK_FAILED,
                                I18nUtil.getMessage(MessageKeys.GIT_DIALOG_UNKNOWN)),
                                "icons/warning.svg", ModernColors.getError());
                        displayFileChangesStatus();
                        retryButton.setVisible(true);
                    }
                } catch (Exception e) {
                    log.error("Failed to perform pre-operation check", e);
                    updateStatus(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_STATUS_CHECK_FAILED,
                            e.getMessage() == null ? I18nUtil.getMessage(MessageKeys.GIT_DIALOG_UNKNOWN) : e.getMessage()),
                            "icons/warning.svg", ModernColors.getError());
                    displayFileChangesStatus();
                    retryButton.setVisible(true);
                } finally {
                    checkingStatus = false;
                    updateExecuteButtonStateByChoice();
                    retryButton.getParent().revalidate();
                    retryButton.getParent().repaint();
                }
            }
        };
        worker.execute();
    }

    /**
     * 更新状态显示
     */
    private void updateStatus(String message, String iconPath, Color color) {
        // Create colored icon with theme-adapted color for better visibility
        statusIcon.setIcon(IconUtil.createColored(iconPath, IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL, color));
        statusMessage.setText(message);
        statusMessage.setForeground(color);
        statusMessage.setToolTipText(null);
    }

    /**
     * 显示状态检查结果
     */
    private void displayStatusCheck(GitStatusCheck check) {
        // 显示详细的状态检查信息
        displayStatusDetails(check);
        // 显示操作选择（如果需要）
        updateActionChoices(check);
        updateExecuteButtonStateByChoice();
    }

    private boolean isRemoteStatusUnavailable(GitStatusCheck check) {
        return check != null && (check.hasAuthenticationIssue
                || (check.hasRemoteRepository && !check.canConnectToRemote));
    }

    private boolean isPullUpToDate(GitStatusCheck check) {
        return operation == GitOperation.PULL
                && check.hasRemoteRepository
                && check.hasUpstreamBranch
                && check.canConnectToRemote
                && !check.isRemoteRepositoryEmpty
                && !check.hasRemoteCommits;
    }

    /**
     * 显示详细的状态检查信息
     */
    private void displayStatusDetails(GitStatusCheck check) {
        StringBuilder html = new StringBuilder();
        html.append(createHtmlStart(false));

        openHtmlSection(html);
        appendHtmlSectionTitle(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_STATUS_SUMMARY));
        appendBooleanRow(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_HAS_UNCOMMITTED_CHANGES), check.hasUncommittedChanges);
        appendBooleanRow(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_HAS_LOCAL_COMMITS), check.hasLocalCommits);
        appendBooleanRow(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_HAS_REMOTE_COMMITS), check.hasRemoteCommits);

        if (check.localCommitsAhead > 0) {
            String msg = I18nUtil.getMessage(MessageKeys.GIT_DIALOG_LOCAL_AHEAD, check.localCommitsAhead);
            appendMutedRow(html, msg);
        }
        if (check.remoteCommitsBehind > 0) {
            String msg = I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOTE_AHEAD, check.remoteCommitsBehind);
            appendMutedRow(html, msg);
        }
        closeHtmlSection(html);

        if (!check.warnings.isEmpty()) {
            openHtmlSection(html);
            appendHtmlSectionTitle(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_WARNINGS));
            for (String warning : check.warnings) {
                appendColoredRow(html, warning, getHtmlWarningColor());
            }
            closeHtmlSection(html);
        }

        if (!check.suggestions.isEmpty()) {
            openHtmlSection(html);
            appendHtmlSectionTitle(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_SUGGESTIONS));
            for (String suggestion : check.suggestions) {
                appendMutedRow(html, suggestion);
            }
            closeHtmlSection(html);
        }

        html.append("</body></html>");
        detailsArea.setText(html.toString());
        detailsArea.setCaretPosition(0);
    }

    /**
     * 根据状态检查结果更新操作选择
     */
    private void updateActionChoices(GitStatusCheck check) {
        optionsPanel.removeAll();
        optionGroup = new ButtonGroup();

        if (operation != GitOperation.COMMIT && isRemoteStatusUnavailable(check)) {
            optionsPanel.setVisible(false);
            optionsPanel.revalidate();
            optionsPanel.repaint();
            return;
        }

        boolean showOptions = false;

        if (operation == GitOperation.COMMIT && check.canCommit) {
            showOptions = true;
            addOptionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_COMMIT_TITLE));
            addOption(OPTION_COMMIT_FIRST,
                    I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_COMMIT_FIRST),
                    I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_COMMIT_FIRST_DESC), true);
            if (!check.hasActualConflicts && !isRemoteStatusUnavailable(check)) {
                addOption(OPTION_COMMIT_AND_PUSH,
                        I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_COMMIT_AND_PUSH),
                        I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_COMMIT_AND_PUSH_DESC), false);
            }
        } else if (operation == GitOperation.PULL) {
            if (check.hasActualConflicts) {
                showOptions = true;
                addOptionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_PULL_CONFLICT_TITLE));
                addOption(OPTION_CANCEL,
                        I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_CANCEL),
                        I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_CANCEL_DESC), true);
                addOption(OPTION_FORCE,
                        I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PULL),
                        I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PULL_DESC), false, ModernColors.getError());
            } else if (check.hasUncommittedChanges) {
                showOptions = true;
                // 如果可以自动合并，优先推荐提交后拉取
                if (check.canAutoMerge) {
                    addOptionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_PULL_UNCOMMITTED_AUTO_MERGE_TITLE));
                    addOption(OPTION_COMMIT_FIRST, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_COMMIT_FIRST_PULL), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_COMMIT_FIRST_PULL_AUTO_MERGE_DESC), false);
                    addOption(OPTION_FORCE, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PULL_DISCARD), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PULL_DISCARD_WARNING_DESC), false, ModernColors.getError());
                } else {
                    addOptionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_PULL_UNCOMMITTED_CHOOSE_TITLE));
                    addOption(OPTION_COMMIT_FIRST, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_COMMIT_FIRST_PULL), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_COMMIT_FIRST_PULL_KEEP_DESC), false);
                    addOption(OPTION_STASH, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_STASH_PULL), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_STASH_PULL_DESC), false);
                    addOption(OPTION_FORCE, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PULL_DISCARD), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PULL_LOSE_DESC), false, ModernColors.getError());
                }
            }
        } else if (operation == GitOperation.PUSH) {
            if (!check.hasLocalCommits || check.localCommitsAhead <= 0) {
                optionsPanel.setVisible(false);
                optionsPanel.revalidate();
                optionsPanel.repaint();
                return;
            }
            // 优先处理实际冲突
            if (check.hasActualConflicts) {
                showOptions = true;
                addOptionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_PUSH_CONFLICT_TITLE));
                addOption(OPTION_CANCEL, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_CANCEL_EXTERNAL_TOOL), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_CANCEL_EXTERNAL_TOOL_DESC), true);
                addOption(OPTION_FORCE, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PUSH_OVERWRITE), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PUSH_OVERWRITE_COMMITS_DESC, check.remoteCommitsBehind), false, ModernColors.getError());
            } else if (check.hasRemoteCommits) {
                // 远程有新提交
                showOptions = true;
                if (check.canAutoMerge && check.localCommitsAhead > 0) {
                    addOptionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_PUSH_REMOTE_AUTO_MERGE_TITLE));
                    addOption(OPTION_PULL_FIRST, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_PULL_FIRST_PUSH), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_PULL_FIRST_PUSH_DESC), true);
                    addOption(OPTION_FORCE, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PUSH_OVERWRITE), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PUSH_OVERWRITE_REMOTE_DESC), false, ModernColors.getError());
                } else {
                    addOptionTitle(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_PUSH_REMOTE_CHOOSE_TITLE));
                    addOption(OPTION_FORCE, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PUSH_OVERWRITE), I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPTION_FORCE_PUSH_OVERWRITE_REMOTE_DESC), false, ModernColors.getError());
                }
            }
        }

        optionsPanel.setVisible(showOptions);
        optionsPanel.revalidate();
        optionsPanel.repaint();
    }

    /**
     * 添加选项标题
     */
    private void addOptionTitle(String title) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        titleLabel.setForeground(ModernColors.getTextPrimary());
        titleLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionsPanel.add(titleLabel);
    }

    /**
     * 添加单个选项
     */
    private void addOption(String value, String text, String description, boolean selected) {
        addOption(value, text, description, selected, null);
    }

    private void addOption(String value, String text, String description, boolean selected, Color textColor) {
        JPanel optionPanel = new JPanel(new BorderLayout(0, 2));
        optionPanel.setBorder(new EmptyBorder(2, 0, 4, 0));
        optionPanel.setOpaque(false);
        optionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JRadioButton radio = new JRadioButton(text, selected);
        radio.setActionCommand(value);
        radio.setOpaque(false);
        radio.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        radio.setForeground(ModernColors.getTextPrimary());
        if (textColor != null) {
            radio.setForeground(textColor);
        }
        optionGroup.add(radio);

        // 监听选项变化，动态更新按钮状态
        radio.addActionListener(e -> updateExecuteButtonStateByChoice());

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.ITALIC, -3));
        // Use theme-adapted secondary text color
        descLabel.setForeground(ModernColors.getTextSecondary());
        descLabel.setBorder(new EmptyBorder(0, 22, 0, 0));

        optionPanel.add(radio, BorderLayout.NORTH);
        optionPanel.add(descLabel, BorderLayout.CENTER);

        optionsPanel.add(optionPanel);
    }

    // 根据当前选项动态判断按钮可用性
    private void updateExecuteButtonStateByChoice() {
        String choice = getUserChoice();
        boolean canExecute = false;
        switch (operation) {
            case PULL -> {
                // 只要不是取消，选了强制拉取/暂存/先提交都允许
                if (OPTION_FORCE.equals(choice) || OPTION_STASH.equals(choice) || OPTION_COMMIT_FIRST.equals(choice)) {
                    canExecute = true;
                } else if (OPTION_CANCEL.equals(choice)) {
                    canExecute = false;
                } else if (statusCheck != null && statusCheck.hasUncommittedChanges) {
                    // 有未提交变更时，要求用户明确选择提交、暂存或丢弃策略。
                    canExecute = false;
                } else {
                    // 默认按 canPull
                    canExecute = statusCheck != null && (statusCheck.canPull || statusCheck.isRemoteRepositoryEmpty);
                }
            }
            case PUSH -> {
                if (OPTION_FORCE.equals(choice) || OPTION_PULL_FIRST.equals(choice)) {
                    canExecute = true;
                } else if (OPTION_CANCEL.equals(choice)) {
                    canExecute = false;
                } else if (statusCheck != null && statusCheck.hasRemoteCommits) {
                    // 远程有新提交时，禁止在未明确选择策略的情况下直接推送。
                    canExecute = false;
                } else {
                    canExecute = statusCheck != null && (statusCheck.canPush || statusCheck.isFirstPush);
                }
            }
            case COMMIT -> {
                // 提交和提交并推送都允许
                if (OPTION_COMMIT_FIRST.equals(choice) || OPTION_COMMIT_AND_PUSH.equals(choice)) {
                    canExecute = true;
                } else {
                    canExecute = statusCheck != null && statusCheck.canCommit;
                }
            }
        }
        if (checkingStatus || operationRunning || statusCheck == null
                || (operation != GitOperation.COMMIT && isRemoteStatusUnavailable(statusCheck))) {
            canExecute = false;
        }
        if (operation == GitOperation.PULL && statusCheck != null
                && (!statusCheck.hasUpstreamBranch
                || (!statusCheck.canPull && !statusCheck.isRemoteRepositoryEmpty))) {
            canExecute = false;
        }
        if (operation == GitOperation.PUSH && statusCheck != null
                && (!statusCheck.hasLocalCommits || statusCheck.localCommitsAhead <= 0
                || statusCheck.hasUncommittedChanges)) {
            canExecute = false;
        }
        executeButton.setEnabled(canExecute);
        String reason = getDisabledActionReason(choice);
        actionHintLabel.setText(reason);
        actionHintLabel.setToolTipText(reason.isBlank() ? null : reason);
        actionHintLabel.setVisible(!reason.isBlank());
        executeButton.setToolTipText(reason.isBlank() ? operation.getDisplayName() : reason);
        footerFeedbackPanel.revalidate();
        footerFeedbackPanel.repaint();
    }

    private String getDisabledActionReason(String choice) {
        if (checkingStatus) {
            return I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_CHECKING);
        }
        if (operationRunning) {
            return "";
        }
        if (statusCheck == null) {
            return I18nUtil.getMessage(MessageKeys.GIT_DIALOG_STATUS_CHECK_FAILED,
                    I18nUtil.getMessage(MessageKeys.GIT_DIALOG_UNKNOWN));
        }
        if (operation != GitOperation.COMMIT && isRemoteStatusUnavailable(statusCheck)) {
            return I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_REMOTE_UNAVAILABLE);
        }

        return switch (operation) {
            case COMMIT -> {
                if (!statusCheck.conflicting.isEmpty()) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_HAS_CONFLICTS);
                }
                if (!statusCheck.canCommit) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_NO_CHANGES);
                }
                if (commitMessageArea.getText().trim().isEmpty()) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_COMMIT_MESSAGE);
                }
                yield "";
            }
            case PULL -> {
                if (!statusCheck.hasRemoteRepository) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_NO_REMOTE);
                }
                if (!statusCheck.hasUpstreamBranch) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_NO_UPSTREAM);
                }
                if (!statusCheck.canPull && !statusCheck.isRemoteRepositoryEmpty) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_NO_REMOTE_CHANGES);
                }
                if (OPTION_CANCEL.equals(choice) || (statusCheck.hasUncommittedChanges && "default".equals(choice))) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_CHOOSE_STRATEGY);
                }
                yield "";
            }
            case PUSH -> {
                if (!statusCheck.hasRemoteRepository) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_NO_REMOTE);
                }
                if (statusCheck.hasUncommittedChanges) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_UNCOMMITTED_CHANGES);
                }
                if (!statusCheck.hasLocalCommits || statusCheck.localCommitsAhead <= 0) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_NO_LOCAL_COMMITS);
                }
                if (statusCheck.hasRemoteCommits && "default".equals(choice)) {
                    yield I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ACTION_REASON_CHOOSE_STRATEGY);
                }
                yield "";
            }
        };
    }

    /**
     * 展示文件变更信息，并在有冲突的文件下展示冲突详情
     */
    private void displayFileChangesStatus() {
        if (statusCheck == null) {
            StringBuilder emptyHtml = new StringBuilder(createHtmlStart(true));
            appendMutedRow(emptyHtml, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_FILE_CHANGES_NOT_AVAILABLE));
            emptyHtml.append("</body></html>");
            fileChangesArea.setText(emptyHtml.toString());
            return;
        }

        StringBuilder html = new StringBuilder();
        html.append(createHtmlStart(true));

        // 本地变更
        openHtmlSection(html);
        appendHtmlSectionTitle(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_LOCAL_CHANGES_TITLE));
        boolean hasLocalChanges = false;
        hasLocalChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_ADDED_FILES), statusCheck.added, "+", getHtmlSuccessColor());
        hasLocalChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CHANGED_FILES), statusCheck.changed, "~", getHtmlWarningColor());
        hasLocalChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_MODIFIED_FILES), statusCheck.modified, "*", getHtmlInfoColor());
        hasLocalChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOVED_FILES), statusCheck.removed, "-", getHtmlErrorColor());
        hasLocalChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_MISSING_FILES), statusCheck.missing, "!", getHtmlErrorColor());
        hasLocalChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_UNTRACKED_FILES), statusCheck.untracked, "?", getHtmlSecondaryColor());
        hasLocalChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CONFLICTING_FILES), statusCheck.conflicting, "#", getHtmlErrorColor());
        if (!hasLocalChanges) {
            appendMutedRow(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_NO_LOCAL_CHANGES));
        }
        closeHtmlSection(html);

        // 远程变更分组展示
        openHtmlSection(html);
        appendHtmlSectionTitle(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOTE_CHANGES_TITLE));
        boolean hasRemoteChanges = false;
        hasRemoteChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOTE_ADDED_FILES), statusCheck.remoteAdded, "+", getHtmlSuccessColor());
        hasRemoteChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOTE_MODIFIED_FILES), statusCheck.remoteModified, "~", getHtmlWarningColor());
        hasRemoteChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOTE_REMOVED_FILES), statusCheck.remoteRemoved, "-", getHtmlErrorColor());
        hasRemoteChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOTE_RENAMED_FILES), statusCheck.remoteRenamed, "R", getHtmlInfoColor());
        hasRemoteChanges |= appendFileCategory(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_REMOTE_COPIED_FILES), statusCheck.remoteCopied, "C", getHtmlInfoColor());
        if (!hasRemoteChanges) {
            appendMutedRow(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_NO_REMOTE_CHANGES));
        }
        closeHtmlSection(html);

        // 冲突文件详情展示
        openHtmlSection(html);
        appendHtmlSectionTitle(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CONFLICT_DETAILS_TITLE));

        if (statusCheck.conflictingFiles != null && !statusCheck.conflictingFiles.isEmpty()) {
            for (String file : statusCheck.conflictingFiles) {
                appendFileRow(html, "#", labelWithValue(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CONFLICT_FILE), file), getHtmlErrorColor());
                List<com.laker.postman.model.ConflictBlock> blocks = statusCheck.conflictDetails == null
                        ? null
                        : statusCheck.conflictDetails.get(file);
                if (blocks != null && !blocks.isEmpty()) {
                    for (int i = 0; i < blocks.size(); i++) {
                        com.laker.postman.model.ConflictBlock block = blocks.get(i);
                        appendMutedRow(html, labelWithValue(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CONFLICT_BLOCK),
                                String.valueOf(i + 1)) +
                                I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CONFLICT_BLOCK_LINES) +
                                block.getBegin() + "-" + block.getEnd() + "]");
                        appendMutedRow(html, labelWithValue(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CONFLICT_BASE),
                                String.join(" | ", block.getBaseLines())));
                        appendColoredRow(html, labelWithValue(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CONFLICT_LOCAL),
                                String.join(" | ", block.getLocalLines())), getHtmlWarningColor());
                        appendColoredRow(html, labelWithValue(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_CONFLICT_REMOTE),
                                String.join(" | ", block.getRemoteLines())), getHtmlInfoColor());
                    }
                } else {
                    appendMutedRow(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_NO_CONFLICT_DETAILS));
                }
            }
        } else {
            appendMutedRow(html, I18nUtil.getMessage(MessageKeys.GIT_DIALOG_NO_FILE_CONFLICTS));
        }
        closeHtmlSection(html);

        html.append("</body></html>");
        fileChangesArea.setText(html.toString());
        fileChangesArea.setCaretPosition(0);
    }

    private String createHtmlStart(boolean monospaced) {
        Font font = monospaced
                ? FontsUtil.getMonospacedFontWithOffset(Font.PLAIN, -2)
                : FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2);
        String family = font.getFamily().replace("'", "");
        return "<html><body style='font-family: " + family + "; font-size: " + font.getSize() + "pt; color: "
                + toHtmlColor(ModernColors.getTextPrimary()) + "; margin: 0; padding: 0;'>";
    }

    private void openHtmlSection(StringBuilder html) {
        html.append("<div style='margin: 0 0 12px 0;'>");
    }

    private void closeHtmlSection(StringBuilder html) {
        html.append("</div>");
    }

    private void appendHtmlSectionTitle(StringBuilder html, String title) {
        html.append("<div style='font-weight: bold; color: ")
                .append(toHtmlColor(ModernColors.getTextPrimary()))
                .append("; margin: 0 0 6px 0;'>")
                .append(escapeHtml(cleanMessage(title)))
                .append("</div>");
    }

    private void appendBooleanRow(StringBuilder html, String label, boolean value) {
        String valueText = value
                ? I18nUtil.getMessage(MessageKeys.GIT_DIALOG_YES)
                : I18nUtil.getMessage(MessageKeys.GIT_DIALOG_NO);
        String valueColor = value ? getHtmlSuccessColor() : getHtmlSecondaryColor();
        html.append("<div style='margin: 3px 0;'>")
                .append("<span style='color: ").append(getHtmlSecondaryColor()).append(";'>")
                .append(escapeHtml(cleanMessage(label)))
                .append("</span> ")
                .append("<span style='font-weight: bold; color: ").append(valueColor).append(";'>")
                .append(escapeHtml(cleanMessage(valueText)))
                .append("</span>")
                .append("</div>");
    }

    private void appendMutedRow(StringBuilder html, String text) {
        html.append("<div style='color: ")
                .append(getHtmlSecondaryColor())
                .append("; margin: 3px 0;'>")
                .append(escapeHtml(cleanMessage(text)))
                .append("</div>");
    }

    private void appendColoredRow(StringBuilder html, String text, String color) {
        html.append("<div style='color: ")
                .append(color)
                .append("; margin: 3px 0;'>")
                .append(escapeHtml(cleanMessage(text)))
                .append("</div>");
    }

    private boolean appendFileCategory(StringBuilder html,
                                       String label,
                                       List<String> files,
                                       String marker,
                                       String markerColor) {
        if (files == null || files.isEmpty()) {
            return false;
        }

        appendMutedRow(html, cleanMessage(label) + " " + files.size());
        for (String file : files) {
            appendFileRow(html, marker, file, markerColor);
        }
        return true;
    }

    private void appendFileRow(StringBuilder html, String marker, String file, String markerColor) {
        html.append("<div style='margin: 2px 0 2px 14px;'>")
                .append("<span style='font-weight: bold; color: ")
                .append(markerColor)
                .append(";'>")
                .append(escapeHtml(marker))
                .append("</span> ")
                .append(escapeHtml(file == null ? "" : file))
                .append("</div>");
    }

    private String labelWithValue(String label, String value) {
        String cleanLabel = cleanMessage(label);
        String cleanValue = cleanMessage(value);
        if (cleanLabel.isEmpty()) {
            return cleanValue;
        }
        if (cleanValue.isEmpty()) {
            return cleanLabel;
        }
        return cleanLabel + " " + cleanValue;
    }

    private String cleanMessage(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("•", "")
                .replace("📊", "")
                .replace("📝", "")
                .replace("📦", "")
                .replace("📁", "")
                .replace("💡", "")
                .replace("❗", "")
                .replace("✅", "")
                .replace("❌", "")
                .trim();
    }

    /**
     * HTML转义帮助方法，防止HTML注入
     */
    private String escapeHtml(String text) {
        return HttpHtmlRenderer.escapeHtml(text);
    }

    private class ExecuteActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (!validateOperation()) {
                return;
            }

            if (operation == GitOperation.COMMIT) {
                commitMessage = commitMessageArea.getText().trim();
            }

            String userChoice = getUserChoice();

            stepIndicator.setCurrentStep(2);
            showProgress();

            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    executeGitOperationWithChoice(userChoice);
                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String message : chunks) {
                        progressBar.setString(message);
                    }
                }

                @Override
                protected void done() {
                    hideProgress();
                    try {
                        get();
                        confirmed = true;
                        updateStatus(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPERATION_COMPLETED), "icons/check.svg", ModernColors.getSuccess());

                        NotificationCenter.showSuccess(
                                I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPERATION_SUCCESS_MESSAGE, operation.getDisplayName())
                        );

                        SwingUtilities.invokeLater(GitOperationDialog.this::dispose);

                    } catch (Exception ex) {
                        log.error("Git operation failed", ex);
                        updateStatus(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPERATION_FAILED, ex.getMessage()), "icons/warning.svg", ModernColors.getError());

                        String errorMessage = ex.getMessage();
                        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                            errorMessage = ex.getCause().getMessage();
                        }

                        NotificationCenter.showError(
                                I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPERATION_FAILED_MESSAGE, errorMessage)
                        );
                        performPreOperationCheck();
                    }
                }

                private void executeGitOperationWithChoice(String choice) throws Exception {
                    publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPERATION_EXECUTING_PROGRESS, operation.getDisplayName()));

                    switch (operation) {
                        case COMMIT -> {
                            if (OPTION_COMMIT_AND_PUSH.equals(choice)) {
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_COMMITTING));
                                var commitResult = workspaceService.commitChanges(workspace.getId(), commitMessage);
                                if (statusCheck.remoteCommitsBehind > 0) {
                                    publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_REMOTE_COMMITS_PULL_FIRST));
                                    var pullResult = workspaceService.pullUpdates(workspace.getId());
                                }
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_COMMIT_DONE_PUSHING));
                                var pushResult = workspaceService.pushChanges(workspace.getId());
                            } else {
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_COMMITTING));
                                var result = workspaceService.commitChanges(workspace.getId(), commitMessage);
                            }
                        }
                        case PUSH -> {
                            if (OPTION_FORCE.equals(choice)) {
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_FORCE_PUSHING));
                                var result = workspaceService.forcePushChanges(workspace.getId());
                            } else if (OPTION_PULL_FIRST.equals(choice)) {
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_PULL_FIRST));
                                var pullResult = workspaceService.pullUpdates(workspace.getId());

                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_THEN_PUSH));
                                var pushResult = workspaceService.pushChanges(workspace.getId());
                            } else {
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_PUSHING));
                                var result = workspaceService.pushChanges(workspace.getId());
                            }
                        }
                        case PULL -> {
                            if (OPTION_COMMIT_FIRST.equals(choice)) {
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_COMMIT_LOCAL_FIRST));
                                String autoCommitMsg = "Auto commit before pull - " +
                                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                var commitResult = workspaceService.commitChanges(workspace.getId(), autoCommitMsg);

                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_THEN_PULL));
                                var pullResult = workspaceService.pullUpdates(workspace.getId());
                            } else if (OPTION_STASH.equals(choice)) {
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_STASHING));
                                var stashResult = workspaceService.stashChanges(workspace.getId());

                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_PULLING_REMOTE));
                                var pullResult = workspaceService.pullUpdates(workspace.getId());

                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_RESTORING_STASH));
                                var popResult = workspaceService.popStashChanges(workspace.getId());
                            } else if (OPTION_FORCE.equals(choice)) {
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_FORCE_PULL_DISCARD));
                                var result = workspaceService.forcePullUpdates(workspace.getId());
                            } else if (OPTION_CANCEL.equals(choice)) {
                                throw new RuntimeException(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_USER_CANCELLED));
                            } else {
                                publish(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_PROGRESS_PULLING_FROM_REMOTE));
                                var result = workspaceService.pullUpdates(workspace.getId());
                            }
                        }
                    }
                }
            };

            worker.execute();
        }


        private boolean validateOperation() {
            if (operation == GitOperation.COMMIT) {
                String message = commitMessageArea.getText().trim();
                if (message.isEmpty()) {
                    NotificationCenter.showWarning(
                            I18nUtil.getMessage(MessageKeys.GIT_DIALOG_VALIDATION_COMMIT_MESSAGE_EMPTY)
                    );
                    return false;
                }
            }

            String choice = getUserChoice();
            if (OPTION_CANCEL.equals(choice)) {
                dispose();
                return false;
            }

            return true;
        }

        private void showProgress() {
            operationRunning = true;
            cancelButton.setEnabled(false);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            footerFeedbackLayout.show(footerFeedbackPanel, "progress");
            progressBar.setIndeterminate(true);
            progressBar.setString(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_OPERATION_EXECUTING));
            executeButton.setEnabled(false);
        }

        private void hideProgress() {
            operationRunning = false;
            cancelButton.setEnabled(true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            footerFeedbackLayout.show(footerFeedbackPanel, "hint");
            progressBar.setIndeterminate(false);
            updateExecuteButtonStateByChoice();
        }
    }

    /**
     * 获取用户当前选择的操作选项
     */
    private String getUserChoice() {
        if (optionGroup == null) {
            return "default";
        }
        ButtonModel selection = optionGroup.getSelection();
        if (selection != null) {
            return selection.getActionCommand();
        }

        return "default";
    }
}
