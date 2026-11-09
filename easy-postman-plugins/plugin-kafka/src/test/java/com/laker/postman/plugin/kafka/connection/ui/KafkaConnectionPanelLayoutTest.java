package com.laker.postman.plugin.kafka.connection.ui;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static com.laker.postman.plugin.kafka.KafkaI18n.t;

import com.laker.postman.common.component.EasyComboBox;
import com.laker.postman.plugin.kafka.MessageKeys;
import com.laker.postman.util.I18nUtil;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import org.testng.annotations.Test;

public class KafkaConnectionPanelLayoutTest {

    @Test
    public void saslFieldsShouldBeDisabledButStayVisibleForNonSaslProtocols() {
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });

        panel.setSaslFieldsEnabled(false);
        assertTrue(panel.clientIdField.isEnabled(), "client id should stay editable for non-SASL protocols");
        assertTrue(panel.saslMechanismCombo.isVisible() && panel.usernameField.isVisible()
                && panel.passwordField.isVisible(), "SASL inputs should stay visible to keep the layout stable");
        assertTrue(!panel.saslMechanismCombo.isEnabled() && !panel.usernameField.isEnabled()
                && !panel.passwordField.isEnabled(), "SASL inputs should be disabled for non-SASL protocols");

        panel.setSaslFieldsEnabled(true);
        assertTrue(panel.saslMechanismCombo.isEnabled() && panel.usernameField.isEnabled()
                && panel.passwordField.isEnabled(), "SASL inputs should be enabled for SASL protocols");
    }

    @Test
    public void connectionActionsShouldAlignWithPasswordRightEdge() {
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });
        panel.setSize(new Dimension(1800, 90));
        for (boolean saslEnabled : new boolean[]{true, false}) {
            panel.setSaslFieldsEnabled(saslEnabled);
            layoutRecursively(panel);
            Point cardOrigin = SwingUtilities.convertPoint(panel.btnCard.getParent(), panel.btnCard.getLocation(), panel.formGrid);
            assertEquals(cardOrigin.x + panel.btnCard.getWidth(), panel.passwordField.getX() + panel.passwordField.getWidth(),
                    "connect button right edge should align with password field right edge, sasl=" + saslEnabled);
            Point testOrigin = SwingUtilities.convertPoint(panel.testConnectionBtn.getParent(),
                    panel.testConnectionBtn.getLocation(), panel.formGrid);
            assertTrue(testOrigin.x + panel.testConnectionBtn.getWidth() < cardOrigin.x,
                    "test connection button should sit left of connect button");
            assertTrue(testOrigin.x >= panel.usernameField.getX() + panel.usernameField.getWidth(),
                    "connection actions should stay inside the last column group");
        }
    }

    @Test
    public void securityProtocolComboShouldUseFixedMaxEasyComboBox() {
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });

        assertTrue(panel.securityProtocolCombo instanceof EasyComboBox<?>,
                "security protocol selector should use EasyComboBox to preserve longest option width");
        assertFixedMaxWidth(panel.securityProtocolCombo);
    }

    @Test
    public void saslMechanismComboShouldUseFixedMaxEasyComboBox() {
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });

        assertTrue(panel.saslMechanismCombo instanceof EasyComboBox<?>,
                "SASL mechanism selector should use EasyComboBox to preserve longest option width");
        assertFixedMaxWidth(panel.saslMechanismCombo);
    }

    @Test
    public void formGridShouldFillModerateWidthWithoutTrailingGap() {
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });
        int preferredWidth = panel.formGrid.getPreferredSize().width;
        int containerWidth = preferredWidth + 200;
        panel.setSize(new Dimension(containerWidth, 90));
        layoutRecursively(panel);

        int rightEdge = panel.passwordField.getX() + panel.passwordField.getWidth();
        assertTrue(rightEdge >= panel.formGrid.getWidth() - 2,
                "password field should reach the grid right edge, edge=" + rightEdge
                        + ", grid=" + panel.formGrid.getWidth());
        assertTrue(panel.formGrid.getWidth() >= containerWidth - 24,
                "form grid width " + panel.formGrid.getWidth() + " should fill container " + containerWidth);
    }

    @Test
    public void narrowContainerShouldKeepAllFieldsAndActionsVisible() {
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });
        panel.setSize(new Dimension(900, 90));
        layoutRecursively(panel);

        assertTrue(panel.passwordField.getX() + panel.passwordField.getWidth() <= panel.formGrid.getWidth(),
                "password field should not be clipped in a narrow container");
        Point cardOrigin = SwingUtilities.convertPoint(panel.btnCard.getParent(), panel.btnCard.getLocation(), panel.formGrid);
        assertTrue(cardOrigin.x + panel.btnCard.getWidth() <= panel.formGrid.getWidth(),
                "connect button should stay visible in a narrow container");
    }

    @Test
    public void formGridShouldCapStretchInVeryWideContainer() {
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });
        panel.setSize(new Dimension(3000, 90));
        layoutRecursively(panel);

        int rightEdge = panel.passwordField.getX() + panel.passwordField.getWidth();
        assertTrue(rightEdge < 2400, "fields should stop stretching on ultra-wide windows, edge=" + rightEdge);
    }

    @Test
    public void connectionActionsShouldUseLabeledButtons() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertConnectionActionsUseLabeledButtons(Locale.CHINESE);
            assertConnectionActionsUseLabeledButtons(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void connectionRowsShouldAlignAcrossLocales() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertConnectionRowsAlign(Locale.CHINESE);
            assertConnectionRowsAlign(Locale.ENGLISH);
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    @Test
    public void securityLabelsShouldUseShortTextWithFullTooltipsAcrossLocales() {
        Locale originalLocale = I18nUtil.currentLocale();
        try {
            assertSecurityLabelsUseShortTextWithFullTooltips(Locale.CHINESE, "协议:", "SASL:");
            assertSecurityLabelsUseShortTextWithFullTooltips(Locale.ENGLISH, "Proto:", "SASL:");
        } finally {
            I18nUtil.setLocale(originalLocale);
        }
    }

    private static void assertConnectionActionsUseLabeledButtons(Locale locale) {
        I18nUtil.setLocale(locale);
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });
        panel.setSize(new Dimension(1320, 90));
        layoutRecursively(panel);

        assertLabeledButton(panel.connectBtn, t(MessageKeys.TOOLBOX_KAFKA_CONNECT));
        assertLabeledButton(panel.disconnectBtn, t(MessageKeys.TOOLBOX_KAFKA_DISCONNECT));
        assertLabeledButton(panel.testConnectionBtn, t(MessageKeys.TOOLBOX_KAFKA_TEST_CONNECTION));
    }

    private static void assertConnectionRowsAlign(Locale locale) {
        I18nUtil.setLocale(locale);
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });
        panel.setSize(new Dimension(1800, 90));
        layoutRecursively(panel);

        Point profileOrigin = SwingUtilities.convertPoint(panel.profileCombo.getParent(),
                panel.profileCombo.getLocation(), panel.formGrid);
        Point deleteOrigin = SwingUtilities.convertPoint(panel.deleteProfileBtn.getParent(),
                panel.deleteProfileBtn.getLocation(), panel.formGrid);
        assertColumn(locale, "Profile group / Protocol", profileOrigin.x,
                deleteOrigin.x + panel.deleteProfileBtn.getWidth(), panel.securityProtocolCombo);
        assertColumn(locale, "Host / SASL", panel.bootstrapField.getX(),
                panel.bootstrapField.getX() + panel.bootstrapField.getWidth(), panel.saslMechanismCombo);
        assertColumn(locale, "Client ID / User", panel.clientIdField.getX(),
                panel.clientIdField.getX() + panel.clientIdField.getWidth(), panel.usernameField);
        assertEquals(panel.bootstrapField.getY(), panel.clientIdField.getY(),
                locale + " Host and Client ID should share the first row");
        assertEquals(panel.securityProtocolCombo.getY(), panel.passwordField.getY(),
                locale + " security fields should share the second row");
        assertTrue(panel.securityProtocolCombo.getY() > panel.bootstrapField.getY(),
                locale + " security row should sit below the connection row");
    }

    private static void assertColumn(Locale locale, String name, int topLeft, int topRight, Component bottom) {
        assertEquals(bottom.getX(), topLeft, locale + " " + name + " should share the left edge");
        assertEquals(bottom.getX() + bottom.getWidth(), topRight, locale + " " + name + " should share the right edge");
    }

    private static void assertSecurityLabelsUseShortTextWithFullTooltips(Locale locale,
                                                                         String protocolText,
                                                                         String saslText) {
        I18nUtil.setLocale(locale);
        KafkaConnectionPanel panel = new KafkaConnectionPanel(() -> {
        }, () -> {
        });
        panel.setSize(new Dimension(1320, 90));
        layoutRecursively(panel);

        assertLabelWithTooltip(panel, protocolText, t(MessageKeys.TOOLBOX_KAFKA_SECURITY_PROTOCOL));
        assertLabelWithTooltip(panel, saslText, t(MessageKeys.TOOLBOX_KAFKA_SASL_MECHANISM));
    }

    private static void assertLabelWithTooltip(Component component, String text, String tooltip) {
        JLabel label = findLabelByText(component, text);
        assertNotNull(label, "label not found: " + text);
        assertEquals(label.getToolTipText(), tooltip, text + " should keep the full label in tooltip");
    }

    private static JLabel findLabelByText(Component component, String text) {
        if (component instanceof JLabel label && text.equals(label.getText())) {
            return label;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JLabel found = findLabelByText(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void assertLabeledButton(JButton button, String text) {
        assertEquals(button.getText(), text, "connection action should show readable text");
        assertNotNull(button.getIcon(), text + " should keep its icon");
        assertTrue(button.getPreferredSize().width >= button.getFontMetrics(button.getFont()).stringWidth(text),
                text + " should not be clipped");
    }

    private static void assertFixedMaxWidth(JComboBox<String> comboBox) {
        int shortestIndex = 0;
        int longestIndex = 0;
        int shortestWidth = Integer.MAX_VALUE;
        int longestWidth = -1;
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            int width = renderedWidth(comboBox, comboBox.getItemAt(i), i);
            if (width < shortestWidth) {
                shortestWidth = width;
                shortestIndex = i;
            }
            if (width > longestWidth) {
                longestWidth = width;
                longestIndex = i;
            }
        }

        comboBox.setSelectedIndex(shortestIndex);
        int compactSelectionWidth = comboBox.getPreferredSize().width;
        comboBox.setSelectedIndex(longestIndex);
        int longestSelectionWidth = comboBox.getPreferredSize().width;

        assertEquals(compactSelectionWidth, longestSelectionWidth,
                "fixed-max combo should not resize when selecting shorter localized values");
    }

    private static int renderedWidth(JComboBox<String> comboBox, String value, int index) {
        @SuppressWarnings("unchecked")
        ListCellRenderer<? super String> renderer = (ListCellRenderer<? super String>) comboBox.getRenderer();
        Component component = renderer.getListCellRendererComponent(new JList<>(), value, index, false, false);
        if (component instanceof JLabel label) {
            return label.getPreferredSize().width;
        }
        return component.getPreferredSize().width;
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
