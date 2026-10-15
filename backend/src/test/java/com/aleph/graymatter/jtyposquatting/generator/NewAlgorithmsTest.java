package com.aleph.graymatter.jtyposquatting.generator;

import com.aleph.graymatter.jtyposquatting.JTypoSquatting;
import com.aleph.graymatter.jtyposquatting.constants.Const;
import com.aleph.graymatter.jtyposquatting.net.DomainName;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires des algorithmes de génération ajoutés (omission, clavier, points/tirets, mots, suffixes).
 */
class NewAlgorithmsTest {

    private static Set<String> generate(Algorithm algorithm, String domain) throws Exception {
        List<DomainName> result = new ArrayList<>();
        algorithm.generate(new DomainName(domain), result);
        return result.stream().map(DomainName::toString).collect(Collectors.toSet());
    }

    // Un domaine d'exemple adapté à chaque algorithme (certains ne s'appliquent qu'à des cas précis)
    private static final Map<Algorithm, String> SAMPLE_DOMAINS = Map.of(
            Algorithm.STRIP_DASH, "my-example.com",
            Algorithm.DOUBLE_REPLACEMENT, "google.com",
            Algorithm.HOMOPHONES, "fourseasons.com",
            Algorithm.WRONG_SLD, "example.co.uk",
            Algorithm.NUMERAL_SWAP, "shop4you.com",
            Algorithm.COMMON_MISSPELLING, "absence.com",
            Algorithm.MISSING_DOT, "www.example.com",
            Algorithm.CHANGE_DOT_DASH, "www.example.com"
    );

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    @DisplayName("Chaque algorithme déclaré produit au moins une variante, sans le domaine d'origine")
    void testEveryAlgorithmGenerates(Algorithm algorithm) throws Exception {
        String domain = SAMPLE_DOMAINS.getOrDefault(algorithm, "example.com");
        Set<String> result = generate(algorithm, domain);
        assertFalse(result.isEmpty(), algorithm.getId() + " n'a rien généré pour " + domain);
        assertFalse(result.contains(domain), algorithm.getId() + " a renvoyé le domaine d'origine");
    }

    @Test
    @DisplayName("Les identifiants d'algorithmes sont uniques")
    void testAlgorithmIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (Algorithm algorithm : Algorithm.values()) {
            assertTrue(ids.add(algorithm.getId()), "Identifiant dupliqué : " + algorithm.getId());
        }
        assertEquals(21, ids.size());
    }

    @Test
    @DisplayName("omission, répétition, inversion")
    void testCharacterEdits() throws Exception {
        assertTrue(generate(Algorithm.OMISSION, "example.com").contains("exmple.com"));
        assertTrue(generate(Algorithm.REPETITION, "example.com").contains("exxample.com"));
        assertTrue(generate(Algorithm.CHANGE_ORDER, "example.com").contains("exmaple.com"));
        assertTrue(generate(Algorithm.ADDITION, "example.com").contains("examplea.com"));
        assertTrue(generate(Algorithm.VOWEL_SWAP, "example.com").contains("exampla.com"));
    }

    @Test
    @DisplayName("Les variantes conservent le sous-domaine et le suffixe public (co.uk)")
    void testPartsArePreserved() throws Exception {
        Set<String> result = generate(Algorithm.OMISSION, "www.example.co.uk");
        assertTrue(result.contains("www.exmple.co.uk"), result.toString());
        for (String variant : result) {
            assertTrue(variant.startsWith("www.") && variant.endsWith(".co.uk"), variant);
        }
    }

    @Test
    @DisplayName("Remplacement par touche adjacente QWERTY et AZERTY")
    void testKeyboardReplacement() throws Exception {
        Set<String> result = generate(Algorithm.REPLACEMENT, "example.com");
        assertTrue(result.contains("wxample.com"), "QWERTY : e → w");
        assertTrue(result.contains("ezample.com"), "AZERTY/QWERTY : x → z");
        assertTrue(generate(Algorithm.REPLACEMENT, "amazon.com").contains("qmazon.com"), "AZERTY : a → q");
        assertTrue(generate(Algorithm.DOUBLE_REPLACEMENT, "google.com").contains("gppgle.com"));
    }

    @Test
    @DisplayName("Points et tirets")
    void testDotDash() throws Exception {
        assertTrue(generate(Algorithm.MISSING_DOT, "www.example.com").contains("wwwexample.com"));
        assertTrue(generate(Algorithm.MISSING_DOT, "example.co.uk").contains("exampleco.uk"));
        assertTrue(generate(Algorithm.SUBDOMAIN, "example.com").contains("ex.ample.com"));
        assertTrue(generate(Algorithm.CHANGE_DOT_DASH, "www.example.com").contains("www-example.com"));
        // Supprimer le seul point produirait un nom sans TLD : rien de valide
        assertTrue(generate(Algorithm.MISSING_DOT, "example.com").isEmpty());
        // L'extension collée au libellé ne donne pas un domaine enregistrable
        assertFalse(generate(Algorithm.MISSING_DOT, "www.example.com").contains("www.examplecom"));
        assertFalse(generate(Algorithm.CHANGE_DOT_DASH, "www.example.com").contains("www.example-com"));
        assertTrue(generate(Algorithm.CHANGE_DOT_DASH, "example.co.uk").contains("example-co.uk"));
    }

    @Test
    @DisplayName("Homophones, singulier/pluriel, chiffres en lettres")
    void testWords() throws Exception {
        assertTrue(generate(Algorithm.HOMOPHONES, "fourseasons.com").contains("forseasons.com"));
        assertTrue(generate(Algorithm.SINGULAR_PLURALIZE, "example.com").contains("examples.com"));
        assertTrue(generate(Algorithm.SINGULAR_PLURALIZE, "examples.com").contains("example.com"));
        Set<String> numerals = generate(Algorithm.NUMERAL_SWAP, "shop4you.com");
        assertTrue(numerals.contains("shopfouryou.com"));
        assertTrue(numerals.contains("shopforyou.com"));
        assertTrue(generate(Algorithm.NUMERAL_SWAP, "oneclick.com").contains("1click.com"));
    }

    @Test
    @DisplayName("Mauvais SLD, ajout de TLD, DNS dynamique")
    void testSuffixes() throws Exception {
        Set<String> sld = generate(Algorithm.WRONG_SLD, "example.co.uk");
        assertTrue(sld.contains("example.org.uk"), sld.toString());
        assertFalse(sld.contains("example.co.uk"));
        assertTrue(generate(Algorithm.WRONG_SLD, "example.com").isEmpty(), "Pas de SLD à remplacer");

        assertTrue(generate(Algorithm.ADD_TLD, "example.com").contains("example.com.br"));
        assertTrue(generate(Algorithm.ADD_DYNAMIC_DNS, "www.example.com").contains("example.duckdns.org"));
    }

    @Test
    @DisplayName("Les fournisseurs DNS dynamiques retenus figurent dans dynamic-dns.json")
    void testDynamicDnsProvidersAreKnown() throws Exception {
        Set<String> known = new HashSet<>();
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("dynamic-dns.json"), StandardCharsets.UTF_8)) {
            for (JsonElement e : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("list")) {
                known.add(e.getAsString());
            }
        }
        for (String provider : Const.DYNAMIC_DNS_PROVIDERS) {
            assertTrue(known.contains("." + provider), provider + " absent de dynamic-dns.json");
        }
    }

    @Test
    @DisplayName("JTypoSquatting agrège tous les algorithmes sans doublon ni domaine d'origine")
    void testAggregation() throws Exception {
        List<String> domains = new JTypoSquatting("example.com").getListOfDomains();
        assertEquals(domains.size(), new HashSet<>(domains).size(), "Doublons détectés");
        assertFalse(domains.contains("example.com"));
        assertTrue(domains.contains("exmple.com"));
        assertTrue(domains.contains("example.duckdns.org"));
        assertTrue(domains.contains("example.fr"));
    }
}
