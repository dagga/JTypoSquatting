package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.net.DomainName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

// it would not work with domain names like xxx.yyy.www.zz domains with www.yyy has subdomain
public class Dash {
    public static void addDash(DomainName domainName, ArrayList<DomainName> resultList) {
        String domainWithoutSubDomainMinusTLD = DomainName.getDomainWithoutSubDomainMinusTLD(domainName.toString());
        String subDomain = domainName.getSubDomain();
        String tld = domainName.getTLD();
        Set<String> uniqueDomains = new HashSet<>();

        for (int i = 1; i < domainWithoutSubDomainMinusTLD.length(); i++) {
            StringBuilder sb = new StringBuilder(domainWithoutSubDomainMinusTLD);
            sb.insert(i, '-');
            String newDomain = subDomain + '.' + sb + '.' + tld;
            if (uniqueDomains.add(newDomain)) {
                try {
                    resultList.add(new DomainName(newDomain));
                } catch (InvalidDomainException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static void removeDash(DomainName domainName, ArrayList<DomainName> resultList) {
        String domainWithoutSubDomainMinusTLD = DomainName.getDomainWithoutSubDomainMinusTLD(domainName.toString());
        if (!domainWithoutSubDomainMinusTLD.contains("-")) {
            return; // No dash to remove, early exit
        }

        String subDomain = domainName.getSubDomain();
        String tld = domainName.getTLD();
        Set<String> uniqueDomains = new HashSet<>();

        // Remove all dashes at once
        StringBuilder sb = new StringBuilder(domainWithoutSubDomainMinusTLD);
        while (sb.indexOf("-") != -1) {
            sb.delete(sb.indexOf("-"), sb.indexOf("-") + 1);
        }
        String newDomain = subDomain + '.' + sb + '.' + tld;
        if (uniqueDomains.add(newDomain)) {
            try {
                resultList.add(new DomainName(newDomain));
            } catch (InvalidDomainException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
