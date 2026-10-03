package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour TLD — génération de variantes de TLD, thread-safety, TLD_LIST chargée.
 */
class TLDTest {

    @Test
    @DisplayName("La liste TLD est chargée et non vide (classpath ou filesystem)")
    void testTLDListLoadedAndNonEmpty() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        TLD.AddAndReplaceAllTLD(dn, result);

        // Si la liste TLD est chargée, on doit avoir des variantes
        assertFalse(result.isEmpty(), "Aucun TLD alternatif généré — TLD_LIST est peut-être vide");
    }

    @Test
    @DisplayName("Le TLD original 'com' n'est pas dans les variantes générées")
    void testOriginalTLDExcluded() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        TLD.AddAndReplaceAllTLD(dn, result);

        for (DomainName variant : result) {
            assertNotEquals("com", variant.getTLD(),
                    "Le TLD original 'com' ne doit pas être dans les variantes : " + variant);
        }
    }

    @Test
    @DisplayName("Aucune variante n'est dupliquée")
    void testNoDuplicates() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        TLD.AddAndReplaceAllTLD(dn, result);

        Set<String> unique = result.stream()
                .map(DomainName::toString)
                .collect(Collectors.toSet());

        assertEquals(result.size(), unique.size(),
                "Des domaines dupliqués ont été générés");
    }

    @Test
    @DisplayName("La partie SLD ('example') est préservée dans toutes les variantes")
    void testSLDPreserved() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        TLD.AddAndReplaceAllTLD(dn, result);

        for (DomainName variant : result) {
            assertEquals("example", variant.getDomain(),
                    "Le SLD a été modifié dans : " + variant);
        }
    }

    @Test
    @DisplayName("Les variantes sont des DomainName valides (pas d'exception à la construction)")
    void testAllVariantsAreValidDomainNames() throws Exception {
        DomainName dn = new DomainName("test.fr");
        ArrayList<DomainName> result = new ArrayList<>();
        // Si une variante invalide est construite, une RuntimeException est levée
        assertDoesNotThrow(() -> TLD.AddAndReplaceAllTLD(dn, result));
    }

    @Test
    @DisplayName("Thread-safety : appels concurrents ne produisent pas d'exception")
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
                    "Erreur dans le thread " + i + " : " + errors[i]);
        }
    }
}
