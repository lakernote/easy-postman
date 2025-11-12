package com.laker.postman.common.window;

import com.laker.postman.common.SingletonFactory;
import com.laker.postman.common.constants.Icons;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.ioc.Component;
import com.laker.postman.ioc.PostConstruct;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.Serial;

/**
 * 启动欢迎窗口（Splash Window），用于主程序加载时的过渡。
 */
@Slf4j
@Component
public class SplashWindow extends JWindow {
    @Serial
    private static final long serialVersionUID = 1L; // 添加序列化ID
    public static final int MIN_TIME = 1000; // 最小显示时间，避免闪屏
    private static final float FADE_STEP = 0.08f; // 渐隐步长
    private static final float MIN_OPACITY = 0.05f; // 最小透明度
    private static final int FADE_TIMER_DELAY = 15; // 渐隐定时器延迟

    private JLabel statusLabel; // 状态标签，用于显示加载状态
    private transient Timer fadeOutTimer; // 渐隐计时器
    private transient ActionListener fadeOutListener; // 渐隐监听器，用于防止内存泄漏
    private volatile boolean isDisposed = false; // 标记窗口是否已释放


    /**
     * Bean 初始化方法 - 在依赖注入完成后自动调用
     * 在 EDT 线程中初始化 UI 组件
     */
    @PostConstruct
    public void init() {
        try {
            // 确保在 EDT 线程中初始化 UI
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
        JPanel content = getJPanel();

        // Logo
        content.add(createLogoPanel(), BorderLayout.CENTER);

        // 应用信息
        content.add(createInfoPanel(), BorderLayout.NORTH);

        // 状态面板
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
                g2.setColor(ModernColors.whiteWithAlpha(25));
                g2.fillOval(x - 6, y - 6, size + 12, size + 12);

                // 绘制中层光晕
                g2.setColor(ModernColors.whiteWithAlpha(40));
                g2.fillOval(x - 4, y - 4, size + 8, size + 8);

                // 绘制圆形背景
                g2.setColor(ModernColors.whiteWithAlpha(95));
                g2.fillOval(x, y, size, size);

                // 绘制内部微妙阴影，增加立体感
                g2.setColor(ModernColors.whiteWithAlpha(15));
                g2.fillOval(x + 2, y + 2, size - 4, size - 4);

                // 绘制边框高光
                g2.setColor(Color.WHITE);
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
        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        infoPanel.setOpaque(false);

        // 应用名称
        JLabel appNameLabel = new JLabel(I18nUtil.getMessage(MessageKeys.APP_NAME), SwingConstants.CENTER);
        appNameLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD, 20));
        appNameLabel.setForeground(ModernColors.whiteWithAlpha(220));
        infoPanel.add(appNameLabel);

        // 版本号
        JLabel versionLabel = new JLabel(SystemUtil.getCurrentVersion(), SwingConstants.CENTER);
        versionLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD, 15));
        versionLabel.setForeground(ModernColors.whiteWithAlpha(220));
        infoPanel.add(versionLabel);

        return infoPanel;
    }

    /**
     * 创建状态面板
     */
    private JPanel createStatusPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 5));
        bottomPanel.setOpaque(false);
        statusLabel = new JLabel(I18nUtil.getMessage(MessageKeys.SPLASH_STATUS_STARTING), SwingConstants.CENTER);
        statusLabel.setFont(FontsUtil.getDefaultFont(Font.PLAIN, 15));
        statusLabel.setForeground(ModernColors.whiteWithAlpha(220));
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        return bottomPanel;
    }

    /**
     * 初始化窗口
     */
    private void initializeWindow(JPanel content) {
        setContentPane(content);
        setSize(350, 240); // 设置窗口大小
        setLocationRelativeTo(null);  // 居中显示

        // 安全设置透明度和置顶
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

    private static JPanel getJPanel() {
        JPanel content = new JPanel() { // 自定义面板，绘制渐变背景和圆角
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                // 现代化蓝色渐变（从iOS蓝到浅蓝）
                GradientPaint gp = new GradientPaint(
                        0, 0, ModernColors.PRIMARY,
                        getWidth(), getHeight(), ModernColors.PRIMARY_LIGHTER
                );
                g2d.setPaint(gp);
                // 圆角背景
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 32, 32);

                // 添加微妙的光泽效果
                GradientPaint glossPaint = new GradientPaint(
                        0, 0, ModernColors.whiteWithAlpha(40),
                        0, getHeight() / 2.0f, ModernColors.whiteWithAlpha(0)
                );
                g2d.setPaint(glossPaint);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight() / 2, 32, 32);

                // 添加边框高光
                g2d.setColor(ModernColors.whiteWithAlpha(80));
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

    public void initMainFrame() {
        SwingWorker<MainFrame, String> worker = new SwingWorker<>() {
            @Override
            protected MainFrame doInBackground() {
                long start = System.currentTimeMillis();

                publish(MessageKeys.SPLASH_STATUS_LOADING_MAIN);
                setProgress(30);
                MainFrame mainFrame = SingletonFactory.getInstance(MainFrame.class);

                publish(MessageKeys.SPLASH_STATUS_INITIALIZING);
                setProgress(60);
                mainFrame.initComponents();

                publish(MessageKeys.SPLASH_STATUS_READY);
                setProgress(100);

                long cost = System.currentTimeMillis() - start;
                log.info("main frame initComponents cost: {} ms", cost);

                ensureMinimumDisplayTime(cost);
                return mainFrame;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                // 在EDT中更新状态
                if (!chunks.isEmpty() && !isDisposed) {
                    setStatus(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    if (isDisposed) return;

                    setStatus(MessageKeys.SPLASH_STATUS_DONE);
                    MainFrame mainFrame = get();

                    // 启动渐隐动画关闭 SplashWindow
                    startFadeOutAnimation(mainFrame);

                } catch (Exception e) {
                    handleMainFrameLoadError(e);
                }
            }
        };
        worker.execute();
    }

    /**
     * 确保最小显示时间
     */
    private void ensureMinimumDisplayTime(long cost) {
        if (cost < MIN_TIME) {
            try {
                Thread.sleep(MIN_TIME - cost);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                log.warn("Thread interrupted while sleeping", interruptedException);
            }
        }
    }

    /**
     * 处理主窗口加载错误
     */
    private void handleMainFrameLoadError(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.error("加载主窗口失败", e);

        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                null,
                I18nUtil.getMessage(MessageKeys.SPLASH_ERROR_LOAD_MAIN),
                I18nUtil.getMessage(MessageKeys.GENERAL_ERROR),
                JOptionPane.ERROR_MESSAGE
        ));
        System.exit(1);
    }

    /**
     * 启动渐隐动画
     */
    private void startFadeOutAnimation(MainFrame mainFrame) {
        if (isDisposed) return;

        // 在开始渐隐动画之前就显示主窗口，实现重叠效果
        SwingUtilities.invokeLater(() -> {
            if (mainFrame != null) {
                mainFrame.setVisible(true);
                // 确保主窗口在前面
                mainFrame.toFront();
                mainFrame.requestFocus();
            }
        });

        fadeOutListener = createFadeOutListener();
        fadeOutTimer = new Timer(FADE_TIMER_DELAY, fadeOutListener);
        fadeOutTimer.start();
    }

    /**
     * 创建渐隐监听器
     */
    private ActionListener createFadeOutListener() {
        return e -> {
            if (isDisposed) {
                stopFadeOutAnimation();
                return;
            }

            try {
                processFadeOutStep();
            } catch (Exception ex) {
                handleFadeOutError(ex);
            }
        };
    }

    /**
     * 处理渐隐步骤
     */
    private void processFadeOutStep() {
        float opacity = getOpacity();
        if (opacity > MIN_OPACITY) {
            setOpacity(Math.max(0f, opacity - FADE_STEP));
        } else {
            completeFadeOut();
        }
    }

    /**
     * 完成渐隐效果
     */
    private void completeFadeOut() {
        stopFadeOutAnimation();
        disposeSafely();
    }

    /**
     * 处理渐隐错误
     */
    private void handleFadeOutError(Exception ex) {
        log.warn("渐隐动画执行失败，直接关闭窗口", ex);
        stopFadeOutAnimation();
        disposeSafely();
        // 主窗口在渐隐开始前就已经显示，这里不需要再显示
    }

    /**
     * 停止渐隐动画
     */
    private void stopFadeOutAnimation() {
        if (fadeOutTimer != null) {
            fadeOutTimer.stop();
            if (fadeOutListener != null) {
                fadeOutTimer.removeActionListener(fadeOutListener);
            }
            fadeOutTimer = null;
            fadeOutListener = null;
        }
    }

    /**
     * 安全释放资源
     */
    private void disposeSafely() {
        if (isDisposed) return;

        isDisposed = true;
        stopFadeOutAnimation();

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
}
