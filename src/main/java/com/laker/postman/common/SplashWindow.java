package com.laker.postman.common;

import com.laker.postman.common.constants.Icons;
import com.laker.postman.common.frame.MainFrame;
import com.laker.postman.util.FontUtil;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;

/**
 * 启动欢迎窗口（Splash Window），用于主程序加载时的过渡。
 */
@Slf4j
public class SplashWindow extends JWindow {
    public static final int MIN_TIME = 1000;
    private final JLabel statusLabel;

    public SplashWindow() {
        // 自定义渐变圆角面板
        JPanel content = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                // 渐变色（可自定义颜色）
                GradientPaint gp = new GradientPaint(0, 0, new Color(90, 155, 255), getWidth(), getHeight(), new Color(245, 247, 250));
                g2d.setPaint(gp);
                // 圆角背景
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 32, 32);
                g2d.dispose();
            }
        };
        content.setLayout(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        // Logo
        JLabel logoLabel = new JLabel(new ImageIcon(Icons.LOGO.getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH)));
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(logoLabel, BorderLayout.CENTER);

        // 应用名称和版本
        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        infoPanel.setOpaque(false);
        JLabel appNameLabel = new JLabel("EasyPostman", SwingConstants.CENTER);
        appNameLabel.setFont(FontUtil.getDefaultFont(Font.BOLD, 22));
        appNameLabel.setForeground(new Color(60, 90, 180));
        infoPanel.add(appNameLabel);
        JLabel versionLabel = new JLabel(SystemUtil.getCurrentVersion(), SwingConstants.CENTER);
        versionLabel.setFont(FontUtil.getDefaultFont(Font.PLAIN, 13));
        versionLabel.setForeground(new Color(120, 130, 150));
        infoPanel.add(versionLabel);
        content.add(infoPanel, BorderLayout.NORTH);

        // 状态
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 5));
        bottomPanel.setOpaque(false);
        statusLabel = new JLabel("正在启动 EasyPostman...", SwingConstants.CENTER);
        statusLabel.setFont(FontUtil.getDefaultFont(Font.PLAIN, 15));
        statusLabel.setForeground(new Color(80, 120, 200));
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(450, 280);
        setLocationRelativeTo(null);
        setBackground(new Color(0, 0, 0, 0)); // 透明背景
        setAlwaysOnTop(true);
        setVisible(true);
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }


    public void initMainFrame() {
        SwingWorker<MainFrame, Void> worker = new SwingWorker<>() {
            @Override
            protected MainFrame doInBackground() {
                long start = System.currentTimeMillis();
                setStatus("正在加载主界面...");
                setProgress(30);
                MainFrame mainFrame = MainFrame.getInstance();
                setStatus("正在初始化组件...");
                setProgress(60);
                mainFrame.initComponents();
                setStatus("准备就绪");
                setProgress(100);
                long cost = System.currentTimeMillis() - start;
                log.info("main frame initComponents cost: {} ms", cost);
                if (cost < MIN_TIME) {
                    try {
                        Thread.sleep(MIN_TIME - cost);
                    } catch (InterruptedException ignored) {
                    }
                }
                return mainFrame;
            }

            @Override
            protected void done() {
                try {
                    setStatus("加载完成，正在显示主界面...");
                    long start = System.currentTimeMillis();
                    MainFrame mainFrame = get();
                    // 渐隐动画关闭 SplashWindow
                    Timer timer = new Timer(15, null);
                    timer.addActionListener(e -> {
                        float opacity = getOpacity();
                        if (opacity > 0.05f) {
                            setOpacity(Math.max(0f, opacity - 0.08f));
                        } else {
                            timer.stop();
                            setVisible(false);
                            dispose();
                            // 显示主界面
                            SwingUtilities.invokeLater(() -> {
                                mainFrame.setVisible(true);
                            });
                        }
                    });
                    timer.start();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "主窗口加载失败，请重启应用。", "错误", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            }
        };
        worker.execute();
    }
}