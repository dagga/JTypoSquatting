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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
            mvc.perform(get("/api/scans")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/scans/some-id/events")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Un utilisateur connecté accède à l'API et à son profil")
        void testAuthenticatedUser() throws Exception {
            mvc.perform(get("/api/scans").with(oidcLogin())).andExpect(status().isOk());
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
            mvc.perform(post("/api/scans").with(oidcLogin())
                            .contentType("application/json").content("{\"baseDomain\":\"example.com\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/scans").with(oidcLogin()).with(csrf())
                            .contentType("application/json").content("{\"baseDomain\":\"example.com\"}"))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("La déconnexion renvoie l'URL de fin de session en JSON (pas de redirection)")
        void testJsonLogout() throws Exception {
            // Enregistrement client de test inconnu du fournisseur : repli sur l'URL par défaut
            mvc.perform(post("/logout").with(oidcLogin()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.logoutUrl").value("/"));
            mvc.perform(post("/logout").with(oidcLogin())).andExpect(status().isForbidden());
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
            mvc.perform(options("/api/scans")
                            .header("Origin", "https://evil.example")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            mvc.perform(get("/api/scans").with(oidcLogin()).header("Origin", "http://localhost:8080"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Nested
    @SpringBootTest(properties = {"jtypo.rate-limit.requests-per-minute=3", "jtypo.quota.scans-per-day=1"})
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class AbuseProtection {

        @Autowired
        private MockMvc mvc;

        @Test
        @DisplayName("Au-delà du débit autorisé, l'API répond 429 avec Retry-After")
        void testRateLimit() throws Exception {
            for (int i = 0; i < 3; i++) {
                mvc.perform(get("/api/me").with(oidcLogin().idToken(t -> t.subject("rate-user"))))
                        .andExpect(status().isOk());
            }
            mvc.perform(get("/api/me").with(oidcLogin().idToken(t -> t.subject("rate-user"))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"));
            // Le compteur est propre à chaque utilisateur
            mvc.perform(get("/api/me").with(oidcLogin().idToken(t -> t.subject("other-user"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Le quota d'analyses répond 429")
        void testScanQuota() throws Exception {
            mvc.perform(post("/api/scans").with(oidcLogin().idToken(t -> t.subject("quota-user"))).with(csrf())
                            .contentType("application/json").content("{\"baseDomain\":\"example.com\"}"))
                    .andExpect(status().isAccepted());
            mvc.perform(post("/api/scans").with(oidcLogin().idToken(t -> t.subject("quota-user"))).with(csrf())
                            .contentType("application/json").content("{\"baseDomain\":\"example.org\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Daily")));
        }

        @Test
        @DisplayName("Les anciens endpoints du mode bureau n'existent pas en mode web")
        void testLegacyEndpointsAbsent() throws Exception {
            mvc.perform(get("/api/data/all").with(oidcLogin())).andExpect(status().isNotFound());
            mvc.perform(get("/api/cancel").with(oidcLogin())).andExpect(status().isNotFound());
            mvc.perform(get("/api/generate-and-check").param("domain", "example.com").with(oidcLogin()))
                    .andExpect(status().isNotFound());
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
