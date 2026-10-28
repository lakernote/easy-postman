package com.laker.postman.panel.collections.editor;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.tab.ClosableTabComponent;
import com.laker.postman.panel.collections.editor.request.RequestEditSubPanel;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class RequestEditorTabMethodBadgeTest extends AbstractSwingUiTest {

    @AfterMethod
    public void clearRequestEditorTabs() throws Exception {
        SwingUtilities.invokeAndWait(() ->
                UiSingletonFactory.getInstance(RequestEditorPanel.class).getTabbedPane().removeAll());
    }

    @Test
    public void requestTabMethodBadgeShouldRefreshWhenMethodChanges() throws Exception {
        HttpRequestItem item = new HttpRequestItem();
        item.setId("method-badge-request");
        item.setName("Badge Request");
        item.setMethod("GET");
        item.setProtocol(RequestItemProtocolEnum.HTTP);

        AtomicReference<JTabbedPane> tabbedPaneRef = new AtomicReference<>();
        AtomicReference<RequestEditSubPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RequestEditorPanel editorPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
            JTabbedPane tabbedPane = editorPanel.getTabbedPane();
            tabbedPane.removeAll();

            RequestEditSubPanel requestPanel = new RequestEditSubPanel(item.getId(), item.getProtocol());
            requestPanel.initPanelData(item);
            tabbedPane.addTab(item.getName(), requestPanel);
            tabbedPane.setTabComponentAt(0, ClosableTabComponent.forRequest(item.getName(), item));
            tabbedPane.setSelectedIndex(0);

            requestPanel.getRequestLinePanel().getMethodBox().setSelectedItem("POST");
            tabbedPaneRef.set(tabbedPane);
            panelRef.set(requestPanel);
        });
        SwingUtilities.invokeAndWait(() -> {
        });

        JTabbedPane tabbedPane = tabbedPaneRef.get();
        RequestEditSubPanel requestPanel = panelRef.get();
        assertNotNull(tabbedPane);
        assertNotNull(requestPanel);

        JLabel label = findLabel(tabbedPane.getTabComponentAt(tabbedPane.indexOfComponent(requestPanel)));
        assertNotNull(label, "request tab component should contain a title label");
        assertTrue(label.getText().contains("POST"), "request tab should show the current HTTP method");
        assertFalse(label.getText().contains("GET"), "request tab should not keep the stale HTTP method");
    }

    @Test
    public void eventStreamHttpRequestShouldRemainAvailableToAssistant() throws Exception {
        HttpRequestItem item = new HttpRequestItem();
        item.setId("assistant-sse-request");
        item.setName("SSE Request");
        item.setMethod("POST");
        item.setUrl("https://example.com/events");
        item.setProtocol(RequestItemProtocolEnum.HTTP);

        AtomicReference<HttpRequestItem> snapshotRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RequestEditorPanel editorPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
            JTabbedPane tabbedPane = editorPanel.getTabbedPane();
            tabbedPane.removeAll();

            RequestEditSubPanel requestPanel = new RequestEditSubPanel(item.getId(), item.getProtocol());
            requestPanel.initPanelData(item);
            tabbedPane.addTab(item.getName(), requestPanel);
            tabbedPane.setSelectedIndex(0);

            snapshotRef.set(editorPanel.getCurrentRequestSnapshotForAssistant());
        });

        assertNotNull(snapshotRef.get(), "HTTP event-stream requests should be available to the code snippet assistant");
        assertEquals(snapshotRef.get().getProtocol(), RequestItemProtocolEnum.HTTP);
    }

    @Test
    public void requestTabDisplayRefreshShouldPreserveExistingMarkers() throws Exception {
        HttpRequestItem item = new HttpRequestItem();
        item.setId("method-badge-state-request");
        item.setName("Badge State Request");
        item.setMethod("GET");
        item.setProtocol(RequestItemProtocolEnum.HTTP);

        AtomicReference<ClosableTabComponent> updatedTabRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tabbedPane = new JTabbedPane();
            RequestEditorTabStateController controller = new RequestEditorTabStateController(tabbedPane);
            RequestEditSubPanel requestPanel = new RequestEditSubPanel(item.getId(), item.getProtocol());
            requestPanel.initPanelData(item);

            tabbedPane.addTab(item.getName(), requestPanel);
            ClosableTabComponent originalTab = ClosableTabComponent.forRequest(item.getName(), item);
            originalTab.updateMarkers(markers -> markers.withDirty(true).withPreviewMode(true));
            tabbedPane.setTabComponentAt(0, originalTab);

            controller.updateRequestDisplay(requestPanel, "POST", RequestItemProtocolEnum.HTTP);
            updatedTabRef.set((ClosableTabComponent) tabbedPane.getTabComponentAt(0));
        });

        ClosableTabComponent updatedTab = updatedTabRef.get();
        assertNotNull(updatedTab);
        assertTrue(updatedTab.getMarkers().isDirty(), "display refresh should preserve dirty marker");
        assertFalse(updatedTab.getMarkers().isNewRequest(), "dirty marker should not also be marked as a new request");
        assertTrue(updatedTab.getMarkers().isPreviewMode(), "display refresh should preserve preview marker");

        JLabel label = findLabel(updatedTab);
        assertNotNull(label, "request tab component should contain a title label");
        assertTrue(label.getText().contains("POST"), "request tab should show the refreshed HTTP method");
    }

    @Test
    public void savedNewEventStreamRequestShouldUseHttpMethodBadge() throws Exception {
        HttpRequestItem item = new HttpRequestItem();
        item.setId("saved-curl-request");
        item.setName("Saved cURL Request");
        item.setMethod("POST");
        item.setProtocol(RequestItemProtocolEnum.HTTP);

        AtomicReference<ClosableTabComponent> updatedTabRef = new AtomicReference<>();
        AtomicReference<String> updatedTitleRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tabbedPane = new JTabbedPane();
            RequestEditorTabStateController controller = new RequestEditorTabStateController(tabbedPane);
            RequestEditSubPanel requestPanel = new RequestEditSubPanel("temporary-request", item.getProtocol());

            tabbedPane.addTab("New Request", requestPanel);
            ClosableTabComponent originalTab = ClosableTabComponent.forRequest("New Request", "GET", item.getProtocol());
            originalTab.updateMarkers(markers -> markers.withNewRequest(true).withDirty(true));
            tabbedPane.setTabComponentAt(0, originalTab);
            tabbedPane.setSelectedIndex(0);

            controller.refreshNewRequestTab(item.getName(), item);
            updatedTabRef.set((ClosableTabComponent) tabbedPane.getTabComponentAt(0));
            updatedTitleRef.set(tabbedPane.getTitleAt(0));
        });

        ClosableTabComponent updatedTab = updatedTabRef.get();
        assertNotNull(updatedTab);
        assertFalse(updatedTab.getMarkers().isNewRequest(), "saved new request tab should no longer show the yellow marker");
        assertFalse(updatedTab.getMarkers().isDirty(), "saved new request tab should no longer show the dirty marker");

        JLabel label = findLabel(updatedTab);
        assertNotNull(label, "request tab component should contain a title label");
        assertTrue(label.getText().contains("POST"), "event-stream requests should display their HTTP method");
        assertEquals(updatedTitleRef.get(), "Saved cURL Request", "saved cURL request tab title should show the saved name");
    }

    @Test
    public void requestTabMethodBadgeShouldRefreshAfterProgrammaticLoad() throws Exception {
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Loaded Badge Request");
        item.setMethod("POST");
        item.setProtocol(RequestItemProtocolEnum.HTTP);

        AtomicReference<JTabbedPane> tabbedPaneRef = new AtomicReference<>();
        AtomicReference<RequestEditSubPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RequestEditorPanel editorPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
            JTabbedPane tabbedPane = editorPanel.getTabbedPane();
            tabbedPane.removeAll();

            RequestEditSubPanel requestPanel = editorPanel.addNewTab(item.getName(), item.getProtocol());
            item.setId(requestPanel.getId());
            requestPanel.initPanelData(item);

            tabbedPaneRef.set(tabbedPane);
            panelRef.set(requestPanel);
        });
        SwingUtilities.invokeAndWait(() -> {
        });

        JTabbedPane tabbedPane = tabbedPaneRef.get();
        RequestEditSubPanel requestPanel = panelRef.get();
        assertNotNull(tabbedPane);
        assertNotNull(requestPanel);

        JLabel label = findLabel(tabbedPane.getTabComponentAt(tabbedPane.indexOfComponent(requestPanel)));
        assertNotNull(label, "request tab component should contain a title label");
        assertTrue(label.getText().contains("POST"), "loaded request tab should show the loaded HTTP method");
        assertFalse(label.getText().contains("GET"), "loaded request tab should not keep the default HTTP method");
    }

    @Test
    public void savedResponseTabShouldKeepSavedResponseIconWhenMethodChanges() throws Exception {
        SavedResponse savedResponse = new SavedResponse();
        savedResponse.setId("saved-response-method-badge");
        savedResponse.setName("Saved Response");

        AtomicReference<JLabel> labelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RequestEditorPanel editorPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
            JTabbedPane tabbedPane = editorPanel.getTabbedPane();
            tabbedPane.removeAll();

            RequestEditSubPanel savedResponsePanel = new RequestEditSubPanel(savedResponse);
            tabbedPane.addTab(savedResponse.getName(), savedResponsePanel);
            tabbedPane.setTabComponentAt(0,
                    new ClosableTabComponent(savedResponse.getName(), RequestItemProtocolEnum.SAVED_RESPONSE, false));
            tabbedPane.setSelectedIndex(0);

            savedResponsePanel.getRequestLinePanel().getMethodBox().setSelectedItem("POST");
            labelRef.set(findLabel(tabbedPane.getTabComponentAt(0)));
        });
        SwingUtilities.invokeAndWait(() -> {
        });

        JLabel label = labelRef.get();
        assertNotNull(label, "saved response tab component should contain a title label");
        assertNotNull(label.getIcon(), "saved response tab should keep its saved-response icon");
        assertFalse(label.getText().contains("POST"), "saved response tab should not be rebuilt as a request method tab");
    }

    private static JLabel findLabel(Component component) {
        if (component instanceof JLabel label) {
            return label;
        }
        if (!(component instanceof Container container)) {
            return null;
        }
        for (Component child : container.getComponents()) {
            JLabel label = findLabel(child);
            if (label != null) {
                return label;
            }
        }
        return null;
    }
}
