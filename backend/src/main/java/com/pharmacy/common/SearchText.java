package com.pharmacy.common;

public final class SearchText {

    private SearchText() {
    }

    public static String like(String raw) {
        String escaped = raw.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
