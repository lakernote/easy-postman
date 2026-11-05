package com.laker.postman.service.collections;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.file.ResponseFileFixtures;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.SavedResponse;
import org.testng.annotations.Test;

import cn.hutool.json.JSONUtil;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class SavedResponseSnapshotMapperTest {

    @Test
    public void shouldCreateSavedResponseSnapshotFromRuntimeExchange() {
        PreparedRequest request = new PreparedRequest();
        request.method = "POST";
        request.url = "https://api.example.com/users";
        request.bodyType = "raw";
        request.body = "{\"name\":\"easy\"}";
        request.headersList = List.of(new HttpHeader(true, "Content-Type", "application/json"));
        request.paramsList = List.of(new HttpParam(true, "verbose", "true"));

        HttpResponse response = new HttpResponse();
        response.code = 201;
        response.body = "{\"id\":1}";
        response.costMs = 123;
        response.bodySize = 8;
        response.headersSize = 42;
        response.headers = new LinkedHashMap<>();
        response.headers.put("Content-Type", List.of("application/json"));
        response.headers.put("X-Trace", List.of("a", "b"));

        SavedResponse savedResponse = SavedResponseSnapshotMapper.fromExchange("Created", request, response);

        assertNotNull(savedResponse.getId());
        assertEquals(savedResponse.getName(), "Created");
        assertEquals(savedResponse.getOriginalRequest().getMethod(), "POST");
        assertEquals(savedResponse.getOriginalRequest().getBody(), "{\"name\":\"easy\"}");
        assertEquals(savedResponse.getOriginalRequest().getOriginalBodySize(), "{\"name\":\"easy\"}".length());
        assertEquals(savedResponse.getOriginalRequest().getHeaders().size(), 1);
        assertEquals(savedResponse.getOriginalRequest().getParams().size(), 1);
        assertEquals(savedResponse.getCode(), 201);
        assertEquals(savedResponse.getHeaders().size(), 2);
        assertEquals(savedResponse.getHeaders().get(1).getValue(), "a, b");
        assertEquals(savedResponse.getPreviewLanguage(), "json");
    }

    @Test
    public void shouldCreateRuntimeResponseFromSavedResponse() {
        SavedResponse savedResponse = new SavedResponse();
        savedResponse.setCode(200);
        savedResponse.setBody("<html></html>");
        savedResponse.setHeaders(List.of(new HttpHeader(true, "Content-Type", "text/html")));
        savedResponse.setCostMs(88);
        savedResponse.setBodySize(13);
        savedResponse.setHeadersSize(20);

        HttpResponse response = SavedResponseSnapshotMapper.toRuntimeResponse(savedResponse);

        assertEquals(response.code, 200);
        assertEquals(response.body, "<html></html>");
        assertEquals(response.headers.get("Content-Type"), List.of("text/html"));
        assertEquals(response.costMs, 88);
        assertEquals(response.bodySize, 13);
        assertEquals(response.headersSize, 20);
    }

    @Test
    public void shouldKeepOnlyPreviewForLargeOriginalRequestBody() {
        PreparedRequest request = new PreparedRequest();
        request.method = "POST";
        request.url = "https://api.example.com/import";
        request.bodyType = "raw";
        request.body = "x".repeat(512 * 1024);

        SavedResponse savedResponse = SavedResponseSnapshotMapper.fromExchange("Imported", request, new HttpResponse());
        SavedResponse.OriginalRequest originalRequest = savedResponse.getOriginalRequest();

        assertTrue(originalRequest.isBodyTruncated());
        assertEquals(originalRequest.getOriginalBodySize(), 512 * 1024);
        assertTrue(originalRequest.getBody().length() <= 64 * 1024);
    }

    @Test
    public void shouldSaveOnlyFileMetadataForDetectedFileResponse() throws Exception {
        byte[] xlsx = ResponseFileFixtures.xlsx();
        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = new String(xlsx, StandardCharsets.UTF_8);
        response.rawBodyBytes = xlsx;
        response.detectedFileExtension = ".xlsx";
        response.fileName = "orders.xlsx";
        response.bodySize = xlsx.length;
        response.headers = new LinkedHashMap<>();
        response.headers.put("Content-Type", List.of("application/json"));

        SavedResponse saved = SavedResponseSnapshotMapper.fromExchange("Export", new PreparedRequest(), response);

        assertEquals(saved.getBody(), "", "Binary content must not be persisted as garbled text");
        assertNotNull(saved.getFile());
        assertEquals(saved.getFile().getFileName(), "orders.xlsx");
        assertEquals(saved.getFile().getExtension(), ".xlsx");
        assertEquals(saved.getBodySize(), xlsx.length);

        SavedResponse reloaded = JSONUtil.toBean(JSONUtil.parseObj(saved), SavedResponse.class);
        HttpResponse restored = SavedResponseSnapshotMapper.toRuntimeResponse(reloaded);

        assertTrue(restored.fileContentOmitted);
        assertEquals(restored.fileName, "orders.xlsx");
        assertEquals(restored.detectedFileExtension, ".xlsx");
        assertEquals(restored.bodySize, xlsx.length);
        assertNull(restored.rawBodyBytes);
    }

    @Test
    public void shouldSaveOnlyFileMetadataForDeclaredBinaryResponse() {
        HttpResponse response = new HttpResponse();
        response.body = "[binary placeholder]";
        response.bodySize = 2048;
        response.fileName = "archive.bin";
        response.headers = new LinkedHashMap<>();
        response.headers.put("Content-Type", List.of("application/octet-stream"));

        SavedResponse saved = SavedResponseSnapshotMapper.fromExchange("Binary", new PreparedRequest(), response);

        assertEquals(saved.getBody(), "");
        assertNotNull(saved.getFile());
        assertEquals(saved.getFile().getFileName(), "archive.bin");
        assertNull(saved.getFile().getExtension());
    }

    @Test
    public void shouldKeepTextBodyAndOmitFileMetadataForRegularResponse() {
        HttpResponse response = new HttpResponse();
        response.body = "{\"ok\":true}";
        response.headers = new LinkedHashMap<>();
        response.headers.put("Content-Type", List.of("application/json"));

        SavedResponse saved = SavedResponseSnapshotMapper.fromExchange("Json", new PreparedRequest(), response);
        HttpResponse restored = SavedResponseSnapshotMapper.toRuntimeResponse(saved);

        assertEquals(saved.getBody(), "{\"ok\":true}");
        assertNull(saved.getFile());
        assertFalse(JSONUtil.parseObj(saved).containsKey("file"), "Regular examples should not gain extra JSON fields");
        assertFalse(restored.fileContentOmitted);
        assertEquals(restored.body, "{\"ok\":true}");
    }

    @Test
    public void shouldRecognizeGarbledFileBodyInLegacyExample() throws Exception {
        SavedResponse legacy = new SavedResponse();
        legacy.setBody(new String(ResponseFileFixtures.xlsx(), StandardCharsets.UTF_8));
        legacy.setBodySize(8345);

        HttpResponse restored = SavedResponseSnapshotMapper.toRuntimeResponse(legacy);

        assertTrue(restored.fileContentOmitted);
        assertEquals(restored.detectedFileExtension, ".xlsx");
        assertEquals(restored.body, "");
        assertEquals(restored.bodySize, 8345);
    }

    @Test
    public void shouldKeepRestorableTextAttachmentWithItsCharset() {
        java.nio.charset.Charset gbk = java.nio.charset.Charset.forName("GBK");
        byte[] csv = "姓名,金额\n张三,10\n".getBytes(gbk);
        HttpResponse response = new HttpResponse();
        response.body = new String(csv, gbk);
        response.rawBodyBytes = csv;
        response.fileAttachment = true;
        response.fileName = "orders.csv";
        response.headers = new LinkedHashMap<>();
        response.headers.put("Content-Type", List.of("text/csv; charset=GBK"));

        SavedResponse saved = SavedResponseSnapshotMapper.fromExchange("Csv", new PreparedRequest(), response);
        SavedResponse reloaded = JSONUtil.toBean(JSONUtil.parseObj(saved), SavedResponse.class);
        HttpResponse restored = SavedResponseSnapshotMapper.toRuntimeResponse(reloaded);

        assertEquals(saved.getBody(), response.body);
        assertTrue(saved.getFile().isAttachment());
        assertTrue(saved.getFile().isContentInBody());
        assertTrue(restored.fileAttachment);
        assertFalse(restored.fileContentOmitted);
        assertEquals(restored.fileName, "orders.csv");
        assertEquals(restored.rawBodyBytes, csv, "恢复后下载必须得到原始 GBK 字节");
    }

    @Test
    public void shouldOmitTextAttachmentThatCannotBeRestoredLosslessly() {
        byte[] invalidUtf8 = {0x61, (byte) 0xC3, 0x28, 0x62};
        HttpResponse response = new HttpResponse();
        response.body = new String(invalidUtf8, StandardCharsets.UTF_8);
        response.rawBodyBytes = invalidUtf8;
        response.fileAttachment = true;
        response.fileName = "data.txt";
        response.headers = new LinkedHashMap<>();
        response.headers.put("Content-Type", List.of("text/plain"));

        SavedResponse saved = SavedResponseSnapshotMapper.fromExchange("Txt", new PreparedRequest(), response);
        HttpResponse restored = SavedResponseSnapshotMapper.toRuntimeResponse(saved);

        assertEquals(saved.getBody(), "");
        assertFalse(saved.getFile().isContentInBody());
        assertTrue(restored.fileContentOmitted);
        assertTrue(restored.fileAttachment);
    }

    @Test
    public void shouldMigrateLegacyTextAttachmentExample() {
        SavedResponse legacy = new SavedResponse();
        legacy.setCode(200);
        legacy.setBody("名称,数量\n");
        legacy.setHeaders(List.of(
                new HttpHeader(true, "Content-Type", "text/csv; charset=GBK"),
                new HttpHeader(true, "Content-Disposition", "attachment")));
        SavedResponse.OriginalRequest original = new SavedResponse.OriginalRequest();
        original.setUrl("{{host}}/api/orders.csv?page=1");
        legacy.setOriginalRequest(original);

        HttpResponse restored = SavedResponseSnapshotMapper.toRuntimeResponse(legacy);

        assertTrue(restored.fileAttachment);
        assertEquals(restored.fileName, "orders.csv");
        assertEquals(restored.rawBodyBytes, "名称,数量\n".getBytes(java.nio.charset.Charset.forName("GBK")));
        assertFalse(restored.fileContentOmitted);

        legacy.setBody("a\uFFFDb");
        legacy.setHeaders(List.of(
                new HttpHeader(true, "Content-Type", "text/plain"),
                new HttpHeader(true, "Content-Disposition", "attachment; filename=report.txt")));
        HttpResponse lossy = SavedResponseSnapshotMapper.toRuntimeResponse(legacy);
        assertTrue(lossy.fileAttachment);
        assertEquals(lossy.fileName, "report.txt");
        assertNull(lossy.rawBodyBytes, "保存时已有损的正文不再还原字节");

        legacy.setHeaders(List.of(new HttpHeader(true, "Content-Type", "text/csv")));
        legacy.setBody("a,b");
        assertFalse(SavedResponseSnapshotMapper.toRuntimeResponse(legacy).fileAttachment, "非附件保持普通文本");
    }

    @Test
    public void shouldMigrateLegacyBinaryPlaceholderExample() {
        SavedResponse legacy = new SavedResponse();
        legacy.setCode(200);
        legacy.setBody("[Binary content saved as temp file. Click the download button on the right to download]");
        legacy.setBodySize(123456);
        legacy.setHeaders(List.of(new HttpHeader(true, "Content-Type", "application/pdf")));

        HttpResponse restored = SavedResponseSnapshotMapper.toRuntimeResponse(legacy);

        assertTrue(SavedResponseSnapshotMapper.isLegacyBinaryPlaceholder(legacy));
        assertTrue(restored.fileContentOmitted);
        assertEquals(restored.body, "");

        SavedResponse handWritten = new SavedResponse();
        handWritten.setBody("abc");
        handWritten.setBodySize(3);
        handWritten.setHeaders(List.of(new HttpHeader(true, "Content-Type", "application/octet-stream")));
        assertFalse(SavedResponseSnapshotMapper.isLegacyBinaryPlaceholder(handWritten), "正文与大小一致说明是真实正文");

        SavedResponse text = new SavedResponse();
        text.setBody("{}");
        text.setBodySize(999);
        text.setHeaders(List.of(new HttpHeader(true, "Content-Type", "application/json")));
        assertFalse(SavedResponseSnapshotMapper.isLegacyBinaryPlaceholder(text));
    }
}
