package com.aleph.graymatter.jtyposquatting.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the outbound network policy (anti-SSRF).
 */
class OutboundPolicyTest {

    private final OutboundPolicy policy = new OutboundPolicy(false, Set.of(80, 443));

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "127.1.2.3", "0.0.0.0", "10.0.0.1", "172.16.5.4", "172.31.255.255",
            "192.168.1.1", "169.254.169.254", "100.64.0.1", "192.0.2.10", "198.18.0.1",
            "224.0.0.1", "255.255.255.255",
            "::1", "::", "fe80::1", "fd00::1", "fc00::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1",
            "64:ff9b::7f00:1", "2002:7f00:1::1", "2001:db8::1", "ff02::1"
    })
    @DisplayName("Internal, reserved or special addresses are refused")
    void testBlockedAddresses(String address) throws Exception {
        assertFalse(policy.isAllowed(InetAddress.getByName(address)), address + " should be refused");
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "93.184.216.34", "172.32.0.1", "2606:4700:4700::1111", "2a00:1450:4007::64"})
    @DisplayName("Public addresses are allowed")
    void testPublicAddresses(String address) throws Exception {
        assertTrue(policy.isAllowed(InetAddress.getByName(address)), address + " should be allowed");
    }

    @Test
    @DisplayName("Resolving a name that points to an internal address fails")
    void testResolveBlocksLocalhost() {
        assertThrows(BlockedDestinationException.class, () -> policy.resolve("localhost"));
        assertThrows(BlockedDestinationException.class, () -> policy.resolve("169.254.169.254"));
    }

    @Test
    @DisplayName("Only http/https and the allowed ports are accepted")
    void testCheckUrl() throws Exception {
        policy.checkUrl(URI.create("https://example.com/"));
        policy.checkUrl(URI.create("http://example.com:80/"));
        assertThrows(BlockedDestinationException.class, () -> policy.checkUrl(URI.create("http://example.com:8080/")));
        assertThrows(BlockedDestinationException.class, () -> policy.checkUrl(URI.create("file:///etc/passwd")));
        assertThrows(BlockedDestinationException.class, () -> policy.checkUrl(URI.create("gopher://example.com/")));
    }

    @Test
    @DisplayName("La politique permissive (tests) autorise tout")
    void testPermissive() throws Exception {
        OutboundPolicy permissive = OutboundPolicy.permissive();
        assertTrue(permissive.isAllowed(InetAddress.getByName("127.0.0.1")));
        permissive.checkUrl(URI.create("http://localhost:12345/"));
    }
}
