package com.laker.postman.panel.performance;

import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.performance.core.assertion.AssertionData;
import com.laker.postman.performance.core.config.CsvDataSetData;
import com.laker.postman.performance.core.controller.ConditionData;
import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.controller.WhileData;
import com.laker.postman.performance.core.extractor.ExtractorData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.SsePerformanceData;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.performance.core.timer.TimerData;


import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.service.variable.RequestExecutionScope;
import com.laker.postman.util.JsonUtil;
import lombok.experimental.UtilityClass;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.UUID;

@UtilityClass
public class PerformanceTreeSnapshot {

    public DefaultMutableTreeNode copy(DefaultMutableTreeNode source) {
        return copy(source, false);
    }

    public DefaultMutableTreeNode copyForPaste(DefaultMutableTreeNode source) {
        return copy(source, true);
    }

    private static DefaultMutableTreeNode copy(DefaultMutableTreeNode source, boolean regenerateRequestIds) {
        PerformanceTreeNode sourceData = source != null && source.getUserObject() instanceof PerformanceTreeNode nodeData
                ? nodeData
                : null;
        DefaultMutableTreeNode copy = new DefaultMutableTreeNode(copyNodeData(sourceData, regenerateRequestIds));
        if (source == null) {
            return copy;
        }
        for (int i = 0; i < source.getChildCount(); i++) {
            copy.add(copy((DefaultMutableTreeNode) source.getChildAt(i), regenerateRequestIds));
        }
        return copy;
    }

    private static PerformanceTreeNode copyNodeData(PerformanceTreeNode source, boolean regenerateRequestIds) {
        if (source == null) {
            return new PerformanceTreeNode("", NodeType.ROOT);
        }
        PerformanceTreeNode copy = new PerformanceTreeNode(source.name, source.type);
        copy.enabled = source.enabled;
        copy.threadGroupData = JsonUtil.deepCopy(source.threadGroupData, ThreadGroupData.class);
        copy.csvDataSetData = JsonUtil.deepCopy(source.csvDataSetData, CsvDataSetData.class);
        copy.loopData = JsonUtil.deepCopy(source.loopData, LoopData.class);
        copy.conditionData = JsonUtil.deepCopy(source.conditionData, ConditionData.class);
        copy.whileData = JsonUtil.deepCopy(source.whileData, WhileData.class);
        copy.httpRequestItem = JsonUtil.deepCopy(source.httpRequestItem, HttpRequestItem.class);
        copy.requestSnapshot = copyRequestSnapshot(source.requestSnapshot);
        String pastedRequestId = regenerateRequestIds && (copy.httpRequestItem != null || copy.requestSnapshot != null)
                ? UUID.randomUUID().toString()
                : null;
        if (pastedRequestId != null && copy.httpRequestItem != null) {
            copy.httpRequestItem.setId(pastedRequestId);
        }
        if (pastedRequestId != null && copy.requestSnapshot != null) {
            copy.requestSnapshot = copy.requestSnapshot.toBuilder()
                    .id(pastedRequestId)
                    .build();
        }
        copy.assertionData = JsonUtil.deepCopy(source.assertionData, AssertionData.class);
        copy.extractorData = JsonUtil.deepCopy(source.extractorData, ExtractorData.class);
        copy.timerData = JsonUtil.deepCopy(source.timerData, TimerData.class);
        copy.ssePerformanceData = JsonUtil.deepCopy(source.ssePerformanceData, SsePerformanceData.class);
        copy.ssePerformanceEnabled = source.ssePerformanceEnabled;
        copy.webSocketPerformanceData = JsonUtil.deepCopy(source.webSocketPerformanceData, WebSocketPerformanceData.class);
        copy.requestExecutionScope = copyRequestExecutionScope(source.requestExecutionScope);
        return copy;
    }

    private static RequestExecutionScope copyRequestExecutionScope(RequestExecutionScope source) {
        return source == null ? null : RequestExecutionScope.fromGroupVariables(source.getGroupVariables());
    }

    private static PerformanceRequestSnapshot copyRequestSnapshot(PerformanceRequestSnapshot source) {
        return source == null ? null : source.toBuilder().build();
    }
}
