package com.aleph.graymatter.jtyposquatting.monitoring;

import com.aleph.graymatter.jtyposquatting.scan.ScanService;
import com.aleph.graymatter.jtyposquatting.scan.ScanUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Monitoring endpoints: health and business Prometheus metrics; nothing else is exposed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
class MonitoringTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ScanService scanService;

    @Test
    @DisplayName("Health and probes are reachable without authentication")
    void testHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(content().string(containsString("UP")));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Business metrics are published in the Prometheus format")
    void testPrometheusMetrics() throws Exception {
        scanService.createScan(new ScanUser("metrics-user", "m", null), "metrics-test.com", false);

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jtypo_scans_submitted_total")))
                .andExpect(content().string(containsString("jtypo_scans_queue{application=\"jtyposquatting\",status=\"PENDING\"}")))
                .andExpect(content().string(containsString("jtypo_sse_streams")));
    }

    @Test
    @DisplayName("Sensitive Actuator endpoints are not exposed")
    void testSensitiveEndpointsHidden() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/heapdump")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/configprops")).andExpect(status().is4xxClientError());
    }
}
