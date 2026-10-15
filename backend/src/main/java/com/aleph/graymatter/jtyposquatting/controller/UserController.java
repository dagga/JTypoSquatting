package com.aleph.graymatter.jtyposquatting.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Current user information for the web frontend.
 */
@RestController
// Disabled on worker instances, whose HTTP server only exposes monitoring endpoints
@ConditionalOnProperty(name = "jtypo.api.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            // Local (desktop) mode: no authenticated user
            return ResponseEntity.noContent().build();
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("id", user.getSubject());
        body.put("username", user.getPreferredUsername());
        body.put("name", user.getFullName());
        body.put("email", user.getEmail());
        return ResponseEntity.ok(body);
    }
}
