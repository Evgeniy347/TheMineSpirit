package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.FactChecks;

public record DirectionThreat(double bearing, double strength, int enemies) {
    public DirectionThreat {
        FactChecks.range("DirectionThreat.bearing", bearing, 0, 2 * Math.PI);
        if (!Double.isFinite(strength) || strength < 0) {
            throw new IllegalArgumentException(
                    "strength = " + strength + ": сила сектора обязана быть конечной и неотрицательной");
        }
        FactChecks.range("DirectionThreat.enemies", enemies, 0, EnemyFact.MAX_COUNT);
    }
}
