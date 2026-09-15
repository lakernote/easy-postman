package com.laker.postman.workspace.cli;

import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@UtilityClass
public class WorkspaceRunWorkspaceResolver {

    public WorkspaceRunWorkspace resolve(String selector) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Workspace directory is required");
        }

        Path direct = toPath(selector);
        if (Files.isDirectory(direct)) {
            return fromDirectory(direct);
        }
        if (Files.isRegularFile(direct)) {
            throw new IllegalArgumentException(
                    "EasyPostman CLI expects a workspace directory, not a collection file: " + direct
            );
        }
        throw new IllegalArgumentException("Workspace directory does not exist: " + direct);
    }

    private WorkspaceRunWorkspace fromDirectory(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        Path fileName = normalized.getFileName();
        return new WorkspaceRunWorkspace(
                fileName == null ? normalized.toString() : fileName.toString(),
                normalized
        );
    }

    private Path toPath(String value) {
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid workspace path: " + value, ex);
        }
    }
}
