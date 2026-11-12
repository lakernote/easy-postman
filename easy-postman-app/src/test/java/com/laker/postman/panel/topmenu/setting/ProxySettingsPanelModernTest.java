package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.common.component.EasyPasswordField;
import com.laker.postman.common.component.setting.SettingsFieldRow;
import com.laker.postman.common.constants.ConfigPathConstants;
import com.laker.postman.http.runtime.app.AppHttpRuntimeSettingsAdapter;
import com.laker.postman.settings.PreferencesStore;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.testng.annotations.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class ProxySettingsPanelModernTest extends AbstractSwingUiTest {

    @Test
    public void shouldDimProxyFieldRowsWhenProxyIsDisabled() throws Exception {
        boolean oldProxyEnabled = SettingManager.isProxyEnabled();
        String oldProxyMode = SettingManager.getProxyMode();
        String oldProxyUsername = SettingManager.getProxyUsername();

        try {
            SettingManager.setProxyEnabled(false);
            SettingManager.setProxyMode(SettingManager.PROXY_MODE_MANUAL);
            SettingManager.setProxyUsername("proxy-user");

            AtomicReference<ProxySettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ProxySettingsPanelModern panel = new ProxySettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            ProxySettingsPanelModern panel = panelRef.get();
            SettingsFieldRow modeRow = findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE)
            );
            SettingsFieldRow hostRow = findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_HOST)
            );
            SettingsFieldRow usernameRow = findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_USERNAME)
            );

            assertRowDisabled(modeRow);
            assertRowDisabled(hostRow);
            assertRowDisabled(usernameRow);

            JCheckBox enabledCheckBox = findCheckBox(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_ENABLED_CHECKBOX)
            );
            assertNotNull(enabledCheckBox);
            SwingUtilities.invokeAndWait(() -> enabledCheckBox.setSelected(true));

            assertRowEnabled(modeRow);
            assertRowEnabled(hostRow);
            assertRowEnabled(usernameRow);
        } finally {
            SettingManager.setProxyEnabled(oldProxyEnabled);
            SettingManager.setProxyMode(oldProxyMode);
            SettingManager.setProxyUsername(oldProxyUsername);
        }
    }

    @Test
    public void shouldUseRevealablePasswordFieldAndAlwaysShowConnectionCheck() throws Exception {
        boolean oldProxyEnabled = SettingManager.isProxyEnabled();
        String oldProxyMode = SettingManager.getProxyMode();

        try {
            SettingManager.setProxyEnabled(true);
            SettingManager.setProxyMode(SettingManager.PROXY_MODE_MANUAL);

            AtomicReference<ProxySettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ProxySettingsPanelModern panel = new ProxySettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            ProxySettingsPanelModern panel = panelRef.get();
            assertNotNull(findFirstComponent(panel, EasyPasswordField.class));

            JLabel previewLabel = findLabel(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PREVIEW_TARGET)
            );
            assertNotNull(previewLabel);
            assertTrue(isEffectivelyVisible(previewLabel, panel));
            JButton checkButton = findButton(panel, I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_CHECK_BUTTON));
            assertNotNull(checkButton);
            assertTrue(checkButton.isEnabled());
            assertTrue(isEffectivelyVisible(checkButton, panel));

            JComboBox<?> modeComboBox = findComboBoxContaining(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE_SYSTEM)
            );
            assertNotNull(modeComboBox);
            SwingUtilities.invokeAndWait(() -> modeComboBox.setSelectedItem(
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE_SYSTEM)
            ));

            assertTrue(isEffectivelyVisible(previewLabel, panel));
        } finally {
            SettingManager.setProxyEnabled(oldProxyEnabled);
            SettingManager.setProxyMode(oldProxyMode);
        }
    }

    @Test
    public void shouldSeparateManualProxyAuthenticationAndSystemPreviewSections() throws Exception {
        boolean oldProxyEnabled = SettingManager.isProxyEnabled();
        String oldProxyMode = SettingManager.getProxyMode();
        String oldProxyUsername = SettingManager.getProxyUsername();

        try {
            SettingManager.setProxyEnabled(true);
            SettingManager.setProxyMode(SettingManager.PROXY_MODE_SYSTEM);
            SettingManager.setProxyUsername("proxy-user");

            AtomicReference<ProxySettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ProxySettingsPanelModern panel = new ProxySettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            ProxySettingsPanelModern panel = panelRef.get();
            assertNotNull(findLabel(panel, I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MANUAL_SECTION_TITLE)));
            assertNotNull(findLabel(panel, I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_AUTH_SECTION_TITLE)));
            JLabel previewSectionLabel = findLabel(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PREVIEW_SECTION_TITLE)
            );
            assertNotNull(previewSectionLabel);
            assertTrue(isEffectivelyVisible(previewSectionLabel, panel));

            assertRowDisabled(findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_TYPE)
            ));
            assertRowDisabled(findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_HOST)
            ));
            assertRowDisabled(findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PORT)
            ));
            assertRowEnabled(findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_USERNAME)
            ));
            assertRowEnabled(findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PASSWORD)
            ));

            JLabel previewLabel = findLabel(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PREVIEW_TARGET)
            );
            assertNotNull(previewLabel);
            assertTrue(isEffectivelyVisible(previewLabel, panel));
        } finally {
            SettingManager.setProxyEnabled(oldProxyEnabled);
            SettingManager.setProxyMode(oldProxyMode);
            SettingManager.setProxyUsername(oldProxyUsername);
        }
    }

    @Test
    public void shouldSaveDisabledManualProxySettingsWithoutPort() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        Path configPath = Path.of(ConfigPathConstants.EASY_POSTMAN_SETTINGS);
        boolean configExisted = Files.exists(configPath);
        String originalConfig = configExisted ? Files.readString(configPath) : null;

        try {
            Properties testSettings = new Properties();
            testSettings.setProperty("proxy_enabled", "false");
            testSettings.setProperty("proxy_mode", SettingManager.PROXY_MODE_MANUAL);
            testSettings.setProperty("proxy_port", "");
            testSettings.setProperty("proxy_username", "before-save");
            replaceSettingsForTest(props, configPath, testSettings);

            AtomicReference<ProxySettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ProxySettingsPanelModern panel = new ProxySettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            ProxySettingsPanelModern panel = panelRef.get();
            JTextField portField = (JTextField) findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PORT)
            ).inputComponent();
            JTextField usernameField = (JTextField) findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_USERNAME)
            ).inputComponent();

            SwingUtilities.invokeAndWait(() -> {
                portField.setText("");
                usernameField.setText("saved-disabled-proxy-user");
                panel.saveBtn.doClick();
            });

            assertFalse(SettingManager.isProxyEnabled());
            assertTrue(SettingManager.isManualProxyMode());
            assertEquals(SettingManager.getProxyUsername(), "saved-disabled-proxy-user");
            assertEquals(new AppHttpRuntimeSettingsAdapter().getProxyPort(), 0);
        } finally {
            props.clear();
            props.putAll(backup);
            restoreConfig(configPath, configExisted, originalConfig);
        }
    }

    @Test
    public void shouldNotPartiallyEnableManualProxyWhenRequiredFieldsAreBlank() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        Path configPath = Path.of(ConfigPathConstants.EASY_POSTMAN_SETTINGS);
        boolean configExisted = Files.exists(configPath);
        String originalConfig = configExisted ? Files.readString(configPath) : null;

        try {
            Properties testSettings = new Properties();
            testSettings.setProperty("proxy_enabled", "false");
            testSettings.setProperty("proxy_mode", SettingManager.PROXY_MODE_MANUAL);
            testSettings.setProperty("proxy_port", "");
            replaceSettingsForTest(props, configPath, testSettings);

            AtomicReference<ProxySettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ProxySettingsPanelModern panel = new ProxySettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            ProxySettingsPanelModern panel = panelRef.get();
            JCheckBox enabledCheckBox = findCheckBox(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_ENABLED_CHECKBOX)
            );
            JTextField hostField = (JTextField) findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_HOST)
            ).inputComponent();
            JTextField portField = (JTextField) findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PORT)
            ).inputComponent();

            SwingUtilities.invokeAndWait(() -> {
                enabledCheckBox.setSelected(true);
                hostField.setText("");
                portField.setText("");
                panel.saveBtn.doClick();
            });

            assertFalse(SettingManager.isProxyEnabled());
        } finally {
            props.clear();
            props.putAll(backup);
            restoreConfig(configPath, configExisted, originalConfig);
        }
    }

    @Test
    public void shouldDisableAndClearProxyCredentialsWhenAuthenticationIsUnchecked() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        Path configPath = Path.of(ConfigPathConstants.EASY_POSTMAN_SETTINGS);
        boolean configExisted = Files.exists(configPath);
        String originalConfig = configExisted ? Files.readString(configPath) : null;

        try {
            Properties testSettings = new Properties();
            testSettings.setProperty("proxy_enabled", "true");
            testSettings.setProperty("proxy_mode", SettingManager.PROXY_MODE_SYSTEM);
            testSettings.setProperty("proxy_username", "proxy-user");
            testSettings.setProperty("proxy_password", "proxy-pass");
            replaceSettingsForTest(props, configPath, testSettings);

            AtomicReference<ProxySettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ProxySettingsPanelModern panel = new ProxySettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            ProxySettingsPanelModern panel = panelRef.get();
            JCheckBox authCheckBox = findCheckBox(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_AUTH_CHECKBOX)
            );
            assertNotNull(authCheckBox);
            assertTrue(authCheckBox.isSelected());
            SettingsFieldRow usernameRow = findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_USERNAME)
            );
            assertRowEnabled(usernameRow);

            SwingUtilities.invokeAndWait(() -> authCheckBox.setSelected(false));
            assertRowDisabled(usernameRow);
            assertRowDisabled(findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PASSWORD)
            ));

            SwingUtilities.invokeAndWait(() -> panel.saveBtn.doClick());
            assertEquals(SettingManager.getProxyUsername(), "");
            assertEquals(SettingManager.getProxyPassword(), "");
        } finally {
            props.clear();
            props.putAll(backup);
            restoreConfig(configPath, configExisted, originalConfig);
        }
    }

    @Test
    public void shouldRejectEnabledAuthenticationWithoutCredentials() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        Path configPath = Path.of(ConfigPathConstants.EASY_POSTMAN_SETTINGS);
        boolean configExisted = Files.exists(configPath);
        String originalConfig = configExisted ? Files.readString(configPath) : null;

        try {
            Properties testSettings = new Properties();
            testSettings.setProperty("proxy_enabled", "true");
            testSettings.setProperty("proxy_mode", SettingManager.PROXY_MODE_SYSTEM);
            testSettings.setProperty("proxy_username", "before-save");
            testSettings.setProperty("proxy_password", "before-pass");
            replaceSettingsForTest(props, configPath, testSettings);

            AtomicReference<ProxySettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ProxySettingsPanelModern panel = new ProxySettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            ProxySettingsPanelModern panel = panelRef.get();
            JComponent passwordField = findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PASSWORD)
            ).inputComponent();
            SwingUtilities.invokeAndWait(() -> {
                ((EasyPasswordField) passwordField).setText("");
                panel.saveBtn.doClick();
            });

            assertEquals(SettingManager.getProxyPassword(), "before-pass");
        } finally {
            props.clear();
            props.putAll(backup);
            restoreConfig(configPath, configExisted, originalConfig);
        }
    }

    @Test
    public void shouldKeepTextFieldsAsWideAsComboBoxesInNarrowLayout() throws Exception {
        boolean oldProxyEnabled = SettingManager.isProxyEnabled();
        String oldProxyMode = SettingManager.getProxyMode();

        try {
            SettingManager.setProxyEnabled(true);
            SettingManager.setProxyMode(SettingManager.PROXY_MODE_MANUAL);

            AtomicReference<int[]> widths = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ProxySettingsPanelModern panel = new ProxySettingsPanelModern();
                panel.getPreferredSize();
                panel.setSize(520, 1200);
                layoutRecursively(panel);
                widths.set(new int[]{
                        findFieldRow(panel, I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_MODE)).inputComponent().getWidth(),
                        findFieldRow(panel, I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_HOST)).inputComponent().getWidth(),
                        findFieldRow(panel, I18nUtil.getMessage(MessageKeys.SETTINGS_PROXY_PASSWORD)).inputComponent().getWidth()
                });
            });

            int comboWidth = widths.get()[0];
            assertTrue(comboWidth > 0);
            assertEquals(widths.get()[1], comboWidth);
            assertEquals(widths.get()[2], comboWidth);
        } finally {
            SettingManager.setProxyEnabled(oldProxyEnabled);
            SettingManager.setProxyMode(oldProxyMode);
        }
    }

    private static void layoutRecursively(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                layoutRecursively(child);
            }
        }
    }

    private static <T extends JComponent> T findFirstComponent(Container container, Class<T> componentType) {
        for (Component component : container.getComponents()) {
            if (componentType.isInstance(component)) {
                return componentType.cast(component);
            }
            if (component instanceof Container child) {
                T found = findFirstComponent(child, componentType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static SettingsFieldRow findFieldRow(Container container, String labelText) {
        for (Component component : container.getComponents()) {
            if (component instanceof SettingsFieldRow row && labelText.equals(row.label().getText())) {
                return row;
            }
            if (component instanceof Container child) {
                SettingsFieldRow found = findFieldRow(child, labelText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JButton findButton(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container nested) {
                JButton found = findButton(nested, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JCheckBox findCheckBox(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JCheckBox checkBox && text.equals(checkBox.getText())) {
                return checkBox;
            }
            if (component instanceof Container child) {
                JCheckBox found = findCheckBox(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void assertRowDisabled(SettingsFieldRow row) {
        assertNotNull(row);
        assertFalse(row.isEnabled());
        assertFalse(row.label().isEnabled());
        assertFalse(row.inputComponent().isEnabled());
    }

    private static void assertRowEnabled(SettingsFieldRow row) {
        assertNotNull(row);
        assertTrue(row.isEnabled());
        assertTrue(row.label().isEnabled());
        assertTrue(row.inputComponent().isEnabled());
    }

    private static JLabel findLabel(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (component instanceof Container child) {
                JLabel found = findLabel(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JComboBox<?> findComboBoxContaining(Container container, String itemText) {
        for (Component component : container.getComponents()) {
            if (component instanceof JComboBox<?> comboBox && containsItem(comboBox, itemText)) {
                return comboBox;
            }
            if (component instanceof Container child) {
                JComboBox<?> found = findComboBoxContaining(child, itemText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean containsItem(JComboBox<?> comboBox, String itemText) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            Object item = comboBox.getItemAt(i);
            if (itemText.equals(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEffectivelyVisible(Component component, Component stopAt) {
        Component current = component;
        while (current != null) {
            if (!current.isVisible()) {
                return false;
            }
            if (current == stopAt) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static Properties getSettingsProperties() throws Exception {
        Field propsField = SettingManager.class.getDeclaredField("props");
        propsField.setAccessible(true);
        return (Properties) propsField.get(null);
    }

    private static void replaceSettingsForTest(Properties props, Path configPath, Properties testSettings) {
        props.clear();
        props.putAll(testSettings);
        PreferencesStore.storeProperties(testSettings, configPath);
    }

    private static void restoreConfig(Path configPath, boolean configExisted, String originalConfig) throws Exception {
        if (configExisted) {
            Files.writeString(configPath, originalConfig);
        } else {
            Files.deleteIfExists(configPath);
        }
    }
}
