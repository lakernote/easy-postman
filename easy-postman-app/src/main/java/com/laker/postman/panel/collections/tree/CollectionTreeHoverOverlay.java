package com.laker.postman.panel.collections.tree;

import com.laker.postman.common.component.tree.RequestTreeCellRenderer;
import lombok.experimental.UtilityClass;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;

@UtilityClass
class CollectionTreeHoverOverlay {
    /**
     * 拖拽时不会收到 mouseExited，滚动时也没有 mouseMoved，
     * 绘制前按真实鼠标位置校正 hover 行，避免灰色背景残留。
     */
    void syncHoveredRow(JTree tree) {
        if (!(tree.getCellRenderer() instanceof RequestTreeCellRenderer renderer) || !tree.isShowing()) {
            return;
        }
        Point mouse = tree.getMousePosition();
        int row = mouse == null ? -1 : rowAt(tree, mouse.y);
        if (row != renderer.getHoveredRow()) {
            renderer.setHoveredRow(row);
            // 当前可能只是局部重绘，旧 hover 行可能在裁剪区外，需整树重绘才能擦除
            SwingUtilities.invokeLater(tree::repaint);
        }
    }

    private int rowAt(JTree tree, int y) {
        int row = tree.getClosestRowForLocation(0, y);
        if (row < 0) {
            return -1;
        }
        Rectangle bounds = tree.getRowBounds(row);
        return bounds != null && y >= bounds.y && y < bounds.y + bounds.height ? row : -1;
    }

    void paint(Graphics g, JTree tree) {
        if (!(tree.getCellRenderer() instanceof RequestTreeCellRenderer renderer)) {
            return;
        }
        int row = renderer.getHoveredRow();
        if (row < 0 || row >= tree.getRowCount()) {
            return;
        }
        Rectangle bounds = tree.getRowBounds(row);
        if (bounds == null) {
            return;
        }
        TreePath path = tree.getPathForRow(row);
        if (path == null || tree.isPathSelected(path)) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(CollectionTreeTheme.hoverOverlayColor());
        g2.fillRect(0, bounds.y, tree.getWidth(), bounds.height);
        g2.dispose();
    }
}
