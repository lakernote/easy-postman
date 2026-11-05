package com.laker.postman.http.runtime.file;

import com.laker.postman.util.FileExtensionUtil;
import lombok.experimental.UtilityClass;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 响应下载文件名解析：Content-Disposition、URL 路径与内容识别结果之间的取舍规则集中在这里。
 */
@UtilityClass
public class ResponseFileNames {

    public static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";

    /**
     * 从响应头中提取 Content-Disposition（大小写不敏感），不存在时返回 {@code null}。
     */
    public static String getContentDisposition(Map<String, List<String>> headers) {
        return ResponseFiles.firstHeader(headers, CONTENT_DISPOSITION_HEADER);
    }

    /**
     * Content-Disposition 为 attachment 时，服务端明确希望以文件方式下载。
     * <p>
     * {@code inline; filename=...} 不算：例如 Spring MVC 会给 {@code /list.do} 的 JSON 响应加上
     * {@code inline;filename=f.txt} 以防 RFD 攻击，这类响应仍应按普通文本展示。
     * </p>
     */
    public static boolean isAttachment(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return false;
        }
        String type = contentDisposition.split(";", 2)[0].trim();
        return "attachment".equalsIgnoreCase(type);
    }

    /**
     * 按 RFC 6266 从 Content-Disposition 中解析文件名，结果已去掉路径并替换掉文件系统不允许的字符。
     * <ol>
     *     <li>{@code filename*}（RFC 5987，如 {@code UTF-8''%E6%8A%A5%E8%A1%A8.xlsx}）优先，按声明的字符集解码</li>
     *     <li>否则使用 {@code filename}；兼容浏览器的常见做法，解码其中合法的 UTF-8 百分号编码
     *     （很多服务端用 {@code URLEncoder} 编码中文文件名）和 RFC 2047 编码字（{@code =?UTF-8?B?...?=}）</li>
     * </ol>
     *
     * @return 解析出的文件名，无法解析时返回 {@code null}
     */
    public static String parseFileNameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return null;
        }
        Map<String, String> parameters = dispositionParameters(contentDisposition);
        String extended = decodeExtendedValue(parameters.get("filename*"));
        String fileName = sanitize(extended);
        if (fileName == null) {
            String plain = parameters.get("filename");
            fileName = plain == null ? null : sanitize(decodeEncodedWords(percentDecodeUtf8(plain)));
        }
        return fileName;
    }

    /**
     * 把服务端或 URL 给出的名字变成可以安全用作默认保存名的文件名：
     * 去掉目录部分（RFC 6266 要求），替换控制字符和 Windows 不允许的字符，去掉首尾空白和末尾的点。
     *
     * @return 处理后的文件名；没有可用字符时返回 {@code null}
     */
    public static String sanitize(String name) {
        if (name == null) {
            return null;
        }
        String baseName = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        StringBuilder result = new StringBuilder(baseName.length());
        for (int i = 0; i < baseName.length(); i++) {
            char c = baseName.charAt(i);
            result.append(c < 0x20 || c == 0x7F || INVALID_FILE_NAME_CHARS.indexOf(c) >= 0 ? '_' : c);
        }
        String cleaned = result.toString().strip();
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).strip();
        }
        if (cleaned.isEmpty() || cleaned.chars().allMatch(c -> c == '_')) {
            return null;
        }
        String stem = cleaned.contains(".") ? cleaned.substring(0, cleaned.indexOf('.')) : cleaned;
        if (WINDOWS_RESERVED_NAMES.contains(stem.toUpperCase(Locale.ROOT))) {
            cleaned = "_" + cleaned;
        }
        return cleaned.length() > MAX_FILE_NAME_LENGTH ? truncateKeepingExtension(cleaned) : cleaned;
    }

    private static final String INVALID_FILE_NAME_CHARS = "<>:\"|?*";
    private static final int MAX_FILE_NAME_LENGTH = 200;
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private static String truncateKeepingExtension(String name) {
        String extension = extensionOf(name);
        if (extension == null || extension.length() > 16) {
            return name.substring(0, MAX_FILE_NAME_LENGTH);
        }
        return name.substring(0, MAX_FILE_NAME_LENGTH - extension.length()) + extension;
    }

    /**
     * 解析 disposition 参数（名称小写），支持带引号和反斜杠转义的值；同名参数以第一个为准。
     */
    private static Map<String, String> dispositionParameters(String contentDisposition) {
        Map<String, String> parameters = new HashMap<>();
        int length = contentDisposition.length();
        int i = contentDisposition.indexOf(';');
        String dispositionType = contentDisposition.substring(0, i < 0 ? length : i);
        if (dispositionType.contains("=")) {
            // 不规范地省略了 disposition 类型，例如 "filename=report.xlsx"
            i = -1;
        } else if (i < 0) {
            return parameters;
        }
        do {
            i++;
            int nameStart = i;
            while (i < length && contentDisposition.charAt(i) != '=' && contentDisposition.charAt(i) != ';') {
                i++;
            }
            String name = contentDisposition.substring(nameStart, i).trim().toLowerCase(Locale.ROOT);
            if (i >= length || contentDisposition.charAt(i) == ';') {
                continue;
            }
            i++;
            while (i < length && Character.isWhitespace(contentDisposition.charAt(i))) {
                i++;
            }
            String value;
            if (i < length && contentDisposition.charAt(i) == '"') {
                StringBuilder quoted = new StringBuilder();
                i++;
                while (i < length && contentDisposition.charAt(i) != '"') {
                    char c = contentDisposition.charAt(i);
                    if (c == '\\' && i + 1 < length) {
                        c = contentDisposition.charAt(++i);
                    }
                    quoted.append(c);
                    i++;
                }
                value = quoted.toString();
                i = contentDisposition.indexOf(';', i);
            } else {
                int end = contentDisposition.indexOf(';', i);
                value = contentDisposition.substring(i, end < 0 ? length : end).trim();
                i = end;
            }
            if (!name.isEmpty()) {
                parameters.putIfAbsent(name, value);
            }
        } while (i >= 0 && i < length);
        return parameters;
    }

    /**
     * 解码 RFC 5987 扩展值 {@code charset'language'percent-encoded}，格式或字符集无效时返回 {@code null}。
     */
    private static String decodeExtendedValue(String value) {
        if (value == null) {
            return null;
        }
        int firstQuote = value.indexOf('\'');
        int secondQuote = firstQuote < 0 ? -1 : value.indexOf('\'', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        try {
            Charset charset = Charset.forName(value.substring(0, firstQuote).trim());
            byte[] bytes = percentDecode(value.substring(secondQuote + 1));
            return bytes == null ? null : strictDecode(bytes, charset);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 只有存在百分号编码、且解码结果是合法 UTF-8 时才解码，避免把普通文件名里的 % 弄坏。
     */
    private static String percentDecodeUtf8(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        byte[] bytes = percentDecode(value);
        String decoded = bytes == null ? null : strictDecode(bytes, StandardCharsets.UTF_8);
        return decoded == null ? value : decoded;
    }

    private static byte[] percentDecode(String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length()) {
                    return null;
                }
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    return null;
                }
                out.write(high << 4 | low);
                i += 2;
            } else {
                out.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
            }
        }
        return out.toByteArray();
    }

    private static final Pattern ENCODED_WORD =
            Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=");

    /**
     * 解码 RFC 2047 编码字（部分服务端沿用邮件的写法，如 {@code =?UTF-8?B?5oql6KGoLnhsc3g=?=}）。
     */
    private static String decodeEncodedWords(String value) {
        if (!value.contains("=?")) {
            return value;
        }
        Matcher matcher = ENCODED_WORD.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String decoded;
            try {
                Charset charset = Charset.forName(matcher.group(1));
                byte[] bytes = "B".equalsIgnoreCase(matcher.group(2))
                        ? Base64.getDecoder().decode(matcher.group(3))
                        : percentDecode(matcher.group(3).replace('_', ' ').replace('=', '%'));
                decoded = bytes == null ? null : strictDecode(bytes, charset);
            } catch (IllegalArgumentException e) {
                decoded = null;
            }
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(decoded == null ? matcher.group() : decoded));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String strictDecode(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * 解析下载文件名，内容无法通过文件头识别时使用。
     *
     * @see #resolve(String, String, String, String)
     */
    public static String resolve(String contentDisposition, String urlEncodedPath, String extension) {
        return resolve(contentDisposition, urlEncodedPath, extension, (ResponseFileTypeDetector.Detection) null);
    }

    /**
     * 解析下载文件名。
     * <ol>
     *     <li>Content-Disposition 携带文件名时优先使用，缺少扩展名时补上 {@code extension}</li>
     *     <li>否则使用 URL 最后一段：没有扩展名时补上 {@code extension}；
     *     接口路由或文本类后缀（/export.do、/download.json）替换为 {@code extension}；
     *     其余后缀与文件头识别结果冲突时按 {@link ResponseFileTypeDetector#reconcile} 纠正（/photo.png 实际是 PDF → photo.pdf）</li>
     *     <li>都没有时生成带时间戳的智能文件名</li>
     * </ol>
     *
     * @param contentDisposition Content-Disposition 响应头，可为空
     * @param urlEncodedPath     请求 URL 的 path，可为空
     * @param extension          内容对应的扩展名（含点），可为空
     * @param detectedExtension  文件头识别出的扩展名（含点），可为空
     */
    public static String resolve(String contentDisposition,
                                 String urlEncodedPath,
                                 String extension,
                                 String detectedExtension) {
        return resolve(contentDisposition, urlEncodedPath, extension,
                detectedExtension == null ? null : new ResponseFileTypeDetector.Detection(detectedExtension, false));
    }

    /**
     * 同 {@link #resolve(String, String, String, String)}，使用带确认信息的识别结果调和 URL 后缀。
     */
    public static String resolve(String contentDisposition,
                                 String urlEncodedPath,
                                 String extension,
                                 ResponseFileTypeDetector.Detection detection) {
        String detectedExtension = detection == null ? null : detection.extension();
        String target = extension != null ? extension : detectedExtension;
        String fileName = parseFileNameFromContentDisposition(contentDisposition);
        if (fileName != null && !fileName.isBlank()) {
            return fileName.contains(".") || target == null ? fileName : fileName + target;
        }

        fileName = fileNameFromUrlPath(urlEncodedPath);
        fileName = fileName == null ? null : sanitize(percentDecodeUtf8(fileName));
        if (fileName == null) {
            return FileExtensionUtil.generateSmartFileName(target);
        }
        String urlExtension = extensionOf(fileName);
        if (target == null) {
            return fileName;
        }
        if (urlExtension == null) {
            return fileName + target;
        }
        String baseName = fileName.substring(0, fileName.length() - urlExtension.length());
        if (REPLACEABLE_URL_EXTENSIONS.contains(urlExtension.toLowerCase(Locale.ROOT))) {
            return target.equalsIgnoreCase(urlExtension) ? fileName : baseName + target;
        }
        String reconciled = ResponseFileTypeDetector.reconcile(urlExtension, detection);
        return reconciled.equals(urlExtension) ? fileName : baseName + reconciled;
    }

    /**
     * 取 URL path 的最后一段作为文件名，去掉 {@code ! ? #} 之后的部分；path 以 / 结尾时返回 {@code null}。
     */
    public static String fileNameFromUrlPath(String path) {
        if (path == null) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        String rawName = lastSlash < 0 ? path : path.substring(lastSlash + 1);
        if (rawName.isEmpty()) {
            return null;
        }
        int cut = rawName.length();
        for (char delimiter : new char[]{'!', '?', '#'}) {
            int index = rawName.indexOf(delimiter);
            if (index >= 0 && index < cut) {
                cut = index;
            }
        }
        return rawName.substring(0, cut);
    }

    /**
     * 返回文件名中的扩展名（含点），没有扩展名时返回 {@code null}。
     */
    public static String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot) : null;
    }

    /** 接口路由后缀和文本类后缀：不代表下载文件的真实类型，可以按内容替换。 */
    private static final Set<String> REPLACEABLE_URL_EXTENSIONS = Set.of(
            ".do", ".action", ".php", ".jsp", ".jspx", ".asp", ".aspx", ".ashx", ".cgi", ".pl", ".py",
            ".rb", ".cfm", ".json", ".txt", ".html", ".htm", ".xhtml", ".xml", ".csv", ".js");

}
