package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.Point;
import java.util.UUID;

public record DefenderCapability(UUID id, Point pos, double hpFraction, int armor, boolean bow, double meleeDps,
        boolean heal) {
    public static final int MAX_ARMOR = 20;

    public DefenderCapability {
        if (id == null) {
            throw new IllegalArgumentException("id не может быть null: приказ обороны адресуется конкретному духу");
        }
        if (pos == null) {
            throw new IllegalArgumentException("pos не может быть null: без позиции не посчитать пост и дистанцию");
        }
        if (!Double.isFinite(hpFraction)) {
            throw new IllegalArgumentException(
                    "hpFraction = " + hpFraction + ": доля здоровья обязана быть конечным числом");
        }
        hpFraction = Math.max(0.0, Math.min(1.0, hpFraction));
        if (armor < 0 || armor > MAX_ARMOR) {
            throw new IllegalArgumentException(
                    "armor = " + armor + ": броня духа считается от 0 до " + MAX_ARMOR + ", иначе фронт не сравнить");
        }
        if (!Double.isFinite(meleeDps) || meleeDps < 0) {
            throw new IllegalArgumentException(
                    "meleeDps = " + meleeDps + ": урон ближнего боя обязан быть конечным и неотрицательным");
        }
    }

    public DefenderRole role() {
        return bow ? DefenderRole.ARCHER : DefenderRole.MELEE;
    }

    public double frontRank() {
        return 0.5 * (armor / (double) MAX_ARMOR) + 0.5 * hpFraction;
    }
}
