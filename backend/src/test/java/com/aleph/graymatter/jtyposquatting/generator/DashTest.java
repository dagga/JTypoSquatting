package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour Dash — vérification de l'insertion / suppression de tirets,
 * et correction du bug "leading dot" sur les domaines sans sous-domaine.
 */
class DashTest {

    // ---------------------------------------------------------------
    // addDash — domaine sans sous-domaine
    // ---------------------------------------------------------------

    @Test
    @DisplayName("addDash génère N-1 variantes pour un domaine de N caractères")
    void testAddDashCountVariants() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.addDash(dn, result);

        // "example" a 7 caractères → 6 positions d'insertion possibles
        assertEquals(6, result.size());
    }

    @Test
    @DisplayName("addDash ne génère pas de domaine avec un point en tête (régression bug leading dot)")
    void testAddDashNoLeadingDot() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.addDash(dn, result);

        for (DomainName variant : result) {
            String s = variant.toString();
            assertFalse(s.startsWith("."),
                    "Le domaine ne doit pas commencer par un point : " + s);
            assertTrue(s.endsWith(".com"),
                    "Le domaine doit se terminer par .com : " + s);
        }
    }

    @Test
    @DisplayName("addDash avec sous-domaine — le sous-domaine est préservé")
    void testAddDashPreservesSubdomain() throws Exception {
        DomainName dn = new DomainName("www.example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.addDash(dn, result);

        // Tous les résultats doivent commencer par "www."
        for (DomainName variant : result) {
            assertTrue(variant.toString().startsWith("www."),
                    "Sous-domaine 'www' absent : " + variant);
        }
    }

    @Test
    @DisplayName("addDash produit les variantes attendues pour 'abc.com'")
    void testAddDashExactVariants() throws Exception {
        DomainName dn = new DomainName("abc.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.addDash(dn, result);

        // "abc" → "a-bc" (pos 1), "ab-c" (pos 2)
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(d -> d.toString().equals("a-bc.com")));
        assertTrue(result.stream().anyMatch(d -> d.toString().equals("ab-c.com")));
    }

    // ---------------------------------------------------------------
    // removeDash
    // ---------------------------------------------------------------

    @Test
    @DisplayName("removeDash supprime un tiret et produit le domaine sans tiret")
    void testRemoveDash() throws Exception {
        DomainName dn = new DomainName("ex-ample.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.removeDash(dn, result);

        assertEquals(1, result.size());
        assertEquals("example.com", result.get(0).toString());
    }

    @Test
    @DisplayName("removeDash ne produit rien si pas de tiret")
    void testRemoveDashNoDash() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.removeDash(dn, result);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("removeDash supprime tous les tirets en une seule fois")
    void testRemoveDashMultiple() throws Exception {
        DomainName dn = new DomainName("e-x-a-mple.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.removeDash(dn, result);

        assertEquals(1, result.size());
        assertEquals("example.com", result.get(0).toString());
    }
}
