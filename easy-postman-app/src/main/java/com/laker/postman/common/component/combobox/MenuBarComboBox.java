package com.laker.postman.common.component.combobox;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.EasyComboBox;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 顶部菜单栏下拉框基类：宽度随选中项自适应，无边框，背景与菜单栏融为一体。
 */
abstract class MenuBarComboBox<E> extends EasyComboBox<E> {
    private static final int MIN_WIDTH = 64;
    private static final int MAX_WIDTH = 200;
    private static final int HEIGHT = 28;

    protected MenuBarComboBox() {
        // FlatLaf 默认最小宽度（ComboBox.minimumWidth=72）大于菜单栏所需，显式下调
        putClientProperty(FlatClientProperties.MINIMUM_WIDTH, MIN_WIDTH);
        setDynamicWidthRange(MIN_WIDTH, MAX_WIDTH);
        setFixedHeight(HEIGHT);
        setBorder(new EmptyBorder(0, 0, 0, 0));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // 主题切换后重新应用菜单栏背景色
        Color background = UIManager.getColor("MenuBar.background");
        if (background != null) {
            String hex = String.format("#%02x%02x%02x", background.getRed(), background.getGreen(), background.getBlue());
            putClientProperty("FlatLaf.style", "buttonBackground: " + hex
                    + "; buttonEditableBackground: " + hex
                    + "; buttonFocusedBackground: " + hex
                    + "; buttonSeparatorColor: null; buttonDisabledSeparatorColor: null");
            setBackground(background);
        }
    }
}
