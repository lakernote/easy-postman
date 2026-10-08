package com.laker.postman.http.runtime.error;

import org.testng.annotations.Test;

import java.net.UnknownHostException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class NetworkErrorMessageResolverTest {

    @Test
    public void shouldTranslateMalformedSocksReplyIntoActionableHint() {
        String message = NetworkErrorMessageResolver.toUserFriendlyMessage("Malformed reply from SOCKS server");

        assertTrue(message.contains("SOCKS"));
        assertTrue(message.contains("HTTP/SOCKS"));
        assertTrue(message.contains("Malformed reply from SOCKS server"));
    }

    @Test
    public void shouldTranslateBadVersionSocksReplyIntoActionableHint() {
        String rawMessage = "Reply from SOCKS server has bad version";

        String message = NetworkErrorMessageResolver.toUserFriendlyMessage(rawMessage);

        assertTrue(NetworkErrorMessageResolver.isSocksProtocolMismatch(rawMessage));
        assertTrue(message.contains("HTTP/SOCKS"));
        assertTrue(message.contains(rawMessage));
    }

    @Test
    public void shouldKeepUnknownMessagesUnchanged() {
        String rawMessage = "Connection reset by peer";

        assertEquals(NetworkErrorMessageResolver.toUserFriendlyMessage(rawMessage), rawMessage);
    }

    @Test
    public void shouldReplaceGarbledUnknownHostMessageWithLocalizedHostHint() {
        UnknownHostException ex = new UnknownHostException("涓嶇煡閬撹繖鏍风殑涓绘満銆� (oktadomainq)");

        String message = NetworkErrorMessageResolver.toUserFriendlyMessage(ex);

        assertTrue(message.contains("oktadomainq"));
        assertTrue(message.contains("baseUrl"));
        assertFalse(message.contains("涓嶇煡"));
    }
}
