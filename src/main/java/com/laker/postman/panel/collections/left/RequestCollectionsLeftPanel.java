package com.laker.postman.panel.collections.left;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.SingletonBasePanel;
import com.laker.postman.common.SingletonFactory;
import com.laker.postman.common.component.tab.ClosableTabComponent;
import com.laker.postman.common.component.tree.RequestTreeCellRenderer;
import com.laker.postman.common.component.tree.TreeTransferHandler;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.model.HttpRequestItem;
import com.laker.postman.model.PreparedRequest;
import com.laker.postman.model.RequestItemProtocolEnum;
import com.laker.postman.model.Workspace;
import com.laker.postman.panel.collections.right.RequestEditPanel;
import com.laker.postman.panel.collections.right.request.RequestEditSubPanel;
import com.laker.postman.panel.collections.right.request.sub.RequestBodyPanel;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.service.collections.RequestCollectionsService;
import com.laker.postman.service.collections.RequestsPersistence;
import com.laker.postman.service.curl.CurlParser;
import com.laker.postman.service.http.HttpRequestFactory;
import com.laker.postman.service.http.PreparedRequestBuilder;
import com.laker.postman.service.postman.PostmanImport;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.NotificationUtil;
import com.laker.postman.util.SystemUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.laker.postman.service.collections.RequestsFactory.APPLICATION_JSON;
import static com.laker.postman.service.collections.RequestsFactory.CONTENT_TYPE;
import static com.laker.postman.service.http.HttpRequestFactory.*;

/**
 * 请求集合面板，展示所有请求分组和请求项
 * 支持请求的增删改查、分组管理、拖拽排序等功能
 */
@Slf4j
public class RequestCollectionsLeftPanel extends SingletonBasePanel {
    public static final String REQUEST = "request";
    public static final String GROUP = "group";
    public static final String ROOT = "root";
    public static final String EXPORT_FILE_NAME = "EasyPostman-Collections.json";
    // 请求集合的根节点
    @Getter
    private DefaultMutableTreeNode rootTreeNode;
    // 请求树组件
    @Getter
    private JTree requestTree;
    // 树模型，用于管理树节点
    @Getter
    private DefaultTreeModel treeModel;
    @Getter
    private transient RequestsPersistence persistence;

    @Override
    protected void initUI() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(200, 200));

        // 顶部面板，导入导出按钮在最上方，环境信息在下方
        JPanel topPanel = getTopPanel();
        add(topPanel, BorderLayout.NORTH);

        JScrollPane treeScrollPane = getTreeScrollPane();
        add(treeScrollPane, BorderLayout.CENTER);
    }

    private JScrollPane getTreeScrollPane() {
        // 初始化请求树
        rootTreeNode = new DefaultMutableTreeNode(ROOT);
        treeModel = new DefaultTreeModel(rootTreeNode);
        Workspace currentWorkspace = WorkspaceService.getInstance().getCurrentWorkspace();
        Path filePath = SystemUtil.getCollectionPathForWorkspace(currentWorkspace);
        // 初始化持久化工具
        persistence = new RequestsPersistence(filePath, rootTreeNode, treeModel);
        // 创建树组件
        requestTree = new JTree(treeModel) {
            @Override
            public boolean isPathEditable(TreePath path) {
                // 禁止根节点重命名
                Object node = path.getLastPathComponent();
                if (node instanceof DefaultMutableTreeNode treeNode) {
                    return treeNode.getParent() != null;
                }
                return false;
            }
        };
        // 不显示根节点
        requestTree.setRootVisible(false);
        // 让 JTree 组件显示根节点的"展开/收起"小三角（即树形结构的手柄）。
        requestTree.setShowsRootHandles(true);
        // 设置树支持多选（支持批量删除）
        requestTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        // 设置树的字体和行高
        requestTree.setCellRenderer(new RequestTreeCellRenderer());
        requestTree.setRowHeight(28);
        JScrollPane treeScrollPane = new JScrollPane(requestTree);
        treeScrollPane.getVerticalScrollBar().setUnitIncrement(16); // 设置滚动条增量
        treeScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        // 启用拖拽排序
        requestTree.setDragEnabled(true); // 启用拖拽
        requestTree.setDropMode(DropMode.ON_OR_INSERT); // 设置拖拽模式为插入
        requestTree.setTransferHandler(new TreeTransferHandler(requestTree, treeModel, this::saveRequestGroups));
        return treeScrollPane;
    }

    private JPanel getTopPanel() {
        return SingletonFactory.getInstance(LeftTopPanel.class);
    }


    @Override
    protected void registerListeners() {
        // 添加键盘监听器，支持 F2 快捷键重命名和 Delete 键删除
        requestTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                TreePath[] selectedPaths = requestTree.getSelectionPaths();
                if (selectedPaths == null || selectedPaths.length == 0) {
                    return;
                }

                boolean isMultipleSelection = selectedPaths.length > 1;
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) requestTree.getLastSelectedPathComponent();

                if (selectedNode != null && selectedNode != rootTreeNode) {
                    if (e.getKeyCode() == KeyEvent.VK_F2) {
                        // F2 重命名 - 仅支持单选
                        if (!isMultipleSelection) {
                            renameSelectedItem();
                        }
                    } else if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                        // Delete 或 Backspace 删除（Mac 上常用 Backspace） - 支持多选
                        deleteSelectedItem();
                    }
                }
            }
        });

        // 鼠标点击事件，右键弹出菜单 左键打开请求
        requestTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { // pressed 而不是 clicked，更加灵敏
                // 统一处理左键和右键点击
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) { // 左键单击 打开请求
                    int selRow = requestTree.getRowForLocation(e.getX(), e.getY());
                    TreePath selPath = requestTree.getPathForLocation(e.getX(), e.getY());
                    // 如果点击位置没有直接命中节点，则获取最近的行
                    if (selRow == -1 || selPath == null) {
                        selRow = requestTree.getClosestRowForLocation(e.getX(), e.getY());
                        if (selRow != -1) {
                            selPath = requestTree.getPathForRow(selRow);
                        }
                    }
                    if (selRow != -1 && selPath != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selPath.getLastPathComponent();
                        if (node.getUserObject() instanceof Object[] obj) {
                            if (REQUEST.equals(obj[0])) {
                                HttpRequestItem item = (HttpRequestItem) obj[1];
                                SingletonFactory.getInstance(RequestEditPanel.class).showOrCreateTab(item);
                            }
                        }
                    }
                }
                if (SwingUtilities.isRightMouseButton(e)) { // 右键点击 弹出菜单
                    int x = e.getX();
                    int y = e.getY();
                    int row = requestTree.getClosestRowForLocation(x, y);
                    if (row != -1) {
                        TreePath clickedPath = requestTree.getPathForRow(row);
                        // 如果右键点击的节点不在当前选中的节点中，则替换选择
                        // 如果已经在选中的节点中，则保持多选状态
                        if (clickedPath != null && !requestTree.isPathSelected(clickedPath)) {
                            requestTree.setSelectionRow(row);
                        }
                    } else {
                        requestTree.clearSelection(); // 没有节点时取消选中
                    }
                    showPopupMenu(x, y); // 无论是否有节点都弹出菜单
                }
            }

            private void showPopupMenu(int x, int y) {
                JPopupMenu menu = new JPopupMenu();
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) requestTree.getLastSelectedPathComponent();
                Object userObj = selectedNode != null ? selectedNode.getUserObject() : null;

                // 检查是否多选
                TreePath[] selectedPaths = requestTree.getSelectionPaths();
                boolean isMultipleSelection = selectedPaths != null && selectedPaths.length > 1;

                // 无论何时都提供"创建root分组"的选项
                JMenuItem addRootGroupItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_ADD_ROOT_GROUP),
                        new FlatSVGIcon("icons/group.svg", 16, 16));
                addRootGroupItem.addActionListener(e -> showAddGroupDialog(rootTreeNode));
                menu.add(addRootGroupItem);

                // 如果树为空或未选中任何节点，只显示根级别创建分组选项
                if (selectedNode == null || selectedNode == rootTreeNode) {
                    menu.show(requestTree, x, y);
                    return;
                }

                // 仅分组节点可新增文件/请求
                if (userObj instanceof Object[] && GROUP.equals(((Object[]) userObj)[0])) {
                    menu.addSeparator();

                    // 新增请求放在第一位（更高频的操作）
                    JMenuItem addRequestItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_ADD_REQUEST),
                            new FlatSVGIcon("icons/request.svg", 16, 16));
                    addRequestItem.addActionListener(e -> showAddRequestDialog(selectedNode));
                    // 多选时禁用
                    addRequestItem.setEnabled(!isMultipleSelection);
                    menu.add(addRequestItem);

                    // 新增分组放在第二位
                    JMenuItem addGroupItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_ADD_GROUP),
                            new FlatSVGIcon("icons/group.svg", 16, 16));
                    addGroupItem.addActionListener(e -> addGroupUnderSelected());
                    // 多选时禁用
                    addGroupItem.setEnabled(!isMultipleSelection);
                    menu.add(addGroupItem);

                    JMenuItem duplicateGroupItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_DUPLICATE),
                            new FlatSVGIcon("icons/duplicate.svg", 16, 16));
                    duplicateGroupItem.addActionListener(e -> duplicateSelectedGroup());
                    // 多选时禁用
                    duplicateGroupItem.setEnabled(!isMultipleSelection);
                    menu.add(duplicateGroupItem);

                    // 导出为Postman
                    JMenuItem exportPostmanItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_EXPORT_POSTMAN),
                            new FlatSVGIcon("icons/export.svg", 16, 16));
                    exportPostmanItem.addActionListener(e -> exportGroupAsPostman(selectedNode));
                    // 多选时禁用
                    exportPostmanItem.setEnabled(!isMultipleSelection);
                    menu.add(exportPostmanItem);

                    // 转移到其他工作区
                    JMenuItem moveToWorkspaceItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_MOVE_TO_WORKSPACE),
                            new FlatSVGIcon("icons/workspace.svg", 16, 16));
                    moveToWorkspaceItem.addActionListener(e -> moveCollectionToWorkspace(selectedNode));
                    // 多选时禁用
                    moveToWorkspaceItem.setEnabled(!isMultipleSelection);
                    menu.add(moveToWorkspaceItem);

                    menu.addSeparator();
                }
                // 请求节点右键菜单增加"复制"
                if (userObj instanceof Object[] && REQUEST.equals(((Object[]) userObj)[0])) {
                    JMenuItem duplicateItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_DUPLICATE),
                            new FlatSVGIcon("icons/duplicate.svg", 16, 16));
                    duplicateItem.addActionListener(e -> duplicateSelectedRequest());
                    // 多选时禁用
                    duplicateItem.setEnabled(!isMultipleSelection);
                    menu.add(duplicateItem);

                    // 复制为cURL命令
                    JMenuItem copyAsCurlItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_CURL),
                            new FlatSVGIcon("icons/curl.svg", 16, 16));
                    copyAsCurlItem.addActionListener(e -> copySelectedRequestAsCurl());
                    // 多选时禁用
                    copyAsCurlItem.setEnabled(!isMultipleSelection);
                    menu.add(copyAsCurlItem);
                    menu.addSeparator();
                }
                // 只有非根节点才显示重命名/删除
                if (selectedNode != rootTreeNode) {
                    JMenuItem renameItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_RENAME),
                            new FlatSVGIcon("icons/refresh.svg", 16, 16));
                    // 设置 F2 快捷键显示
                    renameItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
                    renameItem.addActionListener(e -> RequestCollectionsLeftPanel.this.renameSelectedItem());
                    // 多选时禁用重命名
                    renameItem.setEnabled(!isMultipleSelection);
                    menu.add(renameItem);

                    JMenuItem deleteItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_DELETE),
                            new FlatSVGIcon("icons/close.svg", 16, 16));
                    // 设置 Delete 快捷键显示
                    deleteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
                    deleteItem.addActionListener(e -> RequestCollectionsLeftPanel.this.deleteSelectedItem());
                    // 删除操作始终可用（支持多选）
                    menu.add(deleteItem);
                }
                menu.show(requestTree, x, y);
            }
        });


        SwingUtilities.invokeLater(() -> {  // 异步加载请求组
            persistence.initRequestGroupsFromFile(); // 从文件加载请求集合
            SwingUtilities.invokeLater(() -> {
                HttpRequestItem lastNonNewRequest = RequestCollectionsService.getLastNonNewRequest();
                // 恢复之前已打开请求
                RequestCollectionsService.restoreOpenedRequests();
                // 增加一个plusTab
                SingletonFactory.getInstance(RequestEditPanel.class).addPlusTab();
                // 反向定位到最后一个请求
                if (lastNonNewRequest != null) {
                    locateAndSelectRequest(lastNonNewRequest.getId());
                } else { // 没有请求时默认展开第一个组
                    if (rootTreeNode.getChildCount() > 0) {
                        DefaultMutableTreeNode firstGroup = (DefaultMutableTreeNode) rootTreeNode.getChildAt(0);
                        TreePath path = new TreePath(firstGroup.getPath());
                        requestTree.setSelectionPath(path);
                        requestTree.expandPath(path);
                    }
                }
            });
        });

    }

    private void showAddGroupDialog(DefaultMutableTreeNode rootTreeNode) {
        if (rootTreeNode == null) return; // safety guard
        String groupName = JOptionPane.showInputDialog(SingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_ADD_GROUP_PROMPT));
        if (groupName != null && !groupName.trim().isEmpty()) {
            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(new Object[]{GROUP, groupName});
            // Insert group before the first request child so that groups are always above requests (like Postman)
            int insertIdx = getGroupInsertIndex(rootTreeNode);
            if (insertIdx >= 0 && insertIdx <= rootTreeNode.getChildCount()) {
                rootTreeNode.insert(groupNode, insertIdx);
            } else {
                rootTreeNode.add(groupNode);
            }
            treeModel.reload(rootTreeNode);
            requestTree.expandPath(new TreePath(rootTreeNode.getPath()));
            persistence.saveRequestGroups();
        }
    }


    private void addGroupUnderSelected() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) requestTree.getLastSelectedPathComponent();
        if (selectedNode == null) return;
        showAddGroupDialog(selectedNode);
    }

    private void saveRequestGroups() {
        persistence.saveRequestGroups();
    }

    /**
     * 重命名选中的项（分组或请求）
     * 支持通过 F2 快捷键或右键菜单调用
     */
    private void renameSelectedItem() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) requestTree.getLastSelectedPathComponent();
        if (selectedNode == null) return;

        Object userObj = selectedNode.getUserObject();
        if (userObj instanceof Object[] obj) {
            if (GROUP.equals(obj[0])) {
                // 重命名分组
                Object result = JOptionPane.showInputDialog(
                        this,
                        I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_GROUP_PROMPT),
                        I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_GROUP_TITLE),
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        obj[1]
                );
                if (result != null) {
                    String newName = result.toString().trim();
                    if (!newName.isEmpty() && !newName.equals(obj[1])) {
                        obj[1] = newName;
                        treeModel.nodeChanged(selectedNode);
                        saveRequestGroups();
                    } else if (newName.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                this,
                                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_GROUP_EMPTY),
                                I18nUtil.getMessage(MessageKeys.GENERAL_TIP),
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                }
            } else if (REQUEST.equals(obj[0])) {
                // 重命名请求
                HttpRequestItem item = (HttpRequestItem) obj[1];
                String oldName = item.getName();
                Object result = JOptionPane.showInputDialog(
                        this,
                        I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_REQUEST_PROMPT),
                        I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_REQUEST_TITLE),
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        oldName
                );
                if (result != null) {
                    String newName = result.toString().trim();
                    if (!newName.isEmpty() && !newName.equals(oldName)) {
                        item.setName(newName);
                        treeModel.nodeChanged(selectedNode);
                        saveRequestGroups();

                        // 同步更新已打开Tab的标题
                        RequestEditPanel editPanel = SingletonFactory.getInstance(RequestEditPanel.class);
                        JTabbedPane tabbedPane = editPanel.getTabbedPane();
                        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                            Component comp = tabbedPane.getComponentAt(i);
                            if (comp instanceof RequestEditSubPanel subPanel) {
                                HttpRequestItem tabItem = subPanel.getCurrentRequest();
                                if (tabItem != null && item.getId().equals(tabItem.getId())) {
                                    tabbedPane.setTitleAt(i, newName);
                                    // 更新自定义标签组件
                                    tabbedPane.setTabComponentAt(i, new ClosableTabComponent(newName, item.getProtocol()));
                                    // 同步刷新内容
                                    subPanel.initPanelData(item);
                                }
                            }
                        }
                    } else if (newName.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                this,
                                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_REQUEST_EMPTY),
                                I18nUtil.getMessage(MessageKeys.GENERAL_TIP),
                                JOptionPane.WARNING_MESSAGE
                        );
                    }
                }
            }
        }
    }

    /**
     * 删除选中的项（分组或请求）
     * 支持批量删除多个选中项
     * 支持通过 Delete/Backspace 快捷键或右键菜单调用
     */
    private void deleteSelectedItem() {
        TreePath[] selectedPaths = requestTree.getSelectionPaths();
        if (selectedPaths == null || selectedPaths.length == 0) {
            return;
        }

        // 过滤掉根节点和没有父节点的节点
        List<DefaultMutableTreeNode> nodesToDelete = new ArrayList<>();
        for (TreePath path : selectedPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node != null && node != rootTreeNode && node.getParent() != null) {
                nodesToDelete.add(node);
            }
        }

        if (nodesToDelete.isEmpty()) {
            return;
        }

        // 删除前弹出确认提示
        String confirmMessage;
        if (nodesToDelete.size() == 1) {
            confirmMessage = I18nUtil.getMessage(MessageKeys.COLLECTIONS_DELETE_CONFIRM);
        } else {
            confirmMessage = I18nUtil.getMessage(MessageKeys.COLLECTIONS_DELETE_BATCH_CONFIRM, nodesToDelete.size());
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                confirmMessage,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DELETE_CONFIRM_TITLE),
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // 保存展开状态
        List<TreePath> expandedPaths = saveExpandedPaths();

        RequestEditPanel editPanel = SingletonFactory.getInstance(RequestEditPanel.class);
        JTabbedPane tabbedPane = editPanel.getTabbedPane();

        // 批量关闭相关Tab
        for (DefaultMutableTreeNode node : nodesToDelete) {
            Object userObj = node.getUserObject();
            if (userObj instanceof Object[] obj) {
                if (REQUEST.equals(obj[0])) {
                    // 删除请求：关闭所有与该请求id匹配的Tab
                    HttpRequestItem item = (HttpRequestItem) obj[1];
                    for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
                        Component comp = tabbedPane.getComponentAt(i);
                        if (comp instanceof RequestEditSubPanel subPanel) {
                            HttpRequestItem tabItem = subPanel.getCurrentRequest();
                            if (tabItem != null && item.getId().equals(tabItem.getId())) {
                                tabbedPane.remove(i);
                            }
                        }
                    }
                } else if (GROUP.equals(obj[0])) {
                    // 删除分组：递归关闭该组下所有请求Tab
                    closeTabsForGroup(node, tabbedPane);
                }
            }
        }

        // 批量删除树节点
        for (DefaultMutableTreeNode node : nodesToDelete) {
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent != null) {
                parent.remove(node);
            }
        }

        treeModel.reload();

        // 恢复展开状态
        restoreExpandedPaths(expandedPaths);

        // 调整Tab选中状态
        if (tabbedPane.getTabCount() > 1) {
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 2);
        }

        saveRequestGroups();
    }

    /**
     * 递归关闭分组下所有请求的Tab
     */
    private void closeTabsForGroup(DefaultMutableTreeNode groupNode, JTabbedPane tabbedPane) {
        for (int i = 0; i < groupNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) groupNode.getChildAt(i);
            Object userObj = child.getUserObject();
            if (userObj instanceof Object[] obj) {
                if (REQUEST.equals(obj[0])) {
                    HttpRequestItem item = (HttpRequestItem) obj[1];
                    for (int j = tabbedPane.getTabCount() - 1; j >= 0; j--) {
                        Component comp = tabbedPane.getComponentAt(j);
                        if (comp instanceof RequestEditSubPanel subPanel) {
                            HttpRequestItem tabItem = subPanel.getCurrentRequest();
                            if (tabItem != null && item.getId().equals(tabItem.getId())) {
                                tabbedPane.remove(j);
                            }
                        }
                    }
                } else if (GROUP.equals(obj[0])) {
                    closeTabsForGroup(child, tabbedPane);
                }
            }
        }
    }

    // 返回在 parent 下插入新分组的索引：分组应排在所有请求之上，因此插入到第一个请求位置前
    private int getGroupInsertIndex(DefaultMutableTreeNode parent) {
        if (parent == null) return -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object userObj = child.getUserObject();
            if (userObj instanceof Object[] obj && REQUEST.equals(obj[0])) {
                return i;
            }
        }
        // 没有请求，追加到末尾
        return parent.getChildCount();
    }

    /**
     * 将请求保存到指定分组
     *
     * @param group 分组信息，[type, name] 形式的数组
     * @param item  请求项
     */
    public void saveRequestToGroup(Object[] group, HttpRequestItem item) {
        if (group == null || !GROUP.equals(group[0])) {
            return;
        }
        DefaultMutableTreeNode groupNode = findGroupNode(rootTreeNode, (String) group[1]);
        if (groupNode == null) {
            return;
        }
        DefaultMutableTreeNode requestNode = new DefaultMutableTreeNode(new Object[]{REQUEST, item});
        groupNode.add(requestNode);
        treeModel.reload(groupNode);
        requestTree.expandPath(new TreePath(groupNode.getPath()));
        persistence.saveRequestGroups();
    }

    /**
     * 根据名称查找分组节点
     */
    public DefaultMutableTreeNode findGroupNode(DefaultMutableTreeNode node, String groupName) {
        if (node == null) return null;

        Object userObj = node.getUserObject();
        if (userObj instanceof Object[] obj && GROUP.equals(obj[0]) && groupName.equals(obj[1])) {
            return node;
        }


        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode result = findGroupNode(child, groupName);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * 更新已存在的请求
     *
     * @param item 请求项
     * @return 是否更新成功
     */
    public boolean updateExistingRequest(HttpRequestItem item) {
        if (item == null || item.getId() == null || item.getId().isEmpty()) {
            return false;
        }
        DefaultMutableTreeNode requestNode = RequestCollectionsService.findRequestNodeById(rootTreeNode, item.getId());
        if (requestNode == null) {
            return false;
        }
        Object[] userObj = (Object[]) requestNode.getUserObject();
        HttpRequestItem originalItem = (HttpRequestItem) userObj[1];
        String originalName = originalItem.getName();
        item.setName(originalName);
        userObj[1] = item;
        treeModel.nodeChanged(requestNode);
        persistence.saveRequestGroups();
        // 保存后去除Tab红点
        SwingUtilities.invokeLater(() -> {
            RequestEditPanel editPanel = SingletonFactory.getInstance(RequestEditPanel.class);
            JTabbedPane tabbedPane = editPanel.getTabbedPane();
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                Component comp = tabbedPane.getComponentAt(i);
                if (comp instanceof RequestEditSubPanel subPanel) {
                    HttpRequestItem tabItem = subPanel.getCurrentRequest();
                    if (tabItem != null && item.getId().equals(tabItem.getId())) {
                        editPanel.updateTabDirty(subPanel, false);
                        subPanel.setOriginalRequestItem(item);
                    }
                }
            }
        });
        return true;
    }


    /**
     * 获取分组树的 TreeModel（用于分组选择树）
     */
    public DefaultTreeModel getGroupTreeModel() {
        return treeModel;
    }

    // 复制请求方法
    private void duplicateSelectedRequest() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) requestTree.getLastSelectedPathComponent();
        if (selectedNode == null) return;
        Object userObj = selectedNode.getUserObject();
        if (userObj instanceof Object[] obj && REQUEST.equals(obj[0])) {
            HttpRequestItem item = (HttpRequestItem) obj[1];
            // 深拷贝请求项（假设HttpRequestItem有clone或可用JSON序列化实现深拷贝）
            HttpRequestItem copy = JSONUtil.toBean(JSONUtil.parse(item).toString(), HttpRequestItem.class);
            copy.setId(java.util.UUID.randomUUID().toString());
            copy.setName(item.getName() + " " + I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_SUFFIX));
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
            DefaultMutableTreeNode copyNode = new DefaultMutableTreeNode(new Object[]{REQUEST, copy});
            int idx = parent.getIndex(selectedNode) + 1;
            parent.insert(copyNode, idx);
            treeModel.reload(parent);
            requestTree.expandPath(new TreePath(parent.getPath()));
            persistence.saveRequestGroups();
        }
    }

    // 复制请求为cUrl方法
    private void copySelectedRequestAsCurl() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) requestTree.getLastSelectedPathComponent();
        if (selectedNode == null) return;
        Object userObj = selectedNode.getUserObject();
        if (userObj instanceof Object[] obj && REQUEST.equals(obj[0])) {
            HttpRequestItem item = (HttpRequestItem) obj[1];
            try {
                PreparedRequest req = PreparedRequestBuilder.build(item);
                // 对于 cURL 导出，直接进行变量替换（不需要前置脚本）
                PreparedRequestBuilder.replaceVariablesAfterPreScript(req);
                String curl = CurlParser.toCurl(req);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(curl), null); // 将cUrl命令复制到剪贴板
                NotificationUtil.showSuccess(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_CURL_SUCCESS));
            } catch (Exception ex) {
                NotificationUtil.showError(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_CURL_FAIL, ex.getMessage()));
            }
        }
    }

    private void duplicateSelectedGroup() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) requestTree.getLastSelectedPathComponent();
        if (selectedNode == null) return;
        Object userObj = selectedNode.getUserObject();
        if (userObj instanceof Object[] obj && GROUP.equals(obj[0])) {
            // 深拷贝分组及其所有子节点
            DefaultMutableTreeNode copyNode = deepCopyGroupNode(selectedNode);
            Object[] copyObj = (Object[]) copyNode.getUserObject();
            copyObj[1] = copyObj[1] + I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_SUFFIX);
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
            if (parent != null) {
                int idx = parent.getIndex(selectedNode) + 1;
                parent.insert(copyNode, idx);
                treeModel.reload(parent);
                requestTree.expandPath(new TreePath(parent.getPath()));
                persistence.saveRequestGroups();
            }
        }
    }

    // 深拷贝分组节点及其所有子节点
    private DefaultMutableTreeNode deepCopyGroupNode(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();
        Object[] obj = userObj instanceof Object[] ? ((Object[]) userObj).clone() : null;
        DefaultMutableTreeNode copy = new DefaultMutableTreeNode(obj);
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            Object childUserObj = child.getUserObject();
            if (childUserObj instanceof Object[] childObj) {
                if (GROUP.equals(childObj[0])) {
                    copy.add(deepCopyGroupNode(child));
                } else if (REQUEST.equals(childObj[0])) {
                    // 深拷贝请求节点
                    HttpRequestItem item = (HttpRequestItem) childObj[1];
                    HttpRequestItem copyItem = JSONUtil.toBean(JSONUtil.parse(item).toString(), HttpRequestItem.class);
                    copyItem.setId(java.util.UUID.randomUUID().toString());
                    Object[] reqObj = new Object[]{REQUEST, copyItem};
                    copy.add(new DefaultMutableTreeNode(reqObj));
                }
            }
        }
        return copy;
    }


    // 导出分组为Postman Collection
    private void exportGroupAsPostman(DefaultMutableTreeNode groupNode) {
        if (groupNode == null || !(groupNode.getUserObject() instanceof Object[] obj) || !GROUP.equals(obj[0])) {
            JOptionPane.showMessageDialog(this,
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_EXPORT_POSTMAN_SELECT_GROUP),
                    I18nUtil.getMessage(MessageKeys.GENERAL_TIP), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String groupName = String.valueOf(obj[1]);
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_EXPORT_POSTMAN_DIALOG_TITLE));
        fileChooser.setSelectedFile(new File(groupName + "-postman.json"));
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try {
                JSONObject postmanCollection = PostmanImport.buildPostmanCollectionFromTreeNode(groupNode, groupName);
                FileUtil.writeUtf8String(postmanCollection.toStringPretty(), fileToSave);
                NotificationUtil.showSuccess(I18nUtil.getMessage(MessageKeys.COLLECTIONS_EXPORT_SUCCESS));
            } catch (Exception ex) {
                log.error("Export Postman error", ex);
                JOptionPane.showMessageDialog(this,
                        I18nUtil.getMessage(MessageKeys.COLLECTIONS_EXPORT_FAIL, ex.getMessage()),
                        I18nUtil.getMessage(MessageKeys.GENERAL_ERROR), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 创建一个可多选的请求/分组选择树（用于Runner面板弹窗）
    public JTree createRequestSelectionTree() {
        DefaultTreeModel model = new DefaultTreeModel(cloneTreeNode(rootTreeNode));
        JTree tree = new JTree(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new RequestTreeCellRenderer());
        tree.setRowHeight(28);
        tree.setBackground(new Color(245, 247, 250));
        // 支持多选
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        return tree;
    }

    // 递归克隆树节点（只克隆结构和userObject，不共享引用）
    // 生成一份只读、临时的树结构，用于弹窗选择，保证主界面集合树的安全和稳定
    private DefaultMutableTreeNode cloneTreeNode(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();
        DefaultMutableTreeNode copy = new DefaultMutableTreeNode(userObj instanceof Object[] ? ((Object[]) userObj).clone() : userObj);
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            copy.add(cloneTreeNode(child));
        }
        return copy;
    }

    // 获取树中选中的所有请求（包含分组下所有请求）
    public List<HttpRequestItem> getSelectedRequestsFromTree(JTree tree) {
        List<HttpRequestItem> result = new ArrayList<>();
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) return result;
        for (TreePath path : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            collectRequestsRecursively(node, result);
        }
        // 去重（按id）
        Map<String, HttpRequestItem> map = new LinkedHashMap<>();
        for (HttpRequestItem item : result) {
            map.put(item.getId(), item);
        }
        return new ArrayList<>(map.values());
    }

    // 递归收集请求
    private void collectRequestsRecursively(DefaultMutableTreeNode node, List<HttpRequestItem> list) {
        Object userObj = node.getUserObject();
        if (userObj instanceof Object[] obj) {
            if (REQUEST.equals(obj[0])) {
                list.add((HttpRequestItem) obj[1]);
            } else if (GROUP.equals(obj[0])) {
                for (int i = 0; i < node.getChildCount(); i++) {
                    collectRequestsRecursively((DefaultMutableTreeNode) node.getChildAt(i), list);
                }
            }
        }
    }

    /**
     * 根据请求ID定位并选中树中的对应节点
     *
     * @param requestId 请求ID
     */
    public void locateAndSelectRequest(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return;
        }

        DefaultMutableTreeNode targetNode = RequestCollectionsService.findRequestNodeById(rootTreeNode, requestId);
        if (targetNode == null) { // 如果没有找到对应的请求节点
            return;
        }

        // 构建完整路径
        TreePath treePath = new TreePath(targetNode.getPath());

        // 展开父节点路径，确保目标节点可见
        requestTree.expandPath(treePath.getParentPath());

        // 选中目标节点
        requestTree.setSelectionPath(treePath);

        // 确保焦点在树上（用于突出显示）
        requestTree.requestFocusInWindow();
    }

    /**
     * 切换到指定工作区的请求集合文件，并刷新树UI
     */
    public void switchWorkspaceAndRefreshUI(Path collectionFilePath) {
        if (persistence != null) {
            persistence.setDataFilePath(collectionFilePath);
        }
        // 重新加载树结构
        treeModel.reload(rootTreeNode);
        // 清空 RequestEditPanel 中的请求
        SingletonFactory.getInstance(RequestEditPanel.class).getTabbedPane().removeAll();
        // 新增一个空白请求Tab
        SingletonFactory.getInstance(RequestEditPanel.class).addPlusTab();
    }

    /**
     * 转移集合到其他工作区
     */
    private void moveCollectionToWorkspace(DefaultMutableTreeNode selectedNode) {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof Object[] obj) || !GROUP.equals(obj[0])) {
            return;
        }

        String collectionName = String.valueOf(obj[1]);

        try {
            // 获取所有工作区
            WorkspaceService workspaceService = WorkspaceService.getInstance();
            List<Workspace> allWorkspaces = workspaceService.getAllWorkspaces();
            Workspace currentWorkspace = workspaceService.getCurrentWorkspace();

            // 过滤掉当前工作区
            List<Workspace> availableWorkspaces = allWorkspaces.stream()
                    .filter(w -> currentWorkspace == null || !w.getId().equals(currentWorkspace.getId()))
                    .toList();

            if (availableWorkspaces.isEmpty()) {
                JOptionPane.showMessageDialog(SingletonFactory.getInstance(MainFrame.class),
                        "没有其他可用的工作区",
                        I18nUtil.getMessage(MessageKeys.GENERAL_TIP),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 创建工作区选择对话框
            Workspace selectedWorkspace = showWorkspaceSelectionDialog(availableWorkspaces);
            if (selectedWorkspace == null) {
                return; // 用户取消选择
            }

            // 确认转移操作
            int confirm = JOptionPane.showConfirmDialog(SingletonFactory.getInstance(MainFrame.class),
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_MOVE_TO_WORKSPACE_CONFIRM,
                            collectionName, selectedWorkspace.getName()),
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_MOVE_TO_WORKSPACE_CONFIRM_TITLE),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            // 执行转移操作
            performCollectionMove(selectedNode, selectedWorkspace);

        } catch (Exception ex) {
            log.error("Move collection to workspace failed", ex);
            JOptionPane.showMessageDialog(SingletonFactory.getInstance(MainFrame.class),
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_MOVE_TO_WORKSPACE_FAIL, ex.getMessage()),
                    I18nUtil.getMessage(MessageKeys.GENERAL_ERROR),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 显示工作区选择对话框
     */
    private Workspace showWorkspaceSelectionDialog(List<Workspace> workspaces) {
        JDialog dialog = new JDialog(SingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_MOVE_TO_WORKSPACE_SELECT), true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(SingletonFactory.getInstance(MainFrame.class));
        dialog.setLayout(new BorderLayout());

        // 创建工作区列表
        DefaultListModel<Workspace> listModel = new DefaultListModel<>();
        for (Workspace workspace : workspaces) {
            listModel.addElement(workspace);
        }

        JList<Workspace> workspaceList = new JList<>(listModel);
        workspaceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        workspaceList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Workspace workspace) {
                    setText(workspace.getName());
                    setIcon(new FlatSVGIcon("icons/workspace.svg", 16, 16));
                    setToolTipText(workspace.getDescription());
                }
                return this;
            }
        });

        JScrollPane scrollPane = new JScrollPane(workspaceList);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton(I18nUtil.getMessage(MessageKeys.GENERAL_OK));
        JButton cancelButton = new JButton(I18nUtil.getMessage(MessageKeys.GENERAL_CANCEL));

        final Workspace[] selectedWorkspace = {null};

        okButton.addActionListener(e -> {
            Workspace selected = workspaceList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog,
                        "请选择一个工作区",
                        I18nUtil.getMessage(MessageKeys.GENERAL_TIP),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            selectedWorkspace[0] = selected;
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return selectedWorkspace[0];
    }

    /**
     * 执行集合转移操作
     */
    private void performCollectionMove(DefaultMutableTreeNode collectionNode, Workspace targetWorkspace) {
        // 1. 深拷贝集合节点（包含所有子节点）
        DefaultMutableTreeNode copiedNode = deepCopyGroupNode(collectionNode);

        // 2. 获取目标工作区的集合文件路径
        Path targetCollectionPath = SystemUtil.getCollectionPathForWorkspace(targetWorkspace);

        // 3. 创建目标工作区的持久化工具
        DefaultMutableTreeNode targetRootNode = new DefaultMutableTreeNode(ROOT);
        DefaultTreeModel targetTreeModel = new DefaultTreeModel(targetRootNode);
        RequestsPersistence targetPersistence = new RequestsPersistence(
                targetCollectionPath, targetRootNode, targetTreeModel);

        // 4. 加载目标工作区的现有集合
        targetPersistence.initRequestGroupsFromFile();

        // 5. 将集合添加到目标工作区
        targetRootNode.add(copiedNode);

        // 6. 保存到目标工作区
        targetPersistence.saveRequestGroups();

        // 7. 从当前工作区删除原集合
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) collectionNode.getParent();
        if (parent != null) {
            parent.remove(collectionNode);
            treeModel.reload();
            persistence.saveRequestGroups();
        }

        log.info("Successfully moved collection '{}' to workspace '{}'",
                ((Object[]) collectionNode.getUserObject())[1], targetWorkspace.getName());
    }

    /**
     * 显示添加请求的对话框，包含协议选择和名称输入
     */
    private void showAddRequestDialog(DefaultMutableTreeNode groupNode) {
        JDialog dialog = new JDialog(SingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_ADD_REQUEST_TITLE), true);
        dialog.setSize(400, 260);
        dialog.setLocationRelativeTo(SingletonFactory.getInstance(MainFrame.class));
        dialog.setLayout(new BorderLayout());

        // 主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        // 请求名称输入
        JPanel namePanel = new JPanel(new BorderLayout(10, 5));
        JLabel nameLabel = new JLabel(I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_ADD_REQUEST_NAME));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        JTextField nameField = new JTextField();
        nameField.setPreferredSize(new Dimension(0, 30));
        namePanel.add(nameLabel, BorderLayout.NORTH);
        namePanel.add(nameField, BorderLayout.CENTER);

        // 协议类型选择优化为水平排列的卡片式按钮
        JPanel protocolPanel = new JPanel();
        protocolPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        protocolPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_ADD_REQUEST_PROTOCOL)
        ));

        // 创建单选按钮组
        ButtonGroup protocolGroup = new ButtonGroup();
        JToggleButton httpBtn = new JToggleButton("HTTP");
        httpBtn.setIcon(new FlatSVGIcon("icons/http.svg", 24, 24));
        httpBtn.setSelected(true);
        httpBtn.setFocusPainted(false);
        httpBtn.setFont(FontsUtil.getDefaultFont(Font.PLAIN, 11));
        httpBtn.setVerticalTextPosition(SwingConstants.BOTTOM);
        httpBtn.setHorizontalTextPosition(SwingConstants.CENTER);
        httpBtn.setPreferredSize(new Dimension(100, 60));

        JToggleButton wsBtn = new JToggleButton("WebSocket");
        wsBtn.setIcon(new FlatSVGIcon("icons/websocket.svg", 24, 24));
        wsBtn.setFocusPainted(false);
        wsBtn.setVerticalTextPosition(SwingConstants.BOTTOM);
        wsBtn.setHorizontalTextPosition(SwingConstants.CENTER);
        wsBtn.setFont(FontsUtil.getDefaultFont(Font.PLAIN, 11));
        wsBtn.setPreferredSize(new Dimension(100, 60));

        JToggleButton sseBtn = new JToggleButton("SSE");
        sseBtn.setIcon(new FlatSVGIcon("icons/sse.svg", 24, 24));
        sseBtn.setFocusPainted(false);
        sseBtn.setFont(FontsUtil.getDefaultFont(Font.PLAIN, 11));
        sseBtn.setVerticalTextPosition(SwingConstants.BOTTOM);
        sseBtn.setHorizontalTextPosition(SwingConstants.CENTER);
        sseBtn.setPreferredSize(new Dimension(100, 60));

        protocolGroup.add(httpBtn);
        protocolGroup.add(wsBtn);
        protocolGroup.add(sseBtn);

        protocolPanel.add(httpBtn);
        protocolPanel.add(wsBtn);
        protocolPanel.add(sseBtn);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton(I18nUtil.getMessage(MessageKeys.GENERAL_OK));
        JButton cancelButton = new JButton(I18nUtil.getMessage(MessageKeys.GENERAL_CANCEL));

        okButton.addActionListener(e -> {
            String requestName = nameField.getText().trim();
            if (requestName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_ADD_REQUEST_NAME_EMPTY),
                        I18nUtil.getMessage(MessageKeys.GENERAL_TIP),
                        JOptionPane.WARNING_MESSAGE);
                nameField.requestFocus();
                return;
            }

            // 确定选择的协议类型
            RequestItemProtocolEnum protocol;
            if (httpBtn.isSelected()) {
                protocol = RequestItemProtocolEnum.HTTP;
            } else if (wsBtn.isSelected()) {
                protocol = RequestItemProtocolEnum.WEBSOCKET;
            } else if (sseBtn.isSelected()) {
                protocol = RequestItemProtocolEnum.SSE;
            } else {
                protocol = RequestItemProtocolEnum.HTTP; // 默认为HTTP
            }

            // 创建请求
            createNewRequest(groupNode, protocol, requestName);
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // 组装对话框
        mainPanel.add(namePanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(protocolPanel);

        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // 设置默认焦点和按钮
        dialog.getRootPane().setDefaultButton(okButton);
        SwingUtilities.invokeLater(nameField::requestFocus);

        dialog.setVisible(true);
    }

    /**
     * 创建新请求并添加到指定分组
     */
    private void createNewRequest(DefaultMutableTreeNode groupNode, RequestItemProtocolEnum protocol, String requestName) {
        if (groupNode == null) return; // safety guard
        // 创建默认请求
        HttpRequestItem defaultRequest = HttpRequestFactory.createDefaultRequest();
        defaultRequest.setProtocol(protocol);
        defaultRequest.setName(requestName);
        defaultRequest.getHeaders().put(USER_AGENT, EASY_POSTMAN_CLIENT);

        // 根据协议类型设置不同的默认值
        defaultRequest.setMethod("GET");
        defaultRequest.setUrl("");
        if (protocol.isWebSocketProtocol()) {
            // WebSocket 默认配置
            defaultRequest.setMethod("GET"); // WebSocket连接都是GET
            defaultRequest.getHeaders().put(CONTENT_TYPE, APPLICATION_JSON);
            defaultRequest.setBodyType(RequestBodyPanel.BODY_TYPE_RAW);
            defaultRequest.getHeaders().put(ACCEPT_ENCODING, "identity");
        } else if (protocol.isSseProtocol()) {
            // SSE 默认配置
            defaultRequest.setMethod("GET"); // SSE通常使用GET
            defaultRequest.getHeaders().put(ACCEPT, TEXT_EVENT_STREAM);
            defaultRequest.getHeaders().put(ACCEPT_ENCODING, "identity");
        } else {
            // HTTP 默认配置
            defaultRequest.setMethod("GET");
        }

        // 添加到树中
        DefaultMutableTreeNode reqNode = new DefaultMutableTreeNode(new Object[]{REQUEST, defaultRequest});
        groupNode.add(reqNode);
        treeModel.reload(groupNode);
        requestTree.expandPath(new TreePath(groupNode.getPath()));
        persistence.saveRequestGroups();

        // 自动打开新创建的请求
        SingletonFactory.getInstance(RequestEditPanel.class).showOrCreateTab(defaultRequest);
    }

    /**
     * 保存所有展开的路径
     * @return 所有展开路径的列表
     */
    private List<TreePath> saveExpandedPaths() {
        List<TreePath> expandedPaths = new ArrayList<>();
        int rowCount = requestTree.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            TreePath path = requestTree.getPathForRow(i);
            if (path != null && requestTree.isExpanded(path)) {
                expandedPaths.add(path);
            }
        }
        return expandedPaths;
    }

    /**
     * 恢复展开的路径
     * 根据节点的userObject匹配来恢复展开状态
     * @param expandedPaths 之前保存的展开路径列表
     */
    private void restoreExpandedPaths(List<TreePath> expandedPaths) {
        if (expandedPaths == null || expandedPaths.isEmpty()) {
            return;
        }

        for (TreePath oldPath : expandedPaths) {
            // 根据路径中的节点userObject找到新的路径
            TreePath newPath = findMatchingPath(oldPath);
            if (newPath != null) {
                requestTree.expandPath(newPath);
            }
        }
    }

    /**
     * 根据旧路径的节点userObject查找匹配的新路径
     * @param oldPath 旧的树路径
     * @return 匹配的新路径，如果未找到返回null
     */
    private TreePath findMatchingPath(TreePath oldPath) {
        Object[] oldNodes = oldPath.getPath();
        if (oldNodes.length == 0) {
            return null;
        }

        // 从根节点开始匹配
        DefaultMutableTreeNode currentNode = rootTreeNode;
        List<DefaultMutableTreeNode> matchedNodes = new ArrayList<>();
        matchedNodes.add(currentNode);

        // 跳过根节点，从第一个子节点开始匹配
        for (int i = 1; i < oldNodes.length; i++) {
            DefaultMutableTreeNode oldNode = (DefaultMutableTreeNode) oldNodes[i];
            Object oldUserObj = oldNode.getUserObject();

            // 在当前节点的子节点中查找匹配的节点
            DefaultMutableTreeNode matchedChild = null;
            for (int j = 0; j < currentNode.getChildCount(); j++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) currentNode.getChildAt(j);
                if (isSameNode(child.getUserObject(), oldUserObj)) {
                    matchedChild = child;
                    break;
                }
            }

            if (matchedChild == null) {
                // 没有找到匹配的子节点，停止匹配
                return null;
            }

            matchedNodes.add(matchedChild);
            currentNode = matchedChild;
        }

        return new TreePath(matchedNodes.toArray());
    }

    /**
     * 判断两个节点的userObject是否表示同一个节点
     * @param obj1 第一个userObject
     * @param obj2 第二个userObject
     * @return 是否表示同一个节点
     */
    private boolean isSameNode(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }

        // 根节点比较
        if (ROOT.equals(obj1) && ROOT.equals(obj2)) {
            return true;
        }

        // 数组类型节点比较
        if (obj1 instanceof Object[] arr1 && obj2 instanceof Object[] arr2) {
            if (arr1.length < 2 || arr2.length < 2) {
                return false;
            }

            String type1 = (String) arr1[0];
            String type2 = (String) arr2[0];

            if (!type1.equals(type2)) {
                return false;
            }

            if (GROUP.equals(type1)) {
                // 分组节点按名称比较
                return arr1[1].equals(arr2[1]);
            } else if (REQUEST.equals(type1)) {
                // 请求节点按ID比较
                HttpRequestItem item1 = (HttpRequestItem) arr1[1];
                HttpRequestItem item2 = (HttpRequestItem) arr2[1];
                return item1.getId().equals(item2.getId());
            }
        }

        return false;
    }
}
