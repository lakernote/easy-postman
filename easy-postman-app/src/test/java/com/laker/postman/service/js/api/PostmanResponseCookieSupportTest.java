package com.laker.postman.service.js.api;

import com.laker.postman.http.runtime.model.HttpResponse;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class PostmanResponseCookieSupportTest {

    @Test
    public void shouldCollectEveryCaseInsensitiveSetCookieHeader() {
        HttpResponse response = new HttpResponse();
        response.headers = new LinkedHashMap<>();
        response.headers.put("Set-Cookie", List.of("session=abc; Path=/; HttpOnly"));
        response.headers.put("set-cookie", List.of("theme=dark; Secure; SameSite=Lax"));

        List<Cookie> cookies = PostmanResponseCookieSupport.extract(response);

        assertEquals(cookies.size(), 2);
        assertEquals(cookies.get(0).name, "session");
        assertEquals(cookies.get(0).path, "/");
        assertTrue(cookies.get(0).httpOnly);
        assertEquals(cookies.get(1).name, "theme");
        assertTrue(cookies.get(1).secure);
        assertEquals(cookies.get(1).sameSite, "Lax");
    }

    @Test
    public void shouldParseAttributesWithoutDependingOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Cookie cookie = PostmanResponseCookieSupport.parse(
                    "token=value; DOMAIN=.example.com; MAX-AGE=60; secure; httponly"
            );

            assertEquals(cookie.domain, ".example.com");
            assertEquals(cookie.maxAge, Integer.valueOf(60));
            assertTrue(cookie.secure);
            assertTrue(cookie.httpOnly);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void shouldIgnoreInvalidMaxAgeAndMissingResponses() {
        Cookie cookie = PostmanResponseCookieSupport.parse("token=value; Max-Age=invalid");

        assertNull(cookie.maxAge);
        assertNull(PostmanResponseCookieSupport.find(null, "token"));
        assertFalse(PostmanResponseCookieSupport.extract(null).iterator().hasNext());
    }
}
