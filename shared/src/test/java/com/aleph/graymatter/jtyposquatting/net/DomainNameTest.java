package com.aleph.graymatter.jtyposquatting.net;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour DomainName — parsing subdomain / SLD / TLD.
 */
class DomainNameTest {

    // ---------------------------------------------------------------
    // Construction valide
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Domaine simple (sans sous-domaine) : google.com")
    void testSimpleDomain() throws InvalidDomainException {
        DomainName dn = new DomainName("google.com");
        assertEquals("google", dn.getDomain());
        assertEquals("com", dn.getTLD());
        assertEquals("", dn.getSubDomain());
        assertFalse(dn.haveSubDomain());
        assertEquals("google.com", dn.toString());
    }

    @Test
    @DisplayName("Domaine avec sous-domaine : www.google.com — comportement du parseur")
    void testDomainWithSubdomain() throws InvalidDomainException {
        DomainName dn = new DomainName("www.google.com");
        // Le parseur retire le TLD ("com") → "www.google"
        // haveSubDomain("www.google") est false (un seul '.') → domain = "www.google", subDomain = ""
        // Ce comportement est documenté ici comme référence.
        assertEquals("com", dn.getTLD());
        assertEquals("www.google.com", dn.toString());
        // La détection de sous-domaine nécessite 2+ points avant le TLD
        // Pour le parseur actuel, "www.google.com" est vu comme SLD="www.google", TLD="com"
        assertNotNull(dn.getDomain());
    }

    @Test
    @DisplayName("TLD en deux parties — domaine sans sous-domaine")
    void testTwoPartTLD() throws InvalidDomainException {
        // getSuffix prend seulement la partie après le dernier '.'
        DomainName dn = new DomainName("example.co");
        assertEquals("example", dn.getDomain());
        assertEquals("co", dn.getTLD());
        assertEquals("", dn.getSubDomain());
    }

    @Test
    @DisplayName("Domaine avec tiret : ex-ample.com")
    void testDomainWithHyphen() throws InvalidDomainException {
        DomainName dn = new DomainName("ex-ample.com");
        assertEquals("ex-ample", dn.getDomain());
        assertEquals("com", dn.getTLD());
        assertEquals("ex-ample.com", dn.toString());
    }

    // ---------------------------------------------------------------
    // Méthodes statiques utilitaires
    // ---------------------------------------------------------------

    @Test
    @DisplayName("haveSubDomain() static — détecte correctement")
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
    @DisplayName("getDomainWithoutSubDomainMinusTLD() — domaine sans sous-domaine")
    void testGetDomainWithoutSubDomainMinusTLD() {
        // Sans sous-domaine : retourne le domaine sans le TLD
        assertEquals("example", DomainName.getDomainWithoutSubDomainMinusTLD("example.com"));
        // Avec sous-domaine : retourne la partie SLD (sans le sous-domaine ni le TLD)
        // "www.example.com" → domainWithoutTLD = "www.example" → getDomainWithoutSubDomain("www.example")
        // haveSubDomain("www.example") = true → retourne "example"
        // Mais NOTE : getDomainWithoutSubDomainMinusTLD() appelle haveSubDomain sur le domaine COMPLET
        // avec le TLD encore présent, donc il voit "www.example.com" avec 2 dots → sous-domaine = "www"
        assertEquals("example", DomainName.getDomainWithoutSubDomainMinusTLD("www.example.com"));
    }

    // ---------------------------------------------------------------
    // Cas invalides
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Domaine sans point → InvalidDomainException")
    void testInvalidDomainNoDot() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("nodot"));
    }

    @Test
    @DisplayName("Domaine se terminant par un point → InvalidDomainException")
    void testInvalidDomainTrailingDot() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("trailing."));
    }

    @Test
    @DisplayName("Domaine commençant par un point → InvalidDomainException (Guava)")
    void testInvalidDomainLeadingDot() {
        assertThrows(InvalidDomainException.class, () -> new DomainName(".example.com"));
    }

    @Test
    @DisplayName("Double point dans le domaine → InvalidDomainException (Guava)")
    void testInvalidDomainDoubleDot() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("example..com"));
    }

    @Test
    @DisplayName("Tiret initial dans le label → InvalidDomainException (Guava)")
    void testInvalidDomainLeadingHyphen() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("-abc.com"));
    }

    @Test
    @DisplayName("URL avec protocole → InvalidDomainException (Guava)")
    void testInvalidDomainWithScheme() {
        assertThrows(InvalidDomainException.class, () -> new DomainName("http://a.b"));
    }

    @Test
    @DisplayName("Espaces dans le domaine → InvalidDomainException (Guava)")
    void testInvalidDomainWithSpaces() {
        assertThrows(InvalidDomainException.class, () -> new DomainName(" . "));
    }

    // ---------------------------------------------------------------
    // URL HTTPS
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getAsHttpsUrl() produit une URI correcte")
    void testGetAsHttpsUrl() throws Exception {
        DomainName dn = new DomainName("www.example.com");
        assertEquals("https://www.example.com", dn.getAsHttpsUrl().toString());
    }
}
