package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of Misspell: the KeysValuesSwapMulti fix keeps ALL the misspellings of a word
 * (audit point 6 regression).
 */
class MisspellTest {

    private static Map<String, List<String>> misspellingsMap;

    @BeforeAll
    static void loadMap() {
        // Loads the dictionary once for all the tests
        misspellingsMap = Misspell.loadMisspellings();
    }

    // ---------------------------------------------------------------
    // Tests of the loaded dictionary
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The dictionary is neither null nor empty")
    void testDictionaryNotEmpty() {
        assertNotNull(misspellingsMap);
        assertFalse(misspellingsMap.isEmpty(),
                "The misspelling dictionary must not be empty");
    }

    @Test
    @DisplayName("Each key maps to a non-empty list of misspellings")
    void testEachKeyHasNonEmptyList() {
        for (Map.Entry<String, List<String>> e : misspellingsMap.entrySet()) {
            assertNotNull(e.getValue(),
                    "Null list for key: " + e.getKey());
            assertFalse(e.getValue().isEmpty(),
                    "Empty list for key: " + e.getKey());
        }
    }

    @Test
    @DisplayName("Audit point 6: lists keep several entries when the original JSON had several")
    void testMultipleMisspellingsPreserved() {
        // Looks for a word with at least 2 misspellings in the dictionary
        // (checks that KeysValuesSwapMulti does not drop duplicates)
        long wordsWithMultipleMisspellings = misspellingsMap.values().stream()
                .filter(list -> list.size() > 1)
                .count();

        // In the common-misspellings dictionary, many words have several misspellings
        assertTrue(wordsWithMultipleMisspellings > 0,
                "No word has several misspellings in the inverted dictionary: "
                        + "KeysValuesSwapMulti may have truncated the data");
    }

    @Test
    @DisplayName("No misspelling is duplicated in the list of a key")
    void testNoDuplicateMisspellings() {
        for (Map.Entry<String, List<String>> e : misspellingsMap.entrySet()) {
            List<String> values = e.getValue();
            long distinctCount = values.stream().distinct().count();
            assertEquals(values.size(), distinctCount,
                    "Duplicate misspellings for key '" + e.getKey() + "': " + values);
        }
    }

    // ---------------------------------------------------------------
    // Tests of AddMisspelledDomains
    // ---------------------------------------------------------------

    @Test
    @DisplayName("AddMisspelledDomains returns variants for a domain containing a dictionary word")
    void testAddMisspelledDomainsProducesVariants() throws Exception {
        // "the" is often in the misspelling dictionary
        // Looks for a key present in the map
        String targetWord = misspellingsMap.keySet().iterator().next();
        DomainName dn = new DomainName(targetWord + ".com");

        ArrayList<DomainName> result = new ArrayList<>();
        Misspell.AddMisspelledDomains(dn, result);

        assertFalse(result.isEmpty(),
                "No variant generated for the domain containing '" + targetWord + "'");
    }

    @Test
    @DisplayName("AddMisspelledDomains returns an empty list for a domain without any match")
    void testAddMisspelledDomainsEmptyForUnknownDomain() throws Exception {
        // "zzzzzzz" is very unlikely to be in the dictionary
        DomainName dn = new DomainName("zzzzzzz.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Misspell.AddMisspelledDomains(dn, result);

        assertTrue(result.isEmpty(),
                "Variants were generated for an unknown domain");
    }

    @Test
    @DisplayName("AddMisspelledDomains: no duplicate in the results")
    void testAddMisspelledDomainsNoDuplicates() throws Exception {
        String targetWord = misspellingsMap.keySet().iterator().next();
        DomainName dn = new DomainName(targetWord + ".com");

        ArrayList<DomainName> result = new ArrayList<>();
        Misspell.AddMisspelledDomains(dn, result);

        List<String> strings = result.stream().map(DomainName::toString).collect(Collectors.toList());
        long distinct = strings.stream().distinct().count();
        assertEquals(strings.size(), distinct, "Duplicates were generated");
    }

    @Test
    @DisplayName("AddMisspelledDomains: the original TLD is kept in the variants")
    void testAddMisspelledDomainsTLDPreserved() throws Exception {
        String targetWord = misspellingsMap.keySet().iterator().next();
        DomainName dn = new DomainName(targetWord + ".fr");

        ArrayList<DomainName> result = new ArrayList<>();
        Misspell.AddMisspelledDomains(dn, result);

        for (DomainName variant : result) {
            assertEquals("fr", variant.getTLD(),
                    "The TLD changed in variant: " + variant);
        }
    }
}
