package com.laker.postman.panel.sidebar.global;

import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * 全局变量管理对话框。
 */
public class GlobalVariablesDialog extends JDialog {

    private final GlobalVariablesPanel globalVariablesPanel;

    public GlobalVariablesDialog(Window owner) {
        super(owner, I18nUtil.getMessage(MessageKeys.GLOBAL_VARIABLES_MANAGER_TITLE), ModalityType.MODELESS);
        globalVariablesPanel = new GlobalVariablesPanel();
        initUI();
    }

    private void initUI() {
        ToolWindowSurfaceStyle.skipDialogWindowChrome(this);
        ToolWindowSurfaceStyle.applyDialogSurface(globalVariablesPanel);
        setContentPane(globalVariablesPanel);
        setSize(800, 500);
        setMinimumSize(new Dimension(600, 400));
        setResizable(true);
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        getRootPane().registerKeyboardAction(
                e -> setVisible(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    @Override
    public void setVisible(boolean b) {
        if (b) {
            setLocationRelativeTo(getOwner());
            globalVariablesPanel.prepareForDisplay();
        }
        super.setVisible(b);
    }
}
