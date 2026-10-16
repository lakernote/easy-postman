package com.laker.postman.service.collections;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            saved.setBody(response.body);
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
        return response;
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
        if (response == null || response.headers == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : response.headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Content-Type")) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty() && values.get(0) != null) {
                    return values.get(0).toLowerCase();
                }
            }
        }
        return null;
    }
}
