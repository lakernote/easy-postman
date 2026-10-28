package com.laker.postman.performance.plan;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.plan.PerformanceLoopController;
import com.laker.postman.performance.core.plan.PerformanceTestPlan;
import com.laker.postman.performance.core.plan.PerformanceThreadGroupPlan;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestProtocol;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PerformancePlanDocumentCompilerTest {

    @Test
    public void documentCompilerShouldNotExposeSwingTreeTypes() {
        assertFalse(hasParameterTypeName(PerformancePlanDocumentCompiler.class, "javax.swing.tree.DefaultMutableTreeNode"));
    }

    @Test
    public void shouldCompileRuntimePlanFromPureDocument() {
        LoopData loopData = new LoopData();
        loopData.iterations = 2;

        PerformancePlanNode request = PerformancePlanNode.builder()
                .name("request")
                .type(NodeType.REQUEST)
                .httpRequestItem(requestItem("request"))
                .build();
        PerformancePlanNode loop = PerformancePlanNode.builder()
                .name("loop")
                .type(NodeType.LOOP)
                .loopData(loopData)
                .children(List.of(request))
                .build();
        PerformancePlanNode disabledGroup = PerformancePlanNode.builder()
                .name("disabled group")
                .type(NodeType.THREAD_GROUP)
                .enabled(false)
                .threadGroupData(threadGroupData())
                .children(List.of(request))
                .build();
        PerformancePlanNode enabledGroup = PerformancePlanNode.builder()
                .name("enabled group")
                .type(NodeType.THREAD_GROUP)
                .threadGroupData(threadGroupData())
                .children(List.of(loop))
                .build();
        PerformancePlanDocument document = new PerformancePlanDocument(
                PerformancePlanNode.builder()
                        .name("plan")
                        .type(NodeType.ROOT)
                        .children(List.of(disabledGroup, enabledGroup))
                        .build()
        );

        PerformanceTestPlan plan = PerformancePlanDocumentCompiler.compile(document);

        assertEquals(plan.getThreadGroups().size(), 1);
        PerformanceThreadGroupPlan group = plan.getThreadGroups().get(0);
        assertEquals(group.getName(), "enabled group");
        assertEquals(group.getElements().size(), 1);
        assertTrue(group.getElements().get(0) instanceof PerformanceLoopController);
        PerformanceLoopController loopController = (PerformanceLoopController) group.getElements().get(0);
        assertEquals(loopController.getIterationCount(), 2);
        assertTrue(loopController.getElements().get(0) instanceof PerformanceRequestSampler);
    }

    @Test
    public void requestNodeNameShouldOverrideStaleSnapshotName() {
        PerformanceRequestSnapshot staleSnapshot = PerformanceRequestSnapshot.builder()
                .id("request-1")
                .name("213")
                .url("https://example.test")
                .protocol(PerformanceRequestProtocol.HTTP)
                .build();
        PerformancePlanNode request = PerformancePlanNode.builder()
                .name("111")
                .type(NodeType.REQUEST)
                .requestSnapshot(staleSnapshot)
                .build();

        PerformanceRequestSampler sampler = PerformancePlanDocumentCompiler.compileRequestSampler(request);

        assertEquals(sampler.getName(), "111");
        assertEquals(sampler.getRequestSnapshot().getName(), "111");
        assertEquals(sampler.getHttpRequestItem().getName(), "111");
    }

    private static boolean hasParameterTypeName(Class<?> type, String parameterTypeName) {
        boolean methodParameter = java.util.Arrays.stream(type.getMethods())
                .flatMap(method -> java.util.Arrays.stream(method.getParameterTypes()))
                .map(Class::getName)
                .anyMatch(parameterTypeName::equals);
        boolean constructorParameter = java.util.Arrays.stream(type.getConstructors())
                .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
                .map(Class::getName)
                .anyMatch(parameterTypeName::equals);
        return methodParameter || constructorParameter;
    }

    private static ThreadGroupData threadGroupData() {
        ThreadGroupData data = new ThreadGroupData();
        data.numThreads = 1;
        data.useTime = false;
        data.loops = 1;
        return data;
    }

    private static HttpRequestItem requestItem(String name) {
        HttpRequestItem item = new HttpRequestItem();
        item.setId(name + "-id");
        item.setName(name);
        item.setProtocol(RequestItemProtocolEnum.HTTP);
        return item;
    }
}
