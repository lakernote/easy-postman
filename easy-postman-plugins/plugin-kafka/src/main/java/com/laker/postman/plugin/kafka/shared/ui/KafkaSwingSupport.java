package com.laker.postman.plugin.kafka.shared.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.util.IconUtil;
import lombok.experimental.UtilityClass;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;

@UtilityClass
public class KafkaSwingSupport {

    private static final int TOGGLE_BUTTON_SIZE = 28;
    private static final int TOGGLE_ICON_SIZE = 16;

    public static void onTextChange(JTextComponent component, Runnable action) {
        component.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        });
    }

    /**
     * 工具栏风格的图标切换按钮。选中图标按绘制时主题的 toolbar 选中前景色取色（Easy 亮/暗主题为主色底白图标），
     * 主题未定义时回退为普通前景色，保证任何主题下都可见。
     */
    public static JToggleButton toolbarToggle(String iconPath, String tooltip) {
        JToggleButton button = new JToggleButton();
        button.setIcon(IconUtil.createThemed(iconPath, TOGGLE_ICON_SIZE, TOGGLE_ICON_SIZE));
        FlatSVGIcon selectedIcon = IconUtil.createThemed(iconPath, TOGGLE_ICON_SIZE, TOGGLE_ICON_SIZE);
        selectedIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> selectedToolbarForeground()));
        button.setSelectedIcon(selectedIcon);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(TOGGLE_BUTTON_SIZE, TOGGLE_BUTTON_SIZE));
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return button;
    }

    private static Color selectedToolbarForeground() {
        Color color = UIManager.getColor("ToggleButton.toolbar.selectedForeground");
        return color != null ? color : UIManager.getColor("Label.foreground");
    }
}
