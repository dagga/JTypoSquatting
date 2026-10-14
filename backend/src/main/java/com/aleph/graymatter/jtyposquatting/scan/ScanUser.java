package com.aleph.graymatter.jtyposquatting.scan;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Owner of scans: the logged-in user, or the single local user of the desktop application.
 */
public record ScanUser(String id, String username, String email) {

    public static final ScanUser LOCAL = new ScanUser("local", "local", null);

    public static ScanUser of(OidcUser user) {
        return user == null ? LOCAL : new ScanUser(user.getSubject(), user.getPreferredUsername(), user.getEmail());
    }
}
