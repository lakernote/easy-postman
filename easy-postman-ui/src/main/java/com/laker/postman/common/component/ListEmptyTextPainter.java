package com.laker.postman.common.component;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.FontsUtil;
import lombok.experimental.UtilityClass;

import javax.swing.JComponent;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 在空列表/空表格内部绘制居中的提示文本（支持 \n 多行），替代额外的提示标签。
 */
@UtilityClass
public class ListEmptyTextPainter {

    private static final int TOP_PADDING = 16;
    private static final int MIN_X = 8;

    public static void paint(JComponent component, Graphics g, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
            g2.setColor(ModernColors.getTextHint());
            FontMetrics fm = g2.getFontMetrics();
            int y = TOP_PADDING + fm.getAscent();
            for (String line : text.split("\n")) {
                String trimmed = line.strip();
                g2.drawString(trimmed, Math.max(MIN_X, (component.getWidth() - fm.stringWidth(trimmed)) / 2), y);
                y += fm.getHeight();
            }
        } finally {
            g2.dispose();
        }
    }
}
