package com.theminespirit.core.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IdFilter {
    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final String ANY_PATTERN = "*";
    private static final String ALLOWED_SYMBOLS = "abcdefghijklmnopqrstuvwxyz0123456789_:./*-";
    private static final IdFilter MATCH_ALL = new IdFilter(List.of());

    private final List<String> patterns;

    private IdFilter(List<String> patterns) {
        this.patterns = patterns;
    }

    public static IdFilter matchAll() {
        return MATCH_ALL;
    }

    public static IdFilter of(List<String> patterns) {
        Objects.requireNonNull(patterns, "Список шаблонов не может быть null: пустой фильтр задаётся пустым списком");
        List<String> normalized = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            normalized.add(normalizePattern(pattern));
        }
        return normalized.isEmpty() || normalized.contains(ANY_PATTERN) ? MATCH_ALL : new IdFilter(List.copyOf(normalized));
    }

    public boolean matches(String id) {
        Objects.requireNonNull(id, "id не может быть null: фильтр проверяет идентификатор блока или предмета");
        if (patterns.isEmpty()) {
            return true;
        }
        String qualified = qualify(id);
        for (String pattern : patterns) {
            if (globMatches(pattern, qualified)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesAll() {
        return patterns.isEmpty();
    }

    public List<String> patterns() {
        return patterns;
    }

    private static String normalizePattern(String pattern) {
        Objects.requireNonNull(pattern, "Шаблон фильтра не может быть null: пустое значение в списке — ошибка плана");
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("Шаблон фильтра пуст: пустая строка не совпадает ни с одним id, "
                    + "для «всё» оставьте список пустым или напишите '*'");
        }
        for (int i = 0; i < pattern.length(); i++) {
            if (ALLOWED_SYMBOLS.indexOf(pattern.charAt(i)) < 0) {
                throw new IllegalArgumentException("Шаблон фильтра '" + pattern + "' содержит недопустимый символ '"
                        + pattern.charAt(i) + "': разрешены a-z, 0-9, _ : . / - и подстановка *");
            }
        }
        return pattern.equals(ANY_PATTERN) ? pattern : qualify(pattern);
    }

    private static String qualify(String id) {
        return id.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + id : id;
    }

    private static boolean globMatches(String pattern, String text) {
        int p = 0;
        int t = 0;
        int starPattern = -1;
        int starText = 0;
        while (t < text.length()) {
            if (p < pattern.length() && pattern.charAt(p) == '*') {
                starPattern = p++;
                starText = t;
            } else if (p < pattern.length() && pattern.charAt(p) == text.charAt(t)) {
                p++;
                t++;
            } else if (starPattern >= 0) {
                p = starPattern + 1;
                t = ++starText;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }
}
