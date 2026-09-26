package com.laker.postman.frame;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.common.themes.SimpleThemeManager;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.UtilityClass;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Container;

/**
 * 主窗口平台标题栏和背景外观适配。
 */
@UtilityClass
@Slf4j
class MainWindowChrome {

    static void applyInitialDecorations(JFrame frame) {
        applyWindowsWindowDecorations(frame);
        applyBackground(frame);
        applyMacWindowDecorations(frame);
        applyMacWindowAppearance(frame);
    }

    static void refresh(JFrame frame) {
        applyBackground(frame);
        applyMacWindowAppearance(frame);
        frame.repaint();
    }

    static void applyBackground(JFrame frame) {
        Color background = ModernColors.getBackgroundColor();
        frame.setBackground(background);
        JRootPane rootPane = frame.getRootPane();
        if (rootPane != null) {
            rootPane.setOpaque(true);
            rootPane.setBackground(background);
        }
        if (frame.getLayeredPane() != null) {
            frame.getLayeredPane().setOpaque(true);
            frame.getLayeredPane().setBackground(background);
        }
        if (frame.getGlassPane() instanceof JComponent glassPane) {
            glassPane.setOpaque(false);
            glassPane.setBackground(background);
        }
        Container contentPane = frame.getContentPane();
        if (contentPane instanceof JComponent contentComponent) {
            contentComponent.setOpaque(true);
            contentComponent.setBackground(background);
        }
        applyWindowTitleBarBackground(frame);
    }

    private static void applyWindowsWindowDecorations(JFrame frame) {
        JRootPane rootPane = frame.getRootPane();
        if (!SystemInfo.isWindows_10_orLater || rootPane == null) {
            return;
        }

        log.info("Enabling FlatLaf custom window decorations on Windows");
        rootPane.putClientProperty(FlatClientProperties.USE_WINDOW_DECORATIONS, Boolean.TRUE);
        rootPane.putClientProperty(FlatClientProperties.MENU_BAR_EMBEDDED, Boolean.TRUE);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, Boolean.FALSE);
    }

    private static void applyWindowTitleBarBackground(JFrame frame) {
        JRootPane rootPane = frame.getRootPane();
        if (rootPane == null) {
            return;
        }

        Color titleBarBackground = ModernColors.getWindowChromeBackgroundColor();
        Color titleBarForeground = UIManager.getColor("Menu.foreground");
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND, titleBarBackground);
        rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_FOREGROUND, titleBarForeground);
    }

    private static void applyMacWindowAppearance(JFrame frame) {
        JRootPane rootPane = frame.getRootPane();
        if (!SystemInfo.isMacFullWindowContentSupported || rootPane == null) {
            return;
        }
        if (SimpleThemeManager.isDarkTheme()) {
            rootPane.putClientProperty("apple.awt.windowAppearance", "NSAppearanceNameVibrantDark");
        } else {
            rootPane.putClientProperty("apple.awt.windowAppearance", "NSAppearanceNameVibrantLight");
        }
    }

    private static void applyMacWindowDecorations(JFrame frame) {
        JRootPane rootPane = frame.getRootPane();
        if (!SystemInfo.isMacFullWindowContentSupported || rootPane == null) {
            return;
        }
        // 在窗口首显前一次性应用，避免显示后再切换 title bar / fullWindowContent 造成二次重绘。
        rootPane.putClientProperty("apple.awt.fullWindowContent", true);
        rootPane.putClientProperty("apple.awt.transparentTitleBar", true);
    }
}
