package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.net.DomainName;
import com.google.common.net.InternetDomainName;

import java.util.Collection;
import java.util.List;

/**
 * Splits a domain into subdomain prefix, registrable label and public suffix,
 * using the Public Suffix List (e.g. "www.example.co.uk" → "www", "example", "co.uk").
 */
record DomainParts(String subDomain, String label, String suffix) {

    static DomainParts of(DomainName domainName) {
        String full = domainName.toString().toLowerCase();
        try {
            InternetDomainName idn = InternetDomainName.from(full);
            if (idn.isUnderPublicSuffix()) {
                InternetDomainName top = idn.topPrivateDomain();
                String suffix = top.publicSuffix().toString();
                String label = top.parts().getFirst();
                String sub = full.length() > top.toString().length()
                        ? full.substring(0, full.length() - top.toString().length() - 1)
                        : "";
                return new DomainParts(sub, label, suffix);
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Fall back to the last label as suffix
        }
        int lastDot = full.lastIndexOf('.');
        String rest = full.substring(0, lastDot);
        int prevDot = rest.lastIndexOf('.');
        return prevDot == -1
                ? new DomainParts("", rest, full.substring(lastDot + 1))
                : new DomainParts(rest.substring(0, prevDot), rest.substring(prevDot + 1), full.substring(lastDot + 1));
    }

    String prefix() {
        return subDomain.isEmpty() ? "" : subDomain + ".";
    }

    /** Rebuilds a full domain with the same subdomain and suffix but a different label. */
    String withLabel(String newLabel) {
        return prefix() + newLabel + "." + suffix;
    }

    /** Adds every candidate that differs from the original and is a valid domain name. */
    static void addAll(DomainName original, Collection<String> candidates, List<DomainName> resultList) {
        String originalString = original.toString().toLowerCase();
        for (String candidate : candidates) {
            if (candidate.equals(originalString)) {
                continue;
            }
            try {
                resultList.add(new DomainName(candidate));
            } catch (InvalidDomainException ignored) {
                // Skip invalid generated domains
            }
        }
    }
}
