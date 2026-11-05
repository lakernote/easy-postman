package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.http.runtime.model.HttpResponse;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class FileResponseModelTest {

    @Test
    public void shouldNotShowPlainTextResponseAsFile() {
        HttpResponse response = response("application/json");
        response.body = "{}";

        assertFalse(FileResponseModel.shouldShow(response));
        assertFalse(FileResponseModel.shouldShow(null));
    }

    @Test
    public void shouldDescribeDetectedBinaryFileWithMimeMismatch() {
        HttpResponse response = response("application/json;charset=UTF-8");
        response.rawBodyBytes = new byte[]{1, 2};
        response.detectedFileExtension = ".xlsx";
        response.fileName = "export.xlsx";
        response.bodySize = 2;

        assertTrue(FileResponseModel.shouldShow(response));
        FileResponseModel model = FileResponseModel.from(response);
        assertEquals(model.fileName(), "export.xlsx");
        assertEquals(model.fileType(), "XLSX");
        assertEquals(model.detectedFileType(), "XLSX");
        assertEquals(model.declaredContentType(), "application/json;charset=UTF-8");
        assertFalse(model.canViewAsText(), "识别出的二进制格式按文本查看只有乱码");
        assertTrue(model.contentAvailable());
    }

    @Test
    public void shouldAllowTextAttachmentToBeViewedAsText() {
        HttpResponse response = response("text/csv");
        response.rawBodyBytes = "a,b".getBytes(StandardCharsets.UTF_8);
        response.fileAttachment = true;
        response.fileName = "report.csv";

        assertTrue(FileResponseModel.shouldShow(response));
        FileResponseModel model = FileResponseModel.from(response);
        assertEquals(model.fileType(), "CSV");
        assertNull(model.detectedFileType());
        assertTrue(model.canViewAsText());
    }

    @Test
    public void shouldShowOmittedContentWithoutDownload() {
        HttpResponse response = response(null);
        response.fileContentOmitted = true;
        response.detectedFileExtension = ".pdf";

        assertTrue(FileResponseModel.shouldShow(response));
        assertFalse(FileResponseModel.from(response).contentAvailable());
    }

    @Test
    public void shouldNotShowAttachmentWithoutDownloadablePayload() {
        HttpResponse response = response("application/octet-stream");
        response.filePath = "/path/does/not/exist-" + System.nanoTime();

        assertFalse(FileResponseModel.shouldShow(response));
    }

    @Test
    public void shouldFallBackToFileNameExtensionForType() {
        HttpResponse response = response("application/octet-stream");
        response.rawBodyBytes = new byte[]{1};
        response.fileName = "archive.bin";

        assertEquals(FileResponseModel.from(response).fileType(), "BIN");
    }

    private static HttpResponse response(String contentType) {
        HttpResponse response = new HttpResponse();
        response.headers = new LinkedHashMap<>();
        if (contentType != null) {
            response.headers.put("Content-Type", List.of(contentType));
        }
        return response;
    }
}
