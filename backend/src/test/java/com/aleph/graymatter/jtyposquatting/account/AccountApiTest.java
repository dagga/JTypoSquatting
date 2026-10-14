package com.aleph.graymatter.jtyposquatting.account;

import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;
import com.aleph.graymatter.jtyposquatting.scan.Scan;
import com.aleph.graymatter.jtyposquatting.scan.ScanResult;
import com.aleph.graymatter.jtyposquatting.scan.ScanResultRepository;
import com.aleph.graymatter.jtyposquatting.scan.ScanService;
import com.aleph.graymatter.jtyposquatting.scan.ScanUser;
import com.aleph.graymatter.jtyposquatting.storage.ScreenshotStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Droits RGPD : export des données de l'utilisateur et suppression de son compte.
 */
@SpringBootTest(properties = {"jtypo.storage.directory=${java.io.tmpdir}/jtypo-account-${random.uuid}",
        "jtypo.quota.max-concurrent-scans=10"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ScanService scanService;

    @Autowired
    private ScanResultRepository results;

    @Autowired
    private ScreenshotStore store;

    @Autowired
    private JdbcTemplate jdbc;

    private static OidcLoginRequestPostProcessor user(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("preferred_username", subject));
    }

    private Scan scanWithResult(String userId, String domain, String screenshotKey) throws Exception {
        Scan scan = scanService.createScan(new ScanUser(userId, userId, userId + "@example.com"), domain, false);
        results.insert(new ScanResult(0, scan.id(), "x." + domain, "x." + domain, DomainStatus.SAFE, 200, TlsStatus.VALID,
                "Title", null, null, null, null, screenshotKey, Instant.now()));
        return scan;
    }

    @Test
    @DisplayName("L'export contient le profil, les analyses et les résultats de l'utilisateur, et rien d'autre")
    void testExport() throws Exception {
        Scan mine = scanWithResult("export-user", "mine-export.com", null);
        Scan other = scanWithResult("other-export-user", "other-export.com", null);

        mvc.perform(get("/api/me/export").with(user("export-user")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(jsonPath("$.user.id").value("export-user"))
                .andExpect(jsonPath("$.user.email").value("export-user@example.com"))
                .andExpect(jsonPath("$.scans[*].scan.id", contains(mine.id())))
                .andExpect(jsonPath("$.scans[0].results[0].domain").value("x.mine-export.com"))
                .andExpect(jsonPath("$.scans[*].scan.id", not(hasItem(other.id()))));
    }

    @Test
    @DisplayName("La suppression efface le compte, ses analyses et ses captures non partagées, et elles seules")
    void testDeleteAccount() throws Exception {
        String own = store.put(new byte[]{10, 20, 30});
        String shared = store.put(new byte[]{40, 50, 60});
        scanWithResult("leaving-user", "leaving.com", own);
        scanWithResult("leaving-user", "leaving.org", shared);
        Scan kept = scanWithResult("staying-user", "staying.com", shared);

        mvc.perform(delete("/api/me").with(user("leaving-user"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/me").with(user("leaving-user")).with(csrf())).andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = 'leaving-user'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM scans WHERE user_id = 'leaving-user'", Integer.class));
        assertTrue(store.get(own).isEmpty(), "capture propre à l'utilisateur supprimée");
        assertTrue(store.get(shared).isPresent(), "capture utilisée par un autre utilisateur conservée");
        assertEquals(1, results.countByScan(kept.id()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = 'staying-user'", Integer.class));
    }
}
