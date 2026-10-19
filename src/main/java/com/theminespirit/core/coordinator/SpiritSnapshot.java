package com.theminespirit.core.coordinator;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.Optional;
import java.util.UUID;

public record SpiritSnapshot(
        UUID id,
        Point pos,
        double hpFraction,
        SpiritRole role,
        CombatMode mode,
        int threatsNear,
        boolean attacked,
        Optional<WorkTarget> workTarget,
        long lastSeenTick,
        double food) {

    public SpiritSnapshot {
        requireNonNull(id, "id не может быть null: снимок без id не опознает духа");
        requireNonNull(pos, "pos не может быть null: снимок без позиции не опознает духа");
        requireNonNull(role, "role не может быть null: роль духа входит в решение координатора");
        requireNonNull(mode, "mode не может быть null: режим боя входит в решение координатора");
        hpFraction = clampUnit(hpFraction);
        food = clampUnit(food);
        if (threatsNear < 0) {
            throw new IllegalArgumentException(
                    "threatsNear = " + threatsNear + ": число угроз рядом не может быть отрицательным");
        }
        if (attacked && threatsNear == 0) {
            throw new IllegalArgumentException("attacked = true при threatsNear = 0: атакованный без угроз "
                    + "противоречит снимку — источник обязан согласовать флаг с числом угроз");
        }
        if (lastSeenTick < 0) {
            throw new IllegalArgumentException(
                    "lastSeenTick = " + lastSeenTick + ": тик последнего наблюдения не может быть отрицательным");
        }
        requireNonNull(workTarget, "workTarget не может быть null: у духа нет цели — передайте Optional.empty()");
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static double clampUnit(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
