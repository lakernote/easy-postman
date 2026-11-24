package com.laker.postman.panel.topmenu.help;

import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.platform.update.changelog.ChangelogService;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;

/**
 * 版本更新日志对话框
 *
 * <p>重构后的版本：使用统一的更新源架构，消除代码重复</p>
 */
@Slf4j
public class ChangelogDialog extends JDialog {

    private final JTextArea contentArea;
    private final JButton refreshButton;
    private final JLabel statusLabel;
    private final ChangelogService changelogService;

    public ChangelogDialog(Frame parent) {
        super(parent, I18nUtil.getMessage(MessageKeys.CHANGELOG_TITLE), true);

        this.changelogService = new ChangelogService(SettingManager::getUpdateSourcePreference);

        setLayout(new BorderLayout(10, 10));
        ToolWindowSurfaceStyle.applyDialogWindowChrome(this);
        ToolWindowSurfaceStyle.applyDialogSurface((JPanel) getContentPane());
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        // 顶部面板：当前版本信息
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);

        // 中间面板：更新日志内容
        contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setText(I18nUtil.getMessage(MessageKeys.CHANGELOG_LOADING));
        ToolWindowSurfaceStyle.applyTextComponentDialogSurface(contentArea);

        JScrollPane scrollPane = new JScrollPane(contentArea);
        ToolWindowSurfaceStyle.applyDialogScrollPane(scrollPane);
        scrollPane.setPreferredSize(new Dimension(700, 500));
        add(scrollPane, BorderLayout.CENTER);

        // 底部面板：按钮
        JPanel bottomPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogFooter(bottomPanel);

        // 状态标签
        statusLabel = new JLabel(" ");
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        statusLabel.setForeground(ModernColors.getTextSecondary());
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        ToolWindowSurfaceStyle.applyDialogSurface(buttonPanel);

        refreshButton = ModernButtonFactory.createButton(I18nUtil.getMessage(MessageKeys.CHANGELOG_REFRESH), false);
        refreshButton.addActionListener(e -> loadChangelog());

        JButton githubButton = ModernButtonFactory.createButton(I18nUtil.getMessage(MessageKeys.CHANGELOG_VIEW_ON_GITHUB), false);
        githubButton.setToolTipText(I18nUtil.getMessage(MessageKeys.CHANGELOG_VIEW_ON_GITHUB_TOOLTIP));
        githubButton.addActionListener(e -> openGitHubWeb());

        JButton giteeButton = ModernButtonFactory.createButton(I18nUtil.getMessage(MessageKeys.CHANGELOG_VIEW_ON_GITEE), false);
        giteeButton.setToolTipText(I18nUtil.getMessage(MessageKeys.CHANGELOG_VIEW_ON_GITEE_TOOLTIP));
        giteeButton.addActionListener(e -> openGiteeWeb());

        JButton closeButton = ModernButtonFactory.createButton(I18nUtil.getMessage(MessageKeys.CHANGELOG_CLOSE), true);
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(refreshButton);
        buttonPanel.add(githubButton);
        buttonPanel.add(giteeButton);
        buttonPanel.add(closeButton);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // 设置对话框属性
        setSize(750, 650);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // 异步加载更新日志
        loadChangelog();
    }

    /**
     * 创建顶部面板
     */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(panel);
        panel.setBorder(new EmptyBorder(0, 0, 10, 0));

        String currentVersion = SystemUtil.getCurrentVersion();
        JLabel versionLabel = new JLabel(I18nUtil.getMessage(MessageKeys.CHANGELOG_CURRENT_VERSION, currentVersion));
        versionLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, +1));

        panel.add(versionLabel, BorderLayout.WEST);

        return panel;
    }

    /**
     * 加载更新日志
     */
    private void loadChangelog() {
        contentArea.setText(I18nUtil.getMessage(MessageKeys.CHANGELOG_LOADING));
        refreshButton.setEnabled(false);
        statusLabel.setText(I18nUtil.getMessage(MessageKeys.CHANGELOG_LOADING));

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return changelogService.getChangelog();
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    if (result != null) {
                        contentArea.setText(result);
                        contentArea.setCaretPosition(0); // 滚动到顶部
                        statusLabel.setText(" ");
                    } else {
                        String errorMsg = I18nUtil.getMessage(MessageKeys.CHANGELOG_LOAD_FAILED,
                                I18nUtil.getMessage(MessageKeys.ERROR_NETWORK_TIMEOUT));
                        contentArea.setText(errorMsg);
                        statusLabel.setText(errorMsg);
                    }
                } catch (Exception e) {
                    log.error("Failed to load changelog", e);
                    String errorMsg = I18nUtil.getMessage(MessageKeys.CHANGELOG_LOAD_FAILED, e.getMessage());
                    contentArea.setText(errorMsg);
                    statusLabel.setText(errorMsg);
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /**
     * 打开 GitHub 网页版
     */
    private void openGitHubWeb() {
        // 打开固定站点，无需测速（此方法在 EDT 上执行）
        openUrl(changelogService.getSourceSelector().getSource("GitHub").getWebUrl());
    }

    /**
     * 打开 Gitee 网页版
     */
    private void openGiteeWeb() {
        openUrl(changelogService.getSourceSelector().getSource("Gitee").getWebUrl());
    }

    /**
     * 打开 URL
     */
    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            log.error("Failed to open URL: {}", url, e);
            JOptionPane.showMessageDialog(this,
                    I18nUtil.getMessage(MessageKeys.ERROR_OPEN_LINK_FAILED, e.getMessage()),
                    I18nUtil.getMessage(MessageKeys.GENERAL_ERROR),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 显示更新日志对话框
     */
    public static void showDialog(Frame parent) {
        SwingUtilities.invokeLater(() -> {
            ChangelogDialog dialog = new ChangelogDialog(parent);
            dialog.setVisible(true);
        });
    }
}
