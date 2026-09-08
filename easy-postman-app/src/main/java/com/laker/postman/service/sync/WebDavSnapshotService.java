package com.laker.postman.service.sync;

import com.laker.postman.util.JsonUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class WebDavSnapshotService {
    static final long DEFAULT_MAX_EXTERNAL_WORKSPACE_BYTES = 100L * 1024L * 1024L;
    static final long DEFAULT_MAX_EXTERNAL_WORKSPACE_FILES = 1_000L;

    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String WORKSPACES_JSON = "workspaces.json";
    private static final String APP_SETTINGS = "easy_postman_settings.properties";
    private static final String USER_SETTINGS = "user_settings.json";
    private static final String MANAGED_WORKSPACES_DIR = "workspaces";
    private static final String EXTERNAL_WORKSPACES_DIR = "workspaces/synced-external";
    private static final int MAX_SYNC_BACKUP_COUNT = 3;
    private static final List<String> GIT_WORKSPACE_FIELDS = List.of(
            "gitRepoSource",
            "gitRemoteUrl",
            "currentBranch",
            "remoteBranch",
            "lastCommitId",
            "gitUsername",
            "gitPassword",
            "gitToken",
            "gitAuthType",
            "sshPrivateKeyPath",
            "sshPassphrase"
    );

    private final WebDavSnapshotPolicy policy;
    private final long maxExternalWorkspaceBytes;
    private final long maxExternalWorkspaceFiles;

    public WebDavSnapshotService() {
        this(new WebDavSnapshotPolicy(),
                DEFAULT_MAX_EXTERNAL_WORKSPACE_BYTES,
                DEFAULT_MAX_EXTERNAL_WORKSPACE_FILES);
    }

    WebDavSnapshotService(WebDavSnapshotPolicy policy) {
        this(policy, DEFAULT_MAX_EXTERNAL_WORKSPACE_BYTES, DEFAULT_MAX_EXTERNAL_WORKSPACE_FILES);
    }

    WebDavSnapshotService(WebDavSnapshotPolicy policy,
                          long maxExternalWorkspaceBytes,
                          long maxExternalWorkspaceFiles) {
        this.policy = policy;
        if (maxExternalWorkspaceBytes <= 0 || maxExternalWorkspaceFiles <= 0) {
            throw new IllegalArgumentException("WebDAV snapshot limits must be positive");
        }
        this.maxExternalWorkspaceBytes = maxExternalWorkspaceBytes;
        this.maxExternalWorkspaceFiles = maxExternalWorkspaceFiles;
    }

    public void createSnapshot(Path dataRoot, Path snapshotPath) throws IOException {
        Path normalizedRoot = dataRoot.toAbsolutePath().normalize();
        Path normalizedSnapshot = snapshotPath.toAbsolutePath().normalize();
        Path parent = normalizedSnapshot.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        WorkspaceSnapshotPlan workspacePlan = createWorkspaceSnapshotPlan(normalizedRoot);
        Set<String> writtenEntries = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(normalizedSnapshot))) {
            if (!Files.exists(normalizedRoot)) {
                return;
            }
            for (Path file : collectIncludedFiles(normalizedRoot, normalizedRoot, normalizedRoot)) {
                String entryName = policy.entryName(normalizedRoot, file);
                writeSnapshotEntry(zip, writtenEntries, entryName,
                        snapshotContent(normalizedRoot, entryName, file, workspacePlan));
            }
            writeExternalWorkspaceEntries(zip, writtenEntries, normalizedRoot, workspacePlan.externalWorkspaces());
        }
    }

    public WebDavRestoreResult restoreSnapshot(Path snapshotPath, Path dataRoot) throws IOException {
        Path normalizedRoot = dataRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        Path backupPath = createBackup(normalizedRoot);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(snapshotPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !policy.shouldRestoreEntry(entry.getName())) {
                    continue;
                }
                String entryName = policy.restoreEntryName(entry.getName());
                Path target = normalizedRoot.resolve(entryName).normalize();
                if (!target.startsWith(normalizedRoot)) {
                    continue;
                }
                byte[] content = zip.readAllBytes();
                if (WORKSPACES_JSON.equals(entryName)) {
                    content = restorePortableWorkspacePaths(new String(content, StandardCharsets.UTF_8), normalizedRoot)
                            .getBytes(StandardCharsets.UTF_8);
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(target, content);
            }
        }
        return new WebDavRestoreResult(backupPath);
    }

    private Path createBackup(Path dataRoot) throws IOException {
        Path backupDir = dataRoot.resolve("backups");
        Files.createDirectories(backupDir);
        Path backupPath = Files.createTempFile(
                backupDir,
                "sync-" + LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT) + "-",
                ".zip"
        );
        createSnapshot(dataRoot, backupPath);
        cleanupOldSyncBackups(backupDir, backupPath);
        return backupPath;
    }

    private void cleanupOldSyncBackups(Path backupDir, Path currentBackup) throws IOException {
        List<Path> syncBackups = new ArrayList<>();
        try (var stream = Files.list(backupDir)) {
            syncBackups.addAll(stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSyncBackup)
                    .toList());
        }
        if (syncBackups.size() <= MAX_SYNC_BACKUP_COUNT) {
            return;
        }
        syncBackups.sort(Comparator
                .comparingLong((Path path) -> backupSortTimestamp(path, currentBackup))
                .reversed()
                .thenComparing(path -> path.getFileName().toString()));
        for (int i = MAX_SYNC_BACKUP_COUNT; i < syncBackups.size(); i++) {
            Files.deleteIfExists(syncBackups.get(i));
        }
    }

    private boolean isSyncBackup(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        return fileName.startsWith("sync-") && fileName.endsWith(".zip");
    }

    private long backupSortTimestamp(Path path, Path currentBackup) {
        if (path.toAbsolutePath().normalize().equals(currentBackup.toAbsolutePath().normalize())) {
            return Long.MAX_VALUE;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private byte[] snapshotContent(Path dataRoot,
                                   String entryName,
                                   Path file,
                                   WorkspaceSnapshotPlan workspacePlan) throws IOException {
        if (!requiresTextTransform(entryName)) {
            return Files.readAllBytes(file);
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String transformed = switch (entryName) {
            case WORKSPACES_JSON -> workspacePlan.workspacesJson();
            case APP_SETTINGS -> filterApplicationSettings(content);
            case USER_SETTINGS -> filterUserSettings(content);
            default -> content;
        };
        return transformed.getBytes(StandardCharsets.UTF_8);
    }

    private boolean requiresTextTransform(String entryName) {
        return WORKSPACES_JSON.equals(entryName)
                || APP_SETTINGS.equals(entryName)
                || USER_SETTINGS.equals(entryName);
    }

    private String filterApplicationSettings(String content) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(content == null ? "" : content));
        TreeSet<String> keys = new TreeSet<>(properties.stringPropertyNames());
        StringBuilder filtered = new StringBuilder();
        for (String key : keys) {
            if (shouldSyncApplicationSetting(key)) {
                filtered.append(key).append('=').append(properties.getProperty(key, "")).append('\n');
            }
        }
        return filtered.toString();
    }

    private boolean shouldSyncApplicationSetting(String key) {
        return key != null
                && !key.startsWith("proxy_")
                && !key.startsWith("webdav_sync_")
                && !key.startsWith("plugin_update_")
                && !key.startsWith("custom_trust_material_")
                && !"csv_last_import_directory".equals(key)
                && !"last_update_check_time".equals(key)
                && !"app_update_ignored_markers".equals(key);
    }

    private String filterUserSettings(String content) {
        try {
            JsonNode root = JsonUtil.readTree(content == null || content.isBlank() ? "{}" : content);
            ObjectNode filtered = JsonUtil.createJsonNode();
            copyUserSetting(root, filtered, "language");
            copyUserSetting(root, filtered, "ui.theme");
            return JsonUtil.toJsonPrettyStr(filtered);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void copyUserSetting(JsonNode root, ObjectNode filtered, String key) {
        if (root != null && root.has(key)) {
            filtered.set(key, root.get(key));
        }
    }

    private WorkspaceSnapshotPlan createWorkspaceSnapshotPlan(Path dataRoot) throws IOException {
        Path workspacesFile = dataRoot.resolve(WORKSPACES_JSON);
        if (!Files.exists(workspacesFile)) {
            return new WorkspaceSnapshotPlan("", List.of());
        }
        String content = Files.readString(workspacesFile, StandardCharsets.UTF_8);
        return createWorkspaceSnapshotPlan(content, dataRoot);
    }

    private WorkspaceSnapshotPlan createWorkspaceSnapshotPlan(String content, Path dataRoot) throws IOException {
        List<ExternalWorkspace> externalWorkspaces = new ArrayList<>();
        Set<String> usedExternalNames = new HashSet<>();
        Path normalizedRoot = dataRoot.toAbsolutePath().normalize();
        Path managedWorkspacesRoot = normalizedRoot.resolve(MANAGED_WORKSPACES_DIR).normalize();
        String transformed = transformWorkspaceNodes(content, (objectNode, index, path) -> {
            try {
                Path workspacePath = Path.of(path).toAbsolutePath().normalize();
                if (workspacePath.startsWith(managedWorkspacesRoot)) {
                    String relative = normalizedRoot.relativize(workspacePath).toString().replace('\\', '/');
                    return WebDavSnapshotPolicy.DATA_ROOT_TOKEN + "/" + ensureTrailingSlash(relative);
                }
                if (!Files.isDirectory(workspacePath)) {
                    return path;
                }
                String snapshotName = uniqueExternalWorkspaceName(objectNode, index, usedExternalNames);
                String snapshotRoot = EXTERNAL_WORKSPACES_DIR + "/" + snapshotName;
                inspectExternalWorkspace(
                        workspaceText(objectNode, "name"),
                        workspacePath,
                        snapshotRoot,
                        dataRoot
                );
                externalWorkspaces.add(new ExternalWorkspace(workspacePath, snapshotRoot));
                return WebDavSnapshotPolicy.DATA_ROOT_TOKEN + "/" + ensureTrailingSlash(snapshotRoot);
            } catch (WebDavSnapshotPreflightException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                return path;
            }
        });
        return new WorkspaceSnapshotPlan(transformed, externalWorkspaces);
    }

    private String restorePortableWorkspacePaths(String content, Path dataRoot) throws IOException {
        return transformWorkspacePaths(content, path -> {
            String prefix = WebDavSnapshotPolicy.DATA_ROOT_TOKEN + "/";
            if (path == null || !path.startsWith(prefix)) {
                return path;
            }
            String relative = path.substring(prefix.length());
            return ensureTrailingSlash(dataRoot.resolve(relative).normalize().toString());
        });
    }

    private String transformWorkspacePaths(String content, WorkspacePathTransformer transformer) throws IOException {
        return transformWorkspaceNodes(content, (objectNode, index, path) -> transformer.transform(path));
    }

    private String transformWorkspaceNodes(String content, WorkspaceNodePathTransformer transformer) throws IOException {
        try {
            JsonNode root = JsonUtil.readTree(content == null || content.isBlank() ? "[]" : content);
            if (!(root instanceof ArrayNode arrayNode)) {
                return content;
            }
            int index = 0;
            for (JsonNode node : arrayNode) {
                if (node instanceof ObjectNode objectNode && objectNode.has("path")) {
                    normalizeWorkspaceForWebDavSnapshot(objectNode);
                    JsonNode pathNode = objectNode.get("path");
                    if (pathNode != null && pathNode.isTextual()) {
                        objectNode.put("path", transformer.transform(objectNode, index, pathNode.asText()));
                    }
                }
                index++;
            }
            return JsonUtil.toJsonPrettyStr(arrayNode);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            return content;
        }
    }

    private void normalizeWorkspaceForWebDavSnapshot(ObjectNode workspaceNode) {
        JsonNode typeNode = workspaceNode.get("type");
        if (typeNode == null || !typeNode.isTextual() || !"GIT".equalsIgnoreCase(typeNode.asText())) {
            return;
        }
        workspaceNode.put("type", "LOCAL");
        for (String field : GIT_WORKSPACE_FIELDS) {
            workspaceNode.remove(field);
        }
    }

    private String ensureTrailingSlash(String value) {
        if (value == null || value.isBlank() || value.endsWith("/") || value.endsWith("\\")) {
            return value;
        }
        return value + "/";
    }

    private void writeExternalWorkspaceEntries(ZipOutputStream zip,
                                               Set<String> writtenEntries,
                                               Path dataRoot,
                                               List<ExternalWorkspace> externalWorkspaces) throws IOException {
        for (ExternalWorkspace externalWorkspace : externalWorkspaces) {
            Path virtualRoot = dataRoot.resolve(externalWorkspace.snapshotRoot()).normalize();
            for (Path file : collectIncludedFiles(externalWorkspace.sourceRoot(), dataRoot, virtualRoot)) {
                Path relative = externalWorkspace.sourceRoot().relativize(file);
                String relativeEntryName = relative.toString().replace('\\', '/');
                String entryName = externalWorkspace.snapshotRoot() + "/" + relativeEntryName;
                writeSnapshotEntry(zip, writtenEntries, entryName, file);
            }
        }
    }

    private void inspectExternalWorkspace(String workspaceName,
                                          Path workspacePath,
                                          String snapshotRoot,
                                          Path dataRoot) throws IOException {
        long[] fileCount = {0};
        long[] contentBytes = {0};
        Path virtualRoot = dataRoot.resolve(snapshotRoot).normalize();
        String displayName = workspaceName == null || workspaceName.isBlank()
                ? workspacePath.getFileName().toString()
                : workspaceName;
        try {
            Files.walkFileTree(workspacePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    Path relative = workspacePath.relativize(directory);
                    Path virtualPath = virtualRoot.resolve(relative.toString()).normalize();
                    return policy.shouldDescend(dataRoot, virtualPath)
                            ? FileVisitResult.CONTINUE
                            : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (!Files.isRegularFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path relative = workspacePath.relativize(file);
                    Path virtualPath = virtualRoot.resolve(relative.toString()).normalize();
                    if (!policy.shouldInclude(dataRoot, virtualPath)) {
                        return FileVisitResult.CONTINUE;
                    }
                    fileCount[0]++;
                    contentBytes[0] = Math.addExact(contentBytes[0], Files.size(file));
                    if (fileCount[0] > maxExternalWorkspaceFiles || contentBytes[0] > maxExternalWorkspaceBytes) {
                        throw new WebDavSnapshotPreflightException(
                                displayName,
                                workspacePath,
                                fileCount[0],
                                contentBytes[0],
                                maxExternalWorkspaceBytes,
                                maxExternalWorkspaceFiles
                        );
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (ArithmeticException e) {
            throw new WebDavSnapshotPreflightException(
                    displayName,
                    workspacePath,
                    fileCount[0],
                    Long.MAX_VALUE,
                    maxExternalWorkspaceBytes,
                    maxExternalWorkspaceFiles
            );
        }
    }

    private List<Path> collectIncludedFiles(Path sourceRoot,
                                            Path dataRoot,
                                            Path virtualRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                Path relative = sourceRoot.relativize(directory);
                Path virtualPath = virtualRoot.resolve(relative.toString()).normalize();
                return policy.shouldDescend(dataRoot, virtualPath)
                        ? FileVisitResult.CONTINUE
                        : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (Files.isRegularFile(file)) {
                    Path relative = sourceRoot.relativize(file);
                    Path virtualPath = virtualRoot.resolve(relative.toString()).normalize();
                    if (policy.shouldInclude(dataRoot, virtualPath)) {
                        files.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(file -> sourceRoot.relativize(file).toString()));
        return files;
    }

    private void writeSnapshotEntry(ZipOutputStream zip,
                                    Set<String> writtenEntries,
                                    String entryName,
                                    byte[] content) throws IOException {
        if (!writtenEntries.add(entryName)) {
            return;
        }
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content);
        zip.closeEntry();
    }

    private void writeSnapshotEntry(ZipOutputStream zip,
                                    Set<String> writtenEntries,
                                    String entryName,
                                    Path source) throws IOException {
        if (!writtenEntries.add(entryName)) {
            return;
        }
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream input = Files.newInputStream(source)) {
            input.transferTo(zip);
        } finally {
            zip.closeEntry();
        }
    }

    private String uniqueExternalWorkspaceName(ObjectNode workspaceNode, int index, Set<String> usedNames) {
        String baseName = sanitizeExternalWorkspaceName(workspaceText(workspaceNode, "id"));
        if (baseName.isBlank()) {
            baseName = sanitizeExternalWorkspaceName(workspaceText(workspaceNode, "name"));
        }
        if (baseName.isBlank()) {
            baseName = "workspace_" + (index + 1);
        }
        String candidate = baseName;
        int suffix = 2;
        while (!usedNames.add(candidate)) {
            candidate = baseName + "_" + suffix++;
        }
        return candidate;
    }

    private String workspaceText(ObjectNode workspaceNode, String key) {
        JsonNode node = workspaceNode.get(key);
        return node == null || !node.isTextual() ? "" : node.asText();
    }

    private String sanitizeExternalWorkspaceName(String value) {
        String sanitized = value == null ? "" : value.trim()
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (sanitized.endsWith(".") || sanitized.equals(".") || sanitized.equals("..")) {
            sanitized = sanitized.replaceAll("\\.+$", "");
        }
        if (sanitized.isBlank()) {
            return "";
        }
        String lower = sanitized.toLowerCase();
        if (lower.matches("^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?$")) {
            return "workspace_" + sanitized;
        }
        return sanitized;
    }

    @FunctionalInterface
    private interface WorkspacePathTransformer {
        String transform(String path) throws IOException;
    }

    @FunctionalInterface
    private interface WorkspaceNodePathTransformer {
        String transform(ObjectNode workspaceNode, int index, String path) throws IOException;
    }

    private record WorkspaceSnapshotPlan(String workspacesJson, List<ExternalWorkspace> externalWorkspaces) {
    }

    private record ExternalWorkspace(Path sourceRoot, String snapshotRoot) {
    }
}
