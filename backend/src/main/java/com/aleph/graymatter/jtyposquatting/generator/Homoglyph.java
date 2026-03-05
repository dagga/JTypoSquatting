package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.constants.Const;
import com.aleph.graymatter.jtyposquatting.net.DomainName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Homoglyph {
    public static void addHomglyphedDomains(DomainName domainName, ArrayList<DomainName> resultList) throws InvalidDomainException {
        String domainWithoutTLD = DomainName.getDomainWithoutTLD(domainName.toString());
        String TLD = domainName.getTLD();
        Set<String> uniqueDomains = new HashSet<>();

        // Iterate over all characters in domainWithoutTLD
        for (int idx = 0; idx < domainWithoutTLD.length(); idx++) {
            char c = domainWithoutTLD.charAt(idx);
            // Check if character has similar glyphs
            if (Const.SIMILAR_CHAR.containsKey(c)) {
                List<String> replacements = Const.SIMILAR_CHAR.get(c);
                // Generate all possible replacements for this character
                for (String replacement : replacements) {
                    StringBuilder stringBuilder = new StringBuilder(domainWithoutTLD);
                    stringBuilder.replace(idx, idx + 1, replacement);
                    String newDomain = stringBuilder.toString() + '.' + TLD;
                    // Only add unique domains to avoid duplicates
                    if (uniqueDomains.add(newDomain)) {
                        resultList.add(new DomainName(newDomain));
                    }
                }
            }
        }
    }
}
