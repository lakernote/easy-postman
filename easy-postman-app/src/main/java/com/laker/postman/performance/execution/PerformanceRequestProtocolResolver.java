package com.laker.postman.performance.execution;

import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.plan.PerformancePlanElement;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.plan.PerformanceRequestSampler;
import lombok.experimental.UtilityClass;

@UtilityClass
class PerformanceRequestProtocolResolver {

    boolean isSseScenario(PerformanceRequestSampler sampler) {
        return sampler != null && sampler.getChildren().stream()
                .map(PerformancePlanElement::getType)
                .anyMatch(type -> type == NodeType.SSE_CONNECT || type == NodeType.SSE_READ);
    }

    boolean isWebSocketRequest(PerformanceRequestSnapshot snapshot) {
        return snapshot != null && snapshot.isWebSocket();
    }

    PerformanceProtocol resolvePerformanceProtocol(boolean webSocketRequest, boolean sseRequest) {
        if (webSocketRequest) {
            return PerformanceProtocol.WEBSOCKET;
        }
        if (sseRequest) {
            return PerformanceProtocol.SSE;
        }
        return PerformanceProtocol.HTTP;
    }
}
