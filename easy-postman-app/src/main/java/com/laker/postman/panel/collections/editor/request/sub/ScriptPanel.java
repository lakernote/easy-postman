package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SearchableTextArea;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.HelpButton;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.component.button.SnippetButton;
import com.laker.postman.common.component.dialog.SnippetDialog;
import com.laker.postman.common.component.editor.PostmanJavaScriptTokenMaker;
import com.laker.postman.common.component.editor.ScriptSnippetManager;
import com.laker.postman.common.component.tab.IndicatorTabComponent;
import com.laker.postman.snippet.Snippet;
import com.laker.postman.util.EditorThemeUtil;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.folding.CurlyFoldParser;
import org.fife.ui.rsyntaxtextarea.folding.FoldParserManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;


@Slf4j
public class ScriptPanel extends JPanel {
    private static final String POSTMAN_JS_SYNTAX = "text/postman-javascript";

    private final RSyntaxTextArea prescriptArea;
    private final RSyntaxTextArea postscriptArea;
    private final JTabbedPane tabbedPane;
    private final SnippetButton snippetBtn;
    private final IndicatorTabComponent preScriptTab;
    private final IndicatorTabComponent postScriptTab;
    private final transient AutoCompletion prescriptAc;
    private final transient AutoCompletion postscriptAc;

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(POSTMAN_JS_SYNTAX, PostmanJavaScriptTokenMaker.class.getName());
        // 为自定义语法注册 FoldParser，使代码折叠功能生效
        FoldParserManager.get().addFoldParserMapping(POSTMAN_JS_SYNTAX, new CurlyFoldParser());
    }

    public ScriptPanel() {
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(this);
        // 设置边距
        setBorder(new EmptyBorder(5, 0, 5, 5));

        // 初始化并配置 PreScript 编辑器
        prescriptArea = new FallbackAwareRSyntaxTextArea(6, 40);
        prescriptAc = configureEditor(prescriptArea);
        SearchableTextArea prescriptSearchableArea = new SearchableTextArea(prescriptArea);

        // 初始化并配置 PostScript 编辑器
        postscriptArea = new FallbackAwareRSyntaxTextArea(6, 40);
        postscriptAc = configureEditor(postscriptArea);
        SearchableTextArea postscriptSearchableArea = new SearchableTextArea(postscriptArea);

        // 创建选项卡面板 垂直方向
        tabbedPane = new JTabbedPane(SwingConstants.LEFT);
        ToolWindowSurfaceStyle.applyTabbedPaneCard(tabbedPane);
        String prescriptTabTitle = I18nUtil.getMessage(MessageKeys.SCRIPT_PRESCRIPT_TAB_TITLE);
        String postscriptTabTitle = I18nUtil.getMessage(MessageKeys.SCRIPT_POSTSCRIPT_TAB_TITLE);

        // 创建带指示器的 Tab 组件
        preScriptTab = new IndicatorTabComponent(prescriptTabTitle);
        postScriptTab = new IndicatorTabComponent(postscriptTabTitle);

        // Pre-script 标签带指示器
        tabbedPane.addTab(prescriptTabTitle, prescriptSearchableArea);
        tabbedPane.setTabComponentAt(0, preScriptTab);

        // Post-script 标签带指示器
        tabbedPane.addTab(postscriptTabTitle, postscriptSearchableArea);
        tabbedPane.setTabComponentAt(1, postScriptTab);

        // 添加文档监听器以更新指示器
        addIndicatorListeners();

        add(tabbedPane, BorderLayout.CENTER);

        // 右下角添加帮助按钮和 Snippets 按钮
        snippetBtn = new SnippetButton();

        HelpButton helpBtn = new HelpButton();
        helpBtn.addActionListener(e -> showHelpDialog());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        ToolWindowSurfaceStyle.applyCard(btnPanel);
        btnPanel.add(helpBtn);
        btnPanel.add(snippetBtn);
        add(btnPanel, BorderLayout.SOUTH);

        // 标签页切换监听器 - 只在脚本标签页显示 Snippets 按钮
        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            snippetBtn.setVisible(selectedIndex == 0 || selectedIndex == 1);
        });

        // Snippets 按钮点击事件
        snippetBtn.addActionListener(e -> openSnippetDialog());
    }

    /**
     * 显示帮助对话框
     */
    private void showHelpDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                I18nUtil.getMessage(MessageKeys.SCRIPT_HELP_TITLE), true);
        ToolWindowSurfaceStyle.applyDialogWindowChrome(dialog);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel helpPanel = createHelpPanel();
        dialog.add(helpPanel);

        dialog.setSize(600, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * 创建帮助面板
     */
    private JPanel createHelpPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        ToolWindowSurfaceStyle.applyDialogSurface(panel);
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));

        // 主内容面板
        JPanel contentPanel = new JPanel();
        ToolWindowSurfaceStyle.applyDialogSurface(contentPanel);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // 标题
        JLabel titleLabel = new JLabel(I18nUtil.getMessage(MessageKeys.SCRIPT_HELP_TITLE));
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, +1));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(10));

        // 简介
        JTextArea introArea = createHelpTextArea(I18nUtil.getMessage(MessageKeys.SCRIPT_HELP_INTRO));
        introArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(introArea);
        contentPanel.add(Box.createVerticalStrut(15));

        // 主要功能
        JTextArea featuresArea = createHelpTextArea(I18nUtil.getMessage(MessageKeys.SCRIPT_HELP_FEATURES));
        featuresArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(featuresArea);
        contentPanel.add(Box.createVerticalStrut(15));

        // 快捷键
        JTextArea shortcutsArea = createHelpTextArea(I18nUtil.getMessage(MessageKeys.SCRIPT_HELP_SHORTCUTS));
        shortcutsArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(shortcutsArea);
        contentPanel.add(Box.createVerticalStrut(15));

        // 常用示例
        JTextArea examplesArea = createHelpTextArea(I18nUtil.getMessage(MessageKeys.SCRIPT_HELP_EXAMPLES));
        examplesArea.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        examplesArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(examplesArea);

        // 将内容面板放入滚动面板
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        ToolWindowSurfaceStyle.applyDialogScrollPane(scrollPane);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建帮助文本区域
     */
    private JTextArea createHelpTextArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        ToolWindowSurfaceStyle.applyTextComponentDialogSurface(area);
        area.setBorder(null);
        return area;
    }


    /**
     * 配置编辑器的通用设置，返回安装好的 AutoCompletion 实例
     */
    private AutoCompletion configureEditor(RSyntaxTextArea area) {
        // 设置语法高亮
        area.setSyntaxEditingStyle(POSTMAN_JS_SYNTAX);

        // 代码编辑功能
        area.setCodeFoldingEnabled(true);           // 代码折叠
        area.setAutoIndentEnabled(true);            // 自动缩进
        area.setBracketMatchingEnabled(true);       // 括号匹配
        area.setMarkOccurrences(false);             // 脚本编辑器不标记相同标识符

        // 显示设置
        area.setAntiAliasingEnabled(true);          // 抗锯齿，文字更清晰
        area.setPaintTabLines(true);                // 显示缩进参考线
        area.setTabSize(4);                         // Tab = 4 个空格

        // 统一加载主题、编辑器字体和缺字回退绘制
        EditorThemeUtil.loadTheme(area);

        return addAutoCompletion(area);
    }

    /**
     * 打开代码片段对话框
     */
    private void openSnippetDialog() {
        int tab = tabbedPane.getSelectedIndex();
        if (tab != 0 && tab != 1) {
            return;  // 不在脚本标签页，不执行
        }

        SnippetDialog dialog = new SnippetDialog();
        dialog.setVisible(true);
        Snippet selected = dialog.getSelectedSnippet();

        if (selected != null) {
            RSyntaxTextArea targetArea = (tab == 0) ? prescriptArea : postscriptArea;

            // 智能插入：如果有选中文本，替换它；否则在光标位置插入
            String selectedText = targetArea.getSelectedText();
            String codeToInsert = selected.code;

            // 如果光标不在行首且前面有内容，添加换行
            int caretPosition = targetArea.getCaretPosition();
            try {
                int lineStart = targetArea.getLineStartOffsetOfCurrentLine();
                if (caretPosition > lineStart && selectedText == null) {
                    String lineText = targetArea.getText(lineStart, caretPosition - lineStart);
                    if (!lineText.trim().isEmpty()) {
                        codeToInsert = "\n" + codeToInsert;
                    }
                }
            } catch (Exception ex) {
                log.error("Error calculating insert position", ex);
            }

            targetArea.replaceSelection(codeToInsert);
            targetArea.requestFocusInWindow();
        }
    }

    /**
     * 程序性地设置编辑器内容，临时禁用 AutoCompletion 自动激活，
     * 避免 setText() 触发补全弹窗（AutoCompletion 无法区分用户输入与程序赋值）。
     */
    private void setEditorText(RSyntaxTextArea area, AutoCompletion ac, String text) {
        if (ac != null) ac.setAutoActivationEnabled(false);
        area.setText(text == null ? "" : text);
        area.setCaretPosition(0);
        // 切换请求时的 setText 是数据加载，不是用户编辑；否则第一次 Ctrl/Cmd+Z 会撤销加载动作并清空脚本。
        area.discardAllEdits();
        if (ac != null) ac.setAutoActivationEnabled(true);
    }

    public void setPrescript(String text) {
        setEditorText(prescriptArea, prescriptAc, text);
        updateIndicator(preScriptTab, text);
        if (text != null && !text.trim().isEmpty()) {
            tabbedPane.setSelectedIndex(0);
        }
    }

    public void setPostscript(String text) {
        setEditorText(postscriptArea, postscriptAc, text);
        updateIndicator(postScriptTab, text);
        if (text != null && !text.trim().isEmpty() &&
                (prescriptArea.getText() == null || prescriptArea.getText().trim().isEmpty())) {
            tabbedPane.setSelectedIndex(1);
        }
    }

    public String getPrescript() {
        return prescriptArea.getText();
    }

    public String getPostscript() {
        return postscriptArea.getText();
    }

    public void setEditable(boolean editable) {
        prescriptArea.setEditable(editable);
        postscriptArea.setEditable(editable);
        snippetBtn.setVisible(editable);
        snippetBtn.setEnabled(editable);
    }

    /**
     * 添加脏监听，内容变更时回调
     */
    public void addDirtyListeners(Runnable dirtyCallback) {
        DocumentListener listener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                dirtyCallback.run();
            }

            public void removeUpdate(DocumentEvent e) {
                dirtyCallback.run();
            }

            public void changedUpdate(DocumentEvent e) {
                dirtyCallback.run();
            }
        };
        prescriptArea.getDocument().addDocumentListener(listener);
        postscriptArea.getDocument().addDocumentListener(listener);
    }


    /**
     * 添加文档监听器以更新指示器状态
     */
    private void addIndicatorListeners() {
        // Pre-script 监听器
        prescriptArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateIndicator(preScriptTab, prescriptArea.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateIndicator(preScriptTab, prescriptArea.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateIndicator(preScriptTab, prescriptArea.getText());
            }
        });

        // Post-script 监听器
        postscriptArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateIndicator(postScriptTab, postscriptArea.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateIndicator(postScriptTab, postscriptArea.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateIndicator(postScriptTab, postscriptArea.getText());
            }
        });
    }

    /**
     * 根据文本内容更新指示器状态
     *
     * @param tabComponent Tab组件
     * @param text         文本内容
     */
    private void updateIndicator(IndicatorTabComponent tabComponent, String text) {
        boolean hasContent = text != null && !text.trim().isEmpty();
        tabComponent.setShowIndicator(hasContent);
    }

    /**
     * 为 RSyntaxTextArea 安装自动补全并返回 AutoCompletion 实例
     */
    private AutoCompletion addAutoCompletion(RSyntaxTextArea area) {
        var provider = ScriptSnippetManager.createCompletionProvider();

        AutoCompletion ac = new AutoCompletion(provider);
        ac.setAutoCompleteEnabled(true);
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(200);
        ac.setAutoCompleteSingleChoices(false);
        ac.setShowDescWindow(true);
        ac.setParameterAssistanceEnabled(false);
        ac.install(area);
        return ac;
    }
}
