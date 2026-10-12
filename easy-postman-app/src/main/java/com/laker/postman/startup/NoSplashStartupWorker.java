package com.laker.postman.startup;

import com.laker.postman.frame.MainFrame;
import lombok.RequiredArgsConstructor;

import javax.swing.SwingWorker;
import java.util.concurrent.CountDownLatch;

/**
 * 无 Splash 模式的主窗口后台启动任务。
 */
@RequiredArgsConstructor
class NoSplashStartupWorker extends SwingWorker<MainFrame, Void> {
    private final StartupCoordinator startupCoordinator;
    private final CountDownLatch edtStartupCompleted = new CountDownLatch(1);

    void start() {
        // SwingWorker 的默认线程为 daemon；无窗口时 AWT 也会自动退出。
        // 由短生命周期的非 daemon 线程保活，直到 done 在 EDT 上显示窗口或处理失败。
        Thread startupThread = new Thread(() -> {
            run();
            boolean interrupted = false;
            while (true) {
                try {
                    edtStartupCompleted.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "GUI-No-Splash-Startup");
        startupThread.setDaemon(false);
        startupThread.start();
    }

    @Override
    protected MainFrame doInBackground() {
        try {
            return startupCoordinator.prepareMainFrameShell(null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize GUI runtime", e);
        }
    }

    @Override
    protected void done() {
        try {
            MainFrame mainFrame = get();
            startupCoordinator.showMainFrameAndLoadContent(mainFrame);
            startupCoordinator.runAfterMainContentReady(
                    mainFrame,
                    startupCoordinator::scheduleBackgroundTasks,
                    StartupFailureHandler::showStartupErrorAndExit
            );
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            StartupFailureHandler.showStartupErrorAndExit(e);
        } finally {
            edtStartupCompleted.countDown();
        }
    }
}
