package com.laker.postman.common.component.button;

import com.laker.postman.util.CommonI18n;
import com.laker.postman.util.CommonMessageKeys;
import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.util.IconUtil;

import javax.swing.*;
import java.awt.*;

/**
 * 通用刷新按钮，带图标和统一样式。
 */
public class RefreshButton extends JButton {
    public RefreshButton() {
        this(IconUtil.SIZE_MEDIUM);
    }

    public RefreshButton(int iconSize) {
        setIcon(IconUtil.createThemed("icons/refresh.svg", iconSize, iconSize));
        setToolTipText(CommonI18n.get(CommonMessageKeys.BUTTON_REFRESH));
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    }
}
