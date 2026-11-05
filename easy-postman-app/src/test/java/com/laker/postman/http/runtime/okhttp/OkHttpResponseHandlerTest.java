package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.file.ResponseFileFixtures;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class OkHttpResponseHandlerTest {

    @Test
    public void shouldDiscardResponseBodyWhenMetadataOnlyModeIsUsed() throws Exception {
        String largeBody = "x".repeat(256 * 1024);
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                responseWithBody(largeBody),
                response,
                null,
                PreparedRequest.ResponseBodyMode.METADATA_ONLY,
                1024
        );

        assertEquals(response.bodySize, largeBody.length());
        assertFalse(response.body.contains(largeBody.substring(0, 1024)));
        assertTrue(response.body.contains("skipped") || response.body.contains("跳过"), response.body);
    }

    @Test
    public void shouldKeepOnlyPreviewWhenPreviewModeIsUsed() throws Exception {
        String largeBody = "0123456789".repeat(2048);
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                responseWithBody(largeBody),
                response,
                null,
                PreparedRequest.ResponseBodyMode.PREVIEW,
                128
        );

        assertEquals(response.bodySize, largeBody.length());
        assertTrue(response.body.startsWith("0123456789"), response.body);
        assertTrue(response.body.length() < largeBody.length() / 10, "Preview should not retain the full body");
        assertTrue(response.body.contains("truncated") || response.body.contains("截断"), response.body);
    }

    @Test
    public void shouldKeepRawBytesAndDetectXlsxWhenServerDeclaresJson() throws Exception {
        byte[] xlsx = ResponseFileFixtures.xlsx();
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/api/export.do", "application/json;charset=UTF-8", null, xlsx),
                response,
                null
        );

        assertEquals(response.detectedFileExtension, ".xlsx");
        assertEquals(response.fileName, "export.xlsx");
        assertEquals(response.rawBodyBytes, xlsx);
        assertEquals(response.bodySize, xlsx.length);
        assertFalse(response.fileAttachment);
    }

    @Test
    public void shouldUseContentDispositionFileNameForDetectedFile() throws Exception {
        byte[] xlsx = ResponseFileFixtures.xlsx();
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/api/export", "text/plain",
                        "attachment; filename=\"orders.xlsx\"", xlsx),
                response,
                null
        );

        assertEquals(response.fileName, "orders.xlsx");
        assertTrue(response.fileAttachment);
        assertEquals(response.rawBodyBytes, xlsx);
    }

    @Test
    public void shouldKeepSmartFileNameAndSkipRawBytesForPlainUtf8Json() throws Exception {
        String json = "{\"name\":\"测试\"}";
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/users/123", "application/json; charset=utf-8", null,
                        json.getBytes(StandardCharsets.UTF_8)),
                response,
                null
        );

        assertEquals(response.body, json);
        assertNull(response.rawBodyBytes, "Plain UTF-8 text can be restored from body");
        assertNull(response.detectedFileExtension);
        assertFalse(response.fileAttachment);
        assertNotNull(response.fileName);
        assertFalse(response.fileName.startsWith("123"), response.fileName);
        assertTrue(response.fileName.endsWith(".json"), response.fileName);
    }

    @Test
    public void shouldTreatTextAttachmentAsFileResponse() throws Exception {
        byte[] csv = "id,name\n1,Alice\n".getBytes(StandardCharsets.UTF_8);
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/export", "text/csv",
                        "attachment; filename=\"report.csv\"", csv),
                response,
                null
        );

        assertTrue(response.fileAttachment);
        assertEquals(response.fileName, "report.csv");
        assertEquals(response.rawBodyBytes, csv);
        assertNull(response.detectedFileExtension);
    }

    @Test
    public void shouldKeepInlineFilenameJsonAsPlainText() throws Exception {
        String json = "{\"ok\":true}";
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/user/list.do", "application/json;charset=UTF-8",
                        "inline;filename=f.txt", json.getBytes(StandardCharsets.UTF_8)),
                response,
                null
        );

        assertEquals(response.body, json);
        assertFalse(response.fileAttachment);
        assertNull(response.rawBodyBytes);
        assertTrue(response.fileName.endsWith(".json"), response.fileName);
    }

    @Test
    public void shouldKeepRawBytesForNonUtf8Charset() throws Exception {
        Charset gbk = Charset.forName("GBK");
        byte[] text = "中文内容".getBytes(gbk);
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/text", "text/plain; charset=GBK", null, text),
                response,
                null
        );

        assertEquals(response.body, "中文内容");
        assertEquals(response.rawBodyBytes, text);
    }

    @Test
    public void shouldKeepRawBytesWhenBodyIsNotValidUtf8() throws Exception {
        byte[] invalidUtf8 = {0x61, (byte) 0xC3, 0x28, 0x62};
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/text", "text/plain", null, invalidUtf8),
                response,
                null
        );

        assertEquals(response.rawBodyBytes, invalidUtf8);
        assertNull(response.detectedFileExtension);
    }

    @Test
    public void shouldSniffDeclaredBinaryResponseAndPreferDetectedTypeForFileName() throws Exception {
        byte[] xlsx = ResponseFileFixtures.xlsx();
        HttpResponse octetStream = new HttpResponse();
        HttpResponse wrongExcelMime = new HttpResponse();
        try {
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/api/export.do", "application/octet-stream", null, xlsx),
                    octetStream, null);
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/api/report", "application/vnd.ms-excel", null, xlsx),
                    wrongExcelMime, null);

            assertNotNull(octetStream.filePath);
            assertEquals(octetStream.detectedFileExtension, ".xlsx");
            assertEquals(octetStream.fileName, "export.xlsx");
            assertEquals(wrongExcelMime.detectedFileExtension, ".xlsx", "声明具体但与内容冲突时提示");
            assertEquals(wrongExcelMime.fileName, "report.xlsx");
        } finally {
            deleteTempFile(octetStream);
            deleteTempFile(wrongExcelMime);
        }
    }

    @Test
    public void shouldNotFlagDeclaredBinaryResponseWhenContentMatches() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        HttpResponse declaredPng = new HttpResponse();
        HttpResponse octetPng = new HttpResponse();
        try {
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/logo.png", "image/png", null, png), declaredPng, null);
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/avatar", "application/octet-stream", null, png), octetPng, null);

            assertNull(declaredPng.detectedFileExtension);
            assertEquals(declaredPng.fileName, "logo.png");
            assertTrue(declaredPng.isImage);
            assertEquals(octetPng.detectedFileExtension, ".png");
            assertEquals(octetPng.fileName, "avatar.png");
            assertTrue(octetPng.isImage, "按内容识别出的图片也可以预览");
        } finally {
            deleteTempFile(declaredPng);
            deleteTempFile(octetPng);
        }
    }

    @Test
    public void shouldPreferSignatureOverConflictingImageTypeAndUrlSuffix() throws Exception {
        byte[] pdf = "%PDF-1.7\n%\u00e2\u00e3\n".getBytes(StandardCharsets.ISO_8859_1);
        HttpResponse response = new HttpResponse();
        try {
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/photo.png", "image/png", null, pdf), response, null);

            assertEquals(response.detectedFileExtension, ".pdf");
            assertEquals(response.fileName, "photo.pdf");
            assertFalse(response.isImage, "实际是 PDF，不能按图片预览");
        } finally {
            deleteTempFile(response);
        }
    }

    @Test
    public void shouldNotFlagZipDeclaredAsZipButAdoptDetectedVariant() throws Exception {
        HttpResponse zip = new HttpResponse();
        HttpResponse docx = new HttpResponse();
        HttpResponse xlsm = new HttpResponse();
        try {
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/archive", "application/zip", null, zipWithEntry("a.csv")), zip, null);
            OkHttpResponseHandler.handleResponse(response("http://example.test/doc", "application/zip", null,
                    com.laker.postman.http.runtime.file.ResponseFileFixtures.ooxml("word/document.xml")), docx, null);
            OkHttpResponseHandler.handleResponse(response("http://example.test/report.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null,
                    com.laker.postman.http.runtime.file.ResponseFileFixtures.ooxml("xl/workbook.xml",
                            "application/vnd.ms-excel.sheet.macroEnabled.main+xml")), xlsm, null);

            assertNull(zip.detectedFileExtension, "application/zip + ZIP 不提示类型不符");
            assertEquals(zip.fileName, "archive.zip");
            assertNull(docx.detectedFileExtension);
            assertEquals(docx.fileName, "doc.docx");
            assertEquals(xlsm.detectedFileExtension, ".xlsm");
            assertEquals(xlsm.fileName, "report.xlsm", "启用宏的工作簿必须使用 .xlsm 才能被 Excel 打开");

            HttpResponse plainXlsx = new HttpResponse();
            OkHttpResponseHandler.handleResponse(response("http://example.test/report.xlsm",
                    "application/vnd.ms-excel.sheet.macroEnabled.12", null,
                    com.laker.postman.http.runtime.file.ResponseFileFixtures.ooxml("xl/workbook.xml",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml")), plainXlsx, null);
            deleteTempFile(plainXlsx);
            assertEquals(plainXlsx.detectedFileExtension, ".xlsx");
            assertEquals(plainXlsx.fileName, "report.xlsx", "已确认是标准 xlsx 时纠正错误声明的 .xlsm");
        } finally {
            deleteTempFile(zip);
            deleteTempFile(docx);
            deleteTempFile(xlsm);
        }
    }

    @Test
    public void shouldTrustSpecificDeclaredTypeWhenOnlyContainerIsDetected() throws Exception {
        byte[] zip = zipWithEntry("classes.dex");
        HttpResponse apk = new HttpResponse();
        HttpResponse unmapped = new HttpResponse();
        try {
            OkHttpResponseHandler.handleResponse(response("http://example.test/download",
                    "application/vnd.android.package-archive", null, zip), apk, null);
            OkHttpResponseHandler.handleResponse(response("http://example.test/download",
                    "application/vnd.example.bundle", null, zip), unmapped, null);

            assertNull(apk.detectedFileExtension);
            assertEquals(apk.fileName, "download.apk");
            assertNull(unmapped.detectedFileExtension, "未映射的具体 MIME 不应被通用 ZIP 识别误报为不符");
            assertEquals(unmapped.fileName, "download");
        } finally {
            deleteTempFile(apk);
            deleteTempFile(unmapped);
        }
    }

    @Test
    public void shouldKeepUrlSuffixForUnknownBinaryWithoutSignature() throws Exception {
        byte[] protobuf = {0x08, (byte) 0x96, 0x01, 0x12, 0x03, 0x61, 0x62, 0x63};
        HttpResponse response = new HttpResponse();
        OkHttpResponseHandler.handleResponse(
                response("http://example.test/data.pb", null, null, protobuf), response, null);

        assertEquals(response.detectedFileExtension, ".bin");
        assertEquals(response.fileName, "data.pb", "仅按控制字节判断的未知二进制不能否定 URL 后缀");
    }

    @Test
    public void shouldTreatUnlabeledBinaryAsUnknownFile() throws Exception {
        byte[] protobuf = {0x08, (byte) 0x96, 0x01, 0x12, 0x03, 0x61, 0x62, 0x63};
        HttpResponse unlabeled = new HttpResponse();
        HttpResponse json = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/api/data", null, null, protobuf), unlabeled, null);
        OkHttpResponseHandler.handleResponse(
                response("http://example.test/api/data", null, null,
                        "{\"ok\":true}".getBytes(StandardCharsets.UTF_8)), json, null);

        assertEquals(unlabeled.detectedFileExtension, ".bin");
        assertEquals(unlabeled.rawBodyBytes, protobuf);
        assertEquals(unlabeled.fileName, "data.bin");
        assertNull(json.detectedFileExtension);
    }

    @Test
    public void shouldDecodeChineseContentDispositionFileName() throws Exception {
        byte[] xlsx = ResponseFileFixtures.xlsx();
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/api/export", "application/json",
                        "attachment; filename*=UTF-8''%E8%AE%A2%E5%8D%95.xlsx", xlsx),
                response, null);

        assertEquals(response.fileName, "订单.xlsx");
    }

    @Test
    public void shouldKeepUtf16TextWithoutBomAsText() throws Exception {
        byte[] utf16 = "hello 中文".getBytes(StandardCharsets.UTF_16LE);
        HttpResponse response = new HttpResponse();

        OkHttpResponseHandler.handleResponse(
                response("http://example.test/text", "text/plain; charset=UTF-16LE", null, utf16), response, null);

        assertNull(response.detectedFileExtension);
        assertEquals(response.body, "hello 中文");
    }

    @Test
    public void shouldNameGenericZipDownloadWithZipExtension() throws Exception {
        byte[] zip = zipWithEntry("data.csv");
        HttpResponse download = new HttpResponse();
        HttpResponse disguised = new HttpResponse();
        HttpResponse jar = new HttpResponse();
        try {
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/download", "application/octet-stream", null, zip), download, null);
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/photo.png", "application/octet-stream", null, zip), disguised, null);
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/app.jar", "application/java-archive", null, zip), jar, null);

            assertEquals(download.fileName, "download.zip");
            assertEquals(download.detectedFileExtension, ".zip");
            assertEquals(disguised.fileName, "photo.zip", "png 有唯一文件头，ZIP 内容的 .png 后缀一定是错的");
            assertEquals(jar.fileName, "app.jar");
            assertNull(jar.detectedFileExtension, "具体的声明类型不应被通用 ZIP 识别误报为不符");
        } finally {
            deleteTempFile(download);
            deleteTempFile(disguised);
            deleteTempFile(jar);
        }
    }

    private static byte[] zipWithEntry(String name) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(name));
            zip.write("a,b".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static void deleteTempFile(HttpResponse response) throws IOException {
        if (response.filePath != null) {
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(response.filePath));
        }
    }

    private static Response response(String url, String contentType, String contentDisposition, byte[] body) {
        Response.Builder builder = new Response.Builder()
                .request(new Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(body, contentType == null ? null : MediaType.get(contentType)));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        if (contentDisposition != null) {
            builder.header("Content-Disposition", contentDisposition);
        }
        return builder.build();
    }

    private static Response responseWithBody(String body) throws IOException {
        return new Response.Builder()
                .request(new Request.Builder().url("http://example.test/large").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(ResponseBody.create(body, MediaType.get("text/plain; charset=utf-8")))
                .build();
    }

    @Test
    public void shouldCorrectRouteSuffixWhenDeclaredTypeMatchesContent() throws Exception {
        byte[] pdf = "%PDF-1.7\n%\u00e2\u00e3\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        HttpResponse pdfResponse = new HttpResponse();
        HttpResponse pngResponse = new HttpResponse();
        HttpResponse yamlZip = new HttpResponse();
        try {
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/export.do", "application/pdf", null, pdf), pdfResponse, null);
            OkHttpResponseHandler.handleResponse(
                    response("http://example.test/avatar.action", "image/png", null, png), pngResponse, null);
            OkHttpResponseHandler.handleResponse(response("http://example.test/data.yaml",
                    "application/octet-stream", null, zipWithEntry("a.csv")), yamlZip, null);

            assertEquals(pdfResponse.fileName, "export.pdf");
            assertNull(pdfResponse.detectedFileExtension, "内容与声明一致，不提示类型不符");
            assertEquals(pngResponse.fileName, "avatar.png");
            assertEquals(yamlZip.fileName, "data.zip", "非容器格式的后缀以内容为准");
        } finally {
            deleteTempFile(pdfResponse);
            deleteTempFile(pngResponse);
            deleteTempFile(yamlZip);
        }
    }

    @Test
    public void shouldKeepMoreSpecificDeclaredSubtypeWhenSniffingOnlyFindsFamily() throws Exception {
        HttpResponse response = new HttpResponse();
        try {
            OkHttpResponseHandler.handleResponse(response("http://example.test/export",
                    "application/vnd.ms-excel.sheet.macroEnabled.12", null,
                    com.laker.postman.http.runtime.file.ResponseFileFixtures.xlsx()), response, null);

            assertEquals(response.fileName, "export.xlsm", "文件头只能确定 .xlsx 时不能覆盖声明的 .xlsm");
            assertNull(response.detectedFileExtension);
        } finally {
            deleteTempFile(response);
        }
    }
}
