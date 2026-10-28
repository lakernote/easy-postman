package com.laker.postman.common.component.tree;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.request.model.HttpRequestItem;


import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.panel.collections.tree.CollectionTreePanel;
import com.laker.postman.service.collections.CollectionTreeNodes;
import com.laker.postman.common.component.HttpRequestDisplayMetadata;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.IconUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 自定义树节点渲染器，用于美化 JTree 的节点显示
 * 当鼠标悬浮在 GROUP 节点时，右侧显示 "+" 图标（类似 Postman）
 * 所有节点 hover 时整行显示背景色高亮
 */
public class RequestTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final int ICON_SIZE = 18;

    /**
     * 当前鼠标悬浮的行号，-1 表示无悬浮；由外部（MouseMotionListener）更新
     */
    private int hoveredRow = -1;

    public void setHoveredRow(int row) {
        this.hoveredRow = row;
    }

    public int getHoveredRow() {
        return hoveredRow;
    }

    /**
     * "+" 图标区域宽度，距右边缘偏移：more(20) + plus(20) = 右侧40px内
     */
    public static final int ADD_BUTTON_WIDTH = 20;
    /**
     * "⋯" more 图标紧贴右边缘，宽度 20px
     */
    public static final int MORE_BUTTON_WIDTH = 20;

    /**
     * 当前渲染行是否需要显示 "+" 图标（GROUP 节点 hover 时）
     */
    private boolean showAddButton = false;
    /**
     * 当前渲染行是否需要显示 "⋯" more 图标（GROUP 节点 hover 时）
     */
    private boolean showMoreButton = false;

    /**
     * plus.svg 图标，主题适配，懒加载
     */
    private transient Icon plusIcon = null;
    /**
     * more.svg 图标，主题适配，懒加载
     */
    private transient Icon moreIcon = null;

    private Icon getPlusIcon() {
        if (plusIcon == null) {
            plusIcon = IconUtil.createThemed("icons/plus.svg", ICON_SIZE, ICON_SIZE);
        }
        return plusIcon;
    }

    private Icon getMoreIcon() {
        if (moreIcon == null) {
            moreIcon = IconUtil.createThemed("icons/more.svg", ICON_SIZE, ICON_SIZE);
        }
        return moreIcon;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        showAddButton = false;
        showMoreButton = false;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        CollectionTreeNodes.group(node).ifPresentOrElse(
                group -> renderGroupNode(node, group, row),
                () -> CollectionTreeNodes.request(node).ifPresentOrElse(
                        this::applyRequestRendering,
                        () -> CollectionTreeNodes.savedResponse(node).ifPresent(this::applySavedResponseRendering)
                )
        );

        return this;
    }

    /**
     * 两个图标总宽 + 间距，hover 时为文字预留的右侧空白
     */
    private static final int BUTTONS_RESERVED_WIDTH = ADD_BUTTON_WIDTH + MORE_BUTTON_WIDTH + 4;

    private void renderGroupNode(DefaultMutableTreeNode node, RequestGroup group, int row) {
        String groupName = group.getName();
        boolean isRootLevel = node.getParent() instanceof DefaultMutableTreeNode p &&
                CollectionTreePanel.ROOT.equals(String.valueOf(p.getUserObject()));
        boolean isHover = (row == hoveredRow);

        if (isRootLevel) {
            setIcon(new FlatSVGIcon("icons/collection.svg", ICON_SIZE, ICON_SIZE));

        } else {
            setIcon(new FlatSVGIcon("icons/group.svg", ICON_SIZE, ICON_SIZE));
        }

        if (isHover) {
            // hover 时用纯文本，JLabel 能自动省略超长文字
            setText(groupName);
        } else {
            String nameColor = toHex(ModernColors.getTextPrimary());
            setText("<html><nobr><span style='color:" + nameColor + "'>"
                    + escapeHtml(groupName) + "</span></nobr></html>");
        }

        if (isHover) {
            showAddButton = true;
            showMoreButton = true;
            // 右侧加 padding，JLabel 省略号截断超长文字，为两个图标留出空间
            setBorder(BorderFactory.createEmptyBorder(0, 0, 0, BUTTONS_RESERVED_WIDTH));
        }
    }

    /**
     * 绘制右侧 "+" 和 "⋯" 图标（GROUP hover 时）。
     * 布局（从右到左）：| more(20px) | plus(20px) |
     * 整行 hover 背景色由 JTree.paint() 统一绘制。
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!showAddButton && !showMoreButton) return;

        int w = getWidth();
        int h = getHeight();

        // more 图标：贴右边缘
        if (showMoreButton) {
            Icon more = getMoreIcon();
            int x = w - more.getIconWidth() - 2;
            int y = (h - more.getIconHeight()) / 2;
            more.paintIcon(this, g, x, y);
        }

        // plus 图标：在 more 左边
        if (showAddButton) {
            Icon plus = getPlusIcon();
            int x = w - MORE_BUTTON_WIDTH - plus.getIconWidth() - 2;
            int y = (h - plus.getIconHeight()) / 2;
            plus.paintIcon(this, g, x, y);
        }
    }

    private void applyRequestRendering(HttpRequestItem item) {
        String method = item.getMethod();
        String name = item.getName();
        RequestItemProtocolEnum protocol = item.getProtocol();
        String methodColor = HttpRequestDisplayMetadata.methodColorHex(method);

        if (protocol.isWebSocketProtocol()) {
            method = "WS";
            methodColor = HttpRequestDisplayMetadata.protocolColorHex(method);
        } else {
            method = HttpRequestDisplayMetadata.methodLabel(method);
        }
        setText(buildStyledText(method, methodColor, name));
    }

    private void applySavedResponseRendering(SavedResponse savedResponse) {
        setIcon(IconUtil.createThemed("icons/save-response.svg", IconUtil.SIZE_LARGE, IconUtil.SIZE_LARGE));
        String name = savedResponse.getName();
        int code = savedResponse.getCode();
        long timestamp = savedResponse.getTimestamp();
        String timeStr = new SimpleDateFormat("MM-dd HH:mm:ss").format(new Date(timestamp));
        String statusColor = getStatusColor(code);
        setText(buildSavedResponseText(name, code, timeStr, statusColor));
    }

    private static String getStatusColor(int code) {
        if (code >= 200 && code < 300) return ModernColors.toHtmlColor(ModernColors.getSuccess());
        else if (code >= 300 && code < 400) return ModernColors.toHtmlColor(ModernColors.getInfo());
        else if (code >= 400 && code < 500) return ModernColors.toHtmlColor(ModernColors.getWarning());
        else if (code >= 500) return ModernColors.toHtmlColor(ModernColors.getError());
        return ModernColors.toHtmlColor(ModernColors.getNeutral());
    }

    private static String toHex(Color color) {
        return ModernColors.toHtmlColor(color);
    }

    private static String buildSavedResponseText(String name, int code, String timeStr, String statusColor) {
        String safeName = name == null ? "" : escapeHtml(name);
        int baseFontSize = SettingManager.getUiFontSize();
        int nameFontSize = Math.max(8, baseFontSize - 4);
        int statusFontSize = Math.max(7, baseFontSize - 5);
        int timeFontSize = Math.max(7, baseFontSize - 6);
        return "<html><nobr>"
                + "<span style='font-size:" + nameFontSize + "px'>" + safeName + "</span> "
                + "<span style='color:" + statusColor + ";font-size:" + statusFontSize + "px'>" + code + "</span> "
                + "<span style='color:" + ModernColors.toHtmlColor(ModernColors.getTextHint()) + ";font-size:"
                + timeFontSize + "px'>" + timeStr + "</span>"
                + "</nobr></html>";
    }

    private static String buildStyledText(String method, String methodColor, String name) {
        String safeMethod = method == null ? "" : escapeHtml(method);
        String safeName = name == null ? "" : escapeHtml(name);
        String color = methodColor == null ? ModernColors.toHtmlColor(ModernColors.getTextPrimary()) : methodColor;
        int baseFontSize = SettingManager.getUiFontSize();
        int methodFontSize = Math.max(7, baseFontSize - 5);
        int nameFontSize = Math.max(8, baseFontSize - 4);
        return "<html><nobr>"
                + "<span style='color:" + color + ";font-size:" + methodFontSize + "px'>" + safeMethod + "</span> "
                + "<span style='font-size:" + nameFontSize + "px'>" + safeName + "</span>"
                + "</nobr></html>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(Math.max(16, s.length()));
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
