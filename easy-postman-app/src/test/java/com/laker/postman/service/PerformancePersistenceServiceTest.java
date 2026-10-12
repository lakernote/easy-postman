package com.laker.postman.service;

import com.laker.postman.model.Workspace;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.assertion.AssertionData;
import com.laker.postman.performance.core.config.CsvDataSetData;
import com.laker.postman.performance.core.extractor.ExtractorData;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.SsePerformanceData;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.plan.PerformancePlanDocument;
import com.laker.postman.performance.plan.PerformancePlanConfiguration;
import com.laker.postman.performance.plan.PerformancePlanNode;
import com.laker.postman.performance.plan.PerformancePlanWorkspace;
import com.laker.postman.performance.plan.PerformanceRemoteWorkerSettings;
import com.laker.postman.performance.plan.PerformanceSavedPlan;
import com.laker.postman.panel.performance.tree.PerformanceSwingTreePlanAdapter;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.performance.core.timer.TimerData;
import com.laker.postman.service.variable.RequestExecutionScope;
import org.testng.annotations.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
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

public class PerformancePersistenceServiceTest {

    @Test(description = "退出同步保存必须等待旧的异步保存，最终文件保留最新工作区")
    public void synchronousWorkspaceSaveShouldFollowQueuedAsyncSave() throws Exception {
        Path configPath = Files.createTempDirectory("performance-final-save").resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);

        writeAfterBlockedAsyncSave(service, target -> target.saveWorkspace(namedWorkspace("latest")));

        assertEquals(service.loadWorkspace().getActivePlan().getName(), "latest");
    }

    @Test(description = "同步更新配置必须基于前一个异步保存完成后的工作区")
    public void configurationSaveShouldFollowQueuedAsyncSave() throws Exception {
        Path configPath = Files.createTempDirectory("performance-final-configuration").resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);

        writeAfterBlockedAsyncSave(service, target -> target.saveConfiguration(PerformancePlanConfiguration.builder()
                .efficientMode(true)
                .build()));

        assertEquals(service.loadWorkspace().getActivePlan().getName(), "old");
        assertTrue(service.loadConfiguration().isEfficientMode());
    }

    @Test(description = "清空配置必须排在已经调度的自动保存之后，不能被旧保存重建")
    public void clearShouldFollowQueuedAsyncSave() throws Exception {
        Path configPath = Files.createTempDirectory("performance-final-clear").resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);

        writeAfterBlockedAsyncSave(service, PerformancePersistenceService::clear);

        assertFalse(Files.exists(configPath));
    }

    private static PerformancePlanWorkspace namedWorkspace(String name) {
        return PerformancePlanWorkspace.builder()
                .activePlanId("plan")
                .plans(List.of(PerformanceSavedPlan.builder().id("plan").name(name).build()))
                .build();
    }

    private static void writeAfterBlockedAsyncSave(PerformancePersistenceService service,
                                                   Consumer<PerformancePersistenceService> finalWrite) throws Exception {
        Field executorField = PerformancePersistenceService.class.getDeclaredField("saveExecutor");
        executorField.setAccessible(true);
        ExecutorService saveExecutor = (ExecutorService) executorField.get(service);
        CountDownLatch releaseOldSave = new CountDownLatch(1);
        CountDownLatch finalWriteStarted = new CountDownLatch(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            saveExecutor.execute(() -> {
                try {
                    releaseOldSave.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            service.saveWorkspaceAsync(namedWorkspace("old"));
            Future<?> latestWrite = caller.submit(() -> {
                finalWriteStarted.countDown();
                finalWrite.accept(service);
            });
            assertTrue(finalWriteStarted.await(2, TimeUnit.SECONDS));
            try {
                latestWrite.get(100, TimeUnit.MILLISECONDS);
                fail("Synchronous write returned before the queued older save completed");
            } catch (TimeoutException expected) {
                // 必须等旧保存完成，不能绕过保存队列。
            }
            releaseOldSave.countDown();
            latestWrite.get(2, TimeUnit.SECONDS);
        } finally {
            releaseOldSave.countDown();
            caller.shutdownNow();
            service.shutdown();
        }
    }

    @Test(description = "默认配置路径应跟随当前工作区，便于 Git 工作区同步性能测试方案")
    public void shouldResolveConfigPathFromCurrentWorkspace() throws IOException {
        Path workspaceDir = Files.createTempDirectory("performance-workspace-path");
        Workspace workspace = new Workspace();
        workspace.setPath(workspaceDir.toString() + java.io.File.separator);

        WorkspaceAwarePerformancePersistenceService service = new WorkspaceAwarePerformancePersistenceService(workspace);

        assertEquals(service.getConfigFilePath(), workspaceDir.resolve("performance_config.json"));
    }

    @Test(description = "保存性能测试配置时应真实写入当前工作区，便于 Git 同步")
    public void shouldSaveConfigFileIntoCurrentWorkspace() throws IOException {
        Path workspaceDir = Files.createTempDirectory("performance-workspace-save");
        Workspace workspace = new Workspace();
        workspace.setPath(workspaceDir.toString());
        WorkspaceAwarePerformancePersistenceService service = new WorkspaceAwarePerformancePersistenceService(workspace);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));

        save(service, root, false);

        Path configPath = workspaceDir.resolve("performance_config.json");
        assertTrue(Files.exists(configPath));
        assertFalse(Files.readString(configPath).contains("responseBodyPreviewLimitKb"));

        DefaultMutableTreeNode loadedRoot = load(service, "Loaded Plan");
        assertNotNull(loadedRoot);
        assertEquals(((PerformanceTreeNode) loadedRoot.getUserObject()).name, "Loaded Plan");
        assertFalse(service.loadConfiguration().isEfficientMode());
        assertFalse(Files.readString(configPath).contains("\"csvState\""));
    }

    @Test(description = "异步保存应固定调度时的工作区，避免切换工作区后写错位置")
    public void shouldSaveAsyncToWorkspaceActiveWhenScheduled() throws Exception {
        Path workspaceA = Files.createTempDirectory("performance-workspace-async-a");
        Path workspaceB = Files.createTempDirectory("performance-workspace-async-b");
        BlockingWorkspacePerformancePersistenceService service =
                new BlockingWorkspacePerformancePersistenceService(workspace(workspaceA));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));

        saveAsync(service, root, false);
        if (service.awaitWorkerWorkspaceLookup()) {
            service.setWorkspace(workspace(workspaceB));
            service.releaseWorkerWorkspaceLookup();
        } else {
            service.setWorkspace(workspace(workspaceB));
        }

        Path configInA = workspaceA.resolve("performance_config.json");
        Path configInB = workspaceB.resolve("performance_config.json");
        assertTrue(awaitExists(configInA), "performance_config.json should be saved in the source workspace");
        assertFalse(Files.exists(configInB), "performance_config.json must not be written to the target workspace");
        assertTrue(Files.readString(configInA).contains("Plan"));
    }

    @Test(description = "应保存并恢复 Thread Group 下的 CSV Data Set 节点")
    public void shouldPersistCsvDataSetNodeUnderThreadGroup() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-csv-data-set-node");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));
        DefaultMutableTreeNode group = new DefaultMutableTreeNode(new PerformanceTreeNode("Thread Group", NodeType.THREAD_GROUP, new ThreadGroupData()));
        PerformanceTreeNode csvNodeData = new PerformanceTreeNode("CSV Data Set", NodeType.CSV_DATA_SET);
        csvNodeData.csvDataSetData = new CsvDataSetData(
                "users-1-300.csv",
                List.of("userId", "roomId"),
                List.of(row("userId", "u1", "roomId", "1728"))
        );
        group.add(new DefaultMutableTreeNode(csvNodeData));
        root.add(group);

        save(service, root, true, true, false);

        String json = Files.readString(configPath, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"type\": \"CSV_DATA_SET\""));
        assertTrue(json.contains("\"csvDataSetData\""));
        assertFalse(json.contains("\"csvState\""));

        DefaultMutableTreeNode loadedRoot = load(service, "Loaded Plan");
        DefaultMutableTreeNode loadedGroup = (DefaultMutableTreeNode) loadedRoot.getChildAt(0);
        DefaultMutableTreeNode loadedCsvNode = (DefaultMutableTreeNode) loadedGroup.getChildAt(0);
        PerformanceTreeNode loadedCsvData = (PerformanceTreeNode) loadedCsvNode.getUserObject();

        assertEquals(loadedCsvData.type, NodeType.CSV_DATA_SET);
        assertEquals(loadedCsvData.csvDataSetData.getSourceName(), "users-1-300.csv");
        assertEquals(loadedCsvData.csvDataSetData.getHeaders(), List.of("userId", "roomId"));
        assertEquals(loadedCsvData.csvDataSetData.getRows().get(0).get("roomId"), "1728");
    }

    @Test(description = "应保存并恢复趋势采样开关")
    public void shouldPersistAndLoadTrendEnabled() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-trend-enabled");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));
        save(service, root, true, false);

        assertFalse(service.loadConfiguration().isTrendEnabled());
        assertTrue(Files.readString(configPath, StandardCharsets.UTF_8).contains("\"trendEnabled\": false"));
    }

    @Test(description = "应保存并恢复报表实时刷新开关，默认结束后生成")
    public void shouldPersistAndLoadRealtimeReportRefresh() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-report-refresh");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));
        save(service, root, true, true, true);

        assertTrue(service.loadConfiguration().isReportRealtimeEnabled());
        assertTrue(Files.readString(configPath, StandardCharsets.UTF_8).contains("\"reportRealtimeEnabled\": true"));
    }

    @Test(description = "应支持通过纯计划文档保存请求快照，便于无集合树的压测运行器恢复配置")
    public void shouldSaveAndLoadPlanDocumentWithRequestSnapshotWithoutCollectionTree() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-document");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        HttpRequestItem requestItem = new HttpRequestItem();
        requestItem.setId("snapshot-request");
        requestItem.setName("Snapshot WebSocket");
        requestItem.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
        requestItem.setUrl("wss://example.test/ws");

        WebSocketPerformanceData webSocketData = new WebSocketPerformanceData();
        webSocketData.connectTimeoutMs = 4321;

        PerformancePlanDocument document = new PerformancePlanDocument(
                PerformancePlanNode.builder()
                        .name("Plan")
                        .type(NodeType.ROOT)
                        .children(List.of(
                                PerformancePlanNode.builder()
                                        .name("Users")
                                        .type(NodeType.THREAD_GROUP)
                                        .threadGroupData(new ThreadGroupData())
                                        .children(List.of(
                                                PerformancePlanNode.builder()
                                                        .name("Snapshot WebSocket")
                                                        .type(NodeType.REQUEST)
                                                        .httpRequestItem(requestItem)
                                                        .webSocketPerformanceData(webSocketData)
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
        );

        service.saveConfiguration(PerformancePlanConfiguration.builder()
                .planDocument(document)
                .efficientMode(true)
                .trendEnabled(true)
                .reportRealtimeEnabled(false)
                .build());

        PerformancePlanDocument loadedDocument = service.loadConfiguration().getPlanDocument();

        assertNotNull(loadedDocument);
        PerformancePlanNode loadedRequest = loadedDocument.getRoot().getChildren().get(0).getChildren().get(0);
        assertEquals(loadedRequest.getHttpRequestItem().getId(), "snapshot-request");
        assertEquals(loadedRequest.getHttpRequestItem().getUrl(), "wss://example.test/ws");
        assertEquals(loadedRequest.getHttpRequestItem().getProtocol(), RequestItemProtocolEnum.WEBSOCKET);
        assertEquals(loadedRequest.getWebSocketPerformanceData().connectTimeoutMs, 4321);

        String json = Files.readString(configPath, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"requestSnapshot\""));
        assertFalse(json.contains("\"requestItem\""));
        assertFalse(json.contains("\"requestItemId\""));
        assertTrue(json.contains("wss://example.test/ws"));
    }

    @Test(description = "应支持一次性保存并加载无界面压测运行所需的完整配置包")
    public void shouldSaveAndLoadPerformanceConfigurationBundle() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-bundle");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.numThreads = 3;
        threadGroupData.loops = 2;

        HttpRequestItem requestItem = new HttpRequestItem();
        requestItem.setId("bundle-request");
        requestItem.setName("Bundle HTTP");
        requestItem.setProtocol(RequestItemProtocolEnum.HTTP);
        requestItem.setUrl("https://example.test/bundle");

        PerformancePlanDocument document = new PerformancePlanDocument(
                PerformancePlanNode.builder()
                        .name("Bundle Plan")
                        .type(NodeType.ROOT)
                        .children(List.of(
                                PerformancePlanNode.builder()
                                        .name("Bundle Users")
                                        .type(NodeType.THREAD_GROUP)
                                        .threadGroupData(threadGroupData)
                                        .children(List.of(
                                                PerformancePlanNode.builder()
                                                        .name("Bundle HTTP")
                                                        .type(NodeType.REQUEST)
                                                        .httpRequestItem(requestItem)
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
        );
        service.saveConfiguration(PerformancePlanConfiguration.builder()
                .planDocument(document)
                .efficientMode(false)
                .trendEnabled(false)
                .reportRealtimeEnabled(true)
                .build());

        PerformancePlanConfiguration loadedConfiguration = service.loadConfiguration();

        assertNotNull(loadedConfiguration);
        assertFalse(loadedConfiguration.isEfficientMode());
        assertFalse(loadedConfiguration.isTrendEnabled());
        assertTrue(loadedConfiguration.isReportRealtimeEnabled());

        PerformancePlanNode loadedThreadGroup = loadedConfiguration.getPlanDocument().getRoot().getChildren().get(0);
        PerformancePlanNode loadedRequest = loadedThreadGroup.getChildren().get(0);
        assertEquals(loadedConfiguration.getPlanDocument().getRoot().getName(), "Bundle Plan");
        assertEquals(loadedThreadGroup.getThreadGroupData().numThreads, 3);
        assertEquals(loadedThreadGroup.getThreadGroupData().loops, 2);
        assertEquals(loadedRequest.getHttpRequestItem().getId(), "bundle-request");
        assertEquals(loadedRequest.getHttpRequestItem().getUrl(), "https://example.test/bundle");

        assertFalse(loadedConfiguration.isEfficientMode());
        assertFalse(loadedConfiguration.isTrendEnabled());
        assertTrue(loadedConfiguration.isReportRealtimeEnabled());

        DefaultMutableTreeNode loadedTree = load(service, "Loaded Bundle");
        assertNotNull(loadedTree);
        assertEquals(((PerformanceTreeNode) loadedTree.getUserObject()).name, "Loaded Bundle");
    }

    @Test(description = "应保存并恢复 GUI 远程 worker 配置，便于像 JMeter 一样远程启动全部 worker")
    public void shouldSaveAndLoadRemoteWorkerSettings() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-remote-workers");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        service.saveConfiguration(PerformancePlanConfiguration.builder()
                .efficientMode(true)
                .trendEnabled(true)
                .reportRealtimeEnabled(false)
                .remoteWorkerSettings(PerformanceRemoteWorkerSettings.builder()
                        .enabled(true)
                        .workerEndpoints("127.0.0.1:19090,127.0.0.1:19091")
                        .build())
                .build());

        PerformancePlanConfiguration loadedConfiguration = service.loadConfiguration();

        assertNotNull(loadedConfiguration);
        assertTrue(loadedConfiguration.getRemoteWorkerSettings().isEnabled());
        assertEquals(
                loadedConfiguration.getRemoteWorkerSettings().getWorkerEndpoints(),
                "127.0.0.1:19090,127.0.0.1:19091"
        );

        String json = Files.readString(configPath, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"remoteExecutionEnabled\": true"));
        assertTrue(json.contains("\"remoteWorkers\": \"127.0.0.1:19090,127.0.0.1:19091\""));
    }

    @Test(description = "应保存并恢复请求执行作用域，确保无界面运行器不依赖集合树也能解析分组变量")
    public void shouldSaveAndLoadRequestExecutionScopeInPlanDocument() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-scope");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        HttpRequestItem requestItem = new HttpRequestItem();
        requestItem.setId("scoped-request");
        requestItem.setName("Scoped HTTP");
        requestItem.setProtocol(RequestItemProtocolEnum.HTTP);
        requestItem.setUrl("https://example.test/scope");

        PerformancePlanDocument document = new PerformancePlanDocument(
                PerformancePlanNode.builder()
                        .name("Scoped Plan")
                        .type(NodeType.ROOT)
                        .children(List.of(
                                PerformancePlanNode.builder()
                                        .name("Scoped Users")
                                        .type(NodeType.THREAD_GROUP)
                                        .threadGroupData(new ThreadGroupData())
                                        .children(List.of(
                                                PerformancePlanNode.builder()
                                                        .name("Scoped HTTP")
                                                        .type(NodeType.REQUEST)
                                                        .httpRequestItem(requestItem)
                                                        .requestExecutionScope(RequestExecutionScope.fromGroupVariables(
                                                                Map.of("tenantId", "persisted-tenant")
                                                        ))
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
        );

        service.saveConfiguration(PerformancePlanConfiguration.builder()
                .planDocument(document)
                .efficientMode(true)
                .trendEnabled(true)
                .reportRealtimeEnabled(false)
                .build());

        PerformancePlanDocument loadedDocument = service.loadConfiguration().getPlanDocument();

        PerformancePlanNode loadedRequest = loadedDocument.getRoot().getChildren().get(0).getChildren().get(0);
        assertEquals(loadedRequest.getRequestExecutionScope().getGroupVariable("tenantId"), "persisted-tenant");
    }

    @Test(description = "旧版单计划配置不再兼容加载")
    public void shouldRejectLegacySinglePlanConfiguration() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-no-csv");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        Files.writeString(configPath, """
                {
                  "version": "1.0",
                  "efficientMode": true,
                  "tree": {
                    "name": "Plan",
                    "type": "ROOT",
                    "enabled": true
                  }
	                }
	                """, StandardCharsets.UTF_8);

        assertNull(service.loadConfiguration());
        assertFalse(Files.exists(configPath));
    }

    @Test(description = "空 CSV 状态不应写出脏数据")
    public void shouldKeepCsvStateEmptyWhenNotProvided() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-empty");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));
        save(service, root, true);

        String json = Files.readString(configPath, StandardCharsets.UTF_8);
        assertFalse(json.contains("\"csvState\""));
    }

    @Test(description = "应保存并恢复 WebSocket 发送和等待步骤的独立配置")
    public void shouldPersistWebSocketStepConfigs() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-ws-step");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));
        DefaultMutableTreeNode requestNode = new DefaultMutableTreeNode(new PerformanceTreeNode("WebSocket Example", NodeType.REQUEST));

        PerformanceTreeNode sendNode = new PerformanceTreeNode("WS Send", NodeType.WS_SEND);
        sendNode.webSocketPerformanceData = new WebSocketPerformanceData();
        sendNode.webSocketPerformanceData.sendMode = WebSocketPerformanceData.SendMode.REQUEST_BODY_ON_CONNECT;
        sendNode.webSocketPerformanceData.sendContentSource = WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT;
        sendNode.webSocketPerformanceData.customSendBody = "a-{{user-a}}";
        sendNode.webSocketPerformanceData.sendPreScript = "pm.variables.set('a', 'dynamic');";
        requestNode.add(new DefaultMutableTreeNode(sendNode));

        PerformanceTreeNode readNode = new PerformanceTreeNode("WS Read", NodeType.WS_READ);
        readNode.webSocketPerformanceData = new WebSocketPerformanceData();
        readNode.webSocketPerformanceData.completionMode = WebSocketPerformanceData.CompletionMode.UNTIL_MATCH;
        readNode.webSocketPerformanceData.firstMessageTimeoutMs = 10000;
        readNode.webSocketPerformanceData.messageFilter = "a";
        requestNode.add(new DefaultMutableTreeNode(readNode));

        root.add(requestNode);

        save(service, root, false);

        DefaultMutableTreeNode loadedRoot = load(service, "Loaded Plan");
        DefaultMutableTreeNode loadedRequestNode = (DefaultMutableTreeNode) loadedRoot.getChildAt(0);
        PerformanceTreeNode loadedSendNode = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedRequestNode.getChildAt(0)).getUserObject();
        PerformanceTreeNode loadedReadNode = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedRequestNode.getChildAt(1)).getUserObject();

        assertNotNull(loadedSendNode.webSocketPerformanceData);
        assertEquals(loadedSendNode.webSocketPerformanceData.sendContentSource,
                WebSocketPerformanceData.SendContentSource.CUSTOM_TEXT);
        assertEquals(loadedSendNode.webSocketPerformanceData.customSendBody, "a-{{user-a}}");
        assertEquals(loadedSendNode.webSocketPerformanceData.sendPreScript, "pm.variables.set('a', 'dynamic');");
        assertNotNull(loadedReadNode.webSocketPerformanceData);
        assertEquals(loadedReadNode.webSocketPerformanceData.completionMode,
                WebSocketPerformanceData.CompletionMode.UNTIL_MATCH);
        assertEquals(loadedReadNode.webSocketPerformanceData.messageFilter, "a");
    }

    @Test(description = "应保存并恢复 SSE 阶段节点的独立配置")
    public void shouldPersistSseStageConfigs() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-sse-stage");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));
        DefaultMutableTreeNode requestNode = new DefaultMutableTreeNode(new PerformanceTreeNode("SSE Example", NodeType.REQUEST));

        PerformanceTreeNode connectNode = new PerformanceTreeNode("SSE Connect", NodeType.SSE_CONNECT);
        connectNode.ssePerformanceData = new SsePerformanceData();
        connectNode.ssePerformanceData.connectTimeoutMs = 3456;
        requestNode.add(new DefaultMutableTreeNode(connectNode));

        PerformanceTreeNode readNode = new PerformanceTreeNode("SSE Read", NodeType.SSE_READ);
        readNode.ssePerformanceData = new SsePerformanceData();
        readNode.ssePerformanceData.completionMode = SsePerformanceData.CompletionMode.STREAM_CLOSED;
        readNode.ssePerformanceData.holdConnectionMs = 30000;
        requestNode.add(new DefaultMutableTreeNode(readNode));

        root.add(requestNode);

        save(service, root, false);

        DefaultMutableTreeNode loadedRoot = load(service, "Loaded Plan");
        DefaultMutableTreeNode loadedRequestNode = (DefaultMutableTreeNode) loadedRoot.getChildAt(0);
        PerformanceTreeNode loadedConnectNode = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedRequestNode.getChildAt(0)).getUserObject();
        PerformanceTreeNode loadedReadNode = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedRequestNode.getChildAt(1)).getUserObject();

        assertNotNull(loadedConnectNode.ssePerformanceData);
        assertEquals(loadedConnectNode.ssePerformanceData.connectTimeoutMs, 3456);
        assertNotNull(loadedReadNode.ssePerformanceData);
        assertEquals(loadedReadNode.ssePerformanceData.completionMode,
                SsePerformanceData.CompletionMode.STREAM_CLOSED);
        assertEquals(loadedReadNode.ssePerformanceData.holdConnectionMs, 30000);
    }

    @Test(description = "WebSocket 配置遇到未知枚举值时不再兼容回退")
    public void shouldRejectUnknownWebSocketEnumsWhenLoading() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-ws-defaults");
        Path configPath = tempDir.resolve("performance_config.json");
        Files.writeString(configPath, """
                {
                  "version": "1.1",
                  "activePlanId": "plan-a",
                  "plans": [
                    {
                      "id": "plan-a",
                      "name": "Plan A",
                      "efficientMode": true,
                      "trendEnabled": true,
                      "reportRealtimeEnabled": false,
                      "remoteExecutionEnabled": false,
                      "remoteWorkers": "",
                      "tree": {
                        "name": "Plan",
                        "type": "ROOT",
                        "enabled": true,
                        "children": [
                          {
                            "name": "WebSocket Example",
                            "type": "REQUEST",
                            "enabled": true,
                            "webSocketPerformanceData": {
                              "sendMode": "UNKNOWN_SEND",
                              "sendContentSource": "UNKNOWN_BODY",
                              "completionMode": "MATCHED_MESSAGE",
                              "firstMessageTimeoutMs": 2222,
                              "targetMessageCount": 3,
                              "messageFilter": "ack"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);

        assertNull(service.loadConfiguration());
        assertFalse(Files.exists(configPath));
    }

    @Test(description = "SSE Read 阶段配置遇到未知枚举值时不再兼容回退")
    public void shouldRejectUnknownSseReadEnumsWhenLoading() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-sse-defaults");
        Path configPath = tempDir.resolve("performance_config.json");
        Files.writeString(configPath, """
                {
                  "version": "1.1",
                  "activePlanId": "plan-a",
                  "plans": [
                    {
                      "id": "plan-a",
                      "name": "Plan A",
                      "efficientMode": true,
                      "trendEnabled": true,
                      "reportRealtimeEnabled": false,
                      "remoteExecutionEnabled": false,
                      "remoteWorkers": "",
                      "tree": {
                        "name": "Plan",
                        "type": "ROOT",
                        "enabled": true,
                        "children": [
                          {
                            "name": "SSE Example",
                            "type": "REQUEST",
                            "enabled": true,
                            "children": [
                              {
                                "name": "SSE Read",
                                "type": "SSE_READ",
                                "enabled": true,
                                "ssePerformanceData": {
                                  "completionMode": "UNKNOWN_COMPLETION",
                                  "firstMessageTimeoutMs": 2222,
                                  "targetMessageCount": 3,
                                  "eventNameFilter": "orders",
                                  "messageFilter": "ready"
                                }
                              }
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);

        assertNull(service.loadConfiguration());
        assertFalse(Files.exists(configPath));
    }

    @Test(description = "应保存并恢复 WebSocket Connect 步骤的独立配置")
    public void shouldPersistWebSocketConnectStepConfig() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-ws-connect-step");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));
        DefaultMutableTreeNode requestNode = new DefaultMutableTreeNode(new PerformanceTreeNode("WebSocket Example", NodeType.REQUEST));

        PerformanceTreeNode connectNode = new PerformanceTreeNode("WS Connect", NodeType.WS_CONNECT);
        connectNode.webSocketPerformanceData = new WebSocketPerformanceData();
        connectNode.webSocketPerformanceData.connectTimeoutMs = 12345;
        requestNode.add(new DefaultMutableTreeNode(connectNode));
        root.add(requestNode);

        save(service, root, false);

        DefaultMutableTreeNode loadedRoot = load(service, "Loaded Plan");
        DefaultMutableTreeNode loadedRequestNode = (DefaultMutableTreeNode) loadedRoot.getChildAt(0);
        PerformanceTreeNode loadedConnectNode = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedRequestNode.getChildAt(0)).getUserObject();

        assertNotNull(loadedConnectNode.webSocketPerformanceData);
        assertEquals(loadedConnectNode.webSocketPerformanceData.connectTimeoutMs, 12345);
    }

    @Test(description = "应保存并恢复通用 Loop 控制器配置")
    public void shouldPersistLoopControllerConfig() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-loop");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));
        DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new PerformanceTreeNode("Users", NodeType.THREAD_GROUP));
        PerformanceTreeNode loop = new PerformanceTreeNode("Loop [4x]", NodeType.LOOP);
        loop.loopData = new LoopData();
        loop.loopData.iterations = 4;
        DefaultMutableTreeNode loopNode = new DefaultMutableTreeNode(loop);
        groupNode.add(loopNode);
        root.add(groupNode);

        save(service, root, false);

        DefaultMutableTreeNode loadedRoot = load(service, "Loaded Plan");
        DefaultMutableTreeNode loadedGroupNode = (DefaultMutableTreeNode) loadedRoot.getChildAt(0);
        PerformanceTreeNode loadedLoop = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedGroupNode.getChildAt(0)).getUserObject();

        assertNotNull(loadedLoop.loopData);
        assertEquals(loadedLoop.loopData.iterations, 4);
    }

    @Test(description = "应保存并恢复所有性能节点配置对象")
    public void shouldPersistAllPerformanceNodeConfigs() throws IOException {
        Path tempDir = Files.createTempDirectory("performance-persistence-all-nodes");
        Path configPath = tempDir.resolve("performance_config.json");
        TestablePerformancePersistenceService service = new TestablePerformancePersistenceService(configPath);
        service.init();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new PerformanceTreeNode("Plan", NodeType.ROOT));

        PerformanceTreeNode threadGroup = new PerformanceTreeNode("Users", NodeType.THREAD_GROUP);
        threadGroup.threadGroupData = new ThreadGroupData();
        threadGroup.threadGroupData.numThreads = 7;
        threadGroup.threadGroupData.loops = 3;
        DefaultMutableTreeNode threadGroupNode = new DefaultMutableTreeNode(threadGroup);
        root.add(threadGroupNode);

        DefaultMutableTreeNode sseRequestNode = new DefaultMutableTreeNode(new PerformanceTreeNode("SSE Request", NodeType.REQUEST));
        PerformanceTreeNode sseConnect = new PerformanceTreeNode("SSE Connect", NodeType.SSE_CONNECT);
        sseConnect.ssePerformanceData = new SsePerformanceData();
        sseConnect.ssePerformanceData.connectTimeoutMs = 4321;
        sseRequestNode.add(new DefaultMutableTreeNode(sseConnect));
        PerformanceTreeNode sseRead = new PerformanceTreeNode("SSE Read", NodeType.SSE_READ);
        sseRead.ssePerformanceData = new SsePerformanceData();
        sseRead.ssePerformanceData.completionMode = SsePerformanceData.CompletionMode.MESSAGE_COUNT;
        sseRead.ssePerformanceData.firstMessageTimeoutMs = 1234;
        sseRead.ssePerformanceData.holdConnectionMs = 5678;
        sseRead.ssePerformanceData.targetMessageCount = 9;
        sseRead.ssePerformanceData.eventNameFilter = "orders";
        sseRead.ssePerformanceData.messageFilter = "done";
        sseRequestNode.add(new DefaultMutableTreeNode(sseRead));
        threadGroupNode.add(sseRequestNode);

        PerformanceTreeNode wsRequest = new PerformanceTreeNode("WS Request", NodeType.REQUEST);
        wsRequest.webSocketPerformanceData = new WebSocketPerformanceData();
        wsRequest.webSocketPerformanceData.connectTimeoutMs = 4321;
        DefaultMutableTreeNode wsRequestNode = new DefaultMutableTreeNode(wsRequest);
        wsRequestNode.add(new DefaultMutableTreeNode(new PerformanceTreeNode("WS Connect", NodeType.WS_CONNECT)));

        PerformanceTreeNode assertion = new PerformanceTreeNode("Status Assertion", NodeType.ASSERTION);
        assertion.assertionData = new AssertionData();
        assertion.assertionData.type = "Response Code";
        assertion.assertionData.operator = "=";
        assertion.assertionData.value = "200";
        wsRequestNode.add(new DefaultMutableTreeNode(assertion));

        PerformanceTreeNode extractor = new PerformanceTreeNode("Token Extractor", NodeType.EXTRACTOR);
        extractor.extractorData = new ExtractorData();
        extractor.extractorData.type = "JSONPath";
        extractor.extractorData.expression = "$.token";
        extractor.extractorData.variableName = "token";
        extractor.extractorData.defaultValue = "missing";
        wsRequestNode.add(new DefaultMutableTreeNode(extractor));

        PerformanceTreeNode timer = new PerformanceTreeNode("Think Time", NodeType.TIMER);
        timer.timerData = new TimerData();
        timer.timerData.delayMs = 250;
        wsRequestNode.add(new DefaultMutableTreeNode(timer));
        threadGroupNode.add(wsRequestNode);

        save(service, root, false);

        DefaultMutableTreeNode loadedRoot = load(service, "Loaded Plan");
        DefaultMutableTreeNode loadedThreadGroupNode = (DefaultMutableTreeNode) loadedRoot.getChildAt(0);
        PerformanceTreeNode loadedThreadGroup = (PerformanceTreeNode) loadedThreadGroupNode.getUserObject();
        DefaultMutableTreeNode loadedSseRequestNode = (DefaultMutableTreeNode) loadedThreadGroupNode.getChildAt(0);
        PerformanceTreeNode loadedSseConnect = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedSseRequestNode.getChildAt(0)).getUserObject();
        PerformanceTreeNode loadedSseRead = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedSseRequestNode.getChildAt(1)).getUserObject();
        DefaultMutableTreeNode loadedWsRequestNode = (DefaultMutableTreeNode) loadedThreadGroupNode.getChildAt(1);
        PerformanceTreeNode loadedWsRequest = (PerformanceTreeNode) loadedWsRequestNode.getUserObject();
        PerformanceTreeNode loadedAssertion = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedWsRequestNode.getChildAt(1)).getUserObject();
        PerformanceTreeNode loadedExtractor = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedWsRequestNode.getChildAt(2)).getUserObject();
        PerformanceTreeNode loadedTimer = (PerformanceTreeNode) ((DefaultMutableTreeNode) loadedWsRequestNode.getChildAt(3)).getUserObject();

        assertEquals(loadedThreadGroup.threadGroupData.numThreads, 7);
        assertEquals(loadedThreadGroup.threadGroupData.loops, 3);
        assertEquals(loadedSseConnect.ssePerformanceData.connectTimeoutMs, 4321);
        assertEquals(loadedSseRead.ssePerformanceData.completionMode, SsePerformanceData.CompletionMode.MESSAGE_COUNT);
        assertEquals(loadedSseRead.ssePerformanceData.firstMessageTimeoutMs, 1234);
        assertEquals(loadedSseRead.ssePerformanceData.holdConnectionMs, 5678);
        assertEquals(loadedSseRead.ssePerformanceData.targetMessageCount, 9);
        assertEquals(loadedSseRead.ssePerformanceData.eventNameFilter, "orders");
        assertEquals(loadedSseRead.ssePerformanceData.messageFilter, "done");
        assertEquals(loadedWsRequest.webSocketPerformanceData.connectTimeoutMs, 4321);
        assertEquals(loadedAssertion.assertionData.value, "200");
        assertEquals(loadedExtractor.extractorData.variableName, "token");
        assertEquals(loadedExtractor.extractorData.defaultValue, "missing");
        assertEquals(loadedTimer.timerData.delayMs, 250);
    }

    private static Map<String, String> row(String... keyValues) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put(keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private static void save(PerformancePersistenceService service,
                             DefaultMutableTreeNode root,
                             boolean efficientMode) {
        save(service, root, efficientMode, true, false);
    }

    private static void save(PerformancePersistenceService service,
                             DefaultMutableTreeNode root,
                             boolean efficientMode,
                             boolean trendEnabled) {
        save(service, root, efficientMode, trendEnabled, false);
    }

    private static void save(PerformancePersistenceService service,
                             DefaultMutableTreeNode root,
                             boolean efficientMode,
                             boolean trendEnabled,
                             boolean reportRealtimeEnabled) {
        service.saveConfiguration(PerformancePlanConfiguration.builder()
                .planDocument(PerformanceSwingTreePlanAdapter.toDocument(root))
                .efficientMode(efficientMode)
                .trendEnabled(trendEnabled)
                .reportRealtimeEnabled(reportRealtimeEnabled)
                .remoteWorkerSettings(PerformanceRemoteWorkerSettings.disabled())
                .build());
    }

    private static void saveAsync(PerformancePersistenceService service,
                                  DefaultMutableTreeNode root,
                                  boolean efficientMode) {
        PerformancePlanConfiguration configuration = PerformancePlanConfiguration.builder()
                .planDocument(PerformanceSwingTreePlanAdapter.toDocument(root))
                .efficientMode(efficientMode)
                .trendEnabled(true)
                .reportRealtimeEnabled(false)
                .remoteWorkerSettings(PerformanceRemoteWorkerSettings.disabled())
                .build();
        service.saveWorkspaceAsync(PerformancePlanWorkspace.builder()
                .activePlanId("async-plan")
                .plans(List.of(PerformanceSavedPlan.fromConfiguration("async-plan", "Plan", configuration)))
                .build());
    }

    private static DefaultMutableTreeNode load(PerformancePersistenceService service, String rootName) {
        PerformancePlanConfiguration configuration = service.loadConfiguration();
        PerformancePlanDocument document = configuration == null ? null : configuration.getPlanDocument();
        return PerformanceSwingTreePlanAdapter.toTree(document, rootName);
    }

    private static Workspace workspace(Path workspaceDir) {
        Workspace workspace = new Workspace();
        workspace.setPath(workspaceDir.toString());
        return workspace;
    }

    private static boolean awaitExists(Path path) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (Files.exists(path)) {
                return true;
            }
            Thread.sleep(20);
        }
        return Files.exists(path);
    }

    private static final class TestablePerformancePersistenceService extends PerformancePersistenceService {
        private final Path configPath;

        private TestablePerformancePersistenceService(Path configPath) {
            this.configPath = configPath;
        }

        @Override
        protected Path getConfigFilePath() {
            return configPath;
        }
    }

    private static final class WorkspaceAwarePerformancePersistenceService extends PerformancePersistenceService {
        private final Workspace workspace;

        private WorkspaceAwarePerformancePersistenceService(Workspace workspace) {
            this.workspace = workspace;
        }

        @Override
        protected Workspace getCurrentWorkspace() {
            return workspace;
        }
    }

    private static final class BlockingWorkspacePerformancePersistenceService extends PerformancePersistenceService {
        private final Thread testThread = Thread.currentThread();
        private final CountDownLatch workerWorkspaceLookupStarted = new CountDownLatch(1);
        private final CountDownLatch releaseWorkerWorkspaceLookup = new CountDownLatch(1);
        private volatile Workspace workspace;

        private BlockingWorkspacePerformancePersistenceService(Workspace workspace) {
            this.workspace = workspace;
        }

        private void setWorkspace(Workspace workspace) {
            this.workspace = workspace;
        }

        private boolean awaitWorkerWorkspaceLookup() throws InterruptedException {
            return workerWorkspaceLookupStarted.await(200, TimeUnit.MILLISECONDS);
        }

        private void releaseWorkerWorkspaceLookup() {
            releaseWorkerWorkspaceLookup.countDown();
        }

        @Override
        protected Workspace getCurrentWorkspace() {
            if (Thread.currentThread() != testThread) {
                workerWorkspaceLookupStarted.countDown();
                try {
                    releaseWorkerWorkspaceLookup.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return workspace;
        }
    }
}
