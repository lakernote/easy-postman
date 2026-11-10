package com.laker.postman.panel.collections.editor;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.constants.Icons;
import com.laker.postman.service.setting.ShortcutManager;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

/**
 * Empty state shown by the request editor plus tab.
 */
public class RequestEditorEmptyStatePanel extends JPanel {
    private static final int CARD_RADIUS = 16;
    private static final int CARD_PADDING = 48;
    static final Insets EMPTY_STATE_INSETS = new Insets(12, 20, 12, 20);
    private static final int ICON_SIZE = 96;
    private static final int ICON_RING_SIZE = ICON_SIZE + 32;
    private static final Dimension ICON_CONTAINER_SIZE = new Dimension(ICON_SIZE + 48, ICON_SIZE + 48);
    private static final int HINT_BUTTON_RADIUS = 24;

    private static final List<ShortcutHint> SHORTCUT_HINTS = List.of(
            new ShortcutHint(MessageKeys.COLLECTIONS_MENU_ADD_REQUEST, ShortcutManager.NEW_REQUEST),
            new ShortcutHint(MessageKeys.SAVE_REQUEST, ShortcutManager.SAVE_REQUEST),
            new ShortcutHint(MessageKeys.TAB_CLOSE_CURRENT, ShortcutManager.CLOSE_CURRENT_TAB),
            new ShortcutHint(MessageKeys.TAB_CLOSE_OTHERS, ShortcutManager.CLOSE_OTHER_TABS),
            new ShortcutHint(MessageKeys.TAB_CLOSE_ALL, ShortcutManager.CLOSE_ALL_TABS),
            new ShortcutHint(MessageKeys.EXIT_APP, ShortcutManager.EXIT_APP)
    );

    private final Runnable newRequestAction;

    public RequestEditorEmptyStatePanel() {
        this(RequestEditorEmptyStatePanel::openNewRequestTab);
    }

    RequestEditorEmptyStatePanel(Runnable newRequestAction) {
        this.newRequestAction = Objects.requireNonNull(newRequestAction, "newRequestAction");
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(BorderFactory.createEmptyBorder(
                EMPTY_STATE_INSETS.top,
                EMPTY_STATE_INSETS.left,
                EMPTY_STATE_INSETS.bottom,
                EMPTY_STATE_INSETS.right));
        add(createCardPanel(), BorderLayout.CENTER);
    }

    private JPanel createCardPanel() {
        JPanel cardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                paintCard((Graphics2D) g.create(), getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        cardPanel.setOpaque(false);
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));
        cardPanel.setBorder(BorderFactory.createEmptyBorder(CARD_PADDING, CARD_PADDING, CARD_PADDING, CARD_PADDING));
        addCenteredContent(cardPanel);
        return cardPanel;
    }

    private void addCenteredContent(JPanel cardPanel) {
        cardPanel.add(Box.createVerticalGlue());
        cardPanel.add(createLogoPanel());
        cardPanel.add(Box.createVerticalStrut(32));
        cardPanel.add(createTitleLabel());
        cardPanel.add(Box.createVerticalStrut(16));
        cardPanel.add(createHintButton());
        cardPanel.add(Box.createVerticalStrut(32));
        cardPanel.add(createShortcutPanel());
        cardPanel.add(Box.createVerticalGlue());
    }

    private void paintCard(Graphics2D g2, int width, int height) {
        if (width <= 0 || height <= 0) {
            g2.dispose();
            return;
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        paintCardShadow(g2, width, height);
        paintCardBackground(g2, width, height);
        paintTopAccent(g2, width);
        paintCardBorder(g2, width, height);
        g2.dispose();
    }

    private void paintCardShadow(Graphics2D g2, int width, int height) {
        int[] offsets = {6, 4, 2};
        int[] alphas = {8, 12, 18};
        for (int i = 0; i < offsets.length; i++) {
            int offset = offsets[i];
            g2.setColor(RequestEditorEmptyStateTheme.cardShadow(alphas[i]));
            g2.fillRoundRect(offset, offset, width - offset, height - offset,
                    CARD_RADIUS + offset / 2, CARD_RADIUS + offset / 2);
        }
    }

    private void paintCardBackground(Graphics2D g2, int width, int height) {
        g2.setColor(RequestEditorEmptyStateTheme.cardBackground());
        g2.fillRoundRect(0, 0, width, height, CARD_RADIUS, CARD_RADIUS);
    }

    private void paintTopAccent(Graphics2D g2, int width) {
        Shape oldClip = g2.getClip();
        g2.setClip(0, 0, width, 3);
        g2.setPaint(RequestEditorEmptyStateTheme.topAccentGradient(width));
        g2.fillRoundRect(0, 0, width, CARD_RADIUS, CARD_RADIUS, CARD_RADIUS);
        g2.setClip(oldClip);
    }

    private void paintCardBorder(Graphics2D g2, int width, int height) {
        g2.setColor(RequestEditorEmptyStateTheme.cardBorder());
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(0, 0, width - 1, height - 1, CARD_RADIUS, CARD_RADIUS);
    }

    private JPanel createLogoPanel() {
        JPanel logoPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                paintLogoBackground((Graphics2D) g.create(), getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        logoPanel.setOpaque(false);
        logoPanel.setPreferredSize(ICON_CONTAINER_SIZE);
        logoPanel.setMaximumSize(ICON_CONTAINER_SIZE);
        logoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel logoLabel = new JLabel(new ImageIcon(Icons.LOGO.getImage()
                .getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH)));
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoPanel.add(logoLabel, BorderLayout.CENTER);
        return logoPanel;
    }

    private void paintLogoBackground(Graphics2D g2, int width, int height) {
        if (width <= 0 || height <= 0) {
            g2.dispose();
            return;
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int x = (width - ICON_RING_SIZE) / 2;
        int y = (height - ICON_RING_SIZE) / 2;

        g2.setColor(RequestEditorEmptyStateTheme.logoOuterGlow());
        g2.fillOval(x - 8, y - 8, ICON_RING_SIZE + 16, ICON_RING_SIZE + 16);
        g2.setColor(RequestEditorEmptyStateTheme.logoMiddleGlow());
        g2.fillOval(x - 4, y - 4, ICON_RING_SIZE + 8, ICON_RING_SIZE + 8);
        g2.setColor(RequestEditorEmptyStateTheme.logoBackground());
        g2.fillOval(x, y, ICON_RING_SIZE, ICON_RING_SIZE);
        g2.setColor(RequestEditorEmptyStateTheme.logoBorder());
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(x + 1, y + 1, ICON_RING_SIZE - 2, ICON_RING_SIZE - 2);
        g2.dispose();
    }

    private JLabel createTitleLabel() {
        JLabel label = new JLabel(I18nUtil.getMessage(MessageKeys.CREATE_NEW_REQUEST));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setForeground(RequestEditorEmptyStateTheme.titleForeground());
        label.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 13));
        return label;
    }

    private JLabel createHintButton() {
        RequestEditorHintButton hintButton = new RequestEditorHintButton(
                I18nUtil.getMessage(MessageKeys.REQUEST_EDITOR_EMPTY_STATE_HINT));
        hintButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        hintButton.setHorizontalAlignment(SwingConstants.CENTER);
        hintButton.setForeground(RequestEditorEmptyStateTheme.hintForeground());
        hintButton.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 2));
        hintButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        hintButton.setBorder(BorderFactory.createEmptyBorder(12, 32, 12, 32));
        hintButton.setOpaque(false);
        hintButton.setFocusable(true);
        hintButton.addMouseListener(createHintButtonMouseListener(hintButton));
        return hintButton;
    }

    private MouseAdapter createHintButtonMouseListener(RequestEditorHintButton hintButton) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    newRequestAction.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hintButton.setForeground(RequestEditorEmptyStateTheme.hintHoverForeground());
                hintButton.setHovered(true);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hintButton.setForeground(RequestEditorEmptyStateTheme.hintForeground());
                hintButton.setHovered(false);
                setCursor(Cursor.getDefaultCursor());
            }
        };
    }

    private JPanel createShortcutPanel() {
        JPanel shortcutPanel = new JPanel();
        shortcutPanel.setOpaque(false);
        shortcutPanel.setLayout(new BoxLayout(shortcutPanel, BoxLayout.Y_AXIS));
        for (ShortcutHint shortcutHint : SHORTCUT_HINTS) {
            if (!ShortcutManager.isAssigned(shortcutHint.shortcutId)) {
                continue;
            }
            shortcutPanel.add(createShortcutLabel(shortcutHint));
            shortcutPanel.add(Box.createVerticalStrut(8));
        }
        return shortcutPanel;
    }

    private JLabel createShortcutLabel(ShortcutHint shortcutHint) {
        String actionName = I18nUtil.getMessage(shortcutHint.actionMessageKey);
        String shortcutText = ShortcutManager.getShortcutText(shortcutHint.shortcutId);
        JLabel label = new JLabel(I18nUtil.getMessage(MessageKeys.SHORTCUT_LABEL_FORMAT, actionName, shortcutText));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
        label.setForeground(RequestEditorEmptyStateTheme.shortcutForeground());
        return label;
    }

    private static void openNewRequestTab() {
        UiSingletonFactory.getInstance(RequestEditorPanel.class)
                .addNewTab(I18nUtil.getMessage(MessageKeys.NEW_REQUEST));
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class ShortcutHint {
        private final String actionMessageKey;
        private final String shortcutId;
    }

    private static final class RequestEditorHintButton extends JLabel {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private boolean hovered;

        private RequestEditorHintButton(String text) {
            super(text);
        }

        private void setHovered(boolean hovered) {
            if (this.hovered != hovered) {
                this.hovered = hovered;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hovered) {
                paintHoverBackground(g2);
            } else {
                paintDefaultBackground(g2);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        private void paintHoverBackground(Graphics2D g2) {
            g2.setPaint(RequestEditorEmptyStateTheme.hintHoverGradient(getWidth()));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), HINT_BUTTON_RADIUS, HINT_BUTTON_RADIUS);
            g2.setPaint(RequestEditorEmptyStateTheme.hintHoverHighlight(getHeight()));
            g2.fillRoundRect(0, 0, getWidth(), getHeight() / 2, HINT_BUTTON_RADIUS, HINT_BUTTON_RADIUS);
        }

        private void paintDefaultBackground(Graphics2D g2) {
            g2.setColor(RequestEditorEmptyStateTheme.hintBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), HINT_BUTTON_RADIUS, HINT_BUTTON_RADIUS);
            g2.setColor(RequestEditorEmptyStateTheme.hintBorder());
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, HINT_BUTTON_RADIUS, HINT_BUTTON_RADIUS);
        }
    }
}
