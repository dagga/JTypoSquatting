package com.aleph.graymatter.jtyposquatting.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Logout for a single-page application: instead of redirecting, returns the identity provider
 * end-session URL as JSON ({@code {"logoutUrl": "..."}}). A fetch() call cannot follow a redirect to
 * another origin; the frontend navigates to this URL itself.
 */
public class JsonLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

    public JsonLogoutSuccessHandler(ClientRegistrationRepository clientRegistrations, String postLogoutRedirectUri) {
        super(clientRegistrations);
        setPostLogoutRedirectUri(postLogoutRedirectUri);
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        String logoutUrl = determineTargetUrl(request, response, authentication);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"logoutUrl\":\"" + logoutUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }
}
