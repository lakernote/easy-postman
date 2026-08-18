package com.laker.postman.panel.collections.tree.handler;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.panel.mock.MockServerPanel;
import com.laker.postman.panel.sidebar.SidebarTab;
import com.laker.postman.panel.sidebar.SidebarTabPanel;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.panel.collections.tree.coordinator.RequestTreeCoordinator;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;

import static com.laker.postman.panel.collections.tree.CollectionTreePanel.*;

/**
 * 请求树右键弹出菜单
 */
public class RequestTreePopupMenu {
    private final JTree requestTree;
    private final CollectionTreePanel leftPanel;
    private final RequestTreeCoordinator coordinator;

    public RequestTreePopupMenu(JTree requestTree, CollectionTreePanel leftPanel) {
        this(requestTree, leftPanel, new RequestTreeCoordinator(requestTree, leftPanel));
    }

    RequestTreePopupMenu(JTree requestTree, CollectionTreePanel leftPanel, RequestTreeCoordinator coordinator) {
        this.requestTree = requestTree;
        this.leftPanel = leftPanel;
        this.coordinator = coordinator;
    }

    /**
     * 显示弹出菜单
     */
    public void show(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) requestTree.getLastSelectedPathComponent();
        TreePath[] selectedPaths = requestTree.getSelectionPaths();
        boolean isMultipleSelection = selectedPaths != null && selectedPaths.length > 1;


        if (selectedNode == null || selectedNode == leftPanel.getRootTreeNode()) {
            menu.show(requestTree, x, y);
            return;
        }

        // 分组节点菜单
        if (CollectionTreeNodes.isGroup(selectedNode)) {
            addGroupMenuItems(menu, selectedNode, isMultipleSelection);
        }

        // 请求节点菜单
        if (CollectionTreeNodes.isRequest(selectedNode)) {
            addRequestMenuItems(menu, selectedNode, isMultipleSelection);
        }

        // 保存的响应节点菜单 - 只显示重命名和删除，不显示粘贴等其他选项
        if (CollectionTreeNodes.isSavedResponse(selectedNode)) {
            addRenameAndDeleteMenuItems(menu, isMultipleSelection);
            menu.show(requestTree, x, y);
            return;
        }

        // 粘贴选项
        if (!coordinator.isCopiedRequestsEmpty()) {
            addPasteMenuItem(menu);
        }

        // 重命名和删除（非根节点）
        if (selectedNode != leftPanel.getRootTreeNode()) {
            addRenameAndDeleteMenuItems(menu, isMultipleSelection);
        }

        menu.show(requestTree, x, y);
    }


    /**
     * 添加分组相关菜单项
     */
    private void addGroupMenuItems(JPopupMenu menu, DefaultMutableTreeNode selectedNode, boolean isMultipleSelection) {

        // 添加到功能测试
        JMenuItem addToFunctional = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_ADD_TO_FUNCTIONAL,
                "icons/functional.svg",
                e -> coordinator.addSelectedRequestsToFunctionalTest()
        );
        menu.add(addToFunctional);

        JMenuItem createMockServer = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_CREATE_MOCK_SERVER,
                "icons/mock-server.svg",
                e -> openMockServerForCollection(selectedNode)
        );
        createMockServer.setEnabled(!isMultipleSelection);
        menu.add(createMockServer);
        menu.addSeparator();

        // 新增请求
        JMenuItem addRequest = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_ADD_REQUEST,
                "icons/request.svg",
                e -> coordinator.showAddRequestDialog(selectedNode)
        );
        addRequest.setEnabled(!isMultipleSelection);
        menu.add(addRequest);

        // 新增分组
        JMenuItem addGroup = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_ADD_GROUP,
                "icons/group.svg",
                e -> coordinator.addGroupUnderSelected()
        );
        addGroup.setEnabled(!isMultipleSelection);
        menu.add(addGroup);

        // 复制分组
        JMenuItem duplicate = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_DUPLICATE,
                "icons/duplicate.svg",
                e -> coordinator.duplicateSelectedGroup()
        );
        duplicate.setEnabled(!isMultipleSelection);
        menu.add(duplicate);

        // 导出为 Postman
        JMenuItem exportPostman = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_EXPORT_POSTMAN,
                "icons/postman.svg",
                e -> coordinator.exportGroupAsPostman(selectedNode)
        );
        exportPostman.setEnabled(!isMultipleSelection);
        menu.add(exportPostman);

        // 转移到其他工作区
        JMenuItem moveToWorkspace = createMenuItem(
                MessageKeys.WORKSPACE_TRANSFER_MENU_ITEM,
                "icons/workspace.svg",
                e -> coordinator.moveCollectionToWorkspace(selectedNode)
        );
        moveToWorkspace.setEnabled(!isMultipleSelection);
        menu.add(moveToWorkspace);

        menu.addSeparator();
    }

    /**
     * 添加请求相关菜单项
     */
    private void addRequestMenuItems(JPopupMenu menu, DefaultMutableTreeNode selectedNode, boolean isMultipleSelection) {
        // 添加到功能测试
        JMenuItem addToFunctional = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_ADD_TO_FUNCTIONAL,
                "icons/functional.svg",
                e -> coordinator.addSelectedRequestsToFunctionalTest()
        );
        menu.add(addToFunctional);

        JMenuItem addMockResponse = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_ADD_MOCK_RESPONSE,
                "icons/mock-server.svg",
                e -> addMockResponse(selectedNode)
        );
        addMockResponse.setEnabled(!isMultipleSelection);
        menu.add(addMockResponse);
        menu.addSeparator();

        // 新增请求（在同级分组下）
        DefaultMutableTreeNode parentGroup = (DefaultMutableTreeNode) selectedNode.getParent();
        if (parentGroup != null) {
            JMenuItem addRequest = createMenuItem(
                    MessageKeys.COLLECTIONS_MENU_ADD_REQUEST,
                    "icons/request.svg",
                    e -> coordinator.showAddRequestDialog(parentGroup)
            );
            addRequest.setEnabled(!isMultipleSelection);
            menu.add(addRequest);
        }

        // 复制（创建副本）
        JMenuItem duplicate = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_DUPLICATE,
                "icons/duplicate.svg",
                e -> coordinator.duplicateSelectedRequests()
        );
        menu.add(duplicate);

        // 复制到剪贴板
        JMenuItem copy = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_COPY,
                "icons/copy.svg",
                e -> coordinator.copySelectedRequests()
        );
        int cmdMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, cmdMask));
        menu.add(copy);

        // 复制为 cURL
        JMenuItem copyAsCurl = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_COPY_CURL,
                "icons/curl.svg",
                e -> coordinator.copySelectedRequestAsCurl()
        );
        copyAsCurl.setEnabled(!isMultipleSelection);
        menu.add(copyAsCurl);
        menu.addSeparator();
    }


    /**
     * 添加粘贴菜单项
     */
    private void addPasteMenuItem(JPopupMenu menu) {
        JMenuItem paste = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_PASTE,
                "icons/paste.svg",
                e -> coordinator.pasteRequests()
        );
        int cmdMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        paste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, cmdMask));
        menu.add(paste);
        menu.addSeparator();
    }

    /**
     * 添加重命名和删除菜单项
     */
    private void addRenameAndDeleteMenuItems(JPopupMenu menu, boolean isMultipleSelection) {
        // 重命名
        JMenuItem rename = createMenuItem(
                MessageKeys.COLLECTIONS_MENU_RENAME,
                "icons/edit.svg",
                e -> coordinator.renameSelectedItem()
        );
        rename.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        rename.setEnabled(!isMultipleSelection);
        menu.add(rename);

        // 删除（显示选中数量）
        TreePath[] selectedPaths = requestTree.getSelectionPaths();
        int selectedCount = selectedPaths != null ? selectedPaths.length : 0;

        String deleteText = I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_DELETE);
        if (selectedCount > 1) {
            deleteText += " (" + selectedCount + ")";
        }

        JMenuItem delete = new JMenuItem(deleteText,
                IconUtil.createThemed("icons/close.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        delete.addActionListener(e -> coordinator.deleteSelectedItem());
        delete.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        menu.add(delete);
    }

    /**
     * 创建菜单项的辅助方法（主题适配）
     */
    private JMenuItem createMenuItem(String messageKey, String iconPath, java.awt.event.ActionListener listener) {
        // 彩色图标列表：这些图标本身有品牌色或渐变色，不应该跟随主题变化
        // - postman.svg: Postman 品牌橙色
        // - group.svg: 蓝色文件夹 (#007AFF)
        // - curl.svg: cURL 官方深蓝/绿色符号
        boolean isColoredIcon = iconPath.contains("postman.svg")
                             || iconPath.contains("group.svg")
                             || iconPath.contains("curl.svg");

        JMenuItem item = new JMenuItem(
                I18nUtil.getMessage(messageKey),
                isColoredIcon
                    ? IconUtil.create(iconPath, IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL)
                    : IconUtil.createThemed(iconPath, IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL)
        );
        item.addActionListener(listener);
        return item;
    }

    private void openMockServerForCollection(DefaultMutableTreeNode node) {
        RequestGroup collection = rootCollection(node);
        if (collection == null) return;
        SidebarTabPanel sidebar = UiSingletonFactory.getInstance(SidebarTabPanel.class);
        if (!sidebar.showTab(SidebarTab.MOCK_SERVER)) return;
        MockServerPanel mockPanel = UiSingletonFactory.getInstance(MockServerPanel.class);
        SwingUtilities.invokeLater(() -> mockPanel.createServerForCollection(collection.getId()));
    }

    private void addMockResponse(DefaultMutableTreeNode requestNode) {
        RequestGroup collection = rootCollection(requestNode);
        String requestId = CollectionTreeNodes.request(requestNode).map(item -> item.getId()).orElse(null);
        if (collection == null || requestId == null) return;
        SidebarTabPanel sidebar = UiSingletonFactory.getInstance(SidebarTabPanel.class);
        if (!sidebar.showTab(SidebarTab.MOCK_SERVER)) return;
        MockServerPanel mockPanel = UiSingletonFactory.getInstance(MockServerPanel.class);
        SwingUtilities.invokeLater(() -> mockPanel.addMockResponseForRequest(collection.getId(), requestId));
    }

    private RequestGroup rootCollection(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode current = node;
        while (current != null && current.getParent() != leftPanel.getRootTreeNode()) {
            current = (DefaultMutableTreeNode) current.getParent();
        }
        return CollectionTreeNodes.group(current).orElse(null);
    }
}
