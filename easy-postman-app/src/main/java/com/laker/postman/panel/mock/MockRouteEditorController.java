package com.laker.postman.panel.mock;

import com.laker.postman.http.runtime.file.ResponseFileNames;
import com.laker.postman.http.runtime.file.ResponseFiles;
import com.laker.postman.mock.app.MockCollectionRouteProvider;
import com.laker.postman.mock.app.MockRouteEntry;
import com.laker.postman.mock.app.MockServerManager;
import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.panel.collections.editor.CollectionTreeEditorGateway;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.util.JsonUtil;
import com.laker.postman.util.FileExtensionUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.RequiredArgsConstructor;

import java.awt.Component;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
final class MockRouteEditorController {
    private final MockCollectionRouteProvider routeProvider;
    private final MockServerManager serverManager;
    private final CollectionTreeEditorGateway collectionGateway = new CollectionTreeEditorGateway();

    boolean createRoute(Component owner, MockServerDefinition server) {
        MockRouteEditorDialog.Draft source = new MockRouteEditorDialog.Draft(
                "", I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_DEFAULT_NAME),
                "GET", "/api/example",
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_DEFAULT_RESPONSE_NAME), 200, 0,
                java.util.List.of(), "{\n    \"ok\": true\n}", ""
        );
        MockRouteEditorDialog.Draft draft = MockRouteEditorDialog.showDialog(owner, source, true);
        if (draft == null) return false;
        serverManager.addStandaloneRoute(server.getId(), standaloneRoute(null, draft));
        return true;
    }

    boolean addResponse(Component owner, MockServerDefinition server, MockRouteEntry entry) {
        if (entry.standalone()) {
            return editResponse(owner, server, entry);
        }
        HttpRequestItem request = requireRequest(entry);
        MockRouteEditorDialog.Draft source = draft(request, null, entry);
        MockRouteEditorDialog.Draft edited = MockRouteEditorDialog.showDialog(owner, source, false);
        if (edited == null) return false;
        SavedResponse response = buildResponse(request, null, edited, server.baseUrl());
        if (!collectionGateway.saveResponseForRequest(request, response)) {
            throw new IllegalStateException(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_PERSIST_FAILED));
        }
        return true;
    }

    boolean editResponse(Component owner, MockServerDefinition server, MockRouteEntry entry) {
        if (entry.standalone()) {
            MockRoute existing = serverManager.findStandaloneRoute(server.getId(), entry.routeId())
                    .orElseThrow(() -> new IllegalStateException(I18nUtil.getMessage(
                            MessageKeys.MOCK_SERVER_RESPONSE_NOT_FOUND)));
            MockRouteEditorDialog.Draft edited = MockRouteEditorDialog.showDialog(
                    owner, standaloneDraft(existing), false);
            if (edited == null) return false;
            serverManager.updateStandaloneRoute(server.getId(), standaloneRoute(existing, edited));
            return true;
        }
        HttpRequestItem request = requireRequest(entry);
        SavedResponse existing = routeProvider.findExample(
                        entry.sourceCollectionId(), entry.requestId(), entry.exampleId())
                .orElseThrow(() -> new IllegalStateException(I18nUtil.getMessage(
                        MessageKeys.MOCK_SERVER_RESPONSE_NOT_FOUND)));
        MockRouteEditorDialog.Draft edited = MockRouteEditorDialog.showDialog(
                owner, draft(request, existing, entry), false);
        if (edited == null) return false;
        SavedResponse response = buildResponse(request, existing, edited, server.baseUrl());
        if (!collectionGateway.upsertResponseForRequest(request.getId(), response)) {
            throw new IllegalStateException(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_PERSIST_FAILED));
        }
        return true;
    }

    boolean deleteResponse(MockServerDefinition server, MockRouteEntry entry) {
        if (entry == null || !entry.configured()) return false;
        if (entry.standalone()) {
            return serverManager.removeStandaloneRoute(server.getId(), entry.routeId());
        }
        return collectionGateway.deleteResponseForRequest(entry.requestId(), entry.exampleId());
    }

    private HttpRequestItem requireRequest(MockRouteEntry entry) {
        return routeProvider.findRequest(entry.sourceCollectionId(), entry.requestId())
                .orElseThrow(() -> new IllegalStateException(I18nUtil.getMessage(
                        MessageKeys.MOCK_SERVER_REQUEST_NOT_FOUND)));
    }

    private MockRouteEditorDialog.Draft standaloneDraft(MockRoute route) {
        List<HttpHeader> headers = route.response().getHeaders().entrySet().stream()
                .map(entry -> new HttpHeader(true, entry.getKey(), entry.getValue()))
                .toList();
        return new MockRouteEditorDialog.Draft(
                route.exampleId(), route.requestName(), route.method(), route.pathPattern(),
                route.exampleName(), route.response().getStatusCode(), route.response().getDelayMs(),
                headers, route.response().getBody(), route.script()
        );
    }

    private MockRoute standaloneRoute(MockRoute existing, MockRouteEditorDialog.Draft draft) {
        String routeId = existing == null ? UUID.randomUUID().toString() : existing.routeId();
        String requestId = existing == null ? UUID.randomUUID().toString() : existing.requestId();
        String exampleId = existing == null ? UUID.randomUUID().toString() : existing.exampleId();
        Map<String, String> headers = new LinkedHashMap<>();
        for (HttpHeader header : draft.headers()) {
            if (header != null && header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
                headers.put(header.getKey(), header.getValue() == null ? "" : header.getValue());
            }
        }
        MockResponse response = new MockResponse(draft.statusCode(), headers, draft.body());
        response.setDelayMs(draft.delayMs());
        return new MockRoute(
                routeId,
                requestId,
                draft.requestName(),
                exampleId,
                draft.responseName(),
                draft.method().toUpperCase(Locale.ROOT),
                normalizePath(draft.path()),
                Map.of(),
                Map.of(),
                "",
                response,
                draft.script()
        );
    }

    private MockRouteEditorDialog.Draft draft(HttpRequestItem request,
                                              SavedResponse response,
                                              MockRouteEntry entry) {
        return new MockRouteEditorDialog.Draft(
                response == null ? "" : response.getId(),
                request.getName(), entry.method(), entry.path(),
                response == null
                        ? I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_DEFAULT_RESPONSE_NAME)
                        : response.getName(),
                // Mock Server 无法以 1xx 作为最终响应，编辑器只允许 200–599
                response == null || response.getCode() < 200 || response.getCode() > 599 ? 200 : response.getCode(),
                response == null ? 0 : response.getMockDelayMs(),
                response == null ? java.util.List.of() : response.getHeaders(),
                response == null ? "{\n    \"ok\": true\n}" : response.getBody(),
                response == null ? "" : response.getMockScript()
        );
    }

    private SavedResponse buildResponse(HttpRequestItem request,
                                        SavedResponse existing,
                                        MockRouteEditorDialog.Draft draft,
                                        String fallbackBaseUrl) {
        SavedResponse response = new SavedResponse();
        response.setId(existing == null ? UUID.randomUUID().toString() : existing.getId());
        response.setTimestamp(existing == null ? System.currentTimeMillis() : existing.getTimestamp());
        response.setName(draft.responseName());
        response.setCode(draft.statusCode());
        response.setStatus(String.valueOf(draft.statusCode()));
        response.setHeaders(new ArrayList<>(draft.headers()));
        response.setBody(draft.body());
        response.setPreviewLanguage(previewLanguage(draft));
        response.setMockDelayMs(draft.delayMs());
        response.setMockScript(draft.script());
        if (existing != null) {
            response.setCookies(existing.getCookies());
            response.setCostMs(existing.getCostMs());
            response.setFile(fileInfoAfterEdit(existing, draft.body(), response.getHeaders()));
            response.setBodySize(bodySizeAfterEdit(existing, draft.body(), response.getHeaders(), response.getFile()));
            response.setHeadersSize(existing.getHeadersSize());
        }
        response.setOriginalRequest(originalRequest(request, existing, draft, fallbackBaseUrl));
        return response;
    }

    /**
     * 编辑后仍有效的文件信息，按编辑后的响应头更新文件名：
     * <ul>
     *     <li>文本附件：正文本就是可编辑文本，只要响应头仍声明 attachment 就保留</li>
     *     <li>其余文件：正文未改动时保留（只改延迟/脚本不应让“未保存文件内容”的示例变成 0 字节文件），
     *     手动填写正文，或把响应头改成既非附件也非二进制类型后，按普通文本示例处理</li>
     *     <li>文件名优先取 Content-Disposition；文本附件未声明文件名时按 Content-Type 更新扩展名</li>
     * </ul>
     */
    static SavedResponse.FileInfo fileInfoAfterEdit(SavedResponse existing, String editedBody, List<HttpHeader> editedHeaders) {
        SavedResponse.FileInfo file = existing.getFile();
        if (file == null) {
            return null;
        }
        String contentDisposition = header(editedHeaders, "Content-Disposition");
        if (file.isContentInBody()
                ? !ResponseFileNames.isAttachment(contentDisposition)
                : (!bodyOf(existing).equals(editedBody == null ? "" : editedBody)
                || abandonedFileHeaders(existing, editedHeaders))) {
            return null;
        }
        SavedResponse.FileInfo updated = new SavedResponse.FileInfo();
        updated.setExtension(file.getExtension());
        updated.setContentInBody(file.isContentInBody());
        updated.setAttachment(ResponseFileNames.isAttachment(contentDisposition));
        String dispositionName = ResponseFileNames.parseFileNameFromContentDisposition(contentDisposition);
        if (dispositionName != null && !dispositionName.isBlank()) {
            updated.setFileName(dispositionName);
        } else if (file.isContentInBody()) {
            updated.setFileName(withExtension(file.getFileName(),
                    FileExtensionUtil.guessExtension(header(editedHeaders, "Content-Type"))));
        } else {
            updated.setFileName(file.getFileName());
        }
        return updated;
    }

    /**
     * 用户改动了 Content-Type / Content-Disposition，且新响应头既不是附件也不是二进制类型：不再按文件处理。
     * 响应头未改动时保留（例如服务端声明 JSON、实际返回 xlsx 的示例）。
     */
    private static boolean abandonedFileHeaders(SavedResponse existing, List<HttpHeader> editedHeaders) {
        String contentType = header(editedHeaders, "Content-Type");
        String contentDisposition = header(editedHeaders, "Content-Disposition");
        boolean unchanged = Objects.equals(contentType, header(existing.getHeaders(), "Content-Type"))
                && Objects.equals(contentDisposition, header(existing.getHeaders(), "Content-Disposition"));
        return !unchanged && !ResponseFileNames.isAttachment(contentDisposition)
                && !FileExtensionUtil.isBinaryType(contentType == null ? "" : contentType);
    }

    private static String withExtension(String fileName, String extension) {
        if (fileName == null || extension == null) {
            return fileName;
        }
        String current = ResponseFileNames.extensionOf(fileName);
        return (current == null ? fileName : fileName.substring(0, fileName.length() - current.length())) + extension;
    }

    /**
     * 编辑后的正文大小：正文与字符集都未变，或编辑后仍是未保存内容的文件时沿用原大小；否则按编辑后声明的字符集计算。
     */
    static long bodySizeAfterEdit(SavedResponse existing, String editedBody, List<HttpHeader> editedHeaders,
                                  SavedResponse.FileInfo editedFile) {
        String body = editedBody == null ? "" : editedBody;
        Charset charset = ResponseFiles.charsetOf(header(editedHeaders, "Content-Type"));
        boolean bodyUnchanged = bodyOf(existing).equals(body);
        boolean omittedFile = editedFile != null && !editedFile.isContentInBody();
        // 原本是未保存内容的文件、编辑后降级为普通文本时，旧大小不再对应正文
        boolean wasOmittedFile = existing.getFile() != null && !existing.getFile().isContentInBody();
        boolean sameCharset = charset.equals(ResponseFiles.charsetOf(header(existing.getHeaders(), "Content-Type")));
        if (bodyUnchanged && (omittedFile || (!wasOmittedFile && sameCharset))) {
            return existing.getBodySize();
        }
        return body.getBytes(charset).length;
    }

    private static String bodyOf(SavedResponse response) {
        return response.getBody() == null ? "" : response.getBody();
    }

    private static String header(List<HttpHeader> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (HttpHeader header : headers) {
            if (header != null && header.isEnabled() && name.equalsIgnoreCase(header.getKey())) {
                return header.getValue();
            }
        }
        return null;
    }

    private SavedResponse.OriginalRequest originalRequest(HttpRequestItem request,
                                                          SavedResponse existing,
                                                          MockRouteEditorDialog.Draft draft,
                                                          String fallbackBaseUrl) {
        SavedResponse.OriginalRequest original = new SavedResponse.OriginalRequest();
        SavedResponse.OriginalRequest previous = existing == null ? null : existing.getOriginalRequest();
        original.setMethod(draft.method().toUpperCase(Locale.ROOT));
        original.setUrl(resolveUrl(previous == null ? request.getUrl() : previous.getUrl(),
                draft.path(), fallbackBaseUrl));
        original.setHeaders(copy(previous == null ? request.getHeadersList() : previous.getHeaders()));
        original.setPathVariables(copy(previous == null ? request.getPathVariablesList() : previous.getPathVariables()));
        original.setParams(new ArrayList<>());
        original.setBodyType(previous == null ? request.getBodyType() : previous.getBodyType());
        original.setBody(previous == null ? request.getBody() : previous.getBody());
        original.setFormDataList(copy(previous == null ? request.getFormDataList() : previous.getFormDataList()));
        original.setUrlencodedList(copy(previous == null ? request.getUrlencodedList() : previous.getUrlencodedList()));
        return original;
    }

    private static <T> ArrayList<T> copy(java.util.List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private String previewLanguage(MockRouteEditorDialog.Draft draft) {
        boolean jsonHeader = draft.headers().stream().anyMatch(header -> header != null
                && "Content-Type".equalsIgnoreCase(header.getKey())
                && header.getValue() != null && header.getValue().toLowerCase(Locale.ROOT).contains("json"));
        if (jsonHeader || JsonUtil.isTypeJSON(draft.body())) return "json";
        return "text";
    }

    private String resolveUrl(String existingUrl, String path, String fallbackBaseUrl) {
        String normalizedPath = normalizePath(path);
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (existingUrl != null && !existingUrl.isBlank()) {
            try {
                URI existing = URI.create(existingUrl);
                if (existing.getScheme() != null && existing.getRawAuthority() != null) {
                    return existing.getScheme() + "://" + existing.getRawAuthority() + normalizedPath;
                }
            } catch (IllegalArgumentException ignored) {
                // Variable-based URLs are handled below.
            }
            if (existingUrl.startsWith("{{")) {
                int end = existingUrl.indexOf("}}");
                if (end >= 0) return existingUrl.substring(0, end + 2) + normalizedPath;
            }
        }
        return fallbackBaseUrl + normalizedPath;
    }

    private String normalizePath(String path) {
        String value = path == null || path.isBlank() ? "/" : path.trim();
        return value.startsWith("/") ? value : "/" + value;
    }
}
