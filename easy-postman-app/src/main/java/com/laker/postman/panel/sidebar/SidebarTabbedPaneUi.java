package com.laker.postman.panel.sidebar;

import com.laker.postman.common.constants.ModernColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * 侧边栏专用 TabbedPane 绘制策略。
 */
final class SidebarTabbedPaneUi extends BasicTabbedPaneUI {
    private final BooleanSupplier sidebarExpandedSupplier;
    private final IntSupplier expandedTabWidthSupplier;
    private final IntSupplier collapsedTabWidthSupplier;
    private final IntUnaryOperator expandedTabHeightSupplier;
    private final IntSupplier collapsedTabHeightSupplier;

    SidebarTabbedPaneUi(BooleanSupplier sidebarExpandedSupplier,
                        IntSupplier expandedTabWidthSupplier,
                        IntSupplier collapsedTabWidthSupplier,
                        IntUnaryOperator expandedTabHeightSupplier,
                        IntSupplier collapsedTabHeightSupplier) {
        this.sidebarExpandedSupplier = sidebarExpandedSupplier;
        this.expandedTabWidthSupplier = expandedTabWidthSupplier;
        this.collapsedTabWidthSupplier = collapsedTabWidthSupplier;
        this.expandedTabHeightSupplier = expandedTabHeightSupplier;
        this.collapsedTabHeightSupplier = collapsedTabHeightSupplier;
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabAreaInsets = new Insets(
                SidebarTabMetrics.TAB_AREA_INSET_TOP,
                SidebarTabMetrics.TAB_AREA_INSET_LEFT,
                SidebarTabMetrics.TAB_AREA_INSET_BOTTOM,
                SidebarTabMetrics.TAB_AREA_INSET_RIGHT
        );
        contentBorderInsets = new Insets(0, 0, 0, 0);
        tabInsets = new Insets(1, 0, 1, 0);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
    }

    @Override
    protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
        if (tabPlacement == SwingConstants.LEFT && tabPane != null) {
            int tabAreaWidth = calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
            g.setColor(SidebarTheme.railBackground());
            g.fillRect(0, 0, tabAreaWidth + 1, tabPane.getHeight());
        }
        super.paintTabArea(g, tabPlacement, selectedIndex);
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
        boolean hovered = tabIndex == getRolloverTab() && tabPane != null && tabPane.isEnabledAt(tabIndex);
        if (!isSelected && !hovered) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            enableHighQualityRendering(g2);
            if (isSelected) {
                paintSelectedTabBackground(g2, x, y, w, h);
            } else {
                paintHoverTabBackground(g2, x, y, w, h);
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                  int x, int y, int w, int h, boolean isSelected) {
        // 侧边栏 tab 不绘制边框，选中态由圆角背景和图标颜色表达。
    }

    @Override
    protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                                       int tabIndex, Rectangle iconRect, Rectangle textRect,
                                       boolean isSelected) {
        // 侧边栏 tab 的 focus 态由选中背景表达，不额外绘制虚线框。
    }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        // IntelliJ-style tool windows separate regions with spacing and rounded panels, not hard divider lines.
    }

    @Override
    protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
        return sidebarExpandedSupplier.getAsBoolean()
                ? expandedTabWidthSupplier.getAsInt()
                : collapsedTabWidthSupplier.getAsInt();
    }

    @Override
    protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
        return sidebarExpandedSupplier.getAsBoolean()
                ? expandedTabHeightSupplier.applyAsInt(fontHeight)
                : collapsedTabHeightSupplier.getAsInt();
    }

    @Override
    protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
        if (!sidebarExpandedSupplier.getAsBoolean()) {
            // BasicTabbedPaneUI defaults to -1 for the selected LEFT tab and +1 for the others.
            // That makes the icon jump left as soon as it is selected. Keep every collapsed icon
            // on the same visual center regardless of selection state.
            return SidebarTabMetrics.COLLAPSED_TAB_LABEL_SHIFT_X;
        }
        return super.getTabLabelShiftX(tabPlacement, tabIndex, isSelected);
    }

    @Override
    protected JButton createScrollButton(int direction) {
        return new SidebarScrollButton(direction);
    }

    private void enableHighQualityRendering(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private void paintSelectedTabBackground(Graphics2D g2, int x, int y, int w, int h) {
        Rectangle bounds = tabStateBounds(x, y, w, h);

        g2.setColor(sidebarExpandedSupplier.getAsBoolean()
                ? SidebarTheme.selectedExpandedTabBackground()
                : SidebarTheme.selectedCollapsedTabBackground());
        g2.fillRoundRect(
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                SidebarTabMetrics.SELECTED_BACKGROUND_ARC,
                SidebarTabMetrics.SELECTED_BACKGROUND_ARC
        );
    }

    private void paintHoverTabBackground(Graphics2D g2, int x, int y, int w, int h) {
        Rectangle bounds = tabStateBounds(x, y, w, h);

        g2.setColor(SidebarTheme.hoverTabBackground());
        g2.fillRoundRect(
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                SidebarTabMetrics.SELECTED_BACKGROUND_ARC,
                SidebarTabMetrics.SELECTED_BACKGROUND_ARC
        );
    }

    private Rectangle tabStateBounds(int x, int y, int w, int h) {
        if (!sidebarExpandedSupplier.getAsBoolean()) {
            return collapsedTabStateBounds(x, y, w, h);
        }

        int insetY = SidebarTabMetrics.EXPANDED_SELECTED_BACKGROUND_INSET_VERTICAL;
        return new Rectangle(
                SidebarTabMetrics.expandedSelectedBackgroundX(x),
                y + insetY,
                SidebarTabMetrics.expandedSelectedBackgroundWidth(w),
                Math.max(0, h - insetY * 2)
        );
    }

    private Rectangle collapsedTabStateBounds(int x, int y, int w, int h) {
        int backgroundWidth = Math.min(SidebarTabMetrics.COLLAPSED_SELECTED_BACKGROUND_WIDTH, w);
        int backgroundHeight = Math.min(SidebarTabMetrics.COLLAPSED_SELECTED_BACKGROUND_HEIGHT, h);
        backgroundWidth = Math.max(0, backgroundWidth);
        backgroundHeight = Math.max(0, backgroundHeight);
        return new Rectangle(
                SidebarTabMetrics.collapsedSelectedBackgroundX(x, w, backgroundWidth),
                y + Math.max(0, (h - backgroundHeight) / 2),
                backgroundWidth,
                backgroundHeight
        );
    }

    private static final class SidebarScrollButton extends BasicArrowButton implements UIResource {
        private final int direction;

        private SidebarScrollButton(int direction) {
            super(direction,
                    UIManager.getColor("TabbedPane.selected"),
                    UIManager.getColor("TabbedPane.shadow"),
                    UIManager.getColor("TabbedPane.darkShadow"),
                    UIManager.getColor("TabbedPane.highlight"));
            this.direction = direction;
            setFocusable(false);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(0, 0, 0, 0));
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                ButtonModel model = getModel();
                boolean hovered = model.isRollover() && isEnabled();
                boolean pressed = (model.isPressed() || model.isArmed()) && isEnabled();
                boolean enabled = isEnabled();

                int inset = 2;
                int width = Math.max(0, getWidth() - inset * 2);
                int height = Math.max(0, getHeight() - inset * 2);

                if (hovered || pressed) {
                    Color background = pressed ? ModernColors.getButtonPressedColor() : ModernColors.getHoverBackgroundColor();
                    g2.setColor(ModernColors.withAlpha(background, 230));
                    g2.fillRoundRect(inset, inset, width, height, 8, 8);
                }

                Color arrowColor;
                if (!enabled) {
                    arrowColor = ModernColors.getTextDisabled();
                } else if (pressed || hovered) {
                    arrowColor = ModernColors.getPrimary();
                } else {
                    arrowColor = ModernColors.getTextSecondary();
                }

                g2.setColor(arrowColor);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                paintChevron(g2, direction, getWidth(), getHeight());
            } finally {
                g2.dispose();
            }
        }

        private void paintChevron(Graphics2D g2, int direction, int width, int height) {
            int midX = width / 2;
            int midY = height / 2;
            int size = 4;
            Path2D.Float chevron = new Path2D.Float();

            switch (direction) {
                case SwingConstants.NORTH -> {
                    chevron.moveTo(midX - size, midY + 2);
                    chevron.lineTo(midX, midY - size);
                    chevron.lineTo(midX + size, midY + 2);
                }
                case SwingConstants.SOUTH -> {
                    chevron.moveTo(midX - size, midY - 2);
                    chevron.lineTo(midX, midY + size);
                    chevron.lineTo(midX + size, midY - 2);
                }
                case SwingConstants.WEST -> {
                    chevron.moveTo(midX + 2, midY - size);
                    chevron.lineTo(midX - size, midY);
                    chevron.lineTo(midX + 2, midY + size);
                }
                default -> {
                    chevron.moveTo(midX - 2, midY - size);
                    chevron.lineTo(midX + size, midY);
                    chevron.lineTo(midX - 2, midY + size);
                }
            }

            g2.draw(chevron);
        }

    }
}
