package com.aleph.graymatter.jtyposquatting.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

/**
 * Decides which destinations the analysis engine may connect to (anti-SSRF).
 * <p>
 * Generated domains are attacker-controllable: anyone can register a typo domain pointing to
 * 127.0.0.1, 169.254.169.254 or an internal range. Every resolved address is checked before connecting.
 */
@Component
public class OutboundPolicy {

    private static final List<Cidr> BLOCKED_RANGES = List.of(
            // IPv4
            Cidr.of("0.0.0.0/8"),           // "this" network
            Cidr.of("10.0.0.0/8"),          // private
            Cidr.of("100.64.0.0/10"),       // carrier-grade NAT
            Cidr.of("127.0.0.0/8"),         // loopback
            Cidr.of("169.254.0.0/16"),      // link-local, cloud metadata
            Cidr.of("172.16.0.0/12"),       // private
            Cidr.of("192.0.0.0/24"),        // IETF protocol assignments
            Cidr.of("192.0.2.0/24"),        // documentation
            Cidr.of("192.88.99.0/24"),      // 6to4 relay anycast
            Cidr.of("192.168.0.0/16"),      // private
            Cidr.of("198.18.0.0/15"),       // benchmarking
            Cidr.of("198.51.100.0/24"),     // documentation
            Cidr.of("203.0.113.0/24"),      // documentation
            Cidr.of("224.0.0.0/4"),         // multicast
            Cidr.of("240.0.0.0/4"),         // reserved, broadcast
            // IPv6
            Cidr.of("::/128"),              // unspecified
            Cidr.of("::1/128"),             // loopback
            Cidr.of("64:ff9b::/96"),        // NAT64
            Cidr.of("64:ff9b:1::/48"),      // local-use NAT64
            Cidr.of("100::/64"),            // discard
            Cidr.of("2001::/32"),           // Teredo
            Cidr.of("2001:db8::/32"),       // documentation
            Cidr.of("2002::/16"),           // 6to4
            Cidr.of("fc00::/7"),            // unique local
            Cidr.of("fe80::/10"),           // link-local
            Cidr.of("fec0::/10"),           // site-local (deprecated)
            Cidr.of("ff00::/8")             // multicast
    );

    public static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (compatible; JTypoSquatting/2.0)";

    private final boolean allowPrivateAddresses;
    private final Set<Integer> allowedPorts;
    private final String userAgent;

    @Autowired
    public OutboundPolicy(@Value("${jtypo.outbound.allow-private-addresses:false}") boolean allowPrivateAddresses,
                          @Value("${jtypo.outbound.allowed-ports:80,443}") Set<Integer> allowedPorts,
                          @Value("${jtypo.outbound.user-agent:" + DEFAULT_USER_AGENT + "}") String userAgent) {
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.allowedPorts = Set.copyOf(allowedPorts);
        this.userAgent = userAgent;
    }

    public OutboundPolicy(boolean allowPrivateAddresses, Set<Integer> allowedPorts) {
        this(allowPrivateAddresses, allowedPorts, DEFAULT_USER_AGENT);
    }

    /** User-Agent sent by the HTTP client and the browser, so that analysed sites can identify the service. */
    public String getUserAgent() {
        return userAgent;
    }

    /** Policy for tests against local mock servers: any address, any port. */
    public static OutboundPolicy permissive() {
        return new OutboundPolicy(true, Set.of());
    }

    /**
     * Checks the scheme and port of a URL. The host is checked at resolution time by {@link GuardedDnsResolver}.
     */
    public void checkUrl(URI uri) throws BlockedDestinationException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BlockedDestinationException("Scheme not allowed: " + uri.getScheme());
        }
        if (uri.getHost() == null) {
            throw new BlockedDestinationException("URL without host: " + uri);
        }
        int port = uri.getPort() != -1 ? uri.getPort() : (scheme.equals("https") ? 443 : 80);
        if (!allowedPorts.isEmpty() && !allowedPorts.contains(port)) {
            throw new BlockedDestinationException("Port not allowed: " + port);
        }
    }

    /** Resolves a host name and fails if any of its addresses is not publicly routable. */
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (!isAllowed(address)) {
                throw new BlockedDestinationException("Destination not allowed: " + host + " resolves to " + address.getHostAddress());
            }
        }
        return addresses;
    }

    public boolean isAllowed(InetAddress address) {
        if (allowPrivateAddresses) {
            return true;
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        for (Cidr range : BLOCKED_RANGES) {
            if (range.contains(address)) {
                return false;
            }
        }
        return true;
    }

    private record Cidr(byte[] network, int prefixLength) {

        static Cidr of(String cidr) {
            String[] parts = cidr.split("/");
            try {
                return new Cidr(InetAddress.getByName(parts[0]).getAddress(), Integer.parseInt(parts[1]));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(cidr, e);
            }
        }

        boolean contains(InetAddress address) {
            byte[] bytes = address.getAddress();
            // Java parses IPv4-mapped "::ffff:a.b.c.d" as an Inet4Address, so IPv4 ranges cover them
            if (bytes.length != network.length) {
                return false;
            }
            int bits = bytes.length * 8;
            BigInteger mask = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)
                    .shiftRight(prefixLength).not();
            return new BigInteger(1, bytes).and(mask).equals(new BigInteger(1, network).and(mask));
        }
    }
}
