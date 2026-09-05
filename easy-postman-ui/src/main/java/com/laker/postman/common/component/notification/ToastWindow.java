package com.laker.postman.common.component.notification;

import com.laker.postman.common.component.WindowOpacitySupport;
import com.laker.postman.model.NotificationPosition;
import com.laker.postman.util.CommonI18n;
import com.laker.postman.util.CommonMessageKeys;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

class ToastWindow extends JWindow {
    private final Window parentWindow;
    private final NotificationCenter.NotificationType type;
    private final NotificationPosition position;
    private final String fullMessage;
    private final String title;
    private final Consumer<ToastWindow> onClosed;
    private final Runnable onLayoutChanged;

    private int stackOffset = 0;
    private int toastWidth;
    private boolean hovered = false;
    private boolean expanded = false;
    private boolean closed = false;
    private boolean opacitySupported;
    private int slideStep = 0;
    private Point targetPosition;

    private Timer autoCloseTimer;
    private Timer slideTimer;
    private Timer fadeTimer;
    private JTextArea bodyLabel;
    private JScrollPane bodyScrollPane;
    private JPanel rootPanel;
    private JButton closeButton;
    private JButton expandButton;
    private JButton copyButton;

    private long pausedTime = 0;
    private long startTime = 0;
    private int totalDuration = 0;

    ToastWindow(Window parentWindow,
                String message,
                String customTitle,
                NotificationCenter.NotificationType type,
                int seconds,
                NotificationPosition position,
                Consumer<ToastWindow> onClosed,
                Runnable onLayoutChanged) {
        super(parentWindow);
        this.parentWindow = parentWindow;
        this.type = type;
        this.position = position;
        this.fullMessage = message;
        this.onClosed = onClosed;
        this.onLayoutChanged = onLayoutChanged;

        opacitySupported = WindowOpacitySupport.isOpacitySupported(this);
        configureWindow();
        title = customTitle != null && !customTitle.isBlank() ? customTitle : type.getDefaultTitle();
        String displayMessage = ToastTextFormatter.displayText(message, false);
        toastWidth = ToastStyle.preferredWidth(title, displayMessage);
        rootPanel = buildRootPanel(displayMessage, seconds);
        setContentPane(rootPanel);
        refreshWindowSize();
        targetPosition = calculatePosition(stackOffset);
    }

    void startShow() {
        Point startPosition = ToastPlacement.slideStart(targetPosition, getSize(), position);
        setLocation(startPosition);
        setVisible(true);

        slideStep = 0;
        slideTimer = new Timer(ToastStyle.SLIDE_INTERVAL, e -> {
            slideStep++;
            double progress = (double) slideStep / ToastStyle.SLIDE_STEPS;
            double eased = 1 - Math.pow(1 - progress, 3);
            int x = startPosition.x + (int) ((targetPosition.x - startPosition.x) * eased);
            int y = startPosition.y + (int) ((targetPosition.y - startPosition.y) * eased);
            setLocation(x, y);
            if (slideStep >= ToastStyle.SLIDE_STEPS) {
                ((Timer) e.getSource()).stop();
                setLocation(targetPosition);
                if (totalDuration > 0) {
                    startAutoCloseTimer();
                }
            }
        });
        slideTimer.start();
    }

    void closeQuietly() {
        finishClose();
    }

    void updateStackOffset(int offset) {
        stackOffset = offset;
        targetPosition = calculatePosition(offset);
        setLocation(targetPosition);
    }

    private void configureWindow() {
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setBackground(new Color(0, 0, 0, 0));
        getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
        getRootPane().setOpaque(false);
        getRootPane().setBackground(new Color(0, 0, 0, 0));
        getLayeredPane().setOpaque(false);
    }

    private JPanel buildRootPanel(String displayMessage, int seconds) {
        JPanel wrapper = ToastStyle.createCardPanel(type);

        JPanel card = ToastStyle.createContentPanel();
        JLabel iconLabel = ToastStyle.createTypeIcon(type);
        JLabel titleLabel = ToastStyle.createTitleLabel(title, type);
        JScrollPane bodyText = buildBodyText(displayMessage);
        JPanel actionPanel = buildActionPanel();
        JPanel bodyPanel = ToastStyle.createBodyPanel(bodyText, actionPanel);
        JPanel textPanel = ToastStyle.createTextPanel();
        textPanel.add(titleLabel, BorderLayout.NORTH);
        textPanel.add(bodyPanel, BorderLayout.CENTER);

        closeButton = ToastStyle.createCloseButton(this::startFadeOut);
        closeButton.setOpaque(false);
        JPanel closePanel = ToastStyle.createClosePanel(closeButton);

        card.add(iconLabel, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);
        card.add(closePanel, BorderLayout.EAST);
        wrapper.add(card, BorderLayout.CENTER);

        for (Component component : new Component[]{
                wrapper, card, iconLabel, titleLabel, bodyLabel, bodyText, bodyText.getViewport(), bodyPanel,
                textPanel, actionPanel, expandButton, copyButton, closePanel, closeButton
        }) {
            if (component == null) {
                continue;
            }
            installHoverListener(component);
        }

        totalDuration = seconds * 1000;
        return wrapper;
    }

    private JScrollPane buildBodyText(String displayMessage) {
        bodyLabel = ToastStyle.createBodyTextArea(displayMessage);
        MouseAdapter clickListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    copyMessageAndFlash();
                }
            }
        };
        bodyLabel.addMouseListener(clickListener);
        bodyScrollPane = ToastStyle.createBodyScrollPane(bodyLabel);
        return bodyScrollPane;
    }

    private JPanel buildActionPanel() {
        if (!ToastTextFormatter.isFoldable(fullMessage)) {
            return null;
        }
        expandButton = ToastStyle.createLinkButton(ToastTextFormatter.actionText(expanded), this::toggleExpand);
        copyButton = ToastStyle.createLinkButton(CommonI18n.get(CommonMessageKeys.BUTTON_COPY), this::copyMessageAndFlash);
        return ToastStyle.createActionPanel(expandButton, copyButton);
    }

    private void installHoverListener(Component component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                onHoverEnter();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(),
                        ToastWindow.this);
                if (!new Rectangle(getSize()).contains(point)) {
                    onHoverExit();
                }
            }
        });
    }

    private void onHoverEnter() {
        if (hovered) {
            return;
        }
        hovered = true;
        pauseAutoClose();
        ToastStyle.showCloseButton(closeButton);
    }

    private void onHoverExit() {
        if (!hovered) {
            return;
        }
        hovered = false;
        resumeAutoClose();
        ToastStyle.hideCloseButton(closeButton);
    }

    private void startAutoCloseTimer() {
        startTime = System.currentTimeMillis();
        autoCloseTimer = new Timer(totalDuration, e -> startFadeOut());
        autoCloseTimer.setRepeats(false);
        autoCloseTimer.start();
    }

    private void pauseAutoClose() {
        pausedTime = System.currentTimeMillis();
        if (autoCloseTimer != null) {
            autoCloseTimer.stop();
        }
    }

    private void resumeAutoClose() {
        if (pausedTime <= 0) {
            return;
        }
        long paused = System.currentTimeMillis() - pausedTime;
        startTime += paused;
        pausedTime = 0;
        long remaining = totalDuration - (System.currentTimeMillis() - startTime);
        if (remaining > 0) {
            autoCloseTimer = new Timer((int) remaining, e -> startFadeOut());
            autoCloseTimer.setRepeats(false);
            autoCloseTimer.start();
        } else {
            startFadeOut();
        }
    }

    private void startFadeOut() {
        if (closed) {
            return;
        }
        if (!opacitySupported) {
            finishClose();
            return;
        }
        stopTimers();
        final int[] step = {0};
        fadeTimer = new Timer(ToastStyle.FADE_INTERVAL, e -> {
            step[0]++;
            float alpha = Math.max(0f, 1f - (float) step[0] / ToastStyle.FADE_STEPS);
            try {
                setOpacity(alpha);
            } catch (UnsupportedOperationException | IllegalComponentStateException ignored) {
                opacitySupported = false;
                finishClose();
                return;
            }
            if (step[0] >= ToastStyle.FADE_STEPS) {
                finishClose();
            }
        });
        fadeTimer.start();
    }

    private void finishClose() {
        stopTimers();
        dispose();
        notifyClosed();
    }

    private void stopTimers() {
        if (autoCloseTimer != null) {
            autoCloseTimer.stop();
            autoCloseTimer = null;
        }
        if (slideTimer != null) {
            slideTimer.stop();
            slideTimer = null;
        }
        if (fadeTimer != null) {
            fadeTimer.stop();
            fadeTimer = null;
        }
    }

    private void toggleExpand() {
        expanded = !expanded;
        String displayMessage = ToastTextFormatter.displayText(fullMessage, expanded);
        bodyLabel.setText(displayMessage);
        if (expandButton != null) {
            expandButton.setText(ToastTextFormatter.actionText(expanded));
        }
        toastWidth = ToastStyle.preferredWidth(title, displayMessage);
        refreshWindowSize();
        targetPosition = calculatePosition(stackOffset);
        setLocation(targetPosition);
        onLayoutChanged.run();
    }

    private void refreshWindowSize() {
        int bodyWidth = ToastStyle.bodyWidth(toastWidth);
        setSize(toastWidth, 1);
        bodyLabel.setSize(bodyWidth, Short.MAX_VALUE);
        Dimension preferredBodySize = bodyLabel.getPreferredSize();
        boolean needsBodyScroll = expanded && preferredBodySize.height > ToastStyle.EXPANDED_MAX_BODY_HEIGHT;
        int bodyHeight = expanded
                ? Math.min(preferredBodySize.height, ToastStyle.EXPANDED_MAX_BODY_HEIGHT)
                : preferredBodySize.height;
        bodyScrollPane.setVerticalScrollBarPolicy(needsBodyScroll
                ? ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        ToastStyle.applyBodyScrollPaneChrome(bodyScrollPane);
        bodyScrollPane.setPreferredSize(new Dimension(bodyWidth, Math.max(1, bodyHeight)));
        bodyScrollPane.revalidate();
        rootPanel.revalidate();
        rootPanel.repaint();
        setSize(toastWidth, rootPanel.getPreferredSize().height);
    }

    private Point calculatePosition(int offset) {
        return ToastPlacement.calculate(parentWindow, getSize(), position, offset);
    }

    private void copyMessageAndFlash() {
        copyToClipboard(fullMessage);
        flashFeedback();
    }

    private void copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        } catch (Exception ignored) {
        }
    }

    private void flashFeedback() {
        Color original = bodyLabel.getForeground();
        bodyLabel.setForeground(type.getColor());
        Timer timer = new Timer(200, e -> {
            bodyLabel.setForeground(original);
            ((Timer) e.getSource()).stop();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void notifyClosed() {
        if (closed) {
            return;
        }
        closed = true;
        onClosed.accept(this);
    }
}
