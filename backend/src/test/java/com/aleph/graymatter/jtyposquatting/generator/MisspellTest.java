package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.net.DomainName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour Misspell — vérification que le correctif KeysValuesSwapMulti
 * préserve TOUTES les fautes pour un même mot cible (régression Point 6).
 */
class MisspellTest {

    private static Map<String, List<String>> misspellingsMap;

    @BeforeAll
    static void loadMap() {
        // Charge le dictionnaire une seule fois pour tous les tests
        misspellingsMap = Misspell.loadMisspellings();
    }

    // ---------------------------------------------------------------
    // Tests sur le dictionnaire chargé
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Le dictionnaire est non-null et non-vide")
    void testDictionaryNotEmpty() {
        assertNotNull(misspellingsMap);
        assertFalse(misspellingsMap.isEmpty(),
                "Le dictionnaire de fautes ne doit pas être vide");
    }

    @Test
    @DisplayName("Chaque clé mappe vers une liste non-vide de fautes")
    void testEachKeyHasNonEmptyList() {
        for (Map.Entry<String, List<String>> e : misspellingsMap.entrySet()) {
            assertNotNull(e.getValue(),
                    "Liste null pour la clé : " + e.getKey());
            assertFalse(e.getValue().isEmpty(),
                    "Liste vide pour la clé : " + e.getKey());
        }
    }

    @Test
    @DisplayName("Point 6 — Les listes contiennent plusieurs entrées si le JSON original en avait")
    void testMultipleMisspellingsPreserved() {
        // On cherche un mot dont le dictionnaire contient au moins 2 fautes
        // (vérifie que KeysValuesSwapMulti ne tronque pas les doublons)
        long wordsWithMultipleMisspellings = misspellingsMap.values().stream()
                .filter(list -> list.size() > 1)
                .count();

        // Dans le dictionnaire commun-misspellings, de nombreux mots ont plusieurs fautes
        assertTrue(wordsWithMultipleMisspellings > 0,
                "Aucun mot ne contient plusieurs fautes dans le dictionnaire inversé — "
                        + "KeysValuesSwapMulti a peut-être tronqué les données");
    }

    @Test
    @DisplayName("Aucune faute n'est dupliquée dans la liste pour une même clé")
    void testNoDuplicateMisspellings() {
        for (Map.Entry<String, List<String>> e : misspellingsMap.entrySet()) {
            List<String> values = e.getValue();
            long distinctCount = values.stream().distinct().count();
            assertEquals(values.size(), distinctCount,
                    "Des fautes dupliquées existent pour la clé '" + e.getKey() + "' : " + values);
        }
    }

    // ---------------------------------------------------------------
    // Tests sur AddMisspelledDomains
    // ---------------------------------------------------------------

    @Test
    @DisplayName("AddMisspelledDomains retourne des variantes pour un domaine contenant un mot du dictionnaire")
    void testAddMisspelledDomainsProducesVariants() throws Exception {
        // "the" est souvent dans le dictionnaire de fautes
        // On cherche un mot clé présent dans la map
        String targetWord = misspellingsMap.keySet().iterator().next();
        DomainName dn = new DomainName(targetWord + ".com");

        ArrayList<DomainName> result = new ArrayList<>();
        Misspell.AddMisspelledDomains(dn, result);

        assertFalse(result.isEmpty(),
                "Aucune variante générée pour le domaine contenant '" + targetWord + "'");
    }

    @Test
    @DisplayName("AddMisspelledDomains retourne liste vide pour un domaine sans correspondance")
    void testAddMisspelledDomainsEmptyForUnknownDomain() throws Exception {
        // "zzzzzzz" est très improbable dans le dictionnaire
        DomainName dn = new DomainName("zzzzzzz.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Misspell.AddMisspelledDomains(dn, result);

        assertTrue(result.isEmpty(),
                "Des variantes ont été générées pour un domaine inconnu");
    }

    @Test
    @DisplayName("AddMisspelledDomains — aucun doublon dans les résultats")
    void testAddMisspelledDomainsNoDuplicates() throws Exception {
        String targetWord = misspellingsMap.keySet().iterator().next();
        DomainName dn = new DomainName(targetWord + ".com");

        ArrayList<DomainName> result = new ArrayList<>();
        Misspell.AddMisspelledDomains(dn, result);

        List<String> strings = result.stream().map(DomainName::toString).collect(Collectors.toList());
        long distinct = strings.stream().distinct().count();
        assertEquals(strings.size(), distinct, "Des doublons ont été générés");
    }

    @Test
    @DisplayName("AddMisspelledDomains — le TLD original est préservé dans les variantes")
    void testAddMisspelledDomainsTLDPreserved() throws Exception {
        String targetWord = misspellingsMap.keySet().iterator().next();
        DomainName dn = new DomainName(targetWord + ".fr");

        ArrayList<DomainName> result = new ArrayList<>();
        Misspell.AddMisspelledDomains(dn, result);

        for (DomainName variant : result) {
            assertEquals("fr", variant.getTLD(),
                    "Le TLD a changé dans la variante : " + variant);
        }
    }
}
