package com.laker.postman.panel.collections.tree;

import com.laker.postman.model.Environment;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.HttpRequestItem;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.SearchTextField;
import com.laker.postman.common.component.ToolWindowSidebarToolbar;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.PlusButton;
import com.laker.postman.common.component.dialog.CurlImportDialog;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.panel.collections.tree.coordinator.RequestTreeCoordinator;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.panel.topmenu.TopMenuBar;
import com.laker.postman.service.EnvironmentService;
import com.laker.postman.service.apipost.ApiPostCollectionParser;
import com.laker.postman.collection.model.CollectionParseResult;
import com.laker.postman.service.common.TreeNodeBuilder;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreeDocumentMapper;
import com.laker.postman.service.curl.CurlParser;
import com.laker.postman.service.har.HarParser;
import com.laker.postman.service.ideahttp.IntelliJHttpParser;
import com.laker.postman.collection.importer.postman.PostmanCollectionParser;
import com.laker.postman.service.swagger.SwaggerParser;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.AsyncClipboardUtil;
import com.laker.postman.util.ClipboardUtil;
import com.laker.postman.util.FileChooserUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.service.curl.CurlImportUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.laker.postman.panel.collections.tree.CollectionTreePanel.*;


@Slf4j
public class CollectionTreeToolbar extends UiSingletonPanel {
    private static final String FILE_CHOOSER_STATE_COLLECTION_IMPORT = "collections.import";
    private static final String FILE_CHOOSER_STATE_POSTMAN_IMPORT = "collections.import.postman";
    private static final String FILE_CHOOSER_STATE_HAR_IMPORT = "collections.import.har";
    private static final String FILE_CHOOSER_STATE_SWAGGER_IMPORT = "collections.import.swagger";
    private static final String FILE_CHOOSER_STATE_HTTP_IMPORT = "collections.import.http";
    private static final String FILE_CHOOSER_STATE_APIPOST_IMPORT = "collections.import.apipost";

    private SearchTextField searchField;
    /**
     * + 按钮（新建集合 / 各种导入）
     */
    private PlusButton plusBtn;

    @Override
    protected void initUI() {
        setLayout(new BorderLayout());
        setOpaque(false);

        plusBtn = new PlusButton();
        plusBtn.setToolTipText("New / Import");
        plusBtn.addActionListener(e -> showPlusMenu());

        searchField = new SearchTextField();

        add(new ToolWindowSidebarToolbar(plusBtn, searchField), BorderLayout.CENTER);
    }

    /**
     * 点击 + 按钮弹出菜单：新建集合 + 分隔线 + 各种导入
     */
    private void showPlusMenu() {
        readClipboardCurlTextAsync()
                .thenAccept(curlText -> SwingUtilities.invokeLater(() -> showPlusMenu(curlText)));
    }

    CompletableFuture<String> readClipboardCurlTextAsync() {
        return ClipboardUtil.getClipboardCurlTextAsync();
    }

    private void showPlusMenu(String clipboardText) {
        if (clipboardText != null) {
            int result = JOptionPane.showConfirmDialog(
                    UiSingletonFactory.getInstance(MainFrame.class),
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_CURL_DETECTED),
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_CURL_TITLE),
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                importCurlToCollection(clipboardText);
                return;
            }
        }

        JPopupMenu menu = buildPlusMenu();
        menu.show(plusBtn, 0, plusBtn.getHeight());
    }

    private JPopupMenu buildPlusMenu() {
        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);

        // ── 新建集合
        JMenuItem newCollection = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_NEW_COLLECTION),
                IconUtil.create("icons/collection.svg", IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
        newCollection.addActionListener(e -> {
            CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
            RequestTreeCoordinator coordinator = new RequestTreeCoordinator(leftPanel.getRequestTree(), leftPanel);
            coordinator.showAddGroupDialog(leftPanel.getRootTreeNode());
        });
        menu.add(newCollection);

        menu.addSeparator();

        // ── 各种导入
        JMenuItem importEasy = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_EASY),
                new FlatSVGIcon("icons/easy.svg", IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
        importEasy.addActionListener(e -> importRequestCollection());
        menu.add(importEasy);

        JMenuItem importPostman = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_POSTMAN),
                new FlatSVGIcon("icons/postman.svg", IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
        importPostman.addActionListener(e -> importPostmanCollection());
        menu.add(importPostman);

        JMenuItem importSwagger2 = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_SWAGGER2),
                new FlatSVGIcon("icons/swagger.svg", IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
        importSwagger2.addActionListener(e -> importSwaggerCollection());
        menu.add(importSwagger2);

        JMenuItem importOpenApi3 = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_OPENAPI3),
                new FlatSVGIcon("icons/openapi.svg", IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
        importOpenApi3.addActionListener(e -> importSwaggerCollection());
        menu.add(importOpenApi3);

        JMenuItem importHar = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HAR),
                new FlatSVGIcon("icons/insomnia.svg", IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
        importHar.addActionListener(e -> importHarCollection());
        menu.add(importHar);

        JMenuItem importHttp = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HTTP),
                new FlatSVGIcon("icons/idea-http.svg", IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
        importHttp.addActionListener(e -> importHttpFile());
        menu.add(importHttp);

        JMenuItem importApiPost = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_APIPOST),
                new FlatSVGIcon("icons/apipost.svg", IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
        importApiPost.addActionListener(e -> importApiPostCollection());
        menu.add(importApiPost);

        JMenuItem importCurl = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_CURL),
                new FlatSVGIcon("icons/curl.svg", IconUtil.SIZE_MEDIUM, IconUtil.SIZE_MEDIUM));
        importCurl.addActionListener(e -> importCurlToCollection(null));
        menu.add(importCurl);

        return menu;
    }

    @Override
    protected void registerListeners() {
        // 搜索过滤逻辑
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void filterTree() {
                CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
                String text = searchField.getText().trim();
                if (text.isEmpty()) {
                    searchField.setNoResult(false);
                    // 展开所有一级分组，显示全部
                    expandAll(leftPanel.getRequestTree(), false);
                    leftPanel.getTreeModel().setRoot(leftPanel.getRootTreeNode());
                    leftPanel.getTreeModel().reload();
                    return;
                }

                // 获取搜索选项
                boolean caseSensitive = searchField.isCaseSensitive();
                boolean wholeWord = searchField.isWholeWord();

                DefaultMutableTreeNode filteredRoot = new DefaultMutableTreeNode(ROOT);
                boolean hasResult = filterNodes(leftPanel.getRootTreeNode(), filteredRoot, text, caseSensitive, wholeWord);
                // 无结果时搜索框变红，有结果时恢复正常
                searchField.setNoResult(!hasResult);
                leftPanel.getTreeModel().setRoot(filteredRoot);
                leftPanel.getTreeModel().reload();
                expandAll(leftPanel.getRequestTree(), true);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                filterTree();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterTree();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterTree();
            }
        });

        // 监听搜索选项变化，触发重新过滤
        searchField.addPropertyChangeListener("caseSensitive", evt -> {
            if (!searchField.getText().isEmpty()) {
                CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
                String text = searchField.getText().trim();
                boolean caseSensitive = searchField.isCaseSensitive();
                boolean wholeWord = searchField.isWholeWord();
                DefaultMutableTreeNode filteredRoot = new DefaultMutableTreeNode(ROOT);
                boolean hasResult = filterNodes(leftPanel.getRootTreeNode(), filteredRoot, text, caseSensitive, wholeWord);
                searchField.setNoResult(!hasResult);
                leftPanel.getTreeModel().setRoot(filteredRoot);
                leftPanel.getTreeModel().reload();
                expandAll(leftPanel.getRequestTree(), true);
            }
        });
        searchField.addPropertyChangeListener("wholeWord", evt -> {
            if (!searchField.getText().isEmpty()) {
                CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
                String text = searchField.getText().trim();
                boolean caseSensitive = searchField.isCaseSensitive();
                boolean wholeWord = searchField.isWholeWord();
                DefaultMutableTreeNode filteredRoot = new DefaultMutableTreeNode(ROOT);
                boolean hasResult = filterNodes(leftPanel.getRootTreeNode(), filteredRoot, text, caseSensitive, wholeWord);
                searchField.setNoResult(!hasResult);
                leftPanel.getTreeModel().setRoot(filteredRoot);
                leftPanel.getTreeModel().reload();
                expandAll(leftPanel.getRequestTree(), true);
            }
        });
    }


    // 导入请求集合JSON文件
    private void importRequestCollection() {
        CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        MainFrame mainFrame = UiSingletonFactory.getInstance(MainFrame.class);
        SystemFileChooser fileChooser = FileChooserUtil.createOpenFileChooser(
                FILE_CHOOSER_STATE_COLLECTION_IMPORT,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_DIALOG_TITLE));
        int userSelection = fileChooser.showOpenDialog(mainFrame);
        if (userSelection == SystemFileChooser.APPROVE_OPTION) {
            File fileToOpen = fileChooser.getSelectedFile();
            try {
                // 导入时不清空老数据，而是全部加入到一个新分组下
                String groupName = "EasyPostman";
                DefaultMutableTreeNode easyPostmanGroup = leftPanel.findGroupNode(leftPanel.getRootTreeNode(), groupName);
                if (easyPostmanGroup == null) {
                    RequestGroup group = new RequestGroup(groupName);
                    easyPostmanGroup = CollectionTreeNodes.groupNode(group);
                    leftPanel.getRootTreeNode().add(easyPostmanGroup);
                }
                CollectionDocument document = CollectionDocumentJsonCodec.read(fileToOpen);
                for (DefaultMutableTreeNode groupNode : SwingCollectionTreeDocumentMapper.toTreeNodes(document)) {
                    easyPostmanGroup.add(groupNode);
                }
                leftPanel.getTreeModel().reload();

                leftPanel.getCollectionTreePersistence().saveCurrentTree();
                leftPanel.getRequestTree().expandPath(new TreePath(easyPostmanGroup.getPath()));
                NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_SUCCESS));
            } catch (Exception ex) {
                log.error("Import error", ex);
                JOptionPane.showMessageDialog(mainFrame,
                        I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_FAIL, ex.getMessage()),
                        I18nUtil.getMessage(MessageKeys.GENERAL_ERROR), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 导入Postman集合
    private void importPostmanCollection() {
        CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        MainFrame mainFrame = UiSingletonFactory.getInstance(MainFrame.class);
        SystemFileChooser fileChooser = FileChooserUtil.createOpenFileChooser(
                FILE_CHOOSER_STATE_POSTMAN_IMPORT,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_POSTMAN_DIALOG_TITLE));
        int userSelection = fileChooser.showOpenDialog(mainFrame);
        if (userSelection == SystemFileChooser.APPROVE_OPTION) {
            File fileToOpen = fileChooser.getSelectedFile();
            try {
                String json = FileUtil.readString(fileToOpen, StandardCharsets.UTF_8);
                CollectionParseResult parseResult =
                        PostmanCollectionParser.parsePostmanCollection(json);
                if (parseResult != null) {
                    DefaultMutableTreeNode collectionNode =
                            TreeNodeBuilder.buildFromParseResult(parseResult);
                    leftPanel.getRootTreeNode().add(collectionNode);
                    leftPanel.getTreeModel().reload();
                    leftPanel.getCollectionTreePersistence().saveCurrentTree();
                    leftPanel.getRequestTree().expandPath(new TreePath(collectionNode.getPath()));
                    NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_SUCCESS));
                } else {
                    NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_POSTMAN_INVALID));
                }
            } catch (Exception ex) {
                log.error("Import error", ex);
                NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_FAIL, ex.getMessage()));
            }
        }
    }

    // 导入HAR集合
    private void importHarCollection() {
        CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        MainFrame mainFrame = UiSingletonFactory.getInstance(MainFrame.class);
        SystemFileChooser fileChooser = FileChooserUtil.createOpenFileChooser(
                FILE_CHOOSER_STATE_HAR_IMPORT,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HAR_DIALOG_TITLE));
        int userSelection = fileChooser.showOpenDialog(mainFrame);
        if (userSelection == SystemFileChooser.APPROVE_OPTION) {
            File fileToOpen = fileChooser.getSelectedFile();
            try {
                String json = FileUtil.readString(fileToOpen, StandardCharsets.UTF_8);
                DefaultMutableTreeNode collectionNode = HarParser.parseHar(json);
                if (collectionNode != null) {
                    leftPanel.getRootTreeNode().add(collectionNode);
                    leftPanel.getTreeModel().reload();
                    leftPanel.getCollectionTreePersistence().saveCurrentTree();
                    leftPanel.getRequestTree().expandPath(new TreePath(collectionNode.getPath()));
                    NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_SUCCESS));
                } else {
                    NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HAR_INVALID));
                }
            } catch (Exception ex) {
                log.error("Import HAR error", ex);
                NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_FAIL, ex.getMessage()));
            }
        }
    }

    // 导入Swagger/OpenAPI集合
    private void importSwaggerCollection() {
        CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        MainFrame mainFrame = UiSingletonFactory.getInstance(MainFrame.class);
        SystemFileChooser fileChooser = FileChooserUtil.createOpenFileChooser(
                FILE_CHOOSER_STATE_SWAGGER_IMPORT,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_SWAGGER_DIALOG_TITLE));
        int userSelection = fileChooser.showOpenDialog(mainFrame);
        if (userSelection == SystemFileChooser.APPROVE_OPTION) {
            File fileToOpen = fileChooser.getSelectedFile();
            try {
                String json = FileUtil.readString(fileToOpen, StandardCharsets.UTF_8);
                CollectionParseResult parseResult = SwaggerParser.parseSwagger(json);
                if (parseResult != null) {
                    DefaultMutableTreeNode collectionNode =
                            TreeNodeBuilder.buildFromParseResult(parseResult);
                    leftPanel.getRootTreeNode().add(collectionNode);
                    leftPanel.getTreeModel().reload();
                    leftPanel.getCollectionTreePersistence().saveCurrentTree();
                    leftPanel.getRequestTree().expandPath(new TreePath(collectionNode.getPath()));

                    // 导入环境变量
                    importEnvironmentsFromParseResult(parseResult);

                    NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_SUCCESS));
                } else {
                    NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_SWAGGER_INVALID));
                }
            } catch (Exception ex) {
                log.error("Import Swagger error", ex);
                NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_FAIL, ex.getMessage()));
            }
        }
    }

    /**
     * 从解析结果中导入环境变量
     */
    private void importEnvironmentsFromParseResult(CollectionParseResult parseResult) {
        if (parseResult.getEnvironments() == null || parseResult.getEnvironments().isEmpty()) {
            return;
        }

        int importedCount = 0;
        for (Environment env : parseResult.getEnvironments()) {
            try {
                EnvironmentService.saveEnvironment(env);
                importedCount++;
                log.info("导入环境变量: {} ({}个变量)", env.getName(), env.getVariableList().size());
            } catch (Exception e) {
                log.error("导入环境变量失败: {}", env.getName(), e);
            }
        }

        if (importedCount > 0) {
            log.info("成功导入 {} 个环境", importedCount);

            // 刷新 TopMenuBar 的环境下拉框
            try {
                TopMenuBar topMenuBar = UiSingletonFactory.getInstance(TopMenuBar.class);
                if (topMenuBar.getEnvironmentComboBox() != null) {
                    topMenuBar.getEnvironmentComboBox().reload();
                    log.debug("已刷新环境下拉框");
                }
            } catch (Exception e) {
                log.error("刷新环境下拉框失败", e);
            }
        }
    }

    // 导入HTTP文件
    private void importHttpFile() {
        CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        MainFrame mainFrame = UiSingletonFactory.getInstance(MainFrame.class);
        SystemFileChooser fileChooser = FileChooserUtil.createOpenFileChooser(
                FILE_CHOOSER_STATE_HTTP_IMPORT,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HTTP_DIALOG_TITLE));
        SystemFileChooser.FileNameExtensionFilter httpFilter =
                FileChooserUtil.extensionFilter("HTTP Files (*.http)", "http");
        fileChooser.setFileFilter(httpFilter);
        int userSelection = fileChooser.showOpenDialog(mainFrame);
        if (userSelection == SystemFileChooser.APPROVE_OPTION) {
            File fileToOpen = fileChooser.getSelectedFile();
            try {
                String content = FileUtil.readString(fileToOpen, StandardCharsets.UTF_8);
                String filename = fileToOpen.getName();
                CollectionParseResult parseResult =
                        IntelliJHttpParser.parseHttpFile(content, filename);
                if (parseResult != null) {
                    DefaultMutableTreeNode collectionNode =
                            TreeNodeBuilder.buildFromParseResult(parseResult);
                    leftPanel.getRootTreeNode().add(collectionNode);
                    leftPanel.getTreeModel().reload();
                    leftPanel.getCollectionTreePersistence().saveCurrentTree();
                    leftPanel.getRequestTree().expandPath(new TreePath(collectionNode.getPath()));
                    NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_SUCCESS));
                } else {
                    NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HTTP_INVALID));
                }
            } catch (Exception ex) {
                log.error("Import HTTP file error", ex);
                NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_FAIL, ex.getMessage()));
            }
        }
    }

    /**
     * 导入 ApiPost 文档
     */
    private void importApiPostCollection() {
        CollectionTreePanel leftPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        MainFrame mainFrame = UiSingletonFactory.getInstance(MainFrame.class);
        SystemFileChooser fileChooser = FileChooserUtil.createOpenFileChooser(
                FILE_CHOOSER_STATE_APIPOST_IMPORT,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_APIPOST_DIALOG_TITLE));
        int userSelection = fileChooser.showOpenDialog(mainFrame);
        if (userSelection == SystemFileChooser.APPROVE_OPTION) {
            File fileToOpen = fileChooser.getSelectedFile();
            try {
                String json = FileUtil.readString(fileToOpen, StandardCharsets.UTF_8);
                CollectionParseResult parseResult =
                        ApiPostCollectionParser.parseApiPostCollection(json);
                if (parseResult != null) {
                    DefaultMutableTreeNode collectionNode =
                            TreeNodeBuilder.buildFromParseResult(parseResult);
                    leftPanel.getRootTreeNode().add(collectionNode);
                    leftPanel.getTreeModel().reload();
                    leftPanel.getCollectionTreePersistence().saveCurrentTree();
                    leftPanel.getRequestTree().expandPath(new TreePath(collectionNode.getPath()));

                    importEnvironmentsFromParseResult(parseResult);

                    NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_SUCCESS));
                } else {
                    NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_APIPOST_INVALID));
                }
            } catch (Exception ex) {
                log.error("Import ApiPost error", ex);
                NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_FAIL, ex.getMessage()));
            }
        }
    }

    public void importCurlToGroup(RequestGroup targetGroup) {
        if (targetGroup == null) return;
        importCurlToCollection(null, targetGroup);
    }

    private void importCurlToCollection(String defaultCurl) {
        importCurlToCollection(defaultCurl, null);
    }

    private void importCurlToCollection(String defaultCurl, RequestGroup targetGroup) {
        MainFrame mainFrame = UiSingletonFactory.getInstance(MainFrame.class);
        String curlText = CurlImportDialog.show(mainFrame,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_CURL_DIALOG_TITLE),
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_CURL_DIALOG_PROMPT),
                defaultCurl);
        if (curlText == null || curlText.trim().isEmpty()) return;
        try {
            CurlImportResult importResult = parseCurlImport(curlText);
            if (importResult.items().isEmpty()) {
                NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_CURL_PARSE_FAIL));
                return;
            }

            boolean batchInput = importResult.totalCommands() > 1;
            boolean saved;
            if (targetGroup != null) {
                saved = saveRequestsToGroup(importResult.items(), targetGroup);
            } else if (batchInput) {
                saved = saveRequestsWithGroupDialog(importResult.items());
            } else {
                saved = saveRequestWithGroupDialog(importResult.items().get(0));
            }

            // 导入成功后清空剪贴板
            if (saved) {
                AsyncClipboardUtil.clearStringAsync();
                notifyCurlImportResult(importResult);
            }
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_CURL_PARSE_ERROR, ex.getMessage()));
        }
    }

    private boolean containsCurlCommands(String text) {
        return text != null && !CurlParser.splitCommands(text).isEmpty();
    }

    private CurlImportResult parseCurlImport(String curlText) {
        List<String> commands = CurlParser.splitCommands(curlText);
        List<HttpRequestItem> items = new ArrayList<>();
        int failedCount = 0;

        for (String command : commands) {
            try {
                HttpRequestItem item = CurlImportUtil.fromCurlRequest(CurlParser.parse(command));
                if (item == null) {
                    failedCount++;
                    continue;
                }
                item.setName(defaultCurlRequestName(item));
                items.add(item);
            } catch (Exception ex) {
                failedCount++;
                log.warn("Parse cURL command failed during batch import", ex);
            }
        }

        return new CurlImportResult(items, commands.size(), failedCount);
    }

    private String defaultCurlRequestName(HttpRequestItem item) {
        String url = item.getUrl();
        if (url == null || url.trim().isEmpty()) {
            return I18nUtil.getMessage(MessageKeys.REQUEST_NAME);
        }
        return url;
    }

    private void notifyCurlImportResult(CurlImportResult importResult) {
        if (importResult.totalCommands() <= 1) {
            return;
        }
        if (importResult.failedCount() > 0) {
            NotificationCenter.showWarning(I18nUtil.getMessage(
                    MessageKeys.COLLECTIONS_IMPORT_CURL_BATCH_PARTIAL,
                    importResult.items().size(),
                    importResult.failedCount()
            ));
            return;
        }
        NotificationCenter.showSuccess(I18nUtil.getMessage(
                MessageKeys.COLLECTIONS_IMPORT_CURL_BATCH_SUCCESS,
                importResult.items().size()
        ));
    }


    /**
     * 通过弹窗让用户选择分组和命名，保存 HttpRequestItem 到集合（公用方法，适用于cURL导入等场景）
     *
     * @param item 要保存的请求
     */
    private boolean saveRequestWithGroupDialog(HttpRequestItem item) {
        CollectionTreePanel collectionPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        RequestEditorPanel requestEditPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
        TreeModel groupTreeModel = collectionPanel.getGroupTreeModel();
        RequestNameSelection selection = CollectionGroupSelectionDialog
                .chooseGroupAndRequestName(groupTreeModel, item.getName())
                .orElse(null);
        if (selection == null) return false;
        String requestName = selection.requestName();
        item.setName(requestName);
        item.setId(IdUtil.simpleUUID());
        collectionPanel.saveRequestToGroup(selection.group(), item);
        requestEditPanel.showOrCreateTab(item); // 打开请求编辑tab
        // tree选中新增的请求节点
        collectionPanel.locateAndSelectRequest(item.getId());
        return true;
    }

    private boolean saveRequestsWithGroupDialog(List<HttpRequestItem> items) {
        CollectionTreePanel collectionPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        TreeModel groupTreeModel = collectionPanel.getGroupTreeModel();
        RequestGroup group = CollectionGroupSelectionDialog.chooseGroup(groupTreeModel).orElse(null);
        if (group == null) return false;

        return saveRequestsToGroup(items, group);
    }

    private boolean saveRequestsToGroup(List<HttpRequestItem> items, RequestGroup group) {
        if (items == null || items.isEmpty() || group == null) return false;
        CollectionTreePanel collectionPanel = UiSingletonFactory.getInstance(CollectionTreePanel.class);
        RequestEditorPanel requestEditPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
        HttpRequestItem lastItem = null;
        for (HttpRequestItem item : items) {
            item.setId(IdUtil.simpleUUID());
            if (item.getName() == null || item.getName().trim().isEmpty()) {
                item.setName(defaultCurlRequestName(item));
            }
            collectionPanel.saveRequestToGroup(group, item);
            lastItem = item;
        }

        if (lastItem != null) {
            requestEditPanel.showOrCreateTab(lastItem);
            collectionPanel.locateAndSelectRequest(lastItem.getId());
        }
        return true;
    }

    private record CurlImportResult(List<HttpRequestItem> items, int totalCommands, int failedCount) {
    }


    // 递归过滤节点，支持大小写敏感和整词匹配
    private boolean filterNodes(DefaultMutableTreeNode src, DefaultMutableTreeNode dest, String keyword,
                                boolean caseSensitive, boolean wholeWord) {
        boolean matched = false;
        if (CollectionTreeNodes.isGroup(src)) {
            RequestGroup group = CollectionTreeNodes.group(src).orElseThrow();
            String groupName = group.getName();
            DefaultMutableTreeNode groupNode = CollectionTreeNodes.groupNode(group);
            boolean childMatched = false;
            for (int i = 0; i < src.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) src.getChildAt(i);
                if (filterNodes(child, groupNode, keyword, caseSensitive, wholeWord)) {
                    childMatched = true;
                }
            }
            if (matchesText(groupName, keyword, caseSensitive, wholeWord) || childMatched) {
                dest.add(groupNode);
                matched = true;
            }
        } else if (CollectionTreeNodes.isRequest(src)) {
            HttpRequestItem item = CollectionTreeNodes.request(src).orElseThrow();
            boolean nameMatch = item.getName() != null &&
                    matchesText(item.getName(), keyword, caseSensitive, wholeWord);
            boolean urlMatch = item.getUrl() != null &&
                    matchesText(item.getUrl(), keyword, caseSensitive, wholeWord);
            if (nameMatch || urlMatch) {
                dest.add(CollectionTreeNodes.requestNode(item));
                matched = true;
            }
        } else {
            // 处理 root 节点
            boolean childMatched = false;
            for (int i = 0; i < src.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) src.getChildAt(i);
                if (filterNodes(child, dest, keyword, caseSensitive, wholeWord)) {
                    childMatched = true;
                }
            }
            matched = childMatched;
        }
        return matched;
    }

    /**
     * 判断文本是否匹配关键字，支持大小写敏感和整词匹配
     */
    private boolean matchesText(String text, String keyword, boolean caseSensitive, boolean wholeWord) {
        if (text == null || keyword == null) {
            return false;
        }

        String searchText = caseSensitive ? text : text.toLowerCase();
        String searchKeyword = caseSensitive ? keyword : keyword.toLowerCase();

        if (!wholeWord) {
            // 简单包含匹配
            return searchText.contains(searchKeyword);
        }

        // 整词匹配：使用正则表达式
        // \b 表示单词边界
        String regex = "\\b" + java.util.regex.Pattern.quote(searchKeyword) + "\\b";
        int flags = caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex, flags);
        return pattern.matcher(text).find();
    }

    // 展开/收起所有节点
    private void expandAll(JTree tree, boolean expand) {
        TreeNode root = (TreeNode) tree.getModel().getRoot();
        expandAll(tree, new TreePath(root), expand);
    }

    private void expandAll(JTree tree, TreePath parent, boolean expand) {
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (int i = 0; i < node.getChildCount(); i++) {
                TreeNode n = node.getChildAt(i);
                TreePath path = parent.pathByAddingChild(n);
                expandAll(tree, path, expand);
            }
        }
        if (expand) {
            tree.expandPath(parent);
        } else {
            tree.collapsePath(parent);
        }
    }

}
