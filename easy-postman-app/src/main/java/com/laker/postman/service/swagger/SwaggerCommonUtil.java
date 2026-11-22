package com.laker.postman.service.swagger;

import com.laker.postman.model.Environment;
import com.laker.postman.request.model.HttpHeader;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Swagger/OpenAPI 共享工具类
 * 提供各版本解析器通用的工具方法
 */
@UtilityClass
class SwaggerCommonUtil {

    /**
     * 判断是否不是 HTTP 方法
     */
    static boolean isNotHttpMethod(String method) {
        return !"get".equalsIgnoreCase(method) &&
                !"post".equalsIgnoreCase(method) &&
                !"put".equalsIgnoreCase(method) &&
                !"delete".equalsIgnoreCase(method) &&
                !"patch".equalsIgnoreCase(method) &&
                !"head".equalsIgnoreCase(method) &&
                !"options".equalsIgnoreCase(method);
    }

    /**
     * 从 schema 生成示例 JSON
     */
    static String generateExampleFromSchema(JSONObject schema) {
        return generateExampleFromSchema(null, schema);
    }

    /**
     * 从 schema 生成示例 JSON，支持本地 $ref
     */
    static String generateExampleFromSchema(JSONObject root, JSONObject schema) {
        if (schema == null) {
            return "{}";
        }
        return toJsonText(generateExampleValue(root, schema));
    }

    /**
     * 生成示例值
     */
    static Object generateExampleValue(JSONObject schema) {
        return generateExampleValue(null, schema);
    }

    /**
     * 生成示例值，支持本地 $ref
     */
    static Object generateExampleValue(JSONObject root, JSONObject schema) {
        return generateExampleValue(root, schema, new HashSet<>());
    }

    private static Object generateExampleValue(JSONObject root, JSONObject schema, Set<String> resolvingRefs) {
        if (schema == null) {
            return "";
        }

        String ref = schema.getStr("$ref", "");
        if (!ref.isBlank() && !resolvingRefs.add(ref)) {
            return emptyExampleForCircularReference(root, schema);
        }

        try {
            schema = resolveRefObject(root, schema);

            Object example = getExampleValue(schema);
            if (example != null) {
                return example;
            }

            JSONArray enumValues = schema.getJSONArray("enum");
            if (enumValues != null && !enumValues.isEmpty()) {
                return enumValues.get(0);
            }
            if (schema.containsKey("default")) {
                return schema.get("default");
            }

            String type = getSchemaType(schema);
            switch (type) {
                case "string":
                    String format = schema.getStr("format", "");
                    if ("date".equals(format)) {
                        return "2024-01-01";
                    }
                    if ("date-time".equals(format)) {
                        return "2024-01-01T00:00:00Z";
                    }
                    if ("email".equals(format)) {
                        return "user@example.com";
                    }
                    return "string";

                case "integer":
                    return 0;

                case "number":
                    return 0.0;

                case "boolean":
                    return false;

                case "array":
                    JSONArray array = new JSONArray();
                    JSONObject items = schema.getJSONObject("items");
                    if (items != null) {
                        array.add(generateExampleValue(root, items, resolvingRefs));
                    }
                    return array;

                case "object":
                    JSONObject objectResult = new JSONObject();
                    JSONObject properties = schema.getJSONObject("properties");
                    if (properties != null) {
                        for (String key : properties.keySet()) {
                            objectResult.set(key, generateExampleValue(root, properties.getJSONObject(key), resolvingRefs));
                        }
                    }
                    return objectResult;

                default:
                    JSONArray oneOf = schema.getJSONArray("oneOf");
                    if (oneOf != null && !oneOf.isEmpty()) {
                        return generateExampleValue(root, oneOf.getJSONObject(0), resolvingRefs);
                    }
                    JSONArray anyOf = schema.getJSONArray("anyOf");
                    if (anyOf != null && !anyOf.isEmpty()) {
                        return generateExampleValue(root, anyOf.getJSONObject(0), resolvingRefs);
                    }
                    JSONArray allOf = schema.getJSONArray("allOf");
                    if (allOf != null && !allOf.isEmpty()) {
                        JSONObject merged = new JSONObject();
                        for (Object item : allOf) {
                            if (item instanceof JSONObject part) {
                                Object value = generateExampleValue(root, part, resolvingRefs);
                                if (value instanceof JSONObject valueObj) {
                                    for (String key : valueObj.keySet()) {
                                        merged.set(key, valueObj.get(key));
                                    }
                                }
                            }
                        }
                        if (!merged.isEmpty()) {
                            return merged;
                        }
                    }
                    return "";
            }
        } finally {
            if (!ref.isBlank()) {
                resolvingRefs.remove(ref);
            }
        }
    }

    private static Object emptyExampleForCircularReference(JSONObject root, JSONObject schema) {
        JSONObject resolvedSchema = resolveRefObject(root, schema);
        return "array".equals(getSchemaType(resolvedSchema)) ? new JSONArray() : new JSONObject();
    }

    /**
     * 获取 schema 类型（支持 OpenAPI 3.1 的 type 数组）
     */
    static String getSchemaType(JSONObject schema) {
        if (schema == null) {
            return "string";
        }

        if (schema.containsKey("oneOf") || schema.containsKey("anyOf") || schema.containsKey("allOf")) {
            return "composed";
        }
        if (schema.containsKey("properties")) {
            return "object";
        }
        if (schema.containsKey("items")) {
            return "array";
        }

        Object typeObj = schema.get("type");
        if (typeObj instanceof String) {
            return (String) typeObj;
        }
        if (typeObj instanceof JSONArray) {
            JSONArray types = (JSONArray) typeObj;
            for (Object t : types) {
                if (!"null".equals(t)) {
                    return (String) t;
                }
            }
        }
        return "string";
    }

    /**
     * 获取示例（优先 examples，再 fallback 到 example）
     */
    static Object getExampleValue(JSONObject schema) {
        if (schema == null) {
            return null;
        }

        if (schema.containsKey("examples")) {
            Object examplesObj = schema.get("examples");
            if (examplesObj instanceof JSONArray examples && !examples.isEmpty()) {
                return examples.get(0);
            }
            if (examplesObj instanceof JSONObject examples && !examples.isEmpty()) {
                Iterator<String> iterator = examples.keySet().iterator();
                if (iterator.hasNext()) {
                    Object first = examples.get(iterator.next());
                    if (first instanceof JSONObject firstObj && firstObj.containsKey("value")) {
                        return firstObj.get("value");
                    }
                    return first;
                }
            }
        }

        if (schema.containsKey("example")) {
            return schema.get("example");
        }

        return null;
    }

    static String toJsonText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        return JSONUtil.toJsonStr(value);
    }

    static JSONObject resolveRefObject(JSONObject root, JSONObject source) {
        return resolveRefObject(root, source, new HashSet<>());
    }

    private static JSONObject resolveRefObject(JSONObject root, JSONObject source, Set<String> resolvingRefs) {
        if (source == null) {
            return null;
        }

        String ref = source.getStr("$ref", "");
        if (ref.isBlank()) {
            return source;
        }
        if (!resolvingRefs.add(ref)) {
            return source;
        }

        try {
            Object resolved = resolveLocalRef(root, ref);
            if (!(resolved instanceof JSONObject resolvedObject)) {
                return source;
            }

            JSONObject merged = JSONUtil.parseObj(resolvedObject.toString());
            for (String key : source.keySet()) {
                if (!"$ref".equals(key)) {
                    merged.set(key, source.get(key));
                }
            }

            if (merged.containsKey("$ref") && !ref.equals(merged.getStr("$ref"))) {
                return resolveRefObject(root, merged, resolvingRefs);
            }
            return merged;
        } finally {
            resolvingRefs.remove(ref);
        }
    }

    static Object resolveLocalRef(JSONObject root, String ref) {
        if (root == null || ref == null || ref.isBlank() || !ref.startsWith("#/")) {
            return null;
        }

        Object current = root;
        String[] parts = ref.substring(2).split("/");
        for (String rawPart : parts) {
            String part = rawPart.replace("~1", "/").replace("~0", "~");
            if (current instanceof JSONObject currentObj) {
                current = currentObj.get(part);
            } else if (current instanceof JSONArray currentArr) {
                try {
                    current = currentArr.get(Integer.parseInt(part));
                } catch (NumberFormatException ex) {
                    return null;
                }
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    static String extractExampleFromMediaType(JSONObject root, JSONObject mediaType) {
        if (mediaType == null) {
            return "";
        }

        Object directExample = getExampleValue(mediaType);
        if (directExample != null) {
            return toJsonText(directExample);
        }

        JSONObject schema = mediaType.getJSONObject("schema");
        if (schema == null) {
            return "";
        }

        return generateExampleFromSchema(root, schema);
    }

    static void upsertHeader(List<HttpHeader> headers, String key, String value) {
        if (headers == null || key == null || key.isBlank()) {
            return;
        }
        for (HttpHeader header : headers) {
            if (header != null && key.equalsIgnoreCase(header.getKey())) {
                header.setValue(value);
                header.setEnabled(true);
                return;
            }
        }
        headers.add(new HttpHeader(true, key, value));
    }

    static String extractExtensionScript(JSONObject source, boolean preScript) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        String[] exactKeys = preScript
                ? new String[]{"x-apipost-pre-script", "x-apipost-prescript", "x-apipost-pre-request-script",
                "x-pre-script", "x-prescript", "x-pre-request-script"}
                : new String[]{"x-apipost-post-script", "x-apipost-postscript", "x-apipost-test-script",
                "x-post-script", "x-postscript", "x-test-script"};

        for (String key : exactKeys) {
            String text = extractScriptText(source.get(key));
            if (!text.isBlank()) {
                return text;
            }
        }

        List<String> matches = new ArrayList<>();
        collectExtensionScriptCandidates(source, preScript, matches);
        return String.join("\n", matches).trim();
    }

    static List<JSONObject> mergeParameters(JSONObject root, JSONObject... containers) {
        List<JSONObject> merged = new ArrayList<>();
        if (containers == null) {
            return merged;
        }

        JSONObject deduped = new JSONObject();
        for (JSONObject container : containers) {
            if (container == null) {
                continue;
            }
            JSONArray parameters = container.getJSONArray("parameters");
            if (parameters == null || parameters.isEmpty()) {
                continue;
            }
            for (Object item : parameters) {
                if (item instanceof JSONObject parameter) {
                    JSONObject resolved = resolveRefObject(root, parameter);
                    if (resolved == null) {
                        continue;
                    }
                    String in = resolved.getStr("in", "");
                    String name = resolved.getStr("name", "");
                    String dedupeKey = in + ":" + name;
                    deduped.set(dedupeKey, resolved);
                }
            }
        }
        for (Object value : deduped.values()) {
            if (value instanceof JSONObject parameter) {
                merged.add(parameter);
            }
        }
        return merged;
    }

    static List<String> extractServerUrlsFromExtensionEnv(JSONObject envObject) {
        List<String> urls = new ArrayList<>();
        if (envObject == null) {
            return urls;
        }

        addNonBlank(urls, envObject.getStr("baseUrl", ""));
        addNonBlank(urls, envObject.getStr("url", ""));
        addNonBlank(urls, envObject.getStr("uri", ""));

        JSONArray serverList = envObject.getJSONArray("server_list");
        if (serverList != null) {
            for (Object serverObj : serverList) {
                if (serverObj instanceof JSONObject server) {
                    addNonBlank(urls, server.getStr("uri", ""));
                    addNonBlank(urls, server.getStr("url", ""));
                }
            }
        }
        return urls;
    }

    static void addVariablesFromUnknownStructure(Environment env, Object variables) {
        if (env == null || variables == null) {
            return;
        }

        if (variables instanceof JSONObject variableMap) {
            for (String key : variableMap.keySet()) {
                Object rawValue = variableMap.get(key);
                if (rawValue instanceof JSONObject valueObject) {
                    String nestedKey = firstNonBlank(valueObject.getStr("key", ""), valueObject.getStr("name", ""), key);
                    String nestedValue = valueObject.getStr("value", "");
                    if (!nestedKey.isBlank()) {
                        env.set(nestedKey, nestedValue);
                    }
                } else {
                    env.set(key, rawValue == null ? "" : String.valueOf(rawValue));
                }
            }
            return;
        }

        if (variables instanceof JSONArray variableArray) {
            for (Object variableObj : variableArray) {
                if (variableObj instanceof JSONObject variable) {
                    String key = firstNonBlank(variable.getStr("key", ""), variable.getStr("name", ""));
                    if (!key.isBlank()) {
                        Object rawValue = variable.get("value");
                        env.set(key, rawValue == null ? "" : String.valueOf(rawValue));
                    }
                }
            }
        }
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static void addNonBlank(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value);
        }
    }

    private static void collectExtensionScriptCandidates(Object node, boolean preScript, List<String> matches) {
        if (node == null) {
            return;
        }

        if (node instanceof JSONObject objectNode) {
            for (String key : objectNode.keySet()) {
                Object value = objectNode.get(key);
                String lowerKey = key.toLowerCase(Locale.ROOT);
                boolean candidateKey = lowerKey.startsWith("x-") || lowerKey.contains("script") || lowerKey.contains("task");
                if (candidateKey && isMatchingScriptKey(lowerKey, preScript)) {
                    String extracted = extractScriptText(value);
                    if (!extracted.isBlank()) {
                        matches.add(extracted);
                    }
                } else if (lowerKey.startsWith("x-")) {
                    collectExtensionScriptCandidates(value, preScript, matches);
                }
            }
            return;
        }

        if (node instanceof JSONArray arrayNode) {
            for (Object item : arrayNode) {
                collectExtensionScriptCandidates(item, preScript, matches);
            }
        }
    }

    private static boolean isMatchingScriptKey(String lowerKey, boolean preScript) {
        boolean hasTarget = preScript
                ? lowerKey.contains("pre") || lowerKey.contains("before")
                : lowerKey.contains("post") || lowerKey.contains("after") || lowerKey.contains("test");
        return hasTarget && (lowerKey.contains("script") || lowerKey.contains("task"));
    }

    private static String extractScriptText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text.trim();
        }
        if (value instanceof JSONObject objectValue) {
            for (String key : new String[]{"script", "content", "code", "value"}) {
                String text = objectValue.getStr(key, "").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
            JSONArray steps = objectValue.getJSONArray("steps");
            if (steps != null) {
                return extractScriptText(steps);
            }
            return "";
        }
        if (value instanceof JSONArray arrayValue) {
            List<String> scripts = new ArrayList<>();
            for (Object item : arrayValue) {
                String text = extractScriptText(item);
                if (!text.isBlank()) {
                    scripts.add(text);
                }
            }
            return String.join("\n", scripts).trim();
        }
        return "";
    }
}
