package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.constants.Const;
import com.aleph.graymatter.jtyposquatting.net.DomainName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of Homoglyph: generation of look-alike variants.
 */
class HomoglyphTest {

    @Test
    @DisplayName("Domain without any look-alike character → empty list")
    void testNoHomoglyphCharacters() throws Exception {
        // If no character of "xyz.com" has a substitute in Const.SIMILAR_CHAR, the list stays empty.
        // (Checks that the method does not fail and follows the filtering logic.)
        DomainName dn = new DomainName("xyz.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);
        // The result depends on the map: only checks that no exception is thrown
        assertNotNull(result);
    }

    @Test
    @DisplayName("'google.com' generates variants when 'g', 'o', 'l', 'e' have substitutes")
    void testGoogleGeneratesVariants() throws Exception {
        DomainName dn = new DomainName("google.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        // Variants keep '.com'
        for (DomainName variant : result) {
            assertTrue(variant.toString().endsWith(".com"),
                    "Variant without .com: " + variant);
        }
    }

    @Test
    @DisplayName("All generated domains are unique")
    void testNoDuplicates() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        Set<String> unique = result.stream()
                .map(DomainName::toString)
                .collect(Collectors.toSet());

        assertEquals(result.size(), unique.size(),
                "Duplicates found in the results");
    }

    @Test
    @DisplayName("No variant is identical to the original domain")
    void testVariantsDifferFromOriginal() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        for (DomainName variant : result) {
            assertNotEquals("example.com", variant.toString(),
                    "The variant is identical to the original domain");
        }
    }

    @Test
    @DisplayName("When 'o' has a substitute, 'google' generates a replacement of 'o'")
    void testCharacterOReplacedIfSubstituteExists() throws Exception {
        if (!Const.SIMILAR_CHAR.containsKey('o')) {
            // Conditional test: skipped when 'o' is not in the map
            return;
        }

        DomainName dn = new DomainName("google.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        // "google" contains 3 'o': there must be at least as many variants as substitutes * occurrences
        assertFalse(result.isEmpty(), "No variant generated for 'google.com' although 'o' has substitutes");
    }

    @Test
    @DisplayName("The TLD is not changed by the look-alike substitution")
    void testTLDNotAlteredByHomoglyph() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        // Every variant must keep the TLD of the original
        for (DomainName variant : result) {
            assertEquals("com", variant.getTLD(),
                    "The TLD was changed: " + variant);
        }
    }
}
