package com.laker.postman.common.component.tab;

import com.laker.postman.common.component.HttpRequestDisplayMetadata;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import lombok.experimental.UtilityClass;

@UtilityClass
class RequestTabDisplayMetadata {
    record Badge(String text, String colorHex) {
    }

    static Badge badgeFor(String method, RequestItemProtocolEnum protocol) {
        RequestItemProtocolEnum effectiveProtocol = protocol == null ? RequestItemProtocolEnum.HTTP : protocol;
        if (effectiveProtocol == RequestItemProtocolEnum.SAVED_RESPONSE) {
            return null;
        }
        if (effectiveProtocol.isWebSocketProtocol()) {
            return new Badge("WS", HttpRequestDisplayMetadata.protocolColorHex("WS"));
        }
        String effectiveMethod = normalizeMethod(method);
        if (effectiveMethod == null) {
            return null;
        }
        String label = HttpRequestDisplayMetadata.methodLabel(effectiveMethod);
        if (label.isEmpty()) {
            return null;
        }
        return new Badge(label, HttpRequestDisplayMetadata.methodColorHex(effectiveMethod));
    }

    static String labelText(Badge badge, String title) {
        if (badge == null) {
            return title == null ? "" : title;
        }
        String safeTitle = escapeHtml(title == null ? "" : title);
        String safeBadge = escapeHtml(badge.text());
        String safeColor = badge.colorHex() == null ? "" : badge.colorHex();
        return "<html><nobr>"
                + "<span style='color:" + safeColor + ";font-weight:600'>" + safeBadge + "</span> "
                + "<span>" + safeTitle + "</span>"
                + "</nobr></html>";
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            return null;
        }
        return method.trim();
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.max(16, value.length()));
        for (char c : value.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
