package com.laker.postman.performance.execution;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.performance.core.assertion.AssertionData;
import com.laker.postman.performance.core.controller.ConditionData;
import com.laker.postman.performance.core.extractor.ExtractorData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.plan.PerformanceAssertionElement;
import com.laker.postman.performance.core.plan.PerformanceConditionController;
import com.laker.postman.performance.core.plan.PerformanceExtractorElement;
import com.laker.postman.performance.core.plan.PerformancePlanElement;
import com.laker.postman.performance.core.plan.PerformanceProtocolStageElement;
import com.laker.postman.performance.plan.PerformanceRequestSampler;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class PerformanceResponseCapturePlanTest {

    @Test
    public void shouldKeepBoundedHttpBodyPreviewOutsideEfficientMode() {
        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                false,
                sampler(List.of()),
                false,
                false,
                ""
        );

        assertEquals(plan.httpResponseBodyMode(), PreparedRequest.ResponseBodyMode.PREVIEW);
    }

    @Test
    public void shouldRetainStreamBodyOutsideEfficientMode() {
        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                false,
                sampler(List.of()),
                false,
                true,
                ""
        );

        assertTrue(plan.retainStreamResponseBody());
        assertTrue(plan.trackStreamResponseBodySize());
    }

    @Test
    public void shouldKeepHttpMetadataOnlyForHeaderOnlyPostScriptInEfficientMode() {
        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of()),
                false,
                false,
                "pm.test('header', function () { pm.response.to.have.header('Content-Type'); });"
        );

        assertTrue(plan.runPostScript());
        assertFalse(plan.postScriptNeedsResponseBody());
        assertEquals(plan.httpResponseBodyMode(), PreparedRequest.ResponseBodyMode.METADATA_ONLY);
    }

    @Test
    public void shouldUseHttpPreviewForBodyPostScriptInEfficientMode() {
        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of()),
                false,
                false,
                "pm.test('body', function () { pm.expect(pm.response.json().ok).to.equal(true); });"
        );

        assertTrue(plan.postScriptNeedsResponseBody());
        assertEquals(plan.httpResponseBodyMode(), PreparedRequest.ResponseBodyMode.PREVIEW);
    }

    @Test
    public void shouldTreatResponseSizePostScriptAsMetadataOnly() {
        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of()),
                false,
                false,
                "pm.test('size', function () { pm.expect(pm.response.size().body).to.be.above(0); });"
        );

        assertFalse(plan.postScriptNeedsResponseBody());
        assertEquals(plan.httpResponseBodyMode(), PreparedRequest.ResponseBodyMode.METADATA_ONLY);
    }

    @Test
    public void shouldTrackStreamBodySizeForResponseSizePostScriptWithoutRetainingBody() {
        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of()),
                true,
                false,
                "pm.test('size', function () { pm.expect(pm.response.size().body).to.be.above(0); });"
        );

        assertFalse(plan.postScriptNeedsResponseBody());
        assertFalse(plan.retainStreamResponseBody());
        assertTrue(plan.trackStreamResponseBodySize());
    }

    @Test
    public void shouldRetainSseStreamBodyWhenPostScriptReadsResponseBody() {
        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of()),
                true,
                false,
                "pm.test('body', function () { pm.expect(pm.response.text()).to.contain('done'); });"
        );

        assertTrue(plan.runPostScript());
        assertTrue(plan.postScriptNeedsResponseBody());
        assertTrue(plan.retainStreamResponseBody());
    }

    @Test
    public void shouldRetainSseStreamBodyForLegacyResponseBodyGlobal() {
        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of()),
                true,
                false,
                "pm.test('body', function () { pm.expect(responseBody).to.contain('done'); });"
        );

        assertTrue(plan.runPostScript());
        assertTrue(plan.postScriptNeedsResponseBody());
        assertTrue(plan.retainStreamResponseBody());
    }

    @Test
    public void shouldRetainWebSocketReadPayloadForFilteringWithoutRetainingResponseBody() {
        WebSocketPerformanceData readCfg = new WebSocketPerformanceData();
        readCfg.completionMode = WebSocketPerformanceData.CompletionMode.UNTIL_MATCH;
        readCfg.messageFilter = "ack";
        PerformanceProtocolStageElement readStep = new PerformanceProtocolStageElement(
                "read",
                NodeType.WS_READ,
                null,
                readCfg,
                List.of()
        );

        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of(readStep)),
                false,
                true,
                ""
        );

        assertTrue(plan.retainWebSocketReadPayloads());
        assertFalse(plan.retainStreamResponseBody());
    }

    @Test
    public void shouldRetainWebSocketReadPayloadForConditionReadFiltering() {
        WebSocketPerformanceData readCfg = new WebSocketPerformanceData();
        readCfg.completionMode = WebSocketPerformanceData.CompletionMode.UNTIL_MATCH;
        readCfg.messageFilter = "ack";
        PerformanceProtocolStageElement readStep = new PerformanceProtocolStageElement(
                "read",
                NodeType.WS_READ,
                null,
                readCfg,
                List.of()
        );
        ConditionData conditionData = new ConditionData();
        conditionData.expression = "true";
        PerformanceConditionController condition = new PerformanceConditionController(
                "condition",
                conditionData,
                List.of(readStep)
        );

        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of(condition)),
                false,
                true,
                ""
        );

        assertTrue(plan.retainWebSocketReadPayloads());
        assertFalse(plan.retainStreamResponseBody());
    }

    @Test
    public void shouldRetainWebSocketResponseBodyForReadBodyAssertion() {
        AssertionData data = new AssertionData();
        data.type = "Contains";
        data.content = "ack";
        PerformanceAssertionElement assertion = new PerformanceAssertionElement("contains", data);
        PerformanceProtocolStageElement readStep = new PerformanceProtocolStageElement(
                "read",
                NodeType.WS_READ,
                null,
                new WebSocketPerformanceData(),
                List.of(assertion)
        );

        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of(readStep)),
                false,
                true,
                ""
        );

        assertTrue(plan.retainWebSocketReadPayloads());
        assertTrue(plan.retainStreamResponseBody());
    }

    @Test
    public void shouldUseHttpPreviewForBodyExtractorInEfficientMode() {
        ExtractorData data = new ExtractorData();
        data.type = "JSONPath";
        data.expression = "$.token";
        data.variableName = "token";

        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of(new PerformanceExtractorElement("token", data))),
                false,
                false,
                ""
        );

        assertEquals(plan.httpResponseBodyMode(), PreparedRequest.ResponseBodyMode.PREVIEW);
    }

    @Test
    public void shouldKeepHttpMetadataOnlyForHeaderExtractorInEfficientMode() {
        ExtractorData data = new ExtractorData();
        data.type = "Header";
        data.expression = "X-Trace";
        data.variableName = "trace";

        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of(new PerformanceExtractorElement("trace", data))),
                false,
                false,
                ""
        );

        assertEquals(plan.httpResponseBodyMode(), PreparedRequest.ResponseBodyMode.METADATA_ONLY);
    }

    @Test
    public void shouldKeepHttpMetadataOnlyForResponseFieldExtractorInEfficientMode() {
        ExtractorData data = new ExtractorData();
        data.type = "Response Field";
        data.expression = "Status Code";
        data.variableName = "statusCode";

        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of(new PerformanceExtractorElement("status", data))),
                false,
                false,
                ""
        );

        assertEquals(plan.httpResponseBodyMode(), PreparedRequest.ResponseBodyMode.METADATA_ONLY);
    }

    @Test
    public void shouldRetainWebSocketPayloadForReadBodyExtractor() {
        ExtractorData data = new ExtractorData();
        data.type = "Regex";
        data.expression = "token=(\\w+)";
        data.variableName = "token";
        PerformanceProtocolStageElement readStep = new PerformanceProtocolStageElement(
                "read",
                NodeType.WS_READ,
                null,
                new WebSocketPerformanceData(),
                List.of(new PerformanceExtractorElement("token", data))
        );

        PerformanceResponseCapturePlan plan = PerformanceResponseCapturePlan.resolve(
                true,
                sampler(List.of(readStep)),
                false,
                true,
                ""
        );

        assertTrue(plan.retainWebSocketReadPayloads());
        assertTrue(plan.retainStreamResponseBody());
    }

    private static PerformanceRequestSampler sampler(List<PerformancePlanElement> children) {
        return new PerformanceRequestSampler("request", null, null, children);
    }
}
