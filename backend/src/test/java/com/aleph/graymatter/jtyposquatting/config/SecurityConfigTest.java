package com.aleph.graymatter.jtyposquatting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de la configuration de sécurité : mode OIDC (web public) et mode local (application de bureau).
 */
class SecurityConfigTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class OidcMode {

        @Autowired
        private MockMvc mvc;

        @Test
        @DisplayName("L'API refuse les appels non authentifiés avec 401 (pas de redirection)")
        void testApiRequiresAuthentication() throws Exception {
            mvc.perform(get("/api/data/all")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/generate-and-check").param("domain", "example.com")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Un utilisateur connecté accède à l'API et à son profil")
        void testAuthenticatedUser() throws Exception {
            mvc.perform(get("/api/data/all").with(oidcLogin())).andExpect(status().isOk());
            mvc.perform(get("/api/me").with(oidcLogin().idToken(token -> token
                            .subject("user-1")
                            .claim("preferred_username", "alice")
                            .claim("email", "alice@example.com"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("user-1"))
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(jsonPath("$.email").value("alice@example.com"));
        }

        @Test
        @DisplayName("Les requêtes modifiantes exigent un jeton CSRF")
        void testCsrfRequired() throws Exception {
            mvc.perform(delete("/api/data/all").with(oidcLogin())).andExpect(status().isForbidden());
            mvc.perform(delete("/api/data/all").with(oidcLogin()).with(csrf())).andExpect(status().isOk());
        }

        @Test
        @DisplayName("En-têtes de sécurité présents sur les réponses de l'API")
        void testSecurityHeaders() throws Exception {
            mvc.perform(get("/api/me").with(oidcLogin()))
                    .andExpect(header().string("Content-Security-Policy", SecurityConfig.API_CONTENT_SECURITY_POLICY))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }

        @Test
        @DisplayName("HSTS est envoyé sur les requêtes HTTPS")
        void testHsts() throws Exception {
            mvc.perform(get("/api/me").secure(true).with(oidcLogin()))
                    .andExpect(header().exists("Strict-Transport-Security"));
        }

        @Test
        @DisplayName("Aucune origine tierce n'est autorisée par CORS")
        void testNoCrossOrigin() throws Exception {
            mvc.perform(options("/api/data/all")
                            .header("Origin", "https://evil.example")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            mvc.perform(get("/api/data/all").with(oidcLogin()).header("Origin", "http://localhost:8080"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Nested
    @SpringBootTest(properties = "jtypo.security.mode=local")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class LocalMode {

        @Autowired
        private MockMvc mvc;

        @Test
        @DisplayName("Mode bureau : API accessible sans connexion, pas d'utilisateur courant")
        void testLocalModeIsOpen() throws Exception {
            mvc.perform(get("/api/data/all")).andExpect(status().isOk());
            mvc.perform(delete("/api/data/all")).andExpect(status().isOk());
            mvc.perform(get("/api/me")).andExpect(status().isNoContent());
        }
    }
}
