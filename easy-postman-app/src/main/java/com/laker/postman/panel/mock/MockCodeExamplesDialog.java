package com.laker.postman.panel.mock;

import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SearchableTextArea;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.CommonI18n;
import com.laker.postman.util.CommonMessageKeys;
import com.laker.postman.util.EditorThemeUtil;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import net.miginfocom.swing.MigLayout;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class MockCodeExamplesDialog extends JDialog {
    private final RSyntaxTextArea targetEditor;
    private final DefaultListModel<MockCodeExampleCatalog.Example> listModel = new DefaultListModel<>();
    private final JList<MockCodeExampleCatalog.Example> exampleList = new JList<>(listModel);
    private final RSyntaxTextArea previewEditor = createPreviewEditor();
    private final JLabel exampleTitle = new JLabel();
    private final JTextArea exampleDescription = new JTextArea(2, 20);
    private JButton insertButton;
    private JButton replaceButton;

    private MockCodeExamplesDialog(Component owner, RSyntaxTextArea targetEditor) {
        super(resolveOwner(owner), I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_EXAMPLE_LIBRARY),
                ModalityType.APPLICATION_MODAL);
        this.targetEditor = targetEditor;
        ToolWindowSurfaceStyle.applyDialogWindowChrome(this);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(createContent(), BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(
                event -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        setPreferredSize(new Dimension(880, 580));
        pack();
        setMinimumSize(new Dimension(760, 500));
        setLocationRelativeTo(getOwner());
        registerListeners();
        loadExamples();
        SwingUtilities.invokeLater(exampleList::requestFocusInWindow);
    }

    static void showDialog(Component owner, RSyntaxTextArea targetEditor) {
        new MockCodeExamplesDialog(owner, targetEditor).setVisible(true);
    }

    private JComponent createContent() {
        JPanel outer = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(outer);

        JPanel header = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogHeader(header, 12, 16, 10, 16);
        JLabel title = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_EXAMPLE_LIBRARY));
        title.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 1));
        JLabel hint = new JLabel(I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_EXAMPLE_LIBRARY_HINT));
        hint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        hint.setForeground(ModernColors.getTextSecondary());
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        outer.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new MigLayout(
                "insets 0,fill,novisualpadding", "[240!][grow,fill]", "[grow,fill]"));
        ToolWindowSurfaceStyle.applyDialogSurface(content);
        content.add(createExampleList(), "grow");
        content.add(createPreview(), "grow");
        outer.add(content, BorderLayout.CENTER);
        return outer;
    }

    private JComponent createExampleList() {
        JPanel left = new JPanel(new MigLayout(
                "insets 10 12 10 12,fill,novisualpadding", "[grow,fill]", "[grow,fill]"));
        ToolWindowSurfaceStyle.applyDialogSurface(left);
        ToolWindowSurfaceStyle.applyDialogRightSeparator(left);

        exampleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        exampleList.setFixedCellHeight(48);
        exampleList.setCellRenderer((list, value, index, selected, focused) -> {
            DefaultListCellRenderer renderer = new DefaultListCellRenderer();
            JLabel label = (JLabel) renderer.getListCellRendererComponent(
                    list, value, index, selected, focused);
            label.setText("<html><b>" + value.title() + "</b><br><span style='font-size:90%'>"
                    + value.category().displayName() + "</span></html>");
            label.setToolTipText(value.description());
            return label;
        });
        JScrollPane scrollPane = new JScrollPane(exampleList);
        ToolWindowSurfaceStyle.applyDialogListScrollPane(scrollPane, exampleList);
        left.add(scrollPane, "grow,push");
        return left;
    }

    private JComponent createPreview() {
        JPanel right = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyDialogSurface(right);

        JPanel summary = new JPanel(new MigLayout(
                "insets 10 14 10 14,fillx,wrap 1,novisualpadding", "[grow,fill]", "[]4[]"));
        ToolWindowSurfaceStyle.applyDialogBottomSeparator(summary);
        exampleTitle.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 1));
        exampleDescription.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        exampleDescription.setForeground(ModernColors.getTextSecondary());
        exampleDescription.setEditable(false);
        exampleDescription.setOpaque(false);
        exampleDescription.setLineWrap(true);
        exampleDescription.setWrapStyleWord(true);
        exampleDescription.setFocusable(false);
        summary.add(exampleTitle, "growx,wmin 0");
        summary.add(exampleDescription, "growx,wmin 0");
        right.add(summary, BorderLayout.NORTH);
        right.add(new SearchableTextArea(previewEditor), BorderLayout.CENTER);
        return right;
    }

    private JComponent createFooter() {
        JPanel footer = new JPanel(new MigLayout(
                "insets 9 16 9 16,fillx,novisualpadding", "[grow][]8[]8[]", "[]"));
        ToolWindowSurfaceStyle.applyDialogFooter(footer);
        JButton cancel = ModernButtonFactory.createCompactButton(
                CommonI18n.get(CommonMessageKeys.BUTTON_CANCEL), false, "icons/cancel.svg");
        insertButton = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_EXAMPLE_INSERT), true, "icons/plus.svg");
        replaceButton = ModernButtonFactory.createCompactButton(
                I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_EXAMPLE_REPLACE), false, "icons/code.svg");
        cancel.addActionListener(event -> dispose());
        insertButton.addActionListener(event -> insertSelected());
        replaceButton.addActionListener(event -> replaceWithSelected());
        footer.add(new JLabel(), "growx");
        footer.add(cancel);
        footer.add(insertButton);
        footer.add(replaceButton);
        getRootPane().setDefaultButton(insertButton);
        return footer;
    }

    private void registerListeners() {
        exampleList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updatePreview();
        });
        exampleList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2) {
                    insertSelected();
                }
            }
        });
    }

    private void loadExamples() {
        MockCodeExampleCatalog.examples().forEach(listModel::addElement);
        exampleList.setSelectedIndex(0);
    }

    private void updatePreview() {
        MockCodeExampleCatalog.Example selected = exampleList.getSelectedValue();
        boolean available = selected != null;
        insertButton.setEnabled(available);
        replaceButton.setEnabled(available);
        exampleTitle.setText(available ? selected.title()
                : I18nUtil.getMessage(MessageKeys.MOCK_SERVER_CODE_EXAMPLE_NO_RESULTS));
        exampleDescription.setText(available ? selected.description() : " ");
        previewEditor.setText(available ? selected.code() : "");
        previewEditor.setCaretPosition(0);
    }

    private void insertSelected() {
        MockCodeExampleCatalog.Example selected = exampleList.getSelectedValue();
        if (selected == null) return;
        String prefix = targetEditor.getText().isBlank() || targetEditor.getCaretPosition() == 0 ? "" : "\n\n";
        String suffix = targetEditor.getText().isBlank()
                || targetEditor.getCaretPosition() == targetEditor.getDocument().getLength() ? "" : "\n\n";
        targetEditor.insert(prefix + selected.code() + suffix, targetEditor.getCaretPosition());
        dispose();
        targetEditor.requestFocusInWindow();
    }

    private void replaceWithSelected() {
        MockCodeExampleCatalog.Example selected = exampleList.getSelectedValue();
        if (selected == null) return;
        targetEditor.setText(selected.code());
        targetEditor.setCaretPosition(0);
        dispose();
        targetEditor.requestFocusInWindow();
    }

    private static RSyntaxTextArea createPreviewEditor() {
        RSyntaxTextArea editor = new FallbackAwareRSyntaxTextArea();
        editor.setEditable(false);
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        editor.setCodeFoldingEnabled(true);
        editor.setAntiAliasingEnabled(true);
        EditorThemeUtil.loadTheme(editor);
        EditorThemeUtil.installViewportClippedTokenPainter(editor);
        return editor;
    }

    private static Window resolveOwner(Component component) {
        return component instanceof Window window ? window : SwingUtilities.getWindowAncestor(component);
    }
}
