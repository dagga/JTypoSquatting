package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.constants.Const;
import com.aleph.graymatter.jtyposquatting.net.DomainName;
import com.google.common.net.InternetDomainName;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Variations of what follows the registrable label: second-level domains and dynamic DNS providers.
 */
public class Suffixes {

    /** Wrong SLD: the second-level part of a ccTLD suffix is changed ("example.co.uk" → "example.org.uk"). */
    public static void addWrongSld(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String suffix = parts.suffix();
        int dot = suffix.indexOf('.');
        if (dot == -1) {
            return;
        }
        String sld = suffix.substring(0, dot);
        String rest = suffix.substring(dot + 1);
        Set<String> candidates = new LinkedHashSet<>();
        for (String candidateSld : Const.COMMON_SLD) {
            String candidateSuffix = candidateSld + "." + rest;
            if (!candidateSld.equals(sld) && isPublicSuffix(candidateSuffix)) {
                candidates.add(parts.prefix() + parts.label() + "." + candidateSuffix);
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Add TLD: a country code is appended to a generic suffix ("example.com" → "example.com.br"). */
    public static void addTld(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        Set<String> candidates = new LinkedHashSet<>();
        for (String tld : TLD.getTLDList()) {
            String candidateSuffix = parts.suffix() + "." + tld;
            if (tld.length() == 2 && isPublicSuffix(candidateSuffix)) {
                candidates.add(parts.prefix() + parts.label() + "." + candidateSuffix);
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Dynamic DNS: the label is registered under a free dynamic DNS provider ("example.com" → "example.duckdns.org"). */
    public static void addDynamicDns(DomainName domainName, List<DomainName> resultList) {
        String label = DomainParts.of(domainName).label();
        Set<String> candidates = new LinkedHashSet<>();
        for (String provider : Const.DYNAMIC_DNS_PROVIDERS) {
            candidates.add(label + "." + provider);
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    private static boolean isPublicSuffix(String suffix) {
        try {
            return InternetDomainName.from(suffix).isPublicSuffix();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
