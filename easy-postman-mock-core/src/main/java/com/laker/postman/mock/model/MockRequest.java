package com.laker.postman.mock.model;

import java.util.List;
import java.util.Map;

/**
 * Read-only request snapshot exposed to a mock script.
 */
public record MockRequest(
        String method,
        String path,
        Map<String, List<String>> queryParameters,
        Map<String, List<String>> headers,
        String body,
        Map<String, String> pathVariables
) {
    public String header(String name) {
        return firstIgnoreCase(headers, name);
    }

    public String query(String name) {
        List<String> values = queryParameters.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    public String pathVariable(String name) {
        return pathVariables.get(name);
    }

    private static String firstIgnoreCase(Map<String, List<String>> values, String name) {
        if (name == null) {
            return null;
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(items -> items != null && !items.isEmpty())
                .map(items -> items.get(0))
                .findFirst()
                .orElse(null);
    }
}
