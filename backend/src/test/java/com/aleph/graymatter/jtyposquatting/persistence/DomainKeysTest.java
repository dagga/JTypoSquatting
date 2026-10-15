package com.aleph.graymatter.jtyposquatting.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainKeysTest {

    @Test
    @DisplayName("Domains are stored in lower-case punycode, distinct for each homoglyph")
    void testKeys() {
        assertEquals("example.com", DomainKeys.toKey("Example.COM"));
        assertEquals("xn--exmple-cua.com", DomainKeys.toKey("exämple.com"));
        // "е" cyrillique
        assertNotEquals(DomainKeys.toKey("example.com"), DomainKeys.toKey("еxample.com"));
        assertEquals("exämple.com", DomainKeys.toUnicode("xn--exmple-cua.com"));
    }
}
