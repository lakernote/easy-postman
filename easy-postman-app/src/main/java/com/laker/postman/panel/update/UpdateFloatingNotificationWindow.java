package com.laker.postman.panel.update;

import com.laker.postman.common.component.WindowOpacitySupport;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

class UpdateFloatingNotificationWindow {
    static final int ACTION_DELAY_MS = 300;

    private static final float FADE_STEP = 0.05f;
    private static final int FADE_TIMER_DELAY = 10;

    private final JWindow window;
    private final JFrame parent;
    private final int displayDurationMs;
    private final boolean opacitySupported;
    private Timer fadeTimer;
    private Timer autoCloseTimer;
    private float opacity = 0f;
    private ComponentAdapter parentMoveListener;

    UpdateFloatingNotificationWindow(JFrame parent, int displayDurationMs) {
        this.parent = parent;
        this.displayDurationMs = displayDurationMs;
        this.window = new JWindow(parent);
        this.opacitySupported = WindowOpacitySupport.isOpacitySupported(window);
        configureWindow();
    }

    void installContent(JPanel contentPanel, int width) {
        window.setContentPane(contentPanel);
        window.pack();
        window.setSize(width, window.getHeight());
        positionWindow();
        registerParentMoveListener();
    }

    void display() {
        window.setVisible(true);
        fadeIn();
        resumeAutoClose();
    }

    void pauseAutoClose() {
        stopAutoCloseTimer();
    }

    void resumeAutoClose() {
        stopAutoCloseTimer();
        autoCloseTimer = new Timer(displayDurationMs, e -> fadeOut());
        autoCloseTimer.setRepeats(false);
        autoCloseTimer.start();
    }

    void fadeOut() {
        stopFadeTimer();
        stopAutoCloseTimer();
        if (!opacitySupported) {
            cleanupAndClose();
            return;
        }
        fadeTimer = new Timer(FADE_TIMER_DELAY, null);
        fadeTimer.addActionListener(e -> {
            opacity = Math.max(opacity - FADE_STEP, 0f);
            try {
                window.setOpacity(opacity);
            } catch (UnsupportedOperationException | IllegalComponentStateException ignored) {
                stopFadeTimer();
                cleanupAndClose();
                return;
            }
            if (opacity <= 0f) {
                stopFadeTimer();
                cleanupAndClose();
            }
        });
        fadeTimer.start();
    }

    void fadeOutThen(Runnable action) {
        fadeOut();
        Timer delayTimer = new Timer(ACTION_DELAY_MS, e -> action.run());
        delayTimer.setRepeats(false);
        delayTimer.start();
    }

    private void configureWindow() {
        window.setFocusableWindowState(false);
        window.setType(Window.Type.UTILITY);
        if (opacitySupported) {
            window.setOpacity(0f);
        } else {
            opacity = 1f;
        }
        window.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
        window.getRootPane().setOpaque(false);
        window.getLayeredPane().setOpaque(false);
        window.setBackground(new Color(0, 0, 0, 0));
    }

    private void fadeIn() {
        stopFadeTimer();
        if (!opacitySupported) {
            return;
        }
        fadeTimer = new Timer(FADE_TIMER_DELAY, null);
        fadeTimer.addActionListener(e -> {
            opacity = Math.min(opacity + FADE_STEP, 1.0f);
            try {
                window.setOpacity(opacity);
            } catch (UnsupportedOperationException | IllegalComponentStateException ignored) {
                stopFadeTimer();
                opacity = 1f;
                return;
            }
            if (opacity >= 1.0f) {
                stopFadeTimer();
            }
        });
        fadeTimer.start();
    }

    private void stopFadeTimer() {
        if (fadeTimer != null) {
            fadeTimer.stop();
            fadeTimer = null;
        }
    }

    private void stopAutoCloseTimer() {
        if (autoCloseTimer != null) {
            autoCloseTimer.stop();
            autoCloseTimer = null;
        }
    }

    private void cleanupAndClose() {
        stopFadeTimer();
        stopAutoCloseTimer();
        if (parentMoveListener != null) {
            parent.removeComponentListener(parentMoveListener);
            parentMoveListener = null;
        }
        window.dispose();
    }

    private void registerParentMoveListener() {
        parentMoveListener = new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                positionWindow();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                positionWindow();
            }
        };
        parent.addComponentListener(parentMoveListener);
    }

    private void positionWindow() {
        UpdateNotificationPlacement.positionWindow(window, parent);
    }
}
