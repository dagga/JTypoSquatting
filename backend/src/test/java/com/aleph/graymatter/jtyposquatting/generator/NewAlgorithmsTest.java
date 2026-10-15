package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.JTypoSquatting;
import com.aleph.graymatter.jtyposquatting.constants.Const;
import com.aleph.graymatter.jtyposquatting.net.DomainName;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of the added generation algorithms (omission, keyboard, dots/dashes, words, suffixes).
 */
class NewAlgorithmsTest {

    private static Set<String> generate(Algorithm algorithm, String domain) throws Exception {
        List<DomainName> result = new ArrayList<>();
        algorithm.generate(new DomainName(domain), result);
        return result.stream().map(DomainName::toString).collect(Collectors.toSet());
    }

    // A sample domain suited to each algorithm (some only apply to specific cases)
    private static final Map<Algorithm, String> SAMPLE_DOMAINS = Map.of(
            Algorithm.STRIP_DASH, "my-example.com",
            Algorithm.DOUBLE_REPLACEMENT, "google.com",
            Algorithm.HOMOPHONES, "fourseasons.com",
            Algorithm.WRONG_SLD, "example.co.uk",
            Algorithm.NUMERAL_SWAP, "shop4you.com",
            Algorithm.COMMON_MISSPELLING, "absence.com",
            Algorithm.MISSING_DOT, "www.example.com",
            Algorithm.CHANGE_DOT_DASH, "www.example.com"
    );

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    @DisplayName("Every declared algorithm produces at least one variant, never the original domain")
    void testEveryAlgorithmGenerates(Algorithm algorithm) throws Exception {
        String domain = SAMPLE_DOMAINS.getOrDefault(algorithm, "example.com");
        Set<String> result = generate(algorithm, domain);
        assertFalse(result.isEmpty(), algorithm.getId() + " generated nothing for " + domain);
        assertFalse(result.contains(domain), algorithm.getId() + " returned the original domain");
    }

    @Test
    @DisplayName("Algorithm identifiers are unique")
    void testAlgorithmIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (Algorithm algorithm : Algorithm.values()) {
            assertTrue(ids.add(algorithm.getId()), "Duplicate identifier: " + algorithm.getId());
        }
        assertEquals(21, ids.size());
    }

    @Test
    @DisplayName("Omission, repetition, transposition")
    void testCharacterEdits() throws Exception {
        assertTrue(generate(Algorithm.OMISSION, "example.com").contains("exmple.com"));
        assertTrue(generate(Algorithm.REPETITION, "example.com").contains("exxample.com"));
        assertTrue(generate(Algorithm.CHANGE_ORDER, "example.com").contains("exmaple.com"));
        assertTrue(generate(Algorithm.ADDITION, "example.com").contains("examplea.com"));
        assertTrue(generate(Algorithm.VOWEL_SWAP, "example.com").contains("exampla.com"));
    }

    @Test
    @DisplayName("Variants keep the subdomain and the public suffix (co.uk)")
    void testPartsArePreserved() throws Exception {
        Set<String> result = generate(Algorithm.OMISSION, "www.example.co.uk");
        assertTrue(result.contains("www.exmple.co.uk"), result.toString());
        for (String variant : result) {
            assertTrue(variant.startsWith("www.") && variant.endsWith(".co.uk"), variant);
        }
    }

    @Test
    @DisplayName("Replacement by an adjacent key on QWERTY and AZERTY")
    void testKeyboardReplacement() throws Exception {
        Set<String> result = generate(Algorithm.REPLACEMENT, "example.com");
        assertTrue(result.contains("wxample.com"), "QWERTY : e → w");
        assertTrue(result.contains("ezample.com"), "AZERTY/QWERTY : x → z");
        assertTrue(generate(Algorithm.REPLACEMENT, "amazon.com").contains("qmazon.com"), "AZERTY : a → q");
        assertTrue(generate(Algorithm.DOUBLE_REPLACEMENT, "google.com").contains("gppgle.com"));
    }

    @Test
    @DisplayName("Dots and dashes")
    void testDotDash() throws Exception {
        assertTrue(generate(Algorithm.MISSING_DOT, "www.example.com").contains("wwwexample.com"));
        assertTrue(generate(Algorithm.MISSING_DOT, "example.co.uk").contains("exampleco.uk"));
        assertTrue(generate(Algorithm.SUBDOMAIN, "example.com").contains("ex.ample.com"));
        assertTrue(generate(Algorithm.CHANGE_DOT_DASH, "www.example.com").contains("www-example.com"));
        // Removing the only dot would produce a name without a TLD: nothing valid
        assertTrue(generate(Algorithm.MISSING_DOT, "example.com").isEmpty());
        // An extension glued to the label is not a registrable domain
        assertFalse(generate(Algorithm.MISSING_DOT, "www.example.com").contains("www.examplecom"));
        assertFalse(generate(Algorithm.CHANGE_DOT_DASH, "www.example.com").contains("www.example-com"));
        assertTrue(generate(Algorithm.CHANGE_DOT_DASH, "example.co.uk").contains("example-co.uk"));
    }

    @Test
    @DisplayName("Homophones, singular/plural, numbers as words")
    void testWords() throws Exception {
        assertTrue(generate(Algorithm.HOMOPHONES, "fourseasons.com").contains("forseasons.com"));
        assertTrue(generate(Algorithm.SINGULAR_PLURALIZE, "example.com").contains("examples.com"));
        assertTrue(generate(Algorithm.SINGULAR_PLURALIZE, "examples.com").contains("example.com"));
        Set<String> numerals = generate(Algorithm.NUMERAL_SWAP, "shop4you.com");
        assertTrue(numerals.contains("shopfouryou.com"));
        assertTrue(numerals.contains("shopforyou.com"));
        assertTrue(generate(Algorithm.NUMERAL_SWAP, "oneclick.com").contains("1click.com"));
    }

    @Test
    @DisplayName("Wrong SLD, added TLD, dynamic DNS")
    void testSuffixes() throws Exception {
        Set<String> sld = generate(Algorithm.WRONG_SLD, "example.co.uk");
        assertTrue(sld.contains("example.org.uk"), sld.toString());
        assertFalse(sld.contains("example.co.uk"));
        assertTrue(generate(Algorithm.WRONG_SLD, "example.com").isEmpty(), "No SLD to replace");

        assertTrue(generate(Algorithm.ADD_TLD, "example.com").contains("example.com.br"));
        assertTrue(generate(Algorithm.ADD_DYNAMIC_DNS, "www.example.com").contains("example.duckdns.org"));
    }

    @Test
    @DisplayName("The selected dynamic DNS providers are listed in dynamic-dns.json")
    void testDynamicDnsProvidersAreKnown() throws Exception {
        Set<String> known = new HashSet<>();
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("dynamic-dns.json"), StandardCharsets.UTF_8)) {
            for (JsonElement e : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("list")) {
                known.add(e.getAsString());
            }
        }
        for (String provider : Const.DYNAMIC_DNS_PROVIDERS) {
            assertTrue(known.contains("." + provider), provider + " missing from dynamic-dns.json");
        }
    }

    @Test
    @DisplayName("JTypoSquatting aggregates every algorithm without duplicates nor the original domain")
    void testAggregation() throws Exception {
        List<String> domains = new JTypoSquatting("example.com").getListOfDomains();
        assertEquals(domains.size(), new HashSet<>(domains).size(), "Duplicates found");
        assertFalse(domains.contains("example.com"));
        assertTrue(domains.contains("exmple.com"));
        assertTrue(domains.contains("example.duckdns.org"));
        assertTrue(domains.contains("example.fr"));
    }
}
