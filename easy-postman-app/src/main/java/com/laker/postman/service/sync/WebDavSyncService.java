package com.laker.postman.service.sync;

import lombok.extern.slf4j.Slf4j;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.request.util.HttpUrlUtil;
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
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
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
            uploadSnapshotInternal(validatedSettings, newOperationId());
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
            uploadSnapshotInternal(validatedSettings, newOperationId());
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
            String operationId = newOperationId();
            Optional<WebDavRemoteSnapshot> remoteSnapshot = fetchRemoteSnapshotInternal(validatedSettings, operationId);
            if (remoteSnapshot.isPresent()) {
                OptionalLong remoteCreatedAt = remoteCreatedAt(remoteSnapshot.get());
                if (remoteCreatedAt.isEmpty()) {
                    return AutoUploadResult.SKIPPED_REMOTE_TIMESTAMP_UNKNOWN;
                }
                if (remoteCreatedAt.getAsLong() > localLastSyncTime) {
                    return AutoUploadResult.SKIPPED_REMOTE_NEWER;
                }
            }
            uploadSnapshotInternal(validatedSettings, operationId);
            return AutoUploadResult.UPLOADED;
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    private void uploadSnapshotInternal(WebDavSyncSettings validatedSettings, String operationId) throws IOException {
        long startedAt = System.nanoTime();
        String stage = "create_snapshot";
        long snapshotBytes = -1;
        Path snapshot = null;
        log.info("WebDAV upload started: operationId={}, endpoint={}", operationId, safeServerEndpoint(validatedSettings));
        try {
            snapshot = Files.createTempFile("easypostman-webdav-upload-", ".zip");
            snapshotService.createSnapshot(dataRoot, snapshot);
            snapshotBytes = Files.size(snapshot);
            log.info("WebDAV upload snapshot created: operationId={}, bytes={}", operationId, snapshotBytes);
            WebDavClient client = createClient(validatedSettings, operationId);
            stage = "check_remote_directory";
            client.testConnection();
            stage = "upload_snapshot_archive";
            client.uploadSnapshot(snapshot);
            stage = "upload_manifest";
            String manifest = createManifest(snapshotBytes);
            log.info("WebDAV upload manifest prepared: operationId={}, schemaVersion=1, appVersion={}, snapshotBytes={}",
                    operationId, SystemUtil.getCurrentVersion(), snapshotBytes);
            client.uploadManifest(manifest);
            log.info("WebDAV upload completed: operationId={}, snapshotBytes={}, elapsedMs={}",
                    operationId, snapshotBytes, elapsedMillis(startedAt));
        } catch (IOException | RuntimeException e) {
            log.error("WebDAV upload failed: operationId={}, stage={}, snapshotBytes={}, elapsedMs={}",
                    operationId, stage, snapshotBytes, elapsedMillis(startedAt), e);
            throw e;
        } finally {
            if (snapshot != null) {
                Files.deleteIfExists(snapshot);
            }
        }
    }

    public WebDavRestoreResult restoreSnapshot(WebDavSyncSettings settings) throws IOException {
        SYNC_LOCK.lock();
        String operationId = newOperationId();
        long startedAt = System.nanoTime();
        String stage = "load_manifest";
        try {
            try {
                log.info("WebDAV restore started: operationId={}, endpoint={}", operationId, safeServerEndpoint(settings));
                WebDavSyncSettings validatedSettings = validate(settings);
                WebDavClient client = createClient(validatedSettings, operationId);
                Optional<WebDavRemoteSnapshot> remoteSnapshot = fetchRemoteSnapshotInternal(validatedSettings, operationId);
                if (remoteSnapshot.isPresent()) {
                    WebDavRemoteSnapshot manifest = remoteSnapshot.get();
                    log.info(
                            "WebDAV restore manifest loaded: operationId={}, schemaVersion={}, createdAt={}, appVersion={}, expectedBytes={}",
                            operationId, manifest.schemaVersion(), manifest.createdAt(), manifest.appVersion(), manifest.snapshotBytes()
                    );
                } else {
                    log.info("WebDAV restore manifest is missing: operationId={}, continuing without a size check", operationId);
                }
                Path snapshot = Files.createTempFile("easypostman-webdav-restore-", ".zip");
                try {
                    stage = "download_snapshot_archive";
                    client.downloadSnapshot(snapshot);
                    long downloadedBytes = Files.size(snapshot);
                    log.info("WebDAV restore snapshot downloaded: operationId={}, bytes={}", operationId, downloadedBytes);
                    if (remoteSnapshot.isPresent() && remoteSnapshot.get().snapshotBytes() > 0) {
                        long expectedBytes = remoteSnapshot.get().snapshotBytes();
                        if (downloadedBytes != expectedBytes) {
                            log.warn("WebDAV restore snapshot size mismatch: operationId={}, expectedBytes={}, actualBytes={}",
                                    operationId, expectedBytes, downloadedBytes);
                            throw WebDavSnapshotValidationException.sizeMismatch(expectedBytes, downloadedBytes);
                        }
                    }
                    stage = "validate_and_apply_snapshot";
                    WebDavRestoreResult result = snapshotService.restoreSnapshot(snapshot, dataRoot, operationId);
                    log.info("WebDAV restore completed: operationId={}, backupPath={}, snapshotBytes={}, elapsedMs={}",
                            operationId, result.backupPath(), downloadedBytes, elapsedMillis(startedAt));
                    return result;
                } finally {
                    Files.deleteIfExists(snapshot);
                }
            } catch (IOException | RuntimeException e) {
                log.error("WebDAV restore failed: operationId={}, stage={}, elapsedMs={}",
                        operationId, stage, elapsedMillis(startedAt), e);
                throw e;
            }
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    private WebDavClient createClient(WebDavSyncSettings settings) {
        return createClient(settings, newOperationId());
    }

    private WebDavClient createClient(WebDavSyncSettings settings, String operationId) {
        return clientFactory.create(
                settings.serverUrl(),
                settings.remoteDirectory(),
                settings.username(),
                settings.password(),
                operationId
        );
    }

    private Optional<WebDavRemoteSnapshot> fetchRemoteSnapshotInternal(WebDavSyncSettings settings) throws IOException {
        return fetchRemoteSnapshotInternal(settings, newOperationId());
    }

    private Optional<WebDavRemoteSnapshot> fetchRemoteSnapshotInternal(WebDavSyncSettings settings,
                                                                       String operationId) throws IOException {
        WebDavClient client = createClient(settings, operationId);
        Optional<byte[]> manifest = client.downloadManifestIfPresent();
        if (manifest.isEmpty()) {
            log.info("WebDAV remote snapshot manifest not found: operationId={}", operationId);
            return Optional.empty();
        }
        try {
            WebDavRemoteSnapshot snapshot = WebDavRemoteSnapshot.fromJson(
                    new String(manifest.get(), StandardCharsets.UTF_8)
            );
            log.info("WebDAV remote snapshot manifest parsed: operationId={}, manifestBytes={}, schemaVersion={}, " +
                            "createdAt={}, appVersion={}, snapshotBytes={}",
                    operationId, manifest.get().length, snapshot.schemaVersion(), snapshot.createdAt(),
                    snapshot.appVersion(), snapshot.snapshotBytes());
            return Optional.of(snapshot);
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
        HttpUrl parsedUrl = HttpUrl.parse(HttpUrlUtil.normalizeIpv6Url(normalized.serverUrl()));
        if (parsedUrl == null || !isHttpScheme(parsedUrl.scheme())) {
            throw new IllegalArgumentException("Invalid WebDAV server URL");
        }
        return normalized;
    }

    private static boolean isHttpScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static String newOperationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private static String safeServerEndpoint(WebDavSyncSettings settings) {
        if (settings == null || settings.serverUrl() == null || settings.serverUrl().isBlank()) {
            return "<unconfigured>";
        }
        HttpUrl parsedUrl = HttpUrl.parse(HttpUrlUtil.normalizeIpv6Url(settings.serverUrl().trim()));
        if (parsedUrl == null) {
            return "<invalid-url>";
        }
        return parsedUrl.newBuilder()
                .username("")
                .password("")
                .query(null)
                .fragment(null)
                .build()
                .toString();
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

        default WebDavClient create(String serverUrl, String remoteDirectory, String username, String password,
                                    String operationId) {
            return create(serverUrl, remoteDirectory, username, password).withOperationId(operationId);
        }
    }
}
