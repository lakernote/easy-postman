package com.laker.postman.panel.collections.tree.adapter;

import com.laker.postman.collection.CollectionIdNormalizer;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.service.collections.CollectionFilePersistence;
import lombok.extern.slf4j.Slf4j;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.io.IOException;

@Slf4j
public class SwingCollectionTreePersistence {
    private final CollectionFilePersistence filePersistence;
    private final DefaultMutableTreeNode rootTreeNode;
    private final DefaultTreeModel treeModel;

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

    public void exportCurrentTree(File fileToSave) throws IOException {
        filePersistence.export(currentDocument(), fileToSave);
    }

    public void loadIntoTree() {
        try {
            applyDocument(filePersistence.load());
        } catch (Exception e) {
            log.error("Error loading request collections", e);
            applyDocument(CollectionDocument.empty());
        }
    }

    public void saveCurrentTree() {
        filePersistence.save(currentDocument());
    }

    public void switchDataFilePath(String path) {
        try {
            applyDocument(filePersistence.switchFilePathAndLoad(path));
        } catch (Exception e) {
            log.error("Error switching collection data file", e);
            // 不能保留上一个工作区的树，否则后续保存会把它写进新工作区。
            applyDocument(CollectionDocument.empty());
        }
    }

    private CollectionDocument currentDocument() {
        return SwingCollectionTreeDocumentMapper.fromRoot(rootTreeNode);
    }

    private void applyDocument(CollectionDocument document) {
        int repaired = CollectionIdNormalizer.ensureUniqueIds(document);
        SwingCollectionTreeDocumentMapper.replaceRootChildren(rootTreeNode, document);
        treeModel.reload(rootTreeNode);
        if (repaired > 0) {
            log.info("Reassigned {} duplicate or blank collection IDs (group/request/example)", repaired);
            saveCurrentTree();
        }
    }
}
