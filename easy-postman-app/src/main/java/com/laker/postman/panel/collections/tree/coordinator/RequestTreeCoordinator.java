package com.laker.postman.panel.collections.tree.coordinator;

import com.laker.postman.common.component.notification.NotificationCenter;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.model.Workspace;
import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.request.model.HttpRequestItem;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.dialog.TextInputDialog;
import com.laker.postman.common.component.tab.ClosableTabComponent;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.panel.collections.tree.action.TreeNodeCloner;
import com.laker.postman.panel.collections.tree.action.TreeStateHelper;
import com.laker.postman.panel.collections.tree.dialog.AddRequestDialog;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.panel.collections.editor.request.RequestEditSubPanel;
import com.laker.postman.panel.functional.FunctionalPanel;
import com.laker.postman.panel.sidebar.SidebarTabPanel;
import com.laker.postman.panel.collections.tree.adapter.SwingCollectionTreePersistence;
import com.laker.postman.service.curl.CurlParser;
import com.laker.postman.http.request.HttpRequestFactory;
import com.laker.postman.http.request.PreparedRequestFactory;
import com.laker.postman.http.request.PreparedRequestFinalizer;
import com.laker.postman.service.postman.PostmanCollectionExporter;
import com.laker.postman.panel.workspace.WorkspaceTransferCoordinator;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.util.*;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;


import static com.laker.postman.panel.collections.tree.CollectionTreePanel.*;

/**
 * 请求树交互协调器。
 * 集中编排树节点变更、对话框、持久化和相关面板联动。
 */
@Slf4j
public class RequestTreeCoordinator {
    private final JTree requestTree;
    private final CollectionTreePanel leftPanel;
    private final List<HttpRequestItem> copiedRequests = new ArrayList<>();

    /**
     * 待保护的树路径：addHttpRequestDirectly 创建新请求后设置此值。
     * 仅在本次鼠标点击内防止 BasicTreeUI 覆盖新请求的选中状态，
     * mouseReleased 后由 finishRequestCreationSelection 清除。
     */
    private TreePath pendingSelectPath = null;

    public RequestTreeCoordinator(JTree requestTree, CollectionTreePanel leftPanel) {
        this.requestTree = requestTree;
        this.leftPanel = leftPanel;
        // 注册选中守卫：只要 pendingSelectPath 不为 null，就拦截任何外部对选中状态的覆盖
        requestTree.addTreeSelectionListener(e -> {
            TreePath guard = pendingSelectPath;
            if (guard == null) return;
            TreePath current = requestTree.getSelectionPath();
            if (!guard.equals(current)) {
                // BasicTreeUI 或其他代码把选中改掉了，立刻纠正回来
                pendingSelectPath = null; // 先清除，避免递归
                requestTree.setSelectionPath(guard);
                requestTree.scrollPathToVisible(guard);
            }
        });
    }

    /**
     * 显示添加分组对话框
     */
    public void showAddGroupDialog(DefaultMutableTreeNode parentNode) {
        if (!canContainGroup(parentNode)) return;

        TextInputDialog.showRequiredName(
                UiSingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_ADD_GROUP_TITLE),
                "",
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_GROUP_EMPTY)
        ).ifPresent(groupName -> addGroupToNode(parentNode, groupName));
    }

    /**
     * 在指定节点下添加分组
     */
    private void addGroupToNode(DefaultMutableTreeNode parentNode, String groupName) {
        if (!canContainGroup(parentNode)) return;
        RequestGroup group = new RequestGroup(groupName);
        // 参考 Postman：第一层 Collection 默认 No Auth，子层 Folder 默认 Inherit auth from parent
        boolean isRootLevel = ROOT.equals(String.valueOf(parentNode.getUserObject()));
        if (isRootLevel) {
            group.setAuthType(AuthType.NONE.getConstant());
        } else {
            group.setAuthType(AuthType.INHERIT.getConstant());
        }
        DefaultMutableTreeNode groupNode = CollectionTreeNodes.groupNode(group);

        int insertIdx = getGroupInsertIndex(parentNode);
        if (insertIdx >= 0 && insertIdx <= parentNode.getChildCount()) {
            parentNode.insert(groupNode, insertIdx);
        } else {
            parentNode.add(groupNode);
        }

        leftPanel.getTreeModel().reload(parentNode);
        requestTree.expandPath(new TreePath(parentNode.getPath()));

        leftPanel.getCollectionTreePersistence().saveCurrentTree();
    }

    private boolean canContainGroup(DefaultMutableTreeNode node) {
        return node != null && (CollectionTreeNodes.isGroup(node)
                || node == requestTree.getModel().getRoot()
                || (leftPanel != null && node == leftPanel.getRootTreeNode()));
    }

    /**
     * 在选中节点下添加分组
     */
    public void addGroupUnderSelected() {
        DefaultMutableTreeNode selectedNode = getSelectedNode();
        if (selectedNode == null) return;
        showAddGroupDialog(selectedNode);
    }

    /**
     * 显示添加请求对话框
     */
    public void showAddRequestDialog(DefaultMutableTreeNode groupNode) {
        if (!CollectionTreeNodes.isGroup(groupNode)) return;
        AddRequestDialog dialog = new AddRequestDialog(groupNode, leftPanel);
        dialog.show();
    }

    /**
     * 直接在指定分组下创建一个默认 HTTP GET 请求，不弹对话框（类似 Postman 点击 "+" 的行为）
     */
    public void addHttpRequestDirectly(DefaultMutableTreeNode groupNode) {
        if (!CollectionTreeNodes.isGroup(groupNode)) return;
        HttpRequestItem item = HttpRequestFactory.createBlankRequest(RequestItemProtocolEnum.HTTP);
        item.setName("New Request");
        DefaultMutableTreeNode reqNode = CollectionTreeNodes.requestNode(item);
        leftPanel.getTreeModel().insertNodeInto(reqNode, groupNode, groupNode.getChildCount());
        JTree tree = leftPanel.getRequestTree();
        tree.expandPath(new TreePath(groupNode.getPath()));
        leftPanel.getCollectionTreePersistence().saveCurrentTree();

        TreePath newPath = new TreePath(reqNode.getPath());
        // 设置保护路径：TreeSelectionListener 会拦截 BasicTreeUI 在 mouseReleased 里
        // 触发的 selectPathForEvent，保证最终选中停留在新请求节点上
        pendingSelectPath = newPath;
        UiSingletonFactory.getInstance(RequestEditorPanel.class).showOrCreateTab(item);
        tree.setSelectionPath(newPath);
        tree.scrollPathToVisible(newPath);
    }

    /**
     * 完成本次新增请求的选中保护，避免影响下一次点击的文件夹或请求。
     */
    public void finishRequestCreationSelection() {
        TreePath newPath = pendingSelectPath;
        pendingSelectPath = null;
        if (newPath != null) {
            requestTree.setSelectionPath(newPath);
            requestTree.scrollPathToVisible(newPath);
        }
    }


    /**
     * 重命名选中的项（分组、请求或保存的响应）
     */
    public void renameSelectedItem() {
        DefaultMutableTreeNode selectedNode = getSelectedNode();
        if (selectedNode == null) return;

        if (CollectionTreeNodes.isGroup(selectedNode)) {
            renameGroup(selectedNode);
        } else if (CollectionTreeNodes.isRequest(selectedNode)) {
            renameRequest(selectedNode);
        } else if (CollectionTreeNodes.isSavedResponse(selectedNode)) {
            renameSavedResponse(selectedNode);
        }
    }

    /**
     * 重命名分组
     */
    private void renameGroup(DefaultMutableTreeNode selectedNode) {
        RequestGroup group = CollectionTreeNodes.group(selectedNode).orElse(null);
        if (group == null) {
            return;
        }
        String oldName = group.getName();

        TextInputDialog.showRequiredName(
                UiSingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_GROUP_TITLE),
                oldName,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_GROUP_EMPTY)
        ).ifPresent(newName -> {
            if (newName.equals(oldName)) {
                return;
            }
            updateGroupName(selectedNode, group, newName);
        });
    }

    /**
     * 更新分组名称
     */
    private void updateGroupName(DefaultMutableTreeNode node, RequestGroup group, String newName) {
        group.setName(newName);
        leftPanel.getTreeModel().nodeChanged(node);

        leftPanel.getCollectionTreePersistence().saveCurrentTree();
    }

    /**
     * 重命名请求
     */
    private void renameRequest(DefaultMutableTreeNode selectedNode) {
        HttpRequestItem item = CollectionTreeNodes.request(selectedNode).orElse(null);
        if (item == null) {
            return;
        }
        String oldName = item.getName();

        TextInputDialog.showRequiredName(
                UiSingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_REQUEST_TITLE),
                oldName,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_REQUEST_EMPTY)
        ).ifPresent(newName -> {
            if (newName.equals(oldName)) {
                return;
            }
            updateRequestName(selectedNode, item, newName);
        });
    }

    /**
     * 更新请求名称
     */
    private void updateRequestName(DefaultMutableTreeNode node, HttpRequestItem item, String newName) {
        item.setName(newName);
        leftPanel.getTreeModel().nodeChanged(node);
        leftPanel.getCollectionTreePersistence().saveCurrentTree();

        // 同步更新已打开Tab的标题
        updateOpenedTabsTitle(item, newName);
    }

    /**
     * 更新已打开Tab的标题
     */
    private void updateOpenedTabsTitle(HttpRequestItem item, String newName) {
        RequestEditorPanel editPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
        JTabbedPane tabbedPane = editPanel.getTabbedPane();

        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof RequestEditSubPanel subPanel) {
                HttpRequestItem tabItem = subPanel.getCurrentRequest();
                if (tabItem != null && item.getId().equals(tabItem.getId())) {
                    tabbedPane.setTitleAt(i, newName);
                    tabbedPane.setTabComponentAt(i,
                            ClosableTabComponent.forRequest(newName, item));
                    subPanel.initPanelData(item);
                }
            }
        }
    }

    /**
     * 重命名保存的响应
     */
    private void renameSavedResponse(DefaultMutableTreeNode selectedNode) {
        SavedResponse savedResponse = CollectionTreeNodes.savedResponse(selectedNode).orElse(null);
        if (savedResponse == null) {
            return;
        }
        String oldName = savedResponse.getName();

        TextInputDialog.showRequiredName(
                UiSingletonFactory.getInstance(MainFrame.class),
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_SAVED_RESPONSE_TITLE),
                oldName,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DIALOG_RENAME_SAVED_RESPONSE_EMPTY)
        ).ifPresent(newName -> {
            if (newName.equals(oldName)) {
                return;
            }
            updateSavedResponseName(selectedNode, savedResponse, newName);
        });
    }

    /**
     * 更新保存的响应名称
     */
    private void updateSavedResponseName(DefaultMutableTreeNode node, SavedResponse savedResponse, String newName) {
        savedResponse.setName(newName);
        leftPanel.getTreeModel().nodeChanged(node);
        leftPanel.getCollectionTreePersistence().saveCurrentTree();

        // 同步更新已打开Tab的标题
        updateOpenedSavedResponseTabsTitle(savedResponse, newName);
    }

    /**
     * 更新已打开的保存响应Tab的标题
     */
    private void updateOpenedSavedResponseTabsTitle(SavedResponse savedResponse, String newName) {
        RequestEditorPanel editPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
        JTabbedPane tabbedPane = editPanel.getTabbedPane();

        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof RequestEditSubPanel subPanel) {
                if (subPanel.isSavedResponseTab()) {
                    SavedResponse tabSavedResponse = subPanel.getSavedResponse();
                    if (tabSavedResponse != null && savedResponse.getId().equals(tabSavedResponse.getId())) {
                        tabbedPane.setTitleAt(i, newName);
                        tabbedPane.setTabComponentAt(i,
                                new ClosableTabComponent(newName, RequestItemProtocolEnum.SAVED_RESPONSE, false));
                    }
                }
            }
        }
    }

    /**
     * 删除选中的项（支持多选）
     */
    public void deleteSelectedItem() {
        TreePath[] selectedPaths = requestTree.getSelectionPaths();
        if (selectedPaths == null || selectedPaths.length == 0) return;

        List<DefaultMutableTreeNode> nodesToDelete = filterDeletableNodes(selectedPaths);
        if (nodesToDelete.isEmpty()) return;

        if (!confirmDelete(nodesToDelete.size())) return;

        performDelete(nodesToDelete);
    }

    /**
     * 过滤可删除的节点
     */
    private List<DefaultMutableTreeNode> filterDeletableNodes(TreePath[] paths) {
        List<DefaultMutableTreeNode> nodes = new ArrayList<>();
        for (TreePath path : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node != null && node != leftPanel.getRootTreeNode() && node.getParent() != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * 确认删除
     */
    private boolean confirmDelete(int count) {
        String message = count == 1
                ? I18nUtil.getMessage(MessageKeys.COLLECTIONS_DELETE_CONFIRM)
                : I18nUtil.getMessage(MessageKeys.COLLECTIONS_DELETE_BATCH_CONFIRM, count);

        int confirm = JOptionPane.showConfirmDialog(
                UiSingletonFactory.getInstance(MainFrame.class),
                message,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_DELETE_CONFIRM_TITLE),
                JOptionPane.YES_NO_OPTION
        );

        return confirm == JOptionPane.YES_OPTION;
    }

    /**
     * 执行删除操作
     */
    private void performDelete(List<DefaultMutableTreeNode> nodesToDelete) {
        List<TreePath> expandedPaths = TreeStateHelper.saveExpandedPaths(requestTree);

        RequestEditorPanel editPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
        JTabbedPane tabbedPane = editPanel.getTabbedPane();

        // 关闭相关Tab
        for (DefaultMutableTreeNode node : nodesToDelete) {
            closeTabsForNode(node, tabbedPane);
        }

        // 删除节点
        for (DefaultMutableTreeNode node : nodesToDelete) {
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent != null) {
                parent.remove(node);
            }
        }

        leftPanel.getTreeModel().reload();
        TreeStateHelper.restoreExpandedPaths(requestTree, expandedPaths, leftPanel.getRootTreeNode());

        // 调整Tab选中状态
        if (tabbedPane.getTabCount() > 1) {
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 2);
        }

        leftPanel.getCollectionTreePersistence().saveCurrentTree();
    }

    /**
     * 关闭节点相关的Tab
     */
    private void closeTabsForNode(DefaultMutableTreeNode node, JTabbedPane tabbedPane) {
        HttpRequestItem item = CollectionTreeNodes.request(node).orElse(null);
        if (item != null) {
            // 清空保存的响应列表，确保删除时不会保留已保存的响应
            if (item.getResponse() != null) {
                item.getResponse().clear();
            }
            closeRequestTabs(item, tabbedPane);
        } else if (CollectionTreeNodes.isGroup(node)) {
            closeTabsForGroup(node, tabbedPane);
        } else if (CollectionTreeNodes.isSavedResponse(node)) {
            // 处理删除保存的响应节点
            deleteSavedResponseNode(node);
        }
    }

    /**
     * 关闭请求的Tab
     */
    private void closeRequestTabs(HttpRequestItem item, JTabbedPane tabbedPane) {
        RequestEditorPanel requestEditPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof RequestEditSubPanel subPanel) {
                HttpRequestItem tabItem = subPanel.getCurrentRequest();
                if (tabItem != null && item.getId().equals(tabItem.getId())) {
                    requestEditPanel.removeTabAtWithCleanup(i);
                }
            }
        }
    }

    /**
     * 递归关闭分组下所有请求的Tab
     */
    private void closeTabsForGroup(DefaultMutableTreeNode groupNode, JTabbedPane tabbedPane) {
        for (int i = 0; i < groupNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) groupNode.getChildAt(i);
            closeTabsForNode(child, tabbedPane);
        }
    }

    /**
     * 复制选中的请求（创建副本）
     */
    public void duplicateSelectedRequests() {
        TreePath[] selectedPaths = requestTree.getSelectionPaths();
        if (selectedPaths == null || selectedPaths.length == 0) return;

        List<RequestCopyInfo> copyInfos = collectCopyInfos(selectedPaths);
        if (copyInfos.isEmpty()) return;

        for (RequestCopyInfo info : copyInfos) {
            createDuplicateRequest(info);
        }

        leftPanel.getCollectionTreePersistence().saveCurrentTree();
        NotificationCenter.showSuccess(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_COPY_SUCCESS, copyInfos.size())
        );
    }

    /**
     * 收集复制信息
     */
    private List<RequestCopyInfo> collectCopyInfos(TreePath[] paths) {
        List<RequestCopyInfo> infos = new ArrayList<>();
        for (TreePath path : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            HttpRequestItem item = CollectionTreeNodes.request(node).orElse(null);
            if (item != null) {
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                int idx = parent.getIndex(node);
                infos.add(new RequestCopyInfo(item, parent, idx));
            }
        }
        return infos;
    }

    /**
     * 创建复制的请求
     */
    private void createDuplicateRequest(RequestCopyInfo info) {
        HttpRequestItem copy = JsonUtil.deepCopy(info.item, HttpRequestItem.class);
        copy.setId(java.util.UUID.randomUUID().toString());
        copy.setName(info.item.getName() + " " +
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_SUFFIX));

        DefaultMutableTreeNode copyNode = CollectionTreeNodes.requestNode(copy);
        info.parent.insert(copyNode, info.index + 1);
        leftPanel.getTreeModel().reload(info.parent);
        requestTree.expandPath(new TreePath(info.parent.getPath()));
    }

    /**
     * 复制选中的请求到剪贴板
     */
    public void copySelectedRequests() {
        TreePath[] selectedPaths = requestTree.getSelectionPaths();
        if (selectedPaths == null || selectedPaths.length == 0) return;

        copiedRequests.clear();
        for (TreePath path : selectedPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            HttpRequestItem item = CollectionTreeNodes.request(node).orElse(null);
            if (item != null) {
                HttpRequestItem copy = JsonUtil.deepCopy(item, HttpRequestItem.class);
                copiedRequests.add(copy);
            }
        }

        if (!copiedRequests.isEmpty()) {
            NotificationCenter.showSuccess(
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_COPIED_TO_CLIPBOARD, copiedRequests.size())
            );
        }
    }

    /**
     * 粘贴请求
     */
    public void pasteRequests() {
        if (copiedRequests.isEmpty()) return;

        DefaultMutableTreeNode targetParent = determineTargetParent();
        if (targetParent == null) return;

        for (HttpRequestItem copiedItem : copiedRequests) {
            createPastedRequest(targetParent, copiedItem);
        }

        leftPanel.getTreeModel().reload(targetParent);
        requestTree.expandPath(new TreePath(targetParent.getPath()));
        leftPanel.getCollectionTreePersistence().saveCurrentTree();

        NotificationCenter.showSuccess(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_PASTE_SUCCESS, copiedRequests.size())
        );
    }

    /**
     * 确定粘贴的目标父节点
     */
    private DefaultMutableTreeNode determineTargetParent() {
        DefaultMutableTreeNode targetNode = getSelectedNode();
        if (targetNode == null) {
            return leftPanel.getRootTreeNode();
        }

        if (targetNode == leftPanel.getRootTreeNode() || ROOT.equals(targetNode.getUserObject())) {
            return leftPanel.getRootTreeNode();
        }

        if (CollectionTreeNodes.isGroup(targetNode)) {
            return targetNode;
        }
        if (CollectionTreeNodes.isRequest(targetNode)) {
            return (DefaultMutableTreeNode) targetNode.getParent();
        }

        return leftPanel.getRootTreeNode();
    }

    /**
     * 创建粘贴的请求
     */
    private void createPastedRequest(DefaultMutableTreeNode parent, HttpRequestItem copiedItem) {
        HttpRequestItem pasteItem = JsonUtil.deepCopy(copiedItem, HttpRequestItem.class);
        pasteItem.setId(java.util.UUID.randomUUID().toString());
        pasteItem.setName(copiedItem.getName() + " " +
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_SUFFIX));

        DefaultMutableTreeNode newNode = CollectionTreeNodes.requestNode(pasteItem);
        parent.add(newNode);
    }

    /**
     * 复制请求为cURL命令
     */
    public void copySelectedRequestAsCurl() {
        DefaultMutableTreeNode selectedNode = getSelectedNode();
        if (selectedNode == null) return;

        HttpRequestItem item = CollectionTreeNodes.request(selectedNode).orElse(null);
        if (item == null) return;
        try {
            PreparedRequest req = PreparedRequestFactory.build(item);
            PreparedRequestFinalizer.finalizeForSend(req, item);
            String curl = CurlParser.toCurl(req);

            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(curl), null);

            NotificationCenter.showSuccess(
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_CURL_SUCCESS)
            );
        } catch (Exception ex) {
            NotificationCenter.showError(
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_CURL_FAIL, ex.getMessage())
            );
        }
    }

    /**
     * 复制选中的分组
     */
    public void duplicateSelectedGroup() {
        DefaultMutableTreeNode selectedNode = getSelectedNode();
        if (selectedNode == null) return;

        if (!CollectionTreeNodes.isGroup(selectedNode)) return;

        DefaultMutableTreeNode copyNode = TreeNodeCloner.deepCopyGroupNode(selectedNode);
        RequestGroup copiedGroup = CollectionTreeNodes.group(copyNode).orElse(null);
        if (copiedGroup == null) {
            return;
        }
        String originalName = copiedGroup.getName();
        String newName = originalName + I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_COPY_SUFFIX);
        copiedGroup.setName(newName);

        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
        if (parent != null) {
            int idx = parent.getIndex(selectedNode) + 1;
            parent.insert(copyNode, idx);
            leftPanel.getTreeModel().reload(parent);
            requestTree.expandPath(new TreePath(parent.getPath()));

            leftPanel.getCollectionTreePersistence().saveCurrentTree();
        }
    }

    /**
     * 导出分组为Postman Collection
     */
    public void exportGroupAsPostman(DefaultMutableTreeNode groupNode) {
        if (!isValidGroupNode(groupNode)) {
            showInvalidGroupWarning();
            return;
        }

        String groupName = CollectionTreeNodes.group(groupNode).orElseThrow().getName();

        SystemFileChooser fileChooser = FileChooserUtil.createSaveFileChooser(
                "collections.export.postman",
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_EXPORT_POSTMAN_DIALOG_TITLE));
        fileChooser.setSelectedFile(new File(groupName + "-postman.json"));

        int userSelection = fileChooser.showSaveDialog(UiSingletonFactory.getInstance(MainFrame.class));
        if (userSelection == SystemFileChooser.APPROVE_OPTION) {
            exportToFile(groupNode, groupName, fileChooser.getSelectedFile());
        }
    }

    /**
     * 导出到文件
     */
    private void exportToFile(DefaultMutableTreeNode groupNode, String groupName, File file) {
        try {
            JSONObject postmanCollection =
                    PostmanCollectionExporter.buildPostmanCollectionFromTreeNode(groupNode, groupName);
            FileUtil.writeUtf8String(postmanCollection.toStringPretty(), file);
            NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.COLLECTIONS_EXPORT_SUCCESS));
        } catch (Exception ex) {
            log.error("Export Postman error", ex);
            NotificationCenter.showError(
                    I18nUtil.getMessage(MessageKeys.COLLECTIONS_EXPORT_FAIL, ex.getMessage())
            );
        }
    }

    /**
     * 转移集合到其他工作区
     */
    public void moveCollectionToWorkspace(DefaultMutableTreeNode selectedNode) {
        if (!isValidGroupNode(selectedNode)) return;

        String collectionName = CollectionTreeNodes.group(selectedNode).orElseThrow().getName();

        WorkspaceTransferCoordinator.transferToWorkspace(
                collectionName,
                (targetWorkspace, itemName) -> performCollectionMove(selectedNode, targetWorkspace)
        );
    }

    /**
     * 执行集合移动到目标工作区
     * 将集合从当前工作区移动到目标工作区
     *
     * @param collectionNode  要移动的集合节点
     * @param targetWorkspace 目标工作区
     */
    private void performCollectionMove(DefaultMutableTreeNode collectionNode, Workspace targetWorkspace) {
        // 1. 深拷贝集合节点（包含所有子节点）
        DefaultMutableTreeNode copiedNode = TreeNodeCloner.deepCopyGroupNode(collectionNode);

        // 2. 获取目标工作区的集合文件路径
        String targetCollectionPath = SystemUtil.getCollectionPathForWorkspace(targetWorkspace);

        // 3. 创建目标工作区的持久化工具
        DefaultMutableTreeNode targetRootNode = new DefaultMutableTreeNode(ROOT);
        DefaultTreeModel targetTreeModel = new DefaultTreeModel(targetRootNode);
        SwingCollectionTreePersistence targetPersistence = new SwingCollectionTreePersistence(
                targetCollectionPath, targetRootNode, targetTreeModel);

        // 4. 加载目标工作区的现有集合
        targetPersistence.loadIntoTree();

        // 5. 将集合添加到目标工作区
        targetRootNode.add(copiedNode);

        // 6. 保存到目标工作区
        targetPersistence.saveCurrentTree();

        // 7. 从当前工作区删除原集合
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) collectionNode.getParent();
        if (parent != null) {
            parent.remove(collectionNode);
            leftPanel.getTreeModel().reload();

            leftPanel.getCollectionTreePersistence().saveCurrentTree();
        }

        log.info("Successfully moved collection '{}' to workspace '{}'",
                CollectionTreeNodes.group(collectionNode).map(RequestGroup::getName).orElse(""), targetWorkspace.getName());
    }

    /**
     * 添加选中的请求到功能测试
     */
    public void addSelectedRequestsToFunctionalTest() {
        TreePath[] selectedPaths = requestTree.getSelectionPaths();
        if (selectedPaths == null || selectedPaths.length == 0) return;

        List<HttpRequestItem> requestsToAdd = new ArrayList<>();
        for (TreePath path : selectedPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            collectRequestsRecursively(node, requestsToAdd);
        }

        if (requestsToAdd.isEmpty()) return;

        try {
            FunctionalPanel functionalPanel = UiSingletonFactory.getInstance(FunctionalPanel.class);
            functionalPanel.loadRequests(requestsToAdd);

            // 切换到功能测试Tab
            SidebarTabPanel sidebarPanel = UiSingletonFactory.getInstance(SidebarTabPanel.class);
            JTabbedPane tabbedPane = sidebarPanel.getTabbedPane();
            tabbedPane.setSelectedIndex(3); // 功能测试Tab索引
        } catch (Exception e) {
            log.error("添加请求到功能测试失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    private DefaultMutableTreeNode getSelectedNode() {
        return (DefaultMutableTreeNode) requestTree.getLastSelectedPathComponent();
    }

    private int getGroupInsertIndex(DefaultMutableTreeNode parent) {
        if (parent == null) return -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (CollectionTreeNodes.isRequest(child)) {
                return i;
            }
        }
        return parent.getChildCount();
    }

    private boolean isValidGroupNode(DefaultMutableTreeNode node) {
        return node != null && CollectionTreeNodes.isGroup(node);
    }

    private void showInvalidGroupWarning() {
        JOptionPane.showMessageDialog(
                leftPanel,
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_EXPORT_POSTMAN_SELECT_GROUP),
                I18nUtil.getMessage(MessageKeys.GENERAL_TIP),
                JOptionPane.WARNING_MESSAGE
        );
    }

    private void collectRequestsRecursively(DefaultMutableTreeNode node, List<HttpRequestItem> list) {
        CollectionTreeNodes.request(node).ifPresent(list::add);
        if (CollectionTreeNodes.isGroup(node)) {
            for (int i = 0; i < node.getChildCount(); i++) {
                collectRequestsRecursively((DefaultMutableTreeNode) node.getChildAt(i), list);
            }
        }
    }

    public boolean isCopiedRequestsEmpty() {
        return copiedRequests.isEmpty();
    }

    /**
     * 从父请求中删除保存的响应
     */
    private void deleteSavedResponseNode(DefaultMutableTreeNode savedResponseNode) {
        // 获取父节点（应该是 REQUEST 节点）
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) savedResponseNode.getParent();
        if (parentNode == null) return;

        HttpRequestItem parentRequest = CollectionTreeNodes.request(parentNode).orElse(null);
        SavedResponse toDelete = CollectionTreeNodes.savedResponse(savedResponseNode).orElse(null);
        if (parentRequest == null || toDelete == null) return;

        // 从父请求的 response 列表中删除
        if (parentRequest.getResponse() != null) {
            parentRequest.getResponse().removeIf(resp ->
                    resp.getId() != null && resp.getId().equals(toDelete.getId())
            );
        }
    }

    /**
     * 请求复制信息内部类
     */
    private static class RequestCopyInfo {
        final HttpRequestItem item;
        final DefaultMutableTreeNode parent;
        final int index;

        RequestCopyInfo(HttpRequestItem item, DefaultMutableTreeNode parent, int index) {
            this.item = item;
            this.parent = parent;
            this.index = index;
        }
    }
}
