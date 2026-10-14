package com.aleph.graymatter.jtyposquatting.account;

import com.aleph.graymatter.jtyposquatting.scan.ScanUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Data of the logged-in user: export (GDPR access and portability) and account deletion (erasure).
 */
@RestController
@RequestMapping("/api/me")
@ConditionalOnProperty(name = "jtypo.api.enabled", havingValue = "true", matchIfMissing = true)
public class AccountController {

    private final AccountService accounts;
    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;

    public AccountController(AccountService accounts,
                             ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal OidcUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("jtyposquatting-data.json").build().toString())
                .body(accounts.export(ScanUser.of(user)));
    }

    /**
     * Deletes the account and ends its other sessions. The current session is kept so that the
     * frontend can then log out, which also ends the identity provider session (otherwise the next
     * login would be silent and recreate the account).
     */
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
        accounts.delete(ScanUser.of(user));
        FindByIndexNameSessionRepository<? extends Session> repository = sessions.getIfAvailable();
        if (repository != null && user != null) {
            HttpSession current = request.getSession(false);
            String currentId = current != null ? current.getId() : null;
            // Sessions on every API instance (Spring Session indexes them by principal name)
            repository.findByPrincipalName(user.getName()).keySet().stream()
                    .filter(id -> !id.equals(currentId))
                    .forEach(repository::deleteById);
        }
        return ResponseEntity.noContent().build();
    }
}
