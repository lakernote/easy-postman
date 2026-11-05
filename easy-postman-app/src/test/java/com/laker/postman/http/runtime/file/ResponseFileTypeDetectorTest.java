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
                {ole2("Unknown"), ".bin"},
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
