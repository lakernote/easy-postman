package com.laker.postman.service.setting;

import com.laker.postman.common.constants.ConfigPathConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 快捷键管理器
 * 管理应用程序的所有快捷键配置，支持用户自定义
 */
@UtilityClass
public class ShortcutManager {
    private static final String SHORTCUT_CONFIG_FILE = ConfigPathConstants.SHORTCUTS;
    private static final Properties props = new Properties();
    private static final Map<String, ShortcutConfig> shortcuts = new HashMap<>();

    // 快捷键ID常量
    public static final String SEND_REQUEST = "send_request";
    public static final String NEW_REQUEST = "new_request";
    public static final String SAVE_REQUEST = "save_request";
    public static final String CLOSE_CURRENT_TAB = "close_current_tab";
    public static final String CLOSE_OTHER_TABS = "close_other_tabs";
    public static final String CLOSE_ALL_TABS = "close_all_tabs";
    public static final String EXIT_APP = "exit_app";

    static {
        initDefaultShortcuts();
        load();
    }

    /**
     * 初始化默认快捷键
     */
    private static void initDefaultShortcuts() {
        int cmdMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        shortcuts.put(SEND_REQUEST, new ShortcutConfig(
                SEND_REQUEST, "send_request",
                KeyEvent.VK_ENTER, cmdMask, "Ctrl+Enter", "Cmd+Enter"
        ));

        shortcuts.put(NEW_REQUEST, new ShortcutConfig(
                NEW_REQUEST, "new_request",
                KeyEvent.VK_N, cmdMask, "Ctrl+N", "Cmd+N"
        ));

        shortcuts.put(SAVE_REQUEST, new ShortcutConfig(
                SAVE_REQUEST, "save_request",
                KeyEvent.VK_S, cmdMask, "Ctrl+S", "Cmd+S"
        ));

        shortcuts.put(CLOSE_CURRENT_TAB, new ShortcutConfig(
                CLOSE_CURRENT_TAB, "close_current_tab",
                KeyEvent.VK_W, cmdMask, "Ctrl+W", "Cmd+W"
        ));

        shortcuts.put(CLOSE_OTHER_TABS, new ShortcutConfig(
                CLOSE_OTHER_TABS, "close_other_tabs",
                KeyEvent.VK_W, cmdMask | InputEvent.ALT_DOWN_MASK, "Ctrl+Alt+W", "Cmd+Option+W"
        ));

        shortcuts.put(CLOSE_ALL_TABS, new ShortcutConfig(
                CLOSE_ALL_TABS, "close_all_tabs",
                KeyEvent.VK_W, cmdMask | InputEvent.SHIFT_DOWN_MASK, "Ctrl+Shift+W", "Cmd+Shift+W"
        ));

        shortcuts.put(EXIT_APP, new ShortcutConfig(
                EXIT_APP, "exit_app",
                KeyEvent.VK_Q, cmdMask, "Ctrl+Q", "Cmd+Q"
        ));
    }

    /**
     * 从文件加载快捷键配置
     */
    public static void load() {
        File file = new File(SHORTCUT_CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
                // 应用加载的配置
                for (String key : shortcuts.keySet()) {
                    String keyCodeStr = props.getProperty(key + ".keyCode");
                    String modifiersStr = props.getProperty(key + ".modifiers");
                    if (keyCodeStr != null && modifiersStr != null) {
                        try {
                            int keyCode = Integer.parseInt(keyCodeStr);
                            int modifiers = Integer.parseInt(modifiersStr);
                            shortcuts.get(key).setKeyCode(keyCode);
                            shortcuts.get(key).setModifiers(modifiers);
                        } catch (NumberFormatException e) {
                            // 忽略解析错误，使用默认值
                        }
                    }
                }
            } catch (IOException e) {
                // 忽略，使用默认配置
            }
        }
    }

    /**
     * 保存快捷键配置到文件
     */
    public static void save() {
        for (Map.Entry<String, ShortcutConfig> entry : shortcuts.entrySet()) {
            String key = entry.getKey();
            ShortcutConfig config = entry.getValue();
            props.setProperty(key + ".keyCode", String.valueOf(config.getKeyCode()));
            props.setProperty(key + ".modifiers", String.valueOf(config.getModifiers()));
        }

        try (FileOutputStream fos = new FileOutputStream(SHORTCUT_CONFIG_FILE)) {
            props.store(fos, "EasyPostman Keyboard Shortcuts");
        } catch (IOException e) {
            // 忽略保存错误
        }
    }

    /**
     * 获取快捷键配置
     */
    public static ShortcutConfig getShortcut(String id) {
        return shortcuts.get(id);
    }

    /**
     * 获取 KeyStroke
     */
    public static KeyStroke getKeyStroke(String id) {
        ShortcutConfig config = shortcuts.get(id);
        if (config != null && config.isAssigned()) {
            return KeyStroke.getKeyStroke(config.getKeyCode(), config.getModifiers());
        }
        return null;
    }

    /**
     * 设置快捷键
     */
    public static void setShortcut(String id, int keyCode, int modifiers) {
        ShortcutConfig config = shortcuts.get(id);
        if (config != null) {
            config.setKeyCode(keyCode);
            config.setModifiers(modifiers);
        }
    }

    /**
     * 清除快捷键（置空），避免与其他软件的全局快捷键冲突
     */
    public static void clearShortcut(String id) {
        setShortcut(id, KeyEvent.VK_UNDEFINED, 0);
    }

    public static boolean isAssigned(String id) {
        ShortcutConfig config = shortcuts.get(id);
        return config != null && config.isAssigned();
    }

    /**
     * 获取快捷键的显示文本（根据操作系统），未设置时返回空字符串
     */
    public static String getShortcutText(String id) {
        ShortcutConfig config = shortcuts.get(id);
        if (config != null) {
            return config.getDisplayText();
        }
        return "";
    }

    /**
     * 重置为默认快捷键
     */
    public static void resetToDefaults() {
        shortcuts.clear();
        initDefaultShortcuts();
        save();
    }

    /**
     * 检查快捷键是否冲突
     */
    public static String checkConflict(String excludeId, int keyCode, int modifiers) {
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return null;
        }
        for (Map.Entry<String, ShortcutConfig> entry : shortcuts.entrySet()) {
            if (!entry.getKey().equals(excludeId)) {
                ShortcutConfig config = entry.getValue();
                if (config.getKeyCode() == keyCode && config.getModifiers() == modifiers) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * 快捷键配置类
     */
    @Getter
    public static class ShortcutConfig {
        private final String id;
        private final String nameKey;
        @Setter
        private int keyCode;
        @Setter
        private int modifiers;
        private final String windowsText;
        private final String macText;

        public ShortcutConfig(String id, String nameKey, int keyCode, int modifiers,
                              String windowsText, String macText) {
            this.id = id;
            this.nameKey = nameKey;
            this.keyCode = keyCode;
            this.modifiers = modifiers;
            this.windowsText = windowsText;
            this.macText = macText;
        }

        public boolean isAssigned() {
            return keyCode != KeyEvent.VK_UNDEFINED;
        }

        /**
         * 获取显示文本（根据操作系统），未设置时返回空字符串
         */
        public String getDisplayText() {
            if (!isAssigned()) {
                return "";
            }
            boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
            StringBuilder displayText = new StringBuilder();

            int cmdMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

            if ((modifiers & cmdMask) != 0) {
                displayText.append(isMac ? "Cmd+" : "Ctrl+");
            }
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
                displayText.append("Shift+");
            }
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
                displayText.append(isMac ? "Option+" : "Alt+");
            }
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0 && (modifiers & cmdMask) == 0) {
                displayText.append("Ctrl+");
            }

            displayText.append(KeyEvent.getKeyText(keyCode));

            return displayText.toString();
        }
    }
}
