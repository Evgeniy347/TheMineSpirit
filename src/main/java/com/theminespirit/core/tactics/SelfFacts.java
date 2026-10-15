package com.theminespirit.core.tactics;

public record SelfFacts(Point pos, double hp, double maxHp, double armor, double dps, double speed, double food,
        int blocks, int rangedAmmo, boolean hasWaterBucket, int allies, int healItems) {
    public static final double ARMOR_CAP = 20;

    public SelfFacts {
        FactChecks.required("SelfFacts.pos", pos);
        FactChecks.range("SelfFacts.maxHp", maxHp, 1, 1024);
        FactChecks.range("SelfFacts.hp", hp, 0, maxHp);
        FactChecks.range("SelfFacts.armor", armor, 0, ARMOR_CAP);
        FactChecks.range("SelfFacts.dps", dps, 0, 1000);
        FactChecks.range("SelfFacts.speed", speed, 0, 2);
        FactChecks.range("SelfFacts.food", food, 0, 1);
        FactChecks.range("SelfFacts.blocks", blocks, 0, 2304);
        FactChecks.range("SelfFacts.rangedAmmo", rangedAmmo, 0, 2304);
        FactChecks.range("SelfFacts.allies", allies, 0, 64);
        FactChecks.range("SelfFacts.healItems", healItems, 0, 2304);
    }

    public double hpFraction() {
        return hp / maxHp;
    }

    public boolean hasRanged() {
        return rangedAmmo > 0;
    }
}
