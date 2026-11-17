package com.laker.postman.workspace.reload;

import com.laker.postman.util.SafeFileUtil;
import com.laker.postman.workspace.reload.WorkspaceDataChangeDetector.WorkspaceDataChanges;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class WorkspaceDataChangeDetectorTest {

    @Test
    public void shouldReportExternalModificationOnlyOnce() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-external");
        Path collections = root.resolve("collections.json");
        Files.writeString(collections, "[]", StandardCharsets.UTF_8);
        WorkspaceDataChangeDetector detector = new WorkspaceDataChangeDetector();
        detector.reset(root);

        writeWithNewTimestamp(collections, "[{\"id\":\"1\"}]");

        assertEquals(detector.detect(root).changed(), Set.of(WorkspaceDataFile.COLLECTIONS));
        assertTrue(detector.detect(root).isEmpty());
    }

    @Test
    public void shouldIgnoreOwnAtomicWrites() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-self");
        Path environments = root.resolve("environments.json");
        Files.writeString(environments, "[]", StandardCharsets.UTF_8);
        WorkspaceDataChangeDetector detector = new WorkspaceDataChangeDetector();
        detector.reset(root);

        SafeFileUtil.writeStringAtomically(environments, "[{\"name\":\"dev\"}]");
        Files.setLastModifiedTime(environments, FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        assertTrue(detector.detect(root).isEmpty());
    }

    @Test
    public void restoringAnOlderOwnWriteShouldStillBeExternal() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-revert");
        Path mock = root.resolve("mock_servers.json");
        SafeFileUtil.writeStringAtomically(mock, "[]");
        WorkspaceDataChangeDetector detector = new WorkspaceDataChangeDetector();
        detector.reset(root);

        // 外部改写并被重新加载
        Files.writeString(mock, "[{\"id\":\"external\"}]", StandardCharsets.UTF_8);
        assertEquals(detector.detect(root).changed(), Set.of(WorkspaceDataFile.MOCK_SERVERS));
        SafeFileUtil.rememberLoaded(mock, SafeFileUtil.readForLoad(mock).orElseThrow());

        // Git 恢复到应用曾经写入的版本：内存仍是外部版本，必须再次重新加载
        Files.writeString(mock, "[]", StandardCharsets.UTF_8);
        assertEquals(detector.detect(root).changed(), Set.of(WorkspaceDataFile.MOCK_SERVERS));
    }

    @Test
    public void sameSizeAndTimestampChangeShouldStillBeDetected() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-same-meta");
        Path mock = root.resolve("mock_servers.json");
        Files.writeString(mock, "[{\"id\":\"a\"}]", StandardCharsets.UTF_8);
        FileTime time = Files.getLastModifiedTime(mock);
        WorkspaceDataChangeDetector detector = new WorkspaceDataChangeDetector();
        detector.reset(root);

        Files.writeString(mock, "[{\"id\":\"b\"}]", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(mock, time);

        assertEquals(detector.detect(root).changed(), Set.of(WorkspaceDataFile.MOCK_SERVERS));
    }

    @Test
    public void shouldIgnoreTouchWithoutContentChange() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-touch");
        Path mock = root.resolve("mock_servers.json");
        Files.writeString(mock, "[]", StandardCharsets.UTF_8);
        WorkspaceDataChangeDetector detector = new WorkspaceDataChangeDetector();
        detector.reset(root);

        Files.setLastModifiedTime(mock, FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        assertTrue(detector.detect(root).isEmpty());
    }

    @Test
    public void shouldSeparateUnreadableFilesFromReloadableChanges() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-broken");
        WorkspaceDataChangeDetector detector = new WorkspaceDataChangeDetector();
        detector.reset(root);

        Files.writeString(root.resolve("functional_config.json"), "{\"broken\":", StandardCharsets.UTF_8);
        // 外部工具写入的合法性能配置（不经过本进程写入）
        Path generated = Files.createTempDirectory("ws-detect-generated").resolve("performance_config.json");
        new com.laker.postman.performance.plan.PerformancePlanStorage().saveWorkspace(generated,
                com.laker.postman.performance.plan.PerformancePlanWorkspace.builder().build(), true);
        Files.writeString(root.resolve("performance_config.json"), Files.readString(generated) + "\n", StandardCharsets.UTF_8);

        WorkspaceDataChanges changes = detector.detect(root);
        assertEquals(changes.unreadable(), Set.of(WorkspaceDataFile.FUNCTIONAL));
        assertEquals(changes.changed(), Set.of(WorkspaceDataFile.PERFORMANCE));
    }

    @Test
    public void shouldOnlyResetWhenDataRootChanges() throws Exception {
        Path first = Files.createTempDirectory("ws-detect-first");
        Path second = Files.createTempDirectory("ws-detect-second");
        Files.writeString(second.resolve("collections.json"), "[]", StandardCharsets.UTF_8);
        WorkspaceDataChangeDetector detector = new WorkspaceDataChangeDetector();
        detector.reset(first);

        assertTrue(detector.detect(second).isEmpty());

        Files.delete(second.resolve("collections.json"));
        assertEquals(detector.detect(second).changed(), Set.of(WorkspaceDataFile.COLLECTIONS));
    }

    @Test
    public void shouldTreatValidJsonWithWrongStructureAsUnreadable() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-structure");
        WorkspaceDataChangeDetector detector = new WorkspaceDataChangeDetector();
        detector.reset(root);

        Files.writeString(root.resolve("collections.json"), "{\"name\":\"not an array\"}", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("environments.json"), "[1, 2]", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("functional_config.json"), "{\"rows\":{}}", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("performance_config.json"), "{\"plans\":[]}", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("mock_servers.json"), "{}", StandardCharsets.UTF_8);

        WorkspaceDataChanges changes = detector.detect(root);
        assertEquals(changes.unreadable(), Set.of(WorkspaceDataFile.COLLECTIONS, WorkspaceDataFile.ENVIRONMENTS,
                WorkspaceDataFile.FUNCTIONAL, WorkspaceDataFile.PERFORMANCE, WorkspaceDataFile.MOCK_SERVERS));
        assertTrue(changes.changed().isEmpty());
    }

    @Test
    public void shouldRejectCollectionsTheCodecCannotRead() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-codec");
        Path collections = root.resolve("collections.json");
        Files.writeString(collections, "[{\"type\":\"group\",\"name\":\"no id\",\"children\":[]}]",
                StandardCharsets.UTF_8);

        assertFalse(WorkspaceDataChangeDetector.isLoadable(WorkspaceDataFile.COLLECTIONS, collections));
    }

    @Test
    public void shouldTreatMissingFileAsLoadableChange() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-deleted");
        Path environments = root.resolve("environments.json");
        Files.writeString(environments, "[]", StandardCharsets.UTF_8);
        WorkspaceDataChangeDetector detector = new WorkspaceDataChangeDetector();
        detector.reset(root);

        Files.delete(environments);

        assertTrue(WorkspaceDataChangeDetector.isLoadable(WorkspaceDataFile.ENVIRONMENTS, environments));
        assertEquals(detector.detect(root).changed(), Set.of(WorkspaceDataFile.ENVIRONMENTS));
    }

    @Test
    public void performanceValidationShouldMatchTheRealLoader() throws Exception {
        Path root = Files.createTempDirectory("ws-detect-performance");
        Path performance = root.resolve("performance_config.json");
        Files.writeString(performance, "{\"plans\":[{\"name\":\"no tree\"}]}", StandardCharsets.UTF_8);
        assertFalse(WorkspaceDataChangeDetector.isLoadable(WorkspaceDataFile.PERFORMANCE, performance));

        new com.laker.postman.performance.plan.PerformancePlanStorage().saveWorkspace(performance,
                com.laker.postman.performance.plan.PerformancePlanWorkspace.builder().build(), true);
        assertTrue(WorkspaceDataChangeDetector.isLoadable(WorkspaceDataFile.PERFORMANCE, performance));
    }

    private static void writeWithNewTimestamp(Path file, String content) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
    }
}
