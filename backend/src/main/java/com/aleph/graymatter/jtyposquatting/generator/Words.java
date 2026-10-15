package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.constants.Const;
import com.aleph.graymatter.jtyposquatting.net.DomainName;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Word-level variations: homophones, singular/plural forms and numerals written as words.
 */
public class Words {

    /** Homophones: a word is replaced by a word that sounds alike ("fourseasons" → "forseasons"). */
    public static void addHomophones(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        for (List<String> group : Const.HOMOPHONES) {
            for (String word : group) {
                for (int i = label.indexOf(word); i != -1; i = label.indexOf(word, i + 1)) {
                    for (String replacement : group) {
                        if (!replacement.equals(word)) {
                            candidates.add(parts.withLabel(label.substring(0, i) + replacement + label.substring(i + word.length())));
                        }
                    }
                }
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Singular/plural: a trailing "s" is added or removed ("example" → "examples"). */
    public static void addSingularPlural(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        if (label.endsWith("s")) {
            candidates.add(parts.withLabel(label.substring(0, label.length() - 1)));
            if (label.endsWith("es")) {
                candidates.add(parts.withLabel(label.substring(0, label.length() - 2)));
            }
        } else {
            candidates.add(parts.withLabel(label + "s"));
            if (label.endsWith("x") || label.endsWith("z") || label.endsWith("ch") || label.endsWith("sh")) {
                candidates.add(parts.withLabel(label + "es"));
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }

    /** Numeral swap: a digit is written as a word and vice versa ("shop4you" → "shopforyou", "oneclick" → "1click"). */
    public static void addNumeralSwaps(DomainName domainName, List<DomainName> resultList) {
        DomainParts parts = DomainParts.of(domainName);
        String label = parts.label();
        Set<String> candidates = new LinkedHashSet<>();
        for (Map.Entry<Character, List<String>> entry : Const.NUMERAL.entrySet()) {
            String digit = String.valueOf(entry.getKey());
            for (int i = label.indexOf(digit); i != -1; i = label.indexOf(digit, i + 1)) {
                for (String word : entry.getValue()) {
                    candidates.add(parts.withLabel(label.substring(0, i) + word + label.substring(i + 1)));
                }
            }
            for (String word : entry.getValue()) {
                for (int i = label.indexOf(word); i != -1; i = label.indexOf(word, i + 1)) {
                    candidates.add(parts.withLabel(label.substring(0, i) + digit + label.substring(i + word.length())));
                }
            }
        }
        DomainParts.addAll(domainName, candidates, resultList);
    }
}
