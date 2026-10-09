package com.laker.postman.panel.collections.tree.handler;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.request.model.HttpRequestItem;

import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.tree.RequestTreeCellRenderer;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.panel.collections.tree.coordinator.RequestTreeCoordinator;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 请求树鼠标事件处理器
 * 负责处理树节点的单击、双击和右键菜单
 */
public class RequestTreeMouseHandler extends MouseAdapter {
    private final JTree requestTree;
    private final RequestTreePopupMenu popupMenu;
    private final RequestTreeCoordinator coordinator;
    private final RequestTreeOpenActions openActions;
    private final CollectionTreeClickResolver clickResolver;

    public RequestTreeMouseHandler(JTree requestTree, CollectionTreePanel leftPanel, RequestTreeCoordinator coordinator) {
        this(requestTree, leftPanel, coordinator, new RequestEditorTreeOpenActions());
    }

    RequestTreeMouseHandler(
            JTree requestTree,
            CollectionTreePanel leftPanel,
            RequestTreeCoordinator coordinator,
            RequestTreeOpenActions openActions
    ) {
        this.requestTree = requestTree;
        this.popupMenu = new RequestTreePopupMenu(requestTree, leftPanel, coordinator);
        this.coordinator = coordinator;
        this.openActions = openActions;
        this.clickResolver = new CollectionTreeClickResolver(requestTree);
    }

    // ==================== hover 追踪 ====================

    @Override
    public void mouseMoved(MouseEvent e) {
        handleHover(e);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        handleHover(e);
    }

    private void handleHover(MouseEvent e) {
        int row = clickResolver.rowAtY(e.getY());
        updateHoveredRow(row);
        updateTooltip(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        updateHoveredRow(-1);
        requestTree.setToolTipText(null);
    }

    /**
     * 根据鼠标位置动态设置 tooltip
     */
    private void updateTooltip(MouseEvent e) {
        CollectionTreeClickTarget target = clickResolver.resolve(e.getX(), e.getY());
        if (target != null && target.isGroupMoreActions()) {
            requestTree.setToolTipText("More actions");
            return;
        }
        if (target != null && target.isGroupAddRequestAction()) {
            requestTree.setToolTipText("Add Request");
            return;
        }
        requestTree.setToolTipText(null);
    }

    private void updateHoveredRow(int row) {
        TreeCellRenderer renderer = requestTree.getCellRenderer();
        if (renderer instanceof RequestTreeCellRenderer r) {
            r.setHoveredRow(row);
            requestTree.repaint();
        }
    }

    // ==================== 点击处理 ====================

    @Override
    public void mousePressed(MouseEvent e) {
        // 主题/字体刷新可能让 Swing UI 监听器排到本监听器之后，不能依赖它清除上次点击的保护。
        coordinator.finishRequestCreationSelection();
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
            handleLeftSingleClick(e);
        } else if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
            handleDoubleClick(e);
        } else if (SwingUtilities.isRightMouseButton(e)) {
            handleRightClick(e);
        }
    }

    /**
     * mouseReleased 中拦截 Add/More 按钮区域的点击，避免触发节点展开/收起等默认行为
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
            CollectionTreeClickTarget target = clickResolver.resolve(e.getX(), e.getY());
            if (isGroupRowAction(target)) {
                e.consume();
            }
        }
        coordinator.finishRequestCreationSelection();
    }

    private void handleLeftSingleClick(MouseEvent e) {
        CollectionTreeClickTarget target = clickResolver.resolve(e.getX(), e.getY());
        if (target == null) {
            return;
        }

        if (target.isGroupMoreActions()) {
            e.consume();
            handleGroupMoreActionsClick(target, e.getX(), e.getY());
            return;
        }

        if (target.isGroupAddRequestAction()) {
            e.consume();
            handleGroupAddRequestClick(target.node());
            return;
        }

        openTargetTransiently(target);
    }

    private boolean isGroupRowAction(CollectionTreeClickTarget target) {
        return target != null && (target.isGroupMoreActions() || target.isGroupAddRequestAction());
    }

    private void handleGroupAddRequestClick(DefaultMutableTreeNode groupNode) {
        // addHttpRequestDirectly 会选中新请求，mouseReleased 后结束选中保护。
        coordinator.addHttpRequestDirectly(groupNode);
    }

    private void handleGroupMoreActionsClick(CollectionTreeClickTarget target, int x, int y) {
        requestTree.setSelectionPath(target.path());
        showGroupActionMenu(target.node(), x, y);
    }

    /**
     * 弹出 group 专属菜单（新增请求、新增分组等）
     */
    private void showGroupActionMenu(DefaultMutableTreeNode groupNode, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);

        JMenuItem addRequest = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_ADD_REQUEST),
                IconUtil.createThemed("icons/request.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        addRequest.addActionListener(ev -> coordinator.showAddRequestDialog(groupNode));
        menu.add(addRequest);

        JMenuItem addGroup = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_ADD_GROUP),
                IconUtil.create("icons/group.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        addGroup.addActionListener(ev -> coordinator.showAddGroupDialog(groupNode));
        menu.add(addGroup);

        menu.addSeparator();

        JMenuItem rename = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_RENAME),
                IconUtil.createThemed("icons/edit.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        rename.addActionListener(ev -> coordinator.renameSelectedItem());
        rename.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        menu.add(rename);

        JMenuItem duplicate = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_DUPLICATE),
                IconUtil.createThemed("icons/duplicate.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        duplicate.addActionListener(ev -> coordinator.duplicateSelectedGroup());
        menu.add(duplicate);

        menu.addSeparator();

        JMenuItem delete = new JMenuItem(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_DELETE),
                IconUtil.createThemed("icons/delete.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        delete.addActionListener(ev -> coordinator.deleteSelectedItem());
        delete.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        menu.add(delete);

        menu.show(requestTree, x, y);
    }

    /**
     * 处理单击事件：临时打开请求或分组
     */
    private void openTargetTransiently(CollectionTreeClickTarget target) {
        DefaultMutableTreeNode node = target.node();

        if (CollectionTreeNodes.isGroup(node)) {
            toggleGroupAndOpenTransiently(target);
        } else if (CollectionTreeNodes.isRequest(node)) {
            CollectionTreeNodes.request(node).ifPresent(this::handleRequestClick);
        } else if (CollectionTreeNodes.isSavedResponse(node)) {
            CollectionTreeNodes.savedResponse(node).ifPresent(this::handleSavedResponseClick);
        }
    }

    /**
     * 处理双击事件：打开请求或分组编辑面板
     */
    private void handleDoubleClick(MouseEvent e) {
        CollectionTreeClickTarget target = clickResolver.resolve(e.getX(), e.getY());
        if (target == null) return;

        DefaultMutableTreeNode node = target.node();

        if (CollectionTreeNodes.isRequest(node)) {
            CollectionTreeNodes.request(node)
                    .ifPresent(openActions::openFixedRequest);
        } else if (CollectionTreeNodes.isGroup(node)) {
            RequestGroup group = CollectionTreeNodes.group(node).orElse(null);
            if (group == null) {
                return;
            }
            openActions.openFixedGroup(node, group);
            e.consume(); // 阻止展开/收起
        } else if (CollectionTreeNodes.isSavedResponse(node)) {
            // 双击保存的响应：打开固定 Tab
            CollectionTreeNodes.savedResponse(node).ifPresent(this::handleSavedResponseDoubleClick);
            e.consume();
        }
    }

    /**
     * 处理右键点击：显示弹出菜单
     */
    private void handleRightClick(MouseEvent e) {
        int row = clickResolver.rowAtY(e.getY());
        if (row != -1) {
            TreePath clickedPath = requestTree.getPathForRow(row);
            // 只有当点击的路径未被选中时，才设置为当前选中项
            // 如果点击的路径已经被选中，则保持当前的多选状态
            if (clickedPath != null && !requestTree.isPathSelected(clickedPath)) {
                requestTree.setSelectionPath(clickedPath);
            }
        } else {
            requestTree.clearSelection();
        }
        popupMenu.show(e.getX(), e.getY());
    }

    /**
     * 处理分组点击事件
     */
    private void toggleGroupAndOpenTransiently(CollectionTreeClickTarget target) {
        if (target.isExpansionHandle()) {
            return;
        }

        toggleGroupExpansion(target.path());
        RequestGroup group = CollectionTreeNodes.group(target.node()).orElse(null);
        if (group != null) {
            openActions.openTransientGroup(target.node(), group);
        }
    }

    private void toggleGroupExpansion(TreePath path) {
        if (requestTree.isExpanded(path)) {
            requestTree.collapsePath(path);
        } else {
            requestTree.expandPath(path);
        }
    }

    /**
     * 处理请求点击事件
     */
    private void handleRequestClick(HttpRequestItem item) {
        openActions.openTransientRequest(item);
    }

    /**
     * 处理保存的响应单击事件：临时打开
     */
    private void handleSavedResponseClick(SavedResponse savedResponse) {
        openActions.openTransientSavedResponse(savedResponse);
    }

    /**
     * 处理保存的响应双击事件：打开固定 Tab
     */
    private void handleSavedResponseDoubleClick(SavedResponse savedResponse) {
        openActions.openFixedSavedResponse(savedResponse);
    }

}
