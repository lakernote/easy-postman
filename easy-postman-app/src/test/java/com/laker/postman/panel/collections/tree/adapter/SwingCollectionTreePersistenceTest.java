package com.laker.postman.panel.collections.tree.adapter;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.util.SystemUtil;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SwingCollectionTreePersistenceTest {
    private Path workspaceA;
    private Path workspaceB;
    private DefaultMutableTreeNode root;
    private SwingCollectionTreePersistence persistence;

    @BeforeMethod
    public void setUp() throws Exception {
        Path dir = Files.createTempDirectory("collection-persistence");
        workspaceA = dir.resolve("a.json");
        workspaceB = dir.resolve("b.json");
        writeGroups(workspaceA, "GroupA");
        writeGroups(workspaceB, "GroupB");
        root = new DefaultMutableTreeNode("root");
        persistence = new SwingCollectionTreePersistence(workspaceA.toString(), root, new DefaultTreeModel(root));
    }

    @Test
    public void startupReadShouldNotTouchTreeAndShouldNotOverwriteFileWithEmptyTree() throws Exception {
        SwingCollectionTreePersistence.LoadRequest request = persistence.beginStartupLoad();
        SwingCollectionTreePersistence.LoadedCollections loaded = persistence.read(request);

        assertEquals(root.getChildCount(), 0, "background read must not touch the bound tree model");
        persistence.saveCurrentTree();
        assertTrue(Files.readString(workspaceA).contains("GroupA"));

        assertTrue(persistence.applyLoaded(loaded));
        assertEquals(groupNames(), List.of("GroupA"));
    }

    @Test
    public void changesSavedWhileStartupLoadIsPendingShouldBeMergedAndPersisted() throws Exception {
        SwingCollectionTreePersistence.LoadRequest request = persistence.beginStartupLoad();
        root.add(CollectionTreeNodes.groupNode(new RequestGroup("CreatedDuringLoad")));
        persistence.saveCurrentTree();
        assertFalse(Files.readString(workspaceA).contains("CreatedDuringLoad"), "save is deferred until apply");

        assertTrue(persistence.applyLoaded(persistence.read(request)));

        assertEquals(groupNames(), List.of("GroupA", "CreatedDuringLoad"));
        String saved = Files.readString(workspaceA);
        assertTrue(saved.contains("GroupA") && saved.contains("CreatedDuringLoad"));
    }

    @Test
    public void staleStartupResultShouldNotOverrideNewerWorkspaceSwitch() throws Exception {
        SwingCollectionTreePersistence.LoadRequest startup = persistence.beginStartupLoad();
        SwingCollectionTreePersistence.LoadedCollections startupResult = persistence.read(startup);
        SwingCollectionTreePersistence.LoadRequest switchRequest = persistence.beginSwitch(workspaceB.toString());

        assertTrue(persistence.applyLoaded(persistence.read(switchRequest)));
        assertFalse(persistence.applyLoaded(startupResult), "stale startup result must be discarded");

        assertEquals(groupNames(), List.of("GroupB"));
        persistence.saveCurrentTree();
        assertFalse(Files.readString(workspaceB).contains("GroupA"));
    }

    @Test
    public void savesDuringSwitchShouldGoToPreviousFileUntilNewTreeIsApplied() throws Exception {
        persistence.applyLoaded(persistence.read(persistence.beginStartupLoad()));
        String workspaceBBefore = Files.readString(workspaceB);

        SwingCollectionTreePersistence.LoadRequest switchRequest = persistence.beginSwitch(workspaceB.toString());
        SwingCollectionTreePersistence.LoadedCollections switched = persistence.read(switchRequest);
        root.add(CollectionTreeNodes.groupNode(new RequestGroup("EditedInA")));
        persistence.saveCurrentTree();

        assertTrue(Files.readString(workspaceA).contains("EditedInA"), "edit belongs to the workspace still shown");
        assertEquals(Files.readString(workspaceB), workspaceBBefore);

        assertTrue(persistence.applyLoaded(switched));
        assertEquals(groupNames(), List.of("GroupB"));
    }

    @Test
    public void sameFileReloadShouldKeepEditsMadeWhileReading() throws Exception {
        persistence.applyLoaded(persistence.read(persistence.beginStartupLoad()));

        SwingCollectionTreePersistence.LoadRequest reload = persistence.beginSwitch(workspaceA.toString());
        SwingCollectionTreePersistence.LoadedCollections stale = persistence.read(reload);
        root.add(CollectionTreeNodes.groupNode(new RequestGroup("EditedDuringReload")));
        persistence.saveCurrentTree();

        assertTrue(persistence.applyLoaded(stale));
        assertEquals(groupNames(), List.of("GroupA", "EditedDuringReload"));
        assertTrue(Files.readString(workspaceA).contains("EditedDuringReload"));
    }

    @Test
    public void sameFileReloadKeepingEditsShouldBackUpOverwrittenExternalVersion() throws Exception {
        persistence.applyLoaded(persistence.read(persistence.beginStartupLoad()));
        String externalGroup = "External-" + System.nanoTime();
        writeGroups(workspaceA, externalGroup);

        SwingCollectionTreePersistence.LoadRequest reload = persistence.beginSwitch(workspaceA.toString());
        SwingCollectionTreePersistence.LoadedCollections external = persistence.read(reload);
        root.add(CollectionTreeNodes.groupNode(new RequestGroup("EditedDuringReload")));
        persistence.saveCurrentTree();

        assertTrue(persistence.applyLoaded(external));
        assertFalse(Files.readString(workspaceA).contains(externalGroup));
        Path backupDir = Path.of(SystemUtil.getEasyPostmanPath(), "backups", "overwritten");
        try (Stream<Path> backups = Files.list(backupDir)) {
            assertTrue(backups.anyMatch(file -> {
                try {
                    return Files.readString(file).contains(externalGroup);
                } catch (Exception e) {
                    return false;
                }
            }), "overwritten external version must be backed up");
        }
    }

    @Test
    public void sameFileReloadWithoutEditsShouldPickUpExternalChanges() throws Exception {
        persistence.applyLoaded(persistence.read(persistence.beginStartupLoad()));
        writeGroups(workspaceA, "ExternalGroup");

        assertTrue(persistence.applyLoaded(persistence.read(persistence.beginSwitch(workspaceA.toString()))));
        assertEquals(groupNames(), List.of("ExternalGroup"));
    }

    @Test
    public void flushShouldPersistChangesDeferredDuringStartupLoad() throws Exception {
        SwingCollectionTreePersistence.LoadRequest startup = persistence.beginStartupLoad();
        SwingCollectionTreePersistence.LoadedCollections startupResult = persistence.read(startup);
        root.add(CollectionTreeNodes.groupNode(new RequestGroup("CreatedBeforeExit")));
        persistence.saveCurrentTree();

        persistence.flushPendingStartupLoad();

        String saved = Files.readString(workspaceA);
        assertTrue(saved.contains("GroupA") && saved.contains("CreatedBeforeExit"));
        assertFalse(persistence.isStartupPending());
        assertFalse(persistence.applyLoaded(startupResult), "in-flight startup result is superseded by the flush");
    }

    private List<String> groupNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            CollectionTreeNodes.group(child).ifPresent(group -> names.add(group.getName()));
        }
        return names;
    }

    private static void writeGroups(Path file, String groupName) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(CollectionTreeNodes.groupNode(new RequestGroup(groupName)));
        new SwingCollectionTreePersistence(file.toString(), root, new DefaultTreeModel(root)).saveCurrentTree();
    }
}
