package com.laker.postman.util;

import lombok.experimental.UtilityClass;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * 字体工具类，提供系统默认字体，保留完整的字体降级链以支持 emoji 等特殊字符
 */
@UtilityClass
public class FontsUtil {

    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 32;
    private static final int DEFAULT_FONT_SIZE = 13;
    private static final String DEFAULT_FONT_KEY = "defaultFont";
    private static final String LABEL_FONT_KEY = "Label.font";
    private static final String MONOSPACED_FONT_KEY = "monospaced.font";

    private static int getBaseFontSize() {
        return getBaseUiFont().getSize();
    }

    private static Font getBaseUiFont() {
        Font baseFont = UIManager.getFont(DEFAULT_FONT_KEY);
        if (baseFont == null) {
            baseFont = UIManager.getFont(LABEL_FONT_KEY);
        }
        if (baseFont == null) {
            baseFont = new JLabel().getFont();
        }
        return baseFont == null ? new Font(Font.DIALOG, Font.PLAIN, DEFAULT_FONT_SIZE) : baseFont;
    }

    /**
     * 获取默认字体，从 UIManager 派生以保留降级链，支持 emoji 等特殊字符
     *
     * @param style 字体样式 (Font.PLAIN, Font.BOLD, Font.ITALIC)
     * @param size  字体大小
     * @return Font 对象，从系统默认字体派生
     */
    private static Font getDefaultFont(int style, int size) {
        return getBaseUiFont().deriveFont(style, (float) size);
    }

    /**
     * 获取默认字体，使用用户设置的字体大小
     * 从 UIManager 派生以保留降级链，支持 emoji 等特殊字符
     *
     * @param style 字体样式 (Font.PLAIN, Font.BOLD, Font.ITALIC)
     * @return Font 对象，使用用户设置的字体大小
     */
    public static Font getDefaultFont(int style) {
        return getDefaultFont(style, getBaseFontSize());
    }

    /**
     * 获取默认字体，使用相对于用户设置字体大小的偏移
     * 例如：如果用户设置字体为 14，offset 为 -2，则返回 12 号字体
     *
     * @param style  字体样式 (Font.PLAIN, Font.BOLD, Font.ITALIC)
     * @param offset 相对于用户设置字体大小的偏移量（可正可负）
     * @return Font 对象
     */
    public static Font getDefaultFontWithOffset(int style, int offset) {
        int fontSize = getBaseFontSize() + offset;
        fontSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, fontSize));
        return getDefaultFont(style, fontSize);
    }

    /**
     * 获取等宽字体，字号跟随用户 UI 字号设置。
     *
     * @param style  字体样式 (Font.PLAIN, Font.BOLD, Font.ITALIC)
     * @param offset 相对于用户设置字体大小的偏移量（可正可负）
     * @return Font 对象
     */
    /**
     * 自绘文字前调用：沿用系统桌面文字抗锯齿设置（缺省时开启灰度抗锯齿），与 Swing 组件文字渲染保持一致。
     */
    public static void applyTextRenderingHints(Graphics2D g2) {
        Object desktopHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (desktopHints instanceof Map<?, ?> hints) {
            g2.addRenderingHints(hints);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }

    public static Font getMonospacedFontWithOffset(int style, int offset) {
        Font baseFont = UIManager.getFont(MONOSPACED_FONT_KEY);
        if (baseFont == null) {
            baseFont = new Font(Font.MONOSPACED, Font.PLAIN, getBaseFontSize());
        }
        int fontSize = getBaseFontSize() + offset;
        fontSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, fontSize));
        return baseFont.deriveFont(style, (float) fontSize);
    }
}
