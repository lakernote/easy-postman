package com.laker.postman.service.sync;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.*;

public class WebDavSnapshotServiceTest {

    @Test
    public void snapshotShouldIncludeWorkspaceDataAndCoreSettingsOnly() throws Exception {
        Path dataRoot = Files.createTempDirectory("webdav-snapshot-source");
        write(dataRoot.resolve("workspaces/default/collections.json"), "{}");
        write(dataRoot.resolve("workspaces/default/environments.json"), "{}");
        write(dataRoot.resolve("workspaces/default/.git/config"), "[core]");
        write(dataRoot.resolve("workspaces/default/session.tmp"), "temp");
        write(dataRoot.resolve("workspaces/default/notes.md"), "workspace note");
        write(dataRoot.resolve("workspaces.json"), """
                [{"id":"default-workspace","name":"Default","path":"%s"}]
                """.formatted(dataRoot.resolve("workspaces/default").toString()));
        write(dataRoot.resolve("global_variables.json"), "{}");
        write(dataRoot.resolve("shortcuts.properties"), "send=ctrl ENTER\n");
        write(dataRoot.resolve("easy_postman_settings.properties"), """
                request_timeout=1200
                ui_font_size=14
                proxy_enabled=true
                proxy_host=127.0.0.1
                last_update_check_time=12345
                webdav_sync_password=secret
                csv_last_import_directory=/tmp/imports
                custom_trust_material_enabled=true
                custom_trust_material_entries=[{"path":"/tmp/ca.pem","password":"secret"}]
                """);
        write(dataRoot.resolve("user_settings.json"), """
                {"language":"zh","ui.theme":"dark","windowWidth":1600,"windowHeight":900}
                """);
        write(dataRoot.resolve("request_history.json"), "[]");
        write(dataRoot.resolve("opened_requests.json"), "[]");
        write(dataRoot.resolve("workspace_settings.json"), "{\"currentWorkspaceId\":\"default-workspace\"}");
        write(dataRoot.resolve("plugins/settings.json"), "{}");
        write(dataRoot.resolve("logs/app.log"), "log");
        write(dataRoot.resolve("backups/old.zip"), "backup");
        write(dataRoot.resolve("capture-ca/easy-postman-capture-root-ca.key"), "key");

        Path snapshot = Files.createTempFile("webdav-snapshot", ".zip");
        new WebDavSnapshotService().createSnapshot(dataRoot, snapshot);

        Map<String, String> entries = zipEntries(snapshot);
        assertTrue(entries.containsKey("workspaces/default/collections.json"));
        assertTrue(entries.containsKey("workspaces/default/environments.json"));
        assertTrue(entries.containsKey("workspaces/default/notes.md"));
        assertTrue(entries.containsKey("workspaces.json"));
        assertTrue(entries.containsKey("global_variables.json"));
        assertTrue(entries.containsKey("shortcuts.properties"));
        assertTrue(entries.containsKey("easy_postman_settings.properties"));
        assertTrue(entries.containsKey("user_settings.json"));

        assertFalse(entries.containsKey("workspaces/default/.git/config"));
        assertFalse(entries.containsKey("workspaces/default/session.tmp"));
        assertFalse(entries.containsKey("request_history.json"));
        assertFalse(entries.containsKey("opened_requests.json"));
        assertFalse(entries.containsKey("workspace_settings.json"));
        assertFalse(entries.containsKey("plugins/settings.json"));
        assertFalse(entries.containsKey("logs/app.log"));
        assertFalse(entries.containsKey("backups/old.zip"));
        assertFalse(entries.containsKey("capture-ca/easy-postman-capture-root-ca.key"));

        Properties settings = properties(entries.get("easy_postman_settings.properties"));
        assertEquals(settings.getProperty("request_timeout"), "1200");
        assertEquals(settings.getProperty("ui_font_size"), "14");
        assertFalse(settings.containsKey("proxy_enabled"));
        assertFalse(settings.containsKey("proxy_host"));
        assertFalse(settings.containsKey("last_update_check_time"));
        assertFalse(settings.containsKey("webdav_sync_password"));
        assertFalse(settings.containsKey("csv_last_import_directory"));
        assertFalse(settings.containsKey("custom_trust_material_enabled"));
        assertFalse(settings.containsKey("custom_trust_material_entries"));

        assertEquals(entries.get("user_settings.json").replaceAll("\\s+", ""),
                "{\"language\":\"zh\",\"ui.theme\":\"dark\"}");
        assertTrue(entries.get("workspaces.json").contains("$EASY_POSTMAN_DATA$/workspaces/default/"));
    }

    @Test
    public void restoreShouldRewritePortableWorkspacePathsAndCreateLocalBackup() throws Exception {
        Path sourceRoot = Files.createTempDirectory("webdav-snapshot-restore-source");
        write(sourceRoot.resolve("workspaces/default/collections.json"), "{\"source\":true}");
        write(sourceRoot.resolve("workspaces.json"), """
                [{"id":"default-workspace","name":"Default","path":"%s"}]
                """.formatted(sourceRoot.resolve("workspaces/default").toString()));
        Path snapshot = Files.createTempFile("webdav-snapshot-restore", ".zip");
        WebDavSnapshotService service = new WebDavSnapshotService();
        service.createSnapshot(sourceRoot, snapshot);

        Path targetRoot = Files.createTempDirectory("webdav-snapshot-restore-target");
        write(targetRoot.resolve("workspaces/default/collections.json"), "{\"local\":true}");

        WebDavRestoreResult result = service.restoreSnapshot(snapshot, targetRoot);

        assertTrue(Files.exists(result.backupPath()), "restore should create a local backup before overwriting data");
        String restoredCollections = Files.readString(targetRoot.resolve("workspaces/default/collections.json"));
        assertTrue(restoredCollections.contains("source"));
        String restoredWorkspaces = Files.readString(targetRoot.resolve("workspaces.json"));
        assertTrue(restoredWorkspaces.contains(targetRoot.resolve("workspaces/default").toString()));
        assertFalse(restoredWorkspaces.contains("$EASY_POSTMAN_DATA$"));
    }

    @Test
    public void restoreShouldDowngradeGitWorkspacesBecauseGitMetadataIsNotSynced() throws Exception {
        Path snapshot = Files.createTempFile("webdav-snapshot-git-restore", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(snapshot))) {
            zip.putNextEntry(new ZipEntry("workspaces.json"));
            zip.write("""
                    [{
                      "id":"api",
                      "name":"API",
                      "type":"GIT",
                      "gitRepoSource":"INITIALIZED",
                      "path":"$EASY_POSTMAN_DATA$/workspaces/api/",
                      "gitRemoteUrl":"https://example.com/team/api.git",
                      "currentBranch":"main",
                      "remoteBranch":"origin/main",
                      "lastCommitId":"1234567890abcdef",
                      "gitUsername":"user",
                      "gitToken":"secret"
                    }]
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("workspaces/api/collections.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path targetRoot = Files.createTempDirectory("webdav-snapshot-git-restore-target");

        new WebDavSnapshotService().restoreSnapshot(snapshot, targetRoot);

        String restoredWorkspaces = Files.readString(targetRoot.resolve("workspaces.json"))
                .replaceAll("\\s+", "");
        assertTrue(restoredWorkspaces.contains("\"type\":\"LOCAL\""));
        assertFalse(restoredWorkspaces.contains("\"type\":\"GIT\""));
        assertFalse(restoredWorkspaces.contains("gitRemoteUrl"));
        assertFalse(restoredWorkspaces.contains("gitToken"));
    }

    @Test
    public void snapshotShouldDowngradeGitWorkspacesBecauseGitMetadataIsExcluded() throws Exception {
        Path dataRoot = Files.createTempDirectory("webdav-snapshot-git-source");
        write(dataRoot.resolve("workspaces/api/collections.json"), "{}");
        write(dataRoot.resolve("workspaces/api/.git/config"), "[core]");
        write(dataRoot.resolve("workspaces.json"), """
                [{
                  "id":"api",
                  "name":"API",
                  "type":"GIT",
                  "gitRepoSource":"INITIALIZED",
                  "path":"%s",
                  "gitRemoteUrl":"https://example.com/team/api.git",
                  "currentBranch":"main",
                  "lastCommitId":"1234567890abcdef"
                }]
                """.formatted(dataRoot.resolve("workspaces/api").toString()));

        Path snapshot = Files.createTempFile("webdav-snapshot-git", ".zip");
        new WebDavSnapshotService().createSnapshot(dataRoot, snapshot);

        String workspaceEntry = zipEntries(snapshot).get("workspaces.json").replaceAll("\\s+", "");
        assertTrue(workspaceEntry.contains("\"type\":\"LOCAL\""));
        assertFalse(workspaceEntry.contains("\"type\":\"GIT\""));
        assertFalse(workspaceEntry.contains("gitRemoteUrl"));
    }

    @Test
    public void snapshotShouldIncludeExternalWorkspacesAndRestoreThemUnderDataRoot() throws Exception {
        Path dataRoot = Files.createTempDirectory("webdav-snapshot-external-root");
        Path externalWorkspace = Files.createTempDirectory("webdav-snapshot-external-workspace");
        write(externalWorkspace.resolve("collections.json"), "{\"external\":true}");
        write(externalWorkspace.resolve(".git/config"), "[core]");
        write(dataRoot.resolve("workspaces.json"), """
                [{"id":"external workspace:1","name":"External","path":"%s"}]
                """.formatted(externalWorkspace.toString()));

        Path snapshot = Files.createTempFile("webdav-snapshot-external", ".zip");
        WebDavSnapshotService service = new WebDavSnapshotService();
        service.createSnapshot(dataRoot, snapshot);

        Map<String, String> entries = zipEntries(snapshot);
        assertTrue(entries.containsKey("workspaces/synced-external/external_workspace_1/collections.json"));
        assertFalse(entries.containsKey("workspaces/synced-external/external_workspace_1/.git/config"));
        assertTrue(entries.get("workspaces.json")
                .contains("$EASY_POSTMAN_DATA$/workspaces/synced-external/external_workspace_1/"));

        Path targetRoot = Files.createTempDirectory("webdav-snapshot-external-target");
        service.restoreSnapshot(snapshot, targetRoot);

        assertTrue(Files.exists(targetRoot.resolve("workspaces/synced-external/external_workspace_1/collections.json")));
        assertTrue(Files.readString(targetRoot.resolve("workspaces.json"))
                .contains(targetRoot.resolve("workspaces/synced-external/external_workspace_1").toString()));
    }

    @Test
    public void snapshotShouldAbortAndIdentifyOversizedExternalWorkspace() throws Exception {
        Path dataRoot = Files.createTempDirectory("webdav-snapshot-preflight-root");
        Path externalWorkspace = Files.createTempDirectory("webdav-snapshot-preflight-workspace");
        write(externalWorkspace.resolve("large-collection.json"), "12345");
        write(dataRoot.resolve("workspaces.json"), """
                [{"id":"documents-workspace","name":"Documents 工作区","path":"%s"}]
                """.formatted(externalWorkspace.toString()));

        WebDavSnapshotService service = new WebDavSnapshotService(
                new WebDavSnapshotPolicy(),
                4,
                100
        );
        Path snapshot = Files.createTempFile("webdav-snapshot-preflight", ".zip");

        try {
            service.createSnapshot(dataRoot, snapshot);
            fail("Expected oversized external workspace to be rejected");
        } catch (WebDavSnapshotPreflightException exception) {
            assertEquals(exception.workspaceName(), "Documents 工作区");
            assertEquals(exception.workspacePath(), externalWorkspace);
            assertTrue(exception.contentBytes() > exception.maxContentBytes());
            assertEquals(exception.maxFileCount(), 100L);
            assertTrue(exception.getMessage().contains(externalWorkspace.toString()));
        }
    }

    @Test
    public void restoreShouldKeepOnlyLatestThreeWebDavSyncBackups() throws Exception {
        Path sourceRoot = Files.createTempDirectory("webdav-snapshot-backup-source");
        write(sourceRoot.resolve("workspaces/default/collections.json"), "{\"source\":true}");
        Path snapshot = Files.createTempFile("webdav-snapshot-backup", ".zip");
        WebDavSnapshotService service = new WebDavSnapshotService();
        service.createSnapshot(sourceRoot, snapshot);

        Path targetRoot = Files.createTempDirectory("webdav-snapshot-backup-target");
        write(targetRoot.resolve("workspaces/default/collections.json"), "{\"local\":true}");
        Path backupDir = targetRoot.resolve("backups");
        Files.createDirectories(backupDir);
        Path oldSync1 = backup(backupDir, "sync-old-1.zip", 1_000L);
        Path oldSync2 = backup(backupDir, "sync-old-2.zip", 2_000L);
        Path oldSync3 = backup(backupDir, "sync-old-3.zip", 3_000L);
        Path oldSync4 = backup(backupDir, "sync-old-4.zip", 4_000L);
        Path manualBackup = backup(backupDir, "collections-123-20260617161101.json", 500L);

        WebDavRestoreResult result = service.restoreSnapshot(snapshot, targetRoot);

        List<Path> syncBackups = syncBackups(backupDir);
        assertEquals(syncBackups.size(), 3);
        assertTrue(syncBackups.contains(result.backupPath()));
        assertFalse(Files.exists(oldSync1));
        assertFalse(Files.exists(oldSync2));
        assertTrue(Files.exists(oldSync3));
        assertTrue(Files.exists(oldSync4));
        assertTrue(Files.exists(manualBackup), "cleanup must not remove non-WebDAV backups");
    }

    @Test
    public void restoreShouldNormalizeBackslashZipEntriesAcrossPlatforms() throws Exception {
        Path snapshot = Files.createTempFile("webdav-snapshot-backslash", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(snapshot))) {
            zip.putNextEntry(new ZipEntry("workspaces\\default\\collections.json"));
            zip.write("{\"remote\":true}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Path targetRoot = Files.createTempDirectory("webdav-snapshot-backslash-target");

        new WebDavSnapshotService().restoreSnapshot(snapshot, targetRoot);

        assertTrue(Files.exists(targetRoot.resolve("workspaces/default/collections.json")));
        assertTrue(Files.readString(targetRoot.resolve("workspaces/default/collections.json")).contains("remote"));
    }

    @Test
    public void snapshotShouldPreserveBinaryWorkspaceFiles() throws Exception {
        Path dataRoot = Files.createTempDirectory("webdav-snapshot-binary");
        byte[] binary = new byte[]{0, 1, 2, 3, (byte) 0xFE, (byte) 0xFF};
        Path binaryFile = dataRoot.resolve("workspaces/default/attachment.bin");
        Files.createDirectories(binaryFile.getParent());
        Files.write(binaryFile, binary);

        Path snapshot = Files.createTempFile("webdav-snapshot-binary", ".zip");
        new WebDavSnapshotService().createSnapshot(dataRoot, snapshot);

        assertEquals(zipEntryBytes(snapshot, "workspaces/default/attachment.bin"), binary);
    }

    @Test
    public void policyShouldRejectKnownLocalRuntimeState() throws IOException {
        Path dataRoot = Files.createTempDirectory("webdav-policy");
        WebDavSnapshotPolicy policy = new WebDavSnapshotPolicy();

        assertTrue(policy.shouldInclude(dataRoot, dataRoot.resolve("workspaces/api/collections.json")));
        assertTrue(policy.shouldInclude(dataRoot, dataRoot.resolve("global_variables.json")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("request_history.json")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("opened_requests.json")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("workspace_settings.json")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("client_certificates.json")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("plugins/data/plugin-redis/settings.json")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("logs/app.log")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("backups/sync.zip")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("capture-ca/root.key")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("workspaces/api/.git/config")));
    }

    @Test
    public void policyShouldRejectReservedDirectoriesCaseInsensitively() throws IOException {
        Path dataRoot = Files.createTempDirectory("webdav-policy-case");
        WebDavSnapshotPolicy policy = new WebDavSnapshotPolicy();

        assertFalse(policy.shouldDescend(dataRoot, dataRoot.resolve("workspaces/api/.GIT")));
        assertFalse(policy.shouldDescend(dataRoot, dataRoot.resolve("workspaces/api/Plugins")));
        assertFalse(policy.shouldDescend(dataRoot, dataRoot.resolve("workspaces/api/LOGS")));
        assertFalse(policy.shouldDescend(dataRoot, dataRoot.resolve("workspaces/api/Backups")));
        assertFalse(policy.shouldDescend(dataRoot, dataRoot.resolve("workspaces/api/CAPTURE-CA")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("workspaces/api/.GIT/config")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("workspaces/api/Plugins/settings.json")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("workspaces/api/LOGS/app.log")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("workspaces/api/Backups/sync.zip")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("workspaces/api/CAPTURE-CA/root.key")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("workspaces/api/thumbs.db")));
        assertFalse(policy.shouldInclude(dataRoot, dataRoot.resolve("workspaces/api/.ds_store")));
    }

    @Test
    public void policyShouldRejectDotPathSegmentsWhenRestoring() {
        WebDavSnapshotPolicy policy = new WebDavSnapshotPolicy();

        assertFalse(policy.shouldRestoreEntry("workspaces/../global_variables.json"));
        assertFalse(policy.shouldRestoreEntry("workspaces/.."));
        assertFalse(policy.shouldRestoreEntry("workspaces/default/./collections.json"));
        assertFalse(policy.shouldRestoreEntry("workspaces/default\\..\\collections.json"));
        assertTrue(policy.shouldRestoreEntry("workspaces/default/collections.json"));
    }

    @Test
    public void policyShouldRejectWindowsUnsafeEntryNamesWhenRestoring() {
        WebDavSnapshotPolicy policy = new WebDavSnapshotPolicy();

        assertFalse(policy.shouldRestoreEntry("workspaces/default/a:b.json"));
        assertFalse(policy.shouldRestoreEntry("workspaces/default/question?.json"));
        assertFalse(policy.shouldRestoreEntry("workspaces/default/CON"));
        assertFalse(policy.shouldRestoreEntry("workspaces/default/con.txt"));
        assertFalse(policy.shouldRestoreEntry("workspaces/default/name."));
        assertFalse(policy.shouldRestoreEntry("workspaces/default/name "));
        assertFalse(policy.shouldRestoreEntry("workspaces/default/bad\u0000name.json"));
        assertTrue(policy.shouldRestoreEntry("workspaces/default/safe-name.json"));
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static Path backup(Path backupDir, String fileName, long lastModifiedMillis) throws IOException {
        Path path = backupDir.resolve(fileName);
        Files.writeString(path, "backup", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(path, FileTime.fromMillis(lastModifiedMillis));
        return path;
    }

    private static List<Path> syncBackups(Path backupDir) throws IOException {
        try (var stream = Files.list(backupDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("sync-"))
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .toList();
        }
    }

    private static Map<String, String> zipEntries(Path zipPath) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return entries;
    }

    private static byte[] zipEntryBytes(Path zipPath, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            assertNotNull(entry);
            return zip.getInputStream(entry).readAllBytes();
        }
    }

    private static Properties properties(String content) throws IOException {
        Properties properties = new Properties();
        properties.load(new java.io.StringReader(content));
        return properties;
    }
}
