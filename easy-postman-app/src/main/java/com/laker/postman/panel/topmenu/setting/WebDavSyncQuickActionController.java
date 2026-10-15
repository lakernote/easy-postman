package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.service.sync.WebDavRemoteSnapshot;
import com.laker.postman.service.sync.WebDavSyncService;
import com.laker.postman.service.sync.WebDavSyncSettings;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;

import javax.swing.JButton;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direct WebDAV upload and restore actions for the top toolbar. */
@Slf4j
public class WebDavSyncQuickActionController {
    private final Component dialogParent;
    private final List<JButton> buttons;
    private final WebDavSyncService syncService = new WebDavSyncService();
    private final AtomicBoolean running = new AtomicBoolean();

    public WebDavSyncQuickActionController(Component dialogParent, JButton... buttons) {
        this.dialogParent = dialogParent;
        this.buttons = Arrays.asList(buttons);
    }

    public void uploadAllWorkspaces() {
        run(Action.UPLOAD);
    }

    public void restoreAllWorkspaces() {
        run(Action.RESTORE);
    }

    private void run(Action action) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        setButtonsEnabled(false);

        WebDavSyncSettings settings = SettingManager.getWebDavSyncSettings();
        if (!settings.enabled()) {
            fail(MessageKeys.SETTINGS_WEBDAV_SYNC_STATUS_DISABLED);
            return;
        }
        if (!settings.hasEndpoint()) {
            fail(MessageKeys.SETTINGS_WEBDAV_SYNC_VALIDATION_REQUIRED);
            return;
        }
        HttpUrl url = HttpUrl.parse(settings.serverUrl());
        if (url == null || !"http".equalsIgnoreCase(url.scheme()) && !"https".equalsIgnoreCase(url.scheme())) {
            fail(MessageKeys.SETTINGS_WEBDAV_SYNC_VALIDATION_URL);
            return;
        }
        if (WebDavSyncSettingsPanel.shouldWarnInsecureHttp(url)) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.SETTINGS_WEBDAV_SYNC_VALIDATION_HTTP_WARNING));
        }

        execute(() -> syncService.fetchRemoteSnapshot(settings), remote -> {
            if (confirm(action, remote)) {
                perform(action, settings);
            } else {
                finish();
            }
        });
    }

    private void perform(Action action, WebDavSyncSettings settings) {
        String statusKey = action == Action.UPLOAD
                ? MessageKeys.SETTINGS_WEBDAV_SYNC_STATUS_UPLOADING
                : MessageKeys.SETTINGS_WEBDAV_SYNC_STATUS_RESTORING;
        NotificationCenter.showInfo(I18nUtil.getMessage(statusKey));
        if (action == Action.UPLOAD) {
            execute(() -> {
                syncService.uploadSnapshot(settings);
                return Instant.now().toEpochMilli();
            }, timestamp -> {
                SettingManager.setWebDavSyncLastSyncTime(timestamp);
                NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.SETTINGS_WEBDAV_SYNC_UPLOAD_SUCCESS));
                finish();
            });
            return;
        }
        execute(() -> syncService.restoreSnapshot(settings), result -> {
            long timestamp = Instant.now().toEpochMilli();
            SettingManager.setWebDavSyncLastSyncTime(timestamp);
            WebDavSyncSettingsPanel.showRestoreRestartPrompt(dialogParent, result.backupPath().toString());
            finish();
        });
    }

    private boolean confirm(Action action, Optional<WebDavRemoteSnapshot> remoteSnapshot) {
        return action == Action.UPLOAD
                ? WebDavSyncConfirmationDialog.confirmUpload(dialogParent, remoteSnapshot)
                : WebDavSyncConfirmationDialog.confirmRestore(dialogParent, remoteSnapshot);
    }

    private <T> void execute(SyncTask<T> task, SyncSuccess<T> success) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.run();
            }

            @Override
            protected void done() {
                try {
                    success.accept(get());
                } catch (Exception ex) {
                    log.error("Top toolbar WebDAV action failed", ex);
                    NotificationCenter.showError(I18nUtil.getMessage(
                            MessageKeys.SETTINGS_WEBDAV_SYNC_ACTION_FAILED,
                            WebDavSyncSettingsPanel.actionErrorMessage(ex)
                    ));
                    finish();
                }
            }
        }.execute();
    }

    private void fail(String messageKey) {
        NotificationCenter.showError(I18nUtil.getMessage(messageKey));
        finish();
    }

    private void finish() {
        running.set(false);
        setButtonsEnabled(SettingManager.getWebDavSyncSettings().enabled());
    }

    private void setButtonsEnabled(boolean enabled) {
        buttons.forEach(button -> button.setEnabled(enabled));
    }

    @FunctionalInterface
    private interface SyncTask<T> {
        T run() throws Exception;
    }

    @FunctionalInterface
    private interface SyncSuccess<T> {
        void accept(T value);
    }

    private enum Action {
        UPLOAD,
        RESTORE
    }
}
