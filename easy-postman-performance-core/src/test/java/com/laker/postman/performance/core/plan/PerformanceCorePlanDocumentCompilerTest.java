package com.laker.postman.performance.core.plan;

import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.controller.ConditionData;
import com.laker.postman.performance.core.controller.WhileData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.request.PerformanceRequestKeyValue;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestProtocol;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

public class PerformanceCorePlanDocumentCompilerTest {

    @Test
    public void shouldCompilePureCoreDocumentWithoutAppRequestTypes() {
        LoopData loopData = new LoopData();
        loopData.iterations = 2;
        PerformanceRequestSnapshot snapshot = PerformanceRequestSnapshot.builder()
                .id("request-1")
                .name("request")
                .url("wss://example.test/ws")
                .protocol(PerformanceRequestProtocol.WEBSOCKET)
                .headers(List.of(new PerformanceRequestKeyValue(true, "X-Test", "yes")))
                .build();

        PerformanceCorePlanNode request = PerformanceCorePlanNode.builder()
                .name("request")
                .type(NodeType.REQUEST)
                .requestSnapshot(snapshot)
                .build();
        ConditionData conditionData = new ConditionData();
        conditionData.expression = "{{status}} == 200";
        WhileData whileData = new WhileData();
        whileData.expression = "{{retryCount}} < 3";
        whileData.intervalMs = 250;
        whileData.maxIterations = 12;
        PerformanceCorePlanNode condition = PerformanceCorePlanNode.builder()
                .name("condition")
                .type(NodeType.CONDITION)
                .conditionData(conditionData)
                .children(List.of(request))
                .build();
        PerformanceCorePlanNode whileNode = PerformanceCorePlanNode.builder()
                .name("while")
                .type(NodeType.WHILE)
                .whileData(whileData)
                .children(List.of(condition))
                .build();
        PerformanceCorePlanNode onceOnly = PerformanceCorePlanNode.builder()
                .name("once only")
                .type(NodeType.ONCE_ONLY)
                .children(List.of(whileNode))
                .build();
        PerformanceCorePlanNode simple = PerformanceCorePlanNode.builder()
                .name("simple")
                .type(NodeType.SIMPLE)
                .children(List.of(onceOnly))
                .build();
        PerformanceCorePlanNode loop = PerformanceCorePlanNode.builder()
                .name("loop")
                .type(NodeType.LOOP)
                .loopData(loopData)
                .children(List.of(simple))
                .build();
        PerformanceCorePlanNode disabledGroup = PerformanceCorePlanNode.builder()
                .name("disabled")
                .type(NodeType.THREAD_GROUP)
                .enabled(false)
                .threadGroupData(threadGroupData())
                .children(List.of(request))
                .build();
        PerformanceCorePlanNode enabledGroup = PerformanceCorePlanNode.builder()
                .name("enabled")
                .type(NodeType.THREAD_GROUP)
                .threadGroupData(threadGroupData())
                .children(List.of(loop))
                .build();

        PerformanceTestPlan compiled = PerformanceCorePlanDocumentCompiler.compile(
                new PerformanceCorePlanDocument(PerformanceCorePlanNode.builder()
                        .name("plan")
                        .type(NodeType.ROOT)
                        .children(List.of(disabledGroup, enabledGroup))
                        .build())
        );

        assertEquals(compiled.getThreadGroups().size(), 1);
        PerformanceThreadGroupPlan group = compiled.getThreadGroups().get(0);
        assertEquals(group.getName(), "enabled");
        assertTrue(group.getElements().get(0) instanceof PerformanceLoopController);
        PerformanceLoopController loopController = (PerformanceLoopController) group.getElements().get(0);
        assertEquals(loopController.getIterationCount(), 2);
        assertTrue(loopController.getElements().get(0) instanceof PerformanceSimpleController);
        PerformanceSimpleController simpleController = (PerformanceSimpleController) loopController.getElements().get(0);
        assertEquals(simpleController.getName(), "simple");
        assertEquals(simpleController.getIterationCount(), 1);
        assertTrue(simpleController.getElements().get(0) instanceof PerformanceOnceOnlyController);
        PerformanceOnceOnlyController onceOnlyController = (PerformanceOnceOnlyController) simpleController.getElements().get(0);
        assertEquals(onceOnlyController.getName(), "once only");
        assertTrue(onceOnlyController.getElements().get(0) instanceof PerformanceWhileController);
        PerformanceWhileController whileController = (PerformanceWhileController) onceOnlyController.getElements().get(0);
        assertEquals(whileController.getWhileData().expression, "{{retryCount}} < 3");
        assertEquals(whileController.getWhileData().intervalMs, 250);
        assertEquals(whileController.getWhileData().maxIterations, 12);
        assertTrue(whileController.getElements().get(0) instanceof PerformanceConditionController);
        PerformanceConditionController conditionController = (PerformanceConditionController) whileController.getElements().get(0);
        assertEquals(conditionController.getConditionData().expression, "{{status}} == 200");
        assertTrue(conditionController.getElements().get(0) instanceof PerformanceCoreRequestSampler);

        PerformanceCoreRequestSampler sampler = (PerformanceCoreRequestSampler) conditionController.getElements().get(0);
        assertEquals(sampler.getRequestSnapshot().getUrl(), "wss://example.test/ws");
        assertTrue(sampler.executesChildrenInSamplerOrder());
        assertNotSame(sampler.getRequestSnapshot(), snapshot);
    }

    @Test
    public void requestSamplerShouldNotInventEmptyRequestSnapshot() {
        PerformanceCoreRequestSampler sampler = new PerformanceCoreRequestSampler(
                "request",
                null,
                null,
                List.of()
        );

        assertEquals(sampler.getName(), "request");
        assertFalse(sampler.executesChildrenInSamplerOrder());
        assertEquals(sampler.getChildren(), List.of());
    }

    @Test
    public void requestNodeNameShouldOverrideStaleSnapshotName() {
        PerformanceRequestSnapshot staleSnapshot = PerformanceRequestSnapshot.builder()
                .id("request-1")
                .name("213")
                .url("https://example.test")
                .protocol(PerformanceRequestProtocol.HTTP)
                .build();
        PerformanceCorePlanNode request = PerformanceCorePlanNode.builder()
                .name("111")
                .type(NodeType.REQUEST)
                .requestSnapshot(staleSnapshot)
                .build();

        PerformanceCoreRequestSampler sampler = PerformanceCorePlanDocumentCompiler.compileRequestSampler(request);

        assertEquals(sampler.getName(), "111");
        assertEquals(sampler.getRequestSnapshot().getName(), "111");
    }

    private static ThreadGroupData threadGroupData() {
        ThreadGroupData data = new ThreadGroupData();
        data.numThreads = 1;
        data.useTime = false;
        data.loops = 1;
        return data;
    }
}
