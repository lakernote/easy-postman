package com.laker.postman.service.update;

import com.laker.postman.ioc.Component;
import com.laker.postman.platform.update.UpdateCenter;
import com.laker.postman.platform.update.VersionChecker;
import com.laker.postman.platform.update.model.UpdateInfo;
import com.laker.postman.platform.update.model.UpdatePolicy;
import com.laker.postman.platform.update.model.UpdateTarget;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.util.SystemUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 应用更新检查协调器。
 * 每次程序启动时检查，根据配置频率和上次检查时间决定是否执行检查。
 */
@Slf4j
@Component
public class AppUpdateCheckCoordinator {

    private final VersionChecker versionChecker;
    private final UpdateUiController uiController;
    private final UpdateCenter updateCenter;

    /**
     * 专用于更新检查的线程池（单线程，确保更新检查串行执行）
     */
    private static final ScheduledExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "UpdateChecker");
        thread.setDaemon(true);
        return thread;
    });

    public AppUpdateCheckCoordinator() {
        this(new VersionChecker(SettingManager::getUpdateSourcePreference),
                new UpdateUiController(),
                AppUpdateCenter.get());
    }

    AppUpdateCheckCoordinator(UpdateUiController uiController, UpdateCenter updateCenter) {
        this(new VersionChecker(SettingManager::getUpdateSourcePreference), uiController, updateCenter);
    }

    AppUpdateCheckCoordinator(VersionChecker versionChecker,
                              UpdateUiController uiController,
                              UpdateCenter updateCenter) {
        this.versionChecker = Objects.requireNonNull(versionChecker, "versionChecker");
        this.uiController = Objects.requireNonNull(uiController, "uiController");
        this.updateCenter = Objects.requireNonNull(updateCenter, "updateCenter");
    }

    /**
     * 启动自动更新检查（应用启动时调用）
     * 根据上次检查时间和配置的频率决定是否需要检查更新
     */
    public void startBackgroundCheck() {
        String currentVersion = SystemUtil.getCurrentVersion();
        log.info("Current application version: {}", currentVersion);
        String osInfo = SystemUtil.getOsInfo();
        log.info("Detected operating system: \n{} ", osInfo);

        UPDATE_EXECUTOR.execute(() -> UpdateInstallerFileStore.forCurrentUser().cleanupStale());

        UpdatePolicy policy = updateCenter.policy(UpdateTarget.APP);
        if (!policy.enabled()) {
            log.info("Auto-update check is disabled");
            return;
        }

        long lastCheckTime = updateCenter.state(UpdateTarget.APP).lastCheckTimeMillis();
        long currentTime = System.currentTimeMillis();

        log.info("Update check frequency: {}, last check time: {}",
                policy.frequency().getCode(), lastCheckTime > 0 ? new Date(lastCheckTime) : "never");

        // 判断是否需要检查更新
        if (updateCenter.shouldCheck(UpdateTarget.APP, currentTime)) {
            log.info("Scheduling startup update check...");
            // 使用线程池异步执行，避免阻塞调用线程
            UPDATE_EXECUTOR.submit(() -> {
                try {
                    performUpdateCheck();
                } catch (Exception e) {
                    log.error("Unexpected error in update check task", e);
                }
            });
        } else {
            log.info("Skipping update check - not yet time according to frequency settings");
        }
    }

    /**
     * 手动检查更新（从菜单触发）
     * 注意：手动检查也会更新"上次检查时间"
     */
    public CompletableFuture<UpdateInfo> checkForUpdateManually() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Manual update check initiated");
            UpdateInfo updateInfo = versionChecker.checkForUpdate();
            // 手动检查也记录时间
            updateCenter.recordCheck(UpdateTarget.APP, System.currentTimeMillis());
            log.info("Manual update check completed, timestamp recorded");
            return updateInfo;
        }, UPDATE_EXECUTOR); // 使用统一的更新检查线程池
    }

    /**
     * 执行更新检查的核心逻辑
     */
    private void performUpdateCheck() {
        log.debug("Starting update check task...");
        try {
            UpdateInfo updateInfo = versionChecker.checkForUpdate();
            // 记录检查时间
            updateCenter.recordCheck(UpdateTarget.APP, System.currentTimeMillis());
            log.info("Update check completed successfully, timestamp recorded");

            handleUpdateCheckResult(updateInfo, false);

        } catch (Exception e) {
            log.warn("Update check failed: {}", e.getMessage(), e);
            // 即使失败也记录检查时间，避免频繁重试
            updateCenter.recordCheck(UpdateTarget.APP, System.currentTimeMillis());
        }
    }

    /**
     * 处理更新检查结果
     *
     * @param updateInfo 更新信息
     * @param isManual   是否是手动检查
     */
    public void handleUpdateCheckResult(UpdateInfo updateInfo, boolean isManual) {
        if (updateInfo == null) {
            log.warn("Ignoring empty update check result");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            String marker = notificationMarker(updateInfo);
            if (!shouldNotify(updateInfo, updateCenter.ignoredMarkers(UpdateTarget.APP), isManual)) {
                log.info("Skipping app update notification because marker was ignored: {}", marker);
                return;
            }
            switch (updateInfo.getStatus()) {
                case UPDATE_AVAILABLE -> {
                    log.info("Update available: {} -> {}",
                            updateInfo.getCurrentVersion(), updateInfo.getLatestVersion());

                    if (isManual) {
                        // 手动检查直接显示对话框
                        uiController.showUpdateDialog(updateInfo, () -> rememberIgnoredMarker(updateInfo));
                    } else {
                        // 后台检查显示通知
                        uiController.showUpdateNotification(updateInfo, () -> rememberIgnoredMarker(updateInfo));
                    }
                }
                case UPDATE_AVAILABLE_NO_ASSET -> {
                    log.info("Update available but no asset for current platform: {} -> {}",
                            updateInfo.getCurrentVersion(), updateInfo.getLatestVersion());
                    if (isManual) {
                        // 手动检查：直接显示 NoAsset 对话框
                        uiController.showNoAssetDialog(updateInfo);
                    } else {
                        // 后台检查：先显示右下角 toast，点击后再弹对话框
                        uiController.showNoAssetNotification(updateInfo);
                    }
                }
                case NO_UPDATE -> {
                    if (isManual) {
                        // 只有手动检查时才显示"已是最新版本"
                        showNoUpdateMessage(updateInfo);
                    }
                    log.debug("No update available: {}", updateInfo.getMessage());
                }
                case CHECK_FAILED -> {
                    if (isManual) {
                        // 只有手动检查时才显示错误信息
                        showCheckFailedMessage(updateInfo);
                    }
                    log.debug("Update check failed: {}", updateInfo.getMessage());
                }
            }
        });
    }

    static boolean shouldNotify(UpdateInfo updateInfo, Set<String> ignoredMarkers, boolean isManual) {
        if (updateInfo == null) {
            return false;
        }
        String marker = notificationMarker(updateInfo);
        return marker.isEmpty()
                || isManual
                || ignoredMarkers == null
                || !ignoredMarkers.contains(marker.trim());
    }

    static String notificationMarker(UpdateInfo updateInfo) {
        if (updateInfo == null
                || updateInfo.getLatestVersion() == null
                || updateInfo.getLatestVersion().isBlank()
                || !(updateInfo.isUpdateAvailable() || updateInfo.isUpdateAvailableNoAsset())) {
            return "";
        }
        return UpdateTarget.APP.getId() + "@" + updateInfo.getLatestVersion().trim() + "@" + updateInfo.getStatus();
    }

    private void rememberIgnoredMarker(UpdateInfo updateInfo) {
        updateCenter.rememberIgnoredMarker(UpdateTarget.APP, notificationMarker(updateInfo));
    }

    /**
     * 显示无更新消息
     */
    private void showNoUpdateMessage(UpdateInfo updateInfo) {
        NotificationCenter.showInfo(updateInfo.getMessage());
    }

    /**
     * 显示检查失败消息
     */
    private void showCheckFailedMessage(UpdateInfo updateInfo) {
        NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.ERROR_UPDATE_FAILED, updateInfo.getMessage()));
    }

}
