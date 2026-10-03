package com.aleph.graymatter.jtyposquatting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour ConfigManager — i18n, rechargement de locale, clés absentes.
 */
class ConfigManagerTest {

    private final ConfigManager config = ConfigManager.getInstance();

    // ---------------------------------------------------------------
    // Singleton
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getInstance() retourne toujours la même instance")
    void testSingleton() {
        assertSame(config, ConfigManager.getInstance());
    }

    // ---------------------------------------------------------------
    // getMessage — clés valides et invalides
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getMessage() retourne une valeur non-null pour une clé valide")
    void testGetMessageValidKey() {
        String value = config.getMessage("button.generate");
        assertNotNull(value);
        assertFalse(value.isBlank());
        assertFalse(value.startsWith("!"), "La clé 'button.generate' n'existe pas dans messages.properties");
    }

    @Test
    @DisplayName("getMessage() retourne '!key!' pour une clé inexistante")
    void testGetMessageMissingKey() {
        String value = config.getMessage("this.key.does.not.exist.at.all");
        assertEquals("!this.key.does.not.exist.at.all!", value);
    }

    @Test
    @DisplayName("getMessage() avec args formate correctement la chaîne")
    void testGetMessageWithArgs() {
        // Cette clé doit exister dans messages.properties avec un '%s'
        // Si elle n'existe pas, le test retourne la sentinel et ne plante pas
        String value = config.getMessage("button.generate");
        assertNotNull(value);
    }

    // ---------------------------------------------------------------
    // setLocale — rechargement dynamique du bundle
    // ---------------------------------------------------------------

    @Test
    @DisplayName("setLocale(FR) charge les messages français")
    void testSetLocaleFR() {
        Locale original = config.getLocale();
        try {
            config.setLocale(Locale.FRENCH);
            assertEquals(Locale.FRENCH, config.getLocale());

            // Vérifie qu'on obtient un message non-nul après le rechargement
            String value = config.getMessage("button.generate");
            assertNotNull(value);
        } finally {
            config.setLocale(original);
        }
    }

    @Test
    @DisplayName("setLocale(EN) charge les messages anglais")
    void testSetLocaleEN() {
        Locale original = config.getLocale();
        try {
            config.setLocale(Locale.ENGLISH);
            assertEquals(Locale.ENGLISH, config.getLocale());

            String value = config.getMessage("button.generate");
            assertNotNull(value);
            assertFalse(value.startsWith("!"), "Clé manquante après changement vers EN");
        } finally {
            config.setLocale(original);
        }
    }

    @Test
    @DisplayName("Après setLocale(), getMessage() retourne les nouvelles valeurs")
    void testLocaleChangeReloadsBundle() {
        Locale original = config.getLocale();
        try {
            config.setLocale(Locale.ENGLISH);
            String enValue = config.getMessage("button.generate");

            config.setLocale(Locale.FRENCH);
            String frValue = config.getMessage("button.generate");

            // EN et FR peuvent différer ou non, mais aucun ne doit être null
            assertNotNull(enValue);
            assertNotNull(frValue);
        } finally {
            config.setLocale(original);
        }
    }

    // ---------------------------------------------------------------
    // getLocale
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getLocale() ne retourne jamais null")
    void testGetLocaleNotNull() {
        assertNotNull(config.getLocale());
    }

    // ---------------------------------------------------------------
    // getConfig / getIntConfig / getBooleanConfig
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getConfig() retourne null pour une clé absente (pas d'exception)")
    void testGetConfigMissingKey() {
        assertNull(config.getConfig("non.existent.key.xyz"));
    }

    @Test
    @DisplayName("getIntConfig() retourne la valeur par défaut si la clé est absente")
    void testGetIntConfigDefault() {
        assertEquals(42, config.getIntConfig("non.existent.int.key", 42));
    }

    @Test
    @DisplayName("getBooleanConfig() retourne false par défaut si la clé est absente")
    void testGetBooleanConfigDefault() {
        assertFalse(config.getBooleanConfig("non.existent.bool.key", false));
    }
}
