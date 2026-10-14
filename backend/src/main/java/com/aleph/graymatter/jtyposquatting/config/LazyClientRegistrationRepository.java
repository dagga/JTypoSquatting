package com.aleph.graymatter.jtyposquatting.config;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;

/**
 * Single OpenID Connect client registration, resolved from the issuer discovery document on first use.
 * The application therefore starts even when the identity provider is temporarily unreachable.
 */
public class LazyClientRegistrationRepository implements ClientRegistrationRepository {

    public static final String REGISTRATION_ID = "oidc";

    private final String issuerUri;
    private final String clientId;
    private final String clientSecret;
    private volatile ClientRegistration registration;

    public LazyClientRegistrationRepository(String issuerUri, String clientId, String clientSecret) {
        this.issuerUri = issuerUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        if (!REGISTRATION_ID.equals(registrationId)) {
            return null;
        }
        ClientRegistration current = registration;
        if (current == null) {
            synchronized (this) {
                current = registration;
                if (current == null) {
                    current = ClientRegistrations.fromIssuerLocation(issuerUri)
                            .registrationId(REGISTRATION_ID)
                            .clientId(clientId)
                            .clientSecret(clientSecret)
                            .scope("openid", "profile", "email")
                            .build();
                    registration = current;
                }
            }
        }
        return current;
    }
}
