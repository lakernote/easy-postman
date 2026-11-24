package com.laker.postman.platform.update.source;

import com.laker.postman.platform.update.UpdateSettingsProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 更新源选择器 - 负责根据配置或自动检测选择最佳更新源
 *
 * <p>支持三种模式：</p>
 * <ul>
 *   <li>github - 固定使用 GitHub</li>
 *   <li>gitee - 固定使用 Gitee</li>
 *   <li>auto - 自动选择最快的源</li>
 * </ul>
 */
@Slf4j
public class UpdateSourceSelector {

    private final UpdateSettingsProvider settingsProvider;

    /** GitHub 更新源 */
    private final UpdateSource githubSource;

    /** Gitee 更新源 */
    private final UpdateSource giteeSource;

    /** 缓存的最佳源 */
    private volatile UpdateSource cachedBestSource = null;

    /**
     * 自动检测在进程内共享：应用更新、插件更新、更新日志各自持有选择器，启动时会并发检测。
     * 同一时刻只有一次测速，并发调用等待同一结果（源名称；失败为 null）。失败结果在冷却期内复用，避免每次调用都等满超时。
     */
    private static final Object AUTO_DETECT_LOCK = new Object();
    private static CompletableFuture<String> autoDetection;
    private static volatile long autoDetectionFailedAt;

    /** 测速失败后多久允许重新测速 */
    private static final long FAILED_DETECTION_RETRY_MS = TimeUnit.MINUTES.toMillis(5);

    /** 自动检测超时时间（秒） */
    private static final int AUTO_DETECT_TIMEOUT_SECONDS = 6;

    /** 快速响应阈值（毫秒） */
    private static final long FAST_RESPONSE_THRESHOLD_MS = 3000;

    public UpdateSourceSelector() {
        this(UpdateSettingsProvider.defaults());
    }

    public UpdateSourceSelector(UpdateSettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider == null ? UpdateSettingsProvider.defaults() : settingsProvider;
        this.githubSource = new GitHubUpdateSource();
        this.giteeSource = new GiteeUpdateSource();
    }

    /**
     * 选择最佳更新源
     *
     * @return 选中的更新源
     */
    public UpdateSource selectBestSource() {
        String preference = settingsProvider.getUpdateSourcePreference();

        // 用户指定 GitHub
        if ("github".equalsIgnoreCase(preference)) {
            log.info("Using user-preferred source: GitHub");
            cachedBestSource = githubSource;
            return githubSource;
        }

        // 用户指定 Gitee
        if ("gitee".equalsIgnoreCase(preference)) {
            log.info("Using user-preferred source: Gitee");
            cachedBestSource = giteeSource;
            return giteeSource;
        }

        // auto 模式：自动选择
        return selectSourceAutomatically();
    }

    /**
     * 自动选择最快的更新源
     */
    private UpdateSource selectSourceAutomatically() {
        // 如果有缓存，直接使用
        if (cachedBestSource != null) {
            log.info("Using cached best source: {}", cachedBestSource.getName());
            return cachedBestSource;
        }

        CompletableFuture<String> detection;
        boolean detectHere = false;
        synchronized (AUTO_DETECT_LOCK) {
            if (autoDetection == null || shouldRetryFailedDetection(autoDetection)) {
                autoDetection = new CompletableFuture<>();
                detectHere = true;
            }
            detection = autoDetection;
        }
        if (detectHere) {
            // 在调用线程上测速，不占用公共线程池（测速本身已在公共线程池上并行请求两个源）
            String detectedName = null;
            try {
                UpdateSource detected = detectFastestSource();
                detectedName = detected == null ? null : detected.getName();
            } finally {
                if (detectedName == null) {
                    autoDetectionFailedAt = System.currentTimeMillis();
                }
                detection.complete(detectedName);
            }
        }

        String sourceName = detection.join();
        if (sourceName == null) {
            // 测速失败不写入实例缓存，冷却期过后可以重新测速
            log.info("Using default source: Gitee");
            return giteeSource;
        }
        // 共享的是名称，映射回本实例自己的源对象（getFallbackSource 按实例比较）
        UpdateSource source = githubSource.getName().equals(sourceName) ? githubSource : giteeSource;
        if (!detectHere) {
            log.info("Using auto-detected source: {}", source.getName());
        }
        cachedBestSource = source;
        return source;
    }

    private static boolean shouldRetryFailedDetection(CompletableFuture<String> detection) {
        return detection.isDone()
                && detection.getNow(null) == null
                && System.currentTimeMillis() - autoDetectionFailedAt >= FAILED_DETECTION_RETRY_MS;
    }

    /**
     * @return 测速得到的源；两个源都不可用或超时时返回 null
     */
    private UpdateSource detectFastestSource() {
        log.info("Auto-detecting best update source...");

        // 并行测试两个源
        CompletableFuture<Long> githubTest = CompletableFuture.supplyAsync(githubSource::testConnectionSpeed);
        CompletableFuture<Long> giteeTest = CompletableFuture.supplyAsync(giteeSource::testConnectionSpeed);

        try {
            // 等待测试完成
            CompletableFuture.allOf(githubTest, giteeTest).get(AUTO_DETECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            long githubTime = githubTest.getNow(Long.MAX_VALUE);
            long giteeTime = giteeTest.getNow(Long.MAX_VALUE);

            // 选择更快的源
            if (githubTime < giteeTime && githubTime < FAST_RESPONSE_THRESHOLD_MS) {
                log.info("GitHub is faster ({} ms vs {} ms), using GitHub", githubTime, giteeTime);
                return githubSource;
            } else if (giteeTime < Long.MAX_VALUE) {
                log.info("Gitee is faster or preferred ({} ms vs {} ms), using Gitee", giteeTime, githubTime);
                return giteeSource;
            }
        } catch (Exception e) {
            log.debug("Source detection error: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return null;
    }

    /**
     * 获取备用源（当主源失败时使用）
     *
     * @param primarySource 主源
     * @return 备用源
     */
    public UpdateSource getFallbackSource(UpdateSource primarySource) {
        if (primarySource == githubSource) {
            return giteeSource;
        }
        return githubSource;
    }

    /**
     * 按名称获取指定的更新源，不做测速；未知名称返回 null。
     */
    public UpdateSource getSource(String sourceName) {
        if (githubSource.getName().equalsIgnoreCase(sourceName)) {
            return githubSource;
        }
        if (giteeSource.getName().equalsIgnoreCase(sourceName)) {
            return giteeSource;
        }
        return null;
    }

    /**
     * 根据发布信息中的来源名称获取备用源。
     * 未知来源不做猜测，避免把下载请求发往错误的镜像。
     */
    public UpdateSource getFallbackSource(String primarySourceName) {
        if (primarySourceName == null) {
            return null;
        }
        if (githubSource.getName().equalsIgnoreCase(primarySourceName)) {
            return giteeSource;
        }
        if (giteeSource.getName().equalsIgnoreCase(primarySourceName)) {
            return githubSource;
        }
        return null;
    }

    /**
     * 清除缓存的最佳源
     */
    public void clearCache() {
        cachedBestSource = null;
        synchronized (AUTO_DETECT_LOCK) {
            if (autoDetection != null && autoDetection.isDone()) {
                autoDetection = null;
            }
        }
    }
}
