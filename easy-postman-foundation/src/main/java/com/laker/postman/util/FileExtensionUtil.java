package com.laker.postman.util;

import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.Map;

/**
 * 文件扩展名工具类
 * 统一管理根据 Content-Type 推断文件扩展名的逻辑
 */
@UtilityClass
public class FileExtensionUtil {

    private static final Map<String, String> EXACT_MEDIA_TYPES = Map.ofEntries(
            Map.entry("application/vnd.ms-excel.sheet.macroenabled.12", ".xlsm"),
            Map.entry("application/vnd.ms-excel.sheet.binary.macroenabled.12", ".xlsb"),
            Map.entry("application/vnd.ms-excel.template.macroenabled.12", ".xltm"),
            Map.entry("application/vnd.ms-excel.addin.macroenabled.12", ".xlam"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.template", ".xltx"),
            Map.entry("application/vnd.ms-word.document.macroenabled.12", ".docm"),
            Map.entry("application/vnd.ms-word.template.macroenabled.12", ".dotm"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.template", ".dotx"),
            Map.entry("application/vnd.ms-powerpoint.presentation.macroenabled.12", ".pptm"),
            Map.entry("application/vnd.ms-powerpoint.template.macroenabled.12", ".potm"),
            Map.entry("application/vnd.ms-powerpoint.slideshow.macroenabled.12", ".ppsm"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.template", ".potx"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.slideshow", ".ppsx"),
            Map.entry("application/vnd.oasis.opendocument.text", ".odt"),
            Map.entry("application/vnd.oasis.opendocument.text-template", ".ott"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", ".ods"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet-template", ".ots"),
            Map.entry("application/vnd.oasis.opendocument.presentation", ".odp"),
            Map.entry("application/vnd.oasis.opendocument.presentation-template", ".otp"),
            Map.entry("application/vnd.oasis.opendocument.graphics", ".odg"),
            Map.entry("application/vnd.oasis.opendocument.graphics-template", ".otg"),
            Map.entry("application/vnd.oasis.opendocument.text-master", ".odm"),
            Map.entry("application/vnd.oasis.opendocument.chart", ".odc"),
            Map.entry("application/vnd.oasis.opendocument.formula", ".odf"),
            Map.entry("application/vnd.oasis.opendocument.base", ".odb"),
            Map.entry("application/ofd", ".ofd"),
            Map.entry("application/epub+zip", ".epub"),
            Map.entry("application/java-archive", ".jar"),
            Map.entry("application/x-java-archive", ".jar"));

    /**
     * 根据 Content-Type 推断文件扩展名
     *
     * @param contentType HTTP Content-Type 头
     * @return 文件扩展名（包含点），如果无法判断则返回 null
     */
    public static String guessExtension(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }

        String ct = mediaType(contentType);

        // 精确匹配的 Office 宏/模板变体、ODF、Java 包（必须先于下面 ms-excel 等宽泛匹配）
        String exact = EXACT_MEDIA_TYPES.get(ct);
        if (exact != null) return exact;

        // 文档类型（优先判断，避免被 xml/json 等通用类型误判）
        if (ct.contains("pdf")) return ".pdf";
        if (ct.contains("msword") && !ct.contains("wordprocessingml")) return ".doc";
        if (ct.contains("wordprocessingml.document")) return ".docx";
        if (ct.contains("ms-excel") && !ct.contains("spreadsheetml")) return ".xls";
        if (ct.contains("spreadsheetml.sheet")) return ".xlsx";
        if (ct.contains("ms-powerpoint") && !ct.contains("presentationml")) return ".ppt";
        if (ct.contains("presentationml.presentation")) return ".pptx";

        // 图片类型（在文本类型之前判断，避免 svg+xml 被 xml 误判）
        if (ct.contains("image/png")) return ".png";
        if (ct.contains("image/jpeg")) return ".jpg";
        if (ct.contains("image/gif")) return ".gif";
        if (ct.contains("image/bmp")) return ".bmp";
        if (ct.contains("image/webp")) return ".webp";
        if (ct.contains("image/svg")) return ".svg";
        if (ct.contains("image/ico") || ct.contains("image/x-icon")) return ".ico";

        // 文本类型
        if (ct.contains("json")) return ".json";
        if (ct.contains("xml")) return ".xml";
        if (ct.contains("html")) return ".html";
        if (ct.contains("javascript")) return ".js";
        if (ct.contains("css")) return ".css";
        if (ct.contains("csv")) return ".csv";
        if (ct.contains("text/plain")) return ".txt";
        if (ct.contains("yaml") || ct.contains("yml")) return ".yaml";

        // 压缩文件
        if (ct.contains("application/zip") || ct.contains("x-zip")) return ".zip";
        if (ct.contains("gzip")) return ".gz";
        if (ct.contains("x-tar")) return ".tar";
        if (ct.contains("x-7z-compressed")) return ".7z";
        if (ct.contains("x-rar-compressed")) return ".rar";
        if (ct.contains("x-bzip2")) return ".bz2";
        if (ct.contains("x-xz")) return ".xz";

        // 多媒体
        if (ct.contains("audio/mpeg") || ct.contains("audio/mp3")) return ".mp3";
        if (ct.contains("audio/wav") || ct.contains("audio/x-wav")) return ".wav";
        if (ct.contains("audio/ogg")) return ".ogg";
        if (ct.contains("audio/aac")) return ".aac";
        if (ct.contains("audio/mp4") || ct.contains("audio/x-m4a")) return ".m4a";
        if (ct.contains("audio/flac") || ct.contains("audio/x-flac")) return ".flac";
        if (ct.contains("audio/aiff") || ct.contains("audio/x-aiff")) return ".aiff";
        if (ct.contains("audio/basic")) return ".au";
        if (ct.contains("audio/webm")) return ".webm";
        if (ct.contains("audio/opus")) return ".opus";
        if (ct.contains("video/mp4")) return ".mp4";
        if (ct.contains("video/mpeg")) return ".mpeg";
        if (ct.contains("video/webm")) return ".webm";
        if (ct.contains("video/x-flv")) return ".flv";

        // 其他
        if (ct.contains("apk")) return ".apk";
        if (ct.contains("x-apple-diskimage")) return ".dmg";
        if (ct.contains("x-iso9660-image")) return ".iso";
        if (ct.contains("x-msdownload")) return ".exe";
        if (ct.contains("x-debian-package")) return ".deb";
        if (ct.contains("x-redhat-package-manager")) return ".rpm";
        if (ct.contains("x-shockwave-flash")) return ".swf";

        return null;
    }

    /**
     * 根据文件扩展名生成智能文件名
     *
     * @param extension 文件扩展名（可以带点或不带点）
     * @return 智能生成的文件名（包含时间戳）
     */
    public static String generateSmartFileName(String extension) {
        if (extension == null || extension.isEmpty()) {
            extension = ".txt";
        }

        // 移除扩展名中的点（如果有）
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        String extLower = ext.toLowerCase();

        // 根据扩展名生成友好的文件名前缀
        String prefix = getFilePrefixByExtension(extLower);

        // 使用时间戳确保文件名唯一
        long timestamp = System.currentTimeMillis();
        return prefix + "_" + timestamp + "." + ext;
    }

    /**
     * 根据扩展名获取文件前缀
     */
    private static String getFilePrefixByExtension(String ext) {
        return switch (ext) {
            case "json", "csv" -> "response_data";
            case "xml" -> "response_xml";
            case "html" -> "response_page";
            case "js" -> "response_script";
            case "css" -> "response_style";
            case "txt" -> "response_text";
            case "pdf" -> "document";
            case "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> "document";
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "ico" -> "image";
            case "zip", "gz", "tar", "7z", "rar", "bz2", "xz" -> "archive";
            case "mp3", "wav", "ogg", "aac", "m4a", "flac", "aiff", "au", "opus" -> "audio";
            case "mp4", "mpeg", "webm", "flv" -> "video";
            case "apk", "dmg", "iso", "exe", "deb", "rpm" -> "package";
            default -> "response";
        };
    }

    /**
     * 从 Content-Type 中提取主类型（如 text、image、application 等）
     */
    public static String extractMainType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }

        int slashIndex = contentType.indexOf('/');
        if (slashIndex > 0) {
            return contentType.substring(0, slashIndex).trim();
        }

        return null;
    }

    /**
     * 检查是否为文本类型的 Content-Type
     */
    public static boolean isTextType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return false;
        }

        String ct = mediaType(contentType);

        // 明确的文本类型
        if (ct.startsWith("text/")) {
            return true;
        }

        // application 下的文本类型
        // 注意：使用精确匹配避免误判
        // - "application/xml" 或 "text/xml" 是文本
        // - 但 "image/svg+xml" 或 "application/vnd...openxmlformats..." 不是文本
        boolean isXml = ct.equals("application/xml") || ct.equals("text/xml");

        return ct.contains("json")
                || isXml
                || ct.contains("javascript")
                || ct.contains("x-www-form-urlencoded")
                || ct.contains("x-sh")
                || ct.contains("x-shellscript")
                || ct.contains("x-python")
                || (ct.contains("x-java") && !ct.contains("archive"));
    }

    /**
     * 检查是否为二进制类型的 Content-Type
     */
    public static boolean isBinaryType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return false;
        }

        String ct = mediaType(contentType);
        if (ct.isEmpty()) {
            return false;
        }

        // 优先识别结构化文本类型，例如 application/vnd.*+json。
        if (isTextType(contentType)) {
            return false;
        }

        // 检查明确的二进制类型。
        // 例如：application/vnd.openxmlformats-officedocument.spreadsheetml.sheet 包含 "xml" 但是二进制文件
        if (ct.contains("application/octet-stream")
                || ct.contains("image/")
                || ct.contains("audio/")
                || ct.contains("video/")
                || ct.contains("application/pdf")
                || ct.contains("application/zip")
                || ct.contains("application/gzip")
                || ct.contains("application/msword")
                || ct.contains("application/vnd.")) {
            return true;
        }

        return true;
    }

    /**
     * 取 Content-Type 的媒体类型部分（小写、去掉参数），避免 {@code ; name=data.json} 之类的参数影响判断。
     */
    private static String mediaType(String contentType) {
        int semicolon = contentType.indexOf(';');
        return (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType).trim().toLowerCase(Locale.ROOT);
    }
}
