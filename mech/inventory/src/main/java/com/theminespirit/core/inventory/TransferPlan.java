package com.theminespirit.core.inventory;

import java.util.List;
import java.util.Objects;

public record TransferPlan(List<Move> moves, int moved, int leftover) {
    public TransferPlan {
        Objects.requireNonNull(
                moves, "Список перемещений не может быть null: план переноса — это последовательность ходов");
        moves = List.copyOf(moves);
        if (moved < 0) {
            throw new IllegalArgumentException(
                    "Перенесено " + moved + " предметов: отрицательное количество не бывает, ошибка в подсчёте плана");
        }
        if (leftover < 0) {
            throw new IllegalArgumentException(
                    "Осталось " + leftover + " предметов: отрицательный остаток не бывает, ошибка в подсчёте плана");
        }
    }
}
