package com.laker.postman.mock.app;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.http.runtime.file.ResponseFiles;
import com.laker.postman.ioc.Component;
import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.service.collections.CollectionDocumentRegistry;
import com.laker.postman.service.collections.SavedResponseSnapshotMapper;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App adapter that turns collection requests and saved Examples into mock-core routes.
 */
@Component
public class MockCollectionRouteProvider {

    public List<MockCollectionChoice> listCollections() {
        CollectionDocument document = CollectionDocumentRegistry.getDocument().orElse(CollectionDocument.empty());
        List<MockCollectionChoice> choices = new ArrayList<>();
        for (CollectionNode root : document.getRoots()) {
            if (root != null && root.isGroup()) {
                choices.add(new MockCollectionChoice(
                        root.asGroup().getId(),
                        root.asGroup().getName(),
                        countRequests(root),
                        countExamples(root)
                ));
            }
        }
        return choices;
    }

    public List<MockRoute> buildRoutes(List<String> collectionIds) {
        CollectionDocument document = CollectionDocumentRegistry.getDocument().orElse(CollectionDocument.empty());
        return buildRoutes(document, collectionIds);
    }

    public List<MockRoute> buildRoutes(CollectionDocument document, List<String> collectionIds) {
        List<MockRoute> routes = new ArrayList<>();
        for (String collectionId : normalizedIds(collectionIds)) {
            findCollectionNode(document, collectionId)
                    .ifPresent(collection -> collectRoutes(collectionId, collection, routes));
        }
        return List.copyOf(routes);
    }

    public List<MockRouteEntry> listRouteEntries(String collectionId) {
        return listRouteEntries(collectionId == null ? List.of() : List.of(collectionId));
    }

    public List<MockRouteEntry> listRouteEntries(List<String> collectionIds) {
        List<MockRouteEntry> entries = new ArrayList<>();
        for (String collectionId : normalizedIds(collectionIds)) {
            findCollectionNode(collectionId).ifPresent(collection -> collectRouteEntries(
                    collectionId, collection.asGroup().getName(), collection, entries));
        }
        return List.copyOf(entries);
    }

    public Optional<HttpRequestItem> findRequest(String collectionId, String requestId) {
        return findCollectionNode(collectionId).flatMap(node -> findRequest(node, requestId));
    }

    public Optional<SavedResponse> findExample(String collectionId, String requestId, String exampleId) {
        return findRequest(collectionId, requestId).flatMap(request -> {
            if (request.getResponse() == null) return Optional.empty();
            return request.getResponse().stream()
                    .filter(example -> example != null && exampleId != null && exampleId.equals(example.getId()))
                    .findFirst();
        });
    }

    private Optional<CollectionNode> findCollectionNode(String collectionId) {
        CollectionDocument document = CollectionDocumentRegistry.getDocument().orElse(CollectionDocument.empty());
        return findCollectionNode(document, collectionId);
    }

    private Optional<CollectionNode> findCollectionNode(CollectionDocument document, String collectionId) {
        if (document == null) {
            return Optional.empty();
        }
        return document.getRoots().stream()
                .filter(node -> node != null && node.isGroup())
                .filter(node -> node.asGroup().getId().equals(collectionId))
                .findFirst();
    }

    private Optional<HttpRequestItem> findRequest(CollectionNode node, String requestId) {
        if (node.isRequest()) {
            return requestId != null && requestId.equals(node.asRequest().getId())
                    ? Optional.of(node.asRequest())
                    : Optional.empty();
        }
        for (CollectionNode child : node.getChildren()) {
            Optional<HttpRequestItem> found = findRequest(child, requestId);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private int countExamples(CollectionNode node) {
        if (node.isRequest()) {
            if (!isHttp(node.asRequest())) return 0;
            List<SavedResponse> responses = node.asRequest().getResponse();
            return responses == null ? 0 : responses.size();
        }
        return node.getChildren().stream().mapToInt(this::countExamples).sum();
    }

    private int countRequests(CollectionNode node) {
        if (node.isRequest()) return isHttp(node.asRequest()) ? 1 : 0;
        return node.getChildren().stream().mapToInt(this::countRequests).sum();
    }

    private void collectRoutes(String collectionId, CollectionNode node, List<MockRoute> routes) {
        if (node.isRequest()) {
            HttpRequestItem request = node.asRequest();
            if (!isHttp(request)) return;
            if (request.getResponse() != null) {
                for (SavedResponse example : request.getResponse()) {
                    if (example != null) {
                        routes.add(toRoute(collectionId, request, example));
                    }
                }
            }
            return;
        }
        node.getChildren().forEach(child -> collectRoutes(collectionId, child, routes));
    }

    private void collectRouteEntries(String collectionId,
                                     String collectionName,
                                     CollectionNode node,
                                     List<MockRouteEntry> entries) {
        if (node.isRequest()) {
            HttpRequestItem request = node.asRequest();
            if (!isHttp(request)) return;
            List<SavedResponse> responses = request.getResponse();
            if (responses == null || responses.isEmpty()) {
                entries.add(new MockRouteEntry(
                        collectionId, collectionName, "", false,
                        request.getId(), request.getName(), "", "", firstNonBlank(request.getMethod(), "GET"),
                        extractPath(request.getUrl()), 0, 0, false, false
                ));
            } else {
                for (SavedResponse example : responses) {
                    if (example == null) continue;
                    MockRoute route = toRoute(collectionId, request, example);
                    entries.add(new MockRouteEntry(
                            collectionId, collectionName, route.routeId(), false,
                            request.getId(), request.getName(), route.exampleId(), route.exampleName(),
                            route.method(), route.pathPattern(), route.response().getStatusCode(),
                            route.response().getDelayMs(), true, !route.script().isBlank()
                    ));
                }
            }
            return;
        }
        node.getChildren().forEach(child -> collectRouteEntries(
                collectionId, collectionName, child, entries));
    }

    private boolean isHttp(HttpRequestItem request) {
        return request.getProtocol() == null || request.getProtocol().isHttpProtocol();
    }

    private MockRoute toRoute(String collectionId, HttpRequestItem request, SavedResponse example) {
        SavedResponse.OriginalRequest original = example.getOriginalRequest();
        String method = firstNonBlank(original == null ? null : original.getMethod(), request.getMethod(), "GET")
                .toUpperCase(Locale.ROOT);
        String rawUrl = firstNonBlank(original == null ? null : original.getUrl(), request.getUrl(), "/");
        List<HttpParam> params = original != null ? original.getParams() : request.getParamsList();
        List<HttpHeader> requestHeaders = original != null && original.getHeaders() != null
                && !original.getHeaders().isEmpty()
                ? original.getHeaders()
                : request.getHeadersList();
        String requestBody = firstNonBlank(original == null ? null : original.getBody(), request.getBody(), "");

        int statusCode = example.getCode() <= 0 ? 200 : example.getCode();
        MockResponse response = isFileContentUnavailable(example, method, statusCode)
                ? fileContentUnavailableResponse(example)
                : new MockResponse(statusCode, singleValueHeaders(example.getHeaders()),
                example.getBody() == null ? "" : example.getBody());
        String declaredContentType = response.getHeader("Content-Type");
        if (declaredContentType != null) {
            // Mock Server 统一按 UTF-8 写出正文，charset 必须一致，否则 GBK/UTF-16 示例在客户端会乱码
            response.setHeader("Content-Type", withUtf8Charset(declaredContentType));
        }
        response.setDelayMs(Math.max(0, example.getMockDelayMs()));
        if (response.getHeader("Content-Type") == null) {
            response.setHeader("Content-Type", contentType(example.getPreviewLanguage()));
        }

        String exampleId = firstNonBlank(example.getId(), String.valueOf(example.getTimestamp()), example.getName());
        String routeId = request.getId() + ":" + exampleId;
        if (collectionId != null && !collectionId.isBlank()) {
            routeId = collectionId + ":" + routeId;
        }
        return new MockRoute(
                routeId,
                request.getId(),
                firstNonBlank(request.getName(), method + " " + extractPath(rawUrl)),
                exampleId,
                firstNonBlank(example.getName(), String.valueOf(response.getStatusCode())),
                method,
                extractPath(rawUrl),
                params == null || params.isEmpty() ? queryFromUrl(rawUrl) : params(params),
                multiValueHeaders(requestHeaders),
                requestBody,
                response,
                example.getMockScript()
        );
    }

    /**
     * 文件示例只保存了文件信息（或是旧版本保存的乱码文本），没有可返回的原始内容。
     */
    static boolean isFileContentUnavailable(SavedResponse example, String method, int statusCode) {
        // HEAD、1xx、204、304 本就没有正文，Mock 时无需文件内容
        if ("HEAD".equals(method) || statusCode < 200 || statusCode == 204 || statusCode == 304) {
            return false;
        }
        SavedResponse.FileInfo file = example.getFile();
        if (file != null) {
            // 零字节文件不需要内容即可如实返回
            return !file.isContentInBody() && example.getBodySize() > 0;
        }
        return ResponseFiles.detectFromLegacyText(example.getBody()) != null
                || SavedResponseSnapshotMapper.isLegacyBinaryPlaceholder(example);
    }

    /**
     * 把 Content-Type 中的 charset 改为 UTF-8；未声明 charset 时保持原样（UTF-8 正文对 JSON 等类型即默认值）。
     */
    static String withUtf8Charset(String contentType) {
        Matcher matcher = CHARSET_PARAMETER.matcher(contentType);
        if (!matcher.find() || "utf-8".equalsIgnoreCase(matcher.group(2))) {
            return contentType;
        }
        return contentType.substring(0, matcher.start()) + matcher.group(1) + "UTF-8"
                + contentType.substring(matcher.end());
    }

    private static final Pattern CHARSET_PARAMETER =
            Pattern.compile("(?i)(;\\s*charset\\s*=\\s*)\"?([^;\"\\s]+)\"?");

    /**
     * 不能用原状态码和响应头返回空正文（客户端会收到 0 字节的“文件”），改为明确的 501 说明；
     * Mock 脚本仍可覆盖该响应。
     */
    private MockResponse fileContentUnavailableResponse(SavedResponse example) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/plain; charset=UTF-8");
        return new MockResponse(501, headers, I18nUtil.getMessage(
                MessageKeys.MOCK_EXAMPLE_FILE_CONTENT_UNAVAILABLE, firstNonBlank(example.getName(), "")));
    }

    static String extractPath(String rawUrl) {
        String value = firstNonBlank(rawUrl, "/").trim();
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() != null) {
                return normalizePath(decode(uri.getRawPath()));
            }
        } catch (IllegalArgumentException ignored) {
            // Variable-based collection URLs are not always valid java.net.URI values.
        }

        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);

        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            int slash = value.indexOf('/', scheme + 3);
            value = slash >= 0 ? value.substring(slash) : "/";
        } else if (value.startsWith("{{")) {
            int variableEnd = value.indexOf("}}");
            int slash = variableEnd >= 0 ? value.indexOf('/', variableEnd + 2) : -1;
            value = slash >= 0 ? value.substring(slash) : "/";
        }
        return normalizePath(decode(value));
    }

    private static String normalizePath(String path) {
        String normalized = path == null || path.isBlank() ? "/" : path;
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static Map<String, List<String>> params(List<HttpParam> params) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (HttpParam param : params) {
            if (param != null && param.isEnabled() && param.getKey() != null && !param.getKey().isBlank()) {
                values.computeIfAbsent(param.getKey(), ignored -> new ArrayList<>())
                        .add(param.getValue() == null ? "" : param.getValue());
            }
        }
        return values;
    }

    private static Map<String, List<String>> queryFromUrl(String rawUrl) {
        int query = rawUrl == null ? -1 : rawUrl.indexOf('?');
        if (query < 0 || query == rawUrl.length() - 1) {
            return Map.of();
        }
        String value = rawUrl.substring(query + 1);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String pair : value.split("&")) {
            int separator = pair.indexOf('=');
            String key = decode(separator < 0 ? pair : pair.substring(0, separator));
            String item = decode(separator < 0 ? "" : pair.substring(separator + 1));
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        return values;
    }

    private static Map<String, List<String>> multiValueHeaders(List<HttpHeader> headers) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (headers != null) {
            for (HttpHeader header : headers) {
                if (header != null && header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
                    values.computeIfAbsent(header.getKey(), ignored -> new ArrayList<>())
                            .add(header.getValue() == null ? "" : header.getValue());
                }
            }
        }
        return values;
    }

    private static Map<String, String> singleValueHeaders(List<HttpHeader> headers) {
        Map<String, String> values = new LinkedHashMap<>();
        if (headers != null) {
            for (HttpHeader header : headers) {
                if (header != null && header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
                    values.put(header.getKey(), header.getValue() == null ? "" : header.getValue());
                }
            }
        }
        return values;
    }

    private static String contentType(String previewLanguage) {
        if (previewLanguage == null) return "text/plain; charset=UTF-8";
        return switch (previewLanguage.toLowerCase(Locale.ROOT)) {
            case "json" -> "application/json; charset=UTF-8";
            case "html" -> "text/html; charset=UTF-8";
            case "xml" -> "application/xml; charset=UTF-8";
            default -> "text/plain; charset=UTF-8";
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String decode(String value) {
        try {
            return value == null ? "" : URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private static List<String> normalizedIds(List<String> collectionIds) {
        if (collectionIds == null) return List.of();
        return collectionIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
