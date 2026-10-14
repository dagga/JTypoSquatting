package com.aleph.graymatter.jtyposquatting.storage;

import com.aleph.graymatter.jtyposquatting.scan.ScanResultRepository;
import com.aleph.graymatter.jtyposquatting.scan.ScanUser;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serves screenshots to the users whose scans contain them.
 */
@RestController
public class ScreenshotController {

    private final ScreenshotStore store;
    private final ScanResultRepository results;

    public ScreenshotController(ScreenshotStore store, ScanResultRepository results) {
        this.store = store;
        this.results = results;
    }

    @GetMapping("/api/screenshots/{hash}")
    public ResponseEntity<byte[]> get(@PathVariable String hash, @AuthenticationPrincipal OidcUser user) {
        if (!ScreenshotStore.HASH.matcher(hash).matches()) {
            return ResponseEntity.notFound().build();
        }
        String key = ScreenshotStore.keyOf(hash);
        // 404 rather than 403: do not reveal that a screenshot exists
        if (!results.isScreenshotVisibleTo(key, ScanUser.of(user).id())) {
            return ResponseEntity.notFound().build();
        }
        return store.get(key)
                .map(png -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        // Content-addressed: never changes
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable())
                        .body(png))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
