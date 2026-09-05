package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.component.WindowOpacitySupport;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 响应大小悬浮提示窗口。
 * <p>
 * ResponsePanel 只需要决定何时显示提示，窗口定位、淡入淡出和自动关闭集中在这里。
 */
final class ResponseSizeTooltipWindow extends JWindow {
    private static ResponseSizeTooltipWindow instance;
    private static Timer autoHideTimer;

    private ResponseSizeTooltipWindow(Window parent) {
        super(parent);
        setAlwaysOnTop(true);
        setType(Window.Type.POPUP);
    }

    static void showTooltip(Component anchor, JPanel content) {
        hideTooltip();

        Window parentWindow = SwingUtilities.getWindowAncestor(anchor);
        instance = new ResponseSizeTooltipWindow(parentWindow);
        instance.setContentPane(wrapContent(content));
        instance.pack();
        instance.setLocation(calculateLocation(anchor, instance));
        if (WindowOpacitySupport.isOpacitySupported(instance)) {
            instance.setOpacity(0f);
        }
        instance.setVisible(true);

        fadeIn(instance);
        scheduleAutoHide();
    }

    static void hideTooltip() {
        if (autoHideTimer != null) {
            autoHideTimer.stop();
            autoHideTimer = null;
        }
        if (instance == null) {
            return;
        }
        ResponseSizeTooltipWindow target = instance;
        instance = null;
        fadeOut(target);
    }

    private static JPanel wrapContent(JPanel content) {
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = Math.max(220, d.width);
                return d;
            }
        };
        ToolWindowSurfaceStyle.applyCard(wrapper);
        wrapper.setBorder(new EmptyBorder(0, 0, 0, 0));
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private static Point calculateLocation(Component anchor, Window tooltipWindow) {
        Point loc = anchor.getLocationOnScreen();
        int tooltipWidth = tooltipWindow.getWidth();
        int tooltipHeight = tooltipWindow.getHeight();
        int x = loc.x + (anchor.getWidth() - tooltipWidth) / 2;
        int y = loc.y - tooltipHeight - 6;

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration());
        x = Math.max(insets.left + 4, Math.min(x, screen.width - insets.right - tooltipWidth - 4));
        if (y < insets.top) {
            y = loc.y + anchor.getHeight() + 6;
        }
        return new Point(x, y);
    }

    private static void fadeIn(ResponseSizeTooltipWindow tooltipWindow) {
        if (!WindowOpacitySupport.isOpacitySupported(tooltipWindow)) {
            return;
        }
        Timer fadeIn = new Timer(20, null);
        fadeIn.addActionListener(e -> {
            if (instance == null) {
                ((Timer) e.getSource()).stop();
                return;
            }
            float opacity = Math.min(1f, tooltipWindow.getOpacity() + 0.1f);
            try {
                tooltipWindow.setOpacity(opacity);
            } catch (UnsupportedOperationException | IllegalComponentStateException ignored) {
                ((Timer) e.getSource()).stop();
                return;
            }
            if (opacity >= 1f) {
                ((Timer) e.getSource()).stop();
            }
        });
        fadeIn.start();
    }

    private static void fadeOut(ResponseSizeTooltipWindow tooltipWindow) {
        if (!WindowOpacitySupport.isOpacitySupported(tooltipWindow)) {
            tooltipWindow.setVisible(false);
            tooltipWindow.dispose();
            return;
        }
        Timer fadeOut = new Timer(20, null);
        fadeOut.addActionListener(e -> {
            float opacity = Math.max(0f, tooltipWindow.getOpacity() - 0.12f);
            try {
                tooltipWindow.setOpacity(opacity);
            } catch (UnsupportedOperationException | IllegalComponentStateException ignored) {
                ((Timer) e.getSource()).stop();
                tooltipWindow.setVisible(false);
                tooltipWindow.dispose();
                return;
            }
            if (opacity <= 0f) {
                ((Timer) e.getSource()).stop();
                tooltipWindow.setVisible(false);
                tooltipWindow.dispose();
            }
        });
        fadeOut.start();
    }

    private static void scheduleAutoHide() {
        if (autoHideTimer != null) {
            autoHideTimer.stop();
        }
        autoHideTimer = new Timer(10000, e -> hideTooltip());
        autoHideTimer.setRepeats(false);
        autoHideTimer.start();
    }
}
