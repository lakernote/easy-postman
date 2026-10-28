package com.laker.postman.common.component.tab;

import com.laker.postman.common.constants.ThemeColors;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.Map;

import static com.laker.postman.test.ThemeTokenTestSupport.remember;
import static com.laker.postman.test.ThemeTokenTestSupport.restore;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class RequestTabDisplayMetadataTest {
    private static final String[] THEME_KEYS = {
            ThemeColors.HTTP_METHOD_POST,
            ThemeColors.HTTP_METHOD_PATCH,
            ThemeColors.HTTP_METHOD_DEFAULT,
            ThemeColors.HTTP_PROTOCOL_WS,
            ThemeColors.HTTP_PROTOCOL_SSE
    };

    private Map<String, Object> previousThemeTokens;

    @BeforeMethod
    public void rememberThemeTokens() {
        previousThemeTokens = remember(THEME_KEYS);
    }

    @AfterMethod
    public void restoreThemeTokens() {
        restore(previousThemeTokens);
    }

    @Test
    public void shouldUseRequestMethodBadgeForHttpTabs() {
        UIManager.put(ThemeColors.HTTP_METHOD_POST, new Color(1, 2, 3));

        RequestTabDisplayMetadata.Badge badge =
                RequestTabDisplayMetadata.badgeFor("POST", RequestItemProtocolEnum.HTTP);
        String html = RequestTabDisplayMetadata.labelText(badge, "Post<Test");

        assertEquals(badge.text(), "POST");
        assertEquals(badge.colorHex(), "#010203");
        assertTrue(html.contains(">POST</span>"));
        assertTrue(html.contains(">Post&lt;Test</span>"));
    }

    @Test
    public void shouldUseCompactMethodLabelLikeCollectionTree() {
        UIManager.put(ThemeColors.HTTP_METHOD_PATCH, new Color(4, 5, 6));

        RequestTabDisplayMetadata.Badge badge =
                RequestTabDisplayMetadata.badgeFor("PATCH", RequestItemProtocolEnum.HTTP);

        assertEquals(badge.text(), "PAT");
        assertEquals(badge.colorHex(), "#040506");
    }

    @Test
    public void shouldKeepWebSocketBadgeAndUseHttpMethodForSseResponses() {
        UIManager.put(ThemeColors.HTTP_PROTOCOL_WS, new Color(7, 8, 9));
        RequestTabDisplayMetadata.Badge websocket =
                RequestTabDisplayMetadata.badgeFor("GET", RequestItemProtocolEnum.WEBSOCKET);
        RequestTabDisplayMetadata.Badge eventStream =
                RequestTabDisplayMetadata.badgeFor("GET", RequestItemProtocolEnum.HTTP);

        assertEquals(websocket.text(), "WS");
        assertEquals(websocket.colorHex(), "#070809");
        assertEquals(eventStream.text(), "GET");
    }

    @Test
    public void shouldNotInventMethodBadgeWhenHttpMethodIsMissing() {
        assertNull(RequestTabDisplayMetadata.badgeFor(null, RequestItemProtocolEnum.HTTP));
        assertNull(RequestTabDisplayMetadata.badgeFor("   ", RequestItemProtocolEnum.HTTP));
    }

    @Test
    public void shouldUseHttpMethodBadgeWhenRequestProtocolIsMissing() {
        UIManager.put(ThemeColors.HTTP_METHOD_DEFAULT, new Color(13, 14, 15));

        RequestTabDisplayMetadata.Badge badge = RequestTabDisplayMetadata.badgeFor("CUSTOM", null);

        assertEquals(badge.text(), "CUSTOM");
        assertEquals(badge.colorHex(), "#0d0e0f");
    }

    @Test
    public void shouldNotCreateBadgeForSavedResponseTabs() {
        assertNull(RequestTabDisplayMetadata.badgeFor("GET", RequestItemProtocolEnum.SAVED_RESPONSE));
    }
}
