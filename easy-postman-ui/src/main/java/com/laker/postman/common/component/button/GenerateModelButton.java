package com.laker.postman.common.component.button;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.util.IconUtil;

import javax.swing.*;
import java.awt.*;

/** Compact toolbar action for generating a model from structured content. */
public class GenerateModelButton extends JButton {
    public GenerateModelButton(String tooltip) {
        setIcon(IconUtil.createThemed("icons/file-code.svg", IconUtil.SIZE_SMALL, IconUtil.SIZE_SMALL));
        setToolTipText(tooltip);
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    }
}
