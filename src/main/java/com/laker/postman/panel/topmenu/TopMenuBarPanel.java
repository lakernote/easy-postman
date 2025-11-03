package com.laker.postman.panel.topmenu;

import com.formdev.flatlaf.extras.FlatDesktop;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.SingletonBasePanel;
import com.laker.postman.common.SingletonFactory;
import com.laker.postman.common.component.combobox.EnvironmentComboBox;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.model.Workspace;
import com.laker.postman.panel.topmenu.setting.*;
import com.laker.postman.service.ExitService;
import com.laker.postman.service.UpdateService;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.SystemUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;

import static com.laker.postman.util.SystemUtil.getCurrentVersion;

@Slf4j
public class TopMenuBarPanel extends SingletonBasePanel {
    @Getter
    private EnvironmentComboBox environmentComboBox;

    private JLabel workspaceLabel;
    private JMenuBar menuBar;

    @Override
    protected void initUI() {
        setLayout(new BorderLayout());
        setBorder(createPanelBorder());
        setOpaque(true);
        initComponents();
    }

    private Border createPanelBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, Color.lightGray),
                BorderFactory.createEmptyBorder(1, 4, 1, 4)
        );
    }

    @Override
    protected void registerListeners() {
        FlatDesktop.setAboutHandler(this::aboutActionPerformed);
        FlatDesktop.setQuitHandler((e) -> ExitService.exit());
    }

    private void initComponents() {
        menuBar = new JMenuBar();
        menuBar.setBorder(BorderFactory.createEmptyBorder());
        addFileMenu();
        addLanguageMenu();
        addSettingMenu();
        addHelpMenu();
        addAboutMenu();
        add(menuBar, BorderLayout.WEST);
        addEnvironmentComboBox();
    }

    private void addFileMenu() {
        JMenu fileMenu = new JMenu(I18nUtil.getMessage(MessageKeys.MENU_FILE));
        JMenuItem logMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.MENU_FILE_LOG));
        logMenuItem.addActionListener(e -> openLogDirectory());
        JMenuItem exitMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.MENU_FILE_EXIT));
        // 快捷键绑定为 Command+Q（macOS）或 Ctrl+Q（Windows/Linux
        exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        exitMenuItem.addActionListener(e -> ExitService.exit());
        fileMenu.add(logMenuItem);
        fileMenu.add(exitMenuItem);
        menuBar.add(fileMenu);
    }

    private void openLogDirectory() {
        try {
            Desktop.getDesktop().open(SystemUtil.LOG_DIR.toFile());
        } catch (IOException ex) {
            log.error("Failed to open log directory", ex);
            JOptionPane.showMessageDialog(null,
                    I18nUtil.getMessage(MessageKeys.ERROR_OPEN_LOG_MESSAGE),
                    I18nUtil.getMessage(MessageKeys.GENERAL_ERROR), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addLanguageMenu() {
        JMenu languageMenu = new JMenu(I18nUtil.getMessage(MessageKeys.MENU_LANGUAGE));
        ButtonGroup languageGroup = new ButtonGroup();

        JRadioButtonMenuItem englishItem = new JRadioButtonMenuItem("English");
        JRadioButtonMenuItem chineseItem = new JRadioButtonMenuItem("中文");

        languageGroup.add(englishItem);
        languageGroup.add(chineseItem);

        // 设置当前选中的语言
        if (I18nUtil.isChinese()) {
            chineseItem.setSelected(true);
        } else {
            englishItem.setSelected(true);
        }

        englishItem.addActionListener(e -> switchLanguage("en"));
        chineseItem.addActionListener(e -> switchLanguage("zh"));

        languageMenu.add(englishItem);
        languageMenu.add(chineseItem);
        menuBar.add(languageMenu);
    }

    private void switchLanguage(String languageCode) {
        I18nUtil.setLocale(languageCode);
        // 重新初始化菜单栏以应用新语言
        menuBar.removeAll();
        initComponents();
        // 重新绘制所有窗口
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
        JOptionPane.showMessageDialog(SingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.LANGUAGE_CHANGED),
                I18nUtil.getMessage(MessageKeys.GENERAL_INFO),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void addSettingMenu() {
        JMenu settingMenu = new JMenu(I18nUtil.getMessage(MessageKeys.MENU_SETTINGS));

        // 请求设置
        JMenuItem requestSettingMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_TITLE));
        requestSettingMenuItem.addActionListener(e -> showRequestSettingDialog());
        settingMenu.add(requestSettingMenuItem);

        // 性能设置
        JMenuItem performanceSettingMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.SETTINGS_JMETER_TITLE));
        performanceSettingMenuItem.addActionListener(e -> showPerformanceSettingDialog());
        settingMenu.add(performanceSettingMenuItem);

        // 界面设置
        JMenuItem uiSettingMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.SETTINGS_UI_TITLE));
        uiSettingMenuItem.addActionListener(e -> showUISettingDialog());
        settingMenu.add(uiSettingMenuItem);

        // 网络代理设置
        JMenuItem proxySettingMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_TITLE));
        proxySettingMenuItem.addActionListener(e -> showProxySettingDialog());
        settingMenu.add(proxySettingMenuItem);

        // 客户端证书设置
        JMenuItem clientCertMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.CERT_TITLE));
        clientCertMenuItem.addActionListener(e -> showClientCertificateDialog());
        settingMenu.add(clientCertMenuItem);

        // 系统设置
        JMenuItem systemSettingMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.SETTINGS_AUTO_UPDATE_TITLE));
        systemSettingMenuItem.addActionListener(e -> showSystemSettingDialog());
        settingMenu.add(systemSettingMenuItem);

        menuBar.add(settingMenu);
    }


    private void showRequestSettingDialog() {
        Window window = SwingUtilities.getWindowAncestor(this);
        RequestSettingsDialog dialog = new RequestSettingsDialog(window);
        dialog.setVisible(true);
    }

    private void showPerformanceSettingDialog() {
        Window window = SwingUtilities.getWindowAncestor(this);
        PerformanceSettingsDialog dialog = new PerformanceSettingsDialog(window);
        dialog.setVisible(true);
    }

    private void showUISettingDialog() {
        Window window = SwingUtilities.getWindowAncestor(this);
        UISettingsDialog dialog = new UISettingsDialog(window);
        dialog.setVisible(true);
    }

    private void showSystemSettingDialog() {
        Window window = SwingUtilities.getWindowAncestor(this);
        SystemSettingsDialog dialog = new SystemSettingsDialog(window);
        dialog.setVisible(true);
    }

    private void showProxySettingDialog() {
        Window window = SwingUtilities.getWindowAncestor(this);
        ProxySettingsDialog dialog = new ProxySettingsDialog(window);
        dialog.setVisible(true);
    }

    private void showClientCertificateDialog() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof Frame frame) {
            ClientCertificateSettingsDialog.showDialog(frame);
        } else {
            log.warn("Cannot show client certificate dialog: parent is not a Frame");
        }
    }

    private void addHelpMenu() {
        JMenu helpMenu = new JMenu(I18nUtil.getMessage(MessageKeys.MENU_HELP));
        JMenuItem updateMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.MENU_HELP_UPDATE));
        updateMenuItem.addActionListener(e -> checkUpdate());
        JMenuItem changelogMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.MENU_HELP_CHANGELOG));
        changelogMenuItem.addActionListener(e -> showChangelogDialog());
        JMenuItem feedbackMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.MENU_HELP_FEEDBACK));
        feedbackMenuItem.addActionListener(e -> showFeedbackDialog());
        helpMenu.add(updateMenuItem);
        helpMenu.add(changelogMenuItem);
        helpMenu.add(feedbackMenuItem);
        menuBar.add(helpMenu);
    }

    private void showFeedbackDialog() {
        JOptionPane.showMessageDialog(null, I18nUtil.getMessage(MessageKeys.FEEDBACK_MESSAGE),
                I18nUtil.getMessage(MessageKeys.FEEDBACK_TITLE), JOptionPane.INFORMATION_MESSAGE);
    }

    private void showChangelogDialog() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof Frame frame) {
            ChangelogDialog.showDialog(frame);
        } else {
            log.warn("Cannot show changelog dialog: parent is not a Frame");
        }
    }

    private void addAboutMenu() {
        JMenu aboutMenu = new JMenu(I18nUtil.getMessage(MessageKeys.MENU_ABOUT));
        JMenuItem aboutMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.MENU_ABOUT_EASYPOSTMAN));
        aboutMenuItem.addActionListener(e -> aboutActionPerformed());
        aboutMenu.add(aboutMenuItem);
        menuBar.add(aboutMenu);
    }

    private void addEnvironmentComboBox() {
        if (environmentComboBox == null) {
            environmentComboBox = new EnvironmentComboBox();
        } else {
            environmentComboBox.reload();
        }

        // 创建工作区显示标签
        if (workspaceLabel == null) {
            workspaceLabel = new JLabel();
            workspaceLabel.setFont(FontsUtil.getDefaultFont(Font.PLAIN, 12));
            workspaceLabel.setForeground(new Color(70, 70, 70));
            workspaceLabel.setIcon(new FlatSVGIcon("icons/workspace.svg", 20, 20));
            workspaceLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        }
        updateWorkspaceDisplay();

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(workspaceLabel);
        rightPanel.add(environmentComboBox);
        add(rightPanel, BorderLayout.EAST);
    }

    /**
     * 更新工作区显示
     */
    public void updateWorkspaceDisplay() {
        if (workspaceLabel == null) {
            return;
        }

        try {
            WorkspaceService workspaceService = WorkspaceService.getInstance();
            Workspace currentWorkspace = workspaceService.getCurrentWorkspace();

            if (currentWorkspace != null) {
                String displayText = currentWorkspace.getName();
                // 如果工作区名称太长，截断显示
                if (displayText.length() > 20) {
                    displayText = displayText.substring(0, 15) + "...";
                }
                workspaceLabel.setText(displayText);
            } else {
                workspaceLabel.setText("No Workspace");
            }
        } catch (Exception e) {
            log.warn("Failed to update workspace display", e);
            workspaceLabel.setText("No Workspace");
        }
    }

    private void aboutActionPerformed() {
        String iconUrl = getClass().getResource("/icons/icon.png") + "";
        String html = "<html>"
                + "<head>"
                + "<div style='border-radius:16px; border:1px solid #e0e0e0; padding:20px 28px; min-width:340px; max-width:420px;'>"
                + "<div style='text-align:center;'>"
                + "<img src='" + iconUrl + "' width='56' height='56' style='margin-bottom:10px;'/>"
                + "</div>"
                + "<div style='font-size:16px; font-weight:bold; color:#212529; text-align:center; margin-bottom:6px;'>EasyPostman</div>"
                + "<div style='font-size:12px; color:#666; text-align:center; margin-bottom:12px;'>"
                + I18nUtil.getMessage(MessageKeys.ABOUT_VERSION, getCurrentVersion()) + "</div>"
                + "<div style='font-size:10px; color:#444; margin-bottom:2px;'>"
                + I18nUtil.getMessage(MessageKeys.ABOUT_AUTHOR) + "</div>"
                + "<div style='font-size:10px; color:#444; margin-bottom:2px;'>"
                + I18nUtil.getMessage(MessageKeys.ABOUT_LICENSE) + "</div>"
                + "<div style='font-size:10px; color:#444; margin-bottom:8px;'>"
                + I18nUtil.getMessage(MessageKeys.ABOUT_WECHAT) + "</div>"
                + "<hr style='border:none; border-top:1px solid #eee; margin:10px 0;'>"
                + "<div style='font-size:9px; margin-bottom:2px;'>"
                + "<a href='https://laker.blog.csdn.net' style='color:#1a0dab; text-decoration:none;'>"
                + I18nUtil.getMessage(MessageKeys.ABOUT_BLOG) + "</a>"
                + "</div>"
                + "<div style='font-size:9px; margin-bottom:2px;'>"
                + "<a href='https://github.com/lakernote' style='color:#1a0dab; text-decoration:none;'>"
                + I18nUtil.getMessage(MessageKeys.ABOUT_GITHUB) + "</a>"
                + "</div>"
                + "<div style='font-size:9px;'>"
                + "<a href='https://gitee.com/lakernote' style='color:#1a0dab; text-decoration:none;'>"
                + I18nUtil.getMessage(MessageKeys.ABOUT_GITEE) + "</a>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";
        JEditorPane editorPane = getJEditorPane(html);
        JOptionPane.showMessageDialog(null, editorPane, I18nUtil.getMessage(MessageKeys.MENU_ABOUT_EASYPOSTMAN), JOptionPane.PLAIN_MESSAGE);
    }

    private static JEditorPane getJEditorPane(String html) {
        JEditorPane editorPane = new JEditorPane("text/html", html);
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, I18nUtil.getMessage(MessageKeys.ERROR_OPEN_LINK_FAILED, e.getURL()),
                            I18nUtil.getMessage(MessageKeys.GENERAL_ERROR), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        // 直接用JEditorPane，不用滚动条，且自适应高度
        editorPane.setPreferredSize(new Dimension(310, 350));
        return editorPane;
    }

    /**
     * 检查更新
     */
    private void checkUpdate() {
        UpdateService.getInstance().checkUpdateManually();
    }
}