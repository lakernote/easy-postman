package com.laker.postman.panel.collections.tree.adapter;

import com.laker.postman.collection.CollectionIdNormalizer;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import com.laker.postman.service.collections.CollectionFilePersistence;
import com.laker.postman.util.SafeFileUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 集合文件与 Swing 树模型之间的适配。
 * <p>
 * 加载分三步：EDT 上 {@code begin*} 登记一次加载，后台 {@link #read} 只读文件，EDT 上 {@link #applyLoaded} 挂到树。
 * 每次登记递增代次，过期结果在挂载时被丢弃，避免慢的旧加载覆盖新工作区；保存目标文件也在挂载时才切换，
 * 加载期间的保存仍写回与当前树一致的文件。启动加载未挂载前调用方不应发起切换（见 {@link #isStartupPending()}）。
 */
@Slf4j
public class SwingCollectionTreePersistence {
    private final CollectionFilePersistence filePersistence;
    private final DefaultMutableTreeNode rootTreeNode;
    private final DefaultTreeModel treeModel;
    private long loadGeneration;
    private boolean blockSavesUntilApplied;
    private boolean saveSkippedWhileBlocked;
    private long treeChangeCount;

    public SwingCollectionTreePersistence(String filePath, DefaultMutableTreeNode rootTreeNode, DefaultTreeModel treeModel) {
        this(new CollectionFilePersistence(filePath), rootTreeNode, treeModel);
    }

    SwingCollectionTreePersistence(CollectionFilePersistence filePersistence,
                                   DefaultMutableTreeNode rootTreeNode,
                                   DefaultTreeModel treeModel) {
        this.filePersistence = filePersistence;
        this.rootTreeNode = rootTreeNode;
        this.treeModel = treeModel;
    }

    public record LoadRequest(long generation, String filePath, long treeChangeCount) {
    }

    public record LoadedCollections(long generation, String filePath, long treeChangeCount, CollectionDocument document) {
    }

    public void exportCurrentTree(File fileToSave) throws IOException {
        filePersistence.export(currentDocument(), fileToSave);
    }

    /**
     * 同步加载并挂载到树，仅适用于未绑定到可见 JTree 的模型或已在 EDT 上的调用。
     */
    public void loadIntoTree() {
        applyLoaded(read(beginLoad(filePersistence.getFilePath())));
    }

    /**
     * EDT：登记启动加载。树此时为空，挂载前的保存会被拦截，避免空树覆盖集合文件。
     */
    public LoadRequest beginStartupLoad() {
        blockSavesUntilApplied = true;
        return beginLoad(filePersistence.getFilePath());
    }

    /**
     * EDT：登记切换到另一个集合文件。挂载前树和保存目标仍是旧文件，保存照常写回旧文件。
     */
    public LoadRequest beginSwitch(String path) {
        return beginLoad(path == null || path.isBlank() ? filePersistence.getFilePath() : path);
    }

    /**
     * EDT：启动加载尚未挂载时树为空且保存被推迟，此时不能开始切换工作区，否则推迟的改动无处归属。
     */
    public boolean isStartupPending() {
        return blockSavesUntilApplied;
    }

    /**
     * EDT：退出前同步完成仍在进行的启动加载，确保推迟的改动写入集合文件。
     */
    public void flushPendingStartupLoad() {
        if (blockSavesUntilApplied && saveSkippedWhileBlocked) {
            applyLoaded(read(beginLoad(filePersistence.getFilePath())));
        }
    }

    private LoadRequest beginLoad(String path) {
        loadGeneration++;
        return new LoadRequest(loadGeneration, path, treeChangeCount);
    }

    /**
     * 后台线程：读取并修复集合文件，不触碰树模型。读取失败时返回空集合。
     */
    public LoadedCollections read(LoadRequest request) {
        return new LoadedCollections(request.generation(), request.filePath(), request.treeChangeCount(),
                readDocument(request.filePath()));
    }

    /**
     * 后台线程：读取并修复指定集合文件。读取失败时返回空集合。
     */
    public static CollectionDocument readDocument(String filePath) {
        CollectionFilePersistence reader = new CollectionFilePersistence(filePath);
        CollectionDocument document;
        try {
            document = reader.load();
        } catch (Exception e) {
            log.error("Error loading request collections: {}", filePath, e);
            // 不能保留上一个工作区的树，否则后续保存会把它写进新工作区。
            return CollectionDocument.empty();
        }
        int repaired = CollectionIdNormalizer.ensureUniqueIds(document);
        if (repaired > 0) {
            log.info("Reassigned {} duplicate or blank collection IDs (group/request/example)", repaired);
            reader.save(document);
        }
        return document;
    }

    /**
     * EDT：挂载加载结果。
     *
     * @return 结果已被更新的加载取代时返回 false，树和保存目标保持不变
     */
    public boolean applyLoaded(LoadedCollections loaded) {
        if (loaded.generation() != loadGeneration) {
            log.info("Discarding stale collection load: generation={}, current={}, file={}",
                    loaded.generation(), loadGeneration, loaded.filePath());
            return false;
        }
        if (!blockSavesUntilApplied
                && loaded.treeChangeCount() != treeChangeCount
                && loaded.filePath().equals(filePersistence.getFilePath())) {
            // 同一文件重载期间树已被编辑：读到的内容可能早于这些编辑（或编辑因文件正在读取而未写入），保留树并重新保存。
            // 读到的外部版本已被记为加载基准，编辑期间的保存不会自动备份它；按读到的内容显式备份，可从 backups/overwritten 找回。
            log.warn("Keeping collection tree edited during reload of the same file: {}", loaded.filePath());
            SafeFileUtil.backupDiscardedVersion(Path.of(loaded.filePath()),
                    CollectionDocumentJsonCodec.toJson(loaded.document()).toStringPretty());
            filePersistence.save(currentDocument());
            return true;
        }
        CollectionDocument document = loaded.document();
        boolean carryOver = blockSavesUntilApplied && saveSkippedWhileBlocked && rootTreeNode.getChildCount() > 0;
        if (carryOver) {
            // 启动时树为空，加载期间出现的节点都是用户新建/导入的，追加在已加载内容之后，避免丢失
            List<CollectionNode> merged = document.mutableRootsCopy();
            merged.addAll(currentDocument().getRoots());
            document = new CollectionDocument(merged);
        }
        filePersistence.switchFilePath(loaded.filePath());
        SwingCollectionTreeDocumentMapper.replaceRootChildren(rootTreeNode, document);
        treeModel.reload(rootTreeNode);
        blockSavesUntilApplied = false;
        saveSkippedWhileBlocked = false;
        if (carryOver) {
            log.info("Saved collection changes made while startup collections were loading");
            saveCurrentTree();
        }
        return true;
    }

    public void saveCurrentTree() {
        treeChangeCount++;
        if (blockSavesUntilApplied) {
            // 挂载时会把这些改动合并进加载结果并保存
            saveSkippedWhileBlocked = true;
            log.info("Deferring collection save until startup collections are applied");
            return;
        }
        filePersistence.save(currentDocument());
    }

    private CollectionDocument currentDocument() {
        return SwingCollectionTreeDocumentMapper.fromRoot(rootTreeNode);
    }
}
