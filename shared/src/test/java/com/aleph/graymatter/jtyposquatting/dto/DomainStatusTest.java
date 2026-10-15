package com.aleph.graymatter.jtyposquatting.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of the DomainStatus enum.
 */
class DomainStatusTest {

    @Test
    @DisplayName("Each status has a readable label")
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
    @DisplayName("fromString() resolves the known labels (case-insensitive)")
    void testFromStringKnown() {
        assertEquals(DomainStatus.ACTIVE, DomainStatus.fromString("Active"));
        assertEquals(DomainStatus.ACTIVE, DomainStatus.fromString("active"));
        assertEquals(DomainStatus.ACTIVE, DomainStatus.fromString("ACTIVE"));
        assertEquals(DomainStatus.SUSPICIOUS, DomainStatus.fromString("Suspicious"));
        assertEquals(DomainStatus.TESTING, DomainStatus.fromString("Testing..."));
        assertEquals(DomainStatus.UNREACHABLE, DomainStatus.fromString("unreachable"));
    }

    @Test
    @DisplayName("fromString() returns UNKNOWN for unknown values")
    void testFromStringUnknown() {
        assertEquals(DomainStatus.UNKNOWN, DomainStatus.fromString("invalid"));
        assertEquals(DomainStatus.UNKNOWN, DomainStatus.fromString(""));
        assertEquals(DomainStatus.UNKNOWN, DomainStatus.fromString("random_text"));
    }

    @Test
    @DisplayName("Expected number of enum constants")
    void testEnumValues() {
        assertEquals(7, DomainStatus.values().length);
    }
}
