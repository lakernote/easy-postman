package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.request.model.AuthApiKeyPlacement;
import com.laker.postman.request.model.AuthType;

import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;

import static org.testng.Assert.assertEquals;

public class AuthTabPanelTest extends AbstractSwingUiTest {

    @Test
    public void shouldUseCurrentVisibleCredentialsWhenSwitchingFromBasicToDigest() throws Exception {
        String[] username = new String[1];
        String[] password = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            AuthTabPanel panel = new AuthTabPanel();
            panel.setAuthType(AuthType.BASIC.getConstant());
            panel.setUsername("loaded-user");
            panel.setPassword("loaded-pass");

            textField(panel, "usernameField").setText("edited-user");
            textField(panel, "passwordField").setText("edited-pass");

            panel.setAuthType(AuthType.DIGEST.getConstant());

            username[0] = panel.getUsername();
            password[0] = panel.getPassword();
        });

        assertEquals(username[0], "edited-user");
        assertEquals(password[0], "edited-pass");
    }

    @Test
    public void shouldUseCurrentVisibleCredentialsWhenSwitchingFromDigestToBasic() throws Exception {
        String[] username = new String[1];
        String[] password = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            AuthTabPanel panel = new AuthTabPanel();
            panel.setAuthType(AuthType.DIGEST.getConstant());
            panel.setUsername("loaded-user");
            panel.setPassword("loaded-pass");

            textField(panel, "digestUsernameField").setText("edited-user");
            textField(panel, "digestPasswordField").setText("edited-pass");

            panel.setAuthType(AuthType.BASIC.getConstant());

            username[0] = panel.getUsername();
            password[0] = panel.getPassword();
        });

        assertEquals(username[0], "edited-user");
        assertEquals(password[0], "edited-pass");
    }

    @Test
    public void shouldRoundTripApiKeyFields() throws Exception {
        String[] key = new String[1];
        String[] value = new String[1];
        String[] placement = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            AuthTabPanel panel = new AuthTabPanel();
            panel.setAuthType(AuthType.API_KEY.getConstant());
            panel.setApiKeyName("X-API-Key");
            panel.setApiKeyValue("secret");
            panel.setApiKeyPlacement(AuthApiKeyPlacement.QUERY_PARAMS.getConstant());

            key[0] = panel.getApiKeyName();
            value[0] = panel.getApiKeyValue();
            placement[0] = panel.getApiKeyPlacement();
        });

        assertEquals(key[0], "X-API-Key");
        assertEquals(value[0], "secret");
        assertEquals(placement[0], AuthApiKeyPlacement.QUERY_PARAMS.getConstant());
    }

    private static JTextField textField(AuthTabPanel panel, String fieldName) {
        try {
            Field field = AuthTabPanel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (JTextField) field.get(panel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void apiKeyFieldsShouldKeepUsableWidthWhenPanelIsVerticallyCramped() throws Exception {
        int[] widths = new int[2];

        SwingUtilities.invokeAndWait(() -> {
            AuthTabPanel panel = new AuthTabPanel();
            panel.setAuthType(AuthType.API_KEY.getConstant());
            panel.setSize(700, 230);
            layoutRecursively(panel);

            widths[0] = textField(panel, "apiKeyNameField").getWidth();
            widths[1] = textField(panel, "apiKeyValueField").getWidth();
        });

        assertEquals(widths[0], 250);
        assertEquals(widths[1], 250);
    }

    private static void layoutRecursively(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layoutRecursively(nested);
            }
        }
    }
}
