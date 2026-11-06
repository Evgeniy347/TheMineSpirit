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
    private long doneTotal;
    private long reopenedTotal;
    private @Nullable StageGate gate;

    public void gate(StageGate next) {
        gate = Objects.requireNonNull(next, "Гейт этапа не может быть null: без него неясно, что открыто");
    }

    public StageGate stageGate() {
        return gate == null ? SettlementStage.gate(this) : gate;
    }

    public int stage() {
        return stageGate().stage();
    }

    public static String key(BoardCase boardCase, int level) {
        Objects.requireNonNull(boardCase, "Кейс отметки не может быть null: без него непонятно, что стоит");
        return planKey(boardCase.name(), level);
    }

    public static String planKey(String planId, int level) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: без него непонятно, что стоит");
        if (planId.isBlank() || planId.indexOf(':') >= 0) {
            throw new IllegalArgumentException("id постройки плана '" + planId
                    + "' пуст или содержит ':' — ключ отметки разбирается по двоеточиям, проверьте stages.json");
        }
        if (level < 1) {
            throw new IllegalArgumentException(
                    "Уровень отметки " + level + " меньше 1: первой всегда " + "стоит отметка уровня 1");
        }
        return PREFIX + planId + ":" + level;
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

    public void complete(BoardCase boardCase, int level) {
        note(boardCase, level);
        doneTotal++;
    }

    public void reopen(BoardCase boardCase, int level) {
        forget(boardCase, level);
        reopenedTotal++;
    }

    public void forgetHistory() {
        doneTotal = 0;
        reopenedTotal = 0;
    }

    public long doneTotal() {
        return doneTotal;
    }

    public long reopenedTotal() {
        return reopenedTotal;
    }

    public void notePlan(String planId, int level) {
        built.add(planKey(planId, level));
    }

    public void forgetPlan(String planId, int level) {
        built.remove(planKey(planId, level));
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

    public boolean hasPlan(String planId, int level) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: без него непонятно, что искать");
        return built.contains(planKey(planId, level));
    }

    public Set<String> keys() {
        return Set.copyOf(built);
    }
}
