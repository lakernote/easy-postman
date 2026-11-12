package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.common.component.setting.SettingsFieldRow;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.testng.annotations.Test;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class RequestSettingsPanelModernTest extends AbstractSwingUiTest {

    @Test
    public void remoteScriptFieldsShouldDimAndEnableWithRemoteLoadingToggle() throws Exception {
        boolean oldRemoteEnabled = SettingManager.isRemoteJsRequireEnabled();

        try {
            SettingManager.setRemoteJsRequireEnabled(false);
            AtomicReference<RequestSettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                RequestSettingsPanelModern panel = new RequestSettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            RequestSettingsPanelModern panel = panelRef.get();
            SettingsFieldRow allowedHostsRow = findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_ALLOWED_HOSTS)
            );
            assertNotNull(allowedHostsRow);
            assertFalse(allowedHostsRow.isEnabled());
            assertFalse(allowedHostsRow.label().isEnabled());
            assertFalse(findInputComponent(allowedHostsRow).isEnabled());

            JCheckBox remoteLoadingCheckBox = findCheckBox(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_REQUEST_SCRIPT_REMOTE_REQUIRE_ENABLED)
            );
            assertNotNull(remoteLoadingCheckBox);

            SwingUtilities.invokeAndWait(() -> remoteLoadingCheckBox.setSelected(true));

            assertTrue(allowedHostsRow.isEnabled());
            assertTrue(allowedHostsRow.label().isEnabled());
            assertTrue(findInputComponent(allowedHostsRow).isEnabled());
        } finally {
            SettingManager.setRemoteJsRequireEnabled(oldRemoteEnabled);
        }
    }

    @Test
    public void requestTabsMultiLineToggleShouldNotLiveWithRequestEditorInnerTabs() throws Exception {
        AtomicReference<RequestSettingsPanelModern> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RequestSettingsPanelModern panel = new RequestSettingsPanelModern();
            panel.getPreferredSize();
            panelRef.set(panel);
        });

        assertNull(findCheckBox(
                panelRef.get(),
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_REQUEST_TABS_MULTILINE)
        ));
    }

    @Test
    public void allFieldRowsShouldShareOneLabelColumn() throws Exception {
        AtomicReference<RequestSettingsPanelModern> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            RequestSettingsPanelModern panel = new RequestSettingsPanelModern();
            panel.getPreferredSize();
            panelRef.set(panel);
        });

        UISettingsPanelModernTest.assertSharedCompactLabelColumn(panelRef.get());
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

    private static JComponent findInputComponent(SettingsFieldRow row) {
        for (Component component : row.getComponents()) {
            if (component instanceof JTextField input) {
                return input;
            }
        }
        throw new IllegalStateException("No input component found in settings field row");
    }
}
