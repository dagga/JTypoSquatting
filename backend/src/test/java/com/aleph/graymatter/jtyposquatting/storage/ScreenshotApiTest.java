package com.aleph.graymatter.jtyposquatting.storage;

import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.scan.DomainCheck;
import com.aleph.graymatter.jtyposquatting.scan.DomainChecker;
import com.aleph.graymatter.jtyposquatting.scan.Scan;
import com.aleph.graymatter.jtyposquatting.scan.ScanResultRepository;
import com.aleph.graymatter.jtyposquatting.scan.ScanService;
import com.aleph.graymatter.jtyposquatting.scan.ScanUser;
import com.aleph.graymatter.jtyposquatting.service.DomainCheckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Screenshots: kept in the object storage during a check, served to the owner only.
 */
@SpringBootTest(properties = "jtypo.storage.directory=${java.io.tmpdir}/jtypo-test-${random.uuid}")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScreenshotApiTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 42};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DomainChecker checker;

    @Autowired
    private ScanService scanService;

    @Autowired
    private ScanResultRepository results;

    @MockitoBean
    private DomainCheckService domainCheckService;

    @Test
    @DisplayName("The screenshot is stored outside the database and served to the scan owner only")
    void testScreenshotStoredAndProtected() throws Exception {
        when(domainCheckService.analyze("shot-test.com")).thenReturn(
                new DomainResultDTO("shot-test.com", DomainStatus.SUSPICIOUS, "T", "", "", 200, PNG, "", null));
        Scan scan = scanService.createScan(new ScanUser("shot-owner", "o", null), "shot-base.com", false);

        DomainCheck check = checker.check("shot-test.com", false);
        results.insert(new com.aleph.graymatter.jtyposquatting.scan.ScanResult(0, scan.id(), check.domain(),
                check.domainUnicode(), check.status(), check.httpCode(), check.tlsStatus(), check.title(), null, null,
                null, null, check.screenshotKey(), check.checkedAt()));

        assertNotNull(check.screenshotKey());
        String url = "/api/screenshots/" + ScreenshotStore.hashOf(check.screenshotKey());

        mvc.perform(get("/api/scans/" + scan.id() + "/results").with(oidcLogin().idToken(t -> t.subject("shot-owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].screenshotUrl").value(url));
        mvc.perform(get(url).with(oidcLogin().idToken(t -> t.subject("shot-owner"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        mvc.perform(get(url).with(oidcLogin().idToken(t -> t.subject("someone-else"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/screenshots/not-a-hash").with(oidcLogin().idToken(t -> t.subject("shot-owner"))))
                .andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }
}
