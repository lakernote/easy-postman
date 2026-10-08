package com.laker.postman.http.runtime.observation;

import lombok.experimental.UtilityClass;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.Normalizer;

/** Renders socket endpoints without copying malformed proxy host input into diagnostics. */
@UtilityClass
public class SafeSocketAddressFormatter {
    private static final String INVALID_HOST = "<invalid-host>";

    public String hostPort(InetSocketAddress address) {
        if (address == null) {
            return INVALID_HOST;
        }
        return host(address.getHostString()) + ":" + address.getPort();
    }

    public String socketAddress(InetSocketAddress address) {
        if (address == null) {
            return INVALID_HOST;
        }
        String host = host(address.getHostString());
        InetAddress resolved = address.getAddress();
        if (resolved == null) {
            return host + ":" + address.getPort();
        }
        String ip = resolved.getHostAddress();
        if (resolved instanceof Inet6Address) {
            ip = "[" + ip + "]";
        }
        return host.equals(ip) ? host + ":" + address.getPort()
                : host + "/" + ip + ":" + address.getPort();
    }

    public String host(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            return INVALID_HOST;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        String unbracketed = normalized.startsWith("[") && normalized.endsWith("]")
                ? normalized.substring(1, normalized.length() - 1) : normalized;
        if (unbracketed.indexOf(':') >= 0) {
            return isSafeIpv6Literal(unbracketed) ? "[" + unbracketed + "]" : INVALID_HOST;
        }
        for (int i = 0; i < unbracketed.length(); i++) {
            char ch = unbracketed.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '.' && ch != '-' && ch != '_') {
                return INVALID_HOST;
            }
        }
        return unbracketed;
    }

    public boolean isSafeHost(String value) {
        return !INVALID_HOST.equals(host(value));
    }

    private boolean isSafeIpv6Literal(String value) {
        int zoneIndex = value.indexOf('%');
        String address = zoneIndex < 0 ? value : value.substring(0, zoneIndex);
        if (address.isEmpty()) {
            return false;
        }
        int colonCount = 0;
        for (int i = 0; i < address.length(); i++) {
            char ch = address.charAt(i);
            if (ch == ':') {
                colonCount++;
            }
            if (Character.digit(ch, 16) < 0 && ch != ':' && ch != '.') {
                return false;
            }
        }
        if (colonCount < 2) {
            return false;
        }
        if (zoneIndex >= 0) {
            String zone = value.substring(zoneIndex + 1);
            if (zone.isEmpty()) {
                return false;
            }
            for (int i = 0; i < zone.length(); i++) {
                char ch = zone.charAt(i);
                if (!Character.isLetterOrDigit(ch) && ch != '.' && ch != '_' && ch != '-') {
                    return false;
                }
            }
        }
        return true;
    }
}
