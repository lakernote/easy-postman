package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.setting.SettingsCheckBoxRow;
import com.laker.postman.common.component.setting.SettingsFieldRow;
import com.laker.postman.common.component.setting.SettingsInputStyle;
import com.laker.postman.common.component.setting.SettingsSectionPanel;
import com.laker.postman.common.component.setting.SettingsTextFieldValidator;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 现代化设置面板基类
 * 提供统一的现代化UI风格和交互体验
 */
public abstract class ModernSettingsPanel extends JPanel {
    protected static final int COMPACT_FIELD_LABEL_MIN_WIDTH = 120;
    protected static final int FIELD_LABEL_TEXT_PADDING = 12;

    protected JButton saveBtn;
    @Getter
    protected JButton cancelBtn;
    protected JButton applyBtn;
    protected final Map<JTextField, SettingsTextFieldValidator> validators = new HashMap<>();
    protected final Map<JComponent, Object> originalValues = new HashMap<>();
    private JScrollPane contentScrollPane;

    // 状态管理
    protected boolean hasUnsavedChanges = false;
    private JLabel unsavedChangesLabel;
    private boolean initialized;

    /**
     * 获取主题适配的设置对话框背景色
     */
    protected Color getBackgroundColor() {
        return ModernColors.getDialogChromeBackgroundColor();
    }

    /**
     * 获取主题适配的输入框背景色
     */
    protected Color getInputBackgroundColor() {
        return ModernColors.getInputBackgroundColor();
    }

    /**
     * 获取主题适配的主文本颜色
     */
    protected Color getTextPrimaryColor() {
        return ModernColors.getTextPrimary();
    }

    /**
     * 获取主题适配的次要文本颜色
     */
    protected Color getTextSecondaryColor() {
        return ModernColors.getTextSecondary();
    }

    /**
     * 获取主题适配的滚动条轨道颜色
     */
    protected Color getScrollbarTrackColor() {
        return ModernColors.getScrollbarTrackColor();
    }

    /**
     * 获取主题适配的滚动条滑块颜色
     */
    protected Color getScrollbarThumbColor() {
        return ModernColors.getScrollbarThumbColor();
    }

    /**
     * 获取主题适配的滚动条滑块悬停颜色
     */
    protected Color getScrollbarThumbHoverColor() {
        return ModernColors.getScrollbarThumbHoverColor();
    }

    protected ModernSettingsPanel() {
    }

    private void initializePanel() {
        if (initialized) {
            return;
        }
        initUI();
        registerListeners();
        initialized = true;
    }

    @Override
    public void addNotify() {
        initializePanel();
        super.addNotify();
        resetScrollPositionToTop();
    }

    @Override
    public Dimension getPreferredSize() {
        initializePanel();
        return super.getPreferredSize();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));
        ToolWindowSurfaceStyle.applyDialogSurface(this);

        // 创建主容器
        JPanel mainContainer = new JPanel(new BorderLayout(0, 0));
        ToolWindowSurfaceStyle.applyDialogSurface(mainContainer);

        // 主内容区域
        JPanel contentPanel = new ViewportWidthTrackingPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        ToolWindowSurfaceStyle.applyDialogSurface(contentPanel);
        contentPanel.setBorder(new EmptyBorder(20, 24, 16, 24));

        // 子类实现具体内容
        buildContent(contentPanel);

        // 滚动面板
        contentScrollPane = new JScrollPane(contentPanel);
        contentScrollPane.setBorder(BorderFactory.createEmptyBorder());
        contentScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        contentScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        contentScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ToolWindowSurfaceStyle.applyDialogScrollPane(contentScrollPane);
        customizeScrollBar(contentScrollPane);

        // 组装主容器
        mainContainer.add(contentScrollPane, BorderLayout.CENTER);

        // 底部按钮栏
        JPanel buttonBar = createModernButtonBar();

        add(mainContainer, BorderLayout.CENTER);
        add(buttonBar, BorderLayout.SOUTH);

        // Add ESC key handling to trigger cancel button
        registerKeyboardAction(
                e -> {
                    if (cancelBtn != null) {
                        cancelBtn.doClick();
                    }
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        );

        resetScrollPositionToTop();
    }

    void resetScrollPositionToTop() {
        if (contentScrollPane == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            contentScrollPane.getViewport().setViewPosition(new Point(0, 0));
            contentScrollPane.getVerticalScrollBar().setValue(0);
        });
    }

    /**
     * 子类实现此方法来构建具体的设置内容
     */
    protected abstract void buildContent(JPanel contentPanel);

    /**
     * 子类实现此方法来注册监听器
     */
    protected abstract void registerListeners();

    /**
     * 创建现代化的区域面板
     */
    protected JPanel createModernSection(String title, String description) {
        return new SettingsSectionPanel(title, description);
    }

    /**
     * 创建现代化的字段行（标签 + 输入框）
     */
    protected SettingsFieldRow createFieldRow(String labelText, String tooltip, JComponent inputComponent) {
        styleInputComponent(inputComponent);
        inputComponent.setPreferredSize(new Dimension(SettingsFieldRow.DEFAULT_FIELD_WIDTH, 34));
        inputComponent.setMaximumSize(new Dimension(SettingsFieldRow.DEFAULT_FIELD_WIDTH, 34));
        return new SettingsFieldRow(labelText, tooltip, inputComponent);
    }

    /**
     * 创建自定义列宽的字段行，用于局部表单在不截断当前语言标签的前提下收紧输入列。
     */
    protected SettingsFieldRow createFieldRow(String labelText,
                                              String tooltip,
                                              JComponent inputComponent,
                                              int labelWidth,
                                              int fieldWidth) {
        styleInputComponent(inputComponent);
        return new SettingsFieldRow(labelText, tooltip, inputComponent, labelWidth, fieldWidth);
    }

    protected int calculateFieldLabelWidth(Collection<String> labelTexts) {
        return calculateFieldLabelWidth(
                labelTexts,
                COMPACT_FIELD_LABEL_MIN_WIDTH,
                SettingsFieldRow.DEFAULT_LABEL_WIDTH
        );
    }

    protected int calculateFieldLabelWidth(Collection<String> labelTexts, int minWidth, int maxWidth) {
        FontMetrics metrics = getFontMetrics(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        int maxTextWidth = 0;
        for (String labelText : labelTexts) {
            if (labelText != null) {
                maxTextWidth = Math.max(maxTextWidth, metrics.stringWidth(labelText));
            }
        }
        return Math.min(maxWidth, Math.max(minWidth, maxTextWidth + FIELD_LABEL_TEXT_PADDING));
    }

    /**
     * 创建现代化的复选框行
     */
    protected JPanel createCheckBoxRow(JCheckBox checkBox, String tooltip) {
        return new SettingsCheckBoxRow(checkBox, tooltip);
    }


    /**
     * 样式化输入组件
     */
    private void styleInputComponent(JComponent component) {
        SettingsInputStyle.apply(component);
    }

    /**
     * 创建现代化的按钮栏
     */
    private JPanel createModernButtonBar() {
        JPanel buttonBar = new JPanel(new BorderLayout(12, 0));
        ToolWindowSurfaceStyle.applyDialogFooter(buttonBar);

        unsavedChangesLabel = new JLabel(
                I18nUtil.getMessage(MessageKeys.SETTINGS_UNSAVED_CHANGES_WARNING),
                IconUtil.createColored("icons/warning.svg", 14, 14, ModernColors.getWarning()),
                SwingConstants.LEADING
        );
        unsavedChangesLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        unsavedChangesLabel.setForeground(ModernColors.getTextSecondary());
        unsavedChangesLabel.setVisible(false);
        buttonBar.add(unsavedChangesLabel, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        ToolWindowSurfaceStyle.applyDialogSurface(actionPanel);

        cancelBtn = createModernButton(
                I18nUtil.getMessage(MessageKeys.SETTINGS_DIALOG_CANCEL),
                false
        );

        applyBtn = createModernButton(
                I18nUtil.getMessage(MessageKeys.SETTINGS_DIALOG_APPLY),
                false
        );
        applyBtn.setEnabled(false); // 初始禁用

        saveBtn = createModernButton(
                I18nUtil.getMessage(MessageKeys.SETTINGS_DIALOG_SAVE),
                true
        );

        actionPanel.add(cancelBtn);
        actionPanel.add(applyBtn);
        actionPanel.add(saveBtn);
        buttonBar.add(actionPanel, BorderLayout.EAST);

        return buttonBar;
    }

    /**
     * 创建现代化按钮
     */
    protected JButton createModernButton(String text, boolean isPrimary) {
        return ModernButtonFactory.createButton(text, isPrimary);
    }

    /**
     * 添加间距
     */
    protected Component createVerticalSpace(int height) {
        return Box.createVerticalStrut(height);
    }

    protected void registerSaveShortcut(Runnable saveAction) {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        inputMap.put(KeyStroke.getKeyStroke("control S"), "save");
        actionMap.put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveAction.run();
            }
        });
    }

    /**
     * 设置验证器
     */
    protected void setupValidator(JTextField field, Predicate<String> validator, String errorMessage) {
        validators.put(field, SettingsTextFieldValidator.install(field, validator, errorMessage));
    }

    /**
     * 验证所有字段
     */
    protected boolean validateAllFields() {
        for (Map.Entry<JTextField, SettingsTextFieldValidator> entry : validators.entrySet()) {
            JTextField field = entry.getKey();
            if (!entry.getValue().validateNow()) {
                field.requestFocus();
                return false;
            }
        }
        return true;
    }

    /**
     * 自定义滚动条样式
     */
    private void customizeScrollBar(JScrollPane scrollPane) {
        scrollPane.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = getScrollbarThumbColor();
                this.thumbDarkShadowColor = getScrollbarThumbColor();
                this.thumbHighlightColor = getScrollbarThumbColor();
                this.thumbLightShadowColor = getScrollbarThumbColor();
                this.trackColor = getBackgroundColor();
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createInvisibleButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createInvisibleButton();
            }

            private JButton createInvisibleButton() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isThumbRollover() ? getScrollbarThumbHoverColor() : getScrollbarThumbColor());
                g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2,
                        thumbBounds.width - 4, thumbBounds.height - 4, 6, 6);
                g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(getBackgroundColor());
                g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
                g2.dispose();
            }
        });
    }

    // 工具方法
    protected boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    protected boolean isPositiveInteger(String s) {
        return isInteger(s) && Integer.parseInt(s) >= 0;
    }

    // ==================== 状态管理方法 ====================

    /**
     * 记录组件的原始值
     */
    protected void trackComponentValue(JComponent component) {
        if (component instanceof JTextField) {
            originalValues.put(component, ((JTextField) component).getText());
            ((JTextField) component).getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    checkForChanges();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    checkForChanges();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    checkForChanges();
                }
            });
        } else if (component instanceof JCheckBox) {
            originalValues.put(component, ((JCheckBox) component).isSelected());
            ((JCheckBox) component).addItemListener(e -> checkForChanges());
        } else if (component instanceof JComboBox) {
            originalValues.put(component, ((JComboBox<?>) component).getSelectedItem());
            ((JComboBox<?>) component).addItemListener(e -> checkForChanges());
        }
    }

    /**
     * 检查是否有未保存的更改
     */
    protected void checkForChanges() {
        boolean hasChanges = false;

        for (Map.Entry<JComponent, Object> entry : originalValues.entrySet()) {
            JComponent component = entry.getKey();
            Object originalValue = entry.getValue();

            if (component instanceof JTextField) {
                String currentValue = ((JTextField) component).getText();
                if (!currentValue.equals(originalValue)) {
                    hasChanges = true;
                    break;
                }
            } else if (component instanceof JCheckBox) {
                boolean currentValue = ((JCheckBox) component).isSelected();
                if (currentValue != (Boolean) originalValue) {
                    hasChanges = true;
                    break;
                }
            } else if (component instanceof JComboBox) {
                Object currentValue = ((JComboBox<?>) component).getSelectedItem();
                if (currentValue != null && !currentValue.equals(originalValue)) {
                    hasChanges = true;
                    break;
                }
            }
        }

        setHasUnsavedChanges(hasChanges);
    }

    /**
     * 设置未保存更改状态
     */
    protected void setHasUnsavedChanges(boolean hasChanges) {
        this.hasUnsavedChanges = hasChanges;
        if (unsavedChangesLabel != null) {
            unsavedChangesLabel.setVisible(hasChanges);
            unsavedChangesLabel.revalidate();
            unsavedChangesLabel.repaint();
        }
        if (applyBtn != null) {
            applyBtn.setEnabled(hasChanges);
        }

        // 更新父窗口标题（如果是对话框）
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JDialog && hasChanges) {
            JDialog dialog = (JDialog) window;
            String title = dialog.getTitle();
            if (!title.startsWith("* ")) {
                dialog.setTitle("* " + title);
            }
        } else if (window instanceof JDialog && !hasChanges) {
            JDialog dialog = (JDialog) window;
            String title = dialog.getTitle();
            if (title.startsWith("* ")) {
                dialog.setTitle(title.substring(2));
            }
        }
    }

    /**
     * 放弃更改
     */
    protected void discardChanges() {
        for (Map.Entry<JComponent, Object> entry : originalValues.entrySet()) {
            JComponent component = entry.getKey();
            Object originalValue = entry.getValue();

            if (component instanceof JTextField) {
                ((JTextField) component).setText((String) originalValue);
            } else if (component instanceof JCheckBox) {
                ((JCheckBox) component).setSelected((Boolean) originalValue);
            } else if (component instanceof JComboBox) {
                ((JComboBox) component).setSelectedItem(originalValue);
            }
        }
        setHasUnsavedChanges(false);
    }

    /**
     * 确认放弃更改
     */
    protected boolean confirmDiscardChanges() {
        if (!hasUnsavedChanges) {
            return true;
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                I18nUtil.getMessage(MessageKeys.SETTINGS_CONFIRM_DISCARD_MESSAGE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_CONFIRM_DISCARD_TITLE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        return result == JOptionPane.YES_OPTION;
    }

    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges;
    }

    private static final class ViewportWidthTrackingPanel extends JPanel implements Scrollable {

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            if (getParent() instanceof JViewport viewport) {
                return getPreferredSize().height < viewport.getHeight();
            }
            return false;
        }
    }
}
