package com.laker.postman.util;

import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SafeFileUtilTest {

    @Test
    public void writeStringAtomicallyShouldReplaceContentWithoutLeavingTempFiles() throws Exception {
        Path dir = Files.createTempDirectory("safe-file-write");
        Path target = dir.resolve("nested").resolve("collections.json");

        SafeFileUtil.writeStringAtomically(target, "[1]");
        SafeFileUtil.writeStringAtomically(target, "[2]");

        assertEquals(Files.readString(target, StandardCharsets.UTF_8), "[2]");
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(files.count(), 1L);
        }
    }

    @Test
    public void backupUnreadableShouldKeepOriginalAndDeduplicateSameContent() throws Exception {
        Path dir = Files.createTempDirectory("safe-file-backup");
        Path file = dir.resolve("environments.json");
        Files.writeString(file, "[{broken", StandardCharsets.UTF_8);
        Path backupDir = dir.resolve("backups");

        Optional<Path> first = SafeFileUtil.backupUnreadable(file, backupDir);
        Optional<Path> second = SafeFileUtil.backupUnreadable(file, backupDir);

        assertTrue(first.isPresent());
        assertEquals(second, first);
        assertEquals(Files.readString(file, StandardCharsets.UTF_8), "[{broken");
        assertEquals(Files.readString(first.get(), StandardCharsets.UTF_8), "[{broken");
        try (Stream<Path> backups = Files.list(backupDir)) {
            assertEquals(backups.count(), 1L);
        }
    }

    @Test
    public void backupUnreadableShouldIgnoreMissingFile() throws Exception {
        Path dir = Files.createTempDirectory("safe-file-missing");

        assertFalse(SafeFileUtil.backupUnreadable(dir.resolve("missing.json"), dir.resolve("backups")).isPresent());
    }

    @Test
    public void loadedVersionShouldFollowLatestLoadOrSave() throws Exception {
        Path target = Files.createTempDirectory("safe-file-version").resolve("collections.json");
        SafeFileUtil.writeStringAtomically(target, "[1]");
        String written = SafeFileUtil.fileDigest(target).orElseThrow();
        assertTrue(SafeFileUtil.isLoadedVersion(target.getParent().resolve("./collections.json"), written));

        // 外部改成 [2] 并重新加载后，回到应用曾写入的 [1] 也是外部修改
        Files.writeString(target, "[2]", StandardCharsets.UTF_8);
        assertEquals(SafeFileUtil.readForLoad(target).orElseThrow(), "[2]");
        SafeFileUtil.rememberLoaded(target, "[2]");
        assertFalse(SafeFileUtil.isLoadedVersion(target, written));
        assertTrue(SafeFileUtil.isLoadedVersion(target, SafeFileUtil.fileDigest(target).orElseThrow()));
    }

    @Test
    public void baselineShouldUseTheBytesThatWereRead() throws Exception {
        Path target = Files.createTempDirectory("safe-file-race").resolve("environments.json");
        Files.writeString(target, "[]", StandardCharsets.UTF_8);
        String content = SafeFileUtil.readForLoad(target).orElseThrow();
        String loaded = SafeFileUtil.digest(content.getBytes(StandardCharsets.UTF_8));
        // 读取完成后、记录基准前文件被外部改写
        Files.writeString(target, "[{\"external\":true}]", StandardCharsets.UTF_8);
        SafeFileUtil.rememberLoaded(target, content);

        assertTrue(SafeFileUtil.isLoadedVersion(target, loaded));
        assertFalse(SafeFileUtil.isLoadedVersion(target, SafeFileUtil.fileDigest(target).orElseThrow()));
    }

    @Test
    public void missingFileShouldBeRecordedAsLoadedAbsence() throws Exception {
        Path target = Files.createTempDirectory("safe-file-absent").resolve("mock_servers.json");
        assertTrue(SafeFileUtil.readForLoad(target).isEmpty());
        SafeFileUtil.rememberLoaded(target, "[]");
        assertTrue(SafeFileUtil.isLoadedVersion(target, null));
        // 未修改的空数据不会重新生成被删除的文件
        assertFalse(SafeFileUtil.writeIfChanged(target, "[]"));
        assertFalse(Files.exists(target));
    }

    @Test
    public void writeIfChangedShouldNotRecreateDeletedFileWhenContentIsUnchanged() throws Exception {
        Path target = Files.createTempDirectory("safe-file-baseline").resolve("performance_config.json");
        SafeFileUtil.rememberLoaded(target, "{}");

        assertFalse(SafeFileUtil.writeIfChanged(target, "{}"));
        assertFalse(Files.exists(target));

        assertTrue(SafeFileUtil.writeIfChanged(target, "{\"a\":1}"));
        assertEquals(Files.readString(target), "{\"a\":1}");
    }

    @Test
    public void overwritingPostponedExternalChangeShouldKeepBackup() throws Exception {
        Path target = Files.createTempDirectory("safe-file-external").resolve("mock_servers.json");
        Files.writeString(target, "[]", StandardCharsets.UTF_8);
        SafeFileUtil.rememberLoaded(target, "[]");
        String external = "[{\"external\":\"" + System.nanoTime() + "\"}]";
        Files.writeString(target, external, StandardCharsets.UTF_8);

        SafeFileUtil.writeStringAtomically(target, "[{\"mine\":true}]");

        assertEquals(Files.readString(target), "[{\"mine\":true}]");
        Path backup;
        try (Stream<Path> files = Files.list(SafeFileUtil.overwrittenBackupDirectory())) {
            backup = files.filter(p -> {
                try {
                    return Files.readString(p).equals(external);
                } catch (Exception e) {
                    return false;
                }
            }).findFirst().orElseThrow();
        }
        Files.delete(backup);

        // 自己的写入不再触发备份
        SafeFileUtil.writeStringAtomically(target, "[]");
        try (Stream<Path> files = Files.list(SafeFileUtil.overwrittenBackupDirectory())) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().startsWith("mock_servers.json")
                    && readQuietly(p).contains("mine")));
        }
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            return "";
        }
    }
}
