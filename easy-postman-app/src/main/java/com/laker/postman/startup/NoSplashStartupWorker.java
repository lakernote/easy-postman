package com.laker.postman.startup;

import com.laker.postman.frame.MainFrame;
import lombok.RequiredArgsConstructor;

import javax.swing.SwingWorker;

/**
 * 无 Splash 模式的主窗口后台启动任务。
 */
@RequiredArgsConstructor
class NoSplashStartupWorker extends SwingWorker<MainFrame, Void> {
    private final StartupCoordinator startupCoordinator;

    @Override
    protected MainFrame doInBackground() {
        try {
            return startupCoordinator.prepareMainFrameShell(null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare main frame", e);
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
        }
    }
}
