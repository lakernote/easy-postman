package com.laker.postman.panel.mock;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.EasyJSpinner;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.mock.app.MockCollectionChoice;
import com.laker.postman.mock.model.MockCollectionSource;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.util.CommonI18n;
import com.laker.postman.util.CommonMessageKeys;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class MockServerConfigDialog extends JDialog {
    private final JTextField nameField = new JTextField();
    private final EasyJSpinner portSpinner = new EasyJSpinner(new SpinnerNumberModel(3001, 1, 65_535, 1));
    private final EasyJSpinner delaySpinner = new EasyJSpinner(new SpinnerNumberModel(0, 0, 60_000, 50));
    private final JCheckBox accessProtectionCheck = new JCheckBox(
            I18nUtil.getMessage(MessageKeys.MOCK_SERVER_REQUIRE_ACCESS_KEY));
    private final JPasswordField accessKeyField = new JPasswordField();
    private final JCheckBox corsCheck = new JCheckBox(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CORS));
    private final JCheckBox bodyCheck = new JCheckBox(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_MATCH_BODY));
    private final JCheckBox autoStartCheck = new JCheckBox(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_AUTO_START));
    private final JCheckBox recordLogsCheck = new JCheckBox(
            I18nUtil.getMessage(MessageKeys.MOCK_SERVER_RECORD_CALL_LOGS));
    private final JTextField headersField = new JTextField();
    private final JLabel sourceSummaryLabel = new JLabel();
    private final JLabel validationLabel = new JLabel(" ");
    private final List<SourceOption> sourceOptions = new ArrayList<>();
    private final MockServerDefinition source;
    private final boolean newDefinition;
    private JButton advancedButton;
    private JPanel advancedPanel;
    private JPanel accessKeyPanel;
    private JLabel accessKeyLabel;
    private MockServerDefinition result;

    private MockServerConfigDialog(Component owner,
                                   MockServerDefinition existing,
                                   List<MockCollectionChoice> collections,
                                   int suggestedPort,
                                   String preferredCollectionId) {
        super(resolveOwner(owner),
                CommonI18n.get(existing == null ? CommonMessageKeys.BUTTON_ADD : CommonMessageKeys.BUTTON_EDIT),
                ModalityType.APPLICATION_MODAL);
        newDefinition = existing == null;
        source = newDefinition ? new MockServerDefinition() : existing.copy();
        ToolWindowSurfaceStyle.applyDialogWindowChrome(this);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(createContent(collections, suggestedPort, preferredCollectionId), BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(
                event -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        pack();
        setMinimumSize(new Dimension(640, getPreferredSize().height));
        setLocationRelativeTo(owner);
    }

    static MockServerDefinition showDialog(Component owner,
                                           MockServerDefinition existing,
                                           List<MockCollectionChoice> collections,
                                           int suggestedPort) {
        return showDialog(owner, existing, collections, suggestedPort, null);
    }

    static MockServerDefinition showDialog(Component owner,
                                           MockServerDefinition existing,
                                           List<MockCollectionChoice> collections,
                                           int suggestedPort,
                                           String preferredCollectionId) {
        MockServerConfigDialog dialog = new MockServerConfigDialog(
                owner, existing, collections, suggestedPort, preferredCollectionId);
        dialog.setVisible(true);
        return dialog.result;
    }

    private JPanel createContent(List<MockCollectionChoice> collections,
                                 int suggestedPort,
                                 String preferredCollectionId) {
        JPanel outer = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(outer);

        JPanel header = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogHeader(header, 14, 18, 12, 18);
        JLabel title = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_TITLE));
        title.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 1));
        JLabel hint = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_LOCAL_HINT));
        hint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        hint.setForeground(ModernColors.getTextSecondary());
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        outer.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new MigLayout(
                "insets 16 18 18 18,fillx,wrap 2,gapx 14,gapy 9,novisualpadding,hidemode 3",
                "[right]14[grow,fill]", "[]"));
        ToolWindowSurfaceStyle.applyDialogSurface(form);
        form.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_NAME)));
        form.add(nameField, "growx");
        form.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SOURCES)), "aligny top");
        form.add(createSourcePicker(collections), "growx");

        advancedButton = new JButton();
        advancedButton.putClientProperty(
                FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        advancedButton.setHorizontalAlignment(JButton.LEFT);
        advancedButton.setFocusable(false);
        form.add(new JLabel());
        form.add(advancedButton, "growx");

        advancedPanel = createAdvancedPanel();
        form.add(new JLabel());
        form.add(advancedPanel, "growx");
        advancedButton.addActionListener(event -> setAdvancedVisible(!advancedPanel.isVisible()));

        validationLabel.setForeground(ModernColors.getError());
        validationLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        form.add(new JLabel());
        form.add(validationLabel, "growx");
        outer.add(form, BorderLayout.CENTER);

        populate(collections == null ? List.of() : collections, suggestedPort, preferredCollectionId);
        return outer;
    }

    private JPanel createSourcePicker(List<MockCollectionChoice> collections) {
        JPanel section = new JPanel(new BorderLayout(0, 7));
        ToolWindowSurfaceStyle.applyDialogSection(section);

        JPanel options = new JPanel(new MigLayout(
                "insets 0,fillx,wrap 1,gapy 4,novisualpadding", "[grow,fill]", "[]"));
        ToolWindowSurfaceStyle.applyDialogSurface(options);
        Set<String> knownIds = new HashSet<>();
        for (MockCollectionChoice collection : collections == null ? List.<MockCollectionChoice>of() : collections) {
            knownIds.add(collection.id());
            addSourceOption(options, collection.id(), collection.name(), collection.toString(), true);
        }
        List<String> existingIds = source.collectionSourceIds();
        List<String> existingNames = source.collectionSourceNames();
        for (int index = 0; index < existingIds.size(); index++) {
            String id = existingIds.get(index);
            if (knownIds.contains(id)) continue;
            String name = index < existingNames.size() ? existingNames.get(index) : id;
            addSourceOption(options, id, name,
                    I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SOURCE_MISSING, name), false);
        }
        if (sourceOptions.isEmpty()) {
            JLabel empty = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SOURCES_EMPTY));
            empty.setForeground(ModernColors.getTextSecondary());
            empty.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
            options.add(empty, "growx");
        }

        JScrollPane scrollPane = new JScrollPane(options);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        int sourceHeight = Math.max(64, Math.min(116, Math.max(1, sourceOptions.size()) * 28 + 8));
        scrollPane.setPreferredSize(new Dimension(480, sourceHeight));
        ToolWindowSurfaceStyle.applyDialogScrollPane(scrollPane);
        section.add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new MigLayout(
                "insets 2 0 0 0,fillx,wrap 1,gapy 2,novisualpadding", "[grow,fill]", "[]"));
        ToolWindowSurfaceStyle.applyDialogSurface(footer);
        sourceSummaryLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        JLabel hint = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SOURCES_HINT));
        hint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        hint.setForeground(ModernColors.getTextSecondary());
        footer.add(sourceSummaryLabel, "growx");
        footer.add(hint, "growx");
        section.add(footer, BorderLayout.SOUTH);
        return section;
    }

    private void addSourceOption(JPanel parent,
                                 String id,
                                 String name,
                                 String label,
                                 boolean enabled) {
        JCheckBox checkBox = new JCheckBox(label);
        checkBox.setEnabled(enabled);
        checkBox.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        checkBox.addActionListener(event -> updateSourceSummary());
        sourceOptions.add(new SourceOption(id, name, checkBox));
        parent.add(checkBox, "growx");
    }

    private JPanel createAdvancedPanel() {
        JPanel panel = new JPanel(new MigLayout(
                "insets 10 12 10 12,fillx,wrap 2,gapx 12,gapy 7,novisualpadding,hidemode 3",
                "[right]12[grow,fill]", "[]"));
        ToolWindowSurfaceStyle.applyDialogSection(panel);
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_PORT)));
        panel.add(portSpinner, "w 150!");

        panel.add(new JLabel());
        panel.add(accessProtectionCheck, "growx");
        accessKeyPanel = new JPanel(new MigLayout(
                "insets 0,fillx,wrap 1,gapy 3,novisualpadding", "[grow,fill]", "[]"));
        ToolWindowSurfaceStyle.applyDialogSurface(accessKeyPanel);
        accessKeyPanel.add(accessKeyField, "growx");
        JLabel accessHint = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ACCESS_KEY_HINT));
        accessHint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        accessHint.setForeground(ModernColors.getTextSecondary());
        accessKeyPanel.add(accessHint, "growx");
        accessKeyLabel = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ACCESS_KEY));
        panel.add(accessKeyLabel, "aligny top");
        panel.add(accessKeyPanel, "growx");

        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_DELAY)));
        panel.add(delaySpinner, "w 150!");
        JPanel options = new JPanel(new MigLayout(
                "insets 0,fillx,wrap 1,gapy 4,novisualpadding", "[grow]", "[]"));
        ToolWindowSurfaceStyle.applyDialogSurface(options);
        options.add(corsCheck);
        options.add(autoStartCheck);
        options.add(recordLogsCheck);
        options.add(bodyCheck);
        panel.add(new JLabel());
        panel.add(options, "growx");
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_MATCH_HEADERS)));
        panel.add(headersField, "growx");
        JLabel headersHint = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_MATCH_HEADERS_HINT));
        headersHint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        headersHint.setForeground(ModernColors.getTextSecondary());
        panel.add(new JLabel());
        panel.add(headersHint, "growx");
        accessProtectionCheck.addActionListener(event -> {
            accessKeyLabel.setVisible(accessProtectionCheck.isSelected());
            accessKeyPanel.setVisible(accessProtectionCheck.isSelected());
            resizeToContent();
        });
        return panel;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new MigLayout(
                "insets 10 18 10 18,fillx,novisualpadding", "[grow][]8[]", "[]"));
        ToolWindowSurfaceStyle.applyDialogFooter(footer);
        JButton cancel = ModernButtonFactory.createCompactButton(
                CommonI18n.get(CommonMessageKeys.BUTTON_CANCEL), false, "icons/cancel.svg");
        JButton save = ModernButtonFactory.createCompactButton(
                CommonI18n.get(CommonMessageKeys.BUTTON_SAVE), true, "icons/save.svg");
        cancel.addActionListener(event -> dispose());
        save.addActionListener(event -> save());
        footer.add(new JLabel(), "growx");
        footer.add(cancel);
        footer.add(save);
        getRootPane().setDefaultButton(save);
        return footer;
    }

    private void populate(List<MockCollectionChoice> collections,
                          int suggestedPort,
                          String preferredCollectionId) {
        nameField.setText(source.getName());
        if (source.getName() == null || source.getName().isBlank()) {
            nameField.setText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_TITLE));
        }
        portSpinner.setValue(newDefinition || source.getPort() <= 0 ? suggestedPort : source.getPort());
        delaySpinner.setValue(source.getFixedDelayMs());
        accessKeyField.setText(source.getAccessKey() == null ? "" : source.getAccessKey());
        accessProtectionCheck.setSelected(!new String(accessKeyField.getPassword()).isBlank());
        accessKeyLabel.setVisible(accessProtectionCheck.isSelected());
        accessKeyPanel.setVisible(accessProtectionCheck.isSelected());
        corsCheck.setSelected(source.isCorsEnabled());
        bodyCheck.setSelected(source.isMatchRequestBody());
        autoStartCheck.setSelected(source.isAutoStart());
        recordLogsCheck.setSelected(source.isRecordCallLogs());
        headersField.setText(String.join(", ", source.getMatchHeaderNames() == null
                ? List.of() : source.getMatchHeaderNames()));

        List<String> selectedIds = source.collectionSourceIds();
        if (selectedIds.isEmpty() && preferredCollectionId != null && !preferredCollectionId.isBlank()) {
            selectedIds = List.of(preferredCollectionId);
            if ("Mock Server".equals(source.getName())) {
                for (MockCollectionChoice collection : collections) {
                    if (preferredCollectionId.equals(collection.id())) {
                        nameField.setText(I18nUtil.getMessage(
                                MessageKeys.MOCK_SERVER_DEFAULT_NAME, collection.name()));
                        break;
                    }
                }
            }
        }
        for (SourceOption option : sourceOptions) {
            option.checkBox().setSelected(selectedIds.contains(option.id()));
        }
        updateSourceSummary();

        boolean showAdvanced = source.getFixedDelayMs() > 0
                || !source.isCorsEnabled()
                || source.isAutoStart()
                || !source.isRecordCallLogs()
                || source.isMatchRequestBody()
                || !headersField.getText().isBlank()
                || accessProtectionCheck.isSelected();
        setAdvancedVisible(showAdvanced);
        SwingUtilities.invokeLater(nameField::requestFocusInWindow);
    }

    private void setAdvancedVisible(boolean visible) {
        advancedPanel.setVisible(visible);
        advancedButton.setText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ADVANCED));
        advancedButton.setIcon(IconUtil.createThemed(
                visible ? "icons/chevron-down.svg" : "icons/chevron-right.svg", 14, 14));
        resizeToContent();
    }

    private void resizeToContent() {
        revalidate();
        pack();
        setMinimumSize(new Dimension(640, getPreferredSize().height));
    }

    private void updateSourceSummary() {
        long selected = sourceOptions.stream().filter(option -> option.checkBox().isSelected()).count();
        sourceSummaryLabel.setText(I18nUtil.getMessage(
                MessageKeys.MOCK_SERVER_ROUTE_SOURCES_SELECTED, selected));
    }

    private void save() {
        commitSpinner(portSpinner);
        commitSpinner(delaySpinner);
        if (nameField.getText().isBlank()) {
            validationLabel.setText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_VALIDATION_REQUIRED));
            return;
        }
        List<SourceOption> selectedSources = sourceOptions.stream()
                .filter(option -> option.checkBox().isSelected())
                .toList();
        MockServerDefinition definition = source.copy();
        definition.setName(nameField.getText().trim());
        definition.setCollectionSources(selectedSources.stream()
                .map(option -> new MockCollectionSource(option.id(), option.name()))
                .toList());
        definition.setHost(MockServerDefinition.ALL_INTERFACES_HOST);
        definition.setPort((Integer) portSpinner.getValue());
        definition.setAccessKey(accessProtectionCheck.isSelected()
                ? new String(accessKeyField.getPassword()).trim() : "");
        definition.setFixedDelayMs((Integer) delaySpinner.getValue());
        definition.setCorsEnabled(corsCheck.isSelected());
        definition.setMatchRequestBody(bodyCheck.isSelected());
        definition.setAutoStart(autoStartCheck.isSelected());
        definition.setRecordCallLogs(recordLogsCheck.isSelected());
        definition.setMatchHeaderNames(parseHeaders(headersField.getText()));
        result = definition;
        dispose();
    }

    private List<String> parseHeaders(String text) {
        if (text == null || text.isBlank()) return new ArrayList<>();
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void commitSpinner(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (java.text.ParseException ignored) {
            if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
                JFormattedTextField field = editor.getTextField();
                field.setValue(spinner.getValue());
            }
        }
    }

    private static Window resolveOwner(Component component) {
        return component instanceof Window window ? window : SwingUtilities.getWindowAncestor(component);
    }

    private record SourceOption(String id, String name, JCheckBox checkBox) {
    }
}
