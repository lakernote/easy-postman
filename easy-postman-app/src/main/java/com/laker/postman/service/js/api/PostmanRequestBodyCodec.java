package com.laker.postman.service.js.api;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.RequestBodyTypes;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Converts between Postman's RequestBody value model and EasyPostman's transport request model.
 *
 * <p>This class deliberately has no GraalJS proxy lifecycle or mutation tracking. Those concerns
 * belong to {@link ScriptRequestBodyAccessor}; this codec owns only RequestBody parsing,
 * rendering, serialization, comparison, and write-back rules.</p>
 */
@UtilityClass
class PostmanRequestBodyCodec {

    static boolean isEmpty(String mode,
                           Object raw,
                           Object urlencoded,
                           Object formdata,
                           Object file) {
        if (mode == null) {
            return true;
        }
        return switch (mode) {
            case "raw" -> stringifyRaw(raw).isEmpty();
            case "urlencoded" -> collectionIsEmpty(urlencoded);
            case "formdata" -> collectionIsEmpty(formdata);
            case "file" -> fileSource(file).isEmpty();
            default -> true;
        };
    }

    static String render(String mode, Object raw, Object urlencoded) {
        if ("raw".equals(mode)) {
            return stringifyRaw(raw);
        }
        if ("urlencoded".equals(mode)) {
            StringJoiner joiner = new StringJoiner("&");
            for (HttpFormUrlencoded item : toUrlencodedList(urlencoded)) {
                if (item.isEnabled()) {
                    String key = PostmanQueryCodec.normalizeComponent(item.getKey(), true);
                    String value = PostmanQueryCodec.normalizeComponent(item.getValue(), false);
                    joiner.add(item.getValue() == null ? key : key + "=" + value);
                }
            }
            return joiner.toString();
        }
        return "";
    }

    static Map<String, Object> toJson(String mode,
                                      Object raw,
                                      Object urlencoded,
                                      Object formdata,
                                      Object file,
                                      Object options,
                                      Boolean disabled) {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfNotNull(result, "mode", mode);
        putIfNotNull(result, "raw", ScriptValueConverter.toJavaObject(raw));
        putIfNotNull(result, "urlencoded", urlencodedToJson(urlencoded));
        putIfNotNull(result, "formdata", formdataToJson(formdata));
        putIfNotNull(result, "file", ScriptValueConverter.toJavaObject(file));
        putIfNotNull(result, "options", ScriptValueConverter.toJavaObject(options));
        putIfNotNull(result, "disabled", disabled);
        return result;
    }

    static boolean applyToRequest(PreparedRequest request,
                                  String mode,
                                  Object raw,
                                  Object urlencoded,
                                  Object formdata,
                                  Object file,
                                  Boolean disabled) {
        if (Boolean.TRUE.equals(disabled) || mode == null) {
            clearRequestBody(request);
            return true;
        }
        if ("graphql".equalsIgnoreCase(mode)) {
            return false;
        }

        switch (normalizeMode(mode)) {
            case "formdata" -> {
                request.formDataList = toFormDataList(formdata);
                request.urlencodedList = new ArrayList<>();
                request.body = null;
                request.bodyType = RequestBodyTypes.BODY_TYPE_FORM_DATA;
                request.isMultipart = true;
            }
            case "urlencoded" -> {
                request.urlencodedList = toUrlencodedList(urlencoded);
                request.formDataList = new ArrayList<>();
                request.body = null;
                request.bodyType = RequestBodyTypes.BODY_TYPE_FORM_URLENCODED;
                request.isMultipart = false;
            }
            case "file" -> {
                request.formDataList = new ArrayList<>();
                request.urlencodedList = new ArrayList<>();
                request.body = fileSource(file);
                request.bodyType = RequestBodyTypes.BODY_TYPE_BINARY;
                request.isMultipart = false;
            }
            default -> {
                request.formDataList = new ArrayList<>();
                request.urlencodedList = new ArrayList<>();
                request.body = stringifyRaw(raw);
                request.bodyType = RequestBodyTypes.BODY_TYPE_RAW;
                request.isMultipart = false;
            }
        }
        return true;
    }

    static BodyTransportSnapshot transportSnapshot(String mode,
                                                    Object raw,
                                                    Object urlencoded,
                                                    Object formdata,
                                                    Object file,
                                                    Boolean disabled) {
        boolean bodyDisabled = Boolean.TRUE.equals(disabled);
        if (bodyDisabled || mode == null) {
            return new BodyTransportSnapshot(null, null, bodyDisabled);
        }

        String normalizedMode = normalizeMode(mode);
        Object content = switch (normalizedMode) {
            case "formdata" -> comparable(formdata);
            case "urlencoded" -> comparable(urlencoded);
            case "file" -> fileSource(file);
            default -> comparable(raw);
        };
        return new BodyTransportSnapshot(normalizedMode, content, false);
    }

    static String resolveMode(PreparedRequest request) {
        if (RequestBodyTypes.BODY_TYPE_FORM_DATA.equals(request.bodyType) || request.isMultipart) {
            return "formdata";
        }
        if (RequestBodyTypes.BODY_TYPE_FORM_URLENCODED.equals(request.bodyType)) {
            return "urlencoded";
        }
        if (RequestBodyTypes.BODY_TYPE_BINARY.equals(request.bodyType)) {
            return "file";
        }
        if (RequestBodyTypes.BODY_TYPE_RAW.equals(request.bodyType)) {
            return "raw";
        }
        if (request.formDataList != null && !request.formDataList.isEmpty()) {
            return "formdata";
        }
        if (request.urlencodedList != null && !request.urlencodedList.isEmpty()) {
            return "urlencoded";
        }
        return request.body != null ? "raw" : null;
    }

    static boolean hasBody(PreparedRequest request) {
        return resolveMode(request) != null;
    }

    static String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "file", "formdata", "raw", "urlencoded" -> normalized;
            default -> "raw";
        };
    }

    static Object mapValue(Map<?, ?> map, String key) {
        return ScriptValueConverter.toJavaObject(map.get(key));
    }

    static String stringify(Object value) {
        Object converted = ScriptValueConverter.toJavaObject(value);
        return converted == null ? "" : String.valueOf(converted);
    }

    /**
     * Mirrors RequestBody#toString in postman-collection: a falsy active raw value renders as an
     * empty payload, while truthy values use JavaScript's String conversion rather than Java's
     * collection/map representation.
     */
    static String stringifyRaw(Object value) {
        Object converted = ScriptValueConverter.toJavaObject(value);
        if (isJavaScriptFalsy(converted)) {
            return "";
        }
        return stringifyJavaScriptValue(converted);
    }

    private static boolean isJavaScriptFalsy(Object value) {
        if (value == null || Boolean.FALSE.equals(value)) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.isEmpty();
        }
        if (value instanceof Number number) {
            double numericValue = number.doubleValue();
            return numericValue == 0.0d || Double.isNaN(numericValue);
        }
        return false;
    }

    private static String stringifyJavaScriptValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?>) {
            return "[object Object]";
        }
        if (value instanceof Collection<?> collection) {
            return joinJavaScriptArray(collection);
        }
        if (value.getClass().isArray()) {
            StringJoiner joiner = new StringJoiner(",");
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                joiner.add(stringifyJavaScriptValue(
                        ScriptValueConverter.toJavaObject(Array.get(value, index))
                ));
            }
            return joiner.toString();
        }
        return String.valueOf(value);
    }

    private static String joinJavaScriptArray(Collection<?> values) {
        StringJoiner joiner = new StringJoiner(",");
        for (Object value : values) {
            joiner.add(stringifyJavaScriptValue(ScriptValueConverter.toJavaObject(value)));
        }
        return joiner.toString();
    }

    private static void clearRequestBody(PreparedRequest request) {
        request.body = null;
        request.bodyType = RequestBodyTypes.BODY_TYPE_NONE;
        request.formDataList = new ArrayList<>();
        request.urlencodedList = new ArrayList<>();
        request.isMultipart = false;
    }

    private static Object comparable(Object value) {
        return deepComparable(collectionSource(value));
    }

    private static Object collectionSource(Object value) {
        return ScriptValueConverter.toJavaObject(value);
    }

    private static Object deepComparable(Object converted) {
        if (converted instanceof HttpFormData item) {
            return Arrays.asList(
                    item.isEnabled(), item.getKey(), item.getType(), item.getValue(), item.getDescription()
            );
        }
        if (converted instanceof HttpFormUrlencoded item) {
            return Arrays.asList(item.isEnabled(), item.getKey(), item.getValue(), item.getDescription());
        }
        if (converted instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(
                    String.valueOf(key),
                    deepComparable(ScriptValueConverter.toJavaObject(item))
            ));
            return copy;
        }
        if (converted instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            collection.forEach(item -> copy.add(deepComparable(ScriptValueConverter.toJavaObject(item))));
            return copy;
        }
        return converted;
    }

    private static boolean collectionIsEmpty(Object value) {
        Object converted = comparable(value);
        if (converted instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (converted instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return converted == null;
    }

    static List<HttpFormData> toFormDataList(Object value) {
        Object converted = collectionSource(value);
        if (!(converted instanceof Collection<?> collection)) {
            return new ArrayList<>();
        }
        List<HttpFormData> result = new ArrayList<>();
        for (Object item : collection) {
            Object convertedItem = ScriptValueConverter.toJavaObject(item);
            if (convertedItem instanceof HttpFormData formData) {
                result.add(formData);
                continue;
            }
            if (!(convertedItem instanceof Map<?, ?> map)) {
                continue;
            }
            String key = stringify(mapValue(map, "key"));
            String type = stringify(mapValue(map, "type"));
            boolean file = "file".equalsIgnoreCase(type);
            Object content = file ? mapValue(map, "src") : mapValue(map, "value");
            result.add(new HttpFormData(
                    isEnabled(map),
                    key,
                    file ? HttpFormData.TYPE_FILE : HttpFormData.TYPE_TEXT,
                    scalarOrFirst(content),
                    stringify(mapValue(map, "description"))
            ));
        }
        return result;
    }

    static List<HttpFormUrlencoded> toUrlencodedList(Object value) {
        Object converted = collectionSource(value);
        if (converted instanceof CharSequence text) {
            return parseUrlencoded(text.toString());
        }
        if (!(converted instanceof Collection<?> collection)) {
            return new ArrayList<>();
        }
        List<HttpFormUrlencoded> result = new ArrayList<>();
        for (Object item : collection) {
            Object convertedItem = ScriptValueConverter.toJavaObject(item);
            if (convertedItem instanceof HttpFormUrlencoded urlencodedItem) {
                result.add(urlencodedItem);
                continue;
            }
            if (convertedItem instanceof Map<?, ?> map) {
                result.add(new HttpFormUrlencoded(
                        isEnabled(map),
                        stringify(mapValue(map, "key")),
                        nullableString(mapValue(map, "value")),
                        stringify(mapValue(map, "description"))
                ));
            }
        }
        return result;
    }

    private static List<HttpFormUrlencoded> parseUrlencoded(String text) {
        List<HttpFormUrlencoded> result = new ArrayList<>();
        String[] entries = text.split("&", -1);
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index];
            if (entry.isEmpty() && index < entries.length - 1) {
                result.add(new HttpFormUrlencoded(true, null, null));
                continue;
            }
            int equalsIndex = entry.indexOf('=');
            String key = equalsIndex < 0 ? entry : entry.substring(0, equalsIndex);
            String itemValue = equalsIndex < 0 ? null : entry.substring(equalsIndex + 1);
            result.add(new HttpFormUrlencoded(true, key, itemValue));
        }
        return result;
    }

    private static List<Map<String, Object>> formdataToJson(Object value) {
        if (value == null) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (HttpFormData item : toFormDataList(value)) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("key", item.getKey());
            json.put("type", item.isFile() ? "file" : "text");
            json.put(item.isFile() ? "src" : "value", item.getValue());
            putIfNotBlank(json, "description", item.getDescription());
            if (!item.isEnabled()) {
                json.put("disabled", true);
            }
            result.add(json);
        }
        return result;
    }

    private static List<Map<String, Object>> urlencodedToJson(Object value) {
        if (value == null) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (HttpFormUrlencoded item : toUrlencodedList(value)) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("key", item.getKey());
            json.put("value", item.getValue());
            putIfNotBlank(json, "description", item.getDescription());
            if (!item.isEnabled()) {
                json.put("disabled", true);
            }
            result.add(json);
        }
        return result;
    }

    private static boolean isEnabled(Map<?, ?> map) {
        Object disabledValue = mapValue(map, "disabled");
        if (disabledValue instanceof Boolean disabledFlag) {
            return !disabledFlag;
        }
        Object enabledValue = mapValue(map, "enabled");
        return !(enabledValue instanceof Boolean enabledFlag) || enabledFlag;
    }

    private static String scalarOrFirst(Object value) {
        Object converted = ScriptValueConverter.toJavaObject(value);
        if (converted instanceof List<?> list) {
            return list.isEmpty() ? "" : stringify(list.get(0));
        }
        return stringify(converted);
    }

    private static String fileSource(Object value) {
        Object converted = ScriptValueConverter.toJavaObject(value);
        if (converted instanceof CharSequence source) {
            return source.toString();
        }
        if (converted instanceof Map<?, ?> map) {
            return scalarOrFirst(mapValue(map, "src"));
        }
        return "";
    }

    private static String nullableString(Object value) {
        Object converted = ScriptValueConverter.toJavaObject(value);
        return converted == null ? null : String.valueOf(converted);
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    record BodyTransportSnapshot(String mode, Object content, boolean disabled) {
    }
}
