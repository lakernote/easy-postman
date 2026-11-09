package com.laker.postman.panel.toolbox;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.connection.ConnectionToolbarUi;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.testng.annotations.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class InfluxDbPanelLayoutTest extends AbstractSwingUiTest {

    @Test
    public void disconnectedStateShouldDisableDataActionsAndShowEmptyHint() throws Exception {
        InfluxDbPanel panel = layoutPanel(1320);
        JButton executeBtn = fieldValue(panel, "executeBtn", JButton.class);
        JButton reloadMetaBtn = fieldValue(panel, "reloadMetaBtn", JButton.class);
        assertFalse(executeBtn.isEnabled(), "execute should be disabled before connecting");
        assertFalse(reloadMetaBtn.isEnabled(), "metadata reload should be disabled before connecting");
        java.lang.reflect.Method method = InfluxDbPanel.class.getDeclaredMethod("measurementListEmptyText");
        method.setAccessible(true);
        assertEquals(method.invoke(panel),
                I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_MEASUREMENT_EMPTY_NOT_CONNECTED));
    }

    @Test
    public void localizedConnectionActionsShouldStayInsideCompactPanelWidth() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertConnectionActionsStayInsidePanel(Locale.CHINESE);
            assertConnectionActionsStayInsidePanel(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void connectionActionsShouldUseLabeledButtonsAndIconMetadataReload() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            I18nUtil.setLocale(Locale.CHINESE);
            InfluxDbPanel panel = layoutPanel(1320);

            JButton connect = findButtonByTooltip(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_CONNECT_TOOLTIP));
            assertNotNull(connect);
            assertEquals(connect.getText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_CONNECT));
            JButton test = findButtonByTooltip(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_TEST_CONNECTION_TOOLTIP));
            assertNotNull(test);
            assertEquals(test.getText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_TEST_CONNECTION));
            assertIconOnlyToolbarButton(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_RELOAD_META));
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void queryToolbarIconActionsShouldUseSharedSvgButtonsWithTooltips() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            I18nUtil.setLocale(Locale.CHINESE);
            InfluxDbPanel panel = layoutPanel(1320);

            assertIconOnlyToolbarButton(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_LOAD_TEMPLATE));
            assertIconOnlyToolbarButton(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_CLEAR));
            assertIconOnlyToolbarButton(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_COPY_RESULT));
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void executeActionShouldUseShortTextWithModeTooltipAcrossLocales() throws Exception {
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

    @Test
    public void v1ConnectionFieldPairsShouldAlignAcrossLocales() throws Exception {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertV1FieldPairsAlign(Locale.CHINESE);
            assertV1FieldPairsAlign(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void v2ConnectionFieldPairsShouldAlignAcrossLocales() throws Exception {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertV2FieldPairsAlign(Locale.CHINESE);
            assertV2FieldPairsAlign(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void v2LocalizedPlaceholdersShouldFitAcrossLocales() throws Exception {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertV2PlaceholdersFit(Locale.CHINESE);
            assertV2PlaceholdersFit(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void localizedConnectionLabelsShouldNotBeClipped() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertLocalizedLabelsFit(Locale.CHINESE);
            assertLocalizedLabelsFit(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void v1QueryBuilderLabelsShouldBeLocalizedAcrossLocales() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertV1QueryBuilderLabelsLocalized(Locale.CHINESE);
            assertV1QueryBuilderLabelsLocalized(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void queryTemplatesShouldUseLocalizedDisplayNamesAcrossLocales() throws Exception {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertQueryTemplatesLocalized(Locale.CHINESE);
            assertQueryTemplatesLocalized(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    private static void assertConnectionActionsStayInsidePanel(Locale locale) {
        I18nUtil.setLocale(locale);
        InfluxDbPanel panel = layoutPanel(1320);

        assertButtonInside(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_CONNECT_TOOLTIP));
        assertButtonInside(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_TEST_CONNECTION_TOOLTIP));
        assertButtonInside(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_RELOAD_META));
    }

    private static void assertV1FieldPairsAlign(Locale locale) throws Exception {
        I18nUtil.setLocale(locale);
        InfluxDbPanel panel = layoutPanel(1320);

        JComboBox<?> profileCombo = fieldValue(panel, "profileCombo", JComboBox.class);
        JTextField hostField = fieldValue(panel, "hostField", JTextField.class);
        JComboBox<?> modeCombo = fieldValue(panel, "modeCombo", JComboBox.class);
        JComboBox<?> dbCombo = fieldValue(panel, "dbCombo", JComboBox.class);
        JTextField userField = fieldValue(panel, "userField", JTextField.class);
        JTextField passwordField = fieldValue(panel, "passwordField", JTextField.class);

        assertEquals(boundsInPanel(panel, userField).x, boundsInPanel(panel, profileCombo).x,
                locale + " user field should align with profile field");
        assertEquals(boundsInPanel(panel, passwordField).x, boundsInPanel(panel, hostField).x,
                locale + " password field should align with host field");
        assertEquals(boundsInPanel(panel, dbCombo).x, boundsInPanel(panel, modeCombo).x,
                locale + " database field should align with mode field");
    }

    private static void assertV2FieldPairsAlign(Locale locale) throws Exception {
        I18nUtil.setLocale(locale);
        InfluxDbPanel panel = layoutPanel(1320);
        JComboBox<?> modeCombo = fieldValue(panel, "modeCombo", JComboBox.class);
        modeCombo.setSelectedItem(InfluxDbPanel.QueryMode.FLUX_V2);
        layoutRecursively(panel);

        JComboBox<?> profileCombo = fieldValue(panel, "profileCombo", JComboBox.class);
        JTextField tokenField = fieldValue(panel, "tokenField", JTextField.class);
        JTextField orgField = fieldValue(panel, "orgField", JTextField.class);

        assertEquals(boundsInPanel(panel, tokenField).x, boundsInPanel(panel, profileCombo).x,
                locale + " token field should align with profile field");
        assertEquals(boundsInPanel(panel, orgField).x, boundsInPanel(panel, modeCombo).x,
                locale + " org field should align with mode field");
    }

    private static void assertV2PlaceholdersFit(Locale locale) throws Exception {
        I18nUtil.setLocale(locale);
        InfluxDbPanel panel = layoutPanel(1320);
        JComboBox<?> modeCombo = fieldValue(panel, "modeCombo", JComboBox.class);
        modeCombo.setSelectedItem(InfluxDbPanel.QueryMode.FLUX_V2);
        layoutRecursively(panel);

        JTextField tokenField = fieldValue(panel, "tokenField", JTextField.class);
        JTextField orgField = fieldValue(panel, "orgField", JTextField.class);

        assertPlaceholderFits(tokenField, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_TOKEN_PLACEHOLDER), locale);
        assertPlaceholderFits(orgField, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_ORG_PLACEHOLDER), locale);
    }

    private static void assertExecuteActionUsesShortText(Locale locale) throws Exception {
        I18nUtil.setLocale(locale);
        InfluxDbPanel panel = layoutPanel(1320);

        JButton executeButton = fieldValue(panel, "executeBtn", JButton.class);
        JComboBox<?> modeCombo = fieldValue(panel, "modeCombo", JComboBox.class);

        assertEquals(executeButton.getText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_EXECUTE_SHORT),
                locale + " execute button should use compact text in InfluxQL mode");
        assertEquals(executeButton.getToolTipText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_EXECUTE_V1),
                locale + " execute button tooltip should keep the full InfluxQL action");
        assertCompactPrimaryActionButton(executeButton, locale);

        modeCombo.setSelectedItem(InfluxDbPanel.QueryMode.FLUX_V2);
        layoutRecursively(panel);
        assertEquals(executeButton.getText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_EXECUTE_SHORT),
                locale + " execute button should use compact text in Flux mode");
        assertEquals(executeButton.getToolTipText(), I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_EXECUTE_V2),
                locale + " execute button tooltip should keep the full Flux action");
        assertCompactPrimaryActionButton(executeButton, locale);
    }

    private static void assertLeftSidebarTabsUseCompactTitle(Locale locale) {
        I18nUtil.setLocale(locale);
        InfluxDbPanel panel = layoutPanel(1320);

        JTabbedPane leftTabs = findTabbedPaneWithTitle(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_HISTORY));
        assertNotNull(leftTabs, locale + " left tabs should be present");
        assertEquals(leftTabs.getTitleAt(0), I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_MEASUREMENT_TAB),
                locale + " measurement tab should use compact title");
        assertTrue(leftTabs.getBounds().width <= 260,
                locale + " left tabs should stay within sidebar width instead of expanding for long text");
    }

    private static void assertLocalizedLabelsFit(Locale locale) {
        I18nUtil.setLocale(locale);
        InfluxDbPanel panel = layoutPanel(1320);
        List<String> labels = List.of(
                I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_PROFILE),
                I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_HOST),
                I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_MODE),
                I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_DB),
                I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_USER),
                I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_PASS)
        );

        for (String text : labels) {
            JLabel label = findLabel(panel, text);
            assertNotNull(label, "label not found: " + text);
            assertTrue(label.getWidth() >= label.getPreferredSize().width,
                    locale + " label should not be clipped: " + text
                            + ", width=" + label.getWidth()
                            + ", preferred=" + label.getPreferredSize().width);
        }
    }

    private static void assertV1QueryBuilderLabelsLocalized(Locale locale) {
        I18nUtil.setLocale(locale);
        InfluxDbPanel panel = layoutPanel(1320);

        assertNotNull(findLabel(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_FIELD)),
                locale + " field label should be localized");
        assertNotNull(findLabel(panel, I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_TAGS)),
                locale + " tags label should be localized");
    }

    private static void assertQueryTemplatesLocalized(Locale locale) throws Exception {
        I18nUtil.setLocale(locale);
        InfluxDbPanel panel = layoutPanel(1320);

        JComboBox<?> templateCombo = fieldValue(panel, "templateCombo", JComboBox.class);
        JComboBox<?> modeCombo = fieldValue(panel, "modeCombo", JComboBox.class);

        assertEquals(templateCombo.getItemAt(0),
                I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_TEMPLATE_INFLUXQL_LATEST_100),
                locale + " InfluxQL templates should use localized names");

        modeCombo.setSelectedItem(InfluxDbPanel.QueryMode.FLUX_V2);
        layoutRecursively(panel);
        assertEquals(templateCombo.getItemAt(0),
                I18nUtil.getMessage(MessageKeys.TOOLBOX_INFLUX_TEMPLATE_FLUX_LATEST_100),
                locale + " Flux templates should use localized names");
    }

    private static InfluxDbPanel layoutPanel(int width) {
        InfluxDbPanel panel = new InfluxDbPanel();
        panel.setSize(new Dimension(width, 760));
        layoutRecursively(panel);
        return panel;
    }

    private static void assertButtonInside(InfluxDbPanel panel, String text) {
        JButton button = findButtonByTooltip(panel, text);
        assertNotNull(button, "button not found: " + text);
        Rectangle bounds = boundsInPanel(panel, button);
        assertTrue(bounds.x >= 0, text + " should not start outside the panel");
        assertTrue(bounds.x + bounds.width <= panel.getWidth(),
                text + " should stay inside panel width " + panel.getWidth()
                        + " but ended at " + (bounds.x + bounds.width));
    }

    private static Rectangle boundsInPanel(InfluxDbPanel panel, JButton button) {
        return SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), panel);
    }

    private static Rectangle boundsInPanel(InfluxDbPanel panel, JComponent component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), panel);
    }

    private static void assertPlaceholderFits(JTextField field, String placeholder, Locale locale) {
        int textWidth = field.getFontMetrics(field.getFont()).stringWidth(placeholder);
        int horizontalInsets = field.getInsets().left + field.getInsets().right;
        int requiredWidth = textWidth + horizontalInsets + 8;
        assertTrue(field.getWidth() >= requiredWidth,
                locale + " placeholder should fit: " + placeholder
                        + ", width=" + field.getWidth()
                        + ", required=" + requiredWidth);
    }

    private static void assertIconOnlyToolbarButton(InfluxDbPanel panel, String tooltip) {
        JButton button = findButtonByTooltip(panel, tooltip);
        assertNotNull(button, "button not found: " + tooltip);
        assertTrue(button.getText() == null || button.getText().isBlank(),
                tooltip + " should use icon-only text");
        assertNotNull(button.getIcon(), tooltip + " should expose an SVG icon");
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

    private static JLabel findLabel(Component component, String text) {
        if (component instanceof JLabel label && text.equals(label.getText())) {
            return label;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JLabel found = findLabel(child, text);
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
