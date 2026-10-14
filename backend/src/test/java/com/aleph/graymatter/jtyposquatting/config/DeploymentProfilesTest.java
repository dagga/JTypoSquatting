package com.aleph.graymatter.jtyposquatting.config;

import com.aleph.graymatter.jtyposquatting.JTypoSquattingApplication;
import com.aleph.graymatter.jtyposquatting.scan.ScanWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Profils de déploiement : instances API (sans worker) et workers (sans serveur web), même artefact.
 */
class DeploymentProfilesTest {

    private static ConfigurableApplicationContext start(String profile) {
        return new SpringApplicationBuilder(JTypoSquattingApplication.class)
                .profiles("test", profile)
                .properties("server.port=0", "jtypo.worker.poll-interval-ms=3600000")
                .run();
    }

    @Test
    @DisplayName("Profil worker : traite les analyses, sans serveur web ni sécurité HTTP")
    void testWorkerProfile() {
        try (ConfigurableApplicationContext context = start("worker")) {
            assertFalse(context instanceof WebServerApplicationContext);
            assertEquals(1, context.getBeansOfType(ScanWorker.class).size());
            assertTrue(context.getBeansOfType(SecurityFilterChain.class).isEmpty());
        }
    }

    @Test
    @DisplayName("Profil api : sert l'API, sans worker")
    void testApiProfile() {
        try (ConfigurableApplicationContext context = start("api")) {
            assertInstanceOf(WebServerApplicationContext.class, context);
            assertTrue(context.getBeansOfType(ScanWorker.class).isEmpty());
            assertFalse(context.getBeansOfType(SecurityFilterChain.class).isEmpty());
        }
    }
}
