package com.laker.postman.panel.collections.editor;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.panel.collections.tree.RequestNameSelection;
import com.laker.postman.request.model.HttpRequestItem;
import org.testng.annotations.Test;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class RequestEditorSaveCoordinatorTest {

    @Test
    public void shouldSaveNewRequestThroughContextAndRefreshTab() {
        RequestEditorSaveCoordinator coordinator = new RequestEditorSaveCoordinator();
        FakeSaveContext context = new FakeSaveContext();
        RequestGroup group = new RequestGroup("Group");
        context.selectedRequest = new RequestNameSelection(group, "Saved name");

        boolean saved = coordinator.saveCurrentRequest(context);

        assertTrue(saved);
        assertTrue(context.pinnedTransientTab);
        assertEquals(context.currentRequest.getName(), "Saved name");
        assertEquals(context.currentRequest.getId(), "generated-id");
        assertSame(context.savedGroup, group);
        assertSame(context.savedRequest, context.currentRequest);
        assertEquals(context.refreshedTabName, "Saved name");
    }

    @Test
    public void shouldStopBeforeSavingWhenSettingsValidationFails() {
        RequestEditorSaveCoordinator coordinator = new RequestEditorSaveCoordinator();
        FakeSaveContext context = new FakeSaveContext();
        context.validationError = "bad timeout";

        boolean saved = coordinator.saveCurrentRequest(context);

        assertFalse(saved);
        assertTrue(context.pinnedTransientTab);
        assertEquals(context.settingsValidationErrorShown, "bad timeout");
        assertFalse(context.newRequestDialogOpened);
        assertFalse(context.existingRequestUpdated);
    }

    @Test
    public void shouldReturnFalseWhenNewRequestDialogIsCancelled() {
        RequestEditorSaveCoordinator coordinator = new RequestEditorSaveCoordinator();
        FakeSaveContext context = new FakeSaveContext();

        boolean saved = coordinator.saveCurrentRequest(context);

        assertFalse(saved);
        assertTrue(context.pinnedTransientTab);
        assertTrue(context.newRequestDialogOpened);
        assertFalse(context.newRequestSaved);
        assertFalse(context.newRequestTabRefreshed);
    }

    @Test
    public void shouldReturnFalseWhenExistingRequestUpdateFails() {
        RequestEditorSaveCoordinator coordinator = new RequestEditorSaveCoordinator();
        FakeSaveContext context = new FakeSaveContext();
        context.currentRequest.setId("request-1");
        context.currentRequest.setName("Persisted");
        context.existingUpdateResult = false;

        boolean saved = coordinator.saveCurrentRequest(context);

        assertFalse(saved);
        assertTrue(context.existingRequestUpdated);
        assertSame(context.updatedExistingRequest, context.currentRequest);
        assertSame(context.updateFailureRequest, context.currentRequest);
    }

    @Test
    public void shouldReturnTrueWhenExistingRequestUpdateSucceeds() {
        RequestEditorSaveCoordinator coordinator = new RequestEditorSaveCoordinator();
        FakeSaveContext context = new FakeSaveContext();
        context.currentRequest.setId("request-1");
        context.currentRequest.setName("Persisted");

        boolean saved = coordinator.saveCurrentRequest(context);

        assertTrue(saved);
        assertTrue(context.existingRequestUpdated);
        assertSame(context.updatedExistingRequest, context.currentRequest);
    }

    private static final class FakeSaveContext implements RequestEditorSaveCoordinator.SaveContext {
        private final HttpRequestItem currentRequest = new HttpRequestItem();
        private final TreeModel groupTreeModel = new DefaultTreeModel(null);
        private boolean savedResponseTab;
        private boolean savedResponseRequestSaved;
        private boolean pinnedTransientTab;
        private String validationError;
        private String settingsValidationErrorShown;
        private boolean newRequestDialogOpened;
        private RequestNameSelection selectedRequest;
        private RequestGroup savedGroup;
        private HttpRequestItem savedRequest;
        private String refreshedTabName;
        private boolean newRequestSaved;
        private boolean newRequestTabRefreshed;
        private boolean existingUpdateResult = true;
        private boolean existingRequestUpdated;
        private HttpRequestItem updatedExistingRequest;
        private HttpRequestItem updateFailureRequest;

        @Override
        public boolean isSavedResponseTab() {
            return savedResponseTab;
        }

        @Override
        public boolean saveSavedResponseRequest() {
            return savedResponseRequestSaved;
        }

        @Override
        public void pinTransientTab() {
            pinnedTransientTab = true;
        }

        @Override
        public String validateRequestSettings() {
            return validationError;
        }

        @Override
        public void showSettingsValidationError(String error) {
            settingsValidationErrorShown = error;
        }

        @Override
        public HttpRequestItem currentRequest() {
            return currentRequest;
        }

        @Override
        public void onNoRequestToSave() {
        }

        @Override
        public TreeModel groupTreeModel() {
            return groupTreeModel;
        }

        @Override
        public Optional<RequestNameSelection> chooseGroupAndRequestName(
                TreeModel groupTreeModel,
                String defaultName
        ) {
            newRequestDialogOpened = true;
            return Optional.ofNullable(selectedRequest);
        }

        @Override
        public String newRequestId() {
            return "generated-id";
        }

        @Override
        public void saveRequestToGroup(RequestGroup group, HttpRequestItem item) {
            newRequestSaved = true;
            savedGroup = group;
            savedRequest = item;
        }

        @Override
        public void refreshNewRequestTab(String requestName, HttpRequestItem item) {
            newRequestTabRefreshed = true;
            refreshedTabName = requestName;
        }

        @Override
        public boolean updateExistingRequest(HttpRequestItem item) {
            existingRequestUpdated = true;
            updatedExistingRequest = item;
            return existingUpdateResult;
        }

        @Override
        public void showUpdateExistingRequestFailed(HttpRequestItem item) {
            updateFailureRequest = item;
        }
    }
}
