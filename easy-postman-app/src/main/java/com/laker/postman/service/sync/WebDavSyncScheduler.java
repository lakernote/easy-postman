package com.laker.postman.service.sync;

import com.laker.postman.ioc.Component;
import com.laker.postman.ioc.PreDestroy;
import com.laker.postman.service.setting.SettingManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Schedules WebDAV uploads while the application is running.
 * The scheduler deliberately does not show UI notifications: background failures are
 * recorded in the application log and can be investigated without interrupting work.
 */
@Slf4j
@Component
public class WebDavSyncScheduler {
    private final WebDavSyncService syncService;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;
    private boolean started;

    public WebDavSyncScheduler() {
        this(new WebDavSyncService(), newExecutor());
    }

    WebDavSyncScheduler(WebDavSyncService syncService, ScheduledExecutorService executor) {
        this.syncService = Objects.requireNonNull(syncService, "syncService");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        scheduleFromCurrentSettings();
    }

    /** Reloads the schedule after the WebDAV settings are saved. */
    public synchronized void reload() {
        if (started) {
            scheduleFromCurrentSettings();
        }
    }

    @PreDestroy
    public synchronized void stop() {
        cancelScheduledTask();
        executor.shutdownNow();
        started = false;
        log.debug("WebDAV auto-upload scheduler stopped");
    }

    private void scheduleFromCurrentSettings() {
        cancelScheduledTask();
        WebDavSyncSettings settings = SettingManager.getWebDavSyncSettings();
        if (!settings.enabled() || !settings.autoUploadEnabled() || !settings.hasEndpoint()) {
            log.info("WebDAV auto-upload is not scheduled: enabled={}, autoUploadEnabled={}, endpointConfigured={}",
                    settings.enabled(), settings.autoUploadEnabled(), settings.hasEndpoint());
            return;
        }

        long intervalMinutes = settings.autoUploadIntervalMinutes();
        scheduledTask = executor.scheduleWithFixedDelay(
                this::runAutoUpload,
                intervalMinutes,
                intervalMinutes,
                TimeUnit.MINUTES
        );
        log.info("WebDAV auto-upload scheduled every {} minutes", intervalMinutes);
    }

    private void runAutoUpload() {
        WebDavSyncSettings settings = SettingManager.getWebDavSyncSettings();
        if (!settings.enabled() || !settings.autoUploadEnabled() || !settings.hasEndpoint()) {
            log.info("Skipping WebDAV auto-upload because the current settings are disabled or incomplete");
            return;
        }

        try {
            WebDavSyncService.AutoUploadResult result = syncService.tryAutoUploadSnapshot(
                    settings,
                    SettingManager.getWebDavSyncLastSyncTime()
            );
            switch (result) {
                case SKIPPED_BUSY -> log.info(
                        "Skipping WebDAV auto-upload because another WebDAV operation is in progress"
                );
                case SKIPPED_REMOTE_NEWER -> log.warn(
                        "Skipping WebDAV auto-upload because the remote snapshot is newer than the local state"
                );
                case SKIPPED_REMOTE_TIMESTAMP_UNKNOWN -> log.warn(
                        "Skipping WebDAV auto-upload because the remote snapshot timestamp is missing or invalid"
                );
                case UPLOADED -> {
                    long timestamp = System.currentTimeMillis();
                    SettingManager.setWebDavSyncLastSyncTime(timestamp);
                    log.info("WebDAV auto-upload completed successfully at {}", timestamp);
                }
            }
        } catch (Exception e) {
            log.error("WebDAV auto-upload failed for {}", settings.serverUrl(), e);
        }
    }

    private void cancelScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    private static ScheduledExecutorService newExecutor() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "WebDavAutoUpload");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }
}
