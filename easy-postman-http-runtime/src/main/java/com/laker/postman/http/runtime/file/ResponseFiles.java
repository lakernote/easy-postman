package com.laker.postman.http.runtime.file;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.util.FileExtensionUtil;
import lombok.experimental.UtilityClass;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 响应“是否为文件”的统一判定，供响应面板、示例保存、历史记录、渲染等共用，避免各处规则不一致。
 */
@UtilityClass
public class ResponseFiles {

    public static final String CONTENT_TYPE_HEADER = "Content-Type";

    /**
     * 大小写不敏感地读取第一个响应头值，不存在时返回 {@code null}。
     */
    public static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
        }
        return null;
    }

    /**
     * 响应的 Content-Type，不存在时返回空字符串。
     */
    public static String contentType(HttpResponse response) {
        String contentType = response == null ? null : firstHeader(response.headers, CONTENT_TYPE_HEADER);
        return contentType == null ? "" : contentType;
    }

    /**
     * 响应体按文件处理（而不是文本）：已识别出文件格式、文件内容已省略，或 Content-Type 声明为二进制且确实收到了内容。
     * 这类响应的 {@link HttpResponse#body} 不是可读文本，持久化时只应保留文件元信息。
     * 二进制下载失败（超限、连接中断、无响应体）时 body 是错误提示，按文本保留，便于在历史中排查。
     */
    public static boolean isFileResponse(HttpResponse response) {
        return response != null
                && (response.isRecognizedFile()
                || (FileExtensionUtil.isBinaryType(contentType(response)) && hasFileContent(response)));
    }

    private static boolean hasFileContent(HttpResponse response) {
        return response.bodySize > 0
                || response.rawBodyBytes != null
                || (response.filePath != null && !response.filePath.isBlank());
    }

    /**
     * 只表示“某种二进制数据”、不指明具体格式的 Content-Type。
     */
    public static boolean isGenericBinaryContentType(String contentType) {
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return GENERIC_BINARY_MEDIA_TYPES.contains(mediaType);
    }

    private static final Set<String> GENERIC_BINARY_MEDIA_TYPES = Set.of(
            "application/octet-stream", "binary/octet-stream", "application/binary", "application/unknown",
            "application/download", "application/force-download", "application/x-download");

    /**
     * 文本附件（如 CSV、GBK 编码的 TXT）：响应体是可读文本，但服务端希望以文件方式下载。
     */
    public static boolean isTextAttachment(HttpResponse response) {
        return response != null && response.fileAttachment && !response.isRecognizedFile()
                && !FileExtensionUtil.isBinaryType(contentType(response));
    }

    /**
     * Content-Type 声明的字符集，未声明或无法识别时为 UTF-8（与解码响应体时的默认值一致）。
     */
    public static Charset declaredCharset(HttpResponse response) {
        String contentType = contentType(response);
        for (String parameter : contentType.split(";")) {
            int eq = parameter.indexOf('=');
            if (eq > 0 && "charset".equalsIgnoreCase(parameter.substring(0, eq).trim())) {
                String name = parameter.substring(eq + 1).trim().replace("\"", "");
                try {
                    return Charset.forName(name);
                } catch (IllegalArgumentException e) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * 按声明的字符集把响应体文本还原为字节。
     */
    public static byte[] encodeBody(HttpResponse response) {
        String body = response == null || response.body == null ? "" : response.body;
        return body.getBytes(declaredCharset(response));
    }

    /**
     * 响应体文本能否按声明的字符集无损还原出原始字节，用于判断只保存文本的示例能否恢复出完整文件。
     * 内容落在临时文件里时 {@link HttpResponse#body} 只是提示文字，不能用来还原。
     */
    public static boolean canRestoreBytesFromBody(HttpResponse response) {
        if (response == null || response.body == null
                || (response.filePath != null && !response.filePath.isBlank())) {
            return false;
        }
        // 没有保留原始字节说明正文是无损解码的 UTF-8 文本
        return response.rawBodyBytes == null || Arrays.equals(encodeBody(response), response.rawBodyBytes);
    }

    /**
     * 兼容旧数据：二进制文件曾被按 UTF-8 解码成文本保存，ASCII 部分（ZIP 条目名、%PDF- 头等）仍可识别。
     *
     * @return 识别出的扩展名（含点），无法识别时返回 {@code null}
     */
    public static String detectFromLegacyText(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        return ResponseFileTypeDetector.detectExtension(body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将响应标记为“只有文件信息、没有文件内容”，用于从示例或历史恢复。
     */
    public static void markContentOmitted(HttpResponse response, String extension, String fileName) {
        response.body = "";
        response.rawBodyBytes = null;
        response.fileContentOmitted = true;
        response.detectedFileExtension = blankToNull(extension);
        if (fileName != null) {
            response.fileName = blankToNull(fileName);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
