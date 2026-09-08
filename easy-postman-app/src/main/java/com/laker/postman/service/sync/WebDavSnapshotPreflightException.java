package com.laker.postman.service.sync;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Indicates that a workspace cannot be safely included in a WebDAV snapshot.
 */
public class WebDavSnapshotPreflightException extends IOException {
    private final String workspaceName;
    private final Path workspacePath;
    private final long fileCount;
    private final long contentBytes;
    private final long maxContentBytes;
    private final long maxFileCount;

    public WebDavSnapshotPreflightException(String workspaceName,
                                             Path workspacePath,
                                             long fileCount,
                                             long contentBytes,
                                             long maxContentBytes,
                                             long maxFileCount) {
        super("External workspace exceeds the WebDAV snapshot limit: workspace='"
                + workspaceName + "', path='" + workspacePath + "', files=" + fileCount
                + ", bytes=" + contentBytes + ", limitBytes=" + maxContentBytes
                + ", limitFiles=" + maxFileCount);
        this.workspaceName = workspaceName;
        this.workspacePath = workspacePath;
        this.fileCount = fileCount;
        this.contentBytes = contentBytes;
        this.maxContentBytes = maxContentBytes;
        this.maxFileCount = maxFileCount;
    }

    public String workspaceName() {
        return workspaceName;
    }

    public Path workspacePath() {
        return workspacePath;
    }

    public long fileCount() {
        return fileCount;
    }

    public long contentBytes() {
        return contentBytes;
    }

    public long maxContentBytes() {
        return maxContentBytes;
    }

    public long maxFileCount() {
        return maxFileCount;
    }
}
