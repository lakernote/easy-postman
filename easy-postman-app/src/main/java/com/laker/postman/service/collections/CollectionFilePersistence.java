package com.laker.postman.service.collections;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.util.SafeFileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CollectionFilePersistence {
    private static final ConcurrentHashMap<String, Object> FILE_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> LOADING_STATUS = new ConcurrentHashMap<>();

    private String filePath;

    public CollectionFilePersistence(String filePath) {
        this.filePath = filePath;
    }

    public CollectionDocument load() throws IOException {
        synchronized (fileLock()) {
            setFileLoading(true);
            try {
                File file = new File(filePath);
                Optional<String> content = SafeFileUtil.readForLoad(file.toPath());
                if (content.isEmpty()) {
                    // 文件缺失（例如切换分支删除了它）时按空集合加载，不生成默认文件；示例数据只在新建工作区时写入。
                    log.info("Collection file not found, loading empty collections without creating it: {}", filePath);
                    return remember(file, CollectionDocument.empty());
                }
                return remember(file, readOrEmpty(file, content.get()));
            } finally {
                setFileLoading(false);
            }
        }
    }

    public boolean save(CollectionDocument document) {
        if (isFileLoading()) {
            log.warn("Skipping save operation for file '{}' because it is being loaded", filePath);
            return false;
        }

        synchronized (fileLock()) {
            if (isFileLoading()) {
                log.warn("Skipping save operation for file '{}' because it is being loaded (double-check)", filePath);
                return false;
            }
            try {
                saveIgnoringLoadGuard(document);
                log.debug("Saved collection document to: {}", filePath);
                return true;
            } catch (Exception ex) {
                log.error("Error saving collection document to file: {}", filePath, ex);
                return false;
            }
        }
    }

    public void export(CollectionDocument document, File fileToSave) throws IOException {
        CollectionDocumentJsonCodec.write(fileToSave, document);
    }

    public CollectionDocument switchFilePathAndLoad(String path) throws IOException {
        if (path == null || path.isBlank()) {
            return load();
        }

        String oldPath = this.filePath;
        synchronized (FILE_LOCKS.computeIfAbsent(oldPath, ignored -> new Object())) {
            this.filePath = path;
            return load();
        }
    }

    private CollectionDocument readOrEmpty(File file, String content) {
        try {
            return CollectionDocumentJsonCodec.read(content);
        } catch (Exception e) {
            // 外部编辑可能留下不完整的 JSON：保留原文件并备份，不能用默认内容覆盖。
            log.error("Failed to parse collection file, keeping it unchanged: {}", filePath, e);
            SafeFileUtil.backupUnreadable(file.toPath());
            return CollectionDocument.empty();
        }
    }

    /**
     * 以加载结果作为保存基准：内容未变时不写盘（不会重新生成被删除的文件，也不会覆盖推迟重新加载的外部修改）；
     */
    private CollectionDocument remember(File file, CollectionDocument document) {
        SafeFileUtil.rememberLoaded(file.toPath(), CollectionDocumentJsonCodec.toJson(document).toStringPretty());
        return document;
    }

    private void saveIgnoringLoadGuard(CollectionDocument document) throws IOException {
        SafeFileUtil.writeIfChanged(Path.of(filePath), CollectionDocumentJsonCodec.toJson(document).toStringPretty());
    }

    private Object fileLock() {
        return FILE_LOCKS.computeIfAbsent(filePath, ignored -> new Object());
    }

    private boolean isFileLoading() {
        return LOADING_STATUS.getOrDefault(filePath, false);
    }

    private void setFileLoading(boolean loading) {
        if (loading) {
            LOADING_STATUS.put(filePath, true);
        } else {
            LOADING_STATUS.remove(filePath);
        }
    }
}
