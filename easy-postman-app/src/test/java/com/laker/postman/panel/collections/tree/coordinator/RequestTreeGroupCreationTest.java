package com.laker.postman.panel.collections.tree.coordinator;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.tree.RequestTreeCellRenderer;
import com.laker.postman.common.component.tree.TreeTransferHandler;
import com.laker.postman.common.themes.EasyDarkLaf;
import com.laker.postman.common.themes.EasyLightLaf;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreeDocumentMapper;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreePersistence;
import com.laker.postman.panel.collections.tree.adapter.SwingSavedResponseTreeMutation;
import com.laker.postman.panel.collections.tree.handler.RequestTreeMouseHandler;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.swing.DropMode;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.LookAndFeel;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

public class RequestTreeGroupCreationTest extends AbstractSwingUiTest {

    @DataProvider
    public Object[][] themeConfigurations() {
        return new Object[][]{
                {false, false},
                {false, true},
                {true, true}
        };
    }

    @Test(dataProvider = "themeConfigurations")
    public void addFolderAfterPlusShouldUseClickedGroupAndSurviveReload(
            boolean darkTheme, boolean refreshUi) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (TreeFixture fixture = new TreeFixture(darkTheme, refreshUi)) {
                SavedResponse example = new SavedResponse();
                example.setId("saved-example");
                example.setName("Example");
                HttpRequestItem existingItem = CollectionTreeNodes.request(fixture.existingRequest).orElseThrow();
                SwingSavedResponseTreeMutation.appendSavedResponse(fixture.root, existingItem, example);
                fixture.clickGroupAction(30);
                DefaultMutableTreeNode newRequest = fixture.newRequest();
                assertSame(fixture.tree.getLastSelectedPathComponent(), newRequest);

                fixture.clickGroupAction(2);
                JPopupMenu menu = selectedPopup();
                assertNotNull(menu, "The folder's more-actions menu should open");
                assertSame(fixture.tree.getLastSelectedPathComponent(), fixture.group);
                // The folder menu starts with Add Request followed by Add Folder.
                ((JMenuItem) menu.getComponent(1)).doClick(0);

                DefaultMutableTreeNode newFolder = childGroupNamed(fixture.group, "New Folder");
                assertNotNull(newFolder, "The new folder should be a child of the clicked group");
                assertSame(fixture.coordinator.dialogParent, fixture.group);
                assertEquals(newRequest.getChildCount(), 0,
                        "A request must not acquire a folder child");
                fixture.coordinator.addHttpRequestDirectly(newFolder);
                HttpRequestItem nestedItem = CollectionTreeNodes.request(
                        (DefaultMutableTreeNode) newFolder.getChildAt(0)).orElseThrow();

                DefaultMutableTreeNode reloadedRoot = new DefaultMutableTreeNode(CollectionTreePanel.ROOT);
                SwingCollectionTreeDocumentMapper.replaceRootChildren(reloadedRoot,
                        CollectionDocumentJsonCodec.read(fixture.file.toFile()));
                DefaultMutableTreeNode reloadedGroup = (DefaultMutableTreeNode) reloadedRoot.getChildAt(0);
                DefaultMutableTreeNode reloadedFolder = childGroupNamed(reloadedGroup, "New Folder");
                assertNotNull(reloadedFolder,
                        "The folder must remain present after saving and loading the collection");
                String requestId = CollectionTreeNodes.request(newRequest).orElseThrow().getId();
                assertNotNull(childRequestWithId(reloadedGroup, requestId),
                        "The new request and folder should both remain direct children of the group");
                assertNotNull(childRequestWithId(reloadedFolder, nestedItem.getId()),
                        "Requests inside the new folder must survive reloading");
                DefaultMutableTreeNode reloadedExisting = childRequestWithId(reloadedGroup, existingItem.getId());
                assertNotNull(reloadedExisting);
                assertEquals(reloadedExisting.getChildCount(), 1,
                        "The existing request's saved response should remain supported");
                assertEquals(CollectionTreeNodes.savedResponse(
                        (DefaultMutableTreeNode) reloadedExisting.getChildAt(0)).orElseThrow().getId(), example.getId());
            }
        });
    }

    @Test
    public void openedFolderMenuShouldKeepItsParentWhenSelectionChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (TreeFixture fixture = new TreeFixture(false, true)) {
                fixture.clickGroupAction(2);
                JPopupMenu menu = selectedPopup();
                assertNotNull(menu);
                fixture.tree.setSelectionPath(new TreePath(fixture.existingRequest.getPath()));
                ((JMenuItem) menu.getComponent(1)).doClick(0);

                assertSame(fixture.coordinator.dialogParent, fixture.group);
                assertNotNull(childGroupNamed(fixture.group, "New Folder"));
                assertEquals(fixture.existingRequest.getChildCount(), 0);
            }
        });
    }

    @Test(dataProvider = "themeConfigurations")
    public void clickingAnotherRequestAfterPlusReleaseShouldChangeSelection(
            boolean darkTheme, boolean refreshUi) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (TreeFixture fixture = new TreeFixture(darkTheme, refreshUi)) {
                fixture.clickGroupAction(30);
                assertSame(fixture.tree.getLastSelectedPathComponent(), fixture.newRequest());

                Rectangle bounds = fixture.tree.getPathBounds(new TreePath(fixture.existingRequest.getPath()));
                click(fixture.tree, bounds.x + 40, bounds.y + bounds.height / 2);

                assertSame(fixture.tree.getLastSelectedPathComponent(), fixture.existingRequest,
                        "The creation selection guard must end with the plus-button click");
            }
        });
    }

    @Test
    public void collectionCreationShouldUsePersistentRootWhileSearchFiltersTree() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (TreeFixture fixture = new TreeFixture(false, true)) {
                DefaultMutableTreeNode filteredRoot = new DefaultMutableTreeNode(CollectionTreePanel.ROOT);
                fixture.model.setRoot(filteredRoot);

                insertGroup(fixture.coordinator, fixture.root, "New Collection");

                assertNotNull(childGroupNamed(fixture.root, "New Collection"),
                        "The toolbar must still create collections under the persistent root during search");
                assertEquals(filteredRoot.getChildCount(), 0);
                DefaultMutableTreeNode reloadedRoot = new DefaultMutableTreeNode(CollectionTreePanel.ROOT);
                SwingCollectionTreeDocumentMapper.replaceRootChildren(reloadedRoot,
                        CollectionDocumentJsonCodec.read(fixture.file.toFile()));
                assertNotNull(childGroupNamed(reloadedRoot, "New Collection"),
                        "The collection created during search must be saved under the persistent root");
            }
        });
    }

    @Test
    public void requestAndSavedResponseShouldRejectFolderCreation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (TreeFixture fixture = new TreeFixture(false, true)) {
                DefaultMutableTreeNode response = CollectionTreeNodes.savedResponseNode(new SavedResponse());
                fixture.existingRequest.add(response);
                RequestTreeCoordinator coordinator = new RequestTreeCoordinator(fixture.tree, fixture.panel);

                for (DefaultMutableTreeNode invalidParent : new DefaultMutableTreeNode[]{
                        fixture.existingRequest, response
                }) {
                    int childCount = invalidParent.getChildCount();
                    coordinator.showAddGroupDialog(invalidParent);
                    insertGroup(coordinator, invalidParent, "Invalid Folder");
                    assertEquals(invalidParent.getChildCount(), childCount,
                            "Only a collection or folder can contain a new folder");
                }
            }
        });
    }

    private static void click(JTree tree, int x, int y) {
        long time = System.currentTimeMillis();
        tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_PRESSED, time,
                MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
        tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_RELEASED, time + 10,
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private static JPopupMenu selectedPopup() {
        for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath()) {
            if (element instanceof JPopupMenu menu) {
                return menu;
            }
        }
        return null;
    }

    private static DefaultMutableTreeNode childGroupNamed(DefaultMutableTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (CollectionTreeNodes.group(child).map(RequestGroup::getName).filter(name::equals).isPresent()) {
                return child;
            }
        }
        return null;
    }

    private static DefaultMutableTreeNode childRequestWithId(DefaultMutableTreeNode parent, String id) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (CollectionTreeNodes.request(child).map(HttpRequestItem::getId).filter(id::equals).isPresent()) {
                return child;
            }
        }
        return null;
    }

    private static void insertGroup(RequestTreeCoordinator coordinator, DefaultMutableTreeNode parent, String name) {
        try {
            Method method = RequestTreeCoordinator.class.getDeclaredMethod(
                    "addGroupToNode", DefaultMutableTreeNode.class, String.class);
            method.setAccessible(true);
            method.invoke(coordinator, parent, name);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Object> singletonMap() {
        try {
            Field field = UiSingletonFactory.class.getDeclaredField("INSTANCE_MAP");
            field.setAccessible(true);
            return (Map<Class<?>, Object>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class TreeFixture implements AutoCloseable {
        private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(CollectionTreePanel.ROOT);
        private final DefaultMutableTreeNode group = CollectionTreeNodes.groupNode(new RequestGroup("Collection"));
        private final DefaultMutableTreeNode existingRequest = CollectionTreeNodes.requestNode(new HttpRequestItem());
        private final DefaultTreeModel model = new DefaultTreeModel(root);
        private final JTree tree;
        private final JFrame frame;
        private final LookAndFeel previousLookAndFeel;
        private final Path file;
        private final FixturePanel panel;
        private final NameInputCoordinator coordinator;
        private final Object previousEditor;

        private TreeFixture(boolean darkTheme, boolean refreshUi) {
            try {
                file = Files.createTempFile("collection-group-creation-", ".json");
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            root.add(group);
            HttpRequestItem existingItem = CollectionTreeNodes.request(existingRequest).orElseThrow();
            existingItem.setId("existing-request");
            existingItem.setName("Existing Request");
            group.add(existingRequest);
            previousLookAndFeel = UIManager.getLookAndFeel();
            setLookAndFeel(darkTheme ? new EasyDarkLaf() : new EasyLightLaf());
            tree = new JTree(model) {
                @Override
                public boolean getScrollableTracksViewportWidth() {
                    return true;
                }
            };
            frame = new JFrame("Collection tree regression test");
            tree.setRootVisible(false);
            tree.setShowsRootHandles(true);
            tree.setRowHeight(28);
            tree.setToggleClickCount(0);
            tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
            tree.putClientProperty("FlatLaf.style", "wideCellRenderer: true");
            tree.setCellRenderer(new RequestTreeCellRenderer());
            ToolTipManager.sharedInstance().registerComponent(tree);
            tree.setDragEnabled(true);
            tree.setDropMode(DropMode.ON_OR_INSERT);

            FixtureEditor editor;
            UiSingletonPanel.setFactoryCreationAllowed(true);
            try {
                panel = new FixturePanel(tree, model, root,
                        new SwingCollectionTreePersistence(file.toString(), root, model));
                editor = new FixtureEditor();
            } finally {
                UiSingletonPanel.setFactoryCreationAllowed(false);
            }
            previousEditor = singletonMap().put(RequestEditorPanel.class, editor);
            tree.setTransferHandler(new TreeTransferHandler(tree, model, panel.persistence::saveCurrentTree));
            coordinator = new NameInputCoordinator(tree, panel);
            RequestTreeMouseHandler handler = new RequestTreeMouseHandler(tree, panel, coordinator);
            tree.addMouseListener(handler);
            tree.addMouseMotionListener(handler);

            JScrollPane scrollPane = new JScrollPane(tree);
            ToolWindowSurfaceStyle.applyTreeScrollPaneCard(scrollPane, tree);
            frame.add(scrollPane);
            frame.setSize(350, 220);
            frame.setVisible(true);
            tree.expandPath(new TreePath(group.getPath()));
            tree.setSelectionPath(new TreePath(existingRequest.getPath()));
            if (refreshUi) {
                // Theme/font/language changes reinstall the UI listener after the application listener.
                SwingUtilities.updateComponentTreeUI(frame);
            }
        }

        private void clickGroupAction(int rightOffset) {
            Rectangle bounds = tree.getPathBounds(new TreePath(group.getPath()));
            click(tree, tree.getWidth() - rightOffset, bounds.y + bounds.height / 2);
        }

        private DefaultMutableTreeNode newRequest() {
            return (DefaultMutableTreeNode) group.getChildAt(group.getChildCount() - 1);
        }

        @Override
        public void close() {
            JPopupMenu menu = selectedPopup();
            if (menu != null) {
                menu.setVisible(false);
            }
            frame.dispose();
            ToolTipManager.sharedInstance().unregisterComponent(tree);
            setLookAndFeel(previousLookAndFeel);
            if (previousEditor == null) {
                singletonMap().remove(RequestEditorPanel.class);
            } else {
                singletonMap().put(RequestEditorPanel.class, previousEditor);
            }
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static void setLookAndFeel(LookAndFeel lookAndFeel) {
            try {
                UIManager.setLookAndFeel(lookAndFeel);
            } catch (UnsupportedLookAndFeelException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class NameInputCoordinator extends RequestTreeCoordinator {
        private DefaultMutableTreeNode dialogParent;

        private NameInputCoordinator(JTree tree, CollectionTreePanel panel) {
            super(tree, panel);
        }

        @Override
        public void showAddGroupDialog(DefaultMutableTreeNode parent) {
            dialogParent = parent;
            // Replace only entering a name in the modal dialog; retain the real insertion and persistence.
            insertGroup(this, parent, "New Folder");
        }
    }

    private static final class FixturePanel extends CollectionTreePanel {
        private final JTree tree;
        private final DefaultTreeModel model;
        private final DefaultMutableTreeNode root;
        private final SwingCollectionTreePersistence persistence;

        private FixturePanel(JTree tree, DefaultTreeModel model, DefaultMutableTreeNode root,
                             SwingCollectionTreePersistence persistence) {
            this.tree = tree;
            this.model = model;
            this.root = root;
            this.persistence = persistence;
        }

        @Override
        protected void initUI() {
        }

        @Override
        protected void registerListeners() {
        }

        @Override
        public JTree getRequestTree() {
            return tree;
        }

        @Override
        public DefaultTreeModel getTreeModel() {
            return model;
        }

        @Override
        public DefaultMutableTreeNode getRootTreeNode() {
            return root;
        }

        @Override
        public SwingCollectionTreePersistence getCollectionTreePersistence() {
            return persistence;
        }
    }

    private static final class FixtureEditor extends RequestEditorPanel {
        @Override
        protected void initUI() {
        }

        @Override
        protected void registerListeners() {
        }

        @Override
        public void showOrCreateTab(HttpRequestItem item) {
        }

        @Override
        public void showOrCreateTransientTab(HttpRequestItem item) {
        }
    }
}
