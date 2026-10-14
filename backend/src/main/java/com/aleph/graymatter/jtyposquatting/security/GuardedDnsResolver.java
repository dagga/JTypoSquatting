package com.aleph.graymatter.jtyposquatting.security;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * DNS resolver for Apache HttpClient that only returns addresses allowed by the {@link OutboundPolicy}.
 * The client connects to the addresses validated here, which prevents DNS rebinding between check and use.
 */
public class GuardedDnsResolver implements DnsResolver {

    private final OutboundPolicy policy;

    public GuardedDnsResolver(OutboundPolicy policy) {
        this.policy = policy;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return policy.resolve(host);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return policy.resolve(host)[0].getCanonicalHostName();
    }
}
