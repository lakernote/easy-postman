package com.laker.postman.panel.collections.right.request.sub;

import cn.hutool.core.util.XmlUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.components.FlatTextField;
import com.laker.postman.common.component.SearchTextField;
import com.laker.postman.model.HttpResponse;
import lombok.Getter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.Map;

/**
 * 响应体面板，展示响应体内容和格式化按钮
 */
public class ResponseBodyPanel extends JPanel {
    @Getter
    private final RSyntaxTextArea responseBodyPane;
    private final JButton downloadButton;
    private String currentFilePath;
    private String fileName = "downloaded_file"; // 默认下载文件名
    private final FlatTextField searchField;
    private Map<String, List<String>> lastHeaders;
    private final JComboBox<String> syntaxComboBox;
    private final JButton formatButton;
    private final JButton prevButton;
    private final JButton nextButton;
    RTextScrollPane scrollPane;

    public ResponseBodyPanel() {
        setLayout(new BorderLayout());
        responseBodyPane = new RSyntaxTextArea();
        responseBodyPane.setEditable(false);
        responseBodyPane.setCodeFoldingEnabled(true);
        responseBodyPane.setLineWrap(true);
        responseBodyPane.setHighlightCurrentLine(false); // 关闭选中行高亮
        scrollPane = new RTextScrollPane(responseBodyPane);
        add(scrollPane, BorderLayout.CENTER);
        // 顶部工具栏优化：左侧为搜索，右侧为格式化、语法下拉、下载
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setLayout(new BorderLayout());
        // 左侧操作区
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        syntaxComboBox = new JComboBox<>(new String[]{
                "Auto Detect", "JSON", "XML", "HTML", "JavaScript", "CSS", "Plain Text"
        });
        leftPanel.add(syntaxComboBox);
        toolBar.add(leftPanel, BorderLayout.WEST);
        // 右侧搜索区
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        searchField = new SearchTextField();
        prevButton = new JButton(new FlatSVGIcon("icons/arrow-up.svg", 16, 16));
        prevButton.setToolTipText("Previous");
        nextButton = new JButton(new FlatSVGIcon("icons/arrow-down.svg", 16, 16));
        nextButton.setToolTipText("Next");
        rightPanel.add(searchField);
        rightPanel.add(prevButton);
        rightPanel.add(nextButton);
        formatButton = new JButton(new FlatSVGIcon("icons/format.svg", 16, 16));
        formatButton.setToolTipText("Format");
        rightPanel.add(formatButton);
        downloadButton = new JButton(new FlatSVGIcon("icons/download.svg", 16, 16));
        downloadButton.setToolTipText("Download");
        downloadButton.setVisible(false);
        rightPanel.add(downloadButton);
        toolBar.add(rightPanel, BorderLayout.EAST);
        add(toolBar, BorderLayout.NORTH);
        downloadButton.addActionListener(e -> saveFile());
        formatButton.addActionListener(e -> formatContent());
        searchField.addActionListener(e -> search(true));
        prevButton.addActionListener(e -> search(false));
        nextButton.addActionListener(e -> search(true));
        syntaxComboBox.addActionListener(e -> onSyntaxComboChanged());
    }

    private void onSyntaxComboChanged() {
        int idx = syntaxComboBox.getSelectedIndex();
        if (idx == 0) { // 自动识别
            String syntax = detectSyntax(responseBodyPane.getText(), getCurrentContentTypeFromHeaders());
            responseBodyPane.setSyntaxEditingStyle(syntax);
        } else if (idx == 1) {
            responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        } else if (idx == 2) {
            responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
        } else if (idx == 3) {
            responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML);
        } else if (idx == 4) {
            responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        } else if (idx == 5) {
            responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSS);
        } else {
            responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        }
    }

    private void search(boolean forward) {
        String keyword = searchField.getText();
        if (keyword == null || keyword.isEmpty()) return;
        String text = responseBodyPane.getText();
        if (text == null || text.isEmpty()) return;
        int caret = responseBodyPane.getCaretPosition();
        int pos = -1;
        if (forward) {
            // 向后查找
            int start = caret;
            if (responseBodyPane.getSelectedText() != null && responseBodyPane.getSelectedText().equals(keyword)) {
                start = caret + 1;
            }
            pos = text.indexOf(keyword, start);
            if (pos == -1) {
                // 循环查找
                pos = text.indexOf(keyword);
            }
        } else {
            // 向前查找
            int start = caret - 1;
            if (responseBodyPane.getSelectedText() != null && responseBodyPane.getSelectedText().equals(keyword)) {
                start = caret - keyword.length() - 1;
            }
            if (start < 0) start = text.length() - 1;
            pos = text.lastIndexOf(keyword, start);
            if (pos == -1) {
                pos = text.lastIndexOf(keyword);
            }
        }
        if (pos != -1) {
            responseBodyPane.setCaretPosition(pos);
            responseBodyPane.select(pos, pos + keyword.length());
            responseBodyPane.requestFocusInWindow();
        }
    }

    private void saveFile() {
        if (currentFilePath == null) return;
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("保存文件");
        fileChooser.setSelectedFile(new File(fileName));
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File destFile = fileChooser.getSelectedFile();
            try (InputStream in = new FileInputStream(currentFilePath);
                 OutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                JOptionPane.showMessageDialog(this, "文件已保存到: " + destFile.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "保存文件失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void formatContent() {
        String text = responseBodyPane.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        String contentType = getCurrentContentTypeFromHeaders();
        try {
            if (contentType.contains("json")) {
                JSON json = JSONUtil.parse(text); // 确保是有效的 JSON
                String pretty = JSONUtil.toJsonPrettyStr(json);
                responseBodyPane.setText(pretty);
            } else if (contentType.contains("xml")) {
                String pretty = XmlUtil.format(text);
                responseBodyPane.setText(pretty);
            } else {
                // 其他类型不处理
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "格式化失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getCurrentContentTypeFromHeaders() {
        if (lastHeaders != null) {
            for (String key : lastHeaders.keySet()) {
                if (key != null && key.equalsIgnoreCase("Content-Type")) {
                    java.util.List<String> values = lastHeaders.get(key);
                    if (values != null && !values.isEmpty()) {
                        return values.get(0);
                    }
                }
            }
        }
        return "";
    }

    /**
     * 追加响应体内容，用于WebSocket、SSE等持续接收消息的场景
     *
     * @param message 要追加的消息
     */
    public void appendBodyText(String message) {
        // 获取当前文本内容
        String currentText = responseBodyPane.getText();
        // 追加新消息
        String newText = currentText + (currentText.isEmpty() ? "" : "\n") + message;
        // 更新响应体内容
        responseBodyPane.setText(newText);
        // 滚动到最新内容
        responseBodyPane.setCaretPosition(responseBodyPane.getDocument().getLength());
        responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
    }

    public void setBodyText(HttpResponse resp) {
        if (resp == null) {
            responseBodyPane.setText("");
            currentFilePath = null;
            fileName = "downloaded_file";
            lastHeaders = null;
            downloadButton.setVisible(false);
            responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            responseBodyPane.setCaretPosition(0);
            searchField.setText("");
            syntaxComboBox.setSelectedIndex(0);
            return;
        }
        this.currentFilePath = resp.filePath;
        this.fileName = resp.fileName;
        this.lastHeaders = resp.headers;
        String filePath = resp.filePath;
        String text = resp.body;
        String contentType = "";
        if (resp.headers != null) {
            for (String key : resp.headers.keySet()) {
                if (key != null && key.equalsIgnoreCase("Content-Type")) {
                    java.util.List<String> values = resp.headers.get(key);
                    if (values != null && !values.isEmpty()) {
                        contentType = values.get(0);
                        break;
                    }
                }
            }
        }
        responseBodyPane.setText(text);
        // 动态选择高亮类型，参考 postman
        String syntax = detectSyntax(text, contentType);
        // 自动匹配下拉框选项
        int syntaxIndex = 0;
        if (SyntaxConstants.SYNTAX_STYLE_JSON.equals(syntax)) {
            syntaxIndex = 1;
        } else if (SyntaxConstants.SYNTAX_STYLE_XML.equals(syntax)) {
            syntaxIndex = 2;
        } else if (SyntaxConstants.SYNTAX_STYLE_HTML.equals(syntax)) {
            syntaxIndex = 3;
        } else if (SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT.equals(syntax)) {
            syntaxIndex = 4;
        } else if (SyntaxConstants.SYNTAX_STYLE_CSS.equals(syntax)) {
            syntaxIndex = 5;
        } else if (SyntaxConstants.SYNTAX_STYLE_NONE.equals(syntax)) {
            syntaxIndex = 6;
        }
        syntaxComboBox.setSelectedIndex(syntaxIndex);
        responseBodyPane.setSyntaxEditingStyle(syntax);
        if (filePath != null && !filePath.isEmpty()) {
            downloadButton.setVisible(true);
        } else {
            downloadButton.setVisible(false);
        }
        responseBodyPane.setCaretPosition(0);
    }

    // 动态检测内容类型
    private String detectSyntax(String text, String contentType) {
        if (contentType != null) contentType = contentType.toLowerCase();
        if (contentType != null) {
            if (contentType.contains("json")) return SyntaxConstants.SYNTAX_STYLE_JSON;
            if (contentType.contains("xml")) return SyntaxConstants.SYNTAX_STYLE_XML;
            if (contentType.contains("html")) return SyntaxConstants.SYNTAX_STYLE_HTML;
            if (contentType.contains("javascript")) return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            if (contentType.contains("css")) return SyntaxConstants.SYNTAX_STYLE_CSS;
            if (contentType.contains("text")) return SyntaxConstants.SYNTAX_STYLE_NONE;
        }
        // 内容自动识别
        if (text == null || text.isEmpty()) return SyntaxConstants.SYNTAX_STYLE_NONE;
        String t = text.trim();
        if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
            return SyntaxConstants.SYNTAX_STYLE_JSON;
        }
        if (t.startsWith("<") && t.endsWith(">")) {
            if (t.toLowerCase().contains("<html")) return SyntaxConstants.SYNTAX_STYLE_HTML;
            if (t.toLowerCase().contains("<?xml")) return SyntaxConstants.SYNTAX_STYLE_XML;
        }
        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        responseBodyPane.setEnabled(enabled);
        syntaxComboBox.setEnabled(enabled);
        searchField.setEnabled(enabled);
        downloadButton.setEnabled(enabled);
        scrollPane.setEnabled(enabled);

        if (formatButton != null) formatButton.setEnabled(enabled);
        if (prevButton != null) prevButton.setEnabled(enabled);
        if (nextButton != null) nextButton.setEnabled(enabled);
    }
}

