package com.laker.postman.startup;

import com.laker.postman.common.constants.Icons;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;

/**
 * 启动欢迎窗口（Splash Window），用于主程序加载时的过渡。
 * 使用无边框 JFrame 代替 JWindow，降低 macOS 上首次创建顶层窗口的额外开销。
 */
@Slf4j
class SplashWindow extends JFrame {
    @Serial
    private static final long serialVersionUID = 1L;
    private JLabel statusLabel;
    private volatile boolean isDisposed = false;

    public SplashWindow() {
        super();
        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    /**
     * 在 EDT 线程中初始化 UI 组件。
     */
    public void init() {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                initUI();
            } else {
                SwingUtilities.invokeAndWait(this::initUI);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            log.error("初始化 SplashWindow 被中断", e);
            dispose();
            throw new SplashWindowInitializationException("Failed to initialize splash window", e);
        } catch (Exception e) {
            log.error("初始化 SplashWindow 失败", e);
            // 如果初始化失败，确保窗口被正确释放
            dispose();
            throw new SplashWindowInitializationException("Failed to initialize splash window", e);
        }
    }

    /**
     * 初始化 UI 组件
     */
    private void initUI() {
        JPanel content = createContentPanel();
        initializeWindow(content);
    }

    /**
     * 创建主要内容面板
     */
    private JPanel createContentPanel() {
        JPanel content = createSplashContentPanel();

        content.add(createLogoPanel(), BorderLayout.CENTER);

        content.add(createInfoPanel(), BorderLayout.NORTH);

        content.add(createStatusPanel(), BorderLayout.SOUTH);
        return content;
    }

    /**
     * 创建Logo面板
     */
    private JPanel createLogoPanel() {
        // 创建容器面板，用于绘制圆形背景
        JPanel logoContainer = new JPanel() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int size = 100; // 缩小圆形尺寸
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                // 绘制外层光晕
                g2.setColor(getDecorativeDotColor(25));
                g2.fillOval(x - 6, y - 6, size + 12, size + 12);

                // 绘制中层光晕
                g2.setColor(getDecorativeDotColor(40));
                g2.fillOval(x - 4, y - 4, size + 8, size + 8);

                // 绘制圆形背景
                g2.setColor(getDecorativeDotColor(95));
                g2.fillOval(x, y, size, size);

                // 绘制内部微妙阴影，增加立体感
                g2.setColor(getDecorativeDotColor(15));
                g2.fillOval(x + 2, y + 2, size - 4, size - 4);

                // 绘制边框高光
                g2.setColor(ModernColors.withAlpha(ModernColors.getTextInverse(), 120));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(x + 1, y + 1, size - 2, size - 2);

                g2.dispose();
            }
        };
        logoContainer.setOpaque(false);
        logoContainer.setLayout(new BorderLayout());
        // 调整容器尺寸（100 + 12 = 112，设为 120 留边距）
        logoContainer.setPreferredSize(new Dimension(120, 120));

        Image scaledImage = Icons.LOGO.getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
        ImageIcon logoIcon = new ImageIcon(scaledImage);
        JLabel logoLabel = new JLabel(logoIcon);
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoContainer.add(logoLabel, BorderLayout.CENTER);

        return logoContainer;
    }

    /**
     * 创建应用信息面板
     */
    private JPanel createInfoPanel() {
        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 1));
        infoPanel.setOpaque(false);

        // 应用名称
        JLabel appNameLabel = new JLabel(I18nUtil.getMessage(MessageKeys.APP_NAME), SwingConstants.CENTER);
        appNameLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 8));
        appNameLabel.setForeground(getTextColor());
        infoPanel.add(appNameLabel);

        // 版本号
        JLabel versionLabel = new JLabel(SystemUtil.getCurrentVersion(), SwingConstants.CENTER);
        versionLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 4));
        versionLabel.setForeground(getTextColor());
        infoPanel.add(versionLabel);

        return infoPanel;
    }

    /**
     * 创建状态面板
     */
    private JPanel createStatusPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 3));
        bottomPanel.setOpaque(false);
        statusLabel = new JLabel(I18nUtil.getMessage(MessageKeys.SPLASH_STATUS_STARTING), SwingConstants.CENTER);
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, 3)); // 比标准字体大3号
        statusLabel.setForeground(getTextColor());
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        return bottomPanel;
    }

    /**
     * 初始化窗口
     */
    private void initializeWindow(JPanel content) {
        setContentPane(content);
        setSize(340, 250); // 设置窗口大小
        setLocationRelativeTo(null);  // 居中显示

        setupWindowProperties();

        setVisible(true); // 显示窗口
    }

    /**
     * 安全设置窗口属性
     */
    private void setupWindowProperties() {
        try {
            setBackground(new Color(0, 0, 0, 0)); // 透明背景
        } catch (Exception e) {
            log.warn("设置透明背景失败，使用默认背景", e);
        }

        try {
            setAlwaysOnTop(true); // 窗口总在最上层
        } catch (Exception e) {
            log.warn("设置窗口置顶失败", e);
        }
    }

    private static JPanel createSplashContentPanel() {
        JPanel content = new JPanel() { // 自定义面板，绘制渐变背景和圆角
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                Color gradientStart = ModernColors.getSplashGradientStartColor();
                Color gradientEnd = ModernColors.getSplashGradientEndColor();
                GradientPaint gp = new GradientPaint(
                        0, 0, gradientStart,
                        getWidth(), getHeight(), gradientEnd
                );
                g2d.setPaint(gp);
                // 圆角背景
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 32, 32);

                // 添加微妙的光泽效果
                Color highlightStart = ModernColors.withAlpha(ModernColors.getTextInverse(), 40);
                Color highlightEnd = ModernColors.withAlpha(ModernColors.getTextInverse(), 0);
                GradientPaint glossPaint = new GradientPaint(
                        0, 0, highlightStart,
                        0, getHeight() / 2.0f, highlightEnd
                );
                g2d.setPaint(glossPaint);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight() / 2, 32, 32);

                Color borderColor = ModernColors.withAlpha(ModernColors.getBorderMediumColor(), 100);
                g2d.setColor(borderColor);
                g2d.setStroke(new BasicStroke(2f));
                g2d.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 32, 32);

                g2d.dispose();
            }
        };
        content.setLayout(new BorderLayout(0, 10)); // 使用 BorderLayout 布局
        content.setOpaque(false); // 设置透明背景
        content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24)); // 设置内边距
        return content;
    }

    public void setStatus(String statusKey) {
        if (!isDisposed) {
            SwingUtilities.invokeLater(() -> updateStatusLabel(statusKey));
        }
    }

    /**
     * 更新状态标签
     */
    private void updateStatusLabel(String statusKey) {
        if (!isDisposed && statusLabel != null) {
            statusLabel.setText(I18nUtil.getMessage(statusKey));
        }
    }

    public void startMainFrameInitialization(StartupCoordinator startupCoordinator) {
        new SplashStartupWorker(this, startupCoordinator).execute();
    }

    /**
     * 处理主窗口加载错误
     */
    void handleMainFrameLoadError(Throwable e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        StartupFailureHandler.showStartupErrorAndExit(e);
    }

    /**
     * 启动渐隐动画
     */
    void startFadeOutAnimation(MainFrame mainFrame, StartupCoordinator startupCoordinator) {
        if (isDisposed) return;

        // 先显示主窗口；一旦启动占位符稳定绘制完成就尽快退场，不再阻塞等待主内容完全加载。
        startupCoordinator.showMainFrameAndLoadContent(mainFrame);
        startupCoordinator.runAfterStartupShellReady(
                mainFrame,
                () -> closeSplash(startupCoordinator),
                this::handleMainFrameLoadError
        );
    }

    boolean isDisposed() {
        return isDisposed;
    }

    private void closeSplash(StartupCoordinator startupCoordinator) {
        disposeSafely();
        startupCoordinator.scheduleBackgroundTasks();
    }

    /**
     * 安全释放资源
     */
    private void disposeSafely() {
        if (isDisposed) return;

        isDisposed = true;

        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            dispose();
        });
    }

    /**
     * 重写dispose方法，确保资源完全释放
     */
    @Override
    public void dispose() {
        if (!isDisposed) {
            disposeSafely();
        } else {
            super.dispose();
        }
    }

    /**
     * 获取主题适配的文字颜色
     */
    private Color getTextColor() {
        return ModernColors.withAlpha(ModernColors.getTextInverse(), 220);
    }

    /**
     * 获取主题适配的装饰点颜色
     */
    private Color getDecorativeDotColor(int alpha) {
        return ModernColors.withAlpha(ModernColors.getTextInverse(), alpha);
    }
}
