package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.panel.collections.editor.request.sub.AuthTabPanel;
import com.laker.postman.panel.collections.editor.request.sub.EasyRequestHttpHeadersPanel;
import com.laker.postman.panel.collections.editor.request.sub.EasyRequestParamsPanel;
import com.laker.postman.panel.collections.editor.request.sub.RequestBodyPanel;
import com.laker.postman.panel.collections.editor.request.sub.ResponsePanel;
import com.laker.postman.util.FileMimeTypeUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.function.BooleanSupplier;

@UtilityClass
class RequestEditorUiBinder {
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String CONTENT_TYPE_MULTIPART_FORM = "multipart/form-data";
    private static final String CONTENT_TYPE_BINARY_FALLBACK = FileMimeTypeUtil.DEFAULT_MIME_TYPE;

    static void bindUrlField(JTextField urlField,
                             RequestPreparationFeedbackPresenter feedbackPresenter,
                             Runnable detectAndParseCurl,
                             Runnable parseUrlParamsToParamsPanel,
                             Runnable autoPrependProtocolAction) {
        urlField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                feedbackPresenter.clearUrlValidationFeedback(urlField);
                detectAndParseCurl.run();
                parseUrlParamsToParamsPanel.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                feedbackPresenter.clearUrlValidationFeedback(urlField);
                parseUrlParamsToParamsPanel.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                feedbackPresenter.clearUrlValidationFeedback(urlField);
                detectAndParseCurl.run();
                parseUrlParamsToParamsPanel.run();
            }
        });
        urlField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                autoPrependProtocolAction.run();
            }
        });
        urlField.addActionListener(e -> autoPrependProtocolAction.run());
    }

    static void bindParamsSync(EasyRequestParamsPanel paramsPanel, Runnable parseParamsPanelToUrl) {
        paramsPanel.addTableModelListener(e -> parseParamsPanelToUrl.run());
    }

    static void applyInitialProtocolUi(RequestItemProtocolEnum protocol,
                                       JTabbedPane reqTabs,
                                       RequestBodyPanel requestBodyPanel,
                                       JComponent paramsTabPanel,
                                       AuthTabPanel authTabPanel,
                                       ActionListener wsSendAction) {
        if (protocol.isWebSocketProtocol()) {
            requestBodyPanel.setWsSendActionListener(wsSendAction);
            RequestTabSelector.removeIfPresent(reqTabs, authTabPanel);
            RequestTabSelector.selectFirstVisible(reqTabs, requestBodyPanel, paramsTabPanel);
            requestBodyPanel.setWebSocketConnected(false);
        } else {
            RequestTabSelector.selectFirstVisible(reqTabs, paramsTabPanel, requestBodyPanel);
        }
    }

    static void bindSaveResponseButton(RequestItemProtocolEnum protocol,
                                       RequestEditSubPanelType panelType,
                                       ResponsePanel responsePanel,
                                       ActionListener saveAction) {
        if (responsePanel == null) {
            return;
        }
        if (protocol.isHttpProtocol()
                && panelType != RequestEditSubPanelType.PERFORMANCE_SNAPSHOT
                && responsePanel.getSaveResponseButton() != null) {
            responsePanel.getSaveResponseButton().addActionListener(saveAction);
        }
    }

    static void bindBodyTypeHeaderSync(RequestBodyPanel requestBodyPanel,
                                       EasyRequestHttpHeadersPanel headersPanel,
                                       BooleanSupplier isLoadingDataSupplier) {
        BinaryContentTypeSyncState syncState = new BinaryContentTypeSyncState();
        requestBodyPanel.getBodyTypeComboBox().addActionListener(e -> {
            if (isLoadingDataSupplier.getAsBoolean()) {
                syncState.captureLoadedState(headersPanel, requestBodyPanel.getBinaryFilePath());
                return;
            }

            String selectedType = (String) requestBodyPanel.getBodyTypeComboBox().getSelectedItem();
            if (RequestBodyPanel.BODY_TYPE_NONE.equals(selectedType)) {
                headersPanel.removeHeader(CONTENT_TYPE);
                syncState.clearAutoContentType();
                return;
            }

            String contentType = null;
            if (RequestBodyPanel.BODY_TYPE_RAW.equals(selectedType)) {
                contentType = CONTENT_TYPE_JSON;
            } else if (RequestBodyPanel.BODY_TYPE_FORM_URLENCODED.equals(selectedType)) {
                contentType = CONTENT_TYPE_FORM_URLENCODED;
            } else if (RequestBodyPanel.BODY_TYPE_FORM_DATA.equals(selectedType)) {
                contentType = CONTENT_TYPE_MULTIPART_FORM;
            } else if (RequestBodyPanel.BODY_TYPE_BINARY.equals(selectedType)) {
                contentType = detectBinaryContentType(requestBodyPanel.getBinaryFilePath());
            }
            if (contentType != null) {
                headersPanel.setOrUpdateHeader(CONTENT_TYPE, contentType);
                syncState.rememberAutoContentType(requestBodyPanel.getBinaryFilePath(), contentType);
            }
        });
        if (requestBodyPanel.getBinaryFilePathField() != null) {
            requestBodyPanel.getBinaryFilePathField().getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    syncBinaryFileContentType();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    syncBinaryFileContentType();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    syncBinaryFileContentType();
                }

                private void syncBinaryFileContentType() {
                    String currentPath = requestBodyPanel.getBinaryFilePath();
                    if (isLoadingDataSupplier.getAsBoolean()) {
                        syncState.captureLoadedState(headersPanel, currentPath);
                        return;
                    }
                    if (!RequestBodyPanel.BODY_TYPE_BINARY.equals(requestBodyPanel.getBodyType())) {
                        syncState.rememberBinaryFilePath(currentPath);
                        return;
                    }
                    String currentContentType = findEnabledHeaderValue(headersPanel, CONTENT_TYPE);
                    if (currentContentType != null && !isAutoManagedContentType(currentContentType, syncState)) {
                        syncState.rememberBinaryFilePath(currentPath);
                        return;
                    }
                    String contentType = detectBinaryContentType(currentPath);
                    headersPanel.setOrUpdateHeader(CONTENT_TYPE, contentType);
                    syncState.rememberAutoContentType(currentPath, contentType);
                }
            });
        }
    }

    private static String detectBinaryContentType(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return CONTENT_TYPE_BINARY_FALLBACK;
        }
        return FileMimeTypeUtil.detectMimeType(filePath);
    }

    private static boolean isAutoManagedContentType(String currentContentType, BinaryContentTypeSyncState syncState) {
        if (currentContentType == null || currentContentType.isBlank()) {
            return true;
        }
        String lastAutoContentType = syncState.lastAutoContentType;
        if (lastAutoContentType != null && currentContentType.equalsIgnoreCase(lastAutoContentType)) {
            return true;
        }
        String previousDetectedContentType = syncState.detectLastBinaryFileContentType();
        if (previousDetectedContentType != null && currentContentType.equalsIgnoreCase(previousDetectedContentType)) {
            return true;
        }
        return CONTENT_TYPE_BINARY_FALLBACK.equalsIgnoreCase(currentContentType)
                || CONTENT_TYPE_JSON.equalsIgnoreCase(currentContentType)
                || CONTENT_TYPE_FORM_URLENCODED.equalsIgnoreCase(currentContentType)
                || CONTENT_TYPE_MULTIPART_FORM.equalsIgnoreCase(currentContentType);
    }

    private static String findEnabledHeaderValue(EasyRequestHttpHeadersPanel headersPanel, String key) {
        return headersPanel.getHeadersListFromModel().stream()
                .filter(header -> header.isEnabled() && header.getKey() != null && key.equalsIgnoreCase(header.getKey().trim()))
                .map(header -> header.getValue() == null ? "" : header.getValue().trim())
                .findFirst()
                .orElse(null);
    }

    private static final class BinaryContentTypeSyncState {
        private String lastAutoContentType;
        private String lastBinaryFilePath;

        private void captureLoadedState(EasyRequestHttpHeadersPanel headersPanel, String filePath) {
            rememberBinaryFilePath(filePath);
            String currentContentType = findEnabledHeaderValue(headersPanel, CONTENT_TYPE);
            String detectedContentType = detectBinaryContentType(filePath);
            if (currentContentType != null && currentContentType.equalsIgnoreCase(detectedContentType)) {
                lastAutoContentType = currentContentType;
            }
        }

        private void rememberAutoContentType(String filePath, String contentType) {
            lastBinaryFilePath = filePath;
            lastAutoContentType = contentType;
        }

        private void rememberBinaryFilePath(String filePath) {
            lastBinaryFilePath = filePath;
        }

        private void clearAutoContentType() {
            lastAutoContentType = null;
        }

        private String detectLastBinaryFileContentType() {
            if (lastBinaryFilePath == null || lastBinaryFilePath.isBlank()) {
                return null;
            }
            return detectBinaryContentType(lastBinaryFilePath);
        }
    }
}
