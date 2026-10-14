package com.aleph.graymatter.jtyposquatting.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Storage of screenshots outside the database. Screenshots are addressed by the SHA-256 of their
 * content: identical screenshots (parking pages...) are stored once.
 */
public interface ScreenshotStore {

    Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    /** Stores a PNG screenshot and returns its key. */
    String put(byte[] png);

    Optional<byte[]> get(String key);

    static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String keyOf(String hash) {
        if (!HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("Invalid screenshot hash");
        }
        return "screenshots/" + hash + ".png";
    }

    /** Hash part of a key, used in public URLs. */
    static String hashOf(String key) {
        return key.substring("screenshots/".length(), key.length() - ".png".length());
    }
}
