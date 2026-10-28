package com.laker.postman.performance.plan;

import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.plan.PerformanceCorePlanDocument;
import com.laker.postman.performance.core.plan.PerformanceCorePlanNode;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestProtocol;
import com.laker.postman.performance.core.run.PerformanceRunPlan;
import com.laker.postman.performance.core.run.PerformanceRunPlanJsonStorage;
import com.laker.postman.performance.core.run.PerformanceRunSettings;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class PerformancePlanImportServiceTest {

    @Test
    public void shouldImportExecutableRunPlanAsEditablePlanCandidate() throws Exception {
        Path planPath = Files.createTempDirectory("performance-run-plan-import").resolve("plan.json");
        new PerformanceRunPlanJsonStorage().save(planPath, PerformanceRunPlan.builder()
                .settings(PerformanceRunSettings.builder()
                        .efficientMode(false)
                        .build())
                .testPlan(coreDocument("Imported Run Plan", "https://example.test/run"))
                .build());

        PerformancePlanImportResult result = new PerformancePlanImportService().importPlans(planPath);

        assertEquals(result.getPlans().size(), 1);
        PerformancePlanImportCandidate importedPlan = result.getPlans().get(0);
        assertEquals(importedPlan.getName(), "Imported Run Plan");
        assertFalse(importedPlan.getConfiguration().isEfficientMode());
        assertTrue(importedPlan.getConfiguration().isTrendEnabled());
        assertEquals(importedPlan.getConfiguration()
                .getPlanDocument()
                .getRoot()
                .getChildren()
                .get(0)
                .getChildren()
                .get(0)
                .getHttpRequestItem()
                .getUrl(), "https://example.test/run");
    }

    @Test
    public void shouldImportWorkspacePlansAsEditablePlanCandidates() throws Exception {
        Path configPath = Files.createTempDirectory("performance-workspace-import").resolve("performance_config.json");
        PerformancePlanStorage storage = new PerformancePlanStorage();
        storage.saveWorkspace(configPath, PerformancePlanWorkspace.builder()
                .activePlanId("plan-b")
                .plans(List.of(
                        savedPlan("plan-a", "Plan A", "https://example.test/a", true),
                        savedPlan("plan-b", "Plan B", "https://example.test/b", false)
                ))
                .build());

        PerformancePlanImportResult result = new PerformancePlanImportService().importPlans(configPath);

        assertEquals(result.getPlans().size(), 2);
        assertEquals(result.getPlans().get(0).getName(), "Plan A");
        assertEquals(result.getPlans().get(1).getName(), "Plan B");
        assertTrue(result.getPlans().get(0).getConfiguration().isReportRealtimeEnabled());
        assertFalse(result.getPlans().get(1).getConfiguration().isReportRealtimeEnabled());
    }

    @Test
    public void shouldRejectLegacySinglePlanConfigInsteadOfImportingWithDefaultSettings() throws Exception {
        Path legacyConfigPath = Files.createTempDirectory("performance-legacy-config-import").resolve("performance_config.json");
        Files.writeString(legacyConfigPath, """
                {
                  "version": "1.0",
                  "efficientMode": false,
                  "trendEnabled": false,
                  "reportRealtimeEnabled": true,
                  "remoteExecutionEnabled": true,
                  "remoteWorkers": "127.0.0.1:19090",
                  "tree": {
                    "name": "Legacy Plan",
                    "type": "ROOT",
                    "enabled": true
                  }
                }
                """);

        IllegalArgumentException ex = expectThrows(
                IllegalArgumentException.class,
                () -> new PerformancePlanImportService().importPlans(legacyConfigPath)
        );

        assertTrue(ex.getMessage().contains("legacy single-plan"), ex.getMessage());
        assertTrue(Files.exists(legacyConfigPath));
    }

    @Test
    public void invalidImportFileShouldNotBeDeleted() throws Exception {
        Path invalidPath = Files.createTempDirectory("performance-invalid-import").resolve("plan.json");
        Files.writeString(invalidPath, "{not-json");

        assertThrows(RuntimeException.class, () -> new PerformancePlanImportService().importPlans(invalidPath));
        assertTrue(Files.exists(invalidPath));
    }

    private static PerformanceSavedPlan savedPlan(String id, String name, String url, boolean reportRealtimeEnabled) {
        return PerformanceSavedPlan.builder()
                .id(id)
                .name(name)
                .planDocument(appDocument(name, url))
                .efficientMode(true)
                .trendEnabled(true)
                .reportRealtimeEnabled(reportRealtimeEnabled)
                .build();
    }

    private static PerformancePlanDocument appDocument(String rootName, String url) {
        return PerformanceCorePlanAdapter.toAppDocument(coreDocument(rootName, url));
    }

    private static PerformanceCorePlanDocument coreDocument(String rootName, String url) {
        ThreadGroupData threadGroupData = new ThreadGroupData();
        threadGroupData.numThreads = 2;
        return new PerformanceCorePlanDocument(PerformanceCorePlanNode.builder()
                .name(rootName)
                .type(NodeType.ROOT)
                .children(List.of(
                        PerformanceCorePlanNode.builder()
                                .name(rootName + " Users")
                                .type(NodeType.THREAD_GROUP)
                                .threadGroupData(threadGroupData)
                                .children(List.of(
                                        PerformanceCorePlanNode.builder()
                                                .name(rootName + " Request")
                                                .type(NodeType.REQUEST)
                                                .requestSnapshot(PerformanceRequestSnapshot.builder()
                                                        .id(rootName + "-request")
                                                        .name(rootName + " Request")
                                                        .protocol(PerformanceRequestProtocol.HTTP)
                                                        .method("GET")
                                                        .url(url)
                                                        .build())
                                                .build()
                                ))
                                .build()
                ))
                .build());
    }
}
