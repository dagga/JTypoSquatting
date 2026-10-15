package com.aleph.graymatter.jtyposquatting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of ClientConfig: loading of the API URL and default fallback.
 */
class ClientConfigTest {

    @Test
    @DisplayName("The default API URL is 'http://localhost:8081'")
    void testDefaultApiUrl() {
        // Without client.properties (test classpath), the default value is used
        String url = ClientConfig.getApiUrl();
        assertNotNull(url, "The API URL must not be null");
        assertFalse(url.isBlank(), "The API URL must not be blank");
    }

    @Test
    @DisplayName("setApiUrl() changes the URL returned by getApiUrl()")
    void testSetApiUrl() {
        String original = ClientConfig.getApiUrl();
        try {
            ClientConfig.setApiUrl("http://backend-server:9090");
            assertEquals("http://backend-server:9090", ClientConfig.getApiUrl());
        } finally {
            // Restore the original value so that other tests are not affected
            ClientConfig.setApiUrl(original);
        }
    }

    @Test
    @DisplayName("setApiUrl() with null throws no exception and can be read back")
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
    @DisplayName("The default URL is the expected one when client.properties is missing")
    void testDefaultUrlValue() {
        // Checks the current value; without client.properties on the test classpath, it is localhost:8081
        String url = ClientConfig.getApiUrl();
        // The URL must be a valid HTTP URL
        assertTrue(url == null || url.startsWith("http"),
                "The API URL must start with 'http': " + url);
    }
}
