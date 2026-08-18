package com.laker.postman.mock.script;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped volatile state exposed as {@code pm.state}.
 */
public final class MockState {
    private final ConcurrentHashMap<String, Object> values;

    public MockState(ConcurrentHashMap<String, Object> values) {
        this.values = values;
    }

    public Object get(String key) {
        return key == null ? null : values.get(key);
    }

    public void set(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    public boolean has(String key) {
        return key != null && values.containsKey(key);
    }

    public void unset(String key) {
        if (key != null) {
            values.remove(key);
        }
    }

    public void clear() {
        values.clear();
    }

    public Map<String, Object> toMap() {
        return new LinkedHashMap<>(values);
    }
}
