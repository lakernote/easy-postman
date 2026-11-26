package com.laker.postman.panel.collections.editor.request.sub;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.CloseButton;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * 响应体底部的 JSONPath 过滤栏：输入停顿后自动应用表达式，Enter 立即应用，Esc 关闭。
 */
class ResponseJsonPathFilterBar extends JPanel {

    private static final int APPLY_DELAY_MS = 300;
    private static final int STATUS_MAX_WIDTH = 260;
    /** 状态文本最多占过滤栏宽度的比例，保证输入框始终可用。 */
    private static final double STATUS_MAX_WIDTH_RATIO = 0.4;

    private final JTextField expressionField;
    private final JLabel statusLabel;
    private final Timer applyTimer;

    /**
     * @param onApply 防抖后或按 Enter 时应用表达式
     * @param onEdit  表达式每次被编辑时立即调用，用于作废仍在执行的旧查询
     * @param onClose 关闭过滤栏
     */
    ResponseJsonPathFilterBar(Consumer<String> onApply, Runnable onEdit, Runnable onClose) {
        super(new BorderLayout(6, 0));
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        expressionField = new JTextField();
        expressionField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_PLACEHOLDER));
        expressionField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        expressionField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                IconUtil.createThemed("icons/filter.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        expressionField.getAccessibleContext().setAccessibleName(
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_TOOLTIP));

        statusLabel = new JLabel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                return new Dimension(Math.min(size.width, maxStatusWidth()), size.height);
            }
        };
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        statusLabel.setVisible(false);

        CloseButton closeButton = new CloseButton();
        closeButton.addActionListener(e -> onClose.run());

        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        eastPanel.setOpaque(false);
        eastPanel.add(statusLabel);
        eastPanel.add(closeButton);

        add(expressionField, BorderLayout.CENTER);
        add(eastPanel, BorderLayout.EAST);

        applyTimer = new Timer(APPLY_DELAY_MS, e -> onApply.accept(getExpression()));
        applyTimer.setRepeats(false);
        expressionField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                expressionEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                expressionEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                expressionEdited();
            }

            private void expressionEdited() {
                onEdit.run();
                applyTimer.restart();
            }
        });
        expressionField.addActionListener(e -> {
            applyTimer.stop();
            onApply.accept(getExpression());
        });
        expressionField.registerKeyboardAction(e -> onClose.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
    }

    String getExpression() {
        return expressionField.getText().trim();
    }

    void setExpression(String expression) {
        expressionField.setText(expression);
    }

    JTextField getExpressionField() {
        return expressionField;
    }

    void focusInput() {
        expressionField.requestFocusInWindow();
        expressionField.selectAll();
    }

    void cancelPendingApply() {
        applyTimer.stop();
    }

    void showInfo(String message) {
        showStatus(message, ModernColors.getTextSecondary());
    }

    void showError(String message) {
        showStatus(message, ModernColors.getError());
    }

    void clearStatus() {
        statusLabel.setText(null);
        statusLabel.setToolTipText(null);
        statusLabel.setVisible(false);
    }

    String getStatusText() {
        return statusLabel.isVisible() ? statusLabel.getText() : null;
    }

    private void showStatus(String message, Color color) {
        String singleLine = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        statusLabel.setForeground(color);
        statusLabel.setText(singleLine);
        statusLabel.setToolTipText(singleLine);
        statusLabel.setVisible(!singleLine.isEmpty());
        revalidate();
        repaint();
    }

    /** 超出宽度时由 JLabel 自动以省略号截断，完整内容见 tooltip。 */
    private int maxStatusWidth() {
        int maxWidth = UIScale.scale(STATUS_MAX_WIDTH);
        int barWidth = getWidth();
        return barWidth > 0 ? Math.min(maxWidth, (int) (barWidth * STATUS_MAX_WIDTH_RATIO)) : maxWidth;
    }
}
