package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;
import com.google.common.net.InternetDomainName;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mistakes on label separators: missing dots, extra dots and dots typed as dashes.
 */
public class DotDash {

    /**
     * Removing or replacing the last dot glues the extension to the label ("www.examplecom"): keep only
     * names that still end with a real public suffix, so that they could actually be registered.
     */
    private static Set<String> registrable(Set<String> candidates) {
        Set<String> kept = new LinkedHashSet<>();
        for (String candidate : candidates) {
            try {
                if (InternetDomainName.from(candidate).isUnderPublicSuffix()) {
                    kept.add(candidate);
                }
            } catch (IllegalArgumentException ignored) {
                // Not a valid domain name
            }
        }
        return kept;
    }

    /** Missing dot: one dot is dropped ("www.example.com" → "wwwexample.com", "example.co.uk" → "exampleco.uk"). */
    public static void addMissingDots(DomainName domainName, List<DomainName> resultList) {
        String full = domainName.toString().toLowerCase();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = full.indexOf('.'); i != -1; i = full.indexOf('.', i + 1)) {
            candidates.add(full.substring(0, i) + full.substring(i + 1));
        }
        DomainParts.addAll(domainName, registrable(candidates), resultList);
    }

    /** Subdomain: a dot is inserted inside the label ("example.com" → "ex.ample.com"). */
    public static void addSubdomains(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 1; i < label.length(); i++) {
            if (label.charAt(i - 1) != '-' && label.charAt(i) != '-') {
                candidates.add(parts.withLabel(label.substring(0, i) + "." + label.substring(i)));
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Change dot to dash: one dot is typed as a dash ("www.example.com" → "www-example.com"). */
    public static void addDotToDash(DomainName domainName, List<DomainName> resultList) {
        String full = domainName.toString().toLowerCase();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = full.indexOf('.'); i != -1; i = full.indexOf('.', i + 1)) {
            candidates.add(full.substring(0, i) + "-" + full.substring(i + 1));
        }
        DomainParts.addAll(domainName, registrable(candidates), resultList);
    }
}
