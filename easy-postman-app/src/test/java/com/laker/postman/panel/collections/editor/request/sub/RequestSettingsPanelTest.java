package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.request.edit.HttpRequestEditorContentSummary;
import com.laker.postman.request.edit.HttpRequestEditorDraft;
import com.laker.postman.request.edit.HttpRequestSettingsDraft;
import com.laker.postman.request.model.HttpRequestProxyPolicy;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.Objects;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class RequestSettingsPanelTest extends AbstractSwingUiTest {

    @Test
    public void shouldRejectOversizedTimeoutValue() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel();
        setRequestTimeoutText(panel, "999999999999");

        assertNotNull(panel.validateSettings());
    }

    @Test
    public void shouldNotThrowWhenApplyingOversizedTimeoutValue() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel();
        setRequestTimeoutText(panel, "999999999999");

        assertNull(panel.collectSettings().getRequestTimeoutMs());
    }

    @Test
    public void shouldPreserveInheritedSettingsWhenGlobalDefaultsChange() {
        boolean oldFollowRedirects = SettingManager.isFollowRedirects();

        try {
            SettingManager.setFollowRedirects(true);

            RequestSettingsPanel panel = new RequestSettingsPanel();
            panel.populate(HttpRequestSettingsDraft.builder()
                    .followRedirects(null)
                    .proxyPolicy(HttpRequestProxyPolicy.DEFAULT)
                    .build());

            SettingManager.setFollowRedirects(false);

            HttpRequestSettingsDraft saved = panel.collectSettings();

            assertNull(saved.getFollowRedirects());
            assertEquals(saved.getProxyPolicy(), HttpRequestProxyPolicy.DEFAULT);
            assertFalse(hasSettingsContent(saved));
        } finally {
            SettingManager.setFollowRedirects(oldFollowRedirects);
        }
    }

    @Test
    public void shouldAllowExplicitFollowRedirectsOverrideToReturnToDefault() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel();
        panel.populate(HttpRequestSettingsDraft.builder().followRedirects(Boolean.FALSE).build());
        selectBooleanSetting(panel, "followRedirectsComboBox", null);

        HttpRequestSettingsDraft saved = panel.collectSettings();

        assertNull(saved.getFollowRedirects());
        assertFalse(hasSettingsContent(saved));
    }

    @Test
    public void shouldAllowInheritedFollowRedirectsToBeSavedAsExplicitValue() throws Exception {
        boolean oldFollowRedirects = SettingManager.isFollowRedirects();

        try {
            SettingManager.setFollowRedirects(true);

            RequestSettingsPanel panel = new RequestSettingsPanel();
            panel.populate(HttpRequestSettingsDraft.builder().followRedirects(null).build());
            selectBooleanSetting(panel, "followRedirectsComboBox", Boolean.TRUE);

            HttpRequestSettingsDraft saved = panel.collectSettings();

            assertTrue(saved.getFollowRedirects());
            assertTrue(hasSettingsContent(saved));
        } finally {
            SettingManager.setFollowRedirects(oldFollowRedirects);
        }
    }

    @Test
    public void shouldKeepExplicitOverrideWhenUserChangesInheritedValueAfterGlobalChange() throws Exception {
        boolean oldFollowRedirects = SettingManager.isFollowRedirects();

        try {
            SettingManager.setFollowRedirects(true);

            RequestSettingsPanel panel = new RequestSettingsPanel();
            panel.populate(HttpRequestSettingsDraft.builder().followRedirects(null).build());

            SettingManager.setFollowRedirects(false);
            selectBooleanSetting(panel, "followRedirectsComboBox", Boolean.FALSE);

            HttpRequestSettingsDraft saved = panel.collectSettings();

            assertFalse(saved.getFollowRedirects());
            assertTrue(hasSettingsContent(saved));
        } finally {
            SettingManager.setFollowRedirects(oldFollowRedirects);
        }
    }

    @Test
    public void shouldRebaselineFollowRedirectsAfterSave() throws Exception {
        boolean oldFollowRedirects = SettingManager.isFollowRedirects();

        try {
            SettingManager.setFollowRedirects(false);

            RequestSettingsPanel panel = new RequestSettingsPanel();
            panel.populate(HttpRequestSettingsDraft.builder().followRedirects(null).build());

            selectBooleanSetting(panel, "followRedirectsComboBox", Boolean.TRUE);
            panel.collectSettings();
            panel.rebaseline();

            selectBooleanSetting(panel, "followRedirectsComboBox", Boolean.FALSE);
            HttpRequestSettingsDraft savedAgain = panel.collectSettings();

            assertFalse(savedAgain.getFollowRedirects());
        } finally {
            SettingManager.setFollowRedirects(oldFollowRedirects);
        }
    }

    @Test
    public void shouldAllowRequestProxyPolicyOverride() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel();
        panel.populate(HttpRequestSettingsDraft.builder()
                .proxyPolicy(HttpRequestProxyPolicy.DEFAULT)
                .build());

        selectProxyPolicy(panel, HttpRequestProxyPolicy.NO_PROXY);

        HttpRequestSettingsDraft saved = panel.collectSettings();

        assertEquals(saved.getProxyPolicy(), HttpRequestProxyPolicy.NO_PROXY);
        assertTrue(hasSettingsContent(saved));
    }

    @Test
    public void shouldCollectPresetWebSocketPingInterval() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel(RequestItemProtocolEnum.WEBSOCKET);
        panel.populate(HttpRequestSettingsDraft.builder().webSocketPingIntervalMs(null).build());

        selectIntegerSetting(panel, "webSocketPingIntervalComboBox", 60000);

        HttpRequestSettingsDraft saved = panel.collectSettings();

        assertEquals(saved.getWebSocketPingIntervalMs(), Integer.valueOf(60000));
        assertTrue(hasSettingsContent(saved));
    }

    @Test
    public void shouldCollectCustomWebSocketPingInterval() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel(RequestItemProtocolEnum.WEBSOCKET);
        panel.populate(HttpRequestSettingsDraft.builder().webSocketPingIntervalMs(null).build());

        selectIntegerSetting(panel, "webSocketPingIntervalComboBox", -1);
        setTextField(panel, "webSocketPingIntervalCustomField", "45000");

        HttpRequestSettingsDraft saved = panel.collectSettings();

        assertEquals(saved.getWebSocketPingIntervalMs(), Integer.valueOf(45000));
        assertTrue(hasSettingsContent(saved));
    }

    @Test
    public void shouldUseStandardControlSlotForPresetWebSocketPingInterval() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel(RequestItemProtocolEnum.WEBSOCKET);

        JPanel proxyControlPanel = (JPanel) comboBox(panel, "proxyPolicyComboBox").getParent();
        JPanel pingControlPanel = (JPanel) comboBox(panel, "webSocketPingIntervalComboBox").getParent();
        JPanel customPanel = panelField(panel, "webSocketPingIntervalCustomPanel");

        assertTrue(proxyControlPanel.getPreferredSize().width <= 176);
        assertTrue(pingControlPanel.getPreferredSize().width <= 176);
        assertFalse(customPanel.isVisible());
    }

    @Test
    public void shouldKeepStandardControlSlotWhenCustomWebSocketPingIntervalIsSelected() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel(RequestItemProtocolEnum.WEBSOCKET);

        selectIntegerSetting(panel, "webSocketPingIntervalComboBox", -1);

        JPanel proxyControlPanel = (JPanel) comboBox(panel, "proxyPolicyComboBox").getParent();
        JPanel pingControlPanel = (JPanel) comboBox(panel, "webSocketPingIntervalComboBox").getParent();
        JPanel customPanel = panelField(panel, "webSocketPingIntervalCustomPanel");

        assertTrue(proxyControlPanel.getPreferredSize().width <= 176);
        assertTrue(pingControlPanel.getPreferredSize().width <= 176);
        assertTrue(customPanel.isVisible());
        assertEquals(customPanel.getComponentCount(), 2);
    }

    @Test
    public void shouldRejectTooSmallCustomWebSocketPingInterval() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel(RequestItemProtocolEnum.WEBSOCKET);
        selectIntegerSetting(panel, "webSocketPingIntervalComboBox", -1);
        setTextField(panel, "webSocketPingIntervalCustomField", "1000");

        assertNotNull(panel.validateSettings());
    }

    @Test
    public void settingControlsShouldShareTheSameRightAlignedSlot() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel(RequestItemProtocolEnum.HTTP);
        panel.setSize(720, 600);
        layoutRecursively(panel);

        java.awt.Rectangle redirect = boundsInPanel(panel, comboBox(panel, "followRedirectsComboBox"));
        java.awt.Rectangle proxy = boundsInPanel(panel, comboBox(panel, "proxyPolicyComboBox"));
        java.awt.Rectangle httpVersion = boundsInPanel(panel, comboBox(panel, "httpVersionComboBox"));
        java.lang.reflect.Field timeoutField = RequestSettingsPanel.class.getDeclaredField("requestTimeoutField");
        timeoutField.setAccessible(true);
        java.awt.Rectangle timeout = boundsInPanel(panel, (java.awt.Component) timeoutField.get(panel));

        assertEquals(proxy.x, redirect.x);
        assertEquals(httpVersion.x, redirect.x);
        assertEquals(timeout.x, redirect.x);
        assertEquals(proxy.width, redirect.width);
        assertEquals(httpVersion.width, redirect.width);
        assertEquals(timeout.width, redirect.width);
    }

    @Test
    public void settingControlsShouldNotStretchBeyondWidestOption() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel(RequestItemProtocolEnum.HTTP);
        panel.setSize(1400, 600);
        layoutRecursively(panel);

        int widestOption = Math.max(Math.max(
                        comboBox(panel, "followRedirectsComboBox").getPreferredSize().width,
                        comboBox(panel, "proxyPolicyComboBox").getPreferredSize().width),
                comboBox(panel, "httpVersionComboBox").getPreferredSize().width);

        assertEquals(comboBox(panel, "httpVersionComboBox").getWidth(), Math.max(120, Math.min(widestOption, 176)));
    }

    private static java.awt.Rectangle boundsInPanel(RequestSettingsPanel panel, java.awt.Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), panel);
    }

    private static void layoutRecursively(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layoutRecursively(nested);
            }
        }
    }

    @Test
    public void shouldTrackViewportWidthAfterResize() {
        RequestSettingsPanel panel = new RequestSettingsPanel(RequestItemProtocolEnum.WEBSOCKET);

        panel.setSize(720, 320);
        panel.doLayout();

        JViewport viewport = panel.getViewport();
        assertEquals(viewport.getViewSize().width, viewport.getExtentSize().width);
    }

    @Test
    public void shouldLetEasyComboBoxUseItsMaxOptionWidth() throws Exception {
        RequestSettingsPanel panel = new RequestSettingsPanel(RequestItemProtocolEnum.WEBSOCKET);

        JComboBox<?> proxyComboBox = comboBox(panel, "proxyPolicyComboBox");
        int fixedMaxPreferredWidth = proxyComboBox.getPreferredSize().width;

        for (int i = 0; i < proxyComboBox.getItemCount(); i++) {
            proxyComboBox.setSelectedIndex(i);
            assertEquals(proxyComboBox.getPreferredSize().width, fixedMaxPreferredWidth);
        }
    }

    private static void setRequestTimeoutText(RequestSettingsPanel panel, String value) throws Exception {
        setTextField(panel, "requestTimeoutField", value);
    }

    private static void setTextField(RequestSettingsPanel panel, String fieldName, String value) throws Exception {
        Field field = RequestSettingsPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        JTextField textField = (JTextField) field.get(panel);
        textField.setText(value);
    }

    private static void selectBooleanSetting(RequestSettingsPanel panel, String fieldName, Boolean value) throws Exception {
        Field field = RequestSettingsPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        JComboBox<?> comboBox = (JComboBox<?>) field.get(panel);
        ComboBoxModel<?> model = comboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            Object option = model.getElementAt(i);
            Field valueField = option.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            Object optionValue = valueField.get(option);
            if (Objects.equals(optionValue, value)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
        throw new IllegalArgumentException("No combo option found for value: " + value);
    }

    private static void selectIntegerSetting(RequestSettingsPanel panel, String fieldName, Integer value) throws Exception {
        JComboBox<?> comboBox = comboBox(panel, fieldName);
        ComboBoxModel<?> model = comboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            Object option = model.getElementAt(i);
            Field valueField = option.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            Object optionValue = valueField.get(option);
            if (Objects.equals(optionValue, value)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
        throw new IllegalArgumentException("No combo option found for value: " + value);
    }

    private static JComboBox<?> comboBox(RequestSettingsPanel panel, String fieldName) throws Exception {
        Field field = RequestSettingsPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JComboBox<?>) field.get(panel);
    }

    private static JPanel panelField(RequestSettingsPanel panel, String fieldName) throws Exception {
        Field field = RequestSettingsPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JPanel) field.get(panel);
    }

    private static void selectProxyPolicy(RequestSettingsPanel panel, HttpRequestProxyPolicy value) throws Exception {
        Field field = RequestSettingsPanel.class.getDeclaredField("proxyPolicyComboBox");
        field.setAccessible(true);
        JComboBox<?> comboBox = (JComboBox<?>) field.get(panel);
        ComboBoxModel<?> model = comboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            Object option = model.getElementAt(i);
            Field valueField = option.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            Object optionValue = valueField.get(option);
            if (Objects.equals(optionValue, value)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
        throw new IllegalArgumentException("No proxy policy option found for value: " + value);
    }

    private static boolean hasSettingsContent(HttpRequestSettingsDraft settings) {
        return HttpRequestEditorContentSummary.from(HttpRequestEditorDraft.builder()
                .followRedirects(settings.getFollowRedirects())
                .cookieJarEnabled(settings.getCookieJarEnabled())
                .proxyPolicy(settings.getProxyPolicy())
                .httpVersion(settings.getHttpVersion())
                .requestTimeoutMs(settings.getRequestTimeoutMs())
                .webSocketPingIntervalMs(settings.getWebSocketPingIntervalMs())
                .build()).isHasSettings();
    }

}
