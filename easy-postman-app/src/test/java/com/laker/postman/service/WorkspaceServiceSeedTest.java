package com.laker.postman.service;

import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.*;

public class WorkspaceServiceSeedTest {

    @Test
    public void shouldSeedSamplesIntoStandaloneDirectory() throws Exception {
        Path dir = Files.createTempDirectory("ws-seed-samples");

        WorkspaceService.seedWorkspaceData(dir, true);

        assertNotEquals(Files.readString(dir.resolve("collections.json")).trim(), "[]");
        assertNotEquals(Files.readString(dir.resolve("environments.json")).trim(), "[]");
    }

    @Test
    public void shouldSeedEmptyDataInsideExistingRepository() throws Exception {
        Path dir = Files.createTempDirectory("ws-seed-empty");

        WorkspaceService.seedWorkspaceData(dir, false);

        assertEquals(Files.readString(dir.resolve("collections.json")).trim(), "[]");
        assertEquals(Files.readString(dir.resolve("environments.json")).trim(), "[]");
    }

    @Test
    public void shouldNeverOverwriteExistingData() throws Exception {
        Path dir = Files.createTempDirectory("ws-seed-existing");
        Files.writeString(dir.resolve("environments.json"), "[{\"name\":\"mine\"}]", StandardCharsets.UTF_8);

        WorkspaceService.seedWorkspaceData(dir, true);

        assertEquals(Files.readString(dir.resolve("environments.json")), "[{\"name\":\"mine\"}]");
        assertFalse(Files.exists(dir.resolve("collections.json")));
    }

    @Test
    public void canonicalPathShouldResolveSymlinksAndMissingChildren() throws Exception {
        Path real = Files.createTempDirectory("ws-canonical");
        Path link = real.resolveSibling(real.getFileName() + "-link");
        Files.createSymbolicLink(link, real);
        try {
            assertEquals(WorkspaceService.canonicalPath(link.resolve("api/../api")),
                    WorkspaceService.canonicalPath(real.resolve("api")));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    public void failedFirstRunSeedShouldReportFailure() throws Exception {
        Path file = Files.createTempFile("ws-seed-blocked", ".txt");
        Files.delete(file);
        Files.writeString(file.getParent().resolve(file.getFileName()), "");
        // 目标是普通文件而非目录：无法写入示例数据，应返回 false 以便下次启动重试
        assertFalse(WorkspaceService.seedFirstRunDirectory(file.resolve("child")));
    }
}
