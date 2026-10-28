package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.script.model.TestResult;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import java.awt.Color;
import java.util.List;

/**
 * 响应标签计数/状态徽标控制器。
 * <p>
 * Headers 和 Tests 的标题会随结果动态变化，集中在这里避免 ResponsePanel 混入文案拼装细节。
 */
final class ResponseTabBadgeController {
    private static final int BADGE_ICON_TEXT_GAP = 5;

    private final JButton[] tabButtons;
    private final TabBarBuilder.TabConfig tabConfig;

    ResponseTabBadgeController(JButton[] tabButtons, TabBarBuilder.TabConfig tabConfig) {
        this.tabButtons = tabButtons;
        this.tabConfig = tabConfig;
    }

    void updateResponseHeadersCount(int count) {
        int tabIndex = tabConfig.indexOf(TabBarBuilder.Tab.RESPONSE_HEADERS);
        if (tabIndex < 0 || tabIndex >= tabButtons.length) {
            return;
        }
        JButton headersButton = tabButtons[tabIndex];
        String label = I18nUtil.getMessage(MessageKeys.TAB_RESPONSE_HEADERS);
        if (count > 0) {
            setTabBadge(headersButton,
                    label,
                    String.valueOf(count),
                    ModernColors.getTextPrimary(),
                    neutralBadgeBackground(),
                    neutralBadgeBorder());
            return;
        }
        clearTabBadge(headersButton, label);
    }

    void updateTestResults(List<TestResult> testResults) {
        int tabIndex = tabConfig.indexOf(TabBarBuilder.Tab.TESTS);
        if (tabIndex < 0 || tabIndex >= tabButtons.length) {
            return;
        }
        JButton testsButton = tabButtons[tabIndex];
        String label = I18nUtil.getMessage(MessageKeys.TAB_TESTS);
        if (testResults != null && !testResults.isEmpty()) {
            boolean allPassed = testResults.stream().allMatch(r -> r.passed);
            Color background = allPassed ? ModernColors.getSuccess() : ModernColors.getError();
            setTabBadge(testsButton,
                    label,
                    String.valueOf(testResults.size()),
                    ModernColors.getTextInverse(),
                    resultBadgeBackground(background),
                    resultBadgeBorder(background));
            return;
        }
        clearTabBadge(testsButton, label);
    }

    private static void setTabBadge(JButton button,
                                    String label,
                                    String badgeText,
                                    Color foreground,
                                    Color background,
                                    Color border) {
        button.setText(label);
        TabCountBadgeIcon badgeIcon = new TabCountBadgeIcon(button, badgeText, foreground, background, border);
        button.setIcon(badgeIcon);
        button.setDisabledIcon(badgeIcon);
        button.setHorizontalTextPosition(SwingConstants.LEFT);
        button.setIconTextGap(BADGE_ICON_TEXT_GAP);
        button.getAccessibleContext().setAccessibleName(label + " " + badgeText);
        button.setToolTipText(label + ": " + badgeText);
        refreshTabButton(button);
    }

    private static void clearTabBadge(JButton button, String label) {
        button.setText(label);
        button.setIcon(null);
        button.setDisabledIcon(null);
        button.getAccessibleContext().setAccessibleName(label);
        button.setToolTipText(null);
        refreshTabButton(button);
    }

    private static Color neutralBadgeBackground() {
        return ModernColors.withAlpha(ModernColors.getTextSecondary(), ModernColors.isDarkTheme() ? 72 : 30);
    }

    private static Color neutralBadgeBorder() {
        return ModernColors.withAlpha(ModernColors.getTextSecondary(), ModernColors.isDarkTheme() ? 120 : 72);
    }

    private static Color resultBadgeBackground(Color color) {
        return ModernColors.withAlpha(color, ModernColors.isDarkTheme() ? 225 : 235);
    }

    private static Color resultBadgeBorder(Color color) {
        return ModernColors.withAlpha(color, ModernColors.isDarkTheme() ? 160 : 140);
    }

    private static void refreshTabButton(JButton button) {
        button.revalidate();
        if (button.getParent() != null) {
            button.getParent().revalidate();
            button.getParent().repaint();
        }
        button.repaint();
    }
}
