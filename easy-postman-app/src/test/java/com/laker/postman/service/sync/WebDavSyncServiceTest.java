package com.laker.postman.service.sync;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.testng.Assert.*;

public class WebDavSyncServiceTest {

    @Test
    public void uploadSnapshotShouldCreateRemoteDirectoryAndUploadSnapshotWithManifest() throws Exception {
        Path dataRoot = Files.createTempDirectory("webdav-sync-upload");
        write(dataRoot.resolve("workspaces/default/collections.json"), "{\"ok\":true}");

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(207));
            server.enqueue(new MockResponse().setResponseCode(201));
            server.enqueue(new MockResponse().setResponseCode(201));
            server.start();

            WebDavSyncService service = newService(dataRoot);
            WebDavSyncSettings settings = new WebDavSyncSettings(
                    true,
                    server.url("/dav/").toString(),
                    "EasyPostman",
                    "alice",
                    "secret"
            );

            service.uploadSnapshot(settings);

            RecordedRequest propfind = server.takeRequest();
            assertEquals(propfind.getMethod(), "PROPFIND");
            assertEquals(propfind.getPath(), "/dav/EasyPostman/");

            RecordedRequest snapshotPut = server.takeRequest();
            assertEquals(snapshotPut.getMethod(), "PUT");
            assertEquals(snapshotPut.getPath(), "/dav/EasyPostman/snapshot.zip");
            assertEquals(snapshotPut.getHeader("Authorization"), Credentials.basic("alice", "secret"));
            assertTrue(isZip(snapshotPut.getBody().readByteArray()));

            RecordedRequest manifestPut = server.takeRequest();
            assertEquals(manifestPut.getMethod(), "PUT");
            assertEquals(manifestPut.getPath(), "/dav/EasyPostman/manifest.json");
            assertTrue(manifestPut.getBody().readUtf8().contains("\"snapshotFile\""));
        }
    }

    @Test
    public void restoreSnapshotShouldDownloadSnapshotAndCreateLocalBackup() throws Exception {
        Path sourceRoot = Files.createTempDirectory("webdav-sync-restore-source");
        write(sourceRoot.resolve("workspaces/default/collections.json"), "{\"remote\":true}");
        Path snapshot = Files.createTempFile("webdav-sync-restore", ".zip");
        new WebDavSnapshotService().createSnapshot(sourceRoot, snapshot);

        Path targetRoot = Files.createTempDirectory("webdav-sync-restore-target");
        write(targetRoot.resolve("workspaces/default/collections.json"), "{\"local\":true}");

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(new okio.Buffer().write(Files.readAllBytes(snapshot))));
            server.start();

            WebDavSyncService service = newService(targetRoot);
            WebDavRestoreResult result = service.restoreSnapshot(new WebDavSyncSettings(
                    true,
                    server.url("/dav/").toString(),
                    "EasyPostman",
                    "",
                    ""
            ));

            RecordedRequest request = server.takeRequest();
            assertEquals(request.getMethod(), "GET");
            assertEquals(request.getPath(), "/dav/EasyPostman/snapshot.zip");
            assertTrue(Files.exists(result.backupPath()));
            assertTrue(Files.readString(targetRoot.resolve("workspaces/default/collections.json"))
                    .contains("remote"));
        }
    }

    @Test
    public void fetchRemoteSnapshotShouldParseManifestWhenPresent() throws Exception {
        Path dataRoot = Files.createTempDirectory("webdav-sync-manifest");
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("""
                            {
                                "schemaVersion": 1,
                                "createdAt": "2026-06-22T08:00:00Z",
                                "appVersion": "v1.2.3",
                                "snapshotFile": "snapshot.zip",
                                "snapshotBytes": 12345
                            }
                            """));
            server.start();

            WebDavSyncService service = newService(dataRoot);
            Optional<WebDavRemoteSnapshot> remote = service.fetchRemoteSnapshot(new WebDavSyncSettings(
                    true,
                    server.url("/dav/").toString(),
                    "EasyPostman",
                    "",
                    ""
            ));

            assertTrue(remote.isPresent());
            assertEquals(remote.get().createdAt(), "2026-06-22T08:00:00Z");
            assertEquals(remote.get().appVersion(), "v1.2.3");
            assertEquals(remote.get().snapshotBytes(), 12345L);
            RecordedRequest request = server.takeRequest();
            assertEquals(request.getMethod(), "GET");
            assertEquals(request.getPath(), "/dav/EasyPostman/manifest.json");
        }
    }

    @Test
    public void fetchRemoteSnapshotShouldReturnEmptyWhenManifestIsMissing() throws Exception {
        Path dataRoot = Files.createTempDirectory("webdav-sync-manifest-missing");
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();

            WebDavSyncService service = newService(dataRoot);
            Optional<WebDavRemoteSnapshot> remote = service.fetchRemoteSnapshot(new WebDavSyncSettings(
                    true,
                    server.url("/dav/").toString(),
                    "EasyPostman",
                    "",
                    ""
            ));

            assertTrue(remote.isEmpty());
        }
    }

    @Test
    public void autoUploadShouldNotOverwriteNewerRemoteSnapshot() throws Exception {
        Path dataRoot = Files.createTempDirectory("webdav-sync-auto-upload-conflict");
        write(dataRoot.resolve("workspaces/default/collections.json"), "{\"local\":true}");
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("""
                            {
                                "schemaVersion": 1,
                                "createdAt": "2026-06-22T08:00:00Z",
                                "appVersion": "v1.2.3",
                                "snapshotFile": "snapshot.zip",
                                "snapshotBytes": 12345
                            }
                            """));
            server.start();

            WebDavSyncService service = newService(dataRoot);
            WebDavSyncService.AutoUploadResult result = service.tryAutoUploadSnapshot(
                    new WebDavSyncSettings(true, server.url("/dav/").toString(), "EasyPostman", "", ""),
                    Instant.parse("2026-06-22T07:00:00Z").toEpochMilli()
            );

            assertEquals(result, WebDavSyncService.AutoUploadResult.SKIPPED_REMOTE_NEWER);
            assertEquals(server.getRequestCount(), 1);
            RecordedRequest request = server.takeRequest();
            assertEquals(request.getMethod(), "GET");
            assertEquals(request.getPath(), "/dav/EasyPostman/manifest.json");
        }
    }

    private static WebDavSyncService newService(Path dataRoot) {
        return new WebDavSyncService(
                dataRoot,
                new WebDavSnapshotService(),
                (serverUrl, remoteDirectory, username, password) -> new WebDavClient(
                        new OkHttpClient(),
                        serverUrl,
                        remoteDirectory,
                        username,
                        password
                )
        );
    }

    private static boolean isZip(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            return zip.getNextEntry() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
