package com.laker.postman.mock.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable response exposed to the optional mock script.
 */
@Data
@NoArgsConstructor
public class MockResponse {
    private int statusCode = 200;
    private Map<String, String> headers = new LinkedHashMap<>();
    private String body = "";
    private int delayMs;

    public MockResponse(int statusCode, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        this.body = body == null ? "" : body;
    }

    public MockResponse copy() {
        MockResponse copy = new MockResponse(statusCode, headers, body);
        copy.delayMs = delayMs;
        return copy;
    }

    public void setHeader(String name, Object value) {
        if (name == null || name.isBlank()) {
            return;
        }
        removeHeader(name);
        headers.put(name, value == null ? "" : String.valueOf(value));
    }

    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public void removeHeader(String name) {
        if (name != null) {
            headers.keySet().removeIf(key -> key.equalsIgnoreCase(name));
        }
    }
}
