package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.common.component.dialog.TextInputDialog;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.panel.collections.editor.CollectionTreeEditorGateway;
import com.laker.postman.panel.collections.editor.request.sub.RequestLinePanel;
import com.laker.postman.panel.collections.editor.request.sub.ResponsePanel;
import com.laker.postman.service.collections.SavedResponseSnapshotMapper;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * 已保存响应相关的 UI 操作控制器。
 */
@Slf4j
@RequiredArgsConstructor
final class SavedResponseUiController {
    private final CollectionTreeEditorGateway collectionTreeGateway;

    String promptResponseName(Component owner) {
        String defaultName = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        return TextInputDialog.showRequiredName(
                owner,
                I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_DIALOG_TITLE),
                defaultName,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_SAVED_RESPONSE_EMPTY)
        ).orElse(null);
    }

    void saveResponse(String name,
                      PreparedRequest lastRequest,
                      HttpResponse lastResponse,
                      HttpRequestItem originalRequestItem) {
        try {
            SavedResponse savedResponse = SavedResponseSnapshotMapper.fromExchange(name, lastRequest, lastResponse);

            if (!collectionTreeGateway.saveResponseForRequest(originalRequestItem, savedResponse)) {
                log.warn("无法找到请求节点，保存响应失败");
                NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_REQUEST_NOT_FOUND));
                return;
            }

            NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_SUCCESS, name));
        } catch (Exception ex) {
            log.error("保存响应失败", ex);
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_ERROR, ex.getMessage()));
        }
    }

    boolean updateExampleRequest(String requestId, SavedResponse savedResponse, HttpRequestItem editedRequest) {
        if (requestId == null || savedResponse == null || editedRequest == null) return false;
        SavedResponse.OriginalRequest updatedRequest = SavedResponseSnapshotMapper.toOriginalRequest(editedRequest);
        SavedResponse.OriginalRequest previousRequest = savedResponse.getOriginalRequest();
        if (previousRequest != null && previousRequest.isBodyTruncated()
                && Objects.equals(previousRequest.getBody(), editedRequest.getBody())) {
            updatedRequest.setBodyTruncated(true);
            updatedRequest.setOriginalBodySize(previousRequest.getOriginalBodySize());
        }
        savedResponse.setOriginalRequest(updatedRequest);
        if (!collectionTreeGateway.upsertResponseForRequest(requestId, savedResponse)) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_REQUEST_NOT_FOUND));
            return false;
        }
        return true;
    }

    void updateExampleResponse(HttpRequestItem owner, SavedResponse example,
                               PreparedRequest request, HttpResponse response) {
        SavedResponse updated = SavedResponseSnapshotMapper.fromExchange(example.getName(), request, response);
        updated.setId(example.getId());
        updated.setMockDelayMs(example.getMockDelayMs());
        updated.setMockScript(example.getMockScript());
        // The exchange mapper currently doesn't extract cookie metadata from HttpResponse.
        updated.setCookies(example.getCookies());
        copySnapshot(updated, example);
        if (!collectionTreeGateway.upsertResponseForRequest(owner.getId(), example)) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_REQUEST_NOT_FOUND));
            return;
        }
        NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.SAVED_RESPONSE_UPDATED, example.getName()));
    }

    void saveNewExample(HttpRequestItem owner, String name, PreparedRequest request, HttpResponse response) {
        SavedResponse example = SavedResponseSnapshotMapper.fromExchange(name, request, response);
        if (!collectionTreeGateway.saveResponseForRequest(owner, example)) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_REQUEST_NOT_FOUND));
            return;
        }
        NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.RESPONSE_SAVE_SUCCESS, name));
    }

    private void copySnapshot(SavedResponse source, SavedResponse target) {
        target.setTimestamp(source.getTimestamp());
        target.setOriginalRequest(source.getOriginalRequest());
        target.setCode(source.getCode());
        target.setStatus(source.getStatus());
        target.setHeaders(source.getHeaders());
        target.setCookies(source.getCookies());
        target.setBody(source.getBody());
        target.setPreviewLanguage(source.getPreviewLanguage());
        target.setCostMs(source.getCostMs());
        target.setBodySize(source.getBodySize());
        target.setHeadersSize(source.getHeadersSize());
    }

    void displaySavedResponse(ResponsePanel responsePanel,
                              RequestLinePanel requestLinePanel,
                              ActionListener sendAction,
                              SavedResponse savedResponse) {
        HttpResponse response = SavedResponseSnapshotMapper.toRuntimeResponse(savedResponse);
        SwingUtilities.invokeLater(() -> {
            requestLinePanel.setSendButtonToSend(sendAction);
            responsePanel.setResponseTabButtonsEnable(true);
            if (responsePanel.getSaveResponseButton() != null) {
                responsePanel.getSaveResponseButton().setEnabled(false);
            }
            responsePanel.setResponseBody(response);
            responsePanel.setResponseHeaders(response);
            responsePanel.setStatus(response.code);
            responsePanel.setResponseTime(response.costMs);
            responsePanel.setResponseSize(response.bodySize, null);
            responsePanel.switchToTab(0);
            responsePanel.setResponseBodyEnabled(true);
        });
    }
}
