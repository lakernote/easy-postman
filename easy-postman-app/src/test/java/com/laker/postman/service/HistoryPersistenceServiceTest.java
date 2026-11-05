package com.laker.postman.service;

import cn.hutool.json.JSONObject;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.HttpRouteAttempt;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.history.RequestHistoryItem;
import com.laker.postman.http.runtime.file.ResponseFileFixtures;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class HistoryPersistenceServiceTest {

    @Test
    public void shouldTruncateLargeRequestBodyBeforePersistingHistory() throws Exception {
        HistoryPersistenceService service = new HistoryPersistenceService();
        String largeBody = "x".repeat(12 * 1024);

        PreparedRequest request = new PreparedRequest();
        request.method = "POST";
        request.url = "https://example.com/api/test";
        request.body = largeBody;
        request.sentRequestBody = largeBody;

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = "{\"ok\":true}";

        RequestHistoryItem item = new RequestHistoryItem(request, response, 123456789L);

        JSONObject json = invokeConvertToJson(service, item);
        RequestHistoryItem restored = invokeConvertFromJson(service, json);

        assertNotNull(restored.getRequest());
        assertTrue(restored.getRequest().body.length() < largeBody.length());
        assertEquals(restored.getRequest().body, restored.getRequest().sentRequestBody);
        assertTrue(restored.getRequest().body.contains("内容过大，已截断"));
    }

    @Test
    public void shouldPersistIpv4AndIpv6RouteAttempts() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://example.com/api/test";

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = "ok";
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setDispatcherQueueStart(90L);
        eventInfo.setDispatcherQueueEnd(95L);
        eventInfo.setDnsHost("example.com");
        eventInfo.replaceDnsAddresses(List.of("2001:db8::10", "192.0.2.10"));
        eventInfo.setDnsError("secondary resolver failed");
        eventInfo.recordRetryDecision(true);
        eventInfo.recordFollowUpDecision(true);
        eventInfo.addRouteAttempt(new HttpRouteAttempt(
                "[2001:db8::10]:443", "IPv6", 100L, 125L, 20L,
                false, true, null, "canceled after IPv4 connected"
        ));
        // Keep exercising history compatibility with the original constructor shape.
        eventInfo.addRouteAttempt(new HttpRouteAttempt(
                "192.0.2.10:443", "IPv4", 126L, 140L, true, "h2", null
        ));
        response.httpEventInfo = eventInfo;

        JSONObject json = invokeConvertToJson(new HistoryPersistenceService(),
                new RequestHistoryItem(request, response, 123456789L));
        RequestHistoryItem restored = invokeConvertFromJson(new HistoryPersistenceService(), json);

        assertEquals(restored.getResponse().httpEventInfo.getRouteAttempts().size(), 2);
        assertEquals(restored.getResponse().httpEventInfo.getRouteAttempts().get(0).addressFamily(), "IPv6");
        assertEquals(restored.getResponse().httpEventInfo.getRouteAttempts().get(0).durationMs(), 20L);
        assertTrue(restored.getResponse().httpEventInfo.getRouteAttempts().get(0).canceled());
        assertTrue(restored.getResponse().httpEventInfo.getRouteAttempts().get(1).connected());
        assertFalse(restored.getResponse().httpEventInfo.getRouteAttempts().get(1).canceled());
        assertEquals(restored.getResponse().httpEventInfo.getDispatcherQueueStart(), 90L);
        assertEquals(restored.getResponse().httpEventInfo.getDispatcherQueueEnd(), 95L);
        assertEquals(restored.getResponse().httpEventInfo.getDnsHost(), "example.com");
        assertEquals(restored.getResponse().httpEventInfo.getDnsAddresses(),
                List.of("2001:db8::10", "192.0.2.10"));
        assertEquals(restored.getResponse().httpEventInfo.getDnsError(), "secondary resolver failed");
        assertEquals(restored.getResponse().httpEventInfo.getRetryDecisionCount(), 1);
        assertEquals(restored.getResponse().httpEventInfo.getRetryCount(), 1);
        assertEquals(restored.getResponse().httpEventInfo.getFollowUpDecisionCount(), 1);
        assertEquals(restored.getResponse().httpEventInfo.getFollowUpCount(), 1);
    }

    @Test
    public void shouldKeepOnlyFileInfoForRecognizedFileResponse() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.method = "POST";
        request.url = "https://example.com/export.do";

        HttpResponse response = new HttpResponse();
        response.code = 200;
        byte[] xlsx = ResponseFileFixtures.xlsx();
        response.body = new String(xlsx, StandardCharsets.UTF_8);
        response.rawBodyBytes = xlsx;
        response.detectedFileExtension = ".xlsx";
        response.fileName = "export.xlsx";
        response.bodySize = xlsx.length;

        HistoryPersistenceService service = new HistoryPersistenceService();
        HttpResponse historyResponse = HistoryPersistenceService.toHistoryResponse(response);
        assertNotSame(historyResponse, response);
        assertNull(historyResponse.rawBodyBytes);
        assertEquals(historyResponse.body, "");
        assertTrue(historyResponse.fileContentOmitted);
        assertSame(response.rawBodyBytes, xlsx, "当前响应面板仍需原始字节用于下载");

        JSONObject json = invokeConvertToJson(service, new RequestHistoryItem(request, historyResponse, 1L));
        assertEquals(json.getJSONObject("response").getStr("body"), "");
        RequestHistoryItem restored = invokeConvertFromJson(service, json);

        assertEquals(restored.getResponse().body, "");
        assertTrue(restored.getResponse().fileContentOmitted);
        assertEquals(restored.getResponse().detectedFileExtension, ".xlsx");
        assertEquals(restored.getResponse().fileName, "export.xlsx");
    }

    @Test
    public void shouldRecognizeLegacyGarbledFileHistory() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://example.com/export.do";
        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = new String(ResponseFileFixtures.xlsx(), StandardCharsets.UTF_8);

        HistoryPersistenceService service = new HistoryPersistenceService();
        JSONObject json = invokeConvertToJson(service, new RequestHistoryItem(request, response, 1L));
        RequestHistoryItem restored = invokeConvertFromJson(service, json);

        assertEquals(restored.getResponse().body, "");
        assertEquals(restored.getResponse().detectedFileExtension, ".xlsx");
        assertTrue(restored.getResponse().fileContentOmitted);
    }

    @Test
    public void shouldKeepPlainTextHistoryResponseAsIs() {
        HttpResponse response = new HttpResponse();
        response.body = "{\"ok\":true}";
        assertSame(HistoryPersistenceService.toHistoryResponse(response), response);
    }

    @Test
    public void shouldPersistRecoveredRetryError() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "http://127.0.0.1/";
        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = "ok";
        response.httpEventInfo = new HttpEventInfo();
        response.httpEventInfo.setRecoveredErrorMessage("unexpected end of stream");

        HistoryPersistenceService service = new HistoryPersistenceService();
        RequestHistoryItem restored = invokeConvertFromJson(service,
                invokeConvertToJson(service, new RequestHistoryItem(request, response, 1L)));

        assertNull(restored.getResponse().httpEventInfo.getErrorMessage());
        assertEquals(restored.getResponse().httpEventInfo.getRecoveredErrorMessage(), "unexpected end of stream");
    }

    @Test
    public void shouldDropMissingTempFilePathWhenRestoringHistory() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://example.com/report.pdf";
        java.nio.file.Path existing = java.nio.file.Files.createTempFile("history-response", ".bin");
        try {
            HistoryPersistenceService service = new HistoryPersistenceService();
            HttpResponse missing = new HttpResponse();
            missing.code = 200;
            missing.filePath = existing.resolveSibling("missing-" + System.nanoTime() + ".bin").toString();
            assertNull(invokeConvertFromJson(service, invokeConvertToJson(service,
                    new RequestHistoryItem(request, missing, 1L))).getResponse().filePath);

            HttpResponse present = new HttpResponse();
            present.code = 200;
            present.filePath = existing.toString();
            assertEquals(invokeConvertFromJson(service, invokeConvertToJson(service,
                    new RequestHistoryItem(request, present, 1L))).getResponse().filePath, existing.toString());
        } finally {
            java.nio.file.Files.deleteIfExists(existing);
        }
    }

    private JSONObject invokeConvertToJson(HistoryPersistenceService service, RequestHistoryItem item) throws Exception {
        Method method = HistoryPersistenceService.class.getDeclaredMethod("convertToJson", RequestHistoryItem.class);
        method.setAccessible(true);
        return (JSONObject) method.invoke(service, item);
    }

    private RequestHistoryItem invokeConvertFromJson(HistoryPersistenceService service, JSONObject json) throws Exception {
        Method method = HistoryPersistenceService.class.getDeclaredMethod("convertFromJson", JSONObject.class);
        method.setAccessible(true);
        return (RequestHistoryItem) method.invoke(service, json);
    }

    @Test
    public void shouldKeepOnlyFileInfoForDeclaredBinaryResponse() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://example.com/report.pdf";

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = "[binary saved to temp file]";
        response.fileName = "report.pdf";
        response.bodySize = 2048;
        response.headers = new LinkedHashMap<>();
        response.headers.put("Content-Type", List.of("application/pdf"));

        HistoryPersistenceService service = new HistoryPersistenceService();
        HttpResponse historyResponse = HistoryPersistenceService.toHistoryResponse(response);
        assertTrue(historyResponse.fileContentOmitted);
        assertEquals(historyResponse.body, "");

        JSONObject json = invokeConvertToJson(service, new RequestHistoryItem(request, historyResponse, 1L));
        RequestHistoryItem restored = invokeConvertFromJson(service, json);

        assertTrue(restored.getResponse().fileContentOmitted);
        assertEquals(restored.getResponse().fileName, "report.pdf");
    }

    @Test
    public void shouldPersistTextAttachmentFlag() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://example.com/export";

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = "a,b\n1,2";
        response.fileAttachment = true;
        response.fileName = "export.csv";
        response.headers = new LinkedHashMap<>();
        response.headers.put("Content-Type", List.of("text/csv"));

        HistoryPersistenceService service = new HistoryPersistenceService();
        JSONObject json = invokeConvertToJson(service, new RequestHistoryItem(request, response, 1L));
        RequestHistoryItem restored = invokeConvertFromJson(service, json);

        assertTrue(restored.getResponse().fileAttachment);
        assertEquals(restored.getResponse().body, "a,b\n1,2");
        assertFalse(restored.getResponse().fileContentOmitted);
    }

    @Test
    public void shouldOmitLossyTextAttachmentBodyInHistory() {
        byte[] invalidUtf8 = {0x61, (byte) 0xC3, 0x28, 0x62};
        HttpResponse lossy = new HttpResponse();
        lossy.body = new String(invalidUtf8, StandardCharsets.UTF_8);
        lossy.rawBodyBytes = invalidUtf8;
        lossy.fileAttachment = true;
        lossy.fileName = "data.csv";
        lossy.headers = new LinkedHashMap<>();
        lossy.headers.put("Content-Type", List.of("text/csv"));

        HttpResponse history = HistoryPersistenceService.toHistoryResponse(lossy);

        assertEquals(history.body, "");
        assertTrue(history.fileContentOmitted);
        assertTrue(history.fileAttachment);
        assertEquals(history.fileName, "data.csv");

        HttpResponse gbk = new HttpResponse();
        gbk.body = "名称";
        gbk.rawBodyBytes = "名称".getBytes(java.nio.charset.Charset.forName("GBK"));
        gbk.fileAttachment = true;
        gbk.headers = new LinkedHashMap<>();
        gbk.headers.put("Content-Type", List.of("text/csv; charset=GBK"));

        HttpResponse gbkHistory = HistoryPersistenceService.toHistoryResponse(gbk);

        assertEquals(gbkHistory.body, "名称", "可无损还原的附件保留正文");
        assertFalse(gbkHistory.fileContentOmitted);
        assertNull(gbkHistory.rawBodyBytes);
    }
}
