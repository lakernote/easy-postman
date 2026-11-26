package com.laker.postman.service;

import com.laker.postman.ioc.Autowired;
import com.laker.postman.ioc.Component;
import com.laker.postman.ioc.PreDestroy;
import com.laker.postman.platform.update.model.UpdateInfo;
import com.laker.postman.service.update.AppUpdateCheckCoordinator;
import com.laker.postman.service.update.plugin.PluginUpdateCheckResult;
import com.laker.postman.service.update.plugin.PluginUpdateCheckCoordinator;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 版本更新服务
 */
@Slf4j
@Component
public class UpdateService {

    @Autowired
    private AppUpdateCheckCoordinator appUpdateCheckCoordinator;

    @Autowired
    private PluginUpdateCheckCoordinator pluginUpdateCheckCoordinator;

    private final AtomicBoolean manualCheckInProgress = new AtomicBoolean();

    /**
     * 启动时异步检查更新
     */
    public void checkUpdateOnStartup() {
        // 启动后台更新检查
        appUpdateCheckCoordinator.startBackgroundCheck();
        pluginUpdateCheckCoordinator.startBackgroundCheck();
    }

    /**
     * 手动检查更新（用于菜单调用）
     */
    public void checkUpdateManually() {
        if (!manualCheckInProgress.compareAndSet(false, true)) {
            NotificationCenter.showInfo(I18nUtil.getMessage(MessageKeys.UPDATE_CHECK_IN_PROGRESS));
            return;
        }
        // 网络请求可能耗时数秒，先给出即时反馈，避免用户以为点击无效
        NotificationCenter.showInfo(I18nUtil.getMessage(MessageKeys.UPDATE_CHECKING));
        try {
            CompletableFuture<PluginUpdateCheckResult> pluginUpdateFuture = pluginUpdateCheckCoordinator.checkForUpdateManually();
            appUpdateCheckCoordinator.checkForUpdateManually()
                    .thenCombine(pluginUpdateFuture, (updateInfo, pluginUpdateResult) -> {
                        handleManualCheckResult(updateInfo, pluginUpdateResult);
                        return null;
                    })
                    .whenComplete((ignored, throwable) -> manualCheckInProgress.set(false))
                    .exceptionally(throwable -> {
                        log.error("Manual update check failed", throwable);
                        return null;
                    });
        } catch (RuntimeException ex) {
            manualCheckInProgress.set(false);
            throw ex;
        }
    }

    private void handleManualCheckResult(UpdateInfo updateInfo, PluginUpdateCheckResult pluginUpdateResult) {
        boolean appNoUpdate = updateInfo != null && updateInfo.getStatus() == UpdateInfo.Status.NO_UPDATE;
        boolean pluginCheckSucceeded = pluginUpdateResult != null && !pluginUpdateResult.failed();
        boolean pluginHasUpdates = pluginUpdateResult != null && pluginUpdateResult.hasUpdates();

        if (appNoUpdate && pluginCheckSucceeded && !pluginHasUpdates) {
            NotificationCenter.showInfo(I18nUtil.getMessage(MessageKeys.UPDATE_ALREADY_LATEST_WITH_PLUGINS));
            return;
        }

        if (!(appNoUpdate && pluginHasUpdates)) {
            appUpdateCheckCoordinator.handleUpdateCheckResult(updateInfo, true);
        }
        pluginUpdateCheckCoordinator.handleManualCheckResult(pluginUpdateResult);
    }

}
