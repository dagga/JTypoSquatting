package com.aleph.graymatter.jtyposquatting.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour l'énumération DomainStatus.
 */
class DomainStatusTest {

    @Test
    @DisplayName("Chaque statut possède un label lisible")
    void testLabels() {
        assertEquals("Active", DomainStatus.ACTIVE.getLabel());
        assertEquals("Dead", DomainStatus.DEAD.getLabel());
        assertEquals("Unreachable", DomainStatus.UNREACHABLE.getLabel());
        assertEquals("Suspicious", DomainStatus.SUSPICIOUS.getLabel());
        assertEquals("Safe", DomainStatus.SAFE.getLabel());
        assertEquals("Testing...", DomainStatus.TESTING.getLabel());
        assertEquals("Unknown", DomainStatus.UNKNOWN.getLabel());
    }

    @Test
    @DisplayName("fromString() résout les labels connus (insensible à la casse)")
    void testFromStringKnown() {
        assertEquals(DomainStatus.ACTIVE, DomainStatus.fromString("Active"));
        assertEquals(DomainStatus.ACTIVE, DomainStatus.fromString("active"));
        assertEquals(DomainStatus.ACTIVE, DomainStatus.fromString("ACTIVE"));
        assertEquals(DomainStatus.SUSPICIOUS, DomainStatus.fromString("Suspicious"));
        assertEquals(DomainStatus.TESTING, DomainStatus.fromString("Testing..."));
        assertEquals(DomainStatus.UNREACHABLE, DomainStatus.fromString("unreachable"));
    }

    @Test
    @DisplayName("fromString() retourne UNKNOWN pour les valeurs inconnues")
    void testFromStringUnknown() {
        assertEquals(DomainStatus.UNKNOWN, DomainStatus.fromString("invalid"));
        assertEquals(DomainStatus.UNKNOWN, DomainStatus.fromString(""));
        assertEquals(DomainStatus.UNKNOWN, DomainStatus.fromString("random_text"));
    }

    @Test
    @DisplayName("Nombre correct de constantes d'énumération")
    void testEnumValues() {
        assertEquals(7, DomainStatus.values().length);
    }
}
