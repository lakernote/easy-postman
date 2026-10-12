package com.laker.postman.panel.collections.editor;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.panel.collections.tree.RequestNameSelection;
import com.laker.postman.request.model.HttpRequestItem;

import javax.swing.tree.TreeModel;
import java.util.Optional;

final class RequestEditorSaveCoordinator {

    boolean saveCurrentRequest(SaveContext context) {
        if (context.isSavedResponseTab()) {
            context.showSavedResponseReadonly();
            return false;
        }

        context.pinTransientTab();

        String settingsValidationError = context.validateRequestSettings();
        if (settingsValidationError != null) {
            context.showSettingsValidationError(settingsValidationError);
            return false;
        }

        HttpRequestItem currentItem = context.currentRequest();
        if (currentItem == null) {
            context.onNoRequestToSave();
            return false;
        }

        if (currentItem.isNewRequest()) {
            return saveNewRequest(context, currentItem);
        }

        if (!context.updateExistingRequest(currentItem)) {
            context.showUpdateExistingRequestFailed(currentItem);
            return false;
        }
        return true;
    }

    private boolean saveNewRequest(SaveContext context, HttpRequestItem item) {
        Optional<RequestNameSelection> selection = context.chooseGroupAndRequestName(
                context.groupTreeModel(),
                item.getName()
        );
        if (selection.isEmpty()) {
            return false;
        }

        RequestNameSelection requestSelection = selection.get();
        String requestName = requestSelection.requestName();
        item.setName(requestName);
        item.setId(context.newRequestId());
        context.saveRequestToGroup(requestSelection.group(), item);
        context.refreshNewRequestTab(requestName, item);
        return true;
    }

    interface SaveContext {
        boolean isSavedResponseTab();

        void showSavedResponseReadonly();

        void pinTransientTab();

        String validateRequestSettings();

        void showSettingsValidationError(String error);

        HttpRequestItem currentRequest();

        void onNoRequestToSave();

        TreeModel groupTreeModel();

        Optional<RequestNameSelection> chooseGroupAndRequestName(
                TreeModel groupTreeModel,
                String defaultName
        );

        String newRequestId();

        void saveRequestToGroup(RequestGroup group, HttpRequestItem item);

        void refreshNewRequestTab(String requestName, HttpRequestItem item);

        boolean updateExistingRequest(HttpRequestItem item);

        void showUpdateExistingRequestFailed(HttpRequestItem item);
    }
}
