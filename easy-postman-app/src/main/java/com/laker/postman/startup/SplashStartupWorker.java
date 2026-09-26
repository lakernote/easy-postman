package com.laker.postman.startup;

import com.laker.postman.frame.MainFrame;
import com.laker.postman.util.MessageKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.SwingWorker;
import java.util.List;

/**
 * Splash 模式的主窗口后台启动任务。
 */
@Slf4j
@RequiredArgsConstructor
class SplashStartupWorker extends SwingWorker<MainFrame, String> {
    private static final int MIN_DISPLAY_TIME_MS = 350;

    private final SplashWindow splashWindow;
    private final StartupCoordinator startupCoordinator;

    @Override
    protected MainFrame doInBackground() {
        long startTimeMillis = System.currentTimeMillis();
        try {
            MainFrame mainFrame = startupCoordinator.prepareMainFrameShell(this::publishStage);
            ensureMinimumDisplayTime(startTimeMillis);
            return mainFrame;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare main frame", e);
        }
    }

    @Override
    protected void process(List<String> chunks) {
        if (!chunks.isEmpty() && !splashWindow.isDisposed()) {
            splashWindow.setStatus(chunks.get(chunks.size() - 1));
        }
    }

    @Override
    protected void done() {
        try {
            if (splashWindow.isDisposed()) {
                log.warn("Splash startup worker completed after the splash window was disposed; main frame will not be shown");
                return;
            }

            splashWindow.setStatus(MessageKeys.SPLASH_STATUS_DONE);
            MainFrame mainFrame = get();
            splashWindow.startFadeOutAnimation(mainFrame, startupCoordinator);
        } catch (Exception e) {
            splashWindow.handleMainFrameLoadError(e);
        }
    }

    private void publishStage(StartupCoordinator.StartupStage stage) {
        switch (stage) {
            case STARTING -> {
                publish(MessageKeys.SPLASH_STATUS_STARTING);
                setProgress(10);
            }
            case LOADING_PLUGINS -> {
                publish(MessageKeys.SPLASH_STATUS_LOADING_PLUGINS);
                setProgress(20);
            }
            case LOADING_MAIN -> {
                publish(MessageKeys.SPLASH_STATUS_LOADING_MAIN);
                setProgress(45);
            }
            case READY -> {
                publish(MessageKeys.SPLASH_STATUS_READY);
                setProgress(100);
            }
        }
    }

    private void ensureMinimumDisplayTime(long startTimeMillis) {
        long elapsed = System.currentTimeMillis() - startTimeMillis;
        long remaining = MIN_DISPLAY_TIME_MS - elapsed;
        if (remaining <= 0) {
            return;
        }
        try {
            Thread.sleep(remaining);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("Thread interrupted while sleeping", interruptedException);
        }
    }
}
