package com.theminespirit.core.tactics;

public record FactorScales(
        ArmorModel armor,
        double minDps,
        double winMarginDivisor,
        double lowHpFloor,
        double proximityRange,
        double rangedRange,
        double rangedFull,
        double explosiveRange,
        double allyFull,
        double routesFull,
        double lavaRange,
        double dropSafe,
        double dropRange,
        double nightFloor,
        double hungerThreshold,
        double healFull,
        double leashMargin,
        double speedRatioCap,
        double urgencyHorizon) {
    public FactorScales {
        FactChecks.required("FactorScales.armor", armor);
        FactChecks.range("FactorScales.minDps", minDps, 0.001, 100);
        FactChecks.range("FactorScales.winMarginDivisor", winMarginDivisor, 0.1, 100);
        FactChecks.range("FactorScales.lowHpFloor", lowHpFloor, 0.01, 1);
        FactChecks.range("FactorScales.proximityRange", proximityRange, 0.1, 1000);
        FactChecks.range("FactorScales.rangedRange", rangedRange, 0.1, 1000);
        FactChecks.range("FactorScales.rangedFull", rangedFull, 1, 64);
        FactChecks.range("FactorScales.explosiveRange", explosiveRange, 0.1, 1000);
        FactChecks.range("FactorScales.allyFull", allyFull, 1, 64);
        FactChecks.range("FactorScales.routesFull", routesFull, 1, 10);
        FactChecks.range("FactorScales.lavaRange", lavaRange, 0.1, 100);
        FactChecks.range("FactorScales.dropSafe", dropSafe, 0, 100);
        FactChecks.range("FactorScales.dropRange", dropRange, 0.1, 400);
        FactChecks.range("FactorScales.nightFloor", nightFloor, 0, 1);
        FactChecks.range("FactorScales.hungerThreshold", hungerThreshold, 0.01, 1);
        FactChecks.range("FactorScales.healFull", healFull, 1, 64);
        FactChecks.range("FactorScales.leashMargin", leashMargin, 0.1, 1000);
        FactChecks.range("FactorScales.speedRatioCap", speedRatioCap, 1, 1000);
        FactChecks.range("FactorScales.urgencyHorizon", urgencyHorizon, 0.1, 1000);
    }
}
