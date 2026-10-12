package com.laker.postman.service;

import com.laker.postman.functional.model.FunctionalConfigRow;
import com.laker.postman.functional.model.FunctionalConfigSnapshot;
import com.laker.postman.model.Workspace;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.common.constants.ConfigPathConstants;
import com.laker.postman.functional.model.FunctionalCsvDataState;
import com.laker.postman.ioc.Component;
import com.laker.postman.ioc.PostConstruct;
import com.laker.postman.ioc.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 功能测试配置持久化服务
 * 用于保存和加载功能测试面板中的请求配置
 */
@Slf4j
@Component
public class FunctionalPersistenceService {
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024; // 2MB
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "functional-config-save");
        thread.setDaemon(true);
        return thread;
    });

    public FunctionalPersistenceService() {
    }

    @PostConstruct
    public void init() {
        ensureDirExists();
    }

    private void ensureDirExists() {
        ensureDirExists(getConfigFilePath());
    }

    private void ensureDirExists(Path configPath) {
        try {
            Path configDir = configPath.getParent();
            if (configDir != null && !Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
        } catch (IOException e) {
            log.error("Failed to create config directory: {}", e.getMessage());
        }
    }

    /**
     * 保存功能测试配置
     * 只保存请求ID引用，不保存完整配置，确保与集合中的请求保持同步
     */
    public void save(FunctionalConfigSnapshot snapshot) {
        Path configPath = getConfigFilePath();
        writeAndWait(() -> writeSnapshot(configPath, snapshot));
    }

    private void writeSnapshot(Path configPath, FunctionalConfigSnapshot snapshot) {
        try {
            ensureDirExists(configPath);
            FunctionalConfigSnapshot safeSnapshot = snapshot == null ? FunctionalConfigSnapshot.empty() : snapshot;
            JSONObject root = new JSONObject();
            root.set("version", "1.0");
            root.set("rows", serializeRows(safeSnapshot.getRows()));
            if (safeSnapshot.getCsvState() != null) {
                root.set("csvState", serializeCsvState(safeSnapshot.getCsvState()));
            }

            // 写入文件
            String jsonString = JSONUtil.toJsonPrettyStr(root);
            Files.writeString(configPath, jsonString, StandardCharsets.UTF_8);

            log.info("Successfully saved {} functional test configurations", safeSnapshot.getRows().size());
        } catch (IOException e) {
            log.error("Failed to save functional test config: {}", e.getMessage(), e);
        }
    }

    /**
     * 异步保存配置
     */
    public void saveAsync(FunctionalConfigSnapshot snapshot) {
        // 提交保存任务前先固定路径，防止用户切换 workspace 后把旧数据写到新 workspace。
        Path configPath = getConfigFilePath();
        saveExecutor.execute(() -> writeSnapshot(configPath, snapshot));
    }

    /**
     * 加载功能测试配置
     * 通过ID从集合中获取最新的请求配置，确保与集合保持同步
     */
    public FunctionalConfigSnapshot loadSnapshot() {
        List<FunctionalConfigRow> rows = new ArrayList<>();
        Path configPath = getConfigFilePath();
        File file = configPath.toFile();

        if (!file.exists()) {
            log.info("No functional test config file found, starting fresh");
            return FunctionalConfigSnapshot.empty();
        }

        try {
            // 检查文件大小
            long fileSizeInBytes = file.length();
            if (fileSizeInBytes > MAX_FILE_SIZE) {
                log.warn("Config file is too large ({} bytes), deleting and starting fresh", fileSizeInBytes);
                deleteFile(file);
                return FunctionalConfigSnapshot.empty();
            }

            if (fileSizeInBytes == 0) {
                return FunctionalConfigSnapshot.empty();
            }

            // 读取文件
            String jsonString = Files.readString(configPath, StandardCharsets.UTF_8);
            if (jsonString.trim().isEmpty()) {
                return FunctionalConfigSnapshot.empty();
            }

            JSONObject root = JSONUtil.parseObj(jsonString);
            JSONArray jsonArray = root.getJSONArray("rows");
            rows.addAll(deserializeRows(jsonArray));

            log.info("Successfully loaded {} functional test configurations", rows.size());
            return new FunctionalConfigSnapshot(rows, deserializeCsvState(root.getJSONObject("csvState")));

        } catch (Exception e) {
            log.error("Failed to load functional test config: {}", e.getMessage(), e);
            deleteFile(file);
        }

        return FunctionalConfigSnapshot.empty();
    }

    /**
     * 清空配置
     */
    public void clear() {
        Path configPath = getConfigFilePath();
        writeAndWait(() -> deleteFile(configPath.toFile()));
    }

    private void writeAndWait(Runnable writeAction) {
        // 最终保存和清空也排在旧的自动保存之后，避免旧快照覆盖最新配置。
        Future<?> write = saveExecutor.submit(writeAction);
        try {
            write.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while saving functional config", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to save functional config", e.getCause());
        }
    }

    /**
     * 删除配置文件
     */
    private void deleteFile(File file) {
        try {
            if (file.exists() && !file.delete()) {
                log.warn("Failed to delete config file: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Error deleting config file: {}", e.getMessage());
        }
    }

    private JSONArray serializeRows(List<FunctionalConfigRow> rows) {
        JSONArray jsonArray = new JSONArray();
        if (rows == null) {
            return jsonArray;
        }
        for (FunctionalConfigRow row : rows) {
            if (row == null || row.getRequestId() == null || row.getRequestId().isBlank()) {
                continue;
            }

            JSONObject jsonItem = new JSONObject();
            jsonItem.set("selected", row.isSelected());
            jsonItem.set("requestItemId", row.getRequestId());
            jsonArray.add(jsonItem);
        }
        return jsonArray;
    }

    private List<FunctionalConfigRow> deserializeRows(JSONArray jsonArray) {
        List<FunctionalConfigRow> rows = new ArrayList<>();
        if (jsonArray == null) {
            return rows;
        }

        for (int i = 0; i < jsonArray.size(); i++) {
            try {
                JSONObject jsonItem = jsonArray.getJSONObject(i);
                String requestItemId = jsonItem.getStr("requestItemId");
                boolean selected = jsonItem.getBool("selected", true);
                if (requestItemId == null || requestItemId.isBlank()) {
                    continue;
                }
                rows.add(new FunctionalConfigRow(requestItemId, selected));
            } catch (Exception e) {
                log.warn("Failed to restore config item at index {}: {}", i, e.getMessage());
            }
        }
        return rows;
    }

    private JSONObject serializeCsvState(FunctionalCsvDataState csvState) {
        JSONObject json = new JSONObject();
        json.set("sourceName", csvState.getSourceName());

        JSONArray headers = new JSONArray();
        for (String header : csvState.getHeaders()) {
            headers.add(header);
        }
        json.set("headers", headers);

        JSONArray rows = new JSONArray();
        for (Map<String, String> row : csvState.getRows()) {
            JSONObject rowJson = new JSONObject();
            if (row != null) {
                for (Map.Entry<String, String> entry : row.entrySet()) {
                    rowJson.set(entry.getKey(), entry.getValue());
                }
            }
            rows.add(rowJson);
        }
        json.set("rows", rows);
        return json;
    }

    private FunctionalCsvDataState deserializeCsvState(JSONObject json) {
        if (json == null) {
            return null;
        }

        JSONArray headersJson = json.getJSONArray("headers");
        JSONArray rowsJson = json.getJSONArray("rows");
        if (headersJson == null || rowsJson == null || rowsJson.isEmpty()) {
            return null;
        }

        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headersJson.size(); i++) {
            headers.add(headersJson.getStr(i));
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < rowsJson.size(); i++) {
            JSONObject rowJson = rowsJson.getJSONObject(i);
            Map<String, String> row = new LinkedHashMap<>();
            if (rowJson != null) {
                for (String header : headers) {
                    row.put(header, rowJson.getStr(header, ""));
                }
            }
            rows.add(row);
        }

        return new FunctionalCsvDataState(json.getStr("sourceName"), headers, rows);
    }

    protected Path getConfigFilePath() {
        Workspace workspace = getCurrentWorkspace();
        return Paths.get(ConfigPathConstants.getFunctionalConfigPath(workspace));
    }

    protected Workspace getCurrentWorkspace() {
        try {
            return WorkspaceService.getInstance().getCurrentWorkspace();
        } catch (Exception e) {
            log.debug("Failed to resolve current workspace for functional config path", e);
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
