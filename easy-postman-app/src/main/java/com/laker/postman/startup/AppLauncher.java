package com.laker.postman.startup;

import ch.qos.logback.classic.LoggerContext;
import com.formdev.flatlaf.util.SystemInfo;
import com.laker.postman.common.themes.SimpleThemeManager;
import com.laker.postman.http.runtime.app.AppHttpRuntimeBootstrap;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.platform.instance.SingleInstanceCoordinator;
import com.laker.postman.plugin.runtime.PluginRuntime;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.AppRuntimeLayout;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.util.OptionalInt;

/**
 * 应用启动器：负责主入口之后的启动编排。
 */
@Slf4j
@UtilityClass
public class AppLauncher {
    public static final int GUI_STARTED = Integer.MIN_VALUE;
    private static volatile SingleInstanceCoordinator singleInstanceCoordinator;
    private static volatile boolean skipHostRuntimeShutdown;
    private static volatile String startupCheckpoint = "launcher initialized";

    static void markStartupCheckpoint(String checkpoint) {
        startupCheckpoint = checkpoint;
    }

    public int launch(String[] args) {
        long launchStartedAt = System.nanoTime();
        configureBaseRuntimeEnvironment();
        registerShutdownHook();
        OptionalInt commandExitCode = new AppCommandRouter().route(args, System.out, System.err);
        if (commandExitCode.isPresent()) {
            return commandExitCode.getAsInt();
        }
        markStartupCheckpoint("GUI launch selected");
        log.info("Starting GUI launch sequence: os={} {}, arch={}, java={} ({}), headless={}",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                java.awt.GraphicsEnvironment.isHeadless());
        OptionalInt singleInstanceExitCode = coordinateGuiInstance();
        if (singleInstanceExitCode.isPresent()) {
            return singleInstanceExitCode.getAsInt();
        }
        // 先完成平台和主题配置，再把组件创建切到 EDT。
        configurePlatformWindowDecorations();
        StartupCoordinator startupCoordinator = new StartupCoordinator(launchStartedAt);
        startupCoordinator.startHostIocInitialization();
        initializeLookAndFeel();
        markStartupCheckpoint("look and feel initialized");
        SwingUtilities.invokeLater(() -> startSwingApplication(startupCoordinator));
        return GUI_STARTED;
    }

    private OptionalInt coordinateGuiInstance() {
        markStartupCheckpoint("coordinating GUI instance");
        SingleInstanceCoordinator.LaunchResult result;
        try {
            result = SingleInstanceCoordinator.acquireOrNotify(
                    AppRuntimeLayout.dataRootDirectory(AppLauncher.class),
                    AppSingleInstanceController::requestActivation
            );
        } catch (IOException | RuntimeException exception) {
            skipHostRuntimeShutdown = true;
            StartupFailureHandler.showSingleInstanceSetupErrorAndExit(exception);
            return OptionalInt.of(GUI_STARTED);
        }

        if (result.isPrimary()) {
            singleInstanceCoordinator = result.coordinator();
            markStartupCheckpoint("primary GUI instance acquired");
            log.info("GUI instance coordination selected this process as primary");
            return OptionalInt.empty();
        }
        if (result.status() == SingleInstanceCoordinator.LaunchStatus.EXISTING_INSTANCE_NOTIFIED) {
            skipHostRuntimeShutdown = true;
            log.info("Activated the existing EasyPostman GUI instance");
            return OptionalInt.of(0);
        }

        skipHostRuntimeShutdown = true;
        StartupFailureHandler.showExistingInstanceUnavailableAndExit();
        return OptionalInt.of(GUI_STARTED);
    }

    private void configureBaseRuntimeEnvironment() {
        // 这些配置对 GUI 和 headless CLI 都有效，且不应触发 Swing 初始化。
        Thread.setDefaultUncaughtExceptionHandler(new AppUncaughtExceptionHandler());
        System.setProperty("java.net.useSystemProxies", "true");
        AppHttpRuntimeBootstrap.configure();
    }

    private void configurePlatformWindowDecorations() {
        if (!SystemInfo.isLinux) {
            return;
        }
        log.debug("Detected Linux platform, enabling custom window decorations");
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
    }

    private void startSwingApplication(StartupCoordinator startupCoordinator) {
        markStartupCheckpoint("Swing EDT initialization started");
        log.info("Starting Swing application initialization on EDT");
        startMainFrame(startupCoordinator);
    }

    /**
     * Look and feel 必须在 Swing 组件创建前完成，否则首屏组件会混用旧 UI defaults。
     */
    private void initializeLookAndFeel() {
        log.info("Initializing application look and feel");
        SimpleThemeManager.initTheme();
        log.info("Application look and feel initialized");
    }

    private void startMainFrame(StartupCoordinator startupCoordinator) {
        boolean splashEnabled = SettingManager.isStartupSplashEnabled();
        log.info("Starting main frame initialization: splashEnabled={}", splashEnabled);
        if (splashEnabled) {
            // Splash 模式下先显示轻量过渡窗口，主窗口 shell 准备完成后再切换。
            startWithSplash(startupCoordinator);
            return;
        }
        // IOC 与主题并行初始化；插件就绪后再创建并显示主窗口。
        new NoSplashStartupWorker(startupCoordinator).start();
    }

    private void startWithSplash(StartupCoordinator startupCoordinator) {
        SplashWindow splash = new SplashWindow();
        splash.init();
        splash.startMainFrameInitialization(startupCoordinator);
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                log.info("JVM shutdown hook started: lastStartupCheckpoint={}", startupCheckpoint);
                if (!skipHostRuntimeShutdown) {
                    // 退出时先关闭插件运行时，再销毁宿主 IOC，避免插件回调访问已释放的 Bean。
                    runShutdownStep("plugin runtime", PluginRuntime::shutdown);
                    runShutdownStep("host IOC container", BeanFactory::destroy);
                }
                runShutdownStep("single-instance coordinator", AppLauncher::closeSingleInstanceCoordinator);
                log.info("Application shutdown completed");
            } finally {
                // 最后停止日志上下文，触发 AsyncAppender 的有界 flush，保留关闭阶段日志。
                if (LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext) {
                    loggerContext.stop();
                }
            }
        }, "ShutdownHook"));
    }

    private void runShutdownStep(String resource, Runnable shutdown) {
        try {
            shutdown.run();
        } catch (Throwable e) {
            log.error("Error shutting down {}", resource, e);
        }
    }

    private void closeSingleInstanceCoordinator() {
        SingleInstanceCoordinator coordinator = singleInstanceCoordinator;
        singleInstanceCoordinator = null;
        if (coordinator != null) {
            coordinator.close();
        }
    }
}
