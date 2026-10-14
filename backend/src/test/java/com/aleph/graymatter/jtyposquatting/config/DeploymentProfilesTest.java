package com.aleph.graymatter.jtyposquatting.config;

import com.aleph.graymatter.jtyposquatting.JTypoSquattingApplication;
import com.aleph.graymatter.jtyposquatting.controller.UserController;
import com.aleph.graymatter.jtyposquatting.retention.RetentionService;
import com.aleph.graymatter.jtyposquatting.scan.ScanController;
import com.aleph.graymatter.jtyposquatting.scan.ScanWorker;
import com.aleph.graymatter.jtyposquatting.storage.ScreenshotController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Profils de déploiement : instances API (sans worker) et workers (sans API), même artefact.
 */
class DeploymentProfilesTest {

    private static ConfigurableApplicationContext start(String profile) {
        return new SpringApplicationBuilder(JTypoSquattingApplication.class)
                .profiles("test", profile)
                .properties("server.port=0", "management.server.port=0", "jtypo.worker.poll-interval-ms=3600000")
                .run();
    }

    @Test
    @DisplayName("Profil worker : traite les analyses ; son serveur HTTP n'expose que la supervision")
    void testWorkerProfile() {
        try (ConfigurableApplicationContext context = start("worker")) {
            assertEquals(1, context.getBeansOfType(ScanWorker.class).size());
            assertEquals(1, context.getBeansOfType(RetentionService.class).size(), "purge planifiée sur les workers");
            assertTrue(context.getBeansOfType(ScanController.class).isEmpty());
            assertTrue(context.getBeansOfType(UserController.class).isEmpty());
            assertTrue(context.getBeansOfType(ScreenshotController.class).isEmpty());
        }
    }

    @Test
    @DisplayName("Profil api : sert l'API, sans worker")
    void testApiProfile() {
        try (ConfigurableApplicationContext context = start("api")) {
            assertTrue(context.getBeansOfType(ScanWorker.class).isEmpty());
            assertTrue(context.getBeansOfType(RetentionService.class).isEmpty());
            assertFalse(context.getBeansOfType(SecurityFilterChain.class).isEmpty());
            assertEquals(1, context.getBeansOfType(ScanController.class).size());
        }
    }
}
