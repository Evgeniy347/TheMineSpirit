package com.theminespirit.core.tactics;

public final class WinMargin {
    private static final double LN2 = Math.log(2);

    private WinMargin() {}

    public static double of(SelfFacts self, EnemyMetrics enemies, FactorScales scales) {
        if (enemies.count() == 0) {
            return 0;
        }
        double ownEhp = scales.armor().effectiveHp(self.hp(), self.armor());
        if (ownEhp <= 0) {
            return -1;
        }
        if (enemies.effectiveHp() <= 0) {
            return 1;
        }
        double ratio = timeToDie(self, enemies, scales) / timeToKill(self, enemies, scales);
        return clamp(Math.log(ratio) / LN2 / scales.winMarginDivisor(), -1, 1);
    }

    public static double timeToKill(SelfFacts self, EnemyMetrics enemies, FactorScales scales) {
        return enemies.effectiveHp() / Math.max(scales.minDps(), self.dps());
    }

    public static double timeToDie(SelfFacts self, EnemyMetrics enemies, FactorScales scales) {
        double ownEhp = scales.armor().effectiveHp(self.hp(), self.armor());
        return ownEhp / Math.max(scales.minDps(), enemies.totalDps());
    }

    public static double pressure(SelfFacts self, EnemyMetrics enemies, FactorScales scales) {
        double timeToKill = timeToKill(self, enemies, scales);
        if (timeToKill <= 0) {
            return 0;
        }
        return clamp(1 - timeToDie(self, enemies, scales) / timeToKill, 0, 1);
    }

    public static double urgency(SelfFacts self, EnemyMetrics enemies, FactorScales scales) {
        return clamp(1 - timeToDie(self, enemies, scales) / scales.urgencyHorizon(), 0, 1);
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
