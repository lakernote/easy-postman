package com.laker.postman.performance.plan;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.plan.PerformanceCorePlanDocument;
import com.laker.postman.performance.core.plan.PerformanceCorePlanJsonStorage;
import com.laker.postman.performance.core.plan.PerformanceCorePlanNode;
import com.laker.postman.util.JsonUtil;
import com.laker.postman.util.SafeFileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;

@Slf4j
public class PerformancePlanStorage {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String DEFAULT_PLAN_ID = "default";
    private static final String DEFAULT_PLAN_NAME = "Test Plan";
    private final PerformanceCorePlanJsonStorage corePlanJsonStorage = new PerformanceCorePlanJsonStorage();

    public void saveConfiguration(Path configPath, PerformancePlanConfiguration configuration) {
        PerformancePlanWorkspace workspace = readWorkspace(configPath, false);
        PerformancePlanWorkspace updatedWorkspace = (workspace == null
                ? PerformancePlanWorkspace.builder().build()
                : workspace)
                .updateActiveConfiguration(configuration, DEFAULT_PLAN_NAME);
        saveWorkspace(configPath, updatedWorkspace);
    }

    public void saveWorkspace(Path configPath, PerformancePlanWorkspace workspace) {
        saveWorkspace(configPath, workspace, false);
    }

    /**
     * @param force {@code false} 时内容与加载基准相同则不写盘，避免仅因切换/退出就重新生成被删除的文件或覆盖外部修改
     */
    public void saveWorkspace(Path configPath, PerformancePlanWorkspace workspace, boolean force) {
        if (configPath == null) {
            return;
        }
        try {
            ensureDirExists(configPath);
            String json = toJson(workspace);
            if (force) {
                SafeFileUtil.writeStringAtomically(configPath, json);
            } else {
                SafeFileUtil.writeIfChanged(configPath, json);
            }
        } catch (IOException e) {
            log.error("Failed to save performance test config: {}", e.getMessage(), e);
        }
    }

    /**
     * 以界面当前加载出的方案作为保存基准。
     */
    public void rememberLoaded(Path configPath, PerformancePlanWorkspace workspace) {
        if (configPath != null) {
            SafeFileUtil.rememberLoaded(configPath, toJson(workspace));
        }
    }

    private String toJson(PerformancePlanWorkspace workspace) {
        PerformancePlanWorkspace safeWorkspace = normalizeWorkspace(workspace);
        JSONObject jsonRoot = new JSONObject();
        jsonRoot.set("version", "1.1");
        jsonRoot.set("activePlanId", safeWorkspace.getActivePlanId());
        List<JSONObject> planJsonList = new ArrayList<>();
        for (PerformanceSavedPlan plan : safeWorkspace.getPlans()) {
            planJsonList.add(toPlanJson(plan));
        }
        jsonRoot.set("plans", planJsonList);
        return JSONUtil.toJsonPrettyStr(jsonRoot);
    }

    public PerformancePlanConfiguration loadConfiguration(Path configPath) {
        PerformancePlanWorkspace workspace = readWorkspace(configPath, false);
        return workspace == null ? null : workspace.getActiveConfiguration();
    }

    /**
     * 界面加载：读取的版本会作为之后保存的基准（配合 {@link #rememberLoaded}）。
     */
    public PerformancePlanWorkspace loadWorkspace(Path configPath) {
        return readWorkspace(configPath, true);
    }

    private PerformancePlanWorkspace readWorkspace(Path configPath, boolean asBaseline) {
        if (configPath == null) {
            return null;
        }
        File file = configPath.toFile();
        try {
            if (file.isFile() && file.length() > MAX_FILE_SIZE) {
                log.warn("Config file is too large ({} bytes), ignoring it and keeping the file unchanged", file.length());
                SafeFileUtil.backupUnreadable(configPath);
                return null;
            }
            Optional<String> content = asBaseline
                    ? SafeFileUtil.readForLoad(configPath)
                    : (file.isFile() ? Optional.of(Files.readString(configPath, StandardCharsets.UTF_8)) : Optional.empty());
            if (content.isEmpty() || content.get().trim().isEmpty()) {
                return null;
            }
            String jsonString = content.get();

            JSONObject jsonRoot = JSONUtil.parseObj(jsonString);
            return deserializeWorkspace(jsonRoot);
        } catch (Exception e) {
            log.error("Failed to load performance test config, keeping the file unchanged: {}", e.getMessage(), e);
            SafeFileUtil.backupUnreadable(configPath);
            return null;
        }
    }

    public PerformancePlanWorkspace loadWorkspaceFromJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        return deserializeWorkspace(JSONUtil.parseObj(jsonString));
    }

    public void clear(Path configPath) {
        if (configPath == null) {
            return;
        }
        File file = configPath.toFile();
        if (file.exists()) {
            deleteFile(file);
        }
    }

    private void ensureDirExists(Path configPath) throws IOException {
        Path configDir = configPath.getParent();
        if (configDir != null && !Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }
    }

    private PerformancePlanWorkspace deserializeWorkspace(JSONObject jsonRoot) {
        List<PerformanceSavedPlan> plans = new ArrayList<>();
        Object plansValue = jsonRoot.get("plans");
        if (!(plansValue instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Performance config must contain plans[]");
        }
        for (Object planValue : iterable) {
            if (planValue instanceof JSONObject planJson) {
                plans.add(deserializePlan(planJson));
            } else if (planValue instanceof Map<?, ?> planMap) {
                plans.add(deserializePlan(new JSONObject(planMap)));
            } else {
                throw new IllegalArgumentException("Performance plan entry must be an object");
            }
        }
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("Performance config must contain at least one plan");
        }

        return PerformancePlanWorkspace.builder()
                .activePlanId(requiredString(jsonRoot, "activePlanId"))
                .plans(plans)
                .build();
    }

    private PerformanceSavedPlan deserializePlan(JSONObject jsonRoot) {
        JSONObject treeJson = jsonRoot.getJSONObject("tree");
        if (treeJson == null) {
            throw new IllegalArgumentException("Performance plan must contain tree");
        }
        Map<String, Object> treeMap = jsonObjectToMap(treeJson);
        PerformanceCorePlanNode coreRootNode = corePlanJsonStorage.fromTreeMap(treeMap);
        if (coreRootNode == null) {
            throw new IllegalArgumentException("Performance plan tree is invalid");
        }
        PerformancePlanDocument document = PerformanceCorePlanAdapter.toAppDocument(new PerformanceCorePlanDocument(coreRootNode));

        PerformancePlanConfiguration configuration = PerformancePlanConfiguration.builder()
                .planDocument(document)
                .efficientMode(requiredBoolean(jsonRoot, "efficientMode"))
                .trendEnabled(requiredBoolean(jsonRoot, "trendEnabled"))
                .reportRealtimeEnabled(requiredBoolean(jsonRoot, "reportRealtimeEnabled"))
                .remoteWorkerSettings(PerformanceRemoteWorkerSettings.builder()
                        .enabled(requiredBoolean(jsonRoot, "remoteExecutionEnabled"))
                        .workerEndpoints(requiredString(jsonRoot, "remoteWorkers"))
                        .build())
                .build();
        return PerformanceSavedPlan.fromConfiguration(
                requiredString(jsonRoot, "id"),
                requiredString(jsonRoot, "name"),
                configuration
        );
    }

    private JSONObject toPlanJson(PerformanceSavedPlan plan) {
        JSONObject planJson = new JSONObject();
        PerformanceSavedPlan safePlan = plan == null ? PerformanceSavedPlan.builder().build() : plan;
        planJson.set("id", safePlan.getId());
        planJson.set("name", safePlan.getName());
        writeConfiguration(planJson, safePlan.toConfiguration());
        return planJson;
    }

    private void writeConfiguration(JSONObject json, PerformancePlanConfiguration configuration) {
        PerformancePlanConfiguration safeConfiguration = configuration == null
                ? PerformancePlanConfiguration.builder().build()
                : configuration;
        json.set("efficientMode", safeConfiguration.isEfficientMode());
        json.set("trendEnabled", safeConfiguration.isTrendEnabled());
        json.set("reportRealtimeEnabled", safeConfiguration.isReportRealtimeEnabled());
        PerformanceRemoteWorkerSettings remoteWorkerSettings = safeConfiguration.getRemoteWorkerSettings();
        json.set("remoteExecutionEnabled", remoteWorkerSettings.isEnabled());
        json.set("remoteWorkers", remoteWorkerSettings.getWorkerEndpoints());
        PerformancePlanDocument document = safeConfiguration.getPlanDocument();
        if (document == null) {
            document = defaultDocument();
        }
        PerformanceCorePlanDocument coreDocument = PerformanceCorePlanAdapter.toCoreDocument(document);
        json.set("tree", corePlanJsonStorage.toTreeMap(coreDocument == null ? null : coreDocument.getRoot()));
    }

    private PerformancePlanWorkspace normalizeWorkspace(PerformancePlanWorkspace workspace) {
        if (workspace != null && !workspace.getPlans().isEmpty()) {
            return workspace;
        }
        PerformanceSavedPlan defaultPlan = PerformanceSavedPlan.builder()
                .id(DEFAULT_PLAN_ID)
                .name(DEFAULT_PLAN_NAME)
                .planDocument(defaultDocument())
                .build();
        return PerformancePlanWorkspace.builder()
                .activePlanId(defaultPlan.getId())
                .plans(List.of(defaultPlan))
                .build();
    }

    private PerformancePlanDocument defaultDocument() {
        return new PerformancePlanDocument(PerformancePlanNode.builder()
                .name(DEFAULT_PLAN_NAME)
                .type(NodeType.ROOT)
                .build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonObjectToMap(JSONObject json) {
        return JsonUtil.convertValue(JsonUtil.readTree(json.toString()), Map.class);
    }

    private String requiredString(JSONObject json, String key) {
        String value = json.getStr(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required performance config field: " + key);
        }
        return value;
    }

    private boolean requiredBoolean(JSONObject json, String key) {
        Boolean value = json.getBool(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required performance config field: " + key);
        }
        return value;
    }

    private void deleteFile(File file) {
        try {
            if (file.exists() && !file.delete()) {
                log.warn("Failed to delete config file: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Error deleting config file: {}", e.getMessage());
        }
    }

}
