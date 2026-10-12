package com.theminespirit.core.tactics;

public enum Factor {
    BIAS((situation, assessment, scales) -> 1),
    WIN_MARGIN((situation, assessment, scales) -> assessment.winMargin()),
    LOW_HP((situation, assessment, scales) -> unit(
            (scales.lowHpFloor() - situation.self().hpFraction()) / scales.lowHpFloor())),
    ENEMY_PRESSURE((situation, assessment, scales) -> assessment.enemies().count() == 0 ? 0
            : WinMargin.pressure(situation.self(), assessment.enemies(), scales)),
    PROXIMITY((situation, assessment, scales) -> assessment.enemies().count() == 0 ? 0
            : unit(1 - assessment.enemies().nearest() / scales.proximityRange())),
    RANGED_EXPOSURE((situation, assessment, scales) -> rangedExposure(assessment.enemies(), scales)),
    EXPLOSIVE_NEAR((situation, assessment, scales) -> unit(
            1 - assessment.enemies().nearestExplosive() / scales.explosiveRange())),
    ALLY_SUPPORT((situation, assessment, scales) -> unit(situation.self().allies() / scales.allyFull())),
    ESCAPE_ROUTE((situation, assessment, scales) -> assessment.escapeQuality()),
    TERRAIN_RISK((situation, assessment, scales) -> Math.max(
            unit(1 - situation.env().lavaNear() / scales.lavaRange()),
            unit((situation.env().dropHeight() - scales.dropSafe()) / scales.dropRange()))),
    DARKNESS((situation, assessment, scales) -> Math.max(
            unit(1 - (double) situation.env().light() / EnvFacts.MAX_LIGHT),
            situation.env().night() ? scales.nightFloor() : 0)),
    HUNGER((situation, assessment, scales) -> unit(
            (scales.hungerThreshold() - situation.self().food()) / scales.hungerThreshold())),
    PROTECT_URGENCY((situation, assessment, scales) ->
            assessment.mode() == CombatMode.PROTECT && situation.orders().wardThreatened() ? 1 : 0),
    LEASH_PRESSURE((situation, assessment, scales) -> assessment.enemies().count() == 0 ? 0
            : unit(1 - assessment.leashLeft() / scales.leashMargin())),
    HEALING_AVAILABLE((situation, assessment, scales) -> unit(situation.self().healItems() / scales.healFull())),
    URGENCY((situation, assessment, scales) -> assessment.enemies().count() == 0 ? 0
            : WinMargin.urgency(situation.self(), assessment.enemies(), scales));

    private final FactorFunction function;

    Factor(FactorFunction function) {
        this.function = function;
    }

    public double value(Situation situation, Assessment assessment, FactorScales scales) {
        return function.apply(situation, assessment, scales);
    }

    private static double unit(double value) {
        return WinMargin.clamp(value, 0, 1);
    }

    private static double rangedExposure(EnemyMetrics enemies, FactorScales scales) {
        if (enemies.rangedCount() == 0) {
            return 0;
        }
        return unit(enemies.rangedCount() / scales.rangedFull())
                * unit(1 - enemies.nearestRanged() / scales.rangedRange());
    }

    @FunctionalInterface
    private interface FactorFunction {
        double apply(Situation situation, Assessment assessment, FactorScales scales);
    }
}
