package com.pharmacy.common;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password, int minLength) {
        int minimum = Math.max(minLength, 8);
        if (password == null || password.length() < minimum) {
            throw BusinessException.badRequest("WEAK_PASSWORD", "Password must be at least " + minimum + " characters.");
        }
        if (password.length() > 100) {
            throw BusinessException.badRequest("WEAK_PASSWORD", "Password must be at most 100 characters.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw BusinessException.badRequest("WEAK_PASSWORD", "Password must contain at least one letter and one digit.");
        }
    }
}
