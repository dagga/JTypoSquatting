package com.aleph.graymatter.jtyposquatting;

import com.aleph.graymatter.jtyposquatting.generator.Dash;
import com.aleph.graymatter.jtyposquatting.generator.Homoglyph;
import com.aleph.graymatter.jtyposquatting.generator.Misspell;
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

        // Update TLD list from external sources
        try {
            UpdateTLDList();
        } catch (IOException ioe) {
            log.error("e: ", ioe);
        }

        ArrayList<DomainName> domainsArrayResults = new ArrayList<>();

        // Generate typo variations
        Misspell.AddMisspelledDomains(domainName, domainsArrayResults);
        Homoglyph.addHomglyphedDomains(domainName, domainsArrayResults);
        Dash.addDash(domainName, domainsArrayResults);
        Dash.removeDash(domainName, domainsArrayResults);
        
        // Apply TLD variations to the ORIGINAL domain (not typo variations)
        ArrayList<DomainName> tldVariations = new ArrayList<>();
        TLD.AddAndReplaceAllTLD(domainName, tldVariations);
        
        // Combine: typo variations (with original TLD) + TLD variations (of original domain)
        java.util.Set<String> uniqueDomains = new java.util.LinkedHashSet<>();
        
        // Add typo variations first (use toString() to get full domain with TLD)
        for (DomainName d : domainsArrayResults) {
            uniqueDomains.add(d.toString());
        }
        
        // Add TLD variations of original domain
        for (DomainName d : tldVariations) {
            uniqueDomains.add(d.toString());
        }
        
        listOfDomains.addAll(uniqueDomains);
    }

    public ArrayList<String> getListOfDomains() {
        return new ArrayList<>(listOfDomains);
    }

    private void UpdateTLDList() throws IOException {
        TLD.UpdateTLDList();
    }
}
