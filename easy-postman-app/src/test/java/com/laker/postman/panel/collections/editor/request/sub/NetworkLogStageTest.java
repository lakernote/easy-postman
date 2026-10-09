package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.constants.ThemeColors;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class NetworkLogStageTest {

    private static final List<String> THEME_TOKEN_KEYS = List.of(
            ThemeColors.ERROR,
            ThemeColors.SUCCESS,
            ThemeColors.PRIMARY,
            ThemeColors.INFO,
            ThemeColors.WARNING,
            ThemeColors.TEXT_PRIMARY,
            ThemeColors.TEXT_SECONDARY
    );

    private Map<String, Object> previousThemeTokens;

    @BeforeMethod
    public void rememberThemeTokens() {
        previousThemeTokens = new HashMap<>();
        for (String key : THEME_TOKEN_KEYS) {
            previousThemeTokens.put(key, UIManager.get(key));
        }
    }

    @AfterMethod
    public void restoreThemeTokens() {
        for (Map.Entry<String, Object> entry : previousThemeTokens.entrySet()) {
            UIManager.put(entry.getKey(), entry.getValue());
        }
    }

    @Test
    public void shouldMapEveryHttpNetworkLogEventStageToUiStage() {
        for (NetworkLogEventStage eventStage : NetworkLogEventStage.values()) {
            assertEquals(NetworkLogStage.fromEventStage(eventStage).name(), eventStage.name(),
                    "Every service-layer network log stage should have a UI rendering stage");
        }
    }

    @Test
    public void shouldUseDefaultUiStageWhenEventStageIsNull() {
        assertEquals(NetworkLogStage.fromEventStage(null), NetworkLogStage.DEFAULT);
    }

    @Test
    public void shouldUseReadableDiagnosticStageNames() {
        assertEquals(NetworkLogStage.CALL_START.getStageName(), "RequestStart");
        assertEquals(NetworkLogStage.CALL_END.getStageName(), "RequestEnd");
        assertEquals(NetworkLogStage.REQUEST_COMPLETE.getStageName(), "RequestComplete");
        assertEquals(NetworkLogStage.DNS_START.getStageName(), "DNSStart");
        assertEquals(NetworkLogStage.DNS_END.getStageName(), "DNSEnd");
        assertEquals(NetworkLogStage.SECURE_CONNECT_START.getStageName(), "TLSHandshakeStart");
        assertEquals(NetworkLogStage.SECURE_CONNECT_END.getStageName(), "TLSHandshakeEnd");
        assertEquals(NetworkLogStage.CONNECTION_ACQUIRED.getStageName(), "ConnectionReady");
    }

    @Test
    public void shouldReadStageColorsFromThemeTokens() {
        Color error = new Color(20, 40, 60);
        Color success = new Color(1, 2, 3);
        Color primary = new Color(220, 160, 100);
        Color info = new Color(10, 11, 12);
        Color warning = new Color(13, 14, 15);
        Color text = new Color(16, 17, 18);
        Color secondaryText = new Color(19, 20, 21);
        UIManager.put(ThemeColors.ERROR, error);
        UIManager.put(ThemeColors.SUCCESS, success);
        UIManager.put(ThemeColors.PRIMARY, primary);
        UIManager.put(ThemeColors.INFO, info);
        UIManager.put(ThemeColors.WARNING, warning);
        UIManager.put(ThemeColors.TEXT_PRIMARY, text);
        UIManager.put(ThemeColors.TEXT_SECONDARY, secondaryText);

        assertEquals(NetworkLogStage.FAILED.getColor(), error);
        assertEquals(NetworkLogStage.CALL_END.getColor(), secondaryText);
        assertEquals(NetworkLogStage.REQUEST_COMPLETE.getColor(), secondaryText);
        assertEquals(NetworkLogStage.CONNECT_START.getColor(), secondaryText);
        assertEquals(NetworkLogStage.SECURE_CONNECT_START.getColor(), secondaryText);
        assertEquals(NetworkLogStage.REQUEST_HEADERS_START.getColor(), secondaryText);
        assertEquals(NetworkLogStage.RESPONSE_BODY_END.getColor(), secondaryText);
        assertEquals(NetworkLogStage.REDIRECT.getColor(), primary);
        assertEquals(NetworkLogStage.DEFAULT.getColor(), text);
    }
}
