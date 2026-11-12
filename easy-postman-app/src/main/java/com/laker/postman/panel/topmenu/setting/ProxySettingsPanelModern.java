package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.common.component.EasyPasswordField;
import com.laker.postman.common.component.setting.SettingsFieldRow;
import com.laker.postman.common.component.setting.SettingsHintLabel;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.http.runtime.config.HttpRuntimeSettings;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.config.ProxyOverrideRuntimeSettings;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.http.runtime.okhttp.ProxyConnectionChecker;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.net.URI;

public class ProxySettingsPanelModern extends ModernSettingsPanel {
    private static final int FIELD_SPACING = 8;
    private static final int SECTION_SPACING = 12;
    private static final int SUBSECTION_DESCRIPTION_WIDTH = 660;
    private static final String DEFAULT_PROXY_PREVIEW_TARGET = "example.com";
    private static final int PREVIEW_REFRESH_DELAY_MS = 300;
    private JCheckBox proxyEnabledCheckBox;
    private JComboBox<String> proxyModeComboBox;
    private JComboBox<String> proxyTypeComboBox;
    private JTextField proxyHostField;
    private JTextField proxyPortField;
    private JCheckBox proxyAuthCheckBox;
    private JTextField proxyUsernameField;
    private EasyPasswordField proxyPasswordField;
    private SettingsFieldRow proxyModeRow;
    private SettingsFieldRow proxyTypeRow;
    private SettingsFieldRow proxyHostRow;
    private SettingsFieldRow proxyPortRow;
    private SettingsFieldRow proxyUsernameRow;
    private SettingsFieldRow proxyPasswordRow;
    private SettingsFieldRow proxyPreviewTargetRow;
    private JCheckBox sslVerificationDisabledCheckBox;
    private JTextArea proxyStatusArea;
    private JTextField proxyPreviewTargetField;
    private Timer previewRefreshTimer;
    private JButton checkConnectionButton;
    private JTextArea checkResultArea;
    private boolean connectionCheckRunning;

    private record ProxyViewState(
            boolean proxyEnabled,
            boolean manualMode,
            boolean manualFieldsEnabled,
            boolean authFieldsEnabled
    ) {
    }

    @Override
    protected void buildContent(JPanel contentPanel) {
        JPanel proxySection = createModernSection(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_TITLE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_DESCRIPTION)
        );
        proxyEnabledCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_ENABLED_CHECKBOX),
                SettingManager.isProxyEnabled()
        );
        JPanel proxyEnabledRow = createCheckBoxRow(
                proxyEnabledCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_ENABLED_TOOLTIP)
        );
        proxySection.add(proxyEnabledRow);
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxyModeComboBox = new JComboBox<>(new String[]{
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE_MANUAL),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE_SYSTEM)
        });
        proxyModeComboBox.setSelectedIndex(SettingManager.isSystemProxyMode() ? 1 : 0);
        proxyModeRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE_TOOLTIP),
                proxyModeComboBox
        );
        proxySection.add(proxyModeRow);
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxySection.add(createSubsectionHeader(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MANUAL_SECTION_TITLE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MANUAL_SECTION_DESCRIPTION)
        ));
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxyTypeComboBox = new JComboBox<>(new String[]{
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_TYPE_HTTP),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_TYPE_SOCKS)
        });
        proxyTypeComboBox.setSelectedItem(SettingManager.getProxyType());
        proxyTypeRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_TYPE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_TYPE_TOOLTIP),
                proxyTypeComboBox
        );
        proxySection.add(proxyTypeRow);
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxyHostField = new JTextField(10);
        proxyHostField.setText(SettingManager.getProxyHost());
        proxyHostRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_HOST),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_HOST_TOOLTIP),
                proxyHostField
        );
        proxySection.add(proxyHostRow);
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxyPortField = new JTextField(10);
        proxyPortField.setText(SettingManager.getProxyPortText());
        proxyPortRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PORT),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PORT_TOOLTIP),
                proxyPortField
        );
        proxySection.add(proxyPortRow);
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxySection.add(createSubsectionHeader(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_AUTH_SECTION_TITLE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_AUTH_SECTION_DESCRIPTION)
        ));
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        String savedProxyUsername = SettingManager.getProxyUsername();
        proxyAuthCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_AUTH_CHECKBOX),
                savedProxyUsername != null && !savedProxyUsername.isBlank()
        );
        proxySection.add(createCheckBoxRow(
                proxyAuthCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_AUTH_TOOLTIP)
        ));
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxyUsernameField = new JTextField(10);
        proxyUsernameField.setText(SettingManager.getProxyUsername());
        proxyUsernameRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_USERNAME),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_USERNAME_TOOLTIP),
                proxyUsernameField
        );
        proxySection.add(proxyUsernameRow);
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxyPasswordField = new EasyPasswordField(10);
        proxyPasswordField.setText(SettingManager.getProxyPassword());
        proxyPasswordRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PASSWORD),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PASSWORD_TOOLTIP),
                proxyPasswordField
        );
        proxySection.add(proxyPasswordRow);
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxySection.add(createSubsectionHeader(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_ADVANCED_SECTION_TITLE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_ADVANCED_SECTION_DESCRIPTION)
        ));
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        sslVerificationDisabledCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_SSL_VERIFICATION_CHECKBOX),
                SettingManager.isProxySslVerificationDisabled()
        );
        JPanel sslVerificationRow = createCheckBoxRow(
                sslVerificationDisabledCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_SSL_VERIFICATION_TOOLTIP)
        );
        proxySection.add(sslVerificationRow);
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxySection.add(createSubsectionHeader(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PREVIEW_SECTION_TITLE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PREVIEW_SECTION_DESCRIPTION)
        ));
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxyPreviewTargetField = new JTextField(10);
        proxyPreviewTargetField.setText(DEFAULT_PROXY_PREVIEW_TARGET);
        proxyPreviewTargetRow = createFieldRow(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PREVIEW_TARGET),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PREVIEW_TARGET_TOOLTIP),
                proxyPreviewTargetField
        );
        proxySection.add(proxyPreviewTargetRow);
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        proxySection.add(createCheckConnectionRow());
        proxySection.add(createVerticalSpace(FIELD_SPACING));
        checkResultArea = createInfoTextArea();
        checkResultArea.setVisible(false);
        proxySection.add(checkResultArea);
        proxyStatusArea = createInfoTextArea();
        proxySection.add(proxyStatusArea);
        contentPanel.add(proxySection);
        contentPanel.add(createVerticalSpace(SECTION_SPACING));
        setupValidators();
        updateProxyInputState();
        trackProxyFormState();
    }

    private void setupValidators() {
        setupValidator(
                proxyPortField,
                s -> isInteger(s) && Integer.parseInt(s) > 0 && Integer.parseInt(s) <= 65535,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_PORT_ERROR)
        );
    }

    @Override
    protected void registerListeners() {
        proxyEnabledCheckBox.addItemListener(e -> updateProxyInputState());
        proxyModeComboBox.addItemListener(e -> updateProxyInputState());
        proxyTypeComboBox.addItemListener(e -> updateProxyInputState());
        proxyAuthCheckBox.addItemListener(e -> updateProxyInputState());
        addRefreshListeners(this::updateProxyInputState, proxyHostField, proxyPortField);
        previewRefreshTimer = new Timer(PREVIEW_REFRESH_DELAY_MS, e -> updateProxyInputState());
        previewRefreshTimer.setRepeats(false);
        addRefreshListeners(previewRefreshTimer::restart, proxyPreviewTargetField);
        checkConnectionButton.addActionListener(e -> runConnectionCheck());
        saveBtn.addActionListener(e -> saveSettings(true));
        applyBtn.addActionListener(e -> saveSettings(false));
        cancelBtn.addActionListener(e -> {
            if (confirmDiscardChanges()) {
                closeDialog();
            }
        });
    }

    private void saveSettings(boolean closeAfterSave) {
        if (!validateProxyForm()) {
            return;
        }
        boolean proxyEnabled = isProxyEnabledSelected();
        boolean manualMode = SettingManager.isManualProxyModeValue(getSelectedProxyMode());
        boolean authEnabled = proxyAuthCheckBox.isSelected();
        try {
            SettingManager.setProxyEnabled(proxyEnabled);
            SettingManager.setProxyMode(getSelectedProxyMode());
            if (proxyEnabled && manualMode) {
                SettingManager.setProxyType((String) proxyTypeComboBox.getSelectedItem());
                SettingManager.setProxyHost(getTrimmedText(proxyHostField));
                SettingManager.setProxyPort(Integer.parseInt(getTrimmedText(proxyPortField)));
            }
            SettingManager.setProxyUsername(authEnabled ? getTrimmedText(proxyUsernameField) : "");
            SettingManager.setProxyPassword(authEnabled ? new String(proxyPasswordField.getPassword()) : "");
            SettingManager.setProxySslVerificationDisabled(sslVerificationDisabledCheckBox.isSelected());
            OkHttpClientManager.clearClientCache();
            trackProxyFormState();
            setHasUnsavedChanges(false);

            NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.SETTINGS_SAVE_SUCCESS_MESSAGE));

            if (closeAfterSave) {
                closeDialog();
            }
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_SAVE_ERROR_MESSAGE) + ": " + ex.getMessage());
        }
    }

    private boolean validateProxyForm() {
        boolean proxyEnabled = isProxyEnabledSelected();
        boolean manualMode = SettingManager.isManualProxyModeValue(getSelectedProxyMode());
        if (proxyEnabled && manualMode && (!hasRequiredManualProxyFields() || !validateAllFields())) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_ERROR_MESSAGE));
            return false;
        }
        if (proxyEnabled && proxyAuthCheckBox.isSelected() && !hasRequiredAuthFields()) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_AUTH_REQUIRED));
            return false;
        }
        return true;
    }

    private JPanel createCheckConnectionRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkConnectionButton = createActionButton(
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_CHECK_BUTTON),
                I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_CHECK_TOOLTIP),
                false
        );
        row.add(Box.createHorizontalStrut(SettingsFieldRow.DEFAULT_LABEL_WIDTH + SettingsFieldRow.DEFAULT_GAP));
        row.add(checkConnectionButton);
        row.add(Box.createHorizontalGlue());
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ACTION_BUTTON_HEIGHT));
        return row;
    }

    private void runConnectionCheck() {
        if (connectionCheckRunning || !validateProxyForm()) {
            return;
        }
        HttpRuntimeSettings formSettings = buildFormRuntimeSettings();
        String target = getPreviewTargetInput();
        connectionCheckRunning = true;
        checkConnectionButton.setEnabled(false);
        showCheckResult(I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_CHECK_RUNNING, target), getTextSecondaryColor());
        new SwingWorker<ProxyConnectionChecker.Result, Void>() {
            @Override
            protected ProxyConnectionChecker.Result doInBackground() {
                return ProxyConnectionChecker.check(formSettings, target, ProxyConnectionChecker.DEFAULT_TIMEOUT_MS);
            }

            @Override
            protected void done() {
                connectionCheckRunning = false;
                checkConnectionButton.setEnabled(true);
                try {
                    renderCheckResult(get());
                } catch (Exception ex) {
                    showCheckResult(I18nUtil.getMessage(
                            MessageKeys.SETTINGS_PROXY_CHECK_FAILED,
                            I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_CHECK_ROUTE_DIRECT),
                            ex.getMessage()
                    ), ModernColors.getError());
                }
            }
        }.execute();
    }

    private HttpRuntimeSettings buildFormRuntimeSettings() {
        boolean authEnabled = proxyAuthCheckBox.isSelected();
        String port = getTrimmedText(proxyPortField);
        return ProxyOverrideRuntimeSettings.builder()
                .base(HttpRuntimeSettingsProvider.get())
                .proxyEnabled(isProxyEnabledSelected())
                .proxyMode(getSelectedProxyMode())
                .proxyType(proxyTypeComboBox.getSelectedIndex() == 1
                        ? HttpRuntimeSettings.PROXY_TYPE_SOCKS
                        : HttpRuntimeSettings.PROXY_TYPE_HTTP)
                .proxyHost(getTrimmedText(proxyHostField))
                .proxyPort(isInteger(port) ? Integer.parseInt(port) : 0)
                .proxyUsername(authEnabled ? getTrimmedText(proxyUsernameField) : "")
                .proxyPassword(authEnabled ? new String(proxyPasswordField.getPassword()) : "")
                .proxySslVerificationDisabled(sslVerificationDisabledCheckBox.isSelected())
                .build();
    }

    private void renderCheckResult(ProxyConnectionChecker.Result result) {
        String route = result.route().active()
                ? I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_CHECK_ROUTE_PROXY, result.route().description())
                : I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_CHECK_ROUTE_DIRECT);
        switch (result.status()) {
            case SUCCESS -> showCheckResult(I18nUtil.getMessage(
                    MessageKeys.SETTINGS_PROXY_CHECK_SUCCESS,
                    String.valueOf(result.httpCode()),
                    route,
                    String.valueOf(result.elapsedMs())
            ), ModernColors.getSuccess());
            case PROXY_AUTH_FAILED -> showCheckResult(I18nUtil.getMessage(
                    MessageKeys.SETTINGS_PROXY_CHECK_AUTH_FAILED,
                    route
            ), ModernColors.getError());
            case FAILED -> showCheckResult(I18nUtil.getMessage(
                    MessageKeys.SETTINGS_PROXY_CHECK_FAILED,
                    route,
                    result.message()
            ), ModernColors.getError());
        }
    }

    private void showCheckResult(String text, Color color) {
        checkResultArea.setText(text);
        checkResultArea.setForeground(color);
        if (!checkResultArea.isVisible()) {
            checkResultArea.setVisible(true);
            revalidate();
        }
        repaint();
    }

    private String getSelectedProxyMode() {
        return proxyModeComboBox.getSelectedIndex() == 1
                ? SettingManager.PROXY_MODE_SYSTEM
                : SettingManager.PROXY_MODE_MANUAL;
    }

    private void updateProxyInputState() {
        ProxyViewState state = buildViewState();
        updateProxyControls(state);
        updateInformationalAreas(state);
    }

    private JTextArea createInfoTextArea() {
        JTextArea area = new ShrinkableInfoTextArea();
        area.setOpaque(false);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        area.setForeground(getTextSecondaryColor());
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return area;
    }

    private JComponent createSubsectionHeader(String title, String description) {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(new EmptyBorder(8, 0, 0, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        titleLabel.setForeground(getTextPrimaryColor());
        header.add(titleLabel);

        if (description != null && !description.isBlank()) {
            SettingsHintLabel descriptionLabel = new SettingsHintLabel(description, SUBSECTION_DESCRIPTION_WIDTH);
            descriptionLabel.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
            header.add(descriptionLabel);
        }

        return header;
    }

    private void addRefreshListeners(Runnable refresh, JTextField... fields) {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh.run();
            }
        };
        for (JTextField field : fields) {
            field.getDocument().addDocumentListener(listener);
        }
    }

    private ProxyViewState buildViewState() {
        boolean proxyEnabled = isProxyEnabledSelected();
        boolean manualMode = SettingManager.isManualProxyModeValue(getSelectedProxyMode());
        return new ProxyViewState(
                proxyEnabled,
                manualMode,
                proxyEnabled && manualMode,
                proxyEnabled && proxyAuthCheckBox.isSelected()
        );
    }

    private void updateProxyControls(ProxyViewState state) {
        proxyModeRow.setEnabled(state.proxyEnabled());
        proxyTypeRow.setEnabled(state.manualFieldsEnabled());
        proxyHostRow.setEnabled(state.manualFieldsEnabled());
        proxyPortRow.setEnabled(state.manualFieldsEnabled());
        proxyAuthCheckBox.setEnabled(state.proxyEnabled());
        proxyUsernameRow.setEnabled(state.authFieldsEnabled());
        proxyPasswordRow.setEnabled(state.authFieldsEnabled());
        sslVerificationDisabledCheckBox.setEnabled(state.proxyEnabled());
    }

    private void updateInformationalAreas(ProxyViewState state) {
        proxyStatusArea.setText(buildProxyStatusText(state));
    }

    private String buildProxyStatusText(ProxyViewState state) {
        if (!state.proxyEnabled()) {
            return I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_STATUS_DISABLED);
        }
        if (state.manualMode()) {
            String host = getTrimmedText(proxyHostField);
            String port = getTrimmedText(proxyPortField);
            if (host.isEmpty() || port.isEmpty()) {
                return I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_STATUS_MANUAL_INCOMPLETE);
            }
            return I18nUtil.getMessage(
                    MessageKeys.SETTINGS_PROXY_STATUS_MANUAL_ACTIVE,
                    proxyTypeComboBox.getSelectedItem(),
                    host,
                    port
            );
        }

        return I18nUtil.getMessage(
                MessageKeys.SETTINGS_PROXY_STATUS_SYSTEM_ACTIVE,
                getPreviewTargetDisplay(),
                describeSystemPreview("http"),
                describeSystemPreview("https")
        );
    }

    private String describeSystemPreview(String scheme) {
        OkHttpClientManager.ProxyInspection inspection = OkHttpClientManager.inspectSystemProxyForUrl(
                buildPreviewUrl(scheme)
        );
        if (!inspection.resolved()) {
            return I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_STATUS_UNAVAILABLE);
        }
        if (!inspection.active()) {
            return I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_STATUS_DIRECT);
        }
        return inspection.description();
    }

    private String getPreviewTargetDisplay() {
        String target = getPreviewTargetInput();
        if (!target.contains("://")) {
            return target;
        }
        try {
            URI uri = URI.create(target);
            String authority = uri.getRawAuthority();
            if (authority == null || authority.isBlank()) {
                return target;
            }
            StringBuilder display = new StringBuilder(authority);
            String path = uri.getRawPath();
            if (path != null && !path.isBlank()) {
                display.append(path);
            }
            String query = uri.getRawQuery();
            if (query != null && !query.isBlank()) {
                display.append('?').append(query);
            }
            return display.toString();
        } catch (Exception e) {
            return target;
        }
    }

    private String getPreviewTargetInput() {
        String value = getTrimmedText(proxyPreviewTargetField);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_PROXY_PREVIEW_TARGET;
        }
        return value;
    }

    private String buildPreviewUrl(String scheme) {
        String target = getPreviewTargetInput();
        if (!target.contains("://")) {
            return scheme + "://" + target;
        }
        try {
            URI uri = URI.create(target);
            String authority = uri.getRawAuthority();
            if (authority == null || authority.isBlank()) {
                return scheme + "://" + target;
            }
            return new URI(
                    scheme,
                    authority,
                    uri.getRawPath(),
                    uri.getRawQuery(),
                    uri.getRawFragment()
            ).toString();
        } catch (Exception e) {
            return scheme + "://" + target.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        }
    }

    private static final class ShrinkableInfoTextArea extends JTextArea {
        @Override
        public Dimension getMinimumSize() {
            Dimension minimumSize = super.getMinimumSize();
            return new Dimension(0, minimumSize.height);
        }
    }

    private boolean isProxyEnabledSelected() {
        return proxyEnabledCheckBox.isSelected();
    }

    private boolean hasRequiredManualProxyFields() {
        if (getTrimmedText(proxyHostField).isEmpty()) {
            proxyHostField.requestFocus();
            return false;
        }
        if (getTrimmedText(proxyPortField).isEmpty()) {
            proxyPortField.requestFocus();
            return false;
        }
        return true;
    }

    private boolean hasRequiredAuthFields() {
        if (getTrimmedText(proxyUsernameField).isEmpty()) {
            proxyUsernameField.requestFocus();
            return false;
        }
        if (new String(proxyPasswordField.getPassword()).trim().isEmpty()) {
            proxyPasswordField.requestFocus();
            return false;
        }
        return true;
    }

    private String getTrimmedText(JTextField field) {
        String value = field.getText();
        return value == null ? "" : value.trim();
    }

    private void closeDialog() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JDialog dialog) {
            dialog.dispose();
        }
    }

    private void trackProxyFormState() {
        originalValues.clear();
        trackComponentValue(proxyEnabledCheckBox);
        trackComponentValue(proxyModeComboBox);
        trackComponentValue(proxyTypeComboBox);
        trackComponentValue(proxyHostField);
        trackComponentValue(proxyPortField);
        trackComponentValue(proxyAuthCheckBox);
        trackComponentValue(proxyUsernameField);
        trackComponentValue(proxyPasswordField);
        trackComponentValue(sslVerificationDisabledCheckBox);
    }
}
