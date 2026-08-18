package com.laker.postman.mock.app;

import com.laker.postman.mock.model.MockServerDefinition;
import lombok.experimental.UtilityClass;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

@UtilityClass
public class MockNetworkAddressResolver {

    public String accessUrl(MockServerDefinition definition, int port) {
        String configuredHost = definition == null ? null : definition.getHost();
        String accessHost = MockServerDefinition.ALL_INTERFACES_HOST.equals(configuredHost)
                ? preferredLanAddress()
                : normalizeHost(configuredHost);
        return "http://" + accessHost + ":" + port;
    }

    static String preferredLanAddress() {
        try {
            List<NetworkInterface> interfaces = NetworkInterface.networkInterfaces()
                    .filter(MockNetworkAddressResolver::isUsable)
                    .sorted(Comparator.comparingInt(MockNetworkAddressResolver::interfacePriority))
                    .toList();
            for (NetworkInterface networkInterface : interfaces) {
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
            // Fall back to loopback when network interfaces can't be inspected.
        }
        return MockServerDefinition.LOOPBACK_HOST;
    }

    private boolean isUsable(NetworkInterface networkInterface) {
        try {
            return networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isVirtual();
        } catch (SocketException ignored) {
            return false;
        }
    }

    private int interfacePriority(NetworkInterface networkInterface) {
        String name = networkInterface.getName().toLowerCase();
        if (name.startsWith("en") || name.startsWith("eth")) return 0;
        if (name.startsWith("wl") || name.startsWith("wi")) return 1;
        return 2;
    }

    private String normalizeHost(String host) {
        return host == null || host.isBlank() ? MockServerDefinition.LOOPBACK_HOST : host.trim();
    }
}
