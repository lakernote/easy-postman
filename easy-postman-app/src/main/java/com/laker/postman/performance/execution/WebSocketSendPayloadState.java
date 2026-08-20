package com.laker.postman.performance.execution;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;

import java.util.Objects;

/**
 * Owns the effective request-body template across repeated WebSocket sends.
 *
 * <p>The configured template remains reusable while variables change. An explicit body write, or
 * a legacy {@code pm.request.raw.body} value change, promotes the current request body to the new
 * template for subsequent sends.</p>
 */
final class WebSocketSendPayloadState {
    private final PreparedRequest request;
    private String requestBodyTemplate;

    WebSocketSendPayloadState(PreparedRequest request, String requestBodyTemplate) {
        this.request = request;
        this.requestBodyTemplate = requestBodyTemplate;
    }

    String bodyBeforeScript() {
        return request != null ? request.body : null;
    }

    void captureScriptBodyWrite(String bodyBeforeScript, boolean bodyWriteRequested) {
        String currentBody = request != null ? request.body : null;
        if (bodyWriteRequested || !Objects.equals(currentBody, bodyBeforeScript)) {
            requestBodyTemplate = currentBody;
        }
    }

    boolean hasPayload(WebSocketPerformanceData config) {
        return WebSocketScenarioStepSupport.hasSendPayload(request, requestBodyTemplate, config);
    }

    String resolvePayload(WebSocketPerformanceData config) {
        return WebSocketScenarioStepSupport.resolveSendPayload(request, requestBodyTemplate, config);
    }
}
