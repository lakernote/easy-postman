package com.laker.postman.panel.performance.component;

import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;



import com.laker.postman.common.component.HttpRequestDisplayMetadata;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.performance.core.model.NodeType;
import com.laker.postman.performance.model.PerformanceTreeNode;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.IconUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class PerformanceTreeCellRenderer extends DefaultTreeCellRenderer {

    public static final int SIZE = 18;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        Object userObj = node.getUserObject();
        if (userObj instanceof PerformanceTreeNode nodeData) {
            if (nodeData.type == NodeType.REQUEST) {
                applyRequestRendering(label, nodeData, sel);
                return label;
            }

            // 设置图标
            switch (nodeData.type) {
                case THREAD_GROUP -> label.setIcon(IconUtil.createThemed("icons/user-group.svg", SIZE, SIZE));
                case CSV_DATA_SET -> label.setIcon(IconUtil.createThemed("icons/csv.svg", SIZE, SIZE));
                case SIMPLE -> label.setIcon(IconUtil.createThemed("icons/performance-simple-controller.svg", SIZE, SIZE));
                case LOOP -> label.setIcon(IconUtil.createThemed("icons/refresh.svg", SIZE, SIZE));
                case CONDITION -> label.setIcon(IconUtil.createThemed("icons/performance-condition-controller.svg", SIZE, SIZE));
                case WHILE -> label.setIcon(IconUtil.createThemed("icons/performance-condition-controller.svg", SIZE, SIZE));
                case ONCE_ONLY -> label.setIcon(IconUtil.createThemed("icons/performance-once-only-controller.svg", SIZE, SIZE));
                case ASSERTION -> label.setIcon(IconUtil.createThemed("icons/warning.svg", SIZE, SIZE));
                case EXTRACTOR -> label.setIcon(IconUtil.createThemed("icons/global-variables.svg", SIZE, SIZE));
                case TIMER -> label.setIcon(IconUtil.createThemed("icons/time.svg", SIZE, SIZE));
                case SSE_CONNECT -> label.setIcon(IconUtil.createThemed("icons/connect.svg", SIZE, SIZE));
                case SSE_READ -> label.setIcon(IconUtil.createThemed("icons/time.svg", SIZE, SIZE));
                case WS_CONNECT -> label.setIcon(IconUtil.createThemed("icons/connect.svg", SIZE, SIZE));
                case WS_SEND -> label.setIcon(IconUtil.createThemed("icons/ws-send.svg", SIZE, SIZE));
                case WS_READ -> label.setIcon(IconUtil.createThemed("icons/time.svg", SIZE, SIZE));
                case WS_CLOSE -> label.setIcon(IconUtil.createThemed("icons/ws-close.svg", SIZE, SIZE));
                case ROOT -> label.setIcon(IconUtil.createThemed("icons/performance.svg", SIZE, SIZE));
            }

            String text = nodeData.name;
            if (!nodeData.enabled) {
                if (!sel) {
                    label.setForeground(ModernColors.getTextHint());
                }
                label.setFont(FontsUtil.getDefaultFont(Font.ITALIC));
                label.setText("<html><strike>" + text + "</strike></html>");
            } else {
                // 启用状态：恢复正常样式
                label.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
                label.setText(text);
            }
        }
        return label;
    }

    private void applyRequestRendering(JLabel label, PerformanceTreeNode nodeData, boolean selected) {
        label.setIcon(null);
        label.setFont(FontsUtil.getDefaultFont(nodeData.enabled ? Font.PLAIN : Font.ITALIC));
        if (!nodeData.enabled && !selected) {
            label.setForeground(ModernColors.getTextHint());
        }
        label.setText(buildRequestText(nodeData.httpRequestItem, nodeData.name, !nodeData.enabled, nodeData.ssePerformanceEnabled));
    }

    private String buildRequestText(HttpRequestItem item, String name, boolean disabled, boolean ssePerformanceEnabled) {
        String method = item == null ? "" : item.getMethod();
        String methodColor = HttpRequestDisplayMetadata.methodColorHex(method);
        RequestItemProtocolEnum protocol = item != null && item.getProtocol() != null
                ? item.getProtocol()
                : RequestItemProtocolEnum.HTTP;
        if (ssePerformanceEnabled) {
            method = "SSE";
            methodColor = HttpRequestDisplayMetadata.protocolColorHex(method);
        } else if (protocol.isWebSocketProtocol()) {
            method = "WS";
            methodColor = HttpRequestDisplayMetadata.protocolColorHex(method);
        } else {
            method = HttpRequestDisplayMetadata.methodLabel(method);
        }
        return buildStyledText(method, methodColor, name, disabled);
    }

    private static String buildStyledText(String method, String methodColor, String name, boolean disabled) {
        String safeMethod = method == null ? "" : escapeHtml(method);
        String safeName = name == null ? "" : escapeHtml(name);
        String color = methodColor == null ? ModernColors.toHtmlColor(ModernColors.getTextPrimary()) : methodColor;
        int baseFontSize = SettingManager.getUiFontSize();
        int methodFontSize = Math.max(7, baseFontSize - 5);
        int nameFontSize = Math.max(8, baseFontSize - 4);
        String nameHtml = disabled ? "<strike>" + safeName + "</strike>" : safeName;
        return "<html><nobr>"
                + "<span style='color:" + color + ";font-size:" + methodFontSize + "px'>" + safeMethod + "</span> "
                + "<span style='font-size:" + nameFontSize + "px'>" + nameHtml + "</span>"
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
