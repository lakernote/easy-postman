package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.component.notification.NotificationCenter;

import cn.hutool.core.util.XmlUtil;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemFileChooser;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.EasyComboBox;
import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SearchableTextArea;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.*;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.*;
import lombok.Getter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * 响应体面板，展示 HTTP 响应体内容
 * <p>
 * 主要功能：
 * - 语法高亮显示（JSON、XML、HTML、JavaScript、CSS 等）
 * - 自动/手动格式化
 * - 文本搜索
 * - 自动换行控制
 * - 下载响应内容
 * - 大文件优化处理
 * </p>
 */
public class ResponseBodyPanel extends JPanel {
    @Getter
    private final RSyntaxTextArea responseBodyPane;
    private final DownloadButton downloadButton;
    @Getter
    private SaveResponseButton saveResponseButton; // 保存响应按钮（仅HTTP请求）
    private String currentFilePath;
    private String fileName = DEFAULT_FILE_NAME; // 默认下载文件名
    private final SearchButton searchButton; // 搜索按钮
    private Map<String, List<String>> lastHeaders;
    private HttpResponse currentResponse;
    private final EasyComboBox<String> syntaxComboBox;
    private final FormatButton formatButton;
    private final JToggleButton autoSortJsonKeysButton;
    private final CopyButton copyButton;
    private final GenerateModelButton generateModelButton;
    private final WrapToggleButton wrapButton;
    private final SearchableTextArea searchableTextArea; // 带搜索功能的文本编辑器

    // 常量定义
    private static final int LARGE_RESPONSE_THRESHOLD = 500 * 1024; // 500KB threshold
    private static final int BUFFER_SIZE = 8192;
    private static final String DEFAULT_FILE_NAME = "downloaded_file";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CARD_TEXT = "TEXT";
    private static final String CARD_IMAGE = "IMAGE";
    private static final String CARD_MEDIA = "MEDIA";

    // 图片预览组件
    private final JLabel imagePreviewLabel;
    private final MediaResponsePanel mediaResponsePanel;


    private final JLabel sizeWarningLabel;

    public ResponseBodyPanel(boolean enableSaveButton) {
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(this);
        // 设置边距
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        responseBodyPane = new FallbackAwareRSyntaxTextArea();
        responseBodyPane.setEditable(false);
        responseBodyPane.setCodeFoldingEnabled(true);
        responseBodyPane.setLineWrap(false); // 禁用自动换行以提升大文本性能
        responseBodyPane.setHighlightCurrentLine(false); // 关闭选中行高亮
        // 关闭括号匹配的小浮层提示，避免响应体中长 JSON 滚动查看时遮挡内容
        responseBodyPane.setShowMatchedBracketPopup(false);
        // 统一加载主题、编辑器字体和缺字回退绘制
        EditorThemeUtil.loadTheme(responseBodyPane);
        EditorThemeUtil.installViewportClippedTokenPainter(responseBodyPane);

        // 使用 SearchableTextArea 包装，禁用替换功能（仅搜索）
        searchableTextArea = new SearchableTextArea(responseBodyPane, false);

        // 图片预览组件
        imagePreviewLabel = new JLabel();
        imagePreviewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imagePreviewLabel.setVerticalAlignment(SwingConstants.CENTER);
        JScrollPane imageScrollPane = new JScrollPane(imagePreviewLabel);
        ToolWindowSurfaceStyle.applyScrollPaneCard(imageScrollPane);
        imageScrollPane.setBorder(BorderFactory.createEmptyBorder());

        mediaResponsePanel = new MediaResponsePanel();

        // 使用 CardLayout 在文本、图片和媒体视图之间切换
        JPanel centerPanel = new JPanel(new CardLayout());
        ToolWindowSurfaceStyle.applyCard(centerPanel);
        centerPanel.add(searchableTextArea, CARD_TEXT);
        centerPanel.add(imageScrollPane, CARD_IMAGE);
        centerPanel.add(mediaResponsePanel, CARD_MEDIA);
        add(centerPanel, BorderLayout.CENTER);

        JPanel toolBarPanel = new JPanel();
        ToolWindowSurfaceStyle.applyCard(toolBarPanel);
        toolBarPanel.setLayout(new BoxLayout(toolBarPanel, BoxLayout.X_AXIS));
        toolBarPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        // 语法选择下拉框
        syntaxComboBox = new EasyComboBox<>(SyntaxType.getDisplayNames(), EasyComboBox.WidthMode.DYNAMIC);
        syntaxComboBox.setFocusable(false);
        toolBarPanel.add(syntaxComboBox);
        toolBarPanel.add(Box.createHorizontalStrut(4)); // 间隔

        // 添加大小提示标签
        sizeWarningLabel = new JLabel();
        sizeWarningLabel.setForeground(ModernColors.getWarningDark());
        sizeWarningLabel.setVisible(false);
        toolBarPanel.add(sizeWarningLabel);

        // 弹性空间，将右侧控件推到右边
        toolBarPanel.add(Box.createHorizontalGlue());

        // 搜索按钮
        searchButton = new SearchButton();
        searchButton.addActionListener(e -> {
            responseBodyPane.requestFocusInWindow();
            searchableTextArea.showSearch();
        });
        toolBarPanel.add(searchButton);
        toolBarPanel.add(Box.createHorizontalStrut(1)); // 间隔

        // Key 排序切换按钮，沿用自动换行工具按钮的选中态视觉
        autoSortJsonKeysButton = new JToggleButton();
        autoSortJsonKeysButton.setToolTipText(
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_AUTO_SORT_JSON_KEYS_TOOLTIP));
        autoSortJsonKeysButton.getAccessibleContext().setAccessibleName(
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_AUTO_SORT_JSON_KEYS));
        autoSortJsonKeysButton.setFocusable(false);
        autoSortJsonKeysButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        autoSortJsonKeysButton.putClientProperty(
                FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        autoSortJsonKeysButton.setSelected(false);
        updateAutoSortJsonKeysIcon(autoSortJsonKeysButton.isSelected());
        autoSortJsonKeysButton.addItemListener(e -> updateAutoSortJsonKeysIcon(autoSortJsonKeysButton.isSelected()));
        toolBarPanel.add(autoSortJsonKeysButton);
        toolBarPanel.add(Box.createHorizontalStrut(1));

        // 换行按钮
        wrapButton = new WrapToggleButton();
        toolBarPanel.add(wrapButton);
        toolBarPanel.add(Box.createHorizontalStrut(1));

        // 格式化按钮
        formatButton = new FormatButton();
        toolBarPanel.add(formatButton);
        toolBarPanel.add(Box.createHorizontalStrut(1));

        // 复制按钮
        copyButton = new CopyButton();
        toolBarPanel.add(copyButton);
        toolBarPanel.add(Box.createHorizontalStrut(1));

        generateModelButton = new GenerateModelButton(I18nUtil.getMessage(MessageKeys.JSON_MODEL_GENERATOR_TITLE));
        generateModelButton.addActionListener(e -> JsonModelGeneratorDialog.showDialog(this, responseBodyPane.getText(), "Response"));
        toolBarPanel.add(generateModelButton);
        toolBarPanel.add(Box.createHorizontalStrut(1));

        // 下载按钮
        downloadButton = new DownloadButton();
        toolBarPanel.add(downloadButton);

        // 只有 HTTP 请求才显示保存响应按钮
        if (enableSaveButton) {
            toolBarPanel.add(Box.createHorizontalStrut(1));
            saveResponseButton = new SaveResponseButton();
            toolBarPanel.add(saveResponseButton);
        }

        add(toolBarPanel, BorderLayout.NORTH);

        downloadButton.addActionListener(e -> saveFile());
        formatButton.addActionListener(e -> formatContent());
        copyButton.addActionListener(e -> copyToClipboard());
        wrapButton.addActionListener(e -> toggleLineWrap());
        syntaxComboBox.addActionListener(e -> onSyntaxComboChanged());
        autoSortJsonKeysButton.addActionListener(e -> {
            if (currentResponse != null) {
                setBodyText(currentResponse);
            }
        });
        updateGenerateModelButtonState();
    }

    /**
     * 语法类型下拉框改变事件处理
     * 根据用户选择的语法类型更新编辑器的语法高亮
     */
    private void onSyntaxComboChanged() {
        int idx = syntaxComboBox.getSelectedIndex();
        SyntaxType syntaxType = SyntaxType.getByIndex(idx);

        String syntax;
        if (syntaxType == SyntaxType.AUTO_DETECT) {
            // 自动检测语法类型
            syntax = detectSyntax(responseBodyPane.getText(), getCurrentContentTypeFromHeaders());
        } else {
            // 使用用户选择的语法类型
            syntax = syntaxType.getSyntaxStyle();
        }

        responseBodyPane.setSyntaxEditingStyle(syntax);
    }

    /**
     * 切换自动换行状态
     */
    private void toggleLineWrap() {
        boolean isWrapEnabled = wrapButton.isSelected();
        responseBodyPane.setLineWrap(isWrapEnabled);
    }


    /**
     * 保存文件
     * <p>
     * 支持两种保存模式：
     * 1. 如果有临时文件路径（大文件或二进制文件），从临时文件复制
     * 2. 否则直接保存编辑器中的文本内容
     * </p>
     */
    private void saveFile() {
        SystemFileChooser fileChooser = FileChooserUtil.createSaveFileChooser(
                "response.body.save",
                "Save File");

        // 智能设置默认文件名和扩展名
        String defaultFileName = generateFileName();
        fileChooser.setSelectedFile(new File(defaultFileName));

        int userSelection = fileChooser.showSaveDialog(UiSingletonFactory.getInstance(MainFrame.class));
        if (userSelection == SystemFileChooser.APPROVE_OPTION) {
            File destFile = fileChooser.getSelectedFile();
            try {
                if (currentFilePath != null && !currentFilePath.isEmpty()) {
                    // 如果是文件下载（如二进制文件），从临时文件复制
                    try (InputStream in = new FileInputStream(currentFilePath);
                         OutputStream out = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                } else {
                    // 如果是文本响应，直接保存文本内容
                    String content = responseBodyPane.getText();
                    if (content != null && !content.isEmpty()) {
                        try (OutputStreamWriter writer = new OutputStreamWriter(
                                new FileOutputStream(destFile), StandardCharsets.UTF_8)) {
                            writer.write(content);
                        }
                    }
                }
                NotificationCenter.showInfo("File saved successfully: " + destFile.getAbsolutePath());
            } catch (Exception ex) {
                NotificationCenter.showError("Save File Error: " + ex.getMessage());
            }
        }
    }

    /**
     * 智能生成文件名，根据内容类型添加合适的扩展名
     */
    private String generateFileName() {
        // 如果有明确的文件名（来自 Content-Disposition），使用它
        if (fileName != null && !fileName.equals(DEFAULT_FILE_NAME) && !fileName.isEmpty()) {
            return fileName;
        }

        // 否则根据内容类型生成文件名
        String contentType = getCurrentContentTypeFromHeaders();
        String extension = FileExtensionUtil.guessExtension(contentType);

        // 使用统一的智能文件名生成逻辑
        if (extension == null) {
            extension = ".txt";
        }
        return FileExtensionUtil.generateSmartFileName(extension);
    }


    /**
     * 格式化内容
     * 根据 Content-Type 对 JSON 或 XML 进行格式化美化
     */
    private void formatContent() {
        String text = responseBodyPane.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        String contentType = getCurrentContentTypeFromHeaders();

        try {
            String formatted = null;
            if (contentType.contains("json") || JsonUtil.isTypeJSON(text)) {
                formatted = formatJson(text, autoSortJsonKeysButton.isSelected());
            } else if (contentType.contains("xml")) {
                formatted = XmlUtil.format(text);
            }

            if (formatted != null) {
                responseBodyPane.setText(formatted);
                responseBodyPane.setCaretPosition(0);
            }
        } catch (Exception ex) {
            NotificationCenter.showError("Format Error: " + ex.getMessage());
        }
    }

    /**
     * 复制内容到剪贴板
     * 将响应体内容复制到系统剪贴板
     */
    private void copyToClipboard() {
        String text = responseBodyPane.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        copyTextToClipboard(text);
    }

    private void copyTextToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            NotificationCenter.showInfo("Content copied to clipboard");
        } catch (Exception ex) {
            NotificationCenter.showError("Copy Error: " + ex.getMessage());
        }
    }

    /**
     * 从响应头中获取 Content-Type
     *
     * @return Content-Type 的值，如果不存在则返回空字符串
     */
    private String getCurrentContentTypeFromHeaders() {
        if (lastHeaders != null) {
            for (Map.Entry<String, List<String>> entry : lastHeaders.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(CONTENT_TYPE_HEADER)) {
                    List<String> values = entry.getValue();
                    if (values != null && !values.isEmpty()) {
                        return values.get(0);
                    }
                }
            }
        }
        return "";
    }

    /**
     * 设置响应体内容
     * <p>
     * 该方法会：
     * 1. 自动检测语法类型并设置高亮
     * 2. 显示文件大小警告（如果超过阈值）
     * 3. 根据设置决定是否自动格式化
     * </p>
     *
     * @param resp HTTP 响应对象
     */
    public void setBodyText(HttpResponse resp) {
        if (resp == null) {
            clearResponseBody();
            return;
        }

        this.currentFilePath = resp.filePath;
        this.currentResponse = resp;
        this.fileName = resp.fileName;
        this.lastHeaders = resp.headers;
        String contentType = extractContentType(resp.headers);

        String mainContentType = FileExtensionUtil.extractMainType(contentType);
        boolean isAudio = "audio".equalsIgnoreCase(mainContentType);
        boolean isVideo = "video".equalsIgnoreCase(mainContentType);

        // Java Sound 原生支持的音频内置播放；其他音频编码和视频交给系统播放器。
        if ((isAudio || isVideo) && resp.filePath != null && !resp.filePath.isEmpty()) {
            generateModelButton.setEnabled(false);
            imagePreviewLabel.setIcon(null);
            imagePreviewLabel.setText(null);
            sizeWarningLabel.setVisible(false);
            if (isAudio) {
                mediaResponsePanel.setAudioSource(resp.filePath, resp.fileName, resp.bodySize);
            } else {
                mediaResponsePanel.setVideoSource(resp.filePath, resp.fileName, resp.bodySize);
            }
            switchCard(CARD_MEDIA);
            return;
        }

        mediaResponsePanel.clear();

        // 图片预览
        if (resp.isImage && resp.filePath != null && !resp.filePath.isEmpty()) {
            generateModelButton.setEnabled(false);
            showImagePreview(resp.filePath, resp.bodySize);
            return;
        }

        // 切换回文本视图
        switchCard(CARD_TEXT);
        String text = resp.body;

        int textSize = text != null ? text.getBytes().length : 0;
        boolean isLargeResponse = textSize > LARGE_RESPONSE_THRESHOLD;

        // 显示大小信息
        updateSizeWarning(textSize, isLargeResponse);

        // 动态选择高亮类型
        String syntax = detectSyntax(text, contentType);

        // 自动匹配下拉框选项
        int syntaxIndex = SyntaxType.getBySyntaxStyle(syntax).getIndex();
        syntaxComboBox.setSelectedIndex(syntaxIndex);

        // 设置语法高亮和文本
        responseBodyPane.setSyntaxEditingStyle(syntax);
        responseBodyPane.setText(text);
        updateGenerateModelButtonState();

        // 根据设置和大小决定是否自动格式化
        boolean shouldAutoFormat = SettingManager.isAutoFormatResponse();
        boolean shouldAutoSortKeys = autoSortJsonKeysButton.isSelected();
        if (shouldAutoFormat || shouldAutoSortKeys) {
            if (textSize <= LARGE_RESPONSE_THRESHOLD) {
                autoFormatIfPossible(text, contentType, shouldAutoFormat, shouldAutoSortKeys);
            } else {
                sizeWarningLabel.setText(sizeWarningLabel.getText() + "  "
                        + I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_SKIP_AUTO_PROCESSING_LARGE));
            }
        }

        responseBodyPane.setCaretPosition(0);
    }

    /**
     * 动态检测语法类型
     * <p>
     * 检测策略：
     * 1. 优先根据 Content-Type 响应头判断
     * 2. 其次根据内容特征判断（如 JSON 的 {} 或 []，XML 的 < >）
     * </p>
     *
     * @param text        文本内容
     * @param contentType Content-Type 响应头
     * @return 语法类型常量（来自 SyntaxConstants）
     */
    private String detectSyntax(String text, String contentType) {
        if (contentType != null) contentType = contentType.toLowerCase();
        if (JsonUtil.isTypeJSON(text)) {
            return SyntaxConstants.SYNTAX_STYLE_JSON;
        }
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

    /**
     * 自动格式化内容（如果可能）
     * <p>
     * 对小于等于 500KB 的响应按用户设置处理 JSON/XML；key 排序只作用于 JSON。
     * </p>
     *
     * @param text        文本内容
     * @param contentType Content-Type 响应头
     */
    private void autoFormatIfPossible(String text, String contentType,
                                      boolean autoFormat, boolean sortJsonKeys) {
        if (text == null || text.isEmpty()) {
            return;
        }

        try {
            boolean isJson = (contentType != null && contentType.toLowerCase().contains("json"))
                    || JsonUtil.isTypeJSON(text);
            if (isJson) {
                responseBodyPane.setText(formatJson(text, sortJsonKeys));
            } else if (autoFormat && contentType != null && contentType.toLowerCase().contains("xml")) {
                responseBodyPane.setText(XmlUtil.format(text));
            }
        } catch (Exception ex) {
            // 格式化或排序失败时保留原始响应内容。
        }
    }

    private String formatJson(String json, boolean sortKeys) throws Exception {
        if (!sortKeys) {
            return JsonUtil.toJsonPrettyStr(json);
        }
        try {
            Object parsed = cn.hutool.json.JSONUtil.parse(json);
            return JsonUtil.toJsonPrettyStr(sortJsonValue(parsed));
        } catch (Exception parseFailure) {
            // JsonUtil accepts commented JSON; keep that existing behavior if Hutool cannot parse it.
            return JsonUtil.toJsonPrettyStr(json);
        }
    }

    private void updateAutoSortJsonKeysIcon(boolean selected) {
        String iconPath = "icons/sort-keys.svg";
        if (selected) {
            autoSortJsonKeysButton.setIcon(IconUtil.createOnPrimary(
                    iconPath, IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        } else {
            autoSortJsonKeysButton.setIcon(IconUtil.createThemed(
                    iconPath, IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        }
    }

    private Object sortJsonValue(Object value) {
        if (value instanceof cn.hutool.json.JSONObject object) {
            cn.hutool.json.JSONObject sorted = new cn.hutool.json.JSONObject(true);
            object.keySet().stream().sorted().forEach(key -> sorted.set(key, sortJsonValue(object.get(key))));
            return sorted;
        }
        if (value instanceof cn.hutool.json.JSONArray array) {
            cn.hutool.json.JSONArray sorted = new cn.hutool.json.JSONArray();
            for (Object item : array) {
                sorted.add(sortJsonValue(item));
            }
            return sorted;
        }
        return value;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        responseBodyPane.setEnabled(enabled);
        syntaxComboBox.setEnabled(enabled);
        searchButton.setEnabled(enabled);
        downloadButton.setEnabled(enabled);
        searchableTextArea.setEnabled(enabled);

        if (formatButton != null) formatButton.setEnabled(enabled);
        if (autoSortJsonKeysButton != null) autoSortJsonKeysButton.setEnabled(enabled);
        if (copyButton != null) copyButton.setEnabled(enabled);
        updateGenerateModelButtonState();
        if (wrapButton != null) wrapButton.setEnabled(enabled);
        if (saveResponseButton != null) saveResponseButton.setEnabled(enabled);
        mediaResponsePanel.setEnabled(enabled);
    }

    private void updateGenerateModelButtonState() {
        generateModelButton.setEnabled(isEnabled() && JsonUtil.isTypeJSON(responseBodyPane.getText()));
    }

    // ========== 辅助方法 ==========

    /**
     * 切换中心区域显示的卡片（文本编辑器 或 图片预览）
     */
    private void switchCard(String cardName) {
        Container centerPanel = searchableTextArea.getParent();
        if (centerPanel != null && centerPanel.getLayout() instanceof CardLayout cl) {
            cl.show(centerPanel, cardName);
        }
    }

    /**
     * 加载图片并切换到图片预览卡片
     * <p>
     * - GIF  → ImageIcon(URL)（Swing 内置动图支持，保留动画）
     * - WebP → TwelveMonkeys ImageIO 自动注册后 ImageIO.read() 可解码
     * - SVG  → 降级：直接用文本编辑器显示原始 XML 内容
     * - 其他 → ImageIO.read() + ImageIcon
     * </p>
     *
     * @param filePath 临时文件路径
     * @param bodySize 图片字节数
     */
    private void showImagePreview(String filePath, long bodySize) {
        File file = new File(filePath);
        String nameLower = file.getName().toLowerCase();

        try {
            // ── GIF 动图：使用 ImageIcon(URL) 保留动画 ──────────────────────
            if (nameLower.endsWith(".gif")) {
                ImageIcon gifIcon = new ImageIcon(file.toURI().toURL());
                sizeWarningLabel.setText(String.format("  %d×%d  [%.2f KB]",
                        gifIcon.getIconWidth(), gifIcon.getIconHeight(), bodySize / 1024.0));
                sizeWarningLabel.setVisible(true);
                imagePreviewLabel.setIcon(gifIcon);
                imagePreviewLabel.setText(null);
                switchCard(CARD_IMAGE);
                return;
            }

            // ── WebP / PNG / JPG / BMP / 其他（含 SVG 降级）────────────────
            // TwelveMonkeys 已通过 SPI 自动注册 WebP reader，ImageIO.read() 直接支持
            // SVG 无法被 ImageIO 解码（返回 null），会进入 showImageError 降级处理
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                // SVG 或其他无法解码的格式：读取文件文本内容显示在编辑器中
                if (nameLower.endsWith(".svg")) {
                    String svgText = Files.readString(file.toPath());
                    responseBodyPane.setText(svgText);
                    responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
                    responseBodyPane.setCaretPosition(0);
                    sizeWarningLabel.setText(String.format("  SVG  [%.2f KB]", bodySize / 1024.0));
                    sizeWarningLabel.setVisible(true);
                    switchCard(CARD_TEXT);
                } else {
                    showImageError("[Image cannot be decoded: " + file.getName() + "]", bodySize);
                }
                return;
            }
            sizeWarningLabel.setText(String.format("  %d×%d  [%.2f KB]",
                    img.getWidth(), img.getHeight(), bodySize / 1024.0));
            sizeWarningLabel.setVisible(true);
            imagePreviewLabel.setIcon(new ImageIcon(img));
            imagePreviewLabel.setText(null);
            switchCard(CARD_IMAGE);

        } catch (Exception e) {
            showImageError("[Failed to load image: " + e.getMessage() + "]", bodySize);
        }
    }

    /** 图片加载失败时回退到文本卡片显示错误信息 */
    private void showImageError(String message, long bodySize) {
        responseBodyPane.setText(message);
        responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        sizeWarningLabel.setText(String.format("  [%.2f KB]", bodySize / 1024.0));
        sizeWarningLabel.setVisible(true);
        switchCard(CARD_TEXT);
    }

    /**
     * 清空响应体内容
     */
    private void clearResponseBody() {
        responseBodyPane.setText("");
        updateGenerateModelButtonState();
        currentFilePath = null;
        currentResponse = null;
        fileName = DEFAULT_FILE_NAME;
        lastHeaders = null;
        responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        responseBodyPane.setCaretPosition(0);
        syntaxComboBox.setSelectedIndex(SyntaxType.AUTO_DETECT.getIndex());
        sizeWarningLabel.setVisible(false);
        imagePreviewLabel.setIcon(null);
        imagePreviewLabel.setText(null);
        mediaResponsePanel.clear();
        switchCard(CARD_TEXT);
    }

    /**
     * 从响应头中提取 Content-Type
     */
    private String extractContentType(Map<String, List<String>> headers) {
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(CONTENT_TYPE_HEADER)) {
                    List<String> values = entry.getValue();
                    if (values != null && !values.isEmpty()) {
                        return values.get(0);
                    }
                }
            }
        }
        return "";
    }

    /**
     * 更新大小警告标签
     */
    private void updateSizeWarning(int textSize, boolean isLargeResponse) {
        if (isLargeResponse) {
            sizeWarningLabel.setText(String.format("  [%.2f MB]", textSize / 1024.0 / 1024.0));
            sizeWarningLabel.setVisible(true);
        } else {
            sizeWarningLabel.setVisible(false);
        }
    }

}
