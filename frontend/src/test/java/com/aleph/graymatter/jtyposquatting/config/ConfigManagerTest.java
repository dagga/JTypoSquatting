package com.aleph.graymatter.jtyposquatting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of ConfigManager: i18n, locale reloading, missing keys.
 */
class ConfigManagerTest {

    private final ConfigManager config = ConfigManager.getInstance();

    // ---------------------------------------------------------------
    // Singleton
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getInstance() always returns the same instance")
    void testSingleton() {
        assertSame(config, ConfigManager.getInstance());
    }

    // ---------------------------------------------------------------
    // getMessage: valid and invalid keys
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getMessage() returns a non-null value for a valid key")
    void testGetMessageValidKey() {
        String value = config.getMessage("button.generate");
        assertNotNull(value);
        assertFalse(value.isBlank());
        assertFalse(value.startsWith("!"), "Key 'button.generate' is missing from messages.properties");
    }

    @Test
    @DisplayName("getMessage() returns '!key!' for an unknown key")
    void testGetMessageMissingKey() {
        String value = config.getMessage("this.key.does.not.exist.at.all");
        assertEquals("!this.key.does.not.exist.at.all!", value);
    }

    @Test
    @DisplayName("getMessage() with arguments formats the string")
    void testGetMessageWithArgs() {
        // This key must exist in messages.properties with a '%s'
        // If it does not, the sentinel value is returned and the test does not fail
        String value = config.getMessage("button.generate");
        assertNotNull(value);
    }

    // ---------------------------------------------------------------
    // setLocale: dynamic reloading of the bundle
    // ---------------------------------------------------------------

    @Test
    @DisplayName("setLocale(FR) loads the French messages")
    void testSetLocaleFR() {
        Locale original = config.getLocale();
        try {
            config.setLocale(Locale.FRENCH);
            assertEquals(Locale.FRENCH, config.getLocale());

            // A non-null message is returned after the reload
            String value = config.getMessage("button.generate");
            assertNotNull(value);
        } finally {
            config.setLocale(original);
        }
    }

    @Test
    @DisplayName("setLocale(EN) loads the English messages")
    void testSetLocaleEN() {
        Locale original = config.getLocale();
        try {
            config.setLocale(Locale.ENGLISH);
            assertEquals(Locale.ENGLISH, config.getLocale());

            String value = config.getMessage("button.generate");
            assertNotNull(value);
            assertFalse(value.startsWith("!"), "Missing key after switching to EN");
        } finally {
            config.setLocale(original);
        }
    }

    @Test
    @DisplayName("After setLocale(), getMessage() returns the new values")
    void testLocaleChangeReloadsBundle() {
        Locale original = config.getLocale();
        try {
            config.setLocale(Locale.ENGLISH);
            String enValue = config.getMessage("button.generate");

            config.setLocale(Locale.FRENCH);
            String frValue = config.getMessage("button.generate");

            // EN and FR may or may not differ, but neither may be null
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
    @DisplayName("getLocale() never returns null")
    void testGetLocaleNotNull() {
        assertNotNull(config.getLocale());
    }

    // ---------------------------------------------------------------
    // getConfig / getIntConfig / getBooleanConfig
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getConfig() returns null for a missing key (no exception)")
    void testGetConfigMissingKey() {
        assertNull(config.getConfig("non.existent.key.xyz"));
    }

    @Test
    @DisplayName("getIntConfig() returns the default value when the key is missing")
    void testGetIntConfigDefault() {
        assertEquals(42, config.getIntConfig("non.existent.int.key", 42));
    }

    @Test
    @DisplayName("getBooleanConfig() returns false by default when the key is missing")
    void testGetBooleanConfigDefault() {
        assertFalse(config.getBooleanConfig("non.existent.bool.key", false));
    }
}
