package com.laker.postman.frame;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.placeholder.StartupShellPlaceholderPanel;
import com.laker.postman.common.constants.Icons;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.panel.MainPanel;
import com.laker.postman.panel.lifecycle.AppExitCoordinator;
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
    private final transient MainFrameStartupLifecycle startupLifecycle;
    private final transient MainWindowStateController windowStateController;
    private transient JPanel startupShellPanel;
    private boolean closing;

    // 单例模式，确保只有一个实例
    private MainFrame() {
        super();
        startupLifecycle = new MainFrameStartupLifecycle();
        windowStateController = new MainWindowStateController(this);

        setName(I18nUtil.getMessage(MessageKeys.APP_NAME));
        setTitle(I18nUtil.getMessage(MessageKeys.APP_NAME));
        setIconImage(Icons.LOGO.getImage());
        MainWindowChrome.applyInitialDecorations(this);
    }

    public void initComponents() {
        setJMenuBar(UiSingletonFactory.getInstance(TopMenuBar.class));
        initStartupShell();
    }

    private void initStartupShell() {
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
            if (closing) {
                return;
            }
            try {
                log.info("Main content initialization started");
                replaceStartupShellWithContent(UiSingletonFactory.getInstance(MainPanel.class));
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

    private void replaceStartupShellWithContent(Container nextContentPane) {
        setContentPane(nextContentPane);
        MainWindowChrome.applyBackground(this);
        revalidate();
        repaint();
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
                BeanFactory.getBean(AppExitCoordinator.class).exitApplication();
            }
        });
    }

    /**
     * 用户确认退出后保存窗口状态并停止窗口后台活动。
     */
    public void prepareForExit() {
        closing = true;
        windowStateController.stop();
        windowStateController.saveWindowState();
        setEnabled(false);
    }
}
