package com.aleph.graymatter.jtyposquatting;

import com.aleph.graymatter.jtyposquatting.generator.Algorithm;
import com.aleph.graymatter.jtyposquatting.generator.TLD;
import com.aleph.graymatter.jtyposquatting.net.DomainName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Business logic service for domain typo squatting analysis
 */
public class JTypoSquatting {
    private static final Logger log = LoggerFactory.getLogger(JTypoSquatting.class);
    private final ArrayList<String> listOfDomains = new ArrayList<>();

    public JTypoSquatting(String domain) throws FileNotFoundException, InvalidDomainException {
        DomainName domainName;
        try {
            domainName = new DomainName(domain);
        } catch (InvalidDomainException e) {
            throw e;
        }

        // Every algorithm is applied to the original domain; duplicates across algorithms are dropped
        java.util.Set<String> uniqueDomains = new java.util.LinkedHashSet<>();
        for (Algorithm algorithm : Algorithm.values()) {
            ArrayList<DomainName> variations = new ArrayList<>();
            algorithm.generate(domainName, variations);
            for (DomainName d : variations) {
                uniqueDomains.add(d.toString());
            }
            log.debug("{} generated {} variations", algorithm.getId(), variations.size());
        }
        uniqueDomains.remove(domainName.toString());

        listOfDomains.addAll(uniqueDomains);
    }

    public ArrayList<String> getListOfDomains() {
        return new ArrayList<>(listOfDomains);
    }

    private void UpdateTLDList() throws IOException {
        TLD.UpdateTLDList();
    }
}
