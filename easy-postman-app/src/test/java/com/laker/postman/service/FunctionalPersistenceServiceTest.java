package com.laker.postman.service;

import com.laker.postman.functional.model.FunctionalConfigRow;
import com.laker.postman.functional.model.FunctionalConfigSnapshot;
import com.laker.postman.model.Workspace;

import com.laker.postman.functional.model.FunctionalCsvDataState;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.testng.Assert.*;

public class FunctionalPersistenceServiceTest {

    @Test(description = "退出同步保存必须等待旧的异步保存，最终文件保留最新功能测试配置")
    public void synchronousSaveShouldFollowQueuedAsyncSave() throws Exception {
        Path configPath = Files.createTempDirectory("functional-final-save").resolve("functional_config.json");
        TestableFunctionalPersistenceService service = new TestableFunctionalPersistenceService(configPath);

        writeAfterBlockedAsyncSave(service, target -> target.save(snapshot("latest")));

        assertEquals(service.loadSnapshot().getRows().get(0).getRequestId(), "latest");
    }

    @Test(description = "清空配置必须排在已经调度的自动保存之后，不能被旧保存重建")
    public void clearShouldFollowQueuedAsyncSave() throws Exception {
        Path configPath = Files.createTempDirectory("functional-final-clear").resolve("functional_config.json");
        TestableFunctionalPersistenceService service = new TestableFunctionalPersistenceService(configPath);

        writeAfterBlockedAsyncSave(service, FunctionalPersistenceService::clear);

        assertFalse(Files.exists(configPath));
    }

    @Test(description = "功能测试配置路径应跟随当前工作区，便于 Git 工作区同步")
    public void shouldResolveConfigPathFromCurrentWorkspace() throws IOException {
        Path workspaceDir = Files.createTempDirectory("functional-workspace-path");
        Workspace workspace = new Workspace();
        workspace.setPath(workspaceDir.toString() + File.separator);

        WorkspaceAwareFunctionalPersistenceService service = new WorkspaceAwareFunctionalPersistenceService(workspace);

        assertEquals(service.getConfigFilePath(), workspaceDir.resolve("functional_config.json"));
    }

    @Test(description = "保存功能测试配置时应真实写入当前工作区，便于 Git 同步")
    public void shouldSaveConfigFileIntoCurrentWorkspace() throws IOException {
        Path workspaceDir = Files.createTempDirectory("functional-workspace-save");
        Workspace workspace = new Workspace();
        workspace.setPath(workspaceDir.toString());
        WorkspaceAwareFunctionalPersistenceService service = new WorkspaceAwareFunctionalPersistenceService(workspace);

        FunctionalCsvDataState csvState = new FunctionalCsvDataState(
                "workspace-users.csv",
                List.of("username", "password"),
                List.of(csvRow("username", "alice", "password", "secret"))
        );

        try {
            service.save(new FunctionalConfigSnapshot(
                    List.of(new FunctionalConfigRow("req-workspace", true)),
                    csvState
            ));

            Path configPath = workspaceDir.resolve("functional_config.json");
            assertTrue(Files.exists(configPath));

            FunctionalConfigSnapshot loaded = service.loadSnapshot();
            assertEquals(loaded.getRows().size(), 1);
            assertEquals(loaded.getRows().get(0).getRequestId(), "req-workspace");
            FunctionalCsvDataState loadedCsvState = loaded.getCsvState();
            assertNotNull(loadedCsvState);
            assertEquals(loadedCsvState.getSourceName(), "workspace-users.csv");
            assertEquals(loadedCsvState.getHeaders(), List.of("username", "password"));
            assertEquals(loadedCsvState.getRows().get(0).get("username"), "alice");
        } finally {
            service.shutdown();
        }
    }

    @Test(description = "异步保存应固定调度时的工作区，避免切换工作区后写错位置")
    public void shouldSaveAsyncToWorkspaceActiveWhenScheduled() throws Exception {
        Path workspaceA = Files.createTempDirectory("functional-workspace-async-a");
        Path workspaceB = Files.createTempDirectory("functional-workspace-async-b");
        WorkspaceAwareFunctionalPersistenceService service =
                new WorkspaceAwareFunctionalPersistenceService(workspace(workspaceA));

        FunctionalCsvDataState csvState = new FunctionalCsvDataState(
                "async-users.csv",
                List.of("username"),
                List.of(csvRow("username", "bob"))
        );

        CountDownLatch releaseSave = new CountDownLatch(1);
        try {
            saveExecutor(service).execute(() -> awaitRelease(releaseSave));
            service.saveAsync(new FunctionalConfigSnapshot(
                    List.of(new FunctionalConfigRow("req-async", true)),
                    csvState
            ));
            service.setWorkspace(workspace(workspaceB));
        } finally {
            releaseSave.countDown();
            service.shutdown();
        }

        Path configInA = workspaceA.resolve("functional_config.json");
        Path configInB = workspaceB.resolve("functional_config.json");
        assertTrue(Files.exists(configInA), "functional_config.json should be saved in the source workspace");
        assertFalse(Files.exists(configInB), "functional_config.json must not be written to the target workspace");
        assertTrue(Files.readString(configInA).contains("async-users.csv"));
    }

    @Test(description = "应支持保存并恢复 FunctionalPanel CSV 快照")
    public void shouldPersistAndLoadCsvState() throws IOException {
        Path tempDir = Files.createTempDirectory("functional-persistence-test");
        Path configPath = tempDir.resolve("functional_config.json");
        TestableFunctionalPersistenceService service = new TestableFunctionalPersistenceService(configPath);
        service.init();

        FunctionalCsvDataState csvState = new FunctionalCsvDataState(
                "users.csv",
                List.of("username", "password"),
                List.of(csvRow("username", "alice", "password", "secret"))
        );

        try {
            service.save(new FunctionalConfigSnapshot(
                    List.of(new FunctionalConfigRow("req-1", false)),
                    csvState
            ));

            FunctionalConfigSnapshot loaded = service.loadSnapshot();
            FunctionalCsvDataState loadedCsvState = loaded.getCsvState();

            assertEquals(loaded.getRows().size(), 1);
            assertEquals(loaded.getRows().get(0).getRequestId(), "req-1");
            assertFalse(loaded.getRows().get(0).isSelected());
            assertNotNull(loadedCsvState);
            assertEquals(loadedCsvState.getSourceName(), "users.csv");
            assertEquals(loadedCsvState.getHeaders(), List.of("username", "password"));
            assertEquals(loadedCsvState.getRows().get(0).get("username"), "alice");
        } finally {
            service.shutdown();
        }
    }

    @Test(description = "应从当前 JSON 结构加载 Functional CSV 状态")
    public void shouldLoadCsvStateFromCurrentJsonFixture() throws IOException {
        Path tempDir = Files.createTempDirectory("functional-csv-state");
        Path configPath = tempDir.resolve("functional_config.json");
        Files.writeString(configPath, """
                {"version":"1.0","rows":[],"csvState":{"sourceName":"users.csv","headers":["username"],"rows":[{"username":"alice"}]}}
                """);
        TestableFunctionalPersistenceService service = new TestableFunctionalPersistenceService(configPath);

        FunctionalCsvDataState loadedCsvState = service.loadSnapshot().getCsvState();

        assertNotNull(loadedCsvState);
        assertEquals(loadedCsvState.getSourceName(), "users.csv");
        assertEquals(loadedCsvState.getHeaders(), List.of("username"));
        assertEquals(loadedCsvState.getRows().size(), 1);
        assertEquals(loadedCsvState.getRows().get(0).get("username"), "alice");
    }

    @Test(description = "Functional CSV 状态应在构造和读取时做防御性拷贝")
    public void shouldDefensivelyCopyFunctionalCsvDataState() {
        List<String> headers = new java.util.ArrayList<>(List.of("username"));
        Map<String, String> originalRow = csvRow("username", "alice");
        List<Map<String, String>> rows = new java.util.ArrayList<>(List.of(originalRow));

        FunctionalCsvDataState state = new FunctionalCsvDataState("users.csv", headers, rows);
        headers.add("password");
        originalRow.put("username", "bob");
        rows.add(csvRow("username", "carol"));

        assertEquals(state.getHeaders(), List.of("username"));
        assertEquals(state.getRows().size(), 1);
        assertEquals(state.getRows().get(0).get("username"), "alice");

        List<String> exportedHeaders = state.getHeaders();
        List<Map<String, String>> exportedRows = state.getRows();
        exportedHeaders.add("mutated");
        exportedRows.get(0).put("username", "dave");

        assertEquals(state.getHeaders(), List.of("username"));
        assertEquals(state.getRows().get(0).get("username"), "alice");
    }

    private static Workspace workspace(Path workspaceDir) {
        Workspace workspace = new Workspace();
        workspace.setPath(workspaceDir.toString());
        return workspace;
    }

    private static FunctionalConfigSnapshot snapshot(String requestId) {
        return new FunctionalConfigSnapshot(List.of(new FunctionalConfigRow(requestId, true)), null);
    }

    private static ExecutorService saveExecutor(FunctionalPersistenceService service) throws ReflectiveOperationException {
        Field executorField = FunctionalPersistenceService.class.getDeclaredField("saveExecutor");
        executorField.setAccessible(true);
        return (ExecutorService) executorField.get(service);
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writeAfterBlockedAsyncSave(FunctionalPersistenceService service,
                                                  Consumer<FunctionalPersistenceService> finalWrite) throws Exception {
        CountDownLatch releaseOldSave = new CountDownLatch(1);
        CountDownLatch finalWriteStarted = new CountDownLatch(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            saveExecutor(service).execute(() -> awaitRelease(releaseOldSave));
            service.saveAsync(snapshot("old"));
            Future<?> latestWrite = caller.submit(() -> {
                finalWriteStarted.countDown();
                finalWrite.accept(service);
            });
            assertTrue(finalWriteStarted.await(2, TimeUnit.SECONDS));
            try {
                latestWrite.get(100, TimeUnit.MILLISECONDS);
                fail("Synchronous write returned before the queued older save completed");
            } catch (TimeoutException expected) {
                // 同步保存不能绕过队列，必须等待旧任务完成。
            }
            releaseOldSave.countDown();
            latestWrite.get(2, TimeUnit.SECONDS);
        } finally {
            releaseOldSave.countDown();
            caller.shutdownNow();
            service.shutdown();
        }
    }

    private static Map<String, String> csvRow(String... keyValues) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private static final class TestableFunctionalPersistenceService extends FunctionalPersistenceService {
        private final Path configPath;

        private TestableFunctionalPersistenceService(Path configPath) {
            this.configPath = configPath;
        }

        @Override
        protected Path getConfigFilePath() {
            return configPath;
        }
    }

    private static final class WorkspaceAwareFunctionalPersistenceService extends FunctionalPersistenceService {
        private volatile Workspace workspace;

        private WorkspaceAwareFunctionalPersistenceService(Workspace workspace) {
            this.workspace = workspace;
        }

        private void setWorkspace(Workspace workspace) {
            this.workspace = workspace;
        }

        @Override
        protected Workspace getCurrentWorkspace() {
            return workspace;
        }
    }

}
