package com.laker.postman.panel.workspace.components;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.model.GitRepoSource;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.plugin.git.GitWorkspaceLayout;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

/**
 * 自定义列表单元格渲染器
 */
public class WorkspaceListCellRenderer extends DefaultListCellRenderer {
    private static final String HTML_START = "<html>";
    private static final String HTML_END = "</html>";

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof Workspace workspace) {
            // 渲染在 EDT 上进行：只读取缓存的磁盘探测结果，探测完成后重绘列表
            WorkspaceService.GitLayoutPeek peek = WorkspaceService.getInstance().peekGitLayout(workspace, list::repaint);
            configureWorkspaceIcon(workspace, peek);
            configureWorkspaceText(workspace, peek);
            configureWorkspaceStyle();
            setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        }
        return this;
    }

    /**
     * 图标由磁盘事实决定；探测结果返回前暂按已保存的类型显示。
     */
    private void configureWorkspaceIcon(Workspace workspace, WorkspaceService.GitLayoutPeek peek) {
        GitWorkspaceLayout layout = peek.layout();
        boolean git = peek.known() ? layout != null : workspace.getType() == WorkspaceType.GIT;
        boolean ownedRepository = workspace.getType() == WorkspaceType.GIT && (layout == null || layout.isFullControl());
        FlatSVGIcon icon;
        if (!git) {
            icon = IconUtil.createThemed("icons/local.svg", 20, 20);
        } else if (!ownedRepository) {
            icon = IconUtil.create("icons/git.svg", 20, 20);
        } else if (workspace.getGitRemoteUrl() == null || workspace.getGitRemoteUrl().trim().isEmpty()) {
            icon = IconUtil.create("icons/git-warning.svg", 20, 20);
        } else if (workspace.getGitRepoSource() == GitRepoSource.CLONED) {
            icon = IconUtil.create("icons/git-remote.svg", 20, 20);
        } else {
            icon = IconUtil.create("icons/git.svg", 20, 20);
        }
        setIcon(icon);
    }

    private void configureWorkspaceText(Workspace workspace, WorkspaceService.GitLayoutPeek peek) {
        StringBuilder text = new StringBuilder();
        text.append(HTML_START);

        Workspace current = WorkspaceService.getInstance().getCurrentWorkspace();
        boolean isCurrent = current != null && current.getId().equals(workspace.getId());
        String primaryColor = toHex(ModernColors.getPrimary());
        String secondaryColor = toHex(ModernColors.getTextSecondary());

        // 如果是当前工作区，设置文字颜色为主题色
        if (isCurrent) {
            text.append("<b style='color: ").append(primaryColor).append(";'>").append(workspace.getName()).append("</b>");
        } else {
            text.append("<b>").append(workspace.getName()).append("</b>");
        }

        text.append("<br>");

        // 描述部分也根据是否为当前工作区设置不同颜色
        if (isCurrent) {
            text.append("<small style='color: ").append(primaryColor).append(";'>");
        } else {
            text.append("<small style='color: ").append(secondaryColor).append(";'>");
        }

        text.append(escapeHtml(typeLabel(workspace, peek)));

        // 显示描述信息（警告状态已通过橙色图标表示）
        if (workspace.getDescription() != null && !workspace.getDescription().trim().isEmpty()) {
            text.append(" - ").append(workspace.getDescription());
        }

        text.append("</small>");
        text.append(HTML_END);

        setText(text.toString());
    }

    private void configureWorkspaceStyle() {
        setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        setIconTextGap(5); // 图标和文字间距
    }

    /**
     * 动态计算单元格高度
     * 基于字体大小和内容（两行文字：名称+描述）
     */
    public static int calculateCellHeight() {
        Font font = FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1);
        FontMetrics metrics = new JLabel().getFontMetrics(font);

        // 两行文字的高度 + 上下内边距 + 行间距
        int lineHeight = metrics.getHeight();
        int twoLinesHeight = lineHeight * 2;
        int verticalPadding = 10; // 上下各5像素
        int lineSpacing = 4; // 行间距

        return twoLinesHeight + verticalPadding + lineSpacing;
    }

    static String typeLabel(Workspace workspace, WorkspaceService.GitLayoutPeek peek) {
        if (!peek.known()) {
            return I18nUtil.getMessage(workspace.getType() == WorkspaceType.GIT
                    ? MessageKeys.WORKSPACE_TYPE_GIT : MessageKeys.WORKSPACE_TYPE_LOCAL);
        }
        return typeLabel(workspace, peek.layout());
    }

    /**
     * 列表标记完全来自磁盘探测结果：本地 / Git · 分支 / Git · 仓库名/子目录 · 分支（位于代码仓库中的工作区）。
     */
    static String typeLabel(Workspace workspace, GitWorkspaceLayout layout) {
        if (layout == null) {
            return I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_LOCAL);
        }
        StringBuilder label = new StringBuilder(I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_GIT));
        if (!layout.isFullControl()) {
            Path repositoryName = layout.getRepositoryRoot().getFileName();
            label.append(" · ")
                    .append(repositoryName == null ? layout.getRepositoryRoot().toString() : repositoryName.toString())
                    .append(layout.isEmbedded() ? "/" + layout.scopePathFilter() : "");
        }
        if (layout.getBranch() != null && !layout.getBranch().isBlank()) {
            label.append(" · ").append(layout.getBranch());
        }
        return label.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
