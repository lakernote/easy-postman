package com.laker.postman.performance.result;

import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpHeader;


import com.laker.postman.performance.execution.PerformanceRequestExecutionResult;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.model.PerformanceSampleResult;
import com.laker.postman.performance.model.ResultNodeInfo;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class PerformanceResultDisplayMapperTest {

    @Test
    public void shouldSimplifyDisplayFieldsAndDropEventInfoWhenDisabled() {
        PreparedRequest request = new PreparedRequest();
        request.id = "req-1";
        request.body = "{\"hello\":\"world\"}";
        request.bodyType = "json";
        request.headersList = List.of(new HttpHeader(true, "X-Test", "1"));
        request.collectEventInfo = false;

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.filePath = "/tmp/result.bin";
        response.fileName = "result.bin";
        response.httpEventInfo = new HttpEventInfo();
        response.costMs = 123L;

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api-1",
                "API 1",
                request,
                response,
                null,
                null,
                false,
                false,
                false,
                1000L,
                0L
        );

        ResultNodeInfo resultNodeInfo = PerformanceResultDisplayMapper.toDisplayNodeInfo(
                PerformanceSampleResult.fromExecutionResult(executionResult)
        );

        assertSame(resultNodeInfo.req, request);
        assertSame(resultNodeInfo.resp, response);
        assertNull(request.id);
        assertNull(request.body);
        assertNull(request.bodyType);
        assertNull(request.headersList);
        assertNull(response.filePath);
        assertEquals(response.fileName, "result.bin", "文件名用于详情页展示文件信息");
        assertNull(response.httpEventInfo);
        assertEquals(resultNodeInfo.costMs, 123);
    }

    @Test
    public void shouldHideInternalStreamHeadersAndPromoteStreamErrorForDisplay() {
        PreparedRequest request = new PreparedRequest();
        request.collectEventInfo = false;

        HttpResponse response = new HttpResponse();
        response.code = 0;
        response.costMs = 26L;
        response.headers = new LinkedHashMap<>();
        response.addHeader("Content-Type", List.of("text/plain"));
        response.addHeader("X-Easy-WS-Sent-Count", List.of("0"));
        response.addHeader("X-Easy-WS-Completion-Reason", List.of("failure"));
        response.addHeader("X-Easy-WS-Error", List.of("Resource temporarily unavailable"));

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api-1",
                "WebSocket API",
                request,
                response,
                "",
                List.of(),
                true,
                false,
                true,
                1000L,
                0L
        );

        ResultNodeInfo resultNodeInfo = PerformanceResultDisplayMapper.toDisplayNodeInfo(
                PerformanceSampleResult.fromExecutionResult(executionResult)
        );

        assertEquals(resultNodeInfo.errorMsg, "Resource temporarily unavailable");
        assertTrue(resultNodeInfo.resp.headers.containsKey("Content-Type"));
        assertFalse(resultNodeInfo.resp.headers.containsKey("X-Easy-WS-Sent-Count"));
        assertFalse(resultNodeInfo.resp.headers.containsKey("X-Easy-WS-Completion-Reason"));
        assertFalse(resultNodeInfo.resp.headers.containsKey("X-Easy-WS-Error"));
    }

    @Test
    public void shouldCompactFailedStreamDetailsInEfficientMode() {
        PreparedRequest request = new PreparedRequest();
        request.collectEventInfo = false;

        HttpResponse response = new HttpResponse();
        response.code = 101;
        response.costMs = 26L;
        response.body = "x".repeat(64 * 1024);
        response.headers = new LinkedHashMap<>();
        response.addHeader("X-Easy-WS-Sent-Count", List.of("742"));
        response.addHeader("X-Easy-WS-Received-Count", List.of("8"));
        response.addHeader("X-Easy-WS-Message-Count", List.of("1"));
        response.addHeader("X-Easy-WS-Last-Message", List.of("{\"event\":\"LAST\"}"));
        response.addHeader("X-Easy-WS-Error", List.of("Java heap space"));

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api-1",
                "WebSocket API",
                request,
                response,
                "",
                List.of(),
                true,
                false,
                PerformanceProtocol.WEBSOCKET,
                1000L,
                0L
        );

        ResultNodeInfo resultNodeInfo = PerformanceResultDisplayMapper.toDisplayNodeInfo(
                PerformanceSampleResult.fromExecutionResult(executionResult),
                true
        );

        assertEquals(resultNodeInfo.errorMsg, "Java heap space");
        assertTrue(resultNodeInfo.resp.body.contains("Error: Java heap space"));
        assertTrue(resultNodeInfo.resp.body.contains("Status: 101"));
        assertTrue(resultNodeInfo.resp.body.contains("Sent: 742"));
        assertTrue(resultNodeInfo.resp.body.contains("Received: 8"));
        assertTrue(resultNodeInfo.resp.body.contains("Matched: 1"));
        assertTrue(resultNodeInfo.resp.body.contains("{\"event\":\"LAST\"}"));
        assertFalse(resultNodeInfo.resp.body.contains("x".repeat(8192)));
        assertTrue(resultNodeInfo.resp.body.length() < 4096);
    }

    @Test
    public void shouldLimitStreamPreviewForResultTableDisplay() {
        PreparedRequest request = new PreparedRequest();
        request.collectEventInfo = false;

        HttpResponse response = new HttpResponse();
        response.code = 101;
        response.costMs = 26L;
        response.body = "a".repeat(16 * 1024);

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api-1",
                "WebSocket API",
                request,
                response,
                "",
                List.of(),
                false,
                false,
                PerformanceProtocol.WEBSOCKET,
                1000L,
                0L
        );

        ResultNodeInfo resultNodeInfo = PerformanceResultDisplayMapper.toDisplayNodeInfo(
                PerformanceSampleResult.fromExecutionResult(executionResult),
                false
        );

        assertTrue(resultNodeInfo.resp.body.length() < 5000);
        assertTrue(resultNodeInfo.resp.body.contains("[truncated"));
    }

    @Test
    public void shouldUseSseBodyAsCompactLastMessagePreview() {
        PreparedRequest request = new PreparedRequest();
        request.collectEventInfo = false;

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.costMs = 26L;
        response.body = "data: " + "event-body-".repeat(1024);
        response.headers = new LinkedHashMap<>();
        response.addHeader("X-Easy-SSE-Event-Count", List.of("3"));
        response.addHeader("X-Easy-SSE-Message-Count", List.of("1"));
        response.addHeader("X-Easy-SSE-Event-Type", List.of("answer"));
        response.addHeader("X-Easy-SSE-Error", List.of("SSE timeout"));

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api-1",
                "SSE API",
                request,
                response,
                "",
                List.of(),
                true,
                false,
                PerformanceProtocol.SSE,
                1000L,
                0L
        );

        ResultNodeInfo resultNodeInfo = PerformanceResultDisplayMapper.toDisplayNodeInfo(
                PerformanceSampleResult.fromExecutionResult(executionResult),
                true
        );

        assertTrue(resultNodeInfo.resp.body.contains("Error: SSE timeout"));
        assertTrue(resultNodeInfo.resp.body.contains("Received: 3"));
        assertTrue(resultNodeInfo.resp.body.contains("Matched: 1"));
        assertTrue(resultNodeInfo.resp.body.contains("data: event-body-"));
        assertFalse(resultNodeInfo.resp.body.contains("event-body-".repeat(512)));
    }

    @Test
    public void shouldDisplayInterruptedStreamSampleAsFailure() {
        PreparedRequest request = new PreparedRequest();
        request.collectEventInfo = false;

        HttpResponse response = new HttpResponse();
        response.code = 101;
        response.costMs = 60_000L;
        response.body = "partial";
        response.headers = new LinkedHashMap<>();
        response.addHeader("X-Easy-WS-Sent-Count", List.of("120"));

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api-1",
                "WebSocket API",
                request,
                response,
                "Execution interrupted",
                List.of(),
                false,
                true,
                PerformanceProtocol.WEBSOCKET,
                1000L,
                0L
        );

        ResultNodeInfo resultNodeInfo = PerformanceResultDisplayMapper.toDisplayNodeInfo(
                PerformanceSampleResult.fromExecutionResult(executionResult),
                true
        );

        assertFalse(resultNodeInfo.isActuallySuccessful());
        assertEquals(resultNodeInfo.errorMsg, "Execution interrupted");
        assertTrue(resultNodeInfo.resp.body.contains("Error: Execution interrupted"));
        assertTrue(resultNodeInfo.resp.body.contains("Sent: 120"));
    }

    @Test
    public void shouldDisplayInterruptedHttpSampleWithoutResponseAsFailure() {
        PreparedRequest request = new PreparedRequest();
        request.collectEventInfo = false;

        PerformanceRequestExecutionResult executionResult = new PerformanceRequestExecutionResult(
                "api-1",
                "HTTP API",
                request,
                null,
                "Execution interrupted",
                List.of(),
                false,
                true,
                PerformanceProtocol.HTTP,
                1000L,
                2500L
        );

        ResultNodeInfo resultNodeInfo = PerformanceResultDisplayMapper.toDisplayNodeInfo(
                PerformanceSampleResult.fromExecutionResult(executionResult),
                true
        );

        assertFalse(resultNodeInfo.isActuallySuccessful());
        assertEquals(resultNodeInfo.errorMsg, "Execution interrupted");
        assertEquals(resultNodeInfo.costMs, 2500);
        assertEquals(resultNodeInfo.responseCode, 0);
        assertTrue(resultNodeInfo.resp.body.contains("Execution interrupted"));
    }
}
