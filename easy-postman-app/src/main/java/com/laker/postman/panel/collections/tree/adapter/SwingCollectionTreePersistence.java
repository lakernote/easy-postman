package com.laker.postman.panel.collections.tree.adapter;

import com.laker.postman.collection.CollectionIdNormalizer;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.service.collections.CollectionFilePersistence;
import com.laker.postman.service.collections.DefaultCollectionDocumentFactory;
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
            applyDocument(filePersistence.loadOrCreate(this::defaultDocument));
        } catch (Exception e) {
            log.error("Error loading request collections", e);
        }
    }

    public void saveCurrentTree() {
        filePersistence.save(currentDocument());
    }

    public void switchDataFilePath(String path) {
        try {
            applyDocument(filePersistence.switchFilePathAndLoad(path, this::defaultDocument));
        } catch (Exception e) {
            log.error("Error switching collection data file", e);
        }
    }

    private CollectionDocument currentDocument() {
        return SwingCollectionTreeDocumentMapper.fromRoot(rootTreeNode);
    }

    private CollectionDocument defaultDocument() {
        return DefaultCollectionDocumentFactory.create();
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
