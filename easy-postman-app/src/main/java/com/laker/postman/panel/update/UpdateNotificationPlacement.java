package com.laker.postman.panel.update;

import lombok.experimental.UtilityClass;

import javax.swing.*;
import java.awt.*;

@UtilityClass
class UpdateNotificationPlacement {
    private static final int EDGE_MARGIN = 24;
    private static final int BOTTOM_CLEARANCE = 36;
    private static final int RIGHT_CLEARANCE = 34;

    void positionWindow(Window window, JFrame parent) {
        Rectangle anchor = parentAnchorBoundsOnScreen(parent);
        int width = window.getWidth();
        int height = window.getHeight();

        int x = anchor.x + anchor.width - width - EDGE_MARGIN - RIGHT_CLEARANCE;
        int y = anchor.y + anchor.height - height - EDGE_MARGIN - BOTTOM_CLEARANCE;

        GraphicsConfiguration graphicsConfiguration = parent.getGraphicsConfiguration();
        if (graphicsConfiguration != null) {
            Rectangle usableScreen = usableScreenBounds(graphicsConfiguration);
            int minX = Math.max(anchor.x + EDGE_MARGIN, usableScreen.x + EDGE_MARGIN);
            int minY = Math.max(anchor.y + EDGE_MARGIN, usableScreen.y + EDGE_MARGIN);
            int maxX = Math.min(anchor.x + anchor.width - width - EDGE_MARGIN,
                    usableScreen.x + usableScreen.width - width - EDGE_MARGIN);
            int maxY = Math.min(anchor.y + anchor.height - height - EDGE_MARGIN,
                    usableScreen.y + usableScreen.height - height - EDGE_MARGIN);
            x = clamp(x, minX, maxX);
            y = clamp(y, minY, maxY);
        }
        window.setLocation(x, y);
    }

    private Rectangle parentAnchorBoundsOnScreen(JFrame parent) {
        Component anchor = parent.getContentPane() != null ? parent.getContentPane() : parent.getRootPane();
        if (anchor == null || !anchor.isShowing() || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) {
            return parent.getBounds();
        }
        try {
            Point location = anchor.getLocationOnScreen();
            return new Rectangle(location.x, location.y, anchor.getWidth(), anchor.getHeight());
        } catch (IllegalComponentStateException ignored) {
            return parent.getBounds();
        }
    }

    private Rectangle usableScreenBounds(GraphicsConfiguration graphicsConfiguration) {
        Rectangle screen = graphicsConfiguration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
        return new Rectangle(
                screen.x + insets.left,
                screen.y + insets.top,
                screen.width - insets.left - insets.right,
                screen.height - insets.top - insets.bottom
        );
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
