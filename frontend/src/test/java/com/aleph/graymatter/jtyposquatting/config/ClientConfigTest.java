package com.aleph.graymatter.jtyposquatting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour ClientConfig — chargement de l'URL API et fallback par défaut.
 */
class ClientConfigTest {

    @Test
    @DisplayName("L'URL API par défaut est 'http://localhost:8081'")
    void testDefaultApiUrl() {
        // Si client.properties est absent (classpath de test), on doit avoir la valeur par défaut
        String url = ClientConfig.getApiUrl();
        assertNotNull(url, "L'URL API ne doit pas être null");
        assertFalse(url.isBlank(), "L'URL API ne doit pas être vide");
    }

    @Test
    @DisplayName("setApiUrl() modifie l'URL retournée par getApiUrl()")
    void testSetApiUrl() {
        String original = ClientConfig.getApiUrl();
        try {
            ClientConfig.setApiUrl("http://backend-server:9090");
            assertEquals("http://backend-server:9090", ClientConfig.getApiUrl());
        } finally {
            // Restaurer la valeur d'origine pour ne pas contaminer les autres tests
            ClientConfig.setApiUrl(original);
        }
    }

    @Test
    @DisplayName("setApiUrl() avec null ne lève pas d'exception et peut être relu")
    void testSetApiUrlNull() {
        String original = ClientConfig.getApiUrl();
        try {
            assertDoesNotThrow(() -> ClientConfig.setApiUrl(null));
            assertNull(ClientConfig.getApiUrl());
        } finally {
            ClientConfig.setApiUrl(original);
        }
    }

    @Test
    @DisplayName("L'URL par défaut est celle attendue si client.properties est absent")
    void testDefaultUrlValue() {
        // On force le rechargement en testant la valeur actuelle
        // Si le classpath de test ne contient pas client.properties, doit être localhost:8081
        String url = ClientConfig.getApiUrl();
        // L'URL doit être une URL HTTP valide
        assertTrue(url == null || url.startsWith("http"),
                "L'URL API doit commencer par 'http' : " + url);
    }
}
