package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.setting.SettingsFieldRow;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 现代化请求设置面板
 */
@Slf4j
public class RequestSettingsPanelModern extends ModernSettingsPanel {
    private static final int FIELD_SPACING = 8;
    private static final int SECTION_SPACING = 12;
    private static final int COMPACT_SECTION_SPACING = 4;
    private static final int COMPACT_SECTION_BOTTOM_PADDING = 8;
    private static final int SCRIPT_FIELD_WIDTH = SettingsFieldRow.DEFAULT_FIELD_WIDTH;
    private static final int BYTES_PER_KB = 1024;

    private JTextField maxBodySizeField;
    private JTextField requestTimeoutField;
    private JTextField maxDownloadSizeField;
    private JCheckBox followRedirectsCheckBox;
    private JCheckBox sslVerificationDisabledCheckBox;
    private JComboBox<String> defaultProtocolComboBox;
    private JCheckBox docsTabVisibleCheckBox;
    private JCheckBox paramsTabVisibleCheckBox;
    private JCheckBox authTabVisibleCheckBox;
    private JCheckBox headersTabVisibleCheckBox;
    private JCheckBox bodyTabVisibleCheckBox;
    private JCheckBox scriptsTabVisibleCheckBox;
    private JCheckBox settingsTabVisibleCheckBox;
    private JCheckBox remoteScriptRequireEnabledCheckBox;
    private JCheckBox remoteScriptRequireAllowHttpCheckBox;
    private JTextField remoteScriptAllowedHostsField;
    private JTextField remoteScriptConnectTimeoutField;
    private JTextField remoteScriptReadTimeoutField;
    private JTextField remoteScriptMaxSizeField;
    private SettingsFieldRow remoteScriptAllowedHostsRow;
    private SettingsFieldRow remoteScriptConnectTimeoutRow;
    private SettingsFieldRow remoteScriptReadTimeoutRow;
    private SettingsFieldRow remoteScriptMaxSizeRow;

    @Override
    protected void buildContent(JPanel contentPanel) {
        int fieldLabelWidth = calculatePageFieldLabelWidth();
        JPanel requestSection = createModernSection(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_TITLE),
                ""
        );

        maxBodySizeField = new JTextField(10);
        int maxBodySizeKB = SettingManager.getMaxBodySize() / 1024;
        maxBodySizeField.setText(String.valueOf(maxBodySizeKB));
        JPanel maxBodySizeRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_MAX_BODY_SIZE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_MAX_BODY_SIZE_TOOLTIP),
                maxBodySizeField,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        requestSection.add(maxBodySizeRow);
        requestSection.add(createVerticalSpace(FIELD_SPACING));

        requestTimeoutField = new JTextField(10);
        requestTimeoutField.setText(String.valueOf(SettingManager.getRequestTimeout()));
        JPanel requestTimeoutRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_TIMEOUT),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_TIMEOUT_TOOLTIP),
                requestTimeoutField,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        requestSection.add(requestTimeoutRow);
        requestSection.add(createVerticalSpace(FIELD_SPACING));

        maxDownloadSizeField = new JTextField(10);
        int maxDownloadSizeMB = SettingManager.getMaxDownloadSize() / (1024 * 1024);
        maxDownloadSizeField.setText(String.valueOf(maxDownloadSizeMB));
        JPanel maxDownloadSizeRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_MAX_DOWNLOAD_SIZE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_MAX_DOWNLOAD_SIZE_TOOLTIP),
                maxDownloadSizeField,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        requestSection.add(maxDownloadSizeRow);
        requestSection.add(createVerticalSpace(FIELD_SPACING));

        followRedirectsCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_FOLLOW_REDIRECTS_CHECKBOX),
                SettingManager.isFollowRedirects()
        );
        JPanel followRedirectsRow = createCheckBoxRow(
                followRedirectsCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_FOLLOW_REDIRECTS_TOOLTIP)
        );
        requestSection.add(followRedirectsRow);
        requestSection.add(createVerticalSpace(FIELD_SPACING));

        sslVerificationDisabledCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SSL_VERIFICATION_CHECKBOX),
                SettingManager.isRequestSslVerificationDisabled()
        );
        JPanel sslVerificationRow = createCheckBoxRow(
                sslVerificationDisabledCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SSL_VERIFICATION_TOOLTIP)
        );
        requestSection.add(sslVerificationRow);
        requestSection.add(createVerticalSpace(FIELD_SPACING));

        String[] protocolOptions = {"https", "http"};
        defaultProtocolComboBox = new JComboBox<>(protocolOptions);
        defaultProtocolComboBox.setSelectedItem(SettingManager.getDefaultProtocol());
        JPanel defaultProtocolRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_DEFAULT_PROTOCOL),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_DEFAULT_PROTOCOL_TOOLTIP),
                defaultProtocolComboBox,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        requestSection.add(defaultProtocolRow);

        contentPanel.add(requestSection);
        contentPanel.add(createVerticalSpace(SECTION_SPACING));

        JPanel requestEditorTabSection = createModernSection(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_EDITOR_TABS_TITLE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_EDITOR_TABS_DESCRIPTION)
        );
        docsTabVisibleCheckBox = createRequestEditorTabCheckBox(
                I18nUtil.getMessage(MessageKeys.REQUEST_DOCS_TAB_TITLE),
                SettingManager.REQUEST_EDITOR_TAB_DOCS
        );
        paramsTabVisibleCheckBox = createRequestEditorTabCheckBox(
                I18nUtil.getMessage(MessageKeys.TAB_PARAMS),
                SettingManager.REQUEST_EDITOR_TAB_PARAMS
        );
        authTabVisibleCheckBox = createRequestEditorTabCheckBox(
                I18nUtil.getMessage(MessageKeys.TAB_AUTHORIZATION),
                SettingManager.REQUEST_EDITOR_TAB_AUTH
        );
        headersTabVisibleCheckBox = createRequestEditorTabCheckBox(
                I18nUtil.getMessage(MessageKeys.TAB_REQUEST_HEADERS),
                SettingManager.REQUEST_EDITOR_TAB_HEADERS
        );
        bodyTabVisibleCheckBox = createRequestEditorTabCheckBox(
                I18nUtil.getMessage(MessageKeys.TAB_REQUEST_BODY),
                SettingManager.REQUEST_EDITOR_TAB_BODY
        );
        scriptsTabVisibleCheckBox = createRequestEditorTabCheckBox(
                I18nUtil.getMessage(MessageKeys.TAB_SCRIPTS),
                SettingManager.REQUEST_EDITOR_TAB_SCRIPTS
        );
        settingsTabVisibleCheckBox = createRequestEditorTabCheckBox(
                I18nUtil.getMessage(MessageKeys.TAB_SETTINGS),
                SettingManager.REQUEST_EDITOR_TAB_SETTINGS
        );
        addRequestEditorTabRow(requestEditorTabSection, docsTabVisibleCheckBox);
        addRequestEditorTabRow(requestEditorTabSection, paramsTabVisibleCheckBox);
        addRequestEditorTabRow(requestEditorTabSection, authTabVisibleCheckBox);
        addRequestEditorTabRow(requestEditorTabSection, headersTabVisibleCheckBox);
        addRequestEditorTabRow(requestEditorTabSection, bodyTabVisibleCheckBox);
        addRequestEditorTabRow(requestEditorTabSection, scriptsTabVisibleCheckBox);
        requestEditorTabSection.add(createCheckBoxRow(
                settingsTabVisibleCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_EDITOR_TABS_TOOLTIP)
        ));
        requestEditorTabSection.setBorder(BorderFactory.createEmptyBorder(0, 0, COMPACT_SECTION_BOTTOM_PADDING, 0));

        contentPanel.add(requestEditorTabSection);
        contentPanel.add(createVerticalSpace(COMPACT_SECTION_SPACING));

        JPanel scriptSection = createModernSection(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_TITLE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_DESCRIPTION)
        );

        remoteScriptRequireEnabledCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_ENABLED),
                SettingManager.isRemoteJsRequireEnabled()
        );
        scriptSection.add(createCheckBoxRow(
                remoteScriptRequireEnabledCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_ENABLED_TOOLTIP)
        ));
        scriptSection.add(createVerticalSpace(FIELD_SPACING));

        remoteScriptRequireAllowHttpCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_ALLOW_HTTP),
                SettingManager.isInsecureRemoteJsRequireEnabled()
        );
        scriptSection.add(createCheckBoxRow(
                remoteScriptRequireAllowHttpCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_ALLOW_HTTP_TOOLTIP)
        ));
        scriptSection.add(createVerticalSpace(FIELD_SPACING));

        remoteScriptAllowedHostsField = new JTextField(10);
        remoteScriptAllowedHostsField.setText(SettingManager.getRemoteJsRequireAllowedHosts());
        remoteScriptAllowedHostsRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_ALLOWED_HOSTS),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_ALLOWED_HOSTS_TOOLTIP),
                remoteScriptAllowedHostsField,
                fieldLabelWidth,
                SCRIPT_FIELD_WIDTH
        );
        scriptSection.add(remoteScriptAllowedHostsRow);
        scriptSection.add(createVerticalSpace(FIELD_SPACING));

        remoteScriptConnectTimeoutField = new JTextField(10);
        remoteScriptConnectTimeoutField.setText(String.valueOf(SettingManager.getRemoteJsRequireConnectTimeoutMs()));
        remoteScriptConnectTimeoutRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_CONNECT_TIMEOUT),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_CONNECT_TIMEOUT_TOOLTIP),
                remoteScriptConnectTimeoutField,
                fieldLabelWidth,
                SCRIPT_FIELD_WIDTH
        );
        scriptSection.add(remoteScriptConnectTimeoutRow);
        scriptSection.add(createVerticalSpace(FIELD_SPACING));

        remoteScriptReadTimeoutField = new JTextField(10);
        remoteScriptReadTimeoutField.setText(String.valueOf(SettingManager.getRemoteJsRequireReadTimeoutMs()));
        remoteScriptReadTimeoutRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_READ_TIMEOUT),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_READ_TIMEOUT_TOOLTIP),
                remoteScriptReadTimeoutField,
                fieldLabelWidth,
                SCRIPT_FIELD_WIDTH
        );
        scriptSection.add(remoteScriptReadTimeoutRow);
        scriptSection.add(createVerticalSpace(FIELD_SPACING));

        remoteScriptMaxSizeField = new JTextField(10);
        remoteScriptMaxSizeField.setText(String.valueOf(SettingManager.getRemoteJsRequireMaxBytes() / BYTES_PER_KB));
        remoteScriptMaxSizeRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_MAX_SIZE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_MAX_SIZE_TOOLTIP),
                remoteScriptMaxSizeField,
                fieldLabelWidth,
                SCRIPT_FIELD_WIDTH
        );
        scriptSection.add(remoteScriptMaxSizeRow);

        contentPanel.add(scriptSection);
        contentPanel.add(createVerticalSpace(SECTION_SPACING));

        setupValidators();
        updateRemoteScriptControls();

        trackComponentValue(maxBodySizeField);
        trackComponentValue(requestTimeoutField);
        trackComponentValue(maxDownloadSizeField);
        trackComponentValue(followRedirectsCheckBox);
        trackComponentValue(sslVerificationDisabledCheckBox);
        trackComponentValue(defaultProtocolComboBox);
        trackRequestEditorTabCheckBoxes();
        trackComponentValue(remoteScriptRequireEnabledCheckBox);
        trackComponentValue(remoteScriptRequireAllowHttpCheckBox);
        trackComponentValue(remoteScriptAllowedHostsField);
        trackComponentValue(remoteScriptConnectTimeoutField);
        trackComponentValue(remoteScriptReadTimeoutField);
        trackComponentValue(remoteScriptMaxSizeField);
    }

    private void setupValidators() {
        setupValidator(
                maxBodySizeField,
                this::isPositiveInteger,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_MAX_BODY_SIZE_ERROR)
        );
        setupValidator(
                requestTimeoutField,
                this::isPositiveInteger,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_TIMEOUT_ERROR)
        );
        setupValidator(
                maxDownloadSizeField,
                this::isPositiveInteger,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_MAX_DOWNLOAD_SIZE_ERROR)
        );
        setupValidator(
                remoteScriptConnectTimeoutField,
                value -> !remoteScriptRequireEnabledCheckBox.isSelected() || isStrictlyPositiveInteger(value),
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_SCRIPT_REMOTE_CONNECT_TIMEOUT_ERROR)
        );
        setupValidator(
                remoteScriptReadTimeoutField,
                value -> !remoteScriptRequireEnabledCheckBox.isSelected() || isStrictlyPositiveInteger(value),
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_SCRIPT_REMOTE_READ_TIMEOUT_ERROR)
        );
        setupValidator(
                remoteScriptMaxSizeField,
                value -> !remoteScriptRequireEnabledCheckBox.isSelected() || isStrictlyPositiveInteger(value),
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_SCRIPT_REMOTE_MAX_SIZE_ERROR)
        );
    }

    @Override
    protected void registerListeners() {
        remoteScriptRequireEnabledCheckBox.addItemListener(e -> updateRemoteScriptControls());
        saveBtn.addActionListener(e -> saveSettings(true));
        applyBtn.addActionListener(e -> saveSettings(false));
        cancelBtn.addActionListener(e -> {
            if (confirmDiscardChanges()) {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window instanceof JDialog dialog) {
                    dialog.dispose();
                }
            }
        });
        registerSaveShortcut(() -> saveSettings(false));
    }

    private void saveSettings(boolean closeAfterSave) {
        if (!validateAllFields()) {
            NotificationCenter.showError(
                    I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_ERROR_MESSAGE));
            return;
        }
        if (!hasAnyRequestEditorTabSelected()) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_REQUEST_EDITOR_TABS_ERROR));
            return;
        }

        try {
            int maxBodySizeKB = Integer.parseInt(maxBodySizeField.getText().trim());
            SettingManager.setMaxBodySize(maxBodySizeKB * 1024);
            SettingManager.setRequestTimeout(Integer.parseInt(requestTimeoutField.getText().trim()));

            int maxDownloadSizeMB = Integer.parseInt(maxDownloadSizeField.getText().trim());
            SettingManager.setMaxDownloadSize(maxDownloadSizeMB * 1024 * 1024);

            SettingManager.setFollowRedirects(followRedirectsCheckBox.isSelected());
            SettingManager.setRequestSslVerificationDisabled(sslVerificationDisabledCheckBox.isSelected());
            SettingManager.setDefaultProtocol((String) defaultProtocolComboBox.getSelectedItem());
            SettingManager.setHiddenRequestEditorTabs(getHiddenRequestEditorTabs());
            refreshOpenRequestEditorTabsVisibility();
            SettingManager.setRemoteJsRequireEnabled(remoteScriptRequireEnabledCheckBox.isSelected());
            SettingManager.setInsecureRemoteJsRequireEnabled(remoteScriptRequireAllowHttpCheckBox.isSelected());
            SettingManager.setRemoteJsRequireAllowedHosts(remoteScriptAllowedHostsField.getText());
            SettingManager.setRemoteJsRequireConnectTimeoutMs(Integer.parseInt(remoteScriptConnectTimeoutField.getText().trim()));
            SettingManager.setRemoteJsRequireReadTimeoutMs(Integer.parseInt(remoteScriptReadTimeoutField.getText().trim()));
            SettingManager.setRemoteJsRequireMaxBytes(Integer.parseInt(remoteScriptMaxSizeField.getText().trim()) * BYTES_PER_KB);

            OkHttpClientManager.clearClientCache();

            originalValues.clear();
            trackComponentValue(maxBodySizeField);
            trackComponentValue(requestTimeoutField);
            trackComponentValue(maxDownloadSizeField);
            trackComponentValue(followRedirectsCheckBox);
            trackComponentValue(sslVerificationDisabledCheckBox);
            trackComponentValue(defaultProtocolComboBox);
            trackRequestEditorTabCheckBoxes();
            trackComponentValue(remoteScriptRequireEnabledCheckBox);
            trackComponentValue(remoteScriptRequireAllowHttpCheckBox);
            trackComponentValue(remoteScriptAllowedHostsField);
            trackComponentValue(remoteScriptConnectTimeoutField);
            trackComponentValue(remoteScriptReadTimeoutField);
            trackComponentValue(remoteScriptMaxSizeField);
            setHasUnsavedChanges(false);

            NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.SETTINGS_SAVE_SUCCESS_MESSAGE));

            if (closeAfterSave) {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window instanceof JDialog dialog) {
                    dialog.dispose();
                }
            }
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_SAVE_ERROR_MESSAGE) + ": " + ex.getMessage());
        }
    }

    private void updateRemoteScriptControls() {
        boolean remoteEnabled = remoteScriptRequireEnabledCheckBox.isSelected();
        remoteScriptRequireAllowHttpCheckBox.setEnabled(remoteEnabled);
        setRemoteScriptFieldRowsEnabled(remoteEnabled);
    }

    private void setRemoteScriptFieldRowsEnabled(boolean enabled) {
        remoteScriptAllowedHostsRow.setEnabled(enabled);
        remoteScriptConnectTimeoutRow.setEnabled(enabled);
        remoteScriptReadTimeoutRow.setEnabled(enabled);
        remoteScriptMaxSizeRow.setEnabled(enabled);
    }

    private int calculatePageFieldLabelWidth() {
        return calculateFieldLabelWidth(List.of(
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_MAX_BODY_SIZE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_TIMEOUT),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_MAX_DOWNLOAD_SIZE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_DEFAULT_PROTOCOL),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_ALLOWED_HOSTS),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_CONNECT_TIMEOUT),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_READ_TIMEOUT),
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_MAX_SIZE)
        ));
    }

    private boolean isStrictlyPositiveInteger(String value) {
        return isInteger(value) && Integer.parseInt(value) > 0;
    }

    private JCheckBox createRequestEditorTabCheckBox(String label, String tabId) {
        return new JCheckBox(label, SettingManager.isRequestEditorTabVisible(tabId));
    }

    private void addRequestEditorTabRow(JPanel section, JCheckBox checkBox) {
        section.add(createCheckBoxRow(
                checkBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_EDITOR_TABS_TOOLTIP)
        ));
        section.add(createVerticalSpace(FIELD_SPACING));
    }

    private void trackRequestEditorTabCheckBoxes() {
        trackComponentValue(docsTabVisibleCheckBox);
        trackComponentValue(paramsTabVisibleCheckBox);
        trackComponentValue(authTabVisibleCheckBox);
        trackComponentValue(headersTabVisibleCheckBox);
        trackComponentValue(bodyTabVisibleCheckBox);
        trackComponentValue(scriptsTabVisibleCheckBox);
        trackComponentValue(settingsTabVisibleCheckBox);
    }

    private boolean hasAnyRequestEditorTabSelected() {
        return docsTabVisibleCheckBox.isSelected()
                || paramsTabVisibleCheckBox.isSelected()
                || authTabVisibleCheckBox.isSelected()
                || headersTabVisibleCheckBox.isSelected()
                || bodyTabVisibleCheckBox.isSelected()
                || scriptsTabVisibleCheckBox.isSelected()
                || settingsTabVisibleCheckBox.isSelected();
    }

    private List<String> getHiddenRequestEditorTabs() {
        List<String> hiddenTabs = new ArrayList<>();
        addHiddenTabIfUnchecked(hiddenTabs, docsTabVisibleCheckBox, SettingManager.REQUEST_EDITOR_TAB_DOCS);
        addHiddenTabIfUnchecked(hiddenTabs, paramsTabVisibleCheckBox, SettingManager.REQUEST_EDITOR_TAB_PARAMS);
        addHiddenTabIfUnchecked(hiddenTabs, authTabVisibleCheckBox, SettingManager.REQUEST_EDITOR_TAB_AUTH);
        addHiddenTabIfUnchecked(hiddenTabs, headersTabVisibleCheckBox, SettingManager.REQUEST_EDITOR_TAB_HEADERS);
        addHiddenTabIfUnchecked(hiddenTabs, bodyTabVisibleCheckBox, SettingManager.REQUEST_EDITOR_TAB_BODY);
        addHiddenTabIfUnchecked(hiddenTabs, scriptsTabVisibleCheckBox, SettingManager.REQUEST_EDITOR_TAB_SCRIPTS);
        addHiddenTabIfUnchecked(hiddenTabs, settingsTabVisibleCheckBox, SettingManager.REQUEST_EDITOR_TAB_SETTINGS);
        return hiddenTabs;
    }

    private void addHiddenTabIfUnchecked(List<String> hiddenTabs, JCheckBox checkBox, String tabId) {
        if (!checkBox.isSelected()) {
            hiddenTabs.add(tabId);
        }
    }

    private void refreshOpenRequestEditorTabsVisibility() {
        try {
            UiSingletonFactory.getInstance(RequestEditorPanel.class).updateAllRequestEditorTabsVisibility();
        } catch (Exception ex) {
            log.debug("Failed to refresh open request editor tabs visibility", ex);
        }
    }
}
