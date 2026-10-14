package com.aleph.graymatter.jtyposquatting.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API des analyses : création, cloisonnement par utilisateur, quotas persistants.
 */
@SpringBootTest(properties = {"jtypo.quota.max-concurrent-scans=2", "jtypo.quota.scans-per-day=3"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanApiTest {

    @Autowired
    private MockMvc mvc;

    private static OidcLoginRequestPostProcessor user(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("preferred_username", subject));
    }

    private String createScan(String subject, String domain) throws Exception {
        String location = mvc.perform(post("/api/scans").with(user(subject)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseDomain\":\"" + domain + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("Une analyse est mise en file (202) et stocke le domaine en punycode")
    void testCreateScan() throws Exception {
        mvc.perform(post("/api/scans").with(user("creator")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseDomain\":\"Exämple.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", startsWith("/api/scans/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.baseDomain").value("exämple.com"));
    }

    @Test
    @DisplayName("Un domaine invalide est refusé (400)")
    void testInvalidDomain() throws Exception {
        mvc.perform(post("/api/scans").with(user("invalid")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseDomain\":\"not a domain\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Un utilisateur ne voit ni les analyses ni les résultats d'un autre (403)")
    void testOwnership() throws Exception {
        String scanId = createScan("owner", "example.com");

        mvc.perform(get("/api/scans/" + scanId).with(user("owner"))).andExpect(status().isOk());
        mvc.perform(get("/api/scans/" + scanId).with(user("intruder"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/scans/" + scanId + "/results").with(user("intruder"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/scans/unknown-id").with(user("owner"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/scans").with(user("intruder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(scanId))));
    }

    @Test
    @DisplayName("Les quotas sont calculés depuis la base : analyses actives puis quota journalier")
    void testPersistentQuotas() throws Exception {
        createScan("quota", "example.com");
        createScan("quota", "example.org");
        // Deux analyses en attente : la troisième dépasse le nombre d'analyses simultanées
        mvc.perform(post("/api/scans").with(user("quota")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseDomain\":\"example.net\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error", containsString("simultaneous")));
    }

    @Test
    @DisplayName("Le quota journalier tient compte des analyses terminées")
    void testDailyQuota(@Autowired ScanRepository scans) throws Exception {
        for (String domain : new String[]{"a-example.com", "b-example.com", "c-example.com"}) {
            String id = createScan("daily", domain);
            scans.cancel(id, java.time.Instant.now());
        }
        mvc.perform(post("/api/scans").with(user("daily")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseDomain\":\"d-example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error", containsString("Daily")));
    }
}
