package com.laker.postman.mcp;

import com.laker.postman.model.Workspace;
import com.laker.postman.util.WorkspaceStorageUtil;
import com.laker.postman.workspace.cli.WorkspaceRunWorkspace;
import com.laker.postman.workspace.cli.WorkspaceRunWorkspaceResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the workspace IDs that this MCP process is allowed to access.
 * Tool arguments never become filesystem paths: they are matched only against this catalog.
 */
final class McpWorkspaceCatalog {
    private final List<McpAuthorizedWorkspace> workspaces;
    private final Map<String, McpAuthorizedWorkspace> workspacesById;

    McpWorkspaceCatalog(McpServeOptions options) {
        this(options.workspace() == null || options.workspace().isBlank()
                ? registeredWorkspaces()
                : explicitWorkspace(options.workspace()));
    }

    McpWorkspaceCatalog(List<McpAuthorizedWorkspace> workspaces) {
        LinkedHashMap<String, McpAuthorizedWorkspace> indexed = new LinkedHashMap<>();
        if (workspaces != null) {
            for (McpAuthorizedWorkspace workspace : workspaces) {
                if (workspace != null && workspace.id() != null && !workspace.id().isBlank()) {
                    indexed.putIfAbsent(workspace.id(), workspace);
                }
            }
        }
        this.workspacesById = Map.copyOf(indexed);
        this.workspaces = List.copyOf(indexed.values());
        if (this.workspaces.isEmpty()) {
            throw new IllegalArgumentException("No EasyPostman workspaces are registered");
        }
    }

    List<McpAuthorizedWorkspace> workspaces() {
        return workspaces;
    }

    McpAuthorizedWorkspace resolve(Map<String, Object> arguments) {
        return resolve(McpToolArguments.optionalString(arguments, "workspaceId"));
    }

    McpAuthorizedWorkspace resolve(String workspaceId) {
        if (workspaceId != null) {
            McpAuthorizedWorkspace selected = workspacesById.get(workspaceId);
            if (selected == null) {
                throw new IllegalArgumentException(
                        "Workspace is not authorized: " + workspaceId + ". Call list_workspaces for valid IDs."
                );
            }
            return selected;
        }
        if (workspaces.size() == 1) {
            return workspaces.get(0);
        }
        List<McpAuthorizedWorkspace> current = workspaces.stream()
                .filter(McpAuthorizedWorkspace::current)
                .toList();
        if (current.size() == 1) {
            return current.get(0);
        }
        throw new IllegalArgumentException(
                "workspaceId is required because multiple workspaces are authorized. Call list_workspaces first."
        );
    }

    private static List<McpAuthorizedWorkspace> explicitWorkspace(String selector) {
        WorkspaceRunWorkspace resolved = WorkspaceRunWorkspaceResolver.resolve(selector);
        Path normalized = resolved.directory().toAbsolutePath().normalize();
        String currentId = WorkspaceStorageUtil.getCurrentWorkspace();
        for (Workspace registered : WorkspaceStorageUtil.loadWorkspaces()) {
            Path registeredPath = pathOf(registered);
            if (registeredPath != null && registeredPath.equals(normalized)) {
                return List.of(fromRegistered(registered, registeredPath, currentId));
            }
        }
        return List.of(new McpAuthorizedWorkspace(
                "local-" + shortHash(normalized.toString()),
                resolved.name(),
                "",
                "LOCAL",
                normalized,
                true
        ));
    }

    private static List<McpAuthorizedWorkspace> registeredWorkspaces() {
        String currentId = WorkspaceStorageUtil.getCurrentWorkspace();
        List<McpAuthorizedWorkspace> authorized = new ArrayList<>();
        for (Workspace workspace : WorkspaceStorageUtil.loadWorkspaces()) {
            Path directory = pathOf(workspace);
            if (directory != null) {
                authorized.add(fromRegistered(workspace, directory, currentId));
            }
        }
        return authorized;
    }

    private static McpAuthorizedWorkspace fromRegistered(Workspace workspace,
                                                          Path directory,
                                                          String currentId) {
        String id = safe(workspace.getId());
        if (id.isBlank()) {
            id = "local-" + shortHash(directory.toString());
        }
        String name = safe(workspace.getName());
        if (name.isBlank()) {
            Path fileName = directory.getFileName();
            name = fileName == null ? id : fileName.toString();
        }
        return new McpAuthorizedWorkspace(
                id,
                name,
                safe(workspace.getDescription()),
                workspace.getType() == null ? "LOCAL" : workspace.getType().name(),
                directory,
                id.equals(currentId)
        );
    }

    private static Path pathOf(Workspace workspace) {
        if (workspace == null || workspace.getPath() == null || workspace.getPath().isBlank()) {
            return null;
        }
        try {
            return Path.of(workspace.getPath()).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
