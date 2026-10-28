package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.script.model.TestResult;
import com.laker.postman.util.I18nUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.*;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ResponseTabBadgeControllerTest {
    private boolean originalChinese;

    @BeforeMethod
    public void useEnglishLabels() {
        originalChinese = I18nUtil.isChinese();
        I18nUtil.setLocale("en");
    }

    @AfterMethod
    public void restoreLocale() {
        I18nUtil.setLocale(originalChinese ? "zh" : "en");
    }

    @Test
    public void headersCountShouldUseNeutralMetadataBadgeIcon() {
        JButton headersButton = new JButton();
        JButton[] tabButtons = httpTabButtons();
        tabButtons[TabBarBuilder.createHttpTabs().indexOf(TabBarBuilder.Tab.RESPONSE_HEADERS)] = headersButton;
        ResponseTabBadgeController controller = new ResponseTabBadgeController(tabButtons, TabBarBuilder.createHttpTabs());

        controller.updateResponseHeadersCount(6);

        assertEquals(headersButton.getText(), "Response Headers");
        assertTrue(headersButton.getIcon() instanceof TabCountBadgeIcon);
        TabCountBadgeIcon badgeIcon = (TabCountBadgeIcon) headersButton.getIcon();
        assertEquals(badgeIcon.getText(), "6");
        assertTrue(badgeIcon.getIconHeight() <= 16);
        assertEquals(badgeIcon.getForegroundColor(), ModernColors.getTextPrimary());
        assertFalse(ModernColors.getSuccess().equals(badgeIcon.getBackgroundColor()),
                "Header count is metadata, not success state");
    }

    @Test
    public void testsCountShouldKeepResultSemanticBadge() {
        JButton testsButton = new JButton();
        JButton[] tabButtons = httpTabButtons();
        tabButtons[TabBarBuilder.createHttpTabs().indexOf(TabBarBuilder.Tab.TESTS)] = testsButton;
        ResponseTabBadgeController controller = new ResponseTabBadgeController(tabButtons, TabBarBuilder.createHttpTabs());

        controller.updateTestResults(List.of(new TestResult("status", false, "boom")));

        assertEquals(testsButton.getText(), "Tests");
        assertTrue(testsButton.getIcon() instanceof TabCountBadgeIcon);
        TabCountBadgeIcon badgeIcon = (TabCountBadgeIcon) testsButton.getIcon();
        assertEquals(badgeIcon.getText(), "1");
        assertEquals(badgeIcon.getBackgroundColor().getRed(), ModernColors.getError().getRed());
        assertEquals(badgeIcon.getBackgroundColor().getGreen(), ModernColors.getError().getGreen());
        assertEquals(badgeIcon.getBackgroundColor().getBlue(), ModernColors.getError().getBlue());
        assertTrue(badgeIcon.getBackgroundColor().getAlpha() < 255);
        assertEquals(badgeIcon.getForegroundColor(), ModernColors.getTextInverse());
    }

    @Test
    public void labelsShouldResetWithoutCounts() {
        JButton headersButton = new JButton();
        JButton testsButton = new JButton();
        JButton[] tabButtons = httpTabButtons();
        TabBarBuilder.TabConfig tabConfig = TabBarBuilder.createHttpTabs();
        tabButtons[tabConfig.indexOf(TabBarBuilder.Tab.RESPONSE_HEADERS)] = headersButton;
        tabButtons[tabConfig.indexOf(TabBarBuilder.Tab.TESTS)] = testsButton;
        ResponseTabBadgeController controller = new ResponseTabBadgeController(tabButtons, tabConfig);

        controller.updateResponseHeadersCount(0);
        controller.updateTestResults(List.of());

        assertEquals(headersButton.getText(), "Response Headers");
        assertEquals(testsButton.getText(), "Tests");
        assertNull(headersButton.getIcon());
        assertNull(testsButton.getIcon());
        assertNull(headersButton.getDisabledIcon());
        assertNull(testsButton.getDisabledIcon());
    }

    @Test
    public void passingTestsShouldUseSuccessBadge() {
        JButton testsButton = new JButton();
        JButton[] tabButtons = httpTabButtons();
        tabButtons[TabBarBuilder.createHttpTabs().indexOf(TabBarBuilder.Tab.TESTS)] = testsButton;
        ResponseTabBadgeController controller = new ResponseTabBadgeController(tabButtons, TabBarBuilder.createHttpTabs());

        controller.updateTestResults(List.of(new TestResult("status", true, "")));

        assertNotNull(testsButton.getIcon());
        TabCountBadgeIcon badgeIcon = (TabCountBadgeIcon) testsButton.getIcon();
        assertEquals(badgeIcon.getBackgroundColor().getRed(), ModernColors.getSuccess().getRed());
        assertEquals(badgeIcon.getBackgroundColor().getGreen(), ModernColors.getSuccess().getGreen());
        assertEquals(badgeIcon.getBackgroundColor().getBlue(), ModernColors.getSuccess().getBlue());
        assertTrue(badgeIcon.getBackgroundColor().getAlpha() < 255);
    }

    private static JButton[] httpTabButtons() {
        JButton[] buttons = new JButton[TabBarBuilder.createHttpTabs().tabNames.length];
        java.util.Arrays.setAll(buttons, index -> new JButton());
        return buttons;
    }
}
