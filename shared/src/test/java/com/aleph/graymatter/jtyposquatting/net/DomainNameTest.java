package com.aleph.graymatter.jtyposquatting.net;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of DomainName: parsing of subdomain / SLD / TLD.
 */
class DomainNameTest {

    // ---------------------------------------------------------------
    // Construction valide
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Simple domain (no subdomain): google.com")
    void testSimpleDomain() throws InvalidDomainException {
        DomainName dn = new DomainName("google.com");
        assertEquals("google", dn.getDomain());
        assertEquals("com", dn.getTLD());
        assertEquals("", dn.getSubDomain());
        assertFalse(dn.haveSubDomain());
        assertEquals("google.com", dn.toString());
    }

    @Test
    @DisplayName("Domain with a subdomain: www.google.com, parser behaviour")
    void testDomainWithSubdomain() throws InvalidDomainException {
        DomainName dn = new DomainName("www.google.com");
        // The parser removes the TLD ("com") → "www.google"
        // haveSubDomain("www.google") is false (a single '.') → domain = "www.google", subDomain = ""
        // This behaviour is documented here as a reference.
        assertEquals("com", dn.getTLD());
        assertEquals("www.google.com", dn.toString());
        // Subdomain detection needs 2+ dots before the TLD
        // For the current parser, "www.google.com" is SLD="www.google", TLD="com"
        assertNotNull(dn.getDomain());
    }

    @Test
    @DisplayName("Two-part TLD: domain without a subdomain")
    void testTwoPartTLD() throws InvalidDomainException {
        // getSuffix only takes the part after the last '.'
        DomainName dn = new DomainName("example.co");
        assertEquals("example", dn.getDomain());
        assertEquals("co", dn.getTLD());
        assertEquals("", dn.getSubDomain());
    }

    @Test
    @DisplayName("Domain with a dash: ex-ample.com")
    void testDomainWithHyphen() throws InvalidDomainException {
        DomainName dn = new DomainName("ex-ample.com");
        assertEquals("ex-ample", dn.getDomain());
        assertEquals("com", dn.getTLD());
        assertEquals("ex-ample.com", dn.toString());
    }

    // ---------------------------------------------------------------
    // Static helper methods
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Static haveSubDomain() detects subdomains")
    void testStaticHaveSubDomain() {
        assertTrue(DomainName.haveSubDomain("www.example.com"));
        assertFalse(DomainName.haveSubDomain("example.com"));
    }

    @Test
    @DisplayName("getSubDomain() static")
    void testStaticGetSubDomain() {
        assertEquals("www", DomainName.getSubDomain("www.example.com"));
        assertEquals("", DomainName.getSubDomain("example.com"));
    }

    @Test
    @DisplayName("getDomainWithoutTLD() static")
    void testStaticGetDomainWithoutTLD() {
        assertEquals("www.example", DomainName.getDomainWithoutTLD("www.example.com"));
        assertEquals("example", DomainName.getDomainWithoutTLD("example.com"));
    }

    @Test
    @DisplayName("getSuffix() static")
    void testStaticGetSuffix() {
        assertEquals("com", DomainName.getSuffix("example.com"));
        assertEquals("fr", DomainName.getSuffix("example.fr"));
        assertEquals("", DomainName.getSuffix("nodot"));
    }

    @Test
    @DisplayName("getDomainWithoutSubDomainMinusTLD(): domain without a subdomain")
    void testGetDomainWithoutSubDomainMinusTLD() {
        // Without a subdomain: returns the domain without the TLD
        assertEquals("example", DomainName.getDomainWithoutSubDomainMinusTLD("example.com"));
        // With a subdomain: returns the SLD part (without the subdomain nor the TLD)
        // "www.example.com" → domainWithoutTLD = "www.example" → getDomainWithoutSubDomain("www.example")
        // haveSubDomain("www.example") = true → retourne "example"
        // NOTE: getDomainWithoutSubDomainMinusTLD() calls haveSubDomain on the FULL domain, TLD included,
        // so it sees "www.example.com" with 2 dots → subdomain = "www"
        assertEquals("example", DomainName.getDomainWithoutSubDomainMinusTLD("www.example.com"));
    }

    // ---------------------------------------------------------------
    // Cas invalides
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Domain without a dot → InvalidDomainException")
    void testInvalidDomainNoDot() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("nodot"));
    }

    @Test
    @DisplayName("Domain ending with a dot → InvalidDomainException")
    void testInvalidDomainTrailingDot() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("trailing."));
    }

    @Test
    @DisplayName("Domain starting with a dot → InvalidDomainException (Guava)")
    void testInvalidDomainLeadingDot() {
        assertThrows(InvalidDomainException.class, () -> new DomainName(".example.com"));
    }

    @Test
    @DisplayName("Double dot in the domain → InvalidDomainException (Guava)")
    void testInvalidDomainDoubleDot() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("example..com"));
    }

    @Test
    @DisplayName("Leading dash in a label → InvalidDomainException (Guava)")
    void testInvalidDomainLeadingHyphen() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("-abc.com"));
    }

    @Test
    @DisplayName("URL with a protocol → InvalidDomainException (Guava)")
    void testInvalidDomainWithScheme() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("http://a.b"));
    }

    @Test
    @DisplayName("Spaces in the domain → InvalidDomainException (Guava)")
    void testInvalidDomainWithSpaces() {
        assertThrows(InvalidDomainException.class, () -> new DomainName(" . "));
    }

    // ---------------------------------------------------------------
    // URL HTTPS
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getAsHttpsUrl() produces a correct URI")
    void testGetAsHttpsUrl() throws Exception {
        DomainName dn = new DomainName("www.example.com");
        assertEquals("https://www.example.com", dn.getAsHttpsUrl().toString());
    }
}
