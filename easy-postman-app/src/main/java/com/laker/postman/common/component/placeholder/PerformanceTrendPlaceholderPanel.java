package com.laker.postman.common.component.placeholder;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import java.awt.*;

public class PerformanceTrendPlaceholderPanel extends AbstractPlaceholderPanel {

    public PerformanceTrendPlaceholderPanel() {
        super(new BorderLayout());
        configureRoot(getChartPanelBackgroundColor(), new Insets(18, 18, 18, 18));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        configureRoot(getChartPanelBackgroundColor(), new Insets(18, 18, 18, 18));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FontsUtil.applyTextRenderingHints(g2);
        try {
            enableAntialias(g2);

            int width = getWidth();
            int height = getHeight();
            if (width <= 120 || height <= 120) {
                return;
            }

            Color cardBg = ModernColors.getCardBackgroundColor();
            Color borderColor = ModernColors.getBorderLightColor();
            Color blockColor = getSkeletonBlockColor();
            Color accentColor = getSkeletonAccentColor();
            Color titleColor = ModernColors.getTextSecondary();
            Color hintColor = ModernColors.getTextHint();

            int cardW = Math.min(520, width - 48);
            int cardH = 190;
            int cardX = (width - cardW) / 2;
            int cardY = Math.max(28, (height - cardH) / 2 - 24);

            fillRoundedBlock(g2, cardX, cardY, cardW, cardH, cardBg, 20);
            g2.setColor(borderColor);
            g2.drawRoundRect(cardX, cardY, cardW, cardH, 20, 20);

            int topBarX = cardX + 24;
            int topBarY = cardY + 22;
            fillRoundedBlock(g2, topBarX, topBarY, 112, 10, accentColor, 10);

            fillRoundedBlock(g2, topBarX, topBarY + 28, cardW - 48, 14, blockColor, 12);
            fillRoundedBlock(g2, topBarX, topBarY + 56, cardW - 140, 12, blockColor, 10);
            fillRoundedBlock(g2, topBarX, topBarY + 80, cardW - 180, 12, blockColor, 10);

            int chipY = topBarY + 118;
            int chipX = topBarX;
            int[] chipWidths = {74, 96, 82};
            for (int chipWidth : chipWidths) {
                fillRoundedBlock(g2, chipX, chipY, chipWidth, 28, blockColor, 14);
                chipX += chipWidth + 12;
            }

            g2.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 0));
            g2.setColor(titleColor);
            String title = I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_PLACEHOLDER_TITLE);
            FontMetrics titleMetrics = g2.getFontMetrics();
            int titleX = (width - titleMetrics.stringWidth(title)) / 2;
            int titleY = cardY + cardH + 42;
            g2.drawString(title, titleX, titleY);

            g2.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
            g2.setColor(hintColor);
            String hint = I18nUtil.getMessage(MessageKeys.PERFORMANCE_TREND_PLACEHOLDER_HINT);
            FontMetrics hintMetrics = g2.getFontMetrics();
            int hintX = (width - hintMetrics.stringWidth(hint)) / 2;
            g2.drawString(hint, hintX, titleY + 24);
        } finally {
            g2.dispose();
        }
    }

    private Color getChartPanelBackgroundColor() {
        return ModernColors.getCardBackgroundColor();
    }
}
