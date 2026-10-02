package com.pharmacy.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private Tokens() {
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public static String temporaryPassword() {
        char[] password = new char[12];
        password[0] = 'A';
        password[1] = 'b';
        password[2] = '7';
        for (int index = 3; index < password.length; index++) {
            password[index] = PASSWORD_ALPHABET[RANDOM.nextInt(PASSWORD_ALPHABET.length)];
        }
        for (int index = password.length - 1; index > 0; index--) {
            int swap = RANDOM.nextInt(index + 1);
            char current = password[index];
            password[index] = password[swap];
            password[swap] = current;
        }
        return new String(password);
    }
}
