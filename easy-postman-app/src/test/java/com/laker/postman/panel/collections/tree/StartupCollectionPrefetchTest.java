package com.laker.postman.panel.collections.tree;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreePersistence;
import com.laker.postman.service.collections.CollectionTreeNodes;
import org.testng.annotations.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class StartupCollectionPrefetchTest {

    @Test
    public void takeShouldReturnPrefetchedDocumentOnceAndRejectOtherPath() throws Exception {
        Path file = Files.createTempDirectory("startup-prefetch").resolve("collections.json");
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(CollectionTreeNodes.groupNode(new RequestGroup("Prefetched")));
        new SwingCollectionTreePersistence(file.toString(), root, new DefaultTreeModel(root)).saveCurrentTree();

        StartupCollectionPrefetch.start(file.toString());
        StartupCollectionPrefetch.Result result = StartupCollectionPrefetch.take(file.toString(), true);

        assertNotNull(result);
        assertEquals(result.document().mutableRootsCopy().size(), 1);
        assertNull(StartupCollectionPrefetch.take(file.toString(), true), "prefetch result must be consumed once");

        StartupCollectionPrefetch.start(file.toString());
        assertNull(StartupCollectionPrefetch.take(file + ".other", true), "result for another workspace must be ignored");
    }

    @Test
    public void takeShouldRejectResultWhenFileChangedAfterPrefetch() throws Exception {
        Path file = Files.createTempDirectory("startup-prefetch-stale").resolve("collections.json");
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(CollectionTreeNodes.groupNode(new RequestGroup("Old")));
        new SwingCollectionTreePersistence(file.toString(), root, new DefaultTreeModel(root)).saveCurrentTree();

        StartupCollectionPrefetch.start(file.toString());
        StartupCollectionPrefetch.whenReady(5000).join();
        Files.writeString(file, "[]");
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        assertNull(StartupCollectionPrefetch.take(file.toString(), true), "externally replaced file must be read again");
    }
}
