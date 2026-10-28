package com.laker.postman.panel.performance.tree;

import com.laker.postman.collection.CollectionInheritance;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionInheritanceAdapter;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.performance.plan.PerformancePlanDataCopies;
import com.laker.postman.performance.plan.PerformancePlanDocument;
import com.laker.postman.performance.plan.PerformancePlanNode;
import com.laker.postman.performance.plan.PerformanceRequestSnapshotMapper;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.service.collections.ActiveCollectionTreeNodeRepository;
import com.laker.postman.service.collections.CollectionRequestExecutionScopeResolver;
import com.laker.postman.service.collections.CollectionRequestItemResolver;
import com.laker.postman.service.variable.RequestExecutionScope;
import lombok.experimental.UtilityClass;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@UtilityClass
public class PerformanceSwingTreePlanAdapter {

    public PerformancePlanDocument toDocument(DefaultMutableTreeNode rootNode) {
        return new PerformancePlanDocument(toDocumentNode(rootNode));
    }

    public DefaultMutableTreeNode toTree(PerformancePlanDocument document, String rootName) {
        DefaultMutableTreeNode treeNode = toTreeNode(document == null ? null : document.getRoot());
        if (treeNode != null && rootName != null && treeNode.getUserObject() instanceof PerformanceTreeNode data) {
            data.name = rootName;
        }
        return treeNode;
    }

    public PerformancePlanNode toDocumentNode(DefaultMutableTreeNode treeNode) {
        PerformanceTreeNode data = nodeData(treeNode);
        if (data == null) {
            return null;
        }
        HttpRequestItem effectiveRequestItem = resolveEffectiveRequestItem(data);
        RequestExecutionScope requestExecutionScope = resolveRequestExecutionScope(data);
        return PerformancePlanNode.builder()
                .name(resolveNodeName(data, effectiveRequestItem))
                .type(data.type)
                .enabled(data.enabled)
                .threadGroupData(data.threadGroupData)
                .csvDataSetData(data.csvDataSetData)
                .loopData(data.loopData)
                .conditionData(data.conditionData)
                .whileData(data.whileData)
                .httpRequestItem(effectiveRequestItem)
                .requestSnapshot(resolveRequestSnapshot(data, effectiveRequestItem, requestExecutionScope))
                .assertionData(data.assertionData)
                .extractorData(data.extractorData)
                .timerData(data.timerData)
                .ssePerformanceData(data.ssePerformanceData)
                .webSocketPerformanceData(data.webSocketPerformanceData)
                .requestExecutionScope(requestExecutionScope)
                .children(children(treeNode))
                .build();
    }

    public DefaultMutableTreeNode toTreeNode(PerformancePlanNode node) {
        if (node == null) {
            return null;
        }

        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(toPerformanceTreeNode(node));
        for (PerformancePlanNode child : node.getChildren()) {
            DefaultMutableTreeNode childTreeNode = toTreeNode(child);
            if (childTreeNode != null) {
                treeNode.add(childTreeNode);
            }
        }
        return treeNode;
    }

    private List<PerformancePlanNode> children(DefaultMutableTreeNode treeNode) {
        List<PerformancePlanNode> children = new ArrayList<>();
        if (treeNode == null) {
            return children;
        }
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            PerformancePlanNode child = toDocumentNode((DefaultMutableTreeNode) treeNode.getChildAt(i));
            if (child != null) {
                children.add(child);
            }
        }
        return children;
    }

    private PerformanceTreeNode toPerformanceTreeNode(PerformancePlanNode node) {
        PerformanceTreeNode data = new PerformanceTreeNode(node.getName(), node.getType());
        data.enabled = node.isEnabled();
        data.threadGroupData = PerformancePlanDataCopies.copyThreadGroupData(node.getThreadGroupData());
        data.csvDataSetData = PerformancePlanDataCopies.copyCsvDataSetData(node.getCsvDataSetData());
        data.loopData = PerformancePlanDataCopies.copyLoopData(node.getLoopData());
        data.conditionData = PerformancePlanDataCopies.copyConditionData(node.getConditionData());
        data.whileData = PerformancePlanDataCopies.copyWhileData(node.getWhileData());
        data.httpRequestItem = PerformancePlanDataCopies.copyHttpRequestItem(node.getHttpRequestItem());
        data.requestSnapshot = PerformanceRequestSnapshotMapper.copyRequestSnapshot(node.getRequestSnapshot());
        data.ssePerformanceEnabled = data.type == NodeType.REQUEST
                && node.getChildren().stream().anyMatch(child -> child.getType() == NodeType.SSE_CONNECT
                || child.getType() == NodeType.SSE_READ);
        data.assertionData = PerformancePlanDataCopies.copyAssertionData(node.getAssertionData());
        data.extractorData = PerformancePlanDataCopies.copyExtractorData(node.getExtractorData());
        data.timerData = PerformancePlanDataCopies.copyTimerData(node.getTimerData());
        data.ssePerformanceData = PerformancePlanDataCopies.copySsePerformanceData(node.getSsePerformanceData());
        data.webSocketPerformanceData = PerformancePlanDataCopies.copyWebSocketPerformanceData(node.getWebSocketPerformanceData());
        data.requestExecutionScope = PerformancePlanDataCopies.copyRequestExecutionScope(node.getRequestExecutionScope());
        return data;
    }

    private String resolveNodeName(PerformanceTreeNode data, HttpRequestItem effectiveRequestItem) {
        if (data != null
                && data.type == NodeType.REQUEST
                && effectiveRequestItem != null
                && effectiveRequestItem.getName() != null
                && !effectiveRequestItem.getName().trim().isEmpty()) {
            return effectiveRequestItem.getName();
        }
        return data == null ? null : data.name;
    }

    private PerformanceRequestSnapshot resolveRequestSnapshot(PerformanceTreeNode data,
                                                              HttpRequestItem item,
                                                              RequestExecutionScope scope) {
        if (data == null || data.type != NodeType.REQUEST) {
            return null;
        }
        if (item != null) {
            return PerformanceRequestSnapshotMapper.fromHttpRequestItem(item, scope);
        }
        return withExecutionScope(data.requestSnapshot, scope);
    }

    private PerformanceRequestSnapshot withExecutionScope(PerformanceRequestSnapshot snapshot, RequestExecutionScope scope) {
        if (snapshot == null || scope == null) {
            return snapshot;
        }
        return snapshot.toBuilder()
                .executionScope(PerformanceRequestSnapshotMapper.toScopeSnapshot(scope))
                .build();
    }

    private RequestExecutionScope resolveRequestExecutionScope(PerformanceTreeNode data) {
        if (data == null || data.type != NodeType.REQUEST || data.httpRequestItem == null) {
            return null;
        }
        var latestCollectionScope = PerformanceRequestScopeResolver.resolveLatestCollectionScope(data);
        if (latestCollectionScope.isPresent()) {
            return latestCollectionScope.get();
        }
        if (data.requestExecutionScope != null) {
            return data.requestExecutionScope;
        }
        String requestId = data.httpRequestItem.getId();
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        return CollectionRequestExecutionScopeResolver.resolveCurrentScope(requestId)
                .orElse(null);
    }

    private HttpRequestItem resolveEffectiveRequestItem(PerformanceTreeNode data) {
        if (data == null || data.type != NodeType.REQUEST || data.httpRequestItem == null) {
            return data == null ? null : data.httpRequestItem;
        }
        String requestId = data.httpRequestItem.getId();
        if (requestId == null || requestId.trim().isEmpty()) {
            return data.httpRequestItem;
        }
        HttpRequestItem baseItem = resolveLatestCollectionRequestItem(data).orElse(data.httpRequestItem);
        return new ActiveCollectionTreeNodeRepository()
                .findNodeByRequestId(requestId)
                .map(SwingCollectionInheritanceAdapter::collectGroupChain)
                .map(groupChain -> applyCollectionInheritance(baseItem, groupChain))
                .orElse(baseItem);
    }

    private Optional<HttpRequestItem> resolveLatestCollectionRequestItem(PerformanceTreeNode data) {
        if (data == null || data.type != NodeType.REQUEST || data.httpRequestItem == null) {
            return Optional.empty();
        }
        String requestId = data.httpRequestItem.getId();
        if (requestId == null || requestId.trim().isEmpty()) {
            return Optional.empty();
        }
        return CollectionRequestItemResolver.resolveCurrentRequest(requestId);
    }

    private HttpRequestItem applyCollectionInheritance(HttpRequestItem item, List<RequestGroup> groupChain) {
        if (groupChain == null || groupChain.isEmpty()) {
            return item;
        }
        return CollectionInheritance.apply(item, groupChain);
    }

    private PerformanceTreeNode nodeData(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof PerformanceTreeNode data)) {
            return null;
        }
        return data;
    }
}
