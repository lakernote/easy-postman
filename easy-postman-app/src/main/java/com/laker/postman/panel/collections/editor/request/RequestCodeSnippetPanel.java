package com.laker.postman.panel.collections.editor.request;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SyntaxEditorScrollPane;
import com.laker.postman.common.component.ToolWindowActionToolbar;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.CopyButton;
import com.laker.postman.common.component.button.RefreshButton;
import com.laker.postman.common.component.setting.SettingsInputStyle;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.util.EditorThemeUtil;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

final class RequestCodeSnippetPanel extends JPanel {
    private static final int ACTION_BUTTON_SIZE = 28;

    private final JComboBox<RequestCodeSnippetLanguage> languageComboBox;
    private final JCheckBox resolveVariablesCheckBox;
    private final RSyntaxTextArea codeArea;
    private HttpRequestItem currentRequest;

    RequestCodeSnippetPanel(Runnable collapseAction, Runnable refreshAction) {
        setLayout(new BorderLayout(0, 0));
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel titleLabel = createTitleLabel();
        JButton refreshButton = createActionButton(new RefreshButton(IconUtil.SIZE_SMALL));
        refreshButton.addActionListener(e -> refreshAction.run());

        JButton collapseButton = createCollapseButton();
        collapseButton.addActionListener(e -> collapseAction.run());

        languageComboBox = new JComboBox<>(RequestCodeSnippetLanguage.values());
        languageComboBox.setFocusable(false);
        languageComboBox.setPreferredSize(new Dimension(220, 30));
        SettingsInputStyle.apply(languageComboBox);
        languageComboBox.addActionListener(e -> regenerateCode());

        resolveVariablesCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_CODE_RESOLVE_VARIABLES), true);
        resolveVariablesCheckBox.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
        resolveVariablesCheckBox.setOpaque(false);
        resolveVariablesCheckBox.setToolTipText(
                I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_CODE_RESOLVE_VARIABLES_TOOLTIP));
        resolveVariablesCheckBox.addActionListener(e -> refreshAction.run());

        JButton copyButton = createActionButton(new CopyButton());
        copyButton.addActionListener(e -> copyCode());

        JPanel header = new JPanel(new BorderLayout(8, 0));
        ToolWindowSurfaceStyle.applyToolWindowToolbarSeparator(header, 0, 0, 8, 0);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(ToolWindowActionToolbar.inlineRight(refreshButton, collapseButton), BorderLayout.EAST);

        JPanel toolbar = new JPanel(new BorderLayout(8, 8));
        ToolWindowSurfaceStyle.applyCard(toolbar);
        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.setOpaque(false);
        controls.add(languageComboBox, BorderLayout.CENTER);
        controls.add(copyButton, BorderLayout.EAST);
        toolbar.add(header, BorderLayout.NORTH);
        toolbar.add(controls, BorderLayout.CENTER);
        toolbar.add(resolveVariablesCheckBox, BorderLayout.SOUTH);

        codeArea = createCodeArea();
        SyntaxEditorScrollPane scrollPane = new SyntaxEditorScrollPane(codeArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        ToolWindowSurfaceStyle.applyScrollPaneCard(scrollPane);

        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JLabel createTitleLabel() {
        JLabel titleLabel = new JLabel(I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_CODE_SNIPPET_TITLE));
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, +1));
        titleLabel.setForeground(ModernColors.getTextPrimary());
        return titleLabel;
    }

    private JButton createCollapseButton() {
        JButton button = new JButton(IconUtil.createThemed("icons/tool-window-hide.svg",
                IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        button.setToolTipText(I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_COLLAPSE));
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return createActionButton(button);
    }

    private JButton createActionButton(JButton button) {
        Dimension size = new Dimension(ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        return button;
    }

    void updateRequest(HttpRequestItem request) {
        currentRequest = request;
        regenerateCode();
    }

    private RSyntaxTextArea createCodeArea() {
        RSyntaxTextArea editor = new FallbackAwareRSyntaxTextArea(12, 36);
        editor.setEditable(false);
        editor.setCodeFoldingEnabled(true);
        editor.setAntiAliasingEnabled(true);
        editor.setPaintTabLines(true);
        editor.setHighlightCurrentLine(false);
        editor.setLineWrap(false);
        editor.setTabSize(4);
        EditorThemeUtil.loadTheme(editor);
        return editor;
    }

    private void regenerateCode() {
        RequestCodeSnippetLanguage language = (RequestCodeSnippetLanguage) languageComboBox.getSelectedItem();
        if (language == null) {
            language = RequestCodeSnippetLanguage.CURL;
        }
        codeArea.setSyntaxEditingStyle(language.getSyntaxStyle());
        String code = RequestCodeSnippetGenerator.generate(currentRequest, language, resolveVariablesCheckBox.isSelected());
        if (code.equals(codeArea.getText())) {
            return;
        }
        codeArea.setText(code);
        codeArea.setCaretPosition(0);
    }

    private void copyCode() {
        String code = codeArea.getText();
        if (code == null || code.isBlank()) {
            return;
        }
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(code), null);
        NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_CODE_COPIED));
    }
}
