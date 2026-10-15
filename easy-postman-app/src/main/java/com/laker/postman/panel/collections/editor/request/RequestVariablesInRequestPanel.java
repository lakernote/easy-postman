package com.laker.postman.panel.collections.editor.request;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.ToolWindowActionToolbar;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.RefreshButton;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.variable.VariableType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RequestVariablesInRequestPanel extends JPanel {
    private static final int BADGE_SIZE = 20;
    private static final int ROW_INDENT = BADGE_SIZE + 8;
    private static final int ROW_NAME_WIDTH = 118;
    private static final int ROW_GAP = 10;
    private static final int NAME_MAX_CHARS = 36;
    private static final int VALUE_MAX_CHARS = 56;
    private static final int ACTION_BUTTON_SIZE = 28;

    private final JPanel contentPanel = new ViewportWidthPanel();

    RequestVariablesInRequestPanel(Runnable collapseAction, Runnable refreshAction) {
        setLayout(new BorderLayout(0, 0));
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(new EmptyBorder(10, 12, 10, 12));

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        ToolWindowSurfaceStyle.applyCard(contentPanel);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        ToolWindowSurfaceStyle.applyScrollPaneCard(scrollPane);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(createHeader(collapseAction, refreshAction), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    void updateRequest(HttpRequestItem request) {
        Map<VariableType, List<RequestVariableUsage>> allVariables = RequestVariableCatalog.allByType(request);
        List<RequestVariableUsage> usages = RequestVariableUsageScanner.scan(request, allVariables);
        contentPanel.removeAll();

        if (usages.isEmpty() && allVariables.isEmpty()) {
            contentPanel.add(createEmptyState());
            finishUpdate();
            return;
        }

        if (!usages.isEmpty()) {
            addCaption(I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_USED_VARIABLES));
            addGroupedVariables(groupByType(usages), true);
        }

        if (!allVariables.isEmpty()) {
            if (!usages.isEmpty()) {
                contentPanel.add(Box.createVerticalStrut(4));
            }
            addCaption(I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_ALL_VARIABLES));
            addGroupedVariables(allVariables, false);
        }

        finishUpdate();
    }

    private void addCaption(String text) {
        JLabel caption = createSectionCaption(text);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        lockPreferredHeight(caption);
        contentPanel.add(caption);
        contentPanel.add(Box.createVerticalStrut(8));
    }

    private void addGroupedVariables(Map<VariableType, List<RequestVariableUsage>> grouped, boolean includeUndefined) {
        for (VariableType type : orderedTypes()) {
            List<RequestVariableUsage> typeUsages = grouped.get(type);
            if (typeUsages != null && !typeUsages.isEmpty()) {
                contentPanel.add(createSection(type, typeUsages));
                contentPanel.add(Box.createVerticalStrut(12));
            }
        }

        if (includeUndefined) {
            List<RequestVariableUsage> undefined = grouped.get(null);
            if (undefined != null && !undefined.isEmpty()) {
                contentPanel.add(createUndefinedSection(undefined));
                contentPanel.add(Box.createVerticalStrut(12));
            }
        }
    }

    private JPanel createHeader(Runnable collapseAction, Runnable refreshAction) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        ToolWindowSurfaceStyle.applyToolWindowToolbarSeparator(header, 0, 0, 8, 0);

        JLabel titleLabel = new JLabel(I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_VARIABLES_TITLE));
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, +1));
        titleLabel.setForeground(ModernColors.getTextPrimary());
        header.add(titleLabel, BorderLayout.CENTER);

        JButton refreshButton = createActionButton(new RefreshButton(IconUtil.SIZE_SMALL));
        refreshButton.addActionListener(e -> refreshAction.run());

        JButton collapseButton = createCollapseButton();
        collapseButton.addActionListener(e -> collapseAction.run());
        header.add(ToolWindowActionToolbar.inlineRight(refreshButton, collapseButton), BorderLayout.EAST);
        return header;
    }

    private JButton createCollapseButton() {
        JButton button = new JButton(IconUtil.createThemed("icons/tool-window-hide.svg",
                IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        button.setToolTipText(I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_COLLAPSE));
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return createActionButton(button);
    }

    private JButton createActionButton(JButton button) {
        Dimension size = new Dimension(ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        return button;
    }

    private JPanel createEmptyState() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel empty = new JLabel(I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_VARIABLES_EMPTY));
        empty.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, 0));
        empty.setForeground(ModernColors.getTextSecondary());
        empty.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(empty);
        panel.add(Box.createVerticalStrut(10));

        JTextArea hint = new JTextArea(I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_VARIABLES_HINT));
        hint.setEditable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        hint.setForeground(ModernColors.getTextSecondary());
        hint.setOpaque(false);
        hint.setBorder(BorderFactory.createEmptyBorder());
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(hint);
        return panel;
    }

    private JPanel createSection(VariableType type, List<RequestVariableUsage> usages) {
        return createSectionPanel(type.getDisplayName(), type.getIconSymbol(), type.getColor(), usages);
    }

    private JPanel createUndefinedSection(List<RequestVariableUsage> usages) {
        return createSectionPanel(
                I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_UNDEFINED_VARIABLES),
                "?",
                ModernColors.getWarning(),
                usages
        );
    }

    private JPanel createSectionPanel(String title, String badgeText, Color accent, List<RequestVariableUsage> usages) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));
        header.add(createBadge(badgeText, accent), BorderLayout.WEST);
        JLabel titleLabel = new JLabel(title + " (" + usages.size() + ")");
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 0));
        titleLabel.setForeground(ModernColors.getTextPrimary());
        header.add(titleLabel, BorderLayout.CENTER);
        lockPreferredHeight(header);

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);
        rows.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (RequestVariableUsage usage : usages) {
            rows.add(createUsageRow(usage));
        }
        lockPreferredHeight(rows);

        panel.add(header);
        panel.add(rows);
        lockPreferredHeight(panel);
        return panel;
    }

    private JLabel createBadge(String text, Color accent) {
        JLabel badge = new RoundedBadgeLabel(text, accent);
        badge.setOpaque(false);
        badge.setForeground(accent);
        badge.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        badge.setPreferredSize(new Dimension(BADGE_SIZE, BADGE_SIZE));
        badge.setMinimumSize(new Dimension(BADGE_SIZE, BADGE_SIZE));
        badge.setMaximumSize(new Dimension(BADGE_SIZE, BADGE_SIZE));
        return badge;
    }

    private JPanel createUsageRow(RequestVariableUsage usage) {
        JPanel row = new JPanel(new BorderLayout(ROW_GAP, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(4, ROW_INDENT, 4, 0));

        String displayName = compact(usage.name(), NAME_MAX_CHARS);
        JLabel name = new JLabel(displayName);
        name.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, 0));
        name.setForeground(ModernColors.getTextPrimary());
        name.setToolTipText(usage.name());
        setFixedWidth(name, ROW_NAME_WIDTH);

        String valueText = usage.defined()
                ? compact(usage.value(), VALUE_MAX_CHARS)
                : I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_VARIABLE_UNRESOLVED);
        JLabel value = new JLabel(valueText);
        value.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        value.setForeground(usage.defined() ? ModernColors.getTextSecondary() : ModernColors.getWarning());
        value.setToolTipText(usage.defined() ? usage.value() : valueText);
        value.setMinimumSize(new Dimension(0, value.getPreferredSize().height));

        row.add(name, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        lockPreferredHeight(row);
        return row;
    }

    private void setFixedWidth(JComponent component, int width) {
        Dimension preferred = component.getPreferredSize();
        Dimension fixed = new Dimension(width, preferred.height);
        component.setPreferredSize(fixed);
        component.setMinimumSize(new Dimension(0, preferred.height));
        component.setMaximumSize(fixed);
    }

    private JLabel createSectionCaption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        label.setForeground(ModernColors.getTextSecondary());
        return label;
    }

    private Map<VariableType, List<RequestVariableUsage>> groupByType(List<RequestVariableUsage> usages) {
        Map<VariableType, List<RequestVariableUsage>> grouped = new LinkedHashMap<>();
        for (RequestVariableUsage usage : usages) {
            grouped.computeIfAbsent(usage.type(), ignored -> new ArrayList<>()).add(usage);
        }
        return grouped;
    }

    private List<VariableType> orderedTypes() {
        return List.of(
                VariableType.VARIABLE,
                VariableType.ITERATION_DATA,
                VariableType.GROUP,
                VariableType.ENVIRONMENT,
                VariableType.GLOBAL,
                VariableType.BUILT_IN
        );
    }

    private String compact(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replace("\r", "\\r").replace("\n", "\\n");
        if (singleLine.length() <= maxChars) {
            return singleLine;
        }
        return singleLine.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private void finishUpdate() {
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void lockPreferredHeight(JComponent component) {
        Dimension preferred = component.getPreferredSize();
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    }

    private static final class RoundedBadgeLabel extends JLabel {
        private final Color accent;

        private RoundedBadgeLabel(String text, Color accent) {
            super(text, SwingConstants.CENTER);
            this.accent = accent;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D copy = (Graphics2D) g.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(ModernColors.withAlpha(accent, ModernColors.isDarkTheme() ? 42 : 26));
            copy.fillRoundRect(0, 0, getWidth(), getHeight(), 7, 7);
            copy.dispose();
            super.paintComponent(g);
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 24, 24);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
