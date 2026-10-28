package com.laker.postman.performance.core.plan;

import com.laker.postman.performance.core.assertion.AssertionData;
import com.laker.postman.performance.core.config.CsvDataSetData;
import com.laker.postman.performance.core.controller.ConditionData;
import com.laker.postman.performance.core.controller.LoopData;
import com.laker.postman.performance.core.controller.WhileData;
import com.laker.postman.performance.core.extractor.ExtractorData;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.core.model.PerformanceProtocol;
import com.laker.postman.performance.core.model.SsePerformanceData;
import com.laker.postman.performance.core.model.WebSocketPerformanceData;
import com.laker.postman.performance.core.request.PerformanceAuthType;
import com.laker.postman.performance.core.request.PerformanceRequestExecutionScopeSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestFormDataPart;
import com.laker.postman.performance.core.request.PerformanceRequestKeyValue;
import com.laker.postman.performance.core.request.PerformanceRequestSnapshot;
import com.laker.postman.performance.core.request.PerformanceRequestProtocol;
import com.laker.postman.performance.core.threadgroup.ThreadGroupData;
import com.laker.postman.performance.core.timer.TimerData;
import com.laker.postman.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PerformanceCorePlanJsonStorage {
    public static final String FORMAT_VERSION = "1.0";

    public void saveDocument(Path path, PerformanceCorePlanDocument document) throws IOException {
        if (path == null) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toJson(document), StandardCharsets.UTF_8);
    }

    public PerformanceCorePlanDocument loadDocument(Path path) throws IOException {
        if (path == null || !Files.exists(path) || Files.size(path) == 0) {
            return null;
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return fromJson(json);
    }

    public String toJson(PerformanceCorePlanDocument document) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", FORMAT_VERSION);
        root.put("tree", serializePlanNode(document == null ? null : document.getRoot()));
        return JsonUtil.toJsonPrettyStr(root);
    }

    public PerformanceCorePlanDocument fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        Map<String, Object> root = objectMap(JsonUtil.convertValue(JsonUtil.readTree(json), Map.class));
        PerformanceCorePlanNode rootNode = deserializePlanNode(objectMap(root.get("tree")));
        return rootNode == null ? null : new PerformanceCorePlanDocument(rootNode);
    }

    public Map<String, Object> toDocumentMap(PerformanceCorePlanDocument document) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", FORMAT_VERSION);
        root.put("tree", serializePlanNode(document == null ? null : document.getRoot()));
        return root;
    }

    public PerformanceCorePlanDocument fromDocumentMap(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return null;
        }
        PerformanceCorePlanNode rootNode = deserializePlanNode(objectMap(root.get("tree")));
        return rootNode == null ? null : new PerformanceCorePlanDocument(rootNode);
    }

    public Map<String, Object> toTreeMap(PerformanceCorePlanNode root) {
        return serializePlanNode(root);
    }

    public PerformanceCorePlanNode fromTreeMap(Map<String, Object> tree) {
        return deserializePlanNode(tree);
    }

    private Map<String, Object> serializePlanNode(PerformanceCorePlanNode planNode) {
        Map<String, Object> jsonNode = new LinkedHashMap<>();
        if (planNode == null || planNode.getType() == null) {
            return jsonNode;
        }

        jsonNode.put("name", planNode.getName());
        jsonNode.put("type", planNode.getType().name());
        jsonNode.put("enabled", planNode.isEnabled());

        switch (planNode.getType()) {
            case THREAD_GROUP -> putIfNotNull(jsonNode, "threadGroupData",
                    serializeThreadGroupData(planNode.getThreadGroupData()));
            case CSV_DATA_SET -> putIfNotNull(jsonNode, "csvDataSetData",
                    serializeCsvDataSetData(planNode.getCsvDataSetData()));
            case SIMPLE -> {
            }
            case LOOP -> putIfNotNull(jsonNode, "loopData", serializeLoopData(planNode.getLoopData()));
            case CONDITION -> putIfNotNull(jsonNode, "conditionData", serializeConditionData(planNode.getConditionData()));
            case WHILE -> putIfNotNull(jsonNode, "whileData", serializeWhileData(planNode.getWhileData()));
            case ONCE_ONLY -> {
            }
            case REQUEST -> {
                PerformanceRequestSnapshot requestSnapshot = planNode.getRequestSnapshot();
                if (requestSnapshot != null) {
                    jsonNode.put("requestSnapshot", serializeRequestSnapshot(requestSnapshot));
                }
                putIfNotNull(jsonNode, "webSocketPerformanceData",
                        serializeWebSocketPerformanceData(planNode.getWebSocketPerformanceData()));
            }
            case ASSERTION -> putIfNotNull(jsonNode, "assertionData",
                    serializeAssertionData(planNode.getAssertionData()));
            case EXTRACTOR -> putIfNotNull(jsonNode, "extractorData",
                    serializeExtractorData(planNode.getExtractorData()));
            case TIMER -> putIfNotNull(jsonNode, "timerData", serializeTimerData(planNode.getTimerData()));
            case WS_CONNECT, WS_SEND, WS_READ, WS_CLOSE -> putIfNotNull(jsonNode, "webSocketPerformanceData",
                    serializeWebSocketPerformanceData(planNode.getWebSocketPerformanceData()));
            case SSE_CONNECT, SSE_READ -> putIfNotNull(jsonNode, "ssePerformanceData",
                    serializeSsePerformanceData(planNode.getSsePerformanceData()));
            case ROOT -> {
            }
        }

        List<Map<String, Object>> children = new ArrayList<>();
        for (PerformanceCorePlanNode child : planNode.getChildren()) {
            children.add(serializePlanNode(child));
        }
        if (!children.isEmpty()) {
            jsonNode.put("children", children);
        }

        return jsonNode;
    }

    private Map<String, Object> serializeRequestSnapshot(PerformanceRequestSnapshot snapshot) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", snapshot.getId());
        json.put("name", snapshot.getName());
        json.put("description", snapshot.getDescription());
        json.put("url", snapshot.getUrl());
        json.put("method", snapshot.getMethod());
        json.put("protocol", snapshot.getProtocol().name());
        json.put("headers", serializeKeyValues(snapshot.getHeaders()));
        json.put("bodyType", snapshot.getBodyType());
        json.put("body", snapshot.getBody());
        json.put("params", serializeKeyValues(snapshot.getParams()));
        json.put("formData", serializeFormData(snapshot.getFormData()));
        json.put("urlencoded", serializeKeyValues(snapshot.getUrlencoded()));
        json.put("authType", snapshot.getAuthType().name());
        json.put("authUsername", snapshot.getAuthUsername());
        json.put("authPassword", snapshot.getAuthPassword());
        json.put("authToken", snapshot.getAuthToken());
        json.put("authApiKeyName", snapshot.getAuthApiKeyName());
        json.put("authApiKeyValue", snapshot.getAuthApiKeyValue());
        json.put("authApiKeyPlacement", snapshot.getAuthApiKeyPlacement());
        json.put("followRedirects", snapshot.getFollowRedirects());
        json.put("cookieJarEnabled", snapshot.getCookieJarEnabled());
        json.put("proxyPolicy", snapshot.getProxyPolicy());
        json.put("httpVersion", snapshot.getHttpVersion());
        json.put("requestTimeoutMs", snapshot.getRequestTimeoutMs());
        json.put("webSocketPingIntervalMs", snapshot.getWebSocketPingIntervalMs());
        json.put("prescript", snapshot.getPrescript());
        json.put("postscript", snapshot.getPostscript());
        if (snapshot.getExecutionScope() != null && !snapshot.getExecutionScope().getGroupVariables().isEmpty()) {
            json.put("executionScope", new LinkedHashMap<>(snapshot.getExecutionScope().getGroupVariables()));
        }
        return json;
    }

    private List<Map<String, Object>> serializeKeyValues(List<PerformanceRequestKeyValue> values) {
        List<Map<String, Object>> array = new ArrayList<>();
        if (values == null) {
            return array;
        }
        for (PerformanceRequestKeyValue value : values) {
            if (value == null) {
                continue;
            }
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("enabled", value.isEnabled());
            json.put("key", value.getKey());
            json.put("value", value.getValue());
            putIfNotBlank(json, "description", value.getDescription());
            array.add(json);
        }
        return array;
    }

    private List<Map<String, Object>> serializeFormData(List<PerformanceRequestFormDataPart> values) {
        List<Map<String, Object>> array = new ArrayList<>();
        if (values == null) {
            return array;
        }
        for (PerformanceRequestFormDataPart value : values) {
            if (value == null) {
                continue;
            }
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("enabled", value.isEnabled());
            json.put("key", value.getKey());
            json.put("type", value.getType());
            json.put("value", value.getValue());
            putIfNotBlank(json, "description", value.getDescription());
            array.add(json);
        }
        return array;
    }

    private Map<String, Object> serializeThreadGroupData(ThreadGroupData source) {
        ThreadGroupData data = PerformancePlanCoreDataCopies.copyThreadGroupData(source);
        if (data == null) {
            return null;
        }
        data.normalize();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("threadMode", data.threadMode.name());
        json.put("numThreads", data.numThreads);
        json.put("duration", data.duration);
        json.put("loops", data.loops);
        json.put("useTime", data.useTime);
        json.put("maxInFlightWaitSeconds", data.maxInFlightWaitSeconds);
        json.put("rampUpStartThreads", data.rampUpStartThreads);
        json.put("rampUpEndThreads", data.rampUpEndThreads);
        json.put("rampUpTime", data.rampUpTime);
        json.put("rampUpDuration", data.rampUpDuration);
        json.put("spikeMinThreads", data.spikeMinThreads);
        json.put("spikeMaxThreads", data.spikeMaxThreads);
        json.put("spikeRampUpTime", data.spikeRampUpTime);
        json.put("spikeHoldTime", data.spikeHoldTime);
        json.put("spikeRampDownTime", data.spikeRampDownTime);
        json.put("spikeDuration", data.spikeDuration);
        json.put("stairsStartThreads", data.stairsStartThreads);
        json.put("stairsEndThreads", data.stairsEndThreads);
        json.put("stairsStep", data.stairsStep);
        json.put("stairsHoldTime", data.stairsHoldTime);
        json.put("stairsDuration", data.stairsDuration);
        return json;
    }

    private Map<String, Object> serializeLoopData(LoopData source) {
        LoopData data = PerformancePlanCoreDataCopies.copyLoopData(source);
        if (data == null) {
            return null;
        }
        data.normalize();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("iterations", data.iterations);
        return json;
    }

    private Map<String, Object> serializeConditionData(ConditionData source) {
        ConditionData data = PerformancePlanCoreDataCopies.copyConditionData(source);
        if (data == null) {
            return null;
        }
        data.normalize();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("expression", data.expression);
        return json;
    }

    private Map<String, Object> serializeWhileData(WhileData source) {
        WhileData data = PerformancePlanCoreDataCopies.copyWhileData(source);
        if (data == null) {
            return null;
        }
        data.normalize();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("expression", data.expression);
        json.put("intervalMs", data.intervalMs);
        json.put("timeoutMs", data.timeoutMs);
        json.put("maxIterations", data.maxIterations);
        return json;
    }

    private Map<String, Object> serializeCsvDataSetData(CsvDataSetData data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sourceName", data.getSourceName());
        putIfNotBlank(json, "sourceType", data.getSourceType());
        putIfNotBlank(json, "filePath", data.getFilePath());
        putIfNotBlank(json, "encoding", data.getEncoding());
        putIfNotBlank(json, "delimiter", data.getDelimiter());
        json.put("hasHeader", data.isHasHeader());
        putIfNotBlank(json, "sharingMode", data.getSharingMode());
        putIfNotBlank(json, "eofMode", data.getEofMode());
        json.put("headers", new ArrayList<>(data.getHeaders()));

        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, String> row : data.getRows()) {
            rows.add(row == null ? new LinkedHashMap<>() : new LinkedHashMap<>(row));
        }
        json.put("rows", rows);
        return json;
    }

    private Map<String, Object> serializeAssertionData(AssertionData data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", data.type);
        json.put("content", data.content);
        json.put("operator", data.operator);
        json.put("value", data.value);
        return json;
    }

    private Map<String, Object> serializeExtractorData(ExtractorData data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", data.type);
        json.put("expression", data.expression);
        json.put("variableName", data.variableName);
        json.put("defaultValue", data.defaultValue);
        json.put("matchIndex", data.matchIndex);
        json.put("groupIndex", data.groupIndex);
        return json;
    }

    private Map<String, Object> serializeTimerData(TimerData data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("delayMs", data.delayMs);
        return json;
    }

    private Map<String, Object> serializeSsePerformanceData(SsePerformanceData data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("connectTimeoutMs", data.connectTimeoutMs);
        json.put("completionMode", data.completionMode != null
                ? data.completionMode.name()
                : SsePerformanceData.CompletionMode.SINGLE_MESSAGE.name());
        json.put("firstMessageTimeoutMs", data.firstMessageTimeoutMs);
        json.put("holdConnectionMs", data.holdConnectionMs);
        json.put("targetMessageCount", data.targetMessageCount);
        json.put("eventNameFilter", data.eventNameFilter);
        json.put("messageFilter", data.messageFilter);
        return json;
    }

    private Map<String, Object> serializeWebSocketPerformanceData(WebSocketPerformanceData data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("connectTimeoutMs", data.connectTimeoutMs);
        json.put("sendMode", data.sendMode != null
                ? data.sendMode.name()
                : WebSocketPerformanceData.SendMode.REQUEST_BODY_ON_CONNECT.name());
        json.put("sendContentSource", data.sendContentSource != null
                ? data.sendContentSource.name()
                : WebSocketPerformanceData.SendContentSource.REQUEST_BODY.name());
        json.put("customSendBody", data.customSendBody);
        json.put("sendPreScript", data.sendPreScript);
        json.put("sendCount", data.sendCount);
        json.put("sendIntervalMs", data.sendIntervalMs);
        json.put("completionMode", data.completionMode != null
                ? data.completionMode.name()
                : WebSocketPerformanceData.CompletionMode.SINGLE_MESSAGE.name());
        json.put("firstMessageTimeoutMs", data.firstMessageTimeoutMs);
        json.put("holdConnectionMs", data.holdConnectionMs);
        json.put("targetMessageCount", data.targetMessageCount);
        json.put("messageFilter", data.messageFilter);
        return json;
    }

    private PerformanceCorePlanNode deserializePlanNode(Map<String, Object> jsonNode) {
        if (jsonNode == null || jsonNode.isEmpty()) {
            return null;
        }

        String name = stringValue(jsonNode, "name", null);
        NodeType nodeType = enumValue(NodeType.class, stringValue(jsonNode, "type", null), null);
        if (name == null || nodeType == null) {
            return null;
        }

        var builder = PerformanceCorePlanNode.builder()
                .name(name)
                .type(nodeType)
                .enabled(booleanValue(jsonNode, "enabled", true));

        switch (nodeType) {
            case THREAD_GROUP -> builder.threadGroupData(deserializeThreadGroupData(objectMap(jsonNode.get("threadGroupData"))));
            case CSV_DATA_SET -> builder.csvDataSetData(deserializeCsvDataSetData(objectMap(jsonNode.get("csvDataSetData"))));
            case SIMPLE -> {
            }
            case LOOP -> builder.loopData(deserializeLoopData(objectMap(jsonNode.get("loopData"))));
            case CONDITION -> builder.conditionData(deserializeConditionData(objectMap(jsonNode.get("conditionData"))));
            case WHILE -> builder.whileData(deserializeWhileData(objectMap(jsonNode.get("whileData"))));
            case ONCE_ONLY -> {
            }
            case REQUEST -> {
                builder.requestSnapshot(deserializeRequestSnapshot(objectMap(jsonNode.get("requestSnapshot"))));
                builder.webSocketPerformanceData(deserializeWebSocketPerformanceData(objectMap(jsonNode.get("webSocketPerformanceData"))));
            }
            case ASSERTION -> builder.assertionData(deserializeAssertionData(objectMap(jsonNode.get("assertionData"))));
            case EXTRACTOR -> builder.extractorData(deserializeExtractorData(objectMap(jsonNode.get("extractorData"))));
            case TIMER -> builder.timerData(deserializeTimerData(objectMap(jsonNode.get("timerData"))));
            case WS_CONNECT, WS_SEND, WS_READ, WS_CLOSE -> builder.webSocketPerformanceData(
                    deserializeWebSocketPerformanceData(objectMap(jsonNode.get("webSocketPerformanceData"))));
            case SSE_CONNECT, SSE_READ -> builder.ssePerformanceData(
                    deserializeSsePerformanceData(objectMap(jsonNode.get("ssePerformanceData"))));
            case ROOT -> {
            }
        }

        List<PerformanceCorePlanNode> childNodes = new ArrayList<>();
        for (Object child : listValue(jsonNode.get("children"))) {
            PerformanceCorePlanNode childNode = deserializePlanNode(objectMap(child));
            if (childNode != null) {
                childNodes.add(childNode);
            }
        }

        return builder.children(childNodes).build();
    }

    private PerformanceRequestSnapshot deserializeRequestSnapshot(Map<String, Object> requestJson) {
        if (requestJson == null || requestJson.isEmpty()) {
            return null;
        }
        return PerformanceRequestSnapshot.builder()
                .id(stringValue(requestJson, "id", null))
                .name(stringValue(requestJson, "name", null))
                .description(stringValue(requestJson, "description", null))
                .url(stringValue(requestJson, "url", null))
                .method(stringValue(requestJson, "method", null))
                .protocol(deserializeRequestProtocol(stringValue(requestJson, "protocol", null)))
                .headers(deserializeRequestKeyValues(listValue(requestJson.get("headers"))))
                .bodyType(stringValue(requestJson, "bodyType", null))
                .body(stringValue(requestJson, "body", null))
                .params(deserializeRequestKeyValues(listValue(requestJson.get("params"))))
                .formData(deserializeRequestFormData(listValue(requestJson.get("formData"))))
                .urlencoded(deserializeRequestKeyValues(listValue(requestJson.get("urlencoded"))))
                .authType(enumValue(
                        PerformanceAuthType.class,
                        stringValue(requestJson, "authType", null),
                        PerformanceAuthType.INHERIT
                ))
                .authUsername(stringValue(requestJson, "authUsername", null))
                .authPassword(stringValue(requestJson, "authPassword", null))
                .authToken(stringValue(requestJson, "authToken", null))
                .authApiKeyName(stringValue(requestJson, "authApiKeyName", null))
                .authApiKeyValue(stringValue(requestJson, "authApiKeyValue", null))
                .authApiKeyPlacement(stringValue(requestJson, "authApiKeyPlacement", null))
                .followRedirects(booleanObjectValue(requestJson, "followRedirects"))
                .cookieJarEnabled(booleanObjectValue(requestJson, "cookieJarEnabled"))
                .proxyPolicy(stringValue(requestJson, "proxyPolicy", null))
                .httpVersion(stringValue(requestJson, "httpVersion", null))
                .requestTimeoutMs(integerObjectValue(requestJson, "requestTimeoutMs"))
                .webSocketPingIntervalMs(integerObjectValue(requestJson, "webSocketPingIntervalMs"))
                .prescript(stringValue(requestJson, "prescript", null))
                .postscript(stringValue(requestJson, "postscript", null))
                .executionScope(deserializeRequestExecutionScopeSnapshot(objectMap(requestJson.get("executionScope"))))
                .build();
    }

    private PerformanceRequestProtocol deserializeRequestProtocol(String value) {
        // Older exported plans stored SSE as a request protocol. SSE now belongs
        // to the configured performance stages, while the request itself is HTTP.
        if ("SSE".equalsIgnoreCase(value)) {
            return PerformanceRequestProtocol.HTTP;
        }
        return enumValue(PerformanceRequestProtocol.class, value, PerformanceRequestProtocol.HTTP);
    }

    private List<PerformanceRequestKeyValue> deserializeRequestKeyValues(List<Object> array) {
        if (array.isEmpty()) {
            return List.of();
        }
        List<PerformanceRequestKeyValue> values = new ArrayList<>();
        for (Object item : array) {
            Map<String, Object> json = objectMap(item);
            if (!json.isEmpty()) {
                values.add(new PerformanceRequestKeyValue(
                        booleanValue(json, "enabled", true),
                        stringValue(json, "key", ""),
                        stringValue(json, "value", ""),
                        stringValue(json, "description", "")
                ));
            }
        }
        return values;
    }

    private List<PerformanceRequestFormDataPart> deserializeRequestFormData(List<Object> array) {
        if (array.isEmpty()) {
            return List.of();
        }
        List<PerformanceRequestFormDataPart> values = new ArrayList<>();
        for (Object item : array) {
            Map<String, Object> json = objectMap(item);
            if (!json.isEmpty()) {
                values.add(new PerformanceRequestFormDataPart(
                        booleanValue(json, "enabled", true),
                        stringValue(json, "key", ""),
                        stringValue(json, "type", PerformanceRequestFormDataPart.TYPE_TEXT),
                        stringValue(json, "value", ""),
                        stringValue(json, "description", "")
                ));
            }
        }
        return values;
    }

    private PerformanceRequestExecutionScopeSnapshot deserializeRequestExecutionScopeSnapshot(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return PerformanceRequestExecutionScopeSnapshot.empty();
        }
        Map<String, String> groupVariables = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            groupVariables.put(entry.getKey(), stringValue(entry.getValue(), ""));
        }
        return PerformanceRequestExecutionScopeSnapshot.fromGroupVariables(groupVariables);
    }

    private ThreadGroupData deserializeThreadGroupData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        ThreadGroupData data = new ThreadGroupData();
        data.threadMode = enumValue(
                ThreadGroupData.ThreadMode.class,
                stringValue(json, "threadMode", null),
                data.threadMode
        );
        data.numThreads = intValue(json, "numThreads", data.numThreads);
        data.duration = intValue(json, "duration", data.duration);
        data.loops = intValue(json, "loops", data.loops);
        data.useTime = booleanValue(json, "useTime", data.useTime);
        data.maxInFlightWaitSeconds = intValue(
                json,
                "maxInFlightWaitSeconds",
                data.maxInFlightWaitSeconds
        );
        data.rampUpStartThreads = intValue(json, "rampUpStartThreads", data.rampUpStartThreads);
        data.rampUpEndThreads = intValue(json, "rampUpEndThreads", data.rampUpEndThreads);
        data.rampUpTime = intValue(json, "rampUpTime", data.rampUpTime);
        data.rampUpDuration = intValue(json, "rampUpDuration", data.rampUpDuration);
        data.spikeMinThreads = intValue(json, "spikeMinThreads", data.spikeMinThreads);
        data.spikeMaxThreads = intValue(json, "spikeMaxThreads", data.spikeMaxThreads);
        data.spikeRampUpTime = intValue(json, "spikeRampUpTime", data.spikeRampUpTime);
        data.spikeHoldTime = intValue(json, "spikeHoldTime", data.spikeHoldTime);
        data.spikeRampDownTime = intValue(json, "spikeRampDownTime", data.spikeRampDownTime);
        data.spikeDuration = intValue(json, "spikeDuration", data.spikeDuration);
        data.stairsStartThreads = intValue(json, "stairsStartThreads", data.stairsStartThreads);
        data.stairsEndThreads = intValue(json, "stairsEndThreads", data.stairsEndThreads);
        data.stairsStep = intValue(json, "stairsStep", data.stairsStep);
        data.stairsHoldTime = intValue(json, "stairsHoldTime", data.stairsHoldTime);
        data.stairsDuration = intValue(json, "stairsDuration", data.stairsDuration);
        data.normalize();
        return data;
    }

    private LoopData deserializeLoopData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        LoopData data = new LoopData();
        data.iterations = intValue(json, "iterations", data.iterations);
        data.normalize();
        return data;
    }

    private ConditionData deserializeConditionData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        ConditionData data = new ConditionData();
        data.expression = stringValue(json, "expression", data.expression);
        data.normalize();
        return data;
    }

    private WhileData deserializeWhileData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        WhileData data = new WhileData();
        data.expression = stringValue(json, "expression", data.expression);
        data.intervalMs = intValue(json, "intervalMs", data.intervalMs);
        data.timeoutMs = intValue(json, "timeoutMs", data.timeoutMs);
        data.maxIterations = intValue(json, "maxIterations", data.maxIterations);
        data.normalize();
        return data;
    }

    private CsvDataSetData deserializeCsvDataSetData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        List<String> headers = new ArrayList<>();
        for (Object header : listValue(json.get("headers"))) {
            headers.add(stringValue(header, ""));
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (Object rowValue : listValue(json.get("rows"))) {
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : objectMap(rowValue).entrySet()) {
                row.put(entry.getKey(), stringValue(entry.getValue(), ""));
            }
            rows.add(row);
        }

        CsvDataSetData data = new CsvDataSetData(stringValue(json, "sourceName", null), headers, rows);
        data.setSourceType(stringValue(json, "sourceType", CsvDataSetData.SOURCE_INLINE));
        data.setFilePath(stringValue(json, "filePath", null));
        data.setEncoding(stringValue(json, "encoding", "UTF-8"));
        data.setDelimiter(stringValue(json, "delimiter", ","));
        data.setHasHeader(booleanValue(json, "hasHeader", true));
        data.setSharingMode(stringValue(json, "sharingMode", CsvDataSetData.SHARING_THREAD_GROUP));
        data.setEofMode(stringValue(json, "eofMode", CsvDataSetData.EOF_RECYCLE));
        return data;
    }

    private AssertionData deserializeAssertionData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        AssertionData data = new AssertionData();
        data.type = stringValue(json, "type", data.type);
        data.content = stringValue(json, "content", data.content);
        data.operator = stringValue(json, "operator", data.operator);
        data.value = stringValue(json, "value", data.value);
        return data;
    }

    private ExtractorData deserializeExtractorData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        ExtractorData data = new ExtractorData();
        data.type = stringValue(json, "type", data.type);
        data.expression = stringValue(json, "expression", data.expression);
        data.variableName = stringValue(json, "variableName", data.variableName);
        data.defaultValue = stringValue(json, "defaultValue", data.defaultValue);
        data.matchIndex = intValue(json, "matchIndex", data.matchIndex);
        data.groupIndex = intValue(json, "groupIndex", data.groupIndex);
        return data;
    }

    private TimerData deserializeTimerData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        TimerData data = new TimerData();
        data.delayMs = intValue(json, "delayMs", data.delayMs);
        return data;
    }

    private SsePerformanceData deserializeSsePerformanceData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        SsePerformanceData data = new SsePerformanceData();
        data.connectTimeoutMs = intValue(json, "connectTimeoutMs", data.connectTimeoutMs);
        data.completionMode = enumValue(
                SsePerformanceData.CompletionMode.class,
                stringValue(json, "completionMode", null),
                data.completionMode
        );
        data.firstMessageTimeoutMs = intValue(json, "firstMessageTimeoutMs", data.firstMessageTimeoutMs);
        data.holdConnectionMs = intValue(json, "holdConnectionMs", data.holdConnectionMs);
        data.targetMessageCount = intValue(json, "targetMessageCount", data.targetMessageCount);
        data.eventNameFilter = stringValue(json, "eventNameFilter", data.eventNameFilter);
        data.messageFilter = stringValue(json, "messageFilter", data.messageFilter);
        return data;
    }

    private WebSocketPerformanceData deserializeWebSocketPerformanceData(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        WebSocketPerformanceData data = new WebSocketPerformanceData();
        data.connectTimeoutMs = intValue(json, "connectTimeoutMs", data.connectTimeoutMs);
        data.sendMode = enumValue(
                WebSocketPerformanceData.SendMode.class,
                stringValue(json, "sendMode", null),
                data.sendMode
        );
        data.sendContentSource = enumValue(
                WebSocketPerformanceData.SendContentSource.class,
                stringValue(json, "sendContentSource", null),
                data.sendContentSource
        );
        data.customSendBody = stringValue(json, "customSendBody", data.customSendBody);
        data.sendPreScript = stringValue(json, "sendPreScript", data.sendPreScript);
        data.sendCount = intValue(json, "sendCount", data.sendCount);
        data.sendIntervalMs = intValue(json, "sendIntervalMs", data.sendIntervalMs);
        data.completionMode = enumValue(
                WebSocketPerformanceData.CompletionMode.class,
                stringValue(json, "completionMode", null),
                data.completionMode
        );
        data.firstMessageTimeoutMs = intValue(json, "firstMessageTimeoutMs", data.firstMessageTimeoutMs);
        data.holdConnectionMs = intValue(json, "holdConnectionMs", data.holdConnectionMs);
        data.targetMessageCount = intValue(json, "targetMessageCount", data.targetMessageCount);
        data.messageFilter = stringValue(json, "messageFilter", data.messageFilter);
        return data;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listValue(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String stringValue(Map<String, Object> json, String key, String defaultValue) {
        return stringValue(json.get(key), defaultValue);
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static int intValue(Map<String, Object> json, String key, int defaultValue) {
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

    private static Integer integerObjectValue(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean booleanValue(Map<String, Object> json, String key, boolean defaultValue) {
        Boolean value = booleanObjectValue(json, key);
        return value == null ? defaultValue : value;
    }

    private static Boolean booleanObjectValue(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, value);
    }
}
