package com.laker.postman.panel.lifecycle;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.exception.CancelException;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.ioc.Component;
import com.laker.postman.panel.collections.OpenedRequestTabSessionSaver;
import com.laker.postman.panel.functional.FunctionalPanel;
import com.laker.postman.panel.performance.PerformancePanel;
import lombok.extern.slf4j.Slf4j;

import javax.swing.SwingUtilities;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AppExitCoordinator {
    private static final long REMOTE_STOP_TIMEOUT_SECONDS = 10;
    private boolean exitInProgress;

    /**
     * 显示退出确认对话框，处理未保存内容。
     */
    public void exitApplication() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::exitApplication);
            return;
        }
        if (exitInProgress) {
            return;
        }
        // 模态保存对话框期间仍可能收到 Quit/窗口关闭事件，避免重复退出流程。
        exitInProgress = true;

        // 保存所有打开的请求（包括未保存的和已保存的）
        try {
            OpenedRequestTabSessionSaver.saveOpenTabsOnExit();
        } catch (CancelException e) {
            // 用户取消了保存操作，终止退出
            exitInProgress = false;
            return;
        } catch (RuntimeException e) {
            exitInProgress = false;
            throw e;
        }

        // 保存功能测试配置
        try {
            UiSingletonFactory.getExistingInstance(FunctionalPanel.class)
                    .ifPresent(FunctionalPanel::save);
        } catch (Exception e) {
            log.error("Failed to save functional test config on exit", e);
        }

        // 保存性能测试配置
        try {
            UiSingletonFactory.getExistingInstance(PerformancePanel.class)
                    .ifPresent(PerformancePanel::save);
        } catch (Exception e) {
            log.error("Failed to save performance test config on exit", e);
        }

        // 所有入口都在确认保存后统一清理；取消退出不会释放仍在使用的面板。
        log.info("User chose to exit application");
        MainFrame mainFrame = UiSingletonFactory.getExistingInstance(MainFrame.class).orElse(null);
        if (mainFrame != null) {
            mainFrame.prepareForExit();
        }
        PerformancePanel performancePanel = UiSingletonFactory.getExistingInstance(PerformancePanel.class).orElse(null);
        if (performancePanel != null) {
            try {
                performancePanel.cleanup();
            } catch (Exception e) {
                log.warn("Failed to clean up performance panel on exit", e);
            }
        }

        // 网络停止请求需要完成后才能退出 JVM，等待放在后台，保持 EDT 可处理事件。
        Thread exitThread = new Thread(() -> {
            try {
                if (performancePanel != null
                        && !performancePanel.awaitRemoteStopOnExit(REMOTE_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("Remote stop requests did not all complete successfully within {} seconds",
                            REMOTE_STOP_TIMEOUT_SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for remote performance workers to stop", e);
            } catch (Exception e) {
                log.warn("Failed to wait for remote performance workers to stop", e);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    if (mainFrame != null) {
                        mainFrame.dispose();
                    }
                    System.exit(0);
                });
            }
        }, "ApplicationExit");
        exitThread.setDaemon(false);
        exitThread.start();
    }
}
