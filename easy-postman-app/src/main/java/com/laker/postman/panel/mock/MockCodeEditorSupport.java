package com.laker.postman.panel.mock;

import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ShorthandCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.Font;

final class MockCodeEditorSupport {
    private static final String COMPLETION_INSTALLED = "EasyPostman.mockCodeCompletionInstalled";

    private MockCodeEditorSupport() {
    }

    static void installCompletion(RSyntaxTextArea editor) {
        if (Boolean.TRUE.equals(editor.getClientProperty(COMPLETION_INSTALLED))) return;

        DefaultCompletionProvider provider = new MockCompletionProvider();
        addBasic(provider, "pm.request.method");
        addBasic(provider, "pm.request.path");
        addBasic(provider, "pm.request.body");
        addShorthand(provider, "pm.request.header", "pm.request.header('Header-Name')");
        addShorthand(provider, "pm.request.query", "pm.request.query('name')");
        addShorthand(provider, "pm.request.pathVariable", "pm.request.pathVariable('id')");
        addShorthand(provider, "pm.response.setStatusCode", "pm.response.setStatusCode(200)");
        addShorthand(provider, "pm.response.setHeader", "pm.response.setHeader('Header-Name', 'value')");
        addShorthand(provider, "pm.response.getHeader", "pm.response.getHeader('Header-Name')");
        addShorthand(provider, "pm.response.removeHeader", "pm.response.removeHeader('Header-Name')");
        addShorthand(provider, "pm.response.setBody", "pm.response.setBody(JSON.stringify({ ok: true }))");
        addShorthand(provider, "pm.response.setDelayMs", "pm.response.setDelayMs(1000)");
        addShorthand(provider, "pm.state.get", "pm.state.get('key')");
        addShorthand(provider, "pm.state.set", "pm.state.set('key', value)");
        addShorthand(provider, "pm.state.has", "pm.state.has('key')");
        addShorthand(provider, "pm.state.unset", "pm.state.unset('key')");
        addShorthand(provider, "pm.state.clear", "pm.state.clear()");
        addShorthand(provider, "pm.state.toObject", "pm.state.toObject()");

        AutoCompletion completion = new AutoCompletion(provider);
        completion.setAutoActivationEnabled(true);
        completion.setAutoActivationDelay(180);
        completion.setParameterAssistanceEnabled(true);
        completion.install(editor);
        editor.setToolTipText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_MOCK_COMPLETION_HINT));
        editor.putClientProperty(COMPLETION_INSTALLED, Boolean.TRUE);
    }

    static JButton createExamplesButton(RSyntaxTextArea editor) {
        JButton button = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_EXAMPLE_LIBRARY), false, "icons/code.svg");
        button.addActionListener(event -> MockCodeExamplesDialog.showDialog(button, editor));
        return button;
    }

    static JButton createClearButton(RSyntaxTextArea editor) {
        JButton button = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_MOCK_CLEAR), false, "icons/clear.svg");
        button.addActionListener(event -> editor.setText(""));
        return button;
    }

    static JLabel createQuickReferenceLabel() {
        JLabel label = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_MOCK_QUICK_REFERENCE));
        label.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        label.setToolTipText(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_MOCK_COMPLETION_HINT));
        return label;
    }

    private static void addBasic(DefaultCompletionProvider provider, String value) {
        provider.addCompletion(new BasicCompletion(provider, value));
    }

    private static void addShorthand(DefaultCompletionProvider provider, String input, String replacement) {
        provider.addCompletion(new ShorthandCompletion(provider, input, replacement));
    }

    private static final class MockCompletionProvider extends DefaultCompletionProvider {
        @Override
        public boolean isAutoActivateOkay(JTextComponent component) {
            Document document = component.getDocument();
            int caret = component.getCaretPosition();
            if (caret <= 0) return false;
            try {
                return isValidChar(document.getText(caret - 1, 1).charAt(0));
            } catch (BadLocationException ignored) {
                return false;
            }
        }

        @Override
        protected boolean isValidChar(char value) {
            return Character.isLetterOrDigit(value) || value == '_' || value == '.';
        }
    }
}
