package com.laker.postman.mcp;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

final class McpToolArguments {
    private static final int MAX_STRING_MAP_ENTRIES = 200;
    private static final int MAX_STRING_MAP_KEY_LENGTH = 256;
    private static final int MAX_STRING_MAP_VALUE_LENGTH = 64 * 1024;

    private McpToolArguments() {
    }

    static String requiredString(Map<String, Object> arguments, String name) {
        String value = optionalString(arguments, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    static String optionalString(Map<String, Object> arguments, String name) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        String trimmed = stringValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static int integer(Map<String, Object> arguments, String name, int defaultValue, int min, int max) {
        if (arguments == null || arguments.get(name) == null) {
            return defaultValue;
        }
        Object value = arguments.get(name);
        int parsed;
        if (value instanceof Number number) {
            try {
                parsed = new BigDecimal(number.toString()).intValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException(name + " must be an integer");
            }
        } else {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
        return parsed;
    }

    static boolean bool(Map<String, Object> arguments, String name, boolean defaultValue) {
        if (arguments == null || arguments.get(name) == null) {
            return defaultValue;
        }
        Object value = arguments.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException(name + " must be a boolean");
    }

    static Map<String, String> stringMap(Map<String, Object> arguments, String name) {
        if (arguments == null || arguments.get(name) == null) {
            return Map.of();
        }
        Object value = arguments.get(name);
        if (!(value instanceof Map<?, ?> input)) {
            throw new IllegalArgumentException(name + " must be an object containing string values");
        }
        if (input.size() > MAX_STRING_MAP_ENTRIES) {
            throw new IllegalArgumentException(name + " may contain at most " + MAX_STRING_MAP_ENTRIES + " entries");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException(name + " keys must be non-empty strings");
            }
            if (!(entry.getValue() instanceof String stringValue)) {
                throw new IllegalArgumentException(name + "." + key + " must be a string");
            }
            if (key.length() > MAX_STRING_MAP_KEY_LENGTH) {
                throw new IllegalArgumentException(name + " keys may contain at most "
                        + MAX_STRING_MAP_KEY_LENGTH + " characters");
            }
            if (stringValue.length() > MAX_STRING_MAP_VALUE_LENGTH) {
                throw new IllegalArgumentException(name + "." + key + " may contain at most "
                        + MAX_STRING_MAP_VALUE_LENGTH + " characters");
            }
            result.put(key, stringValue);
        }
        return Map.copyOf(result);
    }
}
