package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import java.util.Objects;

public record RankedTarget(EnemyFact enemy, TargetClass cls, int rank, double distance) {
    public RankedTarget {
        if (enemy == null) {
            throw new IllegalArgumentException("enemy не может быть null: ранг считается для конкретной цели");
        }
        Objects.requireNonNull(cls, "cls не может быть null: цель без класса выпадает из приоритета");
        if (rank < 0) {
            throw new IllegalArgumentException(
                    "rank = " + rank + ": ранг — индекс класса в приоритете, отрицательным не бывает");
        }
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException(
                    "distance = " + distance + ": дистанция обязана быть конечной и неотрицательной");
        }
    }
}
