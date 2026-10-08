package com.laker.postman.http.runtime.observation;

import org.testng.annotations.Test;

import java.net.InetSocketAddress;

import static org.testng.Assert.assertEquals;

public class SafeSocketAddressFormatterTest {

    @Test
    public void shouldDisplayIpv6HostWithBrackets() {
        assertEquals(SafeSocketAddressFormatter.hostPort(
                InetSocketAddress.createUnresolved("::1", 8080)), "[::1]:8080");
    }

    @Test
    public void shouldHideUserInfoPastedIntoProxyHost() {
        InetSocketAddress address = InetSocketAddress.createUnresolved("user:password@proxy.example", 8080);

        assertEquals(SafeSocketAddressFormatter.hostPort(address), "<invalid-host>:8080");
        assertEquals(SafeSocketAddressFormatter.socketAddress(address), "<invalid-host>:8080");
        assertEquals(SafeSocketAddressFormatter.hostPort(
                InetSocketAddress.createUnresolved("http://user:password@proxy.example", 8080)),
                "<invalid-host>:8080");
    }

    @Test
    public void shouldDisplayDecomposedInternationalHostInNormalizedForm() {
        assertEquals(SafeSocketAddressFormatter.host("u\u0308.example"), "\u00fc.example");
    }
}
