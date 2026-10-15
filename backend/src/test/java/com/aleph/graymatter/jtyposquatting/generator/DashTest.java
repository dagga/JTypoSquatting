package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of Dash: insertion and removal of dashes, and fix of the "leading dot" bug on domains
 * without a subdomain.
 */
class DashTest {

    // ---------------------------------------------------------------
    // addDash: domain without a subdomain
    // ---------------------------------------------------------------

    @Test
    @DisplayName("addDash generates N-1 variants for a domain of N characters")
    void testAddDashCountVariants() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.addDash(dn, result);

        // "example" has 7 characters → 6 possible insertion positions
        assertEquals(6, result.size());
    }

    @Test
    @DisplayName("addDash never generates a domain starting with a dot (leading dot regression)")
    void testAddDashNoLeadingDot() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.addDash(dn, result);

        for (DomainName variant : result) {
            String s = variant.toString();
            assertFalse(s.startsWith("."),
                    "The domain must not start with a dot: " + s);
            assertTrue(s.endsWith(".com"),
                    "The domain must end with .com: " + s);
        }
    }

    @Test
    @DisplayName("addDash with a subdomain: the subdomain is preserved")
    void testAddDashPreservesSubdomain() throws Exception {
        DomainName dn = new DomainName("www.example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.addDash(dn, result);

        // Every result must start with "www."
        for (DomainName variant : result) {
            String s = variant.toString();
            assertTrue(s.startsWith("www."),
                    "Sous-domaine 'www' absent : " + s + " Original: " + dn);
        }
    }

    @Test
    @DisplayName("addDash produces the expected variants for 'abc.com'")
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
    @DisplayName("removeDash removes the dash and produces the domain without it")
    void testRemoveDash() throws Exception {
        DomainName dn = new DomainName("ex-ample.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.removeDash(dn, result);

        assertEquals(1, result.size());
        assertEquals("example.com", result.get(0).toString());
    }

    @Test
    @DisplayName("removeDash produces nothing when there is no dash")
    void testRemoveDashNoDash() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.removeDash(dn, result);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("removeDash removes all dashes at once")
    void testRemoveDashMultiple() throws Exception {
        DomainName dn = new DomainName("e-x-a-mple.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Dash.removeDash(dn, result);

        assertEquals(1, result.size());
        assertEquals("example.com", result.get(0).toString());
    }
}
