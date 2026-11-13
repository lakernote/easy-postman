package com.laker.postman.performance.core.run;

import com.laker.postman.performance.core.plan.PerformanceCorePlanDocument;
import com.laker.postman.performance.core.plan.PerformanceCorePlanJsonStorage;
import com.laker.postman.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PerformanceRunPlanJsonStorage {
    public static final String FORMAT_VERSION = "1.0";

    private final PerformanceCorePlanJsonStorage planJsonStorage = new PerformanceCorePlanJsonStorage();

    public void save(Path path, PerformanceRunPlan plan) throws IOException {
        if (path == null) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toJson(plan), StandardCharsets.UTF_8);
    }

    public PerformanceRunPlan load(Path path) throws IOException {
        if (path == null || !Files.exists(path) || Files.size(path) == 0) {
            return null;
        }
        return fromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    public String toJson(PerformanceRunPlan plan) {
        return JsonUtil.toJsonPrettyStr(toMap(plan));
    }

    public PerformanceRunPlan fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        Map<String, Object> root = objectMap(JsonUtil.convertValue(JsonUtil.readTree(json), Map.class));
        return PerformanceRunPlan.builder()
                .schemaVersion(stringValue(root, "schemaVersion", FORMAT_VERSION))
                .generatedBy(stringValue(root, "generatedBy", ""))
                .generatedAt(stringValue(root, "generatedAt", null))
                .environment(readEnvironment(objectMap(root.get("environment"))))
                .globals(new PerformanceRunVariableSet(readVariables(listValue(objectMap(root.get("globals")).get("variables")))))
                .settings(readSettings(objectMap(root.get("settings"))))
                .testPlan(readTestPlan(objectMap(root.get("testPlan"))))
                .assets(readAssets(listValue(root.get("assets"))))
                .build();
    }

    private Map<String, Object> toMap(PerformanceRunPlan plan) {
        PerformanceRunPlan safePlan = plan == null ? PerformanceRunPlan.builder().build() : plan;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", safePlan.getSchemaVersion());
        root.put("generatedBy", safePlan.getGeneratedBy());
        root.put("generatedAt", safePlan.getGeneratedAt());
        root.put("environment", environmentToMap(safePlan.getEnvironment()));
        root.put("globals", variableSetToMap(safePlan.getGlobals()));
        root.put("settings", settingsToMap(safePlan.getSettings()));
        root.put("testPlan", planJsonStorage.toDocumentMap(safePlan.getTestPlan()));
        root.put("assets", assetsToList(safePlan.getAssets()));
        return root;
    }

    private Map<String, Object> environmentToMap(PerformanceRunEnvironment environment) {
        PerformanceRunEnvironment safeEnvironment = environment == null ? PerformanceRunEnvironment.empty() : environment;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", safeEnvironment.getId());
        json.put("name", safeEnvironment.getName());
        json.put("variables", variablesToList(safeEnvironment.getVariables()));
        return json;
    }

    private Map<String, Object> variableSetToMap(PerformanceRunVariableSet variableSet) {
        PerformanceRunVariableSet safeSet = variableSet == null ? PerformanceRunVariableSet.empty() : variableSet;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("variables", variablesToList(safeSet.getVariables()));
        return json;
    }

    private List<Map<String, Object>> variablesToList(List<PerformanceRunVariable> variables) {
        List<Map<String, Object>> array = new ArrayList<>();
        if (variables == null) {
            return array;
        }
        for (PerformanceRunVariable variable : variables) {
            if (variable == null) {
                continue;
            }
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("enabled", variable.isEnabled());
            json.put("key", variable.getKey());
            json.put("value", variable.getValue());
            array.add(json);
        }
        return array;
    }

    private Map<String, Object> settingsToMap(PerformanceRunSettings settings) {
        PerformanceRunSettings safeSettings = settings == null ? PerformanceRunSettings.defaults() : settings;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("efficientMode", safeSettings.isEfficientMode());
        json.put("httpMaxIdleConnections", safeSettings.getHttpMaxIdleConnections());
        json.put("httpKeepAliveSeconds", safeSettings.getHttpKeepAliveSeconds());
        json.put("httpMaxRequests", safeSettings.getHttpMaxRequests());
        json.put("httpMaxRequestsPerHost", safeSettings.getHttpMaxRequestsPerHost());
        json.put("responseBodyPreviewLimitKb", safeSettings.getResponseBodyPreviewLimitKb());
        return json;
    }

    private List<Map<String, Object>> assetsToList(List<PerformanceRunAsset> assets) {
        List<Map<String, Object>> array = new ArrayList<>();
        if (assets == null) {
            return array;
        }
        for (PerformanceRunAsset asset : assets) {
            if (asset == null) {
                continue;
            }
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", asset.getId());
            json.put("type", asset.getType());
            json.put("path", asset.getPath());
            json.put("sha256", asset.getSha256());
            array.add(json);
        }
        return array;
    }

    private PerformanceRunEnvironment readEnvironment(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return PerformanceRunEnvironment.empty();
        }
        return new PerformanceRunEnvironment(
                stringValue(json, "id", null),
                stringValue(json, "name", null),
                readVariables(listValue(json.get("variables")))
        );
    }

    private PerformanceRunSettings readSettings(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return PerformanceRunSettings.defaults();
        }
        return PerformanceRunSettings.builder()
                .efficientMode(booleanValue(json, "efficientMode", true))
                .httpMaxIdleConnections(intValue(json, "httpMaxIdleConnections",
                        PerformanceRunSettings.DEFAULT_HTTP_MAX_IDLE_CONNECTIONS))
                .httpKeepAliveSeconds(longValue(json, "httpKeepAliveSeconds",
                        PerformanceRunSettings.DEFAULT_HTTP_KEEP_ALIVE_SECONDS))
                .httpMaxRequests(intValue(json, "httpMaxRequests",
                        PerformanceRunSettings.DEFAULT_HTTP_MAX_REQUESTS))
                .httpMaxRequestsPerHost(intValue(json, "httpMaxRequestsPerHost",
                        PerformanceRunSettings.DEFAULT_HTTP_MAX_REQUESTS_PER_HOST))
                .responseBodyPreviewLimitKb(intValue(json, "responseBodyPreviewLimitKb",
                        PerformanceRunSettings.DEFAULT_RESPONSE_BODY_PREVIEW_LIMIT_KB))
                .build();
    }

    private PerformanceCorePlanDocument readTestPlan(Map<String, Object> json) {
        return planJsonStorage.fromDocumentMap(json);
    }

    private List<PerformanceRunVariable> readVariables(List<Object> values) {
        List<PerformanceRunVariable> variables = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> json = objectMap(value);
            if (!json.isEmpty()) {
                variables.add(new PerformanceRunVariable(
                        booleanValue(json, "enabled", true),
                        stringValue(json, "key", ""),
                        stringValue(json, "value", "")
                ));
            }
        }
        return variables;
    }

    private List<PerformanceRunAsset> readAssets(List<Object> values) {
        List<PerformanceRunAsset> assets = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> json = objectMap(value);
            if (!json.isEmpty()) {
                assets.add(new PerformanceRunAsset(
                        stringValue(json, "id", ""),
                        stringValue(json, "type", PerformanceRunAsset.TYPE_FILE),
                        stringValue(json, "path", ""),
                        stringValue(json, "sha256", null)
                ));
            }
        }
        return assets;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Object> listValue(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    private String stringValue(Map<String, Object> json, String key, String defaultValue) {
        Object value = json.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private boolean booleanValue(Map<String, Object> json, String key, boolean defaultValue) {
        Object value = json.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private int intValue(Map<String, Object> json, String key, int defaultValue) {
        Object value = json.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private long longValue(Map<String, Object> json, String key, long defaultValue) {
        Object value = json.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
