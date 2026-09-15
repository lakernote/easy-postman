package com.laker.postman.workspace.cli;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Value
@Builder
public class WorkspaceRunOptions {
    String workspace;
    String environment;
    Path iterationDataPath;
    Integer iterationCount;
    Path workingDirectory;
    boolean bail;
    Map<String, String> environmentOverrides;
    Path uploadFileRoot;
    Integer maxRequestReportEntries;

    public WorkspaceRunOptions(String workspace,
                               String environment,
                               Path iterationDataPath,
                               Integer iterationCount,
                               Path workingDirectory,
                               Boolean bail) {
        this(workspace, environment, iterationDataPath, iterationCount, workingDirectory, bail, Map.of(), null, null);
    }

    public WorkspaceRunOptions(String workspace,
                               String environment,
                               Path iterationDataPath,
                               Integer iterationCount,
                               Path workingDirectory,
                               Boolean bail,
                               Map<String, String> environmentOverrides) {
        this(workspace, environment, iterationDataPath, iterationCount, workingDirectory, bail,
                environmentOverrides, null, null);
    }

    public WorkspaceRunOptions(String workspace,
                               String environment,
                               Path iterationDataPath,
                               Integer iterationCount,
                               Path workingDirectory,
                               Boolean bail,
                               Map<String, String> environmentOverrides,
                               Path uploadFileRoot,
                               Integer maxRequestReportEntries) {
        this.workspace = workspace;
        this.environment = environment;
        this.iterationDataPath = iterationDataPath;
        this.iterationCount = iterationCount;
        this.workingDirectory = workingDirectory;
        this.bail = bail != null && bail;
        this.environmentOverrides = environmentOverrides == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(environmentOverrides));
        this.uploadFileRoot = uploadFileRoot;
        if (maxRequestReportEntries != null && maxRequestReportEntries < 0) {
            throw new IllegalArgumentException("maxRequestReportEntries must not be negative");
        }
        this.maxRequestReportEntries = maxRequestReportEntries;
    }
}
