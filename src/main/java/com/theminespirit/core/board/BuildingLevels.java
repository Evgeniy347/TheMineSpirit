package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class BuildingLevels {
    public enum State {
        NEW,
        UPGRADING,
        STANDING
    }

    private BuildingLevels() {}

    public static State state(BoardCase boardCase, int level, BoardLedger ledger) {
        Objects.requireNonNull(boardCase, "Кейс состояния не может быть null: без него неизвестно, что стоит");
        Objects.requireNonNull(ledger, "Ledger не может быть null: без него неизвестно, какой уровень стоит");
        if (level < 1) {
            throw new IllegalArgumentException(
                    "Уровень " + level + " меньше 1: состояния нулевого уровня не существует");
        }
        if (ledger.has(boardCase, level)) {
            return State.STANDING;
        }
        if (level > 1 && ledger.has(boardCase, level - 1)) {
            return State.UPGRADING;
        }
        return State.NEW;
    }

    public static Optional<String> scheme(BoardCase boardCase, int level) {
        Objects.requireNonNull(boardCase, "Кейс схемы не может быть null: без него неизвестно, что строят");
        Optional<String> exact = DataTables.INSTANCE.stages().current().scheme(boardCase, level);
        if (exact.isPresent() || level <= 1) {
            return exact;
        }
        return DataTables.INSTANCE.stages().current().scheme(boardCase, level - 1);
    }

    public static int maxLevel(BoardCase boardCase) {
        Objects.requireNonNull(
                boardCase, "Кейс уровня не может быть null: без него неизвестно, " + "сколько уровней у постройки");
        Integer max = DataTables.INSTANCE.board().current().levels().get(boardCase.name());
        return max == null ? 1 : max;
    }

    public static boolean hasLevels(BoardCase boardCase) {
        Objects.requireNonNull(
                boardCase, "Кейс уровня не может быть null: без него неизвестно, " + "есть ли у постройки уровни");
        return DataTables.INSTANCE.board().current().levels().containsKey(boardCase.name());
    }

    public static OptionalInt nextLevel(BoardCase boardCase, BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Ledger не может быть null: без него неизвестно, какой уровень стоит");
        int max = maxLevel(boardCase);
        for (int level = 1; level <= max; level++) {
            if (!ledger.has(boardCase, level)) {
                return OptionalInt.of(level);
            }
        }
        return OptionalInt.empty();
    }
}
