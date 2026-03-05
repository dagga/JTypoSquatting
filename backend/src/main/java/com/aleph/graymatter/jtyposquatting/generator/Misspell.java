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
    private static JSONObject jo;

    private static JSONObject loadMisspellings() {
        if (jo != null) {
            return jo;
        }
        
        // Try classpath first (fastest)
        try (InputStreamReader reader = new InputStreamReader(
                Misspell.class.getClassLoader().getResourceAsStream("common-misspellings.json"),
                StandardCharsets.UTF_8)) {
            if (reader != null) {
                jo = JSonUtils.KeysValuesSwap((JSONObject) jsonP.parse(reader));
                return jo;
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
                jo = JSonUtils.KeysValuesSwap((JSONObject) jsonP.parse(Files.newBufferedReader(path)));
                return jo;
            }
        } catch (Exception e) {
            // Fall through
        }
        
        throw new RuntimeException("common-misspellings.json not found in classpath or filesystem");
    }

    public static void AddMisspelledDomains(DomainName domainName, ArrayList<DomainName> resultList) throws InvalidDomainException {
        JSONObject localJo = loadMisspellings();
        String domainWithoutTLD = DomainName.getDomainWithoutTLD(domainName.toString());
        String TLD = DomainName.getSuffix(domainName.toString());
        Set<String> uniqueDomains = new HashSet<>();

        Set<String> keySet = (Set<String>) localJo.keySet();

        for (String key : keySet) {
            if (domainWithoutTLD.contains(key)) {
                String misspelledDomainWithoutTLD = domainWithoutTLD.replace(key, (CharSequence) localJo.get(key));
                String newDomain = misspelledDomainWithoutTLD + '.' + TLD;
                if (uniqueDomains.add(newDomain)) {
                    resultList.add(new DomainName(newDomain));
                }
            }
        }
    }
}
