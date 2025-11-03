package com.laker.postman.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class UserSettingsUtil {
    private static final String KEY_WINDOW_WIDTH = "windowWidth";
    private static final String KEY_WINDOW_HEIGHT = "windowHeight";
    private static final String KEY_WINDOW_MAXIMIZED = "windowMaximized";
    private static final String KEY_LANGUAGE = "language";
    private static final Path SETTINGS_PATH = SystemUtil.EASY_POSTMAN_HOME.resolve("user_settings.json");
    private static final Object lock = new Object();
    private static Map<String, Object> settingsCache = null;

    private UserSettingsUtil() {
        // 私有构造函数，禁止实例化
    }

    private static Map<String, Object> readSettings() {
        synchronized (lock) {
            if (settingsCache != null) return settingsCache;
            File file = SETTINGS_PATH.toFile();
            if (!file.exists()) {
                settingsCache = new HashMap<>();
                return settingsCache;
            }
            try {
                String json = FileUtil.readString(file, StandardCharsets.UTF_8);
                if (json == null || json.isBlank()) {
                    settingsCache = new HashMap<>();
                } else {
                    settingsCache = JSONUtil.parseObj(json);
                }
            } catch (Exception e) {
                log.warn("读取用户设置失败", e);
                settingsCache = new HashMap<>();
            }
            return settingsCache;
        }
    }

    private static void saveSettings() {
        synchronized (lock) {
            try {
                String json = JSONUtil.toJsonPrettyStr(settingsCache);
                FileUtil.writeString(json, SETTINGS_PATH.toFile(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("保存用户设置失败", e);
            }
        }
    }

    public static void set(String key, Object value) {
        synchronized (lock) {
            readSettings();
            settingsCache.put(key, value);
            saveSettings();
        }
    }

    public static Object get(String key) {
        return readSettings().get(key);
    }

    public static String getString(String key) {
        Object v = get(key);
        return v == null ? null : v.toString();
    }

    public static Boolean getBoolean(String key) {
        Object v = get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return Boolean.FALSE;
    }

    public static Integer getInt(String key) {
        Object v = get(key);
        if (v instanceof Integer i) return i;
        if (v instanceof String s) try {
            return Integer.parseInt(s);
        } catch (Exception ignore) {
            // 忽略转换异常
        }
        return null;
    }

    public static void remove(String key) {
        synchronized (lock) {
            readSettings();
            settingsCache.remove(key);
            saveSettings();
        }
    }

    public static Map<String, Object> getAll() {
        return Collections.unmodifiableMap(readSettings());
    }

    // 窗口状态专用方法
    public static void saveWindowState(int width, int height, boolean maximized) {
        synchronized (lock) {
            readSettings();
            settingsCache.put(KEY_WINDOW_WIDTH, width);
            settingsCache.put(KEY_WINDOW_HEIGHT, height);
            settingsCache.put(KEY_WINDOW_MAXIMIZED, maximized);
            saveSettings();
        }
    }


    public static Integer getWindowWidth() {
        return getInt(KEY_WINDOW_WIDTH);
    }

    public static Integer getWindowHeight() {
        return getInt(KEY_WINDOW_HEIGHT);
    }

    public static boolean isWindowMaximized() {
        Boolean v = getBoolean(KEY_WINDOW_MAXIMIZED);
        return v != null && v;
    }

    public static boolean hasWindowState() {
        return getWindowWidth() != null && getWindowHeight() != null;
    }

    // 语言设置专用方法
    public static void saveLanguage(String language) {
        set(KEY_LANGUAGE, language);
    }

    public static String getLanguage() {
        return getString(KEY_LANGUAGE);
    }
}