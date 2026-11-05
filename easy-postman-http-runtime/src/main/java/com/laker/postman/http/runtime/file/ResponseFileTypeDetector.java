package com.laker.postman.http.runtime.file;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;

/**
 * 根据响应体开头的字节识别文件类型，结果只用于界面提示和默认文件名。
 * <p>
 * 只看文件头和开头一小段内容，不解析压缩包或复合文档结构：识别不了时返回 {@code null}，
 * 任何异常都不会抛出。下载始终保存原始字节，与识别结果无关。
 * </p>
 */
@UtilityClass
public class ResponseFileTypeDetector {

    /** 识别时最多查看的字节数。 */
    static final int HEAD_BYTES = 64 * 1024;
    /** WHATWG MIME Sniffing 判断二进制时查看的字节数。 */
    private static final int BINARY_SNIFF_BYTES = 1445;

    private static final byte[] PDF = ascii("%PDF-");
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] ZIP_EMPTY = {0x50, 0x4B, 0x05, 0x06};
    private static final byte[] OLE2 = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final byte[] GZIP = {0x1F, (byte) 0x8B, 0x08};
    private static final byte[] SEVEN_ZIP = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final byte[] RAR = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87A = ascii("GIF87a");
    private static final byte[] GIF89A = ascii("GIF89a");
    private static final byte[] RIFF = ascii("RIFF");
    private static final byte[] WEBP = ascii("WEBP");

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};

    /** Office Open XML：ZIP 中的条目名紧跟在本地文件头之后，按名称即可区分。 */
    private static final byte[] OOXML_CONTENT_TYPES = ascii("[Content_Types].xml");
    private static final byte[][] OOXML_PARTS = {
            ascii("xl/workbook.bin"), ascii("xl/workbook.xml"), ascii("word/document.xml"), ascii("ppt/presentation.xml")
    };
    private static final String[] OOXML_EXTENSIONS = {".xlsb", ".xlsx", ".docx", ".pptx"};

    /** 旧版 Office 复合文档（OLE2）：流名以 UTF-16LE 存放在目录中。 */
    private static final byte[][] OLE2_STREAMS = {
            utf16le("WordDocument"), utf16le("PowerPoint Document"), utf16le("Workbook")
    };
    private static final String[] OLE2_EXTENSIONS = {".doc", ".ppt", ".xls"};

    /**
     * 返回识别出的扩展名（含点），无法确定时返回 {@code null}。
     * ZIP、OLE2 无法确定具体文档类型时分别返回 {@code .zip}、{@code .bin}。
     */
    public static String detectExtension(byte[] bytes) {
        if (bytes == null || bytes.length < 3) {
            return null;
        }
        try {
            return detectSignature(bytes);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 识别已落盘的响应文件，只读取开头 {@link #HEAD_BYTES} 字节；读取失败时返回 {@code null}。
     */
    public static String detectExtension(Path file) {
        if (file == null) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return detectExtension(in.readNBytes(HEAD_BYTES));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 按 WHATWG MIME Sniffing 的二进制判定：开头一段内容里出现文本中不会出现的控制字节时视为二进制。
     * 带 UTF-8/UTF-16 BOM 的内容视为文本。
     */
    public static boolean looksBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        if (startsWith(bytes, UTF8_BOM) || startsWith(bytes, UTF16_BE_BOM) || startsWith(bytes, UTF16_LE_BOM)) {
            return false;
        }
        int limit = Math.min(bytes.length, BINARY_SNIFF_BYTES);
        for (int i = 0; i < limit; i++) {
            int b = bytes[i] & 0xFF;
            if (b <= 0x08 || b == 0x0B || (b >= 0x0E && b <= 0x1A) || (b >= 0x1C && b <= 0x1F)) {
                return true;
            }
        }
        return false;
    }

    private static String detectSignature(byte[] bytes) {
        if (startsWith(bytes, PDF)) {
            return ".pdf";
        }
        if (startsWith(bytes, ZIP) || startsWith(bytes, ZIP_EMPTY)) {
            return contains(bytes, OOXML_CONTENT_TYPES) ? firstContained(bytes, OOXML_PARTS, OOXML_EXTENSIONS, ".zip") : ".zip";
        }
        if (startsWith(bytes, OLE2)) {
            return firstContained(bytes, OLE2_STREAMS, OLE2_EXTENSIONS, ".bin");
        }
        if (startsWith(bytes, GZIP)) {
            return ".gz";
        }
        if (startsWith(bytes, SEVEN_ZIP)) {
            return ".7z";
        }
        if (startsWith(bytes, RAR)) {
            return ".rar";
        }
        if (startsWith(bytes, PNG)) {
            return ".png";
        }
        if (startsWith(bytes, JPEG)) {
            return ".jpg";
        }
        if (startsWith(bytes, GIF87A) || startsWith(bytes, GIF89A)) {
            return ".gif";
        }
        if (startsWith(bytes, RIFF) && regionMatches(bytes, 8, WEBP)) {
            return ".webp";
        }
        return null;
    }

    private static String firstContained(byte[] bytes, byte[][] markers, String[] extensions, String fallback) {
        for (int i = 0; i < markers.length; i++) {
            if (contains(bytes, markers[i])) {
                return extensions[i];
            }
        }
        return fallback;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return regionMatches(bytes, 0, prefix);
    }

    private static boolean regionMatches(byte[] bytes, int offset, byte[] target) {
        if (offset < 0 || offset + target.length > bytes.length) {
            return false;
        }
        for (int i = 0; i < target.length; i++) {
            if (bytes[offset + i] != target[i]) {
                return false;
            }
        }
        return true;
    }

    /** 只在开头 {@link #HEAD_BYTES} 字节内查找，内存中的大响应体也只扫描这一段。 */
    private static boolean contains(byte[] bytes, byte[] target) {
        int limit = Math.min(bytes.length, HEAD_BYTES) - target.length;
        for (int i = 0; i <= limit; i++) {
            if (regionMatches(bytes, i, target)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] utf16le(String value) {
        return value.getBytes(StandardCharsets.UTF_16LE);
    }
}
