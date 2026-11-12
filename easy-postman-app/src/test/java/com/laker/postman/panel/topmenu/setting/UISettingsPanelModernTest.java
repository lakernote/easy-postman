package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.common.component.setting.SettingsFieldRow;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.testng.annotations.Test;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class UISettingsPanelModernTest extends AbstractSwingUiTest {

    @Test
    public void downloadThresholdRowShouldUseCompactWidthAndFollowToggleState() throws Exception {
        boolean oldShowProgress = SettingManager.isShowDownloadProgressDialog();

        try {
            SettingManager.setShowDownloadProgressDialog(false);
            AtomicReference<UISettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                UISettingsPanelModern panel = new UISettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            UISettingsPanelModern panel = panelRef.get();
            SettingsFieldRow thresholdRow = findFieldRow(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_DOWNLOAD_THRESHOLD)
            );
            assertNotNull(thresholdRow);
            assertFalse(thresholdRow.isEnabled());
            assertFalse(thresholdRow.label().isEnabled());
            assertFalse(thresholdRow.inputComponent().isEnabled());
            assertSharedCompactLabelColumn(panel);

            JCheckBox showProgressCheckBox = findCheckBox(
                    panel,
                    I18nUtil.getMessage(MessageKeys.SETTINGS_DOWNLOAD_SHOW_PROGRESS)
            );
            assertNotNull(showProgressCheckBox);
            SwingUtilities.invokeAndWait(() -> showProgressCheckBox.setSelected(true));

            assertTrue(thresholdRow.isEnabled());
            assertTrue(thresholdRow.label().isEnabled());
            assertTrue(thresholdRow.inputComponent().isEnabled());
        } finally {
            SettingManager.setShowDownloadProgressDialog(oldShowProgress);
        }
    }

    @Test
    public void sidebarTabsLabelShouldStayCompactAndTopAlignedWithListEditor() throws Exception {
        AtomicReference<UISettingsPanelModern> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            UISettingsPanelModern panel = new UISettingsPanelModern();
            panel.getPreferredSize();
            panelRef.set(panel);
        });

        JLabel label = findLabel(
                panelRef.get(),
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_TABS)
        );

        assertNotNull(label);
        assertEquals(label.getMaximumSize().height, 32);
        assertEquals(label.getAlignmentY(), Component.TOP_ALIGNMENT);
    }

    @Test
    public void requestTabsMultiLineToggleShouldLiveWithGeneralOpenRequestSettings() throws Exception {
        boolean oldMultiLine = SettingManager.isRequestEditorTabsMultiLineEnabled();

        try {
            SettingManager.setRequestEditorTabsMultiLineEnabled(true);
            AtomicReference<UISettingsPanelModern> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                UISettingsPanelModern panel = new UISettingsPanelModern();
                panel.getPreferredSize();
                panelRef.set(panel);
            });

            JCheckBox checkBox = findCheckBox(
                    panelRef.get(),
                    I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_REQUEST_TABS_MULTILINE)
            );

            assertNotNull(checkBox);
            assertTrue(checkBox.isSelected());
        } finally {
            SettingManager.setRequestEditorTabsMultiLineEnabled(oldMultiLine);
        }
    }

    static void assertSharedCompactLabelColumn(Container panel) {
        List<SettingsFieldRow> rows = new ArrayList<>();
        collectFieldRows(panel, rows);
        assertTrue(rows.size() > 1, "Expected multiple settings field rows");
        int labelWidth = rows.get(0).label().getPreferredSize().width;
        int longestText = 0;
        for (SettingsFieldRow row : rows) {
            assertEquals(row.label().getPreferredSize().width, labelWidth,
                    "All field rows on one settings page should share one label column: " + row.label().getText());
            longestText = Math.max(longestText,
                    row.label().getFontMetrics(row.label().getFont()).stringWidth(row.label().getText()));
        }
        assertTrue(labelWidth <= Math.min(SettingsFieldRow.DEFAULT_LABEL_WIDTH, longestText + 20),
                "Settings label column should stay close to the longest localized label text");
    }

    private static void collectFieldRows(Container container, List<SettingsFieldRow> rows) {
        for (Component component : container.getComponents()) {
            if (component instanceof SettingsFieldRow row) {
                rows.add(row);
            } else if (component instanceof Container child) {
                collectFieldRows(child, rows);
            }
        }
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
}
