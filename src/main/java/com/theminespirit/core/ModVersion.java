package com.theminespirit.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Версия мода вида {@code <минор>.<N>}: минор (например 0.1) поднимается вручную, N — номер сборки
 * (число коммитов HEAD). Один формат на сборку, лог, команду и экран доски.
 */
public final class ModVersion {
    private static final Pattern MINOR = Pattern.compile("\\d+\\.\\d+");
    private static final Pattern BUILD = Pattern.compile("[1-9][0-9]*");

    private final String minor;
    private final int build;

    private ModVersion(String minor, int build) {
        this.minor = minor;
        this.build = build;
    }

    public static ModVersion of(String minor, int build) {
        Objects.requireNonNull(minor, "Минор версии не может быть null: без него не собрать номер");
        String trimmed = minor.strip();
        if (!MINOR.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Минор версии должен быть вида 0.1, получено: '" + minor + "'");
        }
        if (build <= 0) {
            throw new IllegalArgumentException("Номер сборки должен быть положительным, получено: " + build);
        }
        return new ModVersion(trimmed, build);
    }

    public static ModVersion parse(String version) {
        Objects.requireNonNull(version, "Версия не может быть null: нечего разбирать");
        String text = version.strip();
        int dot = text.lastIndexOf('.');
        if (dot <= 0) {
            throw new IllegalArgumentException("Версия должна быть вида 0.1.N, получено: '" + version + "'");
        }
        return of(text.substring(0, dot), parseBuild(text.substring(dot + 1)));
    }

    public static int parseBuild(String text) {
        Objects.requireNonNull(text, "Номер сборки не может быть null: нечего проверять");
        String trimmed = text.strip();
        if (!BUILD.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Номер сборки должен быть положительным целым, получено: '" + text + "'");
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Номер сборки слишком велик: '" + text + "'", error);
        }
    }

    public String minor() {
        return minor;
    }

    public int build() {
        return build;
    }

    @Override
    public String toString() {
        return minor + "." + build;
    }
}
