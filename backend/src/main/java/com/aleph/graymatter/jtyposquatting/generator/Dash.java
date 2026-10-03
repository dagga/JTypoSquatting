package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.net.DomainName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

// it would not work with domain names like xxx.yyy.www.zz domains with www.yyy has subdomain
public class Dash {
    public static void addDash(DomainName domainName, ArrayList<DomainName> resultList) {
        String fullDomain = domainName.toString();
        String tld = domainName.getTLD();
        String base = fullDomain.substring(0, fullDomain.length() - tld.length() - 1);
        
        String subDomain = "";
        String domain = base;
        if (base.contains(".")) {
            subDomain = base.substring(0, base.lastIndexOf("."));
            domain = base.substring(base.lastIndexOf(".") + 1);
        }
        
        Set<String> uniqueDomains = new HashSet<>();
        String prefix = (!subDomain.isEmpty()) ? subDomain + "." : "";

        for (int i = 1; i < domain.length(); i++) {
            StringBuilder sb = new StringBuilder(domain);
            sb.insert(i, '-');
            String newDomain = prefix + sb + '.' + tld;
            if (uniqueDomains.add(newDomain)) {
                try {
                    resultList.add(new DomainName(newDomain));
                } catch (InvalidDomainException ignored) {
                    // Ignore invalid domains generated
                }
            }
        }
    }

    public static void removeDash(DomainName domainName, ArrayList<DomainName> resultList) {
        String fullDomain = domainName.toString();
        String tld = domainName.getTLD();
        String base = fullDomain.substring(0, fullDomain.length() - tld.length() - 1);
        
        String subDomain = "";
        String domain = base;
        if (base.contains(".")) {
            subDomain = base.substring(0, base.lastIndexOf("."));
            domain = base.substring(base.lastIndexOf(".") + 1);
        }
        
        if (!domain.contains("-")) {
            return; // No dash to remove, early exit
        }

        // Remove all dashes at once
        StringBuilder sb = new StringBuilder(domain);
        while (sb.indexOf("-") != -1) {
            sb.delete(sb.indexOf("-"), sb.indexOf("-") + 1);
        }
        String newDomain = (!subDomain.isEmpty() ? subDomain + "." : "") + sb + '.' + tld;
        try {
            resultList.add(new DomainName(newDomain));
        } catch (InvalidDomainException ignored) {
            // Ignore invalid domains generated
        }
    }

}
