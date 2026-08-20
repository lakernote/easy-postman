package com.laker.postman.service.js.api;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.RequestBodyTypes;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Postman-compatible view of {@code pm.request.body}.
 *
 * <p>Postman exposes an SDK {@code RequestBody}, rather than the rendered body string. This
 * adapter keeps that object shape in scripts and translates mutations back to the request that
 * EasyPostman sends.</p>
 */
public class ScriptRequestBodyAccessor implements ProxyObject {
    private static final String[] BODY_MEMBER_KEYS = {
            "mode", "raw", "urlencoded", "formdata", "file", "options", "disabled"
    };

    public String mode;
    public Object raw;
    public Object urlencoded;
    public Object formdata;
    public Object file;
    public Object options;
    public Boolean disabled;

    private final PreparedRequest request;
    private final JsListWrapper<HttpFormData> sharedFormData;
    private final JsListWrapper<HttpFormUrlencoded> sharedUrlencoded;
    private BodySnapshot syncedSnapshot;
    private boolean bodyMutationRequested;

    public ScriptRequestBodyAccessor(PreparedRequest request) {
        this(request, null, null);
    }

    ScriptRequestBodyAccessor(PreparedRequest request,
                              JsListWrapper<HttpFormData> sharedFormData,
                              JsListWrapper<HttpFormUrlencoded> sharedUrlencoded) {
        this.request = request;
        this.sharedFormData = sharedFormData;
        this.sharedUrlencoded = sharedUrlencoded;
        loadFromRequest();
        this.syncedSnapshot = snapshot();
    }

    /**
     * Mirrors Postman's {@code RequestBody.update(options)}. A string selects raw mode.
     */
    public void update(Object options) {
        Object definition = toJavaObject(options);
        if (definition instanceof CharSequence text) {
            bodyMutationRequested |= applyDefinition(Map.of("mode", "raw", "raw", text.toString()));
            return;
        }
        if (definition instanceof Map<?, ?> map) {
            bodyMutationRequested |= applyDefinition(map);
        }
    }

    public boolean isEmpty() {
        if (mode == null) {
            return true;
        }
        return switch (mode) {
            case "raw" -> stringify(raw).isEmpty();
            case "urlencoded" -> collectionIsEmpty(urlencoded);
            case "formdata" -> collectionIsEmpty(formdata);
            case "file" -> fileSource(file).isEmpty();
            default -> true;
        };
    }

    /**
     * Matches the Postman SDK string conversion used by {@code JSON.parse(pm.request.body)} and
     * string concatenation. Form-data and file bodies stringify to an empty string in Postman.
     */
    @Override
    public String toString() {
        if ("raw".equals(mode)) {
            return stringify(raw);
        }
        if ("urlencoded".equals(mode)) {
            StringJoiner joiner = new StringJoiner("&");
            for (HttpFormUrlencoded item : toUrlencodedList(urlencoded)) {
                if (item.isEnabled()) {
                    String key = normalizeQueryParamComponent(item.getKey(), true);
                    String value = normalizeQueryParamComponent(item.getValue(), false);
                    joiner.add(item.getValue() == null ? key : key + "=" + value);
                }
            }
            return joiner.toString();
        }
        return "";
    }

    /**
     * Supplies Postman's collection JSON shape to {@code JSON.stringify(pm.request.body)}.
     */
    public Object toJSON() {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfNotNull(result, "mode", mode);
        putIfNotNull(result, "raw", toJavaObject(raw));
        putIfNotNull(result, "urlencoded", urlencodedToJson(urlencoded));
        putIfNotNull(result, "formdata", formdataToJson(formdata));
        putIfNotNull(result, "file", toJavaObject(file));
        putIfNotNull(result, "options", toJavaObject(options));
        putIfNotNull(result, "disabled", disabled);
        return toProxyValue(result);
    }

    /**
     * JavaScript's {@code JSON.stringify} calls {@code toJSON(key)} with one argument.
     */
    public Object toJSON(Object ignoredKey) {
        return toJSON();
    }

    /**
     * Writes body fields back only when the script changed this RequestBody view. This avoids
     * overwriting deliberate mutations made through the legacy {@code pm.request.raw} escape hatch.
     */
    public boolean syncToRaw() {
        BodySnapshot current = snapshot();
        boolean bodyChanged = !Objects.equals(current, syncedSnapshot);
        if (!bodyChanged && !bodyMutationRequested) {
            return false;
        }

        if (bodyChanged) {
            applyToRequest();
        }
        loadFromRequest();
        syncedSnapshot = snapshot();
        bodyMutationRequested = false;
        return true;
    }

    private boolean applyDefinition(Map<?, ?> definition) {
        Object requestedMode = mapValue(definition, "mode");
        if (requestedMode == null) {
            return false;
        }
        if ("graphql".equalsIgnoreCase(stringify(requestedMode))) {
            return false;
        }

        this.mode = normalizeMode(stringify(requestedMode));
        this.raw = mapValue(definition, "raw");
        this.urlencoded = mapValue(definition, "urlencoded");
        this.formdata = mapValue(definition, "formdata");
        this.file = mapValue(definition, "file");
        this.options = mapValue(definition, "options");
        Object disabledValue = mapValue(definition, "disabled");
        this.disabled = disabledValue instanceof Boolean value ? value : null;

        if ("raw".equals(mode) && raw == null) {
            raw = "";
        } else if ("urlencoded".equals(mode) && urlencoded == null) {
            urlencoded = new ArrayList<>();
        } else if ("formdata".equals(mode) && formdata == null) {
            formdata = new ArrayList<>();
        } else if ("file".equals(mode) && file instanceof CharSequence source) {
            file = new LinkedHashMap<>(Map.of("src", source.toString()));
        }
        return true;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "mode" -> mode;
            case "raw" -> raw;
            case "urlencoded" -> urlencoded;
            case "formdata" -> formdata;
            case "file" -> file;
            case "options" -> options;
            case "disabled" -> disabled;
            case "update" -> (ProxyExecutable) arguments -> {
                update(arguments.length > 0 ? arguments[0] : null);
                return null;
            };
            case "isEmpty" -> (ProxyExecutable) arguments -> isEmpty();
            case "toJSON" -> (ProxyExecutable) arguments -> toJSON();
            case "toString" -> (ProxyExecutable) arguments -> toString();
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return BODY_MEMBER_KEYS;
    }

    @Override
    public boolean hasMember(String key) {
        return switch (key) {
            case "mode", "raw", "urlencoded", "formdata", "file", "options", "disabled",
                 "update", "isEmpty", "toJSON", "toString" -> true;
            default -> false;
        };
    }

    @Override
    public void putMember(String key, Value value) {
        Object converted = toJavaObject(value);
        switch (key) {
            case "mode" -> mode = converted == null ? null : converted.toString();
            case "raw" -> raw = converted;
            case "urlencoded" -> urlencoded = converted;
            case "formdata" -> formdata = converted;
            case "file" -> file = converted;
            case "options" -> options = converted;
            case "disabled" -> disabled = converted instanceof Boolean flag ? flag : null;
            default -> {
                return;
            }
        }
        bodyMutationRequested = true;
    }

    @Override
    public boolean removeMember(String key) {
        switch (key) {
            case "mode" -> mode = null;
            case "raw" -> raw = null;
            case "urlencoded" -> urlencoded = null;
            case "formdata" -> formdata = null;
            case "file" -> file = null;
            case "options" -> options = null;
            case "disabled" -> disabled = null;
            default -> {
                return false;
            }
        }
        bodyMutationRequested = true;
        return true;
    }

    private void applyToRequest() {
        if (Boolean.TRUE.equals(disabled) || mode == null) {
            clearRequestBody();
            return;
        }
        if ("graphql".equalsIgnoreCase(mode)) {
            loadFromRequest();
            return;
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
                request.body = stringify(raw);
                request.bodyType = RequestBodyTypes.BODY_TYPE_RAW;
                request.isMultipart = false;
            }
        }
    }

    private void clearRequestBody() {
        request.body = null;
        request.bodyType = RequestBodyTypes.BODY_TYPE_NONE;
        request.formDataList = new ArrayList<>();
        request.urlencodedList = new ArrayList<>();
        request.isMultipart = false;
    }

    private void loadFromRequest() {
        String requestMode = resolveMode(request);
        this.mode = requestMode;
        this.raw = "raw".equals(requestMode) ? Objects.toString(request.body, "") : null;
        List<HttpFormData> formDataList = ensureFormDataList();
        List<HttpFormUrlencoded> urlencodedList = ensureUrlencodedList();
        this.formdata = "formdata".equals(requestMode)
                ? reuseOrCreate(sharedFormData, formDataList, JsListWrapper.ListType.FORM_DATA)
                : null;
        this.urlencoded = "urlencoded".equals(requestMode)
                ? reuseOrCreate(sharedUrlencoded, urlencodedList, JsListWrapper.ListType.URLENCODED)
                : null;
        this.file = "file".equals(requestMode)
                ? new LinkedHashMap<>(Map.of("src", Objects.toString(request.body, "")))
                : null;
        this.options = null;
        this.disabled = null;
    }

    private static <T> JsListWrapper<T> reuseOrCreate(JsListWrapper<T> shared,
                                                       List<T> current,
                                                       JsListWrapper.ListType type) {
        return shared != null && shared.getList() == current
                ? shared
                : new JsListWrapper<>(current, type);
    }

    private List<HttpFormData> ensureFormDataList() {
        if (request.formDataList == null) {
            request.formDataList = new ArrayList<>();
        }
        return request.formDataList;
    }

    private List<HttpFormUrlencoded> ensureUrlencodedList() {
        if (request.urlencodedList == null) {
            request.urlencodedList = new ArrayList<>();
        }
        return request.urlencodedList;
    }

    private BodySnapshot snapshot() {
        return new BodySnapshot(
                mode,
                comparable(raw),
                comparable(urlencoded),
                comparable(formdata),
                comparable(file),
                comparable(options),
                disabled
        );
    }

    private static String resolveMode(PreparedRequest request) {
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

    static Object toJavaObject(Object value) {
        if (!(value instanceof Value jsValue)) {
            return value;
        }
        if (jsValue.isNull()) {
            return null;
        }
        if (jsValue.isHostObject()) {
            return jsValue.asHostObject();
        }
        if (jsValue.isString()) {
            return jsValue.asString();
        }
        if (jsValue.isBoolean()) {
            return jsValue.asBoolean();
        }
        if (jsValue.isNumber()) {
            if (jsValue.fitsInLong()) {
                return jsValue.asLong();
            }
            return jsValue.asDouble();
        }
        if (jsValue.hasArrayElements()) {
            List<Object> items = new ArrayList<>((int) jsValue.getArraySize());
            for (long index = 0; index < jsValue.getArraySize(); index++) {
                items.add(toJavaObject(jsValue.getArrayElement(index)));
            }
            return items;
        }
        if (jsValue.hasMembers()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : jsValue.getMemberKeys()) {
                result.put(key, toJavaObject(jsValue.getMember(key)));
            }
            return result;
        }
        return jsValue.toString();
    }

    /**
     * Matches Postman's QueryParam string conversion: separators are escaped while unresolved
     * variable tokens remain intact for the runtime's later variable-resolution pass.
     */
    static String normalizeQueryParamComponent(String value, boolean encodeEquals) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            if (value.startsWith("{{", index)) {
                int variableEnd = value.indexOf("}}", index + 2);
                if (variableEnd >= 0) {
                    normalized.append(value, index, variableEnd + 2);
                    index = variableEnd + 2;
                    continue;
                }
            }

            char current = value.charAt(index++);
            switch (current) {
                case '&' -> normalized.append("%26");
                case '#' -> normalized.append("%23");
                case '=' -> normalized.append(encodeEquals ? "%3D" : '=');
                default -> normalized.append(current);
            }
        }
        return normalized.toString();
    }

    private static Object comparable(Object value) {
        Object converted = collectionSource(value);
        return deepComparable(converted);
    }

    private static Object collectionSource(Object value) {
        Object converted = toJavaObject(value);
        if (converted instanceof JsListWrapper<?> wrapper) {
            wrapper.sync();
            return wrapper.getList();
        }
        return converted;
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
            map.forEach((key, item) -> copy.put(String.valueOf(key), deepComparable(toJavaObject(item))));
            return copy;
        }
        if (converted instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            collection.forEach(item -> copy.add(deepComparable(toJavaObject(item))));
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

    private static List<HttpFormData> toFormDataList(Object value) {
        Object converted = collectionSource(value);
        if (!(converted instanceof Collection<?> collection)) {
            return new ArrayList<>();
        }
        List<HttpFormData> result = new ArrayList<>();
        for (Object item : collection) {
            Object convertedItem = toJavaObject(item);
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
            HttpFormData formData = new HttpFormData(
                    isEnabled(map),
                    key,
                    file ? HttpFormData.TYPE_FILE : HttpFormData.TYPE_TEXT,
                    scalarOrFirst(content),
                    stringify(mapValue(map, "description"))
            );
            result.add(formData);
        }
        return result;
    }

    private static List<HttpFormUrlencoded> toUrlencodedList(Object value) {
        Object converted = collectionSource(value);
        if (converted instanceof CharSequence text) {
            return parseUrlencoded(text.toString());
        }
        if (!(converted instanceof Collection<?> collection)) {
            return new ArrayList<>();
        }
        List<HttpFormUrlencoded> result = new ArrayList<>();
        for (Object item : collection) {
            Object convertedItem = toJavaObject(item);
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

    /**
     * Postman's {@code RequestBody.update} accepts a query-string value for {@code urlencoded}
     * and delegates to {@code QueryParam.parse}. The SDK deliberately preserves the encoded text.
     */
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

    private static Object mapValue(Map<?, ?> map, String key) {
        return toJavaObject(map.get(key));
    }

    private static String scalarOrFirst(Object value) {
        Object converted = toJavaObject(value);
        if (converted instanceof List<?> list) {
            return list.isEmpty() ? "" : stringify(list.get(0));
        }
        return stringify(converted);
    }

    private static String fileSource(Object value) {
        Object converted = toJavaObject(value);
        if (converted instanceof CharSequence source) {
            return source.toString();
        }
        if (converted instanceof Map<?, ?> map) {
            Object source = mapValue(map, "src");
            return scalarOrFirst(source);
        }
        return "";
    }

    private static String stringify(Object value) {
        Object converted = toJavaObject(value);
        return converted == null ? "" : String.valueOf(converted);
    }

    private static String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase();
        return switch (normalized) {
            case "file", "formdata", "raw", "urlencoded" -> normalized;
            default -> "raw";
        };
    }

    private static String nullableString(Object value) {
        Object converted = toJavaObject(value);
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

    private static Object toProxyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> proxyValues = new LinkedHashMap<>();
            map.forEach((key, item) -> proxyValues.put(String.valueOf(key), toProxyValue(item)));
            return ProxyObject.fromMap(proxyValues);
        }
        if (value instanceof List<?> list) {
            return ProxyArray.fromList(list.stream().map(ScriptRequestBodyAccessor::toProxyValue).toList());
        }
        return value;
    }

    private record BodySnapshot(String mode,
                                Object raw,
                                Object urlencoded,
                                Object formdata,
                                Object file,
                                Object options,
                                Boolean disabled) {
    }
}
