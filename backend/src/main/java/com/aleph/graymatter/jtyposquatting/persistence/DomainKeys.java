package com.aleph.graymatter.jtyposquatting.persistence;

import java.net.IDN;
import java.util.Locale;

/**
 * Conversions between the stored form of a domain (lower-case ASCII/punycode, compared byte by byte)
 * and its Unicode form (display only).
 */
public final class DomainKeys {

    private DomainKeys() {
    }

    /** "Exämple.COM" → "xn--exmple-cua.com". Throws IllegalArgumentException for invalid names. */
    public static String toKey(String domain) {
        return IDN.toASCII(domain.trim(), IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
    }

    /** "xn--exmple-cua.com" → "exämple.com" */
    public static String toUnicode(String key) {
        return IDN.toUnicode(key, IDN.ALLOW_UNASSIGNED);
    }
}
