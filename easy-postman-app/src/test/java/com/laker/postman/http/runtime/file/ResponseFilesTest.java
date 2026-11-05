package com.laker.postman.http.runtime.file;

import com.laker.postman.http.runtime.model.HttpResponse;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ResponseFilesTest {

    @Test
    public void shouldReadContentTypeIgnoringCase() {
        HttpResponse response = new HttpResponse();
        response.headers = new LinkedHashMap<>();
        response.headers.put("content-type", List.of("application/pdf"));

        assertEquals(ResponseFiles.contentType(response), "application/pdf");
        assertEquals(ResponseFiles.contentType(new HttpResponse()), "");
        assertEquals(ResponseFiles.contentType(null), "");
    }

    @Test
    public void shouldTreatRecognizedOrBinaryResponsesAsFiles() {
        HttpResponse text = withContentType("application/json");
        assertFalse(ResponseFiles.isFileResponse(text));

        HttpResponse detected = withContentType("application/json");
        detected.detectedFileExtension = ".xlsx";
        assertTrue(ResponseFiles.isFileResponse(detected));

        HttpResponse omitted = new HttpResponse();
        omitted.fileContentOmitted = true;
        assertTrue(ResponseFiles.isFileResponse(omitted));

        HttpResponse downloaded = withContentType("application/octet-stream");
        downloaded.bodySize = 1024;
        assertTrue(ResponseFiles.isFileResponse(downloaded));
        assertFalse(ResponseFiles.isFileResponse(null));

        HttpResponse failed = withContentType("application/pdf");
        failed.body = "Response incomplete: unexpected end of stream";
        assertFalse(ResponseFiles.isFileResponse(failed), "下载失败时 body 是错误提示，应按文本保留");
        failed.fileAttachment = true;
        assertFalse(ResponseFiles.isTextAttachment(failed), "二进制类型的失败响应也不是文本附件");
    }

    @Test
    public void shouldDetectLegacyGarbledFileText() throws Exception {
        String garbled = new String(ResponseFileFixtures.xlsx(), StandardCharsets.UTF_8);

        assertEquals(ResponseFiles.detectFromLegacyText(garbled), ".xlsx");
        assertNull(ResponseFiles.detectFromLegacyText("{\"ok\":true}"));
        assertNull(ResponseFiles.detectFromLegacyText(null));
    }

    @Test
    public void shouldMarkContentOmitted() {
        HttpResponse response = new HttpResponse();
        response.body = "garbled";
        response.rawBodyBytes = new byte[]{1};
        response.fileName = "keep.xlsx";

        ResponseFiles.markContentOmitted(response, ".xlsx", null);

        assertEquals(response.body, "");
        assertNull(response.rawBodyBytes);
        assertTrue(response.fileContentOmitted);
        assertEquals(response.detectedFileExtension, ".xlsx");
        assertEquals(response.fileName, "keep.xlsx");

        ResponseFiles.markContentOmitted(response, " ", "  ");
        assertNull(response.detectedFileExtension);
        assertNull(response.fileName);
    }

    private static HttpResponse withContentType(String contentType) {
        HttpResponse response = new HttpResponse();
        response.headers = new LinkedHashMap<>();
        response.headers.put("Content-Type", List.of(contentType));
        return response;
    }

    @Test
    public void shouldKeepFileStateWhenSimplifyingCorrectlyDeclaredBinary() {
        HttpResponse pdf = new HttpResponse();
        pdf.headers = new LinkedHashMap<>();
        pdf.headers.put("Content-Type", List.of("application/pdf"));
        pdf.body = "binary saved to temp file";
        pdf.bodySize = 2048;
        pdf.filePath = "/tmp/x.pdf";
        pdf.fileName = "report.pdf";

        pdf.simplify();

        assertNull(pdf.filePath);
        assertEquals(pdf.body, "");
        assertTrue(pdf.fileContentOmitted);
        assertTrue(ResponseFiles.isFileResponse(pdf));
        assertEquals(pdf.fileName, "report.pdf");

        HttpResponse json = new HttpResponse();
        json.headers = new LinkedHashMap<>();
        json.headers.put("Content-Type", List.of("application/json"));
        json.body = "{}";
        json.bodySize = 2;
        json.simplify();
        assertEquals(json.body, "{}");
        assertFalse(json.fileContentOmitted);
    }
}
