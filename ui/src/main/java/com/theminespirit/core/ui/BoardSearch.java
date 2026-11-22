package com.theminespirit.core.ui;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class BoardSearch {
    private BoardSearch() {}

    public static String normalize(@Nullable String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String haystack, @Nullable String query) {
        Objects.requireNonNull(haystack, "Строка поиска не может быть null: пустую задают пустой строкой");
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return true;
        }
        String hay = haystack.toLowerCase(Locale.ROOT);
        int index = 0;
        while (index < normalized.length()) {
            while (index < normalized.length() && Character.isWhitespace(normalized.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < normalized.length() && !Character.isWhitespace(normalized.charAt(index))) {
                index++;
            }
            if (start < index && !hay.contains(normalized.substring(start, index))) {
                return false;
            }
        }
        return true;
    }
}
