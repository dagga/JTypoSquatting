package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.net.DomainName;
import com.aleph.graymatter.jtyposquatting.util.JSonUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Misspell {

    private static final JSONParser jsonP = new JSONParser();
    private static volatile java.util.Map<String, java.util.List<String>> misspellingsMap;
    private static final Object lock = new Object();

    public static java.util.Map<String, java.util.List<String>> loadMisspellings() {
        if (misspellingsMap != null) {
            return misspellingsMap;
        }

        synchronized (lock) {
            if (misspellingsMap != null) {
                return misspellingsMap;
            }

            // Try classpath first (fastest)
            try {
                java.io.InputStream is = Misspell.class.getClassLoader().getResourceAsStream("common-misspellings.json");
                if (is != null) {
                    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        misspellingsMap = JSonUtils.KeysValuesSwapMulti((JSONObject) jsonP.parse(reader));
                        return misspellingsMap;
                    }
                }
            } catch (Exception e) {
                // Fall through to file system
            }

            // Fallback to file system
            try {
                Path path = Paths.get("common-misspellings.json");
                if (!Files.exists(path)) {
                    path = Paths.get("../common-misspellings.json");
                }
                if (!Files.exists(path)) {
                    path = Paths.get(System.getProperty("user.dir"), "common-misspellings.json");
                }
                if (Files.exists(path)) {
                    misspellingsMap = JSonUtils.KeysValuesSwapMulti((JSONObject) jsonP.parse(Files.newBufferedReader(path)));
                    return misspellingsMap;
                }
            } catch (Exception e) {
                // Fall through
            }

            throw new RuntimeException("common-misspellings.json not found in classpath or filesystem");
        }
    }

    public static void AddMisspelledDomains(DomainName domainName, ArrayList<DomainName> resultList) throws InvalidDomainException {
        java.util.Map<String, java.util.List<String>> map = loadMisspellings();
        String domainWithoutTLD = DomainName.getDomainWithoutTLD(domainName.toString());
        String TLD = DomainName.getSuffix(domainName.toString());
        Set<String> uniqueDomains = new HashSet<>();

        for (java.util.Map.Entry<String, java.util.List<String>> entry : map.entrySet()) {
            String correctWord = entry.getKey();
            if (domainWithoutTLD.contains(correctWord)) {
                for (String misspelling : entry.getValue()) {
                    String misspelledDomainWithoutTLD = domainWithoutTLD.replace(correctWord, misspelling);
                    String newDomain = misspelledDomainWithoutTLD + '.' + TLD;
                    if (uniqueDomains.add(newDomain)) {
                        resultList.add(new DomainName(newDomain));
                    }
                }
            }
        }
    }
}
