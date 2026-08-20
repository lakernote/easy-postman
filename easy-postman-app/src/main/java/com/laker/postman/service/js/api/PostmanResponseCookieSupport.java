package com.laker.postman.service.js.api;

import com.laker.postman.http.runtime.model.HttpResponse;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapts HTTP {@code Set-Cookie} response headers to the cookie shape exposed by Postman scripts.
 */
@UtilityClass
class PostmanResponseCookieSupport {

    static List<Cookie> extract(HttpResponse response) {
        if (response == null || response.headers == null || response.headers.isEmpty()) {
            return List.of();
        }

        List<Cookie> cookies = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : response.headers.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().equalsIgnoreCase("Set-Cookie")
                    || entry.getValue() == null) {
                continue;
            }
            for (String headerValue : entry.getValue()) {
                Cookie cookie = parse(headerValue);
                if (cookie != null && cookie.name != null) {
                    cookies.add(cookie);
                }
            }
        }
        return cookies;
    }

    static Cookie find(HttpResponse response, String name) {
        if (name == null) {
            return null;
        }
        return extract(response).stream()
                .filter(cookie -> name.equals(cookie.name))
                .findFirst()
                .orElse(null);
    }

    static Cookie parse(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return null;
        }

        Cookie cookie = new Cookie();
        String[] parts = headerValue.split(";");
        String[] nameValue = parts[0].trim().split("=", 2);
        cookie.name = nameValue[0].trim();
        cookie.value = nameValue.length == 2 ? nameValue[1].trim() : "";

        for (int index = 1; index < parts.length; index++) {
            String[] attribute = parts[index].trim().split("=", 2);
            String attributeName = attribute[0].trim().toLowerCase(Locale.ROOT);
            String attributeValue = attribute.length == 2 ? attribute[1].trim() : null;
            switch (attributeName) {
                case "domain" -> cookie.domain = attributeValue;
                case "path" -> cookie.path = attributeValue;
                case "expires" -> cookie.expires = attributeValue;
                case "max-age" -> cookie.maxAge = parseMaxAge(attributeValue);
                case "secure" -> cookie.secure = true;
                case "httponly" -> cookie.httpOnly = true;
                case "samesite" -> cookie.sameSite = attributeValue;
                default -> {
                    // Ignore extensions that are not represented by the script Cookie model.
                }
            }
        }
        return cookie;
    }

    private static Integer parseMaxAge(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
