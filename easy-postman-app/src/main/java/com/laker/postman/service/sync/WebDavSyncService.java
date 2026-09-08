package com.laker.postman.service.sync;

import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.util.JsonUtil;
import com.laker.postman.util.SystemUtil;
import okhttp3.OkHttpClient;
import okhttp3.HttpUrl;
import okhttp3.Protocol;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.locks.ReentrantLock;

public class WebDavSyncService {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int WRITE_TIMEOUT_MS = 60_000;
    private static final ReentrantLock SYNC_LOCK = new ReentrantLock();

    private final Path dataRoot;
    private final WebDavSnapshotService snapshotService;
    private final WebDavClientFactory clientFactory;

    enum AutoUploadResult {
        UPLOADED,
        SKIPPED_BUSY,
        SKIPPED_REMOTE_NEWER,
        SKIPPED_REMOTE_TIMESTAMP_UNKNOWN
    }

    public WebDavSyncService() {
        this(
                Path.of(SystemUtil.getEasyPostmanPath()),
                new WebDavSnapshotService(),
                WebDavSyncService::createRuntimeClient
        );
    }

    WebDavSyncService(Path dataRoot,
                      WebDavSnapshotService snapshotService,
                      WebDavClientFactory clientFactory) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot").toAbsolutePath().normalize();
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
    }

    public void testConnection(WebDavSyncSettings settings) throws IOException {
        SYNC_LOCK.lock();
        try {
            createClient(validate(settings)).testConnection();
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    public Optional<WebDavRemoteSnapshot> fetchRemoteSnapshot(WebDavSyncSettings settings) throws IOException {
        SYNC_LOCK.lock();
        try {
            return fetchRemoteSnapshotInternal(validate(settings));
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    public void uploadSnapshot(WebDavSyncSettings settings) throws IOException {
        WebDavSyncSettings validatedSettings = validate(settings);
        SYNC_LOCK.lock();
        try {
            uploadSnapshotInternal(validatedSettings);
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    /**
     * Attempts a background upload without waiting for a manual sync already in progress.
     *
     * @return false when another WebDAV operation currently owns the sync lock
     */
    public boolean tryUploadSnapshot(WebDavSyncSettings settings) throws IOException {
        WebDavSyncSettings validatedSettings = validate(settings);
        if (!SYNC_LOCK.tryLock()) {
            return false;
        }
        try {
            uploadSnapshotInternal(validatedSettings);
            return true;
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    /**
     * Attempts an automatic upload while holding the same lock as manual sync operations.
     * A newer or unreadable remote manifest is treated as a conflict and never overwritten
     * by the background scheduler.
     */
    AutoUploadResult tryAutoUploadSnapshot(WebDavSyncSettings settings, long localLastSyncTime) throws IOException {
        WebDavSyncSettings validatedSettings = validate(settings);
        if (!SYNC_LOCK.tryLock()) {
            return AutoUploadResult.SKIPPED_BUSY;
        }
        try {
            Optional<WebDavRemoteSnapshot> remoteSnapshot = fetchRemoteSnapshotInternal(validatedSettings);
            if (remoteSnapshot.isPresent()) {
                OptionalLong remoteCreatedAt = remoteCreatedAt(remoteSnapshot.get());
                if (remoteCreatedAt.isEmpty()) {
                    return AutoUploadResult.SKIPPED_REMOTE_TIMESTAMP_UNKNOWN;
                }
                if (remoteCreatedAt.getAsLong() > localLastSyncTime) {
                    return AutoUploadResult.SKIPPED_REMOTE_NEWER;
                }
            }
            uploadSnapshotInternal(validatedSettings);
            return AutoUploadResult.UPLOADED;
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    private void uploadSnapshotInternal(WebDavSyncSettings validatedSettings) throws IOException {
        Path snapshot = Files.createTempFile("easypostman-webdav-upload-", ".zip");
        try {
            snapshotService.createSnapshot(dataRoot, snapshot);
            long snapshotBytes = Files.size(snapshot);
            WebDavClient client = createClient(validatedSettings);
            client.testConnection();
            client.uploadSnapshot(snapshot);
            client.uploadManifest(createManifest(snapshotBytes));
        } finally {
            Files.deleteIfExists(snapshot);
        }
    }

    public WebDavRestoreResult restoreSnapshot(WebDavSyncSettings settings) throws IOException {
        SYNC_LOCK.lock();
        try {
            WebDavClient client = createClient(validate(settings));
            Path snapshot = Files.createTempFile("easypostman-webdav-restore-", ".zip");
            try {
                client.downloadSnapshot(snapshot);
                return snapshotService.restoreSnapshot(snapshot, dataRoot);
            } finally {
                Files.deleteIfExists(snapshot);
            }
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    private WebDavClient createClient(WebDavSyncSettings settings) {
        return clientFactory.create(
                settings.serverUrl(),
                settings.remoteDirectory(),
                settings.username(),
                settings.password()
        );
    }

    private Optional<WebDavRemoteSnapshot> fetchRemoteSnapshotInternal(WebDavSyncSettings settings) throws IOException {
        WebDavClient client = createClient(settings);
        Optional<byte[]> manifest = client.downloadManifestIfPresent();
        if (manifest.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(WebDavRemoteSnapshot.fromJson(new String(manifest.get(), StandardCharsets.UTF_8)));
        } catch (RuntimeException e) {
            throw new IOException("Invalid WebDAV manifest", e);
        }
    }

    private static OptionalLong remoteCreatedAt(WebDavRemoteSnapshot snapshot) {
        try {
            return OptionalLong.of(Instant.parse(snapshot.createdAt()).toEpochMilli());
        } catch (RuntimeException e) {
            return OptionalLong.empty();
        }
    }

    private static WebDavClient createRuntimeClient(String serverUrl,
                                                    String remoteDirectory,
                                                    String username,
                                                    String password) {
        OkHttpClient okHttpClient = OkHttpClientManager.createDedicatedClientForUrl(
                serverUrl,
                true,
                CONNECT_TIMEOUT_MS,
                READ_TIMEOUT_MS,
                WRITE_TIMEOUT_MS,
                java.util.List.of(Protocol.HTTP_1_1)
        );
        return new WebDavClient(okHttpClient, serverUrl, remoteDirectory, username, password);
    }

    private static WebDavSyncSettings validate(WebDavSyncSettings settings) {
        WebDavSyncSettings normalized = settings == null
                ? new WebDavSyncSettings(false, "", WebDavSyncSettings.DEFAULT_REMOTE_DIRECTORY, "", "")
                : settings;
        if (!normalized.hasEndpoint()) {
            throw new IllegalArgumentException("WebDAV server URL is required");
        }
        HttpUrl parsedUrl = HttpUrl.parse(normalized.serverUrl());
        if (parsedUrl == null || !isHttpScheme(parsedUrl.scheme())) {
            throw new IllegalArgumentException("Invalid WebDAV server URL");
        }
        return normalized;
    }

    private static boolean isHttpScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static String createManifest(long snapshotBytes) {
        ObjectNode root = JsonUtil.createJsonNode();
        root.put("schemaVersion", 1);
        root.put("createdAt", Instant.now().toString());
        root.put("appVersion", SystemUtil.getCurrentVersion());
        root.put("snapshotFile", "snapshot.zip");
        root.put("snapshotBytes", snapshotBytes);
        return JsonUtil.toJsonPrettyStr(root);
    }

    @FunctionalInterface
    interface WebDavClientFactory {
        WebDavClient create(String serverUrl, String remoteDirectory, String username, String password);
    }
}
