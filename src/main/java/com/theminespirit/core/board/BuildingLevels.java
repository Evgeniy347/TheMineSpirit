package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Уровни построек: сколько уровней у кейса и какой следующий доступен. Максимум —
 * данные таблицы board ({@code levels}); кейс без строки имеет ровно один уровень.
 * Следующий уровень — первый неотмеченный, поэтому уровень не прыгает через непостроенный.
 */
public final class BuildingLevels {
    private BuildingLevels() {
    }

    public static int maxLevel(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс уровня не может быть null: без него неизвестно, "
                + "сколько уровней у постройки");
        Integer max = DataTables.INSTANCE.board().current().levels().get(boardCase.name());
        return max == null ? 1 : max;
    }

    public static boolean hasLevels(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс уровня не может быть null: без него неизвестно, "
                + "есть ли у постройки уровни");
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
