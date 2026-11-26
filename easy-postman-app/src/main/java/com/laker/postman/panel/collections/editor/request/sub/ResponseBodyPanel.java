package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.component.notification.NotificationCenter;

import cn.hutool.core.util.XmlUtil;
import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.EasyComboBox;
import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SearchableTextArea;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.*;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.http.runtime.file.ResponseFiles;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.*;
import lombok.Getter;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 响应体面板，展示 HTTP 响应体内容
 * <p>
 * 主要功能：
 * - 语法高亮显示（JSON、XML、HTML、JavaScript、CSS 等）
 * - 自动/手动格式化
 * - 文本搜索
 * - JSONPath 过滤
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
    private final SearchButton searchButton; // 搜索按钮
    private final JToggleButton jsonPathFilterButton;
    private final ResponseJsonPathFilterBar jsonPathFilterBar;
    /** 显示 JSONPath 过滤结果时保存的原始编辑器文档；未过滤时为 null。恢复时直接换回，避免在 EDT 上重新插入大文本。 */
    private Document unfilteredDocument;
    /** 本面板最近写入 {@link #FILTER_DOCUMENT_CACHE} 的数据源键，用于关闭过滤时只释放自己的缓存。 */
    private String filterDocumentSource;
    /** 每次发起过滤或响应变化时递增，用于丢弃后台过时结果。 */
    private final AtomicLong jsonPathFilterGeneration = new AtomicLong();
    private CompletableFuture<Void> pendingJsonPathFilter = CompletableFuture.completedFuture(null);
    /**
     * 过滤输入上限（落盘文件按字节、内存响应体按字符）。解析后的对象树约为原文的 7~8 倍
     * （实测 50MB JSON 约 380MB），在默认 1GB 堆下需留足余量。
     */
    private long jsonPathFilterMaxSourceSize = 20L * 1024 * 1024;
    /** 过滤结果上限（字符），避免 $..* 之类的表达式生成远大于原响应的结果。 */
    private int jsonPathFilterMaxResultChars = 10 * 1024 * 1024;
    private HttpResponse currentResponse;
    private final ResponseBodyDownloader downloader;
    private final EasyComboBox<String> syntaxComboBox;
    private final FormatButton formatButton;
    private final JToggleButton autoSortJsonKeysButton;
    private final CopyButton copyButton;
    private final GenerateModelButton generateModelButton;
    private final WrapToggleButton wrapButton;
    private final SearchableTextArea searchableTextArea; // 带搜索功能的文本编辑器
    private final JPanel centerPanel;
    private final FileResponsePanel fileResponsePanel;
    private final JButton fileOverviewButton;
    private boolean fileRawPreviewActive;

    // 常量定义
    private static final int LARGE_RESPONSE_THRESHOLD = 500 * 1024; // 500KB threshold
    /** 已解析文档缓存，全局只保留一份，避免多个标签页同时打开过滤时各自持有一棵大对象树。 */
    private static final AtomicReference<CachedFilterDocument> FILTER_DOCUMENT_CACHE = new AtomicReference<>();
    private static final ExecutorService JSON_PATH_FILTER_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "response-jsonpath-filter");
        thread.setDaemon(true);
        return thread;
    });
    private static final String CARD_TEXT = "TEXT";
    private static final String CARD_IMAGE = "IMAGE";
    private static final String CARD_MEDIA = "MEDIA";
    private static final String CARD_FILE = "FILE";

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
        fileResponsePanel = new FileResponsePanel(this::downloadFromFileOverview, this::showFileRawPreview);
        downloader = new ResponseBodyDownloader(this::onSavingChanged);

        // 使用 CardLayout 在文本、图片、媒体和文件概览之间切换
        centerPanel = new JPanel(new CardLayout());
        ToolWindowSurfaceStyle.applyCard(centerPanel);
        jsonPathFilterBar = new ResponseJsonPathFilterBar(
                this::applyJsonPathFilter, this::invalidatePendingJsonPathFilter, this::closeJsonPathFilter);
        jsonPathFilterBar.setVisible(false);
        JPanel textCard = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(textCard);
        textCard.add(searchableTextArea, BorderLayout.CENTER);
        textCard.add(jsonPathFilterBar, BorderLayout.SOUTH);
        centerPanel.add(textCard, CARD_TEXT);
        centerPanel.add(imageScrollPane, CARD_IMAGE);
        centerPanel.add(mediaResponsePanel, CARD_MEDIA);
        centerPanel.add(fileResponsePanel, CARD_FILE);
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
        sizeWarningLabel.setMinimumSize(new Dimension(
                0, sizeWarningLabel.getFontMetrics(sizeWarningLabel.getFont()).getHeight()));
        sizeWarningLabel.setVisible(false);
        toolBarPanel.add(sizeWarningLabel);

        // 弹性空间，将右侧控件推到右边
        toolBarPanel.add(Box.createHorizontalGlue());

        fileOverviewButton = new JButton();
        fileOverviewButton.setIcon(IconUtil.createThemed(
                "icons/file.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        fileOverviewButton.setToolTipText(
                I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_BACK_TO_OVERVIEW));
        fileOverviewButton.getAccessibleContext().setAccessibleName(
                I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_BACK_TO_OVERVIEW));
        fileOverviewButton.setFocusable(false);
        fileOverviewButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fileOverviewButton.putClientProperty(
                FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        fileOverviewButton.setVisible(false);
        fileOverviewButton.addActionListener(event -> showFileOverview());
        toolBarPanel.add(fileOverviewButton);
        toolBarPanel.add(Box.createHorizontalStrut(1));

        // 搜索按钮
        searchButton = new SearchButton();
        searchButton.addActionListener(e -> {
            responseBodyPane.requestFocusInWindow();
            searchableTextArea.showSearch();
        });
        toolBarPanel.add(searchButton);
        toolBarPanel.add(Box.createHorizontalStrut(1)); // 间隔

        // JSONPath 过滤切换按钮
        jsonPathFilterButton = new JToggleButton();
        jsonPathFilterButton.setToolTipText(
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_TOOLTIP));
        jsonPathFilterButton.getAccessibleContext().setAccessibleName(
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_TOOLTIP));
        jsonPathFilterButton.setFocusable(false);
        jsonPathFilterButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        jsonPathFilterButton.putClientProperty(
                FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        updateJsonPathFilterIcon(false);
        jsonPathFilterButton.addItemListener(e -> updateJsonPathFilterIcon(jsonPathFilterButton.isSelected()));
        jsonPathFilterButton.addActionListener(e -> {
            if (jsonPathFilterButton.isSelected()) {
                openJsonPathFilter();
            } else {
                closeJsonPathFilter();
            }
        });
        toolBarPanel.add(jsonPathFilterButton);
        toolBarPanel.add(Box.createHorizontalStrut(1));

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

        downloadButton.addActionListener(e -> downloadFromToolbar());
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
            syntax = detectResponseSyntax(responseBodyPane.getText(), currentContentType());
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
     * 工具栏下载（文本预览中）：保存成功后以通知提示；若当前响应有文件概览，同步显示保存位置，
     * 返回概览时不会看到过期的路径。
     */
    private void downloadFromToolbar() {
        HttpResponse responseAtStart = currentResponse;
        startDownload(savedFile -> {
            ResponseBodyDownloader.notifySaved(savedFile);
            if (currentResponse == responseAtStart && FileResponseModel.shouldShow(currentResponse)) {
                fileResponsePanel.showSavedFile(savedFile);
            }
        });
    }

    /**
     * 文件概览下载：保存成功后在概览中显示保存位置及打开入口。
     */
    private void downloadFromFileOverview() {
        HttpResponse responseAtStart = currentResponse;
        startDownload(savedFile -> {
            // 保存期间已切换到其他响应时，不能把结果显示在新响应的概览上
            if (currentResponse == responseAtStart) {
                fileResponsePanel.showSavedFile(savedFile);
            } else {
                ResponseBodyDownloader.notifySaved(savedFile);
            }
        });
    }

    private void startDownload(Consumer<File> onSaved) {
        downloader.download(currentBodySource(), ResponseBodyDownloader.defaultFileName(currentResponse), onSaved);
    }

    private void onSavingChanged(boolean saving) {
        downloadButton.setEnabled(!saving && isEnabled());
        fileResponsePanel.setDownloading(saving);
    }

    /**
     * 将当前响应体同步写入目标文件，写入规则见 {@link ResponseBodyDownloader.Source}。
     */
    void writeResponseBody(File destFile) throws IOException {
        currentBodySource().writeTo(destFile);
    }

    private ResponseBodyDownloader.Source currentBodySource() {
        return ResponseBodyDownloader.Source.of(currentResponse, unfilteredEditorText());
    }

    private void openJsonPathFilter() {
        jsonPathFilterButton.setSelected(true);
        jsonPathFilterBar.setVisible(true);
        revalidate();
        repaint();
        jsonPathFilterBar.focusInput();
        applyJsonPathFilter(jsonPathFilterBar.getExpression());
    }

    private void closeJsonPathFilter() {
        jsonPathFilterBar.cancelPendingApply();
        restoreUnfilteredBody();
        releaseFilterDocument();
        jsonPathFilterBar.clearStatus();
        jsonPathFilterBar.setVisible(false);
        jsonPathFilterButton.setSelected(false);
        revalidate();
        repaint();
    }

    /**
     * 对原始响应体应用 JSONPath 表达式。读取、解析、查询和结果文档构建都在后台线程执行，避免大响应阻塞 EDT；
     * 表达式无效时保留上一次显示内容并提示错误。
     */
    void applyJsonPathFilter(String expression) {
        long generation = jsonPathFilterGeneration.incrementAndGet();
        if (!jsonPathFilterBar.isVisible()) {
            return;
        }
        if (expression == null || expression.isBlank()) {
            restoreUnfilteredBody();
            jsonPathFilterBar.clearStatus();
            return;
        }
        JsonPathFilterSource source = jsonPathFilterSource();
        if (source == null) {
            restoreUnfilteredBody();
            jsonPathFilterBar.showError(I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_UNAVAILABLE));
            return;
        }
        CachedFilterDocument cached = FILTER_DOCUMENT_CACHE.get();
        JsonPathUtil.JsonPathDocument cachedDocument = cached != null && cached.key().equals(source.key())
                ? cached.document() : null;
        long maxSourceSize = jsonPathFilterMaxSourceSize;
        int maxResultChars = jsonPathFilterMaxResultChars;
        pendingJsonPathFilter = CompletableFuture
                .supplyAsync(() -> generation == jsonPathFilterGeneration.get()
                        ? JsonPathFilterOutcome.evaluate(source, cachedDocument, expression, maxSourceSize, maxResultChars)
                        : null, JSON_PATH_FILTER_EXECUTOR)
                .thenAcceptAsync(outcome -> applyJsonPathFilterOutcome(generation, source.key(), outcome),
                        SwingUtilities::invokeLater);
    }

    /** 表达式被编辑后，尚未完成的旧查询结果不再适用，等待防抖后的新查询。 */
    private void invalidatePendingJsonPathFilter() {
        jsonPathFilterGeneration.incrementAndGet();
    }

    /** 测试用：调整过滤输入/结果上限。 */
    void setJsonPathFilterLimits(long maxSourceSize, int maxResultChars) {
        jsonPathFilterMaxSourceSize = maxSourceSize;
        jsonPathFilterMaxResultChars = maxResultChars;
    }

    /** 测试用：等待最近一次过滤完成并应用到界面。 */
    CompletableFuture<Void> pendingJsonPathFilter() {
        return pendingJsonPathFilter;
    }

    /**
     * 过滤基于完整的原始响应体，而不是可能已被格式化/排序键的编辑器文本；
     * 超过内存阈值的响应体已落盘，此时 body 只是提示文本，需读取临时文件。
     *
     * @return 完整响应体不可用（读取中断、超过下载上限被跳过等）时返回 null
     */
    private JsonPathFilterSource jsonPathFilterSource() {
        HttpResponse response = currentResponse;
        if (response != null && !fileRawPreviewActive) {
            if (response.bodyIncomplete || response.bodyPreviewTruncated || response.bodyOmitted) {
                return null;
            }
            if (response.filePath != null && !response.filePath.isBlank()) {
                return JsonPathFilterSource.ofFile(new File(response.filePath), ResponseFiles.declaredCharset(response));
            }
        }
        if (response != null && response.body != null) {
            return JsonPathFilterSource.ofText(response.body);
        }
        return JsonPathFilterSource.ofText(unfilteredEditorText());
    }

    private String unfilteredEditorText() {
        if (unfilteredDocument == null) {
            return responseBodyPane.getText();
        }
        try {
            return unfilteredDocument.getText(0, unfilteredDocument.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void applyJsonPathFilterOutcome(long generation, String sourceKey, JsonPathFilterOutcome outcome) {
        if (outcome == null || generation != jsonPathFilterGeneration.get() || !jsonPathFilterBar.isVisible()) {
            return;
        }
        if (outcome.document() != null) {
            filterDocumentSource = sourceKey;
            FILTER_DOCUMENT_CACHE.set(new CachedFilterDocument(sourceKey, outcome.document()));
        }
        switch (outcome.status()) {
            case UNAVAILABLE -> {
                restoreUnfilteredBody();
                jsonPathFilterBar.showError(I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_UNAVAILABLE));
            }
            case NOT_JSON -> {
                restoreUnfilteredBody();
                jsonPathFilterBar.showError(I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_NOT_JSON));
            }
            case SOURCE_TOO_LARGE -> {
                restoreUnfilteredBody();
                jsonPathFilterBar.showError(I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_SOURCE_TOO_LARGE,
                        toMegabytes(outcome.limit())));
            }
            case RESULT_TOO_LARGE -> jsonPathFilterBar.showError(I18nUtil.getMessage(
                    MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_RESULT_TOO_LARGE, toMegabytes(outcome.limit())));
            case INVALID -> jsonPathFilterBar.showError(
                    I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_INVALID, outcome.error()));
            case NO_MATCH, MATCHED -> {
                if (unfilteredDocument == null) {
                    unfilteredDocument = responseBodyPane.getDocument();
                }
                // 结果文档已在后台构建，这里只替换文档，避免大结果在 EDT 上逐字插入
                responseBodyPane.setDocument(outcome.resultDocument());
                responseBodyPane.setCaretPosition(0);
                if (outcome.status() == JsonPathFilterStatus.NO_MATCH) {
                    jsonPathFilterBar.showInfo(I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_NO_MATCH));
                } else {
                    jsonPathFilterBar.clearStatus();
                }
                generateModelButton.setEnabled(isEnabled() && outcome.structured());
            }
        }
    }

    private static String toMegabytes(long size) {
        return BigDecimal.valueOf(size).divide(BigDecimal.valueOf(1024L * 1024), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private record CachedFilterDocument(String key, JsonPathUtil.JsonPathDocument document) {
    }

    private enum JsonPathFilterStatus {UNAVAILABLE, NOT_JSON, SOURCE_TOO_LARGE, RESULT_TOO_LARGE, INVALID, NO_MATCH, MATCHED}

    /** 过滤数据源：内存中的原始响应体，或超过内存阈值后落盘的完整响应体。 */
    private record JsonPathFilterSource(String key, String text, File file, Charset charset) {

        static JsonPathFilterSource ofText(String text) {
            return new JsonPathFilterSource(text, text, null, null);
        }

        static JsonPathFilterSource ofFile(File file, Charset charset) {
            return new JsonPathFilterSource("file:" + file.getAbsolutePath(), null, file, charset);
        }

        long size() {
            return file == null ? text.length() : file.length();
        }

        String load() throws IOException {
            return file == null ? text : new String(Files.readAllBytes(file.toPath()), charset);
        }
    }

    /** 后台过滤结果；document 为解析后的响应体（可缓存复用），resultDocument 为待展示的结果编辑器文档。 */
    private record JsonPathFilterOutcome(JsonPathFilterStatus status, JsonPathUtil.JsonPathDocument document,
                                         String error, RSyntaxDocument resultDocument, boolean structured, long limit) {

        JsonPathFilterOutcome(JsonPathFilterStatus status, JsonPathUtil.JsonPathDocument document,
                              String error, RSyntaxDocument resultDocument, boolean structured) {
            this(status, document, error, resultDocument, structured, 0);
        }

        static JsonPathFilterOutcome evaluate(JsonPathFilterSource source, JsonPathUtil.JsonPathDocument cachedDocument,
                                              String expression, long maxSourceSize, int maxResultChars) {
            JsonPathUtil.JsonPathDocument document = cachedDocument;
            if (document == null) {
                if (source.size() > maxSourceSize) {
                    return new JsonPathFilterOutcome(JsonPathFilterStatus.SOURCE_TOO_LARGE, null, null, null, false,
                            maxSourceSize);
                }
                String json;
                try {
                    json = source.load();
                } catch (IOException | UncheckedIOException e) {
                    return new JsonPathFilterOutcome(JsonPathFilterStatus.UNAVAILABLE, null, null, null, false);
                }
                try {
                    document = JsonPathUtil.parseDocument(json);
                } catch (IllegalArgumentException notJson) {
                    return new JsonPathFilterOutcome(JsonPathFilterStatus.NOT_JSON, null, null, null, false);
                }
            }
            String result;
            try {
                result = JsonPathUtil.queryAsPrettyJson(document, expression, maxResultChars);
            } catch (JsonPathUtil.ResultTooLargeException tooLarge) {
                return new JsonPathFilterOutcome(JsonPathFilterStatus.RESULT_TOO_LARGE, document, null, null, false,
                        tooLarge.getMaxResultChars());
            } catch (RuntimeException invalid) {
                // 表达式错误为 IllegalArgumentException；其它意外异常同样提示，避免界面无反馈
                String message = invalid.getMessage() == null ? invalid.getClass().getSimpleName() : invalid.getMessage();
                return new JsonPathFilterOutcome(JsonPathFilterStatus.INVALID, document, message, null, false);
            }
            RSyntaxDocument resultDocument = new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_JSON);
            if (result != null) {
                try {
                    resultDocument.insertString(0, result, null);
                } catch (BadLocationException e) {
                    throw new IllegalStateException(e);
                }
            }
            // 结果由 Jackson 序列化，对象/数组必然以 { 或 [ 开头
            boolean structured = result != null && (result.startsWith("{") || result.startsWith("["));
            return new JsonPathFilterOutcome(result == null ? JsonPathFilterStatus.NO_MATCH : JsonPathFilterStatus.MATCHED,
                    document, null, resultDocument, structured);
        }
    }

    private void releaseFilterDocument() {
        jsonPathFilterGeneration.incrementAndGet();
        String ownKey = filterDocumentSource;
        filterDocumentSource = null;
        if (ownKey != null) {
            FILTER_DOCUMENT_CACHE.updateAndGet(cached -> cached != null && cached.key() == ownKey ? null : cached);
        }
    }

    private void restoreUnfilteredBody() {
        if (unfilteredDocument == null) {
            return;
        }
        Document original = unfilteredDocument;
        unfilteredDocument = null;
        // 换回原文档会恢复其自身的语法样式；用户在过滤期间手动指定的语法仍然生效
        responseBodyPane.setDocument(original);
        SyntaxType selectedSyntax = SyntaxType.getByIndex(syntaxComboBox.getSelectedIndex());
        if (selectedSyntax != SyntaxType.AUTO_DETECT) {
            responseBodyPane.setSyntaxEditingStyle(selectedSyntax.getSyntaxStyle());
        }
        responseBodyPane.setCaretPosition(0);
        updateGenerateModelButtonState();
    }

    /** 新响应即将替换编辑器内容时丢弃旧的过滤快照，过滤表达式本身保留以便重新应用。 */
    private void discardJsonPathFilterResult() {
        jsonPathFilterBar.cancelPendingApply();
        unfilteredDocument = null;
        releaseFilterDocument();
        jsonPathFilterBar.clearStatus();
    }

    private void reapplyJsonPathFilter() {
        if (jsonPathFilterBar.isVisible() && !jsonPathFilterBar.getExpression().isEmpty()) {
            applyJsonPathFilter(jsonPathFilterBar.getExpression());
        }
    }

    private void updateJsonPathFilterIcon(boolean selected) {
        String iconPath = "icons/filter.svg";
        jsonPathFilterButton.setIcon(selected
                ? IconUtil.createOnPrimary(iconPath, IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL)
                : IconUtil.createThemed(iconPath, IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
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

        String contentType = currentContentType();

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

    private String currentContentType() {
        return ResponseFiles.contentType(currentResponse);
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

        discardJsonPathFilterResult();
        this.currentResponse = resp;
        this.fileRawPreviewActive = false;
        sizeWarningLabel.setToolTipText(null);
        fileResponsePanel.clear();
        String contentType = ResponseFiles.contentType(resp);

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

        if (FileResponseModel.shouldShow(resp)) {
            showFileResponse(resp);
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
        String syntax = detectResponseSyntax(text, contentType);

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
        reapplyJsonPathFilter();
    }

    private void showFileResponse(HttpResponse response) {
        responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        // 文件概览不展示编辑器，二进制文本延迟到“按文本查看”时再加载，避免在 EDT 上渲染大段乱码
        responseBodyPane.setText("");
        syntaxComboBox.setSelectedIndex(SyntaxType.PLAIN_TEXT.getIndex());
        sizeWarningLabel.setVisible(false);
        generateModelButton.setEnabled(false);
        fileResponsePanel.setResponse(FileResponseModel.from(response));
        switchCard(CARD_FILE);
    }

    private void showFileRawPreview() {
        if (currentResponse == null || currentResponse.rawBodyBytes == null) {
            return;
        }
        fileRawPreviewActive = true;
        responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        responseBodyPane.setText(currentResponse.body == null ? "" : currentResponse.body);
        responseBodyPane.setCaretPosition(0);
        sizeWarningLabel.setText("  "
                + I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_TEXT_PREVIEW_BADGE));
        sizeWarningLabel.setToolTipText(
                I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_TEXT_PREVIEW_NOTICE));
        sizeWarningLabel.setVisible(true);
        switchCard(CARD_TEXT);
        responseBodyPane.requestFocusInWindow();
    }

    private void showFileOverview() {
        if (currentResponse == null || !fileRawPreviewActive) {
            return;
        }
        fileRawPreviewActive = false;
        sizeWarningLabel.setVisible(false);
        sizeWarningLabel.setToolTipText(null);
        switchCard(CARD_FILE);
    }

    /**
     * 二进制响应在文本视图中展示的是接收提示，不是原始响应内容。
     */
    private String detectResponseSyntax(String text, String contentType) {
        if (currentResponse != null
                && !currentResponse.isImage
                && FileExtensionUtil.isBinaryType(contentType)) {
            return SyntaxConstants.SYNTAX_STYLE_NONE;
        }
        return detectSyntax(text, contentType);
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
        jsonPathFilterButton.setEnabled(enabled);
        jsonPathFilterBar.getExpressionField().setEnabled(enabled);
        downloadButton.setEnabled(enabled && !downloader.isSaving());
        fileOverviewButton.setEnabled(enabled);
        searchableTextArea.setEnabled(enabled);

        if (formatButton != null) formatButton.setEnabled(enabled);
        if (autoSortJsonKeysButton != null) autoSortJsonKeysButton.setEnabled(enabled);
        if (copyButton != null) copyButton.setEnabled(enabled);
        updateGenerateModelButtonState();
        if (wrapButton != null) wrapButton.setEnabled(enabled);
        if (saveResponseButton != null) saveResponseButton.setEnabled(enabled);
        mediaResponsePanel.setEnabled(enabled);
        fileResponsePanel.setEnabled(enabled);
    }

    private void updateGenerateModelButtonState() {
        generateModelButton.setEnabled(isEnabled() && JsonUtil.isTypeJSON(responseBodyPane.getText()));
    }

    // ========== 辅助方法 ==========

    /**
     * 切换中心区域显示的响应卡片。
     */
    private void switchCard(String cardName) {
        boolean fileCard = CARD_FILE.equals(cardName);
        boolean fileRawPreview = CARD_TEXT.equals(cardName) && fileRawPreviewActive;

        syntaxComboBox.setVisible(CARD_TEXT.equals(cardName) && !fileRawPreview);
        fileOverviewButton.setVisible(fileRawPreview);
        // 文件概览卡片自带下载按钮，隐藏工具栏下载按钮避免重复
        downloadButton.setVisible(!fileCard);
        searchButton.setVisible(!fileCard);
        wrapButton.setVisible(!fileCard);
        copyButton.setVisible(!fileCard);
        autoSortJsonKeysButton.setVisible(!fileCard && !fileRawPreview);
        formatButton.setVisible(!fileCard && !fileRawPreview);
        generateModelButton.setVisible(!fileCard && !fileRawPreview);
        boolean jsonPathFilterAvailable = CARD_TEXT.equals(cardName) && !fileRawPreview;
        jsonPathFilterButton.setVisible(jsonPathFilterAvailable);
        jsonPathFilterBar.setVisible(jsonPathFilterAvailable && jsonPathFilterButton.isSelected());

        if (centerPanel.getLayout() instanceof CardLayout cl) {
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
                    syntaxComboBox.setSelectedIndex(SyntaxType.XML.getIndex());
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
        syntaxComboBox.setSelectedIndex(SyntaxType.PLAIN_TEXT.getIndex());
        responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        sizeWarningLabel.setText(String.format("  [%.2f KB]", bodySize / 1024.0));
        sizeWarningLabel.setVisible(true);
        switchCard(CARD_TEXT);
    }

    /**
     * 清空响应体内容
     */
    private void clearResponseBody() {
        discardJsonPathFilterResult();
        responseBodyPane.setText("");
        updateGenerateModelButtonState();
        currentResponse = null;
        fileRawPreviewActive = false;
        responseBodyPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        responseBodyPane.setCaretPosition(0);
        syntaxComboBox.setSelectedIndex(SyntaxType.AUTO_DETECT.getIndex());
        sizeWarningLabel.setVisible(false);
        sizeWarningLabel.setToolTipText(null);
        imagePreviewLabel.setIcon(null);
        imagePreviewLabel.setText(null);
        mediaResponsePanel.clear();
        fileResponsePanel.clear();
        switchCard(CARD_TEXT);
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
