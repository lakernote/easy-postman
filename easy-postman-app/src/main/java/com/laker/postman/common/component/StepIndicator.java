package com.laker.postman.common.component;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import java.awt.*;

/**
 * 步骤指示器组件
 * 支持暗黑和亮色主题自动适配
 */
public class StepIndicator extends JPanel {
    private final String[] steps;
    private int currentStep = 0;

    public StepIndicator() {
        this.steps = getStepsForOperation();

        setLayout(new FlowLayout(FlowLayout.CENTER, 10, 0));
        setOpaque(false);

        initSteps();
    }

    /**
     * 获取激活状态的圆圈背景色（主题适配）
     */
    private Color getActiveCircleBackground() {
        return ModernColors.getPrimary();
    }

    /**
     * 获取非激活状态的圆圈背景色（主题适配）
     */
    private Color getInactiveCircleBackground() {
        return ModernColors.getBorderLightColor();
    }

    /**
     * 获取圆圈边框颜色（主题适配）
     */
    private Color getCircleBorderColor() {
        return ModernColors.getBorderMediumColor();
    }

    /**
     * 获取激活状态的文本颜色（主题适配）
     */
    private Color getActiveTextColor() {
        return ModernColors.getTextPrimary();
    }

    /**
     * 获取非激活状态的文本颜色（主题适配）
     */
    private Color getInactiveTextColor() {
        return ModernColors.getTextHint();
    }

    /**
     * 获取箭头颜色（主题适配）
     */
    private Color getArrowColor() {
        return ModernColors.getTextSecondary();
    }

    private String[] getStepsForOperation() {
        return new String[]{I18nUtil.getMessage(MessageKeys.STEP_CHECK_STATUS),
                I18nUtil.getMessage(MessageKeys.STEP_CONFIRM_CHANGE),
                I18nUtil.getMessage(MessageKeys.STEP_EXECUTE_OPERATION)};
    }

    private void initSteps() {
        for (int i = 0; i < steps.length; i++) {
            if (i > 0) {
                add(createArrow());
            }
            add(createStepCircle(i + 1, steps[i], i == currentStep));
        }
    }

    private JPanel createStepCircle(int number, String text, boolean active) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);

        JLabel circle = new StepBadge(String.valueOf(number));
        circle.setPreferredSize(new Dimension(22, 22));
        circle.setBackground(active ? getActiveCircleBackground() : getInactiveCircleBackground());
        circle.setForeground(active ? ModernColors.getTextInverse() : getInactiveTextColor());
        circle.setFont(FontsUtil.getDefaultFont(Font.BOLD));

        // 文本 - 使用主题适配的颜色
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(FontsUtil.getDefaultFontWithOffset(active ? Font.BOLD : Font.PLAIN, -3));
        label.setForeground(active ? getActiveTextColor() : getInactiveTextColor());

        panel.add(circle, BorderLayout.CENTER);
        panel.add(label, BorderLayout.SOUTH);

        return panel;
    }

    private JLabel createArrow() {
        JLabel arrow = new JLabel("›");
        arrow.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, 2)); // 比标准字体大2号
        arrow.setForeground(getArrowColor()); // 使用主题适配的箭头颜色
        return arrow;
    }

    public void setCurrentStep(int step) {
        this.currentStep = step;
        removeAll();
        initSteps();
        revalidate();
        repaint();
    }

    private final class StepBadge extends JLabel {
        private StepBadge(String text) {
            super(text, SwingConstants.CENTER);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            int size = Math.min(width, height);
            int x = (width - size) / 2;
            int y = (height - size) / 2;

            g2.setColor(getBackground());
            g2.fillRoundRect(x, y, size - 1, size - 1, size, size);
            g2.setColor(getCircleBorderColor());
            g2.drawRoundRect(x, y, size - 1, size - 1, size, size);
            g2.dispose();

            super.paintComponent(g);
        }
    }
}
