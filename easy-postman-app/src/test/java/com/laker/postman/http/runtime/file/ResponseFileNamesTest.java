package com.laker.postman.http.runtime.file;

import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ResponseFileNamesTest {

    @Test
    public void shouldReturnFirstContentDispositionHeaderIgnoringCase() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("content-disposition", List.of("attachment; filename=\"report.csv\""));
        headers.put("X-Test", List.of("ok"));

        assertEquals(ResponseFileNames.getContentDisposition(headers), "attachment; filename=\"report.csv\"");
    }

    @Test
    public void shouldReturnNullWhenContentDispositionHeaderIsMissingOrEmpty() {
        Map<String, List<String>> empty = new LinkedHashMap<>();
        empty.put("Content-Disposition", List.of());

        assertNull(ResponseFileNames.getContentDisposition(new LinkedHashMap<>()));
        assertNull(ResponseFileNames.getContentDisposition(empty));
        assertNull(ResponseFileNames.getContentDisposition(null));
    }

    @Test
    public void shouldParseFilenameFromContentDisposition() {
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition("attachment; filename=\"report.csv\""),
                "report.csv");
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition("attachment; filename=report.csv; size=1"),
                "report.csv");
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename*=UTF-8''%E6%B5%8B%E8%AF%95.txt"), "测试.txt");
        assertNull(ResponseFileNames.parseFileNameFromContentDisposition("attachment"));
        assertNull(ResponseFileNames.parseFileNameFromContentDisposition(null));
    }

    @Test
    public void shouldFollowRfc6266FileNamePrecedenceAndDecoding() {
        // filename* 优先于 filename，无论出现顺序
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename=\"fallback.xlsx\"; filename*=UTF-8''%E6%8A%A5%E8%A1%A8.xlsx"), "报表.xlsx");
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename*=utf-8'zh-CN'%E6%8A%A5%E8%A1%A8.xlsx; filename=fallback.xlsx"), "报表.xlsx");
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename*=ISO-8859-1''na%EFve.txt"), "naïve.txt");
        // filename* 无效时退回 filename
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename*=UNKNOWN-CHARSET''x.txt; filename=\"ok.txt\""), "ok.txt");
        // 常见的非标准写法：URLEncoder 编码、RFC 2047 编码字、省略类型、引号内转义
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment;filename=%E6%8A%A5%E8%A1%A8.xlsx"), "报表.xlsx");
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename=\"=?UTF-8?B?5oql6KGoLnhsc3g=?=\""), "报表.xlsx");
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition("filename=report.xlsx"), "report.xlsx");
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename=\"a \\\"quoted\\\" name; v2.txt\""), "a _quoted_ name; v2.txt");
        // 不是合法百分号编码时保持原样
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename=\"100%.txt\""), "100%.txt");
    }

    @Test
    public void shouldSanitizeUnsafeFileNames() {
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename=\"../../etc/passwd\""), "passwd");
        assertEquals(ResponseFileNames.parseFileNameFromContentDisposition(
                "attachment; filename=\"C:\\\\Windows\\\\evil.bat\""), "evil.bat");
        assertEquals(ResponseFileNames.sanitize("a<b>c:d|e?f*.txt"), "a_b_c_d_e_f_.txt");
        assertEquals(ResponseFileNames.sanitize("report.xlsx. "), "report.xlsx");
        assertEquals(ResponseFileNames.sanitize("con.txt"), "_con.txt");
        assertEquals(ResponseFileNames.sanitize("tab\tname.txt"), "tab_name.txt");
        assertNull(ResponseFileNames.sanitize("../"));
        assertNull(ResponseFileNames.sanitize(" . "));
        assertEquals(ResponseFileNames.sanitize("x".repeat(300) + ".xlsx").length(), 200);
        assertTrue(ResponseFileNames.sanitize("x".repeat(300) + ".xlsx").endsWith(".xlsx"));
    }

    @Test
    public void shouldDecodeUrlFileName() {
        assertEquals(ResponseFileNames.resolve(null, "/files/%E6%8A%A5%E8%A1%A8.xlsx", ".xlsx"), "报表.xlsx");
        assertEquals(ResponseFileNames.resolve(null, "/files/a%2F..%2Fb.pdf", ".pdf"), "b.pdf");
    }

    @Test
    public void shouldOnlyTreatAttachmentDispositionAsFileDownload() {
        assertTrue(ResponseFileNames.isAttachment("attachment"));
        assertTrue(ResponseFileNames.isAttachment(" Attachment; filename=a.xlsx"));
        assertFalse(ResponseFileNames.isAttachment("inline; filename=\"a.pdf\""));
        assertFalse(ResponseFileNames.isAttachment("inline;filename=f.txt"), "Spring RFD 防护头不代表文件下载");
        assertFalse(ResponseFileNames.isAttachment("attachmentish"));
        assertFalse(ResponseFileNames.isAttachment("inline"));
        assertFalse(ResponseFileNames.isAttachment(" "));
        assertFalse(ResponseFileNames.isAttachment(null));
    }

    @Test
    public void shouldPreferContentDispositionAndAppendMissingExtension() {
        assertEquals(ResponseFileNames.resolve("attachment; filename=\"report.pdf\"", "/x/y", ".bin"),
                "report.pdf");
        assertEquals(ResponseFileNames.resolve("attachment; filename=\"report\"", "/x/y", ".xlsx"),
                "report.xlsx");
    }

    @Test
    public void shouldResolveUrlFileNameAndTrustDetectedExtension() {
        assertEquals(ResponseFileNames.resolve(null, "/api/export.do", ".xlsx"), "export.xlsx");
        assertEquals(ResponseFileNames.resolve(null, "/api/report.xlsx", ".xlsx"), "report.xlsx");
        assertEquals(ResponseFileNames.resolve(null, "/api/download", ".pdf"), "download.pdf");
        assertTrue(ResponseFileNames.resolve(null, "/api/", ".pdf").endsWith(".pdf"));
    }

    @Test
    public void shouldKeepRealUrlExtensionAndReplaceOnlyRouteOrTextSuffix() {
        // URL 中的文件类后缀保留（含 .jar/.xlsm/.msg 等文件头无法区分的子类型）
        assertEquals(ResponseFileNames.resolve(null, "/files/app.jar", ".zip"), "app.jar");
        assertEquals(ResponseFileNames.resolve(null, "/download/report.xlsm", ".xlsx"), "report.xlsm");
        assertEquals(ResponseFileNames.resolve(null, "/mail/message.msg", ".bin"), "message.msg");
        assertEquals(ResponseFileNames.resolve(null, "/file.dat", ".zip"), "file.dat");
        // 接口路由和文本类后缀按内容替换
        assertEquals(ResponseFileNames.resolve(null, "/api/export.action", ".pdf"), "export.pdf");
        assertEquals(ResponseFileNames.resolve(null, "/api/data.json", ".bin"), "data.bin");
        assertEquals(ResponseFileNames.resolve(null, "/api/export.do", ".zip"), "export.zip");
        assertEquals(ResponseFileNames.resolve(null, "/api/export", ".zip"), "export.zip");
        assertEquals(ResponseFileNames.resolve(null, "/data.JSON", ".json"), "data.JSON");
    }

    @Test
    public void shouldExtractFileNameFromUrlPath() {
        assertEquals(ResponseFileNames.fileNameFromUrlPath("/a/b/report.pdf"), "report.pdf");
        assertEquals(ResponseFileNames.fileNameFromUrlPath("/a/report.pdf!/inner"), "inner");
        assertEquals(ResponseFileNames.fileNameFromUrlPath("/a/report.pdf!x"), "report.pdf");
        assertNull(ResponseFileNames.fileNameFromUrlPath("/a/"));
        assertNull(ResponseFileNames.fileNameFromUrlPath(null));
    }

    @Test
    public void shouldReturnExtensionOfFileName() {
        assertEquals(ResponseFileNames.extensionOf("a.tar.gz"), ".gz");
        assertNull(ResponseFileNames.extensionOf("README"));
        assertNull(ResponseFileNames.extensionOf("trailing."));
        assertNull(ResponseFileNames.extensionOf(null));
    }

    @Test
    public void shouldReplaceDynamicRouteSuffixWithDeclaredExtension() {
        assertEquals(ResponseFileNames.resolve(null, "/stream.do", ".mp4"), "stream.mp4");
        assertEquals(ResponseFileNames.resolve(null, "/export.php", ".csv"), "export.csv");
        assertEquals(ResponseFileNames.resolve(null, "/export.do", null), "export.do");
    }
}
