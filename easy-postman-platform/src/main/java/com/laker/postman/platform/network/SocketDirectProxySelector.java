package com.laker.postman.platform.network;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * JVM-wide default {@link ProxySelector} guard installed right after
 * {@code java.net.useSystemProxies=true}.
 *
 * <ul>
 *     <li>{@code socket://} lookups always return DIRECT. A plain {@code new Socket()} asks the
 *     default selector for {@code socket://host:port}; on Windows the JDK maps a system proxy
 *     entry without a protocol prefix (for example {@code 127.0.0.1:7890}) to SOCKS for that
 *     lookup and does not honor wildcard bypass rules such as {@code 10.*}. That silently tunnels
 *     Redis, single-instance IPC, and other raw TCP connections through the system proxy. An explicit
 *     {@code -DsocksProxyHost} is still honored.</li>
 *     <li>Loopback IP literals and {@code localhost} are always DIRECT, matching WinINet's implicit
 *     loopback bypass. The JDK Windows selector only prefix-matches bypass entries literally, so
 *     {@code 127.*} never matches.</li>
 *     <li>Everything else is delegated unchanged, so HTTP(S) system proxy detection keeps working
 *     for update checks, plugin downloads, Git over HTTP, and the in-app system proxy mode.</li>
 * </ul>
 */
@Slf4j
public final class SocketDirectProxySelector extends ProxySelector {
    private static final List<Proxy> DIRECT = List.of(Proxy.NO_PROXY);

    private final ProxySelector delegate;

    SocketDirectProxySelector(ProxySelector delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps the current default selector. Idempotent; must run before any HTTP client captures
     * {@link ProxySelector#getDefault()}.
     */
    public static synchronized void install() {
        ProxySelector current = ProxySelector.getDefault();
        if (current instanceof SocketDirectProxySelector) {
            return;
        }
        ProxySelector.setDefault(new SocketDirectProxySelector(current));
        log.debug("Installed socket-direct JVM proxy selector over {}", current);
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI can't be null.");
        }
        if (isLoopbackHost(hostOf(uri)) || delegate == null) {
            return DIRECT;
        }
        if ("socket".equalsIgnoreCase(uri.getScheme()) && !isJvmSocksProxyConfigured()) {
            return DIRECT;
        }
        return delegate.select(uri);
    }

    /** An explicit {@code -DsocksProxyHost} is deliberate user configuration, unlike the OS system proxy. */
    private static boolean isJvmSocksProxyConfigured() {
        String host = System.getProperty("socksProxyHost");
        return host != null && !host.isBlank();
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        if (delegate != null) {
            delegate.connectFailed(uri, sa, ioe);
        }
    }

    /**
     * {@link URI#getHost()} is null for registry-based authorities such as {@code 127.0.0.1.:80};
     * like the JDK default selector, fall back to extracting the host from the authority.
     */
    static String hostOf(URI uri) {
        String host = uri.getHost();
        if (host != null) {
            return host;
        }
        String authority = uri.getRawAuthority();
        if (authority == null) {
            return null;
        }
        authority = authority.substring(authority.lastIndexOf('@') + 1);
        if (authority.startsWith("[")) {
            int end = authority.indexOf(']');
            return end > 0 ? authority.substring(0, end + 1) : null;
        }
        int colon = authority.lastIndexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }

    /**
     * Checks only names that need no DNS lookup: {@code localhost} and IP literals.
     */
    static boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.endsWith(".")) {
            // Fully qualified form: "localhost." and "127.0.0.1." name the same host.
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            return true;
        }
        long ipv4 = parseNumericIpv4(normalized);
        if (ipv4 >= 0) {
            return (ipv4 >>> 24) == 127;
        }
        if (normalized.indexOf(':') < 0) {
            return false;
        }
        try {
            // A string containing ':' is parsed as an IPv6 literal without a DNS lookup.
            return InetAddress.getByName(normalized).isLoopbackAddress();
        } catch (UnknownHostException | SecurityException e) {
            return false;
        }
    }

    /**
     * Parses the numeric IPv4 forms accepted by {@link InetAddress} without a DNS lookup, following
     * the JDK {@code IPAddressUtil.textToNumericFormatV4} rules: 1-15 characters, decimal parts,
     * {@code a.b.c.d}, {@code a.b.c} (16-bit c), {@code a.b} (24-bit b) and {@code a} (32-bit).
     * So {@code 127.1}, {@code 2130706433} and {@code 0002130706433} are 127.0.0.1.
     *
     * @return the address as an unsigned 32-bit value, or {@code -1} if not a numeric IPv4 literal
     */
    static long parseNumericIpv4(String host) {
        int length = host.length();
        if (length == 0 || length > 15) {
            return -1;
        }
        long result = 0;
        long part = 0;
        int completedParts = 0;
        boolean newPart = true;
        for (int i = 0; i < length; i++) {
            char c = host.charAt(i);
            if (c == '.') {
                if (newPart || part > 0xff || completedParts == 3) {
                    return -1;
                }
                result = (result << 8) | part;
                completedParts++;
                part = 0;
                newPart = true;
            } else if (c >= '0' && c <= '9') {
                part = part * 10 + (c - '0');
                newPart = false;
            } else {
                return -1;
            }
        }
        int lastPartBits = (4 - completedParts) * 8;
        if (newPart || part >= (1L << lastPartBits)) {
            return -1;
        }
        return (result << lastPartBits) | part;
    }
}
