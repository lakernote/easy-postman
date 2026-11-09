package com.laker.postman.panel.toolbox;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.connection.ConnectionToolbarUi;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.testng.annotations.Test;

import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class ElasticsearchPanelLayoutTest extends AbstractSwingUiTest {

    @Test
    public void authFieldsShouldStayVisibleAndFollowAuthMode() throws Exception {
        ElasticsearchPanel panel = layoutPanel(1320);
        JTextField userField = fieldValue(panel, "usernameField", JTextField.class);
        JComboBox<?> authModeCombo = fieldValue(panel, "authModeCombo", JComboBox.class);

        authModeCombo.setSelectedIndex(0);
        assertTrue(userField.isVisible(), "auth fields should stay in the grid");
        assertFalse(userField.isEnabled(), "auth fields should be disabled without auth");

        authModeCombo.setSelectedIndex(1);
        assertTrue(userField.isEnabled(), "auth fields should be enabled with basic auth");
    }

    @Test
    public void connectionActionsShouldUseLabeledCompactButtons() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertConnectionActionsUseLabeledButtons(Locale.CHINESE);
            assertConnectionActionsUseLabeledButtons(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void disconnectedStateShouldDisableDataActionsAndShowEmptyHint() throws Exception {
        ElasticsearchPanel panel = layoutPanel(1320);
        JButton executeBtn = fieldValue(panel, "executeBtn", JButton.class);
        assertFalse(executeBtn.isEnabled(), "execute should be disabled before connecting");
        java.lang.reflect.Method method = ElasticsearchPanel.class.getDeclaredMethod("indexListEmptyText");
        method.setAccessible(true);
        assertEquals(method.invoke(panel), I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_INDEX_EMPTY_NOT_CONNECTED));
    }

    @Test
    public void queryToolbarActionsShouldUseSharedSvgButtonsWithTooltips() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertQueryToolbarActionsUseIconButtons(Locale.CHINESE);
            assertQueryToolbarActionsUseIconButtons(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void executeActionShouldUseShortTextWithFullTooltipAcrossLocales() throws Exception {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertExecuteActionUsesShortText(Locale.CHINESE);
            assertExecuteActionUsesShortText(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void leftSidebarTabsShouldUseCompactLocalizedTitlesAcrossLocales() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertLeftSidebarTabsUseCompactTitle(Locale.CHINESE);
            assertLeftSidebarTabsUseCompactTitle(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    private static void assertConnectionActionsUseLabeledButtons(Locale locale) {
        I18nUtil.setLocale(locale);
        ElasticsearchPanel panel = layoutPanel(1320);

        JButton connect = findButtonByTooltip(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_CONNECT_TOOLTIP));
        JButton test = findButtonByTooltip(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_TEST_CONNECTION_TOOLTIP));
        assertNotNull(connect, locale + " connect button should exist");
        assertNotNull(test, locale + " test connection button should exist");
        assertEquals(connect.getText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_CONNECT));
        assertEquals(test.getText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_TEST_CONNECTION));
        assertNotNull(connect.getIcon(), locale + " connect button should keep its icon");
    }

    private static void assertQueryToolbarActionsUseIconButtons(Locale locale) {
        I18nUtil.setLocale(locale);
        ElasticsearchPanel panel = layoutPanel(1320);

        assertIconOnlyToolbarButton(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_LOAD_TEMPLATE));
        assertIconButtonWithTooltip(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_FORMAT_JSON));
        assertIconButtonWithTooltip(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_COPY_RESULT));
        assertIconButtonWithTooltip(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_CLEAR));
    }

    private static void assertExecuteActionUsesShortText(Locale locale) throws Exception {
        I18nUtil.setLocale(locale);
        ElasticsearchPanel panel = layoutPanel(1320);

        JButton executeButton = fieldValue(panel, "executeBtn", JButton.class);
        assertEquals(executeButton.getText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_EXECUTE_SHORT),
                locale + " execute button should use compact text");
        assertEquals(executeButton.getToolTipText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_EXECUTE),
                locale + " execute button tooltip should keep full action");
        assertCompactPrimaryActionButton(executeButton, locale);
    }

    private static void assertLeftSidebarTabsUseCompactTitle(Locale locale) {
        I18nUtil.setLocale(locale);
        ElasticsearchPanel panel = layoutPanel(1320);

        JTabbedPane leftTabs = findTabbedPaneWithTitle(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_HISTORY));
        assertNotNull(leftTabs, locale + " left tabs should be present");
        assertEquals(leftTabs.getTitleAt(0), I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_INDEX_TAB),
                locale + " index tab should use compact title");
        assertEquals(leftTabs.getToolTipTextAt(0), I18nUtil.getMessage(MessageKeys.TOOLBOX_ES_INDEX_MANAGEMENT),
                locale + " index tab tooltip should keep full title");
        assertTrue(leftTabs.getBounds().width <= 260,
                locale + " left tabs should stay within sidebar width instead of expanding for long text");
    }

    private static ElasticsearchPanel layoutPanel(int width) {
        ElasticsearchPanel panel = new ElasticsearchPanel();
        panel.setSize(new Dimension(width, 760));
        layoutRecursively(panel);
        return panel;
    }

    private static void assertIconOnlyToolbarButton(ElasticsearchPanel panel, String tooltip) {
        JButton button = findButtonByTooltip(panel, tooltip);
        assertNotNull(button, "button not found: " + tooltip);
        assertIconOnly(button, tooltip);
        assertTrue(FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON.equals(
                        button.getClientProperty(FlatClientProperties.BUTTON_TYPE)),
                tooltip + " should reuse the shared toolbar button style");
        Dimension expectedSize = new Dimension(ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE,
                ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE);
        assertEquals(button.getPreferredSize(), expectedSize,
                tooltip + " should use the shared toolbar button preferred size");
        assertEquals(button.getMinimumSize(), expectedSize,
                tooltip + " should use the shared toolbar button minimum size");
        assertEquals(button.getMaximumSize(), expectedSize,
                tooltip + " should use the shared toolbar button maximum size");
    }

    private static void assertIconButtonWithTooltip(ElasticsearchPanel panel, String tooltip) {
        JButton button = findButtonByTooltip(panel, tooltip);
        assertNotNull(button, "button not found: " + tooltip);
        assertIconOnly(button, tooltip);
    }

    private static void assertIconOnly(JButton button, String tooltip) {
        assertTrue(button.getText() == null || button.getText().isBlank(),
                tooltip + " should use icon-only text");
        assertNotNull(button.getIcon(), tooltip + " should expose an SVG icon");
    }

    private static void assertCompactPrimaryActionButton(JButton button, Locale locale) {
        assertEquals(button.getClass().getName(),
                "com.laker.postman.common.component.button.CompactPrimaryButton",
                locale + " execute button should use the shared compact primary action component");
        assertEquals(button.getPreferredSize().height, 30,
                locale + " compact primary action should not use the larger regular primary height");
        assertEquals(button.getHeight(), 30,
                locale + " compact primary action should keep its rendered height");
        String style = String.valueOf(button.getClientProperty(FlatClientProperties.STYLE));
        assertTrue(style.contains("arc: 6"), locale + " compact primary action should be a rounded rectangle");
        assertTrue(style.contains("margin: 2,8,2,8"),
                locale + " compact primary action should use compact margins");
    }

    private static JButton findButtonByTooltip(Component component, String tooltip) {
        if (component instanceof JButton button && tooltip.equals(button.getToolTipText())) {
            return button;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton found = findButtonByTooltip(child, tooltip);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JTabbedPane findTabbedPaneWithTitle(Component component, String title) {
        if (component instanceof JTabbedPane tabbedPane) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                if (title.equals(tabbedPane.getTitleAt(i))) {
                    return tabbedPane;
                }
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTabbedPane found = findTabbedPaneWithTitle(child, title);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void invoke(Object instance, String methodName, boolean value) throws Exception {
        Method method = instance.getClass().getDeclaredMethod(methodName, boolean.class);
        method.setAccessible(true);
        method.invoke(instance, value);
    }

    private static <T> T fieldValue(Object instance, String fieldName, Class<T> type) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(instance));
    }

    private static void layoutRecursively(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }
}
