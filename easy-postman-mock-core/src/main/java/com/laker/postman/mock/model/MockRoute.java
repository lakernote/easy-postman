package com.laker.postman.mock.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime route built from a collection request and one saved Example response.
 */
public record MockRoute(
        String routeId,
        String requestId,
        String requestName,
        String exampleId,
        String exampleName,
        String method,
        String pathPattern,
        Map<String, List<String>> queryParameters,
        Map<String, List<String>> requestHeaders,
        String requestBody,
        MockResponse response,
        String script
) {
    public MockRoute {
        queryParameters = immutableMultiMap(queryParameters);
        requestHeaders = immutableMultiMap(requestHeaders);
        response = response == null ? new MockResponse() : response.copy();
        script = script == null ? "" : script;
    }

    private static Map<String, List<String>> immutableMultiMap(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, values) -> copy.put(key, values == null ? List.of() : List.copyOf(values)));
        return Map.copyOf(copy);
    }
}
