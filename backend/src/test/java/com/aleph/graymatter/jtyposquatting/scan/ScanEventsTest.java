package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE stream of a scan (resumed with Last-Event-ID) and targeted cancellation.
 */
@SpringBootTest(properties = {"jtypo.sse.poll-interval-ms=50", "jtypo.quota.max-concurrent-scans=10"})
// No result printing: it reads the response headers while the SSE thread is still writing
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class ScanEventsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ScanService scanService;

    @Autowired
    private ScanResultRepository results;

    @Autowired
    private JdbcTemplate jdbc;

    private static OidcLoginRequestPostProcessor user(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject));
    }

    private void addResult(String scanId, String domain) {
        results.insert(new ScanResult(0, scanId, domain, domain, DomainStatus.SAFE, 200, TlsStatus.VALID,
                "Title " + domain, null, null, null, null, null, Instant.now()));
    }

    private void setStatus(String scanId, ScanStatus status) {
        jdbc.update("UPDATE scans SET status = ? WHERE id = ?", status.name(), scanId);
    }

    private org.springframework.mock.web.MockHttpServletResponse lastResponse;

    private static int count(String body, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(body);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private String streamToEnd(String scanId, String subject, String lastEventId) throws Exception {
        // As in a browser, the stream uses the session created at login (otherwise the first SSE write
        // and the end of the request would both try to create it)
        String sessionCookie = mvc.perform(get("/api/me").with(user(subject)))
                .andReturn().getResponse().getHeader("Set-Cookie");
        String sessionId = sessionCookie.substring("SESSION=".length(), sessionCookie.indexOf(';'));
        var builder = get("/api/scans/" + scanId + "/events").with(user(subject)).accept("text/event-stream")
                .cookie(new jakarta.servlet.http.Cookie("SESSION", sessionId));
        if (lastEventId != null) {
            builder.header("Last-Event-ID", lastEventId);
        }
        MvcResult result = mvc.perform(builder).andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(10_000);
        lastResponse = result.getResponse();
        return result.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("The stream sends every result, then the end event")
    void testStreamCompletedScan() throws Exception {
        Scan scan = scanService.createScan(new ScanUser("sse-user", "s", null), "example.com", false);
        addResult(scan.id(), "exmple.com");
        addResult(scan.id(), "exaple.com");
        setStatus(scan.id(), ScanStatus.COMPLETED);

        String body = streamToEnd(scan.id(), "sse-user", null);

        assertEquals(2, count(body, "^event:result$"), body);
        assertEquals(1, count(body, "^event:end$"), body);
        assertTrue(body.contains("\"domain\":\"exmple.com\""));
        assertTrue(body.contains("\"status\":\"COMPLETED\""));
        assertEquals("no-cache, no-transform", lastResponse.getHeader("Cache-Control"));
        assertEquals("no", lastResponse.getHeader("X-Accel-Buffering"));
    }

    @Test
    @DisplayName("With Last-Event-ID, only the missed results are sent again")
    void testResumeWithLastEventId() throws Exception {
        Scan scan = scanService.createScan(new ScanUser("resume-user", "r", null), "example.org", false);
        addResult(scan.id(), "a.example.org");
        addResult(scan.id(), "b.example.org");
        addResult(scan.id(), "c.example.org");
        setStatus(scan.id(), ScanStatus.COMPLETED);
        long firstId = results.findAfter(scan.id(), 0, 1).get(0).id();

        String body = streamToEnd(scan.id(), "resume-user", String.valueOf(firstId));

        assertEquals(2, count(body, "^event:result$"), body);
        assertFalse(body.contains("a.example.org"));
    }

    @Test
    @DisplayName("The stream of a running scan follows new results until the end")
    void testLiveStream() throws Exception {
        Scan scan = scanService.createScan(new ScanUser("live-user", "l", null), "example.net", false);
        setStatus(scan.id(), ScanStatus.RUNNING);
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(300);
                addResult(scan.id(), "exmple.net");
                Thread.sleep(300);
                setStatus(scan.id(), ScanStatus.COMPLETED);
            } catch (InterruptedException ignored) {
            }
        });

        String body = streamToEnd(scan.id(), "live-user", null);

        assertTrue(count(body, "^event:progress$") >= 1, body);
        assertEquals(1, count(body, "^event:result$"), body);
        assertTrue(body.indexOf("event:result") < body.indexOf("event:end"));
    }

    @Test
    @DisplayName("The stream of another user's scan is refused")
    void testStreamOwnership() throws Exception {
        Scan scan = scanService.createScan(new ScanUser("stream-owner", "o", null), "example.io", false);
        mvc.perform(get("/api/scans/" + scan.id() + "/events").with(user("stream-intruder")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cancellation only affects the targeted scan, and only by its owner")
    void testCancel() throws Exception {
        Scan mine = scanService.createScan(new ScanUser("canceller", "c", null), "example.com", false);
        Scan other = scanService.createScan(new ScanUser("canceller", "c", null), "example.org", false);

        mvc.perform(delete("/api/scans/" + mine.id()).with(user("someone-else")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/scans/" + mine.id()).with(user("canceller")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/scans/" + mine.id()).with(user("canceller")).with(csrf()))
                .andExpect(status().isConflict());

        assertEquals(ScanStatus.CANCELLED, scanService.getScan(new ScanUser("canceller", "c", null), mine.id()).status());
        assertEquals(ScanStatus.PENDING, scanService.getScan(new ScanUser("canceller", "c", null), other.id()).status());
    }
}
