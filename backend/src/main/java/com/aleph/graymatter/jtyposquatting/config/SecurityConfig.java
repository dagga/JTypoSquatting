package com.aleph.graymatter.jtyposquatting.config;

import com.aleph.graymatter.jtyposquatting.abuse.RateLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Security configuration.
 * <ul>
 *   <li>{@code jtypo.security.mode=oidc} (default): public web deployment. Users log in through an
 *       OpenID Connect provider; Spring acts as the OAuth2 client (BFF pattern) and keeps the session
 *       in an httpOnly cookie, which also authenticates SSE streams ({@code EventSource} cannot send
 *       an Authorization header).</li>
 *   <li>{@code jtypo.security.mode=local}: legacy desktop application (Swing client), API bound to
 *       127.0.0.1 by the launcher, no authentication.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
// Worker instances have no web server
@ConditionalOnWebApplication
public class SecurityConfig {

    static final String API_CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    @Bean
    @ConditionalOnProperty(name = "jtypo.security.mode", havingValue = "oidc", matchIfMissing = true)
    ClientRegistrationRepository clientRegistrationRepository(@Value("${jtypo.oidc.issuer-uri}") String issuerUri,
                                                              @Value("${jtypo.oidc.client-id}") String clientId,
                                                              @Value("${jtypo.oidc.client-secret}") String clientSecret) {
        return new LazyClientRegistrationRepository(issuerUri, clientId, clientSecret);
    }

    @Bean
    @ConditionalOnProperty(name = "jtypo.security.mode", havingValue = "oidc", matchIfMissing = true)
    SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http,
                                                ClientRegistrationRepository clientRegistrations,
                                                @Value("${jtypo.security.post-login-url:/}") String postLoginUrl,
                                                @Value("${jtypo.security.post-logout-url:{baseUrl}/}") String postLogoutUrl,
                                                @Value("${jtypo.rate-limit.requests-per-minute:120}") int requestsPerMinute)
            throws Exception {
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        logoutSuccessHandler.setPostLogoutRedirectUri(postLogoutUrl);

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/login/**", "/oauth2/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // Single provider: browsers go straight to it instead of a generated login page
                .oauth2Login(login -> login
                        .loginPage("/oauth2/authorization/" + LazyClientRegistrationRepository.REGISTRATION_ID)
                        .defaultSuccessUrl(postLoginUrl, true))
                .logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler))
                // API calls get a 401 instead of a redirect to the login page
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                // After the session is restored, so that limits apply per user (per IP for anonymous calls)
                .addFilterAfter(new RateLimitFilter(requestsPerMinute), SecurityContextHolderFilter.class)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                // The backend only serves JSON and redirects; the Next.js frontend sets its own CSP.
                // HSTS is sent on HTTPS requests (behind the proxy: server.forward-headers-strategy=framework)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(API_CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "jtypo.security.mode", havingValue = "local")
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // The Swing client is not a browser: no cookies, no CSRF exposure
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * CSRF handling for a single-page application: the token is read by JavaScript from the
     * XSRF-TOKEN cookie and sent back in the X-XSRF-TOKEN header (BREACH protection kept for
     * server-rendered forms).
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            xor.handle(request, response, csrfToken);
            // Render the token so that the cookie is always sent to the browser
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(headerValue) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
