package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.Point;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record DefenseMember(
        UUID id,
        Point pos,
        double hpFraction,
        int armor,
        boolean bow,
        double meleeDps,
        double food,
        Set<DefensePotion> potions) {
    public static final int MAX_ARMOR = 20;

    public DefenseMember {
        if (id == null) {
            throw new IllegalArgumentException("id не может быть null: приказ адресуется конкретному духу");
        }
        if (pos == null) {
            throw new IllegalArgumentException("pos не может быть null: без позиции не посчитать отряд и пост");
        }
        if (!Double.isFinite(hpFraction) || !Double.isFinite(food) || !Double.isFinite(meleeDps)) {
            throw new IllegalArgumentException("hpFraction = " + hpFraction + ", food = " + food + ", meleeDps = "
                    + meleeDps + ": здоровье, сытость и урон обязаны быть конечными числами");
        }
        hpFraction = Math.max(0.0, Math.min(1.0, hpFraction));
        food = Math.max(0.0, Math.min(1.0, food));
        if (armor < 0 || armor > MAX_ARMOR) {
            throw new IllegalArgumentException(
                    "armor = " + armor + ": броня духа считается от 0 до " + MAX_ARMOR + ", иначе фронт не сравнить");
        }
        if (meleeDps < 0) {
            throw new IllegalArgumentException(
                    "meleeDps = " + meleeDps + ": урон ближнего боя не может быть отрицательным");
        }
        potions = potions == null ? Set.of() : Set.copyOf(potions);
    }

    public static DefenseMember of(DefenderCapability defender) {
        Objects.requireNonNull(defender, "Защитник не может быть null: отряд собирается из живых духов");
        return new DefenseMember(
                defender.id(),
                defender.pos(),
                defender.hpFraction(),
                defender.armor(),
                defender.bow(),
                defender.meleeDps(),
                1.0,
                defender.heal() ? Set.of(DefensePotion.HEAL) : Set.of());
    }

    public DefenderCapability capability() {
        return new DefenderCapability(id, pos, hpFraction, armor, bow, meleeDps, potions.contains(DefensePotion.HEAL));
    }

    public DefenderRole role() {
        return bow ? DefenderRole.ARCHER : DefenderRole.MELEE;
    }

    public double frontRank() {
        return 0.5 * (armor / (double) MAX_ARMOR) + 0.5 * hpFraction;
    }

    public boolean has(DefensePotion potion) {
        return potions.contains(potion);
    }
}
