package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of TLD: TLD variants, thread safety, loading of TLD_LIST.
 */
class TLDTest {

    @Test
    @DisplayName("The TLD list is loaded and not empty (classpath or file system)")
    void testTLDListLoadedAndNonEmpty() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        TLD.AddAndReplaceAllTLD(dn, result);

        // When the TLD list is loaded, there must be variants
        assertFalse(result.isEmpty(), "No alternative TLD generated: TLD_LIST may be empty");
    }

    @Test
    @DisplayName("The original TLD 'com' is not among the generated variants")
    void testOriginalTLDExcluded() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        TLD.AddAndReplaceAllTLD(dn, result);

        for (DomainName variant : result) {
            assertNotEquals("com", variant.getTLD(),
                    "The original TLD 'com' must not be among the variants: " + variant);
        }
    }

    @Test
    @DisplayName("No variant is duplicated")
    void testNoDuplicates() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        TLD.AddAndReplaceAllTLD(dn, result);

        Set<String> unique = result.stream()
                .map(DomainName::toString)
                .collect(Collectors.toSet());

        assertEquals(result.size(), unique.size(),
                "Duplicate domains were generated");
    }

    @Test
    @DisplayName("The SLD part ('example') is kept in every variant")
    void testSLDPreserved() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        TLD.AddAndReplaceAllTLD(dn, result);

        for (DomainName variant : result) {
            assertEquals("example", variant.getDomain(),
                    "The SLD was changed in: " + variant);
        }
    }

    @Test
    @DisplayName("Variants are valid DomainName objects (no exception at construction)")
    void testAllVariantsAreValidDomainNames() throws Exception {
        DomainName dn = new DomainName("test.fr");
        ArrayList<DomainName> result = new ArrayList<>();
        // Building an invalid variant would throw a RuntimeException
        assertDoesNotThrow(() -> TLD.AddAndReplaceAllTLD(dn, result));
    }

    @Test
    @DisplayName("Thread safety: concurrent calls throw no exception")
    void testConcurrentCalls() throws Exception {
        DomainName dn = new DomainName("concurrent.com");

        int threadCount = 8;
        Thread[] threads = new Thread[threadCount];
        Throwable[] errors = new Throwable[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[i] = Thread.ofVirtual().start(() -> {
                try {
                    ArrayList<DomainName> res = new ArrayList<>();
                    TLD.AddAndReplaceAllTLD(dn, res);
                } catch (Throwable t) {
                    errors[idx] = t;
                }
            });
        }

        for (Thread t : threads) {
            t.join(5000);
        }

        for (int i = 0; i < threadCount; i++) {
            assertNull(errors[i],
                    "Error in thread " + i + ": " + errors[i]);
        }
    }
}
