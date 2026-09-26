package com.laker.postman.frame;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.animation.WindowSnapshotTransition;
import com.laker.postman.common.component.placeholder.StartupShellPlaceholderPanel;
import com.laker.postman.common.constants.Icons;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.panel.MainPanel;
import com.laker.postman.panel.lifecycle.AppExitCoordinator;
import com.laker.postman.panel.performance.PerformancePanel;
import com.laker.postman.panel.topmenu.TopMenuBar;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/**
 * 主窗口类，继承自 JFrame。
 */
@Slf4j
public class MainFrame extends JFrame {
    private final transient WindowSnapshotTransition startupShellTransition;
    private final transient MainFrameStartupLifecycle startupLifecycle;
    private final transient MainWindowStateController windowStateController;
    private transient JPanel startupShellPanel;

    // 单例模式，确保只有一个实例
    private MainFrame() {
        super();
        startupShellTransition = new WindowSnapshotTransition(this);
        startupLifecycle = new MainFrameStartupLifecycle();
        windowStateController = new MainWindowStateController(this);

        setName(I18nUtil.getMessage(MessageKeys.APP_NAME));
        setTitle(I18nUtil.getMessage(MessageKeys.APP_NAME));
        setIconImage(Icons.LOGO.getImage());
        MainWindowChrome.applyInitialDecorations(this);
    }

    public void initComponents() {
        setJMenuBar(UiSingletonFactory.getInstance(TopMenuBar.class));
        installStartupShell();

        // 设置最小窗口尺寸，防止窗口被拖得太小
        Dimension minSize = windowStateController.getMinWindowSize();
        setMinimumSize(minSize);
        if (startupShellPanel != null) {
            startupShellPanel.setPreferredSize(windowStateController.getInitialWindowSize());
        }

        windowStateController.initWindowSize();
        initWindowCloseListener();
        windowStateController.installStateListeners();

        // 如果没有保存的窗口状态，使用 pack() 自适应组件大小
        if (!windowStateController.hasSavedWindowState()) {
            pack();
        }

        // 只有在非最大化状态下才居中窗口，避免最大化恢复路径上的额外跳变
        boolean isMaximized = (getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        if (!isMaximized) {
            setLocationRelativeTo(null);
        }
        log.info("MainFrame component initialization completed: bounds={}, state={}",
                getBounds(), getExtendedState());
    }

    public void loadMainContentAsync() {
        if (!startupLifecycle.markMainContentLoadRequested()) {
            return;
        }
        // 主内容延后到启动壳显示后加载，避免首次展示窗口时被完整工作区初始化阻塞。
        Runnable task = () -> {
            try {
                log.info("Main content initialization started");
                replaceContentWithStartupTransition(UiSingletonFactory.getInstance(MainPanel.class));
                startupShellPanel = null;
                startupLifecycle.markMainContentLoaded();
                log.info("Main content initialization completed");
            } catch (Throwable throwable) {
                log.error("Failed to initialize main content", throwable);
                startupLifecycle.markMainContentLoadFailed(throwable);
            }
        };

        SwingUtilities.invokeLater(task);
    }

    private void installStartupShell() {
        startupShellPanel = createStartupShellPanel();
        setContentPane(startupShellPanel);
        MainWindowChrome.applyBackground(this);
    }

    public void refreshWindowChrome() {
        MainWindowChrome.refresh(this);
    }

    private JPanel createStartupShellPanel() {
        JPanel root = new StartupShellPlaceholderPanel() {
            private boolean firstPaintHandled;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!firstPaintHandled) {
                    firstPaintHandled = true;
                    startupLifecycle.markStartupShellPainted();
                }
            }
        };
        root.setOpaque(true);
        root.setBackground(ModernColors.getBackgroundColor());
        return root;
    }

    private void replaceContentWithStartupTransition(Container nextContentPane) {
        WindowSnapshotTransition.CapturedSnapshot capturedSnapshot = null;
        Container currentContentPane = getContentPane();
        if (currentContentPane instanceof JComponent contentComponent) {
            // 这里只保留基于 layeredPane 的纯绘制快照过渡。
            // 不再使用 glassPane 覆盖整窗，避免挡住底层分割条的 hover / resize cursor。
            capturedSnapshot = startupShellTransition.captureSnapshot(contentComponent);
        }
        setContentPane(nextContentPane);
        MainWindowChrome.applyBackground(this);
        revalidate();
        repaint();
        startupShellTransition.start(capturedSnapshot);
    }

    public void whenMainContentLoaded(Runnable callback) {
        startupLifecycle.whenMainContentLoaded(callback);
    }

    public void whenMainContentLoadFailed(Consumer<Throwable> callback) {
        startupLifecycle.whenMainContentLoadFailed(callback);
    }

    public void whenStartupShellPainted(Runnable callback) {
        startupLifecycle.whenStartupShellPainted(callback);
    }

    private void initWindowCloseListener() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 清理资源并保存状态
                cleanup();
                windowStateController.saveWindowState();
                BeanFactory.getBean(AppExitCoordinator.class).exitApplication();
            }
        });
    }

    /**
     * 清理资源（在窗口关闭时调用）
     */
    private void cleanup() {
        windowStateController.stop();
        startupShellTransition.stop();

        // 清理性能测试面板资源（停止定时器等）
        try {
            UiSingletonFactory.getExistingInstance(PerformancePanel.class)
                    .ifPresent(PerformancePanel::cleanup);
        } catch (Exception e) {
            log.warn("清理 PerformancePanel 资源时出错: {}", e.getMessage());
        }
    }
}
