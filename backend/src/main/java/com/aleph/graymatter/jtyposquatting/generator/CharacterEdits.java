package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Single-character typing mistakes applied to the registrable label.
 */
public class CharacterEdits {

    private static final String VOWELS = "aeiouy";
    private static final String APPENDABLE = "abcdefghijklmnopqrstuvwxyz0123456789";

    /** Omission: one character is missing ("example" → "exmple"). */
    public static void addOmissions(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 0; i < label.length(); i++) {
            candidates.add(parts.withLabel(label.substring(0, i) + label.substring(i + 1)));
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Repetition: one character is typed twice ("example" → "exxample"). */
    public static void addRepetitions(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (c != '-') {
                candidates.add(parts.withLabel(label.substring(0, i + 1) + c + label.substring(i + 1)));
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Change order: two adjacent characters are swapped ("example" → "exmaple"). */
    public static void addTranspositions(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 0; i < label.length() - 1; i++) {
            if (label.charAt(i) != label.charAt(i + 1)) {
                char[] chars = label.toCharArray();
                chars[i] = label.charAt(i + 1);
                chars[i + 1] = label.charAt(i);
                candidates.add(parts.withLabel(new String(chars)));
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Addition: one extra character at the end of the label ("example" → "examplea"). */
    public static void addAdditions(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        Set<String> candidates = new LinkedHashSet<>();
        for (char c : APPENDABLE.toCharArray()) {
            candidates.add(parts.withLabel(parts.label() + c));
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Vowel swap: a vowel is replaced by another vowel ("example" → "exampla"). */
    public static void addVowelSwaps(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 0; i < label.length(); i++) {
            char current = label.charAt(i);
            if (VOWELS.indexOf(current) == -1) {
                continue;
            }
            for (char vowel : VOWELS.toCharArray()) {
                if (vowel != current) {
                    candidates.add(parts.withLabel(label.substring(0, i) + vowel + label.substring(i + 1)));
                }
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }
}
