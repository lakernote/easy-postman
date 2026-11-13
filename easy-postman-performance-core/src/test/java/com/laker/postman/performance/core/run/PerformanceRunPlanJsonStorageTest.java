package com.laker.postman.performance.core.run;

import com.laker.postman.performance.core.config.CsvDataSetData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.plan.PerformanceCorePlanDocument;
import com.laker.postman.performance.core.plan.PerformanceCorePlanNode;
import com.laker.postman.performance.core.request.PerformanceRequestFormDataPart;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class PerformanceRunPlanJsonStorageTest {

    @Test
    public void shouldRoundTripExecutableRunPlanEnvelopeWithRuntimeSemantics() throws Exception {
        PerformanceCorePlanDocument document = runDocument();
        PerformanceRunPlan plan = PerformanceRunPlan.builder()
                .generatedBy("EasyPostman Test")
                .environment(new PerformanceRunEnvironment(
                        "env-dev",
                        "Dev",
                        List.of(new PerformanceRunVariable(true, "baseUrl", "https://example.test"))
                ))
                .globals(new PerformanceRunVariableSet(
                        List.of(new PerformanceRunVariable(true, "token", "abc123"))
                ))
                .settings(PerformanceRunSettings.builder()
                        .efficientMode(false)
                        .httpMaxIdleConnections(12)
                        .httpKeepAliveSeconds(34L)
                        .httpMaxRequests(123)
                        .httpMaxRequestsPerHost(45)
                        .responseBodyPreviewLimitKb(256)
                        .build())
                .testPlan(document)
                .assets(PerformanceRunPlanAssetScanner.scan(document))
                .build();
        PerformanceRunPlanJsonStorage storage = new PerformanceRunPlanJsonStorage();

        String json = storage.toJson(plan);

        assertTrue(json.contains("\"schemaVersion\""));
        assertTrue(json.contains("\"environment\""));
        assertTrue(json.contains("\"globals\""));
        assertTrue(json.contains("\"settings\""));
        assertTrue(json.contains("\"httpMaxRequests\""));
        assertTrue(json.contains("\"httpMaxRequestsPerHost\""));
        assertFalse(json.contains("\"trendEnabled\""));
        assertFalse(json.contains("\"reportRealtimeEnabled\""));
        assertTrue(json.contains("\"testPlan\""));
        assertTrue(json.contains("\"assets/data/users.csv\""));
        assertTrue(json.contains("\"assets/files/avatar.png\""));
        assertFalse(json.contains("\"requestItem\""));

        PerformanceRunPlan loaded = storage.fromJson(json);

        assertEquals(loaded.getSchemaVersion(), PerformanceRunPlanJsonStorage.FORMAT_VERSION);
        assertEquals(loaded.getGeneratedBy(), "EasyPostman Test");
        assertEquals(loaded.getEnvironment().getName(), "Dev");
        assertEquals(loaded.getEnvironment().getVariables().get(0).getKey(), "baseUrl");
        assertEquals(loaded.getGlobals().getVariables().get(0).getValue(), "abc123");
        assertFalse(loaded.getSettings().isEfficientMode());
        assertEquals(loaded.getSettings().getResponseBodyPreviewLimitKb(), 256);
        assertEquals(loaded.getSettings().getHttpMaxIdleConnections(), 12);
        assertEquals(loaded.getSettings().getHttpKeepAliveSeconds(), 34L);
        assertEquals(loaded.getSettings().getHttpMaxRequests(), 123);
        assertEquals(loaded.getSettings().getHttpMaxRequestsPerHost(), 45);
        assertEquals(loaded.getAssets().size(), 2);

        PerformanceCorePlanNode loadedGroup = loaded.getTestPlan().getRoot().getChildren().get(0);
        CsvDataSetData loadedCsv = loadedGroup.getChildren().get(0).getCsvDataSetData();
        assertNotNull(loadedCsv);
        assertTrue(loadedCsv.isFileSource());
        assertEquals(loadedCsv.getFilePath(), "assets/data/users.csv");

        PerformanceRequestSnapshot loadedRequest = loadedGroup.getChildren().get(1).getRequestSnapshot();
        assertEquals(loadedRequest.getFormData().get(0).getValue(), "assets/files/avatar.png");
        assertEquals(loadedRequest.getProxyPolicy(), PerformanceRequestSnapshot.PROXY_POLICY_USE_PROXY);
    }

    @Test
    public void shouldSaveAndLoadRunPlanFile() throws Exception {
        PerformanceRunPlanJsonStorage storage = new PerformanceRunPlanJsonStorage();
        Path planPath = Files.createTempDirectory("ep-run-plan").resolve("plan.json");
        PerformanceRunPlan plan = PerformanceRunPlan.builder()
                .generatedBy("EasyPostman Test")
                .testPlan(runDocument())
                .assets(PerformanceRunPlanAssetScanner.scan(runDocument()))
                .build();

        storage.save(planPath, plan);
        PerformanceRunPlan loaded = storage.load(planPath);

        assertEquals(loaded.getTestPlan().getRoot().getName(), "run plan");
        assertEquals(loaded.getAssets().size(), 2);
        assertEquals(loaded.getSettings().getResponseBodyPreviewLimitKb(),
                PerformanceRunSettings.DEFAULT_RESPONSE_BODY_PREVIEW_LIMIT_KB);
    }

    private static PerformanceCorePlanDocument runDocument() {
        CsvDataSetData csvData = CsvDataSetData.file("users.csv", "assets/data/users.csv");
        csvData.setEncoding("UTF-8");
        csvData.setDelimiter(",");
        csvData.setHasHeader(true);
        csvData.setSharingMode(CsvDataSetData.SHARING_THREAD_GROUP);
        csvData.setEofMode(CsvDataSetData.EOF_RECYCLE);

        PerformanceCorePlanNode csv = PerformanceCorePlanNode.builder()
                .name("Users CSV")
                .type(NodeType.CSV_DATA_SET)
                .csvDataSetData(csvData)
                .build();

        PerformanceRequestSnapshot request = PerformanceRequestSnapshot.builder()
                .id("upload")
                .name("Upload")
                .url("https://example.test/upload")
                .method("POST")
                .proxyPolicy(PerformanceRequestSnapshot.PROXY_POLICY_USE_PROXY)
                .formData(List.of(new PerformanceRequestFormDataPart(
                        true,
                        "avatar",
                        PerformanceRequestFormDataPart.TYPE_FILE,
                        "assets/files/avatar.png"
                )))
                .build();
        PerformanceCorePlanNode requestNode = PerformanceCorePlanNode.builder()
                .name("Upload")
                .type(NodeType.REQUEST)
                .requestSnapshot(request)
                .build();

        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.numThreads = 2;
        PerformanceCorePlanNode group = PerformanceCorePlanNode.builder()
                .name("Users")
                .type(NodeType.THREAD_GROUP)
                .threadGroupData(threadGroupData)
                .children(List.of(csv, requestNode))
                .build();
        return new PerformanceCorePlanDocument(PerformanceCorePlanNode.builder()
                .name("run plan")
                .type(NodeType.ROOT)
                .children(List.of(group))
                .build());
    }
}
