package com.laker.postman.common.component.setting;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.FontsUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Label + editor row for settings forms.
 */
public class SettingsFieldRow extends JPanel {

    public static final int DEFAULT_LABEL_WIDTH = 220;
    public static final int DEFAULT_FIELD_WIDTH = 300;
    public static final int DEFAULT_MIN_FIELD_WIDTH = 160;
    public static final int DEFAULT_ROW_HEIGHT = 36;
    public static final int DEFAULT_GAP = 16;

    private final JLabel label;
    private final JComponent inputComponent;

    public SettingsFieldRow(String labelText, String tooltip, JComponent inputComponent) {
        this(labelText, tooltip, inputComponent, DEFAULT_LABEL_WIDTH, DEFAULT_FIELD_WIDTH);
    }

    public SettingsFieldRow(String labelText, String tooltip, JComponent inputComponent, int labelWidth, int fieldWidth) {
        this.inputComponent = inputComponent;
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, DEFAULT_ROW_HEIGHT));

        label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(labelWidth, 32));
        label.setMinimumSize(new Dimension(labelWidth, 32));
        label.setMaximumSize(new Dimension(labelWidth, 32));
        if (tooltip != null && !tooltip.isEmpty()) {
            label.setToolTipText(tooltip);
        }
        if (fieldWidth > 0) {
            if (inputComponent instanceof JTextField textField) {
                // JTextField recomputes preferred width from columns and would ignore the configured field width.
                textField.setColumns(0);
            }
            inputComponent.setPreferredSize(new Dimension(fieldWidth, 34));
            inputComponent.setMaximumSize(new Dimension(fieldWidth, 34));
            // Text fields and combo boxes report different natural minimums; align them so they shrink together.
            inputComponent.setMinimumSize(new Dimension(Math.min(fieldWidth, DEFAULT_MIN_FIELD_WIDTH), 34));
        }

        add(label);
        add(Box.createHorizontalStrut(DEFAULT_GAP));
        add(inputComponent);
        add(Box.createHorizontalGlue());
        applySettingsSurface();
    }

    public JLabel label() {
        return label;
    }

    public JComponent inputComponent() {
        return inputComponent;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        applyEnabledState();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applySettingsSurface();
    }

    private void applySettingsSurface() {
        setOpaque(false);
        setBackground(ModernColors.getDialogChromeBackgroundColor());
        if (label == null) {
            return;
        }
        label.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        applyEnabledState();
    }

    private void applyEnabledState() {
        boolean enabled = isEnabled();
        if (label != null) {
            label.setEnabled(enabled);
            label.setForeground(enabled ? ModernColors.getTextPrimary() : ModernColors.getTextDisabled());
        }
        SettingsInputStyle.applyEnabledState(inputComponent, enabled);
    }
}
