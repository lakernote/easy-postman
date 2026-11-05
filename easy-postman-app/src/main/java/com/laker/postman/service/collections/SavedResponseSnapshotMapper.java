package com.laker.postman.service.collections;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.file.ResponseFileNames;
import com.laker.postman.http.runtime.file.ResponseFiles;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.util.FileExtensionUtil;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@UtilityClass
public class SavedResponseSnapshotMapper {
    private static final int MAX_ORIGINAL_REQUEST_BODY_PREVIEW_CHARS = 64 * 1024;

    public SavedResponse fromExchange(String name, PreparedRequest request, HttpResponse response) {
        SavedResponse saved = new SavedResponse();
        saved.setId(UUID.randomUUID().toString());
        saved.setName(name);
        saved.setTimestamp(System.currentTimeMillis());
        saved.setOriginalRequest(toOriginalRequest(request));

        if (response != null) {
            saved.setCode(response.code);
            saved.setHeaders(toSavedHeaders(response));
            if (ResponseFiles.isFileResponse(response)) {
                // 示例只保存文件元信息：解码后的文本无法还原二进制内容，保存下来只会是乱码
                saved.setFile(toFileInfo(response, false));
                saved.setBody("");
            } else if (ResponseFiles.isTextAttachment(response)) {
                // 文本附件（如 CSV）保留文本；只有能按声明字符集无损还原原始字节时才保存内容
                boolean restorable = ResponseFiles.canRestoreBytesFromBody(response);
                saved.setFile(toFileInfo(response, restorable));
                saved.setBody(restorable ? response.body : "");
            } else {
                saved.setBody(response.body);
            }
            saved.setCostMs(response.costMs);
            saved.setBodySize(response.bodySize);
            saved.setHeadersSize(response.headersSize);
            saved.setPreviewLanguage(detectPreviewLanguage(response));
        }
        return saved;
    }

    public HttpResponse toRuntimeResponse(SavedResponse savedResponse) {
        HttpResponse response = new HttpResponse();
        if (savedResponse == null) {
            response.headers = new LinkedHashMap<>();
            return response;
        }
        response.code = savedResponse.getCode();
        response.body = savedResponse.getBody();
        response.headers = new LinkedHashMap<>();
        List<HttpHeader> headers = savedResponse.getHeaders();
        if (headers != null) {
            for (HttpHeader header : headers) {
                if (header == null || header.getKey() == null) {
                    continue;
                }
                response.headers.put(header.getKey(), List.of(header.getValue()));
            }
        }
        response.costMs = savedResponse.getCostMs();
        response.bodySize = savedResponse.getBodySize();
        response.headersSize = savedResponse.getHeadersSize();
        applyFileInfo(savedResponse, response);
        return response;
    }

    private SavedResponse.FileInfo toFileInfo(HttpResponse response, boolean contentInBody) {
        SavedResponse.FileInfo fileInfo = new SavedResponse.FileInfo();
        fileInfo.setFileName(response.fileName);
        fileInfo.setExtension(response.detectedFileExtension);
        fileInfo.setAttachment(response.fileAttachment);
        fileInfo.setContentInBody(contentInBody);
        return fileInfo;
    }

    private void applyFileInfo(SavedResponse savedResponse, HttpResponse response) {
        SavedResponse.FileInfo fileInfo = savedResponse.getFile();
        if (fileInfo != null) {
            if (fileInfo.isContentInBody()) {
                response.fileName = fileInfo.getFileName();
                response.rawBodyBytes = ResponseFiles.encodeBody(response);
            } else {
                ResponseFiles.markContentOmitted(response, fileInfo.getExtension(), fileInfo.getFileName());
            }
            response.fileAttachment = fileInfo.isAttachment();
            return;
        }
        // 兼容旧版本保存的示例：文件内容被当作文本保存成了乱码，按文件头识别后改为展示文件概览
        String legacyExtension = ResponseFiles.detectFromLegacyText(savedResponse.getBody());
        if (legacyExtension != null) {
            ResponseFiles.markContentOmitted(response, legacyExtension, null);
            return;
        }
        if (isLegacyBinaryPlaceholder(savedResponse)) {
            ResponseFiles.markContentOmitted(response, null, null);
            return;
        }
        restoreLegacyTextAttachment(savedResponse, response);
    }

    /**
     * 兼容旧版本保存的二进制示例：当时保存的是“已保存为临时文件”之类的提示文字，而不是文件内容。
     * 依据是声明为二进制、记录了响应大小，但正文字节数与之不符；手写或编辑过的正文会同步更新大小，不受影响。
     */
    public boolean isLegacyBinaryPlaceholder(SavedResponse savedResponse) {
        if (savedResponse == null || savedResponse.getFile() != null || savedResponse.getBodySize() <= 0) {
            return false;
        }
        String contentType = null;
        if (savedResponse.getHeaders() != null) {
            for (HttpHeader header : savedResponse.getHeaders()) {
                if (header != null && "Content-Type".equalsIgnoreCase(header.getKey())) {
                    contentType = header.getValue();
                    break;
                }
            }
        }
        if (!FileExtensionUtil.isBinaryType(contentType)) {
            return false;
        }
        String body = savedResponse.getBody() == null ? "" : savedResponse.getBody();
        return body.getBytes(StandardCharsets.UTF_8).length != savedResponse.getBodySize();
    }

    /**
     * 兼容旧版本保存的文本附件（如 CSV）：当时没有文件信息，但响应头和正文都在，可恢复附件标记、文件名和原始字节。
     * 正文含替换字符说明保存时已经有损，不再还原字节。
     */
    private void restoreLegacyTextAttachment(SavedResponse savedResponse, HttpResponse response) {
        String contentDisposition = ResponseFileNames.getContentDisposition(response.headers);
        String contentType = ResponseFiles.contentType(response);
        if (!ResponseFileNames.isAttachment(contentDisposition) || FileExtensionUtil.isBinaryType(contentType)) {
            return;
        }
        response.fileAttachment = true;
        String extension = FileExtensionUtil.guessExtension(contentType);
        SavedResponse.OriginalRequest original = savedResponse.getOriginalRequest();
        response.fileName = ResponseFileNames.resolve(contentDisposition,
                urlPath(original == null ? null : original.getUrl()),
                extension != null ? extension : ".txt");
        if (response.body != null && response.body.indexOf('\uFFFD') < 0) {
            response.rawBodyBytes = ResponseFiles.encodeBody(response);
        }
    }

    private String urlPath(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String path = url.trim();
        int end = path.length();
        for (char delimiter : new char[]{'?', '#'}) {
            int index = path.indexOf(delimiter);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        path = path.substring(0, end);
        int scheme = path.indexOf("://");
        if (scheme >= 0) {
            int slash = path.indexOf('/', scheme + 3);
            path = slash >= 0 ? path.substring(slash) : "";
        }
        return path;
    }

    public void sanitizeSavedResponses(HttpRequestItem item) {
        if (item == null || item.getResponse() == null) {
            return;
        }
        for (SavedResponse savedResponse : item.getResponse()) {
            sanitizeSavedResponse(savedResponse);
        }
    }

    public SavedResponse.OriginalRequest toOriginalRequest(HttpRequestItem item) {
        SavedResponse.OriginalRequest original = new SavedResponse.OriginalRequest();
        if (item == null) return original;
        original.setMethod(item.getMethod());
        original.setUrl(item.getUrl());
        original.setHeaders(item.getHeadersList() != null ? new ArrayList<>(item.getHeadersList()) : new ArrayList<>());
        original.setPathVariables(item.getPathVariablesList() != null ? new ArrayList<>(item.getPathVariablesList()) : new ArrayList<>());
        original.setParams(item.getParamsList() != null ? new ArrayList<>(item.getParamsList()) : new ArrayList<>());
        original.setBodyType(item.getBodyType());
        original.setBody(item.getBody());
        original.setFormDataList(item.getFormDataList() != null ? new ArrayList<>(item.getFormDataList()) : new ArrayList<>());
        original.setUrlencodedList(item.getUrlencodedList() != null ? new ArrayList<>(item.getUrlencodedList()) : new ArrayList<>());
        sanitizeOriginalRequest(original);
        return original;
    }

    public void sanitizeSavedResponse(SavedResponse savedResponse) {
        if (savedResponse == null) {
            return;
        }
        sanitizeOriginalRequest(savedResponse.getOriginalRequest());
    }

    public void sanitizeOriginalRequest(SavedResponse.OriginalRequest originalRequest) {
        if (originalRequest == null || originalRequest.getBody() == null) {
            return;
        }

        String body = originalRequest.getBody();
        long bodySize = body.getBytes(StandardCharsets.UTF_8).length;
        if (body.length() <= MAX_ORIGINAL_REQUEST_BODY_PREVIEW_CHARS) {
            if (!originalRequest.isBodyTruncated()) {
                originalRequest.setOriginalBodySize(bodySize);
            }
            return;
        }

        long originalSize = Math.max(originalRequest.getOriginalBodySize(), bodySize);
        originalRequest.setBody(body.substring(0, MAX_ORIGINAL_REQUEST_BODY_PREVIEW_CHARS));
        originalRequest.setBodyTruncated(true);
        originalRequest.setOriginalBodySize(originalSize);
    }

    private SavedResponse.OriginalRequest toOriginalRequest(PreparedRequest request) {
        SavedResponse.OriginalRequest originalRequest = new SavedResponse.OriginalRequest();
        if (request == null) {
            return originalRequest;
        }
        originalRequest.setMethod(request.method);
        originalRequest.setUrl(request.url);
        originalRequest.setHeaders(request.headersList != null ? new ArrayList<>(request.headersList) : new ArrayList<>());
        originalRequest.setPathVariables(request.pathVariablesList != null ? new ArrayList<>(request.pathVariablesList) : new ArrayList<>());
        originalRequest.setParams(request.paramsList != null ? new ArrayList<>(request.paramsList) : new ArrayList<>());
        originalRequest.setBodyType(request.bodyType);
        originalRequest.setBody(request.body);
        originalRequest.setFormDataList(request.formDataList != null ? new ArrayList<>(request.formDataList) : new ArrayList<>());
        originalRequest.setUrlencodedList(request.urlencodedList != null ? new ArrayList<>(request.urlencodedList) : new ArrayList<>());
        sanitizeOriginalRequest(originalRequest);
        return originalRequest;
    }

    private List<HttpHeader> toSavedHeaders(HttpResponse response) {
        List<HttpHeader> headers = new ArrayList<>();
        if (response.headers == null) {
            return headers;
        }
        for (Map.Entry<String, List<String>> entry : response.headers.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                headers.add(new HttpHeader(true, key, String.join(", ", values)));
            }
        }
        return headers;
    }

    private String detectPreviewLanguage(HttpResponse response) {
        String contentType = findContentType(response);
        if (contentType != null) {
            if (contentType.contains("json")) {
                return "json";
            }
            if (contentType.contains("xml")) {
                return "xml";
            }
            if (contentType.contains("html")) {
                return "html";
            }
            if (contentType.contains("javascript")) {
                return "javascript";
            }
            if (contentType.contains("css")) {
                return "css";
            }
            if (contentType.contains("text")) {
                return "text";
            }
        }

        String body = response.body;
        if (body != null && !body.isEmpty()) {
            String trimmed = body.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                return "json";
            }
            if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
                String lower = trimmed.toLowerCase();
                if (lower.contains("<html")) {
                    return "html";
                }
                if (lower.contains("<?xml")) {
                    return "xml";
                }
            }
        }

        return "text";
    }

    private String findContentType(HttpResponse response) {
        String contentType = ResponseFiles.firstHeader(response == null ? null : response.headers,
                ResponseFiles.CONTENT_TYPE_HEADER);
        return contentType == null ? null : contentType.toLowerCase(Locale.ROOT);
    }
}
