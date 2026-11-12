package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.component.EasyComboBox;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.SwitchButton;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.request.edit.HttpRequestSettingsDraft;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.HttpRequestProxyPolicy;
import com.laker.postman.request.model.HttpRequestVersions;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 请求级 Settings 面板，参考 Postman 的列表式布局。
 */
public class RequestSettingsPanel extends JScrollPane {
    private static final int CONTROL_WIDTH = 176;
    private static final int CONTROL_MIN_WIDTH = 120;
    private int controlSlotWidth = CONTROL_MIN_WIDTH;
    private static final int ROW_COLUMN_GAP = 16;
    private static final int SCROLL_UNIT_INCREMENT = 24;
    private static final int CUSTOM_WEBSOCKET_PING_INTERVAL_OPTION = -1;
    private static final int MIN_CUSTOM_WEBSOCKET_PING_INTERVAL_MS = 5_000;

    private final boolean webSocketSettingsVisible;
    private final EasyComboBox<BooleanSettingOption> followRedirectsComboBox;
    private final SwitchButton useCookieJarSwitch;
    private final EasyComboBox<ProxyPolicyOption> proxyPolicyComboBox;
    private final EasyComboBox<HttpVersionOption> httpVersionComboBox;
    private final EasyComboBox<IntegerSettingOption> webSocketPingIntervalComboBox;
    private final JTextField webSocketPingIntervalCustomField;
    private final JLabel webSocketPingIntervalUnitLabel;
    private JPanel webSocketPingIntervalControlPanel;
    private JPanel webSocketPingIntervalCustomPanel;
    private final JTextField requestTimeoutField;
    private final JLabel requestTimeoutHintLabel;
    private boolean editable = true;

    public RequestSettingsPanel() {
        this(RequestItemProtocolEnum.HTTP);
    }

    public RequestSettingsPanel(RequestItemProtocolEnum protocol) {
        webSocketSettingsVisible = protocol != null && protocol.isWebSocketProtocol();
        ToolWindowSurfaceStyle.applyScrollPaneCard(this);
        setBorder(BorderFactory.createEmptyBorder());
        setViewportBorder(BorderFactory.createEmptyBorder());
        setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        JPanel content = new JPanel(new MigLayout("insets 0, fillx, novisualpadding, gap 0", "[grow,fill]", ""));
        ToolWindowSurfaceStyle.applyCard(content);
        content.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        JPanel viewportContent = new ViewportWidthPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(viewportContent);
        viewportContent.add(content, BorderLayout.NORTH);
        setViewportView(viewportContent);

        followRedirectsComboBox = createSettingComboBox(createBooleanSettingOptions());
        useCookieJarSwitch = new SwitchButton();
        proxyPolicyComboBox = createSettingComboBox(createProxyPolicyOptions());
        httpVersionComboBox = createSettingComboBox(createHttpVersionOptions());
        webSocketPingIntervalComboBox = createSettingComboBox(createWebSocketPingIntervalOptions());
        webSocketPingIntervalCustomField = new JTextField();
        webSocketPingIntervalUnitLabel = new JLabel(I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_UNIT));
        requestTimeoutField = new JTextField();
        requestTimeoutHintLabel = createHintLabel();

        ((AbstractDocument) requestTimeoutField.getDocument()).setDocumentFilter(new DigitsOnlyDocumentFilter());
        ((AbstractDocument) webSocketPingIntervalCustomField.getDocument()).setDocumentFilter(new DigitsOnlyDocumentFilter());
        webSocketPingIntervalCustomField.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        webSocketPingIntervalCustomField.setColumns(8);
        webSocketPingIntervalUnitLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        webSocketPingIntervalUnitLabel.setForeground(ModernColors.getTextSecondary());
        requestTimeoutField.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        requestTimeoutField.setColumns(10);
        String webSocketPingHint = webSocketPingIntervalHint();
        webSocketPingIntervalComboBox.setToolTipText(webSocketPingHint);
        webSocketPingIntervalCustomField.setToolTipText(webSocketPingHint);
        webSocketPingIntervalComboBox.addActionListener(e -> updateWebSocketPingCustomFieldState());
        controlSlotWidth = resolveControlSlotWidth();

        content.add(createSelectRow(
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_FOLLOW_REDIRECTS_LABEL),
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_FOLLOW_REDIRECTS_DESC),
                followRedirectsComboBox
        ), "growx, wrap");
        content.add(createSwitchRow(
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_USE_COOKIE_JAR_LABEL),
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_USE_COOKIE_JAR_DESC),
                useCookieJarSwitch
        ), "growx, wrap");
        content.add(createSelectRow(
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_LABEL),
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_DESC),
                proxyPolicyComboBox
        ), "growx, wrap");
        content.add(createSelectRow(
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_HTTP_VERSION_LABEL),
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_HTTP_VERSION_DESC),
                httpVersionComboBox
        ), "growx, wrap");
        if (webSocketSettingsVisible) {
            content.add(createWebSocketPingIntervalRow(), "growx, wrap");
        }
        content.add(createTimeoutRow(), "growx, wrap");
        populate(null);
    }

    public void populate(HttpRequestSettingsDraft settings) {
        updateRequestTimeoutHint();
        followRedirectsComboBox.setSelectedItem(findBooleanOption(
                followRedirectsComboBox,
                settings != null ? settings.getFollowRedirects() : null
        ));
        Boolean cookieJarEnabled = settings != null ? settings.getCookieJarEnabled() : null;
        useCookieJarSwitch.setSelected(cookieJarEnabled == null || cookieJarEnabled);
        proxyPolicyComboBox.setSelectedItem(findProxyPolicyOption(settings != null ? settings.getProxyPolicy() : null));
        httpVersionComboBox.setSelectedItem(findHttpVersionOption(settings != null ? settings.getHttpVersion() : null));
        selectWebSocketPingInterval(settings != null ? settings.getWebSocketPingIntervalMs() : null);
        Integer requestTimeout = settings != null ? settings.getRequestTimeoutMs() : null;
        requestTimeoutField.setText(requestTimeout != null ? String.valueOf(requestTimeout) : "");
    }

    public HttpRequestSettingsDraft collectSettings() {
        return HttpRequestSettingsDraft.builder()
                .followRedirects(getSelectedBooleanValue(followRedirectsComboBox))
                .cookieJarEnabled(getStoredCookieJarValue())
                .proxyPolicy(getSelectedProxyPolicy())
                .httpVersion(getStoredHttpVersionValue())
                .requestTimeoutMs(getStoredRequestTimeoutValue())
                .webSocketPingIntervalMs(getStoredWebSocketPingIntervalValue())
                .build();
    }

    public void rebaseline() {
        updateRequestTimeoutHint();
    }

    public String validateSettings() {
        String timeoutValidation = validateRequestTimeout(requestTimeoutField.getText());
        if (timeoutValidation != null) {
            return timeoutValidation;
        }
        return validateWebSocketPingInterval();
    }

    public void setEditable(boolean editable) {
        followRedirectsComboBox.setEnabled(editable);
        useCookieJarSwitch.setEnabled(editable);
        proxyPolicyComboBox.setEnabled(editable);
        httpVersionComboBox.setEnabled(editable);
        webSocketPingIntervalComboBox.setEnabled(editable && webSocketSettingsVisible);
        this.editable = editable;
        updateWebSocketPingCustomFieldState();
        requestTimeoutField.setEditable(editable);
        requestTimeoutField.setEnabled(editable);
    }

    public void addDirtyListener(Runnable listener) {
        if (listener == null) {
            return;
        }
        followRedirectsComboBox.addActionListener(e -> listener.run());
        useCookieJarSwitch.addActionListener(e -> listener.run());
        proxyPolicyComboBox.addActionListener(e -> listener.run());
        httpVersionComboBox.addActionListener(e -> listener.run());
        webSocketPingIntervalComboBox.addActionListener(e -> {
            listener.run();
        });
        webSocketPingIntervalCustomField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                listener.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                listener.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                listener.run();
            }
        });
        requestTimeoutField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                listener.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                listener.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                listener.run();
            }
        });
    }

    @Override
    public void doLayout() {
        super.doLayout();
        syncViewportViewWidth();
    }

    private void syncViewportViewWidth() {
        JViewport viewport = getViewport();
        Component view = viewport.getView();
        if (view == null) {
            return;
        }
        Dimension extentSize = viewport.getExtentSize();
        if (extentSize.width <= 0) {
            return;
        }
        Dimension preferredSize = view.getPreferredSize();
        Dimension viewSize = viewport.getViewSize();
        int targetHeight = Math.max(preferredSize.height, extentSize.height);
        if (viewSize.width != extentSize.width || viewSize.height != targetHeight) {
            viewport.setViewSize(new Dimension(extentSize.width, targetHeight));
        }
    }

    /**
     * All rows share one control slot sized to the widest option, so controls line up without
     * stretching short values such as "Auto" into an oversized box.
     */
    private int resolveControlSlotWidth() {
        int width = CONTROL_MIN_WIDTH;
        for (JComponent comboBox : List.of(followRedirectsComboBox, proxyPolicyComboBox, httpVersionComboBox,
                webSocketPingIntervalComboBox)) {
            width = Math.max(width, comboBox.getPreferredSize().width);
        }
        return Math.min(width, CONTROL_WIDTH);
    }

    private <T> EasyComboBox<T> createSettingComboBox(T[] options) {
        EasyComboBox<T> comboBox = new EasyComboBox<>(options, EasyComboBox.WidthMode.FIXED_MAX);
        comboBox.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        return comboBox;
    }

    private JPanel createSwitchRow(String title, String description, SwitchButton switchButton) {
        JPanel panel = new JPanel(new MigLayout("insets 0, fillx, novisualpadding, gap 0", "[grow,right]", "[]"));
        panel.setOpaque(false);
        panel.add(switchButton);
        return createSettingRow(title, description, panel);
    }

    private JPanel createSelectRow(String title, String description, JComponent component) {
        return createSettingRow(title, description, wrapControl(component));
    }

    private JPanel createTimeoutRow() {
        JPanel row = createSettingRow(
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_TIMEOUT_LABEL),
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_TIMEOUT_DESC),
                wrapControl(requestTimeoutField)
        );
        // The hint is usually wider than the control slot, so it spans the row instead of widening the slot.
        requestTimeoutHintLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(requestTimeoutHintLabel, "newline 2, span 2, growx, wmin 0");
        return row;
    }

    private JPanel createWebSocketPingIntervalRow() {
        webSocketPingIntervalControlPanel = createWebSocketPingIntervalControlPanel();
        updateWebSocketPingCustomFieldState();
        return createSettingRow(
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_LABEL),
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_DESC),
                webSocketPingIntervalControlPanel
        );
    }

    private JPanel createSettingRow(String title, String description, JComponent rightComponent) {
        JPanel row = new JPanel(new MigLayout(
                "insets 8 0 8 0, fillx, novisualpadding",
                "[grow,fill]" + ROW_COLUMN_GAP
                        + "[" + CONTROL_MIN_WIDTH + ":" + controlSlotWidth + ":" + controlSlotWidth + ",fill]",
                "[]"
        ));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder());

        JPanel textPanel = new JPanel(new MigLayout("insets 0, fillx, novisualpadding, gap 0", "[grow,fill]", "[]1[]"));
        textPanel.setOpaque(false);
        textPanel.setMinimumSize(new Dimension(0, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        titleLabel.setForeground(ModernColors.getTextPrimary());

        JTextArea descriptionLabel = createDescriptionText(description);

        textPanel.add(titleLabel, "growx, wrap");
        textPanel.add(descriptionLabel, "growx");

        row.add(textPanel, "growx, pushx, wmin 0, aligny center");
        row.add(rightComponent, "growx, aligny center");
        return row;
    }

    private JPanel wrapControl(JComponent component) {
        JPanel panel = new JPanel(new MigLayout("insets 0, fillx, novisualpadding, gap 0", "[grow,fill]", "[]"));
        panel.setOpaque(false);
        panel.add(
                component,
                "growx, pushx, wmin 0, wmax " + controlSlotWidth + ", alignx right"
        );
        return panel;
    }

    private JPanel createWebSocketPingIntervalControlPanel() {
        JPanel panel = new JPanel(new MigLayout(
                "insets 0, fillx, novisualpadding, hidemode 3, gap 0",
                "[grow,fill]",
                "[]2[]"
        ));
        panel.setOpaque(false);

        webSocketPingIntervalCustomPanel = new JPanel(new MigLayout(
                "insets 0, fillx, novisualpadding, hidemode 3, gap 6 0",
                "[grow,fill][pref]",
                "[]"
        ));
        webSocketPingIntervalCustomPanel.setOpaque(false);
        webSocketPingIntervalCustomPanel.add(webSocketPingIntervalCustomField, "growx, wmin 0");
        webSocketPingIntervalCustomPanel.add(webSocketPingIntervalUnitLabel);

        panel.add(
                webSocketPingIntervalComboBox,
                "growx, pushx, wmin 0, wmax " + controlSlotWidth + ", wrap"
        );
        panel.add(webSocketPingIntervalCustomPanel, "growx");
        return panel;
    }

    private JLabel createHintLabel() {
        JLabel label = new JLabel();
        label.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        label.setForeground(ModernColors.getTextSecondary());
        return label;
    }

    private JTextArea createDescriptionText(String description) {
        JTextArea area = new ShrinkableWrapTextArea(description);
        area.setOpaque(false);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(null);
        area.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        area.setForeground(ModernColors.getTextSecondary());
        return area;
    }

    private void updateRequestTimeoutHint() {
        requestTimeoutHintLabel.setText(I18nUtil.getMessage(
                MessageKeys.REQUEST_SETTINGS_TIMEOUT_HINT,
                SettingManager.getRequestTimeout()
        ));
    }

    private String webSocketPingIntervalHint() {
        return I18nUtil.getMessage(
                MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_HINT,
                HttpRequestItem.DEFAULT_WEBSOCKET_PING_INTERVAL_MS,
                MIN_CUSTOM_WEBSOCKET_PING_INTERVAL_MS
        );
    }

    private Boolean getStoredCookieJarValue() {
        return useCookieJarSwitch.isSelected() ? Boolean.TRUE : Boolean.FALSE;
    }

    private String getStoredHttpVersionValue() {
        return getSelectedHttpVersion();
    }

    private Integer getStoredRequestTimeoutValue() {
        return parseRequestTimeoutOrNull();
    }

    private Integer getStoredWebSocketPingIntervalValue() {
        if (!webSocketSettingsVisible) {
            return null;
        }
        IntegerSettingOption option = (IntegerSettingOption) webSocketPingIntervalComboBox.getSelectedItem();
        if (option == null) {
            return null;
        }
        if (Integer.valueOf(CUSTOM_WEBSOCKET_PING_INTERVAL_OPTION).equals(option.value)) {
            return parseIntegerOrNull(webSocketPingIntervalCustomField.getText());
        }
        return option.value;
    }

    private BooleanSettingOption[] createBooleanSettingOptions() {
        return new BooleanSettingOption[]{
                new BooleanSettingOption(
                        null,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_BOOLEAN_DEFAULT)
                ),
                new BooleanSettingOption(
                        Boolean.TRUE,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_BOOLEAN_ENABLED)
                ),
                new BooleanSettingOption(
                        Boolean.FALSE,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_BOOLEAN_DISABLED)
                )
        };
    }

    private HttpVersionOption[] createHttpVersionOptions() {
        List<HttpVersionOption> options = new ArrayList<>();
        options.add(new HttpVersionOption(
                HttpRequestVersions.AUTO,
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_HTTP_VERSION_AUTO)
        ));
        options.add(new HttpVersionOption(
                HttpRequestVersions.HTTP_1_1,
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_HTTP_VERSION_HTTP_1_1)
        ));
        options.add(new HttpVersionOption(
                HttpRequestVersions.HTTP_2,
                I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_HTTP_VERSION_HTTP_2)
        ));
        return options.toArray(new HttpVersionOption[0]);
    }

    private IntegerSettingOption[] createWebSocketPingIntervalOptions() {
        return new IntegerSettingOption[]{
                new IntegerSettingOption(
                        null,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_DEFAULT)
                ),
                new IntegerSettingOption(
                        HttpRequestItem.DISABLED_WEBSOCKET_PING_INTERVAL_MS,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_DISABLED)
                ),
                new IntegerSettingOption(
                        15_000,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_15S)
                ),
                new IntegerSettingOption(
                        30_000,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_30S)
                ),
                new IntegerSettingOption(
                        60_000,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_60S)
                ),
                new IntegerSettingOption(
                        CUSTOM_WEBSOCKET_PING_INTERVAL_OPTION,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_CUSTOM)
                )
        };
    }

    private ProxyPolicyOption[] createProxyPolicyOptions() {
        return new ProxyPolicyOption[]{
                new ProxyPolicyOption(
                        HttpRequestProxyPolicy.DEFAULT,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_DEFAULT)
                ),
                new ProxyPolicyOption(
                        HttpRequestProxyPolicy.USE_PROXY,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_USE_PROXY)
                ),
                new ProxyPolicyOption(
                        HttpRequestProxyPolicy.NO_PROXY,
                        I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_PROXY_POLICY_NO_PROXY)
                )
        };
    }

    private BooleanSettingOption findBooleanOption(EasyComboBox<BooleanSettingOption> comboBox, Boolean value) {
        ComboBoxModel<BooleanSettingOption> model = comboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            BooleanSettingOption option = model.getElementAt(i);
            if ((value == null && option.value == null) || (value != null && value.equals(option.value))) {
                return option;
            }
        }
        return model.getElementAt(0);
    }

    private HttpVersionOption findHttpVersionOption(String value) {
        String normalizedValue = (value == null || value.trim().isEmpty())
                ? HttpRequestVersions.AUTO
                : value;
        ComboBoxModel<HttpVersionOption> model = httpVersionComboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            HttpVersionOption option = model.getElementAt(i);
            if (option.value.equals(normalizedValue)) {
                return option;
            }
        }
        return model.getElementAt(0);
    }

    private ProxyPolicyOption findProxyPolicyOption(HttpRequestProxyPolicy value) {
        HttpRequestProxyPolicy normalizedValue = HttpRequestProxyPolicy.normalize(value);
        ComboBoxModel<ProxyPolicyOption> model = proxyPolicyComboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            ProxyPolicyOption option = model.getElementAt(i);
            if (option.value.equals(normalizedValue)) {
                return option;
            }
        }
        return model.getElementAt(0);
    }

    private void selectWebSocketPingInterval(Integer value) {
        Integer optionValue = isKnownWebSocketPingInterval(value)
                ? value
                : Integer.valueOf(CUSTOM_WEBSOCKET_PING_INTERVAL_OPTION);
        webSocketPingIntervalComboBox.setSelectedItem(findIntegerOption(webSocketPingIntervalComboBox, optionValue));
        webSocketPingIntervalCustomField.setText(isKnownWebSocketPingInterval(value) || value == null
                ? ""
                : String.valueOf(value));
        updateWebSocketPingCustomFieldState();
    }

    private IntegerSettingOption findIntegerOption(EasyComboBox<IntegerSettingOption> comboBox, Integer value) {
        ComboBoxModel<IntegerSettingOption> model = comboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            IntegerSettingOption option = model.getElementAt(i);
            if ((value == null && option.value == null) || (value != null && value.equals(option.value))) {
                return option;
            }
        }
        return model.getElementAt(0);
    }

    private boolean isKnownWebSocketPingInterval(Integer value) {
        return value == null
                || Integer.valueOf(0).equals(value)
                || Integer.valueOf(15_000).equals(value)
                || Integer.valueOf(30_000).equals(value)
                || Integer.valueOf(60_000).equals(value);
    }

    private void updateWebSocketPingCustomFieldState() {
        boolean customSelected = webSocketSettingsVisible && isCustomWebSocketPingIntervalSelected();
        if (webSocketPingIntervalCustomPanel != null) {
            webSocketPingIntervalCustomPanel.setVisible(customSelected);
        }
        webSocketPingIntervalCustomField.setVisible(customSelected);
        webSocketPingIntervalUnitLabel.setVisible(customSelected);
        webSocketPingIntervalCustomField.setEditable(editable && customSelected);
        webSocketPingIntervalCustomField.setEnabled(editable && customSelected);
        Container parent = webSocketPingIntervalControlPanel;
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private boolean isCustomWebSocketPingIntervalSelected() {
        IntegerSettingOption option = (IntegerSettingOption) webSocketPingIntervalComboBox.getSelectedItem();
        return option != null && Integer.valueOf(CUSTOM_WEBSOCKET_PING_INTERVAL_OPTION).equals(option.value);
    }

    private Boolean getSelectedBooleanValue(EasyComboBox<BooleanSettingOption> comboBox) {
        BooleanSettingOption option = (BooleanSettingOption) comboBox.getSelectedItem();
        return option != null ? option.value : null;
    }

    private String getSelectedHttpVersion() {
        HttpVersionOption option = (HttpVersionOption) httpVersionComboBox.getSelectedItem();
        return option != null ? option.value : HttpRequestVersions.AUTO;
    }

    private HttpRequestProxyPolicy getSelectedProxyPolicy() {
        ProxyPolicyOption option = (ProxyPolicyOption) proxyPolicyComboBox.getSelectedItem();
        return option != null ? option.value : HttpRequestProxyPolicy.DEFAULT;
    }

    private Integer parseRequestTimeoutOrNull() {
        return parseIntegerOrNull(requestTimeoutField.getText());
    }

    private Integer parseIntegerOrNull(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0 || parsed > Integer.MAX_VALUE) {
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String validateRequestTimeout(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(normalized);
            if (parsed < 0 || parsed > Integer.MAX_VALUE) {
                return I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_TIMEOUT_VALIDATION);
            }
            return null;
        } catch (NumberFormatException ex) {
            return I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_TIMEOUT_VALIDATION);
        }
    }

    private String validateWebSocketPingInterval() {
        if (!webSocketSettingsVisible || !isCustomWebSocketPingIntervalSelected()) {
            return null;
        }
        String normalized = webSocketPingIntervalCustomField.getText() == null
                ? ""
                : webSocketPingIntervalCustomField.getText().trim();
        if (normalized.isEmpty()) {
            return I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_VALIDATION,
                    MIN_CUSTOM_WEBSOCKET_PING_INTERVAL_MS);
        }
        try {
            long parsed = Long.parseLong(normalized);
            if (parsed < MIN_CUSTOM_WEBSOCKET_PING_INTERVAL_MS || parsed > Integer.MAX_VALUE) {
                return I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_VALIDATION,
                        MIN_CUSTOM_WEBSOCKET_PING_INTERVAL_MS);
            }
            return null;
        } catch (NumberFormatException ex) {
            return I18nUtil.getMessage(MessageKeys.REQUEST_SETTINGS_WEBSOCKET_PING_VALIDATION,
                    MIN_CUSTOM_WEBSOCKET_PING_INTERVAL_MS);
        }
    }

    private static final class BooleanSettingOption {
        private final Boolean value;
        private final String label;

        private BooleanSettingOption(Boolean value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class HttpVersionOption {
        private final String value;
        private final String label;

        private HttpVersionOption(String value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class ProxyPolicyOption {
        private final HttpRequestProxyPolicy value;
        private final String label;

        private ProxyPolicyOption(HttpRequestProxyPolicy value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class IntegerSettingOption {
        private final Integer value;
        private final String label;

        private IntegerSettingOption(Integer value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class ShrinkableWrapTextArea extends JTextArea {
        private ShrinkableWrapTextArea(String text) {
            super(text);
        }

        @Override
        public Dimension getMinimumSize() {
            Dimension minimumSize = super.getMinimumSize();
            return new Dimension(0, minimumSize.height);
        }
    }

    private static final class DigitsOnlyDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (isDigits(string)) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (isDigits(text)) {
                super.replace(fb, offset, length, text, attrs);
            }
        }

        private boolean isDigits(String text) {
            if (text == null || text.isEmpty()) {
                return true;
            }
            for (int i = 0; i < text.length(); i++) {
                if (!Character.isDigit(text.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private ViewportWidthPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return SCROLL_UNIT_INCREMENT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(SCROLL_UNIT_INCREMENT, visibleRect.height - SCROLL_UNIT_INCREMENT);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
