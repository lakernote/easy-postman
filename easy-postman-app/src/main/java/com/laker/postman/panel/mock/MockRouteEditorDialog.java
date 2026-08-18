package com.laker.postman.panel.mock;

import com.laker.postman.common.component.EasyComboBox;
import com.laker.postman.common.component.EasyJSpinner;
import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SearchableTextArea;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.util.CommonI18n;
import com.laker.postman.util.CommonMessageKeys;
import com.laker.postman.util.EditorThemeUtil;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import net.miginfocom.swing.MigLayout;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

final class MockRouteEditorDialog extends JDialog {
    private final JTextField requestNameField = new JTextField();
    private final EasyComboBox<String> methodBox = new EasyComboBox<>();
    private final JTextField pathField = new JTextField();
    private final JTextField responseNameField = new JTextField();
    private final EasyJSpinner statusSpinner = new EasyJSpinner(new SpinnerNumberModel(200, 100, 599, 1));
    private final EasyJSpinner delaySpinner = new EasyJSpinner(new SpinnerNumberModel(0, 0, 60_000, 50));
    private final JTextArea headersArea = new JTextArea(4, 40);
    private final RSyntaxTextArea bodyEditor = editor(SyntaxConstants.SYNTAX_STYLE_JSON);
    private final RSyntaxTextArea scriptEditor = editor(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
    private final JLabel validationLabel = new JLabel(" ");
    private final JLabel codeStatusLabel = new JLabel();
    private final boolean requestNameEditable;
    private JTabbedPane tabs;
    private Draft result;

    private MockRouteEditorDialog(Component owner, Draft source, boolean requestNameEditable) {
        super(resolveOwner(owner),
                I18nUtil.getMessage(source.exampleId().isBlank()
                        ? MessageKeys.MOCK_SERVER_ROUTE_ADD
                        : MessageKeys.MOCK_SERVER_ROUTE_EDIT),
                ModalityType.APPLICATION_MODAL);
        this.requestNameEditable = requestNameEditable;
        MockCodeEditorSupport.installCompletion(scriptEditor);
        ToolWindowSurfaceStyle.applyDialogWindowChrome(this);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(createContent(source), BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(
                event -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        setPreferredSize(new Dimension(860, 660));
        pack();
        setMinimumSize(new Dimension(700, 520));
        setLocationRelativeTo(owner);
    }

    static Draft showDialog(Component owner, Draft source, boolean requestNameEditable) {
        MockRouteEditorDialog dialog = new MockRouteEditorDialog(owner, source, requestNameEditable);
        dialog.setVisible(true);
        return dialog.result;
    }

    private JComponent createContent(Draft source) {
        JPanel outer = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(outer);

        JPanel header = new JPanel(new MigLayout(
                "insets 12 16 10 16,fillx,wrap 4,novisualpadding",
                "[][grow,fill][][grow,fill]", "[]8[]"));
        ToolWindowSurfaceStyle.applyDialogHeader(header, 0, 0, 0, 0);
        header.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_REQUEST_NAME)));
        header.add(requestNameField, "growx");
        header.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_RESPONSE_NAME)));
        header.add(responseNameField, "growx");
        header.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_METHOD)));
        header.add(methodBox, "w 120!");
        header.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_PATH)));
        header.add(pathField, "growx");
        outer.add(header, BorderLayout.NORTH);

        tabs = new JTabbedPane();
        tabs.addTab(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_RESPONSE), createResponseTab());
        tabs.addTab(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_MOCK), createCodeTab());
        outer.add(tabs, BorderLayout.CENTER);

        populate(source);
        return outer;
    }

    private JComponent createResponseTab() {
        JPanel panel = new JPanel(new MigLayout(
                "insets 10 12 10 12,fill,wrap 4,novisualpadding",
                "[][150!][][150!]", "[]8[]4[90!]8[]4[grow,fill]"));
        ToolWindowSurfaceStyle.applyDialogSurface(panel);
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_STATUS)));
        panel.add(statusSpinner);
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_DELAY)));
        panel.add(delaySpinner, "wrap");

        JLabel headersLabel = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_RESPONSE_HEADERS));
        headersLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD));
        panel.add(headersLabel, "span 4,wrap");
        JScrollPane headersScroll = new JScrollPane(headersArea);
        ToolWindowSurfaceStyle.applyDialogScrollPane(headersScroll);
        panel.add(headersScroll, "span 4,grow,pushx,wrap");

        JLabel bodyLabel = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_RESPONSE_BODY));
        bodyLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD));
        panel.add(bodyLabel, "span 4,wrap");
        panel.add(new SearchableTextArea(bodyEditor), "span 4,grow,push,wrap");
        return panel;
    }

    private JComponent createCodeTab() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(panel);

        JPanel header = new JPanel(new MigLayout(
                "insets 7 10 7 12,fillx,novisualpadding", "[grow,fill][]8[]8[]", "[]"));
        ToolWindowSurfaceStyle.applyDialogBottomSeparator(header);
        JLabel hint = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_MOCK_HINT));
        hint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        hint.setForeground(ModernColors.getTextSecondary());
        codeStatusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        JButton examples = MockCodeEditorSupport.createExamplesButton(scriptEditor);
        JButton clear = MockCodeEditorSupport.createClearButton(scriptEditor);
        header.add(hint, "growx,wmin 0");
        header.add(codeStatusLabel);
        header.add(examples);
        header.add(clear);
        panel.add(header, BorderLayout.NORTH);
        panel.add(new SearchableTextArea(scriptEditor), BorderLayout.CENTER);

        JPanel footer = new JPanel(new MigLayout(
                "insets 6 10 6 12,fillx,novisualpadding", "[grow]", "[]"));
        ToolWindowSurfaceStyle.applyDialogFooter(footer);
        JLabel quickReference = MockCodeEditorSupport.createQuickReferenceLabel();
        quickReference.setForeground(ModernColors.getTextSecondary());
        footer.add(quickReference, "growx,wmin 0");
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent createFooter() {
        JPanel footer = new JPanel(new MigLayout(
                "insets 9 16 9 16,fillx,novisualpadding", "[grow][]8[]", "[]"));
        ToolWindowSurfaceStyle.applyDialogFooter(footer);
        validationLabel.setForeground(com.laker.postman.common.constants.ModernColors.getError());
        validationLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        JButton cancel = ModernButtonFactory.createCompactButton(
                CommonI18n.get(CommonMessageKeys.BUTTON_CANCEL), false, "icons/cancel.svg");
        JButton save = ModernButtonFactory.createCompactButton(
                CommonI18n.get(CommonMessageKeys.BUTTON_SAVE), true, "icons/save.svg");
        cancel.addActionListener(event -> dispose());
        save.addActionListener(event -> save());
        footer.add(validationLabel, "growx,wmin 0");
        footer.add(cancel);
        footer.add(save);
        getRootPane().setDefaultButton(save);
        return footer;
    }

    private void populate(Draft source) {
        List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
                .forEach(methodBox::addItem);
        methodBox.setSelectedItem(source.method());
        requestNameField.setText(source.requestName());
        requestNameField.setEditable(requestNameEditable);
        pathField.setText(source.path());
        responseNameField.setText(source.responseName());
        statusSpinner.setValue(source.statusCode());
        delaySpinner.setValue(source.delayMs());
        headersArea.setText(formatHeaders(source.headers()));
        bodyEditor.setText(source.body());
        bodyEditor.setCaretPosition(0);
        scriptEditor.setText(source.script());
        scriptEditor.setCaretPosition(0);
        scriptEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateCodeStatus(); }
            @Override public void removeUpdate(DocumentEvent event) { updateCodeStatus(); }
            @Override public void changedUpdate(DocumentEvent event) { updateCodeStatus(); }
        });
        updateCodeStatus();
        if (!source.script().isBlank()) tabs.setSelectedIndex(1);
        SwingUtilities.invokeLater(() -> (requestNameEditable ? requestNameField : pathField).requestFocusInWindow());
    }

    private void updateCodeStatus() {
        boolean enabled = !scriptEditor.getText().isBlank();
        codeStatusLabel.setText(I18nUtil.getMessage(enabled
                ? MessageKeys.MOCK_SERVER_CODE_MOCK_ENABLED
                : MessageKeys.MOCK_SERVER_CODE_MOCK_DISABLED));
        codeStatusLabel.setForeground(enabled ? ModernColors.getSuccess() : ModernColors.getTextSecondary());
    }

    private void save() {
        commitSpinner(statusSpinner);
        commitSpinner(delaySpinner);
        if (requestNameField.getText().isBlank() || pathField.getText().isBlank()
                || responseNameField.getText().isBlank()) {
            validationLabel.setText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_VALIDATION));
            return;
        }
        List<HttpHeader> headers;
        try {
            headers = parseHeaders(headersArea.getText());
        } catch (IllegalArgumentException ex) {
            validationLabel.setText(ex.getMessage());
            return;
        }
        String path = pathField.getText().trim();
        if (!path.startsWith("/") && !path.startsWith("http://") && !path.startsWith("https://")) {
            path = "/" + path;
        }
        result = new Draft(
                "", requestNameField.getText().trim(), String.valueOf(methodBox.getSelectedItem()), path,
                responseNameField.getText().trim(), (Integer) statusSpinner.getValue(),
                (Integer) delaySpinner.getValue(), headers, bodyEditor.getText(), scriptEditor.getText()
        );
        dispose();
    }

    private static RSyntaxTextArea editor(String syntax) {
        RSyntaxTextArea editor = new FallbackAwareRSyntaxTextArea();
        editor.setSyntaxEditingStyle(syntax);
        editor.setCodeFoldingEnabled(true);
        editor.setAutoIndentEnabled(true);
        editor.setBracketMatchingEnabled(true);
        editor.setMarkOccurrences(true);
        editor.setAntiAliasingEnabled(true);
        editor.setTabSize(4);
        EditorThemeUtil.loadTheme(editor);
        EditorThemeUtil.installViewportClippedTokenPainter(editor);
        return editor;
    }

    private static List<HttpHeader> parseHeaders(String text) {
        List<HttpHeader> headers = new ArrayList<>();
        if (text == null || text.isBlank()) return headers;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_HEADERS_INVALID));
            }
            headers.add(new HttpHeader(true, line.substring(0, separator).trim(), line.substring(separator + 1).trim()));
        }
        return headers;
    }

    private static String formatHeaders(List<HttpHeader> headers) {
        if (headers == null || headers.isEmpty()) return "Content-Type: application/json; charset=UTF-8";
        return headers.stream()
                .filter(header -> header != null && header.getKey() != null && !header.getKey().isBlank())
                .map(header -> header.getKey() + ": " + (header.getValue() == null ? "" : header.getValue()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
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

    record Draft(
            String exampleId,
            String requestName,
            String method,
            String path,
            String responseName,
            int statusCode,
            int delayMs,
            List<HttpHeader> headers,
            String body,
            String script
    ) {
        Draft {
            exampleId = exampleId == null ? "" : exampleId;
            requestName = requestName == null ? "" : requestName;
            method = method == null || method.isBlank() ? "GET" : method;
            path = path == null || path.isBlank() ? "/" : path;
            responseName = responseName == null || responseName.isBlank()
                    ? I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_DEFAULT_RESPONSE_NAME)
                    : responseName;
            statusCode = statusCode < 100 ? 200 : statusCode;
            delayMs = Math.max(0, delayMs);
            headers = headers == null ? List.of() : List.copyOf(headers);
            body = body == null ? "" : body;
            script = script == null ? "" : script;
        }
    }
}
