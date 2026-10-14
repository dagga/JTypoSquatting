package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mistakes on label separators: missing dots, extra dots and dots typed as dashes.
 */
public class DotDash {

    /** Missing dot: one dot is dropped ("www.example.com" → "wwwexample.com", "example.co.uk" → "exampleco.uk"). */
    public static void addMissingDots(DomainName domainName, List<DomainName> resultList) {
        String full = domainName.toString().toLowerCase();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = full.indexOf('.'); i != -1; i = full.indexOf('.', i + 1)) {
            candidates.add(full.substring(0, i) + full.substring(i + 1));
        }
        DomainParts.addAll(domainName, candidates, resultList);
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
        DomainParts.addAll(domainName, candidates, resultList);
    }
}
