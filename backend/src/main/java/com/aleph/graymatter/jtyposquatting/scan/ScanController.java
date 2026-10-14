package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.dto.GenerationRequestDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Scans of the logged-in user. A scan is queued, then processed by a worker; its results can be
 * read page by page.
 */
@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private static final int MAX_PAGE_SIZE = 500;

    private final ScanService scanService;
    private final ScanResultRepository results;

    public ScanController(ScanService scanService, ScanResultRepository results) {
        this.scanService = scanService;
        this.results = results;
    }

    /** Queues a scan: 202 Accepted with the scan and its URL. */
    @PostMapping
    public ResponseEntity<ScanDTO> create(@RequestBody GenerationRequestDTO request,
                                          @AuthenticationPrincipal OidcUser user) throws InvalidDomainException {
        Scan scan = scanService.createScan(ScanUser.of(user), request.getBaseDomain(), request.isForceRefresh());
        return ResponseEntity.accepted().location(URI.create("/api/scans/" + scan.id())).body(ScanDTO.of(scan));
    }

    @GetMapping
    public List<ScanDTO> list(@AuthenticationPrincipal OidcUser user) {
        return scanService.listScans(ScanUser.of(user), 50).stream().map(ScanDTO::of).toList();
    }

    @GetMapping("/{id}")
    public ScanDTO get(@PathVariable String id, @AuthenticationPrincipal OidcUser user) {
        return ScanDTO.of(scanService.getScan(ScanUser.of(user), id));
    }

    /** Results with an id greater than {@code after}, in order. */
    @GetMapping("/{id}/results")
    public List<ScanResultDTO> results(@PathVariable String id,
                                       @RequestParam(defaultValue = "0") long after,
                                       @RequestParam(defaultValue = "100") int limit,
                                       @AuthenticationPrincipal OidcUser user) {
        scanService.getScan(ScanUser.of(user), id);
        return results.findAfter(id, after, Math.min(Math.max(limit, 1), MAX_PAGE_SIZE)).stream()
                .map(ScanResultDTO::of)
                .toList();
    }

    @ExceptionHandler(InvalidDomainException.class)
    public ResponseEntity<Map<String, String>> invalidDomain(InvalidDomainException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
