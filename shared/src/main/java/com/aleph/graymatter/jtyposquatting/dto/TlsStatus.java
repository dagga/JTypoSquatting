package com.aleph.graymatter.jtyposquatting.dto;

/**
 * Result of the TLS certificate validation of an analysed domain.
 * An invalid certificate is a useful signal for typo squatting analysis, so it is reported rather than ignored.
 */
public enum TlsStatus {
    /** Certificate chain and hostname are valid */
    VALID,
    /** Certificate has expired or is not yet valid */
    EXPIRED,
    /** Certificate does not match the domain name */
    HOSTNAME_MISMATCH,
    /** Certificate is self-signed or issued by an unknown authority */
    UNTRUSTED,
    /** TLS handshake failed for another reason (protocol, cipher...) */
    HANDSHAKE_FAILED,
    /** No HTTPS response was obtained (HTTP only or unreachable) */
    NONE
}
