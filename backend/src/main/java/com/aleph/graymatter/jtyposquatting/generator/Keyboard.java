package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fat-finger mistakes based on key adjacency on QWERTY and AZERTY keyboards.
 */
public class Keyboard {

    private static final List<List<String>> LAYOUTS = List.of(
            List.of("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"),
            List.of("1234567890", "azertyuiop", "qsdfghjklm", "wxcvbn")
    );

    private static final Map<Character, Set<Character>> ADJACENT_KEYS = buildAdjacency();

    private static Map<Character, Set<Character>> buildAdjacency() {
        Map<Character, Set<Character>> adjacency = new HashMap<>();
        for (List<String> rows : LAYOUTS) {
            for (int r = 0; r < rows.size(); r++) {
                String row = rows.get(r);
                for (int i = 0; i < row.length(); i++) {
                    Set<Character> neighbours = adjacency.computeIfAbsent(row.charAt(i), k -> new LinkedHashSet<>());
                    addKey(neighbours, row, i - 1);
                    addKey(neighbours, row, i + 1);
                    // Rows are staggered: the row above is shifted left, the row below shifted right
                    if (r > 0) {
                        addKey(neighbours, rows.get(r - 1), i);
                        addKey(neighbours, rows.get(r - 1), i + 1);
                    }
                    if (r < rows.size() - 1) {
                        addKey(neighbours, rows.get(r + 1), i - 1);
                        addKey(neighbours, rows.get(r + 1), i);
                    }
                }
            }
        }
        return adjacency;
    }

    private static void addKey(Set<Character> neighbours, String row, int index) {
        if (index >= 0 && index < row.length()) {
            neighbours.add(row.charAt(index));
        }
    }

    static Set<Character> adjacentKeys(char c) {
        return ADJACENT_KEYS.getOrDefault(c, Collections.emptySet());
    }

    /** Replacement: one character is replaced by an adjacent key ("example" → "wxample"). */
    public static void addReplacements(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 0; i < label.length(); i++) {
            for (char key : adjacentKeys(label.charAt(i))) {
                candidates.add(parts.withLabel(label.substring(0, i) + key + label.substring(i + 1)));
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Double replacement: a doubled character is replaced by a doubled adjacent key ("google" → "gppgle"). */
    public static void addDoubleReplacements(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 0; i < label.length() - 1; i++) {
            char c = label.charAt(i);
            if (c != label.charAt(i + 1)) {
                continue;
            }
            for (char key : adjacentKeys(c)) {
                candidates.add(parts.withLabel(label.substring(0, i) + key + key + label.substring(i + 2)));
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }
}
