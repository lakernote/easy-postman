package com.laker.postman.performance.execution;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class WebSocketSendPayloadStateTest {

    @Test
    public void shouldRetainTemplateUntilTheScriptWritesTheBody() {
        PreparedRequest request = new PreparedRequest();
        request.body = "rendered-before-send";
        WebSocketSendPayloadState state = new WebSocketSendPayloadState(request, "configured-template");
        WebSocketPerformanceData config = requestBodyConfig();

        String beforeNoOp = state.bodyBeforeScript();
        state.captureScriptBodyWrite(beforeNoOp, false);
        assertEquals(state.resolvePayload(config), "configured-template");

        String beforeSameValueWrite = state.bodyBeforeScript();
        state.captureScriptBodyWrite(beforeSameValueWrite, true);
        assertEquals(state.resolvePayload(config), "rendered-before-send");
    }

    @Test
    public void shouldPromoteLegacyRawBodyChangesToTheEffectiveTemplate() {
        PreparedRequest request = new PreparedRequest();
        request.body = "before";
        WebSocketSendPayloadState state = new WebSocketSendPayloadState(request, "configured-template");
        WebSocketPerformanceData config = requestBodyConfig();

        String beforeScript = state.bodyBeforeScript();
        request.body = "changed-through-raw";
        state.captureScriptBodyWrite(beforeScript, false);

        assertEquals(state.resolvePayload(config), "changed-through-raw");
    }

    private static WebSocketPerformanceData requestBodyConfig() {
        WebSocketPerformanceData config = new WebSocketPerformanceData();
        config.sendContentSource = WebSocketPerformanceData.SendContentSource.REQUEST_BODY;
        return config;
    }
}
