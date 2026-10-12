package com.laker.postman.startup;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.service.sync.WebDavSyncScheduler;
import lombok.extern.slf4j.Slf4j;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 协调应用启动过程中与主窗口相关的初始化步骤。
 */
@Slf4j
public class StartupCoordinator {
    private final long launchStartedAtNanos;
    private CompletableFuture<Void> hostIocInitialization;

    public StartupCoordinator(long launchStartedAtNanos) {
        this.launchStartedAtNanos = launchStartedAtNanos;
    }

    /**
     * IOC 扫描只注册 Bean 定义，与 Swing 主题安装互不依赖，可以同时进行。
     */
    public synchronized void startHostIocInitialization() {
        if (hostIocInitialization != null) {
            return;
        }
        hostIocInitialization = CompletableFuture.runAsync(() -> {
            long startedAt = System.nanoTime();
            AppLauncher.markStartupCheckpoint("initializing host IOC container");
            log.info("GUI startup stage: initializing host IOC container");
            GuiStartupBootstrap.initBeanFactory();
            AppLauncher.markStartupCheckpoint("host IOC container initialized");
            log.info("GUI startup stage complete: host IOC container initialized in {} ms",
                    (System.nanoTime() - startedAt) / 1_000_000);
            long workspaceStartedAt = System.nanoTime();
            GuiStartupBootstrap.initWorkspaceState();
            log.info("GUI startup stage complete: workspace state initialized in {} ms",
                    (System.nanoTime() - workspaceStartedAt) / 1_000_000);
        }, task -> {
            Thread thread = new Thread(task, "GUI-IOC-Startup");
            thread.setDaemon(true);
            thread.start();
        });
    }

    public void initializePluginRuntimeAfterHostReady() {
        awaitHostIocInitialization();
        long startedAt = System.nanoTime();
        AppLauncher.markStartupCheckpoint("initializing plugin runtime");
        log.info("GUI startup stage: initializing plugin runtime");
        GuiStartupBootstrap.initPluginRuntime();
        AppLauncher.markStartupCheckpoint("plugin runtime initialized");
        log.info("GUI startup stage complete: plugin runtime initialized in {} ms",
                (System.nanoTime() - startedAt) / 1_000_000);
    }

    private void awaitHostIocInitialization() {
        startHostIocInitialization();
        hostIocInitialization.join();
    }

    public MainFrame prepareMainFrameShell(StartupProgressListener progressListener) throws Exception {
        notifyProgress(progressListener, StartupStage.STARTING);
        awaitHostIocInitialization();
        notifyProgress(progressListener, StartupStage.LOADING_PLUGINS);
        initializePluginRuntimeAfterHostReady();

        notifyProgress(progressListener, StartupStage.LOADING_MAIN);
        AppLauncher.markStartupCheckpoint("creating main frame on EDT");
        log.info("GUI startup stage: creating and initializing main frame on EDT");
        long frameStartedAt = System.nanoTime();
        MainFrame mainFrame = createAndInitializeMainFrameOnEdt();
        AppLauncher.markStartupCheckpoint("main frame initialized");
        log.info("GUI startup stage complete: main frame initialized in {} ms",
                (System.nanoTime() - frameStartedAt) / 1_000_000);

        notifyProgress(progressListener, StartupStage.READY);
        return mainFrame;
    }

    public void showMainFrameAndLoadContent(MainFrame mainFrame) {
        if (mainFrame == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            showMainFrameAndLoadContentOnEdt(mainFrame);
            return;
        }
        SwingUtilities.invokeLater(() -> showMainFrameAndLoadContentOnEdt(mainFrame));
    }

    public void scheduleBackgroundUpdateCheck() {
        StartupUpdateScheduler.scheduleBackgroundUpdateCheck();
    }

    public void scheduleBackgroundTasks() {
        scheduleBackgroundUpdateCheck();
        BeanFactory.getBean(WebDavSyncScheduler.class).start();
    }

    public void runAfterMainContentReady(MainFrame mainFrame,
                                         Runnable onReady,
                                         Consumer<Throwable> onFailure) {
        waitForMainFrameReadiness(mainFrame, true, onReady, onFailure);
    }

    public void runAfterStartupShellReady(MainFrame mainFrame,
                                          Runnable onReady,
                                          Consumer<Throwable> onFailure) {
        waitForMainFrameReadiness(mainFrame, false, onReady, onFailure);
    }

    private void waitForMainFrameReadiness(MainFrame mainFrame,
                                           boolean waitForMainContent,
                                           Runnable onReady,
                                           Consumer<Throwable> onFailure) {
        new MainFrameReadinessWatcher(mainFrame, waitForMainContent, onReady, onFailure).start();
    }

    private void notifyProgress(StartupProgressListener progressListener, StartupStage stage) {
        if (progressListener != null && stage != null) {
            progressListener.onStageChanged(stage);
        }
    }

    private MainFrame createAndInitializeMainFrameOnEdt() throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return createMainFrame();
        }

        MainFrame[] mainFrameHolder = new MainFrame[1];
        Throwable[] errorHolder = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                mainFrameHolder[0] = createMainFrame();
            } catch (Throwable throwable) {
                errorHolder[0] = throwable;
            }
        });

        if (errorHolder[0] != null) {
            if (errorHolder[0] instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(errorHolder[0]);
        }
        return mainFrameHolder[0];
    }

    private MainFrame createMainFrame() {
        log.info("Constructing main frame");
        MainFrame mainFrame = UiSingletonFactory.getInstance(MainFrame.class);
        log.info("Initializing main frame components");
        mainFrame.initComponents();
        log.info("Main frame components initialized");
        return mainFrame;
    }

    private void showMainFrameAndLoadContentOnEdt(MainFrame mainFrame) {
        showMainFrameOnEdt(mainFrame);
        log.info("Scheduling main content loading");
        // 先让轻量启动壳完成首帧显示，再切换到完整主内容，减少首屏阻塞。
        SwingUtilities.invokeLater(mainFrame::loadMainContentAsync);
    }

    private void showMainFrameOnEdt(MainFrame mainFrame) {
        log.info("Showing main frame on EDT");
        mainFrame.whenMainContentLoaded(() -> {
            AppLauncher.markStartupCheckpoint("main content ready");
            log.info("GUI main content ready in {} ms since launch entry",
                    (System.nanoTime() - launchStartedAtNanos) / 1_000_000);
        });
        mainFrame.setVisible(true);
        AppLauncher.markStartupCheckpoint("main frame made visible");
        AppSingleInstanceController.registerReadyMainFrame(mainFrame);
        mainFrame.toFront();
        mainFrame.requestFocus();
        log.info("Main frame is visible in {} ms since launch entry",
                (System.nanoTime() - launchStartedAtNanos) / 1_000_000);
    }

    public enum StartupStage {
        STARTING,
        LOADING_PLUGINS,
        LOADING_MAIN,
        READY
    }

    @FunctionalInterface
    public interface StartupProgressListener {
        void onStageChanged(StartupStage stage);
    }
}
