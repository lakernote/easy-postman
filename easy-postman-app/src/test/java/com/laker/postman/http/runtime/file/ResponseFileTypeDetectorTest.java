package com.laker.postman.http.runtime.file;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ResponseFileTypeDetectorTest {

    @DataProvider
    public Object[][] knownFiles() throws IOException {
        return new Object[][]{
                {ResponseFileFixtures.xlsx(), ".xlsx"},
                {ResponseFileFixtures.ooxml("xl/workbook.bin"), ".xlsb"},
                {ResponseFileFixtures.ooxml("word/document.xml"), ".docx"},
                {ResponseFileFixtures.ooxml("ppt/presentation.xml"), ".pptx"},
                {zip("data.csv", "a,b"), ".zip"},
                {"%PDF-1.7\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1), ".pdf"},
                {ole2("Workbook"), ".xls"},
                {ole2("WordDocument"), ".doc"},
                {ole2("PowerPoint Document"), ".ppt"},
                {ole2("Book"), ".xls"},
                {ole2("Unknown"), ".bin"},
                {ResponseFileFixtures.ooxml("xl/workbook.xml",
                        "application/vnd.ms-excel.sheet.macroEnabled.main+xml"), ".xlsm"},
                {ResponseFileFixtures.ooxml("xl/workbook.xml",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml"), ".xltx"},
                {ResponseFileFixtures.ooxml("word/document.xml",
                        "application/vnd.ms-word.document.macroEnabled.main+xml"), ".docm"},
                {ResponseFileFixtures.ooxml("ppt/presentation.xml",
                        "application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml"), ".ppsx"},
                {odf("application/vnd.oasis.opendocument.text"), ".odt"},
                {odf("application/vnd.oasis.opendocument.spreadsheet-template"), ".ots"},
                {odf("application/vnd.oasis.opendocument.text-master"), ".odm"},
                {odf("application/epub+zip"), ".epub"},
                {odf("application/vnd.oasis.opendocument.graphics-template"), ".otg"},
                {odf("application/vnd.oasis.opendocument.chart"), ".odc"},
                {odf("application/vnd.oasis.opendocument.formula"), ".odf"},
                {odf("text/plain"), ".zip"},
                {odf("application/vnd.oasis.opendocument.unknown"), ".zip"},
                {zip("OFD.xml", "<ofd:OFD/>"), ".ofd"},
                {gzip("hello"), ".gz"},
                {bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C, 0x00, 0x04), ".7z"},
                {bytes(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00), ".rar"},
                {bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00), ".png"},
                {bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10), ".jpg"},
                {"GIF89a....".getBytes(StandardCharsets.US_ASCII), ".gif"},
                {"RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), ".webp"},
        };
    }

    @Test(dataProvider = "knownFiles")
    public void shouldDetectFileTypeFromSignature(byte[] content, String expectedExtension) {
        assertEquals(ResponseFileTypeDetector.detectExtension(content), expectedExtension);
    }

    @Test
    public void shouldNotDetectPlainTextOrIncompleteSignatures() {
        assertNull(ResponseFileTypeDetector.detectExtension((byte[]) null));
        assertNull(ResponseFileTypeDetector.detectExtension((java.nio.file.Path) null));
        assertNull(ResponseFileTypeDetector.detectExtension(new byte[0]));
        assertNull(ResponseFileTypeDetector.detectExtension("{\"ok\":true}".getBytes(StandardCharsets.UTF_8)));
        assertNull(ResponseFileTypeDetector.detectExtension("<html>PK</html>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ResponseFileTypeDetector.detectExtension("%PD".getBytes(StandardCharsets.US_ASCII)));
        assertNull(ResponseFileTypeDetector.detectExtension("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void shouldFallBackToKeywordsWhenZipDirectoryIsUnreadable() throws IOException {
        // 旧版本把二进制按 UTF-8 解码后保存，偏移已失效，只能依赖 ASCII 关键字
        byte[] legacyText = new String(ResponseFileFixtures.xlsx(), StandardCharsets.UTF_8)
                .getBytes(StandardCharsets.UTF_8);

        assertEquals(ResponseFileTypeDetector.detectExtension(legacyText), ".xlsx");
    }

    @Test
    public void shouldDetectBinaryControlBytesLikeWhatwgSniffing() {
        assertTrue(ResponseFileTypeDetector.looksBinary(bytes(0x08, 0x96, 0x01, 0x12, 0x03)));
        assertFalse(ResponseFileTypeDetector.looksBinary("{\"a\":1}\r\n\t".getBytes(StandardCharsets.UTF_8)));
        assertFalse(ResponseFileTypeDetector.looksBinary("中文 GBK".getBytes(java.nio.charset.Charset.forName("GBK"))));
        assertFalse(ResponseFileTypeDetector.looksBinary("ab".getBytes(StandardCharsets.UTF_16)), "带 BOM 的 UTF-16 是文本");
        assertFalse(ResponseFileTypeDetector.looksBinary(new byte[0]));
        assertFalse(ResponseFileTypeDetector.looksBinary(null));
    }

    @Test
    public void shouldDetectFileOnDisk() throws IOException {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("detector", ".tmp");
        try {
            java.nio.file.Files.write(file, ResponseFileFixtures.xlsx());
            assertEquals(ResponseFileTypeDetector.detectExtension(file), ".xlsx");
            assertNull(ResponseFileTypeDetector.detectExtension(file.resolveSibling("missing-" + file.getFileName())));
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    public void shouldOnlyLookAtHeadAndFallBackToZipBeyondIt() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(0);
            zip.putNextEntry(new ZipEntry("padding.bin"));
            zip.write(new byte[ResponseFileTypeDetector.HEAD_BYTES]);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.closeEntry();
        }

        assertEquals(ResponseFileTypeDetector.detectExtension(out.toByteArray()), ".zip");
    }

    @Test
    public void shouldDetectOoxmlVariantWhenContentTypesIsNotFirstEntry() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write("<Relationships/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<Types><Override PartName=\"/xl/workbook.xml\" "
                    + "ContentType=\"application/vnd.ms-excel.sheet.macroEnabled.main+xml\"/></Types>")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write("<workbook/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertEquals(ResponseFileTypeDetector.detectExtension(out.toByteArray()), ".xlsm");
    }

    @Test
    public void shouldTrustConfirmedStandardOoxmlOverClaimedVariant() throws IOException {
        ResponseFileTypeDetector.Detection confirmed = ResponseFileTypeDetector.detect(ResponseFileFixtures.ooxml(
                "xl/workbook.xml", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"));
        ResponseFileTypeDetector.Detection guessed = ResponseFileTypeDetector.detect(ResponseFileFixtures.xlsx());

        assertEquals(confirmed, new ResponseFileTypeDetector.Detection(".xlsx", true));
        assertEquals(guessed, new ResponseFileTypeDetector.Detection(".xlsx", false), "只按关键字认出时未确认子类型");
        assertEquals(ResponseFileTypeDetector.reconcile(".xlsm", confirmed), ".xlsx", "已确认是标准 xlsx，纠正错误的 .xlsm");
        assertEquals(ResponseFileTypeDetector.reconcile(".xlsm", guessed), ".xlsm");
        assertEquals(ResponseFileTypeDetector.reconcile(".jpeg",
                new ResponseFileTypeDetector.Detection(".jpg", true)), ".jpeg", "同一格式的写法始终保留");
        assertEquals(ResponseFileTypeDetector.reconcile(".xlt", ResponseFileTypeDetector.detect(ole2("Workbook"))), ".xlt",
                "OLE2 无法区分模板");
        assertNull(ResponseFileTypeDetector.detect("plain".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void shouldReconcileClaimedExtensionWithSignature() {
        assertEquals(ResponseFileTypeDetector.reconcile(".png", ".pdf"), ".pdf", "唯一文件头冲突时以内容为准");
        assertEquals(ResponseFileTypeDetector.reconcile(".xls", ".xlsx"), ".xlsx");
        assertEquals(ResponseFileTypeDetector.reconcile(".png", ".zip"), ".zip");
        assertEquals(ResponseFileTypeDetector.reconcile(".yaml", ".zip"), ".zip", "只保留允许列表中的容器格式");
        assertEquals(ResponseFileTypeDetector.reconcile(".exe", ".zip"), ".zip");
        assertEquals(ResponseFileTypeDetector.reconcile(".mp4", ".bin"), ".bin");
        assertEquals(ResponseFileTypeDetector.reconcile(".odt", ".zip"), ".odt");
        assertEquals(ResponseFileTypeDetector.reconcile(".xlsm", ".xlsx"), ".xlsm", "识别结果宽泛时保留更具体的扩展名");
        assertEquals(ResponseFileTypeDetector.reconcile(".xlsx", ".xlsm"), ".xlsm", "识别出明确变体时以识别结果为准");
        assertEquals(ResponseFileTypeDetector.reconcile(".docx", ".dotm"), ".dotm");
        assertEquals(ResponseFileTypeDetector.reconcile(".JPEG", ".jpg"), ".JPEG");
        assertEquals(ResponseFileTypeDetector.reconcile(".jar", ".zip"), ".jar", "通用容器不否定基于容器的格式");
        assertEquals(ResponseFileTypeDetector.reconcile(".xlsx", ".bin"), ".xlsx", "加密 Office 是 OLE2 容器");
        assertEquals(ResponseFileTypeDetector.reconcile(".msg", ".bin"), ".msg");
        assertEquals(ResponseFileTypeDetector.reconcile(null, ".pdf"), ".pdf");
        assertEquals(ResponseFileTypeDetector.reconcile(".pdf", (String) null), ".pdf");
    }

    /** ODF/EPUB：第一个条目是未压缩的 mimetype。 */
    private static byte[] odf(String mimeType) throws IOException {
        byte[] content = mimeType.getBytes(StandardCharsets.US_ASCII);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(content);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry entry = new ZipEntry("mimetype");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(content);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write("<office:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] zip(String name, String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] ole2(String streamName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1));
        out.writeBytes(new byte[504]);
        out.writeBytes(streamName.getBytes(StandardCharsets.UTF_16LE));
        return out.toByteArray();
    }

    private static byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

}
