package com.laker.postman.panel.topmenu;

import com.formdev.flatlaf.extras.FlatDesktop;
import com.formdev.flatlaf.util.SystemInfo;
import com.laker.postman.common.IRefreshable;
import com.laker.postman.common.UiSingletonMenuBar;
import com.laker.postman.common.component.combobox.EnvironmentComboBox;
import com.laker.postman.common.component.combobox.WorkspaceComboBox;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.panel.lifecycle.AppExitCoordinator;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class TopMenuBar extends UiSingletonMenuBar implements IRefreshable {
    private TopMenuWorkspaceControls workspaceControls;

    @Override
    protected void initUI() {
        setOpaque(true);
        setBorder(createPanelBorder());
        initComponents();
    }

    static Border createPanelBorder() {
        return BorderFactory.createEmptyBorder(2, 4, 1, 8);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // 主题切换时重新创建边框，确保菜单栏留白保持稳定。
        setBorder(createPanelBorder());
    }

    @Override
    protected void registerListeners() {
        FlatDesktop.setAboutHandler(this::showAboutDialog);
        FlatDesktop.setQuitHandler(e -> BeanFactory.getBean(AppExitCoordinator.class).exitApplication());
    }


    /**
     * 刷新菜单栏（实现 IRefreshable 接口）
     * <p>
     * 重新加载菜单栏（包括菜单项、快捷键、Git 工具栏等所有组件）
     * 在以下场景调用：
     * 1. 语言切换后（需要更新所有菜单文本）
     * 2. 快捷键设置修改后
     * 3. 工作区切换后（需要更新 Git 工具栏显示状态）
     */
    @Override
    public void refresh() {
        removeAll();
        // 重新创建菜单栏所有组件
        initComponents();
        // 刷新界面
        revalidate();
        repaint();
    }

    private void initComponents() {
        if (SystemInfo.isMacFullWindowContentSupported) {
            // macOS Full Window Content 模式下，左侧留出更多空间给红黄绿按钮
            // macOS 红黄绿按钮宽度约 70-76px
            add(Box.createHorizontalStrut(70));
        }

        add(TopMenuFileMenu.create(this));
        add(TopMenuLanguageMenu.create());
        add(TopMenuThemeMenu.create());
        add(TopMenuSettingsMenu.create(this));
        add(TopMenuPluginMenu.create(this));
        add(TopMenuHelpMenu.create(this));
        addAboutMenu();

        add(Box.createGlue()); // 添加弹性空间，将后续组件推到右侧

        workspaceControls().addTo(this);
    }

    public EnvironmentComboBox getEnvironmentComboBox() {
        return workspaceControls().getEnvironmentComboBox();
    }

    public WorkspaceComboBox getWorkspaceComboBox() {
        return workspaceControls().getWorkspaceComboBox();
    }

    /**
     * 更新工作区下拉框内容（不重新加载整个菜单栏）
     * 用于工作区列表变化但当前工作区类型未变化的场景（如创建、重命名）
     */
    public void updateWorkspaceComboBox() {
        workspaceControls().updateWorkspaceComboBox();
    }

    /**
     * 更新工作区显示（包括工作区下拉框和 Git 工具栏）
     * 在外部切换工作区后调用（例如从 WorkspacePanel 切换）
     * 会重新加载整个菜单栏以更新 Git 工具栏显示状态
     */
    public void updateWorkspaceDisplay() {
        workspaceControls().updateWorkspaceDisplay();
    }

    private TopMenuWorkspaceControls workspaceControls() {
        if (workspaceControls == null) {
            workspaceControls = new TopMenuWorkspaceControls(this, this::refresh);
        }
        return workspaceControls;
    }

    private void addAboutMenu() {
        JMenu aboutMenu = new JMenu(I18nUtil.getMessage(MessageKeys.MENU_ABOUT));
        JMenuItem aboutMenuItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.MENU_ABOUT_EASYPOSTMAN));
        aboutMenuItem.addActionListener(e -> showAboutDialog());
        aboutMenu.add(aboutMenuItem);
        add(aboutMenu);
    }

    private void showAboutDialog() {
        TopMenuAboutDialog.show(this);
    }

}
