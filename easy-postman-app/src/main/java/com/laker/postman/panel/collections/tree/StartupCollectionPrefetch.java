package com.laker.postman.panel.collections.tree;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreePersistence;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.service.collections.OpenedRequestTabsStore;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 启动画面期间预读集合文件和标签会话，主内容挂载时数据通常已就绪，可在首帧前同步挂载树和标签，
 * 避免先显示空白树和编辑区再填充。
 */
@Slf4j
@UtilityClass
public class StartupCollectionPrefetch {

    record Result(String filePath, FileStamp collectionStamp, FileStamp sessionStamp,
                  CollectionDocument document, Optional<List<HttpRequestItem>> session) {
    }

    /**
     * 读取前记录的文件状态；领取时不一致说明预读后文件被外部替换，结果已过期。
     */
    record FileStamp(boolean exists, long lastModified, long length) {
        static FileStamp of(String path) {
            File file = new File(path);
            return new FileStamp(file.exists(), file.lastModified(), file.length());
        }
    }

    private static CompletableFuture<Result> prefetch;

    public static synchronized void start(String filePath) {
        if (prefetch != null || filePath == null || filePath.isBlank()) {
            return;
        }
        prefetch = CompletableFuture.supplyAsync(
                () -> {
                    FileStamp sessionStamp = FileStamp.of(OpenedRequestTabsStore.PATHNAME);
                    Optional<List<HttpRequestItem>> session = OpenedRequestTabsStore.tryLoadAll();
                    CollectionDocument document = SwingCollectionTreePersistence.readDocument(filePath);
                    // 读取时可能修复重复 ID 并回写文件，集合文件状态取读取之后的
                    return new Result(filePath, FileStamp.of(filePath), sessionStamp, document, session);
                },
                task -> {
                    Thread thread = new Thread(task, "Startup-Collection-Prefetch");
                    thread.setDaemon(true);
                    thread.start();
                });
    }

    /**
     * 预读完成或超时后完成，不消费结果。用于把主内容挂载推迟到数据就绪，期间保持启动壳显示而不是空白树。
     */
    public static synchronized CompletableFuture<Void> whenReady(long timeoutMs) {
        if (prefetch == null) {
            return CompletableFuture.completedFuture(null);
        }
        return prefetch.handle((result, error) -> (Void) null)
                .completeOnTimeout(null, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 一次性取出预读结果；路径不同、文件已变化或预读失败时返回 null，由调用方重新读取。
     *
     * @param waitForCompletion false 时预读未完成直接返回 null，并保留给后续调用
     */
    static Result take(String filePath, boolean waitForCompletion) {
        CompletableFuture<Result> future;
        synchronized (StartupCollectionPrefetch.class) {
            future = prefetch;
            if (future == null || (!waitForCompletion && !future.isDone())) {
                return null;
            }
            prefetch = null;
        }
        try {
            Result result = future.join();
            if (!filePath.equals(result.filePath())) {
                return null;
            }
            if (!result.collectionStamp().equals(FileStamp.of(filePath))
                    || !result.sessionStamp().equals(FileStamp.of(OpenedRequestTabsStore.PATHNAME))) {
                log.info("Startup collection prefetch is stale, reading again: {}", filePath);
                return null;
            }
            return result;
        } catch (Exception e) {
            log.warn("Startup collection prefetch failed, reading again", e);
            return null;
        }
    }
}
