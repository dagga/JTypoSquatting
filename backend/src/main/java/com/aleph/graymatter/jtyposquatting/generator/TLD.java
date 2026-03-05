package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.net.DomainName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TLD {
    private static final Set<String> TLD_LIST = new HashSet<>();

    static {
        try {
            loadTLDList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load TLD list", e);
        }
    }

    private static void loadTLDList() throws IOException {
        // Try classpath first (fastest)
        java.io.InputStream is = TLD.class.getClassLoader().getResourceAsStream("TLD.txt");
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null) line = reader.readLine(); // skip comment line
                while (line != null) {
                    String tld = line.trim().toLowerCase();
                    if (!tld.isEmpty()) {
                        TLD_LIST.add(tld);
                    }
                    line = reader.readLine();
                }
                return;
            }
        }
        
        // Fallback to file system
        Path tldPath = Paths.get("TLD.txt");
        if (!Files.exists(tldPath)) tldPath = Paths.get("backend/TLD.txt");
        if (!Files.exists(tldPath)) tldPath = Paths.get(System.getProperty("user.dir"), "TLD.txt");
        
        if (Files.exists(tldPath)) {
            try (BufferedReader reader = Files.newBufferedReader(tldPath, StandardCharsets.UTF_8)) {
                String line = reader.readLine();
                if (line != null) line = reader.readLine(); // skip comment line
                while (line != null) {
                    String tld = line.trim().toLowerCase();
                    if (!tld.isEmpty()) {
                        TLD_LIST.add(tld);
                    }
                    line = reader.readLine();
                }
            }
        } else {
            throw new RuntimeException("TLD.txt not found in any location");
        }
    }

    public static void AddAndReplaceAllTLD(DomainName domainName, ArrayList<DomainName> resultList) {
        String domainWithoutTLD = DomainName.getDomainWithoutTLD(domainName.toString());
        String originalTLD = domainName.getTLD().toLowerCase();
        Set<String> uniqueDomains = new HashSet<>();

        for (String tld : TLD_LIST) {
            if (!tld.equals(originalTLD)) {
                String newDomain = domainWithoutTLD + '.' + tld;
                if (uniqueDomains.add(newDomain)) {
                    try {
                        resultList.add(new DomainName(newDomain));
                    } catch (InvalidDomainException e) {
                        // Skip invalid domains silently
                    }
                }
            }
        }
    }

    public static void UpdateTLDList() throws IOException {
        TLD_LIST.clear();
        loadTLDList();
    }
}
