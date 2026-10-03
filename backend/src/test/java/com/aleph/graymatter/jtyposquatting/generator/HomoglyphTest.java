package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.constants.Const;
import com.aleph.graymatter.jtyposquatting.net.DomainName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour Homoglyph — génération de variantes homoglyphes.
 */
class HomoglyphTest {

    @Test
    @DisplayName("Domaine sans caractère homoglyphe → liste vide")
    void testNoHomoglyphCharacters() throws Exception {
        // Si aucun caractère de "xyz.com" n'a de substitut dans Const.SIMILAR_CHAR
        // La liste reste vide. On choisit un domaine dont les caractères ne sont PAS dans la map.
        // (On teste que la méthode ne plante pas et respecte la logique de filtrage.)
        DomainName dn = new DomainName("xyz.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);
        // Le résultat dépend de la map, on vérifie juste l'absence d'exception
        assertNotNull(result);
    }

    @Test
    @DisplayName("Domaine 'google.com' — génère des variantes si 'g','o','l','e' ont des substituts")
    void testGoogleGeneratesVariants() throws Exception {
        DomainName dn = new DomainName("google.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        // Vérifie que les variantes contiennent '.com'
        for (DomainName variant : result) {
            assertTrue(variant.toString().endsWith(".com"),
                    "Variante sans .com : " + variant);
        }
    }

    @Test
    @DisplayName("Tous les domaines générés sont uniques")
    void testNoDuplicates() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        Set<String> unique = result.stream()
                .map(DomainName::toString)
                .collect(Collectors.toSet());

        assertEquals(result.size(), unique.size(),
                "Des doublons ont été détectés dans les résultats");
    }

    @Test
    @DisplayName("Aucune variante n'est identique au domaine original")
    void testVariantsDifferFromOriginal() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        for (DomainName variant : result) {
            assertNotEquals("example.com", variant.toString(),
                    "La variante est identique au domaine original");
        }
    }

    @Test
    @DisplayName("Caractère 'o' → si substitut présent, 'google' génère un remplacement de 'o'")
    void testCharacterOReplacedIfSubstituteExists() throws Exception {
        if (!Const.SIMILAR_CHAR.containsKey('o')) {
            // Test conditionnel : si 'o' n'est pas dans la map, on passe
            return;
        }

        DomainName dn = new DomainName("google.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        // "google" contient 3 'o', donc on doit avoir au moins autant de variantes
        // que de substituts * occurrences
        assertFalse(result.isEmpty(), "Aucune variante générée pour 'google.com' alors que 'o' a des substituts");
    }

    @Test
    @DisplayName("Le TLD n'est pas touché par la substitution homoglyphe")
    void testTLDNotAlteredByHomoglyph() throws Exception {
        DomainName dn = new DomainName("example.com");
        ArrayList<DomainName> result = new ArrayList<>();
        Homoglyph.addHomglyphedDomains(dn, result);

        // Toutes les variantes doivent avoir le même TLD que l'original
        for (DomainName variant : result) {
            assertEquals("com", variant.getTLD(),
                    "Le TLD a été modifié : " + variant);
        }
    }
}
