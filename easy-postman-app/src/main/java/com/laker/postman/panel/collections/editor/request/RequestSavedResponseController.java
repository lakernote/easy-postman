package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.panel.collections.editor.request.sub.RequestLinePanel;
import com.laker.postman.panel.collections.editor.request.sub.ResponsePanel;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.JOptionPane;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 已保存响应动作控制器。
 * <p>
 * 保存、加载和展示已保存响应都属于请求编辑器动作编排，不放在请求编辑面板布局类里。
 */
@Slf4j
@RequiredArgsConstructor
final class RequestSavedResponseController {
    private final Component dialogParent;
    private final SavedResponseUiController savedResponseUiController;
    private final Supplier<PreparedRequest> lastRequestSupplier;
    private final Supplier<HttpResponse> lastResponseSupplier;
    private final Supplier<HttpRequestItem> originalRequestSupplier;
    private final Supplier<SavedResponse> savedResponseSupplier;
    private final Supplier<HttpRequestItem> savedResponseOwnerSupplier;
    private final RequestEditorBinder requestEditorBinder;
    private final RequestEditorDefaultTabSelector requestEditorDefaultTabSelector;
    private final Supplier<RequestItemProtocolEnum> effectiveProtocolSupplier;
    private final Consumer<Boolean> loadingStateSetter;
    private final ResponsePanel responsePanel;
    private final RequestLinePanel requestLinePanel;
    private final ActionListener sendAction;

    void showSaveDialog() {
        HttpResponse lastResponse = lastResponseSupplier.get();
        if (lastResponse == null) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_NO_RESPONSE));
            return;
        }

        SavedResponse currentExample = savedResponseSupplier.get();
        HttpRequestItem owner = savedResponseOwnerSupplier.get();
        if (currentExample != null && owner != null) {
            Object[] choices = {
                    I18nUtil.getMessage(MessageKeys.SAVED_RESPONSE_UPDATE_EXAMPLE),
                    I18nUtil.getMessage(MessageKeys.SAVED_RESPONSE_SAVE_AS_NEW)
            };
            int choice = JOptionPane.showOptionDialog(dialogParent,
                    I18nUtil.getMessage(MessageKeys.SAVED_RESPONSE_SAVE_CHOICE),
                    I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_DIALOG_TITLE),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
            if (choice == 0) {
                savedResponseUiController.updateExampleResponse(owner, currentExample,
                        lastRequestSupplier.get(), lastResponse);
            } else if (choice == 1) {
                String name = savedResponseUiController.promptResponseName(dialogParent);
                if (name != null && !name.trim().isEmpty()) {
                    savedResponseUiController.saveNewExample(owner, name.trim(),
                            lastRequestSupplier.get(), lastResponse);
                }
            }
            return;
        }

        HttpRequestItem originalRequest = originalRequestSupplier.get();
        if (originalRequest == null || originalRequest.isNewRequest()) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_REQUEST_NOT_SAVED));
            return;
        }

        String name = savedResponseUiController.promptResponseName(dialogParent);
        if (name != null && !name.trim().isEmpty()) {
            savedResponseUiController.saveResponse(name.trim(), lastRequestSupplier.get(), lastResponse, originalRequest);
        }
    }

    void loadSavedResponse(SavedResponse savedResponse) {
        if (savedResponse == null) {
            return;
        }
        try {
            SavedResponse.OriginalRequest originalRequest = savedResponse.getOriginalRequest();
            if (originalRequest != null) {
                populateOriginalRequest(originalRequest);
                if (originalRequest.isBodyTruncated()) {
                    int previewLength = originalRequest.getBody() == null ? 0 : originalRequest.getBody().length();
                    NotificationCenter.showWarning(I18nUtil.getMessage(
                            MessageKeys.SAVED_RESPONSE_ORIGINAL_BODY_TRUNCATED,
                            previewLength
                    ));
                }
            }
            savedResponseUiController.displaySavedResponse(responsePanel, requestLinePanel, sendAction, savedResponse);
        } catch (Exception ex) {
            log.error("加载保存的响应失败", ex);
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.RESPONSE_LOAD_ERROR, ex.getMessage()));
        }
    }

    private void populateOriginalRequest(SavedResponse.OriginalRequest originalRequest) {
        loadingStateSetter.accept(true);
        try {
            requestEditorBinder.populateSavedResponseRequest(originalRequest);
            requestEditorDefaultTabSelector.selectBySavedResponse(effectiveProtocolSupplier.get(), originalRequest);
        } finally {
            loadingStateSetter.accept(false);
        }
    }
}
