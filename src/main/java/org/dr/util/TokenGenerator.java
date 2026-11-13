package org.dr.util;

import java.security.SecureRandom;
import java.util.Base64;

public class TokenGenerator {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_TOKEN_LENGTH = 32;

    /**
     * Generate a random Base64URL-encoded token of 32 bytes
     */
    public static String generateSecureToken() {
        return generateSecureToken(DEFAULT_TOKEN_LENGTH);
    }

    /**
     * Generate a random Base64URL-encoded token of specified byte length
     */
    public static String generateSecureToken(int lengthBytes) {
        byte[] bytes = new byte[lengthBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
