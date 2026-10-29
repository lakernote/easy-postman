package com.laker.postman.panel.performance.control;


import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.component.button.RefreshButton;
import com.laker.postman.common.component.button.StartButton;
import com.laker.postman.common.component.button.StopButton;
import com.laker.postman.common.constants.ModernColors;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceRunUiController {

    public static final String ETA_STATUS_ICON = "icons/time.svg";
    public static final String REQUEST_STATUS_ICON = "icons/execution-count.svg";
    private static final String RUN_STATUS_ICON_PROPERTY = "easyPostman.runStatusIconPath";
    private static final int STATUS_ICON_SIZE = 18;
    private static final int STATUS_ICON_ANIMATION_DELAY_MS = 40;
    private static final double STATUS_ICON_ROTATION_STEP_DEGREES = 12.0;

    private final StartButton runButton;
    private final StopButton stopButton;
    private final RefreshButton refreshButton;
    private final List<JComponent> runLockedComponents;
    private final AtomicLong lastProgressSequence = new AtomicLong(0L);
    private Timer statusIconAnimationTimer;

    public PerformanceRunUiController(StartButton runButton,
                                      StopButton stopButton,
                                      RefreshButton refreshButton) {
        this(runButton, stopButton, refreshButton, List.of());
    }

    public PerformanceRunUiController(StartButton runButton,
                                      StopButton stopButton,
                                      RefreshButton refreshButton,
                                      List<JComponent> runLockedComponents) {
        this.runButton = runButton;
        this.stopButton = stopButton;
        this.refreshButton = refreshButton;
        this.runLockedComponents = runLockedComponents == null ? List.of() : List.copyOf(runLockedComponents);
    }

    public void markRunning() {
        runButton.setEnabled(false);
        stopButton.setEnabled(true);
        refreshButton.setEnabled(false);
        setRunLockedComponentsEnabled(false);
    }

    public void markIdle() {
        runButton.setEnabled(true);
        stopButton.setEnabled(false);
        refreshButton.setEnabled(true);
        setRunLockedComponentsEnabled(true);
    }

    public void initializeProgress(JLabel progressLabel, int totalThreads) {
        lastProgressSequence.set(0L);
        setProgressText(progressLabel, formatProgress(0, totalThreads));
    }

    public void updateProgressAsync(JLabel progressLabel, int activeThreads, int totalThreads) {
        SwingUtilities.invokeLater(() -> setProgressText(progressLabel, formatProgress(activeThreads, totalThreads)));
    }

    public void updateProgressAsync(JLabel progressLabel, int activeThreads, int totalThreads, long sequence) {
        if (sequence <= 0L) {
            updateProgressAsync(progressLabel, activeThreads, totalThreads);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (sequence < lastProgressSequence.get()) {
                return;
            }
            lastProgressSequence.set(sequence);
            setProgressText(progressLabel, formatProgress(activeThreads, totalThreads));
        });
    }

    public void updateRunStatus(JLabel statusLabel,
                                String statusText,
                                String iconPath) {
        updateRunStatus(statusLabel, statusText, statusText, iconPath, false);
    }

    public void updateRunStatus(JLabel statusLabel,
                                String statusText,
                                String tooltipText,
                                String iconPath) {
        updateRunStatus(statusLabel, statusText, tooltipText, iconPath, false);
    }

    public void updateRunStatus(JLabel statusLabel,
                                String statusText,
                                String tooltipText,
                                String iconPath,
                                boolean animateIcon) {
        if (statusLabel == null) {
            return;
        }
        if ((statusText == null || statusText.isBlank())
                && (iconPath == null || iconPath.isBlank())) {
            clearRunStatus(statusLabel);
            return;
        }
        statusLabel.setVisible(true);
        statusLabel.setText(statusText == null ? "" : statusText);
        statusLabel.setToolTipText(tooltipText == null || tooltipText.isBlank() ? statusText : tooltipText);
        setStatusIcon(statusLabel, iconPath, animateIcon);
        statusLabel.revalidate();
        statusLabel.repaint();
    }

    public void updateRunStatusAsync(JLabel statusLabel,
                                     String statusText,
                                     String iconPath) {
        SwingUtilities.invokeLater(() -> updateRunStatus(statusLabel, statusText, iconPath));
    }

    public void clearRunStatus(JLabel statusLabel) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setVisible(false);
        statusLabel.setText("");
        statusLabel.setToolTipText(null);
        stopStatusIconAnimation();
        statusLabel.setIcon(null);
        statusLabel.putClientProperty(RUN_STATUS_ICON_PROPERTY, null);
        statusLabel.revalidate();
        statusLabel.repaint();
    }

    private String formatProgress(int activeThreads, int totalThreads) {
        return activeThreads + "/" + totalThreads;
    }

    private void setProgressText(JLabel progressLabel, String text) {
        if (progressLabel == null) {
            return;
        }
        String safeText = text == null || text.isBlank() ? "0/0" : text;
        progressLabel.setText(safeText);
        progressLabel.setToolTipText(safeText);
    }

    private void setStatusIcon(JLabel label, String iconPath, boolean animateIcon) {
        if (label == null || iconPath == null || iconPath.isBlank()) {
            return;
        }
        Object currentPath = label.getClientProperty(RUN_STATUS_ICON_PROPERTY);
        String iconState = iconPath + (animateIcon ? "|animated" : "");
        if (Objects.equals(currentPath, iconState)) {
            return;
        }
        stopStatusIconAnimation();
        FlatSVGIcon svgIcon = new FlatSVGIcon(iconPath, STATUS_ICON_SIZE, STATUS_ICON_SIZE)
                .setColorFilter(new FlatSVGIcon.ColorFilter(color -> ModernColors.getTextPrimary()));
        if (animateIcon) {
            RotatingIcon rotatingIcon = new RotatingIcon(svgIcon);
            label.setIcon(rotatingIcon);
            statusIconAnimationTimer = new Timer(STATUS_ICON_ANIMATION_DELAY_MS, event -> {
                rotatingIcon.advance(STATUS_ICON_ROTATION_STEP_DEGREES);
                label.repaint();
            });
            statusIconAnimationTimer.start();
        } else {
            label.setIcon(svgIcon);
        }
        label.putClientProperty(RUN_STATUS_ICON_PROPERTY, iconState);
    }

    private void stopStatusIconAnimation() {
        if (statusIconAnimationTimer != null) {
            statusIconAnimationTimer.stop();
            statusIconAnimationTimer = null;
        }
    }

    private static final class RotatingIcon implements javax.swing.Icon {
        private final javax.swing.Icon delegate;
        private double angle;

        private RotatingIcon(javax.swing.Icon delegate) {
            this.delegate = delegate;
        }

        private void advance(double degrees) {
            angle = (angle + degrees) % 360.0;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D rotated = (Graphics2D) graphics.create();
            rotated.rotate(
                    Math.toRadians(angle),
                    x + getIconWidth() / 2.0,
                    y + getIconHeight() / 2.0
            );
            delegate.paintIcon(component, rotated, x, y);
            rotated.dispose();
        }

        @Override
        public int getIconWidth() {
            return delegate.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return delegate.getIconHeight();
        }
    }

    private void setRunLockedComponentsEnabled(boolean enabled) {
        for (JComponent component : runLockedComponents) {
            if (component != null) {
                component.setEnabled(enabled);
            }
        }
    }
}
