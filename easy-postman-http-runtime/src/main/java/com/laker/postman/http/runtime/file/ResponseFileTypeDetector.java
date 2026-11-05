package com.laker.postman.http.runtime.file;

import com.laker.postman.util.FileExtensionUtil;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * 根据响应体开头的字节识别文件类型，结果只用于界面提示和默认文件名。
 * <p>
 * 只看文件头和开头一小段内容（最多 {@link #HEAD_BYTES} 字节），不遍历压缩包目录或复合文档结构：识别不了时返回 {@code null}，
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

    private static final byte[] OFD_ENTRY = ascii("OFD.xml");

    /** OOXML：[Content_Types].xml 中主部件的内容类型，可确定标准格式及宏/模板变体。 */
    private static final Map<String, String> OOXML_MAIN_TYPES = Map.ofEntries(
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml", ".xlsx"),
            Map.entry("application/vnd.ms-excel.sheet.binary.macroEnabled.main", ".xlsb"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml", ".docx"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml", ".pptx"),
            Map.entry("application/vnd.ms-excel.sheet.macroEnabled.main+xml", ".xlsm"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml", ".xltx"),
            Map.entry("application/vnd.ms-excel.template.macroEnabled.main+xml", ".xltm"),
            Map.entry("application/vnd.ms-excel.addin.macroEnabled.main+xml", ".xlam"),
            Map.entry("application/vnd.ms-word.document.macroEnabled.main+xml", ".docm"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml", ".dotx"),
            Map.entry("application/vnd.ms-word.template.macroEnabledTemplate.main+xml", ".dotm"),
            Map.entry("application/vnd.ms-powerpoint.presentation.macroEnabled.main+xml", ".pptm"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml", ".ppsx"),
            Map.entry("application/vnd.ms-powerpoint.slideshow.macroEnabled.main+xml", ".ppsm"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.template.main+xml", ".potx"),
            Map.entry("application/vnd.ms-powerpoint.template.macroEnabled.main+xml", ".potm"));

    /** ODF/EPUB：规范要求第一个条目是未压缩的 mimetype 文件，其内容按 {@link FileExtensionUtil} 映射扩展名。 */
    private static final byte[] ODF_MIMETYPE_ENTRY = ascii("mimetype");

    /** 旧版 Office 复合文档（OLE2）：流名以 UTF-16LE 存放在目录中；Book 是 Excel 5/95 的工作簿流。 */
    private static final byte[][] OLE2_STREAMS = {
            utf16le("WordDocument"), utf16le("PowerPoint Document"), utf16le("Workbook"), utf16le("Book")
    };
    private static final String[] OLE2_EXTENSIONS = {".doc", ".ppt", ".xls", ".xls"};

    /** OOXML 共有的基础扩展名，用于列出容器兼容的后缀。 */
    private static final Set<String> OOXML_EXTENSIONS_ALL = Set.of(
            ".xlsx", ".xlsm", ".xlsb", ".xltx", ".xltm", ".xlam", ".docx", ".docm", ".dotx", ".dotm",
            ".pptx", ".pptm", ".ppsx", ".ppsm", ".potx", ".potm");

    /**
     * 只识别到通用容器时，可以保留的已有扩展名（允许列表）：这些格式本身就是 ZIP / OLE2 容器，
     * 文件头无法进一步区分。其余后缀（.yaml/.exe/.mp4/.png 等）都以识别结果为准。
     */
    private static final Map<String, Set<String>> CONTAINER_COMPATIBLE = Map.of(
            ".zip", union(OOXML_EXTENSIONS_ALL, Set.of(
                    ".jar", ".war", ".ear", ".apk", ".aar", ".aab", ".xapk", ".apks", ".ipa", ".xpi", ".crx",
                    ".whl", ".egg", ".nupkg", ".vsix", ".msix", ".msixbundle", ".appx", ".appxbundle",
                    ".epub", ".ofd", ".xps", ".oxps", ".3mf", ".kmz", ".cbz", ".sketch", ".xmind", ".idml",
                    ".pbix", ".pbit", ".twbx", ".hwpx", ".usdz", ".pages", ".numbers", ".key",
                    ".odt", ".ods", ".odp", ".odg", ".odc", ".odf", ".odb", ".odm", ".ott", ".ots", ".otp", ".otg")),
            // OLE2：Outlook 邮件、安装包、Visio/Publisher/Project、WPS/韩文 Office，以及加密后的 OOXML
            ".bin", union(OOXML_EXTENSIONS_ALL, Set.of(
                    ".msg", ".msi", ".msp", ".vsd", ".pub", ".mpp", ".wps", ".et", ".dps", ".hwp",
                    ".doc", ".dot", ".xls", ".xlt", ".xla", ".ppt", ".pps", ".pot")));

    /** 同一格式的不同写法，始终保留已有扩展名。 */
    private static final Map<String, Set<String>> SYNONYMS = Map.of(
            ".jpg", Set.of(".jpeg", ".jpe", ".jfif"),
            ".gz", Set.of(".tgz", ".gzip"));

    /**
     * Office 宏/模板变体：识别结果未确认（只按关键字认出基础格式 .xlsx、或 OLE2 的 .xls）时，
     * 保留更具体的已有扩展名（.xlsm）；已由 [Content_Types].xml 确认时以识别结果为准。
     */
    private static final Map<String, Set<String>> OFFICE_VARIANTS = Map.of(
            ".xlsx", Set.of(".xlsm", ".xltx", ".xltm", ".xlam"),
            ".docx", Set.of(".docm", ".dotx", ".dotm"),
            ".pptx", Set.of(".pptm", ".ppsx", ".ppsm", ".potx", ".potm"),
            ".xls", Set.of(".xlt", ".xla"),
            ".doc", Set.of(".dot"),
            ".ppt", Set.of(".pps", ".pot"));

    /**
     * 识别结果。
     *
     * @param extension 识别出的扩展名（含点）
     * @param exact     是否已确定到具体子类型；为 {@code false} 时可能是更具体的 Office 宏/模板变体
     */
    public record Detection(String extension, boolean exact) {
    }

    /**
     * 返回识别出的扩展名（含点），无法确定时返回 {@code null}。
     * ZIP、OLE2 无法确定具体文档类型时分别返回 {@code .zip}、{@code .bin}。
     */
    public static String detectExtension(byte[] bytes) {
        return extensionOf(detect(bytes));
    }

    /**
     * 识别内容，无法确定时返回 {@code null}，不会抛出异常。
     */
    public static Detection detect(byte[] bytes) {
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
        return extensionOf(detect(file));
    }

    /**
     * 识别已落盘的响应文件，只读取开头 {@link #HEAD_BYTES} 字节；读取失败时返回 {@code null}。
     */
    public static Detection detect(Path file) {
        if (file == null) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return detect(in.readNBytes(HEAD_BYTES));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String extensionOf(Detection detection) {
        return detection == null ? null : detection.extension();
    }

    /**
     * 调和已有扩展名（来自 Content-Type 或 URL）与文件头识别结果，返回应使用的扩展名：
     * <ul>
     *     <li>两者一致或互为同一格式的写法（.jpeg/.jpg），保留已有扩展名</li>
     *     <li>识别结果未确认子类型、已有扩展名是其宏/模板变体（识别 .xlsx、已有 .xlsm）时，保留已有扩展名</li>
     *     <li>只识别到通用容器（.zip/.bin）时，仅保留允许列表中的容器格式（.jar、.docx、.msg 等）</li>
     *     <li>其余情况以文件头为准（/photo.png 实际是 PDF → .pdf；声明 .xlsx 实际是 .xlsm → .xlsm；
     *     声明 .xlsm 而 [Content_Types].xml 确认是标准 xlsx → .xlsx）</li>
     * </ul>
     */
    public static String reconcile(String claimedExtension, Detection detection) {
        if (detection == null) {
            return claimedExtension;
        }
        String detectedExtension = detection.extension();
        if (claimedExtension == null) {
            return detectedExtension;
        }
        String claimed = claimedExtension.toLowerCase(Locale.ROOT);
        if (claimed.equals(detectedExtension)
                || SYNONYMS.getOrDefault(detectedExtension, Set.of()).contains(claimed)
                || (!detection.exact() && OFFICE_VARIANTS.getOrDefault(detectedExtension, Set.of()).contains(claimed))
                || CONTAINER_COMPATIBLE.getOrDefault(detectedExtension, Set.of()).contains(claimed)) {
            return claimedExtension;
        }
        return detectedExtension;
    }

    /**
     * 按未确认子类型的识别结果调和，见 {@link #reconcile(String, Detection)}。
     */
    public static String reconcile(String claimedExtension, String detectedExtension) {
        return reconcile(claimedExtension, detectedExtension == null ? null : new Detection(detectedExtension, false));
    }

    /**
     * 识别结果只是通用容器（.zip/.bin），无法确定具体格式。
     */
    public static boolean isGenericContainer(String extension) {
        return extension != null && CONTAINER_COMPATIBLE.containsKey(extension);
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new java.util.HashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
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

    private static Detection detectSignature(byte[] bytes) {
        String extension = signatureExtension(bytes);
        if (extension == null) {
            return null;
        }
        if (startsWith(bytes, ZIP) || startsWith(bytes, ZIP_EMPTY)) {
            return detectZip(bytes);
        }
        // OLE2 无法区分 .xls 与 .xlt 等模板
        return new Detection(extension, !startsWith(bytes, OLE2));
    }

    private static String signatureExtension(byte[] bytes) {
        if (startsWith(bytes, PDF)) {
            return ".pdf";
        }
        if (startsWith(bytes, ZIP) || startsWith(bytes, ZIP_EMPTY)) {
            return ".zip";
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

    private static Detection detectZip(byte[] bytes) {
        String odf = odfMimeType(bytes);
        if (odf != null) {
            boolean known = odf.startsWith("application/vnd.oasis.opendocument.") || "application/epub+zip".equals(odf);
            String extension = known ? FileExtensionUtil.guessExtension(odf) : null;
            return new Detection(extension != null ? extension : ".zip", true);
        }
        if (contains(bytes, OOXML_CONTENT_TYPES)) {
            String mainType = ooxmlVariant(bytes);
            return mainType != null
                    ? new Detection(mainType, true)
                    : new Detection(firstContained(bytes, OOXML_PARTS, OOXML_EXTENSIONS, ".zip"), false);
        }
        return new Detection(contains(bytes, OFD_ENTRY) ? ".ofd" : ".zip", true);
    }

    /** 第一个条目名为 mimetype 且未压缩时，返回其内容。 */
    private static String odfMimeType(byte[] bytes) {
        if (bytes.length < 38 || u16(bytes, 8) != 0 || !regionMatches(bytes, 30, ODF_MIMETYPE_ENTRY)
                || u16(bytes, 26) != ODF_MIMETYPE_ENTRY.length) {
            return null;
        }
        int start = 30 + ODF_MIMETYPE_ENTRY.length + u16(bytes, 28);
        int size = u16(bytes, 18);
        if (size <= 0 || size > 100 || u16(bytes, 20) != 0 || start + size > bytes.length) {
            return null;
        }
        return new String(bytes, start, size, StandardCharsets.US_ASCII);
    }

    /** 在开头一段内找到 [Content_Types].xml 的本地文件头并解压，按主部件内容类型确定具体格式。 */
    private static String ooxmlVariant(byte[] bytes) {
        int header = contentTypesLocalHeader(bytes);
        if (header < 0) {
            return null;
        }
        int dataStart = header + 30 + OOXML_CONTENT_TYPES.length + u16(bytes, header + 28);
        int available = Math.min(bytes.length, HEAD_BYTES) - dataStart;
        if (available <= 0) {
            return null;
        }
        String contentTypes;
        int method = u16(bytes, header + 8);
        if (method == 0) {
            contentTypes = new String(bytes, dataStart, available, StandardCharsets.UTF_8);
        } else if (method == 8) {
            Inflater inflater = new Inflater(true);
            try {
                inflater.setInput(bytes, dataStart, available);
                byte[] out = new byte[HEAD_BYTES];
                int length = inflater.inflate(out);
                contentTypes = new String(out, 0, length, StandardCharsets.UTF_8);
            } catch (DataFormatException e) {
                return null;
            } finally {
                inflater.end();
            }
        } else {
            return null;
        }
        for (Map.Entry<String, String> variant : OOXML_MAIN_TYPES.entrySet()) {
            if (contentTypes.contains(variant.getKey())) {
                return variant.getValue();
            }
        }
        return null;
    }

    /** 条目名紧跟在 30 字节的本地文件头之后；返回该文件头的偏移，找不到时返回 -1。 */
    private static int contentTypesLocalHeader(byte[] bytes) {
        int limit = Math.min(bytes.length, HEAD_BYTES) - OOXML_CONTENT_TYPES.length;
        for (int name = 30; name <= limit; name++) {
            int header = name - 30;
            if (regionMatches(bytes, name, OOXML_CONTENT_TYPES) && regionMatches(bytes, header, ZIP)
                    && u16(bytes, header + 26) == OOXML_CONTENT_TYPES.length) {
                return header;
            }
        }
        return -1;
    }

    private static int u16(byte[] bytes, int offset) {
        return offset + 1 < bytes.length ? (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8 : -1;
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
