package com.theminespirit.core.board;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class BoardLedger {
    public static final String PREFIX = "building:";

    public record BuildingMark(BoardCase boardCase, int level) {}

    private final Set<String> built = new LinkedHashSet<>();

    public static String key(BoardCase boardCase, int level) {
        Objects.requireNonNull(boardCase, "Кейс отметки не может быть null: без него непонятно, что стоит");
        if (level < 1) {
            throw new IllegalArgumentException(
                    "Уровень отметки " + level + " меньше 1: первой всегда " + "стоит отметка уровня 1");
        }
        return PREFIX + boardCase.name() + ":" + level;
    }

    public static Optional<BuildingMark> parse(@Nullable String dependency) {
        if (dependency == null || !dependency.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = dependency.split(":", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        BoardCase boardCase;
        try {
            boardCase = BoardCase.valueOf(parts[1]);
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
        int level;
        try {
            level = Integer.parseInt(parts[2]);
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
        if (level < 1) {
            return Optional.empty();
        }
        return Optional.of(new BuildingMark(boardCase, level));
    }

    public void note(BoardCase boardCase, int level) {
        built.add(key(boardCase, level));
    }

    public void forget(BoardCase boardCase, int level) {
        Objects.requireNonNull(boardCase, "Кейс снятия отметки не может быть null: без него непонятно, что снимать");
        built.remove(key(boardCase, level));
    }

    public void clear() {
        built.clear();
    }

    public void restore(Set<String> keys) {
        Objects.requireNonNull(keys, "Ключи отметок не могут быть null: пустой набор задают пустым набором");
        for (String key : keys) {
            if (!key.startsWith(PREFIX)) {
                throw new IllegalArgumentException("Ключ отметки '" + key + "' не начинается с '" + PREFIX
                        + "': в ledger хранятся только отметки построек");
            }
            built.add(key);
        }
    }

    public boolean has(BoardCase boardCase, int level) {
        Objects.requireNonNull(boardCase, "Кейс проверки не может быть null: без него непонятно, что искать");
        return built.contains(PREFIX + boardCase.name() + ":" + level);
    }

    public Set<String> keys() {
        return Set.copyOf(built);
    }
}
