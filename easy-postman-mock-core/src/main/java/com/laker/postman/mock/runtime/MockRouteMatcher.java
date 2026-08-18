package com.laker.postman.mock.runtime;

import com.laker.postman.mock.model.MockRequest;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.util.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic Postman-style matcher. Method/path are required; query parameters,
 * configured headers and optionally the body refine the selected Example.
 */
public final class MockRouteMatcher {
    public static final String RESPONSE_ID_HEADER = "x-mock-response-id";
    public static final String RESPONSE_NAME_HEADER = "x-mock-response-name";
    public static final String RESPONSE_CODE_HEADER = "x-mock-response-code";
    public static final String MATCH_REQUEST_BODY_HEADER = "x-mock-match-request-body";
    public static final String MATCH_REQUEST_HEADERS_HEADER = "x-mock-match-request-headers";

    private MockRouteMatcher() {
    }

    public static Optional<Match> match(List<MockRoute> routes,
                                        MockRequest request,
                                        MockServerDefinition definition) {
        Match best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MockRoute route : routes == null ? List.<MockRoute>of() : routes) {
            Match candidate = matchRoute(route, request, definition);
            if (candidate != null && candidate.score() > bestScore) {
                best = candidate;
                bestScore = candidate.score();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Match matchRoute(MockRoute route, MockRequest request, MockServerDefinition definition) {
        if (!safe(route.method()).equalsIgnoreCase(safe(request.method()))) {
            return null;
        }
        if (!selectorMatches(route, request)) {
            return null;
        }

        PathMatch pathMatch = matchPath(route.pathPattern(), request.path());
        if (pathMatch == null) {
            return null;
        }
        if (!multiMapContains(request.queryParameters(), route.queryParameters())) {
            return null;
        }

        List<String> configuredHeaders = effectiveMatchHeaders(definition, request);
        for (String headerName : configuredHeaders) {
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            List<String> expected = valuesIgnoreCase(route.requestHeaders(), headerName);
            List<String> actual = valuesIgnoreCase(request.headers(), headerName);
            if (expected.isEmpty() || !actual.equals(expected)) {
                return null;
            }
        }

        boolean matchRequestBody = definition.isMatchRequestBody()
                || Boolean.parseBoolean(request.header(MATCH_REQUEST_BODY_HEADER));
        if (matchRequestBody && !bodyEquals(route.requestBody(), request.body())) {
            return null;
        }

        int score = pathMatch.score()
                + route.queryParameters().size() * 10
                + configuredHeaders.size() * 10
                + (matchRequestBody ? 20 : 0);
        return new Match(route, pathMatch.variables(), score);
    }

    private static boolean selectorMatches(MockRoute route, MockRequest request) {
        String responseId = request.header(RESPONSE_ID_HEADER);
        if (responseId != null && !responseId.equals(route.exampleId())) {
            return false;
        }
        String responseName = request.header(RESPONSE_NAME_HEADER);
        if (responseName != null && !responseName.equals(route.exampleName())) {
            return false;
        }
        String responseCode = request.header(RESPONSE_CODE_HEADER);
        return responseCode == null || responseCode.equals(String.valueOf(route.response().getStatusCode()));
    }

    private static List<String> effectiveMatchHeaders(MockServerDefinition definition, MockRequest request) {
        List<String> result = new ArrayList<>();
        if (definition.getMatchHeaderNames() != null) {
            definition.getMatchHeaderNames().forEach(name -> addHeaderName(result, name));
        }
        String requested = request.header(MATCH_REQUEST_HEADERS_HEADER);
        if (requested != null) {
            for (String name : requested.split(",")) {
                addHeaderName(result, name);
            }
        }
        return List.copyOf(result);
    }

    private static void addHeaderName(List<String> headers, String name) {
        if (name == null || name.isBlank()) return;
        String normalized = name.trim();
        if (headers.stream().noneMatch(existing -> existing.equalsIgnoreCase(normalized))) {
            headers.add(normalized);
        }
    }

    private static PathMatch matchPath(String pattern, String path) {
        String normalizedPattern = normalizePath(pattern);
        String normalizedPath = normalizePath(path);
        if (normalizedPattern.equals(normalizedPath)) {
            return new PathMatch(Map.of(), 200 + normalizedPattern.length());
        }

        String[] patternSegments = segments(normalizedPattern);
        String[] pathSegments = segments(normalizedPath);
        if (patternSegments.length != pathSegments.length) {
            return null;
        }
        Map<String, String> variables = new LinkedHashMap<>();
        int literalCount = 0;
        for (int i = 0; i < patternSegments.length; i++) {
            String variableName = variableName(patternSegments[i]);
            if (variableName != null) {
                variables.put(variableName, pathSegments[i]);
            } else if (patternSegments[i].equals(pathSegments[i])) {
                literalCount++;
            } else {
                return null;
            }
        }
        return new PathMatch(Map.copyOf(variables), 100 + literalCount * 10);
    }

    private static String variableName(String segment) {
        if (segment.startsWith("{{") && segment.endsWith("}}") && segment.length() > 4) {
            return segment.substring(2, segment.length() - 2);
        }
        if (segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2) {
            return segment.substring(1, segment.length() - 1);
        }
        if (segment.startsWith(":") && segment.length() > 1) {
            return segment.substring(1);
        }
        return null;
    }

    private static String normalizePath(String path) {
        String normalized = path == null || path.isBlank() ? "/" : path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String[] segments(String path) {
        return "/".equals(path) ? new String[0] : path.substring(1).split("/", -1);
    }

    private static boolean multiMapContains(Map<String, List<String>> actual,
                                            Map<String, List<String>> expected) {
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            List<String> actualValues = actual.getOrDefault(entry.getKey(), List.of());
            if (!actualValues.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static List<String> valuesIgnoreCase(Map<String, List<String>> values, String key) {
        if (values == null || key == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        values.forEach((candidate, items) -> {
            if (candidate != null && candidate.equalsIgnoreCase(key) && items != null) {
                result.addAll(items);
            }
        });
        return result;
    }

    private static boolean bodyEquals(String expected, String actual) {
        String left = safe(expected).trim();
        String right = safe(actual).trim();
        if (left.equals(right)) {
            return true;
        }
        return JsonUtil.isStructurallyEqual(left, right);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record Match(MockRoute route, Map<String, String> pathVariables, int score) {
    }

    private record PathMatch(Map<String, String> variables, int score) {
    }
}
