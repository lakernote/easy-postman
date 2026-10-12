package com.laker.postman.service;

import com.laker.postman.common.constants.ConfigPathConstants;
import com.laker.postman.ioc.Component;
import com.laker.postman.ioc.PostConstruct;
import com.laker.postman.ioc.PreDestroy;
import com.laker.postman.model.Workspace;
import com.laker.postman.performance.plan.PerformancePlanConfiguration;
import com.laker.postman.performance.plan.PerformancePlanStorage;
import com.laker.postman.performance.plan.PerformancePlanWorkspace;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 性能测试配置持久化服务
 * 用于保存和加载性能测试面板中的测试计划配置
 */
@Slf4j
@Component
public class PerformancePersistenceService {
    private final PerformancePlanStorage planStorage = new PerformancePlanStorage();
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "performance-config-save");
        thread.setDaemon(true);
        return thread;
    });

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

    public void saveConfiguration(PerformancePlanConfiguration configuration) {
        Path configPath = getConfigFilePath();
        writeAndWait(() -> planStorage.saveConfiguration(configPath, configuration));
    }

    public void saveWorkspace(PerformancePlanWorkspace workspace) {
        Path configPath = getConfigFilePath();
        writeAndWait(() -> planStorage.saveWorkspace(configPath, workspace));
    }

    public void saveWorkspaceAsync(PerformancePlanWorkspace workspace) {
        Path configPath = getConfigFilePath();
        saveExecutor.execute(() -> planStorage.saveWorkspace(configPath, workspace));
    }

    public PerformancePlanConfiguration loadConfiguration() {
        return planStorage.loadConfiguration(getConfigFilePath());
    }

    public PerformancePlanWorkspace loadWorkspace() {
        return planStorage.loadWorkspace(getConfigFilePath());
    }

    /**
     * 清空配置
     */
    public void clear() {
        Path configPath = getConfigFilePath();
        writeAndWait(() -> planStorage.clear(configPath));
    }

    private void writeAndWait(Runnable writeAction) {
        // 所有写入共用同一个队列，退出时的最终保存必须排在旧的自动保存之后。
        Future<?> write = saveExecutor.submit(writeAction);
        try {
            write.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while saving performance config", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to save performance config", e.getCause());
        }
    }

    protected Path getConfigFilePath() {
        Workspace workspace = getCurrentWorkspace();
        return Paths.get(ConfigPathConstants.getPerformanceConfigPath(workspace));
    }

    protected Workspace getCurrentWorkspace() {
        try {
            return WorkspaceService.getInstance().getCurrentWorkspace();
        } catch (Exception e) {
            log.debug("Failed to resolve current workspace for performance config path", e);
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
